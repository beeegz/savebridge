// savebridge [--force] [--flags fichier] <jeu|auto> <dossier_save_source> <dossier_sortie>
#include "core/run.hpp"
#include <cstdio>

int main(int argc, char** argv) {
  Options opt;
  int i = 1;
  for (; i < argc && argv[i][0] == '-' && argv[i][1] == '-'; ++i) {
    const std::string a = argv[i];
    if (a == "--force") opt.force = true;
    else if (a == "--flags" && i + 1 < argc) opt.flagsFile = argv[++i];
  }
  if (argc - i != 3) {
    std::printf("Usage: savebridge [--force] [--flags botw_string_flags.txt] <botw|auto> <save_in> <save_out>\n");
    return 1;
  }
  std::string log;
  const int rc = runConversion(argv[i], argv[i + 1], argv[i + 2], opt, log);
  std::fputs(log.c_str(), rc ? stderr : stdout);
  return rc;
}
