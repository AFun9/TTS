#include "lexicon.h"

#include <cctype>
#include <fstream>
#include <sstream>
#include <unordered_set>

namespace sherpa_tts {

namespace {

void Trim(std::string* s) {
  const char* ws = " \t\n\r\f\v";
  s->erase(0, s->find_first_not_of(ws));
  s->erase(s->find_last_not_of(ws) + 1);
}

std::vector<std::string> SplitLine(const std::string& line) {
  std::vector<std::string> out;
  std::istringstream iss(line);
  std::string part;
  while (iss >> part) out.push_back(part);
  return out;
}

// UTF-8：返回从 pos 开始的字符占用的字节数，若非法则返回 1 并跳过该字节
static size_t Utf8CharLen(const std::string& s, size_t pos) {
  if (pos >= s.size()) return 0;
  unsigned char c = static_cast<unsigned char>(s[pos]);
  if (c < 0x80) return 1;
  if (c < 0xc2) return 1;
  if (c < 0xe0) return (pos + 2 <= s.size()) ? 2 : 1;
  if (c < 0xf0) return (pos + 3 <= s.size()) ? 3 : 1;
  if (c < 0xf8) return (pos + 4 <= s.size()) ? 4 : 1;
  return 1;
}

bool IsAsciiPunct(unsigned char c) {
  return std::ispunct(c) != 0;
}

bool IsUnicodePunct(const std::string& ch) {
  static const std::unordered_set<std::string> kPunct = {
      "，", "。", "！", "？", "；", "：", "、", "…", "—", "–",
      "（", "）", "《", "》", "【", "】", "「", "」", "『", "』"};
  return kPunct.count(ch) != 0;
}

// 按空白和标点切分（保留标点为单独 token），避免 "word," 导致 lexicon miss。
std::vector<std::string> SplitWords(const std::string& text) {
  std::vector<std::string> words;
  std::string cur;
  for (size_t i = 0; i < text.size();) {
    size_t clen = Utf8CharLen(text, i);
    if (clen == 0) break;
    std::string ch = text.substr(i, clen);
    if (clen == 1) {
      unsigned char c = static_cast<unsigned char>(text[i]);
      if (std::isspace(c)) {
        if (!cur.empty()) {
          words.push_back(std::move(cur));
          cur.clear();
        }
      } else if (IsAsciiPunct(c)) {
        if (!cur.empty()) {
          words.push_back(std::move(cur));
          cur.clear();
        }
        words.push_back(std::move(ch));
      } else {
        cur += ch;
      }
    } else {
      if (IsUnicodePunct(ch)) {
        if (!cur.empty()) {
          words.push_back(std::move(cur));
          cur.clear();
        }
        words.push_back(std::move(ch));
      } else {
        cur += ch;
      }
    }
    i += clen;
  }
  if (!cur.empty()) words.push_back(std::move(cur));
  return words;
}

}  // namespace

bool Lexicon::LoadFromFile(const std::string& path) {
  std::ifstream is(path);
  if (!is) return false;
  word_to_phonemes_.clear();
  std::string line;
  while (std::getline(is, line)) {
    Trim(&line);
    if (line.empty()) continue;
    std::vector<std::string> parts = SplitLine(line);
    if (parts.size() < 2) continue;
    std::string word = std::move(parts[0]);
    parts.erase(parts.begin());
    word_to_phonemes_[word] = std::move(parts);
  }
  return true;
}

bool Lexicon::Contains(const std::string& word) const {
  return word_to_phonemes_.count(word) != 0;
}

std::vector<std::string> Lexicon::GetPhonemes(const std::string& word) const {
  auto it = word_to_phonemes_.find(word);
  if (it == word_to_phonemes_.end()) return {};
  return it->second;
}

// 按空格切分，每段作为单个 symbol 查表（用于 "ni hao" 等音素/拼音串）
static std::vector<int64_t> TextToTokenIdsBySpaces(const std::string& text,
                                                   const TokenTable* token_table) {
  std::vector<int64_t> ids;
  std::istringstream iss(text);
  std::string tok;
  while (iss >> tok) {
    if (token_table->Contains(tok)) {
      ids.push_back(token_table->GetId(tok));
    }
  }
  return ids;
}

std::vector<int64_t> TextToTokenIds(const std::string& text,
                                    const Lexicon* lexicon,
                                    const TokenTable* token_table) {
  if (!token_table || token_table->Size() == 0) return {};
  std::vector<int64_t> ids;

  std::vector<std::string> words = SplitWords(text);
  for (const auto& w : words) {
    if (lexicon && lexicon->Size() > 0 && lexicon->Contains(w)) {
      std::vector<std::string> phonemes = lexicon->GetPhonemes(w);
      std::vector<int64_t> sub = token_table->SymbolsToIds(phonemes, false);
      for (int64_t id : sub) ids.push_back(id);
    } else {
      // 无词典或词不在词典：先试整词，再按 UTF-8 字符
      if (token_table->Contains(w)) {
        ids.push_back(token_table->GetId(w));
      } else {
        for (size_t i = 0; i < w.size(); ) {
          size_t clen = Utf8CharLen(w, i);
          if (clen == 0) break;
          std::string ch = w.substr(i, clen);
          if (token_table->Contains(ch)) {
            ids.push_back(token_table->GetId(ch));
          }
          i += clen;
        }
      }
    }
  }
  // 若仍为空且文本含空格：按「空格分隔的 token」再试（音素/拼音串如 "n i2 h ao3"）
  if (ids.empty() && text.find(' ') != std::string::npos) {
    ids = TextToTokenIdsBySpaces(text, token_table);
  }
  return ids;
}

}  // namespace sherpa_tts
