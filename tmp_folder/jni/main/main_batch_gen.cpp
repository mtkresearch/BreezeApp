#include "common/dump.h"
#include "common/logging.h"
#include "common/timer.h"
#include "mtk_llm.h"
#include "tokenizer/tokenizer.h"
#include "tokenizer/tokenizer_factory.h"
#include "utils/NPUWareUtilsLib.h"
#include "utils/utils.h"

#include <filesystem>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace fs = std::filesystem;

using TokenType = mtk::Tokenizer::TokenType;
using TokenizerUPtr = std::unique_ptr<mtk::Tokenizer>;
using mtk::TokenizerFactory;

LlmModelOptions llmModelOpt;
LlmRuntimeOptions llmRuntimeOpt;

size_t inferenceStep = 0; // Global counter

TokenizerUPtr prepare_tokenizer() {
    auto tokenizer =
        TokenizerFactory().create(llmRuntimeOpt.tokenizerPath, llmRuntimeOpt.tokenizerRegex);
    const auto& specialTokens = llmRuntimeOpt.specialTokens;
    if (specialTokens.addBos)
        tokenizer->enableBosToken(specialTokens.bosId);
    return tokenizer;
}

std::tuple<std::string, std::vector<TokenType>>
get_prompt_and_tokens(const std::string& inputString, const TokenizerUPtr& tokenizer,
                      const bool parsePromptTokens) {
    // Parse or tokenize input
    auto inputTokens =
        parsePromptTokens ? utils::parseTokenString(inputString) : tokenizer->tokenize(inputString);

    const auto& inputPrompt = parsePromptTokens ? tokenizer->detokenize(inputTokens) : inputString;
    return {inputPrompt, inputTokens};
}

void llm_init(void** llmRuntime, const std::string& yamlConfigPath) {
    Timer timer;
    timer.start();
    LOG(INFO) << "Begin model init...";

    // Force reset config to default values
    llmModelOpt = {};
    llmRuntimeOpt = {};

    // Load yaml config
    utils::parseLlmConfigYaml(yamlConfigPath, llmModelOpt, llmRuntimeOpt);

    bool status = mtk_llm_init(llmRuntime, llmModelOpt, llmRuntimeOpt);
    if (!status) {
        LOG(FATAL) << "LLM init failed";
    }
    double elapsed = timer.reset();
    LOG(INFO) << "Done model init. (Time taken: " << elapsed << "s)";
}

void llm_swap_model(void* llmRuntime, const size_t tokenSize = 1) {
    Timer timer;
    timer.start();
    LOG(INFO) << "Hot swapping to " << tokenSize << "t model...";
    mtk_llm_swap_model(llmRuntime, tokenSize);
    double elapsed = timer.reset();
    LOG(INFO) << "Done model hot swapping. (Time taken: " << elapsed << "s)";
}

std::vector<TokenType> llm_digest_prompt(void* llmRuntime, const TokenizerUPtr& tokenizer,
                                         const std::vector<TokenType>& inputTokens,
                                         const size_t topk, const size_t modelTokenSize,
                                         double& promptTokPerSec) {
    const auto logitsType = llmModelOpt.modelOutputType;
    void* lastLogits;
    const auto inpBeginIt = inputTokens.cbegin();
    const auto inputTokenCount = inputTokens.size();
    size_t inputTokenIndex = 0;

    const auto startTokenIndex = mtk_llm_get_token_index(llmRuntime);

    // Warn cache overflow
    if (startTokenIndex + inputTokenCount > llmModelOpt.cacheSize) {
        LOG(WARN) << "Input prompt length (" << inputTokenCount << ") is longer than the available "
                  << "context length (cur token index = " << startTokenIndex
                  << ", cache size = " << llmModelOpt.cacheSize << "). Cache will be overflowed.";
    }

    auto getNewTokens = [&]() {
        // Calculate prompt tokens size for current step
        const size_t numInputTokenLeft = inputTokenCount - inputTokenIndex;
        const size_t remainder = numInputTokenLeft % modelTokenSize;
        // Construct subset prompt tokens
        const auto numNewTok = remainder ? remainder : modelTokenSize;
        const auto tokIdxStart = inputTokenIndex;       // inclusive
        const auto tokIdxEnd = tokIdxStart + numNewTok; // exclusive
        const auto newTokens = std::vector(inpBeginIt + tokIdxStart, inpBeginIt + tokIdxEnd);
        LOG(DEBUG) << "Feeding model with prompt tokens [" << tokIdxStart << " - " << tokIdxEnd
                   << "] (numToken=" << numNewTok << "): " << newTokens;
        return newTokens;
    };

    Timer promptTimer;
    promptTimer.start();
    while (inputTokenIndex < inputTokenCount) {
        SET_DUMP_INDEX(inferenceStep++);
        LOG(DEBUG) << "Token position: " << inputTokenIndex << ": " << inputTokens[inputTokenIndex];

        const auto curInputTokens = getNewTokens();
        const auto numNewTok = curInputTokens.size();
        DUMP(INPUTS).fromVector("input_tokens", curInputTokens);
        DUMP(INPUTS).fromString("input_string", tokenizer->detokenize(curInputTokens));

        auto isLastPromptStep = [&] { return inputTokenIndex + numNewTok >= inputTokenCount; };

        // Only the last prompt step needs logits
        const auto logitsKind = isLastPromptStep() ? LogitsKind::LAST : LogitsKind::NONE;
        lastLogits = mtk_llm_inference_once(llmRuntime, curInputTokens, logitsKind);

        inputTokenIndex += numNewTok;
    }
    double promptTimeTaken = promptTimer.reset();

    // Ideal prompt size is a multiple of prompt token batch size
    const size_t idealPromptSize =
        std::ceil(float(inputTokenCount) / modelTokenSize) * modelTokenSize;
    DCHECK_EQ(idealPromptSize % modelTokenSize, 0);
    promptTokPerSec = idealPromptSize / promptTimeTaken;

    LOG(INFO) << "Done analyzing prompt in " << promptTimeTaken << "s" << " (" << promptTokPerSec
              << " tok/s)";
    // Prompt mode ended, take the output and feed as input
    // Argmax to generate the token first
    return utils::getTopkArgmaxV2(logitsType, lastLogits, tokenizer->vocabSize(), topk);
}

Batched<TokenType> llm_autoregressive_per_step(void* llmRuntime,
                                               const Batched<TokenType> batchInputToken,
                                               const size_t vocabSize) {
    // Unsqueeze (B, 1) -> (B, T=1, 1)
    Batched<std::vector<TokenType>> batchInputTokens;
    batchInputTokens.reserve(batchInputToken.size());
    for (const auto& inputToken : batchInputToken) {
        batchInputTokens.push_back({inputToken});
    }

    // Run inference to get the logits in 16 bit
    const auto batchLastLogits = mtk_llm_inference_batch(llmRuntime, batchInputTokens);

    // Compute argmax on the logits
    const auto logitsType = llmModelOpt.modelOutputType;
    Batched<TokenType> batchOutputToken;
    batchOutputToken.reserve(batchLastLogits.size());
    for (const auto lastLogits : batchLastLogits) {
        const auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, vocabSize);
        batchOutputToken.push_back(outputToken);
    }
    return batchOutputToken;
}

Batched<std::vector<TokenType>>
llm_gen_response(void* llmRuntime, const TokenizerUPtr& tokenizer, const size_t maxResponse,
                 const Batched<TokenType> firstInputTokens, Batched<std::string>& fullResponses,
                 double& genTokPerSec) {
    const size_t maxTokenLength = llmModelOpt.maxTokenLength;
    auto curTokenIndex = mtk_llm_get_token_index(llmRuntime);
    const auto& sequenceLength = curTokenIndex; // The number of tokens the model has seen.

    double elapsed = 0, genTotalTime = 0;
    genTokPerSec = 0;
    size_t genTokCount = 0;

    const auto genBatchSize = llmModelOpt.genModelBatchSize;
    Batched<utils::UTF8CharResolver> utf8Resolvers(genBatchSize);
    Batched<std::vector<TokenType>> batchGeneratedTokens(genBatchSize);
    for (auto& generatedTokens : batchGeneratedTokens) {
        generatedTokens.reserve(maxResponse);
    }

    auto appendGeneratedTokens = [&batchGeneratedTokens, genBatchSize](const auto& batchTokens) {
        DCHECK_EQ(batchTokens.size(), genBatchSize);
        for (size_t batch = 0; batch < genBatchSize; batch++) {
            batchGeneratedTokens[batch].push_back(batchTokens[batch]);
        }
    };

    auto isStopToken = [](const auto token) {
        const auto& stopTokenSet = llmRuntimeOpt.specialTokens.stopToken;
        return stopTokenSet.find(token) != stopTokenSet.end();
    };

    DCHECK_EQ(firstInputTokens.size(), genBatchSize);
    DCHECK_EQ(fullResponses.size(), genBatchSize);
    DCHECK_EQ(utf8Resolvers.size(), genBatchSize);

    auto decodeResponse = [&](const auto& batchOutputToken) {
        for (size_t batch = 0; batch < genBatchSize; batch++) {
            const auto& outputToken = batchOutputToken[batch];
            const auto tokStr = tokenizer->detokenize(outputToken);
            auto& utf8Resolver = utf8Resolvers[batch];
            const bool isTokStrResolved = utf8Resolver.addBytes(tokStr);
            if (isTokStrResolved) {
                const auto response = utf8Resolver.getResolvedStr();
                fullResponses[batch] += response;
                if (batch == 0)
                    std::cout << response << std::flush;
            }
            LOG(DEBUG) << "[Response " << genTokCount << "] Output token batch [" << batch
                       << "]: " << outputToken << ": \"" << tokStr << "\"";
        }
    };

    decodeResponse(firstInputTokens);
    appendGeneratedTokens(firstInputTokens);
    auto batchOutputToken = firstInputTokens;

    Timer timer;
    timer.start();
    while (genTokCount < maxResponse && sequenceLength < maxTokenLength) {
        SET_DUMP_INDEX(inferenceStep++);

        // Warn cache overflow
        if (sequenceLength == llmModelOpt.cacheSize) {
            LOG(WARN) << "The max context length (" << llmModelOpt.cacheSize
                      << ") has already been reached, about to overflow the cache.";
        }
        batchOutputToken =
            llm_autoregressive_per_step(llmRuntime, batchOutputToken, tokenizer->vocabSize());
        appendGeneratedTokens(batchOutputToken);
        genTokCount++;
        curTokenIndex++;

        elapsed = timer.reset();
        genTotalTime += elapsed;
        LOG(DEBUG) << "Single loop time taken: " << elapsed * 1000 << " ms";

        decodeResponse(batchOutputToken);

        // Stop when output is a stop token and running in single batch
        if (genBatchSize == 1 && isStopToken(batchOutputToken[0])) {
            std::cout << "</eos>";
            break;
        }
    }
    genTokPerSec = double(genTokCount) / genTotalTime;
    for (size_t batch = 0; batch < genBatchSize; batch++) {
        std::cout << "\n[Full Response Batch " << batch << "]\n"
                  << fullResponses[batch] << std::endl;
    }
    return batchGeneratedTokens;
}

std::tuple<double, double>
llm_inference(void* llmRuntime, const std::string& inputString, const TokenizerUPtr& tokenizer,
              const size_t maxResponse = 50, const bool parsePromptTokens = false) {
    // Convert string to tokens
    auto [inputPrompt, inputTokens] =
        get_prompt_and_tokens(inputString, tokenizer, parsePromptTokens);
    DUMP(PROMPT).fromVector("prompt_tokens", inputTokens);
    DUMP(PROMPT).fromString("prompt_text", inputPrompt);

    std::cout << "\n[Prompt]\n" << inputPrompt << '\n' << std::endl;

    // Input prompt caching
    const auto promptTokenSize = llmModelOpt.promptTokenBatchSize;
    const auto genTokenSize = llmModelOpt.genTokenBatchSize;
    double promptTokPerSec;
    const auto topk = genTokenSize;
    const auto topkOutputTokens = llm_digest_prompt(
        llmRuntime, tokenizer, inputTokens, topk, promptTokenSize, promptTokPerSec);

    // Swap to gen mode model
    if (promptTokenSize != genTokenSize) {
        llm_swap_model(llmRuntime, genTokenSize);
    }

    // Enter gen batch mode
    const auto genBatchSize = llmModelOpt.genModelBatchSize;
    if (genTokenSize > 1 && genTokenSize == genBatchSize) {
        mtk_llm_use_prompt_as_batch_gen(llmRuntime);
    }

    Batched<std::string> fullResponses(genBatchSize);

    // Generation process
    std::cout << "\nResponse (Batch 0) [Max Length = " << maxResponse << "]:" << std::endl;
    DCHECK_EQ(topkOutputTokens.size(), genBatchSize);
    for (size_t batch = 0; batch < genBatchSize; batch++) {
        const auto outputToken = topkOutputTokens[batch];
        LOG(DEBUG) << "First output token (batch " << batch << ")" << outputToken << ": \""
                   << tokenizer->detokenize(outputToken) << "\"";
    }

    double genTokPerSec;
    const auto batchOutputTokens = llm_gen_response(
        llmRuntime, tokenizer, maxResponse, topkOutputTokens, fullResponses, genTokPerSec);

    // Show the output tokens if the input is also tokens
    if (parsePromptTokens) {
        std::cout << "\nGenerated Tokens: " << batchOutputTokens << std::endl;
    }
    std::cout << "\n[Latency]" << std::endl;
    std::cout << "      Prompt Mode: " << promptTokPerSec << " tok/s" << std::endl;
    std::cout << "  Generative Mode: " << genTokPerSec << " tok/s" << std::endl;
    return {promptTokPerSec, genTokPerSec};
}

void llm_reset(void* llmRuntime) {
    mtk_llm_reset(llmRuntime);
}

void llm_release(void* llmRuntime) {
    mtk_llm_release(llmRuntime);
}

int main(int argc, char* argv[]) {
    ScopePerformancer scopePerformancer; // Enable PowerHAL

    std::vector<std::string> yamlConfigPaths;
    size_t maxResponse = 200;
    bool parsePromptTokens = false; // Read prompt as a string of tokens
    bool onePromptPerLine = false;  // Treat each line in prompt text as a single prompt. Will
                                    // replace literal "\n" with new line char '\n'.
    std::string preformatterName = "";
    std::vector<std::string> promptPaths; // Paths containing the prompt text
    std::vector<std::string> prompts;
    // std::string prompt = "Once upon a time,";
    const std::string defaultPrompt = "Tell me about alpacas";

    using utils::matchArgument;

    // Process command line.
    //  -m or --max to set the max response.
    //  -p or --prompt to set the input prompt.
    //  -i or --input-file to set the path to the text containing the input prompt.
    //  --read-tokens to read the input prompt as a string of tokens.
    //  --one-prompt-per-line to treat each line in prompt file as one prompt. The literal "\n" is
    //  treated as new line.
    for (int i = 1; i < argc; i++) {
        std::string curArg(argv[i]);
        if (matchArgument(curArg, "--max", "-m")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            maxResponse = std::atoi(argv[++i]);
        } else if (matchArgument(curArg, "--prompt", "-p")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            prompts.emplace_back(argv[++i]);
        } else if (matchArgument(curArg, "--input-file", "-i")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            promptPaths.emplace_back(argv[++i]);
        } else if (fs::path(curArg).extension() == ".yaml") {
            LOG(INFO) << "Using yaml config file: " << curArg;
            yamlConfigPaths.push_back(curArg);
        } else if (matchArgument(curArg, "--read-tokens", "-t")) {
            parsePromptTokens = true;
        } else if (matchArgument(curArg, "--one-prompt-per-line")) {
            onePromptPerLine = true;
        } else if (matchArgument(curArg, "--preformatter")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            preformatterName = argv[++i];
        } else {
            LOG(INFO) << "Unrecognized argument: " << curArg;
        }
    }

    prompts = utils::readPromptFiles(promptPaths, onePromptPerLine);

    if (prompts.empty())
        prompts.push_back(defaultPrompt); // Use the default example.

    if (yamlConfigPaths.empty()) {
        LOG(ERROR) << "No yaml config file provided.";
    }

    const size_t numPrompt = prompts.size();
    void* llmRuntime;
    for (const auto& yamlConfigPath : yamlConfigPaths) {
        double allPromptTokPerSec = 0, allGenTokPerSec = 0;
        std::cout << "\n>>>>>>>>>>> Current yaml config: " << yamlConfigPath << " <<<<<<<<<<<"
                  << std::endl;
        // Get current config from yaml
        llm_init(&llmRuntime, yamlConfigPath);

        // Create tokenizer for the current config. Its lifetime is until the end of this scope.
        const auto tokenizer = prepare_tokenizer();
        LOG(INFO) << "Vocab size: " << tokenizer->vocabSize();

        // Start inferencing on the prompts
        for (size_t i = 0; i < numPrompt; i++) {
            std::cout << "=========== Processing the " << i
                      << "-th input. ===========" << std::endl;
            std::string prompt = prompts[i];
            DUMP(PROMPT).fromString("text", prompt);
            if (!parsePromptTokens && !preformatterName.empty()) {
                if (utils::addPreformatter(preformatterName, prompt)) {
                    LOG(INFO) << "Preformatted prompt with '" << preformatterName << "'";
                    DUMP(PROMPT).fromString("text_preformatted", prompt);
                } else {
                    LOG(ERROR) << "Invalid preformatter: '" << preformatterName << "'";
                }
            }
            auto [promptTokPerSec, genTokPerSec] =
                llm_inference(llmRuntime, prompt, tokenizer, maxResponse, parsePromptTokens);
            allPromptTokPerSec += promptTokPerSec;
            allGenTokPerSec += genTokPerSec;

            // Reset cache for the next prompt
            llm_reset(llmRuntime);
            llm_swap_model(llmRuntime, llmModelOpt.promptTokenBatchSize);
        }
        llm_release(llmRuntime);
        std::cout << "\n[Average Performance among the given " << numPrompt << " prompts]\n";
        std::cout << "      Prompt Mode: " << allPromptTokPerSec / numPrompt << " tok/s\n";
        std::cout << "  Generative Mode: " << allGenTokPerSec / numPrompt << " tok/s\n";
    }
}