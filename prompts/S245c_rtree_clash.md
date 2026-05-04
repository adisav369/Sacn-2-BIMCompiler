# S245c — R-tree Spatial Index for Instant Clash Detection

## Context
S245b clash detection works but matrix sphere checks are slow on large buildings
(Terminal 48k elements). Currently using 50×50 random sampling as workaround.
SQLite R-tree module enables O(log N) spatial queries — instant for any building size.

## Problem
Default sql.js WASM is compiled without `SQLITE_ENABLE_RTREE`. The `rtree-sql.js`
npm package is a drop-in replacement with R-tree enabled.

## Plan

### 1. Swap sql.js for rtree-sql.js
- CDN: replace `sql-wasm.js` and `sql-wasm.wasm` URLs in `deploy/dev/loader.js` or `index.html`
- Or: host `rtree-sql.js` WASM files on OCI alongside viewer
- Verify: `db.run("CREATE VIRTUAL TABLE test USING rtree(id, minX, maxX)")` must not throw

### 2. Create R-tree at runtime (measure.js `_ensureClashIndexes`)
```sql
CREATE VIRTUAL TABLE IF NOT EXISTS elements_rtree
  USING rtree(id, minX, maxX, minY, maxY, minZ, maxZ);

-- Populate from element_transforms (IFC coordinates, full-width bbox)
INSERT INTO elements_rtree
  SELECT rowid, center_x - bbox_x/2, center_x + bbox_x/2,
         center_y - bbox_y/2, center_y + bbox_y/2,
         center_z - bbox_z/2, center_z + bbox_z/2
  FROM element_transforms;
```
One-time cost per session. Terminal 48k elements: ~200ms.

### 3. Rewrite clash queries to use R-tree
```sql
-- Matrix sphere check: EXISTS per pair (instant with R-tree)
SELECT 1 FROM elements_rtree a
JOIN elements_meta ma ON a.id = ma.rowid
JOIN elements_rtree b ON b.minX < a.maxX AND b.maxX > a.minX
                     AND b.minY < a.maxY AND b.maxY > a.minY
                     AND b.minZ < a.maxZ AND b.maxZ > a.minZ
JOIN elements_meta mb ON b.id = mb.rowid
WHERE ma.discipline = ? AND mb.discipline = ?
  AND a.id < b.id
LIMIT 1

-- Per-pair paginated query (fast with R-tree)
-- Same structure with LIMIT 30 OFFSET ?
```

### 4. Backward compatible
- If R-tree creation fails (old sql.js without module), fall back to sampling
- Existing DBs need no re-extraction
- No schema changes to stored DB files

## Files to modify
- `deploy/dev/index.html` or `deploy/dev/loader.js` — swap WASM CDN URL
- `deploy/dev/sw.js` — update precache for new WASM URL
- `deploy/dev/measure.js` — R-tree creation in `_ensureClashIndexes`, rewrite queries
- `deploy/dev/elevation.js` — can also use runtime R-tree (currently has fallback)

## Verification
- `§CLASH_RTREE created N rows` — logged on R-tree creation
- `§CLASH_INDEXES` — should include rtree
- Terminal matrix: all spheres should resolve in <1 second total
- SampleHouse/Duplex: no regression

## S245d — Features to Outshine Navisworks / Solibri / Revizto

Navisworks pain points (from industry feedback):
- Spits thousands of raw clashes → "clash-counting exercise, not risk filtering"
- Desktop-only, expensive hardware, steep learning curve
- No in-viewer review workflow — export HTML report, lose context
- No tolerance slider — set once, re-run entire test
- Grouped clashes are manual, not smart

What we already beat them on:
- **Zero install, zero server** — browser, any device, any OS
- **DB-backed** — instant storey/discipline queries, no scene traversal
- **In-viewer review** — status cycle (Reviewed/Resolved/Accepted) with localStorage
- **Live tolerance slider** — adjust and re-query without re-running
- **Clash Matrix** — visual discipline grid, click to drill, no report export needed
- **Actual mesh overlap** — clipped geometry at clash zone, not just a red dot

### Features to add (S245d):

**1. Smart Grouping (auto-cluster nearby clashes)**
Navisworks requires manual grouping. We can auto-group:
- Clashes within 1m of each other → one cluster
- Show cluster count on matrix sphere instead of total
- Click cluster → expand to individual clashes
- Dramatically reduces "clash fatigue" on large buildings

**2. Clash Heatmap overlay**
- Color-code storeys/zones by clash density on the 3D model
- Red zones = high clash concentration → prioritize
- No equivalent in Navisworks (report-only)

**3. Clearance violation detection**
- Not just overlap — detect insufficient clearance (e.g. pipe 5cm from beam)
- Already in `clash_rules.json` severity levels, just need negative-overlap query
- Navisworks calls this "near miss" — requires separate test setup

**4. Trend tracking (cross-session)**
- Store clash counts per pair per date in localStorage
- "Last check: 45 clashes → now 32" — shows progress
- No BIM tool does this without external dashboard

**5. BCF export (future)**
- Industry-standard clash exchange format
- One clash → one BCF topic with viewpoint, camera, elements
- Interop with Solibri, Revizto, BIMcollab

**6. AI-ready: pattern detection**
- Log all clash pairs to structured JSON
- Future: LLM prompt "summarize clashes" or "which storey needs most attention"
- DB + structured data = ready for AI without re-architecture

### Competitive comparison

| Feature | Navisworks | Solibri | Revizto | **BIM OOTB** |
|---------|-----------|---------|---------|-------------|
| Install | Desktop 8GB+ | Desktop | Cloud+Desktop | **Zero** |
| Cost | $3,570/yr | $3,400/yr | $700/yr | **Free** |
| Clash matrix | Plugin ($) | Built-in | No | **Built-in** |
| Live tolerance | No (re-run) | No | No | **Slider** |
| In-viewer review | No (report) | Partial | Yes | **Yes** |
| Actual mesh overlap | Red dot | No | No | **Clipped geometry** |
| Offline | Yes | Yes | No | **Yes (PWA)** |
| Mobile | No | No | Yes | **Yes** |
| Smart grouping | Manual | Auto | Basic | **Planned** |
| Heatmap | No | No | No | **Planned** |

## Reference
- [rtree-sql.js](https://www.npmjs.com/package/rtree-sql.js) — pre-built with SQLITE_ENABLE_RTREE
- [SQLite R-tree docs](https://sqlite.org/rtree.html)
- `docs/RTree.md` — existing R-tree spec from Blender era
- [Navisworks 2026 guide](https://bimcafe.in/blog/navisworks-clash-detection-guide-2026/)
- [Navisworks limitations](https://bimheroes.com/navisworks-clash-detection/)
- [AI-driven BIM coordination](https://medium.com/@advenser2007/ai-driven-bim-coordination-the-present-and-future-of-clash-detection-f69120124a57)
- [Solibri vs Navisworks](https://www.novatr.com/blog/solibri-vs-autodesk-navisworks-what-bim-coordinators-and-engineers-should-know)
