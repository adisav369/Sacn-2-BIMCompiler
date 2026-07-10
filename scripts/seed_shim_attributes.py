#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — SCOPE: Watchdog correction 2026-07-10 (RESUME_DISC_WALKER_ENVELOPE_BOUND.md
# §LIVEWIRE closeout). `_shim_attributes` is MEASURED mined data (host-bind percepts) that lived ONLY
# inside gitignored disc_patterns.db — every `rebuild_erp.sh` regeneration silently DROPPED it, so the
# hostbind witnesses (witness_dwwalk_hostbind / witness_hostbind_agnostic / witness_elec_hostbind) and
# build/project_rule_shim.py crashed on a fresh checkout. Read the log after every run.
"""
seed_shim_attributes.py — re-seed the measured `_shim_attributes` percept table into
library/disc_patterns.db. Called by rebuild_erp.sh after regeneration; idempotent standalone too.

NON-INVENT: the 12 rows below are copied VERBATIM from the mined artifact (shim-mining session,
RESUME_TERMINAL_RULE_MINING.md; ACMV_WINDOW_SHIM = the relabelled VENT_WINDOW_SHIM with the
H7-corrected measured offset −429 mm — see RESUME_DISC_WALKER_ENVELOPE_BOUND.md item 1, 2026-07-10).
This file is that data's committed home so a regenerated disc_patterns.db stays complete.
"""
import sqlite3
import sys

DB = sys.argv[1] if len(sys.argv) > 1 else "library/disc_patterns.db"

# (product_value, host_ifc_class, mount, offset_mm, height_mm) — measured, verbatim.
ROWS = [
    ("FP_CEILING_SHIM",   "IfcCovering", "BOTTOM",    5.0, None),
    ("FP_WALL_SHIM",      "IfcWall",     "SIDE",      0.0, 1200.0),
    ("ELEC_CEILING_SHIM", "IfcCovering", "BOTTOM",    5.0, None),
    ("ELEC_WALL_SHIM",    "IfcWall",     "SIDE",      0.0, 1200.0),
    ("CW_CEILING_SHIM",   "IfcCovering", "BOTTOM",    5.0, None),
    ("CW_WALL_SHIM",      "IfcWall",     "SIDE",      0.0, 1000.0),
    ("SP_FLOOR_SHIM",     "IfcSlab",     "TOP",       0.0, None),
    ("SP_WALL_SHIM",      "IfcWall",     "SIDE",      0.0, 600.0),
    ("ACMV_CEILING_SHIM", "IfcCovering", "BOTTOM",    5.0, None),
    ("LPG_WALL_SHIM",     "IfcWall",     "SIDE",      0.0, 500.0),
    ("LPG_FLOOR_SHIM",    "IfcSlab",     "TOP",       0.0, None),
    ("ACMV_WINDOW_SHIM",  "IfcWindow",   "TOP",    -429.0, None),
]


def main():
    con = sqlite3.connect(DB)
    con.execute("""CREATE TABLE IF NOT EXISTS _shim_attributes(
        product_value TEXT, host_ifc_class TEXT, mount TEXT, offset_mm REAL, height_mm REAL)""")
    n = 0
    for r in ROWS:
        if con.execute("SELECT 1 FROM _shim_attributes WHERE product_value=?", (r[0],)).fetchone():
            continue
        con.execute("INSERT INTO _shim_attributes VALUES (?,?,?,?,?)", r)
        n += 1
    con.commit()
    total = con.execute("SELECT COUNT(*) FROM _shim_attributes").fetchone()[0]
    con.close()
    print(f"§SHIM-SEED {DB}: inserted {n}, table now {total} rows (12 expected)")
    if total < 12:
        sys.exit(1)


if __name__ == "__main__":
    main()
