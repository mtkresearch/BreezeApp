#pragma once

#include "common/logging.h"
#include "tokenizer/huggingface_tokenizer.h"
#include "tokenizer/sentencepiece_tokenizer.h"
#include "tokenizer/tiktoken_tokenizer.h"

#include <string>
#include <vector>

namespace mtk {

enum class TokenizerType {
    Undefined,
    SentencePiece,
    HuggingFace,
    Tiktoken
};

class TokenizerFactory {
public:
    TokenizerFactory(const TokenizerType tokenizerType = TokenizerType::Undefined)
        : mTokenizerType(tokenizerType) {}

    template <typename... Args>
    std::unique_ptr<Tokenizer> create(const std::vector<std::string>& tokenizerFiles,
                                      Args&&... args) {
        std::unique_ptr<Tokenizer> tokenizer;

        // Create tokenizer, and return immediately if the creation is successful
#define CREATE_TOKENIZER(TType)                                                        \
    tokenizer = create<TType##Tokenizer>(tokenizerFiles, std::forward<Args>(args)...); \
    if (tokenizer) {                                                                   \
        LOG(INFO) << "Initialized " #TType " tokenizer.";                              \
        return tokenizer;                                                              \
    }

        // FileMatcher().accepts(...) calls are expensive, so the branches are rearranged as below.
#define __DECL__(TType)                                                \
    if (mTokenizerType == TokenizerType::Undefined) {                  \
        if (TType##Tokenizer::FileMatcher().accepts(tokenizerFiles)) { \
            CREATE_TOKENIZER(TType)                                    \
        }                                                              \
    } else if (mTokenizerType == TokenizerType::TType) {               \
        CREATE_TOKENIZER(TType)                                        \
    }

        __DECL__(SentencePiece)
        __DECL__(HuggingFace)
        __DECL__(Tiktoken)

#undef __DECL__
#undef CREATE_TOKENIZER

        CHECK(tokenizer.get() != nullptr)
            << "Unable to match suitable tokenizer type with the given files " << tokenizerFiles;
        return tokenizer;
    }

private:
    // Can be constructed with the provided arguments
    template <typename TokenizerClass, typename... Args>
    static std::enable_if_t<std::is_constructible_v<TokenizerClass, Args...>,
                            std::unique_ptr<Tokenizer>>
    create(Args&&... args) {
        return std::make_unique<TokenizerClass>(std::forward<Args>(args)...);
    }

    // Cannot be constructed with the provided arguments
    template <typename TokenizerClass, typename... Args>
    static std::enable_if_t<!std::is_constructible_v<TokenizerClass, Args...>,
                            std::unique_ptr<Tokenizer>>
    create(Args&&... args) {
        return nullptr;
    }

private:
    TokenizerType mTokenizerType;
};

} // namespace mtk