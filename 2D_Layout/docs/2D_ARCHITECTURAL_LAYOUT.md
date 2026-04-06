# 2D Architectural Layout — Research Reference

## 1. What is a 2D Architectural Layout

A 2D architectural layout is a set of **orthographic views** derived from a 3D building model. Each drawing is a specific way of looking at the same 3D construct:

- **Floor Plan** — A horizontal section cut through the building at ~1.0-1.2m above each floor level, viewed from above. Elements cut by the plane are drawn with heavy lines. Elements below are drawn lighter. Elements above are omitted or dashed.

- **Elevation** — An orthographic projection of one face of the building onto a vertical plane. No perspective distortion. Shows the exterior appearance: wall surfaces, windows, doors, roof profile, ground line.

- **Section** — A vertical section cut through the building, viewed from one side. Shows internal structure: wall composition, floor slabs, ceiling, roof construction. Cut elements are hatched to indicate material.

- **Roof Plan** — A view looking straight down at the roof. Shows roof outline, ridge lines, valleys, slope direction arrows, gutters, downpipes.

All views are derived from the SAME 3D model. The 3D model is the single source of truth. The 2D drawings are just ways of viewing it.

## 2. How Each Floor is Shown Separately

Each storey gets its own floor plan drawing. The process:

1. Identify the floor levels from the spatial structure (IfcBuildingStorey)
2. For each storey, define a horizontal cut plane at 1.0-1.2m above the storey's finished floor level
3. Everything the plane cuts through → heavy lines (walls, columns cut in section)
4. Everything below the plane → lighter lines (furniture, floor finish, low walls)
5. Everything above the plane → omitted or shown dashed (overhead beams, ductwork)

For a multi-storey building:
- Ground Floor Plan: cut at Z = 0.0 + 1.0 = 1.0m
- First Floor Plan: cut at Z = 3.0 + 1.0 = 4.0m (if storey height is 3.0m)
- And so on for each level

Each plan is a separate drawing sheet, with its own title, scale, grid, dimensions.

## 3. The 3D-to-2D Relationship

A floor plan is NOT a simplified sketch of the building. It is literally the **intersection** of a horizontal plane with the 3D geometry:

- A wall mesh with window openings cut into it, when sliced at Z=1.0m (between sill at 0.9m and head at 2.1m), produces line segments showing the wall outline WITH gaps where the windows are. No manual gap-cutting needed.
- A solid partition wall (no openings) produces a continuous rectangular outline showing wall thickness.
- A door opening (if the door geometry extends from floor to 2.1m) shows as a gap in the wall at the cut plane.
- Furniture below the cut height (beds at 0.48m, coffee tables at 0.45m) → projected from above, drawn with lighter lines.
- Furniture crossing the cut height (dining chairs at 1.2m) → cut outline drawn.

The wall meshes in the compiled database (`base_geometries`) already have opening voids modeled. A north wall with 3 windows has 49 vertices (not 8 for a simple box) because the window openings create additional geometry. Slicing this mesh at Z=1.0m automatically produces the correct floor plan outline.

## 4. Annotations are Calculated Overlays

Grid lines, dimensions, room labels, and other annotations do NOT exist as 3D objects. They are calculated from the 3D data and overlaid on the view:

**Grid Lines:**
- Represent the structural reference system of the building
- Calculated from centerlines of structural elements (columns, load-bearing walls)
- They are a reading OF the building, not imposed ON it
- Drawn as dash-dot lines extending beyond the building outline
- Labeled with letters (A, B, C) on one axis, numbers (1, 2, 3) on the other
- Grid bubbles (circles with labels) at each end

**Dimensions:**
- Measured from the 3D element positions
- Chains: bay dimensions (between grids), opening widths, room internal dims, overall
- Drawn with extension lines, dimension lines, and terminators (tick marks for JKR, arrows for ISO)

**Room Labels:**
- Room name, area (m²), and finish code placed at center of each room
- Room boundaries inferred from wall topology in the plan view

**North Arrow:**
- Indicates orientation, placed in margin

**Title Block:**
- Drawing metadata (project name, drawing title, scale, date, drafter)
- Per TB-LKTN/JKR standard: PROJEK, ARKITEK, TAJUK LUKISAN, NO. LUKISAN, etc.

## 5. The Mesh Section Cut Approach

The compiled database stores triangle meshes in `base_geometries` (vertices BLOB + faces BLOB). The approach to generate a 2D floor plan:

**Step 1: Classify elements by relationship to cut plane**

For each element, check its Z range against the cut height (e.g., Z=1.0m):
- **CUT** (minZ < 1.0 < maxZ): Element crosses the section plane → draw section outline (heavy)
- **BELOW** (maxZ <= 1.0): Element entirely below → project top face (light)
- **ABOVE** (minZ >= 1.0): Element entirely above → omit or draw dashed

Element classification from SampleHouse at Z=1.0m:
| Category | Elements |
|----------|----------|
| CUT | IfcWall (5), IfcDoor (3), IfcWindow (4), IfcMember mullions, IfcPlate glass |
| BELOW | bed, coffee_table, sofa, lounge_chairs, workstation_desk, ground slab |
| ABOVE | roof, roof slab, some curtain wall headers |

**Step 2: Slice CUT elements**

For each CUT element, load its triangle mesh and compute the intersection with the horizontal plane at Z=cut_height:

```
For each triangle (v0, v1, v2):
    Count vertices above and below the plane
    If all above or all below → skip (no intersection)
    If mixed → compute intersection points on the edges that cross the plane
    Each crossing triangle produces one 2D line segment (two intersection points)
```

The intersection of an edge (va, vb) with plane Z=cut_height:
```
t = (cut_height - va.z) / (vb.z - va.z)
point_x = va.x + t * (vb.x - va.x)
point_y = va.y + t * (vb.y - va.y)
```

**Step 3: Chain segments into contours**

The raw segments from step 2 are unordered. Chain them into closed polygons:
- Match segment endpoints within a small tolerance
- Each closed chain = one contour (wall outline, opening boundary)

For walls, this produces TWO closed contours: the outer face and the inner face, with gaps where openings exist.

**Step 4: Draw with appropriate styling**

Use `ifc_class` and `element_name` from `elements_meta` to determine line weight and style:
- IfcWall with "Ext" in name → heavy exterior wall line (0.5mm)
- IfcWall with "Partn" in name → medium partition line (0.35mm)
- IfcPlate with "Glazed" → thin glass line in blue (0.18mm)
- IfcMember (curtain wall mullion) → thin structural line (0.18mm)

**Step 5: Project BELOW elements**

For elements entirely below the cut plane (furniture, floor slab):
- Use bounding box projection onto XY (light grey outline)
- Or for more detail: project the top-face triangles of the mesh

**Step 6: Add annotations**

- Grid lines: compute from wall/column centerlines
- Dimensions: compute from element bounding boxes
- Room labels: infer from enclosed wall regions
- North arrow, title block, scale bar

## 6. Alternative Approaches Considered

**A. Bounding-box approach (current)**
- Draw wall as filled rectangles from rtree bounding box
- Hack openings with white rectangles
- Problems: no wall thickness visible, openings are hacked, curtain walls don't close
- Verdict: NOT acceptable for professional output

**B. Double-line from bounding box**
- Draw wall as two parallel lines from bbox edges
- Manually compute opening gaps from door/window bboxes
- Better than (A) but still works from bounding boxes, not actual geometry
- Miss complex shapes, angled walls, curved walls
- Verdict: acceptable for simple buildings but fragile

**C. Mesh section cut (recommended)**
- Slice actual triangle meshes at cut height
- Produces exact 2D outlines including opening voids
- Works for any geometry shape
- The 3D model already has all information; just extract it
- Verdict: correct approach, matches how professional CAD tools work

**D. IfcOpenShell / OpenCASCADE section cutting**
- Uses BRep geometry kernel for precise boolean section operations
- How BlenderBIM/Bonsai generates drawings
- Requires heavy dependencies (OpenCASCADE)
- Verdict: most sophisticated but heavy dependency

## 7. Styling Reference: TB-LKTN / JKR / ISO 128

The `2D_metadata.db` already defines styles for the JKR Malaysian standard:

**Line Weights (plan view):**
| Element | Weight | Color |
|---------|--------|-------|
| Exterior wall (cut) | 0.50mm | #000000 |
| Interior partition (cut) | 0.35mm | #000000 |
| Glass/glazing | 0.18mm | #4488CC |
| Furniture | 0.15mm | #AAAAAA |
| Dimension lines | 0.18mm | #000000 |
| Grid lines | 0.18mm | #888888 |

**Grid Lines:**
- Pattern: dash-dot (long-short-long)
- Bubble radius: 4.0mm on paper
- Labels: ALPHA for horizontal axes (A, B, C), NUMERIC for vertical (1, 2, 3)
- Extend: 15mm beyond building outline

**Dimensions:**
- Terminator: tick marks at 45° (JKR standard), 1.5mm half-length
- Text height: 2.5mm
- Extension gap: 2.0mm from object
- Rows: 10mm (bay dims), 18mm (overall), 26mm (third tier)

**Room Labels (bilingual):**
| Space Type | English | Malay |
|------------|---------|-------|
| LIVING | LIVING ROOM | RUANG TAMU |
| BEDROOM | BEDROOM | BILIK |
| KITCHEN | KITCHEN | DAPUR |
| BATHROOM | BATHROOM | BILIK MANDI |
| CORRIDOR | KORIDOR | KORIDOR |

**Title Block (JKR):**
Fields: PROJEK, PEMILIK, ARKITEK, JENIS BANGUNAN, TAJUK LUKISAN, DILUKIS, UKURAN, HARIBULAN, NO. LUKISAN, PINDAAN NO

## 8. Implementation Roadmap

**Phase 1: Mesh Section Cut Engine**
- Load triangle meshes from `base_geometries`
- Implement plane-triangle intersection
- Chain segments into contours
- Output raw 2D line segments per element

**Phase 2: Styled Floor Plan SVG**
- Apply line weights from element metadata
- Draw CUT elements as section outlines (heavy)
- Draw BELOW elements as projections (light)
- Handle glass/mullion elements specifically

**Phase 3: Annotations**
- Grid lines (calculated from structural element centerlines)
- Dimensions (from element extents)
- Room labels (from wall topology / spatial structure)
- North arrow

**Phase 4: Title Block and Sheet Layout**
- Sheet template from `ad_sheet_template`
- Title block from `ad_title_block`
- Border, scale notation

**Phase 5: Multi-Storey and Other Views**
- Generate separate floor plan per storey
- Elevation views (vertical projection)
- Section views (vertical cut)
- Roof plan

## 9. Data Pipeline

**DAGCompiler `lib/output/*.db` → 2D_Layout `lib/input/`**

The data flows through the multi-module reactor:

```
DAGCompiler/
  lib/input/     ← Rosetta Stones (extracted DBs from IFC, reference only)
  lib/output/    ← Compiled building DBs (the compiler's product)
                      ↓
2D_Layout/
  lib/input/     ← Copies from DAGCompiler/lib/output/ (compiled DBs)
  lib/output/    ← SVG drawings (this module's product)
```

The `reference/rosetta/` and root `output/` directories are being migrated into
`DAGCompiler/lib/input/` and `DAGCompiler/lib/output/` respectively (handled separately).

Rosetta Stones (extracted DBs) are NOT inputs to 2D_Layout. Only compiled DBs are.

| Source (DAGCompiler output) | Copy in 2D_Layout | Status |
|-----------------------------|-------------------|--------|
| `DAGCompiler/lib/output/samplehouse.db` | `lib/input/samplehouse.db` | **Active test target** |
| `DAGCompiler/lib/output/duplex.db` | `lib/input/duplex.db` | Available, not tested yet |
| `DAGCompiler/lib/output/terminal.db` | `lib/input/terminal.db` | Available, not tested yet |

**Testing on SampleHouse (SH) only for now.** Get SH correct to professional standards first, then verify Duplex and Terminal.

## 10. Module Structure

```
2D_Layout/
  pom.xml                          ← Maven module (depends on dag-compiler)
  src/main/java/com/bim/layout/    ← Java implementation
  src/test/java/com/bim/layout/    ← Tests
  lib/input/                       ← Compiled DBs from DAGCompiler (gitignored)
  lib/output/                      ← SVG drawing output (gitignored)
  python/                          ← Python prototype (reference only)
  archive/                         ← Previous output snapshots
  docs/                            ← This document
```

## 11. Key Files

| File | Purpose |
|------|---------|
| `python/drawing_writer.py` | Python prototype (reference for Java port) |
| `python/section_cut.py` | Python mesh slicer (reference for Java port) |
| `lib/input/2D_metadata.db` | Drawing styles, symbols, title block definitions |
| `lib/input/samplehouse.db` | SH compiled DB — primary test building |

## 10. References

- [Floor plan (Wikipedia)](https://en.wikipedia.org/wiki/Floor_plan) — Floor plans are drawn at ~4ft (1.2m) above finished floor
- [Orthographic Drawing: Plans & Sections (Columbia)](http://cdp.arch.columbia.edu/smorgasbord/modules/13-arch-drawing/13-1_orthographic-drawing/) — Plans are horizontal sections viewed from above
- [Architecture Drawing Projections (First In Architecture)](https://www.firstinarchitecture.co.uk/architecture-drawing-projections/) — Orthographic projection principles
- [An optimal algorithm for 3D triangle mesh slicing (Minetto et al.)](https://www.inf.ufpr.br/murilo/public/CAD-slicing.pdf) — O(n+k+m) mesh slicing algorithm
- [IfcOpenShell drawing generation (GitHub #1153)](https://github.com/IfcOpenShell/IfcOpenShell/issues/1153) — Open-source IFC to 2D SVG approach
- [2D drawings from IFC (OSArch)](https://community.osarch.org/discussion/1450/2d-drawings-in-dwg-from-ifc-files) — Community discussion on approaches
- [Drawing 2D: Section and Projection (Aalto)](https://digitaldesign.aalto.fi/digital-design-workflows/vector/2d-cad-drafting/drawing-2d-section-and-projection/) — Section cut and projection principles

---

## Session Log

### 2026-02-17 — Phase 3: TB-LKTN Sheet Compliance (Ground Floor Plan + Elevations)

**What was done:**
Brought `drawing_writer.py` from ~50% to ~80% TB-LKTN standard compliance in a single-file change (916→1316 lines). All output now renders on a fixed A3 sheet (420×297mm) with proper JKR formatting.

**Changes implemented (9 items):**
1. **Fixed A3 sheet coordinate system** — SVG viewbox now `0 0 420 297`. Building centered in content area (265×277mm) left of title block. All coordinates use `to_sheet()` closure combining `world_to_paper()` + sheet offset.
2. **Sheet border** — 0.7mm rectangle at margin boundaries (25L, 10T/R/B) from `ad_sheet_template`.
3. **JKR title block** — 120mm strip right side. 11 fields from `ad_title_block`: PROJEK, PEMILIK, ARKITEK, JENIS BANGUNAN, TAJUK LUKISAN, DILUKIS, UKURAN, HARIBULAN, NO. LUKISAN, DISEMAK, PINDAAN NO. 30/70 label/value column layout. "JABATAN KERJA RAYA MALAYSIA" header.
4. **Grid dash-dot fix** — Changed `dash="2,2"` → `dash="4,1,1,1"` per `ad_grid_style.line_style = 'dash-dot'`.
5. **North arrow** — Procedural filled/outlined triangle + "N" label, top-right of content area.
6. **Room labels** — `infer_rooms()` clusters furniture by keyword (bed→BILIK, sofa/lounge→RUANG TAMU, dining→RUANG MAKAN). Bilingual Malay labels from `ad_room_label` + estimated area in m².
7. **Annotation tags** — D1–D3 and W1–W4 hexagon callouts from `ad_annotation_tag` (4mm, 0.18mm stroke). Doors offset north/east of wall, windows offset south/west.
8. **Scale bar** — Graphic 0–1m–2m–5m bar (alternating black/white) below drawing title.
9. **Door swing direction** — Furniture-based: counts furniture on each side of host wall, swings toward room side. E-W walls: north/south. N-S walls: east/west.
10. **Elevation A3 treatment** — All 4 elevations get same A3 sheet, border, title block with bilingual titles (PANDANGAN HADAPAN, BELAKANG, KIRI, KANAN).

**New function: `read_drawing_metadata()`** — Loads 4 tables from `2D_metadata.db` (sheet template, title block, room labels, annotation tags). All drawing functions receive `meta` dict.

**SVG layers added:** `border`, `title_block`, `room_label` (total 10 layers now).

**Verification:** 24/24 content checks passed. All 5 SVGs (1 plan + 4 elevations) generate cleanly.

**User review feedback:**
- Floor plan is a good improvement — grids present, scale bar is a nice touch, labelling accurate, overall readable and resembling actual 2D
- **Dimension lengths are odd numbers** — need rounding routine per UBBL/housing standard; one set doesn't add up. Math validation check needed.
- **Title block legend underused** — could show door/window schedule (D1 = main door, W1 = fixed window, etc.) in the title block or legend area
- **Annotations could be larger** — arrows and labels on the diagram itself, scaled up to fill empty space on the A3 sheet
- **Elevations are blind** — missing height grids, level markers, storey heights. Need to match TB-LKTN sample guide for side views
- **Roof plan missing** — no plan view of roof yet
- **Elevation views too small** — not filling the available sheet space, should scale up

**What's next (Phase 3b):**
- ~~Dimension rounding / math validation routine~~ ✓ Done (see below)
- Door/window schedule in title block or legend strip
- ~~Elevation height grids + level markers + storey annotations~~ ✓ Done (see below)
- Roof plan view (`--roof-plan`)
- Auto-scale to fill content area (currently fixed 1:100, could compute best-fit)
- Larger annotation tags and leader lines on the drawing

### 2026-02-17 — Phase 3b: Dimension Rounding + Elevation Height Grids (SH focus)

**What was done:**
Fixed two gaps identified in Phase 3 review: odd-numbered dimensions and blind elevations.

**1. Dimension rounding via `snap_grids()`**
Raw grid positions from wall centerline averaging produced non-architectural bay sizes (4455mm, 4681mm, 2218mm, 3583mm). Added `SNAP_MODULE = 100` mm constant and `snap_grids()` function that:
- Anchors at first grid position on each axis
- Snaps each bay distance to nearest 100mm
- Rebuilds subsequent positions so bays are internally consistent
- Guards against collapsing a bay to zero

| Axis | Raw Bays | Snapped Bays | Max Adj |
|------|----------|--------------|---------|
| X | 4900, 4455, 4681 = 14036 | 4900, 4500, 4700 = 14100 | 45mm |
| Y | 2218, 3583 = 5800 | 2200, 3600 = 5800 | 18mm |

All bays now standard housing modules. Overall dimensions add up correctly.
Max adjustment 45mm — within wall thickness tolerance, invisible at 1:100 (0.45mm on paper).

**2. Elevation height grids via `detect_levels()`**
Added `detect_levels()` that auto-detects building height levels from element Z ranges:
- FFL (Finished Floor Level): always 0.000
- CLG (Ceiling Level): mode of interior partition tops, snapped to 100mm → 2.300
- RIDGE: max roof Z, snapped → 3.500

Each elevation now draws:
- Horizontal dash-dot grid lines at each level (extending across full elevation width)
- Left-side level markers: triangle pointer + label (FFL, CEILING LEVEL, RIDGE LEVEL) + elevation value (+0.000, +2.300, +3.500)
- Right-side vertical dimensions: bay heights (2300mm, 1200mm) + overall (3500mm) with tick marks

All 4 elevations (front/rear/left/right) now have height annotations.

**Files modified:** `2D_layout/drawing_writer.py` only (single file, +80 lines net).

**Remaining gaps (SH):**
- Roof plan view still missing
- Elevation views still small (not filling sheet)
- ~~Door/window schedule in legend~~ ✓ Done (see below)
- Larger annotation labels and leader arrows on plan

### 2026-02-17 — Phase 3c: Title overlap fix + Opening schedule legend

**What was done:**
1. **Drawing title pushed below grid bubbles** — Title Y moved from `MARGIN_BOTTOM - 8` to `MARGIN_BOTTOM + 2`, creating 5mm clear gap below bottom grid bubble edge. No more overlap.
2. **Door/window schedule in title block** — New `draw_opening_legend()` function draws a tabular schedule in the 153mm free space of the title block strip, above the JKR fields. Shows:
   - Bilingual header: JADUAL PINTU & TINGKAP / DOOR & WINDOW SCHEDULE
   - Columns: TAG, SIZE, DESCRIPTION, QTY
   - Groups identical openings: D1 (1810×2110, EXTDBL FLUSH, 1 No.), D2-D3 (810×2110, INTSGL, 2 No.), W1-W4 (1810×1210, SGL PLAIN, 4 No.)
   - `_parse_opening_name()` helper extracts description + size from IFC element names

**Remaining gaps (SH):**
- Roof plan view still missing
- ~~Elevation views still small (not filling sheet)~~ ✓ Fixed (see below)
- Larger annotation labels and leader arrows on plan

### 2026-02-17 — Phase 3d: Tight-fit sheet height (landscape desktop)

**Problem:** A3 sheet (420x297mm) had large empty vertical areas. Building content only needs ~112mm (floor plan) or ~85mm (elevation) vertically, but the 297mm sheet created ~100mm+ of dead space above and below. SVGs were tall and wasted screen real estate in landscape desktop view.

**What was done:**
1. **Tight-fit sheet height** — After computing drawing envelope, code now calculates minimum sheet height needed:
   - Left side needs: envelope_h + 20mm (title/scale below)
   - Right side needs: 109mm (title block fields) + overhead (header/legend)
   - Takes max of both sides + margins → overrides `sheet['height_mm']`
2. **JKR header pinned to top** — "JABATAN KERJA RAYA" text now pinned 10mm from top of title block instead of centered in residual space (which broke when sheet shrinks).

**Results:**

| SVG | Before | After | Aspect Ratio |
|-----|--------|-------|-------------|
| Floor plan | 420 x 297 | 420 x 194 | 2.16:1 |
| Elevations | 420 x 297 | 420 x 154 | 2.73:1 |

All content verified within borders, XML well-formed. Diagrams unchanged — only empty space removed.

**Remaining gaps (SH):**
- ~~Roof plan view still missing~~ ✓ Done (see below)
- Elevation roof profile — side views show bbox rectangle instead of pitched profile
- Larger annotation labels and leader arrows on plan

### 2026-02-17 — Phase 3e: Roof plan view

**What was done:**
1. **`draw_roof_plan()`** — New function generating top-down roof view with:
   - Roof outline (bold, eave line) from roof element bbox XY projection
   - Building footprint inside (lighter, showing overhang clearance)
   - Ridge line (dashed) at Y=1.33m, found via section cut at Z=peak-0.1m
   - Bilingual labels: RABUNG/RIDGE, CUCURAN/EAVE
   - Slope direction arrows (4 per side, from ridge toward eaves)
   - Overhang dimension (N=600mm shown, S/E/W detected)
   - Grids with bubbles (A-D, 1-3) — same as floor plan
   - Title block with sheet number A-06
2. **Wired into `main()`** — `--roof-plan` flag, included in `--all`
3. **Tight-fit sheet**: 420 x 163mm (2.58:1 landscape ratio)

**Ridge detection approach:** Section cut at `roof_max_z - 0.1` intersects the roof near its peak. The Y centroid of that contour gives the ridge line position. This generalises to any pitched roof orientation.

**Output summary:** `Roof plan: 1 roof element(s), ridge Y=1.349m, overhang N=647 S=548 E=0 W=696 mm`

**Full SVG set now:** 6 drawings (floor plan, roof plan, 4 elevations)

**Remaining gaps (SH):**
- ~~Elevation side views: roof drawn as bbox rectangle, should show pitched profile~~ Done (see Phase 3f below)
- ~~Elevation ceiling overlap line~~ Done (see Phase 3f below)
- Elevation overhang not differentiated (soffit/fascia detail)
- Larger annotation labels and leader arrows on plan

### 2026-02-17 — Phase 3f: Mesh-based roof silhouette + ceiling overlap

**User feedback:** Side elevations indicate ridge but look too blocky compared to floor and roof plans. Should show the roof shape from actual mesh (curves for curved roofs, pitch for pitched) and show ceiling level as a separate interior reference line, not just the roof line.

**What was done:**
1. **Mesh-based roof silhouette via convex hull projection** — Replaced all bbox/geometric-assumption roof rendering:
   - Loads actual roof triangle mesh vertices from `base_geometries`
   - Projects onto elevation plane (XZ for front/rear, YZ for left/right)
   - Computes 2D convex hull (Andrew's monotone chain algorithm — pure Python, no deps)
   - Hull captures the true roof shape: pitch for gable, curve for barrel vault, hip angles, etc.
   - TB-LKTN result: left/right shows 3-point triangle (gable), front/rear shows 4-point rectangle (correct)
   - Bold eave line drawn from lowest hull edge
   - **Portable to Java**: same algorithm, same approach, works on `float[]` vertex arrays
2. **Ceiling level as interior reference line** — Dashed line (#555555, dash 2,1) at CLG height with inline label "CLG +2.800". Distinct from roof/eave lines. Shows habitable ceiling boundary vs roof void.

**Functions added:**
- `_convex_hull_2d(points)` — Andrew's monotone chain, pure Python, O(n log n)
- `roof_silhouette(db_path, face)` — loads mesh, projects, returns hull

**Known elevation quality gaps still open:**
- Roof overhang soffit/fascia not differentiated from main roof body
- No visible material hatch or texture in section areas
- Annotation tags and leader arrows on plan still small
- No verge/barge board detail on gable ends

### Architecture decision: Python prototype → Java migration
User confirmed multi-module Maven restructure done (DAGCompiler + 2D_Layout). Python `2D_layout/` stays as the prototype testing reference. The Java `2D_Layout/` module has 6 stub classes ready for porting. Fix Python prototype first, then port to Java.

The multi-module reactor makes it easy to add further peer modules (e.g. a future Metadata Editor) — just add a directory with a `pom.xml` and a `<module>` entry in the parent.

---

## 12. Mathematical Foundation — 3D→2D Projection

> **Type-Safe Design connection:** When a designer gesture is committed as a `W_BOM_Variance`
> and promoted, the compiler re-runs and the 2D drawings update automatically — no re-authoring.
> See [SPATIAL_VARIANCE_SRS.md §11](../../docs/SPATIAL_VARIANCE_SRS.md) for the full spec.
> The Synthetic Rosetta Stone (SPATIAL_VARIANCE_SRS.md §12) uses the DrawingWriter as its
> verification step: panel input → variance → compile → project → measured dimension.


### 12.1 Core Principle

The BIM compiler produces a building `B = C(Ω, Φ, Ψ, Λ, J)` — a pure function from intent to
3D geometry. The Rosetta Stone gates G1-G6 prove the 3D output matches source IFC within 0.025mm.

Because 3D placement is deterministic and verified, **2D drawings are exact projections, not
approximations**. Every line in a floor plan, elevation, or section is a direct geometric consequence
of `T(e)` (Stage P of the Unified Compilation Equation):

```
T(e) = T(p(e)) + R(θ(e)) · Δ(e)      [Stage P — element placement]
```

The 2D projection is:

```
2D_view(e, π) = project(T(e), π)
```

Where `π` is the projection plane (horizontal for plans, vertical for elevations, arbitrary for
sections). This is an algebraic identity, not an approximation.

**Industry contrast:** Conventional BIM workflows author 2D drawings manually or infer 3D from
2D (fragile, lossy). This compiler inverts the flow: 3D is primary, 2D is derived. The derived
views are mathematically equivalent to the 3D model — any discrepancy would violate G3-DIGEST.

### 12.2 Three Standard Views

| View | Projection Plane π | Operation | Implementation |
|------|--------------------|-----------|----------------|
| **Floor Plan** | XY at `z = storey_FFL + 1.0m` | Mesh section cut: `∀ triangle t: solve t ∩ π` → chain contour segments | `section_cut.py` / `SectionCut.java` |
| **Elevation** | XZ (front/rear) or YZ (left/right) | Project mesh vertices onto plane; convex hull of projection | `drawing_writer.py:_convex_hull_2d()` |
| **Roof Plan** | XY at `z = roof_peak − 0.1m` | Section cut + slope direction arrows | `drawing_writer.py:draw_roof_plan()` |
| **Section** | Arbitrary vertical plane `ax + by = c` | Mesh section cut on custom plane | PLANNED |

### 12.3 Section Cut Algorithm

For floor plans and sections, the intersection of a triangle mesh with plane `z = z₀`:

```
For each triangle (v₀, v₁, v₂):
  For each edge (vₐ, v_b) straddling z₀:
    t = (z₀ − vₐ.z) / (v_b.z − vₐ.z)
    p = vₐ + t · (v_b − vₐ)           → intersection point in XY
  Two intersection points → one 2D line segment
Chain segments by endpoint proximity → closed contours
```

For walls with opening voids (windows, doors), the mesh already encodes the voids as geometry.
The section cut automatically produces correct outlines with gaps — no manual opening subtraction.

### 12.4 Round-Trip Correctness Proof

The 2D→3D→2D round trip proves both the import and the compile are correct:

```
Source 2D (DXF/DWG)
    ↓ [2D23D import]
Provisional IFC
    ↓ [IFC extraction → BOM compile]
output.db  (G1-G6 verified, ±0.025mm from source IFC)
    ↓ [3D→2D projection]
Derived 2D (SVG/DXF)
```

**Verification invariant:** `‖derived_2D − source_2D‖ ≤ tolerance`

- If the round-trip matches within tolerance: both import and compiler are validated
- If it fails: exactly one of the two legs is wrong — isolation is trivial (compare output.db vs source IFC directly using G1-G6 gates)

No existing BIM tool can perform this verification because they do not have a pure-function
compiler with cryptographic output sealing (G4-TAMPER).

### 12.5 Witnesses

| ID | Claim | How to verify |
|----|-------|--------------|
| W-2D-PLAN | Floor plan contours match mesh section cut at z=FFL+1.0 for SH | Count wall contours: must equal IfcWall count at storey |
| W-2D-ELEV | Elevation silhouette matches convex hull of projected mesh vertices | Compare hull points to element_transforms bounding ranges |
| W-2D-DIGEST | Identical output.db → identical SVG bytes (pure function property) | Run drawing_writer.py twice, diff outputs |
| W-2D-ROUNDTRIP | 2D23D→compile→project matches source DXF within 50mm for SJTII T1 | Overlay derived DXF on source DXF, measure max offset |

---

## 13. 2D23D Integration — Closing the Import Loop

### 13.1 What 2D23D Does

[2D23D](https://github.com/naquib0513/2D23D) is an open-source tool that converts 2D CAD
drawings (DXF/DWG) to provisional 3D IFC models. Key capabilities:

| Capability | Detail |
|-----------|--------|
| Grid detection | 100% accuracy on orthogonal grids |
| Wall detection | Merges collinear segments, handles T/L/X intersections |
| Column placement | At grid intersections |
| Slab generation | From grid extents per storey |
| Scale | Validated on SJTII Terminal 1: 7 floors, 54MB DXF, 1038 walls, 35s |

Shared dependency stack: `ezdxf`, `ifcopenshell`, Blender/Bonsai — zero new dependencies for
this project.

### 13.2 Complementary Roles

| Tool | Direction | Quality | Review needed? |
|------|-----------|---------|---------------|
| 2D23D | 2D → 3D | Provisional (confidence-scored) | Yes — flags uncertain geometry |
| This compiler | 3D → 2D | Exact (G1-G6 proof) | No — derived, not authored |

2D23D solves the hard problem (inference from incomplete drawings). This compiler solves the
easy problem (projection from verified geometry). Together they close the loop.

### 13.3 Integration Pipeline

```
Legacy DXF archive
    ↓  2D23D (35s for 7-floor terminal)
Provisional IFC  [confidence scores flagged]
    ↓  IFC extraction (placement_extractor.py)
extracted.db  [element_instances, elements_rtree, spatial_structure]
    ↓  BOM compile (BuildingCompiler.java)
output.db  [G1-G6 gates run here]
    ↓  3D→2D projection (DrawingWriter.java)
SVG/DXF  [compare against source DXF → W-2D-ROUNDTRIP]
```

### 13.4 Provisional-by-Design Philosophy

2D23D flags geometry with confidence scores. The compiler's wireframe-first interaction
(BIM_Designer_SRS.md §26, WF-R1..R3) mirrors this exactly: GPU bbox overlays until commit.
Low-confidence 2D23D elements map naturally to WF-R1 wireframe state; user review → commit
transitions to WF-R2. The confidence score can be stored in `elements_meta.element_type` for
display in the Designer panel.

### 13.5 Validation Gate Application

After 2D23D import, run the Rosetta Stone gates against the source DXF geometry:

| Gate | 2D23D validation role |
|------|-----------------------|
| G1-COUNT | Wall count in IFC ≥ wall count detected by 2D23D |
| G2-VOLUME | Slab volumes match DXF floor area × storey height |
| G3-DIGEST | Re-running 2D23D on same DXF produces identical IFC |
| G4-TAMPER | Seal output.db after first validated compile |
| G5-PROVENANCE | Each element traceable to source DXF layer + line |
| G6-ISOLATION | Per-floor isolation — SJTII 7 floors compile independently |

### 13.6 Roadmap

| Phase | Task | Milestone |
|-------|------|-----------|
| 1 | Run 2D23D on SJTII T1 DXF → produce IFC | W-2D-ROUNDTRIP baseline |
| 2 | Feed IFC through extraction → compile → G1-G6 | Validate import quality |
| 3 | Project output.db → SVG | Compare to source DXF visually |
| 4 | Automate overlay diff (max offset metric) | W-2D-ROUNDTRIP quantified |
| 5 | Confidence score → WF-R1/R2 state mapping in Designer | Wireframe-first UX for DXF imports |

---

## 14. DXF Output — Professional Architectural Format

### 14.1 Why DXF alongside SVG

SVG is a browser rendering sidecar. DXF is the **professional deliverable** that architects
open in AutoCAD, ArchiCAD, FreeCAD, and QCAD. Key differences:

| Property | SVG | DXF |
|----------|-----|-----|
| Units | ViewBox (implicit mm) | `$INSUNITS=4` (explicit mm) |
| Coordinates | Paper-space (PAPER_FACTOR applied) | Model-space (true mm, 1:1) |
| Layers | `<g id>` groups (no metadata) | Named layers + colour + linetype |
| Line types | `stroke-dasharray` (hardcoded) | LTSCALE-scaled standard linetypes |
| Dimensions | Hardcoded SVG geometry | DIMSTYLE table (auto-scaling) |
| Round-trip | Cannot overlay on source DXF | Direct coordinate comparison |
| App integration | Browser only | Open in any CAD tool |

The round-trip proof (W-2D-ROUNDTRIP) requires DXF output: overlay derived DXF on
source DXF and measure max offset. SVG has no equivalent overlay mechanism.

### 14.2 Layer Naming — AIA Standard

| Our internal layer | AIA DXF layer name | ISO 13567 |
|--------------------|--------------------|-----------|
| `wall_stroke` (exterior cut) | `A-WALL-FULL` | A-WL-FL |
| `wall_stroke` (partition cut) | `A-WALL-PRTN` | A-WL-PT |
| `wall_fill` | `A-WALL-PATT` | A-WL-PA |
| `opening` (windows) | `A-GLAZ` | A-GL |
| `opening` (doors) | `A-DOOR` | A-DR |
| `grid` | `A-GRID` | A-GR |
| `dimension` | `A-ANNO-DIMS` | A-AN-DI |
| `label` / `room_label` | `A-ANNO-TEXT` | A-AN-TX |
| `furniture` | `A-FURN` | A-FU |
| `title_block` | `A-TTLB` | A-TB |

### 14.3 Line Types and Weights — ISO 128

DXF standard linetypes (not hardcoded dash patterns):

| Element | DXF linetype | Weight (pen) |
|---------|-------------|-------------|
| Ground line | `CONTINUOUS` | 0.70 mm |
| Section-cut wall outline | `CONTINUOUS` | 0.50 mm |
| Interior partition | `CONTINUOUS` | 0.35 mm |
| Visible (projection) | `CONTINUOUS` | 0.25 mm |
| Hidden edges | `HIDDEN` | 0.18 mm |
| Grid / centre lines | `CENTER` | 0.18 mm |
| Dimensions, hatching | `CONTINUOUS` | 0.18 mm |

The critical principle: **LTSCALE × linetype definition = printed dash length**.
`LTSCALE` must equal `SCALE / 100` so patterns look correct at the drawing scale.
Our SVG engine currently hardcodes dash lengths — these must be replaced with a
scale-aware formula: `dash_mm = base_dash × (SCALE / 100)`.

### 14.4 DXF Model Space vs Paper Space

DXF geometry lives in **model space** at 1:1 (true mm). Viewports in **paper space**
apply the scale transform. This means:

- All coordinates written at world scale (× 1000 for m→mm, no PAPER_FACTOR)
- Dimension text size = `TXT_DIM × SCALE` (2.5mm at 1:100 → 250 in model space)
- `DIMSCALE = SCALE` in the DIMSTYLE table → all annotations scale correctly

Our SVG engine applies `PAPER_FACTOR` before writing. The DXF exporter must **not**
apply PAPER_FACTOR — write raw world coordinates.

### 14.5 Implementation — `drawing_writer_dxf.py`

```python
import ezdxf

def write_floor_plan_dxf(db_path: str, out_dxf: str, scale: int = 100):
    doc = ezdxf.new('R2010')
    doc.header['$INSUNITS'] = 4   # mm
    msp = doc.modelspace()

    # Define AIA layers
    for name, color, ltype in [
        ('A-WALL-FULL', 7, 'CONTINUOUS'),
        ('A-GRID',      8, 'CENTER'),
        ('A-ANNO-DIMS', 7, 'CONTINUOUS'),
        ...
    ]:
        doc.layers.add(name=name, color=color, linetype=ltype)

    # LTSCALE: dash patterns scale with drawing scale
    doc.header['$LTSCALE'] = scale / 100.0

    # DIMSTYLE: text height, tick marks, scale factor
    dimstyle = doc.dimstyles.new('ARCH')
    dimstyle.dxf.dimscale = scale
    dimstyle.dxf.dimtxt   = 2.5   # mm at 1:1 (= 2.5/scale on paper)
    dimstyle.dxf.dimblk   = 'ARCHTICK'  # 45° tick, not arrow

    # Write geometry in model-space mm (no PAPER_FACTOR)
    for element in cut_elements:
        for contour in section_cut(element):
            points = [(x * 1000, y * 1000) for x, y in contour]
            msp.add_lwpolyline(points, dxfattribs={'layer': 'A-WALL-FULL'})

    doc.saveas(out_dxf)
```

### 14.6 Round-Trip Verification (W-2D-ROUNDTRIP)

```python
# Compare derived DXF against source DXF
import ezdxf

def overlay_diff(derived_dxf, source_dxf, tolerance_mm=50):
    """Measure max offset between matching wall segments."""
    derived = ezdxf.readfile(derived_dxf)
    source  = ezdxf.readfile(source_dxf)
    # For each wall in derived A-WALL-FULL layer:
    #   Find closest wall segment in source
    #   Measure centroid offset
    # Assert max_offset <= tolerance_mm
```

Because coordinates are in model-space mm, the comparison is direct — no viewport
inverse-transform needed. This is W-SYNTHETIC-RS-2 for SJTII T1.

### 14.7 Professional Readability Standards (SVG + DXF)

These apply to BOTH output formats. Defects noticed during SH review vs TBLKTN reference:

**Line weights (ISO 128 pen scale at 1:100):**
- Ground line: **0.70 mm** — boldest line, visual base of every elevation
- Section-cut building outline: **0.50 mm**
- Partitions, doors: **0.35 mm**
- Glass, annotations: **0.25 mm**
- Dimensions, grid, hatching: **0.18 mm** (ISO hairline — minimum legible in print)
- `0.15 mm` and below: **do not use** — prints as 1px, invisible on A3

**Text heights (at 1:100 on paper):**
- Drawing title: 5.0 mm
- Room labels: 3.5 mm
- Grid bubble: 3.0 mm
- Dimensions: 2.5 mm (= 250mm real — legible at arm's length on A3)
- Elevation values: 2.5 mm

**Label spacing:**
- Minimum gap between adjacent level markers: 3× text height (7.5 mm at TXT_DIM=2.5)
- When levels are < MIN_LABEL_GAP apart (e.g. APRON ↔ GRD only 1-2mm paper), use
  a leader line to offset the label while the triangle marker stays at the true level

**Bubble sizing:**
- Grid circle radius: 4.0 mm (8 mm diameter) — do not reduce below 3.5 mm

**Dash patterns (scale-aware):**
- Grid CENTER line: `(4 × SCALE/100), (1 × SCALE/100), (1 × SCALE/100), (1 × SCALE/100)`
- Hidden DASHED: `(4 × SCALE/100), (2 × SCALE/100)`
- Currently hardcoded in SVG — must be computed from SCALE constant

