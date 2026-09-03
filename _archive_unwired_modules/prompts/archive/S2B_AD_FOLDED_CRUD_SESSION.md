# ⚠ DO NOT REMOVE — SESSION CARD: AD-FOLDED CRUD (general, not curated) — "change the app by editing the dictionary"
# Scope: retire `crud_ops.json`'s CURATED 5-table allow-list. FOLD a table's CRUD spec FROM THE AD so EVERY window
#   is editable per its OWN dictionary (Janke/Compiere vision, user principle 2026-06-16 "rules general not custom"):
#   fields from AD_Field/AD_Column · type/ref/mandatory/validation from AD_Column · read-only from AD_Table.IsView +
#   AD_Tab.IsReadOnly + AD_Column.IsUpdateable · plus role/window access. The SIGNED write path is unchanged
#   (hostCreate/hostUpdate/hostDelete → commitCrud → listTip overlay); only the SPEC SOURCE changes (data, not a hand-list).
#   R→E→V, ONE bounded leg. This is the thread behind "are all tables editable?" — today only 5 are; the AD already
#   carries everything to make it general (seed has IsView 1003 · IsUpdateable 26144 · IsReadOnly 1130, fully populated).
# ── ORIGINAL LANE (never lose it): this is a TRIBUTARY of the GRAND LANE / ERP CRITIC UX journey J1→J8
#   (`prompts/GRAND_LANE_STRATEGY.md` = the spine + doctrine; `prompts/ERP_CRITIC_UX_LANE.md` = the judged journey).
#   Spine state: S1/J5 PROCESS ✅ → S2/J4 CREATE ✅ (+ full CRUD #337) → **S3/J6 POST = the official NEXT spine leg**
#   → P2 (5 tenants) → P3 (surpass+gating) → P3.5 (consolidated) → P4 (scorecard). Do this generality fix, then
#   RETURN TO THE SPINE — every leg funnels back into the one judged J1→J8 journey, never a detour that strands it.
# Read FIRST: prompts/GRAND_LANE_STRATEGY.md §0 doctrine + §2 spine + §3 S-rows + prompts/ERP_CRITIC_UX_LANE.md +
#   docs/ERP_COVERAGE_MATRIX.md. The engine law: declarative model FOLDED + every rule EXPOSED; imperative body =
#   registered handler / .foldbundle plugin (never auto-imported).
# Log Mandate: read the witness §-log before any conclusion. Worktree: clean /tmp/wt-* off FRESH origin/main; edit
#   bim-ootb ONLY via /tmp/wt-*. Ship via auto-merge + VERIFY it LANDED (squash-before-late-push orphaned a commit twice).

## R — THE GAP TO VERIFY (live, before code) — witness FIRST
- Open a window whose table is NOT in crud_ops (e.g. C_BPartner / M_Product). Today: no New/Edit/Delete (read-only),
  because the form pills + hostCreate gate on the curated `crud_ops.json` (`_crudHas`/entryFor). PROVE this is the only
  blocker — the AD has the fields (AD_Field), updateability (AD_Column.IsUpdateable), and read-only flags to drive it.
- Confirm the editability rule iDempiere uses, to the cent: a VIEW (AD_Table.IsView='Y') is read-only; a tab with
  AD_Tab.IsReadOnly='Y' is read-only; a column with IsUpdateable='N' is display-only on edit (but settable on New).

## E — THE BUILD (sourced; FOLD the spec, don't hand-list)
1. `foldCrudSpec(table)` — derive the crud_ops-shaped entry from the AD at runtime: fields = AD_Field (isDisplayed,
   isKey) joined to AD_Column (columnName, AD_Reference→type, IsMandatory→required, IsUpdateable, ValRule, DefaultValue);
   verbs gated by AD_Table.IsView (view → read-only, no create/update/delete) + role access. Reuse the EXISTING field/type
   resolution the renderer already folds (ad_parser/ad_data) — do NOT re-derive. crud_ops.json becomes an OPTIONAL
   override (doc-policy/docPolicy fan-out for the 5 document tables stays), not the gate.
2. Wire hostCreate/hostUpdate/hostDelete + the form pills to the folded spec (fall back to crud_ops.json where present).
   The signed write path (commitCrud → listTip overlay → reload-survival) is UNCHANGED — only the spec source moves to AD.
3. While here, close the 4 "custom not general" gaps the audit named (each one-liner, AD-sourced):
   (a) `_docCtx` default warehouse → read `AD_OrgInfo.M_Warehouse_ID` (NOT "lowest org wh id").
   (b) `_allocDocNo` → honor `C_DocType.DocNoSequence_ID` when `IsDocNoControlled='Y'` (else table seq).
   (c) `MV_INSTALLER` 4-table map → discover model-validators from the registry/AD, not a hardcoded switch.

## V — WITNESS W-AD-FOLDED-CRUD-LIVE (erp/tests/poc_*_live.js, §-tagged, 0 pageerrors)
- ASSERT: a window NOT previously in crud_ops (e.g. C_BPartner) now supports New + Edit + Delete on iDempiere's surface,
  each ONE signed op + listTip overlay + survives reload; a VIEW table is correctly read-only (no New/Edit); an
  IsUpdateable='N' field renders display-only on edit. Oracle-diff a created/edited row's persisted fields vs real
  iDempiere (idempiere_test) where derivable. The 5 document tables (c_order…) keep their docPolicy fan-out unchanged.
- REGRESSIONS GREEN: crud-full / create / process(J5) / form-pill / grid-batch / gridstatus / recinfo / draft / blue.
- SHIP: clean /tmp/wt-* off fresh origin/main · sw CACHE_VERSION bump (clean one-line, NO changelog) · bump
  crud_overlay.js?v= / idmp_pills.js?v= / pills_idmp.json?v= if changed · auto-merge · VERIFY it LANDED on main.

## STANDING (already LIVE — do NOT rebuild)
- FULL CRUD ✅ (#337, sw v697, crud_overlay.js?v=14, W-CRITIC-CRUD-FULL-LIVE 10/10): Create/Read/Update/Delete on
  iDempiere's surface, no ring (host seams __crud.create/update/remove; getRecord op-log fallback for synthetic-pk
  drafts; listTip updated:[pk]; formedit/formdel pills). J4 CREATE #331 sw v695. J5 PROCESS #329 sw v694.
- DOCTRINE (Grand Lane §0): visual CRUD ring = Glass/Gravity ONLY; iDempiere keeps its own surface, shares the signed
  engine. Automation law: DERIVE/VALIDATE (no op) · ACT (signed op). NO changelog file — git commit message is the record.
- Spine alternative: S3/J6 POST (prompts/S3_J6_POST_SESSION.md) is the official next spine leg (GL to the cent). This
  AD-folded-CRUD card is the "general not custom" tributary the user prioritised; do EITHER, then continue the spine.

## §SPEC (this leg — authored 2026-06-17, spec-first)
GOAL: a window whose table is NOT in `crud_ops.json` (e.g. C_BPartner, M_Product) becomes New/Edit/Delete-capable
per its OWN dictionary, with the SIGNED write path unchanged. crud_ops.json becomes an OPTIONAL OVERRIDE (the 5
document tables keep their docPolicy fan-out + ownerGate + docAction), not the gate.

ROOT-CAUSE (verified in live code): the ONLY blocker is `crud_overlay.js entryFor(key)` returning null for a
non-curated table → `hostCreate/hostUpdate/hostDelete` log "skipped (not in crud_ops)". The form pills
(formnew/formedit/formdel) ALREADY surface for any form-view record (IdmpPillFormGate); the write lane
(saveForm→commitCrud→listTip→_overlayListTip→reload-survival) is ALREADY table-generic (getRecord keys on
`<table>_id`). So the fix = give entryFor a folded fallback. NO change to the write lane.

DESIGN:
1. ENGINE (crud_overlay.js, testable headless) — `CORE.foldCrudSpec(adFields, opts)` PURE: maps the renderer's
   AD-folded field shape (ADParser.getFields: columnName, name, isMandatory, isReadOnly, isUpdateable, isKey,
   isDisplayed, referenceType, defaultValue, displayLogic) → a crud_ops-shaped entry. mapRefType: integer/amount/
   number/quantity→number · date/datetime→date · tableDirect/table/search→fk (ref=columnName minus _id) ·
   list/yesno/string/text→string (LEG-1 fallback: generic list options are NOT in __meta.docStatus, so render the
   raw value in a text input — editable + truth-bound; AD_Ref_List option-fold is a follow-on). fields filter =
   isDisplayed && !isKey && type∉{button,id}. readonly = isReadOnly || (forVerb==='update' && isUpdateable===false)
   → IsUpdateable='N' is SETTABLE on New, display-only on Edit (iDempiere rule). verbs = (isView||tabReadOnly) ? []
   : ['create','update','delete'] — NOTE 'process' is NOT folded here (DocAction stays FSM/DOC_FAMILY-gated; a
   folded CRUD table is not auto-processable — see Black Book note below).
   + `FOLDED` store + `entryFor` fallback (STORE curated wins; else FOLDED). Seams: `__crud.registerFolded(key,e)`,
   `__crud.ensureStore(cb)`, `__crud.hasEntry(key)`.
2. RENDERER (ad_parser.js) — add `c.IsUpdateable` to getFields SELECT + map `isUpdateable: o.IsUpdateable!=='N'`.
3. HOST (idempiere.html) — `_tableIsView(tn)` (AD_Table.IsView, cached); `_foldCrudSpecForTab(tab,forVerb)` calls
   CORE.foldCrudSpec over `_curTab().fields` + {isView, tab.isReadOnly} and registers it; `_foldableTab`/
   `_curatedHas`; `_crudHas`=curated OR foldable. `_openCreate/_openEdit/_openDelete/_openCrudRing` ensureStore →
   if !curated register the folded (verb-shaped) spec → then invoke the existing host seam. Nothing else changes.

AUDIT GAPS (same fold-from-AD build): (b) `_docCtx` warehouse from `AD_OrgInfo.M_Warehouse_ID` (then org/client
fallback) not "lowest org wh id"; (c) `_allocDocNo`/`_previewDocNo` honour `C_DocType.DocNoSequence_ID` when the
doctype IsDocNoControlled='Y' (else the table seq); (d) `MV_INSTALLER` discovered from the AdModelVal registry
(install* method names) not a hardcoded 4-table switch.

BLACK BOOK note (dev doctrine, user Q 2026-06-17): detect-from-AD is GENERAL (CRUD folds entirely from the dict);
EXECUTE is gated to ported families for Process — the legal action SET is portable (getValidActions) but the action
CONSEQUENCES (completeIt fan-out, voidIt) are model-specific, EXTRACTED never invented (DOC_FAMILY whitelist;
legalActionsFor THROWS on un-walked tables). So CRUD generalises; Process does not. An un-ported doc table shows
Process honestly-absent. C_Project had no Process because MProject is not a DocAction model (no DocStatus column).

WITNESS W-AD-FOLDED-CRUD-LIVE: (1) headless — CORE.foldCrudSpec over real C_BPartner AD rows yields create/update/
delete verbs + fields, a VIEW table yields []; (2) live — C_BPartner New+Edit+Delete each ONE signed op +
_overlayListTip + survives reload; a VIEW table read-only; an IsUpdateable='N' field display-only on edit. Keep the
5 document tables' docPolicy fan-out unchanged. Regressions GREEN: crud-full/create/process/form-pill/grid-batch.
