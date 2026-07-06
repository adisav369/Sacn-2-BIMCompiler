# ⚠ DO NOT REMOVE — RESUME CARD: Phase 2 / W0 = S5 "EARN THE ACTUAL"
# Scope: replace the HASH-baked CommittedAmt (the ×factor "actual") with a REAL fold from atomic op rows, so EVM
#   (PV/EV/AC → CV/SV/CPI/SPI) is honest, not seeded. This is the KEYSTONE of the commercial-cockpit wedge —
#   every later stage (W1 forecast, W2 certified claim, W3 real-project loop) is undefensible until the actual is real.
# DISCIPLINE: EXTRACT/COMPILE ONLY. Read the §-log after every run (exit code ≠ evidence). Money via site/bigdecimal.js.
#   Whitebox §-log witness IS the proof (don't boot a browser to test the engine). GO before deploy; sw bump + KEEP-BOTH precache.
# AUTHORITY: prompts/TM_4D5D_VARIANCE_LANE.md is the act-from-here lane. This card = the W0 build detail. Honour until DONE.

## WHERE WE ARE (2026-06-22)
- S1·S2·S3 ✅ (variance twin, Zoom-Across, 4D gen) · S4 ✅ (shopfloor costing, PP_Order_Cost) · **S6 ✅ DONE/LIVE**
  (what-if: `viewer/whatif.js` W-WHATIF 13/13 sw v694 + `viewer/whatif_panel.js` sw v695; ERPUserGuide §"What-if
  schedule" published with 5 real-app figs). PRs #474/#475 merged.
- "Is it a killer?" analysis → **Phase 2 = THE WEDGE** decided (lane §WEDGE-STRATEGY): one persona (QS/commercial),
  one loop (claim→variance→forecast→certify). FITS AS-IS, no redesign. Arc: **W0=S5 (this card) → W1 W-EAC → W2
  W-CLAIM-CERT → W3 W-COCKPIT-LOOP**. THE TRAP (do NOT build): CPM / resource leveling / calendars / critical-path.

## THE GROUNDED FACTS (verified erp/ad_seed.db + bake script, 2026-06-22 — do NOT re-discover)
- **The hash to replace:** `erp/tests/bake_gw_hospital_variance.js` sets `CommittedAmt = round(PlannedAmt × factor)`,
  factor = `_phaseVariant(name)` (marquee/Superstructure 1.60, else 1.04 + hash%36/100). Baked at PROJECT
  (990000 CommittedAmt=87,372,995, +35%) + PHASE (Super 58.98M, etc.) + TASK grain. **This ×factor IS S5's target.**
- **LINE grain CommittedAmt = NULL on all 28** by design (don't bake pro-rata — it earns here).
- **Source rows that EXIST:** `M_CostElement` (9 — overhead/burden), `PP_Order_Cost` (64 — shopfloor cost, Σ ==
  PlannedAmt 64,719,479 = the PLANNED, per S4/E2b). **MISSING:** `C_ProjectIssue`, `PP_Cost_Collector`.
- `viewer/proj_control.js` `evm()`: AC = `C_Project.InvoicedAmt` (0 until a claim posts); PV = Σ PlannedAmt×lin-frac;
  EV = Σ PlannedAmt where IsComplete='Y'. `viewer/proj_claim.js` `progressClaim()` already moves InvoicedAmt (AC).

## ⚠ THE FIRST DECISION (the non-invent crux — resolve before coding)
The +35% over-run is currently a HASH on the rollup. "Earn the actual" honestly means the actual must FOLD from
REAL atomic op rows, not a factor. But there is no real-world actuals feed (it's a demo). So the honest design:
**make the cost-collector / issue rows the SOURCE OF RECORD (real rows on the signed op-log), and let CommittedAmt =
Σ(those rows) emerge** — the variance then comes FROM the atomic ops, not from a factor on the total. The atomic
rows MAY still be deterministically generated (carry the `'generated'` marker, fixed seed, no Date.now/random — the
lane discipline), but they become the truth everything folds up from. Pick the source:
- **(A) PP_Order_Cost as the actual** — extend the 64 collector rows to carry ACTUAL (incurred) vs PLANNED cost; roll
  Σactual → CommittedAmt per phase→project. Reuses S4 plumbing; closest to "real shopfloor cost". PREFERRED — verify
  in `prompts/TM_SHOPFLOOR_COSTING_SPEC.md §NEXT-STAGE-2` (the cited detail) before committing.
- **(B) Introduce C_ProjectIssue** — material-at-cost + labor/resource issue rows as the actual (iDempiere-native),
  + applied overhead via M_CostElement. More faithful to iDempiere's project-costing, but a NEW table to bake.
Decide A vs B by reading TM_SHOPFLOOR §NEXT-STAGE-2 + the real iDempiere model (`~/idempiere-dev-setup/idempiere`),
NEVER assume. Whichever: the rollup CommittedAmt must equal Σ atomic rows to the cent (no residual hash).

## W0 / S5 BUILD (spec-first; witness NAMES the issue)
1. Resolve the decision above; write the §SPEC-RESOLVED block in the lane S5 stanza first.
2. New/extended fold (extends the cost layer; candidate `viewer/proj_earn.js` or extend `proj_claim.js`/the S4 path —
   confirm by reading, don't pre-create): `earnActual(db, projectId)` → folds the chosen atomic rows (+overhead via
   M_CostElement) → writes CommittedAmt at LINE then rolls LINE→PHASE→PROJECT (Σ to the cent, BigDecimal). Posts the
   incurred cost to the project ledger (F-lane, reuse postingPreview pattern) so AC is real, not a moved InvoicedAmt.
3. Re-bake / migrate: the hash `CommittedAmt` is REPLACED by the folded Σ. `bake_gw_hospital_variance.js` either calls
   the real fold or is retired for an issues/collector bake. Keep deterministic + `'generated'` marker; SAFE dry-run.
4. EVM honesty: after the fold, `proj_control.evm` CV/SV/CPI/SPI derive from real EV/AC (no seeded factor anywhere in
   the chain). Verify the indices move because the OPS moved.

## WITNESS — `erp/tests/earn_actual_witness.js` (W-PC-EARN), whitebox §-log on REAL 990000:
- §EARN-FOLD: CommittedAmt(project) == Σ CommittedAmt(phases) == Σ atomic rows (+overhead) — to the cent; NO factor
  term survives anywhere (grep the chain).
- §EARN-LINE: the 28 NULL LINE CommittedAmt are now folded from atomic rows (not pro-rata of PlannedAmt).
- §EARN-EVM: proj_control.evm CV/SV/CPI/SPI computed from real EV/AC; change an atomic row → indices move accordingly.
- §EARN-SOURCE: every atomic row resolves to a real seed row OR carries the `'generated'` marker (no invented value).

## STARTUP READS (in order)
- this card → `prompts/TM_4D5D_VARIANCE_LANE.md` (§WEDGE-STRATEGY + S5 stanza + W1/W2/W3) →
  `prompts/TM_SHOPFLOOR_COSTING_SPEC.md §NEXT-STAGE-2` (the A-vs-B detail) → `erp/tests/bake_gw_hospital_variance.js`
  (the hash to replace) → `viewer/proj_control.js` evm() + `viewer/proj_claim.js` progressClaim/postingPreview →
  the real iDempiere project-costing model under `~/idempiere-dev-setup/idempiere` (verify, never assume) →
  `erp/ad_seed.db` (M_CostElement / PP_Order_Cost / C_ProjectLine). Memory: [[project_hospital_twin_facts]]
  [[feedback_whitebox_deduce_not_browser]] [[feedback_read_java_spec_first]] [[feedback_stop_on_invent_not_instruct]].

## NEXT AFTER W0 (the wedge arc — do not start until W0 is ✅ honest)
W1 W-EAC (forecast-at-completion, extends whatif.js+proj_control.js) → W2 W-CLAIM-CERT (signed defensible claim,
extends proj_claim.js) → W3 W-COCKPIT-LOOP (the whole loop on a REAL pushed project, not the 990000 seed).
