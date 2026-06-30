# BIM COBOL — The Construction Programming Language

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md) · [SourceCodeGuide](SourceCodeGuide.md)

<div class="bim-banner" markdown>
<b>77 verbs that turn BOM recipes into geometry.</b> The domain vocabulary lives here — TILE, CLUSTER, ROUTE, FRAME — but the compiler underneath is generic. Swap the verbs and you compile a different domain.
</div>

**Version:** 1.0
**Date:** 2026-03-08
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Status:** ACTIVE — **77 verbs implemented, 202 witnesses.** Full layered composition stack L0→L1→L2→L3→L4. F5 integration script exercises 30 verbs across all 5 layers in a single ScriptRunner pass (36 verb lines, 0 failures). 22 verbs need dedicated harness (output.db path, XLSX, component_library.db context). Phase H2: 5 verb wrappers replace all raw SQL on protected tables. T16 tamper rule enforces zero regressions.
**Module:** `BIM_COBOL/` (root-level Maven sibling of DAGCompiler, TopologyMaker)
**Depends on:** BIM_Designer.md (Compiled Construction v0.8), TopologyMaker/docs/TOPOLOGY_MAKER.md (Synthetic Stone §18-19), TheRosettaStoneStrategy.md (Terminal formula coverage — shared concern)
**Supplements:** MANIFESTO.md, PREFAB_ARCHITECTURE.md, DocAction_SRS.md (W_Verb_Node lineage)

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

The BIM_COBOL module has a working `Verb<T>` interface, `VerbContext`, and `VerbResult<T>` framework. **77 verbs implemented, 202 witnesses:**

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
| 14 | `SELECT BOM` | data | — | BOM selection by ID |
| 15 | `LIST BOMS` | data | — | List available BOMs |
| 16 | `DESCRIBE BOM` | data | — | BOM metadata summary |
| 17 | `COUNT BOM` | data | — | BOM line count |
| 18 | `AGGREGATE BOM` | data | — | BOM quantity roll-up |
| 19 | `EXPORT BOM` | data | — | BOM export to file |
| 20 | `CLONE BOM` | data | — | Deep-copy BOM tree |
| 21 | `SUMMARIZE BUILDING` | data | — | Building-level BOM summary |
| 22 | `CREATE BOM` | L0 | W-SY-1..29 | Synthetic BOM creation |
| 23 | `ADD LINE` | L0 | W-SY-1..29 | Add child line to BOM |
| 24 | `SET TACK` | L0 | W-SY-1..29 | Set BOM line tack point |
| 25 | `SET ROTATION` | L0 | W-SY-1..29 | Set BOM line rotation |
| 26 | `SET DIMENSIONS` | L0 | W-SY-1..29 | Set BOM line dimensions |
| 27 | `REMOVE LINE` | L0 | W-SY-1..29 | Remove child line from BOM |
| 28 | `DELETE BOM` | L0 | W-SY-1..29 | Delete entire BOM |
| 29 | `SET LINE PROPERTY` | L0 | W-SY-1..29 | Set arbitrary line property |
| 30 | `EXTRACT AABB` | utility | W-SY-1..29 | Extract axis-aligned bounding box |
| 31 | `SNAP TO GRID` | utility | W-SY-1..29 | Snap coordinates to grid |
| 32 | `VALIDATE AABB` | utility | W-SY-1..29 | Validate AABB consistency |
| 33 | `CREATE ROOM` | L1 | W-SY-30..43 | Room BOM from category |
| 34 | `FURNISH ROOM` | L1 | W-SY-30..43 | Add furniture to room BOM |
| 35 | `RESIZE ROOM` | L1 | W-SY-30..43 | Adjust room dimensions |
| 36 | `STRIP ROOM` | L1 | W-SY-30..43 | Remove furnishings from room |
| 37 | `PARTITION AABB` | L2 | W-SY-44..56 | Split AABB into sub-volumes |
| 38 | `CREATE FLOOR` | L2 | W-SY-44..56 | Floor BOM from room list |
| 39 | `ADD ROOM` | L2 | W-SY-44..56 | Add room to floor BOM |
| 40 | `REMOVE ROOM` | L2 | W-SY-44..56 | Remove room from floor BOM |
| 41 | `SWAP ROOM` | L2 | W-SY-44..56 | Replace room in floor BOM |
| 42 | `COMPOSE BUILDING` | L3 | W-SY-66..72 | Building BOM from floor list |
| 43 | `ADD FLOOR` | L3 | W-SY-66..72 | Add floor to building BOM |
| 44 | `STACK FLOORS` | L3 | W-SY-66..72 | Auto-stack floors with Z offsets |
| 45 | `DEFINE CATEGORY` | L4 | W-SY-57..65 | Create product category |
| 46 | `ADD TEMPLATE RULE` | L4 | W-SY-57..65 | Attach template rule to category |
| 47 | `REGISTER BOM` | L4 | W-SY-57..65 | Register BOM in catalog |
| 48 | `REPORT BOM CATALOG` | H0 | W-H0-1..10 | XLSX BOM catalog report |
| 49 | `REPORT PRODUCT CATALOG` | H0 | W-H0-1..10 | XLSX product catalog report |
| 50 | `REPORT BOM STRUCTURE` | H0 | W-H0-1..10 | XLSX BOM tree structure report |
| 51 | `COMPOSE PREFAB BOM` | H2 | W-H2-1..3 | Idempotent m_bom + m_bom_line creation |
| 52 | `CLEAR VARIANCE FROM BOM` | H2 | W-H2-4..6 | Delete variance/buffer rows |
| 53 | `FILL BUFFERS IN BOM` | H2 | W-H2-7..9 | Interstitial filler computation |
| 54 | `REGISTER BUILDING` | H2 | W-H2-10..12 | c_order creation (DocStatus=IP) |
| 55 | `COMPLETE BUILDING` | H2 | W-H2-13..15 | c_order promotion (IP→CO) |
| 56 | `TRIM WALLS TO ROOF` | §17 | W-TRIM-1..7 | Wall-roof clip: measure roof AABB surface, 50mm tolerance |
| 57 | `EN-BLOC` | original | — | Bulk element operation |
| 58 | `WALK THRU` | original | — | BOM tree traversal verb |
| 59 | `VOID EMPTY_SPACE FOR BUILDING` | H2+ | — | WMS empty space allocation |
| 60 | `OVERRIDE ROOF` | H3 | — | Roof parameter override |
| 61 | `FIX OPENING BBOX` | H3 | — | Opening bounding box correction |
| 62 | `BUILD SPATIAL STRUCTURE` | H3 | — | Spatial hierarchy construction |
| 63 | `FIT` | §4.6 | — | Joining: press-fit connection |
| 64 | `JOIN` | §4.6 | — | Joining: general connection |
| 65 | `ATTACH` | §4.6 | — | Joining: surface attachment |
| 66 | `MOUNT` | §4.6 | — | Joining: bracket/frame mount |
| 67 | `HANG` | §4.6 | — | Joining: suspended connection |
| 68 | `BOLT` | §4.6 | — | Joining: bolted connection |
| 69 | `WELD` | §4.6 | — | Joining: welded connection |
| 70 | `EMBED` | §4.6 | — | Joining: embedded connection |
| 71 | `CLAMP` | §4.6 | — | Joining: clamped connection |
| 72 | `ALONG` | §4.6 | — | Surface: path-following placement |
| 73 | `CORNER` | §4.6 | — | Surface: corner element placement |
| 74 | `ROLLUP AABB` | utility | — | Recursive AABB aggregation |
| 75 | `HELLO WORLD` | infra | — | Verb framework smoke test |
| 76 | `FORGE` | §10 | — | Geometry Forge: formula-driven construction pieces |
| 77 | `FOLLOW` | §10.4.10 | W-FOLLOW-1 | Movement: straight pipe run along surface. Produces lengthMm → persisted as qty in M (mm÷1000 at RouteStage boundary) |
| — | *VerbRegistry + ScriptRunner* | infra | W-41..44 | Dispatch + script execution |
| — | *F5IntegrationTest* | infra | W-F5-1..200 | **End-to-end cross-verb integration (30 verbs, 36 lines, 0 failures)** |

Rosetta Stone validation (W-13..16) is run as part of the `RouteSprinklersVerbTest` suite, proving BIM COBOL geometry matches real Terminal/Duplex IFC data.

#### CHECK BOM (W-COBOL-1..4)

```bimcobol
CHECK BOM <bom_id>
```

Walks the BOM tree from a root, counting BUY/MAKE/PHANTOM nodes and checking structural invariants (empty assemblies, missing products, cycle guard). Read-only against {PREFIX}_BOM.db.

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

First MEP routing verb. Computes sprinkler head grid + pipe routing + NFPA compliance proof from a single statement. Read-only against component_library.db (`ad_room_boundary` for room AABB) and ERP.db (`ad_fp_coverage` for hazard class rules) (LIGHT: max_coverage=18.6m², max_spacing=4.6m, wall_distance=2.3m).

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
- `Duplex_extracted.db` — 1,099 elements, 427 pipes, 358 fittings, 47 receptacles, 14 switches

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

> **Browser implementation:** See **[Clash Detection](CLASH_DETECTION.md)** — 12 discipline-pair rules, live tolerance slider, progressive storey queries, HTML report. Zero install.

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

Room dimensions vs `ad_ubbl_rule` building code checks. Room geometry derived from IfcSpace entries in `spatial_structure`, with bounds computed from contained elements via `rel_contained_in_space`. Rules loaded from {PREFIX}_BOM.db: AREA (mm² → m²), MIN_DIM (mm → m), CEILING_MM (mm → m). Uses `ctx.bomConn()`.

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
         Structured declarations that the 11-stage pipeline processes.
                    │
                    ▼  11-Stage Pipeline
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

Level 1 compiles via the existing 11-stage pipeline into output.db:

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
> `W_Verb_Node` rows (W_Verb_Node model, §15.6). Full Terminal measurement data and
> phase roadmap (TE-1..TE-8) in [`TheRosettaStoneStrategy.md`](TheRosettaStoneStrategy.md)
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

### 4.6 JOINING Verbs — How Elements Connect

Construction joining verbs are **universal across all jurisdictions** — a pipe fits into a fitting the same way everywhere. Standards differ on thresholds (clearance, torque, cover depth), but the mechanical actions are shared. These verbs record HOW two elements connect, writing to `W_Verb_Node` (execution audit) and referencing `ad_assembly_connector` (port metadata in component_library.db).

| Keyword | Syntax | What it does | Metadata source |
|---------|--------|-------------|-----------------|
| `FIT` | `FIT elem INTO host` | Press/friction fit. Checks port diameter match. | `ad_assembly_connector` |
| `JOIN` | `JOIN a TO b AT port` | Permanent connection (threaded/soldered/glued). Proves BFS connectivity. | `ad_assembly_connector` |
| `ATTACH` | `ATTACH elem TO surface` | Non-structural bracket/clip to host surface. | `placement_rules`, `component_definitions` |
| `MOUNT` | `MOUNT elem ON surface WITH bracket` | Bracket/anchor to wall/floor/ceiling. Proves mounting height per code. | `placement_rules` |
| `HANG` | `HANG elem FROM surface WITH hanger SPACING dist` | Suspend from above (rod/strap). Proves hanger count. | `placement_rules` (grid_spacing) |
| `BOLT` | `BOLT a TO b WITH bolt_spec` | Structural bolted connection. Proves bolt count x capacity >= load. | *(future: ad_structural_connection)* |
| `WELD` | `WELD a TO b WITH weld_spec` | Permanent structural fusion. Proves weld capacity >= load. | *(future: ad_structural_connection)* |
| `EMBED` | `EMBED elem IN host COVER depth` | Cast into concrete (rebar, anchor bolt). Proves cover >= code min. | *(future: ad_rebar_cover)* |
| `CLAMP` | `CLAMP elem TO support WITH clamp_type` | Adjustable mechanical fastening. Proves clamp rating >= weight. | *(future: ad_support_type)* |

**Example — common pattern (all 9 verbs follow this):**

```bimcobol
ATTACH sprinkler_23 TO branch_pipe_01
    -- Checks: host_type=PIPE via placement_rules, attachment_face=TOP via component_definitions
    -- W_Verb_Node row: Name='ATTACH sprinkler_23 TO branch_pipe_01', verb_ref='ATTACH', DocStatus='DR'
    -- W_Verb_NodeProduct rows: host_element, attachment_face, connection_type
```

See `DocAction_SRS.md` §1.10 for how joining verbs map to jurisdiction-specific validation (same verb everywhere, different tolerance thresholds per country).

---

## 5. The MEP Routing Problem — In Detail

MEP routing is the first Level 2 verb that matters — it is where manual authoring costs the most time and where errors are most expensive. The `ROUTE SPRINKLERS` verb (§2.4, W-COBOL-9..12) implements the full 8-step pipeline: grid generation within room polygon, beam avoidance, head placement, lateral/branch/main pipe routing, BFS connectivity proof, and NFPA compliance check. See §2.4 ROUTE SPRINKLERS for the full verb spec.

**Key insight:** MEP routes along the ceiling mesh — a 2D pathfinding problem with beam/duct obstacles, analogous to PCB trace routing (EDA). The routing algorithm uses grid graph pathfinding with R-tree spatial queries for clearance checking.

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

**SPEC ONLY — no witnesses.** BIM COBOL has a domain-specific type system where types correspond to construction entities, not programming primitives. Five type families: Spatial (BUILDING, STOREY, ROOM, ZONE, SHAFT, CORE), Element (WALL, SLAB, BEAM, COLUMN, OPENING, ROOF, STAIR, RAILING), MEP System (SPRINKLER/DUCT/DRAINAGE/WATER/ELECTRICAL/GAS_SYSTEM), BOM (UNIT, FLOOR, ROOM_BOM, SET, ITEM), and Type Relationships (containment/hosting/spanning/connecting). The type system enforces construction logic at compile time — you cannot `ROUTE DUCTS IN wall` or `OPEN slab WITH door`. These constraints are type errors, caught before geometry is generated.

---

## 7. Compilation Targets

**SPEC ONLY — no witnesses.** BIM COBOL compiles to three simultaneous outputs: **(1) IFC Geometry** — exact coordinates, meshes, materials, spatial containment for Bonsai/Blender rendering; **(2) Procurement BOM** — `m_bom` hierarchy (UNIT->FLOOR->DISCIPLINE->SET->ITEM), complete bill of materials with quantities (no manual take-off); **(3) Compliance Witnesses** — machine-readable per-rule pass/fail proofs with measured values and code citations. If any of the three is missing, compilation is incomplete.

---

## 8. Grammar Sketch

**SPEC ONLY — no witnesses.** The grammar follows five syntax principles: (1) English-readable — a building inspector can verify intent without understanding IFC; (2) Declarative — no loops, variables, or if/else; the compiler decides HOW, the user declares WHAT; (3) Every noun is typed — `WALL_EXT_MY_200` resolves to M_Product; invalid references are compile errors; (4) Every verb has a proof — no verb executes without a provable outcome; (5) Catalog-driven — extending the language requires catalog entries, not grammar changes. See §2.1 for current DSL vocabulary and §18.11 for the full verb chaining pipeline example.

---

## 9. Relationship to the Bonsai GUI

**SPEC ONLY — no witnesses.** BIM COBOL is the **engine behind** the Bonsai GUI. Every GUI action translates to a BIM COBOL statement (see §18.12 for the full verb-to-GUI mapping table). The GUI is the *editor*, BIM COBOL is the *source code*, the compiler is the *build system*. Key design properties: (1) **Live recompilation** — parameter change triggers incremental recompile of affected zone only; (2) **Round-trip editing** — manual 3D adjustments in Bonsai produce OVERRIDE annotations in the BIM COBOL source, which the compiler honours and re-proves; (3) **TILE in GUI** — user draws surface + picks product + sets spacing = one TILE block expanding to thousands of elements (2D parametric fill that neither Bonsai nor Revit offers today).

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

BIM COBOL's value is not computational — it is **communicative**. A fire engineer can read `ROUTE SPRINKLERS IN "departure" SPACING 3000mm` and certify the result. A fire engineer cannot read equivalent Python code. The domain expert must be able to read the source — that was the whole point of COBOL. Construction has never had this abstraction.

## 12. The Three Compilation Artefacts

See §7. Every BIM COBOL program produces exactly three outputs: IFC/BIM (geometry), BOM (procurement), and Witness (proof). All three are required. None is optional.

---

## 13. Prior Art and Strategic Position

**No existing system combines forward-compilation, BOM generation, and compliance proof from a domain-readable source.** Prior art comparison:

| System | Year | What it does | How BIM COBOL differs |
|--------|------|-------------|----------------------|
| BERA (Georgia Tech) | 2011-2015 | Post-hoc IFC rule checking (circulation, spatial programming) | BIM COBOL is pre-hoc — compiler refuses non-compliant geometry |
| Dynamo / Grasshopper | 2010+ | Visual node-and-wire parametric programming | Procedural and opaque — fire engineer cannot audit a Grasshopper definition |
| mvdXML / IDS (buildingSMART) | 2024 | IFC data presence validation schema | Checks data completeness, not generation. No BOM output |
| BIMSL (Lisbon) | 2023 | BIM + IoT sensor query language for FM | Query language for operational buildings, does not compile new ones |
| LLM + BIM (Text2BIM) | 2024-2025 | Natural language to parametric BIM via ChatGPT | Non-deterministic, no proof chain, no BOM, no compliance guarantee |
| Revit (Autodesk) | 2000+ | Full GUI parametric modelling, 20yr ecosystem | No compile-time compliance, no ERP-ready BOM, no formula-driven placement (1D only), proprietary format |

**BIM COBOL wins:** compile-time compliance, ERP-ready BOM, formula-driven placement (95.8% coverage), deterministic reproduction, domain-expert auditability, scale efficiency (51K->2.9K rows), open format, fully offline.

**Revit wins:** interactive 3D GUI (20yr UX), 10K+ parametric families, construction documents, multi-user collaboration, rendering, ecosystem (Navisworks/ACC/Dynamo), regulatory acceptance, structural/energy analysis.

**Strategic position:** BIM COBOL does not compete with Revit's GUI — it competes with the manual repetitive work inside Revit. BIM COBOL + Bonsai targets **high-repetition, rule-governed** construction (terminals, hospitals, residential developments) where 95%+ of elements follow patterns.

*"The architect writes intent. The compiler produces geometry, BOM, and proof."*

---

## 15. Verbs as Pipeline Applicators — AD_Val_Rules over Spatial Slots

*Added v0.5 — insight from Rosetta Stone analysis*

BIM COBOL verbs are not just standalone validation tools. They are the mechanism by which **cross-discipline MEP rules get applied during the compilation pipeline**, operating over the spatial slot hierarchy (WHERE = M_BOM_Line dx/dy/dz).

### 15.1 The Spatial Slot Hierarchy (M_BOM_Line dx/dy/dz)

The BOM walker produces placement offsets at three levels, each representing a BOM decomposition tier:

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

**The replacement:** Each hardcoded method becomes a BIM COBOL verb that reads its rules from `ad_space_type_mep_bom` and applies them over spatial slots (derived from M_BOM_Line dx/dy/dz) at the appropriate level.

### 15.2 Verb-to-Pipeline Mapping

| StoreyCompiler Method | Replacement Verb | Spatial Slot Level | ad_space_type_mep_bom.placement_rule |
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

The power of verbs over spatial slots (M_BOM_Line dx/dy/dz) is **cross-discipline checking**. Currently each MEP system is placed independently. With verbs:

```bimcobol
-- Level 1: storey-level routing (main pipe runs)
ROUTE SPRINKLERS IN storey MAIN_FROM shaft_fp_1 PIPE_SIZE 65mm

-- Level 2: room-level placement (per spatial slot from M_BOM_Line)
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
Stage 6: VerbStage           — BIM COBOL verbs → MEP elements over spatial slots  ★ NEW
Stage 7: DigestStage         — spatial fingerprinting
Stage 8: GeometryStage       — mesh integrity verification
Stage 9: ProveStage          — mathematical placement proofs
```

#### Verb Storage — W_Verb_Node Model (Design Decision 2026-03-04)

Verb invocations are stored as structured rows, following the iDempiere Manufacturing
`W_Verb_Node` + `W_Verb_NodeProduct` pattern. This replaces the earlier inline
`c_order.verb_script` TEXT column proposal.

**Rationale:** Multi-user GUI editing, per-verb lifecycle tracking (DR→IP→CO→VO),
queryable parameters, Bonsai form-based verb editing, and familiar iDempiere ERP pattern.
See `docs/DocAction_SRS.md` §1 for the iDempiere parallel.

```sql
-- iDempiere Manufacturing: W_Verb_Node = one production operation step
-- BIM semantics: one verb invocation (TILE SURFACE, ARRAY, ROUTE SPRINKLERS...)
-- Lives in output.db (transaction data, not {PREFIX}_BOM.db dictionary)
CREATE TABLE W_Verb_Node (
    W_Verb_Node_ID       INTEGER PRIMARY KEY AUTOINCREMENT,
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

-- iDempiere Manufacturing: W_Verb_NodeProduct = material consumed/produced per operation
-- BIM semantics: structured parameters per verb (ORIGIN_X, GRID_NX, SPACING_MM...)
CREATE TABLE W_Verb_NodeProduct (
    W_Verb_NodeProduct_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    W_Verb_Node_ID         INTEGER NOT NULL REFERENCES W_Verb_Node(W_Verb_Node_ID),
    Name                     TEXT NOT NULL,    -- BIM: param_name (SURFACE_NAME, ORIGIN_X, GRID_NX...)
    Value                    TEXT NOT NULL,    -- BIM: param_value
    ValueType                TEXT DEFAULT 'TEXT'
        CHECK(ValueType IN ('TEXT','REAL','INTEGER')),
    UNIQUE(W_Verb_Node_ID, Name)
);
```

**Example — Terminal roof tiling stored as verb lines:**

| W_Verb_Node_ID | C_Order_ID | SeqNo | Name (verb) | Description (COBOL source) | DocStatus |
|---|---|---|---|---|---|
| 1 | Terminal | 10 | TILE SURFACE | ROOF_DECK_Z19_WEST WITH PLATE... GRID 15 294 STEP 495 150 | DR |
| 2 | Terminal | 20 | TILE SURFACE | ROOF_DECK_Z19_CENTRAL WITH PLATE... GRID 14 174 STEP 495 150 | DR |
| 3 | Terminal | 30 | ARRAY | SLAB_TE_GF_001 WITH REBAR_T16 LENGTH 6000 SPACING 150 COVER 40 | DR |
| 4 | Terminal | 40 | ROUTE SPRINKLERS | Terminal "Departure Hall" SPACING 3000 | DR |

**Corresponding W_Verb_NodeProduct rows for W_Verb_Node_ID=1:**

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
`W_Verb_NodeProduct` rows hold the same data in structured form (what the GUI edits).
Both stay in sync — the text is the COBOL source; the params are the form fields.

**VerbStage execution:**
1. Read `W_Verb_Node` WHERE C_Order_ID = ? AND IsActive = 1 ORDER BY SeqNo
2. For each line: dispatch to VerbRegistry by `verb_keyword`
3. Each verb reads `ad_space_type_mep_bom` for the room's space_type
4. Each verb emits IFC elements into output.db
5. Each verb produces a witness (compliance proof) → stored in `last_result` JSON
6. Update `element_count` and promote `doc_status` DR→IP→CO
7. Cross-discipline clearance checked via R-tree spatial index

This replaces `placeMEPSprinklers()`, `placeHVAC()`, `placeElectrical()`, `mepBomGapFill()` with a data-driven, declarative, provable verb pipeline.

#### C_OrderLine Separation — What Moves to Verb Tables (DECIDED 2026-03-04)

**Architectural invariant:** c_orderline is WHAT-only (no placement columns, no position setters in Java PO). W_Verb_Node completes the split: placement -> `W_Verb_Node`/`W_Verb_NodeProduct` (HOW), spatial containers -> M_BOM_Line dx/dy/dz (WHERE; co_empty_space tables removed S74). Migration: Phase 1 (current) verb tables added, Phase 2 VerbStage with RelationalResolver fallback, Phase 3 drop placement columns. Full analysis: `BBC.md` §1.

> **Phase 1 — CURRENT:** Create verb tables in {PREFIX}_BOM.db (DDL above). Additive, no breakage.
> c_orderline becomes WHAT-only — Java PO class has no placement setters.
>
> **Phase 2:** VerbStage fallback logic: W_Verb_Node rows present → VerbRegistry dispatch;
> absent → RelationalResolver fallback (to be removed in Phase 3).
>
> **Phase 3:** Drop placement columns from c_orderline. Remove RelationalResolver.
> Migrate SH/DX extracted data to verb recipes.
>
> **RESOLVED:** co_empty_space tables removed S74 (W008). Placement via M_BOM_Line dx/dy/dz.
> W_Verb_Node links to C_OrderLine directly (no intermediate spatial slot table).

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
2. **Spatial slot L2 population** (TODO-ST-3) — VerbStage iterates L2 slots (from M_BOM_Line dx/dy/dz). These must exist for all rooms, not just structural tiers.
3. **RelationalResolver removed from critical path** — VerbStage computes placement from room AABB + ad_placement_rule, not from RelationalResolver.

### 16.3 Preparation Work (BIM_COBOL Side — Can Start NOW)

Four workstreams that build language infrastructure without touching the pipeline:

**PREP-1: VerbRegistry + Dispatcher** ✅ DONE (W-COBOL-41..42)

`VerbRegistry.java` — central map of `keyword → Verb<?>` with `createDefault()` (all 77 verbs), `dispatch()` (longest-prefix match), tokenizer preserving `"quoted strings"`.

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
  - Receives output.db Connection + {PREFIX}_BOM.db Connection
  - Iterates C_OrderLine WHERE bom_level = 2 (rooms) — (co_empty_space removed S74)
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

## 17. Post-Compilation Quality Verbs

> **Status:** Partially implemented. First verb wired: TRIM WALLS TO ROOF (Phase F). Remaining candidates deferred.

### 17.1 Implemented

| Keyword | Args | What it does | Witnesses |
|---------|------|-------------|-----------|
| `TRIM WALLS TO ROOF` | (none) | Measure roof AABB surface at each wall centroid. Ridge along longer axis, linear slope from eave (minZ) to crown (maxZ). Walls exceeding roof surface by >50mm flagged for trimming. Flat roofs (minZ≈maxZ) → slope≈0 naturally. Returns slope angle, direction, and profile type per entry. | W-TRIM-1 through W-TRIM-7 |
| `FORGE` | `<piece_type> [key:value ...]` | Formula-driven construction piece computation. 6 engines: SLOPE_CUT (rafter trig), STAIR_FLIGHT (rise-over-run + Blondel), PIPE_BEND (arc geometry), DOME_SECTION (ring×segment panels), BARREL_VAULT (rib arc), REBAR_CAGE (slab/beam/column reinforcement with exposure-class cover). Fabrication data persisted to `ad_forge_fabrication` in output.db. ParametricMesh replaced by ForgeEngine. See [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md), [FORGE_SUITE_SRS](FORGE_SUITE_SRS.md). | W-FORGE-1..11 |

### 17.2 Proposed (Not Yet Implemented)

Candidate verbs: CHECK CLASH, CHECK CONTAINMENT, VERIFY ROOF COVERAGE, CUT OPENINGS, SEAL ENVELOPE. M_AttributeSet_Verb junction table would dispatch verbs automatically by product type.

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
│  {PREFIX}_BOM.db                                                         │
│  m_bom ◀──── new rows                                          │
│  m_bom_line ◀──── new children                                  │
│  m_product_category_line ◀──── new template rules (Level 4 only)│
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
C_Order                         →    C_Order (the work order — "build this building")
C_OrderLine                     →    C_OrderLine (copy of recipe for this specific build)
W_Verb_Node                   →    W_Verb_Node (operations — verb invocations)
M_Product                       →    M_Product (the item being made or consumed)
M_Warehouse / M_Locator        →    M_BOM_Line dx/dy/dz (spatial relationships — WHERE things go)
```

**What BIM adds that ERP doesn't have:**
- `dx/dy/dz` — spatial tack offset (ERP has no concept of "where in the warehouse to place a component")
- `rotation_rule` — orientation (ERP components don't rotate)
- `allocated_width/depth/height_mm` — spatial envelope (ERP has weight/volume but not bounding box)

These three extensions — tack, rotation, envelope — are what turn a manufacturing BOM into a spatial BOM. The rest of the model (product tree, component types, work orders, operations) is identical.

**What this means for the language:**
- BOM primitive verbs are the **PP_Product_BOM_Line** operations: CREATE, ADD, SET, REMOVE
- Convenience verbs are the **C_Order** operations: COMPOSE (= create work order from recipe)
- Template verbs are the **AD_Table/AD_Column** operations: DEFINE, ADD RULE (= extend the dictionary)

The ERP Manufacturing module has been proven in production for 20+ years. BIM COBOL inherits that stability. The spatial extensions (tack, rotation, envelope) are the only novel additions — and they are already proven by the SH/DX Rosetta Stones.

### 18.4 Level 0 — BOM Primitive Verbs (The Machine Layer)

These are the atomic operations on `m_bom` and `m_bom_line`. Every higher-level verb decomposes into a sequence of these primitives. They are the "assembler" of the BOM composition language.

#### CREATE BOM

Create a new `m_bom` row. The fundamental building block.

```bimcobol
CREATE BOM SY_KITCHEN_A TYPE SET CATEGORY KT
    -- Creates m_bom: bom_id=SY_KITCHEN_A, m_product_category_id=KT
    -- No children yet — an empty container

CREATE BOM SY_FLOOR_GF TYPE FLOOR CATEGORY L1
CREATE BOM SY_UNIT_01 TYPE UNIT CATEGORY RE DOC_SUB_TYPE SY
```

**Writes to {PREFIX}_BOM.db:** 1 `m_bom` row.
**Payload:** `CreateBomPayload(bomId, bomType, productCategory, docSubType)`

#### ADD LINE

Add a child to a BOM. This is the core composition operation — one `m_bom_line` row.

```bimcobol
ADD LINE TO SY_KITCHEN_A CHILD Base_Cabinet ROLE BASE_CABINET SEQ 10
    -- Adds m_bom_line: parent=SY_KITCHEN_A, child=Base_Cabinet

ADD LINE TO SY_KITCHEN_A CHILD Base_Cabinet ROLE BASE_CABINET_2 SEQ 20 DX 0.6
    -- Second cabinet, offset 600mm to the right

ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_A SEQ 10 ROTATION 0
ADD LINE TO SY_DUPLEX_SET CHILD SY_HALF_UNIT ROLE UNIT_B SEQ 20 ROTATION 3.14159
    -- DX party-wall mirror via rotation_rule — see §18.3
```

**Writes to {PREFIX}_BOM.db:** 1 `m_bom_line` row.
**Payload:** `AddLinePayload(bomId, childProductId, role, sequence, dx, dy, dz, rotationRule)`

#### SET TACK

Update the spatial offset (dx/dy/dz) on an existing `m_bom_line`. This is how elements move.

```bimcobol
SET TACK ON SY_UNIT_01 LINE ROOF_ASSEMBLY DZ 6.0
    -- Roof sits 6m above ground

SET TACK ON SY_FLOOR_GF LINE SH_LIVING_SET DX 2.5 DY 1.0
    -- Living room offset from floor origin
```

**Writes to {PREFIX}_BOM.db:** Updates `m_bom_line.dx/dy/dz`.
**Payload:** `SetTackPayload(bomId, childProductId, role, dx, dy, dz)`

#### SET ROTATION

Update the rotation_rule on an existing `m_bom_line`. This is how elements orient — including the DX party-wall mirror (see §18.3).

```bimcobol
SET ROTATION ON SY_DUPLEX_SET LINE UNIT_B TO 3.14159    -- party-wall mirror (§18.3)
SET ROTATION ON SY_TERRACE_ROW LINE UNIT_3 TO 1.5708    -- 90° corner lot rotation
```

**Writes to {PREFIX}_BOM.db:** Updates `m_bom_line.rotation_rule`.
**Payload:** `SetRotationPayload(bomId, childProductId, role, rotationRule)`

#### SET DIMENSIONS

Update the allocated envelope (width/depth/height) on a `m_bom_line`. This is how rooms resize.

```bimcobol
SET DIMENSIONS ON SY_FLOOR_GF LINE SY_KITCHEN_A WIDTH 3500 DEPTH 2500 HEIGHT 2800
    -- Kitchen slot is 3.5m × 2.5m × 2.8m

SET DIMENSIONS ON SY_UNIT_01 LINE SY_FLOOR_GF WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Ground floor occupies 10m × 8m × 2.8m within the unit
```

**Writes to {PREFIX}_BOM.db:** Updates `m_bom_line.allocated_width_mm/depth_mm/height_mm`.
**Payload:** `SetDimensionsPayload(bomId, childProductId, role, widthMm, depthMm, heightMm)`

#### SET LINE PROPERTY

General-purpose property setter for any `m_bom_line` column.

```bimcobol
SET LINE PROPERTY ON SY_KITCHEN_A LINE Base_Cabinet COMPONENT_TYPE BUY
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A ROLE KITCHEN
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A LOCATOR_REF NORTH_WALL
SET LINE PROPERTY ON SY_FLOOR_GF LINE SY_KITCHEN_A ORIENTATION EAST
```

**Writes to {PREFIX}_BOM.db:** Updates specified column on `m_bom_line`.
**Payload:** `SetPropertyPayload(bomId, childProductId, property, value)`

#### REMOVE LINE

Delete a child from a BOM.

```bimcobol
REMOVE LINE FROM SY_KITCHEN_A CHILD Upper_Cabinet_4
    -- Deletes one m_bom_line row

REMOVE LINE FROM SY_KITCHEN_A ROLE BUFFER
    -- Deletes by role
```

**Writes to {PREFIX}_BOM.db:** Deletes 1 `m_bom_line` row.
**Payload:** `RemoveLinePayload(bomId, removedChild, removedRole)`

#### DELETE BOM

Delete a BOM and all its children lines. Cascading delete.

```bimcobol
DELETE BOM SY_KITCHEN_A
    -- Deletes m_bom row + all m_bom_line rows where bom_id=SY_KITCHEN_A
    -- FAIL if any other BOM references SY_KITCHEN_A as a child (referential integrity)
```

**Writes to {PREFIX}_BOM.db:** Deletes 1 `m_bom` row + N `m_bom_line` rows.
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
    -- Uses M_Product_Category min/max constraints
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
    -- FAIL: minimum kitchen width is 1800mm (M_Product_Category_Line.min_width_mm)

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
    -- Prefer DX-owned products (building prefix filter)
    -- Falls back to catalog-wide if no DX match

CREATE ROOM LIVING 8000 5000 2800 EMPTY
    -- Creates m_bom with zero children — a slot for manual population
    -- The GUI uses this + FURNISH ROOM to populate interactively
```

**Writes to {PREFIX}_BOM.db:** 1 `m_bom` row + N `m_bom_line` rows.
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

**Writes to {PREFIX}_BOM.db:** N `m_bom_line` rows appended to existing `m_bom`.
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

**Writes to {PREFIX}_BOM.db:** 1 new `m_bom` + N `m_bom_line` rows (clone with modifications).
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

**Writes to {PREFIX}_BOM.db:** Deletes `m_bom_line` rows from existing `m_bom`.
**Payload:** `StripPayload(bomId, removedCount, remainingCount)`

### 18.7 Level 2 — Floor-Level Verbs (Room Composition)

These create FLOOR BOMs by composing rooms into a storey. A floor is an `m_bom` whose children are room SETs, each with allocated dimensions and tack offsets.

#### CREATE FLOOR

Create a new floor BOM from a room list. Allocates AABB to each room and creates `m_bom_line` entries. Uses PARTITION AABB internally to compute spatial layout.

```bimcobol
CREATE FLOOR GF ROOMS LI DN KT BD BT WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Partitions 10000x8000 into 5 rooms
    -- Each room: finds best-fit SET from catalog
    -- Creates m_bom: bom_id=FLOOR_GF_10000x8000, m_product_category_id=GF
    -- Creates m_bom_line per room with dx/dy/dz offsets

CREATE FLOOR L2 ROOMS BD BD BD BT KT WIDTH 10000 DEPTH 8000 HEIGHT 2800
    -- Upper floor: 3 bedrooms + bathroom + kitchen
    -- Same mechanism, different room mix
```

**Writes to {PREFIX}_BOM.db:** 1 `m_bom` + N `m_bom_line` rows.
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

**Writes to {PREFIX}_BOM.db:** 1 `m_bom_line` row appended.
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

**Writes to {PREFIX}_BOM.db:** Deletes 1 `m_bom_line` row.
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

**Writes to {PREFIX}_BOM.db:** Updates 1 `m_bom_line` row (child_product_id).
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

**Writes to {PREFIX}_BOM.db:** 1 root `m_bom` + full tree of child `m_bom` + `m_bom_line` rows.
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

**Writes to {PREFIX}_BOM.db:** 2 `m_bom` rows (slab + floor) + N `m_bom_line` rows. Updates existing roof dz.
**Payload:** `AddFloorPayload(unitBomId, floorBomId, slabBomId, newRoofDz, roomCount)`

#### Mirroring, Rotation, Terrace Rows — No Special Verbs Needed

These are NOT verbs. They are BOM data — expressed via Level 0 primitives. The tack model (dx/dy/dz + rotation_rule) is already a complete spatial instruction set (see §18.3 for the foundational insight and DX duplex proof). Duplex mirrors, terrace row alternation, and corner-lot rotation are all just `ADD LINE ... ROTATION <float>` calls.

#### STACK FLOORS

Auto-compute dz offsets for all floors in a unit. Reads slab thicknesses and floor heights from the BOM tree and sets `m_bom_line.dz` accordingly.

```bimcobol
STACK FLOORS IN SY_MY_DUPLEX
    -- Reads each FLOOR and SLAB child
    -- Computes: GF at dz=0, SLAB_L2 at dz=2800, L2 at dz=3000, ROOF at dz=5800
    -- Updates m_bom_line.dz for each child
```

**Writes to {PREFIX}_BOM.db:** Updates `m_bom_line.dz` values for existing children.
**Payload:** `StackPayload(unitBomId, floorCount, floorOffsets[], totalHeight)`

### 18.9 Level 4 — Catalog-Level Verbs (Template Grammar)

These modify the `M_Product_Category` classification that drives COMPOSE BUILDING. This is the metaprogramming level — changing the rules that generate buildings, not the buildings themselves.

#### DEFINE CATEGORY

Create a new M_Product_Category entry. This adds a room type, structural type, or spatial concept to the grammar.

```bimcobol
DEFINE CATEGORY CL CLASSROOM doc_type Commercial
    -- Creates M_Product_Category: category_id=CL, name=Classroom, doc_type=Commercial

DEFINE CATEGORY LB LOBBY doc_type Commercial
DEFINE CATEGORY CF CAFETERIA doc_type Commercial
DEFINE CATEGORY WS WORKSTATION doc_type Commercial
```

**Writes to {PREFIX}_BOM.db:** 1 `m_product_category` row.
**Payload:** `DefineCategoryPayload(categoryId, name, docType)`

#### ADD TEMPLATE RULE

Insert a containment rule — defines the parent→child relationship with constraints.

```bimcobol
ADD TEMPLATE RULE GF CONTAINS CL MIN 4 MAX 8
    -- A ground floor in this template has 4–8 classrooms
    -- BomTemplateComposer will allocate AABB to each CL slot

ADD TEMPLATE RULE L2 CONTAINS BD MIN 1 MAX 3 Z_EXTENT 0.5
    -- Upper floor: 1–3 bedrooms, each gets 50% of floor height

ADD TEMPLATE RULE PR CONTAINS HU MIRRORING PARTY_WALL_PI
    -- Pair → Half-Unit with party wall mirroring (the DX pattern)
```

**Writes to {PREFIX}_BOM.db:** 1 `m_product_category_line` row.
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

**Writes to {PREFIX}_BOM.db:** Updates 1 `m_bom` row (bom_category, allocated dimensions).
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

**Writes to {PREFIX}_BOM.db:** Full clone of source tree with dimension-adjusted selections.
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

**Writes to {PREFIX}_BOM.db:** New root `m_bom` + mixed child tree.
**Payload:** `DerivePayload(sourceBomId, newBomId, reusedFloors[], replacedFloors[], addedFloors[])`

### 18.11 Verb Chaining — The Feedback Pipeline

Synthetic BOM verbs are designed to chain. The output payload of one verb feeds as input to the next. This is how the Bonsai GUI builds a building interactively:

```bimcobol
EXTRACT AABB FROM POINTS (0,0,0) (12.0,10.0,6.0)       -- → WIDTH 12000 DEPTH 10000 HEIGHT 6000
SNAP TO GRID AABB 12000 10000 SPACING 1000                -- → snapped dimensions
VALIDATE AABB 12000 10000 6000 FOR RESIDENTIAL UNITS 2    -- → valid=true
PARTITION AABB 12000 10000 3000 INTO ROOMS LI DN KT BD BT -- → 5 room slots (GUI preview)
COMPOSE BUILDING RESIDENTIAL 12000 10000 6000 UNITS 2     -- → 12 BOMs, 47 lines
RESIZE ROOM KITCHEN_3500x2500 TO 4000 3000 2800 AS KITCHEN_4000x3000  -- → 2 cabinets added
ADD FLOOR MEZZANINE TO SY_RE_12000x10000 HEIGHT 2400 ROOMS LI        -- → roof shifted up
STACK FLOORS IN SY_RE_12000x10000                          -- → GF=0, MEZZANINE=3000, ROOF=5600
PLACE BOM SY_RE_12000x10000                                -- → output.db
SUMMARIZE BUILDING RE                                      -- → GUI renders 3D preview
```

Each step is an atomic verb with a typed payload. The GUI can checkpoint, undo, and resume from any point.

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

### 18.13 Write Discipline — {PREFIX}_BOM.db Mutation Rules

All synthetic BOM verbs write to {PREFIX}_BOM.db. This breaks the "{PREFIX}_BOM.db = read-only dictionary" rule (§11.2). The distinction is enforced by **EntityType**, not naming prefix:

1. **Dictionary BOMs** (entity_type='D') — extracted from IFC Rosetta Stones or curated by hand. Protected by EntityType enforcement in MBOM.beforeSave()/delete(). Names follow iDempiere convention: BUILDING_SH_STD, FLOOR_DX_L1_STD, SH_LIVING_SET, etc.
2. **Synthetic BOMs** (entity_type='U', SY_ prefix) — created by verbs. Mutable. The SY_ prefix distinguishes machine-generated from curated BOMs.

```
{PREFIX}_BOM.db protection model:
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

The verb suite scales by adding `M_Product_Category` entries and `m_bom` catalog rows (SQL data). No Java changes. The grammar grows; the engine stays fixed.

**Terminal (51K elements) decomposition roadmap:**
1. Extract Terminal rooms into SET-level BOMs (Phase B)
2. `REGISTER BOM` each SET into appropriate category (CK, DP, BG, etc.)
3. `ADD TEMPLATE RULE` for commercial/institutional grammar
4. `COMPOSE BUILDING COMMERCIAL ...` now produces Terminal-scale buildings from catalog

### 18.15 Rosetta Stone Ingestion — Primitives for Extraction

Rosetta Stone ingestion extracts real buildings into BOM trees using the same primitives that create synthetic BOMs (§18.4). The extraction chain (IFC -> IfcOpenShell -> classification -> M_Product -> m_bom/m_bom_line -> REGISTER BOM) decomposes entirely into P0 verb sequences. Extraction primitives and synthetic primitives are the same primitives — the only difference is the source (IFC reference vs user intent). See `TheRosettaStoneStrategy.md` for the full extraction spec and verification tiers. Phase B convenience verbs (EXTRACT ZONE, DECOMPOSE BUILDING, CATALOG EXTRACT) will wrap these primitive sequences.

### 18.16 Language Constructs Derived from Primitives

BIM COBOL primitives compose into higher-level language constructs: variables (BOM references via CREATE/DESCRIBE/DELETE), loops (repetition via multiple ADD LINE calls — the ARRAY verb is sugar over this), conditionals (VALIDATE as guard), composition (MAKE nesting for unbounded BOM depth), copy/template (CLONE BOM as constructor), transactions (verb atomicity via DAO commit/rollback), and introspection (LIST/SELECT/DESCRIBE/COUNT/AGGREGATE as reflection). See individual verb descriptions in §18.4 and §F0.x for details. The ERP Manufacturing module (PP_Product_BOM, C_Order, W_Verb_Node, etc.) maps directly — 5 of 11 constructs already implemented.

### 18.17 Verb Componentisation — Separation of Concerns

Verbs are layered — each layer calls ONLY the layer directly below it. Never skip layers.

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

**Example — CreateRoomVerb (Level 1) calls only Level 0 + Utility:**

```java
// CreateRoomVerb.java — Level 1 convenience verb
public VerbResult<CreateRoomPayload> execute(VerbContext ctx, String... args) {
    VerbResult<?> valid = validateAabbVerb.execute(ctx, category, w, d, h);  // utility
    if (!valid.isOk()) return VerbResult.fail(...);
    VerbResult<?> bom = createBomVerb.execute(ctx, bomId, "SET", category);  // L0
    for (MProduct product : selectedProducts)
        addLineVerb.execute(ctx, bomId, product.getId(), role, seq, dx, dy, dz);  // L0
    setDimensionsVerb.execute(ctx, bomId, childId, w, d, h);  // L0
    return VerbResult.ok(...);
}
```

**One bug = one file = one fix.** Higher-level verbs inherit fixes automatically because they delegate to primitives. `VerbRegistry.createDefault()` provides constructor injection (same pattern as iDempiere ModelValidator chains).

### 18.18 Terminal BOM Conversion — Multi-Discipline Analysis at Scale

*The Sultan Johor Terminal II (SJTII) is a 4-storey institutional building with 51,088 elements across 9 disciplines, federated from multiple consultant IFC files into a single reference database. It is the stress test for everything in §18.*

#### The Scale Problem

Residential BOMs are small (SH is smallest, DX medium). The BOM trees are 3–4 levels deep. A human can read them. A human WROTE them (as SQL INSERTs).

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

Key challenges residential BOMs never face: (1) discipline overlap/deduplication across federated IFC files; (2) IFC class ambiguity (500+ IfcBuildingElementProxy need property-based classification); (3) system topology (MEP connectivity trees, not flat lists); (4) parametric repetition at scale (33K plates = 19 TILE formulas); (5) storey mismatch across discipline models.

#### Analysis Verbs for Terminal-Scale Extraction

Read-only verbs that analyse extracted reference databases to discover BOM structure. Output feeds into creation primitives (§18.4).

| Verb | Purpose |
|------|---------|
| `CENSUS BUILDING` | Element counts per discipline/storey/IFC class |
| `DISCOVER PATTERNS` | Spatial pattern detection per discipline (TILE/FRAME/ARRAY/ROUTE formulas) |
| `CLASSIFY ELEMENTS` | Property-based reclassification of IfcBuildingElementProxy |
| `DISCOVER ZONES` | Spatial clustering (DBSCAN) for room/zone boundaries |
| `ANALYSE SYSTEMS` | MEP connectivity tree recovery (fitting->segment chains) |
| `NORMALISE STOREYS` | Cross-discipline storey alignment |

#### The Terminal Decomposition Pipeline (Verb Sequence)

8-phase pipeline: (1) CENSUS + NORMALISE STOREYS, (2) DISCOVER PATTERNS per discipline (CW->TILE, STR->FRAME/ARRAY, FP->ROUTE, ELEC->WIRE, ACMV->ROUTE DUCT), (3) CLASSIFY proxy elements, (4) DISCOVER ZONES per storey, (5) CREATE BOM + ADD LINE per zone, (6) REGISTER BOMs into catalog, (7) DEFINE CATEGORY + ADD TEMPLATE RULE for commercial grammar, (8) COMPOSE BUILDING COMMERCIAL to prove round-trip. Full decomposition is Phase B scope — the primitives and analysis verbs are abstract across building scale.

#### Institutional-Scale IFC Patterns

Large federated IFC models (50K+) exhibit 6 invariant patterns: (1) power-law class distribution (top 3 classes = 80%); (2) hierarchical spatial locality (DBSCAN clustering); (3) system trees not flat element lists (MEP connectivity); (4) consultant = discipline boundaries (provenance tracking); (5) parametric regularity with exceptions (91% regular, 9% flat); (6) cross-discipline coordination zones (4+ disciplines in 500mm ceiling void). These patterns are invariant across airports, hospitals, schools, factories.

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

The key win: **validate all references before writing anything to {PREFIX}_BOM.db.** Without this, if line 3 of a 10-line script has a bad product ID, lines 1–2 have already mutated the database. A compiler rejects the whole script upfront.

**Evolution path:**

| Stage | When | What |
|-------|------|------|
| Interpreter | Now (P0–P3) | Verb-at-a-time execution. Same pattern as F0.x data verbs. |
| Script runner | Phase F5 | Thin parse→validate→execute pipeline. Read all lines, validate references, execute in a single transaction. Not a full compiler — just a validated batch runner. |
| Full compiler | If needed | AST + optimization passes. Only if script complexity demands it. |

The COBOL/Java analogy holds in the *domain language* sense (construction intent compiles to assembler-level IFC), but the implementation does not need a traditional compiler architecture yet. The 11-stage DAGCompiler pipeline already IS the "compiler" — BIM COBOL verbs are a higher-level way to feed it.

---

## 19. Verb Pattern Detection — BOM Recipe Factorization (FACTORIZE-v1)

**Status:** LIVE (2026-03-17, CLUSTER optimised 2026-03-18). 48,485 → 1,131 lines (42.8:1 compression).

Verb patterns are the BIM_COBOL mechanism for compressing repeated placements into
recipe lines. VerbDetector mines patterns from I_Element_Extraction data;
PlacementCollectorVisitor expands them back to positions at compile time.

### Verb Taxonomy (Detection Verbs)

| Verb | Pattern | verb_ref format | TE instances |
|------|---------|-----------------|--------------|
| **TILE** | 2D uniform grid | `TILE:nx:ny:stepX:stepY` | 12 |
| **ROUTE** | Axis-aligned runs | `ROUTE:X:step:n\|Y:step:n\|...` | 18 |
| **FRAME** | Grid intersections | `FRAME:x1,x2,...\|y1,y2,...` | 78 |
| **CLUSTER** | Catch-all exact offsets | `CLUSTER:dx,dy,dz,w,d,h;...` | 47,607 |
| ~~SPRAY~~ | ~~Semi-regular grid~~ | ~~`SPRAY:stepX:stepY`~~ | **DEPRECATED** — superseded by CLUSTER (session 32) |

**Future:** ARRAY, STACK, MIRROR, WRAP, BRANCH, SCATTER.

### ERP Manufacturing Model Mapping

| BIM_COBOL | iDempiere ERP | Purpose |
|-----------|---------------|---------|
| verb_ref | AttributeSetInstance | Formula parameters |
| qty | M_BOM_Line.qty | Production quantity |
| dx/dy/dz | W_Verb_Node position | Pattern origin |
| AD_Val_Rule | AD_Val_Rule | Compliance constraints |

### Non-Disturbance Principle

Patterns are mined from extraction (RosettaStone baseline). The verb formula must
reproduce exact centroids within 5mm tolerance. Groups < 4 elements always fall
through to unfactored writes (SH/DX safe).

### verb_ref Format Specification

All coordinates in **metres**, floor-relative. dx/dy/dz on the BOM line = pattern origin.

```
TILE:nx:ny:stepX:stepY           — 2D grid, nx*ny instances
ROUTE:X:step:n|Y:step:n|...     — axis-aligned legs chained
FRAME:x1,x2,...|y1,y2,...        — gridline positions (floor-relative)
CLUSTER:dx,dy,dz,w,d,h;...      — exact per-instance offsets + dimensions (lossless)
# SPRAY removed — replaced by CLUSTER (session 32)
```

### Data Flow

```
Phase 1: Populate (one-time, IFCtoBOMMain --populate)
  reference DB ──ExtractionPopulator──→ component_library.db

Phase 2: BOM Pipeline (IFCtoBOMPipeline — reads component_library.db)
  ExtractionElement[] ──group by product──→ VerbDetector.detect()
    ├─ pattern found  → 1 line: verb_ref + qty=N + origin dx/dy/dz
    └─ no pattern     → N lines: qty=1, per-instance dx/dy/dz

Phase 3: Compilation (PlacementCollectorVisitor)
  m_bom_line.verb_ref ──expandVerb()──→ double[qty][6] offsets (dx,dy,dz,w,d,h)
    ├─ TILE    → origin + ix*stepX, iy*stepY
    ├─ ROUTE   → per-leg start + i*step along axis
    ├─ FRAME   → cartesian product of gridlines
    └─ CLUSTER → exact per-instance offsets + dimensions (lossless)
```

### TE Results (2026-03-19)

```
Recipe lines:     1,131        (was 48,485)
Compression:      42.8:1
Verb coverage:    98.4%        (47,715 of 48,485 instances)
Verb breakdown:   CLUSTER 47,607  FRAME 78  ROUTE 18  TILE 12
Flat (unfactored): 770 lines
```

### Fidelity Check Status

| Verb | Instances | Max Error | Avg Error | Status | Cause |
|------|-----------|-----------|-----------|--------|-------|
| TILE | 12 | 0.0000m | 0.0000m | PASS | Uniform grid — exact match |
| FRAME | 60 | 0.0001m | 0.0001m | PASS | Grid intersections — exact match |
| ROUTE | 533 | ~1,273m | ~295m | FAIL | Multi-leg chaining (inter-leg offset) |
| CLUSTER | 47,607 | 0.029m | 0.004m | PASS | Lossless per-instance encoding |

**Known gap (VPA-002):** ROUTE per-leg step-uniformity not verified for multi-leg chains — 533 instances with multi-metre errors.

### Known Limitation: ROUTE Step Uniformity (Gap 6)

`VerbDetector.detectRoute()` chains elements by checking the **constant axis** stays within 5mm tolerance, but does **not** verify **uniform step** on the varying axis. The step is computed as `(last - first) / (count - 1)` — a simple average.

**Fix (R8):** Step-uniformity guard added to `countAxisRun()` — rejects groups where `max_step / min_step > threshold`. Non-uniform groups fall back to CLUSTER.

**Code:** `VerbDetector.java:386-396` (countAxisRun — only checks constAxis)

### Mathematical Basis (Theorems 1+5)

- **Theorem 1 (CLT):** Pattern aggregation reduces variance
- **Theorem 5 (Information Theory):** Buildings are inherently compressible — 42.8:1 compression is mathematically justified

---

## 20. Spatial Predicate Verbs — Reusable Query Primitives

**Status:** SPEC (2026-03-19, session 24)
**Depends on:** output.db schema (`elements_meta`, `elements_rtree`, `element_instances`),
DocValidate.md §15.5 (M1-M17 rules), DATA_MODEL.md §4 (output.db)

### 20.1 The Problem: Spatial Boilerplate

Every validation rule (M1-M17), every CHECK verb, and every future spatial query
re-invents the same patterns as ad-hoc SQL:

```sql
-- M1: Nearest sprinkler to sprinkler (ad-hoc in PlacementValidator)
SELECT b.guid, MIN(dist(a.cx, a.cy, b.cx, b.cy)) ...
-- M12: Pipe clearance (ad-hoc arithmetic)
SELECT centreline_dist - radius_a - radius_b ...
-- M13: Vertical continuity (ad-hoc ROUND grouping)
SELECT ROUND(min_x, 1), ROUND(min_y, 1), COUNT(DISTINCT storey) ...
-- M17: Opening host association (ad-hoc AABB proximity)
SELECT wall.guid WHERE wall_aabb CONTAINS opening_centroid ...
```

Each rule author writes raw R-tree joins, centroid arithmetic, and tolerance
comparisons from scratch. This is the spatial equivalent of every Java class
reimplementing `String.equals()`. The output.db schema is rich — `elements_meta`
has world coordinates, `elements_rtree` has spatial indexing, `element_instances`
has geometry hashes — but there is no reusable query layer over it.

**iDempiere parallel:** iDempiere has `GRANDTOTAL()`, `LINENETAMT()`,
`currencyConvert()` — standard functions every report and callout uses. Nobody
recomputes tax-inclusive totals from line items manually. Spatial predicates
are the BIM equivalent: standard functions every validation rule and verb uses.

### 20.2 Predicate Catalog

Spatial predicates are **pure queries** — no mutation. They return a scalar
(distance, boolean) or a result set (element list). They compose: CHECK verbs
and AD_Val_Rule implementations call predicates instead of raw SQL.

#### Tier 1 — Element-to-Element Relationships

| Predicate | Signature | Returns | SQL core |
|-----------|-----------|---------|----------|
| `DISTANCE_BETWEEN` | `<guid_a> <guid_b>` | Distance in mm (centroid-to-centroid) | `sqrt((a.cx-b.cx)² + (a.cy-b.cy)² + (a.cz-b.cz)²) * 1000` on `elements_rtree` |
| `CLEARANCE_BETWEEN` | `<guid_a> <guid_b>` | Gap in mm (surface-to-surface) | `MAX(0, axis_dist - half_a - half_b)` per axis, then min |
| `INTERSECTS` | `<guid_a> <guid_b> [CLEARANCE mm]` | Boolean (AABBs overlap) | R-tree range query with optional envelope expansion |
| `HOST_OF` | `<guid>` | Host element guid (nearest containing wall/slab) | AABB containment in 2/3 axes (M16/M17 pattern). Upgrades to FK join when R21 lands |
| `NEAREST` | `<guid> TYPE <ifc_class> [LIMIT n]` | Nearest n elements of type | R-tree window query expanding outward until n found |

#### Tier 2 — Spatial Grouping (Set Queries)

| Predicate | Signature | Returns | SQL core |
|-----------|-----------|---------|----------|
| `SAME_LEVEL` | `<guid> [TOLERANCE mm]` | All elements sharing Z-band | `WHERE ABS(e.cz - ref.cz) <= tolerance` via R-tree Z range |
| `SAME_COLUMN` | `<guid> [TOLERANCE mm]` | All elements sharing XY position across storeys | `WHERE ROUND(x,tol) = ROUND(ref_x,tol) AND ROUND(y,tol) = ROUND(ref_y,tol)` (M13/M14/M15) |
| `WITHIN` | `<container_guid>` | All elements whose AABB sits inside container | R-tree: `minX >= c.minX AND maxX <= c.maxX` (all 3 axes) |
| `ALONG_PATH` | `<system_id>` | Ordered elements forming a connected route | Walk `ad_element_dependency` with `relation='CONNECTS_TO'` or fitting-segment chains |
| `COPLANAR` | `<guid> AXIS <X\|Y\|Z> [TOLERANCE mm]` | Elements sharing a surface plane | `WHERE ABS(e.minZ - ref.minZ) <= tol` (for Z-axis; analogous for X/Y) |

#### Tier 3 — Pattern Detection (Statistical Queries)

| Predicate | Signature | Returns | SQL core |
|-----------|-----------|---------|----------|
| `GRID_PITCH` | `<set> AXIS <X\|Y>` | Dominant spacing in mm + regularity % | Sorted coordinate diffs → median + count-within-tolerance / total |
| `NEAREST_NEIGHBOUR_STATS` | `<set>` | Min/max/mean/median NN distance | All-pairs via R-tree window, track min per element |
| `SPAN_COUNT` | `<set> AXIS <X\|Y\|Z>` | Number of distinct storey/level spans | `COUNT(DISTINCT ROUND(coord, tol))` |

### 20.3 SQL Implementation Sketches

Each predicate maps to 5-15 lines of SQL over `elements_meta` + `elements_rtree`. All use centroid computation `(minX+maxX)/2` and R-tree spatial indexing. Example (DISTANCE_BETWEEN):

```sql
SELECT CAST(1000 * sqrt(
    pow((a.minX+a.maxX)/2 - (b.minX+b.maxX)/2, 2) +
    pow((a.minY+a.maxY)/2 - (b.minY+b.maxY)/2, 2) +
    pow((a.minZ+a.maxZ)/2 - (b.minZ+b.maxZ)/2, 2)
  ) AS INTEGER) AS distance_mm
FROM elements_rtree a, elements_rtree b
JOIN elements_meta ma ON a.id = ma.id
JOIN elements_meta mb ON b.id = mb.id
WHERE ma.guid = ? AND mb.guid = ?
```

Other predicates follow the same pattern: NEAREST uses R-tree window expansion with radius parameter; SAME_COLUMN filters by XY tolerance across storeys; CLEARANCE_BETWEEN computes per-axis gap with overlap detection.

### 20.4 How Predicates Compose into CHECK Verbs and AD_Val_Rule

Predicates replace ad-hoc SQL in CHECK verbs and AD_Val_Rule implementations. Example: M1 sprinkler spacing changes from 20 lines of raw SQL to `ctx.predicate("NEAREST", guid, "IfcFlowTerminal", 1).getInt("distance_mm")` — the rule just checks the threshold. CHECK CLASH rewrites as: for each MEP element, `NEAREST ... TYPE IfcBeam`, then `CLEARANCE_BETWEEN` < 50mm = FAIL.

### 20.5 M-Rule Coverage — Which Predicates Unblock What

| M-Rule | Current Method | Predicate Replacement | Unblocks |
|--------|---------------|----------------------|----------|
| M1 sprinkler spacing | ad-hoc NN SQL | `NEAREST ... TYPE IfcFlowTerminal` | Cleaner rule implementation |
| M2 pipe run length | verb_ref param | `ALONG_PATH` (sum segment lengths) | System-aware pipe validation |
| M4 light grid spacing | ad-hoc NN SQL | `NEAREST ... TYPE IfcLightFixture` + `GRID_PITCH` | Same as M1 |
| M5 ceiling Z offset | ad-hoc Z query | `SAME_LEVEL` + `COPLANAR` | Storey-relative validation |
| M6 column bay spacing | ad-hoc grid SQL | `GRID_PITCH ... AXIS X` | Grid regularity check |
| M9/M10 MEP×STR clash | ad-hoc AABB intersection | `INTERSECTS` + `CLEARANCE_BETWEEN` | Clash detection |
| M12 pipe clearance | ad-hoc centreline maths | `CLEARANCE_BETWEEN` | ERP-maths standardised |
| M13-15 vertical continuity | ad-hoc ROUND grouping | `SAME_COLUMN` | Cross-storey alignment |
| M16 opening face anchor | ad-hoc centroid depth | `HOST_OF` + `DISTANCE_BETWEEN` | Opening validation |
| M17 opening host | ad-hoc AABB proximity | `HOST_OF` | Host association (upgrades with R21) |

**13 of 17 M-rules** would use at least one predicate. The remaining 4 (M2 verb
params, M3 product dims, M7 product width, M8 TILE params) are pure SQL lookups
on BOM/product tables — no spatial query needed.

### 20.6 Java SPI — SpatialPredicate Interface

```java
/**
 * Spatial predicate — reusable query over output.db geometry.
 * Same Verb<T> SPI pattern, but read-only (no VerbContext mutation).
 *
 * @Traces BBC.md §2 — Schema-Not-Geometry: predicates wrap the
 *   legitimate ERP-maths cases so no code writes raw AABB SQL.
 */
public interface SpatialPredicate<T> {
    String name();                              // "NEAREST", "CLEARANCE_BETWEEN"
    T execute(Connection outputConn, String... args);
    Class<T> resultType();                      // Integer, Boolean, List<ElementRef>
}
```

Predicates live in `BIM_COBOL/src/main/java/com/bim/cobol/predicate/`.
They are registered in `PredicateRegistry` (same pattern as `VerbRegistry`).
CHECK verbs and PlacementValidator call `registry.execute("NEAREST", guid, ...)`.

### 20.7 Implementation Priority

| Priority | Predicate | Unblocks | Complexity |
|----------|-----------|----------|------------|
| **P0** | `DISTANCE_BETWEEN` | M1, M4, M16, CHECK CLASH | Trivial (5 lines SQL) |
| **P0** | `CLEARANCE_BETWEEN` | M9, M10, M12, CHECK CLASH | Low (per-axis gap calc) |
| **P0** | `NEAREST` | M1, M4, M17, DISCOVER PATTERNS | Medium (R-tree window) |
| **P0** | `WITHIN` | W-BUFFER-1, W-TACK-1, CHECK ROOM | Low (R-tree containment) |
| **P1** | `SAME_LEVEL` | M5, storey assignment | Low (Z-band filter) |
| **P1** | `SAME_COLUMN` | M13, M14, M15 | Low (XY grouping) |
| **P1** | `HOST_OF` | M16, M17 | Medium (2/3-axis containment, R21 upgrade path) |
| **P2** | `COPLANAR` | TILE verb, M8 | Low (single-axis filter) |
| **P2** | `ALONG_PATH` | M2, ANALYSE SYSTEMS | High (graph walk) |
| **P2** | `GRID_PITCH` | M1, M4, M6, DISCOVER PATTERNS | Medium (statistical) |
| **P3** | `INTERSECTS` | M9, M10 | Low (R-tree overlap check) |
| **P3** | `NEAREST_NEIGHBOUR_STATS` | Mining, pattern discovery | Medium (all-pairs) |
| **P3** | `SPAN_COUNT` | Vertical analysis | Low (distinct count) |

P0 predicates (4 total) cover 10 of 13 spatial M-rules and both existing
CHECK verbs (CLASH, PLACEMENT). Implement these first.

### 20.8 Relationship to Schema-Not-Geometry

Spatial predicates are the **approved channel** for ERP-maths. The
Schema-Not-Geometry rule (BBC.md §2, DocValidate §15.6) says: if a check uses
AABB arithmetic, ask whether an IFC relationship could replace it. For the cases
where the answer is NO — distance, clearance, grid pitch, containment — the
arithmetic IS the correct method. Predicates standardise that arithmetic so it
is written once, tested once, and reused everywhere.

When an IFC relationship IS extracted (R21-R24), the predicate upgrades
internally without changing its callers:

```
HOST_OF "guid_door_01"
  Before R21: AABB_PROXIMITY (nearest wall in 2/3 axes)
  After  R21: FK join on host_element_ref (exact)
  Caller code: unchanged
```

This is the same `check_method` upgrade path already designed in
DocValidate §15.6 — the predicate encapsulates the fallback-to-FK transition.

---

*BIM COBOL v0.15 — 77 verbs, 13 spatial predicates, 202 witnesses. Verb pattern detection LIVE: TILE/ROUTE/FRAME/CLUSTER.*
*48,485 → 1,131 lines (42.8:1). Mathematical basis: CLT (Theorem 1) + Information Theory (Theorem 5).*
*The Construction Programming Language*
*March 2026*

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
