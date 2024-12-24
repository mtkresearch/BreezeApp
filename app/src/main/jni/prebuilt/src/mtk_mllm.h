#pragma once

#include "mtk_llm.h"

#include <iostream>
#include <string>
#include <vector>

constexpr int32_t kImagePlaceholderToken = -200; // Placeholder image token

struct MllmRuntimeOptions : public LlmRuntimeOptions {
    FileSource clipFile;
    FileSource patchEmbFile;
    size_t imageTokenSize = 576;
};

bool mtk_mllm_init(void** runtime, const LlmModelOptions& modelOptions,
                   const MllmRuntimeOptions& runtimeOptions,
                   const SharedWeightsHandle* preloadedSharedWeights = nullptr);

void* mtk_mllm_inference_once(void* runtime, const size_t leftPadSize = 0,
                              const size_t rightPadSize = 0, const void* inputEmb = nullptr,
                              const LogitsKind outputKind = LogitsKind::LAST);

void* mtk_mllm_consume_prompt(void* runtime, const std::vector<mtk::Tokenizer::TokenType>& tokens,
                              const std::vector<std::string>& imagePaths, size_t* numPromptToken,
                              const LogitsKind outputKind = LogitsKind::LAST);

void* mtk_mllm_consume_emb(void* runtime, const char* embBuffer, const size_t embBufferSize,
                           const LogitsKind outputKind = LogitsKind::LAST);

size_t mtk_mllm_get_token_index(void* runtime);

void mtk_mllm_rollback(void* runtime, const size_t rollbackCount);

void* mtk_mllm_get_text_embedding(void* runtime,
                                  const std::vector<mtk::Tokenizer::TokenType>& inputTokens,
                                  void* inputTextEmbCopy = nullptr);

void* mtk_mllm_get_clip_embedding(void* runtime, void* imageBuffer, const size_t imageBufferSize);

void mtk_mllm_reset(void* runtime, const bool resetCache = true);

void mtk_mllm_release(void* runtime);

size_t mtk_mllm_get_per_token_logits_size(void* runtime);

size_t mtk_mllm_get_input_emb_size_bytes(void* runtime);