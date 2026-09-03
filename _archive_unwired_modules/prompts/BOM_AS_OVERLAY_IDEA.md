> ⚠ **SUPERSEDED-WHERE-IT-CONFLICTS (2026-06-25 c).** This is the original IDEA CAPTURE. The **canonical** model is now
> `prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md` §VISION-LOCK. Two things here are EVOLVED past:
> (1) **the discipline seam** — STR is **no longer** a transform-partner of ARC; **STR is a WALKER** (it crawls the ARC
> and regenerates to *strengthen* it, like MEP services it). Read every "ARC/STR transform" below as "ARC transforms;
> STR walks." (2) **BOM membership** ("ARC/STR only, dropped verbatim") is RESOLVED 2026-06-26: **editable BOM = ARC
> ONLY; STR moved fully to the walker side** (`Modeller/DISC_Walker/STR_ROUTEWALKING_SPEC.md`), extracted STR kept as oracle. Read "drop
> ARC/STR verbatim" below as "drop ARC verbatim; STR is walked." The *handle/overlay* mechanics below still hold.

# 💭 IDEA CAPTURE — "BOM as an abstract overlay over the ARC/STR substrate"

> **Status:** EXPLORATION, surfaced in the wrong session — **ADOPTED 2026-06-25** as the editing TRUNK and
> PROMOTED into the engine spine [[prompts/SPATIAL_DEPENDENCY_GRAPH.md]] (this doc = the substrate+handles surface;
> the graph = its formal Phase 1–2). Cross-link: [[prompts/MODELLING_FROM_BOM_CASCADE.md]],
> [[prompts/CONSTRUCTION_GRID_BOM_DUAL_MODEL.md]], [[prompts/FUSED_4D5D_WEDGE_LANE.md]]. No code yet; discussion only.
> **Two factual corrections from the 2026-06-25 homework (see below):** the void/fill host link is NOT extracted
> today (proximity-guessed → needs Path B); and **Rooms is the weak handle** (`IfcSpace=0` in SH+Terminal).

## The question that started it
Why BOM-layering at all? Why not just **drop a complete building as-is and grid-edit it** like a normal
Bonsai-style editor (land any authored IFC, move grids, edit in-betweens)? Is there a more elegant simplicity
hiding here — are we rehashing?

## The progression (three reframes, each narrower than the last)
1. **vs a flat mesh:** A mesh is a corpse (triangles, no quantities, no types, no host/child) — you can nudge
   it but the enterprise can't fold anything from it, and a grid-stretch is a funhouse mirror (windows fatten,
   fixtures grow, roof pitch changes). The BOM is the *recipe* that carries quantity + type + host/hosted, so
   the edit becomes a **typed, signed fact** the 4D/5D/ERP can re-fold. *But "better geometry editor" is NOT
   the justification — visual editing is table stakes; the fold is the only differentiator.*
2. **vs Bonsai (which already has the IFC graph):** Authored IFC already gives containment, void/fill,
   aggregation, type/occurrence, quantities. Over that baseline the BOM adds exactly **two** things and nothing
   else: **(a) generative binding** — verbs (LINE/TILE/CLUSTER) say "these 20 columns belong to grid C's
   spacing rule," which raw IFC lacks (20 independent absolute placements → move the grid and nothing follows);
   **(b) the signed fact-fold** — every edit is a live quantity the enterprise re-folds from, not a QTO you
   re-export later.
3. **THE ONE THAT LANDED (user's reframe):** Don't BOM every model at all. **Keep the substrate = the
   extracted DB, ARC/STR only, dropped verbatim** (the proven 0.000mm, non-invent path → no mistakes). Hang an
   **abstract BOM *overlay*** on top that only provides *handles*: **Grids · Storey · Rooms · DISC**. Three of
   those four already exist as the **Find panel's facets** (Storey/Rooms/Discipline) — so the overlay is a
   *promotion of Find's faceting from "navigate" to "edit-handle,"* not a new store. The BOM becomes a **lens
   over authored geometry, not a pipeline you author into.**

## Why the overlay framing is the elegant simplicity
- We **never build verbs/recipe for editing.** Editing = **transforming captured placements through a handle**,
  not reconstructing geometry. **Transform ≠ re-derive** → this *structurally* escapes the rotation/facing GIGO
  scars (those all came from re-deriving/guessing orientation; an overlay that only translates/stretches what
  extraction captured can't reintroduce them).
- Substrate carries geometry (verbatim, non-invent). Overlay carries dimensions/topology (handles). Geometry is
  a **projection** of a handle edit; you edit facts, geometry/cost/schedule are views.

## The narrow scope to actually ponder: "after an ARC/STR drop, the user nudges + readjusts dimensions"
Every gesture collapses to **one primitive**:

| Gesture | Really is |
|---|---|
| Nudge a wall/column | translate one element's anchor by Δ |
| Restretch a bay | move a grid line → translate its bound set by Δ |
| Resize a room | move the room's bounding walls → same as above |
| Change storey height | move a Z-datum → lift everything above by Δ |

→ **One rule:** *a handle OWNS a set of substrate elements; drag by Δ → translate owned anchors, stretch the
spanners between two moved handles, hosted elements ride their host, point-owned fixtures stay invariant.*
Grids (X/Y), storey datums (Z), room edges are just three **sources of handles** feeding the same rule.

The rule's parts mostly live in the substrate (one correction):
- **host-ride** = void/fill relation (openings glued to walls). ⚠ **CORRECTION (2026-06-25):** this is NOT extracted
  today — `rel_fills_host` is dropped at extraction and `_inheritHostRotation` PROXIMITY-GUESSES it (GIGO). It is
  authored & complete in the SOURCE IFC (`IfcRelFillsElement`: 7 for SH, 371 for Terminal) → **recover it = Path B**.
  Host-ride is real only after Path B; the same recovery serves facing, the door-fit RED constraint, and the ORANGE
  "openings may change" signal. [[prompts/SPATIAL_DEPENDENCY_GRAPH.md]] §Path-B-quadruply-load-bearing.
- **spanner-stretch** = slab/beam referencing two grid lines stretches as they separate.
- **invariant** = types/fixtures owned by a *point* handle, not a *span*.

## The ONE new thing to build
**Grids** — the only missing facet. Cheap + non-invent: cluster ARC/STR **column centroids + wall centerlines
per axis** → candidate grid lines; each line *owns* elements whose anchor snaps within tolerance. **Ownership is
DERIVED from substrate positions, never authored** → inherits the substrate's "no mistake" guarantee. Storey
datums come free (IfcBuildingStorey elevation = the Z-grid).

## The discipline seam (why DISC is in the overlay)
ARC/STR are *dropped* → they **transform** under a handle. MEP is *RouteWalked* (regenerated from rules, not
dropped) → DISC is the **regeneration boundary**: nudge a structural wall → ARC/STR set translates directly,
then RouteWalker re-routes the MEP that referenced it. The cross-discipline cascade is already how DISC is used;
the overlay just makes the trigger a drag.

## Crystallized thesis (one breath)
> Drop ARC/STR verbatim from the extracted DB (substrate, no invention). Hang an abstract overlay —
> **Grids + the Find facets (Storey/Rooms/DISC)** — where each handle *owns* a set of captured elements. Every
> edit (nudge, bay, room, storey-height) is the same primitive: drag a handle → translate owned anchors, stretch
> spanners, hosts ride, fixtures invariant; ARC/STR transform, MEP re-RouteWalks. **We transform captured
> geometry, never re-derive it.** The viewer is Bonsai until you edit; the moment you edit, it's the only editor
> where the drag is a signed fact the enterprise folds from.

## Proposed first witness (if pursued)
**W-OVERLAY-GRID-MOVE:** on a real building (SampleHouse / Duplex), derive the grid facet, move grid line C by
+300 mm → assert: bound columns follow exactly, openings ride their host walls, a spanning slab/beam stretches,
fixtures stay invariant, MEP re-routes via RouteWalker, **and ARC/STR rosetta still 0.000 mm against the
transformed substrate** (transform, not re-derive). Whitebox §-log proof first; browser second.

## Open questions — RESOLVED 2026-06-25 (verified against the code)
- **Find faceting reusable?** ✅ YES. `navigate_find.js` has `_treeMode = storey|disc|room|material|phase` as axis
  pills, already **data-gated** ("Storey/Discipline always; Room/Material/Phase appear only when data present"). So
  promoting facets → edit-handles is reuse, not re-derive. **But Rooms is the weak handle:** `IfcSpace=0` in SH +
  Terminal → handle set is really **Grids + Storey + DISC solid; Rooms when-present.**
- **Spanner detection?** bbox crosses two datums = the `spans` edge ([[prompts/SPATIAL_DEPENDENCY_GRAPH.md]] contract).
- **Storey-height lift / straddlers?** ✅ ANSWERED by §SHELL-N-ZSPAN ([[prompts/CONSTRUCTION_GRID_BOM_DUAL_MODEL.md]]):
  stairs/shafts/tall-curtain-walls are **measured Z-spanners** (Terminal: 40 m risers, 45 m columns, 44 m facade) —
  they *stretch* with the storey-height datum (the `instanced-by n` edge), not rigidly ride it. NOT a clean Z-partition.
- **Subsume or sit-beside the cascade?** Neither — it became the **editing TRUNK**, promoted into the engine spine
  [[prompts/SPATIAL_DEPENDENCY_GRAPH.md]]; the cascade's generative half is the layer above. Oracle for any geometry
  claim here = the **pristine `extracted.db` / raw extraction** (transform captured geometry), NOT cooked `output.db`.
