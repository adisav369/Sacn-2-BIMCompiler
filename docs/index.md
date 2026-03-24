---
hide:
  - navigation
  - toc
---

# BIM Intent Compiler

## Construction is manufacturing. A building IS its Bill of Materials.


<figure style="float: right; margin: -12px 0 8px 20px; max-width: 360px; text-align: center;">
  <img src="assets/images/GeneralTall.png" alt="Multi-storey building compiled from BOM — 6 disciplines colour-coded" width="360">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">Compiled in Blender/Bonsai. Colour = discipline (STR, ARC, ELEC, FP, ACMV, SP).</figcaption>
</figure>

A metadata-driven, deterministic compiler that reads BOM data and produces verified 3D building coordinates — the same way an ERP system explodes a manufacturing BOM into work orders.

Every output element traces to a library input. Nothing is invented. No AI inside. Pure arithmetic. The same 9-stage pipeline compiles a 55-element house and a 48,428-element airport terminal. 64 domain verbs (TILE, ROUTE, FRAME, CLUSTER) generate geometry from rules. 2,475 products in the library. 6 mathematical gates prove every output correct — not sampled, proven.

Built on [iDempiere](https://idempiere.org/) ERP conventions, [SQLite](https://www.sqlite.org/), and the [Blender](https://www.blender.org/)/[Bonsai](https://bonsaibim.org/) open-source 3D viewport. From Kuala Lumpur, Malaysia — where BIM is mandated for all projects >= RM10M from July 2025.

<div style="clear: right;"></div>

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

## **How It Works**

```
IFC file → extract → classify.yaml → IFCtoBOM → BOM.db → compile → output.db → gates
           (once)    (human intent)    (once)     (recipe)   (repeat)  (elements)   (proof)
```

A product catalog (`M_Product`) becomes building elements. A Bill of Materials (`M_BOM`) becomes assembly recipes. A work order (`C_Order`) becomes a construction project. The same ERP tables that run a factory floor now compile a building.

---

## **The Rosetta Stone Strategy**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 480px; text-align: center;">
  <img src="assets/images/TerminalExternal.jpeg" alt="Terminal complex — 48,428 elements" width="480">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">48,428 elements. 8 disciplines. Compiled from BOM.</figcaption>
</figure>

Real IFC buildings are decomposed into reference databases. The compiler reads a BOM describing the same building and produces output. If every compiled element lands at the **same position** as the reference — same coordinates, same dimensions, same 3D space — the BOM grammar is **certified**.

35 buildings compiled. From a 55-element house to a 48,428-element airport terminal. 19 pass all 6 mathematical gates. Once a stone is certified, any new building composed from its proven grammar inherits the proof — no new reference needed.

[:octicons-arrow-right-24: Read the full strategy](TheRosettaStoneStrategy.md)

<div style="clear: right;"></div>

---

## **We Got Eyes**

The compiler can see. BIMEyes reduces every element's shape to three dimensionless ratios — planarity, elongation, squareness. A wall *must* be planar. A column *must* be elongated. A slab *must* be flat. These are mathematical facts, not heuristics.

Three verification tiers: **per-element** (each element is geometrically sane), **pairwise** (doors inside walls, furniture inside rooms), and **aggregate** (every room has walls, floor, ceiling, and a door). 28 proof classes across 90,310 elements. 97% pass both shape AND position proof. No AI. No tolerance tuning. Pure arithmetic.

[:octicons-arrow-right-24: EYES specification](EYES_SRS.md)

---

## **Design in Blender, Compile from BOM**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 570px; text-align: center;">
  <img src="assets/images/HTMLYAML.png" alt="BIM Designer web UI — BOM tree, DocAction buttons, Attributes panel" width="570">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">BIM Designer web UI. BOM tree, DocAction lifecycle buttons (Approve, Complete, Promote).</figcaption>
</figure>

The compiler powers a live GUI inside [Blender](https://www.blender.org/) via the [Bonsai](https://bonsaibim.org/) addon. Create a building from a single click, edit room dimensions with sliders, switch jurisdictions, save/recall design versions, and promote a design to a construction-ready work order.

The HTML web UI (port 9878) provides 10 tabs: spatial views, geometry inspection, validation, BOM tree, and colour-coded discipline breakdown. DocAction lifecycle buttons (Draft → Approve → Complete → Promote) drive the design through ERP-standard approval stages. Bidirectional sync: browser pushes commands to Bonsai, Bonsai renders the compiled output live.

392 passing tests. What the user sees IS what the compiler produces, deterministically.

[:octicons-arrow-right-24: Designer guide](BIM_Designer_UserGuide.md)

<div style="clear: right;"></div>

---

## **Quick Start**

```bash
# Prerequisites: Java 17+, Maven 3.8+, SQLite3
git clone https://github.com/red1oon/BIMCompiler.git
cd bim-compiler

mvn compile -q                              # Compile all modules
./scripts/run_RosettaStones.sh classify_sh.yaml   # Compile Sample House + verify gates
./scripts/run_tests.sh                      # Full test gate (392 tests)
```

[:octicons-arrow-right-24: Full getting started guide](WorkOrderGuide.md) · [:octicons-arrow-right-24: IFC onboarding runbook](IFC_ONBOARDING_RUNBOOK.md)

---

## **Documentation Map**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 560px; text-align: center;">
  <img src="assets/images/2D_FLOOR_SAMPLEHOUSE.png" alt="Sample House 2D floor plan — Pelan Lantai" width="560">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">Sample House floor plan (Pelan Lantai) — generated from 1D BOM → 3D compilation → 2D drawing. The round-trip proof.</figcaption>
</figure>

| I want to... | Start here |
|--------------|-----------|
| Understand the system | [System Contract](SystemContract.md) |
| Read the master spec | [BOM-Based Compilation](BOMBasedCompilation.md) |
| Navigate the code | [Source Code Guide](SourceCodeGuide.md) |
| Onboard a new IFC | [IFC Onboarding Runbook](IFC_ONBOARDING_RUNBOOK.md) |
| See the frontier | [Project Order Blueprint](ProjectOrderBlueprint.md) |

<div style="clear: right;"></div>

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

-   **By Redhuan D. Oon**

    ---

    Led ADempiere (2006), paved the way for iDempiere (2010).
    Two decades of ERP BOM expertise applied to construction.

</div>
