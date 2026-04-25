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
| [§2 Compilation Model](#2-the-compilation-model) | The Application Dictionary and BOM contract |
| [§3 Compilation](#3-compilation) | BOM explosion, placement, selection cascade, ASI |
| [§4 Tack Convention](#4-tack-convention-the-spatial-handshake) | The dx/dy/dz spatial offset model (LBD) |
| [§4.3 Element Identity](#43-element-identity-contract-s147) | IFC GUID preservation contract |
| [§5 Pipeline](#5-the-11-stage-pipeline) | 11-stage compilation pipeline |
| [§6 Verbs](#6-bim-cobol-verb-driven-bom-mutation) | 77 domain verbs (TILE, ROUTE, FRAME, CLUSTER) |
| [§7 Verification](#7-verification-the-rosetta-stone-gate) | 6 mathematical gates |
| [§9 Data Flywheel](#9-the-data-flywheel-emergent-intelligence) | How the building fleet teaches the compiler |
| [§10 End State](#10-the-compilation-end-state) | What the compiled output looks like |

**Related specs:**
[DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

---

## 1. iDempiere Entity Mapping

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element or assembly. Defined in ERP.db. `getChildren()` → recurse. Empty → leaf. `getParentBOM()` → null means root |
| **M_Product_Category** | Product grouping for substitution — same Category = interchangeable. `getProductCategory()` returns the shelf |
| **M_BOM** (bill of materials) | Assembly recipe attached to a product. A product IS a BOM when it has children |
| **M_BOM_Line** (BOM child) | Recipe line: child product + qty + relative offset (dx/dy/dz). NOT a per-instance placement |
| **AD_Org** (organization) | Discipline partition (ARC, STR, FP, ACMV, ELEC, SP, CW, LPG). See [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) §10.4 |
| **C_Order** (work order) | Construction project for a specific building. ONE doc type: "Construction Order" |
| **C_OrderLine** (order line) | Instance of a product in this project. Compilation explodes parent → child lines. Exception lines name only deviations |
| **C_DocType** (document type) | "Construction Order" — one only. Classification metadata, NOT a compilation driver |
| **[AD_ChangeLog](https://wiki.idempiere.org/en/AD_ChangeLog)** (audit trail) | Full provenance — every PLACE, DELETE, MOVE, RESIZE logged with before/after state. UNDO/REDO stack |
| **[C_BPartner](PREFAB_ARCHITECTURE.md)** (business partner) | Manufacturer/supplier — WHO makes this product. IFC source accreditation. Starter kit brand: user picks BPartner → all downstream equipment follows. Three dimensions: Category (WHAT) × BPartner (WHO) × SpaceSize (HOW MUCH) |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **[C_Project](ProjectOrderBlueprint.md#2-c_project-site-as-bom)** (project) | Site development — groups C_Orders under one project (budget, schedule, milestones) |
| **[C_Location](https://wiki.idempiere.org/en/C_Location)** (address/coordinates) | Plot location — independent dimension linking site coordinates to C_Order or C_Project |
| **[M_Locator](https://wiki.idempiere.org/en/M_Locator)** (aisle/lot/bin) | Plots akin to warehousing ALB (Aisle/Lot/Bin) but in [terrain](PDF_TERRAIN.md) context — with [2D layout](2D_LAYOUT.md) capability |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

**A BOM is self-describing.** No level labels needed:

- `getParentBOM()` returns null → **root**
- `getChildren()` returns empty → **leaf**
- `getProductCategory()` → what group it belongs to (substitution shelf)

The hierarchy is whatever the products make it. A building, a bridge, a ship — same
three methods, different products. No `UNIT/FLOOR/ROOM/SET/ITEM` vocabulary.

### 1.1 Disciplines

Discipline = AD_Org. The BOM walker does not know what ARC or FP means — it
just recurses. See [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) §10.4
for the full discipline model (DocEvent per Org → ASI → AD_Val_Rule, shared recipes).

---

## 2. The Compilation Model

The BOM databases, product catalog, and validation rules are the **Application
Dictionary** — like iDempiere's AD_Table through AD_Column. They exist. This
document describes how the compiler **uses** them, not how they were created.
For BOM creation, see the Rosetta Stone pipeline and building analysis docs.

**Four databases, three concerns** (WHAT / HOW / WHERE — see [DATA_MODEL.md §6.3](DATA_MODEL.md)):

| Database | Concern | iDempiere parallel |
|----------|---------|-------------------|
| **ERP.db** | WHAT — Product master (M_Product, M_Product_Category, attributes) | Product catalog |
| **component_library.db** | WHAT — Leaf geometry (LOD meshes, orientation metadata) | M_Product_Image |
| ***_BOM.db** | HOW — Assembly recipes (M_BOM, M_BOM_Line with qty + dx/dy/dz) | Bill of Materials |
| **output.db** | WHERE — Placed elements at world coordinates (c_orderline, elements_meta) | Transaction |

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

**No Parametric Mesh in Pipeline.** All geometry comes from `component_library.db`
(LOD_ hash prefix). ASI controls per-instance sizing; the compiler scales library LODs,
never creates geometry. `G5-PROVENANCE` Check 6 enforces zero GEO_ hashes.

### 2.1 Recipe vs Placement — The BOM Contract

**The BOM is a recipe.** Each m_bom_line is a **type line** — one row per
unique product within its parent BOM, with a qty and verb formula reference.
Qty is expressed in the product's **cost_uom**: EA for discrete items (doors,
fittings), M for linear (pipe segments, beams), M2 for area (walls, slabs).
The compiler expands type lines into placement instances at compile time.

**output.db holds placements.** Each row in `c_orderline` / `elements_meta` is one
physical element at its world-space coordinate. The compiler produces these by
expanding BOM recipes through verb formulas (TILE grid, ROUTE path, FRAME bay, etc.)
or flat placement (qty=1 for irregular elements).

```
BOM.db (RECIPE — factored)                  output.db (PLACEMENT — expanded)
┌─────────────────────────────────┐         ┌──────────────────────────────────┐
│ m_bom_line                      │         │ elements_meta / c_orderline      │
│  product_id=PLATE_500x150       │  ──→    │  PLATE_500x150 @ (92.5, -42, 19)│
│  qty=4410                       │ expand  │  PLATE_500x150 @ (93.0, -42, 19)│
│  verb_ref=TILE(15×294, 495mm)   │         │  PLATE_500x150 @ (93.5, -42, 19)│
│                                 │         │  ... (4,410 rows)               │
└─────────────────────────────────┘         └──────────────────────────────────┘
```

**Invariant:** `SUM(m_bom_line.qty)` across all **non-PHANTOM** leaf lines in
the BOM equals the element count in output.db. PHANTOM lines represent spatial
availability — they carry qty but do not expand to output elements.
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
| (UOM=MM/M) | — | — | — | Variable-length: InterimWorkshop recomputes primitive mesh (§6.12.2) |
| flat | 770 | 770 | exact | Irregular placements |

Step-uniformity guard (R8): each ROUTE leg's consecutive gaps must be within
±20% of the average step. Non-uniform groups fall through to CLUSTER or flat writes.
See [`BIM_COBOL.md`](BIM_COBOL.md) §19 for verb taxonomy,
data flow, and fidelity details.

**Pipeline phases (self-contained):**
1. **BOM pipeline** (`IFCtoBOMPipeline.run()`): reads IFC extraction + YAML Order input → populates catalog (idempotent) → writes `{PREFIX}_BOM.db`
2. **Compile** (`DAGCompiler`): reads `{PREFIX}_BOM.db` + `component_library.db` → produces output.db
   *(Authoritative output DDL: Java `BuildingWriter.initSchema()`, not Python `output_schema.sql`.)*

### 2.1.8 IFCtoBOM — BOM Recipe Builder

IFCtoBOM produces the **BOM recipe** (`{PREFIX}_BOM.db`): `m_bom` + `m_bom_line` tables
only. No C_Order, no C_OrderLine, no callouts. Orders are created downstream during
compilation (`processIt()`) — the ERP transaction. See [`DocAction_SRS.md`](DocAction_SRS.md) §1.

**Two inputs:**

1. **IFC extraction DB** (`*_extracted.db`) — elements, IFC family types (`IfcWall`,
   `IfcFurniture`, `IfcSpace`), spatial containment (`IfcRelContainedInSpatialStructure`),
   host relationships (`IfcRelFillsElement`). The IFC file is the authoritative source of
   WHAT exists and WHERE it belongs. Classification is IFC-driven — no YAML involvement.

2. **Classification YAML** (`classify_*.yaml`) — the **Order input**: a human-readable
   stand-in for C_Order + C_OrderLine. Defines building identity, storey mapping, BOM
   template references, discipline routing, and static children. YAML tells the pipeline
   HOW to organise the extracted elements into a BOM tree, not what elements exist.

**Separation of concerns:**

| Concern | Source | NOT from |
|---------|--------|----------|
| What elements exist | IFC extraction DB | YAML |
| Which room an element belongs to | IFC `rel_contained_in_space` | YAML scope boxes |
| Element IFC class/family type | IFC extraction DB | YAML |
| BOM tree shape (BUILDING → FLOOR → ROOM → SET) | YAML Order config | IFC |
| Discipline routing (ARC/STR/FP/ELEC...) | YAML `disciplines:` | IFC |
| Static children (slabs, roof) | YAML `static_children:` | IFC |
| Space-to-template mapping | YAML `ifc_space:` → `template_bom:` | IFC |

See [`DISC_VALIDATION_DB_SRS.md §6.13`](DISC_VALIDATION_DB_SRS.md#613-ifc-driven-extraction).

**Per-order values flow through C_Order, not ad_sysconfig.** YAML fields that
affect compilation (e.g. `mep_order_qty`) must land on C_Order via BomDropper
and be read from the compile DB at Stage 2. `ad_sysconfig` is iDempiere
system-wide configuration — using it to pass per-building YAML values to
compilation bypasses the C_Order convention. See
[DISC_VALIDATION_DB_SRS.md §6.12.4](DISC_VALIDATION_DB_SRS.md) for the
generative MEP schedule architecture (LOD bridge, shim placement, W-LOD-BRIDGE test).

**IFCtoBOM does NOT:**

- Create C_Order or C_OrderLine (that is `processIt()` during compilation)
- Fire callouts (OrderLineProductCallout fires during compilation, not BOM building)
- Invent elements (every leaf traces to `I_Element_Extraction`)
- Use YAML scope boxes for room assignment (IFC spatial containment; scope boxes are Order processing)
- Validate against regulations (see [`DocValidate.md`](DocValidate.md))
- Fill missing pipes or correct gaps (WYSIWYG)

### 2.1.9 BOM Write Path

**Problem:** 18 INSERT statements (7 `m_bom`, 11 `m_bom_line`) spread across 6 builder files, each with a different column subset. DDL changes require N-file edits — P92 (AD_Org_ID) proved the drift.

**Solution:** `BomWriter` — a static utility (same pattern as `ProductRegistrar`) with two core methods: `insertBom(conn, BomRow)` and `insertBomLine(conn, BomLineRow)`. Builder-pattern row objects (`BomRow`, `BomLineRow`) carry all columns with defaults so callers only set the fields they need.

**Invariant:** Every `m_bom` and `m_bom_line` write in IFCtoBOM goes through `BomWriter`. No builder owns raw SQL for these tables.

**Column truth:** `BomRow` mirrors the `m_bom` DDL in `IFCtoBOMPipeline.java`. `BomLineRow` mirrors the `m_bom_line` DDL. Adding a column = add to DDL + add to row class + add to `BomWriter` bind list. One place, not six.

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

*Exception — validation verbs:* `CHECK BOM` may inspect `component_type` as an iDempiere
well-formedness signal (valid values: BUY / MAKE / PHANTOM). The placement walker still
never branches on it. Any value outside {BUY, MAKE, PHANTOM} is a BOM authoring error,
not a compilation input.

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

**Generative MEP LOD bridge:** Products created by the generative MEP schedule
(TOILET, LIGHT, SPRINKLER, etc.) are `extracted_from='SHARED_RECIPE'` with
`source_element_ref=NULL` at creation. ProductRegistrar must reverse-lookup
`ad_element_mep_alias` to resolve the IFC family name, closing the bridge:
`M_Product.source_element_ref → I_Geometry_Map.element_ref → geometry_hash → mesh`.
Without this bridge, BuildingWriter throws MetadataMissingException at compile time.
See [DISC_VALIDATION_DB_SRS.md §6.12.4 subsection 11](DISC_VALIDATION_DB_SRS.md)
for the full LOD bridge spec and W-LOD-BRIDGE test.

---

## 3. Compilation

### 3.1 Terms

| Term | iDempiere parallel | Definition |
|------|-------------------|------------|
| **Compilation** | PP_Product_BOM explosion | The compiler explodes the BOM tree from root to leaves, accumulating tack offsets into world coordinates |
| **PHANTOM** | Phantom BOM line | Spatial availability marker — represents remaining capacity in a parent. Walker skips it (no output element). Enables "where can I place this?" queries |
| **Tack** | Origin datum | Left-Back-Down (LBD) corner = (minX, minY, minZ) = (0,0,0) in own frame. All offsets are parent-LBD to child-LBD |
| **BOM** | Bill of Materials | A product with children (`getChildren()` non-empty). The walker recurses into it |
| **Leaf** | Purchased item | A product with no children (`getChildren()` empty). The compiler emits the element here. Geometry from `component_library.db` |

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

### 3.3 The Order Is Minimal

One `C_OrderLine` referencing a root product compiles an entire building.
The compiler explodes the full BOM tree — accumulating tack offsets, resolving
leaves to geometry. SH, DX, TE all compile
from a single root line.

**Exception lines name only deviations.** The order says "build me this, but
change that child." Product_ID traces directly to the BOM family — no ambiguity.
See [ProjectOrderBlueprint.md](ProjectOrderBlueprint.md) §1 for the exception algebra
(Replace, Remove, Compress, Add).

**Interactive modification** follows the iDempiere BOMDrop Configurator pattern:
expand BOM one level, **Swap** (same Category), **Add** (new line), or **Remove**
(deactivate). The BOM tree stays navigable at any scale — TE has only 58 BOM
nodes and 1,572 lines for 48,428 elements.

### 3.5 Selection Cascade

The BOM chooser (used during modification to find replacement products) filters
by **M_Product_Category** — same Category means interchangeable. Swap a roof →
see only products in the same roof Category. This is the same browse/filter
pattern as iDempiere's product lookup: filter by Category, pick from the shelf.

> **Beyond compilation:** Exception-based ordering, order inheritance, reference
> class compression, and the full Order model are specified in
> [ProjectOrderBlueprint.md](ProjectOrderBlueprint.md).

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

*ASI matrix: BIM_Designer.md §8. Schema: output.db. Generative: [GENERATIVE_HOUSE_SRS.md](GENERATIVE_HOUSE_SRS.md) §6.*

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

### 3.6 Parasitic Discipline Compilation

ARC and STR are **spatial disciplines** — they build from the ground up. Every
child has a dx/dy/dz tack offset: wall sits on slab, column at grid intersection.
The BOM walk produces fully-placed elements.

MEP disciplines (FP, ACMV, ELEC, CW, SP, LPG) are **parasitic** — they
attach to rooms and zones that ARC already placed. Their compilation model
is fundamentally different.

#### 3.6.1 Eight OrderLines, One Building

Each discipline is a separate C_OrderLine. No merging, no shared lines.

```
C_Order: TE (Terminal)
├── C_OrderLine seq=10: ARC_SYSTEM   (AD_Org_ID=1)  → shell: rooms, walls, finishes
├── C_OrderLine seq=20: STR_SYSTEM   (AD_Org_ID=2)  → structure: columns, beams, slabs
├── C_OrderLine seq=30: FP_SYSTEM    (AD_Org_ID=3)  → fire protection
├── C_OrderLine seq=40: ACMV_SYSTEM  (AD_Org_ID=4)  → air conditioning / ventilation
├── C_OrderLine seq=50: ELEC_SYSTEM  (AD_Org_ID=5)  → electrical
├── C_OrderLine seq=60: CW_SYSTEM    (AD_Org_ID=6)  → cold water
├── C_OrderLine seq=70: SP_SYSTEM    (AD_Org_ID=7)  → sanitary plumbing
└── C_OrderLine seq=80: LPG_SYSTEM   (AD_Org_ID=8)  → gas
```

Sequence controls explosion order. ARC explodes first (produces rooms with
positions). STR explodes second (DocEvent can verify columns align to beams).
MEP disciplines explode after — they reference ARC rooms that already exist
in the compiled output.

#### 3.6.2 Service Point Discovery via M_Product_Category

Each ARC room is a product with a category. Some rooms serve a specific
discipline: pump room = category FP, AHU room = category ACMV, TNB room =
category ELEC.

Each discipline root BOM has the **same category** as its service rooms.
FP_SYSTEM has `m_product_category = FP`. To find where FP tacks to, the
compiler matches:

```sql
SELECT dx, dy, dz FROM c_orderline
WHERE m_product_category_id = (SELECT M_Product_Category_ID
                               FROM M_Product_Category WHERE Value = 'FP')
  AND Discipline = 'ARC'
```

No new columns. No cross-references. The existing product taxonomy IS the
wiring between ARC shell and MEP disciplines.

**Root BOM tack origins** — where each discipline's root product attaches
(TE reference data from Terminal extraction, world coordinates):

| OrderLine | Root BOM | Tacks to (ARC room) | M_Product_Category | Origin (world m) | Walk direction |
|-----------|----------|--------------------|--------------------|-----------------|----------------|
| seq=10 ARC | ARC_SYSTEM | Building LBD (world anchor) | ARC | (84.6, -51.2, -30.7) | Ground up ↑ |
| seq=20 STR | STR_SYSTEM | Foundation level | STR | (84.6, -51.2, -30.7) | Ground up ↑ |
| seq=30 FP | FP_SYSTEM | **Pump room** (GF) | FP | (~108, -17, 2.9) | Vertical riser ↑ then horizontal per floor → |
| seq=40 ACMV | ACMV_SYSTEM | **AHU / plant room** (L1+) | ACMV | (~126, -12, 6.1) | Horizontal per floor from AHU → |
| seq=50 ELEC | ELEC_SYSTEM | **DB room** (per floor) | ELEC | (~127, -19, 7.0) | Vertical riser ↑ then radial per floor → |
| seq=60 CW | CW_SYSTEM | **Water tank / meter room** (below GF) | CW | (~103, -29, -1.2) | Ground up ↑ then horizontal to wet rooms → |
| seq=70 SP | SP_SYSTEM | **Manhole / building drain** (below GF) | SP | (~115, -10, -1.1) | Top down ↓ (gravity drainage) |
| seq=80 LPG | LPG_SYSTEM | **Gas meter room** (GF external) | LPG | (~92, -30, 1.8) | Horizontal from meter to kitchens → |

**How it works:**

1. ARC explodes → produces rooms. Some rooms carry discipline-specific categories
   (pump room = FP, AHU room = ACMV, DB room = ELEC, etc.)
2. When FP_SYSTEM explodes, the compiler queries ARC output for rooms with
   `M_Product_Category = FP`. Those room positions become FP_SYSTEM's tack anchors.
3. FP's children (riser, branch pipes, sprinkler heads) distribute from those
   anchors using qty + DocEvent rules. No dx/dy/dz on the BOM line.

**ARC rooms as discipline anchors:** The ARC shell must produce room products
with categories matching each MEP discipline present in the building. If the
Order config has no pump room, FP_SYSTEM has nowhere to tack.
The Order author (YAML or Designer user) ensures service rooms exist for each
discipline OrderLine they add.

**Compulsory service rooms in CO BOM:** For a CO (Commercial) building, the
ARC root BOM includes service rooms as **compulsory children** — pump room
(category FP), AHU room (category ACMV), DB room (category ELEC), water
tank room (category CW), wet core (category SP), gas meter room (category LPG).
These are the frame mounting points. Like a toy car chassis: certain
attachment points are pre-moulded into the frame; the discipline modules
snap onto them.

```
BUILDING_TE_STD (CO)
└── ARC children (compulsory frame):
    ├── ROOM_PUMP         (M_Product_Category = FP)     ← FP tacks here
    ├── ROOM_AHU          (M_Product_Category = ACMV)   ← ACMV tacks here
    ├── ROOM_DB           (M_Product_Category = ELEC)   ← ELEC tacks here
    ├── ROOM_WATER_TANK   (M_Product_Category = CW)     ← CW tacks here
    ├── ROOM_WET_CORE     (M_Product_Category = SP)     ← SP tacks here
    ├── ROOM_GAS_METER    (M_Product_Category = LPG)    ← LPG tacks here
    └── ... (other rooms: lobby, retail, office — no discipline anchor)
```

#### 3.6.2a OrderLine.Product Callout — Auto-Insert Discipline Lines

When the user selects a CO product on the first C_OrderLine, a **Callout**
automatically creates the sibling discipline OrderLines. This is the standard
iDempiere `C_OrderLine.M_Product_ID` callout pattern — when product changes,
callout fires.

```
User action:  C_OrderLine.M_Product_ID = BUILDING_TE_STD
                ↓
Callout:      OrderLine.Product.onProductChanged()
                ↓
Logic:        1. Read M_Product_Category → CO
              2. Read CO BOM children → find discipline root products
                 (each child with AD_Org_ID > 0 is a discipline)
              3. For each discipline root: INSERT C_OrderLine
                 - Product_ID = discipline root BOM (FP_SYSTEM, ACMV_SYSTEM, ...)
                 - AD_Org_ID  = discipline org (3=FP, 4=ACMV, ...)
                 - Sequence   = 30, 40, 50, 60, 70, 80
                 - Qty        = from extraction peek, or 0 (fill-all)
                ↓
Result:       8 OrderLines created (ARC=10, STR=20, FP=30, ... LPG=80)
```

**No invention.** This is the same mechanism as iDempiere's Sales Order
callout: select a product kit → callout auto-inserts component lines.
The callout reads the BOM to discover what lines to create. The user can
then edit quantities, remove disciplines they don't need, or add custom
lines.

**YAML as Order input:** For Rosetta Stone testing, the YAML `classify_te.yaml`
lists the 8 discipline sections explicitly — this is Order data, not extraction
logic. The callout is the interactive (Designer GUI) equivalent of what YAML
declares as C_OrderLine configuration.

**Generalisation across building categories:** The callout is not CO-specific.
It reads the BOM to discover discipline children. Different building categories
have different discipline sets — the callout discovers them, not hardcodes them.

| Category | Typical disciplines | Service anchor concept |
|----------|--------------------|-----------------------|
| **RE** (Residential) | ARC, STR, ELEC, CW+SP (maybe LPG) | Rooms — kitchen, bathroom, DB cupboard |
| **CO** (Commercial) | All 8 — ARC, STR, FP, ACMV, ELEC, CW, SP, LPG | Rooms — pump room, AHU room, DB room, etc. |
| **IN** (Infrastructure) | Varies by sub-type (see below) | **Zones** — spans, chainage segments, stations |

**Infrastructure (IN) discipline mapping:**

| Infra sub-type | Disciplines | Service zones (≈ rooms) |
|----------------|-------------|------------------------|
| **Bridge** (BR) | STR (piers, deck, girders), GEO (foundations), ROAD (approach), DRAIN, ELEC (lighting) | Span, pier, abutment — each a zone product |
| **Road** (RD) | ROAD (pavement, markings), DRAIN (storm drains, culverts), ELEC (street lighting), TRAFFIC (signals) | Chainage segments (100m sections) |
| **Rail** (RL) | RAIL (track, sleepers), STR (stations), ELEC (OHE, signalling), DRAIN | Track sections, station zones, signal locations |
| **Industrial** (IP) | PROCESS (pipes, vessels), STR (steel frame), ELEC, INST (instrumentation) | Process units, pipe racks, control rooms |

The mechanism is identical: the parent product (bridge, road, rail) has
compulsory children (zones) with discipline-specific M_Product_Category.
The callout reads them and creates OrderLines. A bridge pier has
`M_Product_Category = STR`; the STR OrderLine tacks there — same pattern
as a pump room having `M_Product_Category = FP`.

**Room vs Zone:** For buildings (RE, CO), the service anchor is a **room** —
an IfcSpace with walls, floor, ceiling, and a typed category. For infrastructure
(IN), the service anchor is a **zone** — a spatial segment without enclosure.
Both are M_Product entries with categories. The BOM walker treats them
identically: a product with children that the walker recurses into.

**RE is a subset, not an exception.** A house with only ARC + ELEC + SP
simply has a BOM with 3 discipline children. The callout creates 3 OrderLines.
No code change — the BOM is the configuration.

*Callout architecture: [DocValidate.md](DocValidate.md) §1.5 (Column.Callout spec).
Product → verb routing via ASI: BBC.md §3.5.2.
Infrastructure buildings: BR 7/7, IP 7/7 PASS (standard elements).
RD/RL: 0 elements (infra walker gap — non-standard IFC hierarchy).*

#### 3.6.3 Per-Discipline Trace — Start, Walk, End

Each discipline has a **service origin** (where it starts), a **walk pattern**
(how it distributes), and **placement rules** (standards that govern spacing
and coverage). The walker uses qty from the BOM line as its stop condition.

##### ARC — Architecture (AD_Org_ID=1, seq=10)

| Aspect | Detail |
|--------|--------|
| **Start** | Building origin (0,0,0). Builds the spatial shell from ground up. |
| **Walk** | Spatial tack. Every child has dx/dy/dz from extraction. Walls, windows, doors, finishes, furniture — each placed at its extracted world position relative to floor LBD. |
| **End** | All rooms defined. Room containers (FLOOR, ROOM) have world positions in c_orderline. These become tack anchors for all parasitic disciplines. |
| **Elements** | TE: 34,724 (72%). IfcPlate(33K), Wall(330), Window(236), Furniture(176). |
| **Rules** | UBBL Part III (building dimensions). Room area/width/height per occupancy (AD_DocEvent_Rule, `check_method=MIN_DIMENSION`). |
| **Key output** | Typed rooms with M_Product_Category: pump rooms (FP), AHU rooms (ACMV), DB rooms (ELEC), etc. These categories wire parasitic disciplines to their service points. |

##### STR — Structure (AD_Org_ID=2, seq=20)

| Aspect | Detail |
|--------|--------|
| **Start** | Foundation level (FN). Columns, footings at grid intersections. |
| **Walk** | Spatial tack. Columns at grid nodes, beams spanning between columns, slabs covering bays. Each has dx/dy/dz from extraction. |
| **End** | Structural frame complete. Column grid defines the structural bays that ARC rooms sit within. |
| **Elements** | TE: 1,429. Slab(614), Beam(432), Member(312), Column(68). |
| **Rules** | MS 1195 / EC2 (concrete). Column vertical continuity: max XY drift ≤ 25mm across storeys (`STR_COLUMN_CONTINUITY`). Beam-column connectivity (DocEvent: REQUIRED_HOST). |
| **DocEvent** | Verify columns align to beams. STR elements must sit within ARC structural grid — DocEvent checks that STR tacks fall on ARC grid nodes. |

##### FP — Fire Protection (AD_Org_ID=3, seq=30)

| Aspect | Detail |
|--------|--------|
| **Start** | Pump room (M_Product_Category=FP in ARC output). FP_SYSTEM origin = pump room c_orderline position. |
| **Walk** | **Parasitic.** BOM gives qty per floor, no dx/dy/dz. DocEvent distributes: risers through riser shafts (vertical, one per shaft), then branch pipes per floor, then sprinkler heads per room. |
| **End** | Every room with occupancy requiring sprinklers has coverage. Walker stops at qty if set, or covers all eligible rooms if qty=0. |
| **Elements** | TE: 6,863. PipeFitting(3,146), PipeSegment(2,672), FireSuppression(909), Alarm(80). |
| **Rules** | **NFPA 13** (sprinkler spacing): min 3000mm, max 4600mm per Light Hazard §8.6.2.2.1. Coverage area per head: 9.3m² LH, 12.1m² OH1. **UBBL** Part VII (fire requirements by occupancy). `ad_fp_coverage` table (4 hazard classes). |
| **Placement** | Grid distribution within each room. Spacing = `room_area / coverage_per_head`. TE mining confirms bimodal: 3000-4000mm (standard) and 4500mm (dominant grid). |

##### ACMV — Air Conditioning & Mechanical Ventilation (AD_Org_ID=4, seq=40)

| Aspect | Detail |
|--------|--------|
| **Start** | AHU/plant room (M_Product_Category=ACMV in ARC output). ACMV_SYSTEM origin = AHU room position. |
| **Walk** | **Parasitic.** Main ducts from AHU through ceiling void (horizontal per floor). Branch ducts to each room. Air terminals (diffusers/grilles) distributed per room by air change requirement. |
| **End** | Every habitable room has supply + return air terminals. |
| **Elements** | TE: 1,621. DuctFitting(713), DuctSegment(568), AirTerminal(289), Proxy(51). |
| **Rules** | **MS 1525** (energy efficiency, air-conditioned buildings). **ASHRAE 62.1** (ventilation rates). Air changes per hour by occupancy: office 6-8 ACH, retail 8-12 ACH, toilet 15 ACH. Duct sizing by airflow (velocity method). |
| **Placement** | Air terminals: grid within room, spacing derived from throw distance. Ducts: ROUTE verb along ceiling void, branch at room entry points. |

##### ELEC — Electrical (AD_Org_ID=5, seq=50)

| Aspect | Detail |
|--------|--------|
| **Start** | TNB/main switchboard room (M_Product_Category=ELEC in ARC output). ELEC_SYSTEM origin = DB room position. |
| **Walk** | **Parasitic.** Main distribution board → sub-DB per floor (vertical riser) → cable trays per floor (horizontal) → light fixtures + power outlets per room. |
| **End** | Every room has lighting to required lux level + power outlets per occupancy. |
| **Elements** | TE: 1,172. LightFixture(814), Proxy(339), Appliance(19). |
| **Rules** | **MS 1525** (lighting power density). **IES** lighting levels: office 300-500 lux, corridor 100 lux, toilet 150 lux. Light fixture grid: max spacing ~5000mm (`IES_LIGHT_SPACING`, advisory). TE mining: avg grid pitch 4000mm, tight clustering per storey. |
| **Placement** | Light fixtures: ceiling grid aligned (600mm module). Spacing = `room_area / (lux_target / lux_per_fixture)`. TE mining confirms per-storey consistency (Level 1-3: ±2mm Z variance). |
| **Cross-discipline** | NEC 300.4: min 150mm clearance from SP pipes. TE mining: 11 true overlaps, 35 under 150mm on lower floors. `NEC_ELEC_SP_CLEARANCE` rule. |

##### CW — Cold Water (AD_Org_ID=6, seq=60)

| Aspect | Detail |
|--------|--------|
| **Start** | Water tank room / meter room (M_Product_Category=CW in ARC output). CW_SYSTEM origin = tank room position. |
| **Walk** | **Parasitic.** Rising main (vertical riser) → floor distribution (horizontal header) → branch pipes to each wet room → fixtures (taps, valves, flow terminals). |
| **End** | Every wet room (kitchen, bathroom, toilet) has water supply points. |
| **Elements** | TE: 1,431. PipeFitting(638), PipeSegment(619), FlowTerminal(106), Valve(57). |
| **Rules** | **MS 1228** (water supply). Pipe sizing by fixture unit method. Min pressure at highest fixture. Riser: one per core/shaft. Branch: ROUTE verb following wall/ceiling. |
| **Placement** | Fixtures at predefined wall positions (basin, sink, WC). Pipes ROUTE from riser to each fixture. Gravity-fed upper floors, pump-fed if pressure insufficient. |

##### SP — Sanitary Plumbing (AD_Org_ID=7, seq=70)

| Aspect | Detail |
|--------|--------|
| **Start** | Each bathroom/toilet on each floor (M_Product_Category=SP in ARC output, or rooms with sanitary fixtures). SP works **top-down** — waste flows by gravity. |
| **Walk** | **Parasitic, reversed.** Fixtures (WC, basin, floor trap) per wet room → waste pipes collecting per floor → soil stack (vertical, one per wet core) → ground floor manhole → building drain. |
| **End** | Every sanitary fixture connected to drainage. Soil stacks extend to roof (vent). |
| **Elements** | TE: 979. PipeSegment(455), PipeFitting(372), FlowTerminal(150), Valve(2). |
| **Rules** | **MS 1228** (sanitary plumbing). **Uniform Plumbing Code (UPC)**. Min pipe gradient: 1:40 (100mm pipe), 1:60 (150mm pipe). Trap seal: min 50mm water depth. Vent pipe sizing by drainage fixture units. |
| **Placement** | Fixtures at wet room positions. Waste pipes ROUTE with minimum gradient from fixture to stack. Soil stack at wet core position (vertical, full building height + vent above roof). |

##### LPG — Liquefied Petroleum Gas (AD_Org_ID=8, seq=80)

| Aspect | Detail |
|--------|--------|
| **Start** | Gas meter room / LPG storage (M_Product_Category=LPG in ARC output). Must be external or ventilated per DOSH regulations. LPG_SYSTEM origin = meter room position. |
| **Walk** | **Parasitic.** Gas riser (if multi-storey) → floor header → branch to each kitchen/cooking area → gas points (hob connections, valves). |
| **End** | Every kitchen/cooking area has gas supply with isolation valve. |
| **Elements** | TE: 209. PipeFitting(87), PipeSegment(75), Valve(47). |
| **Rules** | **MS 830** (gas installation). **DOSH** (Dept of Safety & Health) regulations. Emergency shut-off valve at entry to each floor. Isolation valve at each appliance. Min clearance from ignition sources. Gas detector in enclosed spaces. Pipe sizing by gas demand (MJ/h). |
| **Placement** | Gas points at kitchen positions. Riser in dedicated shaft (never in electrical riser). Horizontal runs along external walls or ventilated ceiling voids. |

#### 3.6.4 Two Walk Modes

| | ARC / STR (spatial) | MEP (parasitic) |
|---|---------------------|-----------------|
| **BOM walk gives** | Positions (dx/dy/dz per child) | Quantities (qty per room type) |
| **Placement by** | Compiler (deterministic tack accumulation) | DocEvent rules (distribute through ARC rooms) |
| **User control** | Adjust tack offsets in Designer | Adjust qty or drag elements in Designer |

**Spatial walk (ARC/STR):** Parent owns the attachment point (§3.2). The child's
dx/dy/dz is a fixed offset. The walker accumulates: `world_pos = parent_LBD + offset`.
Every element has a deterministic position.

**Parasitic walk (MEP):** The child BOM line has **qty but no meaningful dx/dy/dz**.
The walker produces: "this floor needs 47 sprinklers." DocEvent validation then
distributes them across eligible ARC rooms by rule. The user can adjust in
Designer after.

**Note on SP:** Sanitary plumbing is the only discipline that walks **top-down**
(waste flows by gravity from upper floors to ground). The BOM still lists
fixtures per floor — but the DocEvent must connect them downward to soil stacks,
verifying minimum pipe gradient at each segment.

#### 3.6.5 Qty as Control Knob

The M_BOM_Line qty on a parasitic child is the **user's intent**.
Qty is always in the product's **cost_uom** (from M_Product):

| Product Type | cost_uom | Qty Meaning |
|-------------|----------|-------------|
| Sprinkler head, fitting, valve | EA | `qty = 47` → distribute exactly 47 items |
| Pipe segment, duct segment | M | `qty = 18` → 18 linear meters of pipe |
| Wall panel, slab | M2 | `qty = 24` → 24 square meters |

| Qty | Meaning |
|-----|---------|
| `qty = 47` | Distribute exactly 47 items (EA) or 47 units of measure. Walker stops when count reached. |
| `qty = 0` (or blank) | No limit — distribute until all eligible rooms are covered. |

During Order setup (YAML authoring), the author peeks at the extracted DB to see
what exists: `SELECT count(*) FROM i_element_extraction WHERE discipline = 'FP'` → 47.
This becomes the qty on the FP OrderLine.

In the Designer GUI, the user edits qty directly on the C_OrderLine.
Recompile → different density. Same BOM recipe, different quantity.

#### 3.6.6 The Pipeline for Parasitic Disciplines

```
1. BOM walk      → "Floor GF needs 12 sprinklers (EA), 3 risers (EA)"  (WHAT + HOW MANY)
2. DocEvent      → distribute into ARC rooms by jurisdiction     (WHERE — rule-based)
                   rules: NFPA 13, UBBL, MS 1525, MS 1228, MS 830
3. Designer GUI  → user drags/adjusts positions                  (WHERE — user override)
4. Recompile     → final placed elements in output.db
```

Steps 1–2 are automatic. Step 3 is optional (user intervention). Step 4 is
deterministic — same order produces same output.

**iDempiere parallel:** BOM = bill of materials (recipe). DocEvent = business
rules (routing/distribution). C_OrderLine = user execution (configure-to-order).
The BOM never changes — only the order and the rules change.

#### 3.6.7 Cross-Discipline Clearance

After all 8 OrderLines explode, cross-discipline clash detection fires
(DISC_VALIDATION_DB_SRS §10.4.3 stage 4). Uses ERP-maths centreline clearance
(not mesh intersection):

```
clearance = centreline_2D_distance - radius_a - radius_b
where radius = MIN(width, depth) / 2    ← pipe cross-section
```

TE mining (M12): 11 true overlaps ELEC×SP, 35 pairs under 150mm on lower floors.
Rule: `NEC_ELEC_SP_CLEARANCE`, min 150mm, WARN severity.

*Standards by jurisdiction and discipline: [DISC_VALIDATION_DB_SRS](DISC_VALIDATION_DB_SRS.md) §10.4.3.
TE mining data: [TE_MINING_RESULTS](https://github.com/red1oon/BIMCompiler/blob/master/internal/TE_MINING_RESULTS.md).
Shared discipline recipes: DV025 migration in ERP.db (FP_SYSTEM seed, S100-p73).
DocEvent rule schema: AD_DocEvent_Rule in ERP.db ([DISC_VALIDATION_DB_SRS](DISC_VALIDATION_DB_SRS.md) §10.4.3).*

### 3.7 Rosetta Stone

The Rosetta Stone exercise (SH/DX/TE) proves the pipeline is **lossless**:
the Application Dictionary (BOMs + products + rules) compiles back to the
original building with G1-G6 gates GREEN. See [TestArchitecture.md](TestArchitecture.md)
for gate specification and coverage.

### 3.8 locator_ref — Exception-Order Addressing (Session D)

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

**Scope box origin (`origin_m`)** was historically a YAML-authored containment
filter for extraction. Since S100-p125, extraction uses IFC spatial containment
(`rel_contained_in_space`) instead. Scope boxes now live in **Order processing**
only — the BIM Designer GUI uses them for sub-room zone splits at order time.
Tack_from comes from the room's measured LBD relative to the floor's LBD.

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
Empty SET BOMs (no assigned elements) tagged `INNER` (Order-defined room dims = available space).
`FloorRoomBomBuilder`: FLOOR ROOM BOMs tagged `INNER` (architect intent from IFC or Order config).
`StructuralBomBuilder`/`DisciplineBomBuilder`: default `OUTER` (computed from elements).

#### 4.2.2 PHANTOM — Spatial Availability Index (session 43)

PHANTOMs are `component_type='PHANTOM'` lines in `m_bom_line`. SAP empty
storage bin principle: the bin has a capacity (INNER dims from Order config), the
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

*Product classification (shape archetype, scale band, semantic category) is a
product catalog concern — see `DATA_MODEL.md` and `component_library.db` schema.*

### 4.3 Element Identity Contract (S147)

Every compiled element must carry a traceable identity from IFC source to
output DB. Without this, black box verification (SpatialDiff) cannot pair
output elements to their IFC reference, and white box logging (TACK LEAF)
cannot be correlated with post-hoc analysis.

**Problem found:** S147 discovered that `SpatialDiff` fell back to
position-based pairing for DX furniture (46/55 outliers were measurement
artifacts from mis-pairing, not real errors). Root cause: the output DB
`element_ref` carried `A_`-prefixed IFC GUIDs while the reference DB `guid`
had no prefix → zero identity overlap → fallback to unreliable position sort.

**Contract:** `ElementIdentity` value object, enforced at construction:

| Field | Type | Source | Rule |
|-------|------|--------|------|
| `baseGuid` | String(22) | MA table (per-instance IFC GUID) | Must match `^[0-9A-Za-z_$]{22}$`. Never null. |
| `unitPrefix` | String | Walker scope (`A_`, `B_`, or `""`) | Set by half-unit mirror scope, not by element. |
| `guidSource` | enum | Resolution path | `MA` (Material Allocation), `LINE_REF`, `GENERATED`. Logged for traceability. |

**Derived fields (computed, not stored):**

| Method | Returns | Written to |
|--------|---------|------------|
| `prefixedRef()` | `unitPrefix + baseGuid` | `elements_meta.guid` (unique per unit) |
| `baseGuid()` | Raw IFC GUID | `elements_meta.element_ref` (matches reference DB) |

**Enforcement points:**

1. **`PlacementCollectorVisitor.onLeaf()`** — constructs `ElementIdentity`
   from MA/LINE_REF/GENERATED cascade. Replaces raw `String elementRef`.
2. **`Placement` record** — carries `ElementIdentity` instead of
   `String elementRef`. Compile error if any path skips it.
3. **`BuildingWriter`** — reads `identity.prefixedRef()` for output guid,
   `identity.baseGuid()` for element_ref. Cannot accidentally lose the
   base GUID or conflate it with the prefixed form.
4. **`ElementPersistence.writeElementMeta()`** — accepts `ElementIdentity`.
   The synthetic GUID generation (`MD_LEVEL_1_146`) is eliminated; the
   IFC GUID becomes the output GUID.

**Invariants (testable):**

- `I-GUID-1`: Every element in `elements_meta` has a non-empty `element_ref`
  matching the 22-char IFC pattern.
- `I-GUID-2`: For every `element_ref` in the output DB, there exists a `guid`
  in the reference DB with the same value (identity overlap ≥ 95%).
- `I-GUID-3`: `SpatialDiff.diff()` uses identity-based matching (not
  position-based fallback) when reference DB is available.
- `I-GUID-4`: `guidSource` is never `GENERATED` for extracted buildings
  (all elements have IFC source GUIDs via MA).

**Why a value object, not just two strings:** The previous code had `String
elementRef` flowing through 4 classes across 2 modules. The prefix was
applied in one place (walker line 528), consumed in another (writer line 943),
and checked in a third (SpatialDiff line 262). A single-string design makes
it easy to prefix once and forget that downstream consumers need the base
form. The record makes both forms available everywhere by construction.

**Witness claims:**
- W-GUID-1: PENDING — ElementIdentity enforced in PlacementCollectorVisitor.
- W-GUID-2: PENDING — SpatialDiff identity overlap ≥ 95% for DX.
- W-GUID-3: PENDING — Zero GENERATED guidSource for extracted buildings.

---

## 5. The 11-Stage Pipeline

*ParseStage removed in S140 — `ctx.definition()` was never consumed by any downstream stage. YAML is sole intent source.*

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against BOM + ERP databases |
| 2 | **Compile** | Explodes BOM tree, accumulates tack offsets into world coordinates. Includes generative MEP via `MEPDevicePlacer` ([§6.12.4](DISC_VALIDATION_DB_SRS.md)) |
| 3 | **Route** | MEP routing: CW/SP recipe expansion, RouteWalker pattern mining |
| 4 | **Template** | Template composition (SKIP for non-template buildings) |
| 5 | **Write** | Emits SQLite output DB (C_OrderLine + elements_meta). Resolves geometry via `source_element_ref` → component_library.db |
| 6 | **Verb** | BIM COBOL post-processing → W_Verb_Node audit trail |
| 7 | **Validation** | Rule-based validation against ERP.db constraints |
| 8 | **Digest** | Per-element SHA256 spatial fingerprint |
| 9 | **Geometry** | Mesh integrity validation |
| 10 | **Prove** | Mathematical placement proofs |
| 11 | **Compliance** | Building code compliance checks (AD_DocEvent_Rule) |

Single compilation path: element positions are read from m_bom_line (tack
offsets per §4) and accumulated through the BOM chain into world coordinates.
C_OrderLine → M_Product → BOM explosion (iDempiere prepareIt pattern).

**Viewer:** The compiled output.db feeds directly into the RTree Direct Stream
viewer — camera-driven BLOB tessellation from component_library.db, no
intermediate .blend files. See [RTree.md](RTree.md) §Direct Stream.

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 77 verbs in 5 tiers:

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

### 6.1 Workshop Verbs — Fabrication Instructions on Placed Products

Products in the catalog are **standard rectangular LODs** (uncut). This is correct —
a wall panel is manufactured rectangular and cut to fit on site. The IFC authoring
tool stores the clipped shape (IfcBooleanClippingResult), but extraction yields the
**pre-cut AABB extent**. The compiler places the uncut product at the tack offset.
Workshop verbs then describe the fabrication operation.

**Root verb + sub-verb pattern:**

```
verb_ref = PLACE                          ← placement (root)
workshop = CUT_TOP:ROOF_ASSEMBLY:CURVED   ← fabrication (sub-verb)
```

The root verb resolves WHERE. The sub-verb resolves HOW TO FABRICATE.

| Sub-verb | Operation | Example |
|----------|-----------|---------|
| `CUT_TOP` | Trim upper extent to constraining surface | Wall top → curved roof profile |
| `CUT_BOTTOM` | Trim lower extent to ground/slab slope | Wall base → sloped terrain |
| `CUT_SIDE` | Trim lateral extent to adjacent element | Wall end → angled corner |
| `NOTCH` | Rectangular cutout for penetration | Wall → pipe/duct passage |
| `MITER` | Angle cut at joint | Two walls meeting at non-90° |

**Construction workflow parallel:**

1. **Catalog** — standard product (rectangular panel, straight pipe)
2. **BOM line** — root verb places product at tack offset (compilation)
3. **Workshop instruction** — sub-verb says cut top to roof curve at this location
4. **Fabrication** — InterimWorkshop computes the cut profile from intersection
5. **Output** — product + cut instruction (dimensions, profile, reference element)

**Implementation via AttributeSet (iDempiere M_AttributeSet):**

Workshop sub-verbs are per-instance, not per-product. A wall type is rectangular
in the catalog; each placed instance gets an `M_AttributeSetInstance` carrying
its specific cut:

```
M_AttributeSet: WORKSHOP_CUT
  Attributes: cut_face (TOP/BOTTOM/SIDE), cut_ref_bom_id (constraining element),
              cut_profile (FLAT/CURVED/PITCHED), cut_offset_mm (overshoot depth)
```

This is the same pattern as InterimWorkshop for pipes (§6.12.2 §6) — UOM drives
pipe length, ASI drives wall trim. Both are fabrication instructions resolved at
compile time, not product properties.

**Scope:** Applies broadly as an envelope operation — all elements overshooting
the building envelope (roof, ground, perimeter) receive workshop cuts. Not
element-specific code; a single pass after placement identifies all overshoots
and generates ASI per instance.

---

## 7. Verification: The Rosetta Stone Gate

**Maths that proves visuals without cheating.**

> **Verification gates:** See [`TestArchitecture.md`](TestArchitecture.md) §Verification for the complete G1-G6 gate specification, tamper rules (T1-T16), and the 4-layer defense model.

All 6 gates GREEN for SH and DX. Current counts in [PROGRESS.md](https://github.com/red1oon/BIMCompiler/blob/master/PROGRESS.md).

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools. This is a reproducer from committed data. |
| Rule-based AI | No heuristics, no ML. Selection cascade is deterministic. |
| Parametric design | Parameters come from BOM data, not design exploration. |
| Approximate | Digest is SHA256. Same Order → same output. No tolerance. |
| Interactive | Batch compilation. Like COBOL/ERP — process the order, produce the output. |

---

## 9. The Data Flywheel — Emergent Intelligence

Processing the building fleet creates a **data flywheel** — each onboarded IFC
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
building corpus. Catches mis-labelled files, wrong discipline tagging, and
anomalous element compositions. Advisory only — never blocks.

**Storage:** `ad_building_profile` table in ERP.db (one row per building/ifc_class).

### 9.2 Flywheel Layers

| Layer | Status | Question | Spec |
|-------|--------|----------|------|
| 0 — Extraction | DONE | "Here are 97,000 elements" | Rosetta Stone pipeline |
| 1 — Dimension Ranges | DONE (s47) | "Is this wall a plausible size?" | DimensionRangeValidator |
| 2 — Building Profiles | DONE (s47) | "Does this building's composition make sense?" | §9.1 above |
| 3 — Shape Comprehension | EYES module | "This IfcWall is shaped like a beam" | [EYES_SRS.md](EYES_SRS.md) |
| 4–6 | Future | Relational patterns, sequence patterns, archetype clusters | — |

Each layer uses the same mechanism — query the corpus, aggregate, compare.
No neural networks. No training. Just SQL aggregation on real data.

---

## 10. The Compilation End State

The compiler runs without human assistance. Given a BOM database and the
product catalog (ERP.db + component_library.db), it produces a complete,
verified output.

Adding a new building = adding BOM data. The compiler is the constant.

**Viewer strategy:** The RTree viewer loads compiled output on demand without
modifying any pipeline DB. Geometry hash redirects and rotation corrections are
a library concern handled at viewer runtime — see
[DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) §12 for the full
impact analysis on the DAGCompiler/ERPtoDB pipeline.

---

## 11. Building Registration

Adding a new building = adding BOM data to the Application Dictionary.
`BuildingRegistry.loadActive()` reads C_DocType from the BOM database —
zero building names in Java. See [WorkOrderGuide.md](WorkOrderGuide.md)
for the onboarding pipeline.

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

---

## Audit Findings — S139

Session S139 investigated four first-principle questions about the CLUSTER verb and
the LMP boundary. Every claim cites a line number or commit hash.

### Finding 1 — DAGCompiler Isolation: NO BREACH

**Q: Does DAGCompiler open the extraction DB?**
**A: No.** Exhaustive grep of all `DriverManager.getConnection()` calls in
`DAGCompiler/src/main/java/` shows zero references to any `*_extracted.db` path.
`ExtractionPopulator` (IFCtoBOM module) is the only class that opens `_extracted.db`
and it is never called from DAGCompiler.
`§6.12.1` Compilation Isolation Invariant is **not breached**.

### Finding 2 — CLUSTER Coordinate Origin: GROUP-RELATIVE, NOT WORLD-RELATIVE

**Q: Are CLUSTER verb_ref coordinates world-relative or floor-relative?**
**A: Group-relative LBD-to-LBD.** `VerbDetector.java:449-517` (`detectCluster()`):

- `gMinX = elements.stream().mapToDouble(ExtractionElement::minX).min()` — group LBD min (line 454)
- `offsets[i][0] = e.minX() - gMinX` — element offset from group min LBD (line 463)

The `floorMinX/Y/Z` parameters are received by `detectCluster()` but are **not used**
inside it. The §4 tack convention is preserved at the parent level:
`VerbFactorizer.java:161-163` writes `dx = gMinX - parentMinX` for each CLUSTER
BOM line (group min LBD minus parent BOM LBD), making the group origin parent-relative.

**History:** At `9e73d753` [R16] (2026-03-18), `detectCluster()` used centroid-to-centroid
offsets (`gMinX = centroidX.min()`, `offsets[i][0] = e.centroidX() - gMinX`). At
`ff0f344e` [S101] (2026-03-30), changed to LBD-to-LBD (conformant with §4 tack).
Neither version stored world coordinates — offsets were always group-relative differences.

### Finding 3 — CLUSTER Small-Group Violations: NONE

**Q: How many CLUSTER lines in SH/DX BOM have < 4 instances?**
**A: Zero.** `VerbDetector.java:43` sets `MIN_GROUP = 4`. `detectCluster()` returns
`null` for groups < 4 (line 451). SH BOM CLUSTER counts: **4** (windows), **6**
(dining chairs), **10+10** (curtain wall). All ≥ MIN_GROUP. No spec violation.

**CLUSTER justification review:**
- Curtain wall (ASM_1/ASM_2, 10 elements each): Mixed member types (vertical mullions,
  horizontal rails, sills) with non-uniform dimensions AND varying Z — correctly falls
  through TILE→ROUTE→FRAME→CLUSTER. Vertical mullions at Y=0, 1.87, 3.75, 5.62 appear
  ROUTE-able but mixed with horizontal rails of different dimensions in the same product
  group — ROUTE rejects non-uniform dimensions (VerbDetector.java:243).
- Dining chairs (6 elements): End chairs are rotated 90° relative to side chairs →
  W/D swap (end: 0.427×0.443, side: 0.443×0.427) → TILE rejects non-uniform dims.
  CLUSTER is the only correct verb here.

**Verdict:** CLUSTER usage in SH is **not an offensive violation** — the fallthrough
is geometrically correct given mixed product orientations and non-uniform member types.

### Finding 4 — DX Room BOM Hierarchy: BY DESIGN, NOT A GAP

**Q: Where in CompositionBomBuilder are B-side room BOMs written, and to which parent?**
**A: Room BOMs are NOT written by CompositionBomBuilder — intentionally.**

The system uses two complementary mechanisms:

**Mechanism 1 — Structural shell via MIRRORED_PAIR:**
`CompositionBomBuilder.java` writes DUPLEX_SINGLE_UNIT_STD (walls, slabs, ceilings —
elements modeled for A-side only). UNIT_A gets `rotation_rule=0`, UNIT_B gets
`rotation_rule=π` (180° rotation, not mirror reflection). The B-side is the A-side
rotated about the building center. See [DuplexAnalysis.md §Rotation Center Proof](DuplexAnalysis.md#rotation-center-proof-s145-2026-04-05)
for the mathematical proof (paired element midpoints = building center = MEP core
centroid, zero slop after correct proximity-based pairing).

**Mechanism 2 — Room furniture via ScopeBomBuilder:**
The IFC file contains B-side room furniture at correct world positions (independently
modelled). `ScopeBomBuilder` reads both A-side and B-side rooms from IFC spatial
containment and writes them directly with storey-relative offsets
(`BomHierarchyBuilder.java:75-86`, `dx = childLbd[0] - storeyLbd[0]`). Room BOMs
need no rotation — their positions are already mirrored in the IFC source.

GEO log confirms: `DX_B102_SET` ENTER at anchor=(5.758, 18.253, 1.550) in first pass,
and the GEO validator second pass reaches these correctly. DX passes all gates (G1–G6).

**Conclusion:** The cascade not reaching room BOMs is **correct**. Room BOMs carry
IFC world positions for both sides directly. Applying π to them would double-mirror
the B-side furniture (moving it back to A-side positions). S139 initially misread
this as a gap; GEO log investigation resolves it as intentional two-mechanism design.

---

## Verb Gap — IFC Aggregate Path

*S140 investigation. Findings only — no code changes. S141 decides implementation path.*

### Current State

`VerbDetector` is geometry-only. It receives a flat `List<ExtractionElement>` with AABB
coordinates and attempts TILE → ROUTE → FRAME → SPRAY → CLUSTER. It is blind to IFC's
own grouping intent already captured in the extraction database.

`extractIFCtoDB.py` (lines 618–640) reads `IfcRelAggregates` (parent→child decomposition)
and writes to a `rel_aggregates` table (parent_guid, child_guid). The table is defined
in schema (line 101) and populated during extraction. However:

- `ExtractionReader.ExtractionElement` (17-field record) carries: AABB, `ifcClass`, `guid`,
  `hostElementRef` — **no aggregate parent, no mapped-item flag.**
- `StructuralBomBuilder` (lines 88–236) reads `rel_aggregates` and creates ASSEMBLY BOMs
  for groups with ≥2 matched children. These use `componentType("MAKE")`, not a verb_ref.
- No `AGGREGATE` verb type exists in the verb detection cascade.

### Findings

**F1. rel_aggregates populated but not consumed by VerbDetector.**
The extraction writes IFC aggregate relationships, and `StructuralBomBuilder` reads them
for assembly BOMs. But `VerbDetector` never sees them — it only receives element AABBs.
Elements that are IFC-declared assembly children (e.g. IfcCurtainWall → IfcMember[]) are
treated as independent elements for verb detection and typically fall to CLUSTER.

**F2. ExtractionElement needs an aggregateParentRef field.**
To let VerbDetector (or a pre-verb Phase 0) recognize IFC assemblies, the Java record
must carry the parent GUID from `rel_aggregates`. Currently 17 fields; adding
`String aggregateParentGuid` would enable grouping before geometric verb detection.

**F3. Conceptual Phase 0 — IFC_AGGREGATE.**
Before the TILE → ROUTE → FRAME → SPRAY → CLUSTER cascade, a Phase 0 could:
1. Group elements sharing an `aggregateParentGuid`
2. Emit one BOM level per aggregate group (parent product → member children)
3. Assign verb_ref `AGGREGATE:parentGuid:childCount` or simply use MAKE lines
4. Remaining ungrouped elements proceed through geometric verb detection as today

This would collapse IFC-declared assemblies (curtain walls, stair assemblies, railings)
into sub-BOMs *before* geometry tries to rediscover the grouping pattern.

**F4. BOM-to-BOM recursion (§2.2.2) enables this.**
An IfcCurtainWall with 13 IfcMember children naturally maps to:
```
FLOOR BOM
  └─ CURTAINWALL_01 (MAKE) → CURTAINWALL_01 BOM
       ├─ MEMBER_01 (BUY, dx,dy,dz)
       ├─ MEMBER_02 (BUY, dx,dy,dz)
       └─ ... (13 members, each with relative offset from curtain wall LBD)
```
The walker already supports N-level recursion. The new level is just another BOM.

**F5. IfcMappedItem (instanced copies).**
`extractIFCtoDB.py` handles `IfcMappedItem` for material extraction (doors/windows,
S138). The `object_type` TEXT column in `elements_meta` could flag mapped items.
Currently no verb encoding for IFC-native array repetition — these also fall to CLUSTER.

### Decision Required (S141)

1. Should Phase 0 IFC_AGGREGATE be implemented? Would reduce CLUSTER count for buildings
   with rich IFC grouping (SH curtain walls, DX stairs).
2. Should `ExtractionElement` gain `aggregateParentGuid`? Breaking change to record.
3. Should IfcMappedItem become a distinct verb (INSTANCE) or fold into AGGREGATE?

*See [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) for how BOM compilation positions this project in the industry.*
