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

**Numeric scoring (0-3): 0=absent, 1=partial/plugin, 2=built-in, 3=native/core**

| Tool | IFC | 3D | 4D | 5D | 6D | 7D | BOM | ERP | Proof | **Total /27** |
|------|-----|----|----|----|----|----|----|-----|-------|--------------|
| Revit | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | **9** |
| ArchiCAD | 2 | 3 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | **9** |
| Snaptrude | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| TestFit | 0 | 2 | 0 | 2 | 0 | 0 | 0 | 0 | 0 | **4** |
| Arkio | 0 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **3** |
| Bonsai | 3 | 3 | 0 | 1 | 0 | 0 | 0 | 0 | 0 | **7** |
| FreeCAD | 1 | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | **4** |
| **BIM Compiler** | **3** | 1\* | **2** | **3** | 1\* | 1\* | **3** | **3** | **3** | **20** |

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
Phase B (now):   BIM COBOL DSL → classify_te.yaml → compiler → C_Order
Phase G (later): GUI Editor → BIM COBOL DSL → compiler → C_Order
                  ↑
                  LHF: tree views + forms over existing ERP BOM model
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

## Current Progress (2026-03-15)

| Milestone | Status |
|-----------|--------|
| 3 Rosetta Stone buildings registered | SH (55), DX (1099), TE (48,428) |
| SH + DX fully proven (G1-G6 GREEN) | 6/6 gates PASS |
| Terminal TE-1 storey normalisation | DONE (7 storeys, 8 disciplines) |
| ERP architecture designed | Discipline hierarchy, verb→AttributeSet, Val_Rule |
| Formula compression designed | TILE (65%), ROUTE (18%), FRAME (3%) = 86% coverage |
| Interactive ERD | `docs/terminal_erd.html` |
| 63 BIM COBOL verbs, 196 witnesses | Pipeline: 9 stages |

---

*Cross-references:*
*[`BIM_Designer.md`](BIM_Designer.md) — GUI architecture, ASI overrides, container rules, pattern multiplication*
*[`TerminalAnalysis.md`](TerminalAnalysis.md) — forensics + ERP architecture*
*[`ConstructionAsERPII.txt`](ConstructionAsERPII.txt) — Spatial MRP framing*
*[`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) — bridge/road/rail domain mapping*
*[`terminal_erd.html`](terminal_erd.html) — interactive ERD*
*[`ConstructionAsERP.md`](ConstructionAsERP.md) — full ERP model documentation*
