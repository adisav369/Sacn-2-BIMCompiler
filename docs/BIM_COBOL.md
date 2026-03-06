# BIM COBOL — The Construction Programming Language

**Version:** 0.9
**Date:** 2026-03-03
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Status:** ACTIVE — **12 verbs implemented, 63 witnesses (60 PASS / 3 RED).** All verbs from BC-0 through BC-2 complete. Next: BC-3 (Duct Routing).
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

The BIM_COBOL module has a working `Verb<T>` interface, `VerbContext`, and `VerbResult<T>` framework. **12 verbs implemented, 56/56 witnesses pass:**

| # | Verb | Phase | Witnesses | What it proves |
|---|---|---|---|---|
| 1 | `CHECK BOM` | BC-0 | W-1..4 | BOM tree structural integrity |
| 2 | `COVER WITH COMPOUND_ROOF` | BC-0 | W-5..8 | T-junction valley geometry |
| 3 | `ROUTE SPRINKLERS` | BC-1 | W-9..12 | Grid + pipe routing + NFPA compliance |
| 4 | `CONNECT FITTINGS` | BC-1 | W-17..20 | Port-budget fitting connectivity |
| 5 | `CHECK PLACEMENT` | BC-1 | W-21..24 | P01–P04 element geometry proofs |
| 6 | `CHECK CLASH` | BC-1 | W-25..28 | MEP vs structural bbox overlap |
| 7 | `CHECK ROOM` | BC-1 | W-29..32 | Room dims vs UBBL building code |
| 8 | `CHECK COMPLIANCE` | BC-1 | W-33..36 | Mounting heights + spacing rules |
| 9 | `WIRE LIGHTING` | BC-1 | W-37..40 | Fixture grid + conduit + lux |
| 10 | `VERIFY PLACEMENT` | BC-1 | W-45..48 | Cross-DB placement fidelity |
| 11 | **`TILE SURFACE`** | **BC-2** | **W-49..52** | **2D parametric grid fill** |
| 12 | **`ARRAY`** | **BC-2** | **W-53..56** | **1D linear repetition + code compliance** |
| — | *VerbRegistry + ScriptRunner* | PREP-1/2 | W-41..44 | Dispatch + script execution |
| | | | **56 total** | **56 PASS, 0 RED** |

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
  ad_element_placement:   (TE, L01, IfcFireSuppressionTerminal, SPR_001, ...)
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

| Suffix | Mode | Source | Purpose |
|--------|------|--------|---------|
| `_s` | Singular | Reference extracted DB (ground truth from IFC) | What the building SHOULD look like |
| `_e` | Exploded | Compiled output DB (from BOM walk + assembly) | What the compiler PRODUCES |

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

---

*BIM COBOL v0.10 — 12 verbs + 6 proposed quality verbs, 63 witnesses (60 PASS / 3 RED), 74.4% Terminal formula coverage*
*The Construction Programming Language*
*March 2026*
