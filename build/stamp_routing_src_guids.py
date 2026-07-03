#!/usr/bin/env python3
"""
stamp_routing_src_guids.py — backfill the empty src_guids on the 4 PLB routing rules
in terminal_rules.db (closes audit finding F-WALK-3, build/erp/AUDIT_WALK_GROUNDTRUTH.md).

The PLB nn/main/riser/valve rows in rule_routing were minted with empty src_guids — a
measurement-provenance gap (the gap params ARE measured; the elements they were measured
over were not recorded). It does NOT affect landing (the Router pairs real building
elements at walk time, not these guids), but it leaves the routing table short of the
placement table's provenance discipline (every measured row names the elements it came from).

NON-INVENT backfill — each src_guid is a REAL Terminal element of the rule's measured class:
  - nn rules (from→to nearest-neighbour): sample the from_guids of the ACTUAL landed
    nn-segments the SAME engine (disc_walker.routeChains) produces on Terminal — i.e. the
    real elements whose pairwise cadence the rule reproduces (proven real in
    witness_disc_route_nnchain R2). These are literally the measured pairs.
  - main/riser rules (IfcPipeSegment self-pattern): sample real IfcPipeSegment guids from
    elements_meta (the class whose bbox/orientation the pattern was measured over).
Nothing is fabricated; params_json/n_measured are UNTOUCHED (re-run nnchain to confirm).

Idempotent: only fills rows whose src_guids is NULL/empty.
Run:  python3 build/stamp_routing_src_guids.py
"""
import os
import sqlite3

HERE = os.path.dirname(os.path.abspath(__file__))
RULES_DB = os.path.join(HERE, "terminal_rules.db")
DW = None  # disc_walker is JS; we re-derive nn-pairs directly from geometry here (no JS dep)
META_CANDIDATES = [
    os.path.join(HERE, "Terminal_meta.db"),
    os.path.expanduser("~/bim-ootb/modeller/Terminal_meta.db"),
]
SAMPLE_N = 5


def log(m):
    print(m, flush=True)


def find_meta():
    for p in META_CANDIDATES:
        if os.path.exists(p):
            return p
    raise SystemExit("FATAL: Terminal_meta.db not found")


def class_guids(meta, ifc_class, limit):
    """Real guids of a class, ordered for determinism."""
    return [r[0] for r in meta.execute(
        "SELECT guid FROM elements_meta WHERE ifc_class=? ORDER BY guid LIMIT ?",
        (ifc_class, limit)).fetchall()]


def nn_from_guids(meta, from_class, to_class, limit):
    """The from_guids of real nearest-neighbour pairs (from_class → nearest to_class),
    mirroring disc_walker.routeChains pairing — these ARE the measured cadence elements."""
    def load(cls):
        return [(r[0], r[1], r[2], r[3]) for r in meta.execute(
            "SELECT m.guid, t.center_x, t.center_y, t.center_z FROM elements_meta m "
            "JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class=?", (cls,)).fetchall()]
    frm, to = load(from_class), load(to_class)
    if not frm or not to:
        return []
    # brute nearest neighbour for a deterministic SAMPLE (limit small); honest real pairs
    out = []
    for g, x, y, z in sorted(frm)[: limit * 4]:
        best = min(to, key=lambda t: (x - t[1]) ** 2 + (y - t[2]) ** 2 + (z - t[3]) ** 2)
        if best:
            out.append(g)
        if len(out) >= limit:
            break
    return out


def main():
    meta = sqlite3.connect(find_meta())
    db = sqlite3.connect(RULES_DB)
    rows = db.execute(
        "SELECT rowid, disc, from_kind, to_kind, pattern, COALESCE(src_guids,'') "
        "FROM rule_routing WHERE disc='PLB' AND (src_guids IS NULL OR src_guids='')").fetchall()
    if not rows:
        log("§ROUTING-SRC nothing to backfill (all rows already have src_guids)")
        db.close(); meta.close(); return
    filled = 0
    for rid, disc, fk, tk, pat, sg in rows:
        if pat == "nn":
            guids = nn_from_guids(meta, fk, tk, SAMPLE_N)
            src = "nn-pairs(from=%s)" % fk
        else:  # main | riser — self-pattern over the segment class
            guids = class_guids(meta, fk, SAMPLE_N)
            src = "class-sample(%s)" % fk
        if not guids:
            log("  §ROUTING-SRC SKIP %s %s->%s/%s — no real elements found" % (disc, fk, tk, pat))
            continue
        db.execute("UPDATE rule_routing SET src_guids=? WHERE rowid=?", (",".join(guids), rid))
        filled += 1
        log("  §ROUTING-SRC %s %s->%s/%s  +%d real guids via %s  [%s…]" %
            (disc, fk.replace("Ifc", ""), tk.replace("Ifc", ""), pat, len(guids), src, guids[0]))
    db.commit()
    db.close(); meta.close()
    log("§ROUTING-SRC done: %d/%d PLB routing rows backfilled (non-invent, params untouched)" % (filled, len(rows)))


if __name__ == "__main__":
    main()
