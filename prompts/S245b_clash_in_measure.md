# S245b — Clash Detection in Measure Tool

## Scope
Add clash detection to the Measure right-click/long-press info card.
Bbox overlap query from DB, driven by `clash_rules.json`. No new UI button.

## UX Flow (3 levels)

### Level 1 — Passive count (no highlight)
When the storey info card appears (right-click / long-press), append:
```
─────────────────────
Clashes: 12          ← tappable, pointer-events:auto
```
The count comes from a DB bbox-overlap query scoped to the **same storey**
as the info card. No geometry highlight, no scene change. Just a number.

If the info card covers the whole building envelope (e.g. right-click on roof
where storey = '' or all elements share one storey), the query covers all elements.

### Level 2 — Highlight on tap
User taps "Clashes: 12" in the card → reveal mode:
- All non-clashing elements dim to 10% opacity
- Clashing pairs pulse between normal color and orange (1.5s cycle)
- Clash pairs are grouped — each pair gets a small numbered badge
- Max 20 pairs visible at once; card shows "showing 20 of 47" with scroll
- Tap anywhere outside to dismiss and restore opacity

### Level 3 — Clash detail list
Tap "Clashes: 12" does two things at once:
1. Dims all non-clashing elements to 10% opacity
2. Shows an itemised clash list card (scrollable, max-height 60vh):

```
 ── Clashes: 12 (ARC vs STR: 8, MEP vs ARC: 4) ��─
 1. Wall-023 ↔ Beam-007     0.12m  ███ hard
 2. Pipe-041 ↔ Slab-002     0.03m  ███ soft
 3. Duct-019 ↔ Wall-055     0.08m  ███ hard
 ...
```

Each row is tappable → flies camera to that pair, highlights them in
severity color (red/orange/yellow), shows overlap dimension line.
Tap another row or tap outside to dismiss and restore opacity.

## Clash Detection Logic (DB query, not geometry)

```sql
-- Find bbox overlaps within a storey, between different disciplines
SELECT a.guid AS guid_a, b.guid AS guid_b,
       a.ifc_class AS class_a, b.ifc_class AS class_b,
       -- overlap depth = MIN of overlap on each axis
       MIN(
         (a.bbox_x + a.cx) - (b.cx - b.bbox_x),  -- X overlap
         (a.bbox_y + a.cy) - (b.cy - b.bbox_y),   -- Y overlap
         (a.bbox_z + a.cz) - (b.cz - b.bbox_z)    -- Z overlap
       ) AS overlap_m
FROM element_transforms a
JOIN elements_meta ma ON a.guid = ma.guid
JOIN element_transforms b ON a.guid < b.guid  -- avoid duplicates
JOIN elements_meta mb ON b.guid = mb.guid
WHERE ma.storey = ? AND mb.storey = ?
  AND ma.discipline != mb.discipline            -- cross-discipline only
  -- bbox overlap test (all 3 axes must overlap)
  AND (a.cx - a.bbox_x) < (b.cx + b.bbox_x)
  AND (a.cx + a.bbox_x) > (b.cx - b.bbox_x)
  AND (a.cy - a.bbox_y) < (b.cy + b.bbox_y)
  AND (a.cy + a.bbox_y) > (b.cy - b.bbox_y)
  AND (a.cz - a.bbox_z) < (b.cz + b.bbox_z)
  AND (a.cz + a.bbox_z) > (b.cz - b.bbox_z)
```

Note: bbox columns are half-extents (`bbox_x`, `bbox_y`, `bbox_z`) centered
at (`cx`, `cy`, `cz`). Verify column semantics against `docs/SQLite3D_Schema.md`
before implementing.

## clash_rules.json

```json
{
  "clash_rules": [
    {
      "name": "ARC vs STR",
      "source": { "discipline": "ARC" },
      "target": { "discipline": "STR" },
      "tolerance_m": 0.01,
      "ignore_classes": ["IfcOpeningElement", "IfcSpace"]
    },
    {
      "name": "MEP vs STR",
      "source": { "discipline": "MEP" },
      "target": { "discipline": "STR" },
      "tolerance_m": 0.05,
      "ignore_classes": ["IfcOpeningElement"]
    },
    {
      "name": "MEP vs ARC",
      "source": { "discipline": "MEP" },
      "target": { "discipline": "ARC" },
      "tolerance_m": 0.02
    }
  ],
  "severity": {
    "hard":      { "min_overlap_m": 0.05, "color": "#ff0000" },
    "soft":      { "min_overlap_m": 0.01, "color": "#ff8c00" },
    "clearance": { "max_gap_m":     0.10, "color": "#ffff00" }
  },
  "display": {
    "max_visible": 20,
    "dim_opacity": 0.1,
    "pulse_speed_s": 1.5
  }
}
```

File location: `deploy/dev/clash_rules.json` (fetched on demand, cached in memory).

## Level 4 — Review status + Excel export

### Status toggle
Each clash row in the itemised list is tappable to cycle through statuses:
```
  (no status) → Reviewed → Resolved → Accepted → (no status)
```
Visual indicators on the row:
- **Reviewed** — blue dot, text dimmed slightly
- **Resolved** — green checkmark, strikethrough on element names
- **Accepted** — grey, italic (clash acknowledged, no fix needed)

Statuses are stored in memory (`A._clashStatuses[guidA+'|'+guidB] = 'Reviewed'`).
Persisted to `localStorage` key `bim-clash-statuses-{building}` so they survive
page reloads within the same session. Not stored in DB (read-only principle).

### Excel export
Button at bottom of clash list card: **"Export Report"**
Uses existing SheetJS (xlsx.full.min.js, already loaded for BOQ).

Columns:
| # | Element A | Class A | Disc A | Element B | Class B | Disc B | Storey | Overlap (m) | Severity | Status |
|---|-----------|---------|--------|-----------|---------|--------|--------|-------------|----------|--------|

- Rows colored by severity (hard=red, soft=orange, clearance=yellow)
- Status column reflects current review state
- Header row: building name, date, total clashes, reviewed/resolved/accepted counts
- Filename: `{building}_clashes_{date}.xlsx`

User workflow: right-click → see clash count → tap to reveal list → click rows
to review/resolve → export → email the report. No external tools needed.

### Witnesses
- `§CLASH_STATUS guidA=X guidB=Y status=Reviewed` — logged on status toggle
- `§CLASH_EXPORT clashes=N reviewed=X resolved=Y` — logged on export

## Files to Modify
- `deploy/dev/measure.js` — clash count, reveal, status toggle, export
- `deploy/dev/clash_rules.json` — rules config
- `deploy/dev/index.html` — no new button needed (clash lives inside measure)

## Witnesses
- `§CLASH_COUNT storey=X clashes=N` — logged when info card computes clash count
- `§CLASH_REVEAL storey=X showing=N` — logged when user taps to highlight
- `§CLASH_DETAIL guidA=X guidB=Y overlap=Zm` — logged when detail card shown

## Not in scope
- Geometry-level intersection (expensive, bbox sufficient for first pass)
- BCF XML export (future — industry interop)
- Clash across storeys (future: vertical penetrations)
