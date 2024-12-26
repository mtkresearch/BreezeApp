#pragma once

#include "common/file_source.h"

#include <fstream>
#include <string>
#include <vector>

#define LORA_BIN_VERSION 1

namespace mtk::llm_helper {

/*
  ==== Lora Bin Structure V1 ====
    Header {
      uint32_t Version,
      uint32_t NumLoraInputs (N)
    }
    LoraInputSizes {
       uint32_t nbytes_1,
       uint32_t nbytes_2,
       uint32_t nbytes_3,
       ...,
       uint32_t nbytes_N
    }
    LoraWeightsData {
      char[nbytes_1] data_1,
      char[nbytes_2] data_2,
      char[nbytes_3] data_3,
      ...,
      char[nbytes_N] data_N
    }
*/

struct __attribute__((packed)) LoraWeightsHeader {
    uint32_t version = 0;
    uint32_t numLoraInputs = 0;
};

class LoraWeightsLoader {
private:
    using SizeType = uint32_t;

public:
    explicit LoraWeightsLoader(const FileSource& file);
    ~LoraWeightsLoader() = default;

    size_t getNumLoraInputs();

    LoraWeightsHeader loadHeader();

    std::vector<SizeType> loadSizes();

    // Load Lora weights from file into targetBuffers
    void loadLoraWeights(const std::vector<void*>& targetBuffers,
                         const std::vector<size_t>& targetSizes);

private:
    FileSource mFile;
};

} // namespace mtk::llm_helper