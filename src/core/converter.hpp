#pragma once
#include <filesystem>
#include <memory>
#include <string>
#include <vector>

namespace fs = std::filesystem;

enum class Platform { WiiU, Switch, Unknown };

struct Options {
  bool force = false;                                // ignore le contrôle de version
  std::string flagsFile = "botw_string_flags.txt";   // données propres à un plugin
};

struct Report {
  int converted = 0, copied = 0;
  bool versionBlocked = false; // conversion refusée pour cause de version
  std::vector<std::string> warnings;
};

// Un plugin par jeu : chaque titre a son propre format de sauvegarde.
class IGameConverter {
public:
  virtual ~IGameConverter() = default;
  virtual const char* id() const = 0;
  virtual const char* name() const = 0;
  virtual const char* switchTitleId() const = 0;  // ex. 01007EF00011E000
  virtual const char* wiiuTitleIds() const = 0;   // ex. "101C9500 (EUR), 101C9400 (USA)"
  // Dossier racine de la save trouvé sous 'dir' (n'importe quelle profondeur), vide sinon
  virtual fs::path findRoot(const fs::path& dir) const = 0;
  // Plateforme d'origine d'un dossier racine
  virtual Platform detect(const fs::path& root) const = 0;
  virtual bool convert(const fs::path& root, const fs::path& outDir,
                       Platform target, const Options& opt, Report& rep) const = 0;
};

namespace registry {
void add(std::unique_ptr<IGameConverter> c);
const std::vector<std::unique_ptr<IGameConverter>>& all();
const IGameConverter* find(const std::string& id);
void registerBuiltins();
}

inline const char* toStr(Platform p) {
  return p == Platform::WiiU ? "wiiu" : p == Platform::Switch ? "switch" : "unknown";
}
