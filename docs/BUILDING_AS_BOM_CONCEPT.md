# Building as BOM (Bill of Materials) Concept

> **SUPERSEDED** by `docs/ARCHITECTURE.md` (v3.0, 2026-02-08).
> BOM pattern is now documented in Section 3 of the new document.
> Retained for detailed BOM schema reference and Phase 85 trial data.

**Status:** ~~POC Proven~~ Integrated into ARCHITECTURE.md
**Date:** 2026-02-04 (POC) → 2026-02-06 (Phase 85)
**Inspired By:** iDempiere M_BOM/M_Product hierarchy

---

## Core Insight

**BOM metadata IS DSL metadata.** The BOM tree doesn't just list what parts go where — it carries all placement parameters, defaults, and constraints. The DSL becomes a BOM selector. Java code stabilizes as a resolver framework.

A building is a hierarchical BOM, like a train:

```
BUILDING (Product)
├── HEAD (basement, ground floor)
├── STANDARD (typical floors × N)
└── TAIL (penthouse, roof)
```

Each floor is also a BOM:

```
FLOOR (Sub-assembly)
├── ROOMS (Units)
│   ├── Living
│   ├── Kitchen
│   └── Bedrooms
└── Each room contains PRODUCTS
    ├── Fixtures
    ├── Electrical
    └── MEP
```

---

## The Pattern

### 1. MANIFEST (User Intent)

Minimal declaration - what the user wants:

```bim
BUILDING template:"CONDO_MID" floors:18 {
    rooms: [living, kitchen, 2×bedroom, bathroom]
}
```

### 2. BOM EXPLOSION (System Expands)

System looks up `ad_building_template` → expands HEAD/STANDARD/TAIL:

```
CONDO_MID (18 floors):
├── B2, B1 (HEAD - basement parking)
├── Ground (HEAD - lobby)
├── Level_1..12 (STANDARD - typical condo)
├── Amenity (TAIL)
├── Penthouse (TAIL)
└── Roof (TAIL)
```

### 3. PRODUCT FITTING (LEGO Pattern)

Each space gets products from AD:

```
BATHROOM (3m × 2.5m):
├── TOILET at (2.5, 0.4) - from ad_product_dim
├── SINK at (1.5, 2.3) - knows its clearances
├── LIGHT at (1.5, 1.25, ceiling) - qty_per_area
└── SPRINKLER at (1.5, 1.25) - from ad_fp_coverage
```

### 4. LAYOUT RESOLUTION (Second Pass)

Resolver cleans up overlaps:
- Merge shared walls between rooms
- Resolve product collisions
- Snap to grid

### 5. GENERATED DSL (Amendable Output)

Full detailed DSL user can review/modify before compile:

```bim
BUILDING "MyProject" {
    STOREY "Ground" {
        ROOM "living" bounds:(0,0)-(4,4) {
            DOOR "D1" at:(2,0) on:SOUTH
            OUTLET "O1" at:(0,2,0.3) on:WEST
            ...
        }
    }
}
```

---

## AD Tables (Metadata Layer)

### Building BOM Tables

| Table | Purpose | Count |
|-------|---------|-------|
| `ad_building_template` | Building types (CONDO, OFFICE) | 8 |
| `ad_floor_type` | Floor definitions | 12 |
| `ad_building_bom` | Template → floor mapping | N |
| `ad_unit_type` | Unit types (STUDIO, 1BR) | 7 |

### Product Dimension Tables

| Table | Purpose | Count |
|-------|---------|-------|
| `ad_product_dim` | Product sizes + clearances | 16 |
| `ad_space_dim` | Space requirements | 6 |
| `ad_element_mep` | MEP elements | 12 |

### Space Type Tables

| Table | Purpose | Count |
|-------|---------|-------|
| `ad_space_type` | Space definitions | 26 |
| `ad_space_type_mep` | MEP per space | 22 |

---

## Java Classes

| Class | Purpose |
|-------|---------|
| `BuildingBOM.java` | Expand template → floors |
| `AutoFitter.java` | Fit products in spaces |
| `PreCompiler.java` | Manifest → full DSL |
| `LayoutResolver.java` | Resolve overlaps |
| `MEPAD.java` | Query MEP metadata |

---

## Flow Diagram

```
┌─────────────┐
│  MANIFEST   │ ← User: "18 floor condo, 2BR units"
│  (5 lines)  │
└──────┬──────┘
       │
       ▼
┌─────────────┐      ┌─────────────┐
│ PreCompiler │◄────►│  AD Tables  │
│             │      │  (metadata) │
└──────┬──────┘      └─────────────┘
       │
       ▼
┌─────────────┐
│   Rough     │ ← Rooms placed, products fitted
│   Layout    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Resolver   │ ← Merge walls, fix collisions
│             │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ FULL DSL    │ ← 80+ lines, amendable
│ (output)    │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Compiler   │ ← Normal compilation
│             │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ OUTPUT.db   │ ← BIM database
└─────────────┘
```

---

## Key Principles

### 1. BOM Metadata IS DSL Metadata (Phase 85)

Every placement parameter belongs in the BOM, not in Java code:
- **DSL selects** what to place ("sprinklers in this room")
- **BOM parameterises** how to place it (`z_offset=0.20`, `spacing=4.3`)
- **Java resolves** the final coordinates (`resolveZ()`)

Change a parameter in the database → recompile → all instances update. No code change.

### 2. Products Know Their Dimensions

Every product in AD carries:
- `width`, `depth`, `height`
- `clear_front`, `clear_side` (clearances)
- `fits_in` (what spaces it belongs to)
- `qty_per_area` or `qty_per_room`

No constraint solving needed - just lookup.

### 3. Spaces Know Their Requirements

Every space in AD carries:
- `min_width`, `min_depth`, `min_area`
- `required_products` (must have)
- `optional_products` (if space allows)

Fitting is deterministic.

### 4. Rough Cut First, Refine Later

- First pass: Place rooms roughly
- Second pass: Merge shared walls
- Third pass: Fix product collisions
- Final: Snap to grid

### 5. Rooms Share Walls, Not Own Them

A room doesn't own 4 walls. It:
- References shared walls
- Contributes to wall openings
- Wall is created once, shared by adjacent rooms

### 6. Middle Room = One Wall

A room in the middle of a floor:
- Has one "facing" wall (with door)
- Other walls defined by adjacent rooms
- Back window optional (if exterior wall exists)

---

## Prefab Examples

| Prefab | Size | Rooms | Products |
|--------|------|-------|----------|
| UNIT_STUDIO | 35m² | combined + bath | ~15 |
| UNIT_1BR_A | 55m² | living + kitchen + BR + bath | ~25 |
| UNIT_2BR_A | 85m² | living + kitchen + 2BR + 2bath | ~40 |
| MODULE_BATHROOM_POD | 2.4×2m | complete prefab bath | ~12 |

---

## 80/20 Pattern

**20% Input → 80% Output**

- 5 lines of manifest
- System generates 80+ lines of DSL
- All from AD metadata
- Add new building type = SQL INSERT

**Add New Element Type:**
```sql
INSERT INTO ad_product_dim (product_id, width, depth, height, ...)
VALUES ('NEW_PRODUCT', 0.5, 0.3, 0.8, ...);
-- No Java code change needed
```

**Add New Building Template:**
```sql
INSERT INTO ad_building_template (template_id, ...)
VALUES ('HOTEL_LUXURY', ...);
INSERT INTO ad_building_bom (template_id, floor_type_id, position, ...)
VALUES ('HOTEL_LUXURY', 'BASEMENT_PARKING', 'HEAD', 1, 3, ...);
-- System can now generate HOTEL_LUXURY buildings
```

---

---

## Phase 81: Hierarchical BOM Assemblies (POC)

### Why Assemblies?

**The Problem:** Elements placed individually can misalign:
- Door placed at (5.0, 0, 0) on wall facing NORTH
- Wall rotates to face EAST → door doesn't follow

**The Solution:** Parent assembly contains children with local offsets:
- Wall Panel assembly contains door assembly
- Rotate wall panel → door rotates with it (inherits transform)
- Change door type in library → recompile → all instances updated

### Assembly Tree Structure

```
WALL_PANEL_WITH_DOOR_001
├── CLADDING_001 (leaf: IfcPlate)
├── FRAME_ASSEMBLY_001 (sub-assembly)
│   ├── RAIL_BOTTOM
│   ├── RAIL_TOP
│   ├── STUD_LEFT
│   ├── STUD_RIGHT
│   └── HEADER (over door)
└── DOOR_ASSEMBLY_001 (sub-assembly)
    ├── DOOR_FRAME
    ├── DOOR_LEAF
    └── HARDWARE_SET_001 (sub-sub-assembly)
        ├── HINGES × 2
        ├── HANDLE
        └── LOCK
```

### Database Schema

```sql
-- Assembly definitions
CREATE TABLE element_assemblies (
    assembly_guid TEXT PRIMARY KEY,
    assembly_type TEXT,        -- WALL_PANEL_WITH_DOOR, FLOOR, UNIT
    ifc_class TEXT,            -- IfcElementAssembly
    name TEXT,
    total_width REAL,
    total_depth REAL,
    total_height REAL,
    storey TEXT
);

-- Parent → child links with local offsets
CREATE TABLE assembly_components (
    assembly_guid TEXT,        -- Parent
    component_guid TEXT,       -- Child (element or sub-assembly)
    role TEXT,                 -- FRAME, CLADDING, OPENING, HARDWARE
    local_x REAL,              -- Offset from parent origin
    local_y REAL,
    local_z REAL,
    sequence INTEGER           -- Display order in outliner
);
```

### Java Classes

| Class | Purpose |
|-------|---------|
| `BOMAssembly.java` | Tree builder, recursive hierarchy |
| `BOMBuilder.java` | Create wall panels with nested doors |
| `FloorAssemblyBuilder.java` | Complete floors from templates |
| `BOMTypeSystem.java` | Type/instance pattern (IFC compatible) |
| `BOMVariantSystem.java` | ERP-style inheritance with overrides |

### Key Benefit: Library-Driven Updates

**Change in library → recompile → all instances updated:**

```
Before: Opening rotation wrong in compiled output
  ↓
Fix: Update forward_axis in component_library.db
  ↓
Recompile: All door instances get correct rotation
  ↓
Result: No Java code changes, no manual edits
```

This is the core principle: **configuration over code**.

---

## Type/Instance Pattern (BOMTypeSystem)

Following IFC patterns for Bonsai compatibility:

### Pattern

```
TYPE (template in library)
  └── INSTANCE (references type, inherits properties)
        └── Can override specific properties
```

### Database Schema

```sql
-- Type definitions (templates)
CREATE TABLE bom_types (
    type_id TEXT PRIMARY KEY,
    type_name TEXT,
    category TEXT,             -- DOOR, WINDOW, WALL_PANEL, UNIT
    base_width REAL,
    base_height REAL,
    base_depth REAL,
    door_type TEXT,            -- D1, D2, FD1
    window_type TEXT,          -- W1, W2
    cladding_material TEXT
);

-- Type components (what's in the template)
CREATE TABLE bom_type_components (
    type_id TEXT,
    component_type TEXT,       -- FRAME, CLADDING, DOOR
    component_ref TEXT,        -- Reference to another type
    local_x REAL, local_y REAL, local_z REAL,
    sequence INTEGER
);

-- Instances (placed in building)
CREATE TABLE bom_instances (
    instance_id TEXT PRIMARY KEY,
    type_id TEXT,              -- References bom_types
    storey TEXT,
    world_x REAL, world_y REAL, world_z REAL,
    rotation REAL
);

-- Instance overrides (selective changes)
CREATE TABLE bom_instance_overrides (
    instance_id TEXT,
    property_name TEXT,        -- door_type, width, etc.
    override_value TEXT
);
```

### IFC Mapping

| BOM Pattern | IFC Pattern |
|-------------|-------------|
| `bom_types` | `IfcTypeObject` |
| `bom_instances` | `IfcElement` |
| Type → Instance | `IfcRelDefinesByType` |
| Assembly children | `IfcRelAggregates` |

### Refresh Pattern

```java
// Change type definition
updateType("WALL_PANEL_STD", "door_type", "FD1");

// Refresh all instances of this type
for (Instance inst : getInstancesOfType("WALL_PANEL_STD")) {
    if (!inst.hasOverride("door_type")) {
        inst.setDoorType("FD1");  // Inherits from type
    }
}
```

---

## ERP-Style Variant System (BOMVariantSystem)

Inspired by iDempiere M_BOM / Libero Manufacturing BOM:

### Concept

```
BASE_VARIANT (standard configuration)
  └── DERIVED_VARIANT (inherits, can override)
        └── INSTANCE (inherits from variant chain)
```

### DSL Examples

```bim
# Standard car (all defaults)
CAR "sedan_1"

# Car with wheel override
CAR "sedan_2" {
    wheel [size:22"]
}

# Car with nested overrides
CAR "luxury_1" {
    wheel [size:22", brand:"BBS"]
    interior [leather:TRUE, color:"tan"]
}
```

### Database Schema

```sql
-- Variant definitions
CREATE TABLE bom_variants (
    variant_id TEXT PRIMARY KEY,
    base_variant_id TEXT,      -- Parent variant (for inheritance)
    variant_name TEXT,
    category TEXT
);

-- Selective overrides per variant
CREATE TABLE bom_variant_overrides (
    variant_id TEXT,
    path TEXT,                 -- "wheel.size" or "unit_A.living.wall_north"
    override_type TEXT,        -- COMPONENT, DIMENSION, PROPERTY
    override_value TEXT
);
```

### Resolution Algorithm

```java
// Build effective configuration by walking inheritance chain
EffectiveConfig resolve(String variantId) {
    Variant variant = getVariant(variantId);

    // Start with base (if exists)
    EffectiveConfig config = variant.baseVariantId != null
        ? resolve(variant.baseVariantId)  // Recursive
        : new EffectiveConfig();

    // Apply this variant's overrides
    for (Override override : getOverrides(variantId)) {
        config.apply(override.path, override.value);
    }

    return config;
}
```

### Path-Based Overrides

Override at any level of hierarchy:

| Path | Effect |
|------|--------|
| `door_type` | All doors in variant |
| `unit_A.door_type` | Doors in unit A only |
| `unit_A.living.wall_north.door_type` | Specific door |

---

## Floor Assembly Builder (Complete Floors)

### Pattern

```
FLOOR_TEMPLATE
├── CORRIDOR
│   ├── WALLS (with embedded doors)
│   ├── LIGHTS (per spacing rule)
│   └── SPRINKLERS (per coverage rule)
└── UNITS
    ├── UNIT_W1
    │   ├── LIVING (wall+window assembly)
    │   ├── KITCHEN (wall+fixtures)
    │   └── BEDROOM (wall+closet)
    └── UNIT_W2
        └── ...
```

### Usage

```java
// Create complete floor from template
FloorAssemblyBuilder builder = new FloorAssemblyBuilder(conn);
builder.buildFloorFromTemplate(
    "Level_5",           // Storey name
    "TYPICAL_FLOOR",     // Template ID
    new String[]{"UNIT_W1", "UNIT_W2", "UNIT_E1", "UNIT_E2"}
);

// Result: 150+ elements all properly nested and positioned
```

### Benefits

| Feature | Without Assemblies | With Assemblies |
|---------|-------------------|-----------------|
| Outliner view | Flat list (500 items) | Tree (5 floors × 4 units) |
| Move unit | Select 30 elements | Drag 1 assembly node |
| Change door type | Edit 20 door entries | Change 1 type, recompile |
| Cost rollup | Sum individual elements | Walk tree, aggregate |

---

## Practical Workflow

### 1. Define Types in Library

```sql
-- In component_library.db
INSERT INTO bom_types (type_id, type_name, door_type, ...)
VALUES ('WALL_PANEL_STD', 'Standard Wall Panel', 'D2', ...);
```

### 2. Write DSL with Types

```bim
UNIT "W1" type:UNIT_2BR_A {
    wall_north type:WALL_PANEL_STD
}
```

### 3. Compile → Instances Created

```
UNIT_W1_Ground
├── WALL_NORTH_W1_Ground [type:WALL_PANEL_STD]
│   └── DOOR_W1_NORTH_Ground [type:D2]
└── ...
```

### 4. Need Change? Update Library

```sql
-- Change all standard panels to fire doors
UPDATE bom_types SET door_type = 'FD1' WHERE type_id = 'WALL_PANEL_STD';
```

### 5. Recompile → All Instances Updated

No code changes. No manual edits. Configuration drives output.

---

## Phase 85: BOM Metadata = DSL Metadata (Proven)

### The Principle

Every placement parameter in the compiler was once a hardcoded constant in Java. Phase 85 proves they belong in the BOM tree instead.

The architecture shift:

```
BEFORE (Phase 80):
  Java hardcodes:  ceilingZ = baseZ + height - 0.05
  Bug:             Sprinkler ends up 100mm INSIDE the slab
  To fix:          Change Java, recompile, redeploy

AFTER (Phase 85):
  BOM metadata:    z_rule=BELOW_SLAB, z_offset=0.20
  Java resolves:   sprinklerZ = resolveZ(baseZ, height, slabThickness)
  To fix:          UPDATE ad_bom_child_param SET param_value='0.25' WHERE ...
  Result:          Sprinklers move. No Java change.
```

**DSL selects. BOM parameterises. Java resolves.** That's the full separation.

### Why This Matters

The sprinkler-in-slab bug existed because placement knowledge was split between code and intent:

| What | Where it was | Where it should be |
|------|-------------|-------------------|
| "Place sprinklers" | DSL intent | DSL intent (correct) |
| "200mm below slab" | Hardcoded in Java | BOM child param `z_offset=0.20` |
| "Slab is 150mm" | BIMConstants.java | `ad_floor_type.slab_thickness` |
| "Pendant type" | String literal | BOM child param `head_type=PENDANT` |
| "4.3m spacing" | FireProtectionResolver | BOM child param `spacing=4.3` |

When placement knowledge lives in Java, every building variation requires a code change. When it lives in the BOM, you change a row and recompile.

### Schema: `ad_bom_child_param`

Each BOM child can carry key-value parameters that the resolver reads at compile time:

```sql
CREATE TABLE ad_bom_child_param (
    param_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_child_id    INTEGER NOT NULL,     -- FK → ad_bom_child
    param_key       TEXT NOT NULL,         -- z_offset, spacing, diameter, ...
    param_value     TEXT NOT NULL,         -- "0.20", "4.3", "PENDANT"
    param_type      TEXT DEFAULT 'DOUBLE', -- DOUBLE, STRING, ENUM
    unit            TEXT,                  -- m, mm, m2, deg
    description     TEXT,                  -- Human-readable
    source_code     TEXT,                  -- Code/standard reference
    is_active       INTEGER DEFAULT 1,
    UNIQUE(bom_child_id, param_key)
);
```

The `z_rule` column on `ad_bom_child` governs the Z-positioning strategy:

| z_rule | Meaning | Formula |
|--------|---------|---------|
| `BELOW_SLAB` | Below slab bottom | `baseZ + height - slabThickness - zOffset` |
| `AT_CEILING` | Below ceiling surface | `baseZ + height - zOffset` |
| `ABOVE_FLOOR` | Above floor level | `baseZ + zOffset` |
| `AT_FLOOR` | At floor level | `baseZ` |
| `BETWEEN_FLOORS` | Full storey span | Riser: `floorZ` to `ceilingZ` |

### FP_PIPE_ASSEMBLY: The Proof

One generic PIPE child became four typed roles, each with metadata:

```
FP_PIPE_ASSEMBLY
├── HEAD  (IfcFireSuppressionTerminal)  z_rule=BELOW_SLAB
│   ├── z_offset:  0.20m   (slab_offset + drop)
│   ├── spacing:   4.3m    (NFPA 13 Light Hazard)
│   ├── coverage:  18.6m²  (max area per head)
│   └── head_type: PENDANT
├── MAIN  (IfcPipeSegment/FP_MAIN)      z_rule=BELOW_SLAB
│   ├── z_offset:  0.15m   (below slab bottom)
│   ├── diameter:  0.065m  (65mm / 2.5")
│   └── routing:   MANHATTAN
├── BRANCH (IfcPipeSegment/FP_BRANCH)   z_rule=BELOW_SLAB
│   ├── diameter:  0.025m  (25mm / 1")
│   └── drop_offset: 0.05m (drop from main to head)
└── RISER  (IfcPipeSegment/FP_RISER)    z_rule=BETWEEN_FLOORS
    └── diameter:  0.100m  (100mm / 4")
```

### Java: Resolver, Not Calculator

```java
// BOMRuleAD.java — loads from DB, resolves Z
public record BOMPlacementParams(
    String zRule, double zOffset, double spacing,
    double diameter, double dropOffset, String routing
) {
    public double resolveZ(double baseZ, double storeyHeight, double slabThickness) {
        return switch (zRule) {
            case "BELOW_SLAB" -> baseZ + storeyHeight - slabThickness - zOffset;
            case "AT_CEILING" -> baseZ + storeyHeight - zOffset;
            case "ABOVE_FLOOR" -> baseZ + zOffset;
            default -> baseZ + storeyHeight - zOffset;
        };
    }
}

// BuildingCompiler.java — two lines replace hardcoded offset
BOMPlacementParams headParams = BOMRuleAD.loadPlacementParams("FP_PIPE_ASSEMBLY", "HEAD");
double sprinklerZ = headParams.resolveZ(baseZ, storey.height(), SLAB_THICKNESS);
```

The Java code doesn't know what 0.20 means. It resolves. The BOM knows.

### Trial Results

**The bug:** 70 sprinklers embedded in slabs on condo_mid (detected by BOMSpatialCheck in Phase 84).

**Root cause:** `ceilingZ = baseZ + height - 0.05` placed sprinklers 50mm below ceiling top = 100mm inside the 150mm slab.

**Fix:** `resolveZ()` with `BELOW_SLAB` rule computes `baseZ + height - 0.15 - 0.20 = 200mm below slab bottom`.

Numerical proof (condo_mid Typical_F3):
```
Slab above F3:  Z = 10.35 to 10.50  (150mm thick, top at F4 level)
Sprinkler on F3: Z = 10.05 to 10.15  (placement point at 10.15)
Gap:             10.35 - 10.15 = 0.20m  ✓  (= z_offset from BOM metadata)
```

| Building | Slab overlaps BEFORE | Slab overlaps AFTER |
|----------|---------------------|---------------------|
| condo_mid | 70 | **0** |
| sekolah_kebangsaan | 11 | 11 (not yet recompiled) |
| tb_lktn | 0 | 0 (no sprinklers) |

### Lessons from the Trial

**1. Variable name collision (caught and fixed).** The original `ceilingZ` variable served sprinklers, exhaust fans, and HVAC diffusers. When we changed it to use BOM-resolved sprinkler Z (350mm lower), exhaust fans and diffusers would have silently dropped 300mm. Fix: separate `sprinklerZ` variable for sprinklers, keep `ceilingZ` for general ceiling reference.

**Rule: when migrating a parameter to BOM, audit ALL consumers of the variable you're replacing.** Shared variables are the #1 trap.

**2. Slab thickness is still a global constant.** `SLAB_THICKNESS = 0.15` works for uniform typical floors but `ad_floor_type` carries per-floor-type values (basement 200mm, transfer 300mm). Mixed-use buildings will need per-storey slab thickness passed to `resolveZ()`. Not broken today, but a known gap.

**3. Spacing param stored but not consumed.** BOM has `spacing=4.3` for HEAD, but the compiler reads spacing from `FireProtectionResolver`/DSL. Two sources of truth. They agree today. Must converge — resolver should read from BOM params.

**4. Per-call DB connection.** `loadPlacementParams()` opens/closes SQLite per call (~20 times for 18-storey building). Should cache or use connection-per-compilation.

**Discipline: migrate one parameter at a time. Audit all consumers. Don't let two sources of truth coexist.**

### The Generalisation Path

Phase 85 proves the pattern on fire protection. The same applies everywhere:

| System | Hardcoded Today | BOM Param Tomorrow |
|--------|----------------|-------------------|
| **Sprinklers** | `height - 0.05` | `z_rule=BELOW_SLAB, z_offset=0.20` |
| **Lights** | `height - 0.02` | `z_rule=AT_CEILING, z_offset=0.02` |
| **Diffusers** | `height - 0.1` | `z_rule=AT_CEILING, z_offset=0.10` |
| **Outlets** | `baseZ + 0.3` | `z_rule=ABOVE_FLOOR, z_offset=0.30` |
| **Switches** | `baseZ + 1.2` | `z_rule=ABOVE_FLOOR, z_offset=1.20` |
| **Beams** | `height - 0.6` | `z_rule=BELOW_SLAB, z_offset=0.0, depth=0.6` |

Each migration: move one constant from Java to `ad_bom_child_param`, call `resolveZ()`. Code shrinks. Data grows. Flexibility increases.

### Presets: DSL as BOM Selector (Future)

When all placement params live in BOM metadata, DSL becomes pure selection:

```bim
BUILDING "My Condo" preset:CONDO_MID
```

The preset table maps to BOM param overrides:

```sql
CREATE TABLE ad_bom_preset (
    preset_id TEXT PRIMARY KEY,
    template_id TEXT NOT NULL,
    preset_name TEXT NOT NULL,
    section TEXT DEFAULT 'ALL'  -- TOP, MIDDLE, BOTTOM, ALL
);

CREATE TABLE ad_bom_preset_param (
    preset_id TEXT NOT NULL,
    bom_id TEXT NOT NULL,
    param_key TEXT NOT NULL,
    param_value TEXT NOT NULL,
    UNIQUE(preset_id, bom_id, param_key)
);
```

Sectional overrides: `preset:CONDO_MID top:PREMIUM middle:STANDARD bottom:PARKING`

This completes the circle: **DSL selects a preset → preset selects BOM params → BOM params drive placement → Java resolves**. No hardcoded constants survive.

---

## Phase 93: Furniture Assembly BOM (Next)

### Concept

Furniture is currently placed individually with hardcoded offsets in `FurniturePlacer.java`. Phase 93 migrates to BOM assemblies — the same recursive resolve pattern proven by `T_CONNECTOR_ASSEMBLY` (Phase 92C).

### Assembly Definitions

**OFFICE_SEATING_SET** — a complete office furniture arrangement:
```
OFFICE_SEATING_SET                          <- phantom (grouping only)
├── WORKSTATION_ASSEMBLY          seq=1     <- phantom
│   ├── Office_Desk               seq=1     role=DESK       (0, 0, 0)
│   ├── Office_Chair              seq=2     role=USER_CHAIR  (0, -0.6, 0) rot=π
│   └── iMac_27                   seq=3     role=MONITOR     (-0.59, 0, +0.73)
├── VISITOR_TABLE                 seq=2     role=TABLE       (0, +2.0, 0)
└── VISITOR_SEATING_PAIR          seq=3     <- phantom
    ├── Visitor_Chair_A           seq=1     role=GUEST       (0, -0.3, 0) rot=0
    └── Visitor_Chair_B           seq=2     role=GUEST       (0, +0.3, 0) rot=π
```

### Placement Rules

| Room Type | Furniture Sets | Placement Rule |
|-----------|---------------|----------------|
| OFFICE (small) | 1× WORKSTATION only | Longest free wall, avoid openings |
| OFFICE (big, >=80m²) | 2× OFFICE_SEATING_SET | NW + SE corners, mirrored (rot=π) |
| LOBBY | 2× OFFICE_SEATING_SET | Zone-based, facing center |
| CORRIDOR | None | Circulation — exempt |
| STAIR | 1× WORKSTATION only | If room permits |

### Key Insight: Mirroring from Rotation

Big rooms get two sets. The second set uses `parentRotation=π`, which automatically:
- Flips all X/Y offsets (desk goes to opposite corner)
- Rotates all children (chair faces opposite direction)
- Visitor seating faces the other workstation

No special mirroring code needed — rotation arithmetic handles it.

---

## Phase 94+: Tower-Level BOM Hierarchy (Vision)

### The Libero Manufacturing BOM Parallel

iDempiere's Libero addon (since removed, but the concept was sound) provided manufacturing BOM with:
- **Variants** — same product, different configurations (sedan vs hatchback)
- **Selections** — user picks from options (wheel size, interior material)
- **Optional components** — include/exclude (sunroof, rear spoiler)
- **Phantom BOMs** — groupings that resolve to children (no physical product)

These map directly to building design:
- **Variants** → Ground floor vs Typical floor vs Roof
- **Selections** → Toilet block type A or B, stair configuration
- **Optional** → Elevator (high-rise only), balcony, car porch
- **Phantom** → Floor template (resolves to rooms + corridors + services)

### Tower Hierarchy

```
TOWER_BOM
├── GROUND_FLOOR_TEMPLATE       x1       variant: ground
│   ├── ENTRANCE_LOBBY
│   ├── MANAGEMENT_OFFICE
│   ├── STAIR_ENCLOSURE_A/B
│   └── UTILITY_ROOMS (tnb, pump, genset)
├── TYPICAL_FLOOR_TEMPLATE      x16      variant: typical
│   ├── LIFT_LOBBY
│   ├── CORRIDOR (single, not dual — frees space for units)
│   ├── UNIT_1BR x4
│   ├── TOILET_BLOCK
│   ├── STAIR_A/B
│   └── ELEVATOR_SHAFT
├── ROOF_FLOOR_TEMPLATE         x1       variant: roof
│   └── PLANT_ROOM
└── VERTICAL_SERVICES                    spans all floors
    ├── RISER_STACK
    ├── ELEVATOR_CAR
    └── STAIR_FLIGHTS
```

### DSL Evolution

Current (explicit per-floor):
```bim
FLOOR Typical_F2 copies Typical height:3.4
FLOOR Typical_F3 copies Typical height:3.4
...
```

BOM-driven (template reference):
```bim
TOWER condo_mid {
  GROUND  template:GROUND_LOBBY
  TYPICAL template:TYPICAL_4UNIT  floors:2-17  height:3.4 {
    option TOILET_BLOCK: VARIANT_B
    option STAIR: PRESSURIZED
    remove UNIT_1BR slot:4
    add    UNIT_STUDIO slot:4
  }
  ROOF template:ROOF_PLANT
}
```

### Schema Additions

```sql
-- Extend ad_bom_child with type classification
ALTER TABLE ad_bom_child ADD COLUMN bom_type TEXT DEFAULT 'STANDARD';
-- STANDARD: fixed recipe | PHANTOM: grouping only | VARIANT: user selects | OPTIONAL: include/exclude

CREATE TABLE ad_bom_variant (
    variant_id TEXT PRIMARY KEY,
    bom_id TEXT,           -- parent BOM this variant belongs to
    variant_name TEXT,
    is_default INTEGER DEFAULT 0
);

CREATE TABLE ad_bom_feature (
    feature_id TEXT PRIMARY KEY,
    bom_id TEXT,           -- which BOM this feature belongs to
    feature_name TEXT,     -- "TOILET_TYPE", "STAIR_TYPE"
    required INTEGER DEFAULT 1
);
```

### Already Populated (Awaiting Consumers)

| Table | Rows | Purpose |
|-------|------|---------|
| `ad_building_template` | 8 | Building type profiles (CONDO, OFFICE, etc.) |
| `ad_floor_type` | 12 | Floor definitions (TYPICAL, GROUND, ROOF) |
| `ad_building_bom` | 8 | Template-to-floor mapping |
| `ad_unit_type` | 7 | Unit templates (STUDIO, 1BR, 2BR) |

These tables are the data backbone for Phases 94-96. The Java consumers don't exist yet.

---

## Future Enhancements

1. **Visual Editor** - Edit generated DSL graphically
2. ~~**Variant Generation**~~ ✓ BOMVariantSystem (Phase 81)
3. ~~**Metadata-Driven Placement**~~ ✓ BOMPlacementParams (Phase 85)
4. ~~**Ceiling MEP from BOM**~~ ✓ MEPBOMResolver (Phase 92D)
5. **Furniture Assembly BOM** — Phase 93 (next)
6. **Tower-Level BOM Hierarchy** — Phase 94+ (vision)
7. **Cost Integration** - M_Product pricing from iDempiere
8. **MEP Sizing** - Auto-calculate loads from room counts
9. **Code Compliance** - Validate against UBBL/IBC from AD
10. **Outliner Integration** - Expand/collapse tree in Bonsai
11. **Generalise BOM params** - Migrate lights, outlets, switches, beams to `ad_bom_child_param`
12. **Preset system** - `ad_bom_preset` + `ad_bom_preset_param` for DSL shorthand

---

## References

- iDempiere M_BOM: https://wiki.idempiere.org/en/M_BOM
- IFC IfcRelAggregates: https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/link/ifcrelaggregates.htm
- IFC IfcTypeObject: https://standards.buildingsmart.org/IFC/RELEASE/IFC4/ADD2_TC1/HTML/link/ifctypeobject.htm
- PROGRESS.md: Phase 55 implementation details
- AutoFitter.java: LEGO fitting algorithm
- PreCompiler.java: Manifest expansion
- BOMAssembly.java: Phase 81 hierarchical tree builder
- BOMTypeSystem.java: Phase 81 type/instance pattern
- BOMVariantSystem.java: Phase 81 ERP-style variants
- BOMRuleAD.java: Phase 85 BOMPlacementParams + loadPlacementParams()
- FireSuppressionPlacer.java: Phase 85 metadata-aware pipe routing

---

*POC proven 2026-02-04. Phase 81 BOM assemblies proven 2026-02-05. Phase 85 metadata-driven placement proven 2026-02-06 (70 slab overlaps → 0). BOM metadata IS DSL metadata.*
