# LOD 400 Integration Verification & Assembly Documentation

## Summary Report

**Date:** 2026-01-30
**Task:** Verify LOD400 integration and document assembly patterns

---

## Key Findings

### 1. TB-LKTN ↔ LOD400 Connection Status: CONNECTED (Phase 29)

| Component | Connected? | Details |
|-----------|------------|---------|
| Wall Assemblies | YES | 15 WALL_PANEL assemblies with FRAME + CLADDING |
| Doors | **YES** | 4 doors using LOD400 geometry (FD1: 452 vertices, 872 faces) |
| Windows | NO | Parametric boxes (TERMINAL has commercial windows, not residential) |
| Door Assemblies | **YES** | DOOR_ASSEMBLY with LEAF + HARDWARE (hinges, handle for BOM) |
| Component Library | ACTIVE | DoorWindowLibraryMapper routes to library |

### 2. What We Have (Extracted)

**Generated Databases:**
- `shed_test.db`: 4 WALL_PANEL assemblies (26 elements)
- `tb_lktn.db`: 15 WALL_PANEL assemblies (88 elements)

**Component Library (from TERMINAL):**
- 8,698 component definitions
- IfcDoor: 112 definitions
- IfcWindow: 183 definitions
- IfcFireSuppressionTerminal: 891 (used by SprinklerPlacer)
- IfcColumn/Beam/Member: 908 (used by StructuralPlacer)

**TERMINAL Note:**
- No `element_assemblies` table in TERMINAL database
- Components extracted individually, not as assemblies

### 3. What We Researched (Standards)

| Assembly Type | Standards | Components |
|---------------|-----------|------------|
| STUD_WALL | AS 1684, NZS 3604, IRC R602 | Plates, studs, noggings |
| ROOF_TRUSS | MiTek, Pryda guides | Chords, webs, nail plates |
| DOOR_ASSEMBLY | Industry specs | Frame, leaf, hinges, hardware |
| WINDOW_ASSEMBLY | Industry specs | Frame, sash, glazing, hardware |
| FLOOR_JOIST | Simpson Strong-Tie | Bearers, joists, hangers |

---

## Output Files Created

| File | Content |
|------|---------|
| `docs/lod400-integration-status.md` | Part 1: TB-LKTN connection analysis |
| `docs/lod400-assemblies-extracted.md` | Part 2: Inventory of what we have |
| `docs/lod400-assemblies-residential-research.md` | Part 3: Researched patterns |
| `docs/bim-dsl-dictionary.md` (Section 25) | Part 4: Assembly vocabulary |

---

## Answer to Key Question

**Is TB-LKTN connected to LOD400 library?**

**PARTIALLY.**

| What Works | What Doesn't |
|------------|--------------|
| WALL_PANEL assemblies created | Doors are parametric boxes |
| Assembly schema inherited from Shed | Windows are parametric boxes |
| Components (frame, cladding) generated | HybridFactory not used |
| Sprinklers use library | Schedule types resolve to dimensions only |

---

## Reconnection Completed (Phase 29)

### What Was Done:

#### 1. Created DoorWindowLibraryMapper.java
```java
// Maps SCHEDULE types to library components
var mapping = libraryMapper.mapDoor(widthMm, heightMm, "D1");
if (mapping.usesLibrary()) {
    // Use LOD400 geometry from library
    copyGeometryToOutput(conn, mapping.component().geometryHash());
}
```

#### 2. Modified BuildingWriter.java
```java
// Now routes doors through library lookup
if (libraryMapper != null) {
    var mapping = libraryMapper.mapDoor(widthMm, heightMm, "D?");
    if (mapping.usesLibrary()) {
        writeLibraryDoor(door, doorGuid, storeyName, mapping.component());
        return;
    }
}
// Fallback to parametric
```

#### 3. Door Assembly Structure
```sql
-- DOOR_ASSEMBLY now created for BOM:
DOOR_ASSEMBLY|LEAF|IfcDoor|optional=0|4 doors
DOOR_ASSEMBLY|HANDLE_SET||optional=1|4 handles (BOM)
DOOR_ASSEMBLY|HINGE_100MM||optional=1|12 hinges (BOM)
```

### Verification Results
```
=== LOD400 Library Usage Summary ===
Doors:   4 library, 0 parametric
Windows: 0 library, 7 parametric
Status: CONNECTED (doors using LOD400 geometry)

Door geometry: 452 vertices, 872 faces (real LOD400 door)
```

---

## Status Markers

- ✓ **EXTRACTED**: From TERMINAL/Shed/TB-LKTN databases
- ◐ **RESEARCHED**: From published standards (AS/NZS/IRC, manufacturers)
- ○ **PENDING**: Needs residential IFC extraction

---

## Next Steps (Priority Order)

1. ~~**HIGH**: Route doors through library~~ ✓ DONE (Phase 29)
2. ~~**HIGH**: Create DOOR_ASSEMBLY definitions~~ ✓ DONE (Phase 29)
3. **MEDIUM**: Connect SCHEDULE types (D1/D2/D3) to specific doors in DSL
4. **MEDIUM**: Acquire residential window IFC (TERMINAL has commercial only)
5. **MEDIUM**: Acquire residential IFC for STUD_WALL, ROOF_TRUSS extraction
6. **LOW**: Add fastener/hardware schedules to component library

---

## Verification Queries

```sql
-- Check TB-LKTN assemblies
SELECT assembly_type, COUNT(*) FROM element_assemblies GROUP BY assembly_type;
-- Result: WALL_PANEL | 15

-- Check component library
SELECT ct.ifc_class, COUNT(cd.id) FROM component_types ct
LEFT JOIN component_definitions cd ON cd.type_id = ct.id
WHERE ct.ifc_class IN ('IfcDoor', 'IfcWindow')
GROUP BY ct.ifc_class;
-- Result: IfcDoor | 112, IfcWindow | 183

-- Check if doors use library
SELECT element_name, COUNT(*) FROM elements_meta WHERE ifc_class = 'IfcDoor' GROUP BY element_name;
-- Result: "Entry Door" (parametric, not library)
```

---

## Compliance with PRIME RULE

This analysis follows "Extract, don't imagine":

1. **Extracted** what we have from databases (TERMINAL, Shed, TB-LKTN)
2. **Researched** published standards for patterns we don't have
3. **Marked** what needs future extraction (residential IFC)

No patterns were invented - all documented from verified sources.

---

*Report generated 2026-01-30*
