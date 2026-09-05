#!/usr/bin/env python3
"""
# ⚠ DO NOT REMOVE
SCOPE: prompts/SCALE_AND_UX_SWEEP.md §4 Q1 — PARAMETRIC_DEPTH_RECON_FINDINGS.md's own Q1 finding: the raw
per-instance component_definitions.local_min/max_{x,y,z} rows already exist (23,888 rows; e.g. 129 IfcDoor
rows spanning local width 0.147-1.86m) but NO aggregation by type_id was ever persisted — so "what's the real
mined-variance range for a door/window/etc." required an ad-hoc query every time, and nothing built on top of
it (the LOD touch-up axis in PREFAB_LASSO_MACRO_LIBRARY_DIALOGUE.md §1 was explicitly BLOCKED on this).

This is a SMALL, deterministic aggregation pass — GROUP BY type_id over the REAL rows, nothing invented, nothing
sampled. Idempotent (DROP + CREATE + re-INSERT every run) — same lifecycle as the DV_<prefix>_rules.sql mined
artifacts (CLAUDE.md Sacred Files: in-place regeneration is normal, not a migration to append to).

Read the log after every run (CLAUDE.md Log Mandate) — the §Q1_AGG lines below are read back from the table
itself post-write, not just "insert succeeded" — proof the persisted aggregate matches the raw source rows.
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
import sqlite3
import sys
import pathlib

DB = pathlib.Path(__file__).resolve().parent.parent / 'library' / 'component_library.db'


def main():
    con = sqlite3.connect(str(DB))
    cur = con.cursor()

    cur.execute('DROP TABLE IF EXISTS component_dimension_range')
    cur.execute('''
        CREATE TABLE component_dimension_range (
            type_id INTEGER PRIMARY KEY REFERENCES component_types(id),
            ifc_class TEXT NOT NULL,
            category TEXT NOT NULL,
            discipline TEXT NOT NULL,
            instance_count INTEGER NOT NULL,
            min_width REAL, max_width REAL,   -- local X extent (local_max_x - local_min_x)
            min_depth REAL, max_depth REAL,   -- local Y extent
            min_height REAL, max_height REAL  -- local Z extent
        )
    ''')

    # GROUP BY type_id over the REAL per-instance rows — no invention, no sampling; every instance counted.
    cur.execute('''
        INSERT INTO component_dimension_range
        SELECT
            cd.type_id,
            ct.ifc_class,
            ct.category,
            ct.discipline,
            COUNT(*),
            MIN(cd.local_max_x - cd.local_min_x), MAX(cd.local_max_x - cd.local_min_x),
            MIN(cd.local_max_y - cd.local_min_y), MAX(cd.local_max_y - cd.local_min_y),
            MIN(cd.local_max_z - cd.local_min_z), MAX(cd.local_max_z - cd.local_min_z)
        FROM component_definitions cd
        JOIN component_types ct ON cd.type_id = ct.id
        WHERE cd.local_min_x IS NOT NULL AND cd.local_max_x IS NOT NULL
        GROUP BY cd.type_id, ct.ifc_class, ct.category, ct.discipline
    ''')
    con.commit()

    total_rows = cur.execute('SELECT COUNT(*) FROM component_definitions').fetchone()[0]
    agg_rows = cur.execute('SELECT COUNT(*) FROM component_dimension_range').fetchone()[0]
    agg_instances = cur.execute('SELECT SUM(instance_count) FROM component_dimension_range').fetchone()[0]
    print('§Q1_AGG table=component_dimension_range classes=%d instancesCovered=%d rawRows=%d' % (agg_rows, agg_instances, total_rows))

    # Read back the door row as the concrete, cited-in-spec proof (129 rows, 0.147-1.86m).
    door = cur.execute("SELECT instance_count, min_width, max_width FROM component_dimension_range WHERE ifc_class='IfcDoor'").fetchone()
    if door:
        print('§Q1_AGG type=IfcDoor count=%d minWidth=%.6f maxWidth=%.6f' % door)
    else:
        print('§Q1_AGG type=IfcDoor NOT FOUND (unexpected — spec cites 129 rows)')
        sys.exit(1)

    # Sanity: door count must match the raw ad-hoc query the recon doc cited (129), not an approximation.
    raw_door_count = cur.execute('''
        SELECT COUNT(*) FROM component_definitions cd JOIN component_types ct ON cd.type_id=ct.id
        WHERE ct.ifc_class='IfcDoor'
    ''').fetchone()[0]
    ok = door[0] == raw_door_count == 129
    print('§Q1_AGG_CHECK aggCount=%d rawCount=%d expected=129 match=%s' % (door[0], raw_door_count, ok))
    con.close()
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
