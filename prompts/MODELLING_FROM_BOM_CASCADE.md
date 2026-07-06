# Modelling from the BOM Cascade — vision / ideation (opened 2026-06-23)

## ⚠ DO NOT REMOVE — scope + standing rules
Vision doc for the Modeller's core authoring concept: **the building's BOM cascade IS the modelling grammar.**
Ideation, not yet a code spec — capture the concept before it scatters; spec-first before any implementation.
NON-INVENT: every authored edit is a signed op that FOLDS to geometry (and to the enterprise); never bake cooked
positions. Oracle for any geometry claim = the Java compiler `output.db`. Read the log after every run.
Companion: cascade as-built measured in [[RESUME_DROP_OUTLINER_ROADMAP]] §2; discipline doctrine in
[[ONTOLOGICAL_BOM_EXTRACTION]] (construction-verbs vs placement-verbs); routing in
[[project_sc_naming_and_disc_routing]].

> **DEEPENED 2026-06-25 → [[CONSTRUCTION_GRID_BOM_DUAL_MODEL]]:** piece 2 (the grid lattice) sharpened into a
> first-class **Construction-Grid model** the BOM binds to (two coupled models); adds **datum-order/sequence
> deduction** (foundation-first dynamic datum) + the **§SHELL-N-ZSPAN** layering (shell + typical-storey×n + measured
> Z-spanning leaves that vanish at n→1) + IFC-enrichment properties. Same measure-don't-whitelist doctrine as the
> orientation lane.

## THESIS (the one line)
**The only modeller where the model and the enterprise are the same signed recipe — every stretch/move/delete is a
fact the building, the schedule, and the budget all fold from.** Grid-datum parametric stretch is table stakes
(Revit/ArchiCAD have it); the novelty is the *substrate*: a portable, branchable, signed **BOM op-log** that folds
simultaneously to geometry AND to cost/schedule/ERP, editable at the **recipe-node** level, round-tripping to the
compiler and ERP instead of dying in a proprietary file.

## THE FOUR-PIECE STACK
1. **Cascade (the WHAT/tree)** — `BUILDING → FLOOR → ROOM/SET → ASSEMBLY → LEAF`. Each `bom_id` is a unit of
   selection *and* edit. Stores **relative offsets** (tack chain: `BUILDING_origin + FLOOR_dx + LEAF_dx`).
2. **Grid datum lattice (the WHERE-anchors)** — a **2D×3D grid**: 2D plan grid (X/Y gridlines) × 3D level grid
   (Z storeys / floor-to-floor). Construction elements *bind* to datums; editing a datum re-folds what's bound.
3. **Construction verbs (the HOW-to-rebuild)** — `WALL(baseline,height,thickness)`, `SLAB(footprint,thickness)`,
   `ROOF(pitch,footprint,ridge_axis)`, `OPENING(u,v host=wall)`. Parametric; distinct from **placement verbs**
   (TILE/LINE/CLUSTER) that drop *fixed* FFE leaves. **Today's gap (frozen middle):** SC's leaves are frozen
   boxes — the stretch only becomes a re-fold once these verbs live IN the BOM.
4. **Op-log (the WHEN/provenance)** — every edit is one signed `kernel_ops` op; geometry is the **fold** of the
   log over the BOM. Gives undo/redo (log ops), what-if branches (fork+compare), audit, and the wedge: the *same*
   recipe folds to 4D/5D/ERP.

## HOW BOM SETS ARE USED (the selection/edit grammar)
Selection is by **recipe node**, not by element or generic layer: "this floor's structure," "this room's furniture
set," "this curtain-wall assembly." You operate at any altitude of the recipe.
- **Move a subset** = change ONE node's offset → all descendants re-fold by stored relative dx/dy/dz. Move a floor,
  not 1000 elements.
- **Delete a subset** = remove one `bom_id` → its whole subtree of `GEOM_INSERT`s vanishes as a single fact.
- **Hide / isolate / swap / duplicate** = visibility, sub-BOM reference swap, or clone — all at node granularity.
- **Reusable templates** — any node (stair ASSEMBLY, bathroom SET) is a sub-BOM: author once, instance many; edit
  template → instances re-fold, or fork one. Component reuse at the *recipe* level.
- **Recipe-graft as diff** — "copy L1's furniture to L2," "this floor minus partitions" = reviewable, revertable
  subtree diffs, portable to sibling buildings.

## THE 2D×3D GRID — STRETCH ≠ SCALE (the construction-vs-drawing distinction)
A drawing/photo stretch is a **transform on vertices** (everything scales — door widens, wall thickens, roof pitch
changes). A construction stretch is a **re-evaluation of verbs at new datum parameters** — add *length*, hold
*sizes / angles / relative positions*. Geometry regenerates; it is never scaled.
- **Walls stretch** — drag a gridline → WALL baseline endpoints move → length recomputes, **thickness held**.
- **Roof slopes re-derive** — drag plan wider → footprint run grows → **at held pitch the ridge rises** (or pin
  ridge height → pitch flattens). *The user picks the invariant* (pitch vs height) — impossible with mesh-scaling.
- **Slabs/floors** — footprint corners on grid → extent re-folds with the datum.

## OPENINGS — COHERENT BY CONSTRUCTION (host-constrained drag)
An opening is a **void-child of its host wall** (`OPENING(u,v) host=wall`, the IfcRelVoids/Fills relation), NOT a
free leaf. Consequences:
- When the wall **stretches or moves**, the opening re-folds at its anchor at its **real size** — coherence isn't
  coded, it *falls out* of being a cascade child with a relative anchor.
- When **selected**, the opening drags **along its host's natural axis only** (u along the baseline, optionally v up
  the height). Its degrees of freedom are the host's surface parameters — dragging changes the *anchor parameter*,
  not world XYZ.
- **It cannot be divorced** — because its only coordinate IS the host parameter, "off the wall" is *unrepresentable*.
  **vs Revit:** Revit doors/windows are wall-hosted and slide along the host, but Revit stores world position + a
  host *link* that can be re-hosted or orphaned (delete wall → opening errors/deleted). Here divorce is structural,
  not rule-enforced — there is no field in which an orphaned opening could exist.
- **Sibling-aware snap** — dragging one window can hold equal spacing with its siblings (a LINE verb over the wall
  length) or break out to an explicit anchor; the drag is still a signed op (parameter change) that folds.

## VISUAL / LOD CONTEXT MANAGEMENT (working heavy models)
The cascade **is the LOD ladder** — render at any altitude: a floor as one massing block / preview icon, expand to
rooms, expand to elements. LOD = how deep you fold the recipe for display (and only the active subtree needs a full
mesh fold; the rest stay cheap as bboxes/proxies — lazy per-subtree fold).
- **Preview icon** for a collapsed subtree — a whole floor shows as one icon/thumbnail until expanded; collapse in
  the Outliner = proxy in the viewport (Outliner↔viewport coupling). A "reveal all in high-LOD mesh" toggle folds
  the full tree on demand.
- **Working-set focus** — while editing a node, render its subtree at high LOD; show **adjoining elements**
  (cascade/spatial neighbours: same room, abutting walls, host/hosted) at high LOD for context; **bbox or ghost the
  rest**. Keeps orientation without clutter or cost.
- **Auto-section** — working inside a room auto-clips the floor/ceiling above (driven by the active node's bbox),
  like a recipe-aware section box.

## ADDITIONS FROM DOMAIN OVERSIGHT (proper context)
- **The gizmo's DOF is FOLDED FROM THE VERB.** Fixed FFE leaf → free 3-DOF placement (drop a dining set anywhere).
  Opening → 1–2 DOF on host surface. Wall → endpoints on grid (2D). Roof → pitch/footprint handles. The manipulator
  affordance is *derived from the recipe*, not a generic move tool — the UI tells you what's construction-legal.
- **Coherence classes** — the relation that binds a child to its parent defines what edit is allowed and what
  invariant holds: opening↔wall (void/fill, can't divorce), furniture↔room (containment, soft — can leave), structure
  ↔grid (datum). The cascade encodes which; the manipulator reads it.
- **Two interaction modes** — **stretch handles** on grid datums (re-fold construction verbs) vs **move handles** on
  leaves (re-place fixed FFE). Construction vs placement, visibly different.
- **Live re-fold preview** — while dragging, show the re-folded result (window re-anchoring, ridge rising). Because
  it's a fold, preview = fold-at-cursor.
- **Selection-altitude scrub** — a modifier climbs/descends the cascade at the same click point (click→opening;
  up→wall; up→room; up→floor). Matches the tree.
- **Clash/coordination on drag** — MEP routes + clearances are known; dragging a wall can live-flag a clash with a
  route or a clearance violation, and routes **re-walk** within the new ARC/STR envelope (RouteWalker; MEP stays a
  parasitic overlay, never a draggable layer). Construction-aware feedback as you model. [[RESUME_MEP_COORDINATION]]

## HONEST NOVELTY POSITIONING (don't oversell)
- **Table stakes (others have it):** grid-datum parametric stretch, hosted openings that slide, section boxes,
  detail-level LOD, worksets (Revit/ArchiCAD). Bonsai/IFC have the data model but weak authoring.
- **What no one has (the pitch):** the parametric construction model AS a signed BOM op-log that folds to geometry
  **and** the enterprise from one log; recipe-node-level editing; divorce-is-unrepresentable openings; cascade-derived
  LOD that doubles as the edit grammar; and **portability** — it round-trips to the compiler and ERP, not locked in a
  proprietary file. Revit's parametrics die inside Revit; here the parametric model **is** a foldable signed recipe.
  This is the FUSED 4D/5D wedge extended down into geometry authoring. [[prompts/FUSED_4D5D_WEDGE_LANE.md]]

## DEPENDENCIES / WHAT'S NEEDED TO BUILD (later — spec first)
1. **Construction verbs in the BOM** (close the frozen middle): WALL/SLAB/ROOF/OPENING as grid-bound parametric
   `verb_ref` rows, so stretch = re-fold not transform. **SPEC'd 2026-06-23 → [[CONSTRUCTION_VERB_BOM_GRAMMAR]]**
   (grammar + carrier + fold contract + witnesses, grounded in `WallSpec`/`WallThickness` 4-token enum/`SlabSpecAD`/
   roof param-bag; zero schema migration). Original sketch in [[ONTOLOGICAL_BOM_EXTRACTION]].
2. **Void/fill as a real cascade edge** (opening = child-of-wall), so coherence is structural.
3. **Grid datums emitted** (IfcGrid) so endpoints bind to draggable gridlines (today only StructuralPlacer reads
   gridlines internally; honest degrade = per-element move until emitted).
4. **Outliner ↔ viewport coupling** reading the cascade (`bonsai_outliner.js` exists; `grid_*` family has
   drag/kinematics/recompose). Map node→leaf `GEOM_INSERT` ids for subtree selection.

## MORE IDEAS / OPEN THREADS (next-session fuel)
- **Massing-first, carve-down authoring** — start from one BUILDING block; drag Z level-lines → floors appear; drag
  plan partitions → rooms appear. The cascade *grows as you carve* (top-down recipe authoring), opposite to dropping
  a finished building. Each carve is a signed op.
- **Live quantity/5D on stretch** — because a stretch re-folds the verb, wall area / slab volume recompute live →
  the QS watches cost move as they drag. The wedge, felt in the hand. Pair with **compliance-as-you-stretch** (min
  room dims, corridor widths, ceiling heights, MEP clearances per regs) flagging red during the drag.
- **Design-change = a reviewable diff (PR for buildings)** — "widen lobby 2m" is one op + its fold delta on
  geometry+cost+schedule. Approve/reject/branch like a PR; **variant branches** fork the op-log (Option A vs B) and
  compare folded cost/schedule side-by-side (geometry what-if, mirroring the 4D/5D what-if).
- **Two-way datum binding** — moving an element can push back to its gridline (offer "move the datum" vs "detach");
  bidirectional element↔datum.
- **Parametric joins** — wall-meets-wall miter/butt is a *derived* relation that re-folds when either stretches
  (WallGenerator already does opens_to / shared-edge — surface it as an editable join).
- **Semantic snap ecology** — snap targets derive from cascade+grid (gridlines, sibling spacing, host surface, level
  planes), not raw geometry. **Outliner = command surface**: right-click a node → only verb-legal ops (WALL → "add
  opening / split / change type"; FLOOR → "duplicate up / set height").

## STATUS
Ideation. No code. Cascade is proven present (SC: BUILDING→11 FLOOR→99 SET+52 ASSEMBLY→3516 leaves). The interaction
layer (subtree edit, grid stretch, host-drag, LOD) is the work, gated on the construction-verb upgrade (frozen middle).
**Next-session entry:** (a) ✅ DONE — construction-verb BOM grammar spec'd → [[CONSTRUCTION_VERB_BOM_GRAMMAR]];
**now Phase A = the WALL factorizer + W-CVERB-WALL read-back witness vs `output.db`**; (b) Outliner↔viewport
subtree-select wiring (`bonsai_outliner.js` + `grid_*`); or (c) finish the SC drop work (re-measure vs oracle /
fidelity gates / commit). The IFC2BOM fault (§1) is DONE.
