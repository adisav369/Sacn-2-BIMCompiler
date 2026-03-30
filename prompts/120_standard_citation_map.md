# Gap 5: Standard Citation Map on RouteBuilders

**Spec:** DISC_VALIDATION_DB_SRS §10.4.12 Gap 5
**Prereq:** P119 DONE (RouteBuilders emit verb lines).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The standard references are already cited in comments inside each RouteBuilder. Extract them into a structured map. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.12 Gap 5 — the spec for this work
3. All 6 RouteBuilders in `BIM_COBOL/src/main/java/com/bim/cobol/route/` — find existing standard citations in comments
4. `DAGCompiler/src/main/java/com/bim/compiler/compliance/ComplianceReport.java` — compliance proof chain consumer

## Problem

RouteBuilders cite standards (NFPA 13, MS 1228, MS 1525, MS 830, ASHRAE 62.1) in comments but don't trace parameters to specific clauses. ComplianceStage (Step 12) needs clause-level traceability for jurisdiction proof chains.

Per §10.4.12 Gap 5 examples:
- FP riser 50mm → NFPA 13 §8.15.8 pipe sizing tables
- SP gradient 1:40 → MS 1228 §5.5.2 minimum gradient for 100mm pipe
- ACMV stock length 3000mm → ASHRAE duct construction standard

## Fix

### 1. Add `standardRef()` to DisciplineRouteBuilder interface

```java
/** Map of parameter name → standard clause reference. */
Map<String, String> standardRefs();
```

### 2. Populate in each builder

Extract from existing comments. Example for FpRouteBuilder:
```java
@Override
public Map<String, String> standardRefs() {
    return Map.of(
        "riser_diameter_mm", "NFPA 13 §8.15.8",
        "header_diameter_mm", "NFPA 13 §8.15.8",
        "branch_diameter_mm", "NFPA 13 §8.15.8",
        "sprinkler_spacing_mm", "NFPA 13 §8.5.2.1",
        "coverage_area_m2", "NFPA 13 §8.5.2"
    );
}
```

### 3. Log and persist

- Log at FINE: `[ROUTE] FP: riser_diameter_mm=50 (NFPA 13 §8.15.8)`
- Pass `standardRefs()` to ComplianceStage via RouteReport so jurisdiction proof chains can reference specific clauses

## Gate

- All 6 builders return non-empty `standardRefs()` maps
- FINE log shows parameter→clause citations per discipline
- DisciplineRouteBuilderTest: add 1 test per builder verifying `standardRefs()` is non-empty
- SH 7/7, TE gate: no regression

## What NOT to do

- Do NOT change routing logic or output
- Do NOT modify CrawlRouter or CrawlOps
- Do NOT modify existing migration files
- Do NOT invent standard references — extract only what's already cited in comments or in §10.4.12
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.12 Gap 5 — standardRef() on RouteBuilders
// Clause-level traceability for ComplianceStage jurisdiction proof chains
```

## Commit

```bash
git add BIM_COBOL/src/main/java/com/bim/cobol/route/*.java \
        DAGCompiler/src/main/java/com/bim/compiler/dsl/RouteExecutor.java \
        PROGRESS.md
git commit -m "[S100-p120] Standard citation map: standardRefs() on 6 RouteBuilders"
```

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- standardRefs() entries per builder (count + sample)
- FINE log showing parameter→clause citations
- DisciplineRouteBuilderTest result
- SH 7/7, TE gate
- Any surprises — document, do NOT fix

---
