# Strategic Industry Positioning — BIM Intent Compiler

*Why the gap between visual design editors and a Spatial MRP compiler is an
opportunity, not a deficit.*

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
(CAGR 35.4%). All require a BIM-to-ERP bridge that nobody currently provides.

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

BIM Compiler       [██*]                       [████████████████]
                    ↑                          ↑
                    *GUI = LHF (Phase G)        Spatial MRP output
```

This is the same relationship as CAD vs. CAM in manufacturing: SolidWorks
designs the part, the G-code compiler produces machine instructions. Nobody
expects SolidWorks to run the CNC machine.

### Spatial MRP — Our Unique Position

**Spatial MRP** = Material Requirements Planning extended with spatial
intelligence:

| | Traditional MRP | Visual BIM Tools | **Spatial MRP (BIM Compiler)** |
|--|----------------|-----------------|-------------------------------|
| **Answers** | What materials, when? | What does it look like? | What materials, **where**, how connected? |
| **Input** | BOM + demand forecast | Architect's sketch | IFC extraction (51K elements) |
| **Output** | Purchase orders | 3D model + renders | C_Order + C_OrderLine + PP_Order_Node |
| **Verification** | MRP explosion audit | Visual review | Rosetta Stone gate (G1-G6 proof) |
| **Compression** | BOM explosion | None | 51K elements → 700 BOM lines (73x) |

Visual tools help architects *imagine* buildings. The BIM Compiler helps
contractors *build* them.

---

## IFC/BIM Compliance Scorecard

IFC compliance is the prerequisite for serious construction deployment. Each BIM
dimension compounds difficulty for tools not built on structured data.

**Important context:** We already built and shipped the **IfcOpenShell Federation
addon** for Bonsai — a plugin that extracts multi-discipline IFC models into a
FederateModel spatial database (SQLite + R-tree spatial index), achieving 93%
memory reduction on a 93K-element terminal project. On top of that database, we
delivered working PoCs for 4D (construction schedule + Blender animation), 5D
(automated BOQ with Excel export and CIDB pricing), 7D (digital twin asset
management with maintenance scheduling and IoT sensors), rebar generation, NLP
query against building data, and discipline color schema — each built in a
single Claude Code session. These are hardcoded Python scripts: they prove the
*capabilities* work. What the BIM Intent Compiler adds is the **production
foundation** — a typed verb compiler, mathematical proof gate, BOM
factorisation, and ERP-native tables — that makes these capabilities structural,
repeatable, and scalable to any building type.

| Capability | Revit | ArchiCAD | Snaptrude | TestFit | Arkio | Bonsai | FreeCAD | **BIM Compiler** |
|-----------|-------|----------|-----------|---------|-------|--------|---------|-----------------|
| **IFC native** | Export | Export | Export | No | No | **Native** | Export | **Native (extract)** |
| **openBIM (ISO 19650)** | Partial | Yes | Partial | No | No | **Yes** | Partial | **Yes** |
| **3D Geometry** | Full | Full | Full | Site only | Full | Full | Full | Via Bonsai/Blender |
| **4D Scheduling** | Plugin | Plugin | No | No | No | No | No | **PP_Order_Node (verb seq.)** |
| **5D Cost/QTO** | Manual | Manual | No | Pro forma | No | Basic | No | **Automated BOM → C_OrderLine** |
| **6D Sustainability** | Plugin | Plugin | No | No | No | No | No | M_Product attrs (future) |
| **7D Facility Mgmt** | Plugin | Plugin | No | No | No | No | No | M_Product lifecycle (future) |
| **BOM factorisation** | No | No | No | No | No | No | No | **73x compression (51K→700)** |
| **ERP-native output** | No | No | No | No | No | No | No | **C_Order + iDempiere tables** |
| **Spatial proof** | No | No | No | No | No | No | No | **G1-G6 Rosetta Stone gate** |
| **Inference engine** | No | No | No | No | No | No | No | **Dependency DAG + proof tree** |
| **Product browser + fit** | No | No | Partial | No | No | No | No | **BOM Chooser + AABB fit check** |

**Numeric scoring (0-3): 0=absent, 1=partial/plugin, 2=built-in, 3=native/core**

| Tool | IFC | 3D | 4D | 5D | 6D | 7D | BOM | ERP | Proof | Inference | **Total /30** |
|------|-----|----|----|----|----|----|----|-----|-------|-----------|--------------|
| Revit | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | **9** |
| ArchiCAD | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | **9** |
| Snaptrude | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| TestFit | 0 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| Arkio | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **3** |
| Bonsai | 3 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | 0 | **7** |
| FreeCAD | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| **BIM Compiler** | **3** | 1\* | **2** | **3** | 1\* | 1\* | **3** | **3** | **3** | **3** | **23** |

*\* = low-hanging fruit, already proven in PoC form.* 3D via Bonsai/Blender
viewport (Federation addon ships progressive 3-stage loading for 50K+ elements).
6D/7D already working as hardcoded Bonsai addons (carbon pipeline, asset
management, maintenance scheduling). Component library LOD creation works via
`Mesh2Library.txt` pipeline. Each starred item needs only re-grounding on the
ERP framework — the hard part (proving the capability) is done.

---

## Three Moats

**1. IFC compliance is hard to add retroactively.**
The visual newcomers started with geometry, not IFC. Adding real IFC compliance
— native data model alignment, not just export — requires rearchitecting their
core. [Most tools treat IFC as a file format](https://bimcorner.com/the-ifc-confusion-why-so-many-still-dont-get-openbim-and-how-to-fix-it/),
not a data model. We treat it as structured input to a database pipeline.

**2. DB/ERP integration is a bigger moat than GUI.**
Converting IFC to a relational database with ERP semantics (M_Product, M_BOM,
C_Order) requires deep manufacturing domain knowledge. A startup can hire UI
designers to build a drag-and-drop editor in months. Building a Spatial MRP
engine that maps to iDempiere's manufacturing model requires understanding both
BIM and ERP — a rare combination. The Java DAO layer, BIM COBOL verb grammar,
BOM dimension model, and RosettaStone proof strategy represent years of domain
convergence that cannot be replicated by visual-first tools.

**3. LLM-assisted development favours our architecture.**
LLMs excel at SQL, Python, and structured data — the core of our DB-first
approach. GPU-based 3D rendering (the visual tools' core) is harder for LLMs to
generate. We have concrete proof: our IfcOpenShell Federation addon shipped a
FederateModel spatial DB (93% memory reduction on 93K elements), then 4D
scheduling, 5D BOQ, 7D asset management, rebar generation, NLP query, and
color schema — each built in a single Claude Code session. The entire
Federation addon, with all its features, was developed with LLM assistance at a
pace that would take a conventional team months. This same velocity now applies
to the ERP compiler framework, where each new verb, witness, or BOM dimension
is a bounded task that Claude Code completes in one session (see
[`CONCEPTUAL BLUEPRINT`](CONCEPTUAL%20BLUEPRINT.txt) for layer architecture,
[`BeyondVerbs`](BeyondVerbs.txt) for the roadmap beyond the current verb set).

**4. Symbolic inference over relational data is unique.**
The Inference Engine (BIM_Designer_SRS §14) evaluates validation rules in
dependency order using Kahn's topological sort, produces proof trees with
AD_Val_Rule citations, and SKIPs downstream rules when upstream premises fail.
This is Datalog-style deduction over the 5-database schema — not AI, not
heuristics, but deterministic symbolic reasoning. No visual BIM tool has a
formal proof chain from design decision to regulation citation. The Approve
gate requires all-rules-pass before Promote — a governance pattern borrowed
from ERP document workflows (iDempiere DocAction) that visual tools cannot
replicate without rebuilding their validation from scratch.

**The asymmetry:** adding a GUI to our DB/ERP foundation takes weeks. Adding
DB/ERP depth to their GPU-first foundation takes years. Adding *proven
capabilities* (4D-7D) from our PoC addons to a typed ERP framework takes days.

---

## GUI: Low-Hanging Fruit, Off Critical Path

The PoC must prove the hard parts: BOM factorisation (73x), Spatial MRP, G1-G6
mathematical proof, formula compression (TILE/ROUTE/FRAME). A GUI proves none
of these.

When we need a GUI, the ERP BOM framework is itself a **generic construction
designer for any building type.** A GUI on this foundation is a tree editor +
form editor + Bonsai 3D viewport — standard UI work that LLMs generate easily.

```
Phase B (done):  BIM COBOL DSL → classify_te.yaml → compiler → C_Order
Phase G (now):   GUI Editor → BOM Chooser → snap → save → compile → C_Order
                  ↑
                  20 wire protocol actions, 87/87 tests GREEN
                  Works for ANY building type (RE, CO, IN) — generic
```

### The Designer Architecture (Phase G)

The GUI design is fully specified in [`BIM_Designer.md`](BIM_Designer.md). Three
mechanisms make the Bonsai addon a **parametric construction editor**, not just
a viewer:

**1. AttributeSetInstance overrides (§8)** — When a user stretches a wall or
resizes a room in Bonsai, the change is captured as an `M_AttributeSetInstance`
on the `C_OrderLine`. The catalog product stays generic; the ASI captures the
user's specific dimensions. The compiler blends catalog geometry with ASI
overrides into output.db. This is the iDempiere product-variant pattern applied
to spatial parameters.

**2. Container constraint rules (§9)** — `AD_Val_Rule`-pattern validation
ensures a child never exceeds its parent container. Stretch a room beyond the
floor → the compiler blocks it or offers to extend the floor. The constraint
cascades down the BOM tree: building → floor → room → furniture. Same rule at
every level — data-driven, no code change per building type.

**3. Pattern multiplication (§10)** — The user declares "a window every 2.5m"
or "a beam every 4m" and the compiler generates the instances. This works across
domains: windows along walls (building), piers along deck (bridge), sleepers
along track (rail), lights along kerb (road). The spacing rule is metadata in
`ad_pattern_rule`; the compiler multiplies at compile time. Resize the parent →
pattern recalculates automatically.

**The compound interaction:** ASI resize (§8) → container validate (§9) →
pattern regenerate (§10) → one recompile → correct output. Three rules, zero
manual adjustment. This is what no visual editor offers: **the model re-proves
itself after every edit.**

---

## Construction Technology Stack

```
┌─────────────────────────────────────────────────────────┐
│              CONSTRUCTION TECHNOLOGY STACK               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DESIGN EXPLORATION (upstream)                          │
│  ┌─────────────┐ ┌──────────┐ ┌───────┐ ┌──────────┐  │
│  │  Snaptrude  │ │  TestFit │ │ Arkio │ │  Hypar   │  │
│  │  (sketch→3D)│ │ (AI site)│ │ (VR)  │ │ (gen.des)│  │
│  └─────────────┘ └──────────┘ └───────┘ └──────────┘  │
│                                                         │
│  BIM AUTHORING (midstream)                              │
│  ┌─────────────┐ ┌──────────┐ ┌───────────┐           │
│  │    Revit    │ │ ArchiCAD │ │  Bonsai   │           │
│  │ (parametric)│ │ (arch.)  │ │ (IFC-nat.)│           │
│  └──────┬──────┘ └────┬─────┘ └─────┬─────┘           │
│         └─────────────┴─────────────┘                   │
│                       │                                 │
│                    IFC FILE                              │
│                       │                                 │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │         BIM INTENT COMPILER (downstream)          │  │
│  │                                                    │  │
│  │  IFC → Extract → Classify → BOM → Compile → Prove │  │
│  │  48,428 elements → 700 BOM lines → C_Order        │  │
│  │  63 verbs · 196 witnesses · G1-G6 proven           │  │
│  └────────────────────┬──────────────────────────────┘  │
│                       │                                 │
│                   ERP / PROCUREMENT                     │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │              iDempiere ERP                        │  │
│  │  C_Order → C_OrderLine → Purchase Orders          │  │
│  │  M_Product → M_AttributeSetInstance               │  │
│  │  PP_Order_Node → Manufacturing Instructions       │  │
│  └───────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

The BIM Intent Compiler occupies a position no other tool — commercial or open
source — currently fills. It bridges the design world (IFC models) and the
construction world (ERP procurement). The visual editors make buildings easy to
*imagine*. This compiler makes them possible to *verify, procure, and build*.

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
    work_output.db (50 MB, always regenerable, disposable)
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
| **BIM Intent Compiler** | YAML + Order + ASI | 9-stage pipeline | output.db | component_library.db |

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
| **Save** | Frequent | work_output.db (OrderLine + ASI) | Low — just persist |
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

## Current Progress (2026-03-19)

| Milestone | Status |
|-----------|--------|
| 3 Rosetta Stone buildings registered | SH (55), DX (1099), TE (48,428) |
| SH + DX + TE fully proven (G1-G6) | SH 7/8, DX 7/8, TE 7/8 (pre-existing G2/G5 debt) |
| ERP architecture designed | Discipline hierarchy, verb→AttributeSet, Val_Rule |
| Formula compression | TILE (65%), ROUTE (18%), FRAME (3%) = 86% coverage |
| 63 BIM COBOL verbs, 196 witnesses | Pipeline: 9 stages, seal v17 (74 files INTACT) |
| **BIM Designer Phase G (G-1..G-5 DONE)** | Design Mode, BBox renderer, Save/Recall, BOM Chooser |
| **20 wire protocol actions** | compile, createNew, snap, save, recall, promote, browseItems, placeItem, addRoom, removeRoom, addStorey, setJurisdiction, approve, compareVariants, listBuildings, listCategories, listVariants, compileIncremental, verb, toggleMode |
| **PlacementValidator + Inference Engine** | 32 rules, 6 jurisdictions, dependency DAG + proof tree |
| **BOM Chooser** | SQL LIKE search, AABB fit check, category counts, pagination |
| **Layout editing** | Add/remove room, add storey, dimension sliders, click-to-fix |
| **Ambient compliance** | Live status strip, jurisdiction switch, auto-fix |
| **Approve gate** | InferenceEngine validation + dangle detection + proof trace |
| Semantic Source of Truth paradigm | YAML + Order + ASI = 10 KB building definition |
| **87/87 Designer tests GREEN** | 10 test classes, 30 witness families |

---

*Cross-references:*
*[`BIM_Designer.md`](BIM_Designer.md) — GUI architecture (§17 Design Mode, §18 UX Strategy)*
*[`BIM_Designer_SRS.md`](BIM_Designer_SRS.md) — UX requirements, user journeys, Inference Engine (§14)*
*[`TerminalAnalysis.md`](TerminalAnalysis.md) — forensics + ERP architecture*
*[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) — bridge/road/rail domain mapping*
*[`ConstructionAsERP.md`](ConstructionAsERP.md) — full ERP model documentation*
*[`bim_designer_erd.html`](bim_designer_erd.html) — interactive ERD (4 tabs)*
*[`BIM_Designer_UserGuide.md`](BIM_Designer_UserGuide.md) — setup + usage guide (v0.4)*
