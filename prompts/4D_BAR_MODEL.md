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
