# ⚠ DO NOT REMOVE — Scope: Precision Cam — Auto-Pivot (re-center orbit on drag-end)
# Read the log after every run. No inventions. Honour until DONE.

## Origin
Extends the Feather / Precision Camera drawer born in `prompts/done/S265_UI_AESTHETICS.md`
(§120, §300: "Feather (Precision Camera): tap=Fine toggle, long-press=Reset chip").
Code lives in `deploy/dev/precision_cam.js` (tagged `// S265:`). Single file, no copies → additive, no drift.

## Problem
The drawer's 🎯 **Reset** re-anchors the orbit pivot (`controls.target`) to a point a **fixed
10 units ahead** of the camera (`precision_cam.js:59`) — geometry-blind. If the model sits at a
different distance, you orbit around empty space in front of/behind it. After panning around, the
pivot drifts and orbit no longer spins around what you're actually looking at.

## Feature — 🏺 Pivot (Auto-Pivot toggle)
A **third drawer button**, a sticky toggle (mirrors 🪶 Fine, not one-shot 🎯 Reset). While ON:
- Each time a drag finishes (OrbitControls `'end'` event), re-anchor `controls.target` to the
  **nearest surface to the screen-centre**. A single dead-centre ray misses anything not exactly
  under the crosshair, so sample concentric NDC rings (centre, r=0.06, 0.12, 0.18 × 8 dirs),
  radiating outward, and take the first ring that hits → the nearest-to-centre surface wins.
- Fallbacks in order: all rings miss → scene bounding-box centroid (`Box3.setFromObject`) →
  point-10-ahead (same as Reset) so it never throws.
- Witness logs the ring radius at which it hit (`hit=mesh r=0.06`) for insight.
- Re-anchor fires once immediately on toggle-ON so the pivot snaps right away.
- Only writes `controls.target` then `controls.update()` + `markDirty()` — identical mechanism to
  Reset, different landing point. Camera does NOT jump (OrbitControls keeps the camera offset when
  target moves), so the view is stable while the spin axis follows the scene.

## Mechanism note (why no view jump)
`OrbitControls.update()` recomputes `camera.position = target + offset`. Moving `target` alone and
calling `update()` shifts the orbit pivot without moving the camera — same as the existing
`resetOrbit()`. This is reuse of a proven path, only the target value differs.

## Scope guard — NO impact
Confined to `deploy/dev/precision_cam.js`: +1 button in drawer `innerHTML`, +1 `pointerup` handler,
+1 toggle fn pair (`pivotOn`/`pivotOff`) + `recenterPivot()`, +1 `window.toggleCamPivot` export.
Does NOT touch Fine, Reset, toolbar wiring, render loop, or any other file. The `'end'` listener is
attached on toggle-ON and removed on toggle-OFF (no always-on overhead).

## Witness (whitebox §-log — prove before deploy, per CLAUDE.md)
W-PIVOT-TOGGLE : `§pivot ON` on enable, `§pivot OFF` on disable.
W-PIVOT-RECENTER : after every drag-end while ON, exactly one
  `§pivot recenter target=(x,y,z) hit=mesh|bbox|ahead` — proves target moved to the resolved centre
  and which resolution path fired. Orbit-drag → hit=mesh (or bbox); empty-space view → hit=ahead.

## Test (names the issue it proves)
- ISSUE: "orbit pivot drifts off the model after panning." PROOF: with Pivot ON, pan away then read
  `§pivot recenter` — target lands on the visible surface (hit=mesh), not 10-ahead. With Pivot OFF,
  no `§pivot recenter` line appears (listener detached).
- Browser §-log first (read the console log), Playwright only for wiring (button exists, toggles).

## Steps
1. Implement in `precision_cam.js` per spec above.
2. Syntax check (`node -c`), verify all `§` tags exist, save test log, read the log.
3. Confirm with user before any deploy (deploy/dev flow per CLAUDE.md).

## DONE
(append evidence: § log lines proving W-PIVOT-TOGGLE + W-PIVOT-RECENTER)

Shipped 2026-06-04→2026-06-06 to the canonical viewer (`bim-ootb/viewer/precision_cam.js`, this repo's
`deploy/dev/precision_cam.js` copy was the original build location) — PR #101 (base feature), #103
(keyboard cluster: `A`=Reset, `Q`=Pivot, `CapsLock`=Fine), #104 (homes on selected element), #108
(top-centre icon notices), #109/#111 (fallback hardening — building meshes only, never sky/ground).
Full behavioral detail lives in memory `project_precision_pivot.md`, not restated here.

## 2026-07-27 — Un-archived: pulled back out of `prompts/archive/` into this active folder

Was archived on ship (normal convention — spec done → archived), but a later session investigating
"make Auto-Pivot always active" found the code but not this spec (searched by filename/content for
"pivot"/"precision_cam", the archive folder didn't surface it under those terms either — a `git grep`
across all of `prompts/**/*.md` for "Caps Lock" is what eventually found it). Moving it back to the
active folder so a future dev looking for "how does the precision-cam drawer work" lands on the real
origin doc instead of just the memory summary.

A same-day default-state experiment (below) was tried and reverted, so the "Scope guard — NO impact"
section above still fully holds, unmodified — Auto-Pivot remains exactly the opt-in toggle it
describes.

- **Auto-Pivot defaulting ON at load (bim-ootb PR #1033) was tried, then REVERTED same day (PR #1034,
  `fix/autopivot-default-off`, commit `292be9e`).** A real session's §-tagged log (not a screenshot)
  showed it firing exactly as coded — `§pivot ON tries=1`, then `§pivot recenter ... hit=mesh` on every
  ordinary drag — and that behavior surfaced two real problems once actually used: (1) recentering on
  every drag-end with no opt-in reads as the view drifting on its own, not smooth continuous nav; (2)
  it silently defeated Reset (`A` key) — `resetOrbit()` only moves `controls.target` (no camera motion),
  so its effect is invisible until the next drag/zoom, and with Pivot always listening that next
  drag-end immediately overwrote the target Reset had just set, before the user ever saw Reset work.
  **Current shipped state: Auto-Pivot is opt-in via `Q` again, exactly as this spec originally
  describes** — the polling-default-on code was removed entirely, nothing of it remains.
- **New decision this spec never covered, KEPT (independent of the default-on revert): Fine vs. Pivot
  interaction.** Fine (CapsLock) is a deliberate
  act for slowly closing in on one specific point; Auto-Pivot recentering the target on every drag-end
  while Fine is on would fight that. Resolved as **Fine wins** — `_onEnd` now checks `_pivot && !_fine`,
  i.e. auto-recenter is suppressed while Fine is active. Decided 2026-07-27: Fine has a visible top-
  centre icon and is an explicit user act, so it should not be silently overridden by an ambient one.

## 2026-07-27 (same day, third pass) — §PIVOT_AMBIENT_AUTO: continuous nav by default, done differently

The default-on goal from the top of this section didn't die with the #1034 revert — it came back once
the actual failure mode was understood, built as a genuinely different mechanism instead of just
re-trying #1033's approach. bim-ootb PR (branch `feat/autopivot-idle-count`), same file.

**Why #1033 failed and this doesn't repeat it:** #1033 made `Q`'s own toggle default ON, so its
existing "recenter on every drag-end" logic ran unconditionally — that's what drifted and defeated
Reset. This pass instead adds a **separate, independent listener** (`_ambientOnEnd`) that:
- **No-ops entirely whenever `Q` or Fine are active** — `Q`'s own mechanism above is completely
  untouched, still opt-in, still fires on every drag exactly as before. Ambient only ever runs in the
  gap where neither manual mode is engaged. `pivotOn()`/`fineOn()`/`resetOrbit()` also each proactively
  clear any pending ambient timer the instant they're invoked (not just on the next drag-end) — closing
  a race where a queued ambient correction could land right after the user just deliberately took over.
- **Debounced AND count-gated, not immediate.** Only fires once no drag/zoom has happened for 1.5s
  (`_AMBIENT_REST_MS`) AND at least 3 have accumulated since the last correction
  (`_AMBIENT_THRESHOLD`) — never mid-navigation, never for one small nudge. This is the actual fix for
  the "drift" complaint: nothing ever moves while the user's hand is still on the controls.
- **Uses `recenterPivot()` (raycast, surface-aware), never `resetOrbit()`.** Automating blind-Reset was
  separately evaluated and rejected as unviable in-session — it would reintroduce the "orbiting empty
  space" problem Auto-Pivot exists to fix, on top of still carrying the drift risk.

Net effect: `Q`, Fine, and Reset are byte-for-byte the same proven behavior they were before this
change (one added `_ambientClear()` no-op call each) — the only new code path is the ambient one, and
it only ever writes `controls.target` (never camera position, same no-jump property every function in
this file already relies on), so a wrong ambient firing has no worse a failure mode than "the next zoom
range is slightly different than expected," not a visible jump or lost work.
- Correction to a claim in the memory record (`project_precision_pivot.md`) that this work surfaced:
  `recenterPivot()` does NOT touch `controls.minDistance` — only `Reset` (`resetOrbit()`, → 0) and
  `Fine` (`fineOn`/`fineOff`, → 0.001/0.1) do. There was never a Reset/Fine/Pivot `minDistance`
  three-way collision to resolve; the actual conflict was always-on recenter fighting Fine's slow-
  approach workflow (addressed above), not a shared-field write race.
