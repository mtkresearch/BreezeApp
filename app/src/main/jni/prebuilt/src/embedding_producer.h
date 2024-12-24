
#include "common/logging.h"
#include "llm_helper/include/token_embedding.h"
#include "tokenizer/tokenizer.h"

namespace mtk {

using llm_helper::TokenEmbeddingLut;

// A wrapper to unify embeddings of different modalities
class EmbeddingProducer {
public:
    explicit EmbeddingProducer(const size_t singleEmbSize, const size_t totalEmbCount)
        : kSingleEmbSize(singleEmbSize), kTotalEmbCount(totalEmbCount) {}

    virtual ~EmbeddingProducer() {}

    void setConsumer(void* buffer, const size_t size) {
        DCHECK_NE(buffer, nullptr);
        DCHECK_NE(size, 0);
        mConsumerBuffer = reinterpret_cast<char*>(buffer);
        mConsumerBufferSize = size;
    }

    // Try to produce the `count` number of embeddings, and returns the actual produced count.
    size_t produceEmbedding(const size_t count) {
        CHECK(mConsumerBuffer != nullptr && mConsumerBufferSize != 0) << "Consumer is not yet set.";

        if (mProducedCounter >= kTotalEmbCount) {
            return 0;
        }

        const auto validCount = std::min(getRemaining(), count);
        if (validCount == 0) {
            return 0;
        }

        const auto requestedSize = count * kSingleEmbSize;
        DCHECK_LE(requestedSize, mConsumerBufferSize)
            << "Consumer requested more than what it can receive.";

        // `count` is right aligned, i.e. consumer always demands its buffer to be fully filled.
        const auto writeBuffer = mConsumerBuffer + mConsumerBufferSize - requestedSize;
        const auto writeSize = validCount * kSingleEmbSize;
        produceEmbedding(validCount, writeBuffer, writeSize);

        mProducedCounter += validCount;
        return validCount;
    }

    size_t getTotalProduced() const { return mProducedCounter; }

    size_t getRemaining() const {
        DCHECK_GE(kTotalEmbCount, mProducedCounter);
        return kTotalEmbCount - mProducedCounter;
    }

    bool isEmpty() const { return getRemaining() == 0; }

private:
    virtual void produceEmbedding(const size_t count, void* writeBuffer,
                                  const size_t writeSize) = 0;

protected:
    const size_t kSingleEmbSize; // in bytes
    const size_t kTotalEmbCount;
    size_t mProducedCounter = 0;

    char* mConsumerBuffer = nullptr;
    size_t mConsumerBufferSize = 0;
};

class TextEmbeddingProducer : public EmbeddingProducer {
public:
    explicit TextEmbeddingProducer(const std::vector<Tokenizer::TokenType>& tokens,
                                   const TokenEmbeddingLut* tokenEmbLut, const size_t singleEmbSize)
        : EmbeddingProducer(singleEmbSize, tokens.size()),
          kTokens(tokens),
          kTokenEmbLut(tokenEmbLut) {}

    virtual void produceEmbedding(const size_t count, void* writeBuffer,
                                  const size_t writeSize) override {
        auto startIt = kTokens.begin() + getTotalProduced();
        auto endIt = startIt + count;
        const auto subTokens = std::vector(startIt, endIt);
        kTokenEmbLut->lookupEmbedding(subTokens, writeBuffer, writeSize);
    }

private:
    const std::vector<Tokenizer::TokenType> kTokens;
    const TokenEmbeddingLut* kTokenEmbLut;
};

class ImageEmbeddingProducer : public EmbeddingProducer {
private:
    using ImgEmbLoadFunc = std::function<void*(const std::string&)>;

public:
    explicit ImageEmbeddingProducer(const std::string& imagePath, const size_t imageTokenSize,
                                    ImgEmbLoadFunc imgEmbLoadFunc, const size_t singleEmbSize)
        : EmbeddingProducer(singleEmbSize, imageTokenSize),
          kImagePath(imagePath),
          mImageEmbLoadFunc(imgEmbLoadFunc) {}

    virtual void produceEmbedding(const size_t count, void* writeBuffer,
                                  const size_t writeSize) override {
        const auto srcBuffer = getEmbeddingBuffer();
        const auto readOffset = getTotalProduced() * kSingleEmbSize;
        std::memcpy(writeBuffer, srcBuffer + readOffset, writeSize);
    }

private:
    char* getEmbeddingBuffer() {
        // Lazy load the image embedding
        if (!mIsEmbLoaded) {
            imageEmbBuffer = mImageEmbLoadFunc(kImagePath);
            mIsEmbLoaded = true;
        }
        return reinterpret_cast<char*>(imageEmbBuffer);
    }

private:
    const std::string kImagePath;
    ImgEmbLoadFunc mImageEmbLoadFunc; // Image embedding callback function
    void* imageEmbBuffer = nullptr;
    bool mIsEmbLoaded = false;
};

} // namespace mtk