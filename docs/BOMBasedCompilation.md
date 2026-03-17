# BOM-Based Compilation — Construction as Manufacturing

*If you can bill it, you can build it. If you can BOM it, you can compile it.*

> **Core thesis:** A building is a manufactured product. Its Bill of Materials IS the
> building — every wall, door, pipe, and cabinet is a line item with a position.
> A compiler that reads BOM data and produces 3D coordinates is doing the same thing
> an ERP system does when it explodes a manufacturing BOM into work orders.

---

## 1. Why BOM Metadata Solves Construction

Construction software treats buildings as geometry problems — draw walls, place
doors, route pipes. ERP software treats products as data problems — BOMs, work
orders, procurement. This project proves they are the **same problem**.

| Manufacturing concept | Construction equivalent |
|----------------------|----------------------|
| **M_Product** (product catalog) | Building element (wall panel, door, pipe elbow) |
| **M_BOM** (bill of materials) | Assembly recipe (kitchen set, floor plan, building unit) |
| **M_BOM_Line** (BOM child) | Recipe line: product type + qty (or verb formula). NOT a per-instance placement |
| **C_Order** (work order) | Construction project for a specific building |
| **C_DocType** (document type) | Building type configuration (SH, DX, TB, TE) |
| **CO_EmptySpace** (warehouse slot) | Room/floor slot awaiting BOM content |
| **PP_Order_Node** (operation) | Verb execution record (audit trail) |
| **C_Campaign** (marketing) | Design theme (Bali, Scandinavian, Industrial) |
| **EntityType** (D/U/A) | Dictionary=shipped catalog, User=verb-created, Application=custom |

The BOM hierarchy maps directly to the building hierarchy:

```
UNIT  →  FLOOR  →  ROOM  →  SET  →  ITEM
 │         │         │        │        │
 building  storey    room    furniture  leaf product
                              group    (door, pipe, cabinet)
```

**Three BOM dimensions** (iDempiere pattern) govern selection:

1. **Category** (M_BomCategory) — WHAT: kitchen, bedroom, bathroom, structural
2. **Owner** (C_DocType.DocSubType) — WHICH variant: SH, DX, TB, TE
3. **SpaceSize** (AABB on M_BOM_Line) — HOW MUCH: width × depth × height in mm

**Why this is powerful:** Adding a new building type requires zero Java code.
Define new BOM data (M_BomCategory + M_BomCategoryLine + m_bom rows) and the
compiler handles it. The same way an ERP handles a new product — data, not code.

---

## 2. The Gospel Principle

Reference buildings are treated as **gospel** — authoritative, immutable truth.

```
Extract (IFC source)  →  Commit ({PREFIX}_BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**`{PREFIX}_BOM.db` is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

Every element the compiler produces must trace to a real IFC source. If it cannot
be traced, the output is invalid. This is the first principle.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

---

## 2.1. IFC→BOM Stage — Top-Down AABB Decomposition

*Preamble to compilation: how extracted IFC data becomes a BOM tree.*

The IFCtoBOM pipeline reads `I_Element_Extraction` (component_library.db) and a
classification YAML, then produces a `*_BOM.db` with spatial arrangement (m_bom + m_bom_line).
Products are created in component_library.db first (persistent catalog, reused across buildings),
then transitionally copied to BOM DB. 9 BomValidator checks + 2 pre-flight guards enforce data integrity pre-commit.
See [`YAMLGuide.md`](YAMLGuide.md) §Step 5 for the full pipeline table and §Drift Prevention
for the guard list. The decomposition is **top-down** — from the largest AABB to the smallest —
with each layer stopping when it has assigned its children.

### 2.1.1 Decomposition Layers

```
BUILDING AABB (computed from all extracted elements)
  │
  ├─ split by STOREY (storey names from YAML + I_Element_Extraction.storey)
  │    → compute per-storey AABB from elements in that storey
  │
  ├─ split by DISCIPLINE (from YAML disciplines: map + I_Element_Extraction.discipline)
  │    → ARC_DX_L1, PLB_DX_L1, ELC_DX_L1 etc.
  │    → per-discipline AABB from elements in that discipline×storey
  │
  ├─ split by SCOPE SPACE (from YAML floor_rooms: spaces with AABB)
  │    → elements whose centroid falls within scope space AABB
  │    → produces SET BOMs (LIVING_SET, KITCHEN_SET, etc.)
  │    → dedup: identical M_Product entries get qty, not duplicate BOM lines
  │
  └─ LEAF elements + BUFFER space
       → leaves = M_Product refs with dx/dy/dz (parent-relative from extraction)
       → buffer space fills when validateBOM() detects parent AABB ≠ SUM(children)
```

### 2.1.2 Each Layer Stops

| Layer | Input | Output | Stops When |
|-------|-------|--------|-----------|
| BUILDING | all elements | BUILDING BOM + AABB | storey assignment complete |
| STOREY | elements in this storey | FLOOR BOM + AABB | discipline assignment complete |
| DISCIPLINE | elements in this discipline×storey | DISC BOM + AABB | scope space assignment complete |
| SCOPE SPACE | elements in this room scope | SET BOM + AABB | all elements assigned to leaves |
| LEAF | single M_Product | m_bom_line with dx/dy/dz | terminal — no further split |
| BUFFER | remaining AABB gap | PHANTOM m_bom_line | fills parent = SUM(children) invariant |

### 2.1.3 YAML Defines Scope Spaces

The classify YAML provides the spatial grid that the pipeline decomposes into.
The pipeline does not invent scope spaces — it reads them from the YAML and
assigns extracted elements by spatial containment (centroid within AABB).

```yaml
floor_rooms:
  Ground Floor:
    bom_id: FLOOR_SH_GF_STD
    spaces:
      - { name: LIVING,   template_bom: SH_LIVING_SET,  aabb_mm: [9069, 1682, 1170] }
      - { name: KITCHEN,  template_bom: KITCHEN_SET,     aabb_mm: [7100, 600, 900] }
      - { name: BATHROOM, template_bom: TOILET_FIXTURES, aabb_mm: [0, 0, 0] }
```

The `aabb_mm` on each space is the scope — the spatial envelope that the pipeline
uses to assign elements. Elements whose centroid falls within this scope become
children of that SET BOM. Elements not matching any scope become structural
(direct children of the storey discipline BOM).

### 2.1.4 Dedup and Product Registration

Identical elements (same `element_ref` pattern) are deduplicated into a single
`M_Product` entry. The BOM line carries `qty` for replicates instead of one
line per instance. This is the iDempiere pattern: 47 duplex receptacles =
1 M_Product (`DUPLEX_RECEPTACLE`) with `qty=47` on the BOM line.

### 2.1.5 Discipline Layer (schema_version 2)

For multi-discipline buildings (DX, Terminal), the YAML `disciplines:` map
inserts a discipline BOM level between STOREY and SCOPE SPACE. See
`DISC_BOM_DESIGN.md` §2 for the 5-level hierarchy and §5 for YAML schema v2.
See `TerminalAnalysis.md` §ERP Model Architecture for how discipline codes
sit on M_BomCategory with `doc_type='CO'` and how ROUTE/TILE verbs map to
M_AttributeSet/Instance.

Single-discipline buildings (SH, schema_version 1) skip this layer — no
discipline wrapper needed. DocBaseType (RE/CO) on M_BomCategory determines
which L2 axis is used: rooms (RE) or disciplines (CO).

### 2.1.6 Recipe vs Placement — The BOM Contract

**`{PREFIX}_BOM.db` is a recipe.** Each m_bom_line is a **type line** — one row per
unique product within its parent BOM, with a qty count or verb formula reference.
The compiler expands type lines into placement instances at compile time.

**output.db holds placements.** Each row in `c_orderline` / `elements_meta` is one
physical element at its world-space coordinate. The compiler produces these by
expanding BOM recipes through verb formulas (TILE grid, ROUTE path, FRAME bay, etc.)
or flat placement (qty=1 for irregular elements).

```
{PREFIX}_BOM.db (RECIPE — factored)          output.db (PLACEMENT — expanded)
┌─────────────────────────────────┐         ┌──────────────────────────────────┐
│ m_bom_line                      │         │ elements_meta / c_orderline      │
│  product_id=PLATE_500x150       │  ──→    │  PLATE_500x150 @ (92.5, -42, 19)│
│  qty=4410                       │ expand  │  PLATE_500x150 @ (93.0, -42, 19)│
│  verb_ref=TILE(15×294, 495mm)   │         │  PLATE_500x150 @ (93.5, -42, 19)│
│                                 │         │  ... (4,410 rows)               │
└─────────────────────────────────┘         └──────────────────────────────────┘
```

**SH/DX happen to be trivially factored** — most products appear once per parent
BOM (qty=1), so recipe and placement look identical. The distinction only becomes
visible at TE scale where 505 unique products expand to 48,428 placement instances.

**Invariant:** `SUM(m_bom_line.qty)` across all leaf BOMs in `{PREFIX}_BOM.db` equals
the element count in output.db. The BOM is the recipe; the output is the cooked meal.

**TE factorization (done, 2026-03-17):** 48,428 elements → 1,442 recipe lines (34:1).
VerbDetector mines 4 verb patterns from extraction centroids:

| Verb | Instances | Fidelity | What it detects |
|------|-----------|----------|-----------------|
| TILE | 12 | PASS (0.0m) | 2D uniform grid (roof plates) |
| FRAME | 60 | PASS (0.0m) | Grid intersections (structural bays) |
| ROUTE | 533 | advisory | Axis-aligned uniform-step runs (pipes, ducts) |
| SPRAY | 46,712 | advisory | Semi-regular grid, 10% tolerance (sprinklers, MEP) |

Step-uniformity guard (R8): each ROUTE leg's consecutive gaps must be within
±20% of the average step. Non-uniform groups fall through to SPRAY or flat writes.
See [`VerbPatternArchitecture.md`](VerbPatternArchitecture.md) for verb taxonomy,
data flow, and fidelity details.

**Pipeline phases (separated, 2026-03-17):**
1. **Populate** (`IFCtoBOMMain --populate`): reference DB → component_library.db (one-time)
2. **BOM pipeline** (`IFCtoBOMPipeline.run()`): reads component_library.db → writes `{PREFIX}_BOM.db`
3. **Compile** (`DAGCompiler`): reads `{PREFIX}_BOM.db` → produces output.db

Phase 1 is skip-guarded in `run_RosettaStones.sh` — runs only when extraction
count is 0 for the building_type. Phase 2 can re-run freely (`rm *_BOM.db`).

### 2.1.7 What IFCtoBOM Does NOT Do

- Does not invent elements (EXTRACT only — every leaf traces to I_Element_Extraction)
- Does not compute placement rules (layout_strategy, z_rule stay NULL — generative future)
- Does not validate against regulations (see `VALIDATION_RULE_DESIGN.md`)
- Does not fill missing pipes or correct gaps (WYSIWYG — DX corners without connecting pipes are preserved as-is)

---

## 3. Two Compilation Modes

### EN-BLOC (Singularity)

When the selection cascade narrows to **exactly one BOM**, the result is a
mathematical singularity — the answer is unique, so the compiler takes it whole.

### WALK THRU (Progressive Stacking)

When no single BOM matches, the compiler walks M_BomCategoryLine slots in
sequence, fitting the best candidate into each slot via the selection cascade.

### Selection Cascade

Two fields drive everything: **DocSubType** and **AABB**. A third — **BomCategory**
— scopes the search.

1. **BomCategory** (scope): restricts to correct functional type
2. **AABB fit** (primary): SpaceSize must fit within the slot
3. **Largest volume** (secondary): maximize space usage
4. **seq_no** (tiebreaker): lower preferred

---

## 4. Tack Convention — The Spatial Handshake

Every BOM and every element has a **tack point**: the Left-Front-Down corner of
its bounding box = (0, 0, 0) in its own coordinate frame.

- **Left** = X minimum, **Front** = Y minimum, **Up** = Z positive

All dx/dy/dz offsets in m_bom_line are measured from parent tack to child tack.
All values are positive — a child cannot be behind its parent's origin.

**tack_to / tack_from (Lego principle):** At every BOM level:
- **tack_to** — "I attach to my parent at this point on myself"
- **tack_from** — "my children attach to me at these points"

This convention makes BOM placement purely algebraic — no heuristics, no AI,
no tolerance. Parent origin + line offset = child position. Recursively.

---

## 5. The 9-Stage Pipeline

| # | Stage | What it does |
|---|-------|-------------|
| 1 | **Metadata** | Referential integrity checks against `{PREFIX}_BOM.db` |
| 2 | **Parse** | Reads `.bim` DSL text into records |
| 3 | **Compile** | Produces `BuildingSpec` from BOM hierarchy |
| 4 | **Template** | ST-mode: walks M_BomCategoryLine slots |
| 5 | **Write** | Emits SQLite output DB |
| 6 | **Verb** | BIM COBOL script hook → PP_Order_Node audit trail |
| 7 | **Digest** | Per-element SHA256 spatial fingerprint |
| 8 | **Geometry** | Mesh integrity validation |
| 9 | **Prove** | Mathematical placement proofs |

Both compilation modes follow the same data flow: element positions are read from
m_bom_line (parent-relative offsets) and accumulated via the tack convention into
world coordinates. EN-BLOC takes the BUILDING BOM as-is; WALK THRU recalculates through each layer (BUILDING → FLOOR → SET → BUY).

---

## 6. BIM COBOL — Verb-Driven BOM Mutation

The GUI emits BIM COBOL verbs, never direct SQL. 63 verbs in 5 tiers:

| Tier | Verbs | Purpose |
|------|-------|---------|
| P0 Primitive | CREATE BOM, ADD LINE, SET TACK, SET ROTATION, SET DIMENSIONS, REMOVE LINE, DELETE BOM, SET LINE PROPERTY | BOM CRUD atoms |
| Utility | VALIDATE AABB, SNAP TO GRID, EXTRACT AABB | Validation + transform |
| L1 Convenience | CREATE ROOM, FURNISH ROOM, RESIZE ROOM, STRIP ROOM | Room-level composed verbs |
| Data | SELECT, LIST, DESCRIBE, COUNT, AGGREGATE, EXPORT, CLONE, SUMMARIZE BOM | Query + export |
| Original | PLACE BOM, EN BLOC, WIRE LIGHTING, ROUTE SPRINKLERS, TILE SURFACE, CHECK BOM, ... | Geometry + inspection |

**Layered composition:** L1 verbs call P0 primitives. L2 (floor-level) will call
L1. Never skip layers. Each verb = one file, one keyword, one payload record.

Full grammar spec: [`docs/BIM_COBOL.md`](BIM_COBOL.md)

---

## 7. Verification: The Rosetta Stone Gate

**Maths that proves visuals without cheating.**

> **Verification gates:** See [`TestArchitecture.md`](TestArchitecture.md) §Verification for the complete G1-G6 gate specification, tamper rules (T1-T16), and the 4-layer defense model.

All 6 gates GREEN for SH (55 elements) and DX (1099 elements).

---

## 8. What This Is Not

| It is NOT | Because |
|-----------|---------|
| Revit / ArchiCAD | Those are authoring tools. This is a reproducer from committed data. |
| Rule-based AI | No heuristics, no ML. Selection cascade is deterministic. |
| Parametric design | Parameters come from extracted BOM data, not design exploration. |
| Approximate | AABB matching is 3D exact. Digest is SHA256. No tolerance. |
| Interactive | Batch compilation. Like COBOL/ERP — process the order, produce the output. |

---

## 9. The End State

The compiler runs without human assistance. Given a `.bim` DSL file and two source
databases (`{PREFIX}_BOM.db` + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

---

## 10. Dynamic Building Registration

Adding a building type in the Java compiler requires zero code changes — `BuildingRegistry.loadActive()`
reads C_DocType from `{PREFIX}_BOM.db` and compiles every active row. But the Python and shell tooling that
*populates* `{PREFIX}_BOM.db` hardcodes building names, storey mappings, output paths, and expected element
counts in four files. This is like hardcoding part numbers into the MRP engine instead of reading
the BOM. When extraction counts change or a new building appears, the scripts drift from the data.

This section specifies how to close that gap.

### 10.1 The Problem: Hardcoded Registration

The Java side is clean — `BuildingRegistry` (line 70) issues one SQL query with zero building
names in source code. The Python/shell side is not:

| # | File | Lines | What is hardcoded |
|---|------|-------|-------------------|
| 1 | `scripts/RosettaStoneExtract.py` | 37–60 | `BUILDINGS` dict: prefixes (`SH`, `DX`), doc_sub_type, building_bom_id, storey name→code mappings, roles, seq_no |
| 2 | `scripts/RosettaStoneToBOM.py` | 48–55 | `C_DOCTYPE` list: doc_type_id, DocBaseType, DocSubType, output/reference paths, ExpectedElements (55, 1099, 139, 51088) |
| 3 | `scripts/RosettaStoneToBOM.py` | 345–439 | `M_BOM` / `M_BOM_LINE`: BUILDING BOM headers with AABB, MAKE children with dx/dy/dz offsets |
| 4 | `scripts/run_RosettaStones.sh` | 39–40, 418–435 | `SH_BASE` / `DX_BASE` path variables, `case` dispatch with literal `"SH"` / `"DX"` |
| 5 | `scripts/run_tests.sh` | 125–136 | `run_preflight "Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` — literal building names |
| 6 | `scripts/run_tests.sh` | 145–183 | Expected pass/fail counts (191/1, 34/2, 19/0, 179/7) and intentional-failure offsets |

**Consequence:** Every time an extraction changes element counts, a developer must hand-edit
expected values in at least two files. Adding a third EXTRACTED building requires touching all four.
The Java compiler handles it with one C_DocType row. The tooling should too.

### 10.2 Building Manifest (Source of Truth)

A YAML configuration file `scripts/construction_manifest.yaml` declares each building's identity
and structure. The manifest is an index into extraction truth — it names things, never measures them.

```yaml
# construction_manifest.yaml — declarative building registration
# Rule: storey names, roles, paths.  Never counts or dimensions.

buildings:
  Ifc4_SampleHouse:
    prefix: SH
    doc_sub_type: SH
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc4_samplehouse.db
    reference_path: DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db
    seq_no: 10
    storeys:
      Ground Floor: { code: GF, bom_category: GF, role: GROUND_FLOOR, seq: 1010 }
      Roof:         { code: ROOF, bom_category: RF, role: ROOF, seq: 1020 }
      Unknown:      { code: CW, bom_category: CW, role: CURTAIN_WALL, seq: 1030 }

  Ifc2x3_Duplex:
    prefix: DX
    doc_sub_type: DX
    doc_base_type: RE
    provenance: EXTRACTED
    output_path: DAGCompiler/lib/output/ifc2x3_duplex.db
    reference_path: DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db
    seq_no: 20
    geometry_fail_threshold: 5
    storeys:
      Level 1: { code: L1, bom_category: L1, role: LEVEL_1, seq: 1010 }
      Level 2: { code: L2, bom_category: L2, role: LEVEL_2, seq: 1020 }
      Roof:    { code: ROOF, bom_category: RF, role: ROOF, seq: 1030 }
      T/FDN:   { code: FDN, bom_category: FN, role: FOUNDATION, seq: 1040 }
      Unknown: { code: MISC, bom_category: MS, role: MISC, seq: 1050 }
```

**iDempiere pattern:** The manifest plays the same role as `AD_Table` — it defines metadata
that scripts consume, so no script contains application logic about what buildings exist.
One file is the single source of registration truth. Scripts import it; they never duplicate it.

### 10.3 Derived Values (Never Hardcode)

Some values currently hardcoded in the scripts are not properties of the building's identity.
They are measurements that can — and must — be computed from extraction data.

| Value | Current source | Correct source | Why |
|-------|---------------|----------------|-----|
| `ExpectedElements` (55, 1099) | Hardcoded in `C_DOCTYPE` tuple | `COUNT(*)` from extraction DB | Extraction is the measurement |
| `AABB` on BUILDING BOM | Hardcoded in `M_BOM` list | Computed from extraction envelope | Gospel Principle: extraction = truth |
| Floor AABB dimensions | Hardcoded in `M_BOM` list | Aggregated from storey extraction | Same principle at floor level |
| `M_BOM_LINE` offsets (dx/dy/dz) | Hardcoded in builder tuples | Computed from extraction storey origins | Offsets are measured, not declared |
| DSL content | Generated inline | Derived from manifest + extraction | Content follows structure |
| Spatial digest reference | Implicit in gate thresholds | SHA256 of compiled output | Digest is a function of data |

**Rule:** If a value can be derived from extraction data, it MUST be derived, not declared.
The manifest declares identity (name, role, path). Extraction measures reality (count, envelope,
offset). Hardcoding a derived value violates the Gospel Principle — it replaces measurement with
opinion.

### 10.4 Target Workflow: Add a New Building

After migration, adding a new building follows the same pattern as adding a new product to an
ERP system — one BOM entry, zero code changes:

1. **Manifest entry** — add one YAML block to `construction_manifest.yaml` (name, prefix,
   doc_sub_type, storeys, paths)
2. **Extract IFC** — run extraction pipeline; reference DB appears at the declared path
3. **Regenerate BOM dictionary** — IFCtoBOM Java pipeline (SH/DX) or `RosettaStoneToBOM.py` (TE)
   reads classification YAML + extraction, writes M_BOM, M_BOM_Line with derived counts and dimensions
4. **Verify** — `./scripts/run_RosettaStones.sh classify_XX.yaml` compiles and gates the new building

Zero Python edits. Zero shell edits. Zero Java edits. The manifest is the only touch point.

### 10.5 Script Migration

Each script transitions from hardcoded values to manifest-driven or `{PREFIX}_BOM.db`-driven lookups:

| Script | Reads now | Reads after | Change scope |
|--------|-----------|-------------|-------------|
| `RosettaStoneExtract.py` | `BUILDINGS` dict (lines 37–60): prefixes, storey maps, BOM IDs | `construction_manifest.yaml`: same data, external file | Replace dict literal with `yaml.safe_load()`. Storey mappings move verbatim. |
| `RosettaStoneToBOM.py` | `C_DOCTYPE` list (lines 48–55): paths, ExpectedElements. `M_BOM` / `M_BOM_LINE` (lines 345–439): AABB, offsets | Manifest for identity; extraction DBs for counts, AABB, offsets | Biggest change. Builder loops derive dimensions from extraction instead of literals. |
| `run_RosettaStones.sh` | `SH_BASE` / `DX_BASE` variables, `case` dispatch (lines 39–40, 418–435) | Query `SELECT DocSubType, output_path FROM C_DocType WHERE IsActive=1` from `{PREFIX}_BOM.db` | Shell reads `{PREFIX}_BOM.db` via `sqlite3`. Loop replaces case statement. |
| `run_tests.sh` | Literal `"Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` in preflight calls (lines 125–136); hardcoded expected counts (lines 145–183) | Preflight: query C_DocType for active EXTRACTED buildings. Counts: read from `{PREFIX}_BOM.db` or test output. | Preflight becomes a loop. Expected counts derived, not literal. |

### 10.6 Invariants

Six rules govern the boundary between declaration and derivation:

1. **Manifest is declarative** — storey names, roles, paths, prefix. Never counts, dimensions,
   or coordinates.
2. **Extraction is measurement** — element counts, AABB envelopes, storey origins. These are
   facts read from IFC reference data, not human assertions.
3. **`{PREFIX}_BOM.db` is artifact** — produced by IFCtoBOM Java pipeline (SH/DX) or
   `RosettaStoneToBOM.py` (TE) from classification YAML + extraction.
   It is never hand-edited. Regeneration is idempotent.
4. **Java reads `{PREFIX}_BOM.db` only** — `BuildingRegistry.loadActive()` queries C_DocType. It never
   reads the manifest, never reads extraction DBs, never contains building names.
5. **Shell reads `{PREFIX}_BOM.db` or manifest only** — no script contains a building name as a string
   literal (except in log messages). Control flow is driven by query results.
6. **Drift resistance** — if extraction changes element count from 1099 to 1102, the only
   action is re-running the IFCtoBOM pipeline. No file is hand-edited. No test threshold
   is manually adjusted.

### 10.7 Migration Path

Migration proceeds in five phases, each self-contained and independently verifiable.

| Phase | Scope | Risk | Status |
|-------|-------|------|--------|
| **A** | Create `construction_manifest.yaml` | None | **DONE** — manifest created with SH, DX, TE, ST entries |
| **B** | `RosettaStoneExtract.py` reads manifest | Low | **DONE** — `BUILDINGS` dict replaced with `yaml.safe_load()` |
| **C** | `RosettaStoneToBOM.py` reads manifest | Medium | **DONE** — `C_DOCTYPE` list replaced with `_build_c_doctype()` |
| **D** | `run_RosettaStones.sh` queries `{PREFIX}_BOM.db` | Low | **DONE** — loop replaces case statement, summary dynamic |
| **E** | `run_tests.sh` derives expected counts | Low | Deferred — not a Rosetta Stone concern |

**Gate between phases:** `./scripts/run_RosettaStones.sh all` must produce identical output
after each phase. All phases A–D gated GREEN: SH=55, DX=1099, 0 geometry divergences.

**Note:** TB-LKTN removed from manifest — generative case will be approached fresh once
the EXTRACTED registration pipeline is stable. Terminal (CO_TE) registered with TE-1
storey normalisation DONE (48,428 active elements, 7 storeys, 8 disciplines).

---

*Detailed architecture: [`ConstructionAsERP.md`](ConstructionAsERP.md) |
BOM dimensions: [`BIMasBOMConcept.md`](BIMasBOMConcept.md) |
Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Terminal ERP model: [`TerminalAnalysis.md`](TerminalAnalysis.md) §ERP Model Architecture |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
