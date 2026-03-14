# Strategic Industry Positioning — BIM Intent Compiler

*Where this project sits in the construction technology landscape, and why the gap
between visual editors and a Spatial MRP compiler is an opportunity, not a deficit.*

---

## The Landscape: What Exists Today

The construction technology market has three tiers of tools. Each solves a real
problem — but none solves the problem this project addresses.

### Tier 1: The Incumbents (Geometry Authoring)

| Tool | What It Does | Approach |
|------|-------------|----------|
| **Autodesk Revit** | Full BIM authoring — parametric walls, MEP routing, structural framing | Proprietary, desktop, family-based. Industry standard. |
| **ArchiCAD** (Graphisoft) | Architectural BIM authoring with strong documentation | Proprietary, desktop. Strong in Europe/Asia. |
| **Tekla Structures** (Trimble) | Structural steel/concrete detailing at LOD 400+ | Proprietary, desktop. Fabrication-grade. |

These tools are where buildings are *designed*. They produce IFC files. They have
vast component libraries, parametric families, and polished GUIs. A Revit user drags
a door from a library, drops it on a wall, and it cuts the opening automatically.

**What they cannot do:** Tell you what to *procure*. Revit can model 51,088 elements
but cannot produce a bill of materials that maps to a purchase order. The QS opens the
model in Navisworks, manually counts elements, and exports to Excel. For a Terminal-
sized building, this takes weeks and introduces human error at every step.

### Tier 2: The Visual Newcomers (Design-to-3D)

| Tool | What It Does | Funding | Key Feature |
|------|-------------|---------|-------------|
| [**Snaptrude**](https://www.snaptrude.com/) | Browser-based 3D building design — sketch to BIM | $10M+ Series A | Real-time 3D, instant floor plans from sketches |
| [**TestFit**](https://www.testfit.io/) | Generative site planning — AI tests 1000s of layouts | $20M Series A (2022) | AI-driven feasibility, instant pro forma |
| [**Arkio**](https://www.arkio.is/) | VR/AR collaborative design — sketch in 3D space | Seed stage | Immersive design, Revit round-trip |
| [**Hypar**](https://hypar.io/) | Cloud generative design — parametric building configurator | Acquired by Autodesk (2024) | Function-based building generation |
| [**Modelo**](https://www.modelo.io/) | Web-based 3D model viewer + collaboration | Series A | Browser rendering, stakeholder review |

These tools are impressive. Snaptrude lets you draw a floor plan and watch a 3D
building rise in real-time. TestFit generates site layouts optimised for unit count
and cost. Arkio lets you sculpt buildings in VR. They have beautiful GUIs, smooth
animations, and component libraries you can drag-and-drop.

**What they cannot do:** Prove the model is correct. Snaptrude generates geometry
from sketches — but it cannot verify that the generated model matches the design
intent, that every element traces to a product in a catalog, or that the spatial
arrangement satisfies building regulations. These tools operate in the *design
exploration* phase — they help you decide *what to build*. They say nothing about
*how to build it*.

### Tier 3: The Open Source Ecosystem (IFC-Native)

| Tool | What It Does | Approach |
|------|-------------|----------|
| [**Bonsai/BlenderBIM**](https://bonsaibim.org/) | IFC-native BIM authoring inside Blender | FOSS, Python/C++. Direct IFC manipulation. |
| [**FreeCAD BIM**](https://wiki.freecadweb.org/BIM_Workbench) | BIM workbench in FreeCAD — parametric walls, IFC export | FOSS, C++/Python. Merged into FreeCAD 1.0. |
| [**IfcOpenShell**](https://ifcopenshell.org/) | IFC parsing/generation library — the Swiss Army knife | FOSS, Python/C++. Used by Bonsai internally. |
| [**IFC.js / ThatOpenCompany**](https://thatopen.com/) | Web-based IFC viewer/editor components | FOSS, TypeScript. Fragment-based rendering. |
| [**xBIM**](https://docs.xbim.net/) | .NET IFC toolkit — geometry engine + viewer | FOSS, C#. Server-side IFC processing. |

Bonsai is the closest tool philosophically — it works directly with IFC data, not
geometry-that-gets-converted-later. FreeCAD BIM merged its Arch workbench in v1.0
and offers parametric BIM objects with IFC export. IfcOpenShell powers much of the
open-source BIM ecosystem.

**What they cannot do:** Compile. These tools can *author* IFC models and *parse*
them. They cannot take 51,088 elements, decompose them into a bill of materials,
assign them to storeys and disciplines, compress them via formula verbs (TILE,
ROUTE, FRAME), and produce a verified construction order with spatial proof. They
are editors and libraries, not compilers.

---

## Where BIM Intent Compiler Sits

None of the tools above do what this project does. The gap is categorical:

```
                    DESIGN PHASE              CONSTRUCTION PHASE
                    ────────────              ──────────────────
Tier 1 (Revit)     [████████████]             [                ]
Tier 2 (Snaptrude) [████████]                 [                ]
Tier 3 (Bonsai)    [██████████]               [                ]

BIM Compiler       [    ]                      [████████████████]
                    ↑                          ↑
                    We consume IFC from here    We produce this
```

The entire industry is focused on **design-time** tooling — helping architects
create models. Nobody is focused on **compile-time** tooling — taking a finished
model and producing construction-grade output that maps to ERP procurement.

### The Specific Gap

| Capability | Revit | Snaptrude | Bonsai | FreeCAD | **BIM Compiler** |
|-----------|-------|-----------|--------|---------|-----------------|
| 3D visual editing | Yes | Yes | Yes | Yes | No |
| Component library (drag-drop) | 10K+ families | Cloud library | IFC objects | Parts library | N/A (consumes, not creates) |
| IFC import/export | Yes | Partial | Native | Yes | Import only (extract) |
| BOM generation | Manual QTO | No | Basic QTO | No | **Automated, factorised** |
| Formula compression | No | No | No | No | **TILE/ROUTE/FRAME (73x)** |
| ERP-native output | No | No | No | No | **C_Order + C_OrderLine** |
| Spatial proof (digest) | No | No | No | No | **G1-G6 Rosetta Stone** |
| Regulation validation | Clash detection | No | No | No | **Val_Rule (AD tables)** |
| Multi-discipline BOM | No | No | No | No | **8 disciplines, storey x disc grid** |
| Compilation pipeline | No | No | No | No | **9 stages, 63 verbs** |

### What This Means

The visual editors (Snaptrude, Arkio, TestFit) are solving the **upstream** problem:
"Help me design a building quickly." They compete with Revit on ease-of-use.

We are solving the **downstream** problem: "Given a finished design (IFC), produce
a verified construction order that maps to procurement, scheduling, and cost." We
don't compete with Revit or Snaptrude. We consume their output.

This is the same relationship as between a CAD tool and a CAM compiler in
manufacturing. SolidWorks designs the part. The G-code compiler produces the
machine instructions. Nobody expects SolidWorks to run the CNC machine, and nobody
expects the G-code compiler to have a drag-and-drop GUI.

---

## The "Spatial MRP" Position

The BIM Compiler's unique position is best understood as **Spatial MRP** — Material
Requirements Planning extended with spatial intelligence:

| | Traditional MRP | Visual BIM Tools | **Spatial MRP (BIM Compiler)** |
|--|----------------|-----------------|-------------------------------|
| **Answers** | What materials, when? | What does it look like? | What materials, **where**, how connected? |
| **Input** | BOM + demand forecast | Architect's sketch | IFC extraction (51K elements) |
| **Output** | Purchase orders | 3D model + renders | C_Order + C_OrderLine + PP_Order_Node |
| **Verification** | MRP explosion audit | Visual review | Rosetta Stone gate (G1-G6 mathematical proof) |
| **Compression** | BOM explosion | None | 51K elements → 700 BOM lines (73x) |
| **User** | Manufacturing planner | Architect/designer | QS, contractor, project manager |

The visual tools help architects *imagine* buildings. The BIM Compiler helps
contractors *build* them.

---

## Why the GUI Gap Is Actually a Moat

Stakeholders may look at Snaptrude's animated 3D editor and ask: "Why don't we
have that?" The answer is architectural:

**1. We operate on different data.** Snaptrude manipulates *geometry* (vertices,
faces, extrusions). We manipulate *BOMs* (products, assemblies, quantities). A
BOM is not a visual object — it's a recipe. You don't drag-and-drop a recipe;
you compile it.

**2. Our output is transactional, not visual.** A C_OrderLine with
`M_Product=PIPE_CW_50MM, qty=12` is a procurement record. It goes into iDempiere,
generates purchase orders, triggers scheduling. This is ERP data, not render data.

**3. Our verification is mathematical, not visual.** The Rosetta Stone gate
computes SHA256 spatial digests and proves element-by-element coordinate match.
No visual tool can do this — you can't "see" that 48,428 elements all have correct
coordinates. You can only prove it algebraically.

**4. Formula compression is invisible.** TILE SURFACE compresses 33,324 roof plates
into 20 formulas. This is a 1,666x compression that happens in the BOM, not in the
viewport. There's nothing to animate — the power is in the data reduction.

**When we do need a GUI** (Phase G: Bonsai GUI Editor), it will be a BOM editor —
tree views, storey/discipline grids, verb parameter forms — not a 3D viewport. The
3D viewport already exists (Bonsai/Blender). Our GUI will plug into it, not replace it.

---

## Competitive Positioning Summary

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
│         │             │             │                   │
│         └─────────────┴─────────────┘                   │
│                       │                                 │
│                    IFC FILE                              │
│                       │                                 │
│  ┌────────────────────▼──────────────────────────────┐  │
│  │         BIM INTENT COMPILER (downstream)          │  │
│  │                                                    │  │
│  │  IFC → Extract → Classify → BOM → Compile → Prove │  │
│  │                                                    │  │
│  │  48,428 elements → 700 BOM lines → C_Order        │  │
│  │  63 verbs, 196 witnesses, G1-G6 proven            │  │
│  │  Spatial MRP: WHAT + WHERE + HOW                  │  │
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

**The BIM Intent Compiler occupies a position that no other tool — commercial or
open source — currently fills.** It is the bridge between the design world (IFC
models) and the construction world (ERP procurement). The visual editors make
buildings easy to *imagine*. This compiler makes them possible to *verify, procure,
and build*.

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
*[`TerminalAnalysis.md`](TerminalAnalysis.md) — forensics + ERP architecture*
*[`ConstructionAsERPII.txt`](ConstructionAsERPII.txt) — Spatial MRP framing*
*[`terminal_erd.html`](terminal_erd.html) — interactive ERD*
*[`ConstructionAsERP.md`](ConstructionAsERP.md) — full ERP model documentation*
