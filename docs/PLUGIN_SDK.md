# BIM OOTB — Plugin SDK

## 1. Overview

A plugin is one JS file + one JSON manifest. No build step, no SDK install, no API key.
The browser IDE lets you write, test, and publish plugins without leaving the browser.

**IDE:** `deploy/sandbox/ide.html`
**Test harness:** `node plugin_test.js plugins/my_plugin/`
**Fixture DB:** Duplex (1119 elements, 2.8MB)

## 2. Plugin Structure

```
plugins/my_plugin/
  manifest.json       ← declares name, icon, type, entry point
  plugin.js           ← function setupMyPlugin(A) { ... }
```

### manifest.json

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0",
  "author": "you",
  "icon": "🔍",
  "concern": "QS",
  "type": "tool",
  "toolbar_button": true,
  "requires": ["db"],
  "entry": "plugin.js",
  "description": "One line — what it does"
}
```

Fields: `id` (unique slug), `type` (tool | template | view | connector), `concern` (QS | Factory | Owner | Inspector | Green | General), `requires` (db | libDb | scene — what APP objects the plugin uses).

### plugin.js

```js
function setupMyPlugin(A) {
  // A = the APP object with full API access
  // Register buttons, hooks, panels here
}
```

Convention: function name = `setup` + PascalCase of `id`. The plugin loader calls it automatically.

## 3. Tutorial — Your First Plugin (60 Seconds)

### Step 1: Open the IDE

Open `ide.html`. Duplex building loads in the preview panel. Code editor has a starter template.

### Step 2: Paint all walls blue

```js
function setupMyPlugin(A) {

  A.interact.toolbarButton('🧱', () => {
    const walls = A.query.byClass('IfcWall');
    A.display.colorBy(el =>
      el.ifc_class === 'IfcWall' ? 0x4488ff : null
    );
    A.display.toast(walls.length + ' walls');
  });

}
```

### Step 3: Click Run

Log panel:
```
§PLUGIN_LOAD setupMyPlugin
§TOOLBAR added 🧱 button
§READY click 🧱 to activate
```

### Step 4: Click the 🧱 button

Log panel:
```
§QUERY byClass('IfcWall') → 38 rows  0.2ms
§DISPLAY colorBy applied to 1119 elements
§DISPLAY toast "38 walls"
§PLUGIN_PASS all API calls valid
```

Preview: 38 walls are blue. Everything else unchanged.

### Step 5: Add a table

Add after the toast line:
```js
    const byStorey = A.query.totals('storey', 'count', { where: "ifc_class='IfcWall'" });
    A.display.table(byStorey, 'Walls per Storey');
```

Click Run, click 🧱. A sortable table panel appears.

### Step 6: Save and Package

- **Save** → writes `plugins/my_plugin/plugin.js` + auto-generates `manifest.json`
- **Package** → downloads `my_plugin.zip`
- **Publish** → uploads to OCI plugins/ bucket, appears on marketplace

## 4. Advanced Example — Progress Tracker with Site Photos

Popular demand: colour elements by construction progress, log photos per element, export weekly report.

```js
function setupProgressTracker(A) {

  // Persistent storage — survives page reload
  const STATUS = { NOT_STARTED: 0, IN_PROGRESS: 1, COMPLETE: 2, DEFECT: 3 };
  const COLORS = { 0: 0xcc4444, 1: 0xffaa00, 2: 0x44cc44, 3: 0xff00ff };
  const LABELS = { 0: 'Not Started', 1: 'In Progress', 2: 'Complete', 3: 'Defect' };

  // --- Toolbar button: toggle progress view ---
  A.interact.toolbarButton('📊', () => {
    A.display.colorBy(el => {
      const s = A.store.get('progress_' + el.guid);
      return s != null ? COLORS[s] : null;
    });

    // Summary by status
    const all = A.query.totals('discipline', 'count');
    const summary = Object.entries(LABELS).map(([k, label]) => {
      const guids = A.store.keys('progress_').filter(key => A.store.get(key) == k);
      return { Status: label, Count: guids.length };
    });
    A.display.table(summary, 'Progress Summary');
  });

  // --- Click element: set status + take photo ---
  A.interact.onPick(el => {
    A.interact.prompt([
      { name: 'status', type: 'select', label: 'Status',
        options: Object.entries(LABELS).map(([k, v]) => ({ key: k, display: v })) },
      { name: 'photo', type: 'camera', label: 'Site Photo (optional)' },
      { name: 'notes', type: 'text', label: 'Notes' }
    ]).then(r => {
      A.store.set('progress_' + el.guid, parseInt(r.status));
      if (r.photo) A.store.set('photo_' + el.guid, r.photo);
      if (r.notes) A.store.set('notes_' + el.guid, r.notes);
      A.display.toast(el.name + ' → ' + LABELS[r.status]);
    });
  });

  // --- Export weekly report ---
  A.interact.toolbarButton('📋', () => {
    const elements = A.query.byClass('*');
    const rows = elements.map(el => ({
      GUID: el.guid,
      Class: el.ifc_class,
      Name: el.name,
      Storey: el.storey,
      Discipline: el.discipline,
      Status: LABELS[A.store.get('progress_' + el.guid) || 0],
      Notes: A.store.get('notes_' + el.guid) || '',
      Has_Photo: A.store.get('photo_' + el.guid) ? 'Yes' : 'No'
    }));
    A.export.excel(rows, 'Progress_Report_' + new Date().toISOString().slice(0, 10));
    A.display.toast('Exported ' + rows.length + ' elements');
  });
}
```

Log output when running:
```
§PLUGIN_LOAD setupProgressTracker
§TOOLBAR added 📊 button
§TOOLBAR added 📋 button
§READY click 📊 for progress view, 📋 for report

[user clicks element]
§INTERACT onPick guid=2O2Fr$t4X7Zf8NOew3FLOH class=IfcWall
§INTERACT prompt 3 fields (select, camera, text)
§STORE set progress_2O2Fr$t4X7Zf8NOew3FLOH = 2
§DISPLAY toast "Basic Wall:223 → Complete"

[user clicks 📊]
§QUERY totals(discipline, count) → 3 rows
§STORE keys(progress_) → 47 entries
§DISPLAY colorBy applied to 1119 elements
§DISPLAY table "Progress Summary" 4 rows

[user clicks 📋]
§QUERY byClass(*) → 1119 rows
§STORE read 1119 progress entries, 12 photos, 8 notes
§EXPORT excel "Progress_Report_2026-04-22.xlsx" 1119 rows
§DISPLAY toast "Exported 1119 elements"
§PLUGIN_PASS all API calls valid
```

## 5. API Schema Spec

This schema is designed to be pasted into any AI model (Claude, GPT, etc.) so users can describe what they want in plain English and the AI returns working plugin code.

### Prompt Template for AI-Assisted Plugin Development

```
You are writing a BIM OOTB plugin. The plugin runs in a browser against
a SQLite database containing IFC building data. You have access to the
APP object (referred to as A in the setup function).

THE DATABASE SCHEMA (extracted DB):
  elements_meta: guid TEXT PK, ifc_class TEXT, name TEXT, building TEXT,
                 storey TEXT, discipline TEXT, material TEXT
  element_instances: guid TEXT PK, x REAL, y REAL, z REAL,
                     rx REAL, ry REAL, rz REAL, geometry_hash TEXT
  project_metadata: key TEXT PK, value TEXT
  building_summary: building TEXT, discipline TEXT, count INTEGER,
                    min_x REAL, min_y REAL, min_z REAL,
                    max_x REAL, max_y REAL, max_z REAL

THE API (5 categories, ~30 methods):

QUERY — returns arrays of objects
  A.query.byClass(ifc_class) → [{guid, ifc_class, name, storey, discipline, material, x, y, z}]
  A.query.byStorey(storey_name) → same shape
  A.query.byDisc(discipline) → same shape
  A.query.byGuid(guid) → single element object
  A.query.neighbours(guid, radius_metres) → [{a: element, b: element, dist: number}]
  A.query.path(classA, classB) → [{from: element, to: element, relation: string}]
  A.query.schedule(ifc_class) → [{guid, name, type_name, width, height, properties...}]
  A.query.areas() → [{space_name, storey, area_m2, perimeter_m, height_m}]
  A.query.totals(group_by_field, metric, options?) → [{field_value, count/sum/avg}]

DISPLAY — visual output
  A.display.colorBy(fn) — fn(element) returns hex colour or null (keep original)
  A.display.highlight(guid_array) — yellow glow on listed elements
  A.display.hide(guid_array) — hide elements
  A.display.isolate(guid_array) — hide everything except these
  A.display.label(guid, text) — floating 3D text above element
  A.display.overlay(html_string) — 2D panel overlaid on viewer
  A.display.chart({type, data, label, group, title}) — Chart.js in a panel
  A.display.table(row_array, title) — sortable HTML table in a panel
  A.display.toast(message) — brief notification

INTERACT — user input
  A.interact.onPick(fn) — fn(element) called when user clicks an element
  A.interact.onStoreyChange(fn) — fn(storey_name) called on filter change
  A.interact.toolbarButton(emoji_icon, fn) — adds button, fn called on click
  A.interact.contextMenu([{label, fn}]) — right-click menu items
  A.interact.prompt(field_array) → Promise<{field_name: value}>
    field types: text, number, select, slider, date, camera, checkbox
  A.interact.drag(ifc_class, onDrop_fn) — drag-and-drop from palette

EXPORT — get data out
  A.export.excel(row_array, filename) — .xlsx download via SheetJS
  A.export.csv(row_array, filename) — .csv download
  A.export.pdf(html_string, filename) — print-friendly PDF
  A.export.screenshot(options?) — canvas capture as PNG
  A.export.share(text, blob?) — Web Share API (mobile)
  A.export.qr(guid) — QR code image linking to element in viewer

STORE — persist plugin data (IndexedDB, survives reload)
  A.store.get(key) → value
  A.store.set(key, value)
  A.store.keys(prefix) → [key_strings]
  A.store.table(name, schema) — create in-memory SQL table for plugin use
  A.store.sync(remote_path) — push/pull to OCI bucket

PLUGIN FORMAT:
  function setupPluginName(A) {
    // register buttons, hooks, panels
    // A is the full APP object
  }

RULES:
  - One setup function per plugin
  - Register toolbar buttons for main actions
  - Use A.interact.onPick for element-level actions
  - Use A.store for persistence across reloads
  - Use A.display.toast for user feedback
  - Use A.export for data output
  - Every action must produce a log-visible effect
  - Return working JS — no pseudocode, no placeholders
```

### Example AI Interaction

**User prompt:**
> "I want a plugin that finds all doors without fire ratings and highlights them red. Export a list to Excel."

**AI returns:**
```js
function setupFireRatingChecker(A) {

  A.interact.toolbarButton('🔥', () => {
    const doors = A.query.schedule('IfcDoor');
    const unrated = doors.filter(d => !d.fire_rating || d.fire_rating === 'None');
    A.display.highlight(unrated.map(d => d.guid));
    A.display.colorBy(el =>
      unrated.some(d => d.guid === el.guid) ? 0xff0000 : null
    );
    A.display.table(unrated.map(d => ({
      Name: d.name, Storey: d.storey, Type: d.type_name, Rating: d.fire_rating || 'MISSING'
    })), 'Doors Missing Fire Rating');
    A.display.toast(unrated.length + ' doors without fire rating');
    A.export.excel(unrated, 'Fire_Rating_Audit');
  });

}
```

**User pastes into IDE → Run → sees red doors → exports Excel. Done.**

### AI + IDE Workflow (the standard practice)

**AI Assist modes (user chooses in IDE Settings):**

| Mode | Cost | API key needed | How it works |
|------|------|:-:|---|
| **None** | Free | No | No AI button. User writes JS manually. IDE still works. |
| **Paste Mode** (default) | Free | No | Button copies API schema + user intent to clipboard. User pastes into their own Claude/ChatGPT/any free AI. Pastes response back into IDE. |
| **Claude API** | ~$0.01-0.05/request | Yes (user's own) | IDE calls Claude API directly from browser. Key in localStorage, never sent to OCI. |
| **OpenAI API** | ~$0.01-0.05/request | Yes (user's own) | Same, different endpoint. |
| **Local (Ollama)** | Free | No | IDE calls localhost:11434. Runs on user's machine. Private, offline. |

```
IDE Settings:
  AI Provider: [Paste Mode ▼]     ← default, zero cost
  API Key: [not needed]
  Model: [not needed]
```

**Paste Mode flow (zero cost, zero config):**
```
1. User opens IDE (ide.html)
2. Clicks [🤖 AI Assist] → opens prompt panel
3. Types: "find all doors without fire ratings, highlight red, export Excel"
4. Clicks [📋 Copy Prompt] → clipboard gets API schema + DB schema + user intent
5. User pastes into their own Claude / ChatGPT / any AI (free tier works)
6. AI returns plugin code
7. User pastes response into IDE code editor
8. Clicks [▶ Run]
9. Log panel proves it works (or shows §PLUGIN_FAIL with reason)
10. If fail → describe error to AI → AI fixes → paste → Run again
11. If pass → Save → Package → Publish
```

**API Mode flow (direct, no copy-paste):**
```
1. User opens IDE, sets API key once in Settings
2. Clicks [🤖 AI Assist] → types intent
3. Clicks [Send] → IDE prepends API schema + DB schema automatically
4. AI response lands directly in code editor
5. Clicks [▶ Run] → log proves it
```

The API schema in §5 is the same system prompt regardless of mode. Paste Mode copies it to clipboard. API Mode sends it as the system message. Same result, different plumbing.

**The IDE is mandatory regardless of AI mode because:**
- AI code may have bugs — the log catches them with real data
- AI may use wrong column names — the query runs against real Duplex DB
- AI may call non-existent API methods — the harness rejects them
- The 3D preview shows visual correctness that no AI can guarantee

**The AI writes. The IDE verifies. The log is the proof.**

## 6. Plugin Types Reference

| Type | What it adds | Entry point pattern |
|------|-------------|-------------------|
| **tool** | Toolbar button + action | `toolbarButton` + query/display |
| **template** | Rates, forex, grouping for boq_charts | JSON only, no JS needed |
| **view** | Custom panel or dashboard | `addPanel` + chart/table |
| **connector** | Import/export format | `toolbarButton` + file read/write |

## 7. DB Schema Quick Reference (for plugin authors)

### extracted DB (metadata + transforms)

```sql
-- Every element in the building
SELECT guid, ifc_class, name, building, storey, discipline, material
FROM elements_meta;

-- Position and rotation per element
SELECT guid, x, y, z, rx, ry, rz, geometry_hash
FROM element_instances;

-- Building-level aggregates with bounding boxes
SELECT building, discipline, count, min_x, min_y, min_z, max_x, max_y, max_z
FROM building_summary;

-- Project settings (true north, units, etc.)
SELECT key, value FROM project_metadata;
```

### library DB (geometry BLOBs)

```sql
-- Raw mesh data per unique shape
SELECT geometry_hash, vertices, faces, vertex_count, face_count
FROM component_geometries;

-- vertices = Float32Array BLOB (x,y,z triples)
-- faces = Uint32Array BLOB (triangle indices)
```

### Common queries plugin authors will use

```sql
-- Count by class
SELECT ifc_class, COUNT(*) as qty FROM elements_meta GROUP BY ifc_class ORDER BY qty DESC;

-- All doors on level 2
SELECT * FROM elements_meta WHERE ifc_class='IfcDoor' AND storey='Level 2';

-- Elements within 5m of a point
SELECT *, (ABS(x-10.5)+ABS(y-20.3)+ABS(z-3.0)) as dist
FROM elements_meta m JOIN element_instances i ON m.guid=i.guid
WHERE dist < 5 ORDER BY dist;

-- Discipline summary
SELECT discipline, COUNT(*) as elements,
  MIN(x) as min_x, MAX(x) as max_x
FROM elements_meta m JOIN element_instances i ON m.guid=i.guid
GROUP BY discipline;

-- Floor area (if IfcSpace present)
SELECT name, storey, area FROM elements_meta WHERE ifc_class='IfcSpace';
```
