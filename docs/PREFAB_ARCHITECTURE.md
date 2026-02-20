# Prefab Assembly Architecture

*Supersedes: runtime spatial resolution for standard buildings (FloorPlateBOMResolver fill_remaining path)*
*Extends: ARCHITECTURE.md §3.6 Tower BOM, DSL_AS_CATALOG_SELECTOR.md*

## Principle

Architecture works bottom-up from standards. A UBBL bedroom is 3.1m × 3.1m. Two bedrooms + two bathrooms + kitchen + living = a known-size unit. Two units + core = a known-size floor. The compiler does not compute layout — it selects pre-computed assemblies.

Each level is a BOM of the level below. Resolution is DAG expansion + coordinate translation. No spatial solving.

## MRP BOM Drop — How New Buildings Are Populated

The compiler follows the iDempiere MRP BOM explosion model. SH and DX are the two proven
"cars" — their parts are the reusable catalog. TB-LKTN reuses parts off the same shelf
(BED_SET, LIVING_SET, KITCHEN_CABINET_SET) without redefining them.

```
MRP step           BIM equivalent                    Table
──────────────────────────────────────────────────────────────────
Define product     UNIT_DUPLEX_STD (the house)       ad_bom
Define BOM         Floor × 2 → rooms → sets → items  ad_bom_child + ad_bom_child_param
Raise Sales Order  DSL building declaration           ad_building_registry.dsl_content
BOM Drop           ad_room_slot × ad_room_boundary   → ad_element_rule anchor rows
User edits lines   Remove piano, swap set, add chair  UPDATE/INSERT ad_element_rule
MRP Execution      mvn test (compile)                 RelationalResolver + FurnitureBOMResolver
```

**The BOM Drop produces editable order lines.** The user adjusts the schedule before
compiling. The compiler reads the final schedule — it does not care what edits were made.

## Fabricated Leaf Components — Mesh2Library Contract

For leaf components whose shape cannot be sourced from an existing catalog product
(e.g. pitched roof, drain channel, parametric column cap), the fabrication path is:

```
ad_parametric_mesh        mesh_type = 'GABLE_ROOF_MY', generator_class = 'GableRoofMesh'
ad_parametric_mesh_param  pitch_deg=25, span_mm=6000, overhang_mm=500
      ↓ (sealed ParametricMesh interface generates vertices at compile time)
ad_bom_child_param        dx/dy/dz = assembly-relative position of this mesh
ad_product_dim            width/depth/height = resulting dims after generation
```

**Three-table authority rule applies here too:**
- `ad_parametric_mesh_param` owns the shape parameters (pitch, span, overhang)
- `ad_bom_child_param` owns where this mesh sits in its parent assembly
- `ad_product_dim` owns the resulting bounding dimensions

See `docs/Mesh2Library.txt` for the sealed interface contract and `ad_roof_preset` for
region × building_type → mesh_type lookup. Never hardcode vertex lists in Java.

## Extended Assembly Hierarchy (6 Levels)

```
Level -1: FIXTURE ARRANGEMENT         ← NEW (Phase 115A)
  e.g., WORKSTATION_STD = L-desk + chair + 2 visitor chairs
  MANIFEST: BACK=WALL_BACK, FRONT=CLEARANCE(1.2m), LEFT/RIGHT=JOINABLE
  FABRICATED VARIANT: ParametricMesh leaf (Mesh2Library contract)
  → shape from ad_parametric_mesh_param, position from ad_bom_child_param

Level 0: COMPONENT (exists — component_definitions)
  e.g., Door_900x2100, Light_Downlight, Toilet_WC_FlushTank_6Lpf

Level 0.5: MEP SUB-ASSEMBLY (exists — T_CONNECTOR_ASSEMBLY etc.)
  e.g., SPRINKLER_DROP = tee + transition + drop + head
  MANIFEST: TOP=MAIN_HOOKUP(dia=27mm), BOTTOM=PENDANT_HEAD

Level 1: ROOM ASSEMBLY                ✅ LIVE — Phase BOM-1 (2026-02-21)
  ad_room_slot dispatch → BOM anchor rows → FurnitureBOMResolver expansion
  SH, DX, TB-LKTN: furniture placed via BOM anchors, not flat coords

Level 2: UNIT ASSEMBLY — rooms composed with interface matching
                          ❌ Next — Phase BOM-2: UNIT_DUPLEX_STD, UNIT_SH_STD

Level 3: FLOOR ASSEMBLY — units + core + circulation
                          ❌ Next — Phase BOM-2: FLOOR_1_STD, FLOOR_2_STD

Level 4: BUILDING — DSL selects and stacks floor assemblies
                    ✅ Partially — DSL declares rooms explicitly; full DAG pending
```

## DSL → DAG → Output

```
DSL:  BUILDING type:CONDO_MID
        │
DAG:  FLOOR_TOWER_2U × 16 + GROUND_LOBBY + ROOF_TANK
        │
      UNIT_2BR_STD × 2 + CORE_STD + CORRIDOR_STD
        │
      BEDROOM_STD × 2 + BATHROOM_STD × 2 + KITCHEN_STD + LIVING_STD
        │                                         │
      BED_SET + MEP_CEILING_SET           KITCHEN_COUNTER_SET + MEP_CEILING_SET
        │                                         │
      slab + walls + door + window +        cabinets + counter + sink +
      bed + side_table + light +            light + sprinkler +
      sprinkler  (absolute positions)       waste_pipe  (absolute positions)
```

## Assembly Identity & Versioning

Following the OSGi component model, every assembly carries a **Component_ID** and **Version**:

| OSGi Concept | Prefab Equivalent | Example |
|---|---|---|
| `Bundle-SymbolicName` | `assembly_id` | BATHROOM_WC_SET |
| `Bundle-Version` | `version` | 1.0.0 |
| `Export-Package` | Face interfaces (MANIFEST) | WALL_BACK, WASTE_OUT |
| `Import-Package` | `connects_to` targets | PLUMBING_STACK, FP_MAIN |
| `Require-Capability` | `is_required` slots | SANITARY required in BATHROOM |
| `Provide-Capability` | Connector hooks per face | WASTE_OUT(dia=100mm) |

### Versioning Convention

Semantic versioning: `MAJOR.MINOR.PATCH`

- **MAJOR** — incompatible interface change (different connector diameter, different face contract). Consumers must update.
- **MINOR** — backward-compatible addition (new connector, new optional face). Existing consumers unaffected.
- **PATCH** — internal-only change (component swap, geometry update). Same interfaces.

```
BATHROOM_WC_SET  1.0.0   IPC 405.3.1 clearances, 100mm waste, 15mm supply
BATHROOM_WC_SET  1.1.0   + hand_dryer connector on LEFT face (additive)
BATHROOM_WC_SET  2.0.0   MS1228 clearances (different code = different face contracts)
```

Version range matching (OSGi-style): `[1.0, 2.0)` means any v1.x is compatible. Room slots can specify a version range to allow minor upgrades without re-qualifying the room assembly.

### Loosely Coupled, Tightly Cohesive

Each assembly **owns** its geometry, clearances, and connector contracts (cohesion). Assemblies interact **only** through typed face interfaces and versioned connectors (coupling). No assembly knows another's internals — just like OSGi bundles communicate only through declared services.

This means:
- Swapping BATHROOM_WC_SET v1.0 for v1.1 requires **zero changes** to the room assembly
- A new jurisdiction (MS1228 vs IPC) produces a **new major version**, not a code fork
- The DSL remains a **catalog selector** — it picks assemblies by ID + version constraint, never by internal structure

## MANIFEST Contract Specification

Every assembly (fixture arrangement, room, unit, floor) exposes a **MANIFEST** — a contract describing what each of its six faces provides and requires.

### Face Convention

| Face | Direction | Axis |
|------|-----------|------|
| FRONT | −Y (south) | Approach side — where user faces |
| BACK | +Y (north) | Wall side — anchored against |
| LEFT | −X (west) | |
| RIGHT | +X (east) | |
| TOP | +Z (ceiling) | |
| BOTTOM | −Z (floor) | |

**Orientation convention:** FRONT = where the user approaches.
- For furniture → faces the room interior
- For rooms → the entry/door wall
- For units → the corridor-facing wall
- For floors → the main entry side of the building

### Interface Types

**Structural:**
| Type | Meaning |
|------|---------|
| WALL_BACK | Flush against wall — no gap, fixture anchored |
| JOINABLE | Can abut another assembly side-by-side (gap specified by clearance_m) |
| PARTY_WALL | Shared structural wall with adjacent unit |
| EXTERIOR | External building envelope |
| OPEN | No wall — continuous space |

**Access:**
| Type | Meaning |
|------|---------|
| ENTRY | Door opening (room entry, unit front door) |
| WINDOW | Window opening (daylight, ventilation) |

**Space:**
| Type | Meaning |
|------|---------|
| CLEARANCE | Minimum free space in meters — user activity zone, code-required |

**MEP Connectors:**
| Type | Meaning | Typical Diameter |
|------|---------|-----------------|
| WASTE_OUT | Sanitary drain output | 40mm (basin), 100mm (WC) |
| SUPPLY_IN | Water supply input | 15mm |
| ELEC_IN | Electrical feed | — |
| PIPE_IN / PIPE_OUT | General piping | varies |
| MAIN_HOOKUP | Connects to distribution main | 25–65mm |
| DUCT_IN / DUCT_OUT | HVAC ductwork | varies |

**Vertical:**
| Type | Meaning |
|------|---------|
| SHAFT | Vertical service shaft pass-through |
| RISER_IN | Vertical pipe/duct rising from below |
| RISER_OUT | Vertical pipe/duct continuing above |

### Composition Rule

Two assemblies snap together when their abutting faces are **compatible**:

```
Assembly A, face RIGHT = JOINABLE(0.3m)
Assembly B, face LEFT  = JOINABLE(0.3m)
→ Place B at A.maxX + max(0.3, 0.3)
```

```
Assembly A, face BACK  = WALL_BACK
Room wall, face FRONT  = WALL_BACK
→ A.backY = wall.Y (flush placement)
```

MEP connectors match by type and diameter:
```
BATHROOM_WC_SET, face BOTTOM = WASTE_OUT(dia=100mm)
Plumbing stack, face TOP     = WASTE_IN(dia=100mm)
→ Connect at stack position
```

## Connector Hooks

Typed connection points on the assembly envelope — extends the existing `ad_product_dim.conn_points` JSON pattern (currently used for FIXTURE_TOILET, FIXTURE_SINK, etc.) into a proper relational table.

Existing `ad_product_dim.conn_points` JSON examples from database:
```json
FIXTURE_TOILET: [{"face":"BACK","type":"WASTE"},{"face":"LEFT","type":"SUPPLY"}]
FIXTURE_SINK:   [{"face":"BACK","type":"WASTE"},{"face":"BACK","type":"SUPPLY"}]
FIXTURE_SHOWER: [{"face":"WALL","type":"SUPPLY"},{"face":"FLOOR","type":"WASTE"}]
ELEC_LIGHT:     [{"face":"TOP","type":"ELEC"}]
FP_SPRINKLER:   [{"face":"TOP","type":"FP"}]
```

The new `ad_assembly_connector` table formalizes these with explicit position, diameter, and target system:
```
assembly: BATHROOM_WC_SET
  face: BOTTOM, type: WASTE_OUT, position: (0.2, 0.35, 0.0), dia: 100mm → PLUMBING_STACK
  face: BOTTOM, type: SUPPLY_IN, position: (0.0, 0.35, 0.0), dia: 15mm → WATER_RISER

assembly: T_CONNECTOR_ASSEMBLY
  face: TOP, type: MAIN_HOOKUP, position: (0.0, 0.0, 0.127), dia: 65mm → FP_MAIN
  face: BOTTOM, type: PENDANT_HEAD, position: (0.0, 0.0, 0.0), dia: 15mm → SPRINKLER_HEAD
```

## Concrete Fixture Arrangements (POC — 6 Pieces)

### WORKSTATION_STD

Existing BOM: `WORKSTATION_SET` (5 children: DESK, USER_CHAIR, MONITOR, VISITOR_CHAIR_A, VISITOR_CHAIR_B).

```
 BACK (wall)
 ┌──────────────────────────┐
 │  [desk_with_return]      │   Desk against back wall (dx=0, dy=0)
 │    ╔═══╗  [iMac]         │   Monitor on desk (dx=-0.59, dz=0.70)
 │    ║   ║                 │
 │  [user_chair]            │   User chair (dy=+0.36 from desk center)
 │                          │
 │  [visitor_A] [visitor_B] │   Visitor chairs (dx=+0.95/+1.76, rotated π)
 └──────────────────────────┘
 FRONT (1.2m clearance)

 Envelope: 2.5m × 2.2m × 1.2m (desk height + monitor)
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| BACK | WALL_BACK | Desk flush against wall |
| FRONT | CLEARANCE | 1.2m (visitor approach + chair pullback) |
| LEFT | JOINABLE | 0.3m |
| RIGHT | JOINABLE | 0.3m |
| TOP | — | — |
| BOTTOM | ELEC_IN | Floor box for desk power |

Source: `ad_bom_child_param` — DESK(x=0,y=0), USER_CHAIR(y=+0.36), MONITOR(x=-0.59,z=0.70), VISITOR_CHAIR_A(x=+0.95,y=+0.17,rot=π), VISITOR_CHAIR_B(x=+1.76,y=+0.17,rot=π).

### BATHROOM_WC_SET

Replaces `FixturePlacer` hardcoded toilet placement logic. Maps to existing `TOILET_BLOCK_FIXTURES` BOM roles TOILET + HAND_BIDET for residential use.

```
 BACK (wall)
 ┌─────────────────┐
 │ [WC]    [bidet] │   WC: wall_offset=0.05m, bidet: lateral_offset=0.65m
 │  ║               │   Bidet at z=0.58m (hand reach from seated)
 │  ║               │
 │                  │   533mm clearance (IPC 405.3.1)
 └─────────────────┘
 FRONT (0.533m clearance)

 Envelope: 1.3m × 1.1m × 0.75m
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| BACK | WALL_BACK | Cistern flush against wall (offset 50mm) |
| FRONT | CLEARANCE | 0.533m (IPC 405.3.1 — min 21 inches front clearance) |
| LEFT | CLEARANCE | 0.381m (IPC 405.3.1 — min 15 inches side clearance) |
| RIGHT | CLEARANCE | 0.381m |
| BOTTOM | WASTE_OUT | dia=100mm → PLUMBING_STACK |
| BOTTOM | SUPPLY_IN | dia=15mm → WATER_RISER |

Source: `ad_product_dim` FIXTURE_TOILET — width=0.4m, depth=0.7m, clear_front=0.533m, clear_left/right=0.381m. `ad_bom_child_param` TOILET — wall_offset=0.05, spacing=1.3m, z_offset=0. HAND_BIDET — lateral_offset=0.65, z_offset=0.58.

### BATHROOM_BASIN_SET

Maps to `TOILET_BLOCK_FIXTURES` SINK role.

```
 BACK (wall)
 ┌─────────────┐
 │   [basin]   │   Wall-mounted at z=0.85m
 │    ═══      │   wall_offset=0.05m
 │             │
 │             │   533mm clearance
 └─────────────┘
 FRONT (0.533m clearance)

 Envelope: 0.8m × 0.6m × 0.9m (including splash zone)
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| BACK | WALL_BACK | Basin wall-mounted (offset 50mm) |
| FRONT | CLEARANCE | 0.533m (IPC 405.3.1) |
| LEFT | CLEARANCE | 0.3m |
| RIGHT | CLEARANCE | 0.3m |
| BOTTOM | WASTE_OUT | dia=40mm → PLUMBING_STACK |
| BOTTOM | SUPPLY_IN | dia=15mm → WATER_RISER |

Source: `ad_product_dim` FIXTURE_SINK — width=0.5m, depth=0.45m, clear_front=0.5m, clear_left/right=0.3m. `ad_bom_child_param` SINK — wall_offset=0.05, z_offset=0.85, spacing=0.8m.

### KITCHEN_COUNTER_SET

Extends existing `KITCHEN_CABINET_SET` (4 children: BASE_CABINET, UPPER_CABINET, COUNTER, SINK).

```
 BACK (wall)
 ┌───────────────────────────────┐
 │ [upper_cabinet]  z=1.4m      │   Upper cabinet wall-mounted
 │ ═══════════════════           │
 │ [counter_top]    z=0.85m     │   Counter surface
 │ [base_cabinet]   z=0.0       │   Base cabinet on floor
 │ [sink]           in counter  │   Sink island single 456x455mm
 │                              │
 │                              │   0.9m work aisle clearance
 └───────────────────────────────┘
 FRONT (0.9m clearance)

 Envelope: 2.4m × 0.6m × 2.1m (floor to upper cabinet top)
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| BACK | WALL_BACK | Cabinets flush against wall |
| FRONT | CLEARANCE | 0.9m (work aisle — min for single cook) |
| LEFT | JOINABLE | 0.0m (continuous counter run) |
| RIGHT | JOINABLE | 0.0m |
| BOTTOM | WASTE_OUT | dia=40mm from sink trap → PLUMBING_STACK |
| BOTTOM | SUPPLY_IN | dia=15mm → WATER_RISER |

Source: `ad_bom_child` KITCHEN_CABINET_SET — Cabinet_Base% (seq 1), Cabinet_Upper% (seq 2), Counter_Top% (seq 3), Sink_Island% (seq 4).

### BED_SET

Existing BOM: `BED_SET` (2 children: BED, SIDE_TABLE).

```
 BACK (wall)
 ┌───────────────────────┐
 │        [bed]          │   Bed against back wall (back_to_wall=true)
 │   ╔═══════════╗       │   Queen bed (dx=0, dy=0)
 │   ║           ║       │
 │   ╚═══════════╝       │
 │ [side_table]          │   Side table (dx=+0.98, dy=0)
 │                       │
 └───────────────────────┘
 FRONT (0.6m clearance)

 Envelope: 2.0m × 2.2m × 0.5m
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| BACK | WALL_BACK | Bed headboard against wall |
| FRONT | CLEARANCE | 0.6m (passage at foot of bed) |
| LEFT | CLEARANCE | 0.4m (access to bed side) |
| RIGHT | JOINABLE | 0.3m |

Source: `ad_bom_child_param` BED — back_to_wall=true, name_pattern=Bed_Queen. SIDE_TABLE — dx=+0.98, name_pattern=Side_Table. `ad_product_dim` FURN_BED_DOUBLE — width=1.5m, depth=2.0m, clear_front=0.6m, clear_left/right=0.6m.

### SPRINKLER_PENDANT_SET

Existing BOM: `SPRINKLER_PENDANT_ASSEMBLY` (2 children: SPRINKLER_HEAD, T_ASSEMBLY → nested T_CONNECTOR_ASSEMBLY).

```
        FP MAIN pipe (65mm dia, z_offset=0.15m below slab)
            │
 ┌──────────┼──────────┐
 │    [tee_threaded]   │   TEE: splits main to branch
 │          │          │
 │  [transition_fitting]│   TRANSITION: 65mm → 25mm adaptor
 │          │          │
 │    [drop_pipe]      │   DROP: 25mm vertical, 50mm length
 │          │          │
 │  [sprinkler_head]   │   PENDANT HEAD: z = slab - 0.20m
 └─────────────────────┘

 Total height: 177mm (tee to head)
```

**MANIFEST:**

| Face | Interface | Value |
|------|-----------|-------|
| TOP | MAIN_HOOKUP | dia=65mm → FP_MAIN pipe |
| BOTTOM | — | Pendant head (terminal) |

**Connector hooks:**
```
face: TOP,    type: MAIN_HOOKUP, dia: 65mm, connects_to: FP_MAIN
face: TOP,    type: BRANCH_OUT,  dia: 25mm, connects_to: FP_BRANCH (to adjacent head)
```

Source: `ad_bom_child` T_CONNECTOR_ASSEMBLY — FP_Drop_Pipe(seq 1), FP_Transition_Fitting(seq 2), FP_Tee_Threaded(seq 3). `ad_bom_child_param` FP_PIPE_ASSEMBLY — MAIN dia=0.065m, HEAD z_offset=0.20m, BRANCH dia=0.025m, DROP drop_offset=0.05m.

## Room Slot Protocol

Currently, room contents are resolved through three independent paths:

| Path | Resolver | What it handles |
|------|----------|----------------|
| Furniture | `FurnitureTypeResolver` → `FurnitureBOMResolver` | Desks, beds, sofas, tables |
| Ceiling MEP | `MEPBOMResolver` | Lights, sprinklers, diffusers, fans |
| Fixtures | `FixturePlacer` | Toilets, basins, kitchen sinks (hardcoded) |

The **Room Slot Protocol** unifies these into a single resolution table `ad_room_slot`, where each room type declares named slots filled by fixture arrangements in priority order.

### Slot Resolution

Slots are processed **sequentially by priority** (lowest first). Each slot's clearance envelope is reserved before the next slot is placed. This guarantees non-clash without runtime collision detection.

```
BATHROOM:
  slot: SANITARY     → BATHROOM_WC_SET      face=BACK     priority=10  required=1
  slot: BASIN        → BATHROOM_BASIN_SET    face=LEFT     priority=20  required=1
  slot: EXHAUST      → EXHAUST_FAN_SET       face=TOP      priority=30  required=1
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=40  required=0

BEDROOM:
  slot: FURNITURE    → BED_SET               face=BACK     priority=10  required=1
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=20  required=0

KITCHEN:
  slot: COUNTER      → KITCHEN_COUNTER_SET   face=BACK     priority=10  required=1
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=20  required=0

OFFICE:
  slot: FURNITURE    → WORKSTATION_SET       face=BACK     priority=10  required=1
  slot: VISITOR      → VISITOR_SET           face=FRONT    priority=20  required=0
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=30  required=0

LIVING:
  slot: FURNITURE    → LIVING_SET            face=BACK     priority=10  required=1
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=20  required=0

TOILET_BLOCK:
  slot: SANITARY     → TOILET_BLOCK_FIXTURES face=BACK     priority=10  required=1
  slot: BASIN        → BATHROOM_BASIN_SET    face=LEFT     priority=20  required=1
  slot: FLOOR_TRAP   → FLOOR_TRAP_SET        face=BOTTOM   priority=30  required=1
  slot: EXHAUST      → EXHAUST_FAN_SET       face=TOP      priority=40  required=1
  slot: CEILING_MEP  → MEP_CEILING_SET       face=TOP      priority=50  required=0
```

### Resolution Algorithm

```
for each slot in room.slots (ordered by priority):
    assembly = resolve(slot.assembly_id)
    manifest = assembly.manifest
    anchor_wall = room.wall(slot.slot_face)

    // Reserve clearance envelope
    envelope = compute_envelope(assembly, manifest)
    assert no_overlap(envelope, reserved_zones)
    reserved_zones.add(envelope)

    // Place components
    for each component in assembly.children:
        absolute_pos = anchor_wall.origin + component.offset
        emit(component, absolute_pos)
```

## Database Schema — New Tables

Three new tables, extending the existing `ad_bom` / `ad_product_dim` pattern:

```sql
-- Interface faces per assembly (MANIFEST contract)
CREATE TABLE ad_assembly_manifest (
    manifest_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id   TEXT NOT NULL,          -- ad_bom.bom_id or prefab_product.prefab_id
    version       TEXT NOT NULL DEFAULT '1.0.0',  -- semantic version (OSGi-style)
    face          TEXT NOT NULL,          -- FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM
    interface_type TEXT NOT NULL,         -- WALL_BACK, CLEARANCE, ENTRY, JOINABLE,
                                         -- PARTY_WALL, EXTERIOR, OPEN, WINDOW
    clearance_m   REAL DEFAULT 0,        -- meters of free space required
    UNIQUE(assembly_id, version, face, interface_type)
);

-- Typed connection points on assembly envelope
CREATE TABLE ad_assembly_connector (
    connector_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    assembly_id   TEXT NOT NULL,          -- ad_bom.bom_id or prefab_product.prefab_id
    version       TEXT NOT NULL DEFAULT '1.0.0',  -- semantic version
    face          TEXT NOT NULL,          -- which face the connector is on
    connector_type TEXT NOT NULL,         -- WASTE_OUT, SUPPLY_IN, MAIN_HOOKUP,
                                         -- ELEC_IN, DUCT_IN, DUCT_OUT, PENDANT_HEAD
    position_x    REAL DEFAULT 0,        -- relative to assembly origin (meters)
    position_y    REAL DEFAULT 0,
    position_z    REAL DEFAULT 0,
    diameter_mm   REAL,                  -- pipe/duct diameter
    connects_to   TEXT                   -- target system: PLUMBING_STACK, FP_MAIN,
                                         -- WATER_RISER, ELEC_PANEL, HVAC_TRUNK
);

-- What fixture arrangements a room type accepts (slot protocol)
CREATE TABLE ad_room_slot (
    slot_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    room_type     TEXT NOT NULL,          -- ad_space_type.space_type_id
    slot_name     TEXT NOT NULL,          -- FURNITURE, SANITARY, BASIN, CEILING_MEP,
                                         -- EXHAUST, COUNTER, FLOOR_TRAP, VISITOR
    assembly_id   TEXT,                   -- default BOM for this slot (ad_bom.bom_id)
    version_range TEXT DEFAULT '[1.0,2.0)',  -- OSGi-style version range constraint
    slot_face     TEXT,                   -- which room face to anchor the assembly to
    slot_priority INTEGER DEFAULT 100,   -- lower = placed first, reserves space first
    is_required   INTEGER DEFAULT 0,     -- 1 = room invalid without this slot filled
    UNIQUE(room_type, slot_name)
);
```

### Relationship to Existing Tables

```
ad_space_type ──────────── ad_room_slot ──────────── ad_bom
  (room types)        (what slots a room has)      (what goes in each slot)
                                                        │
                                                   ad_bom_child
                                                   (components in the BOM)
                                                        │
                                                   ad_bom_child_param
                                                   (offsets, clearances, rules)

ad_bom / prefab_product ── ad_assembly_manifest ── (face contracts)
                        └─ ad_assembly_connector ── (typed hookup points)

ad_product_dim ── conn_points JSON (existing, Level 0 components only)
```

## How Existing BOMs Map

| Existing BOM | Level | Action |
|---|---|---|
| WORKSTATION_SET | -1 (fixture arrangement) | Add MANIFEST rows — 5 children unchanged |
| BED_SET | -1 | Add MANIFEST rows — 2 children unchanged |
| BED_SET_MASTER | -1 | Add MANIFEST rows — 4 children unchanged |
| LIVING_SET | -1 | Add MANIFEST rows — 4 children unchanged |
| DINING_SET | -1 | Add MANIFEST rows — 5 children unchanged |
| VISITOR_SET | -1 | Add MANIFEST rows — 3 children unchanged |
| TOILET_BLOCK_FIXTURES | -1 | Add MANIFEST + WASTE_OUT/SUPPLY_IN connectors — 7 children unchanged |
| DUPLEX_BATHROOM_SET | -1 | Add MANIFEST + connectors — 4 children unchanged |
| KITCHEN_CABINET_SET | -1 | Add MANIFEST + WASTE_OUT/SUPPLY_IN connectors — 4 children unchanged |
| T_CONNECTOR_ASSEMBLY | 0.5 (MEP sub-assembly) | Add MAIN_HOOKUP + PENDANT_HEAD connectors — 3 children unchanged |
| SPRINKLER_PENDANT_ASSEMBLY | 0.5 | Add MAIN_HOOKUP connector — 2 children unchanged |
| FP_PIPE_ASSEMBLY | 0.5 | Add RISER_IN/RISER_OUT connectors — future |
| WATER_TANK_ASSEMBLY | 0.5 | Add SUPPLY_OUT connector — future |
| TYPICAL_CONDO_FLOOR | 3 (floor) | Future: Level 3 MANIFEST |
| CORE_ASSEMBLY | 2 (unit) | Future: SHAFT + RISER connectors |
| FLOOR_STRUCTURAL, WALL_PANEL, DOOR_ASSEMBLY, STAIR_COMPLETE, ROOF_ASSEMBLY | structural | No change — structural, not fixture |
| ROOM_FURNITURE, MEP_ROOM | routing | Gradually replaced by ad_room_slot resolution |

## Supersedes (Updated)

| Old | New |
|---|---|
| `FixturePlacer` hardcoded clearances | `ad_assembly_manifest` clearance values per face |
| `ad_product_dim.conn_points` JSON | `ad_assembly_connector` relational table (Level -1 and 0.5) |
| 3-way routing (FurnitureType + MEPBOM + FixturePlacer) | `ad_room_slot` unified slot table |
| Implicit MEP pipe routing (hardcoded Z offsets) | `ad_assembly_connector` typed hookups |
| `ad_unit_type_room` fractional bounds [0..1] | `prefab_bom` absolute mm offsets |
| `FloorPlateBOMResolver.fill_remaining` runtime | `prefab_product` pre-computed floor layout |
| `UnitInteriorResolver` fractional scaling | DAG expansion with absolute positions |
| Runtime "what fits?" computation | Catalog "select what you need" lookup |
| `ROOM_FURNITURE` BOM routing (WORK_ZONE, VISITOR_ZONE, GUEST_SEAT) | `ad_room_slot` with named slots per room type |

## Coexistence

All existing resolvers continue to work. The MANIFEST/slot system is **additive**:

- `FurnitureBOMResolver` reads clearance from `ad_assembly_manifest` instead of hardcoded `WALL_OFFSET=0.5`
- `MEPBOMResolver` still computes quantities; room slots delegate to it for CEILING_MEP slot
- `FixturePlacer` path remains available for non-standard rooms; gradually deprecated as fixture arrangement BOMs cover more cases
- `FloorPlateBOMResolver` and `UnitInteriorResolver` unchanged
- `floor_prefab:FLOOR_TOWER_2U` → new prefab DAG expansion path
- `floor_bom:TYPICAL_CONDO_FLOOR` → existing runtime spatial resolution
- Buildings without either → legacy explicit bounds

No existing builds break. New standard buildings use prefabs.

## Assembly Levels (Detail)

### Level -1: Fixture Arrangements (NEW)

Standard groupings of components that form a functional unit within a room. Each has a MANIFEST contract and optional MEP connectors.

See "Concrete Fixture Arrangements" section above for the 6 POC pieces.

### Level 0: Components (exists)

Table: `component_definitions`. Individual IFC elements with LOD400 geometry. 8,460+ definitions across 21 component types.

### Level 0.5: MEP Sub-Assemblies (exists)

Nested BOMs for MEP distribution elements. T_CONNECTOR_ASSEMBLY (3 children) nests inside SPRINKLER_PENDANT_ASSEMBLY (2 children including the nested T). FP_PIPE_ASSEMBLY orchestrates per-storey fire protection.

### Level 1: Room Assemblies

Standard rooms with known dimensions, component set, slot protocol, and face interfaces.

```
BEDROOM_STD  3100 × 3100 × 3000mm
  walls: 4 × Internal_150mm
  slots:
    FURNITURE  → BED_SET          face=BACK    priority=10
    CEILING_MEP → MEP_CEILING_SET face=TOP     priority=20
  interfaces: S=ENTRY(D2), N=WINDOW(W1), E/W=JOINABLE
```

```
BATHROOM_STD  1500 × 2400 × 3000mm
  walls: 4 × Internal_100mm
  slots:
    SANITARY    → BATHROOM_WC_SET    face=BACK   priority=10
    BASIN       → BATHROOM_BASIN_SET face=LEFT   priority=20
    EXHAUST     → EXHAUST_FAN_SET    face=TOP    priority=30
    CEILING_MEP → MEP_CEILING_SET    face=TOP    priority=40
  interfaces: S=ENTRY(D3), connectors: WASTE_OUT(100mm), SUPPLY_IN(15mm)
```

```
KITCHEN_STD  3000 × 3500 × 3000mm
  walls: 4 × Internal_150mm
  slots:
    COUNTER     → KITCHEN_COUNTER_SET face=BACK  priority=10
    CEILING_MEP → MEP_CEILING_SET     face=TOP   priority=20
  interfaces: S=ENTRY(D2), N=WINDOW(W1), connectors: WASTE_OUT(40mm), SUPPLY_IN(15mm)
```

### Level 2: Unit Assemblies

Standard units: known room arrangement, known total dimensions.

```
UNIT_2BR_STD  8000 × 12000mm
  LIVING_STD     at (0,0)      5000 × 5000
  KITCHEN_STD    at (5000,0)   3000 × 3500
  BEDROOM_STD    at (0,5000)   4500 × 3500
  BEDROOM_STD    at (4500,5000) 3500 × 3500
  BATHROOM_STD   at (0,8500)   4500 × 3500
  BATHROOM_STD   at (4500,8500) 3500 × 3500
  interfaces: S=ENTRY(D1), N=EXTERIOR, W=EXTERIOR, E=PARTY_WALL
  connectors: WASTE_OUT(100mm) at bathroom stack positions
```

### Level 3: Floor Assemblies

Standard floors: units + core + circulation, known total dimensions.

```
FLOOR_TOWER_2U  12000 × 34000mm
  UNIT_2BR_STD   at (0,0)      orient=NONE
  CORE_STD       at (0,12000)  12000 × 8500
  CORRIDOR_STD   at (0,20500)  12000 × 1500
  UNIT_2BR_STD   at (0,22000)  orient=MIRROR_Y
  interfaces: vertical=SHAFT+RISER, perimeter=EXTERIOR
```

### Level 4: Building (DSL)

DSL selects floor assemblies. Compiler stacks them at storey heights.

## Multi-Dimensional Selection

Like iDempiere's C_BPartner × M_Product × M_Project:

| Dimension | Values | Selects |
|-----------|--------|---------|
| Space type | RESIDENTIAL, OFFICE, CORE | Which assembly catalog |
| Size | 8×12m, 6×8.5m | Which size variant |
| Jurisdiction | UBBL, IBC | Which code compliance |

Same 8m × 12m envelope → RESIDENTIAL gets bedrooms + bathrooms. OFFICE gets workstations + meeting rooms. Different assembly, same selection mechanism.

## POC Scope

All off-the-shelf defaults. No variants. No tailoring.

| Assembly | Level | Dimensions | Contents |
|----------|-------|-----------|----------|
| WORKSTATION_STD | -1 | 2.5 × 2.2m | desk + chair + monitor + 2 visitors |
| BATHROOM_WC_SET | -1 | 1.3 × 1.1m | WC + hand bidet |
| BATHROOM_BASIN_SET | -1 | 0.8 × 0.6m | wall-mounted basin |
| KITCHEN_COUNTER_SET | -1 | 2.4 × 0.6m | base cab + upper cab + counter + sink |
| BED_SET | -1 | 2.0 × 2.2m | bed + side table |
| SPRINKLER_PENDANT_SET | 0.5 | 0.1 × 0.1m | tee + transition + drop + head |
| BEDROOM_STD | 1 | 3.1 × 3.1m | walls + door + window + BED_SET + MEP |
| BATHROOM_STD | 1 | 1.5 × 2.4m | walls + door + WC_SET + BASIN_SET + fan |
| KITCHEN_STD | 1 | 3.0 × 3.5m | walls + COUNTER_SET + light |
| LIVING_STD | 1 | 5.0 × 5.0m | walls + windows + LIVING_SET + MEP |
| CORE_STD | 1 | 6.0 × 8.5m | stair + lift + lobby + shaft |
| CORRIDOR_STD | 1 | 1.8 × variable | walls + lights + sprinklers |
| UNIT_2BR_STD | 2 | 8.0 × 12.0m | 2 bed + 2 bath + kitchen + living |
| FLOOR_TOWER_2U | 3 | 12.0 × 34.0m | 2 units + core + corridor |

Mirror/rotation applied at placement time — one assembly, four orientations. Variants come later as additional catalog entries.

## Placement Determinism & Future Editability

### Current Phase: Stone Preset

Every element position is **extracted from the Rosetta Stones** and stored as metadata.
The compose functions read coordinates — they do not compute them. This is identical
to how Tier 1 (dimensions) reached 100%: extract from reference, store in AD table, read.

The placement metadata is **variable data hardwired to Stone values**. The framework
(compose functions, placement handlers, writers) is **invariant**. This separation means:

- NOW: metadata is preset to Stone coordinates → proves the framework works
- LATER: user/GUI changes the same metadata parameters → different building, same engine

Example: a door placed at offset 0.3m along a 4m wall is a parameter in `ad_element_placement`.
The compose function reads `offset_along_wall=0.3`. Change it to `0.8` → door moves. The
compose function doesn't change.

### Spatial Intelligence Patterns (Deferred)

The following patterns exist in the Stones and will be formalised as they are observed
during extraction. They are NOT the current focus — placement accuracy comes first.

- **Back-to-wall**: furniture anchors against the nearest wall (beds, desks, counters).
  Already expressed in MANIFEST face contracts (WALL_BACK).
- **Find-open-space**: new items placed in largest unoccupied zone within a room.
  Room Slot Protocol handles this via priority-ordered clearance reservation.
- **Host awareness**: openings know their host wall; fixtures know their host room.
  IHostable contract defined, pending wiring.
- **Proximity grouping**: related items cluster (dining table + chairs, bed + side table).
  BOM child offsets already encode this in `ad_bom_child_param`.
- **Clearance enforcement**: code-required free space (IPC 405.3.1 toilet clearances).
  MANIFEST clearance_m values per face.

These patterns are already designed in the contracts and MANIFEST system above.
Implementation follows naturally once placement metadata proves the framework.
The Stones provide concrete test cases; BIM standards provide the rules.

### Abstract Rules vs Concrete Values (Deferred)

The placement metadata stores CONCRETE values (door offset=0.3m, angle=90°).
It does NOT yet capture the ABSTRACT RULES that govern those values. Examples:

- "Doors open into the room they serve, not into corridors"
- "Toilets back against the plumbing wall (nearest to stack)"
- "Beds have headboard against the longest uninterrupted wall"
- "Kitchen counters run along the wall opposite the entry"
- "Windows center on the exterior wall they occupy"

These rules are universal — they hold across all buildings, not just the Stones.
The current phase extracts the concrete values to prove the framework. The rules
will be derived later by observing PATTERNS across the 3 Stones and cross-referencing
BIM standards (IPC, UBBL, IBC). Once formalised, the rules become the engine's
"common sense" — allowing it to derive placement for new buildings WITHOUT a
reference Stone. But that is a second-order concern: values first, rules later.

### Contract Readiness Summary

| Layer | Contract | Status | Blocks Placement? |
|-------|----------|--------|-------------------|
| L0 Geometry | IGeometryValidatable | Wired | No |
| L1 Existence | IBIMEntity | Wired | No |
| L2 Identity | IIdentifiable | Wired | No |
| L3 Relationship | IRelatable, IHostable | Partial | No — wired after placement works |
| L4 Aggregation | IAggregatable, IShared, IZoned, IStackable | Wired | No |
| L5 Semantic | IValidatable, IFireProtected | Pending | No — validation layer, not placement |

**Nothing blocks the placement work.** The contracts are ready to receive it.
The architecture is sound; the gap is data (positions), not design.
