#pragma once

#include "tokenizer/tokenizer.h"

#include <string>
#include <vector>

namespace mtk {

struct HFTokenizerContext;

class HuggingFaceTokenizer : public Tokenizer {
public:
    using HFTokenType = int;

    // clang-format off

    // Default use qwen1.5 regex pattern
    static constexpr char kDefaultPattern[] = "((?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\r\n]*|\\s*[\r\n]+|\\s+(?:$|[^\\S])|\\s+)";

    // clang-format on

public:
    explicit HuggingFaceTokenizer(const std::vector<std::string>& tokenizerFiles,
                                  const std::string& regexPattern = kDefaultPattern);

    // Ignore any additional arguments
    template <typename... Args>
    explicit HuggingFaceTokenizer(const std::vector<std::string>& tokenizerFiles,
                                  const std::string& regexPattern = kDefaultPattern, Args&&... args)
        : HuggingFaceTokenizer(tokenizerFiles, regexPattern) {}

    ~HuggingFaceTokenizer();

    virtual size_t vocabSize() const override;

    virtual std::string detokenize(const TokenType token) const override;

    virtual std::string detokenize(const std::vector<TokenType>& tokens) const override;

    virtual void addToken(const TokenType tokenId, const std::string& tokenStr) override;

    virtual void addTokens(const std::unordered_map<TokenType, std::string>& addedTokens) override;

    class FileMatcher : public FileMatcherConcrete<FileMatcher> {
        friend class FileMatcherConcrete;
        friend class HuggingFaceTokenizer;

        static constexpr char kVocabPattern[] = ".*vocab.*\\.txt$";
        static constexpr char kMergesPattern[] = ".*merges.*\\.txt$";

        static inline MatchPatterns kMatchPatterns = {kVocabPattern, kMergesPattern};
    };

private:
    void loadVocabAndMerges(const std::string& vocabPath, const std::string& mergesPath);

    virtual std::vector<TokenType> tokenizeImpl(const std::string& text, const size_t startPos,
                                                const size_t count) const override;

    // Update the token map in context with added tokens
    void insertAddedTokenToCtx(const TokenType tokenId, const std::string& tokenStr);

private:
    std::unique_ptr<HFTokenizerContext> mCtx;
};

} // namespace mtk