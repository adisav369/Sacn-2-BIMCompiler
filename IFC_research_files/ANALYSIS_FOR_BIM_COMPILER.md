# IFC Sample Analysis for BIM Compiler

**Purpose:** Extract vocabulary, patterns, and LOD400 elements from IFC samples to enrich the BIM compiler's topology dictionary and validation rules.

**Philosophy:** EXTRACT, DON'T IMAGINE — everything below comes directly from the sample files.

---

## 1. VOCABULARY ENRICHMENT

### 1.1 Core Entity Types (BIMObjectType Enum Candidates)

From actual file analysis, ranked by frequency:

#### Spatial Hierarchy
```java
IFCPROJECT          // 32 instances - root
IFCSITE             // 56 instances - geographic context
IFCBUILDING         // 723 instances - building container
IFCBUILDINGSTOREY   // 63 instances - floor/level
IFCSPACE            // 187 instances - room/area
IFCZONE             // 1 instance - grouping
```

#### Building Elements (Primary)
```java
IFCWALL              // 755 - generic wall
IFCWALLSTANDARDCASE  // 367 - standard wall with material layers
IFCWALLELEMENTEDCASE // 1 - elemented wall (studs, panels)
IFCSLAB              // 419 - floor/roof slab
IFCBEAM              // 616 - beam
IFCCOLUMN            // 89 - column
IFCMEMBER            // 347 - generic structural member
IFCPLATE             // 186 - plate (steel)
IFCFOOTING           // 19 - foundation
IFCROOF              // 7 - roof
IFCSTAIR             // 16 - stair
IFCSTAIRFLIGHT       // 5 - stair run
IFCRAILING           // 108 - railing
IFCCURTAINWALL       // 11 - curtain wall
IFCCOVERING          // 1280 - ceiling/cladding/insulation
```

#### Openings & Furnishings
```java
IFCDOOR              // 252
IFCWINDOW            // 334
IFCFURNISHINGELEMENT // (in type definitions)
```

#### MEP Elements
```java
IFCFLOWSEGMENT       // 487 - pipes, ducts
IFCFLOWFITTING       // 358 - elbows, tees
IFCFLOWTERMINAL      // 121 - fixtures, outlets
IFCFLOWCONTROLLER    // 14 - valves, dampers
IFCFLOWMOVINGDEVICE  // 4 - pumps, fans
IFCDISTRIBUTIONPORT  // 79 - connection points
```

#### LOD400 Fabrication Elements
```java
IFCREINFORCINGBAR       // 3680 - rebar
IFCMECHANICALFASTENER   // 392 - bolts, screws
IFCDISCRETEACCESSORY    // 2 - shims, connectors
```

#### Infrastructure (IFC4X3)
```java
IFCROAD           // 1
IFCFACILITYPART   // 54
IFCPAVEMENT       // 39
```

### 1.2 Type Definitions

Element types define reusable configurations:
```java
IFCWALLTYPE          // 52
IFCSLABTYPE          // 124
IFCBEAMTYPE          // 144
IFCCOLUMNTYPE        // 31
IFCMEMBERTYPE        // 136
IFCPLATETYPE         // 122
IFCFOOTINGTYPE       // 7
IFCPIPESEGMENTTYPE   // 409
IFCPIPEFITTINGTYPE   // 59
```

---

## 2. RELATIONSHIP PATTERNS (TopologyRules)

### 2.1 Relationship Types by Frequency

```java
IFCRELDEFINESBYPROPERTIES       // 43,239 - property assignment
IFCRELASSOCIATESMATERIAL        // 3,841 - material assignment
IFCRELSPACEBOUNDARY             // 2,029 - space boundaries
IFCRELDEFINESBYTYPE             // 1,513 - type assignment
IFCRELCONNECTSPATHELEMENTS      // 760 - wall connections
IFCRELASSOCIATESDOCUMENT        // 400 - document links
IFCRELAGGREGATES                // 331 - spatial containment
IFCRELVOIDSELEMENT              // 219 - openings in elements
IFCRELFILLSELEMENT              // 187 - elements filling voids
IFCRELCONTAINEDINSPATIALSTRUCTURE // 178 - element placement
IFCRELASSIGNSTOGROUP            // 150 - group assignment
IFCRELASSOCIATESCLASSIFICATION  // 96 - classification
IFCRELCONNECTSELEMENTS          // 55 - element connections
IFCRELNESTS                     // 39 - nesting (parts)
IFCRELCONNECTSPORTTOELEMENT     // 19 - MEP port connections
```

### 2.2 Extracted Dependency Rules

From the samples, these parent-child patterns emerge:

```java
// SPATIAL HIERARCHY (MUST_BE_CONTAINED_BY)
IFCSITE             → IFCPROJECT
IFCBUILDING         → IFCSITE
IFCBUILDINGSTOREY   → IFCBUILDING
IFCSPACE            → IFCBUILDINGSTOREY

// ELEMENT CONTAINMENT (IFCRELCONTAINEDINSPATIALSTRUCTURE)
IFCWALL             → IFCBUILDINGSTOREY
IFCSLAB             → IFCBUILDINGSTOREY
IFCBEAM             → IFCBUILDINGSTOREY
IFCCOLUMN           → IFCBUILDINGSTOREY
IFCDOOR             → IFCBUILDINGSTOREY
IFCWINDOW           → IFCBUILDINGSTOREY

// HOSTING (IFCRELVOIDSELEMENT + IFCRELFILLSELEMENT)
IFCDOOR             → IFCWALL (via void)
IFCWINDOW           → IFCWALL (via void)

// CONNECTION (IFCRELCONNECTSPATHELEMENTS)
IFCWALL             ↔ IFCWALL (path connection)

// TYPE DEFINITION (IFCRELDEFINESBYTYPE)
IFCWALL             → IFCWALLTYPE
IFCSLAB             → IFCSLABTYPE
IFCBEAM             → IFCBEAMTYPE
```

---

## 3. PROPERTY SETS (Pset_*Common)

Standard property sets extracted from samples:

### 3.1 Element Common Properties
```
Pset_WallCommon          // 1,098 occurrences
  - IsExternal: BOOLEAN
  - LoadBearing: BOOLEAN
  - Status: ENUM (NEW, EXISTING, DEMOLISH, TEMPORARY)
  - FireRating: LABEL (e.g., "REI60")
  - AcousticRating: LABEL (e.g., "29dB Rw")
  - SurfaceSpreadOfFlame: LABEL (e.g., "A2 s1 d0")

Pset_SlabCommon          // 656
Pset_BeamCommon          // 668
Pset_ColumnCommon        // 70
Pset_MemberCommon        // 248
Pset_PlateCommon         // 72
Pset_DoorCommon          // 265
Pset_WindowCommon        // 341
Pset_SpaceCommon         // 177
Pset_BuildingCommon      // (via IFCBUILDING)
```

### 3.2 LOD400 Property Sets
```
Pset_ReinforcingBarCommon          // 3,760
  - (rebar diameter, grade, coating)

Pset_ReinforcementBarPitchOfBeam   // 431
Pset_ReinforcementBarPitchOfSlab   // 150
Pset_ReinforcementBarPitchOfWall   // 81
Pset_ReinforcementBarPitchOfColumn // 43

Pset_ElementComponentCommon        // 3,760
Pset_EnvironmentalImpactIndicators // 5,031 (EPD data)
```

### 3.3 Revit-Specific (for import compatibility)
```
PSet_Revit_Constraints
PSet_Revit_Dimensions
PSet_Revit_Phasing
PSet_Revit_Identity Data
PSet_Revit_Mechanical
```

---

## 4. QUANTITY TAKEOFFS (Qto_*)

Base quantities for cost estimation:

```java
IFCQUANTITYLENGTH   // 85,827 - Length, Width, Height, Perimeter
IFCQUANTITYAREA     // 44,449 - NetArea, GrossArea, NetSideArea
IFCQUANTITYVOLUME   // 23,171 - NetVolume, GrossVolume
IFCQUANTITYCOUNT    // 7,313  - Count (fasteners, etc.)
```

Example from sample:
```
Qto_WallBaseQuantities:
  - NetVolume: 4.286 m³
  - Width: 200 mm
  - Length: 5200 mm
  - NetSideArea: 21.43 m²
```

---

## 5. GEOMETRY PATTERNS

### 5.1 Representation Types
```java
IFCEXTRUDEDAREASOLID     // 2,237 - extruded profiles (walls, slabs)
IFCFACETEDBREP           // 4,832 - faceted B-Rep
IFCSWEPTDISKSOLID        // 3,680 - swept disk (rebar, pipes)
IFCTRIANGULATEDFACESET   // 217 - mesh/tessellation
IFCBOOLEANCLIPPINGRESULT // 41 - boolean operations
```

### 5.2 Profile Types (for structural members)
```java
IFCRECTANGLEPROFILEDEF         // 542 - rectangular
IFCARBITRARYCLOSEDPROFILEDEF   // 1,543 - custom profiles
IFCCIRCLEPROFILEDEF            // 72 - circular
IFCLSHAPEPROFILEDEF            // 30 - L-angle
IFCISHAPEPROFILEDEF            // 24 - I-beam/wide flange
IFCTSHAPEPROFILEDEF            // 8 - T-section
IFCUSHAPEPROFILEDEF            // 8 - channel
IFCRECTANGLEHOLLOWPROFILEDEF   // 6 - HSS
IFCCIRCLEHOLLOWPROFILEDEF      // 2 - pipe
```

### 5.3 Geometry Coordinates
```java
IFCCARTESIANPOINT         // 413,224
IFCDIRECTION              // 20,602
IFCAXIS2PLACEMENT3D       // 23,027
IFCLOCALPLACEMENT         // 11,728
```

---

## 6. LOD400 (FABRICATION LEVEL) PATTERNS

### 6.1 Mechanical Fasteners (Bolts)
From `aisc_sculpture_param.ifc`:
```
IFCMECHANICALFASTENER(
  GlobalId,
  OwnerHistory,
  Name='D=3/4 L=2 Shop A325N',  // D=diameter, L=length, grade
  Description='Bolt',
  ObjectType='Bolt',
  LocalPlacement,
  Representation,
  $,
  NominalDiameter=0.75,  // inches
  NominalLength=2.0      // inches
)
```

**Pattern:** Bolt naming convention includes diameter, length, shop/site install, grade.

### 6.2 Reinforcing Bars
Standard pattern for rebar:
```
IFCREINFORCINGBAR(
  GlobalId,
  OwnerHistory,
  Name,
  Description,
  ObjectType,
  LocalPlacement,
  Representation,          // IFCSWEPTDISKSOLID
  Tag,
  SteelGrade,
  NominalDiameter,         // bar size
  CrossSectionArea,
  BarLength,
  BarSurface=.PLAIN./.TEXTURED.
)
```

### 6.3 Material Layers (Wall Assembly)
```
IFCMATERIALLAYERSETUSAGE  // 543 occurrences
IFCMATERIALLAYERSET       // 81
IFCMATERIALLAYER          // 170
  - Material
  - LayerThickness
  - IsVentilated
  - Name (e.g., "Gypsum Board", "Insulation", "Brick")
```

### 6.4 Connection Details
```
IFCCONNECTIONSURFACEGEOMETRY  // 2,335 - surface connections
IFCRELCONNECTSPATHELEMENTS    // 760 - wall path connections
  - RelatingConnectionType
  - RelatedConnectionType
  - ConnectionGeometry
```

---

## 7. MATERIAL PATTERNS

### 7.1 Material Types
```java
IFCMATERIAL                  // 381 - simple material
IFCMATERIALLIST              // 216 - material list
IFCMATERIALLAYERSET          // 81 - layered assembly
IFCMATERIALCONSTITUENTSET    // 131 - composite
IFCMATERIALPROFILESET        // 6 - profile with material
```

### 7.2 Material Names (from samples)
```
concrete_reinforced_in-situ
stone_sand-lime
steel
wood
gypsum_board
insulation
brick
glass
```

---

## 8. CLASSIFICATION SYSTEMS

From samples:
```
IFCCLASSIFICATION:
  - 'Molio' / 'CCI Construction' / v1.0
  - Reference: 'E-AAA' (Single-family house)

IFCCLASSIFICATIONREFERENCE:
  - Links to external classification URIs
  - e.g., https://identifier.buildingsmart.org/uri/molio/cciconstruction/1.0
```

---

## 9. RECOMMENDATIONS FOR BIM COMPILER

### 9.1 Phase 0 Enhancements (Model Archaeology)

Add these queries to extract from federated DB:

```sql
-- Extract all IFC types
SELECT ifc_class, COUNT(*) FROM elements GROUP BY 1 ORDER BY 2 DESC;

-- Extract property sets
SELECT DISTINCT pset_name FROM property_sets;

-- Extract relationship types
SELECT rel_type, COUNT(*) FROM relationships GROUP BY 1;

-- Extract material assignments
SELECT material_name, COUNT(*) FROM materials GROUP BY 1;
```

### 9.2 Phase 1 Enhancements (Topology Dictionary)

**New Interfaces from IFC patterns:**

```java
// LOD400 interfaces
public interface IReinforcingBar extends IBIMObject {
    double getNominalDiameter();
    double getBarLength();
    String getSteelGrade();
    BarSurface getSurface();
}

public interface IMechanicalFastener extends IBIMObject {
    double getNominalDiameter();
    double getNominalLength();
    String getGrade();        // A325N, A490, etc.
    InstallLocation getInstall(); // SHOP, SITE
}

// Material assembly
public interface IMaterialLayerSet {
    List<IMaterialLayer> getLayers();
    double getTotalThickness();
}
```

**Dependency Rules to Add:**

```java
// From IFCRELCONTAINEDINSPATIALSTRUCTURE
new DependencyRule(IFC_WALL, IFC_BUILDING_STOREY, MUST_BE_CONTAINED_BY),
new DependencyRule(IFC_SLAB, IFC_BUILDING_STOREY, MUST_BE_CONTAINED_BY),

// From IFCRELVOIDSELEMENT + IFCRELFILLSELEMENT
new DependencyRule(IFC_DOOR, IFC_WALL, MUST_HOST),
new DependencyRule(IFC_WINDOW, IFC_WALL, MUST_HOST),

// From IFCRELDEFINESBYTYPE
new DependencyRule(IFC_WALL, IFC_WALL_TYPE, MUST_HAVE_TYPE),

// From IFCRELCONNECTSPATHELEMENTS
new ConnectionRule(IFC_WALL, IFC_WALL, CAN_CONNECT_PATH),
```

### 9.3 Phase 2 Enhancements (Geometry)

**From IFC patterns:**

```java
public record Point3D(double x, double y, double z) {}
public record Direction3D(double dx, double dy, double dz) {}
public record Axis2Placement3D(Point3D location, Direction3D axis, Direction3D refDirection) {}
public record LocalPlacement(Axis2Placement3D relativePlacement, Optional<LocalPlacement> parent) {}

// Profile definitions
public sealed interface Profile permits RectangleProfile, CircleProfile, IShapeProfile, LShapeProfile {}
public record RectangleProfile(double xDim, double yDim) implements Profile {}
public record IShapeProfile(double overallWidth, double overallDepth, double webThickness, double flangeThickness) implements Profile {}
```

### 9.4 Phase 3 Enhancements (Validation)

**New validators from IFC patterns:**

```java
// Spatial containment
public class SpatialContainmentValidator {
    // Every WALL must be in a BUILDINGSTOREY
    // Every BUILDINGSTOREY must be in a BUILDING
}

// Material assignment
public class MaterialAssignmentValidator {
    // LoadBearing elements must have material
    // Exterior elements should have thermal properties
}

// Quantity consistency
public class QuantityValidator {
    // NetVolume = Area × Height (for walls)
    // Verify quantities match geometry
}
```

---

## 10. KEY INSIGHTS

1. **IFC is relationship-heavy** — 43,239 property relationships vs 755 walls. The compiler must prioritize relationship extraction.

2. **Type/Instance separation** — Elements reference types. Your `IFCRELDEFINESBYTYPE` pattern should be core.

3. **LOD400 = fasteners + rebar** — 3,680 rebar + 392 fasteners in samples. Steel detailing is well-represented.

4. **Property sets are standardized** — `Pset_*Common` patterns are consistent. Map these to interfaces.

5. **Quantities are pre-calculated** — IFC includes takeoff quantities. Your compiler can validate these against geometry.

6. **Material layers matter** — Wall assemblies have layers. This affects thermal/acoustic calculations.

7. **Spatial hierarchy is strict** — Project → Site → Building → Storey → Space. Validate this DAG.
