# Prefab Assembly Architecture

*Supersedes: runtime spatial resolution for standard buildings (FloorPlateBOMResolver fill_remaining path)*
*Extends: ARCHITECTURE.md §3.6 Tower BOM, DSL_AS_CATALOG_SELECTOR.md*
*Dimension model: [BIMasBOMConcept.md](BIMasBOMConcept.md) — Category (M_BomCategory) + Owner (C_BPartner) + SpaceSize (AABB)*

## Principle

Architecture works bottom-up from standards. A UBBL bedroom is 3.1m × 3.1m. Two bedrooms + two bathrooms + kitchen + living = a known-size unit. Two units + core = a known-size floor. The compiler does not compute layout — it selects pre-computed assemblies.

Each level is a BOM of the level below. Resolution is DAG expansion + coordinate translation. No spatial solving.

## MRP BOM Drop — How New Buildings Are Populated

> Assembly structure governs WHAT exists and WHERE it sits.
> VIEW_CONTRACTS.md governs what the compiler can SEE.
> A row not yet CO is invisible to compilation — by design.

The compiler follows the iDempiere MRP BOM explosion model. SH and DX are the two proven
"cars" — their parts are the reusable catalog. TB-LKTN reuses parts off the same shelf
(BED_SET, LIVING_SET, KITCHEN_CABINET_SET) without redefining them.

```
MRP step           iDempiere           BIM equivalent                          Table
──────────────────────────────────────────────────────────────────────────────────────────
Define product     M_Product + M_BOM   UNIT_DUPLEX_STD (the house)             m_bom (M_BOM)
Define BOM lines   M_BOM_Line          Floor × 2 → rooms → sets → items       m_bom_line + m_attribute
Define attributes  M_Attribute         Ports, clearances, UBBL rules           m_attribute
Raise work order   C_Order             BIM (building declaration)              ad_building_registry (BIM)
BOM Drop           C_OrderLine         Room dispatch → placement instances     ad_element_rule (BIMLine)
User edits lines   Edit C_OrderLine    Remove piano, swap set, add chair       UPDATE/INSERT ad_element_rule
MRP Execution      MRP Run             mvn test (compile)                      RelationalResolver + MBOM
```

**The compilation model:**
- **BIM** (`ad_building_registry`) = the C_Order. Scoped by `bom_owner` (C_BPartner).
- **BIMLine** (`ad_element_rule`) = C_OrderLine. Each line selects an M_BOM and places it.
- **M_BOM** (`m_bom`) = the product + assembly merged. Carries `bom_category` (WHAT) and `bom_owner` (WHO).
- **M_BOM_Line** (`m_bom_line`) = child placement. Carries SpaceSize (HOW MUCH: AABB in mm).
- **M_Attribute** (`m_attribute`) = product-level attributes on leaf items (ports, UBBL clearances).

The BIM selects M_BOMs within its owner scope. Each BIMLine references an M_BOM.
The compiler walks M_BOM → M_BOM_Line recursively, resolving placement at each level.

**The BOM Drop produces editable order lines.** The user adjusts the schedule before
compiling. The compiler reads the final schedule — it does not care what edits were made.

## BOM Drop Positional Chain — Where Each Level Sits

A building is made up of exactly what a vehicle is made up of: bricks, beams, designs.
Raw materials → components → sub-assemblies → assemblies → the complete product.
The BOM is simply that description, made explicit at every level.
iDempiere MFG captures this for manufacturing. The same model applies here without modification.

**The governing rule:** Every BOM level must explicitly declare where each of its children sits.

> *"You cannot say the axle group has two wheels without saying where each wheel is bolted.
> You cannot say UNIT_SH_STD has a Ground Floor without saying where the Ground Floor sits.
> You cannot say FLOOR_SH_GF_STD has a Living Room without saying where the Living Room is."*

The BOM Drop is not a one-time room-level event. It must fire at **every layer** — UNIT, FLOOR, ROOM, SET — each layer producing Orderlines that declare the position of its children relative to its own space bbox.

### The Chain — SH (Ifc4_SampleHouse, actual data)

```
UNIT_SH_STD                       ← building unit Orderline in ad_element_rule
│   host_type=BUILDING             family_ref=UNIT_SH_STD
│   world footprint: 4645 × 5800mm (aggregated from ad_room_boundary)
│
├── FLOOR_SLAB_GF  dZ=0              ← ground slab (pending — see ConstructionAsERP.md)
├── ROOF_ASSEMBLY  dZ=3000mm         ← roof (pending)
│
└── FLOOR_SH_GF_STD  "Ground Floor"    ← floor Orderline
    │   dZ = 0mm (ground level)
    │   host_type=BUILDING              family_ref=FLOOR_SH_GF_STD
    │   footprint: 4645 × 5800mm        height_extent=3000mm
    │
    ├── LIVING_SET  → ROOM_Ground_Floor_1    ← room Orderline (BOM Drop, Phase BOM-1)
    │   │   position: room world coords min_x=1620, min_y=-1246 (ad_room_boundary)
    │   │   dimensions: 4645 × 3308mm        host_type=ROOM
    │   │
    │   ├── Sofa_3Seat     dx=-1.1, dy=0.5   ← item offset (ad_bom_child_param)
    │   ├── Coffee_Table   dx= 0.0, dy=1.2
    │   ├── Armchair_1     dx= 1.2, dy=0.5
    │   └── Armchair_2     dx= 1.2, dy=1.2
    │
    └── BED_SET_MASTER  → ROOM_Ground_Floor_2
        │   position: room world coords (ad_room_boundary)
        │
        ├── Bed_King       dx=0.0,  dy=0.0
        └── Side_Table     dx=0.98, dy=0.0
```

### The Chain — DX (Ifc2x3_Duplex, actual data — two floors)

```
UNIT_DUPLEX_STD                    ← building unit Orderline
│   world footprint: 8383 × 17384mm
│
├── FLOOR_SLAB_GF  dZ=0              ← ground slab (pending — see ConstructionAsERP.md)
│
├── FLOOR_DX_L1_STD  "Level 1"     ← floor Orderline
│   │   dZ = 0mm
│   │   footprint: 8383 × 17384mm   (x: 0.208→8.591, y: -17.592→-0.208)
│   │
│   ├── LIVING_SET              → Rm_Living_1
│   ├── DINING_SET              → Rm_Dining_1
│   ├── KITCHEN_CABINET_SET     → Rm_Kitchen_1
│   └── TOILET_BLOCK_FIXTURES   → Rm_Bath_L1
│
├── FLOOR_SLAB_L2  dZ=+3000mm        ← upper floor slab (pending)
│
├── FLOOR_DX_L2_STD  "Level 2"     ← floor Orderline
│   │   dZ = +3000mm (one storey above Level 1)
│   │   footprint: 7117 × 17384mm   (x: 0.834→7.951, y: -17.592→-0.208)
│   │
│   ├── BED_SET                 → Rm_Bedroom_2
│   ├── BED_SET_MASTER          → Rm_Master_Bed_2
│   ├── WARDROBE_SET            → Rm_Wardrobe_2
│   └── TOILET_BLOCK_FIXTURES   → Rm_Bath_L2
│
└── ROOF_ASSEMBLY  dZ=+6000mm        ← roof (pending)
```

Without FLOOR_DX_L2_STD declaring `dZ=+3000mm`, Level 2 rooms default to Z=0 and are superimposed on Level 1.
Without FLOOR_SH_GF_STD, SH rooms are resolved directly from flat absolute coords in ad_room_boundary — bypassing the relational chain.

### Orderline Anatomy at Each Layer

Every BOM Orderline in `ad_element_rule` carries the same three attribute groups — exactly like a C_OrderLine in iDempiere:

| Group | iDempiere C_OrderLine | BIM ad_element_rule | Example (FLOOR_DX_L2) |
|---|---|---|---|
| **What** | M_Product_ID | `family_ref` | `FLOOR_DX_L2_STD` |
| **Where** | — (ERP has no space) | `host_type` + `host_ref` + `position_rule` + fractionX/Y | `host_type=BUILDING`, `position_value_3=3000` |
| **Space** | Qty × UOM | `width_mm` × `depth_mm` × `height_extent_mm` | 7117 × 17384 × 3000 |

For **UNIT-level** Orderlines:
- `host_type = 'BUILDING'`, `family_ref = 'UNIT_SH_STD'`
- `position_rule = 'FRACTION'`, fractionX/Y = 0.5
- `width_mm, depth_mm` = building footprint aggregated from ad_room_boundary

For **FLOOR-level** Orderlines:
- `host_type = 'BUILDING'`, `family_ref = 'FLOOR_DX_L2_STD'`
- `position_value_3 = storey_z_mm` (Z offset above ground)
- `width_mm, depth_mm` = floor footprint; `height_extent_mm` = storey clear height

For **ROOM-level** Orderlines (Phase BOM-1, already live):
- `host_type = 'ROOM'`, `family_ref = 'LIVING_SET'`
- `position_rule = 'FRACTION'`, fractionX/Y = 0.5 (centered in room)
- `width_mm, depth_mm` = room dims from ad_room_boundary (populated by Phase BOM-2b)

### iDempiere Naming Convention

> **Full dimension model:** see [BIMasBOMConcept.md](BIMasBOMConcept.md) §1–§2.
> M_Product is flattened into M_BOM. `ad_bom_child` = M_BOM_Line.
> Three orthogonal dimensions: `bom_category` (M_BomCategory — WHAT), `bom_owner` (C_BPartner — WHO), SpaceSize (AABB — HOW MUCH).

BOM IDs follow module-prefix discipline, matching iDempiere's `AD_`, `C_`, `M_` layer convention:

| Layer | BIM Table | iDempiere | SH example | DX example |
|---|---|---|---|---|
| Building order | BIM (`ad_building`) | C_Order | `Ifc4_SampleHouse` | `Ifc2x3_Duplex` |
| Order line | BIMLine (`ad_element_rule`) | C_OrderLine | placement instance | placement instance |
| Assembly (product+BOM) | M_BOM (`ad_bom`) | M_Product + M_BOM | `LIVING_4645x3308` | `UNIT_DUPLEX_STD` |
| Assembly child | M_BOM_Line (`ad_bom_child`) | M_BOM_Line | seq 1: Piano | seq 1: Dining_Table |
| Leaf item | M_BOM (no children) | M_Product (IsBOM=N) | `Sofa_3Seat` | `Chair_Dining` |
| Vendor/designer | `bom_owner` on M_BOM | C_BPartner | `SH` | `DX` |

`_STD` suffix = standard (off-the-shelf). Future variants: `UNIT_SH_TYPE_B`, `FLOOR_DX_L1_PREMIUM`.

### What is Live vs Missing (Phase BOM-2 audit — updated 2026-02-25)

| Layer | Table | Status |
|---|---|---|
| UNIT Orderlines (`UNIT_SH_STD`, `UNIT_DUPLEX_STD`) | `ad_element_rule` | ❌ Missing — Phase BOM-2c |
| FLOOR Orderlines (`FLOOR_SH_GF_STD`, `FLOOR_DX_L1/L2_STD`) | `ad_element_rule` | ❌ Missing — Phase BOM-2c (Z cascade works via `floorZOffsets` stopgap — see §8.5) |
| ROOM Orderlines (BOM Drop via ad_room_slot) | `ad_element_rule` | ✅ Live — Phase BOM-1 |
| ROOM spacing facts (width_mm, depth_mm from ad_room_boundary) | `ad_element_rule` | ✅ Live — Phase BOM-2b |
| SET item offsets (dx, dy, dz per child) | `ad_bom_child` | ✅ Live (metres; NOT `ad_bom_child_param` — see Three-Table Authority Rule) |
| Sub-BOM recursion (`child_bom_id` FK on `ad_bom_child`) | `ad_bom_child` | ✅ Live — Phase 4c. Proven: `SOFA_AREA` is a child BOM of Sofa in `SH_LIVING_SET`. Coffee_Table + Side_Tables are children of `SOFA_AREA` with IFC-calibrated offsets relative to Sofa's centroid. Wherever GPD lands Sofa, the cluster follows. |
| GPD-based locator dispatch (`locator_ref`, `layout_strategy`) | `ad_bom_child` | ✅ Live — Phase 4c. `SH_LIVING_SET` Piano/Sofa/Loveseat tagged `NORTH_WALL / LINEAR`. `resolveWithGPD()` in `FurnitureBOMResolver` advances GPD along hostAxis. |
| GGF/GF catalog entries (ad_bom + ad_bom_child hierarchy) | `ad_bom` | ✅ Live — Phase BOM-2a |
| bom_category dimension (`ad_bom.bom_category`, `ad_building.bom_category`) | `ad_bom` | ✅ Live — Phase 4c. SH=5 BOMs, DX=4, TB=2, MY=7, NULL=27 global. Java dispatch pending: `BOMAssemblerAD.lookupSlots()` filter. |

Without UNIT and FLOOR Orderlines, rooms are resolved from flat absolute coords in
`ad_room_boundary` (world XY hardcoded from Revit extraction). The relational chain is
broken at the top two levels. Adding FLOOR Orderlines restores the cascade:
`UNIT_abs + FLOOR_rel_dZ + ROOM_rel_xy = correct world position`.

### Building-Specific Room Topologies

`SH_LIVING` is not the same as `DX_LIVING`. The Sample House living room is a single
open rectangle at ground level combined with the dining area. The Duplex living room
occupies a distinct zone in a multi-unit floor plate with party walls on one side.
Both reference `LIVING_SET` as their furniture assembly, but the room itself is a
different spatial product — different dimensions, different wall interfaces, different
natural light exposure.

Future buildings (`AA_`, `BB_`, ...) will have their own room topologies. Each is a
distinct entry in the catalog with its own name prefix:

| Building | Living room BOM | Distinct because |
|---|---|---|
| `UNIT_SH_STD` | `SH_LIVING_GF` | Single storey, combined living+dining zone |
| `UNIT_DUPLEX_STD` | `DX_LIVING_L1` | Level 1, party wall on east face |
| `UNIT_TBLKTN_STD` | `TBLKTN_COMMON_GF` | Open-plan LIVING+DINING+KITCHEN hybrid |
| Future `UNIT_AA_STD` | `AA_LIVING_STD` | New topology, new variant |

**If you need a new spatial arrangement, it is a new variant** — add a catalog entry,
do not modify an existing one. The existing SH and DX rooms are the Rosetta Stones:
their arrangements are extracted, not invented. New topologies derive from new Stones.

Building-specific naming applies at the UNIT, FLOOR, and ROOM topology levels — where
the spatial arrangement genuinely differs per building. It does **not** propagate down
to the leaf items. The lower the level, the more generic and reusable:

```
UNIT_SH_STD       ← always building-specific (unique floor plate)
  FLOOR_SH_GF_STD ← always building-specific (unique storey footprint)
    SH_LIVING_GF  ← building-specific room topology (SH open-plan vs DX party-wall)
      LIVING_SET  ← may be shared across buildings (same furniture arrangement)
        Sofa_3Seat      ← generic catalog item (a sofa is a sofa)
        Coffee_Table    ← generic catalog item
        Chair_Dining    ← generic catalog item (chair, table, screw, basket, vase)
```

Leaf items (`Chair_Dining`, `Bed_Queen`, `Sofa_3Seat`, a screw, a vase) are pure
M_Product entries in the catalog — no building affinity, no topology, just intrinsic
geometry and material. Any building can reference them. They are the shared vocabulary;
the upper layers are the building-specific sentences constructed from that vocabulary.

### All Layers Are FK Chains — Nothing Is Invented

Every Orderline at every layer is **parent metadata containing a FK reference** to a
catalog product — identical to iDempiere's `C_OrderLine.M_Product_ID → M_Product` or
`C_OrderLine.M_ProductBOM_ID → M_BOM`.

```
ad_element_rule (C_OrderLine)
  family_ref = 'FLOOR_DX_L2_STD'           ← FK → ad_bom.bom_id

ad_bom (M_BOM)
  bom_id = 'FLOOR_DX_L2_STD'
  ↓ children via ad_bom_child (M_BOM_Line)
    child_bom_id = 'BED_SET_MASTER'         ← FK → ad_bom.bom_id (recursive)
    child_bom_id = 'TOILET_BLOCK_FIXTURES'  ← FK → ad_bom.bom_id

ad_bom_child_param (C_BOM_Line attributes)
  dx, dy, dz per child                      ← positional attributes on the FK link

ad_product_dim (M_Product)
  product_id = 'Bed_King'                   ← leaf catalog entry (intrinsic dims only)
```

Nothing is hardcoded in Java. The compiler walks this FK chain at runtime — the same
explosion logic iDempiere uses for MRP BOM Drop. Edit the data, re-compile, get a
different building. The engine does not change.

### Self-Orienting BOMs — Phantom Items Lock the Block

A toilet cannot be wrong-facing because it does not carry its own orientation in isolation.
Its entire space is defined by its phantom items — the items that are part of the bathroom
BOM but are not furniture: the **door** (ENTRY face), the **ceiling** (TOP), the
**exterior wall** (EXTERIOR face), the **plumbing wall** (WALL_BACK).

These phantom items are the MANIFEST face contracts of the bathroom BOM. When the
contractor assigns the bathroom BOM to a room, the block's orientation is resolved once
from those face contracts. Every item inside — toilet, basin, shower — is then
positioned and rotated relative to that locked orientation. The toilet's
`rotation_rule = FACE_AWAY_FROM_WALL` resolves to whichever wall carries `WALL_BACK`
in the MANIFEST. That wall is the plumbing wall. The plumbing wall is always correctly
identified because it is part of the BOM definition, not individually placed.

```
BATHROOM_WC_SET (block orientation locked by MANIFEST)
  MANIFEST:
    SOUTH face = ENTRY(door)         ← phantom item: door position
    NORTH face = WALL_BACK           ← phantom item: plumbing wall
    TOP   face = ceiling             ← phantom item: ceiling / roof extent
    EAST  face = EXTERIOR (or PARTY) ← phantom item: outside or party wall

  TOILET  rotation_rule=FACE_AWAY_FROM_WALL → faces south (toward ENTRY)   ✓
  BASIN   rotation_rule=FACE_AWAY_FROM_WALL → faces south or east           ✓
```

**The door-outside trap:** a door seen from outside the building appears to be on a
different wall than the same door seen from inside the room. Manual assignment of
orientation based on visual inspection will rotate the entire bathroom block wrongly —
thinking the door wall is the east wall when from inside it is the west wall.
The BOM chain avoids this entirely: the door's ENTRY face is derived from its world
coordinate position in the extracted Stone geometry, not from visual judgment.
The coordinate is objective; the perspective is not.

Compare to flat independent placement: toilet, basin, and door are each placed as
separate `ad_element_rule` rows with independent orientation values. Each can be
individually wrong. The BOM block placement makes individual misorientation impossible —
the block rotates as one, phantom items and all.

This is why the BOM chain must be complete before any orientation is resolved.
A bathroom BOM with no FLOOR Orderline parent has no declared block orientation —
the phantom items exist in the data but their face assignments are not anchored
to the building grid. The toilet then falls back to flat placement and can be wrong.

### Offsets Are Local — World Coords Are Dynamically Accumulated

The `dx, dy, dz` values in `ad_bom_child_param` are **local** to the parent BOM's own
coordinate space. They are not world coordinates. They are only "flat" (fixed) relative
to their immediate parent. When the parent is itself placed by its parent, the child's
world position is **dynamically calculated** by accumulating through the chain at compile time.

```
ad_bom_child_param: Sofa_3Seat  dx=-1.1, dy=0.5   ← local to LIVING_SET space

LIVING_SET room placed at world origin:  (minX=1.620m, minY=-1.246m)
                                          ↓ accumulated by compiler
Sofa world position = (1.620 + (-1.1), -1.246 + 0.5) = (0.520m, -0.746m)
```

This means:
- The same `LIVING_SET` BOM with identical `dx/dy` offsets produces **different world positions**
  in SH (placed at world 1.620, -1.246) vs DX (placed at its own room world origin).
- The BOM catalog entry does not store world coords — it stores relationships.
- Changing the room's placement Orderline (host_ref, fractionX/Y) moves all furniture
  inside it automatically — no furniture rows need touching.

The "flat" storage in `ad_bom_child_param` is by design: it makes the BOM catalog
**reusable across buildings**. The world coordinate is an emergent property of the
chain, computed once at compile time by `FurnitureBOMResolver.expandBOMNode()`.

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
  → shape from ad_parametric_mesh_param, position from ad_bom_child (dx/dy/dz in metres)

Level 0: COMPONENT (exists — component_definitions)
  e.g., Door_900x2100, Light_Downlight, Toilet_WC_FlushTank_6Lpf

Level 0.5: MEP SUB-ASSEMBLY (exists — T_CONNECTOR_ASSEMBLY etc.)
  e.g., SPRINKLER_DROP = tee + transition + drop + head
  MANIFEST: TOP=MAIN_HOOKUP(dia=27mm), BOTTOM=PENDANT_HEAD

Level 1: ROOM ASSEMBLY                ✅ LIVE — Phase BOM-1 (2026-02-21) + Phase 4c (2026-02-25)
  Phase BOM-1: ad_room_slot dispatch → BOM anchor rows → FurnitureBOMResolver expansion
  Phase 4c:    GPD dispatch (locator_ref/layout_strategy on ad_bom_child) + sub-BOM recursion
               (child_bom_id). Piano/Sofa/Loveseat placed via NORTH_WALL GPD. SOFA_AREA
               sub-BOM proves child_bom_id recursion: Coffee_Table + Side_Tables cluster
               at Sofa centroid wherever GPD lands it.

Level 2: UNIT ASSEMBLY — rooms composed with interface matching
                          ❌ Phase BOM-2c — UNIT_DUPLEX_STD, UNIT_SH_STD ad_element_rule rows
                          Stopgap: floorZOffsets reads Z from FLOOR BOM rules (§8.5)

Level 3: FLOOR ASSEMBLY — units + core + circulation
                          ❌ Phase BOM-2c — FLOOR_1_STD, FLOOR_2_STD ad_element_rule rows

Level 4: BUILDING — DSL selects and stacks floor assemblies
                    ✅ Partially — DSL declares rooms explicitly; full DAG pending
                    Mode B-pure (generative): TopologyMaker produces ad_room_boundary rows
                    before compilation. TERRACE_MY_1S is the first proven generative building.
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

### Three Compilation Modes

The compiler has a single code path. What changes between buildings is **data availability** in
`ad_room_boundary`. The view layer (`v_verified_room_boundary`) signals which mode applies
per room via the `coordinate_frame` column — not per building, not per compiler flag.

**Mode A — Pure Rosetta Stone**
```
coordinate_frame = IFC_GLOBAL_MM
All room boundaries extracted from a reference IFC
Examples: SH (Ifc4_SampleHouse), DX (Ifc2x3_Duplex)
```
Every room has a verified `ad_room_boundary` row with `coordinate_frame = 'IFC_GLOBAL_MM'`.
The compiler reads world coordinates directly from `v_verified_room_boundary`. No derivation
needed. G8 gate verifies nearest-neighbour < 500mm vs reference.

**Mode B-semi — Mixed Provenance**
```
Some rooms IFC_GLOBAL_MM, some GRID_DERIVED_MM (excluded from views)
Compiler serves extracted rooms via Mode A path, derives remaining via Mode B path
Example: TB-LKTN (Ifc2x3_Laketown — partial extraction)
This is the most realistic production case.
```
The view filter (`coordinate_frame NOT IN ('GRID_DERIVED_MM')`) excludes the hand-approximated
rooms. The compiler falls back to DSL-qty derivation for those rooms only. A building can
have 5 rooms in Mode A and 2 in Mode B simultaneously. No code branching — data presence
determines the path.

**Mode B-pure — Generative**
```
No reference IFC. Zero rows in ad_room_boundary for this building_type.
Compiler derives all boundaries from DSL qty + floor dimensions.
Example: KAMPUNG_HOUSE (hypothetical new building type with no Stone)
```
When `v_verified_room_boundary` returns zero rows for a building, the compiler uses:
```
space_per_unit_mm² = floor_area_mm² / qty
available_space_mm = SQRT(space_per_unit_mm²)
```
Same cascade as Mode A, different data source. The SpaceSolver (future) or Template
Topology Path (see `space_solver_research.md`) produces `ad_room_boundary` rows with
`coordinate_frame = 'CONSTRAINT_SOLVED'` or `'DERIVED_MM'` before compilation.

**Mode signal is in the data, not the compiler:**
```
v_verified_room_boundary
  coordinate_frame = 'IFC_GLOBAL_MM'      → Mode A row
  coordinate_frame = 'DERIVED_MM'         → Mode B-semi or B-pure, valid for compilation
  coordinate_frame = 'CONSTRAINT_SOLVED'  → Mode B-semi or B-pure, valid for compilation
  coordinate_frame = 'GRID_DERIVED_MM'    → excluded by view — caller uses generative path
```

---

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

---

## §8. Place — The Fundamental Spatial Unit

Every geometry element in the BIM DAG — from building unit down to a single
furniture piece — possesses a **Place**. Place is the complete spatial descriptor:
what volume it occupies, which way it faces, and where its anchor stud is.

### 8.1 The Place Record

```java
/**
 * Fundamental spatial descriptor for every geometry element.
 *
 * BoundingBox  — the volume this element occupies
 * up           — which axis is "up" (usually [0,0,1]; explicit for ramps/tilts)
 * front        — the facing direction ("North" bearing as unit vector)
 * hostAxis     — the sequencing direction along the host wall (⊥ to front)
 * anchor      — the stud: canonical XYZ connection reference in parent frame
 * locatorRef  — which M_Locator owns this element's GPD.
 *               M_Locator = the ABL grid cell (in mm) that bounds this element's
 *               placement position. Labels (NORTH_WALL, CENTRE…) are human aliases
 *               for mm grid line intersections from ad_building_grid.
 *
 * front ⊥ hostAxis — always perpendicular by definition:
 *   front    = perpendicular to host wall (faces INTO the room)
 *   hostAxis = parallel to host wall (sequences ALONG the room edge)
 *
 * The highest unit initialises anchor=(0,0,0) — this is the GPD origin.
 * Every child declares its anchor as an offset from its parent's anchor.
 * Resolution is a pure pointer walk — no absolute coords stored anywhere.
 */
record Place(
    BoundingBox  bbox,        // spatial extents (width, depth, height) — all mm
    Vector3D     up,          // "which way is up"
    Vector3D     front,       // "which way this element faces"
    Vector3D     hostAxis,    // "along which axis siblings sequence"
    Point3D      anchor,      // the stud — XYZ connection reference in mm
    String       locatorRef   // M_Locator reference: NORTH_WALL, SOUTH_WALL, CENTRE, FLOAT…
                              // resolves to mm grid cell via ad_building_grid
)
```

### 8.2 GPD — GlobalPointDirection

The GPD is the **moving anchor** of the current placement context. It is not a
fixed origin — it is a live 3D pointer that advances after each element is placed:

```
GPD advances along hostAxis (NOT along front).

Wrong (diagonal problem):  advance along front → element ends up in the middle of room
Correct:                   advance along hostAxis → element sits beside the previous one along the wall
```

GPD advancement:
```
stride = bbox.extentAlong(hostAxis) + bufferChild.extentAlong(hostAxis)
nextGPD = Point3D(
    currentGPD.x + hostAxis.x × stride,
    currentGPD.y + hostAxis.y × stride,
    currentGPD.z                           // Z stays at floor level
)
```

Each M_Locator has its own independent GPD. Cross-Locator placements (piano on NORTH_WALL,
dining in CENTRE) do not share a GPD — their starting points are derived independently
from the room bbox (ad_room_boundary mm extents).

### 8.3 Variance Child — The Spatial Variable

Buffer spacers between furniture pieces are **not a special construct** — they are
ordinary BOM children whose `bbox` is a variable set `(var_x, var_y, var_z)`:

```
ad_product_dim for SPACER_VAR:
    width  = NULL   ← variable
    depth  = NULL   ← variable
    height = NULL   ← variable
```

Resolution:
```
variance = room_extent − Σ(all fixed children extents along hostAxis)
```

The room extent is ALREADY KNOWN from `ad_room_boundary`. No first pass needed.
The variance child receives whatever remains. All three dimensions are independently
variable — any can resolve to 0.0 (perfect fit, no slack).

**Three states:**
```
variance > (0,0,0)  → healthy — slack absorbed into spacers
variance = (0,0,0)  → perfect fit
variance < 0        → GIC violation — fixed children overflow Locator extent
```

The variance child IS the geometry integrity check. No separate overflow validator needed.

**Reuse across room sizes:**
The same BOM template fits any room of the matching category. Larger rooms absorb
more variance; smaller rooms (above minimum) absorb less. The fixed furniture never
moves — only the variance child stretches or compresses:

```
House A  living room = 6500mm → variance = 1300mm  ✓
House B  living room = 7200mm → variance = 2000mm  ✓  (more breathing room)
House C  living room = 5400mm → variance =  200mm  ✓  (tight fit)
House D  living room = 4900mm → variance = −300mm  ✗  GIC violation — BOM doesn't fit
```

### 8.4 PhantomLayout — Transient Empty Storage

The PhantomLayout is the transient working state during BOM resolution — the spatial
equivalent of the SAP WMS **Empty Storage** bin record. It tracks the current fill
state of an M_Locator and the next available anchor point for the following element.

> **Phase 4c simplification (2026-02-25):** The WMS ceremony (DocStatus DR/CO/VO, next_anchor
> persistence, audit columns) was dropped — a synchronous compiler needs none of it.
> The useful concept is distilled into `EmptySpace` — a 3-field immutable record in `com.bim.orm`:
> ```java
> record EmptySpace(String locatorRef, double capacityMm, double usedMm) {
>     double remainingMm()              { return capacityMm - usedMm; }
>     boolean isOverflow()              { return remainingMm() < 0; }
>     EmptySpace place(double extentMm) { return new EmptySpace(locatorRef, capacityMm, usedMm + extentMm); }
> }
> ```
> `EmptySpace` is zero-DB, fully testable in unit tests with no setup.
> `wm_empty_storage_line` is demoted to an optional post-compilation write-only summary.
> PhantomLayout remains the full resolution context (nextAnchor, placed list) while
> `EmptySpace` is the capacity accounting atom that lives inside it.

```java
/**
 * Transient — NOT persisted. Exists only during DSL edit / compile resolution.
 * Equivalent to SAP WMS Empty Storage: current fill state + next putaway coordinate.
 * On DSL save → resolves to permanent ad_bom_child rows.
 *
 * locatorRef: the M_Locator (ABL grid cell in mm) this phantom tracks.
 * nextAnchor: mm coordinate — where the next element's stud locks in.
 */
record PhantomLayout(
    String       hostBomId,    // which BOM template owns this Locator
    String       locatorRef,   // M_Locator reference — mm grid cell (NORTH_WALL, CENTRE…)
    RoomExtent   room,         // fixed container — from ad_room_boundary (mm)
    Point3D      nextAnchor,   // the empty Locator start — where next child goes (mm)
    double       remainingMm,  // how much Locator extent is still free (mm)
    List<Place>  placed        // children already resolved, in sequence
)
```

`nextAnchor` IS the incremental pointer. It advances after each child is placed.
`remainingMm` is the live capacity of the empty bin. The variance child's extent
equals `remainingMm` when all fixed children are placed — they are the same residual
viewed from two perspectives.

**Placement strategies at nextAnchor:**
```
ADJACENT  → place at nextAnchor directly (pack forward, tight against last child)
OPPOSITE  → place at (nextAnchor + remainingMm − newChild.extent) (from far end inward)
FLOAT     → explicit fraction within Locator (existing ROOM_FRACTION path)
```

**DSL "add another element" flow:**
```
1. LIVING_SET resolved → PhantomLayout { nextAnchor=(4.75,0.5,0), remaining=1300mm }
2. User adds SOFA_SMALL (900mm) → check: 900 ≤ 1300 ✓
3. User selects ADJACENT or OPPOSITE
4. Phantom updates: remaining = 1300 − 900 = 400mm
5. DSL save → new ad_bom_child row, sequenceNo = max+1
   Variance child auto-recalculates to 400mm
```

### 8.5 Floor Orientation Cascade (Phase 4b)

A floor's complete spatial frame has two components, both in `ad_element_rule`:

```
position_value_3 (mm)  → Z origin — where "Up" begins (floor elevation)
orientation (radians)  → bearing from global North — which way children face
```

Together these define the floor's world transform. DX Level 2 has both:
```
position_value_3 = 3000mm  → origin Z = 3.0m (upper storey)
orientation      = π        → rotated 180° from North (party-wall mirror duplex)
```

Resolution in `RelationalResolver.computeBomAnchorForRoom()`:
```
// Rotate each furniture position around room centroid
dx = pf.x() − roomCx
dy = pf.y() − roomCy
px = roomCx + dx·cos(θ) − dy·sin(θ)
py = roomCy + dx·sin(θ) + dy·cos(θ)
childRot = pf.rotation() + θ
```

The `floorOrientations` map (loaded by `loadFloorOrientations()`) carries the
orientation per floor BOM ID — the same Map pattern as `floorZOffsets`.

### 8.6 ABL Cross-reference

The PhantomLayout is the Empty Storage record for the ABL M_Locator.
M_Locator = the grid cell (A+B+L in mm) that bounds the placement position.
Full ABL / WMS mapping: see `ARCHITECTURE.md §9`.

---

## §9. BOMCascadeResolver — Architectural Convergence

> **Governing principle (2026-02-25):** All resolver code must be abstract — separation of concerns.
> Resolvers know nothing about IFC, nothing about writers, nothing about SQL sinks.
> They receive a BOM tree + space envelope and return placed elements. Nothing more.

### 9.0 Layer Boundaries

```
DATA LAYER         ad_bom_child, ad_room_boundary, ad_product_dim (SQLite, read-only at resolve time)
       ↓
RESOLVER LAYER     BOMCascadeResolver.resolve() → List<PlacedElement(ref, xyz, rotation, namePattern)>
                   Abstract: no IFC types, no writers, no SQL writes
       ↓
ADAPTER LAYER      StoreyCompiler: PlacedElement → FixtureSpec  (thin mapping, no placement logic)
       ↓
WRITER LAYER       MEPWriter.writeFixture(), BuildingWriter: FixtureSpec → output DB rows
       ↓
VALIDATOR LAYER    Cross-floor MEP continuity, riser shaft penetrations, UBBL compliance
                   Reads compiled output — knows nothing about placement logic
```

**The current `StoreyCompiler.applyPlacementOverrides()` bridge violates this boundary.**
It contains rotation parsing (`Double.parseDouble(fp.orientation())`) — placement logic
that should not exist in an adapter. The bridge exists because `RelationalResolver`
serialises rotation to a string and `StoreyCompiler` deserialises it. Once
`BOMCascadeResolver` outputs `PlacedElement` directly, the bridge and its string
round-trip disappear.

**MEP cross-floor is a validator concern, not a placement concern.** Placement is
per-storey (MEP ceiling set within a FLOOR BOM). Vertical risers connecting
FLOOR L1 to FLOOR L2 are read from the compiled output by the validator — the
resolver never needs to know about adjacent floors.

### 9.1 The Single Recursive Operation

```
Given a BOM level + space envelope:
    select the fitting BOM that covers this envelope  (BomTierResolver logic)
    compute child anchors via wall rule + locatorRef   (FurnitureBOMResolver logic)
    recurse: each child → resolve(nextLevel, childAnchor, childEnvelope)
    return List<PlacedElement> — absolute XYZ for all levels
```

`BomTierResolver.TIERS = { UNIT, FLOOR, ROOM, SET, ITEM }` — the full ALB cascade.
`FurnitureBOMResolver.expandBOMNode()` — handles ROOM→SET→ITEM tail only.
`RelationalResolver.loadBomChain()` — walks UNIT→FLOOR→ROOM for structural elements.

All three are partial implementations of the same operation at overlapping level ranges.

### 9.2 Unified Data Model

```java
// Shared record — combines what all three walkers need
record BOMChild(
    String   bomId,           // this child's BOM (for sub-BOM recursion)
    String   tier,            // UNIT / FLOOR / ROOM / SET / ITEM
    double   minSpaceMm,      // fit gate — was BomTierResolver only
    String   locatorRef,      // NORTH_WALL, CENTRE, FLOAT (Phase 4c)
    String   layoutStrategy,  // LINEAR / SURROUND / FLOAT (Phase 4c)
    boolean  isVariance,      // SPACER_VAR flag (Phase 4c)
    double   dx, dy, dz,      // metres (placement offsets, from ad_bom_child)
    String   wallRule,        // NO_OPENINGS / OPPOSITE_WORK / END_WALL / CENTER
    double   rotation,        // radians
    String   childBomId       // FK for sub-BOM recursion (SOFA_AREA pattern)
)
```

### 9.3 Resolver Structure

```
BOMTreeLoader          — loads ad_bom_child once into shared BOMNode/BOMChild tree
                         (ORM: X_AdBomChild; carries ALL columns both resolvers need)

BOMCascadeResolver.resolve(tier, anchor, envelope, bomId)
    → selects BOM that fits envelope at this tier       (BomTierResolver logic)
    → computes child anchors via wall rule + locatorRef (FurnitureBOMResolver logic)
    → if child.childBomId != null → recurse (SOFA_AREA sub-BOM pattern)
    → recurses: each child → resolve(nextTier, childAnchor, childEnvelope)
    → returns List<PlacedElement> — full XYZ for all levels
```

`EmptySpace` (§8.4) is the capacity gate at the ROOM→LOCATOR boundary.

### 9.4 Implementation Plan

> **DAO pattern:** All resolver data access via `ModelQuery<X_AdBomChild>` etc. (orm-core).
> See [DEVELOPER_GUIDE.md — DAO Pattern](DEVELOPER_GUIDE.md#dao-pattern-orm-core) for details.

| Step | Action | Layer |
|---|---|---|
| 1 | Create `BOMTreeLoader` — load `ad_bom_child` into `BOMNode`/`BOMChild` tree via DAO | DAO |
| 2 | Add Phase 4c columns to `BOMChild`: `locatorRef`, `layoutStrategy`, `isVariance`, `childBomId` | Record |
| 3 | Write `BOMCascadeResolver.resolve(tier, anchor, envelope, bomId)` — abstract resolver | Resolver |
| 4 | Wire `RelationalResolver` to delegate to `BOMCascadeResolver` for UNIT→FLOOR→ROOM | Adapter |
| 5 | Replace `StoreyCompiler.applyPlacementOverrides()` bridge with direct cascade output | Adapter |
| 6 | Delete `BomTierResolver` (view-based, replaced) + deprecate `FurnitureBOMResolver` | Cleanup |
| 7 | Witness `W-CASCADE-1` — SH LIVING_ROOM resolves identical placed furniture as current output | Test |

**Pre-condition:** `migration_phase4c_wms_locator.sql` applied + `height_extent_mm` populated for all FLOOR Orderlines.

### 9.5 Migration Path

The cascade will initially skip Levels 2/3 (UNIT/FLOOR Orderlines still missing) and operate over the same ROOM→SET→ITEM range as today — so no regression. When UNIT/FLOOR Orderlines land (Phase BOM-2c), the cascade naturally extends upward without code change.

**Three compilation modes (§ "Three Compilation Modes") do not change** — `BOMCascadeResolver` operates on the same `ad_room_boundary` coordinate_frame signals. The data determines the mode; the resolver is mode-agnostic.

---

## Appendix — Roadmap (2026-02-25 state)

| Phase | Status | What | Impact |
|---|---|---|---|
| BOM-1 | ✅ DONE | Room slot dispatch + BOM expansion (SH/DX/TB-LKTN) | Furniture from ad_room_slot × ad_room_boundary |
| BOM-2a/b | ✅ DONE | GGF/GF catalog entries + ROOM spacing facts | Five-hop chain data complete |
| BOM-2c | ❌ MISSING | UNIT/FLOOR Orderlines in ad_element_rule | Closes the top two relational hops |
| Phase 4b | ✅ DONE | Floor orientation cascade (DX L2 = π, floorOrientations map) | DX upper furniture correct Z + bearing |
| Phase 4c GPD | ✅ DONE (partial) | locator_ref/layout_strategy on ad_bom_child; resolveWithGPD() | NORTH_WALL linear placement live for SH LIVING_SET |
| Phase 4c sub-BOM | ⏳ Coder task | resolveWithGPD() expand child.childBomId() at GPD centroid (+6 lines) | Re-enables G8-SH (SOFA_AREA cluster follows Sofa) |
| EmptySpace | ⏳ Coder task | Create EmptySpace record in orm-core (+40 lines) | Closes W-PHANTOM-1; testable capacity gate |
| BOMCascadeResolver | ⏳ Planned (WatchDog) | Unify BomTierResolver + FurnitureBOMResolver (§9) | One walker, all levels |
| Mesh dispatch | ⏳ Pending | BuildingWriter: family_ref → generator_class → ParametricMesh | Unblocks TB-LKTN roofs + drains |
| material_ref | ⏳ Pending | Add material_ref to ad_product_dim + seed + compiler reads it | BOM furniture gets color |
| G8-DX | ⏳ Deferred | Replace 40 NULL-bound LOCAL_MM rooms with IFC_GLOBAL_MM | G8-DX calibration |
| AD Events | ⏳ Queued | SpatialRuleValidator, CalloutCascadeValidator | L5 compliance layer |
