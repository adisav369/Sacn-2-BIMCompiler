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
