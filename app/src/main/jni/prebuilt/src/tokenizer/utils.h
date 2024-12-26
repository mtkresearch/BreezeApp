#pragma once

#include "common/comparison.h"
#include "tokenizer/tokenizer.h"

#include <algorithm>
#include <type_traits>
#include <vector>

namespace mtk {

namespace tokenizer_utils {

// If input token is of TokenType, then check if the token fits ImplTokenType value range,
// otherwise check if the token fits TokenType value range.
template <typename ImplTokenType, typename T>
inline constexpr bool isWithinRange(const T token) {
    if constexpr (std::is_same_v<Tokenizer::TokenType, ImplTokenType>) {
        // All same types, no need to check anything
        return true;
    } else if constexpr (std::is_same_v<T, Tokenizer::TokenType>) {
        // Input is TokenType, so check if it can fit ImplTokenType value range
        return (cmp::ge(token, std::numeric_limits<ImplTokenType>::min())
                && cmp::le(token, std::numeric_limits<ImplTokenType>::max()));
    } else {
        // Input is ImplTokenType, so check if it can fit TokenType value range
        return (cmp::ge(token, std::numeric_limits<Tokenizer::TokenType>::min())
                && cmp::le(token, std::numeric_limits<Tokenizer::TokenType>::max()));
    }
}

template <typename ImplTokenType, typename T>
inline constexpr bool isWithinRange(const std::vector<T>& tokens) {
    // Lambda wrapper to select the correct `isWithinRange` overload
    auto isTokenWithinRange = [](auto&& token) {
        return isWithinRange<ImplTokenType>(std::forward<decltype(token)>(token));
    };
    if constexpr (std::is_same_v<Tokenizer::TokenType, ImplTokenType>)
        return true;
    else
        return std::all_of(tokens.begin(), tokens.end(), isTokenWithinRange);
}

} // namespace tokenizer_utils
} // namespace mtk