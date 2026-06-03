# CRUD‑P‑R — the Report verb (spec)

Implements `prompts/CRUD_P_R_REPORT.md`. Spec‑first record of the **verified data reality** this
session, the **witness definitions**, and the **honest reshape of R2** forced by that data. Every number
below is a fold over real rows in `build/erp/{glassbowl_data.db,ad_full.db}` — none asserted.

Read‑order context: `docs/ERP.md §0.17/§0.19` (contained‑set, diff‑oracle) · `prompts/CRUD_OVERLAY.md`
(the ring + Process seam) · `build/erp/crud_overlay.js` (the ring, the pure CORE pattern this mirrors).

---

## 0. The verb — Report as a read fold

The CRUD‑P ring writes (`Process` = signed `SET_STATUS`). Report is the **read face** of the same
op‑log: a report is just a fold. Two folds, both **PURE READ** (no T3 write‑gate; ships on the proven
falsifier foundation):

- **R1 Receipt** — folds **one document** (`c_order`+`c_orderline`, etc.) from the bundle.
- **R2 Financial Report** — folds **the journal** (Trial Balance / P&L) from posted GL facts.
- **R3 Definition‑as‑data** — R1/R2 layout comes from AD rows (`ad_printformat*` / `PA_Report*`),
  not a hardcoded template.

Report is read‑only ⇒ in the ring it is **always enabled** (like `view`); it needs no `verbs[]` entry in
`crud_ops.json` and writes nothing. The receipt panel + ring icon live in a peer module `report_overlay.js`
(sibling to `help_overlay.js` / `crud_overlay.js`), decoupled via the existing key‑addressed intent bus.

---

## 1. Verified data reality (2026‑06‑02 — extract, never invent)

### 1.1 R1 source — present and clean (`glassbowl_data.db`)
`c_order` (8 rows), `c_orderline` (with `linenetamt`), `c_bpartner`, `m_product`, `c_invoice(line)`,
`c_payment`, `c_allocation*`, `m_inout(line)`. A **Receipt folds today**. Witness targets:

| doc | id | lines | Σ linenetamt | grandtotal | tax (grand−Σ) |
|-----|----|-------|--------------|-----------|---------------|
| `c_order` #80001 | 101 | 1 | 95.00 | 100.70 | 5.70 |
| `c_order` #800000 | 104 | 6 | 3657.50 | 3657.50 | 0.00 |

The header/line relationship is the **iDempiere convention** (`c_order`→`c_orderline` on `c_order_id`,
`c_invoice`→`c_invoiceline`, `m_inout`→`m_inoutline`) — *derivable*, not a per‑document invention. So the
fold is one generic routine, not O2C‑specific code.

### 1.2 R2 source — **the prompt's assumption does not hold for this extract** ⚠
The prompt (`CRUD_P_R_REPORT.md` §R2, §"What exists") expects GardenWorld's **posted facts** in
`fact_acct` to bundle whole. **Verified in `ad_full.db`:**

| table | rows | note |
|-------|------|------|
| `fact_acct` | **0** | empty in THIS extract — but it was taken from Docker `idempiere`. The posted journal lives in **`idempiere_test`** (see §1.2.1) |
| `gl_journalline` | **4** | real posted GL, **balanced** Dr 185.00 = Cr 185.00, diff 0.00 |
| `gl_journal` | 2 | the two journals those 4 lines belong to |
| `c_validcombination` | 157 | account combinations (the account dimension) |
| `c_elementvalue` | 379 | the chart of accounts (account names/values) |

A Trial Balance "to the cent from `fact_acct`" is **impossible without inventing facts** here — forbidden
(prime directive). **Reshape (honest, non‑invent):**

- **R2a (shippable now):** fold the Trial Balance from the **real `gl_journalline` rows** (4 lines,
  `amtacctdr`/`amtacctcr`, joined to `c_elementvalue` for account names). It proves the *balanced‑to‑the‑
  cent* discipline on **real posted rows** — small, but real. Witness reports `rows=4 source=gl_journalline`.
- **R2b (un‑parked + VERIFIED 2026‑06‑02):** the full GardenWorld P&L over `fact_acct` IS available — see
  §1.2.1. Pull the real rows from Docker Postgres `idempiere_test` into `glassbowl_data.db` — the same
  non‑invent move as the 11 lifecycle tables (`docs/ERP.md §0.12`), now for the posted journal. R2a
  (`gl_journalline`, 4 rows) is no longer needed as the primary source; keep it only as an offline fallback.
  Phase 3's `Process→Fact_Acct` fan‑out (`BIM_ERP_FOLD.md`) later lets a freshly Processed order hit the
  same P&L.

### 1.2.1 R2 real source — VERIFIED present in Docker `idempiere_test` (2026‑06‑02)
Container `postgres` (`postgres:15`, port 5432), user `adempiere`. **Two databases:** `idempiere` (the
one `ad_full.db` was extracted from — `fact_acct`=0) and **`idempiere_test`** which holds the posted
GardenWorld journal:

| check | value |
|-------|-------|
| `adempiere.fact_acct` rows | **300** (all `postingtype='A'` Actual, `ad_client_id=11` GardenWorld) |
| balance | **Σ amtacctdr 46574.97 = Σ amtacctcr 46574.97**, diff **0.00** → a real balanced trial balance |
| dimensions | 7 `c_period_id`, 21 `account_id`; joins to `c_elementvalue` (value+name, e.g. 11100 Checking Account) |

So the Trial Balance / P&L folds to the cent from real rows — R2 is a clean extract‑then‑fold, not blocked.
Extract `fact_acct` + the `c_elementvalue` (chart of accounts) + `c_period`/`c_acctschema` it references.

This reshape is recorded here so the prompt's R2 line is read against the data, not the assumption.

### 1.3 R3 source — present (`ad_full.db`)
`ad_printformat` (124 Check, 126 Remittance, 100 Order_Header template, 102 Invoice_Header template, …) +
`ad_printformatitem` (per‑format item rows: `name`/`printname`/`seqno`/`isprinted`). `PA_Report` (100
Balance Sheet, 101 Income Statement, 102 Cash Flows) + `pa_reportline`/`pa_reportcolumn` linesets. Layout
**is data**. (Caveat to handle in R3: the Order_Header template's `seqno` are all 0 — order falls back to
row order; name that, don't silently reorder.)

---

## 2. Tasks & witnesses (each names the issue it proves; nothing deploys without GO)

### R1 — Receipt  ·  `report_overlay.js` + `scripts/test_report_overlay.js`
Fold the focused document; render header + lines + subtotal/tax/total. Non‑bundle tables → honest
"not carried", never a fabricated number. Non‑financial documents (`m_inout` — qty, no amount) →
`financial=false`, subtotal/tax shown as "n/a (non‑financial document)".

> **§REPORT‑RECEIPT** `doc=C_Order#101 lines=1 subtotal=95.00 tax=5.70 total=100.70 folds-from=bundle handAuthored=0`
>
> Proof obligation: the rendered totals **equal an independent re‑fold of the raw rows** — the witness
> recomputes `subtotal=Σlinenetamt`, `tax=grand−subtotal` straight from sqlite and asserts equality with
> `CORE.foldReceipt(...)`. `handAuthored=0` ⇒ no literal amount appears in the layout code.

### R2 — Trial Balance  (R2a now; R2b parked) — *next session, after R1 lands*
Bundle the 4 real `gl_journalline` rows (+ the `c_elementvalue` names they reference) into
`glassbowl_data.db`. Fold a Trial Balance.

> **§REPORT‑FIN** `trial-balance Dr=185.00 Cr=185.00 balanced=Y maxDiff=0c folds-from=gl_journalline rows=4`
>
> Folded balances reconcile to the `gl_journalline` sums **to the cent**. The UI names R2b: "P&L over
> `fact_acct` — 0 posted facts in this extract; awaits a posted dump / Phase‑3 posting."

### R3 — Definition‑as‑data — *after R2*
Render R1/R2 through the AD definition rows (`ad_printformat`/`item` for the Receipt, `PA_Report`
structure for the Financial Report). Layout is DATA.

> **§REPORT‑DEF** `receipt=ad_printformat#100 fin=PA_Report#101 handAuthoredLayout=0`

---

## 3. Honest gap (named, not hidden)
`Process` today writes only `docstatus`, not journal facts (`CRUD_OVERLAY.md §Process`, GP3 sidecar). So
R2 reflects **pre‑posted GardenWorld facts** ("the books as loaded") — and in THIS extract those facts are
empty, so R2a folds the only real posted rows (`gl_journalline`). Wiring `Process→Fact_Acct` so a freshly
Processed order hits the P&L is **Phase‑3‑adjacent** (`BIM_ERP_FOLD.md`), not this phase.

## 4. After the receipt — the output lifecycle ("then what?", named 2026‑06‑04)

**The forgotten thread, now named.** Today the receipt is a **view‑only fold**: `report_overlay.js render()`
paints the table and the ONLY action is the close ✕ (`build/erp/report_overlay.js:192/216/221`). There is no
print, share, save, email, or persistence — the flow stops at "read it, close it." This section specifies what
happens *after*, and the key is that there are **TWO separate concerns** that must not be merged (they are not
the same step — conflating them is the muddle):

### 4.1 OUTPUT / DELIVERY — what the human does WITH the receipt (edge/OS intents, server‑free)
The receipt content already exists (the §R1 fold); "after" is rendering/dispatching it. The doctrine already
names these — they were **named but never wired**:
- **Print / Share / Save‑PDF** — `docs/SocialPlatformLens.md:64` ("invoice / receipt → PDF + **OS share
  sheet** → share to anyone, print"). The browser mechanism is edge‑native, no server: `window.print()`
  (print / Save‑as‑PDF) and `navigator.share({files:[…]})` (OS share sheet on mobile), with a Blob download
  as the desktop fallback. NON‑INVENT: the printed/shared artifact IS the same folded receipt — output adds a
  render+dispatch surface, never new data.
- **Email / channel deliver** — `docs/GuaranteedChannels.md §1` (email = the "dumb async post office"; the
  user's own cloud carries the whole signed log as a file). Delivering a receipt = attach the fold (or its
  signed op) to the guaranteed channel. The `fold↔email` adapter is named there; owner currently unassigned.
- **Tasks (spec‑first; each NAMES its witness; nothing deploys without GO):**
  - **R4 — receipt actions:** add Print / Share / Save‑PDF to the receipt panel. Witness `§RPT‑OUT`: the panel
    exposes the three actions; `navigator.share`/`window.print` are invoked with the folded receipt; the
    desktop Blob‑download fallback fires when `navigator.share` is absent. UI overlay only — **HANDS‑OFF the
    write loop**, same rule as §R1.
  - **R5 — channel deliver (later):** dispatch the signed receipt op via the guaranteed channel
    (`GuaranteedChannels`/`SocialPlatformLens`). Witness `§RPT‑SEND`: a delivered receipt is idempotent +
    UUID‑keyed (the fold tolerates the channel — duplicates/out‑of‑order safe). Couples to the email‑adapter owner.
- **Honest residual:** the folded receipt is **RAM‑only** today (it lives in the panel; lost on close/reload).
  Persisting it is log‑first — the receipt is a *projection* of the op‑log, so re‑fold it on demand from the op,
  do NOT store the projection (`[[project_erp_write_path_spine]]`). The print/PDF *layout* is R3's
  `ad_printformat`‑driven rendering (parked, §2 R3).

### 4.2 CONSEQUENCE / POSTING — what the LEDGER does after Complete (NOT the same as 4.1)
A document *action* (Complete) has consequences beyond a receipt:
`Complete = DOC_ACTION + SHIP + INVOICE + Dr‑AR + Cr‑Rev`, all‑or‑none (`prompts/ENGINE_FULL_ERP_ISSUES.md §I‑K`).
**Current honest reality:** `commitProcess` → `buildDocActionGroup(op)` commits **ONLY** the `SET_STATUS` op
(`build/erp/crud_overlay.js:153‑160`) — the status flip, atomic‑READY, but the consequence/posting ops are a
clearly‑marked **DELEGATED extension point** (NON‑INVENT: the browser never fabricates ship/invoice/postings).
So **"Completed" in the UI ≠ the books moved** until the consequence ops arrive.
- **Where they come from:** the install / §13.6 re‑extract — `docs/PLUGIN_ARCHITECTURE.md §13.5` proved the ARI
  sales‑invoice posting genome; **§13.6** rolls it to all postable doc‑types. This is the **Agent‑P / install‑
  oracle lane** (`ENGINE_FULL_ERP_ISSUES.md §2.1`), NOT a browser‑POC blocker — and it is exactly why a freshly
  Processed order's P&L stays empty (§3 above, `BIM_ERP_FOLD.md`).
- **No new browser task here** — this subsection exists to NAME the dependency so "print the receipt" (4.1,
  browser/edge) is never confused with "post the document" (4.2, install/ledger). That conflation was the gap.

### 4.3 Still‑open lifecycle threads (named so they are not forgotten)
`DR→IP→CO` is implemented; **CL (Close) / Void / Reverse / archive** are not specified. Multi‑doc *chaining*
(an order's Complete auto‑creating a shipment → an invoice) is named only as the §I‑K example, with no manifest.
These route to the DocAction / Agent‑P lanes; tracked here as the receipt's upstream/downstream, not built in
this phase.

## 5. Discipline
§‑log under `build/erp/`; READ before concluding. Pre‑flight cite this spec. HANDS‑OFF the live
write‑loop files except to ADD the read‑only Report verb to the ring. Deploy = Glassbowl‑way, bump sw
`CACHE_VERSION`, **EXPLICIT GO**, fetch‑back‑verify.
