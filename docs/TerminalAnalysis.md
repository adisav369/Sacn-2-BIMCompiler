# Terminal Recomposition — SJTII_Terminal Forensics

*Extracted from `docs/TheRosettaStoneStrategy.txt` §TERMINAL RECOMPOSITION (2026-02-28).*
*Updated 2026-03-14 with current pipeline state.*

## Building Identity

| Property | Value |
|----------|-------|
| Stone | 3 of 3 (largest) |
| Name | SJTII_Terminal (Sultan Johor Terminal II) |
| IFC version | IFC2x3 (federated from 9 discipline models) |
| Country | Malaysia |
| Type | Airport terminal, 4+ storeys, institutional |
| Elements | 51,088 (51,092 - 4 IfcSensor metadata-only) |
| Disciplines | 9 (ARC, STR, REB, FP, ACMV, CW, ELEC, SP, LPG) |
| DocSubType | TE |
| C_DocType_ID | CO_TE |
| Reference DB | `DAGCompiler/lib/input/Terminal_Extracted.db` |

## Why Terminal Is Different From SH/DX

SH/DX are residential. Their BOM hierarchy is:
```
UNIT → FLOOR → ROOM → SET → ITEM
```

Terminal is institutional. There are no "rooms" in the residential sense.
Instead there are ZONES: departure hall, check-in counters, boarding gates,
retail areas, mechanical rooms, roof structure. The BOM hierarchy must be:
```
BUILDING → STOREY → DISCIPLINE → ASSEMBLY → COMPONENT
```

Also: SH/DX had 1-2 IFC source files. Terminal was federated from 9
discipline-specific models. The discipline boundaries are authoritative —
they came from separate consultant firms.

## Element Inventory by Discipline

| Discipline | Count | Dominant Classes |
|------------|-------|-----------------|
| **ARC** | 34,724 | IfcPlate(33,324) Wall(330) Window(236) Furniture(176) |
| **FP** | 6,867 | PipeFitting(3,146) PipeSegment(2,672) FireSuppression(909) Alarm(80) |
| **REB** | 2,660 | ReinforcingBar(2,660) — **DEFERRED** (IfcOpenShell Python generates dynamically) |
| **ACMV** | 1,621 | DuctFitting(713) DuctSegment(568) AirTerminal(289) Proxy(51) |
| **CW** | 1,431 | PipeFitting(638) PipeSegment(619) FlowTerminal(106) Valve(57) |
| **STR** | 1,429 | Slab(614) Beam(432) Member(312) Column(68) Wall(3) |
| **ELEC** | 1,172 | LightFixture(814) Proxy(339) Appliance(19) |
| **SP** | 979 | PipeSegment(455) PipeFitting(372) FlowTerminal(150) Valve(2) |
| **LPG** | 209 | PipeFitting(87) PipeSegment(75) Valve(47) |
| **Total** | **51,088** | |

ARC dominates at 68% — almost entirely IfcPlate roof tiles (33,324 = 65% of all elements).

## Storey Structure

| Storey | Elements | Notes |
|--------|----------|-------|
| ROOF | ~34,479 | Mostly IfcPlate metal deck tiles |
| Ground (Aras Tanah) | ~4,166 | Ground floor |
| Level 1 (Aras 01) | ~2,299 | |
| Level 2 (Aras 02) | ~2,765 | |
| Level 3 (Aras 03) | ~1,564 | |
| Level 4 (Aras 04) | ~400 | |
| Ceiling levels | ~673 | Per-storey ceilings |
| Aras Kedai/Jalan/Bumbung | ~153 | Shop/Road/Roof levels |

**RESOLVED (TE-1):** Z-centroid band assignment normalised all storeys.
See §Current State for actual distribution (48,428 active after REBAR exclusion).

## Factorization — The Scale Reduction

| Discipline | Elements | Unique Types | Factor |
|------------|----------|--------------|--------|
| ARC | 34,724 | 519 | **67×** |
| FP | 6,867 | 1,093 | 6× |
| REB | 2,660 | 73 | **36×** (DEFERRED) |
| ACMV | 1,621 | 543 | 3× |
| STR | 1,429 | 555 | 3× |
| ELEC | 1,172 | 401 | 3× |
| CW | 1,431 | 683 | 2× |
| SP | 979 | 566 | 2× |
| **Total** | **51,088** | **4,433** | **12×** |

"Unique Types" = distinct dimensional signatures (dx × dy × dz rounded to mm).
Each unique type becomes one M_Product row. Each BOM line references a type
with `qty > 1` where instances repeat — the factored form.

## The Roof Deck — TILE Verb

33,324 IfcPlate elements tile the roof surface in a regular grid.

Measured from Terminal_Extracted.db:
- Y-step: 150mm (plate depth, edge-to-edge — 3,774 of 3,819 pairs exact)
- X-step: 495mm (plate width, edge-to-edge — 35 of 43 pairs exact)

9 Z-bands of roof panels (Z = 18m to 28m). Each band is a horizontal surface
at a different height. Within each band, plates tile in a regular 2D grid:

```
TILE SURFACE "ROOF_DECK_Z19" WITH "PLATE_500x150x106"
    PANEL "west"    ORIGIN (92.49, -42.16, 19.0)  GRID 15 x 294  STEP (495mm, 150mm)
    PANEL "central" ORIGIN (122.63, -42.16, 19.0) GRID 14 x 174  STEP (495mm, 150mm)
    PANEL "east"    ORIGIN (141.74, -42.16, 19.0) GRID 15 x 34   STEP (495mm, 150mm)
END-TILE
```

~20 TILE statements describe the entire roof (33,324 elements from ~20 formulas).

## Formula Coverage — BIM COBOL Verb Patterns

| Formula Pattern | BIM COBOL Verb | Elements | % | Status |
|----------------|---------------|----------|---|--------|
| TILE (2D grid) | `TILE SURFACE` | 33,324 | 65.2% | LIVE v0.9 |
| PATH (1D route) | `ROUTE SPRINKLERS` | 9,345 | 18.3% | LIVE |
| ARRAY (1D linear) | `ARRAY` | 2,660 | 5.2% | DEFERRED (rebar) |
| GRID (ceiling) | `WIRE LIGHTING / ROUTE` | 2,012 | 3.9% | LIVE |
| PERIMETER | `ENCLOSE / SPAN` | 1,038 | 2.0% | designed |
| GRID (structural) | `FRAME` | 590 | 1.2% | designed |
| **Formula total** | | **48,969** | **95.8%** | **74.4% LIVE** |
| Irregular (flat) | manual placement | 2,123 | 4.2% | |

95.8% of elements follow formula patterns. Only 2,123 elements (furniture,
proxies, miscellaneous) need flat per-element placement.

## Predicted BOM Hierarchy (5 Levels)

```
Level 0: BUILDING_TE_STD (BUILDING, doc_sub_type=TE)
├── Level 1: TERMINAL_TE_GF (FLOOR) — Ground, ~4166 elements
├── Level 1: TERMINAL_TE_L01 (FLOOR) — Level 1, ~2299 elements
├── Level 1: TERMINAL_TE_L02 (FLOOR) — Level 2, ~2765 elements
├── Level 1: TERMINAL_TE_L03 (FLOOR) — Level 3, ~1564 elements
├── Level 1: TERMINAL_TE_L04 (FLOOR) — Level 4, ~400 elements
├── Level 1: TERMINAL_TE_ROOF (FLOOR) — Roof, ~34479 elements
│   ├── Level 2: DECK_BAY_Z19_G01..G20 (SET) — 7356 plates
│   ├── Level 2: DECK_BAY_Z20_G01..G20 (SET) — 7318 plates
│   ├── Level 2: DECK_BAY_Z21_G01..G18 (SET) — 6680 plates
│   ├── Level 2: DECK_BAY_Z22_G01..G18 (SET) — 6846 plates
│   ├── Level 2: DECK_BAY_Z18/Z23/Z25..Z27 (SET) — 5124 plates
│   └── Level 2: ROOF_MEP_TE (SET) — MEP at roof height
└── Each FLOOR contains:
    ├── Level 2: ARC_TE_LXX (DISCIPLINE)
    │   ├── Level 3: WALL_SET — ~80 walls/storey
    │   ├── Level 3: OPENING_SET — doors+windows hosted on walls
    │   ├── Level 3: FURNITURE_SET — zone furniture
    │   └── Level 3: MISC_ARC — coverings, railings, stairs
    ├── Level 2: STR_TE_LXX (DISCIPLINE)
    │   ├── Level 3: FRAME — beams + columns + members
    │   ├── Level 3: SLAB_SET — structural slabs
    │   └── Level 3: REBAR_SET — reinforcing bars (DEFERRED)
    ├── Level 2: FP_TE_LXX (DISCIPLINE)
    │   ├── Level 3: FP_PIPE_RUN — pipe segments + fittings
    │   └── Level 3: SPRINKLER_SET — fire suppression terminals
    ├── Level 2: ACMV_TE_LXX (DISCIPLINE)
    │   ├── Level 3: DUCT_RUN — duct segments + fittings
    │   └── Level 3: AIR_TERM_SET — air terminals
    ├── Level 2: ELEC_TE_LXX (DISCIPLINE)
    │   ├── Level 3: LIGHTING_SET — light fixtures (qty factorized)
    │   └── Level 3: EQUIP — proxies + appliances
    ├── Level 2: CW_TE_LXX / SP_TE_LXX / LPG_TE_LXX
    │   └── (same pattern: pipe runs + fixture sets)
    └── ...
```

## Predicted Sizings

### BOM Catalog (BOMCategory — shared DB)

`TE_BOM.db` keeps **abstract BOM models**, DocBaseTypes, and DocSubTypes.
This is the catalog — not tied to C_Order. It will eventually sit in a common
shared DB alongside SH/DX BOM definitions (BOMCategory).

| Table | Count | Notes |
|-------|-------|-------|
| m_bom (tree nodes) | ~267 | L0(1) + L1(6) + L2(~140) + L3(~120) |
| m_bom_line (edges) | ~700 | Factorized: qty>1 collapses repeats |
| M_Product (new) | ~200 | Terminal-specific types to add |

### Compiled Output (output/Terminal.db)

C_OrderLine is generated at compilation time. High-level VERBs (TILE, ROUTE,
ARRAY) mean orderline count is **not** 1:1 with elements — a single TILE verb
covers thousands of placements. Each C_OrderLine references:
- **M_Product** — LOD element leaf from `component_library.db`
- **M_AttributeSet** — carries Qty/Draw metrics (template)
- **M_AttributeSetInstance** — per-orderline instance, created at compile time

| Table | Count | Notes |
|-------|-------|-------|
| c_orderline | TBD | Verb-compressed, far fewer than 51K |

**Factorization: ~700 BOM lines instead of ~51,300 = 73× reduction.**

## Implementation Phases

| Phase | What | Elements | Dependency |
|-------|------|----------|------------|
| **TE-1** | Storey mapping & spatial normalisation | all 51K | None |
| **TE-2** | ARC envelope decomposition | 34,724 | TE-1 |
| **TE-3** | Structural decomposition | 4,089 | TE-1 |
| **TE-4** | MEP system decomposition (5 sub-disciplines) | 12,279 | TE-1 |
| **TE-5** | M_Product catalog expansion | ~200 products | Parallel |
| **TE-6** | BOM tree assembly (factorized) | all | TE-2,3,4,5 |
| **TE-7** | c_orderline generation | ~51,092 rows | TE-6 |
| **TE-8** | Verification & regression (3-stone gate) | all | TE-7 |

## What SH/DX Taught Us (Foundation Advantage)

1. **Placement determinism works:** extract coords → compile. Terminal already
   has 100% positional match from Phase DE-4.
2. **BOM pattern works:** m_bom hierarchy + m_bom_line with child_product_id.
   Extending to 9 disciplines is data, not code.
3. **M_Product catalog is extensible:** Terminal needs ~200 more products.
   Same table, same pattern.
4. **Discipline dispatch works:** ElementPersistence emits all disciplines.
   Terminal's 9 disciplines already compile correctly.
5. **IFCtoBOM pipeline is abstract:** `classify_te.yaml` follows the same
   YAML-driven pattern as `classify_sh.yaml` and `classify_dx.yaml`.
6. **G5-PROVENANCE is abstract:** 7 checks run per building via DynamicTest.
   No Terminal-specific test code needed.

The challenge is **scale and variety**, not architecture.

## New Verbs & BOM Mechanisms Needed

Terminal introduces patterns that SH/DX didn't need. Each pattern maps to a
BIM COBOL verb and a YAML section that carries user intent.

### Verb: TILE SURFACE (roof deck — 33,324 elements, 65%)

**Mechanism:** 2D grid expansion. One BOM line with `qty=N` expands to N
placements at computed grid positions (origin + i×stepX + j×stepY).

**YAML intent:**
```yaml
roof_deck:
  panels:
    - name: DECK_Z19_WEST
      product: PLATE_500x150x106
      origin_m: [92.49, -42.16, 19.0]
      grid: [15, 294]          # columns × rows
      step_mm: [495, 150]      # X-step, Y-step
    - name: DECK_Z19_CENTRAL
      product: PLATE_500x150x106
      origin_m: [122.63, -42.16, 19.0]
      grid: [14, 174]
      step_mm: [495, 150]
```

**BOM mechanism:** `m_bom_line.qty = grid[0] * grid[1]`. Walker expands qty
to instances, each getting position from grid formula. No 33K rows in BOM.

### Verb: ROUTE (MEP piping — 9,345 elements, 18%)

**Mechanism:** 1D path following. Pipe segments + fittings along a routed path.
Each run = origin, direction, segment lengths, fitting types at turns.

**YAML intent:**
```yaml
mep_systems:
  fire_protection:
    storey: GF
    runs:
      - name: FP_MAIN_GF_01
        segments: [PipeSegment_50mm, PipeFitting_Elbow_50mm, ...]
        path_nodes_m: [[10.0, 5.0, 3.2], [10.0, 15.0, 3.2], [20.0, 15.0, 3.2]]
    sprinklers:
      - name: SPRINKLER_SET_GF
        product: FireSuppressionTerminal
        spacing_mm: 3000
        ceiling_offset_mm: 50
```

### Verb: ARRAY (rebar — 2,660 elements, 5%) — DEFERRED

> **Deferred:** IfcOpenShell repo Federation feature has a working Python script
> that generates rebar dynamically. Will revisit later. Truth comparison test
> should either exclude rebar elements or remove them from input/extracted DB.

**Mechanism:** 1D linear repetition. One bar type at regular spacing along a host.

**YAML intent:**
```yaml
reinforcing:
  storey: GF
  arrays:
    - name: REBAR_SLAB_GF
      product: ReinforcingBar_12mm
      host: SLAB_GF_01
      spacing_mm: 150
      cover_mm: 25
      direction: Y
```

### Verb: WIRE LIGHTING (electrical — 814 elements)

**Mechanism:** 2D ceiling grid. Lights at regular spacing on a ceiling plane.

**YAML intent:**
```yaml
electrical:
  storey: GF
  lighting:
    - name: LIGHTING_GF_MAIN
      product: LightFixture_600x600
      zone_m: [0, 0, 50, 30]   # minX, minY, maxX, maxY
      spacing_mm: [3000, 3000]
      height_m: 3.5
```

### Verb: FRAME (structural grid — 590 elements)

**Mechanism:** Structural bay grid. Columns at grid intersections, beams spanning.

**YAML intent:**
```yaml
structural:
  storey: GF
  frame:
    - name: FRAME_GF
      column: Column_W250
      beam: Beam_W310x60
      grid_m:
        x: [0, 6, 12, 18, 24, 30]
        y: [0, 8, 16]
      height_m: 4.0
```

### BOM Mechanism: qty Expansion

The key new mechanism is `m_bom_line.qty > 1`. SH/DX have qty=1 (one line,
one element). Terminal needs qty=N (one line, N elements at computed positions).

```java
// BOMWalker expansion
for (MBOMLine line : children) {
    int qty = line.getQty();  // 1 for SH/DX, N for TE
    for (int i = 0; i < qty; i++) {
        visitor.visitLeaf(line, i);  // instance index
    }
}
```

Position computation per instance depends on the verb:
- TILE: `origin + (i % cols) * stepX + (i / cols) * stepY`
- ARRAY: `origin + i * spacing * direction`
- ROUTE: segment-by-segment path accumulation
- FRAME: grid intersection lookup

### BOM Mechanism: Discipline Layer

Terminal adds Level 2 = DISCIPLINE between FLOOR and ASSEMBLY:
```
BUILDING → STOREY → DISCIPLINE → ASSEMBLY → LEAF
```

This requires `bom_category` on m_bom to carry discipline identity (ARC, STR,
FP, ACMV, CW, ELEC, SP, LPG, REB). The walker doesn't need discipline-specific
code — it's just another tree level. The YAML `disciplines:` section maps IFC
classes to discipline categories:

```yaml
disciplines:
  ARC:
    classes: [IfcWall, IfcSlab, IfcDoor, IfcWindow, IfcFurniture, IfcRoof,
              IfcPlate, IfcCovering, IfcRailing, IfcStairFlight]
  STR:
    classes: [IfcColumn, IfcBeam, IfcMember]
  REB:  # DEFERRED — IfcOpenShell Python generates dynamically
    classes: [IfcReinforcingBar]
  FP:
    classes: [IfcFireSuppressionTerminal, IfcAlarm, IfcSensor]
    system_type: [FireProtection]
  ACMV:
    classes: [IfcAirTerminal]
    system_type: [HVAC, AirConditioning]
  ELEC:
    classes: [IfcLightFixture, IfcElectricAppliance]
  CW:
    system_type: [ColdWater, DomesticWater]
  SP:
    system_type: [SanitaryPlumbing, Drainage]
  LPG:
    system_type: [Gas, LPG]
```

### YAML as User Intent

The `classify_te.yaml` carries all user intent for Terminal — the same pattern
as `classify_sh.yaml` and `classify_dx.yaml`. The Java pipeline reads YAML,
never hardcodes building-specific logic. Adding a new Rosetta Stone =
writing a new YAML, not new Java code.

## Verb Roadmap — What Terminal Still Needs

ROUTE is a verb family (same walker, different AD tables and products):

| Verb | Status | Discipline | Elements | AD Table |
|------|--------|-----------|----------|----------|
| `TILE SURFACE` | LIVE | ARC (roof) | 33,324 | — |
| `ROUTE SPRINKLERS` | LIVE | FP | 6,867 | ad_fp_coverage |
| `WIRE LIGHTING` | LIVE | ELEC | 1,172 | ad_space_type_mep |
| `ROUTE DUCTS` | PLANNED | ACMV | 1,621 | ad_acmv_sizing |
| `ROUTE PIPES` | PLANNED | CW/SP/LPG | 2,619 | ad_space_type_mep |
| `FRAME` | DESIGNED | STR | 1,429 | — |
| `ENCLOSE` | DESIGNED | ARC (walls) | ~1,038 | — |
| `DISTRIBUTE` | NEEDED | ARC (furniture) | ~2,123 | — |
| `ARRAY` | DEFERRED | REB | 2,660 | BS 8110 / EC2 |

**ROUTE DUCTS** and **ROUTE PIPES** are variants of ROUTE SPRINKLERS — same
path-following walker, different M_Product leaves and AD regulation tables.
Implementation cost: parameter mapping + AD table creation, not new verb code.

**FRAME** is structural bay grid placement. Columns at grid intersections
(BIM_Component, identical), beams spanning between columns (BIM_Slab,
IsInstance=1 if spans vary). Reads structural grid from YAML.

**ENCLOSE** is wall perimeter placement. Follows a 2D closed path, inserts
wall segments (BIM_Wall, IsInstance=1 — length varies) and openings at
specified positions. Needed for ARC walls (~330) + openings (~236 windows,
~176 doors).

**DISTRIBUTE** is irregular zone placement for elements that don't follow
formula patterns — furniture, equipment, proxies (~2,123 elements, 4.2%).
These get flat per-element BOM lines (qty=1 each).

## ERP Model Architecture — Terminal as Third Stone

> **Interactive ERD:** `docs/terminal_erd.html` — 5-tab visualization with
> entity relationships, BOM hierarchy, verb→ERP mapping, M_BomCategory scoping,
> and ROUTE-as-BOM tree with M_AttributeSetInstance.

Terminal is the first building to stress the full iDempiere ERP model. SH/DX
used BIM_Component (IsInstanceAttribute=0 — every element identical). Terminal
forces M_AttributeSet/Instance into active service and reveals the natural
correspondence between BIM construction hierarchy and ERP document flow.

### DocBaseType/DocSubType — Real Semantic Work

DocBaseType determines the **hierarchy shape** (not just a label):

| DocBaseType | Hierarchy | L2 Axis | Compilation Path |
|------------|-----------|---------|-----------------|
| RE (Residential) | BUILDING → FLOOR → **ROOM** → SET → ITEM | Room type (LI, KT, BD) | EN-BLOC (singularity) |
| CO (Commercial) | BUILDING → FLOOR → **DISCIPLINE** → ASSEMBLY → COMPONENT | Discipline (ARC, FP, STR) | WALK THRU (discipline-driven) |

The RE path expects `floor_rooms` in YAML (Living, Kitchen, Bedroom) and walks
rooms to find furniture sets. The CO path expects `disciplines` and never looks
for rooms. Forcing Terminal through the RE path would require fake "rooms" for
discipline zones — that's technical debt avoided.

DocSubType (SH/DX/TE) carries identity for the Prime Rule three-key match.
When a second commercial building arrives (mall, factory), it will be CO with
a different DocSubType. The hierarchy shape stays FLOOR→DISCIPLINE→ASSEMBLY.

### M_BomCategory — Dual Axis, Scoped by doc_type/doc_sub_type

M_BomCategory already has `doc_type` (DocBaseType) and `doc_sub_type` columns.
These columns on the category row itself determine which building types can use
that category. Room categories have `doc_type='RE'`, discipline categories will
have `doc_type='CO'`, and shared categories (storeys, structural) have `doc_type=NULL`.

| Category Type | Codes | BOM Level | doc_type | doc_sub_type |
|--------------|-------|-----------|----------|-------------|
| Storey | GF, L1, L2, L3, L4, RF, FN | Level 1 (FLOOR) | NULL (shared) | NULL |
| Room | LI, KT, BD, BT, DN, FR | Level 2 (RE only) | RE | NULL |
| Discipline | ARC, STR, FP, ACMV, ELEC, CW, SP, LPG | Level 2 (CO only) | CO | NULL |
| Assembly | (verb-specific groupings) | Level 3 | NULL (shared) | NULL |
| Template | ST-SH, ST-DX | Template root | RE | ST |

Room and discipline codes operate at **different BOM levels** and never compete.
Storeys are shared across RE and CO — always at Level 1. The Level 2 axis
changes from room-type to discipline-type based on DocBaseType. No new tables
needed; M_BomCategory holds both sets, scoped by the doc_type column.

### M_AttributeSet/Instance — Per-Verb Usage

SH/DX: zero elements needed instance attributes. Terminal changes that:

| Verb | AttributeSet | IsInstance | Reason |
|------|-------------|------------|--------|
| TILE SURFACE | BIM_Component | 0 | All 33K roof plates **identical** — position varies, not dimensions |
| ROUTE | **BIM_Pipe / BIM_Conduit** | **1** | Each pipe segment has **different length** |
| WIRE LIGHTING | BIM_Component | 0 | All fixtures identical |
| FRAME (columns) | BIM_Component | 0 | All columns identical per grid |
| FRAME (beams) | BIM_Slab | 1 | Beam spans may vary by bay |

M_AttributeSetInstance is needed for **ROUTE-family verbs** (~9,345 FP/CW/SP/LPG
pipe elements with varying lengths). TILE/ARRAY/WIRE produce identical instances —
the formula handles position, not the attribute set.

### TILE — Pattern as Verb Parameter, Not AttributeSet

TILE is BOMQty — the M_Product leaf spreads over an AABB with its orientation.
The pattern (grid formula) lives on PP_Order_NodeProduct, not M_AttributeSet:

```
C_OrderLine (WHAT):   M_Product = ROOF_DECK_PANEL_SET, qty = 4,410
PP_Order_Node (HOW):  Verb = TILE SURFACE
  PP_Order_NodeProduct: origin, grid_cols=15, grid_rows=294, step_x=495, step_y=150
CO_EmptySpaceLine (WHERE): AABB = 7,425 × 44,100 mm (the filled envelope)
```

Changing the grid (16×294 instead of 15×294) changes only PP_Order_NodeProduct.
The same PLATE_500x150x106 product appears in different TILE patterns across
different roof bays. Clean separation: verb owns the formula, BOM owns the qty.

### ROUTE — Segments as BOM Tree + M_AttributeSetInstance

A ROUTE is not a flat list — **it's a BOM tree**. Each segment is a BOM line
with instance attributes (varying length). Fittings are fixed-geometry components.
Branches are sub-BOMs:

```
FP_MAIN_GF_01 (BOM, bom_category: FP)
├── SEGMENT_01 (M_Product: PIPE_CW_50MM)
│   └── M_AttributeSetInstance: {length_mm: 3200}    ← BIM_Pipe, IsInstance=1
├── FITTING_01 (M_Product: ELBOW_90_50MM)
│   └── (no instance — BIM_Component, fixed geometry)
├── SEGMENT_02 (M_Product: PIPE_CW_50MM)
│   └── M_AttributeSetInstance: {length_mm: 4800}
├── TEE_01 (M_Product: TEE_50x25MM)
│   └── branches to:
│       └── BRANCH_RUN_01 (sub-BOM)
│           ├── SEGMENT_B1 (PIPE_CW_25MM, length=1200mm)
│           ├── SPRINKLER_01 (SPRINKLER_UPRIGHT_K80)
│           ├── SEGMENT_B2 (PIPE_CW_25MM, length=4600mm)
│           └── SPRINKLER_02 (SPRINKLER_UPRIGHT_K80)
└── SEGMENT_03 (M_Product: PIPE_CW_50MM)
    └── M_AttributeSetInstance: {length_mm: 2100}
```

This mirrors iDempiere's configurable product model: a shirt has size/color as
M_AttributeSet variants. A pipe segment has length as M_AttributeSet variant.
The BOM tree says "this run needs: 3 segments + 1 elbow + 1 tee + 1 branch."
The instances say "segment 1 is 3200mm, segment 2 is 4800mm."

The leaf M_Product set is small: pipe sizes (25mm, 50mm, 75mm), elbows, tees,
reducers, sprinkler heads, valves. The ROUTE verb assembles them into run-specific
BOM trees with per-segment instance attributes.

### Val_Rule — Regulations as Domain AD Tables

ROUTE verbs must obey building regulations (UBBL, NFPA 13, MS 1910). The
question: how to capture these constraints? iDempiere's AD_Val_Rule uses SQL
WHERE fragments. BIM needs domain-specific AD tables instead — they're queryable,
YAML-declarable, and compose with verb compliance checking.

| Regulation | AD Table | Example Constraint |
|-----------|----------|-------------------|
| Sprinkler spacing | `ad_fp_coverage` | `max_spacing_mm <= 4600 WHERE hazard='ORDINARY'` |
| Pipe sizing for flow | `ad_fp_coverage` | `diameter_mm >= 50 WHERE flow_lpm > 200` |
| Max branch length | `ad_fp_coverage` | `branch_length_mm <= 12000` |
| Receptacle count/area | `ad_space_type_mep` | `receptacle_count >= area_sqm / 10` |
| Duct sizing per ACH | `ad_acmv_sizing` | `duct_area_mm2 >= cfm / velocity` |
| Routing method | `ad_fp_coverage` | `routing_method IN ('TREE','LOOP','GRID')` |

Each verb reads its AD regulation table to determine sizing, spacing, and method.
The verb output (BOM tree) is provably compliant. The Rosetta Stone gate can
verify compliance as a future G7 check (regulation audit).

**Routing method** is a strategy selection on the AD table:
- **TREE** — main → branches → heads (most common)
- **LOOP** — ring main with branches (redundancy)
- **GRID** — parallel mains with cross-connections (large areas)

Same leaf products (pipes, fittings, heads), different BOM tree structure.
The method column on `ad_fp_coverage` determines which ROUTE variant runs.

**YAML intent for regulations:**
```yaml
fire_protection:
  hazard_class: ORDINARY
  coverage_area_sqm: 12.1        # UBBL Table 5.1
  max_spacing_mm: 4600
  min_pipe_diameter_mm: 25
  routing_method: TREE
```

### C_Order/C_OrderLine — Three-Way Separation

The Terminal C_Order in iDempiere terms:

```
C_Order (header):
  C_DocType_ID: CO_TE
  Description: SJTII Airport Terminal

C_OrderLine (tab — one per storey-discipline BOM):
  Line 10: FLOOR_TE_FDN     qty=1     ← Foundation
  Line 20: FLOOR_TE_GF      qty=1     ← Ground Floor
    Line 20.10: ARC_TE_GF   qty=1     ← Architecture
    Line 20.20: STR_TE_GF   qty=1     ← Structure
    Line 20.30: FP_TE_GF    qty=1     ← Fire Protection
      → PP_Order_Node: ROUTE SPRINKLERS "FP_MAIN_GF_01"
        path_nodes, pipe_product, branch_spacing...
    Line 20.40: ACMV_TE_GF  qty=1
    Line 20.50: ELEC_TE_GF  qty=1
    Line 20.60: CW_TE_GF    qty=1
    Line 20.70: SP_TE_GF    qty=1
    Line 20.80: LPG_TE_GF   qty=1
  Line 70: FLOOR_TE_RF      qty=1     ← Roof
    → PP_Order_Node: TILE SURFACE (grid formula per bay)
```

The three-way separation governs the entire architecture:

| Concern | ERP Table | What It Carries |
|---------|-----------|----------------|
| **WHAT** to build | C_OrderLine | Which M_Product/M_BOM, qty |
| **WHERE** it goes | CO_EmptySpaceLine | Origin, AABB, orientation |
| **HOW** to build | PP_Order_Node | Verb parameters (grid, path, method) |

The 7-storey × 8-discipline grid produces ~40-50 C_OrderLines — a normal
iDempiere sales order size. The user sees storeys as order lines, disciplines
as sub-lines, and verbs as manufacturing instructions. The YAML is the order
form; the compiler generates the transactional records.

### Full BOM Tree With ERP Mapping

```
L0: BUILDING_TE_STD (BUILDING, doc_base_type=CO, doc_sub_type=TE)
    C_Order = CO_TE
    ├─ L1: FLOOR_TE_GF (FLOOR, bom_category=GF)
    │  C_OrderLine #20
    │  ├─ L2: ARC_TE_GF (DISCIPLINE, bom_category=ARC)
    │  │  C_OrderLine #20.10
    │  │  └─ L3: [flat placement — walls, doors, windows, furniture]
    │  ├─ L2: STR_TE_GF (DISCIPLINE, bom_category=STR)
    │  │  C_OrderLine #20.20
    │  │  └─ L3: FRAME verb → columns at grid, beams spanning
    │  ├─ L2: FP_TE_GF (DISCIPLINE, bom_category=FP)
    │  │  C_OrderLine #20.30
    │  │  PP_Order_Node: ROUTE SPRINKLERS
    │  │  Val_Rule: ad_fp_coverage (spacing, sizing, method)
    │  │  └─ L3: BOM tree of runs/branches/heads
    │  │     M_AttributeSetInstance per segment (varying lengths)
    │  ├─ L2: ACMV_TE_GF (DISCIPLINE, bom_category=ACMV)
    │  │  PP_Order_Node: ROUTE DUCTS
    │  │  Val_Rule: ad_acmv_sizing (ACH, duct sizing)
    │  │  └─ L3: duct runs + air terminals
    │  ├─ L2: ELEC_TE_GF (DISCIPLINE, bom_category=ELEC)
    │  │  PP_Order_Node: WIRE LIGHTING
    │  │  Val_Rule: ad_space_type_mep (receptacle count)
    │  │  └─ L3: ceiling grid + circuits
    │  └─ L2: CW/SP/LPG_TE_GF
    │     PP_Order_Node: ROUTE (per system)
    │     └─ L3: pipe runs + terminals
    ├─ L1: FLOOR_TE_L01 ... FLOOR_TE_L04
    │  (same discipline structure per storey)
    └─ L1: FLOOR_TE_RF (FLOOR, bom_category=RF)
       C_OrderLine #70
       ├─ L2: ARC_TE_RF (DISCIPLINE, bom_category=ARC)
       │  PP_Order_Node: TILE SURFACE (per bay)
       │  └─ L3: 33K panels from ~20 TILE formulas
       │     BOMQty = grid_cols × grid_rows per formula
       └─ L2: [other disciplines at roof level]
```

## Current State (2026-03-14)

- **TE-1 COMPLETE** — Storey normalisation applied (Z-centroid bands)
- **classify_te.yaml** created — 7 storeys, 8 active disciplines
- **REBAR excluded** — 2,660 elements set `is_active=0` (IfcOpenShell Python)
- **Active elements:** 48,428 (51,088 - 2,660 REBAR)
- **ExtractionReader** — `is_active=1` filter added to `readByStorey()`

**Storey Distribution (active elements):**

| Storey | Count | Disciplines |
|--------|-------|-------------|
| Foundation | 703 | 6 |
| Ground Floor | 3,513 | 8 |
| Level 1 | 2,070 | 8 |
| Level 2 | 2,609 | 7 |
| Level 3 | 1,798 | 7 |
| Level 4 | 2,307 | 7 |
| Roof | 35,428 | 7 |
| **Total** | **48,428** | **8** |

- **CO_TE** exists in C_DocType (doc_sub_type=TE)
- **Terminal_Extracted.db** exists (reference, 51,088 elements)
- **TE_BOM.db** does not exist yet
- **G1-COUNT for TE:** -4 (51,084 vs 51,088 — 4 IfcSensor metadata-only, no spatial coords)
- **Pre-existing known debt:** IfcReinforcingBar GIC(8), no mesh shape check needed
  (Rosetta Stone sameness principle — coordinate match is the geometry guarantee)

---

**Cross-references:**
[`ConstructionAsERP.md`](ConstructionAsERP.md) §11.8 |
[`BOMBasedCompilation.md`](BOMBasedCompilation.md) §2.1.5 |
[`terminal_erd.html`](terminal_erd.html) (interactive ERD) |
[`bim_architecture_viz.html`](bim_architecture_viz.html) (3-DB architecture)
