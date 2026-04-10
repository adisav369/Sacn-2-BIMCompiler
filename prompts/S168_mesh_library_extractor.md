# S168 — Extraction Pipeline: Complete Library at Extraction Time

## DO NOT REMOVE
Scope: Move all constant IFC data (meshes, metadata, product identification) into
component_library.db during extraction. IFCtoBOM becomes read-only consumer.
Read the log after every run.

You are a coder. One bounded task.

---

## Principle Violation Being Fixed

IFCtoBOM currently writes `M_Product`, `I_Geometry_Map`, `ad_bom*` etc. back
into `component_library.db`. This mixes extraction (WHAT) with compilation (HOW)
in the same file.

**Rule:** The library is INPUT, not output. Populated entirely at extraction time.
IFCtoBOM reads from it, writes BOMs/orders to `_BOM.db` / `ERP.db` only.

---

## Architecture After S168

```
IFC file
  ↓
extractIFC (federation_preprocessor.py)
  ↓                          ↓
_extracted.db               component_library.db
(per-building)              (shared, append-only)
  - elements_meta             - component_geometries (mesh BLOBs by hash)
  - element_transforms        - I_Geometry_Map (hash → ifc_class, name, storey)
  - element_instances         - M_Product (product definitions)
  - elements_rtree            - component_definitions
  - spatial_structure         - placement_rules

                              ALL constant from the IFC.
                              Populated ONCE at extraction.
                              Never written to again.

IFCtoBOM (read-only consumer)
  reads FROM: component_library.db + _extracted.db
  writes TO:  _BOM.db / ERP.db
  - ad_bom, ad_bom_child (BOM structures)
  - orders, validation
  - NOTHING back into the library

blend_cache.py (read-only consumer)
  reads FROM: component_library.db (meshes)
            + federation DB (metadata, transforms, instances)
  - GN instancing at 1M+ scale
  - No BLOBs in federation DB needed
```

---

## What moves FROM IFCtoBOM TO extraction

| Table | Currently | After S168 |
|-------|-----------|------------|
| `component_geometries` | Partially filled by IFCtoBOM | Filled at extraction |
| `I_Geometry_Map` | Written by IFCtoBOM (74K rows) | Written at extraction |
| `M_Product` | Created by IFCtoBOM | Created at extraction (product identification) |
| `M_Product_Image` | Created by IFCtoBOM | Created at extraction |
| `component_definitions` | Created by IFCtoBOM | Created at extraction |
| `placement_rules` | Created by IFCtoBOM | Created at extraction |

## What stays in IFCtoBOM

| Table | Where it writes |
|-------|----------------|
| `ad_bom` / `ad_bom_child` | _BOM.db |
| `ad_bom_child_param` | _BOM.db |
| Orders, validation | ERP.db |
| Building-specific compilation | _BOM.db |

---

## Federation DB size impact

```
Before S168:  sandbox_1M.db = 996 MB (BLOBs embedded)
After S168:   sandbox_1M.db = 548 MB (hashes only, BLOBs in library)
              component_library.db = 686 MB (shared across all projects)
```

New IFC extractions append to `component_library.db`. Duplicate geometry_hashes
are ignored (INSERT OR IGNORE). The library grows monotonically but deduplicates
automatically.

---

## blend_cache.py integration (DONE)

Already implemented: `create_cache_gn_instances()` checks if `base_geometries`
has vertex BLOBs. If not, reads from `component_library.db/component_geometries`.
Falls back to embedded BLOBs for legacy DBs.

---

## Implementation phases

### Phase 1: Extraction writes to library (this prompt)
1. Modify `federation_preprocessor.py` to write mesh BLOBs to
   `component_library.db/component_geometries` (INSERT OR IGNORE by hash)
2. Write IFC metadata to `I_Geometry_Map` during extraction
3. Identify products and write `M_Product` definitions during extraction
4. Stop writing BLOBs to `_extracted.db/base_geometries` (hash-only table)
5. Add Terminal to sandbox build

### Phase 2: IFCtoBOM becomes read-only
6. Remove library-writing code from IFCtoBOM pipeline
7. IFCtoBOM reads products/geometry from library
8. BOM compilation writes to `_BOM.db` only
9. Verify existing tests pass

### Phase 3: Light .blend + progressive bake (S167)
10. Save .blend without meshes (~15 MB)
11. Progressive bake from library on open
12. Distance-based LOD swap

---

## Test plan

1. Extract SampleHouse → verify meshes + metadata in component_library.db
2. Extract Terminal → verify append (no duplicates)
3. Build sandbox_1M.db WITHOUT BLOBs → verify 548 MB
4. Load in Blender → verify meshes fetched from library
5. Run IFCtoBOM on SH → verify it reads from library, writes to _BOM.db only
6. Verify existing RosettaStone tests pass

## Files changed

- EDIT: `tools/federation_preprocessor.py` — write to library
- EDIT: `scripts/build_sandbox_1M.py` — hash-only base_geometries (DONE)
- EDIT: `federation/blend_cache.py` — read from library (DONE)
- EDIT: IFCtoBOM pipeline — remove library writes (Phase 2)
- KEEP: `library/component_library.db` — sacred, append-only
