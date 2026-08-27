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

## §A ⛔ SUPERSEDED 2026-08-27 (user decision) — THIS MODEL IS DEAD CODE, DO NOT WIRE AGAINST IT
**Canonical model going forward: the TEMPLATE PATH — `schedule_author.js:425 instantiateTemplate(...)`**,
an explicit edge-based task graph. §I already named it as the owner of "what is the task grid?" the
day this table was built — it was never reconciled with the tree model below, which is why a later
session (2026-08-27 review, §K) re-derived the same contradiction as if new and burned three fix
cycles wiring edges into a model whose own text says "No edges emitted." Every past fix aimed at the
tree below (incl. `bar_model.js buildTree()` and its consumers) was aimed at code that does not run.
**Next work: rewrite this section to describe `instantiateTemplate()` as-built — task grid, edges,
sequencing — not retrofit the tree onto it.** The invariants below (no election / no cycles / no
edges emitted / no stacking) are the OLD model's properties; do not assume any of them hold for the
template path without re-deriving from `schedule_author.js` + `schedule_gate.js` directly. Left
in place, unedited, only as the historical record of what was designed vs. what was verified to run.

## §A [SUPERSEDED — see banner above] THE MODEL — one type, two rules
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


## G.0 ⛔ REGAIN CONTEXT FIRST. YOU KEEP FORGETTING THINGS CENTRAL TO THE FEATURE.
**User, 2026-08-26.** This is the standing complaint across weeks, not one session. The facts below
are load-bearing and were each re-discovered the hard way after being forgotten. Read them before
you touch anything; do not re-derive them.

**Where the feature actually lives**
- The scheduler is **JS in `~/bim-ootb/viewer/`** — `schedule_author.js`, `schedule_gate.js`,
  `cpm_schedule.js`, `support_sweep.js`, `time_machine.js`. `bim-compiler/viewer/` is nearly empty
  and every PR number in these prompts is a **bim-ootb** PR.
- **`git -C ~/bim-ootb fetch && merge --ff-only origin/main` BEFORE reading anything as canon.**
  A 14-commit-stale checkout made this session conclude `4D_template.json` was lost. It was not.
- Work in a `/tmp/wt-*` worktree and **use absolute paths**. A `cd` into the worktree put files in
  the wrong repo three times in one session, once deleting 33 tracked files.

**The three files that define the programme, and which one executes**
- `viewer/rates/4D_template.json` — phases, calendar, `duration_rule`, `capacity_rule`, dependencies.
- `viewer/rates/sequence_rules.json` — a **MIRROR**. Not executed.
- **`viewer/rates.js` IS the executed table.** `viewer.html` never calls `loadSequenceRules()`.
  **Edit both in the same commit** — they have drifted before and it cost real measurements.

**Facts that were forgotten and re-paid for**
- `supportPool` is `seq<=4 ∪ IfcSlab ∪ IfcStairFlight ∪ IfcWall*`. `seq<=4` is **not** a bare phase
  number — it is the classifier's OUTPUT, and `SEQUENCE_NAME_OVERRIDES.glazed_curtainwall_facade`
  is why. HHS `IfcPlate` splits **191 structural / 438 Verglasung glazing**. Any class-based
  "structural" pool re-admits the 438 as load-bearing.
- `support_sweep.js` owns the contact relation: **bearing-below ∪ carrier-above ∪ embedded**.
  Require it. Re-deriving it was wrong four times this session alone.
- `_contactGraph.grounded[i]` is footprint-local — which is **correct** for the ground-bearing
  exemption (nothing beneath me in my own column ⇒ I rest on soil) and **wrong** as an altitude test.
- `min(bz)` over all elements is **not** the ground datum. One deep outlier put every HHS
  ground-floor column 4.70m "in the air".
- This module's IIFE parameter is named `global` and is `self||this` — **in node that is NOT
  globalThis.** A bare `global.SupportSweep` silently returns null. `_writeBarSchedule`'s `_reg()`
  documents this trap; it was walked into anyway.
- `_tmRescaleToTaskWindow` runs **after** `_midairAudit`, so the judge scores a timeline the movie
  does not play. 783 of HHS's floating is manufactured there.

**What is settled and must not be re-litigated**
- Tree shape is **LEVEL-major, phases inside**. Phase-over-level was measured wrong (17 violations).
- `within_level` and `across_levels` are the **same rule at two depths**.
- No edges are emitted — sibling order IS the order.
- Lanes are `LABOR_RATES[trade].max_crews`, a capacity, never a headcount.

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

## G.2 WHAT IS DONE
**bim-ootb PR #1548 — MERGED** (squash, 2026-08-26 11:58Z): ARCH split, `§TPL_WIRED`,
`witness_4d_template_reached`, the probes, the ZDA re-lock.
**bim-ootb PR #1549 — OPEN**: `§TPL_LAYER_ORDER` + `§TPL_LAYER_SELFCHECK`. ⚠ It exists as a separate
PR because #1548 was SQUASH-merged and this work landed after, which orphans it on the old branch —
§10.5's exact pattern. **Start any follow-up off fresh `origin/main`, never off
`feat/arch-envelope-closeup`.**

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
1. ~~**`IfcMember` at DAY 0**~~ — **WRONG CLASS. Corrected 2026-08-26 by measurement, see §H.**
   The residue is **`IfcColumn`, 21 of 21, zero `IfcMember`**, and it is not a scheduling defect.
2. **Hospital `stillInverted=778`** inside tasks — cause not established.
3. The default bucket is mid-programme (§D) — blocked on a measured productivity.
4. Their two untested leads in G.1.

---

# §H THE DAY-0 RESIDUE, RE-MEASURED 2026-08-26 — §G.3 item 1 was the wrong class

**Probe: `bim-ootb scripts/probe_day0_unsupported.js`** (branch `feat/day0-unsupported-probe`,
commits `f48cf56` + `d1e585b`, pushed). §G's headline was measured once and never committed as a
script, so it could not be re-run. It is now a script, and the script disagrees with it.

| building | judged | unsupported | class |
|---|---|---|---|
| Duplex | **0** | 0 | — `§D0_VACUOUS`: all 11 in-scope elements are seq-1 |
| HHS_Office_Federated | 126 | **0** | — |
| Hospital | 566 | **13** | `IfcColumn` x13 |
| Terminal | 158 | **6** | `IfcColumn` x6 (4 of them never held at all) |

**21 of 21 are `IfcColumn`. Zero `IfcMember`.** Do not start on `IfcMember`.

## H.1 A 0 FROM AN EMPTY POPULATION IS NOT A PASS — the probe now says so itself
The first draft of this probe sampled ONE cursor (DAY 0 HR 3, §G's own) and read **0 on all four
buildings**. Not because the model was right — because at that instant every in-scope element on
screen was ground-exempt and **the judge had nothing to judge**. That is the green-witness-over-a-
bad-construct failure this whole file exists to kill, reproduced from inside the file's own lane.
Two changes make it impossible to repeat:
- **`§D0_VACUOUS`** fires when `judged === 0`, and the verdict prints `INCONCLUSIVE`, never `PASS`.
  Duplex is vacuous today and reads that way.
- **The answer is an INTERVAL, not a sample.** Element *i* is unsupported over
  `[start_i, firstSupportStart_i)` — empty when a support is already up, unbounded when there is no
  support at all. Peak concurrency comes from a sweep-line over the endpoints. No cursor to pick,
  so no cursor to pick wrongly.

Also: the exemption is the **SHIPPED** one — `schedule_gate.js:1210` `T.seq !== 1` — not a
re-derived one. An earlier draft used only `G.grounded[i]` and so flagged Duplex's 2
`Floor:150mm Exterior Slab on Grade` (bz -0.137): their only neighbour is an `IfcFooting` whose
**top is 1.113 m below them** (fill in between), so nothing bears them AND `grounded` is 0.
`rates.js` §SLAB_ON_GRADE_RECLASS already names exactly that population.

## H.2 CAUSE — both sub-causes are rows in §E's own table of proxies
Measured element by element; the probe prints every one.

**(a) THE SUPPORT TOP IS UNBOUNDED — §E row 2, verbatim, still live in the shipped judge.**
Hospital column `0Qqamdk$17GhXBxDp5aFcc` (bz 166.445) has as its only "bearing" contacts elements
whose tops are at **177.811 and 170.611 — 11.4 m and 4.2 m ABOVE its own base**. Meanwhile the pile
cap actually under it (`IfcFooting`, top 165.511, **0.933 m** below) and the slab (top 165.811,
**0.633 m** below) are both **rejected**, because `GAP = 0.5`. The judge admits a support 11 m too
high and rejects the footing 0.63 m too low.

> **The two shipped copies of "S bears T" disagree on the upper bound:**
> `support_sweep.js:411` — `S.bz < T.bz - EPS && S.tz >= T.bz - GAP` — **no top bound**
> `schedule_gate.js:1195` (§S64) — same, plus `S.top_z <= T.base_z + GAP`
> §S64's own comment says the wall pool needed that bound or "a wall carries a promoted slab AT ITS
> TOP, never one embedded metres below its crown" — 73 fleet-wide false verdicts. `_contactGraph`
> never got it.

**MEASURED blast radius (`§D0_TOPBOUND`, always on):** of the in-scope bearing contacts,
**Hospital 941 / 2944 = 32.0 %** are supports whose top sits above the base they carry;
Terminal 106 / 987 = 10.7 %; HHS 0.0 %. Applying §S64's bound **removes false supports, so the
count RISES**: Hospital 13 -> **30**, Terminal 6 -> **7**. *The current number is flattered by
contacts that are not carrying anything.* ⛔ Not shipped — changing `_contactGraph` moves the
scheduler, the lock gate and the audit together, and is not done from inside a probe.

**(b) REAL VERTICAL VOIDS — a data defect, not a schedule defect.**
Terminal's 4 "never held" columns (bz 34.768, *05 FOURTH FLOOR LEVEL (OBSERVATORY DECK)*) have
**nothing below them within 4.000 m** — nearest structure is a slab topping at 30.768. Their only
contact is a CARRIER above (the `Kubah` dome proxy). No ordering can hold these. Report as a model
defect; never reschedule around it. Terminal's other 2 rest on an `IfcWall` classified *Architecture
Envelope* (seq 5, starting **237 h later**) — §F's "148 rest on something on their own level
classified into a later phase", confirmed again.

## H.3 ⛔ `§TPL_LAYER_ORDER` MAKES THE NUMBER WORSE, AND ITS OWN WITNESS SAYS PASS
Deliberate A/B, same probe, same DBs, one `viewer/` checkout apart, both reproduced:

| viewer | Hospital | Terminal |
|---|---|---|
| `origin/main` @ `6b12783` (no layer pass) | **13** | 6 |
| `fix/tpl-layer-order` @ `50a4cfe` (with it) | **15** | 6 |

**`§TPL_LAYER_SELFCHECK` reports PASS on that run**, because it only counts inversions where support
and supported are **in the same task** (`if (_taskOf[_S.guid] !== _taskOf[_T.guid]) continue;`). The
2 extra Hospital columns are held from a *different* task, so the self-check is structurally unable
to see the regression it introduced. This is the opening table's *"wrote passes that could not tell
it they had failed"*, one commit after the pass was written to prevent exactly that.
**`fix/tpl-layer-order` (50a4cfe) is NOT merged. Measure this before it lands.**

## H.4 SHARED-WORKTREE HAZARD — hit live, this session
`/tmp/wt-fga-judge` was occupied by a **concurrent session**. `git status` was clean and no `/proc`
cwd pointed at it, so a `git checkout -B` was run there — which **moved that session's working tree
out from under it** mid-task (its reflog: `21:31 commit 50a4cfe` -> `21:32 checkout: moving from
fix/tpl-layer-order`). Nothing was lost only because that session had already committed *and
pushed*. Two measurements taken across the switch silently disagreed (Hospital 15 vs 13) and cost a
determinism hunt — no `Math.random`, no time budget, no DB change; the input was a **different
`viewer/` checkout**.
**Rule: a clean `git status` and zero `/proc` occupants do NOT mean a worktree is free.** A shared
`/tmp/wt-*` may belong to a session that is simply between commands. Make your own
(`git worktree add /tmp/wt-<your-topic> <branch>`) and never `checkout` inside someone else's.

## H.5 ONE STALE NUMBER IN §G.2
`probe_enclosure_geometry.js` re-run confirms §E exactly (Duplex fullyEnclosed 1026/1119 = 91.7 %,
HHS 5217/6839 = 76.3 %; `des=-1` Duplex 28 -> 26 enclosed / 2 open, HHS 63 -> 53 / 10). But §G.2's
*"HHS's 209 floating to 18 genuinely open"* is one commit stale: on the current template schedule it
is **245 floating -> 20 genuinely open**. §TPL_WIRED landed after that measurement.
That script was also living **only on one disk, unversioned**, while §E and §G.2 both cite it as
their source. It is now committed (`f48cf56`).

## H.6 ⛔ NEXT
1. **Decide the top bound on `_contactGraph`'s bearing clause** (§H.2a). One line, 32 %-of-contacts
   blast radius on Hospital, and it makes the number worse before better. A construct decision —
   §A's kind of question, not a witness's.
2. **Re-measure `fix/tpl-layer-order` against §D0 before merging it** (§H.3), and widen
   `§TPL_LAYER_SELFCHECK` to cross-task pairs or it will keep passing on regressions.
3. Terminal's 4 void-standing columns (§H.2b) belong in a **data-defect report**, not the scheduler.
4. Duplex is **vacuous** for this metric — it proves nothing either way today.

**Do not open with a summary of this file. Open with a number.**

---

# §I OWNERSHIP TABLE — WHO OWNS EACH RELATION (built 2026-08-27, from the code)

**Why this exists.** The single most expensive failure in this lane is re-deriving a relation the
codebase already owns — §G.0 counts four times in one session, and a fifth happened the day after
(the ground exemption re-derived as `grounded[i]` when the shipped rule is `seq !== 1`). The cause
is not carelessness, it is that **nothing said who owns what**. This table says it.

**How to use it: find your question, call the owner, do not write a second copy.** If the answer you
need is not in this table, that is a finding — add the row rather than inventing the relation inline.

Paths are `~/bim-ootb/viewer/` unless stated. Line numbers are `origin/main` @ `6b12783`.

| the question | OWNER — call this | never |
|---|---|---|
| **does S support T?** (bearing-below ∪ carrier-above ∪ embedded) | `support_sweep.js:384` `_contactGraph(items)` → `{contacts, grounded, orphans, ok}` | write a bbox/Z-band test inline. ⚠ **2 more copies exist — see §I.1** |
| **does T rest on soil?** (ground exemption) | `schedule_gate.js:1210` — `T.seq !== 1`. Substructure legitimately rests on unmodeled soil | use `grounded[i]` for this. It is **footprint-local** and answers a *different* question — see §I.2 |
| **which ONE thing supports T?** | `support_sweep.js:432` `_designatedSupport(items, G)` | elect a support yourself. §A: the election is the defect |
| **what phase/trade is this element?** | `schedule_author.js` `matchNameOverride()` → `matchRule()`, tables in **`rates.js`** | read `rates/sequence_rules.json` — it is a MIRROR, never executed (§G.0) |
| **how long does it take?** | `schedule_author.js:78` `_installSecs(cls, rule, laborRates, realQty, lengthRatio)` | hand-roll it. `time_machine.js:4494` `getInstallSecs` already delegates here; its local fallback is documented as a divergence risk |
| **what is the task grid?** | `schedule_author.js:425` `instantiateTemplate(...)` from **`rates/4D_template.json`** | derive phases from what the elements did. §B: an envelope cannot constrain what drew it |
| **when does each element happen?** | `schedule_gate.js:421` `computeSchedule(...)`, then `cpm_schedule.js:796` `run(...)` | re-solve. The template path ends at `schedule_author.js:884` `remapSolveToTasks` |
| **where inside its task?** | `schedule_author.js:884` `remapSolveToTasks(solve, tasks, startISO)` | ⚠ a 4th arg `layerOf` exists only on unmerged `fix/tpl-layer-order` — see §H.3 |
| **what level is it on?** | **OWNER: `viewer/lib/level_deriver.js` `LevelDeriver.derive/levelFor`** (T1 containment → T2 declared name → T3 geometry grid → T4 counted). Two callers: `location_axis.js` (display) and, **flag-gated**, the task grid — `schedule_author.js` `_deriverLevelAxis()` behind `opts.levelSource === 'deriver'`, **default OFF**, see §I.3a | re-derive it from `e.storey`. The OLD path (`schedule_gate.js:404` `collapsePhase` → `:338` `deriveBandRanks`) is still what runs by default and is **broken — see §I.3**; do not build new work on it |
| **are two storey names one floor?** | `schedule_gate.js:382` `deriveStoreyMergeMap(spatialStructure)` | ⚠ **has never once run on any shipped building — §I.3** |
| **is this slab ground-bearing?** | `schedule_gate.js:201` `groundworkSlabs(els)`, one shared definition | reclassify slabs inline in a recipe |
| **is anything floating?** | `support_sweep.js:500` `_midairAudit(items)` (movie) · `schedule_gate.js:1122` `auditFloating(...)` (gate) | ⚠ these two disagree on the support top bound — §I.1 |
| **is an edit legal?** (🔓→🔒) | `verifyGanttIntegrity()` → `_midairAudit` | re-score with your own physics |
| **is it on screen at cursor?** | `time_machine.js:169` — `placed` = `start_ts <= cursor && end_ts <= cursor`; `frontier` = `start_ts <= cursor < end_ts` | invent a visibility rule. A probe using `s <= cursor` is counting placed **+** frontier |
| **WHICH MODEL produced this schedule?** | `schedule_author.js:715` — the `§TPL_MODEL` line. `model=template` = CANONICAL, `model=legacy-deriveZones` = the dead model (PR #1553) | assume the canonical model ran because the code *can* pass `template:`. 24 of 35 witnesses pass none, and the fork was SILENT until 2026-08-27 |
| **what did the run actually say?** | the persisted `witness.log` — `bim-ootb scripts/cache_4d_run.js` | re-run `materializeZones`, and **never** wrap it to silence `console.log` (PRIMAL LAW clause 3) |

## §I.1 ⛔ "S supports T" HAS THREE IMPLEMENTATIONS AND THEY DISAGREE
Verified 2026-08-27, not inferred — all three carry the same comment text, so they were copied:

| # | where | upper bound on the support |
|---|---|---|
| 1 | `support_sweep.js:410` — **the owner** | `S.bz < T.bz - EPS && S.tz >= T.bz - GAP` — **none** |
| 2 | `cpm_schedule.js:81` — a full independent copy, *not* a delegation (`function contactGraph` at `:54` re-implements the grid, the cells, the clauses) | **none** |
| 3 | `schedule_gate.js:1195` `auditFloating` wall pool (§S64) | **`S.top_z <= T.base_z + GAP`** |

Copy 3 got the bound because without it "a wall carries a promoted slab AT ITS TOP, never one
embedded metres below its crown" — 73 fleet-wide false verdicts. Copies 1 and 2 never got it.
**MEASURED consequence (§H.2a): 32.0 % of Hospital's in-scope bearing contacts (941/2944) are
"supports" whose top sits above the base they carry.** `cpm_schedule.js`'s copy is the one the
solve runs on, so this is not academic.
`bar_model.js:345` `attachContacts(leaves, contacts, grounded)` is **not** a fourth copy — it is a
consumer, it takes the graph as a parameter. That is the correct shape; the other two should be it.

## §I.2 `grounded[i]` AND `seq === 1` ARE DIFFERENT QUESTIONS
`support_sweep.js:417` — `grounded[i] = (lowest < T.bz - GAP) ? 0 : 1`, where `lowest` is the min
`bz` of everything overlapping T in XY. It means **"nothing is beneath me in my own column"**.
- ✅ Correct for: *is this element resting directly on soil in its footprint?*
- ❌ Wrong for: *is this element allowed to be unsupported?* — that is `seq === 1`.

The gap between them is real and measured: Duplex's 2 `Floor:150mm Exterior Slab on Grade` (bz
−0.137) sit over an `IfcFooting` whose top is **1.113 m below** them (fill in between). Something
*is* beneath them, so `grounded = 0`; nothing *touches* them, so no bearing contact. Only
`seq === 1` exempts them, and `rates.js` §SLAB_ON_GRADE_RECLASS exists to give them that seq.

## §I.3 ⛔ THE LEVEL RELATION IS THE ONE THAT IS ACTUALLY BROKEN
This row has no trustworthy owner, and it is the root of the DAY-0 defects in §H/§W_D0.

**`elements_meta.storey` is mostly absent** (measured 2026-08-27, fleet-wide):

| building | storey NULL/Unknown | declared `IfcBuildingStorey` | bands the schedule uses |
|---|---|---|---|
| Duplex | **86.0 %** (1026/1193) | *no `spatial_structure` table* | 4 |
| Terminal | **69.9 %** (33848/48428) | **6** | **22** |
| HHS_Office_Federated | 30.8 % | 3 | 4 |
| Hospital | 15.9 % | *no `spatial_structure` table* | 8 |

So `collapsePhase`/`deriveBandRanks` are running on a **median-Z inference for 70 % of Terminal**.
Result: the IFC declares 6 storeys, the schedule invents 22, and `06 ROOF LEVEL` — declared by
**10** elements — collects **10,950**. Three naming systems coexist in one federated model: Malay
`Aras *`, English `0N … FLOOR LEVEL`, and `Ceiling Level *` reference planes.

**`deriveStoreyMergeMap` — the function whose whole job is to collapse those — has NEVER RUN.**
It reads `spatial_structure.elevation`; **0 of 4 shipped DBs have that column** (Duplex and Hospital
have no such table at all). Its failure prints as `§S18_STOREY_MERGE_FAIL … "no elevation data,
bands unmerged"`, which reads like benign degradation and is not: it is the level model being wrong.
⚠ **A log line that understates is the same defect as one that lies.**

**What has been ruled out, so nobody re-walks it:**
- `bim-compiler scripts/normalize_storey.py` — run on a Terminal copy it renames 5 `Ceiling Level N`
  bands to `Level N` and reports **`storey rows merged: 0`**; 23 distinct in, 23 out. Does not close it.
- **Room injection does not carry better storey data.** `TermRooms_extracted.db` and
  `Terminal_meta.db` both hold **byte-identical** coverage: 33,848 Unknown, 22 names.
- The datum IS recoverable for 2 of 4: `spatial_structure.center_z` is populated for Terminal (6
  storeys, 17.9–39.8 m) and HHS (3), with `size_z = 0` — a placement point, so `center_z` *is* the
  elevation. `deriveStoreyMergeMap` looks for a column named `elevation` and never sees it.

## §I.3a THE OWNER IS NOW `LevelDeriver` — WIRED, MEASURED, AND LEFT OFF (2026-08-27)
Per §I.4 ("a relation belongs here the moment a **second** caller needs it") the level relation now
has two callers, so it gets a real owner instead of a warning. bim-ootb PR `feat/tpl-level-deriver`.

**Why `LevelDeriver` is immune to the §I.3 corruption — verified in code, not assumed.**
`assignStoreyByZ` (`schedule_author.js:342`) is a **pure local function**: it returns a string that
is stored only into the in-memory element literal's `storey:` field (`:368`). It issues **no
`db.run`/`UPDATE`** — `elements_meta.storey` on disk is never touched by it (the only writers of
that column repo-wide are `wizard_storeys.js`, a deliberate user act, and the importers).
`LevelDeriver.readLookups` reads `SELECT guid, storey FROM elements_meta` **straight from the frozen
DB** (`level_deriver.js:67`), and `levelFor` consumes only `guid`/`base_z`/`top_z` off the element —
**it never reads `el.storey`**. So the `_UNKNOWN` guard at `level_deriver.js:178` sees the
UNREWRITTEN value and actually fires, where the structurally identical guard in `deriveBandRanks`
(`schedule_gate.js:350`) is dead code on this path. **The swap removes the exposure; it does not
relocate it.**

**Measured, `§TPL_LEVEL_DISAGREE`, `viewer/tests/probe_tpl_level_axis.js`:**

| building | n | fabricated by `assignStoreyByZ` | STRUCTURAL disagreement | tasks | grid source |
|---|---|---|---|---|---|
| Duplex | 1,119 | 976 (**87.22%**) | 107 (**9.56%**) | 20 → 20 | `uniform3m` ⚠ |
| HHS_Office_Federated | 6,839 | 2,120 (**31.00%**) | 533 (**7.79%**) | 20 → 17 | `declared` k=3 ✅ |
| Hospital | 63,182 | 9,457 (**14.97%**) | 25,198 (**39.88%**) | 42 → 68 | `uniform3m` ⚠ |

The fabrication column reproduces the 87.0 / 30.8 / 14.9% figures independently. **But fabrication
does NOT predict disagreement** — Duplex is 87% fabricated and only 9.6% structurally different,
Hospital is 15% fabricated and 39.9% different. `assignStoreyByZ`'s nearest-median-Z guess usually
lands on the same floor the geometry does; the divergence comes from the **grid**, not the guess.

⚠ **Report STRUCTURAL, never the raw key diff.** Raw diff counts an element as disagreeing when only
the level's *name* changed. Duplex first measured **100.00%** raw; the real regrouping was **9.56%**.

**⛔ WHY THE FLAG IS OFF — the blocker is DATA, upstream of this code.**
`Duplex_extracted.db` and `Hospital_extracted.db` have **no `spatial_structure` table at all**
(verified by `PRAGMA table_info` — returns empty); only HHS carries storey `center_z`. With no
declared floor lines `buildGrid` falls back to a **uniform 3.0 m grid**, so Hospital's 8 real storeys
become 17 grid lines / 16 occupied levels and the task grid inflates 42 → 68. Level labels degrade to
a mix of real names and synthetic `L3/L5/L8/L9/L10/L11/L12/L15`, several won on absurd plurality
margins (`§TPL_LEVEL_AXIS_NAMEVOTE`: grid line 4 labelled `"Level 6"` on **2 votes out of 5,676**).
This is the same gap `bim-compiler d0b226dce` ("§STOREY_DATUM — write `IfcBuildingStorey.Elevation`,
which was never extracted") just fixed at the extractor — **the shipped DBs have not been
re-extracted since.** Flip this flag only after they are, and re-run the probe to confirm
`gridSource=declared` fleetwide.

**⚠ The witnesses are SCOPE-BLIND to this.** All three template witnesses pass flag-ON
(11/11, 29/29, 6/6; `ran` 82 → 105) *and* are byte-identical flag-OFF. They pass because their
invariants judge crew-legality, ladder monotonicity and coverage — **none of them judges whether the
level PARTITION is correct**, which is exactly what flipping would damage on Hospital. A green
witness here is not permission to flip.

No `gen_version` bump (`_GANTT_CACHE_VERSION = 37`, `time_machine.js:8260`) is needed while the flag
is off: shipped output is proven byte-identical. **Flipping it later REQUIRES the bump**, or existing
users keep a stale cached schedule built on the old level partition.

**Follow-up, not chased here:** `time_machine.js:3687` and `:4563` carry their own copies of
`assignStoreyByZ` feeding the movie/x-ray element builders. Same fabrication, different consumers —
out of scope for this slice.

## §I.4 HOW TO ADD A ROW
A relation belongs here the moment a **second** caller needs it. The shape to copy is
`bar_model.js attachContacts` — take the computed relation as a **parameter**; never recompute it
because the module boundary made it inconvenient to pass. Both §I.1 copies exist for that reason.

---

# §J SESSION 2026-08-27 — WHAT IS ZERO, WHAT WAS RETRACTED, AND THE ONE GAP THAT KEEPS PRODUCING FICTION

## §J.0 ⛔ REVIEW THIS SPEC BEFORE EACH THINKING PASS — NOT ONCE AT SESSION START
**USER RULING 2026-08-27: *"Update the specs first, review each time u set out to think."***

Reading this file once and then working from memory is what produced §J.2's three retractions —
all three are errors this file ALREADY listed, and it had been read that same morning. The rule is
therefore not "read the spec at startup". It is: **before each new measurement or claim, re-open
§E and §I and check the thing you are about to compute against them.** A proxy you are about to
invent is almost certainly already in §E's table of proxies that were measured wrong.

Corollary, the user's own words: ***"If the needle has not moved, then stop and review the model
again and again. Dont put square pegs in round holes."*** A metric invented on the spot to fill a
hole where a RULE should be is the square peg. Write the rule first.

## §J.1 ✅ WHAT IS AT ZERO, AND HOW IT IS CHECKED
Witness: **`bim-ootb viewer/tests/witness_day0_integrity.js`** (§W_D0), run off the persisted cache.
Fleet verdict went **4 PASS / 9 FAIL / 3 INCONCLUSIVE → 14 PASS / 0 FAIL / 2 INCONCLUSIVE.**

| claim | what it asserts | result |
|---|---|---|
| C1 BAND MODEL | bands used == storeys the IFC declares | PASS Duplex 4/4 · HHS 3/3 · Terminal 6/6 · Hospital INCONCLUSIVE |
| C2 SUB FIRST | nothing starts before the Substructure it sits on finishes | PASS ×4 |
| C3 DAY0 SUPPORT | nothing on screen is unheld — **split ORDER vs MODEL** | **ORDER = 0 on all four** |
| C4 NO EARLY MEP | no MEP phase on DAY 0 | PASS ×4 |

Plus, measured directly off the shipped contact graph: **of 718 `IfcColumn` that rest on a real
`IfcSlab`/`IfcFooting`, ZERO start before that support finishes** (Duplex 0/0, HHS 0/221,
Hospital 0/378, Terminal 0/119).

**The two INCONCLUSIVE are honest unknowns, not hidden failures**, and the verdict refuses to print
GREEN because of them: Hospital has no `spatial_structure` table (nothing to check bands against),
and all 87 of its DAY-0 elements are seq-1 ground-bearing footings (nothing for C3 to judge).

**Fixes that got there** (all on `feat/day0-unsupported-probe`, pushed):
- **`§STOREY_DATUM`** — a level is a DECLARED datum, band *i* = `[datum_i, datum_i+1)`, element
  assigned by its own BASE. Replaced nearest-median-Z-of-elements over a pool of raw labels
  (unbounded, and the inference §PATHS NOT TO TAKE #7 forbids). Terminal **22 bands → 6**.
  No declared storeys ⇒ unchanged, and the §-line says so.
- **`tools/extract.py`** now writes `IfcBuildingStorey.Elevation` — it never did, which is why
  `deriveStoreyMergeMap` has NEVER RUN on any shipped building (§I.3).
- **`§TPL_LADDER_BRIDGE`** — the across_levels ladder now bridges past dropped phases, as
  `_empty_phase_rule` already required for the within_level chain. Duplex's
  `Superstructure @ Level 1` had been starting at h0.0 **in parallel with** `Substructure @ T/FDN`.
- Three name overrides, each MEASURED fleet-wide before the pattern was written:
  `foundation_wall_substructure` (Duplex 7, Hospital 28), `finish_floor_finishes` (Duplex 14),
  `stair_member_architecture` (Duplex 4 of 4; **0 of 9,019** `IfcMember` elsewhere).
- **`scripts/cache_4d_run.js`** — run the pipeline ONCE per building, persist `witness.log` +
  `run.json`, key on the DB *and* the content of every input module. 119,568 elements read back in
  **0.43 s**. Every probe reads the cache.

## §J.2 ⛔ THREE RETRACTIONS IN ONE SESSION — ALL THREE ALREADY IN §E
Recorded because the pattern matters more than any one of them.

| I claimed | reality | §E row it violated |
|---|---|---|
| "column before slab by 506.3h", ~80 fleet type-order inversions | **fiction** — the type buckets were filled by the bbox rule, so Hospital's 33,324 `IfcPlate` metal deck and its `IfcCovering` ceilings landed in "slab". I was timing ceilings. Hospital L1 really has 3 `IfcSlab` @293.3h vs 254 `IfcColumn` @300.3h — correctly ordered | *a proxy will be wrong on some building* |
| 92,397 bearing violations | counted every bearing PAIR as a constraint; any-of gives 6,734 | **row 4** — "an element needs ONE support, not all — 1961 → 95" |
| ground exemption = `grounded[i]` | shipped rule is `seq !== 1` (`schedule_gate.js:1210`) | §I.2 — they answer different questions |

**The common cause is not carelessness.** Each time, a number was needed and no RULE existed that
said what the number should be, so a metric was invented on the spot. An invented metric is a proxy,
and §E's whole point is that proxies here are wrong.

## §J.3 ⛔ THERE ARE TWO MODELS AND ONLY ONE RUNS
Verified 2026-08-27, not inferred:
- **`bar_model.js` + `bar_needs.js` are DEAD CODE.** No HTML loads them, `sw.js` does not precache
  them, and nothing outside their own three witnesses calls `BarModel.*`. PR **#1542 is MERGED**.
- They already contain the answers this lane keeps re-deriving: the trade ladder, `phaseOrder`
  ("EXTRACTED, never authored" — a phase's rank is the min sequence its own classes carry),
  `§BAR_LEVEL_FROM_GEOMETRY` ("the IFC storey STRING is not the location … geometry wins"), and
  `§BAR_LEVEL_BANDS`, the granularity **dial** — "a knob, not a defect".
- **`schedule_author.js` is what actually runs**, and it is what §J.1 fixed.

This is §G.2's failure repeating: *"the whole template path shipped, was witnessed, and was NEVER
CALLED — green and dead at the same time."* It happened again, with the Bar model.
⛔ **Establish which model is the target BEFORE touching either.** §J.1's `§STOREY_DATUM` and the
Bar model's `correctLevelsByGeometry` are two independent answers to the same question, in two
modules, one of which cannot run.

## §J.4 ⛔ THE GAP THAT PRODUCES THE FICTION — THE ONE THING NOT IN THIS REPO
**There is no stated rule for what a correct construction sequence must satisfy beyond DAY 0.**

DAY 0 has four written claims, so DAY 0 could be driven to zero. Past DAY 0 there is nothing, so
every measurement becomes a fresh modelling problem and §J.2 is the result.

Concretely unknown, and therefore unjudgeable today: **6,734 bearing-order violations over 106,027
judged pairs** (`scripts/knob_sweep.js`, any-of, off the shipped contact graph). That number is
REPORTED, not called a defect and not called a residue — nothing says which it is.

What the rule has to fix, in the terms it must be written in:
1. **At what granularity does precedence bind** — element, trade, task, or level?
2. **What may legitimately run concurrently** — when is an overlap a real crew working two fronts,
   and when is it a defect?
3. **Which relation carries it** — the bearing graph, the declared phase order, or the trade ladder?
   (`§KNOB_SWEEP` shows the *type* order is an EXPRESSION of the support relation, not a separate
   fact — a column stands on the slab because the slab bears it.)

Until 1–3 are written here, every number past DAY 0 is a metric someone invented, and §J.2 says what
those are worth. **Write the rule, then measure.** Not the other way round.

## §J.5 THE SWEEP HARNESS EXISTS AND IS CHEAP
`scripts/knob_sweep.js` — turn a variant into a dial, run the whole fleet at every setting, and look
for a PLATEAU where the buildings agree, instead of hand-picking a constant (every hand-typed
threshold in this lane has been measured wrong). Runs off the cache in seconds.
Dials **not** swept, named rather than faked because each needs a pipeline re-run per setting:
`§STOREY_DATUM` mode, the §S64 support top bound (§H.2a — 32.0 % of Hospital's bearing contacts),
and `bar_model.js` `§BAR_LEVEL_BANDS` granularity.

---

# §K THOROUGH REVIEW — SPEC vs MODEL vs PLAN (2026-08-27, user-requested)

## §K.1 THE MODEL IN THIS FILE IS NOT THE MODEL THAT RUNS
§A states the model: `Node { children[], work }`, a tree **Building → Level → Phase → Layer →
Element**, and — explicitly — ***"No edges emitted — sibling order IS the order."***

| | implements §A? | runs? |
|---|---|---|
| **`bar_model.js` `buildTree()`** — `GroupBar('Project')` → level → task → `ElementBar` leaves | **YES. This IS §A's tree.** | **NO — dead code (§J.3)** |
| **`schedule_author.js` `instantiateTemplate()`** — per-(phase × level) tasks + `edges.push()` ×2 | **NO. It is a task GRAPH with explicit edges**, the thing §A says the model does not have | **YES — this is production** |

**That single row explains the whole lane.** Every §H/§J fix, including `§TPL_LADDER_BRIDGE`, was a
repair to *edge* wiring in a model whose own spec says there are no edges. The spec and the running
code have been describing two different machines, and the reason §J.2's metrics kept being invented
is that they were being invented against a model that is not the one executing.

⛔ **Before any further scheduling work: decide which of the two is the target.** They are not
complementary — §J.1's `§STOREY_DATUM` and `bar_model.js`'s `correctLevelsByGeometry` are two
independent answers to one question, in two modules, one of which cannot run.

## §K.2 THE PLAN ALREADY EXISTS AND I DID NOT FOLLOW IT
**`prompts/4D_BAR_MODEL.md` §10.3** is the written plan, in order, and it opens with
***"Do not wire anything live until 1–3 are done."***

1. **Kill the scratchpad probes** — fold them into `witness_bar_schedule.js`. Called *"the single
   highest-value task in the lane."*
2. **Audit every witness in the lane against §10.1 rule 1** (any predicate that duplicates a shipped
   one instead of calling it). *"That is the error that produced the only retraction so far, and it
   produced it twice in one day."*
3. Verify `witness_bar_needs.js`'s anti-re-derivation gate directly.
4. Terminal midair **336 vs a shipping 226** — the only axis where the model still loses.
5. The storey-injection gap (below).

**Measured against that plan, this session did none of 1–3** and instead re-derived item 5's
diagnosis by a different route. §J.2's three retractions are precisely what item 2 exists to prevent.

## §K.3 ⛔ ITEM 5 ALREADY NAMED THE FIX — AND SAID MY EXTRACTOR CHANGE WAS NOT NEEDED
§10.3 item 5, **corrected 2026-08-26, the day before this session**:

> *"The mechanism is NOT missing and needs NO source IFC and NO extractor change.
> `viewer/lib/room_walker.js` `writeRooms()` already injects storey rows from the DB alone …
> It has already run on Terminal: **all six of Terminal's storey rows are its output — Terminal has
> ZERO real extracted storeys.** §S18 `deriveStoreyMergeMap` DOES run; it merges 0 because the
> injector emits a storey row **only where it compiled a room** (`room_walker.js:1352`) …
> **The gap is that one line**, in the room lane's file, not in extraction and not in the scheduler."*

**VERIFIED DIRECTLY this review** (the doc is right): every Terminal `IfcBuildingStorey` row is
`guid='STC_…'`, `object_type='COMPILED'` — the injector's own stamp. This is the "room injection"
the user named; it is `writeRooms()`, which is why grepping for "room injection" found nothing.

So `tools/extract.py`'s `elevation` write (§J.1) is **not wrong, but it is not the gap** — the spec
said so a day earlier and I did not read it. It helps only future extractions.

## §K.4 NEW, MEASURED IN THIS REVIEW — THE INJECTED DATUM IS MID-WALL, NOT A FLOOR
`room_walker.js:1191` — `stZ[st] = mean(w[2])`, and `w[2]` is a wall's **centre**-z. A storey datum
must be its FLOOR. Measured against Terminal's own slabs:

| storey | injected datum | median slab top | error |
|---|---|---|---|
| Aras Tanah | 17.927 | 14.768 | **+3.159 m** |
| Aras 01 | 24.717 | 22.768 | +1.949 |
| Aras 02 | 28.577 | 26.768 | +1.809 |
| Aras 03 | 32.537 | 30.768 | +1.769 |
| Aras 04 | 37.142 | 34.768 | +2.374 |
| Aras Bumbung | 39.779 | 39.141 | +0.638 |

⛔ **Consequence for what §J.1 shipped:** `§STOREY_DATUM` bands by `base_z ∈ [datum_i, datum_i+1)`.
An element standing on the Aras 03 floor has `base_z ≈ 30.77` against an Aras 03 datum of 32.537, so
it lands in the **Aras 02** band. **Every element on every floor is assigned one level DOWN,
systematically.** The §W_D0 claims still pass because the bands stay disjoint and monotone — the
offset is uniform — which is exactly why a passing witness did not catch it. *A green suite over a
shifted datum is §J.0's square peg wearing a rosette.*

**The data for the correct value is already in hand:** the walker's wall row is
`[cx, cy, cz, bx, by, bz]`, so a wall's base is `w[2] - w[5]/2`. The floor is the base, not the centre.

## §K.5 ⛔ PLAN — IN THIS ORDER, AND IT IS NOT MINE
Follow `4D_BAR_MODEL.md` §10.3. Nothing below reorders it; the only additions are §K.1 and §K.4.

0. **Decide the target model (§K.1)** — Bar model or template path. Everything else forks on it.
   This is the one item that is a *decision*, not work.
1. §10.3 #1 — fold the scratchpad probes into `witness_bar_schedule.js`. `scripts/knob_sweep.js`
   and `scripts/probe_day0_unsupported.js` from this session are in the repo and gated by nothing;
   they are the same debt.
2. §10.3 #2 — audit every witness in the lane for a re-derived predicate (§I.1 lists three live
   copies of "S bears T"; two of them disagree with the third).
3. ✅ **§K.4 DONE (witness) 2026-08-27 — bim-ootb PR #1552.** Datum is now the FLOOR
   (`room_walker.js:1191` → `mean(w[2] - w[5]/2)`) and a storey row is emitted for every storey
   walked (`:1353` guard removed). Fleet 22/22 storeys match mean wall base-z, 0/22 centre-z;
   Terminal max datum error 3.072 m → 0.511 m; rows recovered Terminal 6→7, Hospital 7→8.
   **Full evidence + the two carried-forward ⛔ items (shipped DBs/baked patches still hold the old
   datum; `compile_rooms.py` py/js lockstep still unfixed) are in §L item 1 — read it there.**
   Then §10.3 #5.
   Predicted payoff for the second alone (SIMULATED in §S72.1, **not measured**): Terminal midair
   **336 → 48**, span 126 → 105 d.
4. §10.3 #4 — Terminal midair 336 vs shipping 226, the only axis where the Bar model still loses.
5. Only then re-baseline, and only then judge the 6,734 (§J.4) — which still has no rule to be
   judged against.

---

# §L ⛔ START HERE — SESSION HANDOFF 2026-08-27

**Read §G.0 (regain context) → §I (ownership table) → §K (this review). Then §K.5 is the plan.**
There are four "next" lists in this file now (§F, §G.3, §H.6, §J.4); **§K.5 supersedes all of them.**

## ✅ The decision that gated everything is MADE
**USER RULING 2026-08-27: the canonical model is the TEMPLATE PATH — `schedule_author.js`
`instantiateTemplate()`.** See the banner at §A.
- `bar_model.js` / `bar_needs.js` are **not the target**. Their measured findings still read well;
  their code is not where work goes.
- **§K.1 is superseded.** It said the fixes went into the wrong model; with the ruling they went
  into the right one, and it was the SPEC (§A) that was stale. §A now carries that correction.
- **`4D_BAR_MODEL.md` §10.3 items 1-4 are no longer the plan** - they are Bar-model work. Item 5
  stands (it is about `room_walker.js`, which feeds the template path too).
- The "no edges emitted" line in §A describes the dead model. The canonical model emits edges and
  that is correct by ruling. Do not re-litigate it.

## ⛔ Therefore the plan is now, in order
1. ✅ **DONE (witness) 2026-08-27 — §K.4 datum fixed.** bim-ootb **PR #1552**, branch
   `fix/room-walker-storey-datum`, one file (`viewer/lib/room_walker.js`) + `scripts/probe_storey_datum.js`.
   - `:1191` now writes `mean(w[2] - w[5]/2)` — the FLOOR. `§STOREY_DATUM_FLOOR` logs centre and
     base side by side so the pair is never hand-re-derived again.
   - `:1353` guard removed; `§STOREY_ROW_EMIT walked/withRooms/withoutRooms` reports what the old
     guard would have dropped, and prints VACUOUS when nothing passed the >=3-wall gate.
   - **MEASURED** (`scripts/probe_storey_datum.js`, datum vs that storey's own median slab top).
     Terminal: Aras Tanah +3.072→+0.511 · Aras 01 +1.947→+0.146 · Aras 02 +1.779→+0.102 ·
     Aras 03 +1.770→**+0.001** · Aras 04 +2.374→+0.200 · Bumbung +0.638→−0.000. Fleet **22/22**
     storeys now equal mean wall BASE-z, **0/22** equal mean centre-z (was 22/22 centre).
   - Rows recovered: Terminal 6→7 (`GROUND FLOOR LEVEL`), Hospital 7→8 (`Level 7A`, a mezzanine at
     196.470 that had NO band between Level 6 @192.159 and Level 7 @199.814).
   - Guard removal verified safe: it keyed on ROOMS, not on DB presence, so it never prevented
     duplicates (Terminal already ships 6 same-name real rows per storey); those real rows carry
     `center_z` NULL on every shipped DB so the consumer's `center_z IS NOT NULL` filter never sees
     them. `stZ` only holds storeys past the >=3-wall gate, so no unwalked storey can be emitted.
   - Regression: `witness_s50_cell_engine` bad=0 · `witness_s55_identity_vs_cell` 6/0 ·
     `witness_stair_flight_assembly_merge` 4/0 · `witness_midair_zero` **10 pass/3 fail BOTH before
     and after** (run on unmodified main to confirm) with judged numbers byte-identical — those 3
     locks are item 2's re-baseline, not a regression.
   - ⛔ **Still carrying the OLD datum:** the shipped `buildings/*_meta.db` and the baked
     `buildings/patches/Terminal_meta.db.sql` (6 rows: 10.066/13.838/17.819/22.629/25.126/3.049, no
     `GROUND FLOOR LEVEL`). This PR fixes the GENERATOR only — regenerating those is a DB-content
     change and needs the patch + self-heal-loader flow.
   - ⛔ **py/js lockstep diverged:** `scripts/compile_rooms.py:1232` (bug 1) + `:1267` (bug 2) and
     the stale `build/room_walker.js:1169`/`:1253` still carry BOTH bugs. They stay in lockstep with
     *each other*, so `build/witness_room_walker_parity.js` (which compares those two, not the
     bim-ootb file) is unaffected today — but they should be mirrored, with a `ROOM_WALKER_V` bump.
2. **Re-baseline `§W_D0` after 1** - the current 14 PASS sits on a datum that is uniformly ~2m high.
3. **Write the sequence rule (§J.4)** - granularity of precedence, what may legitimately overlap,
   which relation carries it. Until it exists the 6,734 cannot be judged, and inventing a metric to
   judge it is what produced §J.2's three retractions.
4. Only then re-open anything else.

## ✅ THE TM WIRING IS SOUND — TRACED 2026-08-27, bim-ootb PR #1553
**The question "does the canonical model reach the Gantt editor?" is ANSWERED: yes. Do not
re-trace it.** `instantiateTemplate` (`schedule_author.js:425`) → `_writeTemplateSchedule` (`:965`)
persists `tasks`/`task_elements`/`task_sequences` (`:1040-1055`) → `time_machine.js
buildTaskIndex()` (`:5290`) reads those same rows back (`:5341`, `:5347`) → `gantt_model.js
buildTasks()` (`:96`) draws each bar at its **task** window (`:167-176`, `spanFrom='task'`).
The hand-off is a DB round-trip, not an object hand-off — TM never sees `instantiateTemplate`'s
return value, and it does not need to.

**§G.2's "shipped, witnessed and NEVER CALLED" is CLOSED** — `§TPL_REACHED` is green on `main`
(all four production call sites pass `template:`, replay confirms `instantiateTemplateRan=true`).
`§TPL_WIRED` (PR #1548) fixed it. Do not re-open that contradiction; it is historical.

**What was actually wrong: three holes that let the canonical model be swapped for the dead one
SILENTLY.** All three fixed in PR #1553.
1. **`schedule_author.js:715` — the fork was silent.** Falsy `opts.template` fell through to
   `SG.deriveZones` with no log line, so no downstream number was attributable to a construct.
   Both branches now emit **`§TPL_MODEL model=template|legacy-deriveZones`**. *Grep this tag
   before trusting any 4D number — it is now the cheapest way to know which model you are reading.*
2. **`rates/4D_template.json` was NOT in `sw.js PRECACHE_ASSETS`** while `rates/sequence_rules.json`
   — which §I calls "a MIRROR, never executed" — was. Offline/cold-SW, `_load4DTemplate()`'s fetch
   fails, `_4dTemplate` stays null, dead path runs; `_4dTemplateTried` is one-shot so **one failed
   fetch drops the canonical model for the whole session.** Precached; `CACHE_VERSION` → v1090.
3. **`witness_gantt_edit_coherence.js:189` passed no `template:`** — the flagship editor witness,
   the one CLAUDE.md names first, measured all 10 claims on the DEAD model. Now builds its fixture
   from the template; new claim **G-COH-10** asserts from the shipped `§TPL_MODEL` line that the
   canonical model is what was judged.

**MEASURED (Duplex) — the two models genuinely differ under an edit:**

| | model | grid | move cascaded | result |
|---|---|---|---|---|
| before | `§AUTHOR_ZONES` | zones=21 edges=30 | **8** | 10 pass / 0 fail |
| after | `§AUTHOR_TPL` v=1.2.0 | tasks=20 edges=29 (withinLevel=16 acrossLevels=13) | **16** | 11 pass / 0 fail |

The canonical model's DECLARED edges propagate an edit to twice as many downstream tasks.
**RED CONTROL:** template moved aside → `model=legacy-deriveZones`, G-COH-10 FAILS (10/1, exit 1)
while the other ten still pass. That asymmetry is precisely why this was invisible for a session.

### ⛔ OPEN, found by this trace — do NOT re-discover
- **24 of 35 witnesses/probes that call `materializeZones` still pass no `template:`**, so they
  judge the dead model. Only `witness_gantt_edit_coherence` (this PR), the four template-specific
  tests, and 4 scripts are canonical. This is §K.2 item 2's audit with a concrete population — run
  the census with: `grep -rl materializeZones viewer/tests/*.js ./witness_*.js scripts/*.js` and
  check each for `template:`. Some may legitimately pin legacy behaviour; that is a judgement per
  file, which is why this PR did not convert them wholesale.
- **`witness_zone_cpm_duplex.js:40` hardcodes an absolute path to a DEAD scratchpad dir** from an
  old session, so it fails on unmodified `main`. Pre-existing, same bug class as the
  `38-offline-pwa.spec.js` `VIEWER_URL` item in CLAUDE.md.
- **`schedule_author_ui.js:288` reads `window._4dTemplate`**, published ONLY by `time_machine.js
  :4128` inside `_load4DTemplate()`. The author drawer is TM-owned (`time_machine.js:2912`) so the
  normal flow is safe, but the drawer opening while the template fetch is still in flight is an
  unguarded race that would silently author the dead model. Not fixed — needs a decision on whether
  the author UI should await the load or refuse.
- **`_writeTemplateSchedule`'s `displaySchedule` return (`:1144`, the `remapSolveToTasks` output)
  has NO production consumer.** The live movie re-derives its own element times via
  `_tmRescaleToTaskWindow` (`time_machine.js:4913`). Two implementations of one idea; only the DB
  task windows are shared. §B calls that rescale "PROJECT rewriting SOLVE's times" — it is still
  live on the element path, and §S8b measured it as manufacturing 100% of played floating.

## State, honestly
- ✅ **DAY 0 is at zero.** `§W_D0` **14 PASS / 0 FAIL / 2 INCONCLUSIVE** (`bim-ootb
  viewer/tests/witness_day0_integrity.js`). It refuses to print GREEN because two claims judged an
  empty population — both Hospital, both honest unknowns, not hidden failures.
- ✅ **0 of 718 `IfcColumn`** start before the slab/footing that bears them.
- ✅ **§K.4 FIXED 2026-08-27 (bim-ootb PR #1552)** — was: injected storey datum = mean wall
  CENTRE-z, 0.64–3.16 m above the floor, so `§STOREY_DATUM` assigned every element one level DOWN,
  uniformly (the witness passed anyway *because* the offset was uniform). Now mean wall BASE-z;
  fleet 22/22 storeys match base-z, Terminal max error 3.072 → 0.511 m. **The CODE is fixed but the
  SHIPPED DBs and baked patches still carry the old datum** — see §L item 1's two ⛔ sub-items
  before trusting any level-based number read off a shipped DB.
- ❓ **6,734 bearing-order violations / 106,027 pairs** — REPORTED, not judged. No rule exists
  saying what a correct sequence must satisfy beyond DAY 0 (§J.4). **Do not invent one to make the
  number mean something** — that is what produced §J.2's three retractions.

## Where the work is
`bim-ootb` branch **`feat/day0-unsupported-probe`** (pushed): `witness_day0_integrity.js`,
`cache_4d_run.js`, `knob_sweep.js`, `probe_day0_unsupported.js`, `probe_enclosure_geometry.js`,
`§STOREY_DATUM`, `§TPL_LADDER_BRIDGE`, 3 name overrides, `buildings/patches/Duplex_extracted.db.sql`.
`bim-compiler` **`fable/meshdb-livewire`**: this file, `CLAUDE.md` PRIMAL LAW + §I, `tools/extract.py`.

## Two habits this session paid for — keep them
1. **`node scripts/cache_4d_run.js`** runs the pipeline ONCE per building and persists
   `witness.log` + `run.json`. 119,568 elements read back in **0.43 s**. Never re-run
   `materializeZones` to answer a question a persisted run already answers, and **never wrap it to
   silence `console.log`** — the shipped `§`-log is the evidence (PRIMAL LAW clause 3).
2. **Re-read §E and §I before each new measurement**, not once at startup (§J.0). Three retractions
   in one session were all errors already listed in this file, which had been read that morning.
