# AC11 Institute Analysis — AC11_Institute_IFC2x3.ifc Guardrails

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
| Proposed DocSubType | IN |
| Proposed DocBaseType | RE |
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

## Verb Analysis (predicted)

| Verb | Elements | Pattern |
|------|----------|---------|
| **TILE** | 196 windows (1.0×1.5m) | **Identical size, repeated across 4 storeys.** Strongest TILE candidate ever — 196 instances of same product. |
| **TILE** | 55 Stuhl (chairs) | Identical product, repeated in offices/labs across all storeys. |
| **TILE** | 52 Schreibtisch (desks) | Same pattern — desk per office, repeated. |
| PLACE | 119 walls | Each unique position. |
| PLACE | 77 doors | Each unique position. |
| PLACE | 26 slabs | Floor plates + roof structure. |
| CLUSTER | 12 railings | 3 per storey, stairwell groups. |
| PLACE | 4 stairs | 1 per storey. |

**Key insight:** This model should achieve **very high verb compression** —
196+55+52 = 303 elements (43% of total) are TILE candidates. Compare: FK had
42 rafters (51%), SH had 0, TE has ROUTE/SPRAY but no TILE at building scale.

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
`docs/YAMLGuide.md` §How to Add a New Building.

### Step 0 — Seed databases (BEFORE extraction)

#### 0a. Register IFC element types in disc_validation.db

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

**Expected:** ~699 elements, 5 storeys, world BBOX TBD.

**Furnishing containment note:** Furnishing elements are contained in IfcSpaces
(not IfcBuildingStorey). The extractor must resolve containment through the
spatial hierarchy: IfcSpace → IfcBuildingStorey → storey name.

### Step 3 — Write `classify_in.yaml`

5 storeys, 82 spaces. The YAML will be significantly larger than FK (7 spaces)
or SH (4 spaces).

**Reference docs for BOM modelling:**
- `docs/YAMLGuide.md` §Schema (v1) — full field reference for `storeys:`, `floor_rooms:`, `spaces:`
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

### Step 6 — Delta checks (target 7/7 PASS)

### Step 7 — Mine validation rules

Key mining targets:
- Window spacing per storey (TILE proof at 196 instances)
- Furniture density per room type (chairs/desks per office)
- Floor-to-floor repetition (OG1 vs OG2 element-for-element match)
- Wall thickness distribution (Kalksandstein vs Stahlbeton)

### Step 8 — Update docs

---

## Preparation Checklist

- [ ] **S0a:** All IFC classes already registered — verify
- [ ] **S0b:** Create + apply `migration/ASM003_ac11_materials.sql` (Kalksandstein, Aluminium)
- [ ] **S1:** Run extraction → `Ifc2x3_AC11Institute_extracted.db`
- [ ] **S2:** Inspect storeys, element counts, world bounding box, per-space AABBs
- [ ] **S3:** Write `classify_in.yaml` (82 spaces — largest YAML yet)
- [ ] **S4:** Write `dsl_in.bim` (5 storeys, level:0-4)
- [ ] **S5:** Add `RE_IN` to `GATE_SCOPE`, run pipeline
- [ ] **S6:** 7/7 delta PASS
- [ ] **S7:** Mine rules (window TILE, furniture density, floor repetition)
- [ ] **S8:** Update IFCAnalysis.md, PROGRESS.md, this doc

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
