#include "llm_helper/include/rotary_embedding.h"

#include "common/logging.h"
#include "mtk_llm_types.h"

#include <cmath>
#include <fstream>
#include <type_traits>

namespace mtk::llm_helper {

// Helper class to simplify embedding lookup logic
class RotaryEmbeddingMasterLut::LookupHelper {
public:
    explicit LookupHelper(const RotaryEmbeddingMasterLut& rotEmbMasterLut,
                          const size_t startLookupTokenIndex)
        : kRowSizeBytes(2 * rotEmbMasterLut.kHeadDim * rotEmbMasterLut.kTypeSize),
          kCosOffset(0),
          kSinOffset(kRowSizeBytes / 2),
          kSingleEmbSize(kRowSizeBytes / 2),
          kMasterLutStart(rotEmbMasterLut.mMasterLut + startLookupTokenIndex * kRowSizeBytes) {}

    size_t lookupCos() {
        mBaseOffset = kCosOffset;
        return lookup();
    }

    size_t lookupSin() {
        mBaseOffset = kSinOffset;
        return lookup();
    }

    size_t lookupCos(const size_t length) {
        mLength = length;
        return lookupCos();
    }

    size_t lookupSin(const size_t length) {
        mLength = length;
        return lookupSin();
    }

    size_t pad(const size_t length, const bool zeroize = true) {
        const auto size = kSingleEmbSize * length;
        if (zeroize) {
            std::memset(mTargetBuffer, 0, size);
        }
        mTargetBuffer += size;
        return size;
    }

    LookupHelper& setLookup(const size_t length) {
        // Use length and disable positions
        mLength = length;
        mPositions = nullptr;
        return *this;
    }

    LookupHelper& setLookup(const std::vector<size_t>& positions) {
        // Use positions and disable length
        mPositions = &positions;
        mLength = 0;
        return *this;
    }

    LookupHelper& setTarget(void* buffer) {
        mTargetBuffer = reinterpret_cast<char*>(buffer);
        return *this;
    }

private:
    size_t lookup() {
        DCHECK(mLength > 0 || mPositions != nullptr);

        auto lookupAtPos = [this](const auto srcPos, const auto dstPos) {
            // Relative `srcPos` to current token index
            const size_t srcOffset = mBaseOffset + srcPos * kRowSizeBytes;
            const size_t dstOffset = dstPos * kSingleEmbSize;
            std::memcpy(mTargetBuffer + dstOffset, kMasterLutStart + srcOffset, kSingleEmbSize);
        };

        size_t totalWritten = 0;
        if (mLength) {
            for (size_t pos = 0; pos < mLength; pos++) {
                lookupAtPos(pos, pos);
            }
            totalWritten = mLength * kSingleEmbSize;
        } else {
            size_t writePos = 0;
            for (const auto pos : *mPositions) {
                // Relative `pos` to the current token index
                lookupAtPos(pos, writePos++);
            }
            totalWritten = writePos * kSingleEmbSize;
        }

        // Advance target write pointer
        mTargetBuffer += totalWritten;
        return totalWritten;
    }

private:
    const size_t kRowSizeBytes;
    const size_t kCosOffset;
    const size_t kSinOffset;
    const size_t kSingleEmbSize;
    const char* kMasterLutStart;

    char* mTargetBuffer = nullptr;
    size_t mBaseOffset = 0;
    size_t mLength = 0;
    const std::vector<size_t>* mPositions = nullptr;
};

RotaryEmbeddingMasterLut::RotaryEmbeddingMasterLut(const LLMType rotEmbType, const size_t length,
                                                   const size_t headDim, const float rotBase,
                                                   const float ntkScale)
    : kType(rotEmbType),
      kTypeSize(getLLMTypeSize(kType)),
      kLength(length),
      kHeadDim(headDim),
      kRotBase(rotBase),
      kNtkScale(ntkScale) {
    // Shape: (length, 2*headDim), where 2 is sin & cos
    mMasterLut = new char[kLength * 2 * kHeadDim * kTypeSize];
}

RotaryEmbeddingMasterLut::~RotaryEmbeddingMasterLut() {
    delete[] mMasterLut;
}

void RotaryEmbeddingMasterLut::load(const std::string& sinMasterPath,
                                    const std::string& cosMasterPath) {
    if (sinMasterPath.size() == 0 && cosMasterPath.size() == 0) {
        generate();
        return;
    }

    LOG(DEBUG) << "Begin loading rotary embedding lookup table from provided paths.";

    std::ifstream fileCos(cosMasterPath, std::ios::binary);
    std::ifstream fileSin(sinMasterPath, std::ios::binary);

    // File paths checking
    if (!fileCos) {
        LOG(WARN) << "Rotary embedding lookup table file not found: " << cosMasterPath << ". "
                  << "Will generate rotary embedding lookup table instead.";
        generate();
        return;
    }
    if (!fileSin) {
        LOG(WARN) << "Rotary embedding lookup table file not found: " << sinMasterPath << ". "
                  << "Will generate rotary embedding lookup table instead.";
        generate();
        return;
    }

    const auto rows = kLength;
    const auto rowSize = 2 * kHeadDim * kTypeSize; // x2 for sin & cos
    const size_t cosOffset = 0;
    const size_t sinOffset = rowSize / 2; // Halfway in row because each row is [<cos><sin>]
    const auto readSize = kHeadDim * kTypeSize;

    // Read lookup table files
    for (size_t i = 0; i < rows; ++i) {
        // Read cos then sin
        fileCos.read(mMasterLut + i * rowSize + cosOffset, readSize);
        fileSin.read(mMasterLut + i * rowSize + sinOffset, readSize);
    }
    mIsReady = true;
}

// clang-format off

// For float and __fp16
template <typename RotEmbType>
void RotaryEmbeddingMasterLut::generate() {
    static_assert(std::is_same<RotEmbType, float>() || std::is_same<RotEmbType, __fp16>(),
                  "Only int16/fp16/fp32 are supported for RotEmbType");
    LOG(DEBUG) << "Generating floating rotary embedding lookup table";

    const auto rowSize = kHeadDim * 2; // x2 for sin & cos
    const size_t rotDim = kHeadDim;
    const size_t rotDimHalf = rotDim / 2;

    const float rotDimFp = static_cast<float>(kHeadDim);
    const float base = (kNtkScale == 1.0f)
                        ? kRotBase
                        : std::powf(kRotBase * kNtkScale, rotDimFp/(rotDimFp - 2.0f));

    for (int pos = 0; pos < kLength; pos++) { // row in lut
        for (int dim = 0; dim < rotDimHalf; dim++) {
            const float freq = float(pos) / std::powf(base, float(dim * 2) / rotDimFp);
            const RotEmbType embCos = static_cast<RotEmbType>(std::cos(freq));
            const RotEmbType embSin = static_cast<RotEmbType>(std::sin(freq));

            const auto& row = pos;
            const auto& col = dim; // At most kHeadDim / 2
            auto masterLutCurPtr = reinterpret_cast<RotEmbType*>(mMasterLut) + row * rowSize + col;

            // Concat Cos then Sin, and duplicate each
            // Each row looks like this:
            //   [<--cos--><--cos--><--sin--><--sin-->]
            //    |        |        |        |
            //    0    rotDimHalf   |        |
            //                    rotDim     |
            //                        rotDim + rotDimHalf
            masterLutCurPtr[0                  ] = embCos;
            masterLutCurPtr[         rotDimHalf] = embCos;
            masterLutCurPtr[rotDim             ] = embSin;
            masterLutCurPtr[rotDim + rotDimHalf] = embSin;
        }
    }
    mIsReady = true;
}

// NOTE: The difference between this and the Python script generated rotary embedding master lut
// is the rounding mechanism during quantization to INT16. Python's Numpy library uses
// round-to-even (banker's rounding) whereas the below C++ code uses round-to-nearest.
template <>
void RotaryEmbeddingMasterLut::generate<int16_t>() {
    LOG(DEBUG) << "Generating int16 rotary embedding lookup table";

    const auto rowSize = kHeadDim * 2; // x2 for sin & cos
    const size_t rotDim = kHeadDim;
    const size_t rotDimHalf = rotDim / 2;

    const float rotDimFp = static_cast<float>(kHeadDim);
    const float base = (kNtkScale == 1.0f)
                        ? kRotBase
                        : std::powf(kRotBase * kNtkScale, rotDimFp/(rotDimFp - 2.0f));

    // Minmax=(-1,1), so qscale = 1/32767
    const float qscale = 0.000030518509447574615;

    auto quantFP32ToINT16 = [&](const float fpval) -> int16_t {
        const int qmin = -32768; // -2^(outBitwidth-1)
        const int qmax = +32767; // 2^(outBitwidth-1)-1
        const int quantized = std::round(fpval / qscale);
        const int clamped = std::max(qmin, std::min(quantized, qmax));
        return clamped;
    };

    for (int pos = 0; pos < kLength; pos++) { // row in lut
        for (int dim = 0; dim < rotDimHalf; dim++) {
            const float freq = float(pos) / std::powf(base, float(dim * 2) / rotDimFp);
            const int16_t embCos = quantFP32ToINT16(std::cos(freq));
            const int16_t embSin = quantFP32ToINT16(std::sin(freq));

            const auto& row = pos;
            const auto& col = dim; // At most kHeadDim / 2
            auto masterLutCurPtr = reinterpret_cast<int16_t*>(mMasterLut) + row * rowSize + col;

            // Concat Cos then Sin, and duplicate each
            // Each row looks like this:
            //   [<--cos--><--cos--><--sin--><--sin-->]
            //    |        |        |        |
            //    0    rotDimHalf   |        |
            //                    rotDim     |
            //                        rotDim + rotDimHalf
            masterLutCurPtr[0                  ] = embCos;
            masterLutCurPtr[         rotDimHalf] = embCos;
            masterLutCurPtr[rotDim             ] = embSin;
            masterLutCurPtr[rotDim + rotDimHalf] = embSin;
        }
    }
    mIsReady = true;
}

// clang-format on

void RotaryEmbeddingMasterLut::generate() {
    switch (kType) {
        case LLMType::INT16:
            generate<int16_t>();
            return;
        case LLMType::FP16:
            generate<__fp16>();
            return;
        case LLMType::FP32:
            generate<float>();
            return;
        default:
            break;
    }
    LOG(FATAL) << "Rotary embedding generator not implemented for " << getLLMTypeName(kType);
}

// RotaryEmbeddingMasterLut supports 1 or 2 rotary embedding inputs
void RotaryEmbeddingMasterLut::setEmbed(std::vector<void*> rotEmbedBuffers, const size_t tokenIndex,
                                        const size_t modelTokenSize, const size_t leftPadLength,
                                        const size_t rightPadLength) const {
    const auto numRotEmbInputs = rotEmbedBuffers.size();
    switch (numRotEmbInputs) {
        case 1: {
            const auto rotEmbInput = rotEmbedBuffers[0];
            setEmbed(rotEmbInput, tokenIndex, modelTokenSize, leftPadLength, rightPadLength);
            break;
        }
        case 2: {
            const auto rotEmbCosInput = rotEmbedBuffers[0];
            const auto rotEmbSinInput = rotEmbedBuffers[1];
            setEmbed(rotEmbCosInput, rotEmbSinInput, tokenIndex, modelTokenSize, leftPadLength,
                     rightPadLength);
            break;
        }
        default:
            LOG(FATAL) << "RotaryEmbeddingMasterLut: Unsupported number of rotary embedding inputs "
                       << "(" << numRotEmbInputs << ").";
    }
}

void RotaryEmbeddingMasterLut::setEmbed(void* rotEmbedBuffer, const size_t tokenIndex,
                                        const size_t modelTokenSize, const size_t leftPadLength,
                                        const size_t rightPadLength) const {
    // Generate Master Lut if not yet done
    if (!mIsReady) {
        LOG(ERROR) << "Attempting to use the rotary embedding lookup table before being "
                   << "initialized.";
        return;
    }
    const auto requestedMaxIndex = tokenIndex + modelTokenSize - 1;
    const auto availableLength = getRotEmbedLength();
    if (requestedMaxIndex >= availableLength) {
        LOG(FATAL) << "Requested rotary embeddings (" << requestedMaxIndex << ") exceeds "
                   << "the max available (" << availableLength << ") in the master lookup table"
                   << ". Please ensure that your maxTokenLength option is set correctly.";
    }
    const auto padLength = leftPadLength + rightPadLength;

    DCHECK_GE(modelTokenSize, padLength);
    const size_t numValidInputToken = modelTokenSize - padLength;

    // The model takes in the rot emb as [2, modelTokenSize, kHeadDim]
    LookupHelper lookupHelper(*this, tokenIndex);

    lookupHelper.setLookup(numValidInputToken);
    lookupHelper.setTarget(rotEmbedBuffer);

    // Lookup cos
    lookupHelper.pad(leftPadLength, /*zeroize*/ false);
    lookupHelper.lookupCos();
    lookupHelper.pad(rightPadLength);

    // Lookup sin
    lookupHelper.pad(leftPadLength, /*zeroize*/ false);
    lookupHelper.lookupSin();
    lookupHelper.pad(rightPadLength);
}

void RotaryEmbeddingMasterLut::setEmbed(void* rotEmbedCosBuffer, void* rotEmbedSinBuffer,
                                        const size_t tokenIndex, const size_t modelTokenSize,
                                        const size_t leftPadLength,
                                        const size_t rightPadLength) const {
    // Generate Master Lut if not yet done
    if (!mIsReady) {
        LOG(ERROR) << "Attempting to use the rotary embedding lookup table before being "
                   << "initialized.";
        return;
    }
    const auto requestedMaxIndex = tokenIndex + modelTokenSize - 1;
    const auto availableLength = getRotEmbedLength();
    if (requestedMaxIndex >= availableLength) {
        LOG(FATAL) << "Requested rotary embeddings (" << requestedMaxIndex << ") exceeds "
                   << "the max available (" << availableLength << ") in the master lookup table"
                   << ". Please ensure that your maxTokenLength option is set correctly.";
    }
    const auto padLength = leftPadLength + rightPadLength;

    DCHECK_GE(modelTokenSize, padLength);
    const size_t numValidInputToken = modelTokenSize - padLength;

    // The model takes in the rot emb as 2 x [1, modelTokenSize, kHeadDim]
    LookupHelper lookupHelper(*this, tokenIndex);
    lookupHelper.setLookup(numValidInputToken);

    // Lookup cos
    lookupHelper.setTarget(rotEmbedCosBuffer);
    lookupHelper.pad(leftPadLength, /*zeroize*/ false);
    lookupHelper.lookupCos();
    lookupHelper.pad(rightPadLength);

    // Lookup sin
    lookupHelper.setTarget(rotEmbedSinBuffer);
    lookupHelper.pad(leftPadLength, /*zeroize*/ false);
    lookupHelper.lookupSin();
    lookupHelper.pad(rightPadLength);
}

// RotaryEmbeddingMasterLut supports 1 or 2 rotary embedding inputs
void RotaryEmbeddingMasterLut::setEmbed(std::vector<void*> rotEmbedBuffers, const size_t tokenIndex,
                                        const std::vector<size_t>& positions) const {
    const auto numRotEmbInputs = rotEmbedBuffers.size();
    switch (numRotEmbInputs) {
        case 1: {
            const auto rotEmbInput = rotEmbedBuffers[0];
            setEmbed(rotEmbInput, tokenIndex, positions);
            break;
        }
        case 2: {
            const auto rotEmbCosInput = rotEmbedBuffers[0];
            const auto rotEmbSinInput = rotEmbedBuffers[1];
            setEmbed(rotEmbCosInput, rotEmbSinInput, tokenIndex, positions);
            break;
        }
        default:
            LOG(FATAL) << "RotaryEmbeddingMasterLut: Unsupported number of rotary embedding inputs "
                       << "(" << numRotEmbInputs << ").";
    }
}

void RotaryEmbeddingMasterLut::setEmbed(void* rotEmbedBuffer, const size_t tokenIndex,
                                        const std::vector<size_t>& positions) const {
    // Generate Master Lut if not yet done
    if (!mIsReady) {
        LOG(ERROR) << "Attempting to use the rotary embedding lookup table before being "
                   << "initialized.";
        return;
    }
    const auto requestedMaxIndex =
        tokenIndex + *std::max_element(positions.begin(), positions.end());
    const auto availableLength = getRotEmbedLength();
    if (requestedMaxIndex >= availableLength) {
        LOG(FATAL) << "Requested rotary embeddings (" << requestedMaxIndex << ") exceeds "
                   << "the max available (" << availableLength << ") in the master lookup table"
                   << ". Please ensure that your maxTokenLength option is set correctly.";
    }

    // The model takes in the rot emb as [2, positions.size(), kHeadDim]
    LookupHelper lookupHelper(*this, tokenIndex);

    lookupHelper.setLookup(positions);
    lookupHelper.setTarget(rotEmbedBuffer);
    lookupHelper.lookupCos();
    lookupHelper.lookupSin();
}

void RotaryEmbeddingMasterLut::setEmbed(void* rotEmbedCosBuffer, void* rotEmbedSinBuffer,
                                        const size_t tokenIndex,
                                        const std::vector<size_t>& positions) const {
    // Generate Master Lut if not yet done
    if (!mIsReady) {
        LOG(ERROR) << "Attempting to use the rotary embedding lookup table before being "
                   << "initialized.";
        return;
    }
    const auto requestedMaxIndex =
        tokenIndex + *std::max_element(positions.begin(), positions.end());
    const auto availableLength = getRotEmbedLength();
    if (requestedMaxIndex >= availableLength) {
        LOG(FATAL) << "Requested rotary embeddings (" << requestedMaxIndex << ") exceeds "
                   << "the max available (" << availableLength << ") in the master lookup table"
                   << ". Please ensure that your maxTokenLength option is set correctly.";
    }

    // The model takes in the rot emb as 2 x [1, positions.size(), kHeadDim]
    LookupHelper lookupHelper(*this, tokenIndex);
    lookupHelper.setLookup(positions);

    lookupHelper.setTarget(rotEmbedCosBuffer);
    lookupHelper.lookupCos();

    lookupHelper.setTarget(rotEmbedSinBuffer);
    lookupHelper.lookupSin();
}

size_t RotaryEmbeddingMasterLut::getRotEmbedSizeBytes(const size_t modelTokenSize) const {
    return 2 * modelTokenSize * kHeadDim * kTypeSize;
}

// The rotary embedding length is and determines the largest token size the model can handle
size_t RotaryEmbeddingMasterLut::getRotEmbedLength() const {
    return kLength;
}

} // namespace mtk::llm_helper