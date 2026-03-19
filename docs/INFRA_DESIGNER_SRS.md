# Infrastructure Designer SRS

**Version:** 1.1 | **Date:** 2026-03-20
**Scope:** Designer UX for infrastructure (bridge/road/rail) + terrain layer
**Companion:** `CORE_SRS.md` §3.1 (gap matrix), `InfrastructureAnalysis.md` (IFC4X3 mapping)
**Session:** S37 — Phase I-1 DONE (FacilityType + dual-mode loadRules + snap wiring)

---

## Current State (after S37)

| Layer | Status | What works |
|-------|--------|------------|
| Rule loading | DONE | FacilityType.BRIDGE loads 13 rules, ROAD 10, RAIL 7 |
| Rule isolation | DONE | Building mode excludes Infra_*, infra excludes building |
| API surface | DONE | `snap(bboxes, jurisdiction, gridMm, facilityType)`, `setJurisdiction(j, bboxes, ft)` |
| `extractActual()` | DONE | Maps width_mm, depth_mm, height_mm, thickness_mm, avg_* for infra |
| `snap()` loop | DONE | Processes SEGMENT + LEAF bomTypes alongside ROOM |
| Rosetta Stones | **DONE** | BR 10/10, RD 4/4, RL 4/4 — CLUSTER verb detection active |
| Layout generator | GAP | Room-grid only — no alignment/span-based infra layout |
| Component library | **DONE** | 33 infra products in component_library.db (registered by ExtractionPopulator) |
| Terrain layer | GAP | Federation addon has PoC — not wired to Designer |

---

## §0 Rosetta Stone BOM Model (from parallel session S37b)

### §0.1 Infrastructure YAML Convention

The parallel session established the infra Rosetta Stone pattern. Key decisions:

**`doc_base_type: IN`** is the infrastructure discriminator. All infra YAMLs use this.
The Designer API can check `docBaseType == "IN"` to select infra mode.

**`segments:` is an alias for `storeys:`** in the YAML schema. The pipeline treats
them identically — same hierarchy, different label. This preserves the
`BUILDING → FLOOR → LEAF` abstraction as `FACILITY → SEGMENT → LEAF`.

### §0.2 Road Rosetta Stone (`classify_rd.yaml`)

```
building_type: Infra_Road
prefix: RD
doc_base_type: IN
segments:
  road  - carriageway:            { code: CW1,  bom_category: CW,  role: CARRIAGEWAY }
  road carriageway:               { code: CW2,  bom_category: CW,  role: CARRIAGEWAY }
  road carriageway - bridge road: { code: CWBR, bom_category: CW,  role: CARRIAGEWAY }
  road - parking:                 { code: PKG,  bom_category: PKG, role: PARKING }
  Unknown:                        { code: MISC, bom_category: MS,  role: MISC }

disciplines:
  GEO:  [IfcEarthworksFill]       # Subgrade/fill
  PAV:  [IfcCourse]               # Pavement layers (subbase→base→binder→surface)
  MARK: [IfcSurfaceFeature]       # Line markings
  ARC:  [IfcBuildingElementProxy]  # Guardrails, signs
```

**BOM structure:** 4 carriageway segments + parking. Each segment contains 8 elements:
2 subgrade + 2 base course + 2 binder + 2 surface. Layer stacking:
subgrade (250mm) → base (120mm) → binder (80mm) → surface (40mm) = 490mm total.
20 line markings along road surface.

**DSL pattern (`dsl_rd.bim`):** EN-BLOC only — extracted, not generative.
```
BUILDING "Infra_Road" type:INFRASTRUCTURE profile:"IFC4X3_Road" {
    SEGMENT "road  - carriageway" { ... }
    SEGMENT "road carriageway" { ... }
    SEGMENT "road carriageway - bridge road" { ... }
    SEGMENT "road - parking" { ... }
}
```

### §0.3 Rail Rosetta Stone (`classify_rl.yaml`)

```
building_type: Infra_Rail
prefix: RL
doc_base_type: IN
segments:
  Rail track:  { code: TRK,  bom_category: TRK, role: TRACK }
  Unknown:     { code: MISC, bom_category: MS,  role: MISC }

disciplines:
  TRK:  [IfcTrackElement, IfcRail]  # Sleepers + rails
  GEO:  [IfcCourse]                  # Ballast beds
  ARC:  [IfcBuildingElementProxy]    # Reference markers
```

**BOM structure:** Single track segment with 72 elements: 66 sleepers + 4 rails
(CLUSTER verb, 70 instances), 2 ballast beds (SNAP). Sleepers are on a diagonal
alignment — not a 2D grid — so VerbDetector assigns CLUSTER (exact per-instance
offsets), not TILE (which requires a rectangular grid). 606mm NN spacing is uniform.

### §0.4 Schema Extensions (from parallel session)

| Migration | What | Impact on Designer |
|-----------|------|-------------------|
| `V010_sustainability_columns.sql` | carbon_kg_per_unit, recyclability, lifespan on M_Product | Future: 6D carbon reports for infra |
| `V011_facility_type.sql` | `facility_type` column on AD_Val_Rule, backfilled from provenance | Future: can replace provenance-based loadRules with direct column filter |
| `V012_report_config.sql` | AD_Report_Config table | Future: report engine for infra compliance |

**V011 note:** Our current `loadInfraRules()` uses `WHERE provenance = ?`. Once V011 is
applied, we could migrate to `WHERE facility_type = ?` for cleaner queries. No urgency —
provenance discriminator works and V011 backfills from it.

### §0.5 Pipeline Enhancements

- `run_RosettaStones.sh`: Fidelity ORDER BY extended from 3 to 9 columns
  (pos + max + dims). Fixes tie-breaking for infra elements with identical positions.
- `ReportDAO.java`: Interface for 4D schedule, 5D cost, 6D carbon, 7D assets, KPI.
  Reads from split DBs (BOM + component_library + validation + work_output).

---

## §1 Terrain Layer (Foundation for All Infrastructure)

### §1.1 What Exists (IfcOpenShell Federation Addon)

The `pdf_terrain/` module at `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`
has a working pipeline:

```
Survey PDF/PNG → Google Vision OCR → JSON elevation points
                                   → Affine calibration
                                   → Blender point cloud (orange spheres at Z elevation)
                                   → Reference image plane at Z=40.0 (white area)
                                   → Export: IFC (IfcGeographicElement) + DXF
```

Key classes:
- `BIM_OT_pdf_terrain_generate` — extracts elevations, creates 3D point cloud
- `PDFTerrainProperties` — pdf_path, point_count, mesh_generated
- Pixel-to-world: `x = px * scale`, `y = (h - py) * scale`, `z = elevation_m`

The `blosm/` module converts OpenStreetMap data:
- `terrain → ('IfcGeographicElement', 'TERRAIN', 'TERRAIN')`
- `water → ('IfcGeographicElement', 'USERDEFINED', 'WATER')`
- Mesh triangulation via `bmesh.ops.triangulate()`
- Federation DB schema: `elements_meta`, `element_transforms`, R-tree spatial index

### §1.2 Terrain in the Designer — What We Need

**Principle:** Terrain is a read-only context layer. The Designer does not edit terrain —
it places infrastructure ON terrain. The terrain provides Z values for placement.

| Feature | Source | Java DAO |
|---------|--------|----------|
| Load terrain mesh | Federation DB (`base_geometries`) | `TerrainDAO.loadMesh()` |
| Query Z at (X,Y) | R-tree + barycentric interpolation on TIN | `TerrainDAO.getElevation(x, y)` |
| Display terrain | BBox wireframe (existing viz pipeline) | N/A (Blender-side) |
| Terrain bounds | `element_transforms` AABB | `TerrainDAO.getBounds()` |

### §1.3 TerrainDAO Spec

```java
public class TerrainDAO {
    /** Load terrain TIN mesh from federation GI database. */
    record TerrainMesh(double[] vertices, int[] faces) {}
    TerrainMesh loadMesh(Connection giConn, String elementId);

    /** Query ground elevation at a point via barycentric interpolation. */
    double getElevation(double xMm, double yMm);

    /** Get terrain AABB for viewport bounds. */
    record TerrainBounds(double minX, double minY, double minZ,
                         double maxX, double maxY, double maxZ) {}
    TerrainBounds getBounds();
}
```

**Contract:** Python writes terrain to federation DB → Java reads via TerrainDAO.
Same pattern as `CORE_SRS.md §3.3` (IfcOpenShell writes, Java reads).

### §1.4 Witnesses

| Witness | What it proves |
|---------|---------------|
| W-TERRAIN-LOAD-1 | TerrainDAO loads mesh from sample federation DB |
| W-TERRAIN-ELEV-1 | getElevation(x,y) returns interpolated Z within 0.1m of known point |
| W-TERRAIN-BOUNDS-1 | getBounds() matches R-tree AABB |

---

## §2 Infrastructure Element Types

### §2.1 IFC4X3 → BOM Category Mapping

Updated from Rosetta Stone YAMLs and `InfrastructureAnalysis.md` §2.3:

| IFC4X3 Entity | Discipline | bom_category | bomType | Detected Verb | Rosetta Stone |
|---------------|-----------|-------------|---------|---------------|---------------|
| IfcBridgePart | STR | BR | SEGMENT | — | BR (spatial structure) |
| IfcFooting | STR | BR | LEAF | CLUSTER (4+) | BR (pier footings) |
| IfcColumn | STR | BR | LEAF | CLUSTER (4+) | BR (pier columns) |
| IfcMember | STR | BR | LEAF | CLUSTER (8) | BR (superstructure members) |
| IfcEarthworksFill | GEO | CW/TRK | LEAF | CLUSTER (4) / SNAP | RD, BR |
| IfcRoadPart | — | CW/PKG | SEGMENT | — | RD (spatial structure) |
| IfcCourse | PAV/GEO | CW/TRK | LEAF | SNAP | RD (2 per segment — below MIN_GROUP) |
| IfcSurfaceFeature | MARK | CW | LEAF | CLUSTER (20) | RD (line markings) |
| IfcRailwayPart | — | TRK | SEGMENT | — | RL (spatial structure) |
| IfcTrackElement | TRK | TRK | LEAF | CLUSTER (66) | RL (sleepers — diagonal, not grid) |
| IfcRail | TRK | TRK | LEAF | CLUSTER (4) | RL (4 rails) |

**Verb notes:** TILE requires a rectangular 2D grid (≥2 unique X AND Y positions).
Sleepers are on a diagonal alignment, so VerbDetector assigns CLUSTER (lossless
per-instance offsets). Road courses have only 2 elements per segment per product,
below MIN_GROUP=4, so they fall through to SNAP. Future larger models with more
elements per segment may trigger TILE/ROUTE detection.

### §2.2 New bomTypes for snap() Dispatch — DONE

`snap()` now processes `ROOM`, `SEGMENT`, and `LEAF` bomTypes (implemented S37).

### §2.3 extractActual() — Infra Parameter Mapping — DONE

Implemented in S37. Current mapping:

```java
private Double extractActual(PlacementRequest req, String paramName) {
    return switch (paramName) {
        // Building (existing)
        case "min_area_m2"   -> req.areaSqM();
        case "min_dim_mm"    -> req.minDimMm();
        case "min_height_mm", "height_mm" -> req.heightMm();
        case "min_width_mm", "width_mm" -> req.widthMm();
        // Infrastructure dimension checks
        case "depth_mm"      -> req.depthMm();
        case "thickness_mm"  -> req.heightMm();
        case "avg_width_mm"  -> req.widthMm();
        case "avg_depth_mm"  -> req.depthMm();
        case "avg_height_mm" -> req.heightMm();
        case "total_depth_mm" -> req.depthMm();
        default -> null;
    };
}
```

**Note:** Infra rules currently use DIMENSION rule_type with extracted reference values
(e.g. pier width_mm=3499). These act as "element must be at least this size" checks.
Future: category-keyed rules with explicit min_ thresholds once Rosetta Stones
establish the BOM category vocabulary.

### §2.4 Witnesses — DONE

| Witness | Status | What it proves |
|---------|--------|---------------|
| W-INFRA-SNAP-1 | PASS | Bridge pier 1000mm → BLOCK (width < 3499mm reference) |
| W-INFRA-SNAP-2 | PASS | Road course 10mm → BLOCK (thickness < 40mm reference) |
| W-INFRA-SNAP-3 | PASS | Rail element 500mm → BLOCK (width < reference) |
| W-INFRA-SNAP-4 | PASS | Building 3100x3100 bedroom → PASS unchanged |

---

## §3 Infrastructure Layout Generator

### §3.1 Alignment-Based Layout (vs Room Grid)

Buildings use a grid layout (X/Y rooms on storeys at Z offsets).
Infrastructure uses **alignment-based** layout:

| Concept | Building | Infrastructure |
|---------|----------|---------------|
| Primary axis | X/Y grid | Alignment centreline (polyline/curve) |
| Cross section | Room width × depth | Carriageway width, span depth |
| Vertical | Storey Z=0 per floor | Terrain Z + super-elevation |
| Repetition | Floor duplication | Segment repetition along alignment |
| YAML key | `storeys:` | `segments:` (alias for `storeys:`) |

### §3.2 Road Layer Stacking (from Rosetta Stone)

Road pavement is a **MAKE path** (like Assembly Builder) — layers stack vertically:

```
Z ↑
  │  Surface (40mm)   — IfcCourse, discipline PAV
  │  Binder (80mm)    — IfcCourse, discipline PAV
  │  Base (120mm)     — IfcCourse, discipline PAV
  │  Subgrade (250mm) — IfcEarthworksFill, discipline GEO
  └──────────────────────────────────────────── terrain Z
```

Total depth: 490mm. Each carriageway segment has 2 instances of each layer (left/right).
This is analogous to wall assembly layers in the Assembly Builder — same UValueCalculator
pattern could compute thermal properties for road pavements.

### §3.3 Rail Repetition Pattern (from Rosetta Stone)

Rail track uses **CLUSTER verb** — 66 sleepers at 606mm uniform spacing (diagonal alignment):

```
Station →
  ├─┤ ├─┤ ├─┤ ├─┤ ├─┤ ...  (66 sleepers, IfcTrackElement — CLUSTER)
  ══════════════════════════  (4 rails, IfcRail — CLUSTER)
  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓  (2 ballast beds, IfcCourse — SNAP, <MIN_GROUP)
```

Gauge: ~1500mm. Sleeper dims: 1517 × 2377 × 150mm (from extraction).
CLUSTER stores exact per-instance offsets (lossless). 75 BOM lines → 5 (93% compression).
TILE would require a rectangular grid; these sleepers follow a diagonal alignment.

### §3.4 Alignment Model

```java
record Alignment(
    String id,
    List<AlignmentPoint> points,  // centreline polyline
    double startStation,          // chainage start (m)
    double endStation             // chainage end (m)
) {}

record AlignmentPoint(
    double station,   // chainage (m)
    double x, double y, double z  // world coordinates (mm)
) {}
```

**Z values come from terrain.** The alignment's Z at each station = terrain elevation
at that (X,Y) + design vertical offset (super-elevation, grade).

### §3.5 Segment Placement Along Alignment

```
SPAN bridge_deck FROM station:0 TO station:30000 WIDTH 12000 DEPTH 2400
  → Places a SEGMENT bbox along alignment, Z from terrain + bearing height

COURSE road_surface FROM station:0 TO station:500000 WIDTH 7300 THICKNESS 50
  → Places LEAF bboxes as material layers, Z stacked on terrain
  → 4 layers per carriageway segment (subgrade→base→binder→surface)

TILE sleepers FROM station:0 TO station:1000000 SPACING 606 WIDTH 2600
  → Places LEAF bboxes at 606mm intervals along rail alignment
  → 66 instances per track segment
```

### §3.6 Witnesses

| Witness | What it proves |
|---------|---------------|
| W-ALIGN-1 | AlignmentPoint Z matches TerrainDAO.getElevation() |
| W-ALIGN-2 | SPAN verb places bbox along alignment between stations |
| W-ALIGN-3 | COURSE verb stacks 4 layers with correct Z offsets (490mm total) |
| W-ALIGN-4 | TILE verb places 66 sleepers at 606mm spacing along alignment |

---

## §4 Component Library — Infrastructure Products (DONE)

### §4.1 Current State: 33 Infra Products in Library

The ExtractionPopulator registered all infra products into `component_library.db`
during the IFCtoBOM pipeline run. **Not a blocker — already done.**

33 pure infra products across 3 facility types:

| Facility | Products | Examples |
|----------|---------|---------|
| Infra_Bridge | 17 | foundation (road/rail), pierstem, arch segment, girder, deck, spandrel wall, filler, name sign, approach slab, railing |
| Infra_Road | 12 | asphalt surface/binder course, base course, subgrade (×4 segments + parking variants), line marking |
| Infra_Rail | 4 | sleeper wood, rail, ballastbed, geo-reference |

### §4.2 Key Product Dimensions (from extraction, metres)

| Product | W | D | H | Source |
|---------|---|---|---|--------|
| road - asphalt surface course | 19.1 | 13.1 | 0.04 | Infra_Road |
| road - asphalt binder course | 19.1 | 13.1 | 0.08 | Infra_Road |
| road - base course | 19.1 | 13.1 | 0.12 | Infra_Road |
| road - subgrade | 19.1 | 13.1 | 0.25 | Infra_Road |
| road - line marking | 1.8 | 1.1 | 0.01 | Infra_Road |
| sleeper wood | 1.5 | 2.4 | 0.15 | Infra_Rail |
| rail | 17.4 | 10.1 | 0.23 | Infra_Rail |
| ballastbed | 19.1 | 13.0 | 0.25 | Infra_Rail |
| foundation - bridge road | 4.3 | 5.4 | 0.7 | Infra_Bridge |
| foundation - bridge rail | 6.2 | 7.3 | 1.0 | Infra_Bridge |

### §4.3 Designer Browse: Already Generic

`browseItems()` queries M_Product with SQL LIKE and category filter.
Infra products will appear when filtered by `building_type = 'Infra_Road'` etc.
No code changes needed — the query is already generic.

---

## §5 Federation Terrain → Designer Pipeline

### §5.1 End-to-End User Journey

```
1. User imports survey PDF via Federation addon (Blender)
   → pdf_terrain extracts elevation points
   → Exports terrain.ifc + terrain data to federation DB

2. User opens Designer, selects FacilityType = ROAD (doc_base_type: IN)
   → Designer loads road validation rules (10 rules)
   → TerrainDAO loads terrain mesh from federation DB
   → Viewport shows terrain wireframe as context layer

3. User defines alignment (polyline over terrain)
   → Each alignment point gets Z from TerrainDAO.getElevation()
   → Alignment shown as 3D curve in viewport

4. User places infrastructure segments along alignment
   → 4 carriageway segments auto-generated from Rosetta Stone pattern
   → Each segment gets 4 pavement layers (MAKE path, Assembly Builder pattern)
   → snap() validates against road rules

5. For rail: TILE verb places 66 sleepers at 606mm spacing
   → Browse component library: TRK category
   → Validator checks dimension rules (sleeper width, depth, height)

6. User snaps + validates
   → snap(bboxes, "", 100, "ROAD") loads road rules
   → Each course/marking checked against infra thresholds
   → BLOCK/PASS verdicts displayed per element

7. ReportDAO generates 5D cost breakdown
   → Reads V010 sustainability columns for carbon (6D)
   → AD_Report_Config (V012) controls report template selection
```

### §5.2 UX Differences from Building Mode

| Aspect | Building Mode | Infrastructure Mode |
|--------|-------------|-------------------|
| Primary layout | Room grid (X,Y on floor) | Alignment (station, offset) |
| Vertical reference | Storey Z=0 per floor | Terrain Z(x,y) per point |
| Repetition | Clone storey | Segment repetition along alignment |
| Validation scope | Jurisdiction (MY/US/UK) | Provenance (Infra_Bridge/Road/Rail) |
| Component library | Rooms, furniture, MEP | Courses, sleepers, piers |
| Context layer | None | Terrain mesh (read-only) |
| Coordinate scale | Metres (5-50m buildings) | Kilometres (100m-10km corridors) |
| YAML hierarchy | `storeys:` | `segments:` (alias) |
| doc_base_type | (various) | `IN` |
| Layer stacking | Assembly Builder (wall) | Same pattern (pavement layers) |

---

## §6 Implementation Phases

### Phase I-1: Infra Snap Wiring — DONE (S37)

FacilityType enum, dual-mode loadRules, extractActual infra params,
snap SEGMENT/LEAF, 15 witnesses. 181/181 GREEN.

### Phase I-2: TerrainDAO (terrain foundation)

**Scope:** Java reads terrain from federation DB.

| Task | File | Effort |
|------|------|--------|
| `TerrainDAO` with loadMesh, getElevation, getBounds | `dao/TerrainDAO.java` (NEW) | Medium |
| Sample federation DB test fixture | `src/test/resources/terrain_sample.db` | Medium |
| 3 witnesses (W-TERRAIN-*) | `TerrainDAOTest.java` (NEW) | Small |

**Gate:** `getElevation(x, y)` returns interpolated Z matching known survey points.

### Phase I-3: Infra Rosetta Stones — DONE (S37b)

**Scope:** Full pipeline pass for Road + Rail Rosetta Stones.

| Task | File | Status |
|------|------|--------|
| `classify_rd.yaml` + `dsl_rd.bim` | `IFCtoBOM/src/main/resources/` | DONE |
| `classify_rl.yaml` + `dsl_rl.bim` | `IFCtoBOM/src/main/resources/` | DONE |
| Component library (33 infra products) | `component_library.db` | DONE (ExtractionPopulator) |
| IFCtoBOMPipeline: route `IN` to DisciplineBomBuilder | `IFCtoBOMPipeline.java:192` | DONE |
| Road BOM compilation | RD_BOM.db: 34 lines, 20 CLUSTER | **RD 4/4 PASS** |
| Rail BOM compilation | RL_BOM.db: 5 lines, 70 CLUSTER (93% compression) | **RL 4/4 PASS** |
| Bridge recompiled with verb detection | BR_BOM.db: 26 lines, 28 CLUSTER | **BR 10/10 PASS** |

**Key fix:** `doc_base_type: IN` was falling into RE path (StructuralBomBuilder, no verb
detection). One-line fix routes IN to DisciplineBomBuilder (same as CO), enabling
VerbDetector cascade for all infrastructure.

### Phase I-4: Alignment Model + Infra Layout Verbs

**Scope:** Station-based placement along polyline.

| Task | File | Effort |
|------|------|--------|
| `Alignment` + `AlignmentPoint` records | `model/Alignment.java` (NEW) | Small |
| Alignment Z from TerrainDAO | Integration | Small |
| SPAN/COURSE/TILE verb extensions for infra | `VerbRegistry` | Medium |
| 4 witnesses (W-ALIGN-*) | `AlignmentTest.java` (NEW) | Medium |

**Depends:** Phase I-2 (TerrainDAO) + Phase I-3 (Rosetta Stones prove verb patterns).

### Phase I-5: Federation Terrain Integration

**Scope:** Wire Blender terrain → Designer viewport.

| Task | File | Effort |
|------|------|--------|
| BlenderBridge terrain context packet | `BlenderBridge.java` | Medium |
| Viewport terrain wireframe render | Python-side (Federation addon) | Medium |
| End-to-end journey test | Integration test | Large |

---

## §7 Witnesses Summary

| Phase | Witnesses | Count | Status |
|-------|----------|-------|--------|
| I-1 | W-INFRA-FILTER-1..4, W-INFRA-SNAP-1..4, InfraUIFilter×7 | 15 | **DONE** |
| I-2 | W-TERRAIN-LOAD-1, W-TERRAIN-ELEV-1, W-TERRAIN-BOUNDS-1 | 3 | planned |
| I-3 | BR 10/10, RD 4/4, RL 4/4 Rosetta Stone gates | 18 | **DONE** |
| I-4 | W-ALIGN-1..4 | 4 | planned |
| I-5 | W-TERRAIN-E2E-1 | 1 | planned |
| **Total** | | **~41** | |

---

## §8 References

| Document | Covers |
|----------|--------|
| `InfrastructureAnalysis.md` | IFC4X3 file inventory, entity census, verb mapping |
| `CORE_SRS.md §3.1` | Gap matrix, Moat 5 (Infrastructure First-Mover) |
| `DISC_VALIDATION_DB_SRS.md` | validation.db schema, 30 infra rules |
| `BIM_Designer.md §17` | snap(), jurisdiction, Design Mode |
| `BlenderBridge.md` | Java-smart/Python-dumb pipe protocol |
| `classify_rd.yaml` | Road Rosetta Stone: 4 carriageways, PAV/MARK/GEO disciplines |
| `classify_rl.yaml` | Rail Rosetta Stone: 66 sleepers @ 606mm, TRK/GEO disciplines |
| `dsl_rd.bim` / `dsl_rl.bim` | Infrastructure DSL scripts (EN-BLOC) |
| `V011_facility_type.sql` | facility_type column on AD_Val_Rule |
| `ReportDAO.java` | 4D-7D report engine interface |
| Federation `pdf_terrain/operator.py` | Survey PDF → elevation points → IFC |
| Federation `blosm/blosm_to_gi_complete.py` | OSM → IfcGeographicElement terrain mesh |

---

*INFRA_DESIGNER_SRS.md v1.2 — Corrected with actual Rosetta Stone results (S37b).*
*Phase I-1 DONE. Phase I-3 DONE (BR 10/10, RD 4/4, RL 4/4). Verb = CLUSTER (not TILE).*
*33 infra products in component_library.db. IFCtoBOMPipeline IN→DisciplineBomBuilder fix.*
