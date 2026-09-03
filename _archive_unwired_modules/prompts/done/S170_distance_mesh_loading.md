# S170 — Distance-Based Mesh Loading + Discipline Lazy Load

## DO NOT REMOVE
Scope: Only load GN template meshes near the camera. Distant instances are
invisible (empty templates, zero GPU cost). Discipline toggle loads/unloads
templates per discipline. Target: 5-10K loaded templates at any time, not 109K.
Read the log after every run.

You are a coder. One bounded task.

---

## Context from S168/S169 (read these first)

- `prompts/S168_mesh_library_extractor.md` — library separation, extraction pipeline
- `prompts/S169_thin_blend_save.md` — save_pre strip, load_post restore

### What's done (S168+S169 session, 2026-04-10)

1. **Library separation** — mesh BLOBs in `component_library.db`, hashes in `_extracted.db`
2. **Local coords extraction** — `USE_WORLD_COORDS=False`, canonical mesh at IFC tack point
   - Tack point = IFC placement origin (door hinge, pipe connection, etc.)
   - **NEVER bbox-center** — see `memory/feedback_tack_point.md`
   - Up=Z, Forward=Y (IFC convention, verified across all element types)
3. **Per-instance rotation** — `element_transforms.rotation_x/y/z` from IFC placement matrix
4. **component_definitions** — UFB metadata (up_axis, forward_axis, attachment_face) written at extraction
5. **IFCtoBOM read-only** — ProductRegistrar writes to ERP.db only, not library
6. **blend_cache.py** — library-only mesh source (hard fail if missing), WAL mode + 60s timeout
7. **save_pre/save_post/load_post handlers** — coded in `__init__.py`, strip meshes on save, restore on open
8. **Site normalization** — georeferenced IFC files re-centered, offset stored for round-trip
9. **Logging** — `§` tagged extraction log for every claim

### What's NOT done (left for this prompt)

- Hospital extraction still running (17 GB RAM, 63K elements)
- Clean re-extraction of all 28 buildings (some hit library lock errors)
- Sandbox rebuild with local-coords data
- Distance-based mesh loading (this prompt)
- Discipline lazy loading
- Testing save_pre/save_post handlers with real data

---

## The Performance Problem

| Metric | Current (109K templates) | Target (5-10K loaded) |
|--------|------------------------|-----------------------|
| Template meshes in RAM | 109K (~5 GB) | 5-10K (~500 MB) |
| Depsgraph rebuild on toggle | 30s (re-evaluates all) | <2s |
| Initial load | 175s (bake all) | 30s (bake near camera) |
| Ctrl+S | ~15 MB (S169 strip) | ~15 MB (same) |

Root cause: 109K templates in `_GN_Templates` collection. Every depsgraph
operation (collection toggle, modifier eval) scales with template count.

---

## Architecture

### Two levels of LOD

**Level 1: Discipline lazy load** (toggle-driven)
- Only load templates referenced by VISIBLE disciplines
- Toggle ARC on → load ARC's ~5K templates
- Toggle MEP off → clear MEP's ~8K templates
- No timer needed, triggered by collection visibility change

**Level 2: Distance-based** (camera-driven)
- Within visible disciplines, only load templates near camera
- 50m radius default (configurable)
- Timer checks camera position every 0.5s
- Load/unload individual templates as camera moves

### How it works with GN

The GN node tree (`Instance on Points`) is unchanged. It references ALL
templates via `instance_index`. The trick: empty templates are invisible.

```
Template mesh has vertices  → GN renders instance at point position
Template mesh is empty      → GN skips (zero GPU cost, zero draw call)
```

So we control visibility by filling/clearing individual template meshes.
No GN modifier rebuild needed — just mesh data swap.

Key Blender APIs:
- `mesh.clear_geometry()` — instant, no depsgraph notification
- `mesh.from_pydata(verts, [], faces)` — instant per mesh
- `mesh.update()` — triggers GN re-eval for that template only
- `bpy.app.timers.register(fn, first_interval=0.5)` — periodic camera check

### Data flow

```
camera position
    ↓
R-tree spatial query (in-memory, from elements_rtree)
    ↓
set of nearby geometry_hashes
    ↓
compare with currently_loaded set
    ↓
load new:   fetch from component_library.db → from_pydata
unload far: mesh.clear_geometry()
```

### R-tree in memory

Load `elements_rtree` + `element_instances` once at startup:
```python
# ~1M entries, ~80 MB in Python
# element_guid → (cx, cy, cz, geometry_hash)
# Use scipy.spatial.cKDTree for fast radius query
```

Or simpler: group by geometry_hash, compute hash centroid, query hash
centroids instead of individual elements. 109K centroids instead of 1M.

---

## Implementation Plan

### Phase 1: Discipline lazy load (do first)

File: `blend_cache.py`

1. At cache creation, DON'T bake all templates. Instead:
   - Create empty template objects (mesh name + geometry_hash property)
   - Build GN trees as today
   - Only bake templates for ARC (default visible discipline)

2. Add `load_discipline(discipline)` / `unload_discipline(discipline)`:
   ```python
   def load_discipline(discipline):
       """Fetch and bake templates used by this discipline."""
       # Get geometry_hashes referenced by elements in this discipline
       hashes = get_hashes_for_discipline(discipline)
       fetch_and_bake(hashes)

   def unload_discipline(discipline):
       """Clear templates only used by this discipline."""
       hashes = get_exclusive_hashes(discipline)  # not shared with other visible
       for h in hashes:
           template_meshes[h].clear_geometry()
   ```

3. Hook into collection visibility change (or use depsgraph update handler)

### Phase 2: Distance-based (do second)

File: `blend_cache.py` + new `lod_manager.py`

1. Load R-tree data into memory at startup
2. Register timer: `bpy.app.timers.register(lod_update, first_interval=0.5)`
3. On each tick:
   - Get camera position
   - Query nearby hashes within radius
   - Load/unload delta

### Phase 3: Progressive quality

- Near camera (0-20m): full mesh
- Mid range (20-50m): simplified mesh (decimate)
- Far (>50m): invisible (empty template)

This requires pre-computed LOD levels in the library. Future work.

---

## Key Learning Points (from S168/S169 session)

1. **Tack point is sacred** — IFC local origin = BOM tack point. NEVER bbox-center.
   The vertices from `USE_WORLD_COORDS=False` are already at the tack point.

2. **Library = canonical shape, Project = placement** — WHAT vs WHERE.
   The 3-door problem (109K hashes from 30K actual shapes) was caused by
   baking world rotation into vertices. Local coords + per-instance rotation
   is the BOM way.

3. **component_library.db is the ONLY mesh source** — no fallback, no bypass.
   If hash not in library → hard fail. `blend_cache.py` enforces this.

4. **WAL mode on library** — enables concurrent reads (Blender viewport)
   while extraction writes. `PRAGMA journal_mode=WAL` + `timeout=60`.

5. **save_pre strips, load_post restores** — .blend is thin (~15 MB).
   Meshes re-baked from library on open. Template mesh custom property
   `mesh['geometry_hash']` survives save/load for restoration.

6. **Site normalization** — georeferenced IFC files (UTM/national grid)
   get re-centered. Offset stored in `site_normalization` table for
   round-trip IFC export.

7. **§ log lines prove claims** — extraction outputs `§EXTRACT`, `§DEDUP`,
   `§ROTATION`, `§TACK_POINT`, `§NORMALIZE`, `§LIBRARY` tags. No log = not done.

---

## Files to edit

- EDIT: `federation/blend_cache.py` — lazy discipline load, distance manager
- NEW:  `federation/lod_manager.py` — R-tree memory cache, camera distance query
- EDIT: `federation/__init__.py` — register timer, hook collection visibility
- READ: `federation/blend_cache.py` — understand current GN template creation flow
- READ: `prompts/S169_thin_blend_save.md` — save/restore handler architecture

## What's running (may be done by next session)

- Hospital extraction (63K elements, 215 MB IFC, ~17 GB RAM)
- After Hospital: clean re-extract all 28 buildings sequentially
- After re-extract: rebuild sandbox_1M.db
- Verify: unique hash count drop, rotation values non-zero

## DO NOT

- Bbox-center library meshes (EVER — feedback_tack_point.md)
- Write to component_library.db from IFCtoBOM (read-only consumer)
- Save mesh BLOBs in .blend files
- Bypass library with fallback to embedded BLOBs
- Use `collection.exclude` for toggling (causes full depsgraph rebuild)
  → Use `mesh.clear_geometry()` / `from_pydata()` for per-template control
