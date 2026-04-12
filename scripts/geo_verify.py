#!/usr/bin/env python3
"""
GEO Verification Script — All-pairs relative offset proof.

Joins GEO TACK LEAF log against extraction DB by IFC GUID,
computes all-pairs relative offsets, reports MATCH/DRIFT.

Usage:
    python3 scripts/geo_verify.py <geo_log> <output_db> <extraction_db>

Example:
    python3 scripts/geo_verify.py \
        "logs/pipeline_Sample House_extracted_20260330_033940.log" \
        DAGCompiler/lib/output/samplehouse.db \
        DAGCompiler/lib/input/SampleHouse_extracted.db

Evidence: SH 58 elements, 1653 pairs, 0.002mm worst, ZERO DRIFT.
          DX 179 elements (GUID-matched), 15931 pairs, 0.004mm worst, ZERO DRIFT.

See: docs/LAST_MILE_PROBLEM.md §7 (GEO smoking gun)
     docs/SPATIAL_COMPILATION_PAPER.md §4 (experimental results)
     docs/TestArchitecture.md (gate verification)
"""
import sqlite3
import re
import sys

def parse_geo_log(log_path):
    """Parse GEO TACK LEAF lines — first occurrence per GUID."""
    geo = {}
    occurrence = {}
    with open(log_path) as f:
        for line in f:
            if '[GEO  ] TACK' not in line or 'LEAF' not in line:
                continue
            m = re.search(
                r'LEAF\s+(.+?)\s+guid=(\S+)\s+.*LBD=\(([-\d.]+),([-\d.]+),([-\d.]+)\)',
                line)
            if m:
                guid = m.group(2)
                # Strip mirrored unit prefix (A_/B_) to match raw IFC GUIDs
                if re.match(r'^[AB]_', guid):
                    guid = guid[2:]
                occurrence[guid] = occurrence.get(guid, 0) + 1
                if occurrence[guid] == 1:
                    geo[guid] = {
                        'name': m.group(1)[:55],
                        'x': float(m.group(3)),
                        'y': float(m.group(4)),
                        'z': float(m.group(5))
                    }
    return geo

def load_positions(db_path, guid_column, table_join):
    """Load element positions from a SQLite DB."""
    conn = sqlite3.connect(db_path)
    rows = conn.execute(f'''
        SELECT {guid_column}, r.minX, r.minY, r.minZ
        FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
        {table_join}
    ''').fetchall()
    conn.close()
    return {r[0]: (r[1], r[2], r[3]) for r in rows}

def main():
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)

    log_path, output_db, extraction_db = sys.argv[1], sys.argv[2], sys.argv[3]

    # Parse GEO log
    geo = parse_geo_log(log_path)

    # Load output positions (by element_ref = IFC GUID)
    out_pos = load_positions(output_db, 'em.element_ref', 'WHERE em.element_ref IS NOT NULL')

    # Load extraction positions (by guid)
    ext_pos = load_positions(extraction_db, 'em.guid', '')

    out_count_row = sqlite3.connect(output_db).execute('SELECT COUNT(*) FROM elements_meta').fetchone()
    out_count = out_count_row[0] if out_count_row else 0

    print(f'GEO GUIDs: {len(geo)}  Output elements: {out_count}  Extraction GUIDs: {len(ext_pos)}')
    print()

    # A. GEO log == output DB
    geo_match = sum(1 for g, v in geo.items()
                    if g in out_pos and
                    max(abs(v['x']-out_pos[g][0]),
                        abs(v['y']-out_pos[g][1]),
                        abs(v['z']-out_pos[g][2])) * 1000 <= 1.0)
    print(f'A. GEO log == output DB: {geo_match}/{len(geo)} within 1mm')

    # B. GUID chain: GEO log GUID found in extraction guid (direct match)
    guid_both = [g for g in geo.keys() if g in ext_pos]
    guid_in_out = sum(1 for g in geo.keys() if g in out_pos)
    print(f'B. GUID chain intact: {len(guid_both)}/{len(geo)} GEO→extraction, {guid_in_out}/{len(geo)} GEO→output')

    # C. All-pairs relative offset (GEO compiled LBD vs extraction positions)
    total = match = drift = 0
    worst = 0.0
    worst_pair = ''
    for i in range(len(guid_both)):
        for j in range(i + 1, len(guid_both)):
            g1, g2 = guid_both[i], guid_both[j]
            # Compiled positions from GEO log (LBD = placed min corner)
            c1 = (geo[g1]['x'], geo[g1]['y'], geo[g1]['z'])
            c2 = (geo[g2]['x'], geo[g2]['y'], geo[g2]['z'])
            e1, e2 = ext_pos[g1], ext_pos[g2]
            err = max(abs((c2[k]-c1[k]) - (e2[k]-e1[k])) * 1000 for k in range(3))
            total += 1
            if err <= 1.0:
                match += 1
            else:
                drift += 1
                if err > worst:
                    worst = err
                    worst_pair = f'{geo[g1]["name"][:30]} <> {geo[g2]["name"][:30]}'

    print()
    print(f'C. All-pairs relative offset (GUID-matched):')
    print(f'   Elements: {len(guid_both)}')
    print(f'   Pairs:    {total}')
    print(f'   MATCH:    {match}')
    print(f'   DRIFT:    {drift}')
    print(f'   Worst:    {worst:.3f}mm')
    if worst_pair:
        print(f'   Worst at: {worst_pair}')
    print()

    if drift == 0 and total > 0:
        print(f'VERDICT: {len(guid_both)} elements, {total} pairs, {worst:.3f}mm worst. ZERO DRIFT.')
    elif drift > 0:
        print(f'VERDICT: {drift} DRIFT(s) detected. Worst: {worst:.3f}mm at {worst_pair}')
    else:
        print(f'VERDICT: No pairs to compare.')

if __name__ == '__main__':
    main()
