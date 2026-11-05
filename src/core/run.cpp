#include "core/run.hpp"

RunResult runConversion(const std::string& gameId, const fs::path& in, const fs::path& out,
                        const Options& opt) {
  registry::registerBuiltins();
  RunResult r;
  std::error_code ec;
  if (!fs::is_directory(in, ec)) { r.log = "source directory not found\n"; return r; }
  if (fs::exists(out, ec) && !fs::is_empty(out, ec)) { r.log = "output directory not empty\n"; return r; }

  fs::path root;
  for (auto& c : registry::all()) {
    if (gameId != "auto" && gameId != c->id()) continue;
    fs::path p = c->findRoot(in);
    if (p.empty()) continue;
    if ((r.from = c->detect(p)) != Platform::Unknown) { r.game = c.get(); root = p; break; }
  }
  if (!r.game) {
    r.code = RUN_UNSUPPORTED;
    r.log = "no supported save found\n";
    return r;
  }

  r.to = r.from == Platform::WiiU ? Platform::Switch : Platform::WiiU;
  r.log = std::string("game: ") + r.game->name() + "\n" + toStr(r.from) + " -> " + toStr(r.to) + "\n";

  Report rep;
  bool ok = false;
  try {
    ok = r.game->convert(root, out, r.to, opt, rep);
  } catch (const std::exception& e) {
    rep.warnings.push_back(std::string("exception: ") + e.what());
  }
  for (auto& w : rep.warnings) r.log += "[!] " + w + "\n";
  r.log += std::string(ok ? "OK" : "FAILED") + ": " + std::to_string(rep.converted) +
           " converted, " + std::to_string(rep.copied) + " copied\n";
  r.code = ok ? RUN_OK : rep.versionBlocked ? RUN_VERSION_BLOCKED : RUN_FAILED;
  return r;
}
