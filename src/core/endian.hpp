#pragma once
#include <cstdint>
#include <cstring>
#if defined(_MSC_VER)
#include <stdlib.h>
#endif

enum class Endian { Big, Little };

inline uint32_t bswap32(uint32_t v) {
#if defined(_MSC_VER)
  return _byteswap_ulong(v);
#else
  return __builtin_bswap32(v);
#endif
}

inline bool hostLittle() {
  const uint16_t x = 1;
  return *reinterpret_cast<const uint8_t*>(&x) == 1;
}

// Lit un u32 encodé dans l'endianness 'e', retourne la valeur native
inline uint32_t read32(const uint8_t* p, Endian e) {
  uint32_t v; std::memcpy(&v, p, 4);
  return (hostLittle() == (e == Endian::Little)) ? v : bswap32(v);
}

// Inverse les 4 octets en place (BE <-> LE)
inline void swap32(uint8_t* p) {
  uint32_t v; std::memcpy(&v, p, 4);
  v = bswap32(v);
  std::memcpy(p, &v, 4);
}
