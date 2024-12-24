#include "tokenizer/huggingface_tokenizer.h"

#include "common/logging.h"
#include "third_party/include/hf-tokenizer/bpe.h"
#include "third_party/include/re2/re2.h"
#include "tokenizer/utils.h"

#include <filesystem>
#include <string>
#include <thread>
#include <type_traits>
#include <vector>

namespace fs = std::filesystem;

namespace mtk {

using TokenType = Tokenizer::TokenType;
using HFTokenType = HuggingFaceTokenizer::HFTokenType;
using HFFileMatcher = HuggingFaceTokenizer::FileMatcher;

struct HFTokenizerContext {
    explicit HFTokenizerContext(const std::string& regexPattern) : re(regexPattern) {}

    // For merges
    BPERanks bpeRanks;
    std::unordered_map<uint8_t, wchar_t> b2u; // Byte to unicode
    std::unordered_map<wchar_t, uint8_t> u2b; // Unicode to byte

    // For vocab
    std::unordered_map<std::string, HFTokenType> t2i; // String to token
    std::unordered_map<HFTokenType, std::string> i2t; // Token to string

    // Tokenizer regex
    RE2 re;
};

HuggingFaceTokenizer::HuggingFaceTokenizer(const std::vector<std::string>& tokenizerFiles,
                                           const std::string& regexPattern)
    : Tokenizer(tokenizerFiles),
      mCtx(std::make_unique<HFTokenizerContext>(regexPattern.empty() ? kDefaultPattern
                                                                     : regexPattern)) {
    // Match the tokenizer files and load if found
    const HFFileMatcher matcher;
    const auto filesFound = searchTokenizerFiles(tokenizerFiles, matcher);
    if (filesFound.size() != matcher.getMatchPatterns().size()) {
        LOG(ERROR) << "Failed to load HuggingFace tokenizer from " << tokenizerFiles;
        return;
    }
    const auto& vocabPath = filesFound.at(HFFileMatcher::kVocabPattern);
    const auto& mergesPath = filesFound.at(HFFileMatcher::kMergesPattern);
    loadVocabAndMerges(vocabPath, mergesPath);

    // Update token maps in context with added tokens (if any)
    for (const auto& [tokenId, tokenStr] : getAddedTokens())
        insertAddedTokenToCtx(tokenId, tokenStr);

    LOG(DEBUG) << "Initialized HuggingFace tokenizer from " << fs::path(vocabPath).filename()
               << " and " << fs::path(mergesPath).filename();
}

HuggingFaceTokenizer::~HuggingFaceTokenizer() {}

void HuggingFaceTokenizer::loadVocabAndMerges(const std::string& vocabPath,
                                              const std::string& mergesPath) {
    auto loadVocabFile = [&] {
        std::fstream vocabFile(vocabPath, std::ios::in);
        load_vocab(vocabFile, &mCtx->t2i, &mCtx->i2t);
    };

    auto loadMergeRules = [&] {
        std::fstream mergesFile(mergesPath, std::ios::in);
        load_merge_rules(mergesFile, &mCtx->bpeRanks);
    };

    // Parsing of vocab and merges are expensive
    std::thread loadVocabFileThread(loadVocabFile);
    std::thread loadMergeRulesThread(loadMergeRules);

    bytes_to_unicode(&mCtx->b2u, &mCtx->u2b);

    loadVocabFileThread.join();
    loadMergeRulesThread.join();
}

size_t HuggingFaceTokenizer::vocabSize() const {
    return mCtx->i2t.size();
}

// Type dispatch detokenize implementation based on TokenType
template <typename T>
inline std::string detokenizeImplDispatch(const std::unique_ptr<HFTokenizerContext>& ctx,
                                          const std::vector<T>& tokens) {
    // Modified based on decode() from hf-tokenzier
    auto decodeTokens = [&](const std::vector<HFTokenType>& tokens) -> std::string {
        auto& i2t = ctx->i2t;
        auto& u2b = ctx->u2b;

        std::string result;
        for (const auto tokenId : tokens) {
            result += i2t.at(tokenId);
        }

        const std::wstring wconcat = utf8_to_wstring(result);
        result.clear();
        for (const wchar_t wchar : wconcat) {
            if (u2b.find(wchar) == u2b.end()) {
                // Unicode map (u2b) doesn't have `wchar`, so convert it back to utf8.
                result += ::utf8(wchar);
                continue;
            }
            result.push_back(char(u2b.at(wchar)));
        }
        return result;
    };

    if constexpr (std::is_same_v<T, HFTokenType>) {
        return decodeTokens(tokens);
    } else {
        DCHECK(tokenizer_utils::isWithinRange<HFTokenType>(tokens));
        return decodeTokens(std::vector<HFTokenType>(tokens.begin(), tokens.end()));
    }
}

std::string HuggingFaceTokenizer::detokenize(const TokenType token) const {
    const std::vector<TokenType> singleToken = {token};
    return detokenizeImplDispatch(mCtx, singleToken);
}

std::string HuggingFaceTokenizer::detokenize(const std::vector<TokenType>& tokens) const {
    // i2t & t2i already contain added tokens, so we can just call decode.
    return detokenizeImplDispatch(mCtx, tokens);
}

// Type dispatch tokenize implementation based on TokenType
inline auto tokenizeImplDispatch(const std::unique_ptr<HFTokenizerContext>& ctx,
                                 const std::string& text) {
    std::vector<HFTokenType> tokens;
    ::encode(text, ctx->re, ctx->bpeRanks, ctx->b2u, ctx->t2i, &tokens);

    if constexpr (std::is_same_v<TokenType, HFTokenType>) {
        return tokens;
    } else {
        // Require token type conversion
        DCHECK(tokenizer_utils::isWithinRange<HFTokenType>(tokens));
        return std::vector<TokenType>(tokens.begin(), tokens.end());
    }
}

std::vector<TokenType> HuggingFaceTokenizer::tokenizeImpl(const std::string& text,
                                                          const size_t startPos,
                                                          const size_t count) const {
    if (count == 0)
        return {};
    if (startPos == 0 && count == text.size())
        return tokenizeImplDispatch(mCtx, text);
    return tokenizeImplDispatch(mCtx, text.substr(startPos, count));
}

void HuggingFaceTokenizer::addToken(const TokenType tokenId, const std::string& tokenStr) {
    Tokenizer::addToken(tokenId, tokenStr);
    insertAddedTokenToCtx(tokenId, tokenStr);
}

void HuggingFaceTokenizer::addTokens(
    const std::unordered_map<TokenType, std::string>& addedTokens) {
    Tokenizer::addTokens(addedTokens);
    // Add added tokens to t2i and i2t
    for (const auto& [tokenId, tokenStr] : addedTokens) {
        insertAddedTokenToCtx(tokenId, tokenStr);
    }
}

void HuggingFaceTokenizer::insertAddedTokenToCtx(const TokenType tokenId,
                                                 const std::string& tokenStr) {
    const auto hfTokenId = static_cast<HFTokenType>(tokenId);
    mCtx->i2t[hfTokenId] = tokenStr;
    mCtx->t2i[tokenStr] = hfTokenId;
}

} // namespace mtk