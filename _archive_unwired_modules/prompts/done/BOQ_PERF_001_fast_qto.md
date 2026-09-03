# ⚠ DO NOT REMOVE — Read this block before any action. Read the log after every run.

## Scope: Speed up 4D/5D QTO load in boq_charts.html — 3 fixes, 1 file only
## Activity category: pipeline/debug (performance)
## Constraint: boq_charts.html ONLY. Do NOT touch measure.js, main.js, navigate*.js, or any other file.
## Backward compatibility: qto_cache schema unchanged, all existing chart outputs unchanged.

---

## Context

`deploy/dev/boq_charts.html` (1971 lines) is slow on large models (125k+ elements).
The mesh viewer loads 125k elements in 5s using batch streaming + rAF yielding.
The 4D/5D page does the opposite: 3 blocking SQL passes + 10 synchronous Chart.js renders.

**Three root causes identified:**

### Fix 1 — Merge 3 SQL passes into 1 (lines 806–861)

Currently:
- Query 1 (line 806): `COUNT(*)` + meshes — `elements_meta LEFT JOIN element_instances GROUP BY disc/cls/storey`
- Query 2 (line 820): `SUM(MAX(bbox_x,bbox_y,bbox_z))` — linear qty — same table, same GROUP BY
- Query 3 (line 839): `SUM(area)` — area qty — same table, same GROUP BY

All three scan the same tables with the same WHERE + GROUP BY.
Replace with ONE query that computes all three simultaneously.

**Merged query:**
```sql
SELECT m.discipline, m.ifc_class, m.storey,
       COUNT(*) as cnt,
       COUNT(DISTINCT i.geometry_hash) as meshes,
       SUM(CASE WHEN t.bbox_x IS NOT NULL AND t.bbox_x > 0
                THEN MAX(t.bbox_x, t.bbox_y, t.bbox_z) ELSE 0 END) as total_length,
       SUM(CASE WHEN t.bbox_x IS NOT NULL AND t.bbox_x > 0
                THEN MAX(t.bbox_x, t.bbox_y, t.bbox_z) *
                     CASE WHEN t.bbox_x >= t.bbox_y AND t.bbox_x >= t.bbox_z
                          THEN MAX(t.bbox_y, t.bbox_z)
                          WHEN t.bbox_y >= t.bbox_x AND t.bbox_y >= t.bbox_z
                          THEN MAX(t.bbox_x, t.bbox_z)
                          ELSE MAX(t.bbox_x, t.bbox_y)
                     END
                ELSE 0 END) as total_area
FROM elements_meta m
LEFT JOIN element_instances i ON m.guid = i.guid
JOIN element_transforms t ON m.guid = t.guid
WHERE m.building = '${_bldSafe}'
GROUP BY m.discipline, m.ifc_class, m.storey
ORDER BY m.discipline, m.storey, cnt DESC
```

After: replace `linearQty` and `areaQty` map lookups with direct columns from this single result.
The processing loop (lines 863+) reads `r[5]` for total_length and `r[6]` for total_area — adjust column indices.

### Fix 2 — Wrap qto_cache INSERTs in a transaction (lines 927–931)

Currently: one `stmt.run()` per row, no transaction. Each INSERT is auto-committed = disk sync per row.
On 500+ QTO rows this is 500 separate transactions.

Replace:
```javascript
db.run("DELETE FROM qto_cache WHERE rate_template = '" + tplName.replace(/'/g,"''") + "'");
const stmt = db.prepare("INSERT INTO qto_cache ...");
for (const r of qtoData) { stmt.run([...]); }
stmt.free();
```

With:
```javascript
db.run("BEGIN");
db.run("DELETE FROM qto_cache WHERE rate_template = '" + tplName.replace(/'/g,"''") + "'");
const stmt = db.prepare("INSERT INTO qto_cache ...");
for (const r of qtoData) { stmt.run([...]); }
stmt.free();
db.run("COMMIT");
```

This is the single largest write-speed fix. 10–50x faster.

### Fix 3 — Render charts progressively with rAF yielding

Currently: all 10 `new Chart(...)` calls fire synchronously in one function call, blocking the UI thread
for the entire duration (can be 2–5s on slow devices with large datasets).

Replace the block of `chartInstances.push(new Chart(...))` calls with a sequential
`renderNext()` function that yields between each chart using `requestAnimationFrame`:

```javascript
const chartBuilders = [
  () => buildPieChart(),
  () => buildMleChart(),
  () => buildTpChart(),
  // ... one function per chart
];
let _chartIdx = 0;
function renderNext() {
  if (_chartIdx >= chartBuilders.length) {
    console.log('[S240] §QTO_CHARTS_DONE total=' + _chartIdx);
    return;
  }
  chartBuilders[_chartIdx++]();
  requestAnimationFrame(renderNext);
}
requestAnimationFrame(renderNext);
```

Extract each `new Chart(...)` block into its own named builder function. No chart logic changes —
only the call order becomes async. The existing `chartInstances` array and `destroyCharts()` are unchanged.

---

## §-log requirements

Add these console.log lines — primary verification mechanism:

```javascript
// After merged query returns:
console.log('[S240] §QTO_SQL_MERGED rows=' + allRows.length + ' elapsed=' + (Date.now()-t0) + 'ms');

// After transaction COMMIT:
console.log('[S240] §QTO_CACHE_TX_WRITE rows=' + qtoData.length + ' elapsed=' + (Date.now()-tw) + 'ms');

// When first chart starts rendering:
console.log('[S240] §QTO_CHART_START count=' + chartBuilders.length);

// When all charts done:
console.log('[S240] §QTO_CHARTS_DONE total=' + _chartIdx);
```

---

## Extraction procedure

1. **Read** boq_charts.html lines 783–940 (QTO load function) fully before writing anything
2. **Read** boq_charts.html lines 1050–1270 (chart rendering block) fully before writing
3. **Apply Fix 1**: replace lines 806–861 with merged query; update column indices in processing loop
4. **Syntax check**: `node --check` won't work on HTML — open in browser devtools, check for JS errors
5. **Apply Fix 2**: wrap INSERT block in BEGIN/COMMIT
6. **Apply Fix 3**: extract each chart block into builder function, wire rAF loop
7. **Run**: open `deploy/dev/boq_charts.html?db=...` locally, confirm §-log lines appear in console
8. **Verify**: all charts still render identically, qto_cache still written
9. **Upload**: bump sw.js CACHE_VERSION (currently v280 → v281), upload boq_charts.html + sw.js

---

## Backward compatibility contract

- `qto_cache` table schema: **unchanged** — same columns, same PRIMARY KEY
- `qtoData` array structure: **unchanged** — same fields, same consumers (schedule, audit, Excel export)
- `scheduleData` generation: **unchanged** — called with same `qtoData`
- `chartInstances` array: **unchanged** — `destroyCharts()` still works
- Cache fast-path (lines 798–804): **unchanged** — hit path skips all three SQL queries already
- All chart outputs: **unchanged** — only render order becomes async, data identical

---

## What NOT to do
- Do NOT change qto_cache schema
- Do NOT change the `qtoData` row structure (fields: cls, disc, storey, qty, uom, etc.)
- Do NOT touch measure.js, main.js, navigate*.js, or any other file
- Do NOT move chart rendering to a Worker (sql.js in Worker is a separate session)
- Do NOT change the Excel export functions (save4D, save5D)
- Do NOT invent new columns or new cache tables

---

## Pre-flight state
- boq_charts.html: 1971 lines
- sw.js CACHE_VERSION: v280
- Branch: `full`
- qto_cache: already exists — fast path (cache hit) is the common case, this fixes the cold/miss path
- Baseline: open boq_charts.html with LTU.db or SampleHouse_extracted.db, note load time before fix
