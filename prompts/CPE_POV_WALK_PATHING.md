# ⚠ DO NOT REMOVE — scope: next-stage exploration only, no implementation yet. Read the log after every run.

# CPE — Movie Pathing by POV Walk (next stage, one-liner)

## ✅ §CPE_WALK_EDIT_V1 SHIPPED (2026-08-07) — PR bim-ootb#1243 MERGED
Sonnet agent implementation, watchdog-verified from the witness log (not the report): 28 gates
(31 PASS lines, Duplex + SampleHouse) — listener isolation proven by dispatched events
(G-WALK-ISOLATE-1/2/3), snap maths numeric (band centre bit-identical to walked pose,
lookAt=pos+facing·10m fallback exact, s∈[0,1], replanMs 38–68), TM lock/restore, freeze
overlay on/off, finish() force-stop. §CPE_SEAM_CONTINUOUS did NOT fire. Diff = seams-only:
`viewer/cpe_walk.js` (new), `cinema_path_editor.js` (panel button, `_walkSnap`, `_walkMount/
_walkUnmount`, finish() guard), `viewer.html`, `sw.js` (precache + v958). Zero edits to
effects.js/time_machine.js/schedule_*/main.js. Agent improvement over the brief: no per-pose
markDirty — walk drives B's scissor pass in its own rAF chain, so the main view stays at 0
frames while walking (repaints only at mount/snap-wow/unmount). Worktree pruned post-merge.
Remaining (unwitnessed by design, needs a human hand): live pointer-lock feel on a real
building — first real-use feedback goes in a new dated section here.

**§CPE_WALK_HALLWAY witnessed (2026-08-07, user ask: "walk along HHS Office or Clinic hallway") —
16/16 PASS on HHS_Office_Federated (6.8k elems) + Clinic (16k elems),** run against merged main
(`1165d03`) in a throwaway worktree, hallway EXTRACTED from each DB (longest aligned same-storey
door pair: HHS "Level 1" 54.75m run / 0.00m cross-offset; Clinic "First Floor" 52.69m / 0.65m).
Three snaps at u=0.25/0.5/0.75 walking the line: centres bit-identical to walked poses; insertion
fractions strictly monotonic (Clinic's DECREASE — s=[0.537→0.485→0.426] — is correct: ordering
follows the PATH's own traversal direction, which there runs opposite the walk; direction-agnostic
determinism is the guarantee, now asserted as such); mid-hall perpendicular facing raycast-HIT a
real wall (HHS 0.041m, Clinic 2.235m — the in-building HIT path proven, complementing the shipped
witness's 500m-out MISS/fallback proof); along-hall facings all real HITs too (1.6–15.6m); replanMs
155–234 on these mid-size buildings (vs 38–68 small residential); §CPE_SEAM_CONTINUOUS seamGapDeg =
0.000 in ALL six pin-bearing replans (landmine silent — note its log family also prints routine
openDeg/handoffYaw lines; gate on the parsed seamGapDeg value, not tag presence). Witness:
scratchpad `witness_cpe_walk_hallway.js` (not committed — one-off coverage run; promote alongside
`witness_cpe_walk_edit.js` if hallway coverage should become repeatable).

**Coverage caveat (2nd watchdog pass, 2026-08-07):** the witnessed run covered TWO buildings —
log tail `PASS Duplex (14/14)` + `PASS SampleHouse (14/14)` — but `witness_cpe_walk_edit.js:28`
defaults to `BLDS || 'Duplex'`, so an env-less rerun regresses to Duplex-only. Both witnessed
buildings are small residential; **Hospital-class is unwitnessed** (where replan is 291–1218ms
and the DLOD flip-storm lives — same gap shape as DEQ_V1's large-building-only caveat). Named
follow-on for any next walk-edit session: run `BLDS=Duplex,Hospital_3` once and record the
replan/snap §-numbers here before claiming large-building fitness.
→ **CLOSED same day: Hospital 8/8 PASS** (87.05m Level-1 hallway run, cross-offset 1.26m;
replanMs 975/1160/1474 — the known §CPE_REPLAN_SLOW scale, within the 5s budget; seamGapDeg
0.000 ×6; perpendicular facing raycast-HIT at 18.469m — a real hit into a larger adjacent
space, which taught the witness its `<10m` wall assumption was residential-shaped: the gate now
asserts hit-vs-exact-10m-fallback, distance-agnostic). Editor-open needed a 600s budget under
swiftshader (300s timed out with zero diagnostics — witness now dumps the page's §-console tail
on INFRA-ERROR). **Witness promoted**: `witness_cpe_walk_hallway.js` committed via PR #1246
(watchdog ask), DB preconditions documented in its header.

### §CPE_WALK_CHROME_POC — live-Chrome Duplex run (2026-08-07, user-instructed visual PoC)
Real Chrome (user's own browser, real GPU, real gestures), localhost worktree serve. Confirmed
live, §-logs + screenshots: mount/unmount clean; freeze overlay captured with B's hole
(`§CPE_WALK_FREEZE_CAPTURE w=1483 h=724 hole={x:16,y:457,w:300,h:190}`); B renders alone while
main is frozen; walk rAF ticking (§CPE_WALK_POSE); WASD moved position and mouse-look swept
yaw/pitch under REAL pointer lock; Esc/lock-loss auto-stopped the walk cleanly (editor stayed
open — the review handoff below). Two real findings, both resolved there-and-then:
1. **Pointer lock requires a real user gesture** — a synthetic `.click()` mounts but lock is
   denied (`NotAllowedError`); a trusted click locks fine. Consequence for tooling only (the
   witness's synthetic path can never test lock itself); product UX unaffected.
2. **The viewer's incidental DB-404→OCI fallback carried the run** — worktree lacked the
   untracked Duplex binary; `§DB_404_OCI_RETRY` fetched it transparently.
Also user-observed live: pointer lock captures the OS cursor screen-wide (their mouse was
"taken over") — which surfaced the §CPE_WALK_SHOES_BTN ruling below, and the confirmed answer
that walk-exit → un-freeze + stick-editor re-wire + repaint is automatic and symmetric
(cpe_walk.js `_onLockChange`→`stop()`; witnessed G-WALK-ISOLATE-3/FREEZE-DROP/TMLOCK-2).

### §CPE_WALK_SHOES_BTN — walk button on B's frame header (2026-08-07, SHIPPED PR #1246)
User: *"The Walk button (shoes icon preferred) should be at the POV frame and the navigation
within that pov frame while the rest supposedly frozen."* Freeze/B behavior already matched;
the button did not. Ruling (advisor-confirmed option A): shoes icon ON B's frame header,
pointer-lock unchanged — lock hides the OS cursor entirely, so screen-wide capture is an
invisible implementation detail; drag-confined-look was declined (limited rotation per gesture,
constant re-grabbing). Implementation: Lucide `footprints` inline SVG (stroke:currentColor —
active-color swap preserved), `pointer-events:auto` opt-in on the pointer-events:none frame,
wired in `_toggleViewfinder`'s ON branch (B is lazy-built), removed from the CPE title row;
**eye-off during an active walk force-stops it first** (§CPE_SOLE_OWNER: the frame owns its
walk, the eye owns the frame). Witness +3 gates (BTN-ABSENT / BTN-B / EYE-OFF-STOP), Duplex
17/17 PASS. sw CACHE_VERSION v959.

**🔒 VISION (LOCKED, user 2026-08-07 — "the perfect rehash"):
POV walk is an input device for the existing stick edit, not a new authoring model.**
Every design question on this feature resolves against this line: if a proposal adds a second
path-authoring model, second path truth, or new mechanics beyond navigate-in-B → snap stick with
facing → existing replan re-snakes, it is out — same mechanics, better experience.

**Author the film path by WALKING it in POV**: drive B (the §CPE_SCRUB_POV_ONLY viewfinder) with
walk-mode controls, record the traversed poses, and let that recorded walk BECOME the path — sticks
derived from the walk instead of the walk derived from sticks. Builds on `§CPE_SCRUB_POV_ONLY` +
the eye-owned scrub transport (prompts/CINEMA_PATH_EDITOR.md). Getting the sticks themselves is
already solved — this is an additional helper tool, not a replacement. Unexplored; spec before any code.

> ⚠ SUPERSEDED 2026-08-07 (user ruling, see §CPE_WALK_EDIT_V1): "the walk BECOMES the path" is
> RETIRED, not deferred — POV walk is an input device for the EXISTING stick-edit mechanics (navigate
> in B, snap a stick with facing, planner re-snakes), never a second path-authoring model. Same
> mechanics, better experience. The paragraph above stands only as the idea's origin.

## Pre-work: perf code study (separate session, before implementation)

Before any walk-mode code: a deep-code-study session profiles the CURRENT canvas cost —
CPE's existing render/raycast loop (stick handles, pipe/hose rendering), the building
mesh load path, and what's live per frame during Alt+C editing. Goal: find the actual
mount/unmount seam so walk-mode (pointer-lock movement, pose recording) and the stick
editor never render/tick in the same frame — one heavy tool live at a time, same canvas,
same loaded mesh, no reload. Output = a written cost breakdown + the seam location,
handed to the implementation session as fact, not re-derived by it.

## Awareness: concurrent sessions on the same viewer surface

Two other sessions are live in parallel on this codebase: **Time Machine** (`viewer/time_machine.js`)
and **4D Generating** (`viewer/schedule_gate.js`, `viewer/schedule_author.js`, `viewer/schedule_diff.js`).
CPE itself lives in `viewer/effects.js`. All four are viewer-side and can be mounted in the same canvas
session — walk-mode must not assume it's the only thing running.

**Isolation requirements, both git and runtime:**
- **Git:** work in a dedicated `/tmp/wt-*` worktree (never the shared checkout — CLAUDE.md Worktree
  Hygiene already logged a real concurrent-edit collision in this repo). `fetch`/merge fresh before
  starting; re-check before pushing since three sessions may land around the same time.
- **Runtime:** the perf-study output (mount/unmount seam, cost breakdown) must state isolation against
  ALL THREE neighbors, not just CPE's own stick editor — if Time Machine's scrub transport or a 4D-driven
  visibility pass can be active while walk-mode's pointer-lock/recording loop runs, the study names
  that conflict explicitly. Walk-mode's own code stays in its own module/file; touch `effects.js` /
  `time_machine.js` / `schedule_*.js` only where unavoidable, and say exactly where and why.

---

## §CPE_WALK_SEAM — pre-work perf code study DONE (2026-08-07, findings only, no code)

Read from `origin/main` @ `fa506ef` via `git show` (the `~/bim-ootb` shared checkout was dirty with a
concurrent CPE session mid-edit in `viewer/cinema_path_editor.js` — not touched, not merged). NOTE
for the implementation session: **CPE no longer lives in effects.js** — the editor is
`viewer/cinema_path_editor.js` (3331 lines); effects.js keeps the plan maths (`cinemaPathPlan`,
`cinemaSeedBands`, `cinemaHoseReanchor`) and the Alt+C entry that calls `cinemaPathEditor.open()`.

### Frame-loop truth (what is live per frame during Alt+C editing)

- **One loop owns all painting:** `main.js animate()` (main.js:857), single-owner rAF, self-parking
  (§IDLE-PARK). Awake term (main.js:862): `_needsRender || streaming || walkModeActive || walkMode ||
  flyActive || _orbiting || _pipelinesCompiling`. Idle Alt+C editing **parks at 0 frames** — CPE is
  fully event-driven and wakes the loop via `A.markDirty()`.
- Per awake frame: `controls.update()` + `walkTick/flyTick` (skipped when `walkModeActive`),
  `streamTick` (no-op post-stream), `dlodTick`, clash LOD, `walkModeGpsTick` (no-op), `walkOrientTick`
  (device-orientation, mobile walk only), `updateMeasureLabels`, then ONE `renderer.render` + the
  opt-in `A._cpeViewfinderRender()` scissor pass (main.js:912 mobile / :927 desktop).

### CPE cost inventory, itemized (all file:line = cinema_path_editor.js unless said)

1. **Editing idle, eye off: zero per-frame.** No CPE rAF exists outside `_pulse` and rehearsal.
2. **`_pulse` (:533)** — rAF chain ONLY while a handle is held; throttled to PULSE_HZ; scales one
   sphere + markDirty. Killed by `_stopPulse()`/release.
3. **Drag (`h.move` :2681)** — per pointermove: `_dragDelta` + `_redrawScene` (:448 — full clear +
   rebuild: film tube of FILM_SAMPLES pts, fat walk tube, ~4 objects per band, then `_renderScrub`
   DOM). Deliberately NO re-plan mid-gesture (§CPE_DRAG_LAND_FIRST); `_replanFilm` runs ONCE on
   pointerup — measured 291ms typical, up to 1218ms on Hospital (§CPE_REPLAN_SLOW; §CPE_PANEL_PERF
   in CINEMA_PATH_EDITOR.md:8966 has the triple-run finding: Alt+C open plans 3× ≈ 560ms).
4. **Eye ON (B)** — `A._cpeViewfinderRender = _vfRender` (:1764): one extra full-scene
   `renderer.render` into a scissor rect per painted frame (:1735). Measured cheap (G-PERF-1;
   §CPE_PANEL_PERF: "eye toggle itself measured cheap"). Side effect: B being open disables the
   orbit DPR drop (§CPE_VF_DPR_GUARD, main.js:721) — orbiting stays full-DPR while B is on.
5. **POV rehearsal (§CPE_SCRUB_POV_ONLY, `_previewFly(povOnly)` step loop :2194)** — CPE's own rAF
   chain; per frame: `plan.poseAt` → vfCam pose (`_applyVFPose` :1315), `_renderScrub` DOM, optional
   TM cursor drive (`tmSetCursor` → `renderAtTime`: 2ms delta path / ~23ms full at 16k objs,
   TM_INCREMENTAL_RENDER_PERF.md), ghost ground + day counter, `markDirty` (which makes animate paint
   main + B). **This loop is the exact template a walk-record loop replaces.**
6. **DLOD (`dlodTick`, dlod.js:127)** — every EVAL_EVERY-th frame; skips when BOTH cameras idle
   (<1cm, dlod.js:140-145); already unions B's camera via `A.cinemaPathEditor.activePOVCamera()`
   (§CPE_DLOD_VF_UNION, dlod.js:132-160). A continuously-moving vfCam defeats the idle skip → full
   InstancedMesh pass + flip storms (measured flips_mean=2671, FPS→53 — §CPE_PANEL_PERF item 3).
7. **Mesh load path: NOT a walk-mode cost.** Meshes are load-time state (streaming.js); walk-mode
   mounts on the same loaded scene, no reload. The one heavyweight deferred init that can land on a
   first Play is TM cold activation (~2.1s, §CPE_PANEL_PERF item 1) — pre-arm idea already named there.

### THE SEAM — mount/unmount, found not invented

CPE already has exactly one input seam and one render seam; walk-mode slots into both with **zero
frame-loop changes**:

- **Input seam = `_wire()` / `_unwire()` (:2628 / :2835).** The whole stick editor's interaction is 4
  capture-phase listeners (keydown/pointerdown/pointermove/pointerup) held in `_state.handlers`.
  Walk-record mount = `_unwire()` + add walk-mode's own listeners (pointer-lock mousemove + WASD);
  unmount = remove them + `_wire()` back. The stick editor's per-move `_redrawScene` structurally
  CANNOT run during a walk because its handlers are off the DOM — "one heavy tool per frame" enforced
  at the listener level, not by an if-guard.
- **Render seam = the `A._cpeViewfinderRender` hook** (set/cleared in `_toggleViewfinder`
  :1764/:1793, master teardown `_vfTeardown` from `finish()` :3102). B IS the walk viewport: drive
  `_state.vfCam` from the pointer-lock pose exactly as `_applyVFPose` does (pose write + markDirty),
  reusing B's existing scissor pass. No new render pass, no second renderer, same canvas, same mesh.
- **Do NOT touch `APP.walkModeActive` / `APP.walkMode`.** Those are walk.js (mobile GPS/device-orient
  walk) and tour.js (tour playback) state — setting them flips `controls.update()` off and arms
  `walkOrientTick` in animate (main.js:877-888). CPE-walk keeps a private flag inside its module;
  markDirty-per-pose-write is all animate needs (identical to how `_previewFly` stays painted).
- **Pose recording:** sample `{pos, look, t}` per step-frame (same shape `plan.poseAt` returns);
  sticks derive from the recorded polyline via the same band shape `open()`'s clone builds
  (:2864-2891) — the walk becomes `plan.bands`, provenance `_stick` flags included.
- **Teardown home:** `finish()` (:3094) is the master teardown ("must not outlive the editor" rule,
  :3099) — walk-mode teardown registers there alongside `_vfTeardown`/`_scrubPanelTeardown`.
- **Pointer-lock precedent in-repo:** navigate_controls.js:28-56 (`canvas.requestPointerLock`,
  mousemove yaw/pitch with clamp, `pointerlockchange` flag) — copy the pattern, not the file (it is
  gated on nav.active and owned by turn-by-turn nav).

### Isolation vs the three neighbors (required output)

1. **CPE's own stick editor** — isolated by the `_wire/_unwire` seam above; also `_stopPulse()` on
   walk-mount. Editor visuals (`_state.objs`) can stay parked (no `_redrawScene` calls happen without
   its handlers); optionally hide them during walk so B's view is clean.
2. **Time Machine** — TM never writes the camera; it renders via its OWN `setTimeout playTick →
   renderAtTime` (time_machine.js:3367), outside the rAF loop, so it CAN legitimately run mid-walk
   (that is today's rehearsal-with-buildup, additive GPU cost only). One rule to inherit: CPE drives
   TM's cursor ONLY through `window.tmSetCursor` and ONLY when TM is already active — never arms it
   (§CPE_SCRUB_BUILDUP_SYNC :1345, "never arm TM from a scrub"). Walk-record with buildup ON follows
   the same rule. No shared mutable state otherwise.
3. **4D modules (schedule_gate/author/diff)** — grep-verified ZERO per-frame presence: no rAF, no
   intervals, no pointer listeners of their own; they act by mutating TM's model and calling
   `renderAtTime`. A Gantt retime mid-walk just triggers TM re-render passes (§GANTT_RETIME_RESYNC
   path) — additive, nothing walk-mode reads or writes.
4. **DLOD is the ONE real added per-frame cost** — a walking vfCam gets correct culling free
   (§CPE_DLOD_VF_UNION) but continuously defeats the idle skip → the flip-storm cost §CPE_PANEL_PERF
   already flagged (FPS→53). Same verdict as there: known DLOD landmine
   (project_dlod_geometry_swap_landmine.md), smallest lever, touch last, not a blocker for v1.

### §CPE_WALK_TM_LOCK — user ruling (2026-08-07, on reviewing the study)
User: *"when POV 'walk' mode, TM is locked or goes to background to avoid user meddling, jamming up
the canvas perf."* Confirmed gap: nothing locks TM today — its panel stays user-drivable while CPE is
open, and its scrub self-renders outside the rAF loop (time_machine.js:3367 `playTick` →
`renderAtTime`), so a user TM-scrub mid-walk stacks full re-render passes onto the walk loop.
**Rule for implementation:** walk-mount pauses TM playback and disables/backgrounds its panel;
walk-unmount restores exactly the prior state. Extends §CPE_SOLE_OWNER ownership doctrine (only the
owner drives a tool); supersedes nothing — the "never ARM TM from a scrub" rule (:1345) still holds
for CPE's own writes.

Clarifications recorded from the same review:
- **"Both cams" ≠ two moving cameras.** While B is on, two cameras PAINT each frame (main full-canvas
  + vfCam inset); in POV walk only vfCam moves. DLOD's idle-skip consults both because its culling is
  global (a zero-scaled instance vanishes in every view — dlod.js:196), so a moving vfCam alone
  defeats the skip.
- **Main canvas is camera-static during POV, not scene-static.** §CPE_SCRUB_POV_ONLY never moves the
  main camera, but BuildUp visibility is per-element scene state, not per-camera — a cursor change
  repaints construction state in BOTH views. Truly freezing the main view would need per-camera
  visibility (three.js layers) — out of v1 scope, noted only.

### §CPE_WALK_CANVAS_FREEZE — user proposal assessed (2026-08-07): freeze main view during walk
User: *"locking up the canvas, ie only when POV walk edit is done and locked back it refreshes, does
this help perf?"* — **Yes, and it is the biggest lever available.** While B is on, every painted
frame is TWO full-scene renders (main full-window + B inset); pixel cost ∝ area (inset ≈ 10-15% of
window) and draw calls halve when the main pass stops. During walk, render only B.
- **Mechanism (the naive way breaks):** three.js default `preserveDrawingBuffer:false` means pixels
  outside the scissor rect are UNDEFINED after present — you cannot simply skip the main pass and
  keep presenting. Freeze via one-time snapshot overlay instead: at walk-mount capture the canvas
  into an `<img>`/2D-canvas overlay covering the main view, render only B's rect live, drop the
  overlay at unmount + one `markDirty` repaint. ~30 lines, deterministic.
- **No "refresh from last path save" needed:** nothing visual is consumed while frozen — unmount is a
  single repaint of live scene state, not a reload; a TM cursor that moved meanwhile shows correctly
  in that same repaint.
- **TM lock stays programmatic, not instruction-only:** a user TM scrub during walk renders UNDER the
  overlay — invisible but still burning GPU per tick (renderAtTime is a direct render). The ~5-line
  lock (pause playback + `pointer-events:none` on the TM panel, restore on unmount) removes it;
  instruction-only would not.
- **DLOD bonus:** with the main view frozen pixels, the walk tick can cull for vfCam ALONE — the
  union frustum and main-camera terms drop out of dlodTick for the duration.

### §CPE_WALK_EDIT_V1 — user UX recap = the v1 scope (2026-08-07, spec now sufficient to code)
User recap, confirmed: *user edits the POV (walks in B) to navigate to the spot he wants the stick,
presses 'stick snap' → a stick is assigned there (visible when edit ends — main view is frozen per
§CPE_WALK_CANVAS_FREEZE until then); user may stop walk, save path on the main Alt+C panel, return
to snap again. The path re-snakes itself with each new snap. The POV carries the cam heading too —
the snap captures the part and FACING the user navigated to.*

**This SHRINKS v1 versus the original one-liner.** Continuous walk-RECORDING ("the walk becomes the
path") is **RETIRED by user ruling (2026-08-07, "for the synch and sanity reason")**, not deferred:
the canvas already edits via sticks+replan, and POV merely gives a better experience over the SAME
mechanics. Design rationale, agreed: (a) one source of path truth — bands → `cinemaPathPlan` → film;
a recorded pose stream is a second truth that must be resampled into bands anyway, and undo/hose/
clip/staging/§CPE_HOLDER_INTEGRITY are all band-shaped; (b) the planner out-films the hand — raw walk
poses carry jitter/hesitation/backtracking, and cleaning them needs smoothing heuristics = invention.
v1 is discrete snap-sticks, which reuses existing machinery nearly everywhere:
- **Re-snake = the existing re-plan, not new script.** `_replanFilm` → `cinemaPathPlan` already
  re-derives the whole path when a band is added/moved (drag-release path today). Cost 291–1218ms,
  fired once per snap (discrete click, never per-frame) — same budget as today's pointerup.
- **Facing = the existing pin data path.** Bands already carry `lookAt` (§CPE_AIM_PIN, `_setPin`
  :2407); §CPE_AIM_PIN_DISABLED only disabled its CLICK trigger, the storage/clone/_buildOverride/
  plan paths all still carry it. Snap sets `band.lookAt` from vfCam facing — zero new plumbing.
  **Two-layer facing model, confirmed in code (user 2026-08-07: "generated to be smooth... a
  separate always-present influence that calmly reorients after that pin-point snap"):** the
  always-present layer is the DERIVED gaze (density/depth LOS + §CINEMA_GAZE_SENSE blend); a pin
  overrides it only inside its band's Voronoi zone on the walk curve (`_pinLookAtAt`,
  effects.js:5161-5189 — "the pin always wins locally at its own band, with LOS/density resuming
  immediately after, no bleed into neighbours"). The calm reorientation on zone exit is the existing
  blend machinery, not new code. Known wrinkle on that seam: §CPE_SEAM_CONTINUOUS gap (57-93° where
  ~0 expected) once seen on pin replans — log if hit, separate bug, do not chase in v1.
- **Save/visibility = unchanged.** OK/Save staging (§CPE_REOPEN_NODE) and `_pathsSave` untouched;
  new sticks appear in the main view at unfreeze via the normal `_redrawScene`.
- **Genuinely NEW code, all inside `viewer/cpe_walk.js`:** (a) walk controls driving `_state.vfCam`
  (pointer-lock look + WASD, pattern from navigate_controls.js:28-56, private mode flag — NOT
  APP.walkModeActive); (b) the snap: project vfCam position onto the current path curve
  (`flowHosed`/`filmPts`) to derive the insertion fraction `s` and band ordering, create the band at
  vfCam pos with `_stick:true` + `lookAt` from facing — the ONE new maths piece; (c) the
  §CPE_WALK_CANVAS_FREEZE overlay + §CPE_WALK_TM_LOCK pause, mounted/unmounted at the `_wire/_unwire`
  seam with teardown in `finish()`.
- **Standalone-codable without impact: YES** — zero edits to effects.js/time_machine.js/
  schedule_*.js; cinema_path_editor.js touched only at the named seams. Build in a `/tmp/wt-*`
  worktree per the Isolation section above. Verification per FUNDAMENTAL LAW: §-log numeric pose
  series (vfCam position/yaw/pitch per snap, band s/centre/lookAt asserted against the walked pose).

**§CPE_WALK_SNAP_WOW (user, same day: "if the main canvas is cheap in just concurrent shows the new
stick it be a wow"):** the frozen backdrop REFRESHES ONCE PER SNAP — snap already pays the
0.3–1.2s replan, so one extra main repaint + snapshot re-capture is noise on top of it. Sequence at
snap: replan (existing) → `_redrawScene` + one painted main frame → re-capture the overlay
**in the same task as that render** (`drawImage` immediately after `renderer.render`, same rAF —
`preserveDrawingBuffer:false` invalidates the buffer after present). Walking stays single-pass
(B only); the user still SEES each stick + re-snaked pipe land in the main view the moment they
snap it. Per-frame freeze economics unchanged.

### §CPE_WALK_UX_V2 — user's walk model, ruled live on Hospital (2026-08-07, SHIPPED PR #1248)
User's Hospital bug report (deployed v959): shoes at the aerial pose planted a SKY STICK
(`§CPE_WALK_SNAP_RESULT s=0.000 centre=(141.01,68.94,212.14)`), pinch did nothing, walk read as
"orbit around a static pos only". Their corrections, now the ruling (and a drift correction: the
"scrub first, then shoes" flow was the assistant's invention — user never asked for it, and
pointer lock hides the cursor so it was physically impossible mid-walk anyway. LISTEN FIRST.):
- **Walk is SELF-SUFFICIENT** (§CPE_WALK_SPAWN): first shoes press spawns at the walk stretch's
  eye-level start (plan-derived via new narrow surface `_walkSpawnPose`); shoes off/on resumes
  bit-identically; only eye-off/editor-close clears the resume. Scrub bar: not part of the flow.
- **Trackpad-first** (§CPE_WALK_GLIDE): "usual finger on mousepad controls, all in the POV only" —
  finger move = pivot (pointer-lock look), two-finger scroll / pinch (ctrl+wheel) = glide along
  facing (1.2m/notch, Shift ×4). WASD stays as a bonus, not the primary.
- **Enter/Space = plant + release** (§CPE_WALK_ENTER_LOCK, Space added "for finger dexterity"):
  one stroke plants the stick (facing = aim pin), runs the replan ("pause a sec to update the
  stick along the path"), and releases to canvas review where the stick is draggable; shoes
  resumes at the same spot for the next stick. Click while locked = snap-and-keep-walking.
- **User's insertion model confirmed 1:1 with shipped maths**: "path adds nearest to where POV is,
  then drag to that spot" IS `_walkSnap` (nearest-point insertion fraction, centre = walked pose).
  "Doable because we do it anyway manually" — the locked vision restated from the user's side.
- Witness 22/22 Duplex (5 new gates: SPAWN / SPAWN-SNAP / GLIDE / RESUME / ENTER-LOCK). Two
  witness-authoring lessons: s=0.000 at the SPAWN is correct (spawn IS the walk head — the bug
  signature was s=0.000 with an aerial centre); a same-tick stop/start toggle races the deferred
  pointerlockchange auto-stop (synthetic-only, precondition documented in the witness).
- **Viability caveats stated, accepted as design**: the glide follows the FULL facing (aim down →
  glide descends) and there is no collision — the walker can pass through walls. This is an
  authoring camera, not a physics walk; fast free positioning is the point.

### Handed to the implementation session as fact
- New module file (e.g. `viewer/cpe_walk.js`). `cinema_path_editor.js` touched only at: a mode-toggle
  button in the panel, the `_wire/_unwire` calls at mount/unmount, `finish()` teardown, and a narrow
  read surface for `_state.vfCam`/`_state.plan` (precedent: `activePOVCamera()` :3173).
  `effects.js` / `time_machine.js` / `schedule_*.js`: **zero edits needed.**
- Verification is §-log numeric truth per the FUNDAMENTAL LAW: recorded pose time-series
  (position/yaw/pitch/rate) asserted programmatically — no screenshots.
