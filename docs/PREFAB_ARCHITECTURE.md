# Prefab Assembly Architecture

*Supersedes: runtime spatial resolution for standard buildings (FloorPlateBOMResolver fill_remaining path)*
*Extends: ARCHITECTURE.md §3.6 Tower BOM, DSL_AS_CATALOG_SELECTOR.md*

## Principle

Architecture works bottom-up from standards. A UBBL bedroom is 3.1m × 3.1m. Two bedrooms + two bathrooms + kitchen + living = a known-size unit. Two units + core = a known-size floor. The compiler does not compute layout — it selects pre-computed assemblies.

```
Component (existing)  →  Room Assembly  →  Unit Assembly  →  Floor Assembly  →  Building
  Door_900x2100           BEDROOM_STD       UNIT_2BR_STD      FLOOR_TOWER_2U     DSL selects
  Light_Downlight         3.1m × 3.1m       8m × 12m          12m × 34m          and stacks
```

Each level is a BOM of the level below. Resolution is DAG expansion + coordinate translation. No spatial solving.

## DSL → DAG → Output

```
DSL:  BUILDING type:CONDO_MID
        │
DAG:  FLOOR_TOWER_2U × 16 + GROUND_LOBBY + ROOF_TANK
        │
      UNIT_2BR_STD × 2 + CORE_STD + CORRIDOR_STD
        │
      BEDROOM_STD × 2 + BATHROOM_STD × 2 + KITCHEN_STD + LIVING_STD
        │
      slab + walls + door + window + light + sprinkler  (absolute positions)
```

## Assembly Levels

### Level 0: Components (exists)
Table: `component_definitions`. Individual IFC elements with LOD400 geometry.

### Level 1: Room Assemblies
Standard rooms with known dimensions, component set, and interfaces.

```
BEDROOM_STD  3100 × 3100 × 3000mm
  walls: 4 × Internal_150mm
  door:  S wall, offset 1.1m
  window: N wall, offset 0.95m
  light: center ceiling
  sprinkler: center ceiling
  interfaces: S=entry, N=exterior
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
  interfaces: S=entry(D1), N=exterior, W=exterior, E=party_wall
```

### Level 3: Floor Assemblies
Standard floors: units + core + circulation, known total dimensions.

```
FLOOR_TOWER_2U  12000 × 34000mm
  UNIT_2BR_STD   at (0,0)      orient=NONE
  CORE_STD       at (0,12000)  12000 × 8500
  CORRIDOR_STD   at (0,20500)  12000 × 1500
  UNIT_2BR_STD   at (0,22000)  orient=MIRROR_Y
  interfaces: vertical=stair+lift, perimeter=exterior
```

### Level 4: Building (DSL)
DSL selects floor assemblies. Compiler stacks them at storey heights.

## Database Schema

Three tables, following iDempiere M_Product / M_BOM pattern:

```sql
-- The catalog: what assemblies exist
prefab_product (
    prefab_id     TEXT PRIMARY KEY,    -- 'BEDROOM_STD'
    prefab_level  INTEGER NOT NULL,    -- 1=room, 2=unit, 3=floor
    space_type    TEXT NOT NULL,        -- BEDROOM, UNIT_2BR, FLOOR_TOWER
    width_mm      INTEGER NOT NULL,
    depth_mm      INTEGER NOT NULL,
    height_mm     INTEGER NOT NULL,
    jurisdiction  TEXT DEFAULT 'UBBL'
)

-- How assemblies compose: parent → child with placement
prefab_bom (
    parent_id     TEXT REFERENCES prefab_product,
    child_id      TEXT REFERENCES prefab_product,
    sequence      INTEGER,
    offset_x_mm   INTEGER DEFAULT 0,
    offset_y_mm   INTEGER DEFAULT 0,
    offset_z_mm   INTEGER DEFAULT 0,
    orientation   TEXT DEFAULT 'NONE',  -- MIRROR_X, MIRROR_Y, ROTATE_90...
    role          TEXT                   -- ENTRY, EXTERIOR, PARTY, STRUCTURAL
)

-- Connection points: how assemblies interface with neighbours
prefab_interface (
    prefab_id      TEXT REFERENCES prefab_product,
    interface_type TEXT NOT NULL,        -- DOOR, WINDOW, PARTY_WALL, SHAFT
    wall           TEXT NOT NULL,        -- N, S, E, W
    offset_mm      INTEGER DEFAULT 0,
    width_mm       INTEGER,
    height_mm      INTEGER,
    component_id   TEXT                  -- FK to component_definitions
)
```

## Multi-Dimensional Selection

Like iDempiere's C_BPartner × M_Product × M_Project:

| Dimension | Values | Selects |
|-----------|--------|---------|
| Space type | RESIDENTIAL, OFFICE, CORE | Which assembly catalog |
| Size | 8×12m, 6×8.5m | Which size variant |
| Jurisdiction | UBBL, IBC | Which code compliance |

Same 8m × 12m envelope → RESIDENTIAL gets bedrooms + bathrooms. OFFICE gets workstations + meeting rooms. Different assembly, same selection mechanism.

## Coexistence

The prefab path is **additive**:
- `floor_prefab:FLOOR_TOWER_2U` → new prefab DAG expansion path
- `floor_bom:TYPICAL_CONDO_FLOOR` → existing runtime spatial resolution
- Buildings without either → legacy explicit bounds

No existing builds break. New standard buildings use prefabs.

## POC Scope

All off-the-shelf defaults. No variants. No tailoring.

| Assembly | Dimensions | Contents |
|----------|-----------|----------|
| BEDROOM_STD | 3.1 × 3.1m | walls + door + window + light + sprinkler |
| BATHROOM_STD | 1.5 × 2.4m | walls + door + WC + basin + fan |
| KITCHEN_STD | 3.0 × 3.5m | walls + counter + sink + light |
| LIVING_STD | 5.0 × 5.0m | walls + windows + lights + sprinkler |
| CORE_STD | 6.0 × 8.5m | stair + lift + lobby + shaft |
| CORRIDOR_STD | 1.8 × variable | walls + lights + sprinklers |
| UNIT_2BR_STD | 8.0 × 12.0m | 2 bed + 2 bath + kitchen + living |
| FLOOR_TOWER_2U | 12.0 × 34.0m | 2 units + core + corridor |

Mirror/rotation applied at placement time — one assembly, four orientations. Variants come later as additional catalog entries.

## Supersedes

| Old Approach | New Approach |
|---|---|
| `ad_unit_type_room` fractional bounds [0..1] | `prefab_bom` absolute mm offsets |
| `FloorPlateBOMResolver.fill_remaining` runtime | `prefab_product` pre-computed floor layout |
| `UnitInteriorResolver` fractional scaling | DAG expansion with absolute positions |
| Runtime "what fits?" computation | Catalog "select what you need" lookup |

Old approaches remain available for non-standard buildings. Prefab path is preferred for standard buildings.
