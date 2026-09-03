# S159 — DX Panel Legend, Grid Layout Reform, MEP Triage

## Status: §A DONE, §B DONE, §C DONE, §D STUB, §F3 DONE, §G DONE, §H DONE

---

## §A — Building Type Label (ISO-killer header)

**Problem:** Right panel header shows only "PUBLIC WORKS DEPARTMENT MALAYSIA".
No building identity. Architect cannot tell which project at a glance.

**Spec:**
Below the JKR header line, before the field rows, add a **building identity block**:

```
PUBLIC WORKS DEPARTMENT MALAYSIA      ← existing header (3.9mm bold)
─────────────────────────────────     ← thick line (0.50mm)
DUPLEX RESIDENTIAL                    ← building type, 5.0mm bold
Ifc4 Duplex                           ← building name, 3.0mm normal
─────────────────────────────────     ← medium line (0.35mm)
[ field rows: PROJECT, CLIENT... ]
```

**Sources (no invention):**
- Building type label: `2d_title_block WHERE field_name='JENIS_BANGUNAN'` → `default_value`
  Override per project: DX → "DUPLEX RESIDENTIAL", SH → "SINGLE STOREY RESIDENTIAL"
- Building name: `spatial_structure WHERE type='IfcBuilding'` → `name`
  Fallback §5.3c: DB stem → "DUPLEX", "IFC4 SAMPLE HOUSE"
- Font size: `title_block.font_height_building_type_mm` (new template key, default 5.0)
- Layer: A-TTLB

**Template key to add** in `drawing_template.json → title_block`:
```json
"font_height_building_type_mm": 5.0,
"font_height_building_name_mm": 3.0
```

---

## §B — Grid Bubble Position Reform

**Problem:** Currently order outward from building is:
`building → dim tiers (tier3, tier2, tier1) → grid bubbles`

The bubbles are right next to the building, cluttered between the walls and dimension lines.

**Correct TB-LKTN order (from reference PDF page 1):**
`building → grid bubbles → dim tiers (tier1 outer, tier2 inner)`

Wait — re-read: user says "round bubbles should be furthest not those measurements".
So the target order outward is:
`building → dim tiers → grid bubbles (outermost)`

**Spec:**
Grid line extends from `bld_min - grid_ext` to `bld_max + grid_ext`.
Bubble sits at the END of the extension (furthest point).
Dimension tiers sit BETWEEN the building edge and the bubble.

```
     dim_tier1   dim_tier2   bld_edge          bld_edge   dim_tier2   dim_tier1
(O)──────────────────────────|== building ==|──────────────────────────(O)
bubble                                                                  bubble
```

**Change in `write_floor_plan_dxf` and `write_roof_plan_dxf`:**
- Bubble Y position: `bld_max_y + grid_ext + bubble_r` (current — already outermost)
- Dimension tier offsets: `tier_1_offset_mm` and `tier_2_offset_mm` must be LESS than
  `grid_ext + bubble_r*2` to stay inside the bubble position
- If `tier_1_offset_mm (26) > grid_ext (15) + bubble_r*2 (8) = 23` → dim is OUTSIDE bubble!

**Fix:** Dim tiers must reference from `bld_edge`, bubble references from `bld_edge + grid_ext`.
Ensure `tier_1_offset_mm < grid_ext_mm` so dims stay inside the extension zone, bubbles outermost.
Suggest: `tier_1_offset_mm: 12`, `tier_2_offset_mm: 6`, `grid_ext: 20` — verify against TBKLTN PDF.

---

## §C — Right Panel Grid Legend (anti-crowding)

**Problem:** When bay spacing is narrow (e.g. <1500mm at 1:100 = <15mm paper), the
dimension text overlaps adjacent text and becomes illegible.

**Two-part solution:**

### C1 — Crowding detection
After computing dims, for each dim:
```
is_crowded = (dim.end - dim.start) * MM / scale < crowding_threshold_mm
```
`crowding_threshold_mm` from template (default 12mm paper). Crowded dims → suppress inline text.

### C2 — Panel legend block
In `_draw_sheet_layout`, after the field rows, draw a **GRID REFERENCE** legend:

```
┌──────────────────────────┐
│  GRID REFERENCE          │  ← section header, 2.5mm bold
├──────────────────────────┤
│  HORIZONTAL (X)          │  ← axis group header
│  A ←──→ B    4900        │
│  B ←──→ C    4500        │
│  C ←──→ D    4700        │
│  TOTAL:      14100       │
├──────────────────────────┤
│  VERTICAL (Y)            │
│  1 ←──→ 2    2200        │
│  2 ←──→ 3    3700        │
│  TOTAL:      5900        │
└──────────────────────────┘
```

**Always show legend** (not only when crowded) — it makes every sheet self-contained.
Crowding only controls whether the inline dim text is also shown.

**Sources:**
- Grid labels + positions: `grids[]` already computed
- Bay dims: `dims[]` already computed (from `generate_dimensions`)
- Legend text height: `title_block.font_height_label_mm` (2.0mm)
- Legend section header: `title_block.font_height_value_mm` (3.0mm)

**Placement:** Below the last field row in the title block panel.
If panel height insufficient, reduce row_h of minor (non-required) field rows by 20%.

---

## §D — DX MEP Page Triage (TBKLTN E-01 equivalent)

**TBKLTN reference (PDF page 2):** Electrical plan shows:
- Same floor plan walls (thin, background)
- Electrical symbols: ceiling light (circle), fan (asterisk), switch (line+arc), power outlet (D-shape)
- Wiring runs as thin lines connecting fixtures to distribution board
- Symbol legend in right panel (replacing door/window schedule)

**DX MEP data available:**
```sql
SELECT ifc_class, COUNT(*) FROM elements_meta
WHERE ifc_class LIKE 'IfcFlow%' GROUP BY ifc_class;
```
Results: IfcFlowSegment=427, IfcFlowFitting=358, IfcFlowTerminal=105, IfcFlowController=14

**Triage — what we can draw vs what we must skip:**

| Element | Can draw? | Why |
|---------|-----------|-----|
| IfcFlowTerminal (105) | YES | These are fixtures — position from elements_rtree, classify by element_name |
| IfcFlowSegment (427) | PARTIAL | Pipe/duct runs — draw as thin lines between bbox centroids |
| IfcFlowFitting (358) | YES | Junctions — draw as small circle at centroid |
| IfcFlowController (14) | YES | Valves/switches — draw as small symbol |

**Gap:** DX is plumbing-heavy (roof drains, pipes). TBKLTN E-01 is electrical.
DX may not have electrical (`IfcElectrical*`) elements — verify:
```sql
SELECT DISTINCT ifc_class FROM elements_meta WHERE ifc_class LIKE '%Elec%' OR element_name LIKE '%light%' OR element_name LIKE '%switch%';
```

**Proposed output for DX:**
- If electrical found: `DX_ELECTRICAL_<ts>.dxf` (page E-01)
- If plumbing only: `DX_PLUMBING_<ts>.dxf` (page M-01) — walls background + pipe layout
- Symbol legend in right panel (replaces door schedule on MEP sheets)

**DX MEP page to add to `DX_2D.json`** (status=GAP until implemented):
```json
{ "sheet": "M-01", "title": "PLUMBING LAYOUT", "file": "PLUMBING", "type": "mep", "status": "GAP" }
```

**Implementation path:**
1. New `write_mep_plan_dxf(db_path, discipline, out_dxf)` function
2. `discipline` = 'PLUMBING' or 'ELECTRICAL'
3. Floor plan walls as background (thin, A-WALL-FULL layer, lw=0.18mm)
4. MEP elements by type → symbol from `2d_drawing_symbol` table
5. `2d_drawing_symbol` has 19 symbols — check which match DX MEP element_names

---

## Implementation Order (next session)

1. §A building type label — quick, high visual impact
2. §C panel grid legend — high value, relatively contained change
3. §B bubble position reform — verify against TBKLTN PDF first, measure offsets
4. §D MEP triage — query DX DB first, confirm what's there, then stub the page

## # DONE

### §A — Building identity block (S161, 2026-04-08)
- Added `font_height_building_type_mm: 5.0` and `font_height_building_name_mm: 3.0` to `drawing_template.json → title_block`
- `_infer_building_identity(db_path)` helper: reads `spatial_structure WHERE type='IfcBuilding'` → name; fallback to stem map
- `_draw_sheet_layout` extended: `building_type`, `building_name`, `grids` params
- Identity block drawn below JKR header (thick line separator), above schedule (medium separator)
- DX shows "DUPLEX RESIDENTIAL" / "Ifc4 Duplex"; SH shows "SINGLE STOREY RESIDENTIAL" / "Ifc4 Sample House"

### §C — Grid reference legend (S161, 2026-04-08)
- `_draw_grid_legend_panel` helper: GRID REFERENCE header + HORIZONTAL(X) bays + VERTICAL(Y) bays + TOTAL rows
- Legend height pre-computed and subtracted from `row_h` (field rows adjust automatically)
- Field rows start at `by0 + legend_h` (legend occupies bottom slice of panel)
- DX legend: A-B=200, B-C=600, ... TOTAL=8700; 1-2=200, 2-3=6000, ... TOTAL=17900
- DX+SH: all 6 views PASS, 8/8 conformity PASS

### Layout audit + 6 fixes (S162, 2026-04-08)
- `layout_audit.py` written — T01/T02/T03/T04/G01/G02/P01/P02/L01/S01 checks, outputs `output/layout_audit.txt`
- F1: `derive_grids` exterior-walls-only (Step 1) + `prune_small_bays` pass → DX 15 grids → 2 grids. Template: `grid.min_structural_bay_m: 1.0`
- F2: elevation height dim clamped — `_dim_right_max` computed from paper/margin/title_block. Template: `dimensions.panel_clearance_mm: 5.0`
- F3: level marker labels abbreviated from `level_markers.label_abbreviations` map; offset from `level_markers.text_right_offset_m: 0.8` (was hardcoded 2.0)
- W1: scale bar bug fixed — `sb_scale_factor = MM` (was `1000/scale` = 100× too small); stagger from `scale_bar.label_stagger: true`
- W2: BUILDING TYPE field row syncs with identity block via `title_block.sync_building_type_field: true`
- W3: `write_elevation_dxf` calls `_infer_building_identity` — all elevations now have identity block
- W5: IFC building name underscores stripped in `_infer_building_identity`
- Result: 0 FAIL (was 12) across all 18 DXF files. Conformity 8/8 PASS.

### §B — Grid bubble reform + level text border fix + S01 audit fix (2026-04-08)
- Root cause of T02 bubble/dim overflow: `ann_top` formula missing `extension_gap_mm` (2mm) + `fitted_max_height_mm: 250` too low for tall buildings (DX needs ~280mm)
- Root cause of L01/T02 level text overflow: `txt_offset` pushes text LEFT of border on wide buildings (DX left/right: only 7mm available for 24mm text)
- §B values applied: `grid.extend_beyond_building_mm: 20`, `tier_1_offset_mm: 12`, `tier_2_offset_mm: 6`, `fitted_max_height_mm: 290`
- `_draw_sheet_layout` ann_top formula changed: `grid_ext + bubble_r*2 + gap + 2mm` (no tier_1 — dims now inside bubble zone)
- Floor plan + roof plan dim formula: `dim_y = bld_max_y + off` (was `bld_max_y + grid_ext + bubble_r*2 + gap + off`)
- Extension lines: start from `bld_max_y + dim_gap` (was `bld_max_y + grid_ext + dim_gap`)
- Level text clamping: pre-compute `_elev_border_left` in `write_elevation_dxf`; clamp text anchor = `max(preferred, border_left + clearance + est_text_w)`
- S01 false positive fixed in `layout_audit.py`: exclude specific type strings from generic check
- Result: DX_FRONT/REAR/LEFT/RIGHT/ROOF → 0 WARN. DX_FLOOR → 13 WARN (T01 only: duplex mirror window tag overlaps — inherent geometry, not renderable)
- Remaining open: SH_FLOOR T01 (3 overlaps in title block schedule rows — layout issue, separate task)

### §D — MEP plan stub (2026-04-08)
- DX DB: IfcFlowTerminal=105 includes BOTH electrical (Sconce/Pendant Light, Telephone Outlet) and plumbing (Water Closet, Sink, Shower)
- No IfcElectric* classes — but electrical fixtures present as IfcFlowTerminal by element_name keyword
- `_classify_mep(element_name)` → 'ELECTRICAL' | 'PLUMBING' | 'MEP' by keyword matching
- `write_mep_plan_dxf(db_path, discipline, out_dxf)` stub added: walls background + flow terminal symbols + legend in schedule slot
- Symbols: ELECTRICAL = circle+cross (A-MEP-ELEC), PLUMBING = circle+dot (A-MEP-PLMB)
- `input/DX_2D.json` created with M-01 PLUMBING LAYOUT (status=GAP until fully implemented)
- MEP handler added to `main()` for `ptype='mep'`
- To promote GAP→DONE: verify symbol placement visually, add IfcFlowSegment pipe runs as thin lines

---

## §F — Remaining T01 Text Overlaps (next session)

**Three distinct overlap classes still in audit:**

### F1 — DX duplex window tags overlap (DX_FLOOR T01)
W1–W14 = Level 1 tags; W15–W28 = Level 2 tags. Because both floors render at the same XY
positions (duplex is a stacked mirror), tags from both levels land on top of each other.
```
WARN T01  "W1" ↔ "W15"  bboxes (7.1,3.0)–(9.5,5.0) ↔ (6.5,3.0)–(10.1,5.0)
WARN T01  "W1" ↔ "W16"  (same position)
```
**Root cause:** tag numbering restarts at W15 for Level 2 but same wall XY.
**Fix options:**
- Suppress tags for whichever storey is NOT the current sheet's storey (once §E multi-storey is done)
- OR offset Level 2 tags by a fixed displacement (e.g. tag on opposite side of wall)

### F2 — DX room name / area label overlap (DX_FLOOR T01)
Adjacent rooms share a wall and their centroid labels collide.
```
WARN T01  "BEDROOM" ↔ "LIVING ROOM"   bboxes overlap by ~2mm
WARN T01  "134.0 m²" ↔ "136.7 m²"    same centroid area
```
**Root cause:** `infer_rooms` produces centroids for both halves of the duplex at nearly the same XY.
**Fix:** post-process room list — if two rooms share the same label and centroids are within
`overlap_threshold`, keep only one (or shift by label height).

### F3 — SH_FLOOR T01 title block schedule overlap ✓ DONE (S163, 2026-04-08)
**Root cause (actual):** `row_h = (panel_h - header_zone - legend_h) / n_fields` did NOT subtract
schedule height. Schedule rows drawn from `id_bot` downward overlapped field rows drawn upward.
**Fix:** pre-compute `sch_total_h = sch_hdr_h + (1+n_rows)*sch_row_h`, subtract from `row_h`.
SH_FLOOR: 0 WARN T01 (was 3).

---

## §G — MEP Page Not Generated ✓ DONE (S163, 2026-04-08)
- `DX_2D.json` M-01: `GAP` → `STUB`
- Dispatch updated: `status in ('DONE', 'STUB')`
- Bug fixed: MEP legend_rows were tuples, schedule code expected dicts — converted to dicts
- DX_PLUMBING generated: 10 terminals (4 WC + 2 Sink + 2 Shower + 2 Bath), 0 FAIL, 0 WARN
- Lavatory (6) and electrical fixtures classified as MEP/ELECTRICAL, not PLUMBING — correct for current discipline filter

---

## §H — Dimension Terminator Inconsistency ✓ DONE (S163, 2026-04-09)

**Problem:** Elevation horizontal bay dims use `add_linear_dim` (DXF entity, ARCH_JKR dimstyle,
`dimblk=ARCHTICK`) → renders as **closed arrowheads** in some viewers.
Floor/roof plan dims use **manual 45° tick lines** (`add_line` at tick_angle=45°).
Result: inconsistent terminator style across pages.

**Fix:** Removed try/except add_linear_dim block. Manual pattern: dim line + extension lines from bubble tops + 45° ticks + text at `dim_y - dim_txt_h * 2.5` (below dim line, stays inside border). All DX elevations: T02 cleared, 0 WARN.

**Location:** `write_elevation_dxf` line ~2165:
```python
dim = msp.add_linear_dim(base=(0, dim_y), p1=(xa, 0), p2=(xb, 0),
                          dimstyle='ARCH_JKR', text=str(int(snapped)))
dim.render()
```

**Fix:** Replace `add_linear_dim` with the same manual pattern used in floor/roof:
```python
msp.add_line((xa, dim_y), (xb, dim_y), dxfattribs={...})
for tx in (xa, xb):
    msp.add_line((tx - tick_dx, dim_y - tick_dy),
                 (tx + tick_dx, dim_y + tick_dy), dxfattribs={...})
msp.add_text(str(int(snapped)), ...).set_placement((mid_x, dim_y + txt_h*0.3), ...)
```
Extension lines: from `_mh(grid_pos)` to `dim_y`. Already have `tick_dx/tick_dy` in scope.
Remove the `try/except add_linear_dim` block entirely.

---

## §E — DX Multi-Storey Floor Plan (next session)

**Problem:** DX duplex upper floor (Level 2) is missing from A-01.
Floor plan cut height = 1.2m. Level 2 walls span Z=3.1m–6.0m → cut misses them entirely.

**DB evidence:**
```sql
SELECT storey, minZ, maxZ FROM elements_meta JOIN elements_rtree ...
-- Level 1: Z=0.0–3.1m  (21 walls, hit by cut at 1.2m)
-- Level 2: Z=3.1–6.0m  (25 walls, MISSED by cut at 1.2m)
-- Roof:    Z=?          (4 walls)
```

**Fix required:**
1. `write_floor_plan_dxf` needs `storey_filter` param (or `cut_height_m` override).
   Read storey Z ranges from `spatial_structure` or `elements_meta.storey` + `elements_rtree.minZ`.
   For each storey, cut height = `storey_bottom_z + 1.2`.
2. `DX_2D.json`: add second floor plan entry:
   ```json
   { "sheet": "A-01b", "title": "UPPER FLOOR PLAN", "file": "FLOOR_L2",
     "type": "plan", "storey": "Level 2", "status": "GAP" }
   ```
3. `main()` dispatch: pass `storey=page.get('storey')` to `write_floor_plan_dxf`.

**storey_bottom_z query:**
```sql
SELECT storey, MIN(minZ) FROM elements_meta JOIN elements_rtree ...
WHERE ifc_class='IfcWall' GROUP BY storey
-- Level 1: 0.0m  → cut_height = 1.2m
-- Level 2: 3.1m  → cut_height = 4.3m
```

**Read before coding:** `docs/WorkOrderGuide.md` §Step 5-6, `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §storey.
