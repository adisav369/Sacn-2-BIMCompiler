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

## Actual Sizings (measured 2026-03-17)

### BOM Catalog (`TE_BOM.db`)

| Table | Predicted | Actual | Notes |
|-------|-----------|--------|-------|
| m_bom (tree nodes) | ~267 | **58** | 1 BUILDING + 7 FLOOR + 50 DISCIPLINE SET |
| m_bom_line (edges) | ~700 | **48,485** | 7 assembly refs + 50 discipline refs + 48,428 LEAF |
| M_Product | ~200 | **563** | 505 catalog + 58 assembly stubs |

**Key insight:** The predicted ~700 factorized BOM lines assumed verb compression
(TILE, ROUTE, ARRAY with qty>1). The actual BOM is flat — one line per element
placement. Verb compression (TE-6/7) will reduce 48,428 LEAF lines to ~2,500.

### Compiled Output (`sjtii_terminal.db`)

| Table | Predicted | Actual | Notes |
|-------|-----------|--------|-------|
| elements_meta | ~48,428 | **48,212** | 216 IfcSlab gap (see §Coding Specs) |
| Delta (enbloc vs walkthru) | 0 | **0** | Compilation is consistent |

**Factorization (current):** 48,428 lines / 505 products = **95.9×** reuse factor.

## Implementation Phases — Actual vs Predicted

| Phase | Predicted | Actual | Status |
|-------|-----------|--------|--------|
| **TE-1** | Storey mapping | Z-centroid band assignment, 7 storeys normalised | **DONE** |
| **TE-2** | ARC decomposition | ExtractionPopulator: 51,088→48,428 active, REBAR deactivated | **DONE** |
| **TE-3** | Schema v2 + DisciplineBomBuilder | BUILDING→FLOOR→DISCIPLINE→LEAF for CO mode | **DONE** |
| **TE-4** | CO compilation pipeline | doc_base_type from YAML, DocBaseType=CO dispatch | **DONE** |
| **TE-5** | Gate wiring + storey fix | CO_TE in GATE_SCOPE, surefire property forwarding | **DONE** |
| **TE-5B** | Surefire fix + gap analysis | Output DB produced, 216 IfcSlab gap diagnosed | **DONE** |
| **TE-5C** | Fix IfcSlab gap | Unique element_ref + discipline propagation | **NEXT** |
| **TE-6** | TILE SURFACE compression | 33K plates → ~20 formulas | planned |
| **TE-7** | MEP verb integration | ROUTE + WIRE + FRAME | planned |

### Steps to Arrive at Compiled Output (guide for future IFC conversions)

The TE pipeline demonstrates the generalised IFC→BOM→compiled-output chain.
Each step is reusable for any new building — only the YAML changes.

```
Step 1: EXTRACT — Python IfcOpenShell → component_library.db
   ├── extract.py reads IFC, writes I_Element_Extraction + I_Geometry_Map
   ├── Per-element: AABB (min/max XYZ), ifc_class, orientation, material
   ├── Per-product: geometry mesh (vertices + faces) in component_geometries
   └── Output: component_library.db tables populated

Step 2: CLASSIFY — YAML declares building identity + discipline mapping
   ├── classify_te.yaml: prefix, building_type, doc_base_type, doc_sub_type
   ├── disciplines: map ifc_class → discipline code (ARC, STR, FP, ...)
   ├── storey_bands: Z-centroid ranges → storey names
   └── Output: YAML file (only human invention in the chain)

Step 3: POPULATE — Java ExtractionPopulator enriches extraction
   ├── Reads component_library.db → I_Element_Extraction
   ├── Z-centroid storey normalisation (NULL storey → band assignment)
   ├── REBAR deactivation (is_active=0 for IfcReinforcingBar)
   ├── M_Product_ID linkage: element_ref → product catalog
   └── Output: component_library.db enriched (deterministic, no invention)

Step 4: BUILD BOM — Java DisciplineBomBuilder creates BOM hierarchy
   ├── Reads extraction by storey + discipline
   ├── Creates: BUILDING BOM → FLOOR BOMs → DISCIPLINE SET BOMs
   ├── Each LEAF line: child_product_id, dx/dy/dz (parent-relative), element_ref
   ├── BomValidator: 9 checks + 2 pre-flights (abort on any failure)
   └── Output: {PREFIX}_BOM.db (m_bom + m_bom_line + M_Product)

Step 5: PREPARE COMPILE DB — Shell prepares per-building temp DB
   ├── cp {PREFIX}_BOM.db → _XX_compile.db
   ├── Apply schema_snapshot_bom.sql (adds tables: C_DocType, c_order, etc.)
   ├── Inject C_DocType row (DocBaseType, OutputDbPath, ExpectedElements)
   ├── Load DSL content from YAML-referenced .bim file
   └── Output: library/_XX_compile.db (temp, auto-cleaned)

Step 6: COMPILE — Java CompilationPipeline reads compile DB, writes output
   ├── BuildingRegistryTest drives compilation via Maven surefire
   ├── BOMWalker traverses hierarchy, PlacementCollectorVisitor collects positions
   ├── Tack convention (§3.4): each level's origin + line dx/dy/dz → world coords
   ├── BuildingWriter emits elements_meta + elements_rtree + geometries
   └── Output: DAGCompiler/lib/output/{building_type}.db

Step 7: VERIFY — Shell runs delta + Rosetta Stone gates
   ├── enbloc vs walkthru element count delta (must be 0)
   ├── Per-class breakdown, AABB centroid delta, geometry divergence
   ├── Rule 8 (world-absolute check), clash check
   └── Output: PASS/FAIL verdict log
```

**Refactoring guide:** To add a higher abstraction layer, the natural
boundary is between Step 4 (BOM) and Step 6 (compile). The BOM is the
**contract interface** — upstream changes (extraction, classification) only
affect BOM content, downstream changes (compilation, verification) only
read the BOM. A new verb (TILE, ROUTE) changes how Step 6 interprets
BOM lines, but the BOM structure (m_bom + m_bom_line) stays the same.

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

> **Spatial MRP** (see `docs/ConstructionAsERPII.txt`): Traditional MRP answers
> "what materials are needed and when?" The BIM Compiler answers "what materials
> are needed, **where**, and **how they connect**." A building is an assembled-to-
> order product — the YAML is the customer order, the classify file is the product
> configuration, and the compiler runs the production order. We're not inventing a
> new paradigm — we're adding a **spatial dimension** to iDempiere's battle-tested
> manufacturing model.
>
> **Future: M_Connection** — element-to-element connection tracking (pipe segment
> to fitting, beam to column) with port semantics and verification status. Natural
> extension of ROUTE-as-BOM-tree. Candidate for G8 gate (connection audit).

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

## Current State (2026-03-17)

- **TE-5 COMPLETE** — Gates wired, storey fix, surefire property forwarding
- **TE_BOM.db** exists — 58 BOMs, 48,428 LEAF lines, 505 products
- **Output DB produced** — enbloc == walkthru (48,212 elements, 0 delta)
- **Active elements:** 48,428 (51,088 - 2,660 REBAR deactivated)
- **Gate status:** G1 FAIL (48,212 vs 48,428 expected), G2/G3/G4/G5 PASS

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

- **CO_TE** in GATE_SCOPE of both RosettaStoneGateTest and BuildingRegistryTest
- **Terminal_Extracted.db** exists (reference, 51,088 elements)
- **G1-COUNT for TE:** -4 (51,084 vs 51,088 — 4 IfcSensor metadata-only, no spatial coords)
- **Pre-existing known debt:** IfcReinforcingBar GIC(8), no mesh shape check needed
  (Rosetta Stone sameness principle — coordinate match is the geometry guarantee)

---

## Infrastructure Corruption Precedent

The `reference/infrastructure/` directory contains 9 IFC4X3_ADD2 files (roads, bridges,
railways). When these were previously processed through the building-only extraction path,
the pipeline corrupted because:

1. `get_storey_for_element()` only recognizes `IfcBuildingStorey` — all infrastructure
   elements became `storey="Unknown"`
2. UNIQUE constraint on `(building_type, storey, ifc_class, ordinal)` broke — all
   elements in one storey caused ordinal collisions
3. Cascade: degenerate BOM → BomValidator FAIL → pipeline abort

**Guard:** Infrastructure IFCs use `IfcFacilityPart` (IfcRoadPart, IfcBridgePart,
IfcRailwayPart) instead of `IfcBuildingStorey`. The extraction layer must FAIL early
on IFC4X3 files with facility parts but no building storeys until support is implemented.

**TE is safe:** Terminal is IFC2x3 with standard `IfcBuildingStorey`. No facility parts.
The corruption risk applies only to IFC4X3 infrastructure files, not to TE.

Full analysis: [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md).

---

## Post-TE-4 BOM Model Analysis (2026-03-16)

### BOM Hierarchy: BUILDING → FLOOR → DISCIPLINE → LEAF

```
BUILDING_TE_STD (73,670 x 59,124 x 59,818 mm)
  ├── TE_FDN  [Foundation]    703 active,  5 disciplines
  ├── TE_GF   [Ground Floor] 3,513 active, 8 disciplines
  ├── TE_L01  [Level 1]      2,070 active, 6 disciplines
  ├── TE_L02  [Level 2]      2,609 active, 8 disciplines
  ├── TE_L03  [Level 3]      1,798 active, 7 disciplines
  ├── TE_L04  [Level 4]      2,307 active, 7 disciplines
  └── TE_RF   [Roof]        35,428 active, 8 disciplines
                             ------
                             48,428 LEAF lines in 50 DISCIPLINE SET BOMs
```

### Envelope Protrusion — Awnings and Canopies

ARC discipline extends **beyond** the STR structural envelope:

| Axis | STR range (m) | ARC range (m) | ARC protrusion (m) |
|------|---------------|---------------|---------------------|
| X (width) | 64.06 | 73.67 | **+9.61** |
| Y (depth) | 42.10 | 56.12 | **+14.02** |

The ARC envelope (84.6–158.3m X, -48.2–7.9m Y) extends ~10m beyond STR
(88.9–153.0m X, -41.2–0.9m Y) in both directions. This is the terminal's
awning/canopy system — IfcPlate elements on the Roof storey (33,324 plates)
that overhang the structural frame. The LPG discipline at -51.2m Y extends
furthest south (underground gas piping below the apron).

The BUILDING BOM AABB (73.67 x 59.12 x 59.82m) encompasses ALL disciplines
including protrusions. Each FLOOR AABB is computed from its own elements,
so floor W/D may exceed the BOM containment rule — this is expected for
awning/canopy overhangs.

### BomCategory Structure

58 BOMs total: 1 BUILDING + 7 FLOOR + 50 DISCIPLINE SET

| BomCategory | Count | Role |
|-------------|-------|------|
| ARC | 7 | Architectural: plates, walls, doors, windows, furniture |
| STR | 7 | Structural: columns, beams, slabs |
| FP | 7 | Fire protection: sprinklers, alarms, pipe segments |
| CW | 7 | Cold water: pipe segments, fittings, valves |
| SP | 7 | Sewerage/plumbing: pipe segments, fittings |
| ACMV | 6 | Air conditioning: air terminals, ducts (no Foundation) |
| ELEC | 6 | Electrical: light fixtures, building element proxies (no Foundation) |
| LPG | 3 | Gas: pipe fittings, segments (Foundation + GF + L1 only) |
| FN/GF/L1-L4/RF | 7 | Storey-level containers |

Not all disciplines appear on all storeys. LPG only reaches Level 1 (gas
risers stop at low levels). ACMV and ELEC skip Foundation (no MEP below grade).

### Tack I/O — Layer-to-Layer Offset Chain

```
BOMWalker tack accumulation (4 levels):

  BUILDING origin = (allMinX, allMinY, allMinZ)
  + FLOOR offset  = (floorMinX - allMinX, floorMinY - allMinY, floorMinZ - allMinZ)
  + DISCIPLINE    = (0, 0, 0)  ← logical grouping, no spatial offset
  + LEAF centroid  = (centroidX - floorMinX, centroidY - floorMinY, centroidZ - floorMinZ)
  ─────────────────
  = element centroid (world coordinates)
```

The DISCIPLINE layer is transparent to tacking — zero offset means the
walker accumulates through it without error. This is the key design insight:
discipline is a **logical** container (ERP grouping) not a **spatial** one.

### EN-BLOC vs WALK THRU

- **EN-BLOC**: reads all 48,428 LEAF lines with pre-computed dx/dy/dz.
  Each line already has parent-relative offsets. Takes each as-is when
  AABB and DocType (CO_TE) are consistent. ~25 min for 48K elements.

- **WALK THRU**: re-derives positions by tacking through the 4-level
  hierarchy. Proves the BOM structure is self-consistent. Both paths
  must produce identical output. Currently slow at 48K elements —
  verb compression (TE-6/7) will reduce to ~2,500 BOM lines.

### Dominant Element: Roof IfcPlate (33,324 = 69%)

The roof deck dominates: 33,324 IfcPlate elements under ARC/Roof.
These are modular metal deck panels forming the terminal's characteristic
undulating roof canopy. Analysis of the reference DB shows regular grid
patterns (X-step ~495mm, Y-step ~150mm) across 9 Z-bands — ideal for
TILE SURFACE verb compression to ~20 panel formulas.

### Compression Roadmap

| Phase | Verb | Elements | → BOM Lines | Ratio |
|-------|------|----------|-------------|-------|
| TE-6 | TILE SURFACE | 33,324 roof plates | ~20 | 1,666x |
| TE-7a | ROUTE | ~13K pipe/duct | ~200 | 65x |
| TE-7b | WIRE LIGHTING | ~2K fixtures | ~50 | 40x |
| TE-7c | FRAME | ~590 col/beam | ~20 | 30x |
| flat | — | ~2,123 irregular | 2,123 | 1x |
| **Total** | | **48,428** | **~2,500** | **19x** |

At the YAML/OrderLine layer: ~235 declarations → 48,428 placements = **206x**.

---

## Coding Specs — TE-5B: 216 IfcSlab Gap Fix (2026-03-17)

### Problem Statement

TE compiles 48,212 elements but BOM has 48,428 LEAF lines. The gap is exactly
**216 IfcSlab** (BOM: 700, output: 489). Every other IFC class matches exactly.
Additionally, 5 IfcSlab are lost at extraction→BOM (705 active → 700 BOM lines).

### Root Cause Chain (3 bugs, 1 design gap)

**Bug 1: element_ref = product type name, not element GUID**

`I_Element_Extraction.element_ref` stores the Revit Family:Type string (e.g.
`Floor:S_Slab_200_RC_Flat_V1`), not a per-element GUID. The Python extractor
puts `{Family}:{Type}` in this field. This means 700 IfcSlab BOM lines have
only 30 distinct element_ref values. The largest group is `jkrST_str-fo_pc_rcp:
300 x 300mm` with 236 occurrences.

SH/DX happen to work because their element_ref values are more unique (fewer
identical types). TE exposes the latent assumption that element_ref = unique ID.

**Bug 2 (REVISED): StoreyCompiler consumes element_ref by product type**

The real root cause is NOT GUID collision. StoreyCompiler generates structural
slabs (Stage 3) and marks element_refs as consumed. Since element_ref is a
product type name, `PlacementLoader.markConsumed("Floor:S_Slab_200_RC_Flat_V1")`
consumes ALL 189 elements of that type. The extracted placement path (Stage 4)
then skips all of them.

Output evidence: IfcSlab GUIDs are `SLAB_GROUND FLOOR_UNIT_*` (StoreyCompiler
pattern), not `STR_MD_SLAB_GROUND_FLOOR_*` (extracted pattern). The 489 output
slabs are StoreyCompiler-generated from computed bay dimensions, not BOM positions.

**Design Gap (FIXED): `deriveDiscipline()` ignores extraction discipline**

`PlacementCollectorVisitor.deriveDiscipline()` mapped IfcSlab → "STR" always.
Fixed in TE-5C: `disciplineStack` now carries the authoritative discipline from
the parent SET BOM's `bom_category`. `resolveDiscipline()` prefers stack over
static mapping. Falls back to `deriveDiscipline()` for SH/DX.

### Spec 1: Unique element_ref via `placement_id`

**File:** `ExtractionPopulator.java` (or Python `extract.py`)

The `element_ref` column in `I_Element_Extraction` must hold a value unique
per element placement, not per product type. Options:

| Option | Value | Uniqueness | Breaking change |
|--------|-------|------------|-----------------|
| A (recommended) | `{storey}:{ifc_class}:{ordinal}` | Unique per extraction | Low — ordinal already exists |
| B | `placement_id` (autoincrement) | Unique by definition | Medium — changes downstream joins |
| C | IFC GlobalId | Unique per IFC spec | High — requires Python extractor change |

**Recommendation:** Option A. Compose element_ref as `{storey}:{ifc_class}:{ordinal}`
at extraction time. This is deterministic (reproducible from same IFC file),
unique per element, and requires no Python extractor changes (ordinal already
computed). The `DisciplineBomBuilder` passes `e.elementRef()` through unchanged.

**Guard:** After implementing, assert `COUNT(DISTINCT element_ref) = COUNT(*)`
on `I_Element_Extraction WHERE is_active=1` in BomValidator.

### Spec 2 (REVISED): Disable StoreyCompiler slab generation for CO mode — **DONE**

**File:** `CompilationPipeline.java:234-241` — `CompileStage.shouldSkip()`

Implemented Option A. `CompileStage.shouldSkip()` returns `true` when
`ctx.entry().docBaseType().equals("CO")`, creating a minimal `BuildingSpec`
(building name, empty storeys). StoreyCompiler never runs for CO buildings —
no `markConsumed()` calls, all 48,428 BOM elements emitted via
`emitGlobalPlacementElements()`.

Result: G1-COUNT 48,428 = 48,428. IfcSlab 489 → 705. SH/DX zero regression.

### Spec 3: Propagate extraction discipline through BOM to placement — **DONE**

**File:** `PlacementCollectorVisitor.java`

Implemented in TE-5C. `disciplineStack` pushes `bom_category` from SET-level
BOMs in `onSubAssembly`, pops in `onSubAssemblyComplete`. `resolveDiscipline()`
prefers stack over `deriveDiscipline()` static mapping.

### Spec 4: Expected element count — active only

**File:** `run_RosettaStones.sh:157` — **DONE** (2026-03-17)

Changed `SELECT COUNT(*)` to include `AND is_active = 1`. Verified SH/DX
unaffected (no deactivated elements).

### Spec 5: 5 missing IfcSlab at extraction→BOM

**Diagnosis needed.** 705 active IfcSlab in extraction, 700 in BOM.
5 elements lost somewhere in DisciplineBomBuilder. Likely cause: storey
mismatch or product lookup failure. Add diagnostic logging to
DisciplineBomBuilder when an extraction element doesn't produce a BOM line.

### Implementation Order

1. Spec 4 ✅ (done — `is_active=1` in expected count)
2. Spec 3 ✅ (done — discipline stack in PlacementCollectorVisitor)
3. Spec 2 ✅ (done — `CompileStage.shouldSkip()` for CO mode, 216 gap closed)
4. Spec 1 — unique element_ref (defensive, for future WYSIWYG gates)
5. Spec 5 — diagnose 5 missing slabs at extraction→BOM (minor)

### Verification

After Spec 2: `rm TE_BOM.db && ./scripts/run_RosettaStones.sh classify_te.yaml`
- G1-COUNT: expected 48,428, actual must equal 48,428
- Delta: enbloc == walkthru (0 difference)
- Output IfcSlab GUIDs should be `STR_MD_SLAB_*` / `ARC_MD_SLAB_*` (extracted)
  not `SLAB_GROUND FLOOR_UNIT_*` (StoreyCompiler)

---

## Learning Points — TE-5 Pipeline Plumbing (2026-03-17)

### L1: Surefire forks a new JVM — CLI `-D` properties don't pass through

Maven's surefire plugin forks a separate JVM to run tests. System properties
passed on the Maven CLI (`-Dbom.db=...`) are Maven properties, NOT JVM system
properties in the forked process. You must explicitly forward them:

```xml
<configuration>
    <systemPropertyVariables>
        <bom.db>${bom.db}</bom.db>
        <bom.mode>${bom.mode}</bom.mode>
        <doc.base.type>${doc.base.type}</doc.base.type>
    </systemPropertyVariables>
</configuration>
```

**Symptom:** `System.getProperty("bom.db")` returns `null` in tests, even though
the shell script passes `-Dbom.db=...` on the Maven command line. Tests PASS
(via `assumeTrue` skip), no output DB produced, zero visible error.

**Trap:** This is invisible in SH/DX when tests are excluded from GATE_SCOPE.
The test silently skips, Maven exits 0, shell interprets as "compiled OK".

### L2: GATE_SCOPE must be kept in sync across test classes

`RosettaStoneGateTest.GATE_SCOPE` and `BuildingRegistryTest.GATE_SCOPE` are
independent `Set<String>` constants. Adding CO_TE to one doesn't add it to
the other. Both must be updated when a new building enters the pipeline.

**Trap:** BuildingRegistryTest uses `assumeTrue(GATE_SCOPE.contains(...))`.
When a docTypeId is missing from GATE_SCOPE, the test is silently skipped
(not failed). Maven reports 0 failures. The shell script sees exit code 0
and says "compiled OK" — but no test actually ran.

### L3: element_ref is NOT a unique element identifier in federated IFC

In federated models (Terminal = 9 discipline files merged), `element_ref` from
the Python extractor is `{Family}:{Type}` (Revit nomenclature). This is a
**product type name**, not a per-element GUID. Examples:

```
Metal Deck:Metal Deck           → 33,324 occurrences (all roof plates)
M_Concrete-Rectangular Beam:... → 126 occurrences (same beam type)
Floor:S_Slab_200_RC_Flat_V1     → 189 occurrences (same slab type)
```

SH/DX happened to work because their models have fewer identical-type elements,
so element_ref was effectively unique. TE's scale (51K elements, 505 products)
broke the latent assumption.

**Rule:** Never assume element_ref is unique. Use `(building_type, storey,
ifc_class, element_ref, ordinal)` as the composite key, or synthesize a unique
ID from these fields.

### L4: Silent UNIQUE constraint catch hides data loss

`ElementPersistence.writeElementMeta()` catches UNIQUE constraint violations
and returns `false`. This was correct for DX multi-unit merge (intentional
deduplication of shared perimeter walls). But in TE, the same catch silently
drops legitimate elements whose GUIDs happen to collide due to ordinal reuse.

**Rule:** The UNIQUE-catch pattern is safe only when the caller knows
duplicates are expected. For CO-mode compilation, GUID construction must
guarantee uniqueness BEFORE the INSERT, not rely on the DB to deduplicate.

### L5: `deriveDiscipline(ifcClass)` is a lossy function

The static mapping `IfcSlab → STR` discards information that the extraction
already knows. A slab in `TE_GF_ARC` is an architectural floor finish; a slab
in `TE_GF_STR` is a structural slab. Both are IfcSlab but serve different roles.
The BOM hierarchy preserves this context, but it's lost at the flat placement
stage because `deriveDiscipline` only looks at the IFC class name.

**Rule:** Discipline is a property of the BOM context (which discipline SET
the element belongs to), not a function of the IFC class alone. The walker
must carry discipline through the hierarchy, like it carries storey.

### L6: `assumeTrue` masks pipeline failures as green

JUnit 5 `assumeTrue(condition)` causes a test to be **skipped**, not failed.
Surefire counts skipped tests as non-failures. Maven exits 0. Shell scripts
that check `$?` see success. The entire pipeline can be silently non-functional
with all-green verdicts.

**Guard:** When a test is skipped unexpectedly, the script should detect
`Tests run: 1, Failures: 0, Errors: 0, Skipped: 1` and treat Skipped > 0
as a warning, or require that at least 1 test actually passed.

### L7: IfcSlab has two code paths — StoreyCompiler vs extracted placements

The compilation pipeline has **two code paths** for IfcSlab:

1. **StoreyCompiler path:** Generates slab geometry from bay/floor dimensions.
   Produces GUIDs like `SLAB_GROUND FLOOR_UNIT_1`. This is the "compiled" path
   — the compiler invents slab geometry based on storey dimensions and structural
   grid, not from extracted element positions.

2. **Extracted placement path:** `emitExtractedElements()` in BuildingWriter
   writes extracted elements with GUIDs like `STR_MD_SLAB_FOUNDATION_10`.
   Uses element positions from the BOM.

In SH/DX (RE mode), all elements go through the extracted path. In TE (CO
mode), IfcSlab may be consumed by `StoreyCompiler.applyPlacementOverrides()`
which marks element_refs as consumed via `PlacementLoader.markConsumed()`.
Subsequent extracted placements with the same element_ref are skipped at
line 959 of BuildingWriter: `if (isConsumed(...)) continue;`

**Key insight:** With non-unique element_ref (product type names), marking
one slab element_ref as consumed (e.g. `Floor:S_Slab_200_RC_Flat_V1`) skips
ALL 189 elements with that same type. This is why the gap is concentrated
in IfcSlab — StoreyCompiler produces a few slabs per storey but consumes
the element_ref for ALL slabs of that type.

**Evidence:** Output GUIDs for IfcSlab are `SLAB_GROUND FLOOR*` (StoreyCompiler),
not `STR_MD_SLAB_GROUND_FLOOR_*` (extracted path). The 489 output slabs are
StoreyCompiler-generated, not extracted placements.

**Fix direction:** Either:
- Disable StoreyCompiler slab generation for CO mode (slabs come from BOM)
- Or make `isConsumed()` match on `(element_ref, ordinal)` not just `element_ref`

### L8: The compilation pipeline is a sequence of consumers

Understanding the pipeline's internal flow is critical for debugging:

```
CompilationPipeline.run()
  ├── Stage 1: TEMPLATE (ST mode only — skipped for RE/CO)
  ├── Stage 2: LOAD — PlacementLoader reads BOM, BOMWalker collects placements
  ├── Stage 3: STOREY — StoreyCompiler generates structural slabs, bay slabs
  │   └── Marks element_refs as "consumed" (applyPlacementOverrides)
  ├── Stage 4: WRITE — BuildingWriter emits elements to output DB
  │   ├── emitCompiledElements() — from StoreyCompiler (slabs, columns, beams)
  │   └── emitExtractedElements() — from BOM placements (skips consumed refs)
  ├── Stage 5: SURFACE — surface styles from component_library.db
  ├── Stage 6: PROVER — PlacementProver verifies spatial properties
  └── Stage 7: SHADOW — cross-check against reference DB
```

The STOREY stage runs BEFORE WRITE. It generates slab/bay elements from
computed dimensions and marks element_refs as consumed. Then WRITE's
extracted path skips consumed refs. This is correct for SH/DX where
element_ref is unique — consuming `Floor_GF_01` consumes exactly one slab.
But for TE where element_ref is a product type name, consuming
`Floor:S_Slab_200_RC_Flat_V1` consumes ALL 189 slabs of that type.

**Rule for new buildings:** If a new building uses CO mode (discipline BOMs),
check whether StoreyCompiler generates structural slabs. If so, either
disable slab generation (BOM already provides slabs) or ensure element_ref
uniqueness so `isConsumed()` doesn't over-consume.

---

**Cross-references:**
[`ConstructionAsERP.md`](ConstructionAsERP.md) §11.8 |
[`BOMBasedCompilation.md`](BOMBasedCompilation.md) §2.1.5 |
[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) |
[`terminal_erd.html`](terminal_erd.html) (interactive ERD) |
[`bim_architecture_viz.html`](bim_architecture_viz.html) (3-DB architecture)
