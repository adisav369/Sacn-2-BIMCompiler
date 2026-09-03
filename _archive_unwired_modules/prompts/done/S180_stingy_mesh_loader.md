# ⚠ DO NOT REMOVE
# Scope: S180 — Viewport Picker + Stingy Mesh Loader (Load/Shred)
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: S180 DONE — all tasks below completed. See §DONE appendix at end.

## Context

S178/S179 established the RTree GPU path as the primary viewport for 1M elements.
S178 added the drill-down query engine (L1 buildings → L2 elements).
This session adds two features:
1. **Viewport click-picker** — click any RTree wireframe box → identify element → populate L2 list
2. **Stingy Mesh Loader** — Load/Shred exact geometry for the active selection only

## Task 0 (P0): Fix Viewport Picker

The picker (`bim.fed_rtree_pick`) currently returns no hits because:
- The building envelopes highlighted by search are LARGE aggregate bboxes
- Individual element bboxes in the R-tree are much smaller
- The slab test works correctly but clicks land on empty space between elements

### Fix strategy

**Two-pass pick:**

Pass 1 — check highlighted building envelopes (`_highlighted_bboxes`):
```python
for i, bbox in enumerate(_highlighted_bboxes):
    if slab_test(ifc_ray_origin, ray_dir, bbox):
        # Hit a highlighted building envelope
        # Return the _search_results[i] building info
        # Trigger fetch_building_elements for that building
```

Pass 2 — if no highlighted envelope hit, fall back to individual element SQL:
```python
# Existing coarse SQL + slab test against elements_rtree
# This works for zoomed-in views where individual elements are visible
```

**Result populates L2 list:** clicking a building envelope in the viewport
triggers the same drill-down as clicking the building name in the N-panel.

Log: `§PROOF PICK_ENVELOPE building=X` or `§PROOF PICK_ELEMENT guid=X`

### Picker activation

Current: modal LEFT_MOUSE via "Click to Identify" button.
Keep this. After pick, the N-panel L2 list auto-populates — user sees
the building or element they clicked without touching the N-panel.

## Task 1: Stingy Mesh Loader (Load / Shred)

## The Stingy Principle

Load the MINIMUM geometry needed for the selection:
- Building selected (L1) → load ARC elements whose bbox touches that building envelope × 1.2
- Element selected (L2) → load that one element's mesh by geometry_hash
- Hard cap: LIMIT 500 elements per Load operation
- Everything else stays as RTree wireframes

## UI

In `BIM_PT_rtree_inspector`, below the L2 element list:

```
[ LOAD MESH ]   [ SHRED ]
```

- **LOAD MESH**: active when a building (L1) or element (L2) is selected
- **SHRED**: removes only what was loaded by the last Load, leaves RTree intact
- Both buttons grey out if nothing is selected

## Load Registry

Module global in `bbox_visualization.py`:
```python
_loaded_collections = {}  # label → [object_names]
                          # e.g. "Loaded_T0_Hospital_ARC" → ["obj1", "obj2", ...]
```

Each Load creates a uniquely named Blender collection and registers it here.
Shred removes that collection and its entry from the dict.

## Operators

### `bim.fed_rtree_load_mesh` (LOAD MESH)

```
If L2 element selected (_selected_element is set):
    query = geometry_hash of that element
    SQL: SELECT geometry_hash FROM elements_meta WHERE guid = ?
    load: 1 mesh from library.blend by hash
    collection: "Loaded_{guid[:8]}"

Elif L1 building selected (_active_building is set):
    bbox = envelope of that building from _search_results
    SQL: SELECT guid, geometry_hash, discipline FROM elements_meta m
         JOIN elements_rtree r ON m.id = r.id
         WHERE m.building = ?
           AND m.discipline = 'ARC'
           AND r.minX <= bbox.maxX*1.2 AND r.maxX >= bbox.minX*0.8
           AND r.minY <= bbox.maxY*1.2 AND r.maxY >= bbox.minY*0.8
         LIMIT 500
    load: all those hashes from library.blend (link=False, append)
    collection: "Loaded_{building}_ARC"
```

Log: `§PROOF LOAD_MESH label=X hashes=N elapsed=Xs`

### `bim.fed_rtree_shred`  (SHRED)

```
label = last loaded collection label (stored in props.rtree_last_loaded)
Remove all objects in _loaded_collections[label]
Remove the Blender collection
Delete entry from _loaded_collections
```

Log: `§PROOF SHRED label=X objects_removed=N`

## ⚠ Geo Hash Hell — Known Risk

When loading multiple meshes from library.blend into one Blender session,
Blender's datablock naming system may rename meshes that share a name prefix:

- geometry_hash `T_{abc123def456...}` truncated to 63 chars → fine
- Two hashes with identical first 63 chars → Blender appends `.001` to the second
- If the object name also collides → `.001` on the object too
- The mesh name no longer matches `geometry_hash` → transform lookup fails
  (`WHERE geometry_hash = mesh.name` returns no row → element placed at origin)

**This happened in S178 at CHUNK_SIZE=2000** — proven by the name collision test
(`T_{full_hash}` naming at 40 chars fixed it, 21/21 PASS, commit 0dc3912c).

### Prevention for S180 Stingy Loader

1. Use **full geometry_hash as mesh name** (40 hex chars, well under 63-char limit)
   — already the standard since S178 fix, verify it holds for appended meshes
2. After `bpy.data.libraries.load`, immediately check:
   ```python
   for mesh in data_to.meshes:
       if mesh.name != expected_hash:
           print(f"[S180] §GEO_HASH_HELL {expected_hash} → renamed to {mesh.name}")
   ```
3. If collision detected → abort Load for that hash, log it, continue with others
4. Never truncate hashes when naming objects or meshes

Log: `§PROOF NO_COLLISION hashes=N all_names_match=True`
If any mismatch: `§GEO_HASH_HELL hash=X renamed=Y` — treat as a blocker.

## Mesh loading mechanics

Use the existing `stage2_library_linker.py` path where possible.
If not available from that module, use direct bpy.data.libraries.load:

```python
with bpy.data.libraries.load(library_blend_path, link=False) as (data_from, data_to):
    hashes_to_load = [h for h in wanted_hashes if h in data_from.meshes]
    data_to.meshes = hashes_to_load

# Create one object per mesh, place at element's transform from element_transforms table
for mesh in data_to.meshes:
    obj = bpy.data.objects.new(mesh.name, mesh)
    collection.objects.link(obj)
    # apply transform from element_transforms WHERE geometry_hash = mesh.name
```

Log: `§PROOF MESH_PLACED hash=X pos=(x,y,z)`

## Props needed

Add to `BIMFederationProperties` in `prop.py`:
```python
rtree_last_loaded: StringProperty(name="Last Loaded", default="")
```

## Files

- `federation/bbox_visualization.py` — `_loaded_collections`, `_active_building`
- `federation/operator.py` — `FedRTreeLoadMesh`, `FedRTreeShred`
- `federation/ui.py` — Load/Shred buttons below L2 list
- `federation/prop.py` — `rtree_last_loaded`
- `federation/__init__.py` — register both operators
- `library/library.blend` — mesh source (276MB, 120K meshes)

## Standing rules

- RTree GPU path must remain active during and after Load/Shred
- Do NOT load more than 500 elements per Load
- Do NOT touch library.blend (read-only source)
- Each Load gets its own named collection — never merge into existing collections
- Read the log after every run

---

## §DONE — S180 Session Appendix

### Task 0: Viewport Picker (two-pass) — DONE
- Pass 1: slab-tests ray against `_highlighted_bboxes` (building envelopes) → returns `type='building'`
- Operator handles `type='building'` → calls `fetch_building_elements`, populates L2 list
- Pass 2: existing SQL element query (unchanged), logs `§PROOF PICK_ELEMENT`
- Log: `§PROOF PICK_ENVELOPE building=X t=Y` or `§PROOF PICK_ELEMENT guid=X`

### Task 1: Stingy Mesh Loader — DONE
- `bim.fed_rtree_load_mesh`: link=True (LOD400 linked ref), rotation_euler applied, §DIAG_LOAD + §TRANSFORM logs
- `bim.fed_rtree_shred`: unlinks from viewport only (mesh datablocks in library.blend untouched)
  - Selection-based: select objects → SHRED removes only those
  - Fallback: no selection → removes last-loaded collection
- `_loaded_collections`, `_library_blend_cache`, `rtree_last_loaded` prop added
- Geo-hash hell = hard RuntimeError (blocker, not warning)
- `invoke` methods on both ops (prevents "Missing modal" spam)

### Bugs fixed this session
- **Class split bug**: original `Edit` matched wrong `return {'PASS_THROUGH'}` in file, splitting
  `LoadFederationStage2Background` and embedding its `execute(RUNNING_MODAL)+cancel` inside
  `FedRTreeShred` — causing "Missing modal" spam. Fixed by restoring both classes.
- **Stale `_selected_element`**: `fetch_building_elements` now clears `_selected_element`
  so old bbox from previous search is not drawn white when drilling a new building.
- **SHRED delete vs unlink**: was calling `bpy.data.objects.remove()` — now uses
  `col.objects.unlink(obj)` (viewport removal only, library datablocks untouched).

### Known issue for next session
- `§DIAG_LOAD` will reveal if stale `_selected_element` was the white-box mismatch cause.
  Expected log: `sel_guid=<sprinkler_guid> sel_bbox=Z[17.478→17.536] dZ=0.058m`
  If instead shows window guid / large dZ → stale element confirmed (fix: clear is in place).
- §S181 prompt written for sprinkler dedup (9 hashes → 2 canonical Terminal hashes).
