# POS WAN-Scale Benchmark — SODA/EODA → central dump relay

> ⚠ DO NOT REMOVE — Scope: internal scalability benchmark. Read the log after every run.
> Witness: **W-POS-WAN-SCALE** (`scripts/poc_pos_wan_scale.js`). Spec-first per CLAUDE.md.
> Internal effort — touches NO live/dev surface, no deploy. Pure headless node + a localhost relay.

## Scenario
A large fleet of POS stations on a WAN. Each station keeps the **full** day detail locally and reports
only a **minimal daily fold** to a central "dump" relay — two reporting waves per station per day:

- **SODA** (start-of-day) — a goods-receipt of the morning stock replenishment: one op carrying the
  folded receipt `{components:{pid:qty}, units}` (M+ stock-in). The components and quantities are the
  **real GardenWorld BOM** of the station's tile (`pp_product_bom(line)`), never invented.
- **EODA** (end-of-day, "Close Cash") — one op carrying the day's fold: `{sales, voided, net_cent,
  consumed:{pid:qty}}`. `consumed` is `POSCore.backflushOps` over the day's **non-voided** sold lines
  (the LATE Close-Cash fold proven by `W-POS-EODA`); `net_cent` is a BigDecimal fold of the sealed
  master price (`m_productprice`) — money to the cent, never raw JS Number.

"Minimal" is the load contract: central receives **2 ops per station per day**, independent of how many
sales each station rang. The op log is the sync spine; the relay (`erp_relay_server.js`) is the **dumb
facilitator** — it sequences (assigns canonical total order, dedupes by `op_uuid`) and folds nothing.

The per-station daily *unit* (price 500, BOM {133×4,134×1,135×1}, bp 112, station 100/wh 104/plv 104)
is **extracted** from `build/erp/ad_seed_fullwidth.db`. The fleet size **N is the benchmark variable** —
N stations replay the real station-100 unit. A deterministic per-station shrink (1 voided sale every
7th station) makes EODA-consume < SODA-receipt so the loop leaves real residual on-hand.

## Reused building blocks (non-invent)
- `build/erp/erp_relay_server.js` — `createRelayServer({port,persistPath})`: `/push` (idempotent by
  `op_uuid`, durable JSONL write-ahead), `/snapshot?after=N`, `/head`, boot-replay on restart.
- `build/erp/pos_core.js` — `buildSaleGroup`, `backflushOps`, `ringLine`, `cartTotal`.
- `scripts/poc_email_dr.js` pattern — EC P-256 sign + AES-256-GCM encrypt-to-user signed snapshot,
  `recover()` picks the tip by max signed seq. Reused inline for the email backup leg.

## Named issues (each test proves/disproves one)
- **B1 ACCEPT THROUGHPUT** — relay accept rate (ops/s, pushes/s) as N scales; sub-linear wall-clock?
- **B2 WAN LATENCY** — per-push RTT distribution (measured loopback) + a deterministic per-station WAN
  one-way model overlaid → end-to-end p50/p95/p99. (A model, like `§QUORUM-RTT-MEASURED`.)
- **B3 IDEMPOTENT RETRY** — re-push a fraction of stations (WAN flap/retry). `accepted=0` extra, relay
  `head` unchanged, central aggregate unchanged → no double-count.
- **B4 MINIMAL-REPORT RECONSTRUCTION** — fold the 2N relayed ops at central → Σ sales, Σ net_cent,
  Σ consumed per component **== expected** (N × unit, to the cent). The minimal report reconstructs the
  full central position.
- **B5 RELAY DISASTER (crash→restart)** — kill the relay after the waves; reopen on the same
  `persistPath`; boot-replay → `head == 2N`, re-folded aggregate identical → canonical order + central
  position survive relay total loss.
- **B6 POS TOTAL FAILURE (email backup)** — a sampled station loses device + relay-side ops; recover its
  last **signed** EODA snapshot from the inbox fixture → `projectionHash == pre-failure`. Forged snapshot
  rejected. Per-station cost = O(1) (one EC sign).
- **B7 SODA LOOP CLOSES** — SODA receipt (M+) and EODA consume (P-) ride the same real BOM recipe; the
  net per component = receipt − consume, with the shrink (voided sales) the only residual → the loop is
  closed by construction, not parallel code.

## Constraint gap vs real iDempiere (which DB constraints bite at scale)
The relay is a **dumb sequencer** — its only enforced constraint is `op_uuid` uniqueness (idempotency).
Real iDempiere enforces many more at the DB layer, applied when the minimal fold **materializes** on
central replay. The benchmark decides which have scaling impact (one normal station's *full* local detail
= 51 constrained writes across 9 documents, from the real `buildSaleGroup` — non-invent):

- **G1 AD_Sequence / DocumentNo (the classic bottleneck → REMOVED).** iDempiere allocates `DocumentNo`
  from a central `AD_Sequence` row under `SELECT … FOR UPDATE` — one global lock per document = a
  serialization point that throttles N concurrent stations. We **partition** the number by
  `station|date|local-seq`: collision-free with **no central lock**, riding the op-log's own O(1) append.
  Test proves 0 collisions partitioned vs the global-counter collision count.
- **G2 Materialization fan-out (LINEAR, no contention).** The WAN stays minimal (2 ops/station); the
  central write-set is where FK / NOT-NULL / UNIQUE / `fact_acct` GL constraints actually cost. Amplification
  = central materialized rows (51×N) ÷ WAN ops (2×N) ≈ 25×, plus GL ∝ lines — but it is **O(N), no
  cross-station contention once numbering is partitioned**. The minimal report shifts cost from the WAN to
  central replay CPU/storage; that is the real ceiling, and it scales linearly.
- **G3 FK ordering (residual correctness gap, not a scaling one).** A sale referencing a station-local NEW
  master (`§P-9` registered product) needs that `M_Product` op ordered before it. Same-station op-log
  preserves causal order; a **cross-station shared new master** is the open FK gap. EODA folds reference
  seed masters only → out of scope here, flagged so it is not silently claimed as covered.

Decision: of the real-ERP constraints, only **centralized sequence allocation** was a true scaling
bottleneck — and partitioned numbering removes it. NOT-NULL/default-fill and GL posting are O(N) central
replay cost (linear); FK ordering is a correctness residual, not a throughput limit.

## Scales & run
Default sweep N ∈ {200, 1000, 5000, 10000}. Override: `node scripts/poc_pos_wan_scale.js 500,2000`.
Determinism: no `Date.now`/`Math.random` in the model (per-station shrink + WAN latency derived from a
hash of the station id). Wall-clock timings are *measurements* (reported, not asserted). PASS gate =
B3..B7 exact + B1/B2 reported. Full output → `build/erp/poc_pos_wan_scale.log` — read the log.
