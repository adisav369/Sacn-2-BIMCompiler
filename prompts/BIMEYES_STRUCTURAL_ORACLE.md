# BIMEyes — THE STRUCTURAL ORACLE (the judge, not the scheduler)

## ⚠ DO NOT REMOVE
**Scope:** build ONE judge that answers *"is anything holding this up, at the instant the user sees
it?"* and make it agree with the screen. **Read the output log after every run — exit code is not
evidence.** Honour this block until the lane is DONE. Spec lives here; findings append here, NOT to
`MEMORY.md`.

**Origin:** user, 2026-08-26, after weeks of `midair=0` while the screen showed hanging pipes.
Sibling lane: `prompts/4D_BAR_MODEL.md` §14–§18 (the *scheduler*). **This file owns the JUDGE.**
Do not re-litigate the Bar model here; do not fix the judge there.

---

# SETTLED — READ BEFORE PROPOSING ANYTHING

## S1. Four independent mechanisms produce `midair=0`. Only one dies with the rescale.
1. **The judge runs before the last transform.** `_midairAudit` at `_displayTimeline`;
   `_tmRescaleToTaskWindow` (`time_machine.js:4843`, applied `:4870`) after it. **0 → 783 on HHS.**
   Dies when `injectGantt` reads Bar leaf times and the rescale is deleted (4D_BAR_MODEL §14.6).
2. **`grounded` is footprint-local and altitude-blind.** `support_sweep.js:399-417` —
   `var lowest = Infinity` … `grounded[i] = (lowest < T.bz - GAP) ? 0 : 1`. **No XY neighbour ⇒
   grounded, at any altitude.** Absence of evidence is read as "rests on the earth."
3. **`des = -1` is silently uncountable.** `_midairAudit`'s first statement is
   `if (sIdx < 0) continue`. 63/6839 on HHS, 62 above ground, max 16.20m.
4. **The election picks ONE support of 36, and can pick one ABOVE.** `support_sweep.js:465`
   `if (poolJ >= 0) { bestJ = poolJ; bestCls = poolCls; }` — **no class guard**, so a pool member
   overhead beats a non-pool member underneath. Second, must-mirror copy at `cpm_schedule.js:159`.

## S2. The traced defect, end to end (Duplex `2O2Fr$t4X7Zf8NOew3FNhv`)
`IfcWallStandardCase`, Architecture, Level 1, seq=5, z[−0.20, 2.90] — a ground-floor wall on a
basement wall. 36 contacts, including six `IfcFlowSegment` pipes classified **bearing-BELOW**.

```
IfcWallStandardCase  seq=5  inPool=no    z[-1.25, 0.00]  bearing-BELOW   playedDay=0.50
IfcSlab              seq=4  inPool=YES   z[ 2.79, 3.10]  carrier-ABOVE   playedDay=0.00  ◄ elected
```

E1 is `addEdge(des[i], i, SS, 1)` — **one edge, start-to-start** (`cpm_schedule.js:194`; SS defined
at `:9` as "cannot start until S starts"). Slab starts day 0.00 → wall starts day 0.00; the walls it
stands on start day 0.25 and 0.50. `h=0 eyeFloating=true · h=3 true · h=6 false`. The judge asks only
`support.s > me.s + 1` **of its own election** → false on both timelines. Three hours of a wall in
mid-air, reported clean.

## S3. `supportPool` is not an arbitrary phase filter — this was tested and the obvious fix is WRONG
`schedule_gate.js:1312` — `seq <= 4 || (IfcSlab && seq>4) || IfcStairFlight`.
Replacing it with a structural-CLASS regex re-admits **438 HHS `IfcPlate` whose `element_name` is
`Systemelement:Verglasung:…` — glazing.** `seq` is the OUTPUT of a cited name-based classifier
(`rates.js:277` §4D_FACADE_ORDER, `id: glazed_curtainwall_facade`), which is why HHS splits
IfcPlate 191 structural / 438 glass and Terminal has 33,324 structural IfcPlate.
- `B\C = {}` on Duplex and HHS — the shipped pool is a strict SUBSET of any structural-class pool.
- Measured minimal alternative: **`E = shipped ∪ IfcWall*`** — HHS bearing 3332→3761 for +207
  contradictions, vs the class regex's 3825 for +325. 87% of the gain, zero glass.
- ⛔ **GATE: §S26.2's own justification figure (Duplex "761 vs 1") does not reproduce from shipped
  code.** Resolve or formally retract it BEFORE the pool moves. The contradiction rise (974→1181)
  cannot be scored against a baseline nobody can recompute.

## S4. The support fix breaks three witnesses — and that is diagnostic, not incidental
Branch `fix/support-pool-walls` (unmerged): HHS 783→355, Duplex 104→48, Hospital Bar midair 92→58 —
but Terminal bandInversions 0→74, Clinic drops off the CELL path, Duplex zone-display floating 22→62.
**Every downstream subsystem is calibrated against the wrong support relation.** You cannot converge
by improving a proxy while four consumers are fitted to its errors. **Do not chase Terminal's 74
until S5 is answered** — that tunes the app to guess-v2.

## S5. ⛔ THE ROOT CAUSE IS PROVENANCE, NOT PERCEPTION. START HERE.
Three layers of discarded truth, all measured 2026-08-26:
1. **Authored topology never read.** `tools/extract.py` reads `IfcRelAssociatesMaterial`,
   `IfcRelDefinesByType`, `IfcRelVoidsElement`/`FillsElement`, `IfcRelAggregates`, containment.
   It does **not** read `IfcRelConnectsElements` / `IfcRelConnectsPathElements`, and **never reads
   `Pset_*.LoadBearing`.** Prior art exists in-repo: `tools/mine_geomap.py:268`,
   `tools/rooms_from_topology.py:131`.
2. **What IS extracted does not ship.** `extract.py:293` creates and populates `rel_fills_host`
   (R21, `:657-689`). Across all eight fleet DBs: `rel_fills_host` **ABSENT ×8**, `rel_aggregates`
   **ABSENT ×8**, `rel_contained_in_space` present in 5 of 8. `ScheduleGate.hostPairs:151` then
   re-derives that same relation geometrically.
3. **Real geometry present, unused.** `component_geometries` holds triangle meshes (HHS 4,710 rows,
   `vertices`/`faces` BLOBs). Every support predicate reads `element_transforms.bbox_*` instead.
   An AABB cannot express contact, bearing direction, or enclosure.

**The renderer draws meshes. The judge computes over boxes. Two models, one screen.** That is the
blindness — plumbing, not epistemics.

## S6. The user's ENCLOSURE rule — measured, and it holds with one guard
*"Midair is all OK as long as it is within walls, floor slab and a roof."* Probe:
`bim-ootb scripts/probe_enclosure_rule.js` (PR #1545, base `44f42dd`). R=5m, K=2, guard ON:

| | day-0 photo | des=-1 blind | played-783 | whole movie | worst concurrent left |
|---|---|---|---|---|---|
| HHS 6,839 | **0/15 forgiven** | 0/62 | 509/783 | 90.1% | 63 (d19) |
| Terminal 48,428 | **0/23 forgiven** | 49/118 | 537/2355 | 60.9% | 645 (d79) |
| Duplex 1,119 | 5/22 | 4/26 | 10/104 | 53.6% | 15 (d6) |

- The photograph is forgiven **zero** times on HHS/Terminal at every R∈{2,5,10}, K∈{0,1,2,4}.
- `NOGUARD=1` reproduces the Duplex misfire (5→9, two of them walls). **Never forgive a load-bearing
  class** — a floating wall is visible.
- **The wall clause does almost nothing** (K=0 → 93.6% vs K=2 → 91.6% on HHS; identical on Duplex).
  The slab sandwich carries the rule. It is a TOLERANCE, not physics — it must gate `unforgiven`
  while `eyeFloating` stays the reported number.

---

# THE DESIGN — what to build

## D1. The correct midair test is GROUND-REACHABILITY, not a neighbour test
Over the elements visible at time *t*: root the contact graph at everything whose `bz ≈ ground
datum`, flood-fill through contacts, and anything unreached is floating. This is what the eye does.
It collapses `census()` / `midairAudit` / `auditFloating` into ONE definition; it dissolves #1435's
symmetric-vs-directional question (reachability is direction-free, and "I hang from a carrier" is
correct by construction — you are supported iff your carrier is); and `des = -1` and the
grounded-exemption stop being special cases, because they are simply unreachable.

## D2. The visual judge that is not a screenshot: raycast the scene graph
The 4D canvas already holds true geometry at time *t*. For each visible element, cast a ray down and
ask what it hits and when that thing appears. `Raycaster` against the BVH the viewer already builds —
numeric, reproducible, assertable. Enclosure (S6) becomes "does a ray up and a ray down both hit
something already placed." **Sample instants, never per frame** — the enclosure probe uses 42 daily
samples on HHS and that is the right shape.

## D3. One persisted support graph, computed once, read by all four consumers
Authored IFC relations where present; mesh contact where not. Persist as a table. This permanently
closes the divergence disease — 4D_BAR_MODEL §9.4 (re-derived judge), §14.3 (wrong stage), §17.2
(blind metric) are three costumes of *two derivations of one fact*.

---

# ⛔ ORDER OF WORK

1. **COVERAGE PROBE ON THE SOURCE IFCs — do this first, it is hours not weeks.**
   For every fleet IFC (locations: `MEMORY.md` → `reference_source_ifc_locations.md`), count
   `IfcRelConnectsElements` + `IfcRelConnectsPathElements` edges and the fraction of elements
   carrying `Pset_*.LoadBearing`. Three outcomes, all decisive:
   - **dense** → the pool question dissolves; `LoadBearing` replaces `seq<=4` with an extracted fact
     and the 761-vs-1 gate (S3) stops blocking.
   - **sparse** → hybrid, and the coverage number names which elements remain guesses.
   - **absent** → you have MEASURED that guessing is necessary; go to mesh-face contact with normals,
     where direction is free (a bearing surface points up, so a slab overhead can never be elected).
2. **Ship `rel_fills_host` + `rel_aggregates` into the viewer DBs** and make `hostPairs` read them
   instead of re-deriving. Smallest possible proof that provenance beats inference.
3. **Build the reachability judge (D1)** against `kernel_ops.start_ts`. Expect it red immediately —
   that is the point. Gate it, with the #1435 lock fixed in the SAME commit (the witness is already
   known not to track the judge: breaking the shipped judge 86,400,000× leaves
   `witness_midair_zero` at `pass=49 fail=0`).
4. **Then** the class guard on BOTH copies of the election (`support_sweep.js:465` +
   `cpm_schedule.js:159`, one commit) and the S3 pool decision.
5. **Then** Terminal's 74 band inversions — not before.

---

# §1 ASSESSMENT — Gemini's "Structural Sequence Oracle" proposal (2026-08-26)

**Right nail, wrong hammer, and written against a schema that does not exist.** Keep the framing;
do not implement it as specified.

## What it gets right
- Downward trace + a hard gate rather than a log line. Correct — `4D_BAR_MODEL.md` §12.5 lesson 7 is
  *"a logged number no invariant reads defends nothing."*
- Naming the check as a first-class rule rather than a helper.
- "Oracle" framing: the judge is a separate concern from the scheduler. That is why this file exists.

## Seven defects, each measured this session
1. **It uses the AABB again.** *"Trace its Axis-Aligned Bounding Box downwards."* That is the exact
   predicate that produced the hell — see S2, where six pipes are classified bearing-BELOW a wall.
2. **`bom.depth < target.depth` is the wrong ordering.** The 4D order is not BOM depth; it is the
   solve, then a per-task affine rescale. 783 HHS elements float ONLY on the played timeline (S1.1).
   The only honest clock is `kernel_ops.start_ts`.
3. **A 5mm scan under the bottom face flags every hanging element as a violation.** The traced duct
   elbow has 8 contacts, all carrier-above, and hangs legitimately. `carrier-above` is already a
   support class in this codebase.
4. **One-level check, not reachability.** Cannot see three mutually-supporting elements floating as a
   cluster, and cannot distinguish an orphan from a data gap. See D1.
5. **The schema is not there.** `elements_meta` in the shipped viewer DB is 8 columns — `guid,
   ifc_class, element_name, storey, discipline, material_name, material_rgba, building`. There is no
   `elements_rtree`, no `min_x/max_x/min_z`, no `product` column, and no `m_bom_line`. Every query in
   the proposal fails on the real fleet.
6. **`FATAL_COMPILATION_ERROR` is unusable on real IFCs.** 63/6839 HHS elements are structurally
   uncountable and 30.8% have no storey at all. A fatal gate refuses to compile every real building
   on day one. It must be a counted metric with a committed baseline, like everything else here.
7. **It solves detection; the defect is ELECTION and PROVENANCE.** Nothing in the proposal touches
   `support_sweep.js:465`, and nothing touches the fact that the building already told us what holds
   what and the extractor dropped it (S5).

## The one line worth keeping
*"See geometry through the lens of topological dependencies."* Correct — but the topology should be
**read from the IFC**, not re-inferred from boxes. That is step 1 of the order of work.

---

# INSTRUMENTS (do not rebuild these)
- `bim-ootb scripts/probe_floating_guid_audit.js` — replays the live chain to `kernel_ops`; judge
  REQUIRED from `support_sweep.js`, never re-derived. Produces §FGA_EYE_FLOATING / §FGA_JUDGE_BLIND /
  §FGA_TIMELINE_MISMATCH.
- `bim-ootb scripts/probe_enclosure_rule.js` — the S6 tolerance, fleet-measured. `TRACE=<guid>` dumps
  one element's full mechanics incl. `supportPool` membership per contact; that mode produced S2.
- Both on `probe/floating-guid-audit`, **PR #1545**, base `44f42dd`.

# ⛔ RESUME HERE
Nothing started. Step 1 (the coverage probe) is unclaimed and blocks the most.
