# PROGRESS — Current Development State

## Current State

**Gate:** `./scripts/run_tests.sh` — **282 PASS / 1 RED / 1 SKIP**

| Suite | Count |
|---|---|
| DAGCompiler | 192 runs (191 PASS, G8-DX RED x1, 1 SKIP) — 2 surefire executions |
| ORMSandbox | 25/25 |
| TopologyMaker | 19/19 |
| BIM_COBOL | 48/48 |

**Intentional RED:** G8-DX (NULL-bound room calibration)
**Pre-existing REDs (outside gate):** X1-SH-GAP, X1-DX-GAP (StructuralCrossCheck), livingSetOverflowFillersZero (TopologyMaker)

**Pipeline:** 9 stages — Metadata, OrderLine, BOM, Template, Compile, Write, Verb, Prove, Digest
**BIM COBOL:** 10 verbs, 48 witnesses. VerbRegistry + ScriptRunner (PREP-1+2 done).

**5 Active Buildings:**

| Building | Mode | expected_elements | EmptySpaceChecksum (L0) |
|---|---|---|---|
| Ifc4_SampleHouse (SH) | EXTRACTED | 56 | b14f0c02c4602a14 |
| Ifc2x3_Duplex (DX) | EXTRACTED | 1099 | 1f6f2018dbda2faa |
| TB_LKTN | GENERATIVE | 139 | eb9188e164bc3156 |
| SJTII_Terminal | EXTRACTED | 51088 | — |
| ST_SH | GENERATIVE | 123 | — |

**SpatialDigests:** Stored in `c_order.spatial_digest`, enforced by BuildingRegistryTest.
Formula: name-agnostic bbox + COUNT per class. DB is authoritative; prefixes for reference:
SH=e858ce01 | DX=3df01e98 | TB=a4d2be7c | Terminal=fed88a1a | ST_SH=24d97489
*(TB updated 2026-03-02 after KITCHEN_CABINET_SET Tall_Cabinet fix — query DB for current values)*

## Model Design — COMPLETE (Q&A1 Rounds 1–7, §11.1–11.37)

All architectural ambiguities resolved. See `docs/ConstructionAsERP.md` §11 for full design decisions.

**Core principles confirmed:**
- **No invention:** every element traces to component_library.db (extracted from IFC)
- **3D exact AABB:** no tolerance, mismatches are data errors
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Digest = mathematical output=input proof** (elements_meta + RTREE, not ESLines)
- **VerbStage before Write** (gradual COBOL-over-assembler takeover)
- **expected_elements:** fixed for EXTRACTED, auto-calculated for GENERATIVE
- **output.db self-contained:** c_order + c_orderline copied from BOM.db at compile time
- **C_DocType model:** DocBaseType (RE/CO/IN) drives template selection, DocSubType (SH/DX/TB) drives BOM scoping. Replaces dual building_type + c_bpartner. Borrowed from iDempiere C_DocType.
- **Selection cascade:** AABB fit (primary) → largest volume → seq_no tiebreaker (lower preferred)

## Completed Work (2026-03-02 to 2026-03-03)

**Data fixes (2026-03-02):**
1. ~~Trace structural door drift~~ — TRACED: ST_SH legacy compiled path invents D2+D7 doors.
2. ~~Furniture AABB drift~~ — FIXED: all SH furniture allocated dims match IFC extraction.
3. ~~KITCHEN_CABINET_SET IfcFlowTerminal → Tall_Cabinet~~ — was already applied.
4. ~~TOILET_BLOCK_FIXTURES~~ — already correct.
5. ~~M_BomCategory.doc_type column~~ — ADDED (Residential/Commercial/Industrial).
6. ~~C_Order fresh-output.db pattern~~ — DONE: c_order + c_orderline in output.db initSchema.

**Model fixes (2026-03-03):**
6a. ~~Add c_orderline entries for ST_SH in BOM.db~~ — WRONG APPROACH (Q&A1 §11.2). Compiler generates C_OrderLines at compile time.
6c. ~~M_BOM.seq_no column~~ — ADDED. Selection cascade: AABB fit → volume → seq_no.
6d. ~~RE template decoupled from ST~~ — RE.C_BPartner_ID=NULL. Template lookup by doc_type. BomTemplateComposer takes docType parameter.
6e. ~~C_DocType table~~ — CREATED with 5 entries. X_C_DocType + MCDocType PO classes live. DocBaseType + DocSubType model documented in §11.36.

## Next Work

### Migration: c_bpartner → C_DocType + doc_sub_type (§11.37)

C_DocType table and PO classes are live. The migration below renames columns and rewires references. **Execute in new session.**

**Phase M1 — Schema migration (BOM.db):**
- `ALTER TABLE m_bom RENAME COLUMN c_bpartner TO doc_sub_type`
- `ALTER TABLE c_order ADD COLUMN C_DocType_ID TEXT REFERENCES C_DocType`
- Backfill C_DocType_ID from building_type + c_bpartner
- Verify all c_order rows have valid C_DocType_ID

**Phase M2 — Java PO rename (9 classes):**
- X_M_BOM/MBOM: `c_bpartner` → `doc_sub_type` (column constant, getter, setter, queries)
- X_C_Order (ORMSandbox + TopologyMaker): add C_DocType_ID column
- BomTemplateContract: `cbpartner` param → `docSubType`

**Phase M3 — Business logic (4 files):**
- CompilationPipeline: `WHERE c_bpartner = ?` → `WHERE doc_sub_type = ?`
- BuildingRegistry: load C_DocType_ID, expose via BuildingEntry
- EN-BLOC/EXPLODE: DocSubType match replaces c_bpartner match

**Phase M4 — Witness tests (4 files):**
- W-OWNER-1/2, W-CBPARTNER-1 → renamed to W-DOCTYPE-* series

**Phase M5 — Documentation (~161 mentions):**
- BIMasBOMConcept.md, ConstructionAsERP.md, METADATA_DRIVEN_ARCHITECTURE.md
- Q&A1.txt: add clarification note (historical references stay)

### Pipeline work — C_OrderLine generation

The key mechanism (already half-implemented in TemplateStage/BomTemplateComposer):
- DocBaseType → look up template (RE for Residential) → walk M_BomCategoryLine tree
- System creates C_OrderLines per template slot with AABB from Z ratios × parent AABB
- Each C_OrderLine → best-fit M_BOM via selection cascade (AABB → volume → seq_no)
- GF recurses → room-level C_OrderLines → leaf BOMs → actual elements from component_library

BomTemplateComposer ALREADY does the template walk → NodeSelection records. Currently those only become CO_EmptySpaceLines. The missing step: NodeSelection → C_OrderLine in output.db.

7. **Template → C_OrderLine generation**: Write C_OrderLines from NodeSelection records to output.db. Each NodeSelection with a selectedBomId = one C_OrderLine.
8. **Element generation from C_OrderLines**: C_OrderLines replace the compiled DSL path (StoreyCompiler). Doors/furniture/MEP from BOM tree, not DSL heuristics.
9. EN-BLOC singularity: when DocSubType matches and exactly one BOM fits → single C_OrderLine, no walk

### Remaining tasks

6b. SIDE_TABLE_A/B in SH_LIVING_SET (610×610×610) — no IFC match, possible invention. Investigate.
10. Rosetta Stone digest: sorted BBox vertex hash per element class, structural union BBox
11. VerbStage execution: SPI interface, move before Write, gradual MEP verb takeover
12. Populate m_bom_line dx/dy from reference IFC centroids
13. RelationalResolver deletion sprint
14. TB-LKTN INVENTION STOP + priority-based furniture placement
15. Terminal BOM modelling (third Rosetta Stone)
16. Mesh2Library compiler dispatch
17. G8-DX calibration investigation (NULL-bound rooms)

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
