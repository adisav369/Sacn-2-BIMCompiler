# ⚠ DO NOT REMOVE — Scope guard
# Scope: add a 5D COST schedule provider to settings_editor.js — the SIBLING of the existing 4D Schedule provider
#        (captured/generated). Read-only first (T3 edit parked). It is the input surface a user SEES before the
#        cost folds into C_ProjectLine — i.e. the precondition surface for BIM_ERP_FOLD.md (Phase 3).
# NON-NEGOTIABLE: Spec-first; witness-led; §-log first (READ the log before conclusions); EXTRACT, NEVER INVENT —
#        quantities fold from the building, rates from the price list; no fabricated costs. EXPLICIT GO before deploy.
# Read first: prompts/SETTINGS_JSON_EDITOR.md (the 4D Schedule provider this mirrors — captured/generated, opts.readonly,
#        __labelKey/__summary, contract Project+Phases[]) · memory [[project_settings_json_editor]] · deploy/buildings/
#        Hospital_*.db (`qto_cache`) + a building with `m_bom_line` (SampleHouse) · the AD price list (m_pricelist).

---

# Settings → 5D Cost schedule (read-only provider)

## Why now
Settings already showcases the 4D Schedule (this building) read-only. There is NO 5D Cost sibling — the BIM-side
gap. The fold (Phase 3) needs the cost schedule as a visible, traceable surface: quantities from the model × rates
from the price list, grouped to a contract that maps 1:1 onto C_ProjectLine. Build it read-only now; editing is T3.

## Tasks (each names its witness; nothing deploys without GO)
### C1 — 5D Cost provider in settings_editor.js
- New provider `_projectCost()` — sibling to `_projectSchedule()`. Reads the building's 5D quantities
  (`qto_cache` for Hospital, `m_bom_line` for SampleHouse — handle both) and resolves rates from the price list.
  Contract: `Project + CostLines[] { item, qty, qtySource, rate, rateSource, amount }`. `opts.readonly` (writable=0).
- **Witness:** `§COST5D provider building=Hospital items=N qty-source=qto_cache rate-source=pricelist|TBD
  total=… handAuthored=0` — every line traces; `amount == qty × rate` folded, never asserted.

### C2 — Honest rate-coverage
- Where a product has no price-list rate, mark `rateSource=TBD` and exclude from the total (or show a separate
  "unpriced N items" line). NEVER invent a rate.
- **Witness:** `§COST5D coverage priced=N/total unpriced=M total-priced=… (unpriced excluded)`.

### C3 — Contract parity with the fold
- The `CostLines[]` contract maps 1:1 onto `C_ProjectLine` (BIM_ERP_FOLD F1) — same item/qty/rate keys, so the
  fold reads this provider's shape directly.
- **Witness:** `§COST5D contract keys==C_ProjectLine parity=Y`.

## Discipline
§-log under `build/erp/` (or deploy/dev test log); READ before concluding. Mirror the 4D provider's structure
exactly (don't invent a new pattern). Read-only; editable 5D cost → `cost_override` table is T3, parked. Deploy =
the OOTB way (bump sw), EXPLICIT GO, fetch-back-verify. This unblocks BIM_ERP_FOLD Phase 3's 5D input.
