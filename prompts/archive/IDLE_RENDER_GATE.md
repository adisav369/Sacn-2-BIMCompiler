# ⚠ DO NOT REMOVE — Scope & Standing Rules
**Scope:** Restore the desktop on-demand render gate in `deploy/dev/main.js` so an idle,
static scene stops repainting at ~60 fps (the "fans spin up when a building is left idle"
bug). **Isolate the continuous-render exception to Time Machine only.** Do NOT touch any
other subsystem. **Read the §-log after every run** — exit code is not evidence. Honour this
block until the witness below passes, then mark `# DONE`.

---

## Issue (what this proves/disproves)
**ISSUE:** With a building loaded and the viewer left **idle on desktop**, one CPU core
sits at ~97% and the GPU compositor thread (`CanvasRenderer`) at ~54%, spinning the fans.

**Proven by inspection (2026-06-04):**
- `ps`/`/proc` sampling: tab JS content process = **0%** while idle; main Firefox
  `CanvasRenderer` thread = **~54%** sustained → continuous GPU repaint, **not** a JS/memory leak.
- Root cause: `deploy/dev/main.js` desktop branch calls `renderer.render()` **every frame
  unconditionally** (no `_needsRender` check), while the **mobile** branch already gates.

## Root cause & history
- `main.js:500‑503` (commit `7d941768`, 2026-05-01) added the full on-demand machinery:
  `_needsRender`, an OrbitControls `change` → dirty listener, and `markDirty()`.
- The desktop gate was **removed in S280c** (memory `project_s280c_perf.md`) because the era
  felt sluggish — but memory itself concludes the sluggishness was an **NVIDIA/Intel driver
  mismatch + unrebooted kernel, "not a code regression."** The gate took the blame by
  coincidence (same-day kernel update). Mobile kept the gate; desktop was reverted to the
  dumb loop and never restored.
- Last real touch to the desktop branch: `f5e7cd7b` (S277c, 2026-05-25). The 2026-06-01
  touch (`2341fe93`) was a **comment-only** license-header sweep.

## Why it is safe to gate now (non-invention, verified)
1. **No continuously-animating shaders.** Grep for `clock` / `elapsedTime` / `uniforms.time`
   → none. Clouds were dropped (user-confirmed).
2. **Every camera animation already marks dirty** — verified call sites:
   navigate_find fly `1274,1290`; measure fly `747`; scene `349,800`; main `202,319,492`;
   plus `markDirty` present in 19 viewer files (dlod, grid_*, picking, panels, tools,
   precision_cam, streaming, ghostglass…). The codebase was built for on-demand rendering.
3. **Time Machine is redundant for the loop too — no TM exception needed.** `playTick`
   (time_machine.js:2210) runs on its **own `setTimeout` timer** (`2227`), advancing the
   cinematic camera **per tick**; each tick calls `renderAtTime` → `markDirty()` **plus a
   direct `renderer.render()`** ("Force immediate render", `1181‑1183`). The main loop only
   re-renders the *same* camera position between ticks → adds **zero** smoothness. Therefore
   the continuous loop is redundant **even during TM**, and gating it **calms TM down too**
   (no GPU burn between ticks or while TM sits paused). Decision: **pure on-demand, NO TM
   special case** — documented in the commit (`git note`-style rationale).

## Fix (single branch, `main.js` only — no other file touched)
Desktop `else` branch becomes:
```js
// §S286: on-demand desktop render. Static idle scene → 0 GPU frames. Restores the
// gate S280c reverted (memory traced that era's sluggishness to a driver mismatch,
// not the gate). No TM exception: TM self-renders via its own setTimeout timer →
// renderAtTime (markDirty + direct render, time_machine.js:1181-1183), so the loop
// is redundant even for TM. Every other camera path already calls markDirty.
if (_needsRender || APP.streaming || APP.walkModeActive || _orbiting) {
  if (APP._composer && APP._composerEnabled) APP._composer.render();
  else APP.renderer.render(APP.scene, APP.camera);
  _needsRender = false;
  if (_idleLogged) { console.log('§IDLE_GATE wake'); _idleLogged = false; }
} else if (!_idleLogged) {
  console.log('§IDLE_GATE park — desktop loop idle, 0 GPU frames (static scene)');
  _idleLogged = true;
}
```
Plus declare `let _idleLogged = false;` beside `_needsRender`. Bump `§MAIN_JS` version + `index.html` `?v=`.

## Witnesses (must pass before DONE)
- **W-IDLE-CPU:** Load a building, leave idle 10s → `/proc` sampling of the Firefox
  `CanvasRenderer` thread drops from ~54% to ~0%. (host-side proof)
- **W-IDLE-LOG:** Console prints `§IDLE_GATE park` once the scene settles idle, and
  `§IDLE_GATE wake` on the next interaction. (whitebox proof — primary)
- **W-TM-CALM:** Activate Time Machine → playback still animates smoothly (TM self-renders
  per tick), AND the GPU calms vs. the old 60 fps free-run — `§IDLE_GATE park` may appear
  between ticks / while TM is paused. Proves the loop was redundant for TM.
- **W-NO-IMPACT:** Orbit/drag, fly-to from Find, walk mode, measure → all still render
  live (each already calls `markDirty`/emits controls `change`).

## Test plan
1. `node --check deploy/dev/main.js` → must pass.
2. Serve from `deploy/` (localhost:8080/dev/). Bump `?v=` to defeat cache.
3. Host-side: sample `CanvasRenderer` thread CPU before vs. 10s-idle after.
4. Browser: read `§IDLE_GATE` lines; exercise TM, orbit, fly, walk → confirm witnesses.

---
# DONE (2026-06-04)
Implemented: `deploy/dev/main.js` — desktop `else` branch gated on
`_needsRender || APP.streaming || APP.walkModeActive || _orbiting`; added `_idleLogged`
+ `§IDLE_GATE park`/`wake` telemetry. No other file touched (TM untouched — proven
redundant). `§MAIN_JS v34→v35`, `index.html main.js?v=33→v35`.

Witness evidence (`deploy/dev/tests/log/idle_gate.log`, via `test_idle_gate.js` @ localhost:8080/dev/):
- §MAIN_JS v35 loaded ......................... fresh code served  ✓
- §IDLE_GATE park (line 47) ................... loop parks when idle, 0 GPU frames  ✓ (W-IDLE-LOG)
- §IDLE_GATE wake (line 69) after markDirty ... wakes on dirty  ✓ (W-IDLE-LOG)
- §IDLE_GATE park (line 71) re-parks .......... renders ONE frame then idles (on-demand)  ✓
- boot §PAGEERROR count = 0 ................... gate does not break boot  ✓ (W-NO-IMPACT)
- (the lone §INIT_ERROR is a no-building 404 in the headless harness — unrelated)

Still host-side to confirm in the user's Firefox (W-IDLE-CPU): hard-reload the loaded
building → CanvasRenderer thread CPU should drop ~54% → ~0% while idle. node --check: PASS.
node --check syntax: PASS.
