# ⚠ DO NOT REMOVE — Scope: Browser LOD for BIM viewer. Read the log after every run.

# S241 — Browser LOD: Camera-Distance Geometry Switching

## Why This Matters
Current state: 23,888 unique geometry hashes, avg 222 vertices, max 79,003 vertices.
Pan lag with 125K elements at full view = GPU fill-rate bottleneck.
LOD cuts rendered triangles by 60-80% at medium/far distance with zero visible quality loss.

---

## §1 Research: How LOD Is Done in Production

### Three.js native: `THREE.LOD`
```js
const lod = new THREE.LOD();
lod.addLevel(highDetailMesh, 0);    // < 50m
lod.addLevel(lowDetailMesh,  50);   // 50–200m
lod.addLevel(hullMesh,       200);  // > 200m
scene.add(lod);
lod.update(camera); // called in animate loop
```
Distance is camera-to-object. Switches geometry automatically. Zero CPU per frame after setup.

### Cesium 3D Tiles (city-scale BIM)
Pre-baked tile hierarchy. Each tile has a geometric error budget.
Loader fetches coarser tiles first, refines as camera approaches.
**Lesson for us:** LOD data must be pre-baked, not computed at runtime.

### Unreal/Unity LOD Groups
Content pipeline generates LOD1/LOD2/LOD3 meshes at export.
Runtime: distance threshold + hysteresis band to avoid pop.
**Lesson for us:** Java DAGCompiler is the right place to generate LOD blobs.

### IFC viewers (xeokit, BIMviewer)
xeokit uses "geometry batching" — merge by material at load, not per-element meshes.
At far distance: discipline-merged geometry (1 draw call per discipline).
**Lesson for us:** Merged geometry per discipline per floor = free LOD0.

---

## §2 Our LOD Strategy: 3-Level

| Level | Distance       | What renders                        | Draw calls |
|-------|----------------|-------------------------------------|------------|
| LOD2  | < 60m (close)  | Full geometry — current state       | ~N hashes  |
| LOD1  | 60–250m (mid)  | Simplified geometry (40% verts)     | ~N hashes  |
| LOD0  | > 250m (far)   | Convex hull per element             | ~N hashes  |
| DISC  | > 500m (city)  | One merged mesh per discipline/floor | ~10        |

Distances are relative to building bounding sphere radius — scale automatically.

---

## §3 Data Model: LOD Blobs in DB

Add LOD columns to `component_geometries` in library DB:

```sql
ALTER TABLE component_geometries ADD COLUMN vertices_lod1 BLOB;  -- 40% verts
ALTER TABLE component_geometries ADD COLUMN faces_lod1    BLOB;
ALTER TABLE component_geometries ADD COLUMN vertices_lod0 BLOB;  -- convex hull
ALTER TABLE component_geometries ADD COLUMN faces_lod0    BLOB;
ALTER TABLE component_geometries ADD COLUMN vertex_count_lod1 INTEGER;
ALTER TABLE component_geometries ADD COLUMN vertex_count_lod0 INTEGER;
```

**Population strategy (Java DAGCompiler):**
- LOD1: vertex decimation using quadric error metric (fast, ships with JMonkeyEngine / custom)
  Target: reduce to 40% of vertices, preserve silhouette
- LOD0: convex hull of original vertices (Apache Commons Math `ConvexHull3D`)
  Result: 6–30 faces regardless of original complexity

**Migration:** append-only SQL migration file. NULL = no LOD available → fall back to full geo.

---

## §4 Browser Streaming: LOD-Aware Fetch

Current fetch in `streaming.js` (line ~176):
```js
stmt = libDb.prepare(`SELECT geometry_hash, vertices, faces FROM component_geometries WHERE geometry_hash = ?`);
```

New fetch — request all LOD levels at once:
```js
stmt = libDb.prepare(`
  SELECT geometry_hash, vertices, faces,
         vertices_lod1, faces_lod1,
         vertices_lod0, faces_lod0
  FROM component_geometries WHERE geometry_hash = ?
`);
```

Store all three geometries in mesh `userData`:
```js
mesh.userData.geo     = geoLOD2;  // full
mesh.userData.geoLOD1 = geoLOD1;  // simplified (null if not in DB)
mesh.userData.geoLOD0 = geoLOD0;  // hull (null if not in DB)
mesh.userData.currentLOD = 2;
```

---

## §5 Runtime LOD Switching

In `animate()` loop, after controls.update():
```js
if (_needsRender) {
  updateLOD(APP);
}
```

```js
function updateLOD(A) {
  const camPos = A.camera.position;
  const bldRadius = A.activeBuildingRadius || 100; // set at stream time
  const D1 = bldRadius * 0.6;   // ~60m for typical building
  const D2 = bldRadius * 2.5;   // ~250m

  A.scene.traverse(obj => {
    if (!obj.isMesh || !obj.userData.geo) return;
    const dist = camPos.distanceTo(obj.position);
    let targetLOD = 2;
    if (dist > D2 && obj.userData.geoLOD0) targetLOD = 0;
    else if (dist > D1 && obj.userData.geoLOD1) targetLOD = 1;

    if (obj.userData.currentLOD !== targetLOD) {
      obj.geometry = targetLOD === 0 ? obj.userData.geoLOD0
                   : targetLOD === 1 ? obj.userData.geoLOD1
                   : obj.userData.geo;
      obj.userData.currentLOD = targetLOD;
    }
  });
}
```

**For InstancedMesh:** LOD is per-hash, not per-instance. Swap the entire InstancedMesh geometry:
```js
iMesh.geometry = targetGeo;
iMesh.instanceMatrix.needsUpdate = true;
```

---

## §6 DISC-Level Merge (LOD city view, >500m)

When camera pulls back far enough to see multiple buildings:
- Pre-merge all meshes per discipline on first trigger
- Swap to merged single-material meshes (10 draw calls total)
- On zoom-in: swap back to per-element LOD2

This is the same technique xeokit uses for city-scale. Already partially implemented in
`streaming.js` for mobile (`mergedCount`). Extend for distance-triggered desktop.

---

## §7 Geometry Decimation in Java (DAGCompiler)

**Algorithm: Quadric Error Metric (QEM)**
- Industry standard for mesh simplification (Garland & Heckbert 1997)
- Preserves silhouette, handles non-manifold geometry (common in IFC)
- Target 40% vertex retention for LOD1

**Java libraries available:**
- `jgeom` (pure Java, includes QEM simplifier)
- Manual QEM implementation (~200 lines) — preferred for zero dependencies

**Convex hull for LOD0:**
- Apache Commons Math 3: `ConvexHullGenerator3D` — already in Maven ecosystem

**Where to add:** `IFCtoBOM.java` post-tessellation step, or a new `LODGenerator.java`
called from `BuildingCompiler.java` after geometry extraction.

**Output:** populate `vertices_lod1/faces_lod1/vertices_lod0/faces_lod0` same BLOB format
as existing `vertices/faces` — Float32 XYZ + Uint32 triangle indices.

---

## §8 Performance Projection

| Scenario | Current draw calls | Current triangles | With LOD (far) |
|----------|-------------------|-------------------|----------------|
| LTU 125K el, full view | ~3,000 | ~27M | ~3,000 / ~11M (60% less) |
| LTU 125K el, city view | ~3,000 | ~27M | ~10 (DISC merge) |

Expected pan improvement at mid/far distance: 2–4× fps gain.
Close-up (< 60m): zero change — full LOD2 always.

---

## §9 Implementation Plan

**Phase 1 — DB schema + Java LOD generator**
- Migration: add LOD columns to `component_geometries`
- `LODGenerator.java`: QEM LOD1 + convex hull LOD0
- Run on existing library — populate LOD blobs
- Witness: §LOD_GENERATED count=N lod1_avg_verts=X lod0_avg_verts=Y

**Phase 2 — Browser fetch LOD blobs**
- Update `streaming.js` SELECT to fetch all LOD levels
- Store in mesh `userData`
- Witness: §LOD_LOADED lod1=N lod0=M (N/M can be 0 if DB not updated yet)

**Phase 3 — Runtime switching**
- Add `updateLOD()` to animate loop
- Set `activeBuildingRadius` at stream time from bounding sphere
- Witness: §LOD_SWITCH dist=X level=Y (log every switch, throttled)

**Phase 4 — InstancedMesh LOD**
- Per-hash geometry swap for InstancedMesh
- Witness: §LOD_INSTANCED hash=X level=Y count=Z

**Phase 5 — DISC city merge**
- Distance trigger (>500m or >3 buildings visible)
- Discipline-merged single geometry
- Witness: §LOD_DISC_MERGE disc=ARC merged_verts=N draw_calls=1

---

## §10 Files to Edit

| File | Change |
|------|--------|
| `migration/` | Add LOD columns to component_geometries |
| `DAGCompiler/.../LODGenerator.java` | New class: QEM + convex hull |
| `DAGCompiler/.../BuildingCompiler.java` | Call LODGenerator post-tessellation |
| `deploy/dev/streaming.js` | Fetch LOD blobs, store in userData |
| `deploy/dev/scene.js` | Add `updateLOD()`, call in animate |
| `deploy/dev/helpers.js` | `blobToGeometry()` reuse for LOD blobs |

---

## §11 Witnesses

- W-LOD_GENERATED: LOD blobs populated in library DB, counts logged
- W-LOD_LOADED: browser logs LOD1/LOD0 availability per hash
- W-LOD_SWITCH: distance-triggered switches logged in console during pan
- W-LOD_VISUAL: no pop visible at switch threshold (subjective, screenshot proof)
- W-LOD_PERF: §FPS log shows improvement at far distance

---

## §12 Out of Scope

- LOD for 2D plans (separate pipeline)
- Progressive streaming by LOD level (future: stream LOD0 first, refine on approach)
- Mobile-specific LOD thresholds (handle in config.js later)
