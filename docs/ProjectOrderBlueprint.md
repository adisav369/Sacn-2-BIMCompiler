# Project Order Blueprint

> **Foundation:** [MANIFESTO](MANIFESTO.md) · [BBC](BOMBasedCompilation.md) · [BIM_Designer_SRS](BIM_Designer_SRS.md)

## Executive Brief

**What this is.** A BIM compiler that treats buildings as manufactured
products. A building is a C_Order. Rooms, floors, and walls are M_Products
in a Bill of Materials. The compiler explodes the BOM, places elements
spatially, and produces a verified 3D model — the same way a factory
produces a product from a parts list. Built on iDempiere ERP conventions,
SQLite, and the Bonsai/BlenderBIM open-source 3D viewport.

**What is proven today (Session 60, March 2026).**

| Achieved | Evidence |
|----------|---------|
| 35 buildings compiled (34 extracted from IFC + 1 generative) | Rosetta Stone gate tests, 19 ALL GREEN |
| 6-gate verification (count, volume, digest, tamper, provenance, isolation) | RosettaStoneGateTest — deterministic, reproducible |
| 9-stage pipeline, 64 verbs, 2,475 products | Full BOM library with 4D/5D/6D on real data |
| 48,428-element Terminal complex compiled | Largest known BOM-based BIM compilation |
| BOM Drop + product swap + compile in BIM Designer | bomDrop(SH) + swapProduct(roof) + compile = 95 elements, gates pass |
| HTML UI (10 tabs) + Bonsai 3D viewport, bidirectional sync | DocAction buttons: Draft → Approve → Complete → Promote |
| Federation addon: work packages, equipment, labour breakdown | Visual BI over SQLite — click to query |
| 392 unit tests, 28 proof assertions, all GREEN | BIMBackOffice 5/5, BonsaiBIMDesigner 392/392 |
| Scorecard: 31/36 (nearest competitor: 9) | 4D/5D live DAOs, 6D/7D/3D/CR/Audit scored |

**What is on the way (this document).**

| Section | Concept | Impact |
|---------|---------|--------|
| §1 | Exception-based ordering — order carries only deviations from base BOM | 200 houses in 1–6 lines each, not full explosion |
| §2 | C_Project — site as BOM, multi-building developments | Entire housing development = 5 lines under one C_Project |
| §3 | Abstract category tree — domain by taxonomy, not code | Same engine for construction, infrastructure, marine, automotive |
| §4 | BOM mining via DocAction=Approve | Users discover and promote reusable recipes from compiled buildings |
| §5 | nD dimensions as queries (4D schedule, 5D cost, procurement) | Every pain point is a SQL query, not a separate tool |
| §6 | Order inheritance — composable variant overlays | Stack upgrades like CSS layers |
| §7 | FOSS ecosystem — open commons model | Wikipedia for construction: engine is free, expertise is the product |
| §9 | DiffVerb + Callout — reactive spatial editing | Drag in viewport → cascading rule-driven consequences |
| §10 | AD_ChangeLog — full provenance and audit trail | Who changed what, when, why — Wikipedia edit history for BOMs |
| §11 | The 8th D — ERP as Business Intelligence | $500B/yr industry waste becomes queryable from one database |
| §12 | Callout rule library — compliance packs by jurisdiction | UBBL, IBC, NFPA as importable rule sets — name-dropping that sells |

**The core thesis.** See [MANIFESTO.md](MANIFESTO.md).

---

## 1. Exception-Based Ordering — Configure-to-Order for Buildings

> **Status:** Future — design specification only.

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
| **Validation** | Swap must be within same M_Product_Category. AABB fit re-checked at swap point. `MValRuleException` (§U6) records acknowledged deviations |
| **Determinism** | Base BOM is immutable in {PREFIX}_BOM.db. Exceptions are ordered. Same input → same output |
| **Scalability** | 200 houses = 200 orders of 1–6 lines each, not 200 × full explosion. The combinatorial space lives in the BOM library, not in orders |
| **Versioning** | C_Order in iDempiere proper carries full audit trail. output.db is a reproducible materialised view |
| **ASI overrides** | M_AttributeSetInstance on the exception C_OrderLine carries per-instance parameters (e.g. colour, material finish) — same mechanism as customer options in manufacturing ERP |

**The order library as asset.** Save all orders in a library. Each is a tiny
diff against a base BOM — like a git branch with a small patch. The library
becomes the developer's real IP: not the geometry (that's in
component_library.db), not the recipes (that's in {PREFIX}_BOM.db), but the
*curated set of building variants* expressed as minimal exception sets.

A catalogue of 50 variants × 3 base types = 150 products, each stored as
1–6 C_OrderLines. The compilation engine is deterministic and dynamic — it
will always reproduce the same output from the same order. The asset is the
order library; the engine is infrastructure.

**Relationship to existing patterns:**
- Extends bomDrop (BBC §3.4) with selective explosion
- Uses swapProduct from the Universal Configurator (ProjectOrderBlueprint.md §1) at targeted locator_refs
- Category constraint enforced by M_Product_Category (same as BOM Selection Cascade BBC §3.5)
- Validation exceptions via AD_Val_Rule_Exception (§U6, `MValRuleException`)
- Compatible with BIM Designer Save/Recall/Promote lifecycle (DATA_MODEL.md §1) — the exception order is what gets Promoted

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
on a line. iDempiere supports both via `PP_Order.QtyOrdered`. The
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

**The site IS a BOM.** C_Project is the iDempiere parent that contains
multiple C_Orders. Any ERP person recognises C_Project immediately — it's
how iDempiere manages multi-order engagements (construction projects,
consulting engagements, government contracts).

```
C_Project: "Taman Melati Phase 2"                      ← the development
│   site_aabb = 400m × 300m
│   infrastructure_bom = SITE_INFRA_STD
│       (roads, drainage, power — reference class children)
│
├── C_Order[1..180]: base_bom='BUILDING_SH_STD'        ← 180 standard houses
│   (reference class — ONE order template × qty=180)
│
├── C_Order[181..195]: base_bom='BUILDING_DX_STD'      ← 15 duplexes
│   (reference class × qty=15)
│
├── C_Order[196]: exception on plot[12]                 ← corner lot variant
│   swap: BUILDING_SH_STD → BUILDING_SH_CORNER
│
└── C_Order[197..200]: base_bom='BUILDING_SH_PREMIUM'  ← 4 premium units
    (full exception set: upgraded finishes, solar, landscaping)
```

**The development is 5 lines.** 200 buildings, site infrastructure, corner
lot exception, premium variant — expressed as reference classes with
indexed exceptions. The entire project compiles from this.

**iDempiere mapping:**

| BIM Compiler | iDempiere | What it does |
|-------------|-----------|-------------|
| Development site | C_Project | Groups related orders, tracks project-level budget/schedule |
| Plot allocation | C_ProjectLine | One line per plot or plot group |
| Building order | C_Order (FK → C_Project) | Exception-based order per building variant |
| Site infrastructure | C_Order (type=INFRA) | Roads, drainage, power — BOM with reference class children |
| Project budget | C_Project.PlannedAmt | SUM of all C_Order cost deltas from base |

**What C_Project triggers in an ERP person's mind:** project accounting,
milestone billing, subcontractor management, progress reporting, variance
analysis. All of these are solved problems in iDempiere. By making the
development a C_Project, every ERP feature that works on projects
works on building developments — without new code.

### 2.1 CTFL Test Plan — C_Project (S67 Watchdog)

> **Method:** ISTQB test design techniques. Test framework BEFORE code.
> **Blocking bug found:** R-PROJ-3 (see §2.1.4).

#### 2.1.1 Equivalence Partitions

| EP | Category | Representative Value |
|----|----------|---------------------|
| EP-1 | Single-variant project | 200 orders, all base_bom='BUILDING_SH_STD' |
| EP-2 | Multi-variant project | 180 SH_STD + 15 DX_STD + 4 SH_PREMIUM + 1 SH_CORNER |
| EP-3 | Project with infrastructure | EP-2 + C_Order(type=INFRA) |
| EP-4 | Indexed exception | EP-1 + exception on plot[12] swap SH_STD→SH_CORNER |
| EP-5 | Empty project (invalid) | C_Project with no C_ProjectLines |
| EP-6 | Exceeds site AABB (invalid) | Aggregate footprint > 400m × 300m |
| EP-7 | Invalid base_bom ref (invalid) | References nonexistent 'BUILDING_XX_PHANTOM' |
| EP-8 | Overlapping footprints (invalid) | Two buildings at identical (dx, dy) |

**Boundary values:** qty=1 (minimum project), qty=2 (minimum multi-building), qty=200 (target scale), site AABB exactly full, site AABB overflow by 1mm, exception index [0], [199], [200].

#### 2.1.2 Test Cases (derived from §2 spec)

| Case | Covers | Input | Expected |
|------|--------|-------|----------|
| CASE-01 | Minimal project | 1 C_ProjectLine, qty=1 | 1 C_Order, FK to project |
| CASE-02 | Multi-variant | 3 lines: SH×3, DX×2, PREMIUM×1 | 6 C_Orders, correct base_bom each |
| CASE-03 | Reference class | qty=180, base_bom=SH_STD | 180 C_Orders, sequential plot indices |
| CASE-04 | Exception | CASE-03 + plot[12]→SH_CORNER | 179 SH_STD + 1 SH_CORNER at index 12 |
| CASE-05 | Infrastructure | C_Order(type=INFRA) alongside buildings | INFRA compiles, no spatial conflict |
| CASE-06 | Full §2 example | 5-line spec (180+15+1+4) | 200 C_Orders, all compile, all gates pass |
| CASE-07 | Site AABB violation | Footprint > site dims | Validation error, compile blocked |
| CASE-08 | Isolation | 3 orders, compile order[1] only | Only order[1] output, others unaffected |
| CASE-09 | Determinism | Same project compiled twice | Identical spatial digests |

#### 2.1.3 Test Framework (test-first — write BEFORE code)

| Test Class | Level | Witnesses |
|-----------|-------|-----------|
| `ProjectDropperTest` | Unit | W-PROJ-DROP-1: create C_Project + C_ProjectLine |
| | | W-PROJ-DROP-2: expand qty=180 → 180 unique C_Orders |
| | | W-PROJ-DROP-3: plot exception swaps one order only |
| | | W-PROJ-DROP-4: zero-qty rejected |
| | | W-PROJ-DROP-5: out-of-range index rejected |
| `ProjectCompileTest` | Integration | W-PROJ-COMPILE-1: 3 orders compile via standard pipeline |
| | | W-PROJ-COMPILE-2: correct element count per order |
| | | W-PROJ-COMPILE-3: orders isolated (no cross-contamination) |
| `ProjectSiteValidationTest` | Integration | W-PROJ-SITE-1: buildings within AABB pass |
| | | W-PROJ-SITE-2: buildings exceeding AABB fail |
| | | W-PROJ-SITE-3: overlapping footprints detected |
| `ProjectDeterminismTest` | System | W-PROJ-DETERM-1: identical digests on recompile |

**Gate integration:** G7-PROJECT (project-level aggregate: total = SUM per-order). G8-SITE (site AABB containment). Existing G1-G6 run per order unchanged.

**Failure criteria:**
1. R-PROJ-3 unresolvable → current C_Order_ID = C_DocType_ID pattern breaks multi-order
2. Performance wall → 10 SH_STD > 10s → need compile-once-copy-many
3. INFRA spatial model incompatible → defer INFRA, test multi-building without it
4. Output consolidation → 200 output.db files impractical → need consolidated output

#### 2.1.4 Blocking Bug: R-PROJ-3 — C_Order_ID Collision

**`BomDropper.createOrder()` (BomDropper.java:48)** uses `entry.docTypeId()` as the C_Order primary key. When 180 orders share docType `RE_SH`:
- Line 85: `DELETE FROM C_Order WHERE C_Order_ID = 'RE_SH'` wipes the previous order
- Only the last of 180 orders survives
- **Fix:** Generate unique C_Order_IDs per plot (e.g., `RE_SH_001` through `RE_SH_180`), or accept an explicit orderId parameter

This must be resolved before any multi-order work. The fix is small (parameterize orderId) but touches BomDropper (sacred file adjacent — many dependencies).

#### 2.1.5 Dependencies

| Dependency | Status | Blocking? |
|-----------|--------|-----------|
| Unique C_Order_ID generation (R-PROJ-3) | NOT implemented | **YES** |
| C_Project + C_ProjectLine schema (migration) | NOT implemented | YES |
| §1 Reference Class (Compress) | NOT implemented (Session D) | NO — expand at project level before BomDropper |
| §1 Exception ordering (full) | Partial (Replace works) | NO — plot exceptions are order-level swaps |
| §6 Order Inheritance | NOT implemented (Session E) | NO — each order carries own base_bom |
| Infrastructure BOM library | Partial (BR/RD/RL exist) | NO — defer INFRA to phase 2 |

**Implementation sequence:** Schema migration → Model classes → Fix R-PROJ-3 → ProjectDropper → Unit tests → Integration tests → Scale test → Site validation → INFRA (phase 2).

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

**What must be built:**
- `ad_site_layout` table: street × lot × terrace grid from terrain topology
- `ad_plot_type` table: STANDARD/CORNER/PREMIUM/INFRA/GREEN classification
- `ad_site_placement_rule` table: put-away rules (plot_type → building_variant)
- `SitePlacementStrategy` class: walks plots, applies rules, assigns C_Orders
- M_BOM_Line dx/dy/dz at site scale: plot spatial relationships with ABL addressing

**Recursive pattern:** Site-scale M_BOM_Line (plot offsets) → Building-scale
M_BOM_Line (room offsets) → same tack convention, different scale. The spatial
relationship lives in the BOM itself at both levels without new infrastructure.

See [MANIFESTO.md](MANIFESTO.md) §Three Concerns for the allocation model.

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

## 5. nD Dimensions — Time, Cost, Variance, Procurement

> **Status:** Future — design specification only.

The order already carries WHAT (C_OrderLine), WHERE (M_BOM_Line dx/dy/dz),
and HOW (PP_Order_Node). The remaining dimensions are columns, not systems.

### 5.1 4D Schedule — Topological Sort of BOM Tree

The BOM tree encodes construction dependency: you can't build Floor 2
before Floor 1. A parent must exist before its children can be placed.

**The construction schedule IS a topological sort of the BOM tree.**
Walk depth-first, assign sequence numbers. That's the 4D schedule.
No Gantt chart tool, no Primavera — just a tree traversal query on the
existing data.

```sql
WITH RECURSIVE bom_walk AS (
    SELECT m_bom_id, parent_id, name, 1 AS seq, 0 AS depth
    FROM m_bom WHERE parent_id IS NULL
    UNION ALL
    SELECT b.m_bom_id, b.parent_id, b.name,
           w.seq + ROW_NUMBER() OVER (ORDER BY b.line_no), w.depth + 1
    FROM m_bom_line bl
    JOIN m_bom b ON bl.child_bom_id = b.m_bom_id
    JOIN bom_walk w ON bl.m_bom_id = w.m_bom_id
)
SELECT seq, depth, name FROM bom_walk ORDER BY seq;
```

**Primavera-class features** that follow from the BOM structure:
- **Critical path:** The deepest branch of the BOM tree. Delay any node
  on it and the whole project slips
- **Parallel tasks:** Siblings in the BOM tree can be built concurrently
  (Room A and Room B on the same floor)
- **Milestone:** Any BOM node can be flagged as a milestone. Completion
  = all children compiled and gates passed
- **Duration:** Verb execution time estimates on PP_Order_Node. TILE a
  floor takes X hours. ROUTE plumbing takes Y hours. Sum up the branch

### 5.2 5D Cost — Inherent in the Data Model

Each C_OrderLine references an M_Product. M_Product has a price.
The cost of a building is `SUM(product.price × orderline.qty)`.
The cost delta of an exception-based order is
`SUM(replacement.price − original.price)` per exception line.

5D is not a feature — it's a query.

### 5.3 Selection-Based Procurement — Click to Subcontract

**Friction:** You want to send the plumbing scope to a subcontractor.
Currently you'd extract it manually.

In the Bonsai viewport or BOM Tree tab, **select a set of items** —
all plumbing, or all items on Floor 3, or all items from vendor X.
The selection is a filtered set of C_OrderLines. From that selection:

- **Total cost** — SUM of selected items (instant, it's a query)
- **Create sub-C_Order** — extract the selection as a child C_Order
  under the same C_Project
- **Assign to subcontractor** — set C_BPartner on the sub-order
- **Track variance** — planned vs actual cost per sub-order

This is **Primavera's Work Breakdown Structure (WBS) meets ERP
procurement** — but driven by clicking items in a 3D viewport, not
by manually building a WBS tree. The BOM tree IS the WBS.

### 5.4 6D Sustainability / 7D Facility Management

Columns on M_Product or M_AttributeSetInstance:
- **6D:** Embodied carbon, recyclability rating, EPD reference per product.
  SUM across the order = building carbon footprint. Exception-based orders
  show the carbon delta of a variant
- **7D:** Maintenance schedule, warranty period, replacement cost per product.
  The compiled order becomes the asset register for facility management.
  Hand it to the building owner as a queryable database, not a PDF

These are not features. They are columns that already have a home in the
iDempiere product master. Populating them is data entry, not development.

---

## 6. Order Inheritance — Composable Variants

> **Status:** Future — design specification only.

**Friction:** DX_SOLAR exists (standard duplex + solar panels). You want
DX_SOLAR_PREMIUM (solar + premium finishes). You re-specify the solar
exceptions plus the premium ones — duplication.

An order can declare a **parent order**:

```
DX_BASE                          ← 1 line (standard duplex)
  └─ DX_SOLAR (parent=DX_BASE)  ← +2 lines (solar panels + switchboard)
      └─ DX_SOLAR_PREMIUM (parent=DX_SOLAR)  ← +3 lines (premium finishes)
```

DX_SOLAR_PREMIUM carries 3 lines, not 6. The compiler resolves the
inheritance chain, applies exceptions in sequence. Conflict resolution:
**last descendant wins** at a given locator_ref.

This is object inheritance applied to construction orders. The Gang of Four
pattern is the **Decorator** — each order in the chain wraps the previous
one, adding or overriding specific slots without duplicating the base.
But you don't need to think in GoF terms. Think of it as:

- **Base order** = the default house
- **Solar upgrade** = a transparency overlay that adds panels
- **Premium upgrade** = another overlay that upgrades finishes
- **Stack the overlays** = the compiled house has all three layers

Each overlay is 2–3 lines. The library of overlays is the product catalog
of upgrades. Mix and match overlays to create variants without authoring
each combination from scratch.

**iDempiere mapping:** C_Order.Ref_Order_ID (reference to parent order).
The compiler walks the Ref_Order chain to root, collects all exception
lines, applies in sequence. Standard iDempiere FK, no schema changes.

### 6.1 Chain Walking

The compiler resolves inheritance by walking `C_Order.Ref_Order_ID` from
the current order to root, collecting exception C_OrderLines at each level.

```
resolveInheritanceChain(orderId):
  chain = []
  current = orderId
  while current != NULL:
    chain.prepend(current)           // root-first ordering
    current = C_Order[current].Ref_Order_ID
  return chain                       // [root, ..., leaf]
```

The chain is walked **root-first**. Each order's exception lines are
applied in sequence. When two orders in the chain modify the same
`locator_ref`, the deeper (closer to leaf) order wins — this is the
"last descendant wins" rule.

**Depth** is defined as distance from root. Root has depth 0. A child
of root has depth 1. Deeper = higher priority.

### 6.2 Override Rule — Last Descendant Wins

At each `locator_ref` in the exploded BOM tree, at most one exception
applies. If multiple orders in the chain target the same `locator_ref`,
the override from the deepest order in the chain takes effect.

```
Example: DX_BASE → DX_SOLAR → DX_SOLAR_PREMIUM (3-deep chain)

DX_BASE (depth 0):
  locator_ref = 'Rm_Kitchen.Light_1'   → product = LIGHT_STD

DX_SOLAR (depth 1, parent = DX_BASE):
  locator_ref = 'Rm_Roof.Panel_1'      → product = SOLAR_PANEL_400W
  locator_ref = 'Rm_Garage.Switchboard' → product = SOLAR_SWITCHBOARD

DX_SOLAR_PREMIUM (depth 2, parent = DX_SOLAR):
  locator_ref = 'Rm_Kitchen.Light_1'   → product = LIGHT_PREMIUM   ← overrides DX_BASE
  locator_ref = 'Rm_Bathroom.Tile_1'   → product = TILE_MARBLE
  locator_ref = 'Rm_Garage.Switchboard' → product = SOLAR_SWITCHBOARD_SMART  ← overrides DX_SOLAR

Resolved exception set (depth wins):
  'Rm_Kitchen.Light_1'    → LIGHT_PREMIUM           (depth 2 beats depth 0)
  'Rm_Roof.Panel_1'       → SOLAR_PANEL_400W        (depth 1, no conflict)
  'Rm_Garage.Switchboard' → SOLAR_SWITCHBOARD_SMART  (depth 2 beats depth 1)
  'Rm_Bathroom.Tile_1'    → TILE_MARBLE             (depth 2, no conflict)
```

**Algorithm:** Build a `Map<locator_ref, ExceptionLine>`. Walk chain
root-to-leaf. Each order's exceptions overwrite earlier entries. After
the walk, the map contains the resolved set. O(N) where N = total
exception lines across the chain.

### 6.3 Sibling Conflict Resolution

**The gap (GAP-SC-5):** The chain is linear (parent → child → grandchild).
But two orders at the **same depth** could both modify the same `locator_ref`
if the user creates two sibling variants from the same parent:

```
DX_BASE (depth 0)
  ├─ DX_SOLAR   (depth 1, parent = DX_BASE)
  │    locator_ref = 'Rm_Garage.Switchboard' → SOLAR_SWITCHBOARD
  │
  └─ DX_PREMIUM (depth 1, parent = DX_BASE)
       locator_ref = 'Rm_Garage.Switchboard' → PREMIUM_SWITCHBOARD
```

If a third order DX_SOLAR_PREMIUM tries to inherit from **both**
DX_SOLAR and DX_PREMIUM, the chain is no longer linear — it's a DAG.

**Resolution: Single-parent inheritance only (error-on-conflict).**

`C_Order.Ref_Order_ID` is a single FK — one parent per order. This is
the iDempiere pattern: C_Order references at most one parent order.
Diamond inheritance (inheriting from two siblings) is structurally
impossible because the FK is scalar, not a list.

The conflict scenario above cannot arise in the data model. The user
must choose one lineage:

```
DX_SOLAR_PREMIUM (parent = DX_SOLAR)
  → inherits solar + adds premium finishes     ← valid: linear chain

DX_PREMIUM_SOLAR (parent = DX_PREMIUM)
  → inherits premium + adds solar panels       ← valid: different linear chain

DX_BOTH (parent = DX_SOLAR AND DX_PREMIUM)
  → IMPOSSIBLE: Ref_Order_ID is scalar FK      ← structurally prevented
```

**iDempiere precedent:** PP_Order_BOM uses `SeqNo` for ordering within
a single parent. M_PriceList_Version uses `ValidFrom` — latest wins.
Both assume a linear resolution path, not a DAG. We follow the same
assumption.

**If sibling combination is needed:** The user authors a new order that
manually includes both sets of exceptions. This is explicit, auditable,
and avoids hidden precedence rules. Three lines (solar + premium + smart
switchboard) instead of an implicit merge of two branches.

**Validation rule:** At compile time, detect cycles in the Ref_Order_ID
chain (A → B → A). Error immediately — cycles indicate data corruption,
not a design intent. Implementation: track visited order IDs during
chain walk; if a repeat is found, throw `IllegalStateException` with
the cycle path.

### 6.4 Ordering Within a Single Chain Level

When a single order carries multiple exception lines, they are applied
in `C_OrderLine.Line` order (iDempiere's standard line numbering,
analogous to `PP_Order_BOM.SeqNo`). If two lines in the same order
target the same `locator_ref`, the higher `Line` number wins —
consistent with "last writer wins" within a document.

**Validation:** At order entry time, warn if two lines in the same
order target the same `locator_ref`. This is likely a user error
(duplicate exception), not intentional layering. The warning does not
block — it informs.

---

## 7. FOSS Ecosystem Model — Open Commons for Construction

> **Status:** Future — strategic architecture.

The compilation engine is infrastructure, not product. The value layers:

| Layer | Ownership | Analogy |
|-------|-----------|---------|
| **Engine** | FOSS commons (GPLv2, same as iDempiere) | MediaWiki software |
| **Category taxonomies** | Community-contributed, curated | Wikipedia article structure |
| **BOM libraries** | Open or proprietary — anyone can publish | Wikipedia articles / proprietary encyclopedias |
| **Component geometry** | Open (IFC imports) or proprietary (vendor models) | Wikimedia Commons / stock photos |
| **Order libraries** | End users — their curated variants | User watchlists / saved configurations |

**Why open wins:** The engine's value scales with the number of category
taxonomies and BOM libraries available. A proprietary engine with one
construction taxonomy serves one market. An open engine with community-
contributed taxonomies for construction, infrastructure, marine, industrial
plant, and interior design serves all markets simultaneously.

**Consulting follows contribution.** The domain expert who contributes a
marine taxonomy becomes the natural consultant for marine BIM compilation.
The BOM library author who publishes 500 tropical residential recipes
becomes the go-to for Southeast Asian housing projects. Revenue comes from
expertise, not from licensing the engine.

**Reverse-engineering as insurance.** Any proprietary addon built on the
open engine can be reverse-engineered from its compiled output — the
deterministic compiler guarantees reproducibility. This makes openness the
strategically dominant choice: share willingly or be reverse-engineered.
Vendors who share gain community goodwill and contribution; those who
don't gain nothing that can't be reproduced.

**Order diffing and cost deltas (§1 extensions):**
- **Order diffing:** Compare two exception-based orders — trivial SQL join,
  shows exactly which product swaps differ between two building variants
- **Cost delta:** `SUM(replacement.cost − original.cost)` per order —
  5D BIM inherent in the data model, not bolted on
- **Compliance gatekeeping:** AD_Val_Rule checks at order entry time, not
  after construction — "non-fire-rated door in exit corridor" caught before
  bomDrop runs
- **Reverse-engineering orders:** Given a compiled output.db, compute the
  minimal exception set against the nearest base BOM — onboards legacy
  buildings as reusable orders

---

## 8. Forward Friction — Anticipated Challenges

### 8.1 IFC Dialect Divergence

Every IFC exporter (Revit, ArchiCAD, Tekla, Allplan) produces subtly
different IFC. Property names vary, containment hierarchies differ,
geometry representations are inconsistent. Each new IFC import is a test
case for the extraction pipeline. The more diverse the corpus (34 buildings
and growing), the more robust the ClusterReclassifier and VerbFactorizer
become. FOSS contributors importing their own IFCs is the scaling strategy.

### 8.2 Verb Coverage per Domain

Each new domain (marine, infrastructure) may need verbs the construction
domain doesn't have (WELD, GRADE, BALLAST). The verb language must be
extensible without breaking existing compilations. New verbs are additive —
they don't modify existing verb semantics.

### 8.3 First-User Onboarding

The first person who isn't the author trying to import an IFC and compile
it will reveal every implicit assumption in the pipeline. The FOSS play
mitigates this — contributors file issues, the assumptions become explicit,
the documentation improves.

### 8.4 The 30-Second Pitch

"Construction is ERP" is a paradigm shift. The Bonsai viewport is the
familiar face for architects. The iDempiere backend is the familiar face
for ERP consultants. A 30-second demo — "click BOM Drop, see building
appear in Bonsai, swap a door, recompile" — is the killer pitch. Two
familiar faces, one unfamiliar connection between them.

### 8.5 Enterprise Scaling — ModelValidator + Federated Spatial DB

<!-- Implementing SpecsAnalysis.txt §15 — processIt() vs ModelValidator -->

Current `processIt()` dispatch is correct for batch compilation — the
compiler processes one building at a time, one discipline at a time, in
a deterministic pipeline. For enterprise deployment (multi-user, real-time
collaborative BIM editing), two companion features become necessary:

**ModelValidator alignment.** iDempiere's `ModelValidator` fires
`beforeSave`/`afterSave` hooks on every PO change — this is the pattern
for interactive, event-driven validation. When the BIM Designer supports
multi-user concurrent editing, each spatial mutation (drag wall, move
sprinkler) must fire validation rules in real-time. Align `processIt()`
dispatch with ModelValidator's event model: register validators via
ComponentFactory (OSGi pattern), fire on MBOMLine save, return
PASS/WARN/BLOCK synchronously. The batch pipeline continues to use
`processIt()` for full-building compilation; ModelValidator handles the
interactive per-element path.

**Federated Spatial DB.** The current in-memory approach handles TE's
48,428 elements, but multi-building federation (200 houses under one
C_Project) cannot hold all buildings in memory simultaneously. The
Federated Model Spatial DB addon (93% memory reduction on 93K elements,
[StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) line 238)
partitions spatial data by building × floor × discipline, loading only
the active partition. This is the same principle as iDempiere's
org-partitioned data — AD_Org scopes which records are visible in a
given context.

These two features are companions for enterprise deployment: ModelValidator
for real-time validation, Federated Spatial DB for multi-project scale.
Neither blocks the current single-building batch pipeline.

---

## 9. DiffVerb and Callout — Reactive Spatial Editing

> **Status:** Future — design specification only.

The building is compiled and visible in the Bonsai viewport. The user wants
to refine aesthetics — drag a fireplace 300mm left to centre it on the
feature wall. This gesture is not an attribute override (ASI handles those).
It is a **spatial mutation with cascading consequences**.

### 9.1 Three Layers of Instance Customisation

| Layer | What it captures | iDempiere parallel | Persistence |
|-------|-----------------|-------------------|-------------|
| **ASI** | Static attribute overrides (colour, material, finish) | M_AttributeSetInstance | Name/value pairs on C_OrderLine |
| **DiffVerb** | Spatial mutations from user gestures | PP_Order_Node (verb_type=DIFF) | Replayable operation with delta parameters |
| **Callout** | Cascading consequences of a change | AD_Rule / AD_Column.Callout | Rule rows that fire on field change |

**ASI** is sufficient for: "this door is oak instead of pine."
**ASI is insufficient for:** "move this fireplace 300mm left" — because
the move has side effects that ASI cannot express. The flue must follow.
The hearth pad must resize. The clearance to the adjacent wall must be
re-validated. The gas line must reroute.

### 9.2 DiffVerb — Captured Gesture as Replayable Operation

When the user drags an element in the viewport, the gesture produces a
**DiffVerb** — a new verb type alongside TILE, ARRAY, ROUTE, CLUSTER.

```
PP_Order_Node:
    verb_type    = 'DIFF'
    locator_ref  = 'L1.Living.FP_Mantel'

PP_Order_NodeProduct (parameters):
    delta_dx     = -300    (mm, parent-relative)
    delta_dy     = 0
    delta_dz     = 0
    source       = 'viewport_drag'   (provenance: user gesture)
```

The DiffVerb is:
- **Stored** as a PP_Order_Node row — same table as all other verbs
- **Replayable** — apply the same DiffVerb to the same base BOM and get
  the same result. Determinism is preserved
- **Composable** — multiple DiffVerbs on the same order stack additively.
  They are the spatial equivalent of exception-based ordering (§1)
- **Diffable** — the DiffVerb IS the diff. No separate diff computation
  needed. The user's gesture is already in delta form

### 9.3 Callout — Cascading Rules from Spatial Change

The DiffVerb fires. Now the **Callout chain** propagates consequences:

```
TRIGGER: C_OrderLine[FP_Mantel].dx changed by -300mm

CALLOUT CHAIN (AD_Rule rows):
├── Rule 1: FP_Flue.dx = FP_Mantel.dx
│   (flue tracks mantel — positional coupling)
│
├── Rule 2: FP_Hearth.width = FP_Mantel.width + 2 × clearance_mm
│   (hearth resizes — dimensional dependency)
│
├── Rule 3: Wall_Adjacent.clearance_check()
│   └── IF clearance < 900mm → AD_Val_Rule WARN
│   (fire code re-validation — constraint check)
│
└── Rule 4: MEP_GasLine.route_to(FP_Mantel.new_position)
    (reroute gas line — verb re-execution)
```

**iDempiere precedent:** In iDempiere, changing `M_Product_ID` on a
`C_OrderLine` triggers a Callout that updates `PriceActual`, `C_Tax_ID`,
and `Description`. The field change is the trigger; the Callout is the
rule engine that propagates consequences. Same pattern — different domain.

**AD_Rule as the Callout store:** Each Callout rule is an `AD_Rule` row:

| Column | Content |
|--------|---------|
| `EventType` | `FIELD_CHANGE` |
| `SourceTable` | `C_OrderLine` |
| `SourceColumn` | `dx` (or `dy`, `dz`, `m_product_id`) |
| `RuleType` | `POSITIONAL` / `DIMENSIONAL` / `CONSTRAINT` / `REROUTE` |
| `TargetLocator` | locator_ref of the affected element |
| `Expression` | SQL or formula: `FP_Flue.dx = :new_value` |

Rules are **declarative** — they state WHAT should happen, not HOW.
The Callout engine evaluates them in dependency order (topological sort,
same as the 4D schedule in §5.1). Circular dependencies are detected
and rejected at rule definition time.

### 9.4 Why This Completes the Editing Model

Without Callout, the user can:
- **Order** a building (C_Order + exceptions) — §1
- **Compile** it (bomDrop + verb execution) — BBC §3
- **Override** attributes (ASI) — static, no side effects

With Callout, the user can also:
- **Sculpt** the compiled result — drag, resize, reposition
- **Trust** that consequences propagate automatically
- **Replay** every gesture — the DiffVerb trail is the edit history

The Bonsai viewport becomes a **live rule-driven editor**, not just a
viewer. Every drag is a DiffVerb. Every DiffVerb fires Callouts. Every
Callout consequence is itself a DiffVerb (recorded, replayable, auditable).
The entire edit session is a sequence of DiffVerbs — like a Photoshop
Action or a Blender modifier stack.

---

## 10. AD_ChangeLog — Provenance and Audit Trail

> **Status:** Future — design specification only.

**Friction:** The BOM library grows through BOM Mining (§4 Approve).
Community contributors promote validated orders into reusable recipes.
Six months later: "where did this floor plan recipe come from? Which IFCs
contributed to it? Who approved it? What gate results did it pass?"

### 10.1 iDempiere AD_ChangeLog

iDempiere's `AD_ChangeLog` records every field change on every record:

| Column | Content |
|--------|---------|
| `AD_Table_ID` | Which table was changed |
| `Record_ID` | Which row |
| `AD_Column_ID` | Which field |
| `OldValue` | Previous value |
| `NewValue` | New value |
| `Updated` | Timestamp |
| `UpdatedBy` | User ID |
| `TrxName` | Transaction context |

This is the provenance chain. Every promoted BOM (§4), every DiffVerb
(§9), every Callout cascade, every exception-based order mutation — all
recorded with who, when, from-what, to-what.

### 10.2 Provenance Chain for Promoted BOMs

When DocAction=Approve promotes a compiled order into a reusable BOM:

```
AD_ChangeLog entries:
├── M_BOM 'FLOOR_TROPICAL_3BR' CREATED
│   UpdatedBy = 'redhuan'
│   TrxName   = 'Approve_Order_2847'
│
├── Source IFCs:
│   ├── extracted_from: 'SampleHouse.ifc' (extraction_id=14)
│   ├── extracted_from: 'Duplex.ifc' (extraction_id=7)
│   └── common_subtree_match: 87% overlap
│
├── Gate results at promotion time:
│   ├── G1_EXTRACTION: PASS (196/196 witnesses)
│   ├── G2_STRUCTURE: PASS
│   └── G5_PROVENANCE: PASS
│
└── Downstream references:
    ├── Used by: C_Order 'DX_SOLAR_v3'
    ├── Used by: C_Order 'SH_CORNER_LOT'
    └── Inherited by: M_BOM 'FLOOR_TROPICAL_4BR' (child recipe)
```

### 10.3 Trust in the Commons

The FOSS ecosystem (§7) depends on trust. A community-contributed BOM
library is only as trustworthy as its provenance. AD_ChangeLog provides:

- **Attribution:** Who contributed this recipe
- **Lineage:** Which IFC extractions fed it, which orders validated it
- **Verification:** Which gates passed at promotion time
- **Impact analysis:** Which orders depend on this BOM — if it changes,
  what needs recompilation

This is the Wikipedia edit history for BOM recipes. Every recipe has a
full audit trail. Disputes are resolved by reading the log, not by
arguing about intent.

### 10.4 DiffVerb Audit Trail

Every viewport gesture (§9) is a DiffVerb recorded in PP_Order_Node.
AD_ChangeLog records the creation of each PP_Order_Node row. Together
they provide a complete edit history:

```
Session replay:
  10:14:03  DIFF  FP_Mantel dx -300mm         (user drag)
  10:14:03  CALLOUT  FP_Flue dx -300mm        (auto: Rule 1)
  10:14:03  CALLOUT  FP_Hearth width +600mm   (auto: Rule 2)
  10:14:04  CALLOUT  WARN clearance 850mm     (auto: Rule 3)
  10:14:15  DIFF  FP_Mantel dx +50mm          (user corrects)
  10:14:15  CALLOUT  FP_Flue dx +50mm         (auto: Rule 1)
  10:14:15  CALLOUT  OK clearance 900mm       (auto: Rule 3 clears)
```

Every line is an AD_ChangeLog entry. The session is replayable.
The audit trail is the undo history, the provenance record, and the
training data for future Callout rule refinement — all in one table.

---

## 11. The 8th D — ERP as Business Intelligence

> **Status:** Future — design specification only.

BIM has accumulated "dimensions" over the years:

| D | What | Typical Tool | How it's done today |
|---|------|-------------|-------------------|
| 3D | Geometry | Revit, ArchiCAD | Modelling software |
| 4D | Time/Schedule | Primavera P6, MS Project | Manually linked to 3D |
| 5D | Cost | CostX, Sage | Manually extracted from 3D |
| 6D | Sustainability | One Click LCA | Separate tool, manual input |
| 7D | Facility Management | Archibus, FM:Systems | Separate system, manual handover |
| **8D** | **ERP / BI** | **Nothing integrated** | **Doesn't exist** |

The 8th D is the enterprise dimension — where the building sits inside
a real database with real ERP operations: procurement, accounting, HR,
subcontractor management, cash flow, variance analysis, business
intelligence. Not as a bolt-on integration, but as the native environment.

**Why 8D doesn't exist today:** IFC is a file format. You can't run
`SELECT SUM(cost) FROM wall WHERE fire_rating < 60 AND zone = 'corridor'`
against an IFC file. You export it to a spreadsheet, filter manually,
and hope you didn't miss anything. Every "D" above 3D is a separate
tool that reads geometry and tries to attach its own data. None of them
share a database. None of them can answer cross-dimensional queries.

**Why it exists here:** The BOM compiler sits on SQLite. Every element
is a row. Every row has spatial coordinates (3D), a position in the BOM
tree (4D sequence), a cost (5D), material properties (6D), maintenance
data (7D), and an ERP document context (C_Order, C_Project, C_BPartner).
Cross-dimensional queries are JOINs.

### 11.1 The $500B Question — Construction Pain as SQL

The construction industry wastes an estimated **$500-600 billion/year
in the US alone** (~30% of total spend) on problems that are fundamentally
data integration failures. Each pain point below becomes a query when
the building lives in a real database:

**Change order impact — "an offer just came in":**
```sql
-- Swap flooring material, what's the cost delta?
SELECT
    SUM(new_product.price - old_product.price) * ol.qty AS cost_delta,
    SUM(new_product.lead_time - old_product.lead_time) AS schedule_delta_days
FROM c_orderline ol
JOIN m_product old_product ON ol.m_product_id = old_product.m_product_id
CROSS JOIN m_product new_product
WHERE new_product.name = 'PORCELAIN_TILE_600'
  AND old_product.m_product_category_id = new_product.m_product_category_id
  AND ol.c_order_id = ?
  AND ol.family_ref LIKE '%FLOOR_TILE%';
```
One query. Instant. Not a 3-day exercise involving the estimator, the
scheduler, and the procurement manager.

**Work package generation:**
```sql
-- Generate mechanical work package for Level 2 North
SELECT ol.family_ref, ol.qty, p.name, p.price * ol.qty AS line_total,
       es.tack_from_x, es.tack_from_y, es.tack_from_z
FROM c_orderline ol
JOIN m_product p ON ol.m_product_id = p.m_product_id
JOIN co_empty_space_line es ON ol.c_orderline_id = es.c_orderline_id
WHERE p.m_product_category_id IN (SELECT id FROM m_product_category
                                   WHERE name IN ('ME','EL','PL'))
  AND es.tack_from_z BETWEEN 3000 AND 6000  -- Level 2 height range
  AND es.tack_from_x > 15000;               -- North zone
```
A work package is a filtered view. Not a 3-week manual exercise.

**Trade conflict detection:**
```sql
-- Which trades overlap in the same zone during the same week?
SELECT wp1.trade, wp2.trade, wp1.zone, wp1.week,
       wp1.crew_size + wp2.crew_size AS total_crew,
       z.max_crew_capacity
FROM work_package wp1
JOIN work_package wp2 ON wp1.zone = wp2.zone
  AND wp1.week = wp2.week AND wp1.trade < wp2.trade
JOIN zone z ON wp1.zone = z.zone_id
WHERE wp1.crew_size + wp2.crew_size > z.max_crew_capacity;
```
Conflict detection is a JOIN, not a 4-hour coordination meeting.

**Cash flow forecast:**
```sql
-- Monthly cash flow projection
SELECT strftime('%Y-%m', scheduled_date) AS month,
       SUM(CASE WHEN flow = 'IN' THEN amount ELSE 0 END) AS cash_in,
       SUM(CASE WHEN flow = 'OUT' THEN amount ELSE 0 END) AS cash_out,
       SUM(CASE WHEN flow = 'IN' THEN amount ELSE -amount END) AS net
FROM (
    -- Revenue: progress payments from client
    SELECT install_month AS scheduled_date, cost * pct_complete AS amount, 'IN' AS flow
    FROM bom_line_progress
    UNION ALL
    -- Cost: payments to subcontractors and suppliers
    SELECT payment_due_date, amount, 'OUT'
    FROM purchase_order_line
) GROUP BY month ORDER BY month;
```

**Quantity validation:**
```sql
-- Do extracted quantities make sense?
SELECT element_type, COUNT(*) AS count,
       SUM(volume_m3) AS total_volume,
       AVG(volume_m3) AS avg_volume,
       CASE WHEN MAX(volume_m3) > 10 * AVG(volume_m3)
            THEN 'OUTLIER' ELSE 'OK' END AS check
FROM bom_line
GROUP BY element_type
HAVING count > 1;
```

**As-built variance:**
```sql
-- What deviated from design?
SELECT bl.family_ref, bl.as_designed_product, bl.as_built_product,
       bl.as_designed_dx - bl.as_built_dx AS position_drift_mm,
       p_old.price - p_new.price AS cost_impact
FROM bom_line bl
JOIN m_product p_old ON bl.as_designed_product = p_old.m_product_id
JOIN m_product p_new ON bl.as_built_product = p_new.m_product_id
WHERE bl.as_built_status = 'DEVIATED';
```

### 11.2 Industry Cost of the Integration Gap

| Pain Point | Est. US Annual Waste | Root Cause | BOM+ERP Solution |
|---|---|---|---|
| Change orders | $128B | Design-cost-schedule disconnect | Cost delta = one query |
| Labour inefficiency | $195B | No BOM-to-labour link | Labour hours = BOM qty × rate |
| Work package errors | $50-80B | Manual scope decomposition | Work package = filtered BOM view |
| Trade coordination | $40-60B | No spatial-temporal integration | Conflict = spatial-temporal JOIN |
| Procurement mistiming | $30-50B | Schedule-procurement disconnect | PO date = install date − lead time |
| MEP clash rework | $25B+ | Geometric-only detection | Clash + code + cost = rule query |
| Quantity errors | $15-25B | Model-to-cost extraction gaps | Quantity = COUNT from BOM |
| As-built variance | $10-20B | No structured tracking | Variance = designed vs built columns |
| Compliance rework | $10-15B | Manual code checking | Compliance = AD_Val_Rule per element |
| Cash flow errors | $5-10B | Accounting-schedule disconnect | Forecast = schedule × cost aggregation |
| **Total** | **~$500-600B/yr** | **No shared database** | **Every pain point is a query** |

**The punchline:** Manufacturing solved these problems decades ago with
MRP/ERP. Construction hasn't because it never had a BOM. This project
provides the BOM. The 8th D is what happens when the BOM lives in a
real database with real ERP operations around it.

### 11.3 Federation + Bonsai = Visual BI

The Federation addon to Bonsai already shows breakdown by work packages,
equipment, and labour factors. This is the visual layer over the SQL:

- **Click a zone** in the 3D viewport → see cost, schedule, labour, trades
- **Select a set of elements** → total cost, generate sub-C_Order,
  assign to subcontractor under C_Project (§2)
- **Colour by dimension** → red = over budget, blue = ahead of schedule,
  yellow = procurement at risk
- **Time slider** → 4D construction sequence, coloured by any dimension

The viewport is the BI dashboard. The database is the warehouse.
The BOM is the star schema. No separate BI tool needed.

---

## 12. Callout Rule Library — Compliance Packs as Product

> **Status:** Future — design specification only.

The Callout rules (§9.3) are domain knowledge encoded as AD_Rule rows.
Fire code clearance, MEP routing dependencies, structural load cascades,
accessibility requirements — these are **reusable, versionable, and
jurisdiction-specific**.

### 12.1 Rule Packs by Jurisdiction

| Pack | Jurisdiction | Content | Authority |
|------|-------------|---------|-----------|
| UBBL-2024 | Malaysia | Fire rating, setbacks, parking ratios, accessibility | KPKT |
| BCA-2019 | Singapore | Fire safety, structural, env sustainability | BCA |
| NCC-2022 | Australia | Fire, structural, energy, accessibility, plumbing | ABCB |
| IBC-2021 | USA (model) | Fire, structural, egress, accessibility | ICC |
| EN-1990-series | EU | Structural Eurocodes | CEN |
| NFPA-13 | USA/intl | Sprinkler design and spacing | NFPA |
| AS/NZS-3000 | Australia/NZ | Electrical wiring rules | Standards Australia |

Each pack is a set of AD_Rule rows — importable, versioned, community-
maintained. A Malaysian project loads UBBL-2024 + NFPA-13. A Singapore
project loads BCA-2019. The engine is the same; the rules change.

### 12.2 Name-Dropping as Market Optics

The world buys on recognised names. A compliance pack labelled
**"IBC-2021 Verified"** or **"NFPA-13 Compliant"** is a headline
that resonates with:

- **Developers:** "Our designs are automatically code-checked"
- **Insurers:** "Fire code compliance is verified per element, not per drawing"
- **Regulators:** "Submissions include machine-readable compliance evidence"
- **Consultants:** Contributing a rule pack makes you the authority on
  that jurisdiction — consulting engagements follow

The rule packs are the **app store** of the FOSS ecosystem (§7). The
engine is free. The category taxonomies are community-contributed. The
rule packs are where domain experts monetise their knowledge — or
contribute it for reputation and consulting flow.

### 12.3 Rule Pack Structure

Each rule pack is a SQL import file:

```sql
-- UBBL-2024 Fire Rating Rules (excerpt)
INSERT INTO ad_rule (name, event_type, source_table, source_column,
                     rule_type, expression, description, pack_id) VALUES
('UBBL-S3.2-FireDoor',  'FIELD_CHANGE', 'c_orderline', 'm_product_id',
 'CONSTRAINT', 'product.fire_rating >= zone.required_fire_rating',
 'Fire door rating must meet zone requirement per UBBL Schedule 3.2',
 'UBBL-2024'),

('UBBL-S7.1-Corridor',  'FIELD_CHANGE', 'co_empty_space_line', 'width_mm',
 'CONSTRAINT', 'width_mm >= 1200',
 'Corridor minimum width 1200mm per UBBL Schedule 7.1',
 'UBBL-2024'),

('UBBL-S4.3-Setback',   'FIELD_CHANGE', 'c_order', 'site_aabb_width',
 'CONSTRAINT', 'site_aabb_width - building_aabb_width >= 2 * setback_mm',
 'Building setback from boundary per UBBL Schedule 4.3',
 'UBBL-2024');
```

Migration-safe: append-only, versioned by `pack_id`, never modifies
existing rules. Load a new version alongside the old one. Projects
pin to a specific pack version.

### 12.4 Beyond Compliance — Engineering Intelligence

Rule packs extend beyond regulatory compliance to **engineering best
practice**:

- **Constructability rules:** "Don't place a beam within 300mm of a
  column face without a bracket detail" — learned from field rework
- **Cost optimisation rules:** "If floor area > 200m², switch from
  solid slab to post-tensioned" — engineering economics
- **MEP coordination rules:** "Maintain 150mm clearance between hot
  water pipe and electrical conduit" — trade coordination logic
- **Prefab compatibility rules:** "If wall length > 12m, split into
  transportable panels at 3m intervals" — manufacturing constraint

These rules are the **accumulated knowledge of the industry**, currently
locked in engineers' heads, firm-specific standards documents, and
tribal knowledge passed down through mentorship. Encoding them as
AD_Rule rows makes them queryable, shareable, and versionable.

The firm that encodes 500 constructability rules into a rule pack owns
a knowledge asset that compounds. Every project that uses the pack
generates feedback (which rules fired, which were overridden via
MValRuleException). The feedback improves the rules. The rules improve
the projects. This is the flywheel.

---

## 13. Rule-Driven Discipline — Validation as Ordering

> **Status:** Specified — first implementation target.
> **Foundation:** §1.1 Add mutation + §12 Rule packs.
> **First case:** Fire Protection (FP) on DemoHouse.

### 13.1 The Problem — Compliance Is Not a Product

A building's structural elements are products the architect chose. Fire
protection is not. Nobody orders sprinklers because they want them —
they order them because the jurisdiction mandates them. The distinction:

| Element type | Origin | Ordering mechanism |
|-------------|--------|-------------------|
| Walls, floors, roof | Architect's design intent | BOM Drop (§1) |
| Upgraded finishes | Client preference | Product swap (§1.1 Replace) |
| Garage removal | Site constraint | Exception (§1.1 Remove) |
| Sprinklers, alarms, smoke detectors | Regulatory mandate | **Rule-driven Add** |
| Electrical outlets, HVAC diffusers | Building code + room function | **Rule-driven Add** |

The first three are authored. The last two are **derived from rules**.
The rules already exist in the validation database. The missing pattern
is: rules that currently only **validate** should also **propose**.

### 13.2 Three States of a Discipline Line

The architect sees a discipline (FP, ELEC, ACMV) in one of three states:

| State | What the architect sees | System behaviour |
|-------|----------------------|-----------------|
| **Absent** | No FP OrderLine on the order | Validation fires **warnings**: "UBBL By-Law 225 requires sprinklers for this configuration." The order compiles without FP — the building is structurally valid but non-compliant. |
| **Proposed** | System suggests FP OrderLine(s) based on rules | Architect reviews each proposed line. Accept → line becomes active. Reject → line is removed, warning persists. Partial accept → some sprinklers, some warnings. |
| **Accepted** | FP OrderLine is active on the order | Compiler resolves placement from `ad_space_type_mep_bom`. Validator checks placement against `ad_fp_coverage`. The discipline is compiled like any other BOM branch. |

This is not auto-populate. The architect is always in control. The system
is an advisor with opinions backed by building code — not an enforcer.

### 13.3 The Suggestion Engine — Rules Propose, Architect Disposes

When the architect clicks "Apply Rules" (or when the order reaches
`docValidate(TIMING_BEFORE_COMPLETE)`), the system:

1. **Reads order context:** `C_Order.Jurisdiction`, `C_Order.OccupancyClass`,
   building height (from BUILDING BOM AABB), total floor area (sum of
   floor BOMs)
2. **Queries trigger rules:** `ad_fp_trigger` WHERE jurisdiction matches
   AND (height > min_height OR area > min_floor_area OR storeys > min_storeys)
3. **For each triggered element type** (SPRINKLER, STANDPIPE, FIRE_ALARM,
   SMOKE_DETECTION):
   - Query `ad_space_type_mep_bom` for each room in the order
   - Compute quantity: `qty_normal` or `per_area_normal × room_area_m2`
   - Apply `ad_fp_coverage` constraints (max_spacing, min_spacing,
     max_coverage_m2)
   - Generate a **proposed C_OrderLine** with:
     - `Discipline = 'FPR'`
     - `M_Product_ID` from `ad_element_mep` (sprinkler head product)
     - `locator_ref` = room locator (placement context)
     - `Qty` = computed count
     - `status = 'PROPOSED'` (not yet accepted)
4. **Present to architect** in the BOM Tree tab or a dedicated
   "Compliance Review" panel. Each proposed line shows:
   - What rule triggered it (code reference: NFPA 13 §10.2.4)
   - What it would place (3 sprinkler heads in LIVING, ceiling grid)
   - Accept / Reject / Modify quantity

```
C_Order: "DemoHouse" (Jurisdiction=MY, OccupancyClass=R)
├── C_OrderLine #1: BOM Drop    BUILDING_SH_STD           ← architect chose
├── C_OrderLine #2: Replace     roof → FK pitched          ← architect chose
│
│   ── "Apply Rules" ──────────────────────────────────────
│
├── C_OrderLine #3: [PROPOSED]  discipline=FPR             ← system proposes
│   LIVING:     2 sprinklers (37.2m² room ÷ 18.6m²/head)
│   KITCHEN:    1 sprinkler  (fixed qty per ad_space_type_mep_bom)
│   BEDROOM1:   1 sprinkler  (12m² < 18.6m² threshold)
│   BEDROOM2:   1 sprinkler
│   BATHROOM:   1 sprinkler  (ceiling center per NFPA 13)
│   Rule: UBBL By-Law 225 + NFPA 13 Table 10.2.4.2.1
│
├── C_OrderLine #4: [PROPOSED]  discipline=ELEC            ← system proposes
│   LIVING:     4 outlets, 2 switches, 1 light
│   KITCHEN:    3 outlets (GFCI), 1 switch, 1 light
│   ...per ad_space_type_mep_bom
│   Rule: MS IEE Wiring Regulations
│
└── C_OrderLine #5: [PROPOSED]  discipline=ACMV            ← system proposes
    LIVING:     1 aircon point, 1 supply diffuser
    ...per ad_space_type_mep_bom
    Rule: MS 1525 (Energy Efficiency)
```

The architect reviews. Accepts FP and ELEC, rejects ACMV ("client wants
ceiling fans, not aircon"). The order now has 4 active OrderLines.
Compile. The validator checks FP placement against NFPA 13 spacing.
ELEC placement against wiring code. ACMV absence noted as advisory
(not blocking — architect made a conscious choice).

### 13.4 iDempiere Parallel — ModelValidator Proposes

In iDempiere, `ModelValidator.docValidate(C_Order, TIMING_BEFORE_COMPLETE)`
runs before an order is completed. It can:
- Add lines (mandatory accessories, regulatory add-ons, tax lines)
- Modify lines (recalculate prices, apply discounts)
- Block completion (missing mandatory fields, credit limit exceeded)

The suggestion engine is this pattern applied to construction:
- **Add lines:** proposed discipline OrderLines
- **Modify lines:** adjust sprinkler qty based on room area recalculation
- **Block completion:** "Cannot complete — no fire protection on 3-storey
  building in MY jurisdiction" (configurable: block or warn)

The key difference from manufacturing ERP: in manufacturing, the
ModelValidator auto-adds and the order proceeds. In construction,
the architect reviews because building design requires professional
judgement. The system is a **second pair of eyes**, not an autopilot.

### 13.5 The Validation Spectrum — From Advisory to Gating

Not all rules have equal weight. The system supports a spectrum:

| Level | Behaviour | Example | Override |
|-------|-----------|---------|----------|
| **ADVISORY** | Log only — appears in compliance report | "Consider adding smoke detectors in bedrooms" | No override needed — it's information |
| **WARNING** | Highlighted in UI, does not block compilation | "UBBL By-Law 225 recommends sprinklers for floor area > 500m²" | Architect acknowledges via `MValRuleException` |
| **MANDATORY** | Blocks `docAction=Complete` unless accepted or excepted | "UBBL By-Law 225 **requires** sprinklers for buildings > 18m" | Architect must Accept the proposed line OR create a documented exception with reason |
| **GATING** | Blocks compilation entirely | "Structural load path incomplete — cannot compile" | Cannot override — engineering constraint, not regulatory |

The level is set per rule in `ad_fp_trigger.is_mandatory` and per rule
pack. A Malaysian project under UBBL treats sprinklers for high-rise
as MANDATORY. The same project might treat bedroom smoke detectors as
ADVISORY. The architect's judgement space is between ADVISORY and
MANDATORY — not above GATING.

### 13.6 Feedback Loop — Exceptions Improve Rules

When the architect **rejects** a proposed line or creates an
`MValRuleException`, the system records:
- Which rule was overridden
- Which building / order / jurisdiction
- The architect's reason (free text or coded: "client preference",
  "alternative compliance path", "not applicable to this occupancy")

Over time, this data reveals:
- Rules that are always rejected → too aggressive, needs threshold adjustment
- Rules that are rejected only in specific occupancies → needs occupancy filter
- Rules that are never rejected → proven, promote to MANDATORY

This is the §12 flywheel applied to compliance: the rule library
self-improves through use. The same pattern as BOM Mining (§4) —
accumulated practice converges toward better rules.

### 13.7 Why This Is the First Framework Feature

Fire Protection is the ideal first case because:

1. **Data is ready.** 12 trigger rules, 4 coverage classes, 19 space types
   already in ERP.db. No new data entry needed.
2. **The rules are well-defined.** NFPA 13 is a prescriptive standard —
   spacing and coverage are formulas, not judgement calls.
3. **The validation path exists.** `PlacementValidatorImpl.validateBatch()`
   is discipline-aware and `checkClearance()` handles FP-STR clearance.
4. **The ERP pattern is proven.** iDempiere ModelValidator has been
   proposing order lines in manufacturing for 20 years.
5. **It generalises immediately.** The same `ad_space_type_mep_bom` table
   already has ELEC (LIGHT, OUTLET, SWITCH) and ACMV (AIRCON_POINT,
   SUPPLY_DIFFUSER) data. Session C of the implementation plan applies
   the same engine to all three disciplines.

Once FP works, ELEC and ACMV are configuration — same engine, different
rule pack rows. The framework is the value; FP is the proof of concept.

### 13.8 Relationship to Other Sections

| Section | Relationship |
|---------|-------------|
| §1.1 Add mutation | The mechanism — FP is an Add OrderLine |
| §5.2 5D Cost | Each proposed FP line has a cost — the compliance cost delta is instant |
| §6 Order inheritance | A "fire-rated" overlay can add FP to any base order |
| §9 DiffVerb + Callout | Moving a wall triggers FP recalculation (room area changed → sprinkler count changed) |
| §10 AD_ChangeLog | Every Accept/Reject of a proposed line is audited |
| §12 Rule packs | NFPA-13, UBBL-2024, IBC-2021 are importable rule packs that drive the suggestion engine |

---

## 14. Implementation Plan — Order Compilation Engine

> **Status:** In progress. Session A DONE (S67 `fac5e8f`), Session B DONE (S67b). Sessions C-E not started.
> **Source:** Consolidated from ACTION_ROADMAP.md Task 4 (S60-S3 reframe).

### 14.1 Triage — Blueprint Sections vs Codebase State

| § | Feature | Codebase State | Gap |
|---|---------|---------------|-----|
| **§1** Exception-based ordering | BomDropper.drop() with exceptions. locator_ref addressing. swapProduct (Replace). | **Session D DONE:** Remove + Compress implemented. Indexed exceptions (Session E) |
| **§1.1** Four mutations | Replace ✓. Add ✓. **Remove ✓** (qty=0 skip, S68b). **Compress ✓** (reference class, S68b). | All 4 mutations implemented |
| **§2** C_Project site-as-BOM | C_Order exists. No C_Project table or parent-order grouping. **R-PROJ-3:** C_Order_ID = docTypeId collision blocks multi-order (§2.1.4). | Schema + model + R-PROJ-3 fix needed. CTFL test plan in §2.1 |
| **§3** Abstract category tree | M_Product_Category exists (46 rows). BOM Selection Cascade (§3.5) works. | Already emergent — no code change needed, taxonomy is data |
| **§4** BOM mining via Approve | PromoteTest (G-10) exists. DocAction state machine works (DR→IP→AP→CO). | Promotion path proven. BOM diff (building vs building) not built |
| **§5** nD dimensions as queries | 5D: M_Product.unit_cost_rm exists. 6D/7D columns exist. 4D: BOM tree = schedule. | All dimensions are columns, not systems. Queries work today |
| **§6** Order inheritance | No Ref_Order_ID column on C_Order. | Schema addition. Exception stacking logic needed |
| **§7** FOSS ecosystem | Architecture supports it. No packaging or contribution flow. | Strategic, not code |
| **§8** Forward friction | 34 buildings compiled. IFC dialect coverage growing. | Operational, not code |
| **§9** DiffVerb + Callout | DiffVerbService records DIFF PP_Order_Node. W007 AD_Rule table (3 seed rules). CalloutEngine fires in topo-sort order. DiffVerbTest 5/5. | **Session F DONE** (S72). Viewport gesture capture pending (Bonsai addon) |
| **§10** AD_ChangeLog | ChangelogDAO fully implemented. SAVE/PLACE/MOVE/RESIZE/DELETE/PROMOTE/UNDO. | ✓ DONE |
| **§11** 8th D — ERP as BI | All data in SQLite. Queries work. Federation addon shows breakdown. | ✓ Working today — it's queries, not features |
| **§12** Callout rule library | AD_Val_Rule (63 rows). ad_fp_trigger (12). ad_code_requirement (23). InferenceEngine exists. | Rules validate. Transition to propose (§13) is the gap |
| **§13** Rule-driven discipline | ad_space_type_mep_bom (186 rows). FireProtectionAD. BOMRuleAD. MEPBomAD. PlacementValidatorImpl. | Data ready. Framework (Absent→Proposed→Accepted) not built |

### 14.2 What Already Exists (Data + Code — READY)

**C_OrderLine operations (5 production files):**
- `BomDropper.drop()` — explodes m_bom → C_Order + C_OrderLine tree
- `OrderLineWalker.walkOrder()` — walks C_OrderLine tree, fires visitor events
- `WorkOutputDAO` — CRUD on C_OrderLine (insert, list, update, swap, linkASI)
- `DesignerAPIImpl` — orchestrates bomDrop, swapProduct, listOrderLines, compile
- `PlacementLoader` — auto-detects OrderLine vs legacy BOM path

**Rule infrastructure (8 AD tables, 325+ rows):**
- `ad_fp_trigger` (12 rows) — when sprinklers/alarms are required
- `ad_fp_coverage` (4 rows) — NFPA 13 spacing/coverage per hazard class
- `ad_space_type_mep_bom` (186 rows) — per-room MEP qty/placement rules
- `ad_element_mep` (12 rows) — MEP element definitions with placement constraints
- `ad_bom_rule` (24 rows) — BOM quantity calculation (PER_AREA, PER_LUX, PER_CFM, etc.)
- `ad_building_code` (14 rows) — code registry (NFPA, UBBL, IBC, NCC)
- `ad_code_requirement` (23 rows) — detailed code rules per element × space type
- `ad_mep_profile` (3 rows) — BUDGET/STANDARD/PREMIUM qty multiplexing
- `AD_Val_Rule` (63 rows) — validation rules with discipline/jurisdiction filters

**Java readers (all production, tested):**
- `FireProtectionAD` — trigger/riser/compartment queries, jurisdiction-aware
- `MEPBomAD` — space_type→product qty lookup, profile-driven
- `BOMRuleAD` — parameterized quantity calculation engine
- `MEPAD` — element definitions, discipline-aware lookup
- `ADSession` — consolidated AD session (factory + cache)
- `PlacementValidatorImpl` — discipline-aware validation
- `InferenceEngine` — dependency-ordered rule evaluation (Kahn's topo sort)
- `MValRuleException` — architect override (accept/reject with reason)

**ERP infrastructure (all tested, GREEN):**
- DocAction state machine (DR→IP→AP→CO→VO) with MOrder transitions
- AD_ChangeLog (ChangelogDAO) — full audit trail
- PP_Order_Node — verb execution audit with parameters
- M_BOM_Line dx/dy/dz — spatial relationships (WHERE concern in BOM itself)
- M_AttributeSetInstance — per-instance customization (partial)
- Three-concern separation enforced by OrderLineInterfaceContractTest (W-LOCK-1..6)

### 14.3 Implementation Sessions

**Session 0: Fix R-PROJ-3 — C_Order_ID collision (prerequisite for §2)**
*Status: DONE (2026-03-24). BomDropper.drop() parameterized with explicit orderId. W-PROJ-ID-1 witness passes. GAP-SC-8 CLOSED.*

BomDropper.java:48 uses `entry.docTypeId()` as C_Order PK (`orderId = entry.docTypeId()`).
Line 85-86 DELETEs any existing order with that ID before INSERT. When multiple orders
share the same DocType (e.g., 180 houses all type RE_SH), each drop wipes the previous.
Only the last order survives.

- **Fix:** Parameterize `drop()` to accept an explicit `orderId`. Default to `entry.docTypeId()`
  for backward compatibility (single-building Rosetta Stone path unchanged). C_Project path
  passes unique IDs per plot (e.g., `RE_SH_001`).
- **Scope:** BomDropper.drop() signature + createOrder() + callers that pass orderId.
  Small change but BomDropper has many dependents — verify all callers.
- **Gate:** Existing Rosetta Stone tests pass unchanged (default orderId = docTypeId).
  New test: drop two SH orders with different IDs → both survive in compile DB.
- **Witness:** W-PROJ-ID-1 (two orders of same DocType coexist)

---

**Session A: Complete the Add mutation (§1.1)**
*Status: **DONE** (S67 `fac5e8f`). Discipline wiring (S66) + addDiscipline API (S67).*

- `OrderMutationService.addDiscipline()` — extracted from DesignerAPIImpl (prevents God Object growth)
- Reads `ad_space_type_mep_bom` for each ROOM in order → creates PROPOSED C_OrderLine per MEP product
- `proposal_status` column on C_OrderLine (W004 migration). Values: PROPOSED, ACCEPTED
- `Discipline` column on C_OrderLine (W003 migration, applied in initSchema)
- bom_child_id = NULL on work_output path (rule-driven, not BOM-derived)
- **Gate:** DM 5/5, SH 7/7, FK 7/7. AddDisciplineTest 4/4. BomDropTest 6/6. BomDropConfigureTest 6/6
- **Witness:** W-DM-TC5-1 extended: ELEC 15 lines / 4 rooms, FP 4 lines / 4 rooms on SH

**Session B: Validation-as-suggestion (§13)**
*Status: **DONE** (S67b). OrderLineMutation interface + 3 implementations. OrderMutationService refactored to delegate.*

- `OrderLineMutation` interface: `List<ProposedOrderLine> propose(woConn, ruleDb, orderId)`
- `FPSuggestion implements OrderLineMutation` — FP products (SPRINKLER, EMERGENCY_LIGHT), 4 lines on SH
- `ELECSuggestion implements OrderLineMutation` — ELEC products (7 types), 15 lines on SH
- `ACMVSuggestion implements OrderLineMutation` — ACMV products (3 types), 7 lines on SH
- `ProposedOrderLine` record — discipline, qty, placement rule, building code, code clause
- `RoomContext` record — shared room discovery across all implementations
- `OrderMutationService.addDiscipline()` refactored: delegates to *Suggestion via SUGGESTIONS map
- `OrderMutationService.proposeAll()` — proposes all disciplines without persisting
- Three states on proposed lines: Absent → Proposed → Accepted (§13.2) — via proposal_status column (W004)
- **Gate:** AddDisciplineTest 4/4 (backward compat). OrderLineMutationTest 8/8. SH 7/7. Full gate GREEN
- **Witness:** W-DM-FP-VAL-1 (FP suggestion fires on order with no existing FP lines — confirmed)

**Session C: Rule pack framing (§12)**
*Status: **DONE** (S67c). pack_id on 4 AD tables. Jurisdiction→pack mapping. MY=13, US=17 proposals on SH.*

- `pack_id` column on ad_space_type_mep_bom (BASE=97, UBBL-2024=34, IBC-2021=45, NFPA-13=10), ad_fp_trigger, ad_fp_coverage, ad_code_requirement
- `OrderLineMutation.packsForJurisdiction()` maps MY→[BASE,UBBL-2024,NFPA-13], US→[BASE,IBC-2021,NFPA-13]
- `propose(woConn, ruleDb, orderId, packIds)` — backward-compatible default (empty=all)
- MEPBOMQuery filters by pack_id IN clause when packIds provided
- **Gate:** RulePackTest 6/6, OrderLineMutationTest 8/8, AddDisciplineTest 4/4, SH 7/7, full gate GREEN
- **Witness:** W-RULEPACK-1 (MY=13 proposals, US=17 proposals — different codes, different counts)

**Session D: Remove + Compress mutations (§1.1, §1.2) — DONE (S68b)**

- Remove: qty=0 on C_OrderLine → BomDropper/OrderLineWalker skips branch ✓
- Compress: reference class flag → compiler instantiates N at computed offsets ✓
- locator_ref addressing: dot-separated M_Product_Category path on C_OrderLine ✓
- **Gate:** RemoveCompressTest 5/5. BomDropperOrderIdTest 1/1. SH Rosetta Stone unchanged
- **Witness:** W-EXCEPTION-1 (remove skips subtree), W-REFCLASS-1 (qty=N instantiated)

**Session E: Order inheritance (§6)**

- Add `Ref_Order_ID` to C_Order (parent order FK)
- Compiler walks inheritance chain → collects all exception lines → applies in sequence
- Conflict resolution: last descendant wins at locator_ref
- **Gate:** DX_SOLAR_PREMIUM = 3 lines on top of DX_SOLAR on top of DX_BASE
- **Witness:** W-INHERIT-1 (stacked overlays compile correctly)

### 14.4 Failure Criteria (stop-and-reassess if)

- **Session A:** FPR C_OrderLines have no m_bom_line parent to walk (bom_child_id is NULL). If so, rule-driven lines need a different walker path — not BOM walk but direct placement from ad_space_type_mep_bom spatial data.
- **Session B:** ad_space_type_mep_bom qty data is too coarse for real placement (no room AABB in the rule rows). If so, the suggestion engine needs M_BOM_Line spatial context (dx/dy/dz + AABB).
- **Session C:** ELEC/ACMV products don't exist in component_library.db (no geometry). S67 onboarded 2 ELEC products; remaining products may block Session C.
- **Session D:** locator_ref addressing conflicts with existing m_bom_line.locator_ref semantics (NORTH_WALL, CENTRE, FLOAT). If so, need separate exception_locator column.
- **Session E:** Inheritance chain conflict — two sibling ancestors both modify the same locator_ref. "Last descendant wins" is undefined for sibling branches. If so, need explicit precedence ordering on Ref_Order_ID.

### 14.5 Relationship to Other Specs

| Spec | Relationship | Action |
|------|-------------|--------|
| BBC.md §3.3-3.5 | Defines Instant Drop, BOM Drop, Selection Cascade — the foundation | Add forward reference to this §14 |
| ACTION_ROADMAP.md Task 4 | Previous home of this plan — now a pointer here | Slim to pointer |
| LAST_MILE_PROBLEM.md §Layer 2 | Generative buildings use same pipeline — no special test | Reference only |
| MANIFESTO.md | ERP patterns (DocAction, three-concern separation) | Reference only |
| DemoHouseAnalysis.md §6 | FP readiness assessment for DemoHouse | Data inventory for Session A |
| GENERATIVE_HOUSE_SRS.md §10.4 | TC-5 session plan | Superseded by this §14 |
