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

---

## §STAGE A 2026-07-30 — GATE NOT PASSED: the specced defect does not reproduce; a much larger one does

**Worker session. No production code changed.** The Stage A gate ("W-HOST-ORDER must report >0 BEFORE
the change, or stop") was run honestly and came back **0**. Per the standing rule — *a witness that
cannot show RED is not a witness, and you do not build a fix for a defect you failed to reproduce* —
Stage A and Stage B were **not implemented**. What follows is the spec that was written first, the
measurements that closed the gate, and the real defect those measurements found instead.

Measured against `~/bim-ootb/buildings/Hospital_extracted.db` (+ 5 more buildings) and `origin/main`
`be88cce`. Every number below comes from `tests/test_host_order.js` (new, committed to `bim-ootb`
branch `fix/4d-host-before-hosted`) running the REAL deployed `viewer/schedule_gate.js`.

### A.0 Spec written before any code (the contract this session held itself to)

- **W-HOST-ORDER (crude proxy, as specced in the brief).** Count seq-7 openings (`IfcWindow`/`IfcDoor`)
  whose `start_ts` precedes the earliest seq-6 wall (`IfcWall`/`IfcWallStandardCase`) in the building.
  *Proves/disproves:* "openings in a wall-less storey bucket fall through the per-Level trade gate and
  schedule at project start." **Must be >0 before, 0 after.**
- **W-HOST-ORDER (real, per-host).** Derive host by MEASURED bbox containment from `element_transforms`
  (`center_*`/`bbox_*`) — opening bbox ⊆ host bbox inflated by `tol` on every axis. Never proximity.
  Ambiguous (>1 candidate) ⇒ **no host, counted, never guessed.** Assert `start_ts(host) <= start_ts(hosted)`.
- **W-HOST-COVERAGE.** hosted / ambiguous / noHost out of 571 openings, swept over `tol ∈ {0, .05, .1, .25, .5}` m
  so the coverage number is visibly tolerance-sensitive rather than a single cherry-picked figure.
- **W-HOST-NO-REGRESSION.** Run the whole element build twice — with and without §STOREY-Z — and compare
  per-storey first-wall / first-opening days numerically. Bottom-up character must survive.
- **Reveal semantics (checked, not assumed).** `viewer/time_machine.js:1132-1157` puts an element on screen
  at `op.start_ts` (as `frontier`), so **start order IS reveal order** and `start_ts` is the correct thing
  to assert on. Confirmed before writing any assertion.

### A.1 The gate: RED was never reached — every form of W-HOST-ORDER is already 0

| witness | tol | hosted | ambiguous | noHost | **violations** |
|---|---|---|---|---|---|
| crude proxy (opening before FIRST wall) | – | – | – | – | **0 / 571** |
| per-host, measured containment | 0.00 m | 433 | 47 | 91 | **0 / 433** |
| per-host, measured containment | 0.05 m | 488 | 51 | 32 | **0 / 488** |
| per-host, measured containment | 0.25 m | 486 | 68 | 17 | **0 / 486** |
| per-host, measured containment | 0.50 m | 452 | 110 | 9 | **0 / 452** |
| **widened superset** — EVERY 3D-overlapping wall must precede | 0.05 m | 565 | 0 | 6 | **0 / 565** |

The crude proxy is also 0 on **Duplex, HHS_Office_Federated, Terminal, LTU_AHouse, JKR** — with and
without §STOREY-Z. It is not merely 0 on Hospital; it is **structurally incapable of going RED**, because
`geoGate` + the project-wide `§CREW-CAP` crew pool already push every seq-7 element hundreds of days past
the first seq-6 wall regardless of bucket. Stage B's constraint `start_ts(host) <= start_ts(hosted)` is
**already satisfied for every derivable host at every tolerance**. Adding it would be a no-op.

### A.2 Two factual corrections to §DIAGNOSIS 2026-07-30 (D2 and D3 are wrong)

**D3 is wrong at the code level.** §STOREY-Z is **already reaching the gate**. `assignStoreyByZ` is applied
at `viewer/time_machine.js:3269` while the `elements` array is built, and *that same array* is what is
handed to `ScheduleGate.computeSchedule` at `viewer/time_machine.js:3358-3359`. There is exactly ONE
`computeSchedule` call site in the repo. Measured: `reassigned=9457` on Hospital. There is no
raw-vs-reassigned split between Gantt and gate — the "one identity derived two ways" C2 shape does not
apply here. **Stage A's premise is void; there is nothing to single-source.**

**D2's mechanism does not occur.** The 7 `storey='Unknown'` openings do **not** schedule at project start.
Measured with §STOREY-Z force-disabled, so the raw bucket is genuinely wall-less:

```
§PER_STOREY Unknown   walls=0  firstWallDay=n/a   openings=7  firstOpeningDay=295.56
            (earliest wall anywhere in the building = day 261.15)
```

`tg` does stay at `baseMs` for that bucket exactly as D2 says — but `start = Math.max(geoGate(el), tg,
slot.time)` (`schedule_gate.js:116`), and the other two terms dominate. D2 read line 113-114 and stopped
before line 116. **The diagnosis was never run; it was reasoned from source.**

### A.3 What the user actually saw — RED, reproduced, 2211/2211

The user reported *"a window revealed before its supporting wall."* It was not an `IfcWindow`.

`viewer/rates/sequence_rules.json` classifies by IFC class alone:
`IfcMember` → **seq 3**, `IfcPlate` → **seq 4** (both *Superstructure*, `STEEL_ERECTOR`);
`IfcWall`/`IfcWallStandardCase` → seq 6; `IfcWindow`/`IfcDoor`/**`IfcCurtainWall`** → seq 7.
`seq <= 4` routes an element into **PASS A, the structural pass**.

On Hospital that classification is materially wrong:

- **2211 / 2211** `IfcPlate` are named `System Panel:**Glazed**:…` — curtain-wall glazing.
- **7122 / 7127** `IfcMember` are named `Curtain Wall:Profilit-…Framing…` — curtain-wall mullions.
- All 9333 **have meshes** (`element_instances`) — they are on screen.

So the entire glazed façade is erected as *structure*:

```
§CLASS_SPAN  (reveal start day; project = 1335 days)
  IfcMember            n=7127  seq=3  Superstructure  min=13.2  p50=84.4  max=146.6
  IfcPlate             n=2211  seq=4  Superstructure  min=14.1  p50=90.2  max=146.1
  IfcWall              n= 158  seq=6  Architecture    min=261.2 p50=274.0 max=281.4
  IfcWallStandardCase  n=1310  seq=6  Architecture    min=261.2 p50=270.8 max=281.5
  IfcWindow            n= 131  seq=7  Architecture    min=289.4 p50=291.2 max=295.3
  IfcCurtainWall       n= 178  seq=7  Architecture    min=281.6 p50=283.4 max=285.3

§W-FACADE-ORDER glazed panels revealed BEFORE the FIRST wall in the building = 2211/2211
§W-FACADE-ORDER panelsWithTouchingWall=1445  violations=1445/1445  worstLateDays=251.19
   e.g. glazed 3sbXgwO310x8BAlsfLa$g4 @day14.13 vs touching IfcWallStandardCase @day264.73 late=250.6d
   e.g. glazed 3sbXgwO310x8BAlsfLa$WY @day14.13 vs touching IfcWall            @day261.68 late=247.5d
```

**A glazed panel appears on day 14. The wall it physically touches appears on day 265.** That is the
window-before-its-wall the user saw, 251 days wide, on 1445 measured touching pairs. This IS the RED the
gate was looking for — it was just never going to be found by looking at `IfcWindow`.

**Corollary, same root cause:** `IfcCurtainWall` (seq 7, day 281-285) is scheduled **~250 days after its own
`IfcPlate`/`IfcMember` children** (seq 4/3, day 14-146). The IFC aggregate is built after its parts. Note
the 178 `IfcCurtainWall` rows have **no `element_transforms` row and no `element_instances` row** — the gate
sees them at world origin (`COALESCE(...,0)`), ~169 m below a building whose median Z is 168.9 m, with a
zero-area footprint, and they are never rendered. A fix must target `IfcPlate`/`IfcMember`, not the container.

**Fleet scope — and the trap that makes this NOT a one-line fix.** A raw `IfcPlate` count is misleading;
what matters is whether the plate is glazing or genuinely structural. Measured by name:

| building | `IfcPlate` | glazing? | witness verdict |
|---|---|---|---|
| Hospital | 2211 | 2211 × `System Panel:Glazed` | **RED** 2211/2211 before any wall; 1445/1445 touching pairs; worst **251.19 d** |
| HHS_Office_Federated | 629 | 438 × `Systemelement:**Verglasung**` (German) | **RED** 154/438 before any wall; 145/145 touching pairs; worst **20.58 d** |
| Terminal | 33,324 | **0** — all `Metal Deck` | **GREEN** — seq 4 is *correct* here; witness does not false-positive |
| LTU_AHouse / JKR / Duplex | 145 / 120 / 0 | – | crude + per-host witnesses all 0 |

So the defect is confirmed on **two independent buildings**, and Terminal proves a blanket
"`IfcPlate` is not structure" reclassification would be **wrong** — its 33,324 metal-deck plates belong in
Superstructure exactly where they are. Note also that HHS names its glazing `Verglasung`: **a name-based
rule is locale-dependent** and will silently miss buildings authored in other languages. The witness
therefore prints `§PLATE_UNMATCHED` (count + name breakdown of every `IfcPlate` its regex did *not* claim)
so that blind spot is always a number on the log rather than a silent omission. This is the concrete
evidence behind the ⛔ decision below: the robust discriminator is the IFC decomposition
(`IfcCurtainWall` ⊃ `IfcPlate`/`IfcMember`), which is **not in this schema** — a bim-compiler extraction task.

### A.4 Why this was NOT fixed in this session

The fix is a **class→sequence reclassification**, not a scheduling-gate change — a different file
(`viewer/rates/sequence_rules.json`), a different blast radius, and a decision that is not the worker's to
make. `IfcPlate`/`IfcMember` at seq 3-4 is *correct* for structural steel plates, gusset plates and bracing;
it is wrong only when the element is curtain-wall fabric. Distinguishing them requires a rule the brief did
not authorise and this session will not invent. **⛔ BLOCKED on one decision:** may the sequence lookup be
widened beyond `ifc_class` — e.g. to consult `element_name` (`/glazed|curtain wall/i`), or to consult the
IFC decomposition (`IfcCurtainWall` ⊃ `IfcPlate`/`IfcMember`, which is **not extracted** into this schema
and would be a bim-compiler task)? Both are EXTRACTION, not invention; the choice between them is an
architecture call.

Whatever is chosen, `tests/test_host_order.js` already gates it: today it prints `§VERDICT RED`, and it is
the artifact that turns green. `viewer/schedule_gate.js` and `viewer/rates/sequence_rules.json` are both
precached (`viewer/sw.js:192`, `CACHE_VERSION` currently `v884`) — the fix commit must bump it. This
session changed no precached file, so no bump was made.

### A.5 Deliverables

- `bim-ootb` branch **`fix/4d-host-before-hosted`** (off `origin/main` `be88cce`), worktree `/tmp/wt-4dorder`
  — adds `tests/test_host_order.js` only. **Zero production files touched.** Not wired into CI
  (`.github/workflows/ci.yml` runs an explicit list; nothing globs `tests/`).
- Stage B not started — correctly gated off by Stage A.

---

## §STAGE B 2026-07-30 — SPEC (written before any code, per Spec-First)

**Worker session, `bim-compiler` branch `feat/rel-aggregates-classification`, worktree `/tmp/wt-extract`.**
Answers §A.4's ⛔ with the second of the two options it named: **consult the IFC decomposition**
(`IfcCurtainWall` ⊃ `IfcPlate`/`IfcMember`), not `element_name`. §A.3 already proved the name rule is
locale-dependent (HHS `Verglasung`, and a mullion named `Rechteckiger Pfosten:6 x 15 mit Deckprofil`).
The decomposition is authored by the modeller and carries no language.

### B.0 Contract

- **B1 `rel_aggregates`** — recover `IfcRelAggregates` (+ `IfcRelNests`) verbatim. `provenance` is
  `ifc:recovered`; nothing is derived, nothing inferred from proximity or name.
- **B2 classification** — recover `IfcRelAssociatesClassification` → `IfcClassificationReference` into
  **nullable** `elements_meta` columns. **Absent ⇒ NULL and COUNTED, never guessed.** Coverage is
  reported as a number per building; near-zero is a valid finding.
- **B3 witness** — re-run §A.3's `§W-FACADE-ORDER` with curtain-wall children reclassified via the new
  edge instead of `ifc_class`. **Must go 1445/1445 RED → 0 GREEN.** *Proves:* the decomposition edge is
  sufficient to order the glazed façade after its wall.
- **B4 no-false-positive** — Terminal's 33,324 `Metal Deck` `IfcPlate` have **no** curtain-wall parent, so
  they must not move. **Must stay GREEN.** *Disproves:* "this is a blanket `IfcPlate` reclassification."
- **Boundary:** analysis only. No `~/bim-ootb` production file is edited, no OCI upload, no `.db` committed.

### B.1 Schema delta (`DAGCompiler/python/extractIFCtoDB.py`)

```sql
-- widened (table already existed; parent_class/child_class/rel_type/provenance are new)
CREATE TABLE rel_aggregates (
    parent_guid TEXT NOT NULL, child_guid TEXT NOT NULL,
    parent_class TEXT, child_class TEXT,
    rel_type TEXT NOT NULL DEFAULT 'aggregates',   -- 'aggregates' | 'nests'
    provenance TEXT DEFAULT 'ifc:recovered',
    PRIMARY KEY (parent_guid, child_guid, rel_type));
-- new nullable columns on elements_meta
classification_code TEXT, classification_system TEXT, classification_name TEXT
```

`rel_type` is what lets `IfcRelNests` ride along without conflating two different IFC relations —
`IfcRelAggregates` is whole/part, `IfcRelNests` is ordered-child (ports, segments). Both are recovered;
a consumer that only wants decomposition filters `rel_type='aggregates'`.

⚠ `classification_name` is a **third** column beyond the two this task specified. Rationale, stated
openly rather than slipped in: the reference carries `('…/uniformat','B2020200','Curtain Walls',#…)` —
the human-readable facet is already in hand at zero extra parse cost, and the whole reason both changes
ride in one pass is that a second fleet re-extract is expensive. Dropping it would have cost exactly
that. Nullable, additive, no consumer reads it yet.

### B.2 IFC2x3 vs IFC4 (the attribute rename that will silently return NULL if missed)

`IfcClassificationReference` is `(Location, **ItemReference**, Name, ReferencedSource)` in IFC2x3 and
`(Location, **Identification**, Name, ReferencedSource, Sort)` in IFC4. Read via `getattr` fallback
`Identification → ItemReference`, or every IFC2x3 building extracts as 100% NULL and looks like a
building with no classification rather than a parser that read the wrong slot.

System name resolves by walking `ReferencedSource` to the `IfcClassification` and taking its `.Name`
(`'Uniformat'`), **not** the reference's own `.Name` (`'Curtain Walls'` — that is the code's label).

### B.3 Multi-association and non-extracted targets

One element may carry >1 classification. **First wins, collisions COUNTED and logged** (`§CLASSIFY
multi=<n>`) — never concatenated, never arbitrated by a rule this repo cannot source. Associations
pointing at a GUID with no `elements_meta` row (non-geometric parents, spatial nodes) are counted
separately as `orphan=<n>` rather than silently dropped.

### B.4 Source-of-truth check performed before any of the above

The shipped `Hospital_extracted.db` reproduces from **`internal/UNMERGED/Hospital_IFC2x3_*.ifc`** (7
discipline files), verified by class histogram against the DB — `IfcPlate` 2211, `IfcMember` 7127,
`IfcCurtainWall` 178, `IfcWallStandardCase` 1310, `IfcWall` 158, `IfcWindow` 131, all exact.
`/home/red1/Downloads/Hospital 2.0.ifc` is **NOT** the source (2690 plates / 7044 members / 0 curtain
walls) and must not be used.

### B.5 RESULTS — measured 2026-07-30. Everything below was RUN, not reasoned.

**Two corrections to the brief this session was given, both found by measurement before coding:**

1. **`extractIFCtoDB.py` already had `IfcRelAggregates`** (table at :202, extraction at :1610). The
   brief's "zero `IfcRelAggregates` handling today" was stale. What was missing was `parent_class` —
   without it the edge is unusable for exactly this problem, because an `IfcCurtainWall` is
   non-geometric and **gets no `elements_meta` row at all**, so there is nothing to join back to.
2. **`extractIFCtoDB.py` did NOT produce the shipped fleet.** Shipped `elements_meta` is
   `(guid, ifc_class, element_name, storey, discipline, material_name, material_rgba, building)` —
   byte-identical to `~/bim-ootb/viewer/import_db_builder.js:38`, the **in-browser importer**. The
   Python extractor writes a different schema (`id`, `element_type`, `base_geometries`). See §B.7.

**Source IFC — identified, not assumed.** `internal/UNMERGED/Hospital_IFC4_*.ifc` (7 discipline files).
IFC2x3 and IFC4 exports of this model have identical architectural counts, so class histogram alone
cannot separate them; **IFC4 was confirmed by GUID overlap and by the MEP class names** — the shipped DB
has `IfcPipeSegment`/`IfcDuctSegment`, which exist only in IFC4 (IFC2x3 collapses both to
`IfcFlowSegment`). Re-extracting IFC2x3 mismatched 22 classes; IFC4 mismatched 4.
`/home/red1/Downloads/Hospital 2.0.ifc` is **NOT** the source (2690 plates / 7044 members / 0 curtain walls).

```
elements_meta         re-extract 63917  vs shipped 63415   (+502)
GUID overlap          63182 shared / 63415 shipped = 99.63%
class histogram       27 of 31 classes EXACT
```
**All 502 are explained, none are drift:**
- `+735 IfcOpeningElement` — the Python extractor keeps openings; the browser importer drops them.
- `−178 IfcCurtainWall, −31 IfcStair, −24 IfcRoof = −233` — these are **precisely the non-geometric
  aggregate containers**. The Python extractor only writes `elements_meta` inside the tessellation loop,
  so a parent with no own body gets no row. 233 missing rows = 233 aggregate parents. The defect and the
  extractor's blind spot are the same 233 objects.

**B1 — `§DECOMP rel_aggregates`**
```
9527 rows | 254 distinct parents | rel_type: aggregates=9527, nests=0
  IfcCurtainWall  parents=178  children=9340    <- 178/178 resolve children (100%)
  IfcStair        parents=31   children=93
  IfcRoof         parents=24   children=24
  IfcBuilding/IfcSite/IfcProject  parents=21  children=70   (spatial decomposition)
  children of IfcCurtainWall by class: IfcMember 7122, IfcPlate 2211, IfcDoor 7
```
Child coverage against the **shipped** element set (the DB the viewer actually loads):
`IfcPlate 2211/2211 (100.0%)` and `IfcMember 7122/7127 (99.93%)` have a curtain-wall parent.
The 5 unmatched `IfcMember` are genuinely not curtain-wall members. **`IfcRelNests` = 0 in this model**
— included in the code and reported as zero, not quietly omitted.

**B2 — `§CLASSIFY`** `coded=4546/63917 (7.11%) uncoded=59371 systems=Uniformat multi=0 orphan=101`.
**Not near-zero — the brief's expectation was wrong.** Full analysis in
`prompts/JKR_SKATA_COMPLIANCE_LANE.md` §PHASE-C.

⚠ **Classification alone would NOT have fixed the façade** — worth stating because it is the tempting
shortcut: `IfcPlate` coded = **0**, `IfcMember` coded = 1580/7127. The Uniformat code `B2020200 Curtain
Walls` sits on the *parent*, not the glazing. `rel_aggregates` is the necessary discriminator; the
classification column is a separate deliverable that happens to ride the same pass.

**B3/B4 — `tests/test_facade_order_decomp.js`.** Rule under test: an element whose **authored** parent is
an `IfcCurtainWall` takes the sequence already published for `IfcCurtainWall` in `sequence_rules.json`
(seq 7 / Architecture / CARPENTER). Parent class from the IFC, sequence from the existing rules file —
no name regex, no invented sequence number.

| case | panels | before | after | worstLateDays | verdict |
|---|---|---|---|---|---|
| **Hospital** | 2211 | **1445/1445 RED**, 2211 before any wall | **0/1445**, 0 before any wall | **251.19 → 0.00** | **RED → GREEN** |
| **HHS_Office_Federated** | 629 | **191/193 RED**, 221 before any wall | **0/193**, 0 before any wall | **20.58 → 0.00** | **RED → GREEN** |
| **Terminal** | 33,324 plates | curtain-wall children **0**, retagged **0** | schedule **bit-identical** | – | **GREEN, no false positive** |

Terminal's GREEN is **structural, not an absence of data**: its `rel_aggregates` was extracted from
`/home/red1/Downloads/TerminalMerged.ifc` and contains 25 edges — `IfcBuilding→23 IfcBuildingStorey`,
`IfcSite`, `IfcProject`. **Zero `IfcCurtainWall` parents.** Its 33,324 `Metal Deck` plates are untouched
because the authored model says they are not curtain-wall fabric.

**The locale blind spot is now a measured number, not a worry.** On HHS the decomposition claims
**629** plates where the `/glaz|verglas|.../i` regex claims **438** — `decompOnly=191`. The name rule was
silently missing **191 curtain-wall plates (30%)** on a building we already ship. On Hospital the two
agree exactly (2211 = 2211). **This is the direct evidence for choosing decomposition over the name rule.**

**Side effect, reported not buried:** moving 9,340 elements from `STEEL_ERECTOR` to `CARPENTER` changes
the crew pools, so `projectDays` grows Hospital **1335 → 1424** and HHS **172 → 192**. That is a real
consequence of correcting the trade, not a defect — but a 6.7% programme extension should be a conscious
acceptance, not a surprise.

### B.6 What was NOT done, and why

- **No `bim-ootb` production file changed.** The fix itself — teaching `sequence_rules.json` /
  `time_machine.js` to consult `rel_aggregates` — is the viewer-side follow-on. This session proved the
  edge is sufficient; wiring it is a separate task with a `sw.js CACHE_VERSION` bump.
- **No OCI upload, no `.db` committed, no `.db` deleted, no push.** Re-extracted DBs are local and
  untracked, in the session scratchpad.
- **Only Hospital was re-extracted in full.** Terminal and HHS have `rel_aggregates` only (a relations-only
  probe reusing the same `extract_rel_aggregates()`, no tessellation). Fleet-wide classification coverage
  is therefore **unmeasured except on Hospital** — do not quote 7.11% as a fleet number.
- **`IfcRelNests` is implemented but unexercised** — 0 rows in all three models. Untested against real
  nest data.

### B.7 ⛔ THE BLOCKER FOR THE FLEET RE-EXTRACT — read before planning it

**The canonical Python extractor is not what built the shipped fleet.** The shipped DBs come from
`~/bim-ootb/viewer/import_db_builder.js` (in-browser). Consequences:

1. The Python extractor's output is **not drop-in** for the viewer: different `elements_meta` shape,
   `base_geometries` instead of `component_geometries`, and it lacks `qto_cache` / `spatial_structure` /
   `rel_contained_in_space` (those come from `scripts/compile_rooms.py` + `build/room_walker.js` as a
   post-pass), and the shipped `tasks` DDL is the older narrow one.
2. It **does not federate** — `-o` overwrites per run. A 7-discipline building needs an explicit merge,
   and `elements_meta.id` is a per-file autoincrement that **collides** across discipline DBs (this
   silently dropped 44k rows on the first attempt here). Any fleet script must merge on `guid`, not `id`.
3. It **drops the 233 non-geometric aggregate containers** (§B.5) — including all 178 `IfcCurtainWall`.
   Shipping its output would *remove* rows the viewer currently has.

**So "re-extract the fleet" is ambiguous and must be decided before anyone starts:** either (a) port
§DECOMP + §CLASSIFY into `import_db_builder.js` and re-import through the browser — but that is a
**viewer production change**, explicitly out of scope for this session; or (b) keep the Python extractor
as a **sidecar** that emits only `rel_aggregates` + the classification columns, and patch them into the
existing shipped DBs via the established `migration/*.sql` + self-heal-loader pattern (CLAUDE.md
§DB CHANGES) — **no full re-extract, no 4GB OCI cycle at all.**

**(b) looks strictly cheaper and is the recommendation**, on measured grounds: `rel_aggregates` for
Hospital is 9,527 rows of two GUIDs and two class names — a few hundred KB of SQL, versus a 263MB binary.

**And a `SEAM_IDENTITY_AUDIT` §CLUSTERS finding falls out of this — one relation, two names, two
schemas.** `import_db_builder.js:77` already declares `bom_tree(parent_guid, child_guid, rel_type)`,
commented *"IFC parent→child relationships (IfcRelVoids/Fills/Aggregates)"* — the same edge as
`rel_aggregates`, under a different name, in the other extractor. Measured across the shipped fleet:

```
Duplex_extracted.db                bom_tree table present, 11 rows
Hospital / Terminal / JKR / HHS / Clinic / LTU_AHouse / Hospital_3 / TermRooms   NO bom_tree table
```

**1 of 9 buildings has the table at all, with 11 rows.** So the browser importer's decomposition edge is
declared but effectively never populated across the fleet, while the Python extractor's is populated but
never shipped. That is why the façade defect survived: *both* extractors can express this relation and
*neither* one delivers it to the viewer. Reconciling the two names into one is probably the real first
step of the follow-on, and it is NOT this session's call to make.
