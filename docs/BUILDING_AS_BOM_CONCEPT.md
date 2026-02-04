# Building as BOM (Bill of Materials) Concept

**Status:** POC Proven
**Date:** 2026-02-04
**Inspired By:** iDempiere M_BOM/M_Product hierarchy

---

## Core Insight

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

### 1. Products Know Their Dimensions

Every product in AD carries:
- `width`, `depth`, `height`
- `clear_front`, `clear_side` (clearances)
- `fits_in` (what spaces it belongs to)
- `qty_per_area` or `qty_per_room`

No constraint solving needed - just lookup.

### 2. Spaces Know Their Requirements

Every space in AD carries:
- `min_width`, `min_depth`, `min_area`
- `required_products` (must have)
- `optional_products` (if space allows)

Fitting is deterministic.

### 3. Rough Cut First, Refine Later

- First pass: Place rooms roughly
- Second pass: Merge shared walls
- Third pass: Fix product collisions
- Final: Snap to grid

### 4. Rooms Share Walls, Not Own Them

A room doesn't own 4 walls. It:
- References shared walls
- Contributes to wall openings
- Wall is created once, shared by adjacent rooms

### 5. Middle Room = One Wall

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

## Future Enhancements

1. **Visual Editor** - Edit generated DSL graphically
2. **Variant Generation** - Generate multiple unit configurations
3. **Cost Integration** - M_Product pricing from iDempiere
4. **MEP Sizing** - Auto-calculate loads from room counts
5. **Code Compliance** - Validate against UBBL/IBC from AD

---

## References

- iDempiere M_BOM: https://wiki.idempiere.org/en/M_BOM
- PROGRESS.md: Phase 55 implementation details
- AutoFitter.java: LEGO fitting algorithm
- PreCompiler.java: Manifest expansion

---

*POC proven 2026-02-04. Ready for refinement.*
