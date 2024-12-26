#include "tokenizer/sentencepiece_tokenizer.h"

#include "common/logging.h"
#include "third_party/include/sentencepiece/sentencepiece_processor.h"
#include "tokenizer/utils.h"

#include <filesystem>
#include <string>
#include <string_view>
#include <type_traits>
#include <vector>

namespace fs = std::filesystem;

namespace mtk {

using SentencePieceProc = ::sentencepiece::SentencePieceProcessor;

using TokenType = Tokenizer::TokenType;
using SpTokenType = SentencePieceTokenizer::SpTokenType;
using SpFileMatcher = SentencePieceTokenizer::FileMatcher;

SentencePieceTokenizer::SentencePieceTokenizer(const std::vector<std::string>& tokenizerFiles)
    : Tokenizer(tokenizerFiles),
      mSpTokenizer(std::make_unique<sentencepiece::SentencePieceProcessor>()) {
    // Load a tokenizer model file that matches "*.model"
    const auto filesFound = searchTokenizerFiles(tokenizerFiles, SpFileMatcher());

    CHECK_EQ(filesFound.size(), 1)
        << "Unable to find SentencePiece tokenizer model file from " << tokenizerFiles;

    const auto& tokenizerModelPath = filesFound.begin()->second;
    const auto status = mSpTokenizer->Load(tokenizerModelPath);
    if (!status.ok()) {
        LOG(ERROR) << "Failed to load SentencePiece tokenizer file. " << status.ToString();
        return;
    }
    LOG(DEBUG) << "Initialized SentencePiece tokenizer from "
               << fs::path(tokenizerModelPath).filename();

    // Check `add_dummy_prefix` setting
    mAddDummyPrefix = [this] {
        static constexpr char testString[] = "-";
        const auto normalized = mSpTokenizer->Normalize(testString);
        const auto withDummyPrefix = std::string(kSpSpaceSymbol) + testString;
        return (normalized == withDummyPrefix);
    }();
    LOG(DEBUG) << "SentencePiece: add_dummy_prefix=" << mAddDummyPrefix;
}

SentencePieceTokenizer::~SentencePieceTokenizer() {}

size_t SentencePieceTokenizer::vocabSize() const {
    return mSpTokenizer->GetPieceSize();
}

void SentencePieceTokenizer::enableBosToken(const TokenType bosToken) {
    // Ignore argument and use sentencepiece's bos_id() instead
    const auto spBosId = mSpTokenizer->bos_id();
    if (spBosId != bosToken) {
        LOG(WARN) << "The given BOS Id (" << bosToken << ") != BOS Id in the tokenizer model file ("
                  << spBosId << "). Will use " << spBosId << " instead.";
    }
    Tokenizer::enableBosToken(spBosId);
}

std::string SentencePieceTokenizer::detokenize(const TokenType token) const {
    DCHECK(tokenizer_utils::isWithinRange<SpTokenType>(token));
    const auto spToken = static_cast<SpTokenType>(token);
    if (isAddedToken(token))
        return addedTokenToString(token);
    else if (mSpTokenizer->IsByte(spToken))
        return mSpTokenizer->DecodeIds({spToken});
    else
        return resolveSpmSpaceSymbol(mSpTokenizer->IdToPiece(spToken));
}

std::string SentencePieceTokenizer::detokenize(const std::vector<TokenType>& tokens) const {
    if constexpr (!std::is_same_v<TokenType, SpTokenType>)
        DCHECK(tokenizer_utils::isWithinRange<SpTokenType>(tokens));

    auto startsWith = [](const auto& str, const auto& prefix) {
        // Use rfind() because find() will continue searching if str doesn't start with prefix
        return str.rfind(prefix, 0) == 0;
    };

    std::vector<std::string> stringPieces;
    stringPieces.reserve(tokens.size());
    bool prevIsAddedToken = false;

    for (const auto token : tokens) {
        if (isAddedToken(token)) {
            stringPieces.emplace_back(addedTokenToString(token));
            prevIsAddedToken = true;
            continue;
        }
        // Skip the leading space symbol if previous is an added token
        const auto& stringPiece = mSpTokenizer->IdToPiece(token);
        size_t startPos = 0;
        if (prevIsAddedToken && startsWith(stringPiece, kSpSpaceSymbol))
            startPos = kSpSpaceSymbol.size();
        stringPieces.emplace_back(stringPiece.begin() + startPos, stringPiece.end());
        prevIsAddedToken = false;
    }
    return mSpTokenizer->DecodePieces(stringPieces);
}

// Type dispatch tokenize implementation based on TokenType
inline auto tokenizeImplDispatch(const std::unique_ptr<SentencePieceProc>& spTokenizer,
                                 const std::string_view& text) {
    if constexpr (std::is_same_v<TokenType, SpTokenType>) {
        return spTokenizer->EncodeAsIds(text);
    } else {
        // Require token type conversion
        const auto& tokens = spTokenizer->EncodeAsIds(text);
        DCHECK(tokenizer_utils::isWithinRange<SpTokenType>(tokens));
        return std::vector<TokenType>(tokens.begin(), tokens.end());
    }
}

std::vector<TokenType> SentencePieceTokenizer::tokenizeImpl(const std::string& text,
                                                            const size_t startPos,
                                                            const size_t count) const {
    const std::string_view subtext = std::string_view(text).substr(startPos, count);
    if (!mAddDummyPrefix && startPos == 0) {
        // `add_dummy_prefix` disabled, so we need to add it manually.
        std::string beginningText(kSpSpaceSymbol);
        beginningText += subtext;
        return tokenizeImplDispatch(mSpTokenizer, beginningText);
    }
    return tokenizeImplDispatch(mSpTokenizer, subtext);
}

} // namespace mtk