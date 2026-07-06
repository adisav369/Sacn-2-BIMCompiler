# ⚠ DO NOT REMOVE — Read the log after every run

## S261: DLOD — Distance Level of Detail for 1M Elements

### Context
S260c achieved BatchedMesh with ~40 draw calls per flush, but progressive flush fragmentation
creates thousands of draw calls for large buildings (LTU 122K = ~2500 draw calls after flush
interval fix). Current DLOD (dlod.js) only does storey culling + frustum hide/show via
`setVisibleAt` — all geometry is fully loaded. This does NOT scale to 1M elements because:

- 1M geometry blobs = ~2GB+ of vertex data — won't fit in GPU memory
- Even with BatchedMesh, the GPU must process all vertices every frame
- Progressive flush fragmentation compounds the draw call problem

### The Fix: Geometry-Swap DLOD

r160 `BatchedMesh.setGeometryAt(slotId, geometryId)` swaps geometry per slot at zero cost.
Each slot starts as an 8-vertex bbox, swaps to full geometry when close.

**Three tiers:**

| Tier | Content | When | Cost |
|------|---------|------|------|
| LOD0 | 8-vert bbox from DB `bbox_x/y/z` columns | distance > 50m or off-screen | 8 verts/element |
| LOD2 | Full geometry blob from `component_geometries` | distance < 50m AND on-screen | 100-10K verts/element |

LOD1 (decimated mesh) is future — skip for now.

### Architecture

```
1M elements in DB
    ↓
Phase 0: Load positions.bin → instant bbox placeholders (existing, <1s)
Phase 1: Load meta.db → elements_meta + element_transforms + element_instances
    ↓
Build ONE BatchedMesh per storey|disc bucket
  - addGeometry(bboxGeo) → geoId_bbox (shared 8-vert box, scaled per element)
  - addGeometry(realGeo) → geoId_real (loaded on demand from geo.db)
  - setMatrixAt(slotId, matrix) — position from element_transforms
  - Start all slots as LOD0: setGeometryAt(slotId, geoId_bbox)
    ↓
Per-frame dlodTick:
  - Camera frustum + distance check per slot
  - Close + visible: if LOD0, fetch blob, addGeometry, setGeometryAt(slotId, geoId_real)
  - Far or hidden: setGeometryAt(slotId, geoId_bbox)
  - Budget: max 200 geometry swaps per frame (avoid stutter)
```

### Key Insight: ONE flush, not progressive

The progressive flush is what kills performance — 25+ flushes creating independent
BatchedMesh sets. For DLOD, we need ONE BatchedMesh per bucket with ALL elements.

**New streaming flow:**
1. Load meta.db (fast, ~5s for 1M) → build streamQueue with bbox dimensions
2. ONE flush: create BatchedMesh with ALL elements as LOD0 (bbox geometry)
   - Each bucket has ONE shared bboxGeo (scaled per element via matrix)
   - 1M elements → ~40 BatchedMesh → ~40 draw calls
   - Total GPU: 1M × 8 verts = 8M vertices (trivial)
3. Background: stream geo.db blobs on demand
   - Priority queue sorted by camera distance
   - dlodTick promotes close elements LOD0→LOD2
   - Demotes far elements LOD2→LOD0 (frees GPU memory)

### Implementation Plan

#### Step 1: Bbox-only BatchedMesh (replace progressive flush)
- In `_flushInstanced()`, create ONE BatchedMesh per storey|disc bucket
- All elements start as bbox geometry (8 verts, scaled by `bbox_x/y/z` via matrix)
- `A._bboxGeoId[bmId]` = geometry ID of the shared bbox in each BatchedMesh
- `A._slotGeoState[bmId][slotId]` = 'bbox' | 'real' (track current LOD)
- No progressive flush — ONE flush after meta.db loaded
- HUD should show "40 draw calls" immediately
- §-tag: `§DLOD_FLUSH buckets=N elements=N draw_calls=N all_bbox=true`

#### Step 2: On-demand geometry fetch
- `A._geoQueue` = priority queue of {bmId, slotId, hash, distance}
- Sorted by distance ascending (nearest first)
- `dlodTick` pops top N items, fetches from geo.db (or meshCache), calls:
  ```js
  var geoId = bm.addGeometry(realGeo);
  bm.setGeometryAt(slotId, geoId);
  A._slotGeoState[bmId][slotId] = 'real';
  ```
- Budget: 200 promotes per tick, 50 demotes per tick
- §-tag: `§DLOD_SWAP promote=N demote=N queue=N cached=N ms=T`

#### Step 3: Demotion (LOD2→LOD0)
- When element moves beyond 80m or out of frustum:
  ```js
  bm.setGeometryAt(slotId, A._bboxGeoId[bmId]);
  A._slotGeoState[bmId][slotId] = 'bbox';
  ```
- Hysteresis: promote at 50m, demote at 80m (prevents flicker at boundary)
- Demoted geometry stays in meshCache (RAM) but GPU buffer is freed

#### Step 4: Consolidation fix (replaces broken _consolidateBatched)
- Since there's only ONE flush, no consolidation needed
- Progressive flush removed entirely for DLOD buildings (>5K elements)
- Small buildings (<5K) keep current single-flush path (already fast)

### Whitebox Tests to Add

```
§WB_DLOD_BBOX: For each split building, verify bbox_x/y/z columns exist in element_transforms
§WB_DLOD_BUDGET: 1M elements × 8 verts = 8M verts. At 4 bytes × 3 (xyz) = 96MB. Must fit in GPU.
§WB_DLOD_SWAP: Verify setGeometryAt exists in THREE.BatchedMesh (r160 API check)
```

### Critical Constraints

1. **setGeometryAt capacity**: BatchedMesh has a max geometry count set at creation.
   Must pre-allocate enough slots for both bbox + real geometries.
   Strategy: `maxGeometryCount = elements.length + uniqueHashes.length`
   (elements for bbox slots, uniqueHashes for real geometry slots)

2. **Vertex buffer capacity**: `new BatchedMesh(count, maxVertexCount, maxIndexCount, mat)`
   Must estimate maxVertexCount for the bucket. Use bbox (8 verts each) + estimate
   real geometry budget (e.g., 500K verts per bucket = ~12K elements at avg 40 verts).
   If exceeded at runtime, skip promotion (element stays as bbox).

3. **Time Machine cooperation**: TM sets `_dlodPaused = true` — DLOD freezes swap state.
   TM controls visibility, DLOD controls geometry. Separate concerns.

4. **Pick still works**: `_batchMeta[bmId][slotId].guid` is set at flush time,
   same guid whether the slot shows bbox or real geometry. Raycast hits the
   currently-active geometry shape.

### Files to Modify
- `deploy/dev/streaming.js` — new `_flushBboxBatched()`, remove progressive flush for DLOD path
- `deploy/dev/dlod.js` — geometry swap logic, priority queue, promote/demote
- `deploy/dev/scene.js` — pre-allocate bbox BufferGeometry (shared)
- `deploy/dev/picking.js` — no change needed (guid lookup unchanged)
- `deploy/dev/tests/whitebox_regression.js` — add §WB_DLOD tests

### §-tags
- `§DLOD_FLUSH buckets=N elements=N draw_calls=N all_bbox=true`
- `§DLOD_SWAP promote=N demote=N queue=N cached=N ms=T`
- `§DLOD_BUDGET gpu_verts=N bbox_verts=N real_verts=N`
- `§DLOD_DEMOTE count=N freed_verts=N`

### Version Bump Protocol
1. Bump `?v=N` for streaming.js, dlod.js in index.html
2. Bump sw.js CACHE_VERSION
3. Match index.html sw.js?v=N

### Testing
1. LTU 122K — all elements as bbox in <3s, real geometry streams in over 30s, smooth orbit throughout
2. Terminal 48K — same pattern, HUD shows ~40 draw calls from start
3. SampleHouse 218 — no DLOD (below 5K threshold), current path unchanged
4. Pick works on bbox AND real geometry
5. Storey/disc filter works on both LOD states
6. Time Machine cinematic works (DLOD paused during playback)

### Success Criteria
- 1M elements: <5s to interactive (all bbox), <60s to fully loaded (close = real, far = bbox)
- HUD: ~40 draw calls throughout (never >100)
- GPU memory: <512MB for 1M elements (bbox = 96MB, real geometry budget-capped)
- Orbit: 60fps during and after streaming (no stutter, no frame drops)
