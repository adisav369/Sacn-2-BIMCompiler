# RESUME — DAGeVu Modeller · polishing follow-ups (post direct-manipulation spine)

```
# ⚠ DO NOT REMOVE
SCOPE: The direct-manipulation SPINE is DONE/LIVE (select→hover→move→multi-select→snap→rotate — see
prompts/RESUME_MODELLER_P3.md for the 6 shipped legs #423/#434/#436/#437/#440/#441). This card carries the
REMAINING lower-priority polish, worked top-to-bottom, witness-first (whitebox §-log à la W-BONSAI-*; puppeteer
for wiring only). NON-INVENT: every transform traces to a signed op or a real bbox. DEPLOY: modeller serves from
bim-ootb MAIN + GH-Pages — branch off FRESH origin/main in a /tmp/wt-* worktree (editing ~/bim-ootb is hook-BLOCKED),
PR→auto-merge squash→verify LIVE. Inline edits to modeller.html need no ?v bump; bonsai_*.js / worker do.

⚠ LANE-SEPARATION / CONCURRENCY: the BOM-drop fix (prompts/BOM_DROP_SPATIAL_INVESTIGATION.md §RESULT) is a SEPARATE
lane (data/placement convention) but it ALSO edits modeller.html (:998/:1016) + bonsai_library.js. Do NOT run these
two as parallel agents/terminals — the worktree isolates the working dir but NOT line-level conflicts on the shared
modeller.html. Sequence them in ONE session (BOM-drop is small → do it first, then polish), or finish one + merge
before starting the other. Within THIS card the items are independent in concept but all touch modeller.html, so
work them SEQUENTIALLY (one leg → PR → merge → next), not fanned out to parallel agents on the same file.
```

## REMAINING ITEMS (priority order; each = one leg → witness → PR → merge → next)
### Rotate follow-ups (complete H3)
1. ✅ DONE/LIVE — **SOLID B-rep rotate** (PR #446, bonsai_kernel_worker.js?v=4). Real occt rotation in the worker
   GEOM_ROTATE branch via `kernel.rotate` about the bbox-centre Z (cleaner than generalTransform — rigid gp_Trsf);
   host `isSolid()` opens the yaw ring to solids. **W-BONSAI-ROTATE-SOLID PASS** (op-log driven, no pointer): non-
   square wall 90° → extents swap ex3↔ey0.4, centre fixed, 1 signed rotate, deterministic, composes(180), undo
   restores, move→rotate→cut honest (void carved the transformed solid nv 24→48).
2. ✅ DONE — **GROUP rotate about a shared centre** (PR #448, auto-merge armed). Gate opens the ring to any all-
   rotatable multi-select; commitRotate emits per child one GEOM_ROTATE (spin about own centre) + GEOM_MOVE orbiting
   its centre about the set centroid (Tᵢ=G+Rθ(Cᵢ−G)); single-sel ⇒ no move. **W-BONSAI-ROTATE-GROUP PASS**: two
   doors (0,0)&(4,0) +90° about (2,0) → (2,-2)&(2,2), centroid fixed, each spun, 2 ROTATE+2 MOVE, reversible.
3. ✅ DONE/LIVE — **SCALE handles (INSERTS)** (bim-ootb PR #563 MERGED, sw v9). Cube X/Y/Z handles on a single
   selected INSERT → drag to non-uniform stretch, EDGE-ANCHORED at the opposite (min) face (user-chosen anchor).
   One signed GEOM_SCALE/drag; `foldInsert` folds it host-side on the insert's LOCAL geometry (pure JS, net fx/fy/fz
   via the bonsai_kernel PATH-B accumulator) → composes exactly, deterministic. **W-BONSAI-SCALE 10/10** (op-log
   driven: x×2 doubles X / −X edge fixed / Y,Z unchanged; one signed; verifies; deterministic; ×1.5 COMPOSES net ×3
   no cross-axis leak; undo restores; Y-scale touches only Y).
3b. ⛔ DEFERRED — **SCALE on B-rep SOLIDS.** occt-wasm `generalTransform` = `BRepBuilderAPI_GTransform` Copy=false →
   the derived shape ALIASES the shared base TShape; a **history-scrub re-fold BETWEEN two scales on the same solid**
   (undo/redo/timeline back-then-forward) corrupts the cached base → the next scale LEAKS the prior factor onto
   untouched axes (witnessed x×2 → scrub n-1/n → x×1.5 = z×2). **SCOPE CORRECTION (measured 2026-06-28):** plain
   CONSECUTIVE scaling withOUT a backward scrub is FINE — x-then-y on a solid gave (×2,×2,unchanged) correct; single
   scale fine. The trigger is the backward history-scrub, not "any 2nd scale" (my PR #563 note overstated it).
   **The cheap wrapper-level fix FAILS:** tested a bake-via-cut (no-op boolean `cut(scaled, farBox)` to de-alias the
   output) in a throwaway worktree → compose-under-scrub STILL leaked z×2 (the corruption survives the partial re-fold
   regardless of output baking; it's in the fold/release lifecycle, not just output→base aliasing). So a real fix is
   NOT a one-liner: either recompile occt-wasm with Copy=true on generalTransform (heavy — emscripten + occt
   toolchain), OR rework the worker's shape-release lifecycle so a partial fold never frees a shape a cached base
   aliases (also non-trivial). RECOMMENDATION: do NOT invest now — inserts (the common case) work; gate handles to
   inserts; pick #4/#5/#8 first. Revisit solid-scale only as a dedicated kernel session if authored-wall scaling is
   actually wanted. (The worker net-scale-on-pristine logic is written + correct pre-corruption; keep for that session.)

### Medium tier (discoverability / correctness / scale — survey M-items)
4. ✅ DONE/LIVE — **M2 sketch/route point recovery** (bim-ootb PR #567, sw v12, W-BONSAI-POINTS-RECOVERY 4/4).
   A cancel-exit (Escape/toggle) buffers the in-progress points to a session draft (`_sketchDraft`/`_routeDraft`);
   re-opening the tool restores them; a commit clears the draft; Backspace-to-empty + cancel discards it. Toast
   announces save/restore. `exit(committed)` param distinguishes commit-clear from cancel-buffer.
5. ✅ DONE/LIVE — **M4 cursor per mode/tool** (bim-ootb PR #564, sw v10, W-BONSAI-CURSOR 12/12). `modeCursor()` →
   crosshair (sketch/route/edge-pick) · copy (insert) · grab (grid-move) · move (gizmo); `applyModeCursor()` on every
   mode enter/exit; `setHover` defers to `modeCursor()` so a mode cursor wins over the M3 hover-pointer.
6. ✅ DONE/LIVE — **M7 assembly drop preview** = the N CHILD boxes at their real landing positions, not just the
   aabb footprint (bim-ootb PR #570, sw v15, W-BONSAI-ASM-PREVIEW 4/4). Gate cleared: the BOM-drop anchor fix
   already shipped (W-BOM-DROP-CENTER), so the preview shows the real re-centred child set. `bonsai_library.
   previewLeafBoxes(id,placement)` builds the N box-proxies at the leaf landings (SAME dropLeaves transform the
   commit uses) merged into one ghost, capped at 2000 → whole-building drop falls back to the TRUE footprint box
   (logged); `showGhost` caches the cluster per (hash,yaw,elev) + rigidly translates per move. Drop path untouched.
7. ✅ DONE/LIVE — **M8 Outliner incremental rebuild** (bim-ootb PR #568, sw v13, W-BONSAI-OUTLINER-INCR 5/5).
   The Outliner rebuilt BOTH the seeded BOM-tree (whole building) AND the flat op-log groups on every `bonsai:oplog`
   change → jank at 100+ features. Now each section renders to its own persistent container; the freshly-built HTML
   is string-diffed against the last render and an unchanged section is left untouched (no innerHTML reparse / no
   re-wire). A geometry commit changes only the flat HTML → the seeded tree DOM is reused (identity preserved);
   active-blue is no longer baked — setActive() paints it over the surviving DOM for flat AND tree-leaf rows.
8. ✅ DONE/LIVE — **M10 error toast** (bim-ootb PR #566, sw v11, W-BONSAI-TOAST 6/6). `toast(msg,kind)` persistent
   notice (errors ~7s, click-dismiss, stack≤4); `setStat` auto-toasts any `FAIL …` so all 11 catch-block errors
   surface; `unhandledrejection` hook for uncaught async failures; `window.toast` exposed.

**REMAINING polish:** ONLY #3b solid-scale kernel leg remains, and it is ⛔ DEFERRED by user direction ("only if
authored-wall scaling becomes a real need") — a dedicated occt-wasm kernel session (recompile generalTransform
Copy=true OR rework the shape-release lifecycle), NOT polish. #6/#7/#9 all SHIPPED 2026-06-28 (sw v12→v15).
Gizmo arc (move/rotate/scale-inserts) + cursor + toast + point-recovery + Outliner-incremental + Z-top + rich
assembly preview all SHIPPED. **The polish backlog is at ZERO except the user-gated #3b.**

### H1 leftover
9. ✅ DONE/LIVE — **H1 Z-drag in pure top view** (bim-ootb PR #569, sw v14, W-BONSAI-ZTOP 4/4). `moveDragPlane('z')`
   degenerates when the camera is ⊥ the ground (ray parallel to the vertical drag plane) → Z-drag was a no-op.
   `_camTopDown()` detects it (camera within ~3° of vertical) and the Z handle maps vertical SCREEN motion → world-Z
   (drag up = +Z, magnitude = pixels × `_zWorldPerPixel`); flows through the SAME snappedMoveDelta/commitMoveDrag
   path. Non-top views byte-identical (branch gated on the degeneracy).

## METHOD (the cadence that worked for the spine)
Worktree off fresh origin/main → spec the witness claim in this card → implement → whitebox §-witness headless
(`tests/bonsai_*_live.js`, drive real handlers via dispatched PointerEvents) → adversarial review of the diff
(it caught a real bug in 3 of the last 5 legs) → PR → auto-merge squash → verify LIVE → regression-run the sibling
witnesses (move/multiselect/snapgeom/rotate/hover/gridmove/gridundo) → mark the item ✅ here → next.

## RELATED
prompts/RESUME_MODELLER_P3.md (the shipped spine) · project_modeller_direct_manip memory · prompts/
BOM_DROP_SPATIAL_INVESTIGATION.md (SEPARATE lane, sequence not parallel — shares modeller.html).
```
