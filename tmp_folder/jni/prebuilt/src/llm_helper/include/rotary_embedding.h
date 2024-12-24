#pragma once

#include "mtk_llm_types.h"

#include <string>
#include <vector>

namespace mtk::llm_helper {

class RotaryEmbeddingMasterLut {
public:
    RotaryEmbeddingMasterLut(const LLMType rotEmbType, const size_t length, const size_t headDim,
                             const float rotBase = 10000.0, const float ntkScale = 1.0);

    virtual ~RotaryEmbeddingMasterLut();

    void load(const std::string& sinMasterPath, const std::string& cosMasterPath);

    void generate();

    template <typename RotEmbType>
    void generate();

    virtual void setEmbed(std::vector<void*> rotEmbedBuffers, const size_t tokenIndex,
                          const size_t modelTokenSize = 1, const size_t leftPadLength = 0,
                          const size_t rightPadLength = 0) const;

    // Single rot emb input with combined cos & sin
    void setEmbed(void* rotEmbedBuffer, const size_t tokenIndex, const size_t modelTokenSize = 1,
                  const size_t leftPadLength = 0, const size_t rightPadLength = 0) const;

    // Two rot emb inputs for separated cos & sin
    void setEmbed(void* rotEmbedCosBuffer, void* rotEmbedSinBuffer, const size_t tokenIndex,
                  const size_t modelTokenSize = 1, const size_t leftPadLength = 0,
                  const size_t rightPadLength = 0) const;

    virtual void setEmbed(std::vector<void*> rotEmbedBuffers, const size_t tokenIndex,
                          const std::vector<size_t>& positions) const;

    // Single rot emb input with combined cos & sin
    void setEmbed(void* rotEmbedBuffer, const size_t tokenIndex,
                  const std::vector<size_t>& positions) const;

    // Two rot emb inputs for separated cos & sin
    void setEmbed(void* rotEmbedCosBuffer, void* rotEmbedSinBuffer, const size_t tokenIndex,
                  const std::vector<size_t>& positions) const;

    size_t getRotEmbedSizeBytes(const size_t modelTokenSize = 1) const;

    // The rotary embedding length is and determines the largest token size the model can handle
    size_t getRotEmbedLength() const;

protected:
    class LookupHelper;
    friend class LookupHelper;

private:
    char* mMasterLut; // byte flatten array
    bool mIsReady = false;

    const LLMType kType;
    const size_t kTypeSize; // in bytes
    const size_t kLength;
    const size_t kHeadDim;
    const float kRotBase = 10000.0;
    const float kNtkScale;
};

} // namespace mtk::llm_helper