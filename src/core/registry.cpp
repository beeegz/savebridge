#include "core/converter.hpp"

std::unique_ptr<IGameConverter> makeBotw();

namespace registry {
static std::vector<std::unique_ptr<IGameConverter>>& store() {
  static std::vector<std::unique_ptr<IGameConverter>> s;
  return s;
}
void add(std::unique_ptr<IGameConverter> c) { store().push_back(std::move(c)); }
const std::vector<std::unique_ptr<IGameConverter>>& all() { return store(); }
const IGameConverter* find(const std::string& id) {
  for (auto& c : store()) if (id == c->id()) return c.get();
  return nullptr;
}
void registerBuiltins() {
  if (store().empty()) add(makeBotw());
  // Ajouter ici : add(makeMk8()); add(makeHyruleWarriors()); ...
}
}
