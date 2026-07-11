<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# WITNESS SAMPLECASTLE MEP STALE — fix a data-vs-witness mismatch, not a code bug (2026-07-11, FABLE-assigned)

```
# ⚠ DO NOT REMOVE
SCOPE: bim-ootb repo. Small, well-scoped fix — one witness file's assertion is stale relative to
current shipped data. PUSH PAUSE IN EFFECT (bim-compiler CLAUDE.md §⏸) — commit locally, verify on
localhost, do NOT push, do NOT open a PR, until told otherwise. Read the log after every run.
```

## What's already known (checked by Sonnet, don't re-derive)
`modeller/tests/witness_modeller_disc_walk.js`'s B5/B6 checks assert that **SampleCastle exposes a
`[data-disc="MEP"]` node** in the Outliner (`await page.waitForSelector('#bo-tree [data-disc="MEP"]',
{ timeout: 30000 })`) and that clicking it produces an honest refusal. This currently FAILS — confirmed
via `sqlite3 modeller/SampleCastle_ARC.db "SELECT discipline, count(*) FROM elements_meta GROUP BY
discipline"` → **`ARC|3342` only, zero MEP rows**. SampleCastle's shipped resident DB is ARC-only today
(traces to the `feat/embed-8-arc-buildings` work — check that branch/its merge commits for the actual
rationale before assuming it's a bug; it may be a deliberate current product scope, not accidental data
loss). The witness was written when SampleCastle still shipped MEP rows and is now testing a fact that
isn't true anymore — **confirmed NOT caused by any in-flight branch** (reproduces identically on
pristine `origin/main`, no branches merged).

B6's current failure mode is also worth fixing regardless of the above: it's an **uncaught promise
rejection that crashes the whole witness process** (not a clean assertion failure) when B5 finds no MEP
node — `witness_modeller_disc_walk.js` line ~87-91 already has partial guarding for this (a comment says
"Harness robustness: if B5 found no MEP node this click throws — catch it so B6-B8 still tally instead
of crashing the suite") but it's evidently not fully catching every path, since Sonnet's fresh pristine-
main run still crashed with an uncaught `TimeoutError` from `page.click`, not a caught+tallied B6 fail.

## Task
1. **Determine the right building.** Query every shipped resident DB
   (`modeller/*_extracted.db`/`*_ARC.db`/`Terminal_meta.db`) for `SELECT discipline, count(*) FROM
   elements_meta GROUP BY discipline` — find one that genuinely has both real MEP-family rows (any of
   PLB/ELEC/ACMV/FP/MEP) AND is a building where RouteWalker/DiscWalker is EXPECTED to honestly refuse
   (no measured rule coverage) — i.e. preserve the ORIGINAL intent of B5/B6 (prove an honest-refusal
   path exists and is reachable), just retarget it to a building that still has the data to reach it.
   If NO shipped building fits that combination anymore (data landscape may have moved further than
   this), that's a real, reportable finding — say so plainly, don't force a fit.
2. **Fix the witness**, not the product data — this is a test-oracle drift, matching the exact pattern
   `fix/terminal-oracle-source` (PR #725, already merged) fixed for a different witness. Retarget B5/B6
   to the correct building/discipline pairing found in step 1, OR if genuinely no fit exists, convert
   B5/B6 to an explicit, logged SKIP (never silently pass, never leave it red forever) with a comment
   citing this file and the exact `SELECT discipline, count(*)...` evidence for why.
3. **Also fix the crash-on-missing-node path** (B6's uncaught rejection) regardless of step 1/2's
   outcome — this is a harness robustness gap independent of which building ends up targeted. Match the
   existing try/catch pattern already partially in place; make it actually catch every path that can
   throw here.
4. Re-run the full witness fresh (`NODE_PATH=~/bim-ootb/tests/node_modules node
   modeller/tests/witness_modeller_disc_walk.js`), confirm ALL checks (not just B5/B6) pass or are
   explicitly, honestly accounted for — paste the real log output in your findings, not a summary.
5. **Do not touch** `SampleCastle_ARC.db` or any other shipped DB — this is a witness-side fix only,
   per the "confirm rationale before assuming bug" note above. If step 1 concludes the ARC-only data
   state itself is wrong (unintentional data loss, not a deliberate scope decision), STOP and report
   that finding instead of restoring data yourself — that's a bigger call than this task's scope.

## DONE WHEN
`witness_modeller_disc_walk.js` runs clean (green or an explicit, logged, justified skip — never a
silent pass or an unexplained red) on pristine current `origin/main`, the crash-on-missing-node path is
fixed, findings appended to this file (fresh dated section) citing the real log output and which
building ended up targeted (or why none fit). Committed locally only, per push pause.
