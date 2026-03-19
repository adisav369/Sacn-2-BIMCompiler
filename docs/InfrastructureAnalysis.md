# Infrastructure IFC Analysis

**Date:** 2026-03-16 | **Updated:** 2026-03-19 (deep review: file probe, spec gap audit, LOD plan)
**Source files:** `reference/infrastructure/` (9 IFC4X3_ADD2 files, ~7.5 MB)
**Purpose:** Understand how infrastructure IFCs map to the existing pipeline, identify spec gaps, and plan the Rosetta Stone path for infrastructure

---

## 1. The Core Insight

The pipeline already has the right abstractions. Infrastructure is not a new problem —
it is the **same hierarchy with different vocabulary**:

| Building (today) | Infrastructure (future) | Pipeline abstraction |
|-------------------|------------------------|---------------------|
| IfcBuilding | IfcRoad / IfcBridge / IfcRailway | **FACILITY** |
| IfcBuildingStorey | IfcRoadPart / IfcBridgePart / IfcRailwayPart | **SEGMENT** |
| IfcSpace (rooms) | Lanes / Spans / Track sections | **SCOPE** |
| Walls, doors, slabs | Courses, rails, pavement, piers | **ELEMENT** |

The BOM hierarchy `BUILDING → FLOOR → LEAF` is already generic in structure:
parent BOM with MAKE children → child BOM with LEAF elements → dx/dy/dz offsets.
Renaming BUILDING to FACILITY and FLOOR to SEGMENT changes labels, not logic.

---

## 2. File Inventory (probed 2026-03-19)

All 9 files are **IFC4X3_ADD2**. All share EPSG:32760 (UTM zone 60S) georeferencing.

### 2.1 Infrastructure Files

| File | Schema | Facilities | Segments | Elements | Coord range (mm) |
|------|--------|-----------|----------|----------|-------------------|
| Infra-Bridge.ifc | IFC4X3 | 3 IfcBridge | 18 IfcBridgePart | 50 | X: -17K..44K, Y: 0..53K, Z: -2.8K..7K |
| Infra-Road.ifc | IFC4X3 | 5 IfcRoad | 26 IfcRoadPart | 55 | X: -23K..41K, Y: 0..50K, Z: -490..0 |
| Infra-Rail.ifc | IFC4X3 | 2 IfcRailway | 2 IfcRailwayPart | 75 | X: -17K..43K, Y: 0..55K, Z: -200..8K |
| Infra-Landscaping.ifc | IFC4X3 | 5 Road + 3 Bridge + 2 Railway | mixed | 112 | X: -26K..37K, Y: 0..55K, Z: -800..8K |
| Infra-Plumbing.ifc | IFC4X3 | 3 IfcBridge (context) | — | 29 | X: -24K..41K, Y: 0..50K, Z: -1.8K..0.2K |

**IfcMapConversion (infra):** Eastings=729011226, Northings=9063960608, Scale=1.0

### 2.2 Building Context Files (same project)

| File | Schema | Facilities | Elements | Notes |
|------|--------|-----------|----------|-------|
| Building-Architecture.ifc | IFC4X3 | 1 IfcBuilding | 15 | **Rotated 30deg** (XAxisAbscissa=0.5) |
| Building-Structural.ifc | IFC4X3 | 1 IfcBuilding | 18 | Same rotation |
| Building-Hvac.ifc | IFC4X3 | 1 IfcBuilding | 6 | Has IfcDistributionSystem |
| Building-Landscaping.ifc | IFC4X3 | 0 (IfcSite only) | 7 | No IfcBuilding — flat site |

**IfcMapConversion (building):** Eastings=729013349, Northings=9063992685 — offset ~2km E, ~32km N from infra origin, plus 30-degree rotation.

### 2.3 Entity Census by Domain

| Domain | IFC4X3-specific types | Count | Notes |
|--------|----------------------|-------|-------|
| **Bridge** | IfcBridgePart(18), IfcBearing(0), IfcFooting(7), IfcEarthworksFill(4) | ~30 structural | Substructure/superstructure/deck decomposition |
| **Road** | IfcRoadPart(26), IfcSurfaceFeature(20), IfcCourse(16+2), IfcEarthworksFill(16) | ~80 layered | Material stacking: subgrade→base→binder→surface |
| **Rail** | IfcRailwayPart(2), IfcTrackElement(66), IfcRail(4), IfcCourse(2) | ~74 repetitive | Sleeper grid → perfect TILE verb candidate |
| **Landscape** | IfcGeographicElement(76), IfcSign(10+4) | ~90 scattered | Trees, grass, apples — SPRAY verb candidate |
| **MEP/Plumb** | IfcPipeSegment(24), IfcElementAssembly(4, manholes) | ~29 linear | Sewer runs → ROUTE verb candidate |
| **Shared** | IfcMember, IfcBeam, IfcColumn, IfcWall, IfcSlab, IfcRailing | ~40 | Already in REFERENCE_CLASSES |

### 2.4 Multi-Facility Problem

Key difference from buildings: **a single IFC file can contain multiple facilities.**

- Infra-Bridge.ifc → 3 IfcBridge (road bridge, rail bridge, road-rail bridge)
- Infra-Road.ifc → 5 IfcRoad
- Infra-Landscaping.ifc → 10 facilities across 3 domains

**Spec conflict:** `BomValidator` enforces `BUILDING count == 1` (line 114-118).
Infrastructure files would need either:
- (a) One extracted DB per facility (split at extraction) — keeps BomValidator untouched
- (b) Generalize BomValidator to `root_facility_count >= 1` — requires spec change

**Recommendation:** Option (a). Extract per-facility, not per-file. This preserves the
single-root-BOM invariant that BOMWalker, singularity check, and delta tests all assume.
The extraction script already takes `--classes` filter; add a `--facility` filter.

---

## 3. Spec Gap Analysis

Cross-referencing the infrastructure probe against master specs. **SRS must be valid
before any code changes** (per `memory/feedback_srs_before_code.md`).

### 3.1 Conflicts with Current Specs

| # | Spec | Section | Conflict | Severity |
|---|------|---------|----------|----------|
| **G1** | BBC.md §1 | "A building is a manufactured product" | Infrastructure is a **linear asset**, not a manufactured product. Road sections are poured layers, not assembled components. The manufacturing metaphor breaks for in-situ work. | **Conceptual — does not block extraction pipeline**, but `doc_base_type` vocabulary needs extension (RE=Residential, CO=Commercial, **IN=Infrastructure?**) |
| **G2** | DATA_MODEL.md §1.3 | "Storey-to-Floor BOM Mapping" | Infrastructure has no storeys. Segments are functional (pier, deck, carriageway), not spatial (ground floor, level 1). | **Naming only** — the mapping logic is generic, just the column name `storey` and YAML key `storeys:` are misleading |
| **G3** | DATA_MODEL.md §1.4 | `bom_type = 'BUILDING'`, `doc_base_type = 'RE'` | Infrastructure needs `bom_type = 'FACILITY'` (or keep BUILDING as abstract root) and new `doc_base_type` values | **Schema vocabulary** — m_bom schema already stores bom_type as TEXT, no migration needed |
| **G4** | YAMLGuide.md §Schema v1 | `doc_base_type: RE` "always RE for residential" | Infrastructure is not residential. Need `doc_base_type` IN/CI/LI for infra. | **Spec text incorrect** — should say "RE for residential, CO for commercial, IN for infrastructure" |
| **G5** | YAMLGuide.md §storeys | "required" | Infrastructure has no storeys. The key should accept `segments:` as alias. | **Parser change needed** — ClassificationYaml.java reads hardcoded key `"storeys"` |
| **G6** | BomValidator.java:49 | `WORLD_COORD_THRESHOLD_M = 500` | Infrastructure elements within a facility span max ~80m (these demo files). The 500m guard applies to **parent-relative offsets**, not absolute coords. **Actually safe** — but needs explicit documentation. | **No conflict** — the guard is correct as-is (checks dx/dy/dz on m_bom_line, which are parent-relative). Document this explicitly. |
| **G7** | BomValidator.java:114 | `buildingCount == 1` | Multi-facility IFC files would violate this if extracted whole. | **Blocked by extraction strategy** — if we extract per-facility (§2.4 option a), this is not hit |
| **G8** | extract.py:150-165 | REFERENCE_CLASSES list | Missing 10+ IFC4X3 element types (IfcTrackElement, IfcCourse, IfcSurfaceFeature, IfcEarthworksFill, IfcGeographicElement, IfcSign, IfcRail, IfcFooting, IfcElementAssembly, IfcDiscreteAccessory) | **Extraction gap** — elements would be silently skipped |
| **G9** | extract.py:168-180 | DISCIPLINE_MAP | No infrastructure discipline mapping. IfcCourse → ? IfcTrackElement → ? IfcGeographicElement → ? | **Need INFRA_DISCIPLINE_MAP** extension |
| **G10** | extract.py:278-313 | `extract_from_ifc_to_reference()` spatial structure | Only extracts IfcBuilding + IfcBuildingStorey + IfcSpace. Does NOT extract IfcRoad/IfcBridge/IfcRailway or their parts. | **Critical gap** — spatial_structure table would be empty for infra |
| **G11** | ExtractionPopulator.java:240-244 | `classifyOrientation()` | Hardcoded to IfcWall/IfcPlate/IfcWallStandardCase. Infra linear elements (IfcCourse, IfcPipeSegment) need orientation too. | **Minor gap** — orientation is advisory, not blocking |
| **G12** | ExtractionPopulator.java:256-265 | Z-band storey resolution | Hardcoded Z-thresholds (0, 4.5, 8, 11.5, 15, 18) for Terminal. Infrastructure segments are not Z-banded. | **Not applicable** — infra elements resolve via IfcFacilityPart containment, not Z-band. No conflict. |

### 3.2 Gaps NOT in Current Specs (new SRS needed)

| # | Gap | What's needed | Priority |
|---|-----|---------------|----------|
| **N1** | No `classify_bridge.yaml` example | Draft a sample infra YAML with `segments:` key, IN doc_base_type, and infra disciplines | HIGH — needed before any extraction attempt |
| **N2** | No infra discipline taxonomy | Define mapping: IfcCourse→ROAD_STR, IfcTrackElement→RAIL, IfcGeographicElement→LAND, IfcPipeSegment→MEP, IfcSign→SIGN | MEDIUM |
| **N3** | No LOD strategy for infra elements | Infrastructure LODs differ from buildings. A road course is a prismatic extrusion, not a catalog item. IfcGeographicElement (trees) may have parametric LODs. | HIGH — must define before `--to library` extraction |
| **N4** | No `IfcMapConversion` handling in extraction | extract.py uses `USE_WORLD_COORDS = True`. Infra world coords are UTM (hundreds of km from origin). Need to either (a) subtract IfcMapConversion origin at extraction, or (b) use local coords. | HIGH — affects all coordinate values in elements_rtree |
| **N5** | No multi-file federation strategy | The 9 IFC files represent one project. Building + infra + landscaping must federate. Current pipeline processes one file → one DB. | MEDIUM — can extract each file separately, federate in YAML |
| **N6** | Layered composition has no BOM pattern | Road cross-section = vertical stack of material layers. Not a "mirrored pair" or "storey with rooms". Needs a new composition type or maps to existing vertical BOM. | LOW — can model as parent BOM with dz-offset children |
| **N7** | Linear referencing not in any spec | Roads/rails position elements by chainage along alignment curve. BOM assumes Cartesian dx/dy/dz. Conversion must happen at extraction. | LOW — demo files already use Cartesian (small scale) |

### 3.3 Component Library LOD Population (one-time process)

Per `YAMLGuide.md` §Step 1, each IFC file goes through a one-time LOD extraction
to `component_library.db` before the Rosetta pipeline can use it. For infrastructure:

**`extract.py --to library` needs:**

1. **Extended REFERENCE_CLASSES** for infra types (G8 above)
2. **Extended CATEGORY_MAP** for infra categories:
   ```
   IfcCourse → PAVEMENT_LAYER, IfcTrackElement → TRACK_ELEMENT,
   IfcSurfaceFeature → ROAD_MARKING, IfcEarthworksFill → EARTHWORK,
   IfcGeographicElement → LANDSCAPE, IfcSign → SIGNAGE,
   IfcRail → RAIL, IfcFooting → FOOTING, IfcElementAssembly → ASSEMBLY
   ```
3. **Extended ATTACHMENT_MAP** for infra placement semantics:
   ```
   IfcCourse → BOTTOM (layered on subgrade), IfcTrackElement → BOTTOM (on ballast),
   IfcGeographicElement → BOTTOM (on ground), IfcSign → BOTTOM (pole-mounted)
   ```
4. **Extended DISCIPLINE_MAP** for infra disciplines:
   ```
   IfcCourse → ROAD, IfcTrackElement → RAIL, IfcRail → RAIL,
   IfcSurfaceFeature → ROAD, IfcEarthworksFill → GEO,
   IfcGeographicElement → LAND, IfcSign → SIGN,
   IfcFooting → STR, IfcElementAssembly → STR
   ```

**Extraction order (recommended):**
```bash
# Step 0: LOD population (one-time, per-file)
python3 tools/extract.py --to library reference/infrastructure/Infra-Bridge.ifc \
    --classes IfcBeam,IfcColumn,IfcMember,IfcWall,IfcSlab,IfcFooting,IfcRailing,IfcEarthworksFill,IfcSign

# Step 1: Reference extraction (per-facility, for Rosetta)
python3 tools/extract.py --to reference reference/infrastructure/Infra-Bridge.ifc \
    -o DAGCompiler/lib/input/Infra_Bridge_extracted.db
```

---

## 4. Spatial Hierarchy Deep Dive

### 4.1 Actual hierarchy from probed files

```
IfcProject
  └─ IfcSite "IFC Infra Sample Project" (environment)
       └─ IfcSite (domain — 6 sites across files)
            │
            ├─ IfcRoad (5 instances across Road.ifc + Landscaping.ifc)
            │   └─ IfcRoadPart (26 total: CARRIAGEWAY, SHOULDER, PARKING, ...)
            │       └─ elements: IfcCourse, IfcSurfaceFeature, IfcEarthworksFill
            │
            ├─ IfcBridge (3 instances: road_bridge, rail_bridge, road_rail_bridge)
            │   └─ IfcBridgePart (18 total: ABUTMENT, PIER, DECK, ...)
            │       └─ elements: IfcBeam, IfcColumn, IfcFooting, IfcMember, IfcWall, IfcSlab
            │
            └─ IfcRailway (2 instances)
                └─ IfcRailwayPart (2 total: track section)
                    └─ elements: IfcTrackElement(66 sleepers), IfcRail(4), IfcCourse(2 ballast)
```

### 4.2 Mapping to BOM hierarchy

```
Infrastructure BOM (proposed)          Building BOM (existing)
──────────────────────────             ──────────────────────
FACILITY (IfcBridge)                   BUILDING (IfcBuilding)
  ├─ SEGMENT (Abutment_North)           ├─ FLOOR (Ground Floor)
  │   └─ LEAF (IfcFooting, IfcWall)     │   └─ LEAF (IfcDoor, IfcWall)
  ├─ SEGMENT (Pier_1)                   ├─ FLOOR (Level 1)
  │   └─ LEAF (IfcColumn, IfcBeam)      │   └─ LEAF (IfcWindow, IfcSlab)
  └─ SEGMENT (Deck)                    └─ FLOOR (Roof)
      └─ LEAF (IfcSlab, IfcRailing)        └─ LEAF (IfcRoof)
```

**Decision point:** Should bom_type be `FACILITY`/`SEGMENT` (new vocabulary) or
keep `BUILDING`/`FLOOR` (abstract, reused)? Recommendation: **keep BUILDING/FLOOR
as abstract terms** — they are already in the BOM schema, all validators, all tests.
Introducing new values requires touching every WHERE clause. Instead, add a
`facility_domain` column to m_bom (values: BUILDING, ROAD, BRIDGE, RAILWAY) for
downstream filtering. This is the iDempiere approach — AD_Ref_List, not new DocBaseType.

---

## 5. What Already Works (no change needed)

1. **BOM hierarchy** — parent-relative dx/dy/dz offsets are domain-agnostic
2. **YAML as invention boundary** — a `classify_bridge.yaml` with `segments:` alias
3. **Product catalog** — `component_library.db` stores geometry by hash, domain-agnostic
4. **BOMWalker** — walks any BOM tree regardless of what BUILDING/FLOOR means
5. **Compilation pipeline** — reads BOM, emits elements, doesn't care about domain
6. **Gate tests (G1-G6)** — count, volume, digest, provenance are universal
7. **VerbDetector** — purely geometric pattern detection, already infra-agnostic
8. **Python extractor `get_storey_for_element()`** — DONE (2026-03-16): handles IfcFacilityPart
9. **500m world-coord guard** — checks parent-relative offsets, not absolute. Infra elements
   span max ~80m within a facility. Safe as-is.

---

## 6. What Needs Vocabulary Generalization (spec changes first)

### 6.1 Extraction Layer (Python) — DONE via `ad_ifc_class_map`

IFC class maps are now data-driven from `disc_validation.db` authority table
`ad_ifc_class_map` (46 rows). Adding a new IFC type = one SQL INSERT, zero code.
See [`DISC_VALIDATION_DB_SRS.md`](DISC_VALIDATION_DB_SRS.md) §5.2 and
[`SourceCodeGuide.md`](SourceCodeGuide.md) for implementation details.

| File | Change | Gap # |
|------|--------|-------|
| `extract.py:150-165` | Add infra types to REFERENCE_CLASSES | G8 |
| `extract.py:168-180` | Add infra discipline mapping to DISCIPLINE_MAP | G9 |
| `extract.py:236-248` | Add infra categories/attachments to CATEGORY_MAP, ATTACHMENT_MAP | G8 |
| `extract.py:287-313` | Extract IfcRoad/IfcBridge/IfcRailway and parts into spatial_structure | G10 |
| `extract.py:316-318` | Handle `USE_WORLD_COORDS` with IfcMapConversion subtraction | N4 |

### 6.2 Java Pipeline (code changes, after SRS update)

| File | Change | Gap # |
|------|--------|-------|
| `ClassificationYaml.java:103` | Accept `segments:` as alias for `storeys:` | G5 |
| `StructuralBomBuilder.java:70,75,107,112` | Parameterize "BUILDING"/"FLOOR" literals from config | G3 |
| `BomValidator.java:114` | Accept multi-facility if extraction splits per-facility (no change if option a) | G7 |
| `ExtractionPopulator.java:240` | Extend orientation classification to infra element types | G11 |
| `YAMLGuide.md` | Update doc_base_type docs: RE/CO/IN | G4 |
| `DATA_MODEL.md §1.3` | Rename "Storey-to-Floor" → "Segment Mapping" in prose | G2 |

### 6.3 Component Library (one-time LOD)

| Step | Command | Purpose |
|------|---------|---------|
| 1 | `extract.py --to library Infra-Bridge.ifc --classes ...` | Populate bridge LODs |
| 2 | `extract.py --to library Infra-Road.ifc --classes ...` | Populate road LODs |
| 3 | `extract.py --to library Infra-Rail.ifc --classes ...` | Populate rail LODs |
| 4 | `extract.py --to library Infra-Landscaping.ifc --classes ...` | Populate landscape LODs |
| 5 | `extract.py --to library Infra-Plumbing.ifc --classes ...` | Populate infra MEP LODs |

These populate geometry into `component_library.db` (INSERT OR IGNORE = safe alongside
existing building geometries). Products are keyed by geometry_hash — no collision risk.

---

## 7. Verb Pattern Opportunities

Infrastructure elements exhibit the same repetitive patterns that verbs already handle.

**Predicted vs Actual (S37b Rosetta Stone results):**

| Domain | Pattern | Predicted | Actual (S37b) | Why |
|--------|---------|-----------|---------------|-----|
| Rail | 66 sleepers along track | TILE | **CLUSTER** | Diagonal alignment, not 2D grid — TILE needs rectangular grid |
| Rail | 4 rails | ROUTE | **CLUSTER** | Only 4 elements — too few for ROUTE pattern |
| Road | 20 road markings | TILE | **CLUSTER** | Irregular positions, not grid-uniform |
| Road | 8 courses per segment | TILE | **SNAP** | 2 per product per segment — below MIN_GROUP=4 |
| Bridge | 8 members in superstructure | TILE | **CLUSTER** | Non-uniform spacing |
| Bridge | 4 footings/columns per pier | TILE | **CLUSTER** | Below TILE threshold |
| Landscape | 76 geographic elements | SPRAY | (not yet compiled) | |
| Plumbing | 24 pipe segments | ROUTE | (not yet compiled) | |

**Key finding:** CLUSTER is the dominant verb for small infra models. TILE requires
a rectangular 2D grid (≥2 unique X AND Y positions, uniform step). Infra elements
on diagonal alignments or with too few instances per segment fall to CLUSTER (lossless
catch-all). Larger models with more elements per segment may trigger TILE/ROUTE.

**Factorization results:**
- RL: 73 elements → 5 BOM lines (70 CLUSTER instances) = **93% compression**
- RD: 53 elements → 34 BOM lines (20 CLUSTER instances) = **36% compression**
- BR: 48 elements → 26 BOM lines (28 CLUSTER instances) = **54% compression**

### 7.1 Mined Validation Rules (from BR Rosetta Stone — 2026-03-19)

The bridge Rosetta Stone (48 elements, 10/10 PASS) yields the following mineable
patterns for `AD_Val_Rule` insertion. These are **observed from the IFC reference**,
not invented — same mining approach as TE sprinkler spacing (see `TE_MINING_RESULTS.md`).

**Structural dimension rules:**

| Rule ID | Discipline | Pattern | Mined value | Source segment |
|---------|-----------|---------|-------------|----------------|
| `BRIDGE_ARCH_MEMBER_DIM` | STR | IfcMember in superstructure | avg 6080×5531×4084mm, 8 instances | railbridge - superstructure |
| `BRIDGE_PIER_COLUMN_RAIL` | STR | IfcColumn in rail pier | 3499×4561×3780mm, 4 instances | rail bridge - pier |
| `BRIDGE_PIER_COLUMN_ROAD` | STR | IfcColumn in road pier | 2276×3393×2286mm, 3 instances | road river bridge - pier |
| `BRIDGE_FOOTING_RAIL` | STR | IfcFooting under rail pier | 6231×7293×1000mm, 4 instances | rail bridge - pier |
| `BRIDGE_FOOTING_ROAD` | STR | IfcFooting under road pier | 4319×5380×700mm, 3 instances | road river bridge - pier |
| `BRIDGE_DECK_THICKNESS` | STR | IfcSlab in deck | 56mm height (thin plate) | road rail bridge - deck |
| `BRIDGE_APPROACH_SLAB` | STR | IfcSlab in approach | 441mm height, 2 instances | road rail bridge - approach |

**Ratio rules (cross-element):**

| Rule ID | Pattern | Mined value | Derivation |
|---------|---------|-------------|------------|
| `FOOTING_SPREAD_RATIO_RAIL` | Footing W / Column W | 6231/3499 = 1.78x | Rail pier: footing must spread wider than column |
| `FOOTING_SPREAD_RATIO_ROAD` | Footing W / Column W | 4319/2276 = 1.90x | Road pier: similar ratio |
| `FOOTING_DEPTH_RATIO` | Footing H / Column H | 1000/3780 = 0.26x (rail), 700/2286 = 0.31x (road) | Footing depth ~26-31% of column height |

**Placement rules:**

| Rule ID | Pattern | Mined value | Derivation |
|---------|---------|-------------|------------|
| `BRIDGE_RAILING_HEIGHT` | IfcRailing on deck | H=956mm (2 instances) | Safety: bridge railing minimum height |
| `BRIDGE_SIGNAGE_COUNT` | IfcSign per superstructure | 4 signs, 1401×826×400mm | Regulatory: identification signage |
| `BRIDGE_SPANDREL_WALL` | IfcWall in superstructure | 17546×10390×4484mm, 4 instances | Arch infill walls |

**Segment Z-continuity rules:**

| Rule ID | Pattern | Mined value | Derivation |
|---------|---------|-------------|------------|
| `SEGMENT_Z_STACK_PIER_DECK` | Pier z_max ≈ Deck z_min | pier max=-0.11, deck min=-0.09 (22mm gap) | Substructure supports superstructure |
| `SEGMENT_Z_STACK_PIER_SUPER` | Pier z_max ≈ Superstructure z_min | pier max=3.29, super min=3.29 (0mm) | Rail pier tops meet rail superstructure |
| `SEGMENT_Z_BELOW_GROUND` | Pier extends below Z=0 | road pier z_min=-3.5m, rail pier z_min=-1.49m | Piers are embedded in ground |

**Next steps:** INSERT these as `AD_Val_Rule` rows in `validation.db` with
`mining_source='Infra_Bridge'`, then use them in `PlacementValidator` for
infrastructure generative compilation. Same pattern as TE-mined NFPA13 spacing rules.

---

## 8. Conceptual Differences (require design decisions)

### 8.1 Linear Referencing

Roads/railways position elements by **chainage along an alignment curve**, not by
Cartesian offset from a box corner. The BOM's dx/dy/dz model assumes Cartesian.

**Resolution:** Convert chainage → Cartesian at extraction time. The demo files already
use Cartesian (small scale, straight alignments). Real-world curved alignments would
need IfcAlignment → polyline → XY at extraction. This is extraction-layer work only.

### 8.2 Layered Composition

A road section is a stack of material layers (subgrade → base → binder → surface),
not an assembly of discrete objects. This maps to a vertical BOM:

```yaml
segments:
  Carriageway_01:
    code: CW01
    bom_category: ROAD
    role: CARRIAGEWAY
    seq: 1010
    # Children are layers with dz offsets (subgrade at 0, base at +200mm, ...)
```

**No new BOM type needed.** Parent = road section, children = layers with dz offsets.
This is the same pattern as a building floor with elements at different heights.

### 8.3 No Rooms

Scope spaces for infrastructure are functional zones (lane, shoulder, median, pier cap)
not enclosed rooms. The centroid-in-AABB scope assignment still works geometrically.

---

## 9. Proposed Extraction → Rosetta Path

### Phase I: LOD Population — DONE (2026-03-19)

Maps extended in extract.py: REFERENCE_CLASSES (+11 types), DISCIPLINE_MAP (+7 infra),
CATEGORY_MAP (+11 infra), ATTACHMENT_MAP (+10 infra). LOD extraction completed for all 9 files.

**Results in component_library.db:**

| IFC4X3 type | Category | Discipline | Unique LODs |
|-------------|----------|-----------|-------------|
| IfcTrackElement | TRACK_ELEMENT | RAIL | 1 (66 instances → 1 unique mesh) |
| IfcRail | RAIL | RAIL | 2 |
| IfcCourse | PAVEMENT_LAYER | ROAD | 7 |
| IfcSurfaceFeature | ROAD_MARKING | ROAD | 1 |
| IfcEarthworksFill | EARTHWORK | GEO | 8 |
| IfcGeographicElement | LANDSCAPE | LAND | 29 |
| IfcSign | SIGNAGE | SIGN | 4 |
| IfcFooting | FOOTING | STR | 4 |
| IfcElementAssembly | ASSEMBLY | STR | 2 |
| IfcDiscreteAccessory | ACCESSORY | STR | 2 |
| IfcChimney | CHIMNEY | ARC | 1 |

### Phase II: Reference Extraction — DONE (2026-03-19)

Spatial structure extraction extended to handle IfcRoad/IfcBridge/IfcRailway and their
IfcFacilityPart children (G10 fix). Reference DBs produced:

| File | Elements | Geometries | Segments | Failed |
|------|----------|-----------|----------|--------|
| Infra_Bridge_extracted.db | 48 | 30 | 9 IfcBridgePart | 2 |
| Infra_Road_extracted.db | 53 | 23 | 6 IfcRoadPart | 2 |
| Infra_Rail_extracted.db | 73 | 5 | 2 IfcRailwayPart | 2 |
| Infra_Landscaping_extracted.db | 101 | 41 | 0 (site-level) | 11 |
| Infra_Plumbing_extracted.db | 27 | 9 | 0 (bridge context) | 2 |

**Segment assignment quality:**
- Rail: 72/73 elements assigned to "Rail track" segment. Good.
- Bridge: 47/48 elements assigned to named parts (pier, deck, superstructure). Good.
- Road: 32/53 elements assigned to named parts. 21 "Unknown" (site-contained).
- Landscaping: 101/101 "Unknown" — correct, IfcGeographicElements belong to IfcSite.
- Plumbing: 27/27 "Unknown" — pipes contained in bridge, not in facility parts.

**N4 (IfcMapConversion):** Not yet applied. World coordinates are in UTM millimetres.
Parent-relative offsets in BOM will be bounded since elements are within facility AABB.
Full IfcMapConversion subtraction is deferred — not blocking for Rosetta pipeline.

### Phase III: Classification YAML — DONE (S34 + S37b)
1. `classify_br.yaml` (bridge) — DONE (S34)
2. `classify_rd.yaml` (road) — DONE (S37b)
3. `classify_rl.yaml` (rail) — DONE (S37b)
4. ClassificationYaml.java accepts `segments:` alias — DONE (S34)

### Phase IV: Gate Convergence — DONE (S37b)
1. All 3 infra Rosetta Stones pass:
   - BR 10/10 PASS (48 elements, 26 BOM lines, 28 CLUSTER instances)
   - RD 4/4 PASS (53 elements, 34 BOM lines, 20 CLUSTER instances)
   - RL 4/4 PASS (73 elements, 5 BOM lines, 70 CLUSTER instances)
2. Key fix: `IFCtoBOMPipeline.java` — route `doc_base_type: IN` to DisciplineBomBuilder
   (was falling into RE/StructuralBomBuilder path which skips VerbDetector)
3. Bridge delta test: enbloc=48 == walkthru=48, 0 geometry divergences

---

## 10. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Existing SH/DX/TE breaks | HIGH | All changes are additive. `segments:` alias doesn't affect `storeys:` parsing. REFERENCE_CLASSES additions are INSERT OR IGNORE. |
| Multi-facility extraction confusion | MEDIUM | Extract per-facility (option a), not per-file. Document clearly. |
| IfcMapConversion coordinate mess | HIGH | Must subtract origin before storing in elements_rtree. Otherwise all coords are 729km from origin. |
| Missing IFC4X3 classes in ifcopenshell | LOW | ifcopenshell 0.7+ supports IFC4X3. Verify with probe script. |
| Verb detection on linear elements | LOW | VerbDetector is purely geometric — works regardless of domain. **Confirmed S37b:** CLUSTER catches all infra patterns. TILE needs rectangular grids (not diagonal alignments). |

---

## 11. Summary

| Question | Answer |
|----------|--------|
| Is the BOM model sufficient? | Yes — parent-relative offsets are domain-agnostic |
| Is the YAML model sufficient? | Yes — `segments:` alias is isomorphic to `storeys:` |
| What is the single adaptation point? | Extraction layer (Python + ExtractionPopulator) |
| Does it break existing buildings? | No — additive vocabulary, existing paths unchanged |
| How many spec gaps? | **12 gaps (G1-G12)** in existing specs, **7 new items (N1-N7)** not yet specified |
| Critical blockers before code? | ~~G8 (REFERENCE_CLASSES)~~, ~~G10 (spatial structure extraction)~~, N4 (IfcMapConversion — deferred, not blocking) |
| LOD library population? | **DONE** — all 9 files extracted, 33 infra products + geometries in component_library.db |
| Rosetta Stone pipeline? | **DONE** — BR 10/10, RD 4/4, RL 4/4 with verb detection (CLUSTER) |
| Recommended approach? | Extract per-facility, keep BUILDING/FLOOR as abstract bom_type, add facility_domain column |

**Completed (2026-03-19):**
1. SRS: Updated `YAMLGuide.md` §Schema — documented `segments:` alias, IN doc_base_type, pre-flight checklist
2. SRS: Updated `DATA_MODEL.md` §1.3 — renamed "Storey-to-Floor" to "Segment Mapping"
3. Code: Extended extract.py — REFERENCE_CLASSES (+11), DISCIPLINE_MAP (+7), CATEGORY_MAP (+11), ATTACHMENT_MAP (+10)
4. Code: Fixed extract.py spatial structure to extract IfcRoad/IfcBridge/IfcRailway + IfcFacilityPart children (G10)
5. Code: Fixed extract.py print_summary crash for library target
6. Data: LOD population — all 9 IFC files → component_library.db (INSERT OR IGNORE, safe)
7. Data: Reference extraction — 5 infra DBs + 4 building DBs → DAGCompiler/lib/input/

**Completed (2026-03-20, S37b):**
8. Code: `classify_rd.yaml` + `dsl_rd.bim` (road Rosetta Stone)
9. Code: `classify_rl.yaml` + `dsl_rl.bim` (rail Rosetta Stone)
10. Fix: `IFCtoBOMPipeline.java` — route `IN` to DisciplineBomBuilder (enables verb detection)
11. Result: BR 10/10, RD 4/4, RL 4/4 — all infra Rosetta Stones pass
12. Verb: CLUSTER dominant (not TILE) — diagonal alignments, small group sizes

**Completed (2026-03-20, S37c):**
13. Code: `PlacementContext` interface — abstract container for rooms AND terrain
14. Code: `RoomContext` (building), `AlignmentContext` (infrastructure) — same API
15. Code: `TerrainSnap` — ON_SURFACE / ABOVE / BELOW / PIER snap modes
16. Data: Real terrain from Federation `pdf_terrain/samples/survey_highres_extracted.json`
    — 689 ground elevation points, 294m × 229m area, Z: 28.1–48.1m (river valley)
17. Witnesses: 13 PlacementContext tests, 4 TerrainSnap tests (road layers, bridge,
    tunnel, drag simulation), all against real terrain data
18. Result: Drag trail proves Z follows terrain: 43.8→43.6→43.2→43.4→42.8→43.4m

---

## 8. Terrain-Following Placement Model (S37)

### 8.1 The Insight: Terrain = Container

Buildings place elements inside rooms. Infrastructure places elements ON/ABOVE/BELOW
terrain. Both answer the same questions:

| Question | Room (Building) | Terrain (Infrastructure) |
|----------|----------------|------------------------|
| Does it fit? | AABB containment | Width ≤ corridor |
| What Z? | Constant (storey level) | Varies — `elevationAt(x,y)` |
| Interface | `PlacementContext` | `PlacementContext` |

The `PlacementContext` abstraction makes the Designer polymorphic: same snap/validate
loop works for a bedroom at storey Z=3000mm and a road course at terrain Z=43410mm.

### 8.2 Terrain Data Source

The Federation `pdf_terrain` addon extracts elevation points from survey PDFs:

```
Survey PDF → Google Vision OCR → JSON (689 points)
  ├─ ground_elevations[]: { x: pixel, y: pixel, z: elevation_m }
  ├─ scale: 0.0423 m/pixel
  └─ image_dimensions: 9934 × 7017 px
World coords: x = px × scale,  y = (img_h - py) × scale,  z = elevation_m
```

Sample: `pdf_terrain/samples/survey_highres_extracted.json`
— 294m × 229m survey area, 20m elevation range (28–48m), river valley slope.

### 8.3 Terrain Snap Modes

Each infrastructure type relates differently to the terrain surface:

| Mode | Formula | Use Case | User Control |
|------|---------|----------|-------------|
| `ON_SURFACE` | Z = terrain + offset | Road layers, sleepers, ballast | Layer stack offset (0–490mm) |
| `ABOVE` | Z = terrain + clearance | Bridge deck, overhead lines | Min clearance (flood/nav level) |
| `BELOW` | Z = terrain - cover - height | Tunnel, pipeline, foundation | Min cover depth |
| `PIER` | Z = terrain (base), extends up | Bridge piers, abutments | Height = clearance + deck depth |

### 8.4 Road Layer Stacking on Terrain

Road pavement stacks 4 layers on the terrain surface. Each layer's Z is computed
relative to the terrain + cumulative offset of layers below:

```
Z ↑ (mm)
43900 │─── road top (terrain + 490mm) ─────── surface course (40mm)
43860 │                                        binder course (80mm)
43780 │                                        base course (120mm)
43660 │                                        subgrade (250mm)
43410 │═══ terrain surface ════════════════════ elevationAt(x,y)
      └──────────────────────────────────────────────────────────
```

Proven by witness W-TERRAIN-SNAP-1 against real survey data.

### 8.5 Bridge Cross-Section on Terrain

Bridge elements span above terrain. The deck floats at clearance height;
piers extend from terrain surface up to deck:

```
Z ↑ (mm)
50810 │─── deck top ────────────────────────── deck slab (2400mm)
48410 │─── deck base (terrain + 5000mm) ────── min clearance
      │    │         │         │
      │    │  pier   │  pier   │              pier height = 7400mm
      │    │         │         │
43410 │════╧═════════╧═════════╧═══════════════ terrain surface
      └──────────────────────────────────────────────────────────
```

Proven by witness W-TERRAIN-SNAP-2.

### 8.6 Tunnel Below Terrain

Tunnels are placed below the terrain surface with a minimum cover depth.
The tube top must remain below terrain at all points along the alignment:

```
Z ↑ (mm)
43410 │═══ terrain surface ════════════════════
40410 │─── tunnel top (terrain - 3000mm cover)
      │    ┌──────────────────┐
      │    │   tunnel tube    │               diameter = 6000mm
      │    │    (6000mm)      │
34410 │────└──────────────────┘─── tunnel base
      └──────────────────────────────────────────────────────────
```

Proven by witness W-TERRAIN-SNAP-3.

### 8.7 Interactive Drag — Z Follows Terrain

During interactive design, the user drags an element across the viewport.
At each mouse position, `TerrainSnap.computeZ()` queries the terrain elevation
and updates the bbox Z. The wireframe bbox "flows" along the terrain surface.

Drag simulation across 294m of real terrain (10 steps):
```
Position:  30m   60m   90m   120m  150m  180m  210m  240m  270m  300m
Elev (m): 43.8  43.8  43.6  43.6  43.2  43.4  42.8  43.4  43.8  43.0
```

The 1m variation across 30m steps shows the river valley gradient.
During drag, wireframe bboxes are shown. On CO save to output DB, the
actual geometry is computed incrementally and shape updates to match.

### 8.8 Engineering Controls

The `offset` parameter in TerrainSnap is the engineer's primary design control:

| Concern | Control | Validator Rule |
|---------|---------|---------------|
| Design level vs natural ground | `offsetMm` per element | — |
| Cut depth (excavation) | `BELOW` mode offset | min_cover_depth |
| Fill height (embankment) | `ON_SURFACE` offset | max_fill_height |
| Minimum cover (tunnel/pipe) | `BELOW` offset ≥ minimum | min_cover_mm |
| Flood clearance (bridge) | `ABOVE` offset ≥ flood level | min_clearance_mm |
| Max gradient (slope %) | Compare Z at consecutive stations | max_gradient_pct |
| Super-elevation (road banking) | Per-lane cross-slope offset | max_crossfall_pct |
| Smooth contour (cut/fill transition) | Spline smoothing between zones | max_vertical_curve_radius |

The snap→validate→adjust loop is the same as building rooms, but terrain-aware.
The user adjusts offsets until the design meets code — validator checks each rule.

**Next steps:**
1. Landscaping + Plumbing Rosetta Stones (classify_ls.yaml, classify_pl.yaml)
2. Larger infra models for TILE/ROUTE verb discovery (CORE_SRS §1 Scale Research)
3. Wire TerrainSnap into Designer snap() loop — compute Z per bbox during validation
4. N4 (IfcMapConversion subtraction) — deferred until real-world infra project needs it

See also: [`YAMLGuide.md`](YAMLGuide.md) §Invention Boundary,
[`DATA_MODEL.md`](DATA_MODEL.md) §Reference DB,
[`BOMBasedCompilation.md`](BOMBasedCompilation.md) §4 Tack Convention,
[`LAST_MILE_PROBLEM.md`](LAST_MILE_PROBLEM.md) §Gap 4 (spec sources).
