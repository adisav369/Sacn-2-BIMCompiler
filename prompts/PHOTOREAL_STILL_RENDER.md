# ⚠ DO NOT REMOVE — Photoreal Still Render: spec for the "photoshop finish" idea (2026-07-15)
# SCOPE: a camera-still render mode aiming for archviz-marketing-image quality. Distinct from
#   MOBILE_PERF.md (that's runtime navigation speed — this is a deliberately expensive, idle-only,
#   opt-in still). Read the log after every run. This file is the spec — no implementation without
#   reading §HONEST VERDICT first; don't build past what that verdict promises.

## HONEST VERDICT (read this before anything else)
**No — this will not be "truly photorealistic" in the indistinguishable-from-a-photograph sense.**
It CAN get meaningfully closer than today's flat-CG look — plausibly "good archviz render" quality
(the SketchUp+Enscape / Twinmotion tier) — but not camera-photograph quality. Three structural
reasons, not effort/time reasons — more budget doesn't remove them:
1. **The geometry itself is idealized.** IFC-derived meshes have no real-world imperfection — no
   chips, stains, dust, slight panel misalignment, weathering. Real photos are full of these; a
   clean BIM extraction never will be, automated or not.
2. **Materials are auto-assigned by class, not hand-tuned per surface.** Even with a real texture
   library (§LAYER 2 below), a script picking "concrete" for every `IfcSlab` can't replicate an
   artist choosing exactly the right weathered-concrete variant for one specific wall.
3. **No per-shot human grading.** Professional photoreal renders get manual exposure/color/DoF/
   composition tuning per image. An automated "press a key, get a still" pipeline can't do that by
   definition — it's a batch process, not an art director.
None of this means the effort is wasted — going from flat-CG to good-archviz is a real, visible,
worthwhile jump. Just don't scope or promise beyond it.

## WHERE IT LIVES (code, from this session)
- `viewer/lib/TAARenderPass.js` / `SSAARenderPass.js` — ported from three.js r185 official source.
- `viewer/effects.js` — `A.startStillRefine()`/`stopStillRefine()`/`toggleStillRefine()`, `Alt+S`.
  Two real bugs found via live user testing + fixed: (1) cancellation was hooked on the generic
  `_startLoop()`/`markDirty()` choke points, which fire from far more than actual canvas interaction
  (self-cancelled on its own trigger keypress) — moved to the precise pointerdown/wheel/controls-start
  signals in `main.js`. (2) natural completion never reset `accumulate`/`_composerEnabled`, leaving the
  composer frozen on the stale accumulated image after the camera moved — both paths now share one
  `_teardownStillRefine()`.
- `viewer/tools.js` `A.toggleShadow` — existing Shadow mode (PCF shadow + SSAO), unmodified (the
  soft-shadow-type experiment from this session was reverted — no demonstrated visual benefit).
- Branch `fix/night-mode-window-glow`, worktree `/tmp/wt-mobile-perf-fix`. Night-glass commit is
  MERGED + LIVE (PR #798, deployed to production). Still-refine commits are pushed to the branch only
  — not merged, not deployed.

## LAYER 1 — Render polish (DONE this session, real but modest)
TAA still-refine (16-sample jittered accumulation, `Alt+S`) + existing Shadow/SSAO. Fixes: jagged
edges, some contact-shadow darkening at wall/floor junctions. Does NOT fix: flat materials, missing
GI/bounce light, generic environment reflections. This is polish on the CURRENT look, not a path to
photoreal on its own — already shipped-in-prototype, not the open work here.

## LAYER 2 — HDRI environment (cheap, real win, NOT started)
Current envMap (`streaming.js` `A._envMap`, intensity 0.6) is generic. Swapping to a real photographed
HDRI sky (e.g. Poly Haven, CC0) is low effort — one texture load + `PMREMGenerator`, no pipeline
changes. Improves reflections on glass/metal noticeably. **Do this first — best effort:benefit ratio
of everything in this spec.**

## LAYER 3 — Real PBR material/texture library (SPEC, ready to implement)
### Goal
Replace the flat-color + fake-noise-grain look with real textured surfaces (concrete, glass, brushed
metal, drywall, timber) for at least the dominant envelope classes, so the still-render actually looks
different from today's screenshots — not more polish on the same flat materials.

### Scope discipline (agreed earlier this session — do not relitigate)
**Still-render-only.** Triplanar sampling costs real per-fragment GPU time (multiple texture taps per
map, per axis). This must ONLY activate during `A.startStillRefine()` (`Alt+S`), never during normal
navigation — the whole point of this session's investigation was protecting nav performance. Gate it
the same way the composer itself is gated (`A._composerEnabled`/`A._stillRefineActive`).
**Two maps only, first pass.** Diffuse + roughness (2 texture types × 3 triplanar axes = 6 samples/
fragment). NOT normal + AO yet — that's 4 maps × 3 axes = 12 samples/fragment, a real cost jump.
Measure the 2-map cost before deciding whether 4-map is worth it.

### Technical approach — triplanar mapping (solves the missing-UV blocker)
Confirmed this session: `tools/extract.py`'s schema stores `vertices`/`faces`/`normals` only — no UV
column anywhere, extraction through viewer. Triplanar mapping needs zero UV data (projects the texture
along whichever world-space axis — X/Y/Z — best matches each fragment's normal, blending at the seams).
Standard, well-established technique (three.js forum threads confirm the pattern; no ready-made addon,
custom shader code required — see Sources below).
- **Where it plugs in**: `streaming.js`'s material-creation function already has an `onBeforeCompile`
  hook for the existing fake-grain-perturbation trick (`_perturbScale`, search for `onBeforeCompile` in
  that file). Triplanar sampling is injected the SAME way — no new hook mechanism needed, extend the
  existing one.
- **Shader sketch** (fragment shader, GLSL, added via `onBeforeCompile`):
  1. Pass world-space position + normal from vertex → fragment shader (three.js's standard material
     already has `vWorldPosition`-equivalent data available via `#include <worldpos_vertex>`; confirm
     exact varying name against the r185 shader chunks before wiring).
  2. In the fragment shader, compute blend weights from `abs(normal)` (dominant axis gets more weight),
     sample the diffuse+roughness textures 3× (once per axis, using the two other position components
     as UV), blend by weight.
  3. Multiply the existing flat `diffuseColor`/`roughnessFactor` by the triplanar sample (so IFC's real
     per-element color tint still comes through — texture adds detail, doesn't replace the "trust IFC
     data" color).

### Texture sourcing
CC0 (public domain, no attribution required) PBR sets from Poly Haven or ambientCG — same sources
named for Layer 2's HDRI. Starter set (cover the highest-population classes first, check actual
per-building class counts before finalizing the list — don't guess): concrete (walls/slabs), glass
(windows/curtain-wall — reuse the night-mode work's class detection: `IfcWindow`/`IfcCurtainWall`/
transparent `IfcPlate`), brushed/painted metal (railings, doors, MEP), drywall/plaster (interior
partitions). 4 texture sets is enough for a first pass — don't over-scope the library before seeing
whether the technique even reads well on real geometry.

### Class → material dispatch
Reuse the exact pattern `STD_MAT` already uses for color fallback (`streaming.js:270-319`, keyed by
`ifc_class`) — add a parallel `TRIPLANAR_MAT` table mapping `ifc_class` → `{diffuseTex, roughnessTex,
scale}` (scale = world-units-per-texture-tile, needs tuning per texture so bricks/panels don't look
stretched). Same dispatch site as the existing color lookup — one extra branch, not a new system.

### Implementation order
1. Source/download the 4 starter texture sets (diffuse+roughness only).
2. Write the triplanar `onBeforeCompile` shader injection, gated to still-refine-only, test on ONE
   material class first (concrete walls — highest element count in most buildings) before wiring the
   rest.
3. Verify live: does it actually look like a textured surface, not a smeared mess? (Triplanar seams at
   45°-ish normals are the classic failure mode — check corners/roof edges specifically.)
4. Measure the per-fragment cost delta (frame time during the Alt+S accumulation, before vs. after) —
   confirms the "still-render-only" gate is actually necessary/justified, and catches the technique
   being unexpectedly expensive before wiring the remaining 3 texture sets.
5. Wire the remaining classes (glass, metal, drywall) once concrete is confirmed working.
6. Screenshot before/after comparison, same discipline as this session's other work — no claiming a
   visual win without a saved image proving it.

### Witness / log tags to add
`§TRIPLANAR_INIT class=<ifc_class> tex=<name>` on first material dispatch, `§TRIPLANAR_PERF ms=<delta>`
for the cost measurement in step 4 — matching this project's existing `§`-tag convention so the result
is checkable from console output, not just eyeballed.

### Sources (triplanar mapping technique, consulted this session)
- [Triplanar Mapping — 3dverse Documentation](https://docs.3dverse.com/engine/rendering/pbr-material-shaders/triplanar)
- [Tri plannar mapping in three.js — three.js forum](https://discourse.threejs.org/t/tri-plannar-mapping-in-three-js/40335)
- [World space UVs & Triplanar Mapping — Cyan](https://cyangamedev.wordpress.com/2020/01/28/worldspace-uvs-triplanar-mapping/)
- [Playing with Texture Projection in Three.js — Codrops](https://tympanus.net/codrops/2020/01/07/playing-with-texture-projection-in-three-js/)

## LAYER 4 — Global illumination / bounce light (the hard problem)
Three options, honestly compared:
1. **Baked lightmaps** (the traditional real-time-archviz approach — Enscape/Lumion/Unreal
   Lightmass): needs a second UV channel (`uv2`) unique-unwrapped per surface. **Checked this
   session: no `uv2`/lightmap-UV generation exists anywhere in this pipeline (`streaming.js`,
   `loader.js`) — IFC-extracted BREP/extruded geometry has no such channel.** Building an automatic
   UV-unwrapper for arbitrary BIM geometry is itself a substantial, error-prone project (this is a
   known-hard problem in the CAD/BIM tooling space generally, not specific to this app) — treat as
   OUT OF SCOPE unless a dedicated session is opened for it specifically.
2. **Real path tracing** (`three-gpu-pathtracer` or similar): the most direct route to real GI/soft
   shadows/reflections, but **confirmed incompatible with `InstancedMesh`/`BatchedMesh`**, which is
   how essentially all of this app's large-building geometry renders (the whole `MOBILE_PERF.md`
   perf stack depends on it). Would need converting instances to individual meshes for a snapshot —
   itself potentially slow/heavy on exactly the large buildings where this matters most. Ruled out
   earlier this session for this reason; still true.
3. **Screen-space GI (SSGI)**: a post-process technique operating on the already-rendered
   color/depth/normal buffers — doesn't care about instancing or UV channels at all, since it never
   touches the mesh data directly. **This is the realistic middle path** for this specific
   architecture: real bounce-light/AO improvement, compatible with the existing InstancedMesh/
   BatchedMesh geometry as-is, no UV-unwrapping project required. Not yet researched for a concrete
   three.js implementation (unlike TAARenderPass, no specific library identified/vetted yet — next
   step if this layer is prioritized).
**Recommendation**: skip lightmap-baking and full path-tracing (both structurally blocked or
disproportionate effort for this codebase); if GI is wanted, research an SSGI post-process pass
specifically.

## OPEN QUESTIONS
- Layer 3: UV question RESOLVED — triplanar mapping needs no UV data, see §LAYER 3 spec above. Ready
  to implement.
- Layer 4: is an SSGI-class three.js addon/technique available and compatible with r185 + this
  EffectComposer pipeline? Not researched — `SSGINode` exists officially but lives in the TSL/
  WebGPU-node postprocessing system, architecturally separate from this app's classic
  `EffectComposer`+`Pass` pipeline (the `TAARenderPass` this session ported is classic-Pass, not TSL).
  Needs a feasibility spike into this app's existing WebGPU-compat-mode code before committing effort.
- Scope check-in: is "good archviz render" (the honest ceiling, §HONEST VERDICT) actually the bar
  wanted, given it's short of true photorealism? Confirmed this session — user agrees, proceeding.

## NOT IN SCOPE (this spec)
- True photograph-indistinguishable output (see §HONEST VERDICT — structurally unreachable here).
- Baked lightmap GI (blocked — no UV2 infrastructure, separate project if ever pursued).
- Real path tracing on full buildings (blocked — InstancedMesh/BatchedMesh incompatibility).

## SESSION RECORD (2026-07-15, continued — Layer 1 shipped, Layer 3 concrete wired+verified)
**Layer 1 (Alt+S TAA still-refine) pushed to production this session** — cherry-picked the 3
still-refine commits onto fresh `main` as a new branch (`fix/still-refine-taa`, PR #801), since
`fix/night-mode-window-glow`'s earlier squash-merge (#798) had diverged that branch's history from
main (see repo CLAUDE.md's own squash-merge-reuse warning — this is a live example of it). Both CI
checks passed, merged, GitHub Pages redeployed, confirmed live via a direct fetch of the deployed
`effects.js`/`TAARenderPass.js`.

**Layer 3 concrete (IfcWall) implemented, verified, one real bug found+fixed — NOT pushed** (stays
on `fix/night-mode-window-glow`, local commit only, per the standing push-pause; user's instruction
was specifically "push the Alt-S effect," not Layer 3).
- Sourced 4 CC0 texture sets from ambientCG (concrete/plaster/metal + diffuse+roughness) via its
  public API — see `viewer/textures/materials/NOTICE.txt`. Only concrete (`IfcWall`) wired this pass,
  per the spec's own "test one class before wiring the rest" order — plaster/metal sourced but unused.
- **Real bug found via headless verification, not eyeballing**: three.js can silently recompile a
  material's shader program (fresh `onBeforeCompile` call → fresh default-valued uniforms) in
  response to renderer state changes. This happened on the very first still-refine frame and reset
  `uTriActive` back to 0, silently undoing effects.js's one-time uniform push and leaving the
  concrete texture dark for the entire 16-sample accumulation — confirmed via direct
  `page.evaluate()` inspection of the live uniform value (0 mid-accumulation, should have been 1).
  Fixed with a per-material `onBeforeRender` that re-asserts the live `A._stillRefineActive` value
  every frame — self-heals across any recompile instead of relying on a single push at start time.
  **This class of bug is easy to miss with screenshot-only verification** — the shader still
  "worked" (compiled, no console errors), it just silently rendered flat. Worth remembering for any
  future onBeforeCompile-based runtime-toggle work in this codebase.
- Verified live (headless Chrome, `viewer/lib/puppeteer` via bim-compiler's node_modules — no
  Playwright suite involved, matches this project's whitebox-first + screenshot-before-claiming-a-
  visual-win discipline): `§TRIPLANAR_INIT`/`§TRIPLANAR_TEX_READY`/`§TRIPLANAR_PERF` log lines all
  fire correctly; `uTriActive` confirmed `1` mid-accumulation after the fix; before/after screenshot
  (camera pointed directly at a real `IfcWall` element queried from the DB — `~/Pictures/Screenshots/
  HHS_triplanar_concrete_{before,after}_2026-07-15.png`) shows real concrete grain appearing, bounded
  correctly to just that wall panel, no visible triplanar seams at the angle tested.
- **Finding worth flagging**: this specific building (HHS_Office_Federated) has only 12 `IfcWall`
  elements total (queried directly from `element_transforms`/`elements_meta`) — most of the exterior
  envelope in typical camera framings is `IfcCurtainWall`/glazing, not opaque wall. A generic
  isometric screenshot mostly misses the concrete texture entirely; had to query the DB for a specific
  wall GUID and point the camera there directly to get a meaningful before/after. This means Layer 3's
  visual payoff on THIS building will look modest until plaster (`IfcWallStandardCase`, more common
  as interior partitions) and/or metal are wired too — concrete alone has limited surface area here.
- Trigger UX discussed and settled: no confirm dialog before generating, no "tear down?" dialog on
  move — the render itself is the feedback (visible progressive sharpening during accumulation,
  instant snap-back to flat/live on interaction). Nothing further to build for this; already matches
  how Alt+S already behaves.
- **Update, same session**: user confirmed seeing the concrete effect on localhost and asked to
  "throw in all we got" — wired plaster (`IfcWallStandardCase`, `IfcCovering`) and metal (`IfcBeam`,
  `IfcMember`, `IfcPlate`, `IfcRailing`, `IfcPipe`/`PipeFitting`/`PipeSegment`, `IfcDuct`/
  `DuctFitting`/`DuctSegment`, `IfcCableCarrier`) onto the same proven pattern, plus concrete now
  also covers `IfcSlab`/`IfcColumn`/`IfcFooting`/`IfcPile`/`IfcStair`/`IfcRamp` (not just `IfcWall`).
  Class groupings taken directly from STD_MAT's own real-world-material comments — no new invented
  mappings. Verified live: 8 distinct triplanar materials across all 3 texture groups in one frame,
  all 6 texture files load, before/after screenshot shows both concrete grain and brushed-metal
  streaking on real geometry simultaneously (`~/Pictures/Screenshots/
  HHS_triplanar_all_classes_{before,after}_2026-07-15.png`). Glass (`IfcWindow`/`IfcCurtainWall`)
  deliberately left untextured — a transmissive/reflective material doesn't suit a diffuse triplanar
  texture the way opaque concrete/plaster/metal do; still relies on envMap reflection as before.
  All 6 sourced texture files are now in active use — nothing sourced-but-unwired remains.

## OPEN QUESTIONS (updated)
- Real-GPU perf measurement of the triplanar cost (spec step 4) — only headless/software numbers
  exist so far (~18-22s for 16 samples under SwiftShader), not representative of real-GPU cost with
  now 3x the material/texture variety active simultaneously.

## SESSION RECORD (2026-07-15, continued — Alt+S photoshoot bundling: sky + amber glow, POC)
User asked to get as close to the reference image as possible for a "photoshoot" moment, explicitly
authorizing artificial/fabricated staging for presentation purposes only (not extracted BIM data,
not touching any real logic) — confirmed this is a POC, governance decision deferred to later.
- Bundled into the SAME Alt+S trigger, all auto-reverting on interaction: the already-existing
  Preetham sky shader (shown at low elevation for evening/sunset side-lighting) + `A.toggleNightMode()`'s
  existing amber fixture-glow/window-glow mechanism (including its synthetic per-storey fallback,
  already built for buildings with zero real `IfcLightFixture` data — reused, not duplicated).
  `toggleNightMode()`'s own moonlight sun/ambient/hemi override is undone immediately after, so the
  sunset mood isn't replaced by night-black.
- **Explicitly out of scope, per user**: ground/corner/roof decorative light props (lanterns, accent
  lights) — this building's IFC data has zero real light-fixture elements of any kind, and standalone
  scene-dressing geometry not in the source model would cross the extract-only line beyond what's
  already precedented (the synthetic per-storey fixture *position* fallback, not fabricated meshes).
  Building-only theme, sky itself deliberately left untouched further per user ("remains the same").
- **Real bug #1 found+fixed**: `A.toggleShadow()` is a 4-state ground-texture CYCLE (off→grass→
  earth→paved→off), not a boolean toggle — calling it twice to "undo" advanced to the next texture,
  still shadow-on. (Fixed, then the whole shadow/ground-cycling path was DROPPED from the photoshoot
  per user request anyway — sky-only now, no ground shadow.)
- **Real bug #2 found+fixed, user-observed live**: Alt+S could self-cancel almost instantly — the
  physical act of pressing the shortcut can itself nudge the mouse, and cancellation is wired to real
  pointerdown/wheel/controls-start signals (by design, from the Layer-1 bugfix earlier this session),
  so the incidental nudge and the deliberate keypress landed in the same gesture. Fixed with a 500ms
  grace window on `A.stopStillRefine()` — a cancel signal within that window of start is ignored, a
  real interaction after it still cancels normally. Verified via simulated pointerdown timing.
- Verified live end-to-end: state (sky visible / night mode) correctly false→true→false across a
  full trigger+move cycle. Screenshots: `~/Pictures/Screenshots/HHS_photoshoot_{before,during}_2026-07-15.png`.
- Not merged/pushed — local commits only on `fix/night-mode-window-glow`, per the standing push-pause.

### Round 2, same session — freeze-until-interaction, ground/skyline/uplighting, camera-facing edges
- **Real bug, user-observed on real GPU**: 16 samples finish in ~150ms, and the old teardown ran on
  natural completion too (same path as cancellation) — the whole photoshoot flashed past almost
  invisibly. Split into `_finishStillRefine()` (natural completion — stops the RAF loop only, leaves
  composer/textures/staging exactly as accumulated) vs `_teardownStillRefine()` (real interaction only
  — full revert). Now behaves like Night/Shadow mode: stays on until you actually turn it off.
- **Real bug, user-observed**: Alt+S could self-cancel almost instantly — pressing the shortcut can
  itself nudge the mouse, and cancellation is wired to real pointerdown/wheel/controls-start. Fixed
  with a 500ms grace window on `A.stopStillRefine()`.
- Added ground uplighting (4 corners) + roofline edge-lining + a full-ring distant skyline silhouette
  (28 boxes, ~2000 sparkled window-lights, warm/cool mix) — all POC staging, anchored to this
  building's own real bbox (queried fresh), explicitly authorized as presentation-only fabrication.
- **Round 2.1 fixes** (same props, after live feedback): dropping shadow mode had also hidden
  `A.ground` entirely (black void) — restored directly (earth texture + warm tint), independent of
  the shadow-cycle system. Uplights pulled in from the outside edge so they sit against the wall face.
  Edge-lining changed from "all 4 sides always" to "only the edge(s) facing the camera" (dot product
  of edge normal vs. camera direction) — this also fixed a stray diagonal line crossing the whole
  frame (the far/notch-crossing edge of this L-shaped building's bbox approximation, floating over
  empty space at a grazing angle) — verified the edge geometry itself was correctly sized/positioned
  via direct object inspection before concluding it was a which-edge-to-draw bug, not bad geometry.
- Confirmed by user: this whole mode is meant to be an elaborate, deliberately-expensive prep step
  (matches the feature's own original framing at the top of this file) — don't hold back on
  thoroughness for the one-time staging setup.
- Screenshot: `~/Pictures/Screenshots/HHS_photoshoot_v2_ground_camerafacing_2026-07-15.png`.
- Not merged/pushed — local commits only on `fix/night-mode-window-glow`, per the standing push-pause.

## RESUME BRIEF (2026-07-15, end of session — read this first, next session)
**Current state: real progress, explicitly NOT complete.** User's own words: "I can see there is
some effort but it is not a complete one." Read this whole section before touching code — it
supersedes the blow-by-blow round-by-round history above for the purpose of picking up the work;
that history stays for context on what's already been tried and ruled out (don't re-litigate it).

### The actual goal, stated plainly (user's own framing, 2026-07-15)
**"The building is in late evening render, with materials in little light, thus artificial lights
are placed to light up the facade facing camera."** That is the whole brief for the lighting-props
part of this feature. Concretely:
- It's dusk/evening — the sun is low, ambient light is naturally low, so the building's own
  materials would read as dim/underexposed if left alone.
- Artificial lights (ground uplights + roof downlights — confirmed this session, edge-lining
  explicitly dropped) exist to COMPENSATE for that — to properly expose/illuminate whichever
  facade is CURRENTLY facing the camera, the way real architectural night photography uses
  supplemental lighting to expose the facade against a darker sky.
- **This is NOT yet done.** The current code places uplight+downlight pairs at all 4 footprint
  corners uniformly, with no preference for the camera-facing side specifically. That's a real gap
  against the stated intent, not just a nice-to-have.

### Hard constraint, repeated by user for emphasis
**"This is done with general deterministic code to apply to any building in any angle on canvas."**
No hardcoding to HHS_Office_Federated, no hardcoding to any specific camera position. Every
position/orientation must be derived at runtime from (a) the currently active building's own real
bbox (`A.dbQuery` on `element_transforms` — already the pattern in use) and (b) the CURRENT
`A.camera.position`/orientation at the moment the still-refine fires. Before considering any
lighting placement "done," test it against a SECOND building and from at least 2-3 different
camera angles on the SAME building — this session only ever verified on HHS_Office_Federated from
a handful of angles, which is exactly how the stale-camera-angle caching bug slipped through once
already (see Round 2 above) and is presumably why the wall-wash lights still aren't confirmed
"evident enough."

### Three concrete open items, in priority order
1. **Camera-facing facade awareness for the wall-wash lights.** Currently 4 uniform corner pairs
   (8 lights total). Needs: determine which facade(s) of the building's footprint face the CURRENT
   camera (reuse the dot-product-of-outward-normal-vs-camera-direction technique already built and
   verified for the now-removed edge-lining — that MATH was correct, only the edge-rendering
   approach itself was dropped) and concentrate/strengthen the wall-wash lighting there specifically,
   not spread evenly around the whole footprint. Recompute fresh every time the photoshoot fires
   (not cached) — this was the exact bug already found and fixed once this session for the
   edge-lining; don't reintroduce it here.
2. **"Surface material of metal, concrete are all not evident enough"** (user, verbatim, this
   session) — the Layer 3 triplanar textures (concrete/plaster/metal, all wired and verified
   working per the earlier SESSION RECORD entries above) aren't visually strong/contrasty enough in
   practice. Candidates to investigate: the `uTriNorm` compensation factor per texture (streaming.js
   `_TRI_CONCRETE`/`_TRI_PLASTER`/`_TRI_METAL`) may be over-flattening contrast; `tileMeters` may be
   too large (too little visible pattern per wall) or too small (aliased into a flat average at
   distance); the multiply-blend against the base IFC color may need a stronger contribution ratio.
   Verify with a zoomed-in before/after crop (same technique used earlier in this session to catch
   the "no visible change" false read) before/after any tuning change — don't just eyeball a wide
   establishing shot.
3. **The sun/ambient/hemi color during the photoshoot is never actually warmed** — `_applyPhotoStaging()`
   calls `A.toggleNightMode()` then immediately restores `A._nightSaved`'s ORIGINAL (daytime-neutral,
   e.g. `0xfff0dd`) sun color, specifically to undo night-mode's moonlight-blue override. That was
   the right call to avoid moonlight-blue, but it means the building's own walls are never given a
   deliberate golden-hour/evening color treatment — only the separate light props around it change.
   If item 1+2 above don't fully deliver "the whole building rendered into the mood," this is the
   remaining lever: a deliberate warm sun tint (not neutral-white, not moonlight-blue) during the
   photoshoot specifically.

### What IS confirmed working, don't re-verify from scratch
- Alt+S (Layer 1, TAA still-refine) — **live in production** (PR #801, merged, confirmed via direct
  fetch of the deployed file). Unrelated to the rest of this brief; nothing further needed here
  unless a new bug is reported.
- Layer 3 triplanar textures fire correctly on concrete/plaster/metal classes, uniform-branch gated
  (near-zero cost when off), self-heals across shader recompiles (`onBeforeRender` fix) — the
  MECHANISM is proven sound; item 2 above is about visual strength/tuning, not correctness.
  Still-refine now correctly FREEZES until real interaction (doesn't self-revert on natural
  completion) and has a 500ms grace window absorbing the incidental Alt+S keypress nudge — both
  fixed and verified this session, no further action needed on either.
  Ground plane restored (earth texture + warm tint), fully reverts on teardown — verified.
  Skyline silhouette (28-box ring + ~2000 sparkled window-lights) reads well per the last screenshot
  — no reported issue with it specifically.
- All work is LOCAL ONLY on `fix/night-mode-window-glow` in `/tmp/wt-mobile-perf-fix` (bim-ootb
  worktree) — not pushed, not merged, per the standing push-pause. Only Alt+S itself was ever
  explicitly authorized to go live (and did, via a separate cherry-picked branch — see Round 1).

### Suggested first steps, next session
1. Fresh localhost server + a normal (non-headless, real GPU) live test first, from 2-3 different
   angles on HHS, to get an honest current baseline screenshot before changing anything further —
   this session's own back-and-forth shows how much daylight there can be between "verified in
   headless SwiftShader" and "what the user actually sees live."
2. Tackle item 1 (camera-facing wall-wash) — reuses math already proven correct once this session.
3. Tackle item 2 (texture contrast) with the zoomed-crop verification discipline.
4. Only then reconsider item 3 (deliberate warm sun tint) if the mood still isn't landing.

## SESSION RECORD (2026-07-15, continued — resume-brief items 1/2/3 all closed)
All three prioritized open items from the RESUME BRIEF above are now implemented and verified —
local commit only (`fix/night-mode-window-glow`, `/tmp/wt-mobile-perf-fix`), not pushed, per the
standing push-pause. Followed the brief's own suggested-first-steps order.

**Item 1 — camera-facing facade wall-wash (`viewer/effects.js`).** Replaced the 4 uniform
footprint-CORNER uplight+downlight pairs with 4 footprint-EDGE pairs (one per facade, positioned
at each edge's own midpoint) — same bbox-rectangle approximation as before, general to any
building. Added `_updateFacadeFacingLights()`, reusing the exact dot-product-of-outward-normal-
vs-camera-direction math already proven correct once this session for the removed edge-lining
(git history `cd8df02`) — continuous `strength = 0.3 + 0.7*facingFrac` per facade (not binary),
recomputed FRESH every `_showPhotoProps(true)` call (every Alt+S trigger), never cached — the
exact bug class already found+fixed once for the old edge-lining. `§PHOTO_FACING` log line added.

**Item 2 — triplanar texture contrast (`viewer/streaming.js`).** Added a `contrastBoost` field per
material group (concrete 1.6, plaster 1.5, metal 1.9) and a `uTriContrast` uniform. The existing
`normFactor`/`uTriNorm` only recenters the texture's AVERAGE luminance to ~1.0 — it does nothing
for how much the texture VARIES around that average, which is why it read as flat. Fix expands
that deviation before the multiply: `triContrasted = clamp((triDiffuse*uTriNorm - 1.0) *
uTriContrast + 1.0, 0.0, 2.5)`, same average brightness, materially more visible grain/streak.

**Item 3 — deliberate warm sun tint (`viewer/effects.js` `_applyPhotoStaging()`).** Was restoring
the building's ORIGINAL daytime-neutral sun/ambient/hemi colors after undoing night-mode's
moonlight override — meaning the walls themselves never got an evening treatment. Now lands on a
deliberate golden-hour tint instead: sun `0xffa55c` at `0.7×` original intensity, ambient
`0x8a6a55`, hemi-sky `0x6a5a7a`, exposure `0.85×` original — all building-independent constants,
scaled off this building's own saved daytime baseline (`A._nightSaved`, untouched so teardown
still restores the true original exactly).

**Verification (headless SwiftShader + real-GPU, per the brief's own discipline):**
- Syntax-checked both files (`node --check`) before any browser test.
- Headless Chrome (puppeteer, `--use-gl=angle --use-angle=swiftshader`) on **two buildings**
  (`HHS_Office_Federated`, 12 IfcWall + curtain-wall L-shape; `warehouse_gardenworld`, a small
  rectangular footprint) × **3 camera angles each** (0°/90°/200° or equivalent orbit) — zero
  `pageerror` events across all 6 runs. `§PHOTO_FACING` confirmed a DIFFERENT facade at ~1.0
  strength each time the angle changed, on both buildings — the determinism requirement ("any
  building, any angle, no hardcoding") is met, not just claimed.
- Zoomed-in close-up on one real `IfcWall` GUID (queried fresh from `element_transforms`, same
  discipline as the original Layer-3 verification): flat grey wall before Alt+S → visibly mottled/
  swirled concrete grain with fine surface cracks after — item 2's "not evident enough" complaint
  is resolved, confirmed by a saved before/after crop, not eyeballed on a wide shot.
  `§`-log confirmed `sun.color=ffa55c intensity=3.08` (exactly the item-3 constant × 0.7 of the
  captured daytime baseline) mid-accumulation via direct object inspection.
- **Real GPU, non-headless** (this machine's actual Xorg session, `DISPLAY=:0`, plain
  `google-chrome-stable` — see `project_machine_chrome_firefox_gpu_launchers` memory for why no
  special GPU flags are needed here): confirmed the ~150ms-for-16-samples timing from the earlier
  SESSION RECORD entry still holds (144ms warm-cache run), and — the actual visual proof — two
  angles on HHS each show ONLY the currently-facing facade with a warm ground-level glow (the
  opposite facade stays dim), matching the user's literal ask ("light up the facade facing
  camera"). Screenshots: `~/Pictures/Screenshots/PHOTOREAL_realgpu_{flat,staged}_{30,160}_
  2026-07-15.png` (flat baseline vs. staged, both angles) plus the headless multi-angle/multi-
  building set and the zoomed-in wall crop, all timestamped the same day.
- Not merged/pushed — local commits only, per the standing push-pause.

## SESSION RECORD (2026-07-15/16, continued — dusk sun/shadow/ground debugging, Hospital reference)
Long, dense follow-on session, all on Hospital (the real stress-test building — 63,182 elements,
101×151×43m, irregular multi-wing footprint). Net result: the wall-wash/contrast/tint work from
the PREVIOUS record above (PR #805) shipped to production this session — user explicitly said
"Deploy now" after confirming it looked right on localhost; cherry-picked the 9 not-yet-merged
commits onto a fresh branch off `origin/main` (the old `fix/night-mode-window-glow` had already
been squash-merged once before — same divergence trap the repo's own CLAUDE.md warns about),
pushed, CI green, merged (bim-ootb PR #805, `94318d7`), GitHub Pages redeployed. **That part IS
live.** Everything below is a SEPARATE, still-local-only branch
(`feat/photoshoot-sun-dusk-reflection`, `/tmp/wt-mobile-perf-fix`) — explicitly NOT deployed, per
direct instruction ("do this only in localhost... I shall do it and report back").

**A real self-caught process failure, worth remembering:** a dark-side accent spotlight was
built, then found broken specifically on Hospital (placed near/inside the already-lit side, not a
genuine dark facade) — root cause: the facade-edge math assumes a rectangle, and Hospital's real
footprint has an entire bbox corner with ZERO elements (confirmed via direct query — 30 elements
in one corner, 0 in another, out of 63,182 total). User caught this live and asked "do you
acknowledge you drifted" — yes. That spotlight code was stashed (not deleted — recoverable) and
never shipped.

**Sun/shadow/reflection work (all local, all on top of the shipped PR #805 work):**
1. Found `A.updateSky()` already repositions the real sun + drives an existing lensflare
   (`scene.js` §S277f) — the "sun reflection" was never broken, just undercut by the photoshoot's
   own exposure cut also dimming the tone-mapped lensflare sprite. Fixed (`toneMapped=false`
   during photoshoot only) + boosted per-material envMapIntensity/metal roughness for a sharper
   specular "hotspot," per user's explicit ask reproducing real sun-behind-camera glint.
2. Lowered dusk sun elevation 8°→6° and enabled REAL shadow-casting (reusing `time_machine.js`'s
   own proven sun-cycle shadow mechanics, not reinvented) for the "long shadow, dramatic facade"
   look — careful to compute the shadow frustum AFTER repositioning the sun (order matters — the
   original `toggleShadow()` code computes frustum before repositioning, which would be wrong here).
3. **Found and fixed a real bug born from my own earlier fix:** a "make ground whiter" emissive
   add is NOT shadow-map-occluded in three.js at all — it flattened the contrast between shadowed/
   lit ground, which is very likely why the user then reported "Shadows? None on the ground."
   Reverted. Same root logic applied to dial back an over-aggressive hemi/ambient blanket boost
   (1.6×/1.3× → 1.25×/1.15×) that likely diluted the discrete point-light addons' visibility too.
4. **Found and fixed a likely root cause for the whole "still not there" pattern across THREE
   separate rounds of real fixes:** `effects.js` AND `streaming.js` are both in `sw.js`'s
   `PRECACHE_ASSETS`, served cache-first — `CACHE_VERSION` was never bumped after any of this
   session's commits, so the service worker most likely kept serving stale cached copies
   regardless of what was on disk. Bumped v751→v752. **Not confirmed whether the user's browser
   had actually picked this up for any of the screenshots reviewed after — this is the single
   biggest open question for next session, see RESUME BRIEF below.**
5. **Found and fixed a real streaming-race bug**, same class already fixed once this session for
   the triplanar shader uniform (§TRIPLANAR_RECOMPILE_FIX) but not applied here the first time:
   the shadow-casting traversal and the material envMap/roughness boost were one-shot pushes at
   Alt+S trigger time — Hospital's rooftop content (589 real trees, 1 helipad, 567 solar panels,
   all confirmed via direct DB query, not guessed) could stream in AFTER that instant and never
   get flagged. Fixed by re-asserting both, idempotently, every accumulation frame.
6. Added atmospheric fog override (warm haze, was a dark blue-purple that darkened distant ground
   further) and made the skyline silhouette denser/closer/bigger (was `envelope*4` radius — so far
   the boxes subtended almost no visible angle, reading as tiny specks).

**Reference-image-driven addons** (`RealistHospital.jpeg`, user-supplied target — analysis: the
drama in that image comes from warm INTERIOR light through glass against a dark shell/cool sky,
NOT sun/shadow drama — a real recalibration worth remembering next session):
- Roof-corner twin spotlight — placed at whichever bbox corner is LEAST camera-facing (the "back
  portion," per explicit correction — first instinct was nearest-camera, which was wrong).
  Recomputed fresh every trigger, same discipline as the facade-facing math.
- That back facade's existing ground/roof wash pair gets an extra 1.8× boost ("ground based
  spotlights too") — reuses the existing fixture, no new light objects.
- Entry-door sconces from real `IfcDoor` positions (capped to 6, lowest storeys first — a proxy
  for "ground floor," not a real exterior-perimeter check).
- Tree uplighting from real vegetation-named elements (capped to 15 for perf).
- **Checked, not fabricated:** Hospital has zero person/human/staffage elements and zero
  ground-level trees (all 589 real trees sit at ONE mid-building terrace, z≈179.5-179.9 of a
  159.8-203.2 range — a rooftop garden, not street-level planting). Both "harvest people" and
  "harvest ground trees" asks are genuinely not available in this building's data — nothing to
  wire up, confirmed via query not assumed.

**Deliberately deferred, not forgotten:**
- **Material reference library** (fix flat-teal-fallback trees/solar-panels/helipad — they render
  in the generic `IfcBuildingElementProxy` teal because they have no real IFC color). Turns out
  bigger than first estimated: `element_name` isn't selected anywhere in the geometry-loading/
  batching queries today, and elements are grouped into InstancedMesh/BatchedMesh by
  `(rgba, ifcClass)` only — adding a name-pattern lookup means touching multiple SQL queries AND
  the batching grouping keys in a performance-critical file, not just the material-creation
  function. Too invasive to do blind without testing. User suggested ~20 curated starter
  materials as reasonable scope when this is tackled properly, as its own session.
- **Cinema pill (360° fly-around clip)** — user wants a camcorder-icon pill, offline/background
  batch: pivot at building center, step camera 1°/frame around a full 360° loop, run a full
  still-refine per frame, assemble into a clip. Target: 360 frames, 15fps = exactly 24 seconds
  (matches the user's ask precisely). Estimated ~1-2 minutes of background production time at the
  ~150ms/frame real-GPU cost already measured (caveat: only measured on a SMALL building — Hospital's
  much heavier scene could cost noticeably more per frame, untested). **Not started — spec only,
  no code written for this yet.**
- Task 5's original framing ("drop front wash since sun handles it, back-only") was supersede by
  the reference-image direction (roof-corner + back-boost instead) — the FRONT-facing wash was
  never actually removed; it still runs at full strength unchanged. Worth a deliberate decision
  next session: keep both hero-front-wash AND back-accent, or actually drop the front now that the
  sun/lensflare/shadow work exists to carry that job. Not resolved, don't assume either way.
- Ground grass+paver COMBINED pattern (grass base + paved rectangles, like a real site plan) —
  user's simpler fallback ("why not just light it up," "use paved, add concrete look") is what
  shipped; the fancier combined-pattern idea is still just an idea, not spec'd.

**Everything in this section is local-commit-only** on `feat/photoshoot-sun-dusk-reflection`
(`/tmp/wt-mobile-perf-fix`), stacked on top of the NOW-MERGED PR #805 work. None of it has been
tested by the assistant in this session — the user explicitly and repeatedly instructed
"don't test, I'll test and report back," which was honored throughout. All reasoning above is
grounded in re-reading the actual code + direct read-only DB queries against Hospital's real data,
not speculation — but zero browser-level confirmation exists yet for any of items 1-6 or the addons.

## RESUME BRIEF (2026-07-16 — read this first; do a STRATEGIC REVIEW before writing any code)
**Do not start tuning constants again on arrival.** This session's biggest lesson: three separate
rounds of real, well-reasoned fixes each got reported as "still not there," and the actual causes
turned out to be (a) a stale service-worker cache possibly serving old code the whole time, and
(b) one of the assistant's OWN earlier fixes (ground emissive) directly undoing another goal
(shadow visibility) — neither of which a fourth round of "increase the number more" would have
found. Guessing-and-patching had run its course; reading the actual mechanism (sw.js precache
list, `_setGroundColor`'s real branching logic, three.js's shadow-occlusion model) is what found
both. Do the same kind of grounded reading before changing any constant further.

**Step 1, before anything else:** confirm the browser is actually running current code. Open
DevTools → Application → Service Workers, confirm the active worker is `v752` or later (bumped
this session specifically because `effects.js`/`streaming.js` are both cache-first precached
assets and every fix this session touched one or both). If it's still on an older version, a hard
refresh (or closing all tabs of the app and reopening) is needed before ANY of this session's
visual claims can be evaluated at all — everything reported as "still not there" may simply never
have been loaded.

**Step 2:** get ONE fresh, confirmed-current-code screenshot from the user on Hospital, at a
camera angle showing at least two facades (so the facing-vs-back distinction is visible), before
making further changes. Compare it point-by-point against what SHOULD now be true:
- Ground: 'paved' texture, no emissive hack (reverted), warm fog haze at distance, hemi/ambient
  at the dialed-back 1.25×/1.15× (not the earlier 1.6×/1.3× that likely flattened everything).
- A real cast shadow from the building onto the ground (shadow-casting is enabled at the dusk sun
  angle, re-asserted every accumulation frame — should no longer be silently skipped by the
  emissive-washout bug).
- Roof-corner twin spotlight + boosted ground wash specifically on the LEAST-camera-facing side
  (not the front — that was an explicit correction mid-session, easy to mis-check).
- Entry sconces near real door positions, tree uplights near the real rooftop-terrace trees.
- A denser, closer, more visible skyline silhouette on the horizon.
- A stronger, sharper metal/glass specular hotspot when the sun is roughly behind the camera.

**If step 2 still looks wrong** after confirming step 1 is genuinely current: don't immediately
patch again — ask what specifically is missing/wrong the same way this session's later rounds did
(exact screenshot, exact complaint), and re-read the relevant mechanism before touching numbers.

**Then, in priority order the user has already stated:**
1. Decide the front-wash-vs-back-only question left open above (§Task 5 note).
2. Scope the material reference library properly as noted (≥20 curated real-material starter set,
   name-pattern lookup, touches SQL queries + batching keys — needs its OWN careful session, not a
   quick add-on, since it's a real change to a performance-critical file).
3. Spec + build the Cinema pill (360° fly-around, 360 frames @ 15fps = 24s, offline/background).
4. Only then revisit the combined grass+paved-rectangle ground pattern, if still wanted.

**Nothing in this session has been pushed or deployed** except the PR #805 work (now live). The
push-pause remains in effect for everything else until the user lifts it or names a breakthrough.
