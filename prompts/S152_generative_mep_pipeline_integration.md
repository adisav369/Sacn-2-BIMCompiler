# S152 — Generative MEP Pipeline Integration + Test Hardening

**Prior work:** S151 committed (7 commits). Generative MEP works in unit tests (S10-S17, 17/17 PASS). SH pipeline 9/9 PASS with 82=58+24 generative. DX pipeline 9/9 PASS with 1119 but **0 generative** — GENERATIVE channel silent.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## CRITICAL: Development Cycle (README Mantra)

1. **Follow specs before coding.** Read the relevant SRS section.
2. **Write tests before coding.** The test defines "done".
3. **Analyse debug logs and review code to fix.** If logs don't reveal the issue, improve logging first.
4. **If you need to change code, change specs first.** Then back to step 1.

## The Problem: DX Generative MEP = 0 in Pipeline

S16 test walks DX_BOM.db directly with erpConn → 329 placements (215+114 generative). But the full pipeline (`run_RosettaStones.sh classify_dx.yaml`) produces 1119 elements with 0 generative. The GENERATIVE log channel is completely silent.

### Root Cause Investigation (S151 findings)

1. **CompileStage wires erpConn** (line 523 of CompilationPipeline.java). This is correct.
2. **DX SET BOMs have correct categories** (LIVING=83, KITCHEN=84, BEDROOM=87, BATHROOM=88) — all map to active ad_space_type entries.
3. **DX SET BOMs have non-zero AABBs** — data is present.
4. **Pipeline log shows 0 GENERATIVE entries** — the generative block in PlacementCollectorVisitor never fires during the pipeline run.
5. **Pipeline compiles from `library/_DX_compile.db`** — this is a copy of DX_BOM.db prepared by the pipeline script. Verify that the SET BOMs and their categories survive the copy.

### Hypothesis: _DX_compile.db Missing Categories

The pipeline script copies DX_BOM.db → _DX_compile.db for BomDropper. The compile DB may not have M_Product_Category (it's a compile-time staging DB, not the full BOM DB). The `resolveSpaceType()` query joins M_Product_Category on the BOM DB — but `bom.db` system property points to `_DX_compile.db`, not `library/DX_BOM.db`.

**Check:** `sqlite3 library/_DX_compile.db "SELECT * FROM M_Product_Category LIMIT 5"` — if empty, the category lookup fails silently and generative MEP never fires.

### Test Weakness (Unacceptable Situation)

The current tests do NOT prove generative MEP works in the full pipeline:

1. **S16/S17 tests** walk the BOM DB directly — they bypass the pipeline's compile DB copy, BomDropper, OrderLine expansion, and BuildingWriter.
2. **BuildingRegistryTest** checks element count but can PASS with generative=0 if expected_elements was already the full count.
3. **No test** captures the GENERATIVE log channel and asserts on it.
4. **No test** runs the full pipeline end-to-end and verifies generative elements exist in output.db.
5. **LOD-BIND logging** was added but never triggered because the pipeline failure was upstream.

### What This Session Must Do

#### Phase 1: Diagnose (Mantra Step 3)

1. Check `library/_DX_compile.db` — does it have M_Product_Category? Does resolveSpaceType() work against it?
2. Add a forensic log at the entry point of the generative block: `BIMLogger.info("GENERATIVE", "CHECK bomType={} category={} spaceType={}")` — log even when spaceType is null.
3. Run the pipeline, read the log, identify the silent failure.

#### Phase 2: Test First (Mantra Step 2)

Write a test that:
1. Creates `_compile.db` the same way the pipeline does
2. Walks it with erpConn
3. Asserts generativeCount > 0
4. Asserts GeoProofRecords with GENERATIVE chain exist
5. Asserts LMP: all OK, Envelope: identifies ceiling overshoots

This test fails BEFORE you fix the code. That's the point.

#### Phase 3: Fix (Mantra Step 3-4)

Fix the root cause (likely: wire category data into compile DB, or read categories from the BOM DB source). Update specs if the fix changes the architecture.

#### Phase 4: Pipeline Green (Full Stack)

Run `./scripts/run_RosettaStones.sh classify_dx.yaml` and verify:
- GENERATIVE SUMMARY shows N > 0 devices
- LOD-BIND shows every generative device resolved to geometry
- Output.db element count = extracted + generative
- 9/9 gates PASS

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `DAGCompiler/README.md` §Development Cycle (the mantra)
3. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.4 (Space Identity — all subsections)
4. `scripts/run_RosettaStones.sh` — how it prepares _compile.db
5. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationPipeline.java` — CompileStage
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` — generative block
7. `DAGCompiler/src/test/java/com/bim/compiler/contract/MepRouteGeometryTest.java` — S10-S17

## Gate

- DX: 9/9 PASS + GENERATIVE SUMMARY shows N > 100 devices
- SH: 9/9 PASS + GENERATIVE SUMMARY shows 24 devices
- MepRouteGeometryTest: 17+/17+ PASS
- New integration test: compile DB walk → generativeCount > 0
- LOD-BIND: 0 NO-MATCH, 0 NO-BOUNDS for generative devices
- GeoProofRecords: LMP 100% OK for all generative devices
- Pipeline log file captures GENERATIVE channel (verify grep finds entries)
