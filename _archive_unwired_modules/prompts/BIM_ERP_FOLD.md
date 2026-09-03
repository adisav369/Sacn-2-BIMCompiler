# ⚠ DO NOT REMOVE — Scope guard
# Scope: THE KEYSTONE — fold a BIM building (Hospital) into an iDempiere Project Order, then run the CRUD-P-R ring
#        on it. 4D (tasks/schedules) → C_ProjectPhase/C_ProjectTask; 5D (qto_cache quantities × rates) →
#        C_ProjectLine; then iDempiere "Generate Order from Project" → C_Order. New code = a pure-mapping
#        bim_adapter.js; the kernel + verbs do NOT change. This makes "BIM and ERP are one engine" a demo, on real
#        building data, not a claim.
# NON-NEGOTIABLE: Spec-first; witness-led; §-log first (READ the log before conclusions); EXTRACT, NEVER INVENT —
#        every C_ProjectLine traces to a qto_cache/m_bom_line row, every phase to a task. EXPLICIT GO before deploy.
# Read first: docs/ERP.md §0.17/§0.19 (contained-set; long-tail ships matcher-free; the adapter-at-one-seam shape) ·
#        prompts/ODOO_FOLD_POC.md (the adapter pattern — mirror it for BIM) · scripts/erp_kernel.js / erp_engine.js
#        (the verbs to reuse: completeOrder/createInvoice/allocate) · deploy/buildings/Hospital_*.db (the source).

---

# BIM → iDempiere Project Order (the fold)

## Why now / what's verified
The iDempiere PROJECT model is REAL and present in `ad_full.db` (`c_project`, `c_projectphase`, `c_projecttask`,
`c_projectline`, `c_projecttype`) — near-empty in GardenWorld, so the STRUCTURE is the target and the BUILDING is
the source data that populates it (non-invent). Hospital (`deploy/buildings/Hospital_*.db`) carries 4D
(`tasks`/`task_sequences`/`task_elements`) and 5D via **`qto_cache`** (Hospital has NO `m_bom` — the adapter must
read whichever 5D representation the building carries: `qto_cache` for Hospital, `m_bom_line` for SampleHouse).

## Tasks (each names its witness; nothing deploys without GO)
### F1 — bim_adapter.js (pure mapping; engine unchanged)
- State-machine + schema map: building → project bridge. `tasks`→`C_ProjectPhase`/`C_ProjectTask` (4D);
  `qto_cache`|`m_bom_line` × rates → `C_ProjectLine` (5D); building → `C_Project`. Output = the SAME op shape
  (`{op_type,id,parameters,ts}` INPUTS; op-GROUP for fan-out).
- **Witness:** `§BIM-FOLD building=Hospital phases=N tasks=M lines=K mapped traces: line→qto/bom=K/K phase→task=N/N
  handAuthored=0 missing=0`.

### F2 — Generate the Project Order
- Drive the adapted ops through the existing verbs; run iDempiere's "Generate Order from Project" (C_Project →
  C_Order + C_OrderLine).
- **Witness:** `§BIM-FOLD generate-order from=C_Project#x order=C_Order#y lines=K total=… == Σ(C_ProjectLine)`.

### F3 — The ring operates on it (closes the circle)
- Process(order) → REAL signed write (the proven write-loop). Report → BOQ Receipt (R1) + project-cost Financial
  (R2). For the project P&L to reflect this order, wire **Process → Fact_Acct fan-out** (the `poc_showstopper`
  group: Dr/Cr journal) — the one piece of genuinely new wiring.
- **Witness:** `§BIM-FOLD ring process=signed-ok receipt=BOQ folds-from=order financial=project-cost folds-from=fact_acct`.

## Honest dependencies
- **5D rates** come from a price list (`m_pricelist`/`m_productprice` in the AD) — extracted, not guessed. If a
  product has no rate, the line is "qty priced where rates exist, TBD otherwise." (Surfaced by `SETTINGS_5D_COST.md`.)
- **Fallback:** if Hospital's `qto_cache` shape needs work, first-cut on **SampleHouse** (`m_bom`/`m_bom_line` present),
  then return to Hospital — same adapter, different 5D source. Log the choice.
- This is **extraction, not invention** — if a verb the building needs doesn't exist, that's a logged FINDING.

## Discipline
§-log under `build/erp/`; READ before concluding. Pre-flight cite the spec. Kernel changes (the Fact_Acct fan-out)
are spec'd here but ADDED to the POC/adapter path, not silently to the live kernel. Deploy = Glassbowl-way, EXPLICIT GO.
