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
| **REB** | 2,660 | ReinforcingBar(2,660) |
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

**Problem:** 34,479 of 51,092 elements have empty storey assignment.
Phase TE-1 normalises Malay/English names and assigns by Z-coordinate.

## Factorization — The Scale Reduction

| Discipline | Elements | Unique Types | Factor |
|------------|----------|--------------|--------|
| ARC | 34,724 | 519 | **67×** |
| FP | 6,867 | 1,093 | 6× |
| REB | 2,660 | 73 | **36×** |
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
| ARRAY (1D linear) | `ARRAY` | 2,660 | 5.2% | LIVE v0.9 |
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
    │   └── Level 3: REBAR_SET — reinforcing bars
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

| Table | Count | Notes |
|-------|-------|-------|
| m_bom (tree nodes) | ~267 | L0(1) + L1(6) + L2(~140) + L3(~120) |
| m_bom_line (edges) | ~700 | Factorized: qty>1 collapses repeats |
| c_orderline | ~51,092 | One per element (carries unique position) |
| M_Product (new) | ~200 | Terminal-specific types to add |
| **TE_BOM.db** | ~15-25 MB | Mostly c_orderline + placement |

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

### Verb: ARRAY (rebar — 2,660 elements, 5%)

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
  REB:
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

## Current State (2026-03-14)

- **TE registered** in `construction_manifest.yaml` (mode: EXTRACTED, 51088 elements)
- **CO_TE** exists in C_DocType (doc_sub_type=TE)
- **Terminal_Extracted.db** exists (reference, 51,088 elements)
- **TE_BOM.db** does not exist yet
- **classify_te.yaml** does not exist yet
- **G1-COUNT for TE:** -4 (51,084 vs 51,088 — 4 IfcSensor metadata-only, no spatial coords)
- **Pre-existing known debt:** IfcReinforcingBar GIC(8), no mesh shape check needed
  (Rosetta Stone sameness principle — coordinate match is the geometry guarantee)
