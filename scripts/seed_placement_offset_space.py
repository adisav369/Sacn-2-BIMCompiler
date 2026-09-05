#!/usr/bin/env python3
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
# ⚠ DO NOT REMOVE — scope: item 4 §W5-RATCHET (RESUME_DISC_WALKER_ENVELOPE_BOUND.md). The COMMITTED
# VERBATIM HOME of the mined per-room Z placement offsets (sibling of seed_shim_attributes.py — same
# reason: the mining inputs are gitignored, a fresh checkout must converge from committed scripts
# alone). Wired into scripts/rebuild_erp.sh's tail. Idempotent: full recreate to exactly these rows.
#
# PROVENANCE: every row mined 2026-07-11 by scripts/mine_placement_offset_space.py from
# build/Duplex_mep_extracted.db element_transforms (median real device center per space_type × device
# family, ad_element_mep_alias DX_MINED classification, M_Product.source_element_ref device bridge,
# n>=2 + W2-containment + W3-ceiling-band + W6-walking-band guards, FLOOR-host rows hz-compensated for
# the walker's half-height lift). Rows with a placement_rule are MEASURED wall-mounted lights (sconces,
# median edge distance 0.09m) — the rule swaps to the generic wall-anchored WALL_HIGH at projection
# time so the walker snaps them to a real wall face; pendants (centre xy, bottom inside the 0-1.8m
# walking band) are deliberately absent — see the miner's §OFFSET-SKIP lines.
# To re-derive: run the miner and diff its printed seed block against ROWS below.
import sqlite3, os, sys

DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'library/disc_patterns.db')
if len(sys.argv) > 1: DB = sys.argv[1]
if not os.path.exists(DB):
    print('❌ PRECONDITION %s missing — run ./scripts/rebuild_erp.sh first' % DB)
    sys.exit(1)

ROWS = [
    ('BATHROOM', 'LIGHT', 'WALL_HIGH', 'CEILING', 0.6103, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=LIGHT wall-mounted median-edge=0.09m'),
    ('BATHROOM', 'OUTLET_GFCI', None, 'FLOOR', 1.2985, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=OUTLET'),
    ('BATHROOM', 'SINK', None, 'FLOOR', 0.8954, 6, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=6 family=SINK'),
    ('BATHROOM', 'SWITCH', None, 'FLOOR', 1.2995, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SWITCH'),
    ('BATHROOM', 'TOILET', None, 'FLOOR', 0.0204, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=TOILET hz-compensated dim_z=0.7684'),
    ('BEDROOM', 'OUTLET', None, 'FLOOR', 0.5315, 17, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=17 family=OUTLET'),
    ('BEDROOM', 'SWITCH', None, 'FLOOR', 1.2935, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=SWITCH'),
    ('CORRIDOR', 'SWITCH', None, 'FLOOR', 1.2935, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SWITCH'),
    ('KITCHEN', 'OUTLET_20A', None, 'FLOOR', 1.1475, 12, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=12 family=OUTLET'),
    ('KITCHEN', 'OUTLET_GFCI', None, 'FLOOR', 1.1475, 12, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=12 family=OUTLET'),
    ('KITCHEN', 'SINK', None, 'FLOOR', 0.9117, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SINK'),
    ('LIVING', 'OUTLET', None, 'FLOOR', 0.5315, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=OUTLET'),
    ('LOBBY', 'LIGHT', 'WALL_HIGH', 'CEILING', 0.6103, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=LIGHT wall-mounted median-edge=0.09m'),
]

# §WALL-SLOT: per-fixture which-wall refs. EMPTY ON DUPLEX BY MEASUREMENT (2026-07-11): at true
# room-guid granularity every wall-anchored pool REFUSED — each mirrored unit mounts the same device
# class on the OPPOSITE absolute wall (unit A bathrooms XMIN, unit B XMAX; kitchen counter wall YMAX
# vs YMIN), and the schema's absolute MIN/MAX refs cannot express a mirror-dependent choice. See the
# miner's §SLOT-SKIP lines. The table+seam stay committed (probe-guarded, byte-inert at 0 rows) for
# any future pool that yields a consistent multiset; mirror-invariant anchors = named follow-up.
SLOTS = [
]

db = sqlite3.connect(DB)
db.execute("DROP TABLE IF EXISTS ad_placement_offset_space")
db.execute("CREATE TABLE ad_placement_offset_space ("
           "space_type_id TEXT NOT NULL, device_id TEXT NOT NULL, placement_rule TEXT, "
           "z_rule TEXT NOT NULL, z_offset REAL NOT NULL, n_measured INTEGER NOT NULL, "
           "source TEXT NOT NULL, provenance TEXT, PRIMARY KEY (space_type_id, device_id))")
db.executemany("INSERT INTO ad_placement_offset_space VALUES (?,?,?,?,?,?,?,?)", ROWS)
db.execute("DROP TABLE IF EXISTS ad_placement_wall_slots")
db.execute("CREATE TABLE ad_placement_wall_slots ("
           "space_type_id TEXT NOT NULL, device_id TEXT NOT NULL, slot_idx INTEGER NOT NULL, "
           "x_ref TEXT NOT NULL, edge_x REAL NOT NULL, y_ref TEXT NOT NULL, edge_y REAL NOT NULL, "
           "n_measured INTEGER NOT NULL, source TEXT NOT NULL, provenance TEXT, "
           "PRIMARY KEY (space_type_id, device_id, slot_idx))")
db.executemany("INSERT INTO ad_placement_wall_slots VALUES (?,?,?,?,?,?,?,?,?,?)", SLOTS)
db.commit()
n = db.execute("SELECT COUNT(*) FROM ad_placement_offset_space").fetchone()[0]
ns = db.execute("SELECT COUNT(*) FROM ad_placement_wall_slots").fetchone()[0]
print('§OFFSET-SEED %s: inserted %d offsets + %d wall slots, tables now %d + %d rows (%d + %d expected)'
      % (DB, len(ROWS), len(SLOTS), n, ns, len(ROWS), len(SLOTS)))
sys.exit(0 if (n == len(ROWS) and ns == len(SLOTS)) else 1)
