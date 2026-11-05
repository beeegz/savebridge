#include <jni.h>
#include "core/run.hpp"

namespace {
std::string str(JNIEnv* env, jstring s) {
  if (!s) return {};
  const char* c = env->GetStringUTFChars(s, nullptr);
  std::string r(c);
  env->ReleaseStringUTFChars(s, c);
  return r;
}
}

// Résultat "clé=valeur" par ligne, puis "---" et le journal brut.
extern "C" JNIEXPORT jstring JNICALL
Java_fr_savebridge_Native_convert(JNIEnv* env, jclass, jstring in, jstring out,
                                  jstring flags, jboolean force) {
  Options opt;
  opt.force = force == JNI_TRUE;
  const std::string f = str(env, flags);
  if (!f.empty()) opt.flagsFile = f;

  const RunResult r = runConversion("auto", str(env, in), str(env, out), opt);
  std::string s = "code=" + std::to_string(r.code) + "\n";
  if (r.game) {
    s += std::string("game=") + r.game->name() + "\n";
    s += std::string("switch_id=") + r.game->switchTitleId() + "\n";
    s += std::string("wiiu_ids=") + r.game->wiiuTitleIds() + "\n";
  }
  s += std::string("from=") + toStr(r.from) + "\nto=" + toStr(r.to) + "\n---\n" + r.log;
  return env->NewStringUTF(s.c_str());
}

// Jeux pris en charge, un nom par ligne
extern "C" JNIEXPORT jstring JNICALL
Java_fr_savebridge_Native_games(JNIEnv* env, jclass) {
  registry::registerBuiltins();
  std::string s;
  for (auto& c : registry::all()) s += std::string(c->name()) + "\n";
  return env->NewStringUTF(s.c_str());
}
