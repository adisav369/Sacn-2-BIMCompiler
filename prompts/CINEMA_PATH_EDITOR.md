# ⚠ DO NOT REMOVE
**Scope:** ONE feature — a waypoint editor that opens after the Alt+C preview, lists the cinema path's
waypoints with their camera info, and lets the user key or drag them before the bake. The "simplest
fastest tour maker."
**Not in scope:** the §CINEMA_SPACE attic-pick default — **owned by another session as of 2026-07-26,
do not touch `_cinemaPathPlan`'s §CINEMA_SPACE block (~L3486-3610)**; see §Out of scope below.
**Read the log after every run.** Verification on this project is `§`-tagged console output, not
screenshots — and for anything continuous (camera path, angles, Z) it is the NUMBERS, per CLAUDE.md's
FUNDAMENTAL LAW. Honour this block until this file is DONE.
**⚠ READING ORDER for a fresh session:** §CINEMA_PATH_EDITOR_MODEL (settled data model) → §CPE_BUILT
(what shipped) → §CPE_LIVE (browser run + the defect it caught) → **§CPE_BANDS (next build, spec ready,
NOT implemented)** → **§CPE_PACING (measured, ONE user decision open)**. The "graph dialog" framing in
the original sections is SUPERSEDED throughout.

**STATUS 2026-07-27 (session end):** everything specced in this file is BUILT AND WITNESSED.
- §CPE_BANDS + full-film tube → **merged to main, PR #1026**.
- §CPE_SCREEN_PLANE + §CPE_PACING → **PR #1027, auto-merge armed, e2e was still running at session
  end. FIRST THING NEXT SESSION: confirm #1027 landed** (`gh pr view 1027 --json state`); if CI went
  red, that is the only loose end.
- The pacing decision is ANSWERED — user: *"I already said derived"* — dive, spin and orbit are all
  derived. Built. **One thing is left to LOOK AT, not decide: the derived totals do not match the
  ~15s/~40s expectation** — see §CPE_PACING_BUILT.

**RESUME AT:** §CPE_PACING_BUILT "what to look at next".

**⚠ 2026-07-27 (later, live user run on Hospital):** OK-after-an-edit CRASHED the bake on shipped
main — `§CPE_OK_CRASH`, root-caused, fixed and witnessed 6/6 (`witness_cpe_ok_bake.js`, the first
gate that walks editor → OK → bake instead of the plan seam). Read **§CPE_OK_CRASH** at the end of
this file before anything else on this lane.

---

# §CINEMA_PATH_EDITOR — the simplest fastest tour maker

## The one sentence
**Build §CINEMA_PATH_EDITOR as the SIMPLEST FASTEST TOUR MAKER — no new icon, no new panel: Alt+C
already computes the plan in ~50–100 ms, so put a DIALOG in that existing gap showing the path as a
graph (reuse the Find panel's route visual) with the per-beat camera angle/Z, and the user either
hits OK to proceed straight into the recording exactly as today, or drags a waypoint / fixes the
camera Z that currently dives into the attic first — with an explicit **"Save this path"** action
that stores the edit as a `cinema_path` table in the building DB exactly the way
`staffage_instances` already round-trips.**

## Origin
User, 2026-07-26 evening, *after* the foundation below was laid and landed:
> "once a path is done, the user can cancel or not, a new feature when pressed calls up a graph panel
> of the scene path and cam angle... That way this time the user can adjust the path points, Z level
> of the camera as now it goes for the attic."

then, scoping it:
> "it is to be the simplest fastest tour maker — perhaps a dialog box with the graph (similar to Find
> rooms path visually as idea) and user can OK to proceed or adjust it, thus no need extra icon."

## §CINEMA_PATH_EDITOR_MODEL — the settled interaction model (user, 2026-07-26, in discussion)
**This section is the spec. Where it disagrees with the "graph dialog" wording elsewhere in this
file, this wins.** Settled point by point with the user; nothing here is inferred.

### The data model — one authored thing, everything else derived
1. **Waypoints are the ONLY authored data.** A waypoint is a position plus a camera height. Nothing
   else is stored.
2. **Camera angle is NEVER authored.** It is LOS — the look direction at waypoint *n* is toward
   waypoint *n+1*. This is why the table carries **one extra final row** (the stopping waypoint): it
   exists to give the last real leg something to look at.
3. **Therefore "adjust the POV" is not a separate gesture.** Dragging waypoint *n+1* is *how* you
   change waypoint *n*'s camera angle. Falls out of (2) for free.
4. **Consequence, load-bearing:** no authored yaw/pitch anywhere → `cinema_path` stores positions and
   times only → **§CINEMA_TURN_SLERP keeps deriving headings from the path exactly as today.** Its
   witness (G6) stays green without being weakened to accommodate this feature. An earlier
   per-waypoint-authored-aim design was considered and dropped for precisely this reason.

### Geometry — no sharp corners can exist
5. **The waypoints are CONTROL points, not corners.** The flown path is smooth everywhere by
   construction, so "sharp corner" is not a state the path can reach. User's own words: *"that means
   there are no 'sharp' corners."*
6. **The curve CUTS INSIDE the corner** (user directive: *"yes cut inside"*) — it does not pass exactly
   through the placed point. The turn is eaten by geometry, not by slowing down.
7. **The cut is BOUNDED BY MEASURED CLEARANCE, not a constant.** `A.cinemaFan` (exposed
   `effects.js:4079`) is already this project's single source for real measured clearance-to-nearest-
   surface — §CINEMA_SPACE calls it "the ONLY where-is-open-space source". The rounding radius at each
   waypoint comes from it. Tight room → tight curve; open hall → wide graceful arc. **Do not invent a
   fixed fillet radius** — that would be exactly the kind of guessed constant the prime rule forbids.
8. **This retires an ungated defect.** "D2 walk-out corner whip — 19.8°/frame, printed by the witness,
   not gated" (listed under §Out of scope) is the user's reported *"about 2 jerks, fast jump at least a
   frame."* It was tolerable only because the derived route is tame. Once waypoints are user-draggable,
   sharp input becomes trivial to create — so smoothing moves IN SCOPE here and gets a real gate (G7).

### Timing — constant speed, editable total
9. **Constant speed. Path length sets the clock.** Drag a point further out and the total rises from
   24s. The displayed total is the truth about what was authored, not a fixed budget.
10. **The total is then itself an editable field.** Key 20s and the whole clip speeds up uniformly;
    key 40s and it slows. Uniform scale over the whole film, not per-leg.
11. **Cost that sits behind that field:** total × fps = frames to bake. 24s = 576 frames; 40s = 960,
    i.e. ~1.7× the render time. Surface it, don't hide it.

### Interaction — reuse, nothing invented
12. **The editor opens AFTER the 10s preview**, with the camera already returned to its initial pose
    (`cinema_maxq.js` already saves/restores it — `camSave`, ~L429).
13. **Click a row (or its waypoint) to HOLD it.** The scene camera backs up so that waypoint is framed.
    `A.controls.enabled = false` while held — the established pattern here, not a new one
    (`grid_drag.js:568/650`, `city.js:993/1013`, `doc_canvas.js:1450/1502`).
14. **Held-point verbs mirror the viewer's own navigation verbs** (user: *"isn't that intuitive?"*):
    - **drag** → move the waypoint on the horizontal plane through its own height (x, z)
    - **ctrl+drag** → move it vertically (camera height — the original complaint)
    - **wheel** → still scene zoom, NOT point depth. Releasing just to look closer would defeat the
      point of holding; x/z/height are already fully covered by the two drags.
15. **Double-click empty canvas to RELEASE** (user directive — single click was rejected as too easy to
    trigger and lose focus). Canvas `dblclick` is claimed only by measure mode (`picking.js:150`), so
    this collides with nothing. Touch has no `dblclick`: reuse the existing 350ms double-tap detector
    at `picking.js:174`, do not write a second one.
16. **While a point is held, single click does nothing** — it must not run element picking, or mid-edit
    clicks select the walls behind the waypoint.
17. **The held state must be unmistakable** — pulsing waypoint + highlighted row + a status line saying
    a point is held and double-click releases. A frozen-but-unexplained canvas reads as a hang.
18. **The pulse reuses §SELECT-PULSE** (`navigate_find.js:2160-2227`): generation counter + rAF +
    `markDirty`, and critically `depthTest:false`/`renderOrder` so the marker **shines through walls** —
    load-bearing, since the camera is outside the building when the editor opens.
19. **Two-way binding is required.** Key a number in the table → the canvas moves. Drag in the canvas →
    the number updates. Same state, two views.

### One defect this design exposes (fix as part of the work)
20. `cinema_maxq.js:357-360` claims `A._maxqActive`, `_wakeAcquire()` and `_dampHold()` **before** the
    plan/preview. `A._maxqActive` makes `dlod_nav.js:307` report `'cinema'` and fully disengage DLOD.
    An editor opening after the preview inherits all three — so a user editing for five minutes holds a
    screen wake-lock and runs Terminal/Hospital at full detail with no LOD the whole time. **The editor
    must release all three while open and re-claim them on OK.**

### Amendment on record — scope guardrail 3 is void
Guardrail 3 below says *"do NOT build an in-viewport 3D drag gizmo."* The user directed exactly that on
2026-07-26 (*"as we click on each row, appears a cam-eye 3d icon... user can adjust... and the canvas
reflects that changed path"*, then the freeze/drag/double-click model above). **Recorded as a deliberate
user amendment, not drift.** Guardrails 1, 2, 4 and 5 stand unchanged — in particular **guardrail 2: OK
without an edit must still be byte-identical to today.**

### Witness claims for this model (in addition to G1-G6 below)
- **G7** no-sharp-corners: on a path with a deliberately sharp authored corner, peak angular rate stays
  under a stated °/frame cap — the same measurement that today prints 19.8°/frame ungated. This is what
  makes "graceful" a NUMBER rather than a look.
- **G8** clearance-bounded rounding: the flown curve's maximum deviation from each authored waypoint is
  ≤ the `A.cinemaFan` clearance measured at that waypoint. Proves the cut-inside is bounded by measured
  space, never by a guessed constant.
- **G9** constant speed + uniform scale: doubling a leg's length raises the total proportionally at
  unchanged m/s; then setting the total to T re-times every leg by the same factor (sampled speeds
  ratio-identical).
- **G10** LOS: sampled look direction at each waypoint points at the next waypoint (within the smoothing
  tolerance), and moving waypoint *n+1* changes waypoint *n*'s aim — the (3) claim, measured.
- **G11** lock release: while the editor is open `A._maxqActive` is false and the wake-lock is released;
  both are re-claimed on OK. Proves defect (20) is actually fixed rather than described.

## §CPE_BUILT — implemented and witnessed 2026-07-27 (`bim-ootb` `feat/cinema-path-editor` @ `bc242be`)
**Status: the model above is BUILT. All gates green except where noted.** Files: new
`viewer/cinema_path_editor.js`; `viewer/effects.js` (corner rounding + override wrapper + persistence
read); `viewer/scene.js` (`cinema_path` write + `A.three2ifc`); `viewer/cinema_maxq.js` (hook + lock
release); `viewer/sw.js` v851→v852.

### Measured results — `witness_cinema_path_editor.js` 9/9 on Duplex AND Terminal
| gate | result |
|---|---|
| G1 OK-without-edit byte-identical | `maxPoseDiff=0` (exactly zero, both buildings) |
| G2 edit changes the path, not the dive before it | `maxDelta=4.30m firstDiffT=0.3375` vs `diveEnds=0.250` |
| G3 height moves by what was asked | straight path: `[1.5000,1.5000]`, err `0.00e+0` |
| G5 control, no authored path | `authored=false route=line`, replan diff `0.00e+0` |
| **G7 no sharp corners** | **7.5°/frame** on a 90° dog-leg, cap 12 — was 19.8 ungated |
| G8 deviation ≤ measured clearance | `dev=1.70m` vs `clear=7.31m/11.35m` (Duplex), `60.00m` (Terminal) |
| G9 constant speed | length ×1.371 → time ×1.371 exactly, at 2.10 m/s (Duplex) / 4.16 m/s (Terminal) |
| G10 LOS aim → next waypoint | `aimErr=0.7°` (Duplex), `5.8°` (Terminal) |
| G12 elastic orbit | endY −4.14→−0.14; clamp fires and is logged (`requested=31.9 granted=45.0 clamped=true`) |

`witness_cinema_path_persist.js` **7/7**: G4b writes no table without an explicit save; G4 round-trips
through a REAL page reload (`_openDbBytes` navigates) at `maxPoseDiff=0.0000m`; G11 proves
`_maxqActive=false` observed from INSIDE the open editor.

### Two real defects the witnesses caught — both were silent, neither was visible in review
1. **`settle` did not follow waypoint 0.** Beats 1-2 flew to the §CINEMA_SPACE pick while Beat 3
   started from `outWp[0]`, so editing row 0 teleported the camera at the beat seam. G3 measured it
   exactly: a 1.5m edit gave `[0.000, 1.508]` — the `0.000` IS the seam.
2. **`odx/odz` still pointed along the DERIVED exit.** Beat 3's gaze falls back to it in the last
   half-metre of the walk, and Beat 4 assumes it is the direction Beat 3 ends on. With an authored
   path the gaze SNAPPED onto the old bearing: **115.2°/frame**, six times worse than the whip this
   feature set out to retire. Both fixed by re-aiming them at the authored path.

### G3's second-order term, reported not hidden
On a path WITH corners the same 1.5m height edit lands at `[1.465, 1.508]`, spread 3.5cm. Not a height
error: raising the path makes the corner fans hit different geometry, so the rounding radius — and with
it the arc-length parameterization — legitimately shifts. Gated exactly on a straight path; printed as
`INFO` on a cornered one.

### ⚠ G6 finding — 5 of 6 pre-existing cinema witnesses are ALREADY RED on `origin/main`
Verified by running each on both the branch and unmodified `origin/main`, and diffing: **byte-identical
output in every case**, so this feature regresses nothing. But the baseline itself is not green:
- `witness_cinema_orbit_v2` — **PASS** (6/6) on the branch.
- `witness_cinema_damping_bleed` — **CRASHES** on both (180s `waitForFunction` timeout at its line 65).
  This is the §CINEMA_DAMPING_BLEED witness the scope header names as must-stay-green; it cannot
  currently prove anything either way.
- `witness_cinema_exit_breathe` — 5/7 on both. Fails its own G0 precondition (`oci 0/1` — one run
  fetched from object storage, so the two runs are not the same film) and G1.
- `witness_cinema_flat_ending`, `witness_cinema_reciprocal`, `witness_cinema_glazing` — FAIL identically
  on both.
**Not fixed here — out of this file's scope, and fixing a red baseline under a feature branch would
hide which change moved which number.** Named so it is a known state, not a surprise. Note
`witness_cinema_reciprocal` needs `HHS_Office_Federated_extracted.db` present or it reports a
misleading `PLAN FAILED` that looks like a code fault.

### §CPE_LIVE — first real-browser run, user, Hospital, 2026-07-27 (`b0db992`)
**The interaction model works end to end in a real browser**, proven by the user's own pasted console:
`§CPE_OPEN waypoints=3 pathLen=29.0m speed=4.83m/s` → `§CPE_HOLD i=0 controls.enabled=false` →
`§CPE_DRAG i=1 axis=xz` → `§CPE_RELEASE why=dblclick` → `§CPE_CLOSE action=ok edited=true total=32.6s`
→ `§CPE_APPLIED total=32.6s frames=489` → bake started. Constant-speed re-timing confirmed live:
pathLen 29.0→70.4m took the film 24.0→32.6s and the frame count 360→489.

**Why Hospital shows only 3 rows** (user asked): not a limit. `§ROOM_GRAPH … exits=0`, so `§CINEMA_EXIT`
fell back to `src=db-doors`, and the graph-route branch only runs for `EXIT::` nodes. The derived route
is therefore `settle → door → outside` — 3 points, 1 corner. A building whose room graph yields exits
gets the corridor waypoints too. User's verdict: *"3 points is also good, simple just adjust intended
edges."*

**⚠ The defect the live log exposed that every witness had missed.** `_cinemaFan` returns
`CINEMA_FAN_FAR` (60) for a ray that hits nothing, so a fan hitting nothing AT ALL reports `min=60.0`:
```
§CINEMA_DIVE ... fanMin=60.0 fanMax=60.0 fanMean=60.0 ... enclosed=0%
§CINEMA_CORNERS ... rMin=15.00 rMax=15.00 maxDeviation=7.50m
```
That is **"unknown"**, not "60 metres of space" — but the rounding read it as a measurement, fell
through to the 40%-of-leg cap, and cut **7.50m inside a hand-placed waypoint**. G8 "passed" only by
comparing that cut against the same fictional 60m. **Terminal was in the same state (`2/2` corners
unmeasured), so its earlier G8 pass was vacuous too** — `clear=60.00m` was printed in the run log and
read past. Fixed: no-hit → UNKNOWN → capped at the existing 3m nudge budget (Terminal's corner radius
4.80→3.00m, deviation 2.40→1.50m), and G8 hardened so measured corners must fit the measurement while
unmeasured ones must obey the cap. **Lesson worth keeping: a sentinel value that is numerically
plausible will pass a gate that compares against it. The live run found it; the headless suite could
not, because it was grading itself with the same sentinel.**

**Log hygiene fixed in the same pass:** `§CINEMA_CORNERS` fired on every `pointermove` (dozens of
identical rows/second in the user's paste) — now only on a shape change or once a second, and it names
`unmeasuredCorners=n/m`. `§CPE_HOLD` no longer re-logs an unchanged hold. `§MAXQ_START` is printed
before the editor opens, so `§MAXQ_START_REVISED` now prints when an edit changes the frame count.

**UI feedback, user-directed same session** (*"status feedback, the buttons should pulse louder perhaps
some orange"*): held waypoint is ORANGE and breathes continuously at 12Hz — throttled deliberately, an
every-frame `markDirty()` would defeat the renderer's idle parking for as long as a point is held (63k
elements on Hospital). A 300ms settle was wrong here: a frozen canvas with a static dot is exactly the
state that reads as a hang. Buttons now carry the status — OK turns orange and breathes when an edit is
queued, "Save this path" lights only when there is something to save, reports `Path saved` and does NOT
close the editor (staging is not recording), and a state line answers "what happens if I stop now?".
Blue waypoints kept clearly visible — the user initially read them as non-functional, then corrected
themselves (*"those 2 blue dots is part of the scene"*), so they are secondary, not de-emphasised away.

### Still open on this lane
- ~~The editor has not been driven by hand in a real browser~~ — **DONE 2026-07-27, see §CPE_LIVE.**
  Still unexercised by hand: ctrl+drag (height), the total-seconds field, and "Save this path" →
  `Ctrl+S` → reopen. Only xz-drag, row-click hold and double-click release have live evidence.
- `witness_cinema_path_editor.js` runs Duplex + Terminal only. Hospital/JKR/LTU_AHouse unrun.

## §CPE_SCREEN_PLANE — BUILT (user, 2026-07-27). Supersedes model items 13-17
> *"Whenever user touch canvas, it reacts without double click... user does that to adjust it to be
> 'facing' so that the dot when moved is merely facing X/Y ranging. Ie the dot when touched, can only
> move in that Xy sense."* … *"u only move up/down left/right, thus u merely adjust your canvas to be
> facing correctly first."*

**The canvas is NEVER frozen.** A drag on a handle moves it in the CAMERA'S OWN VIEW PLANE; everything
else falls through to OrbitControls. Drag and orbit are told apart by the hit-test on pointerdown, not
by a mode.

Why this is better than what it replaced: the freeze only ever existed because dragging and orbiting
competed for one gesture. Resolving that by hit-test instead of by mode **deletes** the freeze, the
double-click release, the touch double-tap, and ctrl+drag-for-height — height is now just a drag from
a side view. Mobile works for free, since nothing depends on a modifier key. Item 17 ("the held state
must be unmistakable, a frozen canvas reads as a hang") is moot: nothing freezes.

### Grab zones — BUILT as specified, with one cosmetic gap
User check, 2026-07-27: *"touching pipe end will pivot its end. Touching mid will take whole length
without pivoting."* **That behaviour is in and verified on merged main:**
`zones = [{p:e[0],z:'a'}, {p:b.c,z:'mid'}, {p:e[1],z:'b'}]`; an end calls `_rotateAbout(b, d.z==='b', p)`
(pivot about the far end, length invariant), `mid` translates the whole band.

⚠ **Cosmetic gap, NOT behavioural — the only thing outstanding on the editor.** The *band* is drawn as
a thin line (`_mkLine`); only the *film* is drawn as a tube. So you grab a small sphere sitting at the
band's end rather than the pipe end itself. Functionally identical today because a band is only
~1–1.3m, so its three spheres span it — but it does not READ as a pipe with grabbable ends, which is
how the user describes it. Fix is small: render each band with `TubeGeometry` at a slightly larger
radius than the film tube, and extend the hit-test from three points to "nearest third of the band's
screen-space length" so the whole pipe is grabbable rather than three dots on it.

**Inherent cost, accepted by the user:** from a top-down view you cannot change height, and from a side
view you cannot move along the view axis. Some moves take two steps — orbit, then drag. That IS the
workflow, not a defect.

## §CPE_PACING_BUILT — derived duration, BUILT and MEASURED 2026-07-27
Every beat is now a measured distance or angle over a stated rate. Frame count follows the plan
(`cinema_maxq` derives `nFrames` from `plan.naturalTotal`) instead of setting it.

| beat | rate | source quantity |
|---|---|---|
| walk | `CINEMA_WALK_MPS` 1.3 m/s | flown path length |
| pull-back | `CINEMA_PULLBACK_MPS` 6.5 m/s | orbitRadius − exit radius |
| dive | `CINEMA_DIVE_MPS` 20 m/s | approach distance, **capped at the envelope** |
| spin | `CINEMA_TURN_DPS` 45°/s | turn angle, **capped at 180°** |
| orbit | `CINEMA_TURN_DPS` 45°/s | one 360° lap = 8.0s |

Beat FRACTIONS are derived-seconds over the natural total, so they no longer depend on `durationSec`
at all — which is what makes "set the total and the whole clip scales uniformly" true by construction
rather than by arithmetic in the editor.

### ⚠ dive distance and spin angle are NOT building properties
They are properties of **where the user was standing** when Alt+C was pressed. Measured on
LTU_AHouse: a **746m approach** and a **522° spin**, producing a 93.6s film of which 37.3s was dive and
11.6s spin. Pacing them raw makes runtime depend on the user's pose, which contradicts the entire
premise. Hence the caps — approach at the envelope, spin at a half turn (the most ever needed to face
anywhere). This distinction is the real finding of the pacing work: **walk and pull-back are building
properties; dive and spin are pose properties; the orbit is a constant lap.**

### Measured derived totals (2026-07-27, warm headless rig)
| building | total | frames@15 | dive | spin | walk | pull-back | orbit |
|---|---|---|---|---|---|---|---|
| Duplex (DX) | **26.1s** | 392 | 2.5 | 0.8 | 9.7 | 5.1 | 8.0 |
| JKR | 32.8s | 492 | 3.0 | 1.0 | 14.3 | 6.5 | 8.0 |
| Terminal | 39.1s | 586 | 3.4 | 0.8 | 19.5 | 7.4 | 8.0 |
| LTU_AHouse (LTU) | **55.4s** | 832 | 6.7 | 4.0 | 23.0 | 13.7 | 8.0 |

### 🔎 WHAT TO LOOK AT NEXT — the totals do not match the ~15s/~40s expectation
Duplex lands at 26.1s against ~15s; LTU at 55.4s against ~40s. **The ratio is right (2.1× vs the
expected 2.7×); the offset is not.** The arithmetic says exactly where it goes: walk + pull-back ALONE
give **14.8s and 36.7s** — almost exactly the expectation. Everything above that is dive + spin +
orbit, and the **orbit's fixed 8.0s lap is the single largest constant on a small building** (8 of
Duplex's 26.1s).
So the open question is not "derived or fixed" — that is settled and built — but **whether the orbit
lap and the arrival belong inside the number the user was picturing.** Three ways to close it, none
picked:
1. The expectation counted only the interior act + recede; the orbit is extra. Nothing to change —
   just report totals split as "film 14.8s + orbit 8s".
2. The orbit lap should be faster on small buildings (rate scaled by envelope rather than a constant
   45°/s).
3. `CINEMA_WALK_MPS` / `CINEMA_PULLBACK_MPS` want tuning together, which moves both endpoints.
**Measure before choosing** — the table above is the baseline to move against.

## §CPE_BANDS_BUILT — implemented and witnessed 2026-07-27 (`feat/cinema-path-editor` @ `5eb69df`)
All of §CPE_BANDS below is BUILT, plus the full-film tube. New file gates:
`witness_cinema_bands.js` **6/6 on Duplex AND Terminal** — these REPLACE G7/G8/G10, which assumed
corner-fillet geometry over free waypoints; that green was re-earned, not carried.
`witness_cinema_path_editor.js` 9/9 both (loose waypoints still supported).
`witness_cinema_path_persist.js` 7/7 against the 3-band schema, round-trip through a real page reload.
`witness_cinema_orbit_v2.js` PASS (regression check on the shipped derived path).

| gate | result |
|---|---|
| B1/B2 bands flown straight at exactly their authored length | err ≤ 2.2e-16, nothing inserted inside a band |
| B3 curve leaves/arrives ALONG each band direction | ≤ 10° at every join |
| B4 connector bow ≤ measured clearance (or the conservative cap where unknown) | bow 0.26–0.29m vs clearance 0.86m |
| B5 no sharp corners, seeded layout | ≤ 12°/frame cap met on both buildings |
| B6 fold | 3 bands → 6 waypoints → flown as authored |
| B7 LOS runs along the band | passes outside the deliberate look-back blend |

### ⚠ THREE LATENT DEFECTS IN SHIPPED CODE, exposed by band geometry — all measured, all fixed
None of these were introduced by bands; bands merely reached them. Each was predicted arithmetically
and then confirmed against a measurement, per the FUNDAMENTAL LAW.
1. **`_cinemaGazeBlend` chose the look-back's turn direction with a PER-FRAME test.** The frame
   `|dYaw|` crossed `CINEMA_TURN_ANTIPODAL_RAD`, `dYaw` moved by 2π and the gaze snapped by `2π × w`.
   At the observed frame `e3=0.906` → `turnW3=0.341` → predicted `2π × 0.341 = 123°`; **measured
   118°/frame**. Fixed by taking the representative of the raw delta NEAREST a per-plan reference —
   continuous, and still lands exactly on the pivot bearing at w=1 (which Beat 4's handoff needs).
   ⚠ A first attempt (freezing the plus-way branch as a per-plan constant) made it WORSE — 178°/frame,
   sustained. Recorded so nobody retries it.
2. **The look-ahead collapse guard measured HORIZONTAL distance only.** Any near-vertical stretch of
   path tripped it while the look-ahead was metres away — just above rather than ahead. Terminal's
   walk-out climbs ~17m with x/z barely moving: target jumped `(-0.80,-6.19,-1.14)` →
   `(-21.82,-25.65,-1.67)`, **113°/frame at t=0.411**. Now a 3D test — what the guard always meant.
3. **The spin's destination bearing came from the next waypoint even with no horizontal baseline.**
   With bands that waypoint is the settle band's own far end, short and near-vertical → **27°/frame at
   the Beat2→3 seam**. Guarded with the same 0.5m the look-ahead guard uses; a normal door-length
   first leg is untouched, so the derived film is unchanged there.

**Net:** seeded band path went 118 → **9.8°/frame** (Duplex), i.e. bands now fly *smoother than the
derived route* (9.8 vs 12.5). Terminal derived measures 11.3.

**Behaviour change to be aware of:** fix (2) alters the DERIVED film on any building whose walk-out
has a near-vertical stretch (Terminal does). That is a defect removal, not a regression — but it is a
visible change, so do not expect frame-identical output to pre-`5eb69df` on such buildings.

### Residual, NOT gated
An adversarial layout (three bands aimed away from each other, forcing the path to double back twice)
peaks at **30.9°/frame** with a total turn of ~365° over 89 frames (mean 4.1). Printed as `INFO` by
the witness rather than gated: a near-reversal genuinely cannot be flown as gently as a normal route,
and pretending otherwise would mean weakening the gate that protects the realistic case. Revisit only
if a real user path hits it.

## §CPE_BANDS — the spec (settled with the user 2026-07-27) — IMPLEMENTED, see above
**Read this before touching the editor again.** Settled in discussion; user quotes inline. Supersedes
the "3 draggable points" shape in §CINEMA_PATH_EDITOR_MODEL — the *data model* (waypoints only, LOS
aim, constant speed) is unchanged; what changes is that each anchor becomes a rigid **band**.

### The idea
> *"Let the first not a point but a band... a stretch say about 5% of the inside... so is the exit...
> by manipulating that, u can have creative curves"*

A point gives position only. A band gives position **and direction** — its two ends are a tangent, and
tangents are what actually shape a curve. That is the whole reason this is worth building.

### The rules (all settled, do not re-litigate)
1. **Three bands, one per existing anchor:** settle, wp1(exit door), stop. No bands added or removed.
2. **A band is a SHORT STRAIGHT segment.** > *"the bands are short straight parts of the path. When
   they are moved their length and straightness does not morph.. keep that steady for now."*
   Rigid. Dragging never bends or resizes one.
3. **Grab zones:** > *"when clicking on one end it pivots.. in the middle it is the whole length moving
   as one."* End = pure ROTATION about the far end (not a resize — length is invariant).
   Middle = translation of the whole band.
4. **Length is therefore NOT draggable** → it needs a number field on the row. The "5% of the inside"
   seed is the actual length, not just a starting size (I initially got this wrong and was corrected).
5. **6 waypoints, folded into 3 rows.** > *"in a way the 3 bands are actually 6 waypoints"* … *"but
   efficiently folded into 3."* The table stays 3 rows. The flown path expands to 6 points at plan
   time, flies, and discards them.
6. **STORE 3 BANDS, NOT 6 POINTS.** `cinema_path` becomes 3 rows of (anchor xyz, direction, length).
   Rigidity then survives save/reload **structurally** — six free points would just be six points, free
   to bend apart on the next session. Safe to change the schema: no building in the wild has the table
   (every restore to date logs `§CINEMA_PATH_RESTORE none`).
7. **Bands are highly movable, and the path must re-form.** > *"user can drag it to a far end, the path
   has to bounce back."* No placement limits. Connector curvature scales with its own span, so a 5m gap
   and a 60m gap both read as one continuous curve.
8. **Authored is authored.** Drag a band through a wall and the camera goes through the wall — the same
   settled doctrine that already governs the derived straight-line route, and it applies more strongly
   to a point placed by hand. Do not add collision fighting.

### Geometry consequences — this REPLACES the corner-fillet approach
- **No rounding INSIDE a band** — that is the straight part; rounding it is the morphing rule 2 forbids.
- **The connector between two bands is tangent-matched at BOTH ends**: it leaves a band along that
  band's own direction and arrives at the next along that one's. A cubic Hermite whose tangents ARE the
  band directions and whose handle length scales with the span. If the connector arrives from any other
  direction you get a kink exactly at the band end — the opposite of the goal.
- The `A.cinemaFan` clearance bound still caps how far a connector may bow. Note it is WEAK outside
  (the fan sees no geometry there, so the conservative cap applies, not a measurement — see §CPE_LIVE).
- `_cinemaRoundCorners` (effects.js) is therefore superseded for the authored path; keep it for the
  derived path or replace both, but do not run it across band interiors.

### Still open on bands
- **Does the camera TRAVEL the settle band during the spin?** Asked, not answered. Travelling it gives
  the drift-and-look that the settled *"no robotic abrupt stop and turn"* ruling asks for, but changes a
  beat that has existing witnesses. My recommendation: travel it, after first confirming no witness
  asserts a stationary spin.
- Dive origin and orbit are still NOT authorable. §CPE_LIVE's complaint is only half closed by the tube
  (visible ≠ editable). Decide later whether they become bands 4 and 5.

### Also asked, also NOT YET BUILT — the full-film tube
> *"the whole flight path must be visible… Render the FULL path as one continuous highlighted curve —
> dive origin → settle → wp1 → stop → orbit… Dragging any of the three must re-derive and re-render
> that whole curve live."* And: *"the flight path should a thicker perhaps blue/yellow pipe depending on
> background colour to contrast."*

Draw it by sampling `plan.poseAt(t)` across t=0..1 — that curve IS the flown film, so it cannot drift
from what the bake flies. Tube geometry, colour chosen against background luminance (yellow on dark,
blue on light), re-checked on background toggle.
**Perf is NOT a blocker — measured, warm:** re-plan 13ms Duplex, 82ms Terminal; sampling 240 poses
0.3ms. (The `§CINEMA_PLAN_MS 456.8` in a live log was a COLD first plan — do not design around it.)

### Engineering risk assessment (asked directly: "is the engineering of the 3 bands an issue?")
Not mathematically. The constrained Hermite is craft, not research. The real risks are process:
1. **G7/G8/G10 are invalidated by construction** — they assume free waypoints + corner fillets. That
   green is RE-EARNED, not carried. Rewrite them for band+connector before claiming the feature works.
2. **Another session owns `_cinemaPathPlan`'s §CINEMA_SPACE block (~L3486-3610)** — keep the footprint
   outside it, as the current implementation already does.
3. **Sequence bands and pacing separately.** Both touch timing; landed together it is impossible to say
   which change moved which number.

## §CPE_PACING — total duration must be DERIVED, not fixed (user, 2026-07-27). MEASURED, ONE DECISION OPEN
Consolidated user ask, three parts:
1. **Interior:** slower constant m/s, re-applied on every edit so dragging always retimes to hold it.
2. **Exterior:** pull back from NEAR to the final orbit distance, rather than starting far.
3. **Total:** falls out of (1) and (2) applied to each building's real geometry — not one fixed number.
   Expectation to CHECK, not to hit: small (DX) ≈ 15s, large (LTU) ≈ 40s.

### The current model is inverted — measured, not asserted
Today total is fixed (360 frames / 15fps = 24s) and speed is whatever makes the derived walk fit
`CINEMA_OUT_SEC`=6s. So **the bigger the building, the faster the camera**: Duplex 2.10 m/s, JKR 3.10,
Terminal 4.22, LTU_AHouse 4.99. Exactly backwards. Frames must become a CONSEQUENCE of duration, not
its cause (`cinema_maxq.js` currently derives duration from `nFrames`).

### Measured geometry (2026-07-27, warm, headless ANGLE rig)
| building | walk pathLen | envelope | near R | final R | pull-back | lap @finalR |
|---|---|---|---|---|---|---|
| Duplex | 12.6m | 50.0 | 11.6 | 45.0 | **33.4m** | 282.7m |
| JKR | 18.6m | 60.3 | 12.0 | 54.3 | 42.3m | 341.3m |
| Terminal | 25.3m | 67.8 | 12.9 | 61.0 | 48.1m | 383.3m |
| LTU_AHouse | 29.9m | 134.6 | 32.1 | 121.1 | **89.0m** | 761.1m |

Duplex→LTU: pull-back 2.7×, envelope 2.7×. The user's 15s→40s expectation is 2.7×. **The ratio is
already present in the real measurements** — nothing needs inventing to produce it.

### The result that decides the design
Taking ONLY the two quantities the user named — interior walk at a constant **1.3 m/s**, exterior
pull-back at **6.5 m/s**:

| | walk | pull-back | sum |
|---|---|---|---|
| Duplex | 9.7s | 5.1s | **14.8s** |
| JKR | 14.3s | 6.5s | 20.8s |
| Terminal | 19.5s | 7.4s | 26.9s |
| LTU_AHouse | 23.0s | 13.7s | **36.7s** |

14.8s vs the ~15s expectation, 36.7s vs ~40s. **These constants were not tuned to hit those numbers** —
they are measured path length and measured pull-back distance over two stated speeds.

### ⛔ THE ONE OPEN DECISION — ask the user first, do not guess
The three beats above are still FIXED: dive 6s + spin 2s + orbit 8s = **a 16s floor**, which alone
exceeds the 15s small-building expectation. Add them back unchanged and totals become 30.8s (Duplex)
and 52.7s (LTU), and the spread collapses from 2.5× to 1.7× — i.e. the expectation is only met if those
three ALSO stop being fixed seconds.
**Question put to the user, unanswered at session end:** do dive / spin / orbit become derived too —
dive paced by its real approach distance, spin by its real turn angle, orbit by a constant angular rate
— or do they stay fixed and the real totals land nearer 31s and 53s?
⚠ Note `CINEMA_DIVE_SEC`'s comment says the dive is deliberately time-boxed and *"never clamped, never
distance-proportional"* (§CINEMA_SIMPLE). Deriving it CONTRADICTS a settled ruling — that is exactly why
this is a user decision and not an implementation choice.

## THE FOUNDATION — read these, do NOT re-derive them
This feature is only cheap because the substrate already exists and is proven. Referenced, not
restated; go to the source for detail.

| what | where | why it matters here |
|---|---|---|
| The plan itself | `viewer/effects.js` `_cinemaPathPlan()` → `{beats:{dive,spin,out,rise}, poseAt(t), pivot, exit, ...}` | Every pose is a pure function of `t`. **Both** consumers — live capture (`effects.js`) and the MaxQ bake (`cinema_maxq.js`) — read this ONE plan, so editing its inputs changes both for free. |
| The editable inputs | same file: `CINEMA_DIVE/SPIN/OUT/RISE_SEC`, `outWp` waypoints, `CINEMA_EYE_M`, `CINEMA_LOOKDOWN_DEG`, the orbit radius band | This is the actual edit surface. Nothing else needs to become editable. |
| The gap to put the dialog in | `§CINEMA_PLAN_MS` — Alt+C already pauses to compute (Duplex ~50–100 ms; Terminal/Hospital 500–750 ms) | No new entry point is needed or wanted. |
| The route renderer to REUSE | `viewer/navigate_find.js:1249 _drawPathHighlight`, `:3274 _renderPathResult` | The Find panel already draws a room route as a line through points; `outWp` is the same shape of data. |
| The persistence pattern to MIRROR | `staffage_instances` — `viewer/scene.js:577 _writeStaffageTable` / `:589 _exportBuildingDb`, `effects.js A._restoreStaffageInstances` | Full round-trip measured working 2026-07-26: `§STAFFAGE_SAVE rows=8` → `§STAFFAGE_RESTORE rows=8` → `restored=8`. See `prompts/STAFFAGE_WALKABLE_PLACEMENT.md §STAFFAGE_PERSIST`. |
| Recent path fixes that must not regress | `prompts/PHOTOREAL_STILL_RENDER.md` §CINEMA_TURN_SLERP (#1018) and §CINEMA_DAMPING_BLEED (#1020) | Their witnesses (`witness_cinema_exit_breathe.js`, `witness_cinema_damping_bleed.js`) must stay green after any change to the plan or its consumers. |

## Scope guardrails (the "simplest fastest" requirement is the spec, not a preference)
1. **No new icon, no new panel, no new keybinding.** It rides the Alt+C press that already exists.
2. **OK must behave exactly like today** — same film, same beats. The default cost of this feature is
   one click. If OK changes the output at all, the feature is wrong.
3. **Reuse the Find route renderer.** Do NOT build a second path-drawing system, and do NOT build an
   in-viewport 3D drag gizmo. A dialog with a 2D graph is the deliverable.
4. **An edited path is AUTHORED data, not derived.** Under the prime rule (EXTRACT OR COMPILE ONLY) it
   must be STORED, never re-guessed: a `cinema_path` table (beats + waypoints + eye height + tilt)
   written by `_exportBuildingDb()` and read on load. Reuse the staffage pattern; do not invent a
   second persistence mechanism.
5. **Persistence is an EXPLICIT action — "Save this path" (user directive 2026-07-26).** Adjusting and
   proceeding is *ephemeral*: this film, this press, nothing written. Only "Save this path" marks the
   edit as the building's stored path. Mechanically this mirrors staffage exactly — the action stages
   the path into the in-memory DB and the user's normal `Ctrl+S` carries it to the file, the same way
   `_writeStaffageTable()` runs at `_exportBuildingDb()` time. Do NOT add a second save route, and do
   NOT auto-persist on adjust: a user experimenting with waypoints must be able to walk away without
   having changed the building.
   Dialog affordances, therefore: **OK** (proceed, unchanged) · **Save this path** (stage the edit) ·
   **Cancel** (abandon, no recording).

## Witness claims (spec-first — write these before any implementation)
- **G1** OK-without-edit is a no-op: beats and sampled `poseAt(t)` are byte-identical to a run with
  the dialog bypassed. (The one gate that protects the zero-friction default.)
- **G2** a waypoint edit changes the sampled path at that waypoint and nowhere else.
- **G3** a camera-Z edit changes eye height by exactly the amount asked, on every affected beat.
- **G4** round-trip: edit → **Save this path** → `_exportBuildingDb()` → `_openDbBytes()` → the SAME
  edited path is in force, proven by SAMPLED POSES, not by the table merely existing.
- **G4b** ephemerality: edit → proceed WITHOUT "Save this path" → export/reopen → the plan is the
  unedited derived one. Adjusting must not silently mutate the building.
- **G5** control: on a DB with no `cinema_path` table the plan is the unedited derived one.
- **G6** §CINEMA_TURN_SLERP's and §CINEMA_DAMPING_BLEED's witnesses stay green.

## §CINEMA_ATTIC_PICK — the dive target ⛔ REASSIGNED, NOT THIS SESSION'S (user, 2026-07-26)
> **"ignore the default attic issue — as another session is handling."**
>
> The "do this FIRST" ordering below is therefore VOID for this file's lane, and the §CINEMA_SPACE block
> in `_cinemaPathPlan` (~L3486-3610) is **another session's working set** — the path-editor work must not
> edit it. Kept below only as the reference record of what that other lane owns. The editor is designed
> so it does not care which room the picker chooses: the settle point is just waypoint 1, draggable.

<details><summary>original folded-in brief (now another session's)</summary>

**Do this FIRST, before the dialog.** Not a priority argument to re-run — the reasoning is simply that
this editor's value is inversely proportional to how often it is needed: a good default makes the
dialog a one-click OK (the differentiator), a bad default makes editing mandatory (just a worse
conventional tool). Fixing the default first also gives the dialog's G1 no-op gate something worth
being a no-op about.

**Symptom:** the dive "goes for the attic" — the camera settles in a top-level/roof void instead of a
real occupiable space.

**What the picker actually does today** (`viewer/effects.js` §CINEMA_SPACE, ~line 3386): ranks ALL
room-graph nodes with rects by `area / (1 + dCtr / (envelope*0.5))` — "largest space nearest the
centre" — takes the single top-ranked one, applies ONE enclosure sanity check, and falls to
bbox-centre if that fails. Live JKR evidence from the user's own log:
```
§CINEMA_SPACE cand=0pNy6pOyf7JPmXRLgxs3sW area=135.2 enclosed=0%   mep=0% chosen=false
§CINEMA_SPACE cand=0BTBFw6f90Nfh9rP1dlXr2 area=27.7  enclosed=100% mep=0% chosen=true (rank=2, skipped 1 disqualified above)
```
So the enclosure gate DOES fire and DOES skip candidates — meaning an attic that reads `enclosed=100%`
will win on area alone. Start by capturing the same `§CINEMA_SPACE` lines on a building that actually
exhibits the attic dive; do not theorise from JKR, which chose a 27.7 m² room.

**⚠ ANTI-DRIFT — do NOT "fix" this by adding floor-level weighting.** That was explicitly ruled out by
the user on 2026-07-20 and the ruling is quoted in the code's own comment: *"abandon the 'next largest
room' idea... It is disastrous for sure. Just back to original 'go to largest space within 4 sec'."*
There is deliberately **no** floor-level weighting and no multi-candidate search. A fix must come from
a different angle — e.g. a stronger occupiability probe (headroom, real floor beneath, reachability
from the room graph) — not from re-ranking by storey. Re-read that comment block before touching it.

</details>

## Out of scope — each has its own session, do not fold them in here
- `§STAFFAGE_PAX_REJECT tried=72 placed=3` with all rejection counters 0 — 69 unattributed
  rejections, the reason only ~3 figures ever appear. `prompts/STAFFAGE_WALKABLE_PLACEMENT.md`.
- **Scene jumping on reopen** — NOT reproduced through `_openDbBytes()` (camera 0.00 m, target
  0.00 m). Needs the user's actual repro route first.
- **D2 walk-out corner whip** — 19.8°/frame on current main, printed by the witness, not gated.
- **JKR path-crossing** — `prompts/Modeller/DISC_Walker/VIEWER_FIND_PANEL_ROOM_ACCURACY.md` §18.

## Two small already-diagnosed items to land alongside (both root-caused, neither started)
- **Restore staffage on LOAD**, not on the first Alt+P press. The round-trip works; the trigger is
  wrong. Details + measurements: `STAFFAGE_WALKABLE_PLACEMENT.md §STAFFAGE_PERSIST` (ANSWERED section).
- **MP4 loses one pixel row** on odd-height canvases: frames grabbed at `w×h`, encoder configured at
  `(w & ~1, h & ~1)`, drawn 1:1. `cinema_maxq.js` `_stitchMp4`.

---

## §CPE_OK_CRASH — OK after an edit kills the bake (user live run, Hospital, 2026-07-27)

**SPEC FIRST. Do not implement before reading this section.** Reported by the user immediately after
§CPE_SCREEN_PLANE landed: *"satisfied the 'pipe band 2 waypoints with mid translatable' working..
however hits error when OK the editor box"*. The grab model itself is fine — the drag worked, the
editor closed, the plan re-derived. The bake is what died.

### The user's own console, the three lines that matter
```
§CPE_CLOSE action=ok edited=true total=54.4s
§CPE_LOCKS re-claimed for the bake (maxqActive=true)
§CINEMA_PATH_EDIT authored waypoints=6 (derived route replaced) orbitScale=0.991 orbitDY=-0.90m
§MAXQ_WAKELOCK acquired (screen stays awake for the bake)
[S274] §ERR_PROMISE Cannot read properties of undefined (reading 'length')
Uncaught (in promise) TypeError: ... at Object.start [as startMaxQualityOrbit] (cinema_maxq.js)
```
The re-plan SUCCEEDED — `§CINEMA_PATH_EDIT authored waypoints=6` is the edited path being flown. The
throw is one line later, in the log statement that reports what just happened.

### Root cause — a stale log line, not a stale path
`cinema_maxq.js:507` (the `§CPE_APPLIED` line) reads `_cpeRes.override.waypoints.length`. That field
**no longer exists**: §CPE_BANDS changed `cinema_path_editor.js` `_buildOverride()` to return
`{ bands, diveSec, spinSec, outSec, riseSec, _total, _naturalTotal, _scale, _pathLen }` — bands, not
waypoints. `effects.js` was ported (it reads `ov.bands` and expands via `_cinemaBandWaypoints`, which
is why the plan is correct); the one consumer in `cinema_maxq.js` was not.

**Why review missed it and the witnesses missed it:** `witness_cinema_bands.js` drives
`A.cinemaPathPlan(dur, override)` directly — the plan seam, not the bake. Nothing in the suite ever
walks the actual user path *editor OK → `start()` continues → bake*. The one line of code between
those two is exactly where the defect sat. (This is `feedback_test_real_user_path_not_seams` charging
its rent a second time.)

**Blast radius: total.** Every edited path is unbakeable on shipped main; only the untouched-OK
guardrail-2 path survives, because that branch never dereferences the override. §CPE_SCREEN_PLANE
therefore shipped a feature whose entire purpose — edit, then record — cannot complete.

### The fix
Report the count the plan actually flew, derived from the shape the editor actually returns:
`bands.length * 2` for a band override (§CPE_BANDS rule: 3 bands fold to 6 waypoints), falling back
to `waypoints.length` if a legacy loose-waypoint override is ever handed in, and to `?` if neither.
No behaviour change to the path, the plan or the bake — this is a log line, and it must stay one.

### Witness — `witness_cpe_ok_bake.js`, the missing user path
Runs Duplex + Terminal. Each gate names what it proves or disproves:
- **K1 (the reported crash)** — open the real editor, KEY a real band edit (the `y` field, the same
  state mutation a drag makes), click the real `#cpe-ok`, and let `start()` continue. PASS = zero
  page errors and the run reaches the bake. FAIL = the `TypeError` above. This gate is RED on
  `origin/main` by construction; if it is not, the fix is not the fix.
- **K2 (the log tells the truth)** — `§CPE_APPLIED waypoints=N` must EQUAL the `N` in the
  `§CINEMA_PATH_EDIT authored waypoints=N` printed by the plan it just built. Not crashing is not
  enough: a hardcoded `0` would also not crash and would lie in every future bug report.
- **K3 (guardrail 2 unbroken)** — OK with NO edit still reaches the bake and still logs
  `§CPE_APPLIED none`. Proves the fix did not move the crash into the other branch.
- The bake is cancelled at frame 0 (`startMaxQualityOrbit()` re-entry sets `_cancel`) so the gate
  costs seconds, not the full cook. Reaching `§MAXQ_IDB_READY`/`§MAXQ_CANCEL i=0` IS "reached the
  bake" — everything past line 507 that the crash denied.

### Measured — RED then GREEN, same rig, same gates (2026-07-27, `fix/cpe-ok-override-crash`)
Headless ANGLE/SwiftShader, local server on 8402, real editor DOM, real `startMaxQualityOrbit`.

**On `origin/main` (unpatched), Duplex — 1/3:**
```
FAIL K1  editorOpened=true edit=-0.47→1.03m reachedBake=false pageErrors=1
         ← THE REPORTED CRASH: Cannot read properties of undefined (reading 'length')
         applied: (§CPE_APPLIED never printed)
FAIL K2  §CPE_APPLIED waypoints=n/a vs §CINEMA_PATH_EDIT authored waypoints=6
PASS K3  untouched OK reaches the bake — the crash only ever hit EDITED paths
```
**With the fix — 3/3 Duplex AND 3/3 Terminal:**
```
Duplex    K1 reachedBake=true pageErrors=0 | §CPE_APPLIED total=26.5s frames=26 waypoints=6
Terminal  K1 reachedBake=true pageErrors=0 | §CPE_APPLIED total=40.1s frames=40 waypoints=6
both      K2 §CPE_APPLIED waypoints=6 == §CINEMA_PATH_EDIT authored waypoints=6
both      K3 §CPE_APPLIED none — derived plan unchanged
```
K3 passing RED is the diagnosis in one line: only the edited branch dereferenced the dead field.
Regression: `witness_cinema_bands.js` **6/6** on Duplex, unchanged.

### The bug reporter half — NOT this branch
The user's next sentence (*"Error submission hits error in Gmail should have send via GH"*) is the
same defect class — the mailto path had no URL length cap while the GitHub path has capped at 8000
since §S280 — but it is **owned by another session and already shipped as PR #1028** (cascades the
log-line count until the URL fits ~1800 chars; measured 8202 → 1561). Do not re-fix it here.

---

## §CPE_PANEL_DRAG — the editor panel is draggable (user, 2026-07-27)

User, after firing a full run: *"happy with how editor behaves.. but can u make the editor panel
draggable"*. The panel is anchored `position:fixed; top:60px; right:12px` and covers a fixed 412px
strip of the viewport — exactly where the bands often sit once the user has orbited to face the axes
they want to drag in (§CPE_SCREEN_PLANE makes "turn to face the plane" the normal gesture, so the
panel WILL end up over the handles).

### Reuse, do not invent — this is model item 14's rule applied to the panel itself
`A._makeDraggable(el)` (`measure.js:13`) is this viewer's ONE panel-drag utility: ~10 panels already
use it (`navigate_find`, `panels`, `issues`, `clash_matrix`, `grid_overlay`, `print_sheet`, `hba_lens`,
`measure` ×2). It grabs the top `el._dragStrip` px (30 desktop / 50 mobile, overridable), arms on
pointerdown but only captures after 4px of movement (so a stationary tap still reaches child
controls), and switches the panel from its right-anchor to free `left/top`. **Do not write a second
one**, and do not edit `_makeDraggable` — it is shared by every one of those panels.

Applied here as: the header div gets `id="cpe-title"` + `cursor:move`, and `_dragStrip = 36` so the
grab zone is exactly the header — the rows below it carry number inputs whose keyed edits must never
fling the panel.

### Why this cannot disturb the path — the thing actually worth gating
`_wire()` installs `pointermove`/`pointerup` on **window** (capture phase). Those are the handlers
that move a band. They are guarded by `_state.drag`, which is only ever set by `h.down` — and `h.down`
is bound to **the canvas alone**. So a header drag cannot become a band drag. That is an argument;
the witness makes it a measurement.

### Scope note, stated not smuggled
Beyond the literal ask, the dragged position is **remembered for the session** (module-scope
`_panelPos`, re-applied on the next open). A panel that forgets where you put it every time Alt+M
re-opens it is a half-feature. It is three lines, it is gated (D4), and it is called out here rather
than slipped in. Nothing is persisted to the DB — this is not `cinema_path` state.

### Witness — `witness_cpe_panel_drag.js`, Duplex + Terminal
- **D1 the ask** — pointerdown on `#cpe-title`, move by a known (dx, dy), pointerup: the panel's
  `getBoundingClientRect()` must have moved by EXACTLY that delta (±1px). Proves the drag is wired
  and 1:1 — a scaled or offset drag would still "look draggable" and be wrong under the hand.
- **D2 the risk** — that same drag must leave the path untouched: **no `§CPE_DRAG` line**, and band
  centres bit-identical before and after. Disproves the window-level-handler collision above.
- **D3 the grab zone** — an identical drag started on a ROW (below the strip) must NOT move the
  panel. Proves keyed edits and row clicks can't fling it.
- **D4 the scope note** — after close and re-open, the panel is back where the user left it.

### Measured — 4/4 Duplex AND 4/4 Terminal (2026-07-27, `feat/cpe-draggable-panel`)
```
D1  panel (976,60) -> (1156,180) = moved (180.0,120.0), asked (180,120)
    §CPE_PANEL_DRAGGABLE handle=cpe-title strip=36 at default anchor
    §CPE_PANEL_MOVED dx=180 dy=120 left=1156 top=180 (remembered for this session)
D2  §CPE_DRAG lines 0 -> 0 | band centres IDENTICAL before and after
D3  panel (1156,180) -> (1156,180) = moved (0.0,0.0) when the drag starts on a row
D4  left at (1156,180), re-opened at (1156,180)
```
**A witness bug worth recording, since it will bite the next puppeteer gate here:**
`page.evaluate(() => window.APP.startMaxQualityOrbit(...))` with a CONCISE arrow body RETURNS the
bake's promise, so `evaluate` waits for the entire cook and dies on `protocolTimeout` after 180s —
it looks exactly like a hung viewer. Use a BLOCK body (`() => { ...; }`) so nothing is returned.

---

## §CPE_SAVED_PATH_LIFECYCLE — "when Saved the edited path, how do we open it?" (user, 2026-07-27)

Answered from the code, end to end. **There is no "open" action, by design — a saved path is not a
document you load, it is state the plan picks up.**

| step | what actually happens | log line |
|---|---|---|
| "Save this path" | `cinema_path_editor.js:664` → `A.stageCinemaPath(_buildOverride())` sets `A._cinemaPathEdit` **in memory only**. Editor stays open — staging is not closing. | `§CINEMA_PATH_STAGE` |
| **Ctrl+S** (Save Building; `scene.js:2136`, also the panel item at `panels.js:1302`) | `A.saveModelDb()` → `_exportBuildingDb()` → `_writeCinemaPathTable()` drops+rewrites a `cinema_path` table — one row per band (IFC anchor, unit dir, length) plus the beat seconds. | `§CINEMA_PATH_SAVE bands=3 total=…s` |
| next Alt+M on that file | `A.cinemaPathPlan()` calls `_cpeLoadFromDb()` **lazily at the first plan** — before anything can observe the derived route — and rebuilds the bands. The editor then opens on the user's path. | `§CINEMA_PATH_RESTORE bands=3 total=…s — authored path in force` (or `none (no cinema_path table) — derived path`) |

### Two consequences the user needs stated, not discovered
1. **It is per-FILE, and a building served from OCI is read-only.** Ctrl+S produces a downloaded
   `.db` via Save-As; the path lives in that local copy. Loading the OCI URL again gets the derived
   path — nothing writes back to the bucket. (Same shape as `staffage_instances`; not a new rule.)
2. **⛔ No UI drops a saved path.** `A.clearCinemaPath()` exists and logs `§CINEMA_PATH_CLEAR`, but
   NOTHING calls it — console only. So once a file carries `cinema_path`, every Alt+M authors from it
   until it is cleared by hand. **Named as a gap and offered to the user; not built unprompted.**
   If picked up: a "Use derived path" button in the editor next to "Save this path" is the whole job.

### One defect found while reading this path — FIXED
`A.stageCinemaPath` logged `waypoints=' + (ov.waypoints ? … : 0)` → a flat **`waypoints=0` on every
save**. Same stale-field species as §CPE_OK_CRASH (§CPE_BANDS replaced `waypoints` with `bands`), but
GUARDED, so it never threw and therefore never got noticed. Now reports `bands=3 waypoints=6` and says
outright that staging is not saving. Worth noting as a pattern: the crash was found in one afternoon
because it threw; its silent twin sat in the same feature untouched. **When a field is renamed, grep
every consumer — the guarded ones are the ones that survive to lie later.**

---

# 2026-07-27 (evening) — three items from the user's second live Hospital run

**RESUME HERE.** Two defects + one agreed-for-later design, all three raised by the user after a full
edit→OK→bake on Hospital. §CPE_PREVIEW_DIVERGENCE is the lead for both defects and is ALREADY
MEASURED in the user's own pasted console — read it first.

## §CPE_PREVIEW_DIVERGENCE — the baked film is NOT the film the user authored ⚠ MEASURED, NOT FIXED

The user's report for item 1 is *"It has a frame jump"*. Before touching curvature, look at this,
because it is bigger than a corner: **the plan built when the editor closes differs from the plan the
user was editing against — in the pivot, the dive and the spin.** Straight from their console, same
session, minutes apart:

| | while editing (every live re-plan) | after OK, the plan that BAKES |
|---|---|---|
| `§CINEMA_PIVOT src` | `arc-bbox-centre` pivot=(-7.3,-2.8,18.0) | **`controls-target(plausible)`** pivot=(0.0,0.0,-0.0) |
| `§CINEMA_DIVE` | `diveDist=14.2m` yaw0=-163.2° pitch0=4.2° | **`diveDist=77.2m`** yaw0=90.2° pitch0=-48.1° |
| `§CINEMA_SPIN` | `dYawRawDeg=11.4 class=already-facing(no-spin) finalSpinDeg=0.0` | **`dYawRawDeg=118.1 direct-turn finalSpinDeg=118.1`** |
| `§CINEMA_EXIT facingDot` | `0.980` | **`-0.471`** |
| `§CINEMA_PACING` | `natural=99.1s = dive 2.5 + spin 0.8 + walk 71.2 + pullback 16.7 + orbit 8.0` | `natural=99.9s = dive 3.9 + **spin 2.6** + walk 71.2 + pullback 14.3 + orbit 8.0` |

**The user previewed a film with no spin at all and got one that turns 118° at the start.** This is
not a subtle regression — it is the first two beats of every edited film. It reproduces in the
EARLIER Hospital run too (preview `arc-bbox-centre`/dive 25.4m/spin 45.8° → bake
`controls-target`/dive 62.8m/spin 107.8°), so it is not a one-off.

**Lead, to be confirmed not assumed:** `finish()` restores the camera to `_state.camSave` and calls
`a.controls.update()` before resolving. The pivot picker then sees a DIFFERENT `controls.target` than
the one in force during editing and switches source from `arc-bbox-centre` to
`controls-target(plausible)` — pivot (0,0,-0) is suspiciously the origin, i.e. a target that was never
re-aimed. Everything downstream (dive distance, `facingDot`, the spin) is derived from the pivot, so
one wrong branch moves all of them together, exactly as the table shows.

**Verify like this, numerically:** capture `A.controls.target` + `A.camera.position` at editor OPEN,
at every live re-plan, and immediately before the post-OK `cinemaPathPlan` call; log them as one
`§CPE_PIVOT_TRACE` line. If the target at bake ≠ the target during editing, that is the defect and
the fix is to plan the bake against the SAME pivot input the editor previewed — not to re-derive it.
**Gate: `§CINEMA_PIVOT src` and `diveDist` must be identical in the last editing re-plan and in the
post-OK plan.** Until that is true, no amount of corner smoothing will make the film match the preview.

## §CPE_EVEN_TURN — item 1 proper: no sharp turns, no camera discontinuity

User: *"if the pipe is facing another way and then ensuing path is a sharp turn, it has to be even
curved out to never have any sharp sudden turns. And the ensuing frames connected do not jump the
camera pos/pov that breaks continuity."*

Two separate obligations, gate them separately:
1. **Position C1** — the flown curve must not kink at a band join. `§CPE_BANDS` B3 already gates
   tangent continuity, so the mechanism exists; what the user hit is the connector being *allowed
   too little room to turn*. Their log: `§CINEMA_BANDS ... maxBow=2.13m **unmeasuredJoins=2/2**` —
   BOTH connectors fell back to the conservative no-hit cap (`nudgeCap=3`) because the fan measured
   nothing, so a hard direction change had to be crammed into a bounded bow. **That is the knob**:
   when clearance is genuinely unknown, a conservative bow is the safe choice for a WALL and the
   wrong choice for a TURN. Decide what an unmeasured join should do — measured clearance stays
   authoritative wherever the fan does return a hit.
2. **Gaze C1** — the per-frame camera turn rate. The existing number to beat is the ungated
   **D2 walk-out corner whip: 19.8°/frame**, and `witness_cinema_bands.js` B5 caps the seeded layout
   at 12°/frame (adversarial layout measured 12.1). The user's edited layout is closer to adversarial
   than to seeded, which is consistent with a visible jump.
**Gate:** peak deg/frame AND peak metres/frame across the WHOLE film (not just the walk) on a
user-shaped edited path, with the §CPE_PREVIEW_DIVERGENCE fix in place — a 118° phantom spin would
otherwise dominate any number this gate produces. Report both peaks and where in `u` they fall.

## §CPE_PACE_LOS — item 2: even speed inside, brake only for what is actually ahead

User: *"too slow and thus gives uneven speed. It has to maintain same speed inside building and apply
brake when too near or facing an object not to crash into or zoom past but slows down just abit not
overdoing."*

**Measured:** `CINEMA_WALK_MPS = 1.3` (`effects.js:3319`) is a flat constant with no proximity term
at all. Their edited walk was **92.5 m ⇒ 71.2 s of the 99.5 s film (72%)** at that fixed pace, and
1492 frames — at the ~4.6 s/frame their §MAXQ_FRAME reported, ≈1.9 hours of cook. The runaway bake
they saw is a straight consequence of the pace, not a separate bug.

**The brake the user is describing ALREADY EXISTS — do not invent a second one.** `tour.js:1111-1165`
carries §INTERIOR_PACING / §INTERIOR_PACING_LOS, settled with this same user on 2026-07-25/26:
- `_invPace(d, ref, min)` — *"the simple inverse formula"*, far ⇒ fast, near ⇒ slow;
- `_losPace(pts, i)` — a SINGLE forward raycast along the leg via the already-exposed
  `A.cinemaLookDist`, `REF=3m` reads as neutral — chosen precisely because an omnidirectional fan-min
  braked for furniture off to the side (the courtyard bug);
- **`PACE_SWING = 1.6`, ONE knob**, symmetric: `MIN=1/1.6`, `MAX=1.6`. This is literally the user's
  *"slows down just abit not overdoing"*, already tuned with them across three live-review rounds;
- `_paceBuildRemap` redistributes time along the path AND rescales the total via `meanFactor`.
It is wired to Fly Tour's flyPath/moveTo/orbit and **not** to the cinema walk-out.

**The work:** raise the cinema base pace (1.3 m/s is a literal pedestrian and the user has now twice
called the result too slow), then apply the EXISTING remap to the walk beat. Two constants, one
reused module — not a new pacing system.
### ⚠ AMENDMENT (user, same evening) — the signal is SCENE BUSYNESS, not just distance ahead
> *"The simple rule is scene busy or noise.. if too much is happening, slow down"*

**This supersedes a pure-LOS reading of the section above.** LOS distance answers *"am I about to hit
something"*; it does NOT answer *"is there a lot going on in this shot"*. A camera crossing a wide but
densely furnished plant room is not close to anything and still deserves to slow down, because there
is something to look at. Distance-ahead alone would fly it.

**Do NOT resolve this by bringing back the omnidirectional fan-MIN.** That was tried and retired for
cause (§INTERIOR_PACING_LOS, 2026-07-26 — a courtyard traversal read slow because a low wall or a
piece of staffage off to the side tripped the min). The user's rule is a **DENSITY/COUNT**, not a
MIN, and the two fail differently: a min brakes for one nearby thing; a density does not fire in an
empty courtyard at all. Record this distinction — it is the reason this is not a re-litigation.

**Deterministic candidates only** (PRIME RULE: the same building must produce the same film on any
machine):
- **fan hit FRACTION** — `A.cinemaFan` already casts 32 rays and is the project's blessed
  clearance source. `hits within R / 32` is a density, costs nothing extra, and is already computed
  at each sample. Strongest candidate.
- **elements in the view frustum** at the sample pose (count of instances/GUIDs) — the most literal
  reading of "how much is happening", deterministic from the DB, but needs a real cost measurement
  before it goes in a per-sample loop.
- **⛔ NOT frame time / FPS / DLOD flip counts.** They measure the MACHINE, not the scene: pacing off
  them makes the same building bake a different film on a different GPU. Tempting because
  `§FPS_MODE`/`§DLOD_TICK` are right there in the log — and wrong for exactly the reason the prime
  rule exists.

**Combine, don't replace:** forward LOS keeps its job (never crash into or zoom past what is directly
ahead); busyness is the second term for "a lot is happening here". Both feed ONE `PACE_SWING`-style
knob so re-tuning stays a single number, per the user's standing *"every edit, it is just a single
number change"*.
**Gate this too:** report per-sample busyness alongside speed, and show that the slow samples are the
busy ones — the number, not the impression.

**Gate:** on an edited path, (a) speed in open interior is CONSTANT within the ±`PACE_SWING` band —
report min/mean/max m/s, not a "feels right"; (b) the slow samples coincide with genuinely low
`cinemaLookDist`, i.e. the brake fires for what is AHEAD, not for anything nearby; (c) the resulting
frame count for the user's Hospital edit lands in minutes, not hours — report it.

## §CPE_IDB_PATH_STORE — agreed, spec-only, NOT NOW (user, 2026-07-27)

User: *"on save it goes to DB.. ok fair for now.. later should be its own in IndexDB first separate
table of saved waypoints if u agree with the idea spec it first for later once we cleared this big
pathing achievement."*

**Agreed, and for a reason the current design already exposes** — see §CPE_SAVED_PATH_LIFECYCLE:
today a saved path can only live inside the building `.db`, so (a) a building served read-only from
OCI can never keep one without the user downloading a private copy, (b) one file holds exactly ONE
path, (c) saving a path means rewriting a 260 MB binary. An IndexedDB store fixes all three, and it
is the same shape the viewer already uses for `bim_ootb_logs` and `bim_ootb_cinema_maxq`.

Sketch, to be settled with the user when it comes up — **do not build from this**:
- store `bim_ootb_cinema_paths`, key `buildingId + name`, value = the band override + `_total` +
  beat seconds (the exact `_buildOverride()` object, IFC-space like `cinema_path` already is);
- MANY named paths per building ⇒ the editor grows a small list: Save as… / Load / Delete;
- the `cinema_path` TABLE STAYS as the portable/exchange format — IndexedDB is the working store,
  Ctrl+S remains how a path travels with the file to someone else. Read order at plan time:
  staged override → IndexedDB (if a path is selected) → `cinema_path` table → derived;
- this also gives `A.clearCinemaPath()` a home in the UI, which §CPE_SAVED_PATH_LIFECYCLE flags as
  missing today.
**Open question for the user when this starts:** should a path selected from IndexedDB auto-apply on
load, or only when picked from the list? (Today the DB table auto-applies, silently.)
