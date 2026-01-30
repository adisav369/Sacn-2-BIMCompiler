# Phase 26: Construction-Aware DSL

## Overview

Phase 26 extends the BIM Compiler DSL from "room boxes" to **construction-aware building description**. The key insight is that 2D construction drawings encode **buildability rules**, not just geometry.

## New DSL Elements

### 1. Structural Grid

Grid lines from architectural drawings serve as:
- Column placement anchors (at intersections)
- Wall alignment guides (along grid lines)
- Room boundary references (bounds:A2-B4)

```dsl
GRID {
    axes: A, B, C, D, E / 2, 3, 4, 5    // X axes / Y axes
    spacing: 2.3, 5.0, 3.1, 3.1 / 3.1, 3.1, 4.4, 3.2  // meters
    // OR
    spacing: from_pdf  // Extract from PDF dimensions
}
```

Grid semantics:
- **A-E**: Horizontal grid lines (typically columns at intersections)
- **2-5**: Vertical grid lines (note: can start at any number, not just 1)
- Spacing defines distance between adjacent grid lines

### 2. Building Envelope

Captures construction relationships between roof and drainage:

```dsl
ENVELOPE {
    DRAINAGE {
        PERIMETER_DRAIN offset:600mm connects:municipal_drain
        GUTTER along:eave
        DOWNPIPE at:A2, A5, E2, E5
    }
}
```

Construction chain: `ROOF overhang → GUTTER → DOWNPIPE → PERIMETER_DRAIN`

### 3. Roof with Overhang

Extended ROOF syntax includes overhang dimension:

```dsl
ROOF pitch:25deg overhang:600mm
```

The overhang determines:
- Gutter position
- Perimeter drain offset from exterior wall
- Shading/weather protection extent

### 4. Grid-Bound Rooms

Rooms can reference grid bounds instead of explicit dimensions:

```dsl
// Traditional: explicit size
BEDROOM "room1" size:4x3m { ... }

// Phase 26: grid bounds
SPACE "room1" type:BEDROOM bounds:C2-D4 { ... }
```

Benefits:
- Room dimensions derived from grid spacing
- Position implicit from grid reference
- Matches how architects specify on drawings

### 5. Universal SPACE Primitive

The `SPACE` keyword is the universal primitive:

```dsl
SPACE "name" type:ROOMTYPE bounds:A2-B4 {
    // Room content
}
```

This allows any room type (including unknown types from different vocabularies) to be specified with explicit type annotation.

### 6. Porch Semantics

Porches have specific roof attachment semantics:

```dsl
PORCH "anjung" bounds:A2-B3 {
    roof: ATTACHED    // Shares main roof structure
    // OR
    roof: SEPARATE    // Independent roof
}
```

## Construction Logic Chains

The DSL now captures these buildability relationships:

| From | To | Relationship |
|------|-----|--------------|
| Grid intersection | Column location | Columns at grid crossings |
| Grid line | Wall alignment | Walls align to grid |
| Roof overhang | Gutter position | Gutter at drip line |
| Gutter | Downpipe | At grid corners |
| Downpipe | Perimeter drain | Below downpipe |
| Wet area cluster | Plumbing wall | Shared stack |
| Porch extent | Foundation | Separate/attached |

## Example: TB-LKTN House

```dsl
BUILDING "TB-LKTN Grid House" {

    GRID {
        axes: A, B, C, D, E / 2, 3, 4, 5
        spacing: 2.3, 5.0, 3.1, 3.1 / 3.1, 3.1, 4.4, 3.2
    }

    ENVELOPE {
        DRAINAGE {
            PERIMETER_DRAIN offset:600mm connects:municipal_drain
            DOWNPIPE at:A2, A5, E2, E5
        }
    }

    STOREY "Ground" level:0 height:3.0m {
        PORCH "anjung" bounds:A2-B3 {
            roof: ATTACHED
        }
        SPACE "ruang_tamu" type:LIVING bounds:B2-C4 {
            DOOR south
            WINDOW south
        }
        SPACE "dapur" type:KITCHEN bounds:B4-C5 {
            WINDOW north
        }
        // ... more rooms
    }

    ROOF pitch:25deg overhang:600mm
}
```

## Implementation Details

### BuildingDefinition Records

New records added:
- `GridDef` - Structural grid with axes and spacing
- `EnvelopeDef` - Building envelope wrapper
- `DrainageDef` - Drainage system with offset and downpipes
- `GridBounds` - Parsed grid bounds (A2-B4 → startX, startY, endX, endY)
- `PorchRoofType` - ATTACHED or SEPARATE enum

### RoomDef Extensions

New fields:
- `gridBounds` - Grid bounds reference (e.g., "A2-B4")
- `porchRoofType` - Porch roof attachment type

New methods:
- `hasGridBounds()` - Check if room uses grid bounds
- `isPorch()` - Check if room is a porch type
- `getParsedGridBounds()` - Get parsed GridBounds object

### Parser Extensions

New patterns:
- `GRID_BOUNDS_PATTERN` - `bounds:([A-Za-z]\d+-[A-Za-z]\d+)`
- `PORCH_ROOF_PATTERN` - `roof:\s*(ATTACHED|SEPARATE)`
- Extended `ROOF_PATTERN` - `ROOF\s+pitch:(\d+)deg(?:\s+overhang:(\d+)mm)?`

New methods:
- `parseGrid()` - Parse GRID block
- `parseEnvelope()` - Parse ENVELOPE/DRAINAGE blocks

## Future Work

1. **Grid-aware column placement** - Auto-place columns at grid intersections
2. **Grid-aware wall alignment** - Snap walls to grid lines
3. **Drainage routing** - Route downpipes to perimeter drain
4. **Porch foundation** - Different foundation for ATTACHED vs SEPARATE
5. **MEP coordination** - Wet area plumbing stacks aligned to grid
