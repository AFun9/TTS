#include "wave_writer.h"

#include <algorithm>
#include <cstring>

namespace sherpa_tts {

namespace {

#pragma pack(push, 1)
struct WavHeader {
  char riff[4] = {'R', 'I', 'F', 'F'};
  uint32_t file_size;
  char wave[4] = {'W', 'A', 'V', 'E'};
  char fmt[4] = {'f', 'm', 't', ' '};
  uint32_t fmt_size = 16;
  uint16_t audio_format = 1;
  uint16_t num_channels = 1;
  uint32_t sample_rate;
  uint32_t byte_rate;
  uint16_t block_align = 2;
  uint16_t bits_per_sample = 16;
  char data[4] = {'d', 'a', 't', 'a'};
  uint32_t data_size;
};
#pragma pack(pop)

}  // namespace

bool WriteWave(const std::string& filename, int32_t sample_rate,
               const float* samples, int32_t n) {
  if (!samples || n <= 0) return false;
  std::ofstream out(filename, std::ios::binary);
  if (!out) return false;

  const uint32_t data_bytes = static_cast<uint32_t>(n) * 2u;
  WavHeader h;
  h.file_size = 36 + data_bytes;
  h.sample_rate = static_cast<uint32_t>(sample_rate);
  h.byte_rate = h.sample_rate * 2u;
  h.data_size = data_bytes;

  out.write(reinterpret_cast<const char*>(&h), sizeof(h));

  std::vector<int16_t> pcm(static_cast<size_t>(n));
  for (int32_t i = 0; i < n; ++i) {
    float f = samples[i];
    f = std::max(-1.f, std::min(1.f, f));
    pcm[i] = static_cast<int16_t>(f * 32767.f);
  }
  out.write(reinterpret_cast<const char*>(pcm.data()), data_bytes);
  return out.good();
}

bool WriteWave(const std::string& filename, int32_t sample_rate,
               const std::vector<float>& samples) {
  return WriteWave(filename, sample_rate, samples.data(),
                   static_cast<int32_t>(samples.size()));
}

}  // namespace sherpa_tts
