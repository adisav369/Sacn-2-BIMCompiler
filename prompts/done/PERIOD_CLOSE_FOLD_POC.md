# ⚠ DO NOT REMOVE — Scope guard
# Scope: a SANDBOX, witness-led POC that turns HolyGrail's last open "hard part" — **compaction = period-close
#        SIGNED CHECKPOINT = balance brought forward** — from an abstract node proof (poc_showstopper §SHOW-CKPT,
#        ALREADY GREEN) into a REAL-KERNEL, then BROWSER, end-to-end fold. This is the **contract-freeze
#        milestone** for the §0.20 distributed substrate: once a closed period collapses to a signed opening
#        balance AND the books still reconcile to the cent over the REAL kernel_ops log, the op-log/sign/sync/
#        checkpoint API is COMPLETE and the UI can integrate on a frozen contract.
# NON-NEGOTIABLE: Spec-first; witness-led (each test NAMES the issue it proves/disproves); §-log first; the
#        Log Mandate (save every run to a log, READ the log before any conclusion — exit code is NOT evidence);
#        deterministic (ids/timestamps/balances/keys are recorded INPUTS — NO Date.now()/Math.random() in any
#        fold/replay path; balances in INTEGER CENTS, exact). REUSE proven primitives; invent no crypto.
# HANDS-OFF: do NOT touch the UI lane (prompts/FRONTEND_LANE_MASTER.md, ~/bim-ootb/erp/idempiere.html or any
#        erp/*.js). This POC is engine-only: build/erp/ source + scripts/ witnesses + (optional) a dev-bucket POC
#        page. NO GH-Pages / mkdocs deploy. NO bim-ootb-live. Dev (OCI bim-ootb-dev/sandbox/erp/) is the ceiling.
# Read first (the resume context):
#   - memory/project_erp_sync_fsm.md  ← THE HANDOFF: every file/witness/URL from the substrate session.
#   - docs/HolyGrail.md §"The hard parts, worked through" (lines ~333-342: "signed checkpoint carrying closing
#     balances + chain-head fingerprint, signed by the controller"; "archived cold, not deleted"; "end state:
#     keep just the ledger") — the claim this POC must make EXECUTABLE on the real kernel.
#   - scripts/poc_showstopper.js §SHOW-CKPT — the PROVEN logic to LIFT (signed ckpt = balance b/f, falsifier
#     reconciles to the cent, tamper caught). poc_showstopper is abstract/node; this POC makes it real-kernel.
#   - build/erp/kernel_ops.js — REAL kernel: commitOp / commitGroup / sealChain / sealFrom / verifyChain /
#     compact (NOTE: compact() is NOT checkpoint/balance-aware today — that is the gap to close).
#   - build/erp/erp_snapshot_sign.js — ECDSA P-256 tip signer (pinned controller key) from this session.
#   - scripts/test_kernel_{sync,rebase,relay,replica}.js — the substrate witnesses; RE-RUN as regression.

---

# Period-Close Fold — Signed Checkpoint = Balance B/F (the contract-freeze milestone)

## Why now
HolyGrail §"hard parts" resolves compaction as: *at period close, write one signed checkpoint carrying the
closing balances + the fingerprint of the chain head; archive the closed period cold; the live tab carries
only the open period + the last checkpoint.* `poc_showstopper.js §SHOW-CKPT` already proved this **abstractly**
(node, integer cents, falsifier `maxDiffCents=0`, tamper caught). What is NOT yet proven: the SAME fold on the
**real `kernel_ops` log** (commitGroup/sealChain/verifyChain), and then **in a browser over SQLite-WASM**. Closing
that is the last substrate "hard part" — after it, the op-log contract is whole and the UI integrates on a frozen API.

## The claim under test (one HolyGrail claim → witnesses)
> A closed accounting period can be collapsed to a single **signed checkpoint op** (closing balances in cents +
> the prior chain-head fingerprint). The pre-checkpoint ops are dropped from the live log. Re-folding
> `[checkpoint + post-checkpoint ops]` yields **byte/cent-identical** state to folding the full history from
> genesis — so compaction loses nothing — and the checkpoint is **tamper-evident** (signature + chain).

## Witnesses (each NAMES the issue; §-tag; against the REAL kernel)
- **§PCLOSE-FOLD** — `closePeriod()` folds the closed period's op-groups to closing balances (integer cents) and
  emits ONE checkpoint op (`op_type:'PERIOD_CLOSE'`, params `{period, closing_balances, prev_tip}`), **signed**
  by the controller key (reuse `erp_snapshot_sign.signTip` over the checkpoint canonical / the new tip).
- **§PCLOSE-BF** — the checkpoint IS the next period's OPENING balance: the next period folds **from the
  checkpoint balances**, never re-reading pre-checkpoint ops. Balance brought forward, literally.
- **§PCLOSE-COMPACT** — pre-checkpoint ops are removed from the live log (working set bounded — cite count
  before/after); `verifyChain` on the live log now verifies back only to the checkpoint (the new genesis).
- **§PCLOSE-RECONCILE (THE FALSIFIER)** — fold `[checkpoint + post-ckpt ops]` vs fold full genesis history →
  `maxDiffCents === 0`. If not zero, balance-b/f is WOUNDED → STOP and correct HolyGrail §compaction (do NOT
  paper over it). This is the load-bearing test.
- **§PCLOSE-TAMPER** — altering the checkpoint's closing_balances (or an archived op) is caught: the controller
  signature fails to verify (no private key to re-sign) and/or the chain breaks at the exact op.
- **§PCLOSE-DET** — rebuild the whole scenario → byte-identical checkpoint tip + signature verifies. No
  `Date.now()`/`Math.random()` on any fold/replay path (ids/ts/balances/keys are recorded INPUTS).
- **§PCLOSE-REOPEN** (optional reinforcement) — a period re-open is a SUPERSEDE op that re-chains onto the head
  (the iDempiere period-reopen wrinkle), not an in-place edit; the original checkpoint stays in the chain.
- **§PCLOSE-BROWSER** — the same, proven in a browser over SQLite-WASM (sql.js), numbers MEASURED
  (performance.now, not asserted — the "ERP perf claims" rule): close-fold ms, bootstrap-from-checkpoint vs
  from-genesis (cite the speedup; mirrors VOLUME_POC §VOL-BOOTSTRAP), verify ms.

## Spec (the contract — written BEFORE code, per CLAUDE.md Spec-First)

### The accounting fold (deterministic, integer cents)
A **balance-bearing op** (a journal posting) is any kernel op whose parsed `parameters` carries BOTH a string
`account` and an integer `cents`. The fold is pure accumulation: `bal[account] += cents`. Double-entry ⇒ a
balanced period sums to 0 across accounts. Document ops (orders/ships/invoices/payments) carry no `cents` and are
skipped — only their journal postings move balances (the extract-only discipline; mirrors poc_showstopper's
`Fact_Acct {Account, Amount}` on the REAL kernel's generic `op_type/parameters`). The fold reads `account/cents`
ONLY — never `timestamp`/`id`/`op_uuid` — so it is timestamp-independent and replay-exact. A posting may carry a
`gid` (op-group id) for traceability; group-level atomicity is already proven (poc_showstopper §SHOW-ATOMIC) and
is out of scope here.

### The checkpoint op (the live-log anchor)
One kernel op, `op_type:'PERIOD_CLOSE'`, `parameters = { period, closing_balances, prev_tip }` — all DETERMINISTIC
(balances integer cents; `prev_tip` = the chain head fingerprint of the period being closed). The op is committed
through the kernel like any other, then **re-stamped to a recorded `ts`** (an INPUT, not `Date.now()`), then sealed.
Because the kernel hashes `parameters` but NOT the `sig` column, the checkpoint's `closing_balances` and `prev_tip`
are inside the hash chain (tamper ⇒ chain breaks at that op), while the controller signature lives OUTSIDE it.

### The signed checkpoint (the controller attestation)
After compaction + re-seal, the new chain head `tip` is the fingerprint of `[PERIOD_CLOSE]`. The controller signs
**that tip** with `erp_snapshot_sign.signTip(privJwk, tip)` (ECDSA P-256, the SAME W-SIGN primitive that signs the
replica snapshot — HolyGrail "signed checkpoint carrying the chain-head fingerprint, signed by the controller").
The signed-checkpoint artifact returned/archived = `{ period, closing_balances, prev_tip, tip, sig, signed_by }`.
ECDSA `k` is random ⇒ the sig BYTES vary per run, but the `tip` is byte-identical on rebuild and the sig VERIFIES
under the pinned controller pubkey — that is the determinism claim (§PCLOSE-DET), exactly as poc_showstopper §SHOW-DET.

### Compaction = balance brought forward (the gap kernel.compact() does NOT close)
`closePeriod` deletes every pre-checkpoint op from the LIVE log (`id < ckptId`), leaving `[PERIOD_CLOSE]` as the
new anchor, then re-seals from genesis. The closed period is the **cold archive** (the full pre-close log, kept
separately — never deleted). `verifyChain` on the live log now verifies back only to the checkpoint. The next
period's ops chain off the checkpoint tip; `bootstrapFromCheckpoint` reads the latest `PERIOD_CLOSE`'s
`closing_balances` as the OPENING balance and folds only post-checkpoint ops — balance brought forward, literally,
never re-reading the archive.

### Module API (build/erp/erp_period_close.js — UMD, browser+node, composes ON the kernel)
- `foldBalances(ops, opening?) → { bal }` — ops = `kernel.replayOps(db)` output (params already parsed); `opening`
  = a `{account:cents}` map brought forward (default empty). Skips `PERIOD_CLOSE`/`PERIOD_REOPEN` + non-postings.
- `closePeriod(db, kernel, signer, periodMeta) → { period, closing_balances, prev_tip, tip, sig, signed_by, archived, liveLen }`
  where `periodMeta = { period, ts }` (ts = recorded close timestamp). signer = `erp_snapshot_sign` + a bound priv JWK.
- `bootstrapFromCheckpoint(db, kernel) → { period, opening, current, postCount, fromCheckpoint }` — opening from the
  latest checkpoint, `current` = fold of post-checkpoint ops over it.
- `reopenPeriod(db, kernel, signer, meta) → {...}` (optional §PCLOSE-REOPEN) — appends a `PERIOD_REOPEN` SUPERSEDE op
  chained onto the head; the original `PERIOD_CLOSE` stays in the chain (audit), never edited in place.

### Falsifier (§PCLOSE-RECONCILE) — the load-bearing test
Two DBs from the SAME fixture: (A) close P1 → compact → append P2 → `bootstrapFromCheckpoint`; (B) fold the FULL
P1+P2 log from genesis, no close. `balEqual(A.current, B.bal).maxDiffCents` MUST be `0`. Non-zero ⇒ balance-b/f
is wounded ⇒ STOP and correct HolyGrail §compaction (do not paper over).

## Build (engine-only; reuse, don't reinvent)
1. **Spec** the checkpoint op shape + the close/bootstrap contract in this file's spec section before code.
2. **`build/erp/erp_period_close.js`** (new, UMD, browser+node) — `closePeriod(db, kernel, signer, periodMeta)`
   and `bootstrapFromCheckpoint(db, kernel)`. It is a FOLD over the kernel log + one signed checkpoint write +
   a checkpoint-aware compaction. Do NOT bloat kernel_ops.js; this composes ON it (commitOp/commitGroup/
   sealChain/verifyChain). Reuse `erp_snapshot_sign.js` for the signature. Balances integer cents.
3. **`scripts/test_kernel_period_close.js`** (W-PCLOSE) — node witness against the REAL kernel
   (`~/bim-ootb/viewer/kernel_ops.js` via the `freshK()` dodge, as the other test_kernel_*.js do). Log to
   `build/erp/test_kernel_period_close.log`. Prove every § above. Mirror poc_showstopper's cent-exact falsifier.
4. **Browser POC** — extend `build/erp/sync_poc.html` (or a new `period_close_poc.html`) to show §PCLOSE-BROWSER
   live with measured numbers; headless-smoke it (puppeteer), then dev-deploy to `bim-ootb-dev/sandbox/erp/`
   (every `oci os object put` with `--content-type`; fetch-back + live smoke). Dev only.
5. **Regression**: re-run `test_kernel_{sync,rebase,relay,replica}.js` — all must stay 🟢 (period-close lives in
   a new module; if you touched kernel_ops at all, prove nothing regressed).

## On green (gated)
- Update `memory/project_erp_sync_fsm.md`: mark the contract-freeze reached; list the W-PCLOSE witness + numbers.
- ONLY if the user says "deploy doc": add a "§PCLOSE — these are runs" line to HolyGrail §"hard parts" and
  `mkdocs gh-deploy --force` (the compaction claim becomes a live browser demo). Otherwise leave the doc.

## Deferred to INTEGRATION (NOT this POC — for when the UI wires onto the frozen contract)
- **Canonical kernel reconcile**: 3 drifted `kernel_ops.js` (bim-ootb/erp · bim-ootb/viewer · build/erp). Collapse
  to the `build/erp` source ([[feedback_erp_source_of_truth]]) before wiring period-close into `idempiere.html`.
- **Reuse `erp/erp_signer.js`** key custody (loadOrMint) — do not fork a parallel controller key into the app.
- Integration ships via the FRONTEND_LANE_MASTER flow (worktree off origin/main → erp-only diff → §-witness →
  PR → `erp/sw.js` bump → verify on Pages), never the OCI-dev shortcut this POC uses.

## Done = every § above 🟢 in `build/erp/test_kernel_period_close.log` (read the log) + §PCLOSE-BROWSER measured +
## the 4 substrate witnesses still 🟢 + a `# DONE` ledger appended here with a §-line proving each claim.

---

# DONE — 2026-06-07 · CONTRACT-FREEZE REACHED (W-PCLOSE)

The §0.20 distributed-substrate op-log contract is now WHOLE: the last HolyGrail "hard part" (compaction =
period-close signed checkpoint = balance brought forward) is EXECUTABLE on the real `kernel_ops` log + in a
browser, not just abstract in `poc_showstopper.js`. The UI may now integrate on a frozen API.

**Built (engine-only, dev-ceiling, UI lane untouched):**
- `build/erp/erp_period_close.js` (UMD, browser+node) — `foldBalances` / `closePeriod` / `bootstrapFromCheckpoint`
  / `reopenPeriod` + `balSum`/`balEqual`. Composes ON the kernel (commitOp/sealChain/verifyChain/replayOps);
  signs the new tip via `erp_snapshot_sign.signTip`. kernel_ops.js NOT modified.
- `scripts/test_kernel_period_close.js` (W-PCLOSE) — node witness vs the REAL viewer kernel (`freshK()`).
- `build/erp/period_close_poc.html` + `scripts/drive_period_close.js` (headless smoke).

**Witnessed — `build/erp/test_kernel_period_close.log` (🟢 ALL PASS, exit 0):**
- §PCLOSE-FOLD — `closing={"AR":7500,"Revenue":-42500,"Cash":35000}` Σ=0; checkpoint signs the new tip
  `e41690f5fe1e…`, verifies under the controller key.
- §PCLOSE-COMPACT — archived=15 ops → live=1 (checkpoint only); live chain verifies off the checkpoint anchor.
- §PCLOSE-BF — P2 opens from the checkpoint (AR 75.00 paid → AR=0, Cash=425.00); only 3 post-ckpt ops folded.
- §PCLOSE-RECONCILE (FALSIFIER) — fold[checkpoint + P2] == fold[full genesis] → **maxDiff=0c**.
- §PCLOSE-TAMPER — (a) forged live ckpt balances break the chain at exactly the checkpoint op (brokeAt=16);
  (b) a re-sealed forger is STILL caught (honest sig over forged tip = invalid, no key to re-sign);
  (c) a forged archived op breaks its chain (brokeAt=8) AND the refold ≠ the signed balances.
- §PCLOSE-DET — rebuild → byte-identical tip `e41690f5fe1e0833…`; both sigs verify; sig bytes differ (ECDSA k random).
- §PCLOSE-REOPEN — reopen = a SUPERSEDE op chained onto the head; original PERIOD_CLOSE stays in the chain (audit).

**§PCLOSE-BROWSER (MEASURED live on OCI dev, headless + live-URL puppeteer, pageerror=0, dot=OK):**
N=20,000 P1 postings (40,000 ops) → close-fold ≈2.6–2.9 s; reconcile **maxDiff=0c**; bootstrap from checkpoint
≈0.9 ms vs from genesis ≈46–53 ms = **~52–59× speedup**, same final balance; tamper caught (chain breaks at the
checkpoint op). LIVE: `https://objectstorage.ap-kulai-2.oraclecloud.com/n/ax3cp6tzwuy2/b/bim-ootb-dev/o/sandbox/erp/period_close_poc.html`
(content-type `text/html`; fetch-back byte-identical). Dev bucket only — NO GH-Pages/live, idempiere.html untouched.

**Regression — the 4 substrate witnesses still 🟢:** `test_kernel_{sync,rebase,relay,replica}.js` all ALL PASS
(exit 0). kernel_ops.js was not touched, so nothing could regress.

**Doc:** HolyGrail.md left unchanged (gated on the user saying "deploy doc").
