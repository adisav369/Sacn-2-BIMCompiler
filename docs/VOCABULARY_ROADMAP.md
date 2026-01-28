# DSL Vocabulary Roadmap

Planned extensions to the BIM Compiler constraint language.

---

## Current Vocabulary (Implemented)

### Horizontal Constraints (Phase 15)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `adjacent:` | `adjacent: roomname` | Rooms must share wall |
| `not_adjacent:` | `not_adjacent: roomname` | Rooms cannot share wall |
| `exterior:` | `exterior: north` | Room must touch building edge |

### Openings (Phase 15B)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `DOOR` | `DOOR south` | Door on wall |
| `WINDOW` | `WINDOW east` | Window on wall |
| Auto-placement | - | Doors for adjacent, windows for exterior |

### MEP (Phase 14B)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `SPRINKLERS` | `SPRINKLERS grid:4.6m` | Fire sprinkler grid |
| `LIGHTS` | `LIGHTS grid:3.0m` | Light fixture grid |

### Roof (Phase 11)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `ROOF` | `ROOF pitch:15deg` | Gable roof |

---

## Phase 16: Vertical Alignment ✓ COMPLETE

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `aligns:` | `aligns: roomname` | Same X,Y as room on other storey |

**Use Case:** Plumbing stacks - bathrooms aligned vertically for efficient wet risers.

```dsl
BUILDING "townhouse" {
    STOREY "Ground" level:0 height:2.8m {
        BATHROOM "bath_lower" size:2.5x3m { adjacent: living }
    }
    STOREY "Upper" level:1 height:2.8m {
        BATHROOM "bath_upper" size:2.5x3m {
            aligns: bath_lower    // Same X,Y position
        }
    }
}
```

**Test:** `TownhouseAlignsTest.java` - 3/3 PASS

---

## Phase 17: Vertical Relations ✓ COMPLETE

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `above:` | `above: roomname` | Directly above room (higher storey) |
| `below:` | `below: roomname` | Directly below room (lower storey) |
| `stack:` | `stack: name` | Named vertical group (all rooms share X,Y) |

**Use Case 1:** "Master bedroom above living room" for structural efficiency.

```dsl
STOREY "Upper" {
    BEDROOM "master" size:5x4m {
        above: living     // Same X,Y as living room below
    }
}
```

**Use Case 2:** MEP plumbing core across 3+ storeys.

```dsl
BUILDING "highrise" {
    STOREY "Ground" { BATHROOM "bath_g" { stack: plumbing_core } }
    STOREY "First"  { BATHROOM "bath_1" { stack: plumbing_core } }
    STOREY "Second" { BATHROOM "bath_2" { stack: plumbing_core } }
}
```

**Tests:**
- `VerticalConstraintTest.java` - 4/4 PASS (above: + stack:)
- `BelowConstraintTest.java` - 3/3 PASS (below:)

---

## Phase 18: Roof Extensions (Planned)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `style:` | `ROOF style:HIP` | GABLE, HIP, FLAT, SHED |
| `ridge:` | `ridge: NORTH_SOUTH` | Ridge orientation |
| `overhang:` | `overhang: 300mm` | Eave overhang |

```dsl
ROOF style:HIP ridge:NORTH_SOUTH pitch:22deg overhang:450mm
```

---

## Phase 19: Wet Zone Grouping (Planned)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `WET_ZONE` | `WET_ZONE "plumbing_core" { ... }` | Group wet rooms |
| `RISER` | `RISER at:A3` | Plumbing riser location |

**Use Case:** Cluster bathrooms, kitchens, laundries for efficient plumbing.

```dsl
WET_ZONE "core" {
    BATHROOM "bath" { adjacent: riser }
    KITCHEN "kitchen" { adjacent: riser }
    LAUNDRY "laundry" { adjacent: riser }
    RISER at:center
}
```

---

## Phase 20: Structural Grid (Planned)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `STRUCTURE_GRID` | `spacing: 6x8m` | Column grid |
| `COLUMN` | `at: intersections` | Column placement |
| `spans:` | `spans: 2x2` | Room spans grid cells |

```dsl
BUILDING "warehouse" {
    STRUCTURE_GRID spacing:6x8m {
        COLUMN at:intersections
    }
    STOREY "Ground" {
        WAREHOUSE "main" spans:4x3 { ... }  // 24m x 24m
    }
}
```

---

## Phase 21: Circulation (Research)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `CORRIDOR` | `connects: [room1, room2, ...]` | Corridor routing |
| `LOBBY` | `connects: [entries]` | Lobby with multiple exits |

**Challenge:** This is a graph routing problem, more complex than CSP.

```dsl
CORRIDOR "main_hall" width:1.8m {
    connects: [entry, living, kitchen, bedroom1, bedroom2]
    fire_exit: required
}
```

---

## Phase 22: Code Compliance (Research)

| Keyword | Syntax | Meaning |
|---------|--------|---------|
| `code:` | `code: IBC_2021` | Building code selection |
| `accessibility:` | `accessibility: ADA` | Accessibility standard |
| `fire:` | `fire: NFPA_13` | Fire code |

```dsl
BUILDING "office" {
    code: IBC_2021
    accessibility: ADA
    fire: NFPA_13

    // Compiler validates against codes
    BATHROOM "accessible" size:2.5x3m {
        // Auto-sized to 60" turning radius
    }
}
```

---

## Future Research Topics

### Natural Language Interface (Layer 4)

```
"Design a 4-bedroom house with en-suite for master"
    ↓
BUILDING "house" {
    STOREY "Ground" {
        LIVING size:5x4m { exterior:south }
        KITCHEN size:4x3m { adjacent:living }
        BEDROOM "master" size:4x4m { adjacent:ensuite; exterior:north }
        BATHROOM "ensuite" size:2.5x3m { adjacent:master }
        BEDROOM "bed2" size:3x3m { ... }
        BEDROOM "bed3" size:3x3m { ... }
        BEDROOM "bed4" size:3x3m { ... }
    }
}
```

### Generative Design

```
OPTIMIZE "footprint" {
    minimize: perimeter
    maximize: natural_light
    constraint: total_area >= 150m²
}
```

### Climate-Responsive

```
CLIMATE zone:4A {
    // Auto-orientation for passive solar
    // Window sizing based on heating/cooling loads
}
```

---

## Implementation Priority

| Phase | Feature | Value | Complexity | Status |
|-------|---------|-------|------------|--------|
| 16 | `aligns:` | High (MEP) | Low | ✓ Complete |
| 17 | `above:/below:/stack:` | High (structure) | Low | ✓ Complete |
| 18 | Roof styles | Medium | Medium | Planned |
| 19 | Wet zones | High (cost) | Medium | Planned |
| 20 | Structural grid | High (commercial) | High | Planned |
| 21 | Corridors | Medium | High | Research |
| 22 | Code compliance | Very High | Very High | Research |

---

## Constraint Category Summary

```
HORIZONTAL (same storey)     VERTICAL (cross-storey)
├── adjacent:                ├── aligns:
├── not_adjacent:            ├── above:
├── exterior:                ├── below:
└── near: (future)           └── stack:

STRUCTURAL                   CIRCULATION
├── spans:                   ├── connects:
├── on_grid:                 ├── fire_exit:
└── load_bearing:            └── min_width:

COMPLIANCE                   MEP
├── code:                    ├── SPRINKLERS
├── accessibility:           ├── LIGHTS
└── fire:                    └── RISER
```
