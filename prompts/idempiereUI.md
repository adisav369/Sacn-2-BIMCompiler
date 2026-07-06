# ⚠ DO NOT REMOVE — Scope guard
# Scope: the iDempiere-UI path — make erp.html the consolidated ERP surface as RENDERER #1 of N under a
#        "pick your ERP" model: one engine (AD-compiled, op-log fold), pluggable ERP front-ends. Ship
#        iDempiere-like first (it already exists in ad_ui.js/ad_graph.js/ad_charts.js); Odoo / ERPNext /
#        Glassbowl-Gravity are HONEST architectural slots, proven later — NOT shipped clones.
# NON-NEGOTIABLE: Spec-first; witness-led (each task names the issue it proves); §-log first (READ the log
#        before any conclusion); deterministic / non-invent (every value a fold; pills + menu are DATA).
#        EXPLICIT GO before any deploy. Registry parity: use bim-ootb/viewer/pill_builder.js AS-IS, the
#        existing I.<name>.svg icon set, real ids/handlers for shared pills — invent nothing (docs/PILL_MANIFEST_SPEC.md §0).
# Read first: prompts/done/ERP_AD_UI.md (the renderer detail — DO NOT duplicate) · docs/PILL_MANIFEST_SPEC.md
#        (the pill bar + AD_Menu drawer) · docs/CRUD_P_R_REPORT_SPEC.md (the Report folds to surface) ·
#        bim-ootb/viewer/{erp.html,ad_ui.js,pill_builder.js} (edit HERE directly — never whole-file copy from bim-compiler).

---

## ▶ START HERE (next session) — state as of 2026-06-02

**LIVE** (`red1oon.github.io/bim-ootb/viewer/`, sw v560): erp.html pill bar (registry-driven, A+ mark) →
`idempiere.html` renderer #1 (iDempiere-classic chrome) with the menu tree + window/tab/grid/form +
**master-detail drill** (Window→Tab→Field), all folded from `ad_seed.db` via SQLite WASM. PRs #82/#83/#84
merged. AD models browsable read-only. Witnesses: `§PILL-MANIFEST`, `§IDEMPIERE-FOLD`, `§IDEMPIERE-MD`
(headless, `bim-ootb/viewer/tests/`).

**Read first:** this prompt + `docs/IDEMPIERE_RENDERER_SPEC.md` (UI, incl. **§3b login/Role/Client/Org**) +
`docs/IDEMPIERE_DATA_STREAMING_SPEC.md` (Hybrid: T0 precache · T1 httpvfs range · T2 shard) +
`docs/IDEMPIERE_2.md` (engine/model). **Process:** read `bim-ootb/GH_DEPLOY.md` and **branch off
`origin/main` BEFORE coding** (last session's lesson); edit `bim-ootb/viewer/` directly; EXPLICIT GO before deploy.

**Pick ONE bounded build (all specced + data-verified, none built):**
- **(a) Login + Role/Client/Org session** — most visible "at home" win; all data present (AD_User 8, AD_Role
  4, AD_Org 9, AD_Window_Access 1080 → role-scoped menu). HONEST: identity SELECTION, not auth. `§IDEMPIERE-LOGIN`. (`RENDERER_SPEC §3b`)
- **(b) Data streaming P1** — httpvfs RANGE proof: query one table from a hosted `ad_full.db`, witness bytes
  ≪ full. The "instant + all data" foundation. `§STREAM`. (`DATA_STREAMING_SPEC §7 P1`)
- **(c) Review pass** — verify the fold vs the Docker PG AD (`postgres:15` :5432, DBs idempiere/idempiere_test)
  + chrome vs `/home/red1/idempiere-dev-setup` `org.adempiere.ui` (clean-room). `§IDEMPIERE-REVIEW`. (§Review pass below)

---

# iDempiere-UI — Renderer #1 of N ("pick your ERP")

## The thesis (why this is the valuable move)
If the engine is data (AD → verbs → signed op-log fold), the UI is a pure RENDERER over that fold — so
"which ERP UI" is a CHOICE, not an architecture. The same GardenWorld fold can render as iDempiere-classic,
Odoo-style, ERPNext-style, or Glassbowl/Gravity. That is the strongest proof of engine-as-data AND the
honest on-ramp (an iDempiere user lands on iDempiere-like UI and is home). The moat is the engine that makes
ANY skin a thin fold — not the skins. So the real deliverable is the **renderer contract**, with renderer #1
shipped and the rest as declared slots.

## Two orthogonal axes (the pill bar offers both)
- **Render axis** (UI paradigm): iDempiere-like ▣ | Odoo-like | ERPNext-like | Glassbowl/Gravity ◉🜨.
- **Model-set axis** (data slice = an AD_Menu group): Order-to-Cash · Materials Management · Project
  Management (BIM→Project Order) · Performance Analysis (Trial Balance/P&L) · …
- Any cell is valid ("Materials Management in iDempiere-like", "Trial Balance in Glassbowl"). Materials
  Management is the BIM↔ERP hinge: a compiled building IS a bill of materials → it flows BIM → Materials →
  Project Order (the Phase-3 keystone, BIM_ERP_FOLD.md). Verb × model-set × renderer = the whole ERP.

## Honest framing (carry into all docs — feedback_erp_perf_claims, feedback_no_hype)
"iDempiere-like UI, shipped; a renderer interface; Odoo/ERPNext as architectural slots you can demo into" —
NOT "we have Odoo/ERPNext." "Looks and works like" not "is". One renderer real, N slots open.

## Build order (each names its witness; NOTHING deploys without GO; edit bim-ootb/viewer directly)

### I1 — Pill bar on erp.html (replace the §2 bottom-nav with the BIM registry)
Mount `pill_builder.js` on erp.html; drive it from a `pills.json` (Tier-1 manifest, docs/PILL_MANIFEST_SPEC §2):
home · settings · ledger(▣, the ERP financial Report — distinct id, NOT BIM's `report`=4D/5D) · graphs ·
edit · process · find · share · tm · help · glassbowl · gravity. Reuse real BIM ids/icons/handlers; new ERP
pills are ADDED rows. The existing AD-render handlers (menu/window/CRUD/charts) become pill `fn`s — no behavior change.
- **Witness:** `§PILL-MANIFEST page=erp pills=N source=pills.json handAuthoredButtons=0 reusedBimIds=[...] newErpIds=[...]`.

### I2 — Report pill surfaces the proven folds (Receipt + Trial Balance + P&L)
> **CONSOLIDATED → `prompts/IDEMPIERE_RECORD_PANEL.md`.** I2 (Report), the `edit` pill, and the `process`
> pill are now handled by ONE combined renderer prompt that REUSES the Glassbowl CRUD-P-R overlays in
> idempiere chrome (no fork) and role-gates Report as "Accts Posted" (§13.7). Build them there, not here.
> This I2 text is kept as the Report-fold reference (fact_acct extract + `report_overlay` reuse).

Extract `fact_acct`(client 11, 300 rows, balanced 46 574.97) + `c_elementvalue` into the ERP data (a sidecar
or ad_seed addition — scripts/extract_fact_acct.sh, retargeted). Reuse `report_overlay` CORE folds (already
witness-proven). Tap `ledger` → Receipt | Trial Balance | P&L.
- **Witness:** `§PILL-REPORT receipt=ok trial-balance Dr=46574.97 Cr=46574.97 balanced=Y pnl netIncome=… folds-from=fact_acct`.

### I3 — Long-press → AD_Menu side drawer (definition-as-data)
Long-press home (or ☰) → an accordion of the 14 standard AD_Menu groups → leaves by seqno, routed by action
(W→window, R→Report, P→Process). Folded from `ad_menu`+`ad_treenodemm` (client 0) — the canonical menu, not a list.
- **Witness:** `§PILL-DRAWER menu groups=14 leaves=N source=ad_menu handAuthored=0` · `§PILL-HOLD pill=home→drawer opened=Y`.

### I4 — Renderer registry + switcher (the "pick your ERP" seam, honest slots)
A `renderers.json` declaring the renderer slots; a switcher (a pill or Settings) that names the ACTIVE renderer.
iDempiere-like = the one BUILT (binds erp.html's AD render); Odoo/ERPNext/Glassbowl = declared, unbuilt slots
(selecting one shows an honest "renderer slot — not built" card, never a fake). The contract = `(AD model +
folded data) → view`; document the boundary erp.html already implements so a slot is a module.
- **Witness:** `§RENDERER active=idempiere built=1 slots=[idempiere,odoo,erpnext,glassbowl] fake=0`.

### I5 — (later, once proven) Glassbowl/Gravity refactored to CONSUME the same contract; then a 2nd paradigm slot.

## Review pass — verify I1 against the iDempiere sources of truth (NEXT SESSION)

I1 shipped (renderer #1 `idempiere.html` + pill bar, LIVE via PR #82/#83, `docs/IDEMPIERE_RENDERER_SPEC.md`).
Before building I2+, a REVIEW session must check the implementation against the **real iDempiere**, not
our own assumptions — diff-oracle discipline (learn behaviour, NEVER copy code/CSS; clean-room per
`docs/IDEMPIERE_2.md` §Guardrails 2 — copying LGPL/GPL source would contaminate the MIT corpus).

- **vs the Docker Postgres AD (source-of-truth Application Dictionary):** container `postgres`
  (`postgres:15`, port 5432, user `adempiere`; DBs `idempiere` + `idempiere_test` — see
  `docs/CRUD_P_R_REPORT_SPEC.md §1.2.1`). Verify our `ad_seed.db` fold MATCHES live PG: the menu tree
  (the 14 standard groups + their order/seqno), `AD_Window`/`AD_Tab`/`AD_Field` counts per window, and
  `action` routing (W/R/P/F). A `§`-logged diff to the cent — name any divergence, never paper over it.
  (Our headless witness `§IDEMPIERE-FOLD` reports groups=53 leaves=534 from `ad_seed.db`; reconcile that
  against the live AD, since `ad_seed.db` is a curated subset.)
- **vs the iDempiere-dev-setup codebase** `/home/red1/idempiere-dev-setup` — esp. `org.adempiere.ui`
  (the ZK web UI / theme): verify `idempiere.html`'s **desktop** chrome matches the REAL iDempiere
  desktop EXACTLY (toolbar icon set + order, the window tab box, grid↔form toggle, breadcrumb, status
  bar). "Follow exactly iDempiere UI" = desktop exact; mobile adapts. Read their layout to learn the
  *behaviour/structure*; reproduce it ourselves — do not lift markup/CSS.
- **Among others:** a running iDempiere ZK instance (if up) for pixel/interaction fidelity; the
  descriptor-driven direction (`docs/IDEMPIERE_2.md` §pivot — AD as the *first* descriptor) so renderer
  #2 (Odoo) reuses the engine; the existing AD parser/renderer (`prompts/done/ERP_AD_UI.md`) — do not duplicate.
- **Discipline carried in:** non-invent (every rendered value a fold over AD rows), witness-led (each
  check names the divergence it proves/disproves), neutral A+ mark only (no trademarked logo), and the
  153/153 AD-UI baseline stays green.
- **Witness:** `§IDEMPIERE-REVIEW ad-source=pg(idempiere) menu-groups-match=Y/N window-fold-match=… ui-vs=org.adempiere.ui gaps=[…] handAuthored=0`.

## Build items specced 2026-06-02 (for the whole original experience)

Beyond I1 (LIVE) + master-detail drill (LIVE, sw v560), two workstreams are now specced + data-verified:

- **Login + Role/Client/Org session** (`docs/IDEMPIERE_RENDERER_SPEC.md §3b`): the recognizable iDempiere
  login → context-choose → role-scoped menu (`AD_Window_Access` 1080) + client/org-scoped data. Folds from
  real rows (AD_User 8 = System/SuperUser/GardenAdmin/…, AD_Role 4, AD_Org 9, AD_User_Roles 9). HONEST: no
  server → identity/context SELECTION, not password auth. Witness `§IDEMPIERE-LOGIN`.
- **Data streaming "the rest of the data"** (`docs/IDEMPIERE_DATA_STREAMING_SPEC.md`, user chose **Hybrid**):
  T0 dictionary precached (instant) · T1 httpvfs RANGE over a hosted `ad_full.db` (45MB; only touched pages
  transfer) · T2 per-module shard offline fallback. Trigger = the window-open seam. A `DataSource`
  abstraction so the renderer is source-agnostic. Range/shard ONLY, never a full-DB download. Phased P1→P4,
  witness `§STREAM*`.

Both compose into ONE filter model (role→menu, client/org→rows, parent→child, tier→source). Build off
`origin/main` (read GH_DEPLOY.md first); EXPLICIT GO before deploy.

## Discipline
§-log under the build dir; READ before concluding. Pills + menu + renderers are DATA. ERP code edited in
bim-ootb/viewer directly. EXPLICIT GO before any deploy (bump sw CACHE_VERSION; fetch-back-verify). Protect
the existing 153/153 AD-UI test baseline — I1 is a registration layer, not a renderer rewrite.
