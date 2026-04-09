# 2D Layout — Architectural Drawing Generator

Generates professional 2D architectural drawings (floor plans, elevations, roof plan)
from a compiled BIM database. Outputs DXF (professional deliverable) with DXF→SVG
proof output for browser verification.

---

## Development Protocol

Every coding session follows this cycle. No exceptions.

```
1. READ SPECS       → 2D_ARCHITECTURAL_LAYOUT.md — understand what the code must do
2. WRITE TESTS      → Write failing test assertions BEFORE writing code
3. RUN + READ LOGS  → Generate output (--all), read diagnostic log + conformity
4. FIX CODE         → Make failing tests pass; if spec needs updating, update spec FIRST
5. VERIFY           → Run all 3 validators; open proof SVG in browser to confirm
6. BACK TO 1        → Next feature: read spec, write test, run, fix, verify
```

**The proof SVG shows exactly what the DXF contains.** `--all` generates DXF + SVG
proof automatically. If the proof SVG is missing a feature, the DXF is missing it.

```bash
# Full cycle:
cd 2D_Layout/python
python3 drawing_writer_dxf.py ../input/SH_extracted.db --all
python3 layout_audit.py          # layout quality: 0 FAIL required
python3 test_conformity.py       # sheet furniture: exit 0 required
python3 test_2d_bim_roundtrip.py ../input/SH_extracted.db  # semantic: 4/4 PASS
# Open output/SVG/*.svg in browser to visually verify
```

---

## Quick Start

```bash
cd 2D_Layout/python

# Generate all drawings for SampleHouse (DXF + SVG proof + per-view logs)
python3 drawing_writer_dxf.py ../input/SH_extracted.db --all

# Generate all drawings for Duplex
python3 drawing_writer_dxf.py ../input/DX_extracted.db --all

# Generate single view (DXF only; add --proof for SVG)
python3 drawing_writer_dxf.py ../input/SH_extracted.db --floor-plan --proof
```

Output:
```
output/DXF/{PREFIX}_{VIEW}_{ts}.dxf        — professional deliverable (CAD)
output/SVG/{PREFIX}_{VIEW}_{ts}.svg        — proof render (open in browser)
output/DXF/log_{PREFIX}_{VIEW}_{ts}.txt    — per-view diagnostic log
output/dxf_diagnostic.txt                  — consolidated session log
output/conformity_log.txt                  — conformity gate results
output/layout_audit.txt                    — layout quality audit
```

Open `.svg` in any browser, `.dxf` in FreeCAD, QCAD, AutoCAD, or LibreCAD.

---

## What Gets Generated

| Sheet | View | DXF + SVG |
|-------|------|-----------|
| A-01 | Floor plan | Horizontal section at 1.2m — walls, doors, windows, furniture, room labels |
| A-02 | Front elevation | South face — wall outlines, roof silhouette, level markers |
| A-03 | Rear elevation | North face (mirrored) |
| A-04 | Left elevation | West face |
| A-05 | Right elevation | East face (mirrored) |
| A-06 | Roof plan | Top-down — ridge, slope arrows, overhangs, eave outline |
| E-01 | Electrical plan | Floor plan + electrical terminal symbols + legend |
| M-01 | Plumbing layout | Floor plan + plumbing terminals + pipe segments + legend |

Pages are driven by `input/{PREFIX}_2D.json`. Only `status=DONE` pages are generated.

---

## Command Reference

```
python3 drawing_writer_dxf.py <database.db> [options]

Options:
  --floor-plan          Floor plan only (DXF)
  --roof-plan           Roof plan only (DXF)
  --elevation FACE      One elevation: front | rear | left | right
  --all                 All pages from {PREFIX}_2D.json (DXF + SVG proof)
  --proof               Generate SVG proof (implied by --all)
  --scale N             Drawing scale denominator (default 100 = 1:100)

Examples:
  python3 drawing_writer_dxf.py ../input/SH_extracted.db --all
  python3 drawing_writer_dxf.py ../input/DX_extracted.db --all
  python3 drawing_writer_dxf.py ../input/SH_extracted.db --floor-plan --proof
  python3 drawing_writer_dxf.py ../input/SH_extracted.db --elevation front
```

---

## Input: Where Does the Database Come From?

The `.db` file is produced by the BIM compiler pipeline:

```
IFC file  →  extractIFCtoDB.py  →  {PREFIX}_extracted.db  →  drawing_writer_dxf.py  →  DXF + SVG
```

Extracted databases go in `input/`:
- `SH_extracted.db` — SampleHouse (IFC4 1-storey residential)
- `DX_extracted.db` — Duplex (IFC4 2-storey residential)

To extract a new IFC file, see `docs/IFC_ONBOARDING_RUNBOOK.md` in the project root.

---

## What Each Drawing Contains

### Floor Plan
- **CUT elements** (walls, columns, doors, windows crossing the cut plane at Z=1.0m):
  drawn with heavy lines showing the section outline
- **BELOW elements** (furniture, floor slabs, low features): drawn as light grey
  bounding boxes — they're projected from above
- **Grid lines** with bubble labels (A, B, C... horizontally; 1, 2, 3... vertically)
- **Bay dimensions** — spacing between grid lines in mm
- **Room labels** — name and area at the centre of each room

### Elevations
- **Wall outlines** — exterior face of the building
- **Roof silhouette** — from actual mesh vertices via convex hull projection,
  with vertical line hatching (metal roofing sheets)
- **Window louvre lines** — horizontal stripes inside each window opening
- **Level markers** (left side, triangle pointer):
  - GRD. FLOOR LEVEL (+0.000)
  - BEAM/CEILING LEVEL (+2.300)
  - RIDGE LEVEL (+3.500)
  - APRON LEVEL (−0.100) — concrete apron around building base
  - GRD. LEVEL (−0.250) — natural ground
- **Grid lines + bubbles** at top
- **Bay dimensions** above grid bubbles
- **Height dimensions** (right side) — floor-to-ceiling, ceiling-to-ridge

**Mirror rule:** Rear elevation is the mirror of Front (viewer is on the other side).
Right elevation is the mirror of Left. The roof tilt will look different between
paired elevations if the building is asymmetric — this is correct, not a bug.

### Roof Plan
- Roof eave outline (bold)
- Ridge line (dashed, labelled RABUNG / RIDGE)
- Slope direction arrows (north and south slopes)
- Overhang dimensions (distance from wall to eave)
- Building footprint (dashed, light)

---

## DXF Format Details

DXF files use AIA standard layer names so they open cleanly in any CAD tool:

| Layer | Contents |
|-------|----------|
| `A-WALL-FULL` | Exterior cut wall outlines |
| `A-WALL-PRTN` | Partition / interior wall outlines |
| `A-GLAZ` | Glazing, curtain wall, windows |
| `A-DOOR` | Door outlines |
| `A-ROOF` | Roof silhouette |
| `A-GRID` | Reference grid lines and circles |
| `A-ANNO-DIMS` | Dimension strings |
| `A-ANNO-TEXT` | Labels, level names, grid labels |
| `A-FURN` | Furniture / below-cut elements |
| `A-ELEV-WALL` | Elevation wall outlines |
| `A-ELEV-LEVL` | Level marker lines and triangles |

Coordinates are in **model-space millimetres** (true 1:1 scale, not scaled to paper).
The header declares `$INSUNITS=4` (mm) so any CAD tool reads units correctly.

Line weights follow ISO 128: ground line 0.70mm, walls 0.50mm, partitions 0.35mm,
grid/dims 0.18mm (hairline).

---

## Opening DXF Files

**FreeCAD** (free, Linux/Windows/Mac):
```
File → Open → select .dxf
```
Turn layers on/off: View → Panels → Model tree → expand layers

**QCAD** (free community edition):
```
File → Open → select .dxf
```
Layer panel on left side.

**LibreCAD** (free):
```
File → Open → select .dxf
```

**AutoCAD / AutoCAD LT**: open directly, layers already named per AIA standard.

---

## Troubleshooting

**"No walls found"** — the database may be an extracted DB (Rosetta Stone), not a
compiled output.db. Make sure you're pointing at the compiler output, not the IFC extract.

**Tiny or huge SVG** — the SVG is in mm units. If your browser shows it too small,
zoom in or open in Inkscape (File → Document Properties → set display to 100%).

**DXF opens but no geometry** — check that the layer `A-WALL-FULL` is visible.
Some CAD tools freeze all layers except layer 0 on open.

**ezdxf not installed** (DXF skipped with warning):
```bash
pip3 install ezdxf
```

---

## Validation

Three independent validators must all pass before declaring DONE:

```bash
# 1. Layout quality — entity overlap, text bleed, grid count
python3 layout_audit.py                                   # 0 FAIL required

# 2. Conformity gate — sheet furniture per §5.0 (border, title block, grids, scale bar)
python3 test_conformity.py                                # exit 0 required

# 3. Semantic round-trip — BIMSRC xdata (wall/grid/dim/room provenance)
python3 test_2d_bim_roundtrip.py ../input/SH_extracted.db # 4/4 PASS required
```

## File Structure

```
2D_Layout/
├── python/
│   ├── drawing_writer_dxf.py   ← main DXF+SVG generator (run this)
│   ├── drawing_writer.py       ← shared: read_elements, derive_grids, infer_rooms
│   ├── section_cut.py          ← mesh-plane intersection engine
│   ├── layout_audit.py         ← layout quality checker
│   ├── test_conformity.py      ← conformity gate (§10.5)
│   └── test_2d_bim_roundtrip.py← semantic round-trip test
├── input/                      ← extracted .db files go here
├── output/
│   ├── DXF/                    ← generated DXF files + per-view logs
│   └── SVG/                    ← proof SVG renders
├── drawing_template.json       ← all formatting constants
├── docs/
│   └── 2D_ARCHITECTURAL_LAYOUT.md   ← full technical spec
└── README.md                   ← this file
```

---

## Standards Reference

- **ISO 128** — line weights (0.18 / 0.25 / 0.35 / 0.50 / 0.70 mm)
- **AIA CAD Layer Guidelines** — layer naming (A-WALL-FULL, A-GRID, A-ANNO-DIMS…)
- **TB-LKTN / JKR Malaysian** — drawing title format, dimension tick style (ARCHTICK)
- **DXF R2010** — output format; `$INSUNITS=4` (mm), `$LTSCALE=1.0` at 1:100

Full technical spec: `docs/2D_ARCHITECTURAL_LAYOUT.md`
