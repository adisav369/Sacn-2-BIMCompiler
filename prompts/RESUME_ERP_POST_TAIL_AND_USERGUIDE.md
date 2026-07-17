# ⚠ DO NOT REMOVE — Scope guard / SONNET FOLLOW-THROUGH CARD (written by the Fable-5 session, 2026-07-18)
# Scope: TWO bounded lanes, worked WORK-TO-ZERO in order. §A finishes the Doc_* posting tail (2 classes,
#   machinery already built and proven — this is execution, not research). §B writes the ERPUserGuide's
#   high-level navigation + the CORE S&D standard-flow chapter (Sales Order → replenishment → PO → shipment
#   → final accounts → financial reporting). Leave every addon lens (POS/Kitchen/WH/Tenancy/BIM-4D/Ninja)
#   as-is — §B is core-ERP only, per the user 2026-07-18.
# READ THE LOG after every run (exit ≠ evidence): every poc_* via `bash build/erp/run_witness.sh scripts/poc_X.js`.
# Honour this preamble until every item below is ✅ or ⛔.
#
# GIT: start from FRESH `origin/master` (`fable/meshdb-livewire` was squash-merged twice — PR #44/#45 —
#   re-using it collides). Push permission is ON (CLAUDE.md 2026-07-17): commit, push, PR, merge — the
#   `system-is-real` CI check is a KNOWN pre-existing red-X (memory `project_ci_system_is_real_red_x`),
#   NOT yours and NOT required; do not chase it.

## WHERE THIS PICKS UP (state as of 2026-07-18, all on master)
- **52 surfaces/classes oracle-equivalent** (`docs/internal/ERP_COVERAGE_MATRIX.md` headline). 17 of the
  20 `org.compiere.acct` factory posters fold at `maxDiff=0c`. The last three: C_Cash, M_Inventory
  (both §A here), M_Production (⛔, stays).
- The B-3 machinery is BUILT and reusable: `scripts/generate_post_oracle.sh` (scratch-clone → OSGi-hosted
  posting → capture → drop) + `scripts/logic_oracle/PostingOracleTest.java` (the vendor `org.idempiere.test`
  tycho harness) + `scripts/capture_post_b3_fixture.js` + the diff pattern in `scripts/poc_post_b3.js` /
  `scripts/poc_post_tail.js`. Cards: `prompts/FABLE5_B3_POSTING_ORACLE.md` (read its §W-2 EXECUTION SPEC
  block — every infra landmine already named) + `prompts/HARDEN_MATRIX.md §W-POST-TAIL`.
- Witness bundle green: poc_post_b3 · poc_post_tail · poc_post_harden · poc_factacct_doc · poc_doc_poster ·
  poc_morder_post · poc_alloc_fx · poc_money_post · poc_matchinv_fx · poc_gljournal · test_report_fin
  (TB 46574.97 / 300 rows).

## §A — finish the posting tail (2 classes; the generator does the heavy lifting)
Both classes have REAL seed documents that were simply never posted — so unlike B-3 there is NO seed
authoring at all: drive the REAL engine over the REAL rows on a scratch clone, capture, diff.

### A-1 C_Cash (ad_table_id 407) — post the 2 existing CO cash journals
- Facts (verified 2026-07-18): 3 `c_cash` docs client 11, 2 at `docstatus='CO'`, ALL `posted='N'`, 0 fact rows.
- Extend `PostingOracleTest.java` (or add a sibling test class next to it — the generate script rsyncs
  whatever is in `scripts/logic_oracle/` by name) with a step that does NOT create anything:
  load each CO `MCash`, run `DocManager.postDocument(ass, MCash.Table_ID, id, true, false, trxName)`,
  assert posted, `commit()`. Reuse `driveCO`'s posting half — the docs are already CO, do NOT re-processIt.
- Read `Doc_Cash.createFacts` FIRST (251 lines, org.compiere.acct) and write the manifest spec into
  HARDEN_MATRIX §W-POST-TAIL before coding: expect per-cashline legs vs `{CashBook.Asset}` /
  `{CashBook.CashTransfer}` / charge/expense variants — cite lines, don't guess. glassbowl already
  carries `c_cash`, `c_cashline` (amount), `c_cashbook_acct` (cb_asset/cb_cashtransfer/cb_receipt).
- Capture: extend `capture_post_b3_fixture.js` (or a small tail-fixture twin) with fact_acct(407) + any
  missing cashbook acct columns (ADDITIVE only). Manifest → `doc_poster.js` (`cash` basis) + diff band in
  `poc_post_tail.js` (same per-doc × schema × (account,side) integer-cent shape). maxDiff=0c gate.

### A-2 M_Inventory (ad_table_id 321) — complete + post the 3 existing draft physical inventories
- Facts: 3 real `m_inventory` DRAFTS client 11 (0 CO, 0 posted). In the test: load each MInventory,
  `processIt(CO)` via the engine (the internal postIt fires — CLIENT_ACCOUNTING='I'), assert
  posted, `commit()` per doc (the per-step-commit lesson: null-trx readers must see rows).
- Read `Doc_Inventory.createFacts` (523 lines) first — expect per-line DR/CR {Product.Asset} vs
  {Warehouse.Differences}|{Product.InventoryClearing} at the product cost (the W-FOLD-MOVEMENT cost hop:
  schema costingmethod → m_costelement → m_cost.currentcostprice). Charge-variant lines cite their branch.
- Gotchas already solved once — reuse, don't rediscover: completion may hit NOT-NULL-no-default columns
  (query information_schema first, the B-3 lesson) and period checks (auto period control is ON, ±100d,
  so current-date completion is fine; the DRAFTS carry old movementdates — if `testPeriodOpen` rejects
  them, set MovementDate/DateAcct to now BEFORE processIt and NAME that as seed-date normalization in
  the § log — it is input prep, not fact authoring).
- Same capture/manifest/witness flow as A-1. If a draft genuinely cannot complete (data invalid),
  ⛔ it BY NAME with the engine's own error and move on — 2/3 posted is an honest result.

### A-3 M_Production — ⛔ stance (do not reopen)
0 documents AND no component `m_cost` rows (the W-FOLD-PRODUCTION named deferral). Do NOT synthesize
costs. Only a future costed-BOM seed reopens this. Leave the ⛔ line in the matrix as-is.

### §A bank + regressions (non-negotiable)
- Re-run the WHOLE bundle listed above + poc_post_tail; all exit 0, logs READ.
- Bank: matrix row edit (ledger 52→54 if both land, headline count word), HARDEN_MATRIX §W-POST-TAIL
  DONE line, PROGRESS.md archive line. Every claim = a § line (Watchdog protocol).

## §B — ERPUserGuide: high-level navigation + the core S&D flow (docs/ERPUserGuide.md)
The guide today (1186 lines) is entry-points + POS/BIM/Ninja addons; the CORE trade cycle has no
walkthrough and there is no top-level map. Two sections to write, addons untouched:

### B-1 "The lay of the land" — high-level navigation (insert right after Quick start)
One screenful: the mental model in this order —
  bubbles front door → Login/tenant → the Bottom Pill Bar (link §3) → Windows/Tabs/Fields (link §4/§5)
  → the Process Button (link §6) → where documents live (Sales Order / Purchase Order / Shipment /
  Invoice / Payment windows) → where the books live (Posting Preview, Trial Balance, Financial Reports §8).
Rules: navigation ONLY (what is where and why), no feature marketing, every claim points at an existing
section or a live surface; a small mermaid map is welcome (docs render it). Match the guide's existing
voice (`feedback_user_guide_quality_bar` memory: quality bar + one-session-for-related-guides).

### B-2 "The standard flow — order to cash, procure to pay, books to reports" (new top-level section)
The S&D-standard cycle, told ONCE as a continuous story with the demo data, each step = do-it + what-
posted. Every step below is ALREADY live and oracle-proven — cite the witness in a footnote-style aside
so the guide inherits credibility without turning into a test report:
  1. **Sales Order** — create/complete (C_Order; completeIt fan-out W-FOLD-COMPLETE).
  2. **Shipment** — the generated M_InOut; on-hand drops (qty spine W-FOLD-QTYONHAND); COGS/Inventory GL
     (W-FOLD-INOUTGL).
  3. **Customer Invoice → AR** — fact lines DR Receivable / CR Revenue+Tax (W-POST-HARDEN, Posting Preview
     shows it live — W-DOC-POSTER).
  4. **Receipt & Allocation** — C_Payment then C_AllocationHdr incl. discount/write-off + VAT correction
     (W-FOLD-PAYMENT / W-FOLD-ALLOC).
  5. **Replenishment** — on-hand fell → ReplenishReport suggests the PO (W-FOLD-REPLENISH; the POS §P-4
     section already demos it — LINK, don't duplicate).
  6. **Purchase Order → Receipt → Vendor Invoice → Match** — PO (commitment ∅ by config, W-MORDER-POST),
     receipt, AP invoice DR InventoryClearing / CR V_Liability (W-FOLD-AP-INVOICE), M_MatchInv clearing
     (W-FOLD-MATCHINV), M_MatchPO's honest ∅ under Average costing (W-POST-TAIL).
  7. **Final accounts** — GL Journal for the manual leg (W-FOLD-GLJOURNAL), bank statement reconcile
     (W-POST-TAIL BankStatement), period close = the posted `fact_acct` journal, TB balances to the cent
     (test_report_fin 46574.97).
  8. **Financial reporting** — link §8 (Balance Sheet / Income Statement / Cash Flow oracle-equivalent,
     W-PA-REPORT) + NinjaExcel workbook.
- Fixed-asset & project postings (B-3's six classes) get ONE paragraph as "also in the books", not a
  walkthrough — they are core-adjacent, the walkthrough stays the trade cycle.
- Verify navigation claims against the LIVE surface (localhost per `feedback_localhost_full_building_url_testing`),
  not from memory; screenshots only where the guide already uses them.
### B-3 out of scope (explicit): POS/Kitchen/WH lenses, Tenancy/HR_BIM, 4D/5D scheduling, Ninja mode —
  already documented; do not restructure them this session.

## SESSION END
- Every §A/§B item ✅ or ⛔-with-the-one-question. Update THIS file's DONE appendix (§-lined), PROGRESS.md,
  push everything (zero local-only commits), PR + merge per the git note at top.
