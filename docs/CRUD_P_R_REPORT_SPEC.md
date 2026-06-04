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

## 4. Discipline
§‑log under `build/erp/`; READ before concluding. Pre‑flight cite this spec. HANDS‑OFF the live
write‑loop files except to ADD the read‑only Report verb to the ring. Deploy = Glassbowl‑way, bump sw
`CACHE_VERSION`, **EXPLICIT GO**, fetch‑back‑verify.

---

## 5. R4 — after‑the‑receipt OUTPUT (delivery channel)  ·  `§RPT-OUT`  *(DONE, deployed sw v9)*
The receipt panel was view‑only (✕ close). R4 adds **Print / Share / Save** — edge‑only, server‑free —
each serialising the SAME folded `rec` (no re‑query, no invented value): `Print` = print‑iframe of
`receiptHtml(rec)`; `Share` = `navigator.share({files:[html]})` → `share({text})` → clipboard; `Save` =
`Blob('text/html')` → `receipt_<doc>.html` download. Witness `build/erp/poc_rpt_out.js` → **§RPT-OUT-R4
PASS**. The delivery channel is *incidental*; the load‑bearing artifact is what R5 now makes verifiable.

---

## 6. R5 — the signed, self‑verifying receipt  ·  `§RPT-SEND`

### 6.0 Why (doctrine)
`HolyGrail.md` condition (3): *"the op‑log makes editing safe — signed, replayable, reversible,
tamper‑evident."* R5 is the FIRST time that property **leaves the app** and becomes consumer‑visible: the
delivered receipt carries (or references) the **signed op‑log chain** so the recipient can **replay it and
verify the chain hash with no server and no trust in the sender** (`ERP.md §0.18` frozen/replayable; the
W‑CHAIN/W‑SIGN witnesses `scripts/poc_chain.js`/`scripts/poc_sign.js`; `kernel_ops.js` `sealChain`/
`verifyChain`/`setSigner`). Because verification is a pure READ‑side replay, R5 **ships ahead of E3** (the
live write path is still dry‑run) — intended.

### 6.1 The honesty boundary (non‑invent — DO NOT cross)
The receipt attests **"the recorded, signed op‑chain"** — it MUST NOT claim "the books moved / posted".
Per `§I‑K`/`§13.6`: **Completed ≠ posted**; GL posting is delegated install‑side. The receipt copy is
precise about what it signs — *the recorded op‑chain, tamper‑evident* — and never over‑claims a GL effect.
Verify‑panel copy: *"This receipt carries its signed op‑chain. Verify replays the chain locally and checks
the tamper‑evident hash + signature — it attests the recorded ops, NOT that the books were posted (GL
posting is delegated install‑side)."* If a value cannot be extracted, absence is shown honestly.

### 6.2 Payload shape — `.erpreceipt.json` embedded in the self‑contained HTML
The signed receipt = the folded `rec` (R1) **+** the signed op‑chain. One canonical JSON object, embedded
verbatim in a `<script type="application/erp-receipt+json">` block inside the delivered HTML (so the file
is BOTH human‑viewable AND machine‑verifiable; a `.erpreceipt.json` sidecar is the same object standalone):

```
{ "v": 1, "kind": "erp-signed-receipt",
  "rec":   { …foldReceipt output: key, docno, lines[], subtotal, tax, total, financial… },
  "chain": { "alg": "SHA-256", "sigAlg": "ECDSA-P256",
             "genesis": "<64×0>",
             "pubKey": "<issuer SPKI hex | null if unsigned (W-CHAIN-only)>",
             "tip":    "<op_hash of the last op>",
             "ops": [ { "id","timestamp","op_type","parameters","input_guids","output_guid",
                        "prev_hash","op_hash","sig" }, … ] },
  "attests": "the recorded, signed op-chain (tamper-evident) — NOT a GL posting" }
```

`canonical(op)` is the **production form** (`kernel_ops.js _canonical`):
`id|timestamp|op_type|parameters|input_guids|output_guid`, and `op_hash = SHA‑256(prev_hash | canonical)`,
`sig` attests over `op_hash` (not in the hash → chain stays byte‑identical across devices). The receipt
reuses the engine's chain — **it does NOT fork a verb**: in‑browser it pulls the already‑sealed rows from
`kernel_ops` (`prev_hash`/`op_hash`/`sig` columns) via the same canonical; the witness reuses the same
canonical + the `poc_sign` ECDSA primitives.

### 6.3 Verify procedure (independent, server‑free)
A **Verify** affordance in the receipt panel (and a self‑contained re‑verify when the HTML is re‑opened)
replays the embedded chain with **no original DB and no network**:
1. walk `ops` in order, `prev = genesis`;
2. for each op: assert `op.prev_hash === prev`; recompute `h = SHA‑256(prev | canonical(op))`; assert
   `h === op.op_hash`; if `pubKey` present assert `verify(op_hash, sig, pubKey)`; `prev = h`;
3. `chainOk = all assertions hold`; `tip = prev`; assert `tip === chain.tip`.
Any altered byte/amount in `rec` or in any op's mutating field flips `op_hash` (or breaks a `prev_hash`
link / signature) at exactly that op → **`chainOk=false`**. This is the whole point: the payload **catches
tampering** offline.

> **Receipt↔chain binding.** `rec` is the human face; the **chain is the truth**. So a meaningful receipt
> binds at least one op whose `parameters` carry the document the `rec` folds (e.g. a `SET_STATUS`/
> `CREATE_DOCUMENT` for `c_order#<docno>`). Tampering the *displayed* `rec` total while leaving the chain
> intact is shown as a **mismatch** (`recBoundOk=false`) — the verifier re‑derives the bound field from the
> signed op and compares; it never trusts the rendered number.

### 6.4 Witness — `build/erp/poc_rpt_send.js` (node, sql.js + real kernel_ops canonical + poc_sign ECDSA)
Each line names its issue; `§`‑log first (`node build/erp/poc_rpt_send.js 2>&1 | tee build/erp/poc_rpt_send.log`):
- **§RPT-SEND-PAYLOAD** — the built receipt payload carries the signed chain: `ops>0`, every op has
  `op_hash`+`sig`, `pubKey` present, `tip` present.
- **§RPT-SEND-VERIFY** — an INDEPENDENT verifier (fresh, no original db) replays the embedded chain →
  `chainOk=true`, `tip` matches `chain.tip`.
- **§RPT-SEND-TAMPER** — flip ONE byte/amount in the payload (an op's `parameters` AND the matching
  displayed `rec` amount) → verify **FAILS** at exactly the tampered op (`chainOk=false brokeAt=N`).
- **§RPT-SEND-SELFCONTAINED** — the payload verifies from the serialized JSON/HTML ALONE (parsed back,
  no kernel, no original db, no network).
- **§RPT-SEND-MONEY** — `rec` amounts fold via BigDecimal exact (subtotal/tax/total == golden), and the
  attested string names the op‑chain, not a GL posting (honesty boundary asserted in the witness).
- **§RPT-SEND PASS** — all green, 0 fails.

### 6.5 Deploy
Glassbowl‑way, **worktree‑isolated off `full`** (never the dirty shared tree). Bump glassbowl sw
`CACHE_VERSION` **v9→v10** (+ `?v=`), `mkdocs gh-deploy` to BIMCompiler gh‑pages, fetch‑back‑verify the
live URL. Sync proven `build/erp/report_overlay.js` to `docs/` (per `feedback_erp_source_of_truth`).
