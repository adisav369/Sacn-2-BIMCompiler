# Compiled Construction Programming Language (CCPL)

**Version:** 0.1
**Date:** 2026-02-28
**Authors:** red1 (architect) + Claude Watchdog (reviewer)
**Status:** CONCEPT — defines the language design for a construction-domain compiler
**Depends on:** concept-paper-compliance-gui.md (Compiled Construction v0.7), TopologyMaker/docs/TOPOLOGY_MAKER.md (Synthetic Stone §18-19), TheRosettaStoneStrategy.txt
**Supplements:** METADATA_DRIVEN_ARCHITECTURE.md, ConstructionAsERP.md, PREFAB_ARCHITECTURE.md

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

**CCPL is the COBOL of construction.** It is a high-level, domain-specific language where construction intent compiles down to IFC geometry + procurement BOM + compliance witnesses. The architect writes in construction concepts. The compiler emits assembler-level IFC.

---

## 2. What Already Exists — The Proto-Language

The BIM Intent Compiler already has a working DSL with 28+ keywords. This is the embryonic form of CCPL — a Level 1 language that already compiles to IFC output.

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

These are all *high-level construction verbs* that currently require manual authoring at the IFC geometry level. CCPL fills this gap.

---

## 3. Language Levels

CCPL operates at three abstraction levels, mirroring the COBOL → assembler → machine code chain:

```
LEVEL 2: CONSTRUCTION INTENT (CCPL — the new language)
         "Route sprinkler system in departure hall at 3m spacing"
         Domain expert can read, verify, and modify.
                    │
                    ▼  CCPL Compiler
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

## 4. CCPL Verb Categories

CCPL verbs map to construction operations, not geometry operations. Each verb compiles down to a cluster of Level 0 IFC elements + BOM entries + compliance checks.

### 4.1 ENVELOPE Verbs — Building Shell

```ccpl
ENCLOSE room_name WITH wall_type
    -- Generates: IfcWall segments forming closed polygon around room boundary
    -- Resolves:  wall build-up from M_Product catalog (e.g., WALL_EXT_MY_150)
    -- Proves:    P10 perimeter closure, SS01 room boundary fidelity

OPEN wall_face OF room_name WITH opening_type AT position
    -- Generates: IfcDoor or IfcWindow hosted on wall
    -- Resolves:  opening family from ad_space_type_opening schedule
    -- Proves:    SS03 opening host match, space contract OPENINGS count

COVER building WITH roof_type PITCH angle OVERHANG distance
    -- Generates: IfcRoof with parametric mesh from lod_roof_preset
    -- Resolves:  roof geometry from building footprint + overhang + pitch
    -- Proves:    SS05 parametric mesh dimensions, witness ROOF_COVERS_ALL

SPAN storey WITH slab_type THICKNESS dimension
    -- Generates: IfcSlab covering storey footprint
    -- Resolves:  slab extent from room boundaries + structural grid
    -- Proves:    P01 positive extent, structural span check
```

### 4.2 MEP Verbs — Mechanical, Electrical, Plumbing

This is the critical new capability. Each MEP verb encapsulates a complete system routing operation.

```ccpl
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

### 4.3 STRUCTURAL Verbs

```ccpl
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

```ccpl
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

```ccpl
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
  spacing      = 3000mm                                from CCPL verb
  offset       = 150mm                                 from CCPL verb

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

CCPL has a domain-specific type system where types correspond to construction entities, not programming primitives.

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

Unlike a conventional programming language that compiles to machine code, CCPL compiles to three simultaneous outputs:

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

Every CCPL verb that involves a code requirement produces a witness. The compliance certificate is not a document someone writes — it is a compilation artefact that the compiler produces automatically.

---

## 8. Grammar Sketch

### 8.1 Program Structure

```ccpl
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

CCPL is not a replacement for the GUI — it is the **engine behind it.** The Bonsai addon GUI translates user interactions into CCPL statements, then invokes the compiler.

```
USER ACTION (Bonsai GUI)              CCPL GENERATED              COMPILED OUTPUT
─────────────────────────────────────────────────────────────────────────────────
Click "Add Room" → drag boundary   →  LIVING "room1" bounds:...  →  IfcSpace + IfcWall[]
Click "Add Door" → click wall      →  OPEN SOUTH WITH DOOR_D1    →  IfcDoor hosted on wall
Click "Add MEP" → select system    →  ROUTE SPRINKLERS ...       →  IfcPipe[] + IfcTerminal[]
Click "Check Code" → select code   →  CHECK room1 AGAINST UBBL   →  witness.json
Slider "Spacing" → adjust          →  SPACING 2500mm (updated)   →  recompile, new layout
```

The GUI is the *editor*. CCPL is the *source code*. The compiler is the *build system*. The user never sees CCPL directly (just as most programmers today never see assembler). But CCPL is what makes the GUI deterministic — every click compiles to a statement, every statement compiles to proven geometry.

### 9.1 Live Recompilation

When the user adjusts a parameter (e.g., sprinkler spacing from 3.0m to 2.5m), the GUI:

1. Updates the CCPL statement: `SPACING 2500mm`
2. Recompiles the affected zone (incremental — only the sprinkler system, not the entire building)
3. Diffs the output (old vs new IFC elements)
4. Updates the Blender viewport (remove old heads, add new heads, reroute pipes)
5. Updates the compliance witness (re-check spacing rule)
6. Updates the BOM (new pipe lengths, fitting counts)

This is the **compiler feedback loop** that makes the GUI responsive. It is exactly analogous to a code editor with live compilation errors — change a line, see the red squiggle instantly.

### 9.2 Round-Trip Editing

A critical property: CCPL must support **round-trip editing**. If the user moves a sprinkler head manually in Bonsai (drag in 3D viewport), the system must:

1. Detect the position change (Blender event)
2. Update the CCPL representation (adjust the grid or add an override)
3. Re-verify compliance (does the new position still satisfy spacing rules?)
4. Update the BOM (pipe lengths change if head moved)

This is the hardest unsolved problem. COBOL did not have round-trip — you wrote code, compiled, ran. CCPL operates in a live-editing environment where the "source code" and the "compiled output" must stay synchronised. The solution: CCPL statements carry **override annotations**.

```ccpl
ROUTE SPRINKLERS IN "departure" SPACING 3000mm BELOW_CEILING 150mm {
    OVERRIDE SPR_017 AT (45200, 23100)  -- user manually repositioned
    OVERRIDE SPR_023 OMIT              -- user deleted (column obstruction)
}
```

Overrides are first-class syntax. The compiler honours them, re-routes around them, and re-proves compliance including the overrides. If an override violates a code rule, the compiler flags it — the user sees a red warning, not a silent acceptance.

---

## 10. Implementation Roadmap

### Phase CCPL-1: MEP Routing Engine (the first L2 verb)

The `ROUTE SPRINKLERS` verb, fully implemented:
- Grid generation within room polygon
- Beam avoidance (shift/reroute)
- Pipe routing (lateral → branch → main → riser)
- Connectivity proof (P17 adapted for fire protection)
- Spacing compliance (MS 1910)
- BOM generation (pipe lengths, fitting counts, head count)

**Why sprinklers first:** Sprinkler systems are the most regular MEP system (grid-based, single pipe size, one device type). They are the "Hello World" of MEP routing. Success here proves the routing engine works before tackling the harder problems (duct sizing, drainage gradient, electrical circuits).

### Phase CCPL-2: Duct Routing

Add `ROUTE DUCTS` with duct sizing calculation (velocity method or equal friction method), branch takeoffs, and air terminal placement. This is harder because duct sizes vary (main → branch → terminal) and clearance envelopes are larger.

### Phase CCPL-3: Drainage and Water

Add `ROUTE DRAINAGE` (gradient-constrained, P16 proof) and `ROUTE COLDWATER` (pressure-driven). These are the pipe systems that connect to fixtures (toilets, basins, taps) rather than grid-placed devices.

### Phase CCPL-4: Structural Verbs

Add `FRAME` and `REINFORCE`. These require structural analysis (load paths, span checks) which is more complex than MEP routing. May integrate with external structural analysis engines via IFC structural analysis model.

### Phase CCPL-5: Round-Trip and Override System

Implement the override annotation system for live editing in Bonsai. This is the integration phase where CCPL becomes the engine behind the GUI.

### Phase CCPL-6: Parser and Error Reporting

Build a proper CCPL parser (ANTLR or hand-written recursive descent) replacing the current regex-based DSL parser. Produce meaningful error messages with code citations:

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

CCPL's value is not computational — it is **communicative**. It makes the building's logic readable by every stakeholder: architect, engineer, inspector, contractor, and owner. This is what COBOL achieved for business, and what construction has never had.

---

## 12. The Three Compilation Artefacts

Every CCPL program produces exactly three outputs. All three are required. None is optional.

```
         CCPL Source
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

If any one of the three is missing, the compilation is incomplete. A building without a BOM cannot be built. A building without a witness cannot be permitted. A building without geometry cannot be visualised. CCPL enforces all three.

---

## 13. Strategic Position

CCPL is to construction what:
- **COBOL** was to business (domain-readable, compiles to lower level)
- **SQL** was to databases (declarative, describes *what* not *how*)
- **VHDL** was to chip design (intent → synthesised hardware)
- **LaTeX** was to typesetting (structured document → formatted output)

In each case, a domain-specific language replaced manual low-level authoring with high-level declarations that compile to verified output. None of these languages made the lower level disappear — you can still write assembler, still hand-craft database queries, still design gates manually. But the 80% case moved to the higher level, and the domain experts gained the ability to read and verify the output.

Construction is the last major industry without this abstraction. CCPL provides it.

---

*"The architect writes intent. The compiler produces geometry, BOM, and proof. The inspector reads the witness. The contractor reads the BOM. The owner sees the building. All from one source."*

---

*Compiled Construction Programming Language v0.1*
*February 2026*
