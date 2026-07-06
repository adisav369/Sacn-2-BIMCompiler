# ⚠ DO NOT REMOVE — Scope guard
# Scope: CONTINUE the Odoo migration-solvent fold as the falsifier for the iDempiere-2.0 thin core.
#        Authority spec = prompts/ODOO_FOLD_POC.md (Steps 0-2, witnesses, acceptance). This file is the
#        CONTINUATION wrapper: resume state + the iDempiere-2.0 framing + the data-dictionary deliverable.
#        DO NOT duplicate ODOO_FOLD_POC.md — follow it; this adds where to resume and what it feeds.
# NON-NEGOTIABLE: spec-first; witness-led (each test NAMES the issue it proves/disproves); §-log first;
#        Log Mandate (save the run, READ the log before conclusions); deterministic (ids/ts/amounts are
#        RECORDED INPUTS from the Odoo export — no Date.now()/Math.random(); STATIC oracle, no live Odoo at replay).
# CLEAN-ROOM: learn Odoo's BEHAVIOUR (run it, diff its executed rows); never copy its source/schema into the
#        MIT tree (LGPL contamination). A verb Odoo needs that iDempiere lacks is a NAMED FINDING, not a fabrication.
# Read first: prompts/ODOO_FOLD_POC.md · docs/IDEMPIERE_2.md · docs/HolyGrail.md (migration solvent) ·
#        ERP.md §0.12/§0.17/§0.19 · build/erp/odoo_fold.log (resume state + master-table map) ·
#        scripts/diff_oracle.js + scripts/erp_kernel.js / erp_engine.js (the verb set + harness to MIRROR).

---

# Odoo Fold — continue (the iDempiere-2.0 falsifier)

## Why this session exists
`docs/IDEMPIERE_2.md` claims a **thin general core** (5-table bridge + verbs + op-log) that every ERP folds
into. Odoo is the fairest external test of that claim. This fold either **holds** (Odoo's O2C reproduces with
the existing verbs + a pure adapter → `newVerbs=[]`) or is **bounded** (a small NAMED set of additions). Both
are valid, reportable results that feed IDEMPIERE_2 — we are trying to FALSIFY, not confirm.

## Resume state (from the 2026-06-02 session — see build/erp/odoo_fold.log)
- **Source is up-able:** `docker start odoo-db odoo` (DB `odoodemo` already initialized — do NOT re-init;
  full launch recipe + the iDempiere `postgres` isolation note are in `build/erp/odoo_fold.log`).
- **Step-0 finding:** Odoo demo ships NO connected O2C chain (SO/picking/invoice disconnected; `picking.done=0`;
  posted invoices have empty `invoice_origin`, none paid). → the oracle MUST be DRIVEN to completion (below).
- **Starter map exists:** the Odoo→iDempiere master-table map + 5 named structural divergences are drafted in
  `build/erp/odoo_fold.log` — VERIFY each row against the real export; do not trust it.

## Work order (per ODOO_FOLD_POC.md — follow that spec for detail)
1. **Step 0 — drive + freeze the oracle.** Via RPC: confirm one `sale.order` → validate its delivery
   (`stock.picking` state=done) → `_create_invoices` + post (`account.move` posted) → register payment →
   reconcile. Export those executed rows to STATIC `build/erp/odoo_oracle.{db,json}`. Log
   `§ODOO-ORACLE so=… picking=… invoice=… payment=… reconcile=… rows=N`. No live Odoo after this.
2. **Step 1 — `scripts/odoo_adapter.js`** (the ONLY new code; the engine does NOT change). This adapter IS
   the **Odoo↔iDempiere data dictionary** the user flagged as the important downstream artifact — the
   schema map (Odoo tables → 5-table bridge, using the iDempiere C_*/M_* vocabulary as lingua franca) + the
   state-machine map (Odoo `(model,state,action)` → the generic transition cell). Seed from the log's map; verify.
3. **Step 2 — fold + diff.** Drive the adapted op-groups through the EXISTING verbs
   (`completeOrder`/`createShipment`/`createInvoice`/`allocate`/`match`); multiset-diff the canonical
   projection against `odoo_oracle` exactly as `diff_oracle.js` does.

## Witnesses (§-log first; the only evidence)
- `§ODOO-FOLD chain so→picking→invoice→payment→reconcile mapped=5/5 missing=0`
- `§ODOO-FOLD verbs used=[…] newVerbs=[…]`  ← the HEADLINE. Empty = thin-core thesis holds for Odoo.
- `§ODOO-FOLD diff matched=K missed=0 extra=0 vs odoo_oracle agree=Y` (delivered qty, invoice total, payment,
  reconciliation amount — to the cent).
- `§ODOO-FOLD replay-hash stable rebuildA==rebuildB agree=Y` (deterministic, holder-irrelevant).
- `§ODOO-FINDINGS …` — every divergence NAMED (expect the 5 in the log: account.move unifies invoice+journal;
  line-level vs header reconcile; SO/PO split vs C_Order+IsSOTrx; recursive res.partner; anglo-saxon vs costing).

## Feed back into iDempiere-2.0 (the point of doing it here)
- Update `docs/IDEMPIERE_2.md` honestly with the result: each finding is either **absorb-and-generalize**
  (e.g. line-level reconcile → the general allocation primitive; header allocation becomes a projection) or a
  **quarantined adapter detail** — never per-ERP cruft in the core. Keep the pivot THIN.
- Promote the verified `odoo_adapter.js` map into the standalone **Odoo↔iDempiere data dictionary** (each row
  witnessed against the oracle, divergences named). This is the first spoke of the pivot/interlingua.
- Update ERP.md / HolyGrail.md migration scope with the honest "Odoo O2C: holds / bounded by [N named]" verdict.

## Guardrails
- Engine does NOT change — only `odoo_adapter.js` is new. Reuse the iDempiere verb set + `diff_oracle` harness.
- Non-invent: every value folds from the export; if the oracle lacks a value, report "absent in dataset",
  never synthesise (mirror the GL `fact_acct=0` honesty).
- Static oracle only; HANDS-OFF the live CRUD/glassbowl files; NO deploy.
- Clean-room + clean-identity (docs/IDEMPIERE_2.md §Guardrails): behaviour not source; no Odoo logo/name as a mark.

## Status
- CONTINUATION kickoff, 2026-06-02. Authority: prompts/ODOO_FOLD_POC.md. Produces `build/erp/odoo_fold.log`
  (append) + `scripts/odoo_adapter.js` + `build/erp/odoo_oracle.*` + a `# DONE` ledger (claim ↔ §ODOO line). No deploy.
