# Forge Suite — Geometry Assist Engine for Open BIM

> **Foundation:** [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) §5 · [BBC](BOMBasedCompilation.md) §2.2.1 · [BIM_COBOL](BIM_COBOL.md) §17–18 · [EYES](EYES_SRS.md) §3–4 · [ShipYard](ShipYard.md) §6–8 · [BlenderBridge](BlenderBridge.md)

<div class="bim-banner" markdown>
<b>The forge makes Bonsai's geometry tools smarter.</b> Bonsai can extrude a profile — the forge tells it how long, at what angle, and whether it meets code. Bonsai can generate a stair — the forge computes the whole multi-flight assembly, checks Blondel's formula, and shows the cost. For pieces no tool can create — dome panels, vault ribs, hull plates — the forge computes them from construction maths and places them in the viewport with drag handles. No node graph. Just a form, a shape in 3D, compliance verdicts, and a cost.
</div>

---

## 1. Three Levels of Geometry Assist

The forge is not just a data enrichment layer. It enhances geometry creation
at three levels — each progressively more valuable:

### Level 1: CREATE — Pieces Bonsai Can't Make

Dome panels in spherical coordinates. Barrel vault ribs on a cylindrical arc.
Ship hull plates from lofted station offsets. These geometries don't exist in
any catalog and no Bonsai native tool produces them. The forge computes their
positions, dimensions, and shapes from construction maths.

| Piece | What the forge creates | Bonsai alone |
|-------|----------------------|-------------|
| 72 dome panels | Spherical coords → (x,y,z) per panel, width varies per ring | Nothing — no spherical layout tool |
| 10 barrel vault ribs | Cylindrical arc → curvature, placement per rib | Nothing — no cylindrical layout tool |
| Hull plates | Cubic spline lofting → unique plate per station | Nothing — no lofting tool |
| Multi-flight stair | 2 stringers + 15 treads + landing, all positioned | Single flight shape only |

### Level 2: ASSIST — Make Existing Tools Smarter

Bonsai's profile extrusion works — but the user must calculate the length,
look up the cut angles, and position manually. The forge pre-computes from
BOM context so the user adjusts rather than starts from scratch.

| Assist | What the forge does | Bonsai alone |
|--------|-------------------|-------------|
| **Auto-length beam** | Reads column positions from BOM → computes span → sets extrusion length | User measures, types length |
| **Rafter from roof** | Reads pitch + span from roof BOM → computes length + cut angles → pre-fills ShapeBuilder | User does trigonometry |
| **Pipe segment** | Reads routing angle → computes arc + chord → pre-fills MEP segment | User calculates bend |
| **Smart defaults** | Pre-fills parameters from BOM context. User adjusts, not creates. | User enters everything from zero |
| **Propagation** | Change roof pitch → all rafters recompute length + cut angles | User re-does each rafter manually |

### Level 3: VERIFY — Check What the User Made

Even for manually-created geometry, the forge adds compliance checking, cost
feedback, and fabrication data that no BIM tool provides live during editing.

| Verification | What the forge adds | Bonsai alone |
|-------------|-------------------|-------------|
| **Building code** | Riser 185mm, Blondel 2R+G=610mm → PASS. Live, as you edit. | No code checking |
| **Cost delta** | Change pitch 30°→35° → +RM 480 timber, +2 hours labour | No cost feedback |
| **Fabrication data** | Cut list: 6004mm, top 60°, bottom 30°, birdsmouth 15mm | No cut list |
| **Compliance** | Min bend radius ≥ 3×diameter → PASS/FAIL per AS/NZS 3500 | No plumbing code check |

---

## 2. Architecture — Six Parts

```
         BONSAI (viewport + UI)              BIM COMPILER (backend)
    ┌─────────────────────────┐         ┌───────────────────────────┐
    │                         │         │                           │
    │  ③ ForgePanel           │ params  │  ① ForgeEngine            │
    │  [Pitch: 30°]  ←drag─┐ │────────→│  compute()                │
    │  [Span: 5200mm]      │ │         │   ├─ dimensions           │
    │                      │ │         │   ├─ fabrication data      │
    │  Compliance:         │ │◄────────│   ├─ compliance verdicts   │
    │  ✓ pitch OK          │ │ results │   └─ cost delta (CostDAO) │
    │  ✓ Blondel OK        │ │         └───────────────────────────┘
    │  Cost: +RM 42.50     │ │
    │                      │ │         ┌───────────────────────────┐
    │  ⑥ ForgeGizmo ───────┘ │         │  ⑥ ad_forge_gizmo (DB)   │
    │  (drag handles on mesh) │         │  metadata-driven configs  │
    │                         │         └───────────────────────────┘
    │  ② ForgeMesh            │ calls
    │  (creates/updates mesh) │────────→ Bonsai native: ShapeBuilder,
    │                         │          bmesh, DumbProfileGenerator
    │  [Approve] [Adjust]     │
    └─────────────────────────┘
            │                                    │
            ▼                                    ▼
    ④ ForgePromotion                    ⑤ ForgeFabrication
    → component_library.db              → work order PDF / 5D
      (new LOD, reusable)                 (cut list for shop floor)
```

| Part | What | Status |
|------|------|--------|
| **① ForgeEngine** | Java: dimensions, angles, compliance, cost from parameters | **DONE** — 6 engines (incl. RebarCageForge), W-FORGE-1..11 |
| **② ForgeMesh** | Python: calls Bonsai native tools to create/update 3D mesh | NOT DONE |
| **③ ForgePanel** | Python: Bonsai sidebar — parameter form, compliance, cost | NOT DONE |
| **④ ForgePromotion** | Approve → component_library.db as LOD | NOT DONE |
| **⑤ ForgeFabrication** | Fabrication data → work order output | **DONE** — ForgeFabricationWriter → ad_forge_fabrication in output.db, S100-p59, W-FORGE-FAB-1..5 |
| **⑥ ForgeGizmo** | Python + DB: drag handles on forged geometry, metadata-driven | NOT DONE |

---

## 3. Interactive Editing — ForgeGizmo (Part ⑥)

### 3.1 What The User Experiences

The user forges a stair. In the viewport, 15 treads + 2 stringers + a landing
appear. On the geometry, **drag handles** let them:

- **Drag the top of the stair up/down** → storey height changes → step count
  recomputes → all treads reposition → compliance panel updates → cost updates
- **Drag the stair width sideways** → width changes → stringer recalculates →
  cost updates
- **Click +/- buttons** → add/remove a tread → riser height redistributes →
  Blondel check re-evaluates
- **Type a number** → click on the pitch handle, type "33.7" → all geometry
  recomputes to the exact value

This is the same interaction Bonsai already provides for doors, windows, and
stairs — but driven by **forge formulas** instead of hardcoded geometry, and
enriched with **compliance + cost** that Bonsai doesn't show.

### 3.2 Bonsai's Existing Gizmo Infrastructure (Verified)

Bonsai ships an enterprise-grade gizmo framework. We use it, not rebuild it.

**Core infrastructure:** `drawing/gizmos.py` (58K+ lines)

| Gizmo type | What it does | Used by |
|-----------|-------------|---------|
| `GizmoDimension` | Drag arrow + text label along an axis | Door width, stair height |
| `GizmoLock` | Toggle icon (lock/unlock a parameter) | Stair total length lock |
| `GizmoPlus` / `GizmoMinus` | Click to increment/decrement integer | Stair tread count |
| `GizmoCycle` | Click to cycle through types | Door swing direction |
| `GizmoValidate` / `GizmoCancel` | Confirm / cancel editing | All parametric elements |
| `GizmoArc` | Arc visualization for rotational params | — |

**Existing parametric gizmo groups:**
- `GizmoDoorEdition` — width, height, panel count, swing type
- `GizmoWindowEdition` — width, height, sill height
- `GizmoStairEdition` — 11 dimension gizmos + lock + plus/minus + cycle

**Interaction features (all built-in):**
- Drag along constrained axis
- Ctrl = snap to nearby vertices (KD-tree)
- Shift = precision mode (0.1× movement)
- Click without drag → keyboard numeric input (supports units: mm, cm, m)
- View-dependent positioning (gizmos face the camera)

**Pattern:** `BaseParametricGizmoGroup` mixin + `DimensionGizmoConfig` list.

### 3.3 Metadata-Driven Gizmo Configuration

**The key design decision:** Gizmo configurations come from the database, not
from hardcoded Python classes. One generic `ForgeGizmoGroup` reads metadata
rows and creates handles dynamically.

**Table: `ad_forge_gizmo`**

```sql
CREATE TABLE ad_forge_gizmo (
    AD_Forge_Gizmo_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    PieceType            TEXT NOT NULL,      -- 'SLOPE_CUT', 'DOME_SECTION', etc.
    ParamName            TEXT NOT NULL,      -- 'pitch', 'span', 'radius', etc.
    GizmoType            TEXT NOT NULL,      -- 'DIMENSION', 'PLUS_MINUS', 'CYCLE', 'LOCK'
    Axis                 TEXT NOT NULL,      -- 'X', 'Y', 'Z', 'ROTATION', 'SCALE'
    MinValue             REAL,              -- compliance min (maps to ForgeEngine check)
    MaxValue             REAL,              -- compliance max
    StepSize             REAL,              -- for PLUS_MINUS: increment per click
    Label                TEXT NOT NULL,      -- display label: "Pitch °", "Span mm"
    SortOrder            INTEGER DEFAULT 0,  -- gizmo layout order
    IsActive             INTEGER DEFAULT 1
);
```

**Example rows:**

| PieceType | ParamName | GizmoType | Axis | Min | Max | Step | Label |
|-----------|-----------|-----------|------|-----|-----|------|-------|
| SLOPE_CUT | pitch | DIMENSION | ROTATION | 5 | 60 | — | Pitch ° |
| SLOPE_CUT | span | DIMENSION | X | 500 | 12000 | — | Span mm |
| SLOPE_CUT | width | DIMENSION | Y | 35 | 300 | — | Width mm |
| SLOPE_CUT | depth | DIMENSION | Z | 35 | 300 | — | Depth mm |
| STAIR_FLIGHT | height | DIMENSION | Z | 2000 | 5000 | — | Height mm |
| STAIR_FLIGHT | tread | DIMENSION | X | 220 | 350 | — | Tread mm |
| STAIR_FLIGHT | riser | DIMENSION | Z | 150 | 220 | — | Riser mm |
| STAIR_FLIGHT | width | DIMENSION | Y | 600 | 2000 | — | Width mm |
| STAIR_FLIGHT | step_count | PLUS_MINUS | — | 3 | 30 | 1 | Steps |
| DOME_SECTION | radius | DIMENSION | SCALE | 1000 | 50000 | — | Radius mm |
| DOME_SECTION | rings | PLUS_MINUS | — | 2 | 50 | 1 | Rings |
| DOME_SECTION | segments | PLUS_MINUS | — | 4 | 64 | 2 | Segments |
| BARREL_VAULT | rise | DIMENSION | Z | 100 | 20000 | — | Rise mm |
| BARREL_VAULT | ribs | PLUS_MINUS | — | 2 | 50 | 1 | Ribs |
| PIPE_BEND | angle | DIMENSION | ROTATION | 1 | 180 | — | Angle ° |
| PIPE_BEND | radius | DIMENSION | X | 50 | 5000 | — | Radius mm |

**The generic Python gizmo class (ForgeGizmoGroup):**

```python
class ForgeGizmoGroup(BaseParametricGizmoGroup, bpy.types.GizmoGroup):
    """
    Metadata-driven gizmo group for all forge piece types.
    Reads ad_forge_gizmo rows from DB → creates DimensionGizmoConfig per row.
    One class handles ALL piece types — no per-type Python code needed.
    """
    bl_label = "Forge Gizmo"
    bl_space_type = 'VIEW_3D'

    # Dynamic — built from DB at setup time
    dimension_gizmo_props = []  # populated from ad_forge_gizmo

    def setup(self, context):
        piece_type = self.get_piece_type(context)
        rows = read_forge_gizmo_rows(piece_type)  # DB query
        for row in rows:
            config = DimensionGizmoConfig(
                attr_name=row.param_name,
                axis=AXIS_MAP[row.axis],
                min_value=row.min_value,
                max_value=row.max_value,
                # ... position computed from current geometry
            )
            self.dimension_gizmo_props.append(config)
        super().setup(context)
```

**Adding a new piece type's gizmos = INSERT rows. No Python code.**

### 3.4 The Interaction Loop

```
User drags gizmo handle (e.g., pitch arrow on rafter)
    │
    ▼
ForgeGizmoGroup.move_set_cb() fires
    │
    ├─ Updates parameter value (pitch: 30° → 33.7°)
    │
    ├─ Sends to ForgeEngine via BlenderBridge:
    │     FORGE SLOPE_CUT pitch:33.7 span:5200 width:90 depth:45
    │
    ├─ ForgeEngine.compute() returns:
    │     ├─ length: 6248mm (was 6004mm)
    │     ├─ cut_angle_top: 56.3° (was 60°)
    │     ├─ compliance: all PASS
    │     └─ cost_delta: +RM 18.40
    │
    ├─ ForgeMesh regenerates geometry:
    │     ShapeBuilder.extrude(profile=90×45, length=6248, cut=56.3°)
    │
    ├─ ForgePanel updates:
    │     Compliance: ✓ pitch 33.7° in 5-60° range
    │     Cost: +RM 18.40
    │
    └─ Viewport updates — user sees the rafter change shape live
```

**Response time target:** < 200ms from drag to viewport update. ForgeEngine
computation is < 1ms (pure maths). BlenderBridge round-trip is the bottleneck
(~50-100ms). ShapeBuilder extrusion is ~10ms. Achievable.

### 3.5 Reference Implementations to Study

| Implementation | What to learn | Path |
|---------------|-------------|------|
| `GizmoStairEdition` | 11 dimension gizmos, +/- buttons, lock toggle, type cycling | `model/stair.py:450` |
| `GizmoDoorEdition` | Width/height drag, panel count, swing cycle | `model/door.py:766` |
| `ClashMarkerGizmo` | Custom sphere geometry, color-coded status, click interaction | `federation/clash/gizmo.py` |
| `DumbProfileJoiner` | Interactive extending/joining of profiles at intersections | `model/profile.py:283` |
| `ProfileDecorator` | GPU-drawn overlays for visual feedback during editing | `model/decorator.py` |
| **Archipack** (external) | Gold standard for drag-to-adjust parametric BIM in Blender. On-screen handles, auto-booleans, live update. | `blender-archipack.org` |

---

## 4. What Bonsai Already Has (Verified)

Before claiming gaps, we verified what Bonsai ships natively. We call these
tools — we don't rebuild them.

| Capability | Bonsai module | Lines | What it does |
|-----------|--------------|-------|-------------|
| **Stair generation** | `model/stair.py` | 763 | 3 types (concrete, wood/steel, generic), parametric, 11 gizmos |
| **Profile extrusion** | `model/profile.py` + `shape_builder.py` | 3,258 | Any IfcMaterialProfileSet, cardinal points, joining |
| **MEP segments** | `model/mep.py` | 1,270 | Duct/pipe/cable authoring, ports, fittings (manual) |
| **Beam/column** | `model/profile.py` (DumbProfileGenerator) | — | From profile set, polyline-based placement |
| **bmesh API** | Used in 13+ modules | — | Full Blender mesh primitives from Python |
| **Clash detection** | `clash/` module | — | Intersection, collision, clearance; smart grouping |
| **Gizmo framework** | `drawing/gizmos.py` | 58K+ | Enterprise-grade: dimension, lock, +/-, cycle, validate |

**What Bonsai does NOT have (verified gaps):**

| Gap | Evidence | Our answer |
|-----|----------|-----------|
| **Rebar/reinforcement** | Zero matches in upstream modules. OSArch confirms gap. | Federation: `rebar_generator.py` (MS 1347, BS 8110, EC2) |
| **Automated MEP routing** | Native MEP is manual only. No pathfinding. | Federation: A* with GPU collision (0.2-0.5s) |
| **Formula-driven mesh** | No parametric mesh from construction formulas | ForgeMesh calling native tools with ForgeEngine params |
| **Compliance-as-you-design** | No building code checking during editing | ForgeEngine + AD_Val_Rule, live in ForgePanel |
| **Fabrication data** | No cut lists, no shop drawing data | ForgeEngine fabrication output |
| **Cost-during-design** | No cost feedback during parametric editing | CostDAO integration in ForgePanel |
| **Context-aware defaults** | Parameters entered from zero, no BOM awareness | ForgeEngine reads BOM → pre-fills parameters |

---

## 5. Proprietary Comparison — The Painful Table

**Verified facts only** — no marketing claims.

### 5.1 Feature Comparison

| Capability | Revit ($3,525/yr) | Tekla ($6,500+/yr) | Grasshopper + Rhino ($995) | ArchiCAD ($4,090/yr) | **BIM Compiler + Bonsai ($0)** |
|-----------|-------------------|-------------------|--------------------------|---------------------|-------------------------------|
| **Parametric walls/floors** | YES — constraint families | Basic | Via plugins | YES — GDL | Via library LOD + ASI sizing |
| **Stair generation** | YES — inflexible, most-complained | NO | Manual scripting | YES | YES — Bonsai native + Forge compliance + cost |
| **Profile/beam extrusion** | YES — family system | YES — strongest | Via scripting | YES — GDL | YES — Bonsai ShapeBuilder + Forge auto-length |
| **Steel connections** | Basic — needs extensions | **BEST IN CLASS** — 500+ | Via plugins | NO | NOT YET |
| **Rebar detailing** | Basic — needs extensions | **BEST IN CLASS** | Via plugins | NO | YES — Federation (3 standards) |
| **MEP auto-routing** | YES — suboptimal, needs fixes | NO | NO | Basic (add-on) | YES — Federation A* + GPU (0.2-0.5s) |
| **Clash detection** | Basic (Navisworks: +$2,775/yr) | YES | NO | Basic | YES — Bonsai native + Federation (44K/0.5s) |
| **Compliance checking** | Partial — no live feedback | NO | Via plugins | NO | YES — AD_Val_Rule, 9 jurisdictions, live |
| **Cost estimation** | NO — 3rd party ($$$) | NO — 3rd party | NO | NO | **YES — CostDAO, 5D native, live delta** |
| **4D scheduling** | NO — Synchro (+$2,775/yr) | NO — external | NO | NO | **YES — native 4D from BOM** |
| **BOM compilation** | NO — schedules only | Partial | NO | NO | **YES — full BOM tree → C_OrderLine** |
| **Interactive drag editing** | YES — dimension grips | YES — parametric | Via GH player | YES — GDL handles | YES — ForgeGizmo (metadata-driven) |
| **Fabrication output** | NO — 3rd party | YES — DSTV steel CNC | NO | NO | PARTIAL — ForgeEngine data |
| **IFC native** | Export only (lossy) | Export only (lossy) | Via plugins | Better than Revit | **YES — native format** |
| **Open source** | NO | NO | NO | NO | **YES — GPL/LGPL** |
| **Vendor lock-in** | .rvt proprietary | .tekla proprietary | .3dm open, GH fragile | .pln proprietary | **NONE — IFC + SQLite** |

### 5.2 Total Cost of Ownership (5-year, single seat)

| Stack | Year 1 | Years 2-5 | **5-Year Total** |
|-------|--------|-----------|-----------------|
| Revit + Navisworks | $6,300 | $25,200 | **$31,500** |
| Tekla Structures | $7,500 | $30,000 | **$37,500** |
| Revit AEC Collection | $4,645 | $18,580 | **$23,225** |
| ArchiCAD | $4,590 | $18,360 | **$22,950** |
| Rhino + Grasshopper | $995 | $0 | **$995** (perpetual) |
| **BIM Compiler + Bonsai** | **$0** | **$0** | **$0** |

### 5.3 The Three Things No Proprietary Tool Does

**1. BOM-to-cost pipeline.** Revit counts doors. It cannot produce the
purchase order, material cost per line item, labour hours, or schedule
impact. We can — the BOM IS the model. 76 verbs, 2475 products.
*Status: PRODUCTION — 35 buildings compiled.*

**2. IFC as native format.** Every proprietary tool treats IFC as an
export checkbox. Bonsai treats IFC as THE format. No translation, no loss.
*Status: PRODUCTION — Bonsai ships this.*

**3. Construction-level data at design time.** Proprietary BIM models the
finished building. We model how to build it — cut lists, rebar schedules,
step counts, pipe spool data, live compliance, live cost.
*Status: PARTIAL — ForgeEngine computes, wiring to UI and work order pending.*

---

## 6. Their Rebuttals — And Our Honest Response

| Their rebuttal | Valid? | Our honest response |
|---------------|--------|-------------------|
| **"Not production-ready for large projects"** | **PARTIAL.** Bonsai v0.8 is younger. 500MB+ models untested. | True for mega-projects. 35 buildings compile (TE at 48K elements). Pipeline proven; viewport maturity is the gap. |
| **"No ecosystem — where are the families?"** | **VALID.** Revit has millions of families. | component_library.db has 2,475 products. The forge adds computed pieces. But Revit's ecosystem is decades ahead. |
| **"No trained workforce"** | **VALID.** Millions know Revit, thousands know Bonsai. | Market maturity, not technical limitation. Early-adopter territory. |
| **"Tekla connections are unmatched"** | **TRUE.** 500+ parametric connections + CNC output. | Acknowledged. Steel connections NOT in near-term roadmap. |
| **"Grasshopper can do anything your forge does"** | **TRUE for geometry. FALSE for ERP.** | Grasshopper can't attach a purchase order, check building code, or show cost. Also: "spaghetti monster" UX vs our form-with-fields. |
| **"BIM 360/ACC cloud collaboration is proven"** | **VALID.** Multi-user, multi-discipline at scale. | No cloud equivalent. BackOffice is single-server. Real gap for multi-office. |
| **"Your routing is just pathfinding — Revit does duct sizing"** | **PARTIAL.** Revit MEP does pressure drop + duct sizing. | True — our routing is spatial, not engineering. Duct sizing is future territory. |
| **"IFC native but my consultants use Revit"** | **VALID reality.** Industry runs on .rvt. | Government BIM mandates (UK, SG, MY, NO) increasingly require IFC. Trend favours open. |
| **"Your drag handles are just Bonsai's gizmos"** | **PARTIALLY TRUE.** Same framework. | Same framework, different intelligence. Our gizmos recompute formulas + show compliance + cost. Bonsai's gizmos only resize mesh. Metadata-driven means new types without code. |

### 6.1 Where We Win Despite the Rebuttals

| Scenario | Why we win |
|----------|----------|
| **SME, cost-sensitive** | $0 vs $31,500 over 5 years |
| **Government with IFC mandate** | Native IFC, built-in compliance |
| **Contractor needing BOM + cost** | BOM compilation — no proprietary tool does this |
| **Fabrication shop** | Direct model-to-cut-list — no re-entry |
| **Developing markets** | License cost = hard barrier. Open source removes it. |
| **Education** | Free + transparent = teachable |

---

## 7. Community Demand — Verified

| Request | Source | Forge Suite relevance |
|---------|--------|----------------------|
| **Parametric rebar tool** | OSArch community discussions — explicit ask, no native tool | Federation has it. Port to Java verbs. |
| **Boolean/union modelling** | OSArch community discussions — "major missing capability" | Not forge territory — Bonsai core |
| **Better asset import UX** | OSArch community discussions — library browsing pain | ForgePanel could surface library search |
| **Sheet management** | OSArch community discussions — ISO 19650, eliminate Inkscape | Not forge — `2D_LAYOUT.md` |
| **Quantity takeoff → cost** | NBS report, JBK ConTech — gap across ALL tools | **YES — 5D native, core differentiator** |
| **Construction sequencing (4D)** | NBS report — "bolted on" everywhere | **YES — native 4D from BOM** |
| **Live compliance feedback** | AEC hackathon themes, student projects | **YES — ForgePanel + AD_Val_Rule** |
| **Cost-of-change visualization** | No tool provides this. Unproven demand. | **SPECULATIVE — build and test** |
| **Interactive parametric editing** | Archipack demonstrates demand in Blender ecosystem | **YES — ForgeGizmo, metadata-driven** |

---

## 8. What Forge Suite Is NOT

- **NOT a Grasshopper replacement.** Grasshopper is visual programming.
  The forge is a parameter form with drag handles. Different tools.
- **NOT structural analysis.** No FEA, no load calculations. Use
  Robot/SAP2000/ETABS. The forge checks code limits, not structural adequacy.
- **NOT competing with Tekla connections.** Acknowledged gap.
- **NOT a rendering engine.** Bonsai (Blender) renders. We compute.

---

## 9. Detailed Part Specifications

### Part ① ForgeEngine — DONE

**Location:** `BIM_COBOL/src/main/java/com/bim/cobol/forge/`
**Spec:** [GEOMETRY_FORGE_SRS.md](GEOMETRY_FORGE_SRS.md) §5

Six engines: SLOPE_CUT, STAIR_FLIGHT, PIPE_BEND, DOME_SECTION, BARREL_VAULT, REBAR_CAGE.
ForgeVerb (76th verb) registered in VerbRegistry. W-FORGE-1..11 PASS.

**Interface:** `ForgeEngine.compute(VerbContext, Map<String,String>) → ForgeResult`
**Output:** `ForgeResult` (pass, promotable, pieceType, summary, records, compliance, error)
**Records:** `GeometryRecord` (bomLineId, productId, length/width/depth, fabrication map, placement xyz, rotation)

### Part ② ForgeMesh — Python, calls Bonsai native tools

**Purpose:** Create or update 3D mesh in viewport from ForgeEngine results.

**When needed:**
- Level 1 (CREATE): Dome panels, vault ribs, hull plates — no library LOD exists
- Level 2 (ASSIST): Rafter with computed length + cut angles — ShapeBuilder extrusion

**When NOT needed:**
- Standard library pieces (walls, doors, furniture) — geometry already in component_library.db

**Implementation approach:**

For **elongated members** (rafter, stringer, pipe):
```python
# Python side — receives ForgeResult via BlenderBridge
def forge_mesh_extrude(result):
    rec = result.records[0]
    profile = ShapeBuilder.rectangle(rec.width_mm / 1000, rec.depth_mm / 1000)
    body = ShapeBuilder.extrude(profile, rec.length_mm / 1000)
    # Apply cut angles from fabrication data
    if 'cut_angle_top' in rec.fabrication:
        apply_angled_cut(body, rec.fabrication['cut_angle_top'], end='top')
    if 'cut_angle_bottom' in rec.fabrication:
        apply_angled_cut(body, rec.fabrication['cut_angle_bottom'], end='bottom')
    return body
```

For **panel arrays** (dome sections, vault segments):
```python
def forge_mesh_panels(result):
    objects = []
    for rec in result.records:
        panel = bmesh.new()
        # Create flat quad at computed dimensions
        verts = create_quad(rec.width_mm / 1000, rec.depth_mm / 1000)
        # Position at computed world coordinates
        translate(panel, rec.placement_x, rec.placement_y, rec.placement_z)
        rotate(panel, rec.rotation)
        objects.append(panel)
    return objects
```

**Bonsai tools used (verified available):**
- `ShapeBuilder.extrude()` — profile extrusion (2,046 lines, 13+ modules use it)
- `ShapeBuilder.rectangle()` / `.circle()` — profile creation
- `bmesh.ops.contextual_create()` — face creation from vertices
- `bmesh.ops.extrude_face_region()` — 3D solid from face
- `DumbProfileGenerator` — standard structural profiles (I-beam, channel, etc.)

**BlenderBridge commands to define:**
- `FORGE_MESH_EXTRUDE` — create extruded member from ForgeResult
- `FORGE_MESH_PANELS` — create panel array from ForgeResult
- `FORGE_MESH_UPDATE` — regenerate after parameter change (gizmo drag)
- `FORGE_MESH_CLEAR` — remove preview mesh (cancel)

**WebUIServer bridge commands — DONE (S100-p90, W-FORGE-BRIDGE-1..5):**
- `forgeCompute` — dispatches ForgeVerb with piece_type + params, returns ForgeResult as JSON
- `forgeCost` — returns fabrication cost breakdown from ad_forge_fabrication
- `forgeList` — lists available piece types and their required parameters

### Part ③ ForgePanel — Bonsai sidebar UI

**Purpose:** Parameter input + live compliance + live cost in a sidebar panel.

**Layout:**
```
┌─────────────────────────────┐
│  FORGE                      │
│  ┌───────────────────────┐  │
│  │ Piece: [SLOPE_CUT  ▼] │  │
│  └───────────────────────┘  │
│                             │
│  Parameters                 │
│  Pitch:  [30.0    ] °       │
│  Span:   [5200    ] mm      │
│  Width:  [90      ] mm      │
│  Depth:  [45      ] mm      │
│                             │
│  ─── Compliance ──────────  │
│  ✓ Pitch 30° in 5-60°      │
│  ✓ Span 5200mm ≤ 12000mm   │
│  ✓ Length 6004mm > 0        │
│                             │
│  ─── Fabrication ─────────  │
│  Length:     6004 mm        │
│  Cut top:   60.0°           │
│  Cut bottom: 30.0°          │
│  Birdsmouth: 14.9 mm        │
│                             │
│  ─── Cost ────────────────  │
│  Material:  RM 42.50        │
│  Labour:    RM 15.00        │
│  Total:     RM 57.50        │
│                             │
│  [Compute]  [Approve]       │
│  [Reset]    [Cancel]        │
└─────────────────────────────┘
```

**Bonsai UI pattern:** `bpy.types.Panel` with `bpy.props.EnumProperty` for
piece type selector, `FloatProperty` / `IntProperty` for parameters, labels
for compliance/cost. Same pattern as Bonsai's existing property panels.

**Data flow:**
1. User selects piece type → panel shows relevant parameter fields
   (from `ad_forge_gizmo` metadata — same rows drive both panel fields and gizmos)
2. User enters/adjusts parameters → [Compute] or auto-compute on change
3. Parameters sent to ForgeEngine via BlenderBridge
4. Results populate compliance, fabrication, and cost sections
5. [Approve] → triggers Part ④ (promotion) + Part ⑤ (fabrication output)

**Auto-populate from BOM context:**
When the user selects an existing BOM line (e.g., a rafter line in the BOM
tree), the panel pre-fills parameters from BOM data:
- pitch from roof BOM attributes
- span from AABB of parent element
- width/depth from M_Product cross-section
The user adjusts rather than starts from scratch (Level 2: ASSIST).

### Part ④ ForgePromotion — Approve → Library

**Purpose:** On Approve, write forged piece to `component_library.db`.

**Path:** DocAction=Approve lifecycle (DocAction_SRS.md). Same machinery as
BOM template promotion (ProjectOrderBlueprint.md §4).

**What gets written:**
- New `M_Product` row with computed dimensions
- LOD prefix entry with geometry data (length, width, depth, fabrication)
- Traceability: ForgeResult.pieceType + parameters stored as provenance

**User choice at Approve time:**
| Choice | When | What's promoted |
|--------|------|----------------|
| Singular LOD | Simple piece (rafter, pipe bend) | One M_Product with dimensions |
| BOM | Compound piece (dome, vault) | M_BOM + M_BOM_Line tree |

### Part ⑤ ForgeFabrication — Cut List → Work Order

**Purpose:** Attach fabrication data to work order output.

**What gets emitted per ForgeResult record:**

| Field | Example | Use |
|-------|---------|-----|
| piece_type | SLOPE_CUT | Identifies the fabrication type |
| length_mm | 6004 | Cutting length |
| cut_angle_top | 60.0 | Saw angle at top end |
| cut_angle_bottom | 30.0 | Saw angle at bottom end |
| birdsmouth_depth | 14.9 | Notch depth |
| material_cost | 42.50 | From CostDAO |
| labour_hours | 0.5 | From CostDAO |

**Output targets:**
- Work order PDF (new fabrication section)
- output.db (new columns on element output or companion table)
- 5D cost line items (material + labour per forged piece)

### Part ⑥ ForgeGizmo — Metadata-Driven Drag Handles

**Purpose:** Interactive drag handles on forged geometry, configured from DB.

**See §3 above for full specification.**

**Key design principles:**
1. ONE generic Python class (`ForgeGizmoGroup`) — no per-type Python code
2. Gizmo configurations from `ad_forge_gizmo` table — new types = INSERT rows
3. Drag → recompute → regenerate mesh → update compliance + cost (< 200ms)
4. Uses Bonsai's proven `BaseParametricGizmoGroup` + `DimensionGizmoConfig`
5. Same interaction as Bonsai doors/windows/stairs — familiar to Bonsai users

---

## 10. Phases

All phases are SPEC ONLY. No timelines.

**Phase 1 — ForgeEngine.** DONE (S99-forge + S100-forge). 6 engines, 11 witnesses,
FORGE verb registered (76th). [GEOMETRY_FORGE_SRS.md](GEOMETRY_FORGE_SRS.md) §5.

**Phase 2 — ForgeFabrication wiring.** DONE (S100-p59). ForgeFabricationWriter
writes to ad_forge_fabrication table in output.db. W-FORGE-FAB-1..5 PASS.

**Phase 3 — ForgePanel.** Bonsai sidebar UI. Parameter form + compliance +
cost. Depends on BlenderBridge forge commands + CostDAO wiring (prompt 53).
Prompt: `60_forge_panel`.

**Phase 4 — ForgeMesh.** Call Bonsai native tools (ShapeBuilder, bmesh) from
ForgeEngine params via BlenderBridge. Prompt: `61_forge_mesh_bridge`.

**Phase 5 — ForgeGizmo.** Metadata-driven drag handles. `ad_forge_gizmo`
table + generic `ForgeGizmoGroup`. Prompt: `62_forge_gizmo`.

**Phase 6 — ForgePromotion.** Approve → component_library.db. DocAction path.
Prompt: `63_forge_promote_lod`.

**Phase 7 — Rebar port.** DONE (S100-forge). RebarCageForge — 6th engine,
implements MS 1347:2020 + BS 8110 + EC2. SlabReinforcement, BeamReinforcement,
ColumnReinforcement classes. W-FORGE-9..11 PASS.

**Phase 8 — Formula-as-metadata.** Migrate hardcoded formulas to
`ad_forge_formula` table. New piece types = SQL INSERTs. May not be needed
if piece type count stays small. [GEOMETRY_FORGE_SRS.md](GEOMETRY_FORGE_SRS.md) §10.

---

## 11. Relationship to Existing Specs

| Spec | Relationship |
|------|-------------|
| [GEOMETRY_FORGE_SRS.md](GEOMETRY_FORGE_SRS.md) | Part ① detail — ForgeEngine interface, 6 engines |
| [BlenderBridge.md](BlenderBridge.md) | Transport layer for Parts ②–⑥ |
| [BIM_Designer_SRS.md](BIM_Designer_SRS.md) | UI patterns for Part ③ |
| [BIM_Designer.md](BIM_Designer.md) | Design Mode interaction model |
| [DocAction_SRS.md](DocAction_SRS.md) | Approve lifecycle for Part ④ |
| [BIM_COBOL.md](BIM_COBOL.md) §17–18 | FORGE verb, verb patterns |
| [EYES_SRS.md](EYES_SRS.md) §3–4 | Verification of forged pieces |
| [ShipYard.md](ShipYard.md) §6–8 | Hull lofting — future ForgeEngine |
| [DocValidate.md](DocValidate.md) | AD_Val_Rule compliance infrastructure |

---

*Status: Phases 1, 2, 7 DONE (ForgeEngine, ForgeFabrication, RebarCageForge). Phases 3–6, 8 SPEC ONLY.*
