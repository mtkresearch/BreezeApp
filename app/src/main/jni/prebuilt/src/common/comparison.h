#pragma once

#include <type_traits>

// Safe comparisons betweeen types that may contain mixture between signed and unsigned types.
// Based on https://www.sandordargo.com/blog/2023/10/11/cpp20-intcmp-utilities

namespace cmp {

template <class T, class U>
constexpr bool eq(T t, U u) noexcept {
    if constexpr (std::is_signed_v<T> == std::is_signed_v<U>)
        return t == u;
    else if constexpr (std::is_signed_v<T>)
        return t >= 0 && std::make_unsigned_t<T>(t) == u;
    else
        return u >= 0 && std::make_unsigned_t<U>(u) == t;
}

template <class T, class U>
constexpr bool ne(T t, U u) noexcept {
    return !eq(t, u);
}

template <class T, class U>
constexpr bool lt(T t, U u) noexcept {
    if constexpr (std::is_signed_v<T> == std::is_signed_v<U>)
        return t < u;
    else if constexpr (std::is_signed_v<T>)
        return t < 0 || std::make_unsigned_t<T>(t) < u;
    else
        return u >= 0 && t < std::make_unsigned_t<U>(u);
}

template <class T, class U>
constexpr bool gt(T t, U u) noexcept {
    return lt(u, t);
}

template <class T, class U>
constexpr bool le(T t, U u) noexcept {
    return !lt(u, t);
}

template <class T, class U>
constexpr bool ge(T t, U u) noexcept {
    return !lt(t, u);
}

} // namespace cmp