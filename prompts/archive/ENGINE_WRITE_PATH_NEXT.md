# ⚠ DO NOT REMOVE — Continue the full-ERP write path (NEW SESSION handoff, from 2026-06-03)
# SCOPE: pick up the browser ERP write path after I-K (op-group atomicity) + I-J (rate-as-op-input) landed.
#   This prompt is the CONTINUATION pointer; the full issue map + decisions live in
#   prompts/ENGINE_FULL_ERP_ISSUES.md (read its §0.0 spine + §0.1 layer map + the DISCUSSION LOG first).
# READ THE LOG AFTER EVERY RUN. Exit code is not evidence. A claim with no §-log line is NOT done.
# NON-NEGOTIABLE: non-invent (never fabricate IDs/DocNos/rates/postings); honest (no over-claim, esp. perf —
#   see MEMORY feedback_erp_perf_claims); spec-first; witness-led (each test NAMES its issue); §-log first,
#   Playwright wiring-only; EXPLICIT GO before any deploy (Glassbowl-way: bump SW CACHE_VERSION).
# READ FIRST: prompts/ENGINE_FULL_ERP_ISSUES.md (§0.0/§0.1/§2.1/§2.2 + §I-K SPEC + DISCUSSION LOG) ·
#   docs/ENGINE_CONTRACT.md §1/§6.1 (the seam + the write-seam re-freeze) · docs/IDEMPIERE_2.md
#   §validation-stack (the 4-tier oracle) · site/kernel_ops.js v8 (commitGroup/sealFrom/assertRateAsInput) ·
#   site/crud_overlay.js (commitProcess → buildDocActionGroup → commitGroup).

---

## ▶ CURRENT (2026-06-04) — read this first; older STATE/NEXT below is history
**DONE this arc (all on PR #8 `feat/erp-write-path-ik-ij`, kernel now v9; NOTHING live):** I-K op-group ·
I-J rate-input · BigDecimal · §I-L money-fold · **§I-D period-close checkpoint** (kernel v9: `closePeriod` /
`latestCheckpoint` / opt-in `verifyChain({fromCheckpoint})` → verify bounded ∝ open period; witness
`scripts/poc_checkpoint.js §CHECKPOINT PASS`; full verify grows 24→93ms, bounded flat ~23ms). Both front-ends
localhost-tested clean: glassbowl (`scripts/probe_localhost_v9.js`) + the REAL renderer
`bim-ootb/erp/idempiere.html` (`scripts/probe_idempiere_v9.js §IPROBE PASS`). After-receipt gap specced:
`docs/CRUD_P_R_REPORT_SPEC.md §4`.

**THE KEY REALITY:** the v9 engine lives in `build/erp` + the glassbowl testbed, but the **real renderer
idempiere.html runs v6 + the old single-op `commitOp` path** (`bim-ootb/erp/`), and env mirrors are drifted
(site/deploy-dev/docs/bim-ootb all behind). The new primitives (`commitGroup`/`closePeriod`) are **dormant** —
nothing calls them in the real app yet.

**SYSTEMATIC PATH (inch forward, one bounded phase per session; spec-first, witness-led, GO before any live deploy):**
- **Phase 0 — Converge + drift gate** *(do first, small):* publish `build/erp` v9 coherently to every env
  (site, deploy/dev, docs, bim-ootb/erp, bim-ootb/viewer); add `scripts/check_kernel_drift.js` (`§DRIFT
  all-envs-match=Y`); re-run both probes. Exit: §DRIFT green + probes PASS.
- **Phase 1 — Wire primitives into idempiere.html:** move its CRUD write to `commitGroup`; add a period-close
  action calling `closePeriod`. Witness: in-browser `§`-log shows grouped commit + signed checkpoint.
- **Phase 2 — R4 receipt Print/Share** (`CRUD_P_R_REPORT_SPEC §4.1`): `window.print`/`navigator.share`/Blob,
  edge-only. Witness `§RPT-OUT`.
- **Phase 3 — Posting after Complete** (§13.6 record-keyed `fact_acct` re-extract → "Completed" moves the books).
- **Phase 4 — FORK 0 write-seam co-ratify (host lane) → coherent OCI/Pages deploy** gated by §DRIFT + SW bump +
  fetch-back-verify + EXPLICIT GO.

**⚠ SHARED WORKING TREE — do NOT branch-hop to commit.** Another session works `feat/revit-plus-lens` /
`feat/precision-pivot-cam` in this same folder concurrently. My branch-switching this arc dropped a local ref
(recovered) and swept that session's 12 files into PR #8 commit `02b2c383` (content safe — same as their
`a8e208f4`; the other session/CICD will reconcile, per user). Next session: stay on ONE branch, or use a
worktree — never `git switch` with a dirty shared tree.

## STATE — what is DONE (2026-06-03, all witnessed + localhost-tested)
- **SPINE DECIDED:** delegate-to-install + mirror NOW; standalone-browser-primary deferred (swappable layer).
  ⇒ I-B-hard / I-C / I-G / I-H are **install-side**, NOT browser-POC blockers. (Memory: [[project_erp_write_path_spine]].)
- **I-K op-group atomicity ✅** — kernel `commitGroup(db, opsArray, groupMeta)` + `sealFrom` + `gid` column in
  `site/kernel_ops.js` v8 (byte-identical mirror `build/erp/kernel_ops.js`). N ops, ONE group hash, all-or-none
  (torn → 0 rows), **sealed once from the tip** (kills the per-op I-D O(n²) reseal). `verifyChain` group-torn
  rule. Witness `scripts/poc_opgroup.js` → `§OPGROUP PASS`.
- **I-K UI ✅** — `commitProcess` (site/crud_overlay.js) commits the doc action via `commitGroup` (pure
  `buildDocActionGroup(op)` → `[statusOp]` today, **extensible to N**). The consequence set (ship/invoice/
  postings) is a clearly-marked DELEGATED extension point — NON-INVENT. Witness `scripts/poc_crud_group.js`
  → `§CRUDGROUP PASS` (`fabricatedConsequence=none`).
- **I-J rate-as-op-input ✅** — `assertRateAsInput` guard wired into `commitGroup`: a conversion-bearing op
  (params.convertedAmt or params.fx) MUST carry rate/rateDate/rateSource or the WHOLE group rejects
  (multi-currency disabled until rate-as-input). Witness `scripts/poc_rate_input.js` → `§RATE PASS`.
- **BigDecimal enforced ✅** — `bigdecimal.js` (== java.math.BigDecimal, proven) now LOADED in `glassbowl.html`
  before the kernel (site/ + build/erp/); the I-J conversion uses exact `BigDecimal`, never raw float. Verified
  in-browser (`window.BigDecimal` present, exact). **Follow-up:** audit broader money folds (erp_postings,
  reports) for raw-Number math (feedback_numbers_via_bigdecimal) — see NEXT item 7.
- **DEPLOYED to branch ✅ (NOT merged — CICD's job)** — branch `feat/erp-write-path-ik-ij`, **PR #8 → `full`**
  (github.com/red1oon/BIMCompiler/pull/8). Pre-commit compile gate passed. NOT yet OCI-deployed to
  bim-ootb-live (separate EXPLICIT-GO). `site/` is the gitignored publish mirror; tracked source = `build/erp/`.
- **Localhost-tested ✅** — real Chromium probe on `http://localhost:8848/glassbowl.html` (serve `site/`):
  kernel v8, no pageerrors, in-browser commitGroup all-or-none + rate guard reject + Edit-mode arms.
  `node /tmp/probe_localhost.js` / `build/erp/probe_localhost.log`. (Re-serve: `cd site && python3 -m http.server 8848`.)
- **Benchmark ✅ (honest, storage-primitive only)** — `scripts/bench_oplog_pg.js` → `build/erp/bench_oplog_pg.log`:
  vs Postgres-15 (iDempiere's engine, the live docker `postgres` container, TEMP table, real data untouched).
  Mature Postgres is FASTER at the raw primitive; the browser edge is **locality (offline/no-server), NOT speed**.
  iDempiere's full completeIt() (callouts+posting) was NOT measured (no Tomcat; delegated). Do not over-claim.

## NEXT — prioritized (pick ONE bounded task; spec-first, witness-led)
1. **DEPLOY (status: branched, OCI pending GO).** Code is on **PR #8 → `full`** (CICD merges). OCI deploy to
   **bim-ootb-live** (the real one) is the remaining EXPLICIT-GO step: bump SW `CACHE_VERSION`, follow CLAUDE.md
   Deploy Flow + deploy/OCI_UPLOAD.md §RULES (MIME!), smoke-test, fetch-back-verify. DO NOT OCI-deploy without GO.
2. **FORK 0 — co-ratify the WRITE seam** (ENGINE_CONTRACT §1/§6.1): `write(ctx, ops)` + manifest
   `gravityRank↔menuGroup`. Then make `commitGroup` reachable via the seam `write()` (today the UI calls
   KernelOps directly; the seam is the intended single coupling). Read seam is already frozen — don't re-ratify it.
3. **I-D residual — period-close checkpoint.** `verifyChain` still walks the whole log (verify_ms 10→17 @1000,
   on-demand not per-write). Implement the period-close signed checkpoint (HolyGrail.md + ERP.md §18.9) so
   verify/fold are bounded. Witness: verify cost flat across periods; `poc_volume_ceiling.js` style.
4. **INSTALL LANE (the delegate-to-install product work).** The sync/push that lets the operator's iDempiere
   commit the browser's ops with ITS sequences/callouts/acct-schema (I-B-hard, I-C, I-G, I-H). This is where the
   delegated consequence ops (Phase-3 extension point) actually get produced. Big; start with the pairing channel.
5. **Tier-triage the callouts** — `prompts/ERP_CALLOUT_PORT.md` (classify the 284 by iDempiere's 4 tiers;
   only field-tier is a UI overlay, the rest route to install/engine lanes).
6. **(Optional) Full iDempiere benchmark.** Bring up iDempiere (Tomcat + the live `idempiere` DB) to measure a
   real `completeIt()` end-to-end — the current bench is storage-primitive only. Honest perf framing required.
7. **Money-fold BigDecimal audit. ✅ DONE 2026-06-04 (§I-L).** Swept the read-side folds.
   `report_overlay.js` (`foldReceipt`/`foldTrialBalance`/`foldPnL`) CONVERTED to BigDecimal (exact 2dp STRING
   leaf, `balanced` via exact `isZero`); mirror re-synced (site/ was STALE — lacked TB+PnL). `erp_postings.js`
   AUDITED exact (integer-cent), left as-is. Witness `scripts/poc_money_fold.js` → `§MONEY-FOLD PASS` (11/11):
   OLD raw-Number+round2 DIVERGES from java.math.BigDecimal on signed sub-cent (balance/tax/net) + magnitude
   >2^53c (MEASURED, not invented); LIVE BigDecimal folds == proven golden. `test_report_overlay`/`test_report_fin`
   updated to the exact-string contract (cent-exact) — ALL PASS. **Residual flagged:** `crud_overlay.js:87`
   `Number(val)` coerces a money field on the New form into the op as a JS Number (write-path input capture,
   not this read-side fold) — queue for the write lane.

## GUARDRAILS
- Sacred: `site/kernel_ops.js` (+ build/erp mirror) is core — additive changes only, keep the two in sync.
- Non-invent: the consequence set (ship/invoice/postings) and any FX rate come from the install / §13.6 re-extract,
  NEVER fabricated in the browser. The extension point in `buildDocActionGroup` is where extracted ops slot in.
- Witnesses to keep GREEN on any kernel/write change: poc_opgroup, poc_crud_group, poc_rate_input, poc_chain,
  poc_kernel (run all; read the logs).

## # SESSION LOG (append here)

### 2026-06-04 — §I-D-CKPT LANDED (NEXT item 3: period-close checkpoint) — verify now bounded
- **Verified inherited state first (Log Mandate):** kernel v8, all 5 guardrail witnesses GREEN, PR #8 work intact.
- **Picked NEXT item 3 (I-D residual)** after scoping: FORK 0 is a JOINT re-freeze (ENGINE_CONTRACT §6.1, can't
  finalize solo); UI backlog needs GO-to-deploy. I-D checkpoint is pure-engine, behind-the-seam, fully completable.
- **DONE (spec-first → witness → impl):** spec `§I-D-CKPT` in ENGINE_FULL_ERP_ISSUES.md; witness
  `scripts/poc_checkpoint.js` → `§CHECKPOINT PASS` (9/9 checks); impl in `build/erp/kernel_ops.js` **v9** (additive:
  `kernel_checkpoints` table + `closePeriod` + `latestCheckpoint` + opt-in `verifyChain({fromCheckpoint})`).
  **MEASURED I-D win:** full verify grows `24→93ms` over K=4 periods; bounded stays FLAT `~23ms` (4.0× @period4).
  Default verify path UNCHANGED; all 5 guardrail witnesses still GREEN.
- **⚠ FLAGGED (pre-existing):** env mirrors stale — site/v6, docs/v6, deploy/dev/v4 vs build/erp v9 (handoff
  said "site v8" — false on disk). NOT synced (GO-gated full publish; lone-kernel sync = untested mixed state).
- **NOT committed / NOT deployed** — working-tree only. Resume: remaining browser-lane = FORK 0 write seam (needs
  host-lane co-ratify), then a checkpoint-anchored kernel *replay* bound (follow-on) + the UI/dictated backlog.

### 2026-06-04 — §I-L money-fold audit LANDED (NEXT item 7) + outstanding-issues ledger
- **Verified inherited state first (Log Mandate):** all 5 guardrail witnesses GREEN (poc_opgroup/poc_crud_group/
  poc_rate_input/poc_chain/poc_kernel), kernel v8, PR #8 open. Browser write-path TOP work (I-K+I-J+BigDecimal)
  intact.
- **Money-fold (item 7) DONE — spec-first, witness-led, MEASURED divergences (non-invent):** probed BEFORE the
  fix; found `round2`'s +EPSILON rescues small positives, so the HONEST gap is **signed sub-cent + magnitude
  >2^53c**, NOT "round2 is broken." Converted `report_overlay.js` 3 folds to BigDecimal (exact 2dp STRING leaf,
  `balanced`=exact `isZero`); `erp_postings.js` audited integer-cent-exact, left. Witness `poc_money_fold.js`
  `§MONEY-FOLD PASS` (11/11). Mirror site/ re-synced (was STALE, md5-identical now). Existing report tests
  updated to exact-string contract → ALL PASS. Logs: `build/erp/poc_money_fold.log` + `test_report_*.log`.
- **NOT committed / NOT deployed** — working-tree change only (build/erp/report_overlay.js + site/ mirror +
  2 tests + 1 new witness + 2 prompt docs). EXPLICIT GO still pending for OCI; commit/PR at user's call.
- **Outstanding-issues ledger delivered (user asked):** under the delegate-to-install spine, browser-lane open =
  {I-D verifyChain checkpoint(+I-I), FORK 0 write-seam, money-fold ✅now}. Install/oracle lane (deferred, decided)
  = {I-A, I-B-hard, I-C tier-triage, I-G §13.6, I-E merge}. Guardrail = I-H. Needs-GO = OCI deploy, I-F prod map.
  Housekeeping debt = erp_replay.js (cited, exists nowhere), stale bim-ootb/viewer/* paths, test_kernel_sign.js.
