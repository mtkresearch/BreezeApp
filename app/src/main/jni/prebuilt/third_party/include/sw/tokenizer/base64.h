/**************************************************************************
   Copyright (c) 2023 sewenew

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 *************************************************************************/

#ifndef SEWENEW_TIKTOKEN_BASE64_H
#define SEWENEW_TIKTOKEN_BASE64_H

#include <cassert>
#include <string>
#include <string_view>
#include "sw/tokenizer/errors.h"

namespace sw::tokenizer::base64 {

std::string decode(const std::string_view &input);

namespace detail {

constexpr uint32_t DECODE_TABLE[] = {255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 62, 255, 255, 255, 63, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 255, 255, 255, 255, 255, 255, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 255, 255, 255, 255, 255, 255, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255};

inline void validate(uint32_t v) {
    if (v == 255) {
        throw Error("invalid char");
    }
}

void decode(const std::string_view &input, std::string &output) {
    assert(input.size() == 4);

    uint32_t val = 0;

    uint8_t c = input[0];
    auto v = DECODE_TABLE[c];
    validate(v);
    val = v;

    c = input[1];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    c = input[2];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    c = input[3];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    output.push_back(static_cast<char>((val >> 16) & 0xFF));
    output.push_back(static_cast<char>((val >> 8) & 0xFF));
    output.push_back(static_cast<char>(val & 0xFF));
}

void decode_1_padding(const std::string_view &input, std::string &output) {
    assert(input.size() == 3);

    uint32_t val = 0;

    uint8_t c = input[0];
    auto v = DECODE_TABLE[c];
    validate(v);
    val = v;

    c = input[1];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    c = input[2];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    output.push_back(static_cast<char>((val >> 10) & 0xFF));
    output.push_back(static_cast<char>((val >> 2) & 0xFF));
}

void decode_2_padding(const std::string_view &input, std::string &output) {
    assert(input.size() == 2);

    uint32_t val = 0;

    uint8_t c = input[0];
    auto v = DECODE_TABLE[c];
    validate(v);
    val = v;

    c = input[1];
    v = DECODE_TABLE[c];
    validate(v);
    val = (val << 6) | v;

    output.push_back(static_cast<char>((val >> 4) & 0xFF));
}

}

inline std::string decode(const std::string_view &input) {
    if (input.empty()) {
        throw Error("empty input");
    }

    // Faster than `input.size() % 4`.
    if ((input.size() & 3) != 0) {
        throw Error("input length must be multiple of 4");
    }

    assert(input.size() >= 4);

    std::string output;
    output.reserve(input.size() / 4 * 3);
    auto idx = 0U;
    for (; idx < input.size() - 4; idx += 4) {
        detail::decode(input.substr(idx, 4), output);
    }

    // Last 4 bytes. Might contain paddings.
    if (input[idx + 3] == '=') {
        if (input[idx + 2] == '=') {
            // Tow paddings.
            detail::decode_2_padding(input.substr(idx, 2), output);
        } else {
            // One padding.
            detail::decode_1_padding(input.substr(idx, 3), output);
        }
    } else {
        // No padding.
        detail::decode(input.substr(idx, 4), output);
    }

    return output;
}

}

#endif // end SEWENEW_TIKTOKEN_BASE64_H
