/**
 * BIM OOTB — Frictionless BIM. Two DBs. One browser. Zero install.
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */

# Spatial ERP OOTB — Forward Roadmap

> What has been built is documented in [SpatialERP_OOTB.md](SpatialERP_OOTB.md).
> This document covers **what comes next** — features planned but not yet implemented.

---

## What Is Already Done

| Component | Status | Tests | Spec |
|---|---|---|---|
| `doc_engine.js` — 9-table schema, StateMachine, JournalEngine | Done (S255) | 79/79 | [SpatialERP_POC.md](../prompts/SpatialERP_POC.md) |
| `ad_parser.js` — AD metadata reader (menus, windows, tabs, fields) | Done (S255) | 48/48 | [ERP.md](ERP.md) |
| `ad_ui.js` + `ad_data.js` + `ad_charts.js` — AD renderer | Done (S256) | 155 | [ERP_AD_UI.md](../prompts/ERP_AD_UI.md) |
| `kernel_ops` — append-only op log with user_tag | Done (S255) | via doc_engine | [SpatialERP_POC.md](../prompts/SpatialERP_POC.md) |
| Data Globe — spatial bubble navigation | Done (S257) | 153 | [SpatialERP_OOTB.md §6.5](SpatialERP_OOTB.md) |
| `erp_search.js` — FTS5 full-text search, 23 tables, BM25 ranking, pattern detection | Done (S258) | 53 | [ERP_GLOBE_SEARCH.md](../prompts/ERP_GLOBE_SEARCH.md) |
| Globe↔Search correlation + FK hub-and-spoke traversal | Done (S258) | 82 | [ERP_GLOBE_SEARCH.md](../prompts/ERP_GLOBE_SEARCH.md) |
| Service Worker + offline mode + IndexedDB seed cache | Done (S258) | T13 | `deploy/dev/sw.js` |
| `erp.html` — standalone card-swipe ERP shell | Done (S256) | via test_ad_ui | [ERP_AD_UI.md](../prompts/ERP_AD_UI.md) |
| AD seed: 370 windows, 1130 tabs, 20911 fields, 1003 AD tables | Done (S255) | — | `deploy/dev/ad_seed.db` |
| GardenWorld data: 18 BPartners, 55 Products, 8 Orders, 8 Invoices | Done (S258) | — | `scripts/export_ad.sh` |
| Globe UX triage — Properties/Data gateways, collapse/dim fix | Done (S259c) | 32 | [S259_GLOBE_UX_TRIAGE.md](../prompts/S259_GLOBE_UX_TRIAGE.md) |
| Cascading Drill Accordion — new-tab panel, colourful cards, FK resolved | Done (S259c) | 13 | [S259_ACCORDION_PANEL.md](../prompts/S259_ACCORDION_PANEL.md) |

---

## R1. Smart Search (FTS5 + Pattern Detection) — DONE (S258)

**Status:** Implemented and deployed. 53 tests in `test_erp_search.js`.

### R1.1 FTS5 Virtual Table — DONE

`erp_search.js` creates an FTS5 virtual table over 23 searchable tables (17 GardenWorld + 6 System AD). Porter stemming + unicode61 tokeniser. Client-filtered (`system` vs `gardenworld`) so searches never cross client boundaries.

```sql
CREATE VIRTUAL TABLE erp_search USING fts5(
  search_text,                    -- composite of all searchable columns
  table_name UNINDEXED,           -- source table
  record_id UNINDEXED,            -- PK
  display_text UNINDEXED,         -- human-readable name
  window_id UNINDEXED,            -- AD_Window_ID for card open
  doc_status UNINDEXED,           -- DR/IP/CO/CL/VO
  client UNINDEXED,               -- 'system' | 'gardenworld'
  tokenize = "porter unicode61"
);
```

10,561 rows indexed. Rebuild on seed load. `updateRecord()` / `removeRecord()` for incremental updates.

### R1.2 Search UX

| Behaviour | Implementation |
|---|---|
| Debounced input (300ms) | `setTimeout` in search handler, cancel on keystroke |
| Pattern detection | RegEx: `INV-\d+` → invoice, `PO-\d+` → purchase order, `>5000` → amount filter |
| BM25 ranking | FTS5 `rank` column, exact match boosted to top |
| Single exact match | Auto-jump: animate camera to bubble, open InfoWindow |
| Multiple matches | Ranked dropdown + simultaneous bubble highlighting on globe |
| Keyboard nav | Arrow keys + Enter in dropdown for power users |
| Empty state | Recent Changes bubble set (see R1.4) |

### R1.3 InfoWindow Bubbles

Each search hit maps to a globe bubble. Clicking a bubble opens an **InfoWindow** — a summary card showing:
- Document header (number, status, date, partner)
- Key amounts (total, balance, outstanding)
- Related records (linked documents, journal entries)
- Action buttons (open full card, drill to journal, view on 3D model)

The InfoWindow replaces the traditional ERP "zoom" / "drill" paradigm with spatial proximity — related records cluster near the hit.

### R1.4 Recent Changes Bubble (Default Landing)

When no search is active, the globe shows a **Recent Changes** constellation:

```sql
SELECT table_name, record_id, op_type, timestamp, user_tag
FROM kernel_ops
ORDER BY timestamp DESC LIMIT 100;
```

Each op becomes a bubble. Colour = op type (green = create, amber = update, red = delete). Size = record importance (documents > lines). The user sees "what changed since I last looked" without clicking anything.

### R1.5 Pre-emptive Search (from kernel_ops observation)

Background worker watches kernel_ops patterns:

| Observed pattern | Pre-emptive action |
|---|---|
| User views same BPartner daily | Pre-fetch that partner's transactions into FTS5 cache |
| User searches "invoices" every Monday 9am | Warm up invoice query result before 9am |
| User opens same project repeatedly | Keep project shard attached (see R3) |

This is a future optimisation layer — the search works without it, but gets faster with usage.

---

## R2. Full iDempiere AD Migration

**Goal:** Populate ALL master data tables from the iDempiere GardenWorld seed, not just BPartner/Product/Project. This gives the search box real data to work with and makes the globe constellation meaningful.

### R2.1 Current State

`export_ad.sh` currently exports:
- **AD metadata:** complete (370 windows, 1130 tabs, 20911 fields, 1003 table definitions)
- **Master data:** partial — only `C_BPartner` (18), `M_Product` (55), `C_Project` (0)

### R2.2 Target: Full GardenWorld Data Export

Extend `export_ad.sh` to export all GardenWorld transactional and master data:

| Table group | Key tables | Expected rows | Purpose |
|---|---|---|---|
| **Accounting** | C_ElementValue (CoA), C_AcctSchema, Fact_Acct | ~500 + journal entries | Chart of accounts, posted journals |
| **Sales** | C_Order, C_OrderLine, C_Invoice, C_InvoiceLine | ~200 orders, ~1000 lines | Full order-to-invoice cycle |
| **Purchasing** | C_Order (IsSOTrx=N), M_InOut, M_InOutLine | ~100 POs, ~500 receipts | Procurement cycle |
| **Inventory** | M_Warehouse, M_Locator, M_Storage | ~10 warehouses, ~200 storage records | Stock positions |
| **Payment** | C_Payment, C_BankStatement, C_BankStatementLine | ~150 payments | Cash flow |
| **HR** | C_BPartner (IsEmployee=Y), AD_User | ~20 employees | Org chart |
| **Tax** | C_Tax, C_TaxCategory | ~30 tax rules | Tax engine data |
| **Pricing** | M_PriceList, M_PriceList_Version, M_ProductPrice | ~3 price lists, ~150 prices | Multi-currency pricing |
| **Document types** | C_DocType, C_DocTypeCounter | ~50 doc types | Document routing |

### R2.3 Schema Mapping

Each iDempiere table maps to an SQLite table with the same name and columns (subset). The AD metadata (`AD_Table`, `AD_Column`) already describes the schema — the export script reads the AD to generate `CREATE TABLE` + `INSERT` statements automatically.

**Key principle:** The AD is the schema. We do not hand-code table definitions. `export_ad.sh` reads `AD_Column` for each `AD_Table` and generates the DDL.

### R2.4 InfoWindow Constellation

With full master data, each AD Window becomes a navigable constellation on the globe:

- **C_BPartner window** → 18 partner bubbles, each with contacts, locations, orders clustered nearby
- **C_Order window** → order bubbles coloured by DocStatus (Drafted=grey, Completed=green, Voided=red)
- **M_Product window** → product bubbles sized by transaction volume
- **Fact_Acct** → journal entry bubbles forming an accounting timeline

The globe is not a gimmick — it is the InfoWindow. Click a partner bubble, see their orders orbit around it. Click an order, see its invoice and payment linked by lines.

---

## R3. Database Sharding and Lazy Loading

**Goal:** Handle real-world data volumes (10K+ BPartners, 100K+ transactions) without choking the browser.

### R3.1 Project-Based Sharding

```
master.db              -- AD metadata + master data (CoA, BPartners, Products, DocTypes)
                       -- 10-20 MB, loads first, always in memory
project_{name}.db      -- all transactions for one project/client/period
                       -- 50-200 MB each, loaded on demand
archive_{year}.db      -- read-only past data, compressed
                       -- loaded via ATTACH DATABASE when queried
```

### R3.2 Loading Strategy

1. **Immediate** (0-5s): Load `master.db` — AD + master data. Globe shows master constellation.
2. **Background** (`requestIdleCallback`): Load active project shard. Transactions appear as bubbles.
3. **On demand** (`ATTACH DATABASE`): User searches for archived data → attach archive shard, query, detach.

### R3.3 Memory Pressure Management

```javascript
// Monitor browser memory
if (navigator.deviceMemory && navigator.deviceMemory < 4) {
  // Low-memory device: limit active bubbles to 500
  // Unload inactive shards after 5 minutes
  // Disable pre-emptive fetch
}

// Performance observer for long tasks
new PerformanceObserver((list) => {
  for (const entry of list.getEntries()) {
    if (entry.duration > 100) {
      // Long task detected — reduce bubble count, defer non-critical queries
    }
  }
}).observe({ type: 'longtask' });
```

---

## R4. Benchmark and Stress Testing

**Goal:** Prove the system handles enterprise-scale data with measurable, reproducible benchmarks. Not speculation — measured results that ship with the repo.

### R4.1 Benchmark Suite (`tests/bench_erp.js`)

| Benchmark | Scenario | Target | What it proves |
|---|---|---|---|
| B1: Seed load | Load full GardenWorld (all master + 10K transactions) | < 3s on desktop, < 8s on mobile | First-load viability |
| B2: FTS5 query | Search across 100K indexed records | < 50ms per query | Search is instant |
| B3: Batch insert | Insert 10K document lines in one transaction | < 2s | BOQ / bulk import speed |
| B4: Journal posting | Post 1000 documents (2000 journal entries) | < 5s | Accounting throughput |
| B5: Globe render | Render 5000 bubbles with labels | 60fps sustained | Visual performance |
| B6: Shard attach | ATTACH 200MB archive DB + query | < 1s attach, < 100ms query | Lazy loading works |
| B7: kernel_ops replay | Replay 10K ops from crash | < 2s | Crash recovery speed |
| B8: Concurrent tabs | 5 browser tabs, same OPFS database | No corruption, no deadlock | Multi-tab safety |
| B9: Memory ceiling | 50K records loaded, sustained usage | < 300MB RSS | Mobile viability |
| B10: Offline round-trip | Go offline → CRUD 100 records → sync on reconnect | Zero data loss | Offline reliability |

### R4.2 Existing Stress Tests (Baseline)

`test_stress.js` (41 tests) already proves:
- 100K documents, 500K lines, 1M kernel_ops — all queries < 1s
- 32 document lifecycles/sec
- 179MB peak for 1.6M rows

The benchmark suite extends this with FTS5, globe rendering, sharding, and multi-tab scenarios.

### R4.3 CI Integration

Benchmarks run on every PR. Results appended to `bench_results.jsonl` for trend tracking. Regression = PR blocked.

---

## R5. Offline Mode and Sync

**Goal:** The ERP works fully offline — all CRUD, all search, all rendering — and syncs when connectivity returns.

### R5.1 What Already Works (from BIM OOTB)

- **Service Worker** (`sw.js`): caches all static assets (HTML, JS, WASM, CSS). Controlled by `CACHE_VERSION`.
- **SQLite in memory**: the `.db` file is loaded into WASM memory. No network needed after first load.
- **OPFS persistence** (planned): Origin Private File System gives crash-safe, quota-managed storage without IndexedDB serialisation overhead.

### R5.2 Offline CRUD

All `ad_data.js` operations (create, read, update, delete) write to the in-memory SQLite DB and log to `kernel_ops`. No network call is involved. The data is safe in OPFS.

When the user explicitly taps "Sync":
1. Export `kernel_ops WHERE synced = 0` as JSON
2. Upload to OCI bucket (or any static host) as `sync/{timestamp}.json`
3. Mark ops as `synced = 1`
4. Download any remote sync files newer than last sync
5. Replay remote ops into local DB (conflict resolution per R6)

### R5.3 First Load Sequence

```
0-2s:    Fetch sql.js WASM engine (1MB, cached by SW after first load)
2-5s:    Download seed .db from OCI (master.db, 10-20MB)
5-8s:    Build FTS5 indexes over master data
8-10s:   Warm-up: recent kernel_ops, default constellation
10s+:    Ready. All subsequent loads < 1s (SW cache + OPFS).
```

After first load, the app never needs the network again unless the user chooses to sync.

---

## R6. CRDT Sync for Multi-Device / Multi-User

**Goal:** Prepare for enterprise deployment where multiple users edit the same dataset from different devices, potentially offline simultaneously.

### R6.1 Why CRDTs

Last-write-wins is acceptable for single-user offline. But enterprise means:
- Field team on-site (offline) + back office (online) editing same project
- Two warehouse pickers scanning into the same inventory simultaneously
- Manager approving on phone while clerk edits on desktop

CRDTs (Conflict-free Replicated Data Types) guarantee convergence without a central server.

### R6.2 Integration Approach

| Layer | Technology | Role |
|---|---|---|
| Op log | `kernel_ops` (already append-only) | Source of truth — each op is a CRDT operation |
| Merge | Hybrid Logical Clocks (HLC) | Causal ordering without wall-clock sync |
| Conflict resolution | Per-field last-writer-wins with HLC timestamp | Deterministic merge, no user intervention for non-conflicting fields |
| True conflicts | UI prompt: show both values, user picks | Only when same field edited by two users in same offline window |
| Transport | OCI bucket (or any object store) | Sync files = serialised op batches |

### R6.3 Implementation Phases

1. **Phase 1 — HLC timestamps** in kernel_ops (add `hlc_timestamp` column). No sync yet, just clock infrastructure.
2. **Phase 2 — Op exchange**: export/import kernel_ops batches between devices via shared bucket.
3. **Phase 3 — Merge engine**: replay remote ops, detect conflicts, auto-resolve or prompt.
4. **Phase 4 — Real-time** (optional): WebRTC or WebSocket for live presence awareness when online.

### R6.4 Self-Healing Guardrails

| Observation | Automatic action |
|---|---|
| Concurrent edits on same document | Visual indicator ("also being edited by Desk-2"), merge on sync |
| Browser memory pressure | Unload inactive shards, archive cold rooms, reduce bubble count |
| Corrupted sync file | Skip + log warning, request re-export from source device |
| Clock drift between devices | HLC corrects drift — ops are causally ordered, not wall-clock ordered |

---

## R7. Migration Scripts (Legacy ERP Import)

**Goal:** One-command import from any legacy ERP into the 9-table schema. The AI knows all legacy schemas — script generation is pattern-matched.

### R7.1 iDempiere (Priority — our own AD)

`scripts/export_ad.sh` already does this partially. Extend to:
- Export ALL GardenWorld data tables (R2.2)
- Generate FTS5 rebuild after import
- Verify with sample queries (count checks per table)
- Output: `ad_seed.db` with full data ready for globe display

### R7.2 Odoo (Future)

```bash
# migrate-from-odoo.sh
# Input: Odoo PostgreSQL backup or JSON-RPC export
# Mapping: res.partner → C_BPartner, sale.order → documents, account.move → journal
# Output: odoo_seed.db
```

### R7.3 SAP / Oracle / Navision (Future)

Each migration script follows the same pattern:
1. Read source dump (IDoc, Oracle export, C/AL backup)
2. Map to 9-table schema (or to iDempiere AD tables, since we already render those)
3. Generate `.db` file
4. Verify with benchmark suite (R4)

**Key insight:** Since we render the full iDempiere AD, migrating to iDempiere's table structure (not just the 9-table schema) gives us instant UI rendering for any imported data. The AD IS the renderer.

---

## R8. Domain Packs (Post-POC Expansion)

**Goal:** Prove the 9-table schema handles any domain by shipping seed databases for different industries.

| Domain | Seed `.db` | Key tables | Handler | Status |
|---|---|---|---|---|
| Construction | `construction_seed.db` | C_Project, BOQ, FAR | `handlers/construction.js` | POC done (S255) |
| F&B (restaurant) | `fnb_seed.db` | Tables, menus, orders, kitchen queue | `handlers/fnb.js` | Planned |
| WMS (warehouse) | `wms_seed.db` | Racks, bins, SKUs, pick lists | `handlers/wms.js` | Planned |
| Field service | `fsm_seed.db` | Jobs, technicians, parts, routes | `handlers/fsm.js` | Planned |
| Accounting only | `gl_seed.db` | Chart of accounts, journal | `handlers/gl.js` | Journal engine done |
| Retail POS | `pos_seed.db` | Products, prices, sales, register | `handlers/pos.js` | Planned |

**No core code changes.** Each domain = new seed data + optional handler JS + optional role_band colours.

---

## R9. Self-Healing Kernel

**Goal:** The `kernel_ops` log is not just an audit trail — it is a behavioural observation engine. By analysing op patterns, the kernel detects user habits, system stress, and data anomalies, then acts pre-emptively without user intervention.

### R9.1 Pattern Detection Engine

A background worker (`requestIdleCallback`) periodically scans recent `kernel_ops` entries and maintains a small `kernel_patterns` table:

```sql
CREATE TABLE kernel_patterns (
  pattern_id TEXT PRIMARY KEY,
  pattern_type TEXT,       -- 'FREQUENCY', 'TIMING', 'ANOMALY', 'PRESSURE'
  entity_key TEXT,         -- what the pattern is about (table, record, query)
  frequency INTEGER,       -- times observed
  last_seen TEXT,          -- ISO timestamp
  action TEXT,             -- what to do when pattern fires
  metadata JSON            -- thresholds, parameters
);
```

### R9.2 Detectable Patterns and Actions

| Pattern | Detection method | Automatic action |
|---|---|---|
| **Daily search** — user searches "pending invoices" every morning | `GROUP BY op_type, DATE(timestamp)` frequency > 5 consecutive days | Pre-fetch that query result into FTS5 cache before 9am (or at app open) |
| **Hot record** — same BPartner opened 10+ times in a week | `COUNT(*) WHERE entity_id = X AND op_type = 'VIEW'` | Pin to Recent Changes constellation, keep in memory |
| **Concurrent edit** — two user_tags writing same entity within 5 minutes | `WHERE entity_id = X AND timestamp > (now - 5min) GROUP BY user_tag HAVING COUNT(DISTINCT user_tag) > 1` | Visual indicator on bubble ("also edited by Desk-2"), queue merge |
| **Stale data** — a document untouched for 90+ days | `MAX(timestamp) WHERE entity_id = X < (now - 90d)` | Dim bubble on globe, suggest archival |
| **Memory pressure** — total loaded records > device threshold | `navigator.deviceMemory` + row count monitoring | Unload cold shards, reduce bubble count, defer non-critical queries |
| **Error spike** — 3+ failed ops in 1 minute | `WHERE op_type LIKE '%ERROR%' AND timestamp > (now - 1min)` | Surface warning toast, log diagnostic snapshot |
| **Workflow bottleneck** — documents stuck in "In Progress" > 7 days | `WHERE metadata->>'docStatus' = 'IP' AND timestamp < (now - 7d)` | Highlight as red bubbles, suggest escalation |

### R9.3 Learning, Not Rules

The kernel does not use hardcoded rules. It observes, counts, and applies thresholds:
- **Frequency threshold** (default 5 in 7 days) → pattern qualifies as "habit"
- **Recency decay** — patterns not observed for 30 days are demoted
- **User override** — user can dismiss a pre-emptive action, kernel stops suggesting it

This is not AI/ML — it is simple SQL aggregation over an append-only log. The intelligence is in what you count, not how you count it.

### R9.4 Crash Recovery (Self-Healing in the Literal Sense)

The kernel log is the system's journal in the database sense:
1. Every mutation writes to `kernel_ops` **before** updating materialized views (FTS5, globe positions, journal)
2. On crash/reload: replay `kernel_ops WHERE replayed = 0`
3. Materialized views are reconstructed from the log — no data loss
4. Checkpoint every 1000 ops to a snapshot file (keep last 2)

**Recovery benchmark target (R4/B7):** 10K ops replayed in < 2 seconds.

---

## R10. Dynamic Bubble Traversal — Graph Explorer — DONE (S258)

**Status:** Implemented in `ad_graph.js` v6. 82 tests in `test_globe_search.js`. Deployed to `bim-ootb-full`.

### R10.1 FK Relationship Discovery — DONE

`_discoverChildren(tableName)` queries AD_Column for all tables referencing `tableName_ID`. No relationship table needed — the AD IS the schema. Example: `C_BPartner` discovers 150+ FK references (C_Order, C_Invoice, C_Payment, M_InOut, etc.).

```javascript
// Derived from AD_Column metadata — no relationships table needed
// AD_Column.ColumnName = 'C_BPartner_ID' in AD_Table 'C_Order'
// → C_Order has FK to C_BPartner → BPartner is parent, Orders are children
function buildRelationshipMap(db) {
  // SELECT c.ColumnName, t.TableName, c.AD_Reference_ID
  // FROM AD_Column c JOIN AD_Table t ON c.AD_Table_ID = t.AD_Table_ID
  // WHERE c.ColumnName LIKE '%_ID' AND c.AD_Reference_ID IN (19, 30)
  // → { 'C_BPartner': ['C_Order', 'C_Invoice', 'C_Payment', 'M_InOut', ...] }
}
```

**Key relationships discovered automatically from AD:**
- `C_BPartner` → `C_Order`, `C_Invoice`, `C_Payment`, `M_InOut`, `C_Project`
- `C_Order` → `C_OrderLine` → `M_Product`
- `C_Invoice` → `C_InvoiceLine` → `M_Product`
- `M_Product` → `M_ProductPrice`, `M_Storage`, `C_OrderLine`, `C_InvoiceLine`
- `M_PriceList` → `M_PriceList_Version` → `M_ProductPrice`
- `M_Warehouse` → `M_Locator` → `M_Storage`
- `C_Project` → `C_ProjectPhase` → `C_ProjectTask`

### R10.2 Child Spawning on Focus

When a bubble receives focus (click or search jump):

1. Query relationship map for child tables
2. For each child table, `SELECT` records where FK = parent record ID
3. Spawn child bubbles orbiting parent (radial for <10, spiral for 10+)
4. Animate: grow from parent center → orbit position (300ms, ease-out)
5. Limit to 20 children; show "+N more" bubble if exceeded

```javascript
function onBubbleFocus(bubble) {
  var children = relationshipMap[bubble.table] || [];
  children.forEach(function (childTable) {
    var fkCol = bubble.table + '_ID';
    var records = ADData.readRecords(db, childTable,
      fkCol + ' = ' + bubble.record_id, null);
    // Spawn up to 20 child bubbles
  });
}
```

### R10.3 Bubble Weight and Visual Prominence

Composite weight determines bubble size:

| Factor | Source | Weight contribution |
|---|---|---|
| Base importance | Table type (document > line > lookup) | 1-5 |
| Child count | `COUNT` of FK references to this record | +1 per 10 children, max +5 |
| Document status | `CO` = +2, `DR` = -1, `VO` = -2 | -2 to +2 |
| Recency | `kernel_ops` last access within 7 days | +1 |

Visual mapping:
- Weight 1-3: small (radius 0.4)
- Weight 4-7: medium (radius 0.7)
- Weight 8-12: large (radius 1.0)
- Weight 13+: hero (radius 1.4, pulsing glow)

### R10.4 Bidirectional Navigation

- **Down:** Click bubble → children spawn
- **Up:** Left arrow key or "Parent" button → camera jumps to parent bubble
- **Breadcrumb trail:** `PriceList 2025 > Version Mar > Oak Table > Storage`
- **Connection lines:** Curved QuadraticBezier lines from parent to focused children

### R10.5 Memory Management

- Children persist 30 seconds after focus moves away, then unload
- Maximum 500 active bubbles — oldest children evicted first
- `navigator.deviceMemory < 4` → reduce to 200 active bubbles
- Focus events logged to `kernel_ops` as `BUBBLE_FOCUS` for R9 pattern detection

### R10.6 Integration Points

| Feature | Integration |
|---|---|
| **R1 Smart Search** | Search hit → auto-focus bubble → children spawn automatically |
| **Role filtering** | Children filtered by role (SALE cannot see supplier children) |
| **R9 Self-Healing** | Frequently focused bubbles pre-spawn children on app load |
| **Offline** | All FK queries are local SQLite — zero latency traversal |

### R10.7 Performance Targets

| Scenario | Target |
|---|---|
| Spawn first 20 children | < 50ms (query + render) |
| Focus switch | < 30ms |
| Weight recalculation | < 1ms per bubble |
| 500 active bubbles | < 200MB |

### R10.8 Implementation — DONE

All features implemented directly in `ad_graph.js` (no separate `bubble_graph.js` needed). Key functions:

| Function | What |
|---|---|
| `_discoverChildren(tableName)` | FK discovery from AD_Column — returns table names that reference this one |
| `_expandRecord(node)` | RECORD click → query FK tables → spawn CHILD bubbles orbiting parent |
| `_collapseNode(node)` / `_collapseAll()` | Toggle collapse, long-press reset |
| `_getBubbleWeight(node)` | Weight formula: TABLE=3-10 (log10 count), RECORD=2+children+status, CHILD=1 |
| `focusNode(table, id)` | Soft focus for search browsing — pulses TABLE, auto-returns home from entity view |
| `navigateToRecord(table, id)` | Deep navigate for Enter/click — dives into entity globe, flies to record |
| `_flyToFront(node)` | Uses target position (not animated), shortest-path normalisation (delta <= PI) |
| `_onPopState()` | Browser back → go back within globe, "Back again to exit" warning on home |

Tests: `test_globe_search.js` — 82 tests covering FK discovery (A), weight formula (F), focusNode (G), search correlation (H/L), zoom stability (M), view transitions (N), perspective overshoot (Q), fly shortest path (R), search filtering (P), mobile interactions (O).

---

## R16. Cascading Drill Accordion Panel — DONE (S259c)

**Status:** Implemented in `ad_ui.js` + `ad_graph.js`. 32+13 tests. Deployed ootb-dev + ootb-full.

### R16.1 Globe UX — Properties & Data Gateways

Tapping a RECORD bubble spawns two gateways:
- **Properties** (orange) → expands sub-bubbles, one per non-null field column. Tap a column bubble → opens accordion filtered & sorted by that column. Visual SQL query picker.
- **Data** (blue) → opens accordion panel directly with all fields + child tabs.

Double-tap RECORD → opens all records of that table. Long-press any bubble → same (all records).

### R16.2 Cascading Drill — New Browser Tab

Each panel opens in a **new browser tab** (multi-screen, drag/arrange). Design:
- **Title bar** (sticky) — record name. Tap → reset to Header.
- **Accordion cards** — colourful rounded panels with chevron, left colour bars (blue/green/gold/pink/purple/teal by position), slide animation.
- **ONE tab open at a time.** Tap row in grid → current closes, next tab opens filtered to that row's FK children.
- **Fields ACROSS** as column headers (horizontal swipe). FK values resolved to Names. No system columns.
- **Child tabs** lazy-load via `window.opener._accordionLoadTab()` bridge.
- No scrollbars — natural touch drag.

### R16.3 Prompts

- Globe UX: `prompts/S259_GLOBE_UX_TRIAGE.md`
- Accordion: `prompts/S259_ACCORDION_PANEL.md` (§1 updated with cascading drill spec)

### R16.4 Next Steps

- Table-level faceted filter (Properties on TABLE bubble → column fill-rate → tap to filter all records)
- Child tab field dedup for tables with multiple AD_Tab definitions
- OPFS persistence for accordion state (reopen where you left off)

---

## Implementation Priority

| Priority | Item | Depends on | Est. sessions | Status |
|---|---|---|---|---|
| **P0** | R2 — Full iDempiere data export | `export_ad.sh` extension | 1-2 | **DONE** (S258) |
| **P1** | R1 — FTS5 Smart Search | R2 (needs data to search) | 2-3 | **DONE** (S258) |
| **P2** | R10 — Dynamic Bubble Traversal | R1 + R2 (needs search + data) | 2-3 | **DONE** (S258) |
| **P2.5** | R16 — Cascading Drill Accordion | R10 (needs globe + traversal) | 1 | **DONE** (S259c) |
| **P3** | R4 — Benchmark suite | R2 + R1 (needs data + queries to measure) | 1 | Next |
| **P4** | R5 — Offline OPFS persistence | SW already done, OPFS is incremental | 1-2 | |
| **P5** | R3 — Database sharding | R4 (benchmarks prove when sharding is needed) | 2 | |
| **P6** | R6 — CRDT sync | R5 (offline must work first) | 3-4 | |
| **P7** | R7 — Migration scripts (Odoo, SAP) | R2 (iDempiere migration proves the pattern) | 2 per source | |
| **P8** | R8 — Domain packs (F&B, WMS) | R1 + R2 (search + data model proven) | 1-2 per domain | |
| **P9** | R9 — Self-Healing Kernel | R1 + R4 (needs search + benchmarks to measure) | 2 | |

---

## Constraints and Tradeoffs

| Constraint | Mitigation |
|---|---|
| First load on slow connection | Progressive: master.db first (small), project shards background |
| Browser storage limits (OPFS) | `navigator.storage.persist()` + quota UI warning |
| Safari OPFS bugs | Fallback to IndexedDB; monitor WebKit bug tracker |
| Mobile memory (2GB devices) | Benchmark R4/B9 enforces < 300MB ceiling; unload inactive shards |
| Multi-tab writes to OPFS | SharedWorker or Web Locks API to serialise writes |
| Security (client-side only) | Per-role `.db` extracts for external sharing (see [SpatialERP_OOTB.md §7](SpatialERP_OOTB.md)) |
| CRDT complexity | Phase 1-2 are simple (HLC + op exchange); Phase 3-4 only when needed |

---

*Last updated: 2026-05-14 (R1+R2 done, R10 spec'd). For architecture and design rationale, see [SpatialERP_OOTB.md](SpatialERP_OOTB.md).*


ADDENDUM 1
Based on our extensive discussion about the **killer front-end experience**, **landed deterministic AI**, **legacy logic migration**, and **entanglement-free architecture**, here are the **appendices** I would add to your Spatial ERP OOTB Roadmap.

These are not rewrites — they are **additions** that preserve your existing priorities while extending the vision.

---

## Proposed Additions to the Roadmap

### New Section: R11. Kitchen-to-Warehouse Fast Setup (Zero-Config Store Creation)

**Goal:** Turn any physical environment (kitchen, office, warehouse corner) into a fully functional store with 3D visualization in under 60 seconds, no configuration required.

**Status:** Planned — New for S259

#### R11.1 One-Click Environment Scan

| Component | Implementation | Time |
|-----------|----------------|------|
| Camera activation | MediaDevices API with environment facing preference | <2s |
| Barcode scanning | ZXing library, continuous scan mode | Real-time |
| Product discovery | Scan 5+ items OR take photos (OCR fallback) | 30s |
| Mock quantity | User sets default (10) or per-product override | 5s |
| Store finalization | Auto-generate warehouse, shelves, inventory | 10s |

**Success metric:** User goes from blank browser to working store in <60 seconds.

#### R11.2 Automatic 3D Store Layout Generation

```sql
-- Layout algorithm: grid placement based on scan order
-- Products arranged 3 columns × N rows, spaced 2 units apart
-- Each product gets a locator bin with 3D coordinates
-- Shelves generated automatically with wood texture
```

**Visual output:** Three.js scene with clickable product cubes, orbital controls, ambient + directional lighting.

#### R11.3 Integration with Existing Modules

| Existing Module | Integration Point |
|----------------|-------------------|
| R1 Smart Search | Store products indexed immediately via FTS5 rebuild |
| R10 Graph Explorer | Product bubbles spawn order/price children on click |
| kernel_ops | All create/update/delete operations logged |
| Service Worker | Full offline capability from first scan |

**Why this belongs in the roadmap:** This is the **ultimate onboarding demo** — proves the entire system works in 5 minutes with zero configuration. No other ERP can do this.

---

### New Section: R12. Landed Deterministic AI (Your Own Code, No Black Box)

**Goal:** Provide AI assistance that is 100% auditable, runs locally, has no external API calls, and cannot have backdoors — using only SQL aggregations, statistics, and pattern matching.

**Status:** Planned — Foundation exists in R9 (Self-Healing Kernel), extends to proactive assistance.

#### R12.1 Pattern Detection (Extension of R9)

| Pattern | Detection Method | Proactive Action |
|---------|-----------------|------------------|
| Daily habit | `GROUP BY op_type, DATE(timestamp)` frequency >5 | Pre-fetch query results before user asks |
| Hot record | Same entity opened 10+ times/week | Pin to Recent Changes, keep in memory |
| Anomaly detection | 3-sigma from historical mean | Alert before save, explain deviation |
| Next action prediction | Most common action at current hour | Pre-load that UI component |

**Implementation:** Pure SQL + JavaScript — no external models, no training data, no API calls.

#### R12.2 Natural Language Query Translation (Deterministic)

```
User types: "show me orders over 5000"

→ Regex patterns detect intent
→ Map to SERE condition: {"field": "GrandTotal", "operator": ">", "value": 5000}
→ Execute FTS5 search with filter
→ Return results as bubble constellation
```

| Pattern | Intent | SERE Translation |
|---------|--------|------------------|
| `>(\\d+)` | Amount filter | `{"field":"GrandTotal","operator":">","value":$1}` |
| `INV-\\d+` | Specific document | Direct lookup by doc_no |
| `pending|draft` | Status filter | `{"field":"DocStatus","operator":"IN","value":["DR","IP"]}` |
| `last (\\d+) days` | Time filter | `{"field":"DateDoc","operator":">","value":"now - $1 days"}` |

**Confidence scoring:** Based on number of matched patterns, show "AI confidence: 94%" before executing.

#### R12.3 Rule Generation from Natural Language

User types: *"When stock is below 10, notify warehouse and create a purchase order"*

AI (deterministic pattern matching) generates:

```json
{
  "rule_id": "AUTO_GEN_001",
  "condition": {"type": "comparison", "field": "QtyOnHand", "operator": "<", "value": 10},
  "actions": [
    {"type": "notify", "role": "warehouse", "message": "Low stock alert"},
    {"type": "create_document", "table": "C_Order", "doc_type": "PURCHASE_ORDER"}
  ]
}
```

**User confirms** → rule saved to `biz_rules` table.

**Why this is not a black box:** Every "AI" function is a deterministic mapping from patterns to SERE JSON. User sees the generated rule before saving. No hidden weights. No model updates.

#### R12.4 Integration with R9 (Self-Healing Kernel)

- R9 observes patterns → R12.1 acts on them
- R12.2/R12.3 user commands → logged to `kernel_ops` → R9 learns new patterns
- Closed loop, fully deterministic, fully auditable

**Security guarantee:** *"Our AI never calls home. Your data never leaves your browser. Every decision is explainable and can be traced to a line of code you can read."*

---

### New Section: R13. Universal Legacy Logic Translation Framework

**Goal:** Migrate business rules from iDempiere, Odoo, and SAP into SERE (Spatial ERP Rules Engine) format — preserving intent, not code.

**Status:** Planned — After R2 (Full iDempiere data export) proves the pattern.

#### R13.1 iDempiere → SERE Translator (Priority)

| iDempiere Artifact | SERE Target | Translation Method |
|--------------------|-------------|--------------------|
| AD Callout (Java) | SERE condition + action | Parse Java AST → map to JSON |
| AD Workflow (XML) | SERE sequence | Direct XML → JSON transformation |
| Model Validator | SERE constraint | Extract condition and error message |
| Document Engine | `kernel_ops` + journal | Already compatible |

**CLI command:** `./migrate-legacy --source idempiere --db postgres://localhost/idempiere --output rules.json`

**Success metric:** 80% of rules translate automatically, 20% require 5 minutes of manual adjustment via rule builder.

#### R13.2 Odoo → SERE Translator

| Odoo Artifact | SERE Target | Translation Method |
|---------------|-------------|--------------------|
| `@api.constrains` | SERE condition | Parse Python AST → map |
| `@api.depends` (compute) | SQL view or trigger | Rewrite as materialized view |
| XML workflow | SERE sequence | Direct mapping (similar structure) |
| `ir.model.constraint` | SERE constraint | Extract SQL condition |

**CLI command:** `./migrate-legacy --source odoo --db postgres://localhost/odoo --output rules.json`

#### R13.3 SAP → SERE Translator (via IDoc)

| SAP Artifact | SERE Target | Translation Method |
|--------------|-------------|--------------------|
| ABAP validation | SERE condition | Pattern-match common patterns (credit check, quantity limit) |
| Pricing procedure | SERE calculation chain | Map access sequences to formula steps |
| Workflow (WF) | SERE sequence | Step-by-step translation |

**CLI command:** `./migrate-legacy --source sap --idoc-file sap_export_idoc.xml --output rules.json`

#### R13.4 Universal Rule Testing Framework

After translation, automatically:
1. Run original test cases (if available) against SERE rules
2. Generate "diff report" showing logic preserved
3. Flag uncertain translations for human review

**Why this belongs in the roadmap:** Answers the #1 enterprise concern: *"Can we keep our 20 years of business logic?"* Answer: *"Yes — but expressed cleanly, not entanglely."*

---

### New Section: R14. Killer Demo Suite (60-Second Sales Tool)

**Goal:** Provide a self-contained, one-click demo that proves the entire ERP cycle in under 5 minutes — designed for sales calls, trade shows, and self-guided evaluation.

**Status:** Planned — Uses R11 as foundation.

#### R14.1 The Kitchen Demo (5-Minute Cycle)

| Step | Action | Time | Proof Point |
|------|--------|------|-------------|
| 1 | Click "Kitchen Demo" → scan 5 products | 60s | Zero-config setup |
| 2 | Tap products in 3D store → add to cart | 60s | Spatial POS |
| 3 | Checkout → QR receipt appears | 30s | e-Invoice with inventory summary |
| 4 | Low stock alert → click "Replenish" | 30s | Auto PO creation |
| 5 | Simulated delivery → stock restored | 30s | Complete supply chain |
| 6 | Sell same item again → verify stock | 60s | Full cycle proven |

**Total:** 5 minutes from blank browser to proven ERP cycle.

#### R14.2 Embedded Demo Script (In-UI)

When user clicks "Start Demo Tour":
- Highlight each UI element as steps progress
- Show tooltips explaining what's happening
- Display timer: "You've completed in 3:42 — 78% faster than traditional ERP setup"
- End with: "Ready to use your own products? Click here."

#### R14.3 Benchmark Dashboard

Display real-time metrics during demo:

| Metric | Current | vs. Industry Average |
|--------|---------|---------------------|
| Time to first product | 12s | 94% faster |
| Setup steps | 1 click | vs. 47 steps |
| Lines of code written | 0 | vs. "customization required" |
| Offline capable | Yes | vs. "requires internet" |

**Why this belongs in the roadmap:** Sales tool that proves value before the customer asks "but can it do X?"

---

### New Section: R15. Developer & Partner Ecosystem

**Goal:** Make it as easy to extend Spatial ERP as it is to use it — with MIT-licensed templates, rule marketplace, and AI-assisted customization.

**Status:** Planned — Post R8 (Domain packs).

#### R15.1 Rule Marketplace (JSON Exchange)

```json
{
  "rule_id": "RETAIL_PROMO_BOGO",
  "name": "Buy One Get One Free",
  "industry": "retail",
  "downloads": 234,
  "rating": 4.8,
  "sere": { ... }  // Full rule JSON
}
```

- Users import with one click
- Submit rules back to marketplace (optional)
- Enterprise version can have private marketplace

#### R15.2 Custom Handler Templates

```javascript
// handlers/my_industry.js — template with TODO markers
class MyIndustryHandler extends BaseHandler {
  async onOrderCreate(order) {
    // TODO: Add your logic here
    // Access: this.db, this.globe, this.kernelOps
  }
}
```

**Generator:** `./generate-handler --industry "pharmaceutical" --rules compliance,serial_tracking`

#### R15.3 Vibe Coding Assistant (For Developers)

Developer types: *"Add a handler that validates serial numbers against a local CSV"*

AI generates (deterministic pattern matching):

```javascript
class SerialValidator extends BaseHandler {
  async validate(orderLine) {
    const serials = await this.loadCSV('/data/serials.csv');
    if (!serials.includes(orderLine.serial_no)) {
      throw new Error(`Serial ${orderLine.serial_no} not found`);
    }
  }
}
```

**Not magic:** Pattern matches "validate", "serial number", "CSV" → template instantiation.

---

## Updated Implementation Priority Table

| Priority | Item | Status | Est. Sessions | Notes |
|----------|------|--------|---------------|-------|
| **P0** | R2 — Full iDempiere data export | ✅ DONE (S258) | 1-2 | Foundation |
| **P1** | R1 — FTS5 Smart Search | ✅ DONE (S258) | 2-3 | Core UX |
| **P2** | R10 — Dynamic Bubble Traversal | ✅ DONE (S258) | 2-3 | Spatial nav |
| **P3** | R4 — Benchmark suite | Next | 1 | Validation |
| **P4** | R11 — Kitchen-to-Warehouse Fast Setup | **NEW** | 2-3 | **Killer demo** |
| **P5** | R5 — Offline OPFS persistence | Planned | 1-2 | Offline core |
| **P6** | R12 — Landed Deterministic AI | **NEW** | 2-3 | No black box |
| **P7** | R3 — Database sharding | Planned | 2 | Scale |
| **P8** | R13 — Legacy Logic Translation | **NEW** | 2-3 per source | Enterprise adoption |
| **P9** | R6 — CRDT sync | Planned | 3-4 | Multi-user |
| **P10** | R14 — Killer Demo Suite | **NEW** | 2 | Sales tool |
| **P11** | R7 — Migration scripts (Odoo, SAP) | Planned | 2 per source | Via R13 |
| **P12** | R8 — Domain packs | Planned | 1-2 per domain | Expansion |
| **P13** | R9 — Self-Healing Kernel | Planned | 2 | Advanced |
| **P14** | R15 — Developer Ecosystem | **NEW** | 2-3 | Community |

---

## Revised Roadmap Summary (End of Document)

### What Makes This Different (Updated)

| Dimension | Conventional ERP | Spatial ERP OOTB |
|-----------|-----------------|------------------|
| **Setup time** | Days to weeks | **60 seconds (kitchen demo)** |
| **AI** | Black box API calls | **Landed deterministic (auditable)** |
| **Legacy migration** | Rip and replace | **Logic translation to SERE** |
| **Demo cycle** | Hours of slides | **5 minutes, self-guided** |
| **Extension** | Custom code (entangled) | **Templates + marketplace** |
| **License** | $$$ per user | **MIT (even enterprise add-on)** |

### The One-Line Pitch (Updated)

> *"Scan your kitchen. Sell in 3D. Restock with one click. Migrate your legacy rules. No black boxes. No entanglement. MIT licensed."*

### Call to Action (Updated)

```bash
# Clone the revolution
git clone https://github.com/red1oon/BIMCompiler

# Open your first store in 60 seconds
cd BIMCompiler
open kitchen_demo.html

# Migrate your legacy rules
./migrate-legacy --source idempiere --db your_db

# Extend with templates
./generate-handler --industry "your_industry"

# Join the ecosystem
# Submit rules. Share demos. Fork freely.
```

---

**The roadmap is not a promise. It's an invitation.**

*Last updated: 2026-05-18 — R16 (Cascading Drill Accordion) DONE (S259c). Added R11-R15 previously.*

Based on the roadmap document you shared and our extensive discussion about validation layers, legacy migration, and spatial purity, here is my recommendation for **ADDENDUM 2** – a section that bridges the existing roadmap with the pragmatic, legacy-friendly, validation-aware approach we've been exploring.

---

## ADDENDUM 2: Pragmatic Validation & Legacy Migration Layer

### Preamble

The core roadmap (R1–R10) establishes a powerful spatial ERP engine with search, globe traversal, offline sync, and self‑healing capabilities. However, enterprise adoption – especially from legacy ERP users (iDempiere, SAP, Odoo, Navision) – requires one additional layer: **a generic, metadata‑driven validation and migration framework** that respects the 5‑table core while accommodating the declarative rules and foreign key expectations of legacy systems.

This addendum does **not** replace any existing roadmap item. It adds a **parallel track** (P‑series) that can be implemented incrementally, starting immediately after R2 (full iDempiere data export).

---

### P1. Generic Validation Layer (ERP‑agnostic)

**Goal**: Store and evaluate validation rules (range checks, existence constraints, cross‑field logic, dynamic lookups) as data, not code – supporting any legacy ERP's rule semantics without bloating the core schema.

**Implementation** (adds 1 table, 1 generic handler):

```sql
CREATE TABLE validation_rules (
    id          TEXT PRIMARY KEY,
    rule_name   TEXT NOT NULL,
    applies_to  TEXT NOT NULL,      -- e.g., 'documents.doc_type=SALES_ORDER'
    rule_type   TEXT NOT NULL,      -- 'sql_check', 'js_condition', 'lookup_filter', 'range'
    priority    INTEGER DEFAULT 0,
    is_active   BOOLEAN DEFAULT 1,
    definition  TEXT NOT NULL,      -- JSON (see examples below)
    error_message TEXT,
    legacy_source TEXT,             -- 'iDempiere', 'SAP', 'Odoo', etc.
    legacy_rule_id TEXT,
    created     TEXT,
    modified    TEXT
);
```

**Rule definition examples** (JSON, human‑readable, mappable from any ERP):

| Legacy Concept | Example Rule | Stored `definition` |
| :------------- | :----------- | :------------------ |
| iDempiere `AD_Val_Rule` | Only active products in current warehouse | `{"type":"sql","query":"SELECT 1 FROM items WHERE product_ref=? AND metadata->>'$.is_active'='Y'","bind_params":["$.product_id"],"expect":"exists"}` |
| SAP ABAP validation | Quantity > 0 | `{"type":"range","field":"$.qty","min":0}` |
| Odoo `@api.constrains` | Start date ≤ end date | `{"type":"js_condition","script":"params.start_date <= params.end_date"}` |
| FK existence | Container must exist | `{"type":"exists","table":"containers","where":"id = ?","bind_params":["$.container_id"]}` |

**Integration with `commitOp`**:

```javascript
function commitOp(db, opType, params) {
    // Before writing to kernel_ops, evaluate all applicable rules
    const rules = db.exec(
        `SELECT * FROM validation_rules 
         WHERE applies_to = ? OR applies_to = ?
         AND is_active = 1 ORDER BY priority`,
        [opType, `${opType}.${params.field}`]
    );
    for (const rule of rules) {
        if (!evaluateValidationRule(rule, params, db)) {
            throw new Error(rule.error_message);
        }
    }
    // ... existing kernel_ops write logic ...
}
```

**Why this belongs in the roadmap**:

- **Unlocks enterprise trust** – rules are explicit, auditable, and migratable.
- **No core bloat** – one small table, one generic handler. No 900‑table explosion.
- **Gradual adoption** – legacy rules can be imported as‑is (via `legacy_source`) and later refined.
- **Supports R9 (Self‑Healing Kernel)** – rule violations become pattern triggers.

**Priority**: **P1.5** (after R2, before R4). Estimated effort: 1–2 sessions.

---

### P2. One‑Command Legacy Import (Migrate, Don't Rewrite)

**Goal**: Extend the existing `export_ad.sh` (iDempiere) and create analogous scripts for SAP, Odoo, Navision – each producing a fully queryable `.db` file that works immediately with the globe, search (R1), and bubble traversal (R10).

**Unified command pattern**:

```bash
# iDempiere (PostgreSQL)
./migrate-from-idempiere.sh --dburl "postgresql://..." --output idempiere_seed.db

# Odoo (PostgreSQL or JSON‑RPC)
./migrate-from-odoo.sh --dburl "postgresql://..." --output odoo_seed.db

# SAP (IDoc export or RFC)
./migrate-from-sap.sh --idoc-dir ./exports/ --output sap_seed.db

# Navision (C/AL backup or Excel)
./migrate-from-navision.sh --backup ./Navision.backup --output navision_seed.db
```

**What each script produces**:

- Full iDempiere AD tables (370 windows, 1130 tabs, 20911 fields) – already done in R2.
- Master data mapped to the same 9‑table schema (or to iDempiere AD tables – because the globe already renders them).
- `validation_rules` populated from legacy rule tables (e.g., iDempiere `AD_Val_Rule`, Odoo `ir.rule`).
- `kernel_ops` seeded with `LEGACY_IMPORT` ops for auditability.
- FTS5 index rebuilt automatically (R1).

**Key insight**: Because the globe and search already render **any** table that follows the AD pattern (iDempiere schema), a legacy script only needs to produce a SQLite database with the same table names and columns. The existing UI (R1, R10) works without modification.

**Priority**: **P2** (parallel to R3). Estimated effort: 1–2 sessions per legacy system, starting with iDempiere (already partially done in R2).

---

### P3. Declarative Lookup & Drill‑Through Metadata

**Goal**: Restore iDempiere‑style dynamic lookups (FK filters) and zoom‑across drilling without hard‑coding – using the same generic metadata table pattern.

**Add two small tables** (or extend `validation_rules` with a `rule_type` = `lookup` or `drill`):

```sql
CREATE TABLE lookup_definitions (
    id             TEXT PRIMARY KEY,
    lookup_for     TEXT NOT NULL,   -- e.g., 'document_lines.item_id'
    query_template TEXT NOT NULL,   -- SQL with ? placeholders
    display_field  TEXT DEFAULT 'name',
    value_field    TEXT DEFAULT 'id'
);

CREATE TABLE drill_mappings (
    id             TEXT PRIMARY KEY,
    from_doc_type  TEXT,            -- e.g., 'SALES_ORDER'
    from_field     TEXT,            -- metadata field, e.g., 'source_doc'
    to_doc_type    TEXT,            -- e.g., 'SHIPMENT'
    to_condition   TEXT             -- SQL WHERE clause with ? for record ID
);
```

**Integration with UI**:

- When the user taps a lookup field, the generic `getLookup()` function executes the stored `query_template` and displays results as a filtered card stack (spatial, not dropdown).
- When the user taps a "Drill" button, the UI queries `drill_mappings` and offers to open the related document card.

**Why this is lightweight**: The swipe card stack already handles dynamic filtering (`?scope=...`). Lookup definitions just parameterise that filtering.

**Priority**: **P3** (after P1, before R6). Estimated effort: 1 session.

---

### P4. Legacy Rule Conversion Tool (iDempiere → Generic Format)

**Goal**: Provide a one‑click utility that reads an iDempiere `AD_Val_Rule` (or any legacy rule table) and outputs a corresponding JSON `definition` for the generic validation layer (P1).

**Implementation**: A small JS function or separate script that:

1. Connects to the legacy database (or reads an exported dump).
2. For each rule in `AD_Val_Rule`:
   - Parses the `Code` (SQL WHERE clause).
   - Converts iDempiere column names to OOTB JSON paths (e.g., `M_Product.IsActive` → `metadata->>'$.is_active'`).
   - Wraps in `{"type":"sql","query":"...","expect":"exists"}` format.
3. Inserts the converted rule into `validation_rules`, preserving `legacy_source='iDempiere'` and `legacy_rule_id`.

**Why this matters**: It proves the generic layer can **absorb** legacy complexity without losing information. The converted rules work immediately; no manual re‑coding.

**Priority**: **P4** (after P1, before P7). Estimated effort: 1 session.

---

### Integration with Existing Roadmap

| Existing Item | How Addendum Complements |
| :------------ | :----------------------- |
| **R1 (Smart Search)** | Search results can include validation rule violations (P1) as "issues to fix". |
| **R2 (Full iDempiere Data Export)** | Extend to export `AD_Val_Rule` into `validation_rules` table. |
| **R4 (Benchmark Suite)** | Add benchmarks for rule evaluation (e.g., 1000 rules applied to 10K ops). |
| **R6 (CRDT Sync)** | Validation rules become part of the sync payload – rules are data, not code. |
| **R7 (Migration Scripts)** | Directly produce `validation_rules` and `lookup_definitions` from legacy sources. |
| **R9 (Self‑Healing Kernel)** | Rule violations feed the pattern detection engine (e.g., frequent `QTY_RANGE` violation → suggest inventory audit). |
| **R10 (Bubble Traversal)** | Show validation rule status as bubble colour (red = rule violation, green = passes). |

---

### Summary of Addendum 2 Items (P‑series)

| Priority | Item | Description | Est. Sessions |
| :------- | :--- | :---------- | :------------ |
| **P1** | Generic Validation Layer | `validation_rules` table + generic evaluator | 1–2 |
| **P1.5** | Integration with `commitOp` | Hook rule evaluation into existing op flow | 0.5 |
| **P2** | One‑Command Legacy Import | Scripts for iDempiere (extend R2), Odoo, SAP, Navision | 1–2 each |
| **P3** | Lookup & Drill Metadata | `lookup_definitions`, `drill_mappings` tables + UI integration | 1 |
| **P4** | Legacy Rule Conversion Tool | iDempiere `AD_Val_Rule` → generic JSON | 1 |
| **P5** | Benchmark for Rules | Add to R4 benchmark suite | 0.5 |

---

### Conclusion

This addendum does **not** add 900 tables. It adds **at most 3 small metadata tables** and a handful of generic handlers. It enables:

- **Enterprise trust** – rules are explicit, auditable, and migratable.
- **Legacy friendliness** – existing SAP/Odoo/iDempiere customers see a clear path.
- **Spatial purity preserved** – the core 5‑table + `kernel_ops` architecture remains untouched.

**Recommendation**: Insert **ADDENDUM 2** after R2 (since R2 already proves iDempiere data export) and before R4 (so benchmarks can include rule evaluation). Implement P1 and P2 first – they deliver the most value for legacy users. P3 and P4 are optional refinements.

Would you like me to draft a **sample `validation_rules` import script** for iDempiere’s `AD_Val_Rule` as a concrete starting point?

Based on your existing roadmap and our deep discussion about the cutting-edge sensory and performance layers, my strong recommendation is to add these as **ADDENDUM 3: Immersive Experience & Extreme Performance Layer**. This directly builds on your existing P-series addendum without conflicting with any planned core work.

### 🎯 ADDENDUM 3: Immersive Experience & Extreme Performance Layer

This addendum adds the sensory and performance capabilities we discussed, and directly leverages your existing `kernel_ops` architecture to drive them. The implementation is designed to be phased, with quick wins immediately available and GPU-compute features maturing rapidly.

#### **X1. Photorealistic Environmental Rendering**
*   **What**: Replace flat lighting with physically accurate sun, sky, and atmospheric scattering.
*   **Implementation**: Integrate `@takram/three-atmosphere` library (Three.js r184+). Add `RGBELoader` + `PMREMGenerator` for HDR environment maps.
*   **Ties to Your Roadmap**: Directly enhances **R10 (Bubble Traversal)** and your 4D schedule viewer. The sky state can be driven by the 4D timeline in `kernel_ops`.

#### **X2. Construction-Grade Particle Effects**
*   **What**: Welding sparks, concrete dust, and heavy-machinery exhaust using GPU-compute particles.
*   **Implementation**: Use `THREE.Points` for CPU-based particles (up to 100K) immediately. Start experimenting with WebGPU compute shaders for GPU-driven particles (the `@newkrok/three-particles` library is emerging as a standard).
*   **Ties to Your Roadmap**: These can be triggered by activity phases from your 4D schedule, managed by the **R9 Self-Healing Kernel** as part of the project state.

#### **X3. Spatial Audio Soundscapes**
*   **What**: 3D positional audio for construction, spatial UI feedback, and immersive data sonification.
*   **Implementation**: Attach `THREE.PositionalAudio` sources to your 3D objects and camera. The Web Audio API's HRTF panning creates realistic 3D sound on standard headphones.
*   **Ties to Your Roadmap**: Complements **R10 (Bubble Traversal)** with spatial audio cues. Audio events are logged in `kernel_ops` for the pattern engine in **R9**. The `AudioContext` requires a user gesture to start, which fits your existing interaction model.

#### **X4. Extreme Performance for 100K+ Elements**
*   **What**: A multi-phase strategy for handling massive BIM and data globe scenes at 60fps.
*   **Implementation**:
    *   **Phase 1 (Instancing)**: Convert repeated elements to `THREE.InstancedMesh`.
    *   **Phase 2 (Batching)**: Group unique geometries by material into `THREE.BatchedMesh`.
    *   **Phase 3 (GPU-Driven)**: Use WebGPU compute shaders for GPU-driven culling when browser support solidifies (the experimental `WEBGPU_RENDERER` flag is already in Three.js).
*   **Ties to Your Roadmap**: This directly supports **R3 (Database Sharding)** by optimizing the visual side of massive data loads. The benchmarks in **R4** should include render-loop metrics (draw calls, frame time).

### 💡 Why This Fits Your Roadmap Now

*   **No Core Bloat**: Like your P-series, these are sensory and render-loop enhancements. They do not touch your 5-table schema, `doc_engine`, or `kernel_ops` logic.
*   **Leverages Existing Kernel**: Your `kernel_ops` log and 4D schedule state machine are the perfect drivers for these effects. The "orchestration" layer you've already built is exactly what's needed to manage particle emitters and audio sources.
*   **Emerging but Practical**: The tools are crossing from experimental to stable *right now*. Starting with the CPU-based phases gives you immediate wins, while the GPU-compute path is becoming a standard feature of the modern web.

### 📝 Proposed Insertion & Priority

I recommend placing this **ADDENDUM 3 after R10 (Dynamic Bubble Traversal)** , as it directly enhances the spatial experience you've built. The implementation can begin immediately with the CPU-based phases, putting you perfectly on track to adopt the GPU-driven features as they become mainstream in the coming months.

This would make your roadmap not just about what an ERP *does*, but how it *feels*—a true next-generation experience.
