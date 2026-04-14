#!/usr/bin/env python3
"""
S186: Bake a single building (or all buildings) from extracted DB → .blend

Each building gets one .blend with all objects placed, meshes linked from
library.blend, organised into discipline collections (ARC, STR, MEP, ELEC, FP).

Usage:
    blender --background --python scripts/bake_building_blend.py -- \
        --db scripts/sandbox_1M.db \
        --library library/library.blend \
        --building Hospital

    blender --background --python scripts/bake_building_blend.py -- \
        --db scripts/sandbox_1M.db \
        --library library/library.blend \
        --all

Output: baked/{building}_baked.blend
"""
import sys, os, time

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--db", required=True, help="Extracted DB (sandbox or single-building)")
parser.add_argument("--library", required=True, help="Path to library.blend")
parser.add_argument("--building", default=None, help="Building name to bake")
parser.add_argument("--all", action="store_true", help="Bake all buildings in DB")
parser.add_argument("--output-dir", default="baked", help="Output directory (default: baked/)")
args = parser.parse_args(argv)

import bpy
import sqlite3
from mathutils import Matrix, Euler, Vector
from pathlib import Path

db_path = Path(args.db).resolve()
lib_path = Path(args.library).resolve()
out_dir = Path(args.output_dir)
out_dir.mkdir(parents=True, exist_ok=True)

if not db_path.exists():
    raise FileNotFoundError(f"DB not found: {db_path}")
if not lib_path.exists():
    raise FileNotFoundError(f"library.blend not found: {lib_path}")

# Resolve component_library.db for redirect corrections
lib_db_path = lib_path.parent / "component_library.db"


def has_building_column(db):
    """Check if elements_meta has a 'building' column."""
    conn = sqlite3.connect(str(db))
    cols = [r[1] for r in conn.execute("PRAGMA table_info(elements_meta)").fetchall()]
    conn.close()
    return 'building' in cols


def get_buildings(db):
    """List distinct buildings in DB. Returns [db_stem] for single-building DBs."""
    if not has_building_column(db):
        return [db.stem.replace('_extracted', '')]
    conn = sqlite3.connect(str(db))
    rows = conn.execute(
        "SELECT DISTINCT building FROM elements_meta "
        "WHERE building IS NOT NULL AND building != '' ORDER BY building"
    ).fetchall()
    conn.close()
    return [r[0] for r in rows]


def get_site_offset(db):
    """Read site_context offset (IFC → Blender coordinate shift)."""
    conn = sqlite3.connect(str(db))
    try:
        row = conn.execute("SELECT site_offset_x, site_offset_y, site_offset_z FROM site_context LIMIT 1").fetchone()
        conn.close()
        if row:
            return Vector((row[0] or 0.0, row[1] or 0.0, row[2] or 0.0))
    except Exception:
        conn.close()
    return Vector((0.0, 0.0, 0.0))


def get_redirect_corrections(hashes):
    """Query geometry_hash_redirect for rotation/offset corrections."""
    rot_corr = {}
    pos_corr = {}
    if not lib_db_path.exists() or not hashes:
        return rot_corr, pos_corr
    try:
        conn = sqlite3.connect(str(lib_db_path))
        for ci in range(0, len(hashes), 999):
            chunk = hashes[ci:ci+999]
            ph = ','.join('?' * len(chunk))
            rows = conn.execute(f"""
                SELECT canonical_hash,
                       rotation_x_correction, rotation_y_correction, rotation_z_correction,
                       offset_x_correction, offset_y_correction, offset_z_correction
                FROM geometry_hash_redirect
                WHERE canonical_hash IN ({ph})
                  AND (rotation_x_correction != 0 OR rotation_y_correction != 0
                       OR rotation_z_correction != 0
                       OR offset_x_correction != 0 OR offset_y_correction != 0
                       OR offset_z_correction != 0)
                GROUP BY canonical_hash
            """, chunk).fetchall()
            for row in rows:
                rot_corr[row[0]] = (row[1] or 0.0, row[2] or 0.0, row[3] or 0.0)
                pos_corr[row[0]] = (row[4] or 0.0, row[5] or 0.0, row[6] or 0.0)
        conn.close()
    except Exception as e:
        print(f"  [S186] redirect query skipped: {e}")
    return rot_corr, pos_corr


def bake_building(building_name, db, library, offset):
    """Bake one building into a .blend file."""
    t0 = time.time()
    print(f"\n{'='*60}")
    print(f"[S186] BAKING: {building_name}")
    print(f"{'='*60}")

    # Fresh scene
    bpy.ops.wm.read_factory_settings(use_empty=True)

    conn = sqlite3.connect(str(db))
    _has_bld = has_building_column(db)

    # Fetch all elements for this building
    if _has_bld:
        elements = conn.execute("""
            SELECT m.guid, m.element_name, m.discipline, m.ifc_class, m.storey,
                   m.material_rgba, i.geometry_hash
            FROM elements_meta m
            JOIN element_instances i ON m.guid = i.guid
            WHERE m.building = ? AND i.geometry_hash IS NOT NULL
        """, (building_name,)).fetchall()
    else:
        elements = conn.execute("""
            SELECT m.guid, m.element_name, m.discipline, m.ifc_class, m.storey,
                   m.material_rgba, i.geometry_hash
            FROM elements_meta m
            JOIN element_instances i ON m.guid = i.guid
            WHERE i.geometry_hash IS NOT NULL
        """).fetchall()

    # Fetch transforms
    guids = [e[0] for e in elements]
    transform_by_guid = {}
    for ci in range(0, len(guids), 999):
        chunk = guids[ci:ci+999]
        ph = ','.join('?' * len(chunk))
        for row in conn.execute(f"""
            SELECT guid, center_x, center_y, center_z,
                   rotation_x, rotation_y, rotation_z
            FROM element_transforms WHERE guid IN ({ph})
        """, chunk).fetchall():
            transform_by_guid[row[0]] = row[1:]
    conn.close()

    print(f"  Elements: {len(elements)}, Transforms: {len(transform_by_guid)}")

    # Unique geometry hashes
    unique_hashes = list({e[6] for e in elements if e[6]})
    print(f"  Unique meshes: {len(unique_hashes)}")

    # Link meshes from library.blend (keep linked — small file, chain to library)
    already = {m.name for m in bpy.data.meshes}
    to_link = [h for h in unique_hashes if h not in already]
    if to_link:
        t_link = time.time()
        with bpy.data.libraries.load(str(library), link=True) as (src, dst):
            src_names = set(src.meshes)
            dst.meshes = [h for h in to_link if h in src_names]
        linked_count = sum(1 for m in dst.meshes if m is not None)
        print(f"  Linked {linked_count} meshes in {time.time()-t_link:.1f}s (kept linked, no make_local)")

    mesh_by_hash = {}
    for h in unique_hashes:
        m = bpy.data.meshes.get(h)
        if m:
            mesh_by_hash[h] = m

    # Redirect corrections
    rot_correction, pos_correction = get_redirect_corrections(unique_hashes)

    # Create discipline collections
    disc_collections = {}
    root_col = bpy.data.collections.new(building_name)
    bpy.context.scene.collection.children.link(root_col)

    ox, oy, oz = offset.x, offset.y, offset.z

    placed = 0
    no_mesh = 0
    no_xform = 0

    for guid, ename, disc, ifc_class, storey, rgba_str, ghash in elements:
        mesh = mesh_by_hash.get(ghash)
        if not mesh:
            no_mesh += 1
            continue

        # Discipline collection
        disc_key = disc or 'OTHER'
        if disc_key not in disc_collections:
            dc = bpy.data.collections.new(f"{building_name}_{disc_key}")
            root_col.children.link(dc)
            disc_collections[disc_key] = dc
        col = disc_collections[disc_key]

        # Create object
        obj_name = ename[:60] if ename else guid[:20]
        obj = bpy.data.objects.new(obj_name, mesh)
        col.objects.link(obj)

        # Material color
        if rgba_str:
            try:
                r, g, b, a = map(float, rgba_str.split(','))
                obj.color = (r, g, b, a)
                if len(obj.material_slots) > 0:
                    mat_key = f"Bake_{rgba_str}"
                    mat = bpy.data.materials.get(mat_key)
                    if mat is None:
                        mat = bpy.data.materials.new(name=mat_key)
                        mat.diffuse_color = (r, g, b, a)
                    obj.material_slots[0].link = 'OBJECT'
                    obj.material_slots[0].material = mat
            except Exception:
                pass

        # Transform
        tr = transform_by_guid.get(guid)
        if tr:
            cx, cy, cz = tr[0], tr[1], tr[2]
            rx, ry, rz = tr[3] or 0.0, tr[4] or 0.0, tr[5] or 0.0
            # Redirect correction
            corr = rot_correction.get(ghash)
            rot_applied = False
            if corr:
                if corr[0] and abs(rx - (-corr[0])) < 0.01:
                    rx += corr[0]; rot_applied = True
                if corr[1] and abs(ry - (-corr[1])) < 0.01:
                    ry += corr[1]; rot_applied = True
                if corr[2] and abs(rz - (-corr[2])) < 0.01:
                    rz += corr[2]; rot_applied = True
            if rot_applied:
                pcorr = pos_correction.get(ghash)
                if pcorr:
                    cx += pcorr[0]; cy += pcorr[1]; cz += pcorr[2]
            loc_mat = Matrix.Translation((cx - ox, cy - oy, cz - oz))
            if rx or ry or rz:
                rot_mat = Euler((rx, ry, rz), 'XYZ').to_matrix().to_4x4()
                obj.matrix_basis = loc_mat @ rot_mat
            else:
                obj.matrix_basis = loc_mat
            placed += 1
        else:
            no_xform += 1

    # Save
    out_path = out_dir / f"{building_name}_baked.blend"
    bpy.ops.wm.save_as_mainfile(filepath=str(out_path.resolve()))
    elapsed = time.time() - t0
    print(f"  [S186] §PROOF BAKE bld={building_name} placed={placed} "
          f"no_mesh={no_mesh} no_xform={no_xform} "
          f"discs={list(disc_collections.keys())} "
          f"file={out_path.name} {elapsed:.1f}s")


# ── Main ──

offset = get_site_offset(db_path)
print(f"[S186] Site offset: ({offset.x:.2f}, {offset.y:.2f}, {offset.z:.2f})")

if args.all:
    buildings = get_buildings(db_path)
    print(f"[S186] Baking {len(buildings)} buildings")
    for bld in buildings:
        bake_building(bld, db_path, lib_path, offset)
    print(f"\n[S186] §PROOF ALL_BAKED count={len(buildings)}")
elif args.building:
    bake_building(args.building, db_path, lib_path, offset)
else:
    print("ERROR: specify --building NAME or --all")
    sys.exit(1)
