# ⚠ DO NOT REMOVE — Scope guard / resume card (specs + strategy review)
# Scope: continue the SPECS and run the STRATEGY REVIEW for the ERP arc:
#        fold (done) → render the fold (AD-gen, in UI session) → installer (ERPMaker, gated on render proof).
# NON-NEGOTIABLE (every turn): spec-first (no code before a written spec section); non-invent / EXTRACT
#        (every value traces to a source — NO synthesised ERP rows, NO Date.now/Math.random); witness-led
#        (each test NAMES the issue it proves); §-log first (READ the log before any conclusion); CLEAN-ROOM
#        for foreign ERPs (learn behaviour from output, never copy closed source); EXPLICIT GO before deploy.
# Read first: PROGRESS.md §migration · docs/HolyGrail.md §migration (+ §boundary) · docs/AD_GEN_FROM_DICTIONARY_SPEC.md
#        · docs/ERPMaker.md (installer) · docs/IDEMPIERE_RENDERER_SPEC.md · scripts/sap_adapter.js · prompts/MIGRATION_CAMPAIGN_RESUME.md.

---

## ▶ WHERE WE ARE (2026-06-03)
- **Odoo migration CLOSED** — 6 chains fold, `newVerbs=[]`, ONE engine change all campaign (f8 partial-qty
  matcher); f2 partial-payment + f1 account-DERIVATION both PASS. Witness `build/erp/odoo_fold.log`.
- **SAP skeleton + flight second-source, GATED** — `scripts/sap_adapter.js` (schema/state HYPOTHESIS, TO-VERIFY)
  + `poc_sap_fold.js` / `poc_sap_flight_fold.js` stop at `§SAP-ORACLE unavailable` (no fabricated rows). The
  license-free `/DMO/` flight source folds the DOCUMENT half (3/6 verbs) when a real export drops in.
- **AD-Gen spec WRITTEN, not built** — `docs/AD_GEN_FROM_DICTIONARY_SPEC.md` (T1–T5). Handed to the UI session:
  generate AD (Table/Column/Window/Tab/Field/Menu/TreeNodeMM) FROM the adapter dictionary → existing renderer
  shows SAP's tables, metadata-only (no transaction oracle needed). T1 started: VBAK/VBAP got typed `columns[]`.

## ▶ SPECS TO CONTINUE (each its own bounded session; spec-first)
1. **AD-Gen render proof (in flight, UI session):** build `genAD(SCHEMA_MAP)` + loader → separate `sap_ad_seed`
   → render in `idempiere.html`. Gate: `§AD-GEN` (handAuthored=0, counts from the dictionary, untyped=K) +
   `§AD-RENDER` (tree/window/grid draw, zero renderer edits). Finish typing `columns[]` for the rest of SCHEMA_MAP.
2. **Installer spec (ERPMaker emit/sign) — GATED on #1:** once AD-gen renders, write the offline-signed-HTML
   section in `docs/ERPMaker.md` (fold → genAD → emit self-contained app). Do NOT build emit before #1 is green.
3. **SAP transaction-oracle hunt (unblocks the FI half):** obtain a real SD+FI standard export (S/4HANA trial /
   GBI / IDES) → `build/erp/sap_oracle.json` → fold POST/ALLOCATE/MATCH + the ACDOCA-as-fold claim. Until then SAP
   stays SPEC. (Flight `/DMO/` export unblocks only the document half.)
4. **Optional Odoo:** multi-currency / anglo-saxon COGS — each its own oracle + witness; likely a POST with more
   resolved lines, not a new verb — but PROVE it.

## ▶ STRATEGY REVIEW (the honest questions to revisit, not re-answer blindly)
- **The claim:** we have a proven, repeatable migration METHOD (iDempiere built-for + Odoo fair-external, both
  `newVerbs=[]`) — NOT "any ERP migrates." Boundary (HolyGrail §): "eat any instance" is earned one diff-oracle
  at a time; SAP standard is the next gate, SAP custom the one after. Keep that line exact; do not let it drift.
- **The two halves:** migration removes the barrier-to-LEAVE; the grail (editable rules, live) is the reason-to-
  LAND; the installer (ERPMaker, zero-install signed HTML) is the adoption WEDGE. Review whether the AD-gen→
  installer path is the strongest first visible proof, or whether a transaction-oracle fold should lead.
- **Honest framing of "interoperability":** dictionary/structure shows now (metadata); transaction data waits for
  a real sample. Say which half a demo proves, every time. No hype (see memory feedback_no_hype / erp_perf_claims).

## ▶ CLOSE-OUT STATE
- Committed on `full`: `4042fe85` (Odoo) · `c2521d0d` (SAP skeleton) · `6ceddf76` (flight) · `20d87c5e` (AD-gen spec).
- Uncommitted (UI session owns): `scripts/sap_adapter.js` typed `columns[]` (spec §1/T1 start).
- Nothing pushed, nothing deployed. Odoo containers may still be running (`docker stop odoo odoo-db` to idle).
