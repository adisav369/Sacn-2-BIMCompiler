# Phase 27: TB-LKTN DSL Dictionary

## Overview

Phase 27 extends the BIM Compiler DSL with construction-aware vocabulary derived from the TB-LKTN affordable housing project (UBBL 1984 compliant).

## Core Concepts

### 1. Structural Grid

Grid lines from architectural drawings define spatial anchors:

```dsl
GRID {
    axes: A, B, C, D, E / 1, 2, 3, 4, 5    // X-axes / Y-axes
    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6  // meters
}
```

**Semantics:**
- Grid intersections → column placement points
- Grid lines → wall alignment guides
- Grid bounds → room positioning (bounds:A2-C3)

### 2. Room Types

| Type | Description | Wall Rules |
|------|-------------|------------|
| `BEDROOM` | Sleeping room | Full walls on all sides |
| `BATHROOM` | Wet area with fixtures | Full walls, waterproofed |
| `KITCHEN` | Food prep area | Full walls or OPEN_PLAN zone |
| `LIVING` | Living space | Full walls or OPEN_PLAN zone |
| `PORCH` | Covered entry | Partial walls (exterior open) |
| `OPEN_PLAN` | **NEW** Combined zones | NO internal walls between zones |

### 3. OPEN_PLAN (Phase 27)

Combines multiple functional zones without internal walls:

```dsl
OPEN_PLAN "common" bounds:B2-D5 {
    zones: LIVING, DINING, KITCHEN
    exterior: south
    exterior: north
}
```

**Semantics:**
- `zones:` defines functional areas (for fixture placement)
- NO walls generated between zones
- Perimeter walls only
- All zones share the space

### 4. Constraints

#### opens_to: (Phase 27)

Indicates room opens onto another space, implying a door connection:

```dsl
BEDROOM "bilik_utama" bounds:A2-C3 {
    opens_to: common    // Door to common area
}
```

**Semantics:**
- Generates door between rooms
- Room must share an edge with target
- Used with OPEN_PLAN to connect private rooms to common areas

#### stack: (Plumbing Stack)

Groups wet areas for vertical MEP coordination:

```dsl
BATHROOM "bilik_mandi" bounds:A3-B4 {
    stack: plumbing
}
BATHROOM "tandas" bounds:A4-B5 {
    stack: plumbing
}
```

**Semantics:**
- Rooms on same stack share vertical risers
- Optimizes plumbing routing
- Ensures wet areas are vertically aligned

#### exterior: (Multiple)

Specifies exterior wall directions:

```dsl
BEDROOM "bilik_2" bounds:D2-E3 {
    exterior: east
    exterior: south
}
```

**Semantics:**
- Each direction gets exterior wall treatment
- Windows auto-placed on exterior walls
- Multiple directions allowed

### 5. Door/Window Specifications

Extended syntax with type codes:

```dsl
DOOR type:D1 size:900x2100 wall:south    // Main entry
DOOR type:D2 size:750x2100               // Bedroom door
DOOR type:D3 size:900x2100               // Wet area door
WINDOW type:W1 size:1800x1000 wall:west  // Large window
WINDOW type:W3 size:600x500 wall:west    // Ventilation window
```

**Type Codes (TB-LKTN):**
| Code | Use | Size (mm) |
|------|-----|-----------|
| D1 | Main entry | 900×2100 |
| D2 | Bedroom | 750×2100 |
| D3 | Wet area | 900×2100 |
| W1 | Standard | 1800×1000 |
| W2 | Living | 1200×1000 |
| W3 | Ventilation | 600×500 |

### 6. Building Envelope

```dsl
ENVELOPE {
    DRAINAGE {
        PERIMETER_DRAIN offset:600mm connects:municipal_drain
    }
}

ROOF pitch:25deg overhang:600mm
```

**Semantics:**
- `offset:` matches roof overhang (construction correlation)
- Drainage follows building perimeter

## Semantic Rules

### Room Size Hierarchy

1. **Common area** should be largest space
2. **Master bedroom** should be largest bedroom
3. **Secondary bedrooms** should be equal or similar
4. **Wet areas** should be smallest

### Validation Checks

```
Master > Bed2, Bed3          (bedroom hierarchy)
WetAreas < Bedrooms          (area appropriateness)
Common > Master              (common largest)
All dimensions > 0           (valid bounds)
```

## Example: TB-LKTN House

```dsl
BUILDING "TB-LKTN" {
    GRID {
        axes: A, B, C, D, E / 1, 2, 3, 4, 5
        spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
    }

    STOREY "Ground" level:0 height:2.8m {
        PORCH "anjung" bounds:C1-D2 { roof: ATTACHED }

        OPEN_PLAN "common" bounds:B2-D5 {
            zones: LIVING, DINING, KITCHEN
            exterior: south
            exterior: north
        }

        BEDROOM "bilik_utama" bounds:A2-C3 {
            opens_to: common
            exterior: west
        }

        BATHROOM "bilik_mandi" bounds:A3-B4 {
            opens_to: common
            stack: plumbing
        }

        BATHROOM "tandas" bounds:A4-B5 {
            opens_to: common
            stack: plumbing
        }

        BEDROOM "bilik_2" bounds:D2-E3 {
            opens_to: common
            exterior: east
        }

        BEDROOM "bilik_3" bounds:D3-E5 {
            opens_to: common
            exterior: east
        }
    }

    ROOF pitch:25deg overhang:600mm
}
```

## Room Areas (PDF Verified)

| Room | Bounds | Size | Area | Status |
|------|--------|------|------|--------|
| anjung | C1-D2 | 3.7×2.3m | 8.51 sqm | ✓ |
| common | B2-D5 | 6.8×6.2m | 42.16 sqm | ✓ Largest |
| bilik_utama | A2-C3 | 4.4×3.1m | 13.64 sqm | ✓ Largest bedroom |
| bilik_mandi | A3-B4 | 1.3×1.5m | 1.95 sqm | ✓ |
| tandas | A4-B5 | 1.3×1.6m | 2.08 sqm | ✓ |
| bilik_2 | D2-E3 | 3.1×3.1m | 9.61 sqm | ✓ |
| bilik_3 | D3-E5 | 3.1×3.1m | 9.61 sqm | ✓ |

**Total: 87.56 sqm** (within UBBL affordable housing limits)
