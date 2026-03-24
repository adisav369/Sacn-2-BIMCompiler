---
hide:
  - navigation
  - toc
---

# BIM Intent Compiler

## Construction is manufacturing. A building IS its Bill of Materials.


<img align="right" src="assets/images/GeneralTall.png" alt="Multi-storey building compiled from BOM — 6 disciplines colour-coded" width="240" style="margin-left: 24px; margin-bottom: 12px;">

A metadata-driven, deterministic compiler that reads BOM data and produces verified 3D building coordinates — the same way an ERP system explodes a manufacturing BOM into work orders.

Every output element traces to a library input. Nothing is invented. No AI inside. Pure arithmetic.

*Colour = discipline (STR, ARC, ELEC, FP, ACMV, SP). Every element placed by arithmetic from a Bill of Materials.*

<br clear="right"/>

---

<div class="grid cards" markdown>

-   **35 Buildings Compiled**

    ---

    34 extracted from real IFC files + 1 generative.
    19 pass all 6 mathematical gates.
    Largest: 48,428 elements (airport terminal).

    [:octicons-arrow-right-24: See the buildings](SampleHouseAnalysis.md)

-   **64 Verbs, 2,475 Products**

    ---

    BIM COBOL: TILE surfaces, ROUTE pipes, FRAME structures,
    CLUSTER repetitions. The compiler speaks construction.

    [:octicons-arrow-right-24: Read the verb grammar](BIM_COBOL.md)

-   **6 Mathematical Gates**

    ---

    Count, Volume, Digest, Tamper, Provenance, Isolation.
    Every compiled building is proven correct — not sampled, proven.

    [:octicons-arrow-right-24: Test architecture](TestArchitecture.md)

-   **ERP-Native Data Model**

    ---

    C_Order, C_OrderLine, M_Product, M_BOM — iDempiere patterns.
    A compiled building IS a manufacturing work order.

    [:octicons-arrow-right-24: Construction as ERP](ConstructionAsERP.md)

</div>

---

## How It Works

```
IFC file → extract → classify.yaml → IFCtoBOM → BOM.db → compile → output.db → gates
           (once)    (human intent)    (once)     (recipe)   (repeat)  (elements)   (proof)
```

The BOM hierarchy maps directly to the building hierarchy:

| Manufacturing | Construction | Table |
|--------------|-------------|-------|
| Product catalog | Building element (wall, door, pipe) | `M_Product` |
| Bill of Materials | Assembly recipe (floor, room, building) | `M_BOM` + `M_BOM_Line` |
| Work order | Construction project for a specific building | `C_Order` + `C_OrderLine` |
| Production operation | Verb execution (TILE, ROUTE, FRAME) | `PP_Order_Node` |
| Warehouse location | Spatial slot (room, plot) | `CO_EmptySpaceLine` |

---

## The Rosetta Stone Strategy

Three real buildings, decomposed into reference databases. The compiler reads a BOM
describing the same building and produces output. The test: does every compiled element
land at the **same position** as the reference?

| Stone | Elements | Gates | What it proves |
|-------|----------|-------|---------------|
| Sample House (SH) | 55 | ALL GREEN | Simple residential, 1 storey |
| FZK Haus (FK) | 82 | ALL GREEN | European residential |
| Duplex (DX) | 1,099 | ALL GREEN | 2-storey, 3 disciplines |
| Terminal (TE) | 48,428 | ALL GREEN | Airport, 8 disciplines, 505 products |

Once a stone passes exact sameness, its BOM grammar is **certified**. Any new building
composed from certified grammar inherits the proof.

<figure markdown="span">
  ![Terminal complex compiled in Blender — 48,428 elements](assets/images/TerminalExternal.jpeg){ width="480" }
  <figcaption>The Terminal (TE) — 48,428 elements, 505 products, 8 disciplines. Compiled from BOM, verified by 6 gates.</figcaption>
</figure>

[:octicons-arrow-right-24: Read the full strategy](TheRosettaStoneStrategy.md)

---

## We Got Eyes

The compiler can see. BIMEyes reduces every element's shape to three dimensionless
ratios — planarity, elongation, squareness. A wall *must* be planar. A column *must*
be elongated. These are mathematical facts, not heuristics.

| Tier | What it proves | Example |
|------|---------------|---------|
| **Per-element** | Each element is geometrically sane | Positive extent, finite coordinates |
| **Pairwise** | Elements relate correctly | Doors inside walls, furniture inside rooms |
| **Aggregate** | The building makes sense | Rooms have walls + floor + ceiling + door |

28 proof classes. 97% of 90,310 elements pass both shape AND position proof. No AI. No tolerance tuning. Pure arithmetic.

[:octicons-arrow-right-24: EYES specification](EYES_SRS.md)

---

## Design in Blender, Compile from BOM

The compiler powers a live GUI inside [Blender](https://www.blender.org/) via the
[Bonsai](https://bonsaibim.org/) addon. Create a building from a single click, edit
room dimensions with sliders, switch jurisdictions, save/recall design versions, and
promote a design to a construction-ready work order.

392 passing tests. The full BOM-to-output pipeline runs behind the GUI — what the user
sees IS what the compiler produces, deterministically.

<figure markdown="span">
  ![BIM Designer web UI — BOM tree, DocAction buttons, Attributes panel](assets/images/HTMLYAML.png){ width="480" }
  <figcaption>The BIM Designer web UI. BOM tree on the left, DocAction lifecycle buttons (Approve, Complete, Promote) across the top.</figcaption>
</figure>

[:octicons-arrow-right-24: Designer guide](BIM_Designer_UserGuide.md)

---

## Quick Start

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
git clone https://github.com/red1oon/bim-compiler.git
cd bim-compiler

mvn compile -q                              # Compile all modules
./scripts/run_RosettaStones.sh classify_sh.yaml   # Compile Sample House + verify gates
./scripts/run_tests.sh                      # Full test gate (392 tests)
```

[:octicons-arrow-right-24: Full getting started guide](WorkOrderGuide.md) · [:octicons-arrow-right-24: IFC onboarding runbook](IFC_ONBOARDING_RUNBOOK.md)

---

## Documentation Map

| I want to... | Start here |
|--------------|-----------|
| Understand the system | [System Contract](SystemContract.md) — the governing document |
| See what's planned | [Action Roadmap](ACTION_ROADMAP.md) — navigation hub for all specs |
| Read the master spec | [BOM-Based Compilation](BOMBasedCompilation.md) — tack, walker, gospel |
| Navigate the code | [Source Code Guide](SourceCodeGuide.md) — entry points, DAOs, patterns |
| Onboard a new IFC | [IFC Onboarding Runbook](IFC_ONBOARDING_RUNBOOK.md) — 8-step recipe |
| Understand ERP mapping | [Construction as ERP](ConstructionAsERP.md) — C_Order, three-concern |
| See the frontier | [Project Order Blueprint](ProjectOrderBlueprint.md) — exception ordering, C_Project, rule packs |

---

<div class="grid cards" markdown>

-   **Open Source**

    ---

    GPL v2 (compatible with iDempiere/Bonsai FOSS ecosystem).
    Documentation: CC BY-SA 4.0.

-   **No AI Inside**

    ---

    Every proof is deterministic. No cloud. No model.
    No magic. No surprise. Pure arithmetic.

-   **Malaysian BIM Mandate**

    ---

    From July 2025, all projects >= RM10M require BIM.
    This compiler produces compliant IFC output.

    ![Sample House floor plan](assets/images/2D_FLOOR_SAMPLEHOUSE.png){ width="280" }

-   **By Redhuan D. Oon**

    ---

    Led ADempiere (2006), paved the way for iDempiere (2010).
    Two decades of ERP BOM expertise applied to construction.

</div>
