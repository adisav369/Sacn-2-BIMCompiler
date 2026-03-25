---
hide:
  - navigation
  - toc
---

# BIM Intent Compiler

## Construction is manufacturing. A building IS its Bill of Materials.

A metadata-driven, deterministic compiler that reads BOM data and produces verified 3D building coordinates — the same way an [ERP system](MANIFESTO.md) explodes a manufacturing BOM into work orders, that can then be further edited in [Bonsai](https://bonsaibim.org/) (Blender BIM).

The [Rosetta Stone strategy](TheRosettaStoneStrategy.md) takes 35 real buildings and recompiles them after their [BOMs](BOMBasedCompilation.md) (Bill of Materials), converted from their flat [IFC](https://www.buildingsmart.org/standards/bsi-standards/industry-foundation-classes/) format — if every element lands at the same coordinates as the original, the grammar is certified. Every output element traces to a library input. Nothing is invented. No AI inside. Pure arithmetic. The same 9-stage pipeline compiles a 55-element house and a 48,428-element airport terminal. [6 mathematical gates](TestArchitecture.md) prove every output correct — not sampled, proven. 

Thereupon, we test it on generic [Construction Orders](ProjectOrderBlueprint.md) to validate the [vocabulary](BIM_COBOL.md) — 64 domain verbs (TILE, ROUTE, FRAME, CLUSTER) generating geometry from [2,475 products](DATA_MODEL.md) in the library.

<div style="max-width: 620px; margin: 32px auto; padding: 24px 40px; background: linear-gradient(to right, #fff8e1, #fffde7, #fff8e1); border-top: 4px solid #ffc107; border-bottom: 4px solid #ffc107; text-align: center;">
<span style="font-size: 1.35em; line-height: 1.7; color: #263238; letter-spacing: 0.3px;">"What <b>AUTODESK</b>, <b>PRIMAVERA</b> and <b>SAP</b> should have done<br>together long ago — <i>but I couldn't wait.</i>"</span>
<br><span style="font-size: 0.8em; letter-spacing: 1.5px; text-transform: uppercase; color: #90a4ae; margin-top: 12px; display: inline-block;">Redhuan D. Oon · ADempiere 2006 · iDempiere 2010 · BIM Compiler 2025</span>
</div>

Built on [iDempiere](https://idempiere.org/) ERP conventions, [SQLite](https://www.sqlite.org/), and the [Blender](https://www.blender.org/)/[Bonsai](https://bonsaibim.org/) open-source 3D viewport - upgraded with the [FederatedModel Spatial Database](https://github.com/red1oon/IfcOpenShell/tree/feature/IFC4_DB/src/bonsai/bonsai/bim/module/federation) and [PDF Terrain](PDF_TERRAIN.md) survey-to-3D — querying geometry that IFC files can only serialize. From Kuala Lumpur, Malaysia — where BIM is [mandated](StrategicIndustryPositioning.md) for all projects >= RM10M from July 2025. It is the Creator, [Redhuan D. Oon](mailto:red1org@gmail.com)'s gift to the world.

<div style="clear: right;"></div>

---

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 280px; text-align: center;">
  <img src="assets/images/GeneralTall.png" alt="Multi-storey building compiled from BOM — 6 disciplines colour-coded" width="280">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">Compiled in Blender/Bonsai. Colour = discipline (STR, ARC, ELEC, FP, ACMV, SP).</figcaption>
</figure>

<div class="grid cards" markdown>

-   **35 Buildings Compiled**

    ---

    34 extracted from real [IFC](https://www.buildingsmart.org/standards/bsi-standards/industry-foundation-classes/) files + 1 generative.
    19 pass all [6 mathematical gates](TestArchitecture.md).
    Largest: [48,428 elements](TerminalAnalysis.md) (airport terminal).

    [:octicons-arrow-right-24: See the buildings](SampleHouseAnalysis.md)

-   **75 Verbs, 2,475 Products**

    ---

    [BIM COBOL](BIM_COBOL.md): TILE surfaces, ROUTE pipes, FRAME structures,
    CLUSTER repetitions. The compiler speaks construction.

    [:octicons-arrow-right-24: Read the verb grammar](BIM_COBOL.md)

-   **6 Mathematical Gates**

    ---

    Count, Volume, Digest, Tamper, Provenance, Isolation.
    Every compiled building is proven correct — not sampled, proven.

    [:octicons-arrow-right-24: Test architecture](TestArchitecture.md)

-   **ERP-Native Data Model**

    ---

    [C_Order, C_OrderLine, M_Product, M_BOM](DATA_MODEL.md) — [iDempiere](https://idempiere.org/) patterns.
    A compiled building IS a manufacturing work order.

    [:octicons-arrow-right-24: Read the ERP world view](MANIFESTO.md) · [:octicons-arrow-right-24: Data model](DATA_MODEL.md)

</div>

---

## **How It Works**

```
IFC file → extract → classify.yaml → IFCtoBOM → BOM.db → compile → output.db → gates
           (once)    (human intent)    (once)     (recipe)   (repeat)  (elements)   (proof)
```

A product catalog ([M_Product](DATA_MODEL.md)) becomes building elements. A Bill of Materials ([M_BOM](BOMBasedCompilation.md)) becomes assembly recipes. A work order ([C_Order](ProjectOrderBlueprint.md)) becomes a construction project. The same [ERP](MANIFESTO.md) tables that run a factory floor now compile a building.

---

## **The Rosetta Stone Strategy**

AI cannot see spatial geometry — it has no native understanding of where a wall ends, how a slab sits, or whether two elements collide. The [Rosetta Stone strategy](TheRosettaStoneStrategy.md) sidesteps AI entirely: real buildings become the ground truth, and every compiled output is verified against that truth with pure arithmetic.

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

The compiler can see. [BIMEyes](EYES_SRS.md) reduces every element's shape to three dimensionless ratios — planarity, elongation, squareness. A wall *must* be planar. A column *must* be elongated. A slab *must* be flat. These are mathematical facts, not heuristics.

Three verification tiers: **per-element** (each element is geometrically sane), **pairwise** (doors inside walls, furniture inside rooms), and **aggregate** (every room has walls, floor, ceiling, and a door). 28 proof classes across 90,310 elements. 97% pass both shape AND position proof. No AI. No tolerance tuning. Pure arithmetic.

[:octicons-arrow-right-24: EYES specification](EYES_SRS.md)

---

## **Design in Blender, Compile from BOM**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 570px; text-align: center;">
  <img src="assets/images/HTMLYAML.png" alt="BIM Designer web UI — BOM tree, DocAction buttons, Attributes panel" width="570">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">BIM Designer web UI. BOM tree, DocAction lifecycle buttons (Approve, Complete, Promote).</figcaption>
</figure>

The compiler powers a live GUI inside [Blender](https://www.blender.org/) via the [Bonsai](https://bonsaibim.org/) addon and the [BIM Designer](BIM_Designer_UserGuide.md). Create a building from a single click, edit room dimensions with sliders, switch jurisdictions, save/recall design versions, and promote a design to a construction-ready work order.

The HTML web UI (port 9878) provides 10 tabs: spatial views, geometry inspection, validation, BOM tree, and colour-coded discipline breakdown. [DocAction](DocAction_SRS.md) lifecycle buttons (Draft → Approve → Complete → Promote) drive the design through ERP-standard approval stages. Bidirectional sync: browser pushes commands to Bonsai, Bonsai renders the compiled output live.

408+ passing tests. What the user sees IS what the compiler produces, deterministically.

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
./scripts/run_tests.sh                      # Full test gate (408+ tests)
```

[:octicons-arrow-right-24: Full getting started guide](WorkOrderGuide.md) · [:octicons-arrow-right-24: IFC onboarding runbook](IFC_ONBOARDING_RUNBOOK.md)

---

## **Documentation Map**

<figure style="float: right; margin: -8px 0 8px 20px; max-width: 560px; text-align: center;">
  <img src="assets/images/2D_FLOOR_SAMPLEHOUSE.png" alt="Sample House 2D floor plan — Pelan Lantai" width="560">
  <figcaption style="font-size: 0.75em; color: #666; margin-top: 4px;">Sample House floor plan (Pelan Lantai) — generated from 1D BOM → 3D compilation → <a href="2D_LAYOUT.html">2D drawing</a>. The round-trip proof.</figcaption>
</figure>

| I want to... | Start here |
|--------------|-----------|
| Understand the ERP world view | [**MANIFESTO — Read First**](MANIFESTO.md) |
| Read the master spec | [BOM-Based Compilation](BOMBasedCompilation.md) |
| Navigate the code | [Source Code Guide](SourceCodeGuide.md) |
| Onboard a new IFC | [IFC Onboarding Runbook](IFC_ONBOARDING_RUNBOOK.md) |
| See the frontier | [Project Order Blueprint](ProjectOrderBlueprint.md) |
| Explore the extensions | [**Federation Addon** — 4D through 8D](BONSAI_EXTENSIONS.md) |

<div style="clear: right;"></div>
