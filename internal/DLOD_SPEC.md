# DLOD Spec — Distance LOD via GN Point Cloud
> **Foundation:** [FULL_LOADER2_SRS](FULL_LOADER2_SRS.md) §5/§7/§8 · [BBC](BOMBasedCompilation.md)

**Version:** 1.0 | **Date:** 2026-04-11
**Scope:** Runtime LOD tier transitions for 1M+ elements via GN instance reference swapping
**Handler:** `dlod_handler.py` — `depsgraph_update_post` callback
**Depends on:** `lod_manager.py` (S170), `blend_cache.py` (S165/S169), `spatial_index.py`

---

## 1. Problem

S170 `lod_manager.py` manages template mesh loading by fill/clear on geometry
data-blocks. This works for discipline toggling and coarse distance loading, but
it operates at the **template** level (one KDTree entry per unique geometry_hash
centroid), not per-element. Two limitations at 1M scale:

1. **No per-element LOD.** A template shared by 10K elements is either all-visible
   or all-invisible. Camera near one instance loads the template globally.
2. **No tiered geometry.** Elements are either full mesh or invisible. No
   intermediate bbox-proxy tier for far elements that still need spatial presence.

## 2. Solution

DLOD adds three LOD tiers per element, driven by camera distance, with transitions
via GN instance reference swapping. The mechanism is:

- Each element is a point in a GN point cloud (existing S165 architecture).
- Each point has an `instance_index` attribute selecting which template mesh.
- LOD transition = update `instance_index` to point at a different template
  (bbox proxy vs linked mesh vs full-material mesh).
- No mesh creation or deletion. No Blender object creation. Array mutation only.

## 3. Three LOD Tiers

Extracted from FULL_LOADER2_SRS.md §5:

| Tier | Distance | Geometry Source | Visual | Cost per element |
|------|----------|----------------|--------|------------------|
| LOD-0 (far) | >100m | bbox proxy: 8 verts, 12 faces from R-tree min/max | Solid discipline color | ~96 bytes GPU |
| LOD-1 (mid) | 10-100m | Linked mesh from library.blend (or baked template) | Discipline color | Full mesh cost |
| LOD-2 (near) | <10m | Same linked mesh | Full materials + UV | Full mesh + material |

**LOD-0 bbox proxy generation:** For each unique geometry_hash, compute the
AABB from the stored R-tree extents of a representative instance, build an
8-vertex 12-face box mesh. Store as `_bbox_{hash}` template alongside the
real mesh template. This is a one-time cost at cache creation.

## 4. Camera Distance Computation

Per-frame vectorized numpy distance from camera to all element centres:

```python
# centres: numpy (N, 3) float32 — all element positions, built once at load
# cam_pos: numpy (3,) float64 — from rv3d.view_matrix.inverted().translation
delta = centres - cam_pos                         # (N, 3) broadcast
dist_sq = np.einsum('ij,ij->i', delta, delta)     # (N,) squared distances
```

Squared distances avoid sqrt cost. Threshold comparisons use squared values:
- `NEAR_SQ = 10.0 * 10.0 = 100.0`
- `FAR_SQ  = 100.0 * 100.0 = 10000.0`

**Cost:** ~4ms for 1M elements (numpy vectorized, single-threaded).

## 5. LOD Bucket Partitioning

```python
lod_2 = dist_sq < NEAR_SQ                        # bool mask
lod_0 = dist_sq > FAR_SQ                          # bool mask
lod_1 = ~lod_2 & ~lod_0                           # remainder
```

Per discipline GN object, the `instance_index` attribute is an int32 numpy array.
Each entry maps to a template object index in the `_GN_Templates` collection.

**Index mapping:** For each geometry_hash, two template indices exist:
- `real_index`: the actual mesh template (existing S165 index)
- `bbox_index`: the bbox proxy template (new, created by DLOD init)

LOD-2 and LOD-1 use `real_index`. LOD-0 uses `bbox_index`. Material
differentiation between LOD-1 and LOD-2 is handled by a future material
override pass (not in scope for this spec — both get the template's material).

## 6. GN Instance Reference Swapping

When an element crosses a threshold boundary, its `instance_index` value changes:

```python
# For elements transitioning from LOD-1 to LOD-0:
needs_swap = current_lod != new_lod               # per-element bool
swap_indices = np.where(needs_swap)[0]             # indices needing update

# Batch limit: max 500 swaps per frame
if len(swap_indices) > BATCH_LIMIT:
    swap_indices = swap_indices[:BATCH_LIMIT]

# Apply swap to the point mesh attribute
for disc, (mesh, idx_arr, elem_map) in disc_data.items():
    disc_swaps = swap_indices[elem_map]            # filter to this discipline
    for i in disc_swaps:
        new_idx = bbox_index if new_lod[i] == 0 else real_index
        idx_arr[i] = new_idx
    mesh.attributes['instance_index'].data.foreach_set('value', idx_arr)
    mesh.update()
```

**Critical constraint (from FULL_LOADER2_SRS.md §12):** GN instance swaps are the
only LOD mechanism. No mesh deletion or creation during LOD transitions.

## 7. Batch Budget

From FULL_LOADER2_SRS.md §7: max 500 swaps per frame to stay under 16ms budget.

| Operation | Cost per swap | Budget at 500 |
|-----------|--------------|---------------|
| numpy index update | ~0.001ms | 0.5ms |
| foreach_set + mesh.update() | ~0.02ms per discipline | ~0.3ms (13 disciplines) |
| Distance computation (1M) | 4ms total | 4ms |
| Total per frame | | ~5ms |

Remaining ~11ms is for Blender's depsgraph eval and GPU draw.

**Prioritisation:** Swaps are ordered by distance delta (elements that moved the
most relative to their threshold get swapped first). This ensures smooth visual
transitions starting from the threshold boundary.

## 8. depsgraph_update_post Handler Design

```
depsgraph_update_post(scene, depsgraph)
    ├── Skip if no Federation_Cached collection
    ├── Skip if camera hasn't moved > MIN_DELTA (1.0m)
    ├── Get camera position from active 3D viewport
    ├── Compute distances (vectorized numpy, all elements)
    ├── Partition into LOD-0/1/2 buckets
    ├── Diff against previous LOD state → swap list
    ├── Apply up to BATCH_LIMIT swaps
    ├── Update per-discipline GN point mesh attributes
    └── Log timing if > 10ms
```

**Re-entrancy guard:** The handler sets a `_dlod_running` flag to prevent
recursive calls triggered by its own mesh updates.

**Throttle:** Camera position is checked first. If moved less than
`LOD_MIN_CAMERA_DELTA` (1.0m), the handler returns immediately. This
prevents computation on every depsgraph tick (which fires on mouse hover, etc.).

## 9. R-tree Frustum Culling Integration

For viewports with bounded frustum (not orbit-all), an optional frustum cull
pre-filters elements before distance computation:

1. Extract view frustum planes from `rv3d.perspective_matrix`.
2. Query R-tree for elements intersecting the frustum AABB (conservative).
3. Only compute distances for frustum-visible elements.

This reduces the numpy distance computation from N to ~N/4 for typical views.

**Implementation:** Uses existing `spatial_index.py` `FederationIndex.query_by_bbox()`
with the frustum's AABB as the query box. The R-tree is SQLite-native, zero setup.

```python
# Extract frustum AABB from perspective matrix
pm = rv3d.perspective_matrix
# ... compute frustum AABB from clip planes ...
frustum_elements = federation_index.query_by_bbox(
    (frustum_min_x, frustum_min_y, frustum_min_z),
    (frustum_max_x, frustum_max_y, frustum_max_z))
```

**Fallback:** If frustum extraction fails or no FederationIndex is loaded,
distance computation runs on all elements (still within budget at 4ms/1M).

## 10. Performance Targets

From FULL_LOADER2_SRS.md §8, refined for DLOD:

| Metric | Target | Mechanism |
|--------|--------|-----------|
| DLOD handler per frame | <10ms | Vectorized numpy + batch limit |
| Distance computation (1M) | <5ms | `np.einsum` squared distances |
| LOD swap batch | <2ms for 500 swaps | numpy array mutation + foreach_set |
| Camera skip (no movement) | <0.1ms | Squared distance check on camera pos |
| Memory overhead | O(N) int8 for LOD state | One byte per element for current tier |
| Init (bbox proxy generation) | <2s for 18K unique hashes | from_pydata on 8-vert boxes |

## 11. Data Structures

```python
class DLODState:
    centres: np.ndarray          # (N, 3) float32 — all element positions
    current_lod: np.ndarray      # (N,) int8 — 0/1/2 per element
    disc_slices: dict            # disc -> (start, end) indices into centres
    hash_to_real_idx: dict       # geometry_hash -> template collection index
    hash_to_bbox_idx: dict       # geometry_hash -> bbox template index
    elem_hash_indices: np.ndarray # (N,) int32 — real template index per element
    elem_bbox_indices: np.ndarray # (N,) int32 — bbox template index per element
    near_threshold_sq: float     # 100.0 (10m squared)
    far_threshold_sq: float      # 10000.0 (100m squared)
    batch_limit: int             # 500
    last_camera_pos: tuple       # (x, y, z) or None
    _running: bool               # re-entrancy guard
```

## 12. Lifecycle

1. **Init** — Called after `create_cache_gn_instances()` completes:
   - Read element positions from disc_positions into flat numpy array
   - Generate bbox proxy templates for all unique geometry_hashes
   - Build hash-to-index maps
   - Initialize `current_lod` to LOD-0 (all far)

2. **Tick** — `depsgraph_update_post` handler:
   - Camera distance check, bucket partition, batch swap

3. **Discipline toggle** — When `lod_manager.on_discipline_toggle()` fires:
   - Clear DLOD state for hidden disciplines (all to LOD-0)
   - Mark newly visible disciplines for re-evaluation on next tick

4. **Shutdown** — On unregister or file close:
   - Clear numpy arrays, deregister handler

## 13. Integration with Existing Code

| Existing module | Integration point |
|-----------------|-------------------|
| `blend_cache.py` `create_cache_gn_instances()` | After Phase 5 (GN setup), call `dlod_init()` to build bbox proxies and state |
| `lod_manager.py` | DLOD replaces distance-based template fill/clear. LODManager discipline toggle stays. |
| `__init__.py` `federation_depsgraph_update()` | Add DLOD tick call inside existing handler |
| `spatial_index.py` | Optional frustum cull pre-filter |
| `logging_utils.py` | DLOD uses `[DLOD]` prefix print pattern (matches `[S170]`, `[S169]` convention) |

## 13a. Save/Open Implication — DLOD Supersedes Thin Save

DLOD makes explicit thin-save unnecessary. At any camera position, ~95% of elements
are LOD-0 (8-vert bbox proxies). The `.blend` is naturally small.

| Event | What happens | Log |
|-------|-------------|-----|
| Save (Ctrl+S) | Writes current LOD state as-is. Most elements are bbox proxies. | `§FINE save: N LOD-0, M LOD-1, K LOD-2` |
| Open | Restores saved LOD state. Viewport shows exactly what was saved. | `§FINE open: restored N LOD-0, M LOD-1, K LOD-2` |
| Camera move | DLOD handler resumes. LOD transitions fire on first `depsgraph_update_post`. | `§DLOD_TICK swaps=X` |

**Bonsai compatibility:** No disruption. Handlers check for federation collections.
No federation → no-op. Normal Bonsai save/open untouched.

## 14. Constraints

- **No mesh creation at runtime.** Bbox proxies are pre-built at cache init.
- **No Blender object creation at runtime.** Only attribute array mutations.
- **No from_pydata() at runtime.** All geometry pre-exists in templates.
- **component_library.db is read-only at runtime.** Only bbox proxy generation reads it (at init, not per-frame).
- **GN instance swaps are the only LOD mechanism.** (FULL_LOADER2_SRS.md §12)

## 15. Testing

| Test | Type | Proof tag | What it proves |
|------|------|-----------|----------------|
| DLOD distance computation | Unit (no Blender) | §PROOF DLOD_DIST | numpy dist matches math.sqrt within float32 |
| DLOD bucket partition | Unit (no Blender) | §PROOF DLOD_BUCKET | Correct LOD assignment for known distances |
| DLOD batch limit | Unit (no Blender) | §PROOF DLOD_BATCH | Never exceeds 500 swaps per tick |
| DLOD handler timing | Blender (background) | §PROOF DLOD_PERF | <10ms for 1M elements |
| DLOD bbox proxy count | Blender (background) | §PROOF DLOD_BBOX | One bbox proxy per unique geometry_hash |
