# ⚠ DO NOT REMOVE — Read the log after every run

## S259: BatchedMesh — 83K → ~200 Draw Calls + DLOD Foundation

### Context
S258 upgraded Three.js to r156 + BVH v0.7.8. LTU (122K elements) still lags:
- `§FLUSH instanced=52172 single=70158 drawCalls=83537` — 83K draw calls
- DLOD disabled (wrong-angle onset, hourglass issues)
- GPU bottleneck is draw calls, not triangle count
- Shadow disabled globally (S259 tuning), NeutralToneMapping + SRGBColorSpace applied
- Night mode + lighting sliders deployed in Sunglass panel (sw v331)

### Root cause
`_flushInstanced()` creates one Mesh per unique geometry hash with 1 instance.
70K single meshes = 70K draw calls. InstancedMesh handles 2+ instances (good),
but 70K hashes appear only once → individual Mesh.

### Solution: THREE.BatchedMesh (r150+)
BatchedMesh consolidates multiple geometries into one draw call.
Group single meshes by storey|disc|material → one BatchedMesh per group.
122K elements → ~200 BatchedMesh objects → ~200 draw calls.

### Phase 1: BatchedMesh Rewrite (this session)

#### Scope
1. Read `deploy/dev/streaming.js` `_flushInstanced()` (lines 334–507)
2. Desktop single-mesh path (lines 356–375) → bucket by `storey|disc|rgba`
3. Build one BatchedMesh per bucket:
   - `addGeometry()` each unique geometry
   - `addInstance()` per element, `setMatrixAt()` for position/rotation
   - `setColorAt()` if per-instance color needed
4. Store per-instance metadata for pick + filter:
   - `A._batchMeta[batchedMesh.id]` = [{guid, storey, disc, ifcClass, instanceIdx}, ...]
   - `A.guidMap[batchedMesh.id + '_' + instanceIdx] = guid`
5. Preserve click-pick in `deploy/dev/picking.js`:
   - BatchedMesh raycast returns `hit.batchId` (not `hit.instanceId`)
   - Add priority branch before InstancedMesh check (line 196)
   - `guid = A._batchMeta[hit.object.id][hit.batchId].guid`
   - Highlight bbox: `hit.object.getMatrixAt(hit.batchId, _m4)` works same as InstancedMesh
6. Preserve storey/disc filter:
   - `setVisibleAt(instanceIdx, false)` per element within batch
   - Build reverse map: storey → [(batchedMesh, instanceIdx), ...]
   - Find ALL code that sets `.visible` on single meshes and add BatchedMesh branch
7. Mobile merge path (lines 408–499) — LEAVE UNTOUCHED (works, has DB nearest-point pick)
8. InstancedMesh path (2+ instances, lines 376–404) — LEAVE UNTOUCHED

#### Clash pair zoom-in
When clash detection zooms to a pair, the two elements must be individually highlightable
even though they're inside a BatchedMesh. Use `setColorAt(instanceIdx, highlightColor)`
to highlight, restore original color after. Same as mobile's DB nearest-point pick but
with precise instanceIdx — no approximate matching needed.

#### Snag pick
When user picks an element from a BatchedMesh for snag/issue creation:
- `hit.batchId` → look up `A._batchMeta[meshId][batchId]` → get guid
- Query DB with guid for full IFC info (same as current single-mesh pick)
- Element is inside a batch but pick returns exact guid — no loss of info

### Phase 2: DLOD via BatchedMesh (future session)

#### Why DLOD broke before (S258 disable reasons)
- **Wrong-angle onset**: thin elements viewed edge-on flicker between LOD levels
- **Hourglass conflicts**: DLOD and Time Machine both control visibility, they fight

#### How to fix with BatchedMesh
`setGeometryIdAt(instanceId, geometryId)` swaps geometry per instance at zero cost.

Three tiers:
| Tier | Content | SSE threshold |
|------|---------|---------------|
| LOD0 | 8-vert bbox (from DB bbox columns, no blob fetch) | < 4px |
| LOD1 | Decimated mesh (~10% faces, future DB column) | 4–16px |
| LOD2 | Full geometry blob (existing) | ≥ 16px |

Screen-space error formula (Cesium/3D-Tiles standard):
```
sse = (geometricError × screenHeight) / (distance × 2 × tan(fovy/2))
```

#### Wrong-angle onset fix
- Bounding-sphere SSE (orientation-independent, not projected bbox)
- Hysteresis band: refine at 16px, coarsen at 12px
- 3-frame delay before swap

#### Hourglass cooperation
- Time Machine sets `_dlodPaused = true` (already in time_machine.js lines 1784–1785)
- When paused, DLOD freezes current LOD state — no swaps
- TM controls visibility via `setVisibleAt()`, DLOD controls geometry via `setGeometryIdAt()`
- Separate concerns: visibility ≠ LOD level

### Key files
- `deploy/dev/streaming.js` — `_flushInstanced()` rewrite (Phase 1)
- `deploy/dev/picking.js` — BatchedMesh click resolution (Phase 1)
- `deploy/dev/scene.js` — BVH `computeBatchedBoundsTree` (Phase 1)
- `deploy/dev/dlod.js` — SSE-driven LOD swap loop (Phase 2)
- `deploy/dev/time_machine.js` — DLOD pause/resume (Phase 2)

### §-tags
- `§BATCHED_FLUSH groups=N drawCalls=N (was N)` — consolidation result
- `§BATCHED_PICK guid=X batchIdx=N` — click-pick on BatchedMesh
- `§BATCHED_FILTER storey=X visible=N hidden=N` — storey filter via setVisibleAt
- `§DLOD_SSE swaps=N lod0=N lod1=N lod2=N` — LOD swap stats (Phase 2)

### Pre-flight checklist (start of session)
1. Read this prompt fully
2. Read `deploy/dev/streaming.js` _flushInstanced() — confirm line numbers
3. Read `deploy/dev/picking.js` — confirm raycast priority chain
4. Grep for ALL places that set `.visible` on meshes (storey/disc filter sites)
5. Verify `THREE.BatchedMesh` exists in r156: `console.log(typeof THREE.BatchedMesh)`
6. Run existing Playwright tests as baseline: `npx playwright test --reporter=list`

### Version bump protocol (MANDATORY on every deploy)
1. Bump `?v=N` for every modified JS file in `index.html`
2. Bump `sw.js` CACHE_VERSION = 'vN+1'
3. Match `index.html` `sw.js?v=N+1`
4. Upload sw.js + index.html + all changed files

### Deploy target
- `bim-ootb-full` first, then dev/live after confirmed

### Testing
1. LTU 122K — draw calls < 500, fps > 30, click-pick works
2. SampleHouse — no regression, pick works, storey filter works
3. Terminal — clash zoom highlights correct pair
4. Hospital — Night mode still works with BatchedMesh
5. Mobile — unchanged (merge path untouched)
