# S222 — Incremental Diff Streaming (variation overlay)

## Concept
No separate "Compare" button. When user drops a 2nd IFC for a building that
already exists in cache, the system auto-detects the variation and streams
the diff on top of the base.

## Flow

### First import (base)
1. User drops IFC → S220 extracts to DB → cached in IndexedDB
2. Viewer streams normally (4D phase order from S221)
3. Building card appears in "My Buildings"

### Second import (variation)
1. User drops updated IFC for same building
2. Import worker extracts to DB2 in memory
3. System detects: building name matches existing cached DB → variation mode
4. Loads base DB from IndexedDB cache (instant — already there)
5. Streams base building first (4D order, normal colours)
6. Then overlays the delta:
   - **Added** (green): elements in DB2 not in DB1 → stream with green material
   - **Removed** (red ghost): elements in DB1 not in DB2 → stream with red 50% opacity
   - **Changed** (yellow): same GUID, different properties → recolour existing mesh yellow
   - **Unchanged**: already streamed, left as-is
7. HUD shows: `Variation "Revised_v2.ifc" — 12 added, 3 removed, 8 changed`

### Diff algorithm (browser-side, no server)
```js
// Both DBs loaded as sql.js instances
const guids1 = new Set(db1.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));
const guids2 = new Set(db2.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));

const added   = [...guids2].filter(g => !guids1.has(g));
const removed = [...guids1].filter(g => !guids2.has(g));

// Changed: same GUID, different properties
const common = [...guids2].filter(g => guids1.has(g));
const changed = common.filter(g => {
  const r1 = db1.exec(`SELECT element_name, material_name, storey, discipline FROM elements_meta WHERE guid='${g}'`);
  const r2 = db2.exec(`SELECT element_name, material_name, storey, discipline FROM elements_meta WHERE guid='${g}'`);
  return JSON.stringify(r1[0].values[0]) !== JSON.stringify(r2[0].values[0]);
});
```

### Landing page UX
- Same "Import IFC" drop zone handles both first import and variation
- No second drop zone needed
- When variation detected, import status shows:
  `"Variation detected — comparing against cached Kitchen_v1..."`
- Building card gets a badge: `v2` with diff summary
- Card button: `3D View ⚡` (same as base — viewer handles the overlay)

### Viewer UX
- Base streams first (4D order), then delta streams on top
- Diff summary panel (top-right, auto-dismiss after 10s):
  ```
  Variation: Kitchen_v2.ifc
  🟢 Added:   12 elements
  🔴 Removed:  3 elements
  🟡 Changed:  8 elements
  ```
- Click category in panel → isolate those elements (hide others at 0.1 opacity)
- Toggle: "Show base only" / "Show variation" / "Show diff"

### Version stacking
- IndexedDB stores base + variations: `{building}_{version}`
- Each import increments version
- Card shows version history dropdown
- Can diff any two versions, not just latest vs previous

## Prerequisites (from S220 implementation)
- Import produces viewer-compatible schema: `elements_meta`, `element_transforms` (center_x/y/z),
  `element_instances` (guid → geometry_hash), `component_geometries` (geometry_hash → BLOBs)
- web-ifc 0.0.77, WASM hosted on OCI with application/wasm MIME
- IndexedDB store `bim_ootb_imports` keyed by filename
- Viewer loads imported DBs via `import://` URL scheme through cachedFetch

## Files
- `deploy/dev/import_worker.js` — detect existing building, extract variation
- `deploy/dev/streaming.js` — delta overlay streaming after base
- `deploy/landing2.html` — variation detection in import handler, card badge
- New: `deploy/dev/diff.js` — GUID set diff logic, colour override, summary panel

## Variation Order Sheet (auto-generated on diff)

When a diff is detected, auto-generate a Variation Order — the cost/schedule
impact report that every QS and project manager needs.

**Data sources (all already exist):**
- Diff result → added/removed/changed GUIDs + ifc_class
- `5D_rates.json` → material + labour + equipment rates per ifc_class
- `4D_phases.json` → phase assignment per ifc_class

**Columns:**
| Status | GUID | Class | Name | Storey | Discipline | Phase | Old Value | New Value | Cost Impact |

**Summary rows:**
- Added total: +RM X (count × rate per class)
- Removed total: -RM Y
- Changed total: ±RM Z (rate difference if material/type changed)
- **Net Impact: ±RM N**
- **Schedule Impact: ±D days** (added/removed elements × productivity rate)
- **Affected Phases:** list of phases touched by the diff

**Output:**
- Excel download (ExcelJS, same pattern as BOQ export)
- Sheet name: `VO_{project}_{part}_v{N}`
- Auto-generated on every diff — no button needed, appears alongside diff summary panel
- Share button sends Excel via navigator.share (same as site cam flow)

**Why this matters:**
This is the document that gets printed, signed, and filed. Every construction
project has variation orders. Today they're manually compiled from spreadsheets.
Here it's automatic — diff the IFC, get the VO with costs. The QS's entire
Tuesday afternoon, automated.

## Files
- `deploy/dev/diff.js` — variation order generation alongside GUID diff
- `deploy/dev/excel.js` — reuse ExcelJS export pattern for VO sheet
- `templates/5D_rates.json` — rate lookup (already exists)
- `templates/4D_phases.json` — phase lookup (already exists)

## Acceptance
- Import SampleHouse.ifc → loads and caches
- Modify 5 elements in Bonsai, re-export as SampleHouse_v2.ifc
- Drop SampleHouse_v2.ifc on same import zone
- System loads cached base first, then shows 5 changed elements in yellow
- HUD shows variation name and diff summary
- Variation Order Excel auto-downloads with cost impact per element
- Net impact shown in diff summary panel: "+RM 39,700 · +4 days"
- No separate Compare button or second drop zone needed

## DO — Testing & Logging

All test output to `deploy/dev/tests/log/`.

### Existing coverage
- **Playwright 08-diff**: 5 tests — load with diffDb param, variance button exists,
  diff computation no throw, result structure valid, overlay no crash
- All 5 PASS with self-diff (same DB as base and diff)

### Gaps to fill in a dedicated session

| Test | Where | What | §-tag |
|------|-------|------|-------|
| Diff with real delta | 08-diff extend | Load base + modified DB, assert added/removed/changed > 0 | `§PW_DIFF_DELTA` |
| Color overlay applied | 08-diff extend | After diff, check scene has green/red/yellow materials | `§PW_DIFF_COLORS` |
| Variance summary text | 08-diff extend | HUD shows "+N added, -M removed" after diff | `§PW_DIFF_SUMMARY` |
| VO Excel download | NEW | Trigger exportVariationOrder, verify download fires | `§PW_VO_EXCEL` |

**Prerequisite:** Need a second test DB with known delta (e.g. Duplex with 5 elements removed).
Create `deploy/buildings/Duplex_modified.db` as fixture.

### test_all.js — add §19
```javascript
// Verify diff.js + variation_order.js wiring
ok('diff.js has computeDiff', diffSrc.includes('computeDiff'));
ok('variation_order.js has exportVariationOrder', voSrc.includes('exportVariationOrder'));
ok('diff.js loaded by viewer', html.includes('diff.js'));
ok('variation_order.js loaded by viewer', html.includes('variation_order.js'));
```

## DO NOT
- Do not modify `deploy/sandbox/` — production
- Do not break existing 08-diff baseline (5/5 PASS)
