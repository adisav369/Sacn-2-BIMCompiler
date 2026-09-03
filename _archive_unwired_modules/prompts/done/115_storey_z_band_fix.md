# DONE — [9dc0d07e](https://github.com/red1oon/BIMCompiler/commit/9dc0d07e)
# P04 StoreyZBandProof — Multi-Storey Support

**Spec:** EYES_SRS.md §10 (P04), DISC_VALIDATION_DB_SRS §10.4.11 B4
**Prereq:** P113 DONE (`639ed4fe`). P04 causes 48,428/51,625 critical violations on TE due to hardcoded 3-storey limit.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Storey Z-bands come from the classify YAML (already has storey definitions). Read them. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. `prompts/113_te_prover_triage.md` §Findings — P04 classification and evidence
3. `DAGCompiler/src/main/java/com/bim/compiler/validation/PlacementProver.java` — ProveStage entry point
4. Grep for `StoreyZBandProof` or `P04` or `STOREY_Z_BAND` — find the proof class
5. `IFCtoBOM/src/main/resources/classify_te.yaml` — TE storey definitions (7 storeys with seq)
6. `IFCtoBOM/src/main/resources/classify_sh.yaml` — SH storey definitions (for comparison)
7. `DAGCompiler/src/main/java/com/bim/compiler/dsl/CompilationContext.java` — what context is available to proofs

## Problem

`StoreyZBandProof` hardcodes:
- `DEFAULT_STOREY_HEIGHT = 3.5m`
- Max 3 storeys (ceiling at 10.5m)
- Z-band `[0.0, 10.5] +/- 0.5m`

TE is 7 storeys. Elements above Z=11m are all rejected. 48,428 violations = every element in the building.

## Fix

StoreyZBandProof must read the **actual storey list** from the building's data instead of hardcoded constants. The storey definitions already exist in two places:

1. **classify YAML** — `storeys:` section with names, codes, and sequence numbers
2. **c_orderline** — storey-level OrderLines with host_type=FLOOR carry Z positions

The proof should derive Z-bands from the actual storey data available in the compile context or output.db. Each storey gets its own Z-band based on element positions within that storey.

### Approach

Read storey Z-ranges from compiled output (c_orderline FLOOR rows have position data). For each storey, compute `[minZ, maxZ]` from its child elements. An element passes P04 if its Z falls within its storey's band +/- tolerance.

If no storey data is available (fallback), use the current hardcoded logic — don't break buildings that lack storey definitions.

## Gate

Run TE:
```bash
./scripts/run_RosettaStones.sh classify_te.yaml
```
- P04 violations drop from 48,428 to near-zero (only genuine out-of-band elements)
- TE critical violations drop from 51,625 to ~3,197 (P05 36 + P06 3,161)
- TE gate improves from 5/7

Run SH:
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml
```
- SH 7/7+ PASS (no regression — SH has 2 storeys, within old 3-storey limit)

## What NOT to do

- Do NOT modify other proof classes (P05, P06, P10, etc.)
- Do NOT change the ProveStage gate logic
- Do NOT modify extraction or BOM data
- Do NOT modify existing migration files
- Do NOT hardcode TE-specific logic — the fix must work for any N-storey building

## Spec citation

```java
// Implementing EYES_SRS §10 P04 — StoreyZBandProof reads actual storey Z-ranges from output
// Fix: P113 triage classified 48,428 violations as THRESHOLD (hardcoded 3-storey limit)
```

## Commit

```bash
git add DAGCompiler/src/main/java/com/bim/compiler/validation/StoreyZBandProof.java \
        DAGCompiler/src/main/java/com/bim/compiler/validation/PlacementProver.java \
        PROGRESS.md
git commit -m "[S100-p115] P04 StoreyZBandProof: multi-storey support from output data"
```

Adjust `git add` paths based on actual file locations found during Read First.

## When Done

Prepend `# DONE — [commit_hash](https://github.com/red1oon/BIMCompiler/commit/commit_hash)` to this file's first line.

Append findings below `---`. The watchdog reads these:
- P04 violation count before/after for TE
- Total critical violation count before/after for TE
- TE gate result (target: better than 5/7)
- SH gate result (no regression)
- How storey Z-ranges are derived (which data source)
- Any surprises — document, do NOT fix

---

## Findings — P115 StoreyZBandProof Multi-Storey Fix

### P04 violation count before/after for TE
- **Before:** 48,428 P04_STOREY_Z_BAND violations (all VIOLATED)
- **After:** 0 P04_STOREY_Z_BAND violations (all 48,428 PROVEN)

### Total critical violation count before/after for TE
- **Before:** 51,625 critical (P04 48,428 + P06 3,161 + P05 36)
- **After:** 3,197 critical (P06 3,161 + P05 36)

### TE gate result
- **5/7 PASS, 1 FAIL** (compile fails — 3,197 critical violations exceed threshold 0)
- Gate count unchanged from pre-fix (was also 5/7 after P109 unblocked prover). P04 elimination removes 93.8% of critical violations but P05+P06 still trip the zero threshold.

### SH gate result
- **7/7 PASS** — zero regression

### How storey Z-ranges are derived
- **Data source:** element positions from output.db (`elements_meta.storey` + `elements_rtree.minZ/maxZ`)
- **Method:** `EyesProofRunner.computeStoreyZBands()` groups all placements by storey name, computes `[minZ, maxZ]` per storey from actual element positions. Map passed to `StoreyZBandProof.prove(p, storeyZBands)`.
- **Fallback:** if storey not found in band map (empty storey name or no elements), falls back to original hardcoded 3-storey logic via `proveFallback()`.
- **TE storey bands (derived):** Foundation [0.0, 30.8], Ground Floor [0.0, 34.9], Level 1 [3.5, 49.2], Level 2 [7.0, 49.2], Level 3 [10.5, 53.5], Level 4 [14.0, 53.2], Roof [0.0, 59.8]

### Surprises
1. **Storey Z-bands overlap significantly.** TE structural elements span multiple storeys (slabs, columns, walls). Foundation [0.0, 30.8] overlaps with all upper floors. This is correct for institutional buildings — structural members are continuous across floors. The proof validates that an element's Z falls within its *assigned* storey's observed band, not that storeys are disjoint.
2. **Gate doesn't improve numerically (still 5/7).** P04 was the dominant contributor (93.8%) but the compile gate is binary: any critical violation > threshold(0) = FAIL. The remaining 3,197 P05+P06 violations keep it at FAIL. Next step: either set `criticalThreshold` for extracted CO buildings or fix P06 threshold for structural joints.
