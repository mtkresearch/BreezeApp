#pragma once

#include "tokenizer/tokenizer.h"

#include <string>
#include <vector>

namespace sw::tokenizer {
class Tiktoken;
}

namespace mtk {

class TiktokenTokenizer : public Tokenizer {
public:
    using TtkTokenType = uint64_t;

    // clang-format off

    // Default use Llama 3 regex pattern
    static constexpr char kDefaultPattern[] = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\r\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\r\n]*|\\s*[\r\n]+|\\s+(?:$|[^\\S])|\\s+";

    // clang-format on

public:
    explicit TiktokenTokenizer(const std::vector<std::string>& tokenizerFiles,
                               const std::string& regexPattern = kDefaultPattern);

    // Ignore any additional arguments
    template <typename... Args>
    explicit TiktokenTokenizer(const std::vector<std::string>& tokenizerFiles,
                               const std::string& regexPattern = kDefaultPattern, Args&&... args)
        : TiktokenTokenizer(tokenizerFiles, regexPattern) {}

    ~TiktokenTokenizer();

    virtual size_t vocabSize() const override;

    virtual std::string detokenize(const TokenType token) const override;

    virtual std::string detokenize(const std::vector<TokenType>& tokens) const override;

    virtual void addToken(const TokenType tokenId, const std::string& tokenStr) override;

    virtual void addTokens(const std::unordered_map<TokenType, std::string>& addedTokens) override;

    class FileMatcher : public FileMatcherConcrete<FileMatcher> {
        friend class FileMatcherConcrete;
        friend class TiktokenTokenizer;

        static constexpr char kTokenizerPattern[] = ".*\\.tiktoken$";
        static inline MatchPatterns kMatchPatterns = {kTokenizerPattern};
    };

private:
    virtual std::vector<TokenType> tokenizeImpl(const std::string& text, const size_t startPos,
                                                const size_t count) const override;

private:
    size_t mVocabSize = 0;
    std::unique_ptr<sw::tokenizer::Tiktoken> mTtkTokenizer;
};

} // namespace mtk