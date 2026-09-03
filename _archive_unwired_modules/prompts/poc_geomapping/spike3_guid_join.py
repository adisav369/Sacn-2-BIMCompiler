#!/usr/bin/env python3
"""Step 2.2 gate check: do source-IFC GlobalIds join corpus elements_meta.guid?
If yes, a relation SIDECAR mined from source IFC is directly usable against the
shipped DBs without re-extracting geometry (PR #543 landmine avoided)."""
import sqlite3, os, re

ROOT = "/home/red1/bim-compiler"
PAIRS = [
    ("SH", "internal/sources/Ifc4_SampleHouse.ifc", "deploy/buildings/SampleHouse_extracted.db"),
    ("DX-ARC", "internal/sources/Ifc2x3_Duplex_Architecture.ifc", "deploy/buildings/Duplex_extracted.db"),
    ("DX-MEP", "internal/UNMERGED/Ifc2x3_Duplex_MEP.ifc", "deploy/buildings/Duplex_extracted.db"),
    ("SC", "internal/sources/Ifc2x3_SampleCastle.ifc", "deploy/buildings/SampleCastle_extracted.db"),
]
# GUIDs of product-ish entities: grab every IFC entity line's first attr (GlobalId)
GUID_RE = re.compile(r"=\s*IFC[A-Z0-9]+\('([0-9A-Za-z_$]{22})'")

for tag, ifc_rel, db_rel in PAIRS:
    guids = set()
    with open(os.path.join(ROOT, ifc_rel), errors="ignore") as f:
        for line in f:
            m = GUID_RE.search(line)
            if m:
                guids.add(m.group(1))
    c = sqlite3.connect(f"file:{os.path.join(ROOT, db_rel)}?mode=ro", uri=True)
    db_guids = {r[0] for r in c.execute("SELECT guid FROM elements_meta")}
    c.close()
    hit = len(db_guids & guids)
    print(f"§JOIN {tag}: ifc-guids={len(guids)} db-elements={len(db_guids)} "
          f"db-guids-found-in-ifc={hit} ({100.0*hit/max(len(db_guids),1):.1f}%)")
