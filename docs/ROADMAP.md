# BIM OOTB — Roadmap

## Principle
DB = model. Template = view. Browser = runtime. Three concerns, never merged.

## Shipped

| # | What | Files |
|---|------|-------|
| S200-S209b | Browser viewer, site camera, walk mode, mobile UX, modular refactor, 149 tests | `deploy/sandbox/` (16 modules) |

## Next

### S210 — Template + Forex (quick win)
- `boq_charts.html` reads `?tpl=` (JSON template override)
- Forex: USD/EUR column next to RM, configurable rate
- Work Package grouping in Excel export
- Chart images embedded in Excel
- Template JSON schema: rates, forex, grouping, dimensions, charts

### S211 — JSONFormMaker → BIM Template Compiler
- JSONFormMaker ([source](https://github.com/red1oon/JSONFormMaker)) outputs BIM template JSON
- Compile to binary BLOB → `INSERT INTO templates (name, concern, version, compiled)`
- boq_charts.html reads template from DB, no separate file
- Concerns: QS, Factory, Owner, Inspector, Green

### S212 — Prefab Factory
- Pod = BOM. QR per component. Checklist = repurposed issue log.
- Exploded view (BOM children offset along Z)
- QR generator per GUID (sticker sheet)
- `checklist.js` (quality gate per step, photo evidence)
- Pod progress on landing page
- Factory template (production rates, pod grouping, step-level 4D)

### S213 — 2D BOM Editor
- `editor2d.js` — orthographic view, room placement
- BOM editor UI — rooms, areas, adjacencies as recipe
- Live recompile via DAGCompiler rules
- DXF export via `2D_Layout/python/drawing_writer.py`

### S214 — Plugin Marketplace (ADUI Metadata) — THE MULTIPLIER
- Plugin = JSON manifest (ADUI schema from JSONFormMaker) + JS module
- Manifest declares: name, concern, inputs, outputs, template, icon
- Plugin registry = SQLite table in a shared `marketplace.db`
- Landing page renders plugin cards (same pattern as building cards)
- Install = copy folder to `plugins/`, add to manifest, refresh
- No build step, no SDK, no API key, no approval process
- Plugin types:
  - **Template** — rates, forex, grouping (QS, factory, green)
  - **View** — custom chart type, dashboard layout
  - **Tool** — new toolbar button (e.g. clash detector, carbon calc)
  - **Connector** — import/export (IFC, COBie, BCF, CSV)
- Runtime: `plugin_loader.js` reads manifests, injects scripts, wires buttons
- Community publishes via PR to `plugins/` or upload to OCI bucket

**Stable Plugin API (guaranteed surface):**

| Method | What |
|--------|------|
| `APP.db.exec(sql)` | Query extracted DB (sql.js) |
| `APP.libDb.exec(sql)` | Query library DB (sql.js) |
| `APP.scene.add/remove(obj)` | 3D objects (Three.js) |
| `APP.camera` | Current viewpoint |
| `APP.activeBuilding` | Current building name |
| `APP.guidMap[meshId]` | Element metadata for picked mesh |
| `APP.status.textContent` | Status bar message |
| `APP.addToolbarButton(icon, title, fn)` | Register toolbar button |
| `APP.addPanel(id, html)` | Register collapsible panel |
| `APP.on(event, fn)` | Lifecycle hooks (load, pick, stream_done) |

Everything else is standard (sql.js, Three.js, DOM, XLSX, Chart.js). We expose, not wrap.

**Plugin Test Harness:**
- `plugin_test.js` — offline testing module, no browser needed
- Ships a mock `APP` object with in-memory sql.js DB (Duplex as fixture)
- Plugin author runs: `node plugin_test.js plugins/my_plugin/`
- Harness calls plugin's `setup*()` against mock APP
- Logs every `APP.db.exec()` call with SQL + row counts
- Logs every `APP.scene.add()` with object type + vertex count
- Logs every `APP.status` write
- Validates manifest schema (required fields, valid types)
- Validates API usage (no access to internals, only stable surface)
- Output: `plugin_test.log` with §PLUGIN_PASS / §PLUGIN_FAIL per check
- Same pattern as `test_all.js` — run locally, read the log, fix, repeat
- Community plugins must pass harness before merge to registry

**Plugin API (5 block categories, ~30 methods):**
- **Query** — byClass, byStorey, byDisc, byGuid, neighbours, path, schedule, areas, totals
- **Display** — colorBy, highlight, hide, isolate, label, overlay, chart, table, toast
- **Interact** — onPick, onStoreyChange, toolbarButton, contextMenu, prompt, drag
- **Export** — excel, csv, pdf, screenshot, share, qr
- **Store** — get, set, table, sync

**Plugin IDE (browser-based, no install):**
- Block palette (draggable API methods) + code editor (CodeMirror CDN)
- Run against live APP + real Duplex DB (1119 elements as fixture)
- Log panel: §-tagged output per API call with real row counts
- 3D preview: live model with plugin effects applied
- Save → auto-generates manifest.json from code analysis
- Package → zip. Publish → upload to OCI plugins/ bucket
- Duplex = starter fixture. Hospital = scale test.
- AI Assist button: sends user intent + API schema + DB schema to LLM, returns plugin code
- User describes in English, AI writes, IDE verifies, log proves. Standard practice.
- Full spec: `docs/PLUGIN_SDK.md`

## Scenarios (template + DB + browser, no new systems)

| # | Scenario | What's needed |
|---|----------|---------------|
| 1 | Building inspector on phone | Template |
| 2 | Offline construction site | Already works |
| 3 | Owner handover (two files) | Template |
| 4 | Clash detection in SQL | One query |
| 5 | QR per element | QR generator (S212) |
| 6 | Fleet dashboard | Already works (city mode) |
| 7 | Voice to BOM | LLM + BOM API |
| 8 | Diff two buildings | SQL diff |
| 9 | Prefab factory floor | S212 |
| 10 | Insurance claim on-site | Template + site camera |
