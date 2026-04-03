# Project Order Blueprint

> **Foundation:** [MANIFESTO](MANIFESTO.md) · [BBC](BOMBasedCompilation.md) · [DISC_VALIDATION_DB_SRS](DISC_VALIDATION_DB_SRS.md)

<div class="bim-banner" markdown>
<b>An ERP is already a matured compilation of events. Define construction as such.</b>
</div>

iDempiere handles orders, costing, document flow, supplier/customer relationships,
cost margins, WIP, materials on site, and large-scale projects with one-to-many
orders — all proven over 20 years. This document maps those ERP patterns to
construction, showing what each iDempiere entity already solves.

**What this document covers:**

| Section | iDempiere pattern | Construction application |
|---------|------------------|------------------------|
| §1 | C_OrderLine exceptions | Order carries only deviations from base BOM. 200 houses in 1–6 lines each |
| §2 | C_Project | Site as BOM — multi-building developments, costing, doc flow, WIP |
| §3 | M_Product_Category tree | Domain by taxonomy, not code — construction, infrastructure, marine |
| §4 | DocAction=Approve | BOM mining — users promote reusable recipes from compiled buildings |
| §5 | Product master columns | nD dimensions — 4D schedule, 5D cost, 6D sustainability, 7D facility, 8D ERP |
| §6 | Ref_Order_ID (parent FK) | Order inheritance — composable variant overlays |
| §7 | Callout + ModelValidator | DiffVerb + Callout — reactive spatial editing with cascading rules |
| §8 | AD_ChangeLog | Full provenance and audit trail |
| §9 | AD_Org + AD_Val_Rule | Discipline as OrderLine — iDempiere processing order |

**The core thesis.** See [MANIFESTO.md](MANIFESTO.md).

---

## 1. Exception-Based Ordering — Configure-to-Order for Buildings

> **Status:** PARTIALLY IMPLEMENTED. Exception-based ordering works (Sessions D+E). Replace, Remove, Compress, Add all implemented. Reference class and indexed exceptions are future.

The full BOM explosion (BBC §3.4 BOM Drop) produces hundreds or thousands of
C_OrderLines. For a developer building 200 houses from the same base design,
this is redundant — the differences between units are a handful of product
swaps, not a different building.

**Principle:** A C_OrderLine records only *deviations* from the standard BOM.
The order says "build me a Duplex, but swap the bedroom door to sliding."
Two lines, not two hundred.

```
C_Order: "Build me a Duplex"                          ← 2 lines total
├── C_OrderLine #1: family_ref='BUILDING_DX_STD'      ← entire building, ONE line
│   (no exception — take the standard BOM as-is)
│
└── C_OrderLine #2: product swap exception             ← the deviation
    locator_ref  = 'Rm_Bedroom_1.Door_1'
    original     = DOOR_PANEL_900
    replacement  = DOOR_SLIDING_900
    (same M_Product_Category — category-constrained)
```

**Compile-time behaviour:** `bomDrop()` explodes the parent BOM fully in the
backend. When the explosion reaches the locator_ref named in an exception
line, it applies the swap. The rest of the tree passes through unchanged.
Output is deterministic — the same order always produces the same building.

**Why this works:**

| Concern | How it's handled |
|---------|-----------------|
| **Validation** | Swap must be within same M_Product_Category (same shelf). `MValRuleException` records acknowledged deviations |
| **Determinism** | Base BOM is immutable. Exceptions are ordered. Same Order → same output |
| **Scalability** | 200 houses = 200 orders of 1–6 lines each, not 200 × full explosion |
| **Versioning** | C_Order carries full audit trail. output.db is a reproducible materialised view |
| **ASI overrides** | M_AttributeSetInstance on the exception C_OrderLine carries per-instance parameters (e.g. colour, material finish) — same as customer options in manufacturing ERP |

### 1.1 Exception Algebra — Four Complete Mutations

Any building variant expressible as a finite diff against a base BOM can be
described with exactly four operations:

| Mutation | Exception | Example |
|----------|-----------|---------|
| **Replace** | swap product at locator_ref | Bedroom door → sliding door |
| **Compress** | qty=N, don't explode (§1.2 Reference Class) | 100 floors as one line |
| **Remove** | qty=0 at locator_ref | Standard duplex WITHOUT garage |
| **Add** | new C_OrderLine with locator_ref | Add solar panels (addDiscipline) |

This is a complete set. No other mutation type is needed. Every variant
is a combination of these four primitives applied to a base BOM.

The **qty=0 removal** is the minimalist complement to product swap. The order
says "everything in the standard BOM, except delete the node at this
locator_ref and its entire subtree." The compiler skips that branch during
explosion. The parent AABB recalculates (the removed child's space becomes
available buffer or the parent shrinks).

### 1.2 Reference Class Pattern — Qty Without Explosion

A skyscraper BOM has three children: Roof, Body, Slab. Body has child
FLOOR_STD with `BOMQty=100`. Full explosion produces 100 identical
C_OrderLines. But every floor is the same — the order should say so in
one line.

**Reference class:** A C_OrderLine with `qty > 1` that is NOT exploded
into individual lines. It declares a *type* and a *count*. The compiler
instantiates at compile time, computing spatial placement (dz per floor)
from the BOM recipe's offset rules.

```
C_Order: "Build me a 100-storey Tower"                  ← 3 lines total
├── C_OrderLine #1: family_ref='TOWER_100F_STD'         ← the grandparent
│   (contains Roof + Body + Slab — ONE line)
│
├── C_OrderLine #2: reference class override
│   locator_ref  = 'Body.FLOOR'
│   family_ref   = 'FLOOR_STD'
│   qty          = 100                                   ← NOT exploded
│   (compiler instantiates 100 floors at regular dz)
│
└── C_OrderLine #3: indexed exception
    locator_ref  = 'Body.FLOOR[47]'                     ← floor index
    swap: LOBBY_STD → LOBBY_EXECUTIVE
    (only floor 47 gets the executive lobby)
```

Three lines for a 100-storey skyscraper with one custom floor.

**Three ordering modes — increasing compression:**

| Mode | Order says | Compiler does | Lines for 100 floors |
|------|-----------|---------------|---------------------|
| **Full explosion** | 100 lines, one per floor | Places each | 100 |
| **Product swap** (§1) | 1 grandparent + swap exceptions | Explodes, swaps | 2–10 |
| **Reference class** | 1 line × qty + indexed exceptions | Instantiates, swaps by index | 1–3 |

**ERP precedent:** This is the manufacturing distinction between
**discrete** and **repetitive** production. Discrete: one work order per
unit (100 work orders). Repetitive: one work order with qty=100, produced
on a line. iDempiere supports both via `C_OrderLine.QtyOrdered`. The
reference class pattern applies the same principle to BOM explosion.

**Indexed exceptions** use `locator_ref` with an array index suffix
(`[47]`). The index is the ordinal position in the parent's child sequence.
The compiler resolves the index to a specific spatial position during
instantiation. Multiple indexed exceptions on the same reference class
are supported — each targets a different instance.

**Why this matters for the abstract tree (§3):** The reference class
pattern proves the tree is truly abstract. A BOM node is not "a floor" —
it is "a typed slot that can be instantiated N times." The category says
what kind of thing fills the slot. The qty says how many. The indexed
exception says which instances deviate. This is the same pattern whether
the repeated element is a floor, a bridge span, a ship frame, or a
parking bay.

---

## 2. C_Project — Site as BOM

> **Status:** Future — design specification only.

**Friction:** 200 houses on a development site, each a separate C_Order.
The site has its own constraints — road layout, utility routing, setback
distances. Managing 200 independent orders with no parent is unmanageable.

**C_Project groups orders. M_Locator places them.** C_Project is the
iDempiere parent for multi-order engagements — project accounting,
milestone billing, subcontractor management. M_Locator (Aisle/Lot/Bin)
provides the site grid — each plot is a locator, each building is
placed into a locator. No BOM at site level — the grid IS the address.

```
C_Project: "Taman Melati Phase 2"                      ← the development
│   M_Warehouse → site locator grid (Street/Lot/Terrace)
│
├── C_Order[1..180]: BUILDING_SH_STD → M_Locator[1..180]  ← 180 plots
├── C_Order[181..195]: BUILDING_DX_STD → M_Locator[181..195]  ← 15 plots
├── C_Order[196]: BUILDING_SH_CORNER → M_Locator[12]      ← corner lot
└── C_Order[197..200]: BUILDING_SH_PREMIUM → M_Locator[197..200]
```

**iDempiere mapping:**

| BIM Compiler | iDempiere | What it does |
|-------------|-----------|-------------|
| Development site | C_Project + M_Warehouse | Groups orders, provides locator grid |
| Plot | M_Locator (Street/Lot/Terrace) | ABL address for each building position |
| Building order | C_Order (FK → C_Project, FK → M_Locator) | Exception-based order placed on a plot |
| Plot availability | M_StorageOnHand | 0 = empty plot, 1 = occupied |
| Project budget | C_Project.PlannedAmt | SUM of all C_Order costs |

**What C_Project + M_Locator triggers in an ERP person's mind:** project
accounting, warehouse management, put-away strategy, milestone billing,
subcontractor management, progress reporting. All solved problems in
iDempiere — without new code.

*Test architecture for C_Project: see [TestArchitecture.md](TestArchitecture.md).*

### 2.2 Site Layout as Warehouse Put-Away (S67)

> **Principle:** A housing development site IS a warehouse. Plots are locators.
> Buildings are inventory. The put-away strategy assigns buildings to plots.
> Terrain topology defines the locator grid (ABL = Street/Lot/Terrace).

**iDempiere mapping:**

| Warehouse | Site | Entity |
|-----------|------|--------|
| M_Warehouse | C_Project | Site development |
| M_Locator (Aisle/Bin/Level) | Plot (Street/Lot/Terrace) | ABL addressing |
| M_LocatorType | PlotType (STANDARD/CORNER/PREMIUM/INFRA/GREEN) | Plot classification |
| M_Locator.capacity | Plot frontage × depth - setbacks | Buildable area |
| M_PutAwayStrategy | SitePlacementStrategy | Which building goes where |
| M_InOutLine | C_Order FK → Plot | One building placed on one plot |
| M_InOutLineMA | Plot attribution | Variant, exceptions, terrain Z |

**Terrain as Locator ABL.** The terrain survey (PDFTerrain: 689 points, 294m × 229m,
Z range 28-48m) defines the physical warehouse floor. Natural terrace bands become
Levels. Roads cut across contours to define Aisles. Lots are sequential along each
road. Each plot's Z is interpolated from the survey via `AlignmentContext.elevationAt(x, y)`.

```
Terrace 2 (Z ≈ 44-48m)  ═══ Street 3 ═══  [P][P][P][P][S][P][P]
                                             PREMIUM lots (hilltop view)
Terrace 1 (Z ≈ 38-44m)  ═══ Street 2 ═══  [C][S][S][S][S][S][S][C]
                                             STANDARD + CORNER lots
Terrace 0 (Z ≈ 28-38m)  ═══ Street 1 ═══  [S][S][S][S][S][S][S][S][S]
                                             STANDARD lots (river valley)
```

**Put-away rules (ad_site_placement_rule):**

| Plot Type | Building Variant | Orientation | Priority |
|-----------|-----------------|-------------|----------|
| CORNER | SH_CORNER | FACE_STREET | 10 |
| PREMIUM | SH_PREMIUM | FACE_VIEW | 20 |
| STANDARD | SH_STD | FACE_STREET | 99 |
| INFRA | SITE_INFRA_STD | ALONG_STREET | 1 |
| GREEN | NULL (no building) | — | 1 |

**Validation constraints:**
- Building AABB fits within plot (frontage, depth, setback)
- Adjacent buildings: gap ≥ side_setback × 2
- Building height ≤ zoning maximum
- Aggregate footprint ≤ site_aabb
- Infrastructure easements respected

**Test case:** 689-point survey site, 24 houses on 3 terraces:
- Terrace 0: 9 SH_STD (river level, Z ≈ 30m)
- Terrace 1: 8 SH_STD + 2 SH_CORNER (mid-slope, Z ≈ 40m)
- Terrace 2: 4 SH_PREMIUM + 1 GREEN (hilltop, Z ≈ 46m)
- Infrastructure: 3 road segments connecting terraces

**Source data:** `IfcOpenShell/.../pdf_terrain/samples/survey_highres_extracted.json`

**Existing infrastructure (all proven, all tested):**
- PDFTerrain: 689-point survey extraction (W-CONTEXT-TERRAIN-1)
- AlignmentContext: `elevationAt(x, y)` interpolation
- TerrainSnap: ON_SURFACE/ABOVE/BELOW snap modes
- CutFillCalculator: earthwork volumes (cut/fill/net m³)
- GradingStrategy: CONTOUR/STRAIGHT/BLEND modes

**Two separate concerns:**

| Concern | Mechanism | iDempiere parallel |
|---------|-----------|-------------------|
| **Site grid** | M_Locator (Street/Lot/Terrace) | Warehouse ALB addressing |
| **Building content** | BOM (root → children → leaf) | Manufacturing BOM |

The locator gives each plot its XYZ position on the site. The building's
own BOM handles everything inside the building. C_Order.M_Locator_ID links
a building to its plot — same as M_InOutLine linking inventory to a bin.

M_StorageOnHand = 0 means the plot is empty (available). Put-away strategy
assigns building variants to plot types. This is standard WMS — no new
infrastructure, just iDempiere's existing warehouse module applied to land.

---

## 3. Abstract Category Tree — Domain by Taxonomy, Not by Code

> **Status:** Architectural observation — already emergent in the current model.

The BOM tree has no hard-coded domain concepts. There is no "Building" class,
no "Floor" class, no "Room" class. There are only **BOMs classified by
M_Product_Category**. The hierarchy is a cascade of categories:

```
BOM(cat=RE) → BOM(cat=L1) → BOM(cat=LI) → BOM(cat=FR)   ← Construction
BOM(cat=BRIDGE) → BOM(cat=SPAN) → BOM(cat=DECK) → BOM(cat=GIRDER)  ← Infrastructure
BOM(cat=VESSEL) → BOM(cat=HULL) → BOM(cat=SECTION) → BOM(cat=FRAME) ← Marine
```

The engine — bomDrop, Selection Cascade (BBC §3.5), verb execution, gate
verification — operates on abstract BOM nodes. It never asks "is this a
building?" It asks "does this BOM have children? does the child fit the
parent AABB? what category constrains the swap?"

**Users paint branches onto the tree** by defining a category taxonomy:

1. Create M_Product_Category rows (e.g., BRIDGE, SPAN, DECK, GIRDER)
2. Create M_Product entries in each category with AABB dimensions
3. Create M_BOM recipes linking products into parent-child hierarchies
4. Import component_library geometry for the leaf products

No code changes. No new verbs (unless the domain needs domain-specific
operations — a marine WELD verb, an infrastructure GRADE verb). The
compilation engine, the Bonsai viewport, the gate verification, the
exception-based ordering (§1) — all work unchanged.

**Category cascade structure:** The taxonomy itself is a tree. RE contains
L1, L2. L1 contains LI, KT, BT. This mirrors how M_Product_Category works
in iDempiere — categories have parent categories. The BOM Selection Cascade
(BBC §3.5) already walks this structure. A new domain is a new category
subtree, not a new codebase.

**Concrete proof:** [ShipYard.md](ShipYard.md) details a marine vertical — hull
plates as TILE verb output, station offsets as curved surface provider, exception
ordering for ice-class reinforcement. Phase 1 (flat-tile hull, 300 elements)
compiles through the existing pipeline with zero code changes.

---

## 4. BOM Mining — Approve as Promote

> **Status:** Future — design specification only.

**Friction:** 34 buildings compiled from IFC extraction. Each required manually
authoring M_BOM recipes. But if Building A and Building B share 80% of their
BOM tree, that shared subtree was authored twice.

**DocAction = Approve promotes a validated order into a reusable BOM artifact.**
This is the iDempiere Approve action repurposed: instead of approving a
purchase order for payment, you approve a compiled building order for
promotion into the BOM library.

**The workflow:**

1. **Import** two IFC buildings → extract BOMs → compile → gates pass
2. **Diff** the two compiled BOM trees (same infrastructure as Rosetta Stone
   gates, run sideways — not "compiled vs reference" but "Building A vs Building B")
3. **Select** the common subtree in the Bonsai viewport or BOM Tree tab —
   lasso a set of nodes, like Photoshop selection
4. **Approve** → the selected subtree is promoted to a new M_BOM in {PREFIX}_BOM.db
   with a name, category, and AABB
5. **Both buildings** now reference the shared recipe. Deviations become
   exception-based orders (§1)

This is **crafting** — like Photoshop Actions or Blender node groups.
The user discovers reusable patterns through work, selects them, and
promotes them to first-class library artifacts. The BOM library writes
itself through accumulated practice.

**Why DocAction = Approve:** The promotion is deliberate and audited.
It's not automatic extraction — the user curates what becomes reusable.
The Approve action in iDempiere already carries audit trail, user ID,
timestamp, and can require multi-level approval for quality control.
A promoted BOM is a vetted artifact, not a raw extraction.

**Aggregation of effort:** Over time, the BOM library converges toward
a minimal set of reusable recipes. 34 buildings might share 12 common
floor plans, 8 room layouts, 5 roof types. Each Approve cycle reduces
redundancy. The library becomes a curated encyclopedia of construction
patterns — contributed by everyone who compiles a building.

---

## 5. nD Dimensions — Every D Is a Column

Every "D" above 3D is a column on iDempiere entities, not a separate system:

| Dimension | iDempiere entity | What it is |
|-----------|-----------------|------------|
| **4D** Schedule | BOM tree topological sort | Depth-first walk = construction sequence. Critical path = deepest branch |
| **5D** Cost | M_Product.unit_cost_rm × C_OrderLine.qty | Cost delta of an exception = one query |
| **6D** Sustainability | M_Product columns (embodied carbon, EPD) | SUM = building carbon footprint |
| **7D** Facility Mgmt | M_Product columns (maintenance, warranty) | Compiled order = asset register |
| **8D** ERP/BI | C_Order, C_Project, C_BPartner | Procurement, WIP, cash flow — native iDempiere |

Populating these is data entry on the product master, not development.
See [MANIFESTO.md](MANIFESTO.md) §Why This Matters.

---

## 6. Order Inheritance — Composable Variants

> **Status:** IMPLEMENTED (Session E, S68e). `InheritanceResolver`, `Ref_Order_ID`. OrderInheritanceTest 6/6.

An order declares a **parent order** via `C_Order.Ref_Order_ID`:

```
DX_BASE                          ← 1 line (standard duplex)
  └─ DX_SOLAR (parent=DX_BASE)  ← +2 lines (solar panels + switchboard)
      └─ DX_SOLAR_PREMIUM (parent=DX_SOLAR)  ← +3 lines (premium finishes)
```

DX_SOLAR_PREMIUM carries 3 lines, not 6. Each overlay is 2-3 lines — like CSS layers stacked on a base. The library of overlays is the product catalog of upgrades.

**Key design rules:**

1. **Chain walking:** `InheritanceResolver` walks `Ref_Order_ID` root-first, collecting exception C_OrderLines at each level. Algorithm: `Map<locator_ref, ExceptionLine>`, root-to-leaf overwrite. O(N).
2. **Last descendant wins:** When two orders in the chain target the same `locator_ref`, the deeper order (closer to leaf) takes effect.
3. **Single-parent only (GAP-SC-5 resolved):** `Ref_Order_ID` is a scalar FK — diamond inheritance is structurally impossible. To combine siblings, author a new order with both exception sets explicitly.
4. **Within-order ordering:** Multiple exception lines in one order apply in `C_OrderLine.Line` order. Higher line number wins at same `locator_ref`.
5. **Cycle detection:** Track visited IDs during chain walk; cycle → `IllegalStateException`.
6. **Duplicate warning:** Two lines in the same order targeting the same `locator_ref` triggers a warning (likely user error), does not block.

---

*FOSS ecosystem model and strategic positioning: see [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md).*

---

## 7. Reactive Editing — DiffVerb + Callout

iDempiere's Callout mechanism applied to spatial editing. Three layers:

| Layer | iDempiere parallel | Construction use |
|-------|-------------------|-----------------|
| **ASI** | M_AttributeSetInstance | Static overrides: colour, material, finish |
| **DiffVerb** | W_Verb_Node (replayable delta) | Spatial mutations: "move fireplace 300mm left" |
| **Callout** | AD_Rule (cascading consequence) | Flue follows, hearth resizes, clearance re-validates |

Every edit is recorded, replayable, composable. The edit session is a
sequence of DiffVerbs — like iDempiere's AD_ChangeLog applied to spatial
operations. Full provenance: who changed what, when, from-what, to-what.

---

## 8. AD_ChangeLog — Provenance

iDempiere's [AD_ChangeLog](https://wiki.idempiere.org/en/AD_ChangeLog)
records every field change on every record. Applied to construction:
every promoted BOM (§4), every DiffVerb (§7), every exception mutation —
all recorded with attribution, lineage, and verification state.

---

## 9. Discipline as OrderLine — The Add Mutation

Each discipline (FP, ELEC, ACMV, CW, SP, LPG) is an **Add** mutation on
the order (§1.1). One C_OrderLine per discipline, with:
- **Product** → the top BOM of that discipline (e.g. FP_SYSTEM)
- **Category** → the entry point tier (e.g. FP_MAIN_ROOM)
- **AD_Org** → the discipline identity, blanket-triggers validation

Processing follows standard iDempiere document order
(see [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) §10.4):

| Stage | iDempiere parallel | What it does |
|-------|-------------------|-------------|
| **1st: DocEvent per Org** | ModelValidator.docValidate() | Discipline blanket rules, top-down walk |
| **2nd: AttributeSet** | M_AttributeSetInstance | Per-product/per-instance resolution |
| **3rd: AD_Val_Rule** | AD_Val_Rule | Government standards, post-hoc compliance |

### 9.1 Discipline States on the Order

| State | What happens |
|-------|-------------|
| **Absent** | No discipline OrderLine. Validation fires warnings if jurisdiction requires it |
| **Proposed** | System suggests OrderLine via `docValidate(TIMING_BEFORE_COMPLETE)`. Architect reviews |
| **Accepted** | OrderLine active. Compilation explodes the discipline BOM cascade |

iDempiere parallel: `ModelValidator.docValidate()` proposing lines before
order completion — same pattern, applied to construction disciplines.

### 9.2 Compliance Rule Packs

AD_Val_Rule rows are jurisdiction-specific, importable, versioned:

| Pack | Jurisdiction | Authority |
|------|-------------|-----------|
| UBBL-2024 | Malaysia | KPKT |
| NFPA-13 | USA/intl | NFPA |
| BCA-2019 | Singapore | BCA |
| IBC-2021 | USA (model) | ICC |

Same BOM, same Org practices, different rule pack = different jurisdiction.
The engine doesn't change — only the post-hoc validation rules.

---

---

## 10. Product Chooser Taxonomy — Finding Products in a Rich Fleet (S136 TODO)

> **Status:** Future — design specification only. Triggered by Hospital's 23,888-product
> library and the question of how users navigate them.

The `component_library.db` currently holds **23,888 products** across all extracted buildings.
The current access model (compiler reads by `M_Product_ID`) is correct for pipeline use but
provides no user-facing navigability. When Hospital, Clinic, and Terminal products are all
seeded, a user choosing a wall for an ICU needs a structured search path, not a flat list.

### 10.1 The Four-Axis Chooser

iDempiere's existing schema gives us four orthogonal navigation axes:

| Axis | iDempiere entity | Example values | User sees |
|------|-----------------|----------------|-----------|
| **Building domain** | M_Product_Category (top level) | HO (Hospital), TE (Airport), RE (Residential) | "I'm working on a hospital" |
| **Discipline** | AD_Org_ID | ARC, STR, MECH, SPR, PLB, ELE | "I need a plumbing fixture" |
| **Element category** | component_types.category | WALL, DOOR, PIPE_SEGMENT, SPRINKLER, STAIR | "I need a wall" |
| **Size / material** | M_AttributeSetInstance | 200mm brick, 100mm glass, 25ACH HVAC unit | "I need 200mm thickness" |

Navigation path for a hospital ICU wall:
```
HO (Hospital domain)
  → ARC (architectural discipline)
    → WALL (element category)
      → M_AttributeSetInstance: 100mm | plasterboard | STC-50 acoustic
```

Current library inventory by discipline+category (23,888 products total):
- ARC: IfcPlate(8,022) + IfcWall(372) + IfcDoor(129) + IfcWindow(202) + IfcStair(34) + ...
- FP: IfcPipeSegment(3,788) + IfcFireSuppressionTerminal(892) + ...
- ACMV: IfcDuctFitting(683) + IfcAirTerminal(268)
- MEP: IfcFlowSegment(417) + IfcFlowFitting(291)
- STR: IfcBeam(412) + IfcMember(399) + IfcColumn(122)
- ELEC: IfcLightFixture(803) + IfcElectricAppliance(22)

**Gap:** Products currently have no human-readable names — they are identified by geometry
hash + dimensional signature (dx × dy × dz). The chooser needs display names:
`"Toilet-Wall-Mounted 450×350×820"` not `"IfcBuildingElementProxy|geom_hash_a3f7c..."`

### 10.2 Naming Convention TODO

Product display name = `{IFC_Category}_{Material}_{DimSignature}` or, where Revit family
names are preserved in `element_name`, use those directly:
- Hospital toilet: `Toilet-Wall-Mounted:454944` → display as `Wall Toilet 450×350×820 (HTM-63)`
- Hospital sink: `M_Sink - Island - Single:455mmx455mm` → `Island Sink 455×455 (clinical grade)`
- SPR head: `IfcFireSuppressionTerminal` → `Sprinkler Head — Pendent 15mm (NFPA 13 LIGHT)`

**Naming session scope:** One session. Read `element_name` from `elements_meta` for each
unique `geometry_hash` → populate `component_definitions.name` with cleaned display string.
Hospital (`MECH_`, `PLB_`, `SPR_` elements) will seed this for the first time with real
Revit family names. This makes the chooser usable for HO_ products immediately.

### 10.3 BOMType as Second Chooser Axis (iDempiere Pattern)

`m_bom.bom_type` currently holds a tier label (`ROOT`, `FLOOR`, `ROOM`). Re-read as
a chooser dimension: BOMType = the **context** where a product appears in the BOM tree.
A sprinkler head only appears in FPR discipline BOMs. A toilet appears in both ARC and PLB.

The user's chooser question: *"Show me all products that appear in ARC BOMs for ICU floors"*.
Answer: filter by `m_bom.bom_category LIKE 'HO_ARC_ICU%'` + `component_types.category`.
No new schema needed — BOMType and M_Product_Category together form the product shelf.

---

## 11. Healthcare Campus — Ultimate PoC Project Order (S136 TODO)

> **Status:** Future concept — design specification only.

The most ambitious demonstration of the Compiler-Designer model: a complete healthcare
campus as a single `C_Project`, compiled from multiple building types, resting on real
terrain with cut-and-fill earthworks.

### 11.1 Campus Composition

```
C_Project: "Healthcare Campus — Phase 1"
│   M_Warehouse → campus site grid (UTM coordinates as ABL)
│   PDFTerrain: 689-point survey → M_InOutLineMA for Z elevation per plot
│
├── C_Order[1]: Hospital (HO_)          → M_Locator[A1]   ← 62,291 elements, 7 disciplines
│     C_DocType: CO_HOSP                                   ← large healthcare complex
│
├── C_Order[2]: Clinic (CL_)            → M_Locator[A2]   ← ~16K elements (federated)
│     C_DocType: CO_CLIN                                   ← outpatient + specialist
│     Ref_Order_ID → Hospital (inherits campus MEP riser specs)
│
├── C_Order[3..N]: Staff Quarters (RE_) → M_Locator[B1..N] ← residential units
│     C_DocType: RE_SH or RE_DX                            ← SampleHouse / Duplex variants
│     Reference class: qty=20, exception-based variants
│
└── C_Order[INFRA]: Site Infrastructure → M_Locator[SITE]
      C_DocType: IN_SITE
      ├── Road network (IfcRoad segments)
      ├── Utility risers (MEP campus trunks)
      └── Cut-and-fill earthworks (PDFTerrain → IfcEarthworksFill)
            terrain_z from survey_highres_extracted.json (689 points, 294m×229m)
            → INFRA_DESIGNER_SRS.md §Cut-and-Fill
```

### 11.2 Why This Is the Ultimate Demo

| Capability | What it proves |
|-----------|---------------|
| Multi-building C_Project | iDempiere C_Project groups Hospital + Clinic + Staff housing in one accounting entity |
| Order inheritance | Staff quarters inherit campus MEP specs from Hospital via `Ref_Order_ID` |
| Reference class | 20 staff units = 1 C_OrderLine with qty=20, indexed exceptions for corner plots |
| PDFTerrain + cut-and-fill | Real survey data drives earthwork BOM (INFRA_DESIGNER_SRS.md) |
| Cross-domain ERP | Hospital procurement (clinical grade), residential (standard grade), infra (civil) all in one ERP.db |
| GPU-instanced DB | ~80K+ elements across 3+ building types, all rendering via transform-reference — no scene-graph collapse |
| RouteSprinklersVerb | Hospital SPR + Clinic FP + Staff quarters FP all validated in one gate run |
| DECLARE ROOMS → FINISH WALLS → 2D floor plan | Full round-trip on every building in the campus |

### 11.3 Prerequisites Before Campus Session

| Prerequisite | Where | Status |
|-------------|-------|--------|
| Hospital HO_001–HO_005 complete | HospitalAnalysis.md | Not started |
| Clinic federation merge (--guid-prefix) | DISC_VALIDATE_SRS.md §TODO | Not started |
| PDFTerrain → IfcEarthworksFill pipeline | INFRA_DESIGNER_SRS.md §Current State | Partial spec |
| DECLARE ROOMS (Hospital + Clinic) | FINISH_WALLS_SRS.md §11 | Designed, not implemented |
| HO_ product categories in ad_ifc_class_map | DISC_VALIDATE_SRS.md §TODO-HO-PC | Not started |
| C_Project multi-order grouping | §2 (this doc) | Design only |

**The campus demo is 6–8 sessions of work beyond HO_005.** It is the right north star.
Each hospital session (HO_001–HO_005) makes progress toward it. The terrain and
residential orders reuse fully proven code (SH/DX already gate at 8/8 PASS).

---

*Implementation history (Sessions 0-F, all DONE) archived in git history.*
