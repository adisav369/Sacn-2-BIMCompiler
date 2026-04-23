"""
Stress test — Blender headless viewport load
Run: blender --background --python scripts/stress_blender_test.py

Tests two modes against sandbox.db:
  Stage 1 — create wireframe bbox objects from elements_rtree (no meshes)
  Stage 2 — create GPU-instanced mesh objects from base_geometries

PRIME RULE: reads only from sandbox.db — no invention.
"""

import bpy
import numpy as np
import sqlite3
import time
import resource
import sys
import os
from pathlib import Path
from mathutils import Vector

SANDBOX_DB = Path(__file__).parent / "sandbox.db"
LOG_FILE   = Path(__file__).parent / "stress_log_blender.txt"

# S173: Log level — FINE enables proof checks (RECONSTRUCT, IFC_TRUTH, PORT_CONNECT).
# INFO skips them. Set via BIM_LOG_LEVEL env var or default to FINE until hardened.
LOG_LEVEL = os.environ.get("BIM_LOG_LEVEL", "FINE").upper()


def log(msg):
    line = f"[BLENDER] {msg}"
    print(line)
    with open(LOG_FILE, "a") as f:
        f.write(line + "\n")


def peak_ram_mb():
    return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024


def clear_scene():
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete()
    for mesh in list(bpy.data.meshes):
        bpy.data.meshes.remove(mesh)


# ── Stage 1: wireframe bbox objects ──────────────────────────────────────────

def make_wireframe_mesh(name, x0, x1, y0, y1, z0, z1):
    verts = [
        (x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),
        (x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1),
    ]
    edges = [
        (0,1),(1,2),(2,3),(3,0),
        (4,5),(5,6),(6,7),(7,4),
        (0,4),(1,5),(2,6),(3,7),
    ]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, edges, [])
    mesh.update()
    return mesh


def run_stage1(conn, collection, limit=None):
    query = "SELECT m.guid, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ FROM elements_meta m JOIN elements_rtree r ON m.id = r.id"
    if limit:
        query += f" LIMIT {limit}"

    t0 = time.perf_counter()
    rows = conn.execute(query).fetchall()
    t_query = time.perf_counter() - t0
    log(f"Stage1 QUERY — {len(rows):,} rows in {t_query:.3f}s")

    t1 = time.perf_counter()
    created = 0
    for (guid, x0, x1, y0, y1, z0, z1) in rows:
        mesh = make_wireframe_mesh(f"WF_{guid[:8]}", x0, x1, y0, y1, z0, z1)
        obj  = bpy.data.objects.new(f"WF_{guid[:8]}", mesh)
        collection.objects.link(obj)
        created += 1
        if created % 50000 == 0:
            log(f"Stage1 — {created:,} objects created  RAM: {peak_ram_mb():.0f} MB")

    t_build = time.perf_counter() - t1
    log(f"Stage1 OBJECTS — {created:,} wireframe objects in {t_build:.3f}s  RAM: {peak_ram_mb():.0f} MB")
    log(f"Stage1 TOTAL — query {t_query:.3f}s + build {t_build:.3f}s = {t_query+t_build:.3f}s")
    return created


# ── Stage 2: GPU instanced meshes ────────────────────────────────────────────

import struct


def euler_to_matrix(rx, ry, rz):
    """Reconstruct 3x3 rotation matrix from Euler XYZ angles."""
    import math as _m
    a, b, c = rx, ry, rz
    return np.array([
        [_m.cos(b)*_m.cos(c), _m.sin(a)*_m.sin(b)*_m.cos(c)-_m.cos(a)*_m.sin(c), _m.cos(a)*_m.sin(b)*_m.cos(c)+_m.sin(a)*_m.sin(c)],
        [_m.cos(b)*_m.sin(c), _m.sin(a)*_m.sin(b)*_m.sin(c)+_m.cos(a)*_m.cos(c), _m.cos(a)*_m.sin(b)*_m.sin(c)-_m.sin(a)*_m.cos(c)],
        [-_m.sin(b),           _m.sin(a)*_m.cos(b),                                 _m.cos(a)*_m.cos(b)]
    ])

def build_mesh_from_blobs(name, verts_blob, faces_blob):
    floats = struct.unpack(f'<{len(verts_blob)//4}f', verts_blob)
    verts  = [(floats[i], floats[i+1], floats[i+2]) for i in range(0, len(floats), 3)]
    ints   = struct.unpack(f'<{len(faces_blob)//4}I', faces_blob)
    faces  = [(ints[i], ints[i+1], ints[i+2]) for i in range(0, len(ints), 3)]
    mesh   = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    return mesh


def run_stage2(conn, collection, limit=None):
    from mathutils import Euler
    import numpy as np

    # Load unique meshes first
    t0 = time.perf_counter()
    geom_rows = conn.execute(
        "SELECT geometry_hash, vertices, faces FROM base_geometries"
    ).fetchall()
    log(f"Stage2 MESH LOAD — {len(geom_rows)} unique meshes from DB in {time.perf_counter()-t0:.3f}s")

    mesh_cache = {}
    blob_cache = {}  # raw verts for proof check
    for (h, verts_blob, faces_blob) in geom_rows:
        if verts_blob and faces_blob:
            mesh_cache[h] = build_mesh_from_blobs(h[:8], verts_blob, faces_blob)
            blob_cache[h] = verts_blob

    log(f"Stage2 MESH BUILD — {len(mesh_cache)} meshes built  RAM: {peak_ram_mb():.0f} MB")

    # S173: Read transforms (centre + rotation) — this is how the loader places meshes
    query = """
        SELECT m.guid, m.element_name, ei.geometry_hash,
               et.center_x, et.center_y, et.center_z,
               et.rotation_x, et.rotation_y, et.rotation_z,
               r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
        FROM elements_meta m
        JOIN element_instances ei ON ei.guid = m.guid
        JOIN element_transforms et ON et.guid = m.guid
        JOIN elements_rtree r ON r.id = m.id
    """
    if limit:
        query += f" LIMIT {limit}"

    t1 = time.perf_counter()
    rows = conn.execute(query).fetchall()
    log(f"Stage2 QUERY — {len(rows):,} placements in {time.perf_counter()-t1:.3f}s")

    t2 = time.perf_counter()
    placed = 0
    missing = 0
    # S173: proof counters — spot-check 20 random elements (FINE mode only)
    import random, math
    proof_indices = set()
    if LOG_LEVEL == "FINE" and rows:
        proof_indices = set(random.sample(range(len(rows)), min(20, len(rows))))
    proof_ok = 0
    proof_fail = 0
    proof_worst = 0.0

    for i, (guid, name, geom_hash, cx, cy, cz, rx, ry, rz,
            x0, x1, y0, y1, z0, z1) in enumerate(rows):
        mesh = mesh_cache.get(geom_hash)
        if not mesh:
            missing += 1
            continue
        obj = bpy.data.objects.new(f"{guid[:8]}", mesh)
        obj.location = Vector((cx, cy, cz))
        obj.rotation_euler = Euler((rx, ry, rz), 'XYZ')
        obj['guid'] = guid
        obj['geometry_hash'] = geom_hash
        collection.objects.link(obj)
        placed += 1

        # S173: RECONSTRUCT proof — rot @ lib_verts + centre vs rtree bbox
        if i in proof_indices and geom_hash in blob_cache:
            verts = np.frombuffer(blob_cache[geom_hash], dtype=np.float32).reshape(-1, 3)
            a, b, c = rx, ry, rz
            R = np.array([
                [math.cos(b)*math.cos(c), math.sin(a)*math.sin(b)*math.cos(c)-math.cos(a)*math.sin(c), math.cos(a)*math.sin(b)*math.cos(c)+math.sin(a)*math.sin(c)],
                [math.cos(b)*math.sin(c), math.sin(a)*math.sin(b)*math.sin(c)+math.cos(a)*math.cos(c), math.cos(a)*math.sin(b)*math.sin(c)-math.sin(a)*math.cos(c)],
                [-math.sin(b),             math.sin(a)*math.cos(b),                                      math.cos(a)*math.cos(b)]
            ], dtype=np.float64)
            world = (R @ verts.astype(np.float64).T).T + np.array([cx, cy, cz])
            w_min = world.min(axis=0)
            w_max = world.max(axis=0)
            err = max(abs(w_min[0]-x0), abs(w_max[0]-x1),
                      abs(w_min[1]-y0), abs(w_max[1]-y1),
                      abs(w_min[2]-z0), abs(w_max[2]-z1))
            if err > proof_worst:
                proof_worst = err
            if err > 0.1:
                proof_fail += 1
                log(f"  FAIL RECONSTRUCT {guid[:16]} err={err:.3f}m "
                    f"recon_X=[{w_min[0]:.2f},{w_max[0]:.2f}] rtree_X=[{x0:.2f},{x1:.2f}]")
            else:
                proof_ok += 1

        if placed % 50000 == 0:
            log(f"Stage2 — {placed:,} instances placed  RAM: {peak_ram_mb():.0f} MB")

    t_place = time.perf_counter() - t2
    log(f"Stage2 INSTANCES — {placed:,} placed ({missing} no mesh) in {t_place:.3f}s  RAM: {peak_ram_mb():.0f} MB")
    log(f"Stage2 TOTAL — {time.perf_counter()-t0:.3f}s")

    # S173: Proof summary — RECONSTRUCT (internal consistency)
    tag = "PASS" if proof_fail == 0 and proof_ok > 0 else "FAIL"
    log(f"§PROOF RECONSTRUCT {tag}  {proof_ok} ok, {proof_fail} fail  "
        f"worst={proof_worst:.4f}m  (rot@mesh+centre vs rtree, {len(proof_indices)} sampled)")

    # S173: ROT_TRUTH — FINE mode: verify against IFC source at the event
    # Checks: lib_mesh == IFC local mesh, stored Euler→R == IFC rot3, XY centre match
    if LOG_LEVEL != "FINE":
        log(f"§PROOF ROT_TRUTH SKIPPED — BIM_LOG_LEVEL={LOG_LEVEL}")
        return

    ifc_dir = Path(__file__).parent.parent / "DAGCompiler/lib/input/IFC/UNMERGED"
    ifc_files = list(ifc_dir.glob("*.ifc")) if ifc_dir.exists() else []
    if not ifc_files:
        log(f"§PROOF ROT_TRUTH SKIPPED — no source IFCs (decoupled mode)")
        return

    try:
        import ifcopenshell, ifcopenshell.geom
    except ImportError:
        log(f"§PROOF ROT_TRUTH SKIPPED — ifcopenshell not available")
        return

    # Build stored transforms lookup
    stored_tx = {}
    for r in conn.execute("""
        SELECT et.guid, et.center_x, et.center_y, et.center_z,
               et.rotation_x, et.rotation_y, et.rotation_z, ei.geometry_hash
        FROM element_transforms et
        JOIN element_instances ei ON ei.guid = et.guid
    """):
        stored_tx[r[0]] = r[1:]

    rot_ok = rot_fail = 0
    MAX_CHECKS = 30
    checked = 0

    for ifc_path in ifc_files[:3]:
        if checked >= MAX_CHECKS:
            break
        ifc_file = ifcopenshell.open(str(ifc_path))
        settings = ifcopenshell.geom.settings()
        settings.set(settings.USE_WORLD_COORDS, False)
        settings.set(settings.WELD_VERTICES, True)
        ifc_it = ifcopenshell.geom.iterator(settings, ifc_file)
        if not ifc_it.initialize():
            continue

        while checked < MAX_CHECKS:
            shape = ifc_it.get()
            guid = shape.guid
            if guid not in stored_tx:
                if not ifc_it.next():
                    break
                continue

            cx, cy, cz, rx, ry, rz, ghash = stored_tx[guid]
            iter_verts = np.array(shape.geometry.verts, dtype=np.float64).reshape(-1, 3)
            if len(iter_verts) < 3:
                if not ifc_it.next():
                    break
                continue

            lib_row = conn.execute(
                "SELECT vertices FROM base_geometries WHERE geometry_hash=?",
                (ghash,)).fetchone()
            # Try library if extracted DB has NULL blobs
            if (not lib_row or not lib_row[0]):
                try:
                    lib_db = sqlite3.connect(str(
                        Path(__file__).parent.parent / "library/component_library.db"))
                    lib_row = lib_db.execute(
                        "SELECT vertices FROM component_geometries WHERE geometry_hash=?",
                        (ghash,)).fetchone()
                    lib_db.close()
                except Exception:
                    pass
            if not lib_row or not lib_row[0]:
                if not ifc_it.next():
                    break
                continue

            lib_verts = np.frombuffer(lib_row[0], dtype=np.float32).reshape(-1, 3)

            # Check 1: mesh identity
            mesh_ok = np.allclose(iter_verts.astype(np.float32), lib_verts, atol=1e-4)

            # Check 2: rotation matrix
            mat = np.array(list(shape.transformation.matrix), dtype=np.float64).reshape(4, 4).T
            rot3 = mat[:3, :3]
            R = euler_to_matrix(rx, ry, rz)
            rot_match = np.allclose(rot3, R, atol=1e-6)

            # Check 3: XY centre
            iter_centre = mat[:3, 3]
            xy_ok = abs(iter_centre[0] - cx) < 0.01 and abs(iter_centre[1] - cy) < 0.01

            if mesh_ok and rot_match and xy_ok:
                rot_ok += 1
            else:
                rot_fail += 1
                if rot_fail <= 3:
                    log(f"  FAIL ROT_TRUTH {guid[:16]} {shape.type} "
                        f"mesh={mesh_ok} rot={rot_match} xy={xy_ok}")

            checked += 1
            if not ifc_it.next():
                break

    tag = "PASS" if rot_fail == 0 and rot_ok > 0 else ("FAIL" if rot_fail > 0 else "SKIP")
    log(f"§PROOF ROT_TRUTH {tag}  {rot_ok} ok, {rot_fail} fail  "
        f"(lib_mesh==iter, Euler→R==rot3, XY match — {checked} checked vs IFC)")


# ── Main ─────────────────────────────────────────────────────────────────────

if not SANDBOX_DB.exists():
    print(f"ERROR: sandbox.db not found at {SANDBOX_DB}. Run stress_loader.py first.")
    sys.exit(1)

conn = sqlite3.connect(str(SANDBOX_DB))
total = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
unique_geom = conn.execute("SELECT COUNT(*) FROM base_geometries").fetchone()[0]
log(f"sandbox.db — {total:,} objects, {unique_geom} unique meshes")

# Stage 1
log("=== STAGE 1: Wireframe bbox objects (no meshes) ===")
col1 = bpy.data.collections.new("Stage1_Stress")
bpy.context.scene.collection.children.link(col1)
run_stage1(conn, col1)

# Clear before Stage 2
clear_scene()

# Stage 2
if unique_geom > 0:
    log("=== STAGE 2: GPU instanced meshes ===")
    col2 = bpy.data.collections.new("Stage2_Stress")
    bpy.context.scene.collection.children.link(col2)
    run_stage2(conn, col2)
else:
    log("=== STAGE 2: SKIP — sandbox.db has no base_geometries (run stress_loader.py B first) ===")

conn.close()
log("DONE — see stress_log_blender.txt")
