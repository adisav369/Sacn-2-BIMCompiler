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

## Reference
- [rtree-sql.js](https://www.npmjs.com/package/rtree-sql.js) — pre-built with SQLITE_ENABLE_RTREE
- [SQLite R-tree docs](https://sqlite.org/rtree.html)
- `docs/RTree.md` — existing R-tree spec from Blender era
