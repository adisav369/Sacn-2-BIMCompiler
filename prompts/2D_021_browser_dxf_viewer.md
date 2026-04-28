# ⚠ DO NOT REMOVE — MANDATORY PREAMBLE
# Scope: Browser-based 2D DXF viewer — render DXF in new tab, linked to 3D viewer
# After every run: read the log before any conclusion. Exit code is not evidence.
# STATUS: ACTIVE

# ⚠ BUILDINGS: Only SH (SampleHouse) and DX (Duplex) have 2D output. Do not attempt others.

# 2D_021 — Browser DXF Viewer

## Icon
Toolbar icon: `2D.png` (black-and-white blueprint floor plan, ~/Downloads/2D.png → deploy/dev/2D.png)
Placement: bottom-center toolbar strip in `index.html`, after the last existing icon.
Style: same as other toolbar buttons (22px, dark background, border, border-radius).
Tooltip: `"2D Plans"`
Click: `window.open('2d.html?db=' + encodeURIComponent(APP.dbUrl), '_blank')`

## Architecture — DXF is the data model

The Python pipeline (`2D_Layout/python/drawing_writer_dxf.py`) generates DXF files
with BIMSRC xdata (group code 1001) on every entity. The browser does NOT convert
DXF to SVG — it parses and renders DXF directly:

```
DXF file (BIMSRC xdata intact)
  → dxf-parser (JS, npm) → in-memory entity tree
  → Canvas2D renderer (lines, polylines, arcs, text, layers)
  → click hit-test → read BIMSRC xdata → GUID correlation
```

### BIMSRC xdata structure (proven via spike test)
```
applicationName: "BIMSRC"
customStrings: [
  "type:wall",
  "ifc_class:IfcMember",
  "element_name:Curtain Wall:Curtain_Wall-Exterior_Glazing",
  "storey:Unknown",
  "pos_x:-7.749"    ← encode floats as strings (parser drops 1040 group codes)
]
```

93/94 BIMSRC tags survive `dxf-parser` round-trip (tested 2026-04-28).

### Variant DB flow (the real power)

Editing in the 2D view is NOT live-sync to 3D. Instead:
1. User edits DXF entities in browser (drag wall, delete door, add partition)
2. Each entity carries BIMSRC → knows which DB GUID it maps to
3. Edits produce a **variant DB** (cloned from base, coordinates updated)
4. User clicks "Compare" → opens viewer with `?db=base&diffdb=variant`
5. Existing `diff.js` fires: green (added), red (removed), yellow (changed)
6. Existing `boq_charts.html` shows 5D cost impact + Variation Order Excel

This reuses the entire diff pipeline (S225) — zero new diff code needed.

### DXF round-trip with AutoCAD

DXF is the interchange format. BIMSRC xdata survives AutoCAD round-trip
(standard DXF behaviour — apps preserve xdata they don't understand).
Architect can: export DXF → edit in AutoCAD → re-import → diff pipeline fires.
The browser editor is one client; any CAD tool that preserves xdata is another.

## Python pipeline (reference implementation)

The 2D pipeline is pure Python, fully working (tested 2026-04-28):
- SampleHouse: 6/6 views PASS (floor, 4 elevations, roof)
- Duplex: 7/7 views PASS (2 floors, 4 elevations, roof, plumbing)

```bash
cd 2D_Layout/python
python3 drawing_writer_dxf.py ../input/SH_extracted.db --all
python3 drawing_writer_dxf.py ../input/DX_extracted.db --all
```

Output: `2D_Layout/output/DXF/*.dxf` + `2D_Layout/output/SVG/*.svg`

Template: `2D_Layout/drawing_template.json` (shared by Python and browser renderer)

## Implementation steps

### Step 1 — Toolbar icon + 2d.html shell
- Copy `2D.png` to `deploy/dev/`
- Add `<button>` to toolbar in `index.html` with `<img src="2D.png">` (22px)
- Create `deploy/dev/2d.html` — minimal shell: load DXF, parse, render to canvas
- DXF source: pre-generated files from Python pipeline (served statically)

### Step 2 — Canvas DXF renderer
- Parse DXF via `dxf-parser`
- Render entities by layer (A-WALL-FULL, A-GRID, A-ANNO-DIMS, etc.)
- Pan/zoom via mouse (wheel = zoom, drag = pan)
- Layer toggle panel (show/hide DXF layers)
- Line weights from DXF layer definitions

### Step 3 — BIMSRC click-to-select
- Hit-test on canvas click → find nearest entity → read BIMSRC xdata
- Show info panel: ifc_class, element_name, storey, GUID
- Highlight selected entity

### Step 4 — Linked selection (BroadcastChannel)
- Click entity in 2D → post `{type:'select', guid}` → 3D tab highlights it
- Click element in 3D → post to 2D tab → highlight corresponding DXF entity
- Join key: GUID from BIMSRC xdata = GUID in elements_meta

### Step 5 — Edit + variant DB
- Drag entities → update DXF coordinates in memory
- Clone base DB → write deltas → variant DB (IndexedDB)
- "Compare" button → `window.open('index.html?db=base&diffdb=variant')`
- Diff pipeline fires automatically

### Step 6 — DXF export
- Serialize in-memory DXF back to file (preserve BIMSRC xdata)
- Download button: user gets edited DXF for AutoCAD/BricsCAD

## Key files
- `deploy/dev/index.html` — add toolbar button
- `deploy/dev/2d.html` — new: DXF viewer page
- `deploy/dev/2D.png` — toolbar icon
- `2D_Layout/python/drawing_writer_dxf.py` — Python pipeline (reference, 228KB)
- `2D_Layout/drawing_template.json` — shared template (line weights, colors, grid styles)
- `deploy/dev/diff.js` — existing diff pipeline (reused for variant comparison)
- `deploy/dev/boq_charts.html` — existing 5D charts (reused for cost impact)
- `node_modules/dxf-parser/` — JS DXF parser (installed, xdata preservation proven)

## Spec references
- `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §20 (BIMSRC xdata)
- `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §22 (Interactive Browser Editor — extended by this prompt)
- `docs/BIM_Designer_Browser.md` (viewer architecture)
