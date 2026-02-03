# IFC Research Files Collection

This folder contains IFC (Industry Foundation Classes) sample files collected from various authoritative sources for BIM compiler/dictionary development.

## Summary Statistics

| Source | Files | Size | IFC Versions |
|--------|-------|------|--------------|
| youshengCode_samples | 7 | 93 MB | IFC2x3, IFC4 |
| buildingsmart_samples | 9 | 8.0 MB | IFC4 |
| steptools_samples | 5 | 4.4 MB | IFC2x3, IFC4, IFC4x3 |
| bim_whale_samples | 2 | 51 MB | IFC4 |
| **Total** | **23** | **~156 MB** | Multiple |

## File Inventory

### youshengCode_samples/
Official sample files for software testing:
- `Ifc2x3_Duplex_Architecture.ifc` (2.3 MB) - Duplex house architecture model
- `Ifc2x3_Duplex_MEP.ifc` (17.9 MB) - Duplex MEP (Mechanical, Electrical, Plumbing)
- `Ifc2x3_SampleCastle.ifc` (49.3 MB) - Complex castle model
- `Ifc4_SampleHouse.ifc` (2.3 MB) - Sample house in IFC4
- `Ifc4_Revit_ARC.ifc` (13.6 MB) - Revit architecture export
- `Ifc4_Revit_STR.ifc` (11.3 MB) - Revit structural export
- `Ifc4_WallElementedCase.ifc` (25 KB) - Simple wall with elements (good for parsing study)

### buildingsmart_samples/
Official buildingSMART PCERT certification samples (IFC4):
- `Building-Architecture.ifc` (226 KB) - Architecture discipline
- `Building-Hvac.ifc` (180 KB) - HVAC systems
- `Building-Landscaping.ifc` (1.4 MB) - Landscaping elements
- `Building-Structural.ifc` (297 KB) - Structural components
- `Infra-Bridge.ifc` (1.9 MB) - Infrastructure: Bridge
- `Infra-Landscaping.ifc` (3.1 MB) - Infrastructure: Landscaping
- `Infra-Plumbing.ifc` (525 KB) - Infrastructure: Plumbing
- `Infra-Rail.ifc` (245 KB) - Infrastructure: Rail
- `Infra-Road.ifc` (439 KB) - Infrastructure: Road

### steptools_samples/
STEP Tools technical samples:
- `AC20-FZK-Haus.ifc` (2.5 MB) - KIT sample house (IFC4)
- `Tabel_Chairs.ifc` (721 KB) - Furniture (Autodesk, IFC4)
- `aisc_sculpture_brep.ifc` (554 KB) - NIST steel brep solids (IFC2x3)
- `aisc_sculpture_param.ifc` (316 KB) - NIST steel parametric (IFC2x3)
- `KIT-Simple-Road-Test-Web-IFC4x3_RC2.ifc` (388 KB) - Road test (IFC4x3)

### bim_whale_samples/
BIM Whale project samples (designed for easy parsing):
- `BasicHouse.ifc` (52.7 MB) - Complete house model
- `SimpleWall.ifc` (40 KB) - Simple wall element

## IFC File Structure Overview

IFC files follow the STEP (ISO 10303-21) format:

```
ISO-10303-21;
HEADER;
  FILE_DESCRIPTION((...), '2;1');
  FILE_NAME('filename.ifc', 'timestamp', ('author'), ('org'), ...);
  FILE_SCHEMA(('IFC4'));  // or IFC2X3, IFC4X3_ADD2
ENDSEC;

DATA;
  #1= IFCPROJECT(...);
  #2= IFCSITE(...);
  ...
ENDSEC;
END-ISO-10303-21;
```

## Key IFC Entity Types for BIM Dictionary

### Spatial Structure
- `IFCPROJECT` - Root element, contains units and context
- `IFCSITE` - Geographic site
- `IFCBUILDING` - Building container
- `IFCBUILDINGSTOREY` - Floor/storey level
- `IFCSPACE` - Room or area

### Building Elements
- `IFCWALL`, `IFCWALLSTANDARDCASE` - Walls
- `IFCSLAB` - Floors, roofs
- `IFCBEAM` - Beams
- `IFCCOLUMN` - Columns
- `IFCDOOR`, `IFCWINDOW` - Openings
- `IFCSTAIR`, `IFCRAMP` - Circulation
- `IFCROOF` - Roof elements

### MEP Elements
- `IFCDUCT`, `IFCPIPE` - Distribution
- `IFCFLOWSEGMENT` - Pipes, ducts
- `IFCFLOWTERMINAL` - Fixtures, outlets

### Geometry
- `IFCEXTRUDEDAREASOLID` - Swept solids
- `IFCFACETEDBREP` - Faceted B-Rep
- `IFCTRIANGULATEDFACESET` - Triangulated mesh
- `IFCCARTESIANPOINT`, `IFCDIRECTION` - Points/vectors

### Properties & Quantities
- `IFCPROPERTYSET` - Property collections
- `IFCPROPERTYSINGLEVALUE` - Single values
- `IFCELEMENTQUANTITY` - Quantities (area, volume, etc.)
- `IFCMATERIAL` - Materials

### Relationships
- `IFCRELAGGREGATES` - Spatial containment
- `IFCRELCONTAINEDINSPATIALSTRUCTURE` - Element placement
- `IFCRELDEFINESBYTYPE` - Type definitions
- `IFCRELASSOCIATESMATERIAL` - Material assignment

## Sources

- [youshengCode/IfcSampleFiles](https://github.com/youshengCode/IfcSampleFiles) - MIT License
- [buildingSMART/Sample-Test-Files](https://github.com/buildingSMART/Sample-Test-Files) - Official samples
- [STEP Tools Sample Files](https://www.steptools.com/docs/stpfiles/ifc/) - Technical samples
- [BIM Whale IFC Samples](https://github.com/andrewisen/bim-whale-ifc-samples) - MIT License
- [buildingSMART IFC Specification](https://technical.buildingsmart.org/standards/ifc/)
