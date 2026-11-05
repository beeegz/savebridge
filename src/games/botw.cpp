// Zelda: Breath of the Wild — Wii U (big-endian) <-> Switch (little-endian)
//
// *.sav (game_data, option, caption) : header 0x0C, puis paires [hash u32][valeur u32].
//   hash = CRC32(nom du flag). Flags string : 'valeur' = 4 caractères bruts -> pas de swap.
// trackblock*.sav (Chemin du héros) : octets 0-3 bruts, u16 @0x04, puis u32 dès 0x08.
//
// Logique trackblock, table de versions et hashes de secours repris de
// BotW-Save-Manager (MIT, DeltaJordan / WemI0 / xombiemp / saranshjain29).
#include "core/converter.hpp"
#include "core/endian.hpp"
#include <array>
#include <fstream>
#include <iterator>
#include <unordered_set>

namespace {

uint32_t crc32(const std::string& s) {
  static const auto table = [] {
    std::array<uint32_t, 256> t{};
    for (uint32_t i = 0; i < 256; ++i) {
      uint32_t c = i;
      for (int k = 0; k < 8; ++k) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
      t[i] = c;
    }
    return t;
  }();
  uint32_t c = 0xFFFFFFFFu;
  for (unsigned char ch : s) c = table[(c ^ ch) & 0xFF] ^ (c >> 8);
  return c ^ 0xFFFFFFFFu;
}

bool readFile(const fs::path& p, std::vector<uint8_t>& out) {
  std::ifstream f(p, std::ios::binary);
  if (!f) return false;
  out.assign(std::istreambuf_iterator<char>(f), {});
  return true;
}
bool writeFile(const fs::path& p, const std::vector<uint8_t>& d) {
  fs::create_directories(p.parent_path());
  std::ofstream f(p, std::ios::binary);
  f.write(reinterpret_cast<const char*>(d.data()), (std::streamsize)d.size());
  return (bool)f;
}

// Versions du jeu : 16 bits bas du premier mot de header
struct Ver { uint16_t tag; const char* name; int rank; };
constexpr Ver kVersions[] = {
  {0x24E2, "1.0", 0}, {0x24EE, "1.1", 1}, {0x2588, "1.2", 2}, {0x29C0, "1.3", 3},
  {0x3EF8, "1.3.3", 4}, {0x471A, "1.4", 5}, {0x471B, "1.5", 6}, {0x471E, "1.6", 7},
};
constexpr int kWiiUMaxRank = 6; // Wii U plafonne en 1.5

const Ver* findVer(uint32_t v) {
  for (auto& x : kVersions) if (x.tag == v) return &x;
  return nullptr;
}

// Flags string connus (secours si la liste générée est absente)
constexpr uint32_t kFallbackStr[] = {
  0x7B74E117, 0xD913B769, 0xB666D246, 0x021A6FF2, 0xFF74960F, 0x8932285F, 0x3B0A289B,
  0x2F95768F, 0x9C6CFD3F, 0xBBAC416B, 0xCCAB71FD, 0xCBC6B5E4, 0x2CADB0E7, 0xA6EB3EF4,
  0x21D4CFFA, 0x22A510D1, 0x98D10D53, 0x55A22047, 0xE5A63A33, 0xBEC65061, 0xBC118370,
  0x0E9D0E75, 0x0BEE9E46 /*PlayerSavePosMapName*/, 0x1C10782F /*WolfLink_TodayFirstDetectedUID*/,
};

bool isTrackblock(const std::string& fn) { return fn.rfind("trackblock", 0) == 0; }

class Botw final : public IGameConverter {
  // Liste des flags string : fichier généré (tools/gen_botw_flags.py) + secours intégré
  static std::unordered_set<uint32_t> loadFlags(const std::string& path, bool& generated) {
    std::unordered_set<uint32_t> set;
    std::ifstream f(path);
    for (std::string l; std::getline(f, l);) {
      while (!l.empty() && (l.back() == '\r' || l.back() == ' ')) l.pop_back();
      if (!l.empty() && l[0] != '#') { set.insert(crc32(l)); generated = true; }
    }
    for (uint32_t h : kFallbackStr) { set.insert(h); set.insert(bswap32(h)); }
    return set;
  }

public:
  const char* id() const override { return "botw"; }
  const char* name() const override { return "Zelda: Breath of the Wild"; }
  const char* switchTitleId() const override { return "01007EF00011E000"; }
  const char* wiiuTitleIds() const override { return "101C9500 (EUR), 101C9400 (USA), 101C9300 (JPN)"; }

  // Racine = premier dossier (parcours en largeur) contenant option.sav
  fs::path findRoot(const fs::path& dir) const override {
    std::vector<fs::path> q{dir};
    for (size_t i = 0; i < q.size(); ++i) {
      std::error_code ec;
      if (fs::is_regular_file(q[i] / "option.sav", ec)) return q[i];
      for (auto& e : fs::directory_iterator(q[i], ec))
        if (e.is_directory(ec)) q.push_back(e.path());
    }
    return {};
  }

  // Détection sur le tag de version (endian-dépendant) de option.sav / game_data.sav
  static Platform detectBuf(const std::vector<uint8_t>& d, const Ver** ver = nullptr) {
    if (d.size() < 0x0C) return Platform::Unknown;
    if (auto v = findVer(read32(d.data(), Endian::Big)))    { if (ver) *ver = v; return Platform::WiiU; }
    if (auto v = findVer(read32(d.data(), Endian::Little))) { if (ver) *ver = v; return Platform::Switch; }
    return Platform::Unknown;
  }

  Platform detect(const fs::path& root) const override {
    std::vector<uint8_t> d;
    return readFile(root / "option.sav", d) ? detectBuf(d) : Platform::Unknown;
  }

  bool convert(const fs::path& in, const fs::path& out, Platform target,
               const Options& opt, Report& rep) const override {
    bool generated = false;
    const auto strHashes = loadFlags(opt.flagsFile, generated);
    if (!generated)
      rep.warnings.push_back("string flag list not provided: built-in fallback list used");

    // 1) Contrôle des versions avant toute écriture
    for (auto& e : fs::recursive_directory_iterator(in)) {
      if (e.path().filename() != "game_data.sav") continue;
      std::vector<uint8_t> d; readFile(e.path(), d);
      const Ver* v = nullptr;
      const Platform src = detectBuf(d, &v);
      const std::string rel = fs::relative(e.path(), in).string();
      if (src == Platform::Unknown) {
        rep.warnings.push_back("unknown save version: " + rel);
        if (!opt.force) { rep.versionBlocked = true; return false; }
        continue;
      }
      if (target == Platform::WiiU && v->rank > kWiiUMaxRank) {
        rep.warnings.push_back(rel + ": save v" + v->name + " > Wii U max 1.5 (downgrade)");
        if (!opt.force) { rep.versionBlocked = true; return false; }
      }
    }

    // 2) Conversion
    for (auto& e : fs::recursive_directory_iterator(in)) {
      if (!e.is_regular_file()) continue;
      const fs::path rel = fs::relative(e.path(), in);
      const fs::path dst = out / rel;
      const std::string fn = e.path().filename().string();

      std::vector<uint8_t> d;
      if (!readFile(e.path(), d)) { rep.warnings.push_back("read error: " + rel.string()); return false; }

      if (e.path().extension() != ".sav") { // caption.jpg, etc.
        writeFile(dst, d); ++rep.copied; continue;
      }
      if (d.size() % 4 || d.size() < 0x0C) {
        rep.warnings.push_back("invalid size: " + rel.string()); return false;
      }

      if (isTrackblock(fn)) {
        // Chemin du héros : pas de flags, pas de strings -> swap pur
        std::swap(d[4], d[5]);
        for (size_t o = 8; o + 4 <= d.size(); o += 4) swap32(&d[o]);
      } else {
        const Platform src = detectBuf(d);
        if (src == Platform::Unknown) {
          rep.warnings.push_back("unknown format: " + rel.string()); return false;
        }
        if (src == target) { writeFile(dst, d); ++rep.copied; continue; }
        const Endian se = src == Platform::WiiU ? Endian::Big : Endian::Little;

        for (size_t o = 0; o < 0x0C; o += 4) swap32(&d[o]);
        for (size_t o = 0x0C; o + 8 <= d.size(); o += 8) {
          const uint32_t h = read32(&d[o], se);
          swap32(&d[o]);
          if (!strHashes.count(h)) swap32(&d[o + 4]);
        }
        // Vérif : le résultat doit être reconnu comme plateforme cible
        if (detectBuf(d) != target) {
          rep.warnings.push_back("verification failed: " + rel.string()); return false;
        }
      }
      if (!writeFile(dst, d)) { rep.warnings.push_back("write error: " + rel.string()); return false; }
      ++rep.converted;
    }
    return true;
  }
};
} // namespace

std::unique_ptr<IGameConverter> makeBotw() { return std::make_unique<Botw>(); }
