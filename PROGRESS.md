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

**Blocking investigation (before any code plan):**
1. Rosetta Stone 123-vs-56 gap: identify phantom/stub/prop elements in ST_SH vs SH, build digest filter
2. C_OrderLine dual-DB schema: design output.db c_orderline table for EXPLODE-generated lines

**Pipeline work (after investigation):**
3. Extend CompilationPipeline ESLine to room/furniture-set level (TODO-ST-3)
4. EN-BLOC singularity detection in CompilationPipeline (C_BPartner match + exactly one BOM)
5. EXPLODE walk path: generate C_OrderLine + ESLine per BomCategoryLine slot, write to output.db
6. Rosetta Stone proof: filtered SpatialDigest(SH)==SpatialDigest(ST_SH)
7. Populate m_bom_line dx/dy from reference IFC centroids
8. VerbStage full execution via SPI pattern (break circular dep DAGCompiler/BIM_COBOL)
9. RelationalResolver deletion sprint (SpatialPlacementVisitor needs independent coord resolution)
10. TB-LKTN INVENTION STOP: compiler halts if component_library lookup returns nothing
11. Terminal BOM modelling: extract/craft BOMs from Terminal IFC (third Rosetta Stone)
12. Mesh2Library compiler dispatch: roof hardcoded, need family_ref to ParametricMesh
13. G8-DX calibration investigation (NULL-bound rooms)

**Known debt (advisory, no gates):** Terminal IfcReinforcingBar GIC(8), DX P23 MEP corners(364), TB P23 drain(6), TB furniture alignment, TB fans 1500mm(5), WARDROBE_SET/BATHROOM_VANITY_SET empty children, DX "Room not enclosed"(21)
