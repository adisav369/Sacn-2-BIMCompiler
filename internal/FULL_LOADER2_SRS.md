# Full Loader 2 SRS — Library-Linked Federation Loading
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md)

**Version:** 1.1 | **Date:** 2026-04-11
**Scope:** Replace BLOB-per-mesh loading with pre-baked library.blend link
**Operator:** `bim.link_federation_library` — "Library" button
**Bake script:** `scripts/bake_library_blend.py`
**Proof test:** `scripts/test_orientation_proof.py`
**Pipeline:** `scripts/pipeline_library.sh <building>`

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
class LinkFederationLibrary(bpy.types.Operator):
    """Load federation via library.blend link (instant mesh loading)"""
    bl_idname = "bim.link_federation_library"
    bl_label = "Library"
    bl_options = {'REGISTER', 'UNDO'}
```

**No fallback.** If `library/library.blend` does not exist, log an error and
prompt the user to run `./scripts/pipeline_library.sh <building>`. The old
BLOB-based `from_pydata()` path is retired (§13).

## 9.1 Material Resolution (3-tier chain)

Materials are resolved at **load time** by the library linker, not at bake time.
The bake script stores geometry-only meshes with a `None` material slot for
object-level override.

| Priority | Source | Coverage (Hospital) |
|----------|--------|---------------------|
| 1. Direct rgba | `elements_meta.material_rgba` | 29% (18,825/63,917) |
| 2. surface_styles | `surface_styles.style_name` match (incl. colon-split for Revit names) | ~171 elements |
| 3. Discipline fallback | `DISCIPLINE_COLORS` dict (ARC, STR, MEP, ACMV, FP, ELEC, CW) | 70% (44,921) |

The 70% discipline fallback is expected — those IFC elements have no
`material_name` in the source file.

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

## 13. Old Loader Retirement (S174)

The BLOB-based `from_pydata()` loaders are retired. Library-linked loading is
the only path.

### 13.1 Retired Operators

| Operator | bl_idname | Replacement |
|----------|-----------|-------------|
| `LoadSolidFederationViewport` | `bim.load_solid_federation_viewport` | `bim.preview_federation_viewport` (R-Tree) |
| `LoadFullFederationViewport` | `bim.load_full_federation_viewport` | `bim.link_federation_library` (Library) |
| `LoadFullFederationViewportGI` | `bim.load_full_federation_viewport_gi` | `bim.link_federation_library` (Library) |

### 13.2 Retired Loader Files

| File | Reason |
|------|--------|
| `federation/stage2_tessellation_loader.py` (root) | BLOB `from_pydata()` path |
| `federation/loading/stage2_tessellation_loader.py` | Duplicate of root |
| `federation/loading/stage2_semantics.py` | Old procedural path |
| `federation/loading/stage2_semantics_optimized.py` | Old procedural path |
| `federation/loading/stage2_semantics_chunked.py` | Old procedural path |
| `federation/loading/stage2_gpu_progressive.py` | Old progressive path |
| `federation/loading/unified_progressive_loader.py` | BLOB `from_pydata()` path |

### 13.3 UI Wiring Changes

| File | Old | New |
|------|-----|-----|
| `federation/__init__.py` | Registers old operator classes | Remove from `classes` tuple |
| `federation/ui_clean.py:211,214` | `load_solid` / `load_full` buttons | `link_federation_library` or remove |
| `federation/ui.py:260` | `load_full` button | `link_federation_library` or remove |
| `federation/webui_sync.py:433` | Fallback to `load_full_federation_viewport_gi` | Fallback to `link_federation_library` |

### 13.4 Retained

- `blend_cache.py` — thin-blend save/restore handlers (`strip_template_meshes`, `restore_template_meshes`) still needed. BLOB cache builders can be removed.
- `stage2_library_linker.py` — the active loader.
- `stage2_gpu_instancing.py` — procedural box instancing for R-Tree preview.

## 14. Pipeline Building Support

`pipeline_library.sh` supports named buildings via a `case` block.

| Building | IFC pattern | Status |
|----------|-------------|--------|
| Hospital | `Hospital_IFC4_*.ifc` (UNMERGED/) | PASS |
| SampleHouse | `Ifc4_SampleHouse.ifc` (IFC/) | PASS |
| Clinic | `Clinic_*.ifc` (UNMERGED/) — generic fallback | Ready |
| Terminal | `SJTII-*.ifc` (IFC/) — needs explicit case | BLOCKED: filename mismatch + coordinate alignment |
| Generic | `${BUILDING}_*.ifc` | Fallback |

### 14.1 Terminal Blocker

Terminal IFC files are named `SJTII-*-Clean.ifc` (not `Terminal_*.ifc`).
The generic case in `pipeline_library.sh` cannot match them. Additionally,
previous extraction shows coordinate misalignment at km-scale despite
`unit_scale=0.001` detection. Requires:

1. Add explicit `Terminal` case to `pipeline_library.sh` with `SJTII-*-Clean.ifc` pattern
2. Investigate geolocation alignment offsets in `extract_merge_disciplines.py`
