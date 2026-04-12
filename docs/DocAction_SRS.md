# DocAction SRS — Document Lifecycle Engine
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [MANIFESTO](MANIFESTO.md) · [TestArchitecture](TestArchitecture.md)

<div class="bim-banner" markdown>
<b>processIt() orchestrates the Order-to-output compilation lifecycle.</b> Maps iDempiere MOrder.processIt() to BIM compilation with DocEvent validation at each stage.
</div>

**Version:** 1.5 (2026-03-26, session 79 — AD_Org_ID migration, work_output.db cleanup)
**Depends on:** [BOMBasedCompilation.md](BOMBasedCompilation.md) §1.2, [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) §9-10, [DocValidate.md](DocValidate.md) §9-§15, [TE_MINING_RESULTS.md](https://github.com/red1oon/BIMCompiler/blob/master/internal/TE_MINING_RESULTS.md), [BIM_Designer_SRS.md](BIM_Designer_SRS.md) §19
**Scope:** The `processIt()` orchestration — iDempiere MOrder.processIt() mapped to
BIM compilation. How the BOM recipe pipeline and DocEvent Validation interact.

> **Activation rule (BBC.md §1.2):** Each discipline in YAML has three states:
> `{prefix}_BOM` (pipeline populates from named BOM), `DocEvent` (validation
> handles it), or absent (discipline does not exist). No ambiguity, no inference.

> **Rules are DATA, not code.** The engine evaluates AD_Val_Rule rows loaded from
> ERP.db. Adding a new rule = SQL INSERT. Adding a new jurisdiction = SQL
> INSERT. The Java code changes only when the ENGINE changes, not when RULES change.

> **Terminal as oracle:** The Terminal (48,428 elements, 8 disciplines, 7+ storeys)
> is the primary rule source. Every mined rule must pass Non-Disturbance against
> Terminal before activation. The building is ground truth; the rule adapts.

---

## 0. Discipline Routing — Three States

| YAML value | Meaning | Who acts |
|------------|---------|---------|
| `{prefix}_BOM` | Populate from named BOM | BOM pipeline (concern #1) |
| `DocEvent` | Validation handles this discipline | DocEvent engine (concern #2) |
| Absent | Discipline does not exist | Nobody — building has no such discipline |

### 0.1 Discipline Routing Matrix

States are **mutually exclusive per discipline per building.** A discipline
cannot be both `{prefix}_BOM` and `DocEvent` simultaneously.

| Discipline | `{prefix}_BOM` support | `DocEvent` support | Notes |
|------------|----------------------|-------------------|-------|
| **ARC** | YES (all stones) | YES (generative) | Primary structure — always present |
| **STR** | YES (TE) | YES (generative) | Often paired with ARC in residential |
| **FP** | YES (TE) | YES | Seed: ad_fp_coverage, ad_space_type_mep_bom |
| **ELEC** | YES (TE) | YES | Seed: AD_Val_Rule 803, ad_space_type_mep_bom |
| **CW** | YES (TE) | YES (advisory Phase 1) | Topology-driven — density calibration only |
| **SP** | YES (TE) | YES (advisory Phase 1) | Same as CW |
| **ACMV** | YES (TE) | YES (advisory Phase 1) | Diffuser count only — duct routing Phase 2 |
| **LPG** | YES (TE) | Absent | Too few elements for rule mining (209 in TE) |
| **REB** | Absent | Absent | Removed from pipeline (Bonsai addon, not construction BOM) |

**Execution order:** Disciplines process sequentially in the order declared in
YAML `disciplines:` map. Each discipline completes (BOM walk or DocEvent placement)
before the next begins. This ensures Tier 2 cross-discipline clash detection has
a stable baseline from preceding disciplines.

**Invalid state handling:** If YAML declares a value other than a valid BOM
database name or `DocEvent`, processIt() logs `BLOCK: invalid discipline routing`
and aborts without modifying the compile DB. No partial state.

```yaml
# Example: generative house — ARC from BOM, MEP via DocEvent
disciplines:
  ARC:  DM_BOM
  FP:   DocEvent       # validation places + checks sprinklers
  ELEC: DocEvent       # validation places + checks electrical
  CW:   DocEvent       # validation places + checks cold water
  SP:   DocEvent       # validation places + checks sanitary
  # ACMV, LPG absent  — this house has no HVAC or gas
```

**Two concerns, explicit routing:**

| # | Concern | Trigger | What It Does |
|---|---------|---------|-------------|
| 1 | **BOM recipe pipeline** | Discipline = `{prefix}_BOM` | Pipeline populates discipline sub-tree from the named BOM database. |
| 2 | **DocEvent Validation** | Discipline = `DocEvent` | Discovers applicable AD_Val_Rule for this discipline, places elements from mined rules, validates against standards. |

**Shared/common rules** (non-clash, regulatory, AABB containment, vertical
continuity) always fire via DocEvent regardless of per-discipline routing —
these are cross-cutting. They apply to whatever exists in the BOM.

```
YAML.FP = TE_BOM       →  BOM pipeline populates FP from TE_BOM.db
                            Shared DocEvent rules still fire (non-clash, regulatory)

YAML.FP = DocEvent     →  DocEvent places FP elements from mined rules
                            DocEvent validates FP spacing, clearance
                            Shared rules also fire

YAML.FP absent          →  No FP in this building. Nothing fires for FP.
```

---

## 1. processIt() Lifecycle — MOrder.processIt() Mapping

### 1.1 The iDempiere Pattern

```
MOrder.processIt():
  prepareIt()   → validate header, create default lines, reserve inventory
  approveIt()   → authority/credit check
  completeIt()  → fire ModelValidator, create downstream docs (InOut, Invoice)
```

### 1.2 BIM processIt() — DocStatus Lifecycle

```
DocStatus:  DR → IP → CO → AP

  DR (Draft)     User has created the order, not yet processed
  IP (In Progress)  processIt() — normal processing, writes to compile DB
  CO (Complete)     Compiled — written to output.db
  AP (Approved)     New {prefix}_BOM.db created — promoted to catalog
```

### 1.3 IP — processIt() (DR → IP)

Normal order processing. Reads YAML intent, populates compile DB.

```
processIt(compile DB):                               DocStatus: DR → IP
  │
  ├── Read W_BuildingConfig (YAML embedded at spawn time)
  │   → AABB, DocType/ST, jurisdiction, discipline routing
  │
  ├── For each discipline in YAML:
  │   │
  │   ├── IF value = {prefix}_BOM:
  │   │   │  BOM pipeline handles it (concern #1)
  │   │   │
  │   │   ├── SELECT m_bom tree from {prefix}_BOM.db
  │   │   │   WHERE AD_Org_ID = discipline
  │   │   │
  │   │   ├── Walk BOM tree → INSERT C_OrderLine per node
  │   │   │   (family_ref, host_type, dx/dy/dz, aabb_*_mm)
  │   │   │
  │   │   ├── Spatial slots derived from M_BOM_Line dx/dy/dz
  │   │   │   (co_empty_space tables removed S74 — W008)
  │   │   │
  │   │   ├── For LEAF nodes: fetch LOD from component_library.db
  │   │   │   → M_Product (dimensions) + component_definitions (attachment)
  │   │   │   → component_geometries (mesh via geometry_hash)
  │   │   │   → INSERT M_AttributeSetInstance (width, depth, height, material)
  │   │   │
  │   │   └── INSERT W_Verb_Node (verb execution record)
  │   │
  │   └── IF value = DocEvent:
  │       │  DocEvent engine handles it (concern #2)
  │       │
  │       ├── SELECT AD_Val_Rule FROM ERP.db
  │       │   WHERE discipline = ? AND jurisdiction IN (?, 'INTL')
  │       │
  │       ├── Read mined rule params:
  │       │   typical_spacing_mm, max_spacing_mm, min_spacing_mm,
  │       │   occupancy_class, check_method
  │       │
  │       ├── Compute placements from rules + room AABB:
  │       │   pitch = min(max_spacing, dim / ceil(dim / typical_spacing))
  │       │   → grid positions for FP heads, ELEC fixtures, etc.
  │       │
  │       ├── For each computed position:
  │       │   ├── SELECT M_Product from component_library.db
  │       │   │   WHERE ifc_class = rule.ifc_class
  │       │   │
  │       │   ├── Fetch LOD: component_definitions → component_geometries
  │       │   │   (library mesh, NOT parametric — feedback_no_parametric.md)
  │       │   │
  │       │   ├── INSERT C_OrderLine
  │       │   │   (family_ref=product_id, dx/dy/dz=computed tack)
  │       │   │
  │       │   ├── Spatial slot from M_BOM_Line dx/dy/dz
  │       │   │   (co_empty_space tables removed S74 — W008)
  │       │   │
  │       │   └── INSERT M_AttributeSetInstance
  │       │       (width, depth, height from M_Product)
  │       │
  │       └── Tier 1 validate each placement against same rules
  │           → INSERT W_Validation_Result per C_OrderLine
  │
  ├── Shared/common validation (always fires):
  │   │
  │   ├── Tier 2: Cross-discipline clash
  │   │   → ClashDetector.checkFloor() per storey
  │   │   → AD_Clash_Rule pairs (MEP×STR, PLB×ELC, FPR×ACMV...)
  │   │   → ERP-maths clearance: centroid_dist - radius_a - radius_b
  │   │   → INSERT W_Validation_Result (tier=2)
  │   │
  │   ├── Tier 3: Vertical continuity
  │   │   → VerticalContinuityChecker.checkBuilding()
  │   │   → Group by (discipline, ifc_class, ROUND(x), ROUND(y))
  │   │   → Check X,Y drift across storeys ≤ max_xy_drift_mm
  │   │   → INSERT W_Validation_Result (tier=3)
  │   │
  │   └── Regulatory compliance
  │       → AD_Val_Rule WHERE rule_type='COMPLIANCE' (UBBL, IRC, NCC...)
  │       → Room area, min dimension, ceiling height checks
  │       → INSERT W_Validation_Result (tier=1, shared)
  │
  └── DocStatus → IP
      Compile DB now has full C_OrderLine tree + validation results
      User can edit in BIM Designer (slider, drag, add/remove rooms)
```

### 1.3a Error Handling and Idempotency

processIt() is **atomic per discipline** — if a discipline fails, all writes
for that discipline are rolled back (SQLite transaction). Previously processed
disciplines retain their state.

| Error Condition | Behaviour | DocStatus | Recovery |
|----------------|-----------|-----------|---------|
| Missing AD_Val_Rule for DocEvent discipline | BLOCK, log `RULE_NOT_FOUND:{discipline}` | Stays DR | Seed rules, retry processIt() |
| DB constraint violation (NOT NULL, FK) | BLOCK, automatic rollback for that discipline | Stays DR | Fix seed data or BOM template |
| Empty BOM tree (`{prefix}_BOM` has 0 lines) | BLOCK, log `EMPTY_BOM:{bom_id}` | Stays DR | Populate BOM database |
| ad_space_type_mep_bom has 0 rows for room type | WARN (DocEvent places 0 elements for that room) | Proceeds to IP | Seed missing room/product row |
| Partial success (3/5 disciplines OK, 2 BLOCK) | Completed disciplines committed, blocked ones rolled back | IP (partial) | Fix blocked disciplines, re-run processIt() (idempotent — skips completed) |

**Idempotency:** processIt() can be re-run safely. It checks each discipline's
C_OrderLine count — if already populated, it skips that discipline. This handles
the partial-success case: fix the blocked discipline's seed data, re-run, and
only the remaining disciplines execute.

**Audit trail:** Every processIt() invocation writes to `W_Verb_Node`:
- `verb_ref = 'PROCESS_IT'`
- `description = 'FP: 45 placed, ELEC: BLOCK (RULE_NOT_FOUND)'`
- `node_status = 'COMPLETE' | 'PARTIAL' | 'BLOCKED'`

### 1.3b Rotation Rule

`m_bom_line.rotation_rule` stores a single decimal angle in **radians**, applied
as 2D planar rotation around the Z-axis in the parent-local coordinate frame.
3D rotation is NOT supported in the current pipeline.

| Column | Type | Unit | Range | Used By |
|--------|------|------|-------|---------|
| `rotation_rule` | REAL | radians | [0, 2π) | PlacementCollectorVisitor (line 140-149, 214-226) |

**Compound rotation:** When the BOM walker descends the tree (BUILDING → FLOOR
→ SET → LEAF), each node's rotation_rule is cumulative. The walker applies
`parent_rotation + child_rotation` to each child's offset before computing
world coordinates. This is a simple angle sum (2D), not quaternion composition.

**Extraction vs generative:**
- Extracted: rotation_rule is NULL for most elements (no rotation in IFC data).
  Non-null only for MIRROR (DX: π radians) and angled placements.
- Generative: rotation_rule set by placement rules or user in BIM Designer.

### 1.4 CO — completeIt() (IP → CO)

Compile to output.db. The order is now a built building.

```
completeIt(compile DB → output.db):                  DocStatus: IP → CO
  │
  ├── CompilationPipeline.run() — same 12-stage pipeline
  │   → BOM walker reads C_OrderLine tree from compile DB
  │   → Resolves LODs from component_library.db
  │   → Writes elements + spatial + geometry to output.db
  │
  ├── INSERT W_Verb_Node records (execution audit trail)
  │   → One row per verb invocation
  │
  ├── ProveStage (Stage 9) gate
  │   → G1-G6 gates run on output.db
  │   → Generative: must PASS including validation
  │   → Extracted: validation READONLY, gates still check geometry
  │
  └── DocStatus → CO
      output.db exists. Building is compiled. Viewable in Bonsai.
```

### 1.5 AP — approveIt() (CO → AP)

Promote to catalog. Creates new {prefix}_BOM.db. Governance gate.

```
approveIt(compile DB → {prefix}_BOM.db):              DocStatus: CO → AP
  │
  ├── Pre-check: all W_Validation_Result = PASS (no BLOCK)
  ├── Pre-check: all dangles resolved (family_ref → M_Product or m_bom)
  ├── Pre-check: host tack tagging verified (W-TACK-1 invariant)
  │
  ├── Walk C_OrderLine tree:
  │   For each line:
  │     → INSERT INTO m_bom in NEW {prefix}_BOM.db
  │     → INSERT INTO m_bom_line for each child
  │     → entity_type = 'U' (User-created)
  │     → Provenance = 'GENERATIVE'
  │
  ├── New {prefix}_BOM.db is now a reusable catalog entry
  │   → Can be referenced by future YAML as a discipline source
  │   → Other buildings can set YAML.ARC = this new BOM
  │
  └── DocStatus → AP
      Master C_Order frozen. BOM created. Design is now catalog.
```

### 1.6 Database Writes per Phase

| Phase | DocStatus | Database | Tables Written | What |
|-------|-----------|----------|---------------|------|
| processIt | DR→IP | compile DB | C_OrderLine, spatial slots (M_BOM_Line dx/dy/dz), M_AttributeSetInstance, W_Validation_Result | Design decisions + all validation tiers |
| processIt | DR→IP | component_library.db | *(read-only)* | LOD fetch: M_Product → component_definitions → component_geometries |
| processIt | DR→IP | {prefix}_BOM.db | *(read-only)* | BOM tree source for `{prefix}_BOM` disciplines |
| processIt | DR→IP | ERP.db | *(read-only)* | AD_Val_Rule for `DocEvent` disciplines + shared rules |
| completeIt | IP→CO | output.db | elements_meta, element_transforms, element_instances, base_geometries, spatial_structure, c_order, c_orderline | Compiled output |
| completeIt | IP→CO | compile DB | W_Verb_Node, C_Order.DocStatus | Execution audit + status update |
| approveIt | CO→AP | NEW {prefix}_BOM.db | m_bom, m_bom_line | Promoted catalog (governance gate, not common) |

**DR → IP → CO is the common path.** Every building goes through process + compile.
**AP is advanced** — governance gate with strict pre-checks (validation PASS, dangles
resolved, host tack verified). Most buildings stay at CO. AP creates a new reusable
BOM catalog entry.

### 1.4b LOD Assembly + Handlers

See `DISC_VALIDATE_SRS.md` for full detail:
- **§9** — LOD resolution chain (5-table metadata lookup)
- **§9.1-9.5** — DocEvent LOD resolution, quantity modes, placement rules, coverage tables
- **§10** — Post-placement handlers H1-H6 (connectivity, non-clash, spacing, host, vertical, completeness)
- **§10.2** — Common vs DocEvent-only handler classification

### 1.7 Compile DB — Tacking + Discipline Store

The compile DB (BomDropper output) stores all temporal, in-progress state:
**tacking** (spatial positions) and **discipline assignment** (which trade
owns each element). Both are editable during IP and frozen at CO.
(Prior to S61, this role was served by work_output.db; see §1.10.)

| Table | Column | What it stores | Editable during IP |
|-------|--------|---------------|-------------------|
| `C_OrderLine` | dx, dy, dz | LBD tack offset in parent | YES — drag/move |
| `C_OrderLine` | AD_Org_ID | Discipline assignment (FP, ELEC, CW...) | YES — reassign |
| `C_OrderLine` | family_ref | Product/BOM reference | YES — swap product |
| `C_OrderLine` | Parent_OrderLine_ID | Tree hierarchy | YES — reparent |
| `M_BOM_Line` | dx/dy/dz | Spatial offset (WHERE) | YES — follows OrderLine |
| `M_BOM_Line` | aabb_*_mm | Slot AABB capacity | YES — resize |
| `M_AttributeSetInstance` | width/depth/height | Instance sizing | YES — slider |
| `W_Validation_Result` | result | Tier 1/2/3 pass/warn/block | Recomputed on change |
| `W_Verb_Node` | verb_ref | Verb execution | Written at CO |

### 1.8 Discipline Verbs During IP

During IP, discipline assignment is fluid. The user or DocEvent engine can
invoke these verbs on C_OrderLine.AD_Org_ID:

#### REASSIGN — Move element to different discipline

| Field | Value |
|-------|-------|
| **Verb** | `REASSIGN` |
| **Input** | C_OrderLine_ID, target_discipline (AD_Org_ID) |
| **Precondition** | Target discipline node exists under same storey. If not, auto-create. |
| **Writes** | `C_OrderLine.AD_Org_ID`, `C_OrderLine.Parent_OrderLine_ID` |
| **Validation** | Tier 1 re-run on element (new discipline rules). Tier 2 re-run on storey (new pair interactions). |
| **W_Verb_Node** | verb_ref='REASSIGN', description='pipe_42: CW→SP' |

SQL: UPDATE AD_Org_ID + Parent_OrderLine_ID, DELETE+re-INSERT W_Validation_Result.

#### SPLIT — Break combined discipline into sub-disciplines

| Field | Value |
|-------|-------|
| **Verb** | `SPLIT` |
| **Input** | source_discipline (e.g. 'MEP'), split_map (ifc_class → target_discipline) |
| **Precondition** | Source discipline node exists with mixed elements. |
| **Writes** | New C_OrderLine discipline nodes (host_type=DISCIPLINE). UPDATE AD_Org_ID + Parent_OrderLine_ID per element. |
| **Validation** | Full Tier 1+2+3 re-run (discipline landscape changed). |
| **W_Verb_Node** | verb_ref='SPLIT', description='MEP→CW(338)+SP(189)+FP(312)+ELEC(65)' |

Disambiguation heuristic: (1) unambiguous ifc_class → direct map, (2) ambiguous
(IfcPipeSegment) → check element_ref string, (3) no match → stay in source, WARN.
Source node → IsActive=0 (soft delete, audit trail).

#### MERGE — Combine disciplines into one

| Field | Value |
|-------|-------|
| **Verb** | `MERGE` |
| **Input** | source_disciplines[] (e.g. ['SP']), target_discipline (e.g. 'CW') |
| **Precondition** | All source + target discipline nodes exist under same storey. |
| **Writes** | UPDATE AD_Org_ID + Parent_OrderLine_ID. Source nodes → IsActive=0. |
| **Validation** | Tier 1 re-run (target rules apply). Tier 2 re-run (fewer pairs). |
| **W_Verb_Node** | verb_ref='MERGE', description='SP→CW (single plumbing contract)' |

#### REPARENT — Move element to different storey or room

| Field | Value |
|-------|-------|
| **Verb** | `REPARENT` |
| **Input** | C_OrderLine_ID, target_parent_id (storey/room/discipline node) |
| **Precondition** | Target parent exists. Same discipline (use REASSIGN to change discipline). |
| **Writes** | `C_OrderLine.Parent_OrderLine_ID`, `C_OrderLine.dz` (recomputed). Spatial slot cache updated. |
| **Validation** | Tier 1 on BOTH old and new parent (spacing recalc). Tier 3 re-check (vertical continuity may break). |
| **W_Verb_Node** | verb_ref='REPARENT', description='sprinkler_23: GF→L1' |

#### SWAP — Replace product, keep position

| Field | Value |
|-------|-------|
| **Verb** | `SWAP` |
| **Input** | C_OrderLine_ID, new_product_id |
| **Precondition** | New product exists in component_library.db. Same ifc_class family (don't swap a pipe for a light). |
| **Writes** | `C_OrderLine.family_ref`. New `M_AttributeSetInstance` (dimensions from new product). |
| **Reads** | component_library.db: M_Product → component_definitions → component_geometries (new LOD). |
| **Validation** | Tier 1 re-run (same spacing rules, different product dims/attachment). |
| **W_Verb_Node** | verb_ref='SWAP', description='sprinkler: pendant→upright' |

### 1.9 Discipline Lifecycle Summary

See §1.2-1.3 for the full processIt() lifecycle (DR → IP → CO → AP) and
§1.8 for discipline verbs available during IP (REASSIGN, SPLIT, MERGE, REPARENT, SWAP).

### 1.10 Construction Standards Localization — iDempiere C_Country Pattern

In iDempiere, `C_Tax` is localized by `C_Country_ID` + `ValidFrom`. The tax
engine is generic — the data drives country-specific behaviour. BIM validation
follows the same pattern: `AD_Val_Rule.jurisdiction` + `AD_Val_Rule.valid_from`.

#### Localization Model

```
iDempiere:                         BIM Validation:
  C_Country → C_Tax                  jurisdiction → AD_Val_Rule
  C_Tax.Rate = 6%                    AD_Val_Rule_Param.value = '3000'
  C_Tax.ValidFrom = 2024-01-01       AD_Val_Rule.valid_from = '2012'
  C_Tax.C_TaxCategory_ID             AD_Val_Rule.discipline + rule_type

W_BuildingConfig.jurisdiction        ← set at project creation
  → SELECT AD_Val_Rule WHERE jurisdiction IN (?, 'INTL')
  → Jurisdiction-specific rules + international common rules
```

#### Jurisdiction Registry

| Code | Country | Primary Standards | Residential | Fire | Electrical | Plumbing |
|------|---------|-------------------|-------------|------|-----------|----------|
| MY | Malaysia | UBBL 2012 | Room sizes §33, ceiling §36, corridor §40, door §41 | SS CP 52 | SS CP 5 | SS CP 48 |
| US | United States | IRC 2021 / IBC 2021 | R304 room, R305 ceiling, R311 egress | NFPA 13 | NEC (NFPA 70) | IPC |
| UK | United Kingdom | Building Regs / NDSS 2015 | NDSS room sizes, Part B fire | BS 9251 | BS 7671 | — |
| AU | Australia | NCC 2022 | F5/10.3 heights, door 820mm, corridor 1000mm | AS 2118.1 | AS/NZS 3000 | AS/NZS 3500 |
| SG | Singapore | BCA Approved Docs | Ceiling 2400mm, corridor 1200mm, door 850mm | SS CP 52 | SS CP 5 | SS CP 48 |
| INTL | International | — | — | NFPA 13 (baseline) | NEC 300.4 (baseline) | — |

#### Common Standards (INTL — always loaded)

Engineering practice rules that fire regardless of jurisdiction: NFPA 13 sprinkler
spacing (3000-4600mm), NEC 300.4 conduit-pipe clearance (150mm), column vertical
continuity (25mm drift), riser vertical continuity (50mm drift), sprinkler-duct
obstruction (3x rule), opening host association.

#### Jurisdiction-Specific Standards

Detailed rule tables (MY rules 101-110, US 201-205, UK 301-304, AU 401-404,
SG 501-503) are seeded in `V004_mined_rules.sql` and documented in
[DocValidate.md](DocValidate.md) §9-§11 with per-jurisdiction thresholds.

#### Construction Verbs

Three verb categories — all jurisdiction-independent (same engine, different thresholds):

- **Joining verbs** (FIT, JOIN, ATTACH, SCREW, BOLT, WELD, CLAMP, MOUNT, EMBED, HANG) — how elements connect. Recorded on W_Verb_Node.
- **Placement verbs** (TILE, ROUTE, ARRAY, PLACE, SNAP) — how elements are positioned. See [BIM_COBOL.md](BIM_COBOL.md) §4.6 for details.
- **Validation verbs** (CHECK PLACEMENT, VERIFY PLACEMENT, CONNECT FITTINGS) — how compliance is checked. Results in W_Validation_Result.

See [DocValidate.md](DocValidate.md) for jurisdiction-specific rule packs and
[BIM_COBOL.md](BIM_COBOL.md) §4.6 for joining verb details and W_Verb_Node recording.

#### Adding a New Jurisdiction

Same as adding a new country to iDempiere — data only, no code:

```
1. INSERT INTO AD_Val_Rule (jurisdiction='NZ', ...)
   → New Zealand Building Code rules
2. INSERT INTO AD_Val_Rule_Param (...)
   → Specific thresholds for NZ
3. User creates project: W_BuildingConfig.jurisdiction = 'NZ'
4. processIt() → SELECT AD_Val_Rule WHERE jurisdiction IN ('NZ', 'INTL')
   → NZ-specific + international common rules fire
   → Zero Java changes
```

#### Mined Rules — Terminal as Calibration

Mined rules (M1-M17) from Terminal carry `jurisdiction='INTL'` and
`valid_from='MINED:Terminal'`. They represent observed engineering
practice from a real building — not a specific code. They serve as:

1. **Calibration** — the Terminal values confirm what spacing/clearance
   real engineers used in practice (not just the code minimum)
2. **Typical values** — `typical_spacing_mm` (from mining) vs `max_spacing_mm`
   (from code). DocEvent uses typical for placement, max for validation.
3. **Non-Disturbance baseline** — every mined rule must pass against the
   Terminal it was mined from. See `TE_MINING_RESULTS.md`.

### 1.11 Architectural Simplification: work_output.db Removal (S61)

**Decision:** `work_output.db` is removed from the architecture. The result is a
**4-DB split**: component_library / ERP (incl. compliance rules) / BOM / output.

**Rationale:** The BOM is read-only (EntityType D). The user cannot edit it — only
promote it as a new BOM (approveIt). Save = Blender native save (.blend file).
The viewport (Bonsai/Blender) holds all visual state. There is no intermediate
design buffer to persist — work_output.db was a buffer between edits and
compilation that has no edits to buffer.

**Lifecycle:** See §1.2-1.3 for the full DR → IP → CO → AP flow. Post-S61, all
compile-time state lives in the compile DB (no work_output.db intermediary).

**Output DB path:** The output path is editable in the Web UI (Tab 1 — Order).
Defaults to `output/{project_name}.db` from C_DocType.OutputDbPath. User can
rename before each CompleteIt to version their builds (e.g. `output/demo_v2.db`).

**What moves where:**

| Was in work_output.db | Now lives in | Notes |
|-----------------------|-------------|-------|
| C_Order + C_OrderLine | Compile DB | BomDropper already writes here |
| M_AttributeSetInstance | Compile DB | ASI overrides per C_OrderLine |
| ~~co_empty_space_line~~ | *(removed S74 — W008)* | Placement via M_BOM_Line dx/dy/dz |
| W_Validation_Result | Compile DB | Validation results |
| W_Verb_Node | Compile DB | Verb audit trail |
| W_Variant | **Removed** | No variants — BOM is read-only, no edit history |
| W_BuildingConfig | **Removed** | YAML config lives in C_DocType.DSLContent |

**Promote conditions (approveIt → AP):** A new BOM must declare:
- **Parent category:** M_Product_Category_ID (e.g. RESIDENTIAL, COMMERCIAL) — determines where it appears in the selection list
- **Qualified name:** Unique, descriptive (e.g. `BUILDING_SH_V2_KITCHEN_SWAP`) — prevents clutter
- **Author / version:** Metadata for traceability
- **Children validated:** All child BOM references must resolve (no dangling FKs)

Without these, promote is blocked. The selection list stays organized.

**S61 completed.** Sections §1.6-§1.9 already reflect post-removal compile DB architecture.

---

## 2. Validation Requirements

<!-- Inner numbering note: §3.1-§5.2, §8.1-§8.3, §9.1-§9.3, §10.1-§10.3, §13.1-§13.3
     are sub-sections within §2's requirement groups, not top-level sections.
     Legacy numbering preserved for cross-reference stability. -->

| Prefix | Domain | Count |
|--------|--------|-------|
| DV-F | Functional (rule engine behaviour) | 20 |
| DV-N | Non-functional (performance, scale) | 6 |
| DV-E | Error/edge-case handling | 8 |
| DV-T | Testability (witness, Non-Disturbance) | 6 |

Priority: **P0** = needed for generative path, **P1** = needed for ambient compliance, **P2** = future (clash resolution, auto-fix).

---

### 2.1 Current State — What Exists

| Component | Status | Location |
|-----------|--------|----------|
| `PlacementValidator` interface | DONE | `validation/PlacementValidator.java` |
| `PlacementValidatorImpl` | DONE — Tier 1 COMPLIANCE only | `validation/PlacementValidatorImpl.java` |
| `InferenceEngine` | DONE — Kahn's topo sort, proof tree | `validation/InferenceEngine.java` |
| `PlacementRequest` / `ValidationVerdict` | DONE | `validation/` records |
| AD_Val_Rule schema | DONE — in ERP.db | `migration/V002_validation_schema.sql` |
| Mined rules M1-M17 seed data | DONE | `migration/V004_mined_rules.sql` |
| UBBL/IRC/UK/AU/SG residential rules | DONE — seeded | `DocValidate.md §9-§11` |
| Non-Disturbance analysis | DONE — documented | `TE_MINING_RESULTS.md` |
| **Tier 2 engine (clash/clearance)** | **PARTIAL — ClashDetector.java exists (bbox-intersection), but does not match the AD_Clash_Rule-driven engine in §4.1** | Spec: DocValidate.md §4, §13.2 |
| **Tier 3 engine (vertical continuity)** | **NOT DONE** | Spec: DocValidate.md §12, §13.3 |
| **ERP-maths spatial predicates** | **IMPLEMENTED — SpatialPredicates.java: nnDistance(), centreClearance()** | Spec: DocValidate.md §15.6 |
| **ConstructionModelSpawner** | **NOT DONE** | Spec: DocValidate.md §14-§15.2 |
| **Non-Disturbance automated test** | **NOT DONE** | Spec: DocValidate.md §7.2, §15.3 |

> **Note: Two separate rule ecosystems.** ERP.db (63 rules,
> jurisdiction/discipline/rule_type schema) and ERP.db (415 rules,
> provenance/ifc_class/check_method schema) are separate ecosystems with
> incompatible schemas. ERP.db drives the DocEvent engine (this spec).
> ERP.db drives the extraction pipeline (see ConstructionAsERP.md).

---

### 2.2 Functional Requirements — Tier 1 Enhancements (DV-F)

### 3.1 check_method Dispatch

The current `PlacementValidatorImpl.extractActual()` only handles 4 param names
(`min_area_m2`, `min_dim_mm`, `min_height_mm`, `min_width_mm`). Mined rules use
richer `check_method` params that require dedicated evaluation logic.

| ID | Requirement | Acceptance Criteria | Priority | Rule(s) |
|----|------------|-------------------|----------|---------|
| DV-F-01 | **check_method dispatch.** When AD_Val_Rule_Param has a `check_method` row, the engine calls the corresponding evaluator instead of simple threshold comparison. | `evaluateRule()` checks for `check_method` param first. If present, dispatches to named method. If absent, falls back to threshold comparison. | P0 | M2, M3, M5, M7, M8, M16, M17 |
| DV-F-02 | **ERP-maths NN distance.** `check_method=NN_CENTROID_DISTANCE` computes planar (XY) nearest-neighbour distance between same-class elements on the same storey. | Given N elements of class C on storey S, compute MIN(XY_dist) for each element. Compare to `min_spacing_mm` and `max_spacing_mm` params. Return BLOCK if outside range. | P0 | M1, M4, M6 |
| DV-F-03 | **ERP-maths clearance.** `check_method=CENTRELINE_CLEARANCE` computes centreline-to-centreline distance minus half cross-section of each element. | `clearance = centroid_2D_dist - radius_a - radius_b` where `radius = MIN(width, depth) / 2`. Same formula as TE_MINING_RESULTS.md M12 §Attempt 2. | P0 | M12 |
| DV-F-04 | **ROUTE segment sum.** `check_method=ROUTE_SEGMENT_SUM` sums lengths of pipe segments sharing a verb_ref run from TEE to terminal. | Query `m_bom_line WHERE verb_ref LIKE 'ROUTE%'`, group by run_id, SUM allocated_width_mm (longest dim = run length). Compare to `max_run_length_mm`. | P1 | M2 |
| DV-F-05 | **M_Product cross-section.** `check_method=M_PRODUCT_CROSS_SECTION` validates pipe/conduit diameter from product dimensions. | `cross_section = MIN(width, depth)` from M_Product. Compare to `min_main_diameter_mm` / `min_branch_diameter_mm`. Distinguish main vs branch by parent BOM hierarchy. | P1 | M3 |
| DV-F-06 | **Per-storey Z consistency.** `check_method=PER_STOREY_Z_CONSISTENCY` checks that same-class elements on a storey have consistent Z. | Compute STDDEV(center_z) for elements of class C on storey S. Compare to `max_z_deviation_mm`. WARN if exceeded. | P1 | M5 |
| DV-F-07 | **TILE verb fidelity.** `check_method=TILE_VERB_FIDELITY` verifies TILE step consistency against product dims. | Already proven by pipeline (0.0mm fidelity). This check confirms post-compilation: TILE(rows×cols, step_mm) matches placed element spacing. | P2 | M8 |
| DV-F-08 | **Beam span vs bay.** `check_method=BEAM_LENGTH_VS_BAY` checks beam product width against nearest column pair spacing. | For each IfcBeam, find two nearest IfcColumn on same storey. Beam width ≤ column_pair_dist × (1 + tolerance_pct/100). | P1 | M7 |
| DV-F-09 | **Opening face-anchor.** `check_method=CENTROID_DEPTH_VS_HOST_CENTER` validates door/window centering in host wall. | `depth_offset = |opening_centroid_depth - host_wall_center|`. Compare to `max_depth_offset_mm`. Skip if ASI has `face_anchor=INT|EXT`. Skip if host wall thinner than `min_host_wall_thickness_mm`. | P1 | M16 |
| DV-F-10 | **Opening host association.** `check_method=AABB_PROXIMITY` verifies every opening (IfcDoor/IfcWindow) has a nearby IfcWall. | Find nearest IfcWall by centroid distance. If distance > `proximity_tolerance_mm`, WARN. Future: upgrade to FK check when `host_element_ref` column lands (R20). | P1 | M17 |

### 3.2 Verdict Types

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| DV-F-11 | **Three verdicts.** Engine returns PASS, WARN, or BLOCK per rule. WARN = advisory (logged, shown in ambient strip). BLOCK = hard stop (prevents compilation). | `ValidationVerdict` gains `WARN` level. Rules with `verdict=WARN` param never return BLOCK. Default = BLOCK if no verdict param. | P0 |
| DV-F-12 | **ADJUST verdict.** When a BLOCK can be auto-corrected (e.g., snap dimension to minimum), return ADJUST with suggested values. | `ValidationVerdict.adjust(suggestedWidth, suggestedDepth)`. BIM Designer slider auto-corrects to suggested value on ADJUST. | P1 |

---

### 2.3 Functional Requirements — Tier 2: Cross-Discipline Clash (DV-F)

### 4.1 ClashDetector Engine

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| DV-F-13 | **ClashDetector class.** Reads AD_Clash_Rule from ERP.db, evaluates element pairs across discipline boundaries on the same storey. | `ClashDetector.checkFloor(Connection bomConn, Connection valConn, String storeyId)` returns `List<ClashResult>`. Uses R-tree (`elements_rtree`) to narrow candidates, then applies AD_Clash_Rule filters. | P1 |
| DV-F-14 | **Clash types.** HARD (intersection), SOFT (close proximity), MATERIAL (penetration through rated assembly), CLEARANCE (min distance). | `AD_Clash_Rule.clash_type` drives check logic: HARD = AABB overlap, SOFT = overlap margin, MATERIAL = overlap + fire_rating check, CLEARANCE = min_distance_mm. | P1 |
| DV-F-15 | **ALLOW_IF conditional verdicts.** When clash verdict = `ALLOW_IF`, check condition column. If condition met (e.g., fire_stop_product_id IS NOT NULL), pass. | `ClashResult` has `conditionMet` boolean. If ALLOW_IF and condition not met, return BLOCK with `resolution_note` from AD_Clash_Rule. | P2 |

### 4.2 Terminal-Inferred Clash Rules

Source: DocValidate.md §4.1, TE_MINING_RESULTS.md M9-M12

| Rule ID | Disciplines | Type | Threshold | Evidence from Terminal |
|---------|------------|------|-----------|----------------------|
| 701 | MEP × STR | HARD | 0mm (intersection) | Beams at z=7.7-25.9m, pipes route around |
| 702 | PLB × ELC | CLEARANCE | 150mm | M12: 11 true overlaps, 35 under 150mm |
| 703 | FPR × ACMV | CLEARANCE | 300mm (3× obstruction) | NFPA 13 obstruction rule |
| 7/8 | SP/ACMV × ARC (fire-rated) | MATERIAL | 0mm + fire_stop | M11: requires fire collar |

### 4.3 ERP-Maths for Cross-Discipline Distance

```
clearance(A, B) = centroid_2D_distance(A, B) - radius(A) - radius(B)

where:
  centroid = (center_x, center_y)          -- from element_transforms
  radius(E) = MIN(width, depth) / 2        -- cross-section from M_Product
  width/depth from AABB or M_Product dims  -- same data BOM already carries
```

This is the **proven M12 formula** from TE_MINING_RESULTS.md:
- Uses only M_Product dimensions + placement positions
- No mesh, no Blender, no viewport
- Works at compile time, design time, and batch time
- 48,428-element Terminal validates in sub-second (SQL aggregation)

---

### 2.4 Functional Requirements — Tier 3: Vertical Continuity (DV-F)

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| DV-F-16 | **Vertical continuity checker.** Groups elements by (discipline, ifc_class, ROUND(x,1), ROUND(y,1)) across storeys. Checks X,Y drift ≤ tolerance. | `VerticalContinuityChecker.checkBuilding(conn, buildingId)` returns `List<ContinuityResult>`. Each result: element_group, storeys_spanned, max_drift_mm, verdict. | P1 |
| DV-F-17 | **Storey span minimum.** Only flag vertical groups spanning ≥ `min_storey_span` storeys. Single-storey elements skip. | Rule param `min_storey_span` (default 3 for risers, 2 for columns). Groups spanning fewer storeys return PASS. | P1 |

### 5.1 Terminal-Inferred Vertical Rules

Source: V004_mined_rules.sql rules 809-811

| Rule | Discipline | Elements | Max Drift | Min Storeys | Terminal Evidence |
|------|-----------|----------|-----------|-------------|-------------------|
| M13 (809) | CW | IfcPipeSegment | 50mm | 3 | CW risers at GF/L1/L2/L3/L4 — consistent x,y |
| M14 (810) | STR | IfcColumn | 25mm | 2 | 56 GF columns + 4 L2 columns — grid positions fixed |
| M15 (811) | FPR | IfcPipeSegment | 50mm | 3 | FP risers span all storeys, tapped per floor |

### 5.2 Query Pattern (from Terminal DB)

```sql
-- Elements with vertical continuity: same (x,y) across multiple storeys
SELECT em.discipline, em.ifc_class,
       ROUND(et.center_x, 0) as grid_x,
       ROUND(et.center_y, 0) as grid_y,
       COUNT(DISTINCT ss.storey) as storey_count,
       GROUP_CONCAT(DISTINCT ss.storey) as storeys,
       MAX(et.center_x) - MIN(et.center_x) as x_drift,
       MAX(et.center_y) - MIN(et.center_y) as y_drift
FROM elements_meta em
JOIN element_transforms et ON em.guid = et.guid
JOIN spatial_structure ss ON em.guid = ss.guid
WHERE em.ifc_class IN ('IfcPipeSegment', 'IfcColumn')
GROUP BY em.discipline, em.ifc_class,
         ROUND(et.center_x, 0), ROUND(et.center_y, 0)
HAVING COUNT(DISTINCT ss.storey) >= 2
ORDER BY storey_count DESC;
```

**Interpretation:** Groups with `x_drift` or `y_drift` > tolerance = WARN/BLOCK.
Groups spanning all storeys with <25mm drift = genuine vertical continuity (columns).
Groups spanning 3+ storeys with <50mm drift = MEP risers.

---

## 3. Non-Functional Requirements (DV-N)

| ID | Requirement | Threshold | Rationale |
|----|------------|-----------|-----------|
| DV-N-01 | **Tier 1 latency.** Single PlacementRequest validation. | < 5ms (in-memory rule lookup) | Real-time slider feedback in BIM Designer |
| DV-N-02 | **Tier 2 latency.** Cross-discipline clash check per floor. | < 200ms for 1000 elements per floor | Ambient compliance strip update |
| DV-N-03 | **Tier 3 latency.** Vertical continuity for entire building. | < 500ms for Terminal-scale (48K elements) | On-demand check, not real-time |
| DV-N-04 | **Rule loading.** activate(jurisdiction) loads all rules. | < 50ms for any jurisdiction | One-time cost on session start |
| DV-N-05 | **Scale.** Validation engine handles Terminal-scale data. | 48,428 elements, 8 disciplines, 7 storeys | Terminal is the largest Rosetta Stone |
| DV-N-06 | **Memory.** Cached rules fit in JVM heap. | < 1MB for 100 rules × 10 params each | Rules are small (ints, strings, doubles) |

---

## 4. Error and Edge-Case Handling (DV-E)

| ID | Scenario | Expected Behaviour | Priority |
|----|---------|-------------------|----------|
| DV-E-01 | ERP.db missing or unreadable | `activate()` throws with clear message. BIM Designer shows "Validation rules unavailable" in ambient strip. Compilation proceeds without validation (DISABLED mode). | P0 |
| DV-E-02 | Rule has unknown `check_method` | Log WARN, skip rule. Do NOT block compilation for unimplemented check methods. Return SKIP in InferenceEngine results. | P0 |
| DV-E-03 | Circular rule dependency | InferenceEngine already handles (Kahn's cycle detection). All cycle members → SKIP. Logged. | DONE |
| DV-E-04 | Element has no M_Product match | ERP-maths checks require product dimensions. If product_id not in M_Product, skip element with WARN log. | P0 |
| DV-E-05 | Storey name mismatch | Terminal uses Malay names ("Aras Tanah", "Aras 01"). DX uses English ("Level 1"). Storey matching must use the storey column from spatial_structure, not hardcoded names. | P0 |
| DV-E-06 | Element has no element_transforms | Some elements may lack centre coordinates. Skip with WARN, do not crash. | P0 |
| DV-E-07 | ALLOW_IF condition references non-existent product | ALLOW_IF fire_stop_product_id check: if product doesn't exist in library, treat as condition-not-met → BLOCK with resolution note. | P1 |
| DV-E-08 | Jurisdiction mismatch | Rule has jurisdiction='MY', project has jurisdiction='US'. Rule not loaded. INTL rules always load. | P0 |

---

## 5. Testability Requirements — Non-Disturbance (DV-T)

### 8.1 Non-Disturbance Protocol

> The building is ground truth. Rules must describe reality, not prescribe it.

| ID | Requirement | Acceptance Criteria | Priority |
|----|------------|-------------------|----------|
| DV-T-01 | **Non-Disturbance gate test.** Automated test runs all active rules against SH, DX, and TE extracted data. | `NonDisturbanceTest` class. For each stone: load rules, run Tier 1+2+3, assert 0 BLOCK verdicts (excluding documented AD_Val_Rule_Exception). | P0 |
| DV-T-02 | **Exception accounting.** Known exceptions (DX P23: 364, TE M12: 35) are counted and verified against AD_Val_Rule_Exception. | Test loads exceptions, verifies count matches expected. If new exceptions appear, test FAILS until documented. | P0 |
| DV-T-03 | **Rule regression.** Any rule parameter change re-triggers Non-Disturbance. | Test compares V004 migration checksum. If changed, full re-run. CI gate. | P1 |

### 8.2 Witness Claims

| Witness | What it Proves | Depends On |
|---------|---------------|------------|
| W-DV-ND-SH | Non-Disturbance PASS for SampleHouse (58 elements, ARC+STR+CW) | DV-T-01 |
| W-DV-ND-DX | Non-Disturbance PASS for Duplex (1099 elements, multi-discipline) | DV-T-01 |
| W-DV-ND-TE | Non-Disturbance PASS for Terminal (48428 elements, 8 disciplines) | DV-T-01 |
| W-DV-T2-CLR | Tier 2 clearance: M12 ELEC-SP detection matches TE_MINING_RESULTS | DV-F-03, DV-F-13 |
| W-DV-T3-COL | Tier 3 vertical: STR column continuity matches Terminal grid | DV-F-16 |
| W-DV-PERF | Tier 1+2+3 on Terminal completes within latency contracts | DV-N-01..03 |

### 8.3 Anti-Regression: Rule Decision Tree

When Non-Disturbance finds a violation on a Rosetta Stone:

```
Violation found:
├─ Is it in AD_Val_Rule_Exception?
│   YES → Expected. COUNT matches? PASS. Count changed? FAIL (investigate).
│   NO ──┐
│        ├─ Is this design intent? (engineer approved deviation)
│        │   YES → ADD AD_Val_Rule_Exception. Document reason. Re-run.
│        │   NO ──┐
│        │        ├─ Is the rule tolerance too tight?
│        │        │   YES → Widen param. Re-run. Document in V004 comment.
│        │        │   NO → Rule is wrong. Fix or remove. Re-derive.
```

---

## 6. Data Flow — The Three-Tier Cascade (Code-Level)

Three tiers fire during processIt() (see §1.3 for full pseudocode):

| Tier | Hook | Engine | Input → Output |
|------|------|--------|---------------|
| **1** | ModelValidator.beforeSave | `PlacementValidatorImpl.validate(PlacementRequest)` | PlacementRequest (category, dims, position) → `ValidationVerdict` (PASS/WARN/BLOCK + rule ref) |
| **2** | DocAction.prepareIt | `ClashDetector.checkFloor(bomConn, valConn, storeyId)` | AD_Clash_Rule pairs per storey → `List<ClashResult>` (element pairs, clash type, verdict) |
| **3** | DocAction.completeIt | `VerticalContinuityChecker.checkBuilding(bomConn, valConn, buildingId)` | CONTINUITY rules, element groups by (discipline, ifc_class, ROUND(x), ROUND(y)) → `List<ContinuityResult>` (drift per group, verdict) |

Tier 1 dispatches by `check_method` if present, else threshold comparison (see DV-F-01).
Tier 2 uses M12 clearance formula (see §4.3). Tier 3 checks x/y drift across storeys (see §5.1).

---

## 7. Integration Points

### 10.1 BIM Designer Ambient Compliance

DocValidate.md §9.5 + BIM_Designer_SRS.md §18.4:

```
Ambient strip shows live validation status:
  [GREEN] 12/12 rules PASS | [YELLOW] 1 WARN: ELEC spacing | [RED] BLOCK: bedroom < 3000mm

Tier 1: fires on every slider change (< 5ms)
Tier 2: fires on floor completion or manual trigger (< 200ms)
Tier 3: fires on building compile or manual trigger (< 500ms)
```

### 10.2 Compile Bridge

When BIM Designer compiles (G-6 CompileBridge):
1. Tier 1 validation runs during BOM creation (beforeSave)
2. Tier 2 runs after all discipline BOMs populated (prepareIt)
3. Tier 3 runs after all floors compiled (completeIt)
4. If mode=ACTIVE and any BLOCK → compilation halted, user notified
5. If mode=READONLY → all results logged, compilation proceeds

### 10.3 ProveStage (Stage 9) Gate

For generative buildings (Provenance=GENERATIVE):
- `ProveStage` includes validation as a sub-gate
- All three tiers must PASS (excluding documented exceptions)
- Gate FAILS if any un-documented BLOCK verdict exists

For extracted buildings (Provenance=EXTRACTED):
- Validation runs in READONLY mode
- Gate PASSES regardless of validation results
- Results logged for analytics/reporting only

---

## 8. Implementation Plan — Phased Delivery

### Phase 1: check_method Dispatch (DV-F-01..10)

**Files to modify:**
- `PlacementValidatorImpl.java` — add check_method dispatch in `evaluateRule()`
- `InferenceEngine.java` — mirror check_method dispatch
- `PlacementRequest.java` — extend with spatial fields (centerX/Y/Z, product dims)

**New file:**
- `SpatialPredicates.java` — reusable ERP-maths functions:
  - `nnDistance(List<Element>, Element) → double` (planar XY)
  - `centreClearance(Element, Element) → double` (M12 formula)
  - `perStoreyZConsistency(List<Element>) → double` (stddev)

**Test:** `CheckMethodDispatchTest.java` — one test per check_method

### Phase 2: ClashDetector (DV-F-13..15)

**New files:**
- `ClashDetector.java` — loads AD_Clash_Rule, evaluates discipline pairs
- `ClashResult.java` — result record

**Data:** AD_Clash_Rule already seeded (rules 701-703, 7-8 in V004)

**Test:** `ClashDetectorTest.java` — use Terminal data, verify M12 counts match

### Phase 3: VerticalContinuityChecker (DV-F-16..17)

**New files:**
- `VerticalContinuityChecker.java` — groups elements, checks drift
- `ContinuityResult.java` — result record

**Test:** `VerticalContinuityTest.java` — verify Terminal column grid

### Phase 4: Non-Disturbance Automated Gate (DV-T-01..03)

**New file:**
- `NonDisturbanceTest.java` — runs all tiers against SH/DX/TE
- Loads AD_Val_Rule_Exception, accounts for documented deviations
- CI gate: fails build if any un-documented BLOCK

### Phase 5: ConstructionModelSpawner (DocValidate.md §14)

**Deferred** until G-7 (Assembly Builder). Depends on:
- C_Order / C_OrderLine schema in compile DB (G-4 DONE)
- W_Verb_Node table (not yet created)
- Full BOM tree walking (BOMWalker exists)

---

## 9. Traceability Matrix

| SRS Requirement | Spec Reference | Code Location | Test Witness |
|----------------|---------------|---------------|-------------|
| DV-F-01 | DocValidate.md §15.5 | PlacementValidatorImpl.evaluateRule() | W-DV-CHECK-DISPATCH |
| DV-F-02 | TE_MINING_RESULTS.md M1 | SpatialPredicates.nnDistance() | W-DV-ND-TE |
| DV-F-03 | TE_MINING_RESULTS.md M12 | SpatialPredicates.centreClearance() | W-DV-T2-CLR |
| DV-F-13 | DocValidate.md §4 | ClashDetector.checkFloor() | W-DV-T2-CLR |
| DV-F-16 | DocValidate.md §12 | VerticalContinuityChecker.checkBuilding() | W-DV-T3-COL |
| DV-T-01 | DocValidate.md §7.2 | NonDisturbanceTest | W-DV-ND-SH/DX/TE |
| DV-N-01..03 | BIM_Designer_SRS.md §18.4 | Performance assertions | W-DV-PERF |

---

## 10. Terminal DB as Rule Oracle — Spatial Evidence Summary

Terminal spatial footprints (48,428 elements, 8 disciplines, 7+ storeys) extracted
via mining pipeline. Position data from `elements_meta`, `element_transforms`,
`spatial_structure`. See [TE_MINING_RESULTS.md](https://github.com/red1oon/BIMCompiler/blob/master/internal/TE_MINING_RESULTS.md) for full
M1/M4/M5/M6/M12 distributions, discipline footprints, and implied placement rules
(structural grid, FP coverage, riser continuity, ceiling alignment, z-zone ordering).

---

## 11. Migration — V005_depends_on.sql

The `depends_on` column in AD_Val_Rule enables rule dependency chains
(InferenceEngine Kahn's sort). This migration adds the column if missing.

```sql
-- V005_add_depends_on.sql
-- Add depends_on FK for rule dependency chains (InferenceEngine)
ALTER TABLE AD_Val_Rule ADD COLUMN depends_on INTEGER REFERENCES AD_Val_Rule(ad_val_rule_id);
```

Already tracked as untracked file: `migration/V005_add_depends_on.sql`.

---

*References:
[DocValidate.md](DocValidate.md) (master validation spec) |
[TE_MINING_RESULTS.md](https://github.com/red1oon/BIMCompiler/blob/master/internal/TE_MINING_RESULTS.md) (Terminal mining data) |
[BIM_Designer_SRS.md](BIM_Designer_SRS.md) §18-19 (ambient compliance, inference) |
[BOMBasedCompilation.md](BOMBasedCompilation.md) §3-4 (tack, BUFFER) |
[TestArchitecture.md](TestArchitecture.md) (G1-G6 gates, traceability) |
[V004_mined_rules.sql](https://github.com/red1oon/BIMCompiler/blob/master/migration/V004_mined_rules.sql) (seeded rules)*
