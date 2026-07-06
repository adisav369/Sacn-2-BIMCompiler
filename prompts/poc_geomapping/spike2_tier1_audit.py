#!/usr/bin/env python3
"""POC Spike 2 (RESUME_IFC_BOM_GEOMAPPING.md §WORKFLOW Phase 2 item 2):
Tier-1 ceiling audit — what relational data is captured today, per building,
and what fraction of elements each signal actually covers.
Read-only. Disposable."""
import sqlite3, os, json

DBS = {
    "SH": "deploy/buildings/SampleHouse_extracted.db",
    "DX": "deploy/buildings/Duplex_extracted.db",
    "SC": "deploy/buildings/SampleCastle_extracted.db",
    "Terminal": "deploy/buildings/Terminal_extracted.db",
}
ROOT = "/home/red1/bim-compiler"

def q1(c, sql, params=()):
    try:
        r = c.execute(sql, params).fetchone()
        return r[0] if r else None
    except sqlite3.OperationalError as e:
        return f"ERR:{e}"

for tag, rel in DBS.items():
    p = os.path.join(ROOT, rel)
    c = sqlite3.connect(f"file:{p}?mode=ro", uri=True)
    tables = [r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type IN ('table','view')")]
    n = q1(c, "SELECT COUNT(*) FROM elements_meta")
    print(f"\n=== {tag} ({rel})  elements={n} ===")
    print(f"§tables: {sorted(t for t in tables if not t.startswith('elements_rtree_'))}")
    # per-signal coverage
    cov = {}
    cov["element_type_nonnull"] = q1(c, "SELECT COUNT(*) FROM elements_meta WHERE element_type IS NOT NULL AND element_type != ''")
    cov["element_name_nonnull"] = q1(c, "SELECT COUNT(*) FROM elements_meta WHERE element_name IS NOT NULL AND element_name != ''")
    cov["storey_known"] = q1(c, "SELECT COUNT(*) FROM elements_meta WHERE storey IS NOT NULL AND storey NOT IN ('','Unknown')")
    try:
        cols = [r[1] for r in c.execute("PRAGMA table_info(elements_meta)")]
    except sqlite3.OperationalError:
        cols = []
    print(f"§elements_meta cols: {cols}")
    if "material_name" in cols:
        cov["material_name"] = q1(c, "SELECT COUNT(*) FROM elements_meta WHERE material_name IS NOT NULL AND material_name != ''")
        cov["material_rgba"] = q1(c, "SELECT COUNT(*) FROM elements_meta WHERE material_rgba IS NOT NULL AND material_rgba != ''")
    for t in ("rel_contained_in_space", "rel_fills_host", "rel_aggregates"):
        if t in tables:
            cov[t + "_rows"] = q1(c, f"SELECT COUNT(*) FROM {t}")
        else:
            cov[t + "_rows"] = "ABSENT"
    if "spatial_structure" in tables:
        try:
            for typ, cnt in c.execute("SELECT type, COUNT(*) FROM spatial_structure GROUP BY type"):
                cov[f"spatial:{typ}"] = cnt
        except sqlite3.OperationalError as e:
            cov["spatial_structure"] = f"ERR:{e}"
    if "element_transforms" in tables:
        tcols = [r[1] for r in c.execute("PRAGMA table_info(element_transforms)")]
        cov["transform_cols"] = ",".join(tcols)
        try:
            for src, cnt in c.execute("SELECT transform_source, COUNT(*) FROM element_transforms GROUP BY transform_source"):
                cov[f"tx_src:{src}"] = cnt
        except sqlite3.OperationalError:
            pass
    for k, v in cov.items():
        if isinstance(v, int) and n and not k.endswith("_rows") and not k.startswith(("spatial:", "tx_src:")) and k != "transform_cols":
            print(f"§{k}: {v} ({100.0*v/n:.1f}%)")
        else:
            print(f"§{k}: {v}")
    # class census
    print("§class census:")
    for cls, cnt in c.execute("SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"):
        print(f"    {cls:<32}{cnt:>6}")
    # element_type sample per class (top 3 classes)
    c.close()
