#pragma once
#include "core/converter.hpp"
#include <string>

// Codes de retour (stables, traduits côté interface)
enum RunCode {
  RUN_OK = 0,
  RUN_BAD_INPUT = 1,       // dossier source/sortie invalide
  RUN_FAILED = 2,          // erreur pendant la conversion
  RUN_VERSION_BLOCKED = 3, // version de save incompatible avec la cible
  RUN_UNSUPPORTED = 4,     // aucun plugin ne reconnaît la save
};

struct RunResult {
  int code = RUN_BAD_INPUT;
  const IGameConverter* game = nullptr;
  Platform from = Platform::Unknown, to = Platform::Unknown;
  std::string log; // journal technique (anglais)
};

// Point d'entrée commun CLI / JNI / iOS. 'in' peut contenir la save à n'importe quelle profondeur.
RunResult runConversion(const std::string& gameId, const fs::path& in, const fs::path& out,
                        const Options& opt);
