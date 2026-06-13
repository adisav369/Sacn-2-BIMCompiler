# Fold Engine — SQLite-WASM Browser Constraints Analysis

*Substrate: one tenant · one tab · one SQLite-WASM instance · per-device shard · signed op-log · async relay. Prototype today (<1M ops, single device). Question: **what breaks first** at 50 devices / 100M total ops / production browsers including mobile?*

> **Method note.** This is a desk calculation grounded in the project's **measured** numbers (22,500 ops/s `KernelOps.commitGroup`; checkpoint bootstrap 0.90 ms vs genesis 47.70 ms; linear to 20M ops at 437 B/op; the 18 ms quorum-CAS window from `W-QUORUM-CAS`) plus the stated browser assumptions. Every assumption is named inline. It is not a fresh benchmark — the next step is to instrument the metrics in §5 and confirm on real devices. Companion to the [Migrate & Compare paper § Disaster recovery & TCO](MigrateComparisonPaper.md#v-dr).
>
> **This doc is reference, not a runnable lane.** The mitigations below are executed from two prompt cards: tiered sharding (the OOM/genesis P0) → `prompts/ERP_DATA_SHARDING_SESSION.md §QUEUED`; everything else (checkpoint-first, compaction, OPFS/IDB, offline-queue, battery) → `prompts/CONSTRAINT_MITIGATION_LANE.md`. Both are **pre-pilot hardening** — run after the equivalence lane (`GAP_CLOSURE_LANE.md`), when a real multi-device/mobile pilot is imminent.

## 1. Executive summary — top 3 findings

1. **Conflict probability is a non-issue at target scale; single-writer is the only HARD limit.** At 50 devices × 1 op/s with a 0.1% shared op-class, same-second collision is **~0.12%**, and within the real 18 ms CAS window it is **~4×10⁻⁷**. You would need **~300–360 devices** before same-second collision reaches 5%. The architectural single-writer-per-shard rule cannot be relaxed — but it is a *feature boundary*, not a scaling failure.
2. **The genesis re-fold is the real cliff, and it is entirely a mobile problem.** 100M-op genesis fold = **2.5 s desktop / 25 s mobile**. Checkpoint bootstrap stays **~2–9 ms flat regardless of N** (O(open-period), not O(N)). The danger is *losing the checkpoint*, not the op count. Mobile must **never** fall back to genesis on a cold start.
3. **In-memory DB growth on mobile is the OOM you actually hit.** iOS Safari kills tabs at ~500 MB total; the live DB must stay <~200 MB (sharing the tab with the WASM runtime + viewer). At 437 B/op that is **~480k open-period ops** — reachable if a device stays offline across a long open period and compaction is tied only to period-close. Compaction must be **size-triggered**, independent of accounting periods.

## 2. Phase 1 — Calculated constraints

| # | Constraint | Calculation | Result |
|---|---|---|---|
| **1** | Max in-memory DB before OOM | Desktop sql.js practical ≈1 GB → 2× margin = **500 MB**; mobile under 500 MB tab-kill minus runtime ≈ **200 MB**. At 437 B/op: 200 MiB/437 = **480k ops**, 500 MiB/437 = **1.2M ops** | **200 MB mobile / 500 MB desktop**; warn 100 MB. (Design keeps the live DB ~13 MB ≈ 31k ops — 6× under the mobile ceiling.) |
| **2** | Genesis re-fold @ 20M ops | (a) 20M×437 B = **8.74 GB** (8.1 GiB) of log; (b) desktop 20M/40M ops/s = **0.50 s**; (c) mobile 20M/4M = **5.0 s**. General: desktop N/40M, mobile N/4M | 1M: 0.025 s / 0.25 s · 10M: 0.25 s / 2.5 s · **100M: 2.5 s / 25 s** |
| **3** | Checkpoint vs genesis bootstrap | Checkpoint = load 0.90 ms + fold open-period residual only (~31k ops at 13 MB). Desktop residual 31k/40M = 0.78 ms → **~1.7 ms**; mobile 31k/4M = 7.8 ms → **~8.7 ms**. **Flat in N.** | @100M: genesis 2,500 ms vs checkpoint 1.7 ms = **1,488× desktop**; 25,000 ms vs 8.7 ms = **2,874× mobile** |
| **4** | Concurrent-writer conflict | λ_shared = N × 1 op/s × 0.1% = 0.001N. P(≥2 in window) = 1 − e^−λ(1+λ). **50 dev, 1 s window:** λ=0.05 → **0.12%**. **18 ms CAS window:** λ=9×10⁻⁴ → **~4×10⁻⁷**. 5%/s threshold: λ≈0.36 → **N≈360 devices** | **0.12%/s @ 50 dev; <1 ppm per CAS window.** Not a near-term bottleneck |
| **5** | OPFS vs IndexedDB | Base 208 ms/1000 ops (sql.js+sha256) → IDB **4,808 ops/s**. OPFS ÷2.5 = 83 ms/1000 → **12,048 ops/s**. Measured batched `commitGroup` = 44 ms/1000 = **22,500 ops/s** (beats both: one signature per group, not per op) | IDB 208 ms · OPFS 83 ms · **batched 44 ms** per 1000-op commit |
| **6** | Mobile | **Battery:** normal mode is bootstrap-once + 0-RTT in-memory reads → negligible; only a pathological re-fold loop costs the stated 5–10%/hr at 50% CPU. **Memory:** 200 MB DB ceiling (per #1). **Offline queue:** typical POS 100 ops/day = **43.7 KB/day**; worst-case 1 op/s offline 24 h = 86,400 ops = **37.8 MB/day** | Battery ✅ negligible · Memory ⚠️ 200 MB · Queue ✅ typical / ⚠️ high-rate |

## 3. Phase 2 — Top 3 bottlenecks

| Rank | Bottleneck | Numerical threshold | User-visible symptom | Hard / Soft |
|---|---|---|---|---|
| **1** | **Mobile genesis fold** | 100M ops → **25 s** mobile bootstrap if checkpoint absent/corrupt | ~25 s white screen / spinner on cold start | **SOFT** — checkpoint bootstrap is ~9 ms flat; never genesis on mobile |
| **2** | **Mobile in-memory OOM** | DB > **200 MB** (~480k open-period ops) when compaction is tied only to period-close | iOS Safari kills the tab; unsynced ops lost if not relayed | **SOFT** — size-triggered compaction decouples from the accounting period |
| **3** | **Offline queue on high-rate device** | **37.8 MB/day** at 1 op/s; unbounded if relay never reconnects | Storage quota exhaustion; eventual write failure | **SOFT** — max-queue cap + checkpoint-and-purge |
| — | *(Single-writer per shard)* | N/A — architectural | Second writer on same shard rejected | **HARD** — by design; a boundary, not a scaling failure |

## 4. Phase 3 — Mitigation strategies

| Bottleneck | Mitigation | LOC estimate | Priority |
|---|---|---|---|
| Genesis fold (mobile) | **Mandatory checkpoint-first bootstrap:** load signed checkpoint, fold only post-checkpoint delta; genesis only as explicit recovery, run in a Web Worker with progress UI (never block the main thread) | ~120 LOC `kernel_ops.js` + ~40 worker shim | **P0** |
| In-memory OOM (mobile) | **Size-triggered signed compaction:** when `PRAGMA page_count` × page_size > 100 MB, fold open-period → new checkpoint, sign it (sha256 chain, prev-hash link), truncate folded ops. Independent of period-close | ~200 LOC `kernel_ops.js` | **P0** |
| OPFS / IDB | **VFS detection + auto-downgrade:** feature-detect `navigator.storage.getDirectory` + COOP/COEP; use OPFS VFS when present (2.5×), fall back to IndexedDB VFS, log `§VFS chosen=…`. (GH-Pages has no COOP/COEP → IDB-only there) | ~80 LOC bootstrap | **P0** |
| Offline queue | **Bounded queue + purge policy:** cap at **50 MB or 7 days**; on cap, force a local checkpoint, purge folded+relayed ops, keep unrelayed ops; exponential-backoff relay retry (cap 5 min) | ~140 LOC relay module | **P1** |
| Shared-resource conflict (the 0.1%) | **Keep the proven quorum-CAS** (`W-QUORUM-CAS`, 18 ms window, oversell=0) for stock/credit op-classes only; tag shared op-classes in the dictionary so the CAS path is taken *only* for them | ~60 LOC (mostly tagging) | **P1** |
| Mobile battery / persistence | **Battery-aware worker folding** (`navigator.getBattery()` → throttle non-urgent folds when <20% & discharging) + **service-worker cache** of WASM/checkpoint for instant resume + `navigator.storage.persist()` to resist eviction | ~90 LOC | **P2** |

## 5. Phase 4 — Production-readiness scorecard

| Constraint | Calculated limit | Current status | Mitigation | Residual risk |
|---|---|---|---|---|
| Max DB size | 200 MB mobile / 500 MB desktop | ✅ (13 MB live, 6× margin) | Size-triggered compaction | **Low** |
| Genesis fold | 25 s @100M mobile | ⚠️ approaching (no enforced checkpoint-first yet) | Checkpoint-first + worker | **Low** |
| Checkpoint bootstrap | ~9 ms flat | ✅ within limit | (already strong) | **Low** |
| Writer conflict | 0.12%/s @50 dev; 5% @~360 dev | ✅ within limit | Quorum-CAS on tagged classes | **Low** |
| OPFS vs IDB | 44 ms (batched) / 208 ms (IDB) per 1k | ⚠️ IDB-only on GH-Pages (no COOP/COEP) | VFS detect + downgrade | **Med** (hosting-bound) |
| Mobile memory | 200 MB tab ceiling | ⚠️ approaching if compaction lags | Size-trigger + persist() | **Med** |
| Offline queue | 37.8 MB/day worst case | ✅ typical / ⚠️ high-rate | Cap + purge + backoff | **Low** |
| Single-writer | 1 writer/shard | 🔴 hard (immutable) | None — architectural | **Hard-by-design** |

## 6. Phase 5 — Monitoring & alerting

| Metric | How to collect | Alert threshold | Action |
|---|---|---|---|
| `db_file_size_mb` | `PRAGMA page_count` × `PRAGMA page_size` | > 100 MB | `compact()` |
| `quota_used_pct` | `navigator.storage.estimate()` (usage/quota) | > 70% | warn user + force purge |
| `open_period_ops` | counter since last checkpoint | > 300k | trigger checkpoint |
| `bootstrap_path` | log `genesis` vs `checkpoint` on init | any `genesis` on mobile | alert: checkpoint missing/corrupt |
| `vfs_backend` | record OPFS vs IDB at init | `idb` on a COOP/COEP origin | log misconfig |
| `offline_queue_mb` | size of the unrelayed op buffer | > 40 MB **or** age > 6 days | force checkpoint + purge folded |
| `cas_retry_rate` | failed/total CAS on shared classes | > 2%/min | back-pressure shared writes |
| `fold_ms_p95` | `performance.now()` around fold | > 1000 ms (mobile) | move fold to worker |
| `battery_pct` | `navigator.getBattery()` | < 20% & discharging | throttle non-urgent folds |

## 7. Recommended next steps (ordered)

1. **P0 — `bootstrap_path` metric + checkpoint-first enforcement.** Cheapest, highest-leverage: guarantees mobile never eats the 25 s genesis.
2. **P0 — size-triggered signed compaction** (`db_file_size_mb` + `open_period_ops` triggers). Removes the only real mobile OOM path. Witness `W-COMPACT-SIGNED maxDiff=0c` (folded state == pre-compaction fold) + §FALSIFIER (skip the prev-hash link → chain breaks).
3. **P0 — VFS detect + IDB fallback**, with an explicit `§VFS` log; document the GH-Pages IDB-only reality so it is not mistaken for a regression.
4. **P1 — bounded offline queue + backoff relay**; witness queue cap + purge-keeps-unrelayed.
5. **P1 — dictionary-tag the 0.1% shared op-classes** so quorum-CAS fires only where needed (already proven, just needs scoping).
6. **P2 — battery-aware worker folding + service-worker WASM/checkpoint cache** for instant resume.

**Honest hard limit (un-mitigable, by design):** one writer per shard. Everything else is soft and within reach of the LOC above. The conflict math one might fear is the *least* of the worries — the genesis fold and the mobile memory ceiling bite first, and both are already ~90% handled by the existing checkpoint design; they just need *enforcement* and *size-triggering*.
