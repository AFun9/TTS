#ifndef SHERPA_TTS_WAVE_WRITER_H_
#define SHERPA_TTS_WAVE_WRITER_H_

#include <cstdint>
#include <fstream>
#include <string>
#include <vector>

namespace sherpa_tts {

// 单声道 16-bit PCM WAV。samples 范围 [-1, 1]，内部转为 int16 写入。
bool WriteWave(const std::string& filename, int32_t sample_rate,
               const float* samples, int32_t n);

bool WriteWave(const std::string& filename, int32_t sample_rate,
               const std::vector<float>& samples);

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_WAVE_WRITER_H_
