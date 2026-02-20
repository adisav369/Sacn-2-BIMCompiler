# Technical Building Guide
**BIM Intent Compiler — Developer Reference**
*Date: 2026-02-21 | Replaces: any older Dev Guide*

> **STATUS — UNDER ACTIVE REVIEW (2026-02-21)**
> This guide is undergoing a verification session to cross-check all claims and identify
> any gaps against the AD Architecture and Prefab Architecture source documents:
> - `docs/TheRosettaStoneStrategy.txt` — Linguist's Method, X-ray scoring, 8 compose primitives
> - `docs/PREFAB_ARCHITECTURE.md` — DSL = catalog selector, 6-level assembly hierarchy
> - `docs/RELATIONAL_PLACEMENT_SPEC.md` — Phase RM migration, relational rules replacing flat coords
>
> Any section marked **[VERIFY]** has not yet been confirmed against the source docs.
> Sections without a marker have been cross-checked in the current session.

---

## 0. The C_Order Analogy — How the ERP Layers Map

The compiler is modelled on iDempiere's order-to-delivery pipeline:

| ERP Concept | BIM Layer | Table | One Row = |
|---|---|---|---|
| C_Order header | Building declaration | `ad_building_registry.dsl_content` | One building |
| C_OrderLine | Element placement rule | `ad_element_rule` | One element in one building |
| M_Product | Catalog product master | `ad_product_dim` | Intrinsic dims of one product type |
| M_BOM_Line (template) | Room slot template | `ad_room_slot` | What a room type should contain |
| C_BOM_Line (instance) | Assembly child offset | `ad_bom_child_param` | dx/dy/dz of one child in one assembly |

**Reading order for new developers:** DSL → ad_element_rule → ad_product_dim → ad_room_slot → ad_bom_child_param.

---

### 0.1 The MRP BOM Explosion — The Full Picture

The car manufacturing analogy makes the architecture clear. A Duplex or SampleHouse IS the car:

```
MRP/ERP                          BIM equivalent
─────────────────────────────────────────────────────────────────
GGF BOM: Automobile              UNIT_DUPLEX_STD  (the complete house)
  └─ GF BOM: Chassis+Body        FLOOR_1_STD + FLOOR_2_STD
       └─ Parent BOM: Interior   LIVING_ROOM_STD + BEDROOM_STD + KITCHEN_STD
            └─ Child BOM: Sets   LIVING_SET + DINING_SET + BED_SET + PIANO
                 └─ Leaf items   sofa, dining chair ×6, table, bed, side table
```

**The BOM Drop** (MRP → Order Lines):
In MRP, a BOM Drop copies BOM lines to Sales Order lines. The planner then edits:
- Remove the piano → delete that order line
- Swap DINING_SET for CANTEEN_SET → change `family_ref` on the anchor row
- Add a custom armchair → INSERT one new `ad_element_rule` row with its dimensions

The **compiler is MRP Execution** — it reads the edited `ad_element_rule` schedule and
computes positions. It does not care what edits were made. Zero code changes needed.

**Implementation status (2026-02-21):**

| Level | BIM equivalent | Table | Status |
|-------|---------------|-------|--------|
| GGF | Complete house (`UNIT_DUPLEX_STD`) | `ad_bom` | ❌ Not yet — next phase |
| GF | Floor assemblies (`FLOOR_1_STD`) | `ad_bom_child` | ❌ Not yet — next phase |
| Parent | Room space BOMs (`LIVING_ROOM_STD`) | `ad_room_slot` dispatch | ✅ Phase BOM-1 |
| Child | Furniture sets (`LIVING_SET`, `DINING_SET`) | `ad_bom_child` | ✅ Phase BOM-1 |
| Leaf | Individual items (sofa, chair, bed) | `ad_bom_child_param` offsets | ✅ Phase BOM-1 |

The bottom three layers (room space → sets → individual items) are live. The top two
(house GGF → floor GF) are the next phase. When complete, a new building type requires
only a DSL entry + one GGF BOM in `ad_bom` — no `ad_element_rule` rows written by hand.

---

## 1. Level 1 — C_Order: The DSL (Building Declaration)

### What the DSL is

`ad_building_registry.dsl_content` is the **order header**. It declares:
- Building identity (`building_id`, `profile`, `protocol`)
- Structural grid (axes + spacing → room boundary coordinates)
- Room types and their grid bounds
- Roof spec

**The DSL does NOT declare furniture, fixtures, openings, MEP, or positions.** Those are C_OrderLines in `ad_element_rule`.

### DSL Keywords and their meaning

```
BUILDING "TB_LKTN"
    profile:  "Malaysian_Residential"    ← selects assembly catalog variant
    protocol: "Residential_Single_Storey" ← governs which slot sets fire
    lod:      300                         ← Level of Detail gate for geometry
{
    GRID {
        axes:    A, B, C, D, E / 1, 2, 3, 4, 5   ← column / row labels
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.6, 1.5  ← mm between axes
    }

    STOREY "Ground Floor" level:0 height:3.0m {

        BEDROOM "bilik_utama" bounds:A2-C3 {   ← room_type maps to ad_room_slot
            exterior: south                      ← which faces are exterior walls
            exterior: west
            opens_to: common                     ← adjacency (door placement hint)
        }

        OPEN_PLAN "common" bounds:C2-D5 {
            zones: LIVING, DINING, KITCHEN       ← sub-type slots within open plan
            exterior: south
            exterior: north
        }
    }

    ROOF pitch:25deg overhang:700mm
}
```

### Room type keywords and their slot dispatch

| DSL Keyword | ad_room_slot room_type | Slot assemblies dispatched |
|---|---|---|
| `BEDROOM` | BEDROOM | BED_SET (required), WARDROBE_SET, CEILING_MEP |
| `BATHROOM` | BATHROOM | TOILET_BLOCK_FIXTURES (required), BATHROOM_VANITY_SET, EXHAUST, FLOOR_TRAP |
| `TOILET` | TOILET | TOILET_BLOCK_FIXTURES (required), BASIN, EXHAUST, FLOOR_TRAP |
| `LIVING` | LIVING | LIVING_SET (required), DINING_SET (if area≥6m²), CEILING_MEP |
| `OPEN_PLAN` | COMMON | LIVING_SET (if area≥15m²), DINING_SET (if area≥8m²), KITCHEN_CABINET_SET |
| `KITCHEN` | KITCHEN | KITCHEN_CABINET_SET (required), CEILING_MEP |
| `DINING` | DINING | DINING_SET (required), CEILING_MEP |
| `CORRIDOR` | — | no slots; walkway only |
| `PORCH` | — | no slots; attached roof only |

**Note on OPEN_PLAN:** Currently OPEN_PLAN bypasses slot dispatch. All furniture is placed via explicit `ad_element_rule` rows. See §7 for the gap.

### Adding a new building — DSL entry

Insert one row into `ad_building_registry`:

```sql
INSERT INTO ad_building_registry
    (building_id, building_name, building_type, provenance,
     dsl_content, output_db_path, reference_db_path,
     is_active, seq_no, expected_elements, geometry_fail_threshold)
VALUES
    ('MY_NEW_HOUSE',
     'My New House — Type C',
     'RESIDENTIAL',           -- building_type = RESIDENTIAL | COMMERCIAL (category)
     'GENERATIVE',            -- GENERATIVE (no reference IFC) | EXTRACTED (Rosetta Stone)
     '-- dsl content here --',
     'DAGCompiler/lib/output/MY_NEW_HOUSE.db',
     NULL,                    -- reference_db_path: NULL for generative
     1, 5,                    -- is_active, seq_no
     0,                       -- expected_elements: set after first successful run
     0);                      -- geometry_fail_threshold
```

**Zero Java files needed.** The compiler discovers and runs the new building automatically.

---

## 2. Level 2 — C_OrderLine: Element Rules (`ad_element_rule`)

### What an element rule is

One row = one element instance in one building. The compiler reads ALL rows for a building, calls `RelationalResolver.resolve()`, and gets back `PlacementAD.Placement` records (world-coordinate bounding boxes) that the writers emit as IFC entities.

### Column reference

```sql
element_ref        TEXT  -- Unique ID: "IfcDoor_1", "TOILET_bilik_utama_1"
ifc_class          TEXT  -- "IfcDoor", "IfcFurnishingElement", "IfcSanitaryTerminal" ...
storey             TEXT  -- Must match STOREY name in DSL: "Ground Floor"
discipline         TEXT  -- ARC | STR | MEP
is_active          INT   -- 1 = active, 0 = disabled (never delete rows)

-- Positioning: THREE MODES
host_type          TEXT  -- WALL | ROOM | BUILDING
host_ref           TEXT  -- "WALL_bilik_utama_SOUTH" | "bilik_utama" | "BUILDING"
position_rule      TEXT  -- FRACTION | BOUNDARY | ABSOLUTE
position_value     REAL  -- FRACTION: 0.0–1.0 along wall/room; ABSOLUTE: cx in mm
position_value_2   REAL  -- FRACTION: perp offset mm (wall) or fractionY (room)

-- Sizing (intrinsic — mirrors ad_product_dim)
width_mm           REAL  -- element width (X-axis)
depth_mm           REAL  -- element depth (Y-axis)
height_extent_mm   REAL  -- element height (Z-axis) — MUST be set or P01/P03 CRITICAL fail
height_mm          REAL  -- mounting height above floor (sill height for windows, etc.)

-- Identity
family_ref         TEXT  -- REQUIRED for fixtures/furniture: must match ad_product_dim.product_id
                          --   e.g., "FIXTURE_TOILET", "FURN_BED_DOUBLE"
                          --   EXTRACTED buildings may use verbatim Revit refs

-- Rotation
orientation        TEXT  -- Concrete: radians string "3.14159"
                          -- Semantic: "NS" | "EW" | "ALONG_HOST"
                          -- Resolved by RelationalResolver.resolveOrientation()
                          --   via ad_product_dim.conn_points → facing direction

-- Material (optional)
material_name      TEXT  -- e.g., "Concrete - Cast-in-Place"
material_rgba      TEXT  -- "#RRGGBBAA"
```

### The three position modes

**FRACTION on WALL** — place on a specific wall face at a fraction along its length:
```sql
host_type='WALL', host_ref='WALL_bilik_utama_SOUTH',
position_rule='FRACTION', position_value=0.4,   -- 40% along wall
position_value_2=0.0,                            -- perp offset mm (0 = flush to wall)
orientation='ALONG_HOST'                          -- door aligns with wall
```
Wall ref format: `WALL_{room_name}_{face}` where face ∈ {NORTH, SOUTH, EAST, WEST}.

**FRACTION on ROOM** — place at a fractional position within room bounds:
```sql
host_type='ROOM', host_ref='bilik_utama',
position_rule='FRACTION', position_value=0.5,    -- fractionX (50% = center)
position_value_2=0.85,                           -- fractionY (85% = near north wall)
```
fractionY ≥ 0.5 → back against north wall; fractionX < 0.5 → back against west wall.

**BOUNDARY** — element spans entire room boundary (walls, slabs):
```sql
host_type='ROOM', host_ref='bilik_utama',
position_rule='BOUNDARY',
-- width/depth/height_extent_mm define wall thickness, not room dims
```

**ABSOLUTE** — hardcoded world centroid (avoid — defeats relational intent):
```sql
host_type='BUILDING', host_ref='BUILDING',
position_rule='ABSOLUTE', position_value=-5059.012, position_value_2=-67.78
-- Only acceptable for Rosetta Stone replication (EXTRACTED buildings)
```

### Inserting element rules for a new building

```sql
-- Toilet in TB-LKTN bathroom
INSERT INTO ad_element_rule
    (building_type, storey, element_ref, ifc_class, discipline,
     host_type, host_ref, position_rule, position_value, position_value_2,
     height_mm, family_ref, width_mm, height_extent_mm, depth_mm,
     orientation, is_active)
VALUES
    ('MY_NEW_HOUSE', 'Ground Floor', 'TOILET_bathroom_1',
     'IfcSanitaryTerminal', 'MEP',
     'WALL', 'WALL_bathroom_NORTH',
     'FRACTION', 0.5, 0.0,
     0.0,                   -- height_mm: flush to floor
     'FIXTURE_TOILET',      -- family_ref → ad_product_dim.product_id
     400.0, 400.0, 700.0,   -- width, height_extent, depth (from ad_product_dim)
     'FACE_INTO_ROOM',      -- semantic orientation → resolved via conn_points
     1);
```

**Rule: height_extent_mm MUST be non-zero.** If zero, the Z-span dz=0 and P01/P03 fail.

---

## 3. Level 3 — M_Product: The Catalog (`ad_product_dim`)

### What the catalog is

One row = one product type with intrinsic geometry. These are the "standard parts" — a toilet is 400×700mm regardless of which building.

```sql
product_id     TEXT   -- "FIXTURE_TOILET" — referenced by ad_element_rule.family_ref
product_type   TEXT   -- DOOR | WINDOW | FIXTURE | FURNITURE | ELECTRICAL | STRUCTURAL
width          REAL   -- metres (X-axis)
depth          REAL   -- metres (Y-axis)
height         REAL   -- metres (Z-axis)

-- Clearance zones (none currently enforced — future CRD gate)
clear_front    REAL   -- clearance in front of product (door swing, toilet approach)
clear_back     REAL   -- clearance behind product
clear_left, clear_right, clear_above, clear_below  REAL

-- Fit constraints
fits_in        TEXT   -- "BATHROOM" — must be placed in this room type
requires_host  TEXT   -- "WALL" — must be placed against wall

-- Connectivity (JSON)
conn_points    TEXT   -- '[{"face":"BACK","type":"WASTE"},{"face":"LEFT","type":"SUPPLY"}]'
                       -- BACK face → element placed against wall → orientation FACE_INTO_ROOM
```

### conn_points and orientation resolution

`conn_points` drives the facing direction of fixtures and furniture. `RelationalResolver.resolveOrientation()` reads conn_points for the product and computes the rotation:

- BACK or WALL face in conn_points + element is ROOM-hosted → element faces INTO room
- Rotation assigned: north wall → π, south → 0, east → π/2, west → −π/2
- This is the correct way to set orientation — not hardcoded radians in ad_element_rule

**Current gap:** `family_ref` must be set AND the product must exist in `ad_product_dim` for conn_points to work. For EXTRACTED buildings (SH/DX), family_ref is a Revit string, not a catalog ID, so conn_points never fires.

---

## 4. Level 4 — M_BOM_Line (template): Room Slots (`ad_room_slot`)

### What room slots are

`ad_room_slot` defines what a room type **should contain** — independent of any specific building. It is the generic BOM template for each room type.

```sql
room_type     TEXT   -- "BATHROOM", "BEDROOM", "LIVING", "COMMON" ...
slot_name     TEXT   -- "SANITARY", "FURNITURE", "EXHAUST" ...
assembly_id   TEXT   -- "TOILET_BLOCK_FIXTURES", "BED_SET", "LIVING_SET" ...
                      -- references ad_bom (the assembly master)
slot_face     TEXT   -- "BACK" | "LEFT" | "TOP" | "BOTTOM" | "FRONT"
slot_priority INT    -- lower = placed first (e.g. SANITARY=10 before EXHAUST=50)
is_required   INT    -- 1 = compilation fails if slot cannot be filled, 0 = optional
profile       TEXT   -- NULL (all) | "MASTER" | "Malaysian_Institutional" ...
min_area      REAL   -- m² threshold: slot fires only if room area >= this value
```

### Area-gated slot selection

Slots with `min_area > 0` fire only when the room is large enough:

| Room type | Slot | Assembly | min_area | TB-LKTN room (m²) | Fires? |
|---|---|---|---|---|---|
| BEDROOM | FURNITURE | BED_SET | 0 | bilik_utama: 9.61 | ✓ |
| BEDROOM | FURNITURE | BED_SET_MASTER | 12 (profile:MASTER) | 9.61 | ✗ too small |
| COMMON | FURNITURE | LIVING_SET | 15 | common: 22.94 | ✓ |
| COMMON | DINING | DINING_SET | 8 | common: 22.94 | ✓ |
| COMMON | COUNTER | KITCHEN_CABINET_SET | 0 | common: 22.94 | ✓ |

**TB-LKTN common (22.94 m²) qualifies for ALL THREE common slots** — LIVING_SET + DINING_SET + KITCHEN_CABINET_SET. This is by design: Malaysian terrace house with open-plan kitchen/dining/living.

### Profile selection

```sql
-- Duplex bedroom with master profile
INSERT INTO ad_room_slot (room_type, slot_name, assembly_id, profile, min_area, is_required)
VALUES ('BEDROOM', 'FURNITURE', 'BED_SET_MASTER', 'MASTER', 12.0, 0);

-- Override: Malaysian institutional dining
INSERT INTO ad_room_slot (room_type, slot_name, assembly_id, profile, min_area, is_required)
VALUES ('DINING', 'FURNITURE', 'CANTEEN_SET', 'Malaysian_Institutional', 0, 1);
```

Profile is selected at the building level via `profile:` in the DSL. A BEDROOM in a `profile:"Malaysian_Residential"` building will NOT fire the `profile:MASTER` slot (no match).

### ✅ Phase BOM-1 (2026-02-21): Room slot dispatch now wired for furniture

**BOM anchor dispatch is LIVE for SH, DX, and TB-LKTN furniture:**
- `ad_room_slot × ad_room_boundary` JOIN produces one BOM anchor row per (building, room, assembly)
- `RelationalResolver` detects anchor rows (`family_ref ∈ ad_bom.bom_id`) and calls `FurnitureBOMResolver.resolveForRoom()`
- `FurnitureBOMResolver` expands each anchor to N child placements using `ad_bom_child_param` offsets
- Individual Revit-extracted furniture leaf rows (FURN_*) deactivated and replaced by BOM anchors

**Still outstanding (not yet wired):**
- OPEN_PLAN rooms: no slot dispatch — furniture added via explicit `ad_element_rule` rows only
- MEP + structural ABSOLUTE rows in DX (261 rows: IfcFlowSegment/Fitting, windows, beams) — still Revit-extracted flat coords
- Full DAG walk `UNIT_DUPLEX_STD → BEDROOM_STD × N → BED_SET` — the unit-level BOM layer is not yet implemented
- CRD/ProvenElement gate remains a future phase

---

## 5. Level 5 — C_BOM_Line (instance): Assembly Child Params (`ad_bom_child_param`)

### What BOM child params are

`ad_bom_child_param` carries assembly-relative offsets for each child within a BOM assembly. These are NOT absolute positions — they are relative to the parent assembly's anchor point.

```sql
bom_child_id  INT   -- FK to ad_bom_child (the assembly member)
param_key     TEXT  -- "dx_mm" | "dy_mm" | "dz_mm" | "rotation_rule" | ...
param_value   TEXT  -- value as string (parsed by FurnitureBOMResolver)
```

### Example: BED_SET assembly

```
BED_SET assembly anchor: back-center of bed (touches BACK wall)
  ├── FURN_BED_DOUBLE  dx=0, dy=0, dz=0           (anchor = the bed)
  ├── FURN_SIDE_TABLE  dx=+0.98m, dy=0, dz=0      (right side table)
  ├── FURN_SIDE_TABLE  dx=-0.98m, dy=0, dz=0      (left side table)
  └── FURN_WARDROBE    dx=+1.2m, dy=0, dz=0  rotation_rule=PARALLEL_TO_WALL
```

`rotation_rule` values:
- Literal radians: `"3.14159"` — applied directly
- Semantic: `"FACE_INTO_ROOM"` | `"FACE_AWAY_FROM_WALL"` | `"PARALLEL_TO_WALL"`
- Resolved by `FixturePlacer.resolveRotationRule()`

---

## 6. How SH/DX Currently Fulfil Their Content (The Honest Picture)

### What the SH and DX DSLs actually do

SH DSL:
```
BUILDING "Ifc4_SampleHouse" type:SINGLE_UNIT profile:"UK_Residential" { ... }
```

DX DSL:
```
BUILDING "Ifc2x3_Duplex" type:MULTI_UNIT profile:"US_Residential" {
    UNIT "A" type:DUPLEX entry:DIRECT
    UNIT "B" type:DUPLEX entry:DIRECT
}
```

**Neither DSL selects BOM assemblies.** They are minimal labels. The type (`SINGLE_UNIT`, `MULTI_UNIT`, `DUPLEX`) tells the BuildingCompiler what structural pattern to use. After Phase BOM-1: SH=63 elements, DX=1197 elements — furniture content now comes from BOM expansion, structural/MEP/windows still from explicit `ad_element_rule`.

### What ad_element_rule contains for SH/DX

SH and DX element rules use **verbatim Revit family refs**, not catalog product IDs:
```
family_ref = "M_Single-Flush:0762 x 2032mm:0762 x 2032mm"   -- DX door
family_ref = "M_W-Wide Flange:W410X60:W410X60"               -- DX structural beam
family_ref = "M_Single-Flush:1250mm x 2010mm:1250mm x 2010mm" -- DX large door
```

These are extracted verbatim from the Revit IFC. They are NOT in `ad_product_dim`. The conn_points orientation system therefore does not fire for SH/DX furniture.

### Position quality (after Phase BOM-1, 2026-02-21)

| Building | ABSOLUTE rows (active) | Relational rows | Furniture ABSOLUTE |
|---|---|---|---|
| SH (63) | 3 (5%) — windows only | 46 (73%) + 14 BOM anchors | **0** ✅ |
| DX (1197) | 261 (22%) — MEP/struct/windows | 795 (66%) + 27 BOM anchors | **0** ✅ |

**SH**: 3 IfcWindow ABSOLUTE rows remain (Revit-extracted corner windows, correct world coords).
**DX**: 261 ABSOLUTE rows remain — all MEP (IfcFlowSegment×151, IfcFlowFitting×70), windows (×22), structural (railing×4, beam×4, stair×2). Furniture layer is fully converted to BOM anchors.

ABSOLUTE rows still store verbatim extracted centroids — they replicate exact positions but have no declared relationship to room boundaries. When room geometry shifts, these items drift. MEP/structural ABSOLUTE migration is a future workstream.

### The "BOM layer-by-layer" question

**Is the full DAG walk `UNIT_DUPLEX_STD → BEDROOM_STD × N → BED_SET` implemented?**

**Partially.** Phase BOM-1 (2026-02-21) implemented the bottom two layers:
- `ad_room_slot × ad_room_boundary` → BOM anchor rows (the slot dispatch layer)
- BOM anchor → `FurnitureBOMResolver` → `ad_bom_child_param` offsets → N children (the BOM expansion layer)

**Not yet implemented:** the top layer — `UNIT_DUPLEX_STD → BEDROOM_STD × 2 + BATHROOM_STD × 2 + KITCHEN_STD + LIVING_STD`. For DX, `type:DUPLEX` assembles walls + openings from DSL room declarations, but unit-level BOM assembly (UNIT_DUPLEX_STD DAG) does not yet drive room content — room types still declared explicitly in DSL. That full DAG walk exists in PREFAB_ARCHITECTURE.md but is not yet implemented.

**What "layer-by-layer" means in the intended architecture:**
```
DSL:   UNIT "A" type:DUPLEX
         ↓ (catalog lookup)
       UNIT_DUPLEX_STD assembly
         ↓ (BOM expansion — ad_bom_child)
       BEDROOM_STD × 2 + BATHROOM_STD × 2 + KITCHEN_STD + LIVING_STD
         ↓ (slot dispatch — ad_room_slot)
       BED_SET + WARDROBE_SET + CEILING_MEP (per bedroom)
         ↓ (BOM child params — ad_bom_child_param)
       bed + side_table (dx=±0.98) + wardrobe (PARALLEL_TO_WALL)
```

This is the target. After Phase BOM-1, SH/DX now use the bottom two layers (slot dispatch + BOM child params) for furniture. The top layer (unit-level BOM assembly) still bypasses to explicit DSL room declarations.

---

## 7. TB-LKTN as CitizenHouse — Current State vs. Intended

### Current state (confirmed by DB query)

TB-LKTN has 72 `ad_element_rule` rows. Furniture uses catalog IDs (correct):
```
IfcFurnishingElement_1  family_ref=FURN_SOFA       ROOM common  fractionX=0.5  fractionY=0.15
IfcFurnishingElement_2  family_ref=FURN_TV_UNIT    ROOM common  fractionX=0.5  fractionY=0.05
IfcFurnishingElement_3  family_ref=DINING_TABLE    ROOM common  fractionX=0.5  fractionY=0.65
IfcFurnishingElement_5-8  (4 dining chairs)       ...
IfcFurnishingElement_9  family_ref=FURN_BED_DOUBLE ROOM bilik_utama  fractionY=0.85
...
```

These are explicit rows — the compiler doesn't dispatch through slots. Result: 102 elements, 58/58 tests green.

### What the intended design looks like

TB-LKTN is a "CitizenHouse" (Government affordable terrace, ~100m²). Its rooms are smaller than a US Duplex:

| Room | TB-LKTN (m²) | DX equivalent | Qualifies for |
|---|---|---|---|
| bilik_utama | 9.61 | ~15+ | BED_SET (not BED_SET_MASTER — below 12m²) |
| bilik_2 / bilik_3 | 9.61 | ~12 | BED_SET only |
| common | 22.94 | ~18 living | LIVING_SET + DINING_SET + KITCHEN_CABINET_SET |
| tandas | 2.08 | ~5 | TOILET_BLOCK_FIXTURES + BASIN |
| bilik_mandi | 2.08 | ~5 | TOILET_BLOCK_FIXTURES + EXHAUST |

The small bedrooms correctly select BED_SET over BED_SET_MASTER. The common room qualifies for all three COMMON slots — this is correct: Malaysian open-plan terrace has combined living/dining/kitchen.

### What needs to happen for proper slot dispatch

1. `MetadataCompiler` must detect room type from DSL keyword → look up `ad_room_slot` for matching room_type
2. For each slot (ordered by priority), check area threshold → if qualifies, dispatch to `FurnitureBOMResolver`
3. `FurnitureBOMResolver` expands assembly_id → reads `ad_bom_child` + `ad_bom_child_param` → places elements relative to room anchor
4. Explicit `ad_element_rule` furniture rows for TB-LKTN would be replaced by slot-dispatched rows (or become the slot anchor rules)
5. OPEN_PLAN zone split (LIVING/DINING/KITCHEN) maps to COMMON slots

**This is NOT yet implemented.** Current explicit `ad_element_rule` furniture rows are the working substitute. They produce correct output but bypass the assembly hierarchy.

### What IS implemented and working

- Three-table authority (ad_product_dim + ad_bom_child_param + ad_element_rule) — schema correct ✓
- RelationalResolver computes positions from room boundaries + fractions ✓
- conn_points orientation resolution (when family_ref is a catalog ID) ✓
- Area-gated slot data in ad_room_slot ✓
- FurnitureBOMResolver exists and reads ad_bom_child_param ✓ (but bypassed for current buildings)

---

## 8. Adding a New Building — End to End

### Checklist

**Step 1: Add building to registry**
```sql
INSERT INTO ad_building_registry (building_id, building_name, building_type,
    provenance, dsl_content, output_db_path, is_active, seq_no)
VALUES ('MY_HOUSE', 'My House Type D', 'RESIDENTIAL', 'GENERATIVE',
    '...dsl...', 'DAGCompiler/lib/output/MY_HOUSE.db', 1, 6);
```

**Step 2: Add room boundaries**
```sql
INSERT INTO ad_room_boundary
    (building_type, room_name, storey, room_type, min_x_mm, max_x_mm, min_y_mm, max_y_mm)
VALUES
    ('MY_HOUSE', 'master_bedroom', 'Ground Floor', 'BEDROOM', 0, 3500, 0, 4000),
    ('MY_HOUSE', 'bathroom_1',     'Ground Floor', 'BATHROOM', 3500, 5000, 0, 2000);
```
Room boundary min/max comes from the GRID axes (spacing × cumulative sums).

**Step 3: Add wall faces** (for door/window placement on walls)
```sql
INSERT INTO ad_wall_face (building_type, room_name, storey, face, wall_type_id, is_exterior)
VALUES
    ('MY_HOUSE', 'master_bedroom', 'Ground Floor', 'SOUTH', 'EXT_WALL', 1),
    ('MY_HOUSE', 'master_bedroom', 'Ground Floor', 'NORTH', 'INT_WALL', 0);
    -- NORTH, SOUTH, EAST, WEST — one row per used face
```

**Step 4: Add element rules** (one per element)
```sql
-- Structural walls (via BOUNDARY rule)
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, width_mm, height_extent_mm, depth_mm, orientation, is_active)
VALUES ('MY_HOUSE', 'Ground Floor', 'IfcPlate_1', 'IfcPlate', 'ARC',
    'ROOM', 'master_bedroom', 'BOUNDARY', 150.0, 3000.0, 150.0, 'NS', 1);

-- Door
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, family_ref, width_mm, height_extent_mm, depth_mm, orientation, is_active)
VALUES ('MY_HOUSE', 'Ground Floor', 'IfcDoor_1', 'IfcDoor', 'ARC',
    'WALL', 'WALL_master_bedroom_SOUTH', 'FRACTION', 0.5, 0.0,
    0.0, 'DOOR_D1', 900.0, 2100.0, 45.0, 'ALONG_HOST', 1);

-- Toilet
INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, family_ref, width_mm, height_extent_mm, depth_mm, orientation, is_active)
VALUES ('MY_HOUSE', 'Ground Floor', 'TOILET_bathroom_1', 'IfcSanitaryTerminal', 'MEP',
    'WALL', 'WALL_bathroom_1_NORTH', 'FRACTION', 0.5, 0.0,
    0.0, 'FIXTURE_TOILET', 400.0, 400.0, 700.0, NULL, 1);
    -- orientation NULL → resolved from conn_points ("BACK" face → FACE_INTO_ROOM)
```

**Step 5: Run tests — watch the new building appear**
```bash
mvn test -pl DAGCompiler
```
Expected output: `PIPELINE COMPLETE: My House Type D — N elements`

**Step 6: Set expected_elements after first run**
```sql
UPDATE ad_building_registry SET expected_elements = N WHERE building_id = 'MY_HOUSE';
```

---

## 9. What Cannot Yet Be Done (Honest Gap List)

| Capability | Status | Needed for |
|---|---|---|
| Slot dispatch from room type | ✗ Not wired | Auto-populate furniture from room type |
| BOM expansion (DAG walk) | ✗ Not wired | UNIT_DUPLEX → room assemblies → elements |
| Area-gated slot selection | ✗ Not wired | BED_SET vs BED_SET_MASTER by room size |
| Profile-filtered slots | ✗ Not wired | Malaysian_Institutional canteen vs DINING_SET |
| clear_front enforcement | ✗ Not wired | Clearance-aware placement, door swing |
| CRD (Construction Rule Dictionary) | ✗ Not built | Math-proved placement at insert time |
| ProvenElement gate | ✗ Not built | Proof-before-write, like PO.save() |
| OPEN_PLAN zone split | ✗ Explicit only | COMMON room → LIVING+DINING+KITCHEN zones |
| conn_points for SH/DX furniture | ✗ Revit refs | Orientation resolution for extracted furniture |

**The path to fix all of these is documented in `docs/LAST_MILE_PROBLEM.md` §0.1 (Ordered Fix Plan).**

The six-step fix plan starts with `family_ref` mandatory gate (MetadataValidator) and ends with BOM anchor migration replacing ABSOLUTE rows with catalog-based rules. Do not skip steps — each step gates the next.

---

## 10. Three-Table Authority (Canonical Reference)

```
ad_product_dim   — intrinsic geometry ONLY (width, depth, height)
                   NEVER position, NEVER rotation
                   family_ref in ad_element_rule must point here (catalog ID)

ad_bom_child_param — assembly-relative offset + rotation ONLY (dx, dy, dz, rotation_rule)
                     NEVER absolute coords, NEVER product dims
                     Values in mm; rotation_rule = literal radians or semantic string

ad_element_rule  — room-relative placement ONLY (host_ref, position_rule, fraction)
                   NEVER product dims (copy them in for resolution only)
                   height_extent_mm MUST be non-zero or P01/P03 CRITICAL fail
```

**Score is arbiter.** Before and after every change: `mvn test -pl DAGCompiler`. If the count drops, revert.

---

*For spatial verification: `python3 DAGCompiler/python/spatial_checker.py ...`*
*For phase history: `PROGRESS.md`*
*For placement proof thresholds: `PlacementProver.isCritical()` — P01-P03, P16-P17, P22 gate; rest advisory*
