# ⚠ DO NOT REMOVE — Photoreal Still Render: spec for the "photoshop finish" idea (2026-07-15)
# SCOPE: a camera-still render mode aiming for archviz-marketing-image quality. Distinct from
#   MOBILE_PERF.md (that's runtime navigation speed — this is a deliberately expensive, idle-only,
#   opt-in still). Read the log after every run. This file is the spec — no implementation without
#   reading §HONEST VERDICT first; don't build past what that verdict promises.
# Full day-by-day history (2026-07-15 → 2026-08-11, ~7400 lines) archived verbatim, nothing lost:
#   prompts/archive/PHOTOREAL_STILL_RENDER_full_history_2026-07-15_to_2026-08-11.md
#   Consolidated 2026-08-11 per user ask ("prompts/# has got too long") — this file kept to the
#   evergreen spec + the still-OPEN threads only. Closed/shipped work is a one-line pointer with
#   its commit/PR; full diagnostic narrative for closed items lives in the archive if ever needed.

## ▶ RESUME — START HERE
Three open threads, all below in full detail:
0. **§WEATHER_ADVANCED_MODE** — SPEC ONLY, no code. Opt-in bake-only weather (the Twinmotion/Lumion
   parity ask). Most of the machinery is already shipped; start at the Phase 1 overcast preset, not
   at clouds. Flagged against the schedule-accuracy-first ruling — a decision, not a queued task.
1. **§LTU_FLOOR_FLICKER** — MaxQ bake floor flicker on `LTU_AHouse`. Mechanism-confirmed
   (transparent-sort instability, ghost-ground × x-ray-staging), NOT pixel-proven, NOT fixed.
2. **§SHADOW_FRONTIER** — shadows on in-progress/ghosted construction elements during a MaxQ bake.
   Mechanism traced, no code bug found (unlike the sun-arc bug below), genuinely needs live
   evidence next. **User ruling 2026-08-11: not serious, nice-to-have only if free — don't burn a
   session on it.**
Closed this session, confirmed working live: §CAM_LIGHT (camera fill-light) and §SUN_ARC
(noon→dusk sweep) — see their one-line status below, full story in the archive.

## §WEATHER_ADVANCED_MODE — SPEC ONLY (2026-08-12, user ask: "can we incorporate what they have as an advanced mode during baking?") — NOT STARTED, NO CODE

**Context for the ask.** Twinmotion and Lumion both ship weather (rain, snow, fog, wind, seasons;
Twinmotion adds volumetric clouds) AND 4D construction phasing in the same tool, so "a film with
weather while the 4D reveal runs" is an existing, documented competitor workflow, not white space.
This viewer currently has **no weather at all** — the sky is a clear-sky Preetham model with no cloud
geometry (see the §PHOTO_SKY comment in `effects.js`, which says so explicitly).

**The surprise on inspection: most of the hard half is already shipped.** Verified against
`origin/main` before writing this — nothing below is assumed:

| already live | where |
|---|---|
| Scene fog colour + density saved, re-tuned for the shoot, restored on teardown | `effects.js` `_photoFogColorSaved` / `A.scene.fog.density = Math.min(..., 0.00006)` |
| **Wet ground** — 6 seeded circular puddles, per-puddle roughness drop + diffuse darkening via a ground-material `onBeforeCompile` injection, plus `§GROUND_WETNESS_OVERRIDE` | `effects.js` §PHOTO_PUDDLE |
| Real photographed HDRI env, swapped in at staging and restored at teardown, loaded by filename from `viewer/textures/hdri/` | `effects.js` §LAYER2_HDRI (`belfast_sunset_puresky_1k.hdr` — currently the ONLY file in that dir) |
| Sun travel 55°→6° per frame of the bake | `effects.js` §SUN_ARC `_sunArcStep` |
| Staffage/entourage trees with real spatial placement + ground seating | `effects.js` §STAFFAGE |

So the gap vs Lumion/Twinmotion is exactly four things: **clouds, precipitation particles, snow
accumulation, and an overcast lighting state.** Everything else a weather preset needs is wired.

**Recommended order — cheapest real gain first. Do NOT start at clouds.**

1. **Phase 1 — an "Overcast / after rain" preset. Highest realism-per-line in the whole list, and it
   needs no new rendering technique at all.** It is a preset over knobs that already exist: add one
   overcast `.hdr` beside the sunset one (the loader takes a filename in a single place), raise fog
   density above the current `0.00006` cap, drop `A.sun.intensity` and lift hemi/ambient for diffuse
   sky-dome light, and leave the shipped puddles on. **The structural reason this is the right first
   move:** the one genuine conflict between an HDRI sky and §SUN_ARC is that an HDRI has its sun
   baked at a fixed position while the arc sweeps — and an overcast sky has *no visible sun disc*, so
   that conflict simply does not arise in this preset. Free pass on the hardest integration problem.
2. **Phase 2 — rain streaks.** GPU points / instanced quads in a camera-locked volume, additive.
   Modest cost. Deliberately AFTER Phase 1: rain particles over dry ground read fake, and the wet
   ground that sells them is the part already built. Rain without Phase 1 is the wrong order.
3. **Phase 3 — clouds.** Either a scrolling cloud-layer texture on a dome (cheap, moves, fake
   parallax) or raymarched volumetrics (real, expensive). Budget reality from this file's own Layer 4
   note: N8AO alone already costs ~317 ms/frame extra on an RTX 4060, and volumetrics are the same
   order — so this is bake-only and will lengthen a bake noticeably. A cloudy HDRI gives photographed
   clouds for free in reflections but cannot move or parallax, and its baked sun WILL fight §SUN_ARC
   in any non-overcast preset.
4. **Phase 4 — snow accumulation.** The payoff shot for "seasons", and the biggest lift: needs an
   up-facing-normal blend in the triplanar shader (`streaming.js` Layer 3). Snow as particles alone,
   with no accumulation on roofs and sills, will not read.

**Honesty constraints on whatever ships.**
- This closes the "no weather at all" gap. It will not match Lumion's weather quality, and the
  positioning must not claim it does — the durable differentiator remains *the film and the 4D
  sequence are derived from the IFC itself, in a browser, with no export round-trip*, not atmosphere.
- `docs/BIMUserGuide.md` §"Sun, sky and shadow while the film records" currently states in print:
  *"There is no weather: no rain, no snow, no cloud shapes."* That sentence must be updated in the
  same PR as whatever phase ships, or the manual becomes false.
- Advanced mode must be **opt-in and bake-only**, like Alt+J/SSGI — never a default that slows every
  film or changes an existing bake's look without being asked for.

### §SUN_START_TIME — one setting: start time. Fixed 6-hour film. (user spec 2026-08-12, reaffirmed) — FEASIBLE, NOT STARTED

**User decision, final:** a **fixed 6-hour duration** and **one setting: the start time**. Default
**12:00**. No span setting, no dusk anchoring, no solar calculation. The film runs
`startTime → startTime + 6h`.

**What each setting produces** (hour → elevation via the sine Time Machine already uses,
`time_machine.js` `applySunCycle`: `elevation = sin((t/24)·2π − π/2)·90` — no new maths):

| start | end | sun elevation | shadow length / height |
|---|---|---|---|
| 06:00 | 12:00 | 0° → 90° | sunrise → overhead |
| 08:00 | 14:00 | 45° → 77.9° | 1.00 → 0.21 |
| 09:00 | 15:00 | 63.6° → 63.6° | 0.50 → 0.50 (peaks overhead mid-film) |
| 10:00 | 16:00 | 77.9° → 45.0° | 0.21 → 1.00 |
| 11:00 | 17:00 | 86.6° → 23.3° | 0.06 → 2.32 |
| **12:00 (default)** | **18:00** | **90° → 0°** | **0 → sunset** |

**The one range bound, and it is mechanical, not a preference:** with a fixed 6-hour span, any start
later than 12:00 ends after 18:00 — below the horizon, i.e. the film ends in the dark (13:00 start ends
at −23.3°). So the setting's range is **06:00–12:00**. That is the whole rule: *the film is six hours,
so noon is the latest you can start.*

#### Implementation — deliberately small

- `PHOTO_SUN_ELEVATION_START` and `PHOTO_SUN_ELEVATION_END` stop being constants and become
  `elevationForHour(startHour)` and `elevationForHour(startHour + 6)`. `_sunElevationAt(tNorm)` keeps
  interpolating between them exactly as it does now. `_sunArcStep` is untouched.
- Setting range 06:00–12:00, default 12:00. One control.
- Persist the chosen start time with the saved Cinema path, so re-baking an old plan gives the same
  film.
- `§PHOTO_SUN_SHADOW_REACH` (frustum) and `§PHOTO_SHADOW_BIAS_SCALE` (grazing term) are computed once
  at staging from a single elevation — they must use whichever end of the chosen window is **lower**,
  since with a settable start that is no longer always the end frame (e.g. a 06:00 start is lowest at
  the START). One `Math.min`, no new machinery.

#### Two consequences of the default worth recording (statements of fact, not objections)

1. The default **changes today's shipped look**: 55° → 6° becomes 90° → 0°. The opening is an overhead
   sun (short shadows — what `PHOTO_SUN_ELEVATION_START`'s own comment describes as reading flat), and
   the film now ends at the horizon rather than 6° above it.
2. At the 0° end, `§PHOTO_SUN_SHADOW_REACH`'s existing `_elevDeg > 0.5` guard skips frustum widening
   (`height/tan(0)` is unbounded). Practically the last frames are at sunset and near-dark, so there is
   nothing to widen the frustum for — the guard already handles it correctly, no change needed.

#### Witness

1. Assert the logged per-frame elevation matches `elevationForHour` for the chosen window, first and
   last frame, at three start times.
2. Assert the setting rejects/clamps a start later than 12:00.
3. Re-run `scripts/witness_shadow_bias_ab.js` at the window's lowest elevation — shadow contrast there
   must be no worse than today's measured −15.2 mean luminance drop.

#### Not in this spec

Real solar geometry (site latitude/longitude + date). It would make the hour label literally true and
make shadows rotate as well as lengthen, and it reuses this same setting — but it is explicitly out of
scope here. Recorded only because the measured coincidence is worth keeping: on 12 Aug at London's
latitude real solar noon is 53.6°, within 1.4° of the 55° hand-tuned into the current constant.

### Separation + re-render architecture (answered 2026-08-12, verified against `origin/main`)

**Q: keep it separate so it can't disturb the working bake — and can we render over the same frames,
or must it be anew?**

**Separate: yes, and the pattern already exists — copy it, don't invent one.** Alt+J/SSGI is already
opt-in and deliberately excluded from the bake path. Advanced weather is the same shape: one flag read
at `_applyPhotoStaging` time, plus a `_weatherStep(tNorm)` called beside the existing
`_sunArcStep(tNorm)` in `cinema_maxq.js`. Flag off ⇒ every existing code path executes exactly as it
does today, byte-identical output. No new architecture needed.

**Anew, not over the same frames — and the reason is structural, not a preference:**
- `cinema_maxq.js` `poseAt(tNorm)` is a **pure function of tNorm** over the saved plan
  (`plan.poseAt(tNorm)` with the §CPE_CLIP window remap as the ONE place the clip is applied), and the
  4D buildup order is derived from that same `plan.poseAt`. So re-running a saved plan reproduces the
  **identical** film frame-for-frame — same camera, same reveal — with only the atmosphere different.
  That is a re-shoot of the same take, not a similar one.
- The saved frames cannot support the alternative. `_captureFrame()` renders the composer, draws into a
  2D canvas, composites the room title / day counter, and `toBlob('image/webp', 0.92)` — **flat RGB, no
  depth, no normals, no motion vectors.** A flat 2D rain overlay could be composited onto that;
  volumetric fog, clouds with correct occlusion, wet reflections and — decisively — **any shadow
  change** cannot. Compositing cannot fix a shadow, which is the thing being asked for.

**⚠ CORRECTION (same day, before anyone scoped against it): the IndexedDB frame store is NOT a cache
and gives NO second-bake speedup today.** An earlier note in this section called sub-range re-rendering
a free capability. It is not free — it is achievable, which is a different claim. Verified in
`cinema_maxq.js`:
- one fixed store, `IDB_NAME = 'bim_ootb_cinema_maxq'`, `IDB_STORE = 'frames'` (line ~412);
- **`await _idbDelete()` at the START of every bake** (line ~1124), immediately before `_idbOpen()`;
- **`await _idbDestroy(db)` at the END** (line ~1458).

So the frames are a scratch buffer that exists only between capture and mux, wiped at both ends. **A
second bake today costs exactly what the first did.** Nothing is reused.

**What IS achievable, and its real limit.** `poseAt(tNorm)` determinism means any frame index is
independently reproducible, so frames COULD be kept — keyed by (plan hash, frame index, settings hash)
— and an advanced pass could then re-render only the frames it actually changes and re-mux the rest.
But note where that does and does not pay:
- **Whole-film weather change: no saving at all.** Every frame's pixels change, so every frame is
  re-rendered regardless. An advanced bake costs a full bake plus whatever weather costs per frame.
  The expense is the per-frame `_composer.render()` + SSAA/N8AO + `toBlob`, not the muxing.
- **Sub-range change: real saving.** The dusk-shadow case (re-shoot only the last ~20% of frames at a
  higher arc-end elevation) is exactly the shape that benefits, and is the case worth building for.

**⚠ Load-bearing constraint — do not simply delete the deletes.** The start-of-bake `_idbDelete()`
exists because a leftover/blocked store caused a real, diagnosed hang: "stuck right after
§MAXQ_PREVIEW done, zero further lines" (LTU, v810/MAXQ v7 — see the §MAXQ_IDB comment block at
line ~510, which documents the three guards added: track+close our own connection, purge a pending
delete BEFORE opening, and race the open against `IDB_OPEN_TIMEOUT_MS`). Any frame-persistence design
must keep those guards intact and add explicit invalidation + a storage budget (360–576 webp frames at
bake resolution is not free disk), not remove the cleanup that fixed a shipped bug.

### The dusk shadow at the end of the film — not a bug, and weather will NOT fix it

User observation on the landed mp4: the shadow stops working toward the end, "hardly noticeable."
That is the cosine law, not a defect. `PHOTO_SUN_ELEVATION = 6` is the arc's end elevation, and direct
sun on horizontal ground scales with `sin(elevation)` — `sin(6°) = 0.105`, about a tenth of the noon
term. A shadow is the *removal* of direct light, so when there is barely any direct light left there is
barely any shadow to see. **Measured, same building, same session, same fix:** mean luminance drop on
shadowed pixels was **−32.7 at 55° vs −15.2 at 6°** — less than half the contrast (scratchpad
`witness_shadow_bias_ab.js`).

Weather mode makes this WORSE, not better: an overcast preset removes directional shadows entirely.

**The actual lever is the arc's end elevation, not the atmosphere.** Ending at ~12–15° instead of 6°
roughly doubles the direct-light term (`sin 12° = 0.208`, `sin 15° = 0.259`) while still throwing long
shadows (`1/tan 12° = 4.7×` building height). **Put that inside advanced mode**, not in the shared
constant — the default film's look then stays exactly as shipped, honouring "separate so as not to
disturb this." Measure the contrast at the candidate elevation with the existing A/B witness before
committing to a number; the sine law predicts ~2× but that is a prediction, not yet a measurement.

**Priority flag, stated once and then it is the user's call.** `feedback_schedule_accuracy_over_movie_polish.md`
(user ruling 2026-08-05) puts movie-maker polish behind 4D schedule accuracy, and
`prompts/4D_SCHEDULE_PERFECTION.md` still carries an open punch list. Weather is polish by that
definition. Worth deciding explicitly rather than drifting into it.

## HONEST VERDICT (read this before anything else)
**No — this will not be "truly photorealistic" in the indistinguishable-from-a-photograph sense.**
It CAN get meaningfully closer than flat-CG look — plausibly "good archviz render" quality (the
SketchUp+Enscape / Twinmotion tier) — but not camera-photograph quality. Three structural reasons,
not effort/time reasons — more budget doesn't remove them:
1. **The geometry itself is idealized.** IFC-derived meshes have no real-world imperfection — no
   chips, stains, dust, slight panel misalignment, weathering.
2. **Materials are auto-assigned by class, not hand-tuned per surface.** Even with a real texture
   library, a script picking "concrete" for every `IfcSlab` can't replicate an artist choosing
   exactly the right weathered-concrete variant for one specific wall.
3. **No per-shot human grading.** An automated "press a key, get a still" pipeline can't do the
   manual exposure/color/DoF/composition tuning a professional render gets — it's a batch process,
   not an art director.
None of this means the effort is wasted — flat-CG to good-archviz is a real, visible, worthwhile
jump. Just don't scope or promise beyond it.

## STATUS — Layers 1-3 shipped and are baseline behavior; Layer 4 (GI) opt-in only
Full original spec (triplanar shader sketch, texture sourcing, technical approach) is in the
archive if ever needed again — kept out of this file because all three are long since shipped and
live, confirmed as recently as today's witness run (`§TRIPLANAR_PERF materials=12` and
`§LAYER2_HDRI_READY belfast_sunset_puresky_1k` both fired on a real load, 2026-08-11):
- **Layer 1** (Alt+S TAA still-refine, 16-sample jittered accumulation) — shipped, baseline.
- **Layer 2** (real photographed HDRI env, Poly Haven CC0) — shipped, baseline.
- **Layer 3** (triplanar PBR diffuse+roughness on dominant envelope classes) — shipped, baseline.
- **Layer 4** (GI/bounce light): baked lightmaps and full path-tracing both ruled out structurally
  (no UV2 infrastructure; `InstancedMesh`/`BatchedMesh` incompatibility) — SSGI shipped once as the
  DEFAULT, hit real bugs (ghosting, noise/transparency), reverted to default OFF, kept **opt-in only
  via Alt+J**. Deliberately EXCLUDED from the MaxQ/Cinema Orbit bake path — N8AO alone already costs
  ~317ms/frame extra on an RTX 4060 ("a recording with GI active would be a ~3fps slideshow").
  User ruling 2026-08-11: do not pursue further unless another concrete innovation to test.

## NOT IN SCOPE (this spec)
- True photograph-indistinguishable output (see §HONEST VERDICT — structurally unreachable here).
- Baked lightmap GI (blocked — no UV2 infrastructure, separate project if ever pursued).
- Real path tracing on full buildings (blocked — InstancedMesh/BatchedMesh incompatibility).

---

## §LTU_FLOOR_FLICKER — MaxQ bake floor flicker on `LTU_AHouse` (2026-08-03) — CAUSE MECHANISM CONFIRMED, static-camera pixel proof shows NO flicker under isolated conditions (motion-coupled case still open), NOT fixed
User report: floor flicker in a successful MaxQ bake (MP4) on `LTU_AHouse`.

**Two hypotheses REFUTED with real evidence:**
1. **Z-fighting / meta-extracted DB mismatch** — a real `2.39999999` vs `2.39999961` divergence
   exists, but is dead data: `LTU_AHouse` serves in split-DB mode (`viewer/streaming.js` §6.9,
   `meta.db`+`geo.db`), and `extracted.db`'s value is never read on that path. Live-confirmed:
   `§GROUND_Y src=gf-storey-slab(VÅNING 1) z=2.40` matches `meta.db` bit-for-bit. Not systemic
   (Hospital: byte-identical in both files; Duplex: no meta.db, doesn't apply).
2. **DLOD swapping** — `dlod_nav.js:307` fully disengages DLOD for the entire bake
   (`app._maxqActive`), every frame. No swap-threshold oscillation is possible during a bake.

**Live suspect (mechanism-confirmed, NOT pixel-proven): transparent-sort instability** between
the ghost-ground fade (`cinema_maxq.js` `_ghostGroundAt()`, `m.transparent=!solid`) and x-ray
construction-staging (`time_machine.js` `_buildXrayElements()`, no slab exclusion) — both went
default-ON the same day as this report. Ground plane Z == ground-floor slab Z by construction
(`tools.js` `_calcGroundY()` reads the identical row). THREE.js's transparent-pass sort is a
function of camera-to-object distance; for two near-coincident semi-transparent surfaces, small
camera-position deltas frame-to-frame can flip which sorts first.

**Pixel-proof status, real GPU bakes (RTX 4060, `--use-gl=angle --use-angle=gl
--ignore-gpu-blocklist --enable-gpu` — swiftshader was measured ~45x too slow for this building
size, use the GL flags for any future LTU/Terminal/Hospital-scale headless witness):**
- First attempt (authored dive path): diff signal dominated by ordinary camera motion, inconclusive.
- **Static-camera Pass 1** (camera pinned, content NOT frozen): two large diff bursts, but spanning
  0-100% of frame height/width — matches this building's own logged reveal-pacing batches, not a
  ground-plane-height band. Camera motion isolated; content-population was not.
- **Static-camera Pass 2** (camera pinned AND construction cursor frozen at the exact
  `§GHOST_GROUND_TRIGGER_FIRED` threshold): diff traces a single smooth hump matching the ghost
  fade's own `smoothstep` formula analytically — **no alternating/oscillating signature found**
  under fully-isolated conditions. Rules out flicker from the opacity ramp alone or static-scene
  numerical instability — does **not** disprove the original camera-motion-coupled hypothesis
  (a fully static camera can't exercise "small camera-position deltas flip sort order" by
  construction — zero motion ⇒ provably stable sort, that's not evidence either way for a moving
  camera).
- Confirmed: no `renderOrder` is set anywhere on the ground plane or x-ray-staged elements
  (grepped the whole `viewer/` tree) — the sort-order collision precondition is real and
  unaddressed. A `renderOrder` fix, if the motion-coupled case is later confirmed, is genuinely new
  ground, not colliding with any existing convention.
- **Blocked, not abandoned:** reading the ACTUAL ground-floor slab's live `renderOrder`/
  `_tm_xrayStaged`/material state at the trigger moment needs the `BatchedMesh`/`InstancedMesh`
  per-instance index (there's no single per-slab scene node to traverse to) — not done, named as
  the concrete next step.

**Status: NOT FIXED.** Per this project's own no-screenshot/log-not-visual-proof rule, a
`renderOrder` fix should not ship without the pixel-level confirmation described above. Next
session: re-run the static-camera harness (`scratchpad/ltu_flicker_probe_static*.js` pattern) with
the camera pre-positioned at ground level near the slab, spanning the trigger window, WITH the
camera genuinely moving this time (small deltas, not fully static) to actually exercise the
motion-coupled hypothesis one way or the other.

---

## §CAM_LIGHT + §SUN_ARC — camera fill-light + noon→dusk sun sweep (2026-08-11) — CLOSED, confirmed working live
**§CAM_LIGHT**: short-range `THREE.PointLight` riding the camera during Alt+S/Alt+C staging.
Shipped `bim-ootb` PR #1284. User-confirmed working live, watching a real bake: "bright torch light
seems to follow camera is working."

**§SUN_ARC**: `PHOTO_SUN_ELEVATION_START=55°` ("high noon") sweeping to the existing dusk value
(6°, unchanged) across the film — `_sunArcStep(tNorm)` calling `A.updateSky()` every frame, forcing
`shadowMap.needsUpdate=true` since `updateSky()` doesn't touch the shadow map itself. Shipped PR
#1284, but hit **two real regressions same day, both landmines of the same kind**:
1. **Call-order bug** (PR #1284 as shipped): `_sunArcStep()` was called BEFORE
   `A.startStillRefine()`, which internally re-runs `updateSky(PHOTO_SUN_ELEVATION, ...)` (the fixed
   dusk value) as part of its own per-frame staging reset — every frame's swept elevation was
   immediately overwritten back to static dusk before capture. Fixed: moved `_sunArcStep()` to
   AFTER `startStillRefine()`. PR #1288.
2. **SW cache-version miss, TWICE in the same session on the same file:** #1284 edited
   `cinema_maxq.js`/`effects.js`, both in `sw.js`'s `PRECACHE_ASSETS` (cache-first, keyed by
   `CACHE_VERSION`) — shipped without bumping the version, so already-installed PWAs kept serving
   stale code no matter what was merged. Caught and fixed for #1284 (v978→v979, PR #1285) — then
   **#1288 made the identical mistake** (touched `cinema_maxq.js`, zero version bump), which is
   exactly why the user's live retest still showed no arc after #1288 supposedly fixed it. Fixed:
   v980→v981, PR #1289. **Lesson, sharpened: this rule needing to be caught twice in one day means
   it isn't self-enforcing from memory — treat "bump CACHE_VERSION" as a hard pre-merge checklist
   item for any `cinema_maxq.js`/`effects.js` diff.**

**Confirmed working, sandbox witness (not eyeballing — headless Puppeteer, isolated worktree, real
merged code, log read after the run):** `§SUN_ARC_STEP` fired `elevation=55.0/42.8/30.5/18.3/6.0`
at `tNorm=0/0.25/0.5/0.75/1`, exact linear sweep, cross-validated against `A.sun.position.y` read
independently from live scene state (fell `4095.76→522.64` in lockstep — the sun object genuinely
moves, not just the printed number). **User confirmed live on a real bake same day: "sun is
working."** Tuning constants (`CAM_LIGHT_INTENSITY=3`, `PHOTO_SUN_ELEVATION_START=55`) are
first-pass, unmeasured guesses — whether they look RIGHT (not just whether they run) is still
subjective/unverified, not a bug.

**Instrumentation shipped for ongoing verification (PR #1290, v981→v982), no logic changed:**
`§SUN_ARC_STEP tNorm=... elevation=...` on every arc step (direct elevation readout — the older
`§PHOTO_SHADOW sunDist=...` line is camera-target distance, not elevation, and is a misleading
proxy). `§PHOTO_SHADOW_FORCE_REASSERT visMeshes=... flippedOn=...` on the forced reassert that
fires once per MaxQ-captured frame right before capture.

## §SHADOW_FRONTIER — shadows on in-progress/ghosted construction elements (2026-08-11) — OPEN, mechanism traced, no proven bug, LOW PRIORITY (user: "nice to have if free")
User report: shadows not affirming on in-progress constructed beams. Later clarified: specifically
whether interior sun/shadow interplay through open (not-yet-enclosed) construction gaps tracks
4D progress correctly, or fights with Alt+S staging's per-frame teardown/rebuild.

**Traced the full mechanism, found no provable code bug** (unlike §SUN_ARC above, which was a
clean call-order mistake). `time_machine.js:1439-1473` (`renderAtTime`'s per-mesh shadow-flag
block): frontier (actively-installing) meshes get `castShadow = !!app._shadowOn` — gated on the
SEPARATE Sunglass toggle, almost certainly `false` during a MaxQ bake; already-placed non-staged
meshes get `castShadow=false` UNCONDITIONALLY every tick (`§S259`). Both look like the reported bug
— BUT `effects.js`'s `_reassertPhotoShadowCoverage(force=true)`, called from `_finishStillRefine()`
at the end of every captured frame's accumulation, does an unconditional full-scene traverse
setting `castShadow=receiveShadow=true` on every visible mesh, no exception for frontier/staged —
runs AFTER the construction-tick stomp and BEFORE the frame is captured. On paper the loop closes.

**Sandbox witness attempt (2026-08-11):** ran a headless Puppeteer witness calling the real shipped
`A._sunArcStep()`/`A.toggleStillRefine()` directly. Confirmed both new log lines fire correctly with
sane, cross-validated numbers — but that run had **no active Time-Machine construction playback**,
so there were no frontier/staged elements to stomp in the first place. `flippedOn=0` there means
"nothing needed fixing," not "the stomp-then-correct cycle was exercised and passed." A follow-up
witness (driving the TM cursor through two points mid-late in the schedule via `window.tmSetCursor`/
`window.tmGetState`, to actually create staged elements and read their `castShadow` before/after)
was scoped and scripted (`scratchpad/witness_shadow_construction_interplay.js`, not run — 2+
still-refine cycles under swiftshader software rendering, ~126s each measured, so several minutes
total) but **deprioritized mid-session per user ruling: not serious, nice-to-have only if free.**

**Ran the script (2026-08-11, revisited then re-closed same session).** Result: inconclusive, not
negative — the test itself was flawed, not the shadow mechanism. `snapshot()` counted meshes with
`o.isMesh && o.userData.guid`, which returned **zero** at both T1 and T2 (`T1_STOMPED guids=0`,
`T2_STOMPED guids=0`) — this building's geometry is entirely `BatchedMesh`/`InstancedMesh`
(confirmed separately in the §LTU_FLOOR_FLICKER section above), so the individually-meshed
population this script checked never existed to measure. Real finding, though: `§XRAY_EDGES
staged=0/6880` — for HHS_Office_Federated's actual derived build order, the x-ray-ghost mechanism
never triggers at all (no element's support carrier ever finishes after its own reveal), so that
specific "ghosted in-progress" visual state may not even occur in this building's data. Second
cursor-advance cycle (T2) never completed within its 180s budget — resource contention with T1's
own still-running AO tail (194.976s), a script sequencing gap (didn't wait for AO settle before
advancing), not evidence of anything broken.

**Closed 2026-08-11, not pursuing further** — then REOPENED same day when the user hit a sharper,
more specific version of the same underlying problem while actively baking. See
§MAIN_BUILDING_SHADOW below for the full reopened investigation, current status, and the concrete
next step for a fresh session (`if ever revisited` above is superseded — it WAS revisited).

## §MAIN_BUILDING_SHADOW — main building casts NO shadow at all; skyline props + Time Machine's native Shadow mode both DO — ✅ SOLVED 2026-08-12 (PR #1302): shadow.bias is normalised depth, so -0.0005 meant 9.87 m here vs 0.305 m in the working path
User's own established facts (do not re-litigate, do not re-verify — treat as given):
- The main building (HHS_Office_Federated) casts **no shadow whatsoever**, at any point in a
  MaxQ bake, confirmed repeatedly across several fresh live bakes same session.
- The decorative skyline silhouette props (`_buildPhotoProps()`, simple `THREE.Mesh` boxes) DO
  cast a visible shadow, in the SAME bakes, SAME scene.
- Time Machine's own native Shadow mode (`A.toggleShadow`, tools.js, the 'h' pill — a completely
  separate system from PHOTO_SHADOW) already renders shadows correctly and always has.
- Rejected explanations, do not re-propose: dense/thin closely-packed geometry needing a
  different shadow bias (guessed, never verified, user explicitly rejected — "IT IS NOT DENSE").
  Ghost-ground opacity ramp (real mechanism, real log evidence, but user rejected it as a pivot
  away from the actual ask — the differential is main-building-vs-skyline, not early-vs-late).

**Shipped this session (all real, all self-verified before shipping, all still live) — do NOT
re-diagnose these, they are closed and working:**
- PR #1293 `§PHOTO_SUN_SHADOW_REACH` — shadow frustum now widens at low sun angles so the
  building's own long dusk shadow doesn't get clipped (was a real, separate, now-fixed bug).
- PR #1295 `§PHOTO_SHADOW_TARGET_CENTRE` — shadow camera now aims at the real building bbox
  centre via `A.ifc2three()`, not wherever the view camera happened to be looking (was a real,
  separate, now-fixed bug — same failure shape as the already-fixed `§CINEMA_PIVOT`).
  Instrumented in #1296 (`§PHOTO_SHADOW_TARGET` log).
- PR #1298 `§PHOTO_SHADOW_FRUSTUM_COVERAGE` — real `THREE.Frustum` test proves 100% of casters
  are geometrically visible to the shadow camera at every angle tested (noon AND dusk). Confirmed
  live on real bakes: `inFrustum=170+ outsideFrustum=0`, every single frame, no exceptions.
- PR #1299 — shadow map resolution doubled 2048→4096 for the bake-only path (was washing out
  small rooftop-scale detail; unrelated to the main-building-zero-shadow problem but a real fix).
- PR #1300 `§SHADOW_FRONTIER_AT_CAPTURE` — real `castShadow` check on the actively-installing
  (frontier) geometry, read at the exact moment each frame is captured, both for individually-
  meshed AND `BatchedMesh`/`InstancedMesh` objects (batch-wide flag, the finest grain this
  renderer allows). Confirmed live on 24+ consecutive real captured frames: `batchCastShadowTrue`
  exactly matches `batchObjsContainingFrontier` every time, `batchCastShadowFalse=0` throughout.

**So: castShadow flags ✓ (both frontier-specific and the general ~170-mesh population, proven
with real numbers across dozens of frames), frustum coverage ✓ (100%, both elevations), shadow
map resolution ✓ (doubled), shadow-camera target ✓ (aimed at the building, not drifting), deploy/
cache correctness ✓ (`§PHOTO_SHADOW_TARGET`/`§PHOTO_SHADOW_FRUSTUM_COVERAGE` both confirmed firing
on real live bakes). Despite all of this, the user's direct, repeated, live observation stands:
still no shadow from the main building.**

**Went one level deeper — read the actual bundled three.js source** (`viewer/lib/three.module.min.js`,
not the app code) to check whether `BatchedMesh`'s shadow-pass inclusion depends on something the
app never computes. Found: the shadow render loop gates each object on
`!object.frustumCulled || frustum.intersectsObject(object)`. Live witness
(`scratchpad/witness_boundingsphere_check.js`) showed every `BatchedMesh` in the scene has
`frustumCulled: true` (so it DOES go through the intersectsObject test) and
`geometry.boundingSphere: null` (the app's own streaming/batching pipeline never computes it) —
BUT the bundled three.js's actual `intersectsObject` implementation checks `object.boundingSphere`
(the BatchedMesh's own top-level one, confirmed present and valid) BEFORE ever falling back to
`geometry.boundingSphere` — so this specific mechanism, while a genuinely odd gap (the app never
computes a value three.js's own newer BatchedMesh-aware code path doesn't even need), is NOT the
cause. Ruled out with the literal engine source, not inferred.

**Status: genuinely stuck.** Every layer checkable from code and console logs has been checked and
comes back clean. This is not for lack of trying — it is the honest limit of what log-only
diagnosis can resolve here.

### ✅ SOLVED 2026-08-12 — `shadow.bias` is NORMALISED depth, not metres (PR #1302)

**Root cause.** `_enablePhotoShadows()` copied `A.toggleShadow`'s proven `A.sun.shadow.bias =
-0.0005` verbatim. three.js applies that constant in **normalised** depth — the bundled
`three.module.min.js`'s own `shadowmap_pars_fragment` does literally `shadowCoord.z += shadowBias`,
where `z` spans `[0,1]` across the shadow camera's `near..far`. Its world-space meaning is therefore
`bias × (far − near)`, and the two paths run wildly different depth ranges (both measured live on
`HHS_Office_Federated`, not derived on paper):

| path | sunDist | near | far | range | what `-0.0005` actually is |
|---|---|---|---|---|---|
| `A.toggleShadow` (tools.js — the path the user confirms has always worked) | 150 m | 7.7 | 617 | 609 m | **0.305 m** |
| `_enablePhotoShadows` (PHOTO_SHADOW) | 5000 m | 250 | 19998 | 19,748 m | **9.874 m** |

**32.4×.** The cause of the range gap: `toggleShadow` repositions the sun to `ctr + env*(0.8,2,0.6)`
(~150 m away) and derives `near/far` from that; the photo path *must not* move the sun —
`A.sun.position` is what `updateSky`, the Sky shader and the lensflare all read, so it stays at
`updateSky`'s `direction * 5000` — and then derives `near = sunDist*0.05`, `far = sunDist*4` from
5000 m.

**Why that erases exactly what the user saw.** A world-space bias of 9.87 m erases any shadow whose
caster→receiver separation *along the sun ray* is under ~10 m. That separation is
`casterHeight / sin(elevation)`, so at the film's 55° opening **nothing under 8.1 m tall cast
anything at all** — every rooftop fixture, and the near part of a short building's own ground
shadow — while the tall skyline silhouette props cleared it easily. That is the
main-building-vs-skyline differential, and it also explains "did not act in the early seconds"
(early = high sun = worst case) and "nor its roof where objects cast no shadow".

**Why every earlier check came back clean.** `castShadow`, frustum coverage, shadow-camera target
and map resolution were all genuinely correct — PRs #1293/#1295/#1298/#1299/#1300 fixed real bugs.
The bias governs the depth *comparison*, which none of those instruments measure.

**Fix (PR #1302).** Hold the WORLD-space bias instead of copying the normalised constant: floor at
`toggleShadow`'s own proven 0.305 m, raised to `texelWorld / tan(lowest arc elevation)` so grazing
dusk sun doesn't self-shadow the ground (acne) — `_enablePhotoShadows` runs once at staging while
`_sunArcStep` sweeps 55°→6° afterwards without recomputing this camera, so the bias has to be safe
at the worst angle the film reaches. Both terms computed from live values. New `§PHOTO_SHADOW_BIAS`
log line. Live on a real load:
`§PHOTO_SHADOW_BIAS worldBias=0.836m bias=-4.234e-5 range=19748m texel=0.088m grazeElev=6`.

**Proof — paired A/B, identical camera pose / sun / geometry, only `shadow.bias` changed:**

| elevation | px darkened | px brightened |
|---|---|---|
| 55° (film opening) | 1,665 | **0** |
| 6° (dusk) | 12,095 | **0** |

Zero brightened at either — the change only ever *adds* shadow. Post-fix, shipped code vs the old
`-0.0005`: 1,599 px darkened at 55°, 1,084 px at 6°, 0 brightened, isolated-dark-pixel fraction
8.3%/8.9% (low ⇒ contiguous cast shadow, not speckle). The dusk 12,095-vs-1,084 gap is the acne the
grazing term prevents: a flat 0.305 m sits below the 0.836 m texel depth-noise floor at 6°, so most
of that 12,095 was ground self-shadow, not building shadow. Witnesses (headless, numeric pixel
counts, no screenshot in the evidence chain), kept out of the session scratchpad so they don't rot:
`scripts/witness_shadow_bias_ab.js`, `scripts/witness_shadow_bias_postfix.js`. Both need a static
server on the bim-ootb tree (`PORT=<port> node scripts/witness_shadow_bias_ab.js`).

**Instrumentation defect found and fixed in the same PR:** `A.sun.updateMatrixWorld()` was missing
before `shadow.updateMatrices()` in the `§PHOTO_SHADOW_FRUSTUM_COVERAGE` block. `updateMatrices`
reads `light.matrixWorld`, **not** `light.position`, so that log could measure a stale sun —
observed `inFrustum=2 outsideFrustum=349` on one load vs `351/0` on a clean one, identical geometry.
The render was never affected (the renderer refreshes matrices before its own shadow pass), but the
log was not trustworthy as evidence. `time_machine.js`'s `applySunCycle` already did this.

**Lesson worth keeping:** a shadow constant copied between two lights is only portable if their
shadow-camera depth ranges match. `shadow.bias` is unitless; `shadow.normalBias` is the one in world
units. Any future path that reuses another path's shadow tuning must compare `far - near` first.

---

**The next step as named BEFORE the fix above (kept for the record — step 4's code-diff is what
found it, though the differential turned out to be the bias, not the `needsUpdate`/`updateMatrixWorld`
candidates guessed here):**
Time Machine's native Shadow mode (`A.toggleShadow`) is PROVEN to work. PHOTO_SHADOW
(`_enablePhotoShadows`, effects.js) is a DELIBERATE, DOCUMENTED reuse of the same underlying
mechanism (see this file's own earlier session notes: "§PHOTO_DUSK_SHADOWS: reuses time_machine.js's
own proven sun-cycle shadow mechanics... NOT reinvented"). Something in the two setups still
differs even though both end up setting the same flags — that differential is the thing to find,
and it needs a **controlled, same-camera-pose, same-building A/B pixel comparison** between the
two, not more flag-reading:
1. Load the building fresh, park the camera at a fixed pose facing the building's own base/ground.
2. Trigger `A.toggleShadow()` (cycle to 'grass' — real Shadow mode ON), capture the canvas, extract
   real pixel/luminance stats near the building's base (same numeric method already used
   throughout this file — mean/std/contrast in a defined region, NOT eyeballing).
3. Toggle Shadow back off, trigger PHOTO_SHADOW instead (`A.toggleStillRefine()`), same camera
   pose, same capture, same numeric extraction.
4. Compare the two numerically. If TM's pass shows a real contrast/darkness signature near the
   base and PHOTO_SHADOW's doesn't, THAT confirms the differential exists visually (closing the
   "is this even real" question definitively) — then diff the two code paths line-by-line
   (`A.toggleShadow` in tools.js vs `_enablePhotoShadows`/`_reassertPhotoShadowCoverage` in
   effects.js) for the one thing that differs beyond what's already been checked here: candidates
   worth checking first are `renderer.shadowMap.needsUpdate` timing/consumption order relative to
   the two systems' different render-loop integration, and whether `A.sun.target.updateMatrixWorld()`
   is being called at the right point relative to `shadow.updateMatrices()` in each path.
This is resourceful and does not require the user's own DevTools — it's a headless Puppeteer
canvas-capture + numeric pixel comparison, the same class of witness already used successfully
several times this session (see `scratchpad/witness_*.js` for the pattern).
