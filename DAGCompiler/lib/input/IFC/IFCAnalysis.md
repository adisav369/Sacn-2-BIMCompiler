# IFC Model Inventory & Quality Analysis

**Generated:** 2026-03-20 | **Location:** `DAGCompiler/lib/input/IFC/`

---

## 1. In-Pipeline Models (have classify YAML)

These models have Rosetta Stone YAMLs and run through the pipeline today.

| Alias | File | Schema | Size | Entities | Storeys | Walls | Doors | Windows | Slabs | PSets | Rels | Quality |
|-------|------|--------|------|----------|---------|-------|-------|---------|-------|-------|------|---------|
| **SH** | Ifc4_SampleHouse.ifc | IFC4 | 2.3 MB | 47,309 | 2 | — | — | — | — | 1,259 | 518 | A |
| **DX-Arch** | Ifc2x3_Duplex_Architecture.ifc | IFC2X3 | 2.4 MB | 38,898 | 4 | 56 | — | — | — | 6,672 | 2,068 | A |
| **DX-MEP** | Ifc2x3_Duplex_MEP.ifc | IFC2X3 | 17.9 MB | 311,480 | 3 | — | — | — | — | 34,567 | 7,031 | A |
| **DX-Fed** | Ifc2x3_Duplex_Federated.ifc | IFC2X3 | 51.0 MB | 973,208 | 4 | — | 427 seg | — | — | 40,216 | 8,486 | A |
| **TE-ACMV** | SJTII-ACMV-A-TER1-00-R0-Clean.ifc | IFC4 | 63.3 MB | 534,994 | 7 | — | — | — | — | 3,404 | 7,818 | A |
| **TE-CW** | SJTII-CW-A-TER1-00-R0-Clean.ifc | IFC4 | 25.5 MB | 251,795 | 9 | — | — | — | — | 4,145 | 7,558 | A |
| **TE-ELEC** | SJTII-ELEC-A-TER1-00-R0-Clean.ifc | IFC4 | 3.5 MB | 39,127 | 13 | — | — | — | — | 2,388 | 4,468 | A |
| **TE-FP** | SJTII-FP-A-TER1-00-R0-Clean.ifc | IFC4 | 23.7 MB | 259,243 | 7 | — | — | — | — | 19,190 | 34,788 | A |
| **TE-LPG** | SJTII-LPG-A-TER1-00-RO-Clean.ifc | IFC4 | 1.4 MB | 14,830 | 8 | — | — | — | — | 638 | 1,158 | A |
| **TE-SP** | SJTII-SP-A-TER1-00-R0-Clean.ifc | IFC4 | 8.6 MB | 87,263 | 7 | — | — | — | — | 2,856 | 5,170 | A |
| **TE-STR** | SJTII-STR-S-TER1-00-R0-Clean.ifc | IFC4 | 4.7 MB | 56,931 | 9 | — | 614 slab | 432 beam | 312 member | 6,284 | 6,066 | A |
| **TE-Fed** | merged_federation.ifc | IFC4 | 215.9 MB | 2,325,214 | 63 | 33K plate | — | — | 705 | 110,516 | 174,473 | A |

| **FK** | FZK_Haus_IFC4.ifc | IFC4 | 2.6 MB | 44,249 | 2 | 13 wall, 11 window, 5 door, 4 slab, 4 beam, 42 member, 2 railing, 1 stair | 4,757 | 659 | A |

**Existing pipeline YAML configs:** `classify_sh.yaml`, `classify_dx.yaml`, `classify_te.yaml`, `classify_br.yaml`, `classify_rd.yaml`, `classify_rl.yaml`, `classify_dm.yaml`, `classify_fk.yaml`

---

## 2. Downloaded — Building Models (candidates for new Rosetta Stones)

| File | Source | Schema | Size | Entities | Storeys | Key Elements | PSets | Rels | Quality | Notes |
|------|--------|--------|------|----------|---------|--------------|-------|------|---------|-------|
| ~~FZK_Haus_IFC4.ifc~~ | ~~KIT~~ | ~~IFC4~~ | — | — | — | — | — | — | — | **DONE** — moved to In-Pipeline as FK (session 38b, 9/10 PASS). |
| **Smiley_West_IFC2x3.ifc** | opensourceBIM | IFC2x3* | 4.1 MB | 73,662 | — | schema parse failed (non-standard header) | 0 | 0 | **C** | Allplan export, non-standard FILE_SCHEMA. Needs header fixup before pipeline use. |
| **AC11_Institute_IFC2x3.ifc** | opensourceBIM | IFC2X3 | 2.8 MB | 49,664 | 5 | 119 wall, 206 window, 82 space, 26 slab, 253 furnishing | 2,631 | 3,611 | **A** | University building. Rich space/furnishing data, great institutional test. |
| **Vogel_Gesamt_IFC2x3.ifc** | opensourceBIM | IFC2x3* | 946 KB | 17,335 | 4 | 56 wall, 15 window, 30 opening, 24 proxy | 0 | 209 | **B** | German residential. No PSets = limited property mining. Good for geometry-only. |
| **FJK_Project_IFC2x3.ifc** | opensourceBIM | unknown | 148 KB | 2,410 | 0 | 6 proxy only | 40 | 8 | **D** | Minimal test fragment. No building structure. Not useful for pipeline. |

### Quality Rating Key
- **A** = Full IFC structure (site/building/storey), geometry, PSets, relationships — ready for pipeline
- **B** = Valid structure but missing PSets or incomplete property data
- **C** = Parseable but non-standard (header fixup needed, missing relationships)
- **D** = Fragment or test file, not useful for Rosetta Stone pipeline

---

## 3. Downloaded — Infrastructure IFC4X3 (buildingSMART PCERT)

All from `github.com/buildingSMART/Sample-Test-Files` — official IFC4X3 certification samples.

| File | Domain | Size | Entities | Key IFC4X3 Classes | PSets | Quality | Notes |
|------|--------|------|----------|--------------------|-------|---------|-------|
| **PCERT_Infra_Bridge_IFC4X3.ifc** | Bridge | 1.9 MB | 883 | 18 IfcBridgePart, 3 IfcBridge, 8 beam, 7 column, 7 footing, 4 earthworksfill, 2 railing, 4 sign | 0 | **B+** | Official bridge cert model. Good spatial structure, no PSets. Has BEAM/COLUMN/FOOTING — overlaps building vocab. |
| **PCERT_Infra_Road_IFC4X3.ifc** | Road | 417 KB | 887 | 26 IfcRoadPart, 5 IfcRoad, 16 course, 16 earthworksfill, 20 surfacefeature | 0 | **B+** | Road alignment model. New IFC4X3 entities: IfcCourse, IfcEarthworksFill, IfcSurfaceFeature. |
| **PCERT_Infra_Rail_IFC4X3.ifc** | Rail | 244 KB | 728 | 66 IfcTrackElement, 4 IfcRail, 2 IfcRailway, 2 IfcRailwayPart, 2 course | 0 | **B+** | Rail model with IfcTrackElement (highest count). New classes: IfcRail, IfcRailway, IfcCourse. |
| **PCERT_Infra_Plumbing_IFC4X3.ifc** | Plumbing | 525 KB | 440 | 24 IfcPipeSegment, 4 element assembly, 1 distribution system | 0 | **B** | Infrastructure plumbing (not building MEP). Pipe-heavy, no PSets. |
| **PCERT_Building_Architecture_IFC4X3.ifc** | Arch | 216 KB | 383 | 4 wall, 3 slab, 2 roof, 2 space, 4 proxy | 8 | **B** | Tiny IFC4X3 building. Good for schema compatibility testing, not rich enough for full Rosetta Stone. |
| **PCERT_Building_Hvac_IFC4X3.ifc** | HVAC | 176 KB | 153 | 2 air terminal, 1 chimney, 1 distribution system | 0 | **C** | Very small. Only 5 real elements. Schema test only. |
| **PCERT_Building_Structural_IFC4X3.ifc** | Structural | 286 KB | 350 | 6 beam, 4 wall, 3 proxy, 2 discrete accessory, 1 footing | 0 | **B** | Small structural model. New: IfcDiscreteAccessory. |

---

## 4. Recommended Next Rosetta Stones (priority order)

| Priority | File | Proposed Prefix | Typology | Why |
|----------|------|----------------|----------|-----|
| ~~1~~ | ~~FZK_Haus_IFC4.ifc~~ | ~~FK~~ | — | **DONE** (session 38b). 82 elements, 9/10 PASS, first timber/TILE stone. |
| **1** | AC11_Institute_IFC2x3.ifc | IN | Institutional (IFC2x3) | 50K entities, 5 storeys, 119 walls, 206 windows, 82 spaces, 253 furnishing. First institutional building — proves grammar generalises beyond residential. IFC2x3 = cross-version proof. |
| **2** | PCERT_Infra_Bridge_IFC4X3.ifc | PB | Infrastructure Bridge | Official IFC4X3 cert file. 18 BridgePart entities. Validates infra bridge rules from `DV006b`. |
| **3** | PCERT_Infra_Road_IFC4X3.ifc | PR | Infrastructure Road | Official IFC4X3. 26 RoadPart, new entity types. Validates road rules from `DV007`. |
| **4** | PCERT_Infra_Rail_IFC4X3.ifc | PL | Infrastructure Rail | Official IFC4X3. 66 TrackElement. Validates rail rules from `DV008`. |
| **5** | Vogel_Gesamt_IFC2x3.ifc | VG | Residential (German) | 17K entities, geometry-rich but no PSets. Good geometry-only stress test. |
| defer | Smiley_West_IFC2x3.ifc | SW | Commercial | Needs header fixup first — non-standard FILE_SCHEMA. 73K entities once fixed. |
| skip | FJK_Project_IFC2x3.ifc | — | — | Too minimal (6 proxies, no structure). |
| skip | PCERT_Building_Hvac_IFC4X3.ifc | — | — | Only 5 real elements. |

---

## 5. Models Requiring Manual Download

These sources require registration, login, or manual navigation. Listed with enough context to evaluate and fetch.

### 5a. High Value — Worth the Effort

| Source | URL | Access | Est. Models | What You Get | Priority |
|--------|-----|--------|-------------|--------------|----------|
| **Schependomlaan** | `wiki.osarch.org/index.php?title=Schependomlaan_IFC_model` | Free, browse to download link | 1 | Multi-storey Dutch residential, IFC4. Well-known reference model with full spatial hierarchy, property sets. One of the most-used IFC4 test files in the industry. | HIGH |
| **OSArch Community Models** | `wiki.osarch.org/index.php?title=IFC_models` | Free, individual download links | ~200 | Curated index page with direct links to models contributed by practitioners. Wide typology range: hospitals, offices, apartments, schools. Each model page describes contents and authoring tool. Browse and cherry-pick by building type. | HIGH |
| **BlenderBIM Demo Files** | `blenderbim.org/docs/users/quickstart/explore_model.html` | Free download | ~10 | BlenderBIM tutorial models — clean IFC4 with full property sets. Good authoring-tool diversity (not Revit-centric). | MEDIUM |
| **IFC.js Example Models** | `github.com/IFCjs/test-ifc-files` | GitHub, free | ~15 | Community test files used by the IFC.js viewer project. Mixed IFC2x3/IFC4. Some are real-world exports from Revit, ArchiCAD, Allplan. | MEDIUM |

### 5b. Academic / Restricted

| Source | URL | Access | Est. Models | What You Get | Priority |
|--------|-----|--------|-------------|--------------|----------|
| **TUM Open Infra Platform** | `github.com/tumcms/Open-Infra-Platform` | GitHub, free (code); models in releases | ~20 | German university infrastructure research. IFC4X3 tunnel, bridge, road models. Best source for infra typology diversity beyond PCERT. | HIGH |
| **TUM MediaTUM datasets** | `mediatum.ub.tum.de` — search "IFC" | Academic portal, free registration | ~20 | Research IFC datasets with metadata. Includes parametric models and edge cases. | MEDIUM |
| **NIBS/COBie datasets** | `nibs.org/cobie` | Membership required (free tier exists) | ~30 | US facility handover datasets. IFC + COBie spreadsheets. Useful for property/classification coverage testing, not geometry. | LOW |
| **Solibri / IFC Model Checker** | Bundled with Solibri software | Commercial license required | ~100 | Deliberately broken/edge-case IFC for validation testing. Not useful for Rosetta Stones (designed to fail). | SKIP |

### 5c. Large-Scale / Real Projects

| Source | URL | Access | What You Get |
|--------|-----|--------|--------------|
| **Autodesk Revit sample projects** | Ships with Revit installation | Revit license needed | `rac_basic_sample_project.rvt` and `rac_advanced_sample_project.rvt` — export to IFC for Revit-specific testing. Most IFC in the wild comes from Revit; testing Revit exports is critical. |
| **buildingSMART Validation Service** | `validate.buildingsmart.org` | Free account | Upload your own IFC, get MVD compliance report. Not a model source, but useful for validating downloaded models. |
| **OpenCDE testcases** | `github.com/buildingSMART/IFC4.3.x-development` | GitHub, free | IFC4.3 development test cases. Bleeding-edge schema examples including IfcTunnel, IfcMarineFacility. |

---

## 6. Entity Type Coverage Matrix

How well does our inventory cover the IFC entity spectrum?

| Entity Domain | Existing Pipeline | New Downloads | Gap |
|---------------|------------------|---------------|-----|
| IfcWall / WallStandardCase | SH, DX | FK, IN, VG | Covered well |
| IfcSlab | SH, DX, TE-STR | FK, PCERT-Arch | Covered |
| IfcBeam | TE-STR | FK, PCERT-Bridge, PCERT-Struct | Covered |
| IfcColumn | TE-STR | FK, PCERT-Bridge | Covered |
| IfcDoor | DX | FK, IN | Covered |
| IfcWindow | SH, DX | FK (22), IN (206) | Now excellent |
| IfcSpace | SH, DX-MEP | IN (82), PCERT-Arch | Improved |
| IfcFurnishing / Furniture | SH | IN (253) | Now covered |
| IfcStair / Railing | DX | FK (3 railing) | Thin — need Schependomlaan |
| IfcRoof | SH | PCERT-Arch (2) | Thin |
| IfcMember | TE-STR (312) | FK (42) | Covered |
| **IfcPipeSegment** | DX-MEP, TE-CW/SP | PCERT-Plumb (24) | Covered |
| **IfcDuctSegment/Fitting** | TE-ACMV | — | TE only |
| **IfcBridge / BridgePart** | — | PCERT-Bridge (3/18) | **NEW** |
| **IfcRoad / RoadPart** | — | PCERT-Road (5/26) | **NEW** |
| **IfcRailway / TrackElement** | — | PCERT-Rail (2/66) | **NEW** |
| **IfcCourse** | — | PCERT-Road (16), Rail (2) | **NEW** |
| **IfcEarthworksFill** | — | PCERT-Bridge (4), Road (16) | **NEW** |
| IfcCurtainWall | TE-CW | — | TE only |
| IfcPlate | TE-Fed (33K) | — | TE only |

---

## 7. File Integrity Notes

| File | Issue | Action Needed |
|------|-------|---------------|
| Smiley_West_IFC2x3.ifc | Non-standard `FILE_SCHEMA` header (Allplan export) — schema detection fails, entity parsing incomplete | Fix header line or write custom parser adapter |
| Vogel_Gesamt_IFC2x3.ifc | Non-standard `FILE_SCHEMA` header — same issue | Fix header |
| FJK_Project_IFC2x3.ifc | No `FILE_SCHEMA` detected, no building hierarchy, only 6 proxies | Delete or keep as negative test |
| PCERT_Building_Hvac_IFC4X3.ifc | Only 5 real elements, 153 total entities | Too small for Rosetta Stone, keep for IFC4X3 schema test only |
| All PCERT files | Zero property sets (PSets=0) — certification geometry-only | Property mining won't yield results; useful for entity/spatial testing only |
