#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — scope: item 4 §W5-RATCHET (RESUME_DISC_WALKER_ENVELOPE_BOUND.md). The COMMITTED
# VERBATIM HOME of the mined per-room Z placement offsets (sibling of seed_shim_attributes.py — same
# reason: the mining inputs are gitignored, a fresh checkout must converge from committed scripts
# alone). Wired into scripts/rebuild_erp.sh's tail. Idempotent: full recreate to exactly these rows.
#
# PROVENANCE: every row mined 2026-07-11 by scripts/mine_placement_offset_space.py from
# build/Duplex_mep_extracted.db element_transforms (median real device center per space_type × device
# family, ad_element_mep_alias DX_MINED classification, M_Product.source_element_ref device bridge,
# n>=2 + W2-containment + W3-ceiling-band guards, FLOOR-host rows hz-compensated for the walker's
# half-height lift). To re-derive: run the miner and diff its printed seed block against ROWS below.
import sqlite3, os, sys

DB = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'library/disc_patterns.db')
if len(sys.argv) > 1: DB = sys.argv[1]
if not os.path.exists(DB):
    print('❌ PRECONDITION %s missing — run ./scripts/rebuild_erp.sh first' % DB)
    sys.exit(1)

ROWS = [
    ('BATHROOM', 'OUTLET_GFCI', 'FLOOR', 1.2985, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=OUTLET'),
    ('BATHROOM', 'SINK', 'FLOOR', 0.8954, 6, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=6 family=SINK'),
    ('BATHROOM', 'SWITCH', 'FLOOR', 1.2995, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SWITCH'),
    ('BATHROOM', 'TOILET', 'FLOOR', 0.0204, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=TOILET hz-compensated dim_z=0.7684'),
    ('BEDROOM', 'OUTLET', 'FLOOR', 0.5315, 17, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=17 family=OUTLET'),
    ('BEDROOM', 'SWITCH', 'FLOOR', 1.2935, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=SWITCH'),
    ('CORRIDOR', 'SWITCH', 'FLOOR', 1.2935, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SWITCH'),
    ('KITCHEN', 'OUTLET_20A', 'FLOOR', 1.1475, 12, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=12 family=OUTLET'),
    ('KITCHEN', 'OUTLET_GFCI', 'FLOOR', 1.1475, 12, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=12 family=OUTLET'),
    ('KITCHEN', 'SINK', 'FLOOR', 0.9117, 2, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=2 family=SINK'),
    ('LIVING', 'OUTLET', 'FLOOR', 0.5315, 4, 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', 'DX_MINED element_transforms n=4 family=OUTLET'),
]

db = sqlite3.connect(DB)
db.execute("CREATE TABLE IF NOT EXISTS ad_placement_offset_space ("
           "space_type_id TEXT NOT NULL, device_id TEXT NOT NULL, z_rule TEXT NOT NULL, "
           "z_offset REAL NOT NULL, n_measured INTEGER NOT NULL, source TEXT NOT NULL, "
           "provenance TEXT, PRIMARY KEY (space_type_id, device_id))")
db.execute("DELETE FROM ad_placement_offset_space")
db.executemany("INSERT INTO ad_placement_offset_space VALUES (?,?,?,?,?,?,?)", ROWS)
db.commit()
n = db.execute("SELECT COUNT(*) FROM ad_placement_offset_space").fetchone()[0]
print('§OFFSET-SEED %s: inserted %d, table now %d rows (%d expected)' % (DB, len(ROWS), n, len(ROWS)))
sys.exit(0 if n == len(ROWS) else 1)
