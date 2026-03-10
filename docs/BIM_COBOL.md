# BIM COBOL — The Construction Programming Language

**Version:** 1.0
**Date:** 2026-03-08
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Status:** ACTIVE — **57 verbs implemented, 183 witnesses (179 PASS / 4 RED pre-existing).** Full layered composition stack L0→L1→L2→L3→L4. F5 integration script exercises 30 verbs across all 5 layers in a single ScriptRunner pass (36 verb lines, 0 failures). 22 verbs need dedicated harness (output.db path, XLSX, component_library.db context). Phase H2: 5 verb wrappers replace all raw SQL on protected tables. T16 tamper rule enforces zero regressions.
**Module:** `BIM_COBOL/` (root-level Maven sibling of DAGCompiler, TopologyMaker)
**Depends on:** BIM_Designer.md (Compiled Construction v0.8), TopologyMaker/docs/TOPOLOGY_MAKER.md (Synthetic Stone §18-19), TheRosettaStoneStrategy.txt (Terminal formula coverage — shared concern)
**Supplements:** METADATA_DRIVEN_ARCHITECTURE.md, ConstructionAsERP.md, PREFAB_ARCHITECTURE.md, ADHistory.md (PP_Order_Node lineage)

---

## 1. The COBOL Analogy

In 1959, COBOL solved a fundamental problem: business logic was trapped in assembler. Payroll calculations, inventory management, and general ledger operations were expressed as machine instructions that only specialists could read. COBOL lifted business operations to a level where the *domain expert* — the accountant, the operations manager — could read the program and verify that it matched the business rule.

```cobol
ADD TAX TO SUBTOTAL GIVING TOTAL.
MOVE TOTAL TO INVOICE-AMOUNT.
IF INVOICE-AMOUNT > CREDIT-LIMIT
    PERFORM CREDIT-HOLD-PROCEDURE.
```

The accountant cannot write assembler. But the accountant can read this and say: "Yes, that is our invoice rule." COBOL did not replace assembler — it compiled *down to* assembler. The business language was a higher abstraction that generated the lower one.

**Construction has the same problem.** Building logic is currently trapped in IFC geometry authoring. To say "put sprinklers in the departure hall at 3-metre spacing", a professional must:

1. Open Revit or Bonsai
2. Manually create each IfcPipeSegment, IfcPipeFitting, IfcFireSuppressionTerminal
3. Route them along the ceiling mesh, calculating clearance by hand
4. Check against UBBL/fire code spacing rules manually
5. Repeat for every room, every storey, every building

This is the assembler-level problem. The domain expert — the architect, the fire engineer, the building inspector — knows the *rule* ("sprinklers at 3m spacing, 150mm below ceiling, per MS 1910"). But expressing that rule requires geometry-level operations that only a CAD specialist can perform.

**BIM COBOL is the COBOL of construction.** It is a high-level, domain-specific language where construction intent compiles down to IFC geometry + procurement BOM + compliance witnesses. The architect writes in construction concepts. The compiler emits assembler-level IFC.

---

## 2. What Already Exists — The Proto-Language

The BIM Intent Compiler already has a working DSL with 28+ keywords. This is the embryonic form of BIM COBOL — a Level 1 language that already compiles to IFC output.

### 2.1 Current DSL Vocabulary

```
STRUCTURAL DECLARATIONS
  BUILDING "name" type:T profile:"P" { ... }     Root container
  GRID { axes: A,B,C / 1,2,3; spacing: ... }     Structural grid
  STOREY "name" level:N height:Hm { ... }         Floor definition
  ROOF pitch:Xdeg overhang:Ymm                    Roof specification

SPATIAL DECLARATIONS
  LIVING "name" bounds:A1-B3 { ... }              Room by grid cell
  BEDROOM "name" adjacent:other { ... }           Room by constraint
  CORRIDOR "name" bounds:B1-C2 { ... }            Circulation space
  TOILET_BLOCK "name" bounds:D1-E2 { ... }        Wet area
  OFFICE "name" bounds:E1-F2 { ... }              Workspace
  PORCH "name" bounds:A1-B1 { ... }               Transition space

OPENING DECLARATIONS
  WINDOW north                                    Window on wall face
  WINDOW type:W1 size:1800x1000 wall:west         Typed window
  DOOR south to:living                            Door with connection
  DOOR south type:D_EXT_DBL size:900x2100         Typed door

VERTICAL CIRCULATION
  STAIR "name" at:F3 width:1.5m to:"First Floor"  Stair connecting storeys
  LANDING "name" at:F3 size:3x3m from:"stair_g"   Stair landing
  ELEVATOR "name" type:PASSENGER car:1100x1400     Lift specification
  SHAFT "name" at:D1 size:1.5x1.5m type:ELECTRICAL  MEP riser

MULTI-UNIT
  UNIT "A" type:DUPLEX entry:DIRECT               Unit from catalog
  SHARED { ... }                                   Common areas

CONSTRAINTS
  adjacent: room1, room2                           Must touch
  not_adjacent: room3                              Must not touch
  exterior: north, east                            Exterior wall faces
  above: room_below                                Vertical stacking
  stack: named_stack                               Named vertical group

MEP (CURRENT — IMPLICIT)
  SPRINKLERS grid:3.0m                             Fire suppression grid
  LIGHTS grid:4.0m type:LED                        Lighting grid
```

### 2.2 Current Compilation Pipeline

```
DSL text  →  ParseStage (regex)  →  BuildingDefinition (records)
          →  CompileStage        →  BuildingSpec (geometry)
          →  TemplateStage       →  BOM composition (ST mode)
          →  WriteStage          →  output.db (IFC elements)
          →  DigestStage         →  spatial fingerprint
          →  GeometryStage       →  mesh integrity
          →  ProveStage          →  mathematical witnesses
```

### 2.3 The Gap

The current DSL handles *what rooms exist* and *where openings go*. It does NOT handle:

- **MEP routing** — how do pipes and ducts find paths through the building?
- **Clearance checking** — does the duct clear the beam? Does the pipe clear the ceiling?
- **System connectivity** — is the sprinkler network connected to the riser?
- **Load-bearing logic** — does this beam span support the slab above?
- **Material specification** — what wall build-up (brick + insulation + block) applies?
- **Construction sequencing** — what gets built first?

These are all *high-level construction verbs* that currently require manual authoring at the IFC geometry level. BIM COBOL fills this gap.

### 2.4 Implemented Verbs (v0.9) — Scoreboard

The BIM_COBOL module has a working `Verb<T>` interface, `VerbContext`, and `VerbResult<T>` framework. **57 verbs implemented, 183 witnesses (179 PASS / 4 RED pre-existing):**

| # | Verb | Layer | Witnesses | What it proves |
|---|---|---|---|---|
| 1 | `CHECK BOM` | original | W-1..4 | BOM tree structural integrity |
| 2 | `COVER WITH COMPOUND_ROOF` | original | W-5..8 | T-junction valley geometry |
| 3 | `ROUTE SPRINKLERS` | original | W-9..12 | Grid + pipe routing + NFPA compliance |
| 4 | `CONNECT FITTINGS` | original | W-17..20 | Port-budget fitting connectivity |
| 5 | `CHECK PLACEMENT` | original | W-21..24 | P01–P04 element geometry proofs |
| 6 | `CHECK CLASH` | original | W-25..28 | MEP vs structural bbox overlap |
| 7 | `CHECK ROOM` | original | W-29..32 | Room dims vs UBBL building code |
| 8 | `CHECK COMPLIANCE` | original | W-33..36 | Mounting heights + spacing rules |
| 9 | `WIRE LIGHTING` | original | W-37..40 | Fixture grid + conduit + lux |
| 10 | `VERIFY PLACEMENT` | original | W-45..48 | Cross-DB placement fidelity |
| 11 | `TILE SURFACE` | original | W-49..52 | 2D parametric grid fill |
| 12 | `ARRAY` | original | W-53..56 | 1D linear repetition + code compliance |
| 13 | `PLACE BOM` | data | — | BOM walk → output.db emission |
| 14 | `EN-BLOC` / `WALK THRU` | data | — | Compilation mode dispatch |
| 15-22 | `SELECT`/`LIST`/`DESCRIBE`/`COUNT`/`AGGREGATE`/`EXPORT`/`CLONE`/`SUMMARIZE` | data | — | BOM query + export |
| 23-30 | `CREATE BOM`/`ADD LINE`/`SET TACK`/`SET ROTATION`/`SET DIMENSIONS`/`REMOVE LINE`/`DELETE BOM`/`SET LINE PROPERTY` | L0 | W-SY-1..29 | Synthetic BOM primitives |
| 31-33 | `EXTRACT AABB`/`SNAP TO GRID`/`VALIDATE AABB` | utility | W-SY-1..29 | Geometry planning |
| 34-37 | `CREATE ROOM`/`FURNISH ROOM`/`RESIZE ROOM`/`STRIP ROOM` | L1 | W-SY-30..43 | Room-level convenience |
| 38-42 | `PARTITION AABB`/`CREATE FLOOR`/`ADD ROOM`/`REMOVE ROOM`/`SWAP ROOM` | L2 | W-SY-44..56 | Floor-level composition |
| 43-45 | `COMPOSE BUILDING`/`ADD FLOOR`/`STACK FLOORS` | L3 | W-SY-66..72 | Building-level composition |
| 46-48 | `DEFINE CATEGORY`/`ADD TEMPLATE RULE`/`REGISTER BOM` | L4 | W-SY-57..65 | Catalog-level taxonomy |
| 49-51 | `REPORT BOM CATALOG`/`REPORT PRODUCT CATALOG`/`REPORT BOM STRUCTURE` | H0 | W-H0-1..10 | XLSX report generation |
| 52 | `COMPOSE PREFAB BOM` | H2 | W-H2-1..3 | Idempotent m_bom + m_bom_line creation |
| 53 | `CLEAR VARIANCE FROM BOM` | H2 | W-H2-4..6 | Delete variance/buffer rows |
| 54 | `FILL BUFFERS IN BOM` | H2 | W-H2-7..9 | Interstitial filler computation |
| 55 | `REGISTER BUILDING` | H2 | W-H2-10..12 | c_order creation (DocStatus=IP) |
| 56 | `COMPLETE BUILDING` | H2 | W-H2-13..15 | c_order promotion (IP→CO) |
| — | *VerbRegistry + ScriptRunner* | infra | W-41..44 | Dispatch + script execution |
| — | *F5IntegrationTest* | infra | W-F5-1..200 | **End-to-end cross-verb integration (30 verbs, 36 lines, 0 failures)** |

Rosetta Stone validation (W-13..16) is run as part of the `RouteSprinklersVerbTest` suite, proving BIM COBOL geometry matches real Terminal/Duplex IFC data.

#### CHECK BOM (W-COBOL-1..4)

```bimcobol
CHECK BOM <bom_id>
```

Walks the BOM tree from a root, counting BUY/MAKE/PHANTOM nodes and checking structural invariants (empty assemblies, missing products, cycle guard). Read-only against BOM.db.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-1 | BED_SET: 5 BUY + 1 PHANTOM, pass=true |
| W-COBOL-2 | FLOOR_SH_GF_STD: multi-level tree, MAKE>0, depth>0 |
| W-COBOL-3 | Nonexistent BOM → fail with "not found" |
| W-COBOL-4 | toJson() contains all expected fields |

#### COVER WITH COMPOUND_ROOF (W-COBOL-5..8)

```bimcobol
COVER WITH COMPOUND_ROOF <mesh_type>
```

Loads parametric mesh definitions from `component_library.db`, generates the primary hip roof and subsidiary gable roofs (discovered via `connects_to` + `valley_type=T_JUNCTION` in `lod_parametric_mesh_param`), then computes T-junction valley geometry where the slopes intersect. Pure geometry — `ValleyStitcher` intersects slope planes via cross-product, finds the meeting point where all three planes converge, validates angles and descent.

**Key classes:**
- `ValleyStitcher` — `Plane3D`, `ValleyLine`, `ValleyResult`, `computeTJunction()`
- `CoverWithRoofVerb` — DB queries, mesh generation, slope plane extraction
- `VerbContext.of(bomConn, componentConn)` — dual-connection context

| Witness | Assertion |
|---------|-----------|
| W-COBOL-5 | HIP_ROOF_MY compound: 1 subsidiary=GABLE_PORCH_MY, no violations |
| W-COBOL-6 | Meeting point Z > 0, Z < 1.30 (below hip ridge), on all 3 planes |
| W-COBOL-7 | Valley V-angle 30-150°, both valley lines descend |
| W-COBOL-8 | Error cases: nonexistent, null componentConn, no args |

**Geometry (from DB params):** Hip south slope z = 0.4663y + 1.259; gable west slope z = 0.1962(x + 2.55); triple intersection at **(1.85, -0.849, 0.863)**.

#### ROUTE SPRINKLERS (W-COBOL-9..12)

```bimcobol
ROUTE SPRINKLERS <building_type> <storey> <room_name> [SPACING <mm>] [HAZARD <class>]
```

First MEP routing verb. Computes sprinkler head grid + pipe routing + NFPA compliance proof from a single statement. Read-only against BOM.db — queries `ad_room_boundary` for room AABB and `ad_fp_coverage` for hazard class rules (LIGHT: max_coverage=18.6m², max_spacing=4.6m, wall_distance=2.3m).

**Key classes:**
- `SprinklerGrid` — pure geometry: grid generation within rectangular AABB, spacing/2 offset, center fallback for narrow rooms
- `PipeRouter` — pure geometry: main line at riserX running along Y, per-head branch pipes, PipeSpec/PipeRoutingResult
- `ComplianceChecker` — NFPA compliance: coverage per head, max spacing, wall distance checks against CoverageRule
- `RouteSprinklersVerb` — JDBC orchestration: room AABB → grid → routing → compliance → SprinklerRoutingPayload

| Witness | Assertion |
|---------|-----------|
| W-COBOL-9 | bilik_utama (BEDROOM 3.1×3.1m): 1 head, pass=true, compliance pass, area ~9.61m² |
| W-COBOL-10 | common (COMMON 3.7×6.2m): 2 heads, max spacing ≤ 4.6m, compliance pass |
| W-COBOL-11 | Pipe routing: total length > 0, one branch per head, fittings present |
| W-COBOL-12 | Error cases: nonexistent room → fail, missing storey → fail, insufficient args → fail |

#### Rosetta Stone Validation (W-COBOL-13..16)

Validates BIM COBOL geometry against real IFC extracted data — the extracted buildings are Rosetta Stones that prove our algorithms match professional engineering practice.

**Data sources:**
- `Terminal_Extracted.db` — 49,059 elements, 909 sprinkler heads (697 pendant + 202 upright), 3,821 pipe segments, 814 light fixtures
- `Ifc2x3_Duplex_extracted.db` — 1,099 elements, 427 pipes, 358 fittings, 47 receptacles, 14 switches

**Grid pattern discovery (Terminal z=14.9 level, 213 pendant heads):** X spacing histogram: **3.0m = 91%** (172/189 measurements). Y spacing varies 0.6–3.6m (multiple zones). AABB spans 57m × 36m. This proves the 3.0m default in our verb matches real-world practice.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-13 | Terminal pendant heads (z=10.9, 108 heads) pass NFPA LIGHT compliance via ComplianceChecker |
| W-COBOL-14 | SprinklerGrid density within 25% of actual Terminal grid at 3.0m spacing |
| W-COBOL-15 | Duplex receptacle count consistent with ad_space_type_mep power_points rules |
| W-COBOL-16 | Terminal MEP census: 7+ IFC classes, 10k+ MEP elements (verb discovery inventory) |

#### CHECK PLACEMENT (W-COBOL-21..24)

```bimcobol
CHECK PLACEMENT <db_path> [TIER <1|2|ALL>]
```

Validates element placement geometry in an extracted or compiled DB. Tier 1: per-element arithmetic proofs (P01 positive extent, P02 finite coords, P03 min dimension, P04 storey Z-band). Tier 2: pairwise proofs (P05 no duplicate position, P06 no same-class overlap). Read-only — opens target DB, never writes.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-21 | Duplex 1099 elements: P01 positive extent — all pass |
| W-COBOL-22 | Duplex: P02 finite coords — no NaN/Inf |
| W-COBOL-23 | Duplex: P04 storey Z-band — 1099 elements within range |
| W-COBOL-24 | Error cases: no args, nonexistent DB → fail gracefully |

#### CHECK CLASH (W-COBOL-25..28)

```bimcobol
CHECK CLASH <db_path> [CLEARANCE <mm>]
```

Pure SQL RTREE bbox overlap detection between MEP elements (IfcFlowSegment, IfcFlowFitting, IfcFlowTerminal, IfcFlowController, IfcLightFixture, IfcPipeSegment, IfcDuctSegment) and structural elements (IfcBeam, IfcColumn, IfcSlab). Expands structural bbox by clearance (default 50mm = BIMConstants.MEP_STRUCTURE_CLEARANCE), checks 3D intersection. Read-only.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-25 | Duplex: 569 clashes (904 MEP, 29 structural, 50mm clearance) |
| W-COBOL-26 | Duplex 0mm clearance: 295 actual bbox overlaps verified |
| W-COBOL-27 | Terminal: 2280 clashes (10436 MEP, 1295 structural) |
| W-COBOL-28 | Error cases: no args, bad path → fail |

#### CHECK ROOM (W-COBOL-29..32)

```bimcobol
CHECK ROOM <db_path> [ROOM_TYPE <type>]
```

Room dimensions vs `ad_ubbl_rule` building code checks. Room geometry derived from IfcSpace entries in `spatial_structure`, with bounds computed from contained elements via `rel_contained_in_space`. Rules loaded from BOM.db: AREA (mm² → m²), MIN_DIM (mm → m), CEILING_MM (mm → m). Uses `ctx.bomConn()`.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-29 | Duplex: 11 rooms with positive area |
| W-COBOL-30 | Duplex: 88 UBBL area checks applied, 64 pass |
| W-COBOL-31 | Duplex: 11 ceiling height checks vs UBBL_CEIL (2.4m) |
| W-COBOL-32 | Error cases: no args, bad path → fail |

#### CHECK COMPLIANCE (W-COBOL-33..36)

```bimcobol
CHECK COMPLIANCE <db_path> [RULE <rule_id>]
```

Element placement vs `ad_placement_rule` mounting heights and spacings. Maps IFC classes to rules (IfcLightFixture→LIGHT_CEILING, IfcFlowTerminal→OUTLET_WALL, IfcFlowController→WALL_SPACED, etc.). Checks element Z against expected height (±150mm tolerance), spacing between same-class elements on same storey ≤ max_spacing_m. Uses `ctx.bomConn()`.

| Witness | Assertion |
|---------|-----------|
| W-COBOL-33 | Duplex: 127/1099 elements checked against placement rules |
| W-COBOL-34 | Duplex: 210 outlet placement checks (OUTLET_WALL rule) |
| W-COBOL-35 | Duplex: 127 spacing checks, all pass (WALL_SPACED max 3.6m) |
| W-COBOL-36 | Error cases: no args, null BOM, bad path → fail |

#### WIRE LIGHTING (W-COBOL-37..40)

```bimcobol
WIRE LIGHTING <building_type> <storey> <room_name> [GRID <mm>] [TYPE <fixture>]
```

Electrical generation verb — computes ceiling light fixture placement, conduit routing, and compliance proofs for a room. Mirrors ROUTE SPRINKLERS pattern: loads room AABB from `ad_room_boundary`, fixture count from `ad_space_type_mep.light_points`, placement constraints from `ad_placement_rule` (LIGHT_CEILING: edge_offset=0.3m, LIGHT_CEILING_GRID: max_spacing=4.6m). Derives spacing from `sqrt(roomArea / light_points)` unless GRID override. Reuses `SprinklerGrid` for fixture placement. Read-only.

**Key classes:**
- `ConduitRouter` — pure geometry: main conduit at panelX (20mm), per-fixture branch conduits (16mm), JUNCTION_BOX fittings
- `WireLightingVerb` — JDBC orchestration: room AABB → light_points → grid → conduit routing → compliance → LightingPayload
- `SprinklerGrid` — reused directly for fixture grid generation

**Compliance checks:** EDGE_OFFSET (>=0.3m from walls), MAX_SPACING (<=4.6m between fixtures), MIN_COUNT (>=light_points from ad_space_type_mep). Also computes lux (informational, not a fail gate) and circuit count (ceil(count/10)).

| Witness | Assertion |
|---------|-----------|
| W-COBOL-37 | bilik_utama (BEDROOM 3.1×3.1m): 1 fixture, compliance PASS, lux>0, area ~9.61m² |
| W-COBOL-38 | common (COMMON 3.7×6.2m): >=2 fixtures (light_points=2), max_spacing<=4.6m, conduit>0 |
| W-COBOL-39 | Conduit routing: totalLength>0, one branch per fixture, fittings non-empty |
| W-COBOL-40 | Error cases: nonexistent room, missing storey, insufficient args → fail |

#### TILE SURFACE (W-COBOL-49..52) ★ NEW

```bimcobol
TILE SURFACE <surface_name> WITH <product_name> ORIGIN <x> <y> <z> GRID <nx> <ny> STEP <dx_mm> <dy_mm>
```

The verb that makes the Terminal BOM feasible. A single TILE statement replaces thousands of flat coordinate rows. 19 TILE formulas generate all 33,324 roof plates across 9 Z-bands. Purely parametric — no DB access. Step values in mm are converted to meters at the verb boundary.

**Key classes:**
- `TileGrid` — pure geometry: `generate()` for single panel, `generateMulti()` for multi-panel concatenation with overlap detection
- `TileSurfaceVerb` — 13-arg parser: surface, product, origin (x,y,z), grid (nx,ny), step (dx,dy). Validates nx/ny ≥ 1, step > 0
- `TilePayload` — carries surfaceName, productName, nx, ny, stepXMm, stepYMm, totalCount, positions

**Example — Terminal Z19 west panel:**
```bimcobol
TILE SURFACE ROOF_DECK_Z19_WEST WITH PLATE_500x150x106 ORIGIN 92.49 -42.16 19.0 GRID 15 294 STEP 495 150
-- Output: ROOF_DECK_Z19_WEST: 15×294 = 4,410 tiles, step 495×150mm
```

| Witness | Assertion |
|---------|-----------|
| W-COBOL-49 | Single panel 15×294 = 4,410 tiles. First pos at origin, last at origin + (14×0.495, 293×0.150, 0) |
| W-COBOL-50 | Multi-panel (3 panels, 7,370 tiles): no duplicate positions (Set size == list size) |
| W-COBOL-51 | Full command dispatch via VerbRegistry: verb="TILE SURFACE", payload.totalCount=4410 |
| W-COBOL-52 | Insufficient args → fail with usage hint. nx=0 → fail. Negative step → fail |

#### ARRAY (W-COBOL-53..56) ★ NEW

```bimcobol
ARRAY <host_element> WITH <product_name> LENGTH <mm> SPACING <mm> COVER <mm> [DIRECTION X|Y|Z]
```

1D linear repetition with structural code compliance. Computes rebar (or any element) positions along a host at regular spacing. Formula: `count = floor((hostLength - 2×cover) / spacing) + 1`. Compliance checks against BS 8110 (cover ≥ 25mm) and EC2 (spacing ≤ 300mm). No DB access.

**Key classes:**
- `LinearArray` — pure geometry: `generate()` with Direction enum (X/Y/Z). Input: spacing/cover/hostLength in mm. Output: positions in meters
- `ArrayVerb` — 9-arg parser with optional DIRECTION. BS 8110 + EC2 compliance gate
- `ArrayPayload` — carries count, spacingMm, coverMm, hostLengthMm, coverCompliant, spacingCompliant, positions

**Example — slab reinforcement:**
```bimcobol
ARRAY SLAB_TE_GF_001 WITH REBAR_T16 LENGTH 6000 SPACING 150 COVER 40
-- Output: SLAB_TE_GF_001: 40 bars @ 150mm spacing, cover 40mm, cover PASS, spacing PASS
```

| Witness | Assertion |
|---------|-----------|
| W-COBOL-53 | LENGTH 6000 SPACING 150 COVER 40 → 40 bars. First at 40mm, last at 5890mm. Monotonically increasing |
| W-COBOL-54 | COVER 20 (below 25mm BS 8110 min) → coverCompliant=false, summary contains "cover FAIL" |
| W-COBOL-55 | DIRECTION Y dispatch: all positions have x=0, z=0. First Y at 40mm cover offset |
| W-COBOL-56 | Missing LENGTH → fail with usage hint |

---

## 3. Language Levels

BIM COBOL operates at three abstraction levels, mirroring the COBOL → assembler → machine code chain:

```
LEVEL 2: CONSTRUCTION INTENT (BIM COBOL — the new language)
         "Route sprinkler system in departure hall at 3m spacing"
         Domain expert can read, verify, and modify.
                    │
                    ▼  BIM COBOL Compiler
LEVEL 1: BUILDING SPECIFICATION (current DSL)
         "BUILDING ... STOREY ... ROOM ... WINDOW ..."
         Structured declarations that the 8-stage pipeline processes.
                    │
                    ▼  8-Stage Pipeline
LEVEL 0: IFC ASSEMBLER (output.db / Blender)
         IfcPipeSegment(guid, start, end, radius)
         IfcPipeFitting(guid, type, position)
         IfcFireSuppressionTerminal(guid, position)
         Individual geometry elements with exact coordinates.
```

### 3.1 Level 2 → Level 1 Compilation

Level 2 statements compile into Level 1 DSL augmented with metadata:

```
L2 source:
  ROUTE SPRINKLERS IN "departure_hall" SPACING 3.0m BELOW_CEILING 150mm

L1 output (generated, not hand-written):
  ad_space_type_mep_bom:  (LOBBY, SPRINKLER, qty=ceil(area/9.0), CEILING_CENTER)
  I_Element_Extraction:   (TE, L01, IfcFireSuppressionTerminal, SPR_001, ...)
  m_bom_line:             (FP_TE_L01, SPRINKLER_HEAD_K80, BUY, qty=N)
```

### 3.2 Level 1 → Level 0 Compilation

Level 1 compiles via the existing 8-stage pipeline into output.db:

```
L1 metadata rows  →  CompileStage resolves positions
                  →  MEPBOMResolver calculates quantities
                  →  WriteStage emits IfcFireSuppressionTerminal elements
                  →  ProveStage proves P17 connectivity + clearance
```

### 3.3 Level 0 → Blender

Level 0 (output.db) loads into Bonsai/Blender via the existing IFC loader:

```
output.db  →  Bonsai Full Load  →  Blender viewport
           →  elements_meta      →  IFC classification
           →  base_geometries    →  mesh data
           →  surface_styles     →  Principled BSDF materials
```

---

## 4. BIM COBOL Verb Categories

BIM COBOL verbs map to construction operations, not geometry operations. Each verb compiles down to a cluster of Level 0 IFC elements + BOM entries + compliance checks.

### 4.1 ENVELOPE Verbs — Building Shell

```bimcobol
ENCLOSE room_name WITH wall_type
    -- Generates: IfcWall segments forming closed polygon around room boundary
    -- Resolves:  wall build-up from M_Product catalog (e.g., WALL_EXT_MY_150)
    -- Proves:    P10 perimeter closure, SS01 room boundary fidelity

OPEN wall_face OF room_name WITH opening_type AT position
    -- Generates: IfcDoor or IfcWindow hosted on wall
    -- Resolves:  opening family from ad_space_type_opening schedule
    -- Proves:    SS03 opening host match, space contract OPENINGS count

COVER building WITH roof_type PITCH angle OVERHANG distance
    -- Generates: IfcRoof with parametric mesh from lod_parametric_mesh_param
    -- Resolves:  roof geometry from building footprint + overhang + pitch
    -- Proves:    SS05 parametric mesh dimensions, witness ROOF_COVERS_ALL

COVER WITH COMPOUND_ROOF mesh_type          ★ IMPLEMENTED (v0.4)
    -- Input:     Primary mesh (HIP_ROOF_MY) from lod_parametric_mesh
    --            Subsidiaries via connects_to + valley_type=T_JUNCTION
    -- Computes:  Generates both meshes via ParametricMesh.generate()
    --            Extracts slope planes from vertex indices
    --            Intersects planes → valley lines (cross-product method)
    --            Finds meeting point (closest approach of coplanar lines)
    -- Validates: Meeting on all 3 planes (5mm tol), angle 20-160°, descend
    -- Proves:    W-COBOL-5..8 (compound pass, meeting point, angle, errors)

SPAN storey WITH slab_type THICKNESS dimension
    -- Generates: IfcSlab covering storey footprint
    -- Resolves:  slab extent from room boundaries + structural grid
    -- Proves:    P01 positive extent, structural span check
```

### 4.2 MEP Verbs — Mechanical, Electrical, Plumbing

This is the critical new capability. Each MEP verb encapsulates a complete system routing operation.

```bimcobol
ROUTE SPRINKLERS IN zone SPACING distance BELOW_CEILING offset
    -- Input:     Room boundary (from synthetic stone or extracted)
    --            Ceiling mesh (from structural slab above)
    --            Beam positions (from structural frame)
    -- Computes:  Grid points at SPACING intervals within room polygon
    --            Ceiling height minus OFFSET = sprinkler head Z
    --            Avoids beams: shift grid points that clash with beam AABB
    --            Lateral pipe runs connecting heads to branch line
    --            Branch lines connecting to main run
    --            Main run connecting to riser (SHAFT)
    -- Generates: IfcFireSuppressionTerminal (heads)
    --            IfcPipeSegment (laterals, branches, mains)
    --            IfcPipeFitting (tees, elbows, reducers)
    -- Proves:    P17 system connected (BFS from riser to every head)
    --            Clearance: every pipe segment > 50mm from beam
    --            MS 1910 spacing compliance (max 3.7m for ordinary hazard)
    -- BOM:       m_bom_line entries for pipe, fittings, heads
    --            Quantities auto-calculated from routing

ROUTE DUCTS IN zone FROM riser SIZE width x height
    -- Input:     Room boundary, ceiling mesh, beam positions
    -- Computes:  Main duct run from riser along corridor ceiling
    --            Branch ducts to each zone
    --            Duct size reduction at branches (velocity method)
    --            Air terminal positions (supply diffusers)
    --            Clearance checking against pipes and beams
    -- Generates: IfcDuctSegment (main + branches)
    --            IfcDuctFitting (tees, elbows, reducers, dampers)
    --            IfcAirTerminal (supply diffusers, return grilles)
    -- Proves:    System connected, clearance, air change rate
    -- BOM:       Duct lengths, fittings, terminals

ROUTE DRAINAGE IN zone GRADIENT ratio TO stack
    -- Input:     Fixture positions (basins, toilets, floor traps)
    --            Stack position (vertical drain)
    -- Computes:  Pipe routes from fixtures to stack
    --            Gradient enforcement (min 1:80 for waste, 1:40 for soil)
    --            Pipe diameter from fixture unit count (MS 1228)
    --            Vent pipe sizing and routing
    -- Generates: IfcPipeSegment (waste + soil + vent)
    --            IfcPipeFitting (bends, junctions, traps)
    -- Proves:    P16 waste flows downhill, P17 system connected
    --            P23 drain alignment (corner gap ≤ 5mm)
    -- BOM:       Pipe lengths by diameter, fitting counts

WIRE LIGHTING IN zone GRID spacing TYPE fixture_type
    -- Input:     Room boundary, ceiling height
    -- Computes:  Grid layout at SPACING intervals
    --            Lux level calculation (fixture lumens × count / area)
    --            Circuit grouping (max fixtures per circuit)
    -- Generates: IfcLightFixture at grid positions
    --            IfcCableSegment (conduit runs, conceptual)
    -- Proves:    Lux level ≥ minimum for room type (MS 1525)
    -- BOM:       Fixture count, conduit length, circuit breaker count

ROUTE COLDWATER IN zone FROM riser PIPE_SIZE diameter
    -- Similar to drainage but pressure-driven (no gradient)
    -- Computes fixture connections, pipe sizing, valve positions
    -- Generates: IfcPipeSegment, IfcPipeFitting, IfcValve, IfcFlowTerminal

ROUTE GAS IN zone FROM meter PIPE_SIZE diameter
    -- Gas piping with safety constraints (external runs, ventilation)
    -- Generates: IfcPipeSegment, IfcPipeFitting, IfcValve
```

### 4.3 REPETITION Verbs — Parametric Placement

These verbs generate elements by FORMULA instead of flat enumeration. A formula describes
WHERE elements go using geometry (grids, paths, arrays) rather than listing every position.

```bimcobol
TILE surface WITH product GRID nx x ny STEP dx dy                    ★ IMPLEMENTED (v0.9)
    -- Input:     Surface name, product type, grid dimensions, spacing
    -- Computes:  pos(i,j) = origin + (i × dx, j × dy, 0)
    --            Each grid cell → one element of product type
    -- Generates: nx × ny IfcPlate (or other product) at computed positions
    -- Proves:    P01 positive extent, P05 no duplicate position, full coverage
    -- BOM:       m_bom_line with qty = nx × ny (factorized)
    -- Expands:   c_orderline per instance at compile time

    -- Multi-panel variant for irregular surfaces:
    TILE SURFACE "ROOF_DECK_Z19" WITH "PLATE_500x150x106"
        PANEL "west"    ORIGIN (92.49, -42.16, 19.0) GRID 15 x 294 STEP (495mm, 150mm)
        PANEL "central" ORIGIN (122.63, -42.16, 19.0) GRID 14 x 174 STEP (495mm, 150mm)
        PANEL "east"    ORIGIN (141.74, -42.16, 19.0) GRID 15 x 34  STEP (495mm, 150mm)
    END-TILE
    -- 3 formulas → 7,356 elements. 19 panels → 33,324 plates (entire roof).
    -- Bonsai has a 1D linear array (BBIM_Array pset). This is a 2D parametric fill
    -- that neither Bonsai nor Revit offers as a single declarative operation.

    -- Evidence: Terminal_Extracted.db roof plates tile in perfect grids.
    --   Y-step: 150mm (99% of pairs). X-step: 495mm (81% of pairs).
    --   9 Z-bands, 19 rectangular panels, 18 of 19 perfect rectangles.
    --   Measured: 1,754× reduction (19 formulas replace 33,324 orderlines).

ARRAY host_element WITH product SPACING distance COVER offset          ★ IMPLEMENTED (v0.9)
    -- Input:     Host element (slab, beam), product type, bar spacing, cover
    -- Computes:  count = floor((host_length - 2×cover) / spacing) + 1
    --            pos(i) = host_start + cover + i × spacing
    -- Generates: count × IfcReinforcingBar (or other product) along host
    -- Proves:    Cover distance ≥ minimum (BS 8110 / EC2), spacing ≤ maximum
    -- BOM:       m_bom_line with qty = count (factorized)

    -- This is the linear analogue of TILE — 1D instead of 2D.
    -- Evidence: 2,660 IfcReinforcingBar in Terminal, 150mm dominant spacing
    --   (73 pairs at 150mm). Each host slab/beam has bars at regular intervals.
```

**Formula coverage across Terminal (51,092 elements):**

| Formula Pattern | Verb | Elements | % | Status |
|---|---|---|---|---|
| TILE (2D grid) | `TILE SURFACE` | 33,324 | 65.2% | **LIVE** (v0.9) |
| PATH (1D route) | `ROUTE SPRINKLERS` (§4.2) | 9,345 | 18.3% | **LIVE** |
| ARRAY (1D linear) | `ARRAY` | 2,660 | 5.2% | **LIVE** (v0.9) |
| GRID (ceiling/floor) | `WIRE LIGHTING` / `ROUTE SPRINKLERS` (§4.2) | 2,012 | 3.9% | **LIVE** |
| PERIMETER (boundary) | `ENCLOSE` / `SPAN` (§4.1) | 1,038 | 2.0% | designed |
| GRID (structural) | `FRAME` (§4.3b) | 590 | 1.2% | designed |
| **FORMULA TOTAL** | | **48,969** | **95.8%** | **74.4% LIVE** |
| Irregular (flat) | manual placement | 2,123 | 4.2% | — |

95.8% of a 51K-element building can be expressed as formulas.
**74.4% of Terminal elements (38,001 / 51,092) are now covered by live verbs** — TILE, ARRAY, ROUTE, and WIRE.
Only 2,123 elements (furniture, proxies, misc) need flat coordinate storage.
**The c_orderline table shrinks from 51,092 to ~2,200 formulas + flat entries.**

> **Terminal BOM reduction:** 58× smaller recipe (51K rows → ~2.9K). Verb invocations stored as
> `PP_Order_Node` rows (PP_Order_Node model, §15.6). Full Terminal measurement data and
> phase roadmap (TE-1..TE-8) in [`TheRosettaStoneStrategy.txt`](TheRosettaStoneStrategy.txt)
> §Terminal Recomposition.

### 4.3b STRUCTURAL Verbs

```bimcobol
FRAME storey WITH grid MEMBER_SIZE beam_spec COLUMN_SIZE col_spec
    -- Generates: IfcBeam at grid intersections (X-spans)
    --            IfcColumn at grid nodes
    --            IfcMember for bracing/secondary
    -- Resolves:  Member sizes from structural catalog (M_Product)
    -- Proves:    Grid completeness, member-slab connection

REINFORCE element WITH rebar_spec COVER dimension SPACING bar_spacing
    -- Generates: IfcReinforcingBar within parent element envelope
    -- Computes:  Bar count from element dimensions and spacing
    --            Cover distance from face
    --            Bar bending schedule
    -- BOM:       Rebar weight (kg), bar count by diameter + length
```

### 4.4 FURNISH Verbs

```bimcobol
FURNISH room WITH furniture_set
    -- Input:     Room boundary, room type
    -- Resolves:  Furniture set from BOM catalog (BED_SET_MASTER, LIVING_SET, etc.)
    -- Computes:  Layout within room boundary (wall-hugging, centroid, clearance)
    -- Generates: IfcFurniture elements
    -- Proves:    SS04 furniture in room bounds
    -- BOM:       Furniture items from SET BOM explosion

EQUIP room WITH equipment_list
    -- For non-furniture equipment: kitchen appliances, bathroom fixtures
    -- Computes:  Position against service wall (water, electrical connections)
    -- Generates: IfcFlowTerminal, IfcSanitaryTerminal
```

### 4.5 COMPLIANCE Verbs

```bimcobol
CHECK room AGAINST code_ref
    -- Evaluates all ad_ubbl_rule entries for room type
    -- Returns: PASS with witness, or FAIL with violation + code citation

CERTIFY building FOR jurisdiction
    -- Runs full compliance suite
    -- Generates: witness.json with per-rule pass/fail + measured values
    -- Output:    Machine-readable compliance certificate
```

---

## 5. The MEP Routing Problem — In Detail

This is the first Level 2 verb that matters, because MEP is where manual authoring costs the most time and where errors are most expensive.

### 5.1 What "ROUTE SPRINKLERS" Actually Computes

```
Given:
  room_polygon = [(x1,y1), (x2,y2), ..., (xn,yn)]   from ad_room_boundary
  ceiling_z    = storey.level + storey.height - slab_thickness
  beam_aabbs   = [(bx1,by1,bz1, bx2,by2,bz2), ...]  from structural frame
  riser_pos    = (rx, ry)                              from SHAFT declaration
  spacing      = 3000mm                                from BIM COBOL verb
  offset       = 150mm                                 from BIM COBOL verb

Step 1: GRID GENERATION
  Generate grid points at spacing intervals within room polygon.
  Use point-in-polygon test (ray casting) to exclude points outside room.
  Result: List<Point2D> head_positions

Step 2: BEAM AVOIDANCE
  For each head_position:
    If position XY within any beam AABB (expanded by pipe_radius + clearance):
      Shift position to nearest clear point along grid axis.
  Result: List<Point2D> adjusted_positions (no beam clashes)

Step 3: SPRINKLER HEAD PLACEMENT
  For each adjusted_position:
    z = ceiling_z - offset
    Create IfcFireSuppressionTerminal at (x, y, z)
  Result: List<SprinklerHead> heads

Step 4: LATERAL PIPE ROUTING
  Group heads into rows (same Y or same X, depending on main run direction).
  For each row:
    Create IfcPipeSegment connecting heads in sequence.
    Add IfcPipeFitting (tee) at each head connection.
  Result: List<PipeRun> laterals

Step 5: BRANCH LINE ROUTING
  Connect lateral rows to a main branch line.
  Branch line runs perpendicular to laterals.
  Route along ceiling, avoiding beams (shift Z or detour XY).
  Result: PipeRun branch

Step 6: MAIN RUN TO RISER
  Route branch line to riser position.
  Use shortest path along ceiling grid, avoiding beams and ducts.
  Add IfcPipeFitting at direction changes (elbow) and connections (tee).
  Result: PipeRun main

Step 7: CONNECTIVITY PROOF
  BFS from riser through all pipe segments.
  Every sprinkler head must be reachable.
  This is P17 (drainage system connected) applied to fire protection.
  Result: PASS (all heads reachable) or FAIL (disconnected heads)

Step 8: COMPLIANCE CHECK
  Max spacing between heads ≤ code limit (MS 1910: 3.7m ordinary hazard).
  Min distance from wall ≤ half spacing.
  Coverage area per head ≤ code limit (12.1m² ordinary hazard).
  Result: Per-head compliance witness
```

### 5.2 The Ceiling Mesh as Routing Surface

The critical geometric insight: **MEP routes along the ceiling mesh.** The ceiling is not empty space — it is a 2D surface with obstacles (beams, other ducts, cable trays). Routing is a 2D pathfinding problem on this surface.

```
CEILING MESH (plan view, looking up):

  ┌──────────────┬──────────────┬──────────────┐
  │              │    BEAM      │              │
  │   ○    ○    │ ██████████  │   ○    ○     │  ○ = sprinkler head
  │              │              │              │
  │   ○    ○    │   ○    ○    │   ○    ○     │  ─ = pipe segment
  │              │              │              │
  │───────────────────────────────────────────│  █ = beam (obstacle)
  │              │              │              │
  │   ○    ○    │   ○    ○    │   ○    ○     │
  │              │    BEAM      │              │
  │   ○    ○    │ ██████████  │   ○    ○     │
  │              │              │              │
  └──────────────┴──────────────┴──────────────┘
                        │
                     RISER (vertical pipe to roof tank)
```

The routing algorithm treats this as a **grid graph** where:
- Nodes = grid intersections at the ceiling level
- Edges = potential pipe segments between adjacent nodes
- Obstacles = beam AABBs + existing pipes/ducts (clearance envelope)
- Cost = pipe length (Dijkstra or A* for shortest path)

This is the same kind of problem EDA (Electronic Design Automation) tools solve for PCB trace routing — but in construction scale. The "traces" are pipes and ducts. The "components" are sprinkler heads and air terminals. The "board" is the ceiling mesh.

### 5.3 Clearance Calculation

Every MEP element must maintain clearance from structure and other MEP:

```
CLEARANCE RULES (from ad_mep_clearance — proposed table):

  Element Type          vs Structure    vs Other MEP    Code Reference
  ────────────────────  ────────────    ────────────    ─────────────
  Sprinkler pipe        50mm            50mm            MS 1910
  ACMV duct             25mm            50mm            MS 1525
  Cold water pipe       25mm            25mm            MS 1228
  Drainage pipe         25mm            50mm            MS 1228
  Electrical conduit    25mm            150mm from HV   JKR standards
  Gas pipe              150mm           150mm           DOSH guidelines
```

The compiler checks every pipe/duct segment against all beams and all other MEP segments. A clash is any overlap of the element AABB expanded by the clearance envelope. This is an R-tree spatial query — the same `elements_rtree` mechanism already in the output database.

---

## 6. Type System

BIM COBOL has a domain-specific type system where types correspond to construction entities, not programming primitives.

### 6.1 Spatial Types

```
BUILDING      — root container, has AABB, has grid, has storeys
STOREY        — floor level, has height, has rooms, has Z-offset
ROOM          — enclosed space, has boundary polygon, has room_type
ZONE          — group of rooms, has purpose (DEPARTURE, RETAIL, MECHANICAL)
SHAFT         — vertical penetration, has type (ELECTRICAL, PLUMBING, HVAC)
CORE          — vertical circulation group (stairs + lifts + lobbies)
```

### 6.2 Element Types

```
WALL          — vertical barrier, has build-up (layers), has face (N/S/E/W)
SLAB          — horizontal span, has thickness, has structural role
BEAM          — horizontal span member, has section profile
COLUMN        — vertical compression member, has section profile
OPENING       — hole in wall (DOOR or WINDOW), has host wall, has schedule ref
ROOF          — top enclosure, has pitch, has overhang, has form (GABLE/HIP/FLAT)
STAIR         — inclined circulation, has width, has rise/going
RAILING       — edge barrier, has height, has host element
```

### 6.3 MEP System Types

```
SPRINKLER_SYSTEM    — fire suppression network (heads + pipes + riser)
DUCT_SYSTEM         — air distribution network (ducts + terminals + riser)
DRAINAGE_SYSTEM     — waste/soil pipe network (pipes + fixtures + stack)
WATER_SYSTEM        — cold/hot water distribution (pipes + fixtures + riser)
ELECTRICAL_SYSTEM   — power + lighting (fixtures + circuits + panel)
GAS_SYSTEM          — fuel distribution (pipes + valves + meter)
```

### 6.4 BOM Types (from ERP pattern)

```
UNIT     — complete building or unit (top of BOM tree)
FLOOR    — one storey's worth of elements (generated per order)
ROOM_BOM — one room's assembly (from catalog)
SET      — sub-assembly (wall panel + frame + cladding)
ITEM     — leaf product (BUY = physical, PHANTOM = spacer)
```

### 6.5 Type Relationships

```
BUILDING contains STOREY[]
STOREY   contains ROOM[] + ZONE[]
ROOM     contains WALL[] + OPENING[] + FURNITURE[]
ROOM     hosts    MEP_SYSTEM[]
WALL     hosts    OPENING[]
SLAB     supports WALL[] (storey above)
BEAM     spans    COLUMN to COLUMN
SHAFT    connects STOREY to STOREY (vertical MEP path)
```

The type system enforces construction logic at compile time. You cannot `ROUTE DUCTS IN wall` — ducts route in rooms, along ceilings. You cannot `OPEN slab WITH door` — doors go in walls. These constraints are type errors, caught before any geometry is generated.

---

## 7. Compilation Targets

Unlike a conventional programming language that compiles to machine code, BIM COBOL compiles to three simultaneous outputs:

### 7.1 IFC Geometry (Level 0)

The primary output — the "machine code" of construction:

```
IfcWall, IfcSlab, IfcDoor, IfcWindow, IfcBeam, IfcColumn,
IfcPipeSegment, IfcDuctSegment, IfcFireSuppressionTerminal,
IfcLightFixture, IfcAirTerminal, IfcFurniture, ...
```

Each with exact coordinates, geometry mesh, material assignment, spatial containment, and assembly membership. This is what Bonsai/Blender renders and what the IFC file contains.

### 7.2 Procurement BOM

The ERP output — what the factory and site need:

```
m_bom hierarchy:  UNIT → FLOOR → DISCIPLINE → SET → ITEM
m_bom_line:       child_product_id, quantity, allocated dimensions
M_Product:        catalog entry with dimensional signature
c_orderline:      per-element placement record
```

A `ROUTE SPRINKLERS` verb generates not just geometry but a complete bill of materials: N metres of 25mm pipe, M tee fittings, P sprinkler heads K80 pendant, Q pipe hangers. The BOM is the purchase order input. No manual take-off.

### 7.3 Compliance Witnesses

The proof output — what the authority needs:

```
witness.json:
  { "rule": "MS_1910_SPACING",
    "room": "DEPARTURE_HALL_L01",
    "required_max_spacing_mm": 3700,
    "actual_max_spacing_mm": 3000,
    "result": "PASS",
    "measured_at": "2026-02-28T14:30:00Z" }
```

Every BIM COBOL verb that involves a code requirement produces a witness. The compliance certificate is not a document someone writes — it is a compilation artefact that the compiler produces automatically.

---

## 8. Grammar Sketch

### 8.1 Program Structure

```bimcobol
PROJECT "Sultan Johor Terminal II" {
    JURISDICTION  MS_UBBL_1984
    FIRE_CODE     MS_1910_2009
    MEP_CODE      MS_1228, MS_1525
    PROFILE       Malaysian_Institutional

    SITE envelope:120000x85000mm storeys:4 {
        GRID {
            axes: A,B,C,D,E,F,G / 1,2,3,4,5,6,7,8
            spacing: 10000,10000,12000,12000,10000,8000 /
                     8000,8000,8000,8000,8000,8000,8000
        }

        STOREY "Aras Tanah" level:0 height:4500mm {
            ZONE "departure" bounds:A1-D6 {
                ENCLOSE WITH WALL_EXT_MY_200
                OPEN SOUTH WITH DOOR_AUTO_SLIDING COUNT 4
                OPEN NORTH WITH WINDOW_CURTAIN_WALL

                ROUTE SPRINKLERS SPACING 3000mm BELOW_CEILING 150mm
                ROUTE DUCTS FROM shaft_acmv_1 SIZE 600x400mm
                WIRE LIGHTING GRID 4000mm TYPE LED_PANEL_600x600
                FURNISH WITH DEPARTURE_SEATING_SET
            }

            ZONE "checkin" bounds:A7-D8 {
                ENCLOSE WITH WALL_INT_MY_100
                OPEN WEST WITH DOOR_D1 COUNT 2

                ROUTE SPRINKLERS SPACING 3000mm BELOW_CEILING 150mm
                WIRE LIGHTING GRID 3000mm TYPE LED_DOWNLIGHT
                EQUIP WITH CHECKIN_COUNTER_SET
            }

            SHAFT "shaft_acmv_1" at:E3 size:2000x2000mm type:HVAC
            SHAFT "shaft_fp_1"   at:E5 size:1500x1500mm type:FIRE_PROTECTION
            SHAFT "shaft_cw_1"  at:F3 size:1000x1000mm type:PLUMBING

            CORE "main_core" bounds:E1-F2 {
                STAIR "stair_1" width:1500mm to:"Aras 01"
                ELEVATOR "lift_1" type:PASSENGER car:1600x2100 door:1200
                ELEVATOR_LOBBY pressurized:true fire_rating:2hr
            }
        }

        STOREY "Aras 01" level:1 height:4000mm {
            -- ... (boarding gates, retail, etc.)
        }

        COVER WITH METAL_DECK PITCH 5deg OVERHANG 1500mm

        FRAME ALL_STOREYS WITH GRID
            BEAM_SIZE UB_457x152x52
            COLUMN_SIZE UC_254x254x73
        REINFORCE SLABS WITH T16 COVER 25mm SPACING 200mm
    }

    CHECK ALL AGAINST MS_UBBL_1984
    CERTIFY FOR CIDB_IBS
}
```

### 8.2 Syntax Principles

1. **English-readable.** A building inspector can read `ROUTE SPRINKLERS IN "departure" SPACING 3000mm` and verify the intent without understanding IFC.

2. **Declarative, not procedural.** No loops, no variables, no if/else. The compiler decides *how* — the user declares *what*. (Exactly as COBOL's PERFORM was declarative invocation, not a goto.)

3. **Every noun is typed.** `WALL_EXT_MY_200` resolves to a M_Product catalog entry. `LED_PANEL_600x600` resolves to a component. Invalid references are compile errors.

4. **Every verb has a proof.** `ROUTE SPRINKLERS` proves connectivity and spacing. `ENCLOSE` proves closure. `CHECK` proves code compliance. No verb executes without a provable outcome.

5. **Catalog-driven.** The language does not define products — it references them. The catalog (M_Product + m_bom + component_library.db) is the "standard library." Extending the language to support new product types requires catalog entries, not grammar changes.

---

## 9. Relationship to the Bonsai GUI

BIM COBOL is not a replacement for the GUI — it is the **engine behind it.** The Bonsai addon GUI translates user interactions into BIM COBOL statements, then invokes the compiler.

```
USER ACTION (Bonsai GUI)              BIM COBOL GENERATED              COMPILED OUTPUT
─────────────────────────────────────────────────────────────────────────────────
Click "Add Room" → drag boundary   →  LIVING "room1" bounds:...  →  IfcSpace + IfcWall[]
Click "Add Door" → click wall      →  OPEN SOUTH WITH DOOR_D1    →  IfcDoor hosted on wall
Click "Add MEP" → select system    →  ROUTE SPRINKLERS ...       →  IfcPipe[] + IfcTerminal[]
Click "Check Code" → select code   →  CHECK room1 AGAINST UBBL   →  witness.json
Slider "Spacing" → adjust          →  SPACING 2500mm (updated)   →  recompile, new layout
```

The GUI is the *editor*. BIM COBOL is the *source code*. The compiler is the *build system*. The user never sees BIM COBOL directly (just as most programmers today never see assembler). But BIM COBOL is what makes the GUI deterministic — every click compiles to a statement, every statement compiles to proven geometry.

**TILE verb in the GUI:** The user draws a surface boundary, picks a product (e.g., metal deck plate), and sets X/Y spacing. The GUI writes a TILE block. The compiler expands it to thousands of elements with computed positions. This is parametric surface tiling — what Revit's curtain wall system does for facades, but generalised to any surface and any product. Bonsai's current array is 1D linear copies along a single vector (`BBIM_Array` pset with count + offset). TILE is a 2D surface fill with separate X/Y step, multi-panel support, and automatic BOM factorization. Neither Bonsai nor Revit offers this as a single declarative operation today.

### 9.1 Live Recompilation

When the user adjusts a parameter (e.g., sprinkler spacing from 3.0m to 2.5m), the GUI:

1. Updates the BIM COBOL statement: `SPACING 2500mm`
2. Recompiles the affected zone (incremental — only the sprinkler system, not the entire building)
3. Diffs the output (old vs new IFC elements)
4. Updates the Blender viewport (remove old heads, add new heads, reroute pipes)
5. Updates the compliance witness (re-check spacing rule)
6. Updates the BOM (new pipe lengths, fitting counts)

This is the **compiler feedback loop** that makes the GUI responsive. It is exactly analogous to a code editor with live compilation errors — change a line, see the red squiggle instantly.

### 9.2 Round-Trip Editing

A critical property: BIM COBOL must support **round-trip editing**. If the user moves a sprinkler head manually in Bonsai (drag in 3D viewport), the system must:

1. Detect the position change (Blender event)
2. Update the BIM COBOL representation (adjust the grid or add an override)
3. Re-verify compliance (does the new position still satisfy spacing rules?)
4. Update the BOM (pipe lengths change if head moved)

This is the hardest unsolved problem. COBOL did not have round-trip — you wrote code, compiled, ran. BIM COBOL operates in a live-editing environment where the "source code" and the "compiled output" must stay synchronised. The solution: BIM COBOL statements carry **override annotations**.

```bimcobol
ROUTE SPRINKLERS IN "departure" SPACING 3000mm BELOW_CEILING 150mm {
    OVERRIDE SPR_017 AT (45200, 23100)  -- user manually repositioned
    OVERRIDE SPR_023 OMIT              -- user deleted (column obstruction)
}
```

Overrides are first-class syntax. The compiler honours them, re-routes around them, and re-proves compliance including the overrides. If an override violates a code rule, the compiler flags it — the user sees a red warning, not a silent acceptance.

---

## 10. Implementation Roadmap

### Phase BC-0: Verb Framework + First Verbs ★ COMPLETE

- `Verb<T>` sealed interface, `VerbContext`, `VerbResult<T>` with JSON serialisation
- `CHECK BOM` — BOM tree walk with structural invariants (W-COBOL-1..4)
- `COVER WITH COMPOUND_ROOF` — T-junction valley stitching between hip and gable roofs (W-COBOL-5..8)
- `ValleyStitcher` — pure geometry: plane intersection, closest approach, validation
- 8/8 witnesses pass against live DB data

### Phase BC-1: MEP Routing + Rosetta Stone ★ COMPLETE

- `ROUTE SPRINKLERS` — grid generation + pipe routing + NFPA compliance (W-COBOL-9..12)
- `SprinklerGrid`, `PipeRouter`, `ComplianceChecker` — pure geometry, no DB
- Rosetta Stone validation against Terminal (909 sprinklers) + Duplex (1099 MEP elements) (W-COBOL-13..16)
- 16/16 witnesses pass against live DB + extracted IFC data
- Grid pattern discovery: 3.0m spacing = 91% of Terminal measurements

### Phase BC-2: Parametric Repetition — TILE + ARRAY ★ COMPLETE

`TileSurfaceVerb` (keyword `TILE SURFACE`) and `ArrayVerb` (keyword `ARRAY`), with pure
geometry helpers `TileGrid` and `LinearArray`. 8 witnesses (W-COBOL-49..56). No DB access —
purely parametric computation with mm → m conversion at verb boundary.

**TILE SURFACE** generates NxM elements on a surface at regular grid spacing. Single-panel
`generate()` and multi-panel `generateMulti()` with overlap detection. 19 formulas
replace 33,324 flat orderlines. Multi-panel block syntax (PANEL...END-TILE) is a future
ScriptRunner enhancement; current grammar supports one panel per statement.

**ARRAY** generates N elements along a host at regular spacing (rebar in slabs/beams).
`count = floor((hostLength - 2×cover) / spacing) + 1`. Direction enum (X/Y/Z). Compliance
checks: cover ≥ 25mm (BS 8110), spacing ≤ 300mm (EC2). VerbResult pass/fail reflects
compliance status.

Combined with existing ROUTE/WIRE verbs, these two new verbs bring **live formula coverage
to 74.4% of Terminal's 51K elements** (§4.3 table). GUI implication: user draws a surface +
picks a product + sets spacing = one TILE block. This is parametric design that neither
Bonsai nor Revit offers as a single declarative operation.

Bonsai's current array feature (`BBIM_Array` pset) supports only 1D linear offset copies
along a single vector (x, y, z). TILE is fundamentally different: a 2D surface fill with
separate X/Y step, multi-panel support, and BOM integration (qty factorization).

**Key files:**
- `BIM_COBOL/src/main/java/com/bim/cobol/geometry/TileGrid.java` — grid position generator
- `BIM_COBOL/src/main/java/com/bim/cobol/geometry/LinearArray.java` — 1D array generator
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/TileSurfaceVerb.java` — TILE SURFACE verb
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/ArrayVerb.java` — ARRAY verb

### Phase F0.x: Data Handling Verbs ★ COMPLETE

8 new verbs lifting common data operations to language level. No raw SQL needed
for BOM querying, export, or analysis. All read-only except CLONE BOM.

**Query & Inspection (4 verbs):**

| Verb | Keyword | Purpose |
|------|---------|---------|
| `SelectBomVerb` | `SELECT BOM` | Filter BOM children by field=value (component_type, role, locator_ref, etc.) |
| `ListBomVerb` | `LIST BOMS` | Enumerate BOMs by prefix (BUILDING_, SY_, FLOOR_, etc.) |
| `DescribeBomVerb` | `DESCRIBE BOM` | Hierarchical tree view with types, roles, dimensions |
| `CountBomVerb` | `COUNT BOM` | Count children, optionally RECURSIVE for full tree |

**Analysis & Export (3 verbs):**

| Verb | Keyword | Purpose |
|------|---------|---------|
| `AggregateBomVerb` | `AGGREGATE BOM` | Group by dimension (component_type, role, bom_level), compute counts |
| `ExportBomVerb` | `EXPORT BOM` | Export BOM tree to CSV or JSON file |
| `SummarizeBuildingVerb` | `SUMMARIZE BUILDING` | Output.db overview: elements, storeys, AABB, IFC class distribution |

**Mutation (1 verb):**

| Verb | Keyword | Purpose |
|------|---------|---------|
| `CloneBomVerb` | `CLONE BOM` | Deep copy BOM tree with new root ID (recursive MAKE children) |

**Example usage:**
```bimcobol
-- Query
LIST BOMS BUILDING_
SELECT BOM BUILDING_DX_STD WHERE component_type = BUY
DESCRIBE BOM DUPLEX_SET_STD
COUNT BOM BUILDING_DX_STD RECURSIVE

-- Analysis
AGGREGATE BOM BUILDING_DX_STD BY component_type
EXPORT BOM BUILDING_SH_STD AS CSV FILE /tmp/sh_bom.csv

-- Mutation
CLONE BOM BUILDING_SH_STD AS SY_SH_COPY

-- Post-compilation
SUMMARIZE BUILDING SH
```

**Key files:**
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/SelectBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/ListBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/DescribeBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/CountBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/AggregateBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/ExportBomVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/SummarizeBuildingVerb.java`
- `BIM_COBOL/src/main/java/com/bim/cobol/verb/CloneBomVerb.java`

### Phase BC-3: Duct Routing

Add `ROUTE DUCTS` with duct sizing calculation (velocity method or equal friction method), branch takeoffs, and air terminal placement. This is harder because duct sizes vary (main → branch → terminal) and clearance envelopes are larger.

### Phase BC-4: Drainage and Water

Add `ROUTE DRAINAGE` (gradient-constrained, P16 proof) and `ROUTE COLDWATER` (pressure-driven). These are the pipe systems that connect to fixtures (toilets, basins, taps) rather than grid-placed devices.

### Phase BC-5: Structural Verbs

Add `FRAME` and `REINFORCE`. These require structural analysis (load paths, span checks) which is more complex than MEP routing. May integrate with external structural analysis engines via IFC structural analysis model.

### Phase BC-6: Round-Trip and Override System

Implement the override annotation system for live editing in Bonsai. This is the integration phase where BIM COBOL becomes the engine behind the GUI.

### Phase BC-7: Parser and Error Reporting

Build a proper BIM COBOL parser (ANTLR or hand-written recursive descent) replacing the current regex-based DSL parser. Produce meaningful error messages with code citations:

```
ERROR at line 14: ROUTE SPRINKLERS SPACING 5000mm
  Spacing 5000mm exceeds MS 1910 maximum of 3700mm for ordinary hazard.
  Suggestion: SPACING 3000mm (within limit, provides 12.5% margin)
```

---

## 11. Why Not Just Use an Existing Language?

**Could we embed construction logic in Python/JavaScript/Rust?**

We could. But then the domain expert (architect, fire engineer, inspector) cannot read the source. The accountant could not read assembler — that was the whole point of COBOL. A fire engineer can read `ROUTE SPRINKLERS IN "departure" SPACING 3000mm`. A fire engineer cannot read:

```python
for head in sprinkler_grid(room.polygon, spacing=3000):
    if not intersects_beam(head.pos, beams, clearance=50):
        pipe_network.add_head(head)
    else:
        head.shift_to_nearest_clear(beams)
pipe_network.route_to_riser(riser_pos)
```

Both express the same logic. But only one is readable by the person who must *certify* the result. The fire engineer signs off on the compliance certificate. If the language that produced it is opaque, the certificate is meaningless.

BIM COBOL's value is not computational — it is **communicative**. It makes the building's logic readable by every stakeholder: architect, engineer, inspector, contractor, and owner. This is what COBOL achieved for business, and what construction has never had.

---

## 12. The Three Compilation Artefacts

Every BIM COBOL program produces exactly three outputs. All three are required. None is optional.

```
         BIM COBOL Source
              │
              ▼
        ┌───────────┐
        │  Compiler  │
        └─────┬─────┘
              │
    ┌─────────┼─────────┐
    ▼         ▼         ▼
 IFC/BIM    BOM       Witness
 (geometry) (procurement) (proof)
```

**1. IFC/BIM** — what the building looks like. Loadable in Bonsai, Revit, ArchiCAD. Contains every element with exact geometry, spatial structure, material assignment.

**2. BOM** — what to buy and build. Complete bill of materials from UNIT down to ITEM. Quantities, dimensions, material specifications. Import directly into iDempiere, SAP, or any ERP.

**3. Witness** — why it is correct. Per-rule compliance proofs with measured values and code citations. Machine-readable. Submittable for digital building permits. The compliance certificate that no other tool produces automatically.

If any one of the three is missing, the compilation is incomplete. A building without a BOM cannot be built. A building without a witness cannot be permitted. A building without geometry cannot be visualised. BIM COBOL enforces all three.

---

## 13. Prior Art — What Exists and What Doesn't

A survey of academic literature and industry standards reveals that **no existing system combines forward-compilation, BOM generation, and compliance proof from a domain-readable source.** Several adjacent attempts exist:

### 13.1 BERA Language (Georgia Tech, 2011–2015)

The closest academic predecessor. The Building Environment Rule and Analysis language is a DSL for checking building rules against existing IFC models — circulation analysis, spatial programming validation. Implemented on JVM, reads IFC via Solibri Model Checker. Published in *Journal of Intelligent & Robotic Systems* (2014).

**Difference from BIM COBOL:** BERA is *post-hoc checking* — the building is modelled first (in Revit), then BERA checks if it is compliant. BIM COBOL is *pre-hoc compilation* — intent is declared, the compiler refuses to produce non-compliant geometry. BERA verifies an existing model. BIM COBOL generates a proven one.

*References:*
- Lee, J.K. (2011). *Building environment rule and analysis (BERA) language and its application for evaluating building circulation and spatial program.* Georgia Institute of Technology.
- Lee, J.K., Eastman, C.M. et al. (2014). *Implementation of a BIM Domain-specific Language for the Building Environment Rule and Analysis.* J. Intelligent & Robotic Systems, Springer.

### 13.2 Dynamo / Grasshopper Visual Programming

Revit's Dynamo and Rhino's Grasshopper allow users to create parametric rules via node-and-wire visual programming. These are the most widely used "scripting" approaches in BIM practice. A 2024 systematic review (*From BIM to computational BIM*) catalogues their application across building research.

**Difference from BIM COBOL:** Visual programming is procedural — the user wires computation nodes. It is powerful but opaque to non-specialists. A fire engineer cannot audit a Grasshopper definition. BIM COBOL is declarative and domain-readable. `ROUTE SPRINKLERS SPACING 3000mm` is auditable by the person who must certify the result.

### 13.3 mvdXML / IDS (buildingSMART Standards)

mvdXML was an XML-based rule language for IFC model validation. It was declared "unimplementable" by software vendors due to ambiguous XSD and documentation. Replaced by **IDS** (Information Delivery Specification), approved as official buildingSMART standard in June 2024. IDS defines *what information must be present* in an IFC file — a schema validator for data completeness.

**Difference from BIM COBOL:** IDS checks data presence ("does this wall have a fire rating property?"). It does not generate buildings, route MEP, or produce BOMs. It is a validation schema, not a compiler.

### 13.4 BIMSL (Lisbon, 2023)

Building Information Modeling Specific Language — a DSL for integrating BIM with sensor data, handling real-time queries that combine IoT sensor readings with building model data.

**Difference from BIM COBOL:** BIMSL is a query language for operational buildings (facilities management). It reads existing models and sensor data. It does not compile new buildings.

### 13.5 LLM + BIM (2024–2025 Research Wave)

Recent papers couple ChatGPT with Grasshopper/Vectorworks to translate natural language ("design a 3-bedroom house") into parametric definitions or BIM assemblies. Text2BIM (ASCE, 2025) uses a multi-agent LLM framework for generating building models from text descriptions.

**Difference from BIM COBOL:** Non-deterministic — same prompt may produce different buildings. No proof chain. No BOM output. No compliance guarantee. These are the exact limitations that a compiled approach solves. LLM-generated buildings cannot be submitted for building permits because there is no machine-verifiable proof that the output satisfies code.

### 13.6 Comparative Analysis

| Capability | BERA | Dynamo | IDS | Bonsai | Revit | **BIM COBOL** |
|---|---|---|---|---|---|---|
| Forward compilation (intent → geometry) | no | partial | no | no | no | **yes** |
| Compliance at compile time | no (post-hoc) | no | no (post-hoc) | no | no | **yes** |
| BOM output (procurement) | no | no | no | no | partial | **yes** |
| Witness proofs (machine-readable) | no | no | no | no | no | **yes** |
| Domain-expert readable source | partial | no | no | no | no | **yes** |
| Deterministic (same input → same output) | yes | yes | yes | yes | yes | **yes** |
| MEP routing from intent | no | manual | no | no | manual | **yes** |
| Parametric 2D tiling (TILE) | no | no | no | 1D only | 1D only | **2D grid** |
| Formula-driven placement (95.8%) | no | manual | no | no | no | **yes** |
| Offline / local execution | yes | yes | yes | yes | **no** | **yes** |

**The gap is real.** No existing system compiles construction intent into the three simultaneous artefacts (IFC geometry + procurement BOM + compliance witnesses) from a domain-readable source. BERA came closest on rule-checking. Grasshopper came closest on parametric generation. But the three-artefact compilation from auditable source — that does not exist.

This positions BIM COBOL as a novel contribution, publishable as *"BIM COBOL: A Domain-Specific Language for Compiled Construction"* in venues such as *Automation in Construction* (Elsevier) or a buildingSMART International Summit.

---

## 14. Strategic Position

BIM COBOL is to construction what:
- **COBOL** was to business (domain-readable, compiles to lower level)
- **SQL** was to databases (declarative, describes *what* not *how*)
- **VHDL** was to chip design (intent → synthesised hardware)
- **LaTeX** was to typesetting (structured document → formatted output)

In each case, a domain-specific language replaced manual low-level authoring with high-level declarations that compile to verified output. None of these languages made the lower level disappear — you can still write assembler, still hand-craft database queries, still design gates manually. But the 80% case moved to the higher level, and the domain experts gained the ability to read and verify the output.

Construction is the last major industry without this abstraction. BIM COBOL provides it.

### 14.1 Honest Competitive Analysis — BIM COBOL + Bonsai vs Revit

Revit is the global industry standard with 20+ years, millions of users, and deep
ecosystem integration. BIM COBOL does not replace Revit. It does things Revit cannot.
The comparison is not "which is better" but "which capabilities exist where."

**Where BIM COBOL wins outright:**

| Capability | Revit | BIM COBOL |
|---|---|---|
| Compliance at compile time | No. Post-hoc only (Model Checker, Solibri). Revit 2026 deprecated the Model Checker API. | Yes. Compiler refuses non-compliant geometry. Witness proofs per verb. |
| BOM procurement output | No. Schedules ≠ BOMs. Quantity takeoffs need CostX, Bluebeam, or manual extraction. No ERP-ready output. | Yes. iDempiere-compatible M_BOM + C_OrderLine directly. Import to any ERP. |
| Formula-driven placement | Limited. Component arrays (1D linear/radial, editable in 2026). No 2D surface tiling. No formula coverage metric. | 95.8% of elements via 5 formula patterns (TILE, PATH, ARRAY, GRID, PERIMETER). |
| Deterministic reproduction | No. Same brief → two architects → two different RVT files. Result depends on user actions, viewport state, undo history. | Yes. Same .bimcobol source → identical output every time. Byte-for-byte. |
| Auditability by domain expert | No. Fire engineer must visually inspect 909 sprinkler heads in 3D. Cannot read the "source" of the MEP layout. | Yes. Fire engineer reads `ROUTE SPRINKLERS SPACING 3000mm` and signs off. |
| Scale efficiency | One element = one object in memory. Airport terminals (50K+ elements) are notoriously slow. | 51K elements in 2.9K factorized rows. Compile-time expansion only. |
| Open format | No. RVT is proprietary. IFC export is "decent" but lossy. Vendor lock-in. | Yes. IFC native. SQLite databases. No subscription. No lock-in. |
| Offline execution | Partially. Revit desktop works offline. Cloud features (ACC, worksharing) need internet. | Fully offline. Local SQLite + local compile. |

**Where Revit wins outright:**

| Capability | Revit | BIM COBOL |
|---|---|---|
| Interactive 3D GUI | Full parametric modelling environment. Click, drag, snap, constrain. 20 years of UX refinement. | None (CLI only). Bonsai/Blender for visualisation but no BIM COBOL-aware GUI yet. |
| Parametric families | 10,000+ manufacturer families. Doors that resize, windows that constrain, MEP fittings with connection points. | 122 M_Product rows. Growing, but orders of magnitude smaller. |
| Construction documents | Plans, sections, elevations, details, schedules. Print-ready sheets with annotation, dimensions, keynotes. | None. Output is IFC + BOM + witness. No drawing output. |
| Multi-user collaboration | Worksharing (central model + local copies). BIM 360 / ACC cloud collaboration. | Single-user only. |
| Rendering | Built-in rendering + cloud rendering. Material previews, walkthroughs, VR. | Relies on Bonsai/Blender (Cycles/EEVEE). No native rendering. |
| Ecosystem integration | Navisworks (clash detection), ACC (project management), Dynamo (scripting), 100+ plugins. | BIM COBOL + Bonsai + IfcOpenShell. Small ecosystem. |
| Regulatory acceptance | Revit files accepted by building departments worldwide. Established submission workflows. | No regulatory track record. Witness proofs are novel — no jurisdiction accepts them yet. |
| Structural/energy analysis | Robot Structural, Insight energy analysis, gbXML export. | None. No analysis engine. |

**Where neither wins yet (the open frontier):**

| Capability | Status |
|---|---|
| AI-assisted design | Revit exploring (Autodesk AI). BIM COBOL is deterministic — LLM could WRITE .bimcobol but the compiler guarantees correctness. Advantage: BIM COBOL, because the output is provable. |
| Digital building permits | No jurisdiction fully automates this. BIM COBOL's witness proofs are the closest machine-readable format, but regulatory adoption is years away. |
| Lifecycle / FM integration | Both weak. Revit hands off to FM tools. BIM COBOL's BOM could feed maintenance schedules but doesn't yet. |

**The strategic insight:**

BIM COBOL does not compete with Revit's GUI. It competes with the **manual repetitive
work inside Revit.** The 95.8% of elements that follow formula patterns — those are the
elements that architects spend days placing manually in Revit. One by one. Click, place,
adjust. Repeat 33,324 times for a roof deck.

The competition is not "BIM COBOL vs Revit" — it is "BIM COBOL + Bonsai vs Revit":
- Bonsai provides the GUI, rendering, IFC editing, and visualisation
- BIM COBOL provides the compilation engine, BOM generation, and compliance proofs
- Together they offer an open-source alternative where the 80% repetitive case is
  automated, the 20% creative case is manual (in Bonsai), and the compliance is proven

Revit will remain dominant for bespoke architectural design — curved facades, complex
interiors, custom details. BIM COBOL targets the **high-repetition, rule-governed** segment:
institutional buildings (terminals, hospitals, schools), residential developments (hundreds
of similar units), infrastructure (repetitive structural bays). These are the projects where
95%+ of elements follow patterns, and where manual placement in Revit is the bottleneck.

The ceiling for BIM COBOL is not "replace Revit" — it is "make Revit unnecessary for the
pattern-governed majority of construction, while remaining interoperable (via IFC) for the
bespoke minority."

---

*"The architect writes intent. The compiler produces geometry, BOM, and proof. The inspector reads the witness. The contractor reads the BOM. The owner sees the building. All from one source."*

---

## 15. Verbs as Pipeline Applicators — AD_Val_Rules over CO_EmptySpace Lines

*Added v0.5 — insight from Rosetta Stone analysis*

BIM COBOL verbs are not just standalone validation tools. They are the mechanism by which **cross-discipline MEP rules get applied during the compilation pipeline**, operating over the CO_EmptySpace line hierarchy.

### 15.1 The CO_EmptySpace Hierarchy

The compilation pipeline produces `co_empty_space_line` at three levels, each representing a BOM decomposition tier:

```
Level 0: UNIT BOM acceptance     — full building AABB        → building-level verbs
Level 1: Structural decomposition — per-storey structural tiers → storey-level routing verbs
Level 2: Room selections          — per-room BOMs (LI, BD, KT) → room-level placement verbs
```

Currently, MEP elements are placed by hardcoded methods in `StoreyCompiler`:

```
placeMEPSprinklers()      → hardcoded grid generation
placeHVAC()               → hardcoded diffuser placement
placeElectrical()         → hardcoded light fixture placement
mepBomGapFill()           → MEPWorker reads ad_space_type_mep_bom
```

**The replacement:** Each hardcoded method becomes a BIM COBOL verb that reads its rules from `ad_space_type_mep_bom` and applies them over the CO_EmptySpace lines at the appropriate level.

### 15.2 Verb-to-Pipeline Mapping

| StoreyCompiler Method | Replacement Verb | CO_EmptySpace Level | ad_space_type_mep_bom.placement_rule |
|---|---|---|---|
| `placeMEPSprinklers()` | `ROUTE SPRINKLERS` | Level 2 (room) | `CEILING_GRID` |
| `placeHVAC()` | `ROUTE DUCTS` | Level 1 (storey) + Level 2 (room) | `CEILING_CENTER` |
| `placeElectrical()` | `WIRE LIGHTING` | Level 2 (room) | `CEILING_CENTER`, `CEILING_GRID` |
| `mepBomGapFill()` | `PLACE FIXTURES` | Level 2 (room) | `WALL_SPACED`, `WALL_ENTRY`, `WALL_BACK`, `FLOOR_LOW` |

### 15.3 The ad_space_type_mep_bom Placement Rules as Verb Patterns

Each `placement_rule` in the table is essentially a verb pattern:

```
CEILING_GRID    → SprinklerGrid.generate() or LightGrid.generate()
CEILING_CENTER  → single fixture at room centroid
WALL_SPACED     → outlets along walls at intervals (NEC 210.52: max 12ft apart)
WALL_ENTRY      → switch near door opening (NEC 404.4)
WALL_BACK       → toilet/sink against service wall (IPC 405)
WALL_SIDE       → basin adjacent to toilet
WALL_HIGH       → aircon point above door height
WALL_SINK       → GFCI outlet adjacent to basin
FLOOR_LOW       → floor trap at lowest point (MS 1228)
```

This means the grammar can be data-driven: when a new `placement_rule` is added to `ad_space_type_mep_bom`, the verb system learns it automatically. No code changes needed.

### 15.4 Cross-Discipline Application

The power of verbs over CO_EmptySpace lines is **cross-discipline checking**. Currently each MEP system is placed independently. With verbs:

```bimcobol
-- Level 1: storey-level routing (main pipe runs)
ROUTE SPRINKLERS IN storey MAIN_FROM shaft_fp_1 PIPE_SIZE 65mm

-- Level 2: room-level placement (per CO_EmptySpace_Line)
FOR EACH room IN storey WHERE ad_space_type_mep_bom.SPRINKLER {
    ROUTE SPRINKLERS IN room
        SPACING (FROM ad_fp_coverage WHERE hazard_class)
        BELOW_CEILING 150mm
        CHECK CLEARANCE AGAINST beams     -- structural avoidance
        CHECK CLEARANCE AGAINST ducts     -- MEP-to-MEP clearance
}
```

The `CHECK CLEARANCE AGAINST` clause is the cross-discipline key. It operates on the R-tree index of elements already placed by prior verbs (structural beams from Level 1, ducts from a prior HVAC verb). This turns the compilation from independent per-discipline passes into a coordinated multi-discipline pipeline where each verb builds on what came before.

### 15.5 Rosetta Stone Evidence for Grammar Enrichment

The Terminal_Extracted.db census reveals which IFC classes need verbs:

| Extracted IFC Class | Count | Proposed Verb | ad_space_type_mep_bom product |
|---|---|---|---|
| `IfcFireSuppressionTerminal` | 909 | `ROUTE SPRINKLERS` ★ done | SPRINKLER |
| `IfcLightFixture` | 814 | `WIRE LIGHTING` | LIGHT |
| `IfcPipeSegment` | 3,821 | `ROUTE PIPES` | (system verb) |
| `IfcPipeFitting` | 4,243 | (emitted by ROUTE verbs) | (fittings) |
| `IfcDuctSegment` | 568 | `ROUTE DUCTS` | SUPPLY_DIFFUSER |
| `IfcDuctFitting` | 713 | (emitted by ROUTE DUCTS) | (fittings) |
| `IfcAirTerminal` | 289 | `PLACE DIFFUSERS` | CEILING_FAN, SUPPLY_DIFFUSER |
| `IfcFlowTerminal` | 256 | `PLACE OUTLETS` | OUTLET, OUTLET_GFCI |
| `IfcFlowController` | 21 | `PLACE SWITCHES` | SWITCH |

The Duplex_extracted.db provides wall-mount MEP patterns:

| Pattern | Extracted Evidence | Verb | Height |
|---|---|---|---|
| Receptacle grid | 47 outlets @ z=0.46m | `PLACE OUTLETS IN room HEIGHT 460mm` | Standard outlet |
| Counter outlets | 6 outlets @ z=1.07m | `PLACE OUTLETS IN room HEIGHT 1070mm` | Counter-height |
| Light switches | 14 switches @ z=1.22m | `PLACE SWITCHES IN room HEIGHT 1220mm` | Standard switch |

### 15.6 VerbStage in the Pipeline — Structured Verb Storage (Option C)

The end state is a new compilation stage between WriteStage and DigestStage:

```
Stage 1: MetadataValidator   — validate referential integrity
Stage 2: ParseStage          — DSL → BuildingDefinition
Stage 3: CompileStage        — BuildingDefinition → BuildingSpec (geometry)
Stage 4: TemplateStage       — template composition (ST mode only)
Stage 5: WriteStage          — BuildingSpec → output.db
Stage 6: VerbStage           — BIM COBOL verbs → MEP elements over CO_EmptySpace lines  ★ NEW
Stage 7: DigestStage         — spatial fingerprinting
Stage 8: GeometryStage       — mesh integrity verification
Stage 9: ProveStage          — mathematical placement proofs
```

#### Verb Storage — PP_Order_Node Model (Design Decision 2026-03-04)

Verb invocations are stored as structured rows, following the iDempiere Manufacturing
`PP_Order_Node` + `PP_Order_NodeProduct` pattern. This replaces the earlier inline
`c_order.verb_script` TEXT column proposal.

**Rationale:** Multi-user GUI editing, per-verb lifecycle tracking (DR→IP→CO→VO),
queryable parameters, Bonsai form-based verb editing, and familiar iDempiere ERP pattern.
See `docs/ADHistory.md` §Manufacturing Workflow for the iDempiere parallel.

```sql
-- iDempiere Manufacturing: PP_Order_Node = one production operation step
-- BIM semantics: one verb invocation (TILE SURFACE, ARRAY, ROUTE SPRINKLERS...)
-- Lives in output.db (transaction data, not BOM.db dictionary)
CREATE TABLE PP_Order_Node (
    PP_Order_Node_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
    C_Order_ID             TEXT NOT NULL REFERENCES c_order(building_id),  -- BIM: which building
    SeqNo                  INTEGER NOT NULL DEFAULT 10,   -- iDempiere: execution order
    Name                   TEXT NOT NULL,                  -- BIM: verb_keyword (TILE SURFACE, ARRAY...)
    Description            TEXT NOT NULL,                  -- BIM: verb_args (human-readable COBOL source)
    S_Resource_ID          INTEGER,                        -- BIM: co_emptyspace_line_id (which spatial slot)
    M_Product_ID           TEXT,                           -- BIM: m_bom_id (which BOM this draws from)
    IsActive               INTEGER DEFAULT 1,
    DocStatus              TEXT DEFAULT 'DR'
        CHECK(DocStatus IN ('DR','IP','CO','VO')),
    last_result            TEXT,                           -- BIM-specific: VerbResult.toJson() witness
    element_count          INTEGER DEFAULT 0,              -- BIM-specific: elements generated
    Created                TEXT DEFAULT (datetime('now')),
    Updated                TEXT DEFAULT (datetime('now'))
);

-- iDempiere Manufacturing: PP_Order_NodeProduct = material consumed/produced per operation
-- BIM semantics: structured parameters per verb (ORIGIN_X, GRID_NX, SPACING_MM...)
CREATE TABLE PP_Order_NodeProduct (
    PP_Order_NodeProduct_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    PP_Order_Node_ID         INTEGER NOT NULL REFERENCES PP_Order_Node(PP_Order_Node_ID),
    Name                     TEXT NOT NULL,    -- BIM: param_name (SURFACE_NAME, ORIGIN_X, GRID_NX...)
    Value                    TEXT NOT NULL,    -- BIM: param_value
    ValueType                TEXT DEFAULT 'TEXT'
        CHECK(ValueType IN ('TEXT','REAL','INTEGER')),
    UNIQUE(PP_Order_Node_ID, Name)
);
```

**Example — Terminal roof tiling stored as verb lines:**

| PP_Order_Node_ID | C_Order_ID | SeqNo | Name (verb) | Description (COBOL source) | DocStatus |
|---|---|---|---|---|---|
| 1 | SJTII_Terminal | 10 | TILE SURFACE | ROOF_DECK_Z19_WEST WITH PLATE... GRID 15 294 STEP 495 150 | DR |
| 2 | SJTII_Terminal | 20 | TILE SURFACE | ROOF_DECK_Z19_CENTRAL WITH PLATE... GRID 14 174 STEP 495 150 | DR |
| 3 | SJTII_Terminal | 30 | ARRAY | SLAB_TE_GF_001 WITH REBAR_T16 LENGTH 6000 SPACING 150 COVER 40 | DR |
| 4 | SJTII_Terminal | 40 | ROUTE SPRINKLERS | SJTII_Terminal "Departure Hall" SPACING 3000 | DR |

**Corresponding PP_Order_NodeProduct rows for PP_Order_Node_ID=1:**

| Name (param) | Value | ValueType |
|---|---|---|
| SURFACE_NAME | ROOF_DECK_Z19_WEST | TEXT |
| PRODUCT_NAME | PLATE_500x150x106 | TEXT |
| ORIGIN_X | 92.49 | REAL |
| ORIGIN_Y | -42.16 | REAL |
| ORIGIN_Z | 19.0 | REAL |
| GRID_NX | 15 | INTEGER |
| GRID_NY | 294 | INTEGER |
| STEP_DX_MM | 495 | REAL |
| STEP_DY_MM | 150 | REAL |

The `Description` column holds the human-readable text (what ScriptRunner parses). The
`PP_Order_NodeProduct` rows hold the same data in structured form (what the GUI edits).
Both stay in sync — the text is the COBOL source; the params are the form fields.

**VerbStage execution:**
1. Read `PP_Order_Node` WHERE C_Order_ID = ? AND IsActive = 1 ORDER BY SeqNo
2. For each line: dispatch to VerbRegistry by `verb_keyword`
3. Each verb reads `ad_space_type_mep_bom` for the room's space_type
4. Each verb emits IFC elements into output.db
5. Each verb produces a witness (compliance proof) → stored in `last_result` JSON
6. Update `element_count` and promote `doc_status` DR→IP→CO
7. Cross-discipline clearance checked via R-tree spatial index

This replaces `placeMEPSprinklers()`, `placeHVAC()`, `placeElectrical()`, `mepBomGapFill()` with a data-driven, declarative, provable verb pipeline.

#### C_OrderLine Separation — What Moves to Verb Tables (DECIDED 2026-03-04)

**Architectural invariant:** c_orderline is WHAT-only. No placement columns.
The Java PO/DAO class has no position setters — the compiler enforces this structurally.
Schema enforces shape. Interface enforces access. Javadoc enforces intent.

The PP_Order_Node model completes the **split** of the overloaded c_orderline table.
Placement moves to `PP_Order_Node` + `PP_Order_NodeProduct` (HOW).
Spatial containers stay in `co_empty_space_line` (WHERE).

**What stays in c_orderline (order topics):**
- building_type, storey, element_ref, ifc_class, discipline, family_ref, is_active

**What migrates to verb params (production detail):**
- host_type, host_ref, position_rule, position_value ×3, height_mm, orientation
- These become structured `PP_Order_NodeProduct` rows (ORIGIN_X, GRID_NX, SPACING_MM, etc.)

**What's already in M_Product (material attributes):**
- width_mm, height_extent_mm, depth_mm, material_name, material_rgba, geometry_hash

**CO_EmptySpaceLine stays — promoted to spatial workstation:**
- ESLine ≈ iDempiere `S_Resource` (workstation with capacity)
- Verb line targets ESLine via `co_emptyspace_line_id` FK
- Multiple verbs target the same ESLine (TILE + ARRAY + ROUTE on one slab)
- ESLine WMS columns (capacity_mm, filled_mm, remaining_mm) track verb consumption
- See `ADHistory.md` §S_Resource Parallel

**BomCategory unchanged:** Still drives template composition (BomCategoryLine.Sequence
= priority). Verbs attach downstream at the ESLine level, not at category level.

**RelationalResolver → VerbStage:** The deprecated resolver reads c_orderline's
placement columns. VerbStage reads `PP_Order_Node` by seq_no instead.
The deprecation path (NORM-3a Phase D→E) aligns with this migration.

**Migration phases** (non-breaking, coexistence):
1. **Phase 1 (current)** — Verb tables added to BOM.db. c_orderline PO class stripped of placement setters. New/generative buildings use verbs. Legacy keeps flat data temporarily.
2. **Phase 2** — VerbStage with fallback to RelationalResolver for legacy buildings.
3. **Phase 3** — Drop placement columns from c_orderline schema. Remove RelationalResolver. SH/DX migrated to verb recipes.

Full analysis: `ConstructionAsERP.md` §11.9.

> **Phase 1 — CURRENT:** Create verb tables in BOM.db (DDL above). Additive, no breakage.
> c_orderline becomes WHAT-only — Java PO class has no placement setters.
>
> **Phase 2:** VerbStage fallback logic: PP_Order_Node rows present → VerbRegistry dispatch;
> absent → RelationalResolver fallback (deprecated path).
>
> **Phase 3:** Drop placement columns from c_orderline. Remove RelationalResolver.
> Migrate SH/DX extracted data to verb recipes.
>
> **RESOLVED:** `PP_Order_Node.co_emptyspace_line_id` is the primary
> production→space link. ESLine.c_orderline_id (NORM-0b) superseded — drop in Phase 3.

---

## 16. VerbStage Integration Plan — After Last Mile

*Added v0.8 — contingent on pipeline last-mile completion (RelationalResolver cleanup, placement accuracy, ST-mode spatial switch)*

The VerbStage (§15.6) cannot land until the pipeline's coordinate chain is stable. But the language infrastructure can be built NOW, independently, so that when the pipeline is ready, VerbStage is a thin wiring layer — not a rewrite.

### 16.1 The Integration Contract

VerbStage requires exactly two capabilities the current verb framework lacks:

| Capability | Current state | Required state |
|---|---|---|
| **Write to output.db** | Verbs are read-only (CHECK/ROUTE return payloads, never write) | Verbs must emit `elements_meta` + `element_instances` rows |
| **Shared spatial index** | Each verb operates in isolation | Verbs must query an R-tree of elements placed by prior verbs (cross-discipline clearance) |

These two changes are the **integration seam**. Everything else — verb dispatch, compliance proofs, geometry computation — already works.

### 16.2 Prerequisites (Pipeline Side — NOT BIM_COBOL Work)

Before VerbStage can be wired in:

1. **Stable LocalCoord.toWorld()** — the coordinate chain must be correct. VerbStage will use the same anchor → world translation as WriteStage. If the translation has bugs (the "last mile"), VerbStage inherits them.
2. **CO_EmptySpaceLine L2 population** (TODO-ST-3) — VerbStage iterates L2 lines. These must exist for all rooms, not just structural tiers.
3. **RelationalResolver deprecated** — VerbStage computes placement from room AABB + ad_placement_rule, not from RelationalResolver. The resolver must be out of the critical path.

### 16.3 Preparation Work (BIM_COBOL Side — Can Start NOW)

Four workstreams that build language infrastructure without touching the pipeline:

**PREP-1: VerbRegistry + Dispatcher** ✅ DONE (W-COBOL-41..42)

`VerbRegistry.java` — central map of `keyword → Verb<?>` with `createDefault()` (all 12 verbs), `dispatch()` (longest-prefix match), tokenizer preserving `"quoted strings"`.

**PREP-2: ScriptRunner (Minimal)** ✅ DONE (W-COBOL-43..44)

`ScriptRunner.java` — reads `.bimcobol` text line by line, strips `--` comments and blanks, dispatches to VerbRegistry, returns `ScriptReport` with pass/fail counts and `toJson()`.

```bimcobol
-- lighting.bimcobol
WIRE LIGHTING TB_LKTN "Ground Floor" bilik_utama
WIRE LIGHTING TB_LKTN "Ground Floor" common
WIRE LIGHTING TB_LKTN "Ground Floor" bilik_mandi
ROUTE SPRINKLERS TB_LKTN "Ground Floor" bilik_utama
ROUTE SPRINKLERS TB_LKTN "Ground Floor" common
CHECK BOM FLOOR_TBLKTN_GF_STD
```

Output: ScriptReport JSON with per-line pass/fail, total witness count.

**PREP-3: Storey-Level Iteration**

Currently every MEP verb takes a single room name. Add a storey-level mode: `WIRE LIGHTING TB_LKTN "Ground Floor"` (no room_name) iterates ALL rooms on that storey via `ad_room_boundary WHERE storey = ?`. Returns an aggregate payload with per-room results. This is exactly the loop VerbStage will execute — building it as a verb feature means VerbStage's per-storey logic is already tested.

**PREP-4: More MEP Verbs (Expand Coverage)**

Each additional verb is more pipeline code that VerbStage can eventually replace:

| Verb | Pattern | DB source | Rosetta Stone evidence |
|---|---|---|---|
| `PLACE OUTLETS` | Wall-mount grid at fixed height | ad_space_type_mep.power_points | 47 Duplex receptacles @ z=0.46m |
| `PLACE SWITCHES` | Wall-mount near door entry | ad_space_type_mep.switch_points | 14 Duplex switches @ z=1.22m |
| `ROUTE DUCTS` | Ceiling grid + velocity sizing | ad_fp_coverage (duct variant) | 568 Terminal ducts |

### 16.4 Integration Sequence (After Last Mile)

When the pipeline is stable, VerbStage integration is a 3-step process:

```
Step 1: VerbStage shell
  - New stage in CompilationPipeline.STAGES after WriteStage
  - Receives output.db Connection + BOM.db Connection
  - Iterates co_empty_space_line WHERE bom_level = 2 (rooms)
  - For each room: loads space_type, queries ad_space_type_mep_bom
  - Dispatches to VerbRegistry per placement_rule

Step 2: Verb write mode
  - VerbContext gains outputConn (output.db, writable)
  - Each verb's payload → elements_meta + element_instances INSERTs
  - Shared R-tree accumulates elements across verbs (clearance queries)
  - VerbResult carries both the compliance proof AND the element count emitted

Step 3: Pipeline replacement
  - Remove placeMEPSprinklers() → ROUTE SPRINKLERS handles it
  - Remove placeElectrical()    → WIRE LIGHTING handles it
  - Remove placeHVAC()          → ROUTE DUCTS handles it
  - Remove mepBomGapFill()      → PLACE OUTLETS + PLACE SWITCHES handle it
  - ProveStage reads verb witnesses instead of running its own compliance checks
```

Each step is independently testable. Step 1 is a skeleton that dispatches but doesn't write. Step 2 adds writes. Step 3 removes the old code. The pipeline never has two paths doing the same thing — the verb path replaces the hardcoded path, it doesn't run alongside it.

### 16.5 Success Criterion

`SpatialDigest(SH_with_VerbStage) == SpatialDigest(SH_without_VerbStage)`

Same building, same geometry, same BOM — but MEP elements placed by BIM COBOL verbs instead of hardcoded Java methods. The digest proves the replacement is exact. This is the same Rosetta Stone strategy used throughout the project: prove equivalence on known-good buildings before extending to new ones.

---

## 17. Proposed Construction Quality Verbs — Post-Compilation Geometric Operations

*Added v0.10 — proposed for next session. These verbs address visual defects observed in SH/DX output: wall-roof intersection, furniture clash, missing elements (piano, door), window geometry.*

### 17.1 The Problem: No Post-Emission Geometry Operations

The current pipeline places elements and writes them. There is no stage that reconciles geometric conflicts between independently-placed elements. Walls don't know about roofs. Furniture doesn't know about other furniture. This produces visual defects that are not data errors but **missing construction operations**.

### 17.2 Level 1 — Witness Verbs (Check Only, No Mutation)

These verbs query the output DB and report defects. They can be implemented immediately as read-only checks against `elements_meta` + `elements_rtree`.

```bimcobol
CHECK CLASH FURNITURE IN ROOM "living"
  -- Detects overlapping furniture bounding boxes within a room
  -- Reports: element pairs, overlap volume, severity
  -- Source: elements_rtree AABB intersection query

CHECK VISIBILITY DOORS
  -- Flags doors with zero-extent AABB or no geometry_hash
  -- Reports: door GUID, dimensions, parent wall
  -- Source: elements_meta WHERE ifc_class='IfcDoor' + base_geometries JOIN

CHECK VISIBILITY WINDOWS
  -- Flags windows with degenerate geometry or missing LOD_Object
  -- Reports: window GUID, geometry_hash present/absent
  -- Source: element_instances LEFT JOIN base_geometries

CHECK CONTAINMENT WINDOWS IN WALLS
  -- Verifies each window AABB intersects exactly one wall AABB
  -- Reports: orphaned windows, windows intersecting zero or >1 walls
  -- Source: elements_rtree spatial join (window ∩ wall)

VERIFY ROOF COVERAGE
  -- Checks all wall tops are below roof drip line at their XY position
  -- Reports: walls protruding above roof surface, gap distance
  -- Source: elements_rtree WHERE ifc_class LIKE 'IfcWall%' vs 'IfcRoof%'

CHECK GEOMETRY BINDING
  -- Flags elements where element_instances.geometry_hash has no base_geometries row
  -- These are GEN-BOX fallbacks — the "big box" symptom
  -- Reports: element GUID, expected product, missing geometry_hash
  -- Source: element_instances LEFT JOIN base_geometries WHERE vertices IS NULL
```

**Implementation:** Each verb is a SQL query + result formatter. No geometry engine needed. Can be witnesses (W-QUALITY-1..6) with GREEN/RED gate.

### 17.3 Level 2 — Construction Verbs (Geometric Mutation)

These verbs modify geometry in the output DB. They require a geometry engine (mesh boolean or half-plane clip).

```bimcobol
TRIM WALLS TO ROOF PROFILE
  -- Boolean subtract: for each wall whose maxZ exceeds roof surface at (wall.X, wall.Y),
  -- clip wall vertices to roof plane. Roof = set of planar faces; wall = extruded rectangle.
  -- This is the single highest-value geometric verb for pitched-roof buildings.
  -- Affects: SH (gable roof), any building with non-flat roof
  -- Implementation: half-plane clip per roof face (simpler than full boolean)

CUT OPENINGS FOR DOORS IN WALLS
  -- Boolean subtract: wall geometry minus door void
  -- Currently doors are placed as separate elements — the wall behind them is solid
  -- This verb removes wall material where doors exist
  -- Implementation: rectangular hole punch (axis-aligned, simpler than arbitrary boolean)

EXTEND WALLS TO SLAB ABOVE
  -- Extends wall maxZ to meet underside of slab/floor above
  -- Closes gap between wall top and next floor plate
  -- Implementation: adjust wall vertex Z values (no boolean needed)
```

**Implementation:** Requires vertex manipulation on `base_geometries` BLOB data. `TRIM WALLS TO ROOF` is the priority — it addresses the most visible SH defect.

### 17.4 Level 3 — Reconciliation Verbs (Cross-Element Coordination)

These compose Level 1 + Level 2 verbs into multi-step operations.

```bimcobol
SEAL ENVELOPE
  -- Sequence: EXTEND WALLS TO SLAB ABOVE, then TRIM WALLS TO ROOF PROFILE
  -- Result: all walls meet adjacent horizontal surfaces with no gaps or protrusions

RESOLVE CLASHES BY PRIORITY
  -- Sequence: CHECK CLASH, then for each overlap:
  --   higher-priority element (structural > furniture) keeps position
  --   lower-priority element adjusts or is flagged for manual review
  -- Uses: M_Product_Category hierarchy (STR > ARC > MEP > FURN)

VALIDATE ASSEMBLY COMPLETENESS
  -- For each element_assembly: verify all required components are present
  -- Stair assembly must have: flight + landing + railing
  -- Door assembly must have: leaf + frame
  -- Reports: incomplete assemblies, missing component roles
```

### 17.5 Diagnostic: Singular vs Exploded Output Comparison

To diagnose whether defects originate from extraction data or BOM explosion, the pipeline should support producing two variant outputs per building:

| Suffix | Mode | BOM data source | Compilation |
|--------|------|-----------------|-------------|
| `_s` | Singular (EN-BLOC) | Flat EXTRACTED BOMs (EXT_SH/EXT_DX, all BUY) | Takes one BOM whole — hello-world POC |
| `_e` | Walk Thru (WALK THRU) | Structured UNIT BOMs (UNIT → FLOOR → SET → BUY) | Walks hierarchy — production target |

**Comparison method:**
```sql
-- Elements in reference but missing in compiled (dropped by pipeline)
SELECT r.guid, r.ifc_class FROM ref.elements_meta r
  LEFT JOIN compiled.elements_meta c ON r.guid = c.guid
  WHERE c.guid IS NULL;

-- Elements in compiled but not in reference (invented by pipeline)
SELECT c.guid, c.ifc_class FROM compiled.elements_meta c
  LEFT JOIN ref.elements_meta r ON c.guid = r.guid
  WHERE r.guid IS NULL;

-- Position drift between reference and compiled
SELECT r.guid, r.ifc_class,
  ABS(r.minX - c.minX) + ABS(r.minY - c.minY) + ABS(r.minZ - c.minZ) as drift_mm
FROM ref.elements_rtree r JOIN compiled.elements_rtree c ON r.id = c.id
WHERE drift_mm > 1.0
ORDER BY drift_mm DESC;
```

**A defect appearing in both `_s` and `_e`** = extraction/geometry data problem.
**A defect appearing only in `_e`** = BOM walk or assembly structure problem.
**A defect appearing only in `_s`** = reference DB has it but compiler drops it.

### 17.6 Implementation Priority

| Priority | Verb | Effort | Impact | Blocks |
|----------|------|--------|--------|--------|
| P0 | `_s` / `_e` diagnostic outputs | LOW | HIGH | Nothing — diagnostic only |
| P1 | CHECK GEOMETRY BINDING | LOW | HIGH | Identifies all GEN-BOX fallbacks (piano, door, staircase) |
| P2 | CHECK CLASH FURNITURE | LOW | MEDIUM | Witness for furniture arrangement quality |
| P3 | VERIFY ROOF COVERAGE | LOW | MEDIUM | Witness for wall-roof intersection |
| P4 | TRIM WALLS TO ROOF PROFILE | HIGH | HIGH | Requires mesh vertex manipulation |
| P5 | SEAL ENVELOPE | HIGH | HIGH | Composes P4 + wall extension |

### 17.7 M_AttributeSet as BOM-Level Verb Dispatch — Design Plan

*Added v0.10 — future feature. M_AttributeSet becomes the link between product type classification and automatic verb dispatch.*

#### The Idea

Today M_AttributeSet classifies products by instance variability: a `BIM_Wall` product has instance-specific length/height (IsInstanceAttribute=1), while a `BIM_Component` is identical everywhere (=0). But the attribute set also implies **construction verbs**: every wall needs trimming to the roof profile. Every pipe needs fittings at junctions. Every slab might need openings cut for MEP penetrations. The product type *is* the construction recipe.

#### Current State

```
M_AttributeSet (BOM.db, 5 rows)
  BIM_Wall      → 10 products (Wall types)
  BIM_Slab      → 8 products  (Floor slab types)
  BIM_Pipe      → 9 products  (MEP pipe types)
  BIM_Conduit   → 1 product   (Electrical conduit)
  BIM_Component → 62 products (Discrete items — no instance variation)
```

M_Product.M_AttributeSet_ID is already an FK. Every product knows its type. But the pipeline never reads M_AttributeSet to decide what to DO with a product after placement.

#### Proposed Extension

**Step 1: M_AttributeSet_Verb junction table (BOM.db)**

```sql
CREATE TABLE M_AttributeSet_Verb (
    M_AttributeSet_ID  TEXT NOT NULL REFERENCES M_AttributeSet(M_AttributeSet_ID),
    verb_keyword       TEXT NOT NULL,  -- VerbRegistry keyword: "TRIM WALLS TO ROOF PROFILE"
    SeqNo              INTEGER NOT NULL DEFAULT 10,  -- execution order within the set
    condition_sql      TEXT,  -- optional SQL predicate (e.g. "roof_type != 'FLAT'")
    is_active          INTEGER DEFAULT 1,
    PRIMARY KEY (M_AttributeSet_ID, verb_keyword)
);
```

**Seed data:**

| M_AttributeSet_ID | verb_keyword | SeqNo | condition_sql |
|----|----|----|---|
| BIM_Wall | EXTEND WALLS TO SLAB ABOVE | 10 | NULL |
| BIM_Wall | TRIM WALLS TO ROOF PROFILE | 20 | roof_type != 'FLAT' |
| BIM_Wall | CUT OPENINGS FOR DOORS IN WALLS | 30 | NULL |
| BIM_Slab | CUT OPENINGS FOR MEP PENETRATIONS | 10 | NULL |
| BIM_Pipe | CONNECT FITTINGS | 10 | NULL |
| BIM_Pipe | ROUTE SPRINKLERS | 20 | discipline = 'FIRE' |
| BIM_Conduit | WIRE LIGHTING | 10 | NULL |

**Step 2: VerbStage reads M_AttributeSet_Verb**

After element placement, VerbStage queries which attribute sets are present in the building's BOM:

```java
// Collect distinct M_AttributeSet_IDs from placed elements
SELECT DISTINCT p.M_AttributeSet_ID
FROM elements_meta em
JOIN M_Product p ON em.product_id = p.product_id
WHERE p.M_AttributeSet_ID IS NOT NULL;

// For each attribute set, look up associated verbs
SELECT verb_keyword, condition_sql
FROM M_AttributeSet_Verb
WHERE M_AttributeSet_ID = ? AND is_active = 1
ORDER BY SeqNo;

// Dispatch each verb via VerbRegistry
```

**Step 3: Per-building verb scripts become automatic**

Instead of hand-writing `.bimcobol` scripts per building, VerbStage generates the verb sequence from the BOM content. A building with walls and a pitched roof automatically gets TRIM + CUT. A building with only slabs and MEP gets penetration cutting. The construction recipe follows from WHAT is placed, not from a script that must be maintained separately.

#### What This Enables

The `.bimcobol` script remains for **overrides** and **custom sequences**. But the default pipeline is: "look at what products are placed → look up their construction verbs → execute in SeqNo order." This is the iDempiere Manufacturing pattern: PP_Order_BOM drives PP_Order_Node. Here, M_AttributeSet_Verb drives automatic PP_Order_Node generation.

**Versatile fine construction** = new verbs can be added to any attribute set at any time. Adding a `FLASH WALL-ROOF JUNCTION` verb to `BIM_Wall` instantly applies it to all 10 wall products in all buildings. No code change, no per-building script edit.

#### Dependencies

- §17.3 Level 2 verbs must exist first (TRIM, CUT, EXTEND need geometry engines)
- VerbStage SPI integration already works (BIM_COBOL provides VerbExecutor)
- PP_Order_Node persistence already works (VerbNodePersister)
- M_Product.M_AttributeSet_ID FK already populated

#### Priority

Phase F (BIM COBOL v1.0). After TRIM WALLS TO ROOF PROFILE (P4) is implemented as a standalone verb, wire it through M_AttributeSet_Verb for automatic dispatch.

---

## 18. Synthetic BOM Creation — The Composition Language

*Added v0.11 — Phase F0.2. This section defines the verb suite for creating new BOM assemblies from the catalog. Where §4–5 define verbs that PLACE and CHECK elements, and §15–17 define verbs that operate on compiled output, this section defines verbs that CREATE the BOM data itself — the input to compilation.*

### 18.1 The Problem: BOM Creation Is Manual SQL

Today, creating a new building type requires hand-writing SQL INSERT statements for `m_bom` + `m_bom_line` rows. A new terrace house variant means 30–50 INSERT statements, carefully maintaining parent-child references, allocated dimensions, tack offsets, and category codes. This is the assembler-level problem applied to BOM authoring.

`BomTemplateComposer` (§3.2, TemplateStage) already automates SELECTION — given an AABB and room grammar, it picks best-fit BOMs from the catalog. But it does not CREATE new BOMs. It selects from what exists. The missing piece: verbs that materialise selections into `m_bom` + `m_bom_line` rows, composable with each other and with the GUI.

### 18.2 The Bonsai Creator Pipeline

In the Bonsai GUI, the user draws a box on screen. That box becomes the AABB input to composition. The pipeline:

```
┌─────────────────────────────────────────────────────────────────┐
│  BONSAI GUI                                                     │
│  ┌─────────┐   ┌──────────┐   ┌──────────────┐   ┌──────────┐ │
│  │ Draw Box │──▶│ SNAP TO  │──▶│ EXTRACT AABB │──▶│ BIM COBOL│ │
│  │ (mouse)  │   │ GRID     │   │ (util verb)  │   │ verb     │ │
│  └─────────┘   └──────────┘   └──────────────┘   └──────────┘ │
│       ▲                                                │        │
│       │              feedback loop                     │        │
│       └────────────── DESCRIBE BOM ◀───────────────────┘        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  BOM.db                                                         │
│  m_bom ◀──── new rows                                          │
│  m_bom_line ◀──── new children                                  │
│  m_bom_category_line ◀──── new template rules (Level 4 only)   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  COMPILATION PIPELINE                                           │
│  PLACE BOM ──▶ output.db ──▶ SUMMARIZE BUILDING ──▶ Bonsai     │
└─────────────────────────────────────────────────────────────────┘
```

The key insight: **the GUI emits BIM COBOL statements, not direct SQL.** Every GUI action maps to a verb. The verb validates, materialises, and returns a payload. The GUI renders the payload as feedback. This is the Bonsai-to-compiler contract.

### 18.3 The Foundational Insight: BOM Metadata IS the Instruction Set

The DX duplex mirroring is the proof. There is no MIRROR code anywhere in the pipeline. The BOMWalker reads `rotation_rule=3.14159` from `m_bom_line` and rotates the child. Party-wall mirroring, terrace row alternation, corner-lot rotation — all expressed as a single float on a BOM line. The tack model (`dx/dy/dz + rotation_rule + allocated_width/depth/height`) is already a complete spatial instruction set.

**Consequence:** the language does not need domain-specific verbs like MIRROR UNIT. What it needs are **BOM primitive verbs** — raw m_bom/m_bom_line manipulation — and **convenience verbs** that compose sequences of primitives. The convenience verbs are sugar. The primitives are the machine.

```
Higher-level verb                BOM primitives it decomposes into
─────────────────                ─────────────────────────────────
"Mirror a half-unit"         →   ADD LINE parent=SET child=UNIT rotation_rule=pi
"Stack a floor at 3m"        →   SET TACK line=SLAB_L2 dz=3.0
"Place 7 cabinets in row"   →   7× ADD LINE child=Cabinet dx=n*0.6
"Resize room to 4m wide"    →   SET DIMENSIONS line=ROOM allocated_width_mm=4000
"Create a duplex pair"       →   CREATE BOM type=SET
                                 ADD LINE child=UNIT_A rotation_rule=0
                                 ADD LINE child=UNIT_B rotation_rule=pi
```

This is the same pattern as ERP manufacturing (§18.3.1): the BOM IS the work order.

#### 18.3.1 The ERP Manufacturing Parallel

The BIM COBOL verb architecture maps directly to the iDempiere Manufacturing module. This is not a metaphor — it is the same data model applied to spatial composition.

```
iDempiere Manufacturing              BIM COBOL
────────────────────                 ─────────
PP_Product_BOM                  →    m_bom (the recipe)
PP_Product_BOM_Line             →    m_bom_line (each component)
PP_Product_BOM_Line.QtyBOM      →    m_bom_line.sequence (repeat count)
PP_Product_BOM_Line.ComponentType →  m_bom_line.component_type (BUY/MAKE/PHANTOM)
PP_Order                        →    C_Order (the work order — "build this building")
PP_Order_BOM                    →    C_OrderLine (copy of recipe for this specific build)
PP_Order_Node                   →    PP_Order_Node (operations — verb invocations)
M_Product                       →    M_Product (the item being made or consumed)
M_Warehouse / M_Locator        →    CO_EmptySpace (spatial inventory — where things go)
```

**What BIM adds that ERP doesn't have:**
- `dx/dy/dz` — spatial tack offset (ERP has no concept of "where in the warehouse to place a component")
- `rotation_rule` — orientation (ERP components don't rotate)
- `allocated_width/depth/height_mm` — spatial envelope (ERP has weight/volume but not bounding box)

These three extensions — tack, rotation, envelope — are what turn a manufacturing BOM into a spatial BOM. The rest of the model (product tree, component types, work orders, operations) is identical.

**What this means for the language:**
- BOM primitive verbs are the **PP_Product_BOM_Line** operations: CREATE, ADD, SET, REMOVE
- Convenience verbs are the **PP_Order** operations: COMPOSE (= create work order from recipe)
- Template verbs are the **AD_Table/AD_Column** operations: DEFINE, ADD RULE (= extend the dictionary)

The ERP Manufacturing module has been proven in production for 20+ years. BIM COBOL inherits that stability. The spatial extensions (tack, rotation, envelope) are the only novel additions — and they are already proven by the SH/DX Rosetta Stones.

### 18.4 Level 0 — BOM Primitive Verbs (The Machine Layer)

These are the atomic operations on `m_bom` and `m_bom_line`. Every higher-level verb decomposes into a sequence of these primitives. They are the "assembler" of the BOM composition language.

#### CREATE BOM

Create a new `m_bom` row. The fundamental building block.

```bimcobol
CREATE BOM SY_KITCHEN_A TYPE SET CATEGORY KT
    -- Creates m_bom: bom_id=SY_KITCHEN_A, bom_type=SET, bom_category=KT
    -- No children yet — an empty container

CREATE BOM SY_FLOOR_GF TYPE FLOOR CATEGORY L1
CREATE BOM SY_UNIT_01 TYPE UNIT CATEGORY RE DOC_SUB_TYPE SY
```

**Writes to BOM.db:** 1 `m_bom` row.
**Payload:** `CreateBomPayload(bomId, bomType, bomCategory, docSubType)`

#### ADD LINE

Add a child to a BOM. This is the core composition operation — one `m_bom_line` row.

```bimcobol
ADD LINE TO SY_KITCHEN_A CHILD Base_Cabinet ROLE BASE_CABINET SEQ 10
    -- Adds m_bom_line: parent=SY_KITCHEN_A, child=Base_Cabinet

ADD LINE TO SY_KITCHEN_A CHILD Base_Cabinet ROLE BASE_CABINET_2 SEQ 20 DX 0.6
    -- Second cabinet, offset 600mm to the right

ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_A SEQ 10 ROTATION 0
ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_B SEQ 20 ROTATION 3.14159
    -- THIS is how you "mirror" a duplex. No MIRROR verb. Just BOM data.
```

**Writes to BOM.db:** 1 `m_bom_line` row.
**Payload:** `AddLinePayload(bomId, childProductId, role, sequence, dx, dy, dz, rotationRule)`

#### SET TACK

Update the spatial offset (dx/dy/dz) on an existing `m_bom_line`. This is how elements move.

```bimcobol
SET TACK ON SY_UNIT_01 LINE ROOF_ASSEMBLY DZ 6.0
    -- Roof sits 6m above ground

SET TACK ON SY_FLOOR_GF LINE SH_LIVING_SET DX 2.5 DY 1.0
    -- Living room offset from floor origin
```

**Writes to BOM.db:** Updates `m_bom_line.dx/dy/dz`.
**Payload:** `SetTackPayload(bomId, childProductId, role, dx, dy, dz)`

#### SET ROTATION

Update the rotation_rule on an existing `m_bom_line`. This is how elements orient — including the DX party-wall mirror.

```bimcobol
SET ROTATION ON SY_DUPLEX_SET LINE UNIT_B TO 3.14159
    -- Party wall mirror — 180° rotation about Z axis. That's it.
    -- The BOMWalker reads this and applies it during traversal.
    -- No MIRROR verb. No mirroring code. Just a float on a BOM line.

SET ROTATION ON SY_TERRACE_ROW LINE UNIT_3 TO 1.5708
    -- 90° corner lot rotation
```

**Writes to BOM.db:** Updates `m_bom_line.rotation_rule`.
**Payload:** `SetRotationPayload(bomId, childProductId, role, rotationRule)`

#### SET DIMENSIONS

Update the allocated envelope (width/depth/height) on a `m_bom_line`. This is how rooms resize.

```bimcobol
SET DIMENSIONS ON SY_FLOOR_GF LINE SY_KITCHEN_A WIDTH 3500 DEPTH 2500 HEIGHT 2800
    -- Kitchen slot is 3.5m × 2.5m × 2.8m

SET DIMENSIONS ON SY_UNIT_01 LINE SY_FLOOR_GF WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Ground floor occupies 10m × 8m × 2.8m within the unit
```

**Writes to BOM.db:** Updates `m_bom_line.allocated_width_mm/depth_mm/height_mm`.
**Payload:** `SetDimensionsPayload(bomId, childProductId, role, widthMm, depthMm, heightMm)`

#### SET LINE PROPERTY

General-purpose property setter for any `m_bom_line` column.

```bimcobol
SET LINE PROPERTY ON SY_KITCHEN_A LINE Base_Cabinet COMPONENT_TYPE BUY
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A ROLE KITCHEN
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A LOCATOR_REF NORTH_WALL
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A ORIENTATION EAST
```

**Writes to BOM.db:** Updates specified column on `m_bom_line`.
**Payload:** `SetPropertyPayload(bomId, childProductId, property, value)`

#### REMOVE LINE

Delete a child from a BOM.

```bimcobol
REMOVE LINE FROM SY_KITCHEN_A CHILD Upper_Cabinet_4
    -- Deletes one m_bom_line row

REMOVE LINE FROM SY_KITCHEN_A ROLE BUFFER
    -- Deletes by role
```

**Writes to BOM.db:** Deletes 1 `m_bom_line` row.
**Payload:** `RemoveLinePayload(bomId, removedChild, removedRole)`

#### DELETE BOM

Delete a BOM and all its children lines. Cascading delete.

```bimcobol
DELETE BOM SY_KITCHEN_A
    -- Deletes m_bom row + all m_bom_line rows where bom_id=SY_KITCHEN_A
    -- FAIL if any other BOM references SY_KITCHEN_A as a child (referential integrity)
```

**Writes to BOM.db:** Deletes 1 `m_bom` row + N `m_bom_line` rows.
**Payload:** `DeleteBomPayload(bomId, deletedLineCount)`

#### Why Primitives Matter

Every higher-level verb in §18.6–18.10 is a **macro** over these 8 primitives. The implementation can be layered:

1. **Primitives** (Level 0): direct m_bom/m_bom_line CRUD via DAO
2. **Convenience verbs** (Level 1–5): call primitives in validated sequences
3. **GUI actions**: emit convenience verbs (or primitives directly for power users)

This means: if a convenience verb doesn't exist for a use case, the user can always drop to primitives. And every new convenience verb is just a new composition of existing primitives — no new infrastructure required.

### 18.5 Utility Verbs — Input Preparation

These verbs transform raw coordinates into BOM-ready parameters. They bridge the gap between GUI-drawn geometry and the AABB inputs that composition verbs consume.

#### EXTRACT AABB

Compute axis-aligned bounding box from coordinates. This is what turns a Bonsai drag-box into verb parameters.

```bimcobol
EXTRACT AABB FROM POINTS (0,0,0) (12.0,10.0,6.0)
    -- Output: WIDTH 12000 DEPTH 10000 HEIGHT 6000 (mm)

EXTRACT AABB FROM ROOM "living" IN output.db
    -- Reads elements_rtree for the named room
    -- Output: WIDTH 4871 DEPTH 3943 HEIGHT 2800

EXTRACT AABB FROM BOM BUILDING_SH_STD
    -- Reads allocated dimensions from m_bom root
    -- Output: WIDTH 16868 DEPTH 8668 HEIGHT 3945
```

**Payload:** `AabbPayload(widthMm, depthMm, heightMm, minX, minY, minZ, maxX, maxY, maxZ)`

This payload feeds directly into COMPOSE BUILDING, CREATE FLOOR, CREATE ROOM, and RESIZE ROOM as their AABB parameters.

#### SNAP TO GRID

Align arbitrary coordinates to the structural grid. Essential for Bonsai: the user drags freely, the verb snaps to the nearest grid intersection.

```bimcobol
SNAP TO GRID (3.7, 5.2) SPACING 1000
    -- Output: (4000, 5000)

SNAP TO GRID AABB 11500 9700 SPACING 500
    -- Output: WIDTH 11500 DEPTH 10000
    -- Width already on grid, depth snapped up to next 500
```

**Payload:** `GridSnapPayload(snappedWidthMm, snappedDepthMm, snappedHeightMm, gridSpacingMm, adjustments[])`

The snap operation always rounds UP to ensure the AABB encloses the drawn box. `adjustments[]` records what changed, so the GUI can highlight snap corrections.

#### PARTITION AABB

Subdivide an AABB into room-sized slots. This is the 1D-to-rooms conversion: a building envelope becomes a list of rooms that fit within it.

```bimcobol
PARTITION AABB 12000 10000 6000 INTO ROOMS LI DN KT BD BT
    -- Uses M_BomCategoryLine min/max constraints
    -- Allocates width proportional to category typical ratios
    -- Output: 5 room AABBs that tile the floor plan

PARTITION AABB 12000 10000 6000 FLOORS 2 HEIGHT_EACH 2800
    -- Splits vertically: 2 floors of 2800mm + slab allowance
    -- Output: 2 floor AABBs stacked at dz=0 and dz=3000
```

**Payload:** `PartitionPayload(slots[], totalWidth, totalDepth, wastePercent)`

Each slot has: `(categoryId, allocWidthMm, allocDepthMm, allocHeightMm, offsetX, offsetY, offsetZ)`. The verb does NOT create BOMs — it computes the spatial layout that CREATE FLOOR and CREATE ROOM will consume. This separation lets the GUI show the partition for user approval before materialising.

#### VALIDATE AABB

Check if an AABB is viable for a given category. Catches impossible rooms before creation.

```bimcobol
VALIDATE AABB 1500 1200 2800 FOR KITCHEN
    -- FAIL: minimum kitchen width is 1800mm (M_BomCategoryLine.min_width_mm)

VALIDATE AABB 5000 4000 2800 FOR BEDROOM
    -- OK: within range, 3 candidate BOMs in catalog
    -- Returns: candidateCount=3, bestFit=BED_SET_MASTER
```

**Payload:** `ValidationPayload(valid, categoryId, candidateCount, bestFitBomId, minWidth, maxWidth, message)`

### 18.6 Level 1 — Room-Level Verbs (Leaf Creation)

These create or modify SET-level BOMs — the rooms that contain furniture and fixtures. A room SET is the basic reusable unit: `SH_LIVING_SET`, `KITCHEN_CABINET_SET`, `BED_SET`. Each is an `m_bom` with `m_bom_line` children pointing to leaf products.

#### CREATE ROOM

Create a new room SET BOM. The verb finds best-fit leaf products from the catalog and populates `m_bom_line` rows.

```bimcobol
CREATE ROOM KITCHEN 3500 2500 2800
    -- Finds catalog kitchen products that fit 3500x2500x2800 AABB
    -- Creates m_bom: bom_id=KITCHEN_3500x2500, bom_category=KT
    -- Creates m_bom_line: Base_Cabinet x5, Upper_Cabinet x3, Counter_Top, Sink
    -- Selection: AABB fit → largest volume → seq_no tiebreaker (§3.3)

CREATE ROOM BEDROOM 4000 3000 2800 FROM DX
    -- Prefer DX-owned products (doc_sub_type filter)
    -- Falls back to catalog-wide if no DX match

CREATE ROOM LIVING 8000 5000 2800 EMPTY
    -- Creates m_bom with zero children — a slot for manual population
    -- The GUI uses this + FURNISH ROOM to populate interactively
```

**Writes to BOM.db:** 1 `m_bom` row + N `m_bom_line` rows.
**Payload:** `CreateRoomPayload(bomId, category, childCount, buyCount, makeCount, phantomCount, wasteVolume)`

#### FURNISH ROOM

Add leaf products to an existing room SET. The selection cascade picks best-fit items from component_library.db.

```bimcobol
FURNISH ROOM SH_LIVING_SET WITH SOFA DESK LAMP
    -- For each product name, finds M_Product in catalog
    -- Computes tack position (dx/dy/dz) from room AABB and placement rules
    -- Appends m_bom_line rows to existing SET

FURNISH ROOM NEW_KITCHEN_SET WITH Base_Cabinet COUNT 7 ALONG NORTH_WALL
    -- Places 7 base cabinets along the north wall face
    -- dx increments by cabinet width, dy=0 (against wall)
```

**Writes to BOM.db:** N `m_bom_line` rows appended to existing `m_bom`.
**Payload:** `FurnishPayload(bomId, addedCount, placedProducts[], unplacedProducts[])`

`unplacedProducts[]` lists items that didn't fit — the room is full. This feedback tells the GUI to flag overflow.

#### RESIZE ROOM

Clone a room SET with a new AABB. Products that no longer fit are dropped; products that now have space may be upgraded to larger variants.

```bimcobol
RESIZE ROOM SH_BED_SET TO 4000 3500 2800 AS BED_SET_LARGE
    -- Clones SH_BED_SET as BED_SET_LARGE
    -- Re-runs selection cascade per child with new AABB
    -- BED (2007x1800) still fits → kept
    -- DESK (1563x819) still fits → kept
    -- Room has extra space → BUFFER adjusted

RESIZE ROOM KITCHEN_CABINET_SET TO 2000 2000 2800
    -- Some cabinets won't fit → dropped with warning
    -- Payload reports: droppedProducts=["Upper_Cabinet_4"]
```

**Writes to BOM.db:** 1 new `m_bom` + N `m_bom_line` rows (clone with modifications).
**Payload:** `ResizePayload(newBomId, keptCount, droppedProducts[], upgradedProducts[])`

#### STRIP ROOM

Remove children from a room SET by role or type. Leaves the container for repopulation.

```bimcobol
STRIP ROOM SH_LIVING_SET
    -- Removes all m_bom_line rows. BOM becomes empty container.

STRIP ROOM SH_LIVING_SET KEEP STRUCTURE
    -- Removes BUY items (furniture) but keeps MAKE sub-assemblies (wall panels, etc.)

STRIP ROOM SH_LIVING_SET ROLE BUFFER
    -- Removes only PHANTOM/BUFFER items
```

**Writes to BOM.db:** Deletes `m_bom_line` rows from existing `m_bom`.
**Payload:** `StripPayload(bomId, removedCount, remainingCount)`

### 18.7 Level 2 — Floor-Level Verbs (Room Composition)

These create FLOOR BOMs by composing rooms into a storey. A floor is an `m_bom` whose children are room SETs, each with allocated dimensions and tack offsets.

#### CREATE FLOOR

Create a new floor BOM from a room list. Allocates AABB to each room and creates `m_bom_line` entries. Uses PARTITION AABB internally to compute spatial layout.

```bimcobol
CREATE FLOOR GF ROOMS LI DN KT BD BT WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Partitions 10000x8000 into 5 rooms
    -- Each room: finds best-fit SET from catalog
    -- Creates m_bom: bom_id=FLOOR_GF_10000x8000, bom_type=FLOOR
    -- Creates m_bom_line per room with dx/dy/dz offsets

CREATE FLOOR L2 ROOMS BD BD BD BT KT WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Upper floor: 3 bedrooms + bathroom + kitchen
    -- Same mechanism, different room mix
```

**Writes to BOM.db:** 1 `m_bom` + N `m_bom_line` rows.
**Payload:** `CreateFloorPayload(bomId, roomCount, rooms[], wastePercent, totalArea)`

Each room entry: `(categoryId, selectedBomId, allocW, allocD, allocH, dx, dy, dz)`.

#### ADD ROOM

Insert a room slot into an existing floor BOM. Finds best-fit from catalog.

```bimcobol
ADD ROOM BD TO FLOOR_SH_GF_STD
    -- Checks remaining AABB space in floor
    -- If space exists: selects best-fit BD BOM, appends m_bom_line
    -- If no space: FAIL with "floor is full" message

ADD ROOM KT TO FLOOR_DX_L2_STD AT 3000 0 0
    -- Explicit tack position override (user placed via GUI)
```

**Writes to BOM.db:** 1 `m_bom_line` row appended.
**Payload:** `AddRoomPayload(floorBomId, addedRoom, remainingWidth, remainingDepth)`

#### REMOVE ROOM

Drop a room from a floor BOM by role or product ID.

```bimcobol
REMOVE ROOM DN FROM FLOOR_DX_L2_STD
    -- Deletes m_bom_line where role='DINING'
    -- Reclaims AABB space for future ADD ROOM

REMOVE ROOM KITCHEN_CABINET_SET FROM FLOOR_DX_L1_STD
    -- By specific product ID
```

**Writes to BOM.db:** Deletes 1 `m_bom_line` row.
**Payload:** `RemoveRoomPayload(floorBomId, removedRole, reclaimedWidth, reclaimedDepth)`

#### SWAP ROOM

Replace one room variant with another. The new room must fit the allocated AABB.

```bimcobol
SWAP ROOM KT IN FLOOR_DX_L1_STD WITH KITCHEN_CABINET_SET_DX_A
    -- Validates KITCHEN_CABINET_SET_DX_A fits the KT slot
    -- Updates m_bom_line.child_product_id

SWAP ROOM BD IN FLOOR_DX_L2_STD WITH BED_SET_MASTER
    -- Upgrade: standard bedroom → master bedroom
```

**Writes to BOM.db:** Updates 1 `m_bom_line` row (child_product_id).
**Payload:** `SwapPayload(floorBomId, role, previousBomId, newBomId, fitMargin)`

### 18.8 Level 3 — Unit-Level Verbs (Building Composition)

These create BUILDING BOMs — complete buildings from floors, structural elements, and MEP.

#### COMPOSE BUILDING

The main event. Given AABB + docType + numUnits, runs `BomTemplateComposer` and materialises the selections into `m_bom` + `m_bom_line` rows.

```bimcobol
COMPOSE BUILDING RESIDENTIAL 12000 10000 6000 UNITS 1
    -- Single-storey house. Walks RE→GF→{LI,DN,KT,BD,BT} template.
    -- Creates: SY_RE_12000x10000 (BUILDING BOM)
    --   → FLOOR_SLAB_GF (structural)
    --   → FLOOR_GF_12000x10000 (room content)
    --     → selected LIVING_SET, DINING_SET, KITCHEN_SET, BED_SET, BATHROOM_SET
    --   → ROOF_ASSEMBLY (dz=3000)
    -- Every m_bom_line has computed dx/dy/dz tack offsets

COMPOSE BUILDING RESIDENTIAL 10000 8000 6000 UNITS 2
    -- Duplex. Walks RE→PR→2×HU→{L1,L2}→rooms.
    -- Creates mirrored pair (rotation_rule=pi on UNIT_B)
    -- Width split by 2 for each half-unit

COMPOSE BUILDING COMMERCIAL 40000 30000 16000 UNITS 1 FLOORS 4
    -- Commercial building (future — when CO categories exist)
    -- Walks CO template: lobby, office floors, services
```

**Writes to BOM.db:** 1 root `m_bom` + full tree of child `m_bom` + `m_bom_line` rows.
**Payload:** `ComposeBuildingPayload(bldgBomId, totalBoms, totalLines, floors, gaps[])`

`gaps[]` lists categories where no catalog BOM fit the AABB — the GUI highlights these as "needs design."

#### ADD FLOOR

Add a storey to an existing unit BOM. Creates slab + floor BOM, adjusts dz stack.

```bimcobol
ADD FLOOR L3 TO SY_MY_DUPLEX HEIGHT 2800 ROOMS BD BD BT
    -- Creates FLOOR_SLAB_L3 at dz = existing roof dz
    -- Creates FLOOR_L3 with room content
    -- Shifts ROOF_ASSEMBLY dz up by (2800 + slab_thickness)
    -- Adjusts existing m_bom_line offsets

ADD FLOOR MEZZANINE TO SY_MY_HOUSE HEIGHT 2400 ROOMS LI
    -- Inserts between GF and ROOF
    -- Partial floor (single room — open-plan mezzanine)
```

**Writes to BOM.db:** 2 `m_bom` rows (slab + floor) + N `m_bom_line` rows. Updates existing roof dz.
**Payload:** `AddFloorPayload(unitBomId, floorBomId, slabBomId, newRoofDz, roomCount)`

#### Mirroring, Rotation, Terrace Rows — No Special Verbs Needed

These are NOT verbs. They are BOM data — expressed via Level 0 primitives:

```bimcobol
-- Duplex party-wall mirror:
CREATE BOM SY_DUPLEX_SET TYPE SET CATEGORY PR
ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_A SEQ 10 ROTATION 0
ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_B SEQ 20 ROTATION 3.14159
    -- Done. The BOMWalker does the rest. No MIRROR code anywhere.

-- Terrace row (4 units, alternating):
CREATE BOM SY_TERRACE_ROW TYPE SET CATEGORY PR
ADD LINE TO SY_TERRACE_ROW CHILD SY_UNIT ROLE UNIT_1 SEQ 10 DX 0.0    ROTATION 0
ADD LINE TO SY_TERRACE_ROW CHILD SY_UNIT ROLE UNIT_2 SEQ 20 DX 6.0    ROTATION 3.14159
ADD LINE TO SY_TERRACE_ROW CHILD SY_UNIT ROLE UNIT_3 SEQ 30 DX 12.0   ROTATION 0
ADD LINE TO SY_TERRACE_ROW CHILD SY_UNIT ROLE UNIT_4 SEQ 40 DX 18.0   ROTATION 3.14159
    -- Four houses in a row, alternating mirror. Pure BOM data.

-- Corner lot (90° rotation):
SET ROTATION ON SY_TERRACE_ROW LINE UNIT_4 TO 1.5708
    -- Unit 4 faces the side street. One float change.
```

This is the DX lesson generalised: **the tack model (dx/dy/dz + rotation_rule) is already a complete spatial instruction set.** The pipeline reads it and applies it. Anything that can be expressed as "place child at offset with rotation" is a BOM line, not a verb.

#### STACK FLOORS

Auto-compute dz offsets for all floors in a unit. Reads slab thicknesses and floor heights from the BOM tree and sets `m_bom_line.dz` accordingly.

```bimcobol
STACK FLOORS IN SY_MY_DUPLEX
    -- Reads each FLOOR and SLAB child
    -- Computes: GF at dz=0, SLAB_L2 at dz=2800, L2 at dz=3000, ROOF at dz=5800
    -- Updates m_bom_line.dz for each child
```

**Writes to BOM.db:** Updates `m_bom_line.dz` values for existing children.
**Payload:** `StackPayload(unitBomId, floorCount, floorOffsets[], totalHeight)`

### 18.9 Level 4 — Catalog-Level Verbs (Template Grammar)

These modify the `M_BomCategory` / `M_BomCategoryLine` grammar that drives COMPOSE BUILDING. This is the metaprogramming level — changing the rules that generate buildings, not the buildings themselves.

#### DEFINE CATEGORY

Create a new M_BomCategory entry. This adds a room type, structural type, or spatial concept to the grammar.

```bimcobol
DEFINE CATEGORY CL CLASSROOM doc_type Commercial
    -- Creates M_BomCategory: category_id=CL, name=Classroom, doc_type=Commercial

DEFINE CATEGORY LB LOBBY doc_type Commercial
DEFINE CATEGORY CF CAFETERIA doc_type Commercial
DEFINE CATEGORY WS WORKSTATION doc_type Commercial
```

**Writes to BOM.db:** 1 `m_bom_category` row.
**Payload:** `DefineCategoryPayload(categoryId, name, docType)`

#### ADD TEMPLATE RULE

Insert M_BomCategoryLine — defines the parent→child containment rule with constraints.

```bimcobol
ADD TEMPLATE RULE GF CONTAINS CL MIN 4 MAX 8
    -- A ground floor in this template has 4–8 classrooms
    -- BomTemplateComposer will allocate AABB to each CL slot

ADD TEMPLATE RULE L2 CONTAINS BD MIN 1 MAX 3 Z_EXTENT 0.5
    -- Upper floor: 1–3 bedrooms, each gets 50% of floor height

ADD TEMPLATE RULE PR CONTAINS HU MIRRORING PARTY_WALL_PI
    -- Pair → Half-Unit with party wall mirroring (the DX pattern)
```

**Writes to BOM.db:** 1 `m_bom_category_line` row.
**Payload:** `AddRulePayload(parentCategory, childCategory, minQty, maxQty, zExtent, mirroringRule)`

#### REGISTER BOM

Tag an existing `m_bom` as belonging to a category, making it available for selection by `BomTemplateComposer.findBestFitAnyOwner()`.

```bimcobol
REGISTER BOM CLASSROOM_SET_A AS CL WIDTH 8000 DEPTH 6000 HEIGHT 3200
    -- Sets m_bom.bom_category = 'CL'
    -- Sets m_bom.allocated_width_mm/depth_mm/height_mm
    -- Now findBestFitAnyOwner("CL", ...) can select it

REGISTER BOM TE_CHECKIN_ZONE AS CK WIDTH 12000 DEPTH 8000
    -- Terminal check-in zone becomes reusable catalog entry
    -- Any future commercial building can select it by AABB fit
```

**Writes to BOM.db:** Updates 1 `m_bom` row (bom_category, allocated dimensions).
**Payload:** `RegisterPayload(bomId, categoryId, widthMm, depthMm, heightMm)`

### 18.10 Level 5 — Variant and Batch Verbs (Typology Generation)

For housing developments, school campuses, clinic chains — the "50 buildings from 1 template" use case. These verbs do not create from scratch; they derive from existing proven BOMs.

#### VARY BUILDING

Clone a BUILDING BOM with dimension changes. Re-runs selection cascade with new AABB to pick appropriate room variants.

```bimcobol
VARY BUILDING BUILDING_SH_STD AS SY_SH_WIDE WIDTH 14000
    -- Clones BUILDING_SH_STD with wider AABB
    -- Kitchen gets larger variant (more cabinets fit)
    -- Living room stretches (SOFA_AREA gets bigger variant if available)
    -- Bedroom unchanged (still fits)

VARY BUILDING BUILDING_DX_STD AS SY_DX_3STOREY FLOORS 3 HEIGHT 9000
    -- Adds third storey
    -- Re-runs DX template with extra L3 level
    -- New floor gets rooms from catalog selection
```

**Writes to BOM.db:** Full clone of source tree with dimension-adjusted selections.
**Payload:** `VaryPayload(sourceBomId, newBomId, changedRooms[], unchangedRooms[], newRooms[])`

#### DERIVE BUILDING

Create a new building type by remixing floors from different source buildings.

```bimcobol
DERIVE BUILDING SY_CLINIC FROM BUILDING_DX_STD REPLACE L1 WITH CLINIC_GF
    -- Takes DX structure (2-storey, mirrored pair)
    -- Swaps L1 rooms for clinic rooms (reception, consulting rooms, pharmacy)
    -- L2 stays: staff bedrooms and bathrooms (reused as-is)

DERIVE BUILDING SY_SCHOOL FROM BUILDING_SH_STD
    ADD FLOOR L2 ROOMS CL CL CL CL
    REPLACE GF WITH SCHOOL_GF
    -- Single-storey house becomes 2-storey school
    -- GF: library, office, staff room, canteen
    -- L2: 4 classrooms
```

**Writes to BOM.db:** New root `m_bom` + mixed child tree.
**Payload:** `DerivePayload(sourceBomId, newBomId, reusedFloors[], replacedFloors[], addedFloors[])`

### 18.11 Verb Chaining — The Feedback Pipeline

Synthetic BOM verbs are designed to chain. The output payload of one verb feeds as input to the next. This is how the Bonsai GUI builds a building interactively:

```bimcobol
-- Step 1: User draws a box in Bonsai
EXTRACT AABB FROM POINTS (0,0,0) (12.0,10.0,6.0)
    → AabbPayload: WIDTH 12000 DEPTH 10000 HEIGHT 6000

-- Step 2: Snap to structural grid
SNAP TO GRID AABB 12000 10000 SPACING 1000
    → GridSnapPayload: WIDTH 12000 DEPTH 10000 (no change needed)

-- Step 3: Check viability
VALIDATE AABB 12000 10000 6000 FOR RESIDENTIAL UNITS 2
    → ValidationPayload: valid=true, candidateFloors=2

-- Step 4: Preview the spatial partition
PARTITION AABB 12000 10000 3000 INTO ROOMS LI DN KT BD BT
    → PartitionPayload: 5 slots with positions
    -- GUI shows the partition overlay on the drawn box
    -- User approves or adjusts room positions

-- Step 5: Compose the building
COMPOSE BUILDING RESIDENTIAL 12000 10000 6000 UNITS 2
    → ComposeBuildingPayload: SY_RE_12000x10000, 12 BOMs, 47 lines

-- Step 6: User clicks on kitchen, drags to resize
RESIZE ROOM KITCHEN_3500x2500 TO 4000 3000 2800 AS KITCHEN_4000x3000
    → ResizePayload: 2 cabinets added, 0 dropped

-- Step 7: User adds a mezzanine
ADD FLOOR MEZZANINE TO SY_RE_12000x10000 HEIGHT 2400 ROOMS LI
    → AddFloorPayload: roof shifted up by 2600mm

-- Step 8: Auto-fix vertical stacking
STACK FLOORS IN SY_RE_12000x10000
    → StackPayload: GF=0, MEZZANINE=3000, ROOF=5600

-- Step 9: Compile and preview
PLACE BOM SY_RE_12000x10000
    → output.db with all elements
SUMMARIZE BUILDING RE
    → SummaryPayload: elements, storeys, AABB — GUI renders 3D preview
```

Each step is an atomic verb with a typed payload. The GUI can checkpoint after any step, undo by reversing the BOM.db writes, and resume from any point.

### 18.12 Verb-to-GUI Action Mapping

Every Bonsai Creator UI action maps to exactly one verb. The GUI never writes SQL directly — it emits BIM COBOL.

| User Action in Bonsai | Verb Emitted | Level |
|---|---|---|
| Draw building envelope | `EXTRACT AABB` + `SNAP TO GRID` | Util |
| "New Building" wizard | `COMPOSE BUILDING` | 3 |
| Drag room into floor | `ADD ROOM` | 2 |
| Resize room handle | `RESIZE ROOM` | 1 |
| Drop furniture into room | `FURNISH ROOM` | 1 |
| Remove room | `REMOVE ROOM` | 2 |
| Swap room variant | `SWAP ROOM` | 2 |
| "Add Storey" button | `ADD FLOOR` | 3 |
| Mirror/rotate unit toggle | `SET ROTATION` (primitive) | 0 |
| Move element | `SET TACK` (primitive) | 0 |
| Change element dimensions | `SET DIMENSIONS` (primitive) | 0 |
| Add child element | `ADD LINE` (primitive) | 0 |
| Remove child element | `REMOVE LINE` (primitive) | 0 |
| Create variant | `VARY BUILDING` | 5 |
| Remix floors | `DERIVE BUILDING` | 5 |
| Define new room type | `DEFINE CATEGORY` + `ADD TEMPLATE RULE` | 4 |
| Import room from another building | `REGISTER BOM` | 4 |
| Create empty room | `CREATE ROOM ... EMPTY` | 1 |
| Create furnished room | `CREATE ROOM` | 1 |
| Clear room contents | `STRIP ROOM` | 1 |
| Check room fits | `VALIDATE AABB` | Util |
| Preview floor layout | `PARTITION AABB` | Util |
| Restack after edits | `STACK FLOORS` | 3 |

### 18.13 Write Discipline — BOM.db Mutation Rules

All synthetic BOM verbs write to BOM.db. This breaks the "BOM.db = read-only dictionary" rule (§11.2). The distinction is enforced by **EntityType**, not naming prefix:

1. **Dictionary BOMs** (entity_type='D') — extracted from IFC Rosetta Stones or curated by hand. Protected by EntityType enforcement in MBOM.beforeSave()/delete(). Names follow iDempiere convention: BUILDING_SH_STD, FLOOR_DX_L1_STD, SH_LIVING_SET, etc.
2. **Synthetic BOMs** (entity_type='U', SY_ prefix) — created by verbs. Mutable. The SY_ prefix distinguishes machine-generated from curated BOMs.

```
BOM.db protection model:
  entity_type='D'  — Dictionary (read-only, PO guards reject mutation)
  entity_type='U'  — User/Synthetic (SY_* prefix, created by verbs, mutable)
  entity_type='A'  — Application (system-managed)
```

Synthetic verbs ONLY create/modify entity_type='U' BOMs. Attempting to modify a Dictionary BOM returns a FAIL result:

```bimcobol
STRIP ROOM SH_LIVING_SET
    -- FAIL: SH_LIVING_SET is entity_type='D' (Dictionary — protected)
    -- Suggestion: CLONE BOM SH_LIVING_SET AS SY_LIVING_CUSTOM, then STRIP
```

This preserves the reference data while allowing unrestricted synthetic creation.

### 18.14 Terminal Scale — Abstract Equivalence

The verb suite is abstract. It does not know whether a ROOM is a kitchen (7 cabinets) or a departure hall (200 seats). The same `CREATE ROOM` verb handles both:

```bimcobol
-- Residential: 7 leaf products
CREATE ROOM KITCHEN 3500 2500 2800
    → KITCHEN_CABINET_SET: 7 Base_Cabinet + 4 Upper_Cabinet + Counter_Top + ...

-- Commercial: 200 leaf products (once Terminal BOMs are decomposed)
CREATE ROOM DEPARTURE_LOUNGE 40000 30000 4500
    → DEPARTURE_LOUNGE_SET: 200 SEATING + 15 RETAIL_COUNTER + 40 BARRIER + ...
```

The verb suite scales by adding `M_BomCategory` entries and `m_bom` catalog rows (SQL data). No Java changes. The grammar grows; the engine stays fixed.

**Terminal (51K elements) decomposition roadmap:**
1. Extract Terminal rooms into SET-level BOMs (Phase B)
2. `REGISTER BOM` each SET into appropriate category (CK, DP, BG, etc.)
3. `ADD TEMPLATE RULE` for commercial/institutional grammar
4. `COMPOSE BUILDING COMMERCIAL ...` now produces Terminal-scale buildings from catalog

### 18.15 Rosetta Stone Ingestion — Primitives for Extraction

The same primitives that CREATE synthetic BOMs also drive the **Rosetta Stone extraction pipeline**. Today this pipeline is bespoke Java + SQL scripts. With BOM primitives, it becomes a verb sequence — scriptable, replayable, and auditable.

The current extraction chain for a new Rosetta Stone (e.g. Terminal):

```
IFC file → IfcOpenShell → merged flat DB (lod_element_placement + lod_geometry_map)
  → classification (storey, discipline, ifc_class grouping)
  → M_Product creation (component_library.db)
  → m_bom + m_bom_line creation (BOM.db)
  → BUILDING BOM registration
```

Every step after classification is BOM primitive operations:

```bimcobol
-- Step 1: Create products from extracted geometry (component_library.db)
-- (This stays as extraction tooling — not BOM COBOL's concern)

-- Step 2: Create the extracted building BOM
CREATE BOM BUILDING_TE_STD TYPE BUILDING CATEGORY RE DOC_SUB_TYPE TE

-- Step 3: Group elements into room/zone SETs
CREATE BOM TE_CHECKIN_SET TYPE SET CATEGORY CK
ADD LINE TO TE_CHECKIN_SET CHILD CheckinCounter_01 ROLE COUNTER SEQ 10 DX 0.0 DY 0.0
ADD LINE TO TE_CHECKIN_SET CHILD CheckinCounter_02 ROLE COUNTER SEQ 20 DX 2.4 DY 0.0
-- ... (repeat for all elements in the zone)
SET DIMENSIONS ON BUILDING_TE_STD CHILD TE_CHECKIN_SET WIDTH 12000 DEPTH 8000 HEIGHT 4500

-- Step 4: Group SETs into floors
CREATE BOM FLOOR_TE_GF_STD TYPE FLOOR CATEGORY L1
ADD LINE TO FLOOR_TE_GF_STD CHILD TE_CHECKIN_SET ROLE CHECKIN SEQ 10
ADD LINE TO FLOOR_TE_GF_STD CHILD TE_DEPARTURE_SET ROLE DEPARTURE SEQ 20
ADD LINE TO FLOOR_TE_GF_STD CHILD TE_RETAIL_SET ROLE RETAIL SEQ 30

-- Step 5: Assemble building
ADD LINE TO BUILDING_TE_STD CHILD FLOOR_TE_GF_STD ROLE GROUND_FLOOR SEQ 10
ADD LINE TO BUILDING_TE_STD CHILD FLOOR_TE_L1_STD ROLE LEVEL_1 SEQ 20 DZ 4.5
ADD LINE TO BUILDING_TE_STD CHILD TE_ROOF_ASSEMBLY ROLE ROOF SEQ 30 DZ 9.0

-- Step 6: Register into catalog for reuse
REGISTER BOM TE_CHECKIN_SET AS CK WIDTH 12000 DEPTH 8000 HEIGHT 4500
REGISTER BOM TE_DEPARTURE_SET AS DP WIDTH 40000 DEPTH 30000 HEIGHT 4500
```

**The payoff:** once Terminal's CHECK_IN zone is extracted and registered as category CK, **any future commercial building** that runs `COMPOSE BUILDING COMMERCIAL ...` can select it by AABB fit. The Rosetta Stone enriches the catalog; the catalog enriches every future building.

This is the compound enrichment model: **extraction primitives and synthetic primitives are the same primitives.** The only difference is the source — IFC reference vs user intent. The BOM data is identical.

#### Extraction Convenience Verbs (Future — Phase B)

When Terminal extraction matures, convenience verbs can wrap the primitive sequences:

```bimcobol
EXTRACT ZONE FROM BUILDING_TE_STD WHERE storey="Aras Tanah" AND discipline="ARC"
    -- Queries lod_element_placement
    -- Groups by spatial proximity
    -- Creates SET BOMs with ADD LINE per element
    -- Returns: zone list with element counts

DECOMPOSE BUILDING BUILDING_TE_STD INTO FLOORS
    -- Groups elements by storey
    -- Creates FLOOR BOMs per storey
    -- Links them to the BUILDING_TE_STD building BOM

CATALOG EXTRACT TE_CHECKIN_SET AS CK
    -- REGISTER BOM + SET DIMENSIONS from actual element extents
    -- Makes the extracted zone available for synthetic reuse
```

These are Phase B scope — but the point is they decompose into the same P0 primitives. No new infrastructure.

### 18.16 Language Constructs Derived from Primitives

The 8 BOM primitives are the instruction set. From them, standard language constructs emerge — the same constructs other DSLs and programming languages provide, but expressed in BOM terms.

#### Variables — BOM References

A BOM ID is a variable. It holds a product structure. Convenience verbs assign to it.

```bimcobol
CREATE BOM SY_MY_HOUSE TYPE UNIT CATEGORY RE    -- declaration + assignment
DESCRIBE BOM SY_MY_HOUSE                        -- dereference (read)
DELETE BOM SY_MY_HOUSE                           -- deallocation
```

#### Loops — Repetition via ADD LINE

No explicit FOR loop. Repetition is expressed as multiple ADD LINE calls with computed offsets. The ARRAY verb (§4.3) is the convenience wrapper — but it decomposes to:

```bimcobol
-- "ARRAY Cabinet ALONG X COUNT 7 SPACING 600" decomposes to:
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_1 SEQ 10 DX 0.0
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_2 SEQ 20 DX 0.6
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_3 SEQ 30 DX 1.2
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_4 SEQ 40 DX 1.8
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_5 SEQ 50 DX 2.4
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_6 SEQ 60 DX 3.0
ADD LINE TO SY_KITCHEN CHILD Cabinet ROLE CAB_7 SEQ 70 DX 3.6
```

#### Conditionals — VALIDATE as Guard

No IF/ELSE. Validation verbs act as guards that prevent invalid operations:

```bimcobol
VALIDATE AABB 1500 1200 2800 FOR KITCHEN     -- guard: returns FAIL
CREATE ROOM KITCHEN 1500 1200 2800            -- would also FAIL (same check internally)
```

The convenience verbs embed validation. The primitive ADD LINE does not — it trusts the caller. This is the power user vs. guided user split.

#### Composition — BOM Nesting (the MAKE pattern)

In manufacturing BOM, a component_type=MAKE child means "this child is itself a BOM that must be manufactured." In BIM COBOL, MAKE children are sub-assemblies:

```bimcobol
CREATE BOM SY_SOFA_AREA TYPE SET CATEGORY FR
ADD LINE TO SY_SOFA_AREA CHILD Sofa_3Seater ROLE SOFA SEQ 10
ADD LINE TO SY_SOFA_AREA CHILD CoffeeTable ROLE TABLE SEQ 20 DX 1.5

-- Now nest it into a room:
ADD LINE TO SY_LIVING CHILD SY_SOFA_AREA ROLE SOFA_AREA SEQ 10 TYPE MAKE
    -- TYPE MAKE tells the walker: recurse into SY_SOFA_AREA's children
```

This is unbounded nesting — rooms contain sets, sets contain sub-assemblies, sub-assemblies contain items. The BOM tree depth is limited only by the data. The primitives don't care about depth.

#### Copy/Template — CLONE as Constructor

CLONE BOM (already implemented, §F0.x) is the copy constructor:

```bimcobol
CLONE BOM BUILDING_SH_STD AS SY_MY_HOUSE
    -- Deep copy: new m_bom + all m_bom_line rows
    -- SY_MY_HOUSE is now an independent copy
    -- Modify freely without affecting BUILDING_SH_STD
```

CLONE + primitive edits = the VARY BUILDING pattern. No special VARY verb needed at the primitive level.

#### Transactions — Verb Atomicity

Each verb is atomic: it either completes fully (all m_bom/m_bom_line rows written) or fails completely (no partial state). This is the DAO pattern — `conn.setAutoCommit(false)` + commit/rollback.

A convenience verb like COMPOSE BUILDING may execute 20+ primitive operations internally. If any fails (e.g. catalog gap), the entire composition rolls back. The GUI sees either success or a clean failure with `gaps[]` listing what's missing.

#### Introspection — Query Verbs as Reflection

The existing data-handling verbs (LIST, SELECT, DESCRIBE, COUNT, AGGREGATE) are the reflection/introspection layer:

```bimcobol
LIST BOMS SY_           -- "what synthetic BOMs exist?"
DESCRIBE BOM SY_HOUSE   -- "what's inside this BOM?"
COUNT BOM SY_HOUSE RECURSIVE  -- "how many leaf elements?"
AGGREGATE BOM SY_HOUSE BY component_type  -- "BUY/MAKE/PHANTOM breakdown"
```

These let the GUI (or a script) inspect BOM state between mutation steps. This is the feedback loop in §18.11.

#### The ERP Mfg Module as Minefield

The iDempiere Manufacturing module provides further constructs to draw from:

| ERP Mfg Concept | BIM COBOL Equivalent | Status |
|---|---|---|
| PP_Product_BOM | `CREATE BOM` | Designed (§18.4) |
| PP_Product_BOM_Line | `ADD LINE` | Designed (§18.4) |
| PP_Order (work order) | `COMPOSE BUILDING` (creates from template) | Designed (§18.8) |
| PP_Order_BOM (order-specific copy) | `CLONE BOM` (deep copy for modification) | **Implemented** |
| PP_Order_Node (operation) | PP_Order_Node (verb invocation audit) | **Implemented** |
| PP_Order_Workflow (operation sequence) | Verb chaining (§18.11) | Designed |
| PP_Cost_Collector (cost accumulation) | `AGGREGATE BOM BY component_type` | **Implemented** |
| M_Forecast (demand planning) | `PARTITION AABB` (spatial demand) | Designed (§18.5) |
| QM_Specification (quality check) | `VALIDATE AABB` / CHECK verbs | Designed |
| PP_Product_Planning (MRP rules) | `ADD TEMPLATE RULE` (grammar rules) | Designed (§18.9) |
| M_Production_Line (capacity) | CO_EmptySpace (spatial capacity) | **Implemented** |
| M_Warehouse → M_Locator hierarchy | CO_EmptySpace L0→L1→L2 hierarchy | **Implemented** |

Five of these are already implemented. The primitives complete the remaining set. The ERP manufacturing framework is a 20-year-proven architecture — BIM COBOL inherits it directly.

### 18.17 Verb Componentisation — Separation of Concerns

#### The Problem with Monolithic Verbs

If COMPOSE BUILDING contains its own BOM creation logic, its own line insertion logic, its own validation logic, and its own dimension computation logic, then:
- A bug in line insertion affects COMPOSE BUILDING, CREATE FLOOR, CREATE ROOM, FURNISH ROOM, and every other verb that adds children
- A change to the AABB validation rule must be updated in every verb that checks dimensions
- Testing requires building an entire building to verify a single line insertion

#### The Solution: Layered Componentisation

```
┌────────────────────────────────────────────────────────┐
│  Level 5: VARY BUILDING, DERIVE BUILDING               │  typology
│  Level 3: COMPOSE BUILDING, ADD FLOOR                   │  building
│  Level 2: CREATE FLOOR, ADD/REMOVE/SWAP ROOM            │  floor
│  Level 1: CREATE ROOM, FURNISH, RESIZE, STRIP           │  room
├────────────────────────────────────────────────────────┤
│  Utility: EXTRACT AABB, PARTITION, VALIDATE, SNAP       │  geometry
├────────────────────────────────────────────────────────┤
│  Level 0: CREATE BOM, ADD LINE, SET TACK,               │  primitives
│           SET ROTATION, SET DIMENSIONS,                  │  (8 verbs)
│           SET LINE PROPERTY, REMOVE LINE, DELETE BOM     │
├────────────────────────────────────────────────────────┤
│  DAO:     MBOM, MBOMLine, MBomCategory,                 │  persistence
│           MBomCategoryLine, BomTemplateComposer          │
└────────────────────────────────────────────────────────┘
```

**Each layer calls ONLY the layer directly below it.** Never skip layers.

- COMPOSE BUILDING calls CREATE FLOOR + ADD LINE + SET TACK (never calls MBOM.create() directly)
- CREATE FLOOR calls CREATE BOM + ADD LINE + SET DIMENSIONS (never calls MBOMLine directly)
- ADD LINE calls MBOM DAO (the only layer that touches SQL)

#### Bug Isolation

| Bug | Fix Location | Verbs Automatically Fixed |
|---|---|---|
| Line insertion sets wrong sequence | `AddLineVerb.java` | All verbs that add children (12+) |
| AABB validation too strict | `ValidateAabbVerb.java` | CREATE ROOM, CREATE FLOOR, COMPOSE BUILDING |
| Tack offset rounding error | `SetTackVerb.java` | FURNISH ROOM, ADD FLOOR, STACK FLOORS |
| Rotation rule not normalised | `SetRotationVerb.java` | Duplex pair creation, terrace rows, corner lots |
| BOM naming collision | `CreateBomVerb.java` | Every verb that creates BOMs |
| Dimension overflow | `SetDimensionsVerb.java` | RESIZE ROOM, ADD ROOM, CREATE FLOOR |

**One bug = one file = one fix.** The higher-level verbs inherit the fix automatically because they delegate to the primitive.

#### Implementation Pattern: Each Verb Calls Other Verbs

```java
// CreateRoomVerb.java — Level 1 convenience verb
public VerbResult<CreateRoomPayload> execute(VerbContext ctx, String... args) {
    // Step 1: Validate (calls utility verb)
    VerbResult<?> valid = validateAabbVerb.execute(ctx, category, w, d, h);
    if (!valid.isOk()) return VerbResult.fail(...);

    // Step 2: Create container (calls Level 0 primitive)
    VerbResult<?> bom = createBomVerb.execute(ctx, bomId, "SET", category);

    // Step 3: Select and add children (calls Level 0 primitive per child)
    for (MProduct product : selectedProducts) {
        addLineVerb.execute(ctx, bomId, product.getId(), role, seq, dx, dy, dz);
    }

    // Step 4: Set allocated dimensions (calls Level 0 primitive)
    setDimensionsVerb.execute(ctx, bomId, childId, w, d, h);

    return VerbResult.ok(...);
}
```

The key: `CreateRoomVerb` never calls `MBOMLine.create()` directly. It calls `AddLineVerb.execute()`. If AddLineVerb gains a new validation rule (e.g. duplicate detection), every higher verb gets it for free.

#### Test Isolation

Each primitive verb can be tested independently:

```
CreateBomVerbTest       — 1 m_bom row created, idempotent, SY_ prefix enforced
AddLineVerbTest         — 1 m_bom_line row, FK validation, sequence auto-increment
SetTackVerbTest         — dx/dy/dz >= 0 (tack convention §3.4)
SetRotationVerbTest     — normalise to [0, 2π), accepts pi/degrees
SetDimensionsVerbTest   — all >= 0, overflow check against parent AABB
RemoveLineVerbTest      — FK cascade check, orphan detection
DeleteBomVerbTest       — cascade delete, referential integrity guard
```

Convenience verb tests then only need to verify the **composition** is correct — they don't re-test the primitives:

```
CreateRoomVerbTest      — correct number of ADD LINE calls, correct selection
ComposeBuildingVerbTest — correct BOM tree structure, correct dz stacking
VaryBuildingVerbTest    — clone fidelity, dimension re-selection
```

#### VerbRegistry: Dependency Injection for Verbs

The existing `VerbRegistry.createDefault()` pattern already supports this. Each verb is registered by keyword. A verb can look up other verbs from the registry:

```java
public class CreateRoomVerb implements Verb<CreateRoomPayload> {
    // Resolved from VerbRegistry at construction time or lazily
    private final Verb<?> createBomVerb;
    private final Verb<?> addLineVerb;
    private final Verb<?> setDimensionsVerb;
    private final Verb<?> validateAabbVerb;
}
```

This is constructor injection — the same pattern iDempiere uses for ModelValidator chains. The registry is the composition root.

### 18.18 Terminal BOM Conversion — Multi-Discipline Analysis at Scale

*The Sultan Johor Terminal II (SJTII) is a 4-storey institutional building with 51,088 elements across 9 disciplines, federated from multiple consultant IFC files into a single reference database. It is the stress test for everything in §18.*

#### The Scale Problem

Residential BOMs are small. SH has 55 elements. DX has 1,099. The BOM trees are 3–4 levels deep. A human can read them. A human WROTE them (as SQL INSERTs).

Terminal has **51,088 elements**. No human can manually author that BOM. The extraction pipeline must decompose a federated IFC model — originally authored by 5+ consultant teams in 5+ software tools — into a reusable BOM hierarchy. This requires analysis verbs that no residential-scale compiler needs.

#### Multi-Discipline Complexity

Real institutional IFC is not one model. It is a **federation** of discipline-specific models, each from a different consultant, each with different modelling conventions:

| Discipline | IFC Classes | Count | Consultant Pattern |
|---|---|---|---|
| **ARC** (Architecture) | IfcWall, IfcSlab, IfcDoor, IfcWindow, IfcCurtainWall, IfcRoof, IfcFurniture | ~8,000 | Rooms, walls, openings, finishes |
| **STR** (Structural) | IfcColumn, IfcBeam, IfcMember, IfcReinforcingBar, IfcFooting, IfcPile | ~4,000 | Grid-aligned frame + 2,660 rebar |
| **MEP** (Mechanical) | IfcPipeSegment, IfcPipeFitting, IfcValve | ~4,600 | Pipe networks (chilled water, fire) |
| **FP** (Fire Protection) | IfcFireSuppressionTerminal, IfcAlarm, IfcSensor | ~1,100 | Sprinkler grids + alarms |
| **ELEC** (Electrical) | IfcLightFixture, IfcElectricAppliance, IfcCableSegment | ~1,200 | Lighting grids + power |
| **ACMV** (Air Conditioning) | IfcDuctSegment, IfcDuctFitting, IfcAirTerminal | ~1,600 | Duct networks + diffusers |
| **CW** (Curtain Wall) | IfcPlate, IfcMember (facade panels + mullions) | ~33,000 | Roof deck plates (TILE pattern) |
| **SP** (Specialist) | IfcProxy, IfcBuildingElementProxy | ~500 | Misc items |
| **LPG** (Landscape/Plumbing/Gas) | IfcFlowTerminal, IfcSanitaryTerminal | ~300 | Fixtures + drainage |

Key challenges that residential BOMs never face:

**1. Discipline overlap.** A pipe that crosses from the MEP model into the FP model is the same physical pipe but appears in two IFC files. Federated databases must deduplicate by GUID or spatial proximity. The extraction pipeline already handles this via `enhanced_federation_GI.db`, but the BOM must track provenance (which consultant, which IFC file, which discipline).

**2. IFC class ambiguity.** `IfcBuildingElementProxy` is used by every discipline for "things that don't have a proper IFC class." In Terminal, 500+ proxies cover everything from bollards to access panels. The BOM needs a classification verb that assigns proxy elements to categories based on property sets, not IFC class alone.

**3. System topology.** MEP elements don't exist in isolation — they form connected systems (pipe networks, duct trees, circuit branches). A sprinkler head is meaningless without its lateral pipe, branch pipe, main run, riser, and pump. The BOM must capture system connectivity, not just element-by-element placement.

**4. Parametric repetition at scale.** 33,324 roof deck plates are NOT 33,324 independent placements. They are 19 TILE formulas. 2,660 rebar bars are 150+ ARRAY formulas. 909 sprinkler heads are zone-based ROUTE formulas. The analysis must discover these patterns and factorize them.

**5. Storey mismatch.** Different disciplines may use different storey definitions. The ARC model may define "Ground Floor" at +0.000, while the STR model defines "Basement" at -3.000 and calls +0.000 "Level 1." The BOM must normalise storey references across disciplines.

#### Analysis Verbs for Terminal-Scale Extraction

These verbs analyse an extracted reference database to discover BOM structure. They are read-only — they inspect, classify, and report. The output feeds into the creation primitives (§18.4) to materialise the discovered structure.

```bimcobol
CENSUS BUILDING TE
    -- Counts elements per discipline, per storey, per IFC class
    -- Groups by spatial zone (AABB clustering)
    -- Discovers: 9 disciplines, 4 storeys, 51K elements
    -- Output: discipline×storey matrix with element counts
    -- This is the first verb to run on any new Rosetta Stone

DISCOVER PATTERNS IN TE DISCIPLINE CW
    -- Analyses spatial distribution for repetition patterns
    -- Grid detection: find dominant X/Y spacing per Z-band
    -- Output: candidate TILE formulas with coverage percentage
    -- Already proven: 19 panels, 33K plates, 95% coverage

DISCOVER PATTERNS IN TE DISCIPLINE STR
    -- Detects: column grids (FRAME formula), beam spans
    -- Detects: rebar arrays (ARRAY formula) with host association
    -- Output: candidate FRAME + ARRAY formulas

DISCOVER PATTERNS IN TE DISCIPLINE FP
    -- Detects: sprinkler grids per zone (ROUTE formula)
    -- Associates heads with pipe networks (system topology)
    -- Output: zone boundaries + ROUTE formulas

CLASSIFY ELEMENTS IN TE WHERE ifc_class = 'IfcBuildingElementProxy'
    -- Reads property sets (IfcPropertySingleValue, IfcClassificationReference)
    -- Assigns M_BomCategory based on properties, not IFC class
    -- Output: reclassification map (proxy GUID → category)

DISCOVER ZONES IN TE STOREY "Aras Tanah"
    -- Spatial clustering: groups elements by proximity
    -- Identifies room/zone boundaries from wall enclosures
    -- Output: zone polygons with element membership
    -- This is the automated version of manual room assignment

ANALYSE SYSTEMS IN TE DISCIPLINE MEP
    -- Follows pipe/duct connectivity (fitting→segment→fitting chains)
    -- Identifies system trees: riser → main → branch → terminal
    -- Computes: pipe lengths, fitting counts, terminal counts per system
    -- Output: system topology suitable for ROUTE formula discovery

NORMALISE STOREYS IN TE
    -- Aligns storey definitions across disciplines
    -- Resolves: "Ground Floor" (ARC) = "Level 1" (STR) = "GF" (MEP)
    -- Maps each element to a canonical storey name
    -- Output: normalised storey map with element reassignments
```

#### The Terminal Decomposition Pipeline (Verb Sequence)

```bimcobol
-- Phase 1: Understand what we have
CENSUS BUILDING TE
NORMALISE STOREYS IN TE

-- Phase 2: Discover patterns per discipline
DISCOVER PATTERNS IN TE DISCIPLINE CW     -- → 19 TILE formulas (33K elements)
DISCOVER PATTERNS IN TE DISCIPLINE STR    -- → FRAME + ARRAY formulas (4K)
DISCOVER PATTERNS IN TE DISCIPLINE FP     -- → ROUTE formulas (1.1K)
DISCOVER PATTERNS IN TE DISCIPLINE ELEC   -- → WIRE formulas (1.2K)
DISCOVER PATTERNS IN TE DISCIPLINE ACMV   -- → ROUTE DUCT formulas (1.6K)

-- Phase 3: Classify ambiguous elements
CLASSIFY ELEMENTS IN TE WHERE ifc_class = 'IfcBuildingElementProxy'

-- Phase 4: Discover spatial zones
DISCOVER ZONES IN TE STOREY "Aras Tanah"
DISCOVER ZONES IN TE STOREY "Aras 01"
DISCOVER ZONES IN TE STOREY "Aras 02"
DISCOVER ZONES IN TE STOREY "Aras 03"

-- Phase 5: Extract zone BOMs (using creation primitives)
CREATE BOM TE_DEPARTURE_SET TYPE SET CATEGORY DP
-- ... (ADD LINE per element in the zone)

-- Phase 6: Register zones into catalog
REGISTER BOM TE_DEPARTURE_SET AS DP WIDTH 40000 DEPTH 30000 HEIGHT 4500
REGISTER BOM TE_CHECKIN_SET AS CK WIDTH 12000 DEPTH 8000 HEIGHT 4500

-- Phase 7: Define commercial template grammar
DEFINE CATEGORY CK CHECKIN doc_type Commercial
DEFINE CATEGORY DP DEPARTURE doc_type Commercial
ADD TEMPLATE RULE CO CONTAINS CK MIN 1 MAX 2
ADD TEMPLATE RULE CO CONTAINS DP MIN 1 MAX 4

-- Phase 8: Prove round-trip
COMPOSE BUILDING COMMERCIAL 120000 85000 16000 UNITS 1 FLOORS 4
-- → Should select Terminal zones by AABB fit
-- → Should reproduce Terminal-equivalent building from catalog
```

The full decomposition is Phase B scope. But the verb language is ready for it NOW because the primitives and analysis verbs are abstract — they don't care if the building has 55 elements or 51K.

#### What LLM-Scale IFC Analysis Teaches Us

Large federated IFC models (50K+ elements) exhibit patterns that small residential models don't:

**1. Power-law distribution.** A few IFC classes dominate: IfcPlate (33K roof plates), IfcPipeSegment (4.6K), IfcReinforcingBar (2.7K). The top 3 classes = 80% of elements. BOM factorisation must target these first — the TILE/ARRAY/ROUTE verbs cover 74.4% of Terminal precisely because of this distribution.

**2. Hierarchical locality.** Elements cluster spatially. A departure hall's 200 seats are all within a 40×30m AABB. A structural bay's 20 columns are all on a 10×8m grid. Zone discovery (DISCOVER ZONES) exploits this — it is DBSCAN clustering on 3D coordinates.

**3. System trees, not element lists.** MEP is never flat. A fire protection system is a tree: pump → riser → main runs → branch lines → laterals → sprinkler heads. Extracting this as a flat element list loses the connectivity that ROUTE verbs need. ANALYSE SYSTEMS recovers the tree topology from IfcRelConnectsPortToPort and spatial adjacency.

**4. Consultant boundaries = discipline boundaries.** In practice, each discipline's IFC file was authored by a different firm with different LOD, different property naming, and different storey definitions. The federation database already merges them, but the BOM must track provenance so that when a structural engineer updates the beam schedule, only the STR discipline BOMs are affected.

**5. Parametric regularity with exceptions.** 91% of Terminal sprinkler X-spacing is 3.0m — but 9% isn't (obstructed zones, special hazard areas). The ROUTE verb handles the 91%. The 9% are flat-placed exceptions stored as individual m_bom_line rows. The analysis verbs must separate regular from irregular — this is the factorisation problem.

**6. Cross-discipline coordination zones.** Some spatial zones require elements from 4+ disciplines: a ceiling void has ducts (ACMV), pipes (MEP), sprinklers (FP), cable trays (ELEC), and lighting (ELEC) all in the same 500mm vertical space. The BOM must group these into coordination zones for clash detection (CHECK CLASH, §17.2) and construction sequencing (PP_Order_Node).

These patterns are invariant across institutional buildings — airports, hospitals, schools, factories. The analysis verbs designed for Terminal will apply directly to the next institutional Rosetta Stone without modification.

### 18.19 Implementation Priority

| Priority | Verb | Rationale |
|---|---|---|
| **P0** | **`CREATE BOM`** | **Foundation primitive. Every other verb calls this.** |
| **P0** | **`ADD LINE`** | **Foundation primitive. Every composition = ADD LINE calls.** |
| **P0** | **`SET TACK` / `SET ROTATION` / `SET DIMENSIONS`** | **Foundation primitives. Spatial placement = setting these values.** |
| **P0** | **`REMOVE LINE` / `DELETE BOM`** | **Foundation primitives. Edit/undo support.** |
| P1 | `EXTRACT AABB` | Foundation utility — all other verbs need AABB input |
| P1 | `CREATE ROOM` | First convenience verb. Composes P0 primitives into room creation. |
| P1 | `COMPOSE BUILDING` | The headline feature. Wraps BomTemplateComposer + P0 primitives. |
| P2 | `PARTITION AABB` | Required for CREATE FLOOR layout computation |
| P2 | `CREATE FLOOR` | Composes rooms into storeys |
| P2 | `FURNISH ROOM` | Interactive room population for GUI |
| P2 | `VALIDATE AABB` | Safety check before creation |
| P3 | `RESIZE ROOM` | Clone + adjust pattern |
| P3 | `ADD ROOM` / `REMOVE ROOM` / `SWAP ROOM` | Floor editing (all decompose to P0 primitives) |
| P3 | `ADD FLOOR` / `STACK FLOORS` | Multi-storey editing |
| P3 | `SNAP TO GRID` | GUI integration |
| P4 | `STRIP ROOM` / `SET LINE PROPERTY` | Destructive edit / general property setter |
| P5 | `DEFINE CATEGORY` / `ADD TEMPLATE RULE` / `REGISTER BOM` | Grammar extension |
| P5 | `VARY BUILDING` / `DERIVE BUILDING` | Typology generation |

**P0 primitives** are the first implementation target. They are trivial — each is a single DAO call (MBOM.create(), MBOMLine.create(), MBOMLine.setDx(), etc.) wrapped in the Verb<T> SPI pattern. Once P0 is done, every higher-level verb is a composition of primitives — no new infrastructure. Each verb = 1 Java file in `BIM_COBOL/src/main/java/com/bim/cobol/verb/`.

### 18.19 Compilation Strategy — Interpreter Now, Compiler Later

BIM COBOL is currently an **interpreter**. `VerbRegistry.dispatch(ctx, line)` takes one line of text, matches the longest keyword prefix, tokenizes args, and executes immediately against the database. No AST, no intermediate representation, no separate compilation step.

This is deliberate. Two use cases pull in different directions:

**1. Interactive / GUI (Bonsai) — Interpreter is correct.**
The user clicks in Bonsai, the GUI emits one verb at a time (`ADD LINE TO SY_KITCHEN_A CHILD Base_Cabinet ...`), and it executes immediately with feedback. This is a REPL. A compiler adds nothing here.

**2. Script-driven composition (`.bimcobol` files) — Compiler adds value.**
The §18.11 verb chaining pipeline and Phase F5 ("script-driven compilation — MEP/structural generation moves entirely to .bimcobol scripts") implies multi-statement scripts. Here a parse→validate→execute pipeline catches errors before mutation:

| Phase | What it catches |
|-------|----------------|
| Parse | Syntax errors (missing keyword, bad token) |
| Validate | All referenced BOMs/products exist BEFORE any mutation |
| Plan | Batch SQL, detect conflicts (two verbs writing same line) |
| Execute | Run the validated plan in a single transaction |

The key win: **validate all references before writing anything to BOM.db.** Without this, if line 3 of a 10-line script has a bad product ID, lines 1–2 have already mutated the database. A compiler rejects the whole script upfront.

**Evolution path:**

| Stage | When | What |
|-------|------|------|
| Interpreter | Now (P0–P3) | Verb-at-a-time execution. Same pattern as F0.x data verbs. |
| Script runner | Phase F5 | Thin parse→validate→execute pipeline. Read all lines, validate references, execute in a single transaction. Not a full compiler — just a validated batch runner. |
| Full compiler | If needed | AST + optimization passes. Only if script complexity demands it. |

The COBOL/Java analogy holds in the *domain language* sense (construction intent compiles to assembler-level IFC), but the implementation does not need a traditional compiler architecture yet. The 9-stage DAGCompiler pipeline already IS the "compiler" — BIM COBOL verbs are a higher-level way to feed it.

---

*BIM COBOL v0.13 — 34 verbs implemented (8 P0 primitives + 3 utilities now live) + 14 convenience + 7 analysis verbs designed (§18), 96 witnesses (92 PASS / 4 RED), 74.4% Terminal formula coverage*
*The Construction Programming Language*
*March 2026*
