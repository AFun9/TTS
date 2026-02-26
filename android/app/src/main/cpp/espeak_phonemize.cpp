#include "espeak_phonemize.h"
#include "token_table.h"

#include <string>
#include <vector>
#if defined(SHERPA_TTS_USE_ESPEAK_NG)
#include <mutex>
#include "espeak-ng/speak_lib.h"
#include "phonemize.hpp"  // piper-phonemize
#endif

namespace sherpa_tts {

namespace {

// char32_t -> UTF-8 string (single character)
std::string CodepointToUtf8(char32_t cp) {
  std::string s;
  if (cp <= 0x7F) {
    s += static_cast<char>(cp);
  } else if (cp <= 0x7FF) {
    s += static_cast<char>(0xC0 | ((cp >> 6) & 0x1F));
    s += static_cast<char>(0x80 | (cp & 0x3F));
  } else if (cp <= 0xFFFF) {
    s += static_cast<char>(0xE0 | ((cp >> 12) & 0x0F));
    s += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
    s += static_cast<char>(0x80 | (cp & 0x3F));
  } else if (cp <= 0x10FFFF) {
    s += static_cast<char>(0xF0 | ((cp >> 18) & 0x07));
    s += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
    s += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
    s += static_cast<char>(0x80 | (cp & 0x3F));
  }
  return s;
}

}  // namespace

#if defined(SHERPA_TTS_USE_ESPEAK_NG)

static std::mutex g_espeak_mutex;
static bool g_espeak_inited = false;
static std::string g_espeak_data_dir;

bool InitEspeakOnce(const std::string& data_dir) {
  if (data_dir.empty()) return false;
  if (g_espeak_inited && g_espeak_data_dir == data_dir) {
    return true;
  }
  std::lock_guard<std::mutex> lock(g_espeak_mutex);
  if (g_espeak_inited && g_espeak_data_dir == data_dir) {
    return true;
  }
  int r = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, data_dir.c_str(), 0);
  g_espeak_inited = (r == 22050);
  if (g_espeak_inited) {
    g_espeak_data_dir = data_dir;
  }
  return g_espeak_inited;
}

EspeakResult TextToTokenIdsWithEspeakDetailed(const std::string& text,
                                              const std::string& data_dir,
                                              const std::string& voice,
                                              const TokenTable* token_table) {
  EspeakResult result;
  if (!token_table || token_table->Size() == 0 || text.empty()) {
    result.code = EspeakErrorCode::kInvalidArgs;
    return result;
  }
  if (!InitEspeakOnce(data_dir)) {
    result.code = EspeakErrorCode::kInitFailed;
    return result;
  }

  int64_t bid = token_table->GetId("^");
  int64_t pid = token_table->GetId("_");
  int64_t eid = token_table->GetId("$");
  if (bid < 0 || pid < 0 || eid < 0) {
    result.code = EspeakErrorCode::kMissingSpecialTokens;
    return result;
  }

  std::vector<std::vector<piper::Phoneme>> phonemes;
  {
    std::lock_guard<std::mutex> lock(g_espeak_mutex);
    piper::eSpeakPhonemeConfig config;
    config.voice = voice.empty() ? "ru" : voice;
    piper::phonemize_eSpeak(text, config, phonemes);
  }
  if (phonemes.empty()) {
    result.code = EspeakErrorCode::kPhonemeEmpty;
    return result;
  }

  std::vector<int64_t> ids;
  ids.push_back(bid);
  for (const auto& p : phonemes) {
    for (piper::Phoneme ph : p) {
      result.phoneme_count += 1;
      std::string sym = CodepointToUtf8(static_cast<char32_t>(ph));
      if (token_table->Contains(sym)) {
        ids.push_back(token_table->GetId(sym));
        ids.push_back(pid);
        result.matched_phoneme_count += 1;
      }
    }
  }
  ids.push_back(eid);

  if (result.matched_phoneme_count == 0) {
    result.code = EspeakErrorCode::kTokenMiss;
    return result;
  }

  result.code = EspeakErrorCode::kOk;
  result.token_ids = std::move(ids);
  return result;
}

#else  // !SHERPA_TTS_USE_ESPEAK_NG

EspeakResult TextToTokenIdsWithEspeakDetailed(const std::string& /*text*/,
                                              const std::string& /*data_dir*/,
                                              const std::string& /*voice*/,
                                              const TokenTable* /*token_table*/) {
  EspeakResult result;
  result.code = EspeakErrorCode::kDisabled;
  return result;
}

#endif

std::vector<int64_t> TextToTokenIdsWithEspeak(const std::string& text,
                                              const std::string& data_dir,
                                              const std::string& voice,
                                              const TokenTable* token_table) {
  return TextToTokenIdsWithEspeakDetailed(text, data_dir, voice, token_table)
      .token_ids;
}

}  // namespace sherpa_tts
