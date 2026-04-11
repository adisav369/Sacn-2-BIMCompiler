# IFC Model Inventory & Quality Analysis

**Generated:** 2026-03-21 (S47 update) | **Location:** `DAGCompiler/lib/input/IFC/`

---

## 0. Federation — Merging Discipline IFCs

Real-world projects export one IFC per discipline (ARC, MEP, STR). The pipeline
needs a single federated IFC per building.

**Option A — IFC-level merge** (single merged IFC):
`tools/federation_preprocessor.py` — IfcPatch MergeProjects, GUID-preserving.
Source: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/federation_preprocessor.py`.

**WARNING:** IFC-level merge can silently drop entire disciplines. Example: Clinic's
`Clinic_Federated.ifc` contained only the ARC discipline (3,298 elements) — the entire
STR discipline (1,100 elements including 12 IfcRoof, 738 beams, 195 columns) was lost.
MergeProjects fails when spatial structures conflict or GUIDs collide across disciplines.

**DELETED (2026-04-11):** `Clinic_Federated.ifc` and `HHS_Office_Federated.ifc` removed —
both were incomplete merges that dropped disciplines. Use Option B (DB-level merge from
individual discipline IFCs in `UNMERGED/`) instead.

**Option B — DB-level merge** (preferred, avoids data loss):
`scripts/extract_merge_disciplines.py` — extracts each discipline IFC separately,
merges at DB level (row-by-row, dedup by geometry_hash). No IFC spatial tree
re-parenting, no GUID collision risk — every element from every discipline makes
it into the final DB.

> **⚠ CRITICAL — S173 Unit Scale Finding:**
> `ifcopenshell.geom.iterator()` returns ALL coordinates in **metres** regardless
> of the IFC file's native length unit (mm, feet, etc.). The iterator applies
> `unit_scale` internally. **Do NOT multiply by `unit_scale` again** — that was
> the S172 bug that caused geometry hell (ARC/STR appeared as a dot at origin
> while MEP was full-size). The old `fix_unit_scale()` post-processing step has
> been removed. This applies to vertices, transform matrices, and bounding boxes.
> Note: `create_shape()` (pre-S172) returned native IFC units — the iterator
> behaves differently.

> **⚠ CRITICAL — Material Color in Blender SOLID Mode:**
> Blender's SOLID viewport mode reads `material.diffuse_color`, NOT the Principled
> BSDF node's Base Color input. Setting only the BSDF node produces correct colors
> in Material Preview but **invisible colors in SOLID mode** (everything appears gray).
> Every material creation path must set BOTH:
> ```python
> mat.diffuse_color = rgba            # SOLID mode reads this
> bsdf.inputs["Base Color"].default_value = rgba  # Material Preview reads this
> ```
> This was fixed in the old tessellation loader (S14x) and must be replicated in any
> new loader (library linker, GN cache, etc.). The `diffuse_color` trap is silent —
> materials appear correctly created in the log but invisible in the viewport.

```bash
# Example: Clinic has 5 discipline IFCs in UNMERGED/
python3 scripts/extract_merge_disciplines.py \
    --ifc-dir DAGCompiler/lib/input/IFC/UNMERGED \
    --pattern "Clinic_*.ifc" \
    --output DAGCompiler/lib/input/Clinic_extracted.db \
    --library library/component_library.db
```

**Option C — Top-up existing DB** (add missed classes without re-extracting):
`scripts/topup_extracted_db.py` — incrementally adds elements from an IFC to an existing DB.

**Folder layout:**

```
IFC/                    ← merged/federated IFCs (pipeline reads from here)
IFC/UNMERGED/           ← raw per-discipline exports (federation input)
```

**Revit models** (3 UNMERGED IFCs: ARC, MEP, STR) are not yet merged — RM currently
runs on `Ifc4_HospitalAuckland.ifc` alone. Full federation pending (PROGRESS.md S104).

**S171/S172 — Extraction performance:**
Switched from `create_shape()` to `geom.iterator()` (v0.8 built-in C++ dedup).
Benchmark: 1.7x faster on low-reuse, more on high-reuse (Hospital 12.7x).

**Extracted DB ↔ Mesh connection:**

```
IFC file
  ↓  geom.iterator() (USE_WORLD_COORDS=False, WELD_VERTICES=True)
  ↓
  ├─ vertices (local coords, tack point origin) ──→ geometry_hash (SHA256)
  ├─ faces (triangle indices)
  └─ transformation.matrix (4x4, world placement + rotation)

geometry_hash is the KEY that connects everything:

  _extracted.db                         component_library.db
  ┌─────────────────────┐              ┌──────────────────────────┐
  │ element_instances    │              │ component_geometries     │
  │   guid ──→ geometry_hash ─────────→│   geometry_hash (PK)     │
  │                      │              │   vertices BLOB (float32)│
  │ element_transforms   │              │   faces BLOB (int32)     │
  │   guid               │              │   vertex_count, face_count│
  │   center_x/y/z       │              ├──────────────────────────┤
  │   rotation_x/y/z     │              │ component_definitions    │
  │                      │              │   geometry_hash          │
  │ base_geometries      │              │   local bounds (min/max) │
  │   geometry_hash      │              │   attachment_face        │
  │   vertices = NULL    │              │   up_axis, forward_axis  │
  │   (hash-only, S168)  │              └──────────────────────────┘
  └─────────────────────┘

  _extracted.db has the WHAT + WHERE (element identity, position, rotation)
  component_library.db has the SHAPE (canonical mesh, one per unique geometry)
  Same geometry_hash = same mesh = BOM deduplication
```

Blender loads this via `blend_cache.py`:
- Template mesh per geometry_hash → `_GN_Templates` collection
- Point mesh per discipline → vertex at center_x/y/z, attributes: instance_index, rotation
- GN `Instance on Points` picks template by index, applies Euler rotation
- S170 LOD: templates start empty, filled on demand by discipline toggle + camera distance

---

## 1. In-Pipeline Models (have classify YAML)

These models have Rosetta Stone YAMLs and run through the pipeline today.

| Alias | File | Schema | Size | Entities | Storeys | Walls | Doors | Windows | Slabs | PSets | Rels | Quality |
|-------|------|--------|------|----------|---------|-------|-------|---------|-------|-------|------|---------|
| **SH** | SampleHouse.ifc | IFC4 | 2.3 MB | 47,309 | 2 | — | — | — | — | 1,259 | 518 | A |
| **DX-Arch** | Duplex_Architecture.ifc | IFC2X3 | 2.4 MB | 38,898 | 4 | 56 | — | — | — | 6,672 | 2,068 | A |
| **DX-MEP** | Duplex_MEP.ifc | IFC2X3 | 17.9 MB | 311,480 | 3 | — | — | — | — | 34,567 | 7,031 | A |
| **DX-Fed** | Duplex_Federated.ifc | IFC2X3 | 51.0 MB | 973,208 | 4 | — | 427 seg | — | — | 40,216 | 8,486 | A |
| **TE-ACMV** | SJTII-ACMV-A-TER1-00-R0-Clean.ifc | IFC4 | 63.3 MB | 534,994 | 7 | — | — | — | — | 3,404 | 7,818 | A |
| **TE-CW** | SJTII-CW-A-TER1-00-R0-Clean.ifc | IFC4 | 25.5 MB | 251,795 | 9 | — | — | — | — | 4,145 | 7,558 | A |
| **TE-ELEC** | SJTII-ELEC-A-TER1-00-R0-Clean.ifc | IFC4 | 3.5 MB | 39,127 | 13 | — | — | — | — | 2,388 | 4,468 | A |
| **TE-FP** | SJTII-FP-A-TER1-00-R0-Clean.ifc | IFC4 | 23.7 MB | 259,243 | 7 | — | — | — | — | 19,190 | 34,788 | A |
| **TE-LPG** | SJTII-LPG-A-TER1-00-RO-Clean.ifc | IFC4 | 1.4 MB | 14,830 | 8 | — | — | — | — | 638 | 1,158 | A |
| **TE-SP** | SJTII-SP-A-TER1-00-R0-Clean.ifc | IFC4 | 8.6 MB | 87,263 | 7 | — | — | — | — | 2,856 | 5,170 | A |
| **TE-STR** | SJTII-STR-S-TER1-00-R0-Clean.ifc | IFC4 | 4.7 MB | 56,931 | 9 | — | 614 slab | 432 beam | 312 member | 6,284 | 6,066 | A |
| **TE-Fed** | merged_federation.ifc | IFC4 | 215.9 MB | 2,325,214 | 63 | 33K plate | — | — | 705 | 110,516 | 174,473 | A |

| **FK** | FZK_Haus_IFC4.ifc | IFC4 | 2.6 MB | 44,249 | 2 | 13 wall, 11 window, 5 door, 4 slab, 4 beam, 42 member, 2 railing, 1 stair | 4,757 | 659 | A |

**Existing pipeline YAML configs:** `classify_sh.yaml`, `classify_dx.yaml`, `classify_te.yaml`, `classify_br.yaml`, `classify_rd.yaml`, `classify_rl.yaml`, `classify_dm.yaml`, `classify_fk.yaml`, `classify_in.yaml`, + 26 more from S44/S44b onboarding.

**Total onboarded:** 34 buildings (S44b). 2,459 products across 25 IFC classes.

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
| ~~1~~ | ~~FZK_Haus_IFC4.ifc~~ | ~~FK~~ | — | **DONE** (S38b). 82 elements, 10/10 PASS, first timber/TILE stone. |
| ~~1~~ | ~~AC11_Institute_IFC2x3.ifc~~ | ~~IN~~ | — | **DONE** (S39c). 699 elements, 9/10 PASS. First institutional building. |
| ~~2~~ | ~~PCERT IFC4X3 (4 files)~~ | ~~BA/BH/BS/IP~~ | — | **DONE** (S44). Config-only onboarding, zero compiler changes. |
| ~~3~~ | ~~Schependomlaan + Clinic (5) + Esplanades + Molio~~ | ~~SC/CA/CS/CH/CE/CP/ES/MO~~ | — | **DONE** (S44). 12 buildings, 8→20 total. |
| ~~4~~ | ~~BimWhale (4) + AC9/AC90 (3) + Revit (3) + Castle + HITOS + Jesse + Wilfer~~ | ~~14 buildings~~ | — | **DONE** (S44b). 20→34 buildings. 4 failed extraction (FJ/SW/VG/ET). |
| **1** | Fresh IFC from user's project | — | Real project | DimensionRangeValidator (DV010) now screens new IFCs against 415 mined rules from 20 buildings. Pipeline auto-flags dimensional outliers. |
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

| Entity Domain | Onboarded Buildings | Mined Rules | Coverage |
|---------------|---------------------|-------------|----------|
| IfcWall | SH, DX, FK, IN, SC, CA, ES, MO, GH, JS, NI, RA, CL, HI + 6 more | 64 rules | **Excellent** — 20 buildings |
| IfcDoor | DX, FK, IN, ES, CA, RA, RM, CL, HI, MO + 7 more | 51 rules | **Excellent** |
| IfcWindow | SH, DX, FK, IN, ES, RA, CL, HI, MO + 5 more | 46 rules | **Excellent** |
| IfcSlab | SH, DX, TE, ES, RM, CL, HI, MO + 6 more | 40 rules | **Excellent** |
| IfcFlowTerminal | CP, CE, CH, RM | 29 rules | **Good** — MEP coverage |
| IfcColumn | ES, RM, RA, CL, HI, MO + 4 more | 22 rules | **Good** |
| IfcFurnishingElement | IN, CA, RA, CL, HI, MO | 19 rules | **Good** |
| IfcBeam | ES, RM, CL, HI, MO + 2 more | 14 rules | **Good** |
| IfcFlowSegment/Fitting | CP, CE, CH, RM | 21 rules | **Good** — pipe/duct |
| IfcDuctSegment/Fitting | RM | 8 rules | Revit MEP only |
| IfcAirTerminal | RM | 3 rules | Revit MEP only |
| IfcBridge / BridgePart | BA (PCERT) | (infra rules) | IFC4X3 |
| IfcRoad / RoadPart | (PCERT) | (infra rules) | IFC4X3 |
| IfcRailway / TrackElement | (PCERT) | (infra rules) | IFC4X3 |
| **Total** | **34 buildings** | **415 rules, 25 classes** | **2,459 products** |

---

## 7. File Integrity Notes

| File | Issue | Action Needed |
|------|-------|---------------|
| Smiley_West_IFC2x3.ifc | Non-standard `FILE_SCHEMA` header (Allplan export) — schema detection fails, entity parsing incomplete | Fix header line or write custom parser adapter |
| Vogel_Gesamt_IFC2x3.ifc | Non-standard `FILE_SCHEMA` header — same issue | Fix header |
| FJK_Project_IFC2x3.ifc | No `FILE_SCHEMA` detected, no building hierarchy, only 6 proxies | Delete or keep as negative test |
| PCERT_Building_Hvac_IFC4X3.ifc | Only 5 real elements, 153 total entities | Too small for Rosetta Stone, keep for IFC4X3 schema test only |
| All PCERT files | Zero property sets (PSets=0) — certification geometry-only | Property mining won't yield results; useful for entity/spatial testing only |

---

## 8. Dimension Range Validation (DV010)

**Status:** LIVE (session 47). Runs automatically in the IFC-to-BOM pipeline.

Every new IFC that enters the pipeline has its extracted element dimensions
checked against typical ranges mined from 20 onboarded buildings:

| IFC Class | Rules | Typical Width Range | Typical Height Range | Buildings |
|-----------|-------|--------------------|--------------------|-----------|
| IfcWall | 64 | 800–10,269mm | 531–5,100mm | 20 |
| IfcDoor | 51 | 625–2,140mm | 2,134–2,283mm | 17 |
| IfcWindow | 46 | 500–2,688mm | 705–2,500mm | 14 |
| IfcSlab | 40 | 1,600–64,767mm | 30–429mm | 14 |
| IfcColumn | 22 | 188–1,000mm | 2,000–4,779mm | 10 |
| IfcBeam | 14 | 189–4,375mm | 200–675mm | 6 |
| ... | ... | ... | ... | ... |
| **Total** | **415 rules** | **25 IFC classes** | **1,245 params** | **20 buildings** |

**What it flags:** Elements with dimensions >5× outside the aggregated
min/max range across all buildings. Example from SH pipeline run:

```
[DimRange] SampleHouse: 55/55 checked, 27 PASS, 28 outliers
  IfcWall: Wall-Partn W=95mm (range [800-10269]mm)   ← thin partition
  IfcMember: Curtain_Wall W=30mm (range [457-1782]mm) ← curtain wall mullion
```

Both are legitimate elements (thin partitions, mullions) — the validator
correctly identifies them as dimensional outliers compared to typical
full-width walls/members. This is valuable for catching data errors in
unfamiliar IFC files before the pipeline invests effort in BOM compilation.

**Advisory only** — never blocks the pipeline. Outlier report is logged and
available for review. As more buildings are onboarded, the mined ranges
become more representative and the false-positive rate decreases.

**To regenerate rules after onboarding new buildings:**
```bash
./scripts/extract_validation_rules.sh <building_type>  # mine rules from extracted DB
./scripts/apply_mined_rules.sh                          # apply all DV_*_rules.sql
```

---

## 7. IFC as Compiled Output — Symmetric Invariants (S145)

An IFC file is a **compiled binary**, not source code. The architect's intent
(the "source") is lost. To extract a reusable BOM, the compiler must
reverse-engineer three logical layers from the placement data:

| Layer | What | Math | BOM impact |
|-------|------|------|------------|
| **Global Invariant** | Interior elements follow 180° rotation about building center | `P_B = R_180(P_A - C) + C` | 1:1 match, single library entry, instance transform |
| **Mirror-Axis Exception** | Envelope elements (exterior walls) stay static — normal vector must face outward | `P_B = Mirror_axis(P_A)` | Handedness check — left-hand vs right-hand may be different SKUs |
| **Functional Offset** | ~700mm residual between theoretical rotation center and actual placement | `P_B = Transform(P_A) + v_offset` | System integration — the "linker" gap for party wall / MEP chase |

**Evidence:** DX Duplex (S145) — paired ARC windows rotate perfectly about
building center Y=-8.9, exterior walls stay at same Y (static envelope),
ceilings show ~700mm offset (party wall thickness). See
[DuplexAnalysis.md §Hybrid Symmetry](../../../docs/DuplexAnalysis.md#hybrid-symmetry-s145-finding).

**Extraction principle:** Don't match identical coordinates — match **relative
topology**. Normalize to local space, fuzzy-pair with tolerance, classify the
residual vector: zero = unit element, wall-thickness = partition, large = interface.

**Philosophy:** An IFC model is a collection of local coordinate systems linked
by functional constraints. Solving for the constraints (the "why" behind the
offset) yields not just a BOM but a generative script that can rebuild the building.
