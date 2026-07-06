# S220 — Landing Page: One Drop Zone, Full Pipeline

## Goal
First-time visitor sees one "Import IFC" drop zone. But the system handles
the full lifecycle through that single entry point:
- First drop = import + 4D-ordered streaming (S220 + S221)
- Second drop (same building) = auto-detect variation + diff overlay (S222)

No separate buttons. No mode toggles. One action, the system figures out the rest.

## Triage — implementation order

### Phase 1: S220 Import IFC (in progress)
- Already on dev landing. WASM fix in progress (`prompts/S220_wasm_mime_fix.md`).
- User drops IFC → web-ifc extracts → sql.js DB → cached in IndexedDB → view in browser.
- Building card appears in "My Buildings" with `3D View ⚡` button.

### Phase 2: S221 4D Construction Streaming (free — just sort the query)
- No new UI. Add ORDER BY CASE to the stream query, sorting elements by
  construction phase: Substructure → Superstructure → MEP → Architecture → Finishes.
- Phase map from `templates/4D_phases.json` (already exists, 30+ ifc_class mappings).
- HUD shows phase names: "SUBSTRUCTURE — 47/1,169" → "SUPERSTRUCTURE — 198/1,169" → ...
- Building assembles itself like a time-lapse. Every viewer load is a 4D animation.
- **Effort:** Low. One SQL CASE + phase detection in streamTick.
- **Full spec:** `prompts/S221_4d_streaming.md`

### Phase 3: S222 Incremental Diff (same drop zone, auto-detect)
- User drops updated IFC for a building that's already cached → variation mode.
- System loads cached base DB first (instant), streams it normally.
- Then overlays delta: green=added, red ghost=removed, yellow=changed.
- HUD: `Variation "Kitchen_v2.ifc" — 12 added, 3 removed, 8 changed`
- No second drop zone. No Compare button. Same import zone handles it.
- **Why after S221:** the 4D streaming makes even the base import captivating.
  Diff builds on top of it — the variation streams in phase order too.
- **Effort:** Medium. GUID set diff in JS, colour override, IndexedDB version detection.
- **Full spec:** `prompts/S222_incremental_diff_streaming.md`

## The story at first sight
1. Drop IFC → building assembles itself phase by phase (4D)
2. Drop updated IFC → see exactly what changed (diff)
3. Same drop zone, same viewer, zero configuration

## Files
- `deploy/dev/streaming.js` — ORDER BY CASE, phase HUD
- `deploy/dev/city.js` — same sort in cityLoadBuilding
- `deploy/dev/import_worker.js` — variation detection
- New: `deploy/dev/diff.js` — GUID set diff, colour overlay, summary panel
- `deploy/landing2.html` — variation badge on building cards
