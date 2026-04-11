# Full Loader 2 SRS — Library-Linked Federation Loading
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md)

**Version:** 1.0 | **Date:** 2026-04-11
**Scope:** Replace BLOB-per-mesh loading with pre-baked library.blend link
**Operator:** `bim.load_full_federation_viewport_v2` — "Full Load v2 (Library Link)"
**Bake script:** `scripts/bake_library_blend.py`
**Proof test:** `scripts/test_orientation_proof.py`

---

## 1. Problem

Current `stage2_tessellation_loader.py` reads vertex/face BLOBs from SQLite and
calls `from_pydata()` per unique geometry_hash. For 18K meshes this takes ~13s
and creates O(unique) Blender mesh datablocks at runtime. The BLOB unpack +
`from_pydata()` path is the bottleneck.

## 2. Solution

Pre-bake all unique meshes into `library/library.blend` (one-time, offline).
At load time, link meshes by name via `bpy.data.libraries.load(link=True)` —
instant (0.03s for 18K meshes). No SQLite BLOB reads at runtime.

## 3. Inputs

| Artifact | Contents | Created by |
|---|---|---|
| `{building}_extracted.db` | `element_transforms` (centre, rotation), `element_instances` (guid-to-hash), `elements_rtree` (world bbox) | `extractIFCtoDB.py` |
| `library/library.blend` | One Mesh datablock per `geometry_hash`, named by hash | `bake_library_blend.py` |
| `library/component_library.db` | Source geometry BLOBs (read only by bake script, never at runtime) | extraction pipeline |

## 4. Architecture

```
library.blend (pre-baked)         extracted.db (meshless)
  Mesh["abc123..."]                 element_transforms: centre, rotation
  Mesh["def456..."]                 element_instances:  guid → geometry_hash
  ...18K meshes                     elements_rtree:     world bbox per element
        │                                   │
        ▼                                   ▼
  bpy.data.libraries.load()        numpy arrays (centres, rotations, hashes)
        │                                   │
        └──────────┬────────────────────────┘
                   ▼
         GN "Instance on Points"
         per-point attrs: hash_index, rotation, scale
```

## 5. Three LOD Tiers

| Tier | Distance | Geometry | Material | Transition |
|---|---|---|---|---|
| LOD-0 (far) | >100m | bbox proxy: 8 verts, 12 faces from R-tree | Solid discipline color | Swap GN instance ref |
| LOD-1 (mid) | 10-100m | Linked mesh from library.blend | Discipline color | Swap GN instance ref |
| LOD-2 (near/selected) | <10m | Same linked mesh | Full materials + UV + selection highlight | Swap GN instance ref |

LOD transition is a swap of the GN instance reference, never mesh reconstruction.

## 6. Load Sequence

1. **Read transforms** — `SELECT centre_x, centre_y, centre_z, rotation_x, rotation_y, rotation_z, geometry_hash FROM element_transforms JOIN element_instances USING(guid)` into numpy arrays.
2. **Link meshes** — `bpy.data.libraries.load(library_blend, link=True)` all meshes whose hash appears in the element set. Target: <0.1s for 18K unique meshes.
3. **Build GN point cloud** — one point per element, per-point attributes: `hash_index` (int), `rotation` (Euler XYZ), `scale` (vec3, default 1,1,1).
4. **GN Instance on Points** — Geometry Nodes tree picks mesh from linked collection by `hash_index`, applies rotation + scale per point.
5. **R-tree frustum culling** — `depsgraph_update_post` handler queries R-tree for viewport frustum, drives LOD transitions per element.

## 7. Distance LOD Protocol

Camera `depsgraph_update_post` handler runs per frame:
- Compute distance from camera to each element centre (vectorised numpy).
- Partition elements into LOD-0 / LOD-1 / LOD-2 buckets by distance threshold.
- For elements crossing a threshold boundary, swap the GN instance reference.
- Batch LOD transitions: max 500 swaps per frame to stay under 16ms budget.

## 8. Performance Targets

| Metric | Target | Witness |
|---|---|---|
| Initial load (100K elements) | <2s | §PROOF LOAD_TIME |
| LOD transition (per batch) | <100ms | §PROOF LOD_SWAP |
| Memory (mesh data) | O(unique_meshes) | §PROOF MEMORY |
| Memory (transforms) | O(elements), numpy arrays only | §PROOF MEMORY |
| File size (scene .blend) | Meshless — transforms + GN tree only | §PROOF FILE_SIZE |
| Library link time (18K meshes) | <0.1s | §PROOF LINK_TIME |

## 9. Operator Registration

```python
class LoadFullFederationViewportV2(bpy.types.Operator):
    """Load federation via library.blend link (instant mesh loading)"""
    bl_idname = "bim.load_full_federation_viewport_v2"
    bl_label = "Full Load v2 (Library Link)"
    bl_options = {'REGISTER', 'UNDO'}
```

**Fallback:** If `library/library.blend` does not exist, log a warning and
delegate to the current `bim.load_full_federation_viewport` (BLOB path).

## 10. Bake Pipeline

```
component_library.db ──→ bake_library_blend.py ──→ library.blend
```

Run once after extraction. Re-run only when `component_library.db` gains new
geometry hashes. The bake script is idempotent — re-running overwrites the
output file.

## 11. Testing

| Test | Type | Proof tag | What it proves |
|---|---|---|---|
| `test_orientation_proof.py --db X --library Y` | Standalone (no Blender) | §PROOF BBOX_RECONSTRUCT | `R × local_mesh + centre ≈ R-tree bbox` within 5cm |
| `test_orientation_proof.py` LIBRARY_COVERAGE | Standalone | §PROOF LIBRARY_COVERAGE | Every hash in extracted DB exists in library |
| `test_orientation_proof.py` EULER_SANITY | Standalone | §PROOF EULER_SANITY | All rotation angles within [-2pi, 2pi] |
| `test_library_blend.py` | Blender (background) | §PROOF BAKE | Baked mesh count matches library DB unique hashes |
| Visual inspection | Manual | §PROOF VISUAL | No spikes, no overflow, elements fit R-tree bbox |

## 12. Constraints

- `component_library.db` is **read-only at runtime**. Only the bake script reads it.
- `library.blend` meshes are **linked, not appended** — single source of truth.
- No `from_pydata()` calls at runtime. If a mesh is missing from library.blend, the element gets LOD-0 bbox proxy, never a runtime mesh build.
- GN instance swaps are the only LOD mechanism. No mesh deletion or creation during LOD transitions.
