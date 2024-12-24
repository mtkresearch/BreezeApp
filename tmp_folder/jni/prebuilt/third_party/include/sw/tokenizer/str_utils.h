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

#ifndef SEWENEW_TIKTOKEN_STR_UTILS_H
#define SEWENEW_TIKTOKEN_STR_UTILS_H

#include <cctype>
#include <cstring>
#include <string>
#include <string_view>

namespace sw::tokenizer::str {

template <typename Iter>
void split(const std::string &str, const std::string &delimiter, Iter result) {
    if (str.empty()) {
        return;
    }

    if (delimiter.empty()) {
        std::transform(str.begin(), str.end(), result,
                [](char c) { return std::string(1, c); });
        return;
    }

    std::string::size_type pos = 0;
    std::string::size_type idx = 0;
    while (true) {
        pos = str.find(delimiter, idx);
        if (pos == std::string::npos) {
            *result = str.substr(idx);
            ++result;
            break;
        }

        *result = str.substr(idx, pos - idx);
        ++result;
        idx = pos + delimiter.size();
    }
}

inline std::string trim(std::string line) {
    auto left_iter = line.begin();
    for (; left_iter != line.end(); ++left_iter) {
        if (!std::isspace(static_cast<unsigned char>(*left_iter))) {
            break;
        }
    }
    line.erase(line.begin(), left_iter);

    auto right_iter = line.rbegin();
    for (; right_iter != line.rend(); ++right_iter) {
        if (!std::isspace(static_cast<unsigned char>(*right_iter))) {
            break;
        }
    }
    line.erase(right_iter.base(), line.end());

    return line;
}

inline bool start_with(const std::string_view &str, const std::string_view &prefix) {
    if (str.size() < prefix.size()) {
        return false;
    }

    return std::memcmp(str.data(), prefix.data(), prefix.size()) == 0;
}

inline bool end_with(const std::string_view &str, const std::string_view &postfix) {
    if (str.size() < postfix.size()) {
        return false;
    }

    return std::memcmp(str.data() + str.size() - postfix.size(), postfix.data(), postfix.size()) == 0;
}

}

#endif // end SEWENEW_TIKTOKEN_STR_UTILS_H
