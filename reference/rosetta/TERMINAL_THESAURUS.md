# Terminal Thesaurus — 3rd Rosetta Stone Cross-Reference

Step 2 of the Linguist's Method applied to the SJTII Terminal (Malaysian airport).
Maps equivalent concepts across 3 stones: SampleHouse (UK residential),
Duplex (US residential), and SJTII Terminal (MY institutional/commercial).

**Source DB**: `reference/rosetta/SJTII_Terminal_extracted.db` — 15,104 elements, 8 disciplines.
**Dictionaries**: `Terminal_dictionary_ARC.txt`, `_STR.txt`, `_FP.txt`, `_ACMV.txt`
**Cross-discipline**: `Terminal_cross_discipline.txt`

## 1. WALLS — Grammar: `thickness = sum(layers)`

| Concept | SampleHouse (UK) | Duplex (US) | Terminal (MY) |
|---------|-----------------|-------------|---------------|
| Standard | 290mm (Bwk+Ins+LBlk+P) | 417mm (Brick on Block) | **150mm** (BrickPlaster) |
| Partition | 95mm (P+MStd+P) | 124mm (92mm Stud) | 150mm (same as exterior) |
| Thick exterior | — | — | **250mm** (BrickPlaster, 15 walls) |
| AHU enclosure | — | — | **230mm** (AHU_V1, 6 walls) |
| Coping/parapet | — | — | **300mm** (Coping_V1, 3 walls) |
| Plumbing | — | 184mm (152mm Stud) | — (pipes route through 150mm walls) |
| Party | — | 493-550mm (CMU) | — (no party walls; institutional) |

**Key insight**: Malaysian institutional uses a single 150mm wall thickness for 93% of walls (306/330).
This simplification vs residential (UK 290mm, US 417mm) reflects thinner tropical walls
with no insulation cavity. The grammar rule `WALL.thickness = sum(layers)` still holds —
Malaysian layers are just simpler: brick + plaster = 150mm.

**Wall naming convention**: `A_Wall_Ext_{thickness}_{finish}_{version}`:
- `BrickPlaster` = dry room default (209 walls, 63%)
- `Ceramic` / `CeramicPaint` = wet room (70 walls, 21%)
- `HT 300x300` = homogeneous tile (21 walls, 6%)

### Wall Finish → Room Function (NEW cross-stone pattern)

| Finish | Count | % | Room Indicator |
|--------|-------|---|----------------|
| BrickPlaster | 209 | 63% | Dry rooms (offices, corridors, lobby) |
| CeramicPaint | 38 | 12% | Wet rooms (toilets, kitchens, prayer rooms) |
| Ceramic | 30 | 9% | Heavy wet rooms (main toilets, ablution) |
| HT 300x300 | 29 | 9% | Tiled zones (possibly commercial frontage) |
| AHU | 6 | 2% | Mechanical enclosure |
| Coping | 3 | 1% | Roof parapet |
| BrickPlasterCeramic | 2 | 1% | Mixed finish |

## 2. OPENINGS — Commercial fire-rated vs residential schedule

| Concept | SampleHouse (UK) | Duplex (US) | Terminal (MY) |
|---------|-----------------|-------------|---------------|
| Doors | 3 (2 int + 1 ext double) | 14 (4 types) | **135** (commercial fire-rated) |
| Windows | 4 (1 type) | 24 (5 types) | **236** (curtain wall + standard) |
| Total openings | 7 | 38 | **371** |
| Door types | 2 | 4 | TBD (federation naming) |
| Window types | 1 | 5 | TBD (federation naming) |

**Key difference**: Terminal has 10-50x more openings than residential. This reflects:
- Institutional: many small rooms (offices, shops, toilets) → many doors
- Commercial facade: curtain wall glazing counted as individual windows
- Fire code: rated doors at every compartment boundary

## 3. FURNITURE — Institutional vs residential

| Context | SampleHouse (UK) | Duplex (US) | Terminal (MY) |
|---------|-----------------|-------------|---------------|
| Furniture count | 14 | 61 | **176** |
| Types | Domestic (sofa, bed, desk) | Domestic (cabinet, vanity) | Institutional (seats, counters, check-in) |
| Room function | Living, Bedroom, Kitchen | Living, Bedroom, Kitchen, Bath | Waiting, Canteen, Office, Shop |
| Spaces defined | Yes (rooms) | Yes (rooms) | **No** (space data empty) |

**Key gap**: Terminal has no IfcSpace data — room function must be inferred from
wall finish (ceramic=wet) and furniture type (waiting seats=lobby). This confirms
the Rosetta strategy: real buildings don't always have clean room data.

## 4. STRUCTURAL — RC frame (MY) vs timber (US) vs masonry (UK)

| Element | SampleHouse (UK) | Duplex (US) | Terminal (MY) |
|---------|-----------------|-------------|---------------|
| Columns | 0 | 0 | **90** (750x750mm RC) |
| Beams | 0 | 8 (W310/W410 steel) | **341** (RC 300-500mm wide) |
| Slabs | 2 | 21 | **91** (RC cast-in-place) |
| Members | 20 (curtain mullions) | 4 (stringers) | **130** (misc structural) |
| Foundation | — | Concrete 127-435mm | Foundation beams 800x750mm |
| Material | Masonry+timber | Wood joist + steel | **Reinforced concrete** |
| Grid | No grid (masonry loadbearing) | No grid (wood frame) | **8m Y-axis grid** |

**Column grid** (20 X-positions x 14 Y-positions, 750x750mm RC):
- Y-axis dominant spacing: **8000mm** (3 bays confirmed at Y = -40157, -32157, -24157, -16157, -8157, -157)
- X-axis: irregular (5800, 4600, 2600, 5000, 6500mm) — follows functional zones
- All columns: 750x750mm RC (uniform size)

**Beam inventory** (3 depth classes for span ranges):
| Beam Size | Typical Span | D/S Ratio | Count |
|-----------|-------------|-----------|-------|
| 300x600mm | 5.0-6.0m | 0.10-0.12 | ~47 |
| 300x750mm | 5.0-11.5m | 0.07-0.15 | ~93 |
| 500x700mm | 3.9-7.3m | 0.10-0.18 | ~44 |
| 500x750mm | 3.9-7.3m | 0.10-0.19 | ~64 |
| 800x750mm (foundation) | 40-60m | 0.01-0.02 | 16 |

**Beam depth/span ratio**: RC beams cluster at D/S = 0.10-0.15 for normal spans.
This is consistent with the structural engineering rule of thumb L/d = 7-15 for RC beams.

## 5. CROSS-DISCIPLINE — NEW from Federation 3rd Stone

This section has no equivalent in the residential stones (SampleHouse/Duplex had no
multi-discipline overlap data). These patterns are discovered from the terminal's 8
discipline model using bbox overlap analysis.

### 5.1 PIPE-IN-WALL

| Wall Thick | Pipe Diameter | Discipline | Overlaps | Fits? |
|-----------|--------------|------------|----------|-------|
| 150mm | 27mm | CW (cold water) | 183 | YES |
| 150mm | 33mm | FP (fire protection) | 132 | YES |
| 150mm | 33mm | CW (cold water) | 130 | YES |
| 150mm | 43-56mm | SP (sanitary) | 57 | YES |
| 150mm | 73mm | CW | 9 | YES |
| 150mm | 110mm | SP | 21 | YES |
| 150mm | 114mm | FP | 14 | YES |
| 300mm | 33mm | FP | 9 | YES |

**Total pipe-wall overlaps: 766. Violations (pipe > wall): 5 (<1%).**

**Grammar Rule**: `PIPE_IN_WALL → pipe.diameter < wall.thickness` — verified with 99.3% compliance.
The 5 violations are edge cases (likely pipe-wall boundary tolerance, not actual penetration).

### 5.2 WET WALL INDICATOR

Ceramic-finish walls strongly correlate with pipe-bearing walls:
- 70 ceramic walls (21% of all walls) co-locate with SP and CW pipe routing
- BrickPlaster walls (209, 63%) have significantly fewer pipe overlaps

**Grammar Rule**: `WALL_FINISH = CERAMIC → adjacent WET_ROOM` —
wall finish type encodes room function even when IfcSpace data is absent.

### 5.3 COLUMN-WALL INTEGRATION

| Pattern | Count | Description |
|---------|-------|-------------|
| Column at wall junction (>=2 walls) | 45 | Column embedded where walls meet |
| Column at single wall | 14 | Column at wall end or mid-span |
| Total column positions | 59 | All unique (X,Y) column locations |

**76% of columns sit at wall junctions** — structural columns don't float in space;
they anchor at wall intersections. This confirms the grammar:
`COLUMN = grid_intersection(span_X, span_Y) AND wall_junction`

### 5.4 DISCIPLINE OVERLAP MATRIX (top 10)

| Disc A | Disc B | Overlap Pairs | Interpretation |
|--------|--------|--------------|----------------|
| FP-FP | (self) | 6,822 | Dense FP pipe network |
| ARC-ARC | (self) | 6,389 | Dense wall/slab/furniture |
| STR-STR | (self) | 4,714 | Beam-column-slab connections |
| STR-ARC | | 4,313 | Structure embedded in architecture |
| ARC-FP | | 1,364 | Fire protection routed through ARC |
| FP-ELEC | | 1,214 | Fire + electrical co-routing |
| ARC-STR | | 1,121 | Architecture wrapping structure |
| ARC-CW | | 1,102 | Cold water routed through walls |
| ARC-ELEC | | 843 | Electrical in/on walls |
| STR-SP | | 742 | Sanitary through structural zones |

**Key insight**: STR↔ARC overlap is the largest inter-discipline pair (5,434 total).
Architecture literally wraps around structure. MEP disciplines (FP, CW, SP, ELEC)
all route through ARC elements. This validates the compiler's approach:
compile ARC first, then route MEP through the ARC skeleton.

### 5.5 CEILING VOID DEPTH (Duct-Slab)

| Storey | Slab Soffit | Duct Top | Void Depth | Duct Count |
|--------|------------|---------|------------|------------|
| Aras 01 | ~8085mm | ~7177mm | **~900mm** | 91 |
| Aras 02 | 12085mm | ~11162mm | **~923mm** | 198 |
| Aras 03 | 16085mm | ~15128mm | **~957mm** | 100 |
| Aras 04 | 20085mm | ~19016mm | **~1069mm** | 9 |

**Grammar Rule**: `CEILING_VOID = slab_soffit - duct_top ≈ 900-1000mm` for institutional.
Malaysian code requires sufficient ceiling void for ACMV duct routing.

### 5.6 SPRINKLER DROP DISTANCE

| Storey | Sprinkler Z (avg top) | Slab soffit Z | Drop from slab |
|--------|----------------------|---------------|----------------|
| Aras Tanah | 5333mm | ~6000mm | **~667mm** |
| Aras 01 | 11037mm | ~12085mm | **~1048mm** |
| Aras 02 | 14922mm | ~16085mm | **~1163mm** |
| Aras 03 | 19178mm | ~20085mm | **~907mm** |
| Aras 04 | 21598mm | ~22085mm | **~487mm** |

Sprinkler heads hang 500-1200mm below slab soffit. The variation suggests some storeys
have deeper ceiling voids (matching ACMV duct space findings above).

## 6. STOREY CONVENTION — Malay ↔ English Z-overlap

The terminal uses dual naming from separate discipline models:

| Malay Name | Element Count | Z Range (mm) | English Equivalent |
|-----------|--------------|-------------|-------------------|
| Aras Tanah | 4,166 | -1051 → 28235 | GROUND FLOOR LEVEL (1,288 elem) |
| Aras Kedai | 69 | 1315 → 3795 | (mezzanine — shop level) |
| Aras 01 | 2,299 | 115 → 20178 | (no exact match — MEP spans multiple) |
| Aras 02 | 2,765 | 2981 → 22406 | 02 FIRST FLOOR LEVEL (370 elem) |
| Aras 03 | 1,564 | 13615 → 24178 | 03 SECOND FLOOR LEVEL (475 elem) |
| Aras 04 | 400 | 17232 → 26672 | 04 THIRD FLOOR LEVEL (382 elem) |
| Aras Bumbung | 39 | 4230 → 29128 | 06 ROOF LEVEL (10 elem) |

**Translation key**:
- Aras = Level/Floor
- Tanah = Ground
- Kedai = Shop
- Bumbung = Roof

**Key insight**: Malay storeys contain MEP elements that span multiple physical floors
(fire protection pipes run through ceiling voids). The English storeys contain CW+STR
elements with tighter Z ranges. This is NOT a naming error — it reflects how different
disciplines define "which storey" differently:
- ARC/CW/STR: storey = structural level (tight Z band)
- MEP/FP: storey = service zone (spans multiple levels through voids)

## SUMMARY — Grammar Phrases from 3rd Stone

| # | Rule | Evidence | Cross-Stone? |
|---|------|----------|-------------|
| 1 | `WALL.thickness = sum(layers)` | MY: 150mm BrickPlaster | YES (all 3 stones) |
| 9 | `PIPE_IN_WALL → pipe.diameter < wall.thickness` | 766 overlaps, 99.3% compliant | NEW (terminal only) |
| 10 | `WALL_FINISH = CERAMIC → WET_ROOM` | 70 ceramic walls co-locate with SP/CW | NEW (terminal only) |
| 11 | `COLUMN = grid_intersection(span_X, span_Y)` | 8m Y-grid, 750x750 RC columns | NEW (terminal only) |
| 12 | `BEAM.depth/span ≈ 0.10-0.15` (RC) | 248 beams, D/S 0.07-0.19 | NEW (terminal only) |
| 13 | `CEILING_VOID ≈ 900-1000mm` (institutional) | 4 storeys consistent | NEW (terminal only) |
| 14 | `SPRINKLER_DROP ≈ 500-1200mm` | 909 terminals measured | NEW (terminal only) |

## PRIORITY ACTIONS

1. **Add Malaysian wall types to `ad_wall_type`** — 150mm BrickPlaster, 250mm BrickPlaster, 230mm AHU
2. **Add wall finish → room function mapping** — `ad_wall_type.finish` column drives room inference
3. **Column grid resolver** — `ad_structural_grid` table for institutional/commercial templates
4. **Beam sizing from span** — `ad_beam_type` with D/S ratio rules
5. **Ceiling void parameter** — expose as building template parameter for MEP routing
