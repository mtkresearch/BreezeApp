#pragma once

#include <string>
#include <unordered_map>
#include <vector>

namespace mtk {

class AddedTokensProcessor;

class Tokenizer {
public:
#if defined(USE_HUGGINGFACE_TOKEN_TYPE) || defined(USE_SENTENCEPIECE_TOKEN_TYPE)
    using TokenType = int;
#elif defined(USE_TIKTOKEN_TOKEN_TYPE)
    using TokenType = uint64_t;
#else
    using TokenType = int64_t;
#endif

public:
    explicit Tokenizer(const std::vector<std::string>& tokenizerFiles);
    virtual ~Tokenizer();

    virtual size_t vocabSize() const = 0;

    // Generic tokenize function with added tokens handling.
    // Subclasses should implement tokenizeImpl.
    std::vector<TokenType> tokenize(const std::string& text) const;

    virtual std::string detokenize(const TokenType token) const = 0;

    virtual std::string detokenize(const std::vector<TokenType>& tokens) const = 0;

private:
    virtual std::vector<TokenType> tokenizeImpl(const std::string& text, const size_t startPos,
                                                const size_t count) const = 0;

public:
    //===----------------------------------------------------===//
    // Template function aliases for `tokenize` and `detokenize`
    //===----------------------------------------------------===//

    // Function alias to `tokenize`
    template <typename T>
    std::vector<TokenType> encode(T&& text) const {
        return tokenize(std::forward<T>(text));
    }

    // Function alias to `detokenize`
    template <typename T>
    std::string decode(T&& tokens) const {
        return detokenize(std::forward<T>(tokens));
    }

public:
    //===-------------------------===//
    // BOS token handling public APIs
    //===-------------------------===//

    // Enable BOS token prepend during tokenization
    virtual void enableBosToken(const TokenType bosToken);

    // Disable BOS token prepend during tokenization
    void disableBosToken();

public:
    //===-------------------===//
    // Added tokens public APIs
    //===-------------------===//
    bool hasAddedTokens() const { return static_cast<bool>(mAddedTokensProcessor); }

    virtual void addToken(const TokenType tokenId, const std::string& tokenStr);

    virtual void addTokens(const std::unordered_map<TokenType, std::string>& addedTokens);

    bool isAddedToken(const TokenType tokenId) const;

    bool isAddedToken(const std::string& tokenStr) const;

    const std::unordered_map<TokenType, std::string>& getAddedTokens() const;

public:
    //===-----------------===//
    // Tokenizer file matcher
    //===-----------------===//

    // A filepath matcher that uses fixed patterns to match.
    // To change the match patterns, choose one of the two options:
    //   1. Inherit this FileMatcher class and override `getMatchPatterns()`.
    //   2. Inherit FileMatcherConcrete class and define `kMatchPatterns`.
    class FileMatcher {
    public:
        // A list of regex patterns
        using MatchPatterns = std::vector<std::string>;

        // Mappings from pattern index to the matched path index
        using MatchResult = std::unordered_map<size_t, size_t>;

    public:
        // Returns the mappings from pattern index to the matched path index.
        // Match patterns are taken from the getMatchPatterns().
        MatchResult match(const std::vector<std::string>& paths) const;

        // Returns true if all patterns can be matched by the given tokenizer file list.
        // If the given list has only one directory path, search the files under it.
        bool accepts(const std::vector<std::string>& tokenizerFiles) const;

        // Returns a list of regex patterns for matching.
        virtual const MatchPatterns& getMatchPatterns() const = 0;
    };

    // A concrete FileMathcher helper class. Derived classes don't need to override any functions.
    // Derived classes use CRTP to override the behavior of `getMatchPatterns()` by simply defining
    // `kMatchPatterns`.
    template <typename FileMatcherImpl>
    class FileMatcherConcrete : public FileMatcher {
    public:
        virtual const MatchPatterns& getMatchPatterns() const override {
            return FileMatcherImpl::kMatchPatterns;
        }
    };

protected:
    //===------------------------------------------------------------===//
    // Added tokens searching and conversion between token id and string
    //===------------------------------------------------------------===//
    struct AddedTokenInfo {
        TokenType tokenId;
        size_t startIndex;
        size_t length;
    };

    std::vector<AddedTokenInfo> findAddedTokens(const std::string& text) const;

    std::string addedTokenToString(const TokenType tokenId) const;

    TokenType addedStringToToken(const std::string& tokenStr) const;

    // Release AddedTokensProcessor instance to stop the handling of added tokens
    void releaseAddedTokensProcessor();

protected:
    //===------------------------------------===//
    // Helper function to search tokenizer files
    //===------------------------------------===//
    static std::unordered_map<std::string, std::string>
    searchTokenizerFiles(const std::vector<std::string>& tokenizerFiles, const FileMatcher& matcher,
                         const bool strict = true);

private:
    std::unique_ptr<AddedTokensProcessor> mAddedTokensProcessor;
    std::optional<TokenType> mBosToken;
};

} // namespace mtk