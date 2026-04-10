# S167 — Dual .blend Cache Modes: Full vs Light

## DO NOT REMOVE
Scope: Add Light cache mode — small .blend with GN point clouds only, meshes fetched
from DB on open via background bake. Read the log after every run.

**Target file:** `federation/blend_cache.py`
**Live path:** `/home/red1/.config/blender/5.0/extensions/.local/lib/python3.11/site-packages/bonsai/bim/module/federation/blend_cache.py`
**Depends on:** S165 (GN instancing) must be working first.

You are a coder. One bounded task.

---

## Problem

S165 solved the 3.5-hour object creation bottleneck with GN instances.
But saving the .blend with 108K embedded template meshes takes ~5 minutes
and produces a ~300 MB file. Re-saving is equally slow every time.

For sharing, git, or first-look workflows, users want a small portable file
that opens instantly and progressively loads meshes.

## Two cache modes

### Full mode (existing S165 behaviour)

- .blend contains: 108K template meshes + 12 GN point clouds + node tree
- Size: ~300 MB
- Open: instant (all meshes already embedded)
- Save: ~5 min (serializes all mesh datablocks)
- Re-save: ~5 min (same cost every time)
- Best for: daily work, open-and-go

### Light mode (NEW)

- .blend contains: 12 GN point clouds + node tree + DB path reference (NO meshes)
- Size: ~15-30 MB
- Open: instant R-tree bboxes, then background mesh bake fills in real geometry
- Save: < 1 second
- Re-save: < 1 second
- Best for: sharing, git, first look, laptops with limited storage

## Architecture for Light mode

### Save (create_cache_gn_light)

Same as S165 `create_cache_gn_instances` but:
1. Skip mesh bake entirely — do NOT create template meshes
2. Create GN point clouds (positions + instance_index) as normal
3. Store `db_path` in root collection custom property (already done)
4. Store `hash_to_index` mapping as JSON in a custom property or text datablock
5. Save .blend — tiny, just point clouds

### Open (load + progressive bake)

1. `load_from_cache()` loads the Light .blend — instant
2. Detect Light mode: template collection `_GN_Templates` is empty or missing
3. Start R-tree preview immediately (bboxes from DB) — user can navigate
4. Kick off background mesh bake via timer:
   ```python
   def _progressive_bake_tick():
       # Bake N meshes per tick (e.g., 1000)
       # Add to _GN_Templates collection
       # GN instances auto-update as templates appear
       # Return interval (0.1s) until all done, then None to stop
   ```
5. Viewport live-updates as meshes fill in — user sees geometry materialise

### Progressive bake detail

Use `bpy.app.timers.register()` to bake meshes in chunks without blocking the UI:

```python
CHUNK_SIZE = 500  # meshes per tick

def register_progressive_bake(context, db_path, tmpl_coll, hash_to_index):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT geometry_hash, vertices, faces FROM base_geometries ...")
    all_geoms = cursor.fetchall()

    state = {'index': 0, 'conn': conn}

    def bake_chunk():
        i = state['index']
        batch = all_geoms[i:i + CHUNK_SIZE]
        if not batch:
            state['conn'].close()
            print(f"[PROGRESSIVE] All {len(all_geoms)} meshes baked")
            return None  # stop timer

        for geom_hash, verts_blob, faces_blob in batch:
            mesh = bpy.data.meshes.new(f"T_{geom_hash[:8]}")
            mesh.from_pydata(unpack_vertices(verts_blob), [], unpack_faces(faces_blob))
            mesh.update()
            obj = bpy.data.objects.new(mesh.name, mesh)
            tmpl_coll.objects.link(obj)

        state['index'] += CHUNK_SIZE
        # Tag depsgraph for GN update
        for area in bpy.context.screen.areas:
            if area.type == 'VIEW_3D':
                area.tag_redraw()

        return 0.1  # next tick in 100ms

    bpy.app.timers.register(bake_chunk)
```

User experience:
```
t=0s     Open .blend — see R-tree bboxes (12 discipline colours)
t=2s     First 500 meshes appear (most common elements fill in first)
t=10s    5,000 meshes — walls, slabs, columns visible
t=60s    30,000 meshes — most structure visible
t=5min   108,000 meshes — full LOD, identical to Full mode
```

### Sort order optimisation

Bake the most-instanced geometry hashes first. This maximises visual coverage
early — the top 1000 geometries cover ~50% of all instances.

Query:
```sql
SELECT bg.geometry_hash, bg.vertices, bg.faces, COUNT(ei.guid) as instance_count
FROM base_geometries bg
JOIN element_instances ei ON bg.geometry_hash = ei.geometry_hash
WHERE bg.vertices IS NOT NULL
GROUP BY bg.geometry_hash
ORDER BY instance_count DESC
```

## UI integration

In the federation panel, cache mode selector:
```
Cache Mode: [Full ▼]  [Light ▼]
```

Or auto-select based on element count:
- < 50K: real objects (current, no GN)
- 50K-500K: Full GN (embedded meshes)
- > 500K: Light GN (progressive bake)

## What stays the same

- GN point clouds, node tree, instance_index attributes — identical
- R-tree preview mode — unchanged
- DB schema — unchanged
- Search (NLP query) — unchanged

## What changes

- `create_cache()` gets a `light=True` parameter (or mode="light")
- `load_from_cache()` detects Light mode and triggers progressive bake
- New function: `register_progressive_bake()`
- UI: cache mode selector in federation panel

## Test plan

1. Create Light cache from sandbox_1M.db — verify .blend < 30 MB
2. Reopen Light .blend — verify R-tree bboxes appear instantly
3. Watch progressive bake — verify meshes fill in over ~5 min
4. Verify GN instances auto-update as templates appear
5. Create Full cache — verify .blend ~300 MB, opens with all meshes
6. Compare: save Light .blend (< 1s) vs save Full .blend (~5 min)
7. Re-save Light after progressive bake completes — verify still small
   (meshes are runtime-only, not persisted)

## Files changed

- EDIT: `federation/blend_cache.py` — add Light mode path + progressive bake
- EDIT: `federation/operator.py` — wire up cache mode in load operator
- EDIT: `federation/ui.py` — add cache mode selector (if UI toggle wanted)
