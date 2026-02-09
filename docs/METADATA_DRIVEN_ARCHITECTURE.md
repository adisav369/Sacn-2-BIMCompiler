# Metadata-Driven BIM Architecture

> **SUPERSEDED** by `docs/ARCHITECTURE.md` (v3.0, 2026-02-08).
> The AD pattern is now proven and documented in Section 2 of the new document.
> The MetadataElementWriter proposal remains a valid migration target.
> Retained for detailed design reference.

**Status:** ~~Design Proposal~~ Historical (superseded — AD pattern now proven)
**Version:** 0.2
**Date:** 2026-02-04
**Inspired By:** iDempiere Application Dictionary (AD) Pattern
**Builds On:**
- `bim-dsl-dictionary.md` - Element type specifications (50% implemented per gap-analysis)
- `gap-analysis-dictionary-vs-implementation.md` - Implementation status
- `contract-architecture-specification.md` - Interface contracts
- `component_library.db` - LOD400 geometry source

---

## Executive Summary

The `bim-dsl-dictionary.md` already specifies 30+ space types, 20+ fixtures, and 15+ MEP components. However, these are implemented as **hardcoded Java enums and methods** rather than queryable metadata. This proposal converts the dictionary specification into a runtime Application Dictionary (AD) that drives the compiler.

**Key Insight from gap-analysis:** "Overall Implementation Coverage: ~50%" - the dictionary exists but code doesn't read from it dynamically.

---

## Problem Statement

Current `BuildingWriter.java` has:
- **20+ specialized write methods** (`writeLight`, `writeDoor`, `writeFixture`, etc.)
- **Hardcoded element knowledge** (IFC class mappings, placement rules, clearances)
- **Bug-prone growth** - each new element type requires new code
- **Pattern violations** - different methods can implement patterns differently

This violates the iDempiere principle: **"Change data, not code."**

---

## Proposed Solution: Metadata-Driven Element Writing

### Core Concept

Replace hardcoded element handlers with a **single generic writer** that reads all behavior from metadata:

```
BEFORE (Code-Driven):
  writeDoor() → hardcoded IfcDoor, clearances, placement
  writeLight() → hardcoded IfcLightFixture, height rules
  writeToilet() → hardcoded IfcSanitaryTerminal, IPC clearances

AFTER (Metadata-Driven):
  writeElement(metadata) → reads ALL behavior from AD tables
```

### Application Dictionary Tables

Extend `component_library.db` (or create `bim_dictionary.db`) with:

#### 1. AD_ElementType (Master Definition)

```sql
CREATE TABLE ad_element_type (
    element_type_id   TEXT PRIMARY KEY,  -- 'DOOR', 'LIGHT', 'TOILET'
    ifc_class         TEXT NOT NULL,     -- 'IfcDoor', 'IfcLightFixture'
    discipline        TEXT NOT NULL,     -- 'ARC', 'ELEC', 'SP'
    geometry_pattern  TEXT NOT NULL,     -- 'BOX', 'CYLINDER', 'LIBRARY'

    -- Placement contract
    host_type         TEXT,              -- 'WALL', 'CEILING', 'FLOOR', NULL
    attachment_face   TEXT,              -- 'TOP', 'BOTTOM', 'SIDE', 'CENTER'
    requires_opening  INTEGER DEFAULT 0, -- 1 = creates opening in host

    -- Validation rules (JSON or separate table)
    min_clearance_front  REAL,           -- meters
    min_clearance_side   REAL,
    min_clearance_above  REAL,

    -- Code references
    code_reference    TEXT,              -- 'IPC 405.3.1', 'IRC R311.7'

    is_active         INTEGER DEFAULT 1
);
```

**Sample Data:**
```sql
INSERT INTO ad_element_type VALUES
('DOOR', 'IfcDoor', 'ARC', 'LIBRARY', 'WALL', 'CENTER', 1, 0.9, 0.0, 2.1, 'IRC R311.2'),
('LIGHT', 'IfcLightFixture', 'ELEC', 'BOX', 'CEILING', 'BOTTOM', 0, 0.0, 0.0, 0.0, NULL),
('TOILET', 'IfcSanitaryTerminal', 'SP', 'LIBRARY', 'FLOOR', 'TOP', 0, 0.533, 0.381, 0.0, 'IPC 405.3.1'),
('OUTLET', 'IfcOutlet', 'ELEC', 'BOX', 'WALL', 'SIDE', 0, 0.0, 0.0, 0.0, NULL),
('COLUMN', 'IfcColumn', 'STR', 'BOX', NULL, NULL, 0, 0.0, 0.0, 0.0, 'Eurocode 2');
```

#### 2. AD_ElementType_LOD (LOD Variants)

```sql
CREATE TABLE ad_element_type_lod (
    element_type_id   TEXT,
    lod_level         INTEGER,           -- 100, 200, 300, 400
    geometry_hash     TEXT,              -- Reference to base_geometries
    local_bounds      TEXT,              -- JSON: {width, depth, height}

    -- LOD-specific properties
    has_detail_geometry INTEGER DEFAULT 0,
    material_layers   TEXT,              -- JSON array

    PRIMARY KEY (element_type_id, lod_level),
    FOREIGN KEY (element_type_id) REFERENCES ad_element_type(element_type_id)
);
```

#### 3. AD_ElementType_Param (Configurable Parameters)

```sql
CREATE TABLE ad_element_type_param (
    element_type_id   TEXT,
    param_name        TEXT,              -- 'width', 'height', 'fire_rating'
    param_type        TEXT,              -- 'REAL', 'INTEGER', 'TEXT', 'BOOLEAN'
    default_value     TEXT,
    min_value         REAL,
    max_value         REAL,
    unit              TEXT,              -- 'mm', 'm', 'degrees'
    is_required       INTEGER DEFAULT 0,

    PRIMARY KEY (element_type_id, param_name)
);
```

#### 4. AD_Placement_Rule (Spatial Rules)

```sql
CREATE TABLE ad_placement_rule (
    rule_id           TEXT PRIMARY KEY,
    element_type_id   TEXT,
    rule_type         TEXT,              -- 'MUST_BE_IN_SPACE', 'MUST_ATTACH_TO', 'MIN_DISTANCE'
    target_type       TEXT,              -- 'BATHROOM', 'WALL', 'COLUMN'
    rule_value        REAL,              -- Distance or ratio
    rule_unit         TEXT,
    error_message     TEXT,

    FOREIGN KEY (element_type_id) REFERENCES ad_element_type(element_type_id)
);
```

---

## Generic Writer Implementation

### Single Entry Point

```java
/**
 * Metadata-driven element writer.
 * All element behavior comes from AD tables - no hardcoded types.
 */
public class MetadataElementWriter {

    private final Connection adConn;     // AD metadata connection
    private final Connection outConn;    // Output DB connection

    /**
     * Write ANY element using metadata definition.
     *
     * @param elementTypeId  AD element type ('DOOR', 'LIGHT', etc.)
     * @param params         Element-specific parameters
     * @param worldBounds    World-space bounding box
     * @param storeyName     Containing storey
     */
    public void writeElement(
            String elementTypeId,
            Map<String, Object> params,
            BoundingBox worldBounds,
            String storeyName) throws SQLException {

        // 1. Load element type definition from AD
        ElementTypeDef def = loadElementType(elementTypeId);

        // 2. Validate parameters against AD schema
        validateParams(def, params);

        // 3. Validate placement rules
        validatePlacement(def, worldBounds, storeyName);

        // 4. Generate geometry based on pattern
        String geoHash = generateGeometry(def, worldBounds, params);

        // 5. Write to output DB (Pattern B enforced)
        String guid = generateGuid(def, storeyName, params);
        writeElementMeta(guid, def.ifcClass(), ...);
        writeInstance(guid, geoHash);  // Always (0,0,0) transform
    }

    private String generateGeometry(ElementTypeDef def, BoundingBox bounds,
                                    Map<String, Object> params) {
        return switch (def.geometryPattern()) {
            case "BOX" -> createBoxGeometry(bounds);
            case "CYLINDER" -> createCylinderGeometry(bounds, params);
            case "LIBRARY" -> getLibraryGeometry(def, bounds, params);
            default -> createBoxGeometry(bounds);  // Fallback
        };
    }

    private String getLibraryGeometry(ElementTypeDef def, BoundingBox bounds,
                                      Map<String, Object> params) {
        // Try to find LOD400 geometry
        var lod400 = loadLOD(def.elementTypeId(), 400);
        if (lod400 != null) {
            // Transform library geometry to world-space
            return transformToWorldSpace(lod400.geometryHash(), bounds);
        }

        // Fallback to lower LOD or parametric
        var lod300 = loadLOD(def.elementTypeId(), 300);
        if (lod300 != null) {
            return transformToWorldSpace(lod300.geometryHash(), bounds);
        }

        // Ultimate fallback: parametric box
        return createBoxGeometry(bounds);
    }
}
```

### Usage Example

```java
// BEFORE: Hardcoded method per type
writeLight(lightSpec, storeyName);
writeDoor(doorSpec, storeyName);
writeToilet(fixtureSpec, storeyName);

// AFTER: Single metadata-driven method
writer.writeElement("LIGHT", Map.of(
    "x", 5.0, "y", 3.0, "z", 2.8,
    "width", 0.3, "depth", 0.3, "height", 0.1
), bounds, "Ground");

writer.writeElement("DOOR", Map.of(
    "width", 0.9, "height", 2.1,
    "wall", "south", "fire_rating", "FD30"
), bounds, "Ground");

writer.writeElement("TOILET", Map.of(
    "model", "close_coupled",
    "accessible", false
), bounds, "Ground");
```

---

## Benefits

### 1. Zero Code Changes for New Elements

```sql
-- Add new element type: Smoke Detector
INSERT INTO ad_element_type VALUES
('SMOKE_DETECTOR', 'IfcSensor', 'ELEC', 'LIBRARY', 'CEILING', 'BOTTOM', 0,
 0.0, 0.0, 0.0, 'NFPA 72');

-- That's it! No Java code changes needed.
```

### 2. Consistent Contract Enforcement

- Pattern B enforced in ONE place (`writeInstance`)
- Placement validation from metadata, not scattered in code
- All elements go through same pipeline

### 3. BOM Generation from Metadata

```sql
-- BOM query becomes generic
SELECT
    et.element_type_id,
    et.ifc_class,
    COUNT(*) as quantity,
    ep.default_value as specification
FROM elements_meta em
JOIN ad_element_type et ON em.ifc_class = et.ifc_class
LEFT JOIN ad_element_type_param ep ON et.element_type_id = ep.element_type_id
GROUP BY et.element_type_id;
```

### 4. LOD Flexibility

```
Request LOD400 → Use library geometry if available
No LOD400?     → Fall back to LOD300
No LOD300?     → Fall back to parametric (current behavior)
```

### 5. Code Compliance from Data

```sql
-- Update code reference without code change
UPDATE ad_element_type
SET code_reference = 'MS 1184:2014',
    min_clearance_front = 1.219  -- Accessible toilet clearance
WHERE element_type_id = 'TOILET_ACCESSIBLE';
```

---

## Bridging from bim-dsl-dictionary.md

The existing dictionary already specifies element types in YAML-like format. This must be converted to AD tables:

### Current Dictionary Format (bim-dsl-dictionary.md)

```yaml
SPACETYPE BATHROOM:
  wall_rule: ENCLOSED
  min_area: 3.5
  natural_light: optional
  fixtures:
    - TOILET: required
    - SINK: required
    - SHOWER: optional
  mep:
    - exhaust_fan: required
    - floor_drain: required
```

### Converted to AD Tables

```sql
-- ad_element_type
INSERT INTO ad_element_type VALUES
('BATHROOM', 'IfcSpace', 'ARC', 'BOX', NULL, NULL, 0, NULL, NULL, NULL, 'UBBL 1984');

-- ad_element_type_param
INSERT INTO ad_element_type_param VALUES
('BATHROOM', 'wall_rule', 'TEXT', 'ENCLOSED', NULL, NULL, NULL, 1),
('BATHROOM', 'min_area', 'REAL', '3.5', 3.5, NULL, 'm²', 1),
('BATHROOM', 'natural_light', 'TEXT', 'optional', NULL, NULL, NULL, 0);

-- ad_placement_rule (fixtures)
INSERT INTO ad_placement_rule VALUES
('BATH_TOILET', 'BATHROOM', 'MUST_CONTAIN', 'TOILET', NULL, NULL, 'Bathroom requires toilet'),
('BATH_SINK', 'BATHROOM', 'MUST_CONTAIN', 'SINK', NULL, NULL, 'Bathroom requires sink'),
('BATH_SHOWER', 'BATHROOM', 'MAY_CONTAIN', 'SHOWER', NULL, NULL, 'Shower is optional');

-- ad_placement_rule (MEP)
INSERT INTO ad_placement_rule VALUES
('BATH_EXHAUST', 'BATHROOM', 'MUST_CONTAIN', 'EXHAUST_FAN', NULL, NULL, 'Bathroom requires exhaust'),
('BATH_DRAIN', 'BATHROOM', 'MUST_CONTAIN', 'FLOOR_DRAIN', NULL, NULL, 'Bathroom requires floor drain');
```

### Dictionary → AD Conversion Script

A one-time script to convert `bim-dsl-dictionary.md` to AD tables:

```python
# scripts/convert_dictionary_to_ad.py
def convert_spacetype(name, spec):
    """Convert dictionary SPACETYPE to AD tables."""
    rows = []

    # Main element type
    rows.append(f"INSERT INTO ad_element_type VALUES ('{name}', 'IfcSpace', 'ARC', 'BOX', ...);")

    # Parameters
    for param, value in spec.items():
        if param not in ['fixtures', 'mep']:
            rows.append(f"INSERT INTO ad_element_type_param VALUES ('{name}', '{param}', ...);")

    # Fixture requirements
    for fixture in spec.get('fixtures', []):
        rows.append(f"INSERT INTO ad_placement_rule VALUES ('{name}_{fixture}', ...);")

    return rows
```

---

## Migration Path

### Phase 1: Create AD Tables (Week 1)
1. Create `ad_element_type` and populate from current hardcoded values
2. Create `ad_element_type_param` from current Spec classes
3. Create `ad_placement_rule` from current validation code

### Phase 2: Create Generic Writer (Week 2)
1. Implement `MetadataElementWriter` class
2. Keep existing write methods as adapters (call generic writer)
3. Verify output matches current behavior

### Phase 3: Migrate Element Types (Week 3-4)
1. Migrate one element type at a time to metadata-driven
2. Remove hardcoded method after verification
3. Add new element types via metadata only

### Phase 4: LOD Integration (Week 5)
1. Populate `ad_element_type_lod` from component_library.db
2. Implement geometry transformation (local → world)
3. Enable LOD fallback chain

---

## Relationship to Existing Code

### What Stays
- `component_library.db` - becomes LOD geometry source
- `BuildingCompiler.java` - orchestration (calls generic writer)
- Contract interfaces (IBIMEntity, IGeometryValidatable)

### What Goes
- All `write<Type>()` methods in BuildingWriter
- Hardcoded IFC class mappings
- Hardcoded clearance values
- Type-specific validation code

### What's New
- `bim_dictionary.db` or extended `component_library.db`
- `MetadataElementWriter` class
- `ElementTypeDef`, `PlacementRule` records
- AD table query utilities

---

## iDempiere Parallel

| iDempiere Concept | BIM Equivalent |
|-------------------|----------------|
| AD_Table | ad_element_type |
| AD_Column | ad_element_type_param |
| AD_Val_Rule | ad_placement_rule |
| AD_Reference | ad_element_type_lod |
| ModelValidator | IGeometryValidatable |
| PO (Persistent Object) | ElementSpec |

---

## Success Criteria

1. **Zero hardcoded element types** in BuildingWriter
2. **New element via SQL only** - no Java changes
3. **LOD fallback working** - 400 → 300 → parametric
4. **All existing tests pass** with metadata-driven writer
5. **BOM generation** uses AD metadata
6. **Code compliance** queryable from AD

---

## Open Questions

1. **Single DB or split?**
   - Option A: Extend `component_library.db` with AD tables
   - Option B: Separate `bim_dictionary.db` for AD, link to library

2. **Versioning?**
   - How to handle AD changes over time?
   - Migration strategy for existing projects?

3. **Performance?**
   - Cache AD lookups during compilation?
   - Pre-load element types at startup?

---

## References

- iDempiere Application Dictionary: https://wiki.idempiere.org/en/Application_Dictionary
- IFC Schema: https://standards.buildingsmart.org/IFC/
- Current component_library.db schema
- BuildingWriter.java (to be replaced)

---

**END OF DESIGN DOCUMENT**
