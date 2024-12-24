#include "executor/llm_medusa_executor.h"

#include "common/logging.h"
#include "common/scope_profiling.h"

namespace mtk {

void LlmMedusaExecutor::setPosEmbed(const size_t tokenIndex) {
    // Cut the array from master
    if (tokenIndex >= kMaxTokenLength) {
        LOG(FATAL) << "Attempting to set rotaty embedding using index exceeding the supported "
                      "max token length ("
                   << kMaxTokenLength << ")";
    }
    DLOG_FUNC_LATENCY(ms)
    const auto& rotEmbInputIdxes = getRotEmbInputIdxs();
    DCHECK_EQ(rotEmbInputIdxes.size(), kRotEmbInputCount);

    auto getRotEmbInputs = [&]() {
        std::vector<void*> rotEmbInputs(kRotEmbInputCount);
        for (size_t i = 0; i < kRotEmbInputCount; i++)
            rotEmbInputs[i] = this->getInputBuffer(rotEmbInputIdxes[i]);
        return rotEmbInputs;
    };

    const bool isMedusaTreeAttn = !mMedusaTreePositions.empty();

    if (isMedusaTreeAttn) {
        CHECK_EQ(mMedusaTreePositions.size(), mModelTokenSize)
            << "Medusa tree attention is not set.";
        DCHECK_EQ(getLeftPadding(), 0);
        DCHECK_EQ(getRightPadding(), 0);
        mRotEmbMasterLut->setEmbed(getRotEmbInputs(), tokenIndex, mMedusaTreePositions);
    } else {
        mRotEmbMasterLut->setEmbed(
            getRotEmbInputs(), tokenIndex, mModelTokenSize, getLeftPadding(), getRightPadding());
    }
}

void LlmMedusaExecutor::resetTokenIndex() {
    LlmExecutorBase::resetTokenIndex();
    resetMedusaTreeAttn();
}

void LlmMedusaExecutor::setMedusaTreeAttn(const std::vector<std::vector<int>>& mask,
                                          const std::vector<size_t>& positions) {
    mMedusaTreePositions = positions;
    mMaskBuilder->setMedusaTreeMask(mask);
}

void LlmMedusaExecutor::resetMedusaTreeAttn() {
    mMedusaTreePositions.clear();
}

void LlmMedusaExecutor::rollbackTreeCache(const std::vector<size_t>& acceptedIndices) {
    DLOG_FUNC_LATENCY(ms)

    size_t firstNonSkipIdx = 0;
    for (const size_t tokenIdx : acceptedIndices) {
        if (tokenIdx == firstNonSkipIdx) {
            firstNonSkipIdx++;
        } else {
            break;
        }
    }
    if (firstNonSkipIdx == acceptedIndices.size()) {
        return; // do nothing
    }

    // View cache buffer of shape [..., mCacheLength, ...] as:
    //   [numRows, (mCacheLength, strideSizeBytes)]
    //    <----->  <----------------------------->
    //      row                   col

    const size_t strideSizeBytes = getCacheStrideSize();
    const size_t rowSize = mCacheLength * strideSizeBytes;

    auto cacheBuffers = getCacheBuffers();

    size_t cacheCounter = 0;
    for (auto cacheBuffer : cacheBuffers) {
        const size_t numRows = getCacheNumRows(cacheCounter++);
        for (size_t rowIdx = 0; rowIdx < numRows; rowIdx++) {
            auto cacheBufRow = cacheBuffer + rowIdx * rowSize; // Pointer pointing to start of row
            size_t dstTokenIdx = mCacheLength - mModelTokenSize + firstNonSkipIdx;
            for (size_t i = firstNonSkipIdx; i < acceptedIndices.size(); i++) {
                size_t tokenIdx = acceptedIndices[i];
                const size_t dstOffset = dstTokenIdx * strideSizeBytes;
                const size_t srcOffset =
                    (mCacheLength - mModelTokenSize + tokenIdx) * strideSizeBytes;
                std::memcpy(cacheBufRow + dstOffset, cacheBufRow + srcOffset, strideSizeBytes);
                dstTokenIdx += 1;
            }
        }
    }
}

} // namespace mtk