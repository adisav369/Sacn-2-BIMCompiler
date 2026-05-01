# 2D Plans Viewer — User Guide

> **Related:** [BIM Designer Guide](BIM_Designer_UserGuide.md) · [BIM Designer Browser](BIM_Designer_Browser.md) · [2D Layout Spec](2D_LAYOUT.md) · [SQLite3D Schema](SQLite3D_Schema.md)

**Version:** 0.1 (2026-04-30)
**Status:** POC — Sample House and Duplex fully proven; large buildings auto-clipped
**File:** `deploy/dev/2d.html`

---

## 1. Overview

The 2D Plans viewer (`2d.html`) is a browser-native floor plan and elevation viewer that requires no installation, no server, and no conversion step. It reads directly from the same pair of SQLite databases that power the BIM OOTB 3D viewer, generates drawing entities on-the-fly using Canvas2D, and renders them in your browser tab. The underlying technology is **sql.js** (SQLite compiled to WebAssembly) for database queries and the HTML5 **Canvas2D API** for 2D rendering — the same architecture as the 3D viewer, without the GPU. Drag-and-drop of external `.dxf` files is also supported for teams working with existing CAD deliverables.

---

## 2. Two Ways to Open

### 2a. From the BIM OOTB Viewer (recommended)

This is the zero-configuration path. The 3D viewer passes the correct database paths automatically.

1. Open the BIM OOTB 3D viewer and load a building.
2. Click the **2D Plans** button in the viewer toolbar.
3. `2d.html` opens in a new browser tab with `?db=`, `?lib=`, and `?bld=` parameters wired from the active building — no manual URL editing required.

### 2b. Direct URL

Append URL parameters to the `2d.html` address to load a specific building directly.

| Param | Required | Example | Purpose |
|-------|----------|---------|---------|
| `db` | Yes | `?db=/buildings/SampleHouse_extracted.db` | Main element database (geometry + IFC data) |
| `lib` | No | `&lib=/buildings/SampleHouse_library.db` | Geometry mesh database |
| `bld` | No | `&bld=SH` | Building prefix used for title block label |

!!! note
    Without `?db=`, the viewer starts in **DXF file mode** and renders the built-in Sample House (SH) baseline. The SH and DX baselines are embedded inline in the HTML — they render without any network fetch.

---

## 3. Toolbar Controls

The toolbar runs across the top of the window. Controls that are irrelevant to the current mode are hidden automatically (for example, the Sheet dropdown is hidden in DB mode; the SH/DX building buttons are hidden once a `?db=` param is present).

| Control | What it does |
|---------|-------------|
| **SH / DX buttons** | Switch between Sample House and Duplex in DXF file mode. Inactive in DB mode. |
| **Sheet dropdown** | Select a DXF sheet file (Floor Plan, Front Elev, Roof Plan, etc.). Visible in DXF file mode only. |
| **Storey dropdown** | Select a floor level (Ground Floor, Level 1, Level 2, …). Populated from the database in DB mode. |
| **Mode dropdown** | Switch view type: Floor Plan, Front Elev, Rear Elev, Left Elev, Right Elev. |
| **Generate** | Re-render the drawing with the current storey and mode selection. |
| **Fit** | Zoom to fit the entire drawing within the canvas. |
| **Layers** | Open or close the layer toggle panel. |
| **BIMSRC** | Activate element origin highlighting. Click any drawn element to see its IFC data in the info bar. |
| **Export DXF** | Download the current view as a `.dxf` file compatible with AutoCAD and other CAD tools. |
| **Drag and drop** | Drop a `.dxf` file onto the canvas from your file manager to load it directly. |

The status bar at the bottom shows the entity count, active layer count, and — when BIMSRC is on — the IFC class, element name, and storey of the last clicked element.

---

## 4. Layer Panel

Click **Layers** to open the layer panel. Each layer can be shown or hidden individually by toggling its checkbox. The drawing re-renders immediately.

The viewer uses the 12-layer AIA subset standard for architectural drawings, plus annotation, title block, and section layers:

| Layer | Colour | Description |
|-------|--------|-------------|
| `A-WALL-FULL` | White | Structural walls — the primary building outline |
| `A-WALL-PRTN` | Light grey | Partition (non-structural) walls |
| `A-WALL-CORE` | Mid grey | Core and shear walls |
| `A-WALL-PATT` | Medium grey | Solid hatch fill behind wall cross-sections |
| `A-GLAZ` | Blue | Glazing and curtain wall |
| `A-DOOR` | Cyan tint | Door swings and frames |
| `A-COLM` | Near-white | Columns |
| `A-STAIR` | Blue-grey | Stair runs and risers |
| `A-RAIL` | Muted grey-green | Railings and balustrades |
| `A-SLAB` | Grey | Slabs and floors (shown in section) |
| `A-ROOF` | Light grey | Roof outline |
| `A-FURN` | Dark blue-grey | Furniture (recedes visually) |
| `A-GRID` | Muted blue-grey | Grid lines |
| `A-ANNO-DIMS` | Blue-grey | Dimension lines and bay widths |
| `A-ANNO-TEXT` | Blue-grey | Annotation and room labels |
| `A-ANNO-SECT` | Cyan-blue | Section cut markers |
| `A-TTLB` | Steel blue | Title block border |

!!! tip
    `A-WALL-PATT` is the solid hatch fill drawn behind wall outlines. Hiding it gives a clean line-only view; showing it makes the wall thickness immediately legible.

---

## 5. Click-to-Info (BIMSRC)

When **BIMSRC** is active (button highlighted), the cursor identifies drawn elements by their source IFC data.

- Click any wall, door, column, or window on the canvas.
- The info bar at the bottom shows: **IFC class**, **element name**, and **storey**.
- The clicked element is highlighted in a contrasting colour.

This traces every drawn entity back to the source IFC file — useful for cross-checking drawing content against the BIM model during design review or QA.

!!! note
    BIMSRC is only meaningful in **DB mode** (`?db=` param present). In DXF file mode, the entities come from pre-rendered CAD geometry rather than the live database, so IFC attribution is not available.

---

## 6. Annotation Config Template

For large buildings (Hospital, Terminal), the viewer automatically suppresses furniture detail beyond the first few room clusters to prevent clutter. This density is controlled by a JSON config stored in your browser's `localStorage`.

**Key:** `bim2d_anno_config`

**Loading order:** `localStorage` (your edits) → remote OCI config (team defaults) → built-in defaults. No installation is needed — the defaults work out of the box.

### Editing the config

1. Open browser DevTools (`F12`).
2. Go to **Application** (Chrome) or **Storage** (Firefox).
3. Select **Local Storage** → the page origin.
4. Find or create the key `bim2d_anno_config`.
5. Paste your edited JSON as the value.
6. Reload the page — the new config takes effect immediately.

### Default config

```json
{
  "_comment": "BIM OOTB 2D annotation density config. Edit in DevTools > Application > Local Storage > bim2d_anno_config",
  "furnDetailRooms": 1,
  "maxFurnPerRoom": 12,
  "seatingThreshold": 8,
  "seatingPattern": "chair|seat|bench|stall|auditorium|theatre|stadium",
  "hallAreaThresholdM2": 50,
  "maxDoorTags": 20,
  "maxWindowTags": 20
}
```

### Field reference

| Field | Default | Meaning |
|-------|---------|---------|
| `furnDetailRooms` | `1` | How many room clusters show individual furniture outlines. Rooms beyond this threshold get a label only — no furniture geometry. |
| `maxFurnPerRoom` | `12` | Maximum furniture items drawn per detailed room. Excess items are suppressed. |
| `seatingThreshold` | `8` | If a cluster contains this many seating elements or more, it is treated as an assembly hall rather than a regular room. |
| `seatingPattern` | (regex) | IFC element name patterns that count as seating. Uses a pipe-separated regular expression. |
| `hallAreaThresholdM2` | `50` | Clusters larger than this area (m²) that also meet the seating threshold are drawn as a single aggregate bounding box. |
| `maxDoorTags` | `20` | Cap on D-tag door annotations. POC limit for large buildings. |
| `maxWindowTags` | `20` | Cap on W-tag window annotations. POC limit for large buildings. |

!!! tip
    To reset to built-in defaults, delete the `bim2d_anno_config` key in DevTools and reload the page.

---

## 7. What Is Generated vs What Comes from DXF

The viewer operates in two distinct modes. Understanding which mode is active explains what you are looking at and what accuracy to expect.

|  | Static DXF | Dynamic (from DB) |
|--|-----------|------------------|
| **Source** | Pre-generated `.dxf` files in `dxf/` folder | `extracted.db` + `library.db` queried live |
| **Layers** | 12 AIA layers | Same 12 AIA layers |
| **Accuracy** | Pixel-perfect production output from the Java pipeline | POC — large buildings auto-clipped to a 30 m centred window |
| **Annotation** | Full spec (all annotation layers) | BIMSRC-tagged walls, doors, windows + furniture + room labels |
| **DXF export** | Yes | Yes |

Static DXF files are produced by the Java `DAGCompiler` pipeline, which runs all geometry processing server-side and writes fully-annotated DXF sheets. Dynamic generation happens in the browser from raw BLOBs and is suitable for interactive review and cross-checking against the live model.

---

## 8. Known Limitations (POC)

!!! note "POC Scope"
    All generated features listed in §25.6 of the [2D Layout Spec](2D_LAYOUT.md) are implemented. Features marked ✗ in that table are deferred to a later sprint.

- **Large building clipping:** Floors larger than approximately 30 m × 30 m are clipped to the building centre. Controlled by `SectionCut.CLIP_MARGIN = 15 m`.
- **Hatch patterns:** Solid fill only (`A-WALL-PATT`). ANSI hatch patterns (brick, concrete, insulation) are deferred to §23.2.
- **Section line:** One auto-generated A-A section line is placed by the pipeline. User-positioned section cuts are deferred.
- **Elevation views:** Structural edges only. Material callouts and finish annotations are deferred.
- **Title block, north arrow, scale bar:** Implemented in the Java pipeline DXF output. Dynamic DB-mode generation is deferred.

---

## 9. Code Structure — For Developers

`deploy/dev/2d.html` is self-contained (~48 000 lines). The rendering pipeline inside it follows a strict layered architecture:

```
URL params / drag-drop
        ↓
  parseDxf()          ← DXF file mode (static baselines)
        OR
  generateFromDb()    ← DB mode (live section cut)
        ↓
  sectionToEntities() + annotationEntities()   ← entity builders
        ↓
  entities[]          ← shared in-memory array
        ↓
  draw() → drawEntity() → drawLine/drawPolyline/drawCircle/drawText/…
```

Key functions and where to find them (by section comment in the source):

| Function | Purpose | Modify to… |
|----------|---------|-----------|
| `getAnnoConfig()` | Loads density config from localStorage → OCI → defaults | Add new config fields |
| `annotationEntities(elements, cfg)` | Furniture outlines, room labels, D/W tags, section marker | Add new annotation types |
| `sectionToEntities(elements)` | Wall/door/window section cut contours + hatch fills | Change how CUT elements are styled |
| `generateFromDb()` | Orchestrates DB load → section cut → grid → title block → annotations | Add new generation stages |
| `drawEntity(e)` | Routes each entity to its draw function by `e.type` | Support new entity types |
| `drawPolyline(e)` | Renders LWPOLYLINE; respects `e.fill` and `e.fillAlpha` | Adjust fill rendering |
| `layerColor(layer)` | Returns hex colour per AIA layer | Change layer colours |
| `fitView()` | Auto-zooms canvas to `window._structBbox` | Adjust zoom margins |

**External JS modules** (loaded as `<script src="…">` before the inline scripts):

| Module | File | Role |
|--------|------|------|
| `SectionCut` | `section_cut.js` | Mesh slicer — produces `{guid, category, contours, center}` per element |
| `GridDims` | `grid_dims.js` | Column grid detection and bay dimension rendering |
| `TitleBlock` | `title_block.js` | Title block, scale bar, north arrow |
| `Elevation` | `elevation.js` | Front/rear/left/right elevation edge extraction |
| `DxfExport` | `dxf_export.js` | Serialises `entities[]` to DXF text for download |

**Adding a new annotation type:**

1. Implement in `annotationEntities()` — push entities to `out[]` with the correct `layer` and `type`.
2. Add a `§RENDER …` or `§AUDIT …` `console.log` for whitebox verification.
3. Add a `LAYER_COLORS` entry if introducing a new layer.
4. Add a Playwright whitebox test in `14-2d-plans.spec.js` that reads `§`-tagged console logs.
5. Run `node deploy/dev/tests/audit_specs.js` — must exit 0.

**Testing hierarchy** (from `docs/TestArchitecture.md`):
- Primary: `§`-tagged `console.log` lines — the coder reads these to confirm counts and values.
- Secondary: Playwright — for wiring, deploy, and regression checks only.

---

*See also: [2D Layout Specification](2D_LAYOUT.md) · [BIM Designer Browser Guide](BIM_Designer_Browser.md) · [SQLite3D Schema](SQLite3D_Schema.md)*

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
