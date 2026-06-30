# ⚠ DO NOT REMOVE — HR_BIM_Asset (operate-phase module) SCOPE
**Scope:** The building's **operate** phase. One generic periodic RUN engine — `(period × parties ×
element-rules) → signed lines → balanced GL post` — serving **payroll · tenancy · strata · maintenance**
as profiles of ONE code path. Tenancy binds a lease to **real geometry** (a real building guid), the money
cycle runs against it, and the result is a tamper-evident signed op-group.
**Status:** ALPHA / DEMONSTRATOR. Every screen + generated output carries
`CONTOH — TIDAK RASMI` / `SAMPLE — NOT OFFICIAL`. Demo values only — no statutory/contract figure asserted.
**Prime rule:** EXTRACT OR COMPILE ONLY. The unit guid is a **real `IfcSpace`/element guid extracted from the
building** — a non-matching guid is honestly shown **un-linked**, never a fabricated binding.
**Log Mandate:** read the witness log (`build/erp/hr_bim_asset.log`) after EVERY run before any conclusion.
Honour this block until the module is DONE.

---

## Why this exists
Most property/asset tools treat a unit as a database row. Here a unit **is** a room in the model: a lease binds
to **real geometry**, and the ERP money cycle runs against that geometry. The same observation generalises — an
asset is a real BIM element, a strata charge is a real owned unit, a payslip is a real party. So all four are
profiles of the **same periodic engine**. That is the whole module: one engine, four profiles, one signed op-log,
geometry-true bindings.

Code: `build/erp/hr_bim_asset.js` · Witness: `scripts/witness_hr_bim_asset.js` · Log: `build/erp/hr_bim_asset.log`.
Builds on the real signed op-log `build/erp/kernel_ops.js` (`commitGroup` / `verifyChain` / `setSigner`).
User-facing copy already shipped: `docs/ERPUserGuide.md#hr-tenancy`, `docs/BIMUserGuide.md#find-lenses-tenancy`.

---

## §PILLAR-4 — one generic periodic RUN (the engine)
**Pillar 4 = the periodic RUN.** There is exactly ONE: `runPeriod(seed, profile, period)`.

```
(period × parties × element-rules) → obligations → balanced lines → ONE signed op-group → GL legs
```

1. **Select** — the profile's selector filters the seed to the obligations live **in this period**
   (in-term leases / due assets / active employees / current owners). Filtering is the profile's only
   per-profile logic; everything downstream is shared.
2. **Line** — each obligation becomes one line `{party, amount, drAccount, crAccount, bindGuid?, watermark}`.
   A line is a **balanced double-entry pair** by construction (Dr X = Cr Y = amount).
3. **Journal** — sum the legs per account; the run is balanced ⇔ `ΣamtacctDr == ΣamtacctCr` (always true
   because every line is a balanced pair — the witness asserts it, never assumes it).
4. **Commit** — the whole run folds as **ONE op-group** via `kernel_ops.commitGroup` (all-or-none, sealed
   once, signed if an edge signer is set). One header op `HBA_RUN` + one `HBA_RUN_LINE` per line.

**Profiles (config, not code forks):**

| Profile | cashDir | Dr | Cr | Selector keeps |
|---|---|---|---|---|
| `PAYROLL` | OUT | Wage Expense (5100) | Wages Payable (2200) | employees active in period |
| `RENTRUN` (tenancy) | IN | AR (1200) | Rent Income (4100) | leases **in-term** for period |
| `STRATA` | IN | AR (1200) | Strata Income (4200) | owners of an owned unit |
| `MAINTENANCE` | OUT | Maint. Expense (5300) | Accrued (2100) | assets with `next_due ≤ period` |

**Rent run = the payroll engine inverted.** Payroll emits payslips (cash OUT); tenancy emits one rent invoice
per *active* lease (cash IN, AR). Same deterministic, glass-box, tamper-evident engine; same balanced journal.

Witnesses: **`§HBA P-one-engine`** (one `runPeriod` serves all four profiles, each balanced) ·
**`§HBA P-tenancy-term`** (RENTRUN emits a line only for in-term leases; an out-of-term lease emits zero) ·
**`§HBA P-tenancy-gl`** (RENTRUN posts a balanced AR journal: Dr AR / Cr Rent Income, ΣDr==ΣCr).

## §BINDING — non-invent geometry binding
A demo lease/asset references a **real guid extracted from the building** (`build/erp/hr_bim_asset.js` is fed
real guids by the witness from `deploy/buildings/WBDG_Office_extracted.db` — storeys `Level 1`/`Level 2`, 7000
real element guids). `bindUnits(records, buildingIndex)` joins each `unit_guid`/`bim_guid` against the real guid
set:
- **join hits** → `bound:true`, the unit lights up (the viewer Tenancy/Asset lens tints it).
- **non-matching guid** → `bound:false`, honestly shown **un-linked** — NEVER a fabricated tint.

The seed deliberately includes one record bound to a **guid that does not exist** in the building, so the
honest-un-linked path is exercised every run. Witness: **`§HBA P-bind`** (N-1 real guids bind, the 1 synthetic
guid is un-linked, zero fabricated bindings) + **`§HBA P-chain`** (the run op-group `verifyChain`s; flip one
param byte → `verifyChain` fails at that op).

## §SPATIAL-VIEW — the viewer lenses (data derivation)
Two data-gated lenses on the viewer toolbar (icons appear ONLY when the loaded building carries the data):
- **Tenancy lens** — colours each bound unit by lease status: `occupied` (green) · `vacant` (grey) ·
  `expiring` (amber, lease ends within the horizon). High level = **population-density dots per storey**, keyed
  to the real `IfcBuildingStorey` (`storey` column). Toggling off restores the model fully (zero residue).
- **IoT / Asset lens** — colours equipment by maintenance state: `ok` / `due` / `overdue` (from `next_due` vs
  period), each asset bound to a real BIM element (`bim_guid`) — the seam for the **7D** operate cockpit.

`spatialView(seed, buildingIndex, period)` returns `{ units:[{guid,status,bound,storey}], storeys:[{storey,
occupied,total,density}], assets:[{guid,state,bound}] }` — pure, node-witnessable; the viewer is a thin renderer
over it (no business logic in the UI). The colour/tint is applied **only** to `bound:true` records.

## §CROSS-APP — one lease, three apps, one op-log
| App | Contributes | On the kernel |
|---|---|---|
| **Viewer** (BIM) | the **WHERE** — the unit lit on the model (Tenancy lens) | `unit_guid` → real `IfcSpace`/element guid |
| **ERP** | the **DEAL + MONEY** — lease as agreement, rent run → AR | `C_BPartner` · `C_Invoice(ARI)` → `C_Payment(ARR)` → allocation → GL |
| **HR** | the **PEOPLE + ACCESS** — tenant party, signed check-in | `C_BPartner` · signed `kernel_op` |

The thread is the op-log: each `HBA_RUN_LINE` op carries `input_guids=[bound geometry guid]` (the BIM WHERE) and
`params.party` (the ERP/HR WHO) — so one signed op binds geometry + money + party. The GL **dotted line**
(fold into a host iDempiere/ERP) lights up **only when that ERP is present**; **standalone, HR_BIM_Asset runs
the full cycle on its own seed** and exposes `run.journal.legs` for a host `post()` to consume. Siblings on the
same engine: **strata** (profile #4) and **asset maintenance** (profile #3, a derived 4D PM timeline off
`next_due`/`pm_cycle`) — the building's **7D operate cockpit**.

---

## DONE criteria
- `node scripts/witness_hr_bim_asset.js` → all of `§HBA P-one-engine / P-tenancy-term / P-tenancy-gl / P-bind /
  P-chain` PASS, log saved to `build/erp/hr_bim_asset.log`, read before claiming GREEN.
- Bindings sourced from a REAL building DB; one synthetic guid honestly un-linked; zero fabricated bindings.
- Doc links from `ERPUserGuide.md`/`BIMUserGuide.md` resolve to the sections above.

## NOT in this alpha (honest scope edges)
- No viewer UI render here (bim-ootb track) — `spatialView()` is the data contract the viewer consumes.
- No host-ERP GL fold landed (standalone journal only) — `run.journal.legs` is the seam.
- No working-calendar / pro-rata day-count on rent (whole-period only).
- IfcSpace rooms are ABSENT from current extracted DBs → units bind to real element/storey guids; when a true
  `IfcSpace` layer is extracted, `unit_guid` points at it with zero engine change.
