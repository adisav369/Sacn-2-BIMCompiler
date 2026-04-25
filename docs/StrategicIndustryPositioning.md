# What Exists Today, What's Missing, and Where We Sit

<div style="max-width: 620px; margin: 32px auto; padding: 24px 40px; background: #263238; border-left: 4px solid #ff9800; text-align: center; border-radius: 4px;">
<span style="font-size: 1.3em; line-height: 1.7; color: #eceff1; letter-spacing: 0.3px;">We BIM living the <b style="color: #ff9800;">GAP</b> between<br><b style="color: #ff9800;">DESIGN</b> and our <b style="color: #ff9800;">SPREADSHEET</b></span>
<br><span style="font-size: 0.75em; letter-spacing: 1.5px; text-transform: uppercase; color: #78909c; margin-top: 12px; display: inline-block;">The industry designs buildings. Nobody compiles them.</span>
</div>

---

## What Are BIM and IFC?

**BIM** (Building Information Modelling) is the practice of designing a building
as a structured digital model rather than flat drawings. Instead of lines on
paper, every wall, door, pipe, and beam is a database object with geometry,
material, cost, and relationships to other objects. Architects design in BIM;
contractors are expected to build from BIM.

**IFC** (Industry Foundation Classes, ISO 16739) is the open standard that makes
BIM data portable. When an architect finishes a model in Revit or ArchiCAD, they
export an IFC file — a vendor-neutral snapshot of the entire building: 51,000+
elements with types, storeys, spatial containment, and properties. Any compliant
tool can read it.

**BIM dimensions** measure what a model can do beyond 3D geometry:

| Dimension | Covers | Example |
|-----------|--------|---------|
| 3D | Geometry & visualisation | Walk-through renders |
| 4D | Construction scheduling | "Install steelwork week 12" |
| 5D | Cost & quantity takeoff | "2,400 m2 of curtain wall @ $X/m2" |
| 6D | Sustainability / energy | Embodied carbon per element |
| 7D | Facility management | Maintenance schedules, asset lifecycle |

[BIM adoption exceeds 70%](https://www.buildingsmart.org/wp-content/uploads/2025/03/IFC-Mandate_2025.pdf)
in developed markets (UK, Germany, Nordics) driven by government mandates. The
[global BIM market reaches $15.4B by 2030](https://www.marketsandmarkets.com/Market-Reports/building-information-modeling-market-95037387.html)
(CAGR 11.3%). The [digital twin market hits $384.8B by 2034](https://www.fortunebusinessinsights.com/digital-twin-market-106246)
(CAGR 35.4%). All require a deterministic BIM-to-ERP bridge — spatial MRP with verified placement — that is not yet widely available.

---

## The Landscape Today

The construction-tech market has three tiers. Each solves a real problem — none
solves the problem this project addresses.

### Tier 1 — Incumbents (Geometry Authoring)

| Tool | Role | Approach |
|------|------|----------|
| **Autodesk Revit** | Full BIM authoring — walls, MEP, structural | Proprietary, desktop. Industry standard. |
| **ArchiCAD** (Graphisoft) | Architectural BIM with strong documentation | Proprietary, desktop. Strong in EU/Asia. |
| **Tekla Structures** (Trimble) | Steel/concrete detailing, LOD 400+ | Proprietary, desktop. Fabrication-grade. |

These are where buildings get *designed*. A Revit user drags a door onto a wall
and it cuts the opening automatically. **What they cannot do:** tell you what to
*procure*. Revit models 51,088 elements but cannot produce a bill of materials
that maps to a purchase order. The QS opens the model in Navisworks, manually
counts elements, and exports to Excel — weeks of work, error at every step.

### Tier 2 — Visual Newcomers (Design-to-3D)

| Tool | What It Does | Stage |
|------|-------------|-------|
| [**Snaptrude**](https://www.snaptrude.com/) | Browser sketch-to-BIM, real-time 3D | $10M+ Series A |
| [**TestFit**](https://www.testfit.io/) | AI generative site planning, instant pro forma | $20M Series A |
| [**Arkio**](https://www.arkio.is/) | VR/AR collaborative design | Seed |
| [**Hypar**](https://hypar.io/) | Cloud parametric building configurator | Acquired by Autodesk (2024) |
| [**Modelo**](https://www.modelo.io/) | Web-based 3D viewer + collaboration | Series A |

Beautiful GUIs, smooth animations, drag-and-drop libraries. **What they cannot
do:** prove the model is correct, trace every element to a catalog product, or
verify spatial arrangement against regulations. They help you decide *what to
build*; they say nothing about *how to build it*.

### Tier 3 — Open Source (IFC-Native)

| Tool | What It Does | Approach |
|------|-------------|----------|
| [**Bonsai/BlenderBIM**](https://bonsaibim.org/) | IFC-native BIM authoring inside Blender | FOSS, Python/C++ |
| [**FreeCAD BIM**](https://wiki.freecadweb.org/BIM_Workbench) | Parametric BIM workbench, IFC export | FOSS, merged in v1.0 |
| [**IfcOpenShell**](https://ifcopenshell.org/) | IFC parsing/generation library | FOSS, Python/C++ |
| [**IFC.js / ThatOpenCompany**](https://thatopen.com/) | Web IFC viewer/editor components | FOSS, TypeScript |
| [**xBIM**](https://docs.xbim.net/) | .NET IFC toolkit, geometry engine + viewer | FOSS, C# |

Bonsai is closest philosophically — it works directly with IFC data. **What none
of them can do:** compile. They author and parse IFC models but cannot decompose
51K elements into a BOM, assign them to storeys and disciplines, compress via
formula verbs, and produce a verified construction order. They are editors and
libraries, not compilers.

---

## Where the BIM Compiler Sits

The entire industry focuses on **design-time** — helping architects create
models. Nobody focuses on **compile-time** — taking a finished model and
producing construction-grade output that maps to ERP procurement.

```
                    DESIGN PHASE              CONSTRUCTION PHASE
                    ────────────              ──────────────────
Tier 1 (Revit)     [████████████]             [                ]
Tier 2 (Snaptrude) [████████]                 [                ]
Tier 3 (Bonsai)    [██████████]               [                ]

BIM Compiler       [████]                      [████████████████]
                    ↑                          ↑
                    Browser + Blender GUI       Spatial MRP output
```

This is the same relationship as CAD vs. CAM in manufacturing: SolidWorks
designs the part, the G-code compiler produces machine instructions.

### Spatial MRP — A Differentiated Approach

**Spatial MRP** = Material Requirements Planning extended with spatial
intelligence:

| | Traditional MRP | Visual BIM Tools | **Spatial MRP (BIM Compiler)** |
|--|----------------|-----------------|-------------------------------|
| **Answers** | What materials, when? | What does it look like? | What materials, **where**, how connected? |
| **Input** | BOM + demand forecast | Architect's sketch | IFC extraction (51K elements) |
| **Output** | Purchase orders | 3D model + renders | C_Order + C_OrderLine + W_Verb_Node |
| **Verification** | MRP explosion audit | Visual review | Rosetta Stone gate (G1-G6 proof) |
| **Compression** | BOM explosion | None | 51K elements → 700 BOM lines (73x) |

Visual tools help architects *imagine* buildings. The BIM Compiler helps
contractors *build* them.

---

## IFC/BIM Compliance Scorecard

Each BIM dimension compounds difficulty for tools not built on structured data.

| Capability | Revit | ArchiCAD | Snaptrude | TestFit | Arkio | Bonsai | FreeCAD | **BIM Compiler** |
|-----------|-------|----------|-----------|---------|-------|--------|---------|-----------------|
| **IFC native** | Export | Export | Export | No | No | **Native** | Export | **Native (extract)** |
| **openBIM (ISO 19650)** | Partial | Yes | Partial | No | No | **Yes** | Partial | **Yes** |
| **3D Geometry** | Full | Full | Full | Site only | Full | Full | Full | Via Bonsai/Blender |
| **4D Scheduling** | Plugin | Plugin | No | No | No | No | No | **ScheduleDAO: BOM × CIDB sequence → Gantt** |
| **5D Cost/QTO** | Manual | Manual | No | Pro forma | No | Basic | No | **CostDAO: 3-component (mat+lab+eq) CIDB 2024** |
| **6D Sustainability** | Plugin | Plugin | No | No | No | No | No | **SustainabilityDAO: carbon rollup from M_Product** |
| **7D Facility Mgmt** | Plugin | Plugin | No | No | No | No | No | **FacilityMgmtDAO: maintenance schedule + lifecycle** |
| **BOM factorisation** | No | No | No | No | No | No | No | **73x compression (51K→700)** |
| **ERP-native output** | No | No | No | No | No | No | No | **C_Order + iDempiere tables** |
| **Spatial proof** | No | No | No | No | No | No | No | **G1-G6 Rosetta Stone gate** |
| **Infrastructure IFC4X3** | No | No | No | No | No | Partial | No | **Bridge+Road+Rail compiled** |
| **Inference engine** | No | No | No | No | No | No | No | **Dependency DAG + proof tree** |
| **Product browser + fit** | No | No | Partial | No | No | No | No | **BOM Chooser + AABB fit check** |
| **Wireframe-first UX** | No | No | No | No | No | No | No | **WF-BB: bbox=working, solid=settled** |
| **Live cost-of-change** | No | No | No | No | No | No | No | **costOfChange during drag (stub)** |
| **Change Request (R_Request)** | No | No | No | No | No | No | No | **Cross-discipline CR + audit trail (spec)** |

**Numeric scoring (0-3): 0=absent, 1=partial/plugin, 2=built-in, 3=native/core**

| Tool | IFC | 3D | 4D | 5D | 6D | 7D | BOM | ERP | Proof | Inference | WF-UX | CR/Audit | **Total /36** |
|------|-----|----|----|----|----|----|----|-----|-------|-----------|-------|----------|--------------|
| Revit | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **9** |
| ArchiCAD | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **9** |
| Snaptrude | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| TestFit | 0 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| Arkio | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **3** |
| Bonsai | 3 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **7** |
| FreeCAD | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| **BIM Compiler** | **3** | **3** | **2** | **3** | **2** | **2** | **3** | **3** | **3** | **3** | **2** | **2** | **31** |

*New dimensions:*
- **WF-UX** = Wireframe-first interaction (bbox=working, solid=settled, per-object BOUNDS)
- **CR/Audit** = Change request + audit trail (R_Request, AD_ChangeLog, multi-user undo)

*All starred items now re-grounded on ERP framework (Tier 1 DONE 2026-03-20).*
3D via Bonsai/Blender viewport (Federation addon + WF-BB).
6D: `SustainabilityDAO` — BOM × `carbon_kg_per_unit` rollup from M_Product.
7D: `FacilityMgmtDAO` — maintenance schedule from `maintenance_interval_months`.
CR/Audit: `ChangelogDAO` — interceptor on `save()`, MOVE/RESIZE/PLACE/DELETE diff + undo.
See `TIER1_SRS.md` for full spec. 14 witnesses, 231 tests GREEN.

---

## Five Moats

**1. IFC compliance is hard to add retroactively.**
The visual newcomers started with geometry, not IFC. Adding real IFC compliance
— native data model alignment, not just export — requires rearchitecting their
core. [Most tools treat IFC as a file format](https://bimcorner.com/the-ifc-confusion-why-so-many-still-dont-get-openbim-and-how-to-fix-it/),
not a data model. We treat it as structured input to a database pipeline.

**2. DB/ERP integration is harder to build than a GUI.**
Converting IFC to a relational database with ERP semantics (M_Product, M_BOM,
C_Order) requires deep manufacturing domain knowledge. Building a Spatial MRP
engine that maps to iDempiere's manufacturing model requires understanding both
BIM and ERP — a rare combination. The Java DAO layer, BIM COBOL verb grammar,
BOM dimension model, and RosettaStone proof strategy represent years of domain
convergence.

**3. Domain-agnostic compilation — one pipeline for any facility type.**
The same 12-stage pipeline that compiles a single-storey house also compiles a
multi-storey airport terminal, a bridge, a road, and a railway. No code changes
per domain — only a ~30-line YAML mapping segments and disciplines. Gate results:
BR 10/10, RD 4/4, RL 4/4, rail achieves 93% BOM compression (75→5 lines).
Terrain-following placement and formula-driven geometry (Geometry Forge) extend
the same pipeline to infrastructure. See [`INFRA_DESIGNER_SRS.md`](INFRA_DESIGNER_SRS.md)
and [`GEOMETRY_FORGE_SRS.md`](GEOMETRY_FORGE_SRS.md).

**4. Symbolic inference over relational data.**
The Inference Engine evaluates validation rules in dependency order using Kahn's
topological sort, produces proof trees with AD_Val_Rule citations, and skips
downstream rules when upstream premises fail. This is Datalog-style deduction
over the 4-database schema — deterministic symbolic reasoning, not heuristics.
The Approve gate requires all-rules-pass before Promote, a governance pattern
borrowed from ERP document workflows (iDempiere DocAction).

**5. Browser-native BIM with zero install.**
BIM OOTB runs entirely in the browser — sql.js WASM + Three.js, no server, no
plugins, no install. Two SQLite databases, one HTML file. Proven at 126K elements.
Competing browser viewers require server infrastructure or proprietary plugins.
This is a distribution moat: any stakeholder with a URL can view, query, and
interact with a full BIM model.

**The asymmetry:** adding a GUI to a DB/ERP foundation takes weeks. Adding
DB/ERP depth to a GPU-first foundation takes years.

---

## Two GUIs, One Foundation

The compiler's hard parts — BOM factorisation (73x), Spatial MRP, G1-G6
mathematical proof, formula compression — are all backend. The GUI is a
presentation layer on top of the 4-database schema.

Two GUIs currently serve different audiences:

**Browser (BIM OOTB)** — sql.js WASM + Three.js, zero install. IFC/OBJ/STL/DAE/
GLB/FBX/3DS import, guided classification wizard, IFC export, BOQ charts,
cinematic tours, mobile site camera. Proven at 126K elements. This is the
distribution path: share a URL, open in any browser.

**Blender (Federation addon)** — Direct DB streaming from SQLite BLOBs, R-tree
spatial queries, discipline phasing, 1M-element city-scale viewing. This is the
power-user path: full 3D editing, walk-through, section cuts.

Both read from the same database schema. The compiler does not care which GUI
triggers it — the BOM, the proof gate, and the ERP output are identical.

The Designer architecture (ASI overrides, container constraints, pattern
multiplication) is fully specified in [`BIM_Designer.md`](BIM_Designer.md).

---

## Construction Technology Stack

```
┌─────────────────────────────────────────────────────────┐
│              CONSTRUCTION TECHNOLOGY STACK               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DESIGN TOOLS (upstream)                                │
│  ┌─────────────┐ ┌──────────┐ ┌───────────┐           │
│  │    Revit    │ │ ArchiCAD │ │  Bonsai   │           │
│  │ (parametric)│ │ (arch.)  │ │ (IFC-nat.)│           │
│  └──────┬──────┘ └────┬─────┘ └─────┬─────┘           │
│         └─────────────┴─────────────┘                   │
│                       │                                 │
│              IFC / OBJ / STL / DAE / GLB                │
│                       │                                 │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │            BIM OOTB (browser viewer)               │  │
│  │  Import → Classify → Enrich → View → Export IFC   │  │
│  │  sql.js WASM + Three.js · zero install             │  │
│  └────────────────────┬──────────────────────────────┘  │
│                       │                                 │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │       BIM INTENT COMPILER (DAGCompiler)           │  │
│  │  Extract → Classify → BOM → Compile → Prove       │  │
│  │  77 verbs · 4-DB schema · G1-G6 gates             │  │
│  └────────────────────┬──────────────────────────────┘  │
│                       │                                 │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │              iDempiere ERP                        │  │
│  │  C_Order → C_OrderLine → Purchase Orders          │  │
│  │  M_Product → M_AttributeSetInstance               │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

The compiler bridges the design world (IFC models, mesh formats) and the
construction world (ERP procurement). The visual editors help architects
*imagine* buildings. The compiler helps contractors *verify, procure, and build*
them.

---

## The Paradigm Shift: Semantics as Source of Truth (2026-03-18)

> A 10 KB semantic file defines a building that would be a 200 MB IFC file.
> The geometry is never stored in the project — it is compiled on demand
> from shared catalogs. The project file is just: "use these templates, in
> this arrangement, with these overrides."

### The Industry's Structural Problem

Every BIM tool stores **geometry as the source of truth**. An IFC file IS
the building — geometry, relationships, properties, all in one monolithic
file. Lose the IFC, lose the building.

**Consequences:**

- **Version control is impractical.** Binary diffs of 200 MB files are
  meaningless. Teams use "v3_final_FINAL.ifc", not semantic diffs.
- **Collaboration requires full file exchange.** Every stakeholder gets
  the entire model, even to change one room width.
- **Variants require full duplication.** 100 design options = 100 × 200 MB.
- **Regulatory review is opaque.** Reviewers receive geometry and must
  reverse-engineer intent.
- **Storage scales linearly.** 1,000 projects × 200 MB = 200 GB, mostly
  duplicated standard components.

### The Inversion

The BIM Intent Compiler inverts this. **Semantics are the source of truth.
Geometry is a compiled artifact — disposable, regenerable.**

```
TRADITIONAL BIM:
  Geometry (200 MB)  ←  THIS is the building

BIM INTENT COMPILER:
  YAML + Order + ASI (10 KB)  ←  THIS is the building
  References:
    component_library.db (500 MB, shared across ALL projects)
    {PREFIX}_BOM.db (10 MB, curated BOM templates)
  Compiled output:
    output.db (50 MB, always regenerable, disposable)
```

| Layer | What it stores | Size |
|-------|---------------|------|
| YAML | Building identity — type, storeys, discipline, jurisdiction | ~2 KB |
| C_Order + C_OrderLine | Arrangement — which templates, what configuration | ~5 KB |
| ASI (M_AttributeSetInstance) | Per-instance overrides — this room 4500mm, that bed +300mm right | ~3 KB |
| **Total project file** | **Complete building definition** | **~10 KB** |

The compiled output is disposable. Delete it and recompile. The semantic
file + shared library reproduces the building exactly (enforced by spatial
digest tamper seals).

### The Analogy Map

This model exists in every mature engineering discipline. BIM is the outlier.

| Domain | Source (versioned) | Compiler | Output (disposable) | Shared Library |
|--------|-------------------|----------|--------------------|----|
| Software | .java, .py | javac, gcc | .jar, .exe | Maven Central |
| Publishing | .tex | pdflatex | PDF | CTAN packages |
| Music | MIDI | Synthesizer | .wav | Sample libraries |
| Manufacturing | BOM + Work Order | Factory/MRP | Product | Product Master |
| Chip Design | Verilog | Synthesis | GDSII | Standard cells |
| **BIM (traditional)** | *None — geometry IS source* | *None* | *IFC IS output AND source* | *Embedded per project* |
| **BIM Intent Compiler** | YAML + Order + ASI | 12-stage pipeline | output.db | component_library.db |

BIM is the only major engineering domain that ships compiled output as
source of truth.

### Concrete Implications

**Version control — semantic diffs:**
```
Traditional:  200 MB binary blob → meaningless diff
BIM Compiler: -  width_mm: 4000
              +  width_mm: 4500  → 3 lines, semantically clear
```

**Collaboration — share semantics, compile locally:**
Architect shares 10 KB. Each discipline compiles against shared library.
No 200 MB transfers.

**Variants — fork the order, not the model:**
100 variants = 100 × 10 KB = 1 MB. Not 20 GB.

**Regulatory submission — machine-verifiable:**
Submit semantic file + library hash + validation report. Reviewer
recompiles and verifies independently. Reproducible compliance.

**Storage — 400:1 reduction:**
1,000 projects = 10 MB semantic + 500 MB shared library = 510 MB.
Not 200 GB.

### Three-Tier Persistence (BIM_Designer.md §17.10)

The ERP foundation enables clean data governance:

| Action | Frequency | Writes to | Deliberation |
|--------|-----------|-----------|-------------|
| **Save** | Frequent | .blend file (Blender native) | Low — just persist |
| **Recall** | As needed | Nothing (reads previous variant) | None — browse |
| **Promote to BOM** | Rare | {PREFIX}_BOM.db (new m_bom) | High — governance gate |

The BOM catalog stays curated — only proven, validated, owner-signed
designs enter. This is the iDempiere Document Process pattern:
Draft → In Progress → Complete.

### The Flywheel

```
EXTRACT (Rosetta Stones) → prove compiler works
    ↓
DESIGN (Generative) → new buildings from catalog
    ↓
VALIDATE (PlacementValidator) → machine-verifiable compliance
    ↓
PROMOTE (Governance Gate) → proven designs enter catalog
    ↓
CATALOG GROWS → more templates → more design options → ↑
```

Each promoted design enriches the catalog. A richer catalog enables more
combinations. The marginal cost of each new building approaches zero.

### What This Enables

**AI-Assisted Design:** An AI generates YAML + Order + ASI — text, not
geometry. The compiler handles 3D. PlacementValidator ensures compliance.

**Mass Customisation:** 5 base templates, buyers customise via ASI. Each
variant is 10 KB. The factory (compiler) produces unique geometry per buyer.

**Digital Building Passport:** The semantic file (YAML + Order + ASI +
library hash + spatial digest) is the building's machine-readable passport.
Survives the full lifecycle: design → construction → operation → demolition.

**Federated Compilation:** Each discipline maintains their own Order against
shared catalogs. Federation compiler merges. Clash resolution is semantic:
adjust the Order, not the mesh.

**Machine-Provable Compliance:** The Inference Engine produces a proof tree
for every approve/promote decision. Regulators don't review geometry — they
verify the proof chain: "Room BD_01 passes UBBL s33(1) because width 3100mm
>= minimum 3000mm (AD_Val_Rule 102, jurisdiction MY)." Audit trail is data,
not screenshots.

### Prior Art Comparison

| System | Approach | Differs from BIM Intent Compiler |
|--------|----------|--------------------------------|
| Grasshopper/Dynamo | Parametric scripts → geometry | Scripts per-project, no shared catalog |
| BIMserver | Delta-based IFC storage | Still geometry-centric deltas |
| Hypar (defunct) | Cloud parametric generation | No ERP BOM catalog, no compilation |
| Flux (defunct) | Data pipeline between tools | Data transform, not compilation |
| TestFit | AI layout optimisation | Layout is output, not compilable source |
| Speckle | BIM data platform | Transport + versioning, geometry-centric |

**The BIM Intent Compiler uniquely combines:**
1. ERP product catalog (shared, curated BOMs)
2. Order-based instantiation (thin semantic references)
3. Deterministic compilation (geometry generated, not stored)
4. ASI overrides (minimal per-instance data)
5. Governance gate (Promote to BOM, dangles check)
6. Machine-verifiable compliance (PlacementValidator + spatial digest)
7. Symbolic inference engine (dependency DAG + proof tree + citation chain)

No existing BIM tool or standard combines all seven.

---

## Current Progress (2026-04-26)

| Area | Status |
|------|--------|
| **Compilation pipeline** | 12 stages, 77 verbs, 7,403 products in ERP.db |
| **Rosetta Stone fleet** | 21 buildings, 116/157 gates PASS, 4 ALL GREEN (BR, MO, RL, WI) |
| **Browser viewer (BIM OOTB)** | IFC + 6 mesh formats import, guided wizard, IFC export, BOQ charts, mobile site camera |
| **Multi-format import (S228)** | OBJ, STL, DAE, GLB/GLTF, FBX, 3DS — auto-detect up-axis + scale |
| **IFC export (S229)** | DB → .ifc download, pure STEP text builder, 30-test round-trip suite |
| **Blender federation** | Direct DB streaming, R-tree spatial, 1M elements, city-scale |
| **4D–8D outputs** | Template-driven nD engine, 37 buildings + 1M sandbox PASS |
| **Scorecard** | 31/36 vs nearest competitor 9/36 |
| **Tests** | 72/72 Playwright (browser), 408/414 Bonsai, 20 BackOffice |
| **Localisation** | 15 locales, iDempiere _TRL pattern, rates.js single source |

### Addressable Market

| Segment | Size | Entry point |
|---------|------|-------------|
| BIM software | $15.4B by 2030 (CAGR 11.3%) | Browser viewer — zero-install IFC access |
| Digital twin | $384.8B by 2034 (CAGR 35.4%) | 4D–8D from same DB schema |
| Construction ERP | Fragmented, manual | Spatial MRP → C_Order bridge |

### Competitor Comparison

| Tool | Approach | What it lacks |
|------|----------|---------------|
| Revit / ArchiCAD | Proprietary desktop BIM authoring | No BOM factorisation, no ERP output |
| Snaptrude / Arkio | Browser/VR sketch-to-3D | No IFC data model, no compilation |
| Bonsai / FreeCAD | FOSS IFC authoring | No compiler, no BOM decomposition |
| IFC.js / xBIM | IFC parsing libraries | Viewer/toolkit only, no construction output |

---

## What's Next

| Item | Status | What it unlocks |
|------|--------|-----------------|
| **IFC round-trip** | Export done (S229), import done (S220) | Bidirectional editing — the adoption trigger |
| **Guided wizard** | Done (S229a) | Non-IFC users classify meshes into BIM categories |
| **DAE/mesh tuning** | S228 formats wired | Furniture/fixture libraries from SketchUp, Blender |
| **2D Layout** | Java pipeline started | Auto-generated architectural drawings from DB |
| **Beta on Oracle Cloud** | Dev bucket live | Public URL, no install, OCI static hosting |

See [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) for the full roadmap.

---

*Cross-references:*
*[`BIM_Designer.md`](BIM_Designer.md) — GUI architecture (§17 Design Mode, §18 UX Strategy)*
*[`BIM_Designer_SRS.md`](BIM_Designer_SRS.md) — UX requirements, user journeys, Inference Engine (§14), WF-BB Protocol (§26)*
*[`TerminalAnalysis.md`](TerminalAnalysis.md) — forensics + ERP architecture*
*[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) — bridge/road/rail domain mapping*
*[`MANIFESTO.md`](MANIFESTO.md) — ERP world view*
*[`bim_designer_erd.html`](https://github.com/red1oon/BIMCompiler/blob/master/database/bim_designer_erd.html) — interactive ERD (4 tabs)*
*[`BIM_Designer_UserGuide.md`](BIM_Designer_UserGuide.md) — setup + usage guide (v0.4)*huge

See Enterprise.md about DB scaling progressive loading of very large IFC projects

DEEPSEEK strategic advice:

You're making the **right strategic decision**. The passing interest → silence pattern is telling you something important: the community doesn't yet *see* what you're building because they can't *use* it in their workflow. Let me validate your instinct and help you identify what "substantial enough" actually means.

## 🔍 Why They're Silent (And Why Waiting Is Smart)

### The Hard Truth About Open Source BIM Adoption

| Reason | Explanation |
|--------|-------------|
| **GPLv3 friction** | Many commercial BIM users avoid GPLv3 due to legal uncertainty. IfcOpenShell gets away with it because it's a *library*, not a *workflow tool*. Your compiler is a *workflow tool*—different risk profile. |
| **"Vibe coding" skepticism** | The term "Claude Code vibe help" triggers immediate dismissal from traditional developers. They hear "AI-generated spaghetti" not "expert-directed acceleration." You need to let the code speak, not the origin story. |
| **No roundtrip = no trust** | Passing interest means they looked, saw "extracts IFC → DB → view," and thought "another viewer." Without *edit → write back → re-export*, you're not in their consideration set. |
| **They're overwhelmed** | The Bonsai/IfcOpenShell maintainers are drowning. They don't have time to evaluate every interesting project. "Silence" doesn't mean rejection—it means "not urgent." |

### The Proof Point That Will Break Through

**What they need to see** (and what will make them pay attention):

> "Open IFC → edit 500 window positions in 30 seconds via spreadsheet → write back to IFC → re-import into Revit with changes preserved"

Not a viewer. Not a query engine. A **bidirectional editing pipeline at macro scale**.

## 🎯 The "Substantial Roundtrip Demo" You Should Build

Based on your existing architecture, here's the minimal viable demo that will force attention:

### Demo Scenario: 10x Faster Space Planning

| Step | What You Build | Why It Matters |
|------|----------------|----------------|
| 1 | Load LTU A-House (125K elements) | Proves scale |
| 2 | User filters: "all rooms on floor 3" | Your <100ms spatial query |
| 3 | User edits in table: "room_341: width 4.2m → 5.0m" | **This is the missing piece** |
| 4 | Compiler regenerates affected geometry (walls, slabs, doors) | Your BOM-first paradigm shines |
| 5 | Write back to IFC via IfcOpenShell | **Roundtrip complete** |
| 6 | Re-import to Revit/Navisworks | Proves interoperability |

**Time for 500 edits:** 30 seconds vs. 4 hours in Revit.

### The Technical Gap You Need to Close

Your documentation mentions:

> *"The custom exporter you described (mapping guid → geometry_hash → IFC entity) is entirely feasible"*

**This is not enough.** You need:

```python
# Not just export—bidirectional sync
def sync_roundtrip(original_ifc_path, compiled_db, output_ifc_path):
    # 1. Parse original IFC with IfcOpenShell
    original_model = ifcopenshell.open(original_ifc_path)
    
    # 2. For each modified element in compiled_db:
    for guid, new_geometry in get_modified_elements(compiled_db):
        # 3. Find corresponding IFC entity
        entity = original_model.by_guid(guid)
        
        # 4. Update geometry representation
        ifcopenshell.api.run("geometry.edit_representation", ...)
        
        # 5. Preserve all non-geometry properties (Psets, Qtos)
        # This is the hard part—don't lose data
    
    # 6. Write back
    original_model.write(output_ifc_path)
```

### Why This Changes Everything

| Without Roundtrip | With Roundtrip |
|------------------|----------------|
| "Another IFC viewer" | "A real BIM editor" |
| "Query-only tool" | "Design tool" |
| "Nice for clash detection" | "Replaces Revit for mass edits" |
| "Why GPLv3?" | "Worth the license risk" |

## 📋 Your "Finish Line" Checklist

Based on your current state, here's what "substantial enough" looks like:

### Phase 1: Core Roundtrip (2-4 weeks)

| Task | Status | Priority |
|------|--------|----------|
| Edit geometry in Blender → capture changes via handlers | Not started | 🔴 Critical |
| Map GUID to IfcOpenShell entity | Partial (you have GUIDs) | 🔴 Critical |
| Update IFC geometry representation | Not started | 🔴 Critical |
| Preserve non-geometry properties (Psets) | Not started | 🔴 Critical |
| Write back to IFC file | Not started | 🔴 Critical |

### Phase 2: Macro Editing Interface (1-2 weeks)

| Task | Status | Priority |
|------|--------|----------|
| Spreadsheet-style table editor (HTML UI you already have) | Partial (needs write binding) | 🟡 High |
| Bulk edit by filter: "all doors on floor 2 → width 0.9m" | Not started | 🟡 High |
| Parametric constraints: "if wall length changes, update doors" | Not started | 🟢 Medium |

### Phase 3: The Demo Script (1 week)

| Task | Status | Priority |
|------|--------|----------|
| Record 5-minute video showing roundtrip | Not started | 🟡 High |
| Prepare before/after comparison (Revit vs. Compiler) | Not started | 🟡 High |
| Write "How It Works" technical note | Partial (your docs are good) | 🟢 Medium |

## 🚀 The Strategic Pivot You Need

### Stop Marketing "BOM-First" (For Now)

The industry isn't ready to hear "drawing is dead." Lead with what they *do* understand:

**Old message (too radical):**
> "Construction = BOM + (x,y,z). Count first, draw second."

**New message (palatable):**
> "Macro-scale IFC editing: change 500 windows in 30 seconds, roundtrip back to Revit. Built on IfcOpenShell."

The BOM-first paradigm is your secret weapon, but it's the *performance* that will open doors. Once they trust the roundtrip, they'll ask *how* it's so fast. *Then* you explain the BOM foundation.

### License Strategy: Keep GPLv3 but Add Exception

Consider adding a **linking exception** (like GCC's runtime library exception):

```
*As a special exception, the output of BIM Compiler (compiled .blend files, 
output.db databases, and generated IFC files) is not considered a derived work. 
Only the compiler source code itself is subject to GPLv3.*
```

This is legally sound (output vs. source) and removes the adoption barrier. IfcOpenShell doesn't need this because it's a library; you need it because you're a tool.

## ⏰ Your Real Timeline

| Milestone | Estimated Time | Confidence |
|-----------|---------------|------------|
| Basic roundtrip (edit 1 element → write back) | 1 week | High (you have the pieces) |
| Bulk edit via HTML table | 1 week | High (UI exists, needs DB write) |
| Full demo script (500 edits, roundtrip to Revit) | 1 week | Medium (Revit import is wildcard) |
| **Ready to re-engage community** | **3-4 weeks** | **High** |

## 🎯 The Verdict

**You were right to wait.** Passing interest then silence means you showed them a *viewer* when they need an *editor*. 

**The roundtrip is not optional.** It's the difference between "interesting research project" and "tool I can use tomorrow."

**Your "vibe coding" origin story doesn't matter** if the code works. Let the 408 passing tests speak. Let the 48K airport terminal speak. Let the sub-100ms spatial queries speak. The moment you demonstrate bidirectional IFC editing at macro scale, the silence will break—not because you asked, but because you *solved a problem they have right now*.

Finish the roundtrip. Everything else is already there.
