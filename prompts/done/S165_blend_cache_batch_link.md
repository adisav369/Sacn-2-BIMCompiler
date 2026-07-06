# S165 — blend_cache.py: Geometry Nodes Instancing for 1M Element Scale

## ⚠ DO NOT REMOVE
Scope: Rewrite blend_cache.py object creation loop to use Geometry Nodes instances.
Read the log after every run. Read ALL of this prompt before writing code — the
"What we tried and failed" section will save you hours of dead ends.

**Target file:** `federation/blend_cache.py` (in Bonsai addon)
**Live path:** `/home/red1/.config/blender/5.0/extensions/.local/lib/python3.11/site-packages/bonsai/bim/module/federation/blend_cache.py`
**Spec:** `internal/StressTest_Community_Report.md`
**Micro-benchmark:** `scripts/stress_micro_benchmark.py`

You are a coder. One bounded task.

---

## What we tried and failed — DO NOT REPEAT these

### Attempt 1: Defer root_coll → scene link
Moved `context.scene.collection.children.link(root_coll)` to after the loop.
**Result:** No improvement. The `disc_coll.objects.link(obj)` call has overhead
even when the collection is disconnected from the scene.

### Attempt 2: Defer discipline_coll → root_coll link
Made discipline collections fully orphaned during the loop. Linked everything
at the end in one shot.
**Result:** Small improvement (~20% at 300K+), but same decay pattern. The
`objects.link()` call has per-object overhead regardless of scene connection.

### Attempt 3: Suppress report_fn viewport callback
Removed `report_fn()` calls during mesh bake loop to prevent UI refresh.
**Result:** Mesh bake rate improved from 136/s to 2000/s at peak, but
Blender's own draw timer (`Draw window and swap: 724ms`) still fires.
Total mesh bake time unchanged at ~290s.

### Attempt 4: Suppress viewport draws during mesh bake
Changed progress interval from 1K to 10K, used `wm.progress_begin/update/end`.
**Result:** Minor improvement. The 724ms draw comes from Blender's internal
timer, not our code. Cannot be suppressed without running in background mode.

### Definitive micro-benchmark (50K objects):
```
A: new() only    : 4.18s — 11,958 obj/s
B: new() + link(): 4.26s — 11,738 obj/s
```
**Conclusion:** `objects.link()` adds almost nothing. `bpy.data.objects.new()`
itself is the bottleneck. Any approach that calls `new()` 1M times will take
3.5 hours. The ONLY solution is to avoid creating 1M objects entirely.

---

## The problem — bpy.data.objects.new() is the wall

We stress-tested loading 1M real IFC elements into Blender. The full load
completed (1,013,288 objects, 12,446 seconds = 3.5 hours), but the rate
decays non-linearly past 150K objects.

**Micro-benchmark at 50K objects proved `objects.link()` is NOT the bottleneck:**

```
A: new() only    : 4.18s — 11,958 obj/s
B: new() + link(): 4.26s — 11,738 obj/s
```

The cost is in `bpy.data.objects.new()` itself — every object registers in
Blender's depsgraph, view layer, selection state, and outliner. There is no
batch API. This is architectural, not fixable by deferring or batching link calls.

**We also proved the DB is not the bottleneck:**

```
DB query for 1M elements:  2.26s
Grouping into disciplines: 2.27s
GPU vertex buffer build:   < 1s
Total data prep:           ~5s
```

---

## The solution — Geometry Nodes Instances

Geometry Nodes instances bypass `bpy.data.objects` entirely. Instead of
1M objects, create **one GN object per discipline** (~12 total). Each uses
"Instance on Points" to place geometry at positions from the DB.

| Approach | Objects in Blender | 1M load time | Selectable |
|----------|-------------------|-------------|------------|
| Current (real objects) | 1,000,000 | 3.5 hours | Blender click |
| **GN instances** | **~12** | **~5 seconds** | R-tree query |

Blender's depsgraph sees 12 objects. The viewport renders 1M instances
via GPU instancing — same visual result, zero per-object overhead.

**Community validated:** GN instances at 1M scale is a known working
pattern in the Blender community (archviz, particle simulations, forest
generators). Not experimental.

---

## Architecture for the BIM federation loader

### Dynamic switch based on element count

```python
THRESHOLD = 50_000  # configurable

def create_cache(context, db_path, ...):
    count = get_element_count(db_path)

    if count < THRESHOLD:
        # Current path — real objects, full editability
        create_cache_real_objects(context, db_path, ...)
    else:
        # GN path — instances, R-tree selection
        create_cache_gn_instances(context, db_path, ...)
```

Smaller projects (< 50K) use the current loader unchanged. Nothing breaks.

### Outliner hierarchy — discipline × building (not 1M flat entries)

The Outliner shows ~336 entries (12 disciplines × 28 buildings), not 1M:

```
Federation_Cached
  ├── ARC
  │   ├── ARC_LTU_AHouse      (45,231 instances)
  │   ├── ARC_Hospital         (22,108 instances)
  │   ├── ARC_Clinic           (8,340 instances)
  │   └── ...
  ├── STR
  │   ├── STR_LTU_AHouse       (12,450 instances)
  │   ├── STR_Hospital         (9,877 instances)
  │   └── ...
  ├── ACMV
  │   ├── ACMV_Hospital        (8,210 instances)
  │   └── ...
  ...
```

Grouping key: extract building name from guid prefix (`T0_LTU_AHouse_...`).
The DB already stores this — no schema change needed.

User can:
- Toggle entire discipline (hide all ARC)
- Toggle one building within a discipline (hide ARC_Hospital only)
- Search any element by name/guid → SQL instant → camera flies to it

### GN path — what to build

**Step 1 — Query DB (same as current, ~2s for 1M):**

```python
cursor.execute("""
    SELECT m.guid, m.discipline, m.ifc_class,
           bg.geometry_hash, bg.vertices, bg.faces,
           et.center_x, et.center_y, et.center_z
    FROM elements_meta m
    JOIN element_instances ei ON m.guid = ei.guid
    JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
    LEFT JOIN element_transforms et ON m.guid = et.guid
""")
```

**Step 2 — Build template meshes (same as current, ~5 min for 108K unique):**

```python
# One mesh per unique geometry_hash — identical to current mesh bake
meshes = {}
for geom_hash, verts_blob, faces_blob in unique_geoms:
    mesh = bpy.data.meshes.new(f"Mesh_{geom_hash[:8]}")
    mesh.from_pydata(unpack_vertices(verts_blob), [], unpack_faces(faces_blob))
    meshes[geom_hash] = mesh
```

**Step 3 — Create GN instance objects (NEW — replaces the 3.5 hour loop):**

For each discipline, create ONE object with a Geometry Nodes modifier:

```python
for discipline, elements in elements_by_discipline.items():
    # Build arrays
    positions = []      # (x, y, z) per element
    geom_indices = []   # index into template mesh list
    guids = []          # for R-tree identification

    for elem in elements:
        positions.append((elem.cx, elem.cy, elem.cz))
        geom_indices.append(mesh_index_map[elem.geometry_hash])
        guids.append(elem.guid)

    # Create point cloud with attributes
    point_cloud = bpy.data.pointclouds.new(f"PC_{discipline}")
    point_cloud.points.add(len(positions))
    # Set positions via foreach_set (fast, numpy-compatible)
    point_cloud.points.foreach_set("co", np.array(positions).flatten())

    # Add custom attributes
    attr_geom = point_cloud.attributes.new("geom_index", 'INT', 'POINT')
    attr_geom.data.foreach_set("value", np.array(geom_indices))

    # Create object
    obj = bpy.data.objects.new(f"Fed_{discipline}", point_cloud)
    # Add GN modifier: Instance on Points + index-based mesh lookup
    add_instance_on_points_modifier(obj, template_meshes)

    disc_coll.objects.link(obj)  # ONE link per discipline, not per element
```

**Step 4 — GN node tree:**

```
Points → Instance on Points → (Index lookup → template mesh) → output
```

The "Index lookup" uses a GN "Switch" or "Index" node to pick the correct
template mesh for each point based on the `geom_index` attribute.

**Step 5 — Selection via R-tree (unchanged):**

Click in viewport → get screen coordinate → convert to 3D ray →
query `elements_rtree` for nearest bbox → highlight via GPU overlay.
No Blender object selection needed.

---

## What stays the same

- Mesh bake (108K unique meshes) — identical, same time
- DB query — identical, same schema
- R-tree preview mode — unchanged
- Material assignment — per-discipline material on the GN object
- Collection hierarchy — Federation_Cached → discipline collections
- Click-to-highlight — R-tree query, 2s response

## What changes

- 1M `bpy.data.objects.new()` calls → **12 calls** (one per discipline)
- 1M `collection.objects.link()` calls → **12 calls**
- Individual object selection → R-tree spatial query
- Outliner shows 12 discipline objects, not 1M elements
- Per-element editing → Focus mode: realize instances for selected zone only

---

## Measured baselines (for before/after comparison)

**Before (current loader):**

```
Mesh bake:      108,121 meshes in 290s (370/s)
Object creation: 1,013,288 objects in 12,446s (81/s average, decaying)
Total:          ~3.5 hours
RAM:            ~28 GB at completion
Save:           OOM — killed
```

**After (GN instances) — targets:**

```
Mesh bake:      108,121 meshes in ~290s (unchanged)
GN setup:       12 discipline objects in ~5s
Total:          ~5 minutes
RAM:            ~4 GB (no per-object overhead)
Save:           Should succeed (~small .blend)
```

---

## Progress logging

```
[CACHE] DB query: 1,024,968 elements in 2.3s
[CACHE] Mesh bake: 108,121 unique meshes in 290s
[CACHE] GN setup: ARC — 127,268 instances (1 object)
[CACHE] GN setup: STR — 39,092 instances (1 object)
[CACHE] GN setup: ACMV — 21,876 instances (1 object)
...
[CACHE] GN setup: 12 disciplines in 4.8s — 0 bpy.data.objects overhead
[CACHE] Scene linked in 0.01s
[CACHE] TOTAL: 296s — RAM 3.8 GB
```

---

## Test plan

1. Run current loader on a small building (SH, 58 elements) — verify unchanged
2. Run current loader on LTU (126K) — verify threshold triggers GN path
3. Run GN path on sandbox_1M.db (1,024,968 elements) — measure total time
4. Verify R-tree click-to-highlight works at 1M
5. Verify .blend save succeeds at 1M
6. Compare file sizes: current .blend vs GN .blend

---

## Files changed

- EDIT: `federation/blend_cache.py` — add `create_cache_gn_instances()`,
  add threshold switch in `create_cache()`
- NEW: `federation/gn_instance_builder.py` (optional — could be inline)
- NO other files touched

## Debug log — mandatory output

Every run MUST produce a log file at `{db_folder}/gn_cache_log.txt` with
timing forensics. This is the before/after evidence.

### Log format

```
================================================================
GN CACHE BUILD LOG
================================================================
Date:     2026-04-11 14:23:05
Machine:  Intel i5-13500HX, 30 GB RAM, RTX 4060
Database: /home/red1/bim-compiler/scripts/sandbox_1M.db
Mode:     GN instances (element count 1,024,968 >= threshold 50,000)

[00:00.0] DB_QUERY      — 1,024,968 elements in 2.26s — RAM 620 MB
[00:02.3] GROUPING      — 13 disciplines, 28 buildings, 336 groups in 0.41s
[00:02.7] MESH_BAKE     — start (108,121 unique geometry hashes)
[00:12.7]   10,000/108,121 meshes (1,781/s) — RAM 1.2 GB
[00:22.7]   20,000/108,121 meshes (1,953/s) — RAM 1.8 GB
...
[04:52.7]  108,121/108,121 meshes — RAM 3.1 GB
[04:52.7] MESH_BAKE     — done in 290.0s (373/s avg)
[04:52.8] GN_SETUP      — creating 336 GN objects
[04:52.9]   ARC_LTU_AHouse: 45,231 instances — point cloud built in 0.03s
[04:53.0]   ARC_Hospital: 22,108 instances — point cloud built in 0.02s
...
[04:57.6] GN_SETUP      — 336 objects, 1,013,288 total instances in 4.8s
[04:57.6] SCENE_LINK    — linking 336 objects to scene in 0.01s — RAM 3.4 GB
[04:57.7] SAVE          — writing .blend cache...
[05:12.0] SAVE          — done in 14.3s — file size 245 MB
[05:12.0] DONE          — total 312s — peak RAM 3.4 GB

SUMMARY
  DB query:       2.3s
  Grouping:       0.4s
  Mesh bake:    290.0s  (108,121 unique meshes)
  GN setup:       4.8s  (336 objects, 1,013,288 instances)
  Scene link:     0.0s
  Save:          14.3s
  TOTAL:        312.0s  (5 min 12 sec)
  Peak RAM:     3.4 GB
  .blend size:  245 MB
  Outliner:     336 entries (12 disciplines × 28 buildings)

COMPARISON (same sandbox_1M.db, same machine)
  Before (real objects): 12,736s (3.5 hr), 28 GB RAM, save OOM
  After  (GN instances):   312s (5 min),  3.4 GB RAM, save OK
  Speedup: 40×
================================================================
```

### Timestamp format

Every line prefixed with `[MM:SS.S]` elapsed time from start.
This makes it trivial to identify stall points in any phase.

### LOG MANDATE

Read the log after every run. Exit code alone is not evidence.
If a phase takes longer than expected, the log shows exactly where.
If GN setup stalls, the per-group timing pinpoints which building/discipline.

---

## Risks

- GN node tree setup via Python API is verbose — research `bpy.types.GeometryNodeGroup`
- PointCloud type may differ between Blender 4.x and 5.x — test on 5.1.0
- Instance attributes (`geom_index`) need testing at 1M scale
- Realized instances (for Focus mode editing) reintroduce the per-object cost
  — only realize small zones (< 50K elements)
- Per-element material override (`material_rgba`) needs GN instance attributes
  instead of object-level material slots — discipline-level colouring works
  directly, per-element colouring is Phase 2
