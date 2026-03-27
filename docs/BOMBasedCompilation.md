# BOM-Based Compilation — The Master Spec

> *If you can draw it, you can build it. If you can BOM it, you can compile it.*

<div class="bim-banner" markdown>
<b>Everything is a BOM.</b> [Houses](SampleHouseAnalysis.md), [terminals](TerminalAnalysis.md), [bridges](InfrastructureAnalysis.md), [ships](ShipYard.md) — same pipeline, different data.
</div>

A [Bill of Materials](https://en.wikipedia.org/wiki/Bill_of_materials) is a
recipe: one parent product, N child products, each with a quantity. Each child
can itself be a BOM — a building contains floors, a floor contains rooms, a
room contains furniture, [recursively](https://wiki.idempiere.org/en/Manufacturing#Bill_of_Materials).
Each level is atomic and self-contained.

This document specifies how that recursive BOM model compiles a building.
Read the [MANIFESTO](MANIFESTO.md) first for the ERP world view.

**Quick navigation:**

| Section | What it covers |
|---------|---------------|
| [§1 Entity Mapping](#1-idempiere-entity-mapping) | iDempiere tables → BIM Compiler equivalents |
| [§2 Compilation Model](#2-the-compilation-model) | How BOM recipes become placed elements |
| [§3 Compilation Modes](#3-two-compilation-modes) | Extracted (Rosetta Stone) vs Generative |
| [§4 Tack Convention](#4-tack-convention-the-spatial-handshake) | The dx/dy/dz spatial offset model |
| [§5 Pipeline](#5-the-9-stage-pipeline) | 9-stage compilation pipeline |
| [§6 Verbs](#6-bim-cobol-verb-driven-bom-mutation) | 75 domain verbs (TILE, ROUTE, FRAME, CLUSTER) |
| [§7 Verification](#7-verification-the-rosetta-stone-gate) | 6 mathematical gates |
| [§9 Data Flywheel](#9-the-data-flywheel-emergent-intelligence) | How 35 buildings teach the compiler |
| [§10 End State](#10-the-compilation-end-state) | What the compiled output looks like |

**Related specs:**
[DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

---

## 1. iDempiere Entity Mapping

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element (wall, door, pipe) or assembly (floor, room SET, building). IsBOM=Y → has M_BOM children |
| **M_Product_Category** | Product classification: ARC, STR, FP, MEP, FURNITURE, etc. |
| **M_BOM** (bill of materials) | Assembly recipe attached to a product. A product IS a BOM when IsBOM=Y |
| **M_BOM_Line** (BOM child) | Recipe line: child product + qty + relative offset (dx/dy/dz). NOT a per-instance placement |
| **C_Order** (work order) | Construction project for a specific building. ONE doc type: "Construction Order" |
| **C_OrderLine** (order line) | Instance of a product in this project. BOM Drop explodes parent → child lines |
| **C_DocType** (document type) | "Construction Order" — one only. Classification metadata, NOT a compilation driver |
| **CO_EmptySpace** (warehouse slot) | *(REMOVED S74 — replaced by M_BOM_Line dx/dy/dz. W008 dropped tables)* |
| **[AD_ChangeLog](https://wiki.idempiere.org/en/AD_ChangeLog)** (audit trail) | Full provenance — every PLACE, DELETE, MOVE, RESIZE logged with before/after state. UNDO/REDO stack |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **[C_Project](ProjectOrderBlueprint.md#2-c_project-site-as-bom)** (project) | Site development — groups C_Orders under one project (budget, schedule, milestones) |
| **[C_Location](https://wiki.idempiere.org/en/C_Location)** (address/coordinates) | Plot location — independent dimension linking site coordinates to C_Order or C_Project |
| **[M_Locator](https://wiki.idempiere.org/en/M_Locator)** (aisle/lot/bin) | Plots akin to warehousing ALB (Aisle/Lot/Bin) but in [terrain](PDF_TERRAIN.md) context — with [2D layout](2D_LAYOUT.md) capability |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

The BOM hierarchy maps directly to the building hierarchy:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
 │         │         │        │        │
 building  storey    room    furniture  leaf product
                              group    (door, pipe, cabinet)
```

**Two selection dimensions** (see [MANIFESTO.md](MANIFESTO.md) §The Pattern for the full rationale):

1. **Category** (M_Product_Category) — WHAT: ARC, STR, FP, KITCHEN, BEDROOM, BATHROOM
2. **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH: width × depth × height in mm

### 1.1 Disciplines Are Metadata, Not Structure

Disciplines (ARC, STR, FP, ACMV, ELEC, SP, CW, LPG) are metadata partitions, not structural
divisions. See [MANIFESTO.md](MANIFESTO.md) §AD_Org for the discipline-as-metadata pattern.

| iDempiere | BIM Compiler |
|-----------|-------------|
| Document classification via metadata | [AD_Org](DISC_VALIDATION_DB_SRS.md) (Discipline enum — the organisational partition) |
| Same document engine | Same BOM walker (§2.2, §4) |
| AD_Val_Rule per document type | AD_Val_Rule per discipline (sprinkler spacing, clearances) |
| Column Callout on field change | Per-discipline product validation |
| Invoice → tax/charge validation | FP BOM → fire code compliance |
| ModelValidator.docValidate() | PlacementValidator per jurisdiction |

The BOM walker does not know what ARC or FP means. It just recurses (§2.2.1).
The `AD_Org_ID` on `m_bom` and `C_OrderLine` is the hook that AD_Val_Rule uses
to fire the right validation rules (see [DocValidate.md](DocValidate.md)).

### 1.2 Discipline Routing — Three States per Discipline

Each discipline in YAML has exactly three possible states:

| YAML value | Meaning | What happens |
|------------|---------|-------------|
| `{prefix}_BOM` | Pipeline populates from named BOM | BOM walker includes this discipline's sub-tree from `{prefix}_BOM.db` |
| `DocEvent` | Validation handles this discipline | DocEvent discovers elements, applies AD_Val_Rule per discipline + shared rules |
| Absent | Discipline does not exist for this building | Nothing. No BOM, no validation. The building genuinely has no such discipline. |

```yaml
# Terminal: all 8 disciplines from extraction BOM
disciplines:  { ARC: TE_BOM, STR: TE_BOM, FP: TE_BOM, ACMV: TE_BOM, ... }

# SampleHouse: ARC only (absent = no such discipline)
disciplines:  { ARC: SH_BOM }

# Generative: ARC from BOM, MEP via DocEvent validation
disciplines:  { ARC: DM_BOM, FP: DocEvent, ELEC: DocEvent, CW: DocEvent, SP: DocEvent }
```

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

## 2. The Compilation Model

Reference buildings are treated as **authoritative, immutable truth**.

```
Extract (IFC source)  →  Commit ({PREFIX}_BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**`{PREFIX}_BOM.db` is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

**EXTRACTED** provenance (SH, DX, TE): every element traces to `I_Element_Extraction`.
**GENERATIVE** provenance (DemoHouse, BIM Designer): elements trace to BOM templates
+ user spatial edits. The provenance field distinguishes origins. See `G4_SRS.md` §2.4.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

**Schema-Not-Geometry rule:** Every validation check must be expressible as SQL
over the 5-database schema. If AABB arithmetic is needed, ask: does an IFC
relationship exist that could be extracted as a column instead? If yes, fix the
schema, not the geometry code. The 5-database architecture is a semantic firewall.
Exception: ERP-maths on product dimensions (e.g., M12 pipe clearance) is legitimate
when no IFC relationship would improve it.
See `DocValidate.md` §15.6 (decision tree), `LAST_MILE_PROBLEM.md` R21-R24 (schema gaps),
`BIM_COBOL.md` §20 (spatial predicate verbs).

**No Parametric Mesh in Pipeline.** All geometry comes from `component_library.db`
(LOD_ hash prefix). The pipeline MUST NOT generate parametric bounding boxes (GEO_
hash prefix). ASI controls per-instance sizing; the compiler scales library LODs,
never creates geometry. `G5-PROVENANCE` Check 6 enforces zero GEO_ hashes.
`createBoxGeometry` and `bindParametric` must not exist in any compilation code path.

**IFC already solves geometry.** The IFC schema carries relational structure
(`IfcRelAggregates`, `IfcRelVoidsElement`, `IfcRelConnectsElements`, `IfcRelDefinesByType`)
pre-digested into SQLite by the FederatedModel DB. The BOM layer adds manufacturing
semantics IFC lacks (m_bom/m_bom_line with qty, verb_ref, tack offsets). Editing a
building = configuring a relational tree, not manipulating geometry (BIM_Designer.md §17.19).

---

## 2.1. IFC→BOM Stage — Top-Down AABB Decomposition

*Preamble to compilation: how extracted IFC data becomes a BOM tree.*

The IFCtoBOM pipeline reads `I_Element_Extraction` (component_library.db) and a
classification YAML, then produces a `*_BOM.db` with spatial arrangement (m_bom + m_bom_line).
Products are written to component_library.db (geometry catalog) AND ERP.db (product master);
BOM DB product copy is deprecated. 12+ BomValidator checks + verb fidelity + 2 pre-flight guards enforce data integrity pre-commit.
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

**Zero-size scope** (`aabb_mm: [0, 0, 0]`): placeholder for rooms with no
scope-assignable elements (e.g., BATHROOM fixtures are structural). SET BOM
created with no leaf lines.

### 2.1.4 Dedup and Product Registration

Identical elements (same `element_ref` pattern) are deduplicated into a single
`M_Product` entry. The BOM line carries `qty` for replicates instead of one
line per instance. This is the iDempiere pattern: 47 duplex receptacles =
1 M_Product (`DUPLEX_RECEPTACLE`) with `qty=47` on the BOM line.

### 2.1.5 Composition Layer (Duplex/Multi-Unit)

CompositionBomBuilder — duplex mirror partition, half-unit and pair BOMs. See `DuplexAnalysis.md`.

### 2.1.6 Discipline Layer (schema_version 2)

For multi-discipline buildings (DX, Terminal), the YAML `disciplines:` map
inserts a discipline BOM level between STOREY and SCOPE SPACE. See
`DISC_BOM_DESIGN.md` §2 for the 5-level hierarchy and §5 for YAML schema v2.
See `TerminalAnalysis.md` §ERP Model Architecture for how discipline codes
sit on M_Product_Category with `doc_type='CO'` and how ROUTE/TILE verbs map to
M_AttributeSet/Instance.

Single-discipline buildings (SH, schema_version 1) skip this layer — no
discipline wrapper needed. The top-level M_Product_Category (RE/CO) determines
which L2 axis is used: rooms (RE) or disciplines (CO). See MANIFESTO.md §Category Cascade.

### 2.1.7 Recipe vs Placement — The BOM Contract

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

**TE factorization:**
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
See [`BIM_COBOL.md`](BIM_COBOL.md) §19 for verb taxonomy,
data flow, and fidelity details.

**Pipeline phases (self-contained):**
1. **BOM pipeline** (`IFCtoBOMPipeline.run()`): extracts → populates catalog (idempotent) → writes `{PREFIX}_BOM.db`
2. **Compile** (`DAGCompiler`): reads `{PREFIX}_BOM.db` + `component_library.db` → produces output.db
   *(Authoritative output DDL: Java `BuildingWriter.initSchema()`, not Python `output_schema.sql`.)*

### 2.1.8 What IFCtoBOM Does NOT Do

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
If it resolves to an `M_Product` in `ERP.db` (product catalog migrated S65;
geometry meshes remain in `component_library.db` via MeshBinder), it is a leaf —
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

The walker recurses until it hits a leaf (depth capped at 20; practical buildings use 4-5).

### 2.2.3 Library Population (Outside Compilation)

New leaf products are created outside compilation: **TopologyMaker** generates
mesh geometry, **Mesh2Library** registers it into `component_library.db`. After
population, the compiler treats it identically to extracted products.

**Orientation metadata:** Each leaf in `component_definitions` carries
`attachment_face`, `up_axis`, `forward_axis`, `orientation`, `default_rotation` —
polarity markers for correct mesh orientation at the tack position.
See `BIM_Designer.md` §8.3 (ASI overrides) and `DocValidate.md` M16/M17.

---

## 3. Two Compilation Modes

### 3.1 Terms

| Term | iDempiere parallel | Definition |
|------|-------------------|------------|
| **ESLine** | S_Resource (spatial workstation) | *(REMOVED S74 — replaced by M_BOM_Line dx/dy/dz. The parent BOM line's offset IS the attachment point.)* |
| **BUFFER** | Phantom BOM line | Fills the gap between SUM(children AABB) and parent AABB. Ensures parent = SUM(children) invariant. |
| **BOM Drop** | PP_Product_BOM explosion | Interactive tree navigation — expand BOM children one level at a time, swap products (same M_Product_Category), add lines. Only needed when making changes. Without BOM Drop, compile explodes the tree automatically (iDempiere Instant BOM Drop pattern). |
| **Instant Drop** | Manufacturing Order processing | No modifications — 1 C_OrderLine references a BOM product, compile explodes the full tree. The quickest hello world test. |
| **Tack** | Origin datum | Left-Back-Down (LBD) corner of AABB = (minX, minY, minZ) = (0,0,0) in own frame. All offsets are parent-LBD to child-LBD. |
| **BOM** | Bill of Materials | Any `m_bom` row. If `child_product_id` resolves to an `m_bom`, the walker recurses into it. There is no MAKE/BUY distinction — the walker decides by existence. |
| **Leaf** | Purchased item | A `child_product_id` that resolves to `M_Product` in `ERP.db` (no matching `m_bom`). The compiler emits the element here. Geometry meshes in `component_library.db`. |

### 3.2 Placement via M_BOM_Line — Parent Owns the Attachment Point

**The child BOM does not know its parent.** A child BOM does not know which
parent hosts it. A leaf product does not know which BOM contains it. This is by design —
a child can be reused in any parent that has a slot for it.

**The parent M_BOM_Line's dx/dy/dz IS the attachment point:**

```
Parent BOM (e.g. FLOOR_TE_GF)
  │
  └─ m_bom_line (children)                            ← parent defines WHAT + WHERE
       │
       ├─ child 1: AD_Org=ARC, dx=0, dy=0, dz=0        ← architecture assembly at origin
       ├─ child 2: AD_Org=STR, dx=500, dy=0, dz=0       ← structural assembly offset 500mm
       └─ ...                                            ← selection cascade (§3.5) picks child
```

**The BOM walker accumulates offsets:** `world_pos = parent_LBD + (dx, dy, dz)`.
One addition per BOM level — that's the entire placement mechanism. When the
selection cascade picks a child BOM, the child's LBD is placed at the parent's
offset position. The child never looks up.

When BOM Drop explodes the tree, C_OrderLine inherits these offsets. No
intermediate table, no slot abstraction — the LBD tack convention (§4) handles
everything.

**Generative consequence:** The child BOM can be swapped, resized, or replaced
without touching any other BOM — the parent owns the attachment point, not the child.

### 3.3 Instant Drop — The HelloWorld Test

One `C_OrderLine` references a BOM product (e.g. BUILDING_SH_STD). No BOM Drop,
no modifications. The compiler explodes the full BOM tree — accumulating tack
offsets, resolving leaves to M_Product geometry. This is the iDempiere **Instant
BOM Drop** pattern. SH (55), DX (1099), TE (48,428) all compile this way.

**Proves:** stacking order, verb expansion, BOM completeness (G1-G6), tack accumulation.

### 3.4 BOM Drop — Interactive Modification

The iDempiere BOMDrop Configurator pattern adapted for BIM. User expands BOM
one level, then **Swap** (same M_Product_Category), **Add** (new C_OrderLine),
or **Remove** (deactivate child line). Compile processes the modified order.

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
fields: **M_Product_Category** and **AABB**.

1. **M_Product_Category** (scope): restricts to same functional type (swap roof → show only RF products)
2. **AABB fit** (primary): replacement must fit within the parent's allocated space
3. **Largest volume** (secondary): maximize space usage
4. **seq_no** (tiebreaker): lower preferred

This is the same browse/filter pattern as iDempiere's product lookup:
filter by M_Product_Category, then by dimensional fit.

> **Beyond BOM Drop:** Exception-based ordering (§1), rule-driven discipline
> addition (§13), order inheritance (§6), reference class compression (§1.2),
> and the full Order Compilation Engine are specified in
> [ProjectOrderBlueprint.md](ProjectOrderBlueprint.md). Implementation plan: §14.

### 3.5.1 AttributeSetInstance — Per-Instance Customization

The Selection Cascade picks the **BOM recipe** (WHAT). The `M_AttributeSetInstance`
(ASI) customizes **each instance** (HOW). See [MANIFESTO.md](MANIFESTO.md)
§M_AttributeSet for the shirt-size analogy and ERP pattern.

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

**Generative flow:** User defines room size → cascade finds matching SET → ASI
overrides stretch walls to new dimensions → compiler resolves `effective = ASI ?? catalog`.
Zero new code — just data on the OrderLine.

*ASI matrix: BIM_Designer.md §8. Schema: output.db. Generative: GENERATIVE_ROOM_SRS.md §6.*

### 3.5.2 Product → Verb Routing via ASI

In iDempiere Manufacturing, each product has a routing (`PP_Product_Planning` →
operations → sequences). We removed PP_Order as too heavy (S73). The lighter
equivalent uses the existing ASI chain to carry verb parameters as structured
per-instance data.

#### The Chain

```
M_Product
  └─ M_AttributeSet_ID → 'BIM_Wall'
       └─ M_AttributeUse → [trim_action, trim_tolerance_mm, joint_type, ...]
            └─ M_Attribute → ValueType, DefaultValue

C_OrderLine
  └─ M_AttributeSetInstance_ID → ASI #47
       └─ M_AttributeInstance → trim_action='CUT_FILL', trim_tolerance_mm=25

AD_Rule
  └─ source_column='dx', rule_type='DIMENSIONAL'
       └─ CalloutEngine → reads ASI → dispatches verb with overrides
```

#### How It Replaces PP_Order Routing

| iDempiere Manufacturing | BIM Equivalent | Why Lighter |
|------------------------|----------------|-------------|
| PP_Product_Planning | M_Product.M_AttributeSet_ID | One FK, not a planning table |
| PP_Order_BOMLine (sequence) | M_AttributeUse.SeqNo | Attribute ordering within set |
| PP_Order_Node (operation) | AD_Rule (reactive callout) | Declarative rules, not operation graph |
| PP_Cost_Collector | Not needed | Costing is on M_PriceList, not per-operation |

#### Resolution at Verb Dispatch Time

When a verb fires (via DiffVerb → CalloutEngine → VerbRegistry):

```
1. Product lookup:    M_Product.M_AttributeSet_ID → 'BIM_Wall'
2. Attribute schema:  M_AttributeUse WHERE M_AttributeSet_ID = 'BIM_Wall'
                      → [trim_action, trim_tolerance_mm, joint_type, ...]
3. Instance values:   M_AttributeInstance WHERE M_AttributeSetInstance_ID = <ASI>
                      → trim_action='CUT_FILL', trim_tolerance_mm=25
4. Default fallback:  M_Attribute.DefaultValue WHERE Name = 'trim_tolerance_mm'
                      → 50 (if no ASI override)
5. Effective params:  effective = ASI_override ?? M_Attribute.DefaultValue
6. Verb execution:    TRIM_WALLS_TO_ROOF(action=CUT_FILL, tolerance=25)
```

This is the same three-tier resolution as §3.5.1 (`ASI_override ?? allocated_*_mm
?? catalog_default`), extended from geometric dimensions to verb parameters.

#### Where the User Works

The user edits **C_OrderLine** — the order line. They never touch M_Product,
M_AttributeSet, or AD_Rule. Those are catalog/engine concerns.

When a product has `M_AttributeSet_ID = 'BIM_Wall'`, it does NOT mean the
wall must be trimmed. It means the wall CAN carry verb-parameter attributes
(trim_action, tolerance, joint_type). The ASI on the order line is the
user's override channel:

- **No ASI** → callout decides from AD_Rule defaults (most common case)
- **ASI trim_action=SKIP** → user explicitly says "don't trim this wall"
- **ASI trim_action=CUT_FILL** → user explicitly says "cut and fill"

Same iDempiere pattern: picking a product on a Sales Order doesn't force a
specific tax rate. The product's tax category defines which rates ARE VALID.
The order line's context (date, jurisdiction) selects the actual rate.
Here: the product's attribute set defines which verb params are valid.
The order line's ASI selects the actual values.

#### What This Means

- **New verb parameter** = `INSERT INTO M_Attribute` + `INSERT INTO M_AttributeUse`.
  No Java change.
- **Per-instance override** = `INSERT INTO M_AttributeInstance` on the order line's ASI.
  No Java change. User edits the order line.
- **New product type with different verb params** = `INSERT INTO M_AttributeSet` +
  map attributes via `M_AttributeUse`. No Java change.
- **AD_Rule** controls WHEN verbs fire. **ASI** controls HOW they execute.
  Both are data. The engine (CalloutEngine + VerbRegistry) is generic.

*Schema: migration/ASI_002_attribute_detail.sql. Callout wiring: DocValidate.md §1.5.
ASI field matrix: BIM_Designer_SRS.md §28.7 + §31.*

### 3.6 The Rosetta Stone — Launch Booster

The Rosetta Stone exercise (SH/DX/TE) proves the pipeline is **lossless**:
Extract → BOM → Compile → Verify (G1-G6 GREEN). It calibrates and proves tack
convention, BOM recursion, verb expansion, and M_BOM_Line placement. Once proven,
the same machinery drives generative compilation where the BOM is authored by a
human or Designer, not extracted from IFC.

**Does NOT prove:** multi-candidate selection, BOM Drop with swap choice
(first test: DemoHouse), or DocValidate compliance (proven separately).

### 3.7 locator_ref — Exception-Order Addressing (Session D)

Every C_OrderLine carries a `locator_ref`: a dot-separated path from the root
that uniquely identifies WHERE in the exploded BOM tree this node sits.

```
Syntax:  segment.segment.segment
Segment: M_Product_Category code (preferred) or bom_id/product_id (fallback)
Example: RE.GF.LI.SOFA_001
         │   │  │  └─ leaf product (no category → product_id used)
         │   │  └─── room category (LI = Living)
         │   └────── floor category (GF = Ground Floor)
         └────────── building category (RE = Residential)
```

**Stability guarantee:** locator_ref is derived from BOM structure
(M_Product_Category at each level), not from runtime state or insertion order.
The same BOM always produces the same locator_refs. This makes them safe for
exception orders to reference across recompilations.

**Exception-order mutations** (ProjectOrderBlueprint.md §1.1):

| Mutation | C_OrderLine state | Compiler behaviour |
|----------|-------------------|-------------------|
| **Remove** | `Qty=0` on a locator_ref | BomDropper skips entire subtree |
| **Compress** | `is_reference_class=1, Qty=N` | Walker instantiates N copies at computed dz offsets |
| **Replace** | Different `family_ref` on a locator_ref | BomDropper swaps the product (existing) |
| **Add** | New C_OrderLine with locator_ref | Direct insertion (addDiscipline, Session A) |

**Migration:** `W005_orderline_locator_ref.sql` — adds `locator_ref TEXT` and
`is_reference_class INTEGER DEFAULT 0` to C_OrderLine.

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

**Convention extends to design workspace:** `C_OrderLine.dx/dy/dz` uses the same
LBD convention. See `G4_SRS.md` §5.4 and `BIM_Designer.md` §17.10.3.

**tack_from / tack_to (Lego principle):** The parent's `m_bom_line` defines
**tack_from** — where the child's corner goes. The child's **tack_to** is always
its own LBD = (0,0,0). The child never looks up.

```
SH_LIVING_SET  (parent AABB = 8000 × 2000 × 1200 mm)
  │
  ├─ tack_from=(0.3, 0.1, 0.0) → Piano         tack_to=(0,0,0) = piano's LBD
  ├─ tack_from=(2.5, 0.8, 0.0) → SOFA_BOM      tack_to=(0,0,0) = sofa group's LBD
  ├─ tack_from=(5.8, 0.2, 0.0) → Dining Table   tack_to=(0,0,0) = table's LBD
  └─ BUFFER fills remaining space (§4.2)
```

The child can be reused in any parent that has a slot big enough.

**Typed coordinate hierarchy:** Code enforces typed coordinates: `LocalCoord`,
`StoreyCoord`, `WorldCoord` (sealed classes). Accumulation chain:
`LocalCoord.toWorld(StoreyCoord) → WorldCoord`. `DriftGuardTest` prevents
direct `WorldCoord` construction — all world positions must flow through the chain.

### 4.1 World Coordinate Reconstruction

```
element_LBD = building_origin + tack_from[1] + tack_from[2] + ... + tack_from[N]
centroid    = element_LBD + (width/2, depth/2, height/2)
```

where each `tack_from[i]` is the full 3D position `(dx, dy, dz)` from that
level's m_bom_line. The walker accumulates all three axes through the BOM chain.

**Origin convention:** Only the **BUILDING BOM** carries a non-zero origin
(`m_bom.origin_x/y/z`). All child BOMs have `origin = (0, 0, 0)` — position is
encoded solely in the parent's tack_from. Non-zero child origins cause double-count.
Centroid is computed **only at the output stage** — never enters the BOM.

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

**Scope box origin (YAML `origin_m`)** is a **containment filter only** — not a
spatial reference for offsets. Tack_from comes from the room's measured LBD
relative to the floor's LBD.

### 4.1.1 validateBOM() — The Spatial Analogue

`BomValidator.java` (9 checks) enforces spatial BOM integrity:
child within parent AABB, no overlap, SUM(children)+BUFFER = parent, non-negative tack_from.

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
- W-AABB-QUAL-1: IMPLEMENTED — m_bom.aabb_qualifier tags INNER vs OUTER correctly.
- W-PHANTOM-1: IMPLEMENTED — PHANTOM lines fill remaining INNER volume. 66 PHANTOMs across 82 SET BOMs.
- W-CENTROID-DIFF-1: IMPLEMENTED — hosted elements use centroid distance for band classification.
- W-TACK-1: IMPLEMENTED (advisory) — child AABB fits within parent. SH: pending promotion to FAIL. TE: 28.5% overshoot (CLUSTER approximate grouping). Promotion blocked until overshoot < 5%.
- W-BUFFER-1: IMPLEMENTED — SUM(children) vs parent. SH: 2/3 balanced. TE: 12/50 balanced (CLUSTER exceeds centroid-based envelope).
- W-WALKTHRU-DIFFERS-1: RESOLVED — single compilation path.

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

Centroid offsets were replaced by LBD offsets in all four builders (S17-S18).
LBD offsets are context-free (`child_minX − parent_minX`); centroid offsets are
sibling-dependent and cannot tile. See `TACK_FIX_SPEC.md` for the fix spec.

---

## 5. The 9-Stage Pipeline

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against `{PREFIX}_BOM.db` |
| 2 | **Parse** | Reads `.bim` DSL text into records |
| 3 | **Compile** | Produces `BuildingSpec` from BOM hierarchy |
| 4 | **Template** | ST-mode: walks M_Product_Category_Line slots |
| 5 | **Write** | Emits SQLite output DB |
| 6 | **Verb** | BIM COBOL script hook → W_Verb_Node audit trail |
| 7 | **Digest** | Per-element SHA256 spatial fingerprint |
| 8 | **Geometry** | Mesh integrity validation |
| 9 | **Prove** | Mathematical placement proofs |

Single compilation path: element positions are read from m_bom_line (tack
offsets per §4) and accumulated through the BOM chain into world coordinates.
C_OrderLine → M_Product → BOM explosion (iDempiere prepareIt pattern).

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 75 verbs in 5 tiers:

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

Processing 34 real buildings creates a **data flywheel** — each onboarded IFC
enriches a pool of mined dimensional observations that validates the next one.
Unlike fixed BIM rules (building codes, clash thresholds), this system validates
against **empirical evidence from its own corpus.**

**Cycle:** New IFC → DimensionRangeValidator checks W/D/H against mined ranges →
pipeline compiles BOM → `extract_validation_rules.sh` mines new patterns →
`apply_mined_rules.sh` feeds back into ERP.db → pool grows.

**Three validation layers:**

| Layer | Question | Source | When |
|-------|----------|--------|------|
| **Dimensional** (DV010) | "Is this wall a plausible size?" | Mined from 20 buildings (415 rules) | IFC onboarding |
| **Compliance** | "Does this room meet building code?" | Researched from UBBL/IRC/NFPA (63 rules) | Design time |
| **Relational** | "Does this bathroom have the right MEP?" | Mined + researched (186 schedules, 4,801 placement rules) | MEP placement |

**Current state:** 415 rules, 25 IFC classes, 1,245 parameters mined from 20 buildings.

### 9.1 Layer 2 — Building Profile Validation

Every building has a signature — the percentage distribution of its IFC classes
(e.g., residential = ~35% IfcWall, MEP = 90%+ flow elements). `BuildingProfileValidator`
compares each new building's profile against archetype profiles mined from the
34-building corpus. Catches mis-labelled files, wrong discipline tagging, and
anomalous element compositions. Advisory only — never blocks.

**Storage:** `ad_building_profile` table in ERP.db (one row per building/ifc_class).

### 9.2 Flywheel Layers

| Layer | Status | Question | Spec |
|-------|--------|----------|------|
| 0 — Extraction | DONE | "Here are 97,000 elements" | This document §2.1 |
| 1 — Dimension Ranges | DONE (s47) | "Is this wall a plausible size?" | DimensionRangeValidator |
| 2 — Building Profiles | DONE (s47) | "Does this building's composition make sense?" | §9.1 above |
| 3 — Shape Comprehension | EYES module | "This IfcWall is shaped like a beam" | [EYES_SRS.md](EYES_SRS.md) |
| 4–6 | Future | Relational patterns, sequence patterns, archetype clusters | — |

Each layer uses the same mechanism — query the corpus, aggregate, compare.
No neural networks. No training. Just SQL aggregation on real data.

---

## 10. The Compilation End State

The compiler runs without human assistance. Given a `.bim` DSL file and two source
databases (`{PREFIX}_BOM.db` + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

## 11. Dynamic Building Registration

Phases A-D complete. Manifest-driven registration via `scripts/construction_manifest.yaml`.

**Architecture:** `BuildingRegistry.loadActive()` reads C_DocType from `{PREFIX}_BOM.db` —
zero building names in Java. Python/shell scripts read the manifest or query `{PREFIX}_BOM.db`.
Adding a new building = one YAML block + IFC extraction + IFCtoBOM pipeline. Zero code changes.

**Key invariants:**
1. Manifest is declarative — storey names, roles, paths, prefix. Never counts or dimensions.
2. Extraction is measurement — element counts, AABB envelopes, storey origins.
3. `{PREFIX}_BOM.db` is artifact — produced by IFCtoBOM, never hand-edited, regeneration is idempotent.
4. Drift resistance — extraction count changes require only re-running the pipeline, no file edits.

See [WorkOrderGuide.md](WorkOrderGuide.md) for the onboarding pipeline and drift prevention.
See [SourceCodeGuide.md](SourceCodeGuide.md) §Extension Recipe for recurring traps.

---

## Why the ERP Concept Is Most Powerful

This spec covers the core compilation model — how BOMs become buildings.
But the ERP paradigm extends far beyond compilation: exception-based ordering,
order inheritance, rule packs, C_Project site management, and the full
4D–8D dimension stack.

**Read next:** [**Project Order Blueprint**](ProjectOrderBlueprint.md) — the extended spec
that shows where the Construction Order goes when applied to real construction projects.

---

*Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Terminal ERP model: [`TerminalAnalysis.md`](TerminalAnalysis.md) §ERP Model Architecture |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
