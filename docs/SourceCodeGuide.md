# BIM Intent Compiler — Source Code Guide

**Version 2.1**

**Creator:** Redhuan D. Oon <red1org@gmail.com>
**Author:** Anthropic Claude Code Opus 4.6
**License (text):** Creative Commons Attribution-ShareAlike 4.0 (CC BY-SA 4.0)
**License (code):** GPL v2 (compatible with iDempiere/Bonsai FOSS ecosystem)
**Copyright:** (c) 2026 Redhuan D. Oon. All rights reserved.
**Repository:** https://github.com/red1oon/BIMCompiler

---

## About the Creator

**Redhuan D. Oon** (red1) — Kuala Lumpur, Malaysia
[GitHub](https://github.com/red1oon) | [ADempiere Wiki](https://www.adempierebr.com/User:Red1) | [iDempiere Contributors](https://idempiere.org/contributors/)

### From ERP to BIM — Why This Project Exists

Redhuan D. Oon led [ADempiere](https://www.adempierebr.com/User:Red1) (2006) — the world's first community-forked open-source ERP, split from Compiere to establish community governance over vendor control. He went on to pave the way for [iDempiere](https://idempiere.org/) (2010), its OSGi-based successor, where he given vital documentation, and extensible plugins before retiring during the 2020 Plandemic.

In 2010 he authored *[Open Source ERP](https://www.amazon.com/Open-Source-ERP-Redhuan-Oon/dp/9673490228)* (Pearson Malaysia) — the first book of its kind worldwide — covering ERP architecture, virtual community building, and the ADempiere ecosystem for developers, integrators, and academics. He also established an Executive Masters programme in Open Source ERP through a Malaysian university, and conducted international advocacy tours across Europe, Asia, South America, and the Middle East promoting FOSS ERP adoption.

That two-decade immersion in iDempiere's Manufacturing BOM module, document-type architecture, and Application Dictionary pattern is the direct domain expertise behind this project's design. The BOM tree is iDempiere's `M_BOM`. The building types are `C_DocType`. The PO layer follows iDempiere's `X_`/`M_` convention. The EntityType guard (D/U/A) is lifted straight from iDempiere's Application Dictionary. The insight that drove this project: **a building is a manufactured product — its BOM is its complete definition**.

His standing FOSS philosophy — *"Merit Before Credit Before Profit"* and *"Contributors Are Priceless"* — shapes the project's verification-first culture: community-verifiable, no vendor lock-in, deterministic over probabilistic. The focus on UNESCO-aligned affordable school construction for developing countries is the mission that ties the ERP manufacturing heritage to real-world AEC impact.

---

## Abstract

The Architecture, Engineering, and Construction (AEC) industry has a productivity problem. While manufacturing doubled its output per worker over 20 years, construction flatlined. The IFC standard (ISO 16739) created a universal building data model — but the gap between *describing* a building and *generating* one deterministically remains wide open.

Five competing approaches have emerged to bridge this gap (see `docs/IntentBIMChallengePaper.pdf`, Table 1):

| Approach | Example | Drift Risk |
|----------|---------|------------|
| LLM-mediated generation | ChatGPT + IFC | **High** — hallucination is the feature |
| Parametric modeling | Grasshopper/Dynamo | Medium — scripts can invent geometry |
| Rule-based expert systems | Solibri | Low — but rules are opaque |
| AI-assisted design | Autodesk Forma | Medium — training data bias |
| **Metadata-driven compilation** | **This project** | **Verifiable zero** — extract or compile only |

This project takes the fifth path: a deterministic DSL-to-IFC compiler validated against Rosetta Stone reference buildings. Every output element traces to a library input. Nothing is invented. The compiler is a *translator*, not a *creator*.

**The drift problem** — why this matters for FOSS:

When AI tools generate building models, how do you prove the output wasn't hallucinated? When a parametric script adds a wall, how do you know the dimensions came from the design brief and not a default value? In safety-critical domains (structural engineering, fire egress, accessibility compliance), *provenance is not optional*.

This compiler solves drift by construction: the Prime Rule ("Extract or Compile Only") is enforced by 6 verification gates, 4 layers of test defense, and a tamper-detection seal. Every element in the output can be traced back to its source in the BOM dictionary or the IFC extraction oracle.

**BIM COBOL** — the domain-specific verb language — exists precisely to make mutations *auditable*. Every change to the building model goes through a named verb that returns a typed pass/fail result. No anonymous SQL. No silent geometry. Every action has a keyword, a witness, and a paper trail.

**This guide** exists to let you, the FOSS developer, get from `git clone` to running the full verification pipeline in under an hour — and to understand *why* every gate passes. Not just "it works" but "here's the mathematical proof that it didn't drift."

> **Further reading:**
> - Challenge paper: `docs/IntentBIMChallengePaper.pdf`
> - Full compilation spec: `docs/BOMBasedCompilation.md`
> - Construction-as-ERP model: `docs/ConstructionAsERP.md`
> - Anti-drift policy: `docs/TestArchitecture.md` §Anti-Drift

---

## Source Code Navigation Map

Before diving in, here's the **Eclipse-ready file index** — every key file with its fully qualified path.

For Java classes: use **Package Explorer** → expand module → `src/main/java` or `src/test/java`.
For Python/YAML/shell scripts: see the tip below.

### Python Scripts (Tier 3 — dictionary construction lifecycle)

> **Eclipse tip:** The `scripts/` folder is NOT a Maven source folder, so Eclipse's
> **Package Explorer hides it**. To see these files in Eclipse:
> 1. **Window → Show View → Project Explorer** (not Package Explorer) — shows all files
> 2. Or: In Package Explorer, click the **⋮ menu → Filters and Customization** and uncheck **"Non-Java elements"**
>
> Once visible, right-click any `.py` file → **Open With → Text Editor**.

| File | Path from project root | Role |
|------|----------------------|------|
| TE_BOM.db builder (TE only) | `scripts/RosettaStoneToBOM.py` | **SH/DX migrated to IFCtoBOM Java.** TE only: generates `TE_BOM.db` |
| IFC extraction (TE only) | `scripts/RosettaStoneExtract.py` | **SH/DX migrated to IFCtoBOM Java.** TE only: called by RosettaStoneToBOM.py |
| Building registration manifest | `scripts/construction_manifest.yaml` | Declarative building identity (SH, DX, TE) |
| AD schema scripts (10×) | `scripts/create_ad_*.py` | TE only: called by RosettaStoneToBOM.py — migrate to Java DAO |
| Test runner (full gate) | `scripts/run_tests.sh` | Compiles + all test suites + 6 gates |
| Rosetta Stone compile+delta | `scripts/run_RosettaStones.sh` | YAML-driven: IFCtoBOM → *_BOM.db → compile → delta |
| Hash seal verifier | `scripts/verify_test_seal.sh` | Layer 1 defense — SHA-256 of 74 critical files |
| Schema snapshot regen | `scripts/generate_output_template.sh` | Regenerates library/*.sql snapshots |

> **Python→Java migration status (2026-03-14):** SH and DX fully migrated to IFCtoBOM
> Java pipeline (`classify_sh.yaml` → `SH_BOM.db`, `classify_dx.yaml` → `DX_BOM.db`).
> DX uses 3-tier mirror partition (`CompositionBomBuilder`) — 485 paired/side + 129
> shared = 1099 elements, enbloc=walkthru delta=0. Python scripts (`RosettaStoneToBOM.py`,
> `RosettaStoneExtract.py`, 10× `create_ad_*.py`) remain for TE (Terminal) BOM generation.
> Target: replace Python Tier 3 scripts with Java DAO constructs where practical.
>
> **Previously deleted (2026-03-13):**
> `populate_sample_house_db.py`, `populate_duplex_db.py`, `rosetta_dictionary.py`,
> `intent_resolver.py`, `convert_spacetypes_to_ad.py`, `add_ad_dimensions.py`,
> `create_ad_bom_rule.py`, `create_ad_bom_grouping.py`, `create_ad_room_sizing.py`,
> `compile_intent.sh`, `full_cycle.sh`.

### Export Scripts (dormant — future verb candidates)

Three export scripts remain dormant. They read from output.db and produce external files.
When activated, each should become a **BIM COBOL verb** (e.g., `EXPORT IFC`, `EXPORT DRAWINGS`,
`EXPORT GLTF`) — giving them typed VerbResult, audit trail via PP_Order_Node, and pipeline
integration through VerbStage. The verb pattern replaces ad-hoc Python subprocess calls with
auditable, deterministic actions that follow the same Verb<T> interface as all other mutations.

| Script | Future Verb | External Deps |
|--------|-------------|---------------|
| `scripts/export_building_to_ifc.py` | `EXPORT IFC` | IfcOpenShell |
| `scripts/export_2d_drawings.py` | `EXPORT DRAWINGS` | matplotlib/SVG |
| `scripts/export_to_gltf.py` | `EXPORT GLTF` | pygltflib, numpy |

### Java — IFC-to-BOM Pipeline (`IFCtoBOM` module)

The `IFCtoBOM` module is the Java port of the Python extraction pipeline, replacing
`populate_sample_house_db.py` and `populate_duplex_db.py` with a DAO-based approach
driven by human/AI-readable **classification YAML**. This is the migration path from
Tier 3 (Python dictionary scripts) to Tier 1 (Java verb-protected DAO).

SH (Ifc4_SampleHouse) and DX (Ifc2x3_Duplex) are fully migrated, outputting
to `SH_BOM.db` and `DX_BOM.db` respectively. TE (Terminal) is next.

**Why Java matters here:** The Python scripts (`RosettaStoneToBOM.py`, `RosettaStoneExtract.py`)
are Tier 3 — they construct dictionary data outside the Java verb/guard layer. The IFCtoBOM
module brings extraction under the same EntityType enforcement, ORM dirty-tracking, and
`beforeSave()` validation that protects all other BOM mutations. When IFCtoBOM is complete,
`{PREFIX}_BOM.db` regeneration will go through Java PO classes — no more raw SQL in Python.

| Class | Eclipse Path |
|-------|-------------|
| `ClassificationYaml` (YAML POJO) | `IFCtoBOM/src/main/java/com/bim/ifctobom/ClassificationYaml.java` |
| `ExtractionPopulator` (populates I_Element_Extraction from reference DB) | `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionPopulator.java` |
| `ExtractionReader` (reads I_Element_Extraction from component_library.db) | `IFCtoBOM/src/main/java/com/bim/ifctobom/ExtractionReader.java` |
| `ProductRegistrar` (M_Product master in component_library.db + transitional copy to BOM DB) | `IFCtoBOM/src/main/java/com/bim/ifctobom/ProductRegistrar.java` |
| `BomValidator` (pre-commit QA — 9 checks: counts, normalization, offsets, AABB, tack I/O, element refs, product normalization, extraction reconciliation) | `IFCtoBOM/src/main/java/com/bim/ifctobom/BomValidator.java` |
| `StructuralBomBuilder` (port of RosettaStoneExtract.py) | `IFCtoBOM/src/main/java/com/bim/ifctobom/StructuralBomBuilder.java` |
| `ScopeBomBuilder` (scope space assignment → SET BOMs) | `IFCtoBOM/src/main/java/com/bim/ifctobom/ScopeBomBuilder.java` |
| `FloorRoomBomBuilder` (YAML-driven room BOMs) | `IFCtoBOM/src/main/java/com/bim/ifctobom/FloorRoomBomBuilder.java` |
| `CompositionBomBuilder` (mirror partition) | `IFCtoBOM/src/main/java/com/bim/ifctobom/CompositionBomBuilder.java` |
| `IntegrityHash` (SHA-256 fingerprint) | `IFCtoBOM/src/main/java/com/bim/ifctobom/IntegrityHash.java` |
| `IFCtoBOMPipeline` (orchestrator) | `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` |
| `IFCtoBOMMain` (CLI entry point) | `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMMain.java` |
| `classify_sh.yaml` (SH classification) | `IFCtoBOM/src/main/resources/classify_sh.yaml` |
| `classify_dx.yaml` (DX classification) | `IFCtoBOM/src/main/resources/classify_dx.yaml` |
| `ClassificationYamlTest` | `IFCtoBOM/src/test/java/com/bim/ifctobom/ClassificationYamlTest.java` |
| `SHPipelineTest` (SH G1-G5 gates) | `IFCtoBOM/src/test/java/com/bim/ifctobom/SHPipelineTest.java` |
| `DXPipelineTest` (DX G1-G5 gates) | `IFCtoBOM/src/test/java/com/bim/ifctobom/DXPipelineTest.java` |
| `IFCtoBOMGateTest` (Java=Python gate) | `IFCtoBOM/src/test/java/com/bim/ifctobom/IFCtoBOMGateTest.java` |

### Java — Compilation Pipeline (`DAGCompiler` module)
| Class | Eclipse Path |
|-------|-------------|
| `CompilationPipeline` | `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` |
| `CompilerStage` (interface) | `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilerStage.java` |
| `BuildingCompiler` | `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingCompiler.java` |
| `BuildingRegistry` | `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingRegistry.java` |
| `BuildingDefinition` | `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingDefinition.java` |
| `MetadataValidator` | `DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java` |
| `VerbExecutor` (SPI interface) | `DAGCompiler/src/main/java/com/bim/compiler/dsl/VerbExecutor.java` |
| `BOMTreeLoader` | `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java` |

### Java — BOM Walker/Visitor Subsystem (`DAGCompiler` module)

The **BOMWalker** is the single traversal engine for the BOM tree. It fires events to
registered **BOMVisitor** implementations — each visitor accumulates one concern
(assembly structure, spatial placement) during the same tree walk. This is the Visitor
pattern applied to iDempiere's `M_BOM` / `M_BOM_Line` hierarchy.

| Class | Eclipse Path |
|-------|-------------|
| `BOMWalker` (traversal engine) | `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/BOMWalker.java` |
| `BOMVisitor` (event interface) | `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/BOMVisitor.java` |
| `PlacementCollectorVisitor` (WHERE) | `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` |
| `AssemblyStructureVisitor` (assembly grouping) | `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/AssemblyStructureVisitor.java` |
| `SpatialPlacementVisitor` (PlacementLoader bridge) | `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/SpatialPlacementVisitor.java` |
| `BOMWalkerTest` (W-WALKER-1..5) | `DAGCompiler/src/test/java/com/bim/compiler/contract/BOMWalkerTest.java` |
| `SpatialPlacementVisitorTest` (W-SPV-1..4) | `DAGCompiler/src/test/java/com/bim/compiler/contract/SpatialPlacementVisitorTest.java` |

Three-way dispatch in `BOMWalker.walkChildren()`:
1. **SubAssembly** — `child_product_id` matches a `bom_id` → recurse deeper (`onSubAssembly` / `onSubAssemblyComplete`)
2. **PHANTOM** — component_type = 'PHANTOM' → gap filler, no output (`onPhantom`)
3. **Leaf** — leaf with geometry → produces output element (`onLeaf`)

Two entry points:
- `walk(rootBomId, visitors, buildingType)` — fires events for children only (standard)
- `walkSelf(rootBomId, visitors, buildingType)` — wraps root BOM in synthetic SubAssembly events (for assemblies)

### Java — Verification Gates (`DAGCompiler` test)
| Class | Eclipse Path |
|-------|-------------|
| `RosettaStoneGateTest` (G1-G6) | `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java` |
| `DataIntegrityTest` (D-1..D-5) | `DAGCompiler/src/test/java/com/bim/compiler/contract/DataIntegrityTest.java` |
| `SpotCheckContractTest` (C2-1..C2-5) | `DAGCompiler/src/test/java/com/bim/compiler/contract/SpotCheckContractTest.java` |
| `BOMDigestVerifyTest` (W-VERIFY-1..5) | `DAGCompiler/src/test/java/com/bim/compiler/contract/BOMDigestVerifyTest.java` |
| `ExtractedBOMWalkTest` (W-BOM-EB-1..5) | `DAGCompiler/src/test/java/com/bim/compiler/contract/ExtractedBOMWalkTest.java` |

### Java — BIM COBOL Verb Language (`BIM_COBOL` module)
| Class | Eclipse Path |
|-------|-------------|
| `Verb<T>` (interface) | `BIM_COBOL/src/main/java/com/bim/cobol/Verb.java` |
| `VerbResult<T>` | `BIM_COBOL/src/main/java/com/bim/cobol/VerbResult.java` |
| `VerbContext` | `BIM_COBOL/src/main/java/com/bim/cobol/VerbContext.java` |
| `VerbRegistry` | `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java` |
| `CheckBomVerb` (read-only example) | `BIM_COBOL/src/main/java/com/bim/cobol/verb/CheckBomVerb.java` |
| `AddLineVerb` (mutation example) | `BIM_COBOL/src/main/java/com/bim/cobol/verb/AddLineVerb.java` |

### Java — ORM / PO Layer (`orm-core` + `ORMSandbox` modules)
| Class | Eclipse Path |
|-------|-------------|
| `BasePO` | `orm-core/src/main/java/com/bim/orm/BasePO.java` |
| `ModelQuery` | `orm-core/src/main/java/com/bim/orm/ModelQuery.java` |
| `X_M_BOM` (generated structure) | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/X_M_BOM.java` |
| `MBOM` (model + business logic) | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java` |
| `X_M_BOMLine` | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/X_M_BOMLine.java` |
| `MBOMLine` | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOMLine.java` |
| `MProduct` | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MProduct.java` |
| `BomTemplateComposer` | `ORMSandbox/src/main/java/com/bim/ormsandbox/po/BomTemplateComposer.java` |

### Databases (inspect with `sqlite3` or DB Browser for SQLite)
| Database | Path | Role |
|----------|------|------|
| SH BOM dictionary | `library/SH_BOM.db` | IFCtoBOM pipeline output — clean SH-only BOM dictionary |
| DX BOM dictionary | `library/DX_BOM.db` | IFCtoBOM pipeline output — clean DX-only BOM dictionary |
| Geometry oracle | `library/component_library.db` | Meshes, materials from IfcOpenShell — ground truth |
| Compile DB | `library/{PREFIX}_compile.db` | **Transient** — assembled per build by `run_RosettaStones.sh` from `{PREFIX}_BOM.db` + schema + C_DocType. Not committed. |
| SH output | `DAGCompiler/lib/output/ifc4_samplehouse.db` | Compiled Sample House |
| DX output | `DAGCompiler/lib/output/ifc2x3_duplex.db` | Compiled Duplex |
| SH reference | `DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db` | IFC extraction oracle |
| DX reference | `DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db` | IFC extraction oracle |

---

## Chapter 1: The Big Picture

### "What is this thing?"

**One-sentence thesis:** *Construction is manufacturing. A building IS its Bill of Materials.*

A car factory doesn't improvise. It has a BOM (Bill of Materials) — a tree of parts, sub-assemblies, and raw materials. The factory *compiles* the BOM into a car. If the BOM says "4 wheels, 1 engine, 2 axles," you get exactly that. Not 3 wheels. Not a bonus spoiler.

This compiler does the same thing for buildings. An IFC file (the industry standard for building data) is *extracted* into a BOM dictionary. That dictionary is *compiled* into an output database. The output is *verified* against the original IFC reference. If any element was invented, added, or lost — the gates catch it.

[Figure 1.1] *Placeholder — Overview diagram: IFC file → IfcOpenShell extraction → component_library.db → IFCtoBOM (classify YAML) → *_BOM.db → Java compiler → output.db → 6-gate verification*

### The 3-Database Architecture

Think of it like a restaurant:

| Database | Restaurant Analogy | Role | Mutability |
|----------|-------------------|------|------------|
| `*_BOM.db` | The **menu** — recipes, ingredient lists, plating instructions | Per-building BOM dictionary (SH_BOM.db, DX_BOM.db). Built by IFCtoBOM Java pipeline from classify YAML | Read-only at compile time |
| `component_library.db` | The **pantry** — actual ingredients with nutritional labels | Geometry oracle: meshes, materials, dimensions extracted from IFC by IfcOpenShell | Read-only (external tool output) |
| `output.db` (per building) | The **plated dish** — what the customer actually gets | Compiled output: elements, instances, spatial structure, placement proofs | Written fresh each compile |

The menu (`{PREFIX}_BOM.db`) says "Caesar Salad = romaine + croutons + parmesan + dressing." The pantry (component_library.db) has the actual romaine with its weight and freshness date. The plated dish (output.db) is the assembled salad. If the plate has anchovies that aren't on the menu or in the pantry — *that's drift*.

> **Schema reference:** For the full table definitions (column names, types, FK relationships) of all three databases, see [`docs/DATA_MODEL.md`](DATA_MODEL.md). What follows here is a hands-on exploration walkthrough — not a schema spec.

### Examining `{PREFIX}_BOM.db` — The Source of Truth

The BOM DB is a SQLite file you can query directly. These queries reveal the truths
the compiler reads at build time.

```bash
# ── Structure: what's in this BOM? ────────────────────────
sqlite3 library/TE_BOM.db "SELECT bom_type, COUNT(*) FROM m_bom GROUP BY bom_type"
# → BUILDING=1, FLOOR=7, SET=50  (hierarchy: BUILDING → FLOOR → DISCIPLINE SET)

# ── Recipe lines vs instances (factorization) ────────────
sqlite3 library/TE_BOM.db "SELECT COUNT(*), SUM(qty) FROM m_bom_line WHERE component_type='LEAF'"
# → 1297 lines | 48428 instances  (37:1 compression via verb patterns)

# ── Verb coverage: which patterns were detected? ─────────
sqlite3 library/TE_BOM.db "
  SELECT SUBSTR(verb_ref, 1, INSTR(verb_ref, ':')-1) AS verb,
         COUNT(*) AS lines, SUM(qty) AS instances
  FROM m_bom_line WHERE component_type='LEAF' AND verb_ref IS NOT NULL
  GROUP BY verb ORDER BY instances DESC"
# → ROUTE 56 lines → 34,139 | SPRAY 297 → 13,336 | TILE 3 → 12

# ── Flat lines (no verb pattern, qty=1 each) ─────────────
sqlite3 library/TE_BOM.db "
  SELECT COUNT(*) FROM m_bom_line
  WHERE component_type='LEAF' AND verb_ref IS NULL"
# → 941 lines (small groups below MIN_GROUP=4 threshold)

# ── Products: how many distinct types? ────────────────────
sqlite3 library/TE_BOM.db "
  SELECT COUNT(DISTINCT child_product_id) FROM m_bom_line WHERE component_type='LEAF'"
# → 505 products  (factorization ratio: 1297/505 = 2.6× lines per product)

# ── Floor distribution: elements per storey ───────────────
sqlite3 library/TE_BOM.db "
  SELECT b.bom_id, b.bom_category,
    (SELECT SUM(l.qty) FROM m_bom_line l WHERE l.bom_id IN
      (SELECT bom_id FROM m_bom WHERE bom_id LIKE b.bom_id || '_%'))
  FROM m_bom b WHERE b.bom_type='FLOOR' ORDER BY b.seq_no"

# ── A single recipe line decoded ──────────────────────────
sqlite3 -header library/TE_BOM.db "
  SELECT child_product_id, role AS ifc_class, storey,
         dx, dy, dz, qty, verb_ref
  FROM m_bom_line WHERE verb_ref LIKE 'TILE%' LIMIT 1"
# → child_product_id=Roof_Tile_Type_A  verb_ref=TILE:2:6:0.495:0.150
#   Meaning: 2 columns × 6 rows, step 495mm × 150mm, origin at (dx,dy,dz)

# ── Offset chain: how world coordinates are computed ──────
# world_centroid = building_origin + MAKE.dx/dy/dz + LEAF.dx/dy/dz + verb_offset
# The MAKE line from BUILDING → FLOOR carries the floor offset:
sqlite3 library/TE_BOM.db "
  SELECT child_product_id, dx, dy, dz FROM m_bom_line
  WHERE bom_id = (SELECT bom_id FROM m_bom WHERE bom_type='BUILDING')
  ORDER BY sequence"

# ── Integrity check: was this BOM tampered with? ──────────
sqlite3 library/TE_BOM.db "SELECT * FROM bom_integrity"
# → hash + timestamp from last pipeline commit
```

**Key invariants to verify:**
- `SUM(qty)` of LEAF lines must equal expected element count (48,428 for TE)
- Every LEAF has a non-NULL `child_product_id` (otherwise BOMWalker silently skips)
- Every LEAF with `storey IS NOT NULL` has an `element_ref` (traceability)
- No `dx/dy/dz` exceeds 500m (world-coordinate leak)
- `verb_ref` expansion produces the same positions as original extraction
  (checked by `BomValidator.checkVerbExpansionFidelity()` in pipeline step 9b)

[Figure 1.2] *Placeholder — Screenshot of Eclipse Package Explorer showing all 7 modules: orm-core, ORMSandbox, DAGCompiler, BIM_COBOL, TopologyMaker, 2D_Layout, IFCtoBOM*

### The iDempiere Connection

Why use ERP tables to model construction? Because manufacturing solved this problem decades ago.

iDempiere is an open-source ERP system with a mature Manufacturing BOM module. This project borrows its table design — `M_BOM`, `M_BOM_Line`, `M_Product`, `C_DocType`, `C_Order` — because these tables already encode the relationships between finished goods, sub-assemblies, raw materials, and work orders. A building is just a very large finished good.

[Figure 1.3] *Placeholder — Side-by-side: iDempiere Manufacturing BOM screen vs BIM Compiler's m_bom table in SQLite*

> **Further reading:**
> - 3-DB architecture: `docs/DATA_MODEL.md`
> - iDempiere BOM mapping: `docs/ConstructionAsERP.md`
> - Interactive architecture diagram: `docs/bim_architecture_viz.html`

---

## Chapter 2: Setting Up Your Workshop

### "How do I get this running?"

**Prerequisites:**

| Tool | Version | Why |
|------|---------|-----|
| JDK | 17+ | Java source/target level |
| Maven | 3.8+ | Multi-module reactor build |
| SQLite3 | 3.x | Database inspection (`sqlite3 library/SH_BOM.db .schema`) |
| Python | 3.10+ | `{PREFIX}_BOM.db` regeneration scripts (PyYAML, IfcOpenShell) |
| Git | 2.x | Version control + tamper detection (G4 gate reads git history) |

### Clone, Build, Verify

```bash
# 1. Clone
git clone https://github.com/red1oon/BIMCompiler.git
cd BIMCompiler

# 2. Compile (quiet mode — just check for errors)
mvn compile -q

# 3. Run the full gate — this IS the proof
./scripts/run_tests.sh
```

> **Watch It! (Eclipse import)** After importing, if you see red markers like `"docs cannot be resolved to a module"` — that's Javadoc `@see` tags with bare file paths (e.g., `@see docs/TestArchitecture.md`). Eclipse's parser treats `docs` as a Java module name. Fix: wrap in `<a href>` tags. The codebase has already been cleaned, but if you add new Javadoc, use `@see <a href="docs/Foo.md">Foo</a>` — never bare paths.

That third command is the one that matters. It compiles every registered building, runs all 6 verification gates, and produces a pass/fail report. If everything is GREEN, the compiler didn't drift.

> **Brain Power:** Before reading further, run `./scripts/run_tests.sh` and read the output. Find the line that says `ROSETTA STONE GATE`. That's where the proof lives.

### Module Dependency Chain

```
orm-core          (BasePO, ModelQuery — no dependencies)
    ↓
ORMSandbox        (PO classes: MBOM, MBOMLine, MProduct — depends on orm-core)
    ↓ ↘
    ↓  IFCtoBOM   (IFC extraction → BOM dictionary — depends on ORMSandbox)
    ↓
DAGCompiler       (Pipeline, compiler, gates — depends on ORMSandbox)
    ↓
BIM_COBOL         (Verb language — depends on DAGCompiler via SPI)

TopologyMaker     (Spatial topology — standalone, depends on orm-core)
2D_Layout         (Floor plan layout — standalone, depends on orm-core)
```

The critical dependency is the SPI (Service Provider Interface) between DAGCompiler and BIM_COBOL. DAGCompiler defines `VerbExecutor` (the interface). BIM_COBOL provides `BimCobolVerbExecutor` (the implementation). Java's `ServiceLoader` discovers it at runtime. This breaks the circular dependency — the compiler doesn't know about verbs, verbs don't know about the compiler.

[Figure 2.1] *Placeholder — Eclipse Import dialog: File → Import → Maven → Existing Maven Projects, with root directory set to the cloned repo*

[Figure 2.2] *Placeholder — Package Explorer after successful import showing all 7 modules with green checkmarks*

From the root `pom.xml` (open in Eclipse: `bim-compiler/pom.xml`):

```xml
<!-- pom.xml:16-24 — the 7-module reactor -->
<modules>
    <module>orm-core</module>
    <module>ORMSandbox</module>
    <module>DAGCompiler</module>
    <module>2D_Layout</module>
    <module>TopologyMaker</module>
    <module>BIM_COBOL</module>
    <module>IFCtoBOM</module>
</modules>
```

> **Further reading:**
> - Build commands, DAO, migrations: `docs/DEVELOPER_GUIDE.md`

---

## Chapter 3: The Rosetta Stones

### "How do we know it works?"

**Verification philosophy:** *Don't trust the compiler. Trust the stones.*

A Rosetta Stone is a reference building with known-good IFC data. We extracted it with IfcOpenShell (an independent tool we didn't write), compiled it with our pipeline, and compared output to input. If they match — element for element, vertex for vertex, material for material — the compiler is faithful.

### Three Reference Buildings

| Stone | IFC Version | Elements | Type | Country |
|-------|-------------|----------|------|---------|
| **SH** — Ifc4_SampleHouse | IFC4 | 55 | Single-unit residential | UK |
| **DX** — Ifc2x3_Duplex | IFC2x3 | 1,099 | Paired half-units, 2-storey | USA |
| **TE** — SJTII Terminal | IFC2x3 | 51,088 | Airport terminal | Malaysia |

SH is the "hello world" — small enough to inspect by hand. DX is the stress test — over a thousand elements with complex multi-unit structure. TE is the scale test — fifty thousand elements proving the pipeline handles real-world institutional buildings.

[Figure 3.1] *Placeholder — IFC viewer showing Ifc4_SampleHouse: single-storey house with flat roof, 3 rooms*

[Figure 3.2] *Placeholder — IFC viewer showing Ifc2x3_Duplex: paired residential units, 2 storeys*

### The 6 Gates — Your Customs Checkpoint

Think of the gates like an airport customs checkpoint. Every element (passenger) in the output (arriving flight) must clear all 6 gates before the build is declared clean:

- **G1-COUNT** — "Did all passengers arrive?" (element count match)
- **G2-VOLUME** — "Does the luggage weigh right?" (AABB volume match)
- **G3-DIGEST** — "Fingerprint match?" (per-element spatial hash)
- **G4-TAMPER** — "Anyone tamper with the scanner?" (source code self-inspection)
- **G5-PROVENANCE** — "Everyone has a passport?" (every element traced to library)
- **G6-ISOLATION** — "No stowaways?" (no output elements outside BOM tree)

G4-TAMPER is the gate that catches *us* (the developers). It scans our own source code and git history for suspicious patterns — disabled tests, hardcoded coordinates, TODO markers in critical paths. If we cheat, G4 catches it.

> **Gate specification:** For the full gate implementation details, tamper rules (T1-T16), 4-layer defense model, and the hash seal mechanism, see [`docs/TestArchitecture.md`](TestArchitecture.md). The gate source lives in `RosettaStoneGateTest.java`.

When you run `./scripts/run_tests.sh`, you'll see output like this:

```
═══════════════════════════════════════════════════════════════════════
  ROSETTA STONE GATE — Compilation Integrity Report
═══════════════════════════════════════════════════════════════════════
  G1-COUNT   RE_SH  PASS  ref=55    out=55    delta=+0
  G1-COUNT   RE_DX  PASS  ref=1099  out=1099  delta=+0
  G2-VOLUME  RE_SH  PASS  delta=+0.00%
  G2-VOLUME  RE_DX  PASS  delta=+0.00%
  G3-DIGEST  RE_SH  PASS  SHA256 match
  G3-DIGEST  RE_DX  PASS  SHA256 match
  G4-TAMPER         PASS  0 violations
  G5-PROVENANCE     PASS  all elements traced
═══════════════════════════════════════════════════════════════════════
```

[Figure 3.3] *Placeholder — Terminal output showing all 6 gates GREEN*

> **Watch It!** If G4-TAMPER shows violations, DO NOT just fix the symptoms. Read the violation messages — they tell you *what rule* was broken and *where*. G4 rules are extensible regex patterns, not hardcoded checks.

> **There Are No Dumb Questions**
>
> *Q: Why not just use a hash of the entire output database?*
> A: Because a single hash doesn't tell you *where* the drift is. G1 tells you if elements were added or lost. G2 tells you if geometry changed. G3 tells you *which* elements changed. G4 tells you if the test itself was compromised. You need all of them.
>
> *Q: What about TE (the terminal)? It has 51K elements but no gate assertions?*
> A: TE is outside GATE_SCOPE (line 47: `Set.of("RE_SH", "RE_DX")`). It compiles and its output is verified, but gate assertions are only enforced for SH and DX where we have stable reference databases. TE is the scale proof, not the integrity proof.

> **Further reading:**
> - Gate implementation details: `docs/TestArchitecture.md`
> - Rosetta Stone strategy: `docs/TheRosettaStoneStrategy.txt`

---

## Chapter 4: From IFC to Dictionary

### "How does a real building become data?"

This is the extraction pipeline — the path from a real IFC file to the `{PREFIX}_BOM.db` dictionary that the Java compiler reads. Understanding this path is essential because it's where the "no invention" rule starts.

### Step 1: IfcOpenShell Extracts Geometry → component_library.db

IfcOpenShell (a FOSS tool we didn't write) reads the IFC file via `tools/extract.py`. This populates `component_library.db` with:
- **component_geometries** — tessellated vertex/face blobs (the actual mesh data)
- **I_Geometry_Map** — element → geometry hash mapping
- **surface_styles** — material RGBA colors

This database is our **oracle** — the independent ground truth we verify against. Geometry lives here permanently and is never copied to `{PREFIX}_BOM.db`.

### Step 2: IFCtoBOM Pipeline Creates Products → component_library.db

The Java pipeline (`ExtractionPopulator` + `ProductRegistrar`) reads the reference DB and populates `component_library.db` with:
- **I_Element_Extraction** — every IFC element with bounding box, storey, M_Product_ID
- **M_Product** — master product catalog (persistent, reused across buildings via INSERT OR IGNORE)
- **M_Product_Image** — product → geometry hash link

This is the **persistent product catalog**. When Terminal adds ~200 products, any that share names with SH/DX products are reused automatically — like iDempiere's product master.

### Step 3: BOM Dictionary → {PREFIX}_BOM.db (references only)

The BOM builders (`StructuralBomBuilder`, `ScopeBomBuilder`, `FloorRoomBomBuilder`) create the spatial hierarchy in `{PREFIX}_BOM.db`:
- **m_bom** — assembly headers (BUILDING → FLOOR → ROOM)
- **m_bom_line** — child placements with `child_product_id` (FK reference to M_Product) + dx/dy/dz offsets

The critical computation is the **tack offset** — each element's position relative to its floor's origin.

The tack offset is pure arithmetic — the centroid of the element minus the origin of its parent floor:

```python
# From StructuralBomBuilder (Java) / RosettaStoneExtract.py (TE legacy)
dx = (min_x + max_x) / 2 - floor_origin_x
dy = (min_y + max_y) / 2 - floor_origin_y
dz = (min_z + max_z) / 2 - floor_origin_z
```

> **Brain Power:** No machine learning. No heuristics. No "close enough." If the IFC says the wall centroid is at (5.2, 3.1, 1.5) and the floor origin is at (0.0, 0.0, 0.0), the tack offset is (5.2, 3.1, 1.5). Period.

### Step 4: The Two-DB Split at Compile Time

At compile time, the compiler reads **both** databases but for different concerns:
- **`{PREFIX}_BOM.db`** — structure: *what* goes *where* (m_bom_line.child_product_id + dx/dy/dz)
- **`component_library.db`** — catalog: *what it looks like* (M_Product dimensions, M_Product_Image → geometry mesh)

`{PREFIX}_BOM.db` never stores geometry. `child_product_id` is a pure FK reference — the product's dimensions and mesh are resolved from `component_library.db` at compile time via `MeshBinder`.

> **Transitional debt:** `ProductRegistrar.ensureProducts()` currently copies M_Product rows into `{PREFIX}_BOM.db` because `BOMWalker` reads `MProduct.get(bomConn, ...)`. Target: refactor BOMWalker to resolve products from `component_library.db` directly, eliminating the copy.

The pipeline is driven by classification YAML (`classify_sh.yaml`, `classify_dx.yaml`):

```
IFCtoBOMPipeline.java (orchestrator):
1. ExtractionPopulator    → populates I_Element_Extraction from reference DB + fills geometry gaps
2. ExtractionReader       → reads I_Element_Extraction from component_library.db
3. ProductRegistrar       → M_Product master in component_library.db, transitional copy to BOM DB
4. StructuralBomBuilder   → generates BUILDING + FLOOR m_bom headers + BUY lines
5. ScopeBomBuilder        → scope space assignment
6. CompositionBomBuilder  → mirror partition (DX only)
7. FloorRoomBomBuilder    → YAML-driven room BOMs
8. BomValidator           → pre-commit QA (9 checks — any FAIL = rollback)
9. IntegrityHash          → SHA-256 fingerprint
```

The key word is **reproducible**. Delete `SH_BOM.db`, run the pipeline, get the same database.
Every time. The pipeline reads from classification YAML (declarative identity) and
`component_library.db` (extracted geometry). It never invents data.

> **TE (Terminal):** Still uses `scripts/RosettaStoneToBOM.py` (Python legacy, pending Java migration).

[Figure 4.1] *Placeholder — ERD diagram of `{PREFIX}_BOM.db` core tables: m_bom, m_bom_line, M_Product, C_DocType, M_BomCategory*

### The "Extract or Compile Only" Prime Rule

This rule is the project's Prime Directive. It means:

1. **Extract** — read data from an external source (IFC file, IfcOpenShell extraction, construction manifest)
2. **Compile** — compute positions, dimensions, and structure from the extracted data using deterministic arithmetic
3. **Never invent** — no hardcoded coordinates, no default dimensions, no "reasonable assumptions"

If you find yourself typing a number that didn't come from a database query or a mathematical computation, you're violating the Prime Rule.

[Figure 4.2] *Placeholder — SQLite browser showing `{PREFIX}_BOM.db` tables: m_bom (left), m_bom_line (right)*

> **Further reading:**
> - Data model and 3-DB split: `docs/DATA_MODEL.md`
> - BOM dictionary pipeline: IFCtoBOM Java (SH/DX), `scripts/RosettaStoneToBOM.py` (TE legacy)
> - Construction manifest: `scripts/construction_manifest.yaml`

---

## Chapter 5: The BOM Tree — A Building in Layers

### "How do Lego instructions work?"

A Lego set has a hierarchy: the box contains bags, bags contain sub-assemblies, sub-assemblies contain bricks. A building BOM works the same way:

```
BUILDING_SH_STD              ← The box (finished good)
├── FLOOR_SH_GF_STD          ← Bag 1: Ground Floor
│   ├── SH_LIVING_SET        ← Sub-assembly: Living Room
│   │   ├── Sofa             ← Brick (LEAF)
│   │   ├── Piano            ← Brick (LEAF)
│   │   └── [buffer filler]  ← PHANTOM (gap, stripped at output)
│   ├── SH_BEDROOM_SET       ← Sub-assembly: Bedroom
│   │   ├── Bed              ← Brick (LEAF)
│   │   └── Wardrobe         ← Brick (LEAF)
│   └── SH_ENTRANCE_SET      ← Sub-assembly: Entrance
├── SH_GF_STR                ← Bag 2: Structural elements (walls, slabs)
│   ├── IfcWall #1           ← Structural LEAF
│   ├── IfcWall #2
│   └── ...55 elements
└── ROOF                     ← Bag 3: Roof structure
```

[Figure 5.1] *Placeholder — Tree diagram showing BUILDING → FLOOR → ROOM/SET → ITEM hierarchy for SH*

### The Tack Convention

Every element has a **tack point** — the Left-Front-Down corner of its bounding box. This is the origin (0,0,0) for all offset calculations.

```
        +Y (depth)
        ↑
        |     +-----------+
        |    /|           /|
        |   / |          / |       Height (Z)
        |  /  |         /  |         ↑
        | /   +--------/---+         |
        |/   /        /   /
        +---/---------+  /
        |  /          | /
        | /           |/
        +-------------+------→ +X (width)
       /
      /  ← TACK POINT (Left-Front-Down = origin)
    +Z comes out toward you
```

[Figure 5.2] *Placeholder — 3D diagram showing tack point on a room box with LFD corner marked*

### BOMChild — The Canonical Child Record

The `BOMChild` record is the atomic unit of the BOM tree. It combines `m_bom_line` columns with `m_attribute` params into a single immutable record.

From `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java:62-69`:

```java
public record BOMChild(
    int id, String bomId, String role, String childProductId,
    String namePattern, String componentType, String locatorRef,
    double dx, double dy, double dz,
    int sequence, boolean isVariance,
    String layoutStrategy,
    Map<String, String> params
) {
```

The `dx`, `dy`, `dz` fields are the tack offset — this child's position relative to its parent's tack point. To compute the world position of any element, you walk the tree from root to leaf, accumulating offsets:

```
world_position = building_origin
               + floor.dx, floor.dy, floor.dz        (Level 1)
               + room.dx, room.dy, room.dz            (Level 2)
               + item.dx, item.dy, item.dz            (Level 3 — leaf)
```

[Figure 5.3] *Placeholder — Cascade placement diagram showing 3 levels of offset accumulation*

### The 3-Table Authority Rule

When the same data exists in both `m_bom_line` columns and `m_attribute` params, the param wins. This is the "3-table authority" rule (BOM header → BOM line → attribute params):

From `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java:176-179`:

```java
// 3-table authority: param overrides column
String nameOverride = params.get("name_pattern");
String effectiveName = nameOverride != null
    ? nameOverride : raw.getChildNamePattern();
```

### Component Types

| Type | Meaning | At Compile Time |
|------|---------|----------------|
| **LEAF** | Full LOD exists in component_library.db | Emitted to output with real geometry |
| **MAKE** | Sub-assembly: LOD to be created via Mesh2Library | Not used in current data |
| **PHANTOM** | Gap filler (buffer between furniture) | Stripped at output — never emitted |

> **Watch It!** Component type is **NOT** a decision field. Recursion into sub-BOMs is determined by tree structure (does `childProductId` exist as a `bom_id`?), not by component type. See `BOMTreeLoader.java:74-79`.

> **Further reading:**
> - Tack convention §3.4: `docs/BOMBasedCompilation.md`
> - BOM dimensions model: `docs/ConstructionAsERP.md` §11
> - Prefab assembly hierarchy: `docs/PREFAB_ARCHITECTURE.md`

---

## Chapter 6: The 9-Stage Pipeline

### "The assembly line"

The compilation pipeline is a sequential chain of 9 stages. Each stage reads from a shared `CompilationContext`, does its work, and writes results back. If a stage fails, the pipeline stops.

[Figure 6.1] *Placeholder — Pipeline stage diagram: 9 boxes in sequence with arrows, labeled 1-METADATA through 9-PROVE*

From `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java:51-61`:

```java
private static final List<CompilerStage> STAGES = List.of(
    new MetadataValidator(),  // Step 1 — validate data before use
    new ParseStage(),         // Step 2
    new CompileStage(),       // Step 3
    new TemplateStage(),      // Step 4 — ST mode only
    new WriteStage(),         // Step 5
    new VerbStage(),          // Step 6 — BIM COBOL script hook
    new DigestStage(),        // Step 7
    new GeometryStage(),      // Step 8
    new ProveStage()          // Step 9
);
```

Every stage implements `CompilerStage` (`DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilerStage.java:9-19`):

```java
public interface CompilerStage {
    void execute(CompilationContext ctx) throws Exception;
    String name();
    default boolean shouldSkip(CompilationContext ctx) { return false; }
}
```

The pipeline runner is minimal — just a loop with logging (`DAGCompiler/.../dsl/CompilationPipeline.java:67-90`):

```java
public static PipelineResult run(BuildingEntry entry) throws Exception {
    CompilationContext ctx = new CompilationContext(entry);
    for (int i = 0; i < STAGES.size(); i++) {
        CompilerStage stage = STAGES.get(i);
        if (stage.shouldSkip(ctx)) {
            System.out.println("[SKIP] " + stage.name());
            continue;
        }
        stage.execute(ctx);
    }
    return ctx.toResult();
}
```

### Stage-by-Stage Walkthrough

#### Stage 1: MetadataValidator — "Check the ingredients before cooking"

Validates `{PREFIX}_BOM.db` referential integrity *before* anything runs. Two categories:

- **Global checks** (cached after first run): BOM chain integrity, geometry hashes, positive dimensions
- **Per-building checks**: building type exists, wall face references, room boundary references

From `DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java:25`:

```java
public class MetadataValidator implements CompilerStage {
    @Override
    public String name() { return "METADATA VALIDATION"; }
```

The NO FALLBACK gate is critical: every LEAF product must have a matching mesh in `component_library.db`. If a product is referenced in the BOM but has no geometry in the library, compilation fails *before* it starts — not at emission time when it's too late to fix.

#### Stage 2: ParseStage — "Read the recipe"

Parses DSL text (the building definition language) into a `BuildingDefinition` object. The DSL is stored in `C_DocType.DSLContent` in `{PREFIX}_BOM.db`.

```java
BuildingDefinition def = BuildingParser.parse(ctx.entry().dslContent());
```

#### Stage 3: CompileStage — "Cook the dish"

The heart of the compiler. Walks the BOM tree, resolves geometry from the component library, computes placements, and produces a `BuildingSpec` — the fully resolved building model.

```java
CompilationResult result = BuildingCompiler.compileWithValidation(ctx.definition());
BuildingSpec spec = result.spec();
```

**CO mode (Terminal):** CompileStage is **skipped** when `DocBaseType=CO`. Commercial/institutional buildings get all elements from BOM extraction — the DSL compilation path (`BuildingCompiler` → `StoreyCompiler`) is not needed. `shouldSkip()` creates a minimal `BuildingSpec` (building name, empty storeys, no roof) so WriteStage can still run the extracted placement path via `emitGlobalPlacementElements()`.

Why skip? `StoreyCompiler` generates structural slabs from computed bay dimensions and calls `PlacementLoader.markConsumed()` on element_refs. For CO buildings the element_ref is a product type name shared by hundreds of elements — consuming one consumes them all, silently dropping extracted slabs from the output.

**CO mode data integrity — what the compiler reads and does NOT read:**

| DB | Opened by | Purpose | Read/Write |
|----|-----------|---------|------------|
| `{PREFIX}_BOM.db` | `PlacementLoader` | BOM tree walk → element placements | Read |
| `component_library.db` | `BOMWalker`, `MeshBinder` | M_Product dimensions, geometry meshes | Read |
| `output.db` | `WriteStage` | Fresh file — deleted and recreated each compile | Write |
| `*_extracted.db` (reference) | `GeometryStage`, `RosettaStoneGateTest` | Post-compile verification ONLY | Read (never during emission) |

The reference/input DB is **never opened** during Stages 1–6 (the compilation and emission stages). It is opened only at Stage 8 (GeometryStage) and in the gate tests — both are read-only verification that does not feed back into the output. No coordinates are adjusted, no counts are patched, no elements are added or removed to match the reference. The output is dictated solely by `{PREFIX}_BOM.db` + `component_library.db`. See `docs/LAST_MILE_PROBLEM.md` §Gap 4 for the full spec source inventory.

**IDE verification:**
- `CompilationPipeline.java:234-241` — `shouldSkip()` guard: `"CO".equals(ctx.entry().docBaseType())`
- `classify_te.yaml:8` — `doc_base_type: CO` (the YAML value that triggers the skip)
- `BuildingRegistry.java:28` — `docBaseType` field on `BuildingEntry` record
- `PlacementLoader.java:160-161` — opens `bom.db` + `component_library.db` only (no reference DB)
- `BuildingWriter.java:959` — `isConsumed()` check (inert for CO — no `markConsumed` calls)
- `GeometryIntegrityChecker.java:89` — reference DB opened here (Stage 8, verification only)

> **Deep dive:** `docs/TerminalAnalysis.md` §Coding Specs has the full forensic chain — 3 bugs, root cause analysis, GUID evidence (`SLAB_GROUND FLOOR_UNIT_*` vs `STR_MD_SLAB_*`), and the 5 remaining Specs.

#### Stage 4: TemplateStage — "Choose from the catalog" (ST mode only)

When DocSubType is 'ST' (Standard Template), there's no pre-built BUILDING BOM. Instead, the template path uses AABB matching to *select* the best-fit BOMs from the catalog at every level of the hierarchy.

This is the WALK-THRU path — the alternative to EN-BLOC (where the BOM already exists and is compiled as-is).

#### Stage 5: WriteStage — "Plate the dish"

Serializes the `BuildingSpec` to output.db: creates tables, writes `elements_meta`, `base_geometries`, `element_instances`, spatial containment, and the `CO_EmptySpace` WMS acceptance record.

#### Stage 6: VerbStage — "Chef's final touches"

Executes BIM COBOL verb scripts via the SPI. If the building has a `.bimcobol` file, its verb lines are parsed and dispatched through `VerbExecutor`. If no BIM_COBOL module is on the classpath, the stage falls back to log-only mode.

#### Stage 7: DigestStage — "Fingerprint the dish"

Computes a SHA-256 spatial digest of every element's placement. This is the fingerprint that G3-DIGEST compares between reference and compiled output.

#### Stage 8: GeometryStage — "Weigh the portions"

Validates mesh integrity: vertex counts, face normals, bounding box consistency. Optionally compares against reference geometry if available.

#### Stage 9: ProveStage — "The inspector signs off"

Mathematical placement proofs. Checks that:
- Elements don't overlap (collision detection)
- Elements are inside their parent's bounding box (containment)
- All placement offsets are non-negative (tack convention compliance)

If all critical proofs pass, the `CO_EmptySpace` header is promoted from IP (In Progress) to CO (Complete). If proofs fail, it's marked RE (Rejected).

### Two Compilation Modes

| Mode | Trigger | What Happens |
|------|---------|-------------|
| **EN-BLOC** | Building has a BUILDING BOM (e.g., BUILDING_SH_STD) | The entire BOM tree is compiled in one pass — singularity |
| **WALK-THRU** | DocSubType = 'ST' (template mode) | AABB matching selects best-fit BOMs level by level — progressive stacking |

[Figure 6.2] *Placeholder — Eclipse debug view stepping through CompilationPipeline.run() showing stage progression*

> **Further reading:**
> - Full compilation spec: `docs/BOMBasedCompilation.md`
> - Pipeline entry point: `DAGCompiler/.../dsl/BuildingCompiler.java`

---

## Chapter 7: The ORM Layer — iDempiere Patterns

### "Standing on the shoulders of ERP giants"

The ORM layer follows iDempiere's Persistent Object (PO) pattern — but stripped to essentials for SQLite. No JPA. No Hibernate. Just a column map, dirty tracking, and explicit transaction ownership.

### BasePO — The Foundation

From `orm-core/src/main/java/com/bim/orm/BasePO.java:8-32`:

```java
/**
 * Lightweight Persistent Object base class — SQLite edition.
 *
 * Responsibilities:
 *   - Hold a JDBC Connection (injected, not owned)
 *   - load(id): SELECT * → populate column map
 *   - save(): INSERT OR IGNORE (new) or UPDATE dirty cols (existing)
 *   - delete(): DELETE WHERE pk = id
 *
 * Column values in LinkedHashMap<String, Object>.
 * Dirty flags in LinkedHashSet<String> — only dirty columns written on UPDATE.
 * Transaction ownership: BasePO never calls commit().
 */
public abstract class BasePO {
    protected final Connection conn;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    private boolean isNewRecord = true;
```

Key design decisions:
- **Connection injected, not owned** — the caller manages the transaction lifecycle
- **Dirty tracking** — only modified columns are written on UPDATE, reducing SQL chatter
- **No commit()** — BasePO never commits. The orchestrator owns the transaction boundary.

### The X_/M_ Convention

Every PO class comes in two layers:

| Layer | File | Role |
|-------|------|------|
| **X_** (generated structure) | `X_M_BOM.java` | Column constants, getters/setters, table metadata |
| **M_** (model logic) | `MBOM.java` | Business logic, factory methods, validation |

This mirrors iDempiere exactly. In iDempiere, `X_M_Product` is auto-generated from the Application Dictionary; `MProduct` adds business logic. Here, `X_M_BOM` defines the column map; `MBOM` adds `getBuildingBom()`, `beforeSave()` guards, and SpaceSize validation.

[Figure 7.1] *Placeholder — Class diagram: BasePO → X_M_BOM → MBOM, showing column constants in X_ and business methods in M_*

### EntityType Enforcement — The Dictionary Guard

From `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java:97-105`:

```java
@Override
protected void beforeSave(boolean newRecord) {
    if (getGroupBy() == null)
        throw new IllegalStateException("group_by must not be null");
    // EntityType guard: dictionary records are read-only through PO layer
    if (!newRecord && !isGodMode() && ENTITYTYPE_Dictionary.equals(getEntityType()))
        throw new IllegalStateException(
            "MBOM " + getBomId() + " is EntityType=D (Dictionary) — read-only. "
            + "Use verbs to create new SY_ records (EntityType=U).");
```

Three EntityTypes protect data integrity:

| EntityType | Code | Meaning | PO Behavior |
|------------|------|---------|-------------|
| Dictionary | D | Reference data from extraction/scripts | **Read-only** — beforeSave() blocks writes |
| User | U | Created by BIM COBOL verbs (SY_ prefix) | Read-write |
| Application | A | System-generated | Read-write |

This is how we prevent drift at the ORM layer. Dictionary records (the ground truth from extraction) cannot be silently modified. If you want to change building data, you must go through a verb — which creates a *new* record with EntityType=U, leaving the dictionary record untouched.

### Key Factory Methods

From `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java:65-73` — finding the top-level building BOM:

```java
/**
 * Find the top-level BUILDING BOM for a given DocSubType
 * (e.g. "SH" → BUILDING_SH_STD).
 * In iDempiere Mfg, this is the finished goods BOM.
 */
public static MBOM getBuildingBom(Connection conn, String docSubType)
        throws SQLException {
    List<MBOM> bldgs = new ModelQuery<>(conn, MBOM::new, Table_Name)
        .where(COLUMNNAME_bom_type + " = ?", "BUILDING")
        .andWhere(COLUMNNAME_doc_sub_type + " = ?", docSubType)
        .andWhere(COLUMNNAME_is_active + " = ?", 1)
        .orderBy(COLUMNNAME_seq_no).list();
    return bldgs.isEmpty() ? null : bldgs.get(0);
}
```

[Figure 7.2] *Placeholder — Eclipse class hierarchy showing X_M_BOMLine → MBOMLine with method list*

> **Further reading:**
> - DAO rules and ORM modules: `docs/DEVELOPER_GUIDE.md`

---

## Chapter 8: BIM COBOL — The Verb Language

### "Talking to the building"

Why a domain-specific language for construction mutations? Because **every change must be auditable**.

When a developer writes raw SQL to modify `m_bom_line`, there's no record of intent. Was it a bug fix? A new room? A material swap? With BIM COBOL, every mutation is a named verb with typed arguments and a pass/fail result:

```
CHECK BOM BUILDING_SH_STD
ADD LINE TO SY_MY_ROOM CHILD sofa_001 ROLE FURNITURE SEQ 10 DX 0.5 DY 0.3
FURNISH ROOM SY_MY_ROOM TEMPLATE SH_LIVING_SET
```

### The Verb<T> Interface

From `BIM_COBOL/src/main/java/com/bim/cobol/Verb.java:13-20`:

```java
/**
 * A BIM COBOL verb — one executable action in the construction language.
 * Verbs are the atoms of the language. Each verb reads BOM state, performs
 * a deterministic computation, and returns a typed result with pass/fail.
 */
public interface Verb<T> {
    /** Grammar keyword, e.g. "CHECK BOM", "ROUTE SPRINKLERS". */
    String keyword();

    /** Execute this verb against the given context. */
    VerbResult<T> execute(VerbContext ctx, String... args) throws SQLException;
}
```

### VerbResult<T> — Typed Pass/Fail

From `BIM_COBOL/src/main/java/com/bim/cobol/VerbResult.java:14-26`:

```java
public record VerbResult<T>(boolean pass, String verb, String summary, T payload) {

    public static <T> VerbResult<T> ok(String verb, String summary, T payload) {
        return new VerbResult<>(true, verb, summary, payload);
    }

    public static <T> VerbResult<T> fail(String verb, String summary, T payload) {
        return new VerbResult<>(false, verb, summary, payload);
    }
}
```

Every verb returns a `VerbResult` with:
- `pass` — did it succeed?
- `verb` — which keyword produced this result
- `summary` — human-readable one-liner
- `payload` — typed detail object (verb-specific)

### VerbContext — The Three Connections

From `BIM_COBOL/src/main/java/com/bim/cobol/VerbContext.java:14`:

```java
public record VerbContext(
    Connection bomConn,       // {PREFIX}_BOM.db (read-only for CHECK verbs)
    Connection componentConn, // component_library.db (nullable)
    Connection outputConn     // output.db (nullable — only for emitting verbs)
) {
```

Read-only verbs (CHECK BOM, LIST BOM) only need `bomConn`. Mutation verbs (ADD LINE, CREATE BOM) write to `bomConn`. Emitting verbs (PLACE BOM, EN-BLOC) write to `outputConn`.

### Example: A Read-Only Verb

`CheckBomVerb` is the simplest complete verb — it walks a BOM tree and counts nodes without writing anything.

From `BIM_COBOL/src/main/java/com/bim/cobol/verb/CheckBomVerb.java:28-68`:

```java
public class CheckBomVerb implements Verb<CheckBomVerb.BomCheckPayload> {

    private static final int MAX_DEPTH = 20;  // cycle guard

    @Override
    public String keyword() { return "CHECK BOM"; }

    @Override
    public VerbResult<BomCheckPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(), "usage: CHECK BOM <bom_id>", null);

        String bomId = args[0];
        Connection conn = ctx.bomConn();

        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(), bomId + " not found", /* ... */);

        List<String> errors = new ArrayList<>();
        int[] counts = new int[3]; // [BUY, MAKE, PHANTOM]
        int maxDepth = walk(conn, bomId, 0, counts, errors);

        BomCheckPayload payload = new BomCheckPayload(
                counts[0], counts[1], counts[2], maxDepth, errors);

        if (errors.isEmpty()) {
            return VerbResult.ok(keyword(),
                String.format("%s: %d BUY, %d MAKE, %d PHANTOM, depth=%d",
                    bomId, counts[0], counts[1], counts[2], maxDepth),
                payload);
        } else {
            return VerbResult.fail(keyword(),
                String.format("%s: %d errors", bomId, errors.size()), payload);
        }
    }
```

> **Brain Power:** Notice the pattern: parse args → validate input → do work → return typed result. Every verb follows this shape. The payload is a record with verb-specific data. The summary is a one-liner for logging. The pass/fail boolean drives pipeline decisions.

### Example: A Mutation Verb

`AddLineVerb` creates one `m_bom_line` row — the atomic write operation for BOM construction.

From `BIM_COBOL/src/main/java/com/bim/cobol/verb/AddLineVerb.java:24-118`:

```java
public class AddLineVerb implements Verb<AddLineVerb.AddLinePayload> {

    @Override
    public String keyword() { return "ADD LINE"; }

    @Override
    public VerbResult<AddLinePayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        // Parse: ADD LINE TO <bom_id> CHILD <child> ROLE <role> SEQ <n>
        String bomId = args[1];

        // ... parse named params (CHILD, ROLE, SEQ, DX, DY, DZ, etc.) ...

        // Validate parent BOM exists
        MBOM parent = MBOM.get(conn, bomId);
        if (parent == null)
            return VerbResult.fail(keyword(), "parent BOM not found: " + bomId, null);

        // Create the line via PO (triggers beforeSave, EntityType guard)
        MBOMLine line = new MBOMLine(conn);
        line.setBomId(bomId);
        line.setChildProductId(childProductId);
        line.setRole(role);
        line.setSequence(sequence);
        line.setDx(dx); line.setDy(dy); line.setDz(dz);
        line.setComponentType(componentType);
        line.setEntityType(MBOM.ENTITYTYPE_User);  // Always 'U' — never 'D'
        line.save();

        return VerbResult.ok(keyword(),
            String.format("ADD LINE %s → %s (role=%s, seq=%d, type=%s)",
                bomId, childProductId, role, sequence, componentType),
            payload);
    }
```

> **Watch It!** Line 106: `line.setEntityType(MBOM.ENTITYTYPE_User)` — verbs always create User records, never Dictionary records. This is how the anti-drift guard works at the verb level. Dictionary data comes from extraction scripts. User data comes from verbs. They never mix.

### VerbRegistry — Dispatch by Longest Prefix

From `BIM_COBOL/src/main/java/com/bim/cobol/VerbRegistry.java:52-80`:

```java
public VerbResult<?> dispatch(VerbContext ctx, String line) {
    String trimmed = line.trim();
    String upper = trimmed.toUpperCase();

    // Find longest keyword prefix
    for (String kw : sortedKeywords) {
        if (upper.startsWith(kw)) {
            if (upper.length() == kw.length()
                    || upper.charAt(kw.length()) == ' ') {
                Verb<?> verb = verbs.get(kw);
                String rest = trimmed.substring(kw.length()).trim();
                String[] args = tokenize(rest);
                return verb.execute(ctx, args);
            }
        }
    }
    return VerbResult.fail("DISPATCH", "unknown keyword: " + trimmed, null);
}
```

Keywords are multi-word (e.g., "CHECK BOM", "COVER WITH COMPOUND_ROOF", "ADD LINE"). The registry sorts keywords by word count descending so the longest prefix matches first. "COVER WITH COMPOUND_ROOF" matches before "COVER WITH" which matches before "COVER".

### The 5-Tier Composition Model

Verbs are organized into 5 composition tiers (P0 through L4), where each layer builds on the one below. P0 primitives (CREATE BOM, ADD LINE) are atomic single-row CRUD. L1 convenience verbs compose P0 calls for room-level operations. L2-L4 scale up through floor, building, and catalog levels. Every higher-level verb decomposes into P0 primitives — no new infrastructure required.

> **Full tier table:** The complete verb tier specification with all 63 verbs, 196 witnesses, and the composition stack is in [`docs/BIM_COBOL.md`](BIM_COBOL.md). Verb pattern detection (TILE, ROUTE, FRAME, SPRAY) and the factorization architecture are in [`docs/VerbPatternArchitecture.md`](VerbPatternArchitecture.md).

[Figure 8.1] *Placeholder — Tier diagram showing P0→L1→L2→L3→L4 verb composition layers*

> **There Are No Dumb Questions**
>
> *Q: How many verbs are there?*
> A: The registry creates 60+ built-in verbs (see `VerbRegistry.createDefault()`). The full verb catalog with witnesses is in `docs/BIM_COBOL.md`.
>
> *Q: Can I add my own verb?*
> A: Yes — implement `Verb<T>`, add `reg.register(new MyVerb())` in `VerbRegistry.createDefault()`. Write a witness test first. See Chapter 10.

### VerbExecutor SPI — The Bridge

The SPI (Service Provider Interface) pattern breaks the circular dependency between DAGCompiler and BIM_COBOL:

From `DAGCompiler/src/main/java/com/bim/compiler/dsl/VerbExecutor.java:9-17`:

```java
/**
 * SPI interface for BIM COBOL verb execution.
 *
 * Implementations discovered via ServiceLoader by VerbStage.
 * If no implementation found, VerbStage falls back to log-only mode.
 *
 * The canonical implementation is BimCobolVerbExecutor in BIM_COBOL.
 * This SPI pattern breaks the circular dependency:
 * DAGCompiler defines the interface, BIM_COBOL provides the implementation.
 */
public interface VerbExecutor {
    ExecutionReport execute(Connection bomConn, Connection outputConn,
                            String buildingId, List<String> verbLines) throws Exception;
}
```

[Figure 8.2] *Placeholder — SPI flow: DAGCompiler defines VerbExecutor → ServiceLoader discovers BimCobolVerbExecutor → BIM_COBOL dispatches via VerbRegistry*

> **Further reading:**
> - Full verb catalog, witnesses, grammar: `docs/BIM_COBOL.md`
> - BIM COBOL grammar spec: `docs/BIM_COBOL.md` §Grammar
> - Synthetic BOM design: `docs/BIM_COBOL.md` §18

---

## Chapter 9: Testing — The 4-Layer Defense

### "Trust, but verify. Then verify the verifier."

The anti-drift defense is not a single test. It's four concentric layers, each catching a different class of failure:

```
╔═══════════════════════════════════════════════════════════╗
║  Layer 4: Git Diff Review (human)                        ║
║  ┌─────────────────────────────────────────────────────┐  ║
║  │  Layer 3: Cross-Database Verification (D-1..D-5)   │  ║
║  │  ┌───────────────────────────────────────────────┐  │  ║
║  │  │  Layer 2: Structural Guards (ArchUnit, T16)  │  │  ║
║  │  │  ┌─────────────────────────────────────────┐  │  │  ║
║  │  │  │  Layer 1: Hash Seal (73 files)         │  │  │  ║
║  │  │  └─────────────────────────────────────────┘  │  │  ║
║  │  └───────────────────────────────────────────────┘  │  ║
║  └─────────────────────────────────────────────────────┘  ║
╚═══════════════════════════════════════════════════════════╝
```

[Figure 9.1] *Placeholder — Defense-in-depth diagram (4 concentric layers)*

### Layer 1: Hash Seal

`scripts/verify_test_seal.sh` computes SHA-256 hashes of 73 critical source files (tests, gates, compilation pipeline). If any file is modified without updating the seal manifest, the pre-commit hook catches it.

**What it catches:** Unauthorized modification of test files, gate logic, or pipeline stages. If someone disables a test, the seal breaks.

### Layer 2: Structural Guards

ArchUnit rules and bytecode scanning enforce architectural constraints:
- EntityType enforcement (T16): scans `src/main/` for raw SQL writes to protected tables
- Module dependency rules: BIM_COBOL must not import DAGCompiler internals
- PO convention: no direct JDBC writes to `m_bom` or `m_bom_line` outside verbs

**What it catches:** Developers bypassing the verb layer to write raw SQL to protected tables.

### Layer 3: Cross-Database Verification

`DataIntegrityTest.java` runs 5 cross-database checks (D-1 through D-5) that compare `{PREFIX}_BOM.db` against `component_library.db` — the oracle we didn't write. The key insight: if a product exists in the BOM but not in the extraction oracle, it was invented. If the AABB envelope differs, the dimensions were fabricated. The oracle (component_library.db) is extracted from real IFC files by an external tool (IfcOpenShell) — to cheat these checks, you would have to forge the IFC source files themselves.

### Layer 4: Git Diff Review

Human review of `[SEAL]` commits. Every seal-updating commit shows the exact diff of what changed in test files — a cheating re-seal is visible in the diff history.

> **Brain Power:** Why are tests "GIGO" (Garbage In, Garbage Out)? Because a unit test only checks what the developer wrote. If the developer writes `assertEquals(55, 55)` instead of querying the actual count, the test passes but proves nothing. The seal + gates + cross-database checks catch what unit tests miss — they verify against *independent* data sources.

> **Full specification:** The complete anti-drift policy (5 rules), all D-1 through D-5 check definitions, tamper rules (T1-T16), hash seal manifest, and the 4-layer defense analysis are in [`docs/TestArchitecture.md`](TestArchitecture.md). The seal verification script is `scripts/verify_test_seal.sh`.

### Layer 5: C2-SpotCheck — Per-Element Coordinate Proof

While G3-DIGEST provides an aggregate spatial hash, `SpotCheckContractTest` goes further:
it picks **5 representative elements** (one per major IFC class) and cross-checks their
**exact AABB coordinates** between the compiled output and the reference extraction oracle.

From `DAGCompiler/src/test/java/com/bim/compiler/contract/SpotCheckContractTest.java`:

| Test | IFC Class | What It Proves |
|------|-----------|----------------|
| C2-1 | IfcSlab | Ground floor slab spans correct building footprint |
| C2-2 | IfcDoor | Interior door placement (wall-hosted offsets) |
| C2-3 | IfcWindow | Window sill height (elevated Z offset, minZ > 0.5m) |
| C2-4 | IfcFurnishingElement | Piano AABB + material_rgba (Wood - Mahogany) |
| C2-5 | IfcWall | North exterior wall span + material (Brick, Common) |

**Anti-cheat design:** No hardcoded golden values. Each test queries the reference DB
*live* for the element, then queries the compiled output for the same element matched
by `ifc_class` + coordinate position (since element names differ between EXTRACTED and
COMPILED modes). 1mm tolerance absorbs floating-point noise.

### Forensic Inspection Guide — Where to Look

For hands-on human verification of the SH sample house (the "trust but verify" path):

**1. Live coordinate comparison** — run these queries side by side:
```sql
-- Output DB (DAGCompiler/lib/output/ifc4_samplehouse.db):
SELECT em.ifc_class, em.element_name, em.storey, em.material_name,
       em.material_rgba, r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ
FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
ORDER BY em.ifc_class, r.minX, r.minY;

-- Reference DB (DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db):
-- Same query. Compare row by row.
```

**2. Dual-output delta** — `./scripts/run_RosettaStones.sh` compiles SH twice:
- `_enbloc.db` — EN-BLOC (takes BOM lines as-is, proves data)
- `_walkthru.db` — WALK THRU (recalculates through BOM layers, proves mechanism)
- Delta between them must be zero. Both must independently match the reference.

**3. The placement formula chain** — trace one element end-to-end:
```
IFC file → IfcOpenShell → component_library.db (I_Element_Extraction)
    → RosettaStoneExtract.py → {PREFIX}_BOM.db (m_bom_line: dx, dy, dz, allocated_*_mm)
    → BOMWalker → PlacementCollectorVisitor (anchor accumulation + leaf offset)
    → output.db (elements_rtree: minX, maxX, minY, maxY, minZ, maxZ)
    → RosettaStoneGateTest G1-G6 → verified against reference DB
```

**4. Key source files** for spatial debugging:
| File | What to check |
|------|--------------|
| `SpatialDigest.java:63-119` | The hash formula — what exactly is hashed |
| `PlacementCollectorVisitor.java:83-134` | `onSubAssembly()` — anchor stack accumulation |
| `PlacementCollectorVisitor.java:156-234` | `onLeaf()` — world coordinate computation |
| `BOMWalker.java:138-181` | Three-way dispatch (SubAssembly/PHANTOM/Leaf) |
| `placementforensics.txt` | End-to-end proof for one element |
| `IFC-to-BOM-forensics.txt` | Full pipeline evidence chain |

**5. Material/surface coverage** — G5-PROVENANCE checks that output material_rgba
coverage >= reference coverage. G3-DIGEST includes material_rgba in the per-element
hash. The piano (C2-4) specifically verifies material_rgba = "0.541,0.337,0.176,1.000"
(Wood - Mahogany) matches between output and reference.

---

## Chapter 9.5: The Architecture Beyond Verbs

### Separation of Concerns — The Golden Rule

From `docs/CONCEPTUAL BLUEPRINT.txt` — each layer owns exactly one kind of data:

```
Concrete data ONLY in YAML (bounds)
Geometry ONLY in Component Library (tack points)
Relationships ONLY in BOM (references)
Execution ONLY in ERP (orders)
Math ONLY in Compiler (constraints)
```

This separation is what makes the system verifiable. A `SpatialDigest` can hash positions
without knowing what they represent. A `BOMWalker` can traverse trees without knowing
what's in the nodes. A `PlacementCollectorVisitor` can accumulate offsets without knowing
which building it's compiling. Each layer is pure, testable, and AI-friendly.

### The Model-Engine-Registry Pattern

The codebase follows an iDempiere-inspired separation (from `docs/CONCEPTUAL BLUEPRINT.txt`):

| Layer | Package Pattern | Responsibility | Example |
|-------|----------------|----------------|---------|
| **Model** | PO classes (`MBOM`, `MBOMLine`) | Pure data containers + dirty tracking | `X_M_BOM.java` |
| **Engine** | Pipeline stages, visitors | Stateless logic — pure math | `PlacementCollectorVisitor.java` |
| **Registry** | Loaders, factories | Data access — reads only | `BOMTreeLoader.java` |
| **Verify** | Digest, gates, contracts | Validation — reports, never mutates | `SpatialDigest.java` |
| **Execute** | Verb layer, SPI | Auditable mutations — typed results | `AddLineVerb.java` |

**Key rule:** Engine never calls Registry (data passed in). Model never has logic (pure POJOs).
Verify never modifies (just reports). This mirrors iDempiere's `AD_Table` (model) /
`AD_Process` (engine) / `DocValidate` (verify) separation.

### The Verb Graph — Future Direction

From `docs/BeyondVerbs.txt` — verbs today are flat (keyword → execute → result). The next
evolution is a **Verb Graph** where verbs declare their dependencies, inputs, outputs,
and side effects:

```java
// Current: verb is a flat keyword
"CHECK BOM" → execute(ctx, bomId) → VerbResult<BomCheckPayload>

// Future: verb is a node in a knowledge graph
@Verb(name = "CHECK BOM",
      inputs = {"bomId"},
      outputs = {"buyCount", "makeCount", "phantomCount"},
      dependsOn = {},          // read-only, no dependencies
      usedBy = {"COMPOSE BUILDING", "EXPORT IFC"},
      idempotent = true,
      sideEffects = "none")
```

This enables: auto-generated dependency graphs, impact prediction ("changing this verb
affects 47 downstream verbs"), intelligent caching (idempotent + stable inputs), and
optimized execution order. The foundation is already in place — VerbRegistry's
longest-prefix dispatch and VerbResult's typed pass/fail make the current system
ready for graph annotation without breaking existing code.

---

## Chapter 10: Contributing — Join the Build

### "How do I add my country's building?"

The extension pattern is designed so that adding a new building type requires **zero Java code**. You add data + configuration, and the existing pipeline compiles it.

### The Extension Recipe

1. **Extract your IFC file** with IfcOpenShell → produces rows in `component_library.db`
2. **Add your building to `construction_manifest.yaml`:**

From `scripts/construction_manifest.yaml:17-34` (open in any text editor — YAML):

```yaml
buildings:
  Ifc4_SampleHouse:
    prefix: SH
    doc_type_id: RE_SH
    name: Sample House
    doc_sub_type: SH
    doc_base_type: RE
    description: "IFC4 Sample House — single-unit residential, 1-storey"
    provenance: EXTRACTED
    expected_elements: 55
    output_path: DAGCompiler/lib/output/ifc4_samplehouse.db
    reference_path: DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db
    building_bom_id: BUILDING_SH_STD
    seq_no: 10
    storeys:
      Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
      Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
```

3. **Regenerate BOM dictionary:** `./scripts/run_RosettaStones.sh classify_sh.yaml` (SH/DX via IFCtoBOM Java) or `python scripts/RosettaStoneToBOM.py` (TE legacy)
4. **Run the gates:** `./scripts/run_tests.sh`

That's it. The pipeline discovers your building from the manifest, the extraction script creates its BOMs, and the compiler compiles it. The gates verify it didn't drift.

[Figure 10.1] *Placeholder — Screenshot of construction_manifest.yaml in editor showing the structure*

### Verb Development Checklist

If you're adding a new verb to BIM COBOL:

1. **Write the witness test first** — what should this verb produce? What's the expected output?
2. **Implement `Verb<T>`** — keyword, execute, payload record
3. **Register in `VerbRegistry.createDefault()`** — one line: `reg.register(new MyVerb())`
4. **Run gates** — `./scripts/run_tests.sh` must stay GREEN
5. **Update seal** — if test files changed, update the hash seal

> **Watch It!** The verb must set `EntityType=U` on any records it creates. Dictionary records (EntityType=D) are sacrosanct — they come from extraction scripts, not from verbs.

### The School Typology — A Worked Example

The project roadmap includes a school building type as the next major milestone. This is the model for community contributions: a building type relevant to developing countries, using the same pipeline but with different BOM structures and room types.

See `docs/ACTION_ROADMAP.md` for the full mission phases.

### The FOSS Impact Vision

This project targets:
- **UNESCO developing-country school construction** — deterministic building models for standardized school designs
- **Affordable housing** — BOM-driven cost estimation from design intent
- **Community verification** — anyone can run the gates, anyone can prove non-drift

The pipeline is FOSS (GPL v2, compatible with iDempiere/Bonsai). The BOM data is declarative (YAML + SQL). The verification is mathematical (SHA-256 digests, containment proofs, cross-database checks). No proprietary tools. No cloud dependencies. One laptop, one `./scripts/run_tests.sh`.

> **Further reading:**
> - Roadmap and mission phases: `docs/ACTION_ROADMAP.md`
> - School typology design: project milestone docs
> - BIM Designer GUI concept: `docs/BIM_Designer.md`

---

## Glossary

| Term | Definition |
|------|-----------|
| **AABB** | Axis-Aligned Bounding Box — the rectangular envelope around a building element, measured in mm |
| **Anti-Drift** | The policy that no data in the output may be invented — everything traces to extraction or deterministic computation |
| **BasePO** | Base Persistent Object — the ORM foundation class providing load/save/delete against SQLite |
| **BIM** | Building Information Modeling — digital representation of a building's physical and functional characteristics |
| **BIM COBOL** | The domain-specific verb language for construction mutations — named for its imperative, keyword-driven style |
| **BOM** | Bill of Materials — hierarchical tree of parts, sub-assemblies, and raw materials |
| **{PREFIX}_BOM.db** | The per-building dictionary database — BOMs, products, categories, building registrations (read-only at compile time) |
| **LEAF** | Component type: full LOD exists in component_library.db (leaf element with real geometry) |
| **C_DocType** | iDempiere document type — encodes building type identity (DocBaseType + DocSubType) |
| **C_Order** | iDempiere order — represents one building instance in output.db |
| **CO_EmptySpace** | WMS (Warehouse Management) acceptance record — tracks whether the compiled building fits its declared envelope |
| **component_library.db** | The geometry oracle — meshes, materials, dimensions extracted by IfcOpenShell (read-only) |
| **CompilerStage** | Interface for pipeline stages — execute(), name(), shouldSkip() |
| **DSL** | Domain-Specific Language — the building definition text parsed by Stage 2 |
| **EN-BLOC** | Compilation mode where the entire BOM tree is compiled in one pass |
| **EntityType** | Data provenance marker: D=Dictionary (read-only), U=User (verb-created), A=Application |
| **G1-G6** | The six verification gates in RosettaStoneGateTest |
| **GodMode** | Override mechanism: placing `GodMode.txt` in the working directory bypasses EntityType guards |
| **IFC** | Industry Foundation Classes (ISO 16739) — the universal building data exchange standard |
| **LOD** | Level of Detail/Development — geometric mesh resolution |
| **M_Product** | iDempiere product — a catalog entry with dimensions, IFC class, and material references |
| **MAKE** | Component type: LOD to be created on-the-fly (future — not used in current data) |
| **PHANTOM** | Component type: gap filler between furniture items — present in `{PREFIX}_BOM.db`, stripped at output |
| **PO** | Persistent Object — iDempiere's ORM pattern (column map + dirty tracking + transaction ownership) |
| **Prime Rule** | "Extract or Compile Only" — the project's cardinal rule against data invention |
| **Rosetta Stone** | A reference building with known-good IFC data used for verification |
| **SPI** | Service Provider Interface — Java's ServiceLoader mechanism for decoupled module discovery |
| **Tack Convention** | Left-Front-Down = (0,0,0). All offsets (dx/dy/dz) are non-negative from the tack point |
| **Tack Point** | The LFD corner of a bounding box — the origin for offset calculations |
| **VerbResult<T>** | Typed pass/fail result from verb execution — carries keyword, summary, and payload |
| **WALK-THRU** | Compilation mode where AABB matching selects best-fit BOMs level by level (template path) |
| **Witness** | A test that proves a verb produces correct output — the verb's "proof of work" |
| **X_/M_ Convention** | Two-layer PO: X_ (generated structure) + M_ (business logic) |

---

## References

1. Oon, R. D. (2026). "Comparative Analysis: Intent-Driven BIM Generation Approaches." Working paper. `docs/IntentBIMChallengePaper.pdf`

2. ISO 16739-1:2024. *Industry Foundation Classes (IFC) for data sharing in the construction and facility management industries.*

3. iDempiere ERP — Enterprise Resource Planning. https://www.idempiere.org

4. IfcOpenShell — Open source IFC toolkit. https://ifcopenshell.org

5. Bonsai (BlenderBIM) — Open source BIM authoring. https://bonsaibim.org

---

## Document Map — Where to Go Next

| If you want to... | Read this |
|-------------------|-----------|
| Understand the full compilation spec | `docs/BOMBasedCompilation.md` |
| See the ERP-to-construction mapping | `docs/ConstructionAsERP.md` |
| Learn the 3-DB schema in detail | `docs/DATA_MODEL.md` |
| Read the BIM COBOL verb catalog | `docs/BIM_COBOL.md` |
| Understand test architecture & anti-drift | `docs/TestArchitecture.md` |
| See the project roadmap | `docs/ACTION_ROADMAP.md` |
| Browse the interactive architecture diagram | `docs/bim_architecture_viz.html` |
| Understand prefab assembly hierarchy | `docs/PREFAB_ARCHITECTURE.md` |
| Read the challenge paper | `docs/IntentBIMChallengePaper.pdf` |
| Learn about the GUI concept | `docs/BIM_Designer.md` |
| Read the Rosetta Stone strategy | `docs/TheRosettaStoneStrategy.txt` |
| Understand report engine design | `docs/ReportEngine.md` |

---

## Appendix A: Code Walkthrough Cheatsheet — From IFC to Verified Output

This appendix is a step-by-step Eclipse walkthrough. Open each file, verify the logic, confirm the data chain. One pass through this sequence and you can prove the compiler doesn't drift.

> **Eclipse setup:** Link `scripts/` into DAGCompiler first (right-click DAGCompiler → New → Folder → Advanced → Link to `/home/red1/bim-compiler/scripts`). Then `Ctrl+Shift+R` finds everything.

---

### PHASE 1: Data Birth — Pipeline Scripts Build `{PREFIX}_BOM.db`

The data chain starts here. Every row in `{PREFIX}_BOM.db` traces to one of these scripts reading from `construction_manifest.yaml` or `component_library.db`. No row is invented.

---

#### Step 1.1 — The Manifest: What Buildings Exist

**Open:** `scripts/construction_manifest.yaml`

[Figure A.1] *Placeholder — Eclipse editor showing construction_manifest.yaml*

This YAML file declares building identity — names, prefixes, paths, doc types. It **never** declares derived values (no counts, no dimensions, no AABB, no offsets).

**What to verify:**
- Line 17: `Ifc4_SampleHouse` entry — `prefix: SH`, `doc_type_id: RE_SH`, `provenance: EXTRACTED`
- Line 36: `Ifc2x3_Duplex` entry — `prefix: DX`, `doc_type_id: RE_DX`, `expected_elements: 1099`
- Line 32-34: `storeys` sub-block — each storey has a `code`, `bom_category`, `role`, and `seq`. These drive floor BOM creation in the extraction step.

**Anti-drift check:** No dimensions anywhere. AABB comes from extraction (Step 1.3). Element counts are TODO-marked as "derive from extraction." The manifest is pure identity.

---

#### Step 1.2 — The Master Builder: IFCtoBOM Java Pipeline (SH/DX) / RosettaStoneToBOM.py (TE)

**For SH/DX:** Open `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — the Java orchestrator.
**For TE (legacy):** Open `scripts/RosettaStoneToBOM.py`

[Figure A.2] *Placeholder — Eclipse editor showing IFCtoBOMPipeline.java or RosettaStoneToBOM.py*

Jump to **line 888** — the `main()` function. This is the 8-step build sequence:

```python
def main():                          # line 888
    create_schema()                  # [1/8] DDL from schema_snapshot_bom.sql
    conn = sqlite3.connect(BOM_DB)
    populate_reference(conn)         # [2/8] C_DocType rows from manifest
    populate_products(conn)          # [3/8] M_Product catalog
    populate_boms(conn)              # [4/8] m_bom + m_bom_line + m_attribute
    run_extraction(conn)             # [5/8] calls RosettaStoneExtract.py
    run_ad_scripts()                 # [6/8] AD config tables (wall types, etc.)
    counts = validate(conn)          # [7/8] referential integrity check
    print_summary(counts)            # [8/8] final row counts
```

**Key functions to inspect:**

| Function | Line | What It Does | `{PREFIX}_BOM.db` Table |
|----------|------|-------------|-------------|
| `_build_c_doctype()` | 62 | Reads `construction_manifest.yaml` → builds C_DocType rows | `C_DocType` |
| `populate_reference()` | 754 | Inserts C_DocType, M_BomCategory, C_BPartner | `C_DocType`, `M_BomCategory`, `C_BPartner` |
| `populate_products()` | 786 | Inserts M_Product catalog (dimensions from IFC extraction) | `M_Product` |
| `populate_boms()` | 798 | Inserts static m_bom headers + m_bom_line children + m_attribute params | `m_bom`, `m_bom_line`, `m_attribute` |
| `run_extraction()` | 829 | Calls `RosettaStoneExtract.extract_all()` — IFC elements → floor BOMs | `m_bom`, `m_bom_line` |
| `validate()` | 862 | Cross-checks: no negative offsets, row counts | (read-only verification) |

**Anti-drift check at line 62:** `_build_c_doctype()` reads YAML fields directly — `cfg['doc_type_id']`, `cfg['name']`, `cfg['doc_base_type']`. No computation, no defaults for critical fields. AABB fields are explicitly `None` (line 86-88) — they will be filled by extraction, not by this script.

---

#### Step 1.3 — The Extraction: IFC Elements → BOM Lines

**Open:** `scripts/RosettaStoneExtract.py`

[Figure A.3] *Placeholder — Eclipse editor showing RosettaStoneExtract.py, scrolled to tack offset computation*

Jump to **line 50** — `extract_building()`. This is where IFC geometry becomes BOM data.

**The data source** (line 63-70) — reads from `component_library.db`, the oracle we didn't write:

```python
elements = comp_conn.execute("""
    SELECT storey, ifc_class, element_ref, ordinal,
           min_x, max_x, min_y, max_y, min_z, max_z,
           orientation, material_name, material_rgba, M_Product_ID
    FROM I_Element_Extraction
    WHERE building_type = ?
""", (building_type,)).fetchall()
```

**Building origin** (line 77-84) — computed from actual element extents, not hardcoded:

```python
all_min_x = min(e[4] for e in elements)   # min of all min_x
all_min_y = min(e[6] for e in elements)   # min of all min_y
all_min_z = min(e[8] for e in elements)   # min of all min_z
origin = (all_min_x, all_min_y, all_min_z)
aabb_w = (all_max_x - all_min_x) * 1000  # meters → mm
```

**The tack offset** (line 167-169) — the critical non-drift computation:

```python
dx = (min_x + max_x) / 2 - floor_origin[0]   # centroid - floor origin
dy = (min_y + max_y) / 2 - floor_origin[1]
dz = (min_z + max_z) / 2 - floor_origin[2]
```

**Anti-drift proof:** Every number in this computation comes from `I_Element_Extraction` — a table populated by IfcOpenShell (external tool). The origin is `min()` of actual coordinates. The offset is centroid minus origin. Pure arithmetic on external data. Nothing invented.

**What gets written** (line 180-199) — one `m_bom_line` row per IFC element:

```python
bom_conn.execute("""
    INSERT INTO m_bom_line
    (bom_id, child_product_id, component_type, role, sequence,
     rotation_rule, ..., dx, dy, dz, ...,
     allocated_width_mm, allocated_depth_mm, allocated_height_mm,
     storey, element_ref, ordinal, orientation,
     material_name, material_rgba)
    VALUES (?, ?, 'BUY', ?, ?, ...)
""", (floor_bom_id, m_product_id, ifc_class, seq,
      rotation_rule, dx, dy, dz,
      alloc_w, alloc_d, alloc_h, ...))
```

Every column traces: `m_product_id` from `I_Element_Extraction.M_Product_ID`. `dx/dy/dz` from centroid arithmetic. `alloc_w/d/h` from `(max - min) * 1000`. `material_name` and `material_rgba` from `I_Element_Extraction` columns. `entity_type = 'D'` (Dictionary — read-only).

---

#### Step 1.4 — Verify `{PREFIX}_BOM.db`: What's Actually in There

**Run in terminal** (or use DB Browser for SQLite):

```bash
sqlite3 library/SH_BOM.db "SELECT bom_id, bom_type, bom_category, doc_sub_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm
    FROM m_bom WHERE bom_type='BUILDING' ORDER BY bom_id"
```

Expected output — AABB values filled by extraction, not hardcoded:

```
BUILDING_DX_STD|BUILDING||DX|9215|26565|7885
BUILDING_SH_STD|BUILDING||SH|16868|8668|3945
BUILDING_TE_STD|BUILDING||TE|...
```

```bash
sqlite3 library/SH_BOM.db "SELECT COUNT(*), bom_id FROM m_bom_line
    WHERE entity_type='D' GROUP BY bom_id ORDER BY COUNT(*) DESC LIMIT 5"
```

Shows extraction element counts per floor BOM — these must match the IFC reference.

[Figure A.4] *Placeholder — Terminal showing `{PREFIX}_BOM.db` query results*

---

### PHASE 2: Compilation — Java Pipeline Reads `{PREFIX}_BOM.db`

Now the Java compiler reads what the pipeline wrote. The compiler never modifies `{PREFIX}_BOM.db` — it's read-only.

---

#### Step 2.1 — Building Discovery: BuildingRegistry

**Open:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/BuildingRegistry.java`

[Figure A.5] *Placeholder — Eclipse editor showing BuildingRegistry.java*

Jump to **line 70** — `loadActive()`:

```java
public static List<BuildingEntry> loadActive() {
    return load("WHERE IsActive = 1 ORDER BY SeqNo");
}
```

This reads `C_DocType` from `{PREFIX}_BOM.db` — the same rows that the pipeline inserted in Step 1.2. The `BuildingEntry` record (line 24-41) carries `dslContent`, `outputDbPath`, `referenceDbPath`, `expectedElements`, `aabbWidthMm/Depth/Height` — all from the database, none hardcoded.

**Anti-drift check:** `BuildingRegistry` has zero constants for building data. Everything comes from `SELECT ... FROM C_DocType`. The Java code is a pure reader.

---

#### Step 2.2 — The Pipeline: 9 Stages in Sequence

**Open:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`

[Figure A.6] *Placeholder — Eclipse editor showing CompilationPipeline.java line 51*

Jump to **line 51** — the stage list:

```java
private static final List<CompilerStage> STAGES = List.of(
    new MetadataValidator(),  // 1 — referential integrity (pre-flight)
    new ParseStage(),         // 2 — DSL text → BuildingDefinition
    new CompileStage(),       // 3 — BOM walk + geometry → BuildingSpec [SKIP for CO]
    new TemplateStage(),      // 4 — ST mode only (AABB-fit selection) [SKIP for non-ST]
    new WriteStage(),         // 5 — BuildingSpec → output.db tables
    new VerbStage(),          // 6 — BIM COBOL script hook [SKIP if no .bimcobol]
    new DigestStage(),        // 7 — SHA-256 spatial fingerprint
    new GeometryStage(),      // 8 — mesh integrity validation
    new ProveStage()          // 9 — mathematical placement proofs
);
```

Three stages have `shouldSkip()` guards: **CompileStage** (CO mode — BOM is source of truth), **TemplateStage** (non-ST — only template buildings need catalog selection), **VerbStage** (no `.bimcobol` script file).

Jump to **line 67** — `run()`, the pipeline loop:

```java
public static PipelineResult run(BuildingEntry entry) throws Exception {
    CompilationContext ctx = new CompilationContext(entry);
    for (int i = 0; i < STAGES.size(); i++) {
        CompilerStage stage = STAGES.get(i);
        if (stage.shouldSkip(ctx)) { continue; }
        stage.execute(ctx);
    }
    return ctx.toResult();
}
```

**Anti-drift check:** The pipeline is a pure function: `BuildingEntry` in → `PipelineResult` out. No global state. No side effects except writing to `output.db` (a fresh file, deleted and recreated at line 234). `PipelineResult` (line 41-49) carries `elementCount`, `spatialDigest`, `proofs`, `geometryReport` — all computed, none cached.

---

#### Step 2.3 — Stage 1: MetadataValidator — Pre-Flight Integrity

**Open:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/MetadataValidator.java`

[Figure A.7] *Placeholder — Eclipse editor showing MetadataValidator.java*

Jump to **line 91** — `checkBomChain()`:

```java
int bomD = queryInt(conn,
    "SELECT COUNT(*) FROM m_bom_line bc " +
    "LEFT JOIN m_bom b ON bc.bom_id = b.bom_id " +
    "WHERE b.bom_id IS NULL AND bc.is_active = 1");
if (bomD > 0) errors.add("m_bom_line.bom_id: " + bomD + " dangling refs");
```

This catches broken foreign keys *before* compilation starts. If `RosettaStoneToBOM.py` produced orphan rows, this gate stops everything.

Jump to **line 195** — `checkBomLeafGeometry()` — the NO FALLBACK gate:

Every LEAF product in the BOM must have a matching mesh in `component_library.db`. No fallback. No placeholder geometry. If the mesh doesn't exist, compilation fails here — not silently at output time.

---

#### Step 2.4 — Stage 3: CompileStage — BOM Tree Walk

**Open:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`

**Two code paths exist here depending on DocBaseType:**

**RE mode (Residential — SH, DX):** CompileStage calls `BuildingCompiler.compileWithValidation()` which invokes `StoreyCompiler.compileStorey()` per storey. This is the DSL compilation path — it parses the building definition, computes room layouts, generates walls/slabs/openings, and produces a full `BuildingSpec`.

**CO mode (Commercial — TE):** CompileStage is **skipped entirely** (`shouldSkip()` returns true). Jump to **line 234**:

```java
@Override
public boolean shouldSkip(CompilationContext ctx) {
    if ("CO".equals(ctx.entry().docBaseType())) {
        ctx.setSpec(new BuildingSpec(ctx.entry().projectName(), List.of(), null));
        return true;
    }
    return false;
}
```

The minimal `BuildingSpec` has the building name (for `PlacementLoader` lookup) but empty storeys and no roof. WriteStage (Stage 5) then emits all 48,428 elements through `emitGlobalPlacementElements()` — the BOM-driven extracted placement path.

**Why CO skips compilation:** StoreyCompiler generates structural slabs from computed bay dimensions and calls `PlacementLoader.markConsumed(buildingName, elementRef)` at `StoreyCompiler.java:2101`. The `elementRef` is a product type name (e.g. `Floor:S_Slab_200_RC_Flat_V1`) — not unique per element. Consuming one product name consumes all 189 elements of that type. When `BuildingWriter.emitGlobalPlacementElements()` checks `isConsumed()` at line 959, it silently drops those slabs. This caused the 216 IfcSlab gap (48,212 vs 48,428).

**YAML source:** `IFCtoBOM/src/main/resources/classify_te.yaml:8`:
```yaml
doc_base_type: CO    # triggers CompileStage.shouldSkip()
```

**IDE verification chain (open each file, jump to line):**

| What | File | Line |
|------|------|------|
| Skip guard | `CompilationPipeline.java` | 234 |
| DocBaseType on entry | `BuildingRegistry.java` | 28 |
| YAML declaration | `classify_te.yaml` | 8 |
| markConsumed (root cause) | `StoreyCompiler.java` | 2101 |
| isConsumed check | `BuildingWriter.java` | 959 |
| Consumption registry | `PlacementLoader.java` | 77-83 |

**For RE mode** (SH, DX), open `DAGCompiler/src/main/java/com/bim/compiler/library/BOMTreeLoader.java`. Jump to **line 134** — `load()`:

```java
public static Map<String, BOMNode> load(String bomDbPath, String... bomIds)
```

This loads the entire BOM tree from `{PREFIX}_BOM.db` into memory. The key data structure is `BOMChild` (line 62) — one record per `m_bom_line` row, enriched with `m_attribute` params via the 3-table authority rule (line 176-179).

**Anti-drift check at line 189-194:** The `dx/dy/dz` values come from either `m_attribute` params (if present) or `m_bom_line` columns (fallback) — both written by `RosettaStoneExtract.py` from IFC extraction data. The Java code reads them; it never computes new offsets.

---

#### Step 2.5 — Stage 5: WriteStage — Output.db Emission

**Open:** `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java`

Jump to **line 226** — `WriteStage.execute()`:

```java
String outputDbPath = ctx.entry().outputDbPath();
File outputFile = new File(outputDbPath);
outputFile.delete();  // ← fresh every time, no stale data
```

The output database is **deleted and recreated** every compilation. No carry-over. No stale elements. Then `BuildingWriter.write(spec)` serializes the `BuildingSpec` to `elements_meta`, `base_geometries`, `element_instances`, and R*Tree spatial index.

**Anti-drift check:** Line 249 counts elements after write:

```java
ctx.setElementCount(queryInt(conn, "SELECT COUNT(*) FROM elements_meta"));
```

This count is what G1-COUNT will verify against the reference database.

---

### PHASE 3: Verification — Proving Non-Drift

The compiler has written output.db. Now we prove it matches the IFC reference.

---

#### Step 3.1 — Gate G1-COUNT: Element Count Match

**Open:** `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java`

[Figure A.9] *Placeholder — Eclipse editor showing RosettaStoneGateTest.java*

Jump to **line 83** — `runG1()`:

```java
int refCount = countElements(b.referenceDbPath());   // from IFC extraction oracle
int outCount = countElements(b.outputDbPath());      // from our compilation
assertEquals(refCount, outCount, ...);
```

`referenceDbPath` points to `Ifc4_SampleHouse_extracted.db` — the IfcOpenShell extraction output we didn't write. `outputDbPath` points to `ifc4_samplehouse.db` — our compilation output. If `refCount != outCount`, an element was invented or lost. Test fails.

---

#### Step 3.2 — Gate G3-DIGEST: Per-Element Spatial Hash

Same file, the G3 gate computes SHA-256 over every element's (guid, ifc_class, x, y, z, dx, dy, dz) in both databases. If even one element's placement differs by a single coordinate, the digests diverge and the gate fails.

This is the definitive non-drift proof: identical spatial fingerprints between independent extraction (IfcOpenShell) and our compilation (Java pipeline).

---

#### Step 3.3 — Gate G4-TAMPER: Self-Inspection

Same file. G4 scans the project's own source code for suspicious patterns:
- `@Disabled` on test methods (suppressed tests)
- Hardcoded coordinate values in pipeline code
- `assertEquals(n, n)` tautologies (tests that always pass)
- TODO markers in gate logic

If we cheat, G4 catches us.

---

#### Step 3.4 — Cross-Database Integrity: DataIntegrityTest

**Open:** `DAGCompiler/src/test/java/com/bim/compiler/contract/DataIntegrityTest.java`

[Figure A.10] *Placeholder — Eclipse editor showing DataIntegrityTest.java*

Jump to **line 59-63** — the setup attaches both databases:

```java
conn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB);
conn.createStatement().execute(
    "ATTACH DATABASE '" + COMP_DB + "' AS lod");
```

This lets a single query cross-join `{PREFIX}_BOM.db` against `component_library.db`. D-1 (line 76-91) checks that every LEAF product in the BOM exists in the extraction oracle's M_Product table. If someone inserted a fake product into `{PREFIX}_BOM.db`, D-1 catches it.

---

#### Step 3.5 — Run the Full Gate

**Run in terminal:**

```bash
./scripts/run_tests.sh
```

[Figure A.11] *Placeholder — Terminal showing full gate output with all GREEN*

This compiles all buildings, runs all 6 gates, all D-1..D-5 integrity checks, and all structural guards. If the output ends with:

```
BUILD SUCCESS
```

...then every element in every output database traces to a library input, matches the IFC reference, and passed mathematical placement proofs. The compiler didn't drift.

---

### Quick Reference: The Complete Data Chain

```
classify_sh.yaml / classify_dx.yaml ← IDENTITY (human-authored, declarative)
        ↓
IFCtoBOMPipeline.java               ← BUILDER (SH/DX — reads YAML + extraction)
        ↓                              (TE legacy: RosettaStoneToBOM.py)
    ├── ExtractionPopulator          → I_Element_Extraction from reference DB + geometry gaps
    ├── ExtractionReader             → reads I_Element_Extraction from component_library.db
    ├── ProductRegistrar             → M_Product master in component_library.db + copy to BOM DB
    ├── StructuralBomBuilder         → BUILDING + FLOOR m_bom + BUY m_bom_line
    ├── ScopeBomBuilder              → scope space assignment
    ├── CompositionBomBuilder        → mirror partition (DX)
    ├── FloorRoomBomBuilder          → YAML-driven room BOMs
    ├── BomValidator                 → pre-commit QA (9 checks — any FAIL = rollback)
    └── IntegrityHash                → SHA-256 fingerprint
                    ↓
                {PREFIX}_BOM.db         ← DICTIONARY (per-building, reproducible)
                    ↓
            BuildingRegistry.loadActive()   ← DISCOVERY (reads C_DocType)
                    ↓
            CompilationPipeline.run()       ← COMPILER (9 stages)
                ├── MetadataValidator       → pre-flight integrity
                ├── ParseStage              → DSL → BuildingDefinition
                ├── CompileStage            → BOM walk → BuildingSpec
                ├── WriteStage              → BuildingSpec → output.db
                ├── DigestStage             → SHA-256 spatial fingerprint
                └── ProveStage              → mathematical placement proofs
                        ↓
                    output.db               ← COMPILED (fresh each run)
                        ↓
                RosettaStoneGateTest         ← VERIFICATION
                    ├── G1-COUNT            → ref count == out count
                    ├── G2-VOLUME           → ref volume ≈ out volume
                    ├── G3-DIGEST           → SHA-256 spatial match
                    ├── G4-TAMPER           → source self-inspection
                    ├── G5-PROVENANCE       → every element → library
                    └── G6-ISOLATION        → no stowaway elements
                        ↓
                    BUILD SUCCESS           ← PROOF: zero drift
```

---

## Appendix A: Quality Verification Guide

This appendix documents how to verify pipeline quality via code and data examination.
All checks are abstract — they apply to any Rosetta Stone building (SH, DX, TE).

### A.1 Rosetta Stone Sameness Principle

**The geometry guarantee is coordinate sameness, not mesh shape verification.**

The extraction oracle (`component_library.db`) contains meshes extracted by IfcOpenShell.
The compiler reads these meshes and places them at coordinates computed from the BOM tree.
The gates verify that **input coordinates = output coordinates** (G1-COUNT, G2-VOLUME,
G3-DIGEST). If coordinate sameness holds, the visual output is WYSIWYG — mesh shapes
are inherited from the oracle, never invented or transformed beyond scale+translate+rotate.

No separate mesh shape check is needed because:
1. Meshes come from `component_library.db` (external oracle, not our code)
2. `geometry_hash` in output traces 1:1 to library mesh (G5 Check 2)
3. LOD_ prefix proves library provenance (G5 Check 6)
4. Coordinate sameness proves placement fidelity (G3-DIGEST)

### A.2 BOM Dictionary Abstraction — SQL Verification

Run against any `*_BOM.db` to verify it's a proper abstract dictionary:

```sql
-- All origins must be (0,0,0) — no world coordinates in dictionary
SELECT bom_id, origin_x, origin_y, origin_z FROM m_bom
  WHERE origin_x != 0.0 OR origin_y != 0.0 OR origin_z != 0.0;
-- Expected: empty result

-- All entities must be 'D' (Dictionary = read-only at compile time)
SELECT bom_id, entity_type FROM m_bom WHERE entity_type != 'D';
SELECT bom_id, child_product_id, entity_type FROM m_bom_line WHERE entity_type != 'D';
-- Expected: empty result

-- Offsets must be parent-relative (no world-absolute > 100m)
SELECT bom_id, child_product_id, dx, dy, dz FROM m_bom_line
  WHERE ABS(dx) > 100 OR ABS(dy) > 100 OR ABS(dz) > 100;
-- Expected: empty result

-- No duplicate products
SELECT m_product_id, COUNT(*) c FROM M_Product GROUP BY m_product_id HAVING c > 1;
-- Expected: empty result

-- Hierarchy check: BUILDING → FLOOR → SET
SELECT bom_type, COUNT(*) FROM m_bom GROUP BY bom_type;
-- Expected: BUILDING=1, FLOOR=N, SET=M
```

### A.3 Verb/DAO Enforcement — Tamper Rules

The G4-TAMPER gate enforces 17 rules. The critical ones for data integrity:

| Rule | What It Blocks | How to Verify |
|------|---------------|---------------|
| **T16** | Raw SQL on `m_bom`, `m_bom_line`, `c_order`, `wm_empty_storage_line` | `grep -rn 'INSERT.*INTO\|UPDATE\|DELETE.*FROM' DAGCompiler/src/main/ --include='*.java' \| grep -i 'm_bom\|c_order\|wm_empty_storage'` — must be empty |
| **T17** | Raw UPDATE on `elements_rtree`, `elements_meta`, `element_instances` | Same grep, excluding `ElementPersistence.java` (authorized gateway) |

All mutations on protected tables go through:
- **Tier 1 (verbs):** `BIM_COBOL/src/main/java/.../verb/` — VerbExecutor SPI
- **Tier 2 (compiler output):** `ElementPersistence.java` — authorized UPDATE gateway
- **Tier 3 (dictionary construction):** `IFCtoBOM/` — different lifecycle, pre-verb

EntityType guards in `X_M_BOM.beforeSave()` and `MBOMLine.beforeSave()` prevent
mutation of Dictionary (D) entities. GodMode.txt bypass is file-based and gitignored.

### A.4 Geometry WYSIWYG — Output Verification

Run against any `output.db` to verify geometry integrity:

```sql
-- Every element has a geometry link
SELECT COUNT(*) FROM element_instances
  WHERE geometry_hash IS NULL OR geometry_hash = '';
-- Expected: 0

-- Every geometry link resolves to a real mesh
SELECT COUNT(*) FROM element_instances ei
  WHERE NOT EXISTS (SELECT 1 FROM base_geometries bg
    WHERE bg.geometry_hash = ei.geometry_hash);
-- Expected: 0

-- No degenerate meshes (vertex_count < 8 = less than a box)
SELECT COUNT(*) FROM base_geometries WHERE vertex_count < 8;
-- Expected: 0

-- Library mesh prevalence: LOD_ = real mesh, GEO_ = parametric fallback
SELECT CASE WHEN geometry_hash LIKE 'LOD_%' THEN 'LOD (library)'
            WHEN geometry_hash LIKE 'GEO_%' THEN 'GEO (parametric)'
            ELSE 'OTHER' END AS source,
       COUNT(*) FROM element_instances GROUP BY source;
-- Expected: LOD = all or nearly all, GEO = 0 or minimal

-- Material coverage
SELECT COUNT(*) as total,
       SUM(CASE WHEN material_rgba IS NOT NULL AND material_rgba != ''
           THEN 1 ELSE 0 END) as with_material
FROM elements_meta;
-- Coverage varies by IFC source richness (SH ~93%, DX ~13%, TE ~81%)

-- Surface styles populated
SELECT COUNT(*) FROM surface_styles;
-- Expected: > 0 when materials exist in elements_meta
```

### A.5 G5-PROVENANCE Gate — 7 Abstract Checks

G5 runs per building via `DynamicTest` — no building-specific code:

| Check | What | Failure Means |
|-------|------|--------------|
| 1 | `material_rgba` coverage >= reference | Materials lost during compilation |
| 2 | Every `element_instance` → `base_geometries` FK | Orphan geometry (invented?) |
| 3 | `vertex_count >= 8` on all geometries | Degenerate mesh (less than a box) |
| 4 | No null/empty `geometry_hash` | Missing geometry binding |
| 5 | `ifc_class` in known whitelist | Unknown element type (invented?) |
| 6 | No `GEO_*` hash prefix | Parametric BBox fallback (no library mesh) |
| 7 | `surface_styles` populated when materials exist | Material library not written |

### A.6 Delta Test — Dual-Path Convergence

The strongest abstract guarantee: two independent compilation paths must produce
identical output. If EN-BLOC (data proof) = WALK-THRU (mechanism proof), both
the data and the compiler are correct.

```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH: 55=55, delta=0
./scripts/run_RosettaStones.sh classify_dx.yaml   # DX: 1099=1099, delta=0
```

The delta test verifies per building (abstract, not dedicated):
- Per-IFC-class element count match
- AABB centroid delta (top 10 worst, must be < 0.5mm)
- `geometry_hash` match (0 divergences)
- Rule 8: all `m_bom_line` coordinates parent-relative (not world-absolute)
- Clash check: 0 furniture AABB overlaps

### A.7 Cross-Database Spot Check

`C2SpotCheckContractTest` queries both extraction reference and compiled output
for the same element, asserting coordinate + material match within 1mm:

```java
// No hardcoded values — live reference DB queries
assertEquals(refCoords, outCoords, 0.001, "coordinate mismatch");
assertEquals(refMaterial, outMaterial, "material_rgba must match");
```

5 spot checks (SH): IfcSlab, IfcDoor, IfcWindow, Piano (material), IfcWall (north).
DX spot checks: pending (future session).

---

*Generated by Anthropic Claude Code Opus 4.6 — March 2026*
*From source code analysis of the BIM Intent Compiler repository*
