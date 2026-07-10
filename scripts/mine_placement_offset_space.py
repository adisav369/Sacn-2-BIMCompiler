#!/usr/bin/env python3
# ⚠ DO NOT REMOVE — scope: item 4 §W5-RATCHET (RESUME_DISC_WALKER_ENVELOPE_BOUND.md). Mines per-room
# (space_type × device) Z placement offsets from the REAL Duplex MEP per-instance transforms and writes
# ad_placement_offset_space into library/disc_patterns.db. EXTRACT ONLY — every value is a median of
# measured element_transforms centers; guards below refuse anything that would violate W2/W3. Read the
# log after every run; §OFFSET-MINE lines are the evidence.
#
# Re-derivation tool. The committed verbatim home of the mined rows (fresh-env path, no gitignored
# inputs needed) is scripts/seed_placement_offset_space.py — after re-mining, diff this script's
# printed seed block against the seeder before trusting either.
#
# Inputs (gitignored, documented env prep — same copies the §LIVEWIRE round-3 protocol used):
#   build/Duplex_mep_extracted.db   — per-instance element_transforms (item 4's named source)
#   deploy/buildings/Duplex_extracted.db — spaces (stamped object_type; run scripts/stamp_space_longnames.py first)
#   library/disc_patterns.db        — committed alias/bridge data + WRITE TARGET (regen: scripts/rebuild_erp.sh)
#   build/duplex_rules.db           — current projection (host_surface/dim_z_m per row; regen:
#                                     python3 build/project_rule_space_schedule.py build/duplex_rules.db residential)
import sqlite3, statistics, sys, os, re

BC = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATHS = {
    'mep': os.path.join(BC, 'build/Duplex_mep_extracted.db'),
    'arc': os.path.join(BC, 'deploy/buildings/Duplex_extracted.db'),
    'pat': os.path.join(BC, 'library/disc_patterns.db'),
    'rules': os.path.join(BC, 'build/duplex_rules.db'),
}
for k, p in PATHS.items():
    if not os.path.exists(p):
        print('❌ PRECONDITION %s missing: %s — see header for the generator/env-prep command' % (k, p))
        sys.exit(1)

mep = sqlite3.connect(PATHS['mep'])
arc = sqlite3.connect(PATHS['arc'])
pat = sqlite3.connect(PATHS['pat'])
rules = sqlite3.connect(PATHS['rules'])

# 1. COMMITTED family classifier (ad_element_mep_alias element_name patterns, priority asc)
alias = list(pat.execute("SELECT canonical_type, match_value FROM ad_element_mep_alias "
                         "WHERE match_field='element_name' AND is_active=1 ORDER BY priority"))
def canonical_of(name):
    for canon, like in alias:
        if like.strip('%').upper() in (name or '').upper():
            return canon
    return None

# 2. COMMITTED device bridge: schedule device_id -> canonical family, via M_Product.source_element_ref
#    (e.g. OUTLET_20A -> 'M_Duplex Receptacle:Standard' -> OUTLET). Direct canonicals map to themselves.
canon_set = {r[0] for r in pat.execute("SELECT Value FROM ad_element_mep")}
bridge = {}
for dev, in pat.execute("SELECT DISTINCT mep_product_id FROM ad_space_type_mep_bom"):
    if dev in canon_set:
        bridge[dev] = dev
    else:
        r = pat.execute("SELECT source_element_ref FROM M_Product WHERE product_id=?", (dev,)).fetchone()
        c = canonical_of(r[0]) if r and r[0] else None
        if c: bridge[dev] = c

# 3. Spaces — same source + label normalization as disc_walker.spacesOf/_spaceTypeFor
spaces = []
for g, ot, nm, cx, cy, cz, sx, sy, sz in arc.execute(
        "SELECT guid, object_type, name, center_x, center_y, center_z, size_x, size_y, size_z "
        "FROM spatial_structure WHERE type='IfcSpace'"):
    if not sx or sx < 0.1 or not sy or sy < 0.1: continue
    label = (ot or nm or '').strip()
    norm = re.sub(r'[_\s]*\d+$', '', re.sub(r'\s+', '_', label.upper())).strip()
    spaces.append(dict(norm=norm, x0=cx-sx/2, x1=cx+sx/2, y0=cy-sy/2, y1=cy+sy/2, z0=cz-sz/2, z1=cz+sz/2))
if not any(s['norm'] and not s['norm'].startswith('SPACE') for s in spaces):
    print('❌ PRECONDITION Duplex spaces unstamped — run: python3 scripts/stamp_space_longnames.py')
    sys.exit(1)

stype_direct = {r[0] for r in rules.execute("SELECT value FROM rule_space_type")}
stype_alias = dict(rules.execute("SELECT alias, space_type_id FROM rule_space_alias"))
def stype_of(norm): return norm if norm in stype_direct else stype_alias.get(norm)

# 4. Measure: real terminal centers grouped by (space_type, canonical family).
# FRAME (caught by W6+W5 on the first run, 2026-07-11): the two extractions disagree per family —
# Duplex_mep_extracted center_z is insertion-point-like, Duplex_extracted center_z is bbox-center
# (WC +0.33, sconce +0.20, pendant −0.54). W5 grades against Duplex_extracted's transforms, so
# offsets MUST be measured in that frame. mep_extracted stays the terminal ROSTER (item 4's named
# per-instance source, guid-identical — asserted below); the oracle frame supplies the centers.
roster = {g for g, in mep.execute("SELECT guid FROM elements_meta WHERE ifc_class='IfcFlowTerminal'")}
oracle = {g for g, in arc.execute("SELECT m.guid FROM elements_meta m JOIN element_transforms t "
                                  "ON m.guid=t.guid WHERE m.ifc_class='IfcFlowTerminal'")}
if roster != oracle:
    print('❌ PRECONDITION terminal guid sets differ between mep_extracted (%d) and Duplex_extracted (%d)'
          % (len(roster), len(oracle)))
    sys.exit(1)
pools = {}
for name, x, y, z in arc.execute(
        "SELECT m.element_name, t.center_x, t.center_y, t.center_z FROM elements_meta m "
        "JOIN element_transforms t ON m.guid=t.guid WHERE m.ifc_class='IfcFlowTerminal'"):
    canon = canonical_of(name)
    if not canon: continue
    best, bestd = None, 1e9
    for s in spaces:
        if s['x0']-0.3 <= x <= s['x1']+0.3 and s['y0']-0.3 <= y <= s['y1']+0.3 and s['z0']-0.5 <= z <= s['z1']+0.5:
            d = max(0, s['x0']-x, x-s['x1']) + max(0, s['y0']-y, y-s['y1'])
            if d < bestd: best, bestd = s, d
    st = stype_of(best['norm']) if best else None
    if not st: continue
    pools.setdefault((st, canon), {'dzf': [], 'dzc': [], 'hts': []})
    pools[(st, canon)]['dzf'].append(z - best['z0'])
    pools[(st, canon)]['dzc'].append(best['z1'] - z)
    pools[(st, canon)]['hts'].append(best['z1'] - best['z0'])

# 5. Derive overrides for existing schedule rows, guards enforced
rows = []
for disc, st, dev, host, zr, zo, dz in rules.execute(
        "SELECT disc, space_type_id, device_id, host_surface, z_rule, z_offset_m, dim_z_m FROM rule_space_schedule"):
    canon = bridge.get(dev)
    pool = pools.get((st, canon)) if canon else None
    if not pool or len(pool['dzf']) < 2:
        continue  # n>=2 guard — no override without at least 2 measured instances
    n = len(pool['dzf'])
    min_ht = min(pool['hts'])
    if zr == 'CEILING':
        off = round(statistics.median(pool['dzc']), 4)
        # W2 containment (center >= floor-5cm ... <= ceiling+5cm) + W3 CEILING-BAND (top half)
        if off < -0.05 or off > min_ht / 2:
            print('§OFFSET-SKIP %s/%s/%s — measured CEILING offset %.3f violates W2/W3 guard (min height %.2f); SKIPPED, observation only' % (disc, st, dev, off, min_ht))
            continue
        # W6 walking-band guard (caught live 2026-07-11: bathroom sconces are WALL lights — a z-only
        # override under a CEILING_* rule keeps center-of-room xy, and lowering it into the 0→1.8m
        # walking band parks an obstacle mid-floor → flood-fill fragmentation, W6 0.543). CEILING-rule
        # overrides only apply when the fixture bbox stays FULLY above the band; wall-mounted-light
        # xy+z mining is the named follow-up alongside edge_x/edge_y.
        if min_ht - off - dz / 2 <= 1.8:
            print('§OFFSET-SKIP %s/%s/%s — lowered CEILING fixture bottom %.2f enters the W6 walking band (<=1.8m); SKIPPED, needs xy+z wall-light mining (follow-up)' % (disc, st, dev, min_ht - off - dz / 2))
            continue
    else:  # FLOOR z semantics; FLOOR-host rows get the walker's half-height lift compensated out
        med = statistics.median(pool['dzf'])
        off = round(med - (dz / 2 if host == 'FLOOR' else 0), 4)
        final = med  # walker-final center above floor
        if final < -0.05 or final > min_ht + 0.05:
            print('§OFFSET-SKIP %s/%s/%s — measured center %.3f outside space band; SKIPPED' % (disc, st, dev, final))
            continue
    if abs(off - zo) < 5e-4:
        continue  # identical to the generic rule — nothing to override
    prov = 'DX_MINED element_transforms n=%d family=%s%s' % (
        n, canon, ' hz-compensated dim_z=%.4f' % dz if host == 'FLOOR' and zr != 'CEILING' else '')
    rows.append((st, dev, zr, off, n,
                 'roster=build/Duplex_mep_extracted.db centers=Duplex_extracted.db(oracle frame) 2026-07-11', prov))
    print('§OFFSET-MINE %s/%s/%s %s z: %.3f -> %.4f (n=%d, %s)' % (disc, st, dev, zr, zo, off, n, prov))

# 6. Write table (deterministic recreate) + emit the seed block for the verbatim-home diff
pat.execute("CREATE TABLE IF NOT EXISTS ad_placement_offset_space ("
            "space_type_id TEXT NOT NULL, device_id TEXT NOT NULL, z_rule TEXT NOT NULL, "
            "z_offset REAL NOT NULL, n_measured INTEGER NOT NULL, source TEXT NOT NULL, "
            "provenance TEXT, PRIMARY KEY (space_type_id, device_id))")
pat.execute("DELETE FROM ad_placement_offset_space")
pat.executemany("INSERT INTO ad_placement_offset_space VALUES (?,?,?,?,?,?,?)", rows)
pat.commit()
print('§OFFSET-MINE DONE %d overrides written to ad_placement_offset_space; table now %d rows' %
      (len(rows), pat.execute("SELECT COUNT(*) FROM ad_placement_offset_space").fetchone()[0]))
print('\n# ---- seed block (paste into scripts/seed_placement_offset_space.py ROWS) ----')
for r in sorted(rows):
    print('    ' + repr(r) + ',')
