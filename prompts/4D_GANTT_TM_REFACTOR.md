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

# §OBJECTIVE — what this lane exists to fix (user, 2026-08-19)

Two symptoms. Everything else in this file is scaffolding around them.

**1. Hanging ARCH/MEP** — elements appearing before what holds them up is built.
Terminal 4,756 · Hospital 7,753 · LTU 12,712 · JKR 3,183 · HHS 1,531 · Clinic 1,102 · Duplex 247
— ⚠ **these are PRE-#1438 figures** (measured before the witness stopped reading deprecated
`_extracted.db` files, §S39.2). On the corrected ruler, `main` at `b81f646` reads Terminal 4,256 ·
Hospital 8,210 · LTU 12,686 · JKR 3,385 · HHS 1,491 · Clinic 1,205 · Duplex 237, fleet 31,470; and
the orphaned §S26.2 fix (`3bf771e`, PR #1440) takes that to 14,166, −55%. **State the commit next
to any number in this lane.** (Strict midair — start before support *starts* — is already
0; it is the start-before-support-*finishes* overlap that reads as floating on screen.)

**2. Stacked Gantt bars** — "one pile, full project length".

**Both are the same defect: scheduling is per ELEMENT, display and measurement are per GROUP.**
`time_machine.js:6074` groups bars by `storey|phase`, but each bar's span is min-start to max-end
over elements scheduled individually.

⛔ **CORRECTED 2026-08-19 by §S43 — this section used to say "one stray element stretches the whole
bar." That is measured to be FALSE.** The median bar owes **0.4–3.3%** of its width to its single
worst element, and deleting that element clears 0–3 wide bars out of 15–35 (zero on Hospital). Bars
are wide because their members are genuinely DISPERSED across the programme — the Tukey-trimmed
span, which already discards both outlier tails, still covers 17–30% of the project at the median.
So the shipped trim is not a weak version of the fix; outlier removal is the wrong lever entirely,
and "fix the grain — schedule the group" means constraining members to a contiguous window, i.e. a
SCHEDULING change, not a display one. §S37 B1 stands as the target; its stated mechanism does not.

**Four measured causes, all unshipped:**
- support predicate counts a pipe as holding up a wall — 760 of 761 Duplex physics-vs-phase
  contradictions (§S26.2)
- `hang` rule is 93-99.8% redundant and the sole cycle source; deleting it → acyclic 7/7 (§S26.3)
- the blob destroys the dependency chain — uncapped crews, live engine finishes Terminal in 0.6d,
  i.e. nearly everything at once (§S31.1)
- a Gantt bar is a hull (§S26.13, above)

**Progress is measured by those seven float numbers moving, and by bars ceasing to span the
project. Not by new sections in this file.**

---

# §STATUS — read this first (2026-08-19, end of a full consolidation)

> **Resuming in a new session? Read `# 🔄 §RESUME` at the END of this file first — it names the
> standing rulings and the required order of work. Both agents it lists have since REPORTED (§S33);
> §S34 then decided the tie-break §S33.1 left open and §S35 built the derivation. Read `# §OBJECTIVE` above FIRST — it
> states the two symptoms this lane exists to fix, and neither has moved. The next step is to ship
> one of the four measured causes, not to measure again.**

**Nothing has shipped. `viewer/` is unchanged.**

**⚖ STANDING RULING — §S32 (user, 2026-08-19), read before acting on ANY section below:** the
extractor is CORRECT and must not be changed; `buildings/*.db` EXTRACTION tables are FROZEN (§S32.6: the
SCHEDULE tables — `schedules`/`tasks`/`task_sequences`/`task_elements`/`calendars` — ARE writable); every derived fact is computed at RUNTIME, ONCE, on load — the room-injection pattern.
§S32.2 lists what this CANCELS in §S27, §S28 and §S31, including the Hospital rebuild those sections
called for.

| § | what it is | standing |
|---|---|---|
| §S25_REVIEW | outside review + what measurement did to it | **HOLDS**, but its "ship-ready" §S25_REVIEW.6 claim is DEAD — killed by §S33.2 (mis-attributed comparison) |
| §S26 | evidence base — the blob is predicate-made, `hang` redundant, LBMS/trains, the IFC container already exists and is empty | **HOLDS** — independently re-derived by two adversarial passes |
| ~~§S27 / §S28~~ | both NOT VETTED designs | **ARCHIVED 2026-08-19** — `prompts/archive/…_S27-S28_rejected_2026-08-19.md`. Not build targets. |
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

---

# §S27-§S28 — ARCHIVED (2026-08-19), and §RESULTS superseded

Two rejected designs and one superseded session-close note, moved to
**`prompts/archive/4D_GANTT_TM_REFACTOR_S27-S28_rejected_2026-08-19.md`**, verbatim.
**Neither design is a build target and neither should be revived as written.**

- **§S27 "the grid"** — NOT VETTED, 4 blocking (§S27.R). Its zone compiler wrote to the DB, which
  §S32.1 rule 2 later forbade outright.
- **§S28 "two lanes"** — NOT VETTED, 4 blocking (§S28.R). Its central premise, that the two lanes
  are independent, was shown false in both directions.
- **§RESULTS (2026-08-18)** — self-described as *"pending a full spec overhaul, not a continuation
  point."* That overhaul happened; `# §STATUS` replaces it.

The facts that survived them are live: §S32 (ruling) · §S37 (ledger) · §S41 (working rules) · and
the measured sections §S26 · §S29 · §S30 · §S31 · §S33-§S36 · §S38 · §S39.

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

---

# §S41 — LANE WORKING RULES (2026-08-19, from user corrections in-session)

**Where these live and why.** `CLAUDE.md` is explicit: *"A session working a `prompts/#.md` file
updates ONLY that file, never `MEMORY.md`"* — because task sessions writing their own memory entries
is the documented root cause of `MEMORY.md` bloat and drift. These rules were briefly written to
memory during this session; that was a rule violation, reverted, and they belong here. **Recorded so
the next session in THIS lane inherits them without touching memory.**

## §S41.1 — PM hat: decide from your own evidence; do not relay an outside reviewer's framing

USER: *"I told u your role and let you decide, not blindly follow. Harden your PM hat."*

Said after I brought back an outside AI reviewer's conclusion ("stop cleaning, ship the sort") and
proposed it as the next step — **re-ordering a plan the user had already approved** (consolidate the
specs first). I had also adopted that reviewer's metric ("390 accumulated §-fixes") as my own before
checking it; **240 of the 390 were `console.log` tags this project's own Log Mandate requires.**

**An outside sounding board is useful for QUESTIONS and unreliable for FACTS.** In the 2026-08-19
exchange not one of its factual claims survived checking:
- *"250-300 of 390 §-fixes would disappear if the importer is fixed"* — measured 37/390, off ~7×.
- *"`IfcRelSequence` present in source, 0 rows in DB"* — **fabricated**; all 7 JKR discipline files
  grep to `IFCRELSEQUENCE:0`. Nothing was dropped.
- *"107 containment rows — synthesized?"* — 50 across files, normal composition.
- *"The extractor is broken"* — it is not; the DBs are stale (§S31.2).
- *"We have a validated sort, ship it"* — false; 2/7 fail under real crew caps.

Several of its QUESTIONS were excellent and produced real findings (the 83.0%/64.6% correctness
metric, the merge resolution, the spatial-hierarchy probe). **The value is inverted from how it
presents.**

**Apply:** take the questions, discard the answers, verify before acting. Never let an outside voice
re-order a sequence the user already agreed to — if it argues for a different order, say so and let
the user decide, do not arrive with the new plan as if it were mine. Before relaying any outside
claim, check it; if it fails, say so in the same message rather than repeating it as a premise. When
my own evidence contradicts the outside view, **lead with my evidence.**

## §S41.2 — a measurement is not finished until it is actioned

USER: *"this is repetition! Already done a few posts ago. Why don't u record what was done?"* and
later *"Why isn't this spec'd when we did go thru it? What else is forgotten?"*

Findings were reported in conversation and not written to this file, so adjacent measurements got
re-run. Findings that WERE written still went nowhere: the `hang`-deletion result (acyclic 7/7,
branch pushed) sat with **no PR ever opened**, and **7 red witness baselines sat undiagnosed for a
full day**. The §S29 generality audit sat unwritten in a scratchpad for hours while the same ground
was re-measured.

**The specific trap behind the worst miss: a measurement of a LOOKUP was read as a fact about the
DATA.** §S31.4's *"storey name joins to a stored elevation only 21-85% of the time"* was recorded as
*"elevation data is missing"* — when the name WAS the grouping and the elevation could be derived
from its own members. **A partial JOIN is not absent DATA.** Two consecutive sessions missed LTU's
floor structure because of it (§S37 header).

**Apply:** write the number into this file in the SAME turn it is produced — not at session end, not
in a scratchpad. **A measurement is finished only when it is (a) an open PR, (b) a decision put in
front of the user, or (c) a §S37 ledger entry stating why it is neither.** Check §S37 before
starting new measurement. When a measurement comes back partial, ask whether it is the right proxy
before concluding the data is absent.

## §S41.3 — this section is lane-scoped, deliberately

These are working rules for the 4D lane, not global doctrine. **Do not promote them to `MEMORY.md`.**
If they prove to generalise across lanes, that is a separate, deliberate synthesis pass by a session
whose job is memory — not a byproduct of finishing a task here.

---

# §S39 — A2 DIAGNOSED: what moved the 7 W-MZ-8 baselines, and a second defect in the same ruler (2026-08-19)

**A2 asked: all 7 W-MZ-8 baselines are RED on main, `pass=32 fail=7`, undiagnosed. Find what moved
them.** Answer: **PR #1434 moved all 7, then PR #1435 moved all 7 again. Neither re-locked.** Found
by commit-by-commit bisect, not by reading commit messages — and the first message read was wrong.

## §S39.1 — the bisect (instrument: `viewer/tests/witness_midair_zero.js`, unchanged, DBs unchanged)

Run on the two fastest buildings at each commit between the lock and main; the DB files are byte-
identical across every run (`BLD_DIR` defaults to the same `~/bim-ootb/buildings` in every commit),
so this isolates CODE from DATA:

| commit | PR | W-MZ-8 |
|---|---|---|
| `8f8d3de` | #1430 — §S20 Part B, where the baselines were locked | ✅ **GREEN** (Duplex 289=289, HHS 1538=1538) |
| `07d6744` | #1431 ScheduleEngine single-source class | ✅ GREEN |
| `dd3a746` | #1432 buildGanttTasks Tukey fence | ✅ GREEN |
| `035561e` | #1433 computeDays axis-end Tukey fence | ✅ GREEN |
| `5ea6fcf` | **#1434 — E3 gate no longer exempts stragglers from their own phase's completion** | ⛔ **RED, all 7** |
| `6a395ca` | #1435 designatedSupport + directional floating judge | ⛔ RED, all 7, different numbers |
| `13700fd` | main today | ⛔ RED, all 7 (`pass=32 fail=7`, and W-MZ-8 is the ONLY failing invariant) |

| building | locked (§S20) | after #1434 | after #1435 = main | net vs lock |
|---|---|---|---|---|
| Terminal | 8,789 | 10,086 | 10,011 | **+1,222 worse** |
| Hospital | 5,107 | 8,466 | 8,103 | **+2,996 worse** |
| Duplex | 289 | 239 | 237 | −52 better |
| HHS_Office_Federated | 1,538 | 1,606 | 1,491 | −47 better |
| Clinic | 3,523 | 958 | 1,205 | −2,318 better |
| LTU_AHouse | 15,896 | 15,296 | 12,686 | −3,210 better |
| JKR | 3,736 | 3,656 | 3,385 | −351 better |

**5 of 7 improved; Terminal and Hospital got worse.** Both movers are merged, intentional,
independently-verified correctness fixes (#1434 is one of §RESULTS' five bugs — the straggler
exemption that let a phase count as done while 54-100% of it wasn't). So the schedule legitimately
moved and **the lock, not the engine, is what is stale.**

**⛔ Correction to #1435's own commit message.** It says W-MZ-8 "now fails against its old locked
baseline numbers" *because of that fix*, and defers the re-lock. Measured: all 7 were **already red
at `6a395ca^`**. #1435 inherited a broken lock and attributed it to itself. §S33.2's framing
("something moved all 7 and was never re-locked") was right that the cause was unknown; the cause is
#1434, one PR earlier.

## §S39.2 — the SECOND defect, found while fixing the first: the ruler reads DEPRECATED DBs

`witness_midair_zero.js:169` — `const DB_FILE = { LTU_AHouse: 'LTU_AHouse_meta.db' };` and
`:242` falls back to `<bld>_extracted.db` for everything else. **Terminal, Hospital and Clinic all
HAVE a `_meta.db`, and the witness reads their deprecated `_extracted.db` instead:**

| building | `_extracted.db` | `_meta.db` | witness reads |
|---|---|---|---|
| Terminal | Jun 5 | **Aug 17** (patched, PR #1427) | ⛔ extracted |
| Hospital | Aug 3 | **Aug 17** (patched, PR #1428) | ⛔ extracted |
| Clinic | Aug 3 | Jun 6 | ⛔ extracted |
| LTU_AHouse | Aug 3 | Aug 10 | ✅ meta |
| Duplex · HHS · JKR | — | none | ✅ n/a |

**Measured cost, same engine (main), only the DB file changed:**

| building | on `_extracted.db` | on `_meta.db` | delta |
|---|---|---|---|
| **Terminal** | 10,011 | **4,256** | **−57.5%** |
| Hospital | 8,103 | 8,210 | +107 |
| Clinic | 1,205 | 1,205 | identical |
| Duplex · HHS · LTU · JKR | 237 · 1,491 · 12,686 · 3,385 | unchanged | — |

**Terminal's W-MZ-8 number is 2.4× larger on the deprecated file.** Every Terminal float figure in
this lane measured through this witness carries that error. This is the same landmine §RESULTS'
addendum hit on 2026-08-18 (stale `Terminal_extracted.db`/`Hospital_extracted.db` vs the same-day
patched `_meta.db` pair) — it was diagnosed then, a branch `fix/witness-dbfile-resolution` was
created for it, and **that branch has zero commits on it.** Another named-never-worked item.

## §S39.3 — why the re-lock is HELD, not pushed

A re-lock to main's current numbers would bless **10,011 for Terminal — a number measured on a
deprecated DB.** Correct order, stated so it is not re-derived:

1. **Fix `DB_FILE` resolution first** — prefer `<bld>_meta.db` whenever it exists, `_extracted.db`
   only when it does not. One line; the branch for it already exists and is empty.
2. **Then re-lock all 7 in the SAME PR**, to the meta-measured numbers: Terminal **4,256** ·
   Hospital **8,210** · Duplex **237** · HHS **1,491** · Clinic **1,205** · LTU **12,686** ·
   JKR **3,385**. Verified locally: `pass=39 fail=0` with the extracted-DB numbers, and the
   meta-DB numbers above are measured, not projected.
3. **Only then judge A1.** The `hang` deletion's "+722 Hospital / +227 JKR" regression was scored
   against the stale §S20 lock AND (for Terminal/Hospital) against a deprecated DB. Both halves of
   the ruler were wrong; A1's trade must be re-measured before it is decided.

**Nothing pushed to `bim-ootb`.** Worktree `/tmp/wt-wmz-pre` holds branch
`fix/wmz8-relock-after-1434-1435` with no commits (the re-lock edit was verified green, then
reverted pending the DB-resolution decision above).

---

# §S42 — THE HISTORICAL ANCHOR: is the graph era ahead of the engine it replaced? (2026-08-19)

**Question (user):** the engine before `0fe8eb2` (#1242, 2026-08-07) ordered by sort with physics as
a clock — no graph, no blob, no cycles. Every fix since removes bad edges from the graph that commit
introduced. **Are we ahead of that baseline, or still climbing back to it?**

**Answer: neither, and the framing does not survive the measurement. The two eras fail in DIFFERENT
ways.** Pre-#1242 is better on overlap and makespan; it is catastrophically worse on the symptom
§OBJECTIVE leads with. Reverting the graph would trade a defect the fleet no longer has for one it
does.

## §S42.1 — method: only the SCHEDULER changes

Instrument: `witness_midair_zero.js` with the pre-#1242 `schedule_gate.js` loaded as a SECOND module
(`git show 0fe8eb2^:viewer/schedule_gate.js`), preserved as
`scripts/anchor/anchor_pre1242_witness.js` in this repo. Held constant: the DB files (today's
`_meta.db`, post-#1438 resolver), the element build (`_buildXrayElements`), the rates, the crew
caps, and **both judges** — today's `ScheduleGate.auditFloating` and this witness's own `census()`.
Today's global `ScheduleGate` is saved and restored around the old module's `require`, since it
assigns the global on load. The old signature is unchanged (`computeSchedule(elements, baseMs,
scaleFactor, maxCrews)`), so nothing is adapted or approximated.

**Today's witness cannot simply run at `0fe8eb2^`** — it hard-requires `viewer/cpm_schedule.js`
(#1398, seven weeks later) and slices `_displayTimeline` by source text. Swapping the scheduler
under a constant judge is the only comparison that is actually like-for-like, and it is the one
reported here.

## §S42.2 — the three engines, side by side

**⚠ WHICH COMMIT EACH COLUMN IS (corrected 2026-08-19 after §S40).** The right-hand column is
`3bf771e` — the §S26.2 support-pool fix — which was **squash-orphaned and is NOT on `main`**
(re-landed as PR #1440). Live `main` at `b81f646` still scores the pre-#1439 numbers: Terminal
4,256 · Hospital 8,210 · Duplex 237 · HHS 1,491 · Clinic 1,205 · LTU 12,686 · JKR 3,385.
**§S42's conclusions are unaffected** — they rest on the pre-#1242 vs PRE-CPM comparison, and the
`3bf771e` column only sharpens how much of the float the CPM layer owns. Every number in this lane
now names its commit; there were three live scoreboards in this file at once (§OBJECTIVE's
pre-#1438 figures, main's post-#1438 figures, and this branch's).

| building | pre-#1242 `0fe8eb2^` float / days | PRE-CPM, `b81f646` main float / days | POST-CPM, **`3bf771e` orphan branch** float / days |
|---|---|---|---|
| Terminal | **42** / 123.6 | **5** / 373.2 | 2,151 / 164.7 |
| Hospital | **3** / 331.8 | **0** / 1026.2 | 3,960 / 543.5 |
| Duplex | **0** / 9.7 | **0** / 31.3 | 44 / 15.4 |
| HHS_Office | **141** / 46.8 | **9** / 129.2 | 889 / 98.6 |
| Clinic | **4** / 149.7 | **1** / 464.1 | 877 / 183.5 |
| LTU_AHouse | **611** / 861.2 | **360** / 2500.2 | 5,023 / 1429.0 |
| JKR | **169** / 40.5 | **81** / 118.2 | 1,222 / 45.6 |

Read the middle column first. **Today's scheduler beats the pre-#1242 scheduler on float, on all 7**
(5 vs 42 · 0 vs 3 · 0 vs 0 · 9 vs 141 · 1 vs 4 · 360 vs 611 · 81 vs 169). **#1242's geometry DAG is
not the debt** — on this judge it is an improvement over what it replaced. It buys that with
DURATION: today's raw schedule is 2.5–3.0× longer than pre-#1242's.

**The float is manufactured by the CPM display layer, not by the graph.** Every building's number
explodes between the middle and right columns — 5 → 2,151, 0 → 3,960, 360 → 5,023 — while the
makespan is COMPRESSED by roughly half (373→165, 1026→544, 2500→1429). CPM buys ~2.2× compression
and pays for it in overlap violations. That is the actual mechanism behind §OBJECTIVE's seven
numbers, and it sits in `_displayTimeline`'s CPM branch (#1398), not in #1242.

## §S42.3 — the column that reverses the verdict

`census()`, this witness's own W-MZ-2 judge — **elements appearing before the first thing they
touch**, the visible "hanging in mid-air" symptom:

| building | pre-#1242 `0fe8eb2^` | POST-CPM, `3bf771e` orphan branch |
|---|---|---|
| Terminal | **900** | **0** |
| Hospital | **709** | **0** |
| Duplex | **17** | **0** |
| HHS_Office | **146** | **0** |
| Clinic | **345** | **0** |
| LTU_AHouse | **4,316** | **0** |
| JKR | **108** | **0** |

**The pre-graph engine put 17 to 4,316 elements on screen before the thing they physically touch
existed.** Today's is zero on all 7 and has been since the graph era matured. §OBJECTIVE states this
as already-solved ground ("strict midair … is already 0"); the anchor shows what solved it.

## §S42.4 — the honest conclusion

- **"The last five weeks were debt repayment" is not supported.** #1242's own scheduler is better
  than its predecessor on the overlap judge, and the graph era eliminated a defect class
  (strict midair) that the pre-graph engine had in the thousands.
- **"Reverting the graph is the shortest route" is refuted by the same run.** It would trade
  44–5,023 overlap violations for 17–4,316 mid-air appearances, plus lose every gate built since.
- **What the anchor DOES change: the target moves.** The float §OBJECTIVE tracks is created almost
  entirely by the CPM display layer compressing the schedule ~2.2×, not by the support graph. The
  next place to look is `_displayTimeline`'s CPM branch, not #1242.
- **Trade-off, stated:** pre-#1242 was 2.5–3.0× faster in makespan than today's raw scheduler. That
  is a real advantage and it is not free — the ~3× spread is how today's raw schedule keeps float
  near zero. Whether the product wants short-and-overlapping or long-and-clean is a user decision
  this section does not make.

**Caveat on the judge:** `auditFloating` counts "starts before a support FINISHES" and `census()`
counts "appears before a support APPEARS". Neither measures whether the trade order is sensible.
Pre-#1242 ordered by `seq`, so phase order alone satisfied most of the finish-before-start test —
which is exactly why it scores well there and fails the physical one.

---

# §S40 — WATCHDOG: PR #1439 IS NOT ON `main`. The lane is scoring itself against a commit that
# never landed. (2026-08-19, verified — id §S40 taken because it was free, §S41/§S42 untouched)

**One line: `3bf771e` (#1439) is not an ancestor of `origin/main`.** It lives only on
`origin/fix/wmz8-relock-after-1434-1435`. GitHub shows the PR `MERGED`, because it *was* merged —
**into a branch that had already been squashed into main 18 minutes earlier.**

```
$ git merge-base --is-ancestor 3bf771e origin/main   →  NO
$ git branch -r --contains 3bf771e                   →  origin/fix/wmz8-relock-after-1434-1435   (only)
$ gh pr view 1439 --json baseRefName                 →  base=fix/wmz8-relock-after-1434-1435
   #1438 merged 12:33Z  (squash of that branch → main = b81f646)
   #1439 merged 12:51Z  (into the branch, post-squash)  ← orphaned here
```

This is the exact failure `CLAUDE.md` names: *"a squash-merge + a late push orphans the new commit
(observed PR #138, 2026-06-05). After a branch is squash-merged, start the follow-up off fresh
`origin/main` — never re-use it."* The branch was re-used.

## §S40.1 — the cost, measured on both commits with the SAME locked witness

`viewer/tests/witness_midair_zero.js`, unmodified, `BLD_DIR=~/bim-ootb/buildings`, run twice in two
detached worktrees. **Both runs `pass=39 fail=0`** — each commit is internally green against its own
lock, which is why nothing has flagged this.

| building | `origin/main` (b81f646, LIVE) | `3bf771e` (#1439, orphaned) | what live is missing |
|---|---|---|---|
| Terminal | **4,256** | 2,151 | −2,105 |
| Hospital | **8,210** | 3,960 | −4,250 |
| LTU_AHouse | **12,686** | 5,023 | −7,663 |
| JKR | **3,385** | 1,222 | −2,163 |
| HHS_Office_Federated | **1,491** | 889 | −602 |
| Clinic | **1,205** | 877 | −328 |
| Duplex | **237** | 44 | −193 |
| **fleet** | **31,470** | **14,166** | **−17,304 (−55%)** |

Instrument named per §STATUS: W-MZ-8 `auditFloating` post-`_displayTimeline`; the can-fail guard
W-MZ-7 (`judge catches a re-introduced hanging`) PASSED on 7/7 in **both** runs, so neither column is
a false zero. DB resolution logged per building (`§W_MZ_DBFILE`): meta for Terminal/Hospital/Clinic/
LTU, extracted for Duplex/HHS/JKR — the post-#1438 rule, identical in both runs.

**Three different scoreboards are now live in this one file:** §OBJECTIVE line 77 (`Terminal 4,756`
— pre-#1438), main's lock (`4,256`), and §S42's right-hand column (`2,151` — the orphan). Only the
middle one describes what a user loads today.

## §S40.2 — recovery is a 130-line cherry-pick, verified clean

`3bf771e`'s parent `7e1d0dc` is content-identical to main's `b81f646` (the squash), so the commit
lifts straight across; only a *merge* of the branch conflicts (stale merge base in
`witness_midair_zero.js`).

```
$ git diff 3bf771e^ 3bf771e | wc -l                        →  130
$ git apply --check   (against a worktree at origin/main)  →  clean, 4 files
   viewer/schedule_gate.js · viewer/cpm_schedule.js · viewer/time_machine.js
   viewer/tests/witness_midair_zero.js  (the 7 W-MZ-8 locks: 4256… → 2151…)
```

Not done by this watchdog session — it is a push to `main` and belongs to the builder. **Until it
lands, `origin/fix/wmz8-relock-after-1434-1435` is the only copy of a −55% fleet result.** Deleting
it as "merged" destroys the work.

## §S40.3 — §S42 JUDGED: method sound, numbers reproduced, one label wrong

Checked rather than taken on report (§S41.1). Independent re-run in a separate worktree at
`origin/main`, with a coverage instrument §S42's probe did not print:

- ✅ **The anchor module is authentic.** The probe loads a copy from another session's scratchpad;
  `diff` against `git show 0fe8eb2^:viewer/schedule_gate.js` → **byte-identical**.
- ✅ **The judge really is held constant.** `SG_PRE1242` is used for `computeSchedule` only; both
  columns are scored by today's `ScheduleGate.auditFloating` and this witness's own `census()`.
- ✅ **Every pre-#1242 number reproduces exactly**: float 42 · 3 · 0 · 141 · 4 · 611 · 169, midair
  900 · 709 · 17 · 146 · 345 · 4,316 · 108 (`§S42_ANCHOR`, my run, `scripts/anchor` probe + coverage).
- ✅ **NEW — the population bias I went looking for is not there.** The probe writes `mOld` under
  `if (r)` and filters `oldSched[e.guid]`, so a scheduler that skipped elements would score low for
  free. Measured: `pre1242Scheduled == todayScheduled == pop` on **7/7**
  (48,428 · 63,182 · 1,119 · 6,839 · 16,071 · 122,330 · 8,985). Full-population, fair comparison.
- ⛔ **The column header "today POST-CPM (#1439)" is not today.** It is §S40's orphan. §S42's own
  conclusions survive — they rest on the pre-#1242 vs today-PRE-CPM pair, which is unaffected — but
  the right column overstates the live engine by 2.0–2.5× per building.

## §S40.4 — the finding §S42 and this run agree on, from opposite directions

`witness_midair_zero.js` has been printing this all along and no section records it: **the base
scheduler leaves almost no float; `_displayTimeline`'s CPM branch manufactures nearly all of it.**

| building | midair before → after | float `auditFloating` before → after (main) |
|---|---|---|
| Terminal | 1,278 → **0** | 5 → **4,256** |
| Hospital | 1,890 → **0** | 0 → **8,210** |
| Duplex | 54 → **0** | 0 → **237** |
| HHS_Office_Federated | 309 → **0** | 9 → **1,491** |
| Clinic | 604 → **0** | 1 → **1,205** |
| LTU_AHouse | 5,551 → **0** | 360 → **12,686** |
| JKR | 242 → **0** | 81 → **3,385** |
| **fleet** | **9,928 → 0** | **456 → 31,470 (69×)** |

Same judge on both sides (`_floatAt()` re-reads the same `items` array `__dt` mutates in place), so
only the times differ. **§OBJECTIVE symptom 1 is not produced by the scheduler, by `hang`, or by the
support predicate — it is produced by the display re-authoring pass, and it is the price paid for
taking strict midair to 0 and halving the makespan.** That is a trade the product has never been
asked about. It also puts §S37 B1 (the hull) and this in the same place: `_displayTimeline` + the
`time_machine.js:6074` grouping are one target, not two.

## §S40.5 — actioned per §S37.5

| # | item | state |
|---|---|---|
| **E1** | **Land #1439 on `main`** — cherry-pick `3bf771e`, re-run W-MZ, PR. −55% fleet float. | ⛔ **DECISION FOR THE USER/BUILDER.** Verified clean; not this session's to push. |
| **E2** | **Do not delete `origin/fix/wmz8-relock-after-1434-1435`** until E1 lands. | ⚠ standing warning |
| **E3** | **§OBJECTIVE's numbers are stale** (line 77 = pre-#1438). Re-state them from main's lock once E1 settles, and say which commit they describe. | open |
| **E4** | **`scripts/anchor/anchor_pre1242_witness.js` `require`s another session's `/tmp` scratchpad** — a `/tmp` clear makes §S42 unreproducible. Vendor the module or read it via `git show`. | open, one line |
| **E5** | The float is made by the CPM layer (§S40.4) — fold into B1's target rather than opening a new lane. | open |


---

# §S43 — B1 MEASURED: the bars are wide, but NOT because of one stray element (2026-08-19)

**Measured before designing, at `_displayTimeline`'s own output and `time_machine.js:6074`'s own
grouping — not in a separate lane.** Instrument: `scripts/hull/hull_probe.js` (a copy of
`witness_midair_zero.js` with a hull measurement added; copy into `bim-ootb/viewer/tests/` to run),
on `3bf771e` — the §S26.2 branch, i.e. post-#1439 float. Read-only. The task tables hold 0 rows on
every fleet building (§S26.13), so `storey|phase` IS the live grouping, as the code's own fallback.

## §S43.1 — the symptom is real and it is widespread

`§S43_HULL` — a "bar" is one `storey|phase` group; RAW = the hull its members actually describe
(`min start … max end`), TRIMMED = what ships after `§GANTT_MINI_TRIM`'s Tukey fence:

| building | bars | project days | RAW >50% | RAW >80% | TRIMMED >50% | TRIMMED >80% | median RAW | median TRIMMED |
|---|---|---|---|---|---|---|---|---|
| LTU_AHouse | 58 | 1,429 | **35** | **25** | 21 | 16 | **74.4%** | 29.8% |
| Duplex | 16 | 15.4 | 8 | 2 | 3 | 2 | **68.7%** | 18.1% |
| HHS_Office | 17 | 98.6 | 9 | 1 | 4 | 0 | **60.0%** | 29.7% |
| Hospital | 35 | 543.5 | 15 | 10 | 11 | 6 | 25.8% | 17.3% |
| Terminal | 72 | 164.7 | 25 | 6 | 12 | 5 | 30.1% | 17.1% |
| JKR | 64 | 45.6 | 14 | 4 | 8 | 1 | 26.6% | 16.5% |
| Clinic | 32 | 183.5 | 9 | **9** | 7 | 3 | 22.9% | 18.4% |

**On LTU 60% of bars span more than half the project and 43% span more than 80% of it**, even
before the trim. Clinic is the sharp case: every one of its 9 wide bars is >80% — there is no
middle. The trim is doing real work (median bar 74.4% → 29.8% on LTU, 68.7% → 18.1% on Duplex) but
it does not fix the shape: 21 of LTU's 58 bars still cross half the project AFTER trimming.

## §S43.2 — ⛔ the stated cause does not survive measurement

§OBJECTIVE and §S37 B1 both say: *"one stray element stretches the whole bar."* Measured, per bar:
the share of the raw hull that disappears if you delete the ONE element that shortens it most
(the earliest starter or the latest finisher, whichever helps more):

| building | median share of hull owed to ONE element | bars where one element owns >50% | RAW>50% bars, before → after dropping that element |
|---|---|---|---|
| JKR | **3.3%** | 8/64 | 14 → 11 |
| Terminal | **1.9%** | 9/72 | 25 → 22 |
| Clinic | 0.8% | 4/32 | 9 → 7 |
| Duplex | 0.7% | 1/16 | 8 → 7 |
| LTU_AHouse | 0.5% | 5/58 | 35 → 31 |
| Hospital | 0.4% | 2/35 | **15 → 15** |
| HHS_Office | 0.4% | 3/17 | 9 → 6 |

**The median bar owes 0.4–3.3% of its width to its worst single element.** Deleting that element
outright removes at most 4 wide bars on any building and removes NONE on Hospital. **A bar is not
wide because one member is late. It is wide because its members are genuinely spread across the
project** — the trimmed span, which throws out the whole outlier tail on both sides, still covers
17–30% of the project at the median and >50% on 3–21 bars per building.

This is the §S30/§S31.3 pattern again: a mechanism written down before it was measured, refuted by
its own measurement. The symptom is real; the named cause is not it.

## §S43.3 — what that means for B1's scope

- **An outlier fix cannot deliver this.** Neither a better trim nor removing stragglers moves the
  numbers above — the shipped Tukey trim is already the strong version of that idea and it leaves
  LTU with 21 bars over half the project.
- **"Schedule the group" is therefore not a display change, it is a SCHEDULING change.** Making a
  `storey|phase` bar narrow means constraining its members to a contiguous window — which changes
  when elements are built, not how they are drawn. That is a much larger claim than "fix the grain"
  suggests, and it needs its own vetted spec.
- **The dispersion has a known source.** §S42 measured that `_displayTimeline`'s CPM pass compresses
  the makespan ~2.2× and manufactures essentially all of the lane's float (fleet 456 → 31,470 on
  `b81f646`). A pass that interleaves work to compress duration is precisely what spreads one
  group's members across the whole programme. **Symptom 1 and symptom 2 share one owner —
  `_displayTimeline` — which is what §OBJECTIVE claims, but through dispersion, not through hulls.**
- **What has NOT been measured yet, and is the next honest step:** how much of a bar's dispersion is
  crew-capacity levelling (a real constraint, correct to keep) versus CPM interleaving (a choice).
  That number decides whether narrowing bars costs schedule duration or is free.

**Nothing designed, nothing built, no grid.** `viewer/` unchanged by this section.

---

# §S44 — THE SPLIT: dispersion is CPM interleaving, NOT crew levelling (2026-08-19)

**The one question §S43 named and §S42 pointed at, answered in one run.** Instrument:
`scripts/hull/split_probe.js` (copy into `bim-ootb/viewer/tests/` to run), on `9db62a6` — main with
#1440 landed. Read-only. Same elements, same `_meta.db` files, same rates, same judges
(`ScheduleGate.auditFloating` + `time_machine.js:6074`'s own `storey|phase` grouping — the task
tables hold 0 rows fleet-wide, §S26.13, so that fallback IS the live grouping).

Three configurations. **A vs C isolates crew levelling. B vs A isolates the CPM pass.**

| | scheduler | crew caps | levelling | CPM interleaving |
|---|---|---|---|---|
| **C** | `computeSchedule` | **off** (uncapped) | no | no |
| **A** | `computeSchedule` | **shipped** | yes | no |
| **B** | `+ _displayTimeline` CPM | **shipped** | yes | **yes** — what ships |

## §S44.1 — median bar span: levelling does nothing, CPM does everything

| building | C caps off | A caps on | **levelling Δ** | B + CPM | **CPM Δ** |
|---|---|---|---|---|---|
| Terminal | 13.6% | 12.6% | **−1.0pp** | 30.1% | **+17.5pp (2.4×)** |
| Hospital | 22.6% | 22.7% | **+0.1pp** | 25.8% | +3.1pp |
| Duplex | 13.4% | 13.4% | **0.0pp** | 68.7% | **+55.3pp (5.1×)** |
| HHS_Office | 30.7% | 33.3% | +2.6pp | 60.0% | **+26.7pp (1.8×)** |
| Clinic | 13.6% | 12.0% | **−1.6pp** | 22.9% | +10.9pp (1.9×) |
| LTU_AHouse | 14.0% | 13.9% | **−0.1pp** | 74.4% | **+60.5pp (5.4×)** |
| JKR | 20.5% | 20.4% | **−0.1pp** | 26.6% | +6.2pp |

**Levelling moves bar width by −1.6 to +2.6pp and the sign goes both ways — it is noise.** CPM
multiplies it 1.1× to 5.4×. Same story on wide bars (>50% of the project), fleet totals:
**C 52 → A 53 → B 115.** Levelling adds ONE wide bar across the whole fleet; CPM adds sixty-two.

Float behaves identically: fleet **456 → 456 → 14,166**. Crew levelling creates no float at all.

## §S44.2 — but "free" is the wrong word, and this is the fork

Levelling is not what widens bars — **it is what costs duration**, and CPM is what buys it back:

| building | C days | A days | levelling cost | B days | CPM saving |
|---|---|---|---|---|---|
| Terminal | 287.2 | 373.2 | +30% | 164.7 | **−56%** |
| Hospital | 740.0 | 1,026.2 | +39% | 543.5 | −47% |
| Duplex | 22.0 | 31.3 | +42% | 15.4 | −51% |
| HHS_Office | 101.2 | 129.2 | +28% | 98.6 | −24% |
| Clinic | 324.2 | 464.1 | +43% | 183.5 | −60% |
| LTU_AHouse | 1,718.2 | 2,500.2 | +45% | 1,429.0 | −43% |
| JKR | 93.1 | 118.2 | +27% | 45.6 | −61% |
| **fleet** | 3,285.9 | 4,642.7 | +41% | **2,432.3** | **−48%** |

**So narrowing bars is free of the levelling constraint, but not free of duration.** The CPM pass
buys a 24–61% shorter programme and pays for it with wider bars AND essentially all the lane's
float. Turning it off would give narrow bars and near-zero float at **~2.3× the duration** — which
is precisely the §S42 trade, now reaching the same fork from the other symptom. **Two findings, one
question.**

## §S44.3 — ⚖ THE QUESTION FOR THE USER (this is the fork, stated once)

Today's shipped schedule (B) versus the same engine without the CPM display pass (A):

| | duration | wide bars (>50%) | float | strict midair |
|---|---|---|---|---|
| **B — ships today** | **2,432 days** | **115** | **14,166** | 0 |
| **A — no CPM pass** | 4,643 days (+91%) | 53 | **456** | 0 on 5/7 · Terminal 900 · LTU 4,316 ⚠ |

⚠ **A is not simply "the good one"** — the strict-midair column is measured on the pre-CPM times in
§S42 and is NOT zero everywhere; the CPM pass is what drives midair to 0 fleet-wide. So the choice
is three-way, not two-way, and that is the honest statement of it.

**What is NOT measured, and is the actual fix candidate:** whether CPM can keep most of its
compression while being constrained to keep a `storey|phase` group's members contiguous. Nobody has
tried it. If a contiguity constraint costs 5% duration it ships; if it costs 90% it is the same
product choice as above. **That is one measurement, and it is the next one — not a design.**

## §S44.4 — what this closes

- §S43's "named-not-taken" split: **answered — interleaving, not levelling.**
- §S42's open trade and §S43's open trade are **the same fork**, and it is now stated with numbers
  on both axes rather than as two separate lanes.
- **Nothing built, nothing shipped.** `viewer/` unchanged by this section.
