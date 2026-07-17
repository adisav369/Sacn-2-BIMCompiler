# ⚠ DO NOT REMOVE — Mobile Performance: the shipped stack + open levers (the ONE place)
# SCOPE: general mobile (3D viewer) speed. Consolidates what was scattered across the
#   `project_s271_mobile_perf` + `project_s280c_perf` memories and 4 done-prompts into one
#   live work-order. A mobile-perf session works FROM HERE.
# PRIME RULE: MEASURE FIRST. Real device (or DevTools mobile + CPU/GPU throttle) → §-log
#   the metric → change ONE lever → re-measure on the SAME device/building. No speculative
#   perf edits; every change names the number it moves. Read the log after every run.
# CAVEAT: the stack below is a MAP from point-in-time memory — VERIFY each item against
#   live code (main.js / scene.js / dlod.js) before relying on or changing it.

## WHERE IT LIVES (code)
- `viewer/main.js`    — DPR scaling, on-demand render gate, tab pause
- `viewer/scene.js`   — antialias off, WebGL context-lost handler
- `viewer/dlod.js`    — DLOD disabled (r160 native culling)
- `viewer/streaming.js` / `viewer/loader.js` — DB streaming, MergedMesh vs BatchedMesh routing

## THE SHIPPED STACK  (from project_s271_mobile_perf — bim-ootb, sw v444+)
1. **r160 native BatchedMesh culling** — `perObjectFrustumCulled=true` (zero JS cost)
2. **No antialias** (scene.js) — eliminates 4× MSAA fill cost
3. **DPR 1 at rest / 0.75 during orbit** (main.js) — 44% fewer pixels while dragging
4. **On-demand render gate** (main.js) — MOBILE ONLY, skip render when idle
5. **Tab pause** (main.js) — `cancelAnimationFrame` when backgrounded
6. **DLOD off** (dlod.js) — r160 handles culling natively
7. **WebGL context-lost handler** (scene.js) — banner + reload on Chrome idle-kill

## THE GPU FALLBACK  (from project_s280c_perf)
- **MergedMesh fallback** when `WEBGL_multi_draw` is ABSENT → 197 draws instead of 70K.
  Affects Intel iGPU + **ALL mobile**. Witnesses: `§S280c_MULTI_DRAW`, `§S280c_PERF_REPORT`.

## DELIBERATELY NOT ON MOBILE  (proven net-negative — do NOT re-add without a fresh witness)
- InstancedMesh zero-scale (buffer re-upload cost > savings)
- Custom BatchedMesh frustum tick (redundant with r160 native)
- On-demand render on DESKTOP (panels don't call markDirty → static screen)

## HARD CONSTRAINT
- `InstancedMesh.frustumCulled` MUST stay `false` — its boundingSphere is base-geometry
  only, so native culling would wrongly cull instances. (GPU stack depends on this.)

## OPEN LEVERS  (next session — pick by MEASURED impact, biggest first)
1. **[I/O — biggest] First-load is DB-fetch-bound on mobile, not GPU.** The meta+geo split
   (load 17–40M meta, not 251–421M geo) exists but is UNDER-USED, and some meta DBs are
   BROKEN on OCI (`LTU_AHouse_meta.db` = **0 bytes** → falls back to the 421M geo file).
   → Audit every served building's `_meta.db` on OCI; regenerate the empty/missing ones.
2. **[I/O] OPFS-resident DB** — persist the building DB to OPFS so REPEAT opens skip the
   network fetch entirely. OPFS is already proven in `analysis_sidecar.js` / `ANALYSIS_SIDECAR.md`
   (no COOP/COEP needed via the async API). Same idea, applied to the building buffer.
   **This lever is bigger than mobile-only** (2026-07-15, user-reported "fly-thru/navigate feels
   heavier on LTU, was smooth before" — real bim-ootb session, relegated here for a dedicated
   session per user's own instruction, not fixed inline): `[[project_ltu_ahouse_memory_architecture]]`
   (2026-07-13) already measured the root cause on DESKTOP, not just mobile — `sql.js` loads the
   ENTIRE DB into WASM linear memory with no paging (`new SQL.Database(new Uint8Array(buf))`,
   5 call sites: `main.js:812`, `streaming.js:1459/1519/1533/1605`, re-verified live 2026-07-15,
   unchanged). LTU = ~440MB permanently resident (43MB meta + 397MB geo) for the tab's whole life.
   **Fresh real-browser evidence (2026-07-15, user's own LTU load console log, not sandboxed)**:
   `§SPLIT_GEO_LOADED src=cache size=379MB ms=2786` — 2.8 real seconds to load geo.db into memory
   from CACHE alone (no network fetch involved) — this is the pure sql.js-parse-into-WASM cost,
   the same cost pays on EVERY open regardless of mobile/desktop or cache-hit.
   **What was checked 2026-07-15 and came back clean (do NOT re-derive)**: LTU's DB files are
   byte-identical to the 2026-07-13 measurement (same 43,053,056/397,422,592 byte sizes, mtimes
   unchanged since June — ruled out "the data grew"). A full code review of every `viewer/*.js`
   commit between 2026-07-12 13:00 and 2026-07-15 found ZERO new `markDirty`/`requestAnimationFrame`/
   `setInterval` calls and ZERO new `mousemove`/`pointermove`/`wheel` listeners — ruled out an
   obvious discrete regressive commit in that window. **What could NOT be completed**: a live
   before/after frame-time comparison (old commit `f7f27e7` 2026-07-12 vs current HEAD) — every
   attempt timed out streaming LTU's 71MB `extracted.db`/397MB `geo.db` over a single-threaded
   local `python3 -m http.server` in the dev sandbox (tried up to 400s budgets). **Next session
   needs either**: real device/devtools Performance+Memory profiling (bypasses the sandbox
   bottleneck entirely, per this file's own PRIME RULE), or a properly-resourced bisection
   environment (parallel http server, or the real OCI-served URL instead of localhost).
   **OPFS impact study needed before building it** (user's own ask, 2026-07-15, not yet scoped):
   the real open question is API compatibility, not just "will it be faster" — every `A.db.exec()`/
   `A.dbQuery()` call site across `viewer/*.js` (hundreds) currently assumes SYNCHRONOUS sql.js
   calls; `@sqlite.org/sqlite-wasm`'s OPFS-backed VFS is ASYNC-only for real paged I/O (the sync
   OPFS access handle API needs a dedicated Worker, per its own spec) — determine whether this is
   a drop-in swap or requires converting every DB-touching call path to async/await before
   estimating effort or committing to the migration.
   **2026-07-16 follow-up sweep (code-side ruled-out set EXTENDED — do not re-derive):**
   - **The 07-08 → 07-12 13:00 window (incl. the 07-11 merge burst, PRs #727–#757) is clean.**
     Every viewer commit there is event-driven Find-panel/Room-Lens UI (CSS visibility, taxonomy,
     Dijkstra-on-tap, role filter); a grep over the FULL `234d41c..HEAD` viewer diff found only
     bounded/self-clearing timers (`_glowInterval` ≤20 tries, `pulseFrame` finite tween,
     `_checkDone` self-clears) and gated `onBeforeRender` hooks — zero new unconditional per-frame
     work. This extends the earlier 07-12→07-15 review both backward and forward.
   - **TAA/still-refine (#801) gating VERIFIED in current code**: `A._composerEnabled=false` by
     default, `main.js:786/798` bypasses the composer entirely during normal navigation; teardown
     restores `accumulate=false` + prior composer flag. Photoreal shaders (#805/#806 triplanar/
     night/puddles/sparkle/shadows) are all `uTriActive`-style gated or staged+torn-down inside
     Alt+S/Cinema Orbit — and ALL post-date the first lag report (07-15 ~morning) anyway, so they
     are not the origin (could only compound). `renderer.shadowMap.enabled=false` at startup
     (scene.js:60) unchanged. three.js upgrades were 05-24 (r160→r184) and 06-27 (r184→r185) —
     outside "this week."
   - **NEW LEAD, environment not code — GPU identity during the laggy sessions (timeline matches
     exactly):** this machine's Chrome launchers carried broken NVIDIA PRIME offload flags that
     killed WebGL outright and were stripped on **2026-07-13** (`[[project_machine_chrome_firefox_
     gpu_launchers]]`) — the lag was reported 07-15. Verified 07-16 live: X primary provider is
     NVIDIA-0, and the RUNNING Chrome gpu-process has `libnvidia-glcore`+`/dev/nvidia0` mapped —
     i.e. TODAY Chrome renders on the RTX 4060, no iGPU fallback active. But whether the 07-13→
     07-15 laggy sessions ran on Intel/ANGLE-fallback is unknown — and **decidable retroactively**:
     `scene.js:54` has printed `§RENDERER_CAPS multi_draw=... gpu=<UNMASKED_RENDERER>` at EVERY
     viewer startup since S281b. → **Next action (user, 1 min): check the §RENDERER_CAPS line in
     any saved laggy-session console log** (and in a fresh LTU load now). `gpu=Intel/llvmpipe` or
     `multi_draw=off` during lag = environment cause, closed, no code chase. `gpu=NVIDIA` +
     `multi_draw=on` = the sql.js-residency/GC baseline stands and a DevTools Performance+Memory
     recording on a real LTU fly-thru is the only remaining instrument (sandbox cannot, twice
     confirmed).
   **2026-07-16 RESOLUTION of the GPU check + new suspects from the user's fresh LTU log
   (real browser, OCI-served GH Pages viewer):**
   - **GPU healthy in Chrome NOW**: `§RENDERER_CAPS multi_draw=on gpu=ANGLE (NVIDIA ... RTX 4060
     Laptop GPU)` — this session ran on the dGPU with the fast batched path. Whether the 07-13→15
     laggy sessions did is still unknown (check §RENDERER_CAPS in any SAVED log from those days).
   - **Firefox answer (user asked "must have affected FF a bit?")**: NO — the 07-13 fix touched
     only the two `google-chrome*.desktop` launchers; Firefox (snap) was untouched and re-asserts
     its own GPU prefs anyway. BUT FF on this machine NEVER has `WEBGL_multi_draw` (Mesa/driver
     gap, `[[project_machine_chrome_firefox_gpu_launchers]]`) → FF always runs the slow per-draw
     BatchedMesh fallback → LTU is permanently heavier in FF than Chrome. Not new, not a
     regression — if this week's testing mixed FF in, that alone explains "FF feels a bit off."
   - **NEW CONFIRMED HITCH MECHANISM — per-op full-DB persist (`viewer/kernel_ops.js` v8,
     `_persistToIdb` line ~100)**: EVERY committed kernel op — including a plain `ELEMENT_PICK`
     that modifies nothing — schedules, 2s debounced, `sealChain()` + a **synchronous main-thread
     `db.export()` of the ENTIRE 42MB LTU extracted.db** + a 42MB IndexedDB write. Fired twice in
     the user's short log (`§KRN_PERSIST ... size=42056KB` ×2, after ELEMENT_PICK and
     BUILDING_OPEN). Small buildings export a few MB → invisible; LTU pays a ~hundreds-of-ms
     main-thread stall landing 2 SECONDS AFTER the interaction — exactly the "slight lag I can't
     pin to anything" signature, and structurally invisible to every §PERF_PROBE (fires on a
     timer, not in any probed path). Live since 06-08 (`df813ce` KRN_PERSIST_FIX revived a
     silently-dead persist), so a month old, not this-week — but it is the biggest LTU-specific
     interaction-hitch found so far. **Fix candidates (fix-session, PUSH PAUSE in effect)**:
     (a) don't persist on read-only ops like ELEMENT_PICK (op-log row ≠ worth a 42MB export), or
     (b) op-log in a small sidecar DB persisted alone, or (c) port erp/kernel_ops.js v12's
     T7-incremental pattern — note the viewer page LOADS BOTH copies (`§KERNEL_OPS_LOADED v12`
     then `v8` — v8 loads LAST and is 4 versions stale; dedupe/upgrade is part of the same fix).
   **SPEC — fix (a) IMPLEMENTED 2026-07-16 (user go: "Then proceed"), branch
   `fix/krn-persist-readonly-ops`, worktree /tmp/wt-krn-persist, LOCAL-ONLY (PUSH PAUSE):**
   - `viewer/kernel_ops.js` (v8, the copy that wins `window.KernelOps` — viewer.html loads it
     LAST at line 875) `commitOp()`: a conservative deny-list `READONLY_OPS = {ELEMENT_PICK,
     BUILDING_OPEN}` — the two ops PROVEN firing full persists in the user's 07-16 LTU log, both
     observational (a pick highlights, an open is a marker; neither mutates the model). For
     those, skip `_persistToIdb()` and log **`§KRN_PERSIST_DEFER type=<op>`** instead. All other
     types (GRID_MOVE/ADD/DELETE/CALIBRATE/DETECT, DISC_SWITCH, ENACT_MOVE, SECTION_CUT,
     VIEW_FILTER, history replays) persist EXACTLY as before — grey-zone view-state ops stay on
     the persisting side deliberately (no judgment calls beyond the proven two).
   - Deferral is loss-bounded by design: persist exports the WHOLE db, so deferred rows ride
     along in the next mutating op's export. Only a session with ZERO mutating ops loses its
     pick/open log rows on refresh — acceptable (they're navigation events, and universal-history
     keeps its own localStorage record regardless).
   - Deliberately NOT gating inside `_persistToIdb` (its `clearTimeout` would let a read-only op
     reset a pending mutating persist's debounce) — the gate is in `commitOp` BEFORE the call.
   - **Witness (issue it proves): "read-only ops no longer trigger the 42MB export"** —
     localhost §-log: (1) BUILDING_OPEN → `§KRN_PERSIST_DEFER`, no `§KRN_PERSIST` follows;
     (2) console-driven GRID_MOVE → `§KRN_PERSIST` still fires. Cache-bust viewer.html
     `kernel_ops.js?v=4→5`.
   - **DONE 2026-07-16 ✅ (witness): `tests/probe_krn_persist_readonly.js` 7/7 PASS** — real
     viewer on real Duplex (localhost :8149, SW blocked, real 3D tap): BUILDING_OPEN and
     ELEMENT_PICK both → `§KRN_PERSIST_DEFER` with ZERO `§KRN_PERSIST`; GRID_MOVE →
     `§KRN_PERSIST url=...Duplex_extracted.db size=14620KB` with `§KRN_CHAIN sealed=3` (the two
     deferred rows rode along, loss-bounded design confirmed). Zero page errors. Log:
     /tmp/wt-krn-persist-probe.log. **LIVE 2026-07-16 ✅ (user lifted PUSH PAUSE for this
     session's work): PR #808 squash-merged (`4c0f4a0`), sw CACHE_VERSION v763→v764. Live
     verified by fetch-back: kernel_ops.js?v=5 contains §KRN_PERSIST_DEFER, viewer.html
     references v=5, sw.js serves v764. The v764 bump ALSO delivers #807 (the Find-panel
     freeze fix, merged separately without its own bust) to returning browsers — live
     navigate_find.js confirmed carrying the parentSet≤1500 gate. Worktree pruned, branch
     deleted both ends.**
   - **First-minute heaviness on LTU is structural, also visible in the log**: progressive
     streaming runs the scene at up to **13,545 draw calls** (`§PROGRESSIVE_FLUSH at=102500`)
     before the final `§BATCHED_FLUSH` collapses to 229; plus `§BVH_DEFERRED built=83537 ms=7381`
     (7.4s incremental BVH build) and `§SHELL_GHOST_BBOX boxes=28569 build_ms=207` right after
     load. Any fly-thru started in the first ~60s (e.g. pressing L immediately, as in this log)
     runs against ALL of that concurrently — feels laggy, is not a regression.
   - **Minor observation**: the log shows the L cinematic tour STARTING TWICE back-to-back
     (`[WALK] START cinematic tour` ×2, no stop between) — `toggleFlyAround` should toggle, so a
     second fire ought to cancel, not restart; two concurrent flyPath tweens fighting the camera
     would read as stutter. Unconfirmed whether double-press or double-wired listener
     (f7f27e7 07-12 touched keyboard wiring for touch-capable desktops — this machine is one).
     Check §KBD_SEQ_ENGINE count per single press if tour fly-thru specifically feels jerky.
3. **[RAM] Dispose-before-navigate** — shipped for 4D5D on mobile (`a98fcbc`: same-tab,
   dispose renderer+scene, free GPU RAM). Extend the dispose pattern to other heavy
   transitions where two scenes would otherwise co-exist.
4. **[GPU] Profile the worst building on a real device** — LTU (~122k elements) on a
   mid-tier Android: confirm draw-call count + frame ms with `§S280c_PERF_REPORT`; only
   then touch the GPU stack.
5. **[GPU — CONFIRMED via code read, not yet live-measured] Alt+Z's X-Ray state has no
   large-building fast path.** `A.cycleXrayBboxMode` (`viewer/tools.js:235`, Off→X-Ray→Bbox→Off)
   unconditionally calls `A.toggleXray()` on its first press — dims EVERY material to opacity
   0.3/transparent regardless of building size, forcing the renderer to sort+blend thousands of
   transparent draws every orbit/pan frame on a large building. `_roomLensOn()`
   (`navigate_find.js:2231`, dated comment "2026-07-15m") got a fix for the EXACT same cost on
   the Find/Room-Lens path: on a large building it now skips X-Ray entirely and goes straight to
   the cached bbox-ghost (`_buildMergedGhost()`) + `filterByGuids(new Set())` — one instanced
   wireframe draw call instead of per-material transparent shading. That fast path was never
   ported back to the manual Alt+Z toggle, so pressing Alt+Z once (Off→X-Ray) on LTU_AHouse
   still pays the old expensive cost even though the Bbox state 2 presses later would have been
   cheap. **Recommended fix (not yet implemented — diagnostic session only, PUSH PAUSE in
   effect)**: give `cycleXrayBboxMode`'s Off→X-Ray step the same "large building → skip to
   bbox-ghost directly" branch `_roomLensOn()` already has, rather than removing/detaching
   Alt+X — the ghost/bbox mechanism itself (`toggleMergedGhost`) is cheap and correctly
   cached+disposed; only the X-Ray state 1 step lacks the guard.
   - **Ruled out by code read**: `toggleXray()`'s OFF-transition (tools.js:200-204) fully restores
     each material's original `transparent`/`opacity`/`side` from `_orig*` fields and resets
     `renderer.sortObjects=true` — cycling back to "solid" should NOT leave residual per-frame
     cost by design. This was NOT independently live-verified (see sandbox limitation below) —
     treat as code-level evidence only, not a measured fact.
   - **Retracted theory (user re-tested live, 2026-07-15)**: earlier in this session, a real
     console-log paste appeared to show `_roomLensOn`/`_probeLenses`/`_allRoomVolumes` costs
     roughly doubling (99ms→229ms, 82ms→191ms, 21ms→45ms) coinciding with Find-panel opening,
     suggesting "something accumulates in RAM and Find clears it." User re-tested directly and
     reports **this does not hold up** — do not re-chase a Find-panel-fixes-it correlation.
6. **[OPEN, UNEXPLAINED — new lead 2026-07-15] HHS_Office_Federated (small building: 94 rooms,
   21 unique materials, 6880 elements) also stalls ~2s "when moving" during real Find-panel use**
   (user's own live report, real browser). This building is FAR too small for lever 5's
   large-building x-ray-dim mechanism to apply (only 21 materials to dim — trivial cost), so
   it's a DIFFERENT, not-yet-diagnosed cause. A full user console-log capture around the stall
   was reviewed — every `§PERF_PROBE` in it tops out at 21ms, nowhere near 2000ms, so the stall
   is invisible to the existing JS-side instrumentation (same blind spot the lever-5 code comment
   already names: "one-shot synchronous JS work, not ongoing paint cost"). Candidates NOT yet
   confirmed or ruled out: (a) `§ROOM_PATH` multi-hop pathfinding (`_roomGraphFor()` →
   `RG.buildGraph()`, `navigate_find.js:1037`) — the graph itself IS cached per-building so repeat
   queries should be cheap, but the Dijkstra/path-search call on a query has no `§PERF_PROBE`
   wrapping it at all, so its cost is simply unmeasured, not ruled out; (b) GC pressure from the
   fresh-`BoxGeometry`-per-door churn in `§DOOR_MARKER_SHAPE` across rapid category-tap sequences
   (each disposed correctly on toggle-off per code read, but disposal ≠ free of GC pause cost);
   (c) the perceived "stall" may simply be the camera fly-to/zoom animation duration
   (`§ROOM_SELECT_ZOOM`/`§GROUP_ZOOM`), not a technical bug at all. **Next step**: wrap the
   tap→visual-update path with `performance.now()` deltas (start at `§TAP_FIRE`, end at next
   paint) on a REAL reproduction of the actual stall, since none of today's existing probes
   caught it.
   - **Sandbox limitation reproduced again this session**: headless Chromium (Playwright +
     swiftshader) crashed twice attempting a live before/after frame-time trace on LTU_AHouse —
     not at load time (that succeeded, `§SPLIT_GEO_LOADED` fired fine), but during the FIRST
     scripted orbit/drag immediately after load ("Target page, context or browser has been
     closed" — no OOM in `dmesg`/`journalctl`, 18GB free at the time, so likely a
     swiftshader/GPU-process crash under sustained software-rendered interaction at 122k-element
     scale, not memory exhaustion). This is the SAME class of sandbox ceiling
     `[[project_ltu_ahouse_memory_architecture]]` already hit at the load stage — now confirmed to
     also block the INTERACTION stage. Real device or DevTools Performance/Memory recording
     remains the only reliable way to get frame-time numbers for large buildings; do not
     re-attempt a full headless orbit-drag session against LTU/HHS-scale geometry in this sandbox,
     it will not complete.

## TEST / WITNESS
- Real device (preferred) or DevTools mobile emulation + CPU 4–6× + GPU throttle.
- Capture per run, naming building + device: **draw calls · frame ms · DPR · first-load ms · peak RAM**.
- NEVER claim a speedup from config presence — measure before/after on the same device/building.
- §-log first; Playwright/visual only to confirm wiring (see docs/TestArchitecture.md §Browser Testing).

## SOURCES CONSOLIDATED HERE  (read for detail; this file supersedes them as the entry point)
- Memory: `project_s271_mobile_perf` (the stack), `project_s280c_perf` (multi_draw fallback).
- Done prompts (bim-compiler/prompts/done/): `S280c_PERF_VERIFY.md`, `S250_mobile_desktop_polish.md`,
  `S207_mobile_ux_viewer.md`, `TIME_MACHINE_MOBILE_FIX.md`.
- Docs: `docs/MOBILE_DEPLOY.md` (split-DB strategy), `prompts/ANALYSIS_SIDECAR.md` (OPFS pattern).
