# ⚠ DO NOT REMOVE
# Scope: S184 — RTree MESH speed: viewport filter + batch query + pre-warm
# Read the log after every run. No claims without §PROOF log lines.
# STATUS: SPEC — implement at session start.

## Context

RTree GPU path is production-ready (S183 DONE). GN Mode is **halted** —
the 8-minute GN evaluation overhead (500 GN modifier trees × Collection Info)
makes it unviable when RTree + Stingy Loader already works in 13s + 5s.
GN's only advantage (auto-mesh-on-halt) isn't worth the startup cost.

**This session:** make RTree's MESH button fast enough that GN isn't missed.
Target: **<2s** per MESH press (down from 5-15s).

---

## GN MODE — HALTED

Do NOT touch any GN/DLOD files. Do NOT fix GN geo hell or BLOB path.
Those are real bugs but GN mode itself is parked until RTree proves
whether <2s mesh is sufficient.

Files OFF LIMITS this session:
- `federation/dlod_handler.py`
- `federation/loading/stage2_library_linker.py` (GN path)
- `federation/lod_manager.py`
- `federation/blend_cache.py` (GN path)

---

## Root cause: why MESH takes 5-15s today

Two bottlenecks in `FedRTreeLoadMesh.execute()` (operator.py:1083-1302):

| Bottleneck | Cost | Line |
|------------|------|------|
| `bpy.data.libraries.load(lib_path)` re-parses 276MB library.blend | 2-5s | 1194 |
| 500 individual `SELECT ... WHERE t.guid = ?` in a for-loop | 5-10s | 1235-1244 |

The link operation itself is instant (~0.03s). The cost is Blender re-parsing
the .blend file index. And then 500 SQLite round-trips when 1 would do.

---

## Fix 1 — Viewport-Centre Filter (user sees only what camera sees)

### Why
Current MESH loads elements by **building envelope** — the whole building.
For a 40K-element hospital, 500 per press still means 80 presses.
The user only cares about what's **in front of the camera**.

### User experience
- Pan camera to where you want geometry → press +ARC → meshes appear **where you're looking**
- Pan somewhere else → press +ARC again → meshes appear there too
- No learning curve. Camera IS the selector. MESH loads what you see.
- With building active → viewport query AND building filter. No building → viewport only.
- Label: `Loaded_VP_{disc}_{offset}` (viewport-driven)

### Implementation
Replace `_query_building()` bbox with viewport-centre R-tree query:

```python
def _query_viewport(self, context, db_path, discipline, building=None,
                    limit=500, offset=0, storey=''):
    """Query elements visible from current viewport centre.
    Radius = min(view_distance * 1.2, 50m) — caps at 50m even at building orbit."""
    import sqlite3
    from . import bbox_visualization as bv

    r3d = context.space_data.region_3d
    off = bv._model_offset
    # Convert viewport centre back to IFC coordinate space
    cx = r3d.view_location.x + (off.x if off else 0.0)
    cy = r3d.view_location.y + (off.y if off else 0.0)
    radius = min(r3d.view_distance * 1.2, 50.0)

    clauses = ["r.minX <= ?", "r.maxX >= ?",
               "r.minY <= ?", "r.maxY >= ?",
               "m.discipline = ?"]
    params = [cx + radius, cx - radius,
              cy + radius, cy - radius,
              discipline]
    if building:
        clauses.append("m.building = ?")
        params.append(building)
    if storey:
        clauses.append("m.storey = ?")
        params.append(storey)
    params += [limit, offset]

    conn = sqlite3.connect(db_path)
    rows = conn.execute(f"""
        SELECT m.guid, i.geometry_hash, m.material_rgba
        FROM elements_meta m
        JOIN element_instances i ON m.guid = i.guid
        JOIN elements_rtree r ON m.id = r.id
        WHERE {' AND '.join(clauses)}
        LIMIT ? OFFSET ?
    """, params).fetchall()
    conn.close()
    return rows
```

### Call site change (operator.py ~1117-1127)
Replace `_query_building(db_path, active_bld, bbox, disc, offset, storey)`
with `_query_viewport(context, db_path, disc, active_bld, 500, offset, storey)`.
Same for `_query_building_no_bbox` calls — both replaced by `_query_viewport`.

The bbox from search results is no longer needed for MESH queries.
Keep `_query_building` / `_query_building_no_bbox` for now (search uses them).

Log: `§PROOF VP_QUERY disc={disc} radius={radius:.0f}m rows={len(rows)} building={building}`

---

## Fix 2 — Batch Transform Query (1 query, not 500)

### Why
The for-loop at operator.py:1227-1263 runs one `SELECT ... WHERE t.guid = ?`
per loaded mesh. For 500 meshes → 500 round-trips → 5-10s wasted.

### Fix
Fetch all transforms in one query BEFORE the object creation loop:

```python
# ── Batch-fetch transforms for all GUIDs in one query ──
all_guids = list(hash_to_guid.values())
conn = sqlite3.connect(db_path)
cur = conn.cursor()
placeholders = ','.join('?' * len(all_guids))
cur.execute(f"""
    SELECT t.guid,
           t.center_x, t.center_y, t.center_z,
           t.rotation_x, t.rotation_y, t.rotation_z,
           r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ
    FROM element_transforms t
    JOIN elements_meta m ON t.guid = m.guid
    JOIN elements_rtree r ON m.id = r.id
    WHERE t.guid IN ({placeholders})
""", all_guids)
transform_by_guid = {row[0]: row[1:] for row in cur.fetchall()}
conn.close()

# ── Create objects + place from lookup (no per-object SQL) ──
for mesh in loaded_meshes:
    ghash = mesh.name
    row_guid = hash_to_guid[ghash]
    obj = _bpy.data.objects.new(ghash, mesh)
    obj.hide_select = False
    col.objects.link(obj)
    tr = transform_by_guid.get(row_guid)
    if tr:
        cx, cy, cz, rx, ry, rz = tr[0], tr[1], tr[2], tr[3] or 0.0, tr[4] or 0.0, tr[5] or 0.0
        obj.location = (cx - ox, cy - oy, cz - oz)
        obj.rotation_euler = (rx, ry, rz)
        placed += 1
    else:
        no_transform += 1
```

Expected: 500 transforms fetched in <50ms (single indexed query) vs 5-10s today.

Log: `§PROOF BATCH_TRANSFORM guids={len(all_guids)} fetched={len(transform_by_guid)} ms={elapsed_ms}`

---

## Fix 3 — Pre-Warm on Drill-In (background library link)

### Why
`bpy.data.libraries.load()` re-parses 276MB library.blend every MESH press.
Takes 2-5s. But user reads cockpit for 2-3s after drill-in — dead time.

### Fix
When `FedRTreeCountBuilding` fires (cockpit drill-in), schedule a background
pre-warm 0.5s later via `bpy.app.timers.register`:

```python
def _prewarm_building_meshes():
    """Background-link geometry hashes for the drilled-in building.
    Fires 0.5s after cockpit drill-in. User reads discipline bars, never waits."""
    import bpy
    building = bv._active_building
    if not building or not bv._db_path_cache or not bv._library_blend_cache:
        return None
    import sqlite3
    conn = sqlite3.connect(bv._db_path_cache)
    hashes = [r[0] for r in conn.execute(
        "SELECT DISTINCT i.geometry_hash FROM elements_meta m "
        "JOIN element_instances i ON m.guid = i.guid "
        "WHERE m.building = ? AND i.geometry_hash IS NOT NULL", (building,)
    ).fetchall()]
    conn.close()
    already = {m.name for m in bpy.data.meshes}
    to_link = [h for h in hashes if h not in already]
    if to_link:
        with bpy.data.libraries.load(bv._library_blend_cache, link=True) as (df, dt):
            available = set(df.meshes)
            dt.meshes = [h for h in to_link if h in available]
        print(f"[S184] §PROOF PREWARM building={building} linked={len(to_link)} "
              f"total_hashes={len(hashes)}")
    return None  # one-shot, don't repeat
```

Register in `FedRTreeCountBuilding.execute()`:
```python
bpy.app.timers.register(_prewarm_building_meshes, first_interval=0.5)
```

Result: by the time user presses +ARC, `already_in_scene` catches everything →
line 1193 `to_link` is empty → skips `libraries.load()` entirely → 0s link cost.

Log: `§PROOF PREWARM building=X linked=N total_hashes=M`

---

## Expected performance after all three fixes

| Action | Before | After | Why |
|--------|--------|-------|-----|
| Library.blend parse | 2-5s | 0s | Pre-warmed during cockpit read |
| Transform queries | 5-10s | <0.1s | Single batch query |
| Object creation | <0.1s | <0.1s | Unchanged |
| **Total MESH press** | **5-15s** | **<1s** | |
| Second MESH press | 5-15s | <0.5s | Meshes + transforms all cached |

---

## Files to change

| File | Change |
|------|--------|
| `federation/operator.py` FedRTreeLoadMesh | Add `_query_viewport()`, batch transform query, viewport label |
| `federation/operator.py` FedRTreeCountBuilding | Register pre-warm timer on drill-in |
| `federation/bbox_visualization.py` | Add `_prewarm_building_meshes()` (lives near other cache globals) |

Read `FedRTreeLoadMesh.execute()` and `FedRTreeCountBuilding.execute()` fully before changing.

---

## Verification sequence

1. Run `bpy.ops.bim.fed_preview()` → RTree loads (must still be <13s, unchanged)
2. Search → click building → observe `§PROOF PREWARM` in log within 2s
3. Press +ARC → observe `§PROOF VP_QUERY` with radius + element count
4. Observe `§PROOF BATCH_TRANSFORM` with single-query timing
5. Observe `§PROOF LOAD_MESH elapsed=<2.0s` — **this is the target**
6. Pan camera elsewhere → press +ARC again → new viewport-local meshes appear
7. Verify placement accuracy (no geo hash hell, transforms match RTree bboxes)

---

## What NOT to change

- `bbox_visualization.py` RTree GPU path — untouched
- `component_library.db` — never write, never read BLOBs from here
- GN/DLOD files — halted, off limits (see top of this prompt)
- Search query helpers (`_query_building`, `_query_building_no_bbox`) — keep for search
- L2 single-element path — untouched (already fast, one element)
- Shred operator — untouched

---

## Standing rules

- Spec before code — this file is the spec
- Read the log after every run
- No `from_pydata()` at runtime — ever
- No BLOB reads from `component_library.db` at runtime — ever
- Camera IS the selector — MESH loads what the viewport shows, nothing more
