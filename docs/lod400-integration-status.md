# LOD 400 Integration Status

## Part 1: TB-LKTN ↔ LOD400 Connection Analysis

**Date:** 2026-01-30
**Status:** PARTIALLY CONNECTED

---

## Summary

| Feature | Connected? | Details |
|---------|------------|---------|
| Wall Assemblies | YES | 15 WALL_PANEL assemblies created |
| Doors | NO | Parametric (not from library) |
| Windows | NO | Parametric (not from library) |
| HybridFactory | NO | BuildingWriter uses direct generation |
| Component Library | UNUSED | 112 doors, 183 windows available |

---

## 1. What TB-LKTN Creates

### Assemblies Created (15 total)
```sql
SELECT assembly_type, COUNT(*) FROM element_assemblies GROUP BY assembly_type;
-- WALL_PANEL | 15
```

### Assembly Structure
| Assembly | Type | Components |
|----------|------|------------|
| SOUTH_WALL_ASSEMBLY | WALL_PANEL | CLADDING(1) + FRAME(4) |
| NORTH_WALL_ASSEMBLY | WALL_PANEL | CLADDING(1) + FRAME(4) |
| WEST_WALL_ASSEMBLY | WALL_PANEL | CLADDING(1) + FRAME(4) |
| EAST_WALL_ASSEMBLY | WALL_PANEL | CLADDING(1) + FRAME(4) |
| INTERIOR_*_WALL_ASSEMBLY | WALL_PANEL | CLADDING(1) + FRAME(4) |

### Components by IFC Class
```
IfcMember  | 60  (wall framing)
IfcPlate   | 15  (wall cladding)
IfcWindow  | 7   (parametric)
IfcDoor    | 4   (parametric)
IfcSlab    | 1
IfcRoof    | 1
```

---

## 2. What's NOT Connected

### Doors (PARAMETRIC - NOT LIBRARY)
```sql
SELECT ifc_class, element_name, COUNT(*) FROM elements_meta
WHERE ifc_class = 'IfcDoor' GROUP BY element_name;
-- IfcDoor | Entry Door | 4
```
- Created with hardcoded dimensions in BuildingCompiler
- NOT resolved from component_library.db
- Schedule types (D1, D2, D3) resolve to dimensions only, not geometry

### Windows (PARAMETRIC - NOT LIBRARY)
```sql
SELECT ifc_class, element_name, COUNT(*) FROM elements_meta
WHERE ifc_class = 'IfcWindow' GROUP BY element_name;
-- IfcWindow | Standard Window | 7
```
- Same issue - parametric boxes, not library geometry

### HybridFactory Not Used
```java
// BuildingWriter.java does NOT import HybridFactory
// Doors/windows generated inline, not through factory
```

---

## 3. Component Library Contents

```sql
SELECT ct.ifc_class, COUNT(cd.id) FROM component_types ct
LEFT JOIN component_definitions cd ON cd.type_id = ct.id
WHERE ct.ifc_class IN ('IfcDoor', 'IfcWindow')
GROUP BY ct.ifc_class;
```

| IFC Class | Library Instances |
|-----------|-------------------|
| IfcDoor | 112 |
| IfcWindow | 183 |

**Library has real LOD400 geometry from TERMINAL extraction but it's not being used!**

---

## 4. What WORKS (from Phase 11 Shed)

Shed correctly creates assemblies:
```
EAST_WALL_ASSEMBLY  | WALL_PANEL | 5 components
NORTH_WALL_ASSEMBLY | WALL_PANEL | 5 components
SOUTH_WALL_ASSEMBLY | WALL_PANEL | 5 components
WEST_WALL_ASSEMBLY  | WALL_PANEL | 5 components
```

TB-LKTN inherited the WALL_PANEL pattern correctly.

---

## 5. Reconnection Requirements

### To connect doors/windows to library:

1. **Modify BuildingWriter.java** to use HybridFactory:
   ```java
   // Current: inline parametric door
   writeDoor(guid, "Entry Door", bbox);

   // Needed: factory lookup
   var door = hybridFactory.createDoor(width, height);
   writeLibraryComponent(door);
   ```

2. **Match SCHEDULE types to library:**
   - D1 (900x2100) → find matching IfcDoor in library
   - D2 (750x2100) → find matching IfcDoor
   - W1 (1800x1000) → find matching IfcWindow
   - W3 (600x500) → find matching IfcWindow

3. **Add door/window assemblies:**
   - DOOR_ASSEMBLY: frame + leaf + hardware
   - WINDOW_ASSEMBLY: frame + sash + glazing

---

## 6. Conclusion

**Status: PARTIALLY CONNECTED**

| Component | Phase 11 (Shed) | Phase 28 (TB-LKTN) |
|-----------|-----------------|-------------------|
| Wall assemblies | YES | YES (inherited) |
| Door library | NOT IMPL | NOT IMPL |
| Window library | NOT IMPL | NOT IMPL |
| HybridFactory | Available | Not used |

**Priority Fix:** Route doors/windows through HybridFactory to use LOD400 library geometry.

---

*Analysis based on tb_lktn.db, shed_test.db, and component_library.db queries*
