#!/usr/bin/env python3
"""
witness_reconcile_terminal_rules.py — W-TRM-RECONCILE

Proves the prior-art reconciliation (TRM001) is faithful + NON-INVENT:
  G1  row parity  — every terminal_rules.db rule -> exactly one reconciled row
  G2  real anchors— every ad_mep_anchor TRM:* XYZ == the real Terminal element
                    (re-resolved from Terminal_meta.db); 0 fabricated coords
  G3  datum loss  — ad_placement_measured.z_offset == the mined dz (lossless
                    adoption of the z_rule/FLOOR vocabulary)
  G4  no-fork     — routing landed in EXISTING ad_mep_pattern, anchors in
                    EXISTING ad_mep_anchor; prior 340 Terminal anchors untouched
  G5  FK valid    — every non-null placement_rule resolves to a real
                    ad_placement_offset row (we reference the dictionary, not fork)
  G6  provenance  — every reconciled row carries a measured:terminal* provenance
                    (or, for anchor/pattern, source_building='Terminal')

Applies TRM001 to a COPY of library/ERP.db (live DB untouched). Reads the .log.
"""
import os
import shutil
import sqlite3
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
RULES_DB = os.path.join(HERE, "terminal_rules.db")
ERP_DB = os.path.join(ROOT, "library", "ERP.db")
MIG = os.path.join(ROOT, "migration", "TRM001_terminal_measured_rules.sql")
META = next((p for p in [
    os.path.expanduser("~/bim-ootb/buildings/Terminal_meta.db"),
    os.path.expanduser("~/bim-ootb/modeller/Terminal_meta.db"),
] if os.path.exists(p)), None)
LOG = os.path.join(HERE, "witness_reconcile_terminal_rules.log")

LINES = []
PASS = [0]
FAIL = [0]


def log(s):
    LINES.append(s)


def check(tag, ok, detail=""):
    mark = "PASS" if ok else "FAIL"
    (PASS if ok else FAIL)[0] += 1
    log("§%s %s %s" % (tag, mark, detail))


def main():
    for p, name in [(RULES_DB, "terminal_rules.db"), (ERP_DB, "ERP.db"),
                    (MIG, "TRM001.sql")]:
        if not os.path.exists(p):
            sys.exit("FATAL: missing %s" % name)
    if not META:
        sys.exit("FATAL: Terminal_meta.db not found")

    tmp = tempfile.mkdtemp()
    erp = os.path.join(tmp, "ERP_witness.db")
    shutil.copy(ERP_DB, erp)

    con = sqlite3.connect(erp)
    # count genuinely-prior (non-TRM) Terminal anchors — robust whether or not
    # TRM001 was already applied to the source DB.
    prior_terminal_anchors = con.execute(
        "SELECT COUNT(*) FROM ad_mep_anchor "
        "WHERE source_building='Terminal' AND anchor_id NOT LIKE 'TRM:%'").fetchone()[0]
    with open(MIG) as fh:
        con.executescript(fh.read())
    con.commit()

    rules = sqlite3.connect(RULES_DB)
    meta = sqlite3.connect(META)

    log("=== W-TRM-RECONCILE — measured Terminal rules -> prior-art ERP.db vocab ===")
    log("erp copy=%s  meta=%s" % (erp, META))
    log("prior Terminal anchors (pre-TRM001) = %d" % prior_terminal_anchors)
    log("")

    # ── G1 row parity ──────────────────────────────────────────────────────
    pairs = [
        ("rule_placement", "ad_placement_measured", None),
        ("rule_routing", "ad_mep_pattern", "source_building='Terminal'"),
        ("rule_space_bom", "ad_space_bom_measured", None),
        ("rule_place_order", "ad_place_order", None),
        ("rule_avoidance", "ad_clash_avoidance", None),
    ]
    for src, dst, where in pairs:
        n_src = rules.execute("SELECT COUNT(*) FROM %s" % src).fetchone()[0]
        w = (" WHERE " + where) if where else ""
        n_dst = con.execute("SELECT COUNT(*) FROM %s%s" % (dst, w)).fetchone()[0]
        check("G1-PARITY", n_src == n_dst,
              "%s(%d) -> %s(%d)" % (src, n_src, dst, n_dst))

    # ── G2 real anchors (0 fabricated XYZ) ─────────────────────────────────
    anchors = con.execute(
        "SELECT ifc_guid, x_m, y_m, z_m FROM ad_mep_anchor "
        "WHERE anchor_id LIKE 'TRM:%'").fetchall()
    bad = 0
    for g, x, y, z in anchors:
        row = meta.execute(
            "SELECT center_x, center_y, center_z FROM element_transforms "
            "WHERE guid=?", (g,)).fetchone()
        if not row or max(abs(row[0] - x), abs(row[1] - y), abs(row[2] - z)) > 1e-6:
            bad += 1
    check("G2-REAL-ANCHORS", bad == 0 and len(anchors) > 0,
          "%d anchors, %d with fabricated/mismatched XYZ" % (len(anchors), bad))

    # ── G3 datum loss (z_offset == mined dz) ───────────────────────────────
    mismatch = 0
    total = 0
    for pr in rules.execute(
            "SELECT disc, ifc_class, storey_scope, dz FROM rule_placement"):
        disc, icls, scope, dz = pr
        r = con.execute(
            "SELECT z_offset, z_rule FROM ad_placement_measured "
            "WHERE disc=? AND ifc_class=? AND IFNULL(storey_scope,'')=IFNULL(?,'')",
            (disc, icls, scope)).fetchone()
        total += 1
        if not r or abs((r[0] or 0) - (dz or 0)) > 1e-9 or r[1] != "FLOOR":
            mismatch += 1
    check("G3-DATUM-LOSSLESS", mismatch == 0,
          "%d/%d placement rows z_offset==dz & z_rule=FLOOR" % (total - mismatch, total))

    # ── G4 no-fork: existing tables used; prior anchors untouched ──────────
    still_prior = con.execute(
        "SELECT COUNT(*) FROM ad_mep_anchor "
        "WHERE source_building='Terminal' AND anchor_id NOT LIKE 'TRM:%'").fetchone()[0]
    check("G4-NOFORK-PRIOR", still_prior == prior_terminal_anchors,
          "prior non-TRM Terminal anchors preserved %d==%d"
          % (still_prior, prior_terminal_anchors))
    pat_in_existing = con.execute(
        "SELECT COUNT(*) FROM ad_mep_pattern WHERE pattern_id LIKE 'TRM_%'").fetchone()[0]
    check("G4-NOFORK-PATTERN", pat_in_existing == 11,
          "routing flowed into existing ad_mep_pattern (%d rows)" % pat_in_existing)

    # ── G5 placement_rule FK resolves to a real ad_placement_offset row ────
    dangling = con.execute(
        "SELECT COUNT(*) FROM ad_placement_measured m "
        "WHERE m.placement_rule IS NOT NULL AND NOT EXISTS "
        "(SELECT 1 FROM ad_placement_offset o WHERE o.placement_rule=m.placement_rule)"
    ).fetchone()[0]
    n_fk = con.execute("SELECT COUNT(*) FROM ad_placement_measured "
                       "WHERE placement_rule IS NOT NULL").fetchone()[0]
    check("G5-FK-VALID", dangling == 0 and n_fk > 0,
          "%d FK'd placement rows, %d dangling" % (n_fk, dangling))

    # ── G6 provenance tags ─────────────────────────────────────────────────
    prov_bad = 0
    for tbl in ("ad_placement_measured", "ad_space_bom_measured",
                "ad_place_order", "ad_clash_avoidance"):
        prov_bad += con.execute(
            "SELECT COUNT(*) FROM %s WHERE provenance NOT LIKE 'measured:terminal%%'"
            % tbl).fetchone()[0]
    # anchors/patterns carry provenance via source_building='Terminal'
    anchor_untagged = con.execute(
        "SELECT COUNT(*) FROM ad_mep_anchor WHERE anchor_id LIKE 'TRM:%' "
        "AND source_building<>'Terminal'").fetchone()[0]
    check("G6-PROVENANCE", prov_bad == 0 and anchor_untagged == 0,
          "%d untagged measured rows, %d untagged anchors" % (prov_bad, anchor_untagged))

    log("")
    log("=== RESULT: %d PASS / %d FAIL ===" % (PASS[0], FAIL[0]))
    with open(LOG, "w") as fh:
        fh.write("\n".join(LINES) + "\n")
    print("\n".join(LINES))
    print("\nlog -> %s" % LOG)
    con.close()
    rules.close()
    meta.close()
    shutil.rmtree(tmp, ignore_errors=True)
    sys.exit(1 if FAIL[0] else 0)


if __name__ == "__main__":
    main()
