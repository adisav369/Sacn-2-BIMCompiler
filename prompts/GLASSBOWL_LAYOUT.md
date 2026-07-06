# GLASSBOWL_LAYOUT.md — the BubbleLayout class + nudge-apart + dock-lens (SPEC)

# ⚠ DO NOT REMOVE
> **Scope:** Centralize ALL bubble positioning into ONE `Layout` module shared by
> `glassbowl.html` + `glassbowl_gravity.html`; add a gentle DETERMINISTIC nudge-apart that
> runs before bubbles reach the screen; add a macOS-dock HOVER LENS for crowded views
> (especially the Gravity **Dictionary** view, which overlaps on desktop and worse on mobile).
> **Pure-read, EXTRACT-ONLY, no T3, no writes.** Keep the existing **102** `§GLASSBOWL-WIRING`
> checks green AND the gen-side `§GLASSBOWL / §LIFECYCLE / §ORBIT` PASS with `hand=0`.
> **READ THE LOG after every run** (exit code is not evidence). Honour until DONE.

## Why (the problem)
Positioning is hardcoded in many places — `radius()` / `project()` / orbit z-planes /
`untangle()` / the new `diagonal` in glassbowl, and `placeGravity()` / `desired()` in gravity.
One class should OWN it, so a single gentle "nudge them apart a bit (not too much)" pass runs
before render, and crowded views declutter via a dock-lens instead of overlapping.

## Decisions (user, 2026-05-31)
- **Crowd fix = DOCK-LENS MAGNIFY.** Crowded bubbles minimize to small dots so nothing
  overlaps at rest; the bubble under the cursor/finger **and its neighbours swell** like the
  macOS dock (proximity → size). Most "play", best on mobile.
- **Scope = BOTH pages.**

## Single source of truth (keep each page self-contained + file://-safe + SW-cacheable)
Author the layout ONCE as `scripts/glassbowl_layout.js` — a `Layout` factory of **pure
functions over nodes** (no DOM, no globals). **INLINE it verbatim** into each served page so
both stay self-contained (no new network dependency, offline SW still works). If a page is
hand-authored rather than generated, inline the IDENTICAL snippet and mark the sync point with
a comment so the two copies never drift.

## API (pure, DETERMINISTIC — no `Math.random`, replay-safe)
- `Layout.seed(nodes)` → initial positions (the existing circle/spine seed).
- `Layout.nudge(nodes, {pad, maxMove, iters})` → anti-overlap relaxation: any pair closer than
  `r_a + r_b + pad` is pushed apart a SMALL fraction, each displacement **clamped to `maxMove`**
  ("not too much"), `iters` small. Same input → same output.
- `Layout.dockLens(nodes, cursor, {restR, maxR, reach})` → returns a per-node RENDER radius:
  `restR` grown toward `maxR` by closeness (within `reach`) to the cursor (dock falloff curve).
  **Visual only** — never mutates a node's `x/y` or data.
- Existing strategies routed THROUGH Layout: `orbitPlanes(z)`, `diagonal`, `gravitySpiral`,
  `dictionaryRow`. The draw/render path calls `Layout`, not inline math.

## Witnesses (each EXTRACT-only, 0 hand-authored)
- **W-LAYOUT** — one `Layout` module owns positions; `draw()`/render call it; no duplicated
  inline position math remains. `§LAYOUT module=1 callers=[draw,...]`.
- **W-NUDGE** — nudge REDUCES the overlapping-pair count while every displacement ≤ `maxMove`,
  is deterministic (identical result on a second run), and PRESERVES the at-rest invariant
  (W-ORBIT pixel-identical at yaw=pitch=0). `§NUDGE overlapsBefore=.. after=.. maxMove=..`.
- **W-DOCKLENS** — moving the pointer near a bubble swells it + its neighbours (render radius
  rises with proximity) in BOTH pages; resting Dictionary bubbles are minimized so there are
  **0 overlaps at rest**; pure-visual; mobile pointer (touch-drag) drives it too.
  `§DOCKLENS rest=.. peak=.. reach=.. overlaps-at-rest=0`.

## Discipline (non-negotiable)
- Refactor-SAFE: all **102** prior checks stay green — esp. *4 spine toggles · edges==49 ·
  5 bubbles lit · reset re-homes · W-ORBIT at-rest identity · W-UNTANGLE · W-DIAG*.
- EXTRACT-ONLY; values from data/geometry; no invented numbers.
- **NO DEPLOY** — build + regenerate + test GREEN locally only; HOLD the live push for explicit
  go (this touches the at-rest layout, so the user reviews first).
- Build loop per change: spec-cite → implement additively → regenerate
  (`node scripts/system_explorer.js 2>&1 | tee build/erp/system_explorer.log`) → extend
  `deploy/dev/tests/test_glassbowl.js` with checks NAMING the issue → run GREEN → read the §-log.
