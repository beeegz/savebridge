cmake_minimum_required(VERSION 3.20)
project(savebridge LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)   # requis pour la .so Android
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

# Core portable (macOS/iOS arm64, Android NDK, Windows/Linux x64)
add_library(savebridge_core STATIC
  src/core/registry.cpp
  src/core/run.cpp
  src/games/botw.cpp)
target_include_directories(savebridge_core PUBLIC src)

if(MSVC)
  target_compile_options(savebridge_core PRIVATE /W4 /O2)
else()
  target_compile_options(savebridge_core PRIVATE -Wall -Wextra -O2)
endif()

if(ANDROID)
  # Bibliothèque JNI chargée par l'app (android/)
  add_library(savebridge_jni SHARED android/app/src/main/cpp/jni_bridge.cpp)
  target_link_libraries(savebridge_jni PRIVATE savebridge_core log)
  target_link_options(savebridge_jni PRIVATE -Wl,--gc-sections -s)
elseif(NOT IOS)
  # CLI desktop (iOS = lib embarquée dans l'app)
  add_executable(savebridge src/main.cpp)
  target_link_libraries(savebridge PRIVATE savebridge_core)
endif()
