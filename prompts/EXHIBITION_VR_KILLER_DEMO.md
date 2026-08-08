# ⚠ DO NOT REMOVE — scope: roadmap/index only, no implementation yet. Read the log after every run.

# Exhibition VR Killer Demo — the three pillars (defined 2026-08-08)

## Origin
User's own framing, stated directly 2026-08-08, after the gamepad/WebXR lane's first two PRs
shipped: this VR experience is assessed as the strongest crowd-puller/first-impression asset in
the whole project ("the main door key") — see `project_cpe_walk_gamepad_lane.md`'s Positioning
note. The user named the three specific experiences that make it a KILLER demo, not just a novelty:

1. **Clash analysis** — zoom in, reveal, reconstruct.
2. **Find space** — walk-thru path.
3. **Time Machine 4D** experience.

This file is the index tying those three to the specs/code that already exist, and naming the real
gap for each. Do not re-derive this mapping — it's settled, read it.

## Pillar 1 — Clash analysis: zoom in, reveal, reconstruct
- **Zoom in — REAL, shipped, working today (not VR-specific).** `A._flyToClash(idx)`
  (`viewer/measure.js:619`) computes the exact 3D overlap bbox and flies the camera there with a
  padded cutaway clip. Already wired into 4 desktop UI entry points.
- **Reveal — PARTLY real.** A global x-ray/ghost toggle exists (`A.xrayOn`, `viewer/tools.js:227`)
  and `A.DISC_COLORS` (12 disciplines, `viewer/config.js:43`) is live. The GAP named in
  `MEP_CLASH_REVEAL_MOVIE.md`: no **discipline-scoped hide** (hide ARCH+STR only, leave MEP
  colored/opaque) — global-only today. Data column (`elements_meta.discipline`) already exists;
  this is a visibility filter to build, not new data to extract.
- **Reconstruct — LEAST defined pillar-1 piece, not yet spec'd precisely.** Read as either (a) the
  camera-path-into-the-clash idea from `MEP_CLASH_REVEAL_MOVIE.md` (walking down a ghosted corridor
  toward the clash, not just snapping to it), or (b) a Time Machine-style "watch it get built"
  replay local to just the clashing elements. Needs a decision before spec'ing, not invented here.
- **VR wrapper** — `CPE_WALK_WEBXR_FINDPANEL.md`'s clash-list slice is the "point at a clash, fly
  to it" piece; spec'd, not built.

## Pillar 2 — Find space, walk-thru path
This is the most mature pillar — the whole gamepad/WebXR-stub lane already targets it directly:
- Walk mode (trackpad/WASD/gamepad) — **shipped**, PR #1251.
- WebXR session lifecycle stub — **shipped**, PR #1253 (locomotion itself still an honest stub).
- Point-at-Find-results-panel-in-VR — **spec'd**, `CPE_WALK_WEBXR_FINDPANEL.md`, not built.
- Comfort-locomotion (teleport, only needed if free joystick-flying stays in the VR experience,
  per the same-day comfort discussion) — **not spec'd yet**, named as high priority given exhibition
  framing (`project_cpe_walk_gamepad_lane.md`'s Demo-readiness gap item 2).

## Pillar 3 — Time Machine 4D experience
- The underlying cinematic camera system is real: `time_machine.js`'s scripted "flythrough"/
  "panoramic"/"hero" scene types (tight tracking at a fixed ~12m distance, cut-based scene changes)
  already drive the 4D construction-sequence playback on desktop — confirmed genuinely "tame"
  motion (scripted + cuts, not user-driven continuous flight), which is the comfort-safe pattern
  for VR (same reasoning as a VR rollercoaster — see the same-day comfort discussion).
- **NEW, unverified gap named here for the first time:** whether Time Machine's playback camera/
  render path is compatible with an active WebXR session has NOT been checked. `time_machine.js`
  does run its own independent rAF loops (`_bgBuildRaf`, `_giConvergeRaf` — confirmed during the
  render-loop investigation for `CPE_WALK_WEBXR_VR.md`), but those are for storyboard-building/GI
  convergence, not confirmed to be the actual playback-camera render step itself. Needs a read of
  how Time Machine drives the camera during playback (likely via main.js's normal render path,
  which is exactly what a `renderer.xr.enabled` session takes over) before assuming "just works
  in a headset." Flag this as the first open question for whoever specs Pillar 3 — do not assume
  either way.

## Performance reality check (measured 2026-08-08, real numbers via witness, not estimated)
Headless-Chrome witness (`renderer.info` after settle, flags: `--use-gl=angle
--use-angle=swiftshader` needed for WebGL to init at all in this sandbox) against the real
`/tmp/wt-sandbox` buildings. Compared against Meta's own WebXR guidance (<100 draw calls
comfortable, >500 even strong GPUs struggle — draw calls, not triangles, are the real ceiling on
Quest-class hardware):

| Building | Draw calls | Triangles | Elements |
|---|---|---|---|
| Duplex | 89 | 345,604 | 88 |
| HHS_Office_Federated | 374 | 1,452,255 | 356 |
| Hospital | 471 | 1,619,286 | 460 |

**CORRECTION (same day, second measurement pass): the 471-draw-call Hospital number above was a
mid-stream snapshot, not the settled final state — do not treat it as Hospital's real number.** A
longer follow-up run watched the same building keep climbing past that point: **1,657 draw calls,
5.97M triangles**, still rising when the test was stopped. Hospital's real fully-loaded draw-call
count is unknown and likely higher than either number logged so far.

**Real element counts, confirmed from the app's own load logs (not the earlier witness's flawed
`elementCount` field, which was a bad scene-graph fallback):** Duplex 1,140, HHS_Office_Federated
6,839, Hospital **63,182**.

**Occlusion culling already exists in the codebase — `viewer/dlod_nav.js`** (`§ROOM_OCCL`, real
WebGL2 occlusion queries, structural-occluder decouple), spec'd in full at
`FLY_TOUR_DLOD_SCALE.md`, toggled by the real **'o' key → `window.toggleDlodNav()`**
(`scene.js:1566`). `NAV_MIN_ELEMENTS = 50000` (`dlod_nav.js:59`) gates the large-building tier —
Hospital (63,182) crosses it, HHS (6,839) doesn't, matching the user's own recollection exactly.
Room-based occlusion (`roomOcclEnabled`) defaults **true** once the pill is on — real occlusion,
not just frustum culling — confirmed **user preference: stays a manual pill toggle, not
auto-engaged, so the user stays in control.** Nothing to change there, it already works that way.

**Occlusion-ON test for Hospital — INCONCLUSIVE, not confirmed working or not working.** Two
attempts, both blocked: `§DLOD_NAV_TOGGLE on=true blocked=streaming` — the app's own busy-gate
refused the toggle because Hospital was still mid-load, even after ~100s of waiting in this sandbox.
**Root cause is the test environment, not the app:** this sandbox runs headless Chrome on
SwiftShader (pure CPU software rendering, no real GPU) — far slower than any real target hardware
for a 63,182-element buffer upload. Whether occlusion actually helps Hospital's draw-call count is
still an OPEN QUESTION — needs either a GPU-accelerated test machine or real target hardware to
answer, not this sandbox as currently configured. Do not read the inconclusive attempt as "occlusion
doesn't help" — it never got the chance to run.

One unexplained, honestly-flagged number from the FIRST (471-call) measurement: Hospital reported
2,274,552 GL_LINE draws (`renderer.info.render.lines`) — not investigated, likely edge-outline
rendering, not confirmed. May also have been a mid-stream artifact given the correction above —
re-check alongside any future occlusion test rather than trusting it in isolation.

**Real-GPU retest attempt (2026-08-08, via the user's own real Chrome, not the sandboxed
headless/SwiftShader witness) — PAUSED, observed but not diagnosed.** Confirmed this machine has
real GPU hardware (NVIDIA RTX 4060 Max-Q), so a real-Chrome retest should have sidestepped the
SwiftShader bottleneck above. Instead, the page state was static/stuck for 15+ seconds
(`calls=6, triangles=0, lines=2274552, geometries=12`, unchanged across two checks) — looks like a
real lag, not just slow streaming. **Per the user: another concurrent session is already
investigating a related lag/memory hiccup (triggered by Night mode, noted earlier same session) —
do not diagnose or fix this here, resume the Hospital perf retest once that other session's fix
lands.** One incidental finding from this attempt worth keeping: the 2,274,552-line number appeared
almost immediately (`calls=6, triangles=0`) — consistent with it being an early wireframe/bbox
loading-placeholder, not final geometry; still not fully confirmed, but less mysterious than
originally flagged.

## Status
No new spec written for Pillar 1's "reconstruct" or Pillar 3 yet — this file is the index/roadmap,
not the spec. Next real work, in the priority order already set by the demo-readiness gap: (1)
hardware in hand, (2) comfort-locomotion decision, (3) build `CPE_WALK_WEBXR_FINDPANEL.md`'s clash
slice (serves Pillar 1 AND 2 at once), (4) spec Pillar 3's WebXR compatibility question, (5) define
"reconstruct" precisely for Pillar 1.
