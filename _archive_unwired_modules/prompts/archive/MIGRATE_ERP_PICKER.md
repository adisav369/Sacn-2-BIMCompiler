# ⚠ DO NOT REMOVE — Migrate/Install ERP-picker + LIVE Odoo migration (NEW SESSION handoff, 2026-06-04)
# SCOPE: turn the Install/Migrate pills from honest stubs into the real "pick-your-ERP" migration dialog,
#   backed by the LIVE-PROVEN Odoo fold. READ THE LOG after every run. §-log first, witness-led, non-invent.
# SPIRIT (user, read FIRST): docs/ERP.md · docs/DistributedERP.md · docs/HolyGrail.md — the engine-as-data /
#   one-engine-N-dictionaries / delegate-to-install doctrine is the WHY behind all of this.

## ▶ STATE (what is DONE + PROVEN this session)
- **LIVE Odoo migration PROVEN.** `scripts/poc_odoo_fold_live.js` → **§ODOO-FOLD-LIVE PASS** (log
  `build/erp/odoo_fold_live.log`). Connects to the RUNNING Odoo 17 (docker `odoo` + `odoo-db`, JSON-RPC
  `http://localhost:8069/jsonrpc`, **db=odoodemo, login=admin, password=admin, uid=2**), re-pulls SO **S00023**'s
  O2C chain live (SO→2 lines→2 delivery moves→invoice INV/2026/00005→GL→reconcile, total **5002.50 paid**),
  folds it through the SAME pure adapter `scripts/odoo_adapter.js` (`A.buildEvents`) + the 6 kernel verbs:
  **mapped=5/5, verbs=[ALLOCATE,CREATE_DOCUMENT,CREATE_LINE,POST,SET_STATUS], newVerbs=[]**, invoice GL
  balances ΣDr==ΣCr=5002.50, live totals == the static `build/erp/odoo_oracle.json`. This UPGRADES the
  static `scripts/poc_odoo_fold.js` (oracle replay, §ODOO-FOLD PASS) to a live connection.
  **⚠ UNCOMMITTED** — `scripts/poc_odoo_fold_live.js` is new on the shared dirty `feat/revit-plus-lens` tree;
  next session commits it on the right branch (engine lane). Doctrine: the extractor runs **install-side**
  (Node, like this script), mirrors to the browser — the browser does NOT CORS into Odoo (delegate-to-install).
- **The write seam exists in-browser now** (`kanban_host.js` → `window.ERP.dispatch`, this session) — so a
  folded foreign chain can dispatch through the SAME seam the kanban uses (each foreign hop = one dispatch).
- **Install/Migrate are still STUBS** in `bim-ootb/erp/idempiere.html`: `openInstallFor`/`openMigrateFor` only
  `status()` a POC message (`§INSTALL-PILL action=install-stub` / `§MIGRATE-PILL action=migrate-stub`).
  `migrate_showme.js` (`window.MigrateShowMe.open()`) IS a real 4-step guided overlay (connect→run→watch→done→
  share), already wired to the in-page "↓ Migrate your iDempiere data (ShowMe)" button — the dialog to build on.

## ▶ THE BUILD — the ERP-picker Migrate dialog (user's spec, verbatim intent)
1. **A list of ALL ERPs, always**: **iDempiere · Odoo · SAP · Oracle · MS Dynamics** ("it is to come" — list them now).
2. **Detect what is present** (e.g. probe the local Odoo `:8069` / an iDempiere PG / etc.). **HIGHLIGHT the
   detected one, GREY the rest.**
3. **Default to the detected source** and ASK: *"Do you want to migrate your <Odoo> data?"* — other options stay
   in the list (greyed/coming-soon), still selectable.
4. **Odoo = the first REAL one** (live-proven above): on confirm → run the install-side extractor (the
   `poc_odoo_fold_live` path) → fold via `odoo_adapter` → **dispatch each hop through `window.ERP`** → the engine.
   The others render honest "coming" until their adapter lands (non-invent — never a fake migration).
5. Install vs Migrate: same dialog family. Install = bring data onto THIS device / QR-pair (delegate-to-install);
   Migrate = fold a foreign ERP. Detection drives the default for both.

## ▶ ALSO REQUESTED (smaller, common-sense)
- **Kanban/Graph fallback when there is NO pivot (docstatus) data**: show a **heat map** (or better) over a
  sensibly-detected **related/lookup** column instead of an empty board — "figure out related look-up data."
  (Today `_groupRecs` picks the first `*status*` field then `flds[0]`; when that isn't a wfmc lifecycle, the
  board has no drop-zones — that's the case to turn into a heat map / lookup view.)
  ✅ DONE (2026-06-04, bim-ootb PR #124) — `_bestPivot()` picks a categorical/lookup column (FK/list/yesno or
  `*_ID`, cardinality ≥2 and < rows, not `flds[0]`); `_groupRecs` flags `hasStatus`; Kanban renders a heat map
  (tiles ∝ real counts) when no lifecycle. Witness `erp/tests/poc_heatmap.js` → **§HEATMAP PASS** (win=140
  Product → `§HEATMAP by=M_Product_Category_ID` 13 cells; win=167 C_Invoice → board, 0 heat cells). sw v574.

## ▶ GUARDRAILS
- Non-invent: every migrated row/amount is a RECORDED input from the source; absent → honest "coming", never faked.
- Witness-led: extend `poc_odoo_fold_live.js` per ERP; keep §ODOO-FOLD-LIVE PASS green; add §-witnesses for the
  dialog (detect→highlight→default→confirm). §-log first.
- Deploy = bim-ootb PR off `origin/main` (erp/ only; the other session shares the tree — never branch-hop dirty;
  use a worktree; realign local main after each merge). SW bump. EXPLICIT-GO is waived for this POC (user standing
  "deploy after test").

## ▶ SESSION CONTEXT (kanban arc shipped this session, all LIVE on bim-ootb)
PR #113 top-buttons→registry · #114 Share context · #115 kanban drag→signed write · #117 kanban durable ·
#119 kanban in idempiere · #120 Graph⇆Kanban switch. Receipt R4 on BIMCompiler gh-pages (sw v9). Engine seam
+ `kanban_host.js` are the reusable write path. Backlog: `prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING`.

## ▶ SPEC — implemented 2026-06-04 (this section is the contract; §-witnesses below prove it)
**Module:** `bim-ootb/erp/erp_picker.js` → `window.ErpPicker.open({mode:'migrate'|'install', status})`.
A single dialog family (Install + Migrate both open it; mode only changes the headline + verb wording).

**S1 — list ALL five, always.** `iDempiere · Odoo · SAP · Oracle · MS Dynamics`, rendered every open
regardless of detection. Each is a card: icon · name · status badge.

**S2 — detect + highlight/grey (non-invent, best-effort).**
- Odoo: `fetch('http://localhost:8069/web/health', {mode:'no-cors'})` raced against a 1.2s timeout. Resolve
  ⇒ port reachable ⇒ **detected**. (no-cors = opaque liveness only; the browser still does NOT read Odoo
  data cross-origin — extraction is delegate-to-install. Mixed-content on https hosts ⇒ silently "not
  detected", honest.)
- iDempiere: Postgres :5432 is not browser-probeable (no TCP from JS) ⇒ never auto-detected; stays a
  **real, selectable** target reached via the ShowMe agent. (Honest: absence of a probe ≠ absence of the ERP.)
- SAP/Oracle/Dynamics: no adapter yet ⇒ **coming** (greyed, still clickable → honest "coming" message).
- Detected real cards are **highlighted** (green ring + "detected" badge); coming cards are **greyed**.

**S3 — default + ask.** Pre-select the detected ERP (else the first real one). Footer asks
*"Do you want to migrate your `<X>` data?"* with a primary **Migrate `<X>`** button; every other card stays
selectable (clicking re-targets the question).

**S4 — route on confirm.**
- `idempiere` → `window.MigrateShowMe.open()` (the existing real PG-agent flow). 
- `odoo` → the **Odoo sub-flow** (delegate-to-install, real): (1) download `odoo_agent.js` + show the run
  command; (2) file-input the produced `odoo_chain.json`; (3) the browser RE-FOLDS each hop through
  `window.ERPKernel` + the wfmc carried in the chain, asserting `mapped==events`, `newVerbs==[]`, invoice
  GL `ΣDr==ΣCr`, and a replay-hash verify. Honest fallback text if `ERPKernel` is absent.
- `sap`/`oracle`/`dynamics` → honest "coming" status; never a fabricated migration.

**S5 — install-side extractor:** `scripts/odoo_agent.js` (Node) re-pulls SO S00023's O2C chain LIVE from
the running odoodemo (the `poc_odoo_fold_live` extraction), folds via `odoo_adapter.buildEvents`, self-checks
(dispatch through the kernel, `newVerbs==[]`, GL balances), and EMITS `build/erp/odoo_chain.json`
`{meta, wfmc, KNOWN_VERBS, events:[{name,d,ops}], totals, gl}` — the artifact the browser loads. The browser
never CORSes into Odoo; the agent runs install-side and the chain file is the recorded bridge.

## ▶ WITNESSES (§-log first)
- `§ODOO-AGENT … events=5 newVerbs=[] gl Dr==Cr … wrote odoo_chain.json` (node, `build/erp/odoo_agent.log`)
- `§ERP-PICKER open mode=<m> erps=5`
- `§ERP-PICKER detect odoo=<Y|N> idempiere=agent`
- `§ERP-PICKER highlight=<key> greyed=[sap,oracle,dynamics]`
- `§ERP-PICKER default=<key>`
- `§ERP-PICKER confirm erp=<key> route=<route>` / `§ERP-PICKER coming erp=<key>`
- `§ODOO-MIGRATE-BROWSER loaded events=5 mapped=5/5 verbs=[…] newVerbs=[] glDr==glCr verify chainOk=Y`
