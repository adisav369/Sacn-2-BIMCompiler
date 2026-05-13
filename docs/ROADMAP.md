# BIM OOTB — Roadmap

## Principle
DB = model. Template = view. Browser = runtime. Three concerns, never merged.

## Shipped

| # | What | Key files |
|---|------|-----------|
| S200–S209b | Browser viewer, site camera, walk mode, mobile UX, cinematic tour, modular refactor | `deploy/dev/` (16 modules) |
| S210 | BOQ charts, template + forex, work packages, chart images in Excel | `deploy/dev/boq_charts.html` |
| S220 | Browser IFC import (web-ifc WASM, IFC2x3+IFC4, 122K elements proven) | `deploy/dev/import.js` |
| S222–S224 | DB refactor, diff engine, Variation Order Excel (FIDIC Clause 12), versioned IndexedDB v2 | `deploy/dev/` |
| S225b | Localisation — 15 locales, iDempiere _TRL pattern, rates.js single source | `deploy/dev/rates.js`, `deploy/dev/locales/` |
| S228 | Drop Zone multi-format: OBJ, STL, DAE, GLB/GLTF, FBX, 3DS. Auto up-axis + scale | `deploy/dev/mesh_import_worker.js` |
| S229a | Guided classification wizard — 6-step flow for non-IFC meshes | `deploy/dev/wizard.js` |
| S229b | IFC export — DB → .ifc download. Pure STEP text builder, 30-test round-trip | `deploy/dev/ifc_export_worker.js` |
| S250 §8 | Share sheet — Save as IFC/DB, Contribute to OOTB, WhatsApp/Email/Copy Link (lazy-loaded) | `deploy/dev/share.js` |

**Security:** Enterprise authentication via 3-layer cryptographic verification — see [`docs/EnterpriseAuthentication.md`](EnterpriseAuthentication.md).

**Tests:** 72/72 Playwright E2E (desktop), 53/55 pure-function. Suite: `deploy/dev/tests/`.

## Next

### Spatial ERP OOTB — Every Record Has a Place
The engine is domain-agnostic. WMS (warehouse pick/putaway), POS (store planogram),
MFG (shop floor work orders), logistics (hub network), and back-office (documents
as spatial cabinets with TikTok-style swipe UX). Five tables, one state machine,
zero install. See [SpatialERP_OOTB.md](SpatialERP_OOTB.md).

### S211 — NLP Query DSL (harden + browser port + voice)
Port the Python NLP DSL (`federation/dataintelligence/nlp/`) to browser JS running against sql.js.
No LLM, no API — pure keyword intent classification + pattern-matched SQL generation.
Voice input via Web Speech API (built into Chrome/Safari — no server, no key, no cost).

**What exists (Python, ~1400 lines):**
- `intent_classifier.py` — 9 intents (COUNT, FIND, TOTAL, LIST, SEARCH, DISCIPLINE, MANUFACTURER, PROPERTY, STOREY), confidence scoring, ambiguity suggestions
- `query_parser.py` — NL → SQL with IFC synonyms (150+ terms), pluralization, storey normalization (EN/MY/DE/FR)
- `query_patterns.py` — 40+ patterns across 8 categories (element_count, manufacturer, property, quantity, cost, material, freetext, discipline)
- `query_executor.py` — execute + format results

**Browser port (`deploy/dev/nlp.js`):**
- Port intent classifier + query patterns to JS (no Python dependency)
- Wire to existing search input (`#search-box` in index.html, currently hidden)
- `db.exec(generatedSQL)` against loaded sql.js DB — zero server round-trip
- Results panel: table below search box, clickable rows highlight elements in 3D
- Ambiguity feedback: if classifier returns suggestions, show them as clickable chips
- Query history: last 10 queries in dropdown, stored in `localStorage`
- Example chip bar: "How many doors?" | "ACMV elements" | "Cost of MEP" (clickable)

**Voice input (`webkitSpeechRecognition` / `SpeechRecognition`):**
- Mic button (🎤) next to search input — tap to listen, tap again to stop
- `continuous: false`, `interimResults: true`, `lang: 'en-US'`
- Interim text shown live in search input (grey italic), final result triggers NLP parse
- On mobile: mic button is primary input (larger, thumb-reachable); keyboard is secondary
- Designed for short byte commands, not sentences (see §Voice Script in `MOBILE_DEPLOY.md`)
- Fallback: if `SpeechRecognition` unavailable, mic button hidden — text input only

**Voice-safe word design:**
Commands use short, phonetically distinct words that speech engines recognise reliably.
Avoid: long sentences, ambiguous homophones, technical jargon the engine mishears.
Pattern: `[SCOPE] [TARGET]` — two to four words max.
- Scope: "floor one", "level two", "all", "this building"
- Target: "doors", "beams", "walls", "lights", "cost", "area"
- Action: "count", "show", "total", "find", "search"
- Example: "floor one doors" → COUNT doors on level 1
- Example: "total cost" → total building cost
- Example: "show ACMV" → list ACMV elements

**Hardening (beyond current Python):**
- SQL injection guard: parameterized templates only (current Python uses `_sanitize_value` regex)
- Unknown query fallback: show "Did you mean...?" with closest pattern match
- Empty result UX: "No {element_type} found. Building has: {available_classes}" from `SELECT DISTINCT ifc_class`
- Cross-building: if city mode, prepend building filter to all generated SQL
- FTS5 graceful degradation: if `elements_fts` table missing, fall back to LIKE queries
- Voice misheard correction: show recognised text + "Not right? [Try again] [Edit]" link
- Test: port Python `__main__` test queries to `test_all.js` (14 queries, expected intent + SQL shape)

**UI — Progressive, non-cluttering:**

Stage 1 — Dormant: Search icon (🔍) in toolbar. One button. Nothing else visible.

Stage 2 — Search bar: Click 🔍 → slim bar slides in below toolbar (not a panel).
  Contains: text input + 🎤 mic button + example chips (3 most relevant for current building).
  Auto-focuses. Press Enter or tap mic. No extra panels, no menus.

Stage 3 — Results toast: Query runs → results appear as a floating toast card (bottom-centre).
  Shows: summary line ("47 doors on Level 1") + [Show in 3D] button.
  Auto-dismisses after 8s unless pinned. Tapping [Show in 3D] highlights elements + dismisses.

Stage 4 — Results table (on demand): If user taps the summary line, toast expands to
  scrollable table (max 6 rows visible, rest scrollable). Click row → fly to element.
  [Pin] keeps table open. [x] dismisses entirely.

Stage 5 — History: Arrow-down on empty search bar → dropdown of last 5 queries.
  Each shows query + result count. Click to re-run.

**Dismissed = gone.** No residual panels, no orphaned UI. Each stage cleans up the previous.
Mobile: toast card is full-width at bottom. Desktop: card is 350px, bottom-right.

### S211a — Clash Detection (template-driven, R-tree bbox)

!!! success "SHIPPED — S245c/d"
    Browser-native clash detection is live. 12 discipline-pair rules, storey-scoped progressive queries, clash matrix, in-viewer review workflow, HTML report. See **[Clash Detection](CLASH_DETECTION.md)** for the full feature guide.

Port the Python clash detector (`federation/clash/detector.py`) to browser JS.
R-tree bbox collision against sql.js — same algorithm, same O(n log n) with spatial prefiltering.

**Clash template (JSON, user-configurable):**
```json
{
  "name": "Default Coordination",
  "tolerance_mm": 10,
  "pairs": [
    { "disc_a": "STR", "disc_b": "ACMV", "tolerance_mm": 25 },
    { "disc_a": "STR", "disc_b": "ELEC", "tolerance_mm": 15 },
    { "disc_a": "STR", "disc_b": "PLB",  "tolerance_mm": 20 },
    { "disc_a": "ARC", "disc_b": "MEP",  "tolerance_mm": 10 }
  ],
  "ignore_classes": ["IfcOpeningElement", "IfcSpace"],
  "ignore_same_storey": false,
  "severity": {
    "hard":   { "clearance_mm": 0,   "color": "0xff0000" },
    "soft":   { "clearance_mm": -50,  "color": "0xff8800" },
    "near":   { "clearance_mm": -150, "color": "0xffff00" }
  }
}
```
- Template stored in `localStorage` or loaded from `?clash_tpl=` URL param
- Settings panel: tolerance slider, discipline pair checkboxes (same layout as Bonsai federation addon)
- Per-pair tolerance overrides default (STR-ACMV ducts need wider tolerance than STR-ELEC conduit)

**Algorithm (JS port of `detector.py`):**
- sql.js has no R-tree virtual table → use `elements_rtree` as regular table with bbox range queries
- For each element in disc_a: `SELECT FROM elements_rtree WHERE minX <= ? AND maxX >= ? ...` filtered to disc_b
- `check_bbox_clash()` identical to Python: expand by tolerance, check 3-axis overlap, compute clearance
- Progress: status bar percentage during scan

**Visualization:**
- Clash pairs coloured by severity (hard=red, soft=orange, near=yellow) — `emissive` on both meshes
- Clash list panel (scrollable, sortable by clearance/discipline/class)
- Click clash row → fly-to midpoint of pair, isolate both elements, show info box:
  ```
  ┌─ CLASH #47 ─────────────────────┐
  │ STR IfcBeam ↔ ACMV IfcDuct     │
  │ Clearance: -12.3 mm (HARD)      │
  │ Overlap: 0.025 m³               │
  │ Storey: Level 2                  │
  │ [Resolve] [Ignore] [Export BCF]  │
  └──────────────────────────────────┘
  ```
- Resolve/Ignore status persisted in `localStorage` (or IndexedDB)
- Export: clash list → Excel (reuse `excel.js` pattern) or BCF XML

**UI:** Clash icon (⚡) in toolbar. Badge shows count. Same toggle pattern as Issues.

### S211b — Heatmap Costing ($ icon → colour by value)
Colour elements by any numeric property. Continuous gradient mapped to geometry already in scene.

**UI:** Dollar icon ($) in toolbar → opens heatmap selection panel:
```
┌─ Heatmap ──────────────────────────┐
│ Colour by: [Cost ▾]               │
│   ○ Cost (RM)    ○ Area (m²)      │
│   ○ Volume (m³)  ○ Custom field   │
│                                    │
│ Gradient: 🟢───🟡───🔴            │
│ Min: 0    Max: 50,000 (auto)      │
│ Legend: [Show] [Hide]              │
│                                    │
│ [Apply] [Clear]                    │
└────────────────────────────────────┘
```

**Data source:**
- Cost: `simple_qto.total_cost_rm` joined to `elements_meta.guid` via `ifc_class`
- Area/Volume: `simple_qto.total_quantity` where `measurement_type` = AREA/VOLUME
- Custom: `element_properties.property_value` (numeric cast) for user-chosen property name
- If `simple_qto` absent, show "No QTO data — run 4D/5D first" with link to BOQ

**Rendering:**
- `THREE.Color.lerpColors()` green→yellow→red based on normalized value (0→1)
- Set `mesh.material.color` per element, preserve original in `userData.origColor`
- Floating legend overlay (gradient bar with min/max labels, positioned bottom-right)
- Hover: show element value in tooltip (reuse hover highlight from `tools.js`)
- Clear: restore `userData.origColor` on all meshes

**Integration with NLP (S211):** Query "cost of ACMV" → auto-applies ACMV heatmap

### S211c — Post-it Notes (3D pinned annotations)
Pin text notes to 3D world positions. Store in IndexedDB. Export to Excel/BCF.

**UI:** Notes icon (📝) in toolbar → opens Notes panel:
```
┌─ Notes ────────────────────────────┐
│ [+ Post-it] [Export] [Clear All]  │
│                                    │
│ 📌 "Check fire seal" — Level 2    │
│    STR IfcBeam · 2026-04-23       │
│ 📌 "Wrong material" — Level 1     │
│    ARC IfcWall · 2026-04-22       │
│ 📌 "Duct clearance OK" — Level 3  │
│    ACMV IfcDuct · 2026-04-23      │
└────────────────────────────────────┘
```

**Workflow:**
1. Click "Post-it" button → cursor changes to crosshair
2. Click element in 3D → raycaster gets hit point + GUID (reuse `picking.js` pattern)
3. Note input — two modes:
   - **Type:** textarea popup (desktop default)
   - **Voice:** hold 🎤 button → `MediaRecorder` captures audio → auto-transcribed via
     `SpeechRecognition` → text shown for review → [Save] / [Re-record]
     Audio blob also stored alongside text (playback on click).
     On mobile, voice is primary — bigger 🎤 button, textarea secondary.
4. Creates:
   - Yellow sprite at hit.point (Three.js `Sprite` with `CanvasTexture`, capped at 50 notes visible)
   - Entry in IndexedDB `notes` store:
     `{ id, guid, text, audio: Blob|null, point: {x,y,z}, timestamp, building, storey, disc, ifc_class }`
5. Click existing sprite → show note popup with [Edit] [Delete] [▶ Play] (if audio attached)

**Voice note details:**
- `MediaRecorder` API (audio/webm) — records actual audio, stored as Blob in IndexedDB
- `SpeechRecognition` runs in parallel — provides text transcription for search/export
- Playback: `<audio>` element in note popup, no external player
- Export includes text transcription (audio stays local — too large for Excel/BCF)
- Max 30 seconds per note (auto-stops, prevents accidental long recordings)
- Visual feedback: pulsing red dot on sprite while recording, mic button turns red

**Export:**
- Excel: same pattern as `excel.js` — columns: ID, Text, GUID, Building, Storey, Discipline, Class, X, Y, Z, Timestamp
  (text column contains voice transcription if voice note)
- BCF: each note → BCF Topic with viewpoint (camera position + direction). Interops with Revit/Solibri/BIMcollab.
- Clear All: confirm dialog → delete all from IndexedDB + remove sprites

**Persistence:** IndexedDB keyed by DB filename. Notes survive page reload. Different buildings = different note sets.
Audio blobs stored in same IndexedDB (binary, ~50KB per 30s recording). No server upload.

### S211d — Revision Diff (on-demand delta, not dual load)
Compare two versions of the same building. Loads diff only — not both full models.

**Approach: SQL EXCEPT on metadata, geometry load on demand**
- User loads current DB normally (already streamed)
- "Compare" button → file picker for older DB
- Open older DB as second sql.js instance (`db2`)
- Diff query (runs instantly, no geometry):
  ```sql
  -- Added elements (in new, not in old)
  SELECT guid FROM elements_meta EXCEPT SELECT guid FROM db2.elements_meta
  -- Removed elements (in old, not in new)
  SELECT guid FROM db2.elements_meta EXCEPT SELECT guid FROM elements_meta
  -- Changed elements (same GUID, different properties)
  SELECT a.guid FROM elements_meta a JOIN db2.elements_meta b ON a.guid = b.guid
  WHERE a.element_name != b.element_name OR a.material_name != b.material_name
        OR a.storey != b.storey OR a.discipline != b.discipline
  ```
- sql.js ATTACH not supported → load both DBs, run EXCEPT via JS Set operations on GUID arrays

**Visualization (diff-only geometry loading):**
- Added (green): load geometry from new DB, colour green. These are new meshes.
- Removed (red): load geometry from OLD DB (`db2`), colour red with 50% opacity. Ghost overlay.
- Changed (yellow): already loaded from new DB, just recolour yellow.
- Unchanged: leave as-is (already streamed from current DB)
- Only removed elements require additional geometry load — minimal overhead.

**UI:**
- Compare button (⇔) appears after streaming complete
- Diff summary panel:
  ```
  ┌─ Revision Diff ──────────────────┐
  │ 🟢 Added:   47 elements          │
  │ 🔴 Removed: 12 elements          │
  │ 🟡 Changed: 89 elements          │
  │ ⚪ Same:    1,021 elements        │
  │                                   │
  │ [Show Added] [Show Removed]       │
  │ [Show Changed] [Show All]         │
  │ [Export Diff Excel] [Clear]       │
  └───────────────────────────────────┘
  ```
- Click category → isolate those elements (hide others at 0.1 opacity)
- Click individual row → fly-to element
- Export: Excel with columns: Status, GUID, Class, Name, Storey, Old Value, New Value

**Prerequisite:** None (does NOT depend on S217 edit_log). Pure SQL set difference on GUIDs.

### S212 — JSONFormMaker → BIM Template Compiler
- JSONFormMaker ([source](https://github.com/red1oon/JSONFormMaker)) outputs BIM template JSON
- Compile to binary BLOB → `INSERT INTO templates (name, concern, version, compiled)`
- boq_charts.html reads template from DB, no separate file
- Concerns: QS, Factory, Owner, Inspector, Green

### S213 — Prefab Factory
- Pod = BOM. QR per component. Checklist = repurposed issue log.
- Exploded view (BOM children offset along Z)
- QR generator per GUID (sticker sheet)
- `checklist.js` (quality gate per step, photo evidence)
- Pod progress on landing page
- Factory template (production rates, pod grouping, step-level 4D)

### S214 — 2D BOM Editor
- `editor2d.js` — orthographic view, room placement
- BOM editor UI — rooms, areas, adjacencies as recipe
- Live recompile via DAGCompiler rules
- DXF export via `2D_Layout/python/drawing_writer.py`

### S215 — Plugin Marketplace (ADUI Metadata) — THE MULTIPLIER
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

### S216 — Property Editing + Save DB (viewer → tool)
- `APP.db.exec("UPDATE ...")` on in-memory sql.js DB
- `A.interact.prompt()` on element pick → edit material, name, status, custom fields
- Save: download modified DB as file, or push to OCI
- Changes tracked in `edit_log` table (guid, field, old, new, timestamp, user)
- No schema change needed — sql.js UPDATE on existing tables

### S217 — IFC Export (tool → interop)
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

### S218 — Change Tracking + Version History (interop → team)
- `edit_log` table tracks every UPDATE (guid, field, old_value, new_value, timestamp, user)
- Version = DB snapshot saved to IndexedDB with timestamp
- Diff two versions: SQL EXCEPT between snapshots → added/removed/changed elements
- Colour diff in viewer: green=added, red=removed, yellow=changed
- Export diff report to Excel
- Simple multi-user: download DB → edit → upload. edit_log enables manual merge.
- Future: row-level conflict resolution, CRDT for real-time (spec only, not built)

### S219 — Multi-User + Access Control + Federation

**Three levels (Level 1-2 cover 95% of real teams):**

Level 1 — File-level turns (works with OCI today):
- `.lock` file in OCI bucket (user + timestamp). Check before download, release on upload.
- One editor at a time, others view read-only with their own templates.
- No server — just OCI object put/get.

Level 2 — Change-level merge (S218 edit_log):
- Multiple users download same version, edit different parts, upload changes.
- `edit_log` table = changeset. Merge = replay logs in timestamp order.
- Conflict = same GUID + same field by two users → last-write-wins or manual pick.
- Same concept as Bentley iTwin changesets, but SQLite files not proprietary server.

Level 3 — Real-time collaboration (future, spec only):
- Yjs CRDT (30KB CDN) or WebRTC peer-to-peer. Not needed for most teams.

**Access control via templates (no server-side auth):**
- Template defines: visible disciplines, visible storeys, can_edit, can_export, can_run_rules
- Different template = different role. Swap template = swap role.
- No SSO/SAML needed — template IS the access filter.

**ISO 19650 audit compliance:**
- `edit_log` table satisfies ISO 19650 information management audit trail
- Every change: who (user), what (guid + field), when (timestamp), before/after (old/new value)
- Version snapshots prove state at any point in time
- Auditor queries the log directly with SQL — no proprietary tool needed
- Export audit trail to Excel for submission

**Federation:**
- Load multiple extracted DBs (different disciplines) into one viewer
- Already works — city mode loads multiple buildings. Same pattern for disciplines.
- STR engineer's DB + MEP engineer's DB → one coordinated scene
- Clash detection across federated DBs = cross-DB spatial query

### S220 — Browser IFC Import (self-service onboarding) ✓ SHIPPED
- Drag-and-drop IFC file onto landing page → extract to DB entirely in browser
- Uses `web-ifc` (WebAssembly IFC parser, MIT, ~2MB CDN) — no server, no Python
- Extraction mirrors `extractIFCtoDB_open.py` output schema (same tables, same contract)
- Pipeline: `File → ArrayBuffer → web-ifc parse → walk spatial tree → populate sql.js DB`
- Tables populated: `project_metadata`, `spatial_structure`, `elements_meta`, `element_transforms`, `base_geometries` (tessellated BLOBs), `surface_styles`, `material_layers`, `simple_qto`
- Progress bar per phase: parsing IFC (30%) → extracting elements (50%) → tessellating geometry (20%)
- Output: download extracted DB file, or stream directly into viewer (no save needed)
- Large file handling: Web Worker for parsing (no UI freeze), chunked element processing
- Tested against: IFC2x3 (Revit, ArchiCAD), IFC4 (Bonsai, Tekla), IFC4x3
- Fallback for unsupported geometry: log skipped elements, show count in summary
- **Exit criterion:** user drops any valid IFC, gets a viewable DB in <60s for <50MB files
- Replaces: CLI `onboard_ifc.sh` for simple cases. CLI remains for batch/scripted pipelines.

### S221 — 4D Construction Animation (timeline playback)
- Auto-play mode on 4D timeline — elements appear as construction date advances
- Data source: `construction_schedule` table (already populated by `nD_engine.py`)
- Playback controls: play/pause, speed (1×/2×/5×/10×), scrub slider, date display
- Element visibility: hidden before start_date, ghost (0.3α) during construction, solid after end_date
- Phase colouring: each discipline gets its construction colour (matches BOQ charts palette)
- Camera path: auto-orbit or user-defined waypoints (click to set, drag to reorder)
- Construction sequence: Substructure → Superstructure → MEP Rough-in → Architecture → MEP Final → Finishes (from `phase` column)
- Overlay: running cost accumulator (5D) ticking alongside 4D date — shows spend-to-date
- Export: canvas capture → WebM video download (MediaRecorder API, no server)
- Shareable: URL with `?t=2026-03-15` deep-links to specific construction date
- **Exit criterion:** Hospital 224-task schedule plays as smooth animation, exportable as video
- Replaces: Synchro ($25K/yr), Navisworks TimeLiner ($8K/yr)

### S222 — Incremental Diff Extraction (Bonsai re-export)
- Re-export IFC from Bonsai → compiler extracts only the delta against previous DB
- Algorithm: GUID-based set comparison (same as S211d but at extraction time)
  - New GUIDs → INSERT into extracted DB
  - Missing GUIDs → mark as `status='REMOVED'` (soft delete, geometry retained for diff view)
  - Same GUID, different properties/geometry → UPDATE + log old values in `change_log` table
- `change_log` table: `(guid, field, old_value, new_value, ifc_version, timestamp)`
- Geometry diff: hash comparison on tessellated BLOBs — only re-tessellate changed elements
- Browser reflects changes immediately: green/red/yellow overlay (reuses S211d visualization)
- Workflow: Bonsai edit → IFC export → `extractIFCtoDB_open.py --diff previous.db` → updated DB → browser auto-shows delta
- Browser-side (S220): if user drops a new IFC and a previous DB exists, auto-diff mode
- Backward compatible: new DB works standalone without previous version. Diff is additive metadata.
- **Exit criterion:** modify 5 elements in Bonsai, re-export, see exactly those 5 highlighted in browser

### S223 — Designer Presets (common changes without Bonsai)
- Preset = named recipe: target selector (SQL WHERE) + action (UPDATE fields or swap product)
- Built-in preset categories:
  - **Finishes:** wall paint colour, floor material, ceiling type — dropdown from `surface_styles`
  - **Doors/Windows:** swap type (single→double, sliding→hinged) — replaces `product_type` + geometry BLOB
  - **Furniture:** swap product from catalogue (desk→standing desk, chair→armchair) — BOM child replacement
  - **MEP equipment:** swap unit (AC split→cassette, basin→sensor basin) — product + clearance zone update
  - **Spatial:** room name, function, department — `elements_meta` UPDATE
- Preset library: JSON definitions in `presets/` folder, loaded as plugin (S215 compatible)
- UI: pick element → right-click → "Apply Preset" → category → specific preset → preview → confirm
- Preview: ghost overlay showing before/after side by side (split opacity)
- Cascade: preset triggers BOQ recalc (quantity/cost update), schedule impact shown in toast
- Community presets: users publish preset packs (country-specific finishes, manufacturer catalogues)
- Geometry presets use pre-tessellated BLOBs from a preset library DB — no modelling engine needed
- **Exit criterion:** user swaps 3 door types and a floor finish, sees updated BOQ totals, no Bonsai involved
- Replaces: round-trip to Blender for the 80% of changes that are data, not geometry

### S224 — Clash Detection on Diff (auto-clash on variation import)
- When S222 diff detects added/changed elements, auto-run S211a clash detection on the delta
- Only check new/changed elements against existing elements — not full O(n²) rescan
- Clash report embedded in Variation Order Excel (separate sheet: "Clashes")
- Viewer: clash pairs highlighted alongside diff colours (red emissive on both clashing elements)
- Clash count shown in diff summary panel: `⚡ 3 clashes detected`
- Click clash → fly-to, show both elements, clearance value, discipline pair
- No manual trigger — clash runs automatically whenever a variation is imported
- Uses same clash template from S211a (`tolerance`, `pairs`, `severity`)
- **Why this matters:** The variation didn't just change cost — it introduced conflicts. The QS and coordinator see both in one report.
- **Exit criterion:** Import MEP_v2 with a duct that passes through a wall → clash auto-detected, shown in viewer + Excel
- Replaces: Solibri ($15K/yr), Navisworks Clash Detective ($8K/yr)

### S225 — QR Code per Element → Scan → Full History
- Generate QR sticker sheet from viewer: select elements → "Print QR" → PDF with one QR per GUID
- QR URL: `{viewer_url}?guid={GUID}` — opens viewer, flies to that element
- Element history panel (on scan): which variation introduced it, cost, phase, approval status, site cam photos
- History assembled from: `change_log` table (S222), issues log (S210), site cam photos (S204)
- Print formats: A4 sheet (20 QRs), label printer (single), sticker roll
- Site workflow: print QR sheet → stick on physical elements during construction → scan to inspect/report
- **Why this matters:** Bridges digital twin to physical site. No other browser tool does this.
- **Exit criterion:** Print QR for 5 elements, scan on phone, see full history for each
- Replaces: BIM Track ($), manual tagging systems

### S226 — Regulatory Auto-Check on Import (rule checker at extraction)
- After S220 import extracts IFC to DB, auto-run rule checker before card appears
- Rules from country pack JSON (`locales/MY.json` → UBBL, `locales/GB.json` → Building Regs)
- Each rule = SQL query + pass/fail condition (same pattern as S211a Rule Checker plugin)
- Card shows compliance badge: `✅ 247 PASS · ⚠ 3 WARN · ❌ 1 FAIL`
- Click badge → compliance panel with rule-by-rule results
- Click any fail → fly-to element, show rule text + what's wrong
- Rules cover: fire escape distance, corridor width, accessibility, MEP clearance, structural spans
- **No separate tool.** Compliance is part of the import pipeline.
- Export: compliance report Excel (rule, status, element GUID, storey, value, required value)
- **Why this matters:** Compliance checking today = Solibri license + manual config. Here it's automatic on drop.
- **Exit criterion:** Import a building with a corridor below minimum width → ❌ FAIL badge, fly-to the corridor
- Replaces: Solibri Rule Checker ($15K/yr), manual code review

### S227 — Live Cost Dashboard per Project
- Landing page shows running cost total per project group: `Hospital — RM 67M · 3 variations · +RM 39,700`
- Cost computed from: merged DB element counts × `5D_rates.json` rates
- Variation impact: sum of all Variation Order net impacts (S222)
- Click → full BOQ breakdown (opens `boq_charts.html` with that project's DB)
- Dashboard updates live as parts are imported/merged/varied
- Multi-project view: all projects on landing page, each with cost summary
- Trend sparkline: cost over time (from variation history in IndexedDB)
- **Why this matters:** Project manager's dashboard. Not a viewer — a control room. One page, all projects, all costs.
- **Exit criterion:** Import 3 projects, each with 2 variations → landing shows all 3 with cost totals and net change
- Replaces: Custom Excel dashboards, Procore cost module ($)

### S228 — Drop Zone Multi-Format Import ✓ SHIPPED
OBJ, STL, DAE, GLB/GLTF, FBX, 3DS import via Drop Zone. Auto up-axis + scale.
Guided classification wizard (S229a). IFC export (S229b). See Shipped table above.

### S230 — COBie Export from Merged DB
- After merge (S222), export merged DB as COBie 6-tab Excel (Facility, Floor, Space, Type, Component, System)
- COBie mapping: `elements_meta` → Component, `spatial_structure` → Floor/Space, `product_types` → Type
- Handover package = one click: COBie + IFC + .db in a ZIP
- **Exit criterion:** Merge 3 parts, download COBie Excel, validate with COBie QC tool

### S231 — AI Query on Diff (NLP → change_log)
- Extend S211 NLP DSL to query `change_log` table (S222)
- Natural language: "What changed in MEP on Level 3?" → SQL on change_log → results + fly-to
- Voice command (S211 voice): same queries via microphone
- **Exit criterion:** Voice "what changed on Level 3" → correct results, fly-to first element

### Starter Plugin Pack (ships with S215)
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
| 4D Timeline Slider | Scrub + animated playback of construction phases (see S221) | Medium | Synchro/Navisworks |

**Localization (country config drives everything):**
- **15 locale files shipped** (`deploy/dev/locales/{code}.js`): en_MY, en_US, en_GB, en_AU, ms_MY, de_DE, fr_FR, es_ES, zh_CN, th_TH, ja_JP, ko_KR, ar_SA, pt_BR, id_ID
- Each locale = full _TRL package: labels + currency + rates + labor + equipment + sequences (iDempiere AD_Window_Trl pattern)
- ISO 3166-1 `iso` field → flag emoji. User copies locale → `MyProject_TRL.js` → edits what differs
- `rates.js` = shared rate constants loaded by all pages; locale overrides rates at runtime
- Cascades to: forex in 4D/5D, rates in BOQ, rules in checker, labour in schedule
- Users change locale anytime via `?lang=` URL parameter or flag selector
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
| 4 | Clash detection in SQL | S211a |
| 5 | QR per element | QR generator (S213) |
| 6 | Fleet dashboard | Already works (city mode) |
| 7 | Voice to BOM | LLM + BOM API |
| 8 | Diff two buildings | S211d |
| 9 | Prefab factory floor | S213 |
| 10 | Insurance claim on-site | Template + site camera |
| 11 | External user brings own IFC | S220 (browser import) |
| 12 | Construction progress animation | S221 (4D playback) |
| 13 | Designer updates without Bonsai | S223 (presets) |
| 14 | Bonsai re-export shows only changes | S222 (incremental diff) |
| 15 | Variation introduces clash | S224 (auto-clash on diff) |
| 16 | QR on physical element → scan → history | S225 (QR per element) |
| 17 | Drop IFC, get compliance report | S226 (auto-check on import) |
| 18 | PM sees all project costs on one page | S227 (live cost dashboard) |
| 19 | Government handover from browser | S228 (COBie/IFC export) |
| 20 | "What changed on Level 3?" | S229 (AI query on diff) |

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
