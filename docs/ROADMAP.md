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

### S215 — Property Editing + Save DB (viewer → tool)
- `APP.db.exec("UPDATE ...")` on in-memory sql.js DB
- `A.interact.prompt()` on element pick → edit material, name, status, custom fields
- Save: download modified DB as file, or push to OCI
- Changes tracked in `edit_log` table (guid, field, old, new, timestamp, user)
- No schema change needed — sql.js UPDATE on existing tables

### S216 — IFC Export (tool → interop)
- DB → IFC4 STEP file, entirely in browser (string concatenation from SQL)
- Tessellated geometry via IfcTriangulatedFaceSet (vertices/faces BLOBs already stored)
- Full spatial hierarchy from `spatial_structure` + `rel_aggregates`
- Element placement from `element_transforms`
- Materials from `material_layers`, colours from `surface_styles`
- Containment from `rel_contained_in_space`
- NOT parametric round-trip — tessellated coordination model (industry standard for federated models)
- Output: `.ifc` file download. Valid IFC4 readable by Revit, ArchiCAD, Solibri, any IFC viewer.
- Writer walks tables in order:
  1. Project + OwnerHistory + Units (project_metadata)
  2. Site → Building → Storeys → Spaces (spatial_structure)
  3. Elements with placement (elements_meta + element_transforms)
  4. Geometry per element (base_geometries BLOBs → IfcTriangulatedFaceSet)
  5. Materials + surface styles
  6. Containment relationships
- Missing from export: property sets (Pset_*), parametric geometry, type definitions, opening links
- Plugin: `ifc_export/plugin.js` — toolbar button, runs SQL, builds STEP text, downloads file

### S217 — Change Tracking + Version History (interop → team)
- `edit_log` table tracks every UPDATE (guid, field, old_value, new_value, timestamp, user)
- Version = DB snapshot saved to IndexedDB with timestamp
- Diff two versions: SQL EXCEPT between snapshots → added/removed/changed elements
- Colour diff in viewer: green=added, red=removed, yellow=changed
- Export diff report to Excel
- Simple multi-user: download DB → edit → upload. edit_log enables manual merge.
- Future: row-level conflict resolution, CRDT for real-time (spec only, not built)

### S218 — Access Control + Federated View
- Role-based: contractor sees only their discipline, owner sees all
- Implemented via template: template defines visible disciplines/storeys/classes
- No server-side auth needed — template is the access filter
- Federated: load multiple extracted DBs (different disciplines) into one viewer
- Already works partially — city mode loads multiple buildings. Same pattern for disciplines.
- Merge view: STR engineer's DB + MEP engineer's DB → one coordinated scene
- Clash detection across federated DBs = cross-DB spatial query

### Starter Plugin Pack (ships with S214)
Pre-installed plugins that prove the marketplace. All use the plugin API, no core changes.

| Plugin | What | Effort | Replaces |
|--------|------|:---:|----------|
| BCF Export | Issue log → BCF XML zip (interop with Revit/Solibri/BIMcollab) | Low | BIMcollab ($) |
| COBie Export | DB → 6-tab spreadsheet (government handover) | Low | Revit COBie toolkit |
| Colour by Property | Colour elements by material/discipline/status/custom | Low | Solibri/Navisworks |
| Door/Window Schedule | Sortable table of types, sizes, fire ratings | Low | Revit schedules |
| Rule Checker | JSON rule packs (SQL query + pass/fail check) per standard | Medium | Solibri ($15K/yr) |
| Revision Compare | Load two DBs, show added/removed/changed elements | Medium | Navisworks ($8K/yr) |
| Selection Sets | Save/recall named GUID groups | Low | All BIM tools |
| 4D Timeline Slider | Scrub through construction phases, show/hide by date | Medium | Synchro/Navisworks |

**Localization (country config drives everything):**
- Setup script on first launch: pick country → sets currency, rates, rules, standards, templates
- Config = one JSON per country (`locales/MY.json`, `locales/GB.json`, `locales/US.json`)
- Cascades to: forex in 4D/5D, rates in BOQ, rules in checker, labour in schedule
- Users change defaults anytime in Settings
- Community contributes country packs (rates + rules + templates) to marketplace

**Background reports (DB processing, no UI needed):**
- User toggles which reports run: compliance, COBie, carbon, cost variance, schedules, clash
- Trigger: on-change (DB updated), weekly, monthly, or manual
- Output: Excel to project reports folder, silent, no popup
- Reports panel lists generated files for download
- Viewer is optional — DB processing is the core value
- User adds more report types from marketplace, unticks what they don't need
- One shop: headless DB processing + coupled responsive viewer when needed

**Country packs (community-contributed):**
- Malaysia (UBBL, MS 1184, CIDB 2024 rates, JKR)
- UK (Building Regs Part B/M/L, NRM rates)
- US (ADA 2010, CSI MasterFormat, RS Means rates)
- Fire safety, MEP clearance (cross-country)

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
