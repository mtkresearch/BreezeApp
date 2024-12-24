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

#ifndef SEWENEW_TIKTOKEN_TOML_H
#define SEWENEW_TIKTOKEN_TOML_H

#include <map>
#include <fstream>
#include <string>
#include <type_traits>
#include <variant>
#include <vector>
#include "sw/tokenizer/str_utils.h"
#include "sw/tokenizer/errors.h"

namespace sw::tokenizer {

namespace detail {

template <typename T, typename = std::void_t<>>
struct HasPushBack : std::false_type {};

template <typename T>
struct HasPushBack<T,
    typename std::enable_if<
        std::is_void<decltype(
            std::declval<T>().push_back(std::declval<typename T::value_type>())
                )>::value>::type> : std::true_type {};

template <typename T, typename = std::void_t<>>
struct HasInsert : std::false_type {};

template <typename T>
struct HasInsert<T,
    typename std::enable_if<
        std::is_same<
            decltype(std::declval<T>().insert(std::declval<typename T::const_iterator>(),
                                                std::declval<typename T::value_type>())),
            typename T::iterator>::value>::type> : std::true_type {};

template <typename T>
struct IsSequenceContainer
    : std::integral_constant<bool,
        HasPushBack<T>::value
            && !std::is_same<typename std::decay<T>::type, std::string>::value> {};

template <typename T>
struct IsAssociativeContainer
    : std::integral_constant<bool,
        HasInsert<T>::value && !HasPushBack<T>::value> {};

}

class Toml {
private:
    using Object = std::map<std::string, Toml>;
    using Array = std::vector<Toml>;
    using Value = std::variant<std::monostate, Object, Array, std::string, bool, double, long long, unsigned long long>;

    class Parser {
    public:
        Parser() = default;

        Toml parse(std::istream &input) {
            Toml root;
            root._value = Object{};
            Toml *cur = &root;
            std::string line;
            while (std::getline(input, line)) {
                line = str::trim(line);
                if (line.empty() || line.front() == '#') {
                    continue;
                }

                auto [key, value] = _parse_line(line);

                if (!std::holds_alternative<std::monostate>(value._value)) {
                    auto &obj = std::get<Object>(cur->_value);
                    obj[key] = std::move(value);
                    continue;
                }

                std::vector<std::string> keys;
                str::split(key, ".", std::back_inserter(keys));
                assert(!keys.empty());

                cur = &root;
                for (const auto &k : keys) {
                    auto &obj = std::get<Object>(cur->_value);
                    auto iter = obj.find(k);
                    if (iter == obj.end()) {
                        obj[k]._value = Object{};
                    } else {
                        if (!std::holds_alternative<Object>(iter->second._value)) {
                            throw Error("invalid");
                        }
                    }
                    cur = &(obj[k]);
                }
            }

            return root;
        }

    private:
        std::pair<std::string, std::string> _split_kv(const std::string &line) const {
            auto pos = line.find("=");
            if (pos == std::string::npos) {
                throw Error("not a kv pair: " + line);
            }

            auto key = str::trim(line.substr(0, pos));
            if ((str::start_with(key, "'") && str::end_with(key, "'"))
                    || (str::start_with(key, "\"") && str::end_with(key, "\""))) {
                key = str::trim(key.substr(1, key.size() - 2));
            }

            auto value = str::trim(line.substr(pos + 1));

            return {std::move(key), std::move(value)};
        }

        std::pair<std::string, Toml> _parse_line(std::string line) const {
            if (line.front() == '[' && line.back() == ']') {
                auto key = str::trim(line.substr(1, line.size() - 2));
                if (key.empty()) {
                    throw Error("invalid line: " + line);
                }

                return {std::move(key), Toml{}};
            } else {
                auto [key, val] = _split_kv(line);

                return {std::move(key), _parse(val)};
            }
        }

        Toml _parse(std::string line) const {
            line = str::trim(line);
            if (line.empty()) {
                throw Error("invalid line");
            }

            if (line.size() >= 6 && str::start_with(line, "'''") && str::end_with(line, "'''")) {
                Toml value;
                value._value = line.substr(3, line.size() - 6);
                return value;
            }

            if (line.size() >= 2 && str::start_with(line, "[") && str::end_with(line, "]")) {
                return _parse_array(line.substr(1, line.size() - 2));
            }

            if (line.size() >= 2 && str::start_with(line, "{") && str::end_with(line, "}")) {
                return _parse_object(line.substr(1, line.size() - 2));
            }

            if (line.size() >= 2 && str::start_with(line, "'") && str::end_with(line, "'")) {
                Toml value;
                value._value = line.substr(1, line.size() - 2);
                return value;
            }

            if (line.size() >= 2 && str::start_with(line, "\"") && str::end_with(line, "\"")) {
                Toml value;
                value._value = line.substr(1, line.size() - 2);
                return value;
            }

            if (line == "true") {
                Toml value;
                value._value = true;
                return value;
            }

            if (line == "false") {
                Toml value;
                value._value = false;
                return value;
            }

            if (line.find(".") != std::string::npos) {
                try {
                    auto d = std::stod(line);
                    Toml value;
                    value._value = d;
                    return value;
                } catch (const std::exception &) {
                    throw Error("not a valid double");
                }
            }

            if (str::start_with(line, "-")) {
                try {
                    auto i = std::stoll(line);
                    Toml value;
                    value._value = i;
                    return value;
                } catch (const std::exception &) {
                    throw Error("not a valid signed integer");
                }
            }

            try {
                auto i = std::stoull(line);
                Toml value;
                value._value = i;
                return value;
            } catch (const std::exception &e) {
                throw Error("not a valid integer");
            }

            throw Error("invalid value");
        }

        Toml _parse_array(std::string line) const {
            std::vector<std::string> items;
            str::split(line, ",", std::back_inserter(items));
            std::vector<Toml> result;
            for (auto &item : items) {
                result.push_back(_parse(item));
            }
            Toml value;
            value._value = std::move(result);
            return value;
        }

        Toml _parse_object(std::string line) const {
            std::vector<std::string> items;
            str::split(line, ",", std::back_inserter(items));
            std::map<std::string, Toml> result;
            for (auto &item : items) {
                auto [key, value] = _split_kv(item);

                result.emplace(std::move(key), _parse(value));
            }
            Toml value;
            value._value = std::move(result);
            return value;
        }
    };

public:
    static Toml parse(const std::string &path) {
        std::ifstream file(path);
        if (!file) {
            throw Error("failed to open file: " + path);
        }

        return Parser{}.parse(file);
    }

    Toml() : _value(std::monostate{}) {}

    bool is_null() const {
        return std::holds_alternative<std::monostate>(_value);
    }

    template <typename T, typename std::enable_if<detail::IsSequenceContainer<T>::value, int>::type = 0>
    T get() const {
        T container;
        if (const auto *p = std::get_if<Array>(&_value)) {
            for (const auto &ele : *p) {
                container.push_back(ele.get<typename T::value_type>());
            }
        } else {
            throw Error("type mismatch");
        }

        return container;
    }

    template <typename T, typename std::enable_if<detail::IsAssociativeContainer<T>::value, int>::type = 0>
    T get() const {
        T container;
        if (const auto *p = std::get_if<Object>(&_value)) {
            for (const auto &[key, val] : *p) {
                container.emplace(key, val.template get<typename T::mapped_type>());
            }
        } else {
            throw Error("type mismatch");
        }

        return container;
    }

    template <typename T, typename std::enable_if<std::is_same<typename std::decay<T>::type, std::string>::value, int>::type = 0>
    T get() const {
        if (const auto *p = std::get_if<std::string>(&_value)) {
            return *p;
        } else {
            throw Error("type mismatch");
        }
    }

    template <typename T, typename std::enable_if<std::is_signed<typename std::decay<T>::type>::value, int>::type = 0>
    T get() const {
        if (const auto *p = std::get_if<long long>(&_value)) {
            return static_cast<T>(*p);
        } else if (const auto *p = std::get_if<unsigned long long>(&_value)) {
            return static_cast<T>(*p);
        } else {
            throw Error("type mismatch");
        }
    }

    template <typename T, typename std::enable_if<std::is_unsigned<typename std::decay<T>::type>::value, int>::type = 0>
    T get() const {
        if (const auto *p = std::get_if<unsigned long long>(&_value)) {
            return static_cast<T>(*p);
        } else if (const auto *p = std::get_if<long long>(&_value)) {
            return static_cast<T>(*p);
        } else {
            throw Error("type mismatch");
        }
    }

    template <typename T, typename std::enable_if<std::is_floating_point<typename std::decay<T>::type>::value, int>::type = 0>
    T get() const {
        if (const auto *p = std::get_if<double>(&_value)) {
            return static_cast<T>(*p);
        } else {
            throw Error("type mismatch");
        }
    }

    template <typename T, typename std::enable_if<std::is_same<typename std::decay<T>::type, bool>::value, int>::type = 0>
    T get() const {
        if (const auto *p = std::get_if<bool>(&_value)) {
            return *p;
        } else {
            throw Error("type mismatch");
        }
    }

    std::map<std::string, const Toml*> items() const {
        if (const auto *p = std::get_if<Object>(&_value)) {
            std::map<std::string, const Toml*> result;
            for (auto &[k, v] : *p) {
                result.emplace(k, &v);
            }
            return result;
        } else {
            throw Error("not an object");
        }
    }

    const Toml& operator[] (const std::string &key) const {
        if (auto *p = std::get_if<Object>(&_value)) {
            auto iter = p->find(key);
            if (iter == p->end()) {
                throw Error("key not exist");
            }

            return iter->second;
        } else {
            throw Error("not an object");
        }
    }

private:
    friend class Parser;

    Value _value;
};

}

#endif // end SEWENEW_TIKTOKEN_TOML_H
