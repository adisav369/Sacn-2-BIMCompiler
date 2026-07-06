# RESUME — DAGeVu Modeller Direct-Manipulation · P3 Multi-Select

```
# ⚠ DO NOT REMOVE
SCOPE: Continue the modeller direct-manipulation lane. §0 SURVEY ✅ + P1 GEOM_MOVE ✅ LIVE. This card hands
the NEXT leg (P3 MULTI-SELECT) to a fresh session. Full lane + ranked punch-list: prompts/MODELLER_DIRECT_MANIPULATION.md.
WITNESS-FIRST: claim before code; whitebox §-log is the proof (à la W-BONSAI-*), Playwright/puppeteer for wiring only.
LOG MANDATE: read the witness log before conclusions. NON-INVENT: every move/select traces to a signed op or real bbox.
DEPLOY: modeller serves from bim-ootb MAIN + GH-Pages — branch work isn't live till merged (PR→CI→merge→verify live,
cold-cache-key fetch; Fastly edge cache has ~10-min TTL). Modeller is NOT behind sw.js precache → bump script-tag ?v=.
WORK IN A WORKTREE off fresh origin/main (/tmp/wt-*) — editing ~/bim-ootb directly is BLOCKED by a PreToolUse hook.
```

## STATE (2026-06-20)
- **§0 SURVEY ✅** — ranked punch-list in `MODELLER_DIRECT_MANIPULATION.md §0-RESULTS` (blockers P1·P2·P3; high H1–H3; bug M1).
- **P1 GEOM_MOVE ✅ LIVE** — bim-ootb PR #423 (squash `1363e8e`), W-BONSAI-MOVE 12/12. HYBRID fold (worker
  `kernel.translate` PATH A + host `foldInsert(op,mv)` PATH B), DELTA op `{parent,dx,dy,dz}`, custom XYZ axis-handle
  gizmo + grid-snap + arrow-nudge + typed step (P2 seam shipped too). See `project_modeller_direct_manip` memory.
- **P3 MULTI-SELECT ✅ DONE/LIVE — W-BONSAI-MULTISELECT 14/14, PR #434 squash `d7e5fba` on main (fast-checks +
  e2e green), modeller.html on main carries the code.** `selectedIds` Set (scalar selectedId/Mesh = PRIMARY anchor, all
  writes funnel `setSelectionIds`); shift+click toggle; shift+drag MARQUEE (capture-phase freezes OrbitControls,
  window-level pointerup/cancel so off-canvas release never strands controls; bbox-CENTRE-in-rect select); group
  move (`commitMove` loops set → N signed GEOM_MOVE same snapped delta) + group delete + centroid gizmo + per-mesh
  ghost. Adversarial review caught+fixed 2 bugs (off-canvas freeze; Esc witness masking the real two-press UX).
  Siblings move/edit/gridmove regression PASS. **VERIFY #434 actually LANDED on main + live before next session.**

## NEXT LEG (P3 ✅) = H1 gizmo polish / H2 snap-to-geometry — see AFTER P3 below
**Goal:** marquee/lasso box-select + shift-click add/remove + group transform about a shared pivot.
- **Today:** `selectedId`/`selectedMesh` are SCALAR (modeller.html ~:486); single-pick only (pointerdown ~:509).
- **Build:** `selectedId` scalar → a `selectedIds` Set; highlight all; shift-click adds/removes; drag an empty-space
  box → select all features whose screen-projected bbox falls inside (window=enclosed / crossing=touched is the
  competitor convention — see §0-RESULTS H2 notes). The MOVE gizmo (just shipped) + Delete act on the SET.
- **Reuse:** the P1 move seam — a group move = N signed `GEOM_MOVE` rows (same delta) OR one batched op; the gizmo
  anchors at the SET centroid. Keep the Connect 'selection' broadcast working (highlight() at ~:487 publishes).
- **Witness W-BONSAI-MULTISELECT (claim):** marquee over N inserts selects exactly those N (others untouched); a
  group move commits N signed `GEOM_MOVE` by the SAME delta; chain verifies; deselect clears. §-log the set + per-op.
  Model the demo hook on `?move=demo` / `?gridmove=demo` (drive real handlers via dispatched PointerEvents); the
  puppeteer test on `viewer/tests/bonsai_move_live.js` (VIEWER=/tmp/wt-<name>/viewer, reads window.__<x>Result).

## SPEC (resolved 2026-06-19, this session) — design committed before code
- **State:** add `selectedIds` Set; `selectedId`/`selectedMesh` stay the PRIMARY/anchor (last-added) so every
  single-select code path (cut/fillet/lod/frame) is untouched. All writes funnel through `setSelectionIds(ids,primary)`
  → repaint emissive (primary brighter `0x2b5a8c`, secondaries `0x1f3f5c`), sync buttons/Outliner/Connect (primary only).
- **Gestures (OrbitControls owns bare left-drag, so SHIFT is the multi gesture — no orbit conflict):**
  bare-click obj = replace-select · bare-click empty = no-op (Esc clears, as today) · **shift+click obj = toggle** ·
  **shift+drag empty = MARQUEE** (CAPTURE-phase pointerdown freezes `controls.enabled=false` BEFORE OrbitControls
  consumes it — proven controls-off idiom; re-enabled on pointerup). Marquee ADDS features whose projected bbox
  CENTRE falls in the screen rect (anchor-select; window/crossing left to H2). Box = a fixed `#sel-marquee` div.
- **Group transform:** `selCentre()` + gizmo size now span the COMBINED bbox of `selMeshes()`; `moveGhost` clones
  EACH selected mesh; `commitMove(dx,dy,dz)` loops the SET → N signed `GEOM_MOVE` by the SAME delta (snap applied
  to the group centroid once). Delete loops the set. Re-fold then `selectMany(ids)` re-grabs fresh meshes.
- **Witness `?multiselect=demo` / `W-BONSAI-MULTISELECT`:** 3 doors on a grid → marquee box over 2 selects EXACTLY
  those 2 (3rd untouched) → shift-click adds the 3rd then removes it (set 3→2) → group Move drag +X commits EXACTLY
  2 `GEOM_MOVE` by the SAME snapped delta, both centres move by it, 3rd door has ZERO move-ops, chain verifies +
  is signed (3 inserts + 2 moves), Esc clears the set to 0. Headless puppeteer on `tests/bonsai_multiselect_live.js`.

## AFTER P3 — progress 2026-06-20
- **M1 grid-undo ✅ DONE/LIVE — PR #436 squash `bad7013`, W-GRIDMOVE-UNDO 4/4.** Gridline coords are now a FOLD of
  the op-log (`Grid.foldFromOplog` = defined base + active GEOM_GRID_MOVE deltas, re-derived on every `bonsai:oplog`
  + at `scrubToShared`); `gridmove.commit` no longer mutates `grid.xs`. undo/redo/scrub revert the grid deterministically.
- **H2 snap-to-geometry ✅ DONE/LIVE — PR #437 squash, W-BONSAI-SNAPGEOM 10/10.** Move snaps the selection's bbox
  key-points to the vertices/edge-mids/face-centres of OTHER features (3×3×3 lattice, real bboxes), diamond marker,
  beats grid, FREE-AXIS selection (adversarial fix), `G` toggles. One `snappedMoveDelta` resolver = preview≡commit.
- **H1 — core ALREADY shipped by P1** (R/G/B `axisHandle` X=0xe2553b Y=0x46b955 Z=0x4f93e0 + white XY hub). ONLY
  remainder = Z-DRAG is a no-op in PURE top view (`moveDragPlane('z')` degenerates when camera⊥ground; arrow
  PageUp/Dn already covers it). Small polish: map vertical screen motion→world-Z when the drag plane is degenerate.
- **H3 free-rotate ✅ DONE/LIVE — PR #440 squash `af8174b`, W-BONSAI-ROTATE 9/9.** Move gizmo carries a yaw RING
  (`TorusGeometry`, `moveAxis:'rotZ'`), gated to a SINGLE INSERT (`isInsert` — solids get no ring → no silent
  no-op). Ring drag = pointer angle about centre, 15° snap (Shift=free); signed `GEOM_ROTATE{parent,drot}` folds
  HOST-side (PATH B): `foldInsert` re-anchors (ox,oy) via centre-invariant solve so the insert SPINS about its
  bbox centre; `kernel.js` sums MOVE+ROTATE into one `{dx,dy,dz,drot}` override; worker `?v=3` tolerant no-op
  branch. `rotGhostShow` previews the spin. Adversarial fix: ring insert-gated. **Follow-ups: SOLID B-rep rotate
  (worker `generalTransform`, mirror GRID_MOVE SCALE) + GROUP rotate about shared centre + SCALE handles.**
- **M3 hover pre-pick ✅ DONE/LIVE — PR #441 squash `fd23ffc`, W-BONSAI-HOVER 5/5.** Cursor over a feature →
  faint glow `0x14324a` (< selection) + pointer cursor; `setHover` skips selected meshes; suppressed while a
  button is held (no orbit fight) / non-select mode / drag; `pointerleave` clears.
- **✅ DIRECT-MANIPULATION SPINE COMPLETE: select→hover→move→multi-select→snap→rotate — all TIER-0 + TIER-1 + M1
  + M3 live (6 PRs: #423 #434 #436 #437 #440 #441).** Remaining is lower-priority polish only:
  - **Rotate follow-ups:** SOLID B-rep rotate (worker `generalTransform`, mirror GRID_MOVE SCALE) · GROUP rotate
    about a shared centre · SCALE handles.
  - **Medium:** M2 transient sketch/route pts (not op-log) · M4 cursor-per-mode · M7 assembly drop preview (N
    child placements) · M8 outliner full-rebuild jank @100+ · M10 error toast · H1 top-view Z-drag.

## METHOD THAT WORKED FOR P1 (repeat)
Judge-panel design (3 proposals → synthesize) → implement in worktree leg-by-leg → whitebox §-witness headless →
adversarial review of the diff → PR → auto-merge squash → verify LIVE (cold cache key). Regression-run the sibling
witnesses (gridmove/edit/sweep PASS; insert/place fail PRE-EXISTING on pristine main — don't chase them).
