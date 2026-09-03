# CE Multi-Leg ROUTE Fix — VerbDetector Multi-Segment Routing

**Spec:** DISC_VALIDATION_DB_SRS §10.4.10, LMP §7
**Prereq:** P131 DONE (Z-guard), P132 DONE (PATTERN + GEO-ROUTE logging)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** The GEO log has the evidence. Read it. No invention.

## Read first

1. `PROGRESS.md` §Current State
2. P131 findings: CE 11 elements, worst=12,858mm drift, "multi-leg ROUTE — deferred"
3. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java` — detectRoute()
4. GEO log from CE: `grep "GEO.*ROUTE\|GEO.*DRIFT" logs/pipeline_*CE*.log`
5. PATTERN log from CE: `grep "PATRN" logs/pipeline_*CE*.log` — storey Z-bands

## Problem

CE has 11 elements with DRIFT after P131. Root cause: multi-leg ROUTE groups
where elements form an L-shape or T-shape (not a single linear run). The
current detectRoute() assumes all elements in a group lie along ONE axis.
When a pipe run turns a corner, detectRoute() picks the wrong axis for some
elements, placing them on the wrong leg.

## Investigation (do this FIRST)

1. Run CE with GEO + PATTERN enabled:
   ```bash
   rm library/CE_BOM.db
   ./scripts/run_RosettaStones.sh classify_ce.yaml 2>&1 | tee /tmp/ce_debug.log
   ```

2. From GEO log, extract the 11 drifting elements:
   ```bash
   grep "DRIFT" /tmp/ce_debug.log | grep -v "DRIFT=0"
   ```

3. For each drifting element, check its CLUSTER fallback reason (P124 diagnostic):
   ```bash
   grep "CLUSTER fallback" /tmp/ce_debug.log | grep "<product_id>"
   ```

4. Determine: are these elements that SHOULD be ROUTE but have multi-leg paths?
   Or should they be CLUSTER (and the issue is in ROUTE misclassification)?

## Fix

Based on investigation, either:

**A) Multi-leg ROUTE detection:** Split L/T-shaped groups into linear segments
before ROUTE detection. Each segment gets its own ROUTE verb with correct axis.

**B) Fallback to CLUSTER:** If multi-leg ROUTE is too complex, ensure these
elements fall to CLUSTER (identity mapping, zero drift). The Z-guard from
P131 should catch them — investigate why it doesn't.

## Gate

- CE: DRIFT from 39,900 → near 0
- SH: 7/7 PASS (zero regression)
- IN: 7/7 PASS (zero regression)

## What NOT to do

- Do NOT modify PlacementCollectorVisitor or BOM walker
- Do NOT modify existing migration files
- Do NOT weaken the Z-guard from P131
- **All logging via BIMLogger — no System.out.println**

## Spec citation

```java
// Implementing DISC_VALIDATION_DB_SRS §10.4.10 — multi-leg ROUTE detection
// Witness: W-DRIFT-CE (39900→0)
```

## Commit

```bash
git add IFCtoBOM/src/main/java/com/bim/ifctobom/VerbDetector.java PROGRESS.md
git commit -m "[S101-p134] CE multi-leg ROUTE fix: split L/T groups into linear segments"
```

## When Done

Prepend `# DONE — [commit_hash]` to this file's first line.

Append findings below `---`:
- Root cause (multi-leg or misclassification?)
- CE DRIFT before/after
- Which fix path (A or B)?
- SH/IN regression check

---
