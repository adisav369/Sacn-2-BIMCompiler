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
