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
