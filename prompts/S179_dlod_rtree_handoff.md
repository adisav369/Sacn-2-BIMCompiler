# ⚠ DO NOT REMOVE
# Scope: S179 — DLOD architecture: RTree=LOD-0, GN=LOD-1/2 near-camera only
# Read the log after every run. No claims without §PROOF log lines.

## Why the previous S179 (realize-to-mesh) was wrong

"Realize Instances" bakes GN instances into plain mesh. This destroys
`instance_index` — the mechanism DLOD uses to swap LOD tiers per element.
DLOD is the whole point of the GN architecture. Do NOT realize.

## Correct architecture (from DLOD_SPEC.md + S175 design)

```
LOD-0 (all 1M elements, always): RTree GPU bboxes — pure GPU lines, instant
LOD-1 (10-100m from camera):     GN instance_index → real mesh template
LOD-2 (<10m from camera):        GN instance_index → full-material mesh
```

The 258-modifier problem arose because GN was trying to represent ALL 1M
elements simultaneously. The fix: GN only covers camera-near elements.
Far elements are already covered by the RTree GPU path.

## Root cause of 5s lag

258 GN modifier objects × Blender depsgraph re-eval on every camera move =
5s per frame. The cause is scope: GN should never hold 1M point instances.

## Target state

```
RTree GPU (always on):      1M bboxes, instant, all disciplines
GN (near camera only):      ~500-2000 elements within 100m of camera
                            6 GN objects (one per discipline)
                            each point mesh: only elements within camera sphere
DLOD handler:               depsgraph_update_post
                            - compute distances to camera (numpy, ~4ms/1M)
                            - for elements entering LOD-1 sphere: add to GN point mesh
                            - for elements leaving sphere: remove from GN point mesh
                            - swap instance_index for LOD tier
```

## Phase 1 — Trim GN point meshes to camera sphere

In `dlod_handler.py`, on every camera movement halt:

1. Get camera position (from `rv3d.view_matrix.inverted().translation`)
2. Vectorized numpy: `dist_sq = einsum(centres - cam, centres - cam)`
3. `near_mask = dist_sq < FAR_SQ` (100m sphere)
4. For each discipline GN object:
   - Rebuild point mesh with ONLY elements where `near_mask[element_index]`
   - Each point: position = element centroid, instance_index = hash→template_index
5. Elements outside sphere: shown as RTree GPU bboxes (already drawn)

Log: `§PROOF DLOD_TRIM disc=ARC near=342 far=684790 gn_pts=342`

## Phase 2 — Verify no visual gap at LOD boundary

At 100m boundary: element switches from RTree GPU bbox (wire) to GN mesh.
Must verify colours match at the boundary. ARC discipline: grey wire → grey mesh.

Log: `§PROOF DLOD_BOUNDARY disc=ARC boundary=100m visual=continuous`

## Phase 3 — Measure depsgraph cost at reduced scale

With ~500 points per discipline GN object (not 1M):
- Expected: depsgraph eval <5ms per discipline object, <30ms total
- Measure: `bpy.app.handlers.depsgraph_update_post` timer before/after

Log: `§PROOF DLOD_EVAL_MS gn_pts=N depsgraph_ms=X target_ms=30`

## What NOT to do

- Do NOT realize/bake GN instances — kills DLOD
- Do NOT try to represent all 1M elements in GN — that's what the RTree is for
- Do NOT flatten chunks back to one collection
- Do NOT remove halt mode from dlod_handler.py

## Files

- `federation/dlod_handler.py` — rebuild GN point meshes on camera halt
- `federation/loading/stage2_library_linker.py` — initial GN load (sets up structure)
- `federation/bbox_visualization.py` — RTree GPU path (LOD-0, already working)
- `internal/DLOD_SPEC.md` — authoritative spec, §3 LOD tiers, §4 distance computation

## Performance targets

| Path | Elements | Expected | Status |
|------|----------|----------|--------|
| RTree GPU (LOD-0) | 1,061,736 | <13s load, instant orbit | **DONE S180** |
| Stingy Mesh Loader | ≤500 on demand | Load/Shred exact IFC mesh | **DONE S180** |
| GN (LOD-1/2) | ~500-2000 near camera | <30ms depsgraph | **PENDING S182+** |
| DLOD transition | per frame | <5ms numpy distance | Spec exists, not proven |

## S180 outcome (read before continuing GN work)

RTree GPU path is fully proven and production-ready:
- 1M elements load in ~13s on a normal laptop
- Stingy Mesh Loader: LOAD/SHRED works, placement dead accurate
- Geo hash hell: closed (T_{full_hash} naming + stale _selected_element bug fixed)
- Sprinkler dedup: queued as S181 (separate geometry quality pass)

**GN/DLOD is the next unresolved piece.** The architecture is spec'd in this
prompt and `internal/DLOD_SPEC.md`. The blocker is `make_local()` in the DLOD
LOD-0→LOD-1 promotion path (`dlod_handler.py`). Without it, GN evaluates linked
meshes at 60fps = frozen viewport. The RTree GPU path runs in parallel and is
unaffected — users can work in RTree mode while GN is being debugged.
