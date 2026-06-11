# iDempiere Browser ERP — User Guide

*The browser kernel renders the full iDempiere Application Dictionary from SQLite — no Java, no server,
no install. This guide walks from first load to POS sale to financial report.*

![The landing page — "The Server Is Obsolete" — explains the concept before you open the app](figs/glassbowl_landing.png)

> **Where to read the full concept:** the landing page links to
> [Migrate & Compare (ERP)](MigrateComparisonPaper.md) — start with the six pillars (double-entry
> ledger · log-is-truth · event-sourcing · active dictionary · hash-trees · SQLite embeddable), then
> the Roadmap section for POS and warehouse-walk. The paper is the architectural rationale; this guide
> is the operating manual.

---

## Quick start

Open **idempiere.html** (or follow the "iDempiere" link from the front door). A login card appears
immediately — no credentials, just pick a role.

---

## 1. Login

The login card lists all AD roles in the seed. Pick one and tap it:

| Role | Use |
|---|---|
| **GardenUser** | The default demo persona — access to Sales, Purchasing, Inventory, POS |
| **Admin** (GardenAdmin) | Full menu (294 / 332 windows); sees all process / form leaves |
| **WebService** | 0 windows — confirms the access-gate works (empty menu is correct) |

The menu is pruned immediately by real `AD_Window_Access` / `AD_Process_Access` grants from the seed —
if a window is absent for your role, that is correct, not a bug.

---

## 2. Install / Migrate

**Install** and **Migrate** are both reached through the **⋯ pill** (bottom-right, "more" menu):

- **Install** — loads the bundled `ad_seed.db` into browser IndexedDB (IDB). This is the full-width
  GardenWorld tenant (bim-ootb PR #265, IDB key v14). It runs once; subsequent loads read from IDB.
- **Migrate from Odoo** — paste / upload an Odoo export; the kernel folds it into the op-log format
  and seeds a new tenant alongside the existing one.
- **Migrate from iDempiere** — same flow for an iDempiere export (the `gen_ad_idmp.js` path; PKs are
  re-banded so they never collide with the Odoo import).

After install the page reloads with the full menu. No URL change needed — the seed is in IDB.

---

## 3. The Bottom Pill Bar (cheat sheet)

The pill is the single entry point for every tool. Left-to-right:

| Icon | Pill ID | What it opens |
|---|---|---|
| ⌂ | `home` | Top-level AD menu (window list, role-pruned) |
| ⊙ | `toggle` / `settings` | Settings panel (language, theme) |
| 🔍 | `search` (red circle) | Record find / filter across the current window |
| ⟳ (ring) | `idmp` / Dictionary | The AD-model dictionary surface — browse AD tables live |
| **W** | `history` | World history overlay — scrub the op-log timeline (‹ dots ›) |
| ⋯ | `more` | Install · Migrate · About · Tour · ShowMe help badges |

**Tip:** long-press the **W** pill to open the Z+bomb drawer (undo / fold-back to a past state).

---

## 4. Navigating Windows

1. Tap **⌂** — the menu tree opens (role-pruned).
2. Browse by category (Sales, Purchasing, Inventory, …) or use the search leaf.
3. Tap a window — the record list loads from the seed db.
4. Tap a record row — the detail form opens with all AD tabs.
5. Breadcrumb at the top shows `Window › Record`. Tap any crumb to go back.

**Keyboard shortcuts** (desktop):

| Key | Action |
|---|---|
| `←` / `→` | Step back / forward in the history bar |
| `?` | Toggle help badges (ShowMe mode) |

---

## 5. Forms, Tabs, and Fields

- **DisplayLogic** is live: fields hide/show based on real `AD_Field.DisplayLogic` expressions (27 of
  60 Sales-Order fields hidden by the evaluator — `ad_evaluator.js`).
- **DocAction bar**: when a document is open the action chips at the top are the *legal* next actions
  for its current DocStatus, derived from `ad_docfsm.js`. Completed orders show Close / Void — never
  ReActivate unless the DocType allows it.
- **Editable fields**: tap a field to edit. Changes are staged in a CRUD overlay. Hit **Save** to
  commit as a signed sidecar op.

---

## 6. The Process Button

When a menu leaf or a form has a **▶ Process** button (type P/R in the menu), tapping it:

1. The `ad_process.js` dispatch spine resolves `AD_Process.classname` → a registered handler.
2. If the process has `AD_Process_Para` rows, a **parameter dialog** appears first — fields are
   validated (`§PROC_PARAM_VALIDATE`) before the handler fires.
3. The handler returns an op-group; `kernel_ops.commitGroup` writes it as a **signed op** to IDB.
4. The form re-reads and shows the updated status.

**Does the record persist?** Yes — every committed op is written to IDB as a signed op-log entry.
The op-log is append-only: every change has a timestamp, a hash chain, and a reversible trail.
No traditional row-update happens; the current state is the fold of all ops on that record.

**Unregistered classnames** show an honest "absent handler" card (the 333-falsifier) — 454 of the
476 `SvrProcess` handlers are named-deferred; the 5 registered ones cover the demo flows.

---

## 7. POS — Point of Sale

**Prerequisite:** log in as **GardenUser** (the POS station `c_pos_id=1` is scoped to that role).
Tap the **POS** pill in the bottom bar (it appears only when the loaded db has a `c_pos` station).

### POS screen layout

- **Left panel** — product grid from `c_poskey → m_product` (the sealed keylayout). Every product
  shows its master price from the station's pricelist — you ring the master, not a manual price.
- **Right panel** — the current cart: partner, line prices, running total.

![POS — Garden User · Store: product grid left, live cart right, replenishment suggestions below](figs/pos_live.png)

### Making a sale

1. Tap a product tile → it is added to the cart at the sealed price.
2. Tap again to increment qty, or edit qty in the line.
3. Review **Total** (BigDecimal fold of line amounts, never a posted figure yet).
4. Tap **Tendered cash — Complete** to commit the sale.

The "Complete" creates ONE signed op-group:

```
CREATE_DOCUMENT  C_Order  (the POS sale order)
CREATE_LINE      C_OrderLine  (one per cart line)
SET_STATUS       C_Order → CO
CREATE_DOCUMENT  M_InOut  (shipment, policy-gated)
CREATE_LINE      M_InOutLine
SET_STATUS       M_InOut → CO
CREATE_DOCUMENT  C_Invoice  (invoice, policy-gated)
SET_STATUS       C_Invoice → CO
CONSUME          M_Transaction P−  (one per BOM leaf, the backflush)
```

### Backflush (§P-3)

If any product has a BOM (e.g. Patio Furniture Set), `erp_engine.explodeBOM` recursively
expands the recipe and adds a `CONSUME P−` op for every leaf component. The on-hand fold
(`qtyOnHand`) reflects the deduction immediately.

### Replenishment (§P-4)

After each sale, the panel runs `poc_replenish` — it folds `m_transaction` to find on-hand
per product and emits a reorder PO for anything that has fallen below `m_replenish.level_min`.
The suggestion list shows at the bottom of the cart panel.

---

## 8. Financial Reporting

Tap **⌂ → Performance Analysis → Financial Report** (or use the Statements pill from the Dictionary):

| Report | Status | Notes |
|---|---|---|
| **Balance Sheet** | ✅ oracle-equivalent | 108 cells, `maxDiff=0c` vs real GardenWorld `fact_acct` |
| **Income Statement** | ✅ oracle-equivalent | 148 cells |
| **Cash Flow** | ✅ oracle-equivalent | 140 cells |
| **Trial Balance** | ✅ oracle-equivalent | `ΣDr==ΣCr=46574.97` (GardenWorld real ledger) |
| **Invoice Print** | ✅ oracle-equivalent | 8/8 invoices, 48 cells, `foldPrint` W-PRINTFORMAT |

The **⎙ Print** button on an invoice form opens a single-page print view generated from the real
`AD_PrintFormat` metadata — not a template, a fold of the dictionary's print format rows.

---

## 9. Warehouse Walk (§S-1 compiled — §S-2..S-5 in progress)

The warehouse app compiles the GardenWorld warehouse as a BIM-like spatial model
(`warehouse_gardenworld.db`, 61 KB — 11 bins == `m_locator`). Current status:

- **§S-1** ✅ — warehouse compiled; bins map to real `m_locator_id` values; render gate green.
- **§S-2** 🟡 — route verb (`wh_route.js`) built; sorts pick lines by the spatial walk sequence
  (`m_bom_line.ordinal`); `poc_wh_route.js` witness pending deploy.
- **§S-3..S-5** ⛔ — walk UI (phone-first fly-to + lens), QR scan, signed put-away op.

---

## 10. Clearing Cache / Resetting Demo Data

The app state lives in **IndexedDB** (key `bim_erp_db`, version 14). To reset to a clean demo:

**Chrome / Edge / Firefox:**
1. Open DevTools (`F12`) → **Application** (Chrome) or **Storage** (Firefox).
2. Find **IndexedDB → bim_erp_db** → right-click → **Delete database**.
3. Reload the page → the Install flow runs again from the bundled seed.

**Quick reset via the browser console:**

```js
indexedDB.deleteDatabase('bim_erp_db');
location.reload();
```

**Service Worker cache** (if the app served a stale version):

```
DevTools → Application → Service Workers → Unregister
DevTools → Application → Cache Storage → Delete All
```

Then hard-reload (`Ctrl+Shift+R`). The sw version is bumped on every deploy so this is only
needed if you see an old `CACHE_VERSION` number in the console.

---

## 11. Pending Roadmap — known gaps and on-demand conditions

### Data-gated (seed doesn't contain these yet)

| Gap | Condition | Fix |
|---|---|---|
| **Trial Balance shows 0 rows** on default `ad_seed.db` | `fact_acct` table not in seed (Postgres-only source) | Load `?db=preview_demo.db` or run `prompts/MIGRATE_POSTING_CONFIG.md` |
| **Posting Preview** empty | Same — acct linkage absent in default seed | Same fix |
| **POS live ring to the cent** | posting-config needed for the live ring | `MIGRATE_POSTING_CONFIG.md` |
| **T_Aging / T_ReportStatement** folds | 13 `T_*` temp-table folds not yet built | Phase B §H-7..§H-11 (future) |

### Engine-gated (code exists, not yet wired to live UI)

| Gap | Engine status | UI status |
|---|---|---|
| `AD_Callout` derived fields on field-change | ✅ headless (W-CALLOUT-HARDEN) | Render-wiring parked |
| `AD_Val_Rule` picklist filter | ✅ headless (W-VALRULE-HARDEN) | Render-wiring parked |
| `AD_Workflow` node-walk | 🟡 `ad_workflow.js` built, no seed activity oracle | Parked |
| Column-level / Record-level access | `AD_Column_Access` / `AD_Record_Access` empty in seed | n/a |
| 454 SvrProcess handlers | 5 registered, 454 named-deferred | Honest absent-handler card shown |
| `stale fork`: `report_overlay.js` in bim-ootb | 256 vs 908 lines in build/erp — lacks foldStatement/foldPrint | Needs sync + sw bump |

### Coverage matrix summary (2026-06-12)

```
✅  7  (live UI witnesses)
🟡 32  (headless-green, render-wiring pending)
⛔  3  (n/a-in-seed: AD_Rule SQL + 2 empty *_Access)
```

The 🟡 ceiling means: every behaviourally interpretable surface that *has seed data* is exercised —
the remaining work is wiring headless-proven engines into live DOM.

---

## 12. Pill Quick-Reference (cheat sheet)

```
Bottom bar
  ⌂          Home — AD menu tree
  ⊙ / ▣      Settings
  🔍 (red)   Record search / filter
  ⟳ (teal)   Dictionary (AD model browser)
  W          History — world op-log scrubber
  ⋯          More: Install · Migrate · About · Tour · ShowMe

Inside a record form
  ▶ (Process) Run an AD_Process (parameter dialog if needed)
  DocAction chips  (Complete · Close · Void · …) — legal set only

Help
  ?            Toggle ShowMe badges
  Tap a badge  Context card with tip + screenshot
  ‹ · ›        Step through ShowMe sequence

History (W pill)
  Click a dot  Jump to that op in the log
  ← / →        Step back/forward one op
  Long-press W Open Z+bomb drawer (undo / fold-back)
```

---

*For architecture details see [ERP.md](ERP.md). For the coverage evidence see
[ERP_COVERAGE_MATRIX.md](ERP_COVERAGE_MATRIX.md). For the migration story see
[MigrateComparisonPaper.md](MigrateComparisonPaper.md).*
