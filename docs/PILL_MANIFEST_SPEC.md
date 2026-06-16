# Pill Manifest & Menu Drawer — spec (erp.html)

A single BIM‑style pill bar as the **unifying affordance surface** for the browser ERP: every loose
screen control, every surface launcher, and the whole iDempiere menu reachable from one place. Home is
**`erp.html`** (in `bim-ootb/viewer/`) — it loads `ad_graph.js`+`ad_ui.js` off `ad_seed.db`, has no pill
bar yet, and sits in the **same repo as `pill_builder.js`** (S281), so pills are native: no vendoring,
no glassbowl↔gravity sync‑point.

Spec‑first; non‑invent (pills + menu are DATA, not hardwired); witness‑led; **EXPLICIT GO before build/deploy**.
This document is the manifest only — nothing is built yet.

Read first: `bim-ootb/viewer/pill_builder.js` (the registry — the pill shape below is its `actions[]` shape),
`prompts/PILLS_TO_GLASSBOWL.md` (P0–P3), `docs/CRUD_P_R_REPORT_SPEC.md` (the Report fold it surfaces).

---

## §0 Registry parity — non‑invent (the governing rule)

"Ensure the registry we use is the SAME, to avoid inventing." Concretely:

1. **Same engine.** Use `pill_builder.js` as‑is (it lives in `bim-ootb/viewer/`). No second pill framework.
2. **Same pill shape.** `{ id, name, key, icon, fn, isActive?, panel?, hold? }` — no new fields.
3. **Same icon vocabulary.** Icons come from the existing `I.<name>.svg` set (`I.clock`, `I.share`,
   `I.search`, `I.home`, `I.clipboard`, `I.eye`, `I.palette`, …). **No hand‑drawn SVGs** for anything the
   set already covers; a genuinely‑missing glyph is an *add to the icon set*, named + reviewed, not inlined.
4. **Reuse real ids/handlers for shared pills.** Verified canonical BIM actions to REUSE verbatim
   (id · name · key · icon): `tm`·Time Machine·t·`I.clock` (toggles the bottom timeline) · `share`·Share·/·`I.share`
   · `find`·Find/Navigate·f·`I.search` · `help`·Help·F1 · `settings`·Settings·= · `home`·Home·`I.home` ·
   `redpill`·Doc Mode·, · plus `night`/`palette`/`shadow`/`fly`/`section`/`xray`/`measure`/`background`.
5. **New ERP pills are ADDED rows, never redefinitions.** `edit`, `process`, `graphs`, `glassbowl`, `gravity`
   have no BIM equivalent → new manifest rows (same shape, icons from the set).
6. **⚠ `report` id collision — DECISION NEEDED.** BIM's `report` pill already means **"4D / 5D"** (key `4`).
   The ERP financial Report (Receipt/Trial Balance/P&L) is a different thing. Options, no silent redefine:
   (a) erp.html owns its own `pills.json`, so `report` there = ERP report (surface‑scoped meaning); or
   (b) give the ERP financial pill a distinct id (e.g. `ledger`, `I.clipboard.svg`) to stay unambiguous when
   surfaces converge. **Recommend (b)** — keeps one global meaning per id. Flagged for the user.

---

## §1 Organization — two tiers (the BIM model: small bar, everything a long‑press away)

`pill_builder.js` already gives: a scrollable vertical pill column (3 visible, scroll to reveal), per‑pill
`isActive` highlight, and a **long‑press (`hold`, 450ms)** secondary. We use that hold for a **side drawer**.

- **Tier 1 — primary pills (the bar):** a *curated, frequent* set — the verbs + surface launchers below.
  Bounded (~12), thumb‑reachable, persisted order (`pill_builder` localStorage config).
- **Tier 2 — long‑press → side drawer:** opens a categorized drawer rendered FROM DATA. The drawer is the
  reach‑everything layer so the bar stays small:
  - **Menu (☰ / Home) long‑press → the iDempiere AD_Menu tree** — the 14 standard groups → their
    windows/reports/processes (§3). This is "iDempiere standard menu gives the important items," rendered
    as definition‑as‑data, never a hand‑typed list.
  - **Report long‑press → the Reports drawer** — the 99 `action='R'` menu items (filtered AD_Menu).
  - **Graphs long‑press → the Charts drawer** — `ad_charts.js getPrebuilt()` chart list.
  - **Tap** = the pill's primary action; **long‑press** = its drawer. Same gesture grammar everywhere.

Rationale: 850 menu items (454 W / 122 P / 99 R / 41 X / 22 F) cannot live in a bar. The bar curates;
the drawer = the AD_Menu, so coverage is total without clutter.

---

## §2 Primary pill manifest (Tier 1)

Shape per `pill_builder.js actions[]`: `{ id, name, key, icon|img, fn, isActive?, panel?, hold?, platform?, order }`.
The manifest itself is DATA (`pills.json`) — a new affordance = a new row, not new bar code (the S281 contract).
★ = explicitly named by the user.

| order | id | icon | name | tap (`fn`) | long‑press (`hold` → drawer) |
|------|----|------|------|-----------|------------------------------|
| 1 | `home` | ⌂ | Home | focus the AD canvas | **AD_Menu tree** (§3) |
| 2 | `settings` | ⚙ | Settings | settings JSON editor | — |
| 3 | ★ `report` | ▤ | Report | Receipt · Trial Balance · P&L (folds `fact_acct`) | **Reports drawer** (99 `R`) |
| 4 | `graphs` | ▦ | Graphs | `ad_charts` overlay | **Charts drawer** (`getPrebuilt`) |
| 5 | ★ `edit` | ✎ | Editable | CRUD ring (create/update/delete, signed) | — |
| 6 | `process` | ▶ | Process | DocAction DR→CO (signed) | (legal actions for docstatus) |
| 7 | `find` | 🔍 | Find | search records / accounts | — |
| 8 | ★ `share` | ⤴ | Share | deep‑link current record/trace/report | — |
| 9 | ★ `glassbowl` | ◉ | Glassbowl | → glassbowl.html | — |
| 10 | ★ `gravity` | 🜨 | Gravity | → glassbowl_gravity.html | — |
| 11 | ★ `help` | ? | Need Help | NeedHelp? / ShowMe overlay | — |
| 12 | `about` | ⓘ | About | about box | — |

**Glassbowl‑local view controls** (`trace`/`untangle`/`diagonal`/`qr`/`reset`/`mute`/`panel`) stay registered
pills **on glassbowl.html** (its own `pills.json`), not on erp.html — they are scene controls, not ERP verbs.
One registry source, per‑surface manifest (same one‑source / per‑surface discipline as the overlays).

---

## §3 The side drawer = iDempiere AD_Menu (definition‑as‑data)

The drawer is NOT a hand‑authored list — it renders the real menu rows, so it IS iDempiere's menu:

- **Source (verified, Docker `idempiere_test`, client 0):** `ad_menu` (name, action, AD_Window/Process/
  Form/Workflow id) + `ad_treenodemm` (parent_id, node_id, seqno) for the `treetype='MM'` tree.
- **14 standard top‑level groups (verified order):** System Admin · Application Dictionary · Partner
  Relations · Quote‑to‑Invoice (Sales) · Requisition‑to‑Invoice (Purchasing) · Returns · Open Items
  (Financial Movements and Aging) · Material Management and Pricing · Project Management · Performance
  Analysis and Accounting · Assets · Manufacturing · Human Resource and Payroll · Maintenance Request.
- **Render:** an accordion of the 14 groups → children by `seqno`; each leaf carries its `action`
  (W/R/P/F/X) and target id. A leaf tap routes by action: `W`→open the AD window (existing `ad_ui`),
  `R`→Report drawer / fold, `P`→Process, `F`→form. `action='R'` filtered = the **Reports drawer**.
- **Project Management** group is the Phase‑3 hook (where the BIM Project Order lands).
- The menu rows are extracted into the ERP data alongside `fact_acct` (§4) — non‑invent, the canonical menu.

---

## §4 Data prerequisites (non‑invent extracts into the ERP data)

The Report pill + the drawer need rows that `ad_seed.db` lacks today. Extract them the same way as the
glassbowl bundle journal (`scripts/extract_fact_acct.sh`, retargeted to the ERP data / a sidecar):

| need | tables | for |
|------|--------|-----|
| financial fold | `fact_acct` (client 11, 300 rows, **balanced 46 574.97**) + `c_elementvalue` (chart of accounts) | Report: Trial Balance / P&L |
| receipt fold | already in `ad_seed.db` (the O2C documents) | Report: Receipt |
| menu drawer | `ad_menu` + `ad_treenodemm` (client 0, `MM` tree) | the AD_Menu side drawer |

Source‑of‑truth note: client‑11 `fact_acct` lives in Docker `idempiere_test` (the `idempiere` DB shows 0
posted) — recorded in `docs/CRUD_P_R_REPORT_SPEC.md §1.2.1`.

---

## §5 Witnesses (headless §‑log first; Playwright wiring only)

- `§PILL-MANIFEST page=erp pills=12 source=pills.json handAuthoredButtons=0` — bar is registry‑driven.
- `§PILL-DRAWER menu groups=14 leaves=N source=ad_menu handAuthored=0` — the drawer folds the real menu.
- `§PILL-REPORT tap=report receipt=ok trial-balance=balanced pnl=ok` — the Report pill surfaces the proven folds.
- `§PILL-HOLD pill=home → drawer=ad_menu opened=Y` and `§PILL-HOLD pill=report → drawer=reports leaves=99`.
- `§PILL-NAV glassbowl→glassbowl.html gravity→glassbowl_gravity.html` — surface launchers route.

## §6 Build order (each names its witness; NOTHING builds without GO)
- **M0** — `pills.json` manifest (Tier‑1 rows) + mount `pill_builder.js` on erp.html. Witness `§PILL-MANIFEST`.
- **M1** — extract `fact_acct`+`c_elementvalue`+`ad_menu`+`ad_treenodemm` into the ERP data (§4).
- **M2** — Report pill: render Receipt + Trial Balance + P&L (reuse the proven `report_overlay` folds). `§PILL-REPORT`.
- **M3** — long‑press side drawer = AD_Menu (definition‑as‑data, §3). `§PILL-DRAWER` / `§PILL-HOLD`.
- **M4** — surface launchers (Glassbowl/Gravity) + Graphs (`ad_charts`). `§PILL-NAV`.
- **M5** — glassbowl.html keeps its own `pills.json` for the scene controls (one registry, per‑surface manifest).

## §7 Discipline
ERP code edited in `bim-ootb/viewer/` directly (never whole‑file copied from bim‑compiler). Pills + menu are
DATA. §‑log under the build dir; READ before concluding. EXPLICIT GO before any build or deploy; bump the sw
CACHE_VERSION on deploy; fetch‑back‑verify.
