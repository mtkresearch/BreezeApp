#pragma once

#include <algorithm>
#include <iterator>
#include <numeric>
#include <thread>
#include <vector>

namespace mtk::llm_helper {

template <class T>
using IterValueType = typename std::iterator_traits<T>::value_type;

template <class T>
using ContainerValueType = typename std::decay_t<T>::value_type;

// Sum
template <class Iterable, class T = ContainerValueType<Iterable>>
inline T reduce_sum(const Iterable& vals) {
    return std::reduce(vals.cbegin(), vals.cend());
}

template <class InputIt, class T = IterValueType<InputIt>>
inline T reduce_sum(InputIt first, InputIt last) {
    return std::reduce(first, last);
}

// Product
template <class Iterable, class T = ContainerValueType<Iterable>>
inline std::decay_t<T> reduce_prod(const Iterable& vals, T&& init = 1) {
    return std::reduce(vals.cbegin(), vals.cend(), std::forward<T>(init), std::multiplies<>());
}

template <class InputIt, class T = IterValueType<InputIt>>
inline std::decay_t<T> reduce_prod(InputIt first, InputIt last, T&& init = 1) {
    return std::reduce(first, last, std::forward<T>(init), std::multiplies<>());
}

template <class Iterable>
inline bool allSame(const Iterable& vals) {
    auto first = vals.cbegin();
    auto last = vals.cend();
    return (std::adjacent_find(first, last, std::not_equal_to<>()) == last);
}

template <class Iterable, class UnaryOp>
inline bool allSame(const Iterable& vals, UnaryOp func) {
    auto first = vals.cbegin();
    auto last = vals.cend();

    if (first == last)
        return true;

    const auto& ref = func(*first);

    auto cur = first;
    while (++cur != last) {
        if (func(*cur) != ref)
            return false;
    }
    return true;
}

} // namespace mtk::llm_helper