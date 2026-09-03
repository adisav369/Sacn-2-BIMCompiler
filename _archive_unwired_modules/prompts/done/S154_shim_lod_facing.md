# S154 — Shim Entities + LOD Resolution + Facing Direction

**FRUSTRATION NOTE FROM OWNER:** S153 drifted into logging and collision optimisation
for two full cycles while the core visual deliverables — shim entities, device facing,
LOD geometry — were never touched. The user sees parametric boxes along the living room
wall. Fridge, sink, toilet are nowhere to be seen. Things hardly moved. **Do not drift.**
Do what the spec says. Check `internal/SpecToCode.md` — 18 items are N. Fix those.
Nothing else.

**Prior work:** S153 delivered collision avoidance, ceiling Z snap, walk reordering,
diagnostics logging. 21/21 tests, 18/18 pipeline. But 0% of the shim architecture.
See `internal/SpecToCode.md` for line-by-line compliance audit.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Never invent.

## Development Cycle (README Mantra)
1. Follow specs before coding — read §12a-§12f in DISC_VALIDATION_DB_SRS.md
2. Write tests before coding — the test defines "done"
3. Analyse debug logs and review code to fix
4. If you need to change code, change specs first

## What S153 Left Undone (from internal/SpecToCode.md)

### Priority 1: LOD Geometry — Make Devices Visible (SH first)

The user cannot see TOILET, SINK, LIGHT, SPRINKLER, SUPPLY_DIFFUSER in Bonsai.
They render as parametric boxes because `component_library.db` has no geometry
for these abstract product tokens.

**Root cause:** `MeshBinder.bind()` calls `library.resolveByProduct(productId)`.
For generative devices, `productId = "TOILET"`. There is no `M_Product_Image`
row mapping "TOILET" → geometry_hash. The `source_element_ref` bridge from S152
mapped M_Product.source_element_ref to IFC family names, but MeshBinder doesn't
use source_element_ref — it uses productId directly.

**Fix — two options (pick simplest):**

A) Add `M_Product_Image` rows for abstract tokens:
```sql
INSERT INTO m_product_image (product_id, geometry_hash)
SELECT 'TOILET', geometry_hash FROM i_geometry_map
WHERE element_ref = (SELECT source_element_ref FROM m_product WHERE product_id = 'TOILET')
LIMIT 1;
```
Repeat for SINK, LIGHT, SPRINKLER, SUPPLY_DIFFUSER.

B) Modify `MeshBinder.bind()` to fall back to `source_element_ref` when
`resolveByProduct` returns null:
```java
if (refGeoHash == null && p.productId() != null) {
    refGeoHash = library.resolveBySourceRef(p.productId()); // new method
}
```

**Verify on SH first.** Run pipeline, open in Bonsai, visually confirm devices
are real geometry not boxes. THEN do DX.

### Priority 2: Shim Entities (§12a.4-6)

Currently `placeGenerativeDevices()` creates bare `PlacementLoader.Placement`
with `ifcClass="IfcFlowTerminal"`. The spec says:

1. Create a SHIM Placement (phantom, not rendered):
   - ifcClass = "IfcVirtualElement" (IFC phantom)
   - familyRef = discipline + "_" + mount + "_SHIM" (e.g. "SP_FLOOR_SHIM")
   - position = wall/ceiling/floor surface point (from ShimMatcher or schedule)

2. Create DEVICE Placement as child of shim:
   - position = shim position + standoff offset (5mm wall, 50mm ceiling)
   - rotationZ = facing direction from placement_rule (§12g GAP-4 table)

The Placement record doesn't have a "parent" field. The shim is expressed as
two consecutive placements — shim first, device second — with the device's
position derived from shim + offset. The renderer doesn't need to know about
the parent relationship; it just places both elements.

**Facing direction table (from §12g GAP-4):**

| Placement Rule | rotationZ (radians) |
|---------------|-------------------|
| WALL_BACK | 0 (default, faces -Y into room) |
| WALL_ENTRY | π (faces +Y) |
| WALL_SIDE | π/2 (faces -X) |
| WALL_SINK | -π/2 (faces +X) |
| CEILING_* | 0 (pendant, no horizontal rotation) |
| FLOOR_* | 0 (upright, no horizontal rotation) |

### Priority 3: S20 Test Must Assert Shim Parent

Current S20 only checks furniture overlap. Spec says:
- Assert parent shim in placement hierarchy
- Assert shim has host_ifc_class
- Assert device offset from shim < 0.5m
- Assert facing direction matches wall normal

### NOT in scope for S154
- §12c END-join route (S21) — deferred to S155
- §12g GAP-5 anchor discovery — deferred to S155

## Gate

- SH: Open in Bonsai — TOILET, SINK, LIGHT visible as real geometry, not boxes
- SH: 9/9 PASS + generative devices have shim + facing
- DX: 9/9 PASS + same
- S20: PASS with shim parent assertion
- MepRouteGeometryTest: 21+/21+ PASS
- internal/SpecToCode.md: §12a.4-6 flipped to Y
