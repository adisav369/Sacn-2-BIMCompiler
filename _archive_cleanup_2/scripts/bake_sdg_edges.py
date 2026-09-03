#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — bake SDG edges into a DEPLOYED (library-schema) building DB for the §FORWARD live demo.
# Scope: the live viewer streams a library-schema DB (component_geometries + 4D tables) that carries
# element_transforms(center + bbox) but NOT the SDG edge tables nor an elements_rtree — and it is in a
# DIFFERENT per-element frame than the raw *_extracted.db fixture (verified: not a constant offset). So the
# ONLY correct source for datums in the viewer's frame is the served DB ITSELF: build AABBs from its own
# center ± bbox/2 (exactly what the viewer renders from) and run the SHIPPED derive functions on them — no
# logic drift, no frame mismatch, zero invented. Idempotent. Read the §-log as proof.
#
# What it writes (matching the extractor schema): elements_rtree (from center±bbox/2), datum_plane,
# rel_anchored, rel_spans. It does NOT fabricate rel_aggregates/rel_fills_host (those are IFC-recovered, not
# derivable from geometry — absent in the library DB) → the live fold runs anchored-to + spans (the grid
# edges); contains/hosted-by are honest no-ops here.
#
# Run:  python3 scripts/bake_sdg_edges.py deploy/dev/buildings/SampleHouse_extracted.db

import os
import sys
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "DAGCompiler", "python"))
from extractIFCtoDB import derive_datums_and_anchors, derive_spans

SCHEMA = (
    "CREATE TABLE IF NOT EXISTS datum_plane (datum_id INTEGER PRIMARY KEY,axis TEXT,coord REAL,"
    "support_count INTEGER,provenance TEXT);"
    "CREATE TABLE IF NOT EXISTS rel_anchored (element_guid TEXT,datum_id INTEGER,axis TEXT,offset_mm REAL,"
    "provenance TEXT,PRIMARY KEY(element_guid,datum_id));"
    "CREATE TABLE IF NOT EXISTS rel_spans (element_guid TEXT,axis TEXT,datum_lo_id INTEGER,datum_hi_id INTEGER,"
    "span_m REAL,provenance TEXT,PRIMARY KEY(element_guid,axis));"
    "CREATE TABLE IF NOT EXISTS elements_rtree (id INTEGER PRIMARY KEY,minX REAL,maxX REAL,minY REAL,maxY REAL,"
    "minZ REAL,maxZ REAL);"
)


def bake(db):
    if not os.path.exists(db):
        print(f"  §BAKE-SDG missing {db}"); return 1
    c = sqlite3.connect(db)
    cols = [r[1] for r in c.execute("PRAGMA table_info(element_transforms)")]
    for need in ("center_x", "bbox_x"):
        if need not in cols:
            print(f"  §BAKE-SDG {db}: element_transforms lacks {need} — cannot derive AABBs"); return 1

    c.executescript(SCHEMA)
    # elements_meta needs an `id` for the derive join; add one (stable rowid) if absent.
    meta_cols = [r[1] for r in c.execute("PRAGMA table_info(elements_meta)")]
    if "id" not in meta_cols:
        c.execute("ALTER TABLE elements_meta ADD COLUMN id INTEGER")
    c.execute("UPDATE elements_meta SET id = rowid")

    for t in ("datum_plane", "rel_anchored", "rel_spans", "elements_rtree"):
        c.execute(f"DELETE FROM {t}")

    # AABB = center ± bbox/2, in the SERVED frame (the frame the viewer renders) — never the fixture's.
    n_box = c.execute("""
        INSERT INTO elements_rtree (id, minX, maxX, minY, maxY, minZ, maxZ)
        SELECT m.id,
               t.center_x - t.bbox_x/2.0, t.center_x + t.bbox_x/2.0,
               t.center_y - t.bbox_y/2.0, t.center_y + t.bbox_y/2.0,
               t.center_z - t.bbox_z/2.0, t.center_z + t.bbox_z/2.0
        FROM elements_meta m JOIN element_transforms t ON t.guid = m.guid
    """).rowcount
    c.commit()

    nd, na = derive_datums_and_anchors(c)
    ns = derive_spans(c)
    c.commit()

    # §-proof: datums emerged, anchors within tol by construction, every edge guid is real.
    bad = c.execute("SELECT count(*) FROM rel_anchored WHERE element_guid NOT IN (SELECT guid FROM elements_meta)").fetchone()[0]
    bad += c.execute("SELECT count(*) FROM rel_spans WHERE element_guid NOT IN (SELECT guid FROM elements_meta)").fetchone()[0]
    zmax = c.execute("SELECT max(coord) FROM datum_plane WHERE axis='Z'").fetchone()[0]
    zmin = c.execute("SELECT min(coord) FROM datum_plane WHERE axis='Z'").fetchone()[0]
    c.close()
    print(f"  §BAKE-SDG {os.path.basename(db)}  aabbs={n_box}  datums={nd}  anchors={na}  spans={ns}  "
          f"Z-range=[{zmin:.3f},{zmax:.3f}]  invented_guids={bad}")
    if bad:
        print("  ✗ BAKE-SDG RED — invented edge guid(s)"); return 1
    if nd == 0:
        print("  ✗ BAKE-SDG RED — no datums emerged (AABBs likely degenerate)"); return 1
    print("  ✓ BAKE-SDG GREEN")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("usage: bake_sdg_edges.py <db> [<db> ...]"); sys.exit(2)
    rc = 0
    for db in sys.argv[1:]:
        rc |= bake(db)
    sys.exit(rc)
