# S222 — Part-Based Import, Merge, Archive

## Naming Convention
User names their IFC files as: `<Project>_<Part>.ifc`

Examples:
```
Hospital_STR.ifc        ← structural
Hospital_ARC.ifc        ← architecture
Hospital_MEP.ifc        ← MEP
Hospital_MEP_v2.ifc     ← MEP revision (variation of part)
```

Parser: split on first `_` → project name = `Hospital`, part = `STR`.
If part contains `_v<N>`, it's a variation of that part.

## IndexedDB Storage

```
Key: {project}              → main merged DB (after merge)
Key: {project}_{part}       → part DB (before merge)
Key: {project}_{part}_v{N}  → variation of part (before merge)
Key: {project}_{part}_arch  → archived part (after merge)
```

## Flow

### 1. Import parts
- User drops `Hospital_STR.ifc` → extracts to DB, stored as `Hospital_STR`
- User drops `Hospital_ARC.ifc` → extracts to DB, stored as `Hospital_ARC`
- User drops `Hospital_MEP.ifc` → extracts to DB, stored as `Hospital_MEP`

### 2. Landing page cards
Each part gets its own card under the project group:

```
┌─ Hospital ──────────────────────────────────────────────┐
│                                                         │
│  ┌─ STR ────────┐  ┌─ ARC ────────┐  ┌─ MEP ────────┐ │
│  │ 1,200 elem   │  │ 3,400 elem   │  │ 2,100 elem   │ │
│  │ 3D View ⚡   │  │ 3D View ⚡   │  │ 3D View ⚡   │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│                                                         │
│  [ Merge All → Hospital ]                               │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 3. Review before merge (viewer level)
- User clicks `3D View ⚡` on any part card → viewer streams that part in 4D order
- User drops a variation (`Hospital_MEP_v2.ifc`) → diff overlay against that part
- User inspects, rotates, checks phases — all at viewer level
- **No merge yet.** User returns to landing page when satisfied.

### 4. Merge (landing page level — commitment)
User clicks **"Merge"** on a reviewed part card. This is a deliberate action.

**Single part merge** — merge one part at a time (not forced to merge all):
- Card shows `Merge?` button per part
- User merges STR first, then ARC later, then MEP when ready
- Each merge folds that part into the project main DB

**"Merge All"** — convenience when all parts are reviewed:

**What happens:**
- Create new sql.js DB (`Hospital` main) if not exists, or append to existing
- For each part DB being merged:
  - Copy all rows from `elements_meta`, `element_transforms`, `element_instances`, `surface_styles`
  - Tag each element with `part` column (or use existing `discipline` if it maps)
  - Geometry hashes are unique per part — no collision
- Save merged DB to IndexedDB as `Hospital`
- Archive each part: rename key from `Hospital_STR` → `Hospital_STR_arch`
- Part cards get "archived" badge (greyed out, collapsed)
- Main merged card appears:

```
┌─ Hospital (merged) ─────────────────────────────────────┐
│  6,700 elements · 3 parts · STR + ARC + MEP            │
│  3D View ⚡                                             │
│                                                         │
│  Archived: STR (1,200) · ARC (3,400) · MEP (2,100)    │
└─────────────────────────────────────────────────────────┘
```

### 4. Variations after merge
- User drops `Hospital_MEP_v2.ifc`
- System detects: project=Hospital, part=MEP, variation=v2
- Loads merged `Hospital` DB as base
- Extracts MEP_v2, diffs against MEP elements in merged DB (by GUID)
- Shows diff overlay: green/red/yellow for MEP changes only
- HUD: `Variation MEP_v2 — 8 added, 2 removed, 15 changed`
- User can then **"Apply Variation"** → updates merged DB, archives MEP_v2

## Merge algorithm

```js
async function mergePartsIntoDB(projectName, parts) {
  const mergedDb = new SQL.Database();

  // Create tables (same schema as extracted DBs)
  mergedDb.run(`CREATE TABLE elements_meta (...)`);
  mergedDb.run(`CREATE TABLE element_transforms (...)`);
  mergedDb.run(`CREATE TABLE element_instances (...)`);
  mergedDb.run(`CREATE TABLE surface_styles (...)`);

  for (const part of parts) {
    const partDb = await loadFromIndexedDB(`${projectName}_${part}`);

    // Copy all rows, tagging with part name
    const rows = partDb.exec("SELECT * FROM elements_meta");
    for (const row of rows[0].values) {
      mergedDb.run("INSERT INTO elements_meta VALUES (...)", row);
    }
    // Same for transforms, instances, styles

    // Archive the part
    await archiveInIndexedDB(`${projectName}_${part}`);
  }

  // Save merged DB
  await saveToIndexedDB(projectName, mergedDb);
  return mergedDb;
}
```

## Card button states

| State | Card shows | Buttons |
|-------|-----------|---------|
| Single part imported | `Hospital_STR · 1,200 elements` | `3D View ⚡` · `Merge?` |
| Multiple parts, same project | Project group with part sub-cards | `3D View ⚡` · `Merge?` per part + `Merge All` |
| Part merged | Part card archived (greyed, collapsed) | `Restore` |
| Main DB exists | `Hospital · 6,700 elements` | `3D View ⚡` · `⬇ .db` |
| Variation dropped on part | Diff badge on part card | `3D View ⚡` (shows diff) |
| Variation reviewed & good | Same part card | `Merge?` (folds variation into main) |

## Files
- `deploy/landing2.html` — project grouping, merge button, archive badges
- `deploy/dev/import_worker.js` — parse `<project>_<part>` naming
- New: `deploy/dev/merge.js` — merge logic, IndexedDB key management, archive
- `deploy/dev/diff.js` — variation detection against merged DB

## Download .db (the deliverable)
- Every card with a DB (part or merged) gets `⬇ .db` button
- Downloads the sql.js DB as a real SQLite `.db` file
- Merged DB downloads as `{Project}_extracted.db` + `{Project}_library.db` (two-file pair)
- Same format as every building in `deploy/buildings/` — drop into any BIM OOTB instance
- Share via email, upload to OCI, open with any SQLite tool
- The browser is the workbench. The `.db` file is the deliverable.

```js
function downloadDb(dbName) {
  const data = db.export();  // sql.js → Uint8Array
  const blob = new Blob([data], { type: 'application/octet-stream' });
  const link = document.createElement('a');
  link.download = dbName + '_extracted.db';
  link.href = URL.createObjectURL(blob);
  link.click();
}
```

## Acceptance
- Import Hospital_STR.ifc + Hospital_ARC.ifc + Hospital_MEP.ifc → 3 part cards grouped
- Click "Merge All" → one merged card, parts archived
- Drop Hospital_MEP_v2.ifc → diff overlay on merged view, MEP changes highlighted
- Archived parts show greyed, can be restored
