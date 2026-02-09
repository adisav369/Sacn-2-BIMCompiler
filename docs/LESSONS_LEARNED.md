# Lessons Learned: 100 Phases of BIM Compiler Development

## 1. Engine Evolution: Retail to Wholesale

The compiler evolved through three distinct eras:

**Era 1 (Phases 1-50): Hardcoded Features**
Every element type required a custom code path. Adding toilet fixtures meant writing `placeToiletBlockFixtures()` with hardcoded stall widths, sink counts, and spacing. Each new element was a week of work.

**Era 2 (Phases 50-85): BOM-Driven Placement**
The `ad_bom_child` + `ad_bom_child_param` tables became universal resolvers. A single `BOMRuleAD.java` class replaced dozens of hardcoded placement methods. Adding a new element meant adding a row to a SQLite table, not writing Java.

**Era 3 (Phases 85-100): Standards as Compiler Rules**
AD metadata tables encode building codes (UBBL, IBC, NFPA) as data. The `StandardsResolver` reads trigger rules (`ad_fp_trigger`) and compartment requirements (`ad_fire_compartment`) to decide WHAT to place. The BOM system decides HOW to place it. Code references (e.g., "UBBL By-Law 230") travel with elements for audit traceability.

**Lesson**: The progression from code to metadata to standards-as-data is the scaling path. Each phase boundary should ask: "Can this be a table row instead of a code change?"

## 2. The BOM Pattern as Universal Resolver

The Building-as-BOM concept (see `docs/BUILDING_AS_BOM_CONCEPT.md`) proved to be the single most powerful abstraction:

- **13 active AD tables** (of 35 total) drive element placement
- `ad_bom_child` recipes define parent-child assembly relationships
- `ad_bom_child_param` stores spatial offsets, Z-rules, placement walls
- `ad_space_type_mep_bom` maps room types to MEP requirements
- `ad_fp_trigger` maps building parameters to fire protection requirements

**Pattern**: Query the library, resolve placement from metadata, write geometry. Every new resolver follows this: `MEPBOMResolver`, `FurnitureBOMResolver`, `FloorPlateBOMResolver`, `StandardsResolver`.

## 3. Federation as Pattern Library

The enhanced federation database (`database/enhanced_federation_GI.db`) contains 17,000+ LOD400 components extracted from real BIM models. It serves as ground truth for:

- **Geometry**: Exact mesh data (vertices, faces) with hashes for dedup
- **Spatial relationships**: Element transforms give real-world positions
- **Assembly patterns**: How components cluster (desk + chair + monitor)
- **Standards compliance**: Fire detection spacing, pipe diameters, fixture counts

**Lesson**: Never invent placement patterns. Query the federation, measure the offsets, encode them as BOM parameters. The federation is always right; your intuition is often wrong.

## 4. Critical Traps and Pitfalls

### Geometry Axis Swaps (Phases 88, 97)
Library components have orientation metadata. When `orientationMatched=true`, the mesh's local axes align with world axes. When false, a 90-degree rotation swaps X and Y extents. Failing to check this causes protrusion failures (elements sticking through walls by 375-625mm).

### Z Double-Correction (Phase 92C)
`FurniturePlacer` pre-subtracts `localMinZ` from target Z. `BuildingWriter.writeFixture()` subtracts it again. Fix: pass raw target Z, let the writer handle correction. This class of bug (two systems both "helping") is the hardest to debug.

### skipKeywords Blindspot (Phase 86)
UNIT, CORRIDOR, and other keywords in `skipKeywords` prevent geometry generation. But DSL parsing still creates room objects — they just produce no walls, doors, or MEP. This is intentional (units are separately compiled), but causes confusion when counts don't match expectations.

### Grid Axes vs Spacing Mismatch (Phase 95B)
A DSL grid can have N axes but only N-2 spacing values. The extra trailing axis has no band. `gridInfoFromGridDef()` must trim axes to `spacing.size() + 1`.

### CladdingSpec Bounds Not Normalized (Phase 88)
West and south walls have `minX > maxX` in CladdingSpec. Always use `Math.min/Math.max` when building spatial indices.

### Stale Python Cache (Phase 90)
After editing Python files, delete `__pycache__/*.pyc`. Stale bytecode will run old code regardless of source changes.

## 5. Architecture Principles That Held

### Record-Based Data Model
Java records for specs (StoreySpec, AlarmSpec, etc.) enforce immutability. The "growing StoreySpec" pattern (22 fields at Phase 56 to 23 at Phase 100) works because backward-compatible constructors absorb change. New fields always default to `List.of()`.

### World-Space Geometry (Pattern B)
All geometry is written in world coordinates with zero transforms. This eliminates an entire class of bugs (transform stacking, coordinate space confusion) at the cost of larger geometry data. The tradeoff is worth it.

### Single-Connection Session
`ADSession` provides one JDBC connection per compilation. ThreadLocal access means nested methods don't need connection parameters threaded through signatures.

### Witness-First Development
The witness system (claims in JSON, verified by sanity checker) forces testable assertions before implementation. "Write the claim first" catches scope creep and ensures measurable progress.

## 6. What Would Be Different Next Time

1. **Start with BOM tables from Phase 1**, not Phase 50. The hardcoded era was necessary for learning, but the metadata pattern was obvious by Phase 20.

2. **Smaller commits**. Phases 93-95B were committed as one batch. Each phase should be independently committable — git bisect needs granular boundaries.

3. **Extract classes earlier**. BuildingCompiler reached 5000+ lines before Phase 93B split it into 4 files. The split was trivial and should have happened at 2000 lines.

4. **Federation-first for all geometry**. Every time we wrote parametric box fallbacks, we later replaced them with LOD400 library meshes. Skip the parametric step — import federation geometry on day one.

5. **Standards tables before features**. Phase 100's `StandardsResolver` should have been Phase 10. Building codes are the requirements; features are the implementation. Encoding requirements as queryable data makes compliance automatic rather than manual.
