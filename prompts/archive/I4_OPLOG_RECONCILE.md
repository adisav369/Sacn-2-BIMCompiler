# ✅ DONE + LIVE (2026-06-05) — I-4 ONE signed op-log: bim-ootb PR #144 (sw v582), §I4-RECONCILE PASS.
#   Decisions taken: D1=id · D2=per-write · D3=both-verifies. erp_kernel kernel_ops DDL = the signed
#   superset (kernel_ops.js owns it; A routed through B). The live SET_STATUS is now ECDSA-signed.
# ✅ PAYOFF DONE + LIVE — GATED SIGNED COMPLETE: PR #147 (sw v583), §GATED-COMPLETE-POC PASS. The editable
#   signed rule gates the real CO transition in erp_seam.dispatch (admission guard, BigDecimal); edit the
#   rule → a blocked order completes, signed, UI-unbypassable. Files: erp_seam.js (guard+currentRuleT),
#   kanban_host.js (admissionRules C_Order:CO→maycomplete), rule_fold.js (RuleFold.setT).
# ── original spec below ──
# ⚠ DO NOT REMOVE — I-4: reconcile the TWO op-logs to ONE signed chain (DECISION + spec, 2026-06-05)
# SCOPE: the seam's write path (window.ERP / erp_kernel) is NOT cryptographically signed; the genuinely
#   ECDSA-signed chain lives in a SEPARATE module (kernel_ops.js). Pick ONE op-log + route the seam through
#   it, so a real doc transition (Complete an order) is a SIGNED op — THEN gate it with the editable rule.
#   "First decision, not cleanup; signed-over-the-wrong-table is worse than unsigned." (engine lane)
#   Spec-first · §-log first · NON-INVENT · consume the seam, never fork a verb. Honour this block until DONE.
# SPIRIT: docs/ERP.md §0.6 (rich op-log keystone) · §0.20/§9-B (W-SIGN) · §0.16/§0.21 (determinism/identity)
#   · prompts/FRONTEND_LANE_MASTER.md §3.1 (the I-4 note this resolves) · §6 I-1..I-5 (measured perf).

## ▶ THE PROBLEM — two kernel_ops, only ONE is signed
| | **A · erp_kernel.js** (`window.ERPKernel`) | **B · kernel_ops.js** (`window.KernelOps`) |
|---|---|---|
| PK | `op_uuid TEXT` (edge-minted, cross-device) | `id INTEGER` (local total-order) + `op_uuid` secondary |
| Chain/sig cols | **none** | `prev_hash` · `op_hash` · `sig` (W-CHAIN + W-SIGN ECDSA P-256) |
| Writer | `apply()` → bare 7-col INSERT | `commitOp()` (+ optional deterministic `ts`) |
| Verify | `replay()` ×2 → projection-hash equality (**effects** determinism) | `verifyChain()` → recompute every `op_hash` + prev-link + **signature** (**log** integrity+authenticity) |
| Proves | "the projection rebuilds deterministically from the ops" | "the log is tamper-evident AND signed" |
| Used by | ERP seam ONLY (erp_seam · kanban_host · erp_picker · odoo_agent) | **SHARED infra**: the whole BIM viewer (grid/scene/picking/panels…) + ERP (rule_fold · crud_overlay · ad_data · erp_replay) |
| Signed? | **NO** (the Kanban `SET_STATUS` "chainOk" = replay-equal, not ECDSA) | **YES** (the rule-edit gesture rides this) |

The two are COMPLEMENTARY, not contradictory: A proves *effects*, B proves *the log*. The gap is that the
**live write path (A) never gets B's signature** — so "signed write path" is today only true for the rule edit.

## ▶ THE KEY REALISATION (this shrinks the decision)
**B's schema is already the superset** — it carries `op_uuid` AND the chain/sig columns AND the same
`op_type/parameters/input_guids/output_guid` A writes. And **B is the shared module; A is ERP-only.**
→ Do NOT fork B's schema (it would ripple through the entire viewer). Instead **route A through B**: the
seam's `apply()` writes its rich op via `KernelOps.commitOp(...)` and seals via `KernelOps.sealChain`, so the
seam's op-log *is* the signed chain. A's `replay()` still reads `op_type/parameters` from that same table
(unchanged), so effects-determinism is preserved. ONE table, ONE writer, BOTH proofs.

## ▶ THE MERGE DESIGN (once the decisions below are made)
1. **One table** = B's `kernel_ops` (id PK + op_uuid + chain/sig). A's `initProjection` drops its own
   kernel_ops DDL and calls `KernelOps.ensureTable(db)` for the log (projection tables stay A's).
2. **One writer** = `erp_kernel.apply()` builds the rich op (payload+actor+before/after+lineage) and appends it
   through `KernelOps.commitOp(db, op_type, richParams, input_guids, output_guid, op_uuid, ts)` (deterministic
   `ts` — already supported), then `KernelOps.sealChain(db)` (signs). The handler/violation-guard ladder in
   `dispatch()` is untouched — only the INSERT seam changes.
3. **One verify** = `seam.verify(ctx)` returns BOTH: `effects` (A's `replay`×2 projection-hash equality) AND
   `chainOk`+`tip` (B's `verifyChain`, incl. signature). Trust = both green.
4. **Replay path** (A) reads the unified table verbatim — `parameters.payload` is A's rich JSON, untouched.

## ▶ DECISIONS YOU OWN (make BEFORE the build — don't guess)
- **D1 · chain-order basis (the real one).** B's `_canonical` hashes over the local `id`. Options:
  - **(i) keep `id`** — simplest; chains are per-device; a 2-device union must re-seal. ✅ *Recommend now*
    (the edge is single-device; KISS; matches the "per-write now, defer multi-device" lean).
  - **(ii) hash over `op_uuid`/a deterministic seq** — device-independent chains (clean union), more work.
    Defer until a real multi-device union exists. ⚠ If you ever want offline 2-device merge, this is the hook.
- **D2 · seal cadence.** **(i) per-write** (`sealChain` after each op — simple; O(n²) re-seal, measured fine at
  hundreds, I-2) ✅ *Recommend now*; **(ii) rolling/append-only seal** (incremental — needed at thousands,
  the I-2 fix). Lean per-write now; rolling deferred to the perf backlog.
- **D3 · keep A's effects-verify alongside B's chain-verify?** ✅ *Recommend YES* — they prove different
  things; `verify()` returns both. (Cheap; it's the strongest honesty story.)

Default if you say nothing: **D1(i) · D2(i) · D3 yes** — the KISS path, reversible into (ii) later.

## ▶ WITNESS CONTRACT (build until green — §-log first, real-browser via tests/poc_*.js)
```
§I4-ONE-LOG       seam writes land on B's table  cols=[prev_hash,op_hash,sig] present=Y
§I4-SET-STATUS    Kanban Complete (DR→CO) is now a SIGNED op  chainOk=Y sig=Y   (was unsigned)
§I4-EFFECTS       replay×2 projection-hash equal  (A determinism intact)
§I4-UNIFIED       rule-edit op + SET_STATUS op share ONE chain; verifyChain end-to-end OK len=N
§I4-TAMPER        flip one op's parameter byte → verifyChain FAILS at exactly that op (brokeAt=id)
§I4-DETERM        no Date.now/Math.random in the op path (deterministic ts; replay byte-stable)
§I4-NO-REGRESS    poc_seam · poc_postings · poc_rule_edit · kanban write all still PASS
§I4-RECONCILE PASS
```

## ▶ BACK-COMPAT CONSTRAINTS (do not break)
- **B is shared with the viewer** — change NOTHING in B's schema or existing behaviour. The `ts` arg is already
  additive; the seam merely becomes a NEW caller of B. Viewer GRID_MOVE/etc. paths must stay byte-identical.
- Existing ERP pocs (`poc_seam`, `poc_postings`, the Kanban `§KANBAN-WRITE`, `poc_rule_edit`) must stay green —
  run them as the no-regress gate.
- `erp_kernel.replay` must still rebuild the projection from the unified rows (it already SELECTs only
  `op_type,parameters` — unaffected by the extra chain columns).

## ▶ THE PAYOFF — gated signed Complete (the NEXT task, unblocked by this)
Once the seam writes on the signed chain, wire the **L1 rule as a guard** consulted in
`erp_kernel.dispatch` step-2 (guards): a `CO` transition is ADMITTED iff the editable signed rule holds
(`GrandTotal ≤ T`), else REJECTED. Then the demo closes the loop:
*edit the rule (signed) → drag a blocked order to Complete → refused → raise T → it completes (signed).*
Witness `§GATED-COMPLETE order=… T=… admit=<Y|N> signedOp=… chainOk=Y` + the rejected case `why=rule-guard`.

## ▶ GUARDRAILS
- **NON-INVENT** · **§-log first** (read the log after every run) · **consume the seam, never fork a verb**.
- **Determinism:** no `Date.now`/`Math.random` in op paths (pass ts/ids in). Money/qty via `bigdecimal.js`.
- **Honesty boundary:** this signs the **log** + makes Complete a signed, rule-gated transition — it is still
  **NOT a GL posting** (Completed ≠ posted, §I-K/§13.6). State exactly what is signed (the op-chain) vs posted.
- **Deploy:** erp/ only · worktree off `origin/main` (shared dirty tree) · SW bump · fetch-back-verify.
```
I-4 is the rung that makes "the live write path is signed" TRUE — then the editable rule governs a real
signed Complete. Ship the unified chain; the gated cycle is one step behind it.
```
