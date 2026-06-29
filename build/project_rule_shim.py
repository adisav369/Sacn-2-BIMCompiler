#!/usr/bin/env python3
"""
project_rule_shim.py — project the host-bind percepts from disc_patterns.db `_shim_attributes`
into a per-building-class `*_rules.db` as a first-class `rule_shim` table (RESUME_DISC_WALKER_
ENVELOPE_BOUND.md §NAMING DIRECTIVE §SHIM). This makes the SHIM flow like routing/placement: the
walker reads its anchor/mount/offset FROM THE PROJECTION (the lean rules DB), not from a caller.

NON-INVENT: every row is copied verbatim from `_shim_attributes` (the prior-art pattern store);
the only derived columns are unit conversions (mm→m), a deterministic `priority` (SIDE/wall
anti-float first), and `same_storey` (1 only for vertically-STACKED hosts — windows stack
floor-on-floor so nearest-XY alone is ambiguous; walls/slabs/coverings do not). No values invented.

IDEMPOTENT + ISOLATED: DROP+CREATE+INSERT `rule_shim` ONLY — the 5 mined rule tables are untouched
(re-run safe, no drift). Source = library/disc_patterns.db (physically library/ERP.db until the
rename slice; both names resolve via symlink). Usage: project_rule_shim.py <rules_db> <building_class>
"""
import os
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
# disc_patterns.db (the pattern store) — symlink to ERP.db today; new code names disc_patterns.
DISC_PATTERNS = os.path.join(HERE, "..", "library", "disc_patterns.db")

# hosts that STACK vertically (same plan XY across storeys) → require same-storey host selection.
# A small, structural fact about the host class, not a tuned constant.
STACKING_HOSTS = {"IfcWindow", "IfcDoor"}


def project_shims(rules_db, building_class, patterns_db=DISC_PATTERNS, log=print):
    if not os.path.exists(patterns_db):
        log(f"§SHIM-PROJ SKIP — {patterns_db} missing (pattern store absent)")
        return 0
    src = sqlite3.connect(patterns_db)
    rows = src.execute(
        "SELECT product_value, host_ifc_class, mount, offset_mm, height_mm FROM _shim_attributes"
    ).fetchall()
    src.close()

    con = sqlite3.connect(rules_db)
    cur = con.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS rule_shim;
        CREATE TABLE rule_shim(
            disc TEXT, fixture_ifc_class TEXT, host_ifc_class TEXT, mount TEXT,
            offset_m REAL, height_m REAL, same_storey INTEGER, priority INTEGER,
            building_class TEXT, provenance TEXT, product_value TEXT);
        """
    )
    n = 0
    for product_value, host, mount, off_mm, ht_mm in rows:
        disc = str(product_value).split("_")[0]            # ELEC_WALL_SHIM → ELEC (selection key, primary)
        offset_m = (off_mm or 0) / 1000.0
        height_m = (ht_mm / 1000.0) if ht_mm is not None else None
        same_storey = 1 if host in STACKING_HOSTS else 0
        priority = 0 if str(mount).upper() == "SIDE" else 1  # wall-SIDE anti-float wins when a disc has >1 shim
        cur.execute(
            "INSERT INTO rule_shim VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            (disc, None, host, mount, offset_m, height_m, same_storey, priority,
             building_class, "projected:disc_patterns._shim_attributes", product_value),
        )
        n += 1
    con.commit()
    con.close()
    log(f"§SHIM-PROJ {os.path.basename(rules_db)} ← disc_patterns._shim_attributes: {n} rule_shim rows "
        f"(building_class={building_class}, fixture_ifc_class=NULL — disc-level; ifc_class refinement is the open selection-key)")
    return n


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("usage: project_rule_shim.py <rules_db> <building_class>", file=sys.stderr)
        sys.exit(2)
    project_shims(sys.argv[1], sys.argv[2])
