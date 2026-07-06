# S164 — MeshBinder Scale Deduplication

**Spec:** `docs/Enterprise.md` §"There Is More" — future optimisation noted in session analysis
**Prerequisite:** S162 (streaming .blend) proven first — this optimisation makes it even smaller

## The Fault

`MeshBinder.java` currently:
1. Fetches base mesh from `component_library.db`
2. Computes scale factors (bboxW/meshW, bboxD/meshD, bboxH/meshH)
3. **Bakes scale into every vertex coordinate**
4. Computes `geometry_hash` from the scaled vertices
5. Writes unique scaled mesh to `output.db → base_geometries`

Result: every element gets a unique `geometry_hash` even if it's the same product
type. 500 SWITCH elements = 500 separate mesh blobs in `output.db`. Terminal
(sjtii_terminal.db): 48,428 geoms for 48,428 elements — 1:1, zero deduplication,
299 MB output DB.

This is the exact problem extracted.db AVOIDS — IFC uses shared `IfcShapeRepresentation`
so 500 identical windows share one geometry_hash. MeshBinder throws that away.

## Evidence

```
sjtii_terminal.db:  48428 geoms / 48428 elements — 1:1 (every element unique after scale)
Duplex output.db:     329 geoms /   329 elements — 1:1
SampleHouse output:    58 geoms /    58 elements — 1:1

vs extracted.db:
Ifc2x3_Duplex:        970 geoms /  1169 elements — 0.83× (IFC dedup working)
LTU_AHouse:         69559 geoms / 125997 elements — 0.55× (near 2:1 instancing)
```

## The Fix

**Do not bake scale into vertices. Store scale separately.**

### Schema change — `element_instances` in output.db

Add scale columns (already has `guid → geometry_hash`):

```sql
ALTER TABLE element_instances ADD COLUMN scale_x REAL NOT NULL DEFAULT 1.0;
ALTER TABLE element_instances ADD COLUMN scale_y REAL NOT NULL DEFAULT 1.0;
ALTER TABLE element_instances ADD COLUMN scale_z REAL NOT NULL DEFAULT 1.0;
ALTER TABLE element_instances ADD COLUMN is_ns_rotated INTEGER NOT NULL DEFAULT 0;
```

### MeshBinder change

Instead of scaling vertices, write the base library hash + scale factors:

```java
// BEFORE (bakes scale → unique hash per element):
MeshGeometry transformed = transform(libraryMesh, scaleX, scaleY, scaleZ, center);
String hash = contentHash(transformed.vertices());
writeGeometry(outputConn, hash, transformed);
writeInstance(outputConn, guid, hash);

// AFTER (preserves library hash → shared mesh, scale per instance):
String baseHash = libraryHash;  // from component_library.db — unchanged
writeGeometryIfAbsent(outputConn, baseHash, libraryMesh);  // write once
writeInstance(outputConn, guid, baseHash, scaleX, scaleY, scaleZ, isNS);
// center/translation still goes to element_transforms as before
```

`writeGeometryIfAbsent` — INSERT OR IGNORE. If 500 SWITCHes share the same
library hash, the mesh is written once on the first INSERT, ignored for the
other 499.

### Stage 2 / federation loader change

Apply scale as Blender object scale, not baked into mesh:

```python
# stage2_tessellation_loader.py
instance = bpy.data.objects.new(guid, template_obj.data)  # shared mesh
instance.location = center_m
instance.scale = (row['scale_x'], row['scale_y'], row['scale_z'])
# Blender applies scale at draw time — GPU instancing preserved
```

`obj.scale` is per-object, not per-mesh — GPU instancing still works.
All 500 SWITCHes share one mesh, each has its own scale vector.

## Expected gains

| Building | Current geoms | After fix | Reduction |
|---------|-------------|-----------|-----------|
| Terminal (48K el) | 48,428 | ~200–500 unique products | ~99% |
| Hospital (64K el) | 2,110 | ~200–500 | ~75–90% |
| Duplex (329 el) | 329 | ~20–30 unique products | ~90% |
| SampleHouse (58 el) | 58 | ~15–20 | ~65% |

Terminal output.db: 299 MB → estimated **5–15 MB**.
Streaming `.blend` for Terminal: already small, now even smaller DB to fetch from.

## Witness

**W-MESHBINDER-DEDUP:** for any building with >10 elements of the same product type,
`SELECT COUNT(DISTINCT geometry_hash) FROM element_instances` <<
`SELECT COUNT(*) FROM element_instances`.

```java
// Assert in TerminalSandboxTest or new MeshBinderDedupTest:
long uniqueGeoms = countDistinctGeoms(outputConn);
long totalElements = countElements(outputConn);
assertTrue(uniqueGeoms < totalElements * 0.5,
    "W-MESHBINDER-DEDUP: expected <50% unique geoms, got "
    + uniqueGeoms + "/" + totalElements);
```

## Files to change

- `DAGCompiler/src/main/java/com/bim/compiler/dsl/MeshBinder.java` — remove scale baking, write base hash + scale cols
- `migration/DV053_element_instances_scale.sql` — ADD COLUMN scale_x/y/z/is_ns_rotated
- `federation/loading/stage2_tessellation_loader.py` — read scale cols, apply as `obj.scale`
- **No changes to component_library.db schema**
- **No changes to extracted.db schema**

## Gate

- SH 9/9, DX 9/9 (no regression — geometry identical, just stored differently)
- Terminal output.db < 20 MB (was 299 MB)
- W-MESHBINDER-DEDUP PASS for DX, SH, Terminal
- Blender: objects visually identical — scale applied at draw time, not baked
