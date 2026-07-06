# ⚠ DO NOT REMOVE — Scope guard
# Scope: wire the now-FROZEN §0.20 op-log substrate (sync / relay / replica / sign / period-close) into the
#        LIVE ERP app (~/bim-ootb/erp/idempiere.html). This is the FIRST prompt in this arc that CROSSES OUT
#        of engine-only/dev-ceiling INTO the live ERP lane — it ships to GH Pages via the FRONTEND_LANE_MASTER
#        flow, gated on the user saying "deploy". Treat it as a careful migration, not a feature sprint.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the issue it proves/disproves); whitebox §-log FIRST
#        (NOT forced-viewport Playwright — [[feedback_whitebox_not_playwright]]); the Log Mandate (save every run,
#        READ the log before any conclusion — exit code is NOT evidence); deterministic on every fold/replay path
#        (ids/ts/balances/keys are recorded INPUTS — NO Date.now()/Math.random(); balances INTEGER CENTS).
# LANE DISCIPLINE (this is the live erp lane now): worktree off FRESH origin/main → erp-only diff → §-witness →
#        PR (HELD until user "deploy") → on deploy: bump erp/sw.js CACHE_VERSION + touched ?v= → verify live on
#        Pages. sw.js is the conflict magnet (concurrent viewer/erp terminals) → on conflict take the HIGHER
#        version + keep ALL changelogs. Symlink ~/bim-ootb/tests/node_modules into the worktree tests/. Leave no
#        stale worktrees/branches; do NOT touch the viewer lane's /tmp/wt-* or the shared ~/bim-ootb tree.
# Read first (the handoff):
#   - memory/project_erp_sync_fsm.md — the WHOLE substrate session (every file/witness/URL) + "Deferred to
#     INTEGRATION" + the contract-freeze (W-PCLOSE) DONE line. This prompt operationalises that deferred section.
#   - prompts/PERIOD_CLOSE_FOLD_POC.md §DONE — the frozen contract: erp_period_close.js (foldBalances/closePeriod/
#     bootstrapFromCheckpoint/reopenPeriod), proven on the POC's SYNTHETIC posting shape ({account,cents}).
#   - The substrate (build/erp/, FROZEN — compose ON it, don't re-open): erp_sync_fsm.js · erp_sequencer.js ·
#     erp_relay_{server,client}.js · erp_replica_client.js · erp_snapshot_sign.js · erp_period_close.js ·
#     kernel_ops.js (the SOURCE-OF-TRUTH kernel: commitOp/commitGroup/sealChain/sealFrom/verifyChain/replayOps).
#   - The LIVE app: ~/bim-ootb/erp/idempiere.html + erp/*.js — esp. erp_kernel.js, erp_postings.js (the REAL
#     journal/Fact_Acct ops), erp_signer.js (the app's W-SIGN key custody, loadOrMint), kernel_ops.js (app copy),
#     crud_overlay.js (CORE.buildOp op shapes), rule_fold.js. AND ~/bim-ootb/viewer/kernel_ops.js (3rd copy).
#   - prompts/FRONTEND_LANE_MASTER.md (the lane + its OPERATING NOTES) · [[feedback_erp_source_of_truth]] ·
#     [[feedback_sw_version]] · [[feedback_whitebox_not_playwright]].

---

# Substrate → UI Integration — onto the frozen op-log contract

## Why now
The §0.20 substrate is frozen and witnessed (sync/rebase/relay/replica/sign + period-close, all 🟢, dev-only).
Its value is unrealised until it lives in the product. Per the agreed plan (substrate-first → freeze → UI on a
stable contract), this is the integration arc. **The contract is frozen as an ENGINE API; what is NOT yet proven
is that the LIVE app's real ops behave under it.** Two things therefore gate everything and must come BEFORE any
UI wiring: (1) ONE canonical kernel, proven not to break the live app; (2) the live app's REAL journal ops
reconcile under period-close. Lead with the falsifiers, not the features.

## The two load-bearing gates (do these FIRST, in order)

### Phase 0 — kernel reconcile, GATED (do NOT collapse blind)
There are THREE drifted `kernel_ops.js`: `build/erp/` (source-of-truth, richer — commitGroup/sealFrom/
assertRateAsInput), `~/bim-ootb/erp/` (what the live app loads), `~/bim-ootb/viewer/` (what the substrate
witnesses load). Collapsing to one is the precondition for integration AND the biggest latent risk in the stack.
- **§INTEG-KERNEL-DIFF** — diff all three; enumerate the drift in both directions (what build/erp adds; what the
  app's erp/ copy has that build/erp lacks). Output the list before changing anything.
- **§INTEG-KERNEL-GREEN (THE GATE)** — run the live app's EXISTING erp witnesses (poc_rule_edit,
  poc_rule_client_scope, the §A–§D chrome witnesses, poc_mobile_cards, poc_init_instant) against the
  **build/erp canonical kernel_ops** — BEFORE collapsing. If any go red, the drift carries app-relied behaviour:
  **reconcile FORWARD into the canonical source (and witness), never overwrite-and-hope.** No collapse until green.
- **§INTEG-COLLAPSE** — only once green: make `erp/` + `viewer/` kernel_ops *copies of* the canonical build/erp
  source ([[feedback_erp_source_of_truth]]). One kernel, witnessed identical behaviour.

### Phase 1 — real-ops period-close reconciliation (THE FIRST REAL TEST, a falsifier)
The period-close falsifier passed on the POC's SYNTHETIC `{account,cents}` ops, not the app's real postings.
- **§INTEG-POSTINGS-RECONCILE** — drive `erp_period_close.closePeriod` with the LIVE app's REAL journal ops
  (what `erp_postings.js`/`erp_kernel.js`/crud_overlay emit into the log → Fact_Acct). Fold `[checkpoint +
  post-ckpt]` vs full genesis → **maxDiffCents === 0**. If not zero, the contract does NOT yet fit the app's ops
  → STOP, map the posting shape into `foldBalances` (account/cents extraction) and re-witness. This is the seam
  the POC could not cover; it is cheap to be wrong here and expensive later.
- **§INTEG-SIGNER-REUSE** — route checkpoint/snapshot signing through the app's `erp/erp_signer.js` loadOrMint
  custody, NOT `erp_snapshot_sign.js`'s pinned DEMO key. The demo key was for the POC; the app has the real edge
  signer. Witness: a checkpoint signed by the app signer verifies under its public key; the demo key is gone.

## Phase 2 — wire the smallest valuable slice (only after Phase 0+1 green)
Do NOT boil the ocean. Pick ONE capability to land first and witness it end-to-end in the app before the next:
- candidate A: **period-close in the app** — an accountant closes a period → signed checkpoint → balance-b/f
  visible; bootstrap-from-checkpoint on next load (the measured ~52–59× win, now on real data).
- candidate B: **disposable-host persistence** — the app's op-log spills to a signed replica the user owns
  (the W-PERSIST / 3-host replica story), recoverable on a fresh device.
- **§INTEG-WIRE** — the chosen slice works in idempiere.html via whitebox §-log; 0 pageErrors; existing erp
  witnesses still green (regression).
- **§INTEG-FRESHNESS** — any NEW SW-cached asset is added to erp/sw.js PRECACHE + version-bumped; the deploy-
  boundary convergence is WITNESSED (a fresh deploy reaches a returning user within ONE reload — the SWR lesson
  from PR #188: prove the freshness invariant, don't assert it).

## Deploy (gated)
Held PR until the user says "deploy". On go: merge → bump erp/sw.js + ?v= → verify live on Pages (the slice works,
sw version current, deploy-boundary convergence holds). Then continue down FRONTEND_LANE_MASTER §OUTSTANDING.

## Out of scope (later, not this prompt)
Relay on real compute (Functions/Worker) + sig-verify at /push + https; incremental rebase (?after=N). Nothing
in the UI depends on these — they are deployment infra for multi-writer sync, deferred until a real multi-writer
need appears.

## Done = §INTEG-KERNEL-DIFF + GREEN(gate) + COLLAPSE + POSTINGS-RECONCILE(maxDiff=0) + SIGNER-REUSE + one WIRED
## slice + FRESHNESS, each whitebox §-witnessed (read the logs); existing erp witnesses still 🟢; PR HELD for
## "deploy". Append a # DONE ledger here with a §-line proving each. Then the substrate is IN THE PRODUCT.

---

# PROGRESS — Phase 0 (2026-06-08)

## §ARCHIVE (standing rule: archive whatever we overwrite + note it here)
The three pre-collapse `kernel_ops.js` copies are archived verbatim at
`build/erp/_archive/kernel_collapse_2026-06-08/` (APP-erp-v6 327L · VIEWER-v6 323L · CANONICAL-build-erp-v8
535L) with a README carrying the full drift map + restore note. The COLLAPSE overwrites
`~/bim-ootb/erp/kernel_ops.js` and `~/bim-ootb/viewer/kernel_ops.js` with the reconciled canonical; both
their pre-overwrite states are in that archive.

## §INTEG-KERNEL-DIFF — 🟢 DONE
Drift is BIDIRECTIONAL (no copy was a clean superset):
- canonical build/erp v8 AHEAD: `commitGroup`/`sealFrom`/`assertRateAsInput` exports; `ensureTable` adds
  `gid` col + per-db `__kernelOpsTableCreated`; `verifyChain` adds W-OPGROUP group-torn logic.
- app erp/ v6 AHEAD on ONE thing: `commitOp` deterministic `ts` 7th param (ERP.md §0.16). NO app caller
  passes it (`grep commitOp(` over erp/+viewer/ = all 4–5 arg), so it is DORMANT — but it is the
  byte-stable-replay capability the period-close substrate relies on, so it must survive the collapse.
- KEY: `crud_overlay.js:517` checks `typeof K.commitGroup !== 'function'` → the DEPLOYED app (v6, no
  commitGroup) is silently running the CRUD write path in DRY FALLBACK. Canonical v8 is the kernel
  crud_overlay/rule_fold were written for; the collapse ACTIVATES the real write path.

## §INTEG-KERNEL-GREEN (THE GATE) — 🟢 GREEN
Ran the live-app erp witnesses + the substrate witnesses against the canonical kernel in an isolated
worktree (`/tmp/wt-kernel-green` off fresh origin/main; shared tree untouched).
- 10/10 app witnesses 🟢, pageErrors=0: `poc_rule_edit` (§RULE-EDIT-POC PASS, chainOk=Y, oracle
  rebuilt==live), `poc_rule_client_scope` (§RULE-CLIENT-SCOPE PASS), `poc_mobile_cards` (§MOBILE-VIEW
  PASS), `poc_init_instant` (§INIT-INSTANT-RESULT PASS), `poc_gated_complete` (§GATED-COMPLETE-POC PASS),
  `poc_i4_reconcile` (§I4-RECONCILE PASS, tamber caught), `poc_accts_posted` (POC-ACCTS-POSTED ALL PASS),
  `poc_kanban_write` (§KANBAN-WRITE-RESULT PASS), `poc_kanban_persist` (§KANBAN-PERSIST-RESULT PASS),
  `test_idempiere_fold` (ALL PASS, 60 windows folded).
- NOTE: two scripts (`poc_accts_posted`, `test_idempiere_fold`) first went RED with
  `Cannot find module 'sql.js'` — environmental (fresh worktree had no top-level node_modules; the error
  fires BEFORE any kernel loads, so it is kernel-independent). Symlinked node_modules → both 🟢. NOT a
  kernel regression.
- 5/5 substrate witnesses 🟢 against the reconciled canonical: `test_kernel_{period_close,sync,rebase,
  relay,replica}` (W-PCLOSE/W-SYNC-FSM/W-REBASE/W-RELAY/W-REPLICA).

## reconcile-FORWARD applied
`ts` 7th param folded into canonical `build/erp/kernel_ops.js` `commitOp` (additive; default path
byte-identical — `stamp = (ts!=null)?ts:Date.now()`). Canonical is now a true superset; re-witnessed
10/10 app + 5/5 substrate 🟢 against the reconciled version.

## §INTEG-COLLAPSE — 🟢 DONE (HELD PR #195, bim-ootb)
Worktree off fresh origin/main; `erp/kernel_ops.js` + `viewer/kernel_ops.js` replaced by the reconciled
canonical (exactly 2-file diff). Viewer-side proven safe: per-fn diff shows undo/redo/replayOps/compact
byte-identical; viewer browser probes are not self-contained in a bare worktree (null-db crashes v6 AND
v8 identically — environmental). PR HELD; deploy gate = bump erp/sw.js CACHE_VERSION + ?v= on kernel_ops
+ witness one-reload freshness. Archive at build/erp/_archive/kernel_collapse_2026-06-08/.

# PROGRESS — Phase 1 (2026-06-08) — 🟢 DONE

## §INTEG-POSTINGS-RECONCILE — 🟢 (witness `scripts/test_integ_postings_reconcile.js`, W-POSTINGS-RECONCILE)
The POC proved only the SYNTHETIC `{account,cents}` shape. The LIVE app emits double-entry `POST` ops:
`parameters={table,id,lines:[{account_id,amtacctdr,amtacctcr}],acctschema}` (per ~/bim-ootb/erp/erp_kernel.js
applyOne §13.1, ΣDR==ΣCR). Per-account balance b/f = Σ(DR)−Σ(CR) cents. Extended `erp_period_close.foldBalances`
ADDITIVELY to fold the real POST shape (synthetic path byte-identical → frozen `test_kernel_period_close`
still 🟢). Witness log `build/erp/test_integ_postings_reconcile.log`:
- A1 closing balances NON-EMPTY (POST folded, not ignored) · A2 == hand-computed net-per-account maxDiff=0c
  (`{101:120050,108:43413,400:-163463}`) · A3 balSum=0 (double-entry) ·
- B1 genesis fold == expected maxDiff=0c · **B2 FALSIFIER fold[ckpt+P2]==fold[genesis] maxDiffCents=0** ·
  B3 AR(101) settles to 0c.
- The RED-first run proved the gap (closing={} when POST ignored) before the fix.

## §INTEG-SIGNER-REUSE — 🟢
Checkpoint tip signed via the APP signer `~/bim-ootb/erp/erp_signer.js` (`makeSigner(mintKeypair)` → sign/verify,
ECDSA P-256), NOT erp_snapshot_sign's pinned DEMO key. A4: sig verifies under the app signer's OWN pubkey;
signed_by='edge:app-signer'. NOTE: loadOrMint's IndexedDB custody is browser-only → verified at Phase 2 wiring;
makeSigner IS the app's sign path, so the node falsifier reuses real app-signer code, not the demo key.

# PROGRESS — Phase 2 (2026-06-08)

## slice A = period-close in-app — 🟢 WIRED (§INTEG-WIRE, PR #197 → main; sw v600 the deploy switch)
glassbowl.html live sidecar op-log → window.PeriodClose; witness bim-ootb/erp/tests/poc_period_close_wire.js 🟢 6/6.

## slice B = disposable-host persistence — 🟢 WITNESSED WHITEBOX (user "proceed B2", 2026-06-08)
`scripts/test_persist_slice.js` (W-PERSIST-SLICE) → 🟢 8/8. Op-log spills to a SIGNED replica the user owns;
a ZERO-STATE fresh device recomputes tip === signed tip + recovers the books to 0c, and a forger without the
user key is rejected. Composes FROZEN substrate (erp_replica_client + erp_period_close.foldBalances + canonical
kernel) with REAL app POST ops + the REAL app signer (erp/erp_signer.js, §INTEG-SIGNER-REUSE — NOT the demo key).
Committed `90d12e41` on bim-compiler `feat/erp-substrate-phase012`. Engine source-of-truth (Phase 0/1) committed
`3d9e07d1`. NOTE: §INTEG-WIRE (a glassbowl/erp export+recover GESTURE) + §INTEG-FRESHNESS for slice B are the UI
lane's job, HELD until the user says "deploy" — the BACKEND lane stops at the whitebox witness (its contract).
