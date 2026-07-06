# ⚠ DO NOT REMOVE — Kernel commitGroup(ops[]) + financial-correctness roadmap (WORK-ORDER, spec-first)
# SCOPE: implement op-group atomicity in the kernel = the OLTP / double-entry rollback the local ERP lacks
#   today. A document event is MANY ops (Complete = SET_STATUS + SHIP + INVOICE + Dr-AR + Cr-Rev), all-or-none.
#   VERIFIED STATE (ENGINE_FULL_ERP_ISSUES.md §I-K, code-checked 2026-06-03): the kernel has NO group primitive
#   (commitOp commits ONE op and auto-seals per op), and crud_overlay.commitProcess commits ONE SET_STATUS
#   (a status flip, NO postings) — so "Completed" in the UI does NOT move the books. This work-order makes the
#   multi-op write BOTH real and atomic, and as a side-effect kills the I-D per-op O(n²) reseal.
# NON-NEGOTIABLE: non-invent; SPEC before code; every claim has a § log line read FROM THE LOG (exit code is not
#   evidence); books NEVER left unbalanced; ALL money/qty via build/erp/bigdecimal.js — NEVER raw JS Number
#   (proven == java.math.BigDecimal, 446/446; scripts/test_bigdecimal_conformance.js). See [[feedback_numbers_via_bigdecimal]].
# READ FIRST: docs/DepreciationPerf.md (where ERP time really goes + the no-decimal risk) ·
#   prompts/ENGINE_FULL_ERP_ISSUES.md §I-K (atomicity) + §I-D (seal O(n²)) + §I-J (rate-as-op-input) ·
#   docs/DistributedERP.md §0 (op-log → fold) + §8 (accounting = reconciliation) ·
#   build/erp/kernel_ops.js (commitOp:93 / sealChain:137 — the per-op reseal) ·
#   build/erp/crud_overlay.js (commitProcess — the single SET_STATUS) ·
#   scripts/poc_showstopper.js §SHOW-ATOMIC (group atomicity ALREADY proven in the POC) ·
#   scripts/spike_writepath.js (the §METER-SEAL regression gate for I-D) · build/erp/bigdecimal.js.

---

## §1 THE TASK — kernel `commitGroup(ops[], meta)`  (the atomic unit becomes the GROUP, not the op)
Spec (write this section into the kernel header BEFORE coding):
1. **Pre-flight validate the WHOLE group first.** Every op valid; for a posting group, enforce the balance
   invariant **Σ Dr − Σ Cr == 0** using BigDecimal (NOT Number). If any check fails → reject the whole group,
   write nothing. (Books can never go half-posted.)
2. **Apply inside ONE SQLite transaction.** `BEGIN` → insert all ops (sharing a `group_id` + ordered
   `group_seq`) → fold the projection → `COMMIT`. On ANY error → `ROLLBACK` (log + projection untouched). This
   is the local equivalent of the server RDBMS transaction — SQLite hands the ACID rollback back to us.
3. **One `group_hash` over the ordered canonical ops** (extend the existing per-op chain; the group is the
   atomic unit, the §SHOW-ATOMIC mechanism). On replay: recompute `group_hash`; a torn group (missing/extra/
   reordered op) → mismatch → reject the WHOLE group (none of its ops apply). Σ stays balanced.
4. **Seal ONCE per group**, seal-from-tip over the new group only — NOT per op. This is also the I-D fix:
   per-write cost O(n)→O(1) amortised. (Land the broader seal-from-tip in §4-C.)
5. **Back-compat:** `commitOp(op)` becomes `commitGroup([op], …)`. Single edits are a group of one.
6. **Schema:** add `group_id`, `group_seq`, `group_hash` to `kernel_ops` via an APPEND-ONLY migration
   (migrations are Sacred — never modify existing ones). Mirror site/ ↔ build/erp/.

## §2 WITNESS — write the claim FIRST, prove from the log
- `§GROUP-ATOMIC` — commit a 5-op posting group; drop one op on replay → whole group rejected, projection
  unchanged, Σ Dr−Cr == 0. (Mirror poc_showstopper §SHOW-ATOMIC onto the LIVE kernel path, not just the POC.)
- `§GROUP-BALANCE` — a posting group with Σ Dr ≠ Σ Cr (one cent off, via BigDecimal) → rejected pre-commit;
  nothing written. Proves books cannot go unbalanced.
- `§GROUP-SEAL` — seal count == number of GROUPS (not ops); re-run `scripts/spike_writepath.js` → `§METER-SEAL`
  ms must FLATTEN over N (the I-D regression gate). One change, both wins (atomicity + flat seal).

## §3 FILES + ORDER
1. `build/erp/kernel_ops.js` — add `commitGroup`, group columns, group_hash, seal-once-per-group; keep
   `commitOp` as `commitGroup([op])`. 2. migration (append-only) for the group columns. 3.
   `build/erp/crud_overlay.js` — `commitProcess` builds the FULL op-group from the doc descriptor (Dr/Cr/ship/
   invoice), not one SET_STATUS. 4. mirror to site/. 5. witnesses §2. Sequence kernel before crud_overlay
   (same write path — file contention, [[ENGINE_FULL_ERP_ISSUES §2.1 caveat]]).

---

## §4 ROADMAP — everything still to implement for financial correctness (in order)
Status as of 2026-06-03. Each line names its issue + witness.

- **A · Wire BigDecimal in (IMMEDIATE).** The class is proven but **nothing uses it yet** — kernel_ops,
  crud_overlay, erp_postings, spike_depreciation still do raw `Number` money math. Replace all money/qty
  arithmetic with `BigDecimal`. Witness: re-run spike_depreciation with BigDecimal → balances exact; add a
  `§DEP-DECIMAL` case where float drifts and BigDecimal holds. (Prereq for §1's balance check.)
- **B · `commitGroup(ops[])` — THIS work-order (§1–§3).** OLTP/double-entry rollback. I-K. §GROUP-*.
- **C · seal-from-tip + period-close checkpoint.** The full I-D fix beyond per-group seal: seal only from the
  last sealed tip; day-close = the natural checkpoint that bounds the projection fold. Witness: §SEAL ms-flat-over-N.
- **D · Double-entry posting on the write path.** commitProcess emits the real Dr/Cr op-group so "Completed"
  moves the books (today it doesn't). I-G + I-K. Scope to one AcctSchema first; name the multi-schema gap.
- **E · Currency: ISO 4217 minor-unit scale + rate-as-op-input.** Per-currency rounding scale (USD 2/JPY 0)
  feeds BigDecimal.setScale. Multi-currency conversion freezes the FX rate as an op INPUT (never looked up at
  replay) — the I-J determinism trap. Multi-currency stays DISABLED until wired.
- **F · Replay-determinism harness.** Prove replay-hash identical across two device-logs AFTER decimal + group
  changes. Guards everything above. Extend poc_distributed / spike_writepath.
- **G · Owner-gate (G-SINGLE-WRITER) + CAS — LATER/product.** For NON-self-balancing cross-entity invariants
  (payment ≤ invoice total; stock ≥ 0) that group-atomicity does NOT cover. I-E (in-browser merge). Deferred.

## §5 OUTCOME
Start at §4-A (wire BigDecimal — fast, unblocks §1's balance check), then §1 commitGroup, then C/D. Each is a
citation for its change (`// Implementing KERNEL_COMMITGROUP.md §X — Witness: §GROUP-…`). Until a row's witness
prints from the log, its write stays honest-disabled.
