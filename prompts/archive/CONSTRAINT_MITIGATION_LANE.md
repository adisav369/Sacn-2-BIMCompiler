# ⚠ DO NOT REMOVE — CONSTRAINT-MITIGATION LANE (pre-pilot hardening; resume card)
# Paste-to-start (NEW SESSION): `proceed with prompts/CONSTRAINT_MITIGATION_LANE.md`
# Scope: implement the browser/scale mitigations the analysis recommends, so the engine survives a real
#   50-device / 100M-op / mobile deployment. SOURCE OF TRUTH (the WHY + the numbers): docs/FoldEngineConstraints.md
#   — this card is its executable backlog. The doc is reference; THIS is what you run.
# Non-negotiable: spec-first · witness-led (§-log first, READ the log) · NON-INVENT · consume the build/erp/
#   engine through the window.ERP seam, never fork a verb · EXPLICIT GO before any deploy · bump sw.js on deploy.
#
# ▶ SEQUENCING (do NOT start ahead of equivalence): the constraints doc's own scorecard shows NOTHING is 🔴 at
#   current prototype scale (single device, <1M ops) except single-writer (immutable, un-mitigable by design).
#   So this is a PRE-PILOT GATE, not now-work. Run GAP_CLOSURE (equivalence) first; open this when a real
#   multi-device / mobile pilot is imminent. Each item below is file-disjoint from GAP_CLOSURE → parallel-safe.
#
# ▶ TIERED SHARDING IS NOT HERE — it's the OOM/genesis P0 and it lives in its OWNER lane:
#   prompts/ERP_DATA_SHARDING_SESSION.md §QUEUED (T0/T1/T2 + shard_loader.js). Do that THERE, not in this card.

## BACKLOG (from docs/FoldEngineConstraints.md §7 — top-down; one item → spec → build → §-witness → flip the
##           doc's Phase-4 scorecard row → next)

**P0 — demo/production safety (cheapest, highest leverage)**
1. **`bootstrap_path` metric + checkpoint-first enforcement.** On init: load the signed checkpoint, fold only
   the post-checkpoint delta; genesis ONLY as explicit recovery, run in a Web Worker with progress UI (never
   block main thread). Log `§BOOTSTRAP path=checkpoint|genesis ms=…`. **§FALSIFIER:** force a missing checkpoint
   on mobile-emulation → must take the worker-genesis path, never a 25 s main-thread freeze. (~120 LOC
   `kernel_ops.js` + ~40 worker shim.) ZERO dependency on the folds — safe to do anytime.
2. **Size-triggered signed compaction.** When `PRAGMA page_count`×page_size > 100 MB (or `open_period_ops` >
   300k): fold open-period → new checkpoint, sign it (sha256 chain, prev-hash link), truncate folded ops.
   Independent of period-close. Witness **W-COMPACT-SIGNED** `maxDiff=0c` (post-compaction fold == pre-compaction
   fold) + **§FALSIFIER** (skip the prev-hash link → chain breaks). (~200 LOC `kernel_ops.js`.)
   ⚠ SOFT-DEP: checkpoint *contents* shift as GAP_CLOSURE's T_* folds land — do this AFTER that lane drains, or
   re-verify when it does.
3. **VFS detect + IndexedDB fallback.** Feature-detect `navigator.storage.getDirectory` + COOP/COEP → OPFS VFS
   (2.5×) else IndexedDB VFS. Log `§VFS chosen=opfs|idb`. Document the GH-Pages IDB-only reality so it isn't
   mistaken for a regression. (~80 LOC bootstrap.)

**P1**
4. **Bounded offline queue + backoff relay.** Cap 50 MB or 7 days; on cap → force local checkpoint, purge
   folded+relayed ops, KEEP unrelayed; exponential-backoff retry (cap 5 min). Witness: cap fires + purge keeps
   unrelayed. (~140 LOC relay module.)
5. **Dictionary-tag the 0.1% shared op-classes** so quorum-CAS (W-QUORUM-CAS, 18 ms window, proven) fires ONLY
   on stock/credit classes, not every op. (~60 LOC, mostly tagging.)

**P2**
6. **Battery-aware worker folding** (`navigator.getBattery()` → throttle non-urgent folds <20% & discharging) +
   **service-worker cache** of WASM/checkpoint for instant resume + `navigator.storage.persist()`. (~90 LOC.)

## MONITORING (build alongside; thresholds in docs/FoldEngineConstraints.md §6)
`db_file_size_mb` · `quota_used_pct` · `open_period_ops` · `bootstrap_path` · `vfs_backend` ·
`offline_queue_mb` · `cas_retry_rate` · `fold_ms_p95` · `battery_pct` — each with its alert threshold + action.

## CONSTRAINTS
- Browser-only solutions (the point is serverless); do NOT add a server. Do NOT drop SQLite-WASM.
- Architecture fixed: per-device shard · signed op-log · async relay · single-writer-per-shard (HARD, leave it).
- Witness on **mobile emulation** (UA + 4× CPU throttle) for anything claiming a mobile limit.

## PER-ITEM OUTPUT
Item · LOC (engine+witness) · §-witness line + §FALSIFIER · the `FoldEngineConstraints.md` Phase-4 row flipped
to ✅/⚠️ with its residual-risk verdict.

## RESUME STATE (updated 2026-06-13 — fold-independent subset DRAINED while GAP_CLOSURE in flight)

| # | Item | Module(s) | Witness | Status |
|---|------|-----------|---------|--------|
| 1 | checkpoint-first bootstrap + `bootstrap_path` metric + genesis→worker | `build/erp/bootstrap_path.js`, `genesis_worker.js` | `scripts/poc_bootstrap_path.js` → `§BOOTSTRAP-PATH OVERALL=PASS` (12/12; F1 mobile-no-worker REFUSES; heartbeat-proven off-main-thread; checkpoint work flat in N; worker state==inline maxDiff=0c) | ✅ DONE |
| 3 | VFS detect + IndexedDB fallback | `build/erp/vfs_detect.js` | `scripts/poc_vfs_detect.js` → `§VFS-DETECT OVERALL=PASS` (10/10; GH-Pages IDB-only named; no silent downgrade) | ✅ DONE |
| 4 | bounded offline queue + backoff relay | `build/erp/offline_queue.js` | `scripts/poc_offline_queue.js` → `§OFFLINE-QUEUE OVERALL=PASS` (17/17; cap bytes&age → checkpoint+purge KEEPS unrelayed; backoff ≤5 min) | ✅ DONE |
| 5 | dictionary-tag shared op-classes for CAS | `build/erp/op_class_tags.js` | `scripts/poc_op_class_tags.js` → `§OP-CLASS-TAG OVERALL=PASS` (13/13; CAS = 0.10% of 1000-op mix; all contended classes tagged) | ✅ DONE |
| 6 | battery-aware folding + persist + resume cache | `build/erp/battery_aware.js` | `scripts/poc_battery_aware.js` → `§BATTERY-AWARE OVERALL=PASS` (13/13; urgent folds never throttled) | ✅ DONE |
| **2** | **size-triggered signed compaction** | — | — | ⛔ **HELD**: SOFT-DEP on GAP_CLOSURE — checkpoint *contents* shift as the T_* folds land. Do AFTER that lane drains (or re-verify W-COMPACT-SIGNED `maxDiff=0c` when it does). |

All five DONE items are **headless-witnessed engine/policy modules in `build/erp/` (lane firewall: no renderer
edits, no deploy)**. Scorecard rows flipped in `docs/FoldEngineConstraints.md` §5 (Genesis fold, OPFS/IDB,
Writer conflict, Offline queue) + the access-freq rows (Max DB size, Mobile memory) from the sharding lane.
**Browser wiring + deploy of all of the above is deferred** (EXPLICIT GO; bump `erp/sw.js`). The node
`worker_threads`/`navigator`/`storage` shims are the witness harness — the browser uses Web Worker / real
`navigator` with the SAME message + API contracts (documented in each module header).

**NEXT when reopened:** item 2 (compaction) once GAP_CLOSURE drains, then the browser-wiring/deploy pass.
