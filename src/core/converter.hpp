#pragma once
#include <filesystem>
#include <memory>
#include <string>
#include <vector>

namespace fs = std::filesystem;

enum class Platform { WiiU, Switch, Unknown };

struct Options {
  bool force = false;      // ignore le contrôle de version
  std::string flagsFile = "botw_string_flags.txt"; // liste des flags string
};

struct Report {
  int converted = 0, copied = 0;
  std::vector<std::string> warnings;
};

// Un plugin par jeu : le format de save est propre à chaque titre.
class IGameConverter {
public:
  virtual ~IGameConverter() = default;
  virtual const char* id() const = 0;
  virtual const char* name() const = 0;
  // Détecte le jeu + la plateforme d'origine du dossier de save
  virtual Platform detect(const fs::path& saveDir) const = 0;
  // Convertit inDir -> outDir vers la plateforme 'target'
  virtual bool convert(const fs::path& inDir, const fs::path& outDir,
                       Platform target, const Options& opt,
                       Report& rep) const = 0;
};

namespace registry {
void add(std::unique_ptr<IGameConverter> c);
const std::vector<std::unique_ptr<IGameConverter>>& all();
const IGameConverter* find(const std::string& id);
void registerBuiltins();
}

inline const char* toStr(Platform p) {
  return p == Platform::WiiU ? "Wii U" : p == Platform::Switch ? "Switch" : "?";
}
