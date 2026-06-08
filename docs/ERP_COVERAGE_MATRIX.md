# ERP Rules & Processes — Coverage Matrix ("have we covered all the bases?")

**Companion to:** [Migrate & Compare (ERP)](MigrateComparisonPaper.md) (the §estimate buckets, now measured) ·
[The Holy Grail](HolyGrail.md#abstracting-the-docaction-corpus--and-why-it-is-the-migration-solvent) (the DocAction
corpus) · [iDempiere logic-admission model](IDEMPIERE_2.md#the-logic-admission-model--how-all-of-odooerpnextsap-logic-enters)
(the six layers this audit scores). This page is the exhaustive enumeration those three reference.

**Prompt:** `prompts/ERP_RULES_AND_PROCESSES.md` (§M-1 of `prompts/SERVERLESS_HARDENING_RESUME.md`).
**Method:** every surface enumerated in *both* homes — CODE (`~/idempiere-dev-setup/idempiere`, a live checkout) and
AD DATA (`build/erp/ad_full.db`, 927 tables, snapshot **2026-05-29**) — and mapped to our browser engine
(`build/erp/*.js`). Three legs per surface: **AD count** (real `sqlite3` query, cited) · **code home** (path + `wc -l`) ·
**engine handling** (file:fn or *absent*). Verdict: ✅ COVERED / 🟡 PARTIAL / ⛔ GAP. **No number is invented** — each
traces to a query or a `find … | wc -l`. Counts are a snapshot; re-run the cited query to refresh.

## Headline

| verdict | count | meaning |
|---|---|---|
| ✅ COVERED | **0** | no surface is fully interpreted *end-to-end into the live render*  |
| 🟡 PARTIAL | **19** | static demo slice **+ the AD logic-expression evaluator** (`ad_evaluator.js`, W-LOGIC-EVAL): the 7 Display/ReadOnly/MandatoryLogic surfaces now parse 100% (3044/3044 boolean rows) and evaluate correctly; wired into `crud_overlay.validateField`/`effectiveFlags` (headless-proven). Residual: live DOM show/hide/disable render-wiring (and tab-level GridTab render) not yet driven by it. |
| ⛔ GAP | **21** | the remaining behavioural / security surfaces are not interpreted at all |

**The honest answer:** *no — not all the bases are covered.* The engine demonstrates the **fold model** on a thin slice —
the `CO` DocAction transition, the double-entry posting fold, and a fixed receipt/TB/P&L report set — for a handful of
demo documents. The **irreducible behavioural surface** named in `MigrateComparisonPaper §estimate`
(process 54k LOC · workflow 7k · callouts 10k · validators · the logic-expression evaluator · the entire security layer)
is enumerated below and is overwhelmingly ⛔. This is what makes the *89× → ~30×* story precise: the 89× counts
delivery/definition, not behavioural parity — most behaviour is **named-and-deferred**, not folded.

> **Engine architecture fact (sets the ceiling):** all engine field-logic is descriptor-driven from a *hand-authored*
> `crud_ops.json` (5 tables — c_order, m_inout, c_invoice, c_payment, c_allocationline; ~23 fields) whose keys
> *mirror* the AD shape (`readonly=!IsUpdateable`, `required=IsMandatory`, …) but are **authored, not read from the AD
> DB**. No code path reads `AD_Column.Callout`, `AD_Val_Rule.Code`, `AD_Rule.Script`, `DisplayLogic`, `AccessLevel`,
> or any `*_Access` table at runtime. **UPDATE (W-LOGIC-EVAL):** there is now a logic-expression evaluator —
> `build/erp/ad_evaluator.js` (recursive-descent port of iDempiere `SimpleBoolean.g4` + `EvaluationVisitor`) — wired
> into `crud_overlay.js:effectiveFlags`/`validateField` via `window.AdEvaluator`. It reads `displaylogic`/`readonlylogic`/
> `mandatorylogic` and evaluates them against (record, context). Witness `scripts/poc_logic_eval.js` → `build/erp/poc_logic_eval.log`
> (`§LOGIC_COVERAGE evaluated=3044 of 3044 boolean-logic rows 100.00%`; §FALSIFIER verdict flips DR→CO). The other named
> surfaces (Callout/Val_Rule/Rule/AccessLevel/`*_Access`) remain unread.

## A · Rules/processes defined in CODE (Java)

| surface | AD count (query) | code home (path, LOC) | engine handling | verdict |
|---|---|---|---|---|
| **DocAction lifecycle** (prepareIt/completeIt/reverseCorrectIt/reverseAccrualIt/voidIt/closeIt/reActivateIt/unlockIt) | 31 classes match `public String completeIt` (`grep -rl`), ~28 real M* models | `DocAction.java` iface **319 LOC**; per-model methods in each M*.java | `crud_overlay.js:docActionOutcome` + `buildDocActionGroup` — folds **only CO** (DR→CO/IP via a field-presence `requires` check); `action` is pass-through metadata, no dispatch table | 🟡 PARTIAL |
| **Posting** (`Doc_*.java` GL fold + `org.compiere.acct`) | n/a (Java; see §B acct-config) | 20 `Doc_*.java` (**12,789 LOC**) + Doc/Fact/FactLine/DocLine in `org/compiere/acct` (**21,443 LOC** total, 35 files) | `erp_period_close.js:foldBalances` (DR−CR cents/account) + `erp_postings.js:readPostings` — sums **pre-folded** journal/oplog lines; **no per-document GL derivation** (the 20 Doc_* debit/credit rules) | 🟡 PARTIAL |
| **SvrProcess** (`org.compiere.process.*` / `org.idempiere.process.*`) | see §B AD_Process (476) | 220 files / **54,377 LOC** (198 + 22) | **absent** — no classname dispatch / process runner (`grep runProcess|executeProcess|svrprocess` → CSS hits only) | ⛔ GAP |
| **Workflow engine** (`org.compiere.wf` / `MWF*`) | see §B AD_Workflow (58) | 19 files / **7,366 LOC** | **absent** — no node-walk / activity / approval routing (`grep wf_node|MWFActivity|nextNode|approval` → 0) | ⛔ GAP |
| **Callouts** (`Callout*.java` / `org.adempiere.base.callout`) | 284 cols carry a Callout, 148 distinct classes | 56 files / **10,340 LOC** (+ pkg 10 files / 1,012) | `crud_overlay.js:validateField` — static type/min/max/regex/list shape only, descriptor-driven; **no callout class dispatched** | 🟡 PARTIAL |
| **Model Validators** (`*ModelValidator` + `ModelValidationEngine`) | `AD_ModelValidator` = 3 | 5 files / 1,165 LOC + `ModelValidationEngine.java` **1,048 LOC** | **absent** — no before/after-doc timing-hook engine; only a single DOC_ACTION→SET_STATUS op | ⛔ GAP |
| **Per-model invariants** (`beforeSave`/`afterSave` on M*) | n/a (Java overrides) | 213 `beforeSave` / 109 `afterSave` method defs across M*.java | `crud_overlay.js:validate` — generic field checks only; **no model-specific cross-field invariants / derived totals** | 🟡 PARTIAL |

## B · Rules/processes defined in the AD (DATA)

| surface | AD count (query) | code home | engine handling | verdict |
|---|---|---|---|---|
| **C_DocType FSM** | C_DocType=**52**; DocAction list=**14** (AD_Reference 135); DocStatus list=**12** (131) | `DocumentEngine.java` (action→method off C_DocType) | `crud_overlay.js` reaches **2 of 12** statuses (CO, IP); no per-C_DocType legal-action set / transition table | ⛔ GAP |
| **AD_Process** (+ Para / Classname / report) | **476** (Classname≠null **337**, IsReport=Y **119**); AD_Process_Para **1208** | the §A 54k-LOC home | `report_overlay.js` fixed `REPORT_MAP` folds receipt/TB/P&L for a hand-listed set; **no AD_Process row/para/classname read or run** | 🟡 PARTIAL |
| **AD_Workflow** (/ WF_Node / NodeNext / Responsible) | AD_Workflow **58**, WF_Node **262**, NodeNext **207**, Responsible **2** | `org.compiere.wf` (§A) | **absent** — no JS reads any AD_WF_* table | ⛔ GAP |
| **AD_Rule** (JSR-223 scripts) | **4** (all RuleType Q / SQL-accounting) | rule engine (Java) | **absent** — no JSR-223/`new Function`/eval interpreter anywhere | ⛔ GAP |
| **AD_Val_Rule** (SQL validation) | **332** (all Type S / SQL) | `MValRule` / lookup | **absent** — `valRule` strings in crud_ops.json are static labels only | ⛔ GAP |
| **AD_ModelValidator** | **3** (Libero MFG, Fixed Assets, Product Price) | `ModelValidationEngine` | **absent** | ⛔ GAP |
| **AD_Column · Callout** | **284** | `CalloutEngine.java` (338) | **absent** (comment "DELEGATED install-side" only) | ⛔ GAP |
| **AD_Column · DefaultValue** | **5647** | `MColumn`/`GridField.getDefault` | `defaultsFor()` — literal/`today`/`auto` only (19 entries); **SQL & `@var@` defaults absent** | 🟡 PARTIAL |
| **AD_Column · ReadOnlyLogic** | **289** | `Evaluator.java` (197) | **`ad_evaluator.js`+`crud_overlay.effectiveFlags`** — all 289 (129 distinct) parse + evaluate; flat `f.readonly` is now fallback (W-LOGIC-EVAL §LOGIC_COVERAGE). Residual: live input-disable render-wiring | 🟡 PARTIAL |
| **AD_Column · MandatoryLogic** | **29** | `Evaluator.java` | **`ad_evaluator.js`+`effectiveFlags`** — all 29 (28 distinct) parse+evaluate; drives `required` (flat `f.required` fallback). Residual: render * marker (W-LOGIC-EVAL) | 🟡 PARTIAL |
| **AD_Column · ValueFormat** (`vformat`) | **1** (negligible) | `DisplayType.java` | **absent** | ⛔ GAP |
| **AD_Column · IsUpdateable=N** | **14705** | `MColumn`/`GridField.setValue` | `validateField` blocks change via `readonly=!IsUpdateable` — **bool modeled, ~22 authored fields only** | 🟡 PARTIAL |
| **AD_Column · IsMandatory=Y** | **12577** | `GridField.isMandatory` | `validateField` 'required' via `required=IsMandatory` — **~22 fields only** | 🟡 PARTIAL |
| **AD_Field · DisplayLogic** | **2588** | `GridField.isDisplayed` + Evaluator | **`ad_evaluator.js`** — all 2588 (776 distinct) parse+evaluate; `effectiveFlags.visible` computed; validateField skips hidden. Residual: live DOM show/hide not yet wired (W-LOGIC-EVAL) | 🟡 PARTIAL |
| **AD_Field · ReadOnlyLogic** | **52** | Evaluator | **`ad_evaluator.js`+`effectiveFlags`** — all 52 (42 distinct) parse+evaluate (W-LOGIC-EVAL). Residual: render-wiring | 🟡 PARTIAL |
| **AD_Field · MandatoryLogic** | **14** | Evaluator | **`ad_evaluator.js`+`effectiveFlags`** — all 14 (8 distinct) parse+evaluate (W-LOGIC-EVAL). Residual: render-wiring | 🟡 PARTIAL |
| **AD_Field · DefaultValue** | **34** | `GridField.getDefault` | partial via `defaultsFor` (descriptor, not field-override) | 🟡 PARTIAL |
| **AD_Tab · WhereClause** | **85** | `GridTab.java` (3735) | **absent** — tab filter not applied | ⛔ GAP |
| **AD_Tab · OrderByClause** | **173** | `GridTab` | **absent** — engine uses ordinal sort | ⛔ GAP |
| **AD_Tab · ReadOnlyLogic** | **42** | `GridTab` + Evaluator | **`ad_evaluator.js`** — all 42 (4 distinct) parse+evaluate (W-LOGIC-EVAL §LOGIC_COVERAGE). Residual: tab-level GridTab render not wired | 🟡 PARTIAL |
| **AD_Tab · DisplayLogic** | **35** | `GridTab` + Evaluator | **`ad_evaluator.js`** — all 35 (25 distinct) parse+evaluate (W-LOGIC-EVAL). Residual: tab-level GridTab render not wired | 🟡 PARTIAL |
| **AD_Tab · IsInsertRecord=N** | **320** | `GridTab.isInsertRecord` | `verbs[]` gates create but **not AD-derived** | 🟡 PARTIAL |
| **AD_Reference** (header) | **606** (D=52, L=311, T=243) | `DisplayType`/`MLookup` | `f.type` tags drive validateField — **hand-set, not from AD_Reference** | 🟡 PARTIAL |
| **AD_Ref_List** (list valid.) | **1545** | `MLookup`/`Lookup` | `validateField` 'list' checks `store.__meta[f.ref]` — **~7 lists vs 1545** | 🟡 PARTIAL |
| **AD_Ref_Table** (table valid.) | **243** | `MLookup` (1337) | **absent** — 'fk' checks `isFinite(Number)` only, no FK-membership query | ⛔ GAP |
| **AD_EntityType** | **12** | `MEntityType` | **absent** — dictionary/customization scope unenforced | ⛔ GAP |
| **AccessLevel** (AD_Table) | **1076** (org/client/system bits) | `MRole`/`MTable` | **absent** | ⛔ GAP |
| **AD_Role** | **5** | `MRole.java` (3533) | **absent** (only a "role-gated" comment) | ⛔ GAP |
| **AD_Window_Access** | **1303** | `MRole.getWindowAccess` | **absent** | ⛔ GAP |
| **AD_Process_Access** | **1309** | `MRole.getProcessAccess` | **absent** | ⛔ GAP |
| **AD_Form_Access** | **145** | `MRole.getFormAccess` | **absent** | ⛔ GAP |
| **AD_Column_Access** | **0** (table empty) | `MRole.getColumnAccess` | **absent** (n/a in seed) | ⛔ GAP |
| **AD_Record_Access** | **0** (table empty) | `MRole.getRecordAccess` | **absent** (n/a in seed) | ⛔ GAP |

## Ranked GAP list (smallest witness that proves/disproves coverage — §-log first)

The gaps rank by **AD surface size × behavioural weight**. Each names the *smallest whitebox `§`-log* that would settle it
(per `docs/TestArchitecture.md` — a log line, not a Playwright test). For the ⛔ rows the `grep`-empty result *is* the
disproof: the line can never fire today.

1. **Logic-expression evaluator** — ✅ **BUILT (W-LOGIC-EVAL).** `build/erp/ad_evaluator.js` (port of iDempiere `SimpleBoolean.g4` + `EvaluationVisitor`) + `crud_overlay.effectiveFlags` wiring. Unblocks 7 rows (now 🟡): `§LOGIC_COVERAGE evaluated=3044 of 3044 boolean-logic rows (100.00%)` + 5 `@SQL=` rows named-deferred (separate `parseSQLLogic` surface); §FALSIFIER readonly flips rw→ro on `@Processed@` N→Y and display flips hide→show on `@DocStatus@!DR` DR→CO; 25 operator-class samples pass. Witness: `scripts/poc_logic_eval.js` → `build/erp/poc_logic_eval.log`. **Residual to ✅:** live DOM render-wiring (show/hide/disable) + tab-level GridTab render.
2. **Security layer** (Role/AccessLevel/4×_Access/EntityType — ~4,200 AD rows, the compliance story). Witness: `§ACCESS_DENY window=Order role=GardenUser` under a restricted role. No role/access symbol in any `build/erp/*.js`.
3. **SvrProcess runner** (476 AD_Process / 54k LOC). Witness: `§PROC run classname=<X> rows_affected=<n>`. No runner exists.
4. **Workflow engine** (58 wf / 262 nodes). Witness: `§WF node=<id> action=C next=<id> activity=created`. 0 grep hits.
5. **DocAction FSM beyond CO** (14 actions × 12 statuses). Witness: `§DOCTYPE_FSM {from,to,action,legalActions:<from C_DocType>}` — today `legalActions=undefined`, `to ∈ {CO,IP}`.
6. **Callout dispatch** (284 cols / 148 classes). Witness: `§CALLOUT fired col=c_bpartner_id derived={M_PriceList_ID,...}` on a BPartner change.
7. **AD_Val_Rule SQL** (332) & **AD_Rule** (4). Witness: `§VALRULE_EVAL id=<n> sql="..." rows=N`. No SQL/script interpreter.
8. **Per-document GL derivation** (the 20 Doc_*). Witness: `§POSTINGS source=fact_acct coverage=complete ΣDR=.. ΣCR=.. bal=0` against a record-keyed Fact_Acct extract — today `coverage=partial` (pre-folded lines only).
9. **Model-validator timing hooks** (BEFORE/AFTER COMPLETE). Witness: `§MODELVAL_HOOK timing=BEFORE_COMPLETE table=C_Order fired=1` — today `groupOps.len=1` (SET_STATUS only).
10. **FK table validation** (AD_Ref_Table, 243). Witness: `§FK_CHECK table=c_bpartner id=999 exists=false reject`.
11. **AD_Tab WhereClause/OrderBy** (85/173). Witness: `§TAB_FILTER where="IsActive='Y'" rows=N`.
12. **SQL/@var@ defaults** (subset of 5647). Witness: `§DEFAULT col=dateordered val=<resolved-from-@#Date@>`.

## Feed back to the conversion estimate

The real AD counts sharpen `MigrateComparisonPaper §"Realistic conversion estimate"`: the irreducible buckets are now
**enumerated, not asserted** — process **54,377 LOC** (476 AD_Process, 337 with a classname), Doc_* posting **12,789 LOC**
(20 doc types), callouts **10,340 LOC** (284 cols / 148 classes), workflow **7,366 LOC** (58 wf), validators **1,165 LOC**
(3 registered), plus a **~3,000-row logic-expression** surface and a **~4,200-row security** surface that have **no engine
home at all**. Folded today: the `CO` transition, the double-entry posting fold, a fixed report set — for ~5–7 demo tables.
This is the precise content behind "89× → ~30×": the 89× is delivery/definition; behavioural parity is a long, *named* tail.

## Provenance / caveats

- All §B numbers from `build/erp/ad_full.db` (927 tables) — table names are **lowercase** there (`ad_column`, not `AD_Column`).
- The shipping seed `~/bim-ootb/erp/ad_seed.db` (378 tables) is a **UI-metadata subset**: it has no `Callout` column and no
  `ad_rule`/`ad_val_rule`/`ad_modelvalidator` tables — so `ad_full.db` is the sole authoritative source for those surfaces.
  Where both carry a surface (C_DocType FSM 52/14/12, acct-config) they agree.
- `AD_Process` has **no `AD_Rule_ID` column** (report linkage is `jasperreport`/`ad_reportview_id`, 98 rows) — the prompt's
  "AD_Rule process link" does not exist as a column; counted the real columns instead.
- `AD_Column_Access`/`AD_Record_Access` exist but are **empty** in this DB (0 rows) — counted as 0; no engine code regardless.
