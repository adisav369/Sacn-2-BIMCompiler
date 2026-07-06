# S165 — 2D Drawing Writer: Spec Reform Implementation

## Scope
Implement the new spec sections added to `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §14–17.
One bounded session. Work in priority order. Stop and log findings before switching item.

## Pre-flight (read before coding)
1. `2D_Layout/docs/2D_ARCHITECTURAL_LAYOUT.md` §14, §15, §16, §17, §18
2. `internal/2DSpecToCode.md` §9.5 open issues table
3. `2D_Layout/python/drawing_writer_dxf.py` — functions:
   - `_draw_sheet_layout` (line ~360) — title block, header zone
   - `_draw_grid_legend_panel` (line ~298) — current always-show logic
   - `write_mep_plan_dxf` (line ~2674) — MEP stub
   - `_log` (line ~724), `_write_log` (line ~1421) — current log mechanism
4. `layout_audit.py` — current checks G01/G02/T01/T02/T04/L01/P01/P02/S01
5. Run `python drawing_writer_dxf.py --all --db ../input/SH_extracted.db` → confirm 0 FAIL baseline before touching code

## Witness claims (write these first, before any code)

- W-LANG: After change, `test_conformity.py` reports `[PASS] LANGUAGE: 0 Malay strings`
  for every DXF sheet in SH and DX runs
- W-TRIAGE: After change, `dxf_diagnostic.txt` contains `§TRIAGE X:` lines for every
  bay in every floor/roof plan. Panel shows only PANEL-classified bays.
- W-LOG: After change, `output/log_SH_FLOOR_{ts}.txt` exists and contains
  `§ENTRY`, `§VALUE`, `§QUERY`, `§RENDER`, `§AUDIT` events with numeric values
- W-MEP-SEG: After change, `DX_PLUMBING_{ts}.dxf` contains line entities on
  `A-MEP-PLMB` layer connecting segment bbox endpoints (count > 0)

## Priority order

### P1 — Delete stale FLOOR_*/FRONT_* files (5 min)
`2D_Layout/output/DXF/` contains old-prefix files (FLOOR_20260408_1201.dxf etc.)
from before the SH_/DX_ prefix was introduced. They pollute `layout_audit.py`.

**Action:** Delete all DXF and SVG files in `output/DXF/` and `output/SVG/` that
do NOT have a `{PREFIX}_` prefix (i.e. filename starts with FLOOR/FRONT/REAR/LEFT/RIGHT/ROOF
without SH_ or DX_). Log which files are removed.

After deletion: re-run `layout_audit.py` → confirm 0 FAIL, 0 WARN for all remaining files.

### P2 — Grid dim triage function (§15)
**Spec:** `2D_ARCHITECTURAL_LAYOUT.md §15`

1. Write `_triage_grid_dims(grids, scale, crowding_threshold_mm=15.0)` returning
   `(inline_dims, panel_dims)` — list of GridBay namedtuples.
2. Call it in `write_floor_plan_dxf` and `write_roof_plan_dxf` after `generate_dimensions()`.
3. Pass `panel_dims` to `_draw_grid_legend_panel` — show only crowded bays.
   If `panel_dims` is empty, show `ALL DIMS SHOWN IN DRAWING` single row.
4. In dimension drawing loop: suppress text for PANEL bays (draw line + tick, no text label).
5. Log every bay: `§TRIAGE X: A-B=4900mm (49.0mm@1:100) → INLINE`
6. Update `layout_audit.py` check G02: panel legend row count == triage PANEL count.

**Verify:** `layout_audit.txt` G02 PASS for SH and DX.
SH has 7 grids → at 1:100 all bays ≥15mm → panel shows `ALL DIMS SHOWN IN DRAWING`.
DX has 2 grids each direction → both bays ≥15mm → same.

### P3 — Comprehensive per-view logging (§16)
**Spec:** `2D_ARCHITECTURAL_LAYOUT.md §16`

1. Add `_LOG_VIEW: str = ''` module var; set it at start of each `write_*` function.
2. `_write_log` saves per-view file `output/log_{PREFIX}_{VIEW}_{ts}.txt` in addition
   to consolidated `dxf_diagnostic.txt`.
3. Add `§VALUE` events for every layout constant in `_draw_sheet_layout`:
   ```
   §VALUE paper_w=420.0mm (template.paper.width_mm)
   §VALUE tb_width=75.0mm (template.paper.title_block_width_mm)
   §VALUE grid_ext=20.0mm (template.grid.extend_beyond_building_mm)
   §VALUE bubble_r=4.0mm (template.grid.bubble_radius_mm)
   §VALUE tier1_offset=12.0mm (template.dimensions.tier_1_offset_mm)
   §VALUE crowding_threshold=15.0mm (template.grid.crowding_threshold_mm)
   ```
4. Add `§ENTRY` at top of every public `write_*` function with param values.
5. Add `§QUERY` + sample row after every `cur.execute` call.
6. Add `§RENDER` per entity in the main draw loops (wall, grid, dim, room, tag).
7. Add `§EXIT` at end of every `write_*` with entity count.

**Verify:** `output/log_SH_FLOOR_{ts}.txt` exists, contains all 6 event types,
numeric values present (not just labels). Grep: `grep "§VALUE" output/log_SH_FLOOR_*.txt | wc -l` → ≥ 10.

### P4 — Language mapping in title block (§14.1)
**Spec:** `2D_ARCHITECTURAL_LAYOUT.md §14.1`

1. Add `_FIELD_LABEL_EN` dict at module top (copy mapping table from §14.1 verbatim).
2. In `_draw_sheet_layout`, when drawing field label text: `label = _FIELD_LABEL_EN.get(field_name, field_name)`.
3. In `test_conformity.py`, add `LANGUAGE` check: scan TEXT entities on `A-TTLB` and
   `A-ANNO-TEXT`; FAIL if any contains Malay indicator strings from §14.1 test spec.
4. Conformity log format per §16.6: `[PASS] LANGUAGE: 0 Malay strings in A-TTLB/A-ANNO-TEXT`.

**Verify:** W-LANG witness. Run SH + DX, check conformity log LANGUAGE = PASS.

### P5 — MEP segment rendering (§17.3) — if time permits
**Spec:** `2D_ARCHITECTURAL_LAYOUT.md §17.3`

Query in `write_mep_plan_dxf`:
```sql
SELECT m.element_name, m.ifc_class,
       (r.minX+r.maxX)/2 cx, (r.minY+r.maxY)/2 cy,
       r.minX, r.maxX, r.minY, r.maxY
FROM elements_meta m JOIN elements_rtree r ON m.id=r.id
WHERE m.ifc_class = 'IfcFlowSegment'
```
For each segment: if `(maxX-minX) >= (maxY-minY)` → horizontal, draw line `(minX,midY)→(maxX,midY)`;
else vertical, draw `(midX,minY)→(midX,maxY)`. Layer=A-MEP-PLMB or A-MEP-ELEC. lw=13 (0.13mm).
Log: `§RENDER SEGMENT layer=... src=guid:{g} ({x1:.0f},{y1:.0f})→({x2:.0f},{y2:.0f})`

**Verify:** W-MEP-SEG witness. `DX_PLUMBING_{ts}.dxf` has line entities on A-MEP-PLMB count > 0.

## Session closeout
- Update `internal/2DSpecToCode.md` §12 — mark implemented items Y
- Update §18 open issues in `2D_ARCHITECTURAL_LAYOUT.md` — mark resolved
- Run `layout_audit.py` → must be 0 FAIL across all files
- Run `python test_conformity.py --all --db ../input/SH_extracted.db` → all PASS
- Commit: `[S165] 2D spec reform: triage, log, English, MEP segments`

## # DONE
(append findings here after each item)
