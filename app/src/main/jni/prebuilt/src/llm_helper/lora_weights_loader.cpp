#include "llm_helper/include/lora_weights_loader.h"

#include "common/file_source.h"
#include "common/logging.h"
#include "llm_helper/include/utils.h"

#include <filesystem>
#include <fstream>
#include <numeric>
#include <string>
#include <vector>

namespace fs = std::filesystem;

namespace mtk::llm_helper {

LoraWeightsLoader::LoraWeightsLoader(const FileSource& file) : mFile(file) {
    if (!mFile.valid())
        LOG(ERROR) << "Failed to load Lora weights file: " << file;
}

size_t LoraWeightsLoader::getNumLoraInputs() {
    if (!mFile.valid())
        return 0;
    return loadHeader().numLoraInputs;
}

LoraWeightsHeader LoraWeightsLoader::loadHeader() {
    if (!mFile.valid())
        LOG(ERROR) << "Lora weights not loaded.";

    const auto [loraFileData, loraFileSize] = mFile.get();

    // Load header
    LoraWeightsHeader header;
    CHECK_LE(sizeof(LoraWeightsHeader), loraFileSize);
    std::memcpy(&header, loraFileData, sizeof(LoraWeightsHeader));

    // Check version
    if (header.version > LORA_BIN_VERSION) {
        LOG(ERROR) << "Unsupported Lora bin version: " << header.version << ". "
                   << "Supported version is <= " << LORA_BIN_VERSION;
    }
    return header;
}

std::vector<LoraWeightsLoader::SizeType> LoraWeightsLoader::loadSizes() {
    // Get number of Lora input
    const auto& header = loadHeader();
    const auto numLoraInputs = header.numLoraInputs;

    constexpr auto headerSize = sizeof(LoraWeightsHeader);

    const auto [loraFileData, loraFileSize] = mFile.get();

    // Load sizes
    std::vector<SizeType> sizes(numLoraInputs);
    const size_t copySize = sizeof(SizeType) * numLoraInputs;
    CHECK_LE(headerSize + copySize, loraFileSize);
    std::memcpy(sizes.data(), loraFileData + headerSize, copySize);
    return sizes;
}

// Load Lora weights from file to targetBuffers
void LoraWeightsLoader::loadLoraWeights(const std::vector<void*>& targetBuffers,
                                        const std::vector<size_t>& targetSizes) {
    if (!mFile.valid()) {
        LOG(ERROR) << "Lora weights not loaded.";
        return;
    }

    const auto [loraFileData, loraFileSize] = mFile.get();

    // Check number of Lora inputs
    const auto& loraInputSizes = loadSizes();
    const auto numLoraInputs = loraInputSizes.size();
    const auto numBuffers = targetBuffers.size();
    DCHECK_EQ(numBuffers, targetSizes.size());
    CHECK_EQ(numLoraInputs, numBuffers) << "Mismatch number of Lora inputs: Expected " << numBuffers
                                        << " but have " << numLoraInputs;

    // Check Lora weights size
    constexpr auto headerSize = sizeof(LoraWeightsHeader);
    const auto sizesSectionSize = sizeof(SizeType) * numLoraInputs;
    const auto loraWeightSectionOffset = headerSize + sizesSectionSize;
    CHECK_GE(loraFileSize, loraWeightSectionOffset);

    // Size based on actual file size
    const auto totalAvailSize = loraFileSize - loraWeightSectionOffset;
    // Size based on the sizes section of the bin file
    const auto totalExpectedSize = reduce_sum(loraInputSizes);
    // Size required according to the argument
    const auto totalRequiredSize = reduce_sum(targetSizes);
    CHECK_EQ(totalExpectedSize, totalAvailSize)
        << "Mismatch of Lora weights sizes available in the actual file (" << totalAvailSize << ") "
        << "and sizes described in the bin (" << totalExpectedSize << ").";
    CHECK_EQ(totalRequiredSize, totalAvailSize)
        << "Mismatch between Lora input buffer total size (" << totalRequiredSize << ") "
        << "and actual Lora weights size (" << totalAvailSize << ")";

    // Read Lora weights to target buffers
    size_t readOffset = loraWeightSectionOffset;
    for (size_t i = 0; i < numBuffers; i++) {
        auto loraInputBuffer = reinterpret_cast<char*>(targetBuffers[i]);
        const auto expectedSize = loraInputSizes[i];
        const auto requiredSize = targetSizes[i];
        CHECK_EQ(expectedSize, requiredSize)
            << "Lora input " << i << ": Expected to read " << expectedSize << " but require "
            << requiredSize << " instead.";
        const auto readSize = requiredSize;
        LOG(DEBUG) << "Reading " << i << "-th Lora weights of size " << readSize;
        DCHECK_LE(readOffset + readSize, loraFileSize);
        std::memcpy(loraInputBuffer, loraFileData + readOffset, readSize);
        readOffset += readSize;
    }
}

} // namespace mtk::llm_helper