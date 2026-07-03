#!/usr/bin/env python3
"""
stamp_src_bbox.py <rules.db> <meta.db> — stamp the MEASURED median bbox extents
(dx,dy,dz) per ifc_class onto rule_placement, so the modeller can render each
GENERATED disc-walk fixture as a BOX of its class's real source footprint/height
instead of a uniform 0.18³ marker cube. Implements §PRIM / W-DW-PRIM
(prompts/RESUME_DISC_WALKER_ENVELOPE_BOUND.md).

Mirrors stamp_terminal_src_area.py: col-guard + idempotent + NON-INVENT.

NON-INVENT boundary (load-bearing):
  - POSITION is untouched — this stamps SIZE only. The cube→box change moves/adds
    NO fixture; the disc-walker's count + x/y/z are unchanged (W-DW-PRIM P4).
  - SIZE = MEDIAN bbox extents of the REAL source elements of that ifc_class in the
    meta DB (elements_meta ⋈ element_transforms), measured the SAME way the JS engine
    medians a class (_med: filter-null, sort, take a[floor(len/2)]). Never a constant.
  - SHAPE is deliberately a BOX, not a class-specific catalog mesh — we have NO landed
    geometry for an absent discipline; only the box's DIMENSIONS carry real information.
  - A rule class with no source element of that class in the meta DB → NULL bbox → the
    engine keeps the 0.18 cube fallback + logs honestly (no fabricated size).

Idempotent: ADD COLUMN guarded; re-running re-measures and overwrites.

Run:  python3 build/stamp_src_bbox.py build/terminal_rules.db ~/bim-ootb/modeller/Terminal_meta.db
      python3 build/stamp_src_bbox.py build/duplex_rules.db   build/Duplex_mep_meta.db
"""
import os
import sys
import sqlite3


def log(m):
    print(m, flush=True)


def med(vals):
    """Match disc_walker.js _med: filter null, sort, take a[floor(len/2)]."""
    a = sorted(v for v in vals if v is not None)
    return a[len(a) // 2] if a else None


def class_bbox(meta, cls):
    """Median (dx,dy,dz) over REAL source elements of ifc_class `cls`; None if absent."""
    rows = meta.execute(
        "SELECT t.bbox_x, t.bbox_y, t.bbox_z FROM elements_meta e "
        "JOIN element_transforms t ON e.guid=t.guid WHERE e.ifc_class=?", (cls,)
    ).fetchall()
    if not rows:
        return None, None, None, 0
    dx = med([r[0] for r in rows]); dy = med([r[1] for r in rows]); dz = med([r[2] for r in rows])
    return dx, dy, dz, len(rows)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: stamp_src_bbox.py <rules.db> <meta.db>")
    rules_db = os.path.abspath(os.path.expanduser(sys.argv[1]))
    meta_db = os.path.abspath(os.path.expanduser(sys.argv[2]))
    for p in (rules_db, meta_db):
        if not os.path.exists(p):
            raise SystemExit("FATAL: not found: " + p)
    log("§STAMP-BBOX rules=%s meta=%s" % (os.path.basename(rules_db), os.path.basename(meta_db)))

    meta = sqlite3.connect(meta_db)
    db = sqlite3.connect(rules_db)
    cols = [r[1] for r in db.execute("PRAGMA table_info(rule_placement)")]
    for c in ("bbox_dx", "bbox_dy", "bbox_dz"):
        if c not in cols:
            db.execute("ALTER TABLE rule_placement ADD COLUMN %s REAL" % c)
            log("§STAMP-BBOX added column %s" % c)
        else:
            log("§STAMP-BBOX column %s present — re-measuring" % c)

    classes = [r[0] for r in db.execute("SELECT DISTINCT ifc_class FROM rule_placement")]
    stamped = absent = 0
    for cls in classes:
        dx, dy, dz, n = class_bbox(meta, cls)
        db.execute("UPDATE rule_placement SET bbox_dx=?, bbox_dy=?, bbox_dz=? WHERE ifc_class=?",
                   (dx, dy, dz, cls))
        if n == 0:
            absent += 1
            log("  §STAMP-BBOX %-28s NO source element → NULL (engine 0.18 cube fallback)" % cls)
        else:
            stamped += 1
            log("  §STAMP-BBOX %-28s (%.3f,%.3f,%.3f) n=%d" % (cls, dx, dy, dz, n))
    db.commit()
    db.close()
    meta.close()
    log("§STAMP-BBOX done: %d classes stamped, %d absent (NULL fallback)" % (stamped, absent))


if __name__ == "__main__":
    main()
