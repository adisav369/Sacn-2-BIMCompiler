# S220/S222 — Next Session: Three Parallel Workstreams

## DO NOT REMOVE
Scope: Three independent agents, each in own worktree, backward compatible on DEV.
Read the log after every run.

## Context
S220 IFC import is WORKING (commit 788eb47c). IFC2x3+IFC4 proven at 122K elements.
All work targets `deploy/dev/` only. `deploy/sandbox/` is PRODUCTION — never touch directly.
Symlinked files (streaming.js, config.js, etc.) edit the sandbox original via dev symlink.

## Architecture (file ownership per agent)

```
AGENT A — DB Builder Refactor + Auto-Save
  OWN files (no symlink):
    deploy/dev/import.js          ← refactored to use shared builder
    deploy/dev/import_worker.js   ← no changes needed
  NEW file:
    deploy/dev/import_db_builder.js  ← extracted shared DB builder
  TOUCHES (inline script):
    deploy/landing2.html          ← replace inline DB builder with script tag

AGENT B — Incremental Diff + Variation Order Excel
  NEW files (no conflicts):
    deploy/dev/diff.js            ← GUID set diff, change_log, colour overlay
    deploy/dev/variation_order.js ← VO Excel generation (uses excel.js pattern)
  TOUCHES (append only):
    deploy/dev/streaming.js       ← symlink to sandbox/streaming.js
      → add diffOverlay() after base streaming (APPEND to end of file)
    deploy/landing2.html          ← variation detection in import handler
      → add variation badge on card (APPEND to renderImportCards)

AGENT C — Enhanced Viewer Diagnostics + Test Harness
  OWN files:
    deploy/dev/s220_test.js       ← NEW: automated import+viewer test
    deploy/dev/s210_test.log      ← test output
  READ-ONLY (no edits):
    deploy/dev/import_worker.js
    deploy/dev/import.js
    deploy/dev/streaming.js
```

## AGENT A — DB Builder Refactor + Auto-Save

### Goal
Extract the duplicated DB builder (elements_meta + element_transforms + element_instances +
component_geometries) into a single shared file. Then auto-save after import.

### Spec

1. Create `deploy/dev/import_db_builder.js`:
```js
// buildImportDBs(SQL, workerResult) → { extractedDb: ArrayBuffer, libraryDb: ArrayBuffer }
// Single source of truth for import DB schema.
// Both landing2.html and import.js call this.
```

Schema (must match viewer's streaming.js expectations):
```sql
-- extracted DB
project_metadata: key TEXT PRIMARY KEY, value TEXT
elements_meta: guid TEXT PK, ifc_class, element_name, storey, discipline, material_name, material_rgba, building
element_transforms: guid TEXT PK, center_x REAL, center_y REAL, center_z REAL, rotation_x, rotation_y, rotation_z
element_instances: guid TEXT PK, geometry_hash TEXT

-- library DB
component_geometries: geometry_hash TEXT PK, vertices BLOB, faces BLOB, building TEXT
```

2. Update `deploy/dev/import.js`:
   - Remove inline `buildDatabases()` function
   - Import shared builder: `<script src="import_db_builder.js">` loaded before import.js
   - Call `buildImportDBs(SQL, msg)` instead

3. Update `deploy/landing2.html`:
   - Add `<script src="sandbox/import_db_builder.js"></script>` before inline script
   - Replace inline DB builder (lines 664-726) with call to `buildImportDBs(SQL, msg)`
   - Auto-save: remove Save button, auto-download DBs after import completes

### Test
- Import any IFC file via landing page → DBs built correctly (same schema)
- Import via dev page → same result
- Verify with: `sqlite3 downloaded.db ".schema"` matches spec above

### Files touched
- NEW: `deploy/dev/import_db_builder.js`
- EDIT: `deploy/dev/import.js` (remove buildDatabases, use shared)
- EDIT: `deploy/landing2.html` (replace inline builder, auto-save)

---

## AGENT B — Incremental Diff + Variation Order Excel

### Goal
When user drops a 2nd IFC for a building already in cache, auto-detect variation,
compute GUID-based diff, overlay green/red/yellow in viewer, generate VO Excel.

### Spec (from `prompts/S222_incremental_diff_streaming.md`)

1. Create `deploy/dev/diff.js` — loaded by viewer index.html:
```js
function setupDiff(A) {
  // Called after base DB loaded, if variation DB exists
  // A.db = base, A.diffDb = variation (both sql.js instances)

  A.computeDiff = function() {
    const guids1 = new Set(A.db.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));
    const guids2 = new Set(A.diffDb.exec("SELECT guid FROM elements_meta")[0].values.map(r => r[0]));

    A.diffResult = {
      added:   [...guids2].filter(g => !guids1.has(g)),
      removed: [...guids1].filter(g => !guids2.has(g)),
      changed: [...guids2].filter(g => guids1.has(g)).filter(g => {
        // Compare key properties
        const r1 = A.db.exec(`SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='${g}'`);
        const r2 = A.diffDb.exec(`SELECT element_name, material_rgba, storey FROM elements_meta WHERE guid='${g}'`);
        return JSON.stringify(r1[0]?.values[0]) !== JSON.stringify(r2[0]?.values[0]);
      }),
    };
    console.log(`[S222] §DIFF added=${A.diffResult.added.length} removed=${A.diffResult.removed.length} changed=${A.diffResult.changed.length}`);
  };

  A.streamDiffOverlay = function() {
    // After base streaming completes, stream diff elements:
    // - Added (green): load geometry from diffDb library, place with green material
    // - Removed (red ghost): already placed, recolour to red 50% opacity
    // - Changed (yellow): already placed, recolour to yellow
  };
}
```

2. Create `deploy/dev/variation_order.js` — VO Excel export:
   - Reuse `excel.js` (ExcelJS) pattern from BOQ export
   - Columns: Status | GUID | Class | Name | Storey | Discipline | Old Value | New Value | Cost Impact
   - Summary rows: Added total, Removed total, Net Impact, Schedule Impact
   - Auto-generated on every diff — appears as download button on diff summary panel

3. Variation detection in `deploy/landing2.html`:
   - In `handleImportFile()`, after DB build, check if `bim_ootb_imports` already has a record for same building name
   - If yes: store as variation (key = `filename_v{N}`), set `meta.baseKey` pointer
   - Card badge: `v2`, `v3`, etc.

4. Viewer wiring in `deploy/dev/index.html`:
   - Add `<script src="diff.js?v=1"></script>` after streaming.js
   - Add `<script src="variation_order.js?v=1"></script>` after diff.js

### Diff colours
```js
const DIFF_COLORS = {
  added:   { color: 0x44cc44, opacity: 1.0 },   // green solid
  removed: { color: 0xcc4444, opacity: 0.5 },   // red ghost
  changed: { color: 0xcccc44, opacity: 1.0 },   // yellow solid
};
```

### Test
- Import SampleHouse.ifc → card appears
- Modify IFC (rename an element, delete one, add one)
- Re-import → variation detected, diff overlay shown
- VO Excel downloads with correct added/removed/changed counts

### Files touched
- NEW: `deploy/dev/diff.js`
- NEW: `deploy/dev/variation_order.js`
- EDIT: `deploy/dev/index.html` (add script tags — APPEND only)
- EDIT: `deploy/landing2.html` (variation detection — APPEND to handleImportFile)

---

## AGENT C — Test Harness

### Goal
Automated test that validates the full import pipeline by comparing import DBs
against Java-extracted DBs. Runs locally via `node deploy/dev/s220_test.js`.

### Spec

1. Create `deploy/dev/s220_test.js`:
   - For each test IFC file that has a matching Java extraction:
     - Read the Java-extracted DB
     - Read the import-extracted DB (user must Save first, or auto-saved by Agent A)
     - Compare: element count (±10%), class distribution, coordinate ranges, material coverage
   - Test files (available locally):
     - `SampleHouse`: Java at `deploy/buildings/SampleHouse_extracted.db`
     - `FZKHaus`: Java at `DAGCompiler/lib/input/Ifc4_FZKHaus_extracted.db`
   - Output: `deploy/dev/s220_test.log` with §-tagged results

2. Schema validation:
   - Verify tables exist: elements_meta, element_transforms, element_instances, component_geometries
   - Verify columns match viewer expectations
   - Verify no NULL center_x/y/z (all transforms must have values)
   - Verify no 0-byte vertex BLOBs in library

3. Coordinate sanity:
   - Envelope must be 1m-1000m in each axis (catch mm/km scaling bugs)
   - Centroid must not be (0,0,0) unless building is genuinely at origin

### Files touched
- NEW: `deploy/dev/s220_test.js`
- WRITE: `deploy/dev/s220_test.log` (output)

---

## Conflict Avoidance Matrix

| File | Agent A | Agent B | Agent C |
|------|---------|---------|---------|
| `import_db_builder.js` | CREATE | — | — |
| `import.js` | EDIT | — | READ |
| `import_worker.js` | — | — | READ |
| `landing2.html` | EDIT (builder) | EDIT (variation) | — |
| `streaming.js` | — | APPEND | READ |
| `diff.js` | — | CREATE | — |
| `variation_order.js` | — | CREATE | — |
| `index.html` | — | APPEND | — |
| `s220_test.js` | — | — | CREATE |

**Conflict: `landing2.html`** — both A and B edit it.
- **Resolution:** Agent A runs FIRST (refactors builder). Agent B runs AFTER (appends variation detection). Or Agent B works in own worktree and merges after A.

## Launch Order
1. **Agent C** (test harness) — fully independent, no conflicts
2. **Agent A** (DB builder refactor) — edits landing2.html
3. **Agent B** (diff + VO) — edits landing2.html AFTER Agent A merges

Or: Agent A + Agent C in parallel, then Agent B after Agent A completes.

## Deploy Commands (after all agents merge)
```bash
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/import_db_builder.js --name sandbox/import_db_builder.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/import.js --name sandbox/import.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/diff.js --name sandbox/diff.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/variation_order.js --name sandbox/variation_order.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/index.html --name sandbox/index.html --content-type text/html --force
oci os object put --bucket-name bim-ootb-dev --file deploy/dev/streaming.js --name sandbox/streaming.js --content-type application/javascript --force
oci os object put --bucket-name bim-ootb-dev --file deploy/landing2.html --name index.html --content-type text/html --force
```
