# BIM Compiler Enrichment Plan

**Based on:** IFC Sample Extraction (2026-02-01)
**Principle:** EXTRACT, DON'T IMAGINE

---

## 1. SUMMARY OF EXTRACTED VALUE

### 1.1 High-Value LOD400 Sources

| File | LOD | Value | Key Extractions |
|------|-----|-------|-----------------|
| `Ifc4_WallElementedCase.ifc` | **LOD400** | **GOLD** | Timber stud wall: STUD, PLATE, assembly hierarchy |
| `aisc_sculpture_param.ifc` | **LOD400** | **GOLD** | Steel fasteners: bolt naming `D=3/4 L=2 Shop A325N` |
| `aisc_sculpture_brep.ifc` | **LOD400** | HIGH | Steel rebar: IFCREINFORCINGBAR with SWEPTDISKSOLID |
| `Building-Structural.ifc` | LOD350 | MEDIUM | Footing, wall quantities, material patterns |

### 1.2 Supporting Samples (LOD200-350)

| Category | Files | Use Case |
|----------|-------|----------|
| Spatial hierarchy | All buildingSMART samples | Validate PROJECT→SITE→BUILDING→STOREY→SPACE DAG |
| MEP patterns | Building-Hvac, Infra-Plumbing | FLOWSEGMENT/FITTING/TERMINAL graph |
| Relationship types | All samples | 16 IFCREL* patterns extracted |
| Property sets | All samples | Pset_*Common canonical names |

---

## 2. ENRICHMENT TARGETS BY PHASE

### Phase 0: Model Archaeology Additions

Add to extraction queries:

```sql
-- New: Extract member predefined types (STUD, PLATE, etc.)
SELECT predefined_type, COUNT(*)
FROM elements
WHERE ifc_class = 'IFCMEMBER'
GROUP BY predefined_type;

-- New: Extract assembly types
SELECT predefined_type, COUNT(*)
FROM elements
WHERE ifc_class = 'IFCELEMENTASSEMBLY'
GROUP BY predefined_type;

-- New: Extract material profiles
SELECT profile_type, material_name
FROM material_profiles;
```

### Phase 1: Topology Dictionary Additions

#### 2.1 New BIMObjectType Entries

```java
// From Ifc4_WallElementedCase.ifc - EXTRACTED
IFCWALLELEMENTEDCASE,      // Aggregate wall (no geometry)
IFCELEMENTASSEMBLY,        // Frame assembly container
IFCMEMBER_STUD,            // Stud with .STUD. predefined type
IFCMEMBER_PLATE,           // Plate with .PLATE. predefined type
IFCBUILDINGELEMENTPART,    // Panel/sheathing

// From NIST steel files - EXTRACTED
IFCMECHANICALFASTENER,     // Bolts, screws
IFCREINFORCINGBAR,         // Rebar

// From MEP samples - EXTRACTED
IFCFLOWSEGMENT,            // Pipes, ducts
IFCFLOWFITTING,            // Elbows, tees
IFCFLOWTERMINAL,           // Fixtures
IFCDISTRIBUTIONPORT,       // Connection points
```

#### 2.2 New Dependency Rules

```java
// TIMBER STUD WALL HIERARCHY - from Ifc4_WallElementedCase.ifc
new DependencyRule(
    IFCMEMBER_STUD,
    IFCELEMENTASSEMBLY,
    MUST_BE_PART_OF,
    "Stud must be part of frame assembly"
),
new DependencyRule(
    IFCMEMBER_PLATE,
    IFCELEMENTASSEMBLY,
    MUST_BE_PART_OF,
    "Plate must be part of frame assembly"
),
new DependencyRule(
    IFCELEMENTASSEMBLY,
    IFCWALLELEMENTEDCASE,
    MUST_BE_PART_OF,
    "Frame assembly must be part of elemented wall"
),
new DependencyRule(
    IFCBUILDINGELEMENTPART,
    IFCWALLELEMENTEDCASE,
    MUST_BE_PART_OF,
    "Panel must be part of elemented wall"
),

// FASTENER RULES - from aisc_sculpture_param.ifc
new DependencyRule(
    IFCMECHANICALFASTENER,
    IFCMEMBER,
    MUST_CONNECT,
    "Fastener must connect members"
),
```

#### 2.3 New Interfaces

```java
// IMemberStud.java - from Ifc4_WallElementedCase.ifc
public interface IMemberStud extends IBIMObject {
    IMaterialProfile getMaterialProfile();  // 2x4 = 1.5" x 3.5"
    double getLength();                      // stud height
    IElementAssembly getParentAssembly();
}

// IMechanicalFastener.java - from aisc_sculpture_param.ifc
public interface IMechanicalFastener extends IBIMObject {
    double getNominalDiameter();  // e.g., 0.75 (3/4")
    double getNominalLength();    // e.g., 2.0
    String getGrade();            // e.g., "A325N"
    InstallLocation getInstall(); // SHOP or SITE
}

// IMaterialProfile.java - from Ifc4_WallElementedCase.ifc
public interface IMaterialProfile {
    String getMaterialName();     // "Southern Pine"
    IProfile getProfile();        // RectangleProfile(1.5, 3.5)
}
```

### Phase 2: Geometry Additions

```java
// New profile types - from samples
public sealed interface Profile permits
    RectangleProfile,      // 542 in samples - studs, plates
    CircleProfile,         // 72 - pipes, rebar
    IShapeProfile,         // 24 - steel beams
    LShapeProfile,         // 30 - angles
    ArbitraryClosedProfile // 1543 - custom
{}

public record RectangleProfile(double xDim, double yDim) implements Profile {
    // Common lumber: 2x4 = (1.5, 3.5), 2x6 = (1.5, 5.5), 2x10 = (1.5, 9.25)
}

// Geometry representation types
public enum GeometryType {
    EXTRUDED_AREA_SOLID,    // 2,237 - walls, slabs, studs
    SWEPT_DISK_SOLID,       // 3,680 - rebar, pipes
    FACETED_BREP,           // 4,832 - complex shapes
    TRIANGULATED_FACESET,   // 217 - mesh
    MAPPED_ITEM             // 4,122 - instanced geometry
}
```

### Phase 3: Validation Additions

```java
// Stud spacing validator - from NZS 3604 / extracted patterns
public class StudSpacingValidator implements Validator<IElementAssembly> {
    // Standard spacings: 400mm (16") or 600mm (24") centers
    // Extracted from Ifc4_WallElementedCase.ifc stud placements
}

// Fastener validator - from aisc_sculpture_param.ifc
public class FastenerValidator implements Validator<IMechanicalFastener> {
    // Validate D/L ratio, grade compatibility
    // Pattern: "D=3/4 L=2 Shop A325N"
}

// Material profile validator
public class MaterialProfileValidator implements Validator<IMemberStud> {
    // Validate profile dimensions match standard lumber
    // 2x4 = 1.5 x 3.5, 2x6 = 1.5 x 5.5, etc.
}
```

### Phase 4: Builder Additions

**DAG Order for Timber Wall:**
```
1. IMaterialProfile (no dependencies)
2. IMemberStud (depends on IMaterialProfile)
3. IMemberPlate (depends on IMaterialProfile)
4. IElementAssembly (depends on IMemberStud, IMemberPlate)
5. IBuildingElementPart (depends on IMaterialLayer)
6. IWallElementedCase (depends on IElementAssembly, IBuildingElementPart)
```

---

## 3. ROOF TRUSS GAP HANDLING

### Current Status
- No LOD400 truss IFC sample found
- Truss geometry is **parametric** (span × pitch → members)
- MiTek/Pryda patterns documented in `lod400-assemblies-residential-research.md`

### Recommended Approach: Option B (Parametric + Caveat)

```java
// RoofTrussAssembly.java
public interface IRoofTrussAssembly extends IElementAssembly {
    double getSpan();
    double getPitch();
    double getSpacing();

    // Members calculated from parameters
    List<IMember> getTopChords();
    List<IMember> getBottomChord();
    List<IMember> getWebs();

    // Source tracking
    default DataSource getSource() {
        return DataSource.RESEARCHED;  // Not EXTRACTED
    }
}

// In WitnessSystem
if (assembly.getSource() == RESEARCHED) {
    witness.addCaveat(
        "Geometry derived from engineering tables, not fabrication IFC"
    );
}
```

### Upgrade Path
When LOD400 truss IFC becomes available:
1. Extract actual member positions
2. Compare with parametric calculation
3. Upgrade source marker to EXTRACTED
4. Remove caveat from witness

---

## 4. SOURCE CLASSIFICATION SYSTEM

Add to compiler vocabulary:

```java
public enum DataSource {
    TERMINAL_EXTRACTED,    // From federated DB (highest trust)
    IFC_EXTRACTED,         // From IFC samples (high trust)
    RESEARCHED,            // From standards/specs (medium trust)
    INFERRED               // Calculated (requires validation)
}

// Usage in SESSION_STATE.md tracking
// STUD_WALL: IFC_EXTRACTED (Ifc4_WallElementedCase.ifc)
// ROOF_TRUSS: RESEARCHED (MiTek specification)
// FASTENER: IFC_EXTRACTED (aisc_sculpture_param.ifc)
```

---

## 5. IMPLEMENTATION ORDER

### Sprint 1: Vocabulary Foundation
- [ ] Add new BIMObjectType entries
- [ ] Add DependencyRule entries for timber hierarchy
- [ ] Create IMemberStud, IMemberPlate interfaces
- [ ] Create IMaterialProfile interface

### Sprint 2: Geometry Support
- [ ] Add Profile sealed interface hierarchy
- [ ] Add RectangleProfile record
- [ ] Add MappedItem support (for instanced geometry)

### Sprint 3: LOD400 Validators
- [ ] StudSpacingValidator
- [ ] FastenerValidator (bolt naming pattern)
- [ ] MaterialProfileValidator (lumber dimensions)

### Sprint 4: Builders
- [ ] MaterialProfileBuilder
- [ ] MemberStudBuilder
- [ ] ElementAssemblyBuilder (frame)
- [ ] WallElementedCaseBuilder

### Sprint 5: Gap Resolution
- [ ] Implement parametric RoofTrussAssembly with RESEARCHED marker
- [ ] Add WitnessSystem caveat for non-extracted sources
- [ ] Document upgrade path for future LOD400 truss IFC

---

## 6. FILES TO UPDATE

| File | Changes |
|------|---------|
| `BIMObjectType.java` | Add 10+ new enum values |
| `DependencyRule.java` | Add timber hierarchy rules |
| `TopologyRules.java` | Add new rules list |
| `Profile.java` | New sealed interface |
| `IMemberStud.java` | New interface |
| `IMechanicalFastener.java` | New interface |
| `SESSION_STATE.md` | Track source classification |
| `ANALYSIS_FOR_BIM_COMPILER.md` | Reference document (done) |

---

## 7. CHECKPOINT GATES

Each sprint requires HUMAN CHECKPOINT before proceeding:

1. **Vocabulary Review**: Verify enum values match IFC exactly
2. **Rule Review**: Verify dependency rules extracted, not invented
3. **Interface Review**: Verify getters match actual IFC attributes
4. **Validator Review**: Verify thresholds from standards, not assumed
5. **Builder Review**: Verify DAG order correct

---

## APPROVAL REQUESTED

This plan enriches the compiler with:
- **10+ new entity types** (EXTRACTED)
- **8+ new dependency rules** (EXTRACTED)
- **4+ new interfaces** (EXTRACTED)
- **1 parametric assembly** (RESEARCHED, marked)

All additions traceable to specific IFC files or documented standards.

**Proceed with Sprint 1?**
