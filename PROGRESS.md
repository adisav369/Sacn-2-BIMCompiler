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
SH=e858ce01 | DX=91e158bd | TB=41132f60 | Terminal=fed88a1a | ST_SH=24d97489
*(DX/TB/ST_SH updated again in EN-BLOC session 2026-03-01 — query DB for current values)*

## Next Work

**Investigations DONE (Rounds 1–6, §11.1–11.28):**
1. ~~Rosetta Stone gap~~ — Element Identity + BBox envelope. MEP excluded. LOD cross-sampling digest.
2. ~~199mm door~~ — naming bug. 2 extra doors (D7/D2) = BOM errors.
3. ~~FlowTerminal~~ — m_bom_line references IfcFlowTerminal stub instead of Tall_Cabinet.
4. ~~Matching~~ — exact AABB + BomCategory DocType (Residential/Commercial). No ε tolerance.
5. ~~Priority~~ — M_BomCategoryLine.Sequence (user-defined defaults).

**Data fixes (before pipeline work):**
6. Fix m_bom_line: KITCHEN_CABINET_SET → child_product_id='Tall_Cabinet' (not IfcFlowTerminal)
7. Fix m_bom_line: TOILET_BLOCK_FIXTURES → child_product_id='IfcSanitaryTerminal' (not IfcFlowTerminal)
8. Fix ST_SH BOM: remove 2 extra doors (D7, D2) not in SH reference
9. Fix ST_SH furniture: compiler must pick exact SH-extracted products (AABB + category match)
10. Add M_BomCategory.doc_type column (Residential/Commercial/Industrial)
11. C_Order + C_OrderLine migration to output.db (ERP transactional schema)

**Pipeline work:**
8. Furniture placement: parent AABB fit = wholesale port; non-fit = priority-based ESLine placement
9. EN-BLOC singularity detection in CompilationPipeline
10. EXPLODE walk path: generate C_OrderLine + ESLine per BomCategoryLine slot, write to output.db
11. Rosetta Stone proof: two-layer digest (product_id hash + BBox vertex hash)
12. Populate m_bom_line dx/dy from reference IFC centroids
13. VerbStage full execution via SPI pattern (break circular dep DAGCompiler/BIM_COBOL)
14. RelationalResolver deletion sprint
15. TB-LKTN INVENTION STOP + priority-based furniture placement
16. Terminal BOM modelling (third Rosetta Stone)
17. Mesh2Library compiler dispatch: roof hardcoded, need family_ref to ParametricMesh
18. G8-DX calibration investigation (NULL-bound rooms)

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
