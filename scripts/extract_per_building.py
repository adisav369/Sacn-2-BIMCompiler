# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""Extract per-building DBs from sandbox + component_library.
Each building gets:
  {name}_extracted.db  — elements_meta, element_transforms, element_instances, surface_styles
  {name}_library.db    — component_geometries (only hashes used by that building), surface_styles
"""
import sqlite3, os, sys, json

SANDBOX = "deploy/sandbox_1M_extracted.db"
LIBRARY = "deploy/component_library.db"
OUTDIR  = "deploy/buildings"

# Buildings already extracted (skip these)
EXISTING = {
    'SampleHouse', 'BimWhale_Tall', 'AC90_Jasmin', 'FZKHaus',
    'AC90_Niedriha', 'BimWhale_Basic', 'VogelGesamt', 'BimWhale_Large',
    'AC9_HausGH', 'HospitalAuckland', 'SmileyWest', 'Jesse',
    'AC11Institute', 'Duplex',
}

# Map: display_name → list of building names in sandbox
# T0_ buildings are unique large buildings
# For city copies (S{n}_0_{arch}), we use the T0_ version only
LARGE_BUILDINGS = {
    'LTU_AHouse':           ['T0_LTU_AHouse'],
    'Hospital':             ['T0_Hospital'],
    'Hospital_3':           ['T0_Hospital_3'],
    'Terminal':             ['T0_Terminal'],
    'Clinic':               ['T0_Clinic'],
    'Ifc4_Revit':           ['T0_Ifc4_Revit'],
    'WBDG_Office':          ['T0_WBDG_Office'],
    'HHS_Office_Federated': ['T0_HHS_Office_Federated'],
    'HITOS':                ['T0_HITOS'],
    'Esplanades':           ['T0_Esplanades'],
    'HospitalGarage':       ['T0_HospitalGarage'],
    'HospitalGarage_2':     ['T0_HospitalGarage_2'],
    # City archetypes — just extract T0_ version (same geometry, different position)
    'Schependomlaan':       ['T0_Schependomlaan'] if False else ['S0_0_Schependomlaan'],
    'SampleCastle':         ['S0_0_SampleCastle'],
    'Molio':                ['S0_0_Molio'],
    'BimWhale_Advanced':    ['S0_0_BimWhale_Advanced'],
}

def extract_building(sandbox_conn, lib_conn, display_name, building_names):
    """Extract one building's data into per-building DBs."""
    ext_path = os.path.join(OUTDIR, f"{display_name}_extracted.db")
    lib_path = os.path.join(OUTDIR, f"{display_name}_library.db")

    if os.path.exists(ext_path):
        print(f"  SKIP {display_name} (already exists)")
        return ext_path, lib_path, 0

    placeholders = ','.join('?' * len(building_names))

    # --- Extracted DB ---
    if os.path.exists(ext_path): os.remove(ext_path)
    ext = sqlite3.connect(ext_path)
    ec = ext.cursor()

    # Create tables
    ec.execute("""CREATE TABLE elements_meta (
        id INTEGER PRIMARY KEY, guid TEXT UNIQUE NOT NULL,
        discipline TEXT NOT NULL, ifc_class TEXT NOT NULL,
        element_name TEXT, element_type TEXT, storey TEXT,
        material_name TEXT, material_rgba TEXT, building TEXT)""")

    ec.execute("""CREATE TABLE element_transforms (
        guid TEXT PRIMARY KEY,
        center_x REAL, center_y REAL, center_z REAL,
        transform_source TEXT,
        rotation_x REAL DEFAULT 0, rotation_y REAL DEFAULT 0, rotation_z REAL DEFAULT 0)""")

    ec.execute("""CREATE TABLE element_instances (
        guid TEXT PRIMARY KEY, geometry_hash TEXT)""")

    ec.execute("""CREATE TABLE surface_styles (
        style_name TEXT PRIMARY KEY, red REAL, green REAL, blue REAL, alpha REAL)""")

    # Copy elements_meta
    rows = sandbox_conn.execute(
        f"SELECT * FROM elements_meta WHERE building IN ({placeholders})",
        building_names).fetchall()
    ec.executemany("INSERT INTO elements_meta VALUES (?,?,?,?,?,?,?,?,?,?)", rows)
    count = len(rows)

    # Copy element_transforms (JOIN to get only matching GUIDs)
    guids = [r[1] for r in rows]  # guid is column 1
    guid_ph = ','.join('?' * len(guids))
    transforms = sandbox_conn.execute(
        f"SELECT * FROM element_transforms WHERE guid IN ({guid_ph})",
        guids).fetchall()
    ec.executemany("INSERT INTO element_transforms VALUES (?,?,?,?,?,?,?,?)", transforms)

    # Copy element_instances
    instances = sandbox_conn.execute(
        f"SELECT * FROM element_instances WHERE guid IN ({guid_ph})",
        guids).fetchall()
    ec.executemany("INSERT INTO element_instances VALUES (?,?)", instances)

    # Copy surface_styles (all — small table)
    try:
        styles = sandbox_conn.execute("SELECT * FROM surface_styles").fetchall()
        ec.executemany("INSERT OR IGNORE INTO surface_styles VALUES (?,?,?,?,?)", styles)
    except: pass

    ext.commit()
    ext.close()

    # --- Library DB ---
    # Get unique geometry hashes for this building
    hashes = list(set(r[1] for r in instances if r[1]))

    if os.path.exists(lib_path): os.remove(lib_path)
    lib = sqlite3.connect(lib_path)
    lc = lib.cursor()

    lc.execute("""CREATE TABLE component_geometries (
        geometry_hash TEXT PRIMARY KEY, vertices BLOB NOT NULL, faces BLOB NOT NULL,
        normals BLOB, vertex_count INTEGER NOT NULL, face_count INTEGER NOT NULL)""")

    lc.execute("""CREATE TABLE surface_styles (
        style_name TEXT PRIMARY KEY, red REAL, green REAL, blue REAL, alpha REAL)""")

    # Fetch geometry BLOBs in chunks (SQLite param limit)
    geo_count = 0
    CHUNK = 500
    for i in range(0, len(hashes), CHUNK):
        chunk = hashes[i:i+CHUNK]
        ph = ','.join('?' * len(chunk))
        geos = lib_conn.execute(
            f"SELECT * FROM component_geometries WHERE geometry_hash IN ({ph})",
            chunk).fetchall()
        lc.executemany("INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?,?,?)", geos)
        geo_count += len(geos)

    # Also try base_geometries from sandbox (fallback)
    missing = len(hashes) - geo_count
    if missing > 0:
        for i in range(0, len(hashes), CHUNK):
            chunk = hashes[i:i+CHUNK]
            ph = ','.join('?' * len(chunk))
            try:
                geos = sandbox_conn.execute(
                    f"SELECT geometry_hash, vertices, faces, normals, vertex_count, face_count FROM base_geometries WHERE geometry_hash IN ({ph})",
                    chunk).fetchall()
                lc.executemany("INSERT OR IGNORE INTO component_geometries VALUES (?,?,?,?,?,?)", geos)
                geo_count += len(geos)
            except: pass

    # Copy surface_styles
    try:
        styles = lib_conn.execute("SELECT * FROM surface_styles").fetchall()
        lc.executemany("INSERT OR IGNORE INTO surface_styles VALUES (?,?,?,?,?)", styles)
    except: pass

    lib.commit()
    lib.close()

    ext_size = os.path.getsize(ext_path)
    lib_size = os.path.getsize(lib_path)
    print(f"  {display_name}: {count} elements, {geo_count} meshes, "
          f"extracted={ext_size//1024}KB library={lib_size//1024}KB")
    return ext_path, lib_path, count


def main():
    os.makedirs(OUTDIR, exist_ok=True)

    print(f"Opening {SANDBOX}...")
    sandbox = sqlite3.connect(SANDBOX)
    print(f"Opening {LIBRARY}...")
    library = sqlite3.connect(LIBRARY)

    manifest = {}
    total = 0

    for name, buildings in LARGE_BUILDINGS.items():
        if name in EXISTING:
            print(f"  SKIP {name} (in EXISTING set)")
            continue
        ext_path, lib_path, count = extract_building(sandbox, library, name, buildings)
        total += count
        if count > 0:
            manifest[name] = {
                'db': f"{name}_extracted.db",
                'lib': f"{name}_library.db",
                'count': count,
                'ext_kb': os.path.getsize(ext_path) // 1024,
                'lib_kb': os.path.getsize(lib_path) // 1024,
            }

    sandbox.close()
    library.close()

    print(f"\nDone. {len(manifest)} buildings extracted, {total} total elements.")
    print("\nManifest:")
    for name, info in sorted(manifest.items(), key=lambda x: -x[1]['count']):
        print(f"  {name}: {info['count']} el, {info['ext_kb']}KB + {info['lib_kb']}KB")


if __name__ == '__main__':
    main()
