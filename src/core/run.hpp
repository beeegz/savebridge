#pragma once
#include "core/converter.hpp"
#include <string>

// Point d'entrée commun CLI / JNI / iOS.
// Retour : 0 OK, 1 erreur d'entrée, 2 échec de conversion. 'log' = journal lisible.
int runConversion(const std::string& gameId, const fs::path& in, const fs::path& out,
                  const Options& opt, std::string& log);
