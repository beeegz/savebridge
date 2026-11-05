#include "core/run.hpp"

int runConversion(const std::string& gameId, const fs::path& in, const fs::path& out,
                  const Options& opt, std::string& log) {
  registry::registerBuiltins();
  std::error_code ec;
  if (!fs::is_directory(in, ec)) { log += "Dossier source introuvable\n"; return 1; }
  if (fs::exists(out, ec) && !fs::is_empty(out, ec)) { log += "Dossier de sortie non vide\n"; return 1; }

  const IGameConverter* conv = nullptr;
  Platform src = Platform::Unknown;
  for (auto& c : registry::all()) {
    if (gameId != "auto" && gameId != c->id()) continue;
    if ((src = c->detect(in)) != Platform::Unknown) { conv = c.get(); break; }
  }
  if (!conv) { log += "Jeu/plateforme non reconnu (option.sav / game_data.sav absent ?)\n"; return 1; }

  const Platform dst = src == Platform::WiiU ? Platform::Switch : Platform::WiiU;
  log += std::string(conv->name()) + " : " + toStr(src) + " -> " + toStr(dst) + "\n";

  Report rep;
  bool ok = false;
  try {
    ok = conv->convert(in, out, dst, opt, rep);
  } catch (const std::exception& e) {
    rep.warnings.push_back(std::string("exception: ") + e.what());
  }
  for (auto& w : rep.warnings) log += "[!] " + w + "\n";
  log += std::string(ok ? "OK" : "ECHEC") + " : " + std::to_string(rep.converted) +
         " convertis, " + std::to_string(rep.copied) + " copiés\n";
  return ok ? 0 : 2;
}
