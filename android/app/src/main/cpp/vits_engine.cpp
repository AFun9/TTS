#include "vits_engine.h"

#include <fstream>
#include <sstream>
#include <vector>

#include <onnxruntime_cxx_api.h>

namespace sherpa_tts {

namespace {

static std::vector<char> ReadFile(const std::string& path) {
  std::ifstream is(path, std::ios::binary | std::ios::ate);
  if (!is) return {};
  size_t size = is.tellg();
  is.seekg(0);
  std::vector<char> buf(size);
  if (!is.read(buf.data(), size)) return {};
  return buf;
}

static std::string GetInputName(Ort::Session* sess, size_t index,
                               OrtAllocator* allocator) {
#if ORT_API_VERSION >= 12
  auto v = sess->GetInputNameAllocated(index, allocator);
  return std::string(v.get());
#else
  auto* v = sess->GetInputName(index, allocator);
  std::string ans = v ? v : "";
  if (v && allocator) allocator->Free(allocator, v);
  return ans;
#endif
}

static std::string GetOutputName(Ort::Session* sess, size_t index,
                                 OrtAllocator* allocator) {
#if ORT_API_VERSION >= 12
  auto v = sess->GetOutputNameAllocated(index, allocator);
  return std::string(v.get());
#else
  auto* v = sess->GetOutputName(index, allocator);
  std::string ans = v ? v : "";
  if (v && allocator) allocator->Free(allocator, v);
  return ans;
#endif
}

static void GetInputNames(Ort::Session* sess, std::vector<std::string>* names,
                          std::vector<const char*>* ptrs) {
  Ort::AllocatorWithDefaultOptions allocator;
  size_t n = sess->GetInputCount();
  names->resize(n);
  ptrs->resize(n);
  for (size_t i = 0; i < n; ++i) {
    (*names)[i] = GetInputName(sess, i, allocator);
    (*ptrs)[i] = (*names)[i].c_str();
  }
}

static void GetOutputNames(Ort::Session* sess, std::vector<std::string>* names,
                           std::vector<const char*>* ptrs) {
  Ort::AllocatorWithDefaultOptions allocator;
  size_t n = sess->GetOutputCount();
  names->resize(n);
  ptrs->resize(n);
  for (size_t i = 0; i < n; ++i) {
    (*names)[i] = GetOutputName(sess, i, allocator);
    (*ptrs)[i] = (*names)[i].c_str();
  }
}

static std::string GetMetadataStr(Ort::Session* sess, const char* key) {
  try {
    Ort::ModelMetadata meta = sess->GetModelMetadata();
    Ort::AllocatorWithDefaultOptions allocator;
#if ORT_API_VERSION >= 12
    auto v = meta.LookupCustomMetadataMapAllocated(key, allocator);
    return v ? std::string(v.get()) : "";
#else
    const char* v = meta.LookupCustomMetadataMap(key, allocator);
    return v ? std::string(v) : "";
#endif
  } catch (...) {
    return "";
  }
}

static int32_t GetMetadataInt(Ort::Session* sess, const char* key,
                              int32_t default_val) {
  std::string s = GetMetadataStr(sess, key);
  if (s.empty()) return default_val;
  return static_cast<int32_t>(std::atoi(s.c_str()));
}

}  // namespace

class VitsEngine::Impl {
 public:
  Ort::Env env_{ORT_LOGGING_LEVEL_WARNING};
  Ort::SessionOptions opts_;
  std::unique_ptr<Ort::Session> sess_;
  std::vector<std::string> input_names_;
  std::vector<const char*> input_names_ptr_;
  std::vector<std::string> output_names_;
  std::vector<const char*> output_names_ptr_;
  int32_t sample_rate_ = 22050;
  int32_t num_speakers_ = 0;
  bool is_piper_or_coqui_ = false;
  float noise_scale_ = 0.667f;
  float noise_scale_w_ = 0.8f;
  float length_scale_ = 1.0f;

  explicit Impl(const VitsConfig& config)
      : noise_scale_(config.noise_scale),
        noise_scale_w_(config.noise_scale_w),
        length_scale_(config.length_scale) {
    opts_.SetIntraOpNumThreads(config.num_threads);
    opts_.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    std::vector<char> model_data = ReadFile(config.model_path);
    if (model_data.empty()) return;

    sess_ = std::make_unique<Ort::Session>(env_, model_data.data(),
                                           model_data.size(), opts_);
    GetInputNames(sess_.get(), &input_names_, &input_names_ptr_);
    GetOutputNames(sess_.get(), &output_names_, &output_names_ptr_);

    sample_rate_ = GetMetadataInt(sess_.get(), "sample_rate", 22050);
    num_speakers_ = GetMetadataInt(sess_.get(), "n_speakers", 0);
    std::string comment = GetMetadataStr(sess_.get(), "comment");
    is_piper_or_coqui_ = (comment.find("piper") != std::string::npos ||
                          comment.find("coqui") != std::string::npos);
  }

  std::vector<float> Run(const std::vector<int64_t>& token_ids, int64_t sid,
                         float speed) {
    if (!sess_ || token_ids.empty()) return {};
    Ort::MemoryInfo memory_info =
        Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);

    const int64_t seq_len = static_cast<int64_t>(token_ids.size());
    std::array<int64_t, 2> x_shape = {1, seq_len};
    Ort::Value x_tensor = Ort::Value::CreateTensor(
        memory_info, const_cast<int64_t*>(token_ids.data()), token_ids.size(),
        x_shape.data(), x_shape.size());

    int64_t len_shape = 1;
    int64_t length_val = seq_len;
    Ort::Value x_length = Ort::Value::CreateTensor(
        memory_info, &length_val, 1, &len_shape, 1);

    float length_scale = length_scale_;
    if (speed > 0 && speed != 1.f) length_scale = 1.f / speed;

    std::vector<Ort::Value> inputs;
    inputs.reserve(6);

    if (is_piper_or_coqui_ && input_names_.size() >= 3) {
      std::array<float, 3> scales = {noise_scale_, length_scale, noise_scale_w_};
      int64_t scale_shape = 3;
      Ort::Value scales_tensor = Ort::Value::CreateTensor(
          memory_info, scales.data(), 3, &scale_shape, 1);
      inputs.push_back(std::move(x_tensor));
      inputs.push_back(std::move(x_length));
      inputs.push_back(std::move(scales_tensor));
      if (input_names_.size() >= 4 && input_names_[3] == "sid") {
        int64_t sid_shape = 1;
        Ort::Value sid_tensor = Ort::Value::CreateTensor(
            memory_info, &sid, 1, &sid_shape, 1);
        inputs.push_back(std::move(sid_tensor));
      }
      if (input_names_.size() >= 5 && input_names_[4] == "langid") {
        int64_t lang_id = 0;
        int64_t lang_shape = 1;
        Ort::Value lang_tensor = Ort::Value::CreateTensor(
            memory_info, &lang_id, 1, &lang_shape, 1);
        inputs.push_back(std::move(lang_tensor));
      }
    } else {
      int64_t one = 1;
      Ort::Value ns = Ort::Value::CreateTensor(memory_info, &noise_scale_, 1,
                                               &one, 1);
      Ort::Value ls = Ort::Value::CreateTensor(memory_info, &length_scale, 1,
                                               &one, 1);
      Ort::Value nsw = Ort::Value::CreateTensor(memory_info, &noise_scale_w_, 1,
                                                &one, 1);
      inputs.push_back(std::move(x_tensor));
      inputs.push_back(std::move(x_length));
      inputs.push_back(std::move(ns));
      inputs.push_back(std::move(ls));
      inputs.push_back(std::move(nsw));
      if (input_names_.size() >= 6 &&
          (input_names_.back() == "sid" || input_names_.back() == "speaker")) {
        int64_t sid_shape = 1;
        Ort::Value sid_tensor = Ort::Value::CreateTensor(
            memory_info, &sid, 1, &sid_shape, 1);
        inputs.push_back(std::move(sid_tensor));
      }
    }

    auto out = sess_->Run(
        {}, input_names_ptr_.data(), inputs.data(), inputs.size(),
        output_names_ptr_.data(), output_names_ptr_.size());
    if (out.empty()) return {};

    Ort::Value& audio = out[0];
    auto shape = audio.GetTensorTypeAndShapeInfo().GetShape();
    int64_t total = 1;
    for (auto d : shape) total *= d;
    const float* p = audio.GetTensorData<float>();
    return std::vector<float>(p, p + total);
  }
};

VitsEngine::VitsEngine(const VitsConfig& config) : impl_(std::make_unique<Impl>(config)) {
  if (impl_->sess_) {
    sample_rate_ = impl_->sample_rate_;
    num_speakers_ = impl_->num_speakers_;
  }
}

VitsEngine::~VitsEngine() = default;

std::vector<float> VitsEngine::Run(const std::vector<int64_t>& token_ids,
                                   int64_t sid, float speed) {
  if (!impl_->sess_) return {};
  return impl_->Run(token_ids, sid, speed);
}

}  // namespace sherpa_tts
