#include "tokenizer/tokenizer.h"

#include "common/logging.h"
#include "third_party/include/re2/re2.h"
#include "third_party/include/yaml-cpp/yaml.h"

#include <algorithm>
#include <filesystem>
#include <list>
#include <string>
#include <vector>

#define NO_EXPORT __attribute__((visibility("hidden")))

namespace fs = std::filesystem;

namespace mtk {

using TokenType = Tokenizer::TokenType;
using FileMatcher = Tokenizer::FileMatcher;
using MatchResult = Tokenizer::FileMatcher::MatchResult;

inline const ::RE2::Options& getFileMatchRE2Options() {
    static ::RE2::Options re2Options;
    re2Options.set_case_sensitive(false);
    return re2Options;
}

class NO_EXPORT AddedTokensProcessor {
public:
    AddedTokensProcessor() {};

    AddedTokensProcessor(const std::unordered_map<TokenType, std::string>& addedTokens) {
        addTokens(addedTokens);
    }

    AddedTokensProcessor(const std::string& addedTokensYamlPath) {
        const auto addedTokensYaml = YAML::LoadFile(addedTokensYamlPath);
        for (const auto& kv : addedTokensYaml) {
            const auto tokenId = kv.first.as<TokenType>();
            const auto& tokenStrNode = kv.second;

            // If the yaml node tag contains "binary", interpret the value as YAML::Binary
            if (tokenStrNode.Tag().rfind("binary") != std::string::npos) {
                const auto& bin = tokenStrNode.as<YAML::Binary>();
                addToken(tokenId, std::string((const char*)bin.data(), bin.size()));
            } else {
                addToken(tokenId, tokenStrNode.as<std::string>());
            }
        }
        LOG(DEBUG) << "Loaded added tokens from " << addedTokensYamlPath;
    }

    void addToken(const TokenType tokenId, const std::string& tokenStr) {
        mAddedStr2Token[tokenStr] = tokenId;
        mAddedToken2Str[tokenId] = tokenStr;
        appendRegexPattern(tokenStr);

        // Invalidate mAddedTokensRegex if already initialized
        if (mAddedTokensRegex)
            mAddedTokensRegex.reset(nullptr);
    }

    void addTokens(const std::unordered_map<TokenType, std::string>& tokens) {
        for (const auto& [tokenId, tokenStr] : tokens) {
            addToken(tokenId, tokenStr);
        }
    }

    const std::unordered_map<TokenType, std::string>& getAddedTokens() const {
        return mAddedToken2Str;
    }

    bool isAddedToken(const std::string& tokenStr) {
        return mAddedStr2Token.find(tokenStr) != mAddedStr2Token.end();
    }

    bool isAddedToken(const TokenType tokenId) {
        return mAddedToken2Str.find(tokenId) != mAddedToken2Str.end();
    }

    std::optional<TokenType> stringToToken(const std::string& tokenStr) {
        if (isAddedToken(tokenStr))
            return mAddedStr2Token.at(tokenStr);
        return std::nullopt;
    }

    std::optional<std::string> tokenToString(const TokenType tokenId) {
        if (isAddedToken(tokenId))
            return mAddedToken2Str.at(tokenId);
        return std::nullopt;
    }

    // Lazy load and return mAddedTokensRegex
    const RE2& getAddedTokensRegex() const {
        if (!mAddedTokensRegex) {
            // Wrap the pattern with parentheses to make it into a capturing group
            mAddedTokensRegex.reset(new RE2('(' + mAddedTokensRegexPattern + ')'));
        }
        return *mAddedTokensRegex.get();
    }

private:
    void appendRegexPattern(const std::string& tokenStr) {
        if (!mAddedTokensRegexPattern.empty())
            mAddedTokensRegexPattern += '|';
        mAddedTokensRegexPattern += ::RE2::QuoteMeta(tokenStr);
    }

private:
    // Added tokens map
    std::unordered_map<std::string, TokenType> mAddedStr2Token;
    std::unordered_map<TokenType, std::string> mAddedToken2Str;

    // Added tokens regex matcher
    std::string mAddedTokensRegexPattern;
    mutable std::unique_ptr<RE2> mAddedTokensRegex; // Lazy load
};

class AddedTokensFileMatcher : public Tokenizer::FileMatcherConcrete<AddedTokensFileMatcher> {
private:
    friend class FileMatcherConcrete;
    static inline MatchPatterns kMatchPatterns = {".*added_tokens.*\\.yaml$"};
};

Tokenizer::Tokenizer(const std::vector<std::string>& tokenizerFiles) {
    // Ensure all tokenizer files exist
    for (const auto& path : tokenizerFiles) {
        CHECK(fs::exists(path)) << "The provided tokenizer file does not exist: " << path;
    }
    // Load added_tokens.yaml if found
    const AddedTokensFileMatcher matcher;
    const auto filesFound = searchTokenizerFiles(tokenizerFiles, matcher, /*strict*/ false);
    if (!filesFound.empty()) {
        const auto& addedTokensYamlPath = filesFound.begin()->second;
        mAddedTokensProcessor = std::make_unique<AddedTokensProcessor>(addedTokensYamlPath);
    }
}

Tokenizer::~Tokenizer() {}

void Tokenizer::enableBosToken(const TokenType bosToken) {
    mBosToken = bosToken;
}

void Tokenizer::disableBosToken() {
    mBosToken.reset();
}

void Tokenizer::addToken(const TokenType tokenId, const std::string& tokenStr) {
    if (!hasAddedTokens())
        mAddedTokensProcessor = std::make_unique<AddedTokensProcessor>(
            std::unordered_map<TokenType, std::string>{{tokenId, tokenStr}});
    else
        mAddedTokensProcessor->addToken(tokenId, tokenStr);
}

void Tokenizer::addTokens(const std::unordered_map<TokenType, std::string>& addedTokens) {
    if (!hasAddedTokens())
        mAddedTokensProcessor = std::make_unique<AddedTokensProcessor>(addedTokens);
    else
        mAddedTokensProcessor->addTokens(addedTokens);
}

bool Tokenizer::isAddedToken(const TokenType tokenId) const {
    if (!hasAddedTokens())
        return false;
    return mAddedTokensProcessor->isAddedToken(tokenId);
}

bool Tokenizer::isAddedToken(const std::string& tokenStr) const {
    if (!hasAddedTokens())
        return false;
    return mAddedTokensProcessor->isAddedToken(tokenStr);
}

const std::unordered_map<TokenType, std::string>& Tokenizer::getAddedTokens() const {
    static const std::unordered_map<TokenType, std::string> emptyAddedTokensMap;
    if (!hasAddedTokens())
        return emptyAddedTokensMap;
    return mAddedTokensProcessor->getAddedTokens();
}

std::string Tokenizer::addedTokenToString(const TokenType tokenId) const {
    const auto& tokenStr = mAddedTokensProcessor->tokenToString(tokenId);
    DCHECK(tokenStr.has_value());
    return tokenStr.value();
}

TokenType Tokenizer::addedStringToToken(const std::string& tokenStr) const {
    const auto& tokenId = mAddedTokensProcessor->stringToToken(tokenStr);
    DCHECK(tokenId.has_value());
    return tokenId.value();
}

void Tokenizer::releaseAddedTokensProcessor() {
    mAddedTokensProcessor.reset(nullptr);
}

std::vector<Tokenizer::AddedTokenInfo> Tokenizer::findAddedTokens(const std::string& text) const {
    if (!hasAddedTokens() || text.empty())
        return {};

    std::vector<AddedTokenInfo> addedTokensInfo;

    ::re2::StringPiece spText(text);
    ::re2::StringPiece match;
    const RE2& addedTokensRegex = mAddedTokensProcessor->getAddedTokensRegex();

    while (::RE2::FindAndConsume(&spText, addedTokensRegex, &match)) {
        const size_t matchSize = match.size();
        if (!matchSize)
            continue;
        const size_t startIndex = match.data() - text.data();
        const auto tokenId = mAddedTokensProcessor->stringToToken(match.as_string());
        DCHECK(tokenId.has_value());
        addedTokensInfo.push_back({tokenId.value(), startIndex, matchSize});
    }
    return addedTokensInfo;
}

std::vector<TokenType> Tokenizer::tokenize(const std::string& text) const {
    if (text.empty())
        return {};

    const auto& foundAddedTokens = findAddedTokens(text); // tokenId, startIndex, length

    if (foundAddedTokens.empty()) {
        // No added token found in text or manual added tokens processing is disabled
        auto outputTokens = tokenizeImpl(text, 0, text.size());
        if (outputTokens.empty())
            LOG(FATAL) << "Tokenization failed with zero output token.";
        if (mBosToken)
            outputTokens.insert(outputTokens.begin(), mBosToken.value());
        return outputTokens;
    }

    std::vector<TokenType> outputTokens;

    if (mBosToken)
        outputTokens.push_back(mBosToken.value());

    auto tokenizeSubstr = [&](const size_t pos, const size_t count) {
        const auto subTokens = tokenizeImpl(text, pos, count);
        if (count > 0 && subTokens.empty())
            LOG(FATAL) << "Tokenization failed with zero output token.";
        outputTokens.insert(outputTokens.end(), subTokens.begin(), subTokens.end());
    };

    size_t curIdx = 0;
    for (const auto& addedTokenInfo : foundAddedTokens) {
        const auto addedTokenId = addedTokenInfo.tokenId;
        const auto addedTokenPos = addedTokenInfo.startIndex;
        const auto addedTokenLength = addedTokenInfo.length;
        LOG(DEBUG) << "Found added token " << addedTokenId << " at pos " << addedTokenPos << ": '"
                   << text.substr(addedTokenPos, addedTokenLength) << "'";

        const size_t prefixLength = (addedTokenPos > curIdx) ? addedTokenPos - curIdx : 0;
        tokenizeSubstr(curIdx, prefixLength);
        outputTokens.push_back(addedTokenId);
        curIdx += prefixLength + addedTokenLength;
    }

    const size_t suffixLength = (curIdx < text.size()) ? text.size() - curIdx : 0;
    tokenizeSubstr(curIdx, suffixLength);

    return outputTokens;
}

std::unordered_map<std::string, std::string>
Tokenizer::searchTokenizerFiles(const std::vector<std::string>& tokenizerFiles,
                                const FileMatcher& matcher, const bool strict) {
    const auto& matchPatterns = matcher.getMatchPatterns();

    auto gatherMatchedFilesFrom = [&](const auto& filepaths, const auto& matchResult) {
        std::unordered_map<std::string, std::string> filesFound;
        for (const auto& [patternIdx, pathIdx] : matchResult) {
            const auto& pattern = matchPatterns[patternIdx];
            const auto& path = filepaths[pathIdx];
            filesFound[pattern] = path;
        }
        return filesFound;
    };

    if (tokenizerFiles.size() == 1 && fs::is_directory(tokenizerFiles[0])) {
        // Given is a directory path, so search for the tokenizer files under this directory
        std::vector<std::string> filesInDirectory;
        const fs::path directory = tokenizerFiles[0];
        for (const auto& entry : fs::directory_iterator(directory)) {
            const auto& filepath = entry.path().string();
            if (fs::is_regular_file(filepath))
                filesInDirectory.emplace_back(filepath);
        }
        const auto& matchResult = matcher.match(filesInDirectory);
        if (strict && matchResult.size() != matchPatterns.size()) {
            LOG(ERROR) << "Unable to find tokenizer files in the given directory: "
                       << tokenizerFiles[0];
            return {};
        }
        return gatherMatchedFilesFrom(filesInDirectory, matchResult);
    } else {
        // Given a list of files
        const auto& matchResult = matcher.match(tokenizerFiles);
        if (strict && matchResult.size() != matchPatterns.size()) {
            LOG(ERROR) << "Invalid tokenizer files: " << tokenizerFiles;
            return {};
        }
        return gatherMatchedFilesFrom(tokenizerFiles, matchResult);
    }
}

// Returns the mappings from pattern index to the matched filepath index
MatchResult FileMatcher::match(const std::vector<std::string>& paths) const {
    MatchResult matches;

    // Create a linked list of RE2 objects with their corresponding pattern indexes.
    const auto& re2Options = getFileMatchRE2Options();
    std::list<std::pair<RE2, size_t>> matchRegexs;

    size_t patternIdx = 0;
    for (const auto& pattern : getMatchPatterns()) {
        // RE2 is non-movable, so need to use std::piecewise_construct
        matchRegexs.emplace_back(std::piecewise_construct,
                                 std::forward_as_tuple(pattern, re2Options),
                                 std::forward_as_tuple(patternIdx++));
    }

    // Start matching
    for (size_t pathIdx = 0; pathIdx < paths.size(); pathIdx++) {
        const auto& filepath = fs::path(paths[pathIdx]).filename().string();
        auto matchRegexIt = matchRegexs.begin();
        while (matchRegexIt != matchRegexs.end()) {
            const auto& [re, patternIdx] = *matchRegexIt;
            if (::RE2::FullMatch(filepath, re)) {
                matches[patternIdx] = pathIdx;
                matchRegexs.erase(matchRegexIt);
                break;
            }
            ++matchRegexIt;
        }
    }
    return matches;
}

bool FileMatcher::accepts(const std::vector<std::string>& tokenizerFiles) const {
    MatchResult matchResult;

    if (tokenizerFiles.size() == 1 && fs::is_directory(tokenizerFiles[0])) {
        // Given is a directory path, so search for files under this directory
        std::vector<std::string> filesInDirectory;
        const fs::path directory = tokenizerFiles[0];
        for (const auto& entry : fs::directory_iterator(directory)) {
            const auto& filepath = entry.path().string();
            if (fs::is_regular_file(filepath))
                filesInDirectory.emplace_back(filepath);
        }
        matchResult = match(filesInDirectory);
    } else {
        matchResult = match(tokenizerFiles);
    }
    return matchResult.size() == getMatchPatterns().size();
}

} // namespace mtk
