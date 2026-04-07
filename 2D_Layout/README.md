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
3. RUN + READ LOGS  → Generate output (--proof), read diagnostic log + TBLKLTN audit
4. FIX CODE         → Make failing tests pass; if spec needs updating, update spec FIRST
5. VERIFY           → Run all 3 tests + --proof; open proof SVG in browser to confirm
6. BACK TO 1        → Next feature: read spec, write test, run, fix, verify
```

**The proof SVG shows exactly what the DXF contains.** If the proof SVG is missing
a feature, the DXF is missing it. No separate code path. One source of truth.

```bash
# Full cycle:
python3 drawing_writer_dxf.py ../lib/input/ifc4_sample_house.db --all --proof
python3 test_no_hardcode.py && python3 test_no_invention.py && python3 test_dxf_vs_svg.py
# Open *_proof.svg in browser to visually verify
```

---

## Quick Start

```bash
cd 2D_Layout/python

# Generate all drawings for SampleHouse — SVG only (browser preview)
python3 drawing_writer.py ../lib/input/ifc4_sample_house.db --all

# Generate all drawings — SVG + DXF (professional CAD format)
python3 drawing_writer.py ../lib/input/ifc4_sample_house.db --all --dxf
```

Outputs land in `2D_Layout/python/output/`. Open `.svg` in any browser, `.dxf` in FreeCAD, QCAD, AutoCAD, or LibreCAD.

---

## What Gets Generated

| File | Format | What it shows |
|------|--------|--------------|
| `*_floor_plan.svg/dxf` | Floor plan | Horizontal section at 1.0m above floor — walls, doors, windows, furniture |
| `*_front_elevation.svg/dxf` | Front elevation | South face of building |
| `*_rear_elevation.svg/dxf` | Rear elevation | North face (mirrored left-right vs front) |
| `*_left_elevation.svg/dxf` | Left elevation | West face |
| `*_right_elevation.svg/dxf` | Right elevation | East face (mirrored vs left) |
| `*_roof_plan.svg` | Roof plan | Top-down view with ridge, slope arrows, overhangs |
| `*_frontrear_elevations.svg` | Paired sheet | Front + Rear stacked on one A3 sheet |
| `*_leftright_elevations.svg` | Paired sheet | Left + Right stacked on one A3 sheet |

---

## Command Reference

```
python3 drawing_writer.py <database.db> [options]

Options:
  --floor-plan          Floor plan only
  --roof-plan           Roof plan only
  --elevation FACE      One elevation: front | rear | left | right
  --all                 All drawings (floor plan + 4 elevations + roof plan)
  --dxf                 Also write DXF files alongside SVGs

Examples:
  python3 drawing_writer.py samplehouse.db --all
  python3 drawing_writer.py samplehouse.db --all --dxf
  python3 drawing_writer.py samplehouse.db --elevation front
  python3 drawing_writer.py samplehouse.db --elevation front --dxf
  python3 drawing_writer.py samplehouse.db --floor-plan --dxf
```

Or use the DXF writer directly (same options, DXF only):
```
python3 drawing_writer_dxf.py <database.db> --all
```

---

## Input: Where Does the Database Come From?

The `.db` file is produced by the BIM compiler pipeline:

```
IFC file  →  DAGCompiler  →  output.db  →  drawing_writer.py  →  SVG / DXF
```

Pre-compiled sample databases are in `lib/input/`:
- `ifc4_sample_house.db` — 1-storey residential (SampleHouse IFC reference model)

To compile a new IFC file, see `docs/IFC_ONBOARDING_RUNBOOK.md` in the project root.

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

## File Structure

```
2D_Layout/
├── python/
│   ├── drawing_writer.py       ← main script (SVG + optional DXF via --dxf)
│   ├── drawing_writer_dxf.py   ← DXF exporter (ezdxf, AIA layers, model-space mm)
│   ├── section_cut.py          ← mesh-plane intersection engine
│   └── output/                 ← generated SVG and DXF files
├── lib/
│   └── input/                  ← compiled .db files go here
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
