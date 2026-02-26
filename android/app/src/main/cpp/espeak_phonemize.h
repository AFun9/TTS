#ifndef SHERPA_TTS_ESPEAK_PHONEMIZE_H_
#define SHERPA_TTS_ESPEAK_PHONEMIZE_H_

#include <string>
#include <vector>

namespace sherpa_tts {

class TokenTable;

enum class EspeakErrorCode : int32_t {
  kOk = 0,
  kDisabled = 1,
  kInvalidArgs = 2,
  kInitFailed = 3,
  kMissingSpecialTokens = 4,
  kPhonemeEmpty = 5,
  kTokenMiss = 6,
};

struct EspeakResult {
  EspeakErrorCode code = EspeakErrorCode::kInvalidArgs;
  std::vector<int64_t> token_ids;
  int32_t phoneme_count = 0;
  int32_t matched_phoneme_count = 0;
};

// 当未提供 lexicon 时，用 espeak-ng 将文本转为音素再查 token 表（Piper/VITS 格式）。
// data_dir: espeak-ng-data 目录的绝对路径（如 .../models/espeak-ng-data）。
// voice: 可选，如 "ru" "en-us"。
// 返回 token id 序列；失败或未编入 espeak 支持时返回空。
EspeakResult TextToTokenIdsWithEspeakDetailed(const std::string& text,
                                              const std::string& data_dir,
                                              const std::string& voice,
                                              const TokenTable* token_table);

std::vector<int64_t> TextToTokenIdsWithEspeak(const std::string& text,
                                              const std::string& data_dir,
                                              const std::string& voice,
                                              const TokenTable* token_table);

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_ESPEAK_PHONEMIZE_H_
