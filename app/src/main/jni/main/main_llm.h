//
// Created by codie on 2024-12-05.
//

#ifndef LLMAPP_MAIN_LLM_H
#define LLMAPP_MAIN_LLM_H

#include <string>
#include <vector>
#include "common/dump.h"
#include "common/logging.h"
#include "common/timer.h"
#include "mtk_llm.h"
#include "tokenizer/tokenizer.h"
#include "tokenizer/tokenizer_factory.h"
#include "utils/NPUWareUtilsLib.h"
#include "utils/utils.h"

void llm_init(void** llmRuntime, const std::string& yamlConfigPath, bool preloadSharedWeights);
std::vector<mtk::Tokenizer::TokenType> llm_gen_response(void* llmRuntime, const std::unique_ptr<mtk::Tokenizer>& tokenizer,
                                                        size_t maxResponse, mtk::Tokenizer::TokenType firstInputToken,
                                                        std::string& fullResponse, double& genTokPerSec);
std::tuple<double, double> llm_inference(void* llmRuntime, const std::string& inputString, const std::unique_ptr<mtk::Tokenizer>& tokenizer, size_t maxResponse, bool parsePromptTokens);
void llm_reset(void* llmRuntime);
void llm_release(void* llmRuntime);
std::unique_ptr<mtk::Tokenizer> prepare_tokenizer();

#endif //LLMAPP_MAIN_LLM_H
