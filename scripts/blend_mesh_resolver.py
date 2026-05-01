# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
BlendMeshResolver — Build-Time Mesh Canonicalisation
=====================================================
Stateless function: apply geometry_hash_redirect rules from component_library.db
to element_instances in any target DB connection.

Called by:
  - extractIFCtoDB_open.py  (after extraction, before close)
  - build_sandbox_1M.py     (after merge, before close)

Spec: internal/BlendMeshResolver.md
"""

import sqlite3
import time
from pathlib import Path

REPO_ROOT   = Path(__file__).resolve().parent.parent
LIBRARY_DB  = REPO_ROOT / "library" / "component_library.db"


def apply_mesh_redirects(target_conn: sqlite3.Connection,
                         library_db_path: Path | str = LIBRARY_DB,
                         caller: str = "unknown",
                         db_name: str = "unknown") -> dict:
    """
    Apply geometry_hash_redirect rules to element_instances in target_conn.

    Args:
        target_conn    : open sqlite3 connection to the DB being resolved
        library_db_path: path to component_library.db (rule source)
        caller         : name of calling script (for §PROOF lines)
        db_name        : filename of the target DB (for §PROOF lines)

    Returns:
        dict with keys: rules_loaded, rules_applied, rows_updated, elapsed_s
    """
    t0 = time.perf_counter()
    library_db_path = Path(library_db_path)

    # ── load rules ──
    if not library_db_path.exists():
        print(f"§RESOLVER WARN  library_db not found: {library_db_path}  caller={caller}")
        return {"rules_loaded": 0, "rules_applied": 0, "rows_updated": 0, "elapsed_s": 0}

    lib = sqlite3.connect(str(library_db_path))
    try:
        tables = {r[0] for r in lib.execute(
            "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
        if "geometry_hash_redirect" not in tables:
            print(f"§RESOLVER SKIP  no geometry_hash_redirect table in library  caller={caller}")
            lib.close()
            return {"rules_loaded": 0, "rules_applied": 0, "rows_updated": 0, "elapsed_s": 0}

        rules = lib.execute(
            "SELECT deprecated_hash, canonical_hash, sub_type "
            "FROM geometry_hash_redirect ORDER BY sub_type, deprecated_hash"
        ).fetchall()
    finally:
        lib.close()

    print(f"§RESOLVER START  caller={caller}  db={db_name}  rules={len(rules)}")

    if not rules:
        print(f"§RESOLVER SKIP  geometry_hash_redirect is empty  caller={caller}")
        return {"rules_loaded": 0, "rules_applied": 0, "rows_updated": 0, "elapsed_s": 0}

    # ── apply rules ──
    rules_applied = 0
    rows_updated  = 0

    for deprecated, canonical, sub_type in rules:
        cur = target_conn.execute(
            "UPDATE element_instances SET geometry_hash = ? WHERE geometry_hash = ?",
            (canonical, deprecated))
        if cur.rowcount > 0:
            print(f"§RESOLVER RULE  deprecated={deprecated[:16]}  "
                  f"canonical={canonical[:16]}  sub_type={sub_type or '?'}  "
                  f"rows={cur.rowcount}")
            rules_applied += 1
            rows_updated  += cur.rowcount
        else:
            print(f"§RESOLVER SKIP  rule={deprecated[:16]}  no matching rows in {db_name}")

    target_conn.commit()

    elapsed = time.perf_counter() - t0
    print(f"§RESOLVER DONE  db={db_name}  rules_applied={rules_applied}  "
          f"rows_updated={rows_updated}  elapsed={elapsed:.3f}s")

    return {
        "rules_loaded":  len(rules),
        "rules_applied": rules_applied,
        "rows_updated":  rows_updated,
        "elapsed_s":     elapsed,
    }


# ── standalone: resolve all extracted DBs in INPUT_DIR ────────────────────────

if __name__ == "__main__":
    import argparse

    INPUT_DIR = REPO_ROOT / "DAGCompiler" / "lib" / "input"

    parser = argparse.ArgumentParser(description="Apply BlendMeshResolver to extracted DBs.")
    parser.add_argument("--db", help="Single DB to resolve (default: all *_extracted.db)")
    parser.add_argument("--library", default=str(LIBRARY_DB), help="Path to component_library.db")
    args = parser.parse_args()

    if args.db:
        targets = [Path(args.db)]
    else:
        targets = sorted(INPUT_DIR.glob("*_extracted.db"))

    total_rows = 0
    for db_path in targets:
        conn = sqlite3.connect(str(db_path))
        # check element_instances exists
        tables = {r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'").fetchall()}
        if "element_instances" not in tables:
            print(f"§RESOLVER SKIP  no element_instances in {db_path.name}")
            conn.close()
            continue
        result = apply_mesh_redirects(
            conn,
            library_db_path=args.library,
            caller="blend_mesh_resolver.__main__",
            db_name=db_path.name)
        total_rows += result["rows_updated"]
        conn.close()

    print(f"\n§RESOLVER SUMMARY  dbs={len(targets)}  total_rows_updated={total_rows}")
