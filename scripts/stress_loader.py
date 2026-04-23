#!/usr/bin/env python3
"""
Stress Loader — StressTest_1M.md
Generates 1,000,000 spatial objects in sandbox.db from library/component_library.db.

Scenario A: bbox only  (elements_meta + elements_rtree)
Scenario B: bbox + mesh BLOBs  (+ base_geometries — expected to OOM/stall)

PRIME RULE: Uses only existing component_definitions IDs. No geometry invented.
Read docs/StressTest_1M.md before modifying.
"""

import sqlite3
import json
import time
import os
import sys
import uuid
import resource
import traceback
from pathlib import Path

# ── Paths ────────────────────────────────────────────────────────────────────
REPO_ROOT    = Path(__file__).parent.parent
LIBRARY_DB   = REPO_ROOT / "library" / "component_library.db"
SANDBOX_DB   = Path(__file__).parent / "sandbox.db"
MANIFEST_OUT = Path(__file__).parent / "stress_blend_manifest.json"
LOG_A        = Path(__file__).parent / "stress_log_A.txt"
LOG_B        = Path(__file__).parent / "stress_log_B.txt"

# ── Parameters ───────────────────────────────────────────────────────────────
SEED_SIZE    = int(os.environ.get("STRESS_SEED", "250"))
ROOM_COLS    = int(os.environ.get("STRESS_COLS", "40"))
ROOM_ROWS    = int(os.environ.get("STRESS_ROWS", "100"))
ROOM_SPACING = 15.0     # metres between room origins
TOTAL_ROOMS  = ROOM_COLS * ROOM_ROWS
TOTAL_OBJECTS = SEED_SIZE * TOTAL_ROOMS


def log(msg, file=None):
    line = f"[STRESS] {msg}"
    print(line)
    if file:
        file.write(line + "\n")
        file.flush()


def peak_ram_mb():
    """RSS in MB (Linux)."""
    try:
        return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024
    except Exception:
        return -1


# ── Step 1: Read seed from library.db ────────────────────────────────────────
def load_seed(logf):
    log(f"Reading {SEED_SIZE} seed products from {LIBRARY_DB}", logf)
    t0 = time.perf_counter()

    lib = sqlite3.connect(str(LIBRARY_DB))
    rows = lib.execute("""
        SELECT id, name, geometry_hash,
               local_min_x, local_max_x,
               local_min_y, local_max_y,
               local_min_z, local_max_z
        FROM component_definitions
        WHERE local_min_x IS NOT NULL
          AND geometry_hash IS NOT NULL
        LIMIT ?
    """, (SEED_SIZE,)).fetchall()
    lib.close()

    elapsed = time.perf_counter() - t0
    log(f"SEED — {len(rows)} products loaded from library.db in {elapsed:.3f}s", logf)
    return rows


# ── Step 2: Build abstract model (1M dicts) ───────────────────────────────────
def compile_model(seed, logf):
    log(f"COMPILE — mapping {TOTAL_OBJECTS:,} objects to abstract model...", logf)
    t0 = time.perf_counter()

    model = []
    obj_id = 1
    for row_idx in range(ROOM_ROWS):
        for col_idx in range(ROOM_COLS):
            offset_x = col_idx * ROOM_SPACING
            offset_y = row_idx * ROOM_SPACING
            storey = f"L{row_idx // 20}"   # 5 storeys of 20 rows each

            for (prod_id, name, geom_hash,
                 lx0, lx1, ly0, ly1, lz0, lz1) in seed:
                model.append({
                    'id':           obj_id,
                    'guid':         f"STRESS-{obj_id:08d}",
                    'discipline':   'STR',
                    'ifc_class':    'IfcFurnishingElement',
                    'element_name': name,
                    'storey':       storey,
                    'geometry_hash': geom_hash,
                    'minX': lx0 + offset_x,
                    'maxX': lx1 + offset_x,
                    'minY': ly0 + offset_y,
                    'maxY': ly1 + offset_y,
                    'minZ': lz0,
                    'maxZ': lz1,
                })
                obj_id += 1

    elapsed = time.perf_counter() - t0
    log(f"COMPILE — {len(model):,} objects mapped in {elapsed:.3f}s  "
        f"[RAM: {peak_ram_mb():.0f} MB]", logf)
    return model


# ── Step 3: Create sandbox.db ─────────────────────────────────────────────────
def create_sandbox(with_geom_table=False):
    if SANDBOX_DB.exists():
        SANDBOX_DB.unlink()
    conn = sqlite3.connect(str(SANDBOX_DB))
    conn.executescript("""
        CREATE TABLE elements_meta (
            id INTEGER PRIMARY KEY,
            guid TEXT UNIQUE NOT NULL,
            discipline TEXT NOT NULL,
            ifc_class TEXT NOT NULL,
            element_name TEXT,
            element_type TEXT,
            storey TEXT,
            material_name TEXT,
            material_rgba TEXT
        );
        CREATE VIRTUAL TABLE elements_rtree USING rtree(
            id, minX, maxX, minY, maxY, minZ, maxZ
        );
    """)
    if with_geom_table:
        conn.execute("""
            CREATE TABLE base_geometries (
                geometry_hash TEXT PRIMARY KEY,
                vertices BLOB NOT NULL,
                faces BLOB NOT NULL,
                vertex_count INTEGER,
                face_count INTEGER
            )
        """)
    conn.commit()
    return conn


# ── Step 4: Bulk insert (bbox only) ───────────────────────────────────────────
def insert_bbox_only(conn, model, logf):
    log(f"IO_TIME — inserting {len(model):,} rows into elements_meta + elements_rtree...", logf)
    t0 = time.perf_counter()

    conn.execute("BEGIN")
    meta_rows = [
        (o['id'], o['guid'], o['discipline'], o['ifc_class'],
         o['element_name'], None, o['storey'], None, None)
        for o in model
    ]
    conn.executemany(
        "INSERT INTO elements_meta VALUES (?,?,?,?,?,?,?,?,?)", meta_rows
    )

    t_meta = time.perf_counter()
    log(f"IO_TIME — elements_meta INSERT done in {t_meta - t0:.3f}s", logf)

    rtree_rows = [
        (o['id'], o['minX'], o['maxX'], o['minY'], o['maxY'], o['minZ'], o['maxZ'])
        for o in model
    ]
    conn.executemany(
        "INSERT INTO elements_rtree VALUES (?,?,?,?,?,?,?)", rtree_rows
    )
    conn.execute("COMMIT")

    t_total = time.perf_counter() - t0
    db_mb = SANDBOX_DB.stat().st_size / (1024 * 1024)
    log(f"IO_TIME — elements_rtree INSERT done. Total IO: {t_total:.3f}s  "
        f"DB size: {db_mb:.1f} MB  RAM: {peak_ram_mb():.0f} MB", logf)


# ── Step 5: Write .blend manifest ─────────────────────────────────────────────
def write_blend_manifest(model, logf):
    log(f"HANDOFF — writing .blend manifest for {len(model):,} objects...", logf)
    t0 = time.perf_counter()

    manifest = [
        {
            'guid':          o['guid'],
            'geometry_hash': o['geometry_hash'],
            'transform': {
                'x': (o['minX'] + o['maxX']) / 2,
                'y': (o['minY'] + o['maxY']) / 2,
                'z': (o['minZ'] + o['maxZ']) / 2,
            }
        }
        for o in model
    ]
    with open(MANIFEST_OUT, 'w') as f:
        json.dump(manifest, f)

    elapsed = time.perf_counter() - t0
    manifest_kb = MANIFEST_OUT.stat().st_size / 1024
    log(f"HANDOFF — manifest written in {elapsed:.3f}s  "
        f"size: {manifest_kb:.0f} KB  RAM: {peak_ram_mb():.0f} MB", logf)


# ── Step 6: Copy mesh BLOBs (Scenario B — expected OOM) ─────────────────────
def insert_mesh_blobs(conn, seed, logf):
    hashes = list({row[2] for row in seed})  # unique geometry_hash values
    log(f"IO_TIME (B) — copying {len(hashes)} unique mesh BLOBs from library.db...", logf)
    t0 = time.perf_counter()

    lib = sqlite3.connect(str(LIBRARY_DB))
    inserted = 0
    crashed_at = None

    import numpy as np
    scale_ok = scale_bad = 0

    try:
        conn.execute("BEGIN")
        for i, h in enumerate(hashes):
            row = lib.execute(
                "SELECT vertices, faces, vertex_count, face_count "
                "FROM component_geometries WHERE geometry_hash = ?", (h,)
            ).fetchone()
            if not row:
                continue
            conn.execute(
                "INSERT OR IGNORE INTO base_geometries VALUES (?,?,?,?,?)",
                (h, row[0], row[1], row[2], row[3])
            )
            inserted += 1

            # S173: spot-check vertex scale (first 10 + every 500th)
            if row[0] and row[2] and row[2] > 0 and (inserted <= 10 or inserted % 500 == 0):
                verts = np.frombuffer(row[0], dtype=np.float32).reshape(-1, 3)
                vmax = np.abs(verts).max()
                if vmax > 500:  # likely mm, not metres
                    scale_bad += 1
                    if scale_bad <= 3:
                        log(f"  §MESH_SCALE WARNING hash={h[:12]} vmax={vmax:.1f} — "
                            f"looks like mm not metres", logf)
                else:
                    scale_ok += 1

            if inserted % 500 == 0:
                log(f"IO_TIME (B) — {inserted} BLOBs copied  "
                    f"RAM: {peak_ram_mb():.0f} MB", logf)

        conn.execute("COMMIT")
        elapsed = time.perf_counter() - t0
        db_mb = SANDBOX_DB.stat().st_size / (1024 * 1024)
        log(f"IO_TIME (B) — {inserted} BLOBs copied in {elapsed:.3f}s  "
            f"DB size: {db_mb:.1f} MB  RAM: {peak_ram_mb():.0f} MB", logf)
        log(f"  §MESH_SCALE summary: {scale_ok} OK (metres), {scale_bad} WARNING (mm?)", logf)

    except MemoryError as e:
        crashed_at = inserted
        log(f"CRASH — OOM at BLOB #{inserted}: {e}", logf)
    except Exception as e:
        crashed_at = inserted
        log(f"CRASH — Exception at BLOB #{inserted}: {e}", logf)
    finally:
        lib.close()

    if crashed_at:
        log(f"CRASH — threshold = {crashed_at} mesh BLOBs "
            f"(approx {crashed_at * 6.7:.0f} KB per BLOB avg)", logf)


# ── Scenario A ───────────────────────────────────────────────────────────────
def run_scenario_a():
    print("\n" + "="*60)
    print("SCENARIO A — Bbox only (1M elements_rtree rows)")
    print("="*60)
    with open(LOG_A, 'w') as logf:
        log(f"START Scenario A  target={TOTAL_OBJECTS:,} objects", logf)
        t_start = time.perf_counter()

        seed  = load_seed(logf)
        model = compile_model(seed, logf)
        conn  = create_sandbox(with_geom_table=False)
        insert_bbox_only(conn, model, logf)
        write_blend_manifest(model, logf)
        conn.close()

        total = time.perf_counter() - t_start
        log(f"DONE Scenario A — wall time {total:.3f}s  "
            f"peak RAM {peak_ram_mb():.0f} MB", logf)
        log(f"sandbox.db: {SANDBOX_DB.stat().st_size / (1024*1024):.1f} MB", logf)

    print(f"\nLog written to {LOG_A}")


# ── Scenario B ───────────────────────────────────────────────────────────────
def run_scenario_b():
    print("\n" + "="*60)
    print("SCENARIO B — Bbox + mesh BLOBs (expected OOM)")
    print("="*60)
    with open(LOG_B, 'w') as logf:
        log(f"START Scenario B  target={TOTAL_OBJECTS:,} objects + mesh BLOBs", logf)
        t_start = time.perf_counter()

        seed  = load_seed(logf)
        model = compile_model(seed, logf)
        conn  = create_sandbox(with_geom_table=True)
        insert_bbox_only(conn, model, logf)
        insert_mesh_blobs(conn, seed, logf)
        write_blend_manifest(model, logf)
        conn.close()

        total = time.perf_counter() - t_start
        log(f"DONE Scenario B — wall time {total:.3f}s  "
            f"peak RAM {peak_ram_mb():.0f} MB", logf)

    print(f"\nLog written to {LOG_B}")


# ── Main ─────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    scenario = sys.argv[1] if len(sys.argv) > 1 else "both"

    if not LIBRARY_DB.exists():
        print(f"ERROR: library.db not found at {LIBRARY_DB}")
        sys.exit(1)

    if scenario in ("A", "both"):
        run_scenario_a()

    if scenario in ("B", "both"):
        run_scenario_b()

    print("\nDone. Review stress_log_A.txt and stress_log_B.txt for stall points.")
