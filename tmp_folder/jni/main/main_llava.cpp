#include "common/dump.h"
#include "common/logging.h"
#include "common/timer.h"
#include "mtk_mllm.h"
#include "third_party/include/yaml-cpp/yaml.h"
#include "tokenizer/tokenizer.h"
#include "tokenizer/tokenizer_factory.h"
#include "utils/NPUWareUtilsLib.h"
#include "utils/utils.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace fs = std::filesystem;

using TokenType = mtk::Tokenizer::TokenType;
using TokenizerUPtr = std::unique_ptr<mtk::Tokenizer>;
using mtk::TokenizerFactory;

static_assert(std::is_signed<TokenType>() == true,
              "Llava requires signed token type to represent negative image placeholder token id");

LlmModelOptions llmModelOpt;
LlmRuntimeOptions llmRuntimeOpt;
MllmRuntimeOptions mllmRuntimeOpt;

size_t inferenceStep = 0; // Global counter

void parseMllmConfigYaml(const std::string& configYamlPath, LlmModelOptions& modelOptions,
                         MllmRuntimeOptions& runtimeOptions) {
    // Load generic LLM options
    utils::parseLlmConfigYaml(configYamlPath, modelOptions, runtimeOptions);

    // Load additional MLLM options
    const auto& config = YAML::LoadFile(configYamlPath);

    const auto& runtimeOptYaml = [&] {
        const auto& llamaRuntimeOptYaml = config["llamaRuntimeOptions"];
        if (llamaRuntimeOptYaml) {
            LOG(WARN) << "The use of 'llamaRuntimeOptions' is deprecated. "
                         "Please use 'runtimeOptions' instead.";
            return llamaRuntimeOptYaml;
        }
        return config["runtimeOptions"];
    }();
    const auto& clipPathYaml = runtimeOptYaml["clipPath"];
    const auto& clipPatchEmbYaml = runtimeOptYaml["clipPatchEmb"];
    const auto& imageTokenSizeYaml = runtimeOptYaml["imageTokenSize"];

    // Error checking:
    //   - 'runtimeOptions' have to be defined.
    //   - 'runtimeOptions.clipPath' has to be defined.

    if (!runtimeOptYaml) {
        LOG(FATAL) << "Invalid yaml config file: 'MllmRuntimeOptions'"
                   << " is not found in the config.";
    }
    if (!clipPathYaml) {
        LOG(FATAL) << "Invalid yaml config file: 'clipPath'"
                   << " is not defined in the config.";
    }
    runtimeOptions.clipFile = clipPathYaml.as<std::string>();

    if (clipPatchEmbYaml)
        runtimeOptions.patchEmbFile = clipPatchEmbYaml.as<std::string>();

    if (imageTokenSizeYaml)
        runtimeOptions.imageTokenSize = imageTokenSizeYaml.as<size_t>();
}

TokenizerUPtr prepare_tokenizer(const LlmRuntimeOptions& runtimeOpt) {
    auto tokenizer = TokenizerFactory().create(runtimeOpt.tokenizerPath, runtimeOpt.tokenizerRegex);
    const auto& specialTokens = runtimeOpt.specialTokens;
    if (specialTokens.addBos)
        tokenizer->enableBosToken(specialTokens.bosId);
    return tokenizer;
}

bool isStopToken(const TokenType token) {
    const auto& stopTokenSet = mllmRuntimeOpt.specialTokens.stopToken;
    return stopTokenSet.find(token) != stopTokenSet.end();
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

inline std::vector<std::string> substr_delimit(const std::string& prompt,
                                               const std::string& delimiter,
                                               const bool preserveDelimiter = true) {
    size_t start = 0, end = 0;
    end = prompt.find(delimiter, end);

    std::vector<std::string> result;

    auto appendResult = [&result](const auto& sv) {
        if (!sv.empty())
            result.push_back(sv);
    };
    while (end != std::string::npos) {
        appendResult(prompt.substr(start, end - start));
        start = end + delimiter.length();
        end = prompt.find(delimiter, start);
        if (preserveDelimiter)
            appendResult(delimiter);
    }
    appendResult(prompt.substr(start, end - start));
    return result;
}

std::tuple<std::string, std::vector<TokenType>>
get_prompt_and_tokens_llava(const std::string& inputString, const TokenizerUPtr& tokenizer,
                            const bool parsePromptTokens) {
    // Parse or tokenize input
    const auto& specialTokens = mllmRuntimeOpt.specialTokens;
    std::vector<TokenType> inputTokens;
    std::string inputPrompt;
    static constexpr char imageTag[] = "<image>";
    const auto imageTokenStr = std::to_string(kImagePlaceholderToken);

    if (parsePromptTokens) {
        const auto tokenStrChunks = substr_delimit(inputString, imageTokenStr, true);
        for (const auto& tokenStrChunk : tokenStrChunks) {
            if (tokenStrChunk == imageTokenStr) {
                inputPrompt += imageTag;
                inputTokens.push_back(kImagePlaceholderToken);
            } else {
                const auto tokenIds = utils::parseTokenString(tokenStrChunk);
                inputTokens.insert(inputTokens.end(), tokenIds.begin(), tokenIds.end());
                inputPrompt += tokenizer->detokenize(tokenIds);
            }
        }
    } else {
        inputTokens.push_back(specialTokens.bosId); // Prepend bos token
        const auto promptTextChunks = substr_delimit(inputString, imageTag, true);
        for (const auto& promptTextChunk : promptTextChunks) {
            if (promptTextChunk == imageTag) {
                inputTokens.push_back(kImagePlaceholderToken);
            } else {
                auto tokenChunk = tokenizer->tokenize(promptTextChunk);
                inputTokens.insert(inputTokens.end(), tokenChunk.begin(), tokenChunk.end());
            }
        }
        inputPrompt = inputString;
    }
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

void llm_swap_model(void* llmRuntime, const size_t batchSize = 1) {
    Timer timer;
    timer.start();
    LOG(INFO) << "Hot swapping to " << batchSize << "t model...";
    mtk_llm_swap_model(llmRuntime, batchSize);
    double elapsed = timer.reset();
    LOG(INFO) << "Done model hot swapping. (Time taken: " << elapsed << "s)";
}

TokenType llm_digest_prompt(void* llmRuntime, const TokenizerUPtr& tokenizer,
                            const std::vector<TokenType>& inputTokens, const size_t modelTokenSize,
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

    // Ideal prompt size is a multiple of prompt batch size
    const size_t idealPromptSize =
        std::ceil(float(inputTokenCount) / modelTokenSize) * modelTokenSize;
    DCHECK_EQ(idealPromptSize % modelTokenSize, 0);
    promptTokPerSec = idealPromptSize / promptTimeTaken;

    LOG(INFO) << "Done analyzing prompt in " << promptTimeTaken << "s" << " (" << promptTokPerSec
              << " tok/s)";
    // Prompt mode ended, take the output and feed as input
    // Argmax to generate the token first
    auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());
    return outputToken;
}

TokenType llm_autoregressive_per_step(void* llmRuntime, const TokenizerUPtr& tokenizer,
                                      const TokenType inputToken) {
    const auto logitsType = llmModelOpt.modelOutputType;
    void* lastLogits;

    // Run inference to get the logits in INT16
    lastLogits = mtk_llm_inference_once(llmRuntime, {inputToken});

    // Compute argmax on the logits
    auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());

    return outputToken;
}

std::vector<TokenType> llm_gen_response(void* llmRuntime, const TokenizerUPtr& tokenizer,
                                        const size_t maxResponse, const TokenType firstInputToken,
                                        std::string& fullResponse, double& genTokPerSec) {
    const size_t maxTokenLength = llmModelOpt.maxTokenLength;
    auto curTokenIndex = mtk_llm_get_token_index(llmRuntime);
    const auto& sequenceLength = curTokenIndex; // The number of tokens the model has seen.

    double elapsed = 0, genTotalTime = 0;
    genTokPerSec = 0;
    size_t genTokCount = 0;

    std::string response;
    utils::UTF8CharResolver utf8Resolver;
    TokenType outputToken = firstInputToken;

    std::vector<TokenType> generatedTokens = {firstInputToken};

    Timer timer;
    timer.start();
    while (genTokCount < maxResponse && sequenceLength < maxTokenLength) {
        SET_DUMP_INDEX(inferenceStep++);

        // Warn cache overflow
        if (sequenceLength == llmModelOpt.cacheSize) {
            LOG(WARN) << "The max context length (" << llmModelOpt.cacheSize
                      << ") has already been reached, about to overflow the cache.";
        }
        outputToken = llm_autoregressive_per_step(llmRuntime, tokenizer, outputToken);
        generatedTokens.push_back(outputToken);
        genTokCount++;
        curTokenIndex++;

        elapsed = timer.reset();
        genTotalTime += elapsed;
        LOG(DEBUG) << "Single loop time taken: " << elapsed * 1000 << " ms";

        // Stop when output is a stop token (default to EoS if not set)
        if (isStopToken(outputToken)) {
            std::cout << "</eos>";
            break;
        }
        // Convert token id from argmax to string (bytes)
        std::string tokStr = tokenizer->detokenize(outputToken);

        LOG(DEBUG) << "[Response " << genTokCount << "] Output token " << outputToken << ": \""
                   << tokStr << "\"";

        bool isTokStrResolved = utf8Resolver.addBytes(tokStr);
        if (isTokStrResolved) {
            response = utf8Resolver.getResolvedStr();
            std::cout << response << std::flush;
            fullResponse += response;
        }
        DUMP(RESPONSE).fromValue("sampled_token", outputToken);
        DUMP(RESPONSE).fromString("sampled_text", tokStr);
        DUMP(RESPONSE).fromString("full_response", fullResponse);
    }
    std::cout << "</end>" << std::endl;
    genTokPerSec = double(genTokCount) / genTotalTime;
    std::cout << "\n[Full Response]\n" << fullResponse << std::endl;
    return generatedTokens;
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
    auto outputToken =
        llm_digest_prompt(llmRuntime, tokenizer, inputTokens, promptTokenSize, promptTokPerSec);
    // Swap to gen mode model
    if (promptTokenSize != genTokenSize) {
        llm_swap_model(llmRuntime, genTokenSize);
    }

    std::string fullResponse;

    // Generation process
    std::cout << "\nResponse [Max Length = " << maxResponse << "]:" << std::endl;
    std::string tokStr = tokenizer->detokenize(outputToken);
    std::cout << tokStr << std::flush;
    fullResponse += tokStr;
    LOG(DEBUG) << "First output token " << outputToken << ": \"" << tokStr << "\"";
    DUMP(RESPONSE).fromValue("sampled_token", outputToken);
    DUMP(RESPONSE).fromString("sampled_text", tokStr);
    DUMP(RESPONSE).fromString("full_response", fullResponse);

    double genTokPerSec;
    const auto outputTokens = llm_gen_response(
        llmRuntime, tokenizer, maxResponse, outputToken, fullResponse, genTokPerSec);

    // Show the output tokens if the input is also tokens
    if (parsePromptTokens) {
        std::cout << "\nGenerated Tokens: " << outputTokens << std::endl;
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

void mllm_init(void** mllmRuntime, const std::string& yamlConfigPath) {
    Timer timer;
    timer.start();
    LOG(INFO) << "Begin LLaVA model init...";

    parseMllmConfigYaml(yamlConfigPath, llmModelOpt, mllmRuntimeOpt);
    std::cout << "Done Parsing YAML" << std::endl;
    bool status = mtk_mllm_init(mllmRuntime, llmModelOpt, mllmRuntimeOpt);
    if (!status) {
        LOG(FATAL) << "LLaVA init failed";
    }
    double elapsed = timer.reset();
    LOG(INFO) << "Done LLaVA init. (Time taken: " << elapsed << "s)";
    return;
}

// Main inference flow
std::tuple<double, double> mllm_inference(void* mllmRuntime, const std::string& prompt,
                                          const std::vector<std::string>& promptImagePaths,
                                          const TokenizerUPtr& tokenizer, const size_t maxResponse,
                                          const bool parsePromptTokens) {
    auto [inputPrompt, inputTokens] =
        get_prompt_and_tokens_llava(prompt, tokenizer, parsePromptTokens);

    if (parsePromptTokens) {
        std::cout << "Prompt string from input tokens: \n" << inputPrompt << std::endl;
    }

    DUMP(PROMPT).fromVector("prompt_tokens", inputTokens);
    DUMP(PROMPT).fromString("prompt_text", inputPrompt);

    auto curTokenIndex = mtk_mllm_get_token_index(mllmRuntime);
    const auto& sequenceLength = curTokenIndex;
    const size_t maxTokenLength = llmModelOpt.maxTokenLength;
    size_t promptTokenSize = llmModelOpt.promptTokenBatchSize;
    const auto logitsType = llmModelOpt.modelOutputType;

    Timer promptTimer, inferenceTimer, fillBufferTimer;
    promptTimer.start();

    // Begin multimodal prompt mode inference
    size_t numPromptToken = 0;

    void* lastLogits =
        mtk_mllm_consume_prompt(mllmRuntime, inputTokens, promptImagePaths, &numPromptToken);

    double promptTimeTaken = promptTimer.reset();

    // Ideal prompt size is a multiple of prompt batch size
    const size_t idealPromptSize =
        std::ceil(float(numPromptToken) / promptTokenSize) * promptTokenSize;
    DCHECK_EQ(idealPromptSize % promptTokenSize, 0);
    const auto promptTokPerSec = idealPromptSize / promptTimeTaken;

    LOG(INFO) << "Done analyzing prompt (Total " << numPromptToken << " tokens) in "
              << promptTimeTaken << "s" << " (" << promptTokPerSec << " tok/s)";

    // Prompt mode ended, take the output and feed as input
    // Apply argmax to generate the first token
    auto outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());

    const auto genTokenSize = llmModelOpt.genTokenBatchSize;
    if (promptTokenSize != genTokenSize) {
        llm_swap_model(mllmRuntime, genTokenSize);
    }

    std::string fullResponse;

    // inference loop: response mode
    std::cout << "\nResponse [Max Length = " << maxResponse << "]:" << std::endl;
    std::string tokStr = tokenizer->detokenize(outputToken);
    std::cout << tokStr << std::flush;
    fullResponse += tokStr;
    LOG(DEBUG) << "First output token " << outputToken << ": \"" << tokStr << "\"";
    DUMP(RESPONSE).fromValue("sampled_token", outputToken);
    DUMP(RESPONSE).fromString("sampled_text", tokStr);
    DUMP(RESPONSE).fromString("full_response", fullResponse);

    // Variables for handling multibyte chars
    utils::UTF8CharResolver utf8Resolver;
    std::string response;

    int curResponse = 0;

    Timer timer;
    double elapsed = 0, genTotalTime = 0;
    double genTokPerSec = 0;
    size_t genTokCount = 0;

    // Begin gen mode inference

    timer.start();
    while (curResponse < maxResponse && sequenceLength < maxTokenLength) {
        mtk_mllm_get_text_embedding(mllmRuntime, {outputToken});

        // Run inference to get the logits
        lastLogits = mtk_mllm_inference_once(mllmRuntime);

        // Compute argmax on the logits
        outputToken = utils::argmaxFrom16bitLogits(logitsType, lastLogits, tokenizer->vocabSize());

        // Convert token id from argmax to string (bytes)
        tokStr = tokenizer->detokenize(outputToken);
        genTokCount++;

        LOG(DEBUG) << "[Response " << genTokCount << "] Output token " << outputToken << ": \""
                   << tokStr << "\"";

        curTokenIndex++;

        // Stop when output is a stop token (default to EoS if not set)
        if (isStopToken(outputToken)) {
            std::cout << "</eos>";
            elapsed = timer.reset();
            genTotalTime += elapsed;
            LOG(DEBUG) << "Single loop time taken: " << elapsed * 1000 << " ms";
            break;
        }

        bool isTokStrResolved = utf8Resolver.addBytes(tokStr);
        if (isTokStrResolved) {
            response = utf8Resolver.getResolvedStr();
            std::cout << response << std::flush;
            fullResponse += response;
        }
        DUMP(RESPONSE).fromValue("sampled_token", outputToken);
        DUMP(RESPONSE).fromString("sampled_text", tokStr);
        DUMP(RESPONSE).fromString("full_response", fullResponse);

        elapsed = timer.reset();
        genTotalTime += elapsed;
        LOG(DEBUG) << "Single loop time taken: " << elapsed * 1000 << " ms";
        curResponse++;
    }
    std::cout << "</end>" << std::endl;
    genTokPerSec = double(genTokCount) / genTotalTime;

    std::cout << "\n\n[Full Prompt]\n" << prompt << std::endl;
    std::cout << "\n[Full Response]\n" << fullResponse << std::endl;
    std::cout << "\n[Latency]" << std::endl;
    std::cout << "      Prompt Mode: " << promptTokPerSec << " tok/s" << std::endl;
    std::cout << "  Generative Mode: " << genTokPerSec << " tok/s" << std::endl;

    return {promptTokPerSec, genTokPerSec};
}

void mllm_release(void* mllmRuntime) {
    mtk_mllm_release(mllmRuntime);
}

int main(int argc, char* argv[]) {
    ScopePerformancer scopePerformancer; // Enable PowerHAL

    std::string yamlConfigPath = "config.yaml";
    size_t maxResponse = 400;
    bool parsePromptTokens = false; // Read prompt as a string of tokens
    std::vector<std::string> prompts;
    std::string defaultPrompt = "Show me a detailed recipe for cooking this at home.";
    std::string preformatterName = "VicunaNoInput";

    // Image Path
    // If given, the real path will be given by cmdline args. Running in MLLM mode
    // If not given, path will be "". Running in text-only mode
    std::vector<std::string> imagePaths;
    std::string defaultimagePath = "/data/local/tmp/llava_sdk/data/llava-bench/images/023.jpg";

    using utils::matchArgument;

    // Process command line.
    //  -m or --max to set the max response.
    //  -p or --prompt to set the input prompt.
    //  -i or --input-file to set the path to the text containing the input prompt.
    //  --read-tokens to read the input prompt as a string of tokens.
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
            std::ifstream fin(argv[++i]);
            if (!fin) {
                LOG(ERROR) << "Unable to open the prompt file: " << argv[i];
                continue;
            } else {
                LOG(INFO) << "Reading prompt from file: " << argv[i];
            }
            std::string promptLine;
            while (std::getline(fin, promptLine) && !promptLine.empty()) {
                if (utils::isWhiteLine(promptLine))
                    continue;
                prompts.push_back(promptLine);
            }
        } else if (fs::path(curArg).extension() == ".yaml") {
            LOG(INFO) << "Using yaml config file: " << curArg;
            yamlConfigPath = curArg;
        } else if (matchArgument(curArg, "--read-tokens", "-t")) {
            parsePromptTokens = true;
        } else if (matchArgument(curArg, "--image", "-im")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            imagePaths.emplace_back(argv[++i]);
        } else if (matchArgument(curArg, "--preformatter", "-pref")) {
            ENSURE_NEXT_ARG_EXISTS(i)
            preformatterName = argv[++i];
        } else {
            LOG(INFO) << "Unrecognized argument: " << curArg;
        }
    }

    if (prompts.empty())
        prompts.push_back(defaultPrompt); // Use the default example.

    if (imagePaths.empty())
        imagePaths.push_back(defaultimagePath); // Use the default image.

    CHECK_EQ(prompts.size(), imagePaths.size());

    bool isMultimodalMode = !imagePaths[0].empty();

    // Insert <image> token
    if (isMultimodalMode && !parsePromptTokens) {
        for (int i = 0; i < prompts.size(); i++) {
            prompts[i].insert(0, "<image>\n");
        }
    }

    double allPromptTokPerSec = 0, allGenTokPerSec = 0;
    const size_t numPrompt = prompts.size();

    // mllm flow (multimodality)
    if (isMultimodalMode) {
        void* mllmRuntime;
        mllm_init(&mllmRuntime, yamlConfigPath);

        // Create tokenizer for the current config. Its lifetime is until the end of this scope.
        const auto tokenizer = prepare_tokenizer(mllmRuntimeOpt);
        LOG(INFO) << "Vocab size: " << tokenizer->vocabSize();

        for (size_t i = 0; i < numPrompt; i++) {
            std::cout << "=========== Processing the " << i
                      << "-th input. ===========" << std::endl;
            std::string prompt = prompts[i];
            const auto promptImagePaths = utils::split(imagePaths[i], ",;");
            DUMP(PROMPT).fromString("text", prompt);
            if (!parsePromptTokens && !preformatterName.empty()) {
                if (utils::addPreformatter(preformatterName, prompt)) {
                    LOG(INFO) << "Preformatted prompt with '" << preformatterName << "'";
                    DUMP(PROMPT).fromString("text_preformatted", prompt);
                } else {
                    LOG(ERROR) << "Invalid preformatter: '" << preformatterName << "'";
                }
            }

            auto [promptTokPerSec, genTokPerSec] = mllm_inference(
                mllmRuntime, prompt, promptImagePaths, tokenizer, maxResponse, parsePromptTokens);
            allPromptTokPerSec += promptTokPerSec;
            allGenTokPerSec += genTokPerSec;
        }
        mllm_release(mllmRuntime);
    } else { // llm flow (text-only)
        void* llmRuntime;
        llm_init(&llmRuntime, yamlConfigPath);

        // Create tokenizer for the current config. Its lifetime is until the end of this scope.
        const auto tokenizer = prepare_tokenizer(llmRuntimeOpt);
        LOG(INFO) << "Vocab size: " << tokenizer->vocabSize();

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
            llm_reset(llmRuntime);
            llm_swap_model(llmRuntime, llmModelOpt.promptTokenBatchSize);
        }
        llm_release(llmRuntime);
    }
    std::cout << "\n[Average Performance among the given " << numPrompt << " prompts]\n";
    std::cout << "      Prompt Mode: " << allPromptTokPerSec / numPrompt << " tok/s\n";
    std::cout << "  Generative Mode: " << allGenTokPerSec / numPrompt << " tok/s\n";
}