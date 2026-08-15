<!-- Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com> · SPDX-License-Identifier: MIT -->
# ⚠ DO NOT REMOVE — VIEWER_MEMORY_LEAK: Chrome memory growth investigation, PMREM leak found+fixed+shipped

**Target repo/code: `bim-ootb` (`viewer/scene.js`, `viewer/streaming.js`) — NOT this repo.** This doc lives
in `bim-compiler` per this project's cross-repo convention (findings/prompts land here even when the fix
ships in the sibling viewer repo). Read the log after every run.

## ⚠ REOPENED 2026-08-15 — PR #1360's 3 leads shipped+verified 2026-08-14, but a NEW unmeasured 83%-mem
## event happened live 2026-08-15 (see bottom section) — do not assume this lane is fully closed until
## that's resolved one way or the other. Same-day follow-up attempted a headless repro (see
## "Follow-up 2026-08-15 (same day)" near the bottom) — genuinely inconclusive, real signal found
## (AO code is NOT the Lead #1 leak pattern) but the repro itself never got past bake frame 0.

## Trigger
User reported Chrome memory growth over time while running the viewer, no exact repro named. Investigated
broadly, starting from 3 grounded leads already read in-session (a live `_matCache` key-widening change,
PR #1356, plus known three.js/three-mesh-bvh disposal footguns).

## Method (doctrine: measure, never eyeball — `docs/archive/TestArchitecture.md` §Browser Testing)
Headless Chromium via Playwright (reused `bim-ootb/tests/node_modules`, symlinked into a fresh worktree),
driven via CDP. Two numeric instruments, no screenshots:
- `renderer.info.memory.{textures,geometries}` — three.js's own live GPU-resource counters. This is ground
  truth for WebGL leaks: unlike JS heap, browser GC never reaches into a WebGL context — an undisposed
  texture/render-target/geometry stays allocated on the GPU forever, so a monotonic climb here with no
  matching drop is unambiguous proof, not noise.
- `page.evaluate` reads of `A._matCache`/`A.meshCache` sizes + `geometry.boundsTree` presence, before/after
  `A.clearStreamed()`, to check disposal-on-eviction actually works.
- JS heap (`performance.memory.usedJSHeapSize` via CDP `HeapProfiler.collectGarbage` + Chrome launched with
  `--js-flags=--expose-gc --enable-precise-memory-info`) was tried but **discarded as unreliable** — a
  control run (same page, same wait, no `clearStreamed()` call at all) showed the exact same ~50MB jump
  between snapshots as the "real" run, tracing the jump to the launch-flag combination itself, not app
  behavior. Recorded here so a future session doesn't re-chase the same red herring.

## Findings

### Lead #1 — CONFIRMED REAL LEAK, FIXED. `scene.js` `A.updateSky()` → PMREM render target
`THREE.PMREMGenerator.fromScene()` allocates a brand-new `WebGLRenderTarget` (texture + framebuffer) on
every call — it is not pooled/reused internally, and three.js never garbage-collects WebGL resources.
Every one of the 3 call sites in `scene.js` did `A._envMap = envRT.texture` with no reference kept to
`envRT` itself, so the previous render target was silently orphaned on the GPU every time.

**Measured (headless, `blank=1` fast boot, 8x `A.updateSky()` calls 2000ms apart — matches the real
throttle in `A._envMapThrottle`):**
```
§BASELINE {"i":0,"textures":2,"geometries":11}
§SNAP {"i":1,"textures":3, ...}   §SNAP {"i":2,"textures":4, ...}   §SNAP {"i":3,"textures":5, ...}
§SNAP {"i":4,"textures":6, ...}   §SNAP {"i":5,"textures":7, ...}   §SNAP {"i":6,"textures":8, ...}
§SNAP {"i":7,"textures":9, ...}   §SNAP {"i":8,"textures":10, ...}
§RESULT iterations=8 textures_first=2 textures_last=10 delta_textures=8 (geometries flat)
```
+1 leaked GPU texture per call, linear, unbounded. `A.updateSky()` is called every frame the sun moves
during a CPE cinema bake (`effects.js` `_sunArcStep`, called "every frame the sun moves" per its own
comment) — a bake or repeated Alt+S/day-night cycling leaks continuously and is the most likely source of
the user-observed growth, since (unlike Leads #2/#3 below) it is NOT bounded by dataset size — it grows
with wall-clock time the sky is active, indefinitely.

**Fix** (`viewer/scene.js`): a closure-scoped `_envRT` tracks the currently-live render target; a new
`_setEnvMap(newRT)` helper disposes the previous one (`_envRT.dispose()`) before assigning the new texture
to `A._envMap`. Wired into all 3 `fromScene()` sites (throttled regen, initial sync generation, no-Sky
fallback gradient).

**Re-measured post-fix, same 8-call loop:**
```
§RESULT iterations=8 textures_first=2 textures_last=2 delta_textures=0
```
Flat. Leak gone.

### Lead #2 — INVESTIGATED, NOT A LEAK. `streaming.js` `A._matCache` key widening (PR #1356)
PR #1356 (this session, `discipline`+`mepHint.code` added to the cache key) increases the number of
distinct materials that CAN exist for a building, but:
- Cache-hit dedup (`if (A._matCache[cacheKey]) return A._matCache[cacheKey];`) means revisiting/re-rendering
  already-seen content never recreates materials — no per-navigation leak.
- `A.clearStreamed()`'s existing dispose loop (disposes every mesh's material, then resets `A._matCache =
  {}`) correctly frees everything when it runs. Measured on `HHS_Office_Federated` (63K+ elements,
  real production-scale building): `matCacheEntries` 26 → 0 after `clearStreamed()`.

Not fixed — nothing to fix. The widened key is real (more distinct materials at peak for a given building)
but bounded, deduped, and disposed correctly. Honest negative result.

### Lead #3 — REAL GAP FOUND (defensive fix), not proven to be an active leak
`A.clearStreamed()` disposed cached `BufferGeometry` via `geometry.dispose()` without first calling
`geometry.disposeBoundsTree()`. three-mesh-bvh's `boundsTree` is a monkey-patched property (wired in
`loader.js`) that sits outside `BufferGeometry`'s own native dispose chain — plain `.dispose()` does not
free it. Fixed by adding `disposeBoundsTree()` before `dispose()` at both disposal sites (in-scene meshes
in the `toRemove` loop, and `A.meshCache` values).

**Caveat, stated honestly:** could not prove this was an *active* leak via heap measurement — a control run
(no `disposeBoundsTree()` fix, but also no other references retained after `A.meshCache = {}`) showed the
BVH structures becoming JS-heap-unreachable and presumably GC-eligible regardless, since nothing else in
the codebase was found holding a stray reference to those geometries after clear. This is shipped as a
correctness fix matching three-mesh-bvh's documented disposal contract (cheap, safe, right thing to do),
not as a proven-leak fix the way Lead #1 is. Verified safe at scale: `HHS_Office_Federated`, 3269
BVH-bearing geometries, all correctly zeroed post-clear (`bvhCount` 3269 → 0, `rendererGeometries` 364 →
11), no errors.

### Separate observation, not fixed — flagged for a future session if wanted
`A.clearStreamed()` has exactly one call site in the whole app (`time_machine.js:7810`, a Time-Machine
scrub-close path). Normal building-to-building navigation (`A.streamBuilding()`) never calls it — buildings
accumulate in `A.meshCache`/`A._matCache`/`A.buildingsRendered` for the rest of the session once streamed,
with no distance-based unload. This is very likely **intentional** (the `A.savedStreams`/`buildingsRendered`
pattern exists specifically so revisiting an already-streamed building is instant, not re-fetched) and is
bounded by the finite building count in a given DB/session — walking a fixed campus does not grow without
limit once every building has been visited once, unlike Lead #1's genuinely unbounded per-tick leak. Not
changed here since it wasn't proven pathological and would be a real design decision (when/how to unload a
building that's fallen far out of view) rather than a bug fix — naming it here so it isn't re-discovered
from scratch if the user reports growth on a very large multi-building site after this fix ships.

## Shipped
- **PR #1360** (`bim-ootb`, branch `fix/memleak-investigate`) — `viewer/scene.js` (PMREM dispose, Lead #1)
  + `viewer/streaming.js` (BVH dispose on clear, Lead #3). Auto-merge (squash) enabled same session.
  https://github.com/red1oon/bim-ootb/pull/1360
- Both changed files `node --check`-clean. Both fixes verified at production scale
  (`HHS_Office_Federated_extracted.db`, 63K+ elements) with no regressions or console errors, in addition
  to the isolated before/after measurements above.

## Resume block (if reopened)
- If the user reports the leak persists after this ships: check whether they're on a workflow this
  investigation didn't cover (Time Machine's own sun-cycle tick, `time_machine.js` `applySunCycle()`, was
  checked and does NOT call `A.updateSky()` per-tick — it mutates `_sky.material.uniforms`/`app.sun.position`
  directly, so it was ruled out as a driver of Lead #1; if that turns out wrong, re-verify with the same
  `renderer.info.memory.textures` method used here).
- If a future session wants to pursue the "buildings never unload" observation above: that needs a product
  decision (unload threshold, whether `A.savedStreams` should evict LRU-style) before any code, per this
  project's Spec-First rule — not a quick follow-up patch.

## ⚠ OPEN 2026-08-15 — live session hit 83% system memory mid-bake, tab killed before it could be measured
User ran a real Hospital Alt+C bake in the tab confirmed running `§BUILD_VERSION v1029` (post-#1360, the
PMREM fix WAS active) for an extended period (bake reached frame ~249/1728 before being reported as
sluggish). System-wide memory hit 83%, unresponsive enough that the user had Chrome killed outright
(`pkill -f -i chrome`, confirmed clean — 16 Chrome processes → 0, system freed back to ~7.1GB used /
21GB available). **No `renderer.info.memory`/`§MEM_CHECK` reading was taken before the kill** — the
console line was handed to the user to paste (`console.log('§MEM_CHECK', JSON.stringify(APP.renderer
.info.memory), 'matCacheKeys=' + Object.keys(APP._matCache||{}).length, ...)`) but the tab was closed
before anyone ran it. **Genuinely unknown, not assumed either way:** whether this was (a) legitimate
memory load for a long-running 63K-element bake with AO/shadow/staging/triplanar all active
simultaneously (this is a real, heavy combination — §PHOTO_AO alone runs 24 frames × denoise per still,
§TRIPLANAR_PERF logged 48 materials, `§NIGHT_STILL_LIGHTS raised to 200 lights` — none of that is cheap,
and none of it was audited by the #1360 investigation, which only checked 3 specific leads: PMREM env-map,
`_matCache`, BVH-on-clear), or (b) a residual leak in a code path #1360 never looked at (e.g. AO/denoise
render targets, night-mode glow materials/lights, staging's own texture swaps — none of these were on
this session's lead list at all).

**Next session, if this recurs:** run the `§MEM_CHECK` line above every few minutes during a comparable
bake (AO+night+triplanar all active) and watch `renderer.info.memory.textures` specifically — if it
climbs roughly linearly with elapsed time/frame count the way Lead #1 did before its fix, that's the
same class of bug in a different subsystem (most likely AO/denoise render targets or night-glow
materials, per the log tags above, since those are the heaviest per-frame allocators this bake exercises
that #1360 never checked). If it stays flat/bounded, 83% was legitimate load for this
building+settings combination, not a leak — a real but separate finding (this bake's memory FOOTPRINT is
just large) from "memory GROWS without bound" (what #1360 was scoped to and fixed).

## Reference — how GPU/WebGL memory management works in this codebase (general, for future sessions)
Captured here since it came up live and is genuinely useful background, not specific to any one bug:

**The core fact:** WebGL/GPU resources — geometries, materials, textures, render targets, compiled shader
programs — are NOT covered by JavaScript's garbage collector. They live on the GPU; JS only holds a
handle. Dropping the JS reference without calling `.dispose()` first orphans the GPU-side memory
permanently — nothing reclaims it later, no matter how aggressively JS GC runs (this is why the JS-heap
measurement in this investigation's own Method section was discarded as unreliable/irrelevant — `perfor
mance.memory` only sees the JS side, never the GPU side where the real leaks in this app live).

**The three failure patterns found/checked in this codebase, as a reusable checklist:**
1. **Reassignment without disposal** — a variable holding a GPU resource gets pointed at a new one before
   the old one is disposed. This was Lead #1 exactly (`A._envMap = envRT.texture` every ~2s, old render
   target never freed). Rule: anywhere a `THREE.Texture`/`WebGLRenderTarget`/`BufferGeometry`/`Material`
   gets reassigned, dispose the previous value first.
2. **Missing a layer in a cleanup path** — `clearStreamed()` disposed geometries but skipped
   `disposeBoundsTree()` first (Lead #3); three-mesh-bvh's structure sits outside `BufferGeometry`'s
   native dispose chain. Rule: cleanup paths need to dispose every layer a resource has, not just the
   outermost/obvious one.
3. **Unbounded caching without eviction** — `A._matCache` (Lead #2) grows with every distinct material
   key; currently safe only because cache-hit dedup + `clearStreamed()`'s existing dispose loop both
   still cover it. Rule: any future change that widens a cache key (like #1356 did) needs to re-check the
   eviction path still empties the WHOLE widened key space, not just re-use the old assumption.

**Practical habit:** a full Alt+C bake is the best stress test available for this class of bug — it
cycles sky/lighting/staging/AO state every frame in a tight loop, so any future disposal gap shows up
fastest there via `renderer.info.memory`, not in casual navigation. The open item above is exactly this
kind of test surfacing something real — whether it's a new leak or just genuine heavy-scene footprint is
the open question, not whether the test was worth running.

## Follow-up 2026-08-15 (same day) — headless repro attempted, partial/inconclusive, real signal found

**Setup:** `/tmp/wt-sandbox` worktree recreated at `origin/main` (`3de6b49`, includes PR #1363's
sun-shadow-graze fix), Hospital DB symlinked, static server on `:8399`. 3 headless Puppeteer runs against
`viewer.html?db=buildings/Hospital_extracted.db`, each driving `window.APP.startMaxQualityOrbit(...)`
directly (the real Alt+C entry point, `viewer/cinema_maxq.js` `start()`), polling
`renderer.info.memory` + `_matCache`/`meshCache`/`_nightLights` sizes via CDP every 4-5s. Same instrument
class as the original PR #1360 investigation, just live-polled instead of a fixed iteration count.

**Run 1** (swiftshader, viewport 1280x800, `frames:40`): streaming alone took ~336s and accounted for
essentially all the growth seen (`textures` 275→2573, `meshCacheKeys` 3905→20609+) — this is legitimate
first-time content load for a 63K+-element building, not a leak (matches Lead #2's own methodology: cache
population on first view is expected). `§MAXQ_START` fired at ~346s. From there, `textures`/`geometries`
held flat at **2737/2801** for the ~154s further observed before the headless browser session died
(`TargetCloseError: Session closed`) — no `§MAXQ_FRAME` ever completed (frame 0's AO/TAA still-refine
phase alone outlasted the observation window).

**Run 2** (GPU-backed headless — `--enable-gpu --use-angle=gl`, matching the user's real hardware path
instead of software rendering): crashed within seconds of launch. **This sandbox has no working
off-screen GPU context for headless Chrome** — swiftshader is the only viable headless path here, so a
byte-for-byte repro of the user's real (GPU-backed, foreground) crash is not possible via this harness.
Per [[feedback_no_interactive_chrome_tool]] the user's real foreground Chrome is off-limits for automated
driving, so there is currently no available path to a fully faithful automated repro.

**Run 3** (swiftshader, viewport shrunk to 640x400 to cut AO/denoise cost, ~28min budget): streaming
settled ~154s in, `§MAXQ_START frames=10 fps=15 path=cinema` confirmed fired. `textures`/`geometries`
then held **exactly flat at 2737/2801 for over 18 straight minutes** (329 samples, elapsedMs 346k→1409k)
— same plateau value as Run 1, independently, on a different viewport — before the same
`Session closed` disconnect. Again zero `§MAXQ_FRAME` completions. `nightLights` stayed 0 the entire run
in both attempts — `§NIGHT_STILL_LIGHTS` never fired, meaning staging's night-light-boost path (one of
the two named suspects below) was **never actually exercised** by either repro attempt. That's a real gap,
not a negative result on that suspect.

**Source check — AO is NOT the Lead #1 leak pattern:** read `effects.js` `_buildStillAO()`/
`_ensureStillAO()` (~3444-3735). `N8AOPass`, `aoScratchRT` (the `THREE.WebGLRenderTarget` at line 3598),
and `shadowRestoreMat`/`shadowRestoreQuad` are all built **exactly once per page session** — memoized
behind `_stillAOPromise`, reused across every AO phase via `ao.adapter.enabled` toggling and
`ao.pass.firstFrame()` accumulation resets, never reallocated per call. This is the opposite shape of
Lead #1's PMREM bug (fresh `WebGLRenderTarget` every call, old one orphaned) — ruled out as written today.

**Honest verdict — inconclusive, not closed either way:**
- **Against a Lead#1-class leak:** `renderer.info.memory` showed zero growth signature across 18-23min of
  real AO/TAA/triplanar work in two independent runs (different viewports), and the AO source itself
  doesn't have the reallocate-without-dispose shape. This is real evidence, not nothing.
- **Not a full answer:** neither run ever completed a single `§MAXQ_FRAME` (frame 0 alone outlasted both
  attempts), so the specific question the resume block asked — does memory climb *across* repeated frames
  the way Lead #1 did — was never actually tested. The real incident reached frame ~249/1728; this
  sandbox's software-rendering path couldn't reach frame 1 in ~40min combined.
- **The crash itself is unexplained by the tracked counters** — textures/geometries were dead flat right
  up to disconnect in both runs, and `dmesg`/`free -h` showed no host-level OOM or memory pressure either
  during or after (system sat at 11Gi/29Gi used post-crash). Two live, unconfirmed hypotheses, neither
  ruled in or out: (a) something outside three.js's tracked Texture/Geometry accounting is the real
  consumer (raw pixel readback, JS heap churn from the AO RAF loop, browser-internal buffers) — would need
  JS-heap or `ps -o rss=` process-level instrumentation, which this run didn't capture; (b) Chrome's own
  hang-detection self-terminating the renderer under a long synchronous software-rendered RAF loop,
  unrelated to any app leak — plausible given headless+swiftshader is inherently much slower than the
  user's real GPU, but also unconfirmed.

**Next session, if picked up again:**
1. Add `ps -o rss= -p <chromePID>` polling alongside `renderer.info.memory` — the one instrument gap this
   run exposed (app counters can stay flat while something else grows).
2. Check why `§NIGHT_STILL_LIGHTS` never fired in a from-scratch load — if it needs a prior manual
   night-mode interaction to populate `A._nightLights`, that's a real repro gap for testing that suspect.
3. A byte-for-byte repro needs real GPU-backed rendering (this sandbox can't do headless GPU, and the
   user's real Chrome is off-limits to automation) — no clean automated path exists right now; may need to
   accept live-session `§MEM_CHECK` capture (user pastes the console line, as was attempted and missed
   2026-08-15) as the only faithful instrument until that gap is resolved.
