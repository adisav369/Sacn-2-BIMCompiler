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
