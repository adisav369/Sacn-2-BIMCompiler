# Architectural Commitment: DSL as Catalog Selector

## The Principle

```
DSL (user)         = SELECT from catalog    → "BUILDING type:CONDO_MID"
Metadata (expert)  = Design catalog         → BOM trees, room layouts, MEP profiles
Java (framework)   = Resolve & compile      → resolveBOM(), resolveZ(), placeAssembly()
```

**DSL = catalog selector. Metadata = design catalog. Java = resolver.**

The DSL should be truly minimal — just type and optional overrides. The metadata (BOM in component_library.db) already carries floor counts, unit mixes, room layouts, MEP profiles, spatial rules. Users browse a design catalog (metadata), pick a type, and the compiler assembles it. Experts create variants over time via SQL INSERT — no Java changes.

## Target DSL (minimal)

```bim
BUILDING "My Project" type:CONDO_MID {
    site: "Lot 123, Bukit Cermin"
    orientation: 15deg
}
```

That's it. Everything else comes from metadata:

| What | Source table(s) |
|------|----------------|
| Floor count, heights | `ad_building_template` + `ad_building_bom` + `ad_floor_type` |
| Unit mix, room layouts | `ad_unit_type` + `ad_unit_type_room` |
| Core layout (stairs, lifts, shafts) | BOM spatial rules |
| MEP profile | `ad_mep_profile` + `ad_space_type_mep_bom` |
| Door/window schedules | `ad_opening_family` + `ad_space_type_opening` |
| Fire protection | `ad_fp_trigger` + `ad_fp_coverage` |
| Furniture | furniture BOM recipes (BED_SET, LIVING_SET, etc.) |

## Variant Growth (the long game)

Experts add variants via SQL INSERT — no Java changes:

```sql
INSERT INTO ad_building_template VALUES ('CONDO_MID_PREMIUM', 'RESIDENTIAL', ...);
INSERT INTO ad_building_bom VALUES ('CONDO_MID_PREMIUM', 'PENTHOUSE', 'TAIL', ...);
```

User selects: `BUILDING type:CONDO_MID_PREMIUM` — different floor mix, same compiler.

## What Metadata Already Has

| Asset | Count | Status |
|-------|-------|--------|
| Building templates | 9 | CONDO_LOW/MID/HIGH, OFFICE_MID/HIGH, MIXED_USE, HOTEL_MID, LANDED_1S/2S |
| Floor types | 12 | With heights, slab thickness, unit type JSON |
| Unit types | 11 | With room counts and areas |
| Unit room layouts | 5 types, 28 rooms | Fractional room coordinates for 2BR_A, HOUSE_3BR, HOUSE_2BR, DUPLEX_GROUND, DUPLEX_UPPER |
| BOM recipes | 21 | With 80 children and 218 params |
| Building-to-floor mappings | 8 | CONDO_MID: basement→ground→typical×N→amenity→penthouse→roof |
| Component definitions | 8,449+ | LOD400 geometry for all element types |
| Component geometries | 8,755+ | Unique vertex/face BLOBs |

## What Metadata Still Needs (future phases)

| Gap | Impact |
|-----|--------|
| Remaining 6 unit types need `ad_unit_type_room` entries | Cannot resolve interior rooms for those types |
| Building templates need CORE BOM children (stairs/lifts/shafts spatial rules) | Core layout still partly hardcoded |
| `FloorPlateBOMResolver` Java consumer for spatial rules | Phase 95 design, partial implementation |
| Variant system (`ad_bom_variant`) for option selection | Multiple configs per template type |
| `ad_space_adjacency` activation | Constraint validation between rooms |

## Separation of Concerns

### What belongs in DSL (user-facing)
- Building type selection
- Site information
- Orientation overrides
- Optional: custom floor count, storey height overrides

### What belongs in metadata (expert-maintained)
- Room sizes, proportions, adjacency rules
- MEP system parameters (pipe sizes, duct dimensions, coverage areas)
- BOM recipes (what children compose an assembly)
- Spatial placement rules (offsets, rotations, z-rules)
- Fire protection triggers and coverage patterns
- Furniture sets per room type

### What belongs in Java (framework)
- Geometry resolution (resolveZ, resolveBounds, orientation matching)
- Assembly compilation (place children, compute transforms)
- Constraint checking (room fits, clearance, code compliance)
- IFC output serialization
- Witness generation

## Why This Matters

1. **Non-programmers can create building variants** — SQL INSERT, not Java
2. **Compiler stays stable** — Java changes only for new resolution strategies
3. **Testable in isolation** — metadata can be validated independently of compilation
4. **Scales to many building types** — one compiler, N metadata sets
5. **Knowledge capture** — expert construction knowledge lives in queryable tables, not buried in code
