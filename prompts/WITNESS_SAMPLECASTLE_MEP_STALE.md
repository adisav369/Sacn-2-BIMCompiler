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

## FINDINGS — 2026-07-11 (Fable execution session) — DONE, committed locally per push pause

**Branch/commit:** bim-ootb `fix/samplecastle-mep-stale` @ `5ac38bf` (worktree `/tmp/wt-samplecastle-mep-stale`,
fresh off `origin/main` @ `a4c61eb`). NOT pushed, NO PR — PUSH PAUSE honoured. File changed:
`modeller/tests/witness_modeller_disc_walk.js` only (+19/−5). No shipped DB touched.

### Step 1 — building determination (full DB sweep, real query output)
`sqlite3 <db> "SELECT discipline, count(*) FROM elements_meta GROUP BY discipline"` across every
`modeller/*.db` resident/extracted DB:

```
Clinic_ARC.db            ARC|1984  MEP|99  PLB|3  STR|534
Duplex_ARC.db            ARC|199   STR|19
Duplex_extracted.db      ARC|253
Garage_ARC.db            ARC|515   STR|756
HHS_ARC.db               ARC|1110  STR|1450
Hospital_ARC.db          ARC|7170  STR|7471
Ifc4_Revit_extracted.db  ARC|1983
SampleCastle_ARC.db      ARC|3342
SampleCastle_ARC_extracted.db ARC|3342
SampleCastle_extracted.db     ARC|3342
SampleHouse_ARC.db       ARC|40    STR|20
SampleHouse_extracted.db ARC|39
Terminal_ARC.db          ARC|35552
Terminal_meta.db         ACMV|1570 ARC|35552 ELEC|833 FP|989 MEP|277 PLB|8175 STR|1032
```

**Winner: Clinic.** Terminal_meta.db has the richest MEP family but is NOT what the Open panel loads —
the `RESIDENTS` registry (`modeller/str_walker_outliner.js:38-47`) maps key `Terminal` → `Terminal_ARC.db`
(ARC-only). **Clinic_ARC.db is the only shipped resident whose actually-loaded DB has MEP-family rows**
(MEP|99, PLB|3). And the honest-refusal half holds: Clinic is column-framed → `_dwRules('Clinic')`
(`modeller.html:4524`) routes to `terminal_rules.db`, which has **zero generic-'MEP' rule rows** in every
walk table — `rule_placement` disciplines = `ACMV|4 ELEC|7 FP|11 PLB|5 STR|9 roof|1`, `rule_mesh_binding` =
`ACMV|3 ELEC|2 FP|2 PLB|2 STR|3 roof|1`, `rule_space_bom` = `FP, roof` only. So clicking the generic MEP
disc node on Clinic → DiscWalker REFUSE, exactly the B5/B6 original intent (node exists + honest refusal).

### SampleCastle ARC-only rationale — deliberate, NOT data loss
Commit `6068fab` ("feat(modeller): embed 8 ARC-only buildings + shared mesh.db resident registry",
`feat/embed-8-arc-buildings`, EMBED_8_ARC_BUILDINGS_MESH_DB.md) explicitly ships each resident as an
"ARC-only metadata DB ... paired with ONE shared mesh.db". Stronger still: `SampleCastle_extracted.db`
(the pre-embed extraction) is ALSO `ARC|3342` only — SampleCastle's source data never carried MEP rows.
No restoration needed, no bigger call to escalate.

### Steps 2+3 — witness fix
- B5/B6 retargeted `SampleCastle` → `Clinic`; header + block comments updated citing this file and the
  `ARC|3342`-only evidence (test-oracle drift, same pattern as `fix/terminal-oracle-source` PR #725).
- Crash path fixed: the old code `.catch(() => null)`-ed the B5 `waitForSelector` but then unconditionally
  `await page.click('#bo-tree [data-disc="MEP"]')` — with no node that click throws an uncaught 30s
  `TimeoutError` and kills the process before B6-B8 tally. (Note: the "Harness robustness" partial-guard
  comment quoted in the task brief does not exist in current `origin/main` — the click was fully unguarded
  there.) Now: click is **skipped** when B5 found no node (no second 30s burn) and **try/caught** when it
  did; either miss path pushes a `HARNESS ...` line into the tallied logs so B6 fails honestly.

### Step 4 — witness runs (real log output, node exit code captured directly)
Fixed witness on the retargeted branch — `NODE_PATH=~/bim-ootb/tests/node_modules node
modeller/tests/witness_modeller_disc_walk.js`, log `witness_disc_walk_final.log`:

```
NODE_EXIT=0
═══ W-UX-DISC — disc = walker (headless) ═══
  ✅ B1 discipline nodes are walker entry points (data-disc + ▶ glyph)  discNodes=4
  ✅ B2 SampleHouse exposes an STR disc node
  ✅ B3 click STR → §DISC-WALK STR surfaced (real walk, not faked)  §DISC-WALK merged into bomtree — one Disc tab (present disciplines drill in, absent ones walk)
  ✅ B4 STR Walker tab expanded on STR click
  ✅ B5 Clinic exposes an MEP disc node
  ✅ B6 click MEP → honest §DISC-WALK MEP refusal (no measured rule)  §DISC-WALK MEP REFUSE no measured rule for MEP (honest, 0 fabricated)
  ✅ B7 NO fabricated run — GEOM_SWEEP count unchanged  before=0 after=0
  ✅ B8 no script LOAD_FAIL / pageerror
W-UX-DISC: 8 PASS / 0 FAIL
```

Crash-guard proven independently (temp copy re-pointed at SampleCastle, i.e. the exact no-MEP condition
that used to crash the suite) — clean tally, no uncaught rejection:

```
  ❌ B5 Clinic exposes an MEP disc node
  ❌ B6 click MEP → honest §DISC-WALK MEP refusal (no measured rule)
  ✅ B7 NO fabricated run — GEOM_SWEEP count unchanged  before=0 after=0
  ✅ B8 no script LOAD_FAIL / pageerror
--- DISC-WALK logs ---
   HARNESS no [data-disc="MEP"] node — click skipped, B6 will tally as FAIL
W-UX-DISC: 6 PASS / 2 FAIL
```

### DONE-WHEN check
Witness green (8/8, exit 0) with a real building targeted (Clinic) — no SKIP needed, a genuine fit
existed. Crash-on-missing-node fixed and separately probed. Committed locally only:
bim-ootb `5ac38bf` on `fix/samplecastle-mep-stale`. Push/PR deferred until PUSH PAUSE lifts.
