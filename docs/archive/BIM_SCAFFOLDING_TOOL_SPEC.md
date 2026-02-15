# BIM Compiler — Extensibility & Scaffolding Specification

**Project:** BIM Intent Compiler
**Date:** 2026-02-15 (revised from 2026-02-02 original)
**Status:** Partially implemented — metadata pipeline works, CLI scaffolding not yet built

---

## What the Architecture Actually Enables

The compiler is metadata-driven. **All geometry comes from reference IFC models via extraction, not from Java code.** This means extensibility is a data problem, not a code problem.

### Three Levels of Extension

| Level | What You Get | What You Do | Code Changes? |
|-------|-------------|-------------|--------------|
| **1. New building variant** | Exact replica of any IFC model | Extract → insert metadata → write DSL manifest | None |
| **2. Modified variant** | Parametric variation of existing building | Edit metadata rows (move walls, swap fixtures, resize) | None |
| **3. New space type** | New room type (e.g., PRAYER_HALL) in vocabulary | Add AD table entries + BOM recipes | Minimal (routing) |

### Current Proof: 3 Rosetta Stones at 100%

| Building | Type | Elements | Disciplines | Positional <50mm |
|----------|------|----------|-------------|-----------------|
| SampleHouse | Single-storey house | 141 | ARC | 100% |
| Duplex | 2-storey stacked duplex | 1,840 | ARC + MEP + STR | 100% |
| Terminal | Multi-storey commercial | 21,401 | ARC + MEP + STR | 100% |

All achieved via the same pattern: **extract from reference IFC → store as metadata → compile to exact replica.**

---

## Level 1: New Building Variant (Zero Code)

### The Pipeline

```
Reference IFC  →  placement_extractor.py  →  ad_element_placement rows
                                           →  ad_slab_spec rows
                                           →  ad_floor_type rows
                                           →  ad_wall_type rows
                                           →  ad_building_storey rows

DSL manifest   →  Parser  →  Compiler  →  Reads metadata  →  Emits elements at exact positions
```

### Step-by-Step

```bash
# 1. Extract reference IFC to a reference database
python3 tools/extract.py /path/to/new_building.ifc --output reference/rosetta/NewBuilding_extracted.db

# 2. Extract placement metadata from reference
python3 tools/placement_extractor.py \
  --reference reference/rosetta/NewBuilding_extracted.db \
  --building-type NEW_BUILDING

# 3. Write a minimal DSL manifest
cat > examples/new_building.bim << 'EOF'
BUILDING "New_Building" {
    TYPE: NEW_BUILDING
    STOREY "Ground Floor" level:0 height:3.2m { }
    STOREY "First Floor"  level:1 height:3.2m { }
}
EOF

# 4. Create E2E test class (or add to existing)
# 5. Compile and verify
mvn compile -q
python3 tools/spatial_checker.py output/new_building.db reference/rosetta/NewBuilding_extracted.db --discipline ARC --positional
```

### What Gets Extracted (32,488 rows across 3 buildings today)

| Table | What It Stores | Rows (current) |
|-------|---------------|----------------|
| `ad_element_placement` | Every element's exact position, class, discipline, storey | 32,488 |
| `ad_slab_spec` | Per-building slab extends (floor/ceiling) | per-storey |
| `ad_floor_type` / `ad_wall_type` | Construction assembly types | per-building |
| `ad_building_storey` | Storey names, levels, heights | per-building |

### What You Get

- Every wall, slab, column, beam, door, window, furniture piece at exact reference position
- Every MEP element (pipes, ducts, fixtures, alarms, lights, sprinklers) at exact position
- Every structural element (columns, beams, piles, footings) at exact position
- Cross-discipline coverage: ARC + MEP + STR + all sub-disciplines (FP, ELEC, ACMV, SP, CW, LPG)
- 100% positional fidelity (<50mm) verified by spatial_checker.py

---

## Level 2: Modified Variant (Zero Code)

Once a building's metadata exists, creating variants is SQL manipulation:

### Example: Move a wall

```sql
-- Shift all walls on Ground Floor 500mm east
UPDATE ad_element_placement
SET minX = minX + 0.5, maxX = maxX + 0.5
WHERE building_type = 'NEW_BUILDING'
  AND storey = 'Ground Floor'
  AND ifc_class IN ('IfcPlate', 'IfcWall');
```

### Example: Swap all light fixtures

```sql
-- Replace downlights with panel lights (different dimensions)
UPDATE ad_element_placement
SET element_ref = 'Panel_Light_600x600',
    minX = minX - 0.15, maxX = maxX + 0.15,  -- wider
    minY = minY - 0.15, maxY = maxY + 0.15
WHERE building_type = 'NEW_BUILDING'
  AND ifc_class = 'IfcLightFixture';
```

### Example: Create a mirrored unit

```sql
-- Mirror unit B from unit A (flip along Y axis)
INSERT INTO ad_element_placement (building_type, storey, ifc_class, discipline, element_ref, ordinal,
                                   minX, maxX, minY, maxY, minZ, maxZ)
SELECT building_type, storey, ifc_class, discipline, element_ref, ordinal + 1000,
       minX, maxX,
       26.0 - maxY, 26.0 - minY,  -- mirror along Y=13.0 center
       minZ, maxZ
FROM ad_element_placement
WHERE building_type = 'DUPLEX' AND storey LIKE '%_a';
```

### Constraints

- Positions are absolute world coordinates — moving one element doesn't move connected elements
- No automatic clash detection on metadata edits (rely on spatial_checker post-compile)
- BOM recipes and assembly connections won't auto-update — manual consistency required
- Score is the arbiter: compile → check → if score drops, revert

---

## Level 3: New Space Type (Minimal Code)

Adding a new room type (e.g., PRAYER_HALL, CLINIC_CONSULT) requires entries in the AD tables that control room compilation.

### Required Metadata Entries

| Table | Purpose | Example |
|-------|---------|---------|
| `ad_space_type` | Room type definition | PRAYER_HALL, HABITABLE, min 20m² |
| `ad_space_type_mep` | MEP rules per room | 4 lights, 2 outlets, 1 switch |
| `ad_space_type_furniture` | Furniture rules | PRAYER_MAT center grid |
| `ad_space_type_opening` | Door/window defaults | D1 south, W2 north |
| `ad_bom` + `ad_bom_child` | Assembly recipe | Components for this room type |
| `ad_room_slot` | Slot positions | WHERE fixtures/furniture go |
| `ad_wall_type_rule` | Wall construction | Exterior: brick+plaster, Interior: partition |

### Example: Adding PRAYER_HALL

```sql
-- 1. Space type definition
INSERT INTO ad_space_type (name, category, wall_rule, min_area_m2, min_dimension_m, omniclass)
VALUES ('PRAYER_HALL', 'HABITABLE', 'ENCLOSED', 20.0, 4.0, '13-65 11 00');

-- 2. MEP rules
INSERT INTO ad_space_type_mep (space_type, system, element_type, qty_rule, source)
VALUES
  ('PRAYER_HALL', 'ELEC', 'LIGHT', '1 per 6m2', 'MS1525'),
  ('PRAYER_HALL', 'ELEC', 'OUTLET', '1 per 10m2', 'MS1525'),
  ('PRAYER_HALL', 'ELEC', 'SWITCH', '1 per entry', 'MS1525'),
  ('PRAYER_HALL', 'FP', 'SPRINKLER', '1 per 12m2', 'NFPA13'),
  ('PRAYER_HALL', 'ACMV', 'DIFFUSER', '1 per 8m2', 'ASHRAE');

-- 3. Furniture rules
INSERT INTO ad_space_type_furniture (space_type, furniture_type, placement, qty_rule)
VALUES ('PRAYER_HALL', 'PRAYER_MAT', 'CENTER_GRID', '1 per 1.2m2');

-- 4. Use in DSL
-- PRAYER_HALL "main_hall" bounds:A1-D4 { exterior: south }
```

### What Needs Java Changes

| What | Where | Why |
|------|-------|-----|
| Room type routing | `StoreyCompiler.java` | Map PRAYER_HALL to correct wall/MEP/furniture logic |
| Witness claims | `witness/PrayerHallClaims.java` | Verification proofs for this room type |
| Protocol binding | `BuildingCompiler.java` | Associate with building protocol (Mosque, Community Centre) |

For room types that follow existing patterns (enclosed room with walls, doors, windows, MEP), the Java changes are minimal — often just adding the type name to an existing `switch` or `Set`.

---

## Extending the Component Library

The component library (`library/component_library.db`) contains 8,766 LOD400 components extracted from real IFC models. Adding new components:

### From IFC Model

```bash
# Extract components from a new IFC file
python3 tools/extract.py /path/to/source.ifc --components --output library/new_components.db

# Merge into main library (SQL)
sqlite3 library/component_library.db "ATTACH 'library/new_components.db' AS src;
INSERT INTO component_definitions SELECT * FROM src.component_definitions
WHERE name NOT IN (SELECT name FROM component_definitions);"
```

### Component Structure

Each component in `component_definitions` has:
- `name` — unique identifier (e.g., `D2_900x2100_FIRE_RATED`)
- `ifc_class` — element type (IfcDoor, IfcLightFixture, etc.)
- `width_mm`, `depth_mm`, `height_mm` — bounding box dimensions
- `geometry_blob` — LOD400 triangulated mesh (binary)
- `orientation` — placement orientation hint

### BOM Recipes Route Components to Rooms

```sql
-- Route a new door type to bathroom rooms
INSERT INTO ad_bom (name, space_type, description) VALUES ('BATHROOM_DOOR', 'BATHROOM', 'Fire-rated bathroom door');
INSERT INTO ad_bom_child (bom_name, component_name, qty, placement_rule)
VALUES ('BATHROOM_DOOR', 'D2_900x2100_FIRE_RATED', 1, 'DOOR_SOUTH');
```

---

## CLI Scaffolding Tool (Not Yet Built)

The original spec proposed a `addon-scaffold` CLI tool to automate the metadata insertion steps above. This is still valuable but now better understood:

### What It Would Do

```bash
# Scaffold a new building variant from IFC
bim-scaffold building \
  --reference /path/to/new.ifc \
  --building-type CLINIC_STANDARD \
  --output examples/clinic.bim

# Scaffold a new space type
bim-scaffold spacetype \
  --name PRAYER_HALL \
  --category HABITABLE \
  --template enclosed-room
```

### Generated Output

```
bim-scaffold building --reference clinic.ifc --building-type CLINIC_STANDARD

Generated:
  reference/rosetta/Clinic_extracted.db          ← Reference extraction
  migration/clinic_placement.sql                 ← 2,340 placement rows
  migration/clinic_metadata.sql                  ← Slab/floor/wall type entries
  examples/clinic.bim                            ← DSL manifest

Next steps:
  1. Run: sqlite3 library/component_library.db < migration/clinic_placement.sql
  2. Run: mvn compile -q
  3. Run: mvn exec:java -Dexec.mainClass="..." -q
  4. Verify: python3 tools/spatial_checker.py output/clinic.db reference/rosetta/Clinic_extracted.db --positional
```

### Implementation Priority

The extraction pipeline (`placement_extractor.py`, `extract.py`) already works. The scaffolding tool would wrap it into a single command. Priority: **after the 3 Rosetta Stones reach 100% across all metrics** (signature + positional + connections).

---

## 51 AD Tables (Current Metadata Schema)

The compiler reads from these metadata tables at compile time. No table requires Java code changes to extend — INSERT new rows and recompile.

| Category | Tables | Purpose |
|----------|--------|---------|
| **Placement** | `ad_element_placement`, `ad_placement_rule` | Element positions from reference IFC |
| **Space** | `ad_space_type`, `ad_space_dim`, `ad_space_adjacency`, `ad_space_exterior_rule`, `ad_space_type_alias` | Room type definitions and constraints |
| **Assembly** | `ad_assembly_manifest`, `ad_assembly_connector`, `ad_room_slot` | Prefab assembly hierarchy |
| **MEP** | `ad_space_type_mep`, `ad_space_type_mep_bom`, `ad_element_mep`, `ad_mep_profile` | MEP system rules per room type |
| **Structural** | `ad_column_type`, `ad_column_type_rule`, `ad_beam_type`, `ad_beam_type_rule` | Column/beam selection rules |
| **Walls** | `ad_wall_type`, `ad_wall_type_rule` | Wall construction assemblies |
| **Openings** | `ad_opening_family`, `ad_space_type_opening` | Door/window families and defaults |
| **BOM** | `ad_bom`, `ad_bom_child`, `ad_bom_child_param`, `ad_building_bom` | Bill of materials recipes |
| **Building** | `ad_building_template`, `ad_building_storey`, `ad_unit_type`, `ad_unit_type_room` | Building/unit structure templates |
| **Floors** | `ad_floor_type`, `ad_floor_type_rule`, `ad_slab_spec`, `ad_covering_type` | Floor/slab construction |
| **Fire** | `ad_fire_compartment`, `ad_fire_riser_requirement`, `ad_fp_coverage`, `ad_fp_trigger` | Fire protection rules |
| **Furniture** | `ad_space_type_furniture` | Furniture placement rules |
| **Codes** | `ad_building_code`, `ad_code_requirement`, `ad_jurisdiction_codes`, `ad_check_applicability`, `ad_check_threshold` | Building code compliance |
| **Circulation** | `ad_stair_requirement`, `ad_elevator_requirement`, `ad_egress_travel`, `ad_vert_circ_trigger`, `ad_pressurization_trigger`, `ad_railing_type` | Vertical circulation rules |
| **Reference** | `ad_reference`, `ad_ref_value`, `ad_product_dim` | Source references and product data |

---

## Success Criteria (Revised)

| Metric | Target | Current |
|--------|--------|---------|
| New building variant from IFC | < 30 minutes end-to-end | Achieved (manual pipeline) |
| Positional fidelity of replica | 100% (<50mm) | Achieved (3/3 stones) |
| Cross-discipline coverage | ARC + MEP + STR | Achieved (Phase CD-1) |
| New space type addition | < 2 hours (with scaffolding) | ~4 hours (manual) |
| Component library extensible | Extract from any IFC | Achieved |
| CLI scaffolding tool | Single command | Not yet built |

---

## Dependencies (Revised)

- [x] Phase B2: Placement determinism (element positions from metadata)
- [x] Phase B3: Exact fidelity (100% positional all 3 stones)
- [x] Phase CD-1: Cross-discipline emission (MEP + STR from metadata)
- [ ] Tier 3: Connection scoring (wall-on-slab, door-in-wall relationships)
- [ ] **Scaffolding CLI tool** — wrap existing pipeline into single command
- [ ] **Test on new building type** (4th stone — Mosque, Clinic, or Office)
