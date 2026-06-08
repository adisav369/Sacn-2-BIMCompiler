# Browser BIM 2D Guide

> **Architecture:** [2D Layout](2D_LAYOUT.md) | **Theory:** [BIM Modeller OOTB](BIM_Modeller_OOTB.md) | **Manifesto:** [MANIFESTO.md](MANIFESTO.md)

<div class="bim-banner" markdown>
<b>No install. No server. No save button.</b> Open a building in the browser, press G, drag a grid line, watch the cost panel update. Reload — your changes are still there.
</div>

---

## Getting Started

1. Open the viewer: load any building from the landing page
2. Press **G** to toggle the grid overlay
3. Click **GF** in the panel to see a floor plan with door arcs and dimensions
4. Long-press a grid line (~0.4s), drag it — the cost panel appears
5. Press **Ctrl+Z** to undo. Reload the page — grid stays where you dragged it.

---

## Prerequisites — what a building DB needs before 2D grid EDIT

> **Recall pin (do not re-discover this):** *basic* 2D (the storey-aware cut, GF/L1 floor
> plans, grid-line detection, long-press grid **drag**, and the spatial-BOQ cost panel) works
> on a plain `{PREFIX}_extracted.db` alone — it only needs `elements_meta` (with `storey`),
> `element_transforms`, and `component_geometries`. Verified: Duplex and SampleCastle render
> grids + contours with **no** MEP/route tables present.
>
> **Full grid EDIT** — the MEP re-route on a discipline switch and BOM-scoped recompose — needs
> three more pieces *defined for that building*, or it silently emits nothing:
>
> | Concern | Artifact (DB · table) | Used by | Absent ⇒ |
> |---------|----------------------|---------|----------|
> | **BOM** (the recipe) | `{PREFIX}_BOM.db` · `m_bom` / `m_bom_line` | BOM recompose on edit | no recompose; baked in by the compile / SC-BOM-seed |
> | **ERP validation** | `ERP.db` · `AD_Val_Rule` (read-only `valConn`) | clearance / spacing / priority constraints (`G4_SRS.md`) | edits unconstrained |
> | **Route-walking routine** | `mep_rw.db` · `ad_mep_pattern` (CW/SP topology) + `ad_mep_anchor` (per `building_type`) | `RouteWalker.walk()` (`route_walker.js` / `routewalker.js`) | `§RW_WALK no patterns…/no anchors…` → **0 MEP segments placed** |
>
> So: **a building must be a "known building"** in `mep_rw.db` (its `building_type` has patterns +
> anchors) and carry its `m_bom` + `AD_Val_Rule` before grid-edit MEP/BOM behaviour is real.
> Without them the plan still *draws* and grid lines still *drag* — but a DISC switch produces no
> fresh route. Diagnose presence with the `§RW_WALK building=… cw=… sp=…` log (or its
> `no patterns/no anchors` warning). NOTE: the `ROUTE → RouteWalker.walk` wiring itself is the
> `RED_PILL.md` **B3** task (was a straight-line stub) — confirm it's live before relying.
> (`BOM_ENGINE_SPEC.md` §strategy=ROUTE · `G4_SRS.md` valConn/bomConn · `DISC_VALIDATION_DB_SRS.md` §6.12.3)
>
> **Persistence interaction (2026-06-08, sw v626 / PR #202):** the kernel-op IndexedDB persistence
> fix is *orthogonal* to the above — it only snapshots the building `_extracted.db` (whole-DB
> `export()`, deletes nothing; touches no BOM/ERP/`mep_rw` table). Complementary effect: RouteWalker
> emits its MEP as `kernel_ops ELEMENT_PLACE`, which now **survives reload** (previously lost — the
> persist path was dead). ⚠ Reasoned + scope-verified, **not witnessed end-to-end**: the combined
> *grid-edit → RouteWalker → persist → reload* round-trip has not been run on a route-defined
> building (none loaded had `mep_rw.db` data). Prove it on such a building before relying.

---

## Keyboard & Interaction Cheat Sheet

| Key / Gesture | Action |
|---------------|--------|
| **G** | Toggle grid overlay on/off |
| **GF** button | Ground Floor plan — section cut + door arcs + opening labels |
| **L1** button | Level 1 plan |
| **Front / Back / Left / Right** | Elevation views |
| **Roof** | Roof plan view |
| **Unlock** (lock icon) | Return to free 3D orbit |
| **Long-press** (~0.4s) on grid line | Start grid drag — 3D planes appear |
| **Drag** after long-press | Move grid line — cost panel updates live |
| **Release** | Commit `GRID_MOVE` to `kernel_ops` log |
| **Ctrl+Z** | Undo last grid move |
| **Ctrl+Shift+Z** or **Ctrl+Y** | Redo undone grid move |
| **Alt+Z** | Toggle X-ray mode |
| **F11** | Toggle fullscreen |
| **Scissors slider** | Move cut plane — grids recompute at new elevation |
| **Save cut** | Save current section as named view |
| **F5 / reload** | Grid positions restored from log — no save button needed |

---

## Views and What They Show

### Floor Plan (GF / L1)

Clicking **GF** or **L1** locks the camera to a top-down orthographic view and fires a horizontal section cut through the building:

- **Wall contours** — closed polylines from triangle mesh slicing at the cut elevation
- **Door swing arcs** — quarter-circle arcs computed from door leaf bbox + nearest wall hinge
- **Window dashes** — jamb tick marks at each window opening
- **Opening labels** — width in mm (`900W`) + type tag (`Single-Flush`) above each door/window
- **Dim chains** — bay widths between grid lines, total span per axis
- **Grid bubbles** — labelled circles (1, 2, A, B) at line endpoints

The cut height is storey-aware: it finds the floor slab top and cuts 1.0 m above it (configurable in `grid_rules.json → floor_plan.cut_offset_m`).

### Elevation (Front / Back / Left / Right)

Locks camera to face the building from that direction. Elevation contours project visible edges. Level markers show storey heights.

### Scissors (Section Cut panel)

The slider moves a cut plane through the building on any axis (X, Y, Z). Grids recompute adaptively at the new elevation. This is a free-form exploration tool — door arcs and opening labels only appear in the named views (GF, L1), not in scissors mode.

---

## Grid Drag and the Cost Panel

When you long-press and drag a grid line:

1. **3D planes** appear — semi-transparent red (X-axis) and blue (Y-axis) rectangles slicing through the building
2. The grid line moves, constrained by `grid_rules.json` (min bay width, snap quantum, max step)
3. On release, a **GRID_MOVE** operation is committed to the `kernel_ops` table in the building's SQLite DB
4. The **cost panel** appears (bottom-right) showing elements within the grid scope:
   - Grouped by IFC class (Wall, Column, Door, Window, Slab...)
   - Quantities, area (m2), volume (m3) per class
   - Click **x** to close the panel

The cost panel query:

```sql
SELECT ifc_class, COUNT(*), SUM(bbox_x * bbox_y), SUM(bbox_x * bbox_y * bbox_z)
FROM elements_meta JOIN element_transforms ON guid
WHERE center_x BETWEEN gridX1 AND gridX2
  AND center_y BETWEEN gridY1 AND gridY2
GROUP BY ifc_class
```

This is a spatial BOQ — the bill of quantities scoped to the area between grid lines.

---

## Persistence — No Save Button

Every grid drag commits a `GRID_MOVE` row to the `kernel_ops` table in the building's SQLite DB. On page reload, the grid overlay replays all non-undone GRID_MOVE ops and repositions grid lines to where you left them.

- **Undo** marks the op as `undone=1` (soft delete, not hard delete — full audit trail preserved)
- **Redo** clears the `undone` flag
- The DB is the save. There is no save button because every operation is already saved.

See [BIM Modeller OOTB — The Modelling Inversion](BIM_Modeller_OOTB.md#the-modelling-inversion) for the architectural reasoning.

---

## Configuration — grid_rules.json

All constants are externalized in `grid_rules.json`, never hardcoded:

| Block | Controls |
|-------|----------|
| `grid_move` | `min_bay_m`, `max_extend_m`, `snap_m` (50mm default), `max_step_m` |
| `grid_detection` | `min_structural_span_m`, `face_cluster_tol_m`, `min_votes`, structural/opportunity classes |
| `floor_plan` | `cut_offset_m`, storey band filter, `opening_label_offset_m`, `opening_label_font_px` |
| `plane_3d` | `plane_opacity`, `plane_color_x` (red), `plane_color_y` (blue), `show_on_drag` |
| `clearance` | Per-class rules for cascade: `wall_min_m`, `grid_min_m`, strategy (proportional/pin_to_wall/center_bay) |
| `shadow` | Drag shadow colour and opacity |

---

## Known Limitations (Current Session)

| Issue | Status | Next step |
|-------|--------|-----------|
| Grid alignment on complex buildings (SampleCastle, HITOS) | Grid lines don't always align with structural walls | Tune opportunity-vote weights; add snap-to-wall post-processing |
| Saved section restore | Section appears in panel but clicking it back doesn't restore view properly | Debug `restoreSavedSection()` path |
| 2D convention parser | Scissors mode shows raw contours — no door arcs, no noise filtering | Apply 2D conventions (arcs, line weights, class filtering) to all section views, not just GF/L1 |
| Drag result as new building | The cost panel shows what changed but doesn't save as a variant building | Future: `commitOp('VARIANT')` creates a fork of the building DB |

---

## Console Log Tags (Debugging)

Open browser console (F12) and look for `§`-tagged lines:

| Tag | What it proves |
|-----|---------------|
| `§KERNEL_OP committed id=N type=T` | Operation logged to DB |
| `§KERNEL_OP undo id=N` | Undo succeeded |
| `§KERNEL_OP replay moves=N` | Init replayed N saved ops |
| `§GRID_3D_PLANES count=N` | 3D planes injected |
| `§GRID_3D_BOQ elements=N` | Cost panel refreshed |
| `§GRID_3D_BAND_VIS shown=N hidden=N` | Storey band visibility applied |
| `§DOOR_ARC_LABEL guid=G width=Nmm` | Opening label generated |
| `§GRID_DETECT xLines=N yLines=M` | Grid detection ran |
| `§GRID_UNDO attempt op=T` | Ctrl+Z fired |

---

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
