# ⚠ DO NOT REMOVE — SCOPE & DISCIPLINE
**Scope:** Renderer #2 = render the iDempiere chrome (`bim-ootb/erp/idempiere.html`) over an **Odoo** model
with ZERO per-model chrome code, via the descriptor seam already shipped. Build an `odoo` descriptor whose
three facets match the contract, register it, and prove `?erp=odoo` drives the UNCHANGED chrome.
**NON-INVENT:** the Odoo catalog (menus/models/fields/records) is EXTRACTED from a real Odoo source
(`odoo_agent` introspection / `odoo_chain.json`), never hand-authored. Every rendered value traces to a pull.
**Read the log after EVERY run** — exit code is not evidence. Save `§`-tagged output, read it, then conclude.
**Spec-first:** name the witness BEFORE code. A test that can't fail proves nothing.
**Honour until DONE.**

---

## ▶ NEXT SESSION — HANDOVER (start HERE; this card is the task)
**This is the next item on `prompts/FRONTEND_LANE_MASTER.md §OUTSTANDING`.** Build **INCREMENT 2** below
(the `odoo` descriptor) — witness-first, then GO-gated deploy. Everything you need is in this card.

- **Session state (2026-06-14):** origin/main = bim-ootb #312, **erp sw v683**. Two §OUTSTANDING items shipped
  this session — do NOT redo: item 1 W-NINJA-EXPORT-LIVE (PR #309 sw v681), item 2 W-AD-SELFEDIT-LIVE (PR #312
  sw v683). Increment 1 of THIS card (the descriptor seam) is also already DONE/LIVE (PR #310 sw v682).
- **Deploy discipline:** localhost headless-chrome verify BEFORE deploy (serve the worktree, drive the real page —
  reuse the harness shape of `bim-ootb/erp/tests/poc_descriptor_seam.js` or the item-1/2 smokes); **EXPLICIT GO
  before any deploy**; deploy from a FRESH bim-ootb worktree off `origin/main` (#312), bump sw `CACHE_VERSION` +
  any changed file's `?v=`, PR + auto-merge, verify GH Pages live, run the live witness on the deployed bundle.
- **CONCURRENCY — don't collide:** the BIM→Project Order lane (`prompts/BIM_TO_PROJECT.md`) is ACTIVE (Task 0/A
  done, Task C engine done, NOT deployed) and touches the **viewer Find bar + ERP.db push** — a different surface.
  Don't edit its files; if its commits land on origin/main, `git fetch && merge` your worktree, don't redo. `sw.js`
  is the conflict magnet — keep BOTH precache additions, take the HIGHER `CACHE_VERSION`.
- **Still ⛔ (parked, need a user fact — not this card):** ERP seam install-tier §3.3 · §P-11 payable-QR · G-3
  headless Adempiere NPE.

---

## WHERE WE ARE (increment 1 DONE + LIVE — do not re-derive)
- **The descriptor seam is built, witnessed, and LIVE on GH Pages** (PR #310 merged, erp sw v682, 2026-06-14).
- `bim-ootb/erp/erp_descriptor.js` = `window.ErpDescriptor` — a registry: `register(name,impl)` · `use(name)` ·
  `active` (live getter) · `list()` · `activeName()`. The FULL CONTRACT is in that file's header comment — READ IT.
- `idempiere.html` no longer names `window.ADParser/ADData/IdmpSession` directly. It does (≈ line 491):
  ```
  (function(){ try { var w = new URLSearchParams(location.search).get('erp'); if (w && window.ErpDescriptor) window.ErpDescriptor.use(w); } catch(e){} })();
  var _D = (window.ErpDescriptor && window.ErpDescriptor.active) || null;
  var ADP = _D ? _D.structure : window.ADParser, ADD = _D ? _D.data : window.ADData, SES = _D ? _D.session : window.IdmpSession;
  ```
  So selecting a descriptor (via `?erp=odoo` or `ErpDescriptor.use('odoo')` BEFORE that line runs) repoints the
  WHOLE chrome. All 24 original call sites (`ADP.*`/`ADD.*`/`SES.*`) are untouched.
- **AD is the first descriptor** (registered inside `erp_descriptor.js`): `{ id:'ad', structure:window.ADParser,
  data:window.ADData, session:window.IdmpSession }` — facets ARE the AD globals verbatim (behavior-identical).
- Witness `bim-ootb/erp/tests/poc_descriptor_seam.js` = **W-DESCRIPTOR-SEAM 7/7** (registered=ad, facets===AD
  globals identity, login→menu 294/332→window opens→12 records through the seam, 0 pageerrors). THIS IS THE
  REGRESSION GATE — the AD path must stay 7/7 after you add Odoo.

## THE CONTRACT (a descriptor = three facets; match these SHAPES exactly — they are the interface)
- **structure** (← AD: `ADParser`):
  - `init(db)` — prime caches; void.
  - `getMenuTree(db)` → `[ menuNode ]` where node = `{ name, action:'W'|'P'|'R'|'X', windowId?, processId?, formId?, children?:[…] }`.
  - `getWindow(db, windowId)` → `{ id, name, tabs:[ { name, tableName, tabLevel, isSingleRow, whereClause,
    orderByClause, fk, fields:[ { columnName, name, referenceType, isKey, isIdentifier, isMandatory, isReadOnly,
    isDisplayed, defaultValue, displayLogic, fieldLength } ] } ] }`. (See `ad_parser.js` `mapField`/`mapTab` ~L42–65 for the exact field/tab shape.)
- **data** (← AD: `ADData`):
  - `readRecords(db, tableName, where, orderBy)` → `[ rowObject ]` (plain `{col:value}`; the chrome reads cells case-tolerantly).
  - `resolveFK(db, columnName, value)` → a display label string (or the raw value when unresolved).
- **session** (← AD: `IdmpSession`):
  - `listUsers(db)`→`[{id,name,clientId,hasRoles}]` · `listClients(db)`→`[{id,name,users}]` ·
    `usersForClient(db,clientId)` · `rolesForUser(db,userId)`→`[{id,name,clientId}]` · `clientFor(db,roleId)`→`{id,name}` ·
    `orgsForRole(db,roleId)`→`[{id,name}]` · `buildContext(db,userId,{roleId,orgId})`→`{user,role,client,org,roles,orgs,winSet,procSet,formSet}`
    (`winSet/procSet/formSet` = `{id:true}` maps) · `scopeMenu(roots,winSet,procSet,formSet)`→`{tree,visible,total,…}` ·
    `deleteClient(db,clientId)`→`{ok,deleted,tables,protected?}`.
- The chrome calls `init` once per window-context, `getMenuTree` at login, `getWindow` on menu-click, `readRecords`
  per tab, `resolveFK` per FK cell, and the session methods through the login flow. Mirror the SHAPES, not the SQL.

## INCREMENT 2 — what to build
1. **Extract the Odoo catalog (NON-INVENT).** Odoo's dictionary = `ir.ui.menu` (menus), `ir.model` (windows/tables),
   `ir.model.fields` (fields), `ir.ui.view` (tab/form structure). Pull them via the agent in `bim-ootb/erp/odoo_agent/`
   (`agent.js` + `odoo_adapter.js`, live Odoo `localhost:8069` db `odoodemo`, same path that produced `odoo_chain.json`)
   → emit an `odoo_model.json` (the Odoo analog of `ad_seed.db`'s dictionary). `odoo_chain.json` ALREADY holds a folded
   SO→invoice→payment slice (`meta`/`wfmc`/`KNOWN_VERBS`/`events`) for the DATA facet — reuse it; pull more rows as needed.
   Honest framing on the page: "folded from odoodemo (Odoo 17) via odoo_agent" — selection, not a server.
2. **Write `bim-ootb/erp/odoo_descriptor.js`** — `ErpDescriptor.register('odoo', { id:'odoo', label:'Odoo', structure, data, session })`
   whose facets read `odoo_model.json` + `odoo_chain.json` and RETURN THE CONTRACT SHAPES above. Map Odoo→shape
   (e.g. `ir.model.fields.ttype` → `referenceType`; `ir.ui.menu` tree → menu nodes; a model's fields → a one-tab window).
   Start with a SMALL faithful slice (e.g. res.partner + sale.order) — prove the chrome renders it, then widen.
3. **Load it** in idempiere.html after `erp_descriptor.js`, precache it, bump sw. `?erp=odoo` is the entry point.
4. **Honesty floor (reuse AD's pattern):** a menu/model with no pulled rows renders "not in this fold", never fakes.

## WITNESS (name first) — W-ODOO-DESCRIPTOR (live, `bim-ootb/erp/tests/poc_odoo_descriptor.js`)
Boot `idempiere.html?erp=odoo` and assert, all `§`-logged:
1. `ErpDescriptor.activeName()==='odoo'`, `active.id==='odoo'`, `list()` includes both `ad` and `odoo`.
2. Odoo menu renders through the seam (`getMenuTree` → ≥1 real Odoo menu node, traced to `ir.ui.menu`).
3. Opening an Odoo "window" loads real records (`getWindow`+`readRecords` → rows>0 from the fold, e.g. the S00023 SO).
4. **Zero per-model chrome code** — the diff adds `odoo_descriptor.js` + `odoo_model.json` (+ a script tag + precache),
   and touches NO render function in idempiere.html (grep the diff: render/openWindow/renderActiveTab unchanged).
5. 0 pageerrors. **AND** the AD regression: `?erp=ad` (default) still passes W-DESCRIPTOR-SEAM 7/7.

## BEARINGS / DISCIPLINE
- **Branch hygiene:** `feat/erp-descriptor-seam` was SQUASH-MERGED — do NOT reuse it. Start a FRESH branch off
  `origin/main` (current tip ≈ `ce0f8ee`, but always `git fetch` first). Worktree under `/tmp/wt-*`, never `~/bim-ootb`.
- **Edit the shipping code in `bim-ootb/erp/`.** `erp_descriptor.js` lives there (UI-renderer seam). The Odoo agent
  lives in `bim-ootb/erp/odoo_agent/`. Mirror the engine source-of-truth rule only for files that have a `build/erp/` twin.
- **Run witnesses:** symlink `~/bim-ootb/tests/node_modules` into the worktree `erp/tests/node_modules`, run with
  `cwd=bim-ootb/erp`, `node tests/poc_*.js`. Whitebox `§`-log first; Playwright only for wiring/DOM presence.
- **The churn is real** (main advances every few min; `sw.js` CACHE_VERSION is the conflict magnet → take the HIGHER
  version, keep BOTH changelogs). Sync `git merge origin/main` → re-witness → push; enable `gh pr merge --auto --squash`;
  VERIFY it landed + deploy live (curl `red1oon.github.io/bim-ootb/erp/sw.js` version + the new file 200).
- **Spec:** `docs/IDEMPIERE_RENDERER_SPEC.md §6` (descriptor seam, increment 1 DONE; increment 2 NEXT). Pivot doctrine
  `docs/IDEMPIERE_2.md §pivot` ("one renderer, N dictionaries"). Memory `project_idempiere_renderer`.
- **After Odoo proves the thesis:** the same seam admits ERPNext / Glassbowl descriptors (the I4 "renderer registry"
  slots) — but ONE descriptor at a time, witnessed, no speculative N-consumer abstraction beyond what's proven.
