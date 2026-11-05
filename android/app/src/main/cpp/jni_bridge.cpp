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

// Retourne "<code>\n<journal>"
extern "C" JNIEXPORT jstring JNICALL
Java_fr_savebridge_Native_convert(JNIEnv* env, jclass, jstring in, jstring out,
                                  jstring flags, jboolean force) {
  Options opt;
  opt.force = force == JNI_TRUE;
  const std::string f = str(env, flags);
  if (!f.empty()) opt.flagsFile = f;

  std::string log;
  const int rc = runConversion("auto", str(env, in), str(env, out), opt, log);
  const std::string res = std::to_string(rc) + "\n" + log;
  return env->NewStringUTF(res.c_str());
}
