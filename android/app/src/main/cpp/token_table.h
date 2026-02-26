#ifndef SHERPA_TTS_TOKEN_TABLE_H_
#define SHERPA_TTS_TOKEN_TABLE_H_

#include <string>
#include <unordered_map>
#include <vector>

namespace sherpa_tts {

// 从 tokens 文件（每行 "symbol id" 或 "symbol\tid"）加载 symbol -> id 映射。
// 与常见 VITS/Piper tokens.txt 格式兼容。
class TokenTable {
 public:
  TokenTable() = default;

  // 从文件路径加载，失败返回 false。
  bool LoadFromFile(const std::string& path);

  // 符号是否存在
  bool Contains(const std::string& symbol) const;

  // 取 id，若不存在返回 -1
  int64_t GetId(const std::string& symbol) const;

  // 将一串符号转为 id 序列，未知符号跳过或返回空（由 strict 决定）
  std::vector<int64_t> SymbolsToIds(const std::vector<std::string>& symbols,
                                    bool strict = false) const;

  size_t Size() const { return symbol_to_id_.size(); }

 private:
  std::unordered_map<std::string, int64_t> symbol_to_id_;
};

}  // namespace sherpa_tts

#endif  // SHERPA_TTS_TOKEN_TABLE_H_
