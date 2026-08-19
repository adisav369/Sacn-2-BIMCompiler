# ⚠ WATCHDOG — READ THIS FIRST, BEFORE ANY OTHER SECTION IN THIS FILE (2026-08-18, appended after a
# multi-day thrash loop — see §RESULTS and its addendum further down for the receipts).

**Posture for whoever opens this file next: distrust-by-default, not continue-by-default.** The days
before this note produced a real, measurable pattern: local fixes verified against proxy metrics
(synthetic sandbox pass, graph-structure correctness, "floating=0") got reported and merged as if they
answered the real acceptance question, when they hadn't — and at least once, a claimed regression
turned out to be a stale-DB measurement artifact, not a code bug, caught only because someone
distrusted the number enough to check the raw file mtimes behind it. That's GIGO in its precise sense
here: not "bad data" alone, but confident conclusions computed on top of ground truth (real buildings,
real dates, real element-to-element order) that was never itself independently confirmed before being
trusted — proxy validation standing in for ground-truth validation, several layers deep before anyone
checked the bottom layer.

**Standing mandate, until explicitly closed:** do not resume feature work, and do not trust any prior
session's "N bugs fixed" / "floating=0" / "verified" claim in this file at face value — re-derive it.
Start from the smallest provable unit and rebuild confidence bottom-up: can this engine get ONE real
building's FIRST substructure phase, on storey 1, to measurably, provably finish (in real days) before
the first element of the very next phase starts — checked directly against real building data, not a
synthetic case, not a graph-structure argument about what a gate should enforce. Until that one
concrete, real-building acceptance test passes and reproduces, treat every downstream claim in this
file (fleet floating counts, "5 bugs fixed," phase gating "working") as unproven, however many PRs
merged on top of it.

**Do not repeat the failure mode named in §RESULTS / its addendum below**: a fix that is locally
correct (passes its own synthetic case, its own unit-level graph check) but was never checked against
the one thing a person on site would actually ask. Every next step from here starts by re-confirming
ground truth, not by building the next layer on an unconfirmed one.

---

# ⚠ DO NOT REMOVE — Build one class: `compute(elements, opts) → ScheduleModel` (per-element times,
# one clock, no rollup baked in), wrapping `ScheduleGate.computeSchedule → CpmSchedule.run` — the
# actual duplicated pair — as the single place "when does each thing get built" is computed.
# `elements` = already-classified (the output of `ScheduleAuthor._buildScheduleElements`, itself
# already the one correct source for classification — do not re-home or duplicate it). Flag, don't
# silently work around, anything else in the current code that computes or persists schedule timing
# independently of this.
#
# ⚠ "Wrapping" means structural consolidation ONLY — it does NOT mean the wrapped code's behavior is
# trusted. `CpmSchedule.run`'s own internal rules (the E1-E4 dependency logic) must independently
# satisfy §VERIFICATION below before this is done. Already found once: the phase-completion gate's
# straggler exemption let a phase count as "done" while most of it wasn't — a faithful wrap of that
# bug is still that bug. Assume nothing inside the wrapped pair is correct until proven — treating
# any part of it as settled and beyond question, without a test proving it, is exactly the habit
# that let this go unnoticed as long as it did.

# §VERIFICATION — the standing discipline, not a one-time pass. No single piece below is sufficient
# alone; each covers what the others can't.
1. **Hand-verified synthetic cases are the primary proof.** A small, fully synthetic scenario with
   its expected per-element result computed BY HAND, before running the engine — not a real
   building. Real data never gives a known-correct answer to diff against, only numbers that seem
   more or less plausible. Live under one location, `scripts/witness_*.js` (this project's existing
   naming convention — reuse it, don't invent a new one). Starting set, already known to matter, not
   optional to defer: stragglers present in a phase, a deadlock-stress case (an element that can't
   cleanly resolve), a level missing a phase, an orphan element, parallel independent zones on one
   band. Add to that set — don't just patch and move on — whenever a real building surfaces a shape
   none of the existing cases cover.
2. **Every synthetic case is a standing gate, not a one-off.** Find this project's existing
   fleet-gate script (the one already run before any change here is called complete — grep for it,
   don't assume a name) and wire every case into it. Do not create a second, parallel gate.
3. **The fleet trace stays running, as secondary confirmation.** It catches whatever the synthetic
   cases didn't think to cover. Real buildings keep surfacing shapes nobody designed for.
4. **Single source of truth is what actually closes off the class of bug, not just one instance of
   it.** As long as the bars and the axis read the engine's real output directly — nothing computed
   independently downstream — the display cannot drift wrong again even when the engine is right.
   Any future change that adds a second computation of schedule timing anywhere is the same defect
   recurring, regardless of what it's called.

---

# §RESULTS (2026-08-18, session close — pending a full spec overhaul, not a continuation point)

Five real bugs found and fixed this session, each hand-verified before being trusted, each shipped
(bim-ootb PRs #1431-#1435): the Gantt bar aggregation's untrimmed small-group cliff; a duplicate copy
of the same cliff sitting in the separate axis/ruler calculation; the phase-completion gate's
straggler exemption (was silently letting a phase count as "done" while most of it wasn't, measured
54-100% of a phase on real buildings); a grounded element (footing, base slab) getting a backwards
"I depend on what's built on top of me" relationship; and the floating-violation checker itself,
which could only tell "something nearby" from "something I actually depend on" once the grounded-
element bug above stopped accidentally masking that it couldn't. `floating=0/7` holds, confirmed two
independent ways, live on `origin/main`. 7/7 synthetic cases pass, hand-computed before running the
engine, re-run directly against the merged code, not taken on a report's word.

**The honest gap, named plainly, not smoothed over — this is why the session stopped here, not a
success note.** None of the above answers, in the terms a person on site would ask it, whether
substructure — or floor slabs — are ACTUALLY complete before a single beam of the first storey goes
up. The engine's phase-completion gate (fixed this session) is built to enforce exactly this,
structurally — but "the gate exists and requires the whole group" is not the same claim as "measured:
substructure fully finishes before the first beam starts, on this building, by this many days." No
test in this file asks that specific question directly, at that specific granularity (phase A vs. the
very next physical element of phase B), and it has not been measured against real fleet data at that
granularity either. The synthetic suite's case 1 shows one hand-built column starting exactly when
its one hand-built footing finishes — a toy proof of the shape, not a general one, and not something
that stands in for a real answer.

**What a spec overhaul needs to make first-class, not an afterthought:** an acceptance test stated in
construction terms — "does phase A fully finish, on this floor, before the first element of phase B
starts" — checkable directly, by name, not inferred from a graph-construction argument about what the
gate SHOULD enforce. Everything shipped this session is real and independently verified for what it
claims; what it does not yet claim is the one thing actually being asked for.

---

## §RESULTS addendum (2026-08-18, later same day — the session above has closed and can't correct its
## own claim, so this does)

**"`floating=0/7` holds, confirmed two independent ways" above needs correction, not retraction.** A
follow-up fleet-gate run on the SAME merged code found `witness_midair_zero`'s separate TRADE
invariant (does a dependent START before its support FINISHES — a different check from the
midair/appear-order one this session fixed) failing on 4/7 buildings: Terminal 12→10011, Hospital
0→8103, Duplex 0→237, HHS_Office_Federated 9→1491. Terminal and Hospital are confirmed **measurement
artifacts** — the witness read stale `Terminal_extracted.db`/`Hospital_extracted.db` (untouched since
June/Aug 3) instead of the correct, same-day-patched `_meta.db` pair (PRs #1427/#1428) — not a real
regression for those two. **Duplex and HHS have no meta/geo split**, so that explanation does not
apply — their regression is still real/unexplained, not cleared either way.

Separately, unresolved: whether yesterday's `Terminal_meta.db`/`Hospital_meta.db` elevation+parentage
patch (#1427/#1428) is itself clean, or a fresh data-integrity problem — self-contradiction check
in progress, same method §S10 used to catch the earlier real corruption.

A live forensic measurement is in progress on Terminal, using `Terminal_meta.db` specifically (not
the stale extracted copy): does storey 1's Substructure phase actually finish, in real days, before
the first Superstructure element starts — the exact acceptance test named as missing above, on a real
building for the first time. Result pending.

**Do not read "floating=0/7" or "5 bugs fixed" as this lane's closing state.** The TRADE-invariant
question (Duplex/HHS) and the meta.db integrity question are both open as of this addendum — whoever
picks this up next should resolve those before treating today's merges as the end of the story.

---

---

# §STATUS — read this first (2026-08-19, end of a full consolidation)

> **Resuming in a new session? Read `# 🔄 §RESUME` at the END of this file first — it names the
> standing rulings and the required order of work. Both agents it lists have since REPORTED (§S33);
> §S34 then decided the tie-break §S33.1 left open and §S35 built the derivation. Next open step is
> §RESUME R.5 step 3 — a probe carrying the REAL engine gates — then ONE vetted spec.**

**Nothing has shipped. `viewer/` is unchanged.**

**⚖ STANDING RULING — §S32 (user, 2026-08-19), read before acting on ANY section below:** the
extractor is CORRECT and must not be changed; `buildings/*.db` are FROZEN and must not be rebuilt or
written to; every derived fact is computed at RUNTIME, ONCE, on load — the room-injection pattern.
§S32.2 lists what this CANCELS in §S27, §S28 and §S31, including the Hospital rebuild those sections
called for.

| § | what it is | standing |
|---|---|---|
| §S25_REVIEW | outside review + what measurement did to it | **HOLDS** — carries the one ship-ready change (§S25_REVIEW.6) |
| §S26 | evidence base — the blob is predicate-made, `hang` redundant, LBMS/trains, the IFC container already exists and is empty | **HOLDS** — independently re-derived by two adversarial passes |
| §S27 | "the grid", 7-stage spec | ⛔ **NOT VETTED** — 4 blocking (§S27.R) |
| §S28 | "two lanes" | ⛔ **NOT VETTED** — 4 blocking (§S28.R); its premise, lane independence, is false in both directions |
| §S29 | generality audit — the design is fitted to 7 multi-storey new-build buildings | **HOLDS** |
| §S30 | the literal sort key, measured | **HOLDS** — instrument-guarded; qualified by §S31.1 |
| §S31 | the extraction chain — what is lost and where | **HOLDS** |
| §S32 | USER RULING — extractor correct · DBs frozen · derive at runtime | **STANDING** — unconditional |
| §S33 | both in-flight agents reported: coverage gate PASSED, §S25_REVIEW.6 claim DIED | **HOLDS** |
| §S34 | declared-vs-geometry tie-break — measured, then RULED (2026-08-19) | **HOLDS** — instrument-guarded, shuffle control 3.9–36.9× |
| §S35 | the runtime level derivation, BUILT — `build/level_deriver.js` + `scripts/witness_level_derive.js` | **BUILT, NOT WIRED** — 100% coverage 7/7, T4=0, 14/14 hand-computed fixtures |
| archive | §S23 · §S24 · §S24_TRIAGE · §S25 · §S25_PROTO | superseded, moved out |

**Two designs written, two rejected, and the measurements that followed refuted premises BOTH of
them rested on** (§S30 killed elevation-first ordering; §S31.1 then qualified §S30 itself; §S31.3
reframed §S26.6 C2). The evidence sections are strong and keep invalidating the design sections.
**That is the pattern this file now records: measure first, then design — not the reverse.**

## ⛔ NOTHING IS SHIP-READY (corrected 2026-08-19)

**§S25_REVIEW.6 was the candidate and it did NOT survive being built.** Its numbers were a
MIS-ATTRIBUTED COMPARISON (prototype-with-v2 vs live engine — see the ⛔ block above it).
Built for real, the tie-break alone gives 6/7 or 5/7, not 7/7, with HHS_Office regressing on
both paths and JKR on one. Not shipped, no PR. The claim as originally written was:
float better on **7/7** (Terminal 4,756→1,555 · Hospital 7,753→1,293 · Clinic 1,102→327 ·
JKR 3,183→1,072 · HHS 1,531→243 · Duplex 247→152 · LTU 12,712→9,461), phase gap better-or-equal on
7/7, backward supports −29% to −51%. It is a tie-break inside one function — no architecture change.

**Shipping it requires, in ONE PR:** both twins changed together (`cpm_schedule.js:120` AND
`time_machine.js:4575` — `witness_midair_zero.js:127` and `probe_captured_floating.js` slice
`_designatedSupport` out of `time_machine.js` BY SOURCE TEXT, so a one-file edit goes green on code
that never ran); a witness for the election itself; and W-MZ-8's float baselines re-measured and
re-locked on all 7 in the same PR.

## What is NOT ready

- **The sort is not validated for shipping.** Under real crew caps it fails 2/7 (Duplex +10,
  HHS_Office +572). 7/7 happens only with capacity lifted to infinity (§S31.1), which is not a
  product, and 21-65% of elements still have a support that sorts after them (§S30.3).
- **`scripts/probe_s30_sortkey.js` is a standalone reimplementation** — no `wallGate`, `hangGate`,
  `openingGate`, host pairs, or phase/level gates. Its numbers do not transfer to the engine.
- **No spec has passed vetting.** Any build against §S27 or §S28 as written is building on a
  rejected design.

## Standing instrument rule (three violations, one class)

`§S25_REVIEW.1` (`engineGap` arithmetically incapable of failing) and TWO false zeros produced on
2026-08-19 — remapped `bz`/`tz` where the judge reads `base_z`/`top_z` (247 vs 0 on the same
schedule), and omitted `s`/`e` so every duration was 0. Both reported 100% correct on 7/7.
**Every number from this lane prints `durOk` and `judgeCanFail`. A green number is worthless until
the instrument has been shown to go red.**

---

---

# §S23-§S25 — ARCHIVED (2026-08-19)

The superseded design trail (§S23 lane hardening · §S24 phase-gate shell · §S24_TRIAGE ·
§S25 layer contract · §S25_PROTO) is moved to
**`prompts/archive/4D_GANTT_TM_REFACTOR_S23-S25_superseded_2026-08-19.md`**, verbatim, ~850 lines.
**None of it is a build target.** §S24_TRIAGE.4's baseline table is reproduced in §S26 where it is
still cited. §S25_REVIEW stays below — it reviewed that trail and produced the one ship-ready
change in this file.

# §S25_REVIEW — outside review of §S25/§S25_PROTO, and what measurement did to it (2026-08-19).
# A Fable-model review was run adversarially against the spec, the prototype and the live engine. Its
# findings are recorded here with the outcome of CHECKING each one — accepted, refuted by experiment,
# or verified wrong. Two of its findings changed the headline of §S25_PROTO; one of its proposed fixes
# was tested and failed; one of its code claims is incorrect. Nothing was taken on the report's word.

## §S25_REVIEW.1 — ACCEPTED, and it is the most important correction in this lane: `engineGap = 0/7`
## is a TAUTOLOGY, not evidence. §S25_PROTO's headline was wrong.

Re-derived by reading the prototype's own code, not the report's summary: `engineGap(effective)`
spans each group by `effGroupOf` — the very membership the pass schedules with. Every element starts
at `max(predFinish, gateTime[effGroup])`; `gateTime[B] ≥ complete(A)`; and `complete(A)` is defined
as `max end over A's effective members` — exactly the quantity `engineGap` compares against. So
`min-start(B) ≥ max-end(A)` is **enforced arithmetic**. It cannot come out non-zero unless the
implementation is broken. It is a useful self-assert, nothing more, and **§S25_PROTO.2 presented it
as the proof that "the gate it enforces is enforced exactly"** — which is the precise failure this
file's own WATCHDOG block names: a proxy standing in for ground-truth validation. Demoted to an
assert; the prototype now prints that caveat inline on every `§S25P_VERDICT` line.

**A second-order consequence the review is also right about:** a re-homed element inherits its new
group's whole gate stack, which physics never demanded (physics demanded only "after my support
finishes"). That inflates the `asLabelled` magnitudes §S25_PROTO then blamed on classification. How
much of the −216d/−768d is gate inheritance rather than physics is NOT yet separated — open.

## §S25_REVIEW.2 — ACCEPTED: the like-for-like comparison existed in the log and was left out of the
## table. Here it is, and it does not flatter the design.

`§S25P_COMPARE` now prints it every run. Same name-based test, same run, all three engines
(negative pairs / worst gap · auditFloating · makespan days):

| building | RAW (local shell) | CPM (live today) | S25 v0 | S25 **+ corrected election (v2)** |
|---|---|---|---|---|
| Terminal | 11/17 −21.5d · **4** · 132.6 | 17/17 −76.0d · 4,756 · 85.1 | 16/17 −96.5d · 3,523 · 103.7 | **11/17** −97.5d · **1,555** · 103.7 |
| Hospital | 5/10 −41.5d · **0** · 333.4 | 10/10 −246.6d · 7,753 · 263.5 | 8/10 −216.8d · 4,095 · 298.1 | **4/10** −71.3d · **1,293** · 300.5 |
| Clinic | 7/10 −14.7d · **0** · 153.4 | 10/10 −85.0d · 1,102 · 96.1 | 10/10 −80.3d · 3,086 · 100.9 | 9/10 −84.2d · **327** · 105.5 |
| LTU_AHouse | 18/20 −112.8d · **25** · 886.1 | 19/20 −747.2d · 12,712 · 748.5 | 17/20 −794.2d · 19,327 · 797.8 | **16/20** −812.5d · **9,461** · 815.3 |
| Duplex | 4/4 −0.4d · **0** · 9.9 | 3/4 −6.3d · 247 · 8.3 | 4/4 −6.0d · 280 · 8.1 | **3/4** −2.5d · **152** · 8.4 |
| HHS | 4/4 −22.0d · **4** · 47.5 | 4/4 −27.4d · 1,531 · 28.8 | 4/4 −22.0d · 1,436 · 45.3 | 4/4 −21.1d · **243** · 45.8 |
| JKR | 14/17 −11.7d · **0** · 38.6 | 16/17 −16.3d · 3,183 · 16.5 | 16/17 −18.5d · 3,078 · 22.8 | **14/17** −24.0d · **1,072** · 28.7 |

Honest reading: **S25 v0 was roughly a wash against CPM and lost to RAW.** The FS makespan growth the
spec predicted is real and was unreported (Terminal 85→104d, Hospital 264→298d). **RAW still wins the
float invariant on 7/7** — its §DEQ_REPAIR loop enforces exactly that predicate — and no variant of
this design has beaten it there.

## §S25_REVIEW.3 — ACCEPTED: four real prototype defects, all fixed

`DUR` ignored `scaleFactor` though §S25.8 specifies it (no effect at scale 1, wrong in production);
a no-storey element with a grouped ancestor was silently lifted into a group it never belonged to,
feeding that group's completion while never counted as re-homed (§S25.7 says it gets no group gate —
now enforced); the contracted-component allocator reused an occupied crew slot with no counter where
`cpm_schedule.js` counts the same event as `drops.crewOverCapScc` (now counted — and it is **0 in the
default configuration**, but **35,705 on Terminal under the bearing-set variant**, which is how that
variant's collapse was caught); and `contradictionDropped: 0` was a dead counter reporting a rule C3
had already deleted — "measured 0 fleet-wide" was vacuous and is removed.

Also accepted, not yet fixed: **Tier-2 phases are ranked alphabetically** (`Finishes` < `MEP Final` <
`MEP Rough-in`), so a Rough-in→Final support classifies as "backward" by name accident. Some fraction
of the deferral population is that artifact. Needs a real Tier-2 order or an explicit "unordered
within Tier-2" rule.

## §S25_REVIEW.4 — ACCEPTED: `midair` and `crew` are structural zeros, not results

`midair 0→0` on 7/7 discriminates nothing: CPM satisfies it by construction (SS edge, never dropped)
and S25 satisfies it by construction (FS edge, always kept or contracted). `crewViol` only flags a
resource after >24h cumulative over-capacity. Of §S25_PROTO.2's four "clean" numbers only
**`leftover = 0`** is genuinely informative — and it stays genuinely informative.

## §S25_REVIEW.5 — REFUTED BY EXPERIMENT, and the refutation is worth more than the proposal:
## §S25.0's central premise is half wrong, and the cycles are intrinsic

The review proposed that C4's all-supports explosion was caused by the *embedded* clause (symmetric —
two coincident boxes each embed the other) and that constraining against exactly the judge's set
(every bearing-below support + the scoped hang set) would be cycle-free and viable. **Tested
(`SUPPORTS=bearing`): it explodes harder.** Largest physics component Terminal **35,217**, Hospital
**38,550**, LTU **62,703**; re-homed 80.7–97.9%; float far worse (Terminal 3,523 → **30,990**);
`crewOverCapScc` 35,705 on Terminal. Not viable.

Isolating why produced the real finding. **`SUPPORTS=bearingonly` (bearing-below edges only, no hang,
no host/opening) gives largest physics component = 1 on every building — zero cycles, zero
contraction, 7/7.** So:

> **Bearing-below alone is a strict order and cannot cycle. Every cycle in this system is created by
> the DOWN-POINTING families — hang, host, opening — whose direction contradicts elevation.**

That corrects §S25.0's premise ("physical support is a strict order by elevation, so it cannot
contain a cycle" — true only of bearing) and it partly vindicates the original CPM design's decision
to contract: any *edge-based* engine holding hang/host constraints must either contract them (which
collapses the schedule — 80-98% re-homed) or drop them (which leaves the float residual). RAW escapes
the dilemma by not using edges for this at all: `geoGate` reads only supports **already placed**, then
`§DEQ_REPAIR` sweeps to a fixpoint. That is the review's untested option (b) — *a placed-support gate
as a NUMBER, not an edge*, which is literally §S25.4's own "gates are numbers, never edges" doctrine
applied to L2. **It remains the one credible route to float→0 and it has still not been tried.**
`bearingonly` itself is dominated (float worse than the corrected election on 6/7) and is kept only
as the instrument that isolated the mechanism.

## ⛔ §S25_REVIEW.6 — CORRECTED 2026-08-19: the numbers below are a MIS-ATTRIBUTED COMPARISON

**The "better on 7/7 versus the live engine" claim is WRONG, and it was the single thing this file
called ship-ready. Verified in `scripts/proto_s25_forward_pass.js`:**

```
608:  const cpmRun = CpmSchedule.run(items, { maxCrews });   <- CPM baseline, computed FIRST
610:  let desOverride = null;                                 <- the v2 election is built AFTER
644:  const s25 = s25Compute(items, maxCrews, desOverride);   <- fed ONLY to the S25 prototype
```

`DESIG=v2` **never reaches `CpmSchedule.run`**. So the table below compares the §S25 PROTOTYPE pass
— its own level ladder, L3/L4 gates, crew allocator and duration model — carrying the v2 election,
against the untouched live engine. **It attributes the prototype's whole architecture to a tie-break
inside one function.**

**What the election tie-break ALONE actually does** (agent build 2026-08-19, branch
`fix/designated-support-election` in `/tmp/wt-desig-election`, both twins patched, port proven
faithful by 0 mismatches across 267,954 elements on all 7 buildings, two independent verifiers):

| building | proto path before→after | witness path before→after |
|---|---|---|
| Terminal | 4,756 → 2,451 | 10,011 → 6,174 |
| Hospital | 7,753 → 6,251 | 8,103 → 6,716 |
| Clinic | 1,102 → 653 | 1,205 → 812 |
| LTU_AHouse | 12,712 → 8,234 | 12,686 → 9,021 |
| Duplex | 247 → 111 | 237 → 65 |
| **HHS_Office** | **1,531 → 1,589 WORSE** | **1,491 → 2,505 WORSE (+68%)** |
| **JKR** | 3,183 → 2,999 | **3,385 → 3,581 WORSE** |

**6/7 (proto path) or 5/7 (witness path) — NOT 7/7.** Magnitudes fall far short of the 1,555 / 1,293
/ 327 promised below. The `-29% to -51%` backward-supports claim DOES hold — it is a pure property
of the election and is independent of the engine it runs in.

**Not shipped. No PR.** The agent hit §S25_REVIEW.9's STOP-AND-REPORT and stopped instead of tuning
the tie-break to chase the target numbers. The faithful port sits uncommitted for inspection.

**⚠ SEPARATE, PRE-EXISTING, AND UNRELATED TO THIS CHANGE: all 7 W-MZ-8 baselines are ALREADY RED on
`main` today** (`witness_midair_zero.js`, run before any edit): Terminal locked 8,789 / got 10,011 ·
Hospital 5,107 / 8,103 · Clinic 3,523 / 1,205 · LTU 15,896 / 12,686 · Duplex 289 / 237 · HHS 1,538 /
1,491 · JKR 3,736 / 3,385. `pass=32 fail=7`. **A locked baseline that no longer matches main is not
a lock.** Something moved all 7 and was never re-locked. Diagnose that BEFORE re-locking anything.

**⚠ AND: there are now THREE float numbers per building depending on the path** — the W-MZ-8 lock,
`proto_s25_forward_pass.js`, and `witness_midair_zero.js`'s own `_buildXrayElements`/
`_displayTimeline` chain (Duplex: 289 / 247 / 237). §S28.R already flagged this. **Every float number
in this lane must name its instrument or it is meaningless.**

The original section follows verbatim as the record of what was claimed.

## §S25_REVIEW.6 — THE WIN: correcting the support ELECTION improves every metric on every building,
## with no change to the engine at all

§S25_PROTO.4 item 1 said fixing `designatedSupport` was the highest-leverage item. Measured, it is.
The corrected election (`DESIG=v2`) keeps the classification exactly as shipped and changes only the
tie-break **between physically equivalent candidates**: at the same class, a structure-pool member
outranks a non-structural one (a pipe 1cm under a wall must not outrank the slab 10cm under it), then
a candidate that does not contradict the phase order outranks one that does.

- **Backward supports −51% to −29%**: Terminal 4,263→3,032 deferrals, Hospital 7,822→6,490, Clinic
  3,539→2,372, JKR 609→299, Duplex 112→66, HHS 1,166→761, LTU 13,460→12,290.
- **Float, versus the live engine: better on 7/7** — Terminal 4,756→**1,555**, Hospital
  7,753→**1,293**, Clinic 1,102→**327**, HHS 1,531→**243**, JKR 3,183→**1,072**, Duplex 247→**152**,
  LTU 12,712→**9,461**.
- **Phase gap, versus the live engine: better or equal on 7/7**, and better than RAW on 3
  (Hospital 4/10 vs 5/10, LTU 16/20 vs 18/20, Duplex 3/4 vs 4/4), equal on 3, worse on 1 (Clinic).

This is the first configuration in this lane that beats the shipped engine on both invariants at
once. It is also the cheapest: a tie-break inside one function.

## §S25_REVIEW.7 — one review claim VERIFIED WRONG, recorded so it is not inherited

The review states `contactGraph`'s carrier clause is a "±GAP = 0.5m band" and concludes rod-hung MEP
0.51-9.5m below its carrier gets no designated support at all. **Incorrect.** `cpm_schedule.js`'s
carrier clause is `S.bz >= T.tz - GAP && S.tz > T.tz + EPS` — a LOWER bound only, uncapped above (the
deliberate §DAY_GAP_TAIL asymmetry, already recorded in this project's memory). The ±GAP band the
review is thinking of is in `auditFloating`'s hang clause (`schedule_gate.js` ~1090), a different
function; the far-carrier case there is covered by §HANG_NEAREST for big elements. The review's
broader point — that S25 constrains against ONE support while `geoGate`/`wallGate`/`hangGate` take the
max over ALL — stands, and is §S25_REVIEW.5's subject.

## §S25_REVIEW.8 — ACCEPTED and still open (not fixed today, named so they are not lost)

- **Within-level trade order is deleted with no replacement.** `phaseTrade` (`schedule_gate.js`
  885-887) and per-trade `bandGate` die under §S25.9, and L3 orders Tier-2 only *after* Tier-1 — so
  MEP Final may run before MEP Rough-in, furniture before ductwork, on every level. §S25.4 must gain
  an intra-Tier-2 order (and see §S25_REVIEW.3's alphabetical artifact — same root).
- **§S25_PROTO.2's "no Tarjan… the machinery is unnecessary" is false as written.** Tarjan, SCC
  contraction, a condensation and a topological longest-path pass are all present — only the
  *merged-graph* scope is gone. The claim must be narrowed to that.
- **§S25.10's acceptance baselines are stale** (W-MZ-8's 8,789/5,107/15,896 came from the
  `_extracted.db` files T8 already flagged; the served-DB measurement is 4,756/7,753/12,712).
- **§S25P_BULK failed on 2/7** (Clinic 1/7 −10.61d, LTU 3/17 −672d) and §S25_PROTO reported no
  failure. Still needs guid-level attribution before the ladder-vs-name explanation is accepted.
- **Hospital's ladder places "Level 5" in four rows and merges `Level 5=Level 3=Level 6`** on a DB
  whose #1427/#1428 elevation patch is still unverified — the −216d Hospital figure may be
  substantially ladder/DB artifact.
- **Gantt-drag is still not a design** ("re-run with the element pinned" is undefined against gates,
  and bars are keyed on names the ladder splits/merges), and the `stragglerOf`/cache return contract
  in `_displayTimeline` still needs porting. Leftover elements currently return `{s:0,e:0}` — day-0
  placement, the exact §4D_NOGEO poison — which must become an explicit consumer contract.
- **Stability is unmeasured:** one added element can move a level's median, re-rank bands and cascade
  re-homing through thousands of elements, against caches keyed by `_GANTT_CACHE_VERSION`.

## §S25_REVIEW.9 — the next move, unchanged in shape but now decided by data

1. **Ship the corrected election first** (§S25_REVIEW.6). It is a tie-break in `designatedSupport`,
   it improves the LIVE engine's own float on 7/7 without any of §S25 being built, and it shrinks the
   population every later stage has to reason about. It needs its own witness (the election is a
   behavioural change to a function three consumers share) — that is the next commit, not a rewrite.
2. **Then test the placed-support NUMERIC gate** (§S25_REVIEW.5's option (b)) — the only untried
   route to float→0, and doctrinally the one §S25.4 already prescribes.
3. **Only then** consider wiring §S25 (§S25.11 S25-4), and not before §S25_REVIEW.1's gate-inheritance
   question is separated from the classification claim.

**STOP-AND-REPORT, inline:** if the corrected election is implemented in `designatedSupport` and the
live engine's float does NOT move as §S25_REVIEW.6 measured, the prototype's election differs from
the shipped one in some way not yet identified — report that difference, do not tune the tie-break
until the numbers agree.

---
# §S26 — THE SCHEDULE IS TRAINS, NOT A GRAPH (2026-08-19, measured)

**Standing:** proposed. Supersedes NOTHING yet — §S25 and §S25_REVIEW stay as written, and the
`designatedSupport` election win in §S25_REVIEW.6 is orthogonal to this and should ship on its own
merits. What §S26 challenges is not any fix in this file; it is the assumption every design in this
file has shared since §S23 — that element-to-element physical precedence is the right abstraction
for a construction schedule.

**Provenance, stated because it matters for how much weight this carries:** §S26 came out of a
2026-08-19 working conversation with the user, not from a spec review. Two of its load-bearing
claims are the USER's, not this session's, and both were then measured and held (§S26.3, §S26.6).
Every number below is from `scripts/probe_s26_rank_monotone.js`, written this session, run on the
same 7-building fleet as `proto_s25_forward_pass.js`, reading the same `contactGraph`/`hostPairs`/
`openingPairs` predicates so the numbers are directly comparable. **Nothing in `viewer/` was
changed.** Probe logs: `§S26G_*`.

## §S26.0 — the architecture changed on a known date, and the date is checkable

`viewer/cpm_schedule.js` is 3 days old (`a5de597`, 2026-08-16). The graph it consumes is not.
Ordering became a physics graph on **2026-08-07, PR #1242 `0fe8eb2`** — "placement order derived
from geometry DAG, seq demoted to tiebreak".

Before that commit, `schedule_gate.js` ordered by two plain sorts and used physics only as a clock:

```js
// 0fe8eb2^ viewer/schedule_gate.js:248-249, 268
var struct = elements.filter(e => e.seq <= 4).sort((a,b) => (a.base_z-b.base_z) || (a.seq-b.seq));
...
var start = Math.max(geoGate(el), slot.time);
```

A sort cannot cycle. A `max()` over already-placed elements cannot cycle. Three days after #1242,
PR #1276 landed `§TM_GEO_ORDER_CYCLES — Terminal support-DAG cycles 37,927→0`. Every design in this
file from §S23 onward is downstream of that one commit.

**This is not an argument that #1242 was wrong.** It closed a real defect (`nothing floats` as a
structural property rather than a per-building patch chase, and the commit message says so). It is
an argument that the cost has never been priced, and §S26.1-§S26.4 price it.

## §S26.1 — VERIFIED: the 93% blob is manufactured, and by which edges exactly

`probe_s26_rank_monotone.js` builds the required-precedence set from the same predicates the engine
uses, then measures its largest strongly-connected component. Two configurations, same fleet, same
run:

| building | n | live predicate | corrected predicate | ratio |
|---|---|---|---|---|
| Hospital_meta | 63,182 | **49,436** | 1,951 | 25× |
| LTU_AHouse_meta | 122,330 | **74,617** | 1,460 | 51× |
| Terminal_meta | 48,428 | **28,802** | 1,358 | 21× |
| Clinic_meta | 16,071 | **8,340** | 768 | 11× |
| JKR_extracted | — | **5,362** | 1,034 | 5× |
| HHS_Office_Federated | 6,839 | **4,628** | 1,742 | 2.7× |
| Duplex_extracted | 1,119 | **672** | 5 | 134× |

`corrected` = `STRUCT=1 EMBED=0`: a support must be a load-bearing class (the existing `seq<=4` +
promoted-slab pool), and the `embedded` family is dropped. The live largest-component share
reproduces §S24_TRIAGE's independently-measured fleet numbers (Hospital 98%, Terminal 93%, LTU 90%)
and §S25_REVIEW's `largest physics component 672` on Duplex — three separate probes agreeing.

**The blob is not a property of the building. It is a property of the predicate.**

## §S26.2 — VERIFIED: the predicate counts plumbing as structure

The live support test is geometric only — any lower box in contact is a support. Measured on Duplex:

- support = anything below → **4,706** bearing relations
- support = load-bearing classes → **702** (6.7× inflation)

Actual offending pairs, from `§S26G_EG`:

```
IfcFlowSegment @ -0.7m / MEP Rough-in   "supports"  IfcWallStandardCase @ 0.1m / Architecture
IfcFlowFitting @  2.6m / MEP Rough-in   "supports"  IfcWall             @ 3.1m / Architecture
```

Consequence — direct physics-vs-phase contradictions on Duplex, `§S26G_CAUSE`:

- support = anything below → **761**
- support = load-bearing classes → **1**

760 of 761 apparent conflicts between physics and phase order are the engine insisting a pipe must
be installed before the wall above it. **There is no physics-vs-convention war in this data.** There
is a predicate that calls plumbing structure, and a solver dutifully resolving the contradiction by
dragging MEP forward — which is the phase scramble this whole lane has been chasing.

## §S26.3 — VERIFIED: the `hang` family is redundant, and it is the sole remaining cycle source

**This claim is the USER's** ("this rule is redundant — when a ceiling is complete it no longer
arises"), recorded here because it was given more than once before it was measured.

Isolation, `STRUCT=1 EMBED=0`, `§S26G_ACYCLIC`:

| configuration | Clinic | Duplex | HHS |
|---|---|---|---|
| drop `hang`, keep bearing+host+opening | **acyclic, largest = 1** | **acyclic, largest = 1** | **acyclic, largest = 1** |
| keep `hang`, drop host+opening | 768 | 5 | 1,742 |

Every remaining cycle lives in `hang`. Host and opening are harmless.

And `hang` contributes no ordering that phase order does not already give — `§S26G_HANGREDUNDANT`,
share of hang edges where the carrier is already an earlier phase than the hanger:

| Hospital | LTU | Duplex | JKR | HHS | Clinic | Terminal |
|---|---|---|---|---|---|---|
| **99.8%** | 98.3% | 98.5% | 97.5% | 97.4% | 97.2% | 93.5% |

The residue is not hanging at all. Top same-phase pairs, `§S26G_HANGSAME`, all inside Superstructure:
`IfcSlab carries IfcBeam` ×153 (Clinic) ×91 (Hospital) · `IfcBeam carries IfcColumn` ×148 (LTU) ·
`IfcPlate carries IfcPlate` ×605 (Terminal). Phase-inverted residue, `§S26G_HANGINV`:
`IfcSlab/Superstructure carries IfcFooting/Substructure`.

A slab does not carry a beam; a beam does not carry a column; a slab does not carry a footing. **Not
one element in the residue is a light, a duct, or a fixture** — nothing in it is actually hanging.
The rule fires 8,026–49,199 times per building, is 93–99.8% redundant, and its non-redundant part is
the same relation with the direction reversed.

**Not chased today, and deliberately:** whether the reversal is a bbox-direction defect in the
predicate or genuinely mis-elevated geometry in the source models. The user ruled it not worth the
contention given the family is being deleted either way. Recorded so it is not rediscovered.

## §S26.4 — the design, stated in the user's terms rather than the graph's

Real programmes are not a dependency web. They are a small number of **trains**, each running level
by level, offset from each other:

```
Level 8   structure
Level 6              walls
Level 4                        MEP first fix
Level 2                                       finishes
```

Precedence lives at **(level, trade)**, never element-to-element. Physics is not the order; it is a
check run on the answer.

Two consequences that came out of the conversation and are design inputs, not observations:

**(a) The structural train's shape is a template, not a constant.** USER: *"beams and columns may all
come up all stories before even floor slabs come in."* That is steel-frame erection — frame runs
several floors ahead, deck and slabs follow. In-situ concrete does the opposite: a storey completes
before the one above starts. Both are common. **Default = in-situ concrete, floor by floor** (the
simpler shape and the common case for the DIY/small-firm long tail this product targets); steel-frame
is template two. A template is a JSON document — trains, order, offsets — editable by a planner
without a rebuild, which is the stated product requirement: *"as long as the resulting JSON is easily
editable by real experts… it can be crude, but they easily fill in the gap or readjust or simply
import their model."*

**(b) The mid-air judge is template-dependent, and today it is not.** Under a steel template a
level-5 column legitimately starts before the level-4 slab exists — it is bolted to the column
below, not resting on the slab. Today's judge would call that a defect and "fix" it. **A hard-coded
assumption about how buildings go up, applied to buildings that do not go up that way, is a latent
defect independent of everything else in this file.**

**Cast-in MEP is not MEP.** Conduit, sleeves and box-outs precede the pour but belong to the concrete
activity ("Slab L4 — rebar, conduit & box-outs, pour"), one bar owned by the structures contractor.
Wall first-fix sits inside or immediately after the wall activity. Neither is a separate trade item,
and neither ever gates the storey above. This is general construction practice, NOT extracted from
project data — flagged as such so it is checked against a real planner before it is built on.

## §S26.5 — HONEST RESULT: the strong form of §S26 is FALSE

The claim worth testing was: *a single integer rank R(e), computed from data alone, satisfies
R(S) < R(E) for every required relation* — which would make ordering a sort, with no graph at all.

**It does not hold.** `§S26G_VERDICT zeroViolation=0/7` in every configuration tried:

| configuration | violations | largest SCC (worst building) |
|---|---|---|
| live predicate | 27–39% | 74,617 |
| `STRUCT=1 EMBED=0` | 3–36% | 1,951 |
| `+ HANG=one` (single elected carrier) | 2.8–12.1% | 504 |
| `+ HANG=0` (family deleted) | 1.65–7.36% | **1 — acyclic on all 7** |

Two things follow, and they must not be merged:

1. **Deleting `hang` reaches acyclic on the FULL fleet** — `§S26G_ACYCLIC … kahnLeftover=0
   largestSCC=1` on all seven, Hospital 63,182 and LTU_AHouse 122,330 included. This is the §S26.8
   stage-1 stop condition and it is **MET**, not projected. The 49,436-element Hospital component
   and the 74,617-element LTU component do not shrink — they cease to exist.
2. **Even acyclic, 1.65–7.36% of relations still point backwards in rank.** Per building
   (`§S26G_VIOL`, `STRUCT=1 EMBED=0 HANG=0`):

   | building | total | bearing | host | opening |
   |---|---|---|---|---|
   | JKR_extracted | 341/20,686 (1.65%) | 86/19,921 | 59/506 | 196/259 |
   | Terminal_meta | 1,050/50,114 (2.10%) | 178/47,213 | 556/2,099 | 316/802 |
   | HHS_Office_Federated | 305/10,252 (2.98%) | 3/9,241 | 222/725 | 80/286 |
   | LTU_AHouse_meta | 124,124/3,933,718 (3.16%) | 120,876/3,925,180 | 1,865/5,466 | 1,383/3,072 |
   | Hospital_meta | 1,529/37,261 (4.10%) | 155/33,495 | 1,123/2,830 | 251/936 |
   | Duplex_extracted | 51/850 (6.00%) | 30/702 | 9/104 | 12/44 |
   | Clinic_meta | 834/11,325 (7.36%) | 261/8,189 | 484/2,737 | 89/399 |

   A sort alone therefore still leaves real backward relations, concentrated in `host` (Clinic 18%,
   HHS 31%, Hospital 40%) and `opening` (JKR 76%). They are *countable and nameable* rather than
   *silent*, which is the whole point of §S26.6, but they are not zero and §S26 must not claim they
   are. **LTU is the outlier and it is a density artefact, not a rate one** — its 120,876 bearing
   violations are 3.1% of 3.9M edges, see §S26.7's 32-supports-per-element flag.

## §S26.6 — WHY THE OLD DEFAULT FAILED, and whether it bites again

**This is the USER's question and it is the most important one in the section**, because §S26.4's
train model IS roughly what the pre-#1242 engine did. If it was abandoned once it can be abandoned
again.

The reason is written in the code at the exact place it was abandoned — `0fe8eb2^
schedule_gate.js:252-262`:

> *"band-gate WITH re-sorting by rank: inversions → 0, but **2,341 elements FLOAT again** (beams
> 15/1970, members 2304/7127, slabs 22/35). geoGate reads `grid`, which holds only what is already
> placed, so re-ordering PASS A places elements before their own supports."*

**The mechanism, exactly:** the *gate* scanned a partial grid containing only already-placed
elements. Reorder, and the support is simply not there to be seen. The gate returns `baseMs`, the
element starts at time zero, and **nothing reports it**. Not an exception, not a cycle, not a
warning — a silent zero.

Three named causes, each with a guard, because the shape of the fix decides whether it recurs:

**C1 — the gate was blind by construction; the judge never was.** `auditFloating(elements, sched, …)`
at `schedule_gate.js:1055-1059` builds its grids from **all** `elements`, not the partial placement
grid. The judge was always global and correct. Only the gate was partial. → **Guard: the gate must
use the judge's scan.** With rank computed up front rather than discovered incrementally, elements
process in rank order and supports are already placed by construction — for the 93–97% that are
rank-monotone. For the 3.0–7.4% that are not (§S26.5), the backward relation is **reported and
counted**, never silently skipped. That single change is the difference between the old default and
a safe return to it.

**C2 — the rank came from storey labels, and labels are junk.** Measured, `§S26G_LADDER`-equivalent
from the S25 probe: Clinic's `"Roof - Main"` occupies **three distinct elevations** (ranks 2, 3 and
4); LTU merges `VÅN 3=VÅN 4=VÅNING 4=Ref.` at −0.6m, and puts 44,383 of 122,330 elements (36%) in a
single group named `Plan 1`. → **Guard: rank comes from elevation bands only** (`§S1_BAND_RANK`'s 3m
bands, already shipped and already used by two other consumers). A storey label is a display name.

**C3 — the reaction changed the architecture instead of the verifier.** #1242 made floats
*structurally impossible* by making physics the order. That traded a silent failure for a structural
one: floats → 0, cycles → 37,927. → **Guard: a check that cannot fail is not a check.** §S25_REVIEW
caught this session's own instance of the same error — `engineGap = 0/7` published as proof when
`min-start(B) ≥ gateTime(B) ≥ complete(A) = max-end(A)` is enforced arithmetic. Same class, three
weeks apart, and it is the most likely thing in this file to recur.

## §S26.7 — what §S26 does NOT solve

Stated in full, because §S25.12's version of this list was incomplete and that was a review finding:

- **Crew levelling, durations, makespan realism, and whether a planner would call the output
  sensible.** Untouched. §S26 addresses ordering and mid-air only.
- **The 3.0–7.4% residual backward relations** (§S26.5), concentrated in `host`. Made visible, not
  eliminated.
- **The `host`/`opening` families' high violation rates** — host: Clinic 18%, HHS 31%, Hospital 40%;
  opening: JKR 76% (196/259) — undiagnosed. These are the largest remaining unknown in §S26 and the
  most likely place its residual 1.65–7.36% is hiding something structural rather than incidental.
- **LTU generates 3,925,180 bearing relations for 122,330 elements — 32 per element**, versus ~1 per
  element on Terminal (47,213/48,428). A 30× density outlier, cause unknown, flagged and not chased.
  Any implementation must expect it as a perf ceiling.
- **Template shapes beyond in-situ concrete and steel frame** — precast, tilt-up, timber — unexamined.
- **The construction-practice claims in §S26.4 are general knowledge, not project data**, and have
  not been checked with a real planner.
- **Nothing here is implemented.** `viewer/` is unchanged; `probe_s26_rank_monotone.js` is a probe.

## §S26.8 — build order, each stage with a stop condition that does not depend on a later stage

1. **Delete `hang`.** Stop condition: `§S26G_ACYCLIC` reports `largestSCC=1` on all **7** buildings.
   **✅ MET 2026-08-19** — `kahnLeftover=0 largestSCC=1` fleet-wide (§S26.5). Independently
   checkable — a property of the edge set, needing no scheduler to exist.
2. **Restrict `support` to load-bearing classes; drop `embedded`.** Stop condition: physics-vs-phase
   contradictions ≤ 1 per building (`§S26G_CAUSE` `phase=`). Also pure edge-set, no scheduler.
3. **Rank from elevation bands + phase.** Stop condition: backward-relation count reported per
   building and matching §S26.5's table to within noise. Pure data, no scheduler.
4. **Sort + `max()` clock, gate using the judge's global scan (C1).** Stop condition: `auditFloating`
   float count vs the live engine, on the same fleet — must not regress. First stage requiring a
   scheduler, and its check is the shipped judge, not a new one.
5. **Template file + offsets.** Stop condition: changing an offset in JSON changes the schedule and
   nothing else; the default reproduces stage 4's numbers exactly.
6. **Template-dependent mid-air judge (§S26.4b).** Stop condition: a steel template does not report
   a level-5 column as mid-air, and the concrete template still does when it genuinely is.

Stages 1–3 are edge-set properties measurable with the probe that already exists. **No stage depends
on a later stage to validate itself** — the specific defect §S25.11 was reviewed for.

## §S26.9 — STOP-AND-REPORT

- **Stage 1 is MET fleet-wide (§S26.5) — but on the PROBE's edge set, not the engine's.** If the
  engine reaches a different result after `hang` is removed for real, the engine's predicate differs
  from `probe_s26_rank_monotone.js`'s in some way not yet identified. Report that difference and
  STOP — do not delete a second family to chase it.
- **If the residual backward-relation count in stage 3 exceeds §S26.5's measured 3.0–7.4% band on any
  building**, the rank construction differs from this probe's. Report the difference; do not tune.
- **If any stage reports a `0` that cannot fail** (C3), treat it as unmeasured and say so in the same
  breath as reporting it. §S25_REVIEW's `engineGap` demotion is the precedent.
- **Before stage 5 is designed**, the §S26.4 construction-practice claims need one pass by someone
  with real planning experience. They are general knowledge, they are load-bearing for the template
  shape, and this session cannot verify them from project data.

## §S26.10 — the field already names all of this, and we never looked (2026-08-19)

USER, on being shown that the three arrow rules converge on location-based planning: *"It is
perplexing why we been doing without even a peek at what is out there."* Researched; the answer is
that every part of §S26.4 is established practice with a literature and commercial implementations.

- **LBMS (Location-Based Management System)** and **Takt planning** ARE the train model. LBMS is
  described as "the combination of location-based methods and a slightly modified Critical Path
  Method" — a location breakdown structure carrying location-based logic on top of CPM. Takt fixes
  the duration per location; LBMS keeps a crew moving with buffers.
  ([LBMS workflow/resource study](https://www.tandfonline.com/doi/full/10.1080/01446193.2017.1410561) ·
  [Takt vs LBMS comparison](https://iglcstorage.blob.core.windows.net/papers/attachment-bab39cab-949b-40a3-b03c-a1fd05b200f5.pdf))
- **Rule-based spatial reasoning from topology exists** — but never as bare geometric contact. It is
  always topology PLUS "structural construction, material layers and work access, some common
  construction practices and hierarchical relationships between the building entities."
  ([Automated Generation of 4D BIM through Spatial Reasoning](https://ascelibrary.org/doi/10.1061/9780784412329.062))
- **The reported limitation of the geometry-only approach is our exact bug:** approaches that stay
  "predominantly quantity- and geometry-driven… consequently require substantial expert intervention
  to define the breakdown and/or precedence relations at an actionable level."
  ([BIM–NLP framework survey](https://www.mdpi.com/2411-9660/10/2/43))
- **Commercial tools multiply recipes, not links.** ALICE "ingests the scope of the project, rules
  about how that scope gets built, and multiplies the rules across the scope."
  ([ALICE Model](https://www.alicetechnologies.com/alice-model) · [BEXEL Manager scheduling](https://bexelmanager.com/bexel-manager/scheduling/))
- **On cycles the field does the OPPOSITE of this engine:** "a graph-based cycle check is applied so
  any candidate link that would create a cycle in the precedence network is REJECTED. Self-loop
  relations are removed, and reverse duplicate links are avoided." Rejected at creation — the cycle
  never exists. `cpm_schedule.js` builds every link, then runs Tarjan and contracts what it finds,
  by which point the good links are inside the knot with the bad ones.

**Fourth practical rule, and the cheapest of the four: refuse a link that would close a cycle,
instead of creating it and breaking it later.**

## §S26.11 — the design restated top-down (supersedes §S26.4's phrasing, not its content)

§S26.4 arrived at the right shape from the wrong end — filtering a web down with boundary clauses.
Stated the way the field states it, there is no web:

1. **The unit of scheduling is a cell: one LOCATION × one TRADE.** Every element belongs to exactly
   one. "Level 3 × Superstructure" is a cell.
2. **Order is two ordered lists, not a graph.** Trades in fixed order within a location; locations
   bottom to top. Nothing to solve, nothing to untangle.
3. **Flow is the tuned parameter** — how far the structure train runs ahead of the walls train.
   LBMS vs Takt is exactly the choice of how that offset behaves.
4. **Arrows are the EXCEPTION**, added by a person where the grid genuinely gets it wrong (transfer
   beam, long-span truss, temporary works). A handful per building, not 2.46 million derived.
5. **A link that would close a cycle is refused, never created** (§S26.10).

§S26.4's three rules then stop being rules and become CONSEQUENCES: *same band* is free because a
cell IS a location; *forward in trade* is free because trades are an ordered list inside a cell;
*one arrow per element* is moot because elements carry no arrows. **The boundaries were being
invented to tame a structure that should not exist.**

Cost, stated plainly: the grid needs locations that are real. That becomes the ONE hard input —
see §S26.12.1, which is also where the answer already sits.

## §S26.12 — VERIFIED: four things the field solved that this repo re-derived or dropped

**1. The declared locations are already in the shipped DB, and the scheduler ignores them.**
`Hospital_meta.db` carries `spatial_structure` (212 rows — `IfcBuildingStorey` with its own z:
`Level 1` @ 168.73, `Level 2` @ 174.06), `rel_contained_in_space` (**8,474** element→room rows,
space guids like `RM_Level_2_25`), and `rel_aggregates` (9,528 declared parent→child). The scheduler
instead parses storey NAME strings through `collapsePhase()` and buckets `base_z` into 3m slabs.
**Rooms are what LBMS calls a location.** The junk-label problem (§S26.6 C2) is not a parsing
problem — the declared answer is in the file and unread.

**2. The host relation is declared in IFC, parsed on import, and dropped before the scheduler.**
`import_worker.js:315` reads `IfcRelVoidsElement` (wall→opening); `:328` reads `IfcRelFillsElement`
(opening→door). The shipped DBs carry neither — `schedule_gate.js:92` records this honestly ("no
IfcRelVoidsElement/host column exists in the shipped extracted DBs… so the host is inferred
geometrically"). Measured cost of that inference (§S26.5): host guesses point BACKWARDS 18% on
Clinic, 31% on HHS, 40% on Hospital; opening guesses 76% on JKR (196/259). **Not a predicate to
tune — a column that stops at the extractor.**

**3. Classification is hand-rolled** — `viewer/rates/sequence_rules.json` maps IFC class → trade by
hand, where Uniclass 2015 / OmniClass Table 22 / MasterFormat publish work sections with IFC
mappings. NOTE THE CONTRAST, because it is the diagnostic: `viewer/rates/` holds **17 national cost
libraries** (BCIS UK, RSMeans US, CIDB MY, Rawlinsons AU, SINAPI BR, GB50500 CN, KICT KR, …). The
cost domain was researched properly. The sequencing domain was not. Same repo, same author. Cost
*felt* like a domain with authorities; sequencing felt like a maths problem. It is not.

**4. IFC's native schedule schema — and this one is NOT a gap. See §S26.13.**

## §S26.13 — THE CONTAINER ALREADY EXISTS, WAS BUILT BY THE USER, AND IS EMPTY

USER: *"we just infuse them with such schedule format data rather than create another 'container'
framework."* Correct — and the container is already shipped, already declared source of truth, and
carrying zero rows.

**Is IFC's schedule schema usually empty in the wild? Measured on the actual source models:**

| model | IfcTask | IfcWorkSchedule | IfcRelSequence |
|---|---|---|---|
| Clinic · TerminalMerged · LTU_AHouse_AIR · Duplex_ARC · jkrST25 | 0 | 0 | 0 |
| **Hospital 2.0.ifc** | **121** | **1** | **43** |

Five of six carry nothing — so yes, usually empty. But the sixth carries a real planner's programme:
`IFCWORKPLAN 'Construction Programme'` · `IFCWORKSCHEDULE 'Baseline Schedule' .PLANNED.` ·
tasks `Structures` / `Piles` / `Zone A` / `Zone B` / `Zone C` · `IFCTASKTIME 'P15D'`
2026-05-16→2026-05-30 · 43 × `IFCRELSEQUENCE … .FINISH_START.` chaining Zone A→B→C.
**121 tasks for a 63,182-element building — the train model, authored by hand, at exactly the grain
the field uses, sitting inside this project's own test model.**

**Has this project ever used it? Yes — the user built it, in the Find Panel period.**
- `2253664` **2026-05-30, PR #59**, Redhuan D. Oon — "capture native IFC 4D in Drop-IFC importer
  (T1+T1b, widened schema)": walks `IfcWorkSchedule`/`IfcTask`(+`IfcTaskTime`)/`IfcRelSequence`,
  dual-direction task↔element links (`IfcRelAssignsToProcess` + `IfcRelAssignsToProduct` — "the
  Bonsai/Hospital fix that yields the 2900 links"), WBS via `IfcRelNests`, `IfcWorkCalendar`,
  "ISO-8601 durations/floats kept VERBATIM (PRIME RULE — no re-derivation)". Ported from
  bim-compiler T1+T1b, so the design predates the viewer.
- `b195103` **2026-06-23, PR #502** — "respect IFC schedules crafted in Bonsai/Revit (adopt, don't
  clobber)", logged as "User point (2026-06-23)".
- `schedule_author.js:6` states it outright: **"SOURCE OF TRUTH = the IFC-native tables
  `schedules`/`tasks`/`task_elements` ONLY"**.
- Dating the era: Find panel `§S275` landed `4352b04`/`58318b9` **2026-05-24**; find multi-select
  `#53` **2026-05-29**; the IFC 4D capture `#59` the NEXT DAY, **2026-05-30**. Same week, same burst.

**And every shipped building DB has the tables empty:**

```
Hospital_meta    schedules=0  tasks=0  task_sequences=0  task_elements=0
Clinic_meta      schedules=0  tasks=0  task_sequences=0  task_elements=0
Duplex_extracted schedules=0  tasks=0  task_sequences=0  task_elements=0
JKR_extracted    schedules=0  tasks=0  task_sequences=0  task_elements=0
Terminal_meta    (the tables do not exist at all)
```

Hospital's source IFC has the programme; `Hospital_meta.db` has zero rows. **The extraction pipeline
discards the planner's schedule.**

**The consequence, and it closes §S26's loop:** `task_sequences` is the `IfcRelSequence` mirror — the
declared home for dependencies. It holds ZERO rows while the engine derives 2.46 million arrows in
memory on every load. That is why the blob went unseen for weeks (the links were never written
anywhere anyone could look), and why the user's product requirement — *"the resulting JSON is easily
editable by real experts… they easily fill in the gap or readjust or simply import their model"* —
cannot be met today: **there is nothing to edit.**

**No new framework is needed.** Fill the tables already declared as source of truth, and the arrows
become rows a planner can see, sort, delete, or replace.

## §S26.14 — MEASURED: the §S26.3 change applied to the LIVE engine (branch, no PR)

Branch `fix/s26-drop-carrier-ordering` (bim-ootb), off `6a395ca`. One-line change in BOTH twins —
`cpm_schedule.js:138` and `time_machine.js:4593` — `designatedSupport` never elects a carrier-above.
`schedule_gate.js hangGate()` (the start-time CLOCK) deliberately untouched, so a genuine rod-hung
element still waits for its carrier. Both twins together because `witness_midair_zero.js:127` and
`probe_captured_floating.js` slice `_designatedSupport` out of `time_machine.js` BY SOURCE TEXT — a
`cpm_schedule`-only edit leaves those witnesses green on code that never ran.

| building | physics SCCs contracted | CPM float | CPM midair |
|---|---|---|---|
| Terminal_meta | 1,291 → **370** | 4,756 → 4,216 | 0 → 0 |
| Hospital_meta | 1,063 → **682** | 7,753 → 8,475 ⚠ | 0 → 0 |
| Clinic_meta | 748 → **662** | 1,102 → 991 | 0 → 0 |
| LTU_AHouse_meta | 5,983 → **4,917** | 12,712 → 11,663 | 0 → 0 |
| Duplex_extracted | 37 → **32** | 247 → 178 | 0 → 0 |
| HHS_Office_Federated | 289 → **130** | 1,531 → 1,279 | 0 → 0 |
| JKR_extracted | 141 → **93** | 3,183 → 3,410 ⚠ | 0 → 0 |

Phase `negGap`: better on 2, worse on 1, unchanged on 4 — **not fixed by this change.**

**HONEST NEGATIVE, and §S26.9's STOP-AND-REPORT was honoured rather than chased:** this does NOT
reproduce the probe's acyclic 7/7. The probe needed THREE changes (drop hang, restrict support to
load-bearing classes, drop `embedded`); this branch makes ONE. Reported, stopped, no second family
deleted to close the gap.

## §S26.15 — STOP-AND-REPORT, added

- **Before any grid/location work, confirm `rel_contained_in_space` coverage per building.** Hospital
  has 8,474 rows for 63,182 elements — **13%**. If room containment covers only a small minority
  fleet-wide, locations must fall back to storeys and §S26.11's cell grid is coarser than LBMS
  assumes. Measure before designing on it.
- **Do NOT "fix" the empty `tasks`/`task_sequences` tables by generating rows from the current
  engine.** That would persist the 2.46-million-arrow web into the IFC-native container and make the
  blob permanent instead of ephemeral. The tables are for a summary programme at Hospital's grain
  (121 tasks), not for element-level physics.
- **Terminal_meta.db lacks the 4D tables entirely** while the other six have them empty — two
  different extraction vintages in one shipped fleet. Establish which is current before either is
  treated as the schema.

## §S26.16 — Room Path reuse: the right machine, the wrong output grain (cursory check, 2026-08-19)

USER: *"The Room Path effort also can be refactored for reuse here."* Checked. The instinct is
right and the reuse is real, but not as a drop-in — the numbers say why.

**What it already is.** `bim-compiler/scripts/compile_rooms.py` and its verbatim JS port
`build/room_walker.js` (also shipped at `bim-ootb/viewer/lib/room_walker.js`) rasterize each
storey's wall/door/column/window footprint at `RES=0.20m`, flood-fill the exterior from the border,
and treat every pocket the exterior cannot reach as a room. **Its output is exactly the two tables
§S26.11 needs**: `spatial_structure` IfcSpace rows and `rel_contained_in_space`. It is also the
answer to a question §S26.12.1 left open — Hospital's 8,474 containment rows are COMPILED by this,
not read from the IFC.

It is hardened, not a sketch: `§RASTER-EPS` (1e-6 cell-fraction snap — translation invariance,
measured 8/14 translations previously flipped Terminal's compile), `§STAIR-EXCLUDE` (a stairwell is
circulation, not a room), `§SUSPECT-LARGE` (a fixed area drop threshold was silently eating HHS
Level 3's real 456 m² corridor and causing 73/70 room-graph dead-ends — now flags instead of drops).
That is the same measure-then-fix discipline this file runs on.

**Why it cannot be the location layer as-is — measured coverage:**

| building | elements | storey rows | IfcSpace | contained | coverage |
|---|---|---|---|---|---|
| Hospital_meta | 63,415 | 63 | 142 | 8,474 | **13%** |
| Clinic_meta | 16,114 | 3 | 118 | 2,133 | **13%** |
| Terminal_meta | 48,428 | 73 | 73 | 1,009 | **2%** |
| JKR_extracted | 9,410 | 4 | 79 | 107 | **1%** |
| HHS_Office_Federated | 6,880 | 3 | 14 | 88 | **1%** |
| LTU_AHouse_meta | 125,698 | 38 | 0 | 0 | **0%** |
| Duplex_extracted | 1,193 | — | — | — | tables absent |

**The cause is structural, not a bug: a room is a void bounded by fabric, so the fabric is never in
it.** `rel_contained_in_space` assigns "elements whose XY centre falls in a room" — a wall's centre
is inside the wall, a slab spans every room at once, a column sits in a wall line. Structure is what
makes rooms, so structure has no room. Rooms cover a building's CONTENTS; a schedule must place its
FABRIC. 87-100% of elements are unplaced.

**Second caveat, and it is the §S26.6 C2 problem appearing in the declared data too:** Hospital's
`spatial_structure` reports **63 storeys**, Terminal **73**, LTU **38**. Those are federated
pseudo-storeys. Declared containment is not automatically clean containment — it needs the same
band-merge treatment storey names do.

**The reuse that IS sound — one rasterizer, two consumers.** The valuable asset is not the room
list; it is the per-storey occupancy raster that produces it (already persisted for two buildings as
`storey_walkable_raster`). Partition that same raster into contiguous **ZONES** and every element on
the storey — fabric included — falls in one.

Zones, not rooms, are the LBMS location for structure, and this project already has the proof in its
own test data: Hospital 2.0.ifc's hand-authored programme (§S26.13) uses tasks named **`Zone A` /
`Zone B` / `Zone C`**, chained `.FINISH_START.`. A human planner, on this exact building, chose
storey-zones as the location grain. Rooms stay the right grain for the trades that work inside them
(architecture, MEP, finishes) — a second, finer level of the same breakdown structure, which is what
a location breakdown structure is for.

**STOP-AND-REPORT:** do NOT fold room compilation into the scheduler before zone coverage is
measured the same way this table measures room coverage. The failure mode is identical — a location
layer that covers a minority of elements silently sends the majority to a default bucket, which is
how the storey-name path failed in the first place.

---
# §S27 — THE GRID: implementable spec (2026-08-19)

**Standing: PROPOSED, NOT VETTED.** Written to be reviewed and torn apart before any build agent is
dispatched — user directive 2026-08-19: *"do not do so until the specs are fully vetted."*
No build work may start against this section until §S27.R records a review verdict.

**Supersedes:** nothing. §S25/§S25_REVIEW stand; §S25_REVIEW.6's `designatedSupport` election win is
independent and should ship on its own merits regardless of §S27's fate. §S26 is the evidence base
this spec sits on — every measurement §S27 relies on is cited there with its `§`-tag, and §S27
introduces NO new measurement of its own.

## §S27.0 — the one-sentence claim, stated so it can be falsified

> A construction schedule is a **grid of (location × trade) cells** whose order comes from two
> ordered lists, not from a derived graph; physics is a post-hoc check on the answer, not an input
> to it; and the result is written into the IFC-native `schedules`/`tasks`/`task_sequences`/
> `task_elements` tables this repo already declares as source of truth.

**It is FALSE if any of these is false, and each is measurable before the next is built:**

- F1 — a location layer can be computed that covers **100%** of scheduled elements (§S26.16 measured
  the room layer at 0-13%; that is the specific failure this must beat).
- F2 — trade order within a location is total and needs no solving (i.e. `sequence_rules.json`'s
  existing `seq` is already a total order over trades).
- F3 — the schedule that results is not worse than today's on the two invariants already locked:
  `auditFloating` float and the directional midair judge.
- F4 — a planner-grade programme (Hospital's own 121 tasks / 43 links, §S26.13) is expressible in
  the resulting tables without loss.

## §S27.1 — data model (4 objects, nothing else)

```
ZONE     { id, band, name, storeyRefs[], polygon|rasterMask, area_m2, elementCount }
CELL     { zoneId, tradeSeq }                    -- the unit of scheduling; == one Gantt bar
TASK     { id, cellId, name, start, end, durationSecs, elementGuids[] }
LINK     { fromTaskId, toTaskId, type=FS, lagSecs, origin: 'template'|'manual'|'physics' }
```

`CELL` is the whole model. An element belongs to exactly one cell. A cell is one bar. **Elements
carry no links.**

## §S27.2 — STAGE 1: the zone compiler (`compile_zones`)

**Reuse, do not rewrite:** `scripts/compile_rooms.py` / `build/room_walker.js` already rasterize a
storey's wall/door/column/window footprint at `RES=0.20m` with `SEAL=2` dilation and
`RASTER_EPS=1e-6` translation invariance (§S26.16). **Take that rasterizer verbatim. Change only the
consumer.** Rooms flood-fill the pockets the exterior CANNOT reach; zones partition the storey's
OCCUPIED extent, so fabric is included.

1. **Band the storeys first.** `spatial_structure` reports 63 storeys for Hospital, 73 for Terminal,
   38 for LTU (§S26.16) — federated pseudo-storeys. Merge by the shipped 3m band rank
   (`§S1_BAND_RANK`, already used by two consumers). A storey NAME is a display label and is never
   an identity. **A band, after merge, is a LEVEL.**
2. **Per level, rasterize the union of all element footprints** (not just wall-like — every
   scheduled element, because every one must land somewhere).
3. **Zone = connected component of that occupancy raster**, min area `ZONE_MIN_AREA` (start at
   `MIN_AREA=4.0` m², the room compiler's own constant — do not invent a new one). Components below
   it merge into their nearest neighbour by centroid distance.
4. **If a level yields ONE component, that level is ONE zone.** This is expected and correct — it is
   the coarse LBMS case, and it degenerates the grid to level × trade, which is still a valid grid.
   **It is NOT a failure and must not be "fixed" by forcing a k-way split.**
5. Zone names are deterministic: `L{bandRank}-Z{componentIndex}`, ordered by centroid (x then y).
   Hospital's own hand-authored programme names them `Zone A/B/C` (§S26.13) — a display alias may
   map onto these, but the identity is the computed one.

**STOP CONDITION S1 (independently checkable, no scheduler needed):** every scheduled element on
every one of the 7 fleet buildings is assigned to exactly one zone. Report `unassigned=0`. Anything
above 0 is a STOP, not a default-bucket. **This is F1 and it is the gate for the whole spec.**

## §S27.3 — STAGE 2: element → cell

`cell(e) = (zone(e), tradeSeq(e))`.

- `zone(e)`: the zone whose raster the element's XY centre falls in, on its own band. For an element
  spanning bands (16.3-16.7% of elements, §S26.5), use its **base** band — that is when it starts
  being built.
- `tradeSeq(e)`: the existing `e.seq` from `viewer/rates/sequence_rules.json`. **Not re-derived.**
- Declared containment (`rel_contained_in_space`) is used where present as a FINER sub-location for
  reporting only. **It does not select the cell** — coverage is 0-13% (§S26.16) and a
  minority-coverage key must never be load-bearing.

**STOP CONDITION S2:** `count(distinct cell) ` is reported per building, and no cell holds more than
`CELL_MAX_FRAC = 40%` of the building's elements. LTU's `Plan 1` currently holds 36% of the building
in one storey group (§S26.6 C2) — if the grid reproduces that, the zone split is not doing its job
and the cause must be reported, not tuned around.

## §S27.4 — STAGE 3: order, from two lists

- **Trades within a level:** ascending `seq`. Verify F2 first — that `sequence_rules.json` yields a
  TOTAL order over the trade values actually present (no ties that matter, no gaps that imply
  concurrency). If it does not, report and STOP; do not invent a tiebreak.
- **Levels:** ascending band rank.
- **Nothing else orders anything.** No graph, no topological sort, no SCC pass, no cycle-breaker.

## §S27.5 — STAGE 4: the template (the only tunable)

A template is a JSON document, shipped as data, editable without a rebuild:

```json
{ "name": "in-situ concrete, floor by floor",
  "trains": ["Substructure","Superstructure","Architecture","MEP","Finishes"],
  "offsets": { "Superstructure": {"after":"Substructure","levels":0},
               "Architecture":   {"after":"Superstructure","levels":1},
               "MEP":            {"after":"Architecture","levels":1},
               "Finishes":       {"after":"MEP","levels":1} },
  "structureRunsAhead": 0 }
```

`structureRunsAhead: 0` = in-situ concrete (a storey completes before the next starts).
`structureRunsAhead: 3` = the steel-frame case the USER named — frame erected 3 levels ahead of
slabs. **Default ships as in-situ concrete**, per the user's product ruling: the long tail of DIY /
small firms wants a sensible OOTB default and experts adjust or replace it.

**Durations** come from `installSecs` exactly as today (`§S25.8`) — a cell's duration is the sum
over its elements, divided by its crew count. **Not re-derived, not invented.**

**STOP CONDITION S4:** changing one offset value changes the schedule and nothing else; the shipped
default reproduces Stage 3's ordering exactly.

## §S27.6 — STAGE 5: links are the exception

Only three origins, and the count is reported per building:

- `template` — the offsets above, materialised as cell→cell FS links.
- `manual` — a person adds one where the grid is genuinely wrong (transfer beam, long-span truss,
  temporary works). Expected order: **tens per building, not thousands.**
- `physics` — **NOT generated in v1.** The 2.46-million-arrow web (§S26.12) is exactly what this
  spec exists to delete. See §S27.8 for the explicit prohibition.

**Cycle policy, taking the field's approach over this engine's (§S26.10):** a link that would close
a cycle is **REFUSED at insertion and reported**, never created-then-contracted. No Tarjan, no
condensation, no cycle-breaker anywhere in v1.

**STOP CONDITION S5:** `linkCount` per building is reported and the `manual` bucket is 0 on a
first run. If the template alone cannot produce a schedule without manual links, say so — do not
back-fill with physics links.

## §S27.7 — STAGE 6: physics becomes a check

Run the existing, unchanged judges on the finished schedule:

- `ScheduleGate.auditFloating` — the float number `witness_midair_zero.js` W-MZ-8 already locks.
- the directional midair judge (`_midairAudit`).

**STOP CONDITION S6 (this is F3, and it is the real acceptance test):** per building, float and
midair must be **no worse** than the live engine's current numbers — the §S26.14 "before" column
(Terminal float 4,756 / midair 0; Hospital 7,753 / 0; Clinic 1,102 / 0; LTU 12,712 / 0; Duplex
247 / 0; HHS 1,531 / 0; JKR 3,183 / 0). A regression here kills §S27 regardless of how clean the
grid is. **Note explicitly: a "0 violations" result from a check built on the grid's own definitions
is NOT evidence** — §S25_REVIEW's `engineGap` tautology is the standing precedent. Only the
pre-existing judges count.

## §S27.8 — STAGE 7: write into the tables that already exist

`schedules` / `tasks` / `task_sequences` / `task_elements` — declared source of truth at
`schedule_author.js:6`, built by the user in PR #59 / #502, currently **0 rows on every shipped
building** (§S26.13).

- One `tasks` row per CELL. Hospital's own planner-authored programme is 121 tasks for 63,182
  elements (§S26.13); a grid of ~7 levels × ~5 trades × 1-3 zones lands in the same order of
  magnitude. **That similarity is the sanity check for F4.**
- One `task_sequences` row per LINK.
- `task_elements` maps elements to their cell's task.
- `Terminal_meta.db` lacks these tables entirely while the other six have them empty — establish
  which extraction vintage is current BEFORE writing (§S26.15).

**PROHIBITION, and it is the most important line in this spec:** do **not** write element-level
physics arrows into `task_sequences`. That would persist the 2.46-million-edge web into the
IFC-native container and make the blob permanent instead of ephemeral (§S26.15).

## §S27.9 — build order and model assignment

| stage | deliverable | model | why |
|---|---|---|---|
| — | this spec, vetted | **Fable** (review) | the role that caught `engineGap` being arithmetically incapable of failing |
| S1 | `compile_zones` + coverage report | **Sonnet** | mechanical once §S27.2 is fixed; stop condition is a single number |
| S2 | element→cell + cell-size report | **Sonnet** | same |
| S3-S4 | order + template loader | **Sonnet** | data-driven, no judgment |
| S5-S6 | links + judge run | **Sonnet** | judges already exist and are unchanged |
| S7 | table writer | **Sonnet** | DDL already shipped |
| any | a stage that STOPS | **Opus** | a stop condition firing is a design question, not a coding one |

**No stage may begin before its predecessor's stop condition is reported green with its number.**
Stages S1-S2 need no scheduler at all — they are properties of the data.

## §S27.10 — what §S27 does NOT solve (stated in full, per §S26.7's precedent)

- **Crew levelling quality, duration realism, makespan credibility.** `installSecs` is carried
  through unchanged; whether the resulting durations are believable to a planner is untested.
- **The construction-practice claims in §S26.4/§S26.11 have not been checked by a real planner.**
  They are general knowledge. F4's Hospital comparison is the only empirical anchor.
- **Zone semantics.** A connected component is a computable proxy for a planner's zone, not the same
  thing. If S1 yields one zone per level on most buildings, the grid is level×trade and this spec
  delivers no zone benefit — only the deletion of the graph. **That would still be a win, but it
  must be reported as what it is, not dressed up.**
- **`rel_contained_in_space` at 0-13%** stays unused for cell selection (§S27.3).
- **The host/opening declared-relation gap** (§S26.12.2 — read on import, dropped before the shipped
  DB, geometric guesses backwards 18-40%/76%) is NOT fixed here. It is an extraction fix and belongs
  in its own lane.
- **The `designatedSupport` election win** (§S25_REVIEW.6) is orthogonal and unaffected.
- **Nothing about steel-frame templates is verified** beyond the user's own observation.

## §S27.R — REVIEW VERDICT (to be filled by the vetting pass; empty = NOT VETTED = do not build)

**VERDICT: NOT VETTED.** The evidence base (§S26) held up under independent re-derivation almost
completely — but §S27 itself carries four blocking defects (R1-R4), each of which would force a
Sonnet build agent to invent, and inventing is the one thing this project forbids. Written
2026-08-19 by the vetting pass §S27.9 row 1 asked for. Method: per the WATCHDOG mandate, nothing
was inherited — every number below was re-measured by this pass's own commands (logs in the session
scratchpad; probe re-runs via `scripts/probe_s26_rank_monotone.js`, DB queries via python3/sqlite3
read-only, IFC via grep). `viewer/` untouched; bim-ootb read-only throughout.

### §S27.R.0 — VERIFIED, re-derived independently (the §S26 base is real)

| claim | source | re-derived result |
|---|---|---|
| room-coverage table, all 7 rows | §S26.16 | **exact**: Hospital 63,415 el / 63 storeys / 142 spaces / 8,474 (13.4%) · Clinic 16,114/3/118/2,133 (13.2%) · Terminal 48,428/73/73/1,009 (2.1%) · JKR 9,410/4/79/107 (1.1%) · HHS 6,880/3/14/88 (1.3%) · LTU 125,698/38/0/0 (0%) · Duplex tables absent |
| 63/73/38 pseudo-storeys | §S27.2.1 | **exact** (`spatial_structure WHERE type LIKE '%Storey%'`) |
| §S26.5 backward-relation table | §S26.5 | **exact** on the 2 re-run buildings: `STRUCT=1 EMBED=0 HANG=0` → Duplex 51/850 (6.00%), bearing 30/702, host 9/104, opening 12/44; Clinic 834/11,325 (7.36%), bearing 261/8,189, host 484/2,737, opening 89/399; both `kahnLeftover=0 largestSCC=1` |
| §S26.1 live vs corrected SCC | §S26.1 | **exact** on Duplex: live 672, `STRUCT=1 EMBED=0` 5, n=1,119; bearing-only acyclic (§S25_REVIEW.5) confirmed by the same run's `bearingKahnLeftover=0` |
| task tables empty | §S26.13 | **confirmed with a correction — see R9**: Hospital/Clinic/JKR/HHS/Duplex all 0 rows; Terminal_meta AND LTU_AHouse_meta lack the tables entirely |
| §S26.14 branch | §S26.14 | branch `fix/s26-drop-carrier-ordering` exists (local+origin, `c30623d` off `6a395ca`); diff is exactly the one-line `if (bestCls === 2) continue` twin change at `cpm_schedule.js:138` + `time_machine.js:4593`. Fleet before/after numbers NOT re-run (cost); the before column matches §S25_REVIEW.2's independently-printed CPM column 7/7 — but see R8 |
| code citations | §S26.12/13, §S27.7/8 | `schedule_author.js:6` SOURCE-OF-TRUTH line ✓ · `schedule_gate.js:92-93` host-inference honesty note ✓ · `import_worker.js:315-317/:329` voids/fills readers ✓ · `witness_midair_zero.js:127` slices `_designatedSupport` by source text ✓ · `storey_walkable_raster` persisted in exactly 2 DBs (Hospital_meta, Terminal_meta) ✓ · Hospital's 8,474 containment rows all `RM_*` guids — compiled, not IFC-read ✓ |
| Hospital 2.0.ifc programme | §S26.13 | 43 `IFCRELSEQUENCE` ✓ · 1 `IFCWORKSCHEDULE` ✓ · 1 `IFCWORKPLAN` ✓ · **121 IfcTask is WRONG — see R6** |

A bonus reconciliation the file never states: the two different element counts it carries per
building (§S26.1's n vs §S26.16's) are BOTH right — "scheduled n" = `elements_meta` −
IfcOpeningElement/IfcSpace (`schedule_author.js:295`) − rows with no usable transform. Verified
exactly: LTU 125,698−3,368−0=122,330 · Duplex 1,193−71−3=1,119 · Hospital 63,415−0−233=63,182.
That definition is load-bearing for F1/S1 and appears nowhere in §S27 — see R3.

### §S27.R.1 — findings, ordered by how much they change the build

**R1 — BLOCKING, WRONG AS WRITTEN: F2 fails in the spec's own vocabulary — §S27.4 and §S27.5
specify two contradictory orders.** Re-derived from `viewer/rates/sequence_rules.json` (58 classes):
`seq` runs 1-11 with no gaps and each seq maps to one phase in the class map — so at SEQ grain F2
holds (a total order exists). But the class map gives Architecture = seqs **{5,6,8}** with MEP
Rough-in = **{7}** *interleaved between them*, and MEP Final = {9}; meanwhile §S27.5's template
trains are PHASES (`"Architecture"` strictly before `"MEP"`), and `"MEP"` matches NO phase value in
the rules (they are `MEP Rough-in`/`MEP Final`). Worse, `NAME_OVERRIDES[0]`
(glazed_curtainwall_facade) assigns phase=Architecture at sequence=**7**, so seq 7 maps to two
phases and phase(seq) is not even a function. §S27.4 (sort by seq → MEP Rough-in runs *inside* the
Architecture band) and §S27.5 (offset trains → all Architecture before all MEP) are both normative
and disagree. Per §S27.4's own rule this is report-and-STOP, delivered at vetting time instead of
build time. **Required amendment: declare ONE normative order (seq is the defensible one — it is
what the class map actually totals) and define the train↔seq mapping explicitly (which seqs
constitute each train, and what train owns seq 7's two phases).** Until then S3, S4, S5 and S6 all
stand on an undefined order.

**R2 — BLOCKING, WRONG CITATION: the level ladder §S27.2.1 tells the builder to reuse does not
exist as shipped code, and the nearest shipped thing is the construction §S26.6 C2 forbids.**
`§S1_BAND_RANK` appears in shipped viewer code only as a changelog comment (`sw.js:57`). The
"already used by two consumers" function is `deriveBandRanks` (`schedule_gate.js:329`; consumers
:469 computeSchedule, :1154 deriveZones) — and it is a **storey-NAME ladder** (groups by
`collapsePhase(e.storey)`, ranks names by median base_z), i.e. exactly the "rank came from storey
labels, and labels are junk" defect C2 guards against. The per-element `floor(base_z/3)` dense rank
§S26's numbers were measured with exists ONLY in `probe_s26_rank_monotone.js:15-17` (study-only,
bim-compiler). Two other shipped 3m constructs are near-misses: `time_machine.js:4947`
`Math.floor(cz/3)` (CENTER z, display banding) and `cpm_schedule.js:197` `floor(levelMeanZ/3)`
(name-keyed levels). A build agent following §S27.2.1 literally would wire in the name ladder and
believe it complied. **Required amendment: name the exact construction (per-element
floor(base_z/3), dense-ranked, the probe's) and state that it must be ported, not found.**

**R3 — BLOCKING, ENGINEGAP-CLASS: STOP CONDITION S1 (`unassigned=0`) is arithmetically near-unable
to fail, and its only failable population is nonzero TODAY on 4/7 buildings.** The zone raster is
the union of ALL element bbox footprints (§S27.2.2) rasterized as filled rects
(`compile_rooms.py:333` `_rasterize` fills the whole bbox); an element's XY centre always lies
inside its own bbox, so every element WITH a transform lands in an occupied cell — and merge-below-
MIN_AREA still assigns. `unassigned` can therefore only count elements with no usable transform.
Pre-computed today, no scheduler: **Hospital 233, Clinic 43, HHS 41, Duplex 3, Terminal/JKR/LTU 0.**
So as written S1 either (a) includes them → guaranteed day-one STOP on 4/7 buildings, or (b)
excludes them → the check cannot fail and F1 is a tautology, the precise §S25_REVIEW.1 trap §S27.7
warns about — for a different stage than the one it actually bites. **Required amendment: define
the S1 denominator (the R0 reconciliation above is the real one, in code at
`schedule_author.js:295`) and state the no-geo policy explicitly** — these are §S25_REVIEW.8's
`{s:0,e:0}` day-0 population and they must not be silently dropped OR silently day-0'd. The
meaningful F1 content then lives in S2, not S1.

**R4 — BLOCKING, UNDER-SPECIFIED: the computation that produces `TASK.start/end` is not in the
spec.** §S27.4 gives order, §S27.5 durations and offsets, §S27.6 links — no stage says how times
are computed from them: do sibling zones within a level run serial (Hospital's planner chains
Zone A→B→C `.FINISH_START.` — §S26.13) or parallel (completely different makespan and float)? What
lag does `"levels": 1` materialise as? How is a multi-resource cell's "crew count" chosen — seq 4
holds IfcSlab (CONCRETE_GANG) and IfcPlate (STEEL_ERECTOR) in ONE cell, and `SEQUENCE_DEFAULT`
(`rates.js:260`) is resource:**null**? Which calendar (`toWall`/`toProductive`, §S25.8)? S4's and
S6's stop conditions both measure this unspecified computation, so neither is currently checkable.
**Required amendment: one §S27.4b-style paragraph defining the forward pass over cells** — it can
be ten lines, but it must exist before a builder writes it by taste.

**R5 — CONDITION, WRONG NUMBER: STOP CONDITION S2's threshold misses its own motivating case and
trips a different building on day one.** Pre-computed today (band=floor(base_z/3), seq from the
class map, degenerate 1 zone/level): max cell share **LTU 19.2%** — the LTU `Plan 1` 36% worry
§S27.3 cites dissolves at cell grain because trades subdivide it — while **Terminal = 40.3%**
(19,521/48,428 in band 6 × seq 4: the Metal Deck IfcPlate population) already exceeds
`CELL_MAX_FRAC=40%`. Others: Duplex 37.9%, Clinic 20.3%, JKR 13.7%, HHS 12.9%, Hospital 11.0%.
As configured, S2 = a guaranteed Terminal STOP (→ Opus per §S27.9) unless zones genuinely split
band 6, and near-vacuous everywhere else. The threshold was set without computing the distribution
it gates. **Amend: either re-derive the threshold from the failure it is meant to catch, or
pre-declare Terminal's stop as the expected first Opus question.** (Method caveat: computed from
the JSON mirror's class map without NAME_OVERRIDES; the curtain-wall override matches zero Terminal
elements by that rule's own documentation, so band6×seq4 stands.)

**R6 — CONDITION, WRONG NUMBER: Hospital's programme is 75 IfcTask, not 121.** `grep -o
"IFCTASK("` on `/home/red1/Downloads/Hospital 2.0.ifc` → **75**; `grep -c "IFCTASK"` → 121 = 75 +
46 `IFCTASKTIME` — the 121 is a substring conflation, the exact GIGO shape the WATCHDOG names. The
project's own importer agrees: `Downloads/Hospital 2.0_meta.db` holds tasks=**75**,
task_sequences=43, schedules=1, task_elements=2,900 (the "2900 links" §S26.13 itself quotes).
43 IfcRelSequence and 1 IfcWorkSchedule verify. Correct §S26.13's table, F4's text, and §S27.8's
sanity line. The order-of-magnitude argument survives — today's occupied (band×seq) cell counts are
Hospital 94, Terminal 74, LTU 68, Clinic 45, JKR 42, HHS 35, Duplex 27, same order as 75.

**R7 — CONDITION, F4 IS UNVERIFIABLE AS WRITTEN, and its anchor contradicts the zone definition.**
(a) No stage measures F4: §S27.8's "same order of magnitude" is not "expressible without loss", and
round-tripping the planner's actual 75 tasks/43 links through the tables is PR #59's importer path,
which §S27 never schedules as a check. (b) Zone = connected component of an occupancy union that
INCLUDES floor slabs (§S27.2.2 "every scheduled element") — a continuous floor plate is one
component by construction, so k=1 per level is the near-certain outcome, which §S27.2.4/§S27.10
accept; but Hospital's planner cut THIS connected building into Zone A/B/C — a spatial split of one
plate. The spec's own F4 anchor is evidence that connectivity cannot reproduce planner zones.
**Amend: give F4 a real check (import the planner's programme into the same tables and diff), and
state in §S27.10 that connectivity-zoning cannot produce Hospital's A/B/C — only disjoint plates
ever split.**

**R8 — CONDITION, INSTRUMENT MISMATCH in STOP CONDITION S6.** S6 pins the §S26.14 "before" column
(probe-measured, e.g. Duplex float **247**) but mandates "the existing, unchanged judges" as the
instrument. Run today on live `main` (`ONLY=Duplex node viewer/tests/witness_midair_zero.js`): the
judge-side measure is `auditFloating 0 → 237`, and W-MZ-8 currently **FAILS** (locked 289, got 237)
— the §RESULTS-addendum Duplex regression, still open. Two instruments, 247 vs 237, ten apart on
the smallest building. "No worse than live" is unfalsifiable until the baseline names its
instrument, shift-hours, and DB set — and until the addendum's two open items (Duplex/HHS TRADE
regression; the #1427/#1428 Hospital/Terminal meta elevation-patch integrity check, which sits
directly under §S27.2's banding input) are resolved or explicitly carried as known-dirty baseline.
S6 is otherwise the one genuinely external check in the spec — this is fixable with one table.

**R9 — CONDITION, WRONG: §S27.8 "Terminal_meta.db lacks these tables entirely while the other six
have them empty."** Re-derived via sqlite_master: **LTU_AHouse_meta.db also lacks all four tables**
(and `qto_cache`/`storey_walkable_raster`). Five empty + TWO absent. The "which extraction vintage
is current" question §S27.8 defers is therefore bigger than stated and blocks S7 on two buildings,
not one.

**R10 — NOTE, WRONG SOURCE CITED: §S27.3's `tradeSeq` source.** `viewer/rates/sequence_rules.json`
is a documented MIRROR — its own `meta.note`, `rates.js:109` and `sw.js:186` all state viewer.html
never calls `loadSequenceRules()`; the executed tables are `rates.js`'s hardcoded
`SEQUENCE_RULES`/`SEQUENCE_DEFAULT`/`SEQUENCE_NAME_OVERRIDES`. The mirror has drifted before
(worth 37d of Hospital programme, per its own header). Cite `rates.js` as the source, or declare
the JSON authoritative for the new engine and say the divergence risk out loud.

**R11 — NOTE, §S27.10 honesty gaps (Q: is the not-solved list complete? No — five omissions):**
1. the **#1427/#1428 elevation-patch integrity question** (§RESULTS addendum, §S25_REVIEW.8) —
   open, and §S27.2's bands are computed from exactly those possibly-patched base_z values;
2. the **Duplex/HHS TRADE-invariant regression** (addendum: "real/unexplained") — open, and it
   lives inside S6's baseline (R8);
3. the **no-geo population** (R3) and its consumer contract (§S25_REVIEW.8's `{s:0,e:0}` day-0
   poison) — never mentioned;
4. the **template-blind midair judge**: §S26.4b names "a hard-coded assumption about how buildings
   go up" a latent defect, §S27.5 ships steel-frame as template two, §S27.7 runs today's judge
   unchanged — a steel-template run will fail S6's judge on legitimate schedules; only the in-situ
   default is protected, and §S27.10's "nothing about steel-frame is verified" understates this;
5. **manual links assume an editing surface that does not exist** — §S27.6 expects a person to add
   tens of links, §S25_REVIEW.8's "Gantt-drag is still not a design" is the same missing UX,
   unlisted. (Minor: §S26.7's LTU 32×-density perf flag also applies to S6's `auditFloating` run.)

### §S27.R.2 — the stop-condition audit §S27 asked for (Q3, answered directly)

No stage's condition depends on a LATER stage — that §S25.11 defect is genuinely absent (S1's
coverage report needs the `zone(e)` lookup that §S27.3 nominally owns, but §S27.9 puts the report
in S1's deliverable, so it is a blurred boundary, not a forward dependency). The cannot-fail trap
is present twice: **S1** (R3) and **S5**, whose `manual=0` on a first run is true by definition
(nobody has added manual links) and whose "if the template alone cannot produce a schedule" has no
defined failure mode — a grid of two sorted lists ALWAYS produces a schedule. S2 can fail (R5 —
and will, on Terminal). S4 is checkable only after R4 is fixed. S6 is real and external (R8
caveat). S7 has no stop condition at all — F4's check should become it (R7).

### §S27.R.3 — Room Path separability (Q4, answered directly)

§S27.2's "take the rasterizer verbatim, change only the consumer" is **honest at the function
boundary but understates what changes**. Genuinely separable and worth taking verbatim:
`_rasterize` (`compile_rooms.py:333-346` — pure: rects+grid+origin → occupancy bytearray),
`_dilate` (:348-362), the `RASTER_EPS` quantizers and extent formulas (`flood_rooms`:561-562; JS
twin `build/room_walker.js:249-272`, constants :22-40). NOT reusable, must be new code: (a) the
INPUT — `storey_walls` (:256-283) is hardwired to WALL_LIKE classes + `discipline='ARC'` +
§STOREY-Z name-anchor assignment, while §S27.2.2 needs all scheduled elements keyed by band rank
(so the producer changes too, not "only the consumer"); (b) connected components over OCCUPIED
cells — the room code labels components only of FREE space (`flood_rooms` pocket loop,
`_flood_exterior`:716); a CC pass over the blocked set exists nowhere in it; (c) a decision the
spec does not make: does the ZONE raster apply `SEAL=2` dilation (0.4m bridging — fuses anything
within 0.8m into one zone) or the raw raster? It is outcome-determining for zone count and is
unspecified. Constants verified as claimed: RES=0.20 (:19), MIN_AREA=4.0 (:20), SEAL=2 (:34),
RASTER_EPS=1e-6 (:46), §STAIR-EXCLUDE (:51-52), §SUSPECT thresholds (:709, :870). The three
walker copies have drifted in surface area (build/room_walker.js 1,347 lines vs
viewer/lib/room_walker.js 1,474 — the viewer copy adds camera-room-index functions); the
rasterizer core matches, but the spec should name WHICH copy is canonical for the port.

### §S27.R.4 — what this review did NOT check

§S26.14's after-column fleet numbers (branch verified, diff verified, numbers not re-run);
§S26.3's hang-redundancy percentages and §S26.2's 4,706 all-below count (the 702 load-bearing
denominator DID reproduce inside the §S26.5 re-run); §S26.5 on the 5 buildings not re-run
(Terminal/Hospital/LTU/JKR/HHS — the two re-run were exact, and §S26.1's three-probe agreement
covers the SCC side); whether `rates.js` and the JSON mirror are in sync TODAY (R10 is about which
to cite, not a measured drift); and the §S26.4 construction-practice claims, which remain
planner-unverified exactly as §S26.9 says.

### §S27.R.5 — what must change before a build agent is dispatched

R1 (one order + train↔seq map), R2 (name the real band-rank construction), R3 (S1 denominator +
no-geo policy), R4 (define the forward pass) are spec amendments — none is large, all four are
prose, and every one of them is a place a Sonnet builder would otherwise invent. R5-R9 are
corrections/decisions that can land in the same editing pass. **If only one thing is fixed first,
fix R1: every stage from S3 on stands on an order the spec currently defines twice,
contradictorily.** Re-vet is cheap after amendment — this review's commands are all cited and
re-runnable.

---
# §S28 — TWO LANES, NOT ONE PIPELINE (2026-08-19)

**Standing: PROPOSED, NOT VETTED.** Supersedes **§S27's SHAPE**, keeps most of its content. §S27 and
§S27.R stay verbatim as record (same treatment §S24 got from §S25). No build agent may be dispatched
until §S28.R records a verdict.

## §S28.0 — why the shape changed, not just the four findings

§S27.R returned NOT VETTED with 4 blocking findings. Three (R1, R2, R3) are one afternoon's spec
sloppiness and are fixed below. R4 is a real gap and is filled below. **But the reason §S28 exists is
a fifth problem §S27.R did not have to name, because it is about layout rather than content:**

§S27 was a **seven-stage parallel build of a new engine beside a live one with locked witnesses**.
That is the same shape as §S23, §S24 and §S25 — three grand designs in this file, none shipped.
Meanwhile the §S26.14 branch moved measured numbers on 7 buildings with a ONE-LINE change in an
afternoon. A fourth grand design is the predictable failure here, and it is a process risk, not a
technical one.

**§S28 splits the work into two lanes that ship independently and neither of which is big-bang:**

- **Lane A — the engine, by DELETION.** Order stops being derived from a graph. Removes machinery.
  Judged entirely by witnesses that already exist. Does not need Lane B.
- **Lane B — the product, by ADDITION.** The cell grid is written into the IFC-native tables so a
  planner gets something editable. Does not need Lane A.

Neither lane blocks the other. Either can be abandoned without stranding the other.

## §S28.1 — R1 RESOLVED: `seq` is the order; `phase` is a label (and the data was right)

§S27.4 asserted trade order comes from `phase`. Measured against
`viewer/rates/sequence_rules.json`:

```
seq 1        Substructure
seq 2,3,4    Superstructure
seq 5,6      Architecture         (IfcWall, IfcDoor, IfcWindow, IfcStair, …)
seq 7        Architecture         (curtain-wall glazing, via NAME_OVERRIDE 'glazed_curtainwall_facade')
seq 7        MEP Rough-in         (IfcFlowSegment, IfcDuctSegment, IfcCableCarrier, …)
seq 8        Architecture         (IfcRoof)
seq 9        MEP Final
seq 10,11    Finishes
```

`seq` is a **total order over 1..11**. `phase` is **not monotone in seq** — "Architecture" occupies
5,6,7,8 and straddles "MEP Rough-in" at 7. Ordering by phase is therefore ambiguous; ordering by seq
never was.

**RESOLUTION: `seq` is the single normative order everywhere in §S28. `phase` is a DISPLAY LABEL for
the Gantt bar and orders nothing.** A cell is `(location, seq)`, never `(location, phase)`. The
`trains` array in the §S27.5 template is replaced by seq bands; a template names its trains by seq
range, and the phase string is carried through for display only.

**Two elements sharing seq 7 across different phases are CONCURRENT, and that is correct, not a
collision** — curtain-wall glazing and MEP first fix genuinely overlap on site.

**The pattern worth naming, because it is now twice:** a human-readable LABEL used as an IDENTITY.
Storey label `"Roof - Main"` at three elevations (§S26.6 C2); phase label `"Architecture"` across
four seq values straddling another trade (here). **Anywhere this codebase keys off a name a human
typed is a candidate for the same defect.** That is a standing search, not a one-off fix.

## §S28.2 — R2 RESOLVED: the band rank must be BUILT, not cited

§S27.2 told the builder to "use the shipped `§S1_BAND_RANK`". §S27.R found no such shipped function.
Confirmed independently — `schedule_gate.js:476, 483, 856`:

```js
var r = _bandRank[collapsePhase(el.storey)];
rankKey[t] = _bandRank[collapsePhase(elements[t].storey)] || 0;
```

`_bandRank` is keyed on `collapsePhase(el.storey)` — the **storey NAME**. It is the label ladder
§S26.6 C2 forbids, not an elevation banding. Real elevation banding exists only in
`bim-compiler/scripts/probe_s26_rank_monotone.js` (`Math.floor(bz / 3)`, dense-ranked).

**RESOLUTION: Lane B builds `bandRankOf(element)` as new, small, named code**, ported from the
probe's construction, with its own witness. It is NOT a citation of existing work. The existing
`_bandRank` is left alone — Lane A does not touch it and Lane B does not consume it.

## §S28.3 — R3 RESOLVED: a stop condition that can actually fail

§S27's STOP CONDITION S1 was `unassigned = 0` where zones are built FROM the elements' own
footprints — guaranteed by construction, the `engineGap` tautology class (§S25_REVIEW).

**RESOLUTION — S1 is replaced by three numbers, each of which can fail:**

- **S1a — `noGeometry`**: elements with no usable bbox, which genuinely cannot be placed. Non-zero
  today (§S27.R measured Hospital 233, non-zero on 4/7). **Report the count and the class histogram.
  It is a data finding, not a pass/fail** — but a change in it between runs is a regression.
- **S1b — `bandSpan`**: elements whose bbox spans more than one band, assigned to their BASE band.
  §S26.5 measured 16.3-16.7%. **Report it; a large move means the banding changed underneath.**
- **S1c — `zoneCount` per level, and the largest zone's share of its level.** If every level yields
  exactly one zone on every building, **say so plainly** — the grid is level×seq, zones add nothing,
  and §S28.6's zone step should be dropped rather than kept as ceremony (§S27.10's own honesty note).

§S27's S2 threshold (`CELL_MAX_FRAC = 40%`) is **withdrawn as a gate** — §S27.R found it trips
Terminal at 40.3% on day one from a genuine metal-deck concentration, while the LTU case that
motivated it dissolves to 19.2% at cell grain. **Report the distribution; do not gate on it.**

## §S28.4 — R4 RESOLVED: how a task's dates are computed (the gap §S27 left)

§S27 never said how `TASK.start`/`end` are produced. The arithmetic already exists and is reused,
not invented — `time_machine.js:5105-5130` `§CREW_DEMAND`:

```js
_crewWorkDays[_r] += (el.installSecs || 0) / 28800;      // crew-days, 8h shift
var _capacityCd = _crews * projectDays;                  // capacity in the SAME unit
```

**A cell's duration is that same computation at cell grain instead of project grain:**

```
cellDemandCrewDays  = Σ (installSecs of the cell's elements) / 28800
cellDurationDays    = cellDemandCrewDays / crews(resource)
```

**UNITS ARE PART OF THE SPEC, and this is why:** `§ARCH_START_TEMPO / M1` records that this exact
ratio was silently wrong for months because demand was quoted in 8-hour crew-days while
`projectDays` was counted on a 24-hour clock — **one calendar day was worth three crew-days of
capacity and every utilisation printed was ~3× overstated.** Any implementation must state the shift
length at every conversion and assert the two sides agree.

**Scheduling within a level:** cells run in ascending `seq`. Zones within one `(level, seq)` run
**SERIALLY** — see §S28.5. Levels run per the template offset.

**STOP CONDITION D:** total makespan is reported next to today's engine per building (§S26.14's
"before" column). A wildly different number is a finding to report, not a result to accept.

## §S28.5 — MEASURED: what a real planner's zones actually are (Hospital 2.0.ifc)

§S27 assumed zones were parallel work areas. **Wrong.** Extracted from the hand-authored programme
in the project's own test model (`/home/red1/Downloads/Hospital 2.0.ifc`, task names in file order):

```
Structures
  Piles                 -> Zone A -> Zone B -> Zone C
  Pile Caps · Foundation Slab · Strip Footing · Footing Columns
  Level 1
    Floor Slab
    Columns             -> Zone A -> Zone B -> Zone C
    Structural Framing  -> Zone A -> Zone B -> Zone C
  Level 2
    Columns             -> Zone A -> Zone B -> Zone C
  Level 3
    Columns             -> Zone C -> Zone B -> Zone A      <-- REVERSED
```

Four facts, all load-bearing for the spec:

1. **The hierarchy is LEVEL → TRADE → ZONE.** Exactly the cell grid, with zone as a third level.
2. **Zones are SPATIAL, not categorical** — same level, same trade, different part of the plan.
3. **Zones are SEQUENTIAL, not parallel.** Every `IfcRelSequence` between them is `.FINISH_START.`
   (§S26.13). A zone split is one crew FLOWING through a floor — the LBMS mechanism (§S26.10) —
   not concurrency.
4. **Level 3's columns run C → B → A.** Serpentine: the crew works back the way it came rather than
   teleporting to Zone A. That is a human planning crew movement, and it is the strongest single
   piece of evidence in this file that the model carries a genuine programme.
5. **The planner zoned STRUCTURE, not architecture** — Piles, Columns, Structural Framing. Nothing
   in the architecture trades is zoned.

**Consequence for §S28.6: build zone-capable, default to one zone per level.** If a level's raster
yields one component, that level is one zone and the grid is level×seq — reported as such per
§S28.3 S1c, never forced into a k-way split.

**Correction to the record:** §S26.13 and §S27.8 say Hospital carries "121 IfcTask". §S27.R found
this is a grep conflation — `IFCTASK(` = **75**, plus 46 `IFCTASKTIME` = 121. The project's own
importer agrees (75). The 43 `IfcRelSequence` figure stands. **The programme is real; the count was
wrong.** Also: §S27.8's "the other six have them empty" is wrong — `LTU_AHouse_meta.db` lacks the
task tables too, so it is 5 empty + 2 absent (Terminal, LTU).

## §S28.6 — LANE B: the product, by addition

Order of work, each step reporting its number before the next begins:

- **B1** `bandRankOf()` — new code per §S28.2, with a witness. Stop: band count and membership
  reported per building; a level is never a storey name.
- **B2** zone compiler — reuse `compile_rooms.py`/`room_walker.js`'s rasterizer, change the consumer
  from exterior-unreachable pockets to occupancy components (§S26.16). Stop: §S28.3 S1a/S1b/S1c.
  **§S27.R raised whether that rasterizer is separable from the flood-fill at all — B2 must answer
  that with function/line boundaries BEFORE writing code, and report if it is not.**
- **B3** element → cell `(level, zone, seq)`. Stop: cell count + size distribution, no gate.
- **B4** durations per §S28.4. Stop: makespan vs §S26.14 baseline.
- **B5** write `schedules`/`tasks`/`task_sequences`/`task_elements`. Stop: row counts; task count in
  the same order of magnitude as Hospital's own 75.
- **PROHIBITION (unchanged from §S27.8, and it is still the most important line):** never write
  element-level physics arrows into `task_sequences`. That makes the 2.46M-edge web permanent
  instead of ephemeral.

Lane B changes **no scheduling behaviour**. It is a new output. Nothing it does can regress a
witness, which is exactly why it is separable.

## §S28.7 — LANE A: the engine, by deletion

Independent of Lane B. The hypothesis, and it is falsifiable in one run:

> Order comes from a sort — `(bandRank, seq, base_z, guid)` — and physics returns to being a
> `max()` delay over already-placed elements, as it was before `0fe8eb2` (2026-08-07). The SCC
> pass, the condensation, the cycle-breaker and the contraction counters become unreachable.

Deletable surface, measured: `viewer/cpm_schedule.js` is 650 lines with **38** lines matching
`tarjan|scc|contract`.

**The one hazard, and it is documented in the code that was deleted** — `0fe8eb2^
schedule_gate.js:252-262`: re-sorting made 2,341 elements float again because the gate scanned a
PARTIAL grid of already-placed elements. **Guard (§S26.6 C1): the gate uses the judge's global scan
(`auditFloating`'s grids, `schedule_gate.js:1055-1059`), which was always global and correct.** A
backward relation is then REPORTED and counted, never silently skipped.

**STOP CONDITION A (this is the whole lane):** float and midair, from the UNCHANGED existing judges,
no worse than §S26.14's "before" column per building. Better is a win; worse kills the lane. **A "0"
produced by any check built on the sort's own definitions is not evidence** (§S25_REVIEW precedent).

§S27.R also flagged that the Duplex float baseline is quoted as 247 (probe) while the witness-side
measure is 237 — **name the instrument in every number this lane reports.**

## §S28.8 — what §S28 does NOT solve

Everything in §S27.10 still applies, plus:

- **Whether Lane A's sort actually reproduces float parity is UNTESTED.** It is the lane's whole
  hypothesis and it may simply fail — that is the point of running it first and cheaply.
- **Crew realism.** §S28.4 reuses `§CREW_DEMAND`'s arithmetic; whether the resulting durations are
  credible to a planner is untested and F4's Hospital comparison is still the only anchor.
- **Zone semantics remain a computable proxy** for what a planner means by a zone. Hospital's
  A/B/C are named regions on a plan; a connected component of an occupancy raster is not obviously
  the same partition, and §S28.5's evidence does not establish that it is.
- **Serpentine order (§S28.5 fact 4) is NOT modelled.** Zones run A→B→C every level; the real
  programme alternates. Named so it is not mistaken for an oversight.
- **The host/opening extraction gap** (§S26.12.2) and the **`designatedSupport` election win**
  (§S25_REVIEW.6) are both untouched and both belong to other lanes.

## §S28.R — REVIEW VERDICT (empty = NOT VETTED = do not build)

**VERDICT: NOT VETTED.** §S28 genuinely fixed R1's order question and R2 (verified below), half-fixed
R3 and R4 — and its two NEW load-bearing pieces both fail re-derivation: §S28.5's headline
observation (the Level 3 reversal, "the strongest single piece of evidence in this file") is a
file-ordering artifact contradicted by both the IfcRelSequence links and the task dates, and BOTH
lanes carry a blocking defect that makes §S28.0's independence claim false. Written 2026-08-19 by
the vetting pass. Method per the WATCHDOG mandate: every number below re-measured by this pass's own
commands — IFC via grep + a paren-aware entity parser, DBs via python3/sqlite3 read-only,
`ONLY=Duplex node viewer/tests/witness_midair_zero.js` run fresh (log saved and read), pre-#1242
code via `git show 0fe8eb2^:viewer/schedule_gate.js`. bim-ootb read-only throughout; `viewer/`
untouched.

### §S28.R.0 — VERIFIED, re-derived independently

| claim | source | re-derived result |
|---|---|---|
| seq/phase table | §S28.1 | **exact** against `sequence_rules.json` (58 classes): seq total over 1..11; Architecture = {5,6,8} + seq 7 via `glazed_curtainwall_facade` override; MEP Rough-in = {7}; MEP Final = {9}; Finishes = {10,11}. Phase is not monotone in seq ✓. One omission: `SEQUENCE_DEFAULT` = phase Architecture, **seq 6, resource null** — absent from the table and load-bearing for R5 below |
| `_bandRank` is a storey-NAME ladder | §S28.2 | **exact** — `schedule_gate.js:476` `_bandRank[collapsePhase(el.storey)]`, `:856` `rankKey[t]=...`; probe's `floor(bz/3)` dense rank confirmed at `probe_s26_rank_monotone.js` (BAND_M=3) |
| noGeometry counts | §S28.3 S1a | **exact**: Hospital 233, Clinic 43, HHS 41, Duplex 3, Terminal/JKR/LTU 0 (elements_meta minus Opening/Space, LEFT JOIN element_transforms IS NULL; zero-bbox rows = 0 everywhere) |
| `§CREW_DEMAND` citation + units history | §S28.4 | **exact** — `time_machine.js:5104-5106` `installSecs/28800`, `:5130` `_capacityCd = _crews * projectDays`, §ARCH_START_TEMPO/M1 3× overstatement comment verbatim at :5112-5116 |
| Hospital counts | §S28.5 | **exact**: `IFCTASK(`=75, `IFCTASKTIME(`=46 (75+46=121 conflation confirmed), `IFCRELSEQUENCE(`=43 all `.FINISH_START.`, `IFCWORKSCHEDULE(`=1; importer DB (`Downloads/Hospital 2.0_meta.db`) tasks=75/task_sequences=43/task_elements=2,900 |
| 5 empty + 2 absent | §S28.5 correction | **exact**: Terminal_meta + LTU_AHouse_meta lack all four tables; the other five have them at 0 rows |
| zones are SPATIAL | §S28.5 fact 2 | **confirmed with data §S28.5 never had**: joining task_elements→element_transforms, L1 Columns Zone A x∈[−12.1,22.5], B x∈[29.3,45.3], C x∈[52.2,86.7] — clean disjoint X-bands of ONE plate (which also confirms §S27.R R7: connectivity-zoning cannot produce them) |
| pre-#1242 order + 2,341-float note | §S28.7 | **exact text** at `0fe8eb2^ schedule_gate.js:245-262`; `auditFloating` builds grids from ALL elements at `schedule_gate.js:1056-1060` ✓; cpm_schedule.js = 650 lines, `tarjan\|scc\|contract` case-insensitive = **40** lines today (spec says 38 — immaterial) |
| W-MZ-8 instrument gap | §S28.7 last ¶ | **worse than stated — see R4**: witness re-run today, `FAIL W-MZ-8 Duplex locked 289 got 237` |

### §S28.R.1 — findings, ordered by how much they change the build

**R1 — BLOCKING, LANE A'S HYPOTHESIS IS NOT THE MEASURED ONE: the sort key §S28.7 states was never
measured, and the evidence cited for it was produced by a different key that §S28.1 just outlawed.**
`probe_s26_rank_monotone.js` (header + :43-46) ranks by **(bandRank, phaseRank, depth, bz, guid)**
with two components §S28.7's `(bandRank, seq, base_z, guid)` does not have: (a) **phaseRank**
(Substructure < Superstructure < Architecture < everything else) — a PHASE order, the exact thing
§S28.1 demoted to a display label, sits inside the probe that produced every §S26.5/§S26.6 monotone
number Lane A leans on; (b) **INHERITANCE** — "a hosted element takes its HOST's bandRank, a hanging
element takes its CARRIER's bandRank", the probe's own hole-closer for the two down-pointing
families — plus (c) `depth` (longest bearing path). So the 93-97%-monotone framing is un-inherited
by Lane A's key: seq≠phaseRank, no depth, and without inheritance a band-major sort puts a hanger's
carrier-above (the slab whose base_z is the next band) systematically LATER than the hanger.
Additionally the claim "as it was before 0fe8eb2" is wrong: the pre-#1242 engine (re-read via git
show) was TWO passes — struct-only `(base_z, seq)`, then non-struct `(seq, bandRank-name, base_z)`
with ALL structure placed before ANY non-structure — never one band-major sort. Every carrier was
placed before any hanger by construction; Lane A's unified sort forfeits exactly that property.
**Required amendment: state the actual key (and whether it includes inheritance — which requires
computing hang/host relations, i.e. the "no graph" framing dies), or re-run the probe with the
literal §S28.7 key and quote THOSE violation numbers.** STOP-AND-REPORT: if the seq-keyed re-run's
backward-relation counts differ materially from §S26.5's table, Lane A's cost is unknown — report,
do not proceed on the phaseRank-keyed numbers.

**R2 — BLOCKING, LANE B IS NOT INERT: any dated rows B5 writes flip every shipped building to the
captured-schedule path in the live viewer.** `injectGantt`'s `_cap` probe (`time_machine.js:
4790-4818`) does `SELECT ... FROM tasks WHERE schedule_start IS NOT NULL ... AND (is_summary IS NULL
OR is_summary=0)` — no schedule_id filter, no status filter, no display_authored gate. One dated
non-summary row + task_elements links ⇒ `_cap` non-null ⇒ timeline rebased to `_cap.base`, covered
elements overlaid with task dates/names (`:5377+`), `_capWindowRescale`/`_ogSupportSweep` engaged.
B5 maps EVERY element to a cell task — near-100% coverage — so "Lane B changes no scheduling
behaviour. It is a new output" (§S28.6) is FALSE as written; the write target is live input to the
display pipeline. (`witness_midair_zero.js` itself does NOT read the tasks tables — verified by
grep — so "cannot regress a witness" is literally true while the live viewer changes completely:
proxy-green, ground-truth-changed, the WATCHDOG's own named failure shape.) This also collides with
the file's DO-NOT-REMOVE header: Lane B's §S28.4 forward pass is a SECOND computation of schedule
timing, persisted where the viewer reads it. **Required amendment: an explicit adoption policy —
either B5's rows are meant to drive the viewer (then say so, spec the interaction with Lane A's
engine output and the §S26.14 baselines, and reuse ScheduleAuthor's writer conventions
`schedule_author.js:448-466` including schedule_id scoping and delete-first), or they must be
invisible to `_cap` (then name the mechanism, which is an engine-side change and breaks Lane B's
"no engine edits" premise).** Note also: shipped-DB writes must ship per the project's
patch+self-heal-loader rule, never as binaries — B5 says nothing about distribution.

**R3 — BLOCKING FOR THE RECORD, WRONG: §S28.5 fact 4 ("Level 3's columns run C→B→A ... REVERSED
... serpentine") is a file-ordering artifact.** Re-derived from the entities, not the file order:
Level 3 Columns' IfcRelSequence links run **Floor Slab→Zone A→Zone B→Zone C** — identical to every
other level — and the IfcTaskTime dates agree: Zone A #3462440 Aug 14-18, Zone B #3462434 Aug
19-23, Zone C #3462428 Aug 24-28 (C carries P75D total float, non-critical). The C,B,A appearance
is entity-ID/file order only (#3462271 C < #3462275 B < #3462277 A). §S28.5's own method line
("task names in file order") is the GIGO mechanism, same class as the 121-task grep conflation it
corrects. Consequences: fact 4 is deleted, not amended; §S28.8's "the real programme alternates" is
also false; and the genuineness of the programme rests on the (real, verified) links/dates/float,
not on serpentine. **The serpentine instinct IS in the data — one level down and §S28.5 missed
it:** Zone A of Piles is the EAST band (x∈[51.8,76.1]) while Zone A of Columns is the WEST band
(x∈[−12.1,22.5]) — the crew flows E→W piling, W→E on columns, and the planner RELABELS so A→B→C
always equals work order. Zone labels are per-(trade) orderings, not fixed regions — which
§S28.6's fixed-zone cell identity cannot represent and must at least name.

**R4 — BLOCKING, THE STOP CONDITIONS CITE NUMBERS THAT DISAGREE OR DON'T EXIST.** (a) STOP A pins
"§S26.14's before column" measured by "the UNCHANGED existing judges" — but three Duplex floats now
coexist: W-MZ-8's lock **289**, §S26.14's before **247** (probe-side), today's judge **237**
(re-run this pass: `FAIL W-MZ-8 Duplex locked 289 got 237`, witness RED on main `6a395ca`). A build
agent literally cannot satisfy the witness and the baseline at once. (b) STOP D and B4 compare
"makespan ... §S26.14's before column" — §S26.14's table has NO makespan column (SCCs/float/midair
only); the nearest real makespan baseline is §S25_REVIEW.2's CPM column (Terminal 85.1d, Hospital
263.5d ... at ITS shift settings; the probe runs SHIFT_HOURS=24, the witness differs). **Required
amendment: ONE baseline table — building × {float, midair, makespan} × named instrument × shift ×
DB set — measured fresh, plus the W-MZ-8 relock/repair decision (the §RESULTS-addendum Duplex
regression is still open), BEFORE either lane's stop condition is evaluable.**

**R5 — CONDITION, §S28.4 RESOLVES R4's FORMULA BUT NOT R4's QUESTIONS.** Still undefined, each a
place a Sonnet builder invents: (a) **multi-resource cells** — seq 4 = CONCRETE_GANG+STEEL_ERECTOR,
seq 7 = 4 resources, seq 9 = 3, seq 6 = CARPENTER+CONCRETE_GANG+null: `crews(resource)` singular
has no value for most real cells; (b) **resource null** (SEQUENCE_DEFAULT, IfcSpace,
IfcBuildingElementProxy) — §CREW_DEMAND itself SKIPS the `_DEFAULT` bucket (`if (!_cdr) continue`);
(c) **what lag `"levels":1` materialises as** — asked by §S27.R R4, still unanswered; (d)
**calendar** (toWall/toProductive, shift hours) unstated — M1's own trap; (e) the formula grants
each cell the FULL crew pool while the live engine's §S6_CREW_PASS (`cpm_schedule.js:398-420`)
serializes elements onto ONE project-wide slot pool per resource — same-resource cells overlapped
by template offsets (PLUMBER at seq 7 level N+1 vs seq 9 level N, CONCRETE_GANG at seqs 1/4/6)
overcommit capacity with no check, and Lane B runs no crewViol judge; (f) the planner's own
programme PIPELINES trades within a level — Framing Zone A starts after Columns Zone **B** (link
verified), while Columns Zone C is still running — which strict serial ascending-seq cannot
express, so STOP D's makespan will run structurally long against the one real anchor.

**R6 — CONDITION, §S28.3 REPLACED A TAUTOLOGY WITH NO GATE AT ALL, and S1b's number is wrong.**
S1a/S1b/S1c are each "report the number" — none has a pass/fail bound, so none can FAIL in the stop
condition sense; with S2's CELL_MAX_FRAC withdrawn, NOTHING gates a pathological grid (a banding
bug putting 90% of a building in one cell passes silently, reported at best). Acceptable only if
declared: Lane B v1 has no hard gates, human reviews the three reports. And S1b's baseline
"§S26.5 measured 16.3-16.7%" fails re-derivation twice: §S26.5 contains no bandSpan measurement
(dangling citation), and the fleet range is actually **8.8-25.0%** (Terminal 8.8, LTU 12.5,
Hospital 16.3, Clinic 16.7, Duplex 16.7, JKR 22.1, HHS 25.0) — "16.3-16.7%" is a three-building
coincidence quoted as the fleet band.

**R7 — CONDITION, THE DEFAULT TEMPLATE STILL DOES NOT EXIST AT SEQ GRAIN.** §S28.1 says "a
template names its trains by seq range" but never writes the default template's ranges, and the
only concrete template in the spec (§S27.5's JSON) still names PHASE trains. Contiguous seq RANGES
cannot express Architecture {5,6,8} straddling MEP Rough-in {7} — the trains must be re-cut at seq
boundaries (e.g. [1],[2-4],[5-6],[7],[8],[9],[10-11]) with offsets redefined between THOSE, and
the phase display labels straddling train boundaries acknowledged. Until the default template JSON
is written into the spec, §S27.R R1's "a builder would invent" verdict still stands for §S28.4's
"levels run per the template offset" and all of B4.

**R8 — CONDITION, §S28.5's remaining facts need three corrections.** (a) Fact 1: the hierarchy is
**DISCIPLINE → LEVEL → WORKTYPE → ZONE** — the roots are `Structures`, `Architecture`, `Site
Works`; levels sit UNDER the Structures train (and substructure worktypes sit directly under it
with no level node). That top discipline layer actually strengthens the trains model — say it.
(b) Fact 5 ("the planner zoned STRUCTURE, not architecture") over-reads the data: the Architecture
root's 7 child tasks are **unnamed ($), undated, unsequenced stubs** — the programme does not
cover architecture at all, so no zoning CHOICE about architecture can be inferred from it. (c) The
sketch omits `Site Works > Site Excavation` (dated, and the true programme start:
Site Excavation → Piles Zone A), and the partial-zone levels (L6 Framing has ONLY Zone B, L6b only
Zone C, L4 no Columns) — the planner's grid has holes, which B3's cell model should expect rather
than "fix".

**R9 — NOTE, LANE INDEPENDENCE (§S28.0) IS FALSE IN BOTH DIRECTIONS, in ways R1/R2 imply but the
spec must state.** A→B: Lane A's sort key names `bandRank`, §S28.2 assigns building `bandRankOf()`
to Lane B (B1) and forbids both lanes the existing name-keyed `_bandRank` — so Lane A either waits
on B1, duplicates it (the DO-NOT-REMOVE header's named defect), or silently uses the C2 junk
ladder. B→A: R2's `_cap` adoption — if B5 lands first, every live building displays Lane B's
captured overlay and Lane A's engine changes become invisible in the product until the adoption
policy exists. The `designatedSupport` twins are NOT a coupling here (neither lane edits them;
witness slices `_designatedSupport` from time_machine source text at witness:127, verified), and
`witness_midair_zero.js` is Lane-A-only (does not read tasks tables, verified). **Amend §S28.0 to
"independent once B1 is extracted as a shared, lane-neutral prerequisite and R2's adoption policy
is decided" — as written, "neither blocks the other" is untrue.**

### §S28.R.2 — the six questions, answered directly

1. **R1-R4 fixed?** R1: order fix REAL (seq table verified exact) but template half missing (R7)
   and the evidence base is phase-ranked (R1 above). R2: FIXED (build-new is right; `_bandRank`
   re-verified as name-keyed). R3: tautology removed, replaced by gate-free reports with one wrong
   number (R6). R4: formula + units citation REAL and exact; the five semantic questions §S27.R R4
   actually asked remain open (R5).
2. **§S28.5 re-derived:** counts exact; zones spatial (proven with coordinates, which §S28.5 never
   did); zones sequential FS ✓; hierarchy needs the discipline root (R8a); **reversal FALSE —
   file-order artifact, links and dates both A→B→C** (R3); "zoned structure not architecture" —
   architecture is an empty stub, programme covers structure+site only (R8b).
3. **Stop conditions:** S1a-c can produce surprising numbers but cannot FAIL — no bounds (R6);
   Hospital 233 verified exact (plus 43/41/3/0/0/0); withdrawing S2 leaves NO gate on cell
   concentration anywhere in Lane B.
4. **§S28.4:** citation and units history verbatim-correct; the formula is §CREW_DEMAND's
   arithmetic at cell grain, but §CREW_DEMAND is a REPORTING block — the engine's actual allocator
   is a global serial slot pool (§S6_CREW_PASS), which the formula ignores along with
   multi-resource/null-resource cells and the calendar (R5).
5. **Lane A guard:** the C1 guard fixes support VISIBILITY, not the CLOCK — a support that sorts
   later has no finish time to `max()` against, so "reported and counted" is not float parity; the
   partial-grid problem survives as a partial-clock problem, made systematic for hang carriers by
   the band-major key, and the "as before 0fe8eb2" precedent claim is factually wrong (R1).
6. **Independence:** false both directions (R9); Lane B's "cannot regress a witness" is true of
   the witness and false of the live viewer (R2).

### §S28.R.3 — what this review did NOT check

§S26.14's after-column numbers (still not re-run); the probe's §S26.5 violation table beyond the
two buildings §S27.R already reproduced; whether `rates.js`'s hardcoded tables and the JSON mirror
are in sync today (R10's citation point stands unfixed — §S28.1 again cites the mirror);
`compile_rooms.py` separability beyond §S27.R.3's function-boundary audit (B2 rightly carries that
as its own gate); LTU's 32×-density perf ceiling; and every construction-practice claim, which
remains planner-unverified except where Hospital's own programme now speaks (R3/R5f/R8).

### §S28.R.4 — what must change before a build agent is dispatched

Same treatment §S27 got: amendments first, re-vet cheap after. (1) R1 — state Lane A's real key
and re-measure the violation table with it, or adopt the probe's key and say what that does to
"seq is the single normative order" and to "no graph"; fix the 0fe8eb2 precedent sentence.
(2) R2 — write the adoption policy for B5 (drive the viewer, or be invisible to `_cap` — named
mechanism either way) and the patch+loader distribution note. (3) R4 — one instrument-named
baseline table + the W-MZ-8 Duplex relock decision. (4) R3/R8 — correct §S28.5 (delete fact 4,
fix facts 1/5, add Site Works + partial zones + label-direction flip) and §S28.8's serpentine
line. (5) R7 — write the default template JSON at seq grain. (6) R5 — one paragraph each:
multi-resource/null-resource crews, offset lag semantics, calendar, and the named decision that
v1 ignores cross-cell crew contention (with R6's "Lane B has no hard gates" declaration made
explicit). **If only one thing is fixed first, fix R1: Lane A is the cheap falsifiable experiment
this spec's whole shape argues for, and as written it would be run with a key nobody measured,
scored by an instrument that is currently red, against a baseline quoted from a different
instrument.**

---
# §S29 — GENERALITY AUDIT: the grid is fitted to 7 multi-storey buildings (2026-08-19)

**Standing: PROPOSED.** Written on the user's directive — *"look out for omissions, gaps in the
model, comparing with what was done onset that was OK, and what is done by others… It has to be
fundamentally handle general cases not particular."* Applies to §S28 and, where noted, to the live
engine as well. Held out of the file until §S28.R lands (concurrent-edit discipline).

## §S29.0 — the test set is not a test set

All 7 fleet buildings are **multi-storey buildings with named storeys, one building each,
new-build, vertical**:

| building | n | storeys | buildings |
|---|---|---|---|
| Terminal_meta | 48,428 | 23 | 1 (`TerminalMerged`) |
| Hospital_meta | 63,415 | 9 | 1 |
| Clinic_meta | 16,114 | 8 | 1 |
| LTU_AHouse_meta | 125,698 | 19 | **no `building` column at all** |
| JKR_extracted | 9,410 | 21 | 1 |
| HHS_Office_Federated | 6,880 | 5 | 1 (despite the name) |
| Duplex_extracted | 1,193 | 5 | 1 |

Every design decision in §S25/§S26/§S27/§S28 was validated against this one shape. **A spec that
passes 7/7 here has been shown to work on one building type.**

## §S29.1 — THE CORE FINDING: the grid needs 4 inputs where the onset needed 2

**Onset (pre-`0fe8eb2`, `schedule_gate.js:248-268`):**
```
struct.sort((base_z, seq)) · nonst.sort((seq, rank, base_z))
start = max(geoGate(el), crewSlot)
```
Required inputs: **`base_z` and `seq`. Two numbers per element.** No storeys, no bands, no zones,
no rooms, no phases, no template. It produces *some* defensible order on any model with geometry
and a class — a warehouse, a bridge, a fit-out, a model with no storey data at all.

**§S28's grid:** `bandRank` (needs meaningful z-stratification) × `zone` (needs a plan raster with
separable components) × `seq` (needs `sequence_rules.json` to cover the model's classes) ×
`template` (needs trades matching the model's actual content). **Four inputs, each of which can be
absent, degenerate, or meaningless.**

**This is a generality REGRESSION versus the thing being replaced, and the spec does not say so.**

**And the failure modes differ in visibility, which is worse than the input count:**
- Onset failed **loudly** — elements float, and `auditFloating` counts them.
- The grid fails **silently** — a degenerate grid produces ONE BIG BAR, and one bar looks like a
  schedule. Nothing in §S28 detects it.

**STOP-AND-REPORT: §S28 needs a DEGENERACY DETECTOR before it needs anything else** —
`cells / elements` ratio, and a hard report when any level yields 1 zone AND 1 trade, or when
`distinct(cells) < 4`. A schedule with fewer cells than trades is not a schedule.

## §S29.2 — `band = floor(z/3)` assumes VERTICAL is the location axis. That is the deepest
particular assumption in the whole design.

A road, bridge, tunnel, jetty, or rail alignment stratifies along a **horizontal chainage**, not
elevation. Their locations are `CH 0+000 → 0+250 → 0+500`. There are no storeys, and z is roughly
constant.

**This is not hypothetical for this project:** `prompts/INFRA_ROAD_RAIL_GAP_CLOSURE.md` exists in
this repo (2026-08-19 working set), and IFC 4.3 ships `IfcFacility`/`IfcBridge`/`IfcRoad`/
`IfcRailway` precisely for it. **A z-band grid cannot schedule a road at all** — every element
lands in one band, the grid collapses to trades, and §S29.1's silent failure fires.

**The general form:** the location axis is **the direction the work progresses along**, which is
vertical for buildings and longitudinal for infrastructure. `floor(z/3)` is one instance of
`floor(projection onto the progression axis / quantum)`.

## §S29.3 — what others do about generality: the LBS is DATA with VARIABLE DEPTH, not a derivation

LBMS's answer (§S26.10) is a **Location Breakdown Structure** that the planner defines, typically
`Site → Building → Section → Floor → Zone → Room`, with each project choosing its own depth. That
single mechanism covers a hospital, a campus and a bridge because the *structure* is an input.

**§S28 hard-codes depth at 2 or 3** (`level × trade`, or `level × zone × trade`). Every generality
question then becomes a special case instead of a parameter:

| project | LBS the field would use | what §S28 gives |
|---|---|---|
| single-storey warehouse | `Building → Zone` | 1 band → trades only |
| hospital | `Building → Floor → Zone` | fits |
| 3-building campus | `Site → Building → Floor → Zone` | **§S29.4 — broken** |
| road | `Route → Chainage` | **§S29.2 — impossible** |
| fit-out | `Floor → Room` | rooms unused (§S27.3) |

**RECOMMENDATION: make the LBS a variable-depth list of AXES, each with its own key function**, and
let the shipped default be `[building, band(z,3m), zone]`. Then infrastructure is a different axis
list, not a different engine — the same move §S28.5's template made for trade order.

## §S29.4 — VERIFIED GAP: multi-building federation merges across buildings

`elements_meta` carries a `building` column — **and LTU_AHouse_meta has no such column at all**,
so the schema is not uniform across the shipped fleet. All 7 fleet buildings report exactly ONE
building, so **this path has never been exercised.**

On a federated campus model, Building A's Level 3 and Building B's Level 3 sit at the same
elevation → the same band → **the same cell → scheduled as one activity**. Two separate structures,
one bar, no way to sequence one before the other.

Federation is not an edge case — it is what BIM coordination *is*, and `HHS_Office_Federated` is
literally named for it. **`building` must be the outermost LBS axis, above band** (§S29.3), and
§S28 does not mention it.

## §S29.5 — `SEQUENCE_DEFAULT` makes an unknown model collapse to one trade, silently

`viewer/rates/sequence_rules.json`: `SEQUENCE_DEFAULT = {phase: "Architecture", sequence: 6}`.

Any class the rules do not cover becomes seq 6. A model dominated by unmatched classes — a process
plant (`IfcTank`, `IfcChimney`, `IfcDistributionChamberElement`), an infrastructure model, a
proprietary export — yields **one seq for the whole building → one cell per zone → one bar.**
Again silent, again §S29.1.

**STOP-AND-REPORT: report `unmatchedClassRate` per building at Lane B's B3 step.** It is currently
measured nowhere, and it is the single number that tells you whether trade order means anything on
a given model.

## §S29.6 — omissions shared by BOTH the onset and the grid (neither is a regression; both are gaps)

- **Demolition / refurbishment.** All 7 buildings are new-build. Refurb inverts the order —
  top-down strip-out before anything ascends. An ascending-only grid cannot express it, and neither
  could the onset sort.
- **Calendar.** `IfcWorkCalendar` is captured by PR #59 (`2253664`) and consumed by nothing.
  Durations are raw days: no weekends, no public holidays, no monsoon shutdown. Real programmes are
  quoted in working days.
- **Procurement / lead time.** Many real programmes are lead-time-driven (structural steel ordered
  12 weeks ahead), not physics- or crew-driven. Neither design has any representation for a
  constraint that is not a physical or resource one.
- **Site and external works.** Roads, drainage, landscaping, temporary works — no storey, often no
  z-stratification. Same shape as §S29.2.
- **Serpentine crew flow.** §S28.8 names it as unmodelled. The GENERAL principle is "the crew's next
  location is the nearest unfinished one", not "reverse alternate levels" — worth stating that way
  so it is not implemented as a Hospital-specific hack.

## §S29.7 — what to change in §S28, ranked

1. **Add the degeneracy detector (§S29.1).** Cheapest, catches the whole silent-failure class, and
   is needed regardless of every other item here.
2. **Make the LBS a variable-depth axis list (§S29.3), with `building` outermost (§S29.4).**
   Default `[building, band(z,3m), zone]` reproduces §S28 exactly on all 7 fleet buildings, so it
   costs nothing today and is the only item that makes infrastructure reachable later.
3. **Report `unmatchedClassRate` (§S29.5).**
4. **State the progression axis explicitly (§S29.2)** even if v1 only implements vertical — so the
   assumption is visible rather than buried in `floor(z/3)`.
5. **Name demolition, calendar, procurement, external works as OUT (§S29.6)** rather than
   unmentioned. §S28.8 currently implies the gap list is complete and it is not.

## §S29.8 — the honest summary

§S28 is a good design **for multi-storey new-build buildings with usable storey data and covered
IFC classes** — which is the fleet, and plausibly most of the addressable market. It is not
fundamental in the sense the user asked for: three of its four inputs (band, zone, template) are
undefined or degenerate outside that shape, and it fails silently rather than loudly when they are.

**Items 1-3 of §S29.7 close the silent-failure class and cost almost nothing on the current fleet.
Item 2 is the one that decides whether this design generalises or gets rewritten when the first
road model arrives.**

---
# §S30 — THE MEASUREMENT BOTH VETTING PASSES DEMANDED (2026-08-19)

`scripts/probe_s30_sortkey.js`. STUDY ONLY, `viewer/` unchanged. §S27.R and §S28.R independently
found the same hole: **Lane A's literal sort key had never been run.** `probe_s26_rank_monotone.js`
measures `(bandRank, phaseRank, depth, bz, guid)` WITH host/carrier band inheritance — it contains
the phase order §S28.1 outlawed plus two components Lane A drops. Every Lane A number was inherited
from an instrument that does not measure Lane A. This runs the literal key.

**Method:** order by the key; clock = `start(e) = max(end of every already-placed real support,
crew slot)` — the pre-`0fe8eb2` architecture with §S26.6's C1 guard (GLOBAL scan, a support that
sorts later is COUNTED not silently skipped); score with the SHIPPED judge called the working way,
`ScheduleGate.auditFloating(keep, schedMap)` on ORIGINAL elements. Baseline = live CPM, same run.

**Instrument guards, because this session produced two false zeros before getting it right:**
`durOk=true` and `judgeCanFail=true` printed on every line. The first false zero passed remapped
objects with `bz`/`tz` where the judge reads `base_z`/`top_z` — every predicate saw `undefined`,
`auditFloating(keep,m)=247` vs `auditFloating(items,m)=0` on the SAME schedule. The second omitted
`s`/`e`, so every duration was 0 and nothing could start before a zero-length thing finished. Both
returned a clean 100% correct on 7/7. **A green number here is worthless until the instrument has
been shown to go red.**

## §S30.1 — RESULT: `KEY=(bandRank, seq, base_z, guid)` — the literal §S28.7 key

| building | n | float CPM → SORT | | makespan | backward-support elements |
|---|---|---|---|---|---|
| LTU_AHouse | 122,330 | 12,712 → **2,752** | −78% | 749d → 844d | 59.5% |
| Terminal | 48,428 | 4,756 → **1,722** | −64% | 85d → 100d | 21.0% |
| Hospital | 63,182 | 7,753 → **3,136** | −60% | 264d → 352d | 61.9% |
| Clinic | 16,071 | 1,102 → **552** | −50% | 96d → 108d | 52.8% |
| JKR | 8,985 | 3,183 → **1,925** | −40% | 17d → 29d | 65.5% |
| Duplex | 1,119 | 247 → 257 | +10 ⚠ | 8d → 8d | 58.4% |
| HHS_Office | 6,839 | 1,531 → **2,103** | +572 ⚠ | 29d → 37d | 48.5% |

**`floatNoWorseThanCPM = 5/7.`** Not the clean pass Lane A assumed, not the failure either.

## §S30.2 — `KEY=(bandRank, base_z, seq)` is WORSE, and that overturns the stated intuition

| building | float CPM → SORT | |
|---|---|---|
| Terminal | 4,756 → 3,141 | PASS |
| JKR | 3,183 → 2,218 | PASS |
| Hospital | 7,753 → 8,147 | +394 |
| Duplex | 247 → 368 | +121 |
| HHS_Office | 1,531 → 2,337 | +806 |
| Clinic | 1,102 → 3,863 | **+2,761** |
| LTU_AHouse | 12,712 → 23,085 | **+10,373** |

**`floatNoWorseThanCPM = 2/7.`** Ordering by elevation *before* trade — the physics-respecting key,
the one this file's own reasoning kept reaching for — is **substantially worse** than trade-first.
Clinic nearly quadruples. That is a measured refutation of an assumption stated repeatedly in
§S26–§S29, including by this author, and it was never tested until now.

## §S30.3 — the finding that matters more than either verdict

**Backward supports are not a residual. They are the norm: 21.0–65.5% of elements have at least
one support that sorts after them.** Raw counts run 2,015 (Duplex) to 1,146,641 (LTU).

§S26.6's C1 guard says such a support is "reported and counted, never silently skipped" — and it
is. But §S28.7 presented C1 as the thing that makes a sort safe. **It does not make it safe; it
makes the damage visible.** A support that sorts later has no finish time to `max()` against, so
the dependent starts early regardless. §S28.R said exactly this ("the C1 guard fixes support
*visibility*, not the *clock*") and it is now measured: correct, and larger than anyone assumed.

**⚠ Confound, named rather than resolved: the float gains may be partly bought with delay.**
Makespan inflates on every building — Hospital +34%, JKR +77%, LTU +13%. Crew queueing pushes
starts later, which incidentally covers gates the sort failed to enforce. The correlation is NOT
clean (Duplex makespan flat yet float worse; HHS makespan +29% and float worse), so delay is not
the whole story — but no run here separates "ordered correctly" from "delayed enough to look
ordered." **Until that separation is measured, §S30.1's 5/7 must not be quoted as ordering quality.**

## §S30.4 — what this settles, and what it does not

**Settles:**
- The literal Lane A key beats live CPM on float on 5/7 buildings, by 40–78% where it wins. The
  sort-based direction is NOT dead — the outcome §S28.R correctly refused to assume either way.
- Trade-first beats elevation-first, decisively (5/7 vs 2/7). The opposite was assumed throughout.
- A pure sort does not eliminate backward supports. At 21–65% it is the dominant behaviour, not an
  edge case, so any design claiming a sort makes physics safe is wrong as stated.

**Does not settle:**
- Whether the float win is ordering or queueing (§S30.3 confound). **This is the next measurement
  and it is cheap:** re-run with crew caps lifted, so delay cannot mask a missing gate.
- Why HHS_Office and Duplex regress while the other five improve. Both are small; not diagnosed.
- Anything about phase-gap, zones, templates, or the IFC-native tables. Untouched here.

## §S30.5 — STOP-AND-REPORT

- **Do NOT start Lane A on this result.** 5/7 with an unseparated confound and 21–65% backward
  supports is a reason to run one more probe, not to change `viewer/`.
- **§S28.7's C1 claim must be restated** before any build: C1 makes backward supports *visible*, it
  does not make a sort *safe*. The current wording implies float parity follows from C1. It does not.
- **Any future number from this lane prints `durOk` and `judgeCanFail`.** Two false zeros in one
  session, plus §S25_REVIEW's `engineGap` tautology three weeks earlier, is three instances of the
  same class. It is the most reliable failure mode in this file.
---
# §S31 — THE EXTRACTION CHAIN: what is actually lost, and where (2026-08-19)

**Recorded late, and that is the finding to note first.** Everything below was measured across
2026-08-19 and existed only in conversation for several hours while `§S29` sat unwritten in a
scratchpad. The user's correction — *"this is repetition! already done a few posts ago. Why don't
u record what was done?"* — is upheld: re-running a measurement because the first result was never
written down is the same waste this file's `⚠ WATCHDOG` header exists to prevent. **A measurement
that is not in this file did not happen.**

## §S31.1 — §S30.6 RESULT: the crew confound, separated (the run §S30.4 called for)

`NOCREW=1` lifts capacity on BOTH engines, so queueing delay cannot mask a gate the sort failed to
enforce. Whatever float survives is ORDERING, not waiting.

| building | float CPM | float SORT | sort better by |
|---|---|---|---|
| Terminal | 38,722 | **3,052** | 12.7× |
| LTU_AHouse | 56,128 | **15,630** | 3.6× |
| Hospital | 18,594 | **5,908** | 3.1× |
| Clinic | 7,398 | **3,242** | 2.3× |
| JKR | 4,530 | **2,262** | 2.0× |
| Duplex | 632 | **324** | 2.0× |
| HHS_Office | 3,999 | **3,048** | 1.3× |

**`floatNoWorseThanCPM = 7/7`** (was 5/7 with caps on — Duplex and HHS_Office both flip to PASS).

**The confound resolved AGAINST the live engine, not against the sort.** Comparing each engine to
its own capped run: CPM 4,756 → 38,722 on Terminal (**8× worse** once queueing stops covering for
it); the sort 1,722 → 3,052 (under 2×). **The live engine's float number is substantially an
artifact of crew delay masking bad ordering.**

Mechanism, visible in makespan: with caps lifted CPM finishes Terminal in **0.6 days** — nearly
everything starting at once. That is the blob. The graph collapses to one component, contraction
gives the whole component a shared start, and every dependency inside it evaporates. The sort takes
4.6 days because it actually waits for supports. **The 49,436-element blob was not merely
scrambling phase order; it was destroying the dependency chain outright.**

`KEY=zfirst` under NOCREW **also reaches 7/7** (run completed 2026-08-19): Terminal 3,144 ·
Hospital 8,124 · Clinic 3,863 · LTU 24,166 · Duplex 368 · HHS_Office 2,337 · JKR 2,228 — every one
better than CPM. So the seqfirst-vs-zfirst gap of §S30.2 is entirely a CAPPED-crew phenomenon:

| condition | seqfirst | zfirst |
|---|---|---|
| crew caps ON (shipping condition) | **5/7** | **2/7** |
| crew caps LIFTED | **7/7** | **7/7** |

**§S30.2's "trade-first decisively beats elevation-first" is hereby QUALIFIED and must not be
quoted bare.** Both keys beat the live engine on ordering alone. Trade-first only pulls ahead once
finite crews are in play — i.e. the advantage is in how the two keys INTERACT WITH CREW QUEUEING,
not in ordering quality. seqfirst groups same-trade work together, so a capped crew pool drains in
one place instead of thrashing between trades; zfirst interleaves trades within a band and makes
every crew queue longer. That is a resource-levelling property, and it is a better reason to prefer
trade-first than the one §S30.2 gave — but it also means neither key is the "physically correct"
order the earlier sections kept reaching for.

One outlier to keep visible: zfirst's LTU makespan is **125.3d** vs seqfirst's 113.3d and CPM's
1.2d, on the same uncapped run. A 100× spread between engines on one building is not explained here.

## §S31.2 — VERIFIED: the extractor is NOT broken; the shipped DBs are stale

User challenge: *"since when is our IFC to DB extraction broken? I need to be convinced as it was
working very well."* Upheld — the "broken extractor" framing (raised by an outside review) is wrong.

`DAGCompiler/python/extractIFCtoDB.py` already declares the relation schema said to be missing:

```sql
CREATE TABLE rel_fills_host (...)    -- IfcRelVoidsElement ∘ IfcRelFillsElement, "recovered
                                     -- verbatim… NON-INVENT, we copy authored relations"
CREATE TABLE port_elements (...)     -- IfcRelConnectsPorts
CREATE TABLE port_connections (...)
CREATE TABLE rel_adjacency / rel_anchored / datum_plane / rel_aggregates / rel_contained_in_space
```

The browser importer reads VOIDS/FILLS into `bom_tree` (`import_worker.js:315,328`) and has since
the 2026-05-23 initial migration. **Both extractors preserve these relations.**

The shipped `buildings/*.db` predate that work and were never rebuilt. `JKR_extracted.db`:
`project_name=jkr_aligned`, `import_date=2026-07-11`, browser-built (`component_geometries`, not
`base_geometries`), **no `bom_tree` table, no `elevation` column**. So every "relation table is
empty" observation is a STALE ARTIFACT, not a lossy pipeline.

**Two corrections to the outside review, both measured:**
- *"`IfcRelSequence` present in source, 0 rows in DB"* — **fabricated.** All 7 JKR discipline files
  grep to `IFCRELSEQUENCE:0`. Nothing was dropped; JKR has no programme. Only `Hospital 2.0.ifc`
  carries one (43 links).
- *"`IfcRelContainedInSpatialStructure` 5+22 in source but 107 in DB — synthesized?"* — 50 across
  all 7 files, and the extractor composes containment from several sources. Not an inflation.

**Merge unblocked, 2026-08-19 (`86b059df2`):** the `extractIFCtoDB.py` conflict with `origin/master`
that blocked the docs deploy was **97% line-ending noise** — master flipped the file to CRLF, this
branch is LF, so all 3,113 lines read as changed. Normalising both sides to LF and re-running the
3-way merge against the merge-base gives **zero conflicting hunks**; the two changes are additive.
Resolution keeps both: master's §S21 (`spatial_structure.elevation` + real `IfcBuilding` parentage
via `.Decomposes`) and this branch's §KUL001 (`elements_meta.building` + `project_metadata`).
3,113/3,118 → 3,151 lines, LF throughout, `py_compile` passes.

## §S31.3 — ⛔ RETRACTED (2026-08-19, same day, by measurement) — the storey-Name claim was WRONG

**This section originally asserted that `extractIFCtoDB.py:499` `return container.Name` was a
one-line defect producing Clinic's three-elevation `"Roof - Main"`, Terminal's 73 storeys, and the
whole label-parsing problem. That is wrong on two counts and is retracted in full.** It is left here
rather than deleted because it was committed to this file and acted on in conversation.

**Wrong file.** Every shipped DB is BROWSER-built — they carry `component_geometries` (the browser
importer's table), not `base_geometries` (the Python CLI's). `extractIFCtoDB.py` produced none of
them. The equivalent browser path is `import_worker.js:284-300`, which is also name-keyed
(`storeyMap[expressID] = normalizeStorey(s.Name)`).

**Wrong phenomenon.** `Clinic.ifc` declares **exactly one** `Roof - Main` storey:
`TOF Footing · Second Floor · Roof - Mech · Roof - Main · Level 2 · Level 1 · First Floor · Unknown`.
No suffixed siblings, nothing ambiguous, nothing collapsed. The "same name at three elevations"
observation came from the §S25 PROTOTYPE's own level ladder (`Level 2=Roof - Main`,
`Roof - Main=Second Floor`, `Roof - Main`), which merges bodies by median datum — **an artifact of
the probe's clustering, not of extraction.** §S26.6 C2 inherited the same mistake and is qualified
by this retraction.

**Also mislabelled:** `import_worker.js:273-282` `§STOREY_NORMALIZE` folds `"Level 2 Ceiling"` ->
`"Level 2"` DELIBERATELY, because Revit exports reference planes as `IfcBuildingStorey`. That is
intentional and correct, and the retracted claim would have flagged it as the bug.

**What IS true, measured on `Clinic_meta.db`** — per-storey element z spread:

| storey | n | z span |
|---|---|---|
| **Unknown** | **5,160** | **13.9m** |
| Second Floor | 1,708 | 10.1m |
| Level 2 | 1,410 | 9.9m |
| First Floor | 2,343 | 9.0m |
| TOF Footing | 1,676 | 4.2m |
| Roof - Main | 45 | 7.9m |

**The real finding is the top row: 5,160 of 16,114 elements (32%) resolve to storey `Unknown`** —
a containment gap, far larger than any naming issue. The wide spans on the others are mostly tall
elements legitimately reaching the level above and are NOT a defect.

**Name-keyed storey mapping remains a LATENT weakness in both importers. It is not the cause of
anything observed on this fleet.** Do not act on it.

## §S31.4 — MEASURED: how much of the elevation is already recoverable TODAY

User challenge: *"isn't the room injection already given us such meta-data?"* — **largely yes.**
`spatial_structure` already carries a real per-storey `center_z`, written by the compile/injection
path (`object_type='COMPILED'`). Hospital's storey ladder is clean: 168.7 / 174.1 / 178.8 / 183.9 /
188.8 — a 5m rhythm, extracted not guessed.

Federation duplicates AGREE: Hospital has 63 storey rows over 20 distinct names, `"Level 5"`
appearing **8 times** (8 discipline files), and **all 8 report the same z**. Storey guids are
synthetic and name-derived (`STC_Level_1`, `STC_First_Floor`), so the join key available today is
the name.

**Joinability of `elements_meta.storey` → a `spatial_structure` storey with a real `center_z`:**

| building | elements | joinable | | ambiguous names |
|---|---|---|---|---|
| Hospital_meta | 63,415 | 53,740 | **84.7%** | 0 |
| HHS_Office_Federated | 6,880 | 4,715 | **68.5%** | 0 |
| Clinic_meta | 16,114 | 5,755 | **35.7%** | 0 |
| Terminal_meta | 48,428 | 11,233 | **23.2%** | 0 |
| JKR_extracted | 9,410 | 2,017 | **21.4%** | 0 |
| LTU_AHouse_meta | 125,698 | — | **no `center_z` column** | — |
| Duplex_extracted | 1,193 | — | **no `spatial_structure` table** | — |

**`ambiguousNames = 0` on every building that has the data** — no storey name maps to two different
elevations. So where the join resolves, it is SAFE, and no extractor change is needed to use it.

**But coverage is 21–85% and two buildings cannot join at all** — the same stale-artifact pattern as
§S31.2 (LTU and Duplex predate the schema). So room injection gives us most of what is needed on
the newest DBs and nothing on the oldest.

## §S31.5 — the ordered conclusion

1. **Elevation does not need to be inferred.** It is declared, and on Hospital it is already
   joinable for 84.7% of elements with zero ambiguity. `floor(z/3)` banding is a workaround for a
   join nobody wired.
2. **The remaining gap is coverage, not capability** — old DBs lack the columns. Rebuilding with the
   now-merged extractor (§S31.2) is the fix, and it is a re-run, not new code.
3. **The extractor change worth making is small:** return the storey GUID alongside the Name, so the
   join is on identity rather than a display string. Master's §S21 already landed the other half
   (`spatial_structure.elevation`).
4. **None of this is scheduled work yet.** No spec has passed vetting (§S27.R, §S28.R both NOT
   VETTED), and §S31 records measurements, not a design.

## §S31.6 — STOP-AND-REPORT

- **Record before re-measuring.** This section exists because several hours of findings lived only
  in chat and one audit (§S29) sat unwritten in a scratchpad while adjacent measurements were
  re-run. Write the number into this file in the same turn it is produced.
- **Do NOT quote §S30.2's "trade-first beats elevation-first" unqualified.** §S31.1 shows it is a
  capped-crew result; both keys beat CPM without caps.
- **Do NOT rebuild the fleet DBs and re-measure §S26–§S30 in one step.** Rebuild ONE building
  (Hospital — highest join coverage, newest schema), confirm `rel_fills_host` / `elevation` / storey
  guids populate, and re-measure that building alone before touching the rest.

---

# §S32 — USER RULING: the extractor is correct, the DBs are frozen, metadata is derived at RUNTIME (2026-08-19)

**USER DIRECTIVE, verbatim:** *"i maintained that the script is correct, and no further tamper on
the DBs. All subsequent metadata must be derived runtime 1 time ie rooms injection."*

**This is a standing architectural constraint, not a preference.** It settles several threads this
file left open and it CANCELS work that earlier sections proposed. Read it before acting on any
earlier recommendation.

## §S32.1 — the three rules

1. **`extractIFCtoDB.py` (and the browser importer) are CORRECT. Do not change them.** The extractor
   already preserves what matters — §S31.2 verified `rel_fills_host`, `port_elements`,
   `port_connections`, `rel_adjacency`, `rel_anchored`, `rel_aggregates`,
   `rel_contained_in_space`. §S31.3's proposed "return the storey GUID" change is **retracted along
   with the claim that motivated it.**
2. **The shipped `buildings/*.db` are FROZEN. Do not rebuild, migrate, or re-import them** to make a
   measurement come out differently. Their contents are the input, whatever state they are in.
3. **Every derived fact is computed at RUNTIME, ONCE, on load** — the room-injection pattern that
   already exists (`scripts/compile_rooms.py` / `build/room_walker.js` /
   `viewer/lib/room_walker.js`): read the frozen DB, compute, hold in memory for the session.

## §S32.2 — what this CANCELS

| earlier item | status under §S32 |
|---|---|
| §S31.5 item 3 — "return the storey GUID alongside the Name" | ⛔ **CANCELLED** — extractor is correct, and §S31.3 is retracted anyway |
| §S31.5 item 2 — "rebuild with the merged extractor" | ⛔ **CANCELLED** — DBs are frozen |
| §S31.6 — "rebuild ONE building (Hospital) and re-measure" | ⛔ **CANCELLED** — same reason |
| §S27.2 / §S28.6 B2 — zone compiler as a build stage writing to the DB | ⛔ **CANCELLED as written** — becomes a runtime derivation, writes nothing |
| §S28.6 B5 — write `schedules`/`tasks`/`task_sequences` into the DB | ⚠ **BLOCKED pending a ruling** — this is a DB write. §S32 forbids tampering; whether the IFC-native tables are "tamper" or "the intended output surface" is a USER decision, not one to assume |
| §S26.12 / §S31.2's "stale artifacts" framing | ⚠ **REFRAMED** — the DBs are not "stale pending rebuild", they are the FROZEN INPUT. Anything missing from them is derived at runtime or not used |

## §S32.3 — what this makes CLEANER

The ruling removes the hardest unsolved problem in §S27/§S28 rather than solving it.

- **No migration, no schema change, no re-extraction, no version skew.** The 5 empty / 2 absent task
  tables, LTU's missing `center_z`, Duplex's missing `spatial_structure` — none of these need fixing
  upstream. The runtime layer computes what it needs from geometry that IS present.
- **One derivation pass, one place.** Storey elevation, band rank, zones, and the 32% `Unknown`
  containment gap (§S31.3) all become outputs of the same runtime pass, not four separate lookups
  with four different coverage rates (§S31.4's 21-85% joinability stops mattering — the pass derives
  it uniformly for 100%).
- **§S29's generality problem shrinks.** A runtime derivation can degrade per building without a
  schema migration: no `spatial_structure`? derive from element z. No rooms? one zone per band.
  The variable-depth LBS §S29.3 asked for becomes a runtime choice, not a stored structure.
- **It matches what already works.** Room injection is the proven precedent and it is already
  hardened (`§RASTER-EPS` translation invariance, `§STAIR-EXCLUDE`, `§SUSPECT-LARGE`).

## §S32.4 — the derivation contract (what a runtime pass must satisfy)

Any runtime derivation added under this ruling MUST:

- **Read only.** No `INSERT`/`UPDATE`/`CREATE` against `buildings/*.db`. Results live in memory for
  the session.
- **Run once per load**, not per query — the room-injection cadence.
- **Be total.** Every scheduled element gets a value, including the 32% with storey `Unknown` and
  the elements in buildings with no `spatial_structure` at all. A derivation that covers a subset
  and silently defaults the rest is the §S29.1 silent-failure class and is not acceptable.
- **Report its own coverage and its fallbacks**, per building, with `§`-tagged log lines — how many
  elements got a declared value, how many a derived one, how many a default.
- **Print `durOk` / `judgeCanFail`-style instrument guards** on any number it produces (§STATUS).

## §S32.6 — CLARIFICATION (user, 2026-08-19): "frozen" is about WHICH TABLES, not which file

Rule 2 was being read too widely and blocked the product goal for a day. Two readings were tried;
**the second is the ruling.**

**First attempt (mine, superseded):** "frozen = the shipped file; edits go to a browser-local copy."
**The user corrected it, and the correction is sharper:** the separation is not WHERE the file
lives — it is **WHICH DATA MODEL** is being written. The building DB already carries two distinct
models, and the schedule one exists precisely to be written:

| model | tables | standing |
|---|---|---|
| **EXTRACTION — source** | `elements_meta` · `element_transforms` · `element_instances` · `component_geometries`/`base_geometries` · `spatial_structure` · `rel_*` · `qto_cache` · `storey_walkable_raster` | **FROZEN.** Rule 2 in full. No rebuilds to make a measurement come out differently, no extractor edits. |
| **SCHEDULE — output** | `schedules` · `tasks` · `task_sequences` · `task_elements` · `calendars` | **WRITABLE. This is what they are for.** Added by the user in PR #59 (`2253664`) and declared source of truth at `schedule_author.js:6`. Currently **0 rows on every shipped building.** |

> **THE RULING: a saved schedule belongs IN the building DB, in the schedule tables. That is not
> tampering — it is a separate data model, and writing it is the intended use.** The extraction
> tables are untouched by it.

Verified on `Hospital_meta.db`: the two groups are cleanly disjoint — extraction tables carry
63,415 / 63,182 / 9,528 / 8,474 / 212 rows; all five schedule tables carry **0**.

**Why this matters and is not a technicality:** with those tables empty, a planner opening the 4D
view has nothing to edit — dependencies exist only in memory, are rebuilt from scratch on every page
load, and vanish when the tab closes. **Filling them IS the feature** the user has stated
repeatedly: *"as long as the resulting JSON is easily editable by real experts… they easily fill in
the gap or readjust or simply import their model."* It also means a building can be shared WITH its
programme, which a browser-local-only store could never do.

**§S28.6 B5 is UNBLOCKED. §S37 C1 is CLOSED.** §S32.5's first STOP-AND-REPORT is narrowed
accordingly: a write to an EXTRACTION table still stops and reports; a write to a SCHEDULE table
does not. **Rule 1 (no extractor edits) is unchanged and still unconditional.**

## §S32.5 — STOP-AND-REPORT

- **Any task that would write to `buildings/*.db` STOPS and reports** rather than writing, including
  §S28.6 B5's task-table write. That is a user decision under §S32.1 rule 2.
- **Any task that would edit `extractIFCtoDB.py` or `import_worker.js`'s extraction logic STOPS.**
  Rule 1 is unconditional.
- **A runtime derivation whose coverage is below 100% STOPS and reports the gap**, rather than
  defaulting the remainder (§S32.4).

---

# 🔄 §RESUME — HANDOFF TO A FRESH OPUS SESSION (written 2026-08-19, end of session)

**Read this block, then `# §STATUS` at the top of this file, then `# §S32`. Those three are enough
to resume. Everything else in this file is evidence you can consult on demand — do NOT read it
end-to-end.**

## R.0 — the one-paragraph state

Two designs were written and BOTH were rejected by adversarial vetting (§S27.R, §S28.R). The
measurements that followed then refuted premises both designs rested on, and one of this session's
own findings (§S31.3) was retracted the same day. **Nothing has shipped; `viewer/` is unchanged.**
What IS solid is the evidence base (§S25_REVIEW, §S26, §S29, §S30, §S31) and one measured,
ship-ready fix. The user's standing ruling §S32 (extractor correct · DBs frozen · derive at runtime)
landed last and cancels parts of the rejected designs.

## R.1 — TWO AGENTS WERE IN FLIGHT when this session ended. Collect them FIRST.

Neither had reported. Do not re-dispatch either without checking whether it landed.

**Agent A — REPORTED, STOPPED, DID NOT SHIP (2026-08-19).** See the ⛔ block above §S25_REVIEW.6:
its numbers were a mis-attributed comparison; built for real the tie-break gives 6/7 or 5/7, not 7/7.
Branch **`fix/designated-support-election` — now COMMITTED AND PUSHED (`7a06a12`)** as negative
evidence, not a shipping candidate; port proven faithful (0 mismatches / 267,954 elements, two
verifiers), so a porting bug is ruled out as the explanation. Do not merge it against
§S25_REVIEW.6's targets — those do not exist on the live engine. **Also surfaced: all 7 W-MZ-8 baselines are ALREADY RED
on main** — diagnose that before re-locking anything. Original brief said: the only change measured on
the REAL engine (float better on 7/7: Terminal 4,756→1,555 · Hospital 7,753→1,293 · Clinic
1,102→327 · JKR 3,183→1,072 · HHS 1,531→243 · Duplex 247→152 · LTU 12,712→9,461; phase gap
better-or-equal 7/7). Briefed on: BOTH twins (`cpm_schedule.js:120` AND `time_machine.js:4575` —
`witness_midair_zero.js:127` and `probe_captured_floating.js` slice the time_machine copy BY SOURCE
TEXT, so a one-file edit goes green on code that never ran); W-MZ-8 `CPM_FLOAT_AFTER_BASELINE`
(~`witness_midair_zero.js:177`) must be re-measured and re-locked in the SAME PR; STOP-AND-REPORT if
its numbers disagree materially with the above rather than tuning the tie-break.
**On receipt:** verify the twin change actually happened and that the witness EXERCISED the new
election (not merely went green). Then it is mergeable.

**Agent B — measures whether a TOTAL runtime level derivation is possible from the frozen DBs.**
Read-only probe, `scripts/probe_s32_level_coverage.js`, four-tier fallback ladder (T1
`rel_contained_in_space`→space→storey→`center_z` · T2 `elements_meta.storey` name→`spatial_structure`
`center_z` · T3 derived from element z · T4 nothing worked), per building, plus a
declared-vs-geometry consistency check nobody has run.
**On receipt: this number is the GATE for everything else.** If T4 is non-zero on any building, a
grid/cell design cannot be built as §S27/§S28 specified, and that must be said plainly rather than
defaulted around (§S32.4, §S29.1).

## R.2 — the standing rulings you must not violate

- **§S32 (user, unconditional):** the extractor is CORRECT — do not edit `extractIFCtoDB.py` or
  `import_worker.js` extraction logic. `buildings/*.db` are FROZEN — no writes, no rebuild, no
  re-import. All derived metadata is computed at RUNTIME, ONCE, on load (the room-injection
  pattern). §S32.2 lists what this CANCELS; §S32.5 lists the STOP-AND-REPORT triggers.
- **§S28.6 B5 (writing `schedules`/`tasks`/`task_sequences` into the DB) is BLOCKED pending a USER
  decision** — it is a DB write, and whether the IFC-native tables count as "tamper" or as the
  intended output surface is the user's call. Do not assume either way.
- **Instrument rule.** Three false zeros in this lane: `engineGap` arithmetically incapable of
  failing (§S25_REVIEW.1), a judge fed `bz`/`tz` where it reads `base_z`/`top_z` (returned 0 on a
  schedule with 247 real violations), and durations all 0 so nothing could float. All three reported
  clean passes. **Every number prints `durOk`/`judgeCanFail`-style guards. A green number is
  worthless until the instrument has been shown to go red.**
- **Vetting is a gate, not a courtesy.** §S27 and §S28 were both stopped by it, correctly. Do not
  dispatch a build agent against a spec whose `§…R` verdict block is empty or NOT VETTED.

## R.3 — what is SOLID (cite these; they survived adversarial re-derivation)

- The 93% blob is manufactured by the support PREDICATE, not by buildings — restricting support to
  load-bearing classes and dropping `embedded` takes Hospital's largest component 49,436→1,951,
  LTU 74,617→1,460, Duplex 672→5 (§S26.1).
- The `hang` family is 93.5-99.8% redundant with phase order and is the SOLE remaining cycle source;
  deleting it gives `largestSCC=1` on all 7 (§S26.3, §S26.5).
- A Gantt bar is a HULL over independently-scheduled elements (`time_machine.js:6074`, grouped by
  `storey|phase`, min-start to max-end). One cause under three symptoms: midair, "one pile full
  project length" stacking, and the phase-gap metric itself (§S26.13 / the code's own comment).
- The IFC-native container already exists, was built by the user (PR #59 `2253664` 2026-05-30,
  PR #502 `b195103`), is declared source of truth at `schedule_author.js:6`, and holds **0 rows on
  every shipped building** (§S26.13).
- The extractor is NOT broken — it already declares `rel_fills_host`, `port_elements`,
  `port_connections`, `rel_adjacency`, `rel_anchored` (§S31.2). Under §S32 the DBs are simply the
  frozen input.
- Sort-key measurement (§S30, §S31.1): under REAL crew caps seqfirst 5/7, zfirst 2/7; with caps
  lifted BOTH reach 7/7. So trade-first's advantage is a resource-levelling property, **not** an
  ordering-quality one, and §S30.2 must not be quoted bare. Backward supports are 21-65% of elements
  — the norm, not a residual, so a sort alone does NOT make physics safe.
- Correctness metric nobody had measured: **mean 83.0%, range 64.6-93.1%** of elements not starting
  before a real support finishes, live CPM path (§S31, Q6).

## R.4 — what is NOT solid (do not build on these)

- §S27 and §S28 as designs — both NOT VETTED, and §S32 has since cancelled parts of both.
- `scripts/probe_s30_sortkey.js` is a standalone reimplementation with NO `wallGate`, `hangGate`,
  `openingGate`, host pairs, or phase/level gates. Its numbers do not transfer to the engine. **A
  probe carrying the real gates is a prerequisite for any sort-based refactor.**
- §S31.3's storey-`Name` claim — RETRACTED in place, wrong file and wrong phenomenon. §S26.6 C2
  inherited the same mistake and is qualified by that retraction. The real Clinic finding is that
  **32% of its elements resolve to storey `Unknown`.**
- JKR as a representative building — it has ~1% room-injection coverage and is the least
  representative of the 7. Do not headline its numbers.

## R.4b — ⚠ READ §S37 BEFORE PICKING WORK

`# §S37` is the CARRIED-FORWARD LEDGER: everything measured in this lane and never converted into
an action. It exists because LTU's storey ladders were missed by two consecutive sessions, and
because two clean results (the `hang` deletion, and 7 red W-MZ-8 baselines) sat idle for a full day.
**Do not start new measurement before checking whether the answer is already in §S37.**

## R.5 — the sequence, in order, and why the order matters

1. **Collect Agent A.** Verify twins + witness actually exercised. Merge.
2. **Collect Agent B.** Its coverage number is the gate.
3. **Build a probe that carries the REAL gates** (R.4) so a sort result transfers to the engine.
4. **THEN write ONE spec**, grounded in 2 and 3, under §S32. Vet it before any refactor code.

**This order is the session's main lesson.** Every design written BEFORE its measurement was
refuted by it — §S30 killed elevation-first ordering, §S31.1 then qualified §S30, §S31.3 was
retracted outright. Three in one session. **Measure, then design.** If the next spec also fails
vetting, stop redesigning and keep making measured single-function fixes like the election one —
the only thing that produced a 7/7 result all day.

## R.6 — housekeeping already done, so you do not redo it

Live file consolidated 2,955→2,166 lines with zero content lost (185 headings before; 120 live + 71
archived; verified programmatically). §S23/§S24/§S24_TRIAGE/§S25/§S25_PROTO archived verbatim to
`prompts/archive/4D_GANTT_TM_REFACTOR_S23-S25_superseded_2026-08-19.md`. §S29 restored ahead of §S30.
`extractIFCtoDB.py` merge with `origin/master` RESOLVED and pushed (`86b059df2`) — it was 97%
CRLF noise; both §S21 and §KUL001 kept. Docs published: `ViewerComponentModel.html` live at
`https://red1oon.github.io/BIMCompiler/ViewerComponentModel.html`, linked from the Viewer guide.
Two orphaned live pages recovered into git in the process (`grislab_proof_run.html`,
`ERP_PROJECT_REVIEW.md`) — both were live on gh-pages and in NO branch.

---

# §S33 — BOTH AGENTS REPORTED (2026-08-19). One gate PASSED, one claim DIED.

## §S33.1 — Agent B: ✅ the §S32 derivation contract is ACHIEVABLE. T4 = 0 on all 7.

`scripts/probe_s32_level_coverage.js` (`32ceb7b66`), read-only, four-tier fallback ladder.
**Independently re-run and verified by this session**, including `md5sum -c` proving
`Hospital_meta.db` is byte-identical after the run.

| building | n | T1 space→storey | T2 name→storey | T3 own z | T4 |
|---|---|---|---|---|---|
| Hospital_meta | 63,182 | 8,474 (13.4%) | 45,033 (71.3%) | 9,675 (15.3%) | **0** |
| HHS_Office_Federated | 6,839 | 88 (1.3%) | 4,586 (67.1%) | 2,165 (31.7%) | **0** |
| Clinic_meta | 16,071 | 2,133 (13.3%) | 3,594 (22.4%) | 10,344 (64.4%) | **0** |
| JKR_extracted | 8,985 | 107 (1.2%) | 1,910 (21.3%) | 6,968 (77.6%) | **0** |
| Terminal_meta | 48,428 | 1,009 (2.1%) | 10,224 (21.1%) | 37,195 (76.8%) | **0** |
| LTU_AHouse_meta | 122,330 | 0 | 0 | 122,330 (100%) | **0** |
| Duplex_extracted | 1,119 | 0 | 0 | 1,119 (100%) | **0** |

**0 of 266,954 elements fall through** (the 276,954 first written here was a transcription slip; the table above sums to 266,954 — re-derived in §S34, every per-building `n` matches). Structural, not luck: `_buildScheduleElements` already drops
zero-transform elements upstream, so everything reaching the scheduler has a finite z and **T3 is a
floor that cannot fail.** `ambiguousStoreyNames=0` on all 7, confirming §S31.4.

**The instrument was proven capable of failing** — a 6-case `§S32_SELFTEST` runs before any real
building, including a deliberately-broken case D that MUST land on T4; all 6 pass. T1 counts
cross-check exactly against §S26.16's independently-measured `rel_contained_in_space` figures on all
7. **This is the first number in this lane that arrived with its own falsifiability proof.**

**NEW, unresolved, and it is a design decision not a measurement:** where a DECLARED level exists it
sometimes contradicts the element's own z by >3m — Terminal T2 **2,161/10,224 (21.1%)**, Hospital T1
557/8,474 (6.6%), others under 2.5%. Some is legitimate (tall elements span bands). **The ladder as
written would silently trust the declared value.** A real derivation needs a stated tie-break for
declared-vs-geometry disagreement. Agent B correctly stopped at the number.

## §S33.2 — Agent A: ⛔ the one "ship-ready" change did NOT survive being built

**STOPPED at §S25_REVIEW.9's STOP-AND-REPORT rather than shipping. That was the correct call and the
gate worked.** Full correction is in the ⛔ block immediately above `§S25_REVIEW.6`; summary:

- **The port is not the problem.** Proven faithful: 0 mismatches across 267,954 elements on all 7
  buildings, verified two independent ways.
- **The claim was a mis-attributed comparison.** `DESIG=v2` never reaches `CpmSchedule.run`
  (`proto_s25_forward_pass.js:608` runs CPM *before* the override exists at `:610`, and it is fed
  only to the prototype at `:644`). The "7/7 versus the live engine" table credited the §S25
  prototype's entire architecture to a tie-break in one function.
- **Built for real: 6/7 (proto path) or 5/7 (witness path).** HHS_Office regresses on BOTH
  (1,531→1,589 and 1,491→2,505, +68%); JKR regresses on the witness path.
- **The `-29% to -51%` backward-supports claim DOES hold** — a pure property of the election,
  independent of the engine around it.

**Two further findings, both pre-existing and unrelated to the change:**
1. **All 7 W-MZ-8 baselines are ALREADY RED on `main`.** `pass=32 fail=7`. A lock that no longer
   matches main is not a lock — something moved all 7 and was never re-locked. **Diagnose before
   re-locking anything.**
2. **Three different float numbers per building depending on path** (Duplex: 289 lock / 247 proto /
   237 witness). §S28.R flagged this; it is now confirmed on all 7. **Every float number in this
   lane must name its instrument.**

## §S33.3 — the tally, stated plainly

**Four claims died by measurement today, and the fourth was the one this file called ready to ship:**
§S30 killed elevation-first ordering · §S31.1 qualified §S30 · §S31.3 was retracted outright ·
§S33.2 killed §S25_REVIEW.6.

**Every one was a claim written before, or inferred beyond, its measurement.** The two things that
survived — §S26's evidence base and §S33.1's coverage result — were measured first and carried their
own falsifiability proof. **That is the whole lesson of this session and it now has four data points.**

## §S33.4 — STOP-AND-REPORT for whoever resumes

- **Do not re-lock W-MZ-8 to today's numbers.** All 7 are red on main for an unknown reason. Find the
  cause first; re-locking would bless whatever broke them.
- **Do not ship the election port to chase §S25_REVIEW.6's targets.** Those targets do not exist on
  the live engine. If the port is shipped it must be on its OWN merits (6/7 or 5/7, HHS regressing),
  as a user decision, with baselines handled per the item above.
- **Do not build the level derivation until the declared-vs-geometry tie-break is DECIDED** (§S33.1).
  Coverage being total does not make the values correct.

---

# §S34 — THE DECLARED-vs-GEOMETRY TIE-BREAK, MEASURED THEN DECIDED (2026-08-19)

**§S33.1 stopped at a number and refused to guess the ruling. This section measures the question
first, then rules.** Instrument: `scripts/probe_s34_declared_vs_geometry.js` (read-only, sql.js
buffer, `db.run()` never called; `md5sum` of all 7 DBs identical before/after — printed below).
`§S34_SELFTEST 13/13`, all four buckets exercised including a fixture that MUST come out FAR and an
interval fixture that MUST come out `false`. `§S34_TIERCHECK` re-derives this probe's own T1/T2
counts against §S33.1's committed table and MATCHES on all 7 buildings, so the ladder has not drifted.

## §S34.0 — the statistic that raised the question could not answer it

§S33.1 reported `|declaredZ − ownCenterZ| > 3m` — Terminal T2 21.1%, Hospital T1 6.6%. That single
distance conflates four situations that need four different rulings, and it silently assumed
`center_z` is the datum a declared storey elevation is keyed to. Measured (`§S34_DATUM`), **no single
datum wins the fleet**: `base_z` is the best predictor on Terminal (77.56% vs 77.25%) and HHS
(99.40% vs 97.11%), `center_z` on Hospital (93.44%), Clinic (94.13%) and JKR (62.32%). A point-datum
test is therefore the wrong instrument on its own terms — which is why the test below compares the
element's whole vertical EXTENT against the storey's INTERVAL.

## §S34.1 — MEASURED: storey elevations are floor lines, and most "disagreement" is the datum

`§S34_GRID` — the declared elevation grid actually available per building:

| building | declared storey elevations (m) | k | min gap |
|---|---|---|---|
| Terminal_meta | 3.05 · 10.07 · 13.84 · 17.82 · 22.63 · 25.13 | 6 | 2.50 |
| Hospital_meta | 168.74 · 174.06 · 178.85 · 183.93 · 188.82 · 193.90 · 201.43 | 7 | 4.78 |
| Clinic_meta | 0.80 · 2.00 · 6.61 | 3 | 1.20 |
| HHS_Office_Federated | 1.69 · 5.36 · 8.75 | 3 | 3.38 |
| JKR_extracted | 82.89 · 82.90 · 85.94 · 89.18 | 4 | **0.01** ⚠ |
| LTU_AHouse_meta, Duplex_extracted | (none — no storey row carries `center_z`) | 0 | n/a |

Only **6 of Terminal's 73** storey rows and **7 of Hospital's 63** carry a `center_z` at all; the
rest are NULL. The grid is small, and it is a set of FLOOR LINES, not band centres.

Consequence, measured (`§S34_SIGNED`): of the declared elements whose extent does not overlap
`[Z_i, Z_i+1)`, **87–100% sit BELOW their own declared floor line**, not above — Terminal 91.2%,
Hospital 93.2%, Clinic 95.5%, HHS 100.0%, JKR 86.7%. That is the hosted-at-level convention (a
slab's top IS the level; a beam hangs under it), not a contradiction.

`§S34_TOLSWEEP` sweeps a downward tolerance and finds a knee at ~3m on every building, after which
it stops paying: Terminal 574→157→105 at 2/3/5m, Hospital 3,764→890→831, HHS 6→6→6, Clinic 50→7→6.
**~3m is one storey height**, so the tolerance is taken from the data, not tuned: the LOCAL storey
gap `Z_i − Z_i−1` (median gap for the lowest storey). Nothing to configure, and it adapts to a
building whose storeys are 2.5m apart or 7.5m apart (§S29 generality).

## §S34.2 — MEASURED: the genuine contradiction population is 1.63% of declared elements

`§S34_GAPTOL` — declared elements whose own extent does not reach their declared storey band even
after the local-gap allowance. **This, and only this, is what a tie-break decides:**

| building | declared | genuine contradictions | % of declared | % of all scheduled |
|---|---|---|---|---|
| Terminal_meta | 11,233 | 140 | 1.25% | 0.289% |
| Hospital_meta | 53,507 | 825 | 1.54% | 1.306% |
| Clinic_meta | 5,727 | 48 | 0.84% | 0.299% |
| HHS_Office_Federated | 4,674 | 6 | 0.13% | 0.088% |
| JKR_extracted | 2,017 | 241 | 11.95% ⚠ | 2.682% |
| LTU_AHouse_meta, Duplex_extracted | 0 | — | MOOT (100% T3, no declared value exists) |
| **fleet** | **77,158** | **1,260** | **1.63%** | **0.47%** |

**What they are** (`§S34_GAPTOL_CLASS`) — overwhelmingly distribution elements and proxies:
Terminal `IfcPipeFitting` 34.3% + `IfcPipeSegment` 29.3% + `IfcBuildingElementProxy` 25.7%;
Hospital `IfcPipeFitting` 52.7% + `IfcDistributionControlElement` 25.6%; HHS 100% `IfcFlowSegment`.
Clinic is the one structural block: 38 `IfcFooting` (79.2%) declared on a storey their geometry is
nowhere near. These are elements whose declared storey is a SYSTEM/design label, not a location.

**Falsifiability, not assumed** (`§S34_CONTROL`): the declared labels are shuffled within each
building and the whole measurement re-run. Real labels beat random ones by **3.9× (Hospital) to
36.9× (Clinic)** on the interval metric and 15.9×–311.8× on the bucket metric. Declared data carries
real information; it is not noise to be discarded. **JKR is the exception and it is reported as
one**: ratio 1.6×, printed as `intervalMetricDiscriminates=NO — ⚠ void`, caused by its two storeys
0.01m apart (82.89 / 82.90). On a grid that degenerate the geometric test cannot separate the
levels at all and the declared NAME is the only thing that can — evidence FOR keeping declared, from
the one building where the instrument admits it failed.

## §S34.3 — ⚖ THE RULING

**Declared wins where the element physically reaches the storey it claims; geometry wins where it
does not. The band a declared storey owns is `[Z_i, Z_i+1)` extended DOWN by the local storey gap.**

1. An element with a declared level (T1 or T2) whose extent `[base_z, top_z]` intersects that
   extended band keeps the DECLARED value — 98.37% of declared elements fleetwide.
2. An element whose extent misses it entirely is OVERRIDDEN to the band its own geometry occupies —
   1,260 elements, 0.47% of the fleet, and the override is COUNTED and `§`-logged per building, per
   class, never silent (§S32.4).
3. An element with no declared level (T3 — 100% of LTU and Duplex, 64–78% of Clinic/JKR/Terminal)
   is placed by geometry alone. No tie-break exists there; nothing to decide.

**Why declared is the default and not geometry:** the shuffle control proves declared labels carry
real information (3.9–36.9×), and JKR proves geometry alone cannot separate two levels 0.01m apart
while the name can. **Why geometry wins the miss case:** a schedule is about when a crew can install
a thing, and a crew cannot install a fitting at z=180 while working a level at z=168 — for the 1.63%
where the label is a system label rather than a location, physical elevation is the operative fact.

## §S34.4 — what this does NOT settle (named, not smoothed over)

- **LTU is federated and a single global grid is wrong for it by construction** (§S29.4). Its storey
  NAMES exist but come from several source models with different floor heights — measured:
  `Plan 1` avg z 4.86 · `Storey 1` 4.45 · `VÅNING 1` 2.95, three different "level 1"s. It has no
  declared `center_z` grid at all, so §S34.3 rule 3 applies and the tie-break never fires; the
  federation problem is untouched by this ruling and stays open.
- **JKR's grid is degenerate** (0.01m between two storeys) and its 11.95% override rate is an
  artifact of that, not a data-quality finding about JKR. Do not headline it (§RESUME R.4).
- **This ruling is about the VALUE of a level, not about how levels order work.** Sort-key and band
  ordering remain where §S30/§S31.1 left them.
- **Correction to §S33.1's prose:** the fleet total is **266,954** scheduled elements, not 276,954 —
  §S33.1's own per-building table sums to 266,954, re-derived here with every per-building `n`
  matching that table exactly (`§S34_TIERCHECK`). The T4=0 result is unaffected.

---

# §S35 — THE RUNTIME LEVEL DERIVATION, BUILT (2026-08-19). 100% coverage on 7/7, nothing wired.

**Deliverables**, both new, both in `bim-compiler` (the `build/room_walker.js` → `viewer/lib/` source
pattern §S32.1 rule 3 names as the precedent):

- `build/level_deriver.js` — the runtime pass. Dual-mode (node + browser sql.js), read-only,
  `window.LevelDeriver` / `module.exports`.
- `scripts/witness_level_derive.js` — 14 hand-computed fixtures (§VERIFICATION rule 1) + the fleet run.

**⚠ NOT WIRED INTO THE SCHEDULER, deliberately.** Consuming this in `CpmSchedule`/`time_machine`
changes ordering behavior and needs its own vetted spec (§RESUME R.5 step 4). This file derives and
reports; nothing in `viewer/` changed.

## §S35.1 — the §S32.4 contract, clause by clause, with the evidence

| §S32.4 clause | how it is met | evidence |
|---|---|---|
| read only | only `db.exec()` with SELECT/PRAGMA — `db.run` occurs once in the file, in a comment | `md5sum -c` on all 7 DBs: **7/7 OK** after every run this session |
| once per load | `derive(db, elements)` is a single pass; caller holds the result | one call per building in the witness |
| **be total** | geometry tier is the floor and cannot fail — `_buildScheduleElements` drops zero-transform rows upstream (`viewer/schedule_author.js:346` `.filter(!e.noGeo)`, read this session) | `T4=0` on 7/7, **coverage 266,954/266,954 = 100.000%** |
| report coverage + fallbacks | `§LEVEL_DERIVE_GRID` / `_TIER` / `_OVERRIDE` / `_AMBIGUOUS`, per building | full log below |
| instrument guards | `§LEVEL_DERIVE_GUARD` prints per building whether the declared and override branches were REACHABLE at all, so "0 overrides" is distinguishable from "the branch never ran" | LTU + Duplex print `overrideBranchReachable=NO — tie-break MOOT`, the other 5 print `YES` |

## §S35.2 — fleet result (`§W_LEVEL_FLEET_VERDICT`, `node scripts/witness_level_derive.js`, exit 0)

| building | n | declared kept | geometry | overrides | grid source | T4 |
|---|---|---|---|---|---|---|
| Terminal_meta | 48,428 | 11,093 (22.9%) | 37,335 | 140 (1.25% of declared) | declared, k=6 | 0 |
| Hospital_meta | 63,182 | 52,684 (83.4%) | 10,498 | 823 (1.54%) | declared, k=7 | 0 |
| Clinic_meta | 16,071 | 5,679 (35.3%) | 10,392 | 48 (0.84%) | declared, k=3 | 0 |
| LTU_AHouse_meta | 122,330 | 0 | 122,330 | 0 (MOOT) | **uniform 3m, k=23** | 0 |
| Duplex_extracted | 1,119 | 0 | 1,119 | 0 (MOOT) | **uniform 3m, k=5** | 0 |
| HHS_Office_Federated | 6,839 | 4,668 (68.3%) | 2,171 | 6 (0.13%) | declared, k=3 | 0 |
| JKR_extracted | 8,985 | 1,778 (19.8%) | 7,207 | 239 (11.85% ⚠) | declared, k=4 | 0 |
| **fleet** | **266,954** | **75,902** | **191,052** | **1,256 (1.63% of declared)** | | **0** |

`ambiguousStoreyNames=0` on all 7 — re-checked per building by the module itself, not inherited
from §S31.4.

**Reconciliation with §S34's probe, not glossed:** the probe counted 1,260 genuine contradictions,
the module overrides 1,256. Element-level diff on the two buildings that differ (`moduleOnly=0`,
`probeOnly=3` Hospital / `2` JKR) shows the module's override set is a strict SUBSET: the delta is
elements sitting within the ±0.05m slack of the tolerance boundary — e.g. an `IfcPipeFitting` whose
top is 5.372m below a declared level with a 5.33m local gap, and a JKR `IfcSlab` at 82.879 against a
band starting at 82.90. **No element is overridden by the module that the probe would have kept.**

## §S35.3 — the witness found a real bug before anything was wired

Fixtures A4 / A5 / A14 failed on the first run. Cause: the downward tolerance is what validates a
DECLARED value, but the first draft also used those extended bands to PLACE geometry-only elements —
and extended bands deliberately OVERLAP (band(4)'s extension reaches into band(0)), so a scan put an
element at z=0.2 on level 4. **Two different questions needed two different intervals:**
`declaredBandOf()` = `[Z_i − localGap, Z_i+1)` for "does this element reach the storey it claims?",
`geomIdx()` = plain `[Z_i, Z_i+1)` for "which storey is it on?". Both are now named, commented with
the failure that produced them, and A14 stands as the guard (3.9m→level 0, 4.1m→level 4 — the
classifier provably responds to input rather than returning a constant).

**This is the §RESUME R.5 lesson working in the other direction:** the fixtures were hand-computed
before the engine ran, so a wrong implementation could not report itself green.

## §S35.4 — the 14 fixtures and what each one proves

`A1` declared+geometry agree · `A2` a slab whose top IS the floor line is not a contradiction
(§S34.1's datum) · `A3` one storey below still counts as declared (the local-gap allowance) ·
`A4` a far miss overrides to geometry and is FLAGGED · `A5` geometry without a declared value is NOT
an override (override count ≠ geometry count) · `A6` T1 beats T2 · `A7` a full-height riser keeps
its declared base level · `A8` a storey name with no elevation is not a declared value · `A9` both
`Unknown` spellings are non-declarations · `A10` T4 stays uncovered, never defaulted · `A11` a
declared value survives unusable geometry · `A12` total off the bottom of the grid · `A13` total off
the top · `A14` the classifier responds to input. **14/14 pass, `§W_LEVEL_FIXTURES pass=14 fail=0`.**

## §S35.5 — STOP-AND-REPORT (what this run is NOT allowed to imply)

- **LTU and Duplex get a uniform-3m grid, and that is a REPORTED FALLBACK, not a result.** No storey
  row in either DB carries an elevation, so there is nothing declared to extract. LTU's is worse than
  Duplex's: it is federated across source models with different floor heights (§S34.4), so a single
  global grid is wrong for it by construction and `k=23` bands from −48m upward is the honest
  printout of that, not a level structure anyone should build a Gantt on. **A derived grid
  (slab-top clustering — Duplex's slabs cluster cleanly at 0.0 / 3.0 / 6.5) is NOT attempted here:
  untested on one building, wrong by construction on the other.** Named as open work, not defaulted.
- **JKR's 11.85% override rate is a grid degeneracy** (two storeys 0.01m apart), not a data-quality
  verdict on JKR. §RESUME R.4 already says not to headline JKR numbers.
- **100% coverage is not 100% correctness.** It says every element got a level from a stated source
  with a stated fallback. Whether those levels order work correctly is §S30/§S31.1 territory and is
  untouched by this section.
- **Nothing shipped.** `viewer/` unchanged, no PR, no DB written.

---

# §S36 — THREE CHECKS ON §S34/§S35 BEFORE ANY SPEC (2026-08-19). Answers only, nothing adopted.

Instrument: `scripts/probe_s36_tiebreak_sensitivity.js`, read-only, `§S36_SELFTEST 6/6` (each pure
function shown able to return the NEGATIVE answer: clustering can return an empty grid, the
partition metric reports 0 on identical grids and non-zero on an offset one, the snap check detects
a foreign line and does not false-positive on an exact one). `md5sum -c` on all 7 frozen DBs: **7/7
OK** before and after every run in this section. No adoption, no switch, no wiring.

## §S36.1 — Q1: the uniform-3m fallback is EXPENSIVE, and clustering does not rescue it

**Answer: not "few". 8.73–29.78% of LTU depending on the clustering bandwidth, 7.06% of Duplex.**

`§S36_Q1_COST` — elements landing in a different level under uniform-3m vs a slab-top-derived grid.
The metric compares GROUPINGS, not level values (each uniform group is mapped to the derived group
most of its members land in; members not following their own group's plurality are counted).
Controls printed alongside: grid-vs-itself = **0**, half-band shift = 21.56% (LTU) / 25.92%
(Duplex) — `metricResponds=YES`.

| clustering params | LTU derived k | elements in a different level | | Duplex derived k | different |
|---|---|---|---|---|---|
| bw=1.00m | 2 | 10,683 (**8.73%**) | | 2 | 79 (**7.06%**) |
| bw=0.50m | 4 | 18,003 (**14.72%**) | | 2 | 79 (7.06%) |
| bw=0.25m, min=10 | 12 | 29,270 (23.93%) | | — | — |
| bw=0.25m, min=3 | 17 | 36,432 (**29.78%**) | | 2 | 79 (7.06%) |

**The second finding matters as much as the first: clustering is not a determinate alternative on
LTU.** Its derived grid swings k=2 → 4 → 17 across bandwidths 1.0 → 0.5 → 0.25m, and the cost
number swings with it. The bandwidth is a free parameter — an invented value — which is exactly
what this project's Prime Rule forbids adopting without a derivation. On Duplex clustering IS stable
(k=2 at every bandwidth) but WRONG in a knowable way: it finds 0.01m and 3.11m and drops the roof at
~6.5m, which has only 1 slab and cannot clear any sane minimum.

**LTU is federated: `IfcBuildingRows=9`.** Nine buildings in one DB, sharing one global z grid. That
is the mechanism behind level_deriver.js's own "wrong by construction" comment, now measured rather
than asserted.

⛔ **This goes on the open list, not in a footnote.** 122,330 elements — **45.8% of the fleet** — are
levelled by a fallback that disagrees with the nearest plausible alternative on 8.7–29.8% of them,
and the alternative is itself parameter-dependent. Neither grid is trustworthy on LTU; the honest
statement is that **LTU has no validated level structure at all**, and any §S30-style ordering claim
computed on LTU inherits that. Do not adopt clustering on this result — it is a cost measurement,
not a switch.

## §S36.2 — Q2: `nearestIdx` never snaps a declared storey onto a foreign line — but JKR's twin is real and it costs 204 overrides

**Answer: `snappedToAForeignLine = 0` on all 5 buildings with a declared grid** (Terminal, Hospital,
Clinic, HHS, JKR — 46 declared storey values in total). The grid is built from those same
elevations, so the snap is exact by construction; measured, not assumed.

`§S36_Q2` — the near-twin population, per building: Terminal minimum line separation 2.497m,
Hospital 4.784m, Clinic 1.196m, HHS 3.382m, **JKR 0.010m** (82.888 / 82.899). JKR is the only case,
and 493 declared elements sit on one of those two twinned lines (284 + 209).

**What the twin actually breaks is not identity — it is the BAND ARITHMETIC**, and both twins break,
from opposite directions (`§JKR_BYLINE`):

| JKR line | local gap (= down tolerance) | declared kept | overridden | override rate |
|---|---|---|---|---|
| 82.888 | 3.040m — but its band CEILING is the twin at 82.899, i.e. 0.011m tall | 209 | 79 | 27.4% |
| 82.899 | **0.010m** — essentially no downward allowance at all | 284 | 128 | **31.1%** |
| 85.938 | 3.040m | 1,251 | 32 | 2.5% |
| 89.182 | 3.244m | 34 | 0 | 0.0% |

**Counterfactual, measured, adopted by nothing** (`§JKR_MERGED_COUNTERFACTUAL`): collapsing grid
lines closer than 0.5m gives declaredKept 1,778 → **1,982** and overrides 239 → **35**. So **204 of
JKR's 239 overrides (85%) are an artifact of two storey lines 1cm apart**, not a property of JKR's
data quality. That also retires the "JKR 11.85%" figure as a headline number (§S35.5 already said
not to headline it; this says why, quantitatively).

**Would carrying storey identity (guid/name) instead of snapped z close it? No.** There is no
snapping bug to close — mis-snap is 0 everywhere. The tie-break still needs a z BAND to ask "does
this element reach the storey it claims", and that band is computed from neighbouring elevations
whether the key is a guid or a number. What closes it is a grid-construction rule (collapse lines
closer than a threshold), measured above. **And identity has a real cost:** 77.1% of Terminal,
80.2% of JKR, 64.7% of Clinic and 100% of LTU/Duplex resolve by geometry and have no storey identity
to carry, so the level key would split into two incompatible spaces — storey ids for declared
elements, band indices for the rest — with every consumer needing a z per key anyway. Zero measured
benefit on 6 of 7 buildings.

## §S36.3 — Q3: 1.0× is the KNEE, not an interior point of a slope — and the outer half is not doing the job §S34 said it was

`§S36_Q3_SWEEP` — override rate vs downward tolerance, as a fraction of the local storey gap:

| building | 0× | 0.25× | 0.5× | 0.75× | **1.0×** | 1.5× | 2.0× |
|---|---|---|---|---|---|---|---|
| Terminal | 23.51% | 16.18% | 8.31% | 3.04% | **1.25%** | 0.93% | 0.93% |
| Hospital | 25.65% | 15.66% | 3.19% | 2.55% | **1.54%** | 1.51% | 1.46% |
| Clinic | 29.44% | 18.07% | 1.22% | 0.93% | **0.84%** | 0.82% | 0.10% |
| HHS_Office | 14.87% | 11.90% | 0.39% | 0.13% | **0.13%** | 0.13% | 0.00% |
| JKR | 38.23% | 34.01% | 19.78% | 12.15% | **11.85%** | 11.85% | 10.56% |

**Shape (`§S36_Q3_SHAPE`): steep below 1.0×, flat immediately above it.** Tightening 1.0× → 0.5×
costs +7.07pp (Terminal), +1.65pp (Hospital), +7.93pp (JKR), +0.38pp (Clinic), +0.26pp (HHS).
Loosening 1.0× → 2.0× buys only −0.32pp / −0.08pp on the two largest — a 20-22× asymmetry. So 1.0×
is the SMALLEST tolerance that reaches the flat part, which is a defensible corner rather than an
arbitrary pick. **Two of five buildings (Clinic, HHS) are already flat at 0.5×; three are not.**

**But the rationale under-describes what the outer half rescues** (`§S36_Q3_OUTERHALF`). The
elements rescued only between 0.5× and 1.0× sit **0.52–0.83 of a full storey** below their declared
floor line (p50), which is not "a slab's top IS the floor line" (that is 0.2–0.3m). What they are:

- **Hospital: 553 `IfcFooting`** of 882 (62.7%) — foundations sitting ~0.81 of a storey below the
  level they carry. Physically defensible: a footing belongs to the structure above it.
- **Terminal: 259 `IfcPipeSegment` + 220 `IfcPipeFitting` + 172 `IfcFurniture`** of 794. The pipes
  are the known MEP pattern; **`IfcFurniture` 0.72 of a storey below its declared floor is not
  explained by any datum convention** and is a named open question, not a settled case.
- **JKR: 101 `IfcSlab` + 20 `IfcBeam`** of 160 — slabs ~0.56 of a gap below their declared line.
  Note JKR's grid is the degenerate one (§S36.2), so read this one last.

**Verdict on the ruling:** §S34.3 lands on the curve's knee and the measurement supports the choice
of 1.0× over 0.5× or 2.0×. What it does NOT support is the stated JUSTIFICATION covering the whole
tolerance — the inner half is the hosted-at-level datum, the outer half is mostly foundations and
MEP, plus a Terminal furniture population nobody has explained. **The honest form of the rule is
"declared wins unless the element is more than one storey away from the level it claims", with the
datum argument covering only part of why that threshold works.** No change made; this is the
qualification that belongs on the rule before it reaches a spec.

## §S36.4 — the three answers in one line each

1. **LTU fallback cost is LARGE** (8.7–29.8%, parameter-dependent) and clustering is not a
   determinate alternative → open list, `⛔ LTU has no validated level structure`.
2. **No mis-snap anywhere (0/46 declared storey values)**; JKR's 1cm twin is real, costs 204 of its
   239 overrides, and storey IDENTITY would not fix it — grid-line collapsing would.
3. **1.0× is the knee** (steep below, flat above, 20–22× asymmetry) — but its outer half rescues
   footings, MEP and unexplained Terminal furniture, not the datum case §S34 cited. Rule stands,
   justification narrowed.

---

# §S37 — CARRIED-FORWARD LEDGER: measured, not actioned (2026-08-19)

**Why this section exists.** User challenge: *"Why isn't this spec'd when we did go thru it? What
else is forgotten?"* — after LTU's storey ladders (`Plan 1..4`, `VÅN 1..5`, `VÅNING 1..4`,
`Storey 1..3`) were found in `elements_meta.storey` AFTER two sessions had concluded LTU has no
level structure.

**The root cause of that miss, stated plainly so it is not repeated:** §S31.4 measured the LOOKUP
("how often does a storey name join to a stored `spatial_structure.center_z`" — 21-85%) and read
the shortfall as missing data. It never asked the next question: **the name IS the grouping, so
derive the elevation from the members carrying it.** §S32 says derive at runtime; the spec still
went hunting for a stored value. **A partial JOIN is not the same as absent DATA.**

**The pattern this ledger fixes:** this lane reliably produces good numbers and then writes them
down instead of converting them into work. Every item below is MEASURED and IDLE. A finding with no
owner and no next action is a finding that will be re-discovered.

## §S37.1 — measured, cheap, and nobody opened it

| # | item | evidence | why it is still open |
|---|---|---|---|
| **A1** | **`hang` deletion → acyclic 7/7.** Branch `fix/s26-drop-carrier-ordering` is PUSHED. | §S26.3, §S26.5, §S26.14 — `largestSCC=1` on all 7; physics SCCs contracted down 8-71%; midair 0→0 | **No PR was ever opened.** Cleanest result in the lane. Float regressed on 2/7 (Hospital, JKR), so it needs a decision, not a merge — but it needs SOMEONE to make it. |
| **A2** | **All 7 W-MZ-8 baselines are RED on `main`.** `pass=32 fail=7` | §S33.2, agent-measured before any edit | **Undiagnosed.** Every float judgement in this lane is scored against locks that no longer match main. This is the gate everything else is measured by, and it is currently broken. **Diagnose before re-locking anything.** |
| **A3** | **Support restricted to load-bearing classes + drop `embedded`** | §S26.1 — Hospital largest component 49,436→1,951, LTU 74,617→1,460, Duplex 672→5 | Measured in a probe, never taken to the engine. |

## §S37.2 — measured, needs a design, never designed against

| # | item | evidence | the unanswered question |
|---|---|---|---|
| **B1** | **A Gantt bar is a HULL** over independently-scheduled elements | §S26.13, `time_machine.js:6074`; the code's own comment names it "one pile, full project length" | ONE cause under THREE symptoms — midair, stacking, and the phase-gap metric itself. Nothing has ever been built against it. **This is the largest unexploited finding in the file.** |
| **B2** | **Trade pipelining** — Hospital starts the next trade in Zone A once the previous clears Zone B, not Zone C | §S28.R, verified in the IFC's own `IfcRelSequence` links | No serial-trade model can express it. §S27/§S28 both assumed serial. Unresolved. |
| **B3** | **32% of Clinic resolves to storey `Unknown`** (5,160/16,114) | §S31.3 | The real Clinic finding, replacing the retracted storey-Name claim. Never chased. |
| **B4** | **Furniture 0.72 of a storey below its declared floor** — Terminal 172 elements | §S36 | No datum convention explains it. Named open by §S36, no owner. |
| **B5** | **LTU per-family level ladders** | this section's header; `elements_meta.storey` | Prompt issued 2026-08-19. If per-family grids work, `FALLBACK_BAND_M` and the clustering bandwidth both disappear — no invented constants left. |

## §S37.3 — blocked on a USER decision, not on work

| # | item | the decision only the user can make |
|---|---|---|
| **C1** | ✅ **DECIDED 2026-08-19 — see §S32.6. Writing the task tables to the USER'S LOCAL COPY is the intended output, not tampering.** The shipped file stays frozen. This item is CLOSED and no longer blocks. |
| **C2** | **Whether `fix/s26-drop-carrier-ordering` ships** | Acyclic 7/7 and midair 0→0, but float regresses on Hospital (+722) and JKR (+227). A trade, not a free win. |
| **C3** | **Lever 2 of the LFS fix** | Settings → Archives → uncheck "Include Git LFS objects in archives". Web-UI only, confirmed not API-exposed. |

## §S37.4 — prerequisites that gate later work

| # | item | gates |
|---|---|---|
| **D1** | **A probe carrying the REAL gates** (`wallGate`, `hangGate`, `openingGate`, host pairs, phase/level gates) | ANY sort-based refactor. `probe_s30_sortkey.js` is a standalone reimplementation; its numbers do not transfer to the engine (§RESUME R.4). |
| **D2** | **§S29.6's untouched classes** — demolition/refurbishment, calendars (`IfcWorkCalendar` captured by PR #59, consumed by nothing), procurement lead times, site/external works | Any claim that the design is general. Named, never examined. |

## §S37.5 — the standing rule this ledger adds

**A measurement is not finished when it is written down. It is finished when it is either (a) an
open PR, (b) a decision put in front of the user, or (c) an entry in this ledger with the reason it
is neither.** Any session that produces a number and ends without doing one of those three has
dropped it — which is how §S37.1's A1 and A2 sat idle, and how LTU's storey ladders were missed by
two consecutive sessions.

---

# §S38 — B5 ANSWERED: the storey NAME carries the ladder. The invented constants go; a new gate opens. (2026-08-19)

Instrument: `scripts/probe_s37_name_derived_ladders.js` (`§S37_SELFTEST 7/7` — the family parser
splits `VÅN`/`VÅNING`, reads a leading digit as an ordinal not a family, reports "no ordinal"; the
ladder check is shown flagging an inverted ladder AND not false-positiving on a clean one; the
partition metric shown at 0 and non-zero). Read-only; `md5sum -c` on all 7 frozen DBs **OK before
and after**. Numbering: `§S37` was taken by the ledger committed in parallel (`c0449334f`) — this is
the answer to its item **B5**, not a competing section.

## §S38.0 — the miss, in one line

`buildGrid()` asked `spatial_structure.center_z` and, finding none, invented a uniform 3m grid for
122,330 LTU elements. It never asked the 121,635 of them (**99.43%**) that carry a storey NAME.

## §S38.1 — Q1/Q2: the ladders exist, and 3 of 4 LTU families are monotonic

Per-name elevation derived as the **median `base_z` of the name's own members** — the statistic §S38.4
then tests rather than assumes.

| building | family | k | elements | derived ladder (m) | monotonic |
|---|---|---|---|---|---|
| LTU | `PLAN` | 4 | **105,253** | 5.25 · 8.58 · 11.72 · 12.79 | **YES** |
| LTU | `VÅNING` | 4 | 6,014 | 1.30 · 4.90 · 7.95 · 11.37 | **YES** |
| LTU | `STOREY` | 3 | 4,067 | 4.56 · 8.21 · 11.46 | **YES** |
| LTU | `VÅN` | 5 | 5,725 | 2.63 · 4.90 · 7.95 · **−0.19** · 16.07 | ⛔ **NO** — `VÅN 3`(7.95) → `VÅN 4`(−0.19) |
| LTU | `REF.` / `TAKPLAN` | 1 each | 297 / 279 | no ordinal — cannot be laddered | n/a |
| Duplex | `LEVEL` | 2 | 123 | 0.00 · 3.22 | YES |
| HHS | `LEVEL` | 3 | 4,674 | 2.87 · 6.25 · 9.85 | YES |

**The offender explains itself.** Member spread (IQR of `base_z`) separates "this name is a floor"
from "this name is not": `Plan 1` 0.55m · `Plan 4` 0.82m · `VÅN 5` 0.01m · `VÅNING 1` 1.14m — versus
**`VÅN 4` 13.87m · `VÅN 2` 10.27m · `TAKPLAN` 9.58m · `VÅN 3` 8.48m**. The four widest are exactly
the family that broke monotonicity plus the un-ordinaled roof plan. `VÅN 4`'s 3,392 elements run from
p25 −0.42m to p75 13.44m — that name is not a storey, it is a bucket. **5,132 LTU elements (4.2%)
sit under a name that is not a single floor.**

Note `VÅN 2` = `VÅNING 2` = 4.90 and `VÅN 3` = `VÅNING 3` = 7.95 exactly — two spellings of one
source model's storeys, which is why family splitting matters and a global grid cannot work.

## §S38.2 — Q3: per-family grids on LTU, measured

`§S37_Q3_COST` / `§S37_Q3_OVERRIDE`, controls printed alongside (self-vs-self **0**, half-band shift
21.58% → `metricResponds=YES`):

| | uniform 3m (shipped) | per-family name-derived |
|---|---|---|
| levels | 11 non-empty bands | 18 groups |
| elements landing in a different level | — | **20,305 (16.69%)** |
| declared coverage | **0** | **121,635 (99.43%)** |
| override rate under the §S34.3 tie-break | n/a (no declared values exist) | **4,008 / 121,635 = 3.30%** |
| still uncovered | 122,330 (100%, all geometry on an invented grid) | **695 (0.57%)**, no name at all |

3.30% sits between Terminal (1.25%) and JKR (11.85%) — LTU stops being the building with no level
structure and becomes an ordinary one. Duplex: 143 named elements only (12.78%), 0 overrides.

## §S38.3 — the constants: BOTH disappear, on this fleet, and one new question replaces them

**`FALLBACK_BAND_M` (3.0m): removable.** A name-derived grid exists on **every** fleet building —
the weakest is Duplex, where 143 named elements (12.78%) still yield four real lines
`[−1.55 (T/FDN) · 0.00 (Level 1) · 3.22 (Level 2) · 6.00 (Roof)]`, on which the remaining 87.22%
place geometrically. `§S37B_GLOBALGRID` prints `fallbackNeeded=NO` for both fallback buildings.
Cost of the swap: LTU 43.24% of elements land differently vs the uniform grid, Duplex 16.18%.

**The clustering bandwidth (`CLUSTER_BW`): removable — it is not needed at all.** §S36.1 only
reached for slab-top clustering because the names had been overlooked. No clustering, no bandwidth,
no `CLUSTER_MIN`.

⚠ **But a NEW question replaces them, and it is not yet answered: the per-name quality gate.**
`VÅN 4` proves a name can be a bucket rather than a floor, and using such a name as a grid LINE
injects a garbage elevation (−0.19m). A candidate gate with no invented constant — *a name's IQR
must not exceed its own family's median inter-level gap, falling back to the building's median gap
for a family of one* — was tested (`§S38_SELFTEST 4/4`, shown returning both answers) and **it is
not good enough**:

| building | names | flagged | flagged elements | verdict |
|---|---|---|---|---|
| LTU | 18 | 5 | 5,429 (4.46%) | ✅ flags exactly `VÅN 2/3/4`, `TAKPLAN`, `Ref.` |
| Hospital · Duplex · HHS | 8 · 4 · 4 | **0** | 0 | ✅ no false positives |
| **Terminal** | 22 | 7 | **6,765 (46.40%)** | ⛔ over-flags |
| **Clinic** | 7 | 3 | **7,779 (71.30%)** | ⛔ over-flags |
| **JKR** | 20 | 10 | **4,390 (54.23%)** | ⛔ over-flags |

Cause, named: those three buildings have mostly single-name families, so the denominator falls back
to the BUILDING's median gap — which on densely-named models is 0.30m (JKR) or 0.98m (Terminal), so
almost every real storey trips it. The gate works where families are ladders and fails where names
are unique-per-storey. **Reported as unsolved rather than tuned into looking solved.**

Mitigation that already exists, measured: with the bad names left IN, LTU's per-family override rate
is still only 3.30% — the §S34.3 tie-break absorbs most of a bad declared level by overriding it.
The gate matters for GRID-LINE construction, not for element assignment.

## §S38.4 — Q4: name-derived vs declared `center_z`, where both exist (23 names, 5 buildings)

| statistic | fleet MAE vs declared | preserves declared order |
|---|---|---|
| median `base_z` | **1.01m** | 4/5 buildings |
| median `center_z` | **0.91m** | **5/5** |
| p10 `base_z` | 1.98m | 5/5 |

The two medians are within 0.10m of each other; `p10` is twice as bad. `median center_z` is also
what the shipped engine already uses (`viewer/schedule_author.js:305-307`), so it is the precedent,
not a new choice. **The one order failure is `median base_z` on JKR — and it is the 1cm twin again**
(`01 Aras Satu` declared 82.89 vs `00 Aras Tanah` declared 82.90; derived puts them 0.97m apart in
the opposite order). §S36.2 already showed that 1cm ordering is a coin flip, so this is weak
evidence against `base_z`, not strong evidence for `center_z`.

**Disagreements are a finding, per the brief.** Hospital tracks within Δ+0.11 to +0.96m on levels
1-5, then drifts to −1.93m on `Level 7` (191 elements — sparse levels derive badly). JKR's derived
elevations sit **0.9–1.9m BELOW** every declared value, consistently. Neither is a defect in the
derivation; both say a derived elevation is a floor-line ESTIMATE with roughly ±1m of scatter, which
is well inside the local storey gap the §S34.3 band uses.

## §S38.5 — this is not only an LTU fix: declared coverage rises fleet-wide

`§S37_NAMES` — elements carrying a storey NAME versus elements the shipped ladder can call declared:

| building | named | currently declared (§S35.2) | distinct names / names with a stored `center_z` |
|---|---|---|---|
| **JKR** | **90.09%** | 19.79% | 20 / 4 |
| LTU | **99.43%** | 0% | 18 / 0 |
| Hospital | 85.03% | 83.38% | 8 / 7 |
| HHS | 69.00% | 68.26% | 4 / 3 |
| **Clinic** | **67.89%** | 35.34% | 7 / 3 |
| Terminal | 30.11% | 22.91% | 22 / 6 |
| Duplex | 12.78% | 0% | 4 / 0 |

**§S31.4's 21-85% "joinability" was measuring the JOIN, not the DATA** — exactly the root cause
§S37 names. Clinic's B3 item (32% `Unknown`) is unchanged by this: those elements genuinely carry no
name and stay on the geometry tier.

## §S38.6 — converted to action, per §S37.5

- **B5 → ANSWERED.** Both invented constants (`FALLBACK_BAND_M`, clustering bandwidth) are removable
  on this fleet; the replacement is name-derived per-family grids. **Nothing adopted here** — the
  brief said measure, not switch, and `level_deriver.js` is unchanged.
- **The change this authorises, when a spec is written:** in `build/level_deriver.js`, `buildGrid()`
  gains a name-derivation tier between T2 and the fallback (per-family grids from member medians),
  and the `uniform3m` branch plus `FALLBACK_BAND_M` are deleted. `levelFor()` gains a T2b tier for
  elements whose name resolves only through the derived ladder. **Not written — that is a spec, and
  specs in this lane get vetted before they get built (§RESUME R.2).**
- **NEW LEDGER ITEM (B6): the per-name quality gate is unsolved.** A derived gate works on 4/7 and
  over-flags on 3/7 (Terminal 46%, Clinic 71%, JKR 54%). Needed for grid-line construction; NOT
  needed for element assignment (the tie-break absorbs bad names — LTU 3.30%). Owner: whoever writes
  the B5 spec. Do not adopt the IQR-vs-family-gap rule as it stands.
- **B4 (Terminal furniture) is unchanged and still unexplained.** Nothing here touches it.
