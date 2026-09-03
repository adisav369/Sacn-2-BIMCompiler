# ⚠ DO NOT REMOVE — Scope guard / handoff: §AD-BEHAVIOR (make the screen BEHAVE off the AD)
# Scope: wire the already-proven engines into the LIVE ERP screen so behaviour (not just structure) is AD-driven:
#        (1-3) fields show/hide/enable per logic, the menu is role-gated, process buttons dispatch; AND NOW
#        (4) a completed document shows its DERIVED GL and a rule-edit RE-FOLDS it on screen — the equivalence
#        lane's posting/doc-action fold made visible. This is the 🟡→✅ residual for the App-Coverage AND the
#        oracle-equivalence rows (docs/ERP_COVERAGE_MATRIX.md). Every 🟡 / "headless-only" verdict lifts to ✅
#        the moment the live screen drives it.
# Owner: the UI / pill-UI / front-end session (it holds the renderer + crud screen). The engine lane
#        (bim-compiler) is DONE and green headless — CONSUME it, do NOT rebuild or edit the engine modules:
#        ad_evaluator.js / ad_access.js / ad_process.js + crud_overlay.effectiveFlags (declarative behaviour) AND
#        scripts/erp_engine.js + scripts/post_resolver.js (the POSTING fold — completeIt/buildDoc/explodeBOM →
#        derived fact_acct, 12 surfaces oracle-equivalent maxDiff=0c, see WHAT IS DONE below).
# NON-NEGOTIABLE: spec-first; whitebox §-log for VALUE verification (READ the log before any conclusion);
#        Playwright ONLY for wiring (the element toggled / the window opened) per CLAUDE.md; non-invent (every
#        verdict traces to a real AD/fact_acct row, never a hand-set boolean); EXPLICIT GO before any deploy.
# Read first: docs/ERP_COVERAGE_MATRIX.md (the 🟡 rows + the 12 oracle-equivalent rows) · docs/ERP_MODEL_ARCHETYPE.md
#        (the posting scoreboard, 10 of ~40 + 1 recipe) · prompts/APP_COVERAGE_LANE.md (the declarative engine spec) ·
#        prompts/FOLD_MODEL_LOGIC.md (the posting-fold witnesses + handoff) · the sibling structure-proof
#        prompts/AD_RENDER_HANDOFF.md (its render twin). LIVE DATA: docker postgres (idempiere_test, client 11) is
#        up — `bash scripts/extract_fact_acct.sh` regenerates build/erp/glassbowl_data.db (real GardenWorld
#        order→ship→invoice→match→pay→allocate + inventory), the one source we own STRUCTURE *and* folded DATA for.

---

## ▶ WHAT IS DONE (engine lane — consume, do not touch)  — all witnessed headless, exit 0
- **`build/erp/ad_evaluator.js`** (W-LOGIC-EVAL, `scripts/poc_logic_eval.js`) — parses + evaluates the real
  iDempiere logic grammar; 3044/3044 boolean logic rows parse. Browser global **`window.AdEvaluator`**;
  `crud_overlay.effectiveFlags(field, record, context) → {visible, readonly, required}` already calls it
  (flat `f.readonly`/`f.required` kept as the no-logic fallback).
- **`build/erp/ad_access.js`** (W-ACCESS, `scripts/poc_access.js`) — `buildRole(db, roleId)` + window/process/
  form grant checks + AccessLevel/org scope, from real `ad_*_access` rows. Browser global **`window.AdAccess`**.
- **`build/erp/ad_process.js`** (W-PROC, `scripts/poc_proc.js`) — `ad_process`+`ad_process_para` → classname
  → JS handler registry → validate params → run; unregistered classname → explicit absent-handler. Browser
  global **`window.AdProcess`** (5 handlers registered; report procs fold via `report_overlay.js`).
- **`scripts/erp_engine.js` + `scripts/post_resolver.js`** (the POSTING fold — the equivalence lane, all
  oracle-witnessed vs real GardenWorld `fact_acct` client 11, `maxDiff=0c`). **PURE** verbs (host injects
  `query`/`bomOf`): `buildDoc(spec,parent,lines)` (the archetype create-verb — shipment/invoice/PO are spec rows)
  · `explodeBOM` (recursive backflush) · `qtyOnHand`/`movementSign` (the inventory spine) · `completeInvoice`.
  `post_resolver.resolve(db, token, masterId, schema)` maps a manifest token (`{BPartner.Receivable}`,
  `{Vendor.V_Liability}`, `{Product.InventoryClearing/Asset/AverageCostVariance}`, `{Tax.Due/Credit}`,
  `{Bank.*}`, `{BPGroup.*}`, `{CashBook.*}`) to the real account, reading ONLY master columns. **12 surfaces
  oracle-equivalent** (docs/ERP_COVERAGE_MATRIX.md): per-doc GL derivation · completeIt Order→Ship→Invoice chain ·
  Doc_Payment · Doc_AllocationHdr (+VAT tax-correction, +FX schema 200000) · StorageOnHand qty · ReplenishReport PO ·
  AP-invoice · M_Movement (inter-org) · M_MatchInv (incl. avg-cost IPV split). The whole trade+inventory loop folds.
  Witnesses: `scripts/poc_{fold_complete,money_post,alloc_post,alloc_fx,qtyonhand,replenish,invoice_complete,
  invoice_post_ap,movement,matchinv,post_harden,backflush}.js` → `build/erp/poc_*.log` (all exit 0).

## ▶ THE FOUR WIRINGS (what the UI must call — the engine already returns the verdict)
1. **Fields react to logic.** When the crud screen draws a field AND on every field change, call
   `window.crud_overlay? / effectiveFlags(field, record, context)` and apply the result to the DOM:
   `visible=false`→hide the row, `readonly=true`→disable, `required=true`→mark/validate. Replace the flat
   show-all/`f.readonly` path for fields that carry a `displaylogic`/`readonlylogic`/`mandatorylogic` string.
2. **Role gates the menu.** Build the session role context `{AD_Role_ID, orgs[], clients[]}` from the logged-in
   user once, then run menu→window→process→form visibility through `window.AdAccess` → a window/process the
   role has no grant for is **absent from the menu / denied on open** (not merely greyed). Record-org scope on
   open where applicable.
3. **Process buttons dispatch.** Route a process action through `window.AdProcess`: read its `ad_process_para`,
   prompt for params, validate, run the resolved handler, surface the result; an **unregistered classname →
   "not available"** (honest), never a silent no-op.
4. **Completion shows the derived GL (the equivalence fold, made visible).** When a document is Completed on
   screen (the DocAction → CO), call the posting fold and show the resulting `fact_acct` lines as a GL preview:
   `post_resolver.resolve` for each manifest token → DR/CR per (account, side). The numbers on screen ARE the
   `maxDiff=0c` oracle-equivalent values (the same the headless witnesses prove). THEN the grail
   (HolyGrail §RULE-EDIT, already proven on Odoo via `prompts/RULE_EDIT_ONE_GESTURE.md`): a signed edit to an
   account/manifest rule **RE-FOLDS** the GL on screen in one gesture — the on-screen reflow ERP_BACKEND_GAP.md
   names. Read-only preview first (lower risk); the editable re-fold is the headline.

## ▶ THE PROOF (these §-lines ARE the evidence; whitebox first, Playwright wiring-only)
- `§AD-LOGIC-LIVE field=<col> ctx@DocStatus DR→CO → visible/ro/req flips on the rendered DOM` — toggle one
  real context var on screen, a dependent field actually changes state. (Engine verdict via §-log; Playwright
  only asserts the element toggled.)
- `§AD-ACCESS-LIVE role=<n> window=<id> shown=<false> reason=no-grant` — log in as a restricted role; a window
  it lacks a grant for is not in the menu / is denied on open. The dual `…shown=true` for a granted one.
- `§AD-PROC-LIVE clicked AD_Process_ID=<n> classname=<X> dispatched=true rows=<n>` — a process button runs the
  registered handler + logs; an unregistered one → `dispatched=false reason=absent-handler` surfaced to the user.
- `§AD-POST-LIVE doc=<C_Invoice|…> id=<n> completed → GL lines=<n> ΣDr=ΣCr maxDiff=0c vs fact_acct` — Complete a
  real GardenWorld doc on screen; the rendered GL preview EQUALS the engine fold AND the oracle (cite the headless
  witness it matches). (Engine verdict via §-log; Playwright only asserts the preview rendered.)
- `§AD-REFOLD-LIVE doc=<n> rule=<token/acct> edit=<old→new> → GL re-derived, Δlines=<…> signed=true` — one signed
  rule edit re-folds the on-screen GL (the grail); the diff is the rule's effect, traced, reversible.

## ▶ DEFINITION OF DONE
The `§…-LIVE` lines GREEN, ZERO edits to the engine modules (consume only — if the UI needs a new engine
entry point, ASK the engine lane, don't fork it). Then **re-verdict the matrix rows 🟡→✅** in
docs/ERP_COVERAGE_MATRIX.md citing the live §-log:
- §AD-LOGIC/ACCESS/PROC-LIVE → the Display/ReadOnly/Mandatory-logic + 6 security rows + AD_Process row move from
  "proven headless" to "drives the live screen".
- §AD-POST-LIVE/§AD-REFOLD-LIVE → the **12 oracle-equivalent posting rows** move from "headless maxDiff=0c" to
  "drives the live GL on screen" — the equivalence work becomes user-visible, and the §RULE-EDIT grail (proven on
  Odoo) lands on our own folded GardenWorld data.
Headline moves off 0✅. Suggested order: start with §AD-POST-LIVE (read-only GL preview — highest payoff, lowest
risk, all 12 folds already proven) on a regenerated glassbowl seed, then §AD-REFOLD-LIVE (the editable grail),
then the declarative trio. EXPLICIT GO before deploy. Prompts are gitignored local work-drivers — nothing to commit.
