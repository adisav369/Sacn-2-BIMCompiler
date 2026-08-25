# 4D BAR MODEL — SPEC

## ⚠ DO NOT REMOVE
**Scope:** replace the 4D scheduling chain with ONE model: a `Bar` base type, a composite tree, and a
single resource-constrained pass. **Read the output log after every run — exit code is not evidence.**
Honour this block until the lane is DONE. Spec lives here; findings append here, NOT to `MEMORY.md`.

**Origin:** user, 2026-08-25 — *"in Java thinking its use of interface inheritance classes model is
simple as Object"* · *"inherit a first Object a 'bar' defined as start/stop like any gant chart bar"*
· *"like Java class properties etc.. the OOP already sorted software design out"*.
Predecessor analysis: `4D_SCHEDULE_PERFECTION.md` §S68–§S71.

---

## §1 WHY — the four hells are one defect

`prompts/4D_SCHEDULE_PERFECTION.md` §S68 established that **the solver has no concept of a phase**
(`phaseTrade` is keyed by `collapsePhase(el.STOREY)` — a storey-name normaliser with a phase-sounding
name). §S71 established that midair, stacking, zero-minute and movie-vs-bars are **not four bugs**.
They are four symptoms of one structural defect:

> **Element time and task time are stored separately, in two modules, with no shared type — so every
> crossing needs a translator, and every translator is where a hell got in.**

The translators, all five of which this spec deletes:

| translator | direction | stores |
|---|---|---|
| `ScheduleGate.deriveZones` | elements → tasks | task times as an ENVELOPE |
| `ScheduleAuthor._writeTemplateSchedule` (§S69) | template → tasks | task times as a WINDOW |
| `ScheduleAuthor.remapSolveToTasks` (§S70) | tasks → elements | element times, to match the windows |
| `§DEQ_REPAIR` 16-sweep loop | elements → elements | repairs what the gates broke |
| `§CREW_CAP_FINAL` re-pack (§S67) | elements → elements | repairs what the repair broke |

OOP names the violations exactly: **encapsulation** (two stored copies of one fact), **Liskov** (no
common supertype, hence translators), **open/closed** (`placeNonst`'s
`Math.max(gg, wg, hangGate, openingGate, hostGate, tg, bg, slot.time)` — eight terms, edited by every
new rule), **single responsibility** (`supportPool` re-derived by hand instead of called — measured
cost: 4,706 Duplex edges instead of 716, and 52 midair instead of 0).

---

## §2 THE MODEL

```java
class Bar {                                  // the root Object
    long start, stop;
    long    duration()        { return stop - start; }
    boolean contains(Bar b)   { return b.start >= start && b.stop <= stop; }
    boolean overlaps(Bar b)   { return start < b.stop && b.start < stop; }
}

interface WorkBar extends Bar {
    long              work();                // seconds of labour
    Trade             trade();               // whose crews
    List<WorkBar>     needs();               // must finish first
    List<WorkBar>     children();            // empty = leaf
}
```

```
Bar
 └─ WorkBar
     ├─ ElementBar   leaf.       start/stop STORED.    children() = []
     └─ GroupBar     composite.  start/stop COMPUTED = min/max of children
         ├─ TaskBar     (phase × level)
         ├─ LevelBar
         └─ ProjectBar
```

### §2.1 THE ONE RULE
**Only leaves store time. Every group derives it.**

`GroupBar.start` is a **getter**, never a field. This is the whole spec in one line: with one stored
timeline there is nothing to reconcile, so no translator can exist, so no hell can enter through one.

Consequences that become **true by construction, not by test**:
- a bar contains its own elements — `contains()` is a tautology on a `GroupBar`
- the movie and the bars are the same numbers — there is only one set
- a phase bar cannot disagree with its contents

---

## §3 `needs()` — ONE LIST, MANY PROVIDERS

Every constraint becomes an edge. **No priority ordering, no `Math.max` line, no gate arithmetic.**

| provider | source | edge |
|---|---|---|
| `SupportNeeds` | geometry, filtered by **`ScheduleGate.supportPool`** (§S26.2 — call it, never re-derive) | bearing element → supported element |
| `HostNeeds` | `ScheduleGate.hostPairs` | host → hosted (§HOSTED_BEFORE_HOST) |
| `CarrierNeeds` | geometry | carrier above → hanging element |
| `OpeningNeeds` | existing `openingGate` predicate | wall/curtain-wall → opening |
| `WallNeeds` | existing `wallGate` predicate | wall → promoted roof slab (§4D_WALLS_BEFORE_ROOF) |
| `PhaseNeeds` | `sequence_rules.json` phase order | `TaskBar(P,L)` → `TaskBar(next P,L)` |
| `LadderNeeds` | policy | `TaskBar(P,L)` → `TaskBar(P,L+1)` (§4D_BAND_MONOTONIC) |

**Adding a rule = adding a provider. The scheduler never changes.** That is the open/closed fix.

### §3.1 EXTRACTION IS MANDATORY
Every provider LIFTS an existing shipped predicate. **Do not re-derive one.** Verified cost of
re-deriving: hand-written "support = anything below" gave Duplex 4,706 edges / 52 midair; calling
`supportPool()` gave 716 edges / **0 midair**. §S26.2 had already measured and rejected that exact
definition. Cite the source function in a comment beside every provider.

---

## §4 THE POLICY FILE — all that is left of `4D_template.json`

Audited 2026-08-25: v1.2.0 was **123 lines, 146 keys, 52 of them prose**, and its data keys were
copies — `phases[].sequence` and `phases[].trades` duplicated `sequence_rules.json`; the ten
dependency edges restated the phase list order and one flag; `scope` and `replicate_per_level` were
the same fact twice, with an **invariant written to check they agreed instead of deleting one**.

Everything else is EXTRACTED. What survives:

```yaml
days_per_week:  7
phase_link:     serial          # FS+0 between consecutive phases on a level
level_link:     self            # a phase waits for itself one level below
building_scope: [Substructure]  # everything else repeats per level
```

`phase order` ← min sequence per phase in `sequence_rules.json` · `trades` ← union of that phase's
classes' resources · `level` ← `deriveBandRanks` · `work` ← `_installSecs` · `crews` ←
`LABOR_RATES.max_crews` · `shift` ← `rates.js SHIFT_HOURS`.

---

## §5 THE SINGLE PASS

```
schedule(ProjectBar root):
  tasks   = topological order of TaskBars over PhaseNeeds + LadderNeeds
  for each task:
      gate = max(stop of its needs())
      elements = its children, topological over their own needs()
      for each element:
          start = max(gate, earliest stop among its placed supports, earliest free crew of its trade)
          stop  = start + work()/1 crew
      # groups need no pass — their span is their children's
```

### §5.1 CYCLES ARE DATA DEFECTS, NOT SCHEDULING PROBLEMS
A support edge pointing backwards against phase order is a **cycle**. **Report it by name; never
schedule around it.** Measured backwards-support today: Terminal 27, Hospital 38, HHS 35, Duplex 8 —
e.g. `Architecture IfcWall` holding up `Superstructure IfcMember`, `Finishes IfcCovering` holding up
`MEP Rough-in`. Those are wrong classifications. The graph must name its own bad data.
`§BAR_CYCLE` — one aggregated line per (predPhase, predClass → succPhase, succClass), with counts.

---

## §6 MEASURED BASELINE — prototype, real fleet, 2026-08-25

Prototype (`Bar`/`GroupBar` + one pass, `supportPool` edges only):

| | Duplex | HHS | Hospital | Terminal |
|---|---|---|---|---|
| **midair** | **0** | **9** | **10** | **16** |
| zero-minute | 0 | 0 | 0 | 0 |
| element outside its bar | **0** | **0** | **0** | **0** |
| phase stacking | 2/34 | 0/29 | 1/65 | 0/105 |
| crew breaches | 0 | 0 | 0 | 0 |
| span | 8.7d | 44.6d | 292.3d | 84.4d |
| runtime | 29ms | 53ms | 1.4s | 0.7s |

Shipping today, for comparison: midair 17 / 147 / 139 / 226 (raw solve); elements outside their bar
up to **81%** (§S70); phase stacking 18% / 34% / — / 17% (§S68).

**Prototype caveats that the build must close, not inherit:**
1. only ONE support predicate was used (bearing via `supportPool`). Four shipped gates — wall, hang,
   opening, host — are not yet providers. **Expect midair to move when they are added.**
2. Hospital reported **1,194 classification cycles** (1.9% of elements). Unexplained. Explain before
   trusting any Hospital number.
3. phase stacking is not exactly 0 because the building-scope Substructure bar spans every level and
   overlaps level tasks — a prototype artifact of putting it under one `LevelBar`. `TaskBar(scope=building)`
   must hang off `ProjectBar`, not a level.

---

## §7 WITNESS CONTRACT

Start from `witness_kit/contract.js`. One witness per LAYER, layer named in the header
(`WITNESS_INTERFACE_FRAMEWORK.md` §CRISIS LESSON 1). Every gate carries its own red control —
`feedback_extract_dont_author_then_gate.md`: **a gate that checks two of my own fields agree is a
deletion request, not a gate.**

| witness | gates |
|---|---|
| `witness_bar_composite.js` | `GroupBar.start/stop` are GETTERS (no stored field, verified in source) · `contains()` holds for every group/child pair · leaf times are the only stored times |
| `witness_bar_needs.js` | every provider LIFTS a shipped predicate (cite + call, never re-derive) · edge counts match the source predicate's own count · `supportPool` is called, not reimplemented |
| `witness_bar_schedule.js` | midair · zero-minute · phase stacking · crew caps · float exists — fleet, real DBs |
| `witness_bar_cycles.js` | every backwards edge is REPORTED, none silently scheduled around |

Fleet: Duplex · HHS_Office_Federated · Hospital · Terminal (the four with measured baselines above).
**Hospital and Terminal are mandatory** — §S69's PLUMBER breach and §S71's midair both needed a tall
or large building to appear at all.

---

## §8 DELETE LIST — the lane is not done until these are gone
`deriveZones` · `_writeTemplateSchedule` · `remapSolveToTasks` · `§DEQ_REPAIR` loop ·
`§CREW_CAP_FINAL` re-pack · `_midairAudit` · `4D_template.json`'s 119 non-policy lines.

Each exists only to keep two stored copies of time in agreement. Remove the second copy and they
have nothing to do. **A PR that adds the Bar model without removing these has not done the job** —
it has added a sixth translator.

---

## ⛔ RESUME HERE
Spec written 2026-08-25. Build not started. Next: §9 build log, appended below as work lands.

---

## §9 BUILD LOG

### §9.1 CORE — DONE (bim-ootb PR #1537, 2026-08-25)
`viewer/bar_model.js` + `viewer/rates/4D_policy.json` + `witness_bar_composite.js`. Additive only,
nothing wired, no existing file touched.

`§WITNESS_BAR_COMPOSITE pass=12 fail=0 ran=119748` over all four mandatory buildings:

| | Duplex | HHS | Hospital | Terminal |
|---|---|---|---|---|
| elements | 1,119 | 6,839 | 63,182 | 48,428 |
| tasks / levels | 18 / 4 | 17 / 4 | 35 / 8 | 72 / 22 |
| span | 8.7d | 44.4d | 287.1d | 84.1d |

**`group-contains-every-child`: 119,748 / 119,748.** That is the number that was 54.5% Hospital /
35.4% Terminal / 18.8% Duplex under the pre-Bar code (§S70). It is now a **tautology, not a
measurement** — a group's span IS min/max of its children, so the check cannot fail without someone
reintroducing a stored group time, which `group-time-is-a-getter` catches in SOURCE.

Gates carry **4 per-gate red controls** plus the contract's own, per
`feedback_extract_dont_author_then_gate.md` — a gate added without proof it can fail is decoration.

Two §6 prototype caveats CLOSED here rather than inherited:
- a building-scope `TaskBar` hangs off `ProjectBar`, never a `LevelBar` (was 2/34 false stacking pairs)
- building-scope tasks collect elements from EVERY level (dropping upper ones lost 1 of Hospital's
  63,182, invisible in every other number)

Suite after: `green=60 new_red=1 known_red=7 total=68` — the one red is the long-standing unrelated
`witness_cpe_buildup_require_tm_first.js`, baselined in §S67.

### §9.2 NEEDS PROVIDERS — in flight
`viewer/bar_needs.js` (support · host · carrier · opening · wall), dispatched to a Sonnet agent with
§3.1's extraction rule as the hard constraint. Core takes `needs()` by injection, so the two compose
without touching the same files.

### §9.3 NOT STARTED
`witness_bar_schedule.js` (the fleet hell measurements — needs §9.2) · `witness_bar_cycles.js` ·
wiring · **§8 DELETE LIST**. Until §8 is done this lane has added a sixth translator, not removed five.

### §9.2 NEEDS PROVIDERS — DONE, plus two integration findings the providers alone could not show
`viewer/bar_needs.js` (bim-ootb PR #1538, Sonnet agent) — `support · host · carrier · opening · wall`,
every provider lifted from a shipped predicate by balanced-brace slicing or by calling the exported
function. `§WITNESS_BAR_NEEDS pass=15 fail=0 ran=964091`; its anti-re-derivation gate matches the
real `computeSchedule`'s own `§GEO_ORDER edges=` count on all four buildings.
Agent-reported caveat, verified: `wall=0` on all four because `_buildScheduleElements` never runs
`_promoteRoofLoadPath()`, so `isPromotedSlab()` never fires on this element source —
`materializeZones` has the identical gap. Lift verified against a synthetic fixture.

**Integration then exposed two defects neither the core nor the providers could show alone.**

**(a) `needs()` is ANY-OF and ALL-OF, not one flat list** (PR #1540). An element's edges do not all
mean the same thing:
- `support`/`bearing` — **any-of**. Something must be under me. A slab on twelve columns starts when
  the first few are up; requiring all twelve serialises the frame and is not how anything is built.
- `host`, `carrier`, `opening`, `wall` — **all-of**. There is one host and you need it.

Flattened into a single `min()`: **HHS midair 609** — the soft support edge finished early and
satisfied the gate, releasing hosted and hanging elements before their host.

**(b) The scheduler must gate on the relation the JUDGE measures** (PR #1539). The provider emitted
geoGate's `below` — anything overlapping underneath, contact or not — while
`witness_midair_zero.js` `census()` tests **bearing contact** (`S.top_z >= E.base_z - GAP`).
Different sets, so the two could never agree: the any-of `min()` released an element on a distant
slab far below it while its real bearing neighbour was unbuilt. Provider now tags `bearing` vs
`support`; the model prefers bearing when present. Same edges, one more bit of information.

**INTEGRATED RESULT — `bar_model` + `bar_needs`, four buildings, real DBs:**

| building | midair | zero-min | outside-bar | stacking | crew | span | ms |
|---|---|---|---|---|---|---|---|
| Duplex | **0** | 0 | 0 | 0/29 | 0 | 8.8d | 5 |
| HHS_Office_Federated | **25** | 0 | 0 | 0/29 | 0 | 46.7d | 17 |
| Hospital | **10** | 0 | 0 | 0/60 | 0 | 294.7d | 105 |
| Terminal | **16** | 0 | 0 | 0/100 | 0 | 84.2d | 62 |

Shipping today: midair 17 / 147 / 139 / 226 (raw solve) · elements outside their own bar up to
**81%** (§S70) · phase stacking 18% / 34% / — / 17% (§S68).

**⛔ OPEN, reported not hidden: cycles are high — 329 / 2,716 / 9,911 / 3,668.** Hospital's 9,911 is
**15.7% of its elements**, force-placed because a hard need was unplaceable inside their task. This
is the §6 caveat 2 that the spec said must be explained, and it is bigger now that host/carrier/
opening are edges. **No Hospital number is trustworthy until it is explained.** Next task in this lane.

### §9.4 ⛔ RETRACTION — the integration probe's midair numbers were WRONG (2026-08-25)

**User: "Again without visual check, how sure are the WITNESS logging?" — asked twice. The second
asking caught a real error.**

`scratchpad/integrate.js` reported midair **0 / 4 / 0 / 0** (Duplex/HHS/Hospital/Terminal). Re-judged
with `census()` **sliced verbatim from `witness_midair_zero.js`** — the project's own judge, the one
the fleet baselines were set with:

| building | shipping (raw solve) | **Bar model, real judge** | probe claimed |
|---|---|---|---|
| Duplex | 17 | **18** | 0 |
| HHS_Office_Federated | 147 | **129** | 4 |
| Hospital | 139 | **124** | 0 |
| **Terminal** | 226 | **697** | 0 |

**Terminal is 3× worse than what ships. The Bar model is NOT yet better than the current engine.**
Every midair claim in §9.2 and §9.3 from the integration probe is withdrawn.

**Cause — the same mirrored-predicate error twice in one session.** `census()` counts a contact as
**bearing OR carrier OR embedded**, over **every** element. The probe's inline judge tested only
`supportPool`-filtered **bearing** — the exact relation the scheduler gates on. A judge built from
the scheduler's own predicate cannot contradict the scheduler. `probe_4d_midair_under_template.js`
(§S70) sliced the real `census()` and was right; when `integrate.js` was written the judge was
re-derived by hand instead.

This is `feedback_extract_dont_author_then_gate.md` in its other form: **re-deriving a predicate you
could have called is how a wrong result gets certified.** It cost 4,706-vs-716 support edges earlier
in the same session, and it cost this.

**Standing rule for this lane, from here:** the midair judge is `census()`, sliced from
`witness_midair_zero.js`, never reimplemented. Any probe or witness in this lane that measures
floating and does not slice it is invalid on its face.

**What survives:** `witness_bar_composite.js` (119,748 pairs) — it tests tree arithmetic, which is
self-contained and needs no external judge. The composite rule (only leaves store time) holds.

**⛔ RESUME: Terminal 697.** Find why the Bar model triples Terminal's midair while improving HHS and
Hospital. Worst cases are `IfcPipeSegment`/MEP Rough-in hanging 41d and `IfcValve`/MEP Rough-in 24d —
carrier relations, which the model treats as ALL-OF hard needs and which `census()` counts but the
probe's judge did not. Suspect the `ceiling_link` upward edge interacting with Terminal's 22 levels.

### §9.5 THE DESIGN LAW — midair and phase discipline are in direct tension (2026-08-25)

**User: "Explore this design property issue. See what we can learn."** Held the graph, the crews and
the judge (`census()`, sliced) fixed; varied ONLY how elements are cut into bars.

| building (raw solve) | no partition | **phase only** | level only | phase × level |
|---|---|---|---|---|
| Duplex (17) | **0** | 17 | 47 | 14 |
| HHS_Office_Federated (147) | **1** | **43** | 899 | 86 |
| Hospital (139) | **1** | **22** | 2,885 | 228 |
| Terminal (226) | **0** | **0** | 1,558 | 413 |

Spans move the same way: unpartitioned 8/35/277/88d, phase×level 11/55/395/132d.

#### 1. The graph was never the problem
**Unpartitioned midair is 0/1/1/0 on the real judge.** One graph + the judge's own contact relation
+ crew caps schedules 63,182 Hospital elements with ONE floater. Every hell this lane chased came
from a layer above the graph.

#### 2. But unpartitioned has NO phase discipline at all
Derived phase bars from that same run: **every phase starts on day 0 and 15/15 phase pairs overlap.**
Terminal — Superstructure 0-88d, Architecture 0-52d, MEP Rough-in 0-88d, MEP Final 0-88d,
simultaneously. **That is hell #1, the overstacked Gantt, in its purest form.**

#### 3. A priority cannot fix that — measured, not assumed
Tried phase order as an RCPSP-style priority rule on the ready set instead of a barrier
(§BAR_PHASE_PRIORITY). **No effect: still 15/15 overlapping, still all day 0.** The reason is
resources, not ordering — **different trades have INDEPENDENT crew pools**, so a ground-floor pipe
crew is free to start on day 1 beside the frame crew, and no geometric constraint forbids it.
Priority orders the ready set; it cannot stop parallel trades starting in parallel.

**Therefore a phase TIME BARRIER is necessary. Discipline cannot emerge from physics.**

#### 4. The barrier's cost scales with how badly the partition cuts VERTICAL contact chains
Contacts are overwhelmingly vertical — a column meets the slab above, a wall meets the slab below.
- **A level boundary cuts straight across them** → catastrophic (Hospital 2,885, Terminal 1,558).
- **A phase boundary runs WITH gravity** (structure → envelope → services already respects it) → cheap.
- `contactsCutByBars` does NOT predict the damage (Duplex phase×level cuts 89.1% for 14 midair;
  Hospital level-only cuts 74.2% for 2,885). It is not how many are cut, it is WHICH.

#### 5. THE LAW
> **Midair and phase discipline trade against each other, and the exchange rate is set by how much
> the partition cuts across gravity. Partition along gravity (phases) and it is nearly free.
> Partition across it (levels) and it is ruinous.**

This is why every fix in this lane's history re-broke the other side, and why §S68's diagnosis
("the solver has no phase in it") was correct but incomplete: adding the phase necessarily costs
midair. The question was never whether to pay, only where.

#### 6. WHAT TO BUILD — partition by PHASE, derive levels
**Schedule on 6 phase bars.** Measured against the shipping engine:

| | shipping | phase-bar model |
|---|---|---|
| Duplex | 17 | 17 |
| HHS | 147 | **43** |
| Hospital | 139 | **22** |
| Terminal | 226 | **0** |

zero-minute 0 · elements outside their bar 0 · crew breaches 0 · phase order perfect.
**Levels become DERIVED children of the phase bar** — 19/17/36/73 reporting sub-bars, spans computed
by the composite, never gated. Free, because a GroupBar's span already IS its children's.

This also reconciles with LBMS: locations stay a reporting and production dimension, but they are
not a scheduling barrier — which is exactly why LBMS defines locations deliberately rather than
inheriting storey strings.

**⛔ RESUME: rebuild the tree as Project > Phase > Level > Element** (level as a derived group, not a
task), re-run the full fleet on `census()`, and gate it in `witness_bar_schedule.js`. Spans grow
against phase×level (Hospital 521d vs 395d) — price that before shipping.

### §9.6 ⛔ §9.5's RECOMMENDATION IS WITHDRAWN — level bars are load-bearing (2026-08-25)

**User: "Will this break our 4D template principle?"** Yes. Measured before acting, and it reverses
§9.6's predecessor.

§9.5 recommended partitioning by PHASE ONLY because it minimised midair. That optimised the one
number being watched and destroyed the one that was not. **§4D_BAND_MONOTONIC** — *"a trade may not
run ahead of ITSELF on the floor below"*, from the user's own report *"upper floors gets walled
first"* — counted as elements on storey rank r+1 starting before that trade's last element on rank r
finishes:

| building | shipping (raw solve) | **phase × level** | phase only |
|---|---|---|---|
| Duplex | 64 | **1** | 118 |
| HHS_Office_Federated | 654 | **1** | 3,857 |
| Hospital | 29,013 | **7** | **48,589** |
| Terminal | 30,318 | **14** | 29,993 |

**Phase-only is worse than the shipping engine**, by 48,589 on Hospital. `level_link: self` holds
inversions at 7 instead of 48,589 — **four orders of magnitude**. It is not a decorative policy line
and it cannot be dropped.

#### The trade is THREE-WAY, not two
| configuration | midair | band inversions |
|---|---|---|
| shipping | 17 / 147 / 139 / 226 | 64 / 654 / 29,013 / 30,318 |
| **phase × level** | 14 / 86 / 228 / 413 | **1 / 1 / 7 / 14** |
| phase only | **0 / 43 / 22 / 0** | 118 / 3,857 / 48,589 / 29,993 |
| no partition | **0 / 1 / 1 / 0** | (all phases day 0 — hell #1) |

**§9.5's LAW STANDS, its CONCLUSION DOES NOT.** Midair and discipline do trade against each other at
an exchange rate set by how the partition cuts gravity — but "discipline" is TWO constraints, not
one: phase order AND band monotonicity. Level bars cost midair and buy band monotonicity. §9.5
priced only half the purchase.

#### VERDICT — the 4D policy is VALIDATED, not broken
All five lines earn their place, each by a measured margin:

| policy line | what it buys | measured without it |
|---|---|---|
| `phase_link: serial` | phase order | every phase starts day 0, 15/15 pairs overlap |
| `level_link: self` | band monotonicity | 7 → **48,589** inversions (Hospital) |
| `ceiling_link: frame_above` | fit-out after the slab it hangs from | 9,911 → 1,765 premature placements |
| `building_scope` | Substructure once, elements not lost | 1 of Hospital's 63,182 silently dropped |
| `days_per_week` | the calendar | — |

**Keep `phase × level`.** It beats shipping on band inversions by four orders of magnitude and on
midair for the two smaller buildings; it is worse on midair for Hospital (228 vs 139) and Terminal
(413 vs 226). **That midair gap is the remaining work** — and §9.5 proved it is not the graph's
fault, since the same graph unpartitioned scores 0/1/1/0. It is the level-boundary cost, and the
target is to pay less of it without giving up the ladder.

**⛔ RESUME:** close the Hospital/Terminal midair gap WITHOUT weakening `level_link`. First idea to
test, not yet tried: the ladder currently chains whole level TASKS; chain the TRADE across levels
instead (the actual §4D_BAND_MONOTONIC wording is per-trade, not per-task), which may hold
inversions near zero while leaving elements freer to follow their contacts.

---

# §10 — WITNESS ACCURACY IS THE WHOLE JOB NOW. START A NEW SESSION HERE.

**User standing constraint, stated three times across 2026-08-25: there is NO visual check. The
`§` logs are the only channel. A number that is wrong is worse than no number, because it gets
believed.** This session produced one full retraction (§9.4) and two near-misses. Every one came
from the witness, not the code.

## §10.1 THE FIVE RULES THIS SESSION PAID FOR

**1. The judge is SLICED, never re-derived.**
`census()` comes out of `witness_midair_zero.js` by balanced-brace slice. §9.4: a hand-written inline
judge testing only `supportPool`-filtered BEARING reported midair `0/4/0/0`; the real judge — which
accepts bearing OR carrier OR embedded over EVERY element — reported `18/129/124/697`. **A judge
built from the scheduler's own predicate cannot contradict the scheduler.** Same error, same day,
cost `4,706` support edges instead of `716` (§S26.2's already-rejected definition, re-derived by hand).
→ *Any probe or witness in this lane that measures floating and does not slice `census()` is invalid
on its face.*

**2. Every gate carries its own red control, committed.**
The contract allows ONE `.redControl()`, which proves the WITNESS can fail — not that each GATE can.
`witness_4d_template.js` carries 10 per-gate controls, `witness_bar_composite.js` 4,
`witness_bar_schedule.js` 3. A gate verified once in a throwaway console is not verified.

**3. A gate that checks two of your own fields agree is a DELETION REQUEST.**
`4D_template.json` v1.2.0 had `scope` and `replicate_per_level` saying the same thing, with an
invariant written to check they matched. 123 lines / 146 keys, of which 4 were genuinely authored.
See `feedback_extract_dont_author_then_gate.md`.

**4. A fleet witness must key on the BUILDING as well.**
`witness_4d_movie_binds_bars.js` failed on its first run: task ids are phase+storey derived, so
`TASK_SUPERSTRUCTURE_LEVEL_1` exists in Hospital, HHS *and* Duplex. Grouping on `taskId` alone pooled
three buildings into one "task" and manufactured order inversions that did not exist.

**5. A crash is not a red, and a red is not a regression.**
A `/tmp/wt-*` worktree has NO `node_modules` — 5 witnesses died `Cannot find module 'sql.js'` and the
runner reported them `new_red`. `ln -s /home/red1/bim-ootb/node_modules <wt>/node_modules` first.
And BASELINE every `new_red` by stashing your own diff and re-running before calling it yours:
`witness_cpe_buildup_require_tm_first.js` has been byte-identically red all session and is nobody's.

## §10.2 WHERE THE NUMBERS STAND — judged by the sliced `census()`

| | shipping | Bar model | gated by |
|---|---|---|---|
| midair | 17 / 147 / 139 / 226 | **12 / 70 / 92 / 336** | `witness_bar_schedule` |
| band inversions | 64 / 654 / 29,013 / 30,318 | **1 / 0 / 4 / 4** | same |
| phase stacking | 18% / 34% / — / 17% | **0 everywhere** | same |
| zero-minute · outside-bar · crew | — | **0 / 0 / 0** | same + `witness_bar_composite` |

Order is Duplex / HHS_Office_Federated / Hospital / Terminal. **All four are mandatory** — §S69's
PLUMBER breach needed 8 levels to appear, §S71's midair needed Terminal.

PRs: **#1537** core (MERGED) · **#1538** providers · **#1539** bearing/below split · **#1542** this
lane's semantics, upward edge, geometry levels, trade ladder, granularity dial.

## §10.3 ⛔ RESUME — HARDEN THE WITNESS UNTIL THE LOGS CAN BE TRUSTED ALONE

In order. Do not wire anything live until 1–3 are done.

1. **Kill the scratchpad probes.** Every number quoted in §9 came from `/tmp` scripts that no suite
   runs and nothing gates. `integrate.js`, `judge.js`, `granularity.js`, `band.js`, `coarse.js`,
   `gap.js`, `labels.js`, `terminal.js`, `outside.js`, `why.js`, `phaseonly.js` are all gone with the
   session. **Fold what matters into `witness_bar_schedule.js` or lose the ability to detect a
   regression at all.** This is the single highest-value task in the lane.

2. **Audit every existing witness in this lane against §10.1 rule 1.** Grep for any predicate that
   duplicates a shipped one instead of calling or slicing it. That is the error that produced the
   only retraction so far, and it produced it twice in one day.

3. **`witness_bar_needs.js` is the agent's, and its anti-re-derivation gate compares against
   `computeSchedule`'s own `§GEO_ORDER edges=` count — verify that claim directly** rather than
   trusting the agent's report. §10.1 rule 1 applies to subagent output too.

4. **Terminal midair 336 vs a shipping 226** — the only axis where the model still loses.
   §9.5 proved it is the level-boundary cost and not the graph (same graph unpartitioned: `0/1/1/0`).
   *Was 513 until `correctLevelsByGeometry` was deleted 2026-08-26 (§10.6).*

5. **⚠ CORRECTED 2026-08-26 — see `4D_SCHEDULE_PERFECTION.md` §S72.2. Everything the earlier version
   of this item said was wrong.** The mechanism is NOT missing and needs NO source IFC and NO
   extractor change. `viewer/lib/room_walker.js` `writeRooms()` already injects storey rows from the
   DB alone — `STC_` guid, `object_type='COMPILED'`, `center_z` = mean wall centre-Z
   (`room_walker.js:1191`), idempotent, version-stamped via `rooms_meta`. **It has already run on
   Terminal: all six of Terminal's storey rows are its output — Terminal has ZERO real extracted
   storeys.** §S18 `deriveStoreyMergeMap` DOES run; it merges 0 because the injector emits a storey
   row **only where it compiled a room** (`room_walker.js:1352`), so 17 of Terminal's 23
   `elements_meta.storey` labels get none. **The gap is that one line**, in the room lane's file, not
   in extraction and not in the scheduler. Predicted payoff (SIMULATED, §S72.1): Terminal midair
   **336 → 48**, span 126 → 105d — a simulation of 6 bands, NOT a measurement of the fix.

6. **§8 DELETE LIST — still untouched.** `deriveZones` · `_writeTemplateSchedule` ·
   `remapSolveToTasks` · `§DEQ_REPAIR` · `§CREW_CAP_FINAL` · `_midairAudit`. **Until these go, this
   lane has ADDED a sixth translator, not removed five**, and §1's whole argument is unspent.

7. **Nothing is wired.** No call site passes the Bar model. P6/XML interop and the editable bars are
   untouched and verified green (`witness_tm_p6_interop_fold` 42/42).

## §10.4 THE ONE THING NOT TO REPEAT
Do not report a number to the user without knowing which judge produced it and whether that judge
shares a predicate with the thing it is judging. Every wrong claim this session passed a green gate.

## §10.5 ⚠ A SQUASH-MERGE ORPHANED A FIX. CHECK FOR THIS BEFORE TRUSTING ANY "MERGED" PR.
**PR #1539 (the bearing/below split) never reached `main`.** It was based on `feat/bar-needs`; that
branch was squash-merged into main as #1538, so #1539 then merged into a branch main had already
absorbed. `gh pr view 1539` says **MERGED**. It was not, where it counts.
**Verified by reading main directly:** `origin/main viewer/bar_needs.js:190` still carried
`addEdge(S.guid, E.guid, 'support')` — the pre-split form. Restored on this branch.

CLAUDE.md already records this hazard verbatim — *"a squash-merge + a late push ORPHANS the new
commit … after a branch is squash-merged, start the follow-up off fresh `origin/main` — never re-use
it"* — and this session stacked on the feature branch anyway.

**Rule for this lane: a PR's MERGED status is not evidence its content is in `main`. Grep main for
the actual line.** Same discipline as §10.1 rule 1: read the source of truth, do not trust a report.


## §10.6 CONSOLIDATION — what is TRUE as of 2026-08-26, after the retractions

This file records its own corrections in place. Reading it linearly will hand you withdrawn claims,
so this section is the settled state. **Where §9 and §10.6 disagree, §10.6 wins.**

### Numbers, judged by `census()` sliced from `witness_midair_zero.js`
| | Duplex | HHS | Hospital | Terminal |
|---|---|---|---|---|
| shipping midair | 17 | 147 | 139 | 226 |
| **Bar model midair** | **12** | **70** | **92** | **336** |
| shipping band inversions | 64 | 654 | 29,013 | 30,318 |
| **Bar model band inversions** | **1** | **0** | **4** | **4** |
| phase stacking · zero-min · outside-bar · crew | **0** | **0** | **0** | **0** |

`witness_bar_schedule` 13/13 · `witness_bar_composite` 12/12 · `witness_bar_needs` 15/15 ·
suite `green=62 new_red=1 known_red=7` (the red is the long-standing unrelated
`witness_cpe_buildup_require_tm_first`).

### WITHDRAWN — do not act on these, they are still in the text above
- **§9.5's recommendation** (partition by phase only) — withdrawn by §9.6. Phase-only is WORSE than
  shipping on band inversions: Hospital 48,589 vs 4. §9.5's *law* stands; its conclusion does not.
- **§9.4's superseded numbers** — the integration probe's `0/4/0/0` midair. Real judge: `18/129/124/697`.
- **§10.3 item 5's original text** — "elevation VERIFIED ABSENT / fix the extractor". Wrong on both.
- **`correctLevelsByGeometry`** — added and DELETED the same session. It was a symptom patch for the
  storey gap: 1,986 fires on Terminal, 0 once levels are right. Removing it is free on three
  buildings and takes Terminal 513 → 336.

### The three open items, in order
1. **`room_walker.js:1352`** — emit a `COMPILED` storey row for every distinct `elements_meta.storey`,
   not only room-bearing ones; widen `stZ` to match. Room lane's file. Predicted Terminal 336 → 48.
2. **Fold the scratchpad probes into `witness_bar_schedule`** (§10.3 item 1) — every §9 number came
   from `/tmp` scripts that no longer exist. Without this there is no regression detection.
3. **§8 DELETE LIST** — untouched. Until it is done this lane has ADDED a translator, not removed five.

### Still true and unchanged
The five policy lines each earn their place by a measured margin (§9.6). The design law (§9.5) holds:
midair and discipline trade at an exchange rate set by how the partition cuts across gravity. The
composite rule (§2.1, only leaves store time) holds — 119,565 rows, gated.
