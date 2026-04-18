# Direct DB Streaming — Camera-Driven Mesh from BLOBs

# ⚠ DO NOT REMOVE
# Scope: Direct Stream viewer — tessellate from DB BLOBs, no .blend files.
# Read the log after every run.

## Pipeline: IFC → Viewport

```
IFC files (multiple disciplines per building)
    ↓
extractIFCtoDB.py --library component_library.db
    ├─ Tessellation → vertices/faces BLOBs → component_library.db
    ├─ Hashing → geometry_hash (SHA256, deduplicated)
    ├─ Elements, transforms, rtree → {Building}_extracted.db
    └─ Surface styles, spatial structure → {Building}_extracted.db

Multiple {Building}_extracted.db files
    ↓
build_sandbox_1M.py
    ├─ Merge element metadata + transforms from 25+ buildings
    ├─ Tile suburbs 45× to reach 1M elements
    ├─ Deduplicate ~50K unique geometry hashes
    └─ Output: sandbox_1M.db (835MB, hash-only, no BLOBs)

Two DB files = complete viewer input
    ├─ sandbox_1M.db        (1M elements, transforms, rtree, surface_styles)
    └─ component_library.db (50K geometry BLOBs, 305MB)
    ↓
Ctrl+Shift+A  →  Direct Stream
    ├─ SQL query per tick → elements + transforms
    ├─ ensure_meshes() → BLOB → from_pydata() → Blender mesh
    ├─ Largest elements first (ORDER BY bbox volume DESC)
    ├─ Camera-aware: stream near, shred far
    └─ No .blend files in the pipeline
```

## Camera Position — CRITICAL, DO NOT CHANGE

- MUST use `_dlod_eye_pos` (`view_matrix.inverted().translation`) = actual camera eye
- DO NOT use `view_location` — that's the orbit pivot, can be underground (Z=-253 bug)
- Blender→IFC: ADD offset (`ifc = blender + offset`). DO NOT use minus.
- XY-only distance for "nearest building" — camera Z varies with orbit angle

## Architecture

```
_dlod_track_eye  (POST_VIEW draw handler, every frame)
    → updates _dlod_eye_pos = (x, y, z) from view_matrix inverse

_direct_stream_tick  (timer, 0.3-2s interval)
    → reads _dlod_eye_pos, converts to IFC coords
    → camera settle check (halt while user navigates, 1s cooldown)
    → XY distance to all building centres (STR/ARC bbox only)
    → detail phase: any building <50m with shell_done → stream all discs
    → shell phase: pick nearest candidate (in-sight + adjacency scoring)
    → fly-to: cinematic lerp (50-80%), lift if behind, no rotation change
    → SQL query: elements ORDER BY bbox volume DESC, LIMIT batch, OFFSET n
    → ensure_meshes() → tessellate missing hashes from component_library.db
    → place objects: from_pydata() + matrix_basis + material
    → per-batch collections (max 1000 objects each)
```

**State lives in `bbox_visualization.py`** (shared with HUD):
- `_direct_stream_enabled`, `_direct_stream_guids`, `_direct_stream_objects`
- `_direct_stream_buildings`, `_direct_stream_active_bld`, `_direct_stream_last_bld`
- `_direct_stream_disc_phase` (shell → shell_done → detail → done)
- `_direct_stream_cam_last`, `_direct_stream_cam_still_t` (settle detection)
- `_direct_stream_shred_blacklist` (last 3, prevents re-stream loop)
- `_direct_stream_bld_index` (Outliner numbering: 01_Building, recycled)

**Files:**
- `direct_stream.py` — tick logic, fly-to, candidate scoring, remove_building
- `mesh_utils.py` — ensure_meshes(), apply_material(), apply_transform()
- `operator.py` — _tessellate_from_blobs(), shred handlers, _load_surface_styles()
- `bbox_visualization.py` — state variables, draw handler, get_model_offset()
- `progress_hud.py` — GPU overlay (disc bars, status, settle/streaming/done)

**Log:** `~/Documents/bonsai/consolelogs/direct_stream.log` (fresh each toggle ON)

## Streaming Behaviour

**Building selection (scored, lower = better):**
- 30% camera XY distance + 70% adjacency to last building
- Forward direction bonus (−50 × dot product)
- Skip: shred blacklist (last 3), already done, out of 300m radius

**Camera:**
- First building: jump + isometric rotation
- Subsequent: lerp 50-80% toward target, no rotation change
- Behind camera: lift Z by 50% of building height
- Settle: halt streaming while user navigates, resume 1s after stop

**Phases per building:**
1. `shell` — ARC+STR only, locked (finish before switching)
2. `shell_done` — release lock, pick next building
3. `detail` — all remaining disciplines (when <50m)
4. `done` — fully streamed

**HUD status cascade:**
- PAUSED — CAM MOVE (user navigating)
- SETTLING... (< 1s since camera stopped)
- RESUMING {building} (< 2s after settle)
- STREAMING {building} {done}/{total}
- DONE {total} (all in radius finished)
- PAN CAM TO STREAM (idle, pulsing amber)
- LAG / BUDGET (overload states)

## Learning Points

1. **`view_location` ≠ camera eye** — orbit pivot can be underground. Always use `view_matrix.inverted().translation`.
2. **XY-only distance** — camera Z varies wildly with orbit angle. Buildings are on the ground plane.
3. **Blender→IFC = ADD offset** — `ifc = blender + offset`. The minus sign was a recurring bug that mirrored camera position.
4. **`col.objects.link()` is O(n)** — per-batch collections (max 1000) keep tick time constant.
5. **`orphans_purge()` hangs** — removed from all paths. `batch_remove()` is fast.
6. **No blocking pre-tessellation** — incremental per tick. Largest elements first = shell in seconds.
7. **Phase offsets per building+phase** — shell and detail use different SQL WHERE clauses; same offset counter breaks pagination.
8. **`from_pydata()` cost is ~0.3ms/mesh** — 50K unique hashes = 15s total, but spread across ticks = invisible.

## Historical Note

S189-S193 explored a .blend-based pipeline: bake .blend files from BLOBs → save to disk →
link into Blender session → DLOD auto-link/unlink by camera distance. This worked but had
fundamental limits: OOM on bulk link, 300MB library.blend read time, save-post complexity,
GN modifier evaluation overhead (8 min for 500 trees). S195 replaced it entirely with
Direct DB Streaming — `from_pydata()` directly from SQLite BLOBs, no .blend files at all.
The bake/link code remains in operator.py but is unused by the current viewer path.

## Next (S198)

1. Pick on DirectStream objects — ray-cast against streamed meshes
2. Detail phase auto-resume — fly inside shell_done building → detail streams
3. Auto-cache .blend after stream — see S192d in S192_cloud_deploy_onboard.md
4. Three-tier DB resolution — see S192b (project/ → City/ → OCI)
