# BIM DSL DICTIONARY

## Purpose

This dictionary defines the complete vocabulary for the BIM Intent Compiler DSL. It serves as:

1. **Reference** for DSL authors writing building definitions
2. **Specification** for parser implementation
3. **Configuration** for the factory pattern (extensible without code changes)
4. **Validation rules** for the compiler

The dictionary follows the iDempiere pattern: vocabulary as data, behavior derived from type.

---

## Document Structure

```
DICTIONARY
├── BUILDING (root container)
│   ├── GRID (structural/spatial grid)
│   ├── ENVELOPE (building shell)
│   │   ├── FOUNDATION
│   │   ├── ROOF
│   │   └── DRAINAGE
│   ├── SCHEDULE (door/window/material types)
│   └── STOREY (floor levels)
│       └── SPACE (universal container - the "Document")
│           ├── SpaceType (determines behavior)
│           ├── Constraints (relationships)
│           ├── Openings (doors, windows)
│           ├── Fixtures (equipment, furniture)
│           └── Zones (sub-areas without walls)
```

---

## 1. BUILDING

Root container for all building elements.

### Syntax
```
BUILDING "<name>" {
    GRID { ... }
    ENVELOPE { ... }
    SCHEDULE { ... }
    STOREY "<name>" level:<n> height:<m>m { ... }
}
```

### Attributes
| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| name | string | Yes | Building identifier |

### Example
```
BUILDING "TB-LKTN" {
    ...
}
```

---

## 2. GRID

Defines structural/spatial grid for positioning.

### Syntax
```
GRID {
    axes: <X-labels> / <Y-labels>
    spacing: <X-spacing> / <Y-spacing>
}
```

### Attributes
| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| axes | labels / labels | Yes | Grid line names (e.g., A,B,C,D,E / 1,2,3,4,5) |
| spacing | meters / meters | Yes | Distance between grid lines |

### Computation
```
Position[axis[0]] = 0
Position[axis[n]] = Position[axis[n-1]] + spacing[n-1]
```

### Example
```
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5
    spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
}

// Computed positions:
// X: A=0, B=1.3, C=3.1, D=6.8, E=9.9
// Y: 1=0, 2=2.3, 3=5.4, 4=6.9, 5=8.5
```

---

## 3. ENVELOPE

Building shell elements (foundation, roof, drainage).

### Syntax
```
ENVELOPE {
    FOUNDATION type:<type> depth:<m>m
    ROOF pitch:<deg>deg overhang:<mm>mm
    PERIMETER_DRAIN offset:<mm>mm
    GUTTER along:<edge>
    DOWNPIPE at:<grid-points>
}
```

### FOUNDATION Types
| Type | Description | Use Case |
|------|-------------|----------|
| SLAB_ON_GRADE | Concrete slab on ground | Most residential |
| STRIP | Strip footings under walls | Load-bearing walls |
| PAD | Isolated pad footings | Column support |
| PILE | Deep pile foundation | Poor soil conditions |

### ROOF Attributes
| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| pitch | degrees | Yes | Roof slope angle |
| overhang | mm | No | Eave projection beyond wall |
| type | enum | No | GABLE, HIP, FLAT, MONO (default: GABLE) |

### DRAINAGE Attributes
| Attribute | Type | Description |
|-----------|------|-------------|
| PERIMETER_DRAIN offset | mm | Distance from building edge (typically = overhang) |
| GUTTER along | edge | EAVE, VALLEY, HIP |
| DOWNPIPE at | grid refs | Locations (e.g., A1, E1, A5, E5) |

### Example
```
ENVELOPE {
    FOUNDATION type:SLAB_ON_GRADE depth:150mm
    ROOF pitch:25deg overhang:600mm type:GABLE
    PERIMETER_DRAIN offset:600mm
    GUTTER along:EAVE
    DOWNPIPE at:A1, E1, A5, E5
}
```

---

## 4. SCHEDULE

Defines reusable types for doors, windows, materials.

### Syntax
```
SCHEDULE doors {
    <code>: <width>x<height> "<description>"
}

SCHEDULE windows {
    <code>: <width>x<height> "<description>"
}

SCHEDULE materials {
    <code>: "<specification>"
}
```

### Example
```
SCHEDULE doors {
    D1: 900x2100 "Metal frame, single leaf solid timber"
    D2: 750x2100 "Flush door with gloss paint"
    D3: 900x2100 "Flush door with gloss paint"
}

SCHEDULE windows {
    W1: 1800x1000 "Aluminium 3 panel adjustable"
    W2: 1200x1000 "Aluminium 2 panel adjustable"
    W3: 600x500 "Aluminium top hung single panel"
}

SCHEDULE materials {
    WALL_EXT: "150mm brick with plaster"
    WALL_INT: "100mm brick with plaster"
    FLOOR: "Cement render with tiles"
}
```

### Usage in SPACE
```
SPACE "bedroom" {
    DOOR type:D2          // Looks up 750x2100 from schedule
    WINDOW type:W1        // Looks up 1800x1000 from schedule
}
```

---

## 5. STOREY

Horizontal division of building (floor level).

### Syntax
```
STOREY "<name>" level:<n> height:<m>m {
    SPACE definitions...
}
```

### Attributes
| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| name | string | Yes | Storey identifier |
| level | integer | Yes | Floor number (0 = ground) |
| height | meters | Yes | Floor-to-floor height |

### Example
```
STOREY "Ground" level:0 height:2.8m {
    ...
}

STOREY "Upper" level:1 height:2.8m {
    ...
}
```

---

## 6. SPACE (Universal Container)

The fundamental building block. All occupiable areas are SPACEs with a SpaceType that determines behavior.

### Syntax
```
SPACE "<name>" type:<SpaceType> bounds:<grid-bounds> {
    // Constraints
    exterior: <direction>
    opens_to: <space-name>
    adjacent: <space-name>
    not_adjacent: <space-name>
    stack: <stack-name>
    above: <space-name>
    below: <space-name>
    
    // Openings
    DOOR type:<code> size:<w>x<h> wall:<direction>
    WINDOW type:<code> size:<w>x<h> wall:<direction>
    
    // Fixtures
    FIXTURE <type> wall:<direction>
    
    // Zones (for OPEN_PLAN only)
    ZONE "<name>" position:<position>
}
```

### Alternative Syntax (type as keyword)
```
BEDROOM "<name>" bounds:<grid-bounds> { ... }
BATHROOM "<name>" bounds:<grid-bounds> { ... }
OPEN_PLAN "<name>" bounds:<grid-bounds> { ... }
```

This is syntactic sugar for `SPACE "<name>" type:BEDROOM ...`

---

## 7. SpaceType Dictionary

SpaceType determines:
- Wall generation rules
- Required/optional fixtures
- Validation rules
- MEP requirements

### SpaceType Definitions

#### BEDROOM
```
BEDROOM {
    category: HABITABLE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: [WARDROBE, CEILING_FAN]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET]
        optional: [CEILING_FAN, AIR_CONDITIONING]
    }
    
    validation: {
        min_area: 6.5 m²          // IRC R304.1
        min_dimension: 2.134 m     // IRC R304.2
        requires_window: true      // IRC R303.1
        requires_egress: true      // IRC R310
    }
    
    openings: {
        door: { min_width: 750, min_height: 2000 }
        window: { min_area: 0.48 m² }  // 8% of floor area
    }
}
```

#### BATHROOM
```
BATHROOM {
    category: SERVICE
    wall_rule: ENCLOSED
    
    fixtures: {
        required: [TOILET, SINK]
        optional: [SHOWER, BATHTUB, EXHAUST_FAN]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET, PLUMBING_STACK, EXHAUST]
        optional: [WATER_HEATER]
    }
    
    validation: {
        min_area: 2.5 m²
        min_dimension: 1.2 m
        requires_window: false     // Can be interior
        requires_egress: true
        requires_ventilation: true // Window OR exhaust fan
    }
    
    openings: {
        door: { min_width: 600, min_height: 2000 }
        window: { optional: true, type: W3 }
    }
    
    constraints: {
        stack: plumbing           // Vertical alignment for drainage
    }
}
```

#### TOILET (WC Only)
```
TOILET {
    category: SERVICE
    wall_rule: ENCLOSED
    
    fixtures: {
        required: [TOILET, SINK]
    }
    
    mep: {
        required: [LIGHT, PLUMBING_STACK, EXHAUST]
    }
    
    validation: {
        min_area: 1.5 m²
        min_dimension: 0.9 m
        requires_window: false
        requires_ventilation: true
    }
    
    constraints: {
        stack: plumbing
    }
}
```

#### KITCHEN
```
KITCHEN {
    category: HABITABLE
    wall_rule: ENCLOSED
    
    fixtures: {
        required: [SINK, STOVE_POINT]
        optional: [REFRIGERATOR_POINT, DISHWASHER_POINT, EXHAUST_HOOD]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET, PLUMBING_STACK]
        optional: [GAS_POINT, EXHAUST_HOOD]
    }
    
    validation: {
        min_area: 4.0 m²
        requires_window: true      // Or exterior door
        requires_egress: true
    }
    
    openings: {
        door: { min_width: 750 }
        window: { required: true }
    }
}
```

#### LIVING
```
LIVING {
    category: HABITABLE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: [CEILING_FAN, AIR_CONDITIONING]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET]
        optional: [CEILING_FAN, AIR_CONDITIONING, TV_POINT]
    }
    
    validation: {
        min_area: 6.5 m²           // IRC R304.1
        min_dimension: 2.134 m
        requires_window: true
    }
    
    openings: {
        door: { min_width: 900 }   // Main entry typically larger
        window: { min_area: 0.48 m² }
    }
}
```

#### DINING
```
DINING {
    category: HABITABLE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: [CEILING_FAN]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET]
    }
    
    validation: {
        min_area: 6.0 m²
        requires_window: false     // Can be interior if open to living
    }
}
```

#### OPEN_PLAN
```
OPEN_PLAN {
    category: HABITABLE
    wall_rule: PERIMETER_ONLY      // KEY: No internal walls
    
    zones: {
        allowed: [LIVING, DINING, KITCHEN]
        // Zones are logical divisions, not physical walls
    }
    
    fixtures: {
        // Inherited from contained zones
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET]
        // Additional MEP from zones
    }
    
    validation: {
        min_area: sum(zone.min_area)
        requires_window: true
        requires_egress: true
    }
    
    openings: {
        door: { min_width: 900 }   // Main entry
        // Rooms with opens_to get doors to this space
    }
}
```

#### CORRIDOR
```
CORRIDOR {
    category: CIRCULATION
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: []
    }
    
    mep: {
        required: [LIGHT]
        optional: [SMOKE_DETECTOR]
    }
    
    validation: {
        min_width: 0.914 m         // IRC R311.6 (36 inches)
        requires_window: false
    }
}
```

#### PORCH
```
PORCH {
    category: EXTERIOR
    wall_rule: NONE                // Open or partial walls
    
    fixtures: {
        optional: [CEILING_FAN, LIGHT]
    }
    
    mep: {
        optional: [LIGHT, POWER_OUTLET]
    }
    
    validation: {
        requires_window: false
        requires_egress: false     // Exterior space
    }
    
    roof: {
        options: [ATTACHED, SEPARATE, NONE]
        default: ATTACHED
    }
}
```

#### GARAGE
```
GARAGE {
    category: VEHICLE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: [WORKBENCH]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET]
        optional: [EV_CHARGER]
    }
    
    validation: {
        min_width: 3.0 m           // Single car
        min_depth: 6.0 m
        requires_window: false
    }
    
    openings: {
        vehicle_door: { min_width: 2400, min_height: 2100 }
        personnel_door: { optional: true }
    }
    
    constraints: {
        fire_separation: true      // IRC R302.6 - separation from habitable
    }
}
```

#### UTILITY / LAUNDRY
```
UTILITY {
    category: SERVICE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: [WASHING_MACHINE_POINT, DRYER_POINT, SINK]
    }
    
    mep: {
        required: [LIGHT, POWER_OUTLET, PLUMBING_STACK]
    }
    
    validation: {
        min_area: 2.0 m²
        requires_window: false
    }
    
    constraints: {
        stack: plumbing            // Near wet areas
    }
}
```

#### STORAGE
```
STORAGE {
    category: SERVICE
    wall_rule: ENCLOSED
    
    fixtures: {
        optional: []
    }
    
    mep: {
        optional: [LIGHT]
    }
    
    validation: {
        min_area: none
        requires_window: false
        requires_egress: false
    }
}
```

#### STAIR
```
STAIR {
    category: CIRCULATION
    wall_rule: AS_REQUIRED         // May be open or enclosed
    
    fixtures: {
        required: [STAIR_FLIGHT, LANDING]
        optional: [RAILING]
    }
    
    validation: {
        min_width: 0.914 m         // IRC R311.7
        max_riser: 196 mm          // IRC R311.7.5.1
        min_tread: 254 mm          // IRC R311.7.5.2
        headroom: 2.032 m          // IRC R311.7.2
    }
    
    constraints: {
        spans_storeys: true        // Vertical connection
    }
}
```

---

## 8. Constraints Dictionary

Constraints express SPACE-to-SPACE relationships.

### Constraint Types

| Constraint | Syntax | Meaning | Compiler Action |
|------------|--------|---------|-----------------|
| exterior | `exterior: <direction>` | Space has exterior wall on this side | Place windows, use exterior wall type |
| opens_to | `opens_to: <space>` | Space has door connection to named space | Generate door at shared edge |
| adjacent | `adjacent: <space>` | Space must share wall with named space | Solver constraint + auto-door |
| not_adjacent | `not_adjacent: <space>` | Space must NOT share wall | Solver constraint |
| stack | `stack: <name>` | Vertical alignment group | Same X,Y across storeys |
| above | `above: <space>` | Directly above named space | Implies stack alignment |
| below | `below: <space>` | Directly below named space | Implies stack alignment |
| aligns | `aligns: <space>` | Same X,Y position (cross-storey) | Solver constraint |

### Direction Values
```
NORTH, SOUTH, EAST, WEST
FRONT, BACK, LEFT, RIGHT  // Aliases based on building orientation
```

### Stack Names (Predefined)
```
plumbing    // Wet areas - drainage alignment
structure   // Columns, load-bearing walls
mep         // Mechanical shafts
```

---

## 9. Openings Dictionary

### DOOR

```
DOOR {
    attributes: {
        type: <schedule-code>      // References SCHEDULE doors
        size: <width>x<height>     // Override schedule size
        wall: <direction>          // Which wall to place on
        offset: <meters>           // Distance from wall start
    }
    
    defaults: {
        height: 2100               // Standard door height
        width: by_spacetype        // From SpaceType.openings.door
    }
    
    auto_placement: {
        trigger: opens_to          // Auto-generate for opens_to constraint
        position: center           // Center on shared edge
    }
}
```

### WINDOW

```
WINDOW {
    attributes: {
        type: <schedule-code>      // References SCHEDULE windows
        size: <width>x<height>     // Override schedule size
        wall: <direction>          // Must match exterior direction
        offset: <meters>           // Distance from wall start
        sill_height: <meters>      // Height from floor (default 0.9m)
    }
    
    defaults: {
        sill_height: 900           // Standard sill height
        size: by_spacetype         // From SpaceType.openings.window
    }
    
    auto_placement: {
        trigger: exterior          // Auto-generate for exterior walls
        condition: spacetype.requires_window
    }
}
```

---

## 10. Fixtures Dictionary

Fixtures are elements that occupy spaces, placed per SpaceType rules.

### Fixture Types

| Fixture | Dimensions (mm) | Placement | Clearance |
|---------|-----------------|-----------|-----------|
| TOILET | 510 × 800 | Against wall | 450mm front, 380mm sides |
| SINK_BATHROOM | 610 × 510 | Against wall | 530mm front |
| SINK_KITCHEN | 530 × 850 | Against wall, under window | 900mm front |
| SHOWER | 900 × 900 | Corner | Door swing clearance |
| BATHTUB | 1700 × 750 | Against wall | 600mm access side |
| STOVE | 600 × 600 | Against wall | 400mm sides |
| REFRIGERATOR_POINT | 900 × 700 | Corner/wall | Door swing clearance |
| CEILING_FAN | 1200 diameter | Center of room | 2400mm floor clearance |
| EXHAUST_FAN | 290 × 290 | Ceiling, wet area | Duct to exterior |
| WARDROBE | 600 × variable | Against wall | 600mm front |

### Fixture Placement Rules

```
FIXTURE {
    placement_priority: [
        wall_mounted: [BACK, SIDE, FRONT]
        corner: [BACK_LEFT, BACK_RIGHT]
        center: [CEILING_CENTER]
    ]
    
    clash_detection: {
        min_separation: 50mm
        skip_if_clash: true        // Log as GEOMETRY_IMPOSSIBLE
    }
    
    auto_placement: {
        by_spacetype: true         // Place fixtures per SpaceType.fixtures
    }
}
```

---

## 11. ZONE (Sub-area within OPEN_PLAN)

Zones are logical divisions without physical walls.

### Syntax
```
ZONE "<name>" position:<position> {
    FIXTURE <type> wall:<direction>
}
```

### Position Values
```
NORTH, SOUTH, EAST, WEST, CENTER
FRONT, BACK, LEFT, RIGHT
```

### Example
```
OPEN_PLAN "common" bounds:B2-D5 {
    ZONE "living" position:SOUTH {
        FIXTURE ceiling_fan
    }
    ZONE "dining" position:CENTER
    ZONE "kitchen" position:NORTH {
        FIXTURE sink wall:NORTH
        FIXTURE stove wall:NORTH
    }
}
```

### Compiler Behavior
- No walls generated between zones
- Fixtures placed per zone position
- MEP aggregated from all zones

---

## 12. Wall Rules

How walls are generated based on SpaceType.

| wall_rule | Meaning | Generated Walls |
|-----------|---------|-----------------|
| ENCLOSED | Fully walled room | All 4 sides |
| PERIMETER_ONLY | Walls at building edge only | Exterior sides only |
| NONE | No walls | Columns/posts only (if structural) |
| AS_REQUIRED | Context-dependent | Per constraint resolution |

### Wall Types

| Context | Wall Type | Thickness |
|---------|-----------|-----------|
| Exterior | EXTERIOR | 150mm (from Terminal) |
| Interior partition | INTERIOR | 100mm |
| Wet area | WET_AREA | 100mm with waterproofing |
| Fire separation | FIRE_RATED | 150mm with rating |

---

## 13. MEP Dictionary

### MEP Elements

| Element | SpaceTypes | Placement | Grid |
|---------|------------|-----------|------|
| LIGHT | All | Ceiling center or grid | 3.0m |
| POWER_OUTLET | All | Wall, 300mm AFF | Per code |
| CEILING_FAN | BEDROOM, LIVING | Ceiling center | - |
| EXHAUST_FAN | BATHROOM, TOILET, KITCHEN | Ceiling | - |
| SMOKE_DETECTOR | CORRIDOR, BEDROOM | Ceiling | Per code |
| SPRINKLER | All (if required) | Ceiling grid | 4.6m max |
| PLUMBING_STACK | SERVICE spaces | Vertical alignment | - |

### MEP Routing

```
MEP {
    ceiling_zone: 440-727mm below structure    // From Terminal G3
    wall_zone: 87-229mm inside face            // From Terminal G3

    plumbing_stack: {
        alignment: stack constraint
        max_offset: 500mm from stack center
    }
}
```

### Fire Suppression Piping (Phase 80)

When sprinklers are generated, FP piping is automatically created:

```
FP_PIPING {
    // Pipe hierarchy
    RISER: diameter_100mm, vertical, per_storey
    MAIN: diameter_65mm, horizontal, ceiling_mounted
    BRANCH: diameter_25mm, connection_to_head

    // NFPA 13 sizing (Light Hazard)
    sizing: {
        riser: 100mm (4")   // Schedule 40
        main: 65mm (2.5")   // Up to 40 heads
        branch: 25mm (1")   // 1-2 heads per branch
    }

    // Routing
    routing: {
        main_offset_from_ceiling: 150mm
        branch_drop_to_head: 50mm
        manhattan_routing: true    // L-shaped paths
    }

    // BOM assemblies
    assemblies: {
        FP_<storey>_RISER: riser segment
        FP_<storey>_MAIN: main distribution
        FP_<storey>_<room>_BRANCH: branches per room
    }
}
```

| Pipe Type | Diameter | IFC Class | Discipline |
|-----------|----------|-----------|------------|
| RISER | 100mm | IfcPipeSegment | FP |
| MAIN | 65mm | IfcPipeSegment | FP |
| BRANCH | 25mm | IfcPipeSegment | FP |

### MEP System Graphs (Phase 35+)

For complete MEP connectivity vocabulary (SystemType, NodeRole, EdgeType, graph operations), see:

**→ [bim-dsl-dictionary-mep-addendum.md](bim-dsl-dictionary-mep-addendum.md)**

---

## 14. Validation Rules

### Building-Level
```
VALIDATION building {
    min_rooms: 1
    max_storeys: 10
    requires_egress: true
    requires_entry: true
}
```

### Storey-Level
```
VALIDATION storey {
    min_height: 2.4m
    max_height: 6.0m
    floor_coverage: 100%           // No gaps
}
```

### Space-Level
```
VALIDATION space {
    // From SpaceType definitions
    check: min_area
    check: min_dimension
    check: requires_window
    check: requires_egress
    check: requires_ventilation
}
```

### Geometry-Level
```
VALIDATION geometry {
    max_gap: 5mm                   // TOLERANCE from Terminal
    wall_connectivity: required
    room_enclosure: required       // For ENCLOSED wall_rule
    manifold: required             // Watertight solids
}
```

---

## 15. Localization

### Malaysian Terms (Auto-mapped)

| Malaysian | English SpaceType |
|-----------|-------------------|
| RUANG_TAMU | LIVING |
| BILIK_UTAMA | BEDROOM (primary) |
| BILIK | BEDROOM |
| BILIK_MANDI | BATHROOM |
| TANDAS | TOILET |
| DAPUR | KITCHEN |
| RUANG_MAKAN | DINING |
| ANJUNG | PORCH |
| RUANG_BASUH | UTILITY |
| KORIDOR | CORRIDOR |
| GARAJ | GARAGE |
| STOR | STORAGE |

### Code Standards

| Region | Code | Validation Set |
|--------|------|----------------|
| US | IRC 2021 | Default |
| Malaysia | UBBL | Localized minimums |
| UK | Building Regs | Localized minimums |

---

## 16. Complete Example

```
BUILDING "TB-LKTN" {

    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }
    
    SCHEDULE doors {
        D1: 900x2100 "Metal frame solid timber"
        D2: 750x2100 "Flush door gloss paint"
        D3: 900x2100 "Flush door gloss paint"
    }
    
    SCHEDULE windows {
        W1: 1800x1000 "Aluminium 3 panel"
        W2: 1200x1000 "Aluminium 2 panel"
        W3: 600x500 "Aluminium top hung"
    }
    
    ENVELOPE {
        FOUNDATION type:SLAB_ON_GRADE depth:150mm
        ROOF pitch:25deg overhang:600mm
        PERIMETER_DRAIN offset:600mm
    }
    
    STOREY "Ground" level:0 height:2.8m {
    
        PORCH "anjung" bounds:A1-E2 {
            roof: ATTACHED
        }
        
        OPEN_PLAN "common" bounds:B2-D5 {
            exterior: south
            DOOR type:D1 wall:south
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:south
            
            ZONE "living" position:south
            ZONE "dining" position:center
            ZONE "kitchen" position:north {
                FIXTURE sink wall:north
            }
        }
        
        BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: west
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:west
        }
        
        BATHROOM "bilik_mandi" bounds:A3-B4 {
            exterior: west
            opens_to: common
            stack: plumbing
            DOOR type:D3
            WINDOW type:W3 wall:west
        }
        
        TOILET "tandas" bounds:A4-B5 {
            exterior: west
            opens_to: common
            stack: plumbing
            DOOR type:D3
            WINDOW type:W3 wall:west
        }
        
        BEDROOM "bilik_2" bounds:D2-E3 {
            exterior: east
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:east
        }
        
        BEDROOM "bilik_3" bounds:D3-E5 {
            exterior: east
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:east
        }
    }
}
```

---

## 17. PROFILE (Regional/Domain Variants)

Profiles adapt the base dictionary for regional codes, building types, or project requirements. Inspired by **HL7 FHIR Profiles** and **STEP Application Protocols**.

### Syntax
```
PROFILE "<name>" extends <base> {
    validation: <code_standard>
    localization: <language>
    spacetypes: include [...] | exclude [...]
    defaults: { ... }
    constraints: { ... }
}
```

### Predefined Profiles

#### Malaysian Residential
```
PROFILE "Malaysian_Residential" extends BASE {
    validation: UBBL_1984
    localization: MS_MY
    
    spacetypes: include [
        ANJUNG, RUANG_TAMU, BILIK, BILIK_UTAMA,
        BILIK_MANDI, TANDAS, DAPUR, RUANG_BASUH
    ]
    
    defaults: {
        storey_height: 2.8m
        roof_pitch: 25deg
        roof_overhang: 600mm
        wall_exterior: 150mm_brick
        wall_interior: 100mm_brick
        door_main: 900x2100
        door_room: 750x2100
        door_wet: 900x2100
        window_habitable: 1800x1000
        window_wet: 600x500
    }
    
    constraints: {
        wet_areas_clustered: true
        plumbing_stack_required: true
        cross_ventilation: recommended
    }
    
    climate: TROPICAL_HUMID
}
```

#### US Residential (IRC)
```
PROFILE "US_Residential_IRC" extends BASE {
    validation: IRC_2021
    localization: EN_US
    
    defaults: {
        storey_height: 2.74m          // 9 ft
        wall_exterior: 2x6_frame
        wall_interior: 2x4_frame
        door_main: 914x2032           // 36" x 80"
        door_room: 762x2032           // 30" x 80"
        window_egress: 0.44_sqm       // 5.7 sq ft
    }
    
    validation_rules: {
        bedroom_min_area: 6.5_sqm     // 70 sq ft
        bedroom_min_dimension: 2.134m  // 7 ft
        ceiling_min_height: 2.286m    // 7.5 ft
        corridor_min_width: 0.914m    // 36"
        stair_min_width: 0.914m       // 36"
        stair_max_riser: 0.196m       // 7.75"
        stair_min_tread: 0.254m       // 10"
    }
    
    climate: VARIES_BY_ZONE
}
```

#### UK Residential
```
PROFILE "UK_Residential" extends BASE {
    validation: BUILDING_REGS_2010
    localization: EN_GB
    
    defaults: {
        storey_height: 2.4m
        wall_exterior: cavity_wall
        wall_interior: 100mm_block
    }
    
    validation_rules: {
        bedroom_min_area: 6.5_sqm     // Single
        bedroom_double_min: 11.0_sqm  // Double
        ceiling_min_height: 2.3m
    }
    
    climate: TEMPERATE_MARITIME
}
```

#### Commercial (IBC)
```
PROFILE "Commercial_IBC" extends BASE {
    validation: IBC_2021
    localization: EN_US
    
    spacetypes: include [
        OFFICE, CONFERENCE, LOBBY, CORRIDOR,
        RESTROOM, BREAK_ROOM, STORAGE, MECHANICAL,
        DEPARTURE_LOUNGE, GATE, CONCOURSE  // Terminal types
    ]
    
    defaults: {
        storey_height: 4.0m
        sprinkler_grid: 4.6m          // NFPA 13
        light_grid: 3.0m
    }
    
    validation_rules: {
        occupancy_load: by_use_group
        egress_width: by_occupancy
        accessibility: ADA_required
    }
}
```

### Profile Inheritance
```
PROFILE "Malaysian_Low_Cost" extends "Malaysian_Residential" {
    // Inherits all Malaysian defaults, adds constraints
    
    constraints: {
        max_built_up: 93_sqm          // 1000 sq ft limit
        min_bedrooms: 3
        open_plan_encouraged: true    // Cost efficiency
    }
    
    defaults: {
        wall_interior: 100mm_brick    // Simplified
        finishes: basic               // Cost control
    }
}
```

### Using Profiles
```
BUILDING "TB-LKTN" profile:"Malaysian_Residential" {
    // All Malaysian defaults apply automatically
    // Validation uses UBBL
    // Malaysian terms auto-recognized
}
```

---

## 18. SPECIALIZATION (Type Hierarchy)

SpaceTypes can specialize base types, inheriting and extending behavior. Inspired by **DITA specialization**.

### Syntax
```
SPACETYPE "<name>" specializes <base_type> {
    // Override or extend base attributes
}
```

### Specialization Examples

#### Bedroom Variants
```
SPACETYPE "MASTER_BEDROOM" specializes BEDROOM {
    validation: {
        min_area: 12.0_sqm           // Larger than base 6.5
        min_dimension: 3.0m
    }
    
    fixtures: {
        required: [WARDROBE]         // Added requirement
        optional: [CEILING_FAN, AIR_CONDITIONING, TV_POINT]
    }
    
    constraints: {
        allows_ensuite: true         // Can have adjacent bathroom
        preferred_position: corner   // Privacy
    }
    
    openings: {
        window: { min_count: 2 }     // Cross ventilation
    }
}

SPACETYPE "CHILD_BEDROOM" specializes BEDROOM {
    validation: {
        min_area: 9.0_sqm
    }
    
    fixtures: {
        optional: [STUDY_DESK, WARDROBE]
    }
    
    safety: {
        window_restrictor: required
    }
}

SPACETYPE "GUEST_BEDROOM" specializes BEDROOM {
    validation: {
        min_area: 9.0_sqm
    }
    
    constraints: {
        near_bathroom: preferred
    }
}
```

#### Bathroom Variants
```
SPACETYPE "ENSUITE" specializes BATHROOM {
    constraints: {
        adjacent: MASTER_BEDROOM     // Required adjacency
        access: private              // Only from bedroom
    }
    
    validation: {
        min_area: 3.5_sqm
    }
    
    fixtures: {
        required: [TOILET, SINK, SHOWER]
    }
}

SPACETYPE "POWDER_ROOM" specializes TOILET {
    // Half-bath for guests
    constraints: {
        near: [LIVING, DINING, ENTRY]
        access: public
    }
    
    validation: {
        min_area: 1.5_sqm
    }
    
    fixtures: {
        required: [TOILET, SINK]
        excluded: [SHOWER, BATHTUB]
    }
}

SPACETYPE "JACK_AND_JILL" specializes BATHROOM {
    // Shared between two bedrooms
    constraints: {
        adjacent: [BEDROOM, BEDROOM]  // Two connections
        doors: 2
    }
    
    fixtures: {
        required: [TOILET, SINK, SHOWER]
        optional: [DOUBLE_SINK]
    }
}
```

#### Kitchen Variants
```
SPACETYPE "GALLEY_KITCHEN" specializes KITCHEN {
    validation: {
        min_width: 1.8m              // Narrow
        max_width: 3.0m
    }
    
    layout: linear_parallel
}

SPACETYPE "ISLAND_KITCHEN" specializes KITCHEN {
    validation: {
        min_area: 12.0_sqm           // Needs space for island
    }
    
    fixtures: {
        required: [SINK, STOVE_POINT, ISLAND]
    }
    
    layout: with_island
}

SPACETYPE "WET_KITCHEN" specializes KITCHEN {
    // Malaysian - separate for heavy cooking
    constraints: {
        ventilation: enhanced
        separate_from: DRY_KITCHEN
    }
    
    fixtures: {
        required: [SINK, STOVE_POINT, EXHAUST_HOOD]
    }
    
    finishes: {
        floor: non_slip_tile
        wall: washable_tile
    }
}

SPACETYPE "DRY_KITCHEN" specializes KITCHEN {
    // Malaysian - prep and light cooking
    constraints: {
        can_be_open_plan: true
    }
    
    fixtures: {
        required: [SINK, COUNTER]
        optional: [MICROWAVE_POINT, COFFEE_POINT]
    }
}
```

#### Living Area Variants
```
SPACETYPE "FAMILY_ROOM" specializes LIVING {
    constraints: {
        adjacent: KITCHEN            // Informal connection
        can_be_open_plan: true
    }
}

SPACETYPE "FORMAL_LIVING" specializes LIVING {
    constraints: {
        near: ENTRY                  // For guests
        separate_from: FAMILY_ROOM
    }
    
    validation: {
        min_area: 15.0_sqm
    }
}

SPACETYPE "SUNROOM" specializes LIVING {
    constraints: {
        exterior: minimum_3_sides
        glazing: enhanced
    }
    
    openings: {
        window: { coverage: 60_percent }
    }
}
```

### Specialization Rules

1. **Inheritance**: Child inherits all parent attributes
2. **Override**: Child can make requirements stricter, not looser
3. **Extension**: Child can add new attributes
4. **Validation**: Child must satisfy parent validation + own

```
// Compiler resolves:
MASTER_BEDROOM
    ├── inherits: BEDROOM.category (HABITABLE)
    ├── inherits: BEDROOM.wall_rule (ENCLOSED)
    ├── inherits: BEDROOM.requires_window (true)
    ├── overrides: BEDROOM.min_area (6.5 → 12.0)
    ├── extends: allows_ensuite (new attribute)
    └── validates: both BEDROOM rules AND MASTER_BEDROOM rules
```

---

## 19. LOD Levels (Level of Detail/Development)

Same space can be defined at different detail levels. Inspired by **CityGML LOD** and **BIM LOD specifications**.

### LOD Definitions

| LOD | Name | Content | Use Case |
|-----|------|---------|----------|
| LOD 100 | Conceptual | Area, type only | Early design |
| LOD 200 | Approximate | Dimensions, positions | Schematic design |
| LOD 300 | Precise | Exact geometry, openings | Design development |
| LOD 350 | Coordination | MEP, structure coordination | Construction docs |
| LOD 400 | Fabrication | Shop drawings, products | Fabrication |
| LOD 500 | As-Built | Verified field conditions | Facilities mgmt |

### Syntax
```
SPACE "<name>" type:<type> lod:<level> {
    // Content varies by LOD
}
```

### LOD Examples

#### LOD 100 - Conceptual
```
SPACE "master" type:BEDROOM lod:100 {
    area: 12_sqm
    // That's it - just type and area
}
```

#### LOD 200 - Approximate
```
SPACE "master" type:BEDROOM lod:200 bounds:A2-C3 {
    exterior: west
    opens_to: common
    // Dimensions from grid, relationships defined
}
```

#### LOD 300 - Precise
```
SPACE "master" type:BEDROOM lod:300 bounds:A2-C3 {
    exterior: west
    opens_to: common
    DOOR type:D2 wall:east offset:0.5m
    WINDOW type:W1 wall:west offset:1.2m sill:0.9m
    // Exact opening positions
}
```

#### LOD 350 - Coordination
```
SPACE "master" type:BEDROOM lod:350 bounds:A2-C3 {
    exterior: west
    opens_to: common
    DOOR type:D2 wall:east offset:0.5m
    WINDOW type:W1 wall:west offset:1.2m sill:0.9m
    
    MEP {
        LIGHT position:center type:LED_PANEL
        POWER_OUTLET wall:east count:2 height:0.3m
        POWER_OUTLET wall:west count:1 height:0.3m
        CEILING_FAN position:center
        AIR_CONDITIONING wall:north height:2.1m
    }
    
    STRUCTURE {
        lintel: above_openings
    }
}
```

#### LOD 400 - Fabrication
```
SPACE "master" type:BEDROOM lod:400 bounds:A2-C3 {
    exterior: west
    opens_to: common
    
    DOOR type:D2 wall:east offset:0.5m {
        product: "Acme Flush Door 750x2100"
        hardware: "SS Lever Handle Set"
        finish: "Gloss White Paint"
    }
    
    WINDOW type:W1 wall:west offset:1.2m sill:0.9m {
        product: "YKK AP 3-Panel Sliding"
        glass: "6mm Clear Float"
        frame: "Powder Coated Aluminium - White"
    }
    
    FINISHES {
        floor: "Timber Laminate 8mm - Oak"
        wall: "Plastered Brick - Emulsion Paint White"
        ceiling: "Plasterboard 9mm - Emulsion Paint White"
        skirting: "Timber 75mm - Gloss White"
    }
    
    MEP {
        LIGHT position:center {
            product: "Philips LED Panel 600x600"
            circuit: "DB1-C3"
        }
        // ... full MEP with products and circuits
    }
}
```

#### LOD 500 - As-Built
```
SPACE "master" type:BEDROOM lod:500 bounds:A2-C3 {
    // LOD 400 content plus:
    
    AS_BUILT {
        survey_date: "2025-01-15"
        surveyor: "ABC Surveyors"
        
        deviations: {
            actual_area: 11.8_sqm    // vs designed 12.0
            wall_north: +15mm        // As-built deviation
        }
        
        installations: {
            door: { serial: "ACM-2025-0042", installed: "2025-01-10" }
            window: { serial: "YKK-2025-1234", installed: "2025-01-08" }
        }
    }
}
```

### LOD Progression

```
Design workflow:
LOD 100 (concept) → LOD 200 (schematic) → LOD 300 (DD) → LOD 350 (CD) → LOD 400 (fab)
                                                                              ↓
                                                                         Construction
                                                                              ↓
                                                                    LOD 500 (as-built)
```

### LOD and BOM

| LOD | BOM Content |
|-----|-------------|
| 100 | Not applicable |
| 200 | Room types only |
| 300 | Generic quantities (walls, openings) |
| 350 | Coordinated quantities |
| 400 | **Product-specific BOM** (procurement-ready) |
| 500 | Verified quantities |

---

## 20. APPLICATION PROTOCOL (Building Type Templates)

Protocols define complete building type requirements. Inspired by **STEP Application Protocols** (AP214, AP242).

### Syntax
```
PROTOCOL "<name>" {
    description: "<text>"
    applicable_profiles: [...]
    
    required_spaces: [...]
    optional_spaces: [...]
    excluded_spaces: [...]
    
    required_relationships: [...]
    validation_rules: [...]
}
```

### Protocol Examples

#### Single-Storey Residential
```
PROTOCOL "Residential_Single_Storey" {
    description: "Single-storey detached house"
    applicable_profiles: [Malaysian_Residential, US_Residential_IRC, UK_Residential]
    
    required_spaces: {
        BEDROOM: { min: 1, max: 6 }
        BATHROOM: { min: 1 }
        KITCHEN: { min: 1 } OR OPEN_PLAN: { with_zone: KITCHEN }
        LIVING: { min: 1 } OR OPEN_PLAN: { with_zone: LIVING }
    }
    
    optional_spaces: [
        DINING, STUDY, GARAGE, PORCH,
        UTILITY, STORAGE, TOILET
    ]
    
    excluded_spaces: [
        STAIR,                        // Single storey
        ELEVATOR,
        DEPARTURE_LOUNGE, GATE        // Commercial only
    ]
    
    required_relationships: {
        all_bedrooms: { requires_window: true }
        wet_areas: { stack: plumbing }
        kitchen: { requires: [ventilation, water_supply] }
    }
    
    validation_rules: {
        min_habitable_area: by_profile
        max_storeys: 1
        requires_entry: true
        requires_egress: per_bedroom
    }
    
    envelope: {
        foundation: required
        roof: required
        external_walls: required
    }
}
```

#### Multi-Storey Residential
```
PROTOCOL "Residential_Multi_Storey" extends "Residential_Single_Storey" {
    description: "Multi-storey detached house"
    
    required_spaces: {
        // Inherited from single-storey plus:
        STAIR: { min: 1 }
    }
    
    optional_spaces: [
        // Inherited plus:
        ELEVATOR
    ]
    
    excluded_spaces: [
        // Remove STAIR from exclusion
    ]
    
    required_relationships: {
        stair: { connects_all_storeys: true }
        bedrooms_upper: { requires_egress: enhanced }  // Fire safety
        wet_areas: { stack: plumbing, vertical_aligned: preferred }
    }
    
    validation_rules: {
        max_storeys: 4               // Beyond = high-rise regulations
        stair_width: by_profile
        fire_separation: per_storey
    }
}
```

#### Apartment Unit
```
PROTOCOL "Apartment_Unit" {
    description: "Individual apartment unit within building"
    
    required_spaces: {
        BEDROOM: { min: 0 }          // Studio allowed
        BATHROOM: { min: 1 }
        KITCHEN: { min: 1 } OR KITCHENETTE: { min: 1 }
        LIVING: { min: 1 }
    }
    
    optional_spaces: [
        DINING, STUDY, UTILITY, STORAGE, BALCONY
    ]
    
    excluded_spaces: [
        STAIR,                        // Building common area
        ELEVATOR,
        GARAGE
    ]
    
    constraints: {
        single_storey: true          // No internal stairs
        entry: { type: APARTMENT_DOOR, count: 1 }
        party_walls: { fire_rated: true, sound_rated: true }
    }
    
    validation_rules: {
        min_area: by_profile         // Varies by bedroom count
        natural_light: required
        ventilation: required
    }
}
```

#### Commercial Office
```
PROTOCOL "Commercial_Office" {
    description: "Commercial office building"
    applicable_profiles: [Commercial_IBC]
    
    required_spaces: {
        LOBBY: { min: 1, at: GROUND }
        ELEVATOR: { min: 1, if: storeys > 3 }
        STAIR: { min: 2, type: FIRE_STAIR }
        RESTROOM: { min: 2, type: [MALE, FEMALE] }  // Per floor
    }
    
    optional_spaces: [
        OFFICE, OPEN_OFFICE, CONFERENCE, BREAK_ROOM,
        RECEPTION, MAILROOM, SERVER_ROOM, MECHANICAL
    ]
    
    required_relationships: {
        stairs: { distributed: true, max_travel_distance: 60m }
        restrooms: { accessible: min_1_per_type }
        mechanical: { access: maintenance }
    }
    
    validation_rules: {
        occupancy_load: calculate_by_area
        egress_width: calculate_by_occupancy
        accessibility: ADA_required
        fire_protection: NFPA_required
        sprinklers: if_area_exceeds_threshold
    }
}
```

#### Airport Terminal
```
PROTOCOL "Airport_Terminal" {
    description: "Passenger terminal building"
    applicable_profiles: [Commercial_IBC]
    
    required_spaces: {
        CHECK_IN_HALL: { min: 1 }
        SECURITY_SCREENING: { min: 1 }
        DEPARTURE_LOUNGE: { min: 1 }
        GATE: { min: 1 }
        ARRIVAL_HALL: { min: 1 }
        BAGGAGE_CLAIM: { min: 1 }
    }
    
    optional_spaces: [
        CONCOURSE, RETAIL, F_AND_B, LOUNGE_PREMIUM,
        IMMIGRATION, CUSTOMS, AIRLINE_OFFICE
    ]
    
    required_relationships: {
        passenger_flow: CHECK_IN → SECURITY → DEPARTURE_LOUNGE → GATE
        separation: { sterile: [DEPARTURE_LOUNGE, GATE], non_sterile: [CHECK_IN] }
        baggage: { separate_from: passenger_flow }
    }
    
    validation_rules: {
        // Terminal-specific from extracted patterns
        gate_spacing: by_aircraft_type
        lounge_area: by_gate_capacity
        ceiling_height: min_4.5m
        sprinkler_spacing: 4.6m      // NFPA 13
    }
}
```

### Using Protocols

```
BUILDING "TB-LKTN" 
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey" 
{
    // Compiler validates against protocol requirements
    // Missing required spaces → ERROR
    // Excluded spaces present → ERROR
    // Relationship violations → ERROR
}
```

### Protocol Validation

```
Compiler checks:
1. All required_spaces present
2. No excluded_spaces present
3. All required_relationships satisfied
4. All validation_rules pass
5. Envelope requirements met

Output:
[PASS] Protocol "Residential_Single_Storey" satisfied
  ✓ BEDROOM count: 3 (required: 1-6)
  ✓ BATHROOM count: 2 (required: min 1)
  ✓ KITCHEN: via OPEN_PLAN zone
  ✓ LIVING: via OPEN_PLAN zone
  ✓ All bedrooms have windows
  ✓ Wet areas on plumbing stack
  ✓ Entry door present
```

---

## 21. Extension Protocol

To add new vocabulary:

### Adding a SpaceType

1. Define in SpaceType dictionary section
2. Specify: category, wall_rule, fixtures, mep, validation, openings
3. Add localization mappings if needed
4. Register in SpaceTypeResolver.java

### Adding a Fixture

1. Add to Fixtures Dictionary with dimensions and placement rules
2. Assign to SpaceTypes that receive it
3. Add to component_library.db (or document parametric fallback)
4. Implement placer in FixturePlacer.java

### Adding a Constraint

1. Add to Constraints Dictionary
2. Implement in SpaceSolver.java
3. Implement geometry realization in BuildingCompiler.java
4. Add validation if needed

### Adding Localization

1. Add term mapping to Localization section
2. Register in SpaceTypeResolver for auto-mapping

### Adding a Profile

1. Define in Profiles section with validation rules and defaults
2. Can extend existing profile
3. Register in ProfileResolver.java

### Adding a Specialization

1. Define with `specializes` parent type
2. Override/extend attributes (can only make stricter, not looser)
3. Register in SpaceTypeResolver.java

### Adding a Protocol

1. Define required/optional/excluded spaces
2. Define required relationships
3. Define validation rules
4. Register in ProtocolValidator.java

---

## 22. Standards Alignment

This dictionary aligns with industry standards:

### IFC (ISO 16739)

| BIM DSL Concept | IFC Equivalent |
|-----------------|----------------|
| BUILDING | IfcBuilding |
| STOREY | IfcBuildingStorey |
| SPACE | IfcSpace |
| SpaceType | IfcSpaceType + PredefinedType |
| OPEN_PLAN zones | IfcZone |
| Wall | IfcWall / IfcWallStandardCase |
| DOOR | IfcDoor |
| WINDOW | IfcWindow |
| opens_to | IfcRelSpaceBoundary |
| adjacent | IfcRelSpaceBoundary |
| Assembly | IfcElementAssembly |

### LOD (BIM Forum)

| BIM DSL LOD | BIM Forum LOD | Content |
|-------------|---------------|---------|
| LOD 100 | LOD 100 | Conceptual |
| LOD 200 | LOD 200 | Approximate geometry |
| LOD 300 | LOD 300 | Precise geometry |
| LOD 350 | LOD 350 | Coordination |
| LOD 400 | LOD 400 | Fabrication |
| LOD 500 | LOD 500 | As-built |

### Classification Systems

| System | Use in DSL |
|--------|------------|
| OmniClass (Table 13) | SpaceType classification |
| UniFormat | Assembly classification |
| MasterFormat | Material classification |
| Uniclass (UK) | Profile: UK_Residential |

### Code Standards Supported

| Code | Profile |
|------|---------|
| IRC 2021 (US Residential) | US_Residential_IRC |
| IBC 2021 (US Commercial) | Commercial_IBC |
| UBBL 1984 (Malaysia) | Malaysian_Residential |
| Building Regs 2010 (UK) | UK_Residential |
| NFPA 13 (Sprinklers) | All commercial profiles |
| ADA (Accessibility) | All US profiles |

---

## 23. Implementation Architecture

### Java Class Mapping

```
Dictionary Section          Java Implementation
─────────────────────────────────────────────────
BUILDING                 → BuildingDefinition.java
GRID                     → GridDefinition.java
ENVELOPE                 → EnvelopeDefinition.java
SCHEDULE                 → ScheduleRegistry.java
STOREY                   → StoreyDefinition.java
SPACE                    → SpaceDefinition.java
SpaceType                → SpaceType.java (enum + rules)
Constraints              → ConstraintType.java
Openings                 → OpeningSpec.java
Fixtures                 → FixtureSpec.java
ZONE                     → ZoneDefinition.java
PROFILE                  → ProfileDefinition.java
Specialization           → SpaceTypeHierarchy.java
LOD                      → LODLevel.java
PROTOCOL                 → ProtocolDefinition.java
```

### Factory Pattern

```
DictionaryReader
    ↓ loads
ProfileResolver ←── selects profile
    ↓
SpaceTypeResolver ←── resolves types (with specialization)
    ↓
ScheduleRegistry ←── looks up door/window types
    ↓
HybridFactory
    ├── LibraryFactory (LOD 400 components)
    └── ParametricFactory (generated geometry)
    ↓
BuildingCompiler
    ↓
ValidatorChain ←── validates against Profile + Protocol
    ↓
IFCExporter
```

### Configuration vs Code

| Aspect | Configuration (Dictionary) | Code (Java) |
|--------|---------------------------|-------------|
| SpaceType definitions | ✓ | |
| Validation rules | ✓ | |
| Fixture dimensions | ✓ | |
| Profile defaults | ✓ | |
| Protocol requirements | ✓ | |
| Parsing logic | | ✓ |
| Geometry generation | | ✓ |
| Solver algorithms | | ✓ |
| IFC export | | ✓ |

**Principle**: Vocabulary as data, algorithms as code.

---

## 24. Complete Example with Modern Features

```
// Full TB-LKTN with Profile, Specialization, LOD

BUILDING "TB-LKTN" 
    profile: "Malaysian_Residential"
    protocol: "Residential_Single_Storey"
    lod: 300
{

    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 1.8, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }
    
    SCHEDULE doors {
        D1: 900x2100 "Metal frame solid timber"
        D2: 750x2100 "Flush door gloss paint"
        D3: 900x2100 "Flush door gloss paint"
    }
    
    SCHEDULE windows {
        W1: 1800x1000 "Aluminium 3 panel"
        W2: 1200x1000 "Aluminium 2 panel"
        W3: 600x500 "Aluminium top hung"
    }
    
    ENVELOPE {
        FOUNDATION type:SLAB_ON_GRADE depth:150mm
        ROOF pitch:25deg overhang:600mm type:GABLE
        PERIMETER_DRAIN offset:600mm
    }
    
    STOREY "Ground" level:0 height:2.8m {
    
        // Using specialized type
        PORCH "anjung" bounds:A1-E2 {
            roof: ATTACHED
        }
        
        // Open plan with zones
        OPEN_PLAN "common" bounds:B2-D5 {
            exterior: south
            DOOR type:D1 wall:south
            DOOR type:D1 wall:north
            WINDOW type:W1 wall:south
            
            ZONE "living" position:south
            ZONE "dining" position:center
            ZONE "kitchen" position:north {
                FIXTURE sink wall:north
            }
        }
        
        // Master bedroom - specialized type
        MASTER_BEDROOM "bilik_utama" bounds:A2-C3 {
            exterior: west
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:west
            WINDOW type:W1 wall:south  // Cross ventilation
        }
        
        // Ensuite could be added with specialized type
        BATHROOM "bilik_mandi" bounds:A3-B4 {
            exterior: west
            opens_to: common
            stack: plumbing
            DOOR type:D3
            WINDOW type:W3 wall:west
        }
        
        TOILET "tandas" bounds:A4-B5 {
            exterior: west
            opens_to: common
            stack: plumbing
            adjacent: bilik_mandi
            DOOR type:D3
            WINDOW type:W3 wall:west
        }
        
        // Regular bedrooms
        BEDROOM "bilik_2" bounds:D2-E3 {
            exterior: east
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:east
        }
        
        BEDROOM "bilik_3" bounds:D3-E5 {
            exterior: east
            opens_to: common
            DOOR type:D2
            WINDOW type:W1 wall:east
        }
    }
}
```

### Compiler Output

```
=== BUILD REPORT ===

Profile: Malaysian_Residential
  ✓ Validation: UBBL_1984
  ✓ Localization: MS_MY
  ✓ Defaults applied

Protocol: Residential_Single_Storey
  ✓ BEDROOM: 3 (required 1-6)
  ✓ BATHROOM: 1 + TOILET: 1 (required min 1 wet area)
  ✓ KITCHEN: via OPEN_PLAN zone
  ✓ LIVING: via OPEN_PLAN zone
  ✓ All bedrooms have windows
  ✓ Wet areas on plumbing stack

LOD: 300
  ✓ Precise geometry
  ✓ Exact opening positions
  ✗ MEP details (LOD 350 required)
  ✗ Product specifications (LOD 400 required)

Geometry:
  Spaces: 7
  Walls: 18 (perimeter + partitions, none inside OPEN_PLAN)
  Doors: 7
  Windows: 8
  Fixtures: 5

Validation:
  ✓ All rooms meet minimum area
  ✓ All habitable rooms have windows
  ✓ Entry door present
  ✓ 0.0mm gaps (geometry verified)

Output:
  → tb_lktn.ifc (valid IFC4)
  → tb_lktn.db (geometry database)
  → tb_lktn_bom.csv (quantities)
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | January 2025 | Initial dictionary from TB-LKTN analysis |
| 2.0 | January 2025 | Added Profile, Specialization, LOD, Protocol (modern standards alignment) |
| 2.1 | February 2025 | Added Fire Suppression Piping vocabulary (Phase 80) |

---

## References

- **HL7 FHIR**: https://hl7.org/fhir/ (Profiles, Resources)
- **DITA**: https://www.oasis-open.org/committees/dita/ (Specialization)
- **CityGML**: https://www.ogc.org/standards/citygml (LOD, ADE)
- **STEP/ISO 10303**: https://www.iso.org/standard/72237.html (Application Protocols)
- **IFC/ISO 16739**: https://www.buildingsmart.org/standards/bsi-standards/ifc/ (BIM Standard)
- **BIM Forum LOD**: https://bimforum.org/lod/ (Level of Development)
- **OmniClass**: https://www.csiresources.org/standards/omniclass (Classification)

---

*Dictionary follows the principle: SPACE is the universal container (like Document in ERP). SpaceType determines behavior. Extend vocabulary through dictionary entries, not code changes. Align with industry standards for interoperability.*

