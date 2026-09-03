# ⚠ DO NOT REMOVE — LOCKED NEXT SLICE (set 2026-06-29, start here next session)

## ✅ 2026-07-02 DONE — §STRETCH-RIDE shipped, bim-ootb PR #604 (auto-merge armed onto main)
Worktree `/tmp/wt-stretch-ride`, branch `feat/stretch-ride`, rebased onto fresh `origin/main` (past #602/#603) →
commit `b5f21fe`, pushed, PR #604 opened with `gh pr merge --auto --squash`. Independently re-verified by the
orchestrating session (not agent-report-only): `W-STRETCH-RIDE` 9/9 node (real SampleHouse `rel_fills_host`
data), `W-E2E-STRETCH-RIDE` 9/9 headless chromium (real click-Open path + live pixel-readback visibility
assertion — the spec's own §OPEN RIGOR ITEM below, now closed). Full regression suite re-run post-rebase:
`witness_sdg_cascade` 7/7, `witness_arc_editable(+smoke)` 10/10+8/8, `witness_e2e_lod_match` 6/6, `witness_e2e_move`
9/9, `witness_sdg_gate(+smoke)` 6/6+6/6. One pre-existing failure (`witness_stretch_gate_smoke.js` S4) confirmed
NOT caused by this change — verified by checking out every touched file at its pre-commit state and re-running
the (unmodified) test file, byte-identical failure both ways; out of scope, not re-opened.
Files: `modeller/bonsai_gridmove.js`, `modeller/modeller.html`, `modeller/sdg_cascade.js` (the pure `stretchRide`
fn — TRANSLATE rides the delta, SCALE keeps proportional position with rider extent never touched, derived only
over the §ARC-1 bridge's real `rel_fills_host` edges), `modeller/sw.js` (v29→v31, resolved the expected
conflict-magnet collision with #602/#603's own bump per the "take the higher version" doctrine), plus 2 new
witness files. SampleCastle correctly rides ZERO doors (its DB + source IFC both have zero `rel_fills_host`
edges — the honest non-invent boundary, not a gap).
Below this point is the ORIGINAL locked spec, kept for history — don't re-derive it.

## ⏳ (historical, now DONE above) 2026-07-02 — IMPLEMENTATION IN FLIGHT (worktree /tmp/wt-stretch-ride, branch feat/stretch-ride off 1e879b5)
Recon facts pinned by the orchestrating session (verified against code/data, don't re-derive):
- **Root cause is concrete**: `bonsai_gridmove.js elementData()` feeds EVERY authored mesh (doors included) to
  GridKinematicEngine tagged `'IfcWall'` → fillings get their OWN independent TRANSLATE/SCALE commands;
  `bonsai_kernel.js` `gridBy` routes them per-featureId into `foldInsert`'s gridCmds branch — a SCALE there
  literally stretches the door. Fix = STRIP rider commands pre-commit + emit induced GEOM_MOVE from the HOST's
  commands (TRANSLATE: same d; SCALE about host min m: Δ = translateDelta + (f−1)·(c−m); rider extent never scales).
- **Data**: bim-ootb `modeller/SampleHouse_extracted.db` rel_fills_host = 7 rows, `Duplex_extracted.db` = 50;
  every opening has EXACTLY ONE filling in both. SampleCastle: NO rel_fills_host table AND its source IFC
  (`internal/sources/Ifc2x3_SampleCastle.ifc`) has NO relation of any kind for the 182 `stelkozijn` window parts
  (74 fills cover 40 windows + 34 doors, none stelkozijn; IfcRelAggregates only parents 277 IfcBuildingElementPart
  under copings/walls) — relation-based ride honestly yields ZERO clusters there; that boundary is by design
  (non-invent), NOT a gap to heuristic-fill.
- Watchdog decision (2026-07-02): relation-based only, forward-only, don't re-open the heuristic question.
**Scope:** §STRETCH-RIDE — cascade-into-stretch ride ("openings can't divorce"). Completes the stretch story:
when a wall is grid-edited, its hosted openings stay correctly host-constrained.
**Repo:** code = bim-ootb worktree off fresh `origin/main`; spec = this file. Builds on §ARC-1/§SDG-CASCADE/§GATE-1/
§STRETCH-1 (all DONE+LIVE, sw v19). [[project_arc_editable_substrate]]

## THE CLAIM (witness-first, before code)
A grid edit emits per-feature commands (TRANSLATE vs SCALE — `gridmove.computeCommands`). The hosted opening must:
- **host TRANSLATE'd** → the door/window RIDES by the same delta (reuse `SdgCascade.ridersFor` — the move-cascade
  already does exactly this; emit induced GEOM_MOVE for the riders).
- **host SCALE'd (stretched)** → the door does NOT scale (a scaled door is wrong); it stays at its RELATIVE position
  along the wall (or pinned to its opening) so it remains inside the new extent. The §GATE-1 door-out check is the
  oracle: after the ride, the door is STILL within its host footprint (no door-out RED for a legitimate stretch).

## WITNESS (W-STRETCH-RIDE, node + headless, REAL SampleHouse)
- TRANSLATE host → door rides exact delta; offset invariant; rosetta-invertible (like W-SDG-CASCADE).
- SCALE host → door stays inside the stretched wall footprint (gate door-out=0); door not scaled (extent unchanged).
- non-invent: ride only on `rel_fills_host` edges via the §ARC-1 bridge.

## SEAM
`commitGridMove` (modeller.html) already runs the gate after `gridmove.commit`. Insert the ride BETWEEN commit and
gate: from `res.commands`, find TRANSLATE'd hosts → ridersFor → emit induced GEOM_MOVE; for SCALE'd hosts compute
the door's keep-in-extent move. Then the gate validates (door-out should be 0 for a correct ride).

## ⚠ OPEN RIGOR ITEM (user-raised 2026-06-29) — fold into this slice's witnesses
User wants the MATHS ALONE to prove the canvas-visible fact (NO human visual check). Current witnesses prove
GEOMETRY (position/extent/centre via actual scene-mesh `geometry.boundingBox`, conformity via gate, mesh-in-scene +
selectable) — but DO NOT prove RASTERIZED PIXELS (visible/lit/in-frustum/opaque/not-occluded). The only visibility
check done all session was ONE screenshot (a visual check, which the user does not want to count). **Add a
pixel-readback visibility assertion to the witness harness** so "it is visible" becomes a §-log claim, not an
eyeball: headless WebGL `gl.readPixels` (or a 2D-projected screen-rect colour sample) asserts the edited mesh
occupies its expected screen pixels with non-background colour; assert `mesh.visible===true && material.opacity>0 &&
in-camera-frustum`. Also still-OPEN from earlier: rotate-witness VERTEX check (orientation under yaw is only
centre+extent-proven, not vertex-proven — see [[feedback_whitebox_no_handwave_geometry]]).
