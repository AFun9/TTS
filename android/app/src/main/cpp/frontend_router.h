#ifndef SHERPA_TTS_FRONTEND_ROUTER_H_
#define SHERPA_TTS_FRONTEND_ROUTER_H_

#include <cstdint>
#include <string>
#include <vector>

namespace sherpa_tts {

class Lexicon;
class TokenTable;

enum class FrontendMode : int32_t {
  kAuto = 0,
  kLexiconFirst = 1,
  kEspeakOnly = 2,
};

enum class FrontendErrorCode : int32_t {
  kOk = 0,
  kInvalidArgs = 1,
  kLexiconMiss = 2,
  kEspeakDataMissing = 3,
  kEspeakDisabled = 4,
  kEspeakInitFailed = 5,
  kEspeakPhonemeEmpty = 6,
  kTokenMiss = 7,
};

struct FrontendResult {
  FrontendErrorCode code = FrontendErrorCode::kInvalidArgs;
  std::vector<int64_t> token_ids;
  bool used_lexicon = false;
  bool used_espeak = false;
  int32_t lexicon_token_count = 0;
  int32_t espeak_phoneme_count = 0;
  int32_t espeak_matched_count = 0;
};

FrontendResult RouteTextToTokenIds(const std::string& text,
                                   const std::string& data_dir,
                                   const std::string& voice,
                                   FrontendMode mode,
                                   const Lexicon* lexicon,
                                   const TokenTable* token_table);

const char* FrontendErrorCodeToString(FrontendErrorCode code);
const char* FrontendModeToString(FrontendMode mode);

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_FRONTEND_ROUTER_H_
