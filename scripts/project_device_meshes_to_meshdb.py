#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — SCOPE: item 5 of prompts/Modeller/DISC_Walker/RESUME_DISC_WALKER_ENVELOPE_BOUND.md
# (§LIVEWIRE spec). Projects the rule-carried device/binding mesh payloads into a shipped modeller
# mesh.db so schedule-generated fixtures render REAL meshes (the LOD400 render seam). Read the log
# after every run — exit code alone is not evidence.
"""
project_device_meshes_to_meshdb.py — EXTRACT-ONLY projection of the 26 rule-carried geometry
hashes into a target mesh.db (component_geometries: geometry_hash PK, vertices, faces, building).

WHY (2026-07-10, §LIVEWIRE): the embed-8 consolidation RE-KEYED every geometry hash (verified:
guid ...29tlUnxYrDDfZmPyOHoW2$ = 7759c52513f88549 extracted vs 169dc2e47423f3d9 shipped), so the
hashes mined into duplex_rules.rule_space_schedule and terminal_rules.rule_mesh_binding resolve
0/26 in the shipped mesh.db. This script copies the REAL payloads byte-verbatim from their
measured sources — no invention, no re-hashing, no fallback shape:
  - rule_space_schedule hashes ← a REPAIRED component_library.db's component_geometries
    (JavaEra_FOSSIL_README.md recipe; the shared-tree complib lacks SPRINKLER+SUPPLY_DIFFUSER)
  - rule_mesh_binding hashes  ← deploy/buildings/Terminal_library.db (pre-consolidation store,
    byte-exact key match verified before this script was written)
A rule hash MISSING from its source is a hard error (never skipped silently). REFUSE-list
devices (geometry_hash NULL in the rules) are by-design absent and not touched.

Usage:
  project_device_meshes_to_meshdb.py --duplex-rules build/duplex_rules.db \
      --terminal-rules build/terminal_rules.db \
      --complib /tmp/wt-fable-bimeyes/library/component_library.db \
      --terminal-lib deploy/buildings/Terminal_library.db \
      --target /tmp/wt-fable-livewire/modeller/mesh.db
Idempotent: INSERT OR REPLACE keyed on geometry_hash; re-runs converge.
"""
import argparse
import sqlite3
import sys


def log(msg):
    print(f"§MESHPROJ {msg}", flush=True)


def rule_hashes(rules_db, sql, label):
    con = sqlite3.connect(f"file:{rules_db}?mode=ro", uri=True)
    rows = con.execute(sql).fetchall()
    con.close()
    log(f"{label}: {len(rows)} distinct rule-carried hashes")
    return rows


def project(src_db, hashes, target_con, building_tag, label):
    src = sqlite3.connect(f"file:{src_db}?mode=ro", uri=True)
    n = 0
    for h, lbl in hashes:
        row = src.execute(
            "SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?", (h,)
        ).fetchone()
        if row is None or row[0] is None or len(row[0]) == 0:
            log(f"FATAL {label} {h} ({lbl}) NOT FOUND in {src_db} — refusing to continue")
            sys.exit(1)
        target_con.execute(
            "INSERT OR REPLACE INTO component_geometries (geometry_hash, vertices, faces, building)"
            " VALUES (?,?,?,?)", (h, row[0], row[1], building_tag))
        log(f"{label} {h} ({lbl}) verts={len(row[0])}B faces={len(row[1])}B → {building_tag}")
        n += 1
    src.close()
    return n


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--duplex-rules", required=True)
    ap.add_argument("--terminal-rules", required=True)
    ap.add_argument("--complib", required=True)
    ap.add_argument("--terminal-lib", required=True)
    ap.add_argument("--target", required=True)
    a = ap.parse_args()

    dx = rule_hashes(a.duplex_rules,
        "SELECT geometry_hash, GROUP_CONCAT(DISTINCT device_id) FROM rule_space_schedule "
        "WHERE geometry_hash IS NOT NULL AND geometry_hash!='' GROUP BY geometry_hash",
        "rule_space_schedule")
    te = rule_hashes(a.terminal_rules,
        "SELECT geometry_hash, disc||'/'||ifc_class FROM rule_mesh_binding",
        "rule_mesh_binding")

    tgt = sqlite3.connect(a.target)
    n1 = project(a.complib, dx, tgt, "DeviceLibrary_residential", "DX")
    n2 = project(a.terminal_lib, te, tgt, "TerminalMerged_rules", "TE")
    tgt.commit()

    # verify from the TARGET itself (read-back, not trust)
    bad = [h for h, _ in dx + te
           if not tgt.execute("SELECT length(vertices)>0 FROM component_geometries "
                              "WHERE geometry_hash=?", (h,)).fetchone()]
    tgt.close()
    if bad:
        log(f"FATAL read-back failed for {bad}")
        sys.exit(1)
    log(f"DONE projected DX={n1} TE={n2} total={n1 + n2}; read-back {n1 + n2}/{n1 + n2} >0-vertex OK")


if __name__ == "__main__":
    main()
