#pragma once

#include "common/file_source.h"
#include "mtk_llm_types.h"
#include "tokenizer/tokenizer.h"

#include <string>
#include <vector>

namespace mtk::llm_helper {

class TokenEmbeddingLut {
public:
    TokenEmbeddingLut(FileSource tokenEmbLutFile, const LLMType tokenEmbLutType,
                      const size_t hiddenSize);

    void setOutput(void* buffer, const size_t size);

    // Lookup embedding from the lookup-table and write to the output buffer set by `setOutput()`.
    // NOTE: The lookup-table value type must match the model input type.
    void lookupEmbedding(const std::vector<Tokenizer::TokenType>& tokens) const;

    // Lookup embedding from the lookup-table and write to the given output buffer.
    // NOTE: The lookup-table value type must match the model input type.
    void lookupEmbedding(const std::vector<Tokenizer::TokenType>& tokens, void* buffer,
                         const size_t size) const;

    size_t getEmbSizeBytes() const { return kLutRowSizeBytes; }

private:
    // Source lookup table
    std::unique_ptr<char[]> mLutBuffer;
    const size_t kTokenEmbLutTypeSize;
    const size_t kHiddenSize;
    const size_t kLutRowSizeBytes;
    size_t mVocabSize;

    // Output write buffer
    char* mOutputBuffer = nullptr;
    size_t mOutputBufferSize = 0;
};

} // namespace mtk::llm_helper