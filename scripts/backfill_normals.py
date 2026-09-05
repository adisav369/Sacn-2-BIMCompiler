#!/usr/bin/env python3
"""
Backfill normals BLOB into component_geometries.

Reads vertices + faces BLOBs, computes per-vertex normals (same algorithm as
Three.js computeVertexNormals), writes back as Float32 BLOB in the normals column.

Usage:
    python3 scripts/backfill_normals.py deploy/buildings/LTU_AHouse_library.db
    python3 scripts/backfill_normals.py deploy/buildings/Terminal_library.db

Safe: only UPDATEs rows where normals IS NULL. Re-runnable.
"""
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
import sys, struct, sqlite3, math, time

def compute_normals(vertices, faces):
    """Compute per-vertex normals from vertices (flat xyz) and face indices."""
    n = len(vertices) // 3
    normals = [0.0] * (n * 3)

    for i in range(0, len(faces), 3):
        ia, ib, ic = faces[i], faces[i+1], faces[i+2]
        if ia >= n or ib >= n or ic >= n:
            continue
        ax, ay, az = vertices[ia*3], vertices[ia*3+1], vertices[ia*3+2]
        bx, by, bz = vertices[ib*3], vertices[ib*3+1], vertices[ib*3+2]
        cx, cy, cz = vertices[ic*3], vertices[ic*3+1], vertices[ic*3+2]

        # edge vectors
        e1x, e1y, e1z = bx - ax, by - ay, bz - az
        e2x, e2y, e2z = cx - ax, cy - ay, cz - az

        # cross product (face normal, not normalized — area-weighted)
        nx = e1y * e2z - e1z * e2y
        ny = e1z * e2x - e1x * e2z
        nz = e1x * e2y - e1y * e2x

        # accumulate on each vertex of the face
        for idx in (ia, ib, ic):
            normals[idx*3]   += nx
            normals[idx*3+1] += ny
            normals[idx*3+2] += nz

    # normalize
    for i in range(n):
        x, y, z = normals[i*3], normals[i*3+1], normals[i*3+2]
        length = math.sqrt(x*x + y*y + z*z)
        if length > 0:
            normals[i*3]   = x / length
            normals[i*3+1] = y / length
            normals[i*3+2] = z / length

    return normals


def backfill(db_path):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Check normals column exists
    cols = [r[1] for r in cur.execute("PRAGMA table_info(component_geometries)")]
    if 'normals' not in cols:
        print(f"Adding normals column to {db_path}")
        cur.execute("ALTER TABLE component_geometries ADD COLUMN normals BLOB")
        conn.commit()

    # Count work
    total = cur.execute("SELECT COUNT(*) FROM component_geometries WHERE normals IS NULL").fetchone()[0]
    if total == 0:
        print(f"{db_path}: all {cur.execute('SELECT COUNT(*) FROM component_geometries').fetchone()[0]} rows already have normals. Nothing to do.")
        conn.close()
        return

    print(f"{db_path}: backfilling normals for {total} geometries...")
    t0 = time.time()
    done = 0
    errors = 0

    rows = cur.execute(
        "SELECT geometry_hash, vertices, faces FROM component_geometries WHERE normals IS NULL"
    ).fetchall()

    for ghash, vblob, fblob in rows:
        try:
            verts = list(struct.unpack(f'<{len(vblob)//4}f', vblob))
            faces = list(struct.unpack(f'<{len(fblob)//4}I', fblob))
            normals = compute_normals(verts, faces)
            nblob = struct.pack(f'<{len(normals)}f', *normals)
            cur.execute("UPDATE component_geometries SET normals = ? WHERE geometry_hash = ?",
                        (nblob, ghash))
            done += 1
        except Exception as e:
            errors += 1
            if errors <= 5:
                print(f"  WARN: {ghash} — {e}")

        if done % 5000 == 0 and done > 0:
            conn.commit()
            elapsed = time.time() - t0
            rate = done / elapsed
            print(f"  {done}/{total} ({rate:.0f}/s)")

    conn.commit()
    elapsed = time.time() - t0
    size_before = conn.execute("SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()").fetchone()[0]
    conn.execute("VACUUM")
    conn.commit()
    size_after = conn.execute("SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()").fetchone()[0]
    conn.close()

    print(f"  DONE: {done}/{total} normals computed, {errors} errors, {elapsed:.1f}s")
    print(f"  DB size: {size_before/1024/1024:.1f}MB -> {size_after/1024/1024:.1f}MB")


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/backfill_normals.py <library.db> [library2.db ...]")
        sys.exit(1)
    for path in sys.argv[1:]:
        backfill(path)
