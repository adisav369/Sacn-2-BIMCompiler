# DONE — Add FINE logging to verb execution path
> Commit: fd797c1b [S98-logging]

You are a coder for bim-compiler. One bounded task.

## Read first

1. This prompt
2. `orm-core/src/main/java/com/bim/orm/BIMLogger.java` — logging API
3. `DAGCompiler/src/main/java/com/bim/compiler/util/BIMLogger.java` — DAGCompiler delegate
4. `docs/LAST_MILE_PROBLEM.md` §Pipeline Debug

## Context

BIMLogger supports FINE level (default since S97). FINE goes to log file only
(quiet console). But the verb execution path emits nothing at FINE — only
CalibrationDAO and InferenceEngine use it. Verbs use `System.out.printf` or
`BIMLogger.info()`. This means pipeline logs capture no verb detail.

## Gap

When `run_RosettaStones.sh` runs, the pipeline log shows:
```
[INFO ] PIPELINE  STEP 1: MetadataValidator — 55 elements
[INFO ] PIPELINE  STEP 5: CompileStage — 3 storeys
```

But no verb-level detail:
```
[FINE ] VERB      HELLO WORLD SH → singularity OK (1 BUILDING BOM)
[FINE ] VERB      PLACE BOM SH → 55 leaves (order=SH_001)
[FINE ] VERB      BUILD SPATIAL STRUCTURE SH → 3 storeys, 27+2+26 elements
```

## Tasks

### 1. BIM_COBOL verb dispatch — log each verb at FINE

In the verb dispatch loop (wherever `VerbResult` is returned), add:
```java
BIMLogger.fine("VERB", "{} {} → {}", verb.keyword(), args, result.summary());
```

Find:
- `BIM_COBOL/src/main/java/com/bim/cobol/BIMCobolRunner.java` or equivalent dispatcher
- Each verb's `execute()` return — ensure `VerbResult` has a summary string

### 2. DAGCompiler stages — log each stage at FINE

In `CompilationPipeline.java`, after each stage completes:
```java
BIMLogger.fine("PIPELINE", "Stage {} ({}) completed in {}ms", i, stage.name(), elapsed);
```

### 3. IFCtoBOM — log extraction counts at FINE

In `IFCtoBOMPipeline.java`, after each extraction step:
```java
BIMLogger.fine("EXTRACTION", "{}: {} elements → {} BOMs", buildingType, elementCount, bomCount);
```

### 4. BomDropper — log drop detail at FINE

```java
BIMLogger.fine("BOMDROP", "{} → {} leaves (order={}, bom={})", buildingId, leafCount, orderId, bomId);
```

## Rules

- Use `BIMLogger.fine()` — never `System.out.println` for operational trace
- FINE = file only, never console (BIMLogger handles this)
- Keep messages grep-friendly: component tag + structured values
- Don't change any logic — logging only
- Each verb should produce exactly 1 FINE line on success, 1 WARN on failure

## Verification

After changes:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
grep "FINE" logs/pipeline_*SH*.log | head -20
```

Should see verb-level detail in the log file. Console output unchanged.
