# IFC Gap Sources — Files to Acquire

**Date:** 2026-03-30
**Purpose:** Track IFC files and non-IFC sources needed to expand the Rosetta Stone fleet.
**Existing IFC folder:** `DAGCompiler/lib/input/IFC/` (50+ files)

---

## Already Have (in IFC folder)

- Clinic (WBDG) — Architecture, Electrical, HVAC, Plumbing, Structural (IFC 2x3)
- Schependomlaan — Multi-unit apartments (IFC 2x3)
- BIM Whale — Basic/Advanced/Large/Tall (IFC 2x3/4)
- PCERT — Building ARC/HVAC/STR + Infrastructure Bridge/Plumbing/Rail/Road (IFC4X3)

## To Acquire — IFC Files

### HIGH Priority (new domains, download and extract)

| Source | URL | Domain | Schema | Notes |
|--------|-----|--------|--------|-------|
| **BIMData Hospital** (6 files) | github.com/bimdata/BIMData-Research-and-Development | Healthcare | IFC2x3+IFC4 | ARC+ELEC+FP+PLB+SPR+STR. Best multi-discipline hospital. |
| **WBDG Office** | wbdg.org/bim/cobie/common-bim-files | Commercial office | IFC2x3 | 2-storey, COBie data included |
| **NBU_OfficeBuilding** | BIMData collection | Multi-storey office | IFC2x3+IFC4 | ARC+STR+HVAC+ELE. Closest to high-rise |
| **bSI IFC4.3.x-sample-models** | github.com/buildingSMART/IFC4.3.x-sample-models | Marine/Infra | IFC4X3 | Check for IfcMarineFacility examples |

### MEDIUM Priority

| Source | URL | Domain | Schema | Notes |
|--------|-----|--------|--------|-------|
| WBDG Barracks | wbdg.org/bim/cobie/common-bim-files | Military residential | IFC2x3 | COBie data |
| SimAUD merged IFC | simaud.org/datasets | Academic/Education | IFC2x3 | ARC+MEP, extends beyond AC11 |
| youshengCode/IfcSampleFiles | github.com/youshengCode/IfcSampleFiles | Mixed Revit exports | IFC4 | |
| bSI Community-Sample-Test-Files | github.com/buildingsmart-community | Community-contributed | Mixed | |
| OpenIFC (Auckland, ~130 models) | openifcmodel.cs.auckland.ac.nz | Mixed | Mixed | Hospital set included |

### NOT Available (must be authored or sourced from partners)

| Domain | Status | Path Forward |
|--------|--------|-------------|
| Industrial / Warehouse | Zero public IFC | Author in Bonsai, or ResPlan converter for layouts |
| Data Centre | Zero public IFC | Author in Bonsai with raised floor + rack products |
| Retail / Shopping Mall | Zero public IFC | Author or industry partner |
| Stadium / Sports Venue | Zero public IFC | Industry partner only |
| Mixed-use / Podium Tower | Zero public IFC | Author or industry partner |

**Key insight:** 5 of 9 gap domains have ZERO publicly available IFC files. The public IFC ecosystem is heavily residential/institutional. Complex commercial/industrial models are always proprietary.

## To Acquire — Non-IFC Sources

| Source | URL | Domain | Format | Conversion |
|--------|-----|--------|--------|------------|
| **ResPlan** (17K plans) | github.com/m-agour/ResPlan | Residential | Vector SVG + graph | Script → _extracted.db |
| **Swiss Dwellings (MSD)** | data.4tu.nl (search "Swiss Dwellings") | Apartments | Vector | Script → _extracted.db |
| **Cloud2BIM** | github.com/VaclavNezerka/Cloud2BIM | Any (point cloud) | LAS/LAZ → IFC | Pipeline tool |
| **OCX Schema** | github.com/OCXStandard/OCX_Schema | Marine hulls | XSD (Apache 2.0) | extractOCXtoDB.py |
| **OCX Parser** | github.com/OCXStandard/ocx-schema-parser | Marine hulls | Python | Parser library |
| **FREEship** | sourceforge.net/projects/freeship | Hull geometry | STEP/IGES/DXF | Test hull gen |
| **CubiCasa5k** | github.com/CubiCasa/CubiCasa5k | Floor plan segmentation | Raster + SVG | ML model |

## Product Catalogs for component_library.db

| Source | URL | Domain | Products | Format |
|--------|-----|--------|----------|--------|
| BIMobject racking | bimobject.com (search "pallet racking") | Industrial | ~50 | IFC download |
| DigiPara elevators | digipara.com | High-rise | ~30 | IFC 4.0 |
| Kingspan raised floors | source.thenbs.com (search "Kingspan") | Data centre | ~20 | NBS structured |
| Legrand cable trays | bimobject.com (search "Legrand P31") | Data centre | ~30 | RFA/IFC |
| Schuco facade | source.thenbs.com (search "Schuco") | High-rise | ~25 | NBS structured |
| Rittal server racks | bimobject.com (search "Rittal") | Data centre | ~15 | IFC |
| Herman Miller healthcare | hermanmiller.com/resources/3d-models | Healthcare | ~40 | RFA |

## Marine Standards (manual entry)

| Standard | Content | Rows | Effort |
|----------|---------|------|--------|
| EN 10067 | Bulb flat profiles HP80-HP430 | ~25 | 1 hour |
| DIN 536 | Crane rail profiles A45-A150 | 8 | 30 min |
| JFE Steel catalog | Angles + flat bars | ~40 | 2 hours |
