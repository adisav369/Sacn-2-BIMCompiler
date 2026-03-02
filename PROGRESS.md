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

## Model Design — COMPLETE (Q&A1 Rounds 1–7, §11.1–11.35)

All architectural ambiguities resolved. See `docs/ConstructionAsERP.md` §11 for full design decisions.

**Core principles confirmed:**
- **No invention:** every element traces to component_library.db (extracted from IFC)
- **3D exact AABB:** no tolerance, mismatches are data errors
- **BOM.db = read-only dictionary**, output.db = fresh each run
- **Digest = mathematical output=input proof** (elements_meta + RTREE, not ESLines)
- **VerbStage before Write** (gradual COBOL-over-assembler takeover)
- **expected_elements:** fixed for EXTRACTED, auto-calculated for GENERATIVE
- **output.db self-contained:** c_order + c_orderline copied from BOM.db at compile time (compiled_at, compiler_version, computed digest/elements/checksum)

## Next Work

**Data fixes (done 2026-03-02):**
1. ~~Trace structural door drift~~ — TRACED: ST_SH legacy compiled path invents D2+D7 doors. Fix: add c_orderline for ST_SH.
2. ~~Furniture AABB drift~~ — FIXED: all SH furniture allocated dims updated to match IFC extraction (bed, desk, piano, sofa, chairs, tables).
3. ~~KITCHEN_CABINET_SET IfcFlowTerminal → Tall_Cabinet~~ — was already applied.
4. ~~TOILET_BLOCK_FIXTURES~~ — already correct (EXHAUST_FAN is legitimately IfcFlowTerminal, sanitary items already IfcSanitaryTerminal).
5. ~~M_BomCategory.doc_type column~~ — ADDED (Residential/Commercial/Industrial). Java ORM updated. 13 residential, 10 generic (NULL).
6. ~~C_Order fresh-output.db pattern~~ — DONE: c_order + c_orderline tables added to output.db initSchema. WriteStage copies BOM.db rows. DigestStage writes computed results (spatial_digest, expected_elements, checksum). doc_status IP→CO.

**Remaining data fixes:**
6a. Add c_orderline entries for ST_SH (3 doors matching SH) → moves to metadata-driven path, stops door invention
6b. SIDE_TABLE_A/B in SH_LIVING_SET (610×610×610) — no IFC match, possible invention. Investigate.

**Pipeline work (after data fixes):**
7. Furniture placement: parent AABB fit = wholesale port; non-fit = priority-based ESLine
8. EN-BLOC singularity detection in CompilationPipeline
9. EXPLODE walk: C_OrderLine + ESLine per BomCategoryLine slot → output.db
10. Rosetta Stone digest: sorted BBox vertex hash per element class, structural union BBox
11. VerbStage execution: SPI interface, move before Write, gradual MEP verb takeover
12. Populate m_bom_line dx/dy from reference IFC centroids
13. RelationalResolver deletion sprint
14. TB-LKTN INVENTION STOP + priority-based furniture placement
15. Terminal BOM modelling (third Rosetta Stone)
16. Mesh2Library compiler dispatch
17. G8-DX calibration investigation (NULL-bound rooms)

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
