# ⚠ DO NOT REMOVE — Finish the local write-path engine (post-commitGroup) — WORK-ORDER, spec-first
# CONTEXT (Watchdog-verified 2026-06-03, logs/ on branch feat/erp-write-path-ik-ij commit 630548c3):
#   ALREADY landed & WITNESSED — do NOT re-implement:
#     ✅ I-K op-group atomicity — commitGroup(): torn group→0 rows Σ=0; clean→whole Σ=0  (scripts/poc_opgroup.js)
#     ✅ I-D seal-from-tip      — commitGroup_ms FLAT 0.31→0.41ms over 4× ops  (spike_writepath §METER-SEAL-FROM)
#     ✅ I-J rate-as-op-input   — deterministic w/ rate frozen; guard rejects missing→multi-ccy disabled (poc_rate_input.js)
#     🟡 UI path commits viaGroup, but the Dr/Cr consequence ops are DELEGATED (not fabricated) — honest, by design
#   IN PROGRESS — MAIN SESSION owns this, do NOT duplicate:
#     gap A — wire BigDecimal INTO the kernel fold. Today kernel_ops.js / crud_overlay.js do NOT require
#     bigdecimal.js (grep=∅); the ledger Σ is raw JS Number. "BigDecimal enforced" is NOT true until the kernel
#     fold uses build/erp/bigdecimal.js. ALSO fix poc_rate_input.js require → build/erp/bigdecimal.js (site/ is gitignored).
# SCOPE (THIS prompt = what's LEFT after commitGroup + BD-in-kernel): the remaining write-path correctness items.
# NON-NEGOTIABLE: non-invent; SPEC before code; every claim a § log line read FROM the log (exit code ≠ evidence);
#   ALL money/qty via build/erp/bigdecimal.js (proven == java.math.BigDecimal, 446/446) — never raw Number.
#   See [[feedback_numbers_via_bigdecimal]].
# READ FIRST: docs/DepreciationPerf.md · prompts/ENGINE_FULL_ERP_ISSUES.md §I-D/§I-G/§I-J ·
#   build/erp/kernel_ops.js (commitGroup/sealFrom) · scripts/poc_opgroup.js · scripts/poc_rate_input.js ·
#   scripts/spike_writepath.js · docs/DistributedERP.md §6 (facilitator) §8 (accounting = reconciliation).

## §1 VERIFY-GATE — do FIRST: confirm MAIN session closed gap A (Watchdog)
- After main lands BD-in-kernel: `§BD-KERNEL` — kernel_ops.js ledger fold uses BigDecimal; a fold over values
  that drift in float stays EXACT; balSum == 0 EXACT (not just formatted). poc_rate_input require == build/erp/.
  No § proof from the log = not done. Do not build on top until this prints.

## §2 PERIOD-CLOSE CHECKPOINT — I-D second half (bounds the PROJECTION fold, not the seal)
- seal-from-tip flattened the SEAL; the projection refold is still Σ-since-genesis. Add the day-close checkpoint:
  `state = checkpoint_at_N + Σ since N` (balance-brought-forward; the 9-to-5/day-close boundary measured in
  DepreciationPerf). Witness `§CHECKPOINT` — refold flat across 100× ops when checkpointed; replay-from-checkpoint
  projection hash == full-replay hash.

## §3 CROSS-DEVICE REPLAY-DETERMINISM HARNESS — F
- Extend poc_distributed / poc_rate_input: two device logs → union → total-order → replay → IDENTICAL projection
  hash on both devices. Witness `§REPLAY-MERGE` replayHashA == replayHashB after merge (the signed-log guarantee;
  this is also what makes BigDecimal load-bearing — a float half-cent would break it).

## §4 DELEGATED POSTING CONSEQUENCES — I-G (scoped, per delegate-to-install)
- Today commitProcess commits SET_STATUS only (consequences delegated — correct, non-invent). Decide which doc
  types get a REAL local Dr/Cr op-group (ONE AcctSchema first) vs pushed to the install. Witness `§POST-GROUP` —
  a Complete emits a BALANCED Dr/Cr op-group (Σ=0) for one doc type; name the multi-AcctSchema gap.

## §5 OWNER-GATE + CAS — G (LATER / product)
- Non-self-balancing cross-entity invariants (payment ≤ invoice total; stock ≥ 0) — NOT covered by group
  atomicity. G-SINGLE-WRITER + the one CAS op-class (DistributedERP §4/§5), in-browser merge (I-E). Deferred;
  respect forward-only / frozen-effects.

## §6 OUTCOME
Order: §1 verify-gate → §2 checkpoint → §3 replay harness → §4 posting → §5 later. Each resolution is a citation
(`// Implementing ENGINE_WRITEPATH_FINISH.md §X — Witness: §…`). Until a section's § prints from the log, its
write stays honest-disabled. Prior commitGroup work-order (KERNEL_COMMITGROUP.md) is SUPERSEDED — commitGroup is done.
