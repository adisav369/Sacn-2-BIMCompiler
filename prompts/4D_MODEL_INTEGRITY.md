# ⁇ THE QUESTION THIS FILE OPENS WITH

## **What is wrong with Claude Code in modelling the solution?**

Asked by the user, 2026-08-26, after weeks of no progress. Answer it before you write code.
The evidence from one full session, all self-inflicted, all measured:

| what it did | what it cost |
|---|---|
| **Invented instead of extracted** when it hit a gap in the model | 5 retractions in one session; a structural-class regex that would have made 438 curtain-wall glazing panels load-bearing |
| **Re-derived a relation the codebase already owned** — four separate times | quantifier wrong (∀ for ∃): 1961 instead of 95 · missing upper bound: a 6.4m riser "bore" the whole building · dropped 2 of 3 clauses: every ceiling-hung pipe read as floating |
| **Reasoned about adjacency with tolerance constants** when the real question was containment, with exact geometry available | "63 uncountable elements" was really **10** · 209 floating was really **18** |
| **Wrote passes that could not tell it they had failed** | the layer pass returned null on a shadowed `global`, changed nothing, emitted IDENTICAL numbers, and nothing flagged it |
| **Trusted a green witness over the construct** | the whole template path shipped, was witnessed, and was NEVER CALLED — green and dead at the same time |
| **Read a stale checkout as canon** | concluded `4D_template.json` was lost when it was 14 commits away |
| **Patched symptoms in sequence** instead of asking whether the construct could express the answer | four rounds of review to reach a one-line change that was then measured wrong anyway |

**The single sentence:** it optimises the artefact in front of it and does not check whether the
*model* can represent the answer — so every fix moves the defect sideways and the loop never closes.

**The user's own diagnosis, which is the correct one:**
> *"WITNESS is moot if underlying design is poor."*

---

# 4D MODEL INTEGRITY — read this BEFORE any 4D scheduling work (opened 2026-08-26)

## ⚠ DO NOT REMOVE — why this file exists
**A witness cannot rescue a poor model.** This lane spent a full session, four review rounds and
five retractions patching a scheduler whose *design* could not express the right answer. Every fix
moved the defect sideways. The instruments were fine; the construct was wrong.

> **USER RULING 2026-08-26:** *"WITNESS is moot if underlying design is poor."* Adopted as the
> first rule of this file. A green suite over a bad construct is the failure mode, not the guard.

So: **design integrity is checked FIRST, by reasoning and by geometry — not by adding a witness.**
If a new session is asked to "fix floating" or "fix stacking", the answer is almost certainly in
this file's §A–§E, not in a new gate.

---

## §A THE MODEL — one type, two rules
Reference implementation: **`poc4d/Poc4D.java`** (oracle) and **`poc4d/poc4d.js`** (port).
`poc4d/parity.sh` gates them byte-identical. Run `java -cp . Poc4D coherent` — must be 0/0.

```
Node { children[], work }
STRUCTURE   an element attaches at the deepest node that fully contains it
TIME        siblings run in order;  a parent spans its children
```

Tree shape — **LEVEL-major, phases inside**, which is what `4D_template.json` already declares via
`within_level` (the chain) and `across_levels` (the ladder):

```
Building → Level → Phase → Layer → Element
```

**`within_level` and `across_levels` are the same rule at two depths.** Levels are siblings under
Building; phases are siblings under a Level. They were never two mechanisms.

### What falls out, by construction — do not re-derive any of these
- **No election.** There is no place in the model to choose *which* of N contacts supports an
  element, so `supportPool` / the direction guard / `designatedSupport`'s exemptions / `des = -1`
  are all **inexpressible**, not fixed. Every 2026-08-26 defect was a defect of choosing.
- **No cycles.** A tree has none. No straggler handling, no SCC contraction, no cycle policy.
- **No edges emitted.** Sibling order IS the order, so `task_sequences` is DERIVED. This kills the
  tautology `4D_template.json` records against itself: *"25 of 25 derived zone edges have a lag
  exactly equal to the observed gap… 100% restatement, 0% logic."*
- **No stacking.** Siblings run in order, so the spread is automatic. Lanes are a **capacity**
  (`LABOR_RATES[trade].max_crews`), never a headcount. MEASURED: simultaneous starts track the cap
  exactly (cap 1→1, 2→2, 3→3, 6→4 where the layer only holds 4).
- **Both addressing holes close with one rule.** An element with no level, and an element spanning
  levels, both attach one node up. Composite's non-uniform depth is the Null Object — no call site
  tests for a missing level.

### ⛔ The two things that broke it when I got them wrong
1. **PHASE-over-LEVEL** — 17 bearing violations on a 20-element sandbox: all-Superstructure-before-
   all-Architecture puts the L2 slab before the L1 walls it rests on. Inverting to level-major: 17→10.
2. **A cell treated as a Leaf** — a Leaf is *a node with no internal order*. Treating
   `(phase × level)` as a leaf reproduced both hells at once. Recurse into ordered layers.

---

## §B SEPARATION OF CONCERNS — every 2026-08-26 defect was a boundary violation
Information flows DOWN. **No layer writes to the layer above it.** That single rule is the whole
diagnosis:

| layer | owns | must never |
|---|---|---|
| ADDRESS | what a level is; what a phase is | be inferred silently (see §C) |
| CLASSIFY | element → (phase, trade). A **lookup** | compute anything |
| DECLARE | the programme: `4D_template.json` | contain geometry |
| PRICE | `duration_rule`: work ÷ crews, per trade | be the span of a solve |
| SOLVE | place tasks honouring declared order + capacity | discover order |
| PROJECT | one visitor per consumer, **write-only** | be authoritative, or read another's output |
| AUDIT | geometry compares result vs physics; **reports** | reschedule, or author order |

**Every measured defect, as a backflow:**

| defect | violation |
|---|---|
| `_tmRescaleToTaskWindow`, 783 floating on HHS | PROJECT rewriting SOLVE's times |
| `deriveZones` — a phase bar is an envelope of its elements | PROJECT defining DECLARE |
| `grounded` altitude-blind; `des = -1`; the pool override | AUDIT authoring DECLARE |

The code says it itself, above `instantiateTemplate`: *"a phase bar was an ENVELOPE over what the
elements did, and **an envelope cannot constrain what drew it**."*

**Editor integrity follows from this and is MEASURED intact** (2026-08-26, template wired):
`witness_gantt_edit_coherence` · `_lock` · `_persist` (14/0) · `_undo` · `witness_gantt_lock_integrity`
· `witness_tm_edit_exception` (23/0) · `witness_undo_dot_spawn` · `witness_whatif_authored_sync` —
all green. An edit moves a node and every projection is RECOMPUTED, never patched. That is exactly
why the rescale cannot come back.

---

## §C ADDRESSING — total, but INFERRED. Know which.
**Phase bucketing is total and healthy.** Measured across 5 buildings / **246k elements**:
`hitDefaultBucket` 0–0.1%, and **0 rules name an undeclared phase**.

**Level bucketing is INFERRED, not read** — and the inference is doing most of the work:

| building | `storey` NULL / "Unknown" in `elements_meta` |
|---|---|
| Duplex | **86.0%** |
| Terminal | **69.9%** |
| HHS_Office_Federated | 30.8% |
| Hospital | 15.9% |
| LTU_AHouse | 3.2% |

The live log says the same thing — *"zone is a median-Z INFERENCE, not IFC truth"*. **A level is a
DATUM: its floor up to the next level's floor.** Taking the band as the envelope of its members is
wrong and was measured wrong: one 6.4m riser labelled L1 stretched L1 over L2 and every L2 element
fell out to building scope. Bands must be disjoint by construction.

---

## §D ⛔ THE DEFAULT BUCKET IS MID-PROGRAMME. OPEN, WITH THE FIX BLOCKED ON A MEASUREMENT.
`SEQUENCE_DEFAULT = {phase: 'Architecture Envelope', sequence: 6, resource: 'MASON'}`.

**A catch-all in the middle of the programme can invert.** Only 0–0.1% of elements reach it, but
what reached it was **MEP**: `IfcCableCarrierFitting` ×66 on Hospital (now mapped properly — its
sibling `IfcCableCarrierSegment` already carried the rule, so that was extraction), plus
`IfcFlowInstrumentType` and `IfcSensorType`. All landing at sequence 6, **ahead of MEP Rough-in**.

**Moving it to the terminal band was TRIED AND REVERTED, and the reason is the open item:** no trade
in `LABOR_RATES` can both sit last AND price unknown work. `LABORER` has no productivity table and
no `default_productivity`; `FINISHER`'s five class keys cannot match an unknown class by definition.
Either choice falls to `_installSecs`' silent 120s floor — the `§TPL_ZERO_MINUTE` defect §S65
removed — and `witness_sequence_template_lock` goes red on `no-zero-minute-rows`.

**To close it:** a MEASURED productivity for the catch-all trade, from real data. Not a number typed
into the repo. Until then the default stays mid-programme and `rates.js` carries this note beside it.

---

## §E ⛔ GEOMETRY — STOP DOING ADJACENCY WITH TOLERANCE BANDS
> **USER, 2026-08-26:** *"you truly have no sense of geometry WITNESSing when all the maths in the
> world is at your disposal."* Correct, and this section is the correction.

**Every proxy this lane used was measured wrong:**

| proxy | how it failed |
|---|---|
| bbox XY-overlap + Z band | a **pipe "bears" a wall** |
| support base bounded, TOP unbounded | a 6.4m riser "bears" **everything above its base** |
| bearing-below only | every **ceiling-hung pipe** read as floating |
| all-of instead of any-of | an element needs **one** support, not all — 1961 → 95 |
| structural **class** whitelist | **438 curtain-wall glazing panels** as load-bearing |
| `grounded` = footprint-local | a duct elbow **7.09m up**, 8 contacts all above, judged "ground" |

**The right question for a hung element is CONTAINMENT, and it is computable.**

> **USER RULING:** *"anything that hangs within a well formed room is no issue."*

`scripts/probe_enclosure_geometry.js` — exact ray/AABB slab test, 6 axis rays from each centroid,
12m reach, XY-gridded. No class names, no phase, no tolerance tuning:

```
Duplex   fullyEnclosed 1026/1119 (91.7%)      HHS 5217/6839 (76.3%)

                      judge says unsupported   ENCLOSED   GENUINELY OPEN
Duplex                        28                  26            2
HHS_Office_Federated          63                  53           10
```

**The 63 "uncountable" elements `4D_BAR_MODEL.md` §14.2 built a whole section around are 10.**
53 hang inside formed rooms and are not an issue at all.

**Rule for any future geometric claim here:** if the predicate is a bounding-box test with a
tolerance constant, it is a proxy and it will be wrong on some building. Compute the actual thing —
containment, occlusion, reachability. The mesh and the transforms are in the DB.

---

## §F WHAT IS ACTUALLY MEASURED TODAY (2026-08-26), so nobody re-derives it
Probe: `bim-ootb scripts/probe_template_hells.js` (legacy vs template, one flag apart).

|  | Duplex | HHS_Office_Federated |
|---|---|---|
| **Stacking** biggest pile | 7 → 7 | 7 → 6 |
| **Stacking** piles ≥ 20 | **0 → 0** | **0 → 0** |
| **Floating** | 21/1118 → 25/1118 | 187/6800 → 209/6800 |

**Stacking is at zero** — nowhere near the ≥20 piles §S14 recorded. **Floating is 2–3% and wiring
the template did NOT improve it.** Of HHS's 209: **61** hang entirely from above (their carrier is
on the level above — an ADDRESSING question, §C), **148** rest on something on their own level
classified into a later phase (a **§5.1 data defect**: named, never scheduled around). Neither is
fixable by scheduling; forcing them means overriding the declared programme with geometry, which is
the election that caused all of this.

**Apply §E's enclosure filter before quoting either number as a defect count.**

## ⛔ RESUME
1. **Read §A–§E before writing any code.** If the task is "fix floating/stacking", the answer is a
   construct question, not a new witness.
2. **§D** — measure a productivity for the catch-all trade, then move the default to last.
3. **§C** — a level is a datum. Decide whether an element hung from the floor above belongs to that
   floor's work (would close 61 of HHS's 209).
4. **§E** — fold the enclosure test into the shipped judge so a hung-in-a-room element stops being
   counted at all.
5. Only then re-baseline. `4D_BAR_MODEL.md` §14.6's ordering still stands: retire
   `_tmRescaleToTaskWindow` before gating anything on the times it produces.

---

# §G RESUME — 2026-08-26 session close

**One-liner:** DAY 0 Substructure+Superstructure unsupported, after the ground-bearing exemption:
**Hospital 0 · HHS 1 · Duplex 2 · Terminal 5** at HR 3 (all remaining are `IfcMember`); stacking 0
everywhere; MEP does not appear on DAY 0 on any of the four.

## G.1 ⚠ THE USER IS DEEPLY FRUSTRATED. READ THIS BEFORE YOU TYPE ANYTHING.
Their words, this session, verbatim:

> *"this is the constant hell dealing with u"* · *"Drifting and forgetting"* · *"Trashing all over"*
> *"for weeks talking to u has not solved anything thus i given up"*
> *"i am utterly frustrated do not ask me to make any decisions"*
> *"stop talking ceremony that i dont read. Your language is always horrible, all request for terse
> without drama never end"*
> *"u have no idea how to read WITNESS logging or device one that tells u things are wrong"*
> *"u truly have no sense of geometry WITNESSing when all the maths in the world is at your disposal"*
> *"u have no idea about systems analysis"* · *"your design must have been spaghetti"*

**They are right on the substance.** This session produced five retractions, three wrong metrics and
two silent no-op bugs before a single number could be trusted. Do not defend any of it.

**Standing orders from them, non-negotiable:**
- **Do NOT ask them to decide anything.** Decide, act, show the number.
- **NEVER refer to what is seen / on screen / visually.** Geometry and maths only. A metric named
  `floatingOnScreen` was written this session and that framing is banned.
- **Terse. No ceremony, no preamble, no drama.** They do not read it.
- **Do not report lesser issues.** Their scope is DAY 0, Substructure/Superstructure, no MEP.
- **"First days no MEP"** — verified true on all four buildings; keep it true.
- **"Anything that hangs within a well formed room is no issue"** — enclosure, not bearing.
- Their lead, not yet chased: *"everything was going fine until the gantt editor"*, and
  *"u not even creating any new geometry, just playback"*.

## G.2 WHAT IS DONE (bim-ootb PR #1548, branch feat/arch-envelope-closeup)
- Architecture split into **Envelope (5-6)** and **Closeup (8)**; bands contiguous, sequence 8 was free.
- **All four production call sites now reach the template** (`§TPL_WIRED`); `witness_4d_template_reached`
  gates it, 6/0. It was dead code shipped 2026-08-25 and never called.
- **`§TPL_LAYER_ORDER`** — inside a task, elements are laid out in support order (topological layers
  of the shipped contact graph), banded so a later layer cannot start before an earlier one ends,
  with the solve's crew-leveling preserved inside each band.
- **`§TPL_LAYER_SELFCHECK`** — reports `applied / moved / stillInverted` and FAILS on a no-op. Built
  because the layer pass shipped silently broken and produced identical numbers.
- Enclosure by ray-cast (`scripts/probe_enclosure_geometry.js`): collapses §14.2's "63 uncountable"
  to **10**, and HHS's 209 floating to **18** genuinely open.
- Suite matches clean `main`; ZDA baseline re-locked with its justification.

## G.3 ⛔ OPEN, IN THEIR PRIORITY ORDER
1. **`IfcMember` at DAY 0** — the entire remaining sub/super residue on all four buildings is this
   one class. Start here.
2. **Hospital `stillInverted=778`** inside tasks — cause not established.
3. The default bucket is mid-programme (§D) — blocked on a measured productivity.
4. Their two untested leads in G.1.

**Do not open with a summary of this file. Open with a number.**
