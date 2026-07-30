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

## ⛔ Blocking questions — put these to the user before building
1. **Task 1a:** when a building has BOTH a captured programme and the geometric fallback available,
   is captured always authoritative, or is there a case for showing the difference between them?
2. **Task 2:** what is the target — an element, a room, or a phase? (`_sfxPhases` already derives phase
   changes per tick, so phase is cheapest; element is most precise.)
3. **Task 2:** when the pacing cannot hit the window within its bounds, does the film get made anyway
   with the miss reported, or does the editor refuse and say so?

## State this builds on (all merged to `bim-ootb` main, 2026-07-29)
CPE **v16**, MAXQ **v17**, sw **v882**. PRs #1081–#1084. `tmFollowTimeline()` is the single verb both
Preview and bake use; `§CPE_BUILDUP_SOURCE mode=S|T` logs the choice unconditionally.
**Not in scope here:** `prompts/CINEMA_FIND_TO_FILM.md` (the other promoted POC), the panel's hardcoded
`— 3 bands` header, and the two incompatible `tasks` schemas — all recorded, none belong in this file.
