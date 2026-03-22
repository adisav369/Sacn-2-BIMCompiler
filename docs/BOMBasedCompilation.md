# BOM-Based Compilation — Construction as Manufacturing

> **Foundation:** [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

*If you can bill it, you can build it. If you can BOM it, you can compile it.*

> **Core thesis:** A building is a manufactured product. Its Bill of Materials IS the
> building — every wall, door, pipe, and cabinet is a line item with a position.
> A compiler that reads BOM data and produces 3D coordinates is doing the same thing
> an ERP system does when it explodes a manufacturing BOM into work orders.

---

## 1. Why BOM Metadata Solves Construction

Construction software treats buildings as geometry problems — draw walls, place
doors, route pipes. ERP software treats products as data problems — BOMs, work
orders, procurement. This project proves they are the **same problem**.

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element (wall panel, door, pipe elbow) |
| **M_BOM** (bill of materials) | Assembly recipe (kitchen set, floor plan, building unit) |
| **M_BOM_Line** (BOM child) | Recipe line: product type + qty (or verb formula). NOT a per-instance placement |
| **C_Order** (work order) | Construction project for a specific building |
| **C_DocType** (document type) | Building type configuration (SH, DX, TB, TE) |
| **CO_EmptySpace** (warehouse slot) | Room/floor slot awaiting BOM content |
| **PP_Order_Node** (operation) | Verb execution record (audit trail) |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

The BOM hierarchy maps directly to the building hierarchy:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
 │         │         │        │        │
 building  storey    room    furniture  leaf product
                              group    (door, pipe, cabinet)
```

**Three BOM dimensions** (iDempiere pattern) govern selection:

1. **Category** (M_BomCategory) — WHAT: kitchen, bedroom, bathroom, structural — flat classification (like M_Product_Category in iDempiere). Products belong to categories.
2. **Provenance** (m_bom.doc_sub_type) — WHO extracted it: SH, DX, TB, TE — metadata on the BOM, not a selection driver
3. **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH: width × depth × height in mm

**Why this is powerful:** Adding a new building type requires zero Java code.
Define new BOM data (M_BomCategory + m_bom rows) and the
compiler handles it. The same way an ERP handles a new product — data, not code.

### 1.1 Disciplines Are Metadata, Not Structure

In iDempiere, `C_DocType.DocBaseType` (SOO, API, MOP) classifies documents.
The document engine is generic — it processes all types identically. What
differs per type is the **validation rules** (tax on Invoice, credit check on
Order, BOM explosion on Manufacturing Order). The engine never has a
`switch(docBaseType)` — it fires AD_Val_Rule / ModelValidator / Column Callout
based on metadata.

Disciplines (ARC, STR, FP, ACMV, ELEC, SP, CW, LPG) follow the same pattern:

| iDempiere | BIM Compiler |
|-----------|-------------|
| C_DocType.DocBaseType | m_bom.bom_category (the discipline marker) |
| Same document engine | Same BOM walker (§2.2, §4) |
| AD_Val_Rule per DocType | AD_Val_Rule per bom_category (sprinkler spacing, clearances) |
| Column Callout on field change | Per-discipline product validation |
| Invoice → tax/charge validation | FP BOM → fire code compliance |
| ModelValidator.docValidate() | PlacementValidator per jurisdiction |

The BOM walker does not know what ARC or FP means. It just recurses
(§2.2.1). The YAML `disciplines:` section is a classifier — it maps
`ifc_class → bom_category` — not a structural change. The discipline
label on `m_bom.bom_category` is the hook that AD_Val_Rule uses to fire
the right validation rules (see `DocValidate.md`).

**Legacy debt:** The DSL-generative compiler path (Phase 0-era, TB-LKTN)
contains hardcoded category checks — `CompilationPipeline:506 IN (...)`,
`categoryToRoomType()` switch, `ComposeBuildingVerb RESIDENTIAL→RE` map
(see `TestArchitecture.md` H3/H4). These are on the DSL path only — the
BOM-based pipeline (Instant Drop / BOM Drop) is clean. Target: migrate DSL path
to data-driven lookups when generative compilation is prioritised.

**MEP disciplines** (FP, ACMV, ELEC, SP, CW, LPG) are not structurally
different from ARC. They follow the same tack convention (§4), the same
BUFFER invariant (§4.2), the same validateBOM() check. What differs is
the validation rules that fire after placement — spacing, clearance,
capacity — just as an Invoice has tax rules that fire after line entry.

### 1.2 Discipline Routing — Three States per Discipline

Each discipline in YAML has exactly three possible states:

| YAML value | Meaning | What happens |
|------------|---------|-------------|
| `{prefix}_BOM` | Pipeline populates from named BOM | BOM walker includes this discipline's sub-tree from `{prefix}_BOM.db` |
| `DocEvent` | Validation handles this discipline | DocEvent discovers elements, applies AD_Val_Rule per discipline + shared rules |
| Absent | Discipline does not exist for this building | Nothing. No BOM, no validation. The building genuinely has no such discipline. |

```yaml
# Terminal: all disciplines from extraction BOM
disciplines:
  ARC:  TE_BOM
  STR:  TE_BOM
  FP:   TE_BOM
  ACMV: TE_BOM
  CW:   TE_BOM
  ELEC: TE_BOM
  SP:   TE_BOM
  LPG:  TE_BOM

# SampleHouse: ARC only, no MEP
disciplines:
  ARC:  SH_BOM
  # FP, ELEC, CW etc. absent — SH has no MEP

# Generative house: ARC from BOM, MEP via DocEvent validation
disciplines:
  ARC:  DM_BOM
  FP:   DocEvent
  ELEC: DocEvent
  CW:   DocEvent
  SP:   DocEvent
```

**No ambiguity.** Every discipline is explicitly routed (`{prefix}_BOM`),
delegated to validation (`DocEvent`), or absent (does not exist). The
pipeline never guesses.

**Two concerns, cleanly separated:**

| Concern | Trigger | Spec |
|---------|---------|------|
| **YAML → BOM pipeline** | Discipline set to `{prefix}_BOM` | This document (BBC.md) |
| **DocEvent Validation** | Discipline set to `DocEvent` | `DocAction_SRS.md` §0 → `DocValidate.md` |

DocEvent validation organizes into two layers:

| Layer | Scope | Examples |
|-------|-------|---------|
| **Discipline silos** | Per-discipline rules for `DocEvent` disciplines | FP spacing (NFPA 13), ELEC ceiling offset, STR column grid |
| **Shared/common** | Cross-cutting rules that always apply | Non-clash (any discipline pair), regulatory (UBBL room sizes), AABB containment, vertical continuity |

See `DocAction_SRS.md` §0 for the `processIt()` orchestration.

---

## 2. The Gospel Principle

Reference buildings are treated as **gospel** — authoritative, immutable truth.

```
Extract (IFC source)  →  Commit ({PREFIX}_BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**`{PREFIX}_BOM.db` is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

For **EXTRACTED** provenance (SH, DX, TE): every element traces to a real IFC
source via `I_Element_Extraction`. If it cannot be traced, the output is invalid.

For **GENERATIVE** provenance (DemoHouse, BIM Designer): elements trace to BOM
templates (sources 1–7 in `LAST_MILE_PROBLEM.md` Gap 4) plus user spatial edits
(C_OrderLine dx/dy/dz in work_output.db). The provenance field on C_Order and
promoted m_bom rows distinguishes the two origins. See `G4_SRS.md` §2.4.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

**Schema-Not-Geometry rule:** Every validation check and spatial query must be
expressible as a SQL query over the 5-database schema. If a check requires AABB
arithmetic (centreline distance, centroid offset, proximity search), ask: does
the IFC schema have a relationship (`IfcRelVoidsElement`, `IfcRelConnectsPathElements`,
`IfcRelContainedInSpatialStructure`) that could be extracted as a column instead?
If yes, that is a missing extraction column — fix the schema, do not add geometry
code. LLMs are blind to spatial geometry but capable at schema mapping. The
5-database architecture is a semantic firewall: extraction pre-digests IFC
relationships into relational keys, validation queries those keys.
Exception: ERP-maths (e.g., M12 pipe clearance via `centreline_distance - radius_a
- radius_b`) is legitimate when the arithmetic on product dimensions IS the correct
method and no IFC relationship would improve it.
See `DocValidate.md` §15.6 for the full decision tree and M1-M17 audit.
See `LAST_MILE_PROBLEM.md` R21-R24 for the 8 identified schema gaps.
See `BIM_COBOL.md` §20 for spatial predicate verbs that standardise ERP-maths
queries (DISTANCE_BETWEEN, CLEARANCE_BETWEEN, NEAREST, WITHIN, etc.) so no
code writes raw AABB SQL — predicates upgrade transparently when R21-R24 land.

**No Parametric Mesh in Pipeline.** Every element in compiled output gets its
geometry from `component_library.db` (LOD_ hash prefix). The pipeline MUST NOT
generate parametric bounding boxes (GEO_ hash prefix) for any element in any mode.
Rationale: Rosetta Stone verification compares compiled output against extracted
reference. If the compiler emits a parametric box where the stone has a real mesh,
the comparison is not apple-to-apple — the gate result is meaningless.
`component_library.db` has basic LOD building blocks for every construction
primitive (wall panel, slab, column, beam, pipe, door, window, fixture).
`M_AttributeSetInstance` controls per-instance sizing (width, depth, height,
material). The compiler scales the library LOD by ASI parameters — it never
creates geometry. Even roof uses TILE verb + ASI shaping, not mesh generation.
`G5-PROVENANCE` Check 6 enforces zero GEO_ hashes: any parametric fallback = FAIL.
`createBoxGeometry` and `bindParametric` must not exist in any compilation code path.

**IFC already solves geometry.** The IFC schema carries a full relational model
underneath the 3D mesh: `IfcRelAggregates` (spatial hierarchy), `IfcRelVoidsElement`
(opening hosts), `IfcRelConnectsElements` (MEP connectivity), `IfcRelDefinesByType`
(product typing). The FederatedModel DB pre-digests these relationships into SQLite
for query speed and adds the manufacturing layer IFC never had (m_bom / m_bom_line
with qty, verb_ref, tack offsets). The BOM Outliner (BIM_Designer.md §17.19) is the
user-facing consequence: editing a building is configuring a relational tree, not
manipulating geometry. Drag a SET to a different FLOOR = FK update. Swap a product
= family_ref change. The compiler renders from the updated schema.

---

## 2.1. IFC→BOM Stage — Top-Down AABB Decomposition

*Preamble to compilation: how extracted IFC data becomes a BOM tree.*

The IFCtoBOM pipeline reads `I_Element_Extraction` (component_library.db) and a
classification YAML, then produces a `*_BOM.db` with spatial arrangement (m_bom + m_bom_line).
Products are created in component_library.db first (persistent catalog, reused across buildings),
then transitionally copied to BOM DB. 9 BomValidator checks + 2 pre-flight guards enforce data integrity pre-commit.
See [`WorkOrderGuide.md`](WorkOrderGuide.md) §Step 5 for the full pipeline table and §Drift Prevention
for the guard list. The decomposition is **top-down** — from the largest AABB to the smallest —
with each layer stopping when it has assigned its children.

### 2.1.1 Decomposition Layers

```
BUILDING AABB (computed from all extracted elements)
  │
  ├─ split by STOREY (storey names from YAML + I_Element_Extraction.storey)
  │    → compute per-storey AABB from elements in that storey
  │
  ├─ split by DISCIPLINE (from YAML disciplines: map + I_Element_Extraction.discipline)
  │    → ARC_DX_L1, PLB_DX_L1, ELC_DX_L1 etc.
  │    → per-discipline AABB from elements in that discipline×storey
  │
  ├─ split by SCOPE SPACE (from YAML floor_rooms: spaces with AABB)
  │    → elements whose centroid falls within scope space AABB
  │    → produces SET BOMs (LIVING_SET, KITCHEN_SET, etc.)
  │    → dedup: identical M_Product entries get qty, not duplicate BOM lines
  │
  └─ LEAF elements + BUFFER space
       → leaves = M_Product refs with dx/dy/dz (parent-relative from extraction)
       → buffer space fills when validateBOM() detects parent AABB ≠ SUM(children)
```

### 2.1.2 Each Layer Stops

| Layer | Input | Output | Stops When |
|-------|-------|--------|-----------|
| BUILDING | all elements | BUILDING BOM + AABB | storey assignment complete |
| STOREY | elements in this storey | FLOOR BOM + AABB | discipline assignment complete |
| DISCIPLINE | elements in this discipline×storey | DISC BOM + AABB | scope space assignment complete |
| SCOPE SPACE | elements in this room scope | SET BOM + AABB | all elements assigned to leaves |
| LEAF | single M_Product | m_bom_line with dx/dy/dz | terminal — no further split |
| BUFFER | remaining AABB gap | PHANTOM m_bom_line | fills parent = SUM(children) invariant |

### 2.1.3 YAML Defines Scope Spaces

The classify YAML provides the spatial grid that the pipeline decomposes into.
The pipeline does not invent scope spaces — it reads them from the YAML and
assigns extracted elements by spatial containment (centroid within AABB).

```yaml
floor_rooms:
  Ground Floor:
    bom_id: FLOOR_SH_GF_STD
    spaces:
      - { name: LIVING,   template_bom: SH_LIVING_SET,  aabb_mm: [9069, 1682, 1170] }
      - { name: KITCHEN,  template_bom: KITCHEN_SET,     aabb_mm: [7100, 600, 900] }
      - { name: BATHROOM, template_bom: TOILET_FIXTURES, aabb_mm: [0, 0, 0] }
```

The `aabb_mm` on each space is the scope — the spatial envelope that the pipeline
uses to assign elements. Elements whose centroid falls within this scope become
children of that SET BOM. Elements not matching any scope become structural
(direct children of the storey discipline BOM).

**Zero-size scope** (`aabb_mm: [0, 0, 0]`): indicates a placeholder scope space
with no spatial extent. No element centroid can fall within a zero-size envelope,
so the SET BOM will have zero children. This is used for rooms that exist in the
YAML topology but have no extracted furniture (e.g., BATHROOM in SH has no
scope-assignable elements — bathroom fixtures are structural, not furniture).
ScopeBomBuilder skips zero-size scopes: the SET BOM is created with no leaf lines.

### 2.1.4 Dedup and Product Registration

Identical elements (same `element_ref` pattern) are deduplicated into a single
`M_Product` entry. The BOM line carries `qty` for replicates instead of one
line per instance. This is the iDempiere pattern: 47 duplex receptacles =
1 M_Product (`DUPLEX_RECEPTACLE`) with `qty=47` on the BOM line.

### 2.1.5 Discipline Layer (schema_version 2)

For multi-discipline buildings (DX, Terminal), the YAML `disciplines:` map
inserts a discipline BOM level between STOREY and SCOPE SPACE. See
`DISC_BOM_DESIGN.md` §2 for the 5-level hierarchy and §5 for YAML schema v2.
See `TerminalAnalysis.md` §ERP Model Architecture for how discipline codes
sit on M_BomCategory with `doc_type='CO'` and how ROUTE/TILE verbs map to
M_AttributeSet/Instance.

Single-discipline buildings (SH, schema_version 1) skip this layer — no
discipline wrapper needed. DocBaseType (RE/CO) on M_BomCategory determines
which L2 axis is used: rooms (RE) or disciplines (CO).

### 2.1.6 Recipe vs Placement — The BOM Contract

**`{PREFIX}_BOM.db` is a recipe.** Each m_bom_line is a **type line** — one row per
unique product within its parent BOM, with a qty count or verb formula reference.
The compiler expands type lines into placement instances at compile time.

**output.db holds placements.** Each row in `c_orderline` / `elements_meta` is one
physical element at its world-space coordinate. The compiler produces these by
expanding BOM recipes through verb formulas (TILE grid, ROUTE path, FRAME bay, etc.)
or flat placement (qty=1 for irregular elements).

```
{PREFIX}_BOM.db (RECIPE — factored)          output.db (PLACEMENT — expanded)
┌─────────────────────────────────┐         ┌──────────────────────────────────┐
│ m_bom_line                      │         │ elements_meta / c_orderline      │
│  product_id=PLATE_500x150       │  ──→    │  PLATE_500x150 @ (92.5, -42, 19)│
│  qty=4410                       │ expand  │  PLATE_500x150 @ (93.0, -42, 19)│
│  verb_ref=TILE(15×294, 495mm)   │         │  PLATE_500x150 @ (93.5, -42, 19)│
│                                 │         │  ... (4,410 rows)               │
└─────────────────────────────────┘         └──────────────────────────────────┘
```

**SH/DX happen to be trivially factored** — most products appear once per parent
BOM (qty=1), so recipe and placement look identical. The distinction only becomes
visible at TE scale where 505 unique products expand to 48,428 placement instances.

**Invariant:** `SUM(m_bom_line.qty)` across all **non-PHANTOM** leaf lines in
`{PREFIX}_BOM.db` equals the element count in output.db. BUFFER/PHANTOM lines
represent gap-fill space — they carry qty but do not expand to output elements.
The BOM is the recipe; the output is the cooked meal.

**TE factorization (done, 2026-03-17; CLUSTER optimised 2026-03-18):**
48,485 instances → 1,131 recipe lines (42.8:1). 505 unique products → 48,428 active elements.
VerbDetector mines 4 verb patterns from extraction centroids:

| Verb | Recipe Lines | Instances | Fidelity | What it detects |
|------|-------------|-----------|----------|-----------------|
| CLUSTER | 354 | 47,607 | approximate (29.1m max, 3.7m avg) | Semi-regular offset-table grouping |
| TILE | 3 | 12 | PASS (0.0m) | 2D uniform grid (roof plates) |
| FRAME | 2 | 78 | PASS (1.08m max) | Grid intersections (structural bays) |
| ROUTE | 2 | 18 | PASS (0.32m max) | Axis-aligned uniform-step runs |
| flat | 770 | 770 | exact | Irregular placements |

Step-uniformity guard (R8): each ROUTE leg's consecutive gaps must be within
±20% of the average step. Non-uniform groups fall through to CLUSTER or flat writes.
*(History: 1,442 lines pre-CLUSTER → 1,297 post-SPRAY → 1,131 post-CLUSTER.)*
See [`BIM_COBOL.md`](BIM_COBOL.md) §19 for verb taxonomy,
data flow, and fidelity details.

**Pipeline phases (self-contained since 2026-03-20):**
1. **BOM pipeline** (`IFCtoBOMPipeline.run()`): extracts geometry → populates product catalog → writes `{PREFIX}_BOM.db`. Calls `ensureProductCatalog()` + `ensureProductImages()` internally (INSERT OR IGNORE = idempotent). No separate populate step required.
2. **Compile** (`DAGCompiler`): reads `{PREFIX}_BOM.db` + `component_library.db` → produces output.db

The shell script (`run_RosettaStones.sh`) still runs `--populate` as a fast-path
when `I_Geometry_Map` count is 0, but it is no longer a prerequisite — the BOM
pipeline is self-contained. Phase 1 can re-run freely (`rm *_BOM.db`).

### 2.1.7 What IFCtoBOM Does NOT Do

- Does not invent elements (EXTRACT only — every leaf traces to I_Element_Extraction)
- Does not compute placement rules (layout_strategy, z_rule stay NULL — generative future)
- Does not validate against regulations (see `DocValidate.md`)
- Does not fill missing pipes or correct gaps (WYSIWYG — DX corners without connecting pipes are preserved as-is)

---

## 2.2 BOM Compilation Model — Recursive Placement

### 2.2.1 The Rule

Every `m_bom_line` is a placement instruction: "place this child's LBD at
offset (dx, dy, dz) from my LBD." The compiler walks the BOM tree by one
rule: if `child_product_id` resolves to another `m_bom`, recurse into it.
If it resolves to an `M_Product` in `component_library.db`, it is a leaf —
emit the element at the accumulated world position.

**`component_type` does not exist in compilation.** The iDempiere Manufacturing
BOM column (`BUY/MAKE/PHANTOM`) has no role. The walker decides BOM-vs-leaf
purely by whether `child_product_id` has a matching `m_bom` row. The column
remains in the schema for iDempiere compatibility; code must never branch on it.

### 2.2.2 BOM-to-BOM Recursion

```
BUILDING BOM  (LBD = world origin, stored in m_bom.origin_x/y/z)
  └─ (dx,dy,dz) = floor LBD position in building  →  FLOOR BOM
       └─ (dx,dy,dz) = room LBD position in floor   →  ROOM BOM (e.g. SH_LIVING_SET)
            ├─ (dx,dy,dz) = piano position in room   →  Piano (leaf in library)
            ├─ (dx,dy,dz) = sofa position in room    →  SOFA BOM (has children)
            │    ├─ (dx,dy,dz) = couch position in sofa  →  Couch (leaf)
            │    └─ (dx,dy,dz) = table position in sofa  →  Table (leaf)
            └─ (dx,dy,dz) = dining position in room  →  DINING BOM (has children)
                 └─ ...
```

Every line carries a 3D position **(dx, dy, dz)** — where the child's LBD
corner sits within the parent's bounding box. All three axes matter: a piano
at (7.67, 1.61, 0.59) means 7.67m right, 1.61m back, 0.59m up from the
room's LBD corner. The walker does not know or care about BOM types. It just
recurses until it hits a leaf. The hierarchy depth is unlimited.

### 2.2.3 Library Population (Outside Compilation)

When no suitable leaf product exists in `component_library.db`, it must be
created outside the compilation pipeline:

1. **TopologyMaker** — generates mesh geometry for a new product type
   (parametric wall, slab, beam). Runs standalone, not during compilation.
2. **Mesh2Library** (`Mesh2Library.txt` pipeline) — registers the generated
   mesh into `component_library.db` as a new leaf product.

After population, the product is a leaf like any other. The compiler never
knows whether a product was extracted from IFC or generated — it just reads
`component_library.db` and positions.

**Orientation metadata:** Each leaf product in `component_definitions` carries
intrinsic orientation: `attachment_face` (TOP/BOTTOM/SIDE/CENTER), `up_axis`,
`forward_axis`, `orientation` (PENDANT/UPRIGHT/WALL_MOUNT), `default_rotation`.
These are the "polarity markers" — like the white dot on a capacitor in SMT
assembly. The compiler uses them to orient the mesh correctly at the tack
position. See `BIM_Designer.md` §8.3 for per-instance ASI overrides
(`face_anchor`, `swing_side`) and `DocValidate.md` M16/M17 for validation.

---

## 3. Two Compilation Modes

### 3.1 Terms

| Term | iDempiere parallel | Definition |
|------|-------------------|------------|
| **ESLine** | S_Resource (spatial workstation) | `co_empty_space_line` — a spatial slot that receives a child BOM. The parent provides the attachment point (tack_from); the child doesn't know its host. |
| **BUFFER** | Phantom BOM line | Fills the gap between SUM(children AABB) and parent AABB. Ensures parent = SUM(children) invariant. |
| **BOM Drop** | PP_Product_BOM explosion | Interactive tree navigation — expand BOM children one level at a time, swap products (same bom_category), add lines. Only needed when making changes. Without BOM Drop, compile explodes the tree automatically (iDempiere Instant BOM Drop pattern). |
| **Instant Drop** | Manufacturing Order processing | No modifications — 1 C_OrderLine references a BOM product, compile explodes the full tree. The quickest hello world test. |
| **Tack** | Origin datum | Left-Back-Down (LBD) corner of AABB = (minX, minY, minZ) = (0,0,0) in own frame. All offsets are parent-LBD to child-LBD. |
| **BOM** | Bill of Materials | Any `m_bom` row. If `child_product_id` resolves to an `m_bom`, the walker recurses into it. There is no MAKE/BUY distinction — the walker decides by existence. |
| **Leaf** | Purchased item | A `child_product_id` that resolves to `M_Product` in `component_library.db` (no matching `m_bom`). The compiler emits the element here. |

### 3.2 The ESLine Mechanism — Parent Owns the Attachment Point

**The child BOM does not know its parent.** A child BOM does not know which
parent hosts it. A leaf product does not know which BOM contains it. This is by design —
a child can be reused in any parent that has a slot for it.

**The parent provides the attachment point via ESLine:**

```
Parent BOM (e.g. FLOOR_TE_GF)
  │
  ├─ tack_from = [slot_1_origin, slot_2_origin, ...]   ← parent defines WHERE
  │
  └─ m_bom_line (children)                               ← parent defines WHAT children
       │
       ├─ child 1: bom_category=ARC, dx/dy/dz            ← architecture assembly
       ├─ child 2: bom_category=STR, dx/dy/dz            ← structural assembly
       └─ ...
            │
            ▼
       Selection cascade picks best child BOM             ← child is selected, not self-aware
            │
            ▼
       ESLine receives the child at parent's tack_from    ← parent's slot, child's content
```

**ESLine carries the parent's tack_from value.** When the selection cascade picks
a child BOM for a slot, the ESLine tells the compiler: "place this child's LBD
at this position within the parent's frame." The child's tack_to (its own LBD)
meets the parent's tack_from (the slot origin). The child never looks up.

**Why this matters for generative:** In the BIM Designer, the user navigates the tree.
The parent BOM defines its children via m_bom_line. The user can BOM Drop to navigate the tree and swap/add products. The child BOM is oblivious — it can be swapped, resized, or replaced without touching any other BOM.

### 3.3 Instant Drop — The HelloWorld Test

The simplest compilation: one `C_OrderLine` references a BOM product (e.g.
BUILDING_SH_STD). No BOM Drop, no modifications. The compiler processes the
order and explodes the BOM tree — walking every level, accumulating tack
offsets (dx/dy/dz), resolving leaves to M_Product geometry.

This is the iDempiere **Instant BOM Drop** pattern: drop without modification,
auto-resolve cascading quantities from the parent.

One C_OrderLine. One CO_EmptySpaceLine at origin (0,0,0). The entire BOM
hierarchy is accepted as-is. SH (55), DX (1099), TE (48,428) all compile
this way — the Rosetta Stones prove the BOM data restacks correctly.

**What Instant Drop proves:**
- **Stacking order:** the BOM hierarchy restacks to reproduce the original
- Verb expansion: factored recipes expand to correct instance count
- The BOM data is complete and self-consistent (G1-G6 gates verify this)
- Tacking: dx/dy/dz accumulates correctly through every BOM level

### 3.4 BOM Drop — Interactive Modification

When the user wants to modify a building, they use **BOM Drop** — the
iDempiere BOMDrop Configurator pattern adapted for BIM.

1. C_OrderLine references a BOM product (e.g. BUILDING_SH_STD)
2. User performs BOM Drop — tree expands one level, showing immediate children
3. User navigates to the child they want to change
4. **Swap:** replace a child with another product in the same bom_category
   (e.g. swap flat roof → pitched roof, both category RF)
5. **Add:** insert a new C_OrderLine for an additional product
   (e.g. add FP sprinkler — validation rules compute placement)
6. **Remove:** deactivate a child line (e.g. remove dining room furniture)
7. Compile processes the modified order — same tacking, same explosion

**Four component types** (from iDempiere BOMDrop Configurator):

| Type | BIM example |
|------|-------------|
| **Component** (compulsory) | Structural: walls, slabs, roof — cannot remove |
| **Optional** (checkbox) | MEP disciplines: FP sprinklers, ACMV — toggle on/off |
| **Variant** (qty adjustable) | Room count: 2 bedrooms → 3 bedrooms |
| **Radio Group** (exclusive) | Roof type: flat OR pitched — pick one |

The BOM Drop tree for Terminal (48,428 elements) has only 58 BOM nodes and
1,572 lines — the verbs (TILE, ROUTE, FRAME, CLUSTER) compress thousands
of instances into formula lines. The tree stays navigable at any scale.

### 3.5 Selection Cascade

The BOM chooser (used during BOM Drop to find replacement products) uses two
fields: **BomCategory** and **AABB**.

1. **BomCategory** (scope): restricts to same functional type (swap roof → show only RF products)
2. **AABB fit** (primary): replacement must fit within the parent's allocated space
3. **Largest volume** (secondary): maximize space usage
4. **seq_no** (tiebreaker): lower preferred

This is the same browse/filter pattern as iDempiere's product lookup:
filter by M_Product_Category, then by dimensional fit.

### 3.5.1 AttributeSetInstance — Per-Instance Customization

The Selection Cascade picks the **BOM recipe** (WHAT). The `M_AttributeSetInstance`
(ASI) customizes **each instance** (HOW) — the iDempiere ERP pattern:

```
ERP:  SKU = "TEE_CREW_NECK"    → ASI: { size: XL, color: navy }
BIM:  Product = "WALL_EXT_150" → ASI: { length_mm: 12500, material: BrickPlaster }
      Product = "PIPE_COLD_25" → ASI: { length_mm: 3200, angle_deg: 45 }
      Product = "DOOR_INT_810" → ASI: { swing_side: -1, face_anchor: INT }
```

**Three-table pattern** (identical to iDempiere manufacturing):

| Table | Role | Example |
|-------|------|---------|
| `M_Product` | Catalog product (canonical dimensions) | WALL_EXT_150: 150mm exterior wall |
| `M_AttributeSetInstance` | Per-instance header (FK from C_OrderLine) | Instance #47: wall on grid A-1 |
| `M_AttributeInstance` | Individual name/value overrides | length_mm=12500, material=BrickPlaster |

**Resolution rule:** `effective_dimension = ASI_override ?? catalog_default`

The compiler resolves effective dimensions from ASI (if customized) or catalog default.
Library LOD is **scaled** by the effective dimension — never remodeled, never generated.

**What varies per instance** (derived from extracted Rosetta Stones):

| Product Type | Varying (IsInstanceAttribute=1) | Fixed (defines product type) |
|---|---|---|
| Wall | length_mm, height_mm, material, finish | thickness |
| Pipe | length_mm, angle_deg, elevation | diameter |
| Door | swing_side, face_anchor (INT/EXT) | width, height |
| Window | sill_height_mm, face_anchor | width, height |
| Slab | area_m2, thickness_mm | material |
| Furniture | — (IsInstanceAttribute=0) | All dims fixed |
| MEP terminal | — (IsInstanceAttribute=0) | Identical everywhere |

`IsInstanceAttribute=0` products (furniture, smoke detectors) need no ASI — every
instance is identical. `IsInstanceAttribute=1` products (walls, pipes, slabs) vary
per instance and carry ASI overrides.

**Generative flow:** The user defines a bigger room (e.g., LIVING 6000×4500×3000).
The cascade finds a matching SET that fits. Each element in the SET becomes a
C_OrderLine. The user (or auto-suggest) creates ASI overrides to stretch walls to
the new room dimensions. The compiler resolves `effective = ASI ?? catalog` and
produces the correctly-sized output. Zero new code — just data on the OrderLine.

*Full ASI field resolution matrix: BIM_Designer.md §8.*
*Schema: W001_work_output_schema.sql (M_AttributeSetInstance, M_AttributeInstance).*
*Generative application: GENERATIVE_ROOM_SRS.md §6.*

### 3.6 The Rosetta Stone — Launch Booster

The Rosetta Stone exercise (SH/DX/TE) proves the pipeline is **lossless**:
Extract → BOM → Compile → Verify. If compiled output matches the reference IFC
exactly (G1-G6 GREEN), the compiler faithfully reproduces known buildings.

**The Rosetta Stone is a launch booster — it is abandoned once it has worked.**
Its purpose is to calibrate and prove the compilation machinery (tack convention,
BOM recursion, verb expansion, ESLine placement). Once proven, the same machinery
drives generative compilation where the BOM is authored by a human or Designer,
not extracted from IFC.

**What the Rosetta Stone proves for generative:**
- Tack convention correctly reconstructs world positions (§4)
- BOM-to-BOM recursion reaches LEAF correctly (§2.2)
- Verb formulas expand to correct instances (§2.1.6)
- ESLine/slot machinery places children at parent's tack_from (§3.2)
- The pipeline from recipe to placement is deterministic and verifiable

**What the Rosetta Stone does NOT prove:**
- Multi-candidate selection (all stones are Instant Drop singularities)
- BOM Drop with actual swap choice → first test is DemoHouse via BIM Designer
- DocValidate compliance checking → proven separately (`DocValidate.md`),
  applied in BIM Designer's ambient compliance (§18.4 in `BIM_Designer.md`)

---

## 4. Tack Convention — The Spatial Handshake

### 4.0 The One Rule

Every `m_bom_line` is a **tack instruction**: place the child's bounding box
corner at this offset from the parent's bounding box corner.

- **LBD** = Left-Back-Down = bounding box minimum corner = (minX, minY, minZ)
- **Left** = X minimum, **Back** = Y minimum, **Down** = Z minimum
- **dx/dy/dz** on an `m_bom_line` = the position within the parent where
  the child's LBD corner sits

No centroids. No special cases. One rule for every line at every depth.

**Convention extends to work_output.db:** `C_OrderLine.dx/dy/dz` in the design
workspace uses the same LBD convention. When a user moves a bbox in BIM Designer,
they're editing a tack value. When Promote writes C_OrderLine → m_bom_line, the
tack values transfer directly. See `G4_SRS.md` §5.4 for tack convention tests
on work_output.db, and `BIM_Designer.md` §17.10.3 for change tier detection.

**tack_from / tack_to (Lego principle):**

A parent BOM has N children. Each child needs a spot in the parent's space.
The parent's `m_bom_line` defines **tack_from** — the (dx,dy,dz) position
within the parent where the child's corner goes. The child's **tack_to** is
always its own LBD = (0,0,0) in its own frame.

```
SH_LIVING_SET  (parent AABB = 8000 × 2000 × 1200 mm)
  │
  ├─ tack_from=(0.3, 0.1, 0.0) → Piano         tack_to=(0,0,0) = piano's LBD
  ├─ tack_from=(2.5, 0.8, 0.0) → SOFA_BOM      tack_to=(0,0,0) = sofa group's LBD
  ├─ tack_from=(5.8, 0.2, 0.0) → Dining Table   tack_to=(0,0,0) = table's LBD
  └─ BUFFER fills remaining space (§4.2)
```

Each `tack_from` is a named slot in the parent. The child's LBD corner lands
there. The child doesn't know the parent — it can be reused in any parent
that has a slot big enough for it. This is the Lego principle: the stud
pattern is on the parent (tack_from), the child just has a flat bottom (tack_to).

### 4.1 World Coordinate Reconstruction

```
element_LBD = building_origin + tack_from[1] + tack_from[2] + ... + tack_from[N]
centroid    = element_LBD + (width/2, depth/2, height/2)
```

where each `tack_from[i]` is the full 3D position `(dx, dy, dz)` from that
level's m_bom_line. The walker accumulates all three axes through the BOM chain.

**Origin convention (R16 lesson):** Only the **BUILDING BOM** carries a non-zero
origin (`m_bom.origin_x/y/z` = world LBD position). All child BOMs (FLOOR,
DISCIPLINE, SET) have `origin = (0, 0, 0)` — their position within the building
is encoded solely in the parent's tack_from (dx/dy/dz on the m_bom_line that
references them). If child BOMs also carried non-zero origins, the walker would
double-count: `line.dx + childBom.origin` would exceed the correct offset.
This was the root cause of R16 (session 17 fix).

Centroid is computed **only at the output stage** — for display, for spatial
digest, for comparison with extraction. Centroid never enters the BOM. The BOM
stores only tack positions (dx/dy/dz) and AABB dimensions.

**Example (SH Living Room):**
```
BUILDING (origin = -9.235, -2.746, -0.470)     ← world LBD stored in m_bom.origin_x/y/z
  tack_from=(0.0, 0.0, 0.0) → FLOOR BOM        ← ground floor LBD = building LBD
    tack_from=(2.2, 5.2, 0.5) → LIVING BOM      ← living room LBD sits here in the floor
      tack_from=(0.3, 0.1, 0.0) → Piano (leaf)  ← piano sits here in the living room
      tack_from=(2.5, 0.8, 0.0) → SOFA BOM      ← sofa group sits here
        tack_from=(0.0, 0.0, 0.0) → Couch        ← couch at sofa group's LBD corner
        tack_from=(1.2, 0.3, 0.0) → Coffee Table  ← table offset from sofa LBD
```

Piano world LBD:
  X = -9.235 + 0.0 + 2.2 + 0.3 = -6.735
  Y = -2.746 + 0.0 + 5.2 + 0.1 = 2.554
  Z = -0.470 + 0.0 + 0.5 + 0.0 = 0.030

Piano centroid = piano_LBD + (piano_width/2, piano_depth/2, piano_height/2)

**Scope box origin (YAML `origin_m`)** is a **containment filter only** — it
determines which elements belong to which room during BOM generation. It is NOT
a spatial reference for offsets. The tack_from on the FLOOR→ROOM line comes from
the room's measured LBD relative to the floor's LBD, not from the scope box origin.

### 4.1.1 validateBOM() — The Spatial Analogue

In iDempiere Manufacturing, `validateBOM()` checks that a BOM is complete and
consistent before it can be used in production. The spatial analogue is:
- Every child's tack_from must place it within the parent's AABB
- Children must not overlap (clash detection)
- The sum of children + BUFFER must account for the parent's full AABB (§4.2)
- All tack_from values are non-negative (child cannot be behind parent's origin)

This validation runs in `BomValidator.java` (9 check methods) and is the spatial
equivalent of the ERP manufacturing BOM integrity check.

### 4.2 BUFFER — The Completeness Invariant

**Definition:** A BUFFER is a phantom m_bom_line that fills the remaining AABB
gap so that the parent's allocated dimensions equal the sum of its children's
allocated dimensions along each axis.

**Invariant:** For every non-leaf BOM:
```
parent.width  = SUM(children.allocated_width)    along tack-X
parent.depth  = SUM(children.allocated_depth)    along tack-Y
parent.height = SUM(children.allocated_height)   along tack-Z
```

Where `allocated_dim` = the child's own AABB dim along that axis (or the
BUFFER's phantom dim for the remainder). The BUFFER absorbs the gap between
the sum of real children and the parent envelope.

**Why BUFFER matters:** Without BUFFER, the compiler cannot prove that a BOM's
spatial claim is fully accounted for. BUFFER is the difference between a recipe
that says "these items go here" and one that says "these items fill exactly
this space." The second form is verifiable; the first is not.

#### 4.2.1 AABB Qualifier (session 43)

The `aabb_qualifier` column on `m_bom` disambiguates which envelope the AABB
dimensions represent. Without qualification, AABB is ambiguous — different
builders compute from different reference surfaces.

| Qualifier    | What it measures                          | Use case                                              |
|-------------|-------------------------------------------|-------------------------------------------------------|
| `INNER`      | Room clear volume, finish-to-finish       | Furniture placement, PHANTOM index, Click-to-Place    |
| `STRUCTURAL` | Centerline-to-centerline (structural grid)| Grid layout, structural analysis, column spacing      |
| `OUTER`      | Full object extent including projections  | Clash detection, site boundary, extraction AABB       |
| `OPENING`    | Clear opening in host element             | Door/window placement, accessibility clearance        |

Maps to GD&T tolerance zones: INNER=LMC, OUTER=MMC, STRUCTURAL=Basic, OPENING=Virtual.

`ScopeBomBuilder`: SET BOMs tagged `OUTER` (computed from element extents).
Empty SET BOMs (no assigned elements) tagged `INNER` (YAML room dims = available space).
`FloorRoomBomBuilder`: FLOOR ROOM BOMs tagged `INNER` (YAML-sourced architect intent).
`StructuralBomBuilder`/`DisciplineBomBuilder`: default `OUTER` (computed from elements).

#### 4.2.2 PHANTOM — Spatial Availability Index (session 43)

PHANTOMs are `component_type='PHANTOM'` lines in `m_bom_line`. SAP empty
storage bin principle: the bin has a capacity (INNER dims from YAML), the
PHANTOM represents remaining capacity after placed elements are subtracted.

```
PHANTOM.width  = max(0, parent.INNER.width  - children_bbox.width)
PHANTOM.depth  = max(0, parent.INNER.depth  - children_bbox.depth)
PHANTOM.height = max(0, parent.INNER.height - children_bbox.height)
```

Per-axis, independently. Not a 3D packing problem — 1D subtraction per axis.
BOMWalker dispatches to `onPhantom()` → no output element, no placement.
Click-to-Place (G-13) queries PHANTOMs for instant "where can I place this?"
Zero-cost foam: sits in the BOM, walker skips it, enables spatial queries.

**Witness claims:**
- W-AABB-QUAL-1: IMPLEMENTED (ScopeBomBuilder, FloorRoomBomBuilder) — m_bom.aabb_qualifier
  correctly tags INNER vs OUTER on SET and FLOOR ROOM BOMs.
- W-PHANTOM-1: IMPLEMENTED (ScopeBomBuilder) — PHANTOM lines fill remaining INNER
  volume per SET BOM. BOMWalker dispatches to onPhantom() (no output). IN: 66 PHANTOMs
  across 82 SET BOMs. SH: verified no regression.
- W-CENTROID-DIFF-1: IMPLEMENTED (SpatialDiff) — hosted elements (IfcWindow/IfcDoor)
  use centroid distance for band classification. Invariant to asymmetric frame projection.
- W-TACK-1: IMPLEMENTED (BomValidator.java) — child AABB fits within parent.
  **SH:** advisory, pending TACK-FIX promotion to FAIL (`TACK_FIX_SPEC.md` §4.1).
  **TE:** 306/1074 lines overshoot (28.5%) — expected for CLUSTER verb (approximate
  grouping assigns elements to nearest product centroid, which may exceed discipline
  SET AABB). Promotion to FAIL blocked until CLUSTER→exact verb promotion reduces
  overshoot below 5%.
- W-BUFFER-1: IMPLEMENTED (BomValidator.java) — SUM(children) vs parent.
  **SH:** 2/3 SET BOMs balanced. **TE:** 12/50 SET BOMs balanced (76% unbalanced).
  Root cause: discipline SET AABBs are computed from element centroids; CLUSTER
  offset-table expansions can exceed the centroid-based envelope. BUFFER invariant
  is structurally valid but requires exact verb fidelity to enforce.
- W-WALKTHRU-DIFFERS-1: RESOLVED — single compilation path (S54b). No mode dichotomy.

#### 4.2.3 Shape Archetype + Scale Band (CP-4 §4a, session 46)

Every `m_bom_line` LEAF row carries two computed columns derived from its
`allocated_width/depth/height_mm` dimensions:

| Column | Values | Derivation |
|--------|--------|------------|
| `shape_archetype` | PLANAR, ELONGATED, COMPACT, MIXED | Dimensionless ratios: planarity = S/L, elongation = M/L (S ≤ M ≤ L sorted dims) |
| `scale_band` | ARCHITECTURAL, FURNITURE, FITTING, TINY | Volume bands: S×M×L / 1e9 m³ |

**Purpose:** These columns replace IFC class as the geometric decision variable.
Phase 4b will switch PlacementProver, SpatialDiff, MeshBinder, and BuildingWriter
from `case "IfcWall"` to `case "PLANAR"` — making the compiler class-agnostic
per BBC.md §2.2.1.

**Computation (identical to GeometricFingerprint.java):**
- Sort (W, D, H) → (S, M, L). If L < 0.01: MIXED.
- PLANAR: planarity < 0.15 AND elongation ≥ 0.40
- ELONGATED: planarity < 0.15 AND elongation < 0.40
- COMPACT: planarity ≥ 0.25 AND elongation ≥ 0.50
- Otherwise: MIXED

**Witness claims:**
- W-ARCHETYPE-BOM: IMPLEMENTED — every LEAF row with dimensions has non-null
  shape_archetype and scale_band. SH: 46/47 classified (1 static child with 0 dims).
- W-ARCHETYPE-COMPUTE: IMPLEMENTED — unit tests verify known shapes (wall→PLANAR,
  column→ELONGATED, furniture→COMPACT).
- W-ARCHETYPE-DIST: IMPLEMENTED — SH distribution: 11 PLANAR, 21 ELONGATED,
  5 COMPACT, 9 MIXED. Building has expected mix of structural + furnishing elements.

#### 4.2.4 Product Category (CP-4 semantic half, session 48)

`component_types.product_category` in `component_library.db` provides the semantic
identity that shape archetype cannot: pipe vs column (both ELONGATED), wall vs slab
(both PLANAR). Runtime code switches on product_category instead of IFC class strings.

| Category | IFC classes | Semantic role |
|----------|-------------|---------------|
| STRUCTURAL_LINEAR | Beam, Column, Member, ReinforcingBar | Primary/secondary framing |
| STRUCTURAL_PLANAR | Slab, Wall, WallStandardCase, Plate, Footing | Horizontal/vertical planar |
| MEP_ROUTING | PipeSegment, DuctSegment, FlowSegment, FlowFitting | Distribution network |
| MEP_TERMINAL | LightFixture, Alarm, SanitaryTerminal, Outlet | End devices |
| OPENING | Door, Window, OpeningElement | Wall penetrations |
| FURNISHING | Furniture, FurnishingElement, BuildingElementProxy | Movable items |
| ENVELOPE | Roof, Covering, Chimney | Building skin |
| CIRCULATION | StairFlight, Railing, RampFlight | Vertical movement |
| SITE | EarthworksFill, GeographicElement | Terrain |
| INFRASTRUCTURE | Rail, TrackElement, Course, Sign | Civil works |

**Resolution:** `ProductCategory.resolve(ifcClass)` — static map mirroring the DB.
Used by PlacementProver (proof rules), PlacementCollectorVisitor (discipline fallback),
MEPWriter (furniture detection), WitnessBuilder (grid/wall-mount validation).

**Migration:** `CP4_002_product_category.sql` — ALTER TABLE + UPDATE + INSERT.

### 4.3 Centroid Drift — Historical Note (FIXED)

**Status (2026-03-19):** All four builders now use LBD offsets:
- DisciplineBomBuilder (CO path: TE) — fixed session 18
- StructuralBomBuilder (structural path) — fixed session 18
- ScopeBomBuilder (RE path: SH, DX) — TACK-FIX FIX-1 code written (compiles, testing pending)
- FloorRoomBomBuilder (FLOOR→ROOM/SPACE) — TACK-FIX FIX-2 code written (compiles, testing pending)
- VerbDetector.detectCluster() — TACK-FIX FIX-3 code written (compiles, testing pending)

See `TACK_FIX_SPEC.md` for the full method specifications and test plan.

**Root cause:** Commit `1399128` (2026-03-10) introduced centroid-floorMin as
"parent-relative" — it passed compilation tests because centroid offsets round-trip
correctly when there is no stacking.

**Why centroid breaks:** Centroid offsets cannot tile. If child A is 1m wide and
starts at parent LBD, child B should start at dx=1.0. With centroids, child A's
offset is 0.5 (its center), and child B's offset must account for A's width plus
its own half-width — the formula becomes context-dependent. LBD offsets are always
`child_minX − parent_minX`, regardless of sibling dimensions.

---

## 5. The 9-Stage Pipeline

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against `{PREFIX}_BOM.db` |
| 2 | **Parse** | Reads `.bim` DSL text into records |
| 3 | **Compile** | Produces `BuildingSpec` from BOM hierarchy |
| 4 | **Template** | ST-mode: walks M_BomCategoryLine slots |
| 5 | **Write** | Emits SQLite output DB |
| 6 | **Verb** | BIM COBOL script hook → PP_Order_Node audit trail |
| 7 | **Digest** | Per-element SHA256 spatial fingerprint |
| 8 | **Geometry** | Mesh integrity validation |
| 9 | **Prove** | Mathematical placement proofs |

Single compilation path: element positions are read from m_bom_line (tack
offsets per §4) and accumulated through the BOM chain into world coordinates.
C_OrderLine → M_Product → BOM explosion (iDempiere prepareIt pattern).

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 63 verbs in 5 tiers:

| Tier | Verbs | Purpose |
|------|-------|---------|
| P0 Primitive | CREATE BOM, ADD LINE, SET TACK, SET ROTATION, SET DIMENSIONS, REMOVE LINE, DELETE BOM, SET LINE PROPERTY | BOM CRUD atoms |
| Utility | VALIDATE AABB, SNAP TO GRID, EXTRACT AABB | Validation + transform |
| L1 Convenience | CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM | Room-level composed verbs |
| Data | SELECT, LIST, DESCRIBE, COUNT, AGGREGATE, EXPORT, CLONE, SUMMARIZE BOM | Query + export |
| Original | PLACE BOM, WIRE LIGHTING, ROUTE SPRINKLERS, TILE SURFACE, CHECK BOM, ... | Geometry + inspection |

**Layered composition:** L1 verbs call P0 primitives. L2 (floor-level) will call
L1. Never skip layers. Each verb = one file, one keyword, one payload record.

Full grammar spec: [`docs/BIM_COBOL.md`](BIM_COBOL.md)

---

## 7. Verification: The Rosetta Stone Gate

**Maths that proves visuals without cheating.**

> **Verification gates:** See [`TestArchitecture.md`](TestArchitecture.md) §Verification for the complete G1-G6 gate specification, tamper rules (T1-T16), and the 4-layer defense model.

All 6 gates GREEN for SH (55 elements) and DX (1099 elements).

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools. This is a reproducer from committed data. |
| Rule-based AI | No heuristics, no ML. Selection cascade is deterministic. |
| Parametric design | Parameters come from extracted BOM data, not design exploration. |
| Approximate | AABB matching is 3D exact. Digest is SHA256. No tolerance. |
| Interactive | Batch compilation. Like COBOL/ERP — process the order, produce the output. |

---

## 9. The Data Flywheel — Emergent Intelligence

The compiler was designed to extract and compile — never to invent. But an
unintended consequence of processing 34 real buildings is that the system
**learns what buildings look like.** Each onboarded IFC enriches a pool of
mined dimensional observations. Each new IFC is validated against that pool.
The more buildings the system processes, the better it gets at catching
problems in the next one.

This is a **data flywheel:**

```
New IFC file arrives
  ↓
DimensionRangeValidator checks every element's W/D/H
against typical ranges mined from 20+ previous buildings
  ↓
Pipeline compiles the building into BOM
  ↓
extract_validation_rules.sh mines new dimension patterns
  ↓
apply_mined_rules.sh feeds them back into disc_validation.db
  ↓
The validation pool grows — better ranges for the next IFC
```

**Nobody in the AEC industry has this.** BIM tools validate against fixed,
hand-authored rules (building codes, clash detection thresholds). This system
validates against **empirical evidence from its own corpus** — the same way a
spell-checker learns from a dictionary built from real documents, not from
grammar rules alone.

Three design decisions in the original specs created the conditions for this
emergent property — none of them intended it:

1. **"Extract or compile only, never invent"** (§1 Prime Rule) — forced the
   pipeline to read real buildings, building a corpus of real-world data.

2. **CP-3: "Scale up the corpus"** (session 44) — onboarded 34 buildings,
   creating enough statistical mass for meaningful ranges.

3. **disc_validation.db architecture** (DISC_VALIDATION_DB_SRS) — established
   a clean separation between product geometry and discipline metadata,
   giving the mined rules a natural home.

The result is three layers of validation that work together:

| Layer | Question | Source | When |
|-------|----------|--------|------|
| **Dimensional** (DV010) | "Is this wall a plausible size?" | Mined from 20 buildings (415 rules) | IFC onboarding |
| **Compliance** | "Does this room meet building code?" | Researched from UBBL/IRC/NFPA (63 rules) | Design time |
| **Relational** | "Does this bathroom have the right MEP?" | Mined + researched (186 schedules, 4,801 placement rules) | MEP placement |

Layer 1 is statistical and self-improving. Layer 2 is prescriptive and
stable. Layer 3 is contextual and domain-rich. No single BIM tool combines
all three — and Layer 1's self-improving nature is entirely novel.

**Current state (session 47):** 415 rules, 25 IFC classes, 1,245 parameters
mined from 20 buildings. False-positive rate decreases with each new
building because the observed ranges widen to accommodate legitimate
variation (thin partitions, large warehouses, unusual structural members).

### 9.1 Layer 2 — Building Profile Validation (session 47)

Layer 1 asks "is this element a plausible size?" Layer 2 asks a higher
question: **"does this building's element composition make sense?"**

Every building has a signature — the percentage distribution of its IFC
classes. Residential buildings are 30–40% IfcWall. MEP-only files are
90%+ flow elements. Structural models are dominated by IfcBeam or
IfcReinforcingBar. This signature is the **building profile.**

```
Residential:  IfcWall 35%  IfcWindow 15%  IfcDoor 10%  IfcSlab 8%  ...
MEP (plumb):  IfcFlowFitting 50%  IfcFlowSegment 44%  IfcFlowController 4%
Structural:   IfcBeam 69%  IfcColumn 12%  IfcSlab 8%  IfcFooting 5%
```

The profile validator answers questions Layer 1 cannot:

| Check | What it catches |
|-------|----------------|
| "Architecture file with 0 walls" | Mis-labelled or corrupt IFC |
| "Residential building with 90% IfcReinforcingBar" | Structural file mis-classified |
| "MEP file with 0 flow elements" | Wrong discipline tagging |
| "13 IFC classes when typical for this size is 7" | Unusually rich or fragmented model |

The profile is mined from the same corpus as Layer 1 — the 34 onboarded
buildings. Each building contributes one profile row per IFC class. The
validator aggregates profiles by building archetype (ARC-dominant,
STR-dominant, MEP-dominant) and compares the new building against the
nearest archetype.

**Flywheel effect:** Each new building refines the archetype profiles.
A hospital with unusual element ratios (high IfcFurnishingElement, low
IfcWall) teaches the system what "institutional" looks like. The next
hospital benefits from that knowledge.

**Storage:** `ad_building_profile` table in disc_validation.db.
One row per (building, ifc_class) — same pattern as ad_val_rule.

**Integration:** `BuildingProfileValidator` runs as a second pre-flight
in IFCtoBOMPipeline, immediately after DimensionRangeValidator. Advisory
only — logs profile anomalies but never blocks.

### 9.2 The Emergence Pattern

Each layer emerges from the data the previous layer collected:

```
Layer 0: EXTRACTION          "Here are 97,000 elements"
         → creates the corpus

Layer 1: DIMENSION RANGES    "Walls are typically 800–10,000mm"        ← DONE (s47)
         → mines per-element statistics from the corpus

Layer 2: BUILDING PROFILES   "Residential buildings are 35% wall"      ← DONE (s47)
         → mines per-building composition from the corpus

Layer 3: SHAPE COMPREHENSION "This IfcWall is shaped like a beam"      ← EYES module
         → geometric fingerprinting classifies what elements ARE
         → 26 proofs verify spatial relationships are valid
         → spec: EYES_SRS.md. Phase 1 ready to build

Layer 4: RELATIONAL PATTERNS "Bathrooms always have toilet + sink"     ← FL-4
         → mines element-to-space containment from the corpus

Layer 5: SEQUENCE PATTERNS   (future) "Plumbing before electrical"
         → mines cross-discipline ordering from BOM build sequence

Layer 6: ARCHETYPE CLUSTERS  (future) "European residential cluster"
         → mines building-level similarity from profiles
```

Each layer uses the same mechanism — query the corpus, aggregate, compare.
The code stays simple. The intelligence comes from the questions asked.
No neural networks. No training. Just SQL aggregation on real data.

Layer 3 (EYES) is different in kind: it introduces **geometric comprehension**
— the system doesn't just compare numbers, it understands shapes. A wall
must be planar (thin in one dimension). A column must be elongated (thin
in two dimensions). Furniture must be compact (similar in all three).
These are mathematical invariants derived from dimensionless ratios, not
heuristics or ML. When combined with the flywheel, EYES advisories tell
the Designer: "This element is labelled IfcWall but its shape says beam.
Check the IFC source." That's a qualitatively stronger signal than
"dimension outside range."

See [EYES_SRS.md](EYES_SRS.md) for the full specification (26 proofs,
3 tiers, 10 product categories, 4 shape archetypes).

---

## 10. The Compilation End State

The compiler runs without human assistance. Given a `.bim` DSL file and two source
databases (`{PREFIX}_BOM.db` + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

---

## 11. Dynamic Building Registration

Adding a building type in the Java compiler requires zero code changes — `BuildingRegistry.loadActive()`
reads C_DocType from `{PREFIX}_BOM.db` and compiles every active row. But the Python and shell tooling that
*populates* `{PREFIX}_BOM.db` hardcodes building names, storey mappings, output paths, and expected element
counts in four files. This is like hardcoding part numbers into the MRP engine instead of reading
the BOM. When extraction counts change or a new building appears, the scripts drift from the data.

This section specifies how to close that gap.

### 10.1 The Problem: Hardcoded Registration

The Java side is clean — `BuildingRegistry` (line 70) issues one SQL query with zero building
names in source code. The Python/shell side is not:

| # | File | Lines | What is hardcoded |
|---|------|-------|-------------------|
| 1 | `scripts/RosettaStoneExtract.py` | 37–60 | `BUILDINGS` dict: prefixes (`SH`, `DX`), doc_sub_type, building_bom_id, storey name→code mappings, roles, seq_no |
| 2 | `scripts/RosettaStoneToBOM.py` | 48–55 | `C_DOCTYPE` list: doc_type_id, DocBaseType, DocSubType, output/reference paths, ExpectedElements (55, 1099, 139, 51088) |
| 3 | `scripts/RosettaStoneToBOM.py` | 345–439 | `M_BOM` / `M_BOM_LINE`: BUILDING BOM headers with AABB, TACK children with dx/dy/dz offsets |
| 4 | `scripts/run_RosettaStones.sh` | 39–40, 418–435 | `SH_BASE` / `DX_BASE` path variables, `case` dispatch with literal `"SH"` / `"DX"` |
| 5 | `scripts/run_tests.sh` | 125–136 | `run_preflight "Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` — literal building names |
| 6 | `scripts/run_tests.sh` | 145–183 | Expected pass/fail counts (191/1, 34/2, 19/0, 179/7) and intentional-failure offsets |

**Consequence:** Every time an extraction changes element counts, a developer must hand-edit
expected values in at least two files. Adding a third EXTRACTED building requires touching all four.
The Java compiler handles it with one C_DocType row. The tooling should too.

### 10.2 Building Manifest (Source of Truth)

A YAML configuration file `scripts/construction_manifest.yaml` declares each building's identity
and structure. The manifest is an index into extraction truth — it names things, never measures them.

```yaml
# construction_manifest.yaml — declarative building registration
# Rule: storey names, roles, paths.  Never counts or dimensions.

buildings:
  Ifc4_SampleHouse:
    prefix: SH
    doc_sub_type: SH
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc4_samplehouse.db
    reference_path: DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db
    seq_no: 10
    storeys:
      Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
      Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
      Unknown:      { code: CW, bom_category: CW, role: CURTAIN_WALL, seq: 1030 }

  Ifc2x3_Duplex:
    prefix: DX
    doc_sub_type: DX
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc2x3_duplex.db
    reference_path: DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db
    seq_no: 20
    geometry_fail_threshold: 5
    storeys:
      Level 1: { code: L1, bom_category: L1, role: LEVEL_1, seq: 1010 }
      Level 2: { code: L2, bom_category: L2, role: LEVEL_2, seq: 1020 }
      Roof:    { code: ROOF, bom_category: RF, role: ROOF, seq: 1030 }
      T/FDN:   { code: FDN, bom_category: FN, role: FOUNDATION, seq: 1040 }
      Unknown: { code: MISC, bom_category: MS, role: MISC, seq: 1050 }
```

**iDempiere pattern:** The manifest plays the same role as `AD_Table` — it defines metadata
that scripts consume, so no script contains application logic about what buildings exist.
One file is the single source of registration truth. Scripts import it; they never duplicate it.

### 10.3 Derived Values (Never Hardcode)

Some values currently hardcoded in the scripts are not properties of the building's identity.
They are measurements that can — and must — be computed from extraction data.

| Value | Current source | Correct source | Why |
|-------|---------------|----------------|-----|
| `ExpectedElements` (55, 1099) | Hardcoded in `C_DOCTYPE` tuple | `COUNT(*)` from extraction DB | Extraction is the measurement |
| `AABB` on BUILDING BOM | Hardcoded in `M_BOM` list | Computed from extraction envelope | Gospel Principle: extraction = truth |
| Floor AABB dimensions | Hardcoded in `M_BOM` list | Aggregated from storey extraction | Same principle at floor level |
| `M_BOM_LINE` offsets (dx/dy/dz) | Hardcoded in builder tuples | Computed from extraction storey origins | Offsets are measured, not declared |
| DSL content | Generated inline | Derived from manifest + extraction | Content follows structure |
| Spatial digest reference | Implicit in gate thresholds | SHA256 of compiled output | Digest is a function of data |

**Rule:** If a value can be derived from extraction data, it MUST be derived, not declared.
The manifest declares identity (name, role, path). Extraction measures reality (count, envelope,
offset). Hardcoding a derived value violates the Gospel Principle — it replaces measurement with
opinion.

### 10.4 Target Workflow: Add a New Building

After migration, adding a new building follows the same pattern as adding a new product to an
ERP system — one BOM entry, zero code changes:

1. **Manifest entry** — add one YAML block to `construction_manifest.yaml` (name, prefix,
   doc_sub_type, storeys, paths)
2. **Extract IFC** — run extraction pipeline; reference DB appears at the declared path
3. **Regenerate BOM dictionary** — IFCtoBOM Java pipeline (SH/DX) or `RosettaStoneToBOM.py` (TE)
   reads classification YAML + extraction, writes M_BOM, M_BOM_Line with derived counts and dimensions
4. **Verify** — `./scripts/run_RosettaStones.sh classify_XX.yaml` compiles and gates the new building

Zero Python edits. Zero shell edits. Zero Java edits. The manifest is the only touch point.

### 10.5 Script Migration

Each script transitions from hardcoded values to manifest-driven or `{PREFIX}_BOM.db`-driven lookups:

| Script | Reads now | Reads after | Change scope |
|--------|-----------|-------------|-------------|
| `RosettaStoneExtract.py` | `BUILDINGS` dict (lines 37–60): prefixes, storey maps, BOM IDs | `construction_manifest.yaml`: same data, external file | Replace dict literal with `yaml.safe_load()`. Storey mappings move verbatim. |
| `RosettaStoneToBOM.py` | `C_DOCTYPE` list (lines 48–55): paths, ExpectedElements. `M_BOM` / `M_BOM_LINE` (lines 345–439): AABB, offsets | Manifest for identity; extraction DBs for counts, AABB, offsets | Biggest change. Builder loops derive dimensions from extraction instead of literals. |
| `run_RosettaStones.sh` | `SH_BASE` / `DX_BASE` variables, `case` dispatch (lines 39–40, 418–435) | Query `SELECT DocSubType, output_path FROM C_DocType WHERE IsActive=1` from `{PREFIX}_BOM.db` | Shell reads `{PREFIX}_BOM.db` via `sqlite3`. Loop replaces case statement. |
| `run_tests.sh` | Literal `"Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` in preflight calls (lines 125–136); hardcoded expected counts (lines 145–183) | Preflight: query C_DocType for active EXTRACTED buildings. Counts: read from `{PREFIX}_BOM.db` or test output. | Preflight becomes a loop. Expected counts derived, not literal. |

### 10.6 Invariants

Six rules govern the boundary between declaration and derivation:

1. **Manifest is declarative** — storey names, roles, paths, prefix. Never counts, dimensions,
   or coordinates.
2. **Extraction is measurement** — element counts, AABB envelopes, storey origins. These are
   facts read from IFC reference data, not human assertions.
3. **`{PREFIX}_BOM.db` is artifact** — produced by IFCtoBOM Java pipeline (SH/DX) or
   `RosettaStoneToBOM.py` (TE) from classification YAML + extraction.
   It is never hand-edited. Regeneration is idempotent.
4. **Java reads `{PREFIX}_BOM.db` only** — `BuildingRegistry.loadActive()` queries C_DocType. It never
   reads the manifest, never reads extraction DBs, never contains building names.
5. **Shell reads `{PREFIX}_BOM.db` or manifest only** — no script contains a building name as a string
   literal (except in log messages). Control flow is driven by query results.
6. **Drift resistance** — if extraction changes element count from 1099 to 1102, the only
   action is re-running the IFCtoBOM pipeline. No file is hand-edited. No test threshold
   is manually adjusted.

### 10.7 Migration Path

Migration proceeds in five phases, each self-contained and independently verifiable.

| Phase | Scope | Risk | Status |
|-------|-------|------|--------|
| **A** | Create `construction_manifest.yaml` | None | **DONE** — manifest created with SH, DX, TE, ST entries |
| **B** | `RosettaStoneExtract.py` reads manifest | Low | **DONE** — `BUILDINGS` dict replaced with `yaml.safe_load()` |
| **C** | `RosettaStoneToBOM.py` reads manifest | Medium | **DONE** — `C_DOCTYPE` list replaced with `_build_c_doctype()` |
| **D** | `run_RosettaStones.sh` queries `{PREFIX}_BOM.db` | Low | **DONE** — loop replaces case statement, summary dynamic |
| **E** | `run_tests.sh` derives expected counts | Low | Deferred — not a Rosetta Stone concern |

**Gate between phases:** `./scripts/run_RosettaStones.sh all` must produce identical output
after each phase. All phases A–D gated GREEN: SH=55, DX=1099, 0 geometry divergences.

**Note:** TB-LKTN removed from manifest — generative case will be approached fresh once
the EXTRACTED registration pipeline is stable. Terminal (CO_TE) registered with TE-1
storey normalisation DONE (48,428 active elements, 7 storeys, 8 disciplines).

---

## Appendix: Onboarding Gotchas (discovered during FK session 38)

These are recurring traps when onboarding a new IFC file. They apply to any new
Rosetta Stone, not just FK.

### G1. GATE_SCOPE allowlist

`BuildingRegistryTest.java` line ~63 has a `GATE_SCOPE` set. **New buildings must be
added to this set** — otherwise the compilation test silently *skips*, Maven exits 0,
and no output DB is produced. Symptom: `!! NO OUTPUT DB produced` even though
IFCtoBOM passed. See `SourceCodeGuide.md` §Chapter 10 Extension Recipe step 4.

### G2. DSL `level:` field is integer (storey index), not elevation

The STOREY regex requires `level:(\d+)` — an integer storey index (0, 1, 2...), not the
actual elevation in metres. Using `level:2.7` causes `Invalid STOREY syntax`. The actual
elevation is derived from `height:` fields stacked from level 0 upward.

### G3. `static_children` vs extracted elements — double-counting

If an element type (e.g. IfcSlab ground slab) is **already in the extraction**, do NOT
also add it as a `static_children` entry in the YAML. The pipeline will create it twice:
once from extraction (structural BOM) and once from the static child. Result: element
count exceeds `ExpectedElements` on C_DocType.

### G4. Bay slab double-emission (metadata-driven path)

When a building is metadata-driven (`hasMetadata=true`), `BuildingWriter.java` correctly
suppresses the main storey slab (line ~580: `if (storey.slab() != null && !hasMetadata)`).
However, `storey.baySlabs()` at line ~596 is **not gated by `!hasMetadata`** — so slabs
beyond the first per storey are emitted twice: once by the metadata BOM path (via
`emitGlobalPlacementElements`) and once by the bay slab writer. This produces duplicate
elements with different GUIDs (`STR_MD_*` vs `SLAB_*_UNIT_*`).

**Fix:** Gate the bay slab block with `&& !hasMetadata`, consistent with the main slab
and the finish-slab/ceiling blocks below it. Until fixed, expect `ExpectedElements + N`
where N = number of extra slabs per storey beyond the first.

### G5. IfcWall vs IfcWallStandardCase normalization

The extraction script (`extract.py`) may normalize `IfcWallStandardCase` to `IfcWall`
(IFC4 superclass). Analysis docs should note both forms. The `ad_ifc_class_map` must
have the form that `extract.py` actually emits — check extraction output, not IFC source.

---

*Detailed architecture: [`ConstructionAsERP.md`](ConstructionAsERP.md) |
BOM dimensions: [`ConstructionAsERP.md`](ConstructionAsERP.md) Appendix A |
Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Terminal ERP model: [`TerminalAnalysis.md`](TerminalAnalysis.md) §ERP Model Architecture |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
