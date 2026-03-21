# BIMLogger — Structured Pipeline Logging
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [ConstructionAsERP](ConstructionAsERP.md) · [TestArchitecture](TestArchitecture.md)

**Module:** `orm-core` (`com.bim.orm.BIMLogger`)
**DAGCompiler extension:** `com.bim.compiler.util.BIMLogger` (adds Point3D-aware methods)

---

## Purpose

Timestamped, levelled logging for every pipeline run. Each compilation creates
a log file in `logs/pipeline_{building}_{mode}_{timestamp}.log` that can be
grepped, diffed, and tracked against LAST_MILE_PROBLEM.md checklist items
without requiring human visual inspection.

---

## Levels (iDempiere-style)

| Level | When to use | Console | File |
|-------|-------------|---------|------|
| **ERROR** | Gate FAIL, pipeline abort | stderr | Yes |
| **WARN** | Proof violated, QA check failed, advisory | stderr | Yes |
| **INFO** | Pipeline steps, gate verdicts, summaries | stdout | Yes |
| **FINE** | Per-element proofs, verb expansion, placement details | — | Yes |
| **DEBUG** | Internal trace (format strings, SQL, cache) | — | Yes |

Default level is **INFO**. Set to **FINE** for per-element tracking.
Set via: `BIMLogger.setLevel(BIMLogger.Level.FINE)` or `-Dbim.log.level=FINE`.

---

## API

### Initialization

```java
// Auto-timestamped file (recommended — called by CompilationPipeline.run())
BIMLogger.initForRun("SH");
// → creates logs/pipeline_SH_20260319_143201.log

// Explicit path
BIMLogger.init("logs/custom.log", BIMLogger.Level.FINE);

// Cleanup
BIMLogger.close();
```

### Standard Methods

```java
BIMLogger.error("PIPELINE", "Abort: {}", reason);
BIMLogger.warn("QA",       "[FAIL] countReconciliation — delta={}", delta);
BIMLogger.info("PIPELINE", "STEP {}: {} — {}", stepNum, stageName, detail);
BIMLogger.fine("PROVER",   "[PROVEN] {} — {} — {}", proofId, element, evidence);
BIMLogger.debug("SQL",     "Query: {}", sql);
```

### Specialized Methods

```java
// Pipeline stage tracking
BIMLogger.stage(1, "MetadataValidator", "55 elements");

// Gate verdicts (G1-G6)
BIMLogger.gate("G1-COUNT", "RE_SH", "PASS", "ref=55 out=55 delta=+0");

// Prover results (54 proofs)
BIMLogger.proof("P05", "PROVEN", "IfcDoor:23", "no duplicate position");

// BomValidator QA checks
BIMLogger.qaCheck("countReconciliation", true, "SUM(qty)=48428 extraction=48428");

// Verification with numeric precision
BIMLogger.verification("G2-VOLUME", true, 4.58, 4.58);
```

### DAGCompiler Extensions (com.bim.compiler.util.BIMLogger)

```java
// Geometry-aware (needs Point3D from DAGCompiler)
BIMLogger.placement("IfcDoor", "Door_D1:23", point3d, rotation);
BIMLogger.factoryRoute("MeshBinder", "Door_D1:23", "product-level match");
BIMLogger.extraction("IfcWall", 5, "storey=GF discipline=ARC");
```

---

## Log File Format

Tab-aligned, grep-friendly:

```
# BIM Compiler Pipeline Log
# Run:   Ifc4_SampleHouse_enbloc
# Start: 2026-03-19 14:32:01.234
# Level: INFO
#
2026-03-19 14:32:01.234 [INFO ] PIPELINE     ======================================================================
2026-03-19 14:32:01.235 [INFO ] PIPELINE     PIPELINE: Ifc4_SampleHouse [EXTRACTED]
2026-03-19 14:32:01.300 [INFO ] PIPELINE     STEP 1: MetadataValidator — starting
2026-03-19 14:32:01.450 [INFO ] PIPELINE     STEP 5: WriteStage — starting
2026-03-19 14:32:02.100 [FINE ] PROVER       [PROVEN] P05 — IfcDoor:23 — no duplicate
2026-03-19 14:32:02.200 [INFO ] GATE         [G1-COUNT ] RE_SH PASS  ref=55 out=55 delta=+0
2026-03-19 14:32:02.300 [INFO ] PIPELINE     PIPELINE COMPLETE: Ifc4_SampleHouse — 55 elements
# End: 2026-03-19 14:32:02.350
```

---

## Grep Patterns for LAST_MILE Tracking

```bash
# All FAILs in latest run
grep '\[FAIL\]' logs/pipeline_*.log

# Gate verdicts
grep '\[GATE\]' logs/pipeline_*.log

# Violated proofs only
grep 'VIOLATED' logs/pipeline_*.log

# Pipeline timing (stage starts)
grep 'STEP.*starting' logs/pipeline_*.log

# QA check results
grep '\[QA\]' logs/pipeline_*.log

# Compare two runs
diff <(grep '\[GATE\]' logs/pipeline_SH_20260319_*.log) \
     <(grep '\[GATE\]' logs/pipeline_SH_20260320_*.log)
```

---

## LAST_MILE Checklist → Log Evidence

| # | Checklist | Log pattern to grep |
|---|-----------|-------------------|
| 1 | Input=Output? | `grep 'G1-COUNT.*PASS' logs/pipeline_*.log` |
| 2 | LOD400 geometry? | `grep 'G5-PROVENANCE.*PASS' logs/pipeline_*.log` |
| 3 | Compiler only? | `grep 'G4-TAMPER.*T18' logs/pipeline_*.log` |
| 6 | Output path? | Single compilation path (S54b) — verified via G1-G6 gates |
| 8 | Visual fidelity? | `grep 'PROVER.*VIOLATED' logs/pipeline_*.log` (expect 0) |
| 10 | Who checks tests? | `grep 'SEAL' logs/run_RosettaStones_*.txt` |

---

## Wiring Status

| Module | Class | Wired? | How |
|--------|-------|--------|-----|
| DAGCompiler | CompilationPipeline.run() | **YES** | `initForRun()` on entry, `close()` on exit |
| DAGCompiler | PlacementProver | **YES** | `proof()` per ProofResult in `printReport()` |
| DAGCompiler | RosettaStoneGateTest | **YES** | `gate()` per verdict in `report()` |
| IFCtoBOM | BomValidator | **YES** | `qaCheck()` per check in `report()` |
| IFCtoBOM | IFCtoBOMPipeline | **YES** | `initForRun()` on entry, 7 `stage()` calls, `close()` in finally |
| BonsaiBIMDesigner | DesignerServer | TODO | Wire for Design Mode session logging |

Remaining: DesignerServer (BonsaiBIMDesigner) — wire for Design Mode session logging.
