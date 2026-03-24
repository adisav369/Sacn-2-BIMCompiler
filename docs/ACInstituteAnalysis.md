# AC11 Institute Analysis — AC11_Institute_IFC2x3.ifc Guardrails
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md)

**Stone:** candidate #5 (German institutional — IFC2x3 ArchiCAD export)
**Created:** 2026-03-20 (session 38b preparation)

---

## Building Identity

| Property | Value |
|----------|-------|
| Name | AC11 Institute |
| IFC version | IFC2X3 |
| Country | Germany |
| Authoring tool | ArchiCAD 11 |
| Type | 5-storey institutional (offices, labs, meeting rooms) |
| Elements | 699 building elements (excluding 287 openings, 3 virtual) |
| Disciplines | 2: ARC (doors, windows, furnishing, railing, stair), STR (walls, slabs, columns) |
| M_Product_Category | RE (Residential) |
| Prefix | IN |
| C_DocType_ID | RE_IN |
| Reference IFC | `DAGCompiler/lib/input/IFC/AC11_Institute_IFC2x3.ifc` |
| Source | opensourceBIM — freely downloadable |

## Storey Structure

| IFC Name | German | Proposed Code | Role | Seq | Elevation (m) |
|----------|--------|---------------|------|-----|---------------|
| Keller | Basement | KE | BASEMENT | 1000 | -3.0 |
| Erdgeschoss | Ground Floor | EG | GROUND_FLOOR | 1010 | 0.0 |
| 1. Obergeschoss | 1st Upper Floor | OG1 | UPPER_FLOOR | 1020 | 3.0 |
| 2. Obergeschoss | 2nd Upper Floor | OG2 | UPPER_FLOOR | 1030 | 6.0 |
| Dachgeschoss | Attic/Roof | DG | ROOF_FLOOR | 1040 | 9.0 |

## Space Inventory (82 rooms across 5 storeys)

| Storey | Spaces | Room Types |
|--------|--------|------------|
| Keller | 17 | Besprechungsraum (meeting), Technikraum (mechanical), Lager (storage), Archiv, WC |
| Erdgeschoss | 18 | Buero (office), Labor (lab), Flur (corridor), WC, Teeküche (kitchenette) |
| 1. Obergeschoss | 22 | Buero, Flur, WC Damen/Herren, Bibliothek (library), Teeküche |
| 2. Obergeschoss | 22 | Buero (×12 identical), Flur, WC, Teeküche — **near-identical to 1.OG** |
| Dachgeschoss | 3 | Dachboden (attic storage ×2), Flur Treppe (stair corridor) |

**Key insight:** 1.OG and 2.OG are near-identical floor plates. This is the first
model with **typical floor repetition** — a WALK-THRU stacking proof at real scale.

## Element Census

| IFC Class | Count | Names / Pattern |
|-----------|-------|-----------------|
| IfcFurnishingElement | 253 | 55 Stuhl (chair), 52 Schreibtisch (desk), 2 Labortisch (lab table), 144 unnamed |
| IfcWindow | 206 | 196× 1.0×1.5m, 6× 0.9×1.5m, 3× 0.9×2.0m, 1× 1.0×2.0m |
| IfcWallStandardCase | 119 | KE=24, EG=29, OG1=29, OG2=29, DG=8 |
| IfcDoor | 77 | KE=16, EG=18, OG1=22, OG2=21 |
| IfcSlab | 26 | KE=1, EG=1, OG1=1, OG2=1, DG=22 (roof structure!) |
| IfcRailing | 12 | 3 per storey (EG, OG1, OG2, DG) — stairwell railings |
| IfcStair | 4 | 1 per storey (KE, EG, OG1, OG2) |
| IfcColumn | 2 | EG only — ground floor columns |
| IfcOpeningElement | 287 | Window/door voids (excluded from element count) |
| IfcVirtualElement | 3 | (excluded) |
| **Total** | **699** | (excluding openings + virtual) |

## Discipline Breakdown (proposed)

| Discipline | Count | Elements |
|------------|-------|----------|
| ARC | 552 | 253 furnishing, 206 windows, 77 doors, 12 railing, 4 stair |
| STR | 147 | 119 walls, 26 slabs, 2 columns |
| **Total** | **699** | |

## Materials

| IFC Material | Maps To |
|--------------|---------|
| Kalksandstein | Sand-lime brick — **NEW** (λ≈0.99, German standard) |
| Stahlbeton | Reinforced concrete — **YES** (existing C30/C35) |
| Aluminium | Window/door frames — **NEW** for component_library |
| Leer | Empty/void material (ArchiCAD default) — skip |

## Property Sets — What's Extractable

| PSet | Count | Value for Pipeline |
|------|-------|--------------------|
| BaseQuantities | 229 | **HIGH** — per-element areas, volumes, lengths |
| Pset_WindowCommon | 206 | **HIGH** — ThermalTransmittance on all windows |
| Pset_WallCommon | 119 | **HIGH** — ThermalTransmittance, IsExternal, LoadBearing |
| Pset_SpaceCommon | 82 | **HIGH** — per-room category, accessibility |
| Pset_SpaceThermalRequirements | 82 | **HIGH** — per-room thermal targets (2nd model after FK) |
| Pset_DoorCommon | 77 | ThermalTransmittance, FireRating |
| Pset_FurnitureTypeCommon | 44 | MEDIUM — furniture classification |
| Pset_SlabCommon | 26 | ThermalTransmittance |
| Pset_RailingCommon | 12 | Height, IsExternal |
| Pset_StairCommon | 4 | HandicapAccessible |
| Pset_ColumnCommon | 2 | LoadBearing, FireRating |
| Pset_BuildingCommon | 1 | OccupancyType, NumberOfStoreys |

## Verb Analysis (predicted → actual)

**Predicted:**

| Verb | Elements | Pattern |
|------|----------|---------|
| **TILE** | 196 windows (1.0×1.5m) | Identical size, repeated across 4 storeys |
| **TILE** | 55 Stuhl (chairs) | Identical product, repeated in offices/labs |
| **TILE** | 52 Schreibtisch (desks) | Same pattern — desk per office |
| PLACE | 119 walls | Each unique position |
| PLACE | 77 doors | Each unique position |
| CLUSTER | 26 slabs | Roof structure with varying dimensions |
| CLUSTER | 12 railings | 3 per storey, stairwell groups |
| PLACE | 4 stairs | 1 per storey |

**Actual (FACTORIZE-v2, session 39c):**

| Storey | Verb Patterns | Instances | Unfactored |
|--------|--------------|-----------|------------|
| Keller STR | 3 | 100 | 23 |
| Erdgeschoss STR | 3 | 92 | 49 |
| 1. Obergeschoss STR | 5 | 87 | 24 |
| 2. Obergeschoss STR | 4 | 84 | 27 |
| Dachgeschoss STR | 1 (CLUSTER) | 15 | 9 |
| **Total** | **16** | **378** | **132** |

**Compression: 791 → 417 BOM lines (47% reduction).**
Scope space elements (253 furnishing) are mostly in small groups (<4 per room)
and fall through to unfactored writes. The storey-level structural elements
(walls, windows, doors) achieve the strongest compression.

VerbDetector ROUTE guard: dimension uniformity check (50mm tolerance) prevents
roof slabs with varying heights from being forced into ROUTE patterns — they
correctly fall through to CLUSTER which stores per-instance dimensions.

---

## What's NEW for component_library.db

### §1. New Material Rows (`ad_material_thermal`)

| material_name | conductivity_w_mk | description | source |
|---------------|-------------------|-------------|--------|
| Kalksandstein | 0.99 | Sand-lime brick (German standard) | DIN 4108 |
| Aluminium | 160.0 | Window/door frame material | CIBSE Guide A |

### §2. New Furnishing Products

253 furnishing elements — first building with significant furniture.
The pipeline will auto-create M_Product entries from extraction. Key types:
- Stuhl (chair) — 55 instances, likely 1-2 unique geometries
- Schreibtisch (desk) — 52 instances, likely 1-2 unique geometries
- Labortisch (lab table) — 2 instances
- 144 unnamed — investigate after extraction

### §3. IFC Class Check

| IFC Class | In `ad_ifc_class_map`? | Action |
|-----------|----------------------|--------|
| IfcWallStandardCase | YES | — |
| IfcWindow | YES | — |
| IfcDoor | YES | — |
| IfcSlab | YES | — |
| IfcColumn | YES | — |
| IfcStair | YES (added in FK session) | — |
| IfcRailing | YES | — |
| IfcFurnishingElement | YES | — |

**All 8 element types already registered.** No S0a work needed.

---

## How To Get IFC into BIM Compiler Framework

Canonical references: `docs/SourceCodeGuide.md` §Chapter 4 (Steps 1-5),
`docs/WorkOrderGuide.md` §How to Add a New Building.

### Step 0 — Seed databases (BEFORE extraction)

#### 0a. Register IFC element types in ERP.db

**All types already registered.** No action needed.

#### 0b. Seed materials in component_library.db

```bash
# Create migration: migration/ASM003_ac11_materials.sql
# New materials: Kalksandstein, Aluminium
# Apply: sqlite3 library/component_library.db < migration/ASM003_ac11_materials.sql
```

### Step 1 — Extract geometry from IFC

```bash
python3 tools/extract.py --to reference \
    DAGCompiler/lib/input/IFC/AC11_Institute_IFC2x3.ifc \
    -o DAGCompiler/lib/input/Ifc2x3_AC11Institute_extracted.db
```

### Step 2 — Inspect extracted data

```bash
DB=DAGCompiler/lib/input/Ifc2x3_AC11Institute_extracted.db

# Storeys and counts
sqlite3 "$DB" "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"

# World bounding box
sqlite3 "$DB" "
SELECT ROUND(MIN(r.minX),2), ROUND(MAX(r.maxX),2),
       ROUND(MIN(r.minY),2), ROUND(MAX(r.maxY),2),
       ROUND(MIN(r.minZ),2), ROUND(MAX(r.maxZ),2)
FROM elements_rtree r"

# Per-space bounding boxes (for YAML scope spaces)
# Use IfcSpace geometry via ifcopenshell (same method as FK)
```

**Actual results (session 39):**
- 699 elements, 91 geometries, 5 storeys, 82 spaces
- World BBOX: X[-1.0, 43.0] Y[-2.0, 17.0] Z[-3.3, 12.049]
- Per-storey: KE=162, EG=184, OG1=160, OG2=160, DG=33
- Discipline split: ARC=697, STR=2 (only columns are STR)

**Furnishing containment note:** Furnishing elements are contained in IfcSpaces
(not IfcBuildingStorey). The extractor resolves containment through the
spatial hierarchy: IfcSpace → IfcBuildingStorey → storey name.
Of 699 elements: 253 furnishing in IfcSpaces, 446 structural at storey level.

### Step 3 — Write `classify_in.yaml`

5 storeys, 82 spaces. The YAML will be significantly larger than FK (7 spaces)
or SH (4 spaces).

**Reference docs for BOM modelling:**
- `docs/WorkOrderGuide.md` §Schema (v1) — full field reference for `storeys:`, `floor_rooms:`, `spaces:`
- `docs/BOMBasedCompilation.md` §2.1 — BOM tree structure (BUILDING → FLOOR → SET → LEAF)
- `docs/BOMBasedCompilation.md` §Appendix G3 — avoid double-counting extracted elements as static_children
- `docs/SourceCodeGuide.md` §Step 3 — how BOM builders create m_bom/m_bom_line from YAML
- `docs/FZKHausAnalysis.md` §Step 3 — worked example of classify YAML with IfcSpace-derived AABBs
- `classify_fk.yaml` — template for scope space syntax (7 spaces, 2 storeys)
- `classify_sh.yaml` — simplest YAML (4 spaces, 1 storey)

**Grouping strategy for 82 spaces → IN_BOM.db:**

```
BUILDING_IN_STD
├── FLOOR_IN_KE_STD (Keller)          ← 17 SET BOMs
│   ├── IN_KE_BESPRECHUNG_I_SET       (Besprechungsraum I — 22 furnishing)
│   ├── IN_KE_BESPRECHUNG_II_SET      (Besprechungsraum II — 22 furnishing)
│   ├── IN_KE_TECHNIK_I_SET           (Technikraum I)
│   └── ... 14 more
├── FLOOR_IN_EG_STD (Erdgeschoss)     ← 18 SET BOMs
│   ├── IN_EG_BUERO_MEIER_SET         (Buero Meier — 2 furnishing)
│   ├── IN_EG_LABOR_I_SET             (Labor I — 10 furnishing)
│   ├── IN_EG_LABOR_II_SET            (Labor II — 11 furnishing)
│   └── ... 15 more
├── FLOOR_IN_OG1_STD (1. Obergeschoss) ← 22 SET BOMs
│   ├── IN_OG1_BUERO_SCHMIDT_SET
│   ├── IN_OG1_WC_DAMEN_SET
│   ├── IN_OG1_BIBLIOTHEK_SET
│   └── ... 19 more
├── FLOOR_IN_OG2_STD (2. Obergeschoss) ← 22 SET BOMs (near-identical to OG1)
│   ├── IN_OG2_BUERO_302_SET
│   └── ... 21 more
└── FLOOR_IN_DG_STD (Dachgeschoss)    ← 3 SET BOMs
    ├── IN_DG_DACHBODEN_1_SET
    ├── IN_DG_DACHBODEN_2_SET
    └── IN_DG_FLUR_TREPPE_SET
```

**Total: 82 SET BOMs + 5 structural FLOOR BOMs + 5 room FLOOR BOMs + 1 BUILDING = 93 BOMs.**

**Key modelling decisions:**
1. **Furnishing in spaces, not storeys:** The IFC puts furniture in IfcSpace containers.
   The scope space AABB must enclose the furniture. Use IfcSpace geometry for AABBs.
2. **OG1 ≈ OG2:** Near-identical floor plates. The YAML lists them separately (no template
   reuse in v1 schema), but the compiled output should show near-identical BOM structure.
   This is a future `repeat:` DSL keyword candidate.
3. **Structural elements on storeys:** Walls, slabs, columns, stairs, railings are contained
   directly in IfcBuildingStorey — they go on the structural FLOOR BOM, not SET BOMs.
4. **DG has 22 slabs:** The Dachgeschoss roof structure (22 slabs) is analogous to FK's
   42 rafters. These are all on one structural floor BOM.
5. **No static_children needed:** All slabs are extracted — see `BOMBasedCompilation.md` §Appendix G3.

### Step 4 — Write `dsl_in.bim`

```
BUILDING "Ifc2x3_AC11Institute" type:SINGLE_UNIT profile:"DE_Institutional" {
    STOREY "Keller" level:0 height:3.0m { }
    STOREY "Erdgeschoss" level:1 height:3.0m { }
    STOREY "1. Obergeschoss" level:2 height:3.0m { }
    STOREY "2. Obergeschoss" level:3 height:3.0m { }
    STOREY "Dachgeschoss" level:4 height:3.0m { }
}
```

### Step 5 — Run pipeline

```bash
./scripts/run_RosettaStones.sh classify_in.yaml
```

**Add `RE_IN` to `GATE_SCOPE`** in `BuildingRegistryTest.java` before running.
(See `SourceCodeGuide.md` §10 step 4 and `BOMBasedCompilation.md` §Appendix G1.)

### Step 6 — Delta checks

**Actual results (session 39): 9/11 PASS**

| Gate | Result | Notes |
|------|--------|-------|
| IFCtoBOM Pipeline | PASS | 93 BOMs, 791 lines, 4.5x factorization |
| EN-BLOC compile | PASS | 699 elements |
| WALK-THRU compile | PASS | 699 elements |
| Delta (EB vs WT) | PASS | 0 element count delta |
| Geometry divergence | PASS | 0 hash mismatches |
| Rule 8 (coords) | PASS | All coordinates parent-relative |
| Clash check | PASS | 0 furniture clashes |
| C9-AXISDIM | PASS | 0 axis dimension mismatches |
| **C8-GEODIV** | **FAIL** | 6 IfcFurnishingElement mesh types (ref=6, out=0) — library gap |
| **G4-TAMPER** | **FAIL** | Uncommitted code (expected, same as SH/FK) |

**C8 root cause:** Furnishing products (chairs, desks, lab tables) have 7 distinct
geometries in the reference DB but no matching meshes in `component_library.db`.
Same pattern as DX (12 lost types). Fix: import furnishing LOD geometry into library.

**BOM QA highlights:**
- 93 BOMs: 1 BUILDING + 10 FLOOR + 82 SET
- 786 product-linked LEAF lines (699 extraction + 87 template/static)
- 174 distinct products → 4.5x factorization ratio

### Step 7 — Mine validation rules (deferred)

Key mining targets for future session:
- Window spacing per storey (TILE proof at 196 instances)
- Furniture density per room type (chairs/desks per office)
- Floor-to-floor repetition (OG1 vs OG2 element-for-element match)
- Wall thickness distribution (Kalksandstein vs Stahlbeton)

### Step 8 — Update docs (DONE)

- Updated this doc with extraction + pipeline results
- Hardened `SourceCodeGuide.md` §10 with complete IFC onboarding recipe
- Updated `PROGRESS.md` with IN session results

---

## Preparation Checklist

- [x] **S0a:** All IFC classes already registered — verified (8/8)
- [x] **S0b:** `migration/ASM003_ac11_materials.sql` created + applied (Kalksandstein, Aluminium)
- [x] **S1:** Extraction → `Ifc2x3_AC11Institute_extracted.db` (699 elements, 91 geometries)
- [x] **S2:** Inspected: 5 storeys, world BBOX [-1,43]x[-2,17]x[-3.3,12], 82 space AABBs
- [x] **S3:** `classify_in.yaml` — 216 lines, 82 spaces (largest YAML yet)
- [x] **S4:** `dsl_in.bim` — 5 storeys, level:0-4, heights 3.0/3.0/3.0/3.0/2.669m
- [x] **S5:** `RE_IN` added to GATE_SCOPE (BuildingRegistryTest + RosettaStoneGateTest), pipeline run
- [x] **S6:** 9/11 PASS — C8 (furnishing library gap) + G4 (uncommitted) are known patterns
- [ ] **S7:** Mine rules — deferred to future session
- [x] **S8:** Docs updated

---

## Comparison with Existing Stones

| Metric | SH | FK | **IN** | DX | TE |
|--------|----|----|--------|----|----|
| Elements | 55 | 82 | **699** | 1099 | 48428 |
| Storeys | 1 | 2 | **5** | 2 | 7 |
| Spaces | 4 | 7 | **82** | ~20 | — |
| Windows | 3 | 11 | **206** | ~50 | — |
| Furniture | 0 | 0 | **253** | 0 | 0 |
| TILE candidates | 0 | 42 | **303** | 0 | 12 |
| IFC version | IFC4 | IFC4 | **IFC2x3** | IFC2x3 | IFC4 |
| Typology | Residential | Residential | **Institutional** | Residential | Terminal |

**IN fills the gap between FK (82) and DX (1099) — it's the mid-scale stone
that proves the pipeline handles multi-storey institutional buildings with
real furniture, real spaces, and massive window repetition.**

---

*IN is the verb compression proof-of-concept. If 196 windows + 55 chairs + 52 desks
compress to ~10 TILE patterns, the BOM factorization ratio will be the highest yet.*
