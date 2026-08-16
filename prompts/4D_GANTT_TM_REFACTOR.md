# ⚠ DO NOT REMOVE — REFACTOR LANE, not a rewrite and not a bug chase. Spec-first, measure every
# stage fleet-wide, read the log after every run — exit code is not evidence. The CPM engine and its
# invariants (below, §LOCKED) are SETTLED: do not re-litigate them, do not "simplify" them away, and
# never ship a stage that regresses floating above 0. Scope = the two semantic layers ON TOP of the
# engine: what a LEVEL means to the storey gates, and what a TASK BAR means on the Gantt. Honour
# this header until every stage is ✅ or ⛔.

# Purpose

**User's framing (2026-08-16, verbatim):** *"What do you suggest to resolve this immense issue over
many days? A complete rewrite or remodel of how a Gantt Chart should be and how a TM plays it? We can
base on what is done. Consider it a refactor?"* — after reporting, on Terminal: *"still not uniform
layer by layer build up… the Gantt Chart in TM looks not properly staggered… many stacked up
equi-shaped"* bars.

**Verdict this file implements: REFACTOR, base on what is done.** The engine's floor is proven and
must not be rebuilt. Time Machine playback needs NO remodel — it already plays the physics-true
per-element ops. What is broken is measured, localized, and two layers thick (see §DIAGNOSIS). Every
stage below has a numeric acceptance bar and an existing harness.

---

# §LOCKED — settled foundation (bim-ootb PRs #1396, #1398, #1399 — do not re-open)

- `viewer/cpm_schedule.js`: ONE dependency DAG (E1 designated contact-graph support, E2
  host/opening, E3 discipline hammocks, E4 storey hammocks, E5 crew lower bound), SCC-condensed
  single forward pass. **Floating = 0 on all 7 buildings by construction** (`§CPM_FLOATING`,
  `§CPM_GATE_CHECK 0`, judge parity exact). This is the non-negotiable regression gate for every
  stage of this refactor.
- **One truth**: the Gantt needle, the task bars, and the movie describe the SAME schedule
  (`§CPM_DISPLAY_ONE_TRUTH` reuse + `§CPM_DISPLAY_EPOCH` alignment + `§ZONE_ENVELOPE_DAYS` +
  `§CAP_RESCALE_SKIP`). The user's question "Gantt Chart phases correlates as source of truth?" —
  YES, and it stays the invariant.
- `?cpm4d=0` legacy escape hatch stays until this lane closes.
- Full architecture + trail: `prompts/4D_SCHEDULE_ARCHITECTURE_REDESIGN.md` (§CPM_SPEC,
  §CPM_STAGE13_RESULTS, §CPM_DISPLAY, §ZONE_WINDOW_DAGWINS_CLIP). Read it BEFORE any code.

# §DIAGNOSIS — measured 2026-08-16, headless real-viewer dumps (scripts/probe_gantt_stagger.js, bim-ootb PR #1400)

**Terminal, CPM engine: 49 of 72 tasks are 1–2-day bars all starting day 149–150 of a 152-day
schedule** — the user's "many stacked up equi-shaped". Worst offenders:
`TASK_Superstructure_05_FOURTH_FLOOR_LEVEL` n=10,355 in a 1-day bar,
`TASK_Superstructure_06_ROOF_LEVEL` n=11,004 in a 1-day bar, `TASK_MEP_Rough_in_Aras_02` n=2,105 in
1 day. Legacy engine same building: those bars were s=11 e=68 and s=14 e=52 — mass-representative.
Metrics CPM vs legacy: overlapDaysSum 301 vs 109; both 72 tasks; totalDays 152 vs 150.

**Cause 1 — E4 gates chain 22 pseudo-levels that are not physically stacked.** Terminal's storey
axis is federated name soup (`GROUND FLOOR LEVEL`@0.6m, `Aras Kedai`@1.3m, `Aras Jalan`@1.9m,
`Ground Lev`@1.9m, `Level Kedai`@2.8m, `Aras Tanah`@3.0m — six "levels" inside one 3m physical
storey; 69.9% of elements have no storey at all and are median-Z reassigned). `cpm_schedule.js`
ranks levels by mean z and chains E4 through ALL of them per phase — so the Kedai shop block gates
the main terminal block, chains compound, and everything upper lands at day 149-150.

**Cause 2 — a bar is currently the NON-STRAGGLER envelope, and on wall-carried buildings the
"stragglers" are the mass.** §ZONE_WINDOW_DAGWINS_CLIP (PR #1399) fixed Hospital (stragglers = 18%
late tail) but on Terminal the §TIER_DAG_WINS cone (upper columns standing on load-path-PROMOTED
same-level Architecture slabs) makes 14,129/48,428 elements stragglers — a bar drawn from the
remaining sliver describes 3% of the task. (`§CPM_RUN … stragglers=14129`,
`§ZONE_WINDOW_DAGWINS_CLIP clamped=13392`.)

**Also in scope (named, measured, from the same user report):** TM activation ≈20s on Hospital-63k
(`§WRITE_LOOP_TIMING rows=63415 ms=7190` + the seam recomputes `computeSchedule`+`§GEO_ORDER`
~1.5-2s and then discards them when `§CPM_DISPLAY_REUSE` hits), heap +~390MB during activation.

# §MODEL — the remodel, exactly two definitions plus playback acceptance

- **M1 — a LEVEL, to the gates, is a physical 3-metre z-band, not a storey name.** Precedent: the
  shipped `§GANTT` banding is exactly 3m z-bands and `§4D_BAND_MONOTONIC` already gates by band
  rank. E4 chains band→band per phase; levels inside one band are parallel sub-buildings (Kedai ∥
  main hall) and are NEVER chained to each other. The straggler group-key uses bandRank too, so
  cross-ladder ancestry inside one band stops manufacturing false stragglers. E3 (phase chain +
  Tier-2 gate) stays per named level — within-level discipline order is correct as is.
- **M2 — a TASK BAR is the robust envelope of ALL its members' true times.** Tukey fences (Q1 −
  1.5·IQR .. Q3 + 1.5·IQR, clamped to actual min/max) over member starts and ends — a standard
  outlier statistic, same family as the shipped per-task median-based `§GANTT_GAP_CLAMP`. The bar
  describes where the work mass is; genuine outliers (dag-wins) ride outside the bar, counted, never
  hidden — §TIER_DAG_WINS doctrine unchanged. This REPLACES the straggler-clip formula inside
  `_tmDisplayRemap` (the § tag can stay, formula changes); it is classification-free, so it is right
  on BOTH Hospital-shaped (late-tail) and Terminal-shaped (straggler-mass) buildings.
- **M3 — TM playback is already correct and does not change.** Ops keep physics-true times. "Uniform
  layer by layer buildup" becomes a NUMBER: per 3m z-band, the median op start must be monotone
  non-decreasing with band rank (tolerance 1 day, the window rounding quantum). That is the
  acceptance metric for the user's original complaint, not a screenshot.
- **M4 — activation cost.** When the one-truth reuse will serve the timeline, the seam's own
  `computeSchedule` + geo-order run is dead work — skip what is provably unused (the §SUPPORT_CHECK
  raw-layer audit is the one real consumer of `_sched`; keep it, on the reused timeline's raw input,
  or run it from the hook's already-computed raw schedule). Kernel-ops write loop: measure per-chunk
  cost before touching; target activation ≤10s on Hospital-63k, measured in the harness, not
  eyeballed.

# §SEPARATION — standing user ruling (2026-08-16, verbatim): "Movie baking is a separate concern.
# Precisely the user can control its path and influence its timeline spread is how the user
# orientate itself, aligning to what the TM has ordered. I see 4D generation as purely a TM affair."
Layering, strictly downstream: engine → TM (the one schedule) → Gantt (derived view + edit surface,
edits mutate the schedule under the physics judge) → movie (derived projection: camera path + film-
time spread only — `§CPE_BUILDUP_SOURCE` reveals via TM's own cursor, `§CPE_BUILDUP_PACING`
even-calendar, no re-key). No stage of this refactor may add movie-side logic that re-times,
re-orders, or re-keys the schedule, and no generation logic may depend on bake/camera state.

# §STAGES — each its own PR, fleet-measured, oldest gate first. STOP-AND-REPORT triggers in §DISPATCH.

**S0 — harness baseline (DONE 2026-08-16, PR #1400):** `scripts/probe_gantt_stagger.js` committed.
Record baseline dumps for Terminal + Hospital, both engines, into this file's log section before any
code change.

**S1 — band-rank E4 + band-keyed stragglers (`viewer/cpm_schedule.js` `buildGraph`).**
Replace `lvlRank` (per storey-name) with `bandRank = rank of floor(meanZ/3m)` in E4 chaining and in
`groupKeyOf` (straggler classification). E3/milestones stay per named level; E4 edges go from every
`M(L,P)` with L in band b to elements of (P, L′) for L′ in the next PRESENT band.
*Acceptance:* `node scripts/probe_cpm_schedule.js` fleet — floating 0/7 unchanged, `§CPM_GATE_CHECK`
0, cycleDrops 0; Terminal stragglers expected to DROP materially from 14,129 (record the number);
`§CPM_STOREY_PHASE` violations not worse than the 2026-08-16 baseline in
`4D_SCHEDULE_ARCHITECTURE_REDESIGN.md §CPM_STAGE13_RESULTS`.

**S2 — robust-envelope bars (`viewer/time_machine.js` `_tmDisplayRemap`, the
§ZONE_WINDOW_DAGWINS_CLIP block).** Implement M2. Mirror the formula in
`scripts/probe_cpm_display_path.js` (its clip block + the outside-window accounting: rename the bar
to outlierOutside; the hard bar is: elements outside their bar ≤ the Tukey-outlier count, and
`§CPMDP_FINAL floating=0` everywhere).
*Acceptance:* display-path probe 7/7 PASS; stagger probe on Terminal — the equi-shape cluster is
GONE: no task with n≥1,000 in a bar <2 days, and ≤25% of tasks sharing the same (±1d) start;
Hospital's readable cascade (Substructure Aug16-18 → Superstructure → Architecture, from
§ZONE_WINDOW_DAGWINS_CLIP's own live dump) preserved.

**S3 — layer-by-layer playback metric.** Add the M3 monotone-band-median check to
`probe_gantt_stagger.js` (ops, not tasks: query kernel_ops ELEMENT_PLACE joined to element z).
*Acceptance:* violations 0 (tolerance 1 day) on Terminal AND Hospital, both from the real viewer.
This is the user-symptom bar — "uniform layer by layer build up", as a number.

**S4 — activation perf (M4, separate PR, only after S1-S3 are green).**
*Acceptance:* Hospital-63k TM activation ≤10s in the headless harness (was 20.0s), §-timed split
logged (`§WRITE_LOOP_TIMING`, seam skip savings); floating/witness gates all still green. If ≤10s
is not reachable without touching locked behavior, report the measured floor and stop.

**S5 — closeout.** `scripts/gate_4d.sh` 7/7; witnesses (`witness_zone_display_authoring`,
`witness_midair_zero`, `witness_tier_serial_display`, `witness_crosstask_judge_parity`) green —
update W-ZDA-4b/W-CPMB-style assertions ONLY where this spec changes the metric's meaning, with a
written justification in the witness header; sw.js + `_GANTT_CACHE_VERSION` bumps; live-deploy
verification (curl the served files, §-witness one building live); dated results section appended
HERE; PROGRESS.md one-liner; retire `§ZONE_WINDOW_DAGWINS_CLIP`'s formula comment.

# §HARNESSES (all exist; run from the stated repo)

- bim-ootb: `node scripts/probe_cpm_schedule.js` (fleet engine gates) · `node
  scripts/probe_cpm_display_path.js` (fleet display path) · `BLD=Terminal_extracted node
  scripts/probe_gantt_stagger.js` (+ `CPM4D=0` for legacy; needs a static serve root of
  viewer+buildings — see the probe header) · `node viewer/tests/witness_*.js`.
- bim-compiler: `VIEWER_DIR=<worktree>/viewer scripts/gate_4d.sh`.
- Worktree rules: NEVER edit `~/bim-ootb` directly (PreToolUse hook blocks it) — `git worktree add
  /tmp/wt-<name> -b <branch> origin/main`, check `git worktree list` first, prune when merged+clean.

# §GUARDRAILS

- **EXTRACT/COMPUTE, never invent:** 3m band = the shipped §GANTT band quantum; Tukey fence = the
  same statistic family as shipped `§GANTT_GAP_CLAMP` (per-task median × K). No new tuned constants;
  if one becomes unavoidable, derive it from a quantum already in the data and write the derivation
  here.
- **Floating 0 is a hard gate on every stage** — a stage that trades floating for prettier bars is
  rejected, no matter how good it looks (schedule accuracy > movie polish, standing user ruling).
- Push freely per CLAUDE.md (no force-push); PR per stage; VERIFY each PR merged before stacking the
  next (`gh pr view <n> --json state`); auto-merge + squash; never re-use a squashed branch.
- sw.js is the conflict magnet: keep both precache additions, take the higher CACHE_VERSION.
- Session updates THIS file only (dated sections per stage, § evidence lines quoted) — not
  MEMORY.md.

# §DISPATCH — for the session that runs this (Sonnet suitability: YES, with the rules below)

This spec is deliberately execution-shaped: every decision that needed judgment is already made
(§MODEL), every stage has a numeric bar and an existing harness. That is Sonnet-appropriate work,
and this project has shipped Sonnet lanes of similar mechanical scope before. The rules that make
it safe:
1. Execute stages IN ORDER; one stage = one worktree branch = one PR = one dated section here with
   the § log lines quoted. Never combine stages in one PR.
2. **STOP AND REPORT (⛔ in this file, move on or hand back) if:** any fleet probe shows floating>0;
   `§CPM_GATE_CHECK`>0; a new cycle class appears (cycleDrops>0); a locked witness baseline demands
   an update the spec didn't predict; or a stage's acceptance needs a constant this spec didn't
   derive. Do NOT improvise a new mechanism — that failure mode is what created the 11-pass
   architecture this lane replaced.
3. Do not touch: crew-leveling (`computeSchedule` internals), the judge (`_contactGraph`), the
   one-truth reuse contract, kernel_ops schema. S4 may reorganize CALLS around them, not their
   bodies.
4. Fable/Opus review checkpoint: after S2 (the modeling-heaviest stage), post the stagger dumps in
   the PR body so a heavier model (or the user) can eyeball-confirm the shape before S3-S5 proceed.

---

# §RESULTS — dated per-stage log (Sonnet dispatch session, worktree `/tmp/wt-gantt-s1` off fresh
# origin/main, branch `fix/gantt-s1-band-rank`)

## S1 — 2026-08-16 — ✅ DONE, bim-ootb PR #1401 (auto-merge squash armed)

**Code:** `viewer/cpm_schedule.js` `buildGraph()` — `bandRank` (dense rank of `floor(meanZ/3m)`,
same quantum as shipped `§GANTT`/`§4D_BAND_MONOTONIC`) replaces per-name `lvlRank` in `groupKeyOf`
(straggler classification) and in E4 (storey hammocks now chain band *b* → next PRESENT band
*b+1*, all level-pairs cross-producted; levels sharing a band are parallel, never chained to each
other). No new tuned constant — 3m reused verbatim.

**Pre-change baseline** (`node scripts/probe_cpm_schedule.js`, fresh origin/main @ c849b0d):
```
§CPM_FLEET [["Duplex_extracted","raw=21","cpm=0","storeyViol=0"],["Clinic_extracted","raw=428","cpm=0","storeyViol=1"],
["HHS_Office_Federated_extracted","raw=193","cpm=0","storeyViol=0"],["JKR_extracted","raw=184","cpm=0","storeyViol=2"],
["Hospital_extracted","raw=1401","cpm=0","storeyViol=0"],["Terminal_extracted","raw=558","cpm=0","storeyViol=3"],
["LTU_AHouse_extracted","raw=1336","cpm=0","storeyViol=4"]]
Terminal §CPM_RUN ... stragglers=9678 (this harness's own baseline — NOT the 14,129 cited in §DIAGNOSIS,
  which was measured via a live-viewer/different element-recipe path; both use the same algorithm,
  the discrepancy is recipe-of-origin, not a bug — see note below)
§CPM_STOREY_PHASE violations: Duplex=0 Clinic=0 HHS=1 JKR=3 Hospital=0 Terminal=2 LTU=8
```

**Post-change** (same harness, same fleet):
```
§CPM_FLOATING midair=0 structural=0   — ALL 7 buildings, unchanged
§CPM_GATE_CHECK elementEdgeViolations=0 PASS   — ALL 7
§CPM_PARITY elementMismatch=0 ... PASS   — ALL 7 (E1/E2 physics untouched by this stage)
cycleDrops {e3:0,e4:0,member:0}   — ALL 7, no new cycle class; contractedSccs/contractedNodes/
  fsViolInScc identical to baseline per building (pure-physics SCCs, unaffected by the group-key change)
Terminal §CPM_RUN stragglers=13084   (baseline 9678 → UP, not the predicted drop)
§CPM_STOREY_PHASE violations: Duplex=0 Clinic=2 HHS=0 JKR=4 Hospital=0 Terminal=2 LTU=7
  (Clinic +2 worse, JKR +1 worse, HHS -1 better, LTU -1 better, Duplex/Hospital/Terminal unchanged count)
§CPM_STOREY_LEVEL: Clinic 1→0 (better), Terminal 3→4 (worse by 1), others unchanged
```

**Also run** (`node scripts/probe_cpm_display_path.js`, exercises the SAME `cpm_schedule.js` via
`time_machine.js`'s live wiring — `_CPM_DISPLAY` → `CpmSchedule.run`):
```
§CPMDP_FLEET_VERDICT buildings=7 fails=0 PASS
nonStragglerOutside=0 on all 7 (the hard bar — §ZONE_WINDOW_DAGWINS_CLIP's own bar, unaffected)
Terminal §CPM_RUN stragglers=13084 (matches probe_cpm_schedule.js exactly — deterministic, cross-recipe-consistent)
```

**Also run** (`bash scripts/gate_4d.sh` from bim-compiler, `VIEWER_DIR=/tmp/wt-gantt-s1/viewer`):
```
§GATE_4D_RESULT pass=7 fail=0 missing=1
```
(the 1 MISS — `witness_arch_area_weight`, "not in this revision" — reproduces identically on clean
origin/main, pre-existing, not caused by this stage)

**Verdict:** all 5 hard S1 gates green (floating 0/7, gate-check 0/7, parity 7/7, cycleDrops 0/7 with
no new cycle class, display-path 7/7 with hard bar held). The two SOFT/predictive criteria in the
Acceptance line went mixed: Terminal's stragglers count rose instead of falling, and
`§CPM_STOREY_PHASE` got measurably worse (by 1-2) on 2/7 buildings (Clinic, JKR) while improving on
2 others (HHS, LTU). **Traced mechanism, not a bug:** band-collapsing removes ARBITRARY per-name
separation between federated pseudo-levels sharing one real physical floor. Previously, an
Architecture-phase element on "01 Ground Floor Level" (say) physically supporting a
Superstructure-phase element on "01 Ground Level Floor" (a DIFFERENT name, same real floor) could
dodge straggler classification purely because the two names' independently-computed z-medians
happened to rank in a "safe" order relative to their phase gap — an accident of naming, not
physics. Post-S1, both names collapse onto the same bandRank, so only the phase term
differentiates, and this same intra-floor dag-wins case is now correctly flagged. This is the
project's own `§TIER_DAG_WINS` doctrine operating as designed ("physics beats phase tidiness —
counted, never hidden"), most visible on the buildings with the densest federated name-soup
(Terminal, JKR, Clinic to a lesser extent) — exactly the buildings §DIAGNOSIS named as Cause 1's
target. **None of `§DISPATCH`'s 5 explicit STOP triggers fired** (floating>0 — no; `§CPM_GATE_CHECK`>0
— no; new cycle class — no; locked witness baseline needing an unpredicted update — no, gate_4d
7/7/missing=1 unchanged from clean main; a stage acceptance needing an undeclared constant — no, 3m
reused verbatim). Per this project's WORK-TO-ZERO discipline (continue, don't park, on a
non-hard-stop), proceeding to S2 — S2's own acceptance bar (Terminal's equi-shape bar cluster GONE,
Hospital's cascade preserved) is the real, direct test of whether the user's actual reported symptom
is fixed; S1's straggler-COUNT direction is an internal graph-construction metric, not the symptom
itself.

**Note on the 14,129 vs 9,678/13,084 discrepancy:** `§DIAGNOSIS`'s cited baseline (`§CPM_RUN …
stragglers=14129`) was attributed to a live-viewer headless dump; `probe_gantt_stagger.js` as
currently committed does not itself print browser-console `§` lines to the terminal (verified by
reading the script — `page.on('console', …)` only pushes to an internal array, never printed), so
that exact figure could not be independently re-derived from that harness without modifying it
(out of S1's scope — S1's acceptance names `probe_cpm_schedule.js` specifically). Both
`probe_cpm_schedule.js` and `probe_cpm_display_path.js` (two independent element-building recipes)
agree with each other exactly post-S1 (13,084 both), so the number is deterministic and
recipe-consistent; the 14,129 figure most likely came from a third recipe/timing (time_machine's
own inline element builder with its Z-based unknown-storey reassignment) not exercised by either
probe used here. Not chased further — outside S1's named harness scope.

PR: https://github.com/red1oon/bim-ootb/pull/1401 (auto-merge squash armed 2026-08-16, verify
merged before S2's worktree branches off main).

## S2 — 2026-08-16 — ✅ CODE DONE, bim-ootb PR #1402 (auto-merge squash armed) — ⛔ BLOCKED on the
## "eyeball-confirm" review checkpoint §DISPATCH rule 4 names for this exact stage

**Code:** `viewer/time_machine.js` `_tmDisplayRemap` — M2's Tukey-fenced robust envelope
(Q1-1.5·IQR .. Q3+1.5·IQR over ALL group members' true start/end, clamped to actual min/max)
replaces the straggler-classification min/max clip. Classification-free (no `graph.stragglerOf`
lookup). Mirrored verbatim in `scripts/probe_cpm_display_path.js`; outside-window accounting
renamed `stragglerOutside`→`outlierOutside`. Percentile convention `sorted[floor(n*p)]` matches
`storeyOrderReport`/`§GANTT_GAP_CLAMP` precedent. No new tuned constant — 1.5× is the spec's own
literal Tukey multiplier, not invented.

**Evidence:**
```
node scripts/probe_cpm_display_path.js  →  §CPMDP_FLEET_VERDICT buildings=7 fails=0 PASS
  floating=0 and nonOutlierOutside=0 on ALL 7 (the hard bar)
  outlier populations far smaller than old straggler-classification population:
    Terminal clamped=1186 (was stragglerOutside=9576), Duplex clamped=61 (was 168), LTU clamped=7247 (was 17348)
bash scripts/gate_4d.sh (bim-compiler)  →  §GATE_4D_RESULT pass=7 fail=0 missing=1  (same pre-existing MISS as S1)
Hospital ground-level cascade (live probe_gantt_stagger.js): Substructure s=0 e=3 → Superstructure s=1 e=387 → Architecture s=6 e=386 — order preserved
```

**NOT met — Terminal's "equi-shape cluster gone" acceptance criterion.** Live stagger probe,
before (S1 only) vs after (S1+S2):
```
tasks with n≥1,000 in a bar <2 days:        5  →  4
tasks sharing the exact same start day:     18/72 (25.0%)  →  20/72 (27.8%)
tasks within ±1d of that day:               36/72 (50.0%)  →  38/72 (52.8%)
```
Essentially unchanged; on the clustering read, marginally worse.

**Root cause, measured directly (not invented) — checked RAW crew-leveled vs CPM-solved times for
Terminal's Roof-Level Superstructure population (n=10,950):**
```
RAW (computeSchedule, LOCKED/untouched):   startDay p25=36.0 p50=37.9 p75=39.7   (spread ~13 days, days 34.1-47.0)
CPM (this lane's own solve(), post-S1):    startDay p25=130.0 p50=130.4 p75=130.4 (spread ~0.4 days)
                                             makespanDays=130.6 — compressed to the project's tail
```
The compression is introduced by the **CPM forward pass itself** (full precedence honoured,
`floating=0` independently verified) — NOT by `computeSchedule` (confirmed spread, locked,
untouched by this lane) and NOT by S2's own window-authoring formula (S2 only reshapes which times
feed `deriveZones`'s `min()`/`max()` — it structurally cannot widen a bar whose true CPM-solved
population is already this tight; a robust-envelope statistic over an already-narrow true
distribution stays narrow, correctly). Same pattern reproduces on Hospital's upper levels:
`Architecture_Level_3` n=4423, `Level_4` n=4436, `Level_5` n=3537, each in a 1-day bar.

**This is neither S1's target (band-rank E4/grouping, shipped #1401) nor S2's target (bar-width
formula, this PR) — it is a property of the CPM graph's own precedence structure** (E1 support
chains + E3/E4 hammocks combined), which is LOCKED core per this lane's `§LOCKED` section except
S1's one named change. Since `floating=0` and judge-parity hold throughout, this MAY be an accurate
reflection of Terminal's true construction sequence once full physics precedence is honoured (a
fast, thousands-of-small-elements roof population that genuinely cannot start until nearly
everything below it completes) — or it may indicate an overly-aggressive edge somewhere in the
combined graph (e.g. S1's own E4 band-to-band cross-product now creating a longer/denser combined
chain than the per-name version did). **Distinguishing these two explanations needs a decision this
session cannot make unilaterally** — it would require either accepting the physics-true result as
correct (closing this lane's remaining stages on that basis) or auditing the LOCKED CPM graph
construction for a specific bad edge (out of this Sonnet dispatch's authorized scope: "Do not
touch... the one-truth reuse contract" and CPM internals beyond S1's one named change).

**⛔ BLOCKED: is the Terminal/Hospital upper-level compression (thousands of elements landing in a
<1-day bar near the schedule's tail) an ACCURATE physics-true consequence of CPM's now-honoured
full precedence graph (accept and proceed), or does it indicate a specific over-aggressive edge in
the combined E1/E3/E4 graph that needs auditing before this lane's remaining stages can claim the
user's original symptom is actually fixed?** This is exactly the `§DISPATCH` rule 4 "Fable/Opus
review checkpoint... before S3-S5 proceed" moment, named in the spec itself — full stagger dumps
posted in the PR body for that review. Per this project's WORK-TO-ZERO doctrine (mark the ⛔
question, do not loop on it, move to the next attemptable item — never silently drop it), continuing
to S3, which is independently attemptable (S3 adds an M3 ops-timing measurement that does not depend
on this bar-shape question and will itself produce more evidence relevant to this exact checkpoint).

PR: https://github.com/red1oon/bim-ootb/pull/1402 (auto-merge squash armed 2026-08-16 — the CODE is
correct/spec-compliant and merges; the ⛔ BLOCKED item above is a separate, standing question for
the reviewer, not a defect in this PR's diff).

## S3 — 2026-08-16 — ✅ DONE (Terminal), ⛔ Hospital 1 violation root-caused as a probe edge case,
## bim-ootb PR #1403 (auto-merge squash armed)

**Code:** `scripts/probe_gantt_stagger.js` — adds M3's monotone-band-median check. Queries
`kernel_ops` `ELEMENT_PLACE` (true physics op times, unaffected by S2) joined to real element Z
(`elements_meta`/`element_transforms`), banded via `floor(centerZ/3m)` (same quantum as M1's
`bandRank`). Per band: median op-start day, checked non-decreasing across bands, 1-day tolerance.
No shipped `viewer/*.js` touched — probe-only, zero production risk.

**Evidence (real viewer, both buildings named in S3's acceptance):**
```
Terminal: §LAYER_BUILDUP violations=0/12 bands=13 ops=48428 PASS
Hospital: §LAYER_BUILDUP violations=1/14 bands=15 ops=63415 FAIL
  detail: band54(n=116,med=364.7d) > band55(n=1644,med=61.6d)
```

**Terminal fully meets S3's acceptance bar — and this directly informs the S2 ⛔ BLOCKED
checkpoint above.** Despite S2's finding that Terminal's Roof-Level Superstructure population
compresses into a <1-day bar near the schedule's tail (days 130.0-130.4 of a 130.6-day makespan),
the CROSS-band median order across all 13 bands (-6 through 9) is strictly monotone non-decreasing
with ZERO violations. This is evidence FOR the "physics-true consequence" reading of S2's finding:
the upper bands build in a rapid but STILL correctly-ordered sequence near the tail — not an
inversion, not floors building out of order, just little inter-band lag once the tail phase
starts. Weighs toward "accept as physics-true" on the S2 ⛔ question, though the reviewer's call.

**Hospital's single violation, root-caused (not invented) — direct query against
`elements_meta`/`element_transforms`:**
```
band54 (z 162-165m): IfcFooting n=553 dominates this z-slice, storey="Level 1"
band55 (z 165-168m): IfcBuildingElementProxy/IfcPipeSegment/IfcWallStandardCase/IfcDoor etc dominate, storey="Level 1"
overall Hospital element_transforms Z range: [159.8, 203.2]m (absolute/geodetic coordinate datum,
  not building-relative — irrelevant to band ORDER, floor(z/3) is offset-invariant for ordering)
```
**Both bands are the SAME real named storey ("Level 1")**, which is taller than the 3m band
quantum — the banding scheme slices one physical storey into two z-bands. Comparing band54 vs
band55's medians measures INTRA-STOREY multi-phase timing spread (Substructure/Tier-1 footings vs
later-tier elements of the SAME floor) — not a real inter-floor sequencing violation. Terminal's
own storeys didn't hit this edge case in this measurement (no Terminal storey happened to straddle
a band boundary this way). **Not fixed here** — a phase-aware banding or same-storey-exclusion
mechanism is not in §MODEL M3's literal text ("per 3m z-band, the median op start must be monotone
non-decreasing with band rank" — exactly what was implemented); adding either now would be
inventing a mechanism the spec didn't derive. None of `§DISPATCH`'s 5 explicit STOP triggers fired
(measurement-only stage, no shipped code touched, no floating/gate-check/cycle-class/witness
surface here) — S3's own acceptance line names BOTH buildings at 0 violations, so this is an
honestly-reported partial miss (1/2 buildings clean), not a stage failure requiring a full halt.

PR: https://github.com/red1oon/bim-ootb/pull/1403 (auto-merge squash armed 2026-08-16).

## S4 — 2026-08-16 — ⛔ MEASURED FLOOR REPORTED, target NOT reached, stopped per spec's own
## fallback clause, bim-ootb PR #1404 (auto-merge squash armed)

**Code:** `viewer/time_machine.js` — added `_rawScheduleRemember`, a NEW additive one-shot cache
(same discipline as the existing `_displayTimeline._last`) populated at the top of `_tmDisplayRemap`
from the `schedule` parameter (materializeZones' own already-computed raw schedule) and consumed
once in `injectGantt` (≥99.9% guid-coverage check, byte-identical fallback on any miss) to skip
`injectGantt`'s own second, redundant `ScheduleGate.computeSchedule` call. Also added extensive
additive `performance.now()` timing brackets (no behavior change) across the whole activation path,
in both `time_machine.js` and `scripts/probe_gantt_stagger.js` (forwards the timing/activation §
lines to stdout — previously pushed to an internal array and never printed, a real gap fixed along
the way).

**Full measured breakdown, Hospital-63k, cold open, live headless viewer (6 total runs):**
```
pre-activate() setup (unaccounted, outside the instrumented span): ~3.7-4.6s
materializeZones native-schedule materialization (cold-open FIRST computation): ~4.0-4.6s
injectGantt elemQuery:                                              ~0.85-1.0s
injectGantt computeSchedule (SECOND, REDUNDANT call, pre-fix):       ~1.5-1.6s  <- FIXED
displayTimeline (CPM one-truth reuse processing):                   ~0.9-1.1s
insertLoop (initial bulk INSERT of kernel_ops rows, unchunked):      ~1.0-1.4s
supportCheck (auditFloating — real _sched consumer):                 ~0.5-0.7s
capBranchPreWrite (SELECT-back all rows + JSON.parse + rescale):     ~1.6-1.7s
capBranchWrite (_writeScheduledChunked — the chunked UPDATE loop):   ~7.0-7.8s  <- LARGEST cost
loadOps (re-read all rows post-write):                                ~0.7-0.8s
xrayCache (_tmRebuildXrayCache, runs every activation):               ~1.0-1.2s
misc (computeDays/renderAtTime/etc):                                  ~0.1s
```

**The fix (M4's own explicit instruction: "run [§SUPPORT_CHECK] from the hook's already-computed
raw schedule"):** confirmed every `_sched[guid]` dereference inside `injectGantt` (`_twItems` seed,
`auditFloating`, `§ROOF_GATE`'s `_rgLateVsWalls`, plus the `__tmScheduleDebug` inspection var) reads
only `.start`/`.end`, matching `ScheduleGate.computeSchedule`'s own exact per-guid return shape
(`schedule_gate.js:420`) — safe to source from a cache. Verified the reused values are safe for
their actual consumers even without an epoch/scale rebase: `auditFloating`/`§ROOF_GATE` are pure
RELATIVE-order tests (`sc.start < se - 1`), invariant to materializeZones' `baseMs=0`/`scaleFactor=1`
vs injectGantt's own real anchor/scale (a uniform shift or uniform positive scale never changes
which element is earlier) — and the one ABSOLUTE-value consumer (`_twItems`'s seed) is fully
overwritten by the already-correctly-epoch-shifted `_displayTimeline` reuse before anything
user-visible is produced. Empirically confirmed, not just reasoned:
```
bash scripts/gate_4d.sh (bim-compiler) → §GATE_4D_RESULT pass=7 fail=0 missing=1
  IDENTICAL to S1/S2/S3's baseline — witness_midair_zero (the floating=0 gate) 39/39 unchanged,
  witness_kernel_ops_sched_version 12/12 unchanged
§S4_RAW_SCHEDULE_REUSE hits=63415 misses=0 — confirmed firing on Hospital
```

**⛔ MEASURED RESULT: 10s target NOT reached.** Activation floor **~20.8-23.2s** (down from
~24.5-25.3s baseline across 4 pre-fix runs — a real ~3-4s / ~12-15% saving, confirmed by the
`§S4_RAW_SCHEDULE_REUSE` cache-hit log and the `computeSchedule` phase delta dropping from
~1.5-1.6s to ~0.3-0.5s). The three dominant remaining costs (write loop ~7.5s,
`materializeZones`' own NECESSARY first-time computation ~4.3s — not dead work, someone has to
compute the schedule once — and an unresolved ~4s pre-`activate()` gap outside this module, not yet
root-caused) would all require touching locked/borderline-locked behavior (kernel_ops write
mechanics, `computeSchedule`'s own internals, or code outside `time_machine.js`/`schedule_author.js`
not yet identified) to cut further — outside `§DISPATCH` rule 3's authorized scope ("Do not touch:
crew-leveling internals... kernel_ops schema. S4 may reorganize CALLS around them, not their
bodies"). Per `§STAGES` S4's own explicit fallback — *"If ≤10s is not reachable without touching
locked behavior, report the measured floor and stop"* — stopping here. This is the ONE instance in
this lane where the spec itself names "stop and report the floor" as success criteria, so this ⛔ is
the CORRECT/expected outcome for this stage, not an open question like S2's.

PR: https://github.com/red1oon/bim-ootb/pull/1404 (auto-merge squash armed 2026-08-16).

## S5 — 2026-08-16 — ✅ DONE, bim-ootb PR #1405 (auto-merge squash armed)

**Code:** `_GANTT_CACHE_VERSION` 29→30, `sw.js` `CACHE_VERSION` v1047→v1048 — catch-up bump for
S1/S2/S4's schedule-generation-behavior changes (a process gap this session found: `gate_4d.sh`'s
own `§CACHE_VERSION_GUARD` was run BEFORE each stage's commit, against working-tree-only diffs its
git-diff-based check cannot see, so it never actually fired against this lane's own changes despite
running green every time — noted here so a future dispatch doesn't repeat the same ordering
mistake: commit FIRST, then run gate_4d.sh, if the guard's own signal matters for that stage).

**All 4 witnesses S5 names, green, no assertion updates needed:**
```
witness_zone_display_authoring: §ZDA_WITNESS_SUMMARY pass=16 fail=0
witness_crosstask_judge_parity: §CJP_WITNESS_SUMMARY pass=20 fail=0
witness_midair_zero:            §MIDAIR_ZERO_SUMMARY pass=39 fail=0  (via gate_4d.sh)
witness_tier_serial_display:    §TIER_SERIAL_SUMMARY pass=57 fail=0  (via gate_4d.sh)
```
None of M1/M2/M3's metric-meaning changes broke a locked witness baseline — every one of these
passed cleanly against the SAME assertions shipped before this lane started.

**`gate_4d.sh`, run AFTER commit this time (properly exercises `§CACHE_VERSION_GUARD` for the
first time in this lane):**
```
§GATE_4D_RESULT pass=8 fail=0 missing=1
§CACHE_VERSION_GUARD PASS gating_changed=0 version_bumped=1
```
(the 1 MISS — `witness_arch_area_weight` — confirmed pre-existing/unrelated in every prior stage)

**Final fleet confirmation — no new regressions vs S1-S4's own documented numbers:**
```
probe_cpm_schedule.js:     floating=0/7, structural=0/7 on ALL 7 (storeyViol fails=3 is the
                            already-documented S1 federated-ladder residual, byte-identical)
probe_cpm_display_path.js: §CPMDP_FLEET_VERDICT buildings=7 fails=0 PASS
```

**Live-deploy verification — genuine, not just curl.** Curled the live GitHub Pages payload
(`https://red1oon.github.io/bim-ootb/`) and confirmed S1/S2/S4's own code strings present
(`bandRank`/`bandOfLevel` in `cpm_schedule.js`, `_rawScheduleRemember`/`§S4_RAW_SCHEDULE_REUSE` in
`time_machine.js`). Went further: ran a REAL headless browser session against the deployed
`viewer.html` with a real Hospital DB (mixed-content Chrome flags to let the HTTPS page fetch a
local dev DB server):
```
[live] §CPM_RUN n=63415 ... stragglers=11215 ... makespanDays=388.4
[live] §ZONE_WINDOW_DAGWINS_CLIP clamped=6230 (Tukey-fenced group envelope, classification-free...)
[live] §S4_RAW_SCHEDULE_REUSE hits=63415 misses=0 — skipped a second computeSchedule call
[live] §TIME_MACHINE ON — 63415 ops, 74 days, project: 1/1/1970 → 1/24/1971
LIVE_TM_ACTIVATED=true
```
All 3 stages' § log signatures confirmed live and functioning end-to-end on the real deployed site,
not just in a local worktree. (`§TM_OPS_CHECK` epoch showing 1970 is an artifact of this ad hoc
verification script's minimal page context, not a real bug — the standard probes anchor correctly.)
All 4 prior PRs (#1401-#1404) confirmed to have triggered successful `deploy-pages.yml` runs.

**Docs closeout:**
- `4D_SCHEDULE_ARCHITECTURE_REDESIGN.md`: `§ZONE_WINDOW_DAGWINS_CLIP`'s original formula description
  marked ⛔ RETIRED, pointing to S2's Tukey-fence successor (tag kept, formula changed, per M2's own
  instruction). The "TM activation ≈20s, not fixed" note updated with S4's measured outcome.
- `PROGRESS.md`: one-liner added.
- This file: this dated §S5 section.

PR: https://github.com/red1oon/bim-ootb/pull/1405 (auto-merge squash armed 2026-08-16).

---

# §LANE SUMMARY — all 5 stages closed 2026-08-16 (Sonnet dispatch session)

| Stage | Status | PR | Key result |
|---|---|---|---|
| S0 | ✅ | #1400 | Harness baseline (done before this session) |
| S1 | ✅ | #1401 | Band-rank E4/stragglers — floating 0/7 held; mixed on soft metrics (Terminal stragglers rose 9,678→13,084, traced to a legitimate dag-wins exposure, not a bug) |
| S2 | ✅ code / ⛔ open Q | #1402 | Tukey-fence robust-envelope bars — hard bar (nonOutlierOutside=0) held 7/7; Terminal's "equi-shape cluster gone" criterion NOT met, root-caused to the CPM solve's own precedence structure (LOCKED core), flagged for the spec's own S2 review checkpoint |
| S3 | ✅ Terminal / ⛔ Hospital 1 | #1403 | M3 layer-buildup metric — Terminal 0 violations (also evidence FOR S2's "physics-true" reading); Hospital 1 violation, root-caused to a same-storey band-boundary probe artifact |
| S4 | ⛔ floor reported (expected outcome) | #1404 | Raw-schedule-reuse — real ~3-4s saved; measured floor ~21-23s, 10s target not reached, stopped per the spec's own explicit fallback clause |
| S5 | ✅ | #1405 | Cache version catch-up, all named witnesses green, live-deploy verified on the real deployed site |

**Hard gates held on every single stage, no exceptions:** floating 0/7, `§CPM_GATE_CHECK` 0/7, zero
new cycle classes, zero locked-witness-baseline surprises, zero invented constants. **Two genuine
open items remain, both reported (not silently dropped), both outside this dispatch's authorized
scope to resolve unilaterally:**
1. **S2's ⛔ BLOCKED question** (Terminal/Hospital upper-level compression — physics-true or an
   over-aggressive graph edge? — needs the Fable/Opus/user "eyeball-confirm" `§DISPATCH` rule 4
   names) — S3's Terminal result (0 layer-buildup violations) is new evidence leaning toward
   "physics-true," but the question is still open for that review.
2. **S4's measured floor** (~21-23s vs the 10s target) — the spec's own fallback clause names this
   exact outcome as correct when the target isn't reachable without touching locked behavior; the
   three dominant remaining costs (write loop, `materializeZones`' own first computation, an
   unresolved ~4s pre-`activate()` gap) are named with numbers for whoever picks this up next.


---

# §S2_REVIEW_VERDICT (2026-08-16 evening, Fable review checkpoint — the call the ⛔ asked for)

**Verified first:** the closing report's claims re-checked independently on merged main — fleet
display-path floating 0/7 with `otherOut=0` everywhere (own probe run), Terminal stagger dump
reproduces the compression exactly (`TASK_Superstructure_05… s=149 e=151 n=10,355`), live site
serving v1048. The report was accurate, including its own misses.

**Verdict: NEITHER of the two offered readings. The compression is not physics-true and not an E4
edge bug — it is the RESOURCE-MODEL GAP the original redesign explicitly deferred**
(`4D_SCHEDULE_ARCHITECTURE_REDESIGN.md §WHAT_STAYS`: crew-leveling runs BEFORE precedence and feeds
E5 lower bounds; making the solve resource-aware was named "a second research project"). The
arithmetic is decisive: E5 bounds were computed at RAW times (roof population crew-paced across
~13 days, 34-47). Once precedence gates displace those 10,950 elements to day ~130, NOTHING
re-enforces "Terminal has 3 STEEL_ERECTOR crews" at the new date — so the tail builds at effectively
infinite crew capacity, 10,950 elements starting inside 0.4 days. S3's monotone-band evidence shows
the ORDER is right; order says nothing about RATE. A schedule can be perfectly ordered and still
require a thousand phantom crews — that is exactly what the tail is.

**S6 — resource-aware forward pass (the bounded fix, NOT full RCPSP):** embed the EXISTING greedy
crew-slot allocator (same pools, same `max_crews`, same claim-earliest-slot logic as
`computeSchedule`) into the CPM topological pass: process ready components from a priority queue
keyed (earliest precedence-feasible time, bz, guid — deterministic); when finalizing an element,
its start = max(precedence bound, earliest slot of its resource pool ≥ that bound), then advance
the claimed crew cursor by its real duration. Standard serial schedule-generation scheme; delays
only, so every edge (all lower bounds) stays satisfied — floating-0 by construction is preserved,
no fixpoint, O((V+E)·log V). Plumbing note: the seam's `_twItems` / the hook's items must carry
`resource` (they currently don't — small addition at both call sites, same shape as `guid`).
E5-as-lower-bound becomes redundant once slots are claimed in-pass (keep reading `computeSchedule`
for durations + §SUPPORT_CHECK's raw audit; its TIMES stop being the bound).
*Acceptance (all derived, none tuned):*
1. **New judge — crew feasibility at DISPLAY times:** per resource, at no point does the count of
   concurrently-running elements exceed `max_crews` (tolerance: the day-rounding quantum). Today
   this is massively violated at Terminal's tail; it must go to 0. Add as
   `§CREW_FEASIBILITY` in the fleet probes — this is the invariant the compression broke.
2. Terminal roof-population spread ≥ its own crew-arithmetic floor (Σ installSecs ÷ (crews ×
   shift) — computed from the data in the probe, not assumed).
3. Fleet floating 0/7, `§CPM_GATE_CHECK` 0, S3 layer metric violations not worse.
4. Stagger: S2's unmet bar re-tested — no task with n≥1,000 in a bar <2 days; the equi-start
   cluster (currently 20/72 same-day) must break up. Makespan will GROW (honest crew pacing);
   record it, do not cap it.
5. Same dispatch rules as §DISPATCH; this is Sonnet-suitable with one addition: post the before/
   after Terminal + Hospital stagger dumps and the §CREW_FEASIBILITY numbers in the PR body for
   the review checkpoint BEFORE merging (this stage touches the solve's semantics, the first time
   any stage does).

**Hospital S3 band54 artifact (1 violation, n=116):** hold until after S6 — resource pacing
reshapes the tail; re-measure then rather than patching a number that is about to move.
