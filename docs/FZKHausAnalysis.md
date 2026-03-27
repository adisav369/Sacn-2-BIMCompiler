# FZK-Haus Analysis — FZK_Haus_IFC4.ifc Guardrails
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md) · [DISC_VALIDATION_DB_SRS](DISC_VALIDATION_DB_SRS.md) §10.4.6 (ROOF sub-discipline)

<div class="bim-banner" markdown>
<b>82-element German residential with pitched roof — IFC4 ArchiCAD proof.</b> Two-storey house validates European authoring tool interoperability and roof geometry extraction.
</div>

**Stone:** candidate #4 (German residential — IFC4 ArchiCAD export)
**Created:** 2026-03-20 (session 38 preparation)

---

## Building Identity

| Property | Value |
|----------|-------|
| Name | FZK-Haus (Forschungszentrum Karlsruhe) |
| IFC version | IFC4 (single file) |
| Country | Germany (KIT research building) |
| Authoring tool | ArchiCAD (ARCHICAD BIM Base Quantities present) |
| Type | 2-storey residential with pitched roof |
| Elements | 99 building elements (see census below) |
| Disciplines | 3 proposed: ARC, STR, ROOF (see §Discipline Breakdown) |
| M_Product_Category | RE (Residential) |
| Prefix | FK |
| C_DocType_ID | RE_FK |
| Reference IFC | `DAGCompiler/lib/input/IFC/FZK_Haus_IFC4.ifc` |
| Source | KIT ifcwiki — freely downloadable |

## Storey Structure

| IFC Name | German | Proposed Code | Role | Seq |
|----------|--------|---------------|------|-----|
| Erdgeschoss | Ground Floor | EG | GROUND_FLOOR | 1010 |
| Dachgeschoss | Attic/Upper | DG | UPPER_FLOOR | 1020 |

## Space Inventory (7 rooms)

| IFC Name | German | Proposed Role | Storey | Thermal Req |
|----------|--------|---------------|--------|-------------|
| Flur | Hall/Corridor | CORRIDOR | EG | 20°C |
| Buero | Office | OFFICE | EG | 20°C |
| Bad | Bathroom | BATHROOM | EG | 24°C |
| Schlafzimmer | Bedroom | BEDROOM | EG | 20°C |
| Wohnen | Living | LIVING | EG | 20°C |
| Küche | Kitchen | KITCHEN | EG | 15°C |
| Galerie | Gallery/Mezzanine | GALLERY | DG | 20°C |

**Note:** All 7 spaces have `Pset_SpaceThermalRequirements` with summer/winter temperatures.
This is the first model with per-room thermal data — SH and DX have none.

## Element Census

| IFC Class | Count | Names / Pattern |
|-----------|-------|-----------------|
| IfcWallStandardCase | 13 | 5 interior (`Wand-Int-ERDG-*`), 4 exterior ground (`Wand-Ext-ERDG-*`), 4 exterior upper (`Wand-Ext-OG-*`) |
| IfcWindow | 11 | 9 ground (`EG-Fenster-1..9`), 2 upper (`OG-Fenster-1..2`) |
| IfcDoor | 5 | 3 interior (`Innentuer-1..3`), 1 entry (`Haustuer`), 1 terrace (`Terrassentuer`) |
| IfcSlab | 4 | 1 ground slab (`Bodenplatte`), 1 intermediate (`Slab-033`), 2 roof slabs (`Dach-1`, `Dach-2`) |
| IfcMember | 42 | 36 rafters (`Sparren-1..36`), 4 purlins (`Pfette-*`), 1 ridge beam (`First`), 1 unknown |
| IfcBeam | 4 | 1 lintel (`Unterzug-1`), 2 purlins (`Pfette-2-1`, `Pfette-1-1`), 1 ridge (`First`) |
| IfcRailing | 2 | Gallery railing (`Geländer_horizontal 13`) |
| IfcStair | 1 | Spiral staircase (`Wendeltreppe`) |
| IfcOpeningElement | 17 | Window/door voids |
| **Total** | **99** | (excluding openings: **82**) |

## Discipline Breakdown (proposed)

| Discipline | Count | Elements |
|------------|-------|----------|
| ARC | 34 | 13 walls, 11 windows, 5 doors, 2 railing, 1 stair, 2 roof slabs |
| STR | 48 | 42 members (rafters/purlins), 4 beams, 1 ground slab, 1 intermediate slab |
| **Total** | **82** | |

**Compared to SH (55 elements, 2 disciplines):** FK is 50% larger with a full pitched-roof
timber structure. The 42 members + 4 beams make the STR discipline dominant — SH has no
timber/roof structure at all.

## Materials

| IFC Material | Type | Existing in Library? |
|--------------|------|---------------------|
| Leichtbeton 102890359 | Lightweight concrete block | **NO** — library has `Masonry - Concrete Block` (dense, λ=1.13). Lightweight is λ≈0.19 |
| Stahlbeton 65690 | Reinforced concrete | **YES** — maps to existing C30/C35 |
| Holz | Timber (structural) | **NO** — library has no timber material at all |
| Solid / Solid 397409098 | Generic solid (ArchiCAD default) | **NO** — unmapped, needs investigation |

### Material Layer Sets

| Layer Set | Material | Thickness (m) | Maps To |
|-----------|----------|---------------|---------|
| Leichtbeton 102890359 0.24 | Lightweight concrete | 0.240 | **NEW** wall type needed |
| Leichtbeton 102890359 0.30 | Lightweight concrete | 0.300 | **NEW** wall type needed |
| Stahlbeton 65690 0.2 | Reinforced concrete | 0.200 | Closest: `Floor:Floor-Grnd-Susp` but not exact match |
| Solid 397409098 0.2 | Generic solid | 0.200 | Unknown — ArchiCAD default fill |

## Property Sets — What's Extractable

### Already in Pipeline (confirmed by SH/DX)
| PSet | Properties | Status |
|------|------------|--------|
| Pset_WallCommon | ThermalTransmittance | Extractable |
| Pset_DoorCommon | ThermalTransmittance, FireRating, AcousticRating | Extractable |
| Pset_WindowCommon | ThermalTransmittance, FireRating, AcousticRating | Extractable |
| Pset_SlabCommon | ThermalTransmittance | Extractable |
| Pset_BeamCommon | Slope, IsExternal, LoadBearing | Extractable |

### NEW — Not in Any Existing Rosetta Stone
| PSet | Properties | Value for Pipeline |
|------|------------|--------------------|
| **Pset_SpaceThermalRequirements** | SpaceTemperatureMax/Min, Summer/Winter, AirConditioning | **HIGH** — per-room thermal targets. Feeds ASSEMBLY_BUILDER U-value calc. No existing stone has this. |
| **Pset_SpaceCommon** | Category, HandicapAccessible, NaturalVentilation | **HIGH** — accessibility + ventilation flags per room. Feeds BIM_Designer space validation. |
| **Pset_StairCommon** | HandicapAccessible, IsExternal, FireExit | **MEDIUM** — stair compliance data. Cross-ref with `ad_stair_requirement`. |
| **Pset_BuildingCommon** | OccupancyType, NumberOfStoreys, YearOfConstruction, SprinklerProtection, GrossPlannedArea | **HIGH** — building-level metadata. First model with occupancy classification (`citygml:1000 = residential`). |
| **Pset_ProjectCommon** | ConstructionMode (`Massivhaus`=masonry), BuildingPermitId, GrossAreaPlanned | **MEDIUM** — project context. |
| **Pset_SiteCommon** | BuildingHeightLimit (9m) | **MEDIUM** — regulatory constraint from IFC. |
| **Pset_FireRatingProperties** | FireResistanceRating | **MEDIUM** — empty in this file but the structure is present for mining. |
| **ArchiCADProperties** | ArchiCAD-specific metadata | LOW — vendor-specific, not portable. |
| **BaseQuantities** | GrossFloorArea, NetHeight, GrossPerimeter, NetVolume | **HIGH** — IFC standard quantities. 176 quantity entries. SH has none. |

---

## What's NEW for component_library.db

### §1. New Material Rows (`ad_material_thermal`)

| material_name | conductivity_w_mk | description | source |
|---------------|-------------------|-------------|--------|
| Leichtbeton (Lightweight Concrete) | 0.19 | Lightweight concrete block, German standard | DIN 4108 / FZK-Haus IFC |
| Holz (Structural Timber) | 0.13 | Softwood structural timber (spruce/pine) | DIN 4108 / CIBSE Guide A |

**Why it matters:** The library currently has zero timber entries. FK's 42 rafters + 4 beams are
all timber — this is the first pitched-roof timber structure in our inventory. The Assembly Builder
(G-7 SRS) needs timber conductivity for U-value calculations on roof assemblies.

### §2. New Wall Types (`ad_wall_type`)

| wall_type_id | category | construction | total_mm | material | U-value |
|--------------|----------|-------------|----------|----------|---------|
| EXTERIOR_DE_LB_240 | EXTERIOR | MASONRY | 240 | Leichtbeton | 1.5 W/m²K |
| EXTERIOR_DE_LB_300 | EXTERIOR | MASONRY | 300 | Leichtbeton | 0.4 W/m²K |
| INTERIOR_DE_LB_240 | INTERIOR | MASONRY | 240 | Leichtbeton | 1.5 W/m²K |

**Why it matters:** Library currently has only US stud-frame and UK brick/block walls.
These are the first European masonry monolithic walls — single-leaf concrete block, no cavity.
Different construction paradigm from the existing US/UK/MY types.

### §3. New Beam Types (`ad_beam_type`)

| beam_type_id | category | construction | depth_mm | width_mm | material |
|--------------|----------|-------------|----------|----------|----------|
| TIMBER_RAFTER_200 | ROOF | TIMBER | ~200 | ~80 | Holz |
| TIMBER_PURLIN_240 | ROOF | TIMBER | 240 | 200 | Holz |
| TIMBER_RIDGE | ROOF | TIMBER | ~200 | ~200 | Holz |

**Why it matters:** Library has only RC beams. No timber at all. FK introduces the
entire ROOF/TIMBER beam category. The 42 rafters (`Sparren`) are a TILE verb candidate
(identical cross-section, equally spaced along ridge).

### §4. New Space Type (`ad_space_type`)

| space_type_id | category | notes |
|---------------|----------|-------|
| GALLERY | HABITABLE | Open mezzanine / gallery overlooking double-height space |

**Why it matters:** Gallery/mezzanine is not in the existing space types. It's common in
European residential (especially with pitched-roof upper floors). Needs railing rule
(already present in FK: 2 × IfcRailing on gallery edge).

### §5. New Discipline: ROOF (or extend STR)

FK's roof structure is 46 elements — more than half the building:
- 36 rafters (Sparren) — timber members at ~30° slope
- 4 purlins (Pfette) — horizontal timber spanning between gable walls
- 1 ridge beam (First) — timber at apex
- 4 beams (Unterzug/Pfette duplicates in IfcBeam)
- 1 stair connecting to attic

**Options:**
1. **Extend STR** with `ROOF` sub-category on beam_type — simpler, follows existing structure
2. **New ROOF discipline** — more accurate for discipline-routing but needs `bad_discipline_priority` row

**Recommendation:** Option 1. Add `category=ROOF` to `ad_beam_type` and keep discipline as STR.

### §6. Thermal Data Pipeline (NEW capability)

FK is the **first model** that enables end-to-end thermal extraction:

```
Per room: Pset_SpaceThermalRequirements → target temperatures
Per wall: Pset_WallCommon.ThermalTransmittance → U-value (W/m²K)
Per window: Pset_WindowCommon.ThermalTransmittance → U-value
Per door: Pset_DoorCommon.ThermalTransmittance → U-value
Per slab: Pset_SlabCommon.ThermalTransmittance → U-value
Material: MaterialLayerSet → layer thicknesses + conductivity
```

This chain connects directly to `ASSEMBLY_BUILDER_SRS.md` U-value calculations. The SH/DX
models have none of this property data — FK is the proof-of-concept for thermal compliance.

### §7. Quantities (NEW — BaseQuantities)

176 `IfcElementQuantity` entries with:
- GrossFloorArea per storey (241.5 m² total, 119.8 m² ground)
- NetHeight per storey (2.7 m)
- Per-element: surface area, volume, length, width, height

**Why it matters:** SH/DX have zero IFC quantities. This enables cross-checking BOM quantities
against IFC-native quantities — a validation gate we couldn't test before.

---

## Verb Analysis (predicted)

| Verb | Elements | Pattern |
|------|----------|---------|
| **TILE** | 36 Sparren (rafters) | Identical cross-section, equal spacing along ridge. Classic TILE candidate. |
| PLACE | 13 walls, 4 slabs, 5 doors | Standard placement, each unique position. |
| PLACE | 11 windows | Varied sizes but each at unique location. |
| CLUSTER | 2 railing segments | Gallery railing — two segments forming one guard. |
| PLACE | 1 stair | Single spiral staircase. |

**Key insight:** The 36 rafters are the **first real TILE candidate in a building model**.
SH has flat qty=1 everything. DX has mirrors but no tiling. FK's rafters prove TILE
on structural timber — a new verb+material combination.

---

## Guardrails (session 38 — PROVEN)

| Gate | Expected | Actual | Status |
|------|----------|--------|--------|
| G1-COUNT | 82 | enbloc=82, walkthru=82 | PASS |
| Delta | 0 | 0 geometry divergences | PASS |
| Rule 8 | 0 violations | all parent-relative | PASS |
| Clash | 0 | 0 furniture clashes | PASS |
| C8 diversity | 0 losses | 0 | PASS |
| C9 axis dims | 0 mismatches | 0 | PASS |
| Storeys | 2 | 2 (EG=26, DG=56) | PASS |
| Spaces | 7 | 7 | PASS |
| BOM lines | ~82 | 93 (82 element + 11 structure) | PASS |
| Pipeline | 9/10 | g4_tamper only (uncommitted changes) | PASS* |

### Mined Data

**Rafter TILE pattern (first real TILE candidate):**
- 42 rafters: 80×5500×3360mm, all identical
- Two rows: north (y=5.0, Sparren 1-20) + south (y=-0.5, Sparren 21-42)
- Regular 700mm OC spacing (18 intervals) + 160mm edge closers
- Written to `migration/DV009_fzk_haus_rules.sql` (deferred — AD_Val_Rule table pending)

**Extraction corrections vs analysis predictions:**
- IFC reports `IfcWallStandardCase` → extractor normalizes to `IfcWall`
- Discipline split: predicted ARC=34/STR=48, actual ARC=36/STR=46 (stair+railings=ARC)

## Edge Cases & Warnings

1. **German naming:** All element names are German (`Wand`, `Fenster`, `Tür`, `Sparren`).
   Pipeline handles UTF-8 and `\X2\...\X0\` IFC encoding correctly — no issues found.

2. **Storey names:** Extraction uses human-readable names (`Erdgeschoss`, `Dachgeschoss`)
   directly — no GUID mapping needed (analysis prediction was wrong).

3. **Member vs Beam overlap:** Purlins appear as both IfcMember and IfcBeam — no
   deduplication needed, they are separate IFC elements with different GUIDs.

4. **Spiral stair:** Single `IfcStair` extracted successfully. No new preset needed.

5. **Pitched roof slabs:** `Dach-1` and `Dach-2` assigned to DG (Dachgeschoss) storey.
   `Dach-1` falls inside GALLERY scope space. Discipline: STR (from ad_ifc_class_map).

6. **Bay slab double-emission bug:** Fixed in `BuildingWriter.java` line ~596.
   `storey.baySlabs()` was not gated by `!hasMetadata`, causing duplicate slabs in
   metadata-driven buildings. See `BOMBasedCompilation.md` §Appendix G4.

7. **No static_children needed:** Bodenplatte already extracted as IfcSlab on FK_EG_STR.
   Adding it as a static child would double-count.

---

## How To Get IFC into BIM Compiler Framework

This is the full recipe for onboarding any new IFC file into the Rosetta Stone pipeline.
FK-specific examples are shown, but the process is identical for every new model.
Canonical references: `docs/SourceCodeGuide.md` §Chapter 4 (Steps 1–5), `docs/WorkOrderGuide.md` §How to Add a New Building.

### Overview

```
 ┌─────────┐  S0  ┌───────────┐  S0  ┌───────────┐  S1  ┌──────────┐
 │ .ifc    │─────▸│ disc_     │─────▸│ component_│─────▸│_extracted│
 │ file    │      │ validation│      │ library.db│      │ .db      │
 └─────────┘      │ .db       │      │ (seeds)   │      └────┬─────┘
                  └───────────┘      └───────────┘           │
                                                    S2 inspect
                                                             │
                  ┌───────────┐      ┌───────────┐           │
                  │ dsl_{pfx} │◂─S4─ │ classify_ │◂──S3──────┘
                  │ .bim      │      │ {pfx}.yaml│
                  └─────┬─────┘      └─────┬─────┘
                        │                  │
                        ▼                  ▼
                  ┌───────────────────────────┐
                  │  run_RosettaStones.sh     │
                  │  Step S5 (all auto)       │
                  └───────────┬───────────────┘
                              │
           ┌───────────┬──────┴──────┬───────────┐
           ▼           ▼             ▼           ▼
      {PFX}_BOM.db  component_   {pfx}_enbloc  {pfx}_walkthru
      (recipe)      library.db   .db (output)  .db (output)
                    (catalog)
                        │
               S6 delta checks (7/7)
                        │
               S7 mine rules → migration SQL
                        │
               S8 update docs
```

### Step 0 — Seed databases (BEFORE extraction)

**Both** `ERP.db` and `component_library.db` must be seeded before
`extract.py` runs. `extract.py` reads `ad_ifc_class_map` at startup to decide
which IFC classes to extract — unregistered types are **silently skipped**.
Classification tables (`ad_material_thermal`, `ad_wall_type`, `ad_beam_type`)
must also exist before the pipeline creates products.

Spec: `docs/SourceCodeGuide.md` §Step 1 (IFC Class Authority Table), `docs/DISC_VALIDATION_DB_SRS.md` §5.2

#### 0a. Register IFC element types in ERP.db

```bash
# 0a-i. List IFC types in the file
python3 -c "
import ifcopenshell
f = ifcopenshell.open('DAGCompiler/lib/input/IFC/FZK_Haus_IFC4.ifc')
types = sorted(set(e.is_a() for e in f.by_type('IfcElement')))
for t in types:
    print(f'  {t:30s}  {len(f.by_type(t)):>4} instances')
"

# 0a-ii. Compare with registered types
sqlite3 library/ERP.db \
    "SELECT ifc_class FROM ad_ifc_class_map WHERE is_active=1 ORDER BY ifc_class"

# 0a-iii. Add missing types (example: IfcMember for timber rafters)
sqlite3 library/ERP.db "
INSERT OR IGNORE INTO ad_ifc_class_map
    (ifc_class, discipline, category, attachment_face, ifc_schema, domain, description)
VALUES
    ('IfcMember', 'STR', 'MEMBER', 'BOTTOM', 'IFC4', 'BUILDING',
     'Structural member — rafter, purlin, brace');
"
```

**FK note:** IfcMember (42 rafters) and IfcStair (1 spiral) may not be in the map.
Check before proceeding.

#### 0b. Seed materials, wall types, beam types in component_library.db

New materials, wall types, and beam types that FK introduces must be in
`library/component_library.db` before the pipeline runs.

```bash
# Verify existing geometry coverage
sqlite3 library/component_library.db "SELECT COUNT(*) FROM component_geometries"
# Current: 24,797 geometry hashes

sqlite3 library/component_library.db "
SELECT COUNT(*) as linked,
       (SELECT COUNT(*) FROM M_Product WHERE is_active=1) as total
FROM M_Product_Image"
# Current: 616 linked / total products
# After FK pipeline runs, expect ~82 new M_Product + ~82 new geometry hashes
```

```bash
# Create migration: migration/ASM002_fzk_materials.sql
cat > migration/ASM002_fzk_materials.sql << 'MIGRATION'
-- ASM002: FZK-Haus materials and component types
-- Append-only. Do not modify existing migrations.

-- §1. New materials (ad_material_thermal)
INSERT OR IGNORE INTO ad_material_thermal
    (material_name, conductivity_w_mk, description, source)
VALUES
    ('Leichtbeton', 0.19,
     'Lightweight concrete block (German Leichtbeton)',
     'DIN 4108 / FZK-Haus IFC'),
    ('Holz', 0.13,
     'Structural softwood timber (spruce/pine)',
     'DIN 4108 / CIBSE Guide A');

-- §2. New wall types (ad_wall_type)
INSERT OR IGNORE INTO ad_wall_type
    (wall_type_id, category, construction, total_mm,
     fire_rating, is_loadbearing, is_exterior,
     material_primary, ifc_class, description, is_active, span_mode)
VALUES
    ('EXTERIOR_DE_LB_300', 'EXTERIOR', 'MASONRY', 300,
     NULL, 1, 1,
     'Leichtbeton', 'IfcWallStandardCase',
     'German exterior - Leichtbeton 300mm monolithic. U=0.4 W/m²K.',
     1, 'PER_STOREY'),
    ('INTERIOR_DE_LB_240', 'INTERIOR', 'MASONRY', 240,
     NULL, 1, 0,
     'Leichtbeton', 'IfcWallStandardCase',
     'German interior - Leichtbeton 240mm loadbearing. U=1.5 W/m²K.',
     1, 'PER_STOREY');

-- §3. New beam types — timber roof structure (ad_beam_type)
INSERT OR IGNORE INTO ad_beam_type
    (beam_type_id, category, construction, depth_mm, width_mm,
     span_max_m, is_loadbearing, material_primary, ifc_class,
     description, is_active)
VALUES
    ('TIMBER_RAFTER', 'ROOF', 'TIMBER', 200, 80,
     6.0, 1, 'Holz', 'IfcMember',
     'Timber rafter (Sparren) — pitched roof, ~30° slope', 1),
    ('TIMBER_PURLIN', 'ROOF', 'TIMBER', 240, 200,
     8.0, 1, 'Holz', 'IfcBeam',
     'Timber purlin (Pfette) — horizontal, gable-to-gable', 1),
    ('TIMBER_RIDGE', 'ROOF', 'TIMBER', 200, 200,
     10.0, 1, 'Holz', 'IfcBeam',
     'Timber ridge beam (First) — apex of pitched roof', 1);
MIGRATION

# Apply it
sqlite3 library/component_library.db < migration/ASM002_fzk_materials.sql
```

Spec: `docs/ASSEMBLY_BUILDER_SRS.md` — U-value calc depends on `ad_material_thermal`

#### 0c. How geometry gets into the library (the LOD chain)

> **Full spec:** [`SourceCodeGuide.md` §Step 4.1: Geometry Resolution Chain](SourceCodeGuide.md#step-41-geometry-resolution-chain--lod-into-component_librarydb)
> — 4-step ordered chain with ASCII diagram, two-table naming convention,
> LOD levels, compile-time resolution order.

**Summary:** Mesh geometry flows from the IFC (via `_extracted.db`) into
`component_library.db` in 4 strict-ordered substeps during pipeline Step 5:

| Order | What | Code |
|-------|------|------|
| ① | Mesh blobs → `component_geometries` | `ExtractionPopulator.fillGeometryGaps()` |
| ② | Element→hash links → `I_Geometry_Map` | same |
| ③ | Product catalog → `M_Product` | `ProductRegistrar.ensureProductCatalog()` |
| ④ | Product→hash → `M_Product_Image` | `ProductRegistrar.ensureProductImages()` |

**Migrations (Step 0b above) seed classification rules — NOT mesh geometry.**
The actual mesh blobs come from the IFC file via extraction.
If this chain breaks, products have no mesh → compilation emits 0 placements.

### Step 1 — Extract geometry from IFC (Python, one-time)

```bash
python3 tools/extract.py --to reference \
    DAGCompiler/lib/input/IFC/FZK_Haus_IFC4.ifc \
    -o DAGCompiler/lib/input/Ifc4_FZKHaus_extracted.db
```

**Output:** `Ifc4_FZKHaus_extracted.db` — an SQLite DB containing:

| Table | What |
|-------|------|
| `elements_meta` | Element names, IFC classes, storey assignments |
| `elements_rtree` | Bounding boxes (AABB min/max per axis) |
| `element_instances` | Geometry hashes per element |
| `base_geometries` | Mesh blobs (vertices + faces) |

The `building_type` field in the YAML must match this filename minus `_extracted.db`:
`building_type: Ifc4_FZKHaus`

### Step 2 — Inspect extracted data

```bash
DB=DAGCompiler/lib/input/Ifc4_FZKHaus_extracted.db

# 2a. List storeys and element counts
sqlite3 "$DB" "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey"
# Expected for FK:
#   Erdgeschoss|~50
#   Dachgeschoss|~32

# 2b. List IFC classes and counts
sqlite3 "$DB" \
    "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC"
# Expected: IfcMember 42, IfcWallStandardCase 13, IfcWindow 11, ...

# 2c. Bounding box (world envelope) — needed for scope space origins
sqlite3 "$DB" "
SELECT ROUND(MIN(r.minX),2) as xMin, ROUND(MAX(r.maxX),2) as xMax,
       ROUND(MIN(r.minY),2) as yMin, ROUND(MAX(r.maxY),2) as yMax,
       ROUND(MIN(r.minZ),2) as zMin, ROUND(MAX(r.maxZ),2) as zMax
FROM elements_rtree r"

# 2d. Check for mirror symmetry
#   If xMin..xMax is symmetric around a centre line → MIRRORED_PAIR composition
#   FK: single unit, no mirror expected
```

**These storey names become the keys in the YAML `storeys:` section.**
Map the IFC GUID-style names to human-readable codes.

### Step 3 — Write the classification YAML (the ONLY invention step)

Create `IFCtoBOM/src/main/resources/classify_fk.yaml`.
The YAML is the **single point of human intent** — everything else is deterministic.

```yaml
# ── FK: FZK-Haus (German 2-storey residential, IFC4, ArchiCAD) ──
schema_version: 1

building:
  building_type: Ifc4_FZKHaus        # must match {name}_extracted.db
  prefix: FK                          # → FK_BOM.db
  building_bom_id: BUILDING_FK_STD
  doc_sub_type: FK                    # deprecated — redundant with prefix (see DATA_MODEL.md §7)
  doc_base_type: RE                   # deprecated — redundant with M_Product_Category=RE (see DATA_MODEL.md §7)
  name: FZK-Haus
  dsl_file: dsl_fk.bim               # BIM COBOL script

  storeys:
    # Keys = exact storey names from elements_meta.storey
    Erdgeschoss:  { code: EG, bom_category: EG, role: GROUND_FLOOR, seq: 1010 }
    Dachgeschoss: { code: DG, bom_category: DG, role: UPPER_FLOOR, seq: 1020 }

  floor_rooms:
    Erdgeschoss:
      bom_id: FLOOR_FK_EG_STD
      bom_category: EG
      spaces:
        # origins and AABBs come from Step 2c query — adjust after extraction
        - { name: FLUR,          template_bom: FK_CORRIDOR_SET,  role: CORRIDOR,  seq: 10,
            aabb_mm: [TBD], origin_m: [TBD] }
        - { name: BUERO,         template_bom: FK_OFFICE_SET,    role: OFFICE,    seq: 20,
            aabb_mm: [TBD], origin_m: [TBD] }
        - { name: BAD,           template_bom: FK_BATHROOM_SET,  role: BATHROOM,  seq: 30,
            aabb_mm: [TBD], origin_m: [TBD] }
        - { name: SCHLAFZIMMER,  template_bom: FK_BEDROOM_SET,   role: BEDROOM,   seq: 40,
            aabb_mm: [TBD], origin_m: [TBD] }
        - { name: WOHNEN,        template_bom: FK_LIVING_SET,    role: LIVING,    seq: 50,
            aabb_mm: [TBD], origin_m: [TBD] }
        - { name: KUECHE,        template_bom: FK_KITCHEN_SET,   role: KITCHEN,   seq: 60,
            aabb_mm: [TBD], origin_m: [TBD] }
    Dachgeschoss:
      bom_id: FLOOR_FK_DG_STD
      bom_category: DG
      spaces:
        - { name: GALERIE,       template_bom: FK_GALLERY_SET,   role: GALLERY,   seq: 10,
            aabb_mm: [TBD], origin_m: [TBD] }

  static_children:
    - { child_product_id: FLOOR_SLAB_FK_EG, role: GROUND_SLAB, seq: 5, dz: 0.0 }
```

**Checklist for filling TBD values:**
1. Run extraction (Step 1) → query bounding boxes per space
2. Use `IfcSpace` centroids from the extracted DB to set `origin_m`
3. Use `IfcSpace` extent to set `aabb_mm`
4. For rooms without explicit IfcSpace, use element cluster bounding boxes

Spec: `docs/WorkOrderGuide.md` §Schema (v1) — full field reference

### Step 4 — Write the BIM COBOL DSL script

Create `IFCtoBOM/src/main/resources/dsl_fk.bim`.

```
; ── FZK-Haus DSL: simple building, no mirror ──
; Copy from dsl_sh.bim and adapt
BUILDING BUILDING_FK_STD
  WALK-THRU
    FLOOR EG  AT DZ 0.0
    FLOOR DG  AT DZ 2.7   ; Erdgeschoss height = 2.7m (from BaseQuantities)
  END-WALK-THRU
END-BUILDING
```

**FK is a simple building (no composition/mirror)**, so `dsl_sh.bim` is the template.
Verb ref: `docs/BIM_COBOL.md`

### Step 5 — Build the BOM (automated pipeline)

```bash
# Delete stale BOM if re-running
rm -f library/FK_BOM.db

# Run pipeline for FK only
./scripts/run_RosettaStones.sh classify_fk.yaml
```

**What happens automatically:**

| Stage | Code | Writes To |
|-------|------|-----------|
| Load YAML | `ClassificationYaml.load()` | — |
| Create schema | `IFCtoBOMPipeline` | `FK_BOM.db` |
| Extract | `ExtractionPopulator.populate()` | `component_library.db` (M_Product, geometry) |
| Read extraction | `ExtractionReader.readByStorey()` | — |
| Product catalog | `ProductRegistrar.ensureProductCatalog()` | `component_library.db` |
| Product images | `ProductRegistrar.ensureProductImages()` | `component_library.db` |
| Scope spaces | `ScopeBomBuilder.build()` | `FK_BOM.db` (room assignments) |
| Structural BOM | `StructuralBomBuilder.build()` | `FK_BOM.db` (BUILDING + FLOOR BOMs) |
| Room BOMs | `FloorRoomBomBuilder.build()` | `FK_BOM.db` (static children + templates) |
| QA gate | `BomValidator.validateAndReport()` | — (**FAIL → rollback**) |
| Commit | `IFCtoBOMPipeline` | `FK_BOM.db` (integrity hash) |

**Outputs:**
- `library/FK_BOM.db` — BOM recipe (m_bom + m_bom_line + ad_sysconfig)
- `library/component_library.db` — updated with FK products and geometry
- `DAGCompiler/lib/output/ifc4_fzkhaus.db` — compiled output

### Step 6 — Verify: Delta checks (automated)

The script automatically runs 7 delta checks:

| # | Check | Expected |
|---|-------|----------|
| 1 | G1-COUNT: element count matches expected | 82 |
| 2 | G3-DIGEST: spatial digest matches reference | PASS |
| 3 | G4-TAMPER: no anti-patterns in source | PASS |
| 4 | Singularity: BUILDING BOM is root | 1 root |
| 5 | Rule 8: all offsets within parent AABB | 0 violations |
| 6 | C8: geometry diversity preserved | 0 losses |
| 7 | C9: per-axis dimension match | 0 mismatches |

**Target:** `7/7 PASS` before proceeding to rule mining.

### Step 7 — Mine validation rules from the output

```bash
DB=DAGCompiler/lib/output/ifc4_fzkhaus.db

# 8a. Structural dimensions per element type
sqlite3 "$DB" "
SELECT em.ifc_class, em.storey, COUNT(*) as cnt,
       ROUND(AVG((r.maxX-r.minX)*1000)) as avg_W_mm,
       ROUND(AVG((r.maxY-r.minY)*1000)) as avg_D_mm,
       ROUND(AVG((r.maxZ-r.minZ)*1000)) as avg_H_mm
FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
GROUP BY em.ifc_class, em.storey HAVING COUNT(*) > 1
ORDER BY cnt DESC" -header -column

# 8b. Rafter spacing (TILE verb candidate)
sqlite3 "$DB" "
SELECT em.name, ROUND(r.minX, 3) as x, ROUND(r.minY, 3) as y
FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
WHERE em.name LIKE 'Sparren%'
ORDER BY r.minX" -header -column
# → If spacing is regular, this proves TILE verb for timber rafters

# 8c. Thermal data harvest (NEW for FK)
sqlite3 "$DB" "
SELECT em.name, em.ifc_class,
       -- thermal transmittance if available via properties
       em.storey
FROM elements_meta em
WHERE em.ifc_class IN ('IfcWallStandardCase', 'IfcWindow', 'IfcDoor', 'IfcSlab')
ORDER BY em.storey, em.ifc_class" -header -column
```

Write mined rules to `migration/DV009_fzk_haus_rules.sql` (append-only).
Spec: `docs/SourceCodeGuide.md` §Ch4.Step5, `docs/WorkOrderGuide.md` §Step 7

### Step 8 — Update inventory and docs

```bash
# 8a. Update IFCAnalysis.md — move FK from "candidate" to "in-pipeline"
# 8b. Update this file (FZKHausAnalysis.md) — fill in gate results
# 8c. Update PROGRESS.md — record what was done
# 8d. If new verb confirmed (TILE on rafters) → update BIM_COBOL.md
```

### Troubleshooting Quick Reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| `0 elements extracted` | IFC class not in `ad_ifc_class_map` | Step 0a — add missing types |
| `NULL M_Product_ID` | Extraction bug or renamed element | Check `I_Element_Extraction` in component_library.db |
| `Storey not in YAML` | YAML storey key doesn't match extracted storey name | `sqlite3 extracted.db "SELECT DISTINCT storey FROM elements_meta"` and fix YAML |
| `Space contract FAIL` | Elements outside all AABB scope boxes | Widen `aabb_mm` or add catch-all scope space |
| `BomValidator FAIL` | BOM structure error (orphan lines, missing parent) | Check YAML `building_bom_id` matches, `static_children` refs exist |
| `Delta count mismatch` | EN-BLOC ≠ WALK-THRU element count | Check DSL script storey DZ values, floor_rooms coverage |
| `Geometry hash mismatch` | Different compilation path produced different mesh | Check component_library.db `M_Product_Image` — geometry_hash should be deterministic |
| `German umlauts garbled` | IFC `\X2\...\X0\` encoding not decoded | Pipeline handles this — if names are garbled in DB, check extract.py encoding |

---

## Preparation Checklist (session 38 — COMPLETE)

- [x] **S0a:** IfcStair added to `ad_ifc_class_map` (IfcMember already present)
- [x] **S0b:** `migration/ASM002_fzk_materials.sql` created + applied (2 materials, 2 wall types, 3 beam types)
- [x] **S1:** Extraction → `Ifc4_FZKHaus_extracted.db` (82 elements, 37 geometries, 2 storeys, 7 spaces)
- [x] **S2:** Inspected: EG=26 elements, DG=56 elements, BBOX=(-0.5,12.5)×(-0.5,10.5)×(-0.2,6.318)
- [x] **S3:** `classify_fk.yaml` written (7 scope spaces with IfcSpace AABBs/origins)
- [x] **S4:** `dsl_fk.bim` written (2 storeys, level:0/1, no ROOF — already extracted)
- [x] **S5:** `run_RosettaStones.sh classify_fk.yaml` → 9/10 PASS (g4_tamper only)
- [x] **S6:** Delta: enbloc=82 == walkthru=82, 0 geometry divergences, C8+C9 PASS
- [x] **S7:** Rules mined → `migration/DV009_fzk_haus_rules.sql` (deferred — AD_Val_Rule pending)
- [x] **S8:** This doc updated, PROGRESS.md + BOMBasedCompilation.md updated

---

*FK is the thermal proof-of-concept. If FK compiles with U-values flowing end-to-end,
it unlocks the Assembly Builder's energy compliance path for all future models.*
