# ERP Backend — Separation Design Spec (§0 of `prompts/ERP_BACKEND_GAP.md`)

**Status:** REVIEW + DESIGN deliverable, written BEFORE any Track-A lane implementation.
**Scope:** the behavioural-coverage backend arc (A-1…A-6) + the seams between concerns. UI bridge stays PARKED.
**Prime rule (inherited):** EXTRACT, DON'T INVENT. Every rule comes from `build/erp/ad_full.db` (927 tables,
lowercase) or the iDempiere checkout `~/idempiere-dev-setup/idempiere`. This spec maps *where* each rule lives and
*which module* interprets it — it never hand-authors a rule the AD already defines.

**Reads:** [ERP_COVERAGE_MATRIX.md](ERP_COVERAGE_MATRIX.md) (the scoreboard) · [HolyGrail.md](HolyGrail.md)
(DocAction corpus) · [DistributedERP.md](DistributedERP.md) §8 (accounting = the reconciliation engine) ·
[CONCURRENT_LANES_ROADMAP.md](CONCURRENT_LANES_ROADMAP.md) (overlay-aspect model).

---

## 1 · The invariant: three layers that must NEVER merge

This is `CLAUDE.md` **"Three Concerns never merge"** applied to the backend engine. Every Track-A concern is built
as a layer-2 module and is FORBIDDEN from reaching sideways into another layer-2 module.

| Layer | What it is | Where it lives | Determinism contract |
|---|---|---|---|
| **1 · DECLARATION** | the AD data — *what the rule IS* | `ad_full.db` (read-only input) | never hard-coded; re-query to refresh |
| **2 · INTERPRETATION** | one engine module per concern — *how the rule is evaluated* | `build/erp/ad_*.js` | pure `(record, ctx, db) → verdict/effect`; **no side-channel to a sibling module** |
| **3 · LOG / FOLD** | where state is derived | `kernel_ops.js` + `erp_period_close.foldBalances` | the ONLY place effects land; integer cents; no `Date.now`/`Math.random` on any fold/replay path |

**The compose rule:** layer-2 modules are pure functions. They are COMPOSED by a *caller* (the `crud_overlay`
field path, or the doc-action path), never by each other. The caller is the only code that knows about more than one
concern at a time. This is the shape already proven by the three DONE modules:

```
ad_evaluator.js   (record, ctx)        -> boolean         logic-expression verdict  (W-LOGIC-EVAL)
ad_access.js      (role, object, db)   -> {allow, mode}   security gate              (W-ACCESS)
ad_process.js     dispatch(db,info,ctx)-> ProcessResult   SvrProcess dispatch spine (W-PROC)
```

Each is a self-contained IIFE (`module.exports` for the node witness, `window.X` for the browser), no kernel
dependency, no import of a sibling layer-2 module. **Track A extends this set; it does not refactor it.**

---

## 2 · Concern map — one row per Track-A lane

For EACH concern: (a) AD source slice · (b) module boundary + the pure signature it exposes · (c) compose-seam (who
calls it, what it returns) · (d) **what it must NOT know about** (the anti-coupling rule).

### A-1 ⭐ Posting — per-document GL derivation  (`build/erp/ad_posting.js`, W-POST-DERIVE)
- **(a) AD source slice:** the acct-config tables (all present + populated in `ad_full.db`, verified 2026-06-09):
  `c_acctschema` (2) + `c_acctschema_element` (12) define the schema; `c_acctschema_default` (the client/org
  default for every accounting role — `c_receivable_acct`, `p_revenue_acct`, `t_due_acct`, `p_cogs_acct`, …);
  the per-entity OVERRIDE tables `m_product_acct` (110), `c_bp_customer_acct`/`c_bp_vendor_acct` (36/36),
  `c_tax_acct` (12), `c_bp_group_acct` (6), `m_product_category_acct` (28). **Every `*_acct` column holds a
  `c_validcombination_id`** → resolve via `c_validcombination.account_id` (157 combos) to the GL element
  (`c_elementvalue`, 379). This resolution chain IS the data; the engine ports the *mechanism* of walking it.
- **(b) module boundary + signature:** `ad_posting.js` exposes
  `derive(db, doc) -> { lines:[{account_id, amtacctdr, amtacctcr}], dr, cr, balanced }`.
  Internally a **DERIVATION TABLE** (data, like Lane-1's grammar): `doctype → [{role, side, basis}]` — e.g. AR
  Invoice = `[{C_Receivable, DR, grandtotal}, {P_Revenue, CR, lineNet}, {T_Due, CR, taxAmt}]`. A pure
  `resolveAccount(db, role, {schema, product?, bpartner?, tax?})` walks override→default and returns the
  `account_id`. Unported doc types return `{balanced:null, reason:'doctype-deferred'}` (NOT a silent 0-line fold).
- **(c) compose-seam:** the **doc-action path** calls `ad_posting.derive` when an action would post (COMPLETE/POST),
  then hands the balanced lines to **layer 3** (`foldBalances`, which already accepts `{lines:[…]}` double-entry
  shape — see `erp_period_close.js:_isPosting`/lines branch). `derive` returns DATA; it never folds, never writes.
- **(d) MUST NOT know about:** workflow/routing (it derives lines for a doc that *is being* completed; it does not
  decide *whether* completion is allowed — that is A-6/A-5). It must not call `ad_access`, `ad_evaluator`, or the
  kernel. **Pinned seam ↓ (§3).**

### A-2 · Val-rule / AD_Rule interpreter  (extends `build/erp/ad_evaluator.js`, W-VALRULE)
- **(a) AD source slice:** `ad_val_rule` (332, all Type S / SQL where-clause) + `ad_rule` (4, JSR-223 script).
- **(b) module boundary + signature:** a NEW sibling `evalValRule(db, valRuleId, {record, ctx}) -> {sql, rows, ok}`
  that substitutes `@context@`/`@Field@` tokens into the SQL where-clause and runs it for list/FK filtering.
  Lives next to `ad_evaluator` (boolean logic) but is a distinct surface — SQL-validation ≠ boolean display-logic.
  `ad_rule` Groovy/JSR-223 scripts: named-deferred (no JS interpreter for Groovy) — explicit, not silent.
- **(c) compose-seam:** called by the field/lookup path to filter a list or FK candidate set.
- **(d) MUST NOT know about:** posting, doc-actions. It validates/filters a value; it does not derive one (that is
  the callout's job — **pinned seam ↓ §3**).

### A-3 · Callout dispatch spine  (`build/erp/ad_callout.js`, W-CALLOUT — the W-PROC pattern)
- **(a) AD source slice:** `ad_column.callout` (284 cols carry a callout / 148 distinct classes).
- **(b) module boundary + signature:** mirror `ad_process.js` exactly — a `REGISTRY` of `classname → handler`,
  `dispatch(db, {table, column, record, ctx}) -> {fired, derived:{…}}`; start with 2–3 REAL handlers (a
  price/qty callout, a bpartner callout); unregistered classname → explicit absent-handler.
- **(c) compose-seam:** the field-change path fires the callout AFTER a value changes; the returned `derived` map
  is applied to sibling fields by the caller.
- **(d) MUST NOT know about:** validation (A-2). A callout DERIVES a value; a val-rule VALIDATES a value.
  **Pinned seam ↓ §3.**

### A-4 · Model-validator timing hooks  (`build/erp/ad_modelval.js`, W-MODELVAL)
- **(a) AD source slice:** `ad_modelvalidator` (3 registered) + the 213 `beforeSave`/109 `afterSave` Java overrides.
- **(b) module boundary + signature:** a timing-hook engine `fireHooks(timing, {table, record, ctx}) -> {ok, blocked?}`
  for `BEFORE/AFTER × NEW/SAVE/COMPLETE`. A registry of validators keyed by table+timing.
- **(c) compose-seam:** the doc-action path calls `fireHooks('BEFORE_COMPLETE', …)` around the transition; a
  blocking hook aborts before A-1 posting runs.
- **(d) MUST NOT know about:** the GL derivation table (it gates the action; it does not post). It may BLOCK a
  completion, but the posting fold (A-1) is downstream and ignorant of why.

### A-5 · C_DocType FSM beyond CO  (extends the doc-action path, W-DOCFSM)
- **(a) AD source slice:** `c_doctype` (52) × DocAction list (14, AD_Reference 135) × DocStatus list (12, ref 131).
- **(b) module boundary + signature:** the legal-action/transition table PER `c_doctype` →
  `legalActions(db, doctypeId, fromStatus) -> [actions]` + `transition(action, fromStatus) -> toStatus`.
- **(c) compose-seam:** the doc-action path consults it to gate which actions are offered/allowed; reuses the
  existing reversal-family handlers (VO/RE/CL/RA).
- **(d) MUST NOT know about:** posting amounts, workflow routing. It is a pure status machine.

### A-6 · Workflow engine  (`build/erp/ad_workflow.js`, W-WF — sequence LAST; gates §H-10 SoD)
- **(a) AD source slice:** `ad_workflow` (58), `ad_wf_node` (262), `ad_wf_nodenext` (207), `ad_wf_responsible` (2).
- **(b) module boundary + signature:** `walk(db, wfId, {record, ctx}) -> {node, next, activity}` — node-walk +
  activity creation + approval routing. Mechanism + a 1–2 workflow sample; rest named-deferred.
- **(c) compose-seam:** triggered by a doc-action (e.g. a CO that requires approval routes instead of completing).
- **(d) MUST NOT know about:** GL derivation. **Pinned seam ↓ §3** — completing a doc may *trigger* routing, but
  the GL fold (A-1) must stay ignorant of WF.

---

## 3 · The two risk seams — PINNED before any code

> A clean seam map is worth more than a fast first lane (§0 directive).

### Seam ❶ — **posting ↔ workflow**  (A-1 ↔ A-6)
**The risk:** completing a document can (a) trigger approval routing *and* (b) post the GL. If the posting fold
learns about workflow state, the reconciliation engine stops being deterministic-from-data.
**The pin:**
- A-6 (workflow) decides **whether** a doc reaches COMPLETE. It runs FIRST, on the doc-action path.
- A-1 (posting) derives GL lines **given** a doc that *is* completing. It is called only AFTER the action is
  admitted, and it reads ONLY the document + acct-config — never a WF node, activity, or approval state.
- The two never call each other. The doc-action path is the sole composer: `admit?(A-6/A-5/A-4) → derive(A-1) →
  fold(layer 3)`. The GL fold is byte-identical whether the doc was auto-completed or approval-routed.

### Seam ❷ — **callout ↔ val-rule**  (A-3 ↔ A-2)
**The risk:** both fire on a field change; conflating them produces an engine that can't say whether it changed a
value or merely checked one.
**The pin — one verb each:**
- **Callout (A-3) DERIVES** — given a field change, computes *new values* for sibling fields (`derived:{…}`). Write.
- **Val-rule (A-2) VALIDATES/FILTERS** — given a field + context, decides which values are *legal* (a where-clause
  over a candidate set). Read.
- Ordering on the field-change path: callout derives → then val-rule validates the (possibly derived) value. They
  share the record+ctx but never the other's output channel; A-3 returns a value map, A-2 returns a boolean/row-set.

---

## 4 · Implementation order (against this spec)

`A-1` (posting, thesis-critical) → `A-2` (val-rule, cheap, extends ad_evaluator) → `A-3` (callout spine) →
`A-4` (model-val) → `A-5` (DocType FSM) → `A-6` (workflow). Track B §H-7/§H-8 in parallel as budget allows.
**No lane starts until its seam is pinned here.** Each lane closes by RE-VERDICTING its matrix rows (⛔→🟡) — the
ceiling this arc is 🟡 (headless-proven); ✅ needs the parked live UI.

## 5 · The anti-coupling test (CI-able later)

A grep that should stay empty: no `ad_posting.js` may `require`/reference `ad_workflow`/`ad_access`/`ad_evaluator`;
no `ad_callout.js` may reference `ad_val_rule` logic; the only cross-module names allowed inside a layer-2 module
are layer-1 (`db`) and layer-3 (`foldBalances`, and only A-1 touches that). If that grep ever hits, a seam leaked.
