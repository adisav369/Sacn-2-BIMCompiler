# BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
# Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
# SPDX-License-Identifier: MIT
#!/usr/bin/env python3
"""
S189: BLOB tessellation worker — per-chunk subprocess.

Reads geometry BLOBs from component_library.db, tessellates via from_pydata,
applies materials (surface_styles + Principled BSDF), transforms, and saves
a self-contained fat .blend chunk.

Replaces library.blend dependency entirely — no 305MB file read.

Usage:
    blender --background --factory-startup --python blob_tessellate_worker.py -- \
        --db extracted.db \
        --library-db component_library.db \
        --building Hospital \
        --offset 0 --limit 31500 \
        --output baked/Hospital_chunk0.blend

See prompts/S189_blob_tessellation.md for spec.
See prompts/S188_rtree_ux_performance.md §Part F for rationale.
"""
import sys, os, time, struct
from datetime import datetime as _dt

def _ts():
    """Wall-clock timestamp for log correlation across processes."""
    return _dt.now().strftime('%H:%M:%S.%f')[:-3]

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
import argparse
parser = argparse.ArgumentParser()
parser.add_argument("--db", required=True, help="Extracted DB (sandbox or single-building)")
parser.add_argument("--library-db", default="", help="Path to component_library.db")
parser.add_argument("--building", required=True, help="Building name to bake")
parser.add_argument("--offset", type=int, default=0, help="Element offset (chunk start)")
parser.add_argument("--limit", type=int, default=0, help="Element limit (chunk size, 0=all)")
parser.add_argument("--disciplines", default="", help="Comma-separated discipline filter (e.g. ARC,HEAT)")
parser.add_argument("--output", required=True, help="Output .blend path")
parser.add_argument("--merge", nargs='+', default=[], help="Merge mode: list of chunk .blend files to combine")
parser.add_argument("--base", default="", help="Base .blend to open before merging (progressive save)")
parser.add_argument("--wait", type=int, default=0, help="Wait N seconds before merging (user saves & closes Blender)")
args = parser.parse_args(argv)

import bpy
import sqlite3
from mathutils import Matrix, Euler, Vector
from pathlib import Path

db_path = Path(args.db).resolve()
lib_db_path = Path(args.library_db).resolve()
out_path = Path(args.output).resolve()
out_path.parent.mkdir(parents=True, exist_ok=True)

if not db_path.exists():
    raise FileNotFoundError(f"DB not found: {db_path}")
if not lib_db_path.exists():
    raise FileNotFoundError(f"Library DB not found: {lib_db_path}")


# ── BLOB unpacking (ported from stage2_tessellation_loader.py) ──

def unpack_vertices(blob):
    """Unpack binary BLOB into list of (x,y,z) vertex tuples."""
    if not blob:
        return []
    floats = struct.unpack(f'<{len(blob)//4}f', blob)
    return [(floats[i], floats[i+1], floats[i+2]) for i in range(0, len(floats), 3)]


def unpack_faces(blob):
    """Unpack binary BLOB into list of (i1,i2,i3) face tuples."""
    if not blob:
        return []
    ints = struct.unpack(f'<{len(blob)//4}I', blob)
    return [(ints[i], ints[i+1], ints[i+2]) for i in range(0, len(ints), 3)]


# ── Helpers (shared with bake_building_blend.py) ──

def has_building_column(db):
    conn = sqlite3.connect(str(db))
    cols = [r[1] for r in conn.execute("PRAGMA table_info(elements_meta)").fetchall()]
    conn.close()
    return 'building' in cols


def get_site_offset(db):
    conn = sqlite3.connect(str(db))
    try:
        row = conn.execute(
            "SELECT site_offset_x, site_offset_y, site_offset_z FROM site_context LIMIT 1"
        ).fetchone()
        conn.close()
        if row:
            return Vector((row[0] or 0.0, row[1] or 0.0, row[2] or 0.0))
    except Exception:
        conn.close()
    return Vector((0.0, 0.0, 0.0))


def get_redirect_corrections(lib_db, hashes):
    """Query geometry_hash_redirect for rotation/offset corrections."""
    rot_corr = {}
    pos_corr = {}
    if not hashes:
        return rot_corr, pos_corr
    try:
        conn = sqlite3.connect(str(lib_db))
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
        print(f"  [S189] redirect query skipped: {e}")
    return rot_corr, pos_corr


def load_surface_styles(db):
    """Load surface_styles from extracted DB. Returns dict: style_name -> rgba string."""
    styles = {}
    try:
        conn = sqlite3.connect(str(db))
        tables = [r[0] for r in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='surface_styles'"
        ).fetchall()]
        if 'surface_styles' not in tables:
            conn.close()
            return styles
        for r in conn.execute(
                "SELECT style_name, surface_r, surface_g, surface_b, "
                "COALESCE(transparency, 0) FROM surface_styles"):
            alpha = max(0.0, 1.0 - (r[4] or 0.0))
            styles[r[0]] = f"{r[1]:.3f},{r[2]:.3f},{r[3]:.3f},{alpha:.3f}"
        conn.close()
        print(f"  [S189] surface_styles: {len(styles)} entries loaded")
    except Exception as e:
        print(f"  [S189] surface_styles load skipped: {e}")
    return styles


def resolve_rgba(material_name, rgba_str, surface_styles):
    """Resolve material RGBA: direct -> surface_styles lookup -> None."""
    if rgba_str:
        return rgba_str, 'direct'
    if not material_name or not surface_styles:
        return None, 'none'
    if material_name in surface_styles:
        return surface_styles[material_name], 'surface_styles'
    for part in material_name.split(':'):
        part = part.strip()
        if part in surface_styles:
            return surface_styles[part], 'surface_styles'
    return None, 'none'


def create_material(rgba_str):
    """Create or reuse a Blender material from RGBA string.
    S188 pattern: Principled BSDF for transparency."""
    mat_key = f"Bake_{rgba_str}"
    mat = bpy.data.materials.get(mat_key)
    if mat is not None:
        return mat
    try:
        r, g, b, a = map(float, rgba_str.split(','))
    except Exception:
        return None
    mat = bpy.data.materials.new(name=mat_key)
    mat.diffuse_color = (r, g, b, a)
    # S188: node-based material for transparency (Blender 5.0 EEVEE Next)
    if a < 0.99:
        mat.use_nodes = True
        nodes = mat.node_tree.nodes
        nodes.clear()
        out_n = nodes.new('ShaderNodeOutputMaterial')
        bsdf = nodes.new('ShaderNodeBsdfPrincipled')
        if 'Base Color' in bsdf.inputs:
            bsdf.inputs['Base Color'].default_value = (r, g, b, 1.0)
        if 'Alpha' in bsdf.inputs:
            bsdf.inputs['Alpha'].default_value = a
        mat.node_tree.links.new(bsdf.outputs['BSDF'], out_n.inputs['Surface'])
        try:
            mat.blend_method = 'BLEND'
        except (AttributeError, TypeError):
            pass
    return mat


# ── Main bake logic ──

def bake_chunk(building, db, lib_db, offset_elem, limit_elem, site_offset):
    t0 = time.time()
    chunk_label = f"offset={offset_elem}" if limit_elem else "all"
    print(f"\n{'='*60}")
    print(f"[S189] {_ts()} §BLOB_SPAWN bld={building} chunk={chunk_label} pid={os.getpid()}")
    print(f"{'='*60}")

    # Fresh scene
    bpy.ops.wm.read_factory_settings(use_empty=True)

    # Load surface_styles for material resolution
    surface_styles = load_surface_styles(db)

    # Also try library DB for surface_styles (sandbox merges them there)
    lib_styles = load_surface_styles(lib_db)
    for k, v in lib_styles.items():
        if k not in surface_styles:
            surface_styles[k] = v

    conn = sqlite3.connect(str(db))
    _has_bld = has_building_column(db)

    # Fetch elements for this building
    # S189z: discipline-based chunking — --disciplines filter takes priority over offset/limit
    disc_filter = args.disciplines.split(',') if args.disciplines else []
    limit_clause = f"LIMIT {limit_elem} OFFSET {offset_elem}" if limit_elem and not disc_filter else ""
    if disc_filter:
        _ph = ','.join('?' * len(disc_filter))
        _disc_where = f"AND m.discipline IN ({_ph})"
    else:
        _disc_where = ""
    if _has_bld:
        _params = [building] + disc_filter
        elements = conn.execute(f"""
            SELECT m.guid, m.element_name, m.discipline, m.ifc_class, m.storey,
                   m.material_rgba, i.geometry_hash, m.material_name
            FROM elements_meta m
            JOIN element_instances i ON m.guid = i.guid
            WHERE m.building = ? AND i.geometry_hash IS NOT NULL
              AND m.ifc_class != 'IfcOpeningElement'
              {_disc_where}
            ORDER BY m.discipline
            {limit_clause}
        """, _params).fetchall()
    else:
        elements = conn.execute(f"""
            SELECT m.guid, m.element_name, m.discipline, m.ifc_class, m.storey,
                   m.material_rgba, i.geometry_hash, m.material_name
            FROM elements_meta m
            JOIN element_instances i ON m.guid = i.guid
            WHERE i.geometry_hash IS NOT NULL
              AND m.ifc_class != 'IfcOpeningElement'
              {_disc_where}
            ORDER BY m.discipline
            {limit_clause}
        """, disc_filter).fetchall()

    # S189z: log what this worker is baking
    _disc_breakdown = {}
    for e in elements:
        d = e[2] or 'OTHER'
        _disc_breakdown[d] = _disc_breakdown.get(d, 0) + 1
    print(f"  {_ts()} §WORKER elements={len(elements)} "
          f"discs={_disc_breakdown} filter={disc_filter or 'ALL'}")

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
    print(f"  Unique meshes needed: {len(unique_hashes)}")

    # ── BLOB tessellation: read from component_library.db, create via from_pydata ──
    t_tess = time.time()
    mesh_cache = {}  # geometry_hash -> bpy.types.Mesh
    blob_miss = 0
    lib_conn = sqlite3.connect(str(lib_db))

    for ci in range(0, len(unique_hashes), 999):
        chunk = unique_hashes[ci:ci+999]
        ph = ','.join('?' * len(chunk))
        rows = lib_conn.execute(f"""
            SELECT geometry_hash, vertices, faces
            FROM component_geometries
            WHERE geometry_hash IN ({ph})
        """, chunk).fetchall()
        found = {r[0] for r in rows}
        for ghash, verts_blob, faces_blob in rows:
            verts = unpack_vertices(verts_blob)
            faces = unpack_faces(faces_blob)
            if not verts:
                blob_miss += 1
                continue
            mesh = bpy.data.meshes.new(ghash)
            mesh.from_pydata(verts, [], faces)
            mesh.update()
            mesh.materials.append(None)  # S189: material slot for per-object color
            mesh_cache[ghash] = mesh
        # Count misses (hash in DB but no BLOB)
        for h in chunk:
            if h not in found:
                blob_miss += 1

    lib_conn.close()
    tess_s = time.time() - t_tess
    print(f"  {_ts()} BLOB tessellation: {len(mesh_cache)} meshes in {tess_s:.1f}s "
          f"(miss={blob_miss})")

    # Redirect corrections
    rot_correction, pos_correction = get_redirect_corrections(lib_db, unique_hashes)

    # Create discipline collections
    # S189z: root collection — must NOT end with a disc suffix (ARC/HEAT/etc)
    # or the linker's suffix filter will pick it up alongside its children.
    disc_collections = {}
    root_col = bpy.data.collections.new(building)
    bpy.context.scene.collection.children.link(root_col)

    ox, oy, oz = site_offset.x, site_offset.y, site_offset.z

    placed = 0
    no_mesh = 0
    no_xform = 0
    ss_hits = 0

    for guid, ename, disc, ifc_class, storey, rgba_str, ghash, mat_name in elements:
        mesh = mesh_cache.get(ghash)
        if not mesh:
            no_mesh += 1
            continue

        # Discipline collection
        disc_key = disc or 'OTHER'
        if disc_key not in disc_collections:
            dc = bpy.data.collections.new(f"{building}_{disc_key}")
            root_col.children.link(dc)
            disc_collections[disc_key] = dc
        col = disc_collections[disc_key]

        # Create object
        obj_name = ename[:60] if ename else guid[:20]
        obj = bpy.data.objects.new(obj_name, mesh)
        col.objects.link(obj)

        # Material — resolve via surface_styles when material_rgba is empty
        resolved, source = resolve_rgba(mat_name, rgba_str, surface_styles)
        if source == 'surface_styles':
            ss_hits += 1
        if resolved:
            try:
                r, g, b, a = map(float, resolved.split(','))
                obj.color = (r, g, b, a)
                mat = create_material(resolved)
                if mat and len(obj.material_slots) > 0:
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

    # S189: Store source DB path as scene property so Preview can resume RTree mode
    bpy.context.scene["fed_db_path"] = str(db)
    bpy.context.scene["fed_building"] = building

    # Save
    bpy.ops.wm.save_as_mainfile(filepath=str(out_path))
    elapsed = time.time() - t0
    file_mb = out_path.stat().st_size / (1024 * 1024) if out_path.exists() else 0
    print(f"  [S189] {_ts()} §BLOB_COMPLETE bld={building} chunk={chunk_label} "
          f"placed={placed} no_mesh={no_mesh} no_xform={no_xform} "
          f"ss_hits={ss_hits} tess={tess_s:.1f}s "
          f"discs={list(disc_collections.keys())} "
          f"file={out_path.name} size={file_mb:.1f}MB elapsed={elapsed:.1f}s")


def _log(msg):
    """Print and flush — ensures poll/run can read progress in real time."""
    print(msg, flush=True)


def merge_chunks(building, db, chunk_files, output, base_blend=""):
    """S189: Merge chunk .blend files into one combined .blend.
    If base_blend is given, opens that first (progressive save — keeps old baked buildings)."""
    t0 = time.time()
    _log(f"\n{'='*60}")
    _log(f"[S189] {_ts()} §MERGE_START bld={building} chunks={len(chunk_files)} "
         f"base={'YES' if base_blend else 'fresh'} pid={os.getpid()}")
    _log(f"{'='*60}")

    import bpy
    from pathlib import Path

    if base_blend and Path(base_blend).exists():
        # Progressive save — open user's current session, append new building
        bpy.ops.wm.open_mainfile(filepath=base_blend)
        _log(f"  [S189] {_ts()} opened base: {Path(base_blend).name} "
             f"({Path(base_blend).stat().st_size / (1024*1024):.1f}MB)")
        # Shred any partial overnight objects for this building
        prefix = f"Loaded_{building}_"
        for col in list(bpy.data.collections):
            if col.name.startswith(prefix):
                for obj in list(col.objects):
                    bpy.data.objects.remove(obj, do_unlink=True)
                bpy.data.collections.remove(col)
        # Also remove old Baked_ collection if re-baking
        old_baked = bpy.data.collections.get(f"Baked_{building}")
        if old_baked:
            for child in list(old_baked.children):
                for obj in list(child.objects):
                    bpy.data.objects.remove(obj, do_unlink=True)
                bpy.data.collections.remove(child)
            bpy.data.collections.remove(old_baked)
            _log(f"  [S189] {_ts()} removed old Baked_{building}")
    else:
        bpy.ops.wm.read_factory_settings(use_empty=True)

    _disc_suffixes = {'ARC','STR','MEP','ELEC','FP','OTHER',
                      'PLB','HEAT','HVAC','VENT','SAN','ACMV'}

    total_objects = 0
    for cp in chunk_files:
        if not Path(cp).exists():
            _log(f"  [S189] {_ts()} SKIP missing: {cp}")
            continue
        t_c = time.time()
        cp_mb = Path(cp).stat().st_size / (1024*1024)
        _log(f"  [S189] {_ts()} linking {Path(cp).name} ({cp_mb:.1f}MB)...")
        # S189j: link=True — reference only, no mesh copy. 10s instead of 5min.
        # Baked files stay in baked/ as mesh source. Session stays small.
        with bpy.data.libraries.load(cp, link=True) as (src, dst):
            disc_cols = [c for c in src.collections
                         if any(c.endswith(f'_{d}') for d in _disc_suffixes)]
            if disc_cols:
                dst.collections = disc_cols
            elif src.collections:
                dst.collections = [src.collections[0]]
        for col in dst.collections:
            if col is not None:
                bpy.context.scene.collection.children.link(col)
                total_objects += len(col.all_objects)
        merge_s = time.time() - t_c
        _log(f"  [S189] {_ts()} §MERGE_LINK {Path(cp).name} "
             f"in {merge_s:.1f}s — {total_objects} objects so far")

    # Store DB path for Preview auto-resume
    bpy.context.scene["fed_db_path"] = str(db)
    bpy.context.scene["fed_building"] = building

    # Save combined
    out_path = Path(output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    mesh_count = len(bpy.data.meshes)
    obj_count = len(bpy.data.objects)
    _log(f"  [S189] {_ts()} §MERGE_SAVING meshes={mesh_count} objects={obj_count} "
         f"target={out_path.name}...")
    t_save = time.time()
    bpy.ops.wm.save_as_mainfile(filepath=str(out_path.resolve()))
    save_s = time.time() - t_save
    elapsed = time.time() - t0
    file_mb = out_path.stat().st_size / (1024*1024) if out_path.exists() else 0
    _log(f"  [S189] {_ts()} §MERGE_COMPLETE bld={building} objects={total_objects} "
         f"file={out_path.name} size={file_mb:.1f}MB save={save_s:.1f}s total={elapsed:.1f}s")

    # S189j: Keep baked files — link=True references need them on disk
    _log(f"  [S189] {_ts()} baked files retained ({len(chunk_files)} linked)")


# ── Main ──

if args.merge:
    # S189k: Wait for user to save & close Blender before merging
    if args.wait > 0:
        _log(f"[S189] {_ts()} §MERGE_WAIT {args.wait}s — save & close Blender")
        for _i in range(args.wait, 0, -10):
            _log(f"[S189] {_ts()} §MERGE_COUNTDOWN {_i}s remaining...")
            time.sleep(min(10, _i))
        _log(f"[S189] {_ts()} §MERGE_COUNTDOWN done — finalizing")
    # Merge mode — combine chunk .blends into one (with optional base for progressive save)
    merge_chunks(args.building, Path(args.db).resolve(), args.merge, args.output,
                 base_blend=args.base)
else:
    # Bake mode — tessellate from BLOBs
    db_path = Path(args.db).resolve()
    lib_db_path = Path(args.library_db).resolve()
    if not db_path.exists():
        raise FileNotFoundError(f"DB not found: {db_path}")
    if not lib_db_path.exists():
        raise FileNotFoundError(f"Library DB not found: {lib_db_path}")
    offset = get_site_offset(db_path)
    print(f"[S189] Site offset: ({offset.x:.2f}, {offset.y:.2f}, {offset.z:.2f})")
    bake_chunk(args.building, db_path, lib_db_path, args.offset, args.limit, offset)
