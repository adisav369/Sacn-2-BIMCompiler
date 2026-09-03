# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, headless §-witness BENCHMARK that puts a number on the one HolyGrail sentence still
#        ASSERTED rather than measured (docs/HolyGrail.md §3 OLTP coda): "What remains is mechanical, not
#        theoretical — maintain the read-projection incrementally at high append rates, BOUNDED BY THE PERIOD
#        CHECKPOINT so the working set stays small." It is `scripts/poc_volume.js` ONLY, Node, logging to
#        build/erp/poc_volume.log. It REUSES the showstopper/chain primitives; it invents no crypto.
# WHAT "HIGH USERS" MEANS HERE: there is NO central server to overload (physics partitions writers; each
#        terminal applies in-RAM). So the thresholds are PER-TERMINAL + MERGE costs, not concurrent connections:
#        append+chain throughput, fold/replay time vs op-count, chain-verify throughput, checkpoint sign/verify,
#        BOOTSTRAP from the last checkpoint vs PERIOD size, and the merge total-order sort for M device logs.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the threshold it finds); §-log first; Log Mandate
#        (save the run, READ the log before conclusions). Determinism applies to the DATA — ids/amounts are
#        index-derived INPUTS, NO Math.random in the fixture. TIMING is the measured OUTPUT — process.hrtime is
#        legitimate here (it is the observable, not invented data); only the durations vary run to run.
# HONEST FRAME: this is a FALSIFIER. Single-threaded Node is an UPPER BOUND on per-terminal cost (the
#        conservative direction). The load-bearing claim is §VOL-BOOTSTRAP: bootstrap-from-checkpoint must stay
#        ~FLAT as total history grows (cost ∝ PERIOD size, not total N). If it instead scales with total history,
#        the "bounded working set" claim does NOT deliver and HolyGrail §3's coda must be qualified. Log it honestly.
# HANDS-OFF the live build/erp CRUD/glassbowl files (read/require only). NO deploy. NO mkdocs.
# Read first: docs/HolyGrail.md §"3. OLTP" coda + §"1. Compacting…" · docs/ERP.md §18.9 (checkpointed
#        projections: aggregate = checkpoint_at_N + Σ(mutations since N)) · scripts/poc_showstopper.js (the
#        chain/fold/checkpoint primitives to reuse) · scripts/poc_distributed.js (totalOrder for the merge).

---

# Volume / Thresholds — Sandbox Benchmark (the "mechanical, bounded by checkpoint" claim, measured)

## The fixture (deterministic data, measured time)
Synthetic CRUD-shaped op-groups = balanced journal pairs (Dr Cash +amt / Cr Revenue −amt), amounts index-
derived (`((j%997)+1)*100` cents), so Σ-balances = 0 and the projection has a BOUNDED key-set (a chart of
accounts) no matter how long the log grows. That bounded projection over an unbounded log is the whole point:
the LOG is O(history); the WORKING SET is O(chart). The checkpoint is what stops replay being O(history).

## Witnesses (each NAMES the threshold it finds)
- `§VOL-APPEND n=<N> ms=<t> opsPerSec=<r> heapMB=<m>` — build+chain N ops (per-terminal write cost, sha256-bound).
- `§VOL-FOLD n=<N> ms=<t> opsPerSec=<r> accounts=<k>` — fold N ops from genesis (the cold bootstrap cost);
  `accounts=<k>` shows the working set stays small (bounded) as N grows.
- `§VOL-VERIFY n=<N> ms=<t> opsPerSec=<r>` — verifyChain over N ops (audit/replay-integrity cost).
- `§VOL-CKPT signMs=<t> verifyMs=<t>` — the period-close ECDSA sign + verify (must be ~constant, negligible vs N).
- `§VOL-BOOTSTRAP period=<P> totalHistory=<T> genesisFoldMs=<g> bootstrapFoldMs=<b> flat=<Y/N>` — **THE
  LOAD-BEARING TEST**: fold-from-genesis grows ∝ T; bootstrap (checkpoint opening + last period only) must stay
  ∝ P (flat across growing T). `flat=Y` ⇒ the checkpoint bounds the working set; `flat=N` ⇒ claim qualified.
- `§VOL-MERGE devices=<M> opsEach=<K> total=<MK> sortMs=<t>` — total-order merge cost for M device logs.
- `§VOL-THRESH liveTab16ms=<N16> bootstrap1s=<N1s> …` — the derived budgets: max live-tab size before a fold
  crosses one 60fps frame (16 ms) / before bootstrap crosses 1 s ⇒ the period-close cadence the doc implies.

## Acceptance
- A benchmark, not a binary gate — but it FAILS (exit 1) if `§VOL-BOOTSTRAP flat=N` (the bounded-working-set
  claim did not hold), or if the sanity Σ-balance ≠ 0 (the fold is wrong). Otherwise exit 0 with the threshold table.
- Read the log; cite the numbers; quote the doc sentence the bootstrap result confirms or qualifies.

## Run
    node --max-old-space-size=4096 scripts/poc_volume.js 2>&1 | tee build/erp/poc_volume.log
→ READ the log → write the `# DONE` ledger (the threshold table + the §VOL-BOOTSTRAP verdict).

---

# DONE — 2026-06-01 · `scripts/poc_volume.js` · `build/erp/poc_volume.log` · `§VOL PASS` exit 0

Numbers are single-threaded Node (an UPPER BOUND on per-terminal cost); fold/verify reported HOT (warmup +
min-of-4 — without this the first timed fold was ~7× cold and inconsistent between sections; that was a
measurement-hygiene bug, fixed). Op shape = CRUD_CREATE Fact_Acct journal pairs (CORE.buildOp-equivalent,
witnessed in poc_showstopper). Machine-specific; re-run for absolute figures — the SHAPES of the curves are
the result, not the constants.

**Per-terminal costs (the curves, not the connection count — there is no central server):**

| op-count | append+chain (write) | fold (hot) | verify chain (hot) |
|---:|---:|---:|---:|
| 1k   | ~128k ops/s | ~16M ops/s | ~683k ops/s |
| 10k  | ~201k ops/s | ~50M ops/s | ~704k ops/s |
| 100k | ~246k ops/s | ~46M ops/s | ~620k ops/s |
| 1M   | ~260k ops/s, heap ~450 MB | ~43M ops/s, 23 ms | ~615k ops/s, 1.6 s |

- **§VOL-FOLD** — fold scales linearly; `accounts=2` at every N → the **working set is bounded** (a chart of
  accounts) while the LOG is O(history). This is the structural reason compaction works.
- **§VOL-CKPT** — period-close ECDSA `sign=0.6 ms verify=0.2 ms`: constant, negligible vs any large fold.

**§VOL-BOOTSTRAP — the load-bearing test (PASSED):** `bootstrapMs=[0.2, 0.2, 0.2]` (flat=Y) as total history
grows `[10k, 100k, 1M]`, while `genesisMs=[0.2, 2.1, 24.0]` (grows=Y); speedup at 1M = **116×**. The checkpoint
holds bootstrap to the PERIOD size, not the total history — quantitatively confirming HolyGrail §3's coda
("maintain the read-projection … BOUNDED BY THE PERIOD CHECKPOINT so the working set stays small").

**§VOL-MERGE** — 8 device logs × 12.5k = 100k ops total-order in 6.6 ms, in memory, no coordinator.

**§VOL-THRESH — derived budgets (the period-close cadence the architecture implies):**
- Fold within one 60 fps frame (16 ms) up to **~690k ops** in the live tab.
- Cold fold under 1 s up to **~43M ops**.
- **HONEST CAVEAT — the real bottleneck is the chain, not the fold.** Fold alone is ~43M ops/s, but
  append+chain is ~260k ops/s and verify is ~615k ops/s (both SHA-256-bound). A bootstrap that *verifies*
  integrity (verify + fold), not just folds, is gated by verify → **~600k ops/s ≈ a 1 s integrity-checked
  bootstrap at ~0.6M ops, and ~16 ms at ~10k ops.** So the conservative period-close cadence is set by the
  hash rate (~0.6M ops/s), an order of magnitude tighter than the fold-only figure. The browser port
  (`crypto.subtle`, async) and any future Merkle-root membership change these constants; the bounded-working-
  set RESULT does not.

**Verdict:** the "mechanical, bounded by the checkpoint" claim holds with numbers; the binding constraint is
the per-op SHA-256 (append + verify), which sets the practical period-close cadence — a tuning knob, not a wall.
