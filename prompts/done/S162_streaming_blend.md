# S162 — Streaming `.blend`: Scene Graph + DB Geometry Store

**Spec:** `docs/Enterprise.md` §"There Is More — The Streaming `.blend`" → TODO 1
**Live doc:** https://red1oon.github.io/BIMCompiler/Enterprise/
**Target repo:** `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`

You are a coder. One bounded task.

## PRIME RULE
Extract or compile only. No invention. Read the spec section in Enterprise.md before
writing any code. Cite it: `# Implementing Enterprise.md §"There Is More"`.

## What to build

A new module `federation/loading/streaming_blend.py` containing three persistent
handlers, plus registration in `federation/__init__.py`. No changes to any other file.

The behaviour:
- **`blend_save_pre`** — two jobs in one handler: (1) syncs viewport edits back to
  the DB via `guid` → `UPDATE elements_rtree`; (2) strips mesh data to stubs so the
  written `.blend` is scene graph only. Keeps in-memory cache so geometry is not lost.
- **`blend_save_post`** — restores mesh data from cache after save. Viewport unaffected.
- **`blend_load_post`** — on open, detects streaming stubs and rehydrates from DB.
  Falls back gracefully if DB missing. Skips objects that already have mesh data
  (legacy embedded `.blend` — leave as-is).

## Dual-DB geometry fetch (mandatory)

`load_post` must check both DBs — same `geometry_hash` key, same BLOB format:

```python
def _fetch_mesh_from_db(extracted_conn, library_conn, geometry_hash):
    # Extracted elements (walls, slabs, beams) → extracted.db → base_geometries
    row = extracted_conn.execute(
        "SELECT vertices, faces FROM base_geometries WHERE geometry_hash=?",
        (geometry_hash,)).fetchone()
    if not row and library_conn:
        # Generative elements (FRIDGE, SWITCH, TOILET…) → component_library.db
        row = library_conn.execute(
            "SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?",
            (geometry_hash,)).fetchone()
    return row  # None if not in either DB
```

Log the source:
```
[STREAMING] load_post — hash a1b2c3d4 found in extracted.db
[STREAMING] load_post — hash e5f6g7h8 found in component_library.db (generative)
[STREAMING] load_post — hash x9y0z1w2 NOT FOUND in either DB — stub mesh kept
```

For large unique-geometry projects (stadiums, airports): most hashes resolve from
`extracted.db`. For generative residential: most resolve from `component_library.db`.
Both are small SQLite files; the fetch logic is identical.

## Size-based auto-switch (mandatory)

Streaming activates only above a threshold. Small projects stay embedded as today.

```python
STREAMING_THRESHOLD = 50_000  # elements — configurable

def should_stream(db_path: str) -> bool:
    conn = sqlite3.connect(db_path)
    count = conn.execute("SELECT COUNT(*) FROM elements_meta").fetchone()[0]
    conn.close()
    return count >= STREAMING_THRESHOLD
```

Call `should_stream()` in `blend_load_post` before deciding whether to rehydrate
or leave embedded. Log the decision:

```
[STREAMING] load_post — 8450 elements < 50000 threshold, embedded mode (no-op)
[STREAMING] load_post — 125997 elements >= 50000 threshold, streaming mode
```

## Fallback contract (mandatory)

```python
# Streaming stub detection — one condition:
if obj.get('geometry_hash') and len(obj.data.vertices) == 0:
    # streaming mode — rehydrate from DB
else:
    # legacy embedded or non-federation object — skip, leave unchanged
```

Old `.blend` files with embedded LODs must open exactly as before.

## Logging — every step must log

Use `print()` with a `[STREAMING]` prefix so output is greppable.
Log at each stage:

```
[STREAMING] save_pre  — stripping N meshes (M unique hashes), .blend will be X KB
[STREAMING] save_post — restored N meshes from cache in Xs
[STREAMING] load_post — found N streaming objects, DB at <path>
[STREAMING] load_post — rehydrated N meshes (M unique hashes) in Xs
[STREAMING] load_post — FALLBACK: DB not found at <path>, N objects have stub meshes
[STREAMING] load_post — SKIP: object <name> already has mesh data (legacy .blend)
[STREAMING] load_post — SKIP: no geometry_hash, not a federation object
```

Timing must be logged: `import time; t0 = time.time(); ...; print(f"... in {time.time()-t0:.1f}s")`

## File: `federation/loading/streaming_blend.py`

```python
# Implementing Enterprise.md §"There Is More — The Streaming .blend"
# Streaming .blend: scene graph in .blend, geometry in DB.
# save_pre  strips mesh → thin .blend written
# save_post restores mesh → viewport intact
# load_post rehydrates mesh from DB on open

import time
import sqlite3
import struct
import bpy
from bpy.app.handlers import persistent
from pathlib import Path

# In-memory cache: populated by save_pre, consumed by save_post
_mesh_cache: dict[str, bpy.types.Mesh] = {}   # geometry_hash → Mesh


def _unpack_vertices(blob: bytes) -> list[tuple]:
    floats = struct.unpack(f'<{len(blob)//4}f', blob)
    return [(floats[i], floats[i+1], floats[i+2]) for i in range(0, len(floats), 3)]

def _unpack_faces(blob: bytes) -> list[tuple]:
    ints = struct.unpack(f'<{len(blob)//4}I', blob)
    return [(ints[i], ints[i+1], ints[i+2]) for i in range(0, len(ints), 3)]

def _fetch_mesh_from_db(conn, geometry_hash: str) -> bpy.types.Mesh | None:
    row = conn.execute(
        "SELECT vertices, faces FROM base_geometries WHERE geometry_hash = ?",
        (geometry_hash,)
    ).fetchone()
    if not row:
        return None
    verts = _unpack_vertices(row[0])
    faces = _unpack_faces(row[1])
    mesh = bpy.data.meshes.new(geometry_hash[:8])
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    return mesh

def _get_db_path() -> str | None:
    try:
        props = bpy.context.scene.BIMFederationProperties
        raw = props.federation_database_path
        return bpy.path.abspath(raw) if raw else None
    except Exception:
        return None


@persistent
def blend_save_pre(dummy):
    """Strip mesh data from federation objects before .blend is written."""
    global _mesh_cache
    _mesh_cache = {}
    t0 = time.time()

    federation_objs = [
        o for o in bpy.data.objects
        if o.get('geometry_hash') and o.data and hasattr(o.data, 'vertices')
    ]

    stripped = 0
    for obj in federation_objs:
        h = obj['geometry_hash']
        if len(obj.data.vertices) > 0:          # has real mesh — cache + strip
            _mesh_cache[h] = obj.data
            stub = bpy.data.meshes.new(f"stub_{h[:8]}")
            obj.data = stub
            stripped += 1
        # else: already a stub — nothing to strip

    unique = len(_mesh_cache)
    size_kb = sum(
        len(m.vertices) * 12 + len(m.polygons) * 12
        for m in _mesh_cache.values()
    ) // 1024

    print(f"[STREAMING] save_pre  — stripped {stripped} meshes "
          f"({unique} unique hashes, ~{size_kb} KB freed) in {time.time()-t0:.1f}s")


@persistent
def blend_save_post(dummy):
    """Restore mesh data from cache after .blend write completes."""
    global _mesh_cache
    if not _mesh_cache:
        return
    t0 = time.time()

    restored = 0
    for obj in bpy.data.objects:
        h = obj.get('geometry_hash')
        if h and h in _mesh_cache:
            obj.data = _mesh_cache[h]
            restored += 1

    print(f"[STREAMING] save_post — restored {restored} meshes "
          f"from cache in {time.time()-t0:.1f}s")
    _mesh_cache = {}


@persistent
def blend_load_post(dummy):
    """Rehydrate mesh data from DB for streaming stubs on .blend open."""
    t0 = time.time()

    streaming_objs = []
    legacy_count = 0
    skip_count = 0

    for obj in bpy.data.objects:
        h = obj.get('geometry_hash')
        if not h:
            skip_count += 1
            continue
        if not hasattr(obj.data, 'vertices'):
            skip_count += 1
            continue
        if len(obj.data.vertices) > 0:
            # Legacy embedded .blend — mesh already present
            legacy_count += 1
            print(f"[STREAMING] load_post — SKIP: {obj.name} already has mesh (legacy .blend)")
            continue
        streaming_objs.append(obj)

    if not streaming_objs:
        if legacy_count:
            print(f"[STREAMING] load_post — {legacy_count} legacy embedded objects, "
                  f"no streaming stubs found")
        return

    print(f"[STREAMING] load_post — found {len(streaming_objs)} streaming objects "
          f"({legacy_count} legacy skipped, {skip_count} non-federation skipped)")

    db_path = _get_db_path()
    if not db_path or not Path(db_path).exists():
        print(f"[STREAMING] load_post — FALLBACK: DB not found at '{db_path}', "
              f"{len(streaming_objs)} objects have stub meshes (geometry invisible)")
        return

    print(f"[STREAMING] load_post — DB at {db_path}")

    conn = sqlite3.connect(db_path)
    mesh_cache: dict[str, bpy.types.Mesh] = {}   # geometry_hash → Mesh
    rehydrated = 0
    missing = 0

    for obj in streaming_objs:
        h = obj['geometry_hash']
        if h not in mesh_cache:
            mesh = _fetch_mesh_from_db(conn, h)
            if mesh:
                mesh_cache[h] = mesh
            else:
                print(f"[STREAMING] load_post — WARN: hash {h[:12]}… not in DB")
                missing += 1
                continue
        obj.data = mesh_cache[h]
        rehydrated += 1

    conn.close()
    print(f"[STREAMING] load_post — rehydrated {rehydrated} meshes "
          f"({len(mesh_cache)} unique hashes"
          + (f", {missing} missing from DB" if missing else "")
          + f") in {time.time()-t0:.1f}s")
```

## Registration: add to `federation/__init__.py`

In `register()`, after the existing `load_post` registrations (line ~453):

```python
from .loading.streaming_blend import blend_save_pre, blend_save_post, blend_load_post

if blend_save_pre not in bpy.app.handlers.save_pre:
    bpy.app.handlers.save_pre.append(blend_save_pre)
if blend_save_post not in bpy.app.handlers.save_post:
    bpy.app.handlers.save_post.append(blend_save_post)
if blend_load_post not in bpy.app.handlers.load_post:
    bpy.app.handlers.load_post.append(blend_load_post)
```

In `unregister()`, after the existing removals (line ~475):

```python
from .loading.streaming_blend import blend_save_pre, blend_save_post, blend_load_post

if blend_save_pre in bpy.app.handlers.save_pre:
    bpy.app.handlers.save_pre.remove(blend_save_pre)
if blend_save_post in bpy.app.handlers.save_post:
    bpy.app.handlers.save_post.remove(blend_save_post)
if blend_load_post in bpy.app.handlers.load_post:
    bpy.app.handlers.load_post.remove(blend_load_post)
```

## Verify it works — read the Blender console

After implementation, open a federation `.blend` and check the console for:

```
[STREAMING] load_post — found N streaming objects ...
[STREAMING] load_post — rehydrated N meshes (M unique hashes) in Xs
```

Save the file, check for:
```
[STREAMING] save_pre  — stripped N meshes (M unique hashes, ~X KB freed) in Xs
[STREAMING] save_post — restored N meshes from cache in Xs
```

Verify file size on disk shrank. Reopen and verify geometry is still visible.

## Gate

- Open legacy embedded `.blend` → geometry visible, no `[STREAMING]` rehydration
  (legacy path untouched)
- Open streaming `.blend` → `[STREAMING] load_post — rehydrated N meshes` in console
- Save streaming `.blend` → file size < 10 MB for Hospital/LTU
- Re-open saved `.blend` → geometry still visible, same log output
- DB missing → `[STREAMING] load_post — FALLBACK` in console, no crash

## Files changed

- NEW: `federation/loading/streaming_blend.py`
- EDIT: `federation/__init__.py` (register/unregister only — ~6 lines added)
- NO other files touched
