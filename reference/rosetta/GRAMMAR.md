# Rosetta Grammar — Formal Rules from First Principles

Step 3 of the Linguist's Method. These are mathematical truths derived
from 3 Rosetta Stones: SampleHouse (UK residential), Duplex (US residential),
and SJTII Terminal (MY institutional). Rules 1-8 from residential stones;
Rules 9-14 from cross-discipline analysis of the terminal 3rd stone.

## Verified Grammar Rules

### Rule 1: WALL.thickness = Σ(layers) — VERIFIED ACROSS 3 STONES
```
Wall thickness is never a magic number.
It equals the sum of its material layers, stored in ad_wall_type.layers.

UK exterior:  102Bwk + 75Ins + 100LBlk + 12P = 289mm ≈ 290mm ✓
UK partition: 12P + 70MStd + 12P = 94mm ≈ 95mm ✓
US exterior:  Brick + Block = 417mm ✓
US partition: 92mm Stud = 124mm ✓
US furring:   152mm Stud = 152mm ✓  |  38mm Stud = 54mm ✓
US plumbing:  152mm Stud = 184mm ✓
US party:     CMU = 493mm / 550mm ✓
MY standard:  Brick + Plaster = 150mm ✓ (306/330 walls, 93%)
MY thick ext: 250mm BrickPlaster ✓ (15 walls)
MY AHU:       230mm ✓ (6 walls — mechanical enclosure)

3 dialects, same rule. No insulation cavity in tropical climate → simpler layers.

Resolver: WallTypeResolver.resolveThickness(context, a, b, profile, fallback)
Source:   ad_wall_type.total_mm, ad_wall_type.layers
```

### Rule 2: OPENING.thin = frame_depth (NOT mesh depth)
```
Opening bbox thin dimension = family frame depth, not library mesh panel depth.

UK ext door:     frame = 199mm  (mesh was 150mm) ✓
UK int door:     frame = 178mm  (mesh was 150mm) ✓
UK window:       frame = 353mm  (mesh was 150mm) ✓
US int door:     frame = 174mm  (mesh was 150mm) ✓
US entry door:   frame = 467mm  (mesh was 150mm) ✓
US glass door:   frame = 467mm  (mesh was 150mm) ✓
US window:       frame = 417mm  (mesh was 150mm) = wall thickness ✓
US skylight:     frame = 178mm  ✓

Resolver: OpeningBomAD.getFamily(familyId).depthMm()
Source:   ad_opening_family.depth_mm
Override: OpeningWriter.writeLibraryDoor/Window overrides mesh depth with family depth
```

### Rule 3: OPENING.area = schedule(W, H)
```
Opening width and height come from the family's schedule, overridden by DSL.

Priority chain: DSL explicit > profile-specific default > generic default > family default

UK door 1810x2110:  from ad_opening_family UK_EXT_DBL_DOOR ✓
UK door 880x2150:   from ad_opening_family UK_INT_DOOR_810 ✓
UK window 1860x1210: from ad_opening_family UK_WINDOW_1810 ✓
US door 914x2108:   from ad_opening_family US_INT_DOOR_762 ✓
US door 1016x2108:  from ad_opening_family US_INT_DOOR_864 ✓
US window 819x759:  from ad_opening_family US_CASEMENT_819 ✓
US window 4835x2420: from ad_opening_family US_FIXED_4835 ✓

Resolver: OpeningBomAD.getDefaults(spaceType, profile)
Source:   ad_space_type_opening + ad_opening_family
```

### Rule 4: FURNITURE = catalog(profile, room_type)
```
Furniture is entirely profile-dependent. No universal sizes.
Selection: room → slot → BOM recipe → component_definitions

UK Living: sofa(2290x950), armchair(1430x1400), coffee_table(1200x550),
           dining_table(2000x1000) + 6 dining_chairs, piano(1370x600)
UK Bedroom: bed_queen(2007x1800), desk(1563x819)
US Living: 2 sofas(1830x813), coffee_table_rect(1830x915), coffee_table_cube(610x610)
US Kitchen: 8 base_cabinets(1000x860), counters(1000-4000x625), 4 upper_cabinets(1000x600)
US Bedroom: bed + 2 tall_cabinets(2000x800)
US Bathroom: vanity_cabinet(820x650)

Resolver: FurnitureWorker (slot → BOM → component)
Source:   ad_room_slot + ad_bom_child + component_definitions
```

### Rule 5: IfcPlate ≡ IfcWall (semantic equivalence)
```
Compiler writes walls as IfcPlate (cladding panels).
Reference IFCs use IfcWall.
For spatial comparison, these are identical — same bone structure.

Similarly: IfcFurniture ≡ IfcFurnishingElement
```

### Rule 6: SLAB = structural + finish
```
Slabs have two independent layers. Both are profile-dependent.

UK ground:  470mm structural (65Scr+80Ins+100Blk+75PC)
US ground:  127mm structural (concrete)
UK upper:   165mm structural (simple)
US upper:   305mm structural (wood joist+subfloor) + 13mm ceramic / 19mm wood finish

Compiler currently generates 1 slab per storey (structural only).
Reference has multiple zoned slabs (structural + finish per zone).
```

## Partially Verified Rules

### Rule 7: STRUCTURAL = template_grid — PARTIALLY VERIFIED (3rd stone)
```
Structural frame comes from building template, not room types.

UK: curtain wall mullions (30mm) — no US/MY equivalent
US: steel beams (W310/W410) — no UK/MY equivalent
MY: RC columns 750x750mm on 8m Y-grid + RC beams 300-500mm wide ✓

3rd stone confirms hypothesis: structural system is template-level (RC frame vs
timber frame vs masonry), grid is building-level (8m for institutional, none for
residential). Room types don't determine structural elements.
```

### Rule 8: OPENING.count = DSL_intent (not BOM_auto)
```
Compiler overcounts doors/windows (auto-generates from BOM defaults per room).
Reference only has architect-placed openings.
Hypothesis: BOM should provide defaults only when DSL doesn't specify.
PARTIALLY VERIFIED — DSL explicit > BOM default works, but pruning needed.
```

## Cross-Discipline Rules (from 3rd Stone — Terminal)

These rules derive from bbox overlap analysis across 8 disciplines in the
SJTII Terminal. They have no equivalent in the residential stones (which
are single-discipline ARC+MEP models).

### Rule 9: PIPE_IN_WALL → pipe.diameter < wall.thickness
```
Pipes route through walls only when they fit.
Verified: 766 pipe-wall overlaps in terminal, 99.3% compliant (5 edge cases).

Wall 150mm: pipes 27-114mm (CW, FP, SP) — all fit ✓
Wall 300mm: pipes 33mm (FP) — fits with margin ✓

Pipe discipline distribution in walls:
  CW (cold water):     ~350 overlaps — concealed supply/waste
  FP (fire protection): ~270 overlaps — concealed sprinkler mains
  SP (sanitary plumbing): ~130 overlaps — concealed waste pipes

Grammar: any pipe routed through a wall must have diameter < wall thickness.
This constrains wall thickness selection for wet rooms — a 150mm wall can
accept pipes up to ~110mm; larger pipes need thicker walls or exposed routing.
```

### Rule 10: WALL_FINISH = CERAMIC → adjacent WET_ROOM
```
Wall finish type encodes room function, even without IfcSpace data.

Terminal evidence (330 walls):
  BrickPlaster:     209 (63%) — dry rooms (offices, corridors, lobby)
  Ceramic/CeramicPaint: 70 (21%) — wet rooms (toilets, ablution, kitchens)
  HT 300x300:       29 (9%)  — tiled zones (commercial, prayer rooms)

Naming convention: A_Wall_Ext_{thickness}_{finish}_{version}
The finish suffix is a reliable wet-room indicator across the building.

Grammar: ceramic wall finish → room requires waterproofing → WET_ROOM.
This enables room-function inference when explicit space data is missing.
```

### Rule 11: COLUMN = grid_intersection(span_X, span_Y)
```
Structural columns sit at grid intersections, integrated with walls.

Terminal evidence (90 columns, all 750x750mm RC):
  Y-axis grid: 8000mm spacing (dominant — 3+ bays confirmed)
  X-axis grid: irregular (5800, 4600, 2600mm — follows functional zones)
  76% of columns at wall junctions (45/59 positions touch ≥2 walls)

Grammar: columns are NOT freestanding. They occur at:
  1. Grid intersection points (span_X × span_Y)
  2. Wall junction points (where ≥2 walls meet)
These two constraints usually coincide — walls follow the structural grid.
```

### Rule 12: BEAM.depth ∝ span.length (RC institutional)
```
Beam depth scales with span length. Depth/span ratio clusters at 0.10-0.15.

Terminal evidence (248 RC beams, excluding foundation):
  300x600mm beams: span 5.0-6.0m → D/S = 0.10-0.12
  300x750mm beams: span 5.0-11.5m → D/S = 0.07-0.15
  500x700mm beams: span 3.9-7.3m → D/S = 0.10-0.18
  500x750mm beams: span 3.9-7.3m → D/S = 0.10-0.19
  800x750mm foundation: span 40-60m → D/S = 0.01-0.02 (ground beams, different rule)

Engineering rule of thumb: RC beam L/d = 7-15 → D/S = 0.07-0.14.
Terminal data confirms this with slight conservatism (up to D/S = 0.19).

Grammar: beam depth = span × 0.10 to 0.15 for RC institutional.
Not yet verified for steel (Duplex) or timber (SampleHouse).
```

### Rule 13: CEILING_VOID ≈ 900-1000mm (institutional)
```
Gap between slab soffit and top of ACMV ducts defines ceiling void.

Terminal evidence (4 storeys with ACMV ducts):
  Aras 01: void ~900mm (91 ducts)
  Aras 02: void ~923mm (198 ducts)
  Aras 03: void ~957mm (100 ducts)
  Aras 04: void ~1069mm (9 ducts)

Grammar: institutional buildings need ~1m ceiling void for ACMV.
Residential buildings (SampleHouse, Duplex) have no ACMV data for comparison.
This is a building-type-dependent parameter, not a universal constant.
```

### Rule 14: SPRINKLER_DROP ≈ 500-1200mm below slab
```
Fire suppression terminals hang below slab soffit at consistent drop distance.

Terminal evidence (909 sprinkler heads across 6 storeys):
  Drop range: 500-1200mm
  Average: ~900mm (varies by storey and ceiling void depth)

Grammar: sprinkler head Z = slab_soffit_Z - drop_distance.
Drop distance depends on ceiling void depth and plenum configuration.
```

## Score Impact Log

All scores use `--discipline ARC` for convergence (Phase 119D lesson).

| Phase | SampleHouse ARC X-ray | Duplex ARC X-ray | Terminal ARC X-ray | Notes |
|-------|-----------------------|------------------|--------------------| ------|
| 118C baseline | 2% | 5% | — | Initial Rosetta pairs |
| 119A walls | 8% blended | 8% blended | — | Wall thickness from ad_wall_type |
| 119D depth | 17% ARC | 27% ARC | — | Frame depth override + ARC discipline filter |
| 119E openings | 17% ARC | 27% ARC | — | Opening count pruning |
| **120 Thesaurus** | **17%** (6/35) | **26%** (49/183) | **2%** (85/3660) | Thesaurus→AD alignment + 3rd pair |

### Phase 120 Detail (ARC-only)

| Pair | Wall Match | Opening Match | Furniture | X-ray Near | Overall |
|------|-----------|---------------|-----------|------------|---------|
| SampleHouse | 5/5 (100%) | 5/7 (71%) | 1/14 (7%) | 6/35 (17%) | 17/64 (26%) |
| Duplex | 48/57 (84%) | 15/38 (39%) | 22/61 (36%) | 49/183 (26%) | 134/360 (37%) |
| Terminal | 306/333 (91%) | 52/371 (14%) | 0/0 (—) | 85/3660 (2%) | 445/5071 (8%) |

**Terminal wall 91%**: Malaysian_Institutional 150mm BrickPlaster rule matches 306/333 reference walls.
Proves Grammar Rule 1 across 3 stones (UK 290mm, US 417mm, MY 150mm).

**Duplex window improvement**: US_FIXED_2800 now matches 4/4 bedroom windows (was 0).
US_FIXED_750 matches 2/4 kitchen windows (was 0). Net X-ray near -2 due to casement
removal, but exact quality improved.

### Architectural Insight: DSL as Catalog Selector (Phase 121 direction)

The Terminal DSL has 37 manually-defined rooms across 4 storeys. This violates the
catalog-selector principle — the DSL should reference a BUILDING TYPE, not individual
rooms. Phase 121 priority: `ad_building_template` → `ad_floor_template` → room
expansion. Target: `BUILDING type:TERMINAL_4F` generates all rooms from metadata.
