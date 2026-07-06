# ⚠ DO NOT REMOVE — Scope guard / handoff: §AD-RENDER (the render proof)
# Scope: prove the iDempiere AD renderer (front-end lane) DRAWS a generated AD seed as a navigable
#        menu→window→tab→grid UI with the structure traced to the seed. This is the ONE cross-lane gate.
# Owner: the UI / front-end session (this is the lane that holds the renderer). The generator lane
#        (bim-compiler: gen_ad.js + the seed .db files) is DONE and green (§AD-GEN). Do NOT rebuild it.
# NON-NEGOTIABLE: spec-first; §-log first (READ the log before any conclusion); whitebox §-log for VALUE
#        verification, Playwright ONLY for wiring (scripts load / tree renders / a window opens) per CLAUDE.md;
#        non-invent (every field shown traces to an ad_field row — no hand-built screen); EXPLICIT GO before deploy.
# Read first: docs/AD_GEN_FROM_DICTIONARY_SPEC.md §2 (the contract) + §5 (the witnesses) ·
#        prompts/SPECS_AND_STRATEGY_RESUME.md (the arc) · this card.

---

## ▶ WHAT IS DONE (generator lane — do not touch, just consume)
- `scripts/gen_ad.js` — pure `genAD(spec) → {rows, counts}` + `writeSeed()` loader. All `§AD-GEN` GREEN.
- Seeds ready on disk (deploy/dev/):
  - **`sap_ad_seed.db`** — 14 tables / 90 cols / 14 windows / 17 menu nodes. FULL login+access scaffolding.
  - **`odoo_ad_seed.db`** — 8 tables / 8 windows / 10 menu nodes. FULL login+access scaffolding. (columns=0 — known gap.)
  - `glassbowl_ad_seed.db` — 13 tables / 721 cols (richest), BUT generated before the session tables were
    added → **lacks ad_client/ad_user/ad_role/ad_window_access**. Regenerate with current gen_ad.js before use.
  - `sampleerp_ad_seed.db` — Excel-provider demo (4/20).

## ▶ THE CONTRACT (what your UI must READ — pinned, from gen_ad.js writeSeed)
All lowercase table/column names. The renderer folds these exactly:
- `ad_menu (ad_menu_id, name, issummary, action, ad_window_id)` + `ad_treenodemm (node_id, parent_id, seqno)` → the tree.
- `ad_window (ad_window_id, name, windowtype)` → opened from a menu leaf (action='W').
- `ad_tab (ad_tab_id, ad_window_id, ad_table_id, tablevel, seqno, ...)` → tabs under a window.
- `ad_field (ad_field_id, ad_tab_id, ad_column_id, name, seqno, isdisplayed, ...)` → the displayed fields.
- `ad_column (ad_column_id, ad_table_id, columnname, name, ad_reference_id, fieldlength, iskey, isidentifier)`.
- `ad_reference` + `ad_ref_list` → resolve `ad_column.ad_reference_id` to a display type.
- Login scope: `ad_user`/`ad_role`/`ad_user_roles`/`ad_role_orgaccess`/`ad_window_access` (System user → Admin role
  → access over every generated window) so the existing session/menu-scope flow works with ZERO renderer change.

**If your new UI reads AD differently than this shape**, that is fine — but STATE the shape your UI expects in
your `# DONE` appendix so the generator lane can confirm `genAD` output matches it. One agreed schema, checked
once = the two lanes stay independent. Silent contract drift is the only way this gate fails.

## ▶ THE PROOF (spec §5 — these §-lines ARE the evidence; nothing else counts)
- `§AD-RENDER <ui> loaded sap_ad_seed → menu nodes=N windows openable=N grid columns=N` — the renderer draws the
  generated AD; counts come from the rendered DOM/state, compared to the seed's row counts.
- `§AD-RENDER round-trip SAP VBAK opens → fields shown == AD_Field count for that tab` — a real table renders as a
  faithful window; the on-screen field count EQUALS the ad_field rows for that tab. (Grids EMPTY — no oracle yet.
  Empty grids are HONEST, not a bug. STRUCTURE is what this proves.)
- Whitebox §-log carries the value verification. Playwright ONLY for wiring (scripts load, tree renders, a window
  opens) — NO Playwright value-verification (add a §-log line instead). Run audit_specs.js if you add Playwright.

## ▶ RECOMMENDED DEMO SOURCE (strategy, 2026-06-03)
For the render proof itself: load **sap_ad_seed.db** or **odoo_ad_seed.db** (full access scaffolding, known-good).
For the eventual data-rich front-door demo (STRUCTURE + real lifecycle data): regenerate `glassbowl_ad_seed.db`
with current gen_ad.js (iDempiere's own order→invoice→payment data — the one source we own structure AND data for).
SAP rides STRUCTURE-only with honest empty grids — it is the "and it generalizes" reach claim, NOT the front door.

## ▶ DEFINITION OF DONE
`§AD-RENDER` GREEN (both lines), zero renderer edits to the AD-fold path (or, if a new UI, the AD shape it reads
is stated and matches `genAD`). Update PROGRESS.md §migration with the witnessed state. Then the ERPMaker installer
emit step (docs/ERPMaker.md) is UNBLOCKED — that is the next spec, gated on this proof.
