#include "llm_helper/include/mask_builder.h"

#include "common/logging.h"

namespace mtk::llm_helper {

// Define mask values for different types
template <typename T>
struct MaskVal;

#define __DECL_MASK__(TYPE, TRUE_VAL, FALSE_VAL)  \
    template <>                                   \
    struct MaskVal<TYPE> {                        \
        static constexpr TYPE kTrue = TRUE_VAL;   \
        static constexpr TYPE kFalse = FALSE_VAL; \
    };

__DECL_MASK__(bool, true, false)
__DECL_MASK__(int16_t, 0, -32768)
__DECL_MASK__(__fp16, 0, -100)
__DECL_MASK__(float, 0, -100)
#undef __DECL_MASK__

MaskBuilder::MaskBuilder(void* maskBuffer, const size_t maskSizeBytes, const LLMType maskType,
                         const size_t cacheLength)
    : mMaskBuffer(maskBuffer),
      mMaskSizeBytes(maskSizeBytes),
      kMaskType(maskType),
      kMaskTypeSize(getLLMTypeSize(maskType)),
      mCacheLength(cacheLength) {}

MaskBuilder::~MaskBuilder() {}

void MaskBuilder::updateMaskSize(const size_t sizeBytes) {
    mMaskSizeBytes = sizeBytes;
}

void MaskBuilder::updateCacheLength(const size_t cacheLength) {
    mCacheLength = cacheLength;
}

void MaskBuilder::markMaskDirty() {
    mIsMaskUpdatable = false;
}

template <typename MaskType>
void MaskBuilder::buildMask(const size_t modelTokenSize, const size_t numSeenToken) {
    constexpr auto maskTrue = MaskVal<MaskType>::kTrue;
    constexpr auto maskFalse = MaskVal<MaskType>::kFalse;
    const size_t maskLength = mCacheLength + modelTokenSize;

    // The mask is a combination (concat) of input cache mask and attention mask
    const size_t startTrueIdx = mCacheLength - std::min(mCacheLength, numSeenToken);

    const size_t rowSize = mMaskSizeBytes / modelTokenSize / kMaskTypeSize;

    const size_t expectedMaskSizeBytes = modelTokenSize * maskLength * kMaskTypeSize;
    // Use '<' instead of '!=' because mMaskSizeBytes may be padded by compiler to fit HW
    if (mMaskSizeBytes < expectedMaskSizeBytes) {
        LOG(WARN) << "Model input mask size (" << mMaskSizeBytes << ") < mask size to be built ("
                  << expectedMaskSizeBytes
                  << "). Please ensure your model options are set correctly.";
    }

    if (isMedusaTreeAttn()) {
        DCHECK_EQ(mLeftPadLength, 0)
            << "For medusa inference, tree-candidate length must align with genTokenSize.";
        DCHECK_EQ(mRightPadLength, 0)
            << "For medusa inference, tree-candidate length must align with genTokenSize.";
    }

    if (isFoldedGenBatchMode()) {
        // Token size is interpreted as batch size
        const auto& foldedBatchSize = modelTokenSize;
        DCHECK_EQ(mLeftPadLength + mRightPadLength, 0); // Padding is not allowed in this mode
        // There are modelTokenSize number of rows
        for (size_t inTokIdx = 0; inTokIdx < modelTokenSize; inTokIdx++) {
            const auto& rowIdx = inTokIdx; // For clarity
            auto curMaskBuffer = reinterpret_cast<MaskType*>(mMaskBuffer) + rowIdx * rowSize;
            size_t i = 0; // Buffer write index

            // Set the (rectangle) input cache mask as True for prompt tokens
            while (i < startTrueIdx)
                curMaskBuffer[i++] = maskFalse;

            while (i < startTrueIdx + mGenBatchNumPromptTokens)
                curMaskBuffer[i++] = maskTrue;

            // Set the identity matrices at the right to interpret token size as batch size
            size_t batchIdx = 0;
            while (i < maskLength) {
                curMaskBuffer[i++] = (batchIdx == inTokIdx) ? maskTrue : maskFalse;
                batchIdx = (batchIdx + 1) % foldedBatchSize;
            }
            DCHECK_EQ(batchIdx, 0)
                << "Please ensure the cache size is sufficient for gen batch mode.";
        }
        mIsMaskUpdatable = false; // Disable mask update
        return;
    }

    // There are modelTokenSize number of rows
    for (size_t inTokIdx = 0; inTokIdx < modelTokenSize; inTokIdx++) {
        const auto& rowIdx = inTokIdx; // For clarity
        auto curMaskBuffer = reinterpret_cast<MaskType*>(mMaskBuffer) + rowIdx * rowSize;
        size_t i = 0; // Buffer write index

        // Set the (rectangle) input cache mask
        while (i < startTrueIdx)
            curMaskBuffer[i++] = maskFalse;

        while (i < mCacheLength)
            curMaskBuffer[i++] = maskTrue;

        if (!isMedusaTreeAttn()) {
            // Set the (triangle) attention mask
            const size_t attnTrueCount = inTokIdx + 1;
            for (size_t counter = 0; counter < attnTrueCount; counter++) {
                curMaskBuffer[i++] = maskTrue;
            }
            // Fill the remaining with False
            while (i < maskLength)
                curMaskBuffer[i++] = maskFalse;
        } else {
            // Medusa mask
            for (const auto medusaMaskVal : mMedusaTreeMask[rowIdx]) {
                if (medusaMaskVal == 1)
                    curMaskBuffer[i++] = maskTrue;
                else
                    curMaskBuffer[i++] = maskFalse;
            }
            DCHECK_EQ(i, maskLength);
        }
    }

    // Modify mask for padding if needed. Mask is not updatable if modified for padding.
    mIsMaskUpdatable = !adjustMaskForPadding<MaskType>(modelTokenSize);
}

template <typename MaskType>
void MaskBuilder::updateMask(const size_t modelTokenSize, const size_t numSeenToken,
                             const size_t length) {
    if (!mIsMaskUpdatable) {
        buildMask<MaskType>(modelTokenSize, numSeenToken);
        return;
    }

    // The mask is a combination (concat) of input cache mask and attention mask
    auto maskBuffer = reinterpret_cast<MaskType*>(mMaskBuffer);

    const size_t rowSize = mMaskSizeBytes / modelTokenSize / kMaskTypeSize;
    const size_t startTrueOffset = mCacheLength - std::min(mCacheLength, numSeenToken);

    // Only modify the left rectangle part
    for (size_t inTokIdx = 0; inTokIdx < modelTokenSize; inTokIdx++) {
        const auto& rowIdx = inTokIdx; // For clarity
        auto curMaskBuffer = maskBuffer + rowIdx * rowSize + startTrueOffset;
        const size_t trueCount = std::min(length, numSeenToken); // Can only True for seen token
        std::fill(curMaskBuffer, curMaskBuffer + trueCount, MaskVal<MaskType>::kTrue);
    }
    // Modify mask for padding if needed. Mask is not updatable if modified for padding.
    mIsMaskUpdatable = !adjustMaskForPadding<MaskType>(modelTokenSize);
}

void MaskBuilder::buildMask(const size_t modelTokenSize, const size_t numSeenToken) {
    switch (kMaskType) {
        case LLMType::INT16:
            buildMask<int16_t>(modelTokenSize, numSeenToken);
            return;
        case LLMType::FP16:
            buildMask<__fp16>(modelTokenSize, numSeenToken);
            return;
        case LLMType::FP32:
            buildMask<float>(modelTokenSize, numSeenToken);
            return;
        default:
            break;
    }
    LOG(FATAL) << "Attempting to build mask with type " << getLLMTypeName(kMaskType) << ". "
               << "Supported types are INT16, FP16, FP32.";
}

void MaskBuilder::updateMask(const size_t modelTokenSize, const size_t numSeenToken,
                             const size_t length) {
    switch (kMaskType) {
        case LLMType::INT16:
            updateMask<int16_t>(modelTokenSize, numSeenToken, length);
            return;
        case LLMType::FP16:
            updateMask<__fp16>(modelTokenSize, numSeenToken, length);
            return;
        case LLMType::FP32:
            updateMask<float>(modelTokenSize, numSeenToken, length);
            return;
        default:
            break;
    }
    LOG(FATAL) << "Attempting to update with an unsupported mask type. "
               << "Supported types are INT16, FP16, FP32.";
}

void MaskBuilder::notifyLeftPadding(const size_t padLength) {
    CHECK_EQ(mRightPadLength, 0) << "Attempting to set left pad after right pad has been set.";
    if (mLeftPadLength > 0) {
        LOG(WARN) << "Calling notifyLeftPadding() multiple times before building/updating mask.";
    }
    CHECK(padLength == 0 || !isFoldedGenBatchMode())
        << "Padding is not supported in folded gen batch mode.";
    mLeftPadLength = padLength;
}

void MaskBuilder::notifyRightPadding(const size_t padLength) {
    CHECK_EQ(mLeftPadLength, 0) << "Attempting to set right pad after left pad has been set.";
    if (mRightPadLength > 0) {
        LOG(WARN) << "Calling notifyRightPadding() multiple times before building/updating mask.";
    }
    CHECK(padLength == 0 || !isFoldedGenBatchMode())
        << "Padding is not supported in folded gen batch mode.";
    mRightPadLength = padLength;
}

template <typename MaskType>
bool MaskBuilder::adjustMaskForPadding(const size_t modelTokenSize) {
    if (mLeftPadLength + mRightPadLength == 0) {
        return false; // No need to modify mask since no padding
    }
    DCHECK(mLeftPadLength == 0 || mRightPadLength == 0)
        << "Only allow setting either left or right pad";
    constexpr auto maskFalse = MaskVal<MaskType>::kFalse;
    const size_t maskLength = mCacheLength + modelTokenSize;

    // The mask is a combination (concat) of input cache mask and attention mask
    auto maskBuffer = reinterpret_cast<MaskType*>(mMaskBuffer);

    const size_t rowSize = mMaskSizeBytes / modelTokenSize / kMaskTypeSize;

    if (mLeftPadLength > 0) {
        // Mask the padded rows
        for (size_t inTokIdx = 0; inTokIdx < mLeftPadLength; inTokIdx++) {
            auto curMaskBuffer = maskBuffer + inTokIdx * rowSize;
            std::fill(curMaskBuffer, curMaskBuffer + maskLength, maskFalse);
        }
        // Mask the padded attention region
        for (size_t inTokIdx = mLeftPadLength; inTokIdx < modelTokenSize; inTokIdx++) {
            auto curMaskBuffer = maskBuffer + inTokIdx * rowSize + mCacheLength;
            // Anything from inTokIdx + 1 onwards is already False, so can skip them.
            const size_t maskPadCount = std::min(mLeftPadLength, inTokIdx + 1);
            std::fill(curMaskBuffer, curMaskBuffer + maskPadCount, maskFalse);
        }
        mLeftPadLength = 0; // Reset pad length
    } else if (mRightPadLength > 0) {
        // Mask the padded rows
        const auto startIdx = modelTokenSize - mRightPadLength;
        for (size_t inTokIdx = startIdx; inTokIdx < modelTokenSize; inTokIdx++) {
            auto curMaskBuffer = maskBuffer + inTokIdx * rowSize;
            std::fill(curMaskBuffer, curMaskBuffer + maskLength, maskFalse);
        }
        mRightPadLength = 0; // Reset pad length
    }
    return true; // Mask is modified for padding
}

void MaskBuilder::setMedusaTreeMask(const std::vector<std::vector<int>>& mask) {
    mMedusaTreeMask = mask;
}

void MaskBuilder::enterFoldedGenBatchMode(const size_t numPromptTokens) {
    DCHECK_GT(numPromptTokens, 0);
    mGenBatchNumPromptTokens = numPromptTokens;
}

void MaskBuilder::reset() {
    markMaskDirty();
    mMedusaTreeMask.clear();
    mGenBatchNumPromptTokens = 0;
}

} // namespace mtk::llm_helper