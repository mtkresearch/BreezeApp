#include "tokenizer/tiktoken_tokenizer.h"

#include "common/logging.h"
#include "third_party/include/sw/tokenizer/tiktoken.h"
#include "tokenizer/utils.h"

#include <filesystem>
#include <string>
#include <type_traits>
#include <vector>

namespace fs = std::filesystem;

namespace mtk {

using TokenType = Tokenizer::TokenType;
using TtkTokenType = TiktokenTokenizer::TtkTokenType;
using TtkFileMatcher = TiktokenTokenizer::FileMatcher;

using Tiktoken = ::sw::tokenizer::Tiktoken;
using TiktokenFactory = ::sw::tokenizer::TiktokenFactory;
using Toml = ::sw::tokenizer::Toml;

// Based on TiktokenFactory in third_party/include/sw/tokenizer/tiktoken.h
inline Tiktoken::Encoder getTtkEncoder(const std::string& path) {
    std::ifstream file(path);
    if (!file) {
        throw ::sw::tokenizer::Error("Failed to open Tiktoken encoder file: " + path);
    }

    auto parseLine = [](const auto& line) {
        const auto pos = line.find(" ");
        if (pos == std::string::npos) {
            throw ::sw::tokenizer::Error("Invalid encoder line: " + line);
        }
        const auto token = ::sw::tokenizer::base64::decode({line.data(), pos});
        uint64_t rank = 0;
        try {
            rank = std::stoul(line.substr(pos + 1));
        } catch (const std::exception&) {
            throw ::sw::tokenizer::Error("Invalid encoder rank: " + line);
        }
        return std::pair{std::move(token), rank};
    };

    Tiktoken::Encoder encoder;
    std::string line;
    while (std::getline(file, line)) {
        auto [token, rank] = parseLine(line);
        if (!encoder.emplace(std::move(token), rank).second) {
            throw ::sw::tokenizer::Error("Duplicate item: " + line);
        }
    }

    return encoder;
}

inline Tiktoken::Encoder
addedTokensToSpecialEncoder(const std::unordered_map<TokenType, std::string>& addedTokens) {
    Tiktoken::Encoder specialEncoder;
    for (const auto& [tokenId, tokenStr] : addedTokens) {
        if constexpr (!std::is_same_v<TokenType, TtkTokenType>)
            DCHECK(tokenizer_utils::isWithinRange<TtkTokenType>(tokenId));
        specialEncoder[tokenStr] = static_cast<TtkTokenType>(tokenId);
    }
    return specialEncoder;
}

TiktokenTokenizer::TiktokenTokenizer(const std::vector<std::string>& tokenizerFiles,
                                     const std::string& regexPattern)
    : Tokenizer(tokenizerFiles) {
    // Load a tokenizer model file that matches "*.tiktoken"
    const auto filesFound = searchTokenizerFiles(tokenizerFiles, TtkFileMatcher());

    CHECK_EQ(filesFound.size(), 1)
        << "Unable to find Tiktoken tokenizer file from " << tokenizerFiles;

    const auto& tokenizerFile = filesFound.begin()->second;

    // Gather regex pattern and encoders (token maps)
    const std::string& tiktokenRegex = regexPattern.empty() ? kDefaultPattern : regexPattern;
    CHECK(!tiktokenRegex.empty()) << "Tiktoken tokenizer requires regex pattern to initialize.";

    const auto& tiktokenEncoder = getTtkEncoder(tokenizerFile);
    const auto& tiktokenSpecialEncoder = addedTokensToSpecialEncoder(getAddedTokens());
    mTtkTokenizer =
        std::make_unique<Tiktoken>(tiktokenEncoder, tiktokenSpecialEncoder, tiktokenRegex);
    // Compute vocab size
    mVocabSize = tiktokenEncoder.size() + tiktokenSpecialEncoder.size();

    // Tiktoken will automatically handle added tokens internally
    releaseAddedTokensProcessor();

    LOG(DEBUG) << "Initialized Tiktoken tokenizer from " << fs::path(tokenizerFile).filename();
}

TiktokenTokenizer::~TiktokenTokenizer() {}

size_t TiktokenTokenizer::vocabSize() const {
    return mVocabSize;
}

// Type dispatch tokenize implementation based on TokenType
inline auto tokenizeImplDispatch(const std::unique_ptr<Tiktoken>& tiktoken,
                                 const std::string& text) {
    const auto& tokens = tiktoken->encode(text, /*with_special_token*/ true);
    if constexpr (std::is_same_v<TokenType, TtkTokenType>) {
        return tokens;
    } else {
        DCHECK(tokenizer_utils::isWithinRange<TtkTokenType>(tokens));
        return std::vector<TokenType>(tokens.begin(), tokens.end());
    }
}

std::vector<TokenType> TiktokenTokenizer::tokenizeImpl(const std::string& text,
                                                       const size_t startPos,
                                                       const size_t count) const {
    DCHECK(!hasAddedTokens())
        << "Manual added tokens preprocessing should be disabled for TiktokenTokenizer.";
    DCHECK(startPos == 0 && count == text.size())
        << "Attempting to tokenize substring via TiktokenTokenizer::tokenizeImpl. "
           "Manual added tokens preprocessing should be disabled for TiktokenTokenizer.";
    if (count == 0)
        return {};
    return tokenizeImplDispatch(mTtkTokenizer, text);
}

// Type dispatch detokenize implementation based on TokenType
template <typename T>
inline std::string detokenizeImplDispatch(const std::unique_ptr<Tiktoken>& tiktoken,
                                          const std::vector<T>& tokens) {
    if constexpr (std::is_same_v<T, TtkTokenType>) {
        return tiktoken->decode(tokens);
    } else {
        DCHECK(tokenizer_utils::isWithinRange<TtkTokenType>(tokens));
        return tiktoken->decode(std::vector<TtkTokenType>(tokens.begin(), tokens.end()));
    }
}

std::string TiktokenTokenizer::detokenize(const TokenType token) const {
    DCHECK(tokenizer_utils::isWithinRange<TtkTokenType>(token));
    const std::vector<TtkTokenType> singleToken = {static_cast<TtkTokenType>(token)};
    return detokenizeImplDispatch(mTtkTokenizer, singleToken);
}

std::string TiktokenTokenizer::detokenize(const std::vector<TokenType>& tokens) const {
    return detokenizeImplDispatch(mTtkTokenizer, tokens);
}

void TiktokenTokenizer::addToken(const TokenType tokenId, const std::string& tokenStr) {
    if (!mTtkTokenizer) {
        return Tokenizer::addToken(tokenId, tokenStr);
    }
    LOG(ERROR) << "Calling addToken manually is disabled for Tiktoken tokenizer.";
}

void TiktokenTokenizer::addTokens(const std::unordered_map<TokenType, std::string>& addedTokens) {
    if (!mTtkTokenizer) {
        return Tokenizer::addTokens(addedTokens);
    }
    LOG(ERROR) << "Calling addTokens manually is disabled for Tiktoken tokenizer.";
}

} // namespace mtk