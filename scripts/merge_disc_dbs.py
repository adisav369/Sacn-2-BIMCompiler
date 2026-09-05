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
import sqlite3, glob, os, sys

def merge_with_disc(pattern, output, disc_map, building_name):
    files = sorted(glob.glob(pattern))
    print(f'Merging {len(files)} files → {output}')
    
    if os.path.exists(output): os.remove(output)
    out = sqlite3.connect(output)
    oc = out.cursor()
    
    oc.execute('CREATE TABLE project_metadata (key TEXT PRIMARY KEY, value TEXT)')
    oc.execute('CREATE TABLE elements_meta (guid TEXT PRIMARY KEY, ifc_class TEXT, element_name TEXT, storey TEXT, discipline TEXT, material_name TEXT, material_rgba TEXT, building TEXT)')
    oc.execute('CREATE TABLE element_transforms (guid TEXT PRIMARY KEY, center_x REAL, center_y REAL, center_z REAL, rotation_x REAL, rotation_y REAL, rotation_z REAL, bbox_x REAL, bbox_y REAL, bbox_z REAL)')
    oc.execute('CREATE TABLE element_instances (guid TEXT PRIMARY KEY, geometry_hash TEXT)')
    oc.execute('CREATE TABLE component_geometries (geometry_hash TEXT PRIMARY KEY, vertices BLOB, faces BLOB, building TEXT)')
    oc.execute('CREATE TABLE schedules (schedule_id TEXT PRIMARY KEY, name TEXT, status TEXT, created_date TEXT)')
    oc.execute('CREATE TABLE tasks (task_id TEXT PRIMARY KEY, schedule_id TEXT, name TEXT, start_date TEXT, finish_date TEXT, duration_days REAL, status TEXT)')
    oc.execute('CREATE TABLE task_sequences (predecessor_id TEXT, successor_id TEXT, sequence_type TEXT, lag_days REAL DEFAULT 0, PRIMARY KEY (predecessor_id, successor_id))')
    oc.execute('CREATE TABLE task_elements (task_id TEXT, guid TEXT, PRIMARY KEY (task_id, guid))')
    
    oc.execute('INSERT INTO project_metadata VALUES (?,?)', ('building_name', building_name))
    oc.execute('INSERT INTO project_metadata VALUES (?,?)', ('import_date', '2026-05-02'))
    
    total = 0
    for f in files:
        # Determine discipline from filename via disc_map
        stem = os.path.basename(f).replace('.db', '')
        # Strip prefix (ltu_ or hosp_)
        for prefix in disc_map:
            if prefix in stem:
                disc = disc_map[prefix]
                break
        else:
            disc = 'ARC'  # fallback
        
        src = sqlite3.connect(f)
        src.row_factory = sqlite3.Row
        
        rows = src.execute('SELECT * FROM elements_meta').fetchall()
        for r in rows:
            oc.execute('INSERT OR IGNORE INTO elements_meta VALUES (?,?,?,?,?,?,?,?)',
                      (r['guid'], r['ifc_class'], r['element_name'], r['storey'],
                       disc, r['material_name'], r['material_rgba'], building_name))
        
        for r in src.execute('SELECT * FROM element_transforms').fetchall():
            oc.execute('INSERT OR IGNORE INTO element_transforms VALUES (?,?,?,?,?,?,?,?,?,?)',
                      tuple(r))
        
        for r in src.execute('SELECT * FROM element_instances').fetchall():
            oc.execute('INSERT OR IGNORE INTO element_instances VALUES (?,?)', tuple(r))
        
        for r in src.execute('SELECT * FROM component_geometries').fetchall():
            oc.execute('INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?)', tuple(r))
        
        count = len(rows)
        total += count
        src.close()
        print(f'  + {os.path.basename(f)}: {count} elements → disc={disc}')
    
    out.commit()
    final = oc.execute('SELECT count(*) FROM elements_meta').fetchone()[0]
    hashes = oc.execute('SELECT count(DISTINCT geometry_hash) FROM element_instances').fetchone()[0]
    discs = oc.execute('SELECT discipline, count(*) FROM elements_meta GROUP BY discipline ORDER BY count(*) DESC').fetchall()
    out.close()
    print(f'  TOTAL: {final} elements, {hashes} unique hashes')
    print(f'  DISCIPLINES: {dict(discs)}')

# Will be called after Node.js extraction completes
if __name__ == '__main__':
    what = sys.argv[1] if len(sys.argv) > 1 else 'both'
    
    if what in ('ltu', 'both'):
        merge_with_disc('/tmp/reextract/ltu_LTU_AHouse_*.db', '/tmp/reextract/LTU_AHouse.db',
            {'ARC': 'ARC', 'STR': 'STR', 'PLB': 'PLB', 'SAN': 'SAN',
             'HEAT': 'HEAT', 'COOL': 'ACMV', 'AIR': 'VENT', 'DUCT': 'ACMV', 'VOID': 'VOID'},
            'LTU_AHouse')
    
    if what in ('hosp', 'both'):
        merge_with_disc('/tmp/reextract/hosp_Hospital_IFC4_*.db', '/tmp/reextract/Hospital.db',
            {'ARC': 'ARC', 'STR': 'STR', 'PLB': 'PLB', 'MECH': 'MEP',
             'ELE': 'ELEC', 'FIRE': 'FP', 'SPR': 'FP'},
            'Hospital')
