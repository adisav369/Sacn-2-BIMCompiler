# System Contract — BIM Intent Compiler

> **What this is.** The governing document. Every spec answers a question;
> this document says which spec answers which question, for whom, and when.
> Like iDempiere's Application Dictionary governs modules, this contract
> governs the 52 docs in `docs/`.
>
> **Foundation:** [BBC](../BOMBasedCompilation.md) · [SystemContract](SystemContract.md) ·
> [TestArchitecture](../TestArchitecture.md) · [ProjectOrderBlueprint](../ProjectOrderBlueprint.md) ·
> [DATA_MODEL](../DATA_MODEL.md) · [SourceCodeGuide](../SourceCodeGuide.md) · [INDEX](../INDEX.md)

---

## 1. The Recursive Principle

The system operates at three scales. **The same pattern governs all three.**

```
SCALE 1: SITE           SCALE 2: BUILDING         SCALE 3: ROOM
────────────────        ─────────────────         ──────────────
C_Project               C_Order                   C_OrderLine
  plots (locators)        rooms (slots)             elements (leaves)
  site AABB               building AABB             room AABB
  put-away strategy       BOM explosion             verb placement
  terrain topology        storey structure          furniture layout

WHAT: C_ProjectLine     WHAT: C_OrderLine         WHAT: M_BOM_Line
HOW:  PlacementStrategy HOW:  PP_Order_Node       HOW:  Verb (TILE/ROUTE/FRAME)
WHERE: Plot (ABL)       WHERE: M_BOM_Line dx/dy/dz WHERE: tack offset (dx/dy/dz)
```

A site is to plots what a building is to rooms what a room is to elements.
The compiler, the gates, the DocAction lifecycle, the three-concern separation
— all work unchanged at every scale. New scales are configuration, not code.

**Spec authority:** §4 below (three-concern matrix),
[BBC.md §3.3-3.5](../BOMBasedCompilation.md) (BOM Drop, Selection Cascade),
[ProjectOrderBlueprint.md §2](../ProjectOrderBlueprint.md) (C_Project site-as-BOM).

---

## 2. Entity Registry

Every table in the system serves one of five roles. No table serves two roles.

### 2.1 Dictionary (AD) — System Configuration

**Database:** {PREFIX}_BOM.db, ERP.db
**Lifecycle:** Static. Changed by migration SQL only. Never by runtime code.
**iDempiere equivalent:** AD_Table, AD_Column, AD_Val_Rule, AD_Process.

| Table | Role | Spec |
|-------|------|------|
| `ad_fp_trigger` | Fire protection trigger rules | [ProjectOrderBlueprint.md §13](../ProjectOrderBlueprint.md) |
| `ad_fp_coverage` | NFPA 13 spacing/coverage | [ProjectOrderBlueprint.md §13](../ProjectOrderBlueprint.md) |
| `ad_space_type_mep_bom` | Per-room MEP qty/placement | [ProjectOrderBlueprint.md §13](../ProjectOrderBlueprint.md) |
| `ad_element_mep` | MEP element definitions | [DISC_VALIDATE_SRS.md](../DISC_VALIDATE_SRS.md) |
| `ad_bom_rule` | BOM quantity calculation | [DISC_VALIDATE_SRS.md](../DISC_VALIDATE_SRS.md) |
| `ad_building_code` | Code registry (NFPA, UBBL) | [DocValidate.md](../DocValidate.md) |
| `ad_code_requirement` | Code rules per element × space | [DocValidate.md](../DocValidate.md) |
| `ad_mep_profile` | Budget/Standard/Premium multiplier | [DISC_VALIDATE_SRS.md](../DISC_VALIDATE_SRS.md) |
| `AD_Val_Rule` | Validation rules | [DocValidate.md](../DocValidate.md) |
| `C_DocType` | "Construction Order" — one document type (see MANIFESTO.md §The Order) | [SystemContract.md §2](SystemContract.md) |
| `M_Product_Category` | Product taxonomy (46 rows) | [BBC.md §1](../BOMBasedCompilation.md) |

### 2.2 Master Data (M) — Product Catalog & BOM Recipes

**Database:** {PREFIX}_BOM.db, component_library.db, ERP.db
**Lifecycle:** Grows with each building onboarded. Changed by IFCtoBOM extraction or manual migration.
**iDempiere equivalent:** M_Product, M_BOM, M_BOM_Line.

| Table | Role | Spec |
|-------|------|------|
| `M_Product` | Product catalog (2475 products) | [BBC.md §1](../BOMBasedCompilation.md) |
| `m_bom` | Assembly recipe (BUILDING/FLOOR/ROOM) | [BBC.md §3](../BOMBasedCompilation.md) |
| `m_bom_line` | Child placement (dx/dy/dz tack offsets) | [BBC.md §4](../BOMBasedCompilation.md) |
| `m_bom_line_ma` | Per-instance identity (GUID threading) | [LAST_MILE_PROBLEM.md §Gap 5](../LAST_MILE_PROBLEM.md) |
| `m_attribute` | Leaf attributes (ports, clearances) | [DATA_MODEL.md](../DATA_MODEL.md) |
| `M_AttributeSet` | Attribute templates | [DATA_MODEL.md](../DATA_MODEL.md) |
| `component_definitions` | Component metadata + bounds | [DATA_MODEL.md §1](../DATA_MODEL.md) |
| `component_geometries` | Vertex/face BLOBs (deduplicated) | [DATA_MODEL.md §1](../DATA_MODEL.md) |
| `placement_rules` | Host-relative placement | [DATA_MODEL.md §1](../DATA_MODEL.md) |
| `surface_styles` | Material RGBA per product | [TheRosettaStoneStrategy.md](../TheRosettaStoneStrategy.md) |

### 2.3 Transaction Data (C) — Orders & Compiled Output

**Database:** compile DB (C_Order, C_OrderLine), output.db (elements)
**Lifecycle:** Created fresh each compile. Mutable during design session.
**iDempiere equivalent:** C_Order, C_OrderLine, M_InOut, M_InOutLine.

| Table | Role | Spec |
|-------|------|------|
| `C_Order` | Construction order (one per building) | [SystemContract.md §2](SystemContract.md) |
| `C_OrderLine` | Order line (one per BOM node) | [SystemContract.md §2](SystemContract.md) |
| `C_Project` | Site development (groups C_Orders) | [ProjectOrderBlueprint.md §2](../ProjectOrderBlueprint.md) — **NOT YET IMPLEMENTED** |
| `C_ProjectLine` | Plot allocation (one per plot/group) | [ProjectOrderBlueprint.md §2](../ProjectOrderBlueprint.md) — **NOT YET IMPLEMENTED** |
| `M_AttributeSetInstance` | Per-instance customer options | [SystemContract.md §2](SystemContract.md) |
| `elements_meta` | Compiled elements (world xyz) | [DATA_MODEL.md §4](../DATA_MODEL.md) |
| `element_instances` | Geometry instances (transform) | [DATA_MODEL.md §4](../DATA_MODEL.md) |

### 2.4 Production Data (PP) — How Things Were Built

**Database:** output.db, compile DB
**Lifecycle:** Created during compilation. Immutable after.
**iDempiere equivalent:** PP_Order, PP_Order_Node.

| Table | Role | Spec |
|-------|------|------|
| `PP_Order_Node` | Verb execution audit | [BIM_COBOL.md](../BIM_COBOL.md) |
| `PP_Order_NodeProduct` | Verb parameters | [BIM_COBOL.md](../BIM_COBOL.md) |
| `AD_ChangeLog` | Full audit trail (who/what/when) | [ProjectOrderBlueprint.md §10](../ProjectOrderBlueprint.md) |

### 2.5 Spatial Data — Where Things Go

**Database:** compile DB, output.db
**Lifecycle:** Spatial relationships live in M_BOM_Line dx/dy/dz (the BOM itself).
**iDempiere equivalent:** M_BOM_Line tack offsets encode WHERE; no separate locator needed.

| Table | Role | Spec |
|-------|------|------|
| `m_bom_line` (dx/dy/dz) | Spatial relationships within BOM | [MANIFESTO.md §Three Concerns](../MANIFESTO.md) |
| `elements_rtree` | R-tree spatial index | [DATA_MODEL.md](../DATA_MODEL.md) |

> **Note:** `co_empty_space` / `co_empty_space_line` are compiler-internal tables
> (implementation detail for filler queries). They are NOT architectural entities.
> Empty space = BOM line with `bom_type = 'FILLER'`, queryable via SQL.

---

## 3. Transaction Catalogue

Every mutation to the system is a **DocAction** — an auditable, reversible
document transition. Not an API call. A document transition.

### 3.1 Document Lifecycle

```
                    ┌─────────────────────────────────────────┐
                    │                                         │
  ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐     │
  │ NEW  │──→│  DR  │──→│  IP  │──→│  AP  │──→│  CO  │──→ VO│
  │      │   │Draft │   │In Pr │   │Approv│   │Compl │  Void│
  └──────┘   └──────┘   └──────┘   └──────┘   └──────┘     │
                │                                   │         │
                │         MUTATIONS                 │         │
                │         (between DR and CO)       │         │
                │                                   │         │
                ├── BomDrop    → explode BOM tree   │         │
                ├── AddLine   → rule-driven insert  │         │
                ├── SwapLine  → product replacement │         │
                ├── RemoveLine → qty=0 skip         │         │
                ├── Compress  → reference class     │         │
                └── ASI Edit  → dimension override  │         │
                                                    │         │
                    CompleteIt = COMPILE             │         │
                    (pipeline runs, output.db created)        │
                                                              │
                    Void = REVERSE                            │
                    (output.db deleted, order reverts to DR)  │
                                                              │
                    └─────────────────────────────────────────┘
```

**Spec authority:** [DocAction_SRS.md](../DocAction_SRS.md) (lifecycle),
[ProjectOrderBlueprint.md §1.1](../ProjectOrderBlueprint.md) (four mutations),
[ProjectOrderBlueprint.md §14.3](../ProjectOrderBlueprint.md) (implementation sessions).

### 3.2 Mutation Registry

Every mutation has: a trigger, a validation, an effect, and an audit record.

| Mutation | Trigger | Pre-condition | Effect | Audit |
|----------|---------|--------------|--------|-------|
| **BomDrop** | User selects building type | C_DocType exists, M_BOM exists | C_Order + C_OrderLine tree created | AD_ChangeLog: PLACE |
| **AddLine** | Rule engine or user | Product exists in catalog | New C_OrderLine (status=PROPOSED) | AD_ChangeLog: SAVE |
| **SwapLine** | User selects replacement | Replacement product fits parent AABB | C_OrderLine.family_ref updated | AD_ChangeLog: MOVE |
| **RemoveLine** | User removes element | Line exists, not structural-required | C_OrderLine.qty = 0 (skipped) | AD_ChangeLog: DELETE |
| **Compress** | Reference class flag | N instances of same product | 1 C_OrderLine with qty=N | AD_ChangeLog: SAVE |
| **ASI Edit** | User drags in viewport | Dimension within product constraints | M_AttributeSetInstance updated | AD_ChangeLog: RESIZE |
| **CompleteIt** | User clicks Complete | All validations pass | CompilationPipeline runs → output.db | AD_ChangeLog: PROMOTE |
| **Void** | User reverses | Order is CO status | Output deleted, status → DR | AD_ChangeLog: UNDO |

### 3.3 Mutation Status: What Exists Today

| Mutation | Status | Spec | Code |
|----------|--------|------|------|
| BomDrop | ✓ DONE | BBC.md §3.3 | BomDropper.drop() |
| SwapLine | ✓ DONE | ProjectOrderBlueprint.md §1.1 | WorkOutputDAO.swapProduct() |
| AddLine | PARTIAL | ProjectOrderBlueprint.md §14.3 Session A | Discipline wired (S66), API missing |
| RemoveLine | NOT STARTED | ProjectOrderBlueprint.md §14.3 Session D | — |
| Compress | NOT STARTED | ProjectOrderBlueprint.md §14.3 Session D | — |
| ASI Edit | PARTIAL | BIM_Designer.md | M_AttributeSetInstance exists, no recompile path |
| CompleteIt | ✓ DONE | DocAction_SRS.md | CompilationPipeline.run() |
| Void | ✓ DONE | DocAction_SRS.md | MOrder.voidIt() |

---

## 4. Three-Concern Matrix

Every entity at every scale must have all three concerns defined.
A `?` means the spec is missing — this is a gap that must be closed.

### 4.1 Scale 1: Site

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | C_ProjectLine (which buildings, qty) | NOT IMPL | [Blueprint §2](../ProjectOrderBlueprint.md) |
| **HOW** | SitePlacementStrategy (put-away rules) | NOT IMPL | [Blueprint §2.2](../ProjectOrderBlueprint.md) (this session) |
| **WHERE** | Plot locator (ABL: Street/Lot/Terrace) | NOT IMPL | [Blueprint §2.2](../ProjectOrderBlueprint.md) (this session) |

### 4.2 Scale 2: Building

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | C_OrderLine (which elements) | ✓ DONE | [SystemContract.md §2](SystemContract.md) |
| **HOW** | PP_Order_Node (verb execution) | ✓ DONE | [BIM_COBOL.md](../BIM_COBOL.md) |
| **WHERE** | M_BOM_Line dx/dy/dz (spatial relationships) | ✓ DONE | [MANIFESTO.md §Three Concerns](../MANIFESTO.md) |

### 4.3 Scale 3: Room

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | M_BOM_Line (which products) | ✓ DONE | [BBC.md §3](../BOMBasedCompilation.md) |
| **HOW** | Verb pattern (TILE/ROUTE/FRAME) | ✓ DONE | [BIM_COBOL.md](../BIM_COBOL.md) |
| **WHERE** | tack offset (dx/dy/dz) | ✓ DONE | [BBC.md §4](../BOMBasedCompilation.md) |

### 4.4 Cross-Scale: Mutations

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | C_OrderLine (status=PROPOSED) | PARTIAL | [Blueprint §14.3 Session A](../ProjectOrderBlueprint.md) |
| **HOW** | OrderLineMutation interface | NOT IMPL | [Blueprint §14.3 Session B](../ProjectOrderBlueprint.md) |
| **WHERE** | locator_ref addressing | NOT IMPL | [Blueprint §14.3 Session D](../ProjectOrderBlueprint.md) |

### 4.5 Cross-Scale: ASI/Viewport

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | M_AttributeSetInstance (dimension override) | PARTIAL | [SystemContract.md §2](SystemContract.md) |
| **HOW** | Recompile trigger (which verbs re-fire) | **NOT SPECCED** | Gap — needs ASI_MUTATION_SRS |
| **WHERE** | Which element, which dimension | **NOT SPECCED** | Gap — needs ASI_MUTATION_SRS |

### 4.6 Cross-Scale: Freehand Drawing

| Concern | Entity | Status | Spec |
|---------|--------|--------|------|
| **WHAT** | Viewport geometry → BOM mutation | **NOT SPECCED** | Gap — needs FREEHAND_SRS |
| **HOW** | Drag → ASI → recompile chain | **NOT SPECCED** | Gap — needs FREEHAND_SRS |
| **WHERE** | Viewport coordinates → tack offset | **NOT SPECCED** | Gap — needs FREEHAND_SRS |

---

## 5. Validation Chain

Every validation rule fires at a specific point in the document lifecycle.
Rules do not invent — they **block or propose**.

### 5.1 Validation Timing

| When | What Fires | Effect |
|------|-----------|--------|
| **BomDrop** | M_BOM exists, M_Product active, AABB valid | Block drop if BOM missing |
| **AddLine (rule-driven)** | ad_space_type_mep_bom qty check, product exists | Propose C_OrderLine (status=PROPOSED) |
| **SwapLine** | Replacement fits parent AABB, category compatible | Block if AABB overflow |
| **Pre-Complete** | AD_Val_Rule (63 rules), PlacementValidatorImpl | Block compile if violations |
| **Post-Complete** | G1-G6 gates, EYES proofs, W-* witnesses | Report failures (compile already ran) |
| **Post-Complete (composed)** | G7-COMPOSITION, W-COMP-* witnesses | Verify fragment provenance + spatial invariants |

### 5.2 Rule Pack Model

**Current gap:** No versioning, no effectivity dates, no jurisdiction scoping.

**Required (from ERP precedent):**

| Concept | iDempiere | BIM Compiler | Status |
|---------|-----------|-------------|--------|
| Rule version | M_PriceList_Version.ValidFrom | AD_Val_Rule.ValidFrom | **NOT IMPL** |
| Jurisdiction | AD_Org_ID | ad_building_code.jurisdiction | EXISTS (14 rows) |
| Pack grouping | M_DiscountSchema | pack_id on rule tables | NOT IMPL (Blueprint §14.3 Session C) |
| Precedence | M_PriceList priority | Rule pack priority | **NOT SPECCED** |

---

## 6. Allocation Model — Site as Warehouse

> **The terrain IS the warehouse floor plan. Plots ARE locators.
> Buildings ARE inventory. Put-away strategy assigns buildings to plots.**

### 6.1 Warehouse ↔ Site Mapping

| Warehouse (iDempiere) | Site (BIM Compiler) | Entity |
|-----------------------|--------------------|---------|
| M_Warehouse | C_Project | Site development |
| M_Warehouse.AABB | C_Project.site_aabb | Site envelope |
| M_Locator | Plot | Individual lot |
| M_Locator.Aisle | Street | Road/access way |
| M_Locator.Bin | Lot number | Sequential along street |
| M_Locator.Level | Terrace | Elevation contour band |
| M_LocatorType | PlotType | STANDARD/CORNER/PREMIUM/INFRA/GREEN |
| M_Locator.capacity | Plot.frontage × depth | Buildable area |
| M_PutAwayStrategy | SitePlacementStrategy | Which building goes where |
| **C_ProjectLine** | **Plot assignment** | **One plot = one C_ProjectLine, FK to C_Order** |
| M_InOut | Material delivery batch | Goods receipt for construction materials to site |
| M_InOutLine | Individual material receipt | 100 panels delivered, 50 bags cement received |
| M_InOutLineMA | Lot/attribute tracking | Batch number, inspection date, material cert |

<!-- Implementing SpecsAnalysis.txt §13 — M_InOut = materials movement -->

> **Clarification (S79):** M_InOut is iDempiere's goods receipt/shipment — it
> tracks **materials movement**, not site allocation. Plot placement uses
> `C_ProjectLine` (one plot = one line under C_Project, FK to the building's
> C_Order). M_InOut enters the picture under 4D scheduling: when materials
> arrive on site, M_InOut records the delivery batch; M_InOutLine records each
> material item; M_InOutLineMA tracks lot numbers and inspection certificates.
> → [ProjectOrderBlueprint.md §5.1](../ProjectOrderBlueprint.md#51-4d-schedule--topological-sort-of-bom-tree)

### 6.2 Plot as Locator — ABL Addressing

```
TERRAIN TOPOLOGY (from PDFTerrain survey: 689 points, 294m × 229m, Z: 28-48m)

  Terrace 2 (Z ≈ 44-48m)    Street 3
  ┌──┬──┬──┬──┬──┬──┬──┐    ═══════════
  │01│02│03│04│05│06│07│    Lots along street
  └──┴──┴──┴──┴──┴──┴──┘
                              Street 2
  Terrace 1 (Z ≈ 38-44m)    ═══════════
  ┌──┬──┬──┬──┬──┬──┬──┬──┐
  │01│02│03│04│05│06│07│08│  Corner = 01 and 08
  └──┴──┴──┴──┴──┴──┴──┴──┘
                              Street 1
  Terrace 0 (Z ≈ 28-38m)    ═══════════
  ┌──┬──┬──┬──┬──┬──┬──┬──┬──┐
  │01│02│03│04│05│06│07│08│09│  River valley (lowest)
  └──┴──┴──┴──┴──┴──┴──┴──┴──┘

ABL ADDRESS: Street-Lot-Terrace
  S1-03-0 = Street 1, Lot 3, Terrace 0 (river valley, Z ≈ 32m)
  S2-08-1 = Street 2, Lot 8, Terrace 1 (corner lot, Z ≈ 42m)
  S3-04-2 = Street 3, Lot 4, Terrace 2 (hilltop, Z ≈ 46m)
```

**Terrain Z from PDFTerrain:** Each plot's elevation is interpolated from
the 689-point survey via `AlignmentContext.elevationAt(x, y)`. The plot
centre (x, y) gives the terrain Z. Cut-and-fill is computed via
`CutFillCalculator` to determine platform level.

**Existing infrastructure:**
- PDFTerrain: 689-point survey extraction (proven, W-CONTEXT-TERRAIN-1)
- AlignmentContext: `elevationAt(x, y)` interpolation (proven)
- TerrainSnap: ON_SURFACE/ABOVE/BELOW snap modes (proven)
- CutFillCalculator: earthwork volumes (proven)
- GradingStrategy: CONTOUR/STRAIGHT/BLEND modes (proven)

### 6.3 Put-Away Strategy — Site Placement Rules

```
TABLE: ad_site_placement_rule (NOT YET IMPLEMENTED)

| rule_id | plot_type | building_variant | orientation_rule | priority |
|---------|-----------|-----------------|-----------------|----------|
| SPR-01  | CORNER    | SH_CORNER       | FACE_STREET     | 10       |
| SPR-02  | PREMIUM   | SH_PREMIUM      | FACE_VIEW       | 20       |
| SPR-03  | STANDARD  | SH_STD          | FACE_STREET     | 99       |
| SPR-04  | INFRA     | SITE_INFRA_STD  | ALONG_STREET    | 1        |
| SPR-05  | GREEN     | NULL            | —               | 1        |

VALIDATION:
  - building.aabb fits within plot (frontage, depth, setback)
  - building.aabb_height ≤ zoning_max_height
  - adjacent buildings: gap ≥ side_setback × 2
  - INFRA plots: utility easement width respected
  - total building footprint ≤ site_aabb (aggregate check)
```

### 6.4 Compilation Flow at Site Scale

```
1. C_Project created (site_aabb from survey boundary)
2. Site grid generated: streets × lots × terraces from terrain topology
     Each plot = M_BOM_Line at site scale (ABL address, frontage, depth via dx/dy/dz)
     Each plot.z = elevationAt(plot_centre) from PDFTerrain survey
3. C_ProjectLine created: qty=180 SH_STD, qty=15 DX_STD, etc.
4. Put-away strategy runs:
     For each C_ProjectLine:
       For each unit in qty:
         Find available plot matching rules (plot_type → building_variant)
         Create C_Order with FK to plot
         Mark plot is_available = N
5. Exceptions applied: plot[12] → swap SH_STD to SH_CORNER
6. Each C_Order compiles independently via standard pipeline
7. G7-PROJECT gate: aggregate count, spatial containment, no overlap
8. G8-SITE gate: all buildings within site_aabb, setbacks respected
```

### 6.5 Test Case: 689-Point Survey

**Source:** `IfcOpenShell/.../pdf_terrain/samples/survey_highres_extracted.json`
**Site:** 294m × 229m, Z range 28.1-48.1m (20m), 689 elevation points
**Terrain character:** River valley (Z ≈ 28m) to hilltop (Z ≈ 48m), 3 natural terraces

**Demo scenario:** 24 houses on 3 terraces (small scale, proves the pattern):
- Terrace 0 (Z ≈ 30m): 9 SH_STD lots along Street 1 (river level)
- Terrace 1 (Z ≈ 40m): 8 SH_STD + 2 SH_CORNER lots along Street 2
- Terrace 2 (Z ≈ 46m): 4 SH_PREMIUM + 1 GREEN lots along Street 3 (hilltop view)
- Infrastructure: 3 road segments connecting terraces

**Witnesses:**
- W-SITE-LAYOUT-1: 24 plots generated from terrain, ABL addresses assigned
- W-SITE-PUTAWAY-1: put-away rules assign correct variants to plot types
- W-SITE-TERRAIN-1: each plot Z matches survey interpolation within 0.5m
- W-SITE-COMPILE-1: all 24 buildings compile via standard pipeline
- W-SITE-CONTAIN-1: all buildings within site_aabb, setbacks respected

---

## 7. Effectivity Model

Rules, BOMs, and prices change over time. The system must know
**which version applies to which order**.

### 7.1 Rule Pack Effectivity

| Concept | Column | Example |
|---------|--------|---------|
| Pack ID | `pack_id` on AD_Val_Rule | 'NFPA-13-2024', 'UBBL-2024' |
| Valid From | `valid_from DATE` | '2024-01-01' |
| Valid To | `valid_to DATE` | '2028-12-31' (NULL = current) |
| Jurisdiction | `jurisdiction` | 'MY', 'US', 'AU' |
| Order Date | C_Order.DateOrdered | Determines which pack applies |

**Resolution:** Most specific pack wins. Order:
1. Plot-specific rule (exception on this plot)
2. Project-level rule (C_Project jurisdiction)
3. Active pack for jurisdiction + date
4. Default pack (no jurisdiction filter)

**iDempiere precedent:** M_PriceList_Version.ValidFrom + M_ProductPrice priority.

### 7.2 BOM Effectivity (Future)

| Concept | Column | Example |
|---------|--------|---------|
| BOM Version | m_bom.version | 'SH_STD_v2' |
| Valid From | m_bom.valid_from | '2026-01-01' |
| Supersedes | m_bom.supersedes_id | FK to previous version |

Not implemented. Current model: one active BOM per M_Product_Category + building prefix.

---

## 8. Verification Registry

Every gate, every witness, every proof — what it verifies, when it runs.

### 8.1 Gates

| Gate | Scale | What It Proves | Spec |
|------|-------|---------------|------|
| G1-COUNT | Building | Element count matches BOM | [TestArchitecture.md](../TestArchitecture.md) |
| G2-VOLUME | Building | Total volume preserved | [TestArchitecture.md](../TestArchitecture.md) |
| G3-DIGEST | Building | Spatial digest matches reference | [TestArchitecture.md](../TestArchitecture.md) |
| G4-TAMPER | Building | Seal intact, no unauthorized changes | [G4_SRS.md](../G4_SRS.md) |
| G5-PROVENANCE | Building | All elements trace to BOM source | [TestArchitecture.md](../TestArchitecture.md) |
| G6-ISOLATION | Building | No cross-building contamination | [TestArchitecture.md](../TestArchitecture.md) |
| G7-COMPOSITION | Building (composed) | Fragment provenance + fidelity + spatial invariants | [TheRosettaStoneStrategy.md §Tier 4](../TheRosettaStoneStrategy.md) |
| G7-PROJECT | Site | Aggregate count, spatial containment | [Blueprint §2.1](../ProjectOrderBlueprint.md) |
| G8-SITE | Site | All buildings within site_aabb, setbacks | [Blueprint §2.1](../ProjectOrderBlueprint.md) |

### 8.2 Verification Tiers

| Tier | What | Reference Needed? | Spec |
|------|------|-------------------|------|
| Tier 1: Vocabulary | Right parts | Yes (reference DB) | [TheRosettaStoneStrategy.md](../TheRosettaStoneStrategy.md) |
| Tier 2: Placement | Right places | Yes (reference DB) | [TheRosettaStoneStrategy.md](../TheRosettaStoneStrategy.md) |
| Tier 3: Integrity | Building works | No (reference-free) | [EYES_SRS.md](../EYES_SRS.md) |
| Tier 4: Composition | Valid sentences from proven words | Indirect (source stones) | [TheRosettaStoneStrategy.md §Tier 4](../TheRosettaStoneStrategy.md) |

---

## 9. Spec Dependency Graph

Which spec governs which, and what order to read.

```
                    SystemContract.md (THIS FILE)
              ┌──────────┼──────────┬──────────┐
              │          │          │          │
          BBC.md    DATA_MODEL  DocAction  TestArchitecture
          (engine)  (schema)   (lifecycle) (verification)
              │          │                    │
       ┌──────┼──────┐   │          ┌────────┼────────┐
       │      │      │   │          │        │        │
  BIM_COBOL Source  BIM_   │        EYES  TheRosetta  G4_SRS
  (verbs)   Code   Designer │      (proofs) Stone     (tamper)
            Guide  (UX)    │               (tiers 1-4)
                           │
                 ┌─────────┼─────────┐
                 │         │         │
           DocValidate  DISC_VAL  CALIBRATION
           (rules)     (disciplines) (density)

              ProjectOrderBlueprint.md
              (§1-§14: the frontier — what's next)

              INFRA_DESIGNER_SRS.md
              (infrastructure: terrain, alignment, cut-fill)
```

**Reading order for a new contributor:**
1. **[MANIFESTO.md](../MANIFESTO.md)** — the ERP world view (read this first, always)
2. BBC.md — entity mapping table + compilation model
3. This file (SystemContract.md) — entity registry, three-concern matrix, gap register
4. DATA_MODEL.md — schema, 4-DB architecture
5. TestArchitecture.md — how verification works
6. ProjectOrderBlueprint.md — what's planned
7. SourceCodeGuide.md — where the code is

---

## 10. Gap Register

Computational areas where the spec is incomplete.
Each gap must be closed with an SRS before code is written.

| Gap | Area | What's Missing | Priority |
|-----|------|---------------|----------|
| **GAP-SC-1** | ASI mutation → recompile | Which verbs re-fire? Which witnesses re-check? Transaction flow spec | HIGH (blocks viewport drag) |
| **GAP-SC-2** | Freehand drawing → BOM | How viewport geometry becomes a BOM mutation | MED (blocks freehand mode) |
| **GAP-SC-3** | Site grid generation | Algorithm to subdivide site_aabb into plots using terrain topology | HIGH (blocks C_Project) |
| **GAP-SC-4** | Rule pack versioning | Effectivity dates, version precedence, pack lifecycle. **Tagging done** (S67c pack_id on 4 AD tables). Versioning/lifecycle deferred. | MED (partially addressed) |
| **GAP-SC-5** | Order inheritance conflict | Single-parent FK prevents sibling DAG; linear chain resolved by depth (deepest wins). See ProjectOrderBlueprint.md §6.3. InheritanceResolver + OrderInheritanceTest 6/6. | **CLOSED** (Session E, S68e, 2026-03-25) |
| **GAP-SC-6** | Compile-once-copy-many | Performance optimization for reference class (qty=180) | MED (blocks C_Project at scale) |
| **GAP-SC-7** | Output consolidation | Per-building vs consolidated output.db for C_Project | MED (blocks C_Project) |
| **GAP-SC-8** | R-PROJ-3 C_Order_ID collision | BomDropper parameterized with explicit orderId (Session 0). Default path unchanged. W-PROJ-ID-1 proves coexistence. | **CLOSED** (Session 0, 2026-03-24) |
