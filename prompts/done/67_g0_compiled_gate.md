# DONE
# G0-COMPILED Gate + Rosetta Script Fail-Loud — Prevent TE Recurrence

**Priority:** Verification hardening. Prevents extraction-only outputs
from silently passing G1-G6 gates. Non-disruptive — adds checks, doesn't
change existing gates.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Add checks. Don't restructure.

## Read first

1. `docs/TerminalAnalysis.md` §Compilation Status — "Why This Wasn't
   Flagged Earlier" (3 blind spots).
2. `DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java`
   — how G1-G6 work. Find where `buildings` list is loaded and how
   `GATE_SCOPE` controls which buildings are tested.
3. `scripts/run_RosettaStones.sh` — line 146-148: `prepare_compile_db()`
   returns 1 (skip) when BOM.db is missing. Find how to make this FAIL
   loudly instead.
4. `docs/LAST_MILE_PROBLEM.md` — check current gap register format.

## Task 1: G0-COMPILED gate

Add a new gate G0 in RosettaStoneGateTest that checks whether the output
DB was actually compiled (has c_order rows), not just extracted:

```java
/**
 * G0-COMPILED: Output DB must contain at least 1 c_order row.
 * Prevents extraction-only outputs from silently passing G1-G6.
 * @Traces TerminalAnalysis.md §Compilation Status — blind spot #2
 */
@TestFactory
@Order(0)
@DisplayName("G0-COMPILED")
Collection<DynamicTest> g0_compiled() {
    // For each building in GATE_SCOPE:
    //   SELECT COUNT(*) FROM c_order → must be > 0
    //   If 0: FAIL with "output is extraction-only, not compiled"
}
```

**Important constraints:**
- G0 must NOT break existing passing buildings (SH, FK, DM all have c_order > 0)
- G0 MUST fail for TE (c_order = 0) — this is the point
- Use `assumeTrue(GATE_SCOPE.contains(tag))` same as other gates
- If a building is NOT in GATE_SCOPE, it's skipped (not failed)
- G4-TAMPER (source scan) has no DB dependency — it must still work standalone

### No skip list — G0 must FAIL for TE

Do NOT create a G0_SKIP set. The whole point of G0 is to make TE FAIL
visibly. A skip is still silent — it's just a polite version of what's
happening now. G0 must FAIL for TE with a clear message:
`"CO_TE: output is extraction-only, not compiled (c_order = 0)"`.
When prompt 66 is done and TE compiles, the FAIL goes away naturally.

## Task 2: Rosetta script fail-loud

In `scripts/run_RosettaStones.sh`, change the BOM.db missing case from
silent skip to loud warning:

```bash
# Current (silent):
if [ ! -f "$bom_db" ]; then
    echo "  [WARN] ${bom_db} not found..."
    return 1
fi

# New (loud):
if [ ! -f "$bom_db" ] || [ ! -s "$bom_db" ]; then
    echo "  [FAIL] ${bom_db} not found or empty — IFCtoBOM pipeline failed for ${prefix}"
    echo "         Check logs/pipeline_*${prefix}* for QA report"
    verdict "BOM_${prefix}" "FAIL" "BOM.db not found or empty"
    return 1
fi
```

Also add a check after `compile_building()` to verify c_order is populated:

```bash
# After compile_building, verify output has c_order
local order_count=$(sqlite3 "${OUTPUT_BASE}.db" "SELECT COUNT(*) FROM c_order" 2>/dev/null || echo "0")
if [ "$order_count" -eq 0 ]; then
    echo "  [WARN] ${PREFIX}: output.db has 0 c_order rows — compilation may not have run"
fi
```

## Task 3: Update LAST_MILE_PROBLEM.md

Add a new gap entry for the IFCtoBOM-to-compilation blind spot:

```
### R25 — IFCtoBOM QA Failure Not Propagated to Gate Results

**Status:** OPEN (found S99)
**Root cause:** IFCtoBOM pipeline ABORTs to log file. Rosetta script
treats missing BOM.db as "skip" not "fail." G1-G6 gates don't check
whether output was compiled or extracted.
**Fix:** G0-COMPILED gate (checks c_order > 0) + script fail-loud.
**Buildings affected:** TE (CO_TE). Extraction passes, BOM compilation blocked.
```

## What NOT to do

- Do NOT modify G1-G6 gates — they are correct for what they test
- Do NOT change IFCtoBOM QA thresholds
- Do NOT remove TE from GATE_SCOPE — it should be tested, with G0 FAILING visibly
- Do NOT change the compilation pipeline
- Do NOT break SH/FK/DX/DM (they must all still pass including G0)

## Verify

1. `mvn compile -q` — PASS
2. `mvn test -pl DAGCompiler -Dtest=RosettaStoneGateTest -Dpipeline.tests.skip=false`
   — SH/FK/DM pass G0-G6. TE: G0 FAIL (extraction-only, c_order = 0).
3. `./scripts/run_RosettaStones.sh classify_sh.yaml` — SH 7/7 PASS (no regression)
4. `./scripts/run_RosettaStones.sh classify_te.yaml` — loud FAIL for BOM.db missing
5. **Sacred file seal:** After changes to RosettaStoneGateTest.java and
   run_RosettaStones.sh, run `scripts/verify_test_seal.sh` and update the
   seal hash in `docs/TestArchitecture.md`.

## When Done

Prepend `# DONE` + commit hash to this file's first line.
