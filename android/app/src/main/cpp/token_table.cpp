#include "token_table.h"

#include <fstream>
#include <sstream>

namespace sherpa_tts {

namespace {

void Trim(std::string* s) {
  const char* ws = " \t\n\r\f\v";
  s->erase(0, s->find_first_not_of(ws));
  s->erase(s->find_last_not_of(ws) + 1);
}

}  // namespace

bool TokenTable::LoadFromFile(const std::string& path) {
  std::ifstream is(path);
  if (!is) return false;
  symbol_to_id_.clear();
  std::string line;
  while (std::getline(is, line)) {
    Trim(&line);
    if (line.empty()) continue;
    std::istringstream iss(line);
    std::string sym;
    int64_t id = -1;
    iss >> sym;
    if (iss.eof()) {
      id = static_cast<int64_t>(std::atoi(sym.c_str()));
      sym = " ";
    } else {
      iss >> id;
    }
    if (id < 0) continue;
    symbol_to_id_[std::move(sym)] = id;
  }
  return !symbol_to_id_.empty();
}

bool TokenTable::Contains(const std::string& symbol) const {
  return symbol_to_id_.count(symbol) != 0;
}

int64_t TokenTable::GetId(const std::string& symbol) const {
  auto it = symbol_to_id_.find(symbol);
  if (it == symbol_to_id_.end()) return -1;
  return it->second;
}

std::vector<int64_t> TokenTable::SymbolsToIds(
    const std::vector<std::string>& symbols, bool strict) const {
  std::vector<int64_t> ids;
  ids.reserve(symbols.size());
  for (const auto& s : symbols) {
    int64_t id = GetId(s);
    if (id < 0) {
      if (strict) return {};
      continue;
    }
    ids.push_back(id);
  }
  return ids;
}

}  // namespace sherpa_tts
