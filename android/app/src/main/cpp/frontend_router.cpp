#include "frontend_router.h"

#include "espeak_phonemize.h"
#include "lexicon.h"
#include "token_table.h"

#include <utility>

namespace sherpa_tts {

namespace {

FrontendErrorCode MapEspeakError(EspeakErrorCode code) {
  switch (code) {
    case EspeakErrorCode::kOk:
      return FrontendErrorCode::kOk;
    case EspeakErrorCode::kDisabled:
      return FrontendErrorCode::kEspeakDisabled;
    case EspeakErrorCode::kInitFailed:
      return FrontendErrorCode::kEspeakInitFailed;
    case EspeakErrorCode::kPhonemeEmpty:
      return FrontendErrorCode::kEspeakPhonemeEmpty;
    case EspeakErrorCode::kMissingSpecialTokens:
    case EspeakErrorCode::kTokenMiss:
      return FrontendErrorCode::kTokenMiss;
    case EspeakErrorCode::kInvalidArgs:
    default:
      return FrontendErrorCode::kInvalidArgs;
  }
}

}  // namespace

FrontendResult RouteTextToTokenIds(const std::string& text,
                                   const std::string& data_dir,
                                   const std::string& voice,
                                   FrontendMode mode,
                                   const Lexicon* lexicon,
                                   const TokenTable* token_table) {
  FrontendResult result;
  if (!token_table || token_table->Size() == 0 || text.empty()) {
    result.code = FrontendErrorCode::kInvalidArgs;
    return result;
  }

  if (mode != FrontendMode::kEspeakOnly) {
    result.token_ids = TextToTokenIds(text, lexicon, token_table);
    result.lexicon_token_count = static_cast<int32_t>(result.token_ids.size());
    if (!result.token_ids.empty()) {
      result.code = FrontendErrorCode::kOk;
      result.used_lexicon = true;
      return result;
    }
    if (mode == FrontendMode::kLexiconFirst) {
      result.code = FrontendErrorCode::kLexiconMiss;
      return result;
    }
  }

  if (data_dir.empty()) {
    result.code = FrontendErrorCode::kEspeakDataMissing;
    return result;
  }

  EspeakResult espeak =
      TextToTokenIdsWithEspeakDetailed(text, data_dir, voice, token_table);
  result.used_espeak = (espeak.code == EspeakErrorCode::kOk);
  result.espeak_phoneme_count = espeak.phoneme_count;
  result.espeak_matched_count = espeak.matched_phoneme_count;
  result.token_ids = std::move(espeak.token_ids);
  result.code = MapEspeakError(espeak.code);
  return result;
}

const char* FrontendErrorCodeToString(FrontendErrorCode code) {
  switch (code) {
    case FrontendErrorCode::kOk:
      return "OK";
    case FrontendErrorCode::kInvalidArgs:
      return "INVALID_ARGS";
    case FrontendErrorCode::kLexiconMiss:
      return "LEXICON_MISS";
    case FrontendErrorCode::kEspeakDataMissing:
      return "ESPEAK_DATA_MISSING";
    case FrontendErrorCode::kEspeakDisabled:
      return "ESPEAK_DISABLED";
    case FrontendErrorCode::kEspeakInitFailed:
      return "ESPEAK_INIT_FAILED";
    case FrontendErrorCode::kEspeakPhonemeEmpty:
      return "ESPEAK_PHONEME_EMPTY";
    case FrontendErrorCode::kTokenMiss:
      return "TOKEN_MISS";
    default:
      return "UNKNOWN";
  }
}

const char* FrontendModeToString(FrontendMode mode) {
  switch (mode) {
    case FrontendMode::kAuto:
      return "auto";
    case FrontendMode::kLexiconFirst:
      return "lexicon_first";
    case FrontendMode::kEspeakOnly:
      return "espeak_only";
    default:
      return "unknown";
  }
}

}  // namespace sherpa_tts
