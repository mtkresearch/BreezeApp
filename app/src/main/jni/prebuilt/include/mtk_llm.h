#pragma once

#include "common/file_source.h"
#include "mtk_llm_types.h"
#include "tokenizer/tokenizer.h"

#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

// clang-format off

struct LlmModelOptions {
    // Sizes
    size_t genModelBatchSize    = 1; // Currently batch inference is only supported by gen mode
    size_t promptTokenBatchSize = 1;
    size_t genTokenBatchSize    = 1;
    size_t cacheSize            = 512;
    size_t hiddenSize           = 4096;
    size_t numHead              = 32;
    size_t numLayer             = 32;
    size_t maxTokenLength       = 2048;
    size_t numMedusaHeads       = 0;

    // Rotary Embedding
    float rotEmbBase            = 10000.0;
    float ntkScale              = 1.0;

    // Types
    mtk::LLMType modelInputType  = mtk::LLMType::INT16;
    mtk::LLMType modelOutputType = mtk::LLMType::INT16;
    mtk::LLMType cacheType       = mtk::LLMType::INT16;
    mtk::LLMType maskType        = mtk::LLMType::INT16;
    mtk::LLMType rotEmbType      = mtk::LLMType::INT16;

    // Quantization
    float embOutputQuantScale   = 0; // default 0 means not used
    float modelOutputQuantScale = 1;
};

using LoraKey = std::string;
using ModelConfig = std::string; // Example "128t1024c"
using ChunkFiles = std::vector<FileSource>;
using TokenSet = std::unordered_set<mtk::Tokenizer::TokenType>;

struct LlmRuntimeOptions {
    struct SpecialTokens {
        mtk::Tokenizer::TokenType bosId = 1; // Beginning of Sentence Token Id
        mtk::Tokenizer::TokenType eosId = 2; // End of Sentence Token Id
        bool addBos = false; // Whether BoS token will be prepended during tokenization
        TokenSet stopToken;  // Inference stops once the model generates a stop token
    } specialTokens;
    std::string tokenizerRegex;             // Optional
    std::vector<std::string> tokenizerPath; // Either a directory or file path(s)
    FileSource tokenEmbFile;
    std::unordered_map<ModelConfig, ChunkFiles> dlaFiles;
    FileSource dlaLmHeadFile;
    FileSource dlaMedusaHeadsFile;
    int startTokenIndex = 0;
    ChunkFiles cacheFiles; // Each file is a concatenation of all caches in a chunk.
    ChunkFiles sharedWeightsFiles;

    LoraKey initWithLoraKey;
    size_t loraInputCount = 0; // Per DLA chunk
    std::unordered_map<LoraKey, ChunkFiles> loraWeightsFiles;
};

// clang-format on

template <typename T>
using Batched = std::vector<T>;

enum class LogitsKind {
    NONE,
    LAST, // Last logits
    FULL  // Full logits
};

struct SharedWeightsHandle;

void mtk_llm_preload_shared_weights(SharedWeightsHandle** sharedWeightsHandle,
                                    const LlmRuntimeOptions& runtimeOptions);

void mtk_llm_free_preloaded_shared_weights(SharedWeightsHandle* sharedWeightsHandle);

bool mtk_llm_init(void** runtime, const LlmModelOptions& modelOptions,
                  const LlmRuntimeOptions& runtimeOptions,
                  const SharedWeightsHandle* preloadedSharedWeights = nullptr);

void mtk_llm_release(void* runtime);

void mtk_llm_set_medusa_tree_attn(void* runtime, const std::vector<std::vector<int>>& mask,
                                  const std::vector<size_t>& positions);

void mtk_llm_use_prompt_as_batch_gen(void* runtime);

void* mtk_llm_inference_once(void* runtime,
                             const std::vector<mtk::Tokenizer::TokenType>& inputTokens,
                             const LogitsKind outputKind = LogitsKind::LAST);

Batched<void*>
mtk_llm_inference_batch(void* runtime,
                        const Batched<std::vector<mtk::Tokenizer::TokenType>>& batchInputTokens,
                        const LogitsKind outputKind = LogitsKind::LAST);

std::tuple<void*, void*>
mtk_llm_inference_once_return_hidden(void* runtime,
                                     const std::vector<mtk::Tokenizer::TokenType>& inputTokens,
                                     const LogitsKind outputKind = LogitsKind::LAST);

void* neuron_medusa_heads_inference_once(void* runtime, void* hiddenState);

void mtk_llm_swap_model(void* runtime, const size_t tokenSize = 1, const size_t cacheSize = 0);

size_t mtk_llm_advance_cache_size(void* runtime);

void mtk_llm_apply_lora(void* runtime, const LoraKey& loraKey);

void mtk_llm_apply_lora_from_buffer(void* runtime,
                                    const std::vector<const char*>& loraWeightBuffers,
                                    const std::vector<size_t>& sizes);

void mtk_llm_remove_lora(void* runtime);

void mtk_llm_get_caches(void* runtime, std::vector<std::vector<char*>>& caches,
                        size_t& byteSizePerCache);

void mtk_llm_reset(void* runtime, const bool resetCache = true);

size_t mtk_llm_get_per_token_logits_size(void* runtime);

size_t mtk_llm_get_per_token_hidden_states_size(void* runtime);

size_t mtk_llm_get_token_index(void* runtime);

void mtk_llm_rollback(void* runtime, const size_t rollbackCount);

void mtk_llm_medusa_rollback(void* runtime, const std::vector<size_t>& acceptedIndices);
