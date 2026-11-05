#!/usr/bin/env python3
"""Tests synthétiques : round-trip Wii U 1.5 <-> Switch + refus 1.6 -> Wii U."""
import filecmp, struct, subprocess, sys, tempfile, zlib
from pathlib import Path

exe = sys.argv[1]
t = Path(tempfile.mkdtemp())

def sav(e, ver):
    h = zlib.crc32(b"PlayerSavePosMapName")
    return (struct.pack(e + "III", ver, 0xFFFFFFFF, 1) + struct.pack(e + "II", 0x1234, 500)
            + struct.pack(e + "I", h) + b"MapA")

def run(*a):
    return subprocess.run([exe, *a], capture_output=True, text=True).returncode

(t / "wiiu/0").mkdir(parents=True)
(t / "wiiu/option.sav").write_bytes(sav(">", 0x471B))
(t / "wiiu/0/game_data.sav").write_bytes(sav(">", 0x471B))
(t / "wiiu/0/caption.jpg").write_bytes(b"\xff\xd8JPEG")
(t / "wiiu/0/trackblock00.sav").write_bytes(b"TRAK" + struct.pack(">H", 7) + b"\0\0" + struct.pack(">ff", 1.5, -2.0))

assert run("auto", str(t / "wiiu"), str(t / "sw")) == 0
g = (t / "sw/0/game_data.sav").read_bytes()
assert g[:4] == struct.pack("<I", 0x471B) and g[-4:] == b"MapA", "conversion incorrecte"
assert run("auto", str(t / "sw"), str(t / "back")) == 0
for f in ["option.sav", "0/game_data.sav", "0/trackblock00.sav", "0/caption.jpg"]:
    assert filecmp.cmp(t / "wiiu" / f, t / "back" / f, shallow=False), f

(t / "sw16/0").mkdir(parents=True)
(t / "sw16/option.sav").write_bytes(sav("<", 0x471E))
(t / "sw16/0/game_data.sav").write_bytes(sav("<", 0x471E))
assert run("auto", str(t / "sw16"), str(t / "x")) == 2, "le downgrade 1.6 aurait dû être refusé"
assert run("--force", "auto", str(t / "sw16"), str(t / "y")) == 0
print("selftest OK")
