/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# ⚠ DO NOT REMOVE — Scope guard
# Scope: ERP Globe + Search integration — live bubble correlation,
#        child hub-and-spoke spawning, FK-derived relationships.
#        Extends ad_graph.js + erp_search.js + ad_ui.js.
# Read the log after every run. Exit code is not evidence.
# Spec-first: implement only what is described in a § section below.

---

# ERP Globe Search — Live Bubble Correlation + Child Traversal

## Delivered this session (S258)

| File | What | Tests |
|---|---|---|
| `erp_search.js` | FTS5 full-text index over 23 tables, 10561 records, client-filtered (system/gardenworld), BM25 ranking, pattern detection, incremental update | 53 |
| `ad_graph.js` v6 | Globe: TABLE/RECORD/CHILD node types, focusNode (soft), navigateToRecord (deep), FK discovery via AD_Column, hub-and-spoke child spawning, collapse toggle, weight formula, grow-from-centre animation (800ms), perspective=450, shortest-path fly, pinch-close→goBack, momentum kill on pinch release, browser back interception ("Back again to exit"), 🔍 mobile search button | 82 |
| `ad_ui.js` v10 | Search↔Globe: arrow/hover→soft focus TABLE bubble, Enter/click→openWindow (double-fire guard), tap-outside dismisses search, GardenWorld default client | 155 |
| `erp.html` | SW registration, IndexedDB cache for ad_seed.db (7.9MB → instant second visit), ?v=13 | — |
| `sw.js` v302 | Precaches all ERP modules (ad_graph, ad_ui, erp_search, ad_parser, ad_data, ad_charts, kernel_ops) | — |
| `test_globe_search.js` | 82 tests: FK discovery, weight, focusNode, navigateToRecord, zoom stability, view transitions, perspective overshoot, fly shortest path, search filtering, mobile, full §7 scenario | 82 |

**Current state:** Globe opens maximized with GardenWorld (15 TABLE bubbles). Search (Alt+S or 🔍 or long-press empty) finds records, arrow keys pulse TABLE bubbles, Enter opens card. Tap TABLE → dive into entity globe (records as full navigable sphere). Tap RECORD → FK children orbit out. Esc/tap-empty → fly back to originating TABLE. Pinch-close → go back. Browser back on home → "Back again to exit" toast. ad_seed.db cached in IndexedDB for instant mobile revisits.

**R1 + R2 + R10 DONE.** See `docs/ERP_Roadmap.md` for next priorities (R4 Benchmarks).

## Session startup — read these before coding:
1. `docs/ERP_Roadmap.md` — full roadmap with R1-R10 status + **Addendum 1** (R11-R15: Kitchen Setup, Deterministic AI, Legacy Migration, Entanglement-Free Architecture, Operational Excellence) + **Addendum 2** + **Addendum 3**
2. This file — delivered features + remaining §1-§7 specs
3. `deploy/dev/ad_graph.js` — globe renderer (v6, ~1350 lines)
4. `deploy/dev/ad_ui.js` — UI orchestrator (~1950 lines)
5. `deploy/dev/erp_search.js` — FTS5 search (474 lines)
6. `deploy/dev/tests/test_globe_search.js` — 82 whitebox tests

---

## §1. Search ↔ Globe Live Correlation

**Goal:** As user scrolls through search results (arrow keys or hover), the corresponding
bubble on the globe highlights in real-time. User sees WHERE the record lives spatially
before deciding to open it.

### §1.1 Bubble Highlight on Search Result Focus

When a search result gains focus (arrow key selection or pointer hover):

1. Find the bubble node on the globe whose `table` + `record_id` matches the result
2. If found: animate the camera to fly to that bubble (reuse existing `flyToNode`)
3. Pulse the bubble (scale up 1.5x, glow ring) for 2 seconds
4. If not found on current constellation: show a toast "Record not on current view"

```javascript
// In ad_ui.js, when search result is highlighted:
function _onSearchResultFocus(hit) {
  if (typeof ADGraph !== 'undefined' && ADGraph.focusNode) {
    ADGraph.focusNode(hit.table_name, hit.record_id);
  }
}
```

### §1.2 ADGraph.focusNode() — New API

Add to `ad_graph.js`:

```javascript
function focusNode(tableName, recordId) {
  // Find node in current _nodes array where node.table === tableName
  // and node.id === recordId (or node.key === tableName + '_' + recordId)
  // If found: flyToNode(node), pulse animation
  // Return true if found, false if not
}
```

### §1.3 Search Result Click → Bubble Focus + Card Open

When user presses Enter or clicks a search result:
1. Focus the bubble on globe (§1.1)
2. Open the window card view (existing `openWindow()`)
3. Both happen simultaneously — user sees spatial context AND data

---

## §2. Globe Node Registry — Table-Level Bubbles

**Current state:** Globe shows aggregate nodes (one per table: "C_BPartner", "M_Product", etc.)
with count-based sizing and edges showing FK relationships.

**Change:** Each table bubble should be expandable into record-level bubbles.

### §2.1 Node Types

| Type | What | Visual |
|---|---|---|
| `TABLE` | Aggregate — one bubble per table | Large, labelled with table name + count |
| `RECORD` | Individual record | Medium, labelled with display name |
| `CHILD` | Child of a focused record | Small, orbiting parent |

### §2.2 Node Data Structure

Extend the existing node object in `ad_graph.js`:

```javascript
{
  key: 'C_BPartner',          // unique key
  table: 'C_BPartner',        // table name
  id: null,                   // null for TABLE nodes, record PK for RECORD/CHILD
  type: 'TABLE',              // TABLE | RECORD | CHILD
  label: 'Business Partner',  // display label
  count: 18,                  // row count (TABLE only)
  displayText: null,          // record name (RECORD/CHILD only)
  children: [],               // spawned child nodes
  parent: null,               // parent node reference
  // ... existing positional fields (x, y, z, sx, sy, etc.)
}
```

---

## §3. Child Hub-and-Spoke on Bubble Click

**Goal:** Clicking a TABLE bubble expands it into individual record bubbles.
Clicking a RECORD bubble spawns its FK children as orbiting mini-bubbles.
Multiple expansions coexist — clicking a new bubble adds children WITHOUT clearing others.

### §3.1 TABLE → RECORD Expansion

Click a TABLE node (e.g., "C_BPartner"):
1. Query `SELECT * FROM C_BPartner WHERE IsActive = 'Y' LIMIT 30`
2. Spawn RECORD bubbles orbiting the TABLE bubble
3. TABLE bubble shrinks slightly, records orbit at radius 1.5x
4. Radial layout for ≤12, spiral for >12
5. Animation: records grow from table center → orbit position (300ms ease-out)

### §3.2 RECORD → CHILD Expansion (FK Traversal)

Click a RECORD node (e.g., "Seed Farm Inc." C_BPartner_ID=117):
1. Discover FK relationships from AD_Column metadata:
   - Which tables have a column named `C_BPartner_ID`?
   - → `C_Order`, `C_Invoice`, `C_Payment`, `M_InOut`, `C_Project`
2. For each FK table, query: `SELECT * FROM C_Order WHERE C_BPartner_ID = 117`
3. Spawn CHILD bubbles orbiting the RECORD bubble
4. Each child cluster is a mini hub-and-spoke
5. Child bubbles coloured by table type (commercial=amber, material=green)
6. Limit 20 children per FK table; "+N more" bubble if exceeded

### §3.3 FK Discovery from AD_Column

```javascript
function discoverChildren(db, tableName) {
  // The FK column for this table is tableName + '_ID'
  // Find all OTHER tables that have a column with this name
  var fkCol = tableName + '_ID';
  var sql = "SELECT DISTINCT t.TableName FROM AD_Column c " +
            "JOIN AD_Table t ON c.AD_Table_ID = t.AD_Table_ID " +
            "WHERE c.ColumnName = '" + fkCol + "' " +
            "AND t.TableName != '" + tableName + "' " +
            "AND t.IsActive = 'Y'";
  // Returns: ['C_Order', 'C_Invoice', ...] — tables that reference this one
}
```

### §3.4 Multi-Expansion (No Clearing)

- Each expansion adds bubbles to the scene — does NOT remove existing ones
- Bubbles from different expansions have different orbit centres (their parent)
- Memory limit: max 500 total bubbles on screen
- Old expansions fade after 60 seconds of no interaction (but can be re-expanded)
- Visual: connection lines (curved) from parent to children when parent is focused

### §3.5 Collapse

- Click an expanded TABLE/RECORD again → collapse its children (toggle)
- Or: long-press → collapse all expansions, return to TABLE-level view

---

## §4. Bubble Weight and Sizing

### §4.1 Weight Formula

```javascript
function getBubbleWeight(node) {
  var w = 1;
  if (node.type === 'TABLE') {
    w = 3 + Math.min(Math.log10(node.count + 1) * 2, 7); // 3-10
  } else if (node.type === 'RECORD') {
    w = 2;
    // Bonus for child count (if expanded)
    w += Math.min(node.children.length / 5, 3);
    // Bonus for document status
    if (node.docStatus === 'CO') w += 1;
  } else { // CHILD
    w = 1;
  }
  return w;
}
```

### §4.2 Visual Mapping

| Weight | Radius | Style |
|---|---|---|
| 1-2 | 6px | Simple dot |
| 3-5 | 12px | Filled circle with label |
| 6-8 | 20px | Filled circle, bold label, count badge |
| 9+ | 30px | Large circle, glow ring, bold label |

---

## §5. Integration Requirements

### §5.1 Files to Modify

| File | Changes |
|---|---|
| `ad_graph.js` | Add `focusNode()`, node types (TABLE/RECORD/CHILD), child spawning, FK discovery, multi-expansion, weight formula |
| `ad_ui.js` | Wire search result focus → `ADGraph.focusNode()`, wire search result click → focus + open |
| `erp_search.js` | No changes needed (already returns table_name + record_id) |
| `erp.html` | No changes needed |

### §5.2 Performance Targets

| Scenario | Target |
|---|---|
| TABLE → RECORD expansion (30 records) | < 50ms |
| RECORD → CHILD FK discovery + query | < 30ms |
| focusNode() fly-to animation | 500ms (smooth) |
| 500 bubbles on screen | 60fps sustained |
| Search result highlight correlation | < 16ms (one frame) |

### §5.3 Test Plan

New test file: `tests/test_globe_search.js` — target 30+ tests:

| Section | Tests |
|---|---|
| A: FK discovery | discoverChildren finds correct FK tables from AD_Column |
| B: TABLE expansion | Click TABLE → RECORD bubbles spawn with correct data |
| C: RECORD expansion | Click RECORD → CHILD bubbles spawn from FK query |
| D: Multi-expansion | Two expansions coexist, neither clears the other |
| E: Collapse | Click again → children removed |
| F: Weight | Bubble sizes match weight formula |
| G: focusNode | focusNode(table, id) finds and highlights correct bubble |
| H: Search correlation | Search result focus triggers focusNode |
| I: Limit | >30 records shows "+N more" bubble |
| J: §-log coverage | All §-tags present |

### §5.4 Existing Tests Must Pass

All 255 existing tests (48 ad_parser + 155 erp_ui + 52 erp_search) must remain green.

---

## §6. What NOT to Do

- Do NOT add a new minimize/maximize button — the existing ⛶/− toggle works
- Do NOT replace the globe renderer — extend `ad_graph.js` in place
- Do NOT use Three.js — the globe is canvas 2D, keep it canvas 2D
- Do NOT add WebGL — this must work on low-end phones
- Do NOT clear other bubbles when expanding a new one — multi-expansion is core UX
- Do NOT add server calls — all queries are local SQLite

---

## §7. Demo Scenario (Acceptance Criteria)

1. Open erp.html → globe fills viewport in maximized state (−)
2. See TABLE bubbles: C_BPartner (large), M_Product (large), C_Order (medium), etc.
3. Press Alt+S → glass search panel appears
4. Type "Seed" → results appear: "Seed Farm Inc." (BPartner), "Grass Seeder" (Product), etc.
5. Arrow down through results → **each result's bubble pulses on the globe in real-time**
6. Press Enter on "Seed Farm Inc." → camera flies to C_BPartner bubble, card opens
7. Click the C_BPartner TABLE bubble → 18 partner RECORD bubbles orbit into view
8. Click "Seed Farm Inc." record bubble → CHILD bubbles spawn: 2 orders, 1 invoice, 1 payment orbit around it
9. Click "Joe Block" record bubble → its children spawn separately (doesn't clear Seed Farm's children)
10. Long-press empty space → all expansions collapse, back to TABLE view
11. Bubble sizes: C_BPartner (count=18) is larger than C_Payment (count=2)
