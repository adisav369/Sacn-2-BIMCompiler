# Depreciation Build — a measured witness of *server → serverless*

> A concrete, reproducible case study behind [DistributedERP.md §"From server to serverless"](DistributedERP.md).
> It answers one question with numbers: **where does an ERP's batch time actually go, and what removes it?**
> The honest finding is also a counter-hype: *most* of the win is a server-side SQL rewrite, not SQLite-WASM.

## The case

A government fixed-asset system: every asset depreciates over **40 years monthly (480 periods)** before it
is useful for reporting. On iDempiere this build was recalled at **~20 minutes**; a partial Java→SQL rewrite
brought it to ~5 minutes. The build is an **End-of-Year event** — computed once, then read all year.

The depreciation maths is trivial — one line, extracted faithfully from iDempiere source:

```
expense[p] = round( (cost − accum) / (life − (p−1)), 2, HALF_UP )    # MDepreciation.apply_SL:330
accum     += expense[p]                                              # Σ == cost (last period self-corrects)
```

So if the maths is one line, where do 20+ minutes go?

## Where the time actually goes — it is the per-row save, not the maths

iDempiere's build loop (`MDepreciationWorkfile.buildDepreciation:720`) calls
`MDepreciationExp.createDepreciation(...)` **once per period**, which does, **per period**:

1. `new MAsset(...)` — a **DB reload of the asset** (`MDepreciationExp.java:~180`), and a second reload inside
   `MDepreciation.invoke:239` when a method is set;
2. `saveEx()` (`:196`) — and this is **not one INSERT**. Through the PO layer it allocates the PK from the
   centralized **`AD_Sequence`**, generates the `_UU`, fills the audit/tenancy columns, fires
   **`ModelValidationEngine`**, then inserts.

So one asset = ~480 periods × ~2 DB round-trips = **~960 round-trips**; a govt base of thousands of assets =
**~1 million+ round-trips through the PO layer.** That is the 20 minutes. The arithmetic is noise.

## The four-tier comparison (measured)

All four run the *same* workload (1,000 assets × 480 periods = 480,000 rows), all verified to **balance**
(Σ expense = cost), against the **same real Postgres** (paths 1–3) and in-process SQLite-WASM (path 4).
Reproducible: [`scripts/spike_depreciation.js`](https://github.com/red1oon/BIMCompiler/blob/full/scripts/spike_depreciation.js).
Network latency is applied **consistently to every tier** (this is the fair fight — corrected Java and the
SQL replacement run over the wire too in a real deployment).

| network RTT | 1 · PRESENT (per-row save) | 2 · CORRECTED Java (hoist + batch) | 3 · SQL replacement (set-based) | 4 · SQLite-WASM (in-process) |
|---|---|---|---|---|
| localhost (0.1 ms) | 1.1 min | 909 ms | 574 ms | 338 ms |
| **1 ms** (LAN) | **15.5 min** | 1.8 s | 574 ms | 338 ms |
| **2 ms** (typical) | **31.5 min** | 2.8 s | 575 ms | 338 ms |
| **5 ms** (remote) | **79.5 min** | 5.8 s | 578 ms | 338 ms |

Round-trips per full base: PRESENT **960,000** · CORRECTED **1,000** · SQL **1** · WASM **0**.

Reading it:

- **PRESENT** is the only tier that *explodes* with latency — it is round-trip-bound. **31.5 min at 2 ms
  confirms the recalled ~20 min**, and real iDempiere adds ModelValidator/sequence CPU on top, so it is a floor.
- **CORRECTED Java** kills 99.9% of round-trips but still does one per asset → grows with the network (sub-second to ~6 s).
- **SQL replacement** does **one** round-trip total → **essentially network-immune** (574–578 ms across all RTT).
  This is the single biggest lever in the table.
- **SQLite-WASM** is the same ballpark as the SQL replacement (~1.7× faster), **not** an order beyond it.

## The honest decomposition

The 20-min → sub-second win is **the SQL replacement, not SQLite-WASM.** A competent server-side set-based
rewrite gets you there and is network-immune. SQLite-WASM does **not** out-*build* a proper SQL rewrite.

SQLite-WASM's advantage is **architectural, not throughput**:

1. **No server** — runs on the device, nothing to host;
2. **Offline** — the build runs with no connection;
3. **Local reads** — the result is already local, so every subsequent report is **~22 ms with zero server
   calls** (the EOY-then-read-instantly property; `§DEP-REPORT`).

If you have a server and want speed, **the SQL replacement is the right answer.** SQLite-WASM answers a
*different* question — *"what if there is no server at all?"*

## Can the `.save()` really be replaced? — yes, but the envelope is the hard part

`MDepreciationExp` has **no `beforeSave`/`afterSave`** — so `saveEx()` runs *no* model-specific logic on a
depreciation row. What it runs is the generic **PO envelope**: an ID from the centralized `AD_Sequence`, a
`uuid`, tenancy (`AD_Client_ID`/`AD_Org_ID`), `IsActive`, the audit columns, and the ModelValidator hook —
filling **19 NOT-NULL columns** in total. *That* is why a naive `INSERT (expense, accum…) VALUES …` fails: it
is rejected for the missing envelope, and it silently bypasses any validator.

A SQL replacement must reproduce the **whole** envelope. Proven against the live table, inside a transaction
that was **rolled back** so nothing persisted:

```
INSERTED rows=480 | null_envelope=0 | ids_contiguous=true | maxAccum=100000.00 (must=100000)
post-rollback persisted=0 (must=0)
```

The block of IDs is reserved from `AD_Sequence` in **one** round-trip (`UPDATE … SET currentnext = currentnext
+ N RETURNING …`) instead of N calls. That centralized-ID reservation is the *only* coordinated step — it is
precisely the [`DistributedERP.md §6.1` centralized-ID problem](DistributedERP.md). In the standalone
SQLite-WASM path the PK **is** an edge-minted UUID and no central sequence is consulted at all — which is the
only reason the envelope gets *simpler* there, not merely faster.

**Caveat (non-invent):** this is faithful only when **no ModelValidator with real logic** is registered
against the table in *your* instance. Core registers none on `A_Depreciation_Exp`; a customized instance must
verify its `AD_ModelValidator` scope before trusting a SQL replacement.

## Does SQLite have the same ActiveRecord impedance?

No — because **the impedance is the *pattern*, not the engine.** SQLite is pattern-neutral: you can drive it
set-based (one `INSERT … SELECT`) or row-at-a-time. Three things to hold:

- **SQLite's own "row-at-a-time tax"** is the per-statement transaction (a commit/fsync per autocommit insert).
  480k individual inserts = 480k commits = slow. The fix is one `BEGIN…COMMIT` around the batch — which is why
  path 4 is fast. The discipline (batch, don't per-row-commit) carries over; the *network* part does not exist.
- **The impedance can reincarnate locally** — not as objects, but as **per-op full re-projection.** The
  `kernel_ops` seal that re-hashes the *whole* log on every op (the O(n²) `sealChain`, the `I-D` issue) is the
  same disease in a new costume: redoing work per row/op. SQLite-WASM does *not* save you from a bad algorithm.
- **Net:** local-first removes the **round-trip** class of slowness (most of iDempiere's batch pain) and raises
  the threshold where row-at-a-time bites — but **set-based / incremental thinking still matters.** The lesson
  of this page is exactly that: the engine changed; the discipline did not.

## Witness & limits

- Reproduce: `node scripts/spike_depreciation.js` → reads `logs/spike_depreciation.log`
  (`§DEP-1-PRESENT … §DEP-4-WASM`, `§DEP-NETWORK`, `§DEP-CORRECT all-paths-balance=OK`).
- **Honest limits:** PRESENT runs on localhost and *excludes* ModelValidator/sequence CPU, so real iDempiere
  over a network is **slower**, not faster, than tier 1. The SQL replacement's 574 ms is the *ideal* pure-CTE
  on an idle DB; a real instance with load + posting + a partly-set-based rewrite explains the recalled 5 min.
  SQLite-WASM's 338 ms is in-memory; persisting the 480k-row DB durably (OPFS) is an extra one-time EOY cost.
