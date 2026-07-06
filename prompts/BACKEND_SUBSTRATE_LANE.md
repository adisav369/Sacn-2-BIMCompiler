# ⚠ DO NOT REMOVE — Scope guard
# Lane: BACKEND / ERP substrate (event-sourced op-log, period-close, distributed durability). Distinct from the
#       UI+doc lane (prompts/UI_AND_DOC_LANE.md); shared files only erp/sw.js + erp/idempiere.html (see §SHARED).
#       Read the log after every run (exit code is NOT evidence). Honour until every item is ✅ DONE or ⛔ BLOCKED.
# NON-NEGOTIABLE: EXTRACT/COMPILE ONLY — never invent a number or an op. Spec-first, witness-led (each test NAMES
#       the issue it proves/disproves); whitebox §-log; deterministic on every fold/replay path (ids/ts/balances
#       are recorded INPUTS, no Date.now()/Math.random(); balances INTEGER CENTS). build/erp/ is the source of
#       truth ([[feedback_erp_source_of_truth]]); per-env copies sync FROM it.
# Read first: memory [[project_erp_sync_fsm]] (the WHOLE substrate arc + Phase 0/1/2A DONE lines) +
#       prompts/ERP_SUBSTRATE_INTEGRATION.md (the frozen contract + the phase ledger) + docs/ERP.md /
#       docs/HolyGrail.md §"hard parts" / docs/DistributedERP.md.

---

# Backend substrate lane — continue to zero

## State at handoff (2026-06-08) — Phase 0/1/2A all green
- **Phase 0 §INTEG-COLLAPSE** — ONE canonical `kernel_ops.js` (v8 + deterministic `ts` fold) is on bim-ootb
  `main` (#195, auto-merged). 10/10 app + 5/5 substrate witnesses green. Pre-collapse copies archived
  `build/erp/_archive/kernel_collapse_2026-06-08/`.
- **Phase 1 §INTEG-POSTINGS-RECONCILE + §INTEG-SIGNER-REUSE** — `erp_period_close.foldBalances` extended
  (additively) to fold the app's REAL double-entry `POST {lines:[{account_id,amtacctdr,amtacctcr}]}` ops; tip
  signed via the app `erp_signer.js`. Witness `scripts/test_integ_postings_reconcile.js` (W-POSTINGS-RECONCILE)
  🟢 maxDiff=0c; frozen synthetic `scripts/test_kernel_period_close.js` still 🟢.
- **Phase 2 slice A §INTEG-WIRE** — period-close IN-APP on glassbowl.html's live sidecar op-log
  (`period_close_ui.js` window.PeriodClose). Witness `bim-ootb/erp/tests/poc_period_close_wire.js` 🟢 6/6.
  Landing via **PR #197 → main** (verify it merged; sw v600 is the deploy switch).

## ⚠ UNCOMMITTED in this repo (bim-compiler) — COMMIT FIRST (source of truth)
These engine edits are on the `feat/revit-plus-lens` working tree, NOT yet committed:
- `build/erp/kernel_ops.js` — the deterministic `ts` 7th param folded into commitOp (Phase 0 reconcile-forward).
- `build/erp/erp_period_close.js` — POST-shape fold (Phase 1).
- `build/erp/period_close_ui.js` — synced copy of the app module.
- `scripts/test_integ_postings_reconcile.js` (+ `build/erp/test_integ_postings_reconcile.log`) — the W-POSTINGS witness.
- `build/erp/_archive/kernel_collapse_2026-06-08/` — the archived pre-collapse kernels + README.
Commit these to a backend branch (NOT mixed with UI/doc). `git add` specific files (never -A). Re-run
`node scripts/test_kernel_period_close.js` + `node scripts/test_integ_postings_reconcile.js` → both 🟢 before commit.

## OUTSTANDING (work top-to-bottom to zero)

### §B-1 — commit the uncommitted engine source-of-truth (above) + verify witnesses green.
✅ DONE (2026-06-08, commit `3d9e07d1` on branch `feat/erp-substrate-phase012`). Staged only the specific
files (never -A): kernel_ops.js, erp_period_close.js, period_close_ui.js, scripts/test_integ_postings_reconcile.js,
_archive/kernel_collapse_2026-06-08/. The witness `.log` is .gitignored (regenerable artifact) — left untracked.
Pre-commit compile gate passed. Witnesses re-run green BEFORE commit:
`node scripts/test_kernel_period_close.js` → 🟢 ALL PASS (period-close fold = signed checkpoint = balance b/f);
`node scripts/test_integ_postings_reconcile.js` → 🟢 ALL PASS (A1-A4 + B1-B3 falsifier, maxDiff=0c, balSum=0,
app-signed+verified). NOT pushed (backend branch local; user merges/deploys).

### §B-2 — Phase 2 candidate B: disposable-host persistence (the W-PERSIST / 3-host replica story) — IF user wants
The user picked slice A first; B was deferred. Wire the live op-log to spill to a SIGNED replica the user owns,
recoverable on a fresh device. Substrate already exists (`erp_replica_client.js` / `erp_snapshot_sign.js` /
`gen_replica_snapshot.js`, witnessed W-REPLICA/W-SIGN). Slice: from the glassbowl/erp surface, export+sign the
op-log snapshot to a user channel; on a fresh load, fetch+replay+verify (recompute tip == signed tip). Witness it
whitebox before any UI. ⛔ Confirm with the user this is the next slice (vs stopping at A).
✅ DONE (2026-06-08, user said "proceed B2"; commit `90d12e41` on `feat/erp-substrate-phase012`). Witnessed
WHITEBOX before any UI: `scripts/test_persist_slice.js` (W-PERSIST-SLICE) → 🟢 ALL PASS (8/8). Composes the
FROZEN substrate (erp_replica_client replay+failover + erp_period_close.foldBalances + canonical kernel) with the
REAL app POST ops (§INTEG-POSTINGS shape) and the REAL app signer (`~/bim-ootb/erp/erp_signer.js`,
§INTEG-SIGNER-REUSE) — the key the USER owns, NOT erp_snapshot_sign's demo pinned key. No engine re-open; export
is an inline helper (UI lane picks the export surface later). Proves: S1 export signed by user's own key · S6
disposable-host failover · **S2 zero-state fresh device recomputes tip === signed tip** · S3/S3b books recovered
to the cent (recovered==live==hand-computed, maxDiff=0c) · S4 ownership verifies under the user's PUBLIC key alone
(no private key) · **S5 FALSIFIER: forger self-consistifies the tip but the user-key signature REJECTS it**. Log
`build/erp/test_persist_slice.log` (.gitignored, regenerable). NEXT (UI lane, when user says "deploy"): wire the
export+recover gesture onto the glassbowl/erp surface + §INTEG-FRESHNESS — NOT this backend lane.

### §B-3 — deferred substrate infra (OUT OF SCOPE until a real multi-writer need): relay on real compute
(Functions/CF Worker) + sig-verify at `/push` + https; incremental rebase (`?after=N`). No UI depends on these.
Do NOT build speculatively — note only.

### §B-4 — doc gate: HolyGrail.md §"hard parts" still describes compaction/period-close as prose; update to point
at the now-EXECUTABLE witnesses ONLY when the user says "deploy doc" (held).

## §SHARED — coordination with the UI lane
Only `erp/sw.js` (CACHE_VERSION magnet — take HIGHER, keep BOTH precache lists) + `erp/idempiere.html` are shared.
Backend slices touch period_close_ui/kernel/erp_period_close (separate). Work bim-ootb changes in a `/tmp/wt-*`
worktree off FRESH `origin/main` (never the shared tree — hook-blocked); let auto-merge land PRs but VERIFY (the
github-actions bot squash-merges in seconds — a late push orphans commits; start follow-ups off fresh origin/main).

## Done = §B-1 committed+green; §B-2 decided (and witnessed if taken); §B-3/§B-4 noted/held. # DONE ledger per item.
