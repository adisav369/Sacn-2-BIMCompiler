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

# §S23 — LANE PROCESS HARDENING (2026-08-17, meta-review finding — PROPOSED, NOT GO'D, do not
# start without an explicit go). Not a schedule/rendering fix. The user asked, after S1-S22, "what
# is missing or lacking in this lane's design that causes constant session drift — is it the
# language or framing?" This is that review's answer, turned into a spec, so it doesn't evaporate.
# ⚠ Was briefly swept into the S1-S22 history archive by a housekeeping pass (2026-08-17/18) despite
# being "proposed, not go'd" — not a closed item, restored here 2026-08-18. Archiving is for DONE
# work; do not re-archive this section until Parts A-D below are actually shipped or explicitly dropped.

**Verdict the review reached:** drift here is mostly ARCHITECTURAL (a real 6-layer pipeline: raw
engine → `CpmSchedule.run` E1-E5 → task-window authoring on a *separate real-calendar clock* →
`kernel_ops` physics timestamps on the TM's *own zero-anchored clock* → render/visibility/xray/DLOD
→ Gantt-drag edit commit — §S22's bug was those two clocks colliding), not sloppy prose — this
file's discipline (dated sections, `§`-log evidence, retractions kept verbatim, `§PATHS NOT TO
TAKE`) is already tight. But one language-level lever DID measurably work: specs that bake an
explicit "if X doesn't hold, STOP — don't force" clause into the SAME paragraph as the ask (§S22's
"if none of the 3 candidates reproduce, report, don't force a fix onto the wrong one") prevented the
next drift instance; specs without one (§S13.6, before that discipline existed) didn't. Four parts,
independently shippable, none touch schedule/rendering code:

**Part A — a one-page layer map.** A single table near the top of this file (after §LOCKED, before
§PATHS NOT TO TAKE) naming, in pipeline order: engine name, file:function, its clock/epoch, and
which stage(s) touched it. Extract this from what S1-S22 already measured — do not re-derive or
guess anything new. Purpose: a fresh session should be able to place a new symptom on this map in
one read, instead of re-discovering (as §S9→S11 and §S13.6→S13.8 both did, the hard way) that the
bug is one layer away from where it was first suspected. Include the near-synonym trap explicitly:
`ScheduleGate` vs `ScheduleAuthor`, `computeSchedule` vs `CpmSchedule.run`, `_displayTimeline` vs
the retired `_twoTierRemap`/`displayRemap` — name which is live, which is retired, in one line each.

**Part B — retrofit the STOP-AND-REPORT escape hatch into §DISPATCH itself.** The current rule 2
list (floating>0, GATE_CHECK>0, new cycle class, unpredicted witness-baseline update, missing
constant) is enumerated, which invites "my situation isn't literally on this list, so proceed" —
exactly the gap §S13.6 fell through (no clause anticipated "the mechanism doesn't run on the live
path"; §S19 Part A anticipated "a witness baseline needs an update" but not "the witness has no
subject left"). Add ONE principle-based catch-all line: *"if a measurement doesn't match what the
task assumed going in, STOP and report the actual finding — do not reframe the hypothesis to fit
what's convenient to ship."* Then audit whether every stage spec from here on states its own
STOP-AND-REPORT condition inline (§S22 did; earlier stages didn't) — make that inline statement a
required part of any new stage spec, not something a session has to remember to check three sections
back in §DISPATCH.

**Part C — name the concurrent-session hazard on this file's own RESUME/HANDOFF block.** S1-S22
landed in one calendar day from what were evidently concurrent sessions (`087c15990 Merge
origin/fable/meshdb-livewire (concurrent session sync)`). The 🏁 block is this lane's single
"read this first" pointer, appended to by whichever session finishes last — a session that starts
reading it while another session is mid-write gets a stale picture, through no fault of the prose.
Add one line at the top of every 🏁 block: `git log -3 --oneline -- prompts/4D_GANTT_TM_REFACTOR.md`
+ confirm no other worktree has this file's branch checked out and dirty, before treating the block
below it as current — mirrors CLAUDE.md's existing "Concurrent branches" rule, stated as a reflex
exactly where the race actually bites in this lane, instead of three files away.

**Part D — verify code-behavior claims against a fresh read, not memory (2026-08-18).** A session
narrated the E3 cycle-breaking mechanism from memory, unread — correct by luck, only confirmed by an
outside check, not itself. Rule (in `CLAUDE.md` Standing Rules, general, not lane-specific): before a
code claim justifies a decision — dispatch, "it's proven" — re-read the file:lines and cite them,
unless already read this turn. Gated at the decision point, not every mention, so it can't become
ceremony. **Confirmed firing correctly same day:** the fork's "carrier-above has no distance cap =
false-support artifact" claim got re-checked against the actual code before being reported settled —
found a pre-existing, deliberate 2026-08-15 design decision (uncapped distance is correct for hung
MEP, tested and chosen on purpose) the fork's framing had missed. Sent back to reconcile instead of
reporting a half-picture. This is Part D working, not a new open item.

**Acceptance:** all four parts are documentation-only — no code, no witness, no fleet-probe rerun
needed. "Done" is the map existing and being accurate to what S1-S22 already measured, the
catch-all clause being added to §DISPATCH, one worked example of the inline-STOP-AND-REPORT
convention on the NEXT stage spec written after this one, and the Part D re-read-and-cite reflex
demonstrably applied at least once more without the user having to ask for it.

**STOP-AND-REPORT if:** Part A's layer map can't be extracted cleanly from what's already measured
in S1-S22 (i.e. it would require guessing at a layer nobody has actually profiled yet) — report
which layer is unclear, don't invent its behavior to complete the table.

---

# ⛔ SUPERSEDED BY §S25 (2026-08-18, same day) — DO NOT IMPLEMENT §S24.3. Kept verbatim as the record
# of how §S25 was reached; §S24_TRIAGE below is the review that superseded it. If you are here to
# build, go to §S25.
#
# §S24 — ARCHITECTURE SPEC: LOCAL PHASE-GATE SHELL, replacing whole-graph Tarjan cycle-breaking
# (2026-08-18, PROPOSED, NOT GO'D — do not implement without an explicit go). A new session must
# triage this spec deeply, end to end, BEFORE any code is touched. If anything below is wrong,
# incomplete, or contradicted by re-checking the code, STOP and report it — do not patch around a
# spec gap silently. Correctness and completeness of the SPEC come first, per this project's own
# Spec-First rule; nothing here has been implemented.

## §S24.1 — Problem statement (real numbers, all measured today, not estimated)

`viewer/cpm_schedule.js` (PR #1396, days old — NOT the same age as the rest of this feature) builds
ONE combined graph across an ENTIRE building — every element, every physical-support edge (E1/E2),
every phase/storey ordering edge (E3/E4/member) — in one shared adjacency structure, then runs Tarjan
SCC over the WHOLE thing. When Round-1 (`solve()`, lines ~424-440) finds a multi-node component, it
drops EVERY E3/E4/member edge touching ANY node in that component — not just the specific edge(s)
that actually close the cycle.

Measured on Terminal (`Terminal_meta.db`, 48,428 elements, 48,513 graph nodes):
- The Ground-Floor Substructure→Superstructure gate fails: all 56 Superstructure elements at that
  storey start (earliest day 15.47) before Substructure fully finishes (day 46.22) — a −30.74 day
  violation, 56/56 offenders, zero exceptions.
- Traced to: the Substructure milestone for Ground Floor sits in a pre-drop SCC of **45,182 nodes —
  93% of the entire graph.** Next-largest component anywhere is size 3.
- Restricted to physics-only edges (E1+E2), the largest genuine cycle anywhere in the whole building
  is **size 5**. 0/56 of the offending columns have any physics-only path back to their own
  milestone's ancestors — they have NO conflict of their own.
- Cause: E4 (storey-band hammock) edges alone number **79,011 — more than the building's element
  count.** That density, combined with a handful of scattered, genuinely-real 3-5-element physics
  knots elsewhere in the building, is enough to glue 93% of the graph into one shared component.
  Round-1 then wipes the phase/storey ordering safety net across that whole component, not just near
  the real knots.
- **Tested and REJECTED**: swapping the E4 grouping key from a derived Z-height band to the real,
  now-patched storey identity cut E4 edges 40% (79,011→47,702) but did NOT shrink the specific giant
  component (45,182→45,558, slightly BIGGER) and did NOT fix the violation (still 56/56 offenders).
  **This rules out "wrong grouping" as the cause** — the failure is the algorithm's granularity
  (whole-SCC blanket drop), not which edges feed it.

## §S24.2 — Precedent this spec is built on, not invented

`viewer/schedule_gate.js` (PR #67, 2026-05-30, ~2.5 months before this spec) already solved the same
CLASS of problem — floating beams — with an explicitly **local, no-shared-graph** design: *"an
element cannot start until the structure topping out within ±0.5m of its base_z is complete... No
CPM/deps."* Verified on real Hospital data at full scale (63,415 elements): floating beams 1128→0,
clean. That design cannot produce today's failure mode BY CONSTRUCTION — there is no shared graph
node for an unrelated local conflict anywhere in the building to collapse through.

## §S24.3 — Proposed architecture

**Core insight:** phase order is a FIXED, KNOWN, small sequence (`TIER1_ORDER`: Substructure →
Superstructure → Architecture, then Tier-2 phases after Tier-1 completes) — not a general graph
requiring cycle detection. It can be computed as a bounded, sequential forward pass instead of a
whole-building shared graph.

**Step 1 — physical support, local, per-element, UNCHANGED.** `designatedSupport()`'s bearing-
below/embedded/carrier-above logic plus §GROUNDED_NEVER_HANGS (grounded elements never get a
carrier-above fallback) is already correct and already local — it does not participate in today's
failure and does not need to change. Keep it exactly as-is.

**Step 2 — phase/storey group finish times, computed as a bounded aggregate, NOT a graph edge.** For
each `(level, phase)` group, in fixed `TIER1_ORDER` sequence (then Tier-2 after Tier-1 completes):
compute the group's finish time = MAX(finish time of every member). A member's own finish time comes
from (a) its Step-1 local physical-support constraint, and (b) the PRECEDING phase-group's
already-computed finish time (a plain number by this point, not a live graph node). Because phase
order is fixed and processed strictly in sequence, group N's gate is always a known constant before
group N+1 is computed — no backward edge, no cycle possible, by construction. Storey/band gating
(today's E4) is the same shape one level up: process levels in real-elevation order (§S24.4 below),
each level's group finish time gates the next level's same-phase group, again a sequential number
comparison, never a shared graph edge.

**Step 3 — narrow, LOCAL override for genuine physics-vs-phase-order conflicts.** For an element whose
Step-1 support constraint would force it earlier than its Step-2 phase gate allows: check ONLY that
element's own direct support relationship against ONLY its own phase gate. If forcing the phase gate
would contradict that element's own physical reality (the bracket-on-wall / hung-MEP / grounded-root
shape already characterized in this file), relax the gate for THAT element alone — matching the
originally-intended narrow exception ("physical support absolute, phase order dropped only for the
specific element(s) genuinely caught"), never for anything it happens to share a milestone with.
This is a per-element numeric comparison (two already-computed times), not a graph traversal — it
cannot cascade to unrelated elements because there is no shared component for it to cascade through.

**Why this can't reproduce today's failure:** there is no whole-building adjacency structure and no
Tarjan pass over it. A local conflict for element X is resolved by comparing two numbers belonging to
X; it has no mechanism to reach, let alone drop a gate for, element Y elsewhere in the building.

## §S24.4 — Known sub-problems this spec must also close, not defer

- **Federated pseudo-level name duplicates are real and wider than previously scoped.** Confirmed
  today: 8 near-duplicate storey-name pairs building-wide on Terminal (not just the known Kedai/
  Jalan/Tanah cluster), ~0.15m apart at every level from ground to roof. The sequential level-order
  in Step 2 must collapse these deliberately (by real elevation proximity, a small explicit tolerance
  band) — NOT accidentally via a derived Z-band the way the current code does. Tested today: treating
  them as fully separate does NOT create a false gate between pairs (checked, 8/8 clean) — so
  collapsing is a cleanliness/precision improvement here, not a correctness blocker, but the
  triaging session must re-verify this on the other 6 buildings, not assume Terminal generalizes.
- **E2 (host/opening pairs)** — already local/per-pair, should compose with this shell unchanged;
  triaging session should confirm no hidden dependency on the whole-graph structure exists.
- **Crew-capacity leveling (§S6_CREW_PASS)** — today's crew-aware Kahn pass walks the SCC
  condensation. Under this spec it would instead walk the much smaller, well-defined sequence of
  phase-groups (Step 2/3's own order) — same crew-pool/claim-slot mechanism, smaller and simpler
  input. Not yet designed in detail — triaging session must specify this concretely before build.
  Do not assume it "just works" without writing it out.
- **Gantt-drag editing** — does an edit require re-running the full sequential pass, or can it be
  incremental? Not addressed by this spec. Named as open, not silently assumed either way.
- **MEP-typing/zone-capacity problem (separate, still open, NOT closed by this spec)**: the
  untyped-`IfcBuildingElementProxy`-defaults-to-Architecture issue and its zone-capacity side effects
  (two failed attempts today, see §RESULTS addendum) are ORTHOGONAL to this architecture change — do
  not conflate the two lanes. This spec does not fix MEP typing, and MEP typing does not block this.

## §S24.5 — Acceptance criteria (the same real test that found today's bug, not a new one invented)

On a real building, storey 1, using the correct current DB (`_meta.db` where one exists, never a
stale `_extracted.db` — check mtimes first, this bit the team once already today): does the LATEST
Substructure finish time come at or before the EARLIEST Superstructure start time? Real days, real
elements, gap ≥ 0. Re-run on Terminal first (Ground Floor: today's exact failing case, −30.74 days),
then the rest of the fleet. The 8-element synthetic sandbox (§E3_SYNTHETIC_SUITE CASE1) stays as a
supplementary regression gate but is NOT sufficient proof by itself — today's whole investigation
exists because it structurally cannot see this failure mode (too small to ever form a large SCC).
`floating=0/7` and `gate_4d.sh` green are necessary, not sufficient, same reason.

## §S24.6 — What this spec does NOT claim

This is a design, not a verified fix. Nothing in §S24.3 has been built or measured. The triaging
session's job is to find what's wrong or missing here BEFORE writing code — if the sequential
group-finish-time computation turns out to have its own hidden cross-group cycle risk, or the
crew-leveling redesign in §S24.4 doesn't actually fit this shape once specified in full, that is
exactly the kind of finding this spec exists to be checked against, not evidence the checker did
something wrong.

---

# §S24_TRIAGE — the deep review §S24 asked for, done before any code (2026-08-18). VERDICT:
# **§S24's diagnosis is real and reproduces exactly; its ARCHITECTURE is not yet buildable as
# written.** Nine defects below, each measured, not argued. §S24 stays PROPOSED, NOT GO'D — and now
# additionally NOT CLEARED TO BUILD until §S24_TRIAGE.3's amendments are made to the spec itself.
#
# Method (per this file's own WATCHDOG mandate — re-derive, don't inherit): every §S24.1 number was
# re-measured from scratch on the DB the viewer actually serves, then the checks §S24 does NOT make
# were added. Tool: **`bim-compiler/scripts/probe_phase_gate.js`** (new, study-only, committed with
# this section) — the first place the §S24.5 acceptance question is asked directly, per (level,
# phase-pair), on BOTH engines. Reproduce: `node scripts/probe_phase_gate.js` (7 buildings, ~12 min).
# Logs behind every number below: `§PG_*` lines of that run.

## §S24_TRIAGE.1 — VERIFIED, independently re-derived (Terminal_meta.db, mtime 2026-08-17T10:12Z, 48,428 elements)

Every headline number in §S24.1 is exact, not approximate:
- Ground Floor Substructure→Superstructure gap **−30.74d**, offenders **56/56**, Substructure finish
  46.22d vs Superstructure start 15.47d — reproduced to 2 decimals (`§PG_GAP_CPM_TABLE`).
- Pre-drop SCC **45,182** nodes of 48,513; next-largest component **3** (`§PG_SCC`).
- Physics-only (E1+E2) largest SCC **5** (`§PG_SCC_PHYSICS`).
- **E4 = 79,011** edges, more than the 48,428 element count (`§PG_EDGES`).
- §S24.2's PR #67 quotes are verbatim from commit `ca1d656` (2026-05-30): "an element cannot start
  until the structure topping out within ±0.5m of its base_z is complete… No CPM/deps", Hospital
  63,415 elements, floating 1128→0.
- The engine under review IS the live one — `time_machine.js:4322` `_displayTimeline` calls
  `CpmSchedule.run` and writes its times onto the display items; the legacy fallback is deleted.
  §S13.6's "the mechanism doesn't run on the live path" trap does NOT apply here.
- One wording correction, not a refutation: Round 1 (`cpm_schedule.js:411-424`) drops every
  E3/E4/member edge whose **both** endpoints are inside a multi-node SCC — not every edge "touching
  ANY node" (an edge leaving the component survives). At 93% coverage the practical effect is what
  §S24.1 says: measured drops **E3 13,079/13,442, E4 70,886/79,011, member 45,086/45,513**.

**New, and it strengthens the motive §S24 only argued on one building:** the pathology is fleet-wide.
Giant pre-drop component as a share of nodes — Duplex 51%, HHS 83%, Clinic 87%, LTU 90%, JKR 91%,
Terminal 93%, Hospital 98%. 0/7 buildings pass the acceptance question today.

## §S24_TRIAGE.2 — WRONG or INCOMPLETE in the spec (T1-T9, ordered by how much they change the build)

**T1 — §S24.2's precedent does not carry the weight the architecture puts on it. The local shell
fails the same acceptance test, on every building.** `ScheduleGate.computeSchedule` has NO
phase-completion gate at all: it has a trade-`seq` gate within one collapsed level
(`schedule_gate.js:886-887 phaseTrade[ph][seq]`) and a per-trade cross-band gate
(`:475 bandGate`) — neither knows the word Substructure. Measured on its RAW output
(`§PG_GAP_RAW`): Terminal 11/17 level-phase pairs negative (worst −21.5d), Hospital 5/10 (−41.5d),
Clinic 7/10 (−14.7d), LTU 18/20 (−112.8d), Duplex 4/4 (−0.4d), HHS 4/4 (−22.0d), JKR 14/17 (−11.7d).
PR #67 solved FLOATING (nothing before its support), which is a different invariant from PHASE
ORDER. So §S24.3 Step 2 is **new code with no working precedent**, not a revert to a design that
already worked — and the spec must stop describing it as the latter. (CPM is still far worse —
Terminal 17/17 −76.0d, Hospital 10/10 −246.6d, LTU 19/20 −747.2d — so the redesign's direction is
not what is in doubt; its stated pedigree is.)
Second-order consequence the spec must also answer: `§4D_BAND_MONOTONIC` (`schedule_gate.js:455-460`)
records a still-standing design ruling that "a global floor gate would serialize the project and
destroy the trade train". A hard phase-completion gate is exactly that shape one level down. Say
explicitly why the same objection does not apply, or which ruling is being superseded.

**T2 — "no backward edge, no cycle possible, by construction" (§S24.3 Step 2) is true of the GATE
edges only. The PHYSICS edges the same pass must honour run backward, in quantity.** Designated
supports whose SUPPORT sits in a strictly LATER (band, phase) group than the element it supports
(`§PG_BACKWARD`): Terminal **2,821/48,116**, Hospital **6,833/62,643 (10.9%)**, Clinic
**3,051/15,740 (19.4%)**, LTU 5,836/119,620, HHS 956/6,786, JKR 566/8,885, Duplex 95/1,107. These
are NOT exotic: the dominant designation class is **cls=0 genuine bearing-below** (Terminal 2,103,
Hospital 4,759, Clinic 2,204, LTU 4,204) — not hung MEP, not carrier-above artifacts. A strictly
sequential group-by-group forward pass reaches these elements with their support's time **not yet
computed**. §S24.3 says a member's finish comes from "(a) its Step-1 local physical-support
constraint" — for 6-19% of elements, (a) is a forward reference. The spec must state the rule:
defer-and-revisit, iterate to fixpoint, or ignore the constraint — and "ignore" silently re-creates
"starts before its support finishes", the invariant already open in the §RESULTS addendum.

**T3 — §S24.3 Step 3 and §S24.5 contradict each other, and the population is large.** Step 3 grants
a per-element relaxation of the phase gate for genuine conflicts; §S24.5 accepts only "LATEST phase-A
finish ≤ EARLIEST phase-B start", which any single relaxed element breaks by definition. The
candidate population is T2's counts (up to 2,821 on Terminal, 6,833 on Hospital). Pick one and write
it into both sections: either (a) relaxed elements are excluded from the acceptance measure AND
their count is locked as a baseline that cannot silently grow (the discipline W-MZ-8 already uses),
or (b) relaxation is forbidden across a Tier-1 phase boundary and only permitted inside Tier-2.

**T4 — Step 1's "designatedSupport() … is already correct … Keep it exactly as-is" is the spec's
most load-bearing unproven claim, and Step 3 makes a wrong support dangerous instead of merely
inert.** Among the backward supports of T2, measured phase pairs include same-level
**Architecture→Superstructure** — a wall designated as the support of a column on its own level:
Hospital 513, HHS 427, Clinic 386, JKR 77, Duplex 14 — and **MEP Rough-in→Superstructure** (Terminal
644, Clinic 914 MEP→MEP cross-level). Today a wrong support merely gets contracted into an SCC.
Under Step 3 it becomes the TRIGGER that licenses a per-element gate exemption — silently, one
element at a time, with no shared component to make it visible. Validate the trigger before building
the mechanism that trusts it.

**T5 — §S24.1's cause attribution ("E4 density + a handful of genuinely-real 3-5-element physics
knots") is incomplete, and the correction points at a much cheaper first experiment.** Ablation on
the identical graph (`§PG_ABLATE`, largest component after removing one population):

| building | base | −backward-E1 | −all E4 | −all E3 | −all E1 | −all E2 |
|---|---|---|---|---|---|---|
| Terminal | 45,182 | **11,531** | 13,045 | 44,490 | 79 | 45,171 |
| Hospital | 62,132 | **19,014** | 21,188 | 52,780 | 1 | 62,132 |
| Clinic | 14,087 | **7** | 2,997 | 13,120 | 7 | 14,087 |
| LTU_AHouse | 110,351 | **685** | 11,256 | 104,571 | 674 | 109,819 |
| HHS | 5,661 | **11** | 3,407 | 2,744 | 1 | 5,661 |
| JKR | 8,232 | **112** | 3,040 | 6,755 | 3 | 8,231 |
| Duplex | 577 | **2** | 143 | 467 | 1 | 576 |

Removing the T2 backward-support edges alone dissolves the pathology outright on 5 of 7 buildings —
a ~2-6% edge population, not the 79,011-edge E4 mass. The size-≤5 pure-physics knots §S24.1 names
are not the closers at all; 2,805 of Terminal's 2,821 backward edges sit inside the giant component.
Two real cycles dumped from it, both a single backward physics edge closed through the forward
gate net: (1) 6 hops, `IfcWall Aras02/Architecture →member→ M(Aras02,Architecture) →E3→
M(Aras02,_T1_COMPLETE) →E3→ IfcFlowTerminal Aras02/MEP-Final →E1→ IfcSlab Aras02/Superstructure
→member→ M(Aras02,Superstructure) →E3→` back; (2) 3 hops, `IfcWall Aras02/Architecture →E2→
IfcWindow Aras01/Architecture →member→ M(Aras01,Architecture) →E4→` back. **Consequence for
sequencing the work:** a bounded experiment — refuse to emit (or defer) an E1/E2 edge that
contradicts the group order, inside the existing engine — is testable in hours against the same
§PG numbers and would discriminate before a whole-engine rewrite is committed to. It is not a
complete fix (Terminal/Hospital keep an 11.5k/19k residual), which is itself information the spec
does not currently have.

**T6 — §S24.4's "E2 … should compose with this shell unchanged" is false as measured.** Host/opening
pairs run backward in group order too: Terminal **650/2,099** host pairs (31%) + 76/802 opening
pairs, Clinic 490/2,737, HHS 206/725, Hospital 172/2,830, LTU 644/5,466 + 849/3,072. T5's cycle (2)
is an E2 edge closing a 3-hop cycle. E2 is a cycle SOURCE today, not a neutral passenger, and the
same forward-reference question as T2 applies to it.

**T7 — §S24.4's federated-duplicate scoping is too narrow, and the part it misses lands directly on
Step 2's group key.** The ~0.15m near-duplicate pairs are real (Terminal's `spatial_structure` has 73
storey rows; Aras 01/02/03/04/Tanah/Bumbung each repeat at ~0.035-0.15m apart). But the same NAME
also appears at datums **15-16m apart**: GROUND FLOOR LEVEL at elev −15.03 AND 0.0; 02 FIRST FLOOR at
−7.03 AND +8.0; 03 SECOND at −3.03 AND +12.0; 04 THIRD at +0.97 AND +16.0; 06 ROOF at 7.55 AND 22.57;
00 Aras Asas at −17.03 AND −2.0. Both engines group by `collapsePhase(storey)` = the NAME;
`deriveStoreyMergeMap`'s elevation-band merge (`schedule_gate.js:373`) is NOT applied on the CPM
path. Element-level effect (`§PG_LEVEL_SPLIT`): Terminal's "GROUND FLOOR LEVEL" pools **236 elements
centred at −15.6m with 1,052 centred at +0.9m**; LTU has 5 such levels ("VÅN 4" = 1,867 at 0.0 +
1,525 at 13.7m); JKR 1. Step 2's "group finish = MAX over members" inherits that pooling wholesale —
so level identity is a **prerequisite**, not the "cleanliness/precision improvement" §S24.4 calls it.
(§S24.4's "collapsing is not a correctness blocker, verified 8/8 clean on Terminal" was checked
against the 0.15m case only; it does not cover this one.)

> **⚠ T7 CORRECTED, same day, before §S25 was written — the finding survives, its headline example
> does not.** The Terminal "236 elements centred at −15.6m" cluster is NOT a second datum: they are
> the 236 `jkrST_str-fo_pc_rcp` **30m precast piles** (bbox_z 30.15, base −30.69, top −0.64) that
> `schedule_gate.js auditFloating`'s own `foundation_pile_misclassified_slab` note already documents
> — real Substructure, correctly on the ground floor, sitting exactly where a pile sits. The error
> was in the detection, not the data: clustering on **centre-z** mistakes any tall element for a
> separate datum. Re-measured with vertical **interval-overlap** clustering (tolerance = the shipped
> `GAP` 0.5m), which a 30m pile and the slab above it correctly share: Terminal 5 levels / **99**
> elements in minor clusters (sub-storey strays, e.g. Level 02 = 170 at 10.8-11.0m + 6 at 11.8-11.9m),
> JKR 3 levels / 31, and **zero on Hospital, Clinic, Duplex and HHS**. The one building where this is
> a real, large defect is **LTU_AHouse: 5 levels, 4,887 elements (4% of 122,330)** whose geometry does
> not overlap their own level's body at all — "VÅN 4" carries 1,867 elements at −1.4..1.4m alongside
> 1,525 at 10.9..16.5m; "VÅNING 3" 158 at −0.6..1.9m alongside 2,049 at 6.0..13.4m. So T7's
> conclusion stands (name-based level identity is unsound and Step 2's MAX-over-members inherits it)
> but its **scope is one building plus strays, not the fleet** — and any level-identity rule must
> cluster by interval overlap, never by centre distance. `probe_phase_gate.js` §PG_LEVEL_SPLIT was
> fixed to the interval rule in the same commit as §S25.

**T8 — §S24.5's DB rule is right for the wrong reason and picks the wrong file on Clinic.** The
viewer fetches `<Building>_meta.db` whenever the meta+geo pair exists (`streaming.js:2190-2218`
§DB_SPLIT_DETECT) — **regardless of mtime**. `Clinic_meta.db` (Jun 6) is OLDER than
`Clinic_extracted.db` (Aug 3) and is still the served half, so "check mtimes first" selects a file
the live viewer never loads. Rule should be "the half the viewer serves". Worse, this is still live
in committed code, not just in one session's ad-hoc run: `viewer/tests/witness_midair_zero.js:169`
maps only `LTU_AHouse` to a `_meta.db` and falls back to `<name>_extracted.db` for everything else —
so **every W-MZ baseline for Terminal, Hospital and Clinic is measured on a DB the live viewer does
not serve** (Terminal_extracted.db is Jun 5, 74 days stale). That is the §RESULTS-addendum
"measurement artifact" still sitting in the fleet gate. Fix it there before any before/after
comparison is trusted; the Duplex/HHS TRADE regression the addendum could not explain is NOT covered
by this (both are single-DB buildings) and stays open.

**T9 — the spec is silent on E1's SS-vs-FS semantics, which is exactly the open question it inherits.**
`cpm_schedule.js:157` emits E1 as **SS** ("T cannot start until S STARTS"). That is why a dependent
can legitimately start before its support FINISHES, which is the separate TRADE invariant W-MZ-8
locks per building (Terminal 8,789, Hospital 5,107, LTU 15,896 — deliberate, baselined, not zero).
§S24.3 Step 1 keeps designatedSupport "exactly as-is" but never says whether the new shell keeps SS.
Under §S24 as written the number stays where it is — so the spec should say so out loud rather than
leave a reader to assume the redesign answers the addendum's open TRADE question. It does not.

**Two smaller ones, named so they aren't discovered late:** (a) §S24.4 calls the crew redesign "same
crew-pool/claim-slot mechanism, smaller and simpler input" — it is not the same ORDER. Today's
allocator is one global priority queue over the whole condensation keyed (earliest feasible time, bz,
guid) with project-wide pools (`cpm_schedule.js` solve, §S6_CREW_PASS); a group-sequential pass hands
slots out in GROUP order, so groups that legitimately overlap in time (the trade train) compete
differently. State the claim order explicitly. (b) Consolidation: this file's own DO-NOT-REMOVE
header mandates ONE class wrapping `computeSchedule → CpmSchedule.run`. §S24 replaces the second
half while `computeSchedule` still runs first (CPM reads only its DURATIONS, `DUR = e-s`) — meaning
its geoGate/hangGate/bandGate/phaseTrade gates and its 16-sweep §DEQ_REPAIR fixpoint would become a
third gate layer under the new shell. Say whether Steps 1-2 subsume them or duplicate them.

## §S24_TRIAGE.3 — what must change in the spec BEFORE code (each maps to a finding above)

- **A (T2, T6)** — a written rule for physics constraints that point into a later group, with the
  measured population as its acceptance number. This is the single biggest hole.
- **B (T3)** — reconcile Step 3 with §S24.5: exempt-and-lock-the-count, or forbid cross-Tier-1
  relaxation. Both sections must say the same thing.
- **C (T7)** — level identity (datum-split names) resolved first, as a prerequisite step, not a
  §S24.4 footnote.
- **D (T4)** — validate designatedSupport's backward designations before Step 3 is allowed to trust
  them as its trigger.
- **E (T1)** — restate §S24.2 as "precedent for LOCAL support gating", not for phase gating, and
  reconcile with §4D_BAND_MONOTONIC's standing anti-serialization ruling.
- **F (T8)** — fix the DB-selection rule, and fix `witness_midair_zero.js:169` before any
  before/after fleet claim.
- **G (T9 + smaller ones)** — state E1 SS-vs-FS, the crew claim order, and what happens to
  computeSchedule's own gates.
- **H (T5)** — sequence the work: run the bounded backward-edge experiment inside the existing
  engine first. If it clears 5/7 buildings as the ablation predicts, the rewrite's scope changes.

**STOP-AND-REPORT (inline, per §S23 Part B — this is the worked example that section's acceptance
asks for):** if the backward-edge experiment (H) does NOT move `§PG_GAP_CPM` on the buildings the
ablation predicts it will, STOP and report that the ablation model is wrong — do not proceed to
build §S24.3 on the strength of a prediction that just failed, and do not re-frame the target to
whatever the experiment did happen to improve.

## §S24_TRIAGE.4 — the baseline every future fix is judged against (`node scripts/probe_phase_gate.js`, 2026-08-18)

| building (served DB) | n | RAW neg/pairs, worst | CPM neg/pairs, worst | giant SCC / nodes | backward supports |
|---|---|---|---|---|---|
| Terminal_meta | 48,428 | 11/17, −21.5d | 17/17, −76.0d | 45,182 / 48,513 | 2,821 / 48,116 |
| Hospital_meta | 63,182 | 5/10, −41.5d | 10/10, −246.6d | 62,132 / 63,222 | 6,833 / 62,643 |
| Clinic_meta | 16,071 | 7/10, −14.7d | 10/10, −85.0d | 14,087 / 16,106 | 3,051 / 15,740 |
| LTU_AHouse_meta | 122,330 | 18/20, −112.8d | 19/20, −747.2d | 110,351 / 122,398 | 5,836 / 119,620 |
| Duplex_extracted | 1,119 | 4/4, −0.4d | 3/4, −6.3d | 577 / 1,138 | 95 / 1,107 |
| HHS_Office_Federated_extracted | 6,839 | 4/4, −22.0d | 4/4, −27.4d | 5,661 / 6,856 | 956 / 6,786 |
| JKR_extracted | 8,985 | 14/17, −11.7d | 16/17, −16.4d | 8,232 / 9,058 | 566 / 8,885 |

`gate_4d.sh` green and `floating=0/7` remain necessary-not-sufficient, for the reason §S24.5 already
gives — neither can see this failure class.

## §S24_TRIAGE.5 — what this triage did NOT check (so nobody reads it as broader than it is)

Not re-derived: §S24.1's "0/56 offending columns have any physics-only path back to their own
milestone's ancestors" (plausible and consistent with the size-5 physics SCC, but not measured here),
and §S24.4's "8/8 clean" federated-pair check on the 0.15m case. Not touched: the MEP-typing/zone-
capacity lane (§S24.4 is right that it is orthogonal), Gantt-drag incremental re-run (still open,
correctly named), the Duplex/HHS TRADE regression, and the `Terminal_meta.db`/`Hospital_meta.db`
(#1427/#1428) integrity question — all four remain exactly as open as the §RESULTS addendum left
them. No code in `viewer/` was changed by this triage; the only artifact is the study-only probe.

---

# §S25 — THE LAYER CONTRACT AND ONE FORWARD PASS (2026-08-18). **SUPERSEDES §S24 — do not implement
# §S24.3.** §S24 and §S24_TRIAGE stay in this file as the record of how this was reached; they are
# history now, not instructions. This section is the whole design, start to finish, and it is meant
# to be read alone.
#
# Why superseded rather than amended: §S24 built a phase gate on a precedent that has no phase layer
# (§S24_TRIAGE T1), and defined its conflict rule in terms of GROUP ORDER — "relax the gate for an
# element the gate would force too early" — when the fact that decides the conflict is GEOMETRY.
# Patching those two would have left the same shape. The layering below is what the user named, and
# it resolves both by construction.

## §S25.0 — The statement this whole design rests on
> **⚠ ITS CENTRAL PREMISE IS HALF WRONG — corrected by measurement 2026-08-19, see §S25_REVIEW.5.**
> "Physical support is a strict order by elevation, so it cannot contain a cycle" is true ONLY of
> bearing-below. The hang, host and opening families point DOWN or sideways and make the physics
> layer genuinely cyclic. Acyclicity is manufactured by contraction, not inherited from geometry.

**A construction schedule is not a general dependency graph. It is four layers, each already an
order, composed in a fixed precedence.** Physical support is a strict order by elevation, so it
cannot contain a cycle — every physics cycle measured is a data error, and they are tiny (largest
physics-only SCC on any building in the fleet: 5-17 nodes). Phase order is a fixed list of 3+2
entries. Level order is a total order by height. Crew capacity is not an order at all — it delays.

The 45,182-node component is therefore **manufactured**, not discovered: it appears only when the
layers are flattened into one edge set and a general solver is asked to sort out the contradictions,
at which point the information about which layer each edge came from — the only thing that could
decide the conflict — has already been thrown away. Ablation proves it (§S24_TRIAGE T5): removing a
2-6% edge population dissolves the giant component outright on 5 of 7 buildings.

Stop flattening, and the entire apparatus goes with it: no Tarjan, no SCC condensation, no
contraction, no milestone nodes, no hammock edges, no membership edges, no straggler ancestry, no
16-sweep repair fixpoint, no sweep cap. What replaces them is one forward pass whose termination is
a one-line argument (§S25.5).

## §S25.1 — The four layers, and who wins

| layer | what it is | source | may it reorder? |
|---|---|---|---|
| **L1 LEVEL LADDER** | level identity + level order | element geometry + storey names | — (it is the coordinate system L3 uses) |
| **L2 PHYSICS** | "this cannot be built until that is finished" | geometry, per element | **absolute — always wins** |
| **L3 CONVENTION** | phase order within a level, level order within a phase | fixed lists | **default only — applies where L2 is silent, yields where L2 speaks** |
| **L4 CAPACITY** | crews per resource | rates table | **never reorders — delays only** |

**The precedence rule, in one sentence:** *physics is absolute, convention is a default, capacity is
a delay, and every place where physics and convention disagree is named, counted and reported — never
silently relaxed and never silently enforced.*

That last clause is the part both previous designs lacked. §CPM_STRAGGLER_MEMBERSHIP silently
exempted; §CPM_STRAGGLER_EXEMPTION_DROPPED silently enforced and let Tarjan clean up; §S24.3 Step 3
would have silently relaxed per element. All three are the same mistake — resolving a contradiction
without recording that one occurred.

## §S25.2 — L1: the level ladder

Two distinct jobs, both from measured data, neither invented:

**Identity.** A level is a storey name PLUS a vertical body. Cluster a name's elements by
**interval overlap** of `[base_z, top_z]` with tolerance `GAP` (0.5m, the shipped constant): two
elements share a level if their vertical extents touch. **Never cluster by centre-z distance** — that
reports any tall element as its own datum, which is exactly the error §S24_TRIAGE T7 had to correct
(Terminal's 236 real 30m precast piles read as a phantom second ground floor). Distinct names whose
bodies overlap collapse to ONE level: that is the correct treatment of federated ladders (Terminal's
`Aras 01` @8.15m and `02 FIRST FLOOR LEVEL` @8.00m are one physical storey exported twice).

Measured need, on the scheduler's own element set (`§PG_LEVEL_SPLIT`, 2026-08-18): **LTU_AHouse 5
levels / 5,045 elements (4.1%)** whose geometry does not overlap their own level's body at all
("VÅN 4" holds 1,867 elements at −1.4..1.4m alongside 1,525 at 10.9..16.5m); JKR 2 levels / 94;
Terminal 1 level / 41; and **zero on Hospital, Clinic, Duplex and HHS**. So this layer is mandatory
for one building and near-noop on four — build it, but do not let it grow into a project.

**Order.** Rank levels by ascending body base. Keep the shipped M1 rule for the ORDERING relation:
band = `floor(meanZ / 3m)`, dense-ranked over the bands actually present, and the level gate is
band-to-band. Levels sharing a band are parallel sub-buildings (Terminal's Kedai shop block vs the
main hall) and are never chained to each other. Identity is what changes here; the band ordering is
already measured and stays.

Print the derived ladder every run (`§S25_LADDER`), for the same reason §4D_BAND_MONOTONIC prints
its own: a wrong ladder enforces a wrong order confidently.

## §S25.3 — L2: physics
> **⚠ AMENDED by §S25_PROTO C3-C4 and §S25_REVIEW (2026-08-19):** the same-group "contradiction drop"
> below is DELETED (acyclicity comes from contracting physics cycles, not from an ordering rule), and
> "FS closes the float invariant" is FALSE as stated — it holds only for the one designated support.
> Read §S25_PROTO C3, C4 and §S25_REVIEW.5 before implementing this section.

**Relation (unchanged geometry, keep it):** `contactGraph` → `designatedSupport` (bearing-below,
embedded, carrier-above, with §GROUNDED_NEVER_HANGS) plus `hostPairs` / `openingPairs`. One
predecessor per element from support, plus host/opening predecessors.

**Semantics: FINISH-to-START.** A dependent may not START until its support FINISHES. This replaces
today's start-to-start edge (`cpm_schedule.js:157`, `addEdge(des[i], i, SS, 1, 'e1')`), and it is the
literal statement of what this lane was asked for — *the floor is finished before the beam goes up*.
Consequences, stated up front so they are not read later as regressions: `auditFloating`'s TRADE
count (a dependent starting before its support finishes) should collapse from its locked baselines
(Terminal 8,789, Hospital 5,107, LTU_AHouse 15,896 — W-MZ-8) toward zero, and the makespan will grow.
Both are the intended effect. FS is uniform across all three support classes — a column on a cured
footing, a window in a finished wall, a duct hung from a poured slab.

**Within one group**, order by `base_z` (guid tie-break). A same-group physics constraint whose
support does not sort earlier is a **contradiction in the data**: drop it, count it
(`§S25_CONTRADICTION`), never contract it. This is what retires Round-2 contraction; the population
it replaces is small and already characterised (physics-only SCCs of size ≤5 on most buildings, 1,291
of them on Terminal, 17 max on HHS).

## §S25.4 — L3: convention. Gates are numbers, never edges

- **Phase gate:** at a level, `Substructure → Superstructure → Architecture`, then every Tier-2 phase
  after all Tier-1 phases present at that level.
- **Level gate:** same phase, previous band.
- **A gate's value is the completion time of the gating group**, where completion is
  `max end over that group's members that were NOT deferred` (§S25.5), and the excluded set is
  reported per group.

No milestone nodes exist. No E3/E4/member edges exist. A gate is a `Float64` that becomes known when
a counter reaches zero, and it is read by a `max()`.

## §S25.5 — The deferral rule: the entire conflict resolution, and why it terminates
> **⛔ SUPERSEDED IN PLACE by §S25_PROTO C1-C2 (2026-08-19). The rule as written below DEADLOCKS on
> first contact with real data (975 of 1,119 elements stuck on Duplex).** Kept verbatim because this
> file keeps its retractions. Implement the effective-rank mechanism in §S25_PROTO C1-C2, never this.

Pass order is `(bandRank, phaseRank)` — a **total order**. For each physics constraint `p → e`:

- `group(p) < group(e)` → ordinary predecessor. Nothing special.
- `group(p) == group(e)` → ordinary predecessor if `p` sorts earlier by `base_z`; otherwise a
  contradiction (§S25.3), dropped and counted.
- **`group(p) > group(e)` → `e` is DEFERRED**: it keeps the constraint (physics is absolute), it
  becomes schedulable only when `p` is finalized, and **it is excluded from its own group's
  completion time**.

**Termination, in one line: a deferral always points strictly forward in a total order, so a chain of
deferrals strictly increases and cannot close.** That is the proof that replaces Tarjan. There is no
fixpoint, no iteration cap, and no cycle to break — not because cycles are broken well, but because
none can be formed.

**Deferral is LOCAL and non-transitive** — decided by an element's own direct predecessor, never by
ancestry through a condensation. That single word is the difference from §CPM_STRAGGLER_MEMBERSHIP,
whose transitive definition classified 54-100% of a phase as stragglers and made "phase complete"
meaningless. The local population is measured and small: **Terminal 5.9% (2,821 of 48,116), Hospital
10.9%, HHS 14.1%, Clinic 19.4%, Duplex 8.6%, JKR 6.4%, LTU_AHouse 4.9%**, plus backward host/opening
pairs (Terminal 726, Clinic 495, LTU_AHouse 1,493).

**Excluding a deferred element from its group's completion is not a fudge — it is the honest
statement.** A physically-late element is not doing that group's work at that time; it merely carries
the group's label. But the exclusion is only honest while it is *visible*: the deferred count per
group is a reported number with a locked baseline, and a group with a large deferred share is a
**data-quality alarm**, not something to schedule around. Clinic's 19.4% is exactly such an alarm and
should be read as one.

## §S25.6 — L4: capacity

Same allocator as today and as `computeSchedule`: per-resource slot array, claim the earliest free
slot, `MAX_CREWS_DEFAULT = 3`, pools shared project-wide. One rule made explicit because §S24.4 got
it wrong ("same mechanism, smaller input"): **crew slots are claimed in TIME order, not in group
order.** The ready set is a min-heap keyed `(precedence-feasible start, base_z, guid)`. A
group-sequential claim order would let a group that is later in the pass but earlier in time claim
after one that runs later — measurably wrong wherever a pool spans phases (`CONCRETE_GANG` appears in
both Substructure and Superstructure). Capacity delays; it never reorders.

## §S25.7 — The algorithm, whole

```
compute(elements, opts):
  ladder      = levelLadder(elements)                  # §S25.2 — identity by interval overlap, order by band
  group(e)    = (bandRank[level(e)], phaseRank(e.phase))         # the pass order: a TOTAL order
  C           = contactGraph(elements)                 # §S25.3 — unchanged geometry
  preds(e)    = designatedSupport(e,C) + hostPairs + openingPairs        # FS, every one of them

  for each constraint p->e:                            # §S25.5 — classify, never merge
      forward | sameGroup-ordered   -> predecessor
      sameGroup-unordered           -> DROP, count §S25_CONTRADICTION
      group(p) > group(e)           -> predecessor AND mark e DEFERRED

  predCount[e]     = # unfinalized predecessors
  groupRemaining[g]= # NON-deferred members of g
  gatePending[g]   = # gating groups (prev phase @ level, same phase @ prev band) not yet complete
  gateTime[g]      = max completion of those gating groups                # a number, not a node

  ready(e)  iff predCount[e]==0 and gatePending[group(e)]==0
  heap key  = ( max(gateTime[group(e)], finish of finalized preds), base_z, guid )

  while heap:
      e     = pop()
      start = max(gateTime[group(e)], every predecessor's finish, earliest free slot of e.resource)
      end   = toWall(toProductive(start) + dur(e))     # §S25.8
      commit that crew slot to `end`
      for each dependent d: if --predCount[d]==0 and gate open -> push d
      if not deferred(e) and --groupRemaining[group(e)]==0:
          complete(g) = max end over g's non-deferred members
          for each h gated by g: gateTime[h] = max(gateTime[h], complete(g))
                                 if --gatePending[h]==0 -> push h's ready members

  assert processed == elements.length                  # MUST be 0 leftover; print it, never repair it
```

`O((V+E) log V)`, one pass, no fixpoint, no sweep cap. **A non-zero leftover is a bug in this design
and must be reported as one** — not swept up by a fallback loop, which is how `computeSchedule`'s
`§SUPPORT_CYCLE` residue and `§DEQ_REPAIR`'s 16 sweeps came to exist.

Elements with no level (no storey) get no group gate: physics and crews only, counted separately.

## §S25.8 — Durations, and why this can be ONE engine

`dur = round(installSecs * scaleFactor * 1000)` productive ms, and
`end = toWall(toProductive(start) + dur)`. Verified pure by direct read (`schedule_gate.js:554-558`):
it depends on the element and the shift window and on **nothing about placement**. So the layered
pass computes its own durations and does **not** need `ScheduleGate.computeSchedule` as a duration
oracle — which is precisely what makes one engine possible instead of two-and-a-bridge. `installSecs`
keeps coming from `ScheduleAuthor._installSecs` (real class fragmentation + linear weighting), the
one existing source; nothing about classification moves.

## §S25.9 — Where it lands, and what dies

`viewer/schedule_engine.js` **already exists** as the `compute(elements, opts) → ScheduleModel` class
this file's DO-NOT-REMOVE header mandates, and today it is wired to nothing but two probes. §S25 is
its BODY: its Step 1 (`SG.computeSchedule`) and Step 2 (`CPM.run`) are replaced by §S25.7. Order of
wiring, each step independently checkable:

1. `time_machine.js:4322` `_displayTimeline` → `ScheduleEngine.compute` (the live movie + Gantt).
2. `schedule_author.js:404` → the same call (task-window authoring, today a second `computeSchedule`).
3. `time_machine.js:5179` stops calling `computeSchedule`.

Then delete, not deprecate: `cpm_schedule.js`'s `buildGraph` / `tarjanScc` / `solve` and
`computeSchedule`'s scheduling machinery — `geoGate`, `wallGate`, `hangGate`, `openingGate`,
`hostGate`, `bandGate`/`bandCommit`, `phaseTrade`, the Kahn placement loop, `§SUPPORT_CYCLE`'s
fallback and the 16-sweep `§DEQ_REPAIR` fixpoint. Their job is done by L2+L3+L4 and leaving them
resident is exactly the third gate layer §S24_TRIAGE flagged.

**Stays, untouched:** `contactGraph`, `designatedSupport`, `hostPairs`, `openingPairs`,
`collapsePhase`, `deriveBandRanks`, `toWall`/`toProductive`, `auditFloating`, `deriveZones`,
`_buildScheduleElements`, `_installSecs` — geometry, classification and audit, none of which is
scheduling. `stragglerOf` becomes `deferredOf`: safe, because the bar-window formula that once read
it is already classification-free (`time_machine.js:4422-4427`) and nothing else consumes it.

Bump `_GANTT_CACHE_VERSION` (`time_machine.js:7903`) — the schedule shape changes — and bump
`sw.js CACHE_VERSION` in the same PR as any `viewer/*.js` change, per this project's standing rule.

## §S25.10 — Acceptance: what "done" means, against today's measured baselines

1. **`§PG_GAP_CPM` = 0 negative gaps on 7/7 buildings**, or every remaining negative gap attributable
   by guid to a listed deferral, with that count locked. Today: 7/7 fail, worst −747.2d (LTU).
2. **`auditFloating` → 0**, or a locked, listed exception set. Today: Terminal 8,789 / Hospital 5,107
   / LTU 15,896 (W-MZ-8). This is the FS invariant of §S25.3 and it is the acceptance the user asked
   for in construction terms.
3. `_midairAudit` stays 0/7 (nothing appears before what it touches).
4. `§CREW_FEASIBILITY` stays 0 violations.
5. **Leftover elements = 0**, printed every run.
6. The 7 hand-computed synthetic cases still pass, plus **three new ones this design's own failure
   modes require**: a deferral chain across three groups; a same-group contradiction pair; a level
   with a genuine datum split. Hand-compute each before running it, per §VERIFICATION rule 1, and
   wire them into the existing fleet gate — not a second gate.
7. `§PG_LEVEL_SPLIT` ladder correct on 7/7, and every measurement taken on the DB half the viewer
   actually serves (fix `witness_midair_zero.js:169` first — §S24_TRIAGE T8).

## §S25.11 — Build order (each stage lands alone, each has its own stop condition)

- **S25-1 — L1 ladder only.** No scheduling change. Prove the ladder on 7/7 via `§PG_LEVEL_SPLIT`.
  *STOP-AND-REPORT if* a building's ladder disagrees with its `spatial_structure` elevations in a way
  the interval rule cannot explain — report it, do not tune the tolerance until the table looks nice.
- **S25-2 — the forward pass, physics FS + gates, no crews.** Measure `§PG_GAP` and `auditFloating`.
  *STOP-AND-REPORT if* leftover ≠ 0, or if negative gaps survive that are not explained by a listed
  deferral — the deferral model is then wrong and must be reported wrong, not widened until it fits.
- **S25-3 — L4 crews**, time-ordered claims. Re-check `§CREW_FEASIBILITY` and makespan.
  *STOP-AND-REPORT if* adding crews reintroduces a negative gap: capacity must only delay.
- **S25-4 — wiring + deletion** (§S25.9), cache versions bumped, dead gates removed.
  *STOP-AND-REPORT if* any deleted function still has a live caller outside tests.
- **S25-5 — Gantt-drag**, the question §S24 left open: an edit is a **re-run of the same pure pass
  with the dragged element pinned** (its start fixed, everything downstream recomputed). Same
  function, one extra constraint — no incremental variant, no second code path. Verify the lock gate
  (`verifyGanttIntegrity`) judges the re-run by the same rules.

Before S25-2, run the bounded experiment §S24_TRIAGE H already specified (refuse or defer the
backward physics constraints inside today's engine): it costs hours, and it either confirms the
ablation model on 5/7 buildings or proves it wrong before any of this is built. *STOP-AND-REPORT if
it does not move `§PG_GAP_CPM` where the ablation predicts* — that would mean this section's
diagnosis is wrong, and that finding outranks the plan.

## §S25.12 — What this does NOT solve, said plainly

- **The LTU_AHouse 5,045 mislabelled elements are a DATA defect.** The ladder makes them visible and
  schedules them where their geometry actually is; it does not repair the source model. An element at
  z≈0 labelled level 4 is wrong in the IFC, and that belongs to extraction, not scheduling.
- **MEP typing / zone capacity** (untyped `IfcBuildingElementProxy` → Architecture) is orthogonal and
  stays open, exactly as §S24.4 had it.
- **The Duplex/HHS TRADE regression** in the §RESULTS addendum is *expected* to be subsumed by
  §S25.3's FS semantics, since it is the same invariant. That is a prediction, not a claim — if FS
  lands and those two buildings still show it, STOP and report, do not fold it into this section.
- **`Terminal_meta.db` / `Hospital_meta.db` integrity** (#1427/#1428 elevation+parentage patch) is
  still unverified and unrelated to this design.
- **Nothing here is measured yet.** §S25.0-§S25.6 are grounded in numbers already taken (§S24_TRIAGE.4
  is the baseline table); §S25.7's pass has not been run against a building even once. The first
  session to build it should expect to find something in here that the data does not support — and
  should report that, not route around it.

---

# §S25_PROTO — §S25 BUILT AND MEASURED ON ALL 7 BUILDINGS (2026-08-19). Still a prototype
# (`bim-compiler/scripts/proto_s25_forward_pass.js`, study-only) — `viewer/` is unchanged. This is
# §S25.11's stages S25-1..S25-3 executed as one measurement instead of argued about.
#
# **Headline: the layer contract works mechanically and is provably clean on its own terms —
# leftover 0, gate violations 0, midair 0, crew violations 0, on 7/7 buildings. And it still cannot
# make the phase statement true as LABELLED, because 17-64% of elements carry a phase label their own
# physics contradicts.** The remaining problem is not the scheduler. It is classification.

## §S25_PROTO.1 — Four corrections the prototype forced on §S25, each found by running it

**C1 — §S25.5 as written DEADLOCKS. Excluding a deferred element from its group's completion is not
enough.** First run, Duplex: **975 of 1,119 elements stuck**. The cycle is: a NON-deferred element X
waits on a deferred element Y in its own group → the group never completes → the later group Y waits
for never opens → Y never runs → X never runs. Lateness has to MOVE the element, not excuse it.
**Replacement rule:** `effectiveRank(e) = max(passRank(group(e)), max over predecessors p of
effectiveRank(p))` — one longest-path propagation. Every constraint is then forward-or-same by
construction, group completion means "all my work is done" with no exclusion accounting, and no
deadlock can form. Re-homed elements are counted and reported exactly as deferred ones were.

**C2 — that propagation must run over the CONDENSATION, not over elements.** Propagating per element
and then giving a contracted component its members' max rank afterwards re-broke it (**674 of 1,119
stuck**): raising a member's rank after the fact leaves its successors ranked BELOW their own
predecessor. Compute component ranks first, propagate over the condensation, then assign.

**C3 — physics cycles must be CONTRACTED, not dropped.** Dropping the edges inside a mutual-support
component cost **53 midair violations on Duplex** (the dependent had nothing holding it). Contracting
them — one shared start, the judge's own equality contract — restores **midair 0**. This is a Tarjan
on the PHYSICS LAYER ONLY (largest component in the fleet: 4-17 nodes), where a cycle means the model
is wrong; it is not the merged-graph Tarjan §S25.0 rejects. Consequence: §S25.3's invented same-group
"contradiction drop" is unnecessary — acyclicity comes from the contraction — and is deleted.
Measured contradictions dropped fleet-wide after this change: **0**.

**C4 — §S25.3's "FS closes the float invariant" is true only for the DESIGNATED support, and the
obvious fix is measurably worse.** `auditFloating` tests every qualifying support; the designated
edge covers one. Constraining against all of them was tried (`ALL_SUPPORTS=1`, kept in the probe so
this stays checkable): contact relations in real data are mutual, so the physics cycles explode —
Duplex largest component **5 → 672**, contracted edges **76 → 4,821**, re-homed **31.6% → 93.3%**,
makespan **8.1d → 2.7d**. A schedule where almost everything shares a start is not a schedule.
The RAW shell reaches float=0 by a different mechanism entirely (a grid gate that only sees supports
ALREADY PLACED, plus its 16-sweep repair loop). So float stays a **counted residual**, not zero, and
§S25.10 acceptance #2 must be rewritten to say so.

## §S25_PROTO.2 — Results, 7/7 buildings, one run, same shift (24h) for all three engines

RAW = `computeSchedule` · CPM = `CpmSchedule.run` (live today) · S25 = this pass.
`engineGap` = the acceptance question measured on the ENGINE's own level identity, effective
membership — what §S25 actually enforces. `asLabelled` = the same test with the re-homed tail counted
back into its labelled phase — what a planner reading phase names sees.

| building | n | leftover | engineGap | asLabelled (worst) | midair CPM→S25 | float CPM→S25 | crew | re-homed | pass ms |
|---|---|---|---|---|---|---|---|---|---|
| Terminal_meta | 48,428 | **0** | **0/14** | 14/15 (−30.98d) | 0→0 | 4,756→**3,523** | 0 | 25.9% | 406 |
| Hospital_meta | 63,182 | **0** | **0/10** | 8/10 (−216.81d) | 0→0 | 7,753→**4,095** | 0 | 16.9% | 784 |
| Clinic_meta | 16,071 | **0** | **0/7** | 10/10 (−80.34d) | 0→0 | 1,102→3,086 | 0 | 39.9% | 121 |
| LTU_AHouse_meta | 122,330 | **0** | **0/11** | 13/14 (−768.47d) | 0→0 | 12,712→19,327 | 0 | 34.7% | 1,515 |
| Duplex_extracted | 1,119 | **0** | **0/2** | 4/4 (−5.95d) | 0→0 | 247→280 | 0 | 31.6% | 5 |
| HHS_Office_Federated | 6,839 | **0** | **0/4** | 4/4 (−22.03d) | 0→0 | 1,531→**1,436** | 0 | 33.4% | 54 |
| JKR_extracted | 8,985 | **0** | **0/4** | 9/9 (−19.21d) | 0→0 | 3,183→**3,078** | 0 | 63.6% | 84 |

Read it honestly, both halves:

- **What is now proven.** The pass terminates with **zero leftover on 7/7** — the total-order
  termination argument holds on real data, at 122k elements, in 1.5s. The gate it enforces is
  enforced **exactly: 0 violations, worst 0.00d, on every building**. `midair` stays 0 (parity with
  CPM). Crew feasibility stays 0. No Tarjan over a merged graph, no fixpoint, no sweep cap, no
  straggler ancestry — the machinery §S25.0 said was unnecessary is in fact unnecessary.
- **What is not fixed, and is not a scheduler problem.** `asLabelled` is still 8/10 to 14/15
  negative. That is the direct arithmetic consequence of re-homing 17-64% of elements: when a
  "Substructure" pipe is physically forced to wait for a level-3 slab, the Substructure phase — AS
  LABELLED — cannot finish before Superstructure starts, under any scheduler. **No algorithm can make
  that statement true. Only re-classifying the element can.**
- **float is a mixed result, and C4 explains it:** better on Terminal (−26%), Hospital (−47%), HHS,
  JKR; worse on Clinic, LTU, Duplex. It tracks how much of each building's support structure the one
  designated edge happens to cover.
- **The amplification is the real lever.** JKR: **609 backward constraints → 5,715 re-homed elements
  (9.4×)**. Terminal: 4,263 → 12,547. A handful of wrong support designations relabels a large part
  of a building. §S24_TRIAGE T4/D (validate `designatedSupport`'s backward cases) is therefore not a
  follow-up — it is the highest-leverage item in this lane, and it now has a number attached.

## §S25_PROTO.3 — Two new gaps the prototype exposed in §S25.2 (level ladder)

- **The acceptance test must use the ENGINE's level identity, not the storey name.** Measured by
  name, Clinic and LTU "failed" — but the engine had split Clinic's `Roof - Main` into **three**
  physical levels (median 4.5m, 7.3m, 11.3m) and merged federated names elsewhere
  (`Level 2=Roof - Main`, `VÅN 3=VÅN 4=VÅNING 4=Ref.`). Comparing a re-derived ladder against a
  by-name grouping measures the mismatch, not the schedule. §S25.10 #1 needs this stated.
- **Tiny levels get a full band rank and gate real work.** LTU's ladder contains levels with **n=2 at
  −45.8m** and n=2 at −13.9m, each occupying its own band rank ahead of 44,383 elements at 5.3m.
  §S25.2 needs a minimum-population / merge-into-nearest rule, derived from data, not a guessed
  threshold. Not yet designed.

## §S25_PROTO.4 — Where this leaves the lane (the honest next move, not a plan to write another plan)

1. **Fix the support designation before anything else** (§S24_TRIAGE T4/D). The 9.4× amplification
   makes this the only change with fleet-scale leverage. Specifically: the same-level
   `Architecture→Superstructure` designations (a wall elected as a column's support — Hospital 513,
   HHS 427, Clinic 386) and `MEP→Superstructure` (Terminal 644). Each is a candidate misclassification
   that today silently re-homes everything resting on it.
2. **Then re-run this prototype.** `asLabelled` is the number that should move. If it does not move
   when the bad designations are fixed, **STOP and report** — that would mean the re-homing is
   dominated by genuine physics, and the phase labels themselves (not the supports) are what disagree
   with the building.
3. **Only then wire §S25 into `schedule_engine.js`** (§S25.11 S25-4). Wiring an engine whose
   `asLabelled` output is still 8/10 negative would ship a correct scheduler that still looks wrong
   on a planner's screen — the exact trap this whole file exists to avoid.

**STOP-AND-REPORT, inline:** if fixing the designations moves `asLabelled` but breaks `engineGap`
(currently 0/7), the layer contract is not as separable as §S25.1 claims — report that, do not
re-tune the ladder to make both numbers look good at once.

---

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
