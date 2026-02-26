#ifndef SHERPA_TTS_LEXICON_H_
#define SHERPA_TTS_LEXICON_H_

#include <string>
#include <unordered_map>
#include <vector>

#include "token_table.h"

namespace sherpa_tts {

// 词典：词 -> 音素符号序列。配合 TokenTable 将文本转为 token id 序列。
// 词典文件格式：每行 "词 音素1 音素2 ..."（空格分隔）。
class Lexicon {
 public:
  Lexicon() = default;

  // 从文件加载，失败返回 false。
  bool LoadFromFile(const std::string& path);

  // 是否包含该词
  bool Contains(const std::string& word) const;

  // 获取词的音素符号序列（未找到返回空）
  std::vector<std::string> GetPhonemes(const std::string& word) const;

  size_t Size() const { return word_to_phonemes_.size(); }

 private:
  std::unordered_map<std::string, std::vector<std::string>> word_to_phonemes_;
};

// 将文本按空格/标点切词，查词典得到音素序列，再通过 TokenTable 转为 id 序列。
// 若词典为空则按字符尝试（TokenTable 中有单字符则用单字符 id）。
std::vector<int64_t> TextToTokenIds(const std::string& text,
                                    const Lexicon* lexicon,
                                    const TokenTable* token_table);

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_LEXICON_H_
