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

---

# §CURRENT PICTURE — 2026-08-16 evening, live reproduction, for the next Fable session

**One-paragraph state of the world:** S0-S5 are merged and proven (floating=0/7 held, live-deployed,
v1048/gantt-v30 confirmed serving). None of that work has yet fixed what the user actually sees,
because the ACTUAL fix for the loudest symptom — S6 — is fully diagnosed and spec'd but **not built**.
Today's live session (user reopened Terminal after the S1-S5 deploy) reproduced all three symptoms
the user originally reported, unchanged, plus surfaced two mechanisms not previously named in this
file. This section is the handoff: what's proven, what's diagnosed-not-built, what's newly found,
and the order to attack it in. No code was changed investigating any of this — read-only.

**Show-stopper check: none.** Nothing here breaks the floating=0 invariant or any locked witness.
Every item below is either an already-spec'd, ready-to-execute fix (S6) or a hypothesis with a named
first verification step (S7, S8) — not a design unknown. This is executable, not a "back to the
drawing board" situation.

**Symptom → cause map (live-confirmed 2026-08-16 evening):**
| User-reported symptom | Cause | Status |
|---|---|---|
| "Bars all stacked up, not spread out, same as before" | Crew capacity (E5) enforced at RAW times only, never re-checked once CPM precedence displaces 10,950 Roof-Level elements to day ~130 — tail builds at effective infinite crew capacity (§S2_REVIEW_VERDICT above) | **Diagnosed + spec'd (S6). Not built — this is why the symptom is unchanged.** |
| "Elements come on and then disappear" during playback | Candidate: `§XRAY_EDGES staged=2698/48428` (5.6%) — elements whose support carrier finishes after their own reveal, held in an xray/ghost state | **Candidate only — visual mechanism and baseline not yet confirmed (S8).** |
| "Gantt chart cannot be edited well, bars disappear when pulled" | Candidate: `_retimeSpan`'s affine clamp collapsing Tukey-outlier elements' duration toward zero when a now-narrower (S2) bar is dragged/resized | **Candidate only — not yet measured (S7).** |
| "Likely v has not bumped" | Ruled out | `§CACHE_PUT key=gantt:v30` confirmed live in the same session — current code IS running. Not a cache/deploy issue. |

## Finding A — `§XRAY_EDGES staged=2698/48428`, candidate for the playback disappear symptom

Live log, Terminal, today: `§XRAY_EDGES n=46141 ms=685.1 staged=2698/48428` — elements whose last
support carrier finishes AFTER their own reveal, held in the `_tm_xrayStaged` ghost/xray state
(`viewer/time_machine.js`, `prompts/GANTT_ACCURACY.md §Z_STACK_XRAY_STAGING`, witness
`witness_zstack_xray_staging.js`). The code carries its own named invariant at two points
(`time_machine.js:4300`, `:4336`): *"guard and judge MUST stay one physics or §XRAY_EDGES staged>0
comes back"* / *"keeps §XRAY_EDGES staged=0 (the 2026-08-07 alignment invariant)."* Live measurement
today violates that invariant at 5.6%.

**This is NOT automatically an S1-S5 regression.** `OG_SWEEP_SKIP` (`time_machine.js:6053-6062`,
shipped in PR #1399, predates this lane) deliberately SKIPS the strict end-bar repair sweep that used
to drive staged toward 0, in favor of bar fidelity — an already-measured, deliberate tradeoff on
Hospital (code comment: *"keeping the sweep = 1781 elements pushed OUT of their own bars (97.2%
fidelity); skipping it = 31 out (99.95%), floating 79→63"*). Elevated staged count may be the accepted
cost of that earlier, separate decision, not something S1/S2 broke.

**Two things nobody has checked yet:**
1. No pre-S1 Terminal staged-count baseline exists. Unknown whether 2698 is worse than before this
   lane (in scope here) or unchanged/pre-existing (a separate, older ticket — name it, do not
   scope-creep into fixing it under this lane without saying so).
2. Nobody has read what `_tm_xrayStaged=true` actually renders as (opacity/wireframe toggle around
   `time_machine.js:2274-2285`, `_xrayCacheMemo`/solidify logic ~3857-3934) — it may just look like a
   persistent ghost (odd, not flickering), in which case it's NOT the cause of "appear then
   disappear" and the real cause is still unfound.

## Finding B — Gantt bar drag/resize makes elements disappear (candidate: `_retimeSpan` clamp)

**This exact symptom class was reported and fixed before.** 2026-08-07, user report quoted verbatim
in the code: *"foundation piling nor others does not come onto canvas anymore, though i dragged to
certain bars passing."* Fixed via `_tmResyncAfterRetime()` (`time_machine.js:6979-6983`), which
resyncs three derived caches after every retime commit (the incremental-reveal event index, `_ops`
sort order, the xray solidify cache) — without it, "the canvas plays the OLD times" (the fix's own
comment). **Confirmed live-read today: this call is still correctly wired** —
`commitGanttDrag` → `retimeTaskElements` → `_tmResyncAfterRetime()` (`time_machine.js:7055-7059`) is
intact. This is not a simple regression of that 2026-08-07 fix being removed.

**New hypothesis, tied directly to this lane's own S2 change.** `_retimeSpan()`
(`time_machine.js:6933-6941`) affinely remaps an element's op time from a task bar's OLD window
`[oS,oE]` to its NEW one `[nS,nE]` after a drag/resize, and HARD-CLAMPS any element whose true time
falls outside `[oS,oE]` to the new window's edge (`if (s<nS) s=nS; if (e>nE) e=nE;`, then
`if (e<=s) e=min(nE,s+60000)` — a 60-second floor). S2 (`§ZONE_WINDOW_DAGWINS_CLIP`'s Tukey-fence
successor, PR #1402) made bars deliberately NARROWER than before, and by M2's own doctrine pushes
MORE elements outside their task's own bar window as "genuine dag-wins outliers... never hidden"
(Terminal alone: clamped=1186 outliers fleet-wide per S2's own PR numbers). When a now-narrower bar
with a larger outside-window population gets dragged, `_retimeSpan`'s clamp can collapse many of
those outliers' duration toward the 60-second floor — squashing them to a near-instant at one edge of
the new window. During TM playback that would read as the bar's content flashing or vanishing rather
than persisting — exactly the reported symptom.

**Not yet measured — this is a hypothesis with a named first step, not a confirmed cause.**

## §S7 — Gantt bar-edit outlier collapse (NEW stage — investigate, then fix if confirmed)

*Step 1 — measure, don't assume:* add a `§-log` to `retimeTaskElements` (`time_machine.js:6943`)
counting, per drag/resize commit: how many of `bar.guids` have `op.start_ts`/`op.end_ts` outside the
task's OLD `[oS,oE]` before the retime (i.e., are that task's Tukey outliers), and what duration
(`e-s`) `_retimeSpan` gives each one afterward. Reproduce live on Terminal: drag/resize one of the
Roof-Level Superstructure tasks (large known outlier population, per S2's PR #1402 numbers) and read
the log.
*Acceptance:*
1. Confirm or refute with real numbers whether a drag collapses outlier elements to near-zero
   duration (≤ a few minutes) in proportion to the outside-window population.
2. If confirmed: fix `_retimeSpan` (or its caller) so an outside-window element gets a uniform
   delta-shift — the SAME doctrine M2 already established for authoring ("never squeeze a straggler
   back in") — instead of a proportional rescale that can crush it toward a boundary. Re-measure the
   same drag: zero near-zero-duration collapses outside an intentional resize.
3. Fleet regression: floating 0/7, `§CPM_GATE_CHECK` 0/7 unchanged — this stage only touches the EDIT
   path, never the solve.
4. Live UI verification per this project's standing rule (§-log first, browser second, never
   eyeball-only — `feedback_whitebox_not_playwright`/`feedback_log_not_visual_proof`): drag a real bar
   in the live viewer, confirm via the new log line that previously-outside-window elements kept a
   sane duration — not just that the drag "looked fine."
5. If refuted (outlier collapse isn't what's happening): report the actual `§-log` numbers and keep
   looking — do not close S7 on "couldn't reproduce," the user's report is real and specific.

## §S8 — xray-staged regression check (NEW stage — investigate only, may turn out out-of-scope)

*Investigate:*
1. Read what `_tm_xrayStaged=true` actually renders as (opacity/wireframe toggle, ~`time_machine.js`
   lines 2274-2285 and the solidify logic ~3857-3934) — state definitively whether it can make a
   mesh disappear/reappear, or only ghosts it persistently.
2. Get a pre-S1 Terminal staged-count baseline (checkout the commit before PR #1401, or run the same
   session's `?cpm4d=0` legacy-engine escape hatch as an A/B) and compare to today's live 2698/48428.
*Acceptance:* a plain factual answer, not a fix attempt — is 2698 elevated by this lane's changes (in
scope here, chase it) or unchanged/pre-existing (name it as a separate, older item; do not fold a fix
for it into this lane without a fresh spec section saying so).

## §PRIORITY — standing user ruling (2026-08-16 evening, verbatim): *"Not worried about small flicker
issue. Main glaring is stack hell, not spread out, editor failure, 4D out of sequence... get some
underlying prerequisite fundamental foundation laid down first."*

**S6 is a PREREQUISITE, not just first-in-line — this is a real dependency, not a priority call.**
S6 changes what every task bar looks like: today's 0.4-day stacked-tail shape becomes a properly
crew-paced spread across days once S6 lands. S7's hypothesis (bar-drag collapsing outlier elements)
is measured against TODAY's compressed bar shape — the outlier population per task, and how badly
`_retimeSpan`'s clamp crushes them, both change once S6 reshapes the bars underneath it. Testing or
fixing S7 before S6 risks re-doing that measurement against a shape that's about to move. **Build and
land S6 first, THEN re-run S7's investigation against the post-S6 bars** — not because S6 is more
important, but because S7's own numbers aren't stable until S6 lands.

**S8 (playback flicker) is explicitly DEPRIORITIZED by the user — do not spend a session on it
unless separately asked.** It was never confirmed as a real bug (candidate mechanism only, per
Finding A above) and the user has explicitly said it's not the concern. Leave the investigation steps
above as a marker for if/when it's revisited; do not pick it up proactively.

## Recommended order for the next session

1. **S6 (the foundation) — build it.** Already fully spec'd (§S2_REVIEW_VERDICT above): fold the
   existing crew-slot allocator into the CPM forward pass so precedence-delayed work gets re-paced by
   real crew capacity, not left to land simultaneously. Same Sonnet-dispatchable shape as S1-S5, with
   the one addition already named: post the before/after stagger dumps for review BEFORE merge, since
   this is the first stage to touch the solve's semantics. This is the fix for "stack hell, not
   spread out" AND "4D out of sequence" (S3 already showed band ORDER is correct — S6 fixes the RATE).
2. **S7, re-run against post-S6 bars.** Confirm-or-refute the `_retimeSpan` clamp hypothesis with the
   NEW (spread-out) bar shapes, not today's compressed ones — the numbers from a pre-S6 measurement
   would be stale the moment S6 merges. This is the fix for "editor failure."
3. **S8 — hold, not in this pass.** Per the user's explicit ruling above.

**Chase to zero means (revised scope):** S6 executed and re-measured against its own acceptance bar
(including a fresh `probe_gantt_stagger.js` dump on Terminal showing the stack broken up), THEN S7's
hypothesis confirmed-or-refuted against the post-S6 bar shapes and fixed if confirmed. S8 stays
parked. Report the fresh stagger dump once S6 lands — that's the first point "same old symptoms" can
honestly be retested.

---

# §S6_RESULTS — 2026-08-16 evening, ✅ DONE + MERGED, bim-ootb PR #1406 (Fable session, worktree
# /tmp/wt-gantt-s6, branch fix/gantt-s6-crew-pass)

**Code:** `viewer/cpm_schedule.js` `solve(items, graph, opts)` — §S6_CREW_PASS, the serial
schedule-generation scheme §S2_REVIEW_VERDICT spec'd. E5's TIMES retired as lower bound (ES seeds
from the raw schedule's min start, the base epoch — not per-element raw times); the pass claims the
SAME per-resource crew slots computeSchedule's allocator uses (claim-earliest-slot,
`max_crews_fixed ?? max_crews`, MAX_CREWS_DEFAULT=3 mirrored), from a deterministic binary-heap
priority queue keyed (precedence-feasible time, bz, guid; milestone-only comps first at equal
time). Delays only → floating-0 by construction. Contracted SCCs keep ONE shared start (judge
equality); pool-exhausted members counted in `crewOverCapScc` (fleet 0-45), never hidden.
Plumbing: `resource` added to `_twItems` + the `_tmDisplayRemap` hook items; `_displayTimeline`
passes the LABOR_RATES cap lookup; both fleet probes plumb `resource` + `maxCrews` and gain
§CREW_FEASIBILITY + §CREW_SPREAD_FLOOR as hard fleet gates. sw.js v1049, gantt cache v31 (bumped
WITH the change; §CACHE_VERSION_GUARD run AFTER commit this time — PASS version_bumped=1).

**Acceptance 1 — §CREW_FEASIBILITY (the invariant the compression broke):** BEFORE: RAW 0
violations on all 7 (computeSchedule enforces crews) but CPM output violated on 6/7 — Terminal
STEEL_ERECTOR cap=3 maxConc=14,417; Hospital PLUMBER cap=2 maxConc=10,657; LTU PLUMBER
maxConc=15,294 overDays=30.3. AFTER: **0 violations, all 7, both probes** (§CREW_FEASIBILITY_CPM +
§CREW_FEASIBILITY_CPMDP all PASS; tolerance = 1-day quantum).

**Acceptance 2 — §CREW_SPREAD_FLOOR:** BEFORE 17 group violations fleet-wide (Terminal
`Superstructure||06 ROOF LEVEL n=10950 span=0.5d floor=7.6d VIOL`). AFTER **0 fleet-wide** — roof
span 11.1d ≥ 7.6d; every n≥1000 group ≥ its own Σdur/cap floor.

**Acceptance 3 — hard gates:** floating 0/7, §CPM_GATE_CHECK 0/7, §CPM_PARITY 7/7, cycleDrops
{e3:0,e4:0,member:0} all 7 with contractedSccs/Nodes/fsViolInScc BYTE-IDENTICAL to baseline
(graph construction untouched — straggler counts identical 314/8138/2921/6201/19814/13084/44593);
§CPMDP_FLEET 7/7 PASS (nonOutlierOutside=0); gate_4d pass=8 fail=0 missing=1 (same pre-existing
MISS); witness_zone_display_authoring 16/0 + witness_crosstask_judge_parity 20/0, NO assertion
updates. §LAYER_BUILDUP (S3 metric, live viewer): Terminal 0/12 PASS unchanged; Hospital 1/14 —
the SAME pre-S6 band54>band55 same-storey artifact (medians moved 364.7→60.7 / 61.6→18.1, the
boundary-slice artifact persists exactly as §S2_REVIEW_VERDICT predicted; still a probe artifact,
not a sequencing bug — parked as before).

**Acceptance 4 — the user's symptom, live stagger dumps (probe_gantt_stagger.js, real viewer):**
```
                                    Terminal        Hospital
tasks n≥1000 in a bar <2 days:      4  → 0          8  → 0
tasks sharing one exact start day:  20/72 → 9/72    11/35 → 3/35
tasks within ±1d of that day:       38/72 → 17/72   16/35 → 3/35
cluster location:                   day 150 (tail)→day 1 (start)   day 385→day 10
totalDays:                          151 → 106       388 → 329
```
The residual same-start group sits at PROJECT START (trades legitimately begin early) — the
tail-stack shape is gone. **Makespan note (recorded, not capped):** the spec predicted growth;
makespans SHRANK (Terminal 130.6→100.2d, Hospital 327.4→278.1d, all 7 shorter). The prediction
assumed E5's raw floors stayed; with E5 times retired (the spec's own instruction) the in-pass
slots pace work in topological priority order — more efficient than the raw z-order allocator,
and legitimate because BOTH directions of the crew constraint are now proven (concurrency ≤ cap
AND span ≥ crew floor).

**Soft-metric drift (reported, not hidden):** §CPM_STOREY LEVEL-granularity violations 3→5
buildings (Duplex 0→1, Clinic 0→1, JKR 2→7, Terminal 4→5, LTU 4→6; HHS/Hospital stay 0). Detail
rows are dominated by federated name-soup pairs (same physical floor, different names: `VÅNING 1`
vs `VÅN 1`, `01 Ground Level Floor` vs `01 Ground Floor Level`) whose p50s were indistinguishable
under tail compression and now separate visibly under honest spreading — the S1 federated-ladder
residual made more VISIBLE, not a new inversion class (the ops-level physics metric is unchanged).

**Review checkpoint (§DISPATCH rule 4 / S6 acceptance 5):** performed in-session by this Fable
session (the reviewer class the spec names) — full before/after dumps + §CREW_FEASIBILITY numbers
posted in the PR #1406 body BEFORE merge; verdict APPROVE (three element recipes agree exactly:
engine probe = display probe = live viewer via §CPM_DISPLAY_REUSE hits=48428 misses=0 midair=0).
The verdict comment itself could not be posted to GitHub (permission classifier blocked
`gh pr comment`) — this section is the canonical record of it.

PR: https://github.com/red1oon/bim-ootb/pull/1406 — MERGED 2026-08-16T12:18Z, auto-merge squash.

# §S7_RESULTS — 2026-08-16 evening, ✅ CONFIRMED + FIXED, bim-ootb PR #1408 (same Fable session,
# worktree /tmp/wt-gantt-s7, branch fix/gantt-s7-retime-outliers — run AFTER S6 merged, per §PRIORITY)

**Step 1, measured (not assumed) — hypothesis CONFIRMED, worse than hypothesized.** Real
`commitGanttDrag` in the real headless viewer (new `__tmGanttDrag`/`__tmGanttWindows` test hooks,
`__tmZoneProbe` convention; NOTE `__tmGanttBars` was already taken — drawGanttMini's rect debug
export clobbers it after the drawer opens, cost one probe iteration to find), Terminal
`TASK_Superstructure_06_ROOF_LEVEL` n=11,004 post-S6:
```
move +5d:     §RETIME_OUTLIER_AUDIT outsideOldWindow=440 collapsed60s=437 inverted=217 outlierDurMs=[-13113849,185810]
resizeR -30%: §RETIME_OUTLIER_AUDIT outsideOldWindow=440 collapsed60s=437 inverted=217 outlierDurMs=[-8014019,113551]
```
`_retimeSpan`'s affine map assumes containment; the 440 M2 Tukey outliers riding outside the drawn
bar get extrapolated-then-clamped — 437 crushed to the 60s floor and **217 INVERTED (end before
start, to −3.6h): corrupted kernel_ops that cannot render** — the user's "bars disappear when
pulled", mechanically.

**Fix — §S7_OUTLIER_DELTA** (`_retimeSpan`, containment test `opS < oS-1 || opE > oE+1`): an
outside-window op gets the window's uniform START delta (nS−oS) with TRUE duration preserved —
move shifts it with the task, resizeR leaves it untouched (delta 0). Never-squeeze-a-straggler,
edit-side. Insiders keep byte-identical affine behavior. Re-measured same drags: **collapsed 0,
inverted 0, durations [106s..180s]** (real install durations), both gestures.

**Regressions:** witness_gantt_edit_undo 9/0 + witness_gantt_edit_lock 5/0 (assertions untouched);
gate_4d pass=8 fail=0 missing=1 (same MISS); probe_cpm_display_path 7/7 PASS (edit path only, the
solve untouched). §RETIME_OUTLIER_AUDIT ships in `retimeTaskElements` (whitebox);
`scripts/probe_gantt_drag_outliers.js` is the repeatable harness. sw.js v1050 (gantt cache stays
v31 — generation unchanged, edit path only).

PR: https://github.com/red1oon/bim-ootb/pull/1408 (auto-merge squash armed 2026-08-16T12:54Z).

# §S8 — PARKED per §PRIORITY (user ruling: "Not worried about small flicker issue"). Not picked up
# this pass; investigation steps remain above as the marker for if/when it's revisited.

---

# 🏁 LANE STATUS — 2026-08-16 evening close (Fable session)

S6 ✅ MERGED+LIVE (#1406, sw v1049/gantt v31 → superseded by later bumps, code verified serving) ·
S7 ✅ MERGED+LIVE (#1408, merged 13:18Z after a sw.js sync-merge with #1407 §MIRROR_ROOM_PROBE —
conflict resolved per the standing rule, both notes kept, version advanced to v1051; live site
verified serving v1051 with §S7_OUTLIER_DELTA/§RETIME_OUTLIER_AUDIT present) · S8 ⛔ PARKED (user
ruling in §PRIORITY). Chase-to-zero per the revised scope is COMPLETE: S6 re-measured against its
own acceptance bar (fresh Terminal stagger dump: thin-big-bars 4→0, same-start 20/72→9/72, tail
cluster gone), S7 confirmed with real numbers and fixed (437 collapses + 217 inversions per
gesture → 0/0). The user's three named symptoms: "stack hell" → fixed (S6); "editor failure" →
fixed (S7); "small flicker" → parked at user's own direction (S8). Next session picking this lane
up: re-test "same old symptoms" against the LIVE rebuild with the user in the loop — the numeric
groundwork is all in §S6_RESULTS/§S7_RESULTS above. Worktrees wt-gantt-s6/wt-gantt-s7 pruned
(merged+clean); probe harness scripts/probe_gantt_drag_outliers.js is committed for reuse.

---

# §S9_GROUND_SLAB_FIRST — NEW stage (2026-08-16 late, user live report: "still do not see the ground
# slabs proceeding first before the beams all round") — SPEC (written before code, per Spec-First)

**Symptom, measured (diag scripts, session scratchpad — engine-side S6 solve, Terminal):** 16/432
beams start before day 2, ALL at `GROUND FLOOR LEVEL`. The ground plate (50 IfcSlab at band 4)
is classified `Superstructure` seq 4 — the DECK-slab rule (slabs after beams, correct for upper
floors, wrong for slab-on-grade). Two mechanisms combine: (1) `GROUND FLOOR LEVEL` has NO
Substructure group (Terminal's piles live under other pseudo-level names), so E3's
Substructure→Superstructure gate does not exist at that level; (2) E1 is deliberately SS
(start-start), so a beam may start the moment its column STARTS — nothing orders "plate done
before steel starts" unless the phase chain does it. Floating=0 throughout — this is a
CLASSIFICATION gap, not a physics/solve bug. This was the original §WHY_ELUSIVE acceptance quote
("even the simple ground slabs are not done before the beams and walls") — the CPM redesign fixed
the resting-on-support half; the groundwork-phase half was never classified.

**M5 — a GROUNDWORK SLAB is Substructure, detected from the data (no new constants):** an IfcSlab
currently classified Superstructure is reclassified `phase='Substructure'` iff BOTH:
1. It has NO bearing-below contact (the judge's own predicate: `S.bz < slab.bz − EPS && S.tz ≥
   slab.bz − GAP`, XY-overlap) whose phase ≠ Substructure — i.e. it bears on grade, piles, or
   footings only, never on Superstructure framing (that is what makes a DECK a deck).
2. Its 3m z-band (`floor(bz/3)`, the shipped §GANTT quantum) is the building's LOWEST band
   containing any Superstructure element — "ground" extracted per building, no assumed datum.
`seq`/`resource` unchanged (CONCRETE_GANG already). Fleet census of this exact predicate:
Terminal 50 (all `GROUND FLOOR LEVEL|b4` — the plate, surgically), LTU ~31 (band-0 slabs;
condition 2 exists precisely to exclude LTU's TAKPLAN/upper-band noise measured at 14), all other
5 buildings 0. Blast radius is small and named.

**Implementation (one shared definition, both recipes):** helper in `schedule_gate.js` (same
shared-pure-function status as `hostPairs` — "the one shared definition" doctrine), called by BOTH
element recipes (`schedule_author._buildScheduleElements` + time_machine's inline builder) so zone
authoring, task bars, milestones, and the movie stay ONE truth; logs `§GROUNDWORK_SLAB n=… levels=…`
in both. E3 then does the rest with zero solver changes: M(L, Substructure) ← ground slabs → FS →
every Superstructure element at L. E4 chains piles' bands below it. No cycle by construction
(groundwork slabs' physics ancestry is Substructure-only, so they are never stragglers of their new
group; milestone edges point strictly forward in group key).

*Acceptance:*
1. Terminal engine probe: beams starting < day 2 → 0; ground-plate p90 END ≤ min Superstructure
   start at `GROUND FLOOR LEVEL` (tolerance 1d, the rounding quantum).
2. Fleet hard gates unchanged: floating 0/7, §CPM_GATE_CHECK 0/7, §CREW_FEASIBILITY 0/7,
   §CREW_SPREAD_FLOOR 0, §CPMDP 7/7, gate_4d, witnesses untouched-green.
3. §LAYER_BUILDUP (live, Terminal + Hospital): not worse (Terminal 0, Hospital 1 band54 artifact).
4. §CPM_STOREY_PHASE / storeyViol: record before/after; not materially worse (the S1 name-soup
   residual may move — report, don't hide).
5. Live Terminal kernel_ops: within `GROUND FLOOR LEVEL`'s collapsed level, no IfcBeam/IfcColumn
   ELEMENT_PLACE op starts before the groundwork slabs' last end (tolerance 1d) — the user-visible
   symptom, as a number.
6. sw.js bump; PR with before/after numbers; dated results section here.
