#pragma once

#include "tokenizer/tokenizer.h"

#include <string>
#include <string_view>
#include <vector>

namespace sentencepiece {
class SentencePieceProcessor;
}

namespace mtk {

class SentencePieceTokenizer : public Tokenizer {
public:
    using SpTokenType = int;

private:
    static constexpr std::string_view kSpSpaceSymbol = "\xe2\x96\x81";

public:
    explicit SentencePieceTokenizer(const std::vector<std::string>& tokenizerFiles);

    // Ignore any additional arguments
    template <typename... Args>
    explicit SentencePieceTokenizer(const std::vector<std::string>& tokenizerFiles, Args&&... args)
        : SentencePieceTokenizer(tokenizerFiles) {}

    ~SentencePieceTokenizer();

    virtual size_t vocabSize() const override;

    virtual void enableBosToken(const TokenType bosToken) override;

    virtual std::string detokenize(const TokenType token) const override;

    virtual std::string detokenize(const std::vector<TokenType>& tokens) const override;

    class FileMatcher : public FileMatcherConcrete<FileMatcher> {
        friend class FileMatcherConcrete;
        friend class SentencePieceTokenizer;

        static constexpr char kTokenizerModelPattern[] = ".*\\.model$";
        static inline MatchPatterns kMatchPatterns = {kTokenizerModelPattern};
    };

private:
    virtual std::vector<TokenType> tokenizeImpl(const std::string& text, const size_t startPos,
                                                const size_t count) const override;

private:
    static std::string resolveSpmSpaceSymbol(std::string str) {
        constexpr std::string_view normalSpaceSymbol = " ";
        size_t start_pos = 0;
        while ((start_pos = str.find(kSpSpaceSymbol, start_pos)) != std::string::npos) {
            str.replace(start_pos, kSpSpaceSymbol.size(), normalSpaceSymbol);
            start_pos += normalSpaceSymbol.size();
        }
        return str;
    }

private:
    std::unique_ptr<sentencepiece::SentencePieceProcessor> mSpTokenizer;

    // Whether the `add_dummy_prefix` option in normalizer spec is enabled.
    bool mAddDummyPrefix = false;
};

} // namespace mtk