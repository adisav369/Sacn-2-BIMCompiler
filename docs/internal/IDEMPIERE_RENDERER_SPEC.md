# iDempiere Renderer (#1 of N) — spec

Implements `prompts/idempiereUI.md` task **I1**, under the user decision (2026-06-02):

> "iDempiere-classic chrome" — `idempiere.html` is a SEPARATE page, the recognizable iDempiere
> desktop look (left AD_Menu tree · window tab-strip · grid/form · toolbar · status bar). It is
> **launched from `erp.html`** via the `idempiere` pill → URL `idempiere.html`.
> "we follow exactly idempiere UI." Scope this session: **I1 only**.

**The POC framing (the user's words):** *"that is the poc challenge if SQLite WASM is up to it. Its
success will invite more strategy."* So the deliverable's burden of proof is: a browser page, **no
server, no iDempiere runtime**, folds the real Application Dictionary out of `ad_seed.db` (SQLite WASM)
and renders a faithful iDempiere UI. The §-witness must show the fold counts come from SQLite, not from
hand-authored data.

Spec-first; witness-led; §-log first (READ the log before any conclusion); deterministic / non-invent
(menu, window, tabs, fields are all AD rows — a fold, never a hardcoded screen). **EXPLICIT GO before any
deploy.** ERP code is edited in `bim-ootb/viewer/` directly.

Read first: `prompts/idempiereUI.md` (the renderer-contract thesis), `docs/PILL_MANIFEST_SPEC.md` (the
pill bar this mounts), `prompts/ERP_AD_UI.md` (the existing AD parser/renderer this REUSES — do not
duplicate), `bim-ootb/viewer/{ad_parser.js,ad_data.js,pill_builder.js,pills.json}`.

---

## §1 The two surfaces (the "pick your ERP" seam)

| surface | role | paradigm |
|---------|------|----------|
| `erp.html` | the **hub / engine view** — spatial data-globe (`ad_graph.js`) + the BIM pill bar | engine-as-data, renderer-neutral |
| `idempiere.html` | **renderer #1** — iDempiere-classic chrome over the SAME AD fold | tree → window → tab → grid/form |
| Odoo / ERPNext / Glassbowl | declared renderer **slots** (I4, unbuilt) | — |

`erp.html` keeps its current AD rendering; I1 only **adds the pill bar** and the launcher. The
`idempiere` pill (in `pills.json`, `nav:"idempiere.html"`, neutral `aplus.png` A+ mark — never the
trademarked logo, per `docs/IDEMPIERE_2.md` §Guardrails 3 / `prompts/IDEMPIERE_PILL_HANDOFF.md`) routes to
renderer #1. One engine, pluggable front-ends — the moat is the engine, the skins are thin folds.

## §2 Pill bar on `erp.html` (I1, registration layer — NOT a renderer rewrite)

- The manifest `bim-ootb/viewer/pills.json` (13 rows) is already written + witness-proven
  (`tests/test_pills_manifest.js` → §PILL-MANIFEST, ALL PASS, handAuthoredButtons=0).
- This session mounts it: a new module `erp_pills.js` fetches `pills.json`, resolves each icon, binds
  `fn`/`hold`/`nav` BY ID, and instantiates the existing `PillBuilder` (`pill_builder.js`, used as-is).
- **Icon source — non-invent.** Icons are the existing Lucide set that `panels.js` defines (`ICONS`).
  Because `panels.js` is a BIM-viewer module that cannot load standalone on `erp.html` (it references a
  global `A` at top level), the needed icons are carried in a small **data-only** module `icons.js`
  (`window.ICONS`), each SVG **copied verbatim** from `panels.js`. A parity test asserts no drift. No
  pill inlines hand-drawn SVG; a `@bim` pill adopts the icon `panels.js` binds for that id.
- The `@bim` id→icon map (verified against `panels.js`): `home`→home · `settings`→(inline gear) ·
  `find`→search · `share`→share · `tm`→clock · `help`→lifeBuoy.
- New ERP pill fns bind to REAL existing handlers where they exist (no behaviour change): `home`→
  `ADUI.showMenu`, `find`→search, `graphs`→`ADCharts`, nav pills (`idempiere`/`glassbowl`/`gravity`)→
  `location.href`. Pills whose surfaces land in later tasks (`ledger` I2, `edit`/`process` CRUD-P,
  drawers I3) bind to an honest "arrives in I2/I3" toast — never a fake screen.
- The existing `#bottom-nav` (rendered by `ad_ui.js`) is **kept** for I1, not hidden: its List / New /
  Charts / More handlers are not yet mirrored as pill `fn`s, so removing it would regress UX. The pill
  bar mounts on the right edge (vertical) and coexists. I1's "replace the §2 bottom-nav" clause is
  *completed* once those handlers land as pill fns (I2/I4). `ad_ui.js` is untouched, so the 153/153
  AD-UI baseline is protected.

> **Witness §PILL-MANIFEST** `page=erp pills=13 source=pills.json handAuthoredButtons=0 reusedBimIds=[home,settings,find,share,tm,help] newErpIds=[ledger,graphs,edit,process,idempiere,glassbowl,gravity] mountedButtons=13`
> — emitted by `erp_pills.js` at mount on the live page (counts the DOM buttons it actually built).

## §3 `idempiere.html` — renderer #1 (iDempiere-classic chrome)

Boots the SAME way `erp.html` hydrates (sql.js WASM + `ad_seed.db`, IndexedDB cache → network), then
renders classic chrome. **Nothing is hardcoded** — every visible string is a fold over AD rows.

Layout (faithful to the iDempiere ZK default theme — light, blue header):

```
┌───────────────────────────────────────────────────────────────┐
│ [logo] iDempiere   [ search… ]            client · role · ⌂ ?  │  header
├───────────┬───────────────────────────────────────────────────┤
│ ▾ Sales   │  C_Order  ×                                        │  window tab(s)
│   Order ● │  [⟲][＋][🖫][🗑]  ◀ Record 1 of N ▶   [Grid|Form]   │  toolbar
│   Invoice │ ┌─ Order ─ Order Line ─ … ───────────────────────┐ │  AD tab-strip
│ ▾ Partner │ │  (Form) Label  [value]   Label  [value]        │ │
│   B.P.    │ │  (Grid) ┌────┬────┬────┐                        │ │  grid/form body
│ ▾ Project │ │         │ …  │ …  │ …  │                        │ │
│   …       │ └────────────────────────────────────────────────┘ │
├───────────┴───────────────────────────────────────────────────┤
│ Record 1 of N · C_Order · folded from SQLite WASM (ad_seed.db) │  status bar
└───────────────────────────────────────────────────────────────┘
```

Components (I1 first cut — reads are real; writes are deferred to CRUD-P, clearly disabled):
1. **Header** — a neutral **A+** mark (CSS badge; the pill uses the `aplus.png` raster) + a global
   filter box that filters the menu tree. No trademarked logo anywhere.
2. **Left menu tree** — `ADParser.getMenuTree(db)`: summary nodes = collapsible folders, leaves = items
   carrying `action` (W/R/P/F) + `windowId`. Tap a `W` leaf → open the window.
3. **Window tab-strip** — each opened window is a ZK-style tab (close-able).
4. **Toolbar** — Refresh / New / Save / Delete / record-nav (◀ ▶ first/last) / Grid↔Form toggle. I1:
   Refresh, record-nav, Grid/Form toggle are wired; New/Save/Delete are present but disabled (a
   `title="arrives with CRUD-P"`), because Process/Edit writes land in later tasks. Toolbar glyphs that
   the Lucide set covers reuse it; the few classic glyphs it lacks use plain Unicode text (◀ ▶ ＋ ⟲ 🗑),
   not invented SVG art.
5. **AD tab-strip** — `ADParser.getWindow(db,id).tabs` by `seqNo`; header tab (TabLevel 0) first.
6. **Body** — Grid (`ADData.readRecords` → table of the tab's table, identifier columns) and Form
   (fields from `getFields`, rendered by `referenceType`, FK shown via `ADData.resolveFK`). Toggle
   between them like iDempiere.
7. **Status bar** — "Record i of N · {table} · folded from SQLite WASM".
8. **URL** — `?window=<id>` opens a window deep-link (parity with `erp.html`).

> **Witness §IDEMPIERE-FOLD** (headless, over `ad_seed.db` via sql.js — proves SQLite is the source)
> `menu groups=G leaves=L source=ad_menu · window=C_Order(143) tabs=T headerFields=F gridRows=R source=sqlite handAuthored=0`
> — `G/L/T/F/R` are counted from SQLite by the SAME `ADParser`/`ADData` calls `idempiere.html` makes;
> a non-zero menu + a non-empty window fold IS the proof "SQLite WASM is up to it."

## §3b The full original session experience — login → Role/Client/Org → scoped menu+data

The recognizable iDempiere on-ramp is the **login + context-selection** flow, then a **role-scoped** menu
and **client/org-scoped** data. All of it folds from real rows already in `ad_seed.db` (verified
2026-06-02) — non-invent:

| step | source (rows present) | renders |
|------|------------------------|---------|
| 1. Identity | `AD_User` (8: **System · SuperUser · GardenAdmin · GardenUser · Joe Sales · Carl Boss · Henry Seed · WebService**) | the login user list |
| 2. Role | `AD_User_Roles` (9 user→role) → `AD_Role` (4: GardenWorld Admin/User, …) | the Role dropdown for that user |
| 3. Client | `AD_Role.AD_Client_ID` → `AD_Client` (GardenWorld 11) | the Client (a role fixes its client) |
| 4. Org | `AD_Role_OrgAccess` (17) → `AD_Org` (9: HQ, Store Central, Furniture, Fertilizer, Store N/S/E/W, Stores) | the Org dropdown |
| 5. (Warehouse / Language / Date) | `M_Warehouse` / `AD_Language` / today | the rest of the login context |
| → **role-scoped menu** | `AD_Window_Access` (1080 role→window) | the AD_Menu tree filtered to what the role may open |
| → **client/org-scoped data** | `AD_Client_ID` / `AD_Org_ID` columns | every window's rows filtered to the chosen tenant/org |

Flow: **Login screen** (pick user) → **Role/Client/Org choose** screen (iDempiere's signature second step)
→ the header context bar shows `Client · Role · Org` (today static "GardenWorld · System Administrator" —
make it the live selection) → menu + data scope to it → a Logout / "change role" returns to step 2.

**Honest framing (mandatory — [[feedback_no_hype]], non-invent):** there is **no server**, so this is
**identity + context SELECTION, not password authentication** — the part of iDempiere's login that is a
*choice*, replicated faithfully; it is NOT a security gate and must never be presented as one (accept-any /
no password in the offline demo, clearly labelled). Seed reality to name, not paper over: only **client 11
GardenWorld** carries data (System/client 0 is metadata-only here); roles/orgs are GardenWorld's.

**Composes with the rest:** role-scope = an `AD_Window_Access` filter on the menu tree; client/org-scope =
a `WHERE AD_Client_ID/AD_Org_ID` layer on every read — the SAME filter mechanism as master-detail's parent
FK and the data-streaming tier select (`docs/IDEMPIERE_DATA_STREAMING_SPEC.md`). One filtering model.

> **Witness §IDEMPIERE-LOGIN** `user=GardenAdmin roles=N client=GardenWorld(11) org=HQ menu-visible=<w>/<all> source=ad_user/ad_role/ad_window_access handAuthored=0` — the session context + role-scoped menu count fold from the AD, no hand-authored identity.

This is a build item (a new I-task — login/session ahead of or alongside I2), specced here so the **whole
original experience** is captured, not just menu→window→drill.

### §3b.1 Build contract (this session — bounded, non-invent decisions)

The fold logic lives in a shared, side-effect-free module `bim-ootb/viewer/idmp_session.js`
(`window.IdmpSession`) so the headless witness and `idempiere.html` make the **same** calls over the
**same** `ad_seed.db` — no hand-authored identity. Pure functions, each `§`-logged:

- `listUsers(db)` → the 8 `AD_User` rows, each flagged `hasRoles` (an `AD_User_Roles` row exists). Seed
  reality NAMED, not papered over: only **SuperUser(100) · GardenAdmin(101) · GardenUser(102) ·
  WebService(50001)** have roles; **System(10) · Joe Sales(103) · Carl Boss(104) · Henry Seed(105)** have
  none → rendered, disabled, labelled "(no roles)". A role-less user cannot proceed (faithful to iDempiere).
- `rolesForUser(db, userId)` → `AD_User_Roles` ⨝ `AD_Role` for the user (the Role dropdown).
- `clientFor(db, roleId)` → `AD_Role.AD_Client_ID` → `AD_Client` (a role fixes its client; here always
  GardenWorld 11 — client 0/System is metadata-only).
- `orgsForRole(db, roleId)` → `AD_Role_OrgAccess` ⨝ `AD_Org`. Org **0 = "*" (All accessible)** is offered
  when present (it is, for Admin role 102) and means "no org narrowing"; any other org narrows data to it.
- `accessibleWindows(db, roleId)` → `Set` of `AD_Window_ID` from `AD_Window_Access` (`IsActive='Y'`).
  Verified scope difference: Admin(102)=294 / User(103)=163 of 332 W-windows in the menu.
- `scopeMenu(roots, winSet)` → prune **`action='W'` leaves** whose `windowId ∉ winSet`, then prune summary
  folders left with zero visible descendants. Returns `{ tree, visible, total }`.

**Bounded scope (what this build does NOT do — named, [[feedback_listen_first]]):** only `W` (window)
leaves are access-scoped; `P/R/F/X/I/T` leaves stay visible (their `AD_Process_Access`/`AD_Form_Access`
tables exist in the seed but scoping them is out of scope here — the spec's witness counts windows). Data
scope = append `AD_Client_ID IN (0,<client>)` and, when a specific org is chosen, `AD_Org_ID IN (0,<org>)`
to each tab read **only when the column exists** on that table — the same clause layer as master-detail's
parent-FK filter. No writes; "login" is identity/context **selection**, never auth ([[feedback_no_hype]]).

## §4 Out of scope this session (named, not silently dropped)
- CRUD writes (New/Save/Delete) — the buttons render disabled; writes arrive with CRUD-P (`edit`/`process`).
- The `ledger` Report folds (I2), the AD_Menu long-press drawer on erp.html (I3), the renderer
  registry/switcher + Odoo/ERPNext/Glassbowl slot cards (I4).
- Three.js / WebGL chrome. idempiere.html is deliberately light DOM, like the ERP shell.
- **Descriptor-driven generalization (forward, renderer #2):** I1's idempiere.html calls `ADParser`
  directly (AD-specific). The next architectural step (`docs/IDEMPIERE_2.md` §pivot — "one renderer, N
  dictionaries") is to make the renderer **descriptor-driven** — AD as the *first* descriptor, not
  hardcoded — so the Odoo renderer (#2) reuses the same chrome over a different model dictionary. Not
  done in I1; noted so the seam is built deliberately when renderer #2 starts.

## §5 Discipline
§-log under `bim-ootb/viewer/tests/`; READ before concluding. Pre-flight cite this spec + the prompt.
Edit `bim-ootb/viewer/` directly; never whole-file copy from bim-compiler. **EXPLICIT GO before any
deploy** (bump `sw.js` CACHE_VERSION + register `idempiere.html`/`icons.js`/`erp_pills.js` in the SW
precache; fetch-back-verify). Protect the 153/153 AD-UI baseline — I1 adds modules + CSS, it does not
rewrite `ad_ui.js`.

## §6 Descriptor seam (renderer #2 — DONE increment 1, 2026-06-14)
The §pivot decision ("build the descriptor seam WHEN renderer #2 starts") is now executed. `idempiere.html`
reached into `window.ADParser` / `ADData` / `IdmpSession` at **24 call sites**. `erp/erp_descriptor.js`
(`window.ErpDescriptor`) abstracts these into a **descriptor = three facets**:
- `structure`: `init(db)` · `getMenuTree(db)` · `getWindow(db, windowId)` — the dictionary / window shape
- `data`: `readRecords(db, table, where, orderBy)` · `resolveFK(db, columnName, value)`
- `session`: `listUsers/listClients/usersForClient/rolesForUser/clientFor/orgsForRole(db, …)` ·
  `buildContext(db, userId, {roleId, orgId})` · `scopeMenu(roots, winSet, procSet, formSet)` · `deleteClient(db, clientId)`

The return-SHAPES are the contract. `ErpDescriptor.register(name, impl)` / `.use(name)` / `.active`;
`?erp=<name>` selects before the chrome caches its `ADP/ADD/SES` aliases. **AD is the first descriptor** —
its facets ARE the AD globals verbatim, so it's behavior-identical (witness asserts facet identity).
Witness `bim-ootb/erp/tests/poc_descriptor_seam.js` (**W-DESCRIPTOR-SEAM 7/7**). LIVE on GH Pages (erp sw v681→merge).

**NEXT — increment 2:** a 2nd descriptor (`odoo`) registering the SAME-shaped facets over a real Odoo model
slice (`ir.model`/`ir.ui.menu`/`ir.model.fields` → the Odoo dictionary; rows via the existing odoo fold) so the
UNCHANGED chrome renders Odoo with zero per-model chrome code — the thesis proof. NON-INVENT: extract the Odoo
catalog from a real source (odoo_agent introspection), never hand-author model metadata.
