// savebridge [--force] [--flags file] <game|auto> <save_in> <save_out>
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
    registry::registerBuiltins();
    std::printf("Usage: savebridge [--force] [--flags file] <game|auto> <save_in> <save_out>\nGames:");
    for (auto& c : registry::all()) std::printf(" %s (%s)", c->id(), c->name());
    std::printf("\n");
    return RUN_BAD_INPUT;
  }
  const RunResult r = runConversion(argv[i], argv[i + 1], argv[i + 2], opt);
  std::fputs(r.log.c_str(), r.code ? stderr : stdout);
  return r.code;
}
