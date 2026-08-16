# ⚠ DO NOT REMOVE — this is a STEP-BACK ARCHITECTURE STUDY, not a tactical bug chase. Read the log
# after every run, spec-first, no invented dependency edges or rates — same Prime Directive as
# `4D_SCHEDULE_PERFECTION.md`. This file's job is different from that one: that file is a
# chronological tactical log ("chase the next symptom to zero"); this file asks WHY the symptoms
# keep recurring across two months of sessions and proposes a STRUCTURAL fix, not another patch.
# Read this file's `§WHY_ELUSIVE` and `§THE_PATTERN` sections before writing a single line of code.

---

# Purpose

**User's own framing, verbatim (2026-08-16), after a session that shipped a real fix (fleet floating
265→133, bim-ootb PR #1395) and rejected another (storey-order per-element clamp, +81% regression):**
*"Can u update the prompt to truly take a step back to study why all this is so elusive, what
structural design pattern to use, to resolve all the 4D schedule hell? Then we give to a Fable
session to solve it once and for all."* Followed immediately by a concrete, damning example: *"at
present even the simple ground slabs are not done before the beams and walls!"*

That example is real and numerically confirmed, not anecdotal — see §EVIDENCE below. This document is
for whichever session (Fable or otherwise) picks up the redesign: it diagnoses the structural root
cause across ~15 sessions of accumulated patches, names the pattern that resolves it, and lays out a
staged plan that respects this project's fidelity bar (7 buildings, locked witness baselines,
EXTRACT/COMPUTE never invent) instead of a big-bang rewrite that breaks things silently.

---

# §WHY_ELUSIVE — the diagnosis

## The symptom pattern, stated precisely

Every session in `4D_SCHEDULE_PERFECTION.md`'s history (read the file's own EXP1-8 / §TIER2_AFTER_TIER1
/ §HOSTED_BEFORE_HOST / §DOOR_WINDOW_HOST_WALL / §CROSSTASK_JUDGE_PARITY / §STOREY_ORDER_REPORT
sections for the raw evidence) follows the SAME shape: a real construction-plausibility violation is
observed (an element appears before its real support, a storey builds out of order, a discipline
overlaps another it shouldn't) → a NEW, narrowly-scoped repair pass is written to push the offending
elements later → it is measured to fix the reported case → it is later found to have moved OTHER
elements into a NEW violation somewhere else, because it operates on the OUTPUT of a previous pass
without a model of what that pass's own consumers need. Repeat, ~monthly, for 6+ named passes now.

**This session alone reproduced the pattern twice, cleanly, as a controlled experiment:**
- The storey-order fix (`§TIER1_PER_ELEMENT_CLAMP`, this session) was PROVABLY safer *by construction*
  than what it replaced (strictly less element movement, a precedented pattern already shipped once
  elsewhere in this exact file). Measured fleet-wide anyway: floating regressed +81% on 3 of 4
  buildings. A locally-provable improvement produced a globally worse outcome, because the change
  touched SHARED intermediate state (`t1EndZ`, the barrier a *different*, downstream pass depends on)
  that its own local proof never accounted for.
- The window-rounding fix (`§CJP_DAY_ROUNDING_TOL`, this session) worked cleanly, zero regressions,
  fleet floating -49.8%. The difference: it touched a value used ONLY inside its own function's own
  decision, never read by anything else downstream. It is safe for the same reason the storey fix
  wasn't — it doesn't leak into shared state.

That contrast is the whole diagnosis in miniature: **fixes that stay local to one pass's own boundary
condition land clean; fixes that touch anything another pass reads or writes keep breaking, no matter
how carefully the individual change is reasoned about.** With 6+ passes now sharing one mutable
`items[i].s / items[i].e` array, almost nothing IS local anymore.

## The pipeline as it exists today (traced this session, verified against real source)

One element's displayed time passes through, in order:
1. `computeSchedule` (schedule_gate.js) — RAW greedy crew-capacity simulation. Bottom-up by base_z for
   structure (PASS A), by trade for everything else (PASS B). Global shared crew pools, not
   storey-scoped.
2. `deriveZones` / `materializeZones` — groups elements into (phase, storey) zones, authors Gantt task
   WINDOWS from the zone span (a SEPARATE representation from the per-element times, historically
   diverging from them — `§ZONE_DISPLAY_AUTHORING`, 2026-08-15, was the biggest attempt yet to
   reconcile this specific divergence).
3. `_tier1Serialize` — enforces Substructure→Superstructure→Architecture order, but only as a UNIFORM
   PER-ZONE SHIFT sized off the group's earliest/latest element, not a per-element precedence edge.
4. `_tierAuditRegate` — a SEPARATE, whole-building DAG re-gate pass, iterated up to 6 rounds with
   step 3, using bearing/hang physics distinct from every other pass's own copy of that same physics.
   Documented as **78–90% of Terminal's entire 4D-generation wall time** (§TIER_REGATE_WORKLIST) —
   because it is a repeated full-array rescan to a fixpoint, not a single pass over a precomputed graph.
5. `§TIER2_AFTER_TIER1` — clamps every Tier-2 (MEP/Finishes) element in a zone to that zone's Tier-1
   completion, computed from whatever step 3+4 left behind.
6. `§HOSTED_BEFORE_HOST` / `§DOOR_WINDOW_HOST_WALL_DISPLAY` — two MORE passes, each patching a specific
   cross-zone relationship (a hosted fixture vs its host wall) that steps 3-5 broke, because those steps
   are zone-scoped and a fixture + its host wall are routinely in different zones.
7. `_midairRepair` — a general "no element before its first contact" fixpoint, window-bound, using
   ANOTHER copy of the support-contact predicate (via `_contactGraph`).
8. `_capWindowRescale` / `applyGapClampRescale` — a per-task LINEAR RESCALE fitting element times into
   the Gantt window authored in step 2 — a reconciliation pass that exists ONLY because steps 1-7 and
   step 2 are not the same schedule.
9. `_ogSupportSweep` — YET ANOTHER support-physics pass, with its OWN, narrower carrier-candidate
   predicate than `_contactGraph`'s (see §EVIDENCE — this exact gap needed three separate patches,
   §OG_HANG_BAND / §OG_HANG_UNBOUND / §XRAY_WALL_SCOPE, each just widening it closer to what the judge
   already accepted).
10. `_cjpJudgeParity` — closes the remaining gap between the judge (`_contactGraph`) and what step 9
    could reach, window-bound (this session added the 1-day rounding tolerance here).
11. `_contactGraph` / `floatingCensus` — THE JUDGE. Computed independently of every step above. Nothing
    that ran in steps 1-10 is even the same code path as this — it is a completely separate re-derivation
    of "what supports what."

**That is eleven passes, five of them independently re-deriving some version of "what physically
touches/supports what," none of them sharing a single graph.** No wonder a fix to any one of them keeps
resurfacing a symptom in a different one.

## §EVIDENCE — the same defect, three independent times, all found by this project itself

1. **Two schedules, not one.** RAW (`computeSchedule`) vs DISPLAY (`_twoTierRemap`+`_midairRepair`) were
   two genuinely different timelines for most of this project's life; `§ZONE_DISPLAY_AUTHORING`
   (2026-08-15) unified WHICH one authors the Gantt window, but the reconciliation is still a rescale
   pass (step 8 above), not structural unification — there are still two representations (continuous
   element ms times vs day-rounded window boundaries) requiring this session's `§CJP_DAY_ROUNDING_TOL`
   patch to paper over their mismatch.
2. **Five independent copies of "what supports what."** `_contactGraph` (the judge), `hangGate` (the
   generative engine), `_buildXraySupportCache`, `auditFloating()` (schedule_gate.js), and
   `_ogSupportSweep`'s own inline predicate all separately encode "is S a real physical carrier of T,"
   with different tolerances, discovered out of sync with each other over and over: §OG_HANG_BAND
   (2026-08-15) widened `_ogSupportSweep`'s search radius from 0.5m to 9.5m to match the judge; five
   commits later `§OG_HANG_UNBOUND` widened it AGAIN because 9.5m was ALSO wrong, to match `hangGate`'s
   own unbounded definition; `§XRAY_WALL_SCOPE` fixed a THIRD copy the same session. Same relationship,
   patched into agreement piecemeal, four separate times, because it was never one function.
3. **Storey/build order was never an edge.** `§4D_BAND_MONOTONIC` in `computeSchedule` only ever RANKS
   storeys (median base_z), it does not GATE anything on that rank — and `_tier1Serialize`'s uniform
   shift enforces discipline order per zone, never storey order across zones. This session's
   `§STOREY_ORDER_REPORT` probe proved the consequence numerically: RAW is often clean (Clinic 0/6
   violations) but the DISPLAY remap introduces violations on 4/4 buildings measured — because nothing
   in the pipeline ever asserts "Level 2 cannot start before Level 1," it only ever asserts phase order
   WITHIN whatever zone the element already landed in.

**The user's live report — "ground slabs not done before beams and walls" — is the same defect, most
basic case.** Confirmed numerically this session, not eyeballed: Hospital's shipped, post-all-fixes
state (`§EXP8_FINAL_BYCLASS`, `probe_captured_floating.js`) still shows **32 columns, 11 beams, 4 pipe
fittings, and 2 footings among its 51 still-floating elements** — i.e. beams and footings routinely
displayed appearing before whatever they physically rest on, even after eleven repair passes. If the
graph-of-real-relationships existed once and were solved once, a beam appearing before its own footing
would be **structurally impossible**, not something to chase to a smaller residual, forever.

## Why this happened (not a criticism, a diagnosis of the growth pattern)

Every one of the eleven passes above was added to fix ONE observed, real, correctly-diagnosed symptom,
under the project's own good discipline (measured, witnessed, never invented). None of them is wrong on
its own terms. The defect is that no session ever had the budget to ask "should this be a new pass, or
a new edge in one graph" — so the answer was always "a new pass," and the passes accumulated faster
than any one of them could be fully reasoned about against all the others. This is the textbook failure
mode of a compiler with many independent peephole-optimization passes and no shared canonical
Intermediate Representation (IR): each pass is locally correct, the composition is not, and every new
symptom looks like it needs its own new pass — because, in this architecture, it does.

---

# §THE_PATTERN — what actually resolves this

## Name it: this is Critical Path Method (CPM) scheduling, and the codebase doesn't do it

**CPM/PERT (textbook operations research, exactly what commercial 4D tools like Primavera P6 and MS
Project implement) is: build ONE directed acyclic graph where a node is an activity and an edge means
"B cannot start until A reaches some state," then compute every node's earliest-start time in a SINGLE
forward pass over the graph in topological order.** This is not a new idea to invent — it is the
80-year-old, textbook-correct answer to exactly the problem this file has been fighting by hand since
2026-06. Its correctness proof is elementary (induction over topological order: a node's start is
`max(predecessor.finish)` over all real predecessors, so no node can ever be scheduled before a real
predecessor by construction) and its cost is linear in `V + E` — ONE pass, not an iterated fixpoint.

## The concrete redesign

1. **One edge-computation phase, run once, before any scheduling.** Every "is B blocked on A" fact this
   codebase currently re-derives 3-5 times (structural bearing/hang/embedded contact, host/opening
   pairs, discipline order, storey order) becomes an EDGE in one graph, computed from the SAME real
   geometry query every consumer shares — not five independently-tolerance-tuned copies. Concretely:
   - Physical contact/bearing/hang/embedded edges: `_contactGraph`'s own spatial-grid algorithm (already
     O(N), not O(N²) — keep it) becomes THE one definition. `hangGate`, `_buildXraySupportCache`,
     `auditFloating()`, `_ogSupportSweep`'s inline predicate are deleted, not re-synced a fifth time.
   - Host/opening edges: `ScheduleGate.hostPairs`/`openingPairs` already exist as a single shared
     definition (this pattern is ALREADY right for this one relationship — extend it, don't re-invent).
   - Discipline-order edges: instead of `_tier1Serialize`'s uniform per-zone shift, a real edge from
     "every element of phase *k* in zone Z" to "every element of phase *k+1* in zone Z" — or, to avoid
     an edge explosion, a single summary edge per zone from phase *k*'s LATEST element to phase *k+1*'s
     EARLIEST element (this is exactly what CPM calls a "finish-to-start" activity link at the summary/
     hammock level, standard technique, not a new invention).
   - Storey-order edges: THE MISSING PIECE. A summary edge (same hammock pattern) from each storey's
     relevant completion to the storey above's start, per discipline — this is the edge that has NEVER
     EXISTED in this codebase, which is why every attempt to enforce storey order by warping an
     unrelated mechanism (the Tier1 shift) either doesn't reach far enough (uniform shift) or breaks
     something else (per-element clamp, this session's rejected attempt). Once it is a real edge, the
     forward pass enforces it for free, same as every other edge, no special-case mechanism needed.
2. **One scheduling phase: topological sort + forward longest-path.** Replaces `_tier1Serialize` +
   `_tierAuditRegate` + `§TIER2_AFTER_TIER1` + `§HOSTED_BEFORE_HOST` + `§DOOR_WINDOW_HOST_WALL_DISPLAY`
   + `_midairRepair` + `_ogSupportSweep` + `_cjpJudgeParity` — eight passes — with ONE. Every edge from
   phase 1 is honoured simultaneously, by construction, in one linear-time pass. No iteration, no
   "did anything move this sweep," no 16-sweep cap, no window-bound special case (see point 4).
3. **Windows are DERIVED, never separately authored.** A Gantt task's window = `[min(start), max(end)]`
   over the (already-correct) per-element times of that (phase, storey) group, computed AFTER
   scheduling. This deletes `materializeZones`' independent window authoring, `_capWindowRescale` /
   `applyGapClampRescale`, and this session's own `§CJP_DAY_ROUNDING_TOL` patch — there is no window to
   round-trip against, because the window is a VIEW of the one true schedule, not a second schedule.
4. **The judge becomes a pure verifier, not a repair target.** `floatingCensus`/`_contactGraph`
   (renamed/kept as the edge-builder from point 1) still runs — but its job changes from "measure how
   much is still broken after eleven repair attempts" to "assert the graph-building step didn't miss a
   real edge." A nonzero result after the CPM pass is a GRAPH CONSTRUCTION BUG (a real contact the edge
   phase failed to detect — e.g. a spatial-grid cell-size or tolerance issue), not a scheduling failure
   to chase with a twelfth repair pass. This is a genuinely different, much smaller class of bug to
   debug, and it's debuggable by inspecting ONE graph, not by tracing state through eleven mutating
   passes.

## §WHAT_STAYS — do not throw out crew-capacity scheduling

**Combining precedence (CPM) with scarce shared-resource contention (crew capacity) is a genuinely hard
problem** — Resource-Constrained Project Scheduling (RCPSP) is NP-hard in general; there is no simple
"just add crew-capacity as more edges" trick that stays linear-time. `computeSchedule`'s existing greedy
crew-slot allocation (claim earliest-available slot of N, bottom-up by base_z) is a reasonable, already-
working heuristic for that SEPARATE problem — it is not where the eleven-pass mess lives, and this
redesign should not touch it. The right integration: run crew-leveling FIRST (as today) to get each
element's resource-constrained earliest-possible time, feed that into the CPM graph as one more kind of
lower-bound edge (a synthetic "cannot start before `<crew-available-time>`" edge from a virtual source
node), and let the ONE forward pass reconcile resource bounds and precedence edges together in the same
linear pass. Do not attempt to make crew-leveling itself precedence-aware in the same breath — that's a
second research project, not this one.

---

# §EXECUTION_PLAN — staged, measured, never a big-bang replace

This touches the highest-blast-radius code in the project — every one of the 7 shipped buildings'
locked witness baselines (`witness_tier_serial_display.js` W-TS-1b's exact `dagWins` counts,
`witness_midair_zero.js`'s `FLOAT_AFTER_BASELINE`/`ORPHAN_BASELINE`, `witness_crosstask_judge_parity.js`)
depend on the CURRENT pipeline's exact behavior. **A same-session full replacement is not the ask here
and would violate this project's own measure-before-build discipline** (see CLAUDE.md Spec-First,
Anti-Drift, and this very file's own EXP5a/EXP5b/§TIER1_PER_ELEMENT_CLAMP rejected-experiment history —
every one of those was rejected specifically because it was measured broadly BEFORE being trusted).

1. **Build the edge-computation phase and the CPM forward-pass as a NEW, side-by-side module** —
   does not replace anything yet. Feed it the same 7 buildings' extracted DBs.
2. **Verify the new pass's own output against ground truth, not against the old pipeline's baselines**
   (those baselines encode the OLD pipeline's bugs — do not treat them as correct). Ground truth here
   means: zero elements start before any of their real contact-graph predecessors (this is now provable
   by construction, but verify the graph-construction step itself found every real edge — cross-check
   against `_contactGraph`'s existing spatial-grid population counts per building as a sanity bound).
3. **Compare wall-clock cost.** The new pass should be FASTER than today's `_tierAuditRegate` alone
   (documented 78-90% of Terminal's whole 4D-gen time) since it replaces an iterated fixpoint with one
   linear pass — measure this explicitly, it's a real, checkable prediction of this redesign, not a
   hand-wave.
4. **Only after 1-3 are clean on all 7 buildings**, propose (do not silently execute) retiring the
   eleven old passes one at a time, oldest-and-narrowest-scope first, re-running the FULL existing
   witness suite after each retirement to catch any behavior the new pass doesn't yet cover. Update
   locked witness baselines only with an explicit, measured, written justification — same discipline
   `§CJP_DAY_ROUNDING_TOL`'s witness update used this session.
5. **The storey-order and "ground slab before beams" symptoms are the acceptance bar**, not a vague
   "feels better": re-run this session's `§STOREY_ORDER_REPORT` probe (still live in
   `probe_captured_floating.js`) and the `§EXP8_FINAL_BYCLASS` per-class floating breakdown before and
   after — 0 violations, 0 structural elements (footing/column/beam/slab) in the floating byClass
   breakdown, is the actual, numeric, non-negotiable target this redesign exists to hit.

---

# §GUARDRAILS — standing project rules this MUST still follow (condensed; read the source for full text)

- **EXTRACT/COMPUTE, NEVER INVENT (`CLAUDE.md` Prime Rule).** Every edge in the new graph traces to a
  real geometry query or a real, named business rule (discipline order, host/opening pairs) — never an
  invented tolerance. If a genuinely new tolerance is unavoidable (as `§CJP_DAY_ROUNDING_TOL`'s 1-day
  bound was this session), it must be DERIVED from an existing quantum already in the data (as that one
  was, from the window's own day-rounding), named, and measured — not guessed.
- **Spec-First.** Write the spec section for the edge-computation phase and the CPM pass BEFORE code,
  same as every other change in this file's history.
- **Measure before trusting (Anti-Drift, this file's own EXP5a/EXP5b/§TIER1_PER_ELEMENT_CLAMP
  precedent).** A locally-provable improvement is not evidence of a globally-safe one — every stage of
  §EXECUTION_PLAN above ends in a full-fleet measurement, not a single-building spot check.
- **§ Log Mandate.** Every run's `§`-tagged log lines are the evidence, read them, never trust exit code
  or a visual/screenshot (`CLAUDE.md` FUNDAMENTAL LAW — this applies to schedule correctness exactly as
  much as it applies to camera paths).
- **Push freely once measured** (`CLAUDE.md` §PUSH PAUSE — LIFTED), but this specific redesign's blast
  radius means each stage of §EXECUTION_PLAN should land as its own reviewable PR, not one giant commit.

---

# §OPEN_QUESTIONS — for the session that picks this up, not resolved here

1. **Edge granularity at scale.** LTU_AHouse has 122,330 elements. Per-element discipline/storey summary
   edges (hammock links, point 1 above) keep the graph small; per-element PHYSICAL contact edges already
   exist today via `_contactGraph`'s spatial grid and are the right granularity for THOSE — but the exact
   node/edge count budget for the combined graph across all 7 buildings hasn't been measured. Measure
   before assuming it's fine.
2. **Where crew-leveling's lower-bound edges get injected** (§WHAT_STAYS) — as a per-element synthetic
   edge, or a per-zone one — affects both graph size and whether crew-contention effects stay visible
   per-element or only at the aggregate zone level the Gantt shows. Needs a decision, not a default.
3. **What happens to the eleven old passes' NAMED, hard-won special-case knowledge** (§OG_HANG_BAND's
   9.5m measured carrier-distance band; §HOSTED_BEFORE_HOST's specific IfcCovering support-pool fix;
   §XRAY_WALL_SCOPE's promoted-roof-slab wall restriction). These are real, measured facts about this
   project's actual buildings' geometry, not implementation accidents — the new single edge-builder
   needs to absorb every one of them as a parameter/rule, not silently drop them and regress a fixed bug.
   Audit each named `§`-tag in `time_machine.js`'s support/contact code before deleting the function it
   lives in.
