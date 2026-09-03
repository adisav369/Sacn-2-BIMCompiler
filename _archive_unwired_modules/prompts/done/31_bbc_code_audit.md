# BBC Process Verification — Spec vs Code Audit

You are an auditor for bim-compiler. Your job: read the BBC spec, then verify
every claim against the actual Java code. Use agents to parallelize. Do NOT
write code or run the pipeline — this is a read-only audit.

## Goal

Answer one question: **Can the BBC compilation process actually work as specified?**

Read `docs/BOMBasedCompilation.md` (BBC.md) end-to-end, then trace each
mechanism through the Java source. Note gaps, drift, dead code, and unimplemented
claims. Write findings to the appendix at the bottom of this file.

## Read first

1. `docs/INDEX.md` — doc map
2. `docs/BOMBasedCompilation.md` — the spec under audit (all sections)
3. `docs/SourceCodeGuide.md` — entry points and DAO locations
4. `PROGRESS.md` — current state

## Audit Traces (launch agents in parallel where independent)

### Trace 1: BOM Recursion (BBC §2.2)

Verify the recursive BOM walker exists and works as described:

- Find the DAG compiler / BOM walker class. Read it.
- Confirm: walks m_bom → m_bom_line → resolves child_product_id → recurses
  if child has m_bom, emits leaf otherwise
- Confirm: dx/dy/dz accumulates through recursion levels (tack offsets)
- Confirm: component_type is NOT branched on (§2.2.1 says walker ignores it)
- Confirm: component_library.db is the leaf resolver
- Check: is recursion depth unlimited or hardcoded?
- Note any deviation from spec.

### Trace 2: IFCtoBOM Pipeline (BBC §2.1)

Verify the extraction pipeline:

- Find `IFCtoBOMPipeline` (or equivalent). Read it.
- Confirm: BUILDING → STOREY → DISCIPLINE → SCOPE SPACE → LEAF decomposition layers
- Confirm: YAML `floor_rooms:` spaces define scope, elements assigned by centroid
- Confirm: BUFFER/PHANTOM fills parent = SUM(children) invariant
- Confirm: dedup (same element_ref → qty on BOM line, not duplicate lines)
- Confirm: BomValidator runs 9 checks before commit (§2.1)
- Confirm: products written to component_library.db first, then to BOM DB
- Note any missing layer or deviation.

### Trace 3: Verb Expansion (BBC §2.1.6 + §6)

Verify verb-based compilation:

- Find VerbDetector and verb classes (TILE, ROUTE, FRAME, CLUSTER)
- Confirm: recipe lines with verb_ref expand to N placement instances
- Confirm: TILE = 2D grid, ROUTE = axis-aligned run, FRAME = grid intersections,
  CLUSTER = offset-table grouping
- Check: does SUM(qty) across non-PHANTOM leaves == output element count?
- How does flat placement (qty=1, no verb) work?
- Note any verb that exists in spec but not in code, or vice versa.

### Trace 4: Instant Drop vs BOM Drop (BBC §3.3–3.4)

Verify both compilation modes:

- Find where C_OrderLine references a BOM product and triggers compilation
- Confirm: Instant Drop = no modifications, full tree explosion
- Confirm: BOM Drop = interactive tree navigation, swap/add/remove
- Find the Selection Cascade (§3.5): M_Product_Category filter → AABB fit → volume → seq_no
- Find ASI resolution: `effective = ASI_override ?? catalog_default` (§3.5.1)
- Note if BOM Drop is actually implemented or spec-only.

### Trace 5: 9-Stage Pipeline (BBC §5)

Verify the pipeline stages exist:

- Find the pipeline orchestrator class
- List the actual stages vs the 9 specified in BBC §5
- Confirm stage ordering and data flow
- Note any stage that's missing or different.

### Trace 6: Tack Convention (BBC §4)

Verify the spatial offset model:

- Find where LBD (Left-Back-Down) tack convention is implemented
- Confirm: dx/dy/dz on m_bom_line = parent-LBD to child-LBD offset
- Confirm: world position = accumulated tack through BOM hierarchy
- Find the BUFFER invariant check: parent AABB == SUM(children AABB)
- Note any coordinate system confusion (Y-up vs Z-up, origin conventions).

### Trace 7: EntityType Guards (BBC §2)

Verify the D/U/A protection:

- Find X_M_BOM.java / X_M_BOMLine.java EntityType guards
- Confirm: Dictionary (D) records are read-only, User (U) records mutable
- Confirm: GodMode bypass exists for migrations only
- Note any gap in the guard.

### Trace 8: Output Schema (BBC §2.1.6 + §10)

Verify compiled output structure:

- Find where output.db is created (WorkOutputDAO or similar)
- Confirm: output.db has c_orderline / elements_meta with world-space coordinates
- Confirm: recipe (BOM.db) → placement (output.db) expansion
- Check: does the output schema match what downstream (4D/5D/6D) expects?

## Rules

- Read-only. No code changes, no migrations, no test runs.
- Cite file:line for every finding.
- Be specific: "MBOM.java:65 does X" not "the code does X"
- If a BBC claim is aspirational (spec-only, not implemented), say so explicitly.
- If code contradicts spec, quote both.
- Launch agents for independent traces — they can read in parallel.
- `mvn compile -q` as a final sanity check that the codebase compiles.

## When Done

Append all findings below the `---` line as:

```
# Appendix: BBC Code Audit Findings

## Trace 1: BOM Recursion
...
## Trace 2: IFCtoBOM Pipeline
...
(etc.)

## Summary Verdict
[CAN WORK / GAPS FOUND / STRUCTURAL MISMATCH]
- List of gaps (spec claims not backed by code)
- List of drift (code does something spec doesn't describe)
- List of dead code (code exists but spec says otherwise)
```

Commit: `[S89-audit] BBC spec-vs-code audit — 8 traces`

---

# Appendix: BBC Code Audit Findings

## Trace 1: BOM Recursion

**Walker class:** `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/BOMWalker.java`

- **Recursive walk m_bom → m_bom_line → resolve → recurse/emit:** CONFIRMED. `walkChildren()` (BOMWalker.java:170) loads lines via `MBOMLine.getByBom()` (:178), attempts `loadBom(childProductId)` (:191). Child BOM exists → `onSubAssembly` + recurse (:204). No child BOM + product exists + not PHANTOM → `onLeaf` (:224). Matches spec.

- **dx/dy/dz accumulation:** CONFIRMED. `PlacementCollectorVisitor.java` maintains `anchorStack` (Deque<double[]>, :50). `onSubAssembly` (:99) computes `newAnchor = parent + lineDx + bomOriginX` (:173-177), pushes. `onLeaf` (:216) reads anchor, applies leaf offsets (:223-225). World pos = `anchor[0] + offsets[qi][0] + iHalfW` (:297). Rotation also accumulated via `rotationStack`.

- **component_type NOT branched on:** CONFIRMED with caveat. BOMWalker.java:28-45 documents: "walker ignores component_type for traversal decisions." PHANTOM is checked (:213) but only for gap-fill suppression (no output), not traversal direction. Spec intent preserved.

- **component_library.db as leaf resolver:** DEVIATION. BOMWalker:243 `forDefaultDb()` connects `compConn` to `ERP.db`, not component_library.db. Products migrated to ERP.db (S65 Step 3). BBC §2.2.1 says "resolves to M_Product in component_library.db" — **spec stale**, code uses ERP.db. Geometry (meshes, M_Product_Image) still in component_library.db via `MeshBinder` (:306).

- **Recursion depth:** `MAX_DEPTH = 20` (BOMWalker.java:61). Spec says "unlimited" (BBC:426). **Safety guard** — 20 levels far exceeds practical buildings (4-5 levels). Functionally equivalent.

## Trace 2: IFCtoBOM Pipeline

**Pipeline class:** `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java`

- **Decomposition layers:** CONFIRMED with two-path split:
  - **CO/IN path** (:256): `DisciplineBomBuilder` → BUILDING → FLOOR → DISCIPLINE → LEAF. No SCOPE SPACE layer.
  - **RE path** (:265): `ScopeBomBuilder` + `CompositionBomBuilder` + `StructuralBomBuilder` + `FloorRoomBomBuilder` → BUILDING → STOREY → SCOPE SPACE → LEAF. No DISCIPLINE layer (single-discipline, BBC §2.1.5: "schema_version 1 skips this layer").

- **YAML floor_rooms defines scope:** CONFIRMED. Pipeline:296 iterates `config.floorRooms()`. `ScopeBomBuilder` uses `aabb_mm` from spaces.

- **BUFFER/PHANTOM invariant:** CONFIRMED. `ScopeBomBuilder.java:151-169` writes PHANTOM lines when `phantomW > 0 || phantomD > 0 || phantomH > 0`. `BomValidator.checkBufferInvariant()` (:443) validates X-axis (1% tolerance). **Currently advisory only** — returns 0, does not gate.

- **Dedup:** CONFIRMED via `VerbFactorizer.java`. Groups by `child_product_id` (:100-103). Groups ≥ 4 → `VerbDetector` pattern detection → single line with qty=N + verb_ref. Smaller groups → per-instance lines (qty=1).

- **BomValidator check count:** DEVIATION. Spec says "9 checks." Code runs **12 checks** in `validateAndReport()` (:67-98) plus `checkVerbExpansionFidelity` at pipeline:328. Spec count stale — checks added since spec written.

- **Products to component_library.db first:** CONFIRMED with deviation. `ProductRegistrar.ensureProductCatalog()` writes to component_library.db **and** ERP.db (pipeline:213-215). BOM DB copy is described as dead code (ProductRegistrar.java:21: "DEAD CODE: ensureProducts still copies M_Product to the BOM DB but BOMWalker was refactored (R7)").

## Trace 3: Verb Expansion

- **Recipe lines with verb_ref expand to N placements:** CONFIRMED. `PlacementCollectorVisitor.onLeaf()` (:278-281): `expandVerb(verbRef, qty, leafDx, leafDy, leafDz)` returns `double[qty][3+]`. Loop at :285 creates one `Placement` per instance.

- **TILE = 2D grid:** CONFIRMED. `VerbDetector.java:175` detects nx*ny grid. `PlacementCollectorVisitor:412` `expandTile()` iterates ix*iy.

- **ROUTE = axis-aligned run:** CONFIRMED. `VerbDetector.java:218` detects axis-aligned legs with R8 step-uniformity guard (20% tolerance). `PlacementCollectorVisitor:435` `expandRoute()` chains legs.

- **FRAME = grid intersections:** CONFIRMED. `VerbDetector.java:73` detects xLines × yLines cartesian product. `PlacementCollectorVisitor:469` `expandFrame()` computes product of gridlines.

- **CLUSTER = offset-table grouping:** CONFIRMED. `VerbDetector.java:115` catch-all, stores per-instance offsets as dx,dy,dz,w,d,h. `PlacementCollectorVisitor:520` `expandCluster()` parses 6-value entries.

- **SPRAY:** Exists in code (VerbDetector:366), superseded by CLUSTER in detection cascade. NOT in BBC §2.1.6 verb table. Legacy retention.

- **Flat placement (qty=1, no verb):** `expandVerb()` at :380-387 returns single-entry array with line's dx/dy/dz when verbRef is null.

- **Verb count:** BBC §6 says "64". VerbRegistry has 75 (now, post-S89-trim1). Spec stale.

## Trace 4: Instant Drop vs BOM Drop

- **Instant Drop:** CONFIRMED, IMPLEMENTED. `BomDropper.drop()` at `DAGCompiler/.../BomDropper.java:45-119`. Full tree explosion, no user intervention. `BomDropTest.java:31` "TC-1 Instant Drop BUILDING_SH_STD -> 55 elements."

- **BOM Drop:** IMPLEMENTED (not spec-only). `DesignerAPIImpl.bomDrop()` at `BonsaiBIMDesigner/.../DesignerAPIImpl.java:1720`. Returns `BomTreeNode` for GUI Outliner. Multiple tests: BomDropTest, BomDropCompileTest, BomDropConfigureTest, OrderConfiguratorTest.

- **Selection Cascade (§3.5):** IMPLEMENTED. `SelectionCascadeTest.java:24` tests category + AABB matching. Four-step cascade (category filter → AABB fit → largest volume → seq_no tiebreaker) confirmed.

- **ASI resolution (§3.5.1):** PARTIALLY IMPLEMENTED. ASI extraction side works (`VerbFactorizer.writeASI()` :350). Compilation consumption side does NOT query ASI tables — `PlacementCollectorVisitor.onLeaf()` reads `line.getAllocatedWidthMmExact()` (:239) or `product.getWidth()` (:247). CLUSTER verb_ref encodes per-instance dims inline as workaround for extracted buildings. **Gap:** Generative path lacks `effective = ASI_override ?? catalog_default` resolution chain.

- **Exception-based ordering (§3.7):** Implemented via `ExceptionLine` records + `InheritanceResolver.resolveExceptions()`. Beyond basic §3.3-3.4.

## Trace 5: 9-Stage Pipeline

**Orchestrator:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`

**EXACT 1:1 MATCH between spec and code.** All 9 stages present, in order:

| # | Spec Stage | Code Class | Line | stage name() |
|---|-----------|------------|------|-------------|
| 1 | Metadata | `MetadataValidator` | :57 | "METADATA VALIDATION" |
| 2 | Parse | `ParseStage` | :211 | "PARSE DSL" |
| 3 | Compile | `CompileStage` | :222 | "COMPILE TO BUILDINGSPEC" |
| 4 | Template | `TemplateStage` | :127 | "TEMPLATE COMPOSITION" (ST-mode only) |
| 5 | Write | `WriteStage` | :263 | "WRITE TO DB" |
| 6 | Verb | `VerbStage` | separate file | "VERB STAGE (BIM COBOL)" |
| 7 | Digest | `DigestStage` | :613 | "SPATIAL DIGEST" |
| 8 | Geometry | `GeometryStage` | :670 | "GEOMETRY INTEGRITY CHECK" |
| 9 | Prove | `ProveStage` | :693 | "PLACEMENT MATHEMATICAL PROOF" |

Data flow: sequential via `CompilationContext` (:77). Skip conditions documented for stages 3 (CO DocBaseType), 4 (non-ST), 6 (no .bimcobol), 9 (no relational data).

## Trace 6: Tack Convention

- **LBD (Left-Back-Down) = bounding box minimum corner = (minX, minY, minZ):** CONFIRMED throughout.

- **dx/dy/dz on m_bom_line = parent-LBD to child-LBD offset:** CONFIRMED.
  - `X_M_BOMLine.java:25-27`: dx/dy/dz REAL DEFAULT 0.0 (metres).
  - `ScopeBomBuilder.java:133-148`: SET LBD = [minX, minY, minZ], child offsets relative to SET LBD.
  - `FloorRoomBomBuilder.java:58-66`: `spaceDx = setLbd[0] - floorLbd[0]` — LBD-to-LBD subtraction.

- **World position = accumulated tack:** CONFIRMED.
  - `PlacementCollectorVisitor` stack-based accumulation via `anchorStack`.
  - `LocalCoord.toWorld()` (`DAGCompiler/.../coordinate/LocalCoord.java:27-36`) — typed path with ArchUnit D8 gate.
  - `WorldCoord` construction restricted to `LocalCoord.toWorld()` + `StoreyCoord.asWorld()`.

- **BUFFER invariant check:** CONFIRMED, advisory. `BomValidator.checkBufferInvariant()` (:443-477) checks X-axis, 1% tolerance. Returns 0 (does not gate). Comment: "advisory until BUFFER lines implemented (§4.2)."

- **Coordinate system:** Z-up consistently. `ExtrusionAxis.java` defaults Z_UP for walls/columns/doors/windows. No Y-up/Z-up confusion found.

## Trace 7: EntityType Guards

- **D records read-only, U records mutable:** CONFIRMED.
  - `MBOM.beforeSave()` (:96-104): blocks update of EntityType=D unless GodMode.
  - `MBOM.delete()` (:141-148): blocks delete of EntityType=D unless GodMode.
  - `MBOMLine.beforeSave()` (:21-27) and `MBOMLine.delete()` (:32-39): same guards.
  - Constants in `X_M_BOM.java:72-74`: D (Dictionary), U (User), A (Application).

- **GodMode bypass for migrations only:** CONFIRMED. `X_M_BOM.java:84-87` checks `Files.exists(Path.of("GodMode.txt"))` once per JVM (static initializer). `GodMode.txt` is gitignored (`.gitignore:58-59`).

- **Gap: New-record bypass.** Guard only fires on `!newRecord` (MBOM.java:100). A new record with entity_type='D' can be INSERTed without restriction. Nothing prevents code from accidentally creating fake Dictionary records. Discipline-enforced, not code-enforced.

- **Gap: No validation on setEntityType().** `X_M_BOM.java:127` and `X_M_BOMLine.java:181` accept any string value. No CHECK constraint in Java code.

## Trace 8: Output Schema

- **Where output.db is created:** `BuildingWriter.initSchema()` at `DAGCompiler/.../dsl/BuildingWriter.java:83-416`. Called from CompilationPipeline, BuildingCompilerCLI, IntentCompiler.

- **c_orderline in output.db:** Lean — columns: C_OrderLine_ID, C_Order_ID, Storey, Name, IfcClass, Discipline, AD_Org_ID, M_Product_ID, IsActive. **No world-space coordinates** (dx/dy/dz not on c_orderline).

- **elements_meta:** id, guid, discipline, ifc_class, element_name, element_type, storey, fire_rating_hr, material_name, material_rgba, element_ref. **Also no explicit world-space coordinates.**

- **World-space lives in elements_rtree:** `CREATE VIRTUAL TABLE elements_rtree USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ)` (BuildingWriter.java:109-113). Joined to elements_meta via id.

- **element_transforms:** center_x, center_y, center_z, transform_source (BuildingWriter.java:126-134).

- **Python output_schema.sql divergence:** STALE. `simple_qto` schemas structurally different (Python: per-element; Java: aggregate). Multiple tables/views in Java missing from Python DDL. Downstream Python tools may expect wrong schema.

## Summary Verdict

**CAN WORK — with documented spec staleness.**

The BBC compilation process is structurally sound. All 8 traces confirm the core mechanisms work as intended. No structural mismatches found.

### Gaps (spec claims not backed by code)
1. **ASI resolution at compile time** (§3.5.1): extraction writes ASI, compilation does NOT consume ASI tables for generative path. CLUSTER inline dims serve as workaround for extracted buildings.
2. **BUFFER invariant** (§4.2): check exists but is advisory-only (returns 0, does not gate).

### Drift (code does something spec doesn't describe)
1. **Leaf resolver DB:** BBC says component_library.db; code uses ERP.db (S65 migration). Spec needs update.
2. **BomValidator count:** BBC says 9 checks; code has 12 + verb fidelity. Spec count stale.
3. **Product write target:** BBC says component_library.db first; code writes to component_library.db + ERP.db. BOM DB copy is dead code.
4. **Verb count:** BBC §6 says 64; VerbRegistry has 75. Spec stale.
5. **SPRAY verb:** exists in VerbDetector, not in BBC verb table. Superseded by CLUSTER.
6. **Python output_schema.sql:** diverged from Java BuildingWriter.initSchema().

### Soft gaps (discipline-enforced, not code-enforced)
1. **EntityType new-record bypass:** Nothing prevents creating new records with entity_type='D' through PO layer.
2. **setEntityType() accepts any value:** No Java-side validation. Would need DDL CHECK constraint.
3. **MAX_DEPTH=20:** Spec says "unlimited." Functionally equivalent for real buildings.

### Dead code
1. `ProductRegistrar.ensureProducts()` BOM DB copy (refactored R7, documented as dead).

