# ⚠ DO NOT REMOVE — Scope guard
# Scope: BUILD the first metadata plugin end-to-end — close the §13.1 posting gap and prove the
#        "accounting genome" manifest (charge-model DR/CR→Acct + masters) on the REAL platform data.
#        HEADLESS POC in bim-compiler/scripts/ ONLY (Node + ad_seed.db). NO viewer wiring this session.
# Authority specs (FOLLOW, do not duplicate):
#   docs/PLUGIN_ARCHITECTURE.md  — §3.5 manifest, §3.5.1 resolver grammar, §13 prototype readiness (the work order)
#   docs/ENGINE_CONTRACT.md      — the engine↔UI seam (read/dispatch/manifest/verbs/verify + role ctx)
#   docs/IDEMPIERE_2.md          — the thin core (5-table bridge + verbs + signed op-log; state = the fold)
#   docs/DistributedERP.md §13   — gravity sharding (only if manifest() is touched)
# NON-NEGOTIABLE: spec-first; witness-led (each test NAMES the issue it proves/disproves); §-log first
#   (save the run, READ the log before conclusions — exit code is not evidence); deterministic / non-invent
#   (ids/ts/amounts are RECORDED INPUTS from ad_seed.db — no Date.now()/Math.random(); resolver reads real
#   columns, never guesses an account; absent value = report "absent", never synthesize).
# DISCIPLINE: ENGINE LANE ONLY — do NOT edit bim-ootb/viewer/* (renderer session, branch
#   feat/idempiere-master-detail). Do NOT switch/commit on that branch. No deploy. EXPLICIT GO before
#   any viewer wiring or deploy. Read ad_seed.db, copy it locally if needed; never mutate the seed.

---

# Engine — first posting plugin (close §13.1, prove the accounting genome)

## Why this session exists
The platform dazzles as a renderer but cannot yet POST. `PLUGIN_ARCHITECTURE.md §13.1` names the ONE gap:
the engine `journal` projection is a settlement edge (`id,batch_id,journal_id,source,metadata`) with **no
DR/CR, no account_id**. Closing it + driving one document type through a metadata manifest is the real
test of the thin-core thesis: a new posting doc-type should be **a manifest, not code**.

## Resume state (verified 2026-06-02 — re-verify, do not trust)
- `scripts/erp_kernel.js` — the Node kernel: verbs `CREATE_DOCUMENT/SET_STATUS/CREATE_LINE/ALLOCATE/MATCH`
  (`~72-80`), `applyOne` (`~90-134`), `replay()→{ops,hash}` (`~165-179`), `journal` projection (`~40`).
  `kernel_ops.js`/`erp_signer.js` provide the W-CHAIN/W-SIGN (ECDSA P-256, `_canonical`/`sealChain`).
- Account masters present in `bim-ootb/viewer/ad_seed.db` (counts verified): `C_ElementValue` 379
  (Account ID = `C_ElementValue_ID`; `Value/Name/AccountType`), `c_bp_customer_acct` 36
  (`c_receivable_acct`), `m_product_category_acct` 28 (`p_revenue_acct`,`p_cogs_acct`,`p_asset_acct`),
  `c_charge_acct` 6, `c_acctschema_default` 2 (fallback), `C_AcctSchema` (id 101 in samples). Real iD
  posting-line shape lives in seed `gl_journalline` (`account_id, amtacctdr, amtacctcr`) — MIRROR it.
- Multi-writer already PROVEN: `node scripts/poc_distributed.js` + `poc_showstopper.js` PASS — do not redo.

## Work order (per PLUGIN_ARCHITECTURE.md §13 — follow it for detail)
1. **Close §13.1 (engine glue, not plugin).** Extend the `journal` projection with `account_id,
   amtacctdr, amtacctcr` (mirror `gl_journalline`). Add a `POST` verb to the kernel that takes a document
   + a charge rule and emits **balanced** journal lines (ΣamtacctDR = ΣamtacctCR), signed + replayable
   like every other op. No per-plugin account logic in the verb.
2. **The resolver (host glue, §3.5.1).** Implement `{Master.Role}` → real column, keyed by
   `(master_id, c_acctschema_id)`, fallback `c_acctschema_default`: `{BPartner.Receivable}`→
   `c_bp_customer_acct.c_receivable_acct`, `{Product.Revenue}`→`m_product_category_acct.p_revenue_acct`
   (via product→category). Read masters from `ad_seed.db` (copy locally; read-only).
3. **The test plugin (Track B, §13.3).** The "Sales Invoice → Post" manifest verbatim from the spec
   (`doc_type:C_Invoice, acctschema:101, masters:[C_BPartner,M_Product], charge:[complete: DR
   {BPartner.Receivable}=GrandTotal, CR {Product.Revenue}=LineNetAmt]`). Pick a real invoice+partner+
   product from `ad_seed.db`. Author writes ONLY the manifest; the kernel + resolver do the rest.
   (Tax/rounding deferred — post net first; `{Tax.Due}` is a later charge row.)

## Witnesses (§-log first; the only evidence — append to a log file, READ it before concluding)
- `§PLUGIN-POST doc=C_Invoice#<id> dr=[<acctId>:amt] cr=[<acctId>:amt] balanced=Y (ΣDR=ΣCR)` — posts,
  balances, accounts resolved from REAL masters (e.g. receivable 234, revenue 229).
- `§PLUGIN-REPLAY post then replay → rebuildA==rebuildB agree=Y` — deterministic after the POST verb.
- `§PLUGIN-MINIMAL author wrote: lines=N files=1 newSkill=none` — authoring-cost headline (manifest only).
- `§PLUGIN-RESOLVE token={BPartner.Receivable} master=<bpId> → acct=<C_ElementValue_ID> source=<table.col>`
  for each token — proves non-invent resolution against the seed (fallback path logged when used).

## Acceptance
The prototype is DONE when the four `§` lines above are in the log against `ad_seed.db`, proving: §13.1
closed, the manifest posts a balanced entry with accounts resolved from real masters, and replay is
stable. Then STOP — viewer wiring + the Tier-1 overlay (Track A, touches bim-ootb pills.json = other
session) are a SEPARATE, coordinated session, EXPLICIT GO required.

## Guardrails
- Engine lane only; ad_seed.db read-only; no viewer edits; no deploy; no other-session branch.
- POST verb is generic (resolver + balanced lines); the doc-type specifics live in the manifest (data).
- Non-invent: every account/amount folds from ad_seed.db; missing → report "absent", never synthesize.
- Spec-first: cite `// Implementing PLUGIN_ARCHITECTURE.md §13.x` before code; witness before claim.

## Status
**CLOSED — DONE, 2026-06-02.** Authority: docs/PLUGIN_ARCHITECTURE.md §13 (resolved design pinned in §13.5).
Headless POC in `scripts/` (`poc_post.js` + `post_resolver.js`), engine glue in `scripts/erp_kernel.js`.
No UI, no deploy. Migration (Odoo fold) stays KIV.

**Engine seam handed off (consumers, separate sessions):**
- §13.6 — Fact_Acct-gated rollout of POST to all postable doc-types (engine lane; needs the real Fact_Acct import).
- §13.7 — `readPostings(recordRef, ctx)`: the role-gated "Accts Posted" read-fold (isshowacct via AD_Role).
- The RENDERER consumes both via `prompts/IDEMPIERE_RECORD_PANEL.md` (reuse the proven CRUD-P-R overlays in
  idempiere.html — "Accts Posted" = the Report verb, role-gated). This engine session does NOT do that wiring.

# DONE — claim ↔ §-line (log: build/erp/post_poc/poc_post.log, run against build/erp/post_poc/ad_seed.db copy)

| Claim | Evidence (§-line in the log) |
|---|---|
| §13.1 closed — `journal` projection now double-entry (`account_id, amtacctdr, amtacctcr`), `POST` verb added, balanced + replayable | `erp_kernel.js:40` schema + `applyOne` POST case (asserts ΣDR=ΣCR to the cent); `§PLUGIN-POST … balanced=Y` |
| Manifest posts a BALANCED entry from REAL masters (author wrote only the manifest) | `§PLUGIN-POST doc=C_Invoice#103 dr=[234:161.12] cr=[229:152,255:9.12] balanced=Y (ΣDR=161.12=ΣCR=161.12)` |
| Accounts resolved NON-INVENT from real seed columns (each token → column → C_ElementValue) | `§PLUGIN-RESOLVE {BPartner.Receivable} master=117 → acct=234 source=c_bp_customer_acct.c_receivable_acct element=518(12110 A/R-Trade)`; `{Product.Revenue} → 229 … element=758(41000 Trade Revenue)`; `{Tax.Due} master=105 → 255 source=c_tax_acct.t_due_acct element=596(21610 Tax due)` |
| POST is deterministic — replay rebuilds the projection exactly | `§PLUGIN-REPLAY post then replay → rebuildA==rebuildB agree=Y` (live=A=B=7184cb35) |
| POST couples ONLY through the op-log bus (§6) — no direct journal writes | `§PLUGIN-CODE tier=2 verb=POST coupling=oplog directCalls=0 (kernelOps.POST=1 journalRows=3 opSourced=Y)` |
| Authoring cost = one manifest, no new code | `§PLUGIN-MINIMAL author wrote: lines=3 files=1 newSkill=none` |
| No regression from the shared-schema change | sibling POCs PASS: poc_kernel / poc_identity / poc_longtail; test_crud_process_writeloop 11/11 (logs build/erp/post_poc/regress_*.log) |

DECISION (logged §13.5): superseded "post net first" — every seed sales invoice carries tax, so the
honest balanced entry is the 3-line one with `{Tax.Due}` (user-confirmed). STOP here: viewer wiring +
Tier-1 overlay (Track A, touches bim-ootb pills.json = other session) remain a SEPARATE, EXPLICIT-GO session.
