/**
 * 本项目 TTS JNI：纯本仓库实现的 TTS（token 表 + 词典 + VITS ONNX + WAV），不包含 sherpa-onnx。
 * 若未链接 ONNX Runtime（未定义 SHERPA_TTS_USE_ONNXRUNTIME），则为占位实现。
 */
#include <jni.h>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include <android/log.h>
#define LOG_TAG "SherpaTts"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

#if defined(SHERPA_TTS_USE_ONNXRUNTIME)
#include "frontend_router.h"
#include "lexicon.h"
#include "token_table.h"
#include "vits_engine.h"
#include "wave_writer.h"
#endif

namespace {

std::string JstringToStd(JNIEnv* env, jstring jstr) {
  if (!env || !jstr) return {};
  const char* utf = env->GetStringUTFChars(jstr, nullptr);
  if (!utf) return {};
  std::string s(utf);
  env->ReleaseStringUTFChars(jstr, utf);
  return s;
}

#if defined(SHERPA_TTS_USE_ONNXRUNTIME)
constexpr jint kErrInvalidHandle = -100;
constexpr jint kErrInvalidInput = -101;
constexpr jint kErrVitsRunEmpty = -102;
constexpr jint kErrWriteWave = -103;
#endif

}  // namespace

#if defined(SHERPA_TTS_USE_ONNXRUNTIME)
struct TtsHandle {
  sherpa_tts::TokenTable token_table;
  sherpa_tts::Lexicon lexicon;
  std::unique_ptr<sherpa_tts::VitsEngine> vits;
  int32_t speaker_id = 0;
  std::string data_dir;
  std::string voice = "ru";
  sherpa_tts::FrontendMode frontend_mode = sherpa_tts::FrontendMode::kAuto;
};
#endif

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_k2fsa_sherpa_tts_engine_TTSEngine_nativeCreate(
    JNIEnv* env, jobject /* thiz */, jstring modelPath, jstring tokensPath,
    jstring dataDir, jstring lexiconPath, jint frontendMode, jstring voice,
    jint speakerId, jfloat speed, jint numThreads, jboolean debug) {
#if !defined(SHERPA_TTS_USE_ONNXRUNTIME)
  (void)env;
  (void)modelPath;
  (void)tokensPath;
  (void)dataDir;
  (void)lexiconPath;
  (void)frontendMode;
  (void)voice;
  (void)speakerId;
  (void)speed;
  (void)numThreads;
  (void)debug;
  LOGW("nativeCreate: 当前为占位构建，未链接 ONNX Runtime。请设置 ONNXRUNTIME_ROOT 并重新编译以启用 TTS。");
  return 0;
#else
  (void)speed;
  (void)debug;

  std::string model = JstringToStd(env, modelPath);
  std::string tokens = JstringToStd(env, tokensPath);
  std::string data_dir = JstringToStd(env, dataDir);
  std::string lexicon = JstringToStd(env, lexiconPath);
  std::string voice_str = JstringToStd(env, voice);

  if (model.empty() || tokens.empty()) {
    LOGW("nativeCreate: model 或 tokens 路径为空");
    return 0;
  }

  auto h = std::make_unique<TtsHandle>();
  h->data_dir = data_dir;
  h->voice = voice_str.empty() ? "ru" : voice_str;
  if (frontendMode < static_cast<jint>(sherpa_tts::FrontendMode::kAuto) ||
      frontendMode > static_cast<jint>(sherpa_tts::FrontendMode::kEspeakOnly)) {
    LOGW("nativeCreate: frontendMode 非法=%d，回退为 auto", frontendMode);
    h->frontend_mode = sherpa_tts::FrontendMode::kAuto;
  } else {
    h->frontend_mode = static_cast<sherpa_tts::FrontendMode>(frontendMode);
  }
  if (!h->token_table.LoadFromFile(tokens)) {
    LOGW("nativeCreate: 加载 tokens 失败 path=%s", tokens.c_str());
    return 0;
  }

  if (!lexicon.empty()) {
    h->lexicon.LoadFromFile(lexicon);
  }

  sherpa_tts::VitsConfig vits_config;
  vits_config.model_path = model;
  vits_config.num_threads = numThreads > 0 ? numThreads : 1;
  h->vits = std::make_unique<sherpa_tts::VitsEngine>(vits_config);
  if (h->vits->SampleRate() <= 0) {
    LOGW("nativeCreate: VITS 模型加载失败或 sample_rate=0 path=%s", model.c_str());
    return 0;
  }

  h->speaker_id = speakerId;
  return reinterpret_cast<jlong>(h.release());
#endif
}

JNIEXPORT jint JNICALL
Java_com_k2fsa_sherpa_tts_engine_TTSEngine_nativeGenerate(
    JNIEnv* env, jobject /* thiz */, jlong handle, jstring text, jfloat speed,
    jstring outputWavPath) {
#if !defined(SHERPA_TTS_USE_ONNXRUNTIME)
  (void)env;
  (void)handle;
  (void)text;
  (void)speed;
  (void)outputWavPath;
  return 0;
#else
  if (handle == 0) return kErrInvalidHandle;
  TtsHandle* h = reinterpret_cast<TtsHandle*>(handle);
  if (!h->vits) return kErrInvalidHandle;

  std::string text_str = JstringToStd(env, text);
  std::string out_path = JstringToStd(env, outputWavPath);
  if (text_str.empty() || out_path.empty()) {
    LOGW("nativeGenerate: text 或 outputWavPath 为空");
    return kErrInvalidInput;
  }

  sherpa_tts::FrontendResult front = sherpa_tts::RouteTextToTokenIds(
      text_str, h->data_dir, h->voice, h->frontend_mode, &h->lexicon,
      &h->token_table);
  if (front.code != sherpa_tts::FrontendErrorCode::kOk) {
    LOGW("nativeGenerate: FrontendFail code=%s mode=%s text_len=%zu token_table=%zu lexicon=%zu data_dir_empty=%d voice=%s lexicon_tokens=%d espeak_phonemes=%d espeak_matched=%d",
         sherpa_tts::FrontendErrorCodeToString(front.code),
         sherpa_tts::FrontendModeToString(h->frontend_mode), text_str.size(),
         h->token_table.Size(), h->lexicon.Size(), h->data_dir.empty() ? 1 : 0,
         h->voice.c_str(), front.lexicon_token_count, front.espeak_phoneme_count,
         front.espeak_matched_count);
    return -static_cast<jint>(front.code);
  }

  std::vector<float> samples = h->vits->Run(front.token_ids, h->speaker_id, speed);
  if (samples.empty()) {
    LOGW("nativeGenerate: VITS Run 返回空");
    return kErrVitsRunEmpty;
  }

  int32_t sample_rate = h->vits->SampleRate();
  if (!sherpa_tts::WriteWave(out_path, sample_rate, samples)) {
    LOGW("nativeGenerate: WriteWave 失败 path=%s", out_path.c_str());
    return kErrWriteWave;
  }
  return static_cast<jint>(sample_rate);
#endif
}

JNIEXPORT void JNICALL
Java_com_k2fsa_sherpa_tts_engine_TTSEngine_nativeRelease(JNIEnv* env,
                                                         jobject /* thiz */,
                                                         jlong handle) {
  (void)env;
  if (handle == 0) return;
#if defined(SHERPA_TTS_USE_ONNXRUNTIME)
  delete reinterpret_cast<TtsHandle*>(handle);
#endif
}

}  // extern "C"
