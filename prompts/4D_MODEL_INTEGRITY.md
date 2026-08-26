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
| **what level is it on?** | `schedule_gate.js:404` `collapsePhase(storey)` → `:338` `deriveBandRanks(...)` | ⚠ **this is the broken one — see §I.3.** Do not build on it without reading that section |
| **are two storey names one floor?** | `schedule_gate.js:382` `deriveStoreyMergeMap(spatialStructure)` | ⚠ **has never once run on any shipped building — §I.3** |
| **is this slab ground-bearing?** | `schedule_gate.js:201` `groundworkSlabs(els)`, one shared definition | reclassify slabs inline in a recipe |
| **is anything floating?** | `support_sweep.js:500` `_midairAudit(items)` (movie) · `schedule_gate.js:1122` `auditFloating(...)` (gate) | ⚠ these two disagree on the support top bound — §I.1 |
| **is an edit legal?** (🔓→🔒) | `verifyGanttIntegrity()` → `_midairAudit` | re-score with your own physics |
| **is it on screen at cursor?** | `time_machine.js:169` — `placed` = `start_ts <= cursor && end_ts <= cursor`; `frontier` = `start_ts <= cursor < end_ts` | invent a visibility rule. A probe using `s <= cursor` is counting placed **+** frontier |
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

## §I.4 HOW TO ADD A ROW
A relation belongs here the moment a **second** caller needs it. The shape to copy is
`bar_model.js attachContacts` — take the computed relation as a **parameter**; never recompute it
because the module boundary made it inconvenient to pass. Both §I.1 copies exist for that reason.
