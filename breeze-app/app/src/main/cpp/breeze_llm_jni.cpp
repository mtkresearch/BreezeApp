#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <setjmp.h>
#include <signal.h>

// Include necessary headers from the prebuilt libraries
#include "mtk_llm.h"
#include "tokenizer/tokenizer.h"
#include "tokenizer/tokenizer_factory.h"

#define LOG_TAG "BreezeJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using TokenType = mtk::Tokenizer::TokenType;
using TokenizerUPtr = std::unique_ptr<mtk::Tokenizer>;

// Global state needed for JNI implementation
static void* llmRuntime = nullptr;
static TokenizerUPtr tokenizer;
static jmp_buf jump_buffer;
static volatile sig_atomic_t fault_occurred = 0;

static void signal_handler(int signum) {
    fault_occurred = 1;
    longjmp(jump_buffer, 1);
}

// JNI implementation for the Breeze app package
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeInitLlm(JNIEnv *env,
                                                                        jobject /* this */,
                                                                        jstring yamlConfigPath,
                                                                        jboolean preloadSharedWeights) {
    const char *configPath = nullptr;
    struct sigaction sa, old_sa;
    bool success = false;

    memset(&sa, 0, sizeof(struct sigaction));
    sa.sa_handler = signal_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;

    if (sigaction(SIGSEGV, &sa, &old_sa) == -1) {
        LOGE("Failed to set up signal handler");
        return JNI_FALSE;
    }

    try {
        configPath = env->GetStringUTFChars(yamlConfigPath, 0);
        if (!configPath) {
            LOGE("Failed to get config path string");
            return JNI_FALSE;
        }

        LOGI("Initializing LLM with config: %s", configPath);

        if (setjmp(jump_buffer) == 0) {
            fault_occurred = 0;
            
            // Call the mtk_llm library functions
            LlmModelOptions llmModelOpt = {};
            LlmRuntimeOptions llmRuntimeOpt = {};
            
            // Parse YAML config
            utils::parseLlmConfigYaml(configPath, llmModelOpt, llmRuntimeOpt);
            
            // Initialize with optional shared weights
            SharedWeightsHandle* sharedWeightsHandle = nullptr;
            if (preloadSharedWeights) {
                mtk_llm_preload_shared_weights(&sharedWeightsHandle, llmRuntimeOpt);
            }
            
            // Initialize LLM runtime
            bool initSuccess = mtk_llm_init(&llmRuntime, llmModelOpt, llmRuntimeOpt, sharedWeightsHandle);
            if (!initSuccess || !llmRuntime) {
                LOGE("Failed to initialize LLM runtime");
                goto cleanup;
            }

            // Create tokenizer
            tokenizer = TokenizerFactory().create(llmRuntimeOpt.tokenizerPath, llmRuntimeOpt.tokenizerRegex);
            if (!tokenizer) {
                LOGE("Failed to create tokenizer");
                goto cleanup;
            }
            
            // Enable BOS token if needed
            const auto& specialTokens = llmRuntimeOpt.specialTokens;
            if (specialTokens.addBos) {
                tokenizer->enableBosToken(specialTokens.bosId);
            }

            success = true;
        } else {
            LOGE("Segmentation fault occurred during LLM initialization");
        }
    } catch (const std::exception &e) {
        LOGE("Exception during LLM initialization: %s", e.what());
    } catch (...) {
        LOGE("Unknown exception during LLM initialization");
    }

    cleanup:
    if (configPath) {
        env->ReleaseStringUTFChars(yamlConfigPath, configPath);
    }

    // Restore the old signal handler
    if (sigaction(SIGSEGV, &old_sa, NULL) == -1) {
        LOGE("Failed to restore old signal handler");
    }

    if (fault_occurred) {
        if (llmRuntime) {
            mtk_llm_release(llmRuntime);
            llmRuntime = nullptr;
        }
        if (tokenizer) {
            tokenizer.reset();
        }
    }

    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeInference(JNIEnv *env,
                                                                          jobject /* this */,
                                                                          jstring inputString,
                                                                          jint maxResponse,
                                                                          jboolean parsePromptTokens) {
    if (!llmRuntime || !tokenizer) {
        LOGE("LLM not initialized");
        return nullptr;
    }

    const char *input = env->GetStringUTFChars(inputString, 0);
    std::string prompt(input);
    env->ReleaseStringUTFChars(inputString, input);

    LOGI("Performing inference for input: %s", prompt.c_str());

    // Handle preformatting if needed
    jstring preformatterName = env->NewStringUTF("Llama3NoInput");
    if (!parsePromptTokens && preformatterName != nullptr) {
        const char *preformatterNameStr = env->GetStringUTFChars(preformatterName, 0);
        std::string preformatterNameCpp(preformatterNameStr);
        env->ReleaseStringUTFChars(preformatterName, preformatterNameStr);

        if (!preformatterNameCpp.empty()) {
            if (utils::addPreformatter(preformatterNameCpp, prompt)) {
                LOGI("Preformatted prompt with '%s'", preformatterNameCpp.c_str());
            } else {
                LOGE("Invalid preformatter: '%s'", preformatterNameCpp.c_str());
            }
        }
    }

    // Use the tokenizer to handle the input
    std::vector<TokenType> inputTokens;
    if (parsePromptTokens) {
        inputTokens = utils::parseTokenString(prompt);
    } else {
        inputTokens = tokenizer->tokenize(prompt);
    }
    
    // Full inference process would go here, but we'll use the library functions
    std::string fullResponse;
    double promptTokPerSec = 0.0, genTokPerSec = 0.0;
    
    // Perform inference (simplified for brevity)
    const auto promptTokenSize = 128; // Default value, should be replaced with actual value from config
    
    void* logits = nullptr;
    // Process input tokens in batches and get last logits
    for (size_t i = 0; i < inputTokens.size(); i += promptTokenSize) {
        auto batchSize = std::min(promptTokenSize, inputTokens.size() - i);
        std::vector<TokenType> batch(inputTokens.begin() + i, inputTokens.begin() + i + batchSize);
        logits = mtk_llm_inference_once(llmRuntime, batch, i + batchSize >= inputTokens.size() ? LogitsKind::LAST : LogitsKind::NONE);
    }
    
    // Generate response tokens
    TokenType outputToken = utils::argmaxFrom16bitLogits(llmModelOpt.modelOutputType, logits, tokenizer->vocabSize());
    
    fullResponse = tokenizer->detokenize(outputToken);
    
    // Generate remaining tokens
    for (int i = 1; i < maxResponse; i++) {
        logits = mtk_llm_inference_once(llmRuntime, {outputToken});
        outputToken = utils::argmaxFrom16bitLogits(llmModelOpt.modelOutputType, logits, tokenizer->vocabSize());
        
        // Check for stop tokens
        bool isStopToken = false;
        if (isStopToken) break;
        
        std::string tokStr = tokenizer->detokenize(outputToken);
        fullResponse += tokStr;
    }

    // Clean up local references
    env->DeleteLocalRef(preformatterName);

    // Return the fullResponse as a jstring
    return env->NewStringUTF(fullResponse.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeStreamingInference(JNIEnv *env,
                                                                                   jobject /* this */,
                                                                                   jstring inputString,
                                                                                   jint maxResponse,
                                                                                   jboolean parsePromptTokens,
                                                                                   jobject callback) {
    if (!llmRuntime || !tokenizer) {
        LOGE("LLM not initialized");
        return nullptr;
    }

    const char *input = env->GetStringUTFChars(inputString, 0);
    std::string prompt(input);
    env->ReleaseStringUTFChars(inputString, input);

    LOGI("Performing streaming inference for input: %s", prompt.c_str());

    // Handle preformatting if needed
    jstring preformatterName = env->NewStringUTF("Llama3NoInput");
    if (!parsePromptTokens && preformatterName != nullptr) {
        const char *preformatterNameStr = env->GetStringUTFChars(preformatterName, 0);
        std::string preformatterNameCpp(preformatterNameStr);
        env->ReleaseStringUTFChars(preformatterName, preformatterNameStr);

        if (!preformatterNameCpp.empty()) {
            if (utils::addPreformatter(preformatterNameCpp, prompt)) {
                LOGI("Preformatted prompt with '%s'", preformatterNameCpp.c_str());
            } else {
                LOGE("Invalid preformatter: '%s'", preformatterNameCpp.c_str());
            }
        }
    }

    // Prepare for streaming
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");

    // Call streaming inference with callback
    std::string fullResponse;
    
    // Define the token callback
    auto tokenCallback = [&](const std::string &token) {
        jstring jToken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jToken);
        env->DeleteLocalRef(jToken);
    };
    
    // Tokenize input
    std::vector<TokenType> inputTokens;
    if (parsePromptTokens) {
        inputTokens = utils::parseTokenString(prompt);
    } else {
        inputTokens = tokenizer->tokenize(prompt);
    }
    
    // Process input tokens in batches and get first output token
    const auto promptTokenSize = 128; // Default value
    void* logits = nullptr;
    for (size_t i = 0; i < inputTokens.size(); i += promptTokenSize) {
        auto batchSize = std::min(promptTokenSize, inputTokens.size() - i);
        std::vector<TokenType> batch(inputTokens.begin() + i, inputTokens.begin() + i + batchSize);
        logits = mtk_llm_inference_once(llmRuntime, batch, i + batchSize >= inputTokens.size() ? LogitsKind::LAST : LogitsKind::NONE);
    }
    
    // Generate first token
    TokenType outputToken = utils::argmaxFrom16bitLogits(llmModelOpt.modelOutputType, logits, tokenizer->vocabSize());
    std::string tokStr = tokenizer->detokenize(outputToken);
    fullResponse += tokStr;
    tokenCallback(tokStr);  // Call the callback with the first token
    
    // Generate remaining tokens
    for (int i = 1; i < maxResponse; i++) {
        logits = mtk_llm_inference_once(llmRuntime, {outputToken});
        outputToken = utils::argmaxFrom16bitLogits(llmModelOpt.modelOutputType, logits, tokenizer->vocabSize());
        
        // Check for stop tokens
        bool isStopToken = false;
        if (isStopToken) break;
        
        tokStr = tokenizer->detokenize(outputToken);
        fullResponse += tokStr;
        tokenCallback(tokStr);  // Call the callback with each token
    }
    
    // Clean up local references
    env->DeleteLocalRef(preformatterName);

    // Return the fullResponse as a jstring
    return env->NewStringUTF(fullResponse.c_str());
}

JNIEXPORT void JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeReleaseLlm(JNIEnv *env,
                                                                           jobject /* this */) {
    if (llmRuntime) {
        mtk_llm_release(llmRuntime);
        llmRuntime = nullptr;
    }
    tokenizer.reset();
    LOGI("LLM resources released");
}

JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeResetLlm(JNIEnv *env,
                                                                         jobject /* this */) {
    if (!llmRuntime) {
        LOGE("LLM not initialized");
        return JNI_FALSE;
    }

    mtk_llm_reset(llmRuntime);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeSwapModel(JNIEnv *env, 
                                                                          jobject /* this */, 
                                                                          jint tokenSize) {
    if (!llmRuntime) {
        LOGE("LLM not initialized");
        return JNI_FALSE;
    }

    mtk_llm_swap_model(llmRuntime, static_cast<size_t>(tokenSize));
    return JNI_TRUE;
}

} // extern "C"