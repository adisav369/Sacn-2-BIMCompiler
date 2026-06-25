# DAGeVu Modeller — User Guide

> **Work in progress.** The DAGeVu modeller is an early, spine-proven authoring surface — this guide is
> deliberately a short intro plus an index of the toolbar icons. Expect it to grow as the modeller does.

---

> ## ⚠ UNDER DEPRECATION — a major direction shift is in progress *(2026-06-25)*
>
> **The from-scratch authoring surface described below is not broken — it is being *inverted*.** We are
> experimenting with a new primary direction for the modeller, and the existing tools (insert · sketch ·
> extrude · sweep · the grid) remain available while the shift lands. Nothing here is going away without a
> replacement; this note is so you don't feel stranded if the surface starts changing under you.
>
> **The inversion — *don't author from scratch; open a ready-made building and edit it.*** Instead of drawing a
> model up from a blank grid, you **📂 Open an existing `extracted.db`** (a real building's **ARC** model — its
> *digital twin*) and **edit that**. The other disciplines and dimensions are not re-drawn — they **follow**:
> structure, MEP, the 4D schedule, the 5D cost and the ERP all **auto-complete** by "crawling" against the ARC
> (RouteWalk), and every edit and every follow is one **signed operation** the enterprise folds from.
>
> **Why the shift — four benefits you feel immediately:**
> - **Speed** — you start *complete* (a real building), not from an empty canvas.
> - **Completion** — open a bare ARC and the rest *fills itself in* (RouteWalk), rather than you placing every part.
> - **Reuse** — the real building is the substrate; you compose and edit *batches*, never reconstruct.
> - **Trust** — the opened twin is a faithful, verified reconstruction, so editing starts from truth (no invention).
>
> **The underlying principle: *Open = ARC only.*** What you open and edit is the **architectural** model — the single
> editable substrate. Structure / MEP / 4D / 5D / ERP are shown but *derived* (they regenerate against your ARC
> edits). This makes the digital twin **editable + generative** (it completes itself), not a read-only mirror.

*How it works (the inversion at a glance):*

<svg viewBox="0 0 820 320" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="The modelling inversion: open a ready ARC twin, edit it, and the rest auto-completes from one signed op-log." style="max-width:100%;height:auto;font-family:system-ui,-apple-system,sans-serif">
  <rect x="0" y="0" width="820" height="320" fill="#fbfcfe"/>
  <text x="20" y="28" font-size="16" font-weight="700" fill="#1b2b3a">The Modelling Inversion — open a ready ARC twin, edit, auto-complete</text>
  <rect x="20" y="46" width="780" height="40" rx="6" fill="#f1f1f1" stroke="#cfcfcf"/>
  <text x="34" y="63" font-size="12.5" font-weight="700" fill="#9a9a9a">OLD ✗ author from scratch</text>
  <text x="34" y="79" font-size="12" fill="#9a9a9a">blank canvas  →  draw walls  →  place every element by hand  →  slow, and still incomplete (no STR/MEP/4D/5D/ERP)</text>
  <g font-size="12" fill="#16324a">
    <rect x="20" y="118" width="170" height="74" rx="8" fill="#e9f2fb" stroke="#3f78b5" stroke-width="1.5"/>
    <text x="105" y="140" text-anchor="middle" font-weight="700">📂 Open extracted.db</text>
    <text x="105" y="158" text-anchor="middle">the ARC twin</text>
    <text x="105" y="176" text-anchor="middle" fill="#5b6473">(real building, verbatim)</text>
    <rect x="232" y="118" width="170" height="74" rx="8" fill="#e9f2fb" stroke="#3f78b5" stroke-width="1.5"/>
    <text x="317" y="140" text-anchor="middle" font-weight="700">Edit the ARC</text>
    <text x="317" y="158" text-anchor="middle">Outliner BOM Tree</text>
    <text x="317" y="176" text-anchor="middle" fill="#5b6473">re-parent · grid · move</text>
    <rect x="444" y="118" width="170" height="74" rx="8" fill="#eef6ee" stroke="#5a9e5a" stroke-width="1.5"/>
    <text x="529" y="140" text-anchor="middle" font-weight="700">Followers crawl</text>
    <text x="529" y="158" text-anchor="middle">RouteWalk</text>
    <text x="529" y="176" text-anchor="middle" fill="#5b6473">STR · MEP regenerate</text>
    <rect x="656" y="118" width="144" height="74" rx="8" fill="#eef6ee" stroke="#5a9e5a" stroke-width="1.5"/>
    <text x="728" y="140" text-anchor="middle" font-weight="700">Auto-complete</text>
    <text x="728" y="158" text-anchor="middle">4D · 5D · ERP</text>
    <text x="728" y="176" text-anchor="middle" fill="#5b6473">the live twin</text>
  </g>
  <g fill="#3f78b5" font-size="20" font-weight="700">
    <text x="201" y="161">→</text><text x="413" y="161">→</text><text x="625" y="161">→</text>
  </g>
  <rect x="20" y="222" width="780" height="44" rx="8" fill="#1b1d23"/>
  <text x="410" y="243" text-anchor="middle" font-size="13" font-weight="700" fill="#dce6f4">ONE signed op-log</text>
  <text x="410" y="260" text-anchor="middle" font-size="11.5" fill="#aab4c4">every edit &amp; every follow is a signed fact — model · structure · MEP · 4D · 5D · ERP all fold from it</text>
  <text x="20" y="292" font-size="11" fill="#8a93a0">Open = ARC only (the single editable substrate). The twin becomes editable + generative — it completes itself.</text>
  <text x="20" y="308" font-size="10.5" fill="#aab4c4">Experimental: modeller.html?bomtree · spec SPATIAL_DEPENDENCY_GRAPH §INVERT-TWIN-EDITING</text>
</svg>

> *Status:* the first slice — the **📂 Open** icon and the editable **BOM Tree** in the Outliner — is live behind the
> experimental `?bomtree` flag. The auto-complete (RouteWalk) and the geometry-on-canvas editing land next. The
> classic authoring surface documented below stays usable throughout.

---

## What it is

DAGeVu is a **browser BIM authoring** surface that sits beside the read-only viewer. Instead of editing a file,
you apply **operations** — insert a component, sketch a profile, extrude, sweep, fillet — and the model is a
**deterministic fold** of that signed operation log. The op-log *is* the feature tree: every action is
recorded, replayable, and reversible, and the same signed-log idea drives the Kernel-ERP engine.

**Open it:** [red1oon.github.io/bim-ootb/viewer/modeller.html](https://red1oon.github.io/bim-ootb/viewer/modeller.html)
(desktop — the B-rep kernel is heavy). The **Home** button returns to the Matrix landing.

![DAGeVu Modeller — the INSERT · BOM CATALOG panel open (left), the authored scene on the grid, the ⋯ toolbar pill rail at the bottom‑right, and the op-log history scrubber along the bottom](assets/modeller.png)

## Your first insert — the BOM catalog

The fastest way to build is to **assemble, not draw**. Tap the **Insert** tool (the cube icon in the
toolbar — open the **⋯** pill rail at the bottom‑right if the pills are collapsed) to open the
**INSERT · BOM CATALOG** panel on the left:

1. **Find a component.** Type in **search parts…**, or narrow with the filter chips —
   **All · Structure · Openings · Furniture · Sets**. The catalog is extracted from the real
   component library (`component_library.db` plus the per-building BOMs), so what you see are actual
   parts and pre-built assemblies.
2. **Pick a part — or a whole set.** Single parts drop one element. **Sets** are *assemblies* (whole
   BOM sets) grouped by level — **Buildings · Floors · Rooms · Sets · Items** — each collapsible, with a
   part-count badge (e.g. *Duplex Single Half-Unit*, *DX Level 1 Structured*). Pick one to drop the
   entire recipe at once.
3. **Aim and place.** The status line prompts **"aim on the grid, R to rotate, click to place"**. Move
   the cursor over the grid to position the ghost preview; press **R** to rotate (the **Rotate** angle
   in the panel footer updates); set **Elev** (metres) to lift it to a storey height; then **click** to
   drop it.
4. **It's a signed op.** The placement lands as one operation in the op-log — visible on the history
   scrubber at the bottom and fully **undoable** (`Ctrl+Z`). An assembly drops as a single grouped op
   of *N* parts, each seated and oriented from the recipe — e.g. **doors and windows take their host
   wall's facing automatically**, rotating with the wall rather than landing flat.

From there, use the toolbar pills to refine: move/rotate a placed object, sketch and extrude new geometry,
cut, sweep an MEP run, or bump a component's level of detail. The **Outliner** panel (left) lists the
placed elements; collapse it with its chevron to free up the canvas.

## The toolbar — icon index

The toolbar is a **⋯ pill rail** at the bottom-right: tap **⋯** to fan the icon-only glass pills up,
and hover any pill for its name. The **? Help** pill opens the **TOOLBAR · PILL REGISTRY** — the live
list of every tool's icon, name, and keyboard shortcut (`Esc` always cancels the current mode).

| Icon | Does |
|------|------|
| **⋯ Toolbar** | Fan the toolbar pills open / closed (bottom-right) |
| **? Help** | Toolbar & shortcuts — the live pill registry |
| **Home** | Back to the Matrix landing |
| **Fit** | Zoom to fit — frames the selection, or the whole scene (`F`) |
| **Iso** | Cycle the view: Iso ⇄ Top |
| **Wall** | Draw a wall |
| **Opening** | Place an opening (door / window) in a wall |
| **Grid** | Show / add a construction grid |
| **Move Grid** | Drag a gridline — attached walls recompose with it |
| **Move** | Move the selected object — drag an axis handle or nudge with the arrow keys (`M`) |
| **Sketch** | Start a 2D sketch |
| **Extrude** | Push a sketch profile into a solid |
| **Axis** | Set the constraint intent the solver enforces on the sketch |
| **Cut** | Boolean-cut one solid with another |
| **Route** | Define a route to sweep a profile along (e.g. an MEP run) |
| **Sweep Run** | Sweep the profile along the route |
| **Fillet** | Round a selected solid's picked edges |
| **Apply** | Commit the pending fillet / chamfer |
| **Insert** | Insert a library component — assemble, don't draw (catalog + BOM-assembly drop) |
| **LOD 200** | Refine the selected component's level of detail (same signed row) |
| **IFC** | Export the authored model as IFC4 |
| **Undo** | Undo the last operation (`Ctrl+Z`) |
| **Redo** | Redo (`Ctrl+Shift+Z`) |
| **Delete** | Delete the selection (`Del`) |
| **Clear** | Empty the scene |
| **Sound** | Toggle authoring sound feedback |
| **Connect** | Connect Scene — share selection / timeline with the Viewer & ERP (opt-in; surfaces stay separate) |

---

*Part of [BIM OOTB](USER_GUIDE.md). Copyright (c) 2025–2026 Redhuan D. Oon. MIT Licensed.*
