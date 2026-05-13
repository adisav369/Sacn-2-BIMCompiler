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
| `ad_ui.js` + `ad_data.js` + `ad_charts.js` — AD renderer | Done (S256) | 132 | [ERP_AD_UI.md](../prompts/ERP_AD_UI.md) |
| `kernel_ops` — append-only op log with user_tag | Done (S255) | via doc_engine | [SpatialERP_POC.md](../prompts/SpatialERP_POC.md) |
| Data Globe — spatial bubble navigation | Done (S257) | 153 | [SpatialERP_OOTB.md §6.5](SpatialERP_OOTB.md) |
| Service Worker + offline mode | Done (BIM OOTB) | T13 | `deploy/dev/sw.js` |
| `erp.html` — standalone card-swipe ERP shell | Done (S256) | via test_ad_ui | [ERP_AD_UI.md](../prompts/ERP_AD_UI.md) |
| AD seed: 370 windows, 1130 tabs, 20911 fields, 1003 AD tables | Done (S255) | — | `deploy/dev/ad_seed.db` |
| GardenWorld data: 18 BPartners, 55 Products | Done (S255) | — | `scripts/export_ad.sh` |

---

## R1. Smart Search (FTS5 + Pattern Detection)

**Goal:** Single search box that replaces menu navigation entirely. Type anything — document number, partner name, product, amount, date phrase — and land on the right record instantly.

### R1.1 FTS5 Virtual Table

Create an FTS5 virtual table over all searchable master and transaction data:

```sql
CREATE VIRTUAL TABLE erp_search USING fts5(
  doc_no,             -- INV-001, PO-2024-003, SO-123
  bpartner_name,      -- "Joe Block", "Patio Furniture Ltd"
  product_name,       -- "Oak Table", "Standard Chair"
  description,        -- free-text from metadata JSON
  table_name,         -- source table for result routing
  record_id,          -- PK in source table
  tokenize = 'porter unicode61'
);
```

Rebuild on seed load. Incremental update on every `ad_data.js` save/delete.

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

## Implementation Priority

| Priority | Item | Depends on | Est. sessions |
|---|---|---|---|
| **P0** | R2 — Full iDempiere data export | `export_ad.sh` extension | 1-2 |
| **P1** | R1 — FTS5 Smart Search | R2 (needs data to search) | 2-3 |
| **P2** | R4 — Benchmark suite | R2 + R1 (needs data + queries to measure) | 1 |
| **P3** | R5 — Offline OPFS persistence | SW already done, OPFS is incremental | 1-2 |
| **P4** | R3 — Database sharding | R4 (benchmarks prove when sharding is needed) | 2 |
| **P5** | R6 — CRDT sync | R5 (offline must work first) | 3-4 |
| **P6** | R7 — Migration scripts (Odoo, SAP) | R2 (iDempiere migration proves the pattern) | 2 per source |
| **P7** | R8 — Domain packs (F&B, WMS) | R1 + R2 (search + data model proven) | 1-2 per domain |
| **P8** | R9 — Self-Healing Kernel | R1 + R4 (needs search + benchmarks to measure) | 2 |

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

*Last updated: 2026-05-14. For architecture and design rationale, see [SpatialERP_OOTB.md](SpatialERP_OOTB.md).*
