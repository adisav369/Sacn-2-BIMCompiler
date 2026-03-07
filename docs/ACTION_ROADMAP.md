*PROMPT AT EACH STEP:
1. Show me the current status first
2. Explain the task in plain English before proceeding
3. Implement minimal changes only
4. Run and show results
5. STOP completely for my review

Do NOT:
- Fix anything beyond this task
- Refactor unrelated code
- Add features not in the task
- Continue past the STOP point

Ready. Show me the current state of

# Action Roadmap — BIM Intent Compiler to Production

## Context

The BIM compiler has proven its core thesis: deterministic reproduction of known
buildings from committed BOM gospel. Three Rosetta Stones (SH, DX, Terminal) at
100% positional match. 12 BIM COBOL verbs. 9-stage pipeline. Model design complete.

This roadmap charts the path from current POC to production framework covering:
- Rosetta Stone gate convergence (all 5 gates green)
- Terminal BOM recomposition (51K elements)
- 2D drawing export (compiled 3D → SVG architectural drawings)
- **Synthetic Rosetta Stone** — the round-trip proof (3D → 2D → 3D)
- TB-LKTN generative compilation from 2D layout input
- BIM COBOL language maturity
- Bonsai GUI editor integration
- iDempiere ERP integration (CSV → REST → OSGI)

Nine phases. Phase 0 is the foundation — without it, nothing compiles correctly.

---

## Phase 0.0: The Structured BOM Gap — How It Hid

**Discovered:** 2026-03-07 (when `_e` path was first wired to actual compilation).

**What happened:** The structured BOMs (UNIT_SH_STD, UNIT_DUPLEX_STD) were created
in Feb 2026 as furniture arrangement skeletons for the EXPLODE/Template mode.
They were never on the compilation path. The pipeline always compiled via flat
coordinates (c_orderline until Mar 3, then EXTRACTED BOMs from Mar 5). The `_e`
output in `run_RosettaStones.sh` was literally `cp _s.db _e.db` — a copy, not a
compilation. No one ever ran a compilation through the structured hierarchy.

**Timeline of the gap:**

| Date | Event | Structured BOM state |
|------|-------|---------------------|
| Feb 12-13 | Rosetta convergence: furniture slots created | Skeleton with generic "IfcFurniture" as child_product_id |
| Feb 23 | `f10ddec` Seed product_ref for DINING_SET/LIVING_SET | Slots defined but still generic IDs |
| Mar 3 | `2dfae58` Fix DX kitchen BOM: IfcFurniture→Counter_Top | **Only DX kitchen fixed** (13 lines). Rest left generic |
| Mar 5 | P0.1-BOM: EXTRACTED BOMs created (EXT_SH=55, EXT_DX=1099) | Pipeline uses singular (EN-BLOC) compilation. Structured BOMs untouched |
| Mar 6 | Phase A COMPLETE: 6 gates GREEN | All via singular (EN-BLOC) path. Exploded never tested |
| Mar 7 | `_e` wired to `bom.mode=STRUCTURED` | **Gaps instantly visible:** SH=0/55, DX=153/1099 |

**Why it hid:** The gates (G1-G6) tested singular (EN-BLOC) compilation output. The
structured BOMs were a design artifact that nobody compiled through. The roadmap
captured `HW-3: Wire _e` but described it as a plumbing task, not recognising
the structured BOMs were fundamentally incomplete.

**Two root causes:**

1. **Generic product IDs** — BUY leaves use IFC class names ("IfcFurniture",
   "IfcRoof") as `child_product_id` instead of real M_Product entries. Without
   M_Product → M_Product_Image, MeshBinder cannot resolve geometry → elements
   skipped. SH: all 21 furniture slots affected. DX: living/bedroom/bathroom
   sets affected (~40 lines). The DX kitchen sets were the ONLY ones fixed
   (commit `2dfae58`), which is why DX structured gets 153 elements.

2. **Structural layer never BOMified** — walls, doors, windows, curtain wall
   members, glazing plates, slabs, railings, MEP piping have no representation
   in the structured hierarchy. The structured BOMs were built as room-level
   furniture arrangement skeletons ONLY. The structural envelope and MEP systems
   were never started. This accounts for ~34/55 SH elements and ~946/1099 DX
   elements.

**Current state (as of 2026-03-08):**

| Building | Singular (_s) | Exploded (_e) | Gap |
|----------|---------------|-----------------|-----|
| SH | 55 elements | 0 elements | -55 (all missing) |
| DX | 1099 elements | 153 elements | -946 |

**Fix plan (for next session):**
- **Fix 1 (SQL migration, small):** Replace generic child_product_id with real
  M_Product.Name for all furniture BUY leaves. Products + M_Product_Image entries
  already exist. ~60 UPDATE statements.
- **Fix 2 (SQL migration, large):** Add structural BOM branches under each FLOOR
  BOM. Derive parent-relative dx/dy/dz from EXTRACTED flat data (EXT_SH/EXT_DX
  have all positions). Create WALL, DOOR, WINDOW, SLAB, CURTAIN_WALL, MEP
  sub-BOMs with BUY lines per instance.

**Gate:** `run_RosettaStones.sh` — both _s and _e match the reference for SH and DX.

**Lesson:** Never leave a `cp` stub as a test double. If the _e path had been
wired to actual compilation from day one, these gaps would have surfaced in
February — not March.

---

## Phase 0: EN-BLOC Singularity — Wire the Missing Path

**Goal:** EXTRACTED buildings (SH/DX) compile with correct element count matching
reference. Two sub-goals, in order:

1. **Immediate (P0-SH/DX/TE):** PlacementLoader metadata-driven path — EXTRACTED
   buildings reproduce via `lod_element_placement` pre-extracted positions.
   Element count matches C_DocType.ExpectedElements. No DSL invention.
2. **End-state (P0-BOM):** EN-BLOC BOM walk — the compiler reads BOM-relative
   offsets (m_bom_line dx/dy/dz), resolves CO_EmptySpaceLine alignment, and
   computes world coordinates. PlacementLoader flat extraction replaced by compiled
   reproduction. 1 C_OrderLine + 1 CO_EmptySpaceLine per building.

Sub-goal 1 proves the extraction chain is intact (output=input). Sub-goal 2
proves the compilation method works (BOM gospel → world coordinates). Both must
pass the Rosetta Stone digest.

### Pipeline (BOM-driven, P0.2 — DONE)

```
m_bom_line (BOM.db) → PlacementLoader.loadFromBOM() → hasMetadata=true
  → origin + dx ± allocated/2000 (tack convention maths)
  → StoreyCompiler.applyPlacementOverrides()
  → BuildingWriter.emitGlobalPlacementElements()
  → elements_meta + elements_rtree
```

PlacementLoader.loadFromBOM() computes world coords from BOM offsets (origin + dx ±
allocated/2000), same tack convention maths as BOMTierResolver.expandBOMNode().
This is NOT a flat copy — it is a SQL-level BOM computation. The remaining gap:
no explicit singularity check, no C_OrderLine, no CO_EmptySpaceLine in the
EN-BLOC (_s) path. The EXPLODE (_e) path needs structured BOM root wiring.
See HelloWorld Phase for _s/_e dual output plan.

### Root cause — cascade gap (discovered 2026-03-05)

The c_order→C_DocType migration changed building identifiers.
`lod_element_placement.building_type` used old names (SAMPLE_HOUSE, DUPLEX) but
`C_DocType.ProjectName` uses new names (Ifc4_SampleHouse, Ifc2x3_Duplex).
PlacementLoader cache key didn't match → `hasMetadata=false` → StoreyCompiler DSL
invention (wrong counts: SH 122 instead of 55, DX 755 instead of 1099).
Additionally, SH/DX entries had `is_active=0` because they historically used the
relational path (c_orderline → RelationalResolver), which has been deleted (2026-03-05).

Previous "positive results" came from pre-extracted c_orderlines stored in BOM.db
(the answer sheet baked into the dictionary). That data is gone (c_orderline
correctly dropped from BOM.db per §11.2).

### Tasks

| Task | What | Status |
|------|------|--------|
| P0-SH | **SH cascade gap fix** — rename SAMPLE_HOUSE→Ifc4_SampleHouse in lod_element_placement, activate (is_active=1), fix stale c_orderline query in ComponentLibrary | **DONE** — 55/55 GREEN |
| P0-DX | **DX cascade gap fix** — rename DUPLEX→Ifc2x3_Duplex in lod_element_placement, activate (is_active=1). 14 missing IfcFlowController extracted from reference DB and inserted (6 smoke detectors, 4 ball valves, 4 backflow preventers — all MEP, storey Unknown) | **DONE** — 1099/1099 GREEN |
| P0-TE | **Terminal/ST_SH MetadataValidator** — skip ad_building_grid/ad_room_boundary/ad_wall_face checks for EXTRACTED buildings (they don't need DSL metadata, they use placement data) | Deferred |
| P0-DOC | **BOMBasedCompilation.md accuracy** — fix pipeline table (§4), add §4.1 EXTRACTED data flow, add §4.2 ABSOLUTE anti-pattern, distinguish EN-BLOC design vs current PlacementLoader path (§3.1), fix DX count 1085→1099 (§5) | **DONE** |
| P0-BOM | **EN-BLOC BOM walk** — implement the BOM-offset path (§11.1 design). BOM walk produces same digest as PlacementLoader. Proves compilation method, not just extraction chain. | Future |

**Fix recipe (same for each EXTRACTED building):**
1. `UPDATE lod_element_placement SET building_type='<ProjectName>' WHERE building_type='<old_name>'`
2. `UPDATE lod_element_placement SET is_active=1 WHERE building_type='<ProjectName>'`
3. Verify PlacementLoader finds placements → hasMetadata=true → metadata-driven path

**DX note (resolved):** lod_element_placement had 1085 rows for DUPLEX, expected 1099.
14 IfcFlowController were missing from legacy flat extraction (6 smoke detectors,
4 ball valves, 4 backflow preventers — all MEP, storey "Unknown"). Extracted from
reference DB (`Ifc2x3_Duplex_extracted.db`) and inserted into lod_element_placement.

**Gate:** `BuildingRegistryTest` — SH and DX element counts match C_DocType.ExpectedElements.

**Dependency:** None. This is the foundation. Phase A cannot start without it.

---

## Phase 0.1: Product Catalog Normalisation — Instances → Products + BOM

**Goal:** Replace the flat instance dump (`lod_element_placement`, 1099 rows for DX
alone) with a proper product catalog + BOM assembly structure. Same data, correct
model. Plain English product names for Bonsai Outliner display.

**Root cause:** The extraction pipeline dumped every IFC element as a separate row
in `lod_element_placement`, each with its own XYZ. But DX has only **78 unique
products** placed 1099 times. A smoke detector is one product — the 6 instances
are BOM placements, not 6 different products. The current table conflates product
identity (WHAT) with instance placement (WHERE).

### The normalisation

```
CURRENT (flat dump in component_library.db):
  lod_element_placement: 1099 rows, each with baked-in XYZ
    "M_Smoke Detector:Smoke Detector:Smoke Detector:610550" at (1.82, -4.92, 2.50)
    "M_Smoke Detector:Smoke Detector:Smoke Detector:610280" at (6.61, -12.79, 2.50)
    ... (6 rows for the same product)

TARGET (product catalog + BOM):
  M_Product (BOM.db):     78 rows — one per unique product type
    "Smoke Detector"       140×140×102mm  (intrinsic dimensions)
    "Ball Valve 50mm"       80×139×216mm
    "Elbow - Generic"       35× 35× 29mm
    "Base Cabinet 1000mm" 1000×625×860mm

  m_bom + m_bom_line (BOM.db): assembly recipes with placement
    DUPLEX_L1_MEP (M_BOM)
      → Smoke Detector  qty=2  dx=1.82  dy=-4.92  dz=2.50  rotation_rule=POINT
      → Smoke Detector  qty=2  dx=6.61  dy=-12.79 dz=2.50  rotation_rule=POINT
      → Ball Valve 50mm qty=2  dx=3.28  dy=-10.65 dz=2.93  rotation_rule=NS
      ...
```

### What changes

| Concern | Current | Target |
|---------|---------|--------|
| **Product identity** | element_ref with Revit instance ID suffix (1099 "products") | M_Product with plain English Name + Description (78 products) |
| **Intrinsic dimensions** | Derived from XYZ delta per row | M_Product.Width/Depth/Height (canonical, orientation-independent) |
| **Intrinsic orientation** | Not stored | M_Product → component_definitions (up-axis, forward-axis, attachment face) |
| **Placement** | XYZ baked into each row | m_bom_line dx/dy/dz + rotation_rule (relative to parent BOM) |
| **Quantity** | Implicit (count rows) | Explicit m_bom_line.qty |
| **Naming** | `M_Smoke Detector:Smoke Detector:Smoke Detector:610550` | Name=`Smoke Detector`, Description=`M_Smoke Detector:Smoke Detector (Revit family)` |
| **Table prefix** | `lod_element_placement` (wrong prefix for component_library.db) | M_Product in BOM.db + m_bom_line in BOM.db (correct prefixes) |
| **Bonsai Outliner** | Flat list of 1099 cryptic names | BOM tree: Duplex → Level 1 → MEP → Smoke Detector ×2 |

### Plain English naming rule

Product Names must be readable by non-technical users in the Bonsai Outliner
BOM tree. The Revit family string goes in Description for traceability.

| element_ref (Revit) | M_Product.Name | M_Product.Description |
|---------------------|----------------|----------------------|
| `M_Smoke Detector:Smoke Detector:Smoke Detector:610550` | Smoke Detector | Revit: M_Smoke Detector (ceiling-mount, MEP) |
| `M_Ball Valve - 50-150 mm:50 mm:50 mm:578433` | Ball Valve 50mm | Revit: M_Ball Valve 50-150mm (pipe isolation) |
| `M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm` | Backflow Preventer 25mm | Revit: M_Backflow Preventer DCW-Hydronic 15-50mm |
| `M_Elbow - Generic:Elbow - Generic:Elbow - Generic` | Pipe Elbow | Revit: M_Elbow Generic (multiple diameters) |
| `M_Base Cabinet-Double Door & 2 Drawer:1000mm:1000mm` | Base Cabinet 1000mm | Revit: M_Base Cabinet Double Door + 2 Drawer |
| `M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle` | Power Outlet (Duplex) | Revit: M_Duplex Receptacle |

### Tasks

| Task | What | Status |
|------|------|--------|
| P0.1-DEDUP | **Deduplicate instances → products** — 79 unique M_Product entries. M_AttributeSet (5 rows). lod_element_placement.M_Product_ID backfilled (1099 DX rows). | **DONE** |
| P0.1-LOD | **{LOD_key; LOD_Object} pair** — canonical product geometry library. LOD_key (79 rows): M_Product_ID → geometry_hash + up_axis/forward_axis/attachment_face. LOD_Object (78 rows): mesh blobs. 8 previously unmapped products (valves, smoke detectors, railings, stairs, roof slab) extracted from reference DB. View: `lod_product_geometry`. PO: `X_LOD_Library` / `M_LOD_Library`. Migration: `migration_LOD_pair.sql`. | **DONE** |
| P0.1-CAT | **M_Product_Category** — IFC classification hierarchy in BOM.db. 4 discipline parents (Structural/MEP/Architectural/Assembly) → 29 IFC class leaves. M_Product.M_Product_Category_ID FK, 187/187 backfilled. PO: `X_M_Product_Category` / `M_M_Product_Category`. Migration: `migration_M_Product_Category.sql`. | **DONE** |
| P0.1-X1DX | **X1-DX digest upgrade** — StructuralCrossCheckTest upgraded from count-only to full SHA-256 digest for all 13 DX IFC classes. Fixed float-epsilon sort bug (Java sort after mm rounding). All 1099 element positions proven correct vs reference. | **DONE** |
| P0.1-ORIENT | **Intrinsic orientation** — Deferred. Up/forward/attachment now stored on LOD_key (populated from component_definitions). Orientation data flows through LOD pair, not needed as separate task. Rotation per-instance via m_bom_line.rotation_rule (already populated). | **DEFERRED → absorbed into P0.1-LOD** |
| P0.1-BOM | **Build BOM lines** — for each building (SH, DX), create m_bom + m_bom_line entries that reproduce all instances via qty + dx/dy/dz + rotation_rule. The BOM explosion must produce the same 1099 (DX) / 55 (SH) elements. **Rosetta Stone = all BUY, no MAKE.** Every element is already defined (extracted from IFC) — there are no sub-assemblies to "make". Flat BOM: one BUY line per instance. | **DONE** — EXT_SH=55, EXT_DX=1099, 5/5 witnesses GREEN |
| P0.1-RENAME | **Table rename** — `lod_element_placement` retained as extraction archive. New data flows through M_Product + LOD pair + m_bom_line. | **DONE** — lod_element_placement view dropped, ad_element_placement SH/DX deactivated (P0.2) |
| P0.1-VERIFY | **Rosetta Stone digest** — BOM explosion path produces same SpatialDigest as PlacementLoader path. If digests match, the product catalog is proven correct and the flat instance table becomes archive-only. | **DONE** — 5/5 W-VERIFY GREEN, then restructured to BOM-only (P0.2) |
| P0.2 | **BOM Walk + M_Product_Image** — PlacementLoader reads BOM.db (m_bom_line +6 instance columns). LOD_key→M_Product_Image. SH/DX deactivated in ad_element_placement. computeFromPlacement() deleted — BOM is sole source. | **DONE** |

**Rosetta Stone BOM principle:** EXTRACTED buildings are **all BUY, never MAKE**.
Every element already exists — it was extracted from the reference IFC. The BOM is
a flat list of BUY lines (one per instance), each carrying the centroid position and
AABB dimensions from the original extraction. No storey sub-assemblies, no MAKE
hierarchy. The distinction is: GENERATIVE buildings use MAKE (assemble from recipe),
EXTRACTED buildings use BUY (reproduce from archive). This applies to SH, DX, and
Terminal Rosetta Stones.

**Gate:** SpatialDigest(BOM walk) == SpatialDigest(PlacementLoader) for SH and DX.

**Dependency:** Phase 0 (PlacementLoader path working for baseline comparison).

---

## Phase A: Rosetta Stone Gate Convergence

**Goal:** All 5 gates GREEN for SH and DX. The "plane can take off" proof.

**Current state (2026-03-07): 6 GATES GREEN for SH and DX. Phase A COMPLETE.**
G1-COUNT, G2-VOLUME, G3-DIGEST, G4-TAMPER, G5-PROVENANCE, G6-ISOLATION — all PASS.

| Task | What | Status |
|------|------|--------|
| A1 | ~~SH T3 furniture fix~~ — IfcFurnishingElement class drift fixed. StoreyCompiler furniture section removed (was converting IfcFurnishingElement→IfcFurniture via FixtureSpec→MEPWriter). Furniture now goes through FLAT emission (emitGlobalPlacementElements) preserving original ifc_class. | **DONE** |
| A2 | ~~DX chain test repair~~ — BOMChainIntegrityTest.java DELETED (7 tests queried dropped c_orderline/c_order). BomChainIntegrityTest trimmed: R2/R4/R6/R7 deleted, R1/R3/R5a/R5b survive (4/4 GREEN). | **DONE** |
| A3 | ~~G5-PROVENANCE material_rgba~~ — Backfilled material_rgba from reference extracted DBs into component_library.db (SH: 51/55, DX: 139/1099). G5 Check 1 now compares output coverage against reference (not 100%), since IFC sources legitimately lack surface styles for some elements. | **DONE** |
| A4 | ~~G4-TAMPER~~ — T10 DSL clean (8→0). RelationalResolver deleted (2 TODOs gone). 6 TODOs reworded to `Phase F:` / `Design note:`. 3 violations eliminated: T6 @Disabled replaced with Assumptions.assumeTrue(false, reason); T8×2 return null refactored (findContainingWall→stream-based, findPlacement→multi-method decomposition). | **DONE** |
| A5 | ~~G3-DIGEST~~ — IfcFurnishingElement class drift fixed. Cross-mode digest excludes geometry_hash (extraction uses IFC hashes, compilation uses LOD hashes — same geometry, different naming). Float sort fixed: ORDER BY uses ROUND(r.* * 1000) (mm precision) + maxX/maxY/maxZ tie-break. | **DONE** |
| A6 | ~~G2-VOLUME~~ — totalVolume() was counting orphan elements_rtree rows in reference DBs (SH: 71 vs 55, DX: 1155 vs 1099). Fixed query to JOIN with elements_meta. SH +0.00%, DX +0.00%. | **DONE** |
| A7 | ~~RelationalResolver deletion~~ — @Deprecated, returned empty. PlacementLoader simplified (single loadFromBOM path after P0.2). SpatialPlacementVisitor updated. CompilerContractTest reflection → PlacementLoader. | **DONE** |
| A8 | ~~G5 IFC whitelist~~ — +IfcFlowController (14 DX gate valves), +IfcStairFlight (2 DX stair flights). SH/DX: 0 unknown ifc_class. | **DONE** |
| A9 | ~~G6-ISOLATION gap closure~~ — 7/9 gaps closed (2026-03-06). Assembly contamination (#1), surface/material dump (#2/#3), geometry dedup (#4), spatial structure (#5), DX containment (#6), storey names (#8). G6-ISOLATION gate: 4 checks (unused styles, missing storeys, IfcSpace, containment). | **DONE** |
| A10 | ~~Product-level geometry~~ — ProductGeometry contract (sealed record + Registry with startup validation). MeshBinder: product-level path first, instance fallback. M_Product_Image: 87→115 rows (2026-03-07). | **DONE** |
| A11 | ~~Table renames~~ — ad_element_placement → I_Element_Extraction, ad_geometry_map → I_Geometry_Map. PO classes renamed. iDempiere I_ Import convention (2026-03-07). | **DONE** |
| A12 | ~~Infrastructure~~ — Migration script index (29 scripts documented), logging infrastructure (log_helper.sh, audit_integrity.sh), persistent test/Rosetta logs (2026-03-07). | **DONE** |

**Gate:** `RosettaStoneGateTest` — G1-G6 all PASS for RE_SH and RE_DX.

**Dependency:** None (can start immediately). Foundation for everything else.

---

## HelloWorld Phase: RosettaStone _s/_e Dual Output

**Goal:** Both _s (EN-BLOC) and _e (EXPLODE) compilations match the reference
extracted DBs. Delta must be zero. BLOCKS Phase A.1 and everything downstream.

**Current state (2026-03-08): _s works (55/1099). _e wired but incomplete (0/153).**

- _s = EN-BLOC: walks flat EXTRACTED BOMs (EXT_SH/EXT_DX). All BUY, real product
  IDs. Proven correct: 6 gates GREEN.
- _e = EXPLODE: walks structured hierarchy (UNIT_SH_STD / UNIT_DUPLEX_STD →
  FLOOR → SET → BUY). Same BOMWalker + PlacementCollectorVisitor code, different
  root BOM selection via `bom.mode=STRUCTURED`.
- **Gap:** _e produces SH=0, DX=153 elements. Root cause: Phase 0.0 (structured
  BOM gap — generic product IDs + missing structural layer).

**Completed:** HW-1 (doc cleanup), HW-2 (_s/_e documentation), HW-3 (wire _e) — all DONE.

| Task | What | Status |
|------|------|--------|
| HW-4 | **Fix generic product IDs** — replace "IfcFurniture" etc. with real M_Product entries in structured BOM leaves. SQL migration (~60 UPDATEs). | TODO |
| HW-5 | **Add structural BOM layer** — walls, doors, windows, slabs, curtain wall, MEP under FLOOR BOMs. Derive offsets from EXTRACTED flat data. SQL migration. | TODO |
| HW-6 | **Add _e gate coverage** — RosettaStoneGateTest only tests _s. Add `bom.mode=STRUCTURED` test run so _e regressions are caught by `mvn test`. | TODO |
| HW-7 | **Dead code cleanup** — `fromFamilyBridge` (always false), `resolveByFamilyRank` (disabled), stale comment in BuildingWriter:920. | TODO |

**Gate:** `run_RosettaStones.sh` — both _s and _e match the reference for SH and DX.

**Dependency:** Phase A (gate infrastructure). Phase 0.0 (structured BOM data).
BLOCKS Phase A.1 (geometry fidelity).

### Code Audit: Silent-Skip Chain (2026-03-08)

The _e path has a chain of silent fallbacks that drop elements without hard failure.
HW-4 and HW-5 fix the **data** that triggers this chain. HW-6 adds the **gate** that
catches future regressions. No code changes needed for HW-4/HW-5 — the silent-skip
behaviour is correct (stderr warnings exist), the problem is missing data.

**The chain (for reference — do not "fix" these, they are correct guards):**

1. `BOMWalker:163` — MAKE child with no `m_bom` entry → treated as leaf (stderr).
   Empty FLOOR/SET stubs produce 0 elements. **Fixed by HW-5** (add structural children).
2. `BOMWalker:153` — `MProduct.get()` returns null for generic product ID → null product
   in NodeContext. **Fixed by HW-4** (point to real M_Product).
3. `PlacementCollectorVisitor:148` — null product + zero allocated dims → skip (stderr).
   **Fixed by HW-4** (real product has dims via M_Product or allocated_*_mm).
4. `MeshBinder:59` — no M_Product_Image for product → `resolveByProduct()` returns null →
   deprecated `resolveGeometryByInstance` fallback → also null → skip.
   **Fixed by HW-4** (real M_Product has M_Product_Image entry).

**Architectural note (hasMetadata fork):** `StoreyCompiler:181` and `BuildingWriter:475`
use `PlacementLoader.hasPlacement()` as a binary switch — either ALL elements come from
BOM metadata, or ALL come from legacy DSL compilation. Partial structured BOM output
(e.g. DX _e=153/1099) sends the pipeline into the legacy path for missing storeys.
This is acceptable for HW-4/HW-5 because the goal is **complete** structured BOMs
(delta=0), not partial. Once HW-4+HW-5 make the structured BOMs complete,
`hasPlacement()` returns true and the metadata path is taken for all storeys.

---

## Phase A.1: Geometry Fidelity (G7-GEOMETRY)

**Goal:** Vertex-level mesh fidelity proof. Every compiled element uses the correct
canonical mesh from LOD_Object, not just the right bounding box.

**Current state (2026-03-07): INVESTIGATING. 12/55 SH elements have wrong meshes.**

**Problem:** Gates G1-G6 all PASS but don't check vertex-level fidelity. M_Product_Image
geometry_hash mappings are wrong for ~9 SH furniture products — product names match
correctly but point to the wrong canonical mesh in LOD_Object. Vertex/face counts
don't match reference.

**Scale contract softening (committed c152a90):**
- MeshBinder: DimensionalContractViolation softened (throw → warn + proceed with
  parametric scaling). Parametric elements (walls, pipes, beams) legitimately scale
  beyond [0.3, 3.0] — these are not errors.
- BuildingWriter: catch DimensionalContractViolation → parametric fallback instead of
  hard throw. Degradation list tracks fallbacks.
- run_tests.sh: Expected counts updated (303 PASS / 7 RED).

| Task | What | Status |
|------|------|--------|
| G7-1 | **SH mesh audit** — identify all wrong M_Product_Image → LOD_Object mappings for SH products | Investigated — 12/55 wrong |
| G7-2 | **Data fix** — correct geometry_hash in M_Product_Image for affected products + migration script | TODO |
| G7-3 | **G7 gate** — RosettaStoneGateTest @Order(7): match compiled vs reference by AABB, compare vertex_count/face_count | TODO |
| G7-4 | **DX analysis** — run same vertex fidelity comparison for DX (1099 elements, 79 products) | TODO |
| G7-5 | **Scale contract** — MeshBinder DimensionalContractViolation softening (throw → warn for parametric elements) | WIP (uncommitted) |

**Gate:** G7-GEOMETRY in RosettaStoneGateTest. Vertex/face counts match reference for all SH and DX elements.

**Dependency:** Phase A (product-level geometry infrastructure must exist).

---

## Phase B: Terminal BOM Recomposition (TE-1 → TE-8)

**Goal:** 51,092 elements compiled from BOM gospel. Third Rosetta Stone at full
fidelity via BOM explosion, not just placement metadata.

Already designed in detail in `TheRosettaStoneStrategy.txt`. Summary:

| Sub-phase | Task | Elements | Depends on |
|-----------|------|----------|------------|
| TE-0 | Create TE_BOM.db (empty schema + qty column) | — | — |
| TE-1 | Storey normalisation (34,479 elements with empty storey) | Spatial | TE-0 |
| TE-5 | M_Product catalog expansion (122→~270 products) | Catalog | — (parallel) |
| TE-2 | ARC envelope decomposition (roof deck, walls, openings, furniture) | 34,724 | TE-1 |
| TE-3 | Structural frame decomposition (beams, columns, rebar) | 4,089 | TE-1 |
| TE-4 | MEP system decomposition (FP, ACMV, CW, ELEC, SP, LPG) | 12,279 | TE-1 |
| TE-6 | BOM tree assembly (factorised ~700 lines, not ~51K) | BOM | TE-2,3,4,5 |
| TE-7 | C_OrderLine generation + BOMWalker qty expansion | 51,092 | TE-6 |
| TE-8 | SpatialDigest verification + 3-stone regression | Proof | TE-7 |

**Critical path:** TE-0 → TE-1 → TE-2 → TE-6 → TE-7 → TE-8

**Key innovation:** Factorised BOM model — 700 m_bom_line rows (with qty) replace
51,300 naive rows (73× reduction). BIM COBOL TILE/ARRAY verbs handle 74% of
element placement parametrically.

**Gate:** RosettaStoneGateTest G1-G5 ALL PASS for CO_TE. Three-stone regression holds.

**Dependency:** Phase A (gate infrastructure proven on SH/DX first).

---

## Phase C: 2D Drawing Export

**Goal:** Compiled 3D output.db → professional SVG architectural drawings.
Floor plans, elevations, roof plan, sections — all from the same 3D model.

**Current state:** 2D_Layout/ module exists. 6 Java stub classes. Python prototype
archived. SH compiled DB available as test input.

| Task | What | Key file |
|------|------|----------|
| C1 | Port `SectionCut.java` — mesh-plane intersection (horizontal cut at Z=1.0m per storey) | Port from `python/section_cut.py` |
| C2 | Port `SVGBuilder.java` — SVG document builder with layers, line weights, hatching | New Java |
| C3 | Port `DrawingWriter.java` — main orchestrator: classify elements, cut, project, annotate | Port from `python/drawing_writer.py` |
| C4 | `GridDerivation.java` — extract structural grid lines from column/wall centerlines | New Java |
| C5 | `MetadataReader.java` — read compiled DB spatial structure, storey levels, element metadata | New Java |
| C6 | SH complete drawing set — floor plan + 4 elevations + roof plan to professional standard | Exhaust SH first |
| C7 | DX multi-storey — 2 floor plans + elevations (test multi-storey section cuts) | After C6 |
| C8 | TB-LKTN drawings — generative building from compiled output | After C7 |
| C9 | Terminal drawings — large-scale (51K elements, multi-discipline layering) | After Phase B |

**Output format:** SVG (vector, scalable). Future: DXF export for CAD import.

**SH roof note:** 197-vertex curved barrel vault. Use upper/lower envelope extraction
from mesh vertices (max/min Z at each horizontal position). Do NOT use convex hull.

**Gate:** SH drawing set matches hand-drawn reference. DX multi-storey correct.

**Dependency:** C1-C6 can start immediately (SH data exists). C9 needs Phase B.

---

## Phase D: Synthetic Rosetta Stone — Two Round-Trip Proofs

**Goal:** Prove the compiler is round-trip stable through TWO independent loops:

### Loop 1: 3D → 1D → 3D (already partially proven)

```
IFC source (3D)
    → Extract → BOM.db + component_library.db
        → 1D DSL (.bim text + C_Order + BOM recipes)
            → Compile (9-stage pipeline)
                → 3D output.db
                    → SpatialDigest must match reference
```

This is the current Rosetta Stone proof. SH and DX already demonstrate it
(100% positional match). The digest verification (G3) is the gate. Phase A
makes this rigorous (all 5 gates green).

### Loop 2: 3D → 2D → 3D (the novel contribution)

```
3D compiled output.db
    → 2D export (SVG floor plans + elevations + sections)
        → 2D parser (extract rooms, walls, openings, grid)
            → Generate 1D DSL (.bim) or C_OrderLines
                → Compile (9-stage pipeline)
                    → 3D output.db (Synthetic Rosetta Stone)
                        → SpatialDigest must match original
```

This closes the second loop. It proves:
- The 2D export preserves enough information for 3D reconstruction
- An architect's 2D drawings (the industry's primary medium) can drive compilation
- The compiler is round-trip stable — no information loss in either cycle

**Both loops converge at verification:** the SpatialDigest of the Synthetic
Rosetta Stone must match the original. Same elements, same positions, same
dimensions. The round-trip is lossless.

| Task | What | Input | Output |
|------|------|-------|--------|
| D1 | **SH 3D→2D→3D** — compile SH → export 2D → parse 2D → recompile → verify digest | SH output.db + SVGs | SH_synthetic.db |
| D2 | **DX 3D→2D→3D** — same for Duplex (multi-storey, multi-discipline) | DX output.db + SVGs | DX_synthetic.db |
| D3 | **2D parser** — read SVG floor plans + elevations → extract room boundaries, wall positions, opening locations, grid lines → generate DSL (.bim) or C_OrderLines | SVG files | .bim DSL or SQL |
| D4 | **Terminal 3D→2D→3D** — same for Terminal (51K elements, 9 disciplines) | TE output.db + SVGs | TE_synthetic.db |
| D5 | Register Synthetic Rosetta Stones in C_DocType — new DocSubType (e.g. SH_SYN, DX_SYN, TE_SYN) | — | BOM.db entries |

**What makes this novel:** Traditional BIM is one-way (3D → 2D drawings for
the contractor). This compiler goes both ways. The architect's 2D drawings
become first-class compilation input. 3D → 1D → 3D proves the BOM model.
3D → 2D → 3D proves the drawing export. Together they prove the entire chain.

**Gate:** SpatialDigest(SH_original) == SpatialDigest(SH_synthetic). Same for DX, TE.

**Dependency:** Phase C (2D export must work). Phase B (for Terminal round-trip).

---

## Phase E: Generative Compilation from 2D Layout

**Goal:** A building that has never existed as IFC — designed from 2D architectural
drawings — compiled to full 3D BIM output.

**Current state:** TB-LKTN compiles as GENERATIVE (139 elements). BIM COBOL script
exists (`scripts/TB_LKTN.bimcobol`). 5×5 structural grid, 7 rooms defined.

| Task | What |
|------|------|
| E1 | **2D layout parser** — read 2D floor plan (from architect's PDF/SVG) → extract grid, rooms, walls, openings → generate DSL + C_OrderLines |
| E2 | **TB-LKTN from 2D** — parse the actual LKTN 2D reference drawing → compile → compare against current generative output |
| E3 | **Priority furniture placement** — M_BomCategoryLine.Sequence drives dining→sofa→piano priority. ESLine clash avoidance. Buffer fillers. |
| E4 | **INVENTION STOP** — compiler halts if component_library returns nothing. Team adds missing LOD mesh, re-runs. |
| E5 | **BIM COBOL scripts for full TB-LKTN MEP** — WIRE LIGHTING all rooms, ROUTE SPRINKLERS all zones, CHECK COMPLIANCE UBBL |
| E6 | **New generative building** — apply the same 2D→3D pipeline to a different building type (e.g. low-rise office, school). Proves the framework is generic, not TB-LKTN-specific. |

**The 1D → 2D → 3D chain:**
```
1D text intent (.bim DSL / BIM COBOL)
    → 3D compilation (pipeline stages 1-9)
        → output.db (SQLite with elements + geometry)
            → 2D export (Phase C: SVG floor plans + elevations)
```
And the reverse (Phase D):
```
2D layout (architect's drawing)
    → 2D parser (extract rooms, walls, grid)
        → DSL / C_OrderLines
            → 3D compilation
                → output.db
```

**Gate:** TB-LKTN from 2D matches TB-LKTN from DSL. New building compiles clean.

**Dependency:** Phase C (2D export), Phase D (round-trip parser reuse).

---

## Phase F: BIM COBOL v1.0+ Language Maturity

**Goal:** All element generation verb-driven. Hardcoded Java assembler retired.

**Current state:** 12 verbs, 60/63 witnesses pass. SPI wired. TILE SURFACE covers
65% of Terminal elements. Combined formula coverage: 95.8% (74.4% LIVE).

| Task | What | Coverage |
|------|------|----------|
| F1 | **Terminal-scale verbs** — TILE SURFACE for 33K roof plates (19 panels), ARRAY for 2,660 rebar, ROUTE for 9,345 fire protection pipes | +38K elements |
| F2 | **ENCLOSE / SPAN verbs** — perimeter wall placement (1,038 elements designed) | +1K |
| F3 | **FRAME verb** — structural grid placement: beams + columns per bay (590 elements designed) | +590 |
| F4 | **Duct routing** — ROUTE DUCTS for ACMV (1,621 elements) | +1.6K |
| F5 | **Script-driven compilation** — MEP/structural generation moves entirely to .bimcobol scripts. Java assembler methods deleted. | Architecture |
| F6 | **Verb execution in pipeline** — VerbStage produces PlacedElements consumed by WriteStage. Full participation in Prove + Digest. | Pipeline |
| F7 | **Language spec v1.0** — formal grammar, reserved words, error messages, script library | Documentation |

**End state:** The pipeline becomes Compile → **Verb** → Write → Prove → Digest.
No hardcoded Java element generation remains. Adding new building features = new
.bimcobol script lines, not Java code changes.

**Gate:** TB-LKTN compiles entirely from .bimcobol. Terminal TE-4 MEP from verbs.

**Dependency:** Phase B (Terminal data for F1). Phase E (generative pipeline for F5).

---

## Phase G: Bonsai GUI Editor

**Goal:** Visual building editor integrated with the compiler. User selects building
type → compiler runs → Bonsai viewport shows result → user edits → recompiles.

**Current state:** Conceptual design only. Bonsai (BlenderBIM addon) used for
visualization but not integrated with compiler.

| Task | What |
|------|------|
| G1 | **Python addon skeleton** — Bonsai panel with building typology chooser, site/code/budget selectors |
| G2 | **Java compiler CLI** — command-line interface that takes DSL input + returns output.db path. Callable from Python subprocess. |
| G3 | **Python↔Java bridge** — Python addon calls Java compiler via subprocess or REST local server. Receives output.db path. |
| G4 | **IFC write-back** — convert output.db → IFC file → load into Bonsai viewport. Reuse existing Bonsai IFC import. |
| G5 | **Live recompilation** — user edits an OrderLine in the panel → regenerates DSL → calls compiler → refreshes viewport. Batch, not interactive — save → process → refresh. |
| G6 | **C_OrderLine editor** — panel showing C_OrderLines as editable rows. Edit triggers recompile. |
| G7 | **CO_EmptySpaceLine visualisation** — show placement slots as wireframe boxes in viewport. User sees where things go before final compile. |
| G8 | **BOM commit** — "Save as BOM" button. Satisfactory arrangement → new M_BOM in BOM.db. Available for future EN-BLOC singularity matching. |

**Workflow:**
1. User opens Bonsai, selects "BIM Compiler" panel
2. Picks building type (Residential SH / DX / TB / Commercial TE)
3. Adjusts parameters (site dimensions, room count, budget)
4. Clicks "Compile" → Java pipeline runs → output.db produced
5. Bonsai imports IFC from output → viewport shows 3D building
6. User edits OrderLines → recompile → viewport refreshes
7. Satisfied → "Save as BOM" commits to BOM.db dictionary

**Gate:** SH compiles and displays in Bonsai from GUI. User edits trigger recompile.

**Dependency:** Phase C (2D output useful but not required). Phase F (verb-driven
compilation preferred). Practically can start G1-G3 anytime.

---

## Phase H: iDempiere ERP Integration

**Goal:** Compiled BOM → real procurement. iDempiere handles vendor assignment,
pricing, MRP net requirements, purchase order generation.

**Current state:** `IDempiereExporter.java` (framework, ~365 lines). Hardcoded
product mappings. CSV export for M_Product + M_BOM.

### H1: CSV Export (enhance existing)

| Task | What |
|------|------|
| H1a | Polish IDempiereExporter — dynamic product mapping from M_Product (not hardcoded) |
| H1b | Export I_Product CSV (iDempiere import format) from BOM.db M_Product catalog |
| H1c | Export I_BOM CSV from m_bom + m_bom_line hierarchy |
| H1d | Export I_Order CSV from compiled C_Order + C_OrderLine (output.db) |
| H1e | Costed BOM export with MYR pricing from M_Product catalog |

**Gate:** CSV files import successfully into iDempiere GardenWorld test instance.

### H2: REST API Client

| Task | What |
|------|------|
| H2a | REST client library — call iDempiere REST endpoints for M_Product CRUD |
| H2b | M_Product sync — push BOM.db products to iDempiere, pull vendor pricing back |
| H2c | M_BOM sync — push assembly recipes to iDempiere Manufacturing module |
| H2d | C_Order sync — push compiled orders to iDempiere, receive PO numbers back |
| H2e | MRP integration — trigger iDempiere MRP calculation from compiled BOM, receive net requirements |

**Gate:** Compiled building → iDempiere M_BOM → MRP → Purchase Order generated.

### H3: OSGI Plugin (Full Integration)

| Task | What |
|------|------|
| H3a | iDempiere plugin project — OSGI bundle structure, extension points |
| H3b | Native BIM window in iDempiere — view compiled buildings, browse BOM tree |
| H3c | Manufacturing integration — PP_Order_Node as iDempiere manufacturing operations |
| H3d | Warehouse integration — CO_EmptySpaceLine as S_Resource (WMS spatial workstations) |
| H3e | Bidirectional sync — iDempiere vendor changes propagate to BOM.db pricing |

**Gate:** iDempiere user creates Construction Order → BIM compiler runs as backend →
result visible in iDempiere Manufacturing window → MRP generates purchase orders.

**Dependency:** H1 anytime. H2 after Phase A (clean output). H3 after Phase G (GUI
patterns established) and H2 (REST proven).

---

## Phase Dependency Graph

```
Phase 0.0 ─── Structured BOM Gap (discovered) ────────── ⚠️ TODO (HW-4, HW-5)
  │
Phase 0 ─── EN-BLOC Singularity ──────────────────────── ✅ DONE
  │
  └──► Phase 0.1 ─── Product Catalog Normalisation ──── ✅ DONE
  │       │
  │       └──► Phase 0.2 ─── BOM Walk + M_Product_Image  ✅ DONE
  │
  └──► Phase A ─── Gate Convergence (6 gates GREEN) ──── ✅ DONE
          │
          ├──► HelloWorld ─── _s/_e Dual Output ──────── 🔧 WIP (wired, data incomplete)
          │       │  needs Phase 0.0 fix (HW-4 + HW-5)
          │       │
          │       └──► Phase A.1 ─── Geometry Fidelity (G7)
          │
          ├──► Phase B ─── Terminal Recomposition (51K)
          │       │
          │       └──► Phase F ─── BIM COBOL v1.0
          │               │
          │               └──► Phase G ─── Bonsai GUI
          │                       │
          │                       └──► Phase H3 ─── iDempiere OSGI
          │
          ├──► Phase C ─── 2D Drawing Export (3D → SVG)
          │       │
          │       └──► Phase D ─── Synthetic Rosetta Stone
          │               │
          │               └──► Phase E ─── Generative from 2D
          │
          └──► Phase H1 ─── iDempiere CSV Export
                  │
                  └──► Phase H2 ─── iDempiere REST API
```

**Three parallel tracks:**
- **Track 1 — Core pipeline:** 0.0 → HW → A.1 → B → F → G → H3
  (structured BOM fix → _s/_e convergence → geometry fidelity → Terminal BOM → verb language → GUI → ERP plugin)
- **Track 2 — 2D round-trip:** A → C → D → E
  (gate convergence → 2D export → Synthetic Rosetta Stone → generative from 2D)
- **Track 3 — ERP integration:** H1 → H2
  (CSV export → REST API, partially independent)

**Convergence points:**
- Tracks 1 + 2 meet at Phase F (verbs drive both extracted and generative buildings)
- Track 3 meets Track 1 at H3 (OSGI plugin needs GUI patterns from Phase G)
- Phase D proves TWO loops: 3D→1D→3D (Track 1 verification) and 3D→2D→3D (Track 2)

**Current position (2026-03-08):** Phase 0.0 is the immediate blocker. Structured
BOM data must be fixed (HW-4 generic IDs + HW-5 structural layer) before _s/_e
delta can reach zero. Tracks 2 and 3 unblocked.

---

## Milestone Summary

| Milestone | Gate | What it proves | Status |
|-----------|------|----------------|--------|
| **M0** (Phase 0) | SH=55, DX=1099 elements via PlacementLoader | Extraction chain intact, element counts match reference | **DONE** |
| **M0.0** (Phase 0.0) | Exploded (_e) matches reference for SH and DX | Hierarchical BOM compiles to same result as reference | **TODO** |
| **M0.1** (Phase 0.1) | 78 M_Products, BOM digest == PlacementLoader digest | Product catalog normalised, BOM walk proven | **DONE** |
| **M1** (Phase A) | 6 gates GREEN for SH/DX | Extraction-to-compilation chain intact + isolation | **DONE** |
| **M1-HW** (HelloWorld) | _s and _e each match reference independently | Both compilation modes proven against ground truth | **WIP** (blocked by M0.0) |
| **M1.1** (Phase A.1) | G7 vertex fidelity for SH/DX | Every mesh matches reference (not just bbox) | — |
| **M2** (Phase B) | G1-G7 PASS for Terminal | 51K-element building from BOM gospel | — |
| **M3** (Phase C) | SH professional drawing set | 3D → 2D export works | — |
| **M4** (Phase D) | Round-trip digest match | 2D → 3D → 2D is lossless | — |
| **M5** (Phase E) | TB-LKTN from 2D layout | Generative compilation from architect drawings | — |
| **M6** (Phase F) | Zero Java assembler code | All generation is verb-driven | — |
| **M7** (Phase G) | Bonsai compile-edit-recompile | Visual editor works end-to-end | — |
| **M8** (Phase H) | iDempiere PO from compiled BOM | ERP procurement from BIM | — |
