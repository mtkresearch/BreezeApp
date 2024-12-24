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

#ifndef SEWENEW_TOKENIZER_TIKTOKEN_H
#define SEWENEW_TOKENIZER_TIKTOKEN_H

#include <cctype>
#include <cstdint>
#include <limits>
#include <memory>
#include <fstream>
#include <functional>
#include <string>
#include <unordered_set>
#include <unordered_map>
#include <vector>
#include "re2/re2.h"
#include "sw/tokenizer/base64.h"
#include "sw/tokenizer/errors.h"
#include "sw/tokenizer/toml.h"

namespace sw::tokenizer {

class Tiktoken {
public:
    using Encoder = std::unordered_map<std::string, uint64_t>;
    using Decoder = std::unordered_map<uint64_t, std::string>;

    //inline static const std::string ENDOFTEXT = "<|endoftext|>";
    //inline static const std::string FIM_PREFIX = "<|fim_prefix|>";
    //inline static const std::string FIM_MIDDLE = "<|fim_middle|>";
    //inline static const std::string FIM_SUFFIX = "<|fim_suffix|>";
    //inline static const std::string ENDOFPROMPT = "<|endofprompt|>";

    Tiktoken(Encoder encoder,
            Encoder special_encoder,
            const std::string &pattern) {
        _encoder = std::move(encoder);
        _special_token_encoder = std::move(special_encoder);

        _decoder = _build_decoder(_encoder);
        _special_token_decoder = _build_decoder(_special_token_encoder);

        if (pattern.empty()) {
            throw Error("no pattern is specified");
        }

        _regex = _create_regex(pattern);

        _special_token_regex = _build_special_token_regex(_special_token_encoder);
    }

    std::vector<uint64_t> encode(const std::string &text, bool with_special_token = true) {
        if (!with_special_token) {
            std::vector<uint64_t> tokens;
            uint64_t last_piece_token_len = 0;
            re2::StringPiece input(text);
            _encode(input, tokens, last_piece_token_len);

            return tokens;
        } else {
            return _encode_with_special_token(text, _special_token_encoder).first;
        }
    }

    std::vector<uint64_t> encode(const std::string &text, const std::unordered_set<std::string> &allowed_special) {
        return _encode_with_special_token(text, allowed_special).first;
    }

    std::string decode(const std::vector<uint64_t> &tokens) {
        std::string ret;
        ret.reserve(tokens.size() * 2);
        for (auto token : tokens) {
            std::string token_bytes;
            auto iter = _decoder.find(token);
            if (iter != _decoder.end()) {
                token_bytes = iter->second;
            } else {
                iter = _special_token_decoder.find(token);
                if (iter != _special_token_decoder.end()) {
                    token_bytes = iter->second;
                } else {
                    throw Error("unknown token: " + std::to_string(token));
                }
            }
            ret += token_bytes;
        }

        return ret;
    }

private:
    using Re2UPtr = std::unique_ptr<re2::RE2>;

    uint64_t _max_size() {
        return std::numeric_limits<uint64_t>::max();
    }

    Re2UPtr _create_regex(const std::string &pattern) const {
        assert(!pattern.empty());

        return std::make_unique<re2::RE2>("(" + pattern + ")");
    }

    Re2UPtr _build_special_token_regex(const Encoder &special_encoder) {
        std::string special_pattern;
        for (const auto &ele : special_encoder) {
            if (!special_pattern.empty()) {
                special_pattern += "|";
            }
            special_pattern += re2::RE2::QuoteMeta(ele.first);
        }

        if (special_pattern.empty()) {
            return nullptr;
        }

        return _create_regex(special_pattern);
    }

    Decoder _build_decoder(const Encoder &encoder) {
        Decoder decoder;
        for (const auto &[k, v] : encoder) {
            decoder.emplace(v, k);
        }

        if (encoder.size() != decoder.size()) {
            throw Error("duplicate items in encoder");
        }

        return decoder;
    }

    template <typename T>
    std::pair<std::optional<std::string>, re2::StringPiece> _split_with_allowed_special_token(re2::StringPiece &input, const T &allowed_special) {
        if (!_special_token_regex) {
            return std::make_pair(std::nullopt, input);
        }

        auto start = input.begin();
        std::string special;
        while (true) {
            if (!re2::RE2::FindAndConsume(&input, *_special_token_regex, &special)) {
                // No special token.
                break;
            }

            if (allowed_special.count(special) == 1) {
                // Found an allowed special token, split the text with it.
                return std::make_pair(std::move(special), re2::StringPiece(start, input.begin() - start - special.size()));
            } // else try to find the next special token
        }

        return std::make_pair(std::nullopt, input);
    }

    void _encode(re2::StringPiece &input, std::vector<uint64_t> &ret, uint64_t &last_piece_token_len) {
        std::string piece;
        assert(_regex);
        while (re2::RE2::FindAndConsume(&input, *_regex, &piece)) {
            auto iter = _encoder.find(piece);
            if (iter != _encoder.end()) {
                last_piece_token_len = 1;
                ret.push_back(iter->second);
                continue;
            }
            auto tokens = _byte_pair_encode(piece, _encoder);
            last_piece_token_len = tokens.size();
            ret.insert(ret.end(), tokens.begin(), tokens.end());
        }
    }

    template <typename T>
    std::pair<std::vector<uint64_t>, uint64_t> _encode_with_special_token(const std::string &text, const T &allowed_special) {
        std::vector<uint64_t> tokens;
        uint64_t last_piece_token_len = 0;
        re2::StringPiece input(text);
        while (true) {
            auto [special, sub_input] = _split_with_allowed_special_token(input, allowed_special);

            _encode(sub_input, tokens, last_piece_token_len);

            if (special) {
                uint64_t token = 0;
                try {
                    token = _special_token_encoder.at(*special);
                } catch (const std::out_of_range &) {
                    // Should never go here, since special pattern includes all special chars.
                    assert(false);
                }

                tokens.push_back(token);
                last_piece_token_len = 0;
            } else {
                break;
            }
        }

        // last_piece_token_len is how many tokens came from the last regex split. This is used
        // for determining unstable tokens, since you can't merge across (stable) regex splits
        return std::make_pair(tokens, last_piece_token_len);
    }

    std::vector<uint64_t> _byte_pair_merge(
            const std::string &piece,
            const std::unordered_map<std::string, uint64_t> &ranks,
            std::function<uint64_t (uint64_t, uint64_t)> func) {
        // This is a vector of (start, rank).
        // The rank is of the byte pair starting at position start.
        // The rank of the last item in the vector is not a valid value.
        std::vector<std::pair<uint64_t, uint64_t>> parts;
        parts.reserve(piece.size() + 1);
        for (auto idx = 0U; idx < piece.size() + 1; ++idx) {
            parts.emplace_back(idx, _max_size());
        }

        auto get_rank = [&piece, &ranks](const std::vector<std::pair<uint64_t, uint64_t>> &parts, uint64_t start_idx, uint64_t skip) -> std::optional<uint64_t> {
            if (start_idx + skip + 2 < parts.size()) {
                auto s = parts[start_idx].first;
                auto e = parts[start_idx + skip + 2].first;
                auto key = piece.substr(s, e - s);
                auto iter = ranks.find(key);
                if (iter != ranks.end()) {
                    return iter->second;
                }
            }
            return std::nullopt;
        };

        // We look up the ranks once in the beginning and iteratively update
        // them during each merge, which reduces the number of rank lookups.
        for (auto i = 0U; i < parts.size() - 2; ++i) {
            auto rank = get_rank(parts, i, 0);
            if (rank) {
                // usize::MAX is a sentinel value and cannot be a valid rank
                assert(*rank != _max_size());
                parts[i].second = *rank;
            }
        }

        // If you have n parts and m merges, this does O(mn) work.
        // We could do something with a heap and do O(m log n) work.
        // It is important to consider that n is often small (<100), and as such
        // the cache-locality benefits outweigh the algorithmic complexity downsides
        // of the `parts` vector data structure above.

        // Note that we hash bytes, not token pairs. As long as we train BPE the way we
        // currently do, this is equivalent. An easy way to break this would be to decouple
        // merge priority from token index or to prevent specific token merges.
        while (true) {
            if (parts.size() == 1) {
                break;
            }

            // usize::MAX is a sentinel rank value allowing us to
            // take the min more quickly
            auto min_rank = std::make_pair<uint64_t, uint64_t>(_max_size(), 0);
            for (auto i = 0U; i < parts.size() - 1; ++i) {
                auto rank = parts[i].second;
                if (rank < min_rank.first) {
                    min_rank.first = rank;
                    min_rank.second = i;
                }
            }

            if (min_rank.first != _max_size()) {
                auto i = min_rank.second;

                // NOTE: We are about to remove parts[i + 1]. We do not do it
                // yet because there are cache-locality benefits to updating
                // parts[i] and parts[i-1] before removing, which could thrash
                // the cache. Thus, we update the rank calculation by skipping over
                // parts[i + 1], by invoking `get_rank!` with `skip = 1`.
                auto rank = get_rank(parts, i, 1);
                if (rank) {
                    parts[i].second = *rank;
                } else {
                    parts[i].second = _max_size();
                }
                if (i > 0) {
                    auto rank = get_rank(parts, i - 1, 1);
                    if (rank) {
                        parts[i - 1].second = *rank;
                    } else {
                        parts[i - 1].second = _max_size();
                    }
                }

                parts.erase(parts.begin() + (i + 1));
            } else {
                break;
            }
        }
        std::vector<uint64_t> out;
        out.reserve(parts.size() - 1);
        for (auto i = 0U; i < parts.size() - 1; ++i) {
            auto s = parts[i].first;
            auto e = parts[i + 1].first;
            out.push_back(func(s, e));
        }
        return out;
    }

    std::vector<uint64_t> _byte_pair_encode(const std::string &piece, const Encoder &encoder) {
        if (piece.size() == 1) {
            auto iter = encoder.find(piece);
            if (iter != encoder.end()) {
                return std::vector<uint64_t>({iter->second});
            } else {
                // TODO: is it possible?
                return {};
            }
        }

        return _byte_pair_merge(piece, encoder,
                [&piece, &encoder](uint64_t start, uint64_t stop) {
                    std::string key = piece.substr(start, stop - start);
                    auto iter = encoder.find(key);
                    if (iter != encoder.end()) {
                        return iter->second;
                    } else {
                        // TODO: what if key does not exist? Should we return `unknown`?
                        // assert(false); // ??
                        return uint64_t(0);
                    }
                });
    }

    Encoder _encoder;
    Encoder _special_token_encoder;
    Decoder _decoder;
    Decoder _special_token_decoder;

    Re2UPtr _regex;
    Re2UPtr _special_token_regex;
};

class TiktokenFactory {
private:
    struct Config {
        std::string path;
        Tiktoken::Encoder special_tokens;
        std::string pattern;
    };

public:
    explicit TiktokenFactory(const std::string &config) {
        auto conf = Toml::parse(config);
        for (auto &[name, value] : conf["encodings"].items()) {
            if (!_encodings.emplace(name, _parse_config(*value)).second) {
                throw Error("duplicate encoding conf");
            }
        }
    }

    Tiktoken create(const std::string &name) const {
        auto iter = _encodings.find(name);
        if (iter == _encodings.end()) {
            throw Error("unknown name: " + name);
        }

        return _create(iter->second);
    }

private:
    Tiktoken _create(const Config &config) const {
        auto encoder = _load_encoder(config.path);

        return Tiktoken(std::move(encoder), config.special_tokens, config.pattern);
    }

    Tiktoken::Encoder _load_encoder(const std::string &path) const {
        std::ifstream file(path);
        if (!file) {
            throw Error("failed to open encoder file: " + path);
        }

        Tiktoken::Encoder encoder;
        std::string line;
        while (std::getline(file, line)) {
            auto [token, rank] = _parse(line);

            if (!encoder.emplace(std::move(token), rank).second) {
                throw Error("duplicate item: " + line);
            }
        }

        return encoder;
    }

    std::pair<std::string, uint64_t> _parse(const std::string &line) const {
        auto pos = line.find(" ");
        if (pos == std::string::npos) {
            throw Error("invalid encoder line: " + line);
        }

        auto token = base64::decode({line.data(), pos});
        uint64_t rank = 0;
        try {
            rank = std::stoul(line.substr(pos + 1));
        } catch (const std::exception &) {
            throw Error("invalid encoder rank: " + line);
        }

        return {std::move(token), rank};
    }

    Config _parse_config(const Toml &value) const {
        Config conf;
        conf.path = value["ranks"].get<std::string>();
        conf.pattern = value["pattern"].get<std::string>();
        conf.special_tokens = value["special_tokens"].get<std::unordered_map<std::string, uint64_t>>();

        return conf;
    }

    std::unordered_map<std::string, Config> _encodings;
};

}

#endif // end SEWENEW_TOKENIZER_TIKTOKEN_H
