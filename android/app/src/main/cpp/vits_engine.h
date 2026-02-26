#ifndef SHERPA_TTS_VITS_ENGINE_H_
#define SHERPA_TTS_VITS_ENGINE_H_

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace sherpa_tts {

struct VitsConfig {
  std::string model_path;
  int num_threads = 1;
  float noise_scale = 0.667f;
  float noise_scale_w = 0.8f;
  float length_scale = 1.0f;
};

// VITS ONNX 推理：输入 token id 序列，输出 float 音频与采样率。
// 参考常见 VITS/Piper/Coqui 导出格式，根据模型 input 名称自动选择输入顺序。
class VitsEngine {
 public:
  explicit VitsEngine(const VitsConfig& config);
  ~VitsEngine();

  VitsEngine(const VitsEngine&) = delete;
  VitsEngine& operator=(const VitsEngine&) = delete;

  int32_t SampleRate() const { return sample_rate_; }
  int32_t NumSpeakers() const { return num_speakers_; }

  // 返回生成的 float 音频；失败返回空。
  std::vector<float> Run(const std::vector<int64_t>& token_ids, int64_t sid = 0,
                         float speed = 1.0f);

 private:
  class Impl;
  std::unique_ptr<Impl> impl_;
  int32_t sample_rate_ = 0;
  int32_t num_speakers_ = 0;
};

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_VITS_ENGINE_H_
