# DONE
# TE IFCtoBOM — Phase 2: Remove DISCIPLINE SET, Flatten to FLOOR→LEAF

**Priority:** The real blocker. 471 tack overflows + 36 unbalanced BOMs.
Root cause: DISCIPLINE SET is a tree level with its own AABB — but
disciplines are not spatial containers. Fix: remove the SET level.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Discipline is a line attribute (AD_Org_ID),
not a tree level. Read the spec before touching code.

## Read first

1. `docs/DISC_VALIDATION_DB_SRS.md` §10.4.1–§10.4.4 — the spatial model:
   discipline is AD_Org on the line, not a BOM level. Space + Occupant +
   Verb + Rule. Impact on BOM tree.
2. `docs/BOMBasedCompilation.md` §4.0–§4.3 — LBD tack convention:
   `dx = child.minX - parent.minX`. World coord = origin + Σ(tack).
3. `docs/MANIFESTO.md` §AD_Org — disciplines cut ACROSS the category
   tree, they don't appear AS levels within it.
4. `IFCtoBOM/src/main/java/com/bim/ifctobom/DisciplineBomBuilder.java`
   — the code that creates DISCIPLINE SET BOMs (lines 131–178).
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/VerbFactorizer.java:176`
   — LEAF tack: `dx = e.minX() - parentMinX`. Currently parentMinX =
   floor min (correct), but the intermediate SET BOM causes the
   BomValidator to check against SET AABB (wrong).

## Understanding the problem

Current hierarchy (wrong):
```
BUILDING → FLOOR → DISCIPLINE SET → LEAF
                   ↑ has own AABB
                   ↑ 471 tack overflows because MEP elements span
                     beyond the SET's envelope
```

Correct hierarchy (same as SH/DX):
```
BUILDING → FLOOR → LEAF (each line carries AD_Org_ID)
```

Why the overflows happen: `DisciplineBomBuilder` computes a per-discipline
AABB (line 140–148) and creates a SET BOM. But FP pipes span the entire
floor. ARC plates overhang 14m beyond STR. CW risers cross storeys. The
SET AABB can't contain its children because a discipline is not a room —
it has no walls.

The tack math itself is correct (`e.minX() - fMinX` at VerbFactorizer:176).
The bug is the intermediate SET level that BomValidator checks against.
Remove the SET → overflows vanish.

## Task

### Step 1: Flatten DisciplineBomBuilder — remove SET level

In `DisciplineBomBuilder.java`, change the hierarchy from
BUILDING → FLOOR → SET → LEAF to BUILDING → FLOOR → LEAF.

**Note on bom_type:** Do NOT rename BUILDING/FLOOR values. These are
legacy labels. The fix is to stop code from branching on them — the
tree structure and M_Product_Category carry the semantics. A bridge
will have `bom_type='BUILDING'` (cosmetic) with `M_Product_Category=BRIDGE`
(semantic). Infrastructure vocabulary lives in the category, not bom_type.

**Remove** (lines 131–166):
- The `byDiscipline` grouping loop
- The SET BOM header creation (`insertBomHeader` for SET)
- The MAKE line from FLOOR → SET

**Replace with:** Write LEAF lines directly under the FLOOR BOM.
Each LEAF line carries the discipline as `bom_category` (existing column)
which maps to AD_Org_ID. The `VerbFactorizer.factorize()` call stays
the same — just change the parent BOM ID from `discBomId` to `floorBomId`.

Before:
```java
VerbFactorizer.factorize(bomConn, discBomId, discElems, fMinX, fMinY, fMinZ, 10, true);
```

After:
```java
VerbFactorizer.factorize(bomConn, floorBomId, discElems, fMinX, fMinY, fMinZ, discSeq, true);
```

The discipline grouping still happens in the `byDiscipline` loop for
verb factorization (same-product grouping per discipline). But the BOM
lines land under FLOOR, not under a SET.

### Step 2: Add AD_Org_ID / bom_category on LEAF lines

Ensure each LEAF line written by VerbFactorizer carries the discipline
code. Check `VerbFactorizer.factorize()` — it calls `insertBomLine()`.
The `bom_category` parameter should be the discipline code (ARC, STR,
FP, etc.), not null.

### Step 3: Update BomValidator

W-TACK-1 and W-BUFFER-1 currently check SET→LEAF. After flattening,
they check FLOOR→LEAF. The SQL joins `m_bom_line l JOIN m_bom b ON
l.M_BOM_ID = b.M_BOM_ID` — this automatically checks the parent, which
is now FLOOR. No SQL change needed if the SET BOMs are removed.

Verify: run QA on the flattened BOM — W-TACK-1 should show 0 overflows
because all tacks are relative to FLOOR (which contains everything).

### Step 4: Remove CO passthrough hack

`CompilationPipeline.java:352-354` hardcodes `"CO"` to skip CompileStage
and pass extraction coordinates to output unchanged. Three violations:

- **Anti-Drift §1** — magic coordinates
- **DriftGuardTest D6** — hardcoded category branch
- **LMP §7** — input = output

**DELETE the CO skip block entirely:**

```java
// DELETE this block from CompilationPipeline.java:
if ("CO".equals(ctx.entry().mProductCategoryId())) {
    ctx.setSpec(new BuildingSpec(ctx.entry().projectName(), List.of(), null));
    return true;
}
```

CO buildings must compile through the same path as RE. If CompileStage
fails for CO, that failure must be visible — not bypassed.

### Step 5: Verify TE BOM counts

After flattening, the BOM should have:
- 1 BUILDING BOM (unchanged)
- 7 FLOOR BOMs (unchanged)
- 0 SET BOMs (removed)
- ~1,515 LEAF lines under FLOOR BOMs (was under SET BOMs)
- 48,428 total instances (unchanged — factorization preserved)

## What NOT to do

- Do NOT create new BOM levels — flatten, don't restructure
- Do NOT change the tack convention (BBC §4 is the spec)
- Do NOT change VerbFactorizer tack math (it's already correct)
- Do NOT change SH/FK/DX behaviour — they don't use DisciplineBomBuilder
- Do NOT weaken W-TACK-1 / W-BUFFER-1 checks
- Do NOT change BomValidator QA thresholds
- Do NOT invent a new discipline column — use existing `bom_category`

## Verify

1. `mvn compile -q` — PASS
2. Run `./scripts/run_RosettaStones.sh classify_te.yaml`
   - IFCtoBOM QA: W-TACK-1 PASS (0 overflows)
   - IFCtoBOM QA: W-BUFFER-1 PASS (7/7 FLOOR BOMs balanced)
   - TE_BOM.db populated (non-empty, ~1,515 lines, 48,428 instances)
   - BOM count: 8 (1 BUILDING + 7 FLOOR), not 58
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)
4. `./scripts/run_RosettaStones.sh classify_fk.yaml` — FK PASS (no regression)
4. `./scripts/run_RosettaStones.sh classify_te.yaml` — should now
   compile (BomDrop + pipeline) and produce c_order + c_orderline
5. Tamper seal: `bash scripts/verify_test_seal.sh` — reseal if
   changed files are in manifest

## Commit message

```
[S100-p66] TE: flatten DISCIPLINE SET → FLOOR→LEAF with AD_Org_ID

Remove DISCIPLINE SET as BOM tree level. LEAF lines write directly
under FLOOR with bom_category=discipline code. Tack parent = floor.
W-TACK-1: 471→0 overflows. W-BUFFER-1: 7/7 balanced. BOM count:
58→8 (1 BUILDING + 7 FLOOR). Delete CO passthrough hack from
CompilationPipeline. Ref: DISC_VALIDATION_DB_SRS.md §10.4.1–§10.4.4.
```

## When Done

Prepend `# DONE` + commit hash to this file's first line.
