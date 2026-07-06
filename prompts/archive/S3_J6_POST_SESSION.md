# ⚠ DO NOT REMOVE — SESSION CARD: GRAND LANE S3 / J6 POST + REPORT (the GL to the cent)
# Scope: make iDempiere's **Posted REAL on its own surface** — after a signed Complete (J5), the document's GL
#   is DERIVABLE TO THE CENT and shown on iDempiere's OWN Posted affordance: the Accts-Posted / Posting-Preview
#   pills (already gated to posting docs by the `Posted` column, §AD-GATE) render the balanced fold (Dr==Cr) for
#   the LIT document, and every line reconciles to real iDempiere `fact_acct` to the cent. This is the THIRD leg
#   of the Complete → Create → Post journey (J5 ✅ #329, J4 ✅ #331). R→E→V, ONE bounded leg. Read-only on the
#   read side (no new write verb) — posting is a DERIVE/VALIDATE fold, not an ACT (Grand Lane §0 automation law).
# Read FIRST: prompts/GRAND_LANE_STRATEGY.md §0 doctrine + §3 S3 + prompts/ERP_CRITIC_UX_LANE.md (J6) +
#   docs/ERP_MODEL_ARCHETYPE.md (MOrder archetype + ~25 completeIt deltas) + docs/ERP_COVERAGE_MATRIX.md.
# Log Mandate: read the witness §-log before any conclusion — exit code is NOT evidence.
# Worktree: clean /tmp/wt-* off FRESH origin/main (J4 landed as #331; start the follow-up off origin/main, never
#   re-use a squash-merged branch). Edit bim-ootb ONLY via /tmp/wt-*. (Auto-merge can squash BEFORE a late push —
#   it orphaned the J4 cleanup once; verify the commit actually LANDED on main, and if a follow-up is needed start
#   it off fresh origin/main.)

## CONTEXT — what already exists (do NOT re-derive)
- The ENGINE is oracle-equivalent at the GL layer: `doc_poster.js` (per-doc GL derivation, == real iDempiere
  `fact_acct(318)` maxDiff=0c), `post_resolver.js` (token→account, FOLD-frozen), `erp_postings.js` (frozen
  read-fold, `window.ERPPostings`), `accts_posted.js` (the lens — buildCtx/buildPostedVM/mount), `erp_preview.js`
  (Posting-Preview seam `window.ERPPreview` — gate + derive off the loaded db). ad_seed.db carries `fact_acct`
  (300 rows, TB 46574.97) + complete posting config (§MIGRATE-POSTCFG, v653). Odoo 12-odoo.db = posting config
  to the cent vs account_move_line. preview_demo.db = the data-gated demo for Posting-Preview.
- idempiere.html ALREADY surfaces both pills, gated by §AD-GATE (`_isPostingDoc` = the table carries the AD
  `Posted` Button col): `openPostedFor()` (Accts-Posted) + the Posting-Preview lens (`window.ERPPreview.openPreview`).
  They light up on a db carrying posting config; honest `coverage:absent` otherwise. NO new verb.
- J5 made Complete REAL (signed CO survives reload, `_overlayDocTip`). J4 made New REAL (signed CRUD_CREATE
  appears + survives reload, `_overlayListTip`; `crud_overlay.js?v=13`).

## R — THE GAP TO VERIFY (live, before code) — diagnose with a witness FIRST
- After a signed Complete on iDempiere's own surface (J5), does the LIT document's **Posted** view derive the GL
  **to the cent**? PROVE on GardenWorld (resident `fact_acct`): open a CO order/invoice → the Accts-Posted /
  Posting-Preview pill → assert the fold is BALANCED (Dr==Cr) and each line == the oracle `fact_acct` row (or the
  in-app `doc_poster` derivation) to the cent. Then the HONEST gate: where a tenant's db has NO posting config
  (a thin PoC shard), the pill must say `coverage:absent` — never fake a posting. PROVE both arms before any fix.
- Likely finding (mirror J4/J5): the engine derives correctly headless; the live gap is the WIRING — e.g. the
  Posted pill reads the IMMUTABLE bundle's `Posted='N'`, not the op-log tip after a signed Complete, OR the
  newly-created (J4) order has no posting config row so the preview correctly gates absent. Diagnose, don't assume.

## E — THE BUILD (sourced; reuse the proven engines + the J5/J4 read-the-tip pattern — generalize, don't fork)
1. Read-the-tip for `Posted`: after a signed Complete, the document's Posted status should reflect the op-log
   (the same way `_overlayDocTip` folds DocStatus). If the Posted column rides the bundle only, generalize the
   read-the-tip overlay so the live Posted view is truth. (DERIVE/VALIDATE only — no ACT, no new write verb.)
2. Surface the balanced GL fold on iDempiere's own Posted affordance for the LIT doc via the EXISTING
   `window.AcctsPosted` / `window.ERPPreview` renderer — never a forked poster. Honest `coverage:absent` where data is thin.
3. Oracle-diff to the cent: each derived line == real iDempiere `fact_acct` (GardenWorld resident) / `doc_poster(318)`.

## V — WITNESS W-CRITIC-POST-LIVE (erp/tests/poc_critic_*_live.js, §-tagged, 0 pageerrors)
- ASSERT: a completed doc's Posted view derives the GL to the cent (Dr==Cr, each line == fact_acct/doc_poster, 0c);
  the Posted state reads-the-tip after a signed Complete (survives reload); a thin-data tenant gates `coverage:absent`
  honestly (no faked posting). Oracle-diff to the cent on GardenWorld's resident fact_acct.
- REGRESSIONS GREEN: recinfo / draft / blue / grid-batch / form-pill / gridstatus / **J5 process** / **J4 create**
  (poc_critic_create_live).
- SHIP: clean /tmp/wt-* off fresh origin/main · sw CACHE_VERSION bump (clean one-line marker — NO changelog file;
  the git commit message is the record, user 2026-06-16) · bump `crud_overlay.js?v=` in idempiere.html if it changed
  · auto-merge · VERIFY it LANDED on main (the squash-before-late-push trap).

## AFTER J6 → P2 (the 5 tenants end-to-end) → P3 (surpass + gating) → P3.5 (consolidated) → P4 (scorecard).

## STANDING (carry-over, already LIVE — do NOT rebuild)
- J5 PROCESS ✅ (#329, sw v694): Complete signed + persists + fan-out on iDempiere's own DocAction surfaces.
- J4 CREATE ✅ (#331, sw v695, crud_overlay.js?v=13, W-CRITIC-CREATE-LIVE 12/12): New opens the create form DIRECTLY
  (ring never fanned, doctrine §0) → `__crud.create`/`hostCreate`; the AD callout fills bill+pricelist
  (`fireCreateCallout`, header `CalloutOrder.bPartner` registered as HOST GLUE so the engine's
  `installDefaultHandlers` stays 6 — W-CALLOUT pin intact); DocumentNo = AD_Sequence preview (`_previewDocNo`)
  finalised by `_allocDocNo` on Save; mandatory MOrder beforeSave defaults (warehouse/doctype/currency/payterm)
  fill via session `_docCtx`; `_overlayListTip` folds CORE.listTip so the signed row APPEARS + survives reload,
  client/org-gated (W-CRITIC-GATING); `overlay:committed` CRUD arm refolds the grid. New pill re-pointed
  `formnew`→`_openCreate`→`__crud.create`. Engine files (ad_callout/ad_modelval/crud_ops) UNCHANGED.
- FULL CRUD ✅ (#337, sw v697, crud_overlay.js?v=14, W-CRITIC-CRUD-FULL-LIVE 10/10): Change + Delete now work on
  iDempiere's surface (host seams `__crud.update`/`__crud.remove`, no ring; `getRecord` op-log fallback so a saved
  synthetic-pk draft is editable; `listTip` reports `updated:[pk]`; formedit/formdel pills). New→change→delete = the
  basic flow, signed + reload-surviving. KNOWN: editing a CO/CL order silently no-ops (FSM should block+warn — refine).
- ⚠️ GENERAL-NOT-CUSTOM AUDIT (Janke AD vision — user principle): fold editability/defaults from AD, don't hand-list.
  Open: (1) `crud_ops.json` curated 5-table list → derive from `AD_Table.IsView`+`AD_Column.IsUpdateable`+
  `AD_Tab.IsReadOnly`+role (a proper S-card: "fold the CRUD spec from the AD" → every window editable per its dict);
  (2) `_docCtx` warehouse heuristic → `AD_OrgInfo.M_Warehouse_ID`; (3) `_allocDocNo` table seq → `C_DocType.DocNoSequence_ID`
  when IsDocNoControlled; (4) `MV_INSTALLER` hardcoded map → discover. `AD_Column.Callout` IS editable (signed re-fold).
- DOCTRINE (Grand Lane §0): visual CRUD ring = Glass/Gravity ONLY; iDempiere keeps its own surface, shares the
  signed engine. Automation law: DERIVE / VALIDATE (no op) · ACT (signed op). Posting = DERIVE/VALIDATE — read-only.
- NO changelog file anywhere (deleted internal/SW_CHANGELOG.md, stripped sw.js inline history — user 2026-06-16);
  the git commit message is the only record. Release notes only when production-ready.
