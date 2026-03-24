# BIM Intent Compiler — Source Code Guide

> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md) · [ACTION_ROADMAP](ACTION_ROADMAP.md)

**Version 3.0**

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

In 2010 he authored *[Open Source ERP](https://www.amazon.com/Open-Source-ERP-Redhuan-Oon/dp/9673490228)* (Pearson Malaysia) — the first book of its kind worldwide — covering ERP architecture, virtual community building, and the ADempiere ecosystem for developers, integrators, and academics.

That two-decade immersion in iDempiere's Manufacturing BOM module, document-type architecture, and Application Dictionary pattern is the direct domain expertise behind this project's design. The BOM tree is iDempiere's `M_BOM`. The building types are `C_DocType`. The PO layer follows iDempiere's `X_`/`M_` convention. The insight: **a building is a manufactured product — its BOM is its complete definition**.

---

## Abstract

The AEC industry has a productivity problem. This project takes a fifth path — **metadata-driven compilation** — a deterministic DSL-to-IFC compiler validated against Rosetta Stone reference buildings. Every output element traces to a library input. Nothing is invented.

The Prime Rule ("Extract or Compile Only") is enforced by 6 verification gates, 4 layers of test defense, and a tamper-detection seal. BIM COBOL — the domain-specific verb language — makes every mutation auditable.

> **Further reading:**
> - Challenge paper: `docs/IntentBIMChallengePaper.pdf`
> - Full compilation spec: `docs/BOMBasedCompilation.md`
> - Construction-as-ERP model: `docs/SystemContract.md`

---

## §1 — The Big Picture

**One-sentence thesis:** *Construction is manufacturing. A building IS its Bill of Materials.*

### The 4-Database Architecture

| Database | Analogy | Role | Mutability |
|----------|---------|------|------------|
| `*_BOM.db` | The **menu** | Per-building BOM dictionary. Built by IFCtoBOM pipeline | Read-only at compile time |
| `component_library.db` | The **pantry** | LOD catalog: product geometry, meshes, materials (21 tables) | Read-only (external tool output) |
| `disc_validation.db` | The **recipe book** | Discipline metadata: schedules, types, placement rules, alias cascade | Read-only (migration-seeded) |
| `output.db` | The **plated dish** | Compiled output: elements, instances, spatial structure | Written fresh each compile |

If the plate has anchovies that aren't on the menu or in the pantry — *that's drift*.

### The iDempiere Connection

iDempiere's Manufacturing BOM tables (`M_BOM`, `M_BOM_Line`, `M_Product`, `C_DocType`, `C_Order`) already encode the relationships between finished goods, sub-assemblies, and raw materials. A building is just a very large finished good.

> **Deep dive:** Schema details → [`DATA_MODEL.md`](DATA_MODEL.md) | iDempiere mapping → [`SystemContract.md`](SystemContract.md)

---

## §2 — Setting Up Your Workshop

### Prerequisites

| Tool | Version | Why |
|------|---------|-----|
| JDK | 17+ | Java source/target level |
| Maven | 3.8+ | Multi-module reactor build |
| SQLite3 | 3.x | Database inspection |
| Python | 3.10+ | `{PREFIX}_BOM.db` regeneration scripts (PyYAML, IfcOpenShell) |
| Git | 2.x | Version control + tamper detection (G4 gate reads git history) |

### Clone, Build, Verify

```bash
git clone https://github.com/red1oon/BIMCompiler.git
cd BIMCompiler
mvn compile -q
./scripts/run_tests.sh    # THIS is the proof
```

> **Eclipse import:** If you see red markers like `"docs cannot be resolved to a module"` — that's Javadoc `@see` tags with bare file paths. Eclipse treats `docs` as a Java module name. Use `@see <a href="docs/Foo.md">Foo</a>` instead.

### Module Dependency Chain

```
orm-core          (BasePO, ModelQuery — no dependencies)
    ↓
ORMSandbox        (PO classes: MBOM, MBOMLine, MProduct)
    ↓ ↘
    ↓  IFCtoBOM   (IFC extraction → BOM dictionary)
    ↓
DAGCompiler       (Pipeline, compiler, gates)
    ↓
BIM_COBOL         (Verb language — depends on DAGCompiler via SPI)

TopologyMaker     (Spatial topology — standalone)
2D_Layout         (Floor plan layout — standalone)

BIMBackOffice     (ERP back-end: reports, portfolio, sessions)
    ↑
BonsaiBIMDesigner (Blender addon bridge + GUI)
    ├── BIMBackOffice
    ├── DAGCompiler
    ├── BIM_COBOL
    └── ORMSandbox
```

The SPI (Service Provider Interface) between DAGCompiler and BIM_COBOL breaks the circular dependency. DAGCompiler defines `VerbExecutor`; BIM_COBOL provides `BimCobolVerbExecutor`. Java's `ServiceLoader` discovers it at runtime.

```xml
<!-- pom.xml — 9-module reactor -->
<modules>
    <module>orm-core</module>
    <module>ORMSandbox</module>
    <module>DAGCompiler</module>
    <module>2D_Layout</module>
    <module>TopologyMaker</module>
    <module>BIM_COBOL</module>
    <module>IFCtoBOM</module>
    <module>BIMBackOffice</module>
    <module>BonsaiBIMDesigner</module>
</modules>
```

### DAO Pattern (orm-core)

**Rule:** Use DAO for all new resolver code. Raw JDBC is legacy — permitted in production paths already committed, not in new code.

| Module | Package | When to use |
|--------|---------|-------------|
| `orm-core` | `com.bim.orm` | Shared PO base + query builder. Zero business logic. |
| `ORMSandbox` | `com.bim.ormsandbox` | Building inspector, preflight checks, standalone tools. |
| `TopologyMaker` | `com.bim.topology` | Typology/UBBL domain — its own PO layer. |

`DAGCompiler` may import `orm-core` (Phase 4c added this dep). It does NOT import ORMSandbox PO classes.

**ModelQuery usage:**

```java
// Load all m_bom_line rows for a given BOM (conn = {PREFIX}_BOM.db)
List<X_M_BOMLine> children = new ModelQuery<>(conn, X_M_BOMLine::new, X_M_BOMLine.Table_Name)
    .where("bom_id = ?", bomId)
    .orderBy("sequence ASC")
    .list();

// Load a product_dim by product_id (conn = {PREFIX}_BOM.db)
X_AdProductDim dim = new ModelQuery<>(conn, X_AdProductDim::new, X_AdProductDim.Table_Name)
    .where("product_id = ?", productId)
    .first();  // returns null if not found
```

**PO naming:**
- `X_` prefix — plain PO (column getters/setters, no business logic)
- `M_` prefix — domain model (adds factory methods, lifecycle, validation)
- Table_Name constant: `X_M_BOMLine.Table_Name = "m_bom_line"` (must match actual table)
- PK field: TEXT PK must be set explicitly before `save()` — `BasePO.isNewRecord` flag determines INSERT vs UPDATE

**BasePO trap:** `isNewRecord` is an explicit flag — not derived from PK presence. Always `markAsNew()` for new objects:

```java
X_M_BOMLine child = new X_M_BOMLine(conn);
child.setBomId("SOFA_AREA");
child.setSequence(1);
child.markAsNew();   // sets isNewRecord = true
child.save();        // → INSERT
```

---

## §3 — The Rosetta Stones

**Verification philosophy:** *Don't trust the compiler. Trust the stones.*

### Five Reference Buildings

| Stone | IFC | Elements | Type | Country |
|-------|-----|----------|------|---------|
| **SH** | IFC4 | 55 | Single-unit residential | UK |
| **FK** | IFC4 | 82 | Detached residential | Germany |
| **IN** | IFC2x3 | 699 | Institutional (ArchiCAD 11) | Germany |
| **DX** | IFC2x3 | 1,099 | Paired duplex, 2-storey | USA |
| **TE** | IFC2x3 | 48,428 | Airport terminal | Malaysia |

### The 6 Gates

| Gate | What |
|------|------|
| **G1-COUNT** | Element count match |
| **G2-VOLUME** | AABB volume match |
| **G3-DIGEST** | Per-element spatial hash |
| **G4-TAMPER** | Source code self-inspection |
| **G5-PROVENANCE** | Every element traced to library |
| **G6-ISOLATION** | No output elements outside BOM tree |

> **Gate specification:** [`TestArchitecture.md`](TestArchitecture.md) — tamper rules (T1-T16), 4-layer defense, hash seal, forensic guide.

---

## §4 — From IFC to Dictionary

The extraction pipeline: IFC file → `{PREFIX}_BOM.db` dictionary.

| Step | What | Tool |
|------|------|------|
| 1. Extract geometry | IFC → `component_library.db` | `tools/extract.py` (IfcOpenShell) |
| 2. Create products | Enriched extraction → M_Product catalog | `ExtractionPopulator` + `ProductRegistrar` |
| 3. Build BOM tree | BUILDING → FLOOR → ROOM hierarchy + tack offsets | `StructuralBomBuilder`, `ScopeBomBuilder`, `FloorRoomBomBuilder` |
| 4. Geometry resolution | 4-substep chain: blobs → links → products → images | `ComponentLibrary.resolveByProduct()` |

The tack offset is pure arithmetic: `dx = centroid_x - floor_origin_x`. No ML. No heuristics.

> **Deep dive:** Geometry resolution chain → [`DATA_MODEL.md`](DATA_MODEL.md) | IFC class authority table → [`DISC_VALIDATION_DB_SRS.md`](DISC_VALIDATION_DB_SRS.md) §5.2 | Infrastructure extraction → [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md)

### Mining Validation Rules

After a Rosetta Stone passes 10/10, observed patterns become `AD_Val_Rule` entries:

| Type | Example |
|------|---------|
| `DIMENSION` | Pier column 3499×4561×3780mm |
| `RATIO` | Footing/column width ≥ 1.78x |
| `Z_CONTINUITY` | Pier top ≈ deck bottom (≤100mm gap) |

Three-step process: (1) Query output DB for patterns, (2) Write append-only `migration/DV00N_*.sql`, (3) Apply to `validation.db`.

> **Mining methodology:** [`TE_MINING_RESULTS.md`](TE_MINING_RESULTS.md) | Bridge rules → [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) §7.1

---

## §5 — The BOM Tree

A building BOM is a hierarchy: BUILDING → FLOOR → ROOM/SET → ITEM (LEAF).

```
BUILDING_SH_STD              ← Finished good
├── FLOOR_SH_GF_STD          ← Ground Floor
│   ├── SH_LIVING_SET        ← Sub-assembly: Living Room
│   │   ├── Sofa             ← LEAF (real geometry)
│   │   └── [buffer]         ← PHANTOM (gap filler, stripped at output)
│   └── SH_BEDROOM_SET
├── SH_GF_STR                ← Structural elements
└── ROOF
```

### The Tack Convention

Every element has a **tack point** — Left-Back-Down (LBD) corner = (0,0,0) in its own frame. All `m_bom_line` dx/dy/dz values are tack_from positions.

| Component Type | Meaning | At Compile Time |
|----------------|---------|-----------------|
| **LEAF** | Full LOD in component_library.db | Emitted with real geometry |
| **MAKE** | Sub-assembly | Not used in current data |
| **PHANTOM** | Gap filler (buffer) | Stripped — never emitted |

> **Full spec:** [`BOMBasedCompilation.md`](BOMBasedCompilation.md) §4 — tack convention, BOMChild record, 3-table authority rule, cascade placement.

---

## §6 — The 9-Stage Pipeline

```java
List<CompilerStage> STAGES = List.of(
    new MetadataValidator(),  // 1 — validate before use
    new ParseStage(),         // 2 — read DSL
    new CompileStage(),       // 3 — walk BOM tree, resolve geometry
    new TemplateStage(),      // 4 — AABB matching (ST mode only)
    new WriteStage(),         // 5 — serialize to output.db
    new VerbStage(),          // 6 — BIM COBOL script hook
    new DigestStage(),        // 7 — SHA-256 spatial fingerprint
    new GeometryStage(),      // 8 — mesh integrity validation
    new ProveStage()          // 9 — mathematical placement proofs
);
```

| Mode | Trigger | Behaviour |
|------|---------|-----------|
| **Instant Drop** | 1 C_OrderLine, no modifications | BOM tree exploded in one pass (BBC.md §3.3) |
| **BOM Drop** | User navigates tree, swaps/adds products | Modified order compiled through same pipeline (BBC.md §3.4) |

**Entry point:** `DesignerAPI.bomDrop(buildingProductId)` → `DesignerAPIImpl.explodeBomTree()`.
Creates C_Order + walks m_bom/m_bom_line recursively. IsBOM products (matching m_bom) recurse;
leaves become C_OrderLine LEAF rows. Returns `BomTreeNode` for Outliner (expand/collapse).
DocAction: Save(validate) → Approve(new product config) → Complete(compile+viewport).

**CO mode (Terminal/Institutional):** CompileStage is skipped for M_Product_Category=CO buildings. All elements come from BOM extraction — no DSL compilation path needed.

> **Full pipeline spec:** [`BOMBasedCompilation.md`](BOMBasedCompilation.md) — per-stage walkthrough, CO mode forensics, data integrity chain.

---

## §7 — The ORM Layer

iDempiere's PO pattern adapted for SQLite: column map, dirty tracking, explicit transaction ownership.

| Layer | Example | Role |
|-------|---------|------|
| **X_** (generated) | `X_M_BOM.java` | Column constants, getters/setters |
| **M_** (model) | `MBOM.java` | Business logic, factory methods, `beforeSave()` guards |

Three EntityTypes protect data integrity:

| EntityType | Code | PO Behaviour |
|------------|------|-------------|
| Dictionary | D | **Read-only** — beforeSave() blocks writes |
| User | U | Read-write (created by verbs) |
| Application | A | Read-write (system-generated) |

> **ORM internals:** See §2 DAO Pattern above for ModelQuery, PO naming, BasePO trap.

---

## §8 — BIM COBOL

Every mutation is a named verb with typed arguments and pass/fail result:

```
CHECK BOM BUILDING_SH_STD
ADD LINE TO SY_MY_ROOM CHILD sofa_001 ROLE FURNITURE SEQ 10 DX 0.5 DY 0.3
FURNISH ROOM SY_MY_ROOM TEMPLATE SH_LIVING_SET
```

Core interfaces: `Verb<T>` (execute → `VerbResult<T>`), `VerbContext` (3 connections: bom/component/output), `VerbRegistry` (longest-prefix dispatch).

5-tier composition: P0 primitives (CREATE BOM, ADD LINE) → L1 room-level → L2 floor → L3 building → L4 catalog. 63 verbs, 196 witnesses.

> **Full verb catalog, grammar, witnesses:** [`BIM_COBOL.md`](BIM_COBOL.md) §19 (verb pattern detection, TILE/ROUTE/FRAME/CLUSTER)

---

## §9 — Testing

Four concentric defence layers:

| Layer | What | Catches |
|-------|------|---------|
| 1. Hash Seal | SHA-256 of 74 critical source files | Unauthorized test/gate modification |
| 2. Structural Guards | ArchUnit + bytecode scanning | Raw SQL bypassing verb layer |
| 3. Cross-Database | D-1..D-5 checks (BOM vs oracle) | Invented products, fabricated dimensions |
| 4. Git Diff Review | Human review of `[SEAL]` commits | Cheating re-seals |

**Layer 5 (C2-SpotCheck):** Picks 5 representative elements and cross-checks exact AABB coordinates between compiled output and reference oracle. No hardcoded golden values — queries both DBs live.

> **Full specification:** [`TestArchitecture.md`](TestArchitecture.md) — anti-drift policy, D-1..D-5 checks, tamper rules T1-T16, forensic inspection guide.

---

## §10 — BonsaiBIMDesigner Module

**Purpose:** Blender/Bonsai GUI bridge — Java TCP server + thin Python addon.

### Architecture

```
┌──────────────────┐     ndjson TCP      ┌──────────────────┐
│  Bonsai (Python)  │ ◄───────────────► │  DesignerServer   │
│  client.py        │     port 9876      │  (Java)           │
│  operator.py      │                    │  DesignerAPIImpl  │
│  panel.py         │                    │  40+ wire actions  │
│  design_bbox.py   │                    │                    │
│  props.py         │                    │  DAO layer:        │
└──────────────────┘                    │  DesignerDAO       │
                                         │  WorkOutputDAO     │
                                         │  CalibrationDAO    │
                                         └──────────────────┘
```

**Principle:** Java-smart / Python-dumb. All SQL, rollup logic, and business rules live in Java DAOs. The Python addon receives pre-computed JSON and renders it.

### Java Packages

| Package | Key Classes | Concern |
|---------|-------------|---------|
| `api/` | `DesignerAPI`, `DesignerAPIImpl`, `DesignerServer`, `AssemblyAPI` | Facade + TCP server (40+ actions) |
| `assembly/` | `AssemblyBuilderService`, `AssemblyDAO`, `UValueCalculator` | G-7 envelope assembly (BS EN ISO 6946) |
| `compile/` | `IncrementalCompiler`, `CompileScopeDetector`, `RoomLayoutGenerator` | Scope-limited recompile, layout generation |
| `dao/` | `DesignerDAO`, `WorkOutputDAO`, `CalibrationDAO` | BOM browsing, design persistence, calibration |
| `validation/` | `PlacementValidator`, `InferenceEngine`, `TerrainSnap`, `CutFillCalculator` | Jurisdiction rules, terrain snap, cut/fill |
| `protocol/` | `JsonProtocol`, `StatusMessage` | ndjson codec, async push |
| `watch/` | `ArtifactWatcher`, `ChangeEvent` | mtime polling for auto-recompile |

### Python Addon (Blender)

| File | Role |
|------|------|
| `client.py` | TCP client — 40+ RPC methods, 10s socket timeout, threading contract |
| `operator.py` | Blender operators — thin delegates to server actions |
| `panel.py` | UI panel — section chooser, BOM browser, compliance strip |
| `design_bbox.py` | GPU batch wireframe renderer (Phase 1: overlay, Phase 2: per-object BOUNDS) |
| `props.py` | Scene properties (connection, building, design mode, BOM chooser) |

> **Full SRS:** [`BIM_Designer.md`](BIM_Designer.md) | UX requirements → [`BIM_Designer_SRS.md`](BIM_Designer_SRS.md) | Bridge protocol → [`BlenderBridge.md`](BlenderBridge.md) | Assembly → [`ASSEMBLY_BUILDER_SRS.md`](ASSEMBLY_BUILDER_SRS.md) | Infrastructure → [`INFRA_DESIGNER_SRS.md`](INFRA_DESIGNER_SRS.md)

---

## §11 — BIMBackOffice Module

**Purpose:** ERP back-end logic — multi-user, multi-project server view.

```
┌──────────────────┐     HTTP :9877      ┌──────────────────┐
│  Web/CLI client   │ ◄───────────────► │  BackOfficeServer  │
│  Bonsai bridge    │     Gson JSON      │  SessionManager    │
└──────────────────┘                    │  4-thread pool     │
                                         └────────┬─────────┘
                      ┌───────────────────────────┼──────────────┐
                      ▼                           ▼              ▼
               {PREFIX}_BOM.db          component_library.db   work_output.db
```

### DAOs

| DAO | Concern | Wire Actions |
|-----|---------|-------------|
| `CostDAO` | 5D: 3-component cost (material + labor + equipment) | `costBreakdown` |
| `ScheduleDAO` | 4D: Construction sequence → Gantt (Malaysian calendar) | `constructionSchedule` |
| `SustainabilityDAO` | 6D: Embodied carbon per product × qty | `carbonFootprint` |
| `FacilityMgmtDAO` | 7D: Maintenance schedule + lifecycle cost | `maintenanceSchedule`, `lifecycleCost` |
| `PortfolioDAO` | Cross-project: portfolio, Kanban, balanced scorecard | `portfolio`, `kanban`, `balancedScorecard` |
| `ChangelogDAO` | Audit trail: change history with undo | `changelog`, `undoChanges` |
| `ReportDAO` | Report engine interface (8 methods, 11 record types) | via portfolio endpoints |

### Server

| Feature | Implementation |
|---------|---------------|
| HTTP endpoints | 11 REST endpoints on port 9877 |
| Sessions | Token-based, 30-min timeout, `ConcurrentHashMap` |
| Conflict detection | `whoIsEditing(buildingId)` per active session |
| Write locks | `ReentrantLock` per DB file |
| Read connections | `PRAGMA query_only=1` + WAL mode |

> **Full SRS:** [`BACK_OFFICE_SRS.md`](BACK_OFFICE_SRS.md) | 4D-7D DAOs → [`TIER1_SRS.md`](TIER1_SRS.md)

---

## §12 — Contributing

The extension pattern: adding a new building type requires **zero Java code**.

### IFC Onboarding Recipe (10 Steps)

| Step | What | Artefact |
|------|------|----------|
| 0 | Pre-flight: verify IFC element types vs `ad_ifc_class_map` | — |
| 1 | Extract geometry from IFC | `{type}_extracted.db` |
| 2 | Extract per-space AABBs from IFC | AABB data for YAML |
| 3 | Write `classify_{prefix}.yaml` | Classification YAML |
| 4 | Write `dsl_{prefix}.bim` | Building DSL definition |
| 5 | Add to `construction_manifest.yaml` | Building identity |
| 6 | Add to `GATE_SCOPE` (**critical** — without this, tests silently skip) | Two test files |
| 7 | Run `./scripts/run_RosettaStones.sh classify_xx.yaml` | Compilation + DimensionRangeValidator pre-flight |
| 8 | Interpret results (G4 FAIL = uncommitted, C8 = library gaps, DimRange = outliers) | Gate report |
| 9 | Mine dimension rules: `./scripts/extract_validation_rules.sh {type}` | `migration/DV_{prefix}_rules.sql` |
| 10 | Apply mined rules: `./scripts/apply_mined_rules.sh` | Updates `disc_validation.db` ad_val_rule |

Proven on 34 buildings. Step 7 now includes DV010 dimension validation — every element's W/D/H is checked against mined ranges from 20 buildings (415 rules, 25 IFC classes). Steps 9–10 feed new building data back into the validation pool. See [`ACInstituteAnalysis.md`](ACInstituteAnalysis.md) for a worked example (699 elements).

> **Full self-service runbook:** [`IFC_ONBOARDING_RUNBOOK.md`](IFC_ONBOARDING_RUNBOOK.md) — step-by-step with commands, expected output, troubleshooting, and template generator (`scripts/new_building.sh`).

### Verb Development

1. Write the witness test first
2. Implement `Verb<T>` — keyword, execute, payload record
3. Register in `VerbRegistry.createDefault()`
4. Run gates — `./scripts/run_tests.sh` must stay GREEN
5. Update seal if test files changed

> **Full recipe with common gotchas:** [`WorkOrderGuide.md`](WorkOrderGuide.md) | Verb catalog → [`BIM_COBOL.md`](BIM_COBOL.md)

---

## Source Code Navigation Map

### Scripts

| File | Role |
|------|------|
| `tools/extract.py` | IFC extraction (LOD + Rosetta) — reads `ad_ifc_class_map` from `disc_validation.db` |
| `scripts/run_tests.sh` | Full gate — compile + all tests + 6 gates |
| `scripts/run_RosettaStones.sh` | YAML-driven: IFCtoBOM → compile → delta |
| `scripts/verify_test_seal.sh` | SHA-256 seal verification (74 critical files) |
| `scripts/construction_manifest.yaml` | Building identity registry |
| `scripts/onboard_ifc.sh` | End-to-end IFC onboarding (steps 0–8 automated) |
| `scripts/extract_validation_rules.sh` | Mine dimension rules from compiled output DBs |
| `scripts/apply_mined_rules.sh` | Uncomment + apply all DV_*_rules.sql to disc_validation.db |

### Java — IFCtoBOM

| Class | Role |
|-------|------|
| `IFCtoBOMPipeline` | Orchestrator (includes DV010 DimensionRangeValidator pre-flight) |
| `DimensionRangeValidator` | IFC quality gate: checks element dims against 415 mined rules |
| `ExtractionPopulator` | Populates I_Element_Extraction from reference DB |
| `ProductRegistrar` | M_Product master catalog |
| `VerbFactorizer` | Reusable verb compression: group→detect→factored/unfactored LEAF writes |
| `VerbDetector` | Pattern cascade: TILE > ROUTE > FRAME > CLUSTER |
| `StructuralBomBuilder` | BUILDING + FLOOR headers + verb-compressed LEAF lines |
| `ScopeBomBuilder` | Scope space assignment + verb-compressed LEAF lines |
| `DisciplineBomBuilder` | CO/IN discipline hierarchy + verb-compressed LEAF lines |
| `CompositionBomBuilder` | Mirror partition (DX) |
| `BomValidator` | Pre-commit QA (9 checks) |

### Java — DAGCompiler

| Class | Role |
|-------|------|
| `CompilationPipeline` | 9-stage orchestrator |
| `BuildingCompiler` | Stage 3 — BOM tree walk + geometry resolution |
| `BuildingRegistry` | C_DocType → building identity |
| `BOMWalker` | Tree traversal engine (3-way dispatch) |
| `PlacementCollectorVisitor` | Anchor accumulation + world coordinate computation |
| `RosettaStoneGateTest` | G1-G6 gates |

### Java — BIM_COBOL

| Class | Role |
|-------|------|
| `Verb<T>` | Interface: keyword + execute → VerbResult |
| `VerbRegistry` | Longest-prefix dispatch (63 verbs) |
| `VerbContext` | 3 connections: bom/component/output |

### Java — ORM

| Class | Role |
|-------|------|
| `BasePO` | Column map + dirty tracking |
| `X_M_BOM` / `MBOM` | Two-layer PO: structure + business logic |
| `MBOMLine` | BOM line with tack offsets |

### Databases

| Database | Path | Role |
|----------|------|------|
| SH/DX/FK/IN BOM | `library/{PREFIX}_BOM.db` | Per-building BOM dictionary |
| Geometry oracle | `library/component_library.db` | Meshes, materials from IfcOpenShell |
| Validation rules | `library/validation.db` | AD_Val_Rule + AD_Val_Rule_Param |
| Discipline validation | `library/disc_validation.db` | Phase 2 CalibrationDAO, IFC class map |
| SH/DX output | `DAGCompiler/lib/output/*.db` | Compiled output |
| SH/DX reference | `DAGCompiler/lib/input/*_extracted.db` | IFC extraction oracle |

---

## Quick Reference: The Complete Data Chain

```
IFC File (.ifc)
    │ IfcOpenShell (tools/extract.py)
    ▼
component_library.db                    disc_validation.db
  ├─ component_geometries (mesh blobs)    ├─ ad_ifc_class_map (46 rows)
  ├─ I_Geometry_Map (element→geom)        ├─ AD_Val_Rule (placement rules)
  ├─ M_Product (catalog)                  └─ ad_space_type_mep_bom
  └─ M_Product_Image (product→geom)
    │ IFCtoBOM Java Pipeline
    ▼
{PREFIX}_BOM.db
  ├─ m_bom (BUILDING → FLOOR → SET)
  ├─ m_bom_line (child placements: dx/dy/dz)
  └─ C_DocType (building identity)
    │ DAGCompiler (9-stage pipeline)
    ▼
output.db
  ├─ elements_meta (ifc_class, storey, material)
  ├─ elements_rtree (spatial index: minX..maxZ)
  ├─ base_geometries (placed meshes)
  └─ spatial_structure (containment)
    │ RosettaStoneGateTest (G1-G6)
    ▼
VERIFIED ✓ (or drift detected ✗)
```

---

## Glossary

| Term | Definition |
|------|-----------|
| **AABB** | Axis-Aligned Bounding Box — rectangular envelope around an element, measured in mm |
| **Anti-Drift** | Policy: no output data may be invented — everything traces to extraction or computation |
| **BIM COBOL** | Domain-specific verb language for construction mutations |
| **BOM** | Bill of Materials — hierarchical tree of parts and sub-assemblies |
| **C_DocType** | iDempiere document type — "Construction Order" (one type; classification lives on M_Product_Category) |
| **Instant Drop** | No modifications — 1 C_OrderLine, compile explodes BOM tree (BBC.md §3.3) |
| **EntityType** | Data provenance: D=Dictionary (read-only), U=User (verb-created), A=Application |
| **G1-G6** | Six verification gates in RosettaStoneGateTest |
| **LEAF** | Component type: full LOD exists in component_library.db |
| **PHANTOM** | Component type: gap filler — stripped at output |
| **Prime Rule** | "Extract or Compile Only" — no data invention |
| **Rosetta Stone** | Reference building with known-good IFC data for verification |
| **Tack Point (LBD)** | Left-Back-Down corner = (minX, minY, minZ) = (0,0,0) in own frame |
| **BOM Drop** | Interactive tree navigation — swap/add products by bom_category (BBC.md §3.4) |
| **Witness** | Test proving a verb/DAO produces correct output |

---

## References

1. Oon, R. D. (2026). "Comparative Analysis: Intent-Driven BIM Generation Approaches." `docs/IntentBIMChallengePaper.pdf`
2. ISO 16739-1:2024. *Industry Foundation Classes (IFC).*
3. iDempiere ERP. https://www.idempiere.org
4. IfcOpenShell. https://ifcopenshell.org
5. Bonsai (BlenderBIM). https://bonsaibim.org

---

## Document Map — Where to Go Next

| If you want to... | Read this |
|-------------------|-----------|
| Understand the full compilation spec | [`BOMBasedCompilation.md`](BOMBasedCompilation.md) |
| See the ERP-to-construction mapping | [`SystemContract.md`](SystemContract.md) |
| Learn the 4-DB schema in detail | [`DATA_MODEL.md`](DATA_MODEL.md) |
| Read the BIM COBOL verb catalog | [`BIM_COBOL.md`](BIM_COBOL.md) |
| Understand test architecture & anti-drift | [`TestArchitecture.md`](TestArchitecture.md) |
| Learn the BIM Designer GUI | [`BIM_Designer.md`](BIM_Designer.md) |
| Understand the assembly builder | [`ASSEMBLY_BUILDER_SRS.md`](ASSEMBLY_BUILDER_SRS.md) |
| Learn back-office architecture | [`BACK_OFFICE_SRS.md`](BACK_OFFICE_SRS.md) |
| Read 4D-7D DAO specifications | [`TIER1_SRS.md`](TIER1_SRS.md) |
| Understand infrastructure analysis | [`InfrastructureAnalysis.md`](InfrastructureAnalysis.md) |
| See the project roadmap | [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md) |
| Learn YAML pipeline config | [`WorkOrderGuide.md`](WorkOrderGuide.md) |
| Browse the interactive architecture diagram | [`bim_architecture_viz.html`](bim_architecture_viz.html) |
| Read the Rosetta Stone strategy | [`TheRosettaStoneStrategy.md`](TheRosettaStoneStrategy.md) |
| Understand calibration | [`CALIBRATION_SRS.md`](CALIBRATION_SRS.md) |
| Learn the Blender bridge protocol | [`BlenderBridge.md`](BlenderBridge.md) |
| Read infrastructure designer SRS | [`INFRA_DESIGNER_SRS.md`](INFRA_DESIGNER_SRS.md) |
| See market positioning & scorecard | [`StrategicIndustryPositioning.md`](StrategicIndustryPositioning.md) |

---

## Conventions

### DAO Rule
- Use DAO (`ModelQuery<X_M_BOMLine>` etc.) for all resolver code
- Raw JDBC only for: complex JOINs in MetadataValidator, read-only inspection
- Nullable getters: `MOrderLine.getHeightMmOrNull()` (asDoubleOrNull pattern)

### iDempiere Naming
- Conceptual sections: iDempiere entity names (C_Order, M_BOM, C_BPartner)
- Implementation sections: actual table names
- `c_bpartner` = C_BPartner = "Construction Building Pattern" (SH/DX/TB/MY/TE/ST)
- Storeys, rooms, components are ALL M_BOMs — compiler is logistics forwarder
- Buffer space (M_Product_Category=ST) = explicit M_BOM_Line child for SpaceSize invariant

### iDempiere ERP Layer Mapping
- `M_Product` (BOM.db), `m_bom`/`m_bom_line`/`m_attribute`/`M_Product_Category` (BOM.db)
- `c_order`/`c_orderline`/`co_empty_space`/`co_empty_space_line` (output.db)
- `C_BPartner`/`C_Campaign`/`AD_User`/`C_DocType` (BOM.db lookup/config)

---

## Critical Traps

### SQL / Schema
- `element_instances` column is `guid` (NOT `element_guid`)
- `M_Product` columns: `width`, `depth`, `height` — units are **metres**
- `M_Product` column is `product_type` (NOT `product_category`) — no `ifc_class` column
- `c_order.building_type` = category (RESIDENTIAL/COMMERCIAL), NOT building ID
- `m_bom_line.child_name_pattern` = LIKE pattern — use `product_ref` FK for exact match
- `c_orderline.family_ref` is NOT an FK to ad_opening_family
- `c_orderline.height_extent_mm` = element height (MUST be set or dz=0 → P01/P03 CRITICAL)
- `c_orderline.height_mm` = mounting height above floor (sill/outlet) — separate from extent
- `lod_geometry_map.geometry_hash` REFERENCES `component_geometries(geometry_hash)` — FK must exist first
- `RelationalResolver.loadRules()` ORDER BY id — insertion order = roofIndex numbering

### BOM / component_type
- Assembly relationships identified STRUCTURALLY: `child_product_id IN (SELECT bom_id FROM m_bom)`
- BOMWalker uses structural detection (loadBom), NOT component_type — safe
- MBOMLine.isNestedBom() still checks MAKE — fragile, prefer structural check
- Never change component_type values without verifying ALL downstream consumers

### Data Integrity — PRIME RULE
- **All data produced by code, never manual SQL fixes** — that is cheating
- `component_library.db` = LOD catalog data only (geometry, materials, dimensions)
- `*_BOM.db` files are generative — always delete before regenerating (rm first)
- Never produce or reference monolithic `library/BOM.db` — only `{PREFIX}_BOM.db`

### Java / Compiler
- PlacementProver: P01-P03/P16-P17/P22 = gate (critical); P04-P15/P18-P21/P23 = advisory
- MeshBinder `isNS`: mesh-proximity comparison, NOT bbox aspect ratio
- GUID ordinal: ALWAYS use `++ordinalCounter`, NEVER `line.getOrdinal()`
- BOM furniture LOD: `tx=p.minX()-lb[0]*sX` (min-corner, NOT center)
- DX compiles as "Ifc2x3_Duplex" (NOT MULTI_UNIT)
- Metadata buildings: roof via `overrideRoofPosition()` ONLY — `BuildingSpec.roof()` NOT called

### Geometry / Coord
- WorldCoord: ONLY via `LocalCoord.toWorld(StoreyCoord)` or `StoreyCoord.asWorld()`. D8 ArchUnit enforces.
- elements_rtree: id, minX, maxX, minY, maxY, minZ, maxZ (NOT interleaved)
- Output DB R*Tree = metres, CO_EmptySpace = mm (multiply by 1000.0)
- Library geometry (non-GEO_ hash) uses canonical coords, NOT world coords

### IFC Type
- Walls stored as `IfcPlate` (not IfcWall) — SQL must include IfcPlate
- Duplex party walls ARE `IfcWall` (not IfcPlate) — assertion uses `IfcWall:Party`
