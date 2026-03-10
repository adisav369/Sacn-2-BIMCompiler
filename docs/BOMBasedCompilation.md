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
| **M_BOM_Line** (BOM child) | Placed element with position (dx/dy/dz) and rotation |
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
Extract (IFC source)  →  Commit (BOM.db)  →  Reproduce (compile)  →  Verify (gates)
```

**BOM.db is a pure dictionary** — never written to during compilation. It defines
assembly recipes, product dimensions, building type configuration, and spatial rules.
All extracted from reference buildings and curated as immutable data.

Every element the compiler produces must trace to a real IFC source. If it cannot
be traced, the output is invalid. This is the first principle.

**EntityType enforcement:** Dictionary records (entity_type='D') are read-only at
the PO layer. Verbs create new records as entity_type='U'. The guard is in code
(MBOM.beforeSave / MBOMLine.beforeSave), not documentation.

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
| 1 | **Metadata** | Referential integrity checks against BOM.db |
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

The GUI emits BIM COBOL verbs, never direct SQL. 38 verbs in 5 tiers:

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

| Gate | What it checks |
|------|---------------|
| **G1-COUNT** | Element count: reference = compiled |
| **G2-VOLUME** | Total AABB volume match |
| **G3-DIGEST** | Per-element spatial SHA256 |
| **G4-TAMPER** | Source code self-inspection |
| **G5-PROVENANCE** | Every element traced to library |
| **G6-ISOLATION** | Output scoped to building only |

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
databases (BOM.db + component_library.db), it produces a complete, verified output.

The cycle: **Extract → Commit → Compile → Verify → Fix → Repeat** until all gates pass.

Adding a new building = adding BOM data. The compiler is the constant.

---

---

## 10. Dynamic Building Registration

Adding a building type in the Java compiler requires zero code changes — `BuildingRegistry.loadActive()`
reads C_DocType from BOM.db and compiles every active row. But the Python and shell tooling that
*populates* BOM.db hardcodes building names, storey mappings, output paths, and expected element
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
    output_path: DAGCompiler/lib/output/ifc4_sample_house.db
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
3. **Regenerate BOM.db** — `RosettaStoneToBOM.py` reads manifest + extraction, writes
   C_DocType, M_BOM, M_BOM_Line with derived counts and dimensions
4. **Verify** — `./scripts/run_RosettaStones.sh all` compiles and gates the new building
   alongside existing ones

Zero Python edits. Zero shell edits. Zero Java edits. The manifest is the only touch point.

### 10.5 Script Migration

Each script transitions from hardcoded values to manifest-driven or BOM.db-driven lookups:

| Script | Reads now | Reads after | Change scope |
|--------|-----------|-------------|-------------|
| `RosettaStoneExtract.py` | `BUILDINGS` dict (lines 37–60): prefixes, storey maps, BOM IDs | `construction_manifest.yaml`: same data, external file | Replace dict literal with `yaml.safe_load()`. Storey mappings move verbatim. |
| `RosettaStoneToBOM.py` | `C_DOCTYPE` list (lines 48–55): paths, ExpectedElements. `M_BOM` / `M_BOM_LINE` (lines 345–439): AABB, offsets | Manifest for identity; extraction DBs for counts, AABB, offsets | Biggest change. Builder loops derive dimensions from extraction instead of literals. |
| `run_RosettaStones.sh` | `SH_BASE` / `DX_BASE` variables, `case` dispatch (lines 39–40, 418–435) | Query `SELECT DocSubType, output_path FROM C_DocType WHERE IsActive=1` from BOM.db | Shell reads BOM.db via `sqlite3`. Loop replaces case statement. |
| `run_tests.sh` | Literal `"Ifc4_SampleHouse"` / `"Ifc2x3_Duplex"` in preflight calls (lines 125–136); hardcoded expected counts (lines 145–183) | Preflight: query C_DocType for active EXTRACTED buildings. Counts: read from BOM.db or test output. | Preflight becomes a loop. Expected counts derived, not literal. |

### 10.6 Invariants

Six rules govern the boundary between declaration and derivation:

1. **Manifest is declarative** — storey names, roles, paths, prefix. Never counts, dimensions,
   or coordinates.
2. **Extraction is measurement** — element counts, AABB envelopes, storey origins. These are
   facts read from IFC reference data, not human assertions.
3. **BOM.db is artifact** — produced by `RosettaStoneToBOM.py` from manifest + extraction.
   It is never hand-edited. Regeneration is idempotent.
4. **Java reads BOM.db only** — `BuildingRegistry.loadActive()` queries C_DocType. It never
   reads the manifest, never reads extraction DBs, never contains building names.
5. **Shell reads BOM.db or manifest only** — no script contains a building name as a string
   literal (except in log messages). Control flow is driven by query results.
6. **Drift resistance** — if extraction changes element count from 1099 to 1102, the only
   action is re-running `RosettaStoneToBOM.py`. No file is hand-edited. No test threshold
   is manually adjusted.

### 10.7 Migration Path

Migration proceeds in five phases, each self-contained and independently verifiable.

| Phase | Scope | Risk | Status |
|-------|-------|------|--------|
| **A** | Create `construction_manifest.yaml` | None | **DONE** — manifest created with SH, DX, TE, ST entries |
| **B** | `RosettaStoneExtract.py` reads manifest | Low | **DONE** — `BUILDINGS` dict replaced with `yaml.safe_load()` |
| **C** | `RosettaStoneToBOM.py` reads manifest | Medium | **DONE** — `C_DOCTYPE` list replaced with `_build_c_doctype()` |
| **D** | `run_RosettaStones.sh` queries BOM.db | Low | **DONE** — loop replaces case statement, summary dynamic |
| **E** | `run_tests.sh` derives expected counts | Low | Deferred — not a Rosetta Stone concern |

**Gate between phases:** `./scripts/run_RosettaStones.sh all` must produce identical output
after each phase. All phases A–D gated GREEN: SH=55, DX=1099, 0 geometry divergences.

**Note:** TB-LKTN removed from manifest — generative case will be approached fresh once
the EXTRACTED registration pipeline is stable. Terminal (CO_TE) registered but not yet
compiled by Rosetta Stone script (DocBaseType=CO, pending extraction analysis).

---

*Detailed architecture: [`ConstructionAsERP.md`](ConstructionAsERP.md) |
BOM dimensions: [`BIMasBOMConcept.md`](BIMasBOMConcept.md) |
Assembly hierarchy: [`PREFAB_ARCHITECTURE.md`](PREFAB_ARCHITECTURE.md) |
Action roadmap: [`ACTION_ROADMAP.md`](ACTION_ROADMAP.md)*
