# Infrastructure Designer SRS
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>Bridge, road, rail, tunnel — same compiler, terrain-following placement.</b> Designer UX for infrastructure with IFC4X3 vocabulary and alignment context.
</div>

**Version:** 2.0 | **Date:** 2026-03-20
**Scope:** Designer UX for infrastructure (bridge/road/rail/tunnel) + terrain-following placement
**Companion:** `StrategicIndustryPositioning.md` (gap matrix), `InfrastructureAnalysis.md` §8 (terrain model)
**Session:** S37 — terrain-following placement PoC proven on real 689-point survey data

---

## Current State (after S37)

| Layer | Status | What works |
|-------|--------|------------|
| Rule loading | **DONE** | FacilityType.BRIDGE loads 13 rules, ROAD 10, RAIL 7 |
| Rule isolation | **DONE** | Building mode excludes Infra_*, infra excludes building |
| API surface | **DONE** | snap/setJurisdiction with facilityType, listSegments, deriveFacilityType |
| Infra BOM vocabulary | **DONE** | Bridge: ABT/PIR/DCK/SUP/APR. Road: CW/PKG. Rail: TRK + disciplines |
| Rosetta Stones | **DONE** | BR 10/10, RD 4/4, RL 4/4 — 33 infra products in library |
| PlacementContext | **DONE** | Abstract interface: RoomContext (building) + AlignmentContext (infra) |
| Terrain snap | **DONE** | TerrainSnap: ON_SURFACE / ABOVE / BELOW / PIER modes |
| Contour following | **DONE** | `fromContour()` builds winding path along elevation band |
| Curvature + heading | **DONE** | `curvatureAtStation()`, `headingAtStation()`, `positionAtStation()` |
| Real terrain proof | **DONE** | 689-point survey, 294×229m, 20m range — drag trail proven |
| Cut-and-fill volumes | **DONE** | CutFillCalculator: flat + alignment, proven on 689-pt terrain |
| Terrain-aware snap() | **DONE** | SnapOptions +terrainContext, bbox Z adjusted per element |
| Wireframe → LOD save | WIRING | On CO save: bbox → MeshBinder → full geometry from library |
| Blender viewport | GAP | BlenderBridge terrain render, interactive drag |

---

## §0 Rosetta Stone BOM Model (from parallel session S37b)

### §0.1 Infrastructure YAML Convention

The parallel session established the infra Rosetta Stone pattern. Key decisions:

**`doc_base_type: IN`** is the infrastructure discriminator in YAML (deprecated — will migrate
to M_Product_Category=IN, see DATA_MODEL.md §7). All infra YAMLs use this.
The Designer API checks the top-level M_Product_Category to select infra mode.

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

**DSL pattern (`dsl_rd.bim`):** Extracted (singularity), not generative.
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
  Reads from split DBs (BOM + component_library + validation + output).

---

## §1 Terrain as Placement Context (PROVEN)

### §1.1 The Insight: Terrain = Container

Buildings place elements inside rooms. Infrastructure places elements ON/ABOVE/BELOW
terrain. Both are **placement contexts** — same abstract interface:

```java
public interface PlacementContext {
    boolean fits(double w, double d, double h, double x, double y);
    double elevationAt(double x, double y);
    Bounds bounds();
    String contextType();  // "ROOM" or "ALIGNMENT"
}
```

| | RoomContext (Building) | AlignmentContext (Infra) |
|---|---|---|
| **fits()** | AABB containment in room | Width ≤ corridor along centreline |
| **elevationAt()** | Constant (storey Z) | Varies — terrain Z at (X,Y) |
| **Z during drag** | Fixed | Flows along terrain surface |
| **On CO save** | Wireframe → LOD geometry | Same — bbox → MeshBinder → library LOD |

### §1.2 Terrain Data Source

Federation `pdf_terrain` addon extracts survey elevations:

```
Survey PDF → Google Vision OCR → survey_highres_extracted.json
  ground_elevations[689]: { x: pixel, y: pixel, z: elevation_m }
  scale: 0.0423 m/pixel, image: 9934 × 7017 px
  World: x = px × scale, y = (img_h - py) × scale, z = elevation_m
```

**Proven sample:** 689 points, 294m × 229m area, Z: 28.1–48.1m (river valley slope).
Source: `pdf_terrain/samples/survey_highres_extracted.json`.

### §1.3 TerrainSnap — How Elements Relate to Terrain

Each infrastructure type has a specific Z relationship to the terrain surface.
`TerrainSnap` computes the element's base Z during interactive drag:

| Mode | Z Formula | Use Case | User Adjusts |
|------|-----------|----------|-------------|
| `ON_SURFACE` | terrain + offset | Road layers, sleepers, ballast | Layer stack offset |
| `ABOVE` | terrain + clearance | Bridge deck, overhead lines | Min clearance (flood level) |
| `BELOW` | terrain - cover - height | Tunnel, pipeline, foundation | Min cover depth |
| `PIER` | terrain (base), extends up | Bridge piers, abutments, retaining walls | Height to deck |

**Road layer stacking** (ON_SURFACE, cumulative offset):
```
Z ↑ (mm)                                   offset
43900 ─── surface course (40mm)  ──────── +450
43860 ─── binder course (80mm)   ──────── +370
43780 ─── base course (120mm)    ──────── +250
43660 ─── subgrade (250mm)       ──────── +0
43410 ═══ terrain surface ════════════════ elevationAt(x,y)
```

**Bridge cross-section** (ABOVE + PIER):
```
50810 ─── deck top (2400mm slab) ──────── ABOVE: terrain + 5000mm clearance
48410 ─── deck base
      │  pier  │  pier  │               PIER: terrain → deck
43410 ═══ terrain surface ════════════════
```

**Tunnel** (BELOW):
```
43410 ═══ terrain surface ════════════════
40410 ─── tunnel top                      BELOW: terrain - 3000mm cover
34410 ─── tunnel base (6000mm diameter)
```

### §1.4 Engineering Controls

| Concern | TerrainSnap Mode | Offset Meaning | Validator Rule |
|---------|-----------------|----------------|---------------|
| Road design level | ON_SURFACE | Fill height above terrain | max_fill_height |
| Cut depth | BELOW | Excavation depth | max_cut_depth |
| Flood clearance | ABOVE | Distance above flood level | min_clearance_mm |
| Tunnel cover | BELOW | Soil cover above tunnel | min_cover_mm |
| Max gradient | — | Compare Z at consecutive stations | max_gradient_pct |
| Super-elevation | ON_SURFACE | Per-lane cross-slope | max_crossfall_pct |
| Contour tolerance | fromContour() | ± band around target Z | cut/fill volume |

User can also set a **straight design level** (fixed Z override) for traditional
cut-and-fill — the difference between design Z and terrain Z at each station gives
earthworks volume.

### §1.5 Witnesses (PROVEN on real terrain)

| Witness | Status | What it proves |
|---------|--------|---------------|
| W-CONTEXT-ROOM-1 | **PASS** | Room fits furniture + constant Z |
| W-CONTEXT-ROOM-2 | **PASS** | Element too wide → rejected |
| W-CONTEXT-ALIGN-1 | **PASS** | Alignment Z interpolation along centreline |
| W-CONTEXT-ALIGN-2 | **PASS** | Element wider than corridor → rejected |
| W-CONTEXT-ALIGN-3 | **PASS** | Terrain elevation varies by position |
| W-CONTEXT-POLY | **PASS** | Room + Alignment both satisfy PlacementContext |
| W-CONTEXT-TERRAIN-1 | **PASS** | 689 real survey points loaded, 294×229m, 20m range |
| W-CONTEXT-TERRAIN-2 | **PASS** | Elevation query returns valley slope gradient |
| W-CONTEXT-TERRAIN-3 | **PASS** | Road course fits in corridor, gets real Z |
| W-TERRAIN-SNAP-1 | **PASS** | Road 4-layer stack: terrain→+250→+370→+450→+490mm |
| W-TERRAIN-SNAP-2 | **PASS** | Bridge deck at terrain+5m, pier base at terrain |
| W-TERRAIN-SNAP-3 | **PASS** | Tunnel at terrain−3m cover, top below surface |
| W-TERRAIN-SNAP-4 | **PASS** | Drag trail: Z follows terrain (43.8→43.2→42.8→43.4m) |

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

## §5 Interactive UX: Click → Drag → Save

### §5.1 The Three Phases

Infrastructure design in the Designer follows the same wireframe-first pattern
as building design, but terrain-aware:

**Phase A — Click to Place (wireframe bbox)**
```
1. User selects facility type: ROAD / BRIDGE / RAILWAY / TUNNEL
   → listFacilityTypes() populates dropdown
   → deriveFacilityType(IN, RD) → ROAD → loads 10 road rules
   → Terrain loaded from Federation survey JSON into AlignmentContext

2. User clicks on terrain → bbox appears at terrain Z
   → elevationAt(clickX, clickY) gives base Z
   → TerrainSnap.onSurface(0) for road, .above(5000) for bridge, .below(3000) for tunnel
   → Wireframe bbox rendered at correct terrain-following Z

3. For alignment mode: user draws polyline over terrain
   → Each vertex snaps to terrain Z
   → Or: fromContour(points, 43000, ±1000, 7300) auto-generates curved path
```

**Phase B — Drag to Adjust (wireframe flows along terrain)**
```
4. User drags element across terrain
   → At each mouse position: computeZ(terrain, x, y, elementH) updates bbox Z
   → Wireframe bbox "snuggles" onto terrain surface
   → Heading rotates to follow alignment tangent (headingAtStation)
   → Curvature shown for tight bends (curvatureAtStation → radius)

5. User adjusts offset
   → Slider: fill height / cut depth / clearance / cover
   → Re-snap: bbox Z updates = terrain ± offset
   → Layer stack: subgrade→base→binder→surface auto-stacks

6. Validate continuously
   → snap(bboxes, "", gridMm, "ROAD") checks against infra rules
   → BLOCK/PASS per element in real-time
   → Gradient check: compare Z at consecutive stations
```

**Phase C — CO Save (wireframe → full LOD geometry)**
```
7. User saves (CO — Complete Order)
   → Each wireframe bbox resolves to M_Product in component_library.db
   → MeshBinder.bind() loads real geometry from component_geometries
   → Full LOD material applied from extraction (33 infra products)
   → Shape updates incrementally in output DB
   → output.db (compile DB) stores design state for variant recall

8. Incremental update
   → User modifies one element → only that bbox re-resolves
   → Rest of design untouched (same as building incremental compile)
```

### §5.2 Contour-Following Design (PROVEN)

The killer feature: alignment follows terrain contours instead of cutting straight
through. `AlignmentContext.fromContour()` selects terrain points within a ±tolerance
band and orders them into a smooth path using nearest-neighbour greedy walk.

**Proven on real terrain (689 survey points, river valley):**

| Contour | Points | Path Length | Heading Range | Use |
|---------|--------|------------|---------------|-----|
| 43m | 365 | 2.9 km | 63° → -158° → -23° | Valley road |
| 45m | 291 | 2.0 km | (different curve) | Ridge bridge |

The 43m road curves 221° through the valley. The 45m bridge follows a different
line 2m higher. Curvature varies from R=17m (tight bend) to R=1037m (near-straight).

**User controls:**
- **Target elevation** — which contour to follow (slider)
- **Tolerance band** — ±Xm around target (wider = smoother, more cut/fill)
- **Grading slider** — blend 0→100% between contour and straight (see §5.6)
- **Offset** — constant fill/cut above/below contour line

### §5.5 Cut-and-Fill Volume Computation (DONE — S38b)

`CutFillCalculator` computes earthworks volumes from terrain vs design Z:

| Method | Input | What it computes |
|--------|-------|-----------------|
| `flatLevel()` | terrain points + constant Z | Cut/fill per Voronoi influence cell |
| `alongAlignment()` | terrain context + design profile | Trapezoidal cross-sections per station pair |

**Proven on real 689-point terrain (294×229m, Z: 28–48m):**

| Design Level | Cut (m³) | Fill (m³) | Net | Ratio |
|-------------|---------|---------|-----|-------|
| 40m (below valley) | 254,566 | 1,166 | +253K | 218:1 cut |
| 43.8m (mean) | 31,512 | 31,504 | +8 | 1.00 balanced |
| 20m (below all) | 1,603,575 | 0 | all cut | ∞ |

Result record: `CutFillResult(cutVolumeM3, fillVolumeM3, netVolumeM3, cutPointCount, fillPointCount, onGradeCount)`.

### §5.6 Grading Strategy — Contour / Straight / Blend (DONE — S38b)

`GradingStrategy` controls how the design Z relates to the natural terrain Z.
The **default is CONTOUR** — the road bends to follow the terrain, minimising
earthworks. The user can drag a slider to straighten the alignment, accepting
more cut-and-fill in exchange for a shorter, more direct route.

```
                    ← CONTOUR (0%)                    STRAIGHT (100%) →
                    follow terrain                     fixed design level
                    zero earthworks                    maximum earthworks
                    longest route                      shortest route

  Slider:  ├──────────────┼──────────────┤
           0.0           0.5            1.0
```

**Design Z formula:**
```
designZ = terrainZ × (1 − blend) + designLevelMm × blend
```

| Mode | blend | designZ | Earthworks | Route |
|------|-------|---------|-----------|-------|
| CONTOUR (default) | 0.0 | = terrainZ | Zero | Follows contours, curves |
| BLEND 50% | 0.5 | midpoint | ~half | Partially straightened |
| STRAIGHT | 1.0 | = designLevel | Maximum | Straight cut-and-fill |

**Proven on real terrain:**

| Grading | Total Movement (m³) | Note |
|---------|-------------------|------|
| Contour (0%) | 0 | Road follows terrain perfectly |
| Blend (50%) | 396 | Half contour, half straight |
| Straight (100%) | 793 | Fixed 43.8m design level |

Earthworks increase **monotonically** with blend — mathematically guaranteed
by the linear interpolation formula.

**Key classes:**
- `GradingStrategy.java` — CONTOUR / STRAIGHT / BLEND modes, `designZAt()`, `computeDesignProfile()`
- `SnapOptions.gradingStrategy` — passed into snap(), null = CONTOUR default
- `DesignerAPIImpl.snap()` — applies `grading.designZAt(terrainZ)` before TerrainSnap

**UI integration:** Blender N-panel slider. On change → recompute snap() with new
grading → cut/fill volumes update live → user sees earthworks cost of straightening.

### §5.3 Drag Trail (measured on real terrain)

```
Drag across 294m at Y=midpoint, 10 steps:
Position:  30m   60m   90m   120m  150m  180m  210m  240m  270m  300m
Elev (m): 43.8  43.8  43.6  43.6  43.2  43.4  42.8  43.4  43.8  43.0
```

The element's Z flows along the terrain. In the viewport, the wireframe bbox
rises and falls with the valley — the user sees the infrastructure hugging
the natural ground.

### §5.4 UX Comparison: Building vs Infrastructure

| Aspect | Building Mode | Infrastructure Mode |
|--------|-------------|-------------------|
| Placement context | RoomContext (constant Z) | AlignmentContext (variable Z) |
| Click to place | Bbox at storey level | Bbox at terrain Z |
| Drag | Slides on floor plane | Flows along terrain surface |
| Layout | Room grid (X,Y on floor) | Alignment (station + contour) |
| Repetition | Clone storey | Segment along alignment |
| Validation | Jurisdiction (MY/US) | Provenance (Infra_Bridge/Road/Rail) |
| Components | Rooms, furniture, MEP | Courses, sleepers, piers |
| Vertical ref | Storey Z=0 | Terrain Z(x,y) per point |
| Curve | N/A (rectangular rooms) | headingAtStation, curvatureAtStation |
| CO save | Wireframe → LOD | Same — MeshBinder → library geometry |
| YAML key | `storeys:` | `segments:` (alias) |

---

## §6 Implementation Phases

### Phase I-1: Infra Snap Wiring — DONE (S37)

FacilityType enum, dual-mode loadRules, extractActual infra params,
snap SEGMENT/LEAF, 15 witnesses. 181/181 GREEN.

### Phase I-2: Terrain Placement Model — DONE (S37)

PlacementContext interface, RoomContext, AlignmentContext, TerrainSnap,
contour-following (`fromContour`), curvature/heading/position queries.
Proven on 689-point real survey terrain. 16 PlacementContext witnesses GREEN.

**Key classes:**
- `PlacementContext.java` — abstract container interface
- `RoomContext.java` — building rooms (constant Z)
- `AlignmentContext.java` — terrain corridors (variable Z, curves, contour-follow)
- `TerrainSnap.java` — ON_SURFACE / ABOVE / BELOW / PIER snap modes

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

**Key fix:** M_Product_Category=IN was falling into the residential path (StructuralBomBuilder,
no verb detection). One-line fix routes IN to DisciplineBomBuilder (same as CO), enabling
VerbDetector cascade for all infrastructure.

### Phase I-4: Wire TerrainSnap into Designer snap() Loop — DONE (S38b)

**Scope:** During snap(), compute Z per bbox from terrain context.

| Task | File | Status |
|------|------|--------|
| SnapOptions +terrainContext/terrainSnap | `DesignerAPI.java` | DONE |
| snap() terrain Z adjustment loop | `DesignerAPIImpl.java` | DONE |
| CutFillCalculator (flat + alignment) | `CutFillCalculator.java` | DONE |
| 8 witnesses: cut-fill + terrain snap | `CutFillTerrainSnapTest.java` | DONE |

**Key classes:**
- `CutFillCalculator.java` — flat design level + alignment profile cut/fill volumes
- `SnapOptions` — extended with `terrainContext` + `terrainSnap` fields
- `DesignerAPIImpl.snap()` — terrain Z adjustment before validation

**Gate:** snap(road_bboxes, ROAD, terrain) → each bbox Z follows terrain. **PASS.**

### Phase I-5: BlenderBridge Terrain Viewport

**Scope:** Wire terrain context to Blender viewport for interactive drag.

| Task | File | Effort |
|------|------|--------|
| BlenderBridge terrain context packet | `BlenderBridge.java` | Medium |
| Viewport terrain wireframe render | Python-side (Federation addon) | Medium |
| Mouse drag → computeZ() → bbox update | Python drag handler | Medium |
| CO save → MeshBinder → output DB | Existing pipeline | Small |
| End-to-end journey test | Integration test | Large |

### Phase I-6: Contour Design Mode

**Scope:** User selects target elevation, tolerance → auto-generates curved alignment.

| Task | File | Effort |
|------|------|--------|
| UI: contour elevation slider + tolerance slider | Blender N-panel | Medium |
| Call `AlignmentContext.fromContour()` from slider | Python operator | Small |
| Display contour path as curve in viewport | Python viz | Medium |
| Cut/fill volume computation (ΔZ × area per station) | Java | Medium |
| Gradient + super-elevation validation rules | AD_Val_Rule migration | Small |

---

## §7 Witnesses Summary

| Phase | Witnesses | Count | Status |
|-------|----------|-------|--------|
| I-1 | W-INFRA-FILTER-1..4, W-INFRA-SNAP-1..4, InfraUIFilter×7, InfraVocab×7 | 22 | **DONE** |
| I-2 | W-CONTEXT-ROOM-1..2, W-CONTEXT-ALIGN-1..3, W-CONTEXT-POLY | 6 | **DONE** |
| I-2 | W-CONTEXT-TERRAIN-1..3 (real 689-point survey) | 3 | **DONE** |
| I-2 | W-TERRAIN-SNAP-1..4 (road layers, bridge, tunnel, drag) | 4 | **DONE** |
| I-2 | W-CONTOUR-1..3 (contour-follow, bridge vs road, curvature) | 3 | **DONE** |
| I-3 | BR 10/10, RD 4/4, RL 4/4 Rosetta Stone gates | 18 | **DONE** |
| I-4 | W-CUTFILL-FLAT-1..3, W-CUTFILL-ALIGN-1, W-SNAP-TERRAIN-1..4, W-GRADING-*-1..2, W-GRADING-SNAP-1 | 13 | **DONE** |
| I-5 | Blender drag + CO save E2E | 2 | planned |
| I-6 | Contour design mode E2E | 2 | planned |
| **Total** | | **~64** | |

---

## §8 References

| Document | Covers |
|----------|--------|
| `InfrastructureAnalysis.md` | IFC4X3 file inventory, entity census, verb mapping |
| `StrategicIndustryPositioning.md` | Gap matrix, Moat 5 (Infrastructure First-Mover) |
| `DISC_VALIDATION_DB_SRS.md` | validation.db schema, 30 infra rules |
| `BIM_Designer.md §17` | snap(), jurisdiction, Design Mode |
| `BlenderBridge.md` | Java-smart/Python-dumb pipe protocol |
| `classify_rd.yaml` | Road Rosetta Stone: 4 carriageways, PAV/MARK/GEO disciplines |
| `classify_rl.yaml` | Rail Rosetta Stone: 66 sleepers @ 606mm, TRK/GEO disciplines |
| `dsl_rd.bim` / `dsl_rl.bim` | Infrastructure DSL scripts (extracted singularity) |
| `V011_facility_type.sql` | facility_type column on AD_Val_Rule |
| `ReportDAO.java` | 4D-7D report engine interface |
| Federation `pdf_terrain/samples/survey_highres_extracted.json` | 689-point terrain (real survey) |
| Federation `pdf_terrain/operator.py` | Survey PDF → elevation points → IFC |
| `PlacementContext.java` | Abstract container: Room + Alignment |
| `AlignmentContext.java` | Terrain corridors, contour-follow, curvature |
| `TerrainSnap.java` | ON_SURFACE / ABOVE / BELOW / PIER snap modes |
| `PlacementContextTest.java` | 16 witnesses on real terrain data |
| `StrategicIndustryPositioning.md` | Moat 5: infra first-mover, terrain-following |

---

*INFRA_DESIGNER_SRS.md v2.0 — Terrain-following placement model proven on real data.*
*Phases I-1 + I-2 + I-3 DONE. 38 witnesses GREEN on 689-point survey terrain.*
*Contour-following: 2.9km winding road, R=17m–1037m curves, heading 221° sweep.*
*204/204 tests GREEN. Rosetta Stones BR 10/10, RD 4/4, RL 4/4 undisturbed.*
