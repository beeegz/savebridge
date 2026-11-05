#!/usr/bin/env python3
"""Extrait les noms des flags de type string depuis VOTRE dump BotW.
pip install oead
python gen_botw_flags.py <dump>/content/Pack/Bootup.pack > botw_string_flags.txt
"""
import sys
import oead

boot = oead.Sarc(open(sys.argv[1], "rb").read())
gd = oead.Sarc(oead.yaz0.decompress(boot.get_file("GameData/gamedata.ssarc").data))
names = set()
for f in gd.get_files():
    root = oead.byml.from_binary(f.data)
    for key, entries in root.items():
        if "string" in key.lower():
            for e in entries:
                names.add(str(e["DataName"]))
print("\n".join(sorted(names)))
