# Forge Suite — Parametric Computation for Open BIM

> **Foundation:** [GEOMETRY_FORGE_SRS](GEOMETRY_FORGE_SRS.md) §5 · [BBC](BOMBasedCompilation.md) §2.2.1 · [BIM_COBOL](BIM_COBOL.md) §17–18 · [EYES](EYES_SRS.md) §3–4 · [ShipYard](ShipYard.md) §6–8

<div class="bim-banner" markdown>
<b>Proprietary BIM tools model buildings. We compile them.</b> Revit draws a rafter; we compute it from pitch + span and hand the fabrication shop a cut list with cost and schedule. The gap is not geometry — Bonsai already has profile extrusion, stair generation, and clash detection. The gap is the bridge from design to procurement: compliance checking, fabrication data, and cost impact — live, as you design.
</div>

---

## 1. What Problem Does the Forge Suite Solve?

A BIM designer in Bonsai today can model a building. They can place walls, extrude
profiles, generate stairs, and detect clashes. What they cannot do:

1. **See the cut list** — a rafter is 6004mm, cut top 60°, cut bottom 30°.
   The timber yard needs this. Bonsai doesn't produce it.
2. **See the cost delta** — changing pitch from 30° to 35° adds RM 480 in
   timber and 2 hours of labour. No open-source BIM tool shows this live.
3. **See the code verdict** — riser height 185mm, Blondel 2R+G=610mm, PASS.
   Bonsai's stair generator creates geometry but doesn't check building code.
4. **Compute pieces the library doesn't have** — dome panels, barrel vault ribs,
   hull plates. These are site-specific; no catalog covers them.

The Forge Suite adds these four capabilities to Bonsai without replacing any
existing tool. Bonsai creates geometry. The forge enriches it with ERP data.

---

## 2. Architecture — Five Parts

```
         BONSAI (viewport)              BIM COMPILER (backend)          OUTPUT
    ┌────────────────────┐         ┌───────────────────────────┐   ┌──────────────┐
    │  ③ ForgePanel      │ params  │  ① ForgeEngine            │   │ component_   │
    │  [Pitch: 30°]      │────────→│  compute()                │   │ library.db   │
    │  [Span: 5200mm]    │         │   ├─ dimensions           │   │ (new LOD)    │
    │                    │         │   ├─ fabrication data      │   └──────┬───────┘
    │  Compliance:       │◄────────│   ├─ compliance verdicts   │          │
    │  ✓ pitch OK        │ results │   └─ cost delta (CostDAO) │     ④ Promote
    │  ✓ Blondel OK      │         └───────────────────────────┘          │
    │  Cost: +RM 42.50   │                                                │
    │                    │         ┌───────────────────────────┐          ▼
    │  ② ForgeMesh       │ calls   │  Bonsai native:           │   ┌──────────────┐
    │  (for new pieces   │────────→│  ShapeBuilder.extrude()   │   │ work order   │
    │   not in library)  │         │  bmesh primitives         │   │ PDF / 5D     │
    │                    │         │  stair.py (existing)      │   └──────────────┘
    │  [Approve] [Adjust]│         └───────────────────────────┘     ⑤ Fabrication
    └────────────────────┘
```

| Part | What | Status | Depends on |
|------|------|--------|-----------|
| **① ForgeEngine** | Java computation: dimensions, angles, compliance, cost | **DONE** — 5 engines, W-FORGE-1..8 | — |
| **② ForgeMesh** | Calls Bonsai native tools for pieces not in library | NOT DONE | BlenderBridge |
| **③ ForgePanel** | Bonsai sidebar: parameter input, compliance display, cost | NOT DONE | ①, BlenderBridge |
| **④ ForgePromotion** | Approve → component_library.db as LOD | NOT DONE | ② |
| **⑤ ForgeFabrication** | Cut list / fabrication data → work order output | NOT DONE | ① |

---

## 3. What Bonsai Already Has (Verified)

Before claiming gaps, we verified what Bonsai ships natively. These are NOT
forge territory — we call them, we don't rebuild them.

| Capability | Bonsai module | Lines | What it does |
|-----------|--------------|-------|-------------|
| **Stair generation** | `model/stair.py` | 763 | 3 types (concrete, wood/steel, generic), parametric, gizmos |
| **Profile extrusion** | `model/profile.py` + `shape_builder.py` | 3,258 | Any IfcMaterialProfileSet, cardinal points, joining |
| **MEP segments** | `model/mep.py` | 1,270 | Duct/pipe/cable authoring, ports, fittings (manual placement) |
| **Beam/column** | `model/profile.py` (DumbProfileGenerator) | — | From IfcMaterialProfileSet, polyline-based placement |
| **bmesh API** | Used in 13+ modules | — | Full Blender mesh primitives from Python |
| **Clash detection** | `clash/` module | — | Intersection, collision, clearance; smart grouping; visual |

**What Bonsai does NOT have (verified gaps):**

| Gap | Evidence | Our answer |
|-----|----------|-----------|
| **Rebar/reinforcement** | Zero matches in upstream `structural/` or `model/` modules. OSArch confirms: no parametric 3D rebar tool | Federation: `rebar_generator.py` + `rebar_standards.py` (MS 1347, BS 8110, EC2) |
| **Automated MEP routing** | Native MEP is manual segment placement only. No pathfinding | Federation: `mep_engineering/tool.py` — A* with GPU-accelerated collision, 200 waypoints in 0.2-0.5s |
| **Procedural mesh from formulas** | No parametric mesh generation. Anti-parametric-mesh policy in BIM Compiler (BBC §2.2.1), but ForgeEngine computes dimensions that ForgeMesh can feed to native tools | ForgeMesh (Part ②) — calls ShapeBuilder/bmesh with computed parameters |
| **Compliance-as-you-design** | No building code checking during editing | ForgeEngine compliance checks + AD_Val_Rule evaluation |
| **Fabrication data** | No cut lists, no shop drawing data | ForgeEngine fabrication output |
| **Cost-during-design** | No cost feedback during parametric editing | CostDAO integration in ForgePanel |

---

## 4. Proprietary Comparison — The Painful Table

This table compares what proprietary users pay for against what the BIM Compiler
+ Bonsai stack provides. **Verified facts only** — no marketing claims.

### 4.1 Feature Comparison

| Capability | Revit ($3,525/yr) | Tekla ($6,500+/yr) | Grasshopper + Rhino ($995) | ArchiCAD ($4,090/yr) | **BIM Compiler + Bonsai ($0)** |
|-----------|-------------------|-------------------|--------------------------|---------------------|-------------------------------|
| **Parametric walls/floors** | YES — constraint-based families | Basic | Via plugins | YES — GDL scripting | Via library LOD + ASI sizing |
| **Stair generation** | YES — notoriously inflexible, most-complained feature | NO | Manual scripting | YES | YES — Bonsai native (3 types) + Forge compliance checks |
| **Profile/beam extrusion** | YES — family system | YES — strongest | Via scripting | YES — GDL | YES — Bonsai DumbProfileGenerator + ShapeBuilder |
| **Steel connections** | Basic — needs extensions | **BEST IN CLASS** — 500+ parametric connections | Via plugins | NO | NOT YET — future forge territory |
| **Rebar detailing** | Basic — needs extensions | **BEST IN CLASS** — individual bar modelling | Via plugins | NO | YES — Federation (MS 1347, BS 8110, EC2) |
| **MEP auto-routing** | YES — produces suboptimal routes needing manual fixes | NO | NO | Basic (add-on) | YES — Federation A* with GPU collision (0.2-0.5s) |
| **Clash detection** | Basic (Navisworks for serious: +$2,775/yr) | YES | NO | Basic | YES — Bonsai native (3 modes) + Federation (44K elements in 0.5s, R-tree) |
| **Compliance checking** | Partial — no live code feedback | NO | Via plugins | NO | YES — AD_Val_Rule, 9 jurisdiction packs, live verdicts |
| **Cost estimation** | NO — needs 3rd party ($$$) | NO — needs 3rd party | NO | NO | **YES — CostDAO, 5D native, live cost delta** |
| **4D scheduling** | NO — needs Navisworks/Synchro (+$2,775/yr) | NO — needs external | NO | NO | **YES — native 4D, compiled from BOM** |
| **BOM compilation** | NO — schedules only | Partial — material lists | NO | NO — schedules only | **YES — full BOM tree, M_Product → M_BOM_Line → C_OrderLine** |
| **Fabrication output** | NO — needs 3rd party | YES — DSTV for steel CNC | NO | NO | PARTIAL — ForgeEngine data, PDF wiring pending |
| **IFC native** | Export only (quality complaints) | Export only (lossy roundtrip) | Via plugins | Better than Revit | **YES — IFC is the native format (Bonsai = IfcOpenShell)** |
| **Open source** | NO | NO | NO (Rhino perpetual but closed) | NO | **YES — GPL/LGPL** |
| **Vendor lock-in** | Proprietary .rvt format | Proprietary .tekla format | Open .3dm but GH definitions fragile | Proprietary .pln format | **NONE — IFC + SQLite** |

### 4.2 Total Cost of Ownership (5-year, single seat)

| Stack | Year 1 | Years 2-5 | **5-Year Total** |
|-------|--------|-----------|-----------------|
| Revit + Navisworks | $6,300 | $25,200 | **$31,500** |
| Tekla Structures | $7,500 | $30,000 | **$37,500** |
| Revit AEC Collection | $4,645 | $18,580 | **$23,225** |
| ArchiCAD | $4,590 | $18,360 | **$22,950** |
| Rhino + Grasshopper | $995 | $0 | **$995** (perpetual) |
| **BIM Compiler + Bonsai** | **$0** | **$0** | **$0** |

*Note: Rhino is perpetual at $995 — genuine value. But Rhino is a geometry
modeler, not a BIM tool. No BOM, no cost, no schedule, no compliance.*

### 4.3 The Three Things No Proprietary Tool Does

These are genuine differentiators, not aspirational claims:

**1. BOM-to-cost pipeline.** Revit can tell you how many doors you have.
It cannot tell you the purchase order, the material cost per line item,
the labour hours, or the schedule impact. We can — because the BOM IS
the model. 76 verbs, 2475 products, 4-DB architecture.
*Status: PRODUCTION — 35 buildings compiled.*

**2. IFC as native format.** Every proprietary tool treats IFC as an export
format — lossy, approximate, a compliance checkbox. Bonsai treats IFC as
THE format. IfcOpenShell reads and writes IFC natively. No translation,
no loss. *Status: PRODUCTION — Bonsai ships this.*

**3. Construction-level data at design time.** Proprietary BIM models the
finished building. We model how to build it — cut lists, rebar schedules,
step counts, pipe spool data. The fabrication shop gets data directly from
the model, not from a manual re-entry into a separate estimating tool.
*Status: PARTIAL — ForgeEngine computes fabrication data, not yet wired to
work order output.*

---

## 5. Their Rebuttals — And Our Honest Response

A Revit/Tekla user evaluating our stack will push back. Here is what they
will say and what is true:

| Their rebuttal | Is it valid? | Our honest response |
|---------------|-------------|-------------------|
| **"Not production-ready for large projects"** | **PARTIALLY VALID.** Bonsai v0.8 is younger than Revit 2025. Large-model performance is improving but not yet at Revit/Tekla scale for 500MB+ models. | True for mega-projects today. But 35 buildings compile correctly (SH through TE at 48K elements). The pipeline is proven; the viewport maturity is the gap. |
| **"No ecosystem — where are the families/plugins?"** | **VALID.** Revit has millions of parametric families. Bonsai's library is small. | component_library.db has 2,475 products. Growing with every compiled building. The forge adds computed pieces. But the Revit family ecosystem is decades ahead. |
| **"No trained workforce"** | **VALID.** Millions know Revit. Bonsai users number in thousands. | This is a market maturity issue, not a technical one. BIM Compiler's value proposition is for firms willing to invest in the open stack. Early-adopter territory. |
| **"Tekla's steel connections are unmatched"** | **TRUE.** 500+ parametric connections, direct CNC output. We have nothing comparable. | Acknowledged. Steel connection detailing is NOT in our roadmap for near term. Tekla is best-in-class here. |
| **"Grasshopper can do anything your forge does"** | **TRUE for geometry. FALSE for ERP integration.** Grasshopper can compute a rafter length. It cannot attach a purchase order, check Malaysian building code, and show the cost delta. | The forge's value is not the trigonometry — it's the BOM traceability + compliance + cost. Grasshopper definitions are also fragile ("spaghetti monster"), single-user, and require programming skill. Our forge is a form with fields. |
| **"BIM 360/ACC cloud collaboration is proven"** | **VALID.** Autodesk's cloud platform handles multi-user, multi-discipline on large projects. | We have no cloud collaboration equivalent. BackOffice is single-server. This is a real gap for multi-office projects. |
| **"Your MEP routing is just pathfinding — Revit does full duct sizing + pressure drop"** | **PARTIALLY VALID.** Revit MEP calculates duct sizes from airflow requirements and pipe sizes from flow rates. Our routing finds a collision-free path but doesn't do mechanical engineering. | True. Our routing is spatial, not engineering. Duct sizing from load calculations is future territory (AD_Val_Rule could carry the rules, but the formulas aren't implemented). |
| **"IFC native sounds good but my consultants use Revit"** | **VALID reality check.** The industry runs on Revit. IFC is the theory; .rvt is the practice. | True today. But government BIM mandates (UK, Singapore, Malaysia, Norway) increasingly require IFC deliverables. The trend favours open formats. |

### 5.1 Where We Win Despite the Rebuttals

| Scenario | Why we win |
|----------|----------|
| **Small-to-medium firm, cost-sensitive** | $0 vs $31,500 over 5 years. The maths is brutal. |
| **Government/public sector with IFC mandate** | Native IFC = no export pain. Compliance checking built in. |
| **Contractor who needs BOM + cost, not pretty renders** | BOM compilation is our core. No proprietary tool does this. |
| **Fabrication shop needing cut lists** | Direct from model to shop floor — no manual re-entry. |
| **Developing markets (Malaysia, Indonesia, etc.)** | License cost is a hard barrier. Open source removes it. |
| **Educational institutions** | Free + transparent = teachable. Students learn the why, not just the how. |

---

## 6. Effort Assessment — Verified

### Part ① ForgeEngine — DONE

Five engines: SLOPE_CUT, STAIR_FLIGHT, PIPE_BEND, DOME_SECTION, BARREL_VAULT.
8 witnesses PASS. Pure Java trigonometry, no external dependencies.

**What it actually delivers today:** Dimensions, cut angles, step counts, arc
lengths, panel positions, compliance verdicts. Backend only — no UI.

### Part ② ForgeMesh — MEDIUM effort, VERIFIED feasible

**What it needs to do:** For pieces NOT in the library (dome panels, vault ribs),
call Bonsai native tools to create mesh from ForgeEngine parameters.

**Verified Bonsai capabilities we would call:**
- `ShapeBuilder.extrude()` — for elongated members (rafters, stringers). Verified:
  2,046-line utility, used by 13+ Bonsai modules.
- `bmesh.ops.contextual_create()` + `extrude_face_region()` — for flat panels
  (dome sections). Verified: used by `stair.py`, `roof.py`, `wall.py`.
- `DumbProfileGenerator` — for standard-profile members. Verified: 1,212 lines,
  handles any IfcMaterialProfileSet.

**Dependency:** BlenderBridge (`docs/BlenderBridge.md`) — Java sends parameters
via pipe, Python creates mesh in Bonsai. Bridge exists but forge commands not
yet defined.

**What does NOT need ForgeMesh:** Standard pieces (walls, doors, beams, columns)
that already exist in `component_library.db`. For these, ForgeEngine only
provides fabrication data — Bonsai already has the geometry.

### Part ③ ForgePanel — MEDIUM effort, VERIFIED feasible

**What it needs to do:** Sidebar panel in Bonsai Designer with:
- Piece type selector
- Parameter fields (auto-populated from BOM context where possible)
- Live compliance verdicts (green/red per rule)
- Live cost delta (from CostDAO)
- Approve / Adjust buttons

**Verified Bonsai UI pattern:** Bonsai uses `bpy.types.Panel` with property
groups. The stair generator (`stair.py`) has a full parameter panel with
interactive gizmos — same pattern.

**Dependency:** ForgeEngine results must travel back via BlenderBridge.

### Part ④ ForgePromotion — SMALL effort

**What it does:** On Approve, write the forged piece to `component_library.db`
as a new LOD entry (M_Product + geometry dimensions).

**Path exists:** `DocAction=Approve` lifecycle is already implemented
(`DocAction_SRS.md`). Promotion machinery exists for BOM templates
(`ProjectOrderBlueprint.md §4`). Forge just produces a new input format.

### Part ⑤ ForgeFabrication — SMALL effort

**What it does:** Attach ForgeEngine fabrication data (cut angles, step counts,
bend specs) to the work order PDF.

**Path exists:** Work order output already emits from compiled BOM. Fabrication
data is a new column set on the output — same pattern as ASI attribute
overrides.

---

## 7. Community Demand — What's Been Asked For

From OSArch community forums, Bonsai GitHub issues, and AEC industry surveys:

| Request | Source | Forge Suite relevance |
|---------|--------|----------------------|
| **Parametric rebar tool** | OSArch community discussions — explicit ask, no native tool | Federation has it. Port to Java verbs for integration. |
| **Boolean/union modelling** | OSArch community discussions — "major missing modelling capability" | Not forge territory — Bonsai core issue |
| **Better asset import UX** | OSArch community discussions — library browsing pain | ForgePanel could surface library search alongside forge |
| **Sheet management / drawing production** | OSArch community discussions — ISO 19650 naming, eliminate Inkscape | Not forge territory — `2D_LAYOUT.md` addresses this |
| **Partial file loading** | OSArch community discussions — filter large IFC by spatial hierarchy | Not forge territory — IfcOpenShell core |
| **Faster mesh handling** | OSArch community discussions — bypass OCC for tessellations | Not forge territory — Bonsai renderer optimization |
| **Quantity takeoff → cost** | NBS report, JBK ConTech — gap across ALL tools | **YES — this is our core differentiator.** 5D native. |
| **Construction sequencing (4D)** | NBS report — "bolted on" in all proprietary tools | **YES — native 4D from BOM compilation.** |
| **Live compliance feedback** | AEC hackathon themes, student projects | **YES — ForgePanel + AD_Val_Rule.** |
| **Cost-of-change visualization** | No tool provides this. Unproven demand. | **SPECULATIVE — build and test.** |

---

## 8. What Forge Suite Is NOT

To prevent scope creep and honest positioning:

- **NOT a Grasshopper replacement.** Grasshopper is a general-purpose visual
  programming environment. The forge is a parameter form. Different tools,
  different audiences.
- **NOT a structural analysis tool.** The forge computes geometry and checks
  code limits (max span, min pitch). It does NOT do finite element analysis,
  load calculations, or structural adequacy checks. Use Robot/SAP2000/ETABS
  for that.
- **NOT a rendering engine.** The forge produces geometry and data. Bonsai
  (Blender) handles visualisation.
- **NOT competing with Tekla's steel connections.** Acknowledged gap.
  Steel connection detailing is a specialist domain we don't address.

---

## 9. Phases

All phases are SPEC ONLY. No timelines.

**Phase 1 — ForgeEngine.** DONE (S99-forge). 5 engines, 8 witnesses, FORGE
verb registered (76th). GEOMETRY_FORGE_SRS.md §5.

**Phase 2 — ForgeFabrication wiring.** Attach fabrication data to work order
output. Small effort — column additions to output tables.

**Phase 3 — ForgePanel.** Bonsai sidebar UI. Parameter form + compliance
display + cost delta. Requires BlenderBridge forge commands.

**Phase 4 — ForgeMesh.** For pieces not in library: call Bonsai native tools
(ShapeBuilder, bmesh) from ForgeEngine parameters via BlenderBridge.

**Phase 5 — ForgePromotion.** Approve → component_library.db. DocAction path.

**Phase 6 — Rebar port.** Port Federation `rebar_generator.py` + standards
to Java verbs. Standards tables (MS 1347, BS 8110, EC2) are the asset —
the Java port is mechanical.

**Phase 7 — Formula-as-metadata.** Migrate hardcoded formulas to
`ad_forge_formula` table. New piece types become SQL INSERTs.
(May not be needed — see GEOMETRY_FORGE_SRS.md §10.)

---

## 10. Relationship to Existing Specs

| Spec | Relationship |
|------|-------------|
| [GEOMETRY_FORGE_SRS.md](GEOMETRY_FORGE_SRS.md) | Part ① detail — ForgeEngine interface, 5 engines, formula-as-metadata |
| [BlenderBridge.md](BlenderBridge.md) | Transport layer for Parts ②–④ |
| [BIM_Designer_SRS.md](BIM_Designer_SRS.md) | UI patterns for Part ③ |
| [DocAction_SRS.md](DocAction_SRS.md) | Approve lifecycle for Part ④ |
| [BIM_COBOL.md](BIM_COBOL.md) §17–18 | FORGE verb registration, verb patterns |
| [EYES_SRS.md](EYES_SRS.md) §3–4 | Verification of forged pieces (archetype, ratios) |
| [ShipYard.md](ShipYard.md) §6–8 | Hull lofting — future ForgeEngine (not in Phase 1) |

---

*Status: Phase 1 DONE (ForgeEngine). Phases 2–7 SPEC ONLY.*
