#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — SCOPE: Watchdog fresh-worktree verification round 2 (2026-07-10,
# RESUME_DISC_WALKER_ENVELOPE_BOUND.md §LIVEWIRE correction thread). On a PRISTINE LFS checkout of
# library/component_library.db there is no M_Product_Image table (fossil recipe step 2's pre_s173
# restore was never committed anywhere git-reachable — even JavaEra_FOSSIL_README.md is gitignored),
# so restore_generative_meshes.py CRASHED at its M_Product_Image cleanup and python-sqlite3 rolled
# the whole restore transaction back — leaving SPRINKLER/SUPPLY_DIFFUSER dangling (W-SCHED-MINE 6/7,
# W-DX-WALKBACK-RSGT 13/14 on a fresh worktree). Read the log after every run.
"""
seed_dangling_meshes.py — committed, idempotent generator (sibling of seed_shim_attributes.py):
  1. CREATE M_Product_Image IF ABSENT (schema verbatim from the live complib — an EMPTY percept
     table; CL_001's INSERT-OR-IGNORE re-seeds rows from restored meshes, same as after the
     restore script's dangling-row cleanup).
  2. Restore the two dangling LOD400 device meshes BYTE-VERBATIM from their real measured source
     (deploy/buildings/HHS_Office_Federated_extracted.db — a gitignored building input, per the
     documented copy-in recipe), exactly the same element_name query + I_Geometry_Map type row
     restore_generative_meshes.py uses. Nothing fabricated.

Usage: seed_dangling_meshes.py [complib_path]   (default library/component_library.db)
Run BEFORE (or after — both idempotent) restore_generative_meshes.py on a fresh checkout.
"""
import sys
# --- utf8-console guard (2026-09-05) ---------------------------------------------
# This script prints non-ASCII (box-drawing, arrows, section marks). On a console whose
# encoding is not UTF-8 -- Windows cp1252 is the common case -- print() raises
# UnicodeEncodeError and kills the script mid-run. That is not hypothetical: it aborted
# scripts/restore_generative_meshes.py immediately after it created its back-compat view
# but BEFORE it restored any mesh, which is why the component_library repair silently
# needed two passes to converge. errors="replace" is deliberate: a mangled glyph in a log
# line is always better than a dead pipeline stage.
for _s in (sys.stdout, sys.stderr):
    try:
        _s.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError, OSError):
        pass  # already-wrapped, detached, or replaced by a non-TextIOWrapper (e.g. in tests)
# ---------------------------------------------------------------------------------
import os
import sqlite3
import sys

LIB = sys.argv[1] if len(sys.argv) > 1 else "library/component_library.db"
SRC = "deploy/buildings/HHS_Office_Federated_extracted.db"

# device -> (element_ref == source element_name prefix, ifc_class); source rows verified real
# (RESUME_DISC_WALKER_ENVELOPE_BOUND.md: SPRINKLER ca5aa235c4360dde 240v/140f, SUPPLY_DIFFUSER
# 816d4dbdc5aec706 168v/104f — the exact hashes rule_space_schedule carries).
DEVICES = {
    "SPRINKLER": ("M_Sprinkler - Pendent - Hosted:15 mm Pendent on Drop with Guard", "IfcFlowTerminal"),
    "SUPPLY_DIFFUSER": ("M_Supply Diffuser - Rectangular Face Round Neck:600x600 - 250 Neck", "IfcFlowTerminal"),
}


def log(msg):
    print(f"§SEED-MESH {msg}", flush=True)


def main():
    if not os.path.exists(LIB):
        log(f"FATAL {LIB} missing"); sys.exit(1)
    con = sqlite3.connect(LIB); cur = con.cursor()

    if not cur.execute("SELECT 1 FROM sqlite_master WHERE name='M_Product_Image'").fetchone():
        cur.execute("""CREATE TABLE M_Product_Image (
            M_Product_ID  TEXT PRIMARY KEY,
            geometry_hash TEXT NOT NULL,
            up_axis       TEXT NOT NULL DEFAULT 'Z',
            forward_axis  TEXT NOT NULL DEFAULT 'Y',
            attachment_face TEXT NOT NULL DEFAULT 'CENTER')""")
        log("created M_Product_Image (was absent — pristine pre-rename complib)")

    if not os.path.exists(SRC):
        log(f"FATAL source {SRC} missing — copy in the gitignored building DB first (documented recipe)")
        sys.exit(1)
    scon = sqlite3.connect(f"file:{SRC}?mode=ro", uri=True)
    n = 0
    seeded = {}
    for dev, (name, ifc_class) in DEVICES.items():
        row = scon.execute(
            "SELECT ei.geometry_hash, cg.vertices, cg.faces "
            "FROM elements_meta em JOIN element_instances ei ON ei.guid=em.guid "
            "JOIN component_geometries cg ON cg.geometry_hash=ei.geometry_hash "
            "WHERE (em.element_name=? OR em.element_name LIKE ?) "
            "AND cg.vertices IS NOT NULL AND cg.faces IS NOT NULL LIMIT 1",
            (name, name + ":%")).fetchone()
        if not row:
            log(f"FATAL no real mesh for {dev} ({name}) in {SRC}"); sys.exit(1)
        h, vblob, fblob = row
        seeded[dev] = h
        if not cur.execute("SELECT 1 FROM component_geometries WHERE geometry_hash=?", (h,)).fetchone():
            cur.execute(
                "INSERT INTO component_geometries (geometry_hash, vertices, faces, normals, vertex_count, face_count) "
                "VALUES (?, ?, ?, NULL, ?, ?)",
                (h, vblob, fblob, len(vblob) // 12, len(fblob) // 12))
            n += 1
            log(f"restored {dev} hash={h} v={len(vblob) // 12} f={len(fblob) // 12} (byte-verbatim from {os.path.basename(SRC)})")
        else:
            log(f"already present: {dev} hash={h}")
        if cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name='I_Geometry_Map'").fetchone() \
                and not cur.execute("SELECT 1 FROM I_Geometry_Map WHERE element_ref=? AND ordinal IS NULL",
                                    (name,)).fetchone():
            cur.execute(
                "INSERT INTO I_Geometry_Map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source, provenance) "
                "VALUES (NULL, ?, ?, NULL, NULL, ?, ?, 'LIBRARY')",
                (name, ifc_class, h, f"restored_from:{os.path.basename(SRC)}"))
    scon.close()
    con.commit()

    # read-back from the target itself — by the seeded hashes (component_geometries is what this
    # seeder owns; the I_Geometry_Map type rows are conditional on the rename having happened)
    bad = [d for d, h in seeded.items() if not con.execute(
        "SELECT 1 FROM component_geometries WHERE geometry_hash=? AND length(vertices)>0", (h,)).fetchone()]
    con.close()
    if bad:
        log(f"FATAL read-back failed for {bad}"); sys.exit(1)
    log(f"DONE inserted={n}; read-back 2/2 device meshes resolve >0-vertex "
        f"({', '.join(d + '=' + h for d, h in sorted(seeded.items()))})")


if __name__ == "__main__":
    main()
