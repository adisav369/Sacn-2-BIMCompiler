# ⚠ DO NOT REMOVE
# Scope: S189 — BLOB tessellation + chunk-parallel bake + live-link UX
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: IN PROGRESS — Preview baked-link not firing + Outliner hierarchy fix

## Context

S188 proved the bottleneck chain:
- library.blend (305MB) is opened per batch → 1-2s hitch in Overnight
- SHORT-CUT bake appends from library.blend → 125s for Hospital 22800 meshes
- Link-back of 44MB fat .blend → **17min freeze** (blocking `libraries.load`)
- `blend_cache.py` Full Load already tessellates from DB BLOBs — zero hitches

Part F spec: `prompts/S188_rtree_ux_performance.md` §Part F

## What to implement

### 1. `scripts/blob_tessellate_worker.py` (NEW)

Per-chunk BLOB tessellation subprocess. Replaces `bake_building_blend.py` for
the non-library path.

```
blender --background --factory-startup --python blob_tessellate_worker.py -- \
    --db extracted.db \
    --library-db component_library.db \
    --building Hospital \
    --offset 0 --limit 31500 \
    --output baked/Hospital_chunk0.blend
```

Core loop (port from `blend_cache.py` `unpack_vertices`/`unpack_faces`):
```python
for guid, ghash, rgba, mat_name, ... in elements[offset:limit]:
    blob = lib_db.execute("SELECT vertices, faces FROM component_geometries WHERE geometry_hash=?", (ghash,))
    if ghash not in mesh_cache:
        mesh = bpy.data.meshes.new(ghash)
        mesh.from_pydata(unpack_verts(blob[0]), [], unpack_faces(blob[1]))
        mesh_cache[ghash] = mesh
    obj = bpy.data.objects.new(name, mesh_cache[ghash])
    # Apply material (surface_styles + BSDF, void filter, same as S188b)
    # Apply transform (rotation, redirect correction)
```

Output: fat .blend chunk (~10MB for 31K elements). Self-contained.

### 2. Overnight modal: in-process BLOB tessellation

Replace `libraries.load(lib_path, link=False)` in Overnight's modal loop with
direct BLOB reads from `component_library.db`. Same modal timer pattern (50
elements per tick, yield to viewport). Zero hitches.

### 3. LOAD MESH: same BLOB path

Replace `libraries.load` in `_query_viewport` / `_query_single` handler with
BLOB tessellation. Same per-click behaviour, no library.blend dependency.

### 4. Chunk-parallel spawn

When building >50K elements, `_spawn_blob_bake()` splits into 4 equal chunks,
spawns 4 `blob_tessellate_worker.py` subprocesses. `_poll_bake_subprocess()`
appends each chunk .blend on completion (~3s each, no freeze).

## Benchmarks to prove

| Metric | Before (S188) | After (S189) | Proof tag |
|--------|--------------|-------------|-----------|
| Overnight per-batch | 1-2s hitch | <50ms | §BLOB_BATCH |
| Hospital bake total | 160s | ~40s | §BLOB_COMPLETE |
| LTU 126K total (4 workers) | N/A | ~60s | §BLOB_ALL_DONE |
| Link-back per chunk | N/A | <3s | §BLOB_APPEND |
| Hospital link-back | 17min | 4×3s=12s | §BLOB_APPEND |
| Reopen fat .blend | <3s | <3s (unchanged) | §PROOF OPEN |

## Standing rules

- Read `prompts/S188_rtree_ux_performance.md` §Part F for full spec
- Read `blend_cache.py` `unpack_vertices`/`unpack_faces` — port, don't reinvent
- Void filter: `ifc_class != 'IfcOpeningElement'` in all queries
- Materials: surface_styles + Principled BSDF (S188a pattern)
- Bump `_FED_VERSION` on every code change
- §PROOF log lines for all benchmarks

## Source files

- `federation/loading/stage2_tessellation_loader.py` — `unpack_vertices`, `unpack_faces` (port from here)
- `federation/blend_cache.py` — Full Load BLOB tessellation reference
- `federation/operator.py` — Overnight modal, LOAD MESH, `_poll_bake_subprocess`, `_live_link_baked`
- `scripts/bake_building_blend.py` — current library.blend bake (fallback)
- `library/component_library.db` — BLOB source (123,573 meshes)
- `docs/PackageDistro.md` — live-link architecture spec

## Open issues (S189 session end)

### 1. Preview baked-link not firing
The linking code in `PreviewFederationViewport.execute()` (line ~3370) scans `baked/`
dir and links `.blend` files. Logs show NO `§PREVIEW_LINK` entries — the code block
is not reached or the `_baked_dir` path resolves wrong. Debug:
- Add `print(f"§DEBUG baked_dir={_baked_dir} exists={_baked_dir.exists()}")` before the if
- Check `bv._db_path_cache` is set before the link block runs
- The DB path may be relative (`//...`) — needs `bpy.path.abspath()` + `.resolve()`
- The `baked/` dir is at project root, not relative to the DB

### 2. Outliner hierarchy — disc collections loose under Scene
Overnight/LOAD MESH creates `Loaded_{building}_{DISC}` collections directly under
Scene Collection. They should be grouped:
```
Scene Collection
  └── T0_Hospital          ← parent (missing)
       ├── T0_Hospital_ARC
       ├── T0_Hospital_STR
       └── T0_Hospital_MEP
```
Currently:
```
Scene Collection
  ├── T0_Hospital_ARC      ← loose, no parent
  ├── T0_Hospital_STR
  └── T0_Hospital_MEP
```
Fix: in Overnight and LOAD MESH, create a parent `Loaded_{building}` collection
if it doesn't exist, and nest disc collections under it. Same as bake script does
with `root_col = bpy.data.collections.new(building)`.

### 3. Baked files also need building parent in Outliner
The baked `.blend` files already have `{building}` root collection with disc children.
When linked into session, the collections should appear nested, not flat. Verify
`link=True` preserves the hierarchy from the baked file.
