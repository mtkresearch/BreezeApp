#ifndef HUGGINGFACE_TRANSFORMERS_TOKENIZER_GPT2_BPE
#define HUGGINGFACE_TRANSFORMERS_TOKENIZER_GPT2_BPE

#include <re2/re2.h>
#include <re2/stringpiece.h>

#include <codecvt>
#include <fstream>
#include <iostream>
#include <set>
#include <string>
#include <unordered_map>
#include <utility>

std::wstring utf8_to_wstring(const std::string& str);

std::string wstring_to_utf8(const std::wstring& str);

std::string utf8(wchar_t c);

void bytes_to_unicode(std::unordered_map<uint8_t, wchar_t>* b2u,
                      std::unordered_map<wchar_t, uint8_t>* u2b);

void byte_encode_token(const std::string& token,
                       const std::unordered_map<uint8_t, wchar_t>& b2u,
                       std::wstring* result);

// hash_pair_wstring is used in BPERanks to make a pair of wstrings
// hashable, so the pair can be used as the key to unordered_map.
struct hash_pair_wstring {
  size_t operator()(const std::pair<std::wstring, std::wstring>& p) const {
    auto hash1 = std::hash<std::wstring>{}(p.first);
    auto hash2 = std::hash<std::wstring>{}(p.second);
    // If hash1 == hash2, their XOR is zero.
    return (hash1 != hash2) ? hash1 ^ hash2 : hash1;
  }
};

// BPERanks maps each merge rule, which is a pair of wstrings, to its
// rank.  This mapping allows quick lookup for the optimal merge rule.
using BPERanks = std::unordered_map<std::pair<std::wstring, std::wstring>, int,
                                    hash_pair_wstring>;

void load_merge_rules(std::istream& ins, BPERanks* bpe_ranks);

void get_pairs(const std::wstring& word,
               std::vector<std::pair<std::wstring, std::wstring> >* pairs);

void bpe(const std::wstring& token, const BPERanks& bpe_ranks,
         std::vector<std::wstring>* result);

void tokenize(const std::string& text, RE2& re, BPERanks& bpe_ranks,
              const std::unordered_map<uint8_t, wchar_t>& b2u,
              std::vector<std::string>* result);

void load_vocab(std::istream& ins, std::unordered_map<std::string, int>* t2i,
                std::unordered_map<int, std::string>* i2t);
void encode(const std::string& text, RE2& re, BPERanks& bpe_ranks,
            std::unordered_map<uint8_t, wchar_t>& b2u,
            const std::unordered_map<std::string, int>& t2i,
            std::vector<int>* ids);
std::string decode(const std::vector<int>& ids,
                   const std::unordered_map<wchar_t, uint8_t>& u2b,
                   const std::unordered_map<int, std::string>& i2t);

#endif  // HUGGINGFACE_TRANSFORMERS_TOKENIZER_GPT2_BPE
