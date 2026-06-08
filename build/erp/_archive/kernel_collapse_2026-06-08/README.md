# Archive — kernel_ops.js pre-collapse snapshots (2026-06-08)

Per the standing rule (archive whatever we overwrite + note it in the spec), these are the THREE
drifted `kernel_ops.js` copies captured **before** the §INTEG-COLLAPSE of
`prompts/ERP_SUBSTRATE_INTEGRATION.md` Phase 0 collapsed them to one canonical kernel.

| file | source | ver | lines | role before collapse |
|------|--------|-----|-------|----------------------|
| `kernel_ops.APP-erp-v6.js`        | `~/bim-ootb/erp/kernel_ops.js`    | v6 | 327 | what the LIVE ERP app loaded |
| `kernel_ops.VIEWER-v6.js`         | `~/bim-ootb/viewer/kernel_ops.js` | v6 | 323 | what the substrate node-witnesses loaded |
| `kernel_ops.CANONICAL-build-erp-v8.js` | `build/erp/kernel_ops.js`    | v8 | 535 | source-of-truth (PRE the ts-fold below) |

## Drift map (why three, what each uniquely had)
- **build/erp v8 (canonical) was AHEAD** on: `commitGroup`, `sealFrom`, `assertRateAsInput` (exports);
  `ensureTable` `gid` column + per-db `__kernelOpsTableCreated` flag; `verifyChain` group-torn logic.
- **app erp/ v6 was AHEAD** on ONE thing: `commitOp` had a deterministic `ts` 7th param (ERP.md §0.16) —
  canonical hardcoded `Date.now()`. No app caller passed it (dormant), but it is the byte-stable-replay
  capability the period-close substrate needs.
- **viewer v6** was strictly behind both (no `ts`, no v8 features) — oldest copy (2026-06-01).

## Reconciliation applied (lose nothing → forward-fold, never overwrite-blind)
The `ts` param was folded FORWARD into canonical `build/erp/kernel_ops.js` `commitOp` so the collapse
loses nothing. Canonical is now a true superset of all three. The collapse makes `~/bim-ootb/erp/` and
`~/bim-ootb/viewer/` copies of this reconciled canonical.

## GREEN gate evidence (§INTEG-KERNEL-GREEN, 2026-06-08) — see the spec DONE ledger
- 10/10 live-app erp witnesses 🟢 against the reconciled canonical (`poc_rule_edit`, `poc_rule_client_scope`,
  `poc_mobile_cards`, `poc_init_instant`, `poc_gated_complete`, `poc_i4_reconcile`, `poc_accts_posted`,
  `poc_kanban_write`, `poc_kanban_persist`, `test_idempiere_fold`), pageErrors=0.
- 5/5 substrate witnesses 🟢 (`test_kernel_{period_close,sync,rebase,relay,replica}`).

Restore = copy the relevant file back over its source path.
