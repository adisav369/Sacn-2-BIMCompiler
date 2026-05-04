# S245c — R-tree + Clash Detection Performance & UX Overhaul

## DONE (2026-05-04)

### 1. WASM swap: sql.js → rtree-sql.js
- CDN: `https://cdn.jsdelivr.net/npm/rtree-sql.js@1.7.0/dist/sql-wasm.js` (+ `.wasm`)
- Updated: `loader.js`, `streaming.js`, `sw.js` (v248), `boq_charts.html`
- Drop-in replacement — same `initSqlJs` API, adds `SQLITE_ENABLE_RTREE`

### 2. R-tree built async at runtime
- `_ensureClashIndexes` creates `elements_rtree` virtual table
- Populated in batches of 5000 with `setTimeout` yields (avoids main-thread timeout)
- Terminal 48k elements: ~1.2s total, non-blocking
- §-tags: `§CLASH_RTREE table created`, `§CLASH_RTREE batch N/total`, `§CLASH_RTREE ready N rows in Xms`

### 3. R-tree self-join limitation discovered
SQLite R-tree accelerates "find entries overlapping **this box**" (constant bounds, O(log N)).
It does NOT accelerate "find any **pair** of entries that overlap each other" (self-join → O(N²), worse than bbox arithmetic).
All clash queries remain bbox arithmetic with discipline/storey filtering + LIMIT.
R-tree is built for future S245d features (smart grouping, heatmap, clearance) where single-element lookups with constant bounds are the use case.

### 4. Matrix background check: discipline envelope overlap
- One `GROUP BY discipline` query computes spatial envelope (min/max XYZ per discipline) — instant
- Envelope overlap test is pure arithmetic, no SQL per pair
- Green = guaranteed no clashes (envelopes don't intersect). Orange = possible (click cell for exact)
- §-tag: `§CLASH_ENVELOPES N disciplines`, `§CLASH_MATRIX_BG disc|disc = OVERLAP|clear`

### 5. Clash visualization: bbox wireframes + clipped mesh
- Click clash row → fly-to overlap zone (camera distance based on overlap size, not element bbox)
- Clipped actual mesh geometry at overlap: red (element A) + blue (element B), `depthTest:false`
- §-tag: `§CLASH_VIZ overlap=WxHxDm meshes=N`

### 6. UX fixes
- **Matrix persists** when clicking cells or closing clash list (`_dismissClashes(keepMatrix)`)
- **Measure dots disabled** while clash panels are open (no blue dot interference)
- **Info card X close** works (draggable handler now respects `class="...close..."`)
- **No auto-dismiss on canvas click** — orbit freely around clash visualization
- **Status toggle**: right-click + long-press + double-click all cycle Reviewed→Resolved→Accepted
- **Clash highlights cleaned up** properly in all dismiss paths

## Files modified
| File | Change |
|------|--------|
| `deploy/dev/loader.js` | CDN → rtree-sql.js@1.7.0 |
| `deploy/dev/streaming.js` | `locateFile` → rtree-sql.js |
| `deploy/dev/sw.js` | Cache v248, new WASM URLs |
| `deploy/dev/boq_charts.html` | CDN consistency |
| `deploy/dev/measure.js` | R-tree creation, envelope check, clash viz, UX fixes |

## Performance (Terminal, 48k elements, 7 disciplines)
| Operation | Before (S245b) | After (S245c) |
|-----------|----------------|---------------|
| R-tree build | N/A | 1.2s async (non-blocking) |
| Matrix bg check | 50×50 sampling (missed sparse clashes) | Envelope overlap (instant, accurate) |
| Cell click query | ORDER BY overlap (N² timeout) | LIMIT 30 (instant, short-circuit) |
| Fly-to camera | maxBbox × 3 (zoomed out to slab) | overlapMax × 3 (tight on clash) |

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
- R-tree query: "find all elements within 1m of this clash" — constant bounds, O(log N)
- Show cluster count on matrix sphere instead of total
- Click cluster → expand to individual clashes

**2. Clash Heatmap overlay**
- R-tree range query: count clashes per zone/storey
- Color-code 3D model by clash density
- Red zones = high concentration → prioritize

**3. Clearance violation detection**
- R-tree expanded bounds: "find elements within 5cm of this pipe"
- Already in `clash_rules.json` severity levels
- Navisworks calls this "near miss" — requires separate test setup

**4. Trend tracking (cross-session)**
- Store clash counts per pair per date in localStorage
- "Last check: 45 clashes → now 32" — shows progress

**5. BCF export (future)**
- Industry-standard clash exchange format
- One clash → one BCF topic with viewpoint, camera, elements

**6. AI-ready: pattern detection**
- Structured JSON clash log → LLM prompt "summarize clashes"

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
| Smart grouping | Manual | Auto | Basic | **R-tree ready** |
| Heatmap | No | No | No | **R-tree ready** |

## S245d Resume — Critical Context

### The Query Heat Problem
Terminal has 48,428 elements × 7 disciplines. The fundamental clash query is a cross-join
(`element_transforms a JOIN element_transforms b ON a.guid < b.guid`) with bbox overlap check.
This is O(N²) per discipline pair on full building — hangs the main thread.

**What works (keep):**
- Storey-scoped queries: each storey has 200-4000 elements → cross-join is fast
- Discipline envelope overlap: one GROUP BY, instant, accurate for green/orange matrix
- LIMIT 30 without ORDER BY: short-circuits at first 30 matches
- Auto-pick top 5 storeys: `_queryClashesPair` finds storeys with both disciplines

**What hangs (avoid):**
- Full-building cross-join without storey filter (48k × 48k)
- ORDER BY overlap_m DESC on cross-join (forces full scan before LIMIT)
- Exhaustive loops: `while(true) { batch = query(offset); offset += 30; }`
- R-tree self-join (`rtree a JOIN rtree b`) — worse than bbox, tested and proven O(N²)

**What R-tree CAN do (S245d features):**
- Single-element lookups: "find all elements within 1m of THIS element" — constant bounds
- Smart grouping, heatmap, clearance detection — all constant-bounds queries

### Current State (v43, measure.js?v=27)
- 12 clash rules in clash_rules.json (ELEC/FP/ACMV pairs added)
- Right-click empty space = whole-building info card
- HTML report: 6 charts (severity, status, disc pair, class pair, radar, top offenders)
- Matrix snapshot in HTML report (top-right). Editable action sheet. CSV export.
- Status: 🟡RVW 🟢SLV ⚪ACC. Row highlight. Sticky header.
- SW v249, rtree-sql.js@1.7.0

### S245d TODO
1. HTML report click → viewer scene sync (`window.opener.postMessage`)
2. Trend tracking (localStorage clash counts per date)
3. Resolution progress chart in HTML report
4. Storey heatmap bar chart (clash density per storey — needs storey-scoped counts)

## Reference
- [rtree-sql.js](https://www.npmjs.com/package/rtree-sql.js) — pre-built with SQLITE_ENABLE_RTREE
- [SQLite R-tree docs](https://sqlite.org/rtree.html) — constant-bounds queries only
- `docs/RTree.md` — existing R-tree spec from Blender era
