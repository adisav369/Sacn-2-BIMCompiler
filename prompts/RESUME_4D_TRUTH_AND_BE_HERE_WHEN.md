# ⚠ DO NOT REMOVE — Scope & Working Rules
**Scope:** TWO tasks, in this order, and nothing else:
1. **T1b + §4D_HOST_BEFORE_HOSTED** — make the 4D build order TRUE.
2. **§CPE_BE_HERE_WHEN** — pin a point on the camera path to a moment in that timeline.
**The order is a dependency, not a preference.** Task 2 solves pacing so the camera arrives when
something is being built. If the timeline still installs a window before its wall, task 2 aims the
camera at a moment that is wrong — precisely and confidently wrong. Do not start task 2 until task 1's
gate is green. If you can only finish one, finish task 1.

**Read the log after every run.** Proof on this project is `§`-tagged console output and NUMBERS.
For anything continuous (camera position, tilt, rate, arrival time) it is the numbers computed from
real object state, never a screenshot and never "the film looks right" — CLAUDE.md's FUNDAMENTAL LAW.
**Spec before code, including test code.** Every witness must name the issue it proves or disproves,
and must be able to show the RED. **Answer the ⛔ blocking questions before building.**
Honour this block until this file is DONE.

---

## Task 1 — T1b + §4D_HOST_BEFORE_HOSTED: make the build order true
**Read first:** `prompts/GANTT_ACCURACY.md` §4D_HOST_BEFORE_HOSTED (the whole section, including the
struck-through mechanism — it shows what was already ruled out) and `prompts/4D_CAPTURE_AND_FALLBACK.md`
§2.1 + §5.2 (T1b) + line ~359.

**The finding, from a user watching a baked Hospital film 2026-07-29:** a window revealed before its
supporting wall. It reads like a scheduler bug and is not one.

**The reframe that matters — do not skip it.** Hospital's captured IFC programme carries **deps +
element links 46/46**, early/late 45/46, float 44/46, is_critical 45/46 — and **our schema discards all
of it**. So `viewer/schedule_gate.js` is heuristically re-deriving an order a planner already stated.
**This is the fallback running on a building that did not need a fallback.**

**Two tracks, in this order:**
- **1a — T1b (the higher value, already specced, never built).** Widen the schema to capture deps +
  early/late + float + is_critical, and let a captured programme's own dependencies drive the order.
  ⚠ **Boundary, unchanged:** capture and replay CPM, **never recompute float**. Reading a planner's
  stated dependencies is not us solving CPM and must not become that.
- **1b — the hosting gate, FALLBACK ONLY.** For buildings with no programme, add HOST-BEFORE-HOSTED as
  a third constraint beside `schedule_gate.js`'s two passes. ⚠ **Strict Z cannot express this** — a wall
  CONTAINS its window (0.0→3.0 vs 0.9→2.1 inside it); containment is not ordinal. The existing Z
  stacking matrix (PASS A/B, ε=0.05m) is correct and must not be replaced to fix a relation it was never
  meant to carry. ⚠ **Check the cheap cause FIRST:** trade `seq` ordering in PASS B may be the whole
  defect. ⚠ The host link must be EXTRACTED (IFC relationship or measured containment), never inferred
  from proximity — that is invention, forbidden by the Prime Directive.

**Gates:** W-HOST-ORDER (`start_ts(host) <= start_ts(hosted)` for every hosted element on Hospital;
report the violation count BEFORE and after — before must be > 0 or you never reproduced it),
W-HOST-NO-REGRESSION (bottom-up character survives; `§GANTT_MINI` phase spans do not collapse),
W-HOST-COVERAGE (how many elements have a derivable host; the rest keep their order and are COUNTED).

**The demonstration is already set up, and it is not the proof.** The user still holds the authored
Hospital path. Re-bake that exact path and compare against `BIM_MaxQ_Hospital_1785273910881.mp4`
(1852×960, 1186 frames, 79.067 s — user-accepted reference). Same camera, same duration, one variable.
Correct order puts the façade closing over the exposed services **in front of the lens** instead of
before it. ⚠ Re-bake from the SAVED path — confirm `§CPE_OPEN src=authored` and unchanged band/hose
counts first, or the comparison is worthless. The films demonstrate; **W-HOST-ORDER proves.**

**Honesty tiers move with this:** captured deps → tier 1 (*linked schedule*); the geometric gate stays
tier 2 (*this model's derived 4D*). Never "a construction programme" for tier 2. See
`prompts/CINEMA_PATH_EDITOR.md` §5.

---

## Task 2 — §CPE_BE_HERE_WHEN: be HERE when THAT is being built
**Read first:** `prompts/CINEMA_PATH_EDITOR.md` §CPE_BE_HERE_WHEN (the full section), plus §CPE_WHEN_HERE
(item 7 in `prompts/CINEMA_DELIGHT_BATCH.md` — the READ direction this inverts) and §CPE_SPEED_RAMP.

**The origin:** the user's published demo caught the façade panelling going on and a glimpse of inner-room
services *by accident* — two clocks coincided. Drag one band and it is gone. Task 2 makes that
authorable: pin a path point to a timeline moment and let PACING solve for it.

**Possible only since 2026-07-29:** §CPE_BUILDUP_FOLLOW_TM made the target stand still. Before it, the
reveal was re-keyed to the camera path, so "when is this built" moved with every band drag.

⚠ **Carry §CPE_SPEED_RAMP's law:** fold the constraint **INTO the cost integrand, never as a multiplier
after it**, or §CPE_EVEN_TURN's turn-per-frame bound breaks and the jerk returns. That lane cost several
sessions and three dead ends — do not re-learn it. **The peak-deg/frame witness is the gate.**
⚠ **The honest limit is a REQUIREMENT, not a caveat:** where the schedule lacks that ordering there is no
window to arrive in, and the feature must report *"no such window on this building"*. **It must never
nudge the schedule** — that would be the camera authoring the build order again, which the user
explicitly ruled out on 2026-07-29 (*"do not bake anything for TM.. it is user's own plan"*).

**Witness sketch:** `§CPE_BE_HERE_WHEN target=<guid|room|phase> window=<iso>..<iso> cameraT=<f>
cursorAt=<iso> offsetDays=<n> hit=1|0 peakTurnDeg=<n> (bound=<n>)` — plus the unchanged jerk gate.
Report failure to hit the window as a NUMBER, never by silently landing nearby.

---

## ⛔ Blocking questions — ANSWERED 2026-07-29, before any build
1. **Task 1a — captured vs fallback:** **captured always wins.** When a building has both, the geometric
   fallback does not run on it at all; no divergence display. Matches the tier-1/tier-2 honesty split
   already in `CINEMA_PATH_EDITOR.md` §5 — keep it that simple.
2. **Task 2 — target grain:** **phase.** `_sfxPhases` already derives phase changes per tick — cheapest,
   and the target is "be here when THIS PHASE is building," not one specific element. Room/element grain
   not pursued for the first build.
3. **Task 2 — miss handling:** **bake anyway, report the miss.** Deliver the film with the closest
   achievable arrival; report `offsetDays` and whether `peakTurnDeg` stayed inside `bound` as numbers, so
   the miss is visible rather than silent. Editor does not refuse.

## State this builds on (all merged to `bim-ootb` main, 2026-07-29)
CPE **v16**, MAXQ **v17**, sw **v882**. PRs #1081–#1084. `tmFollowTimeline()` is the single verb both
Preview and bake use; `§CPE_BUILDUP_SOURCE mode=S|T` logs the choice unconditionally.
**Not in scope here:** `prompts/CINEMA_FIND_TO_FILM.md` (the other promoted POC), the panel's hardcoded
`— 3 bands` header, and the two incompatible `tasks` schemas — all recorded, none belong in this file.

---

## §DIAGNOSIS 2026-07-30 — why Task 1 is still open, measured on the shipped Hospital DB

Diagnosis only. No code changed. Read against `~/bim-ootb/buildings/Hospital_extracted.db` and
`origin/main` `be88cce`.

### D0 — Task 1 was never built
`git grep -iE "HOST_BEFORE_HOSTED|W-HOST-ORDER"` over all of `bim-ootb` returns **zero** production hits.
The recent merged work (#1088 §CACHE_KEY, #1089 §CPE_ROOM_TITLE, #1090/#1091 §GEO-SERVED) is a different
lane. Nothing has yet touched the build order, so the window-before-wall the user saw on 2026-07-29 is
unchanged and expected. **The gate has not run RED yet, let alone green.**

### D1 — the reframe in Task 1a does not apply to the shipped Hospital
The spec says Hospital "carries deps + element links 46/46 … and our schema discards all of it," concluding
this is "the fallback running on a building that did not need a fallback." **Measured, the shipped extract
carries no programme at all:**
```
Hospital_extracted.db :  schedules 0 | tasks 0 | task_sequences 0 | task_elements 0
Hospital_meta.db      :  schedules 0 | tasks 0 | task_sequences 0 | task_elements 0
```
The tables exist (`task_sequences` even has `predecessor_id/successor_id/sequence_type/lag_days`) and are
empty; `tasks` has no early/late/float/is_critical columns. So the 46/46 deps are in the **source IFC**, not
in what the viewer loads. **Consequence for planning: T1b (1a) cannot change what the user sees until
Hospital is re-extracted.** Only track 1b — the fallback gate — moves today's film. The dependency order
stated in the ⚠ block still holds for *correctness*, but 1a is a compiler-side task, not a viewer-side one.

### D2 — the cheap cause the spec told us to check first: **it is the storey bucket, not the trade order**
Trade order is already correct. `viewer/rates/sequence_rules.json`:
`IfcWall`/`IfcWallStandardCase` → **seq 6** (MASON); `IfcWindow`/`IfcDoor` → **seq 7** (CARPENTER).
Both are `seq > 4`, so both land in `schedule_gate.js` PASS B, whose per-Level trade gate
(`:110–120`) makes trade *k* wait for every lower trade in its own phase bucket. Wall-before-window **is**
expressed — *within a bucket*.

The bucket is `collapsePhase(el.storey)` — the raw `elements_meta.storey`. Measured on Hospital:

| storey | walls (seq 6) | windows+doors (seq 7) |
|---|---|---|
| Level 1 | 311 | 180 |
| Level 2 | 209 | 121 |
| Level 3 | 310 | 96 |
| Level 4 | 336 | 88 |
| Level 5 | 254 | 73 |
| Level 6 | 35 | 5 |
| Level 7 | 8 | 1 |
| Level 7A | 5 | 0 |
| **Unknown** | **0** | **7** |

**`storey='Unknown'` holds 7 openings and zero walls.** Their `phaseTrade['Unknown']` bucket has no seq<7
entry, so `tg` stays at `baseMs` (`schedule_gate.js:113–114`) and all 7 are scheduled **at project start** —
ahead of every wall in the building. That is a window before its supporting wall, produced by bucketing,
not by the trade table.

### D3 — why a fix already exists and still did not help: it landed on the display side
PR #869 (`926bd20`) added **§STOREY-Z**: reassign no-storey elements to the nearest real storey by median Z.
It lives in `viewer/time_machine.js:3243–3292` (inside the mini-Gantt data prep) and in
`viewer/lib/room_walker.js:203,225`. **It is not in `viewer/schedule_gate.js`.**

So the same feature holds two notions of "which storey is this element on": the **Gantt** reassigns and
looks correctly cascading; the **gate that computes the reveal order** buckets on the un-reassigned raw
storey. The picture and the order disagree, and the picture is the one that looks right. This is the same
shape as `prompts/SEAM_IDENTITY_AUDIT.md` §CLUSTERS **C2** — one identity derived two ways, the display
fixed and the computation missed.

### D4 — W-HOST-ORDER as specced is **not measurable** on the shipped DB
The gate wants `start_ts(host) <= start_ts(hosted)` per hosted element. Hospital's tables are:
`component_geometries, project_metadata, spatial_structure, element_instances, qto_cache, task_elements,
element_transforms, rel_contained_in_space, task_sequences, elements_meta, schedules, tasks`.
There is **no `rel_fills_element` / `rel_voids_element`** — the wall↔window host relation was never
extracted. `rel_contained_in_space` is space containment, a different relation.
So the witness cannot be written against the current extract without either (a) extracting the IFC
relationship compiler-side, or (b) deriving containment geometrically — and (b) must be *measured*
containment (window bbox inside wall bbox), never proximity, per the Prime Directive.

### What this means for the next session
1. **The 7 `Unknown`-storey openings are a real, cheap, measurable defect** — and the honest first move is
   to make W-HOST-ORDER report RED on them before touching anything. Reproduce first (§GUARDRAILS).
2. **§STOREY-Z belongs in one place**, called by both the Gantt and the gate. That is a smaller change than
   1b and may be most of what the user actually sees.
3. **7 of 571 openings is not obviously the whole defect.** The user saw *a* window before *its* wall; these
   7 are ahead of *every* wall. Whether the remaining 564 are ordered correctly relative to their own hosts
   is exactly what D4 says we cannot yet measure. **Do not close Task 1 on the strength of D2 alone.**
4. Task 1a is re-scoped: it is a **bim-compiler extraction** task (capture deps + host relations into the
   schema), not a viewer task. It cannot be witnessed on today's shipped Hospital.

**Not verified in this pass:** that these 7 are the elements the user saw in the baked film. That needs the
film or a §-logged run, and this pass ran neither.
