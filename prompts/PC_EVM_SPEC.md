# ⚠ DO NOT REMOVE — SPEC: "S5(B) — Earned-Value Management from the existing twin (no invented data)"
# Parent lane: prompts/TM_4D5D_VARIANCE_LANE.md §S5 · resume: prompts/RESUME_360_KANBAN_PIVOT.md
# PRIME RULE: EXTRACT/COMPILE ONLY. Every number traces to a real column. Whitebox §-log first (read the log;
#   exit code ≠ evidence). Money via the seed's integer rupiah (exact in JS Number < 2^53; _money() for display).
#   Honour this block + "read the log after every run" until DONE.

## §ISSUE THIS PROVES
The ⚖ drawer shows cost Δ (S2) + a projected schedule slip (E3). It does NOT yet express the standard
Earned-Value capstone — the running cost-efficiency + the at-completion forecast — that a Primavera/Navisworks
rival shows. S5(B) folds EVM from the EXISTING twin (PlannedAmt/CommittedAmt + phase windows). NO generated
C_ProjectIssue layer (that was option A — declined: it would fabricate transactions that sum back to the figure
they decompose = circular, and the user vetoed pro-rata line-committed 2026-06-20). Real earn-the-actual waits
for the OPFS round-trip (real docs); this is the honest, fully-extracted EVM.

## §HONESTY FINDING (decided during spec — do NOT fake an independent SPI)
Standard EVM wants PV/EV/AC → SPI(=EV/PV) + CPI(=EV/AC). On THIS twin:
- COST is real: CPI/EV/AC fold from PlannedAmt/CommittedAmt + the cursor's baseline progress. ✅ ship.
- SCHEDULE has NO independent actual: C_ProjectPhase has only planned dates; PP_Order.DateStart/Finish are NULL;
  the 4D gantt follows the baseline. Our ONLY schedule-deviation signal is the E3 slip — and that is projected
  FROM cost with ratio r=Committed/Planned, so slip = dur×(r−1) ⇒ any SPI derived from it = dur/(dur×r) = 1/r =
  Planned/Committed ≡ **CPI**. An "SPI" here would be CPI re-labelled = a fake schedule measurement.
- DECISION: ship the COST EVM honestly (EV/AC/CPI/CV + the EAC/VAC forecast). Do NOT emit an independent SPI;
  the schedule story stays the E3 "projected finish (+N d) projected from cost". Label the EVM block "cost · from
  records". (If the user later wants SPI, it needs real actual dates — the OPFS round-trip, same gate as option A.)

## §DATA (verified erp/ad_seed.db — all EXTRACTED)
C_ProjectPhase (C_Project_ID=990000, PlannedAmt>0): StartDate/EndDate + PlannedAmt + CommittedAmt per phase.
Σ PlannedAmt = 64,719,479 (BAC) · Σ CommittedAmt = 87,372,995. C_ProjectIssue/PP_Cost_Collector/M_Product_Costing
ABSENT (the reason A is impossible without invention).

## §IMPLEMENTATION (viewer/time_machine.js)
- `_computeEVM(V, cursor)` → {EV, AC, CPI, CV, BAC, EAC, VAC}. For each twin phase: baseline progress
  `frac = clamp((cursor − startDate)/(endDate − startDate), 0, 1)`; `EV += pCost·frac`, `AC += aCost·frac`.
  `BAC = V.tP`; `CPI = AC>0 ? EV/AC : 1`; `CV = EV − AC`; `EAC = CPI>0 ? round(BAC/CPI) : V.tA`; `VAC = BAC − EAC`.
  (At completion EV=BAC ⇒ EAC = AC = the real CommittedAmt to the rupiah — the fold reconstructs the actual.)
- `drawVariance`: after the SP (E3) block, append a compact EVM line to `#tm-var-head` (or the list):
  `EV <m> / AC <m> · CPI <x.xx> · forecast EAC <m> (VAC ±<m>)` — CPI/VAC red when over (<1 / negative),
  green under. Honest tag "cost · from records". Cursor-driven (re-folds on scrub via the existing drawVariance).
- `console.log('§EVM_FOLD cursor=<ms> EV=<> AC=<> CPI=<x.xxx> EAC=<> VAC=<> BAC=<>')`.

## §WITNESS (whitebox §-log first; Hospital geom in OPFS → live visual deferred)
`viewer/tests/test_pc_evm.js` — W-PC-EVM, replicates `_computeEVM` against the real seed:
  A — at cursor = project end (all phases complete): EV == Σ PlannedAmt (BAC) to the rupiah; AC == Σ CommittedAmt;
      CPI == BAC/ΣCommitted (≈0.741); **EAC == the real CommittedAmt (87,372,995) to the rupiah** (forecast
      reconstructs the actual); VAC == BAC − EAC (negative = overrun).
  B — at a mid cursor (end of Superstructure): CPI < 1 (over budget), EAC > BAC (forecasts the overrun), EV/AC
      from completed phases only, all finite.
  C — identities hold: CPI = EV/AC · CV = EV − AC · EAC = round(BAC/CPI) · VAC = BAC − EAC.
  D — NO invention: C_ProjectIssue count == 0 (nothing generated); the fold is read-only.
  E — deterministic (byte-identical across runs).
  Each assertion NAMES its issue. Regressions: re-run test_shop_dates (E3) + test_pp_zoom_tm.

## §SHIP
Branch off fresh origin/main; viewer sw bump + time_machine.js?v bump; PR → main → verify live (§EVM_FOLD in
served time_machine.js, sw bumped). No ERP file / seed change.

## §STATUS
- [x] spec (this file)
- [x] _computeEVM + drawVariance EVM header line + §EVM_FOLD (viewer/time_machine.js)
- [x] W-PC-EVM 14/14 (viewer/tests/test_pc_evm.js) — at completion EAC==87,372,995==real CommittedAmt to the
      rupiah; CPI 0.741; mid 0.626. Regressions W-SHOP-DATES / W-PPZOOM-TM green.
- [~] ship — PR #471 (bim-ootb), auto-merge squash armed, viewer sw v693 / time_machine.js?v=56; VERIFY live
      (§EVM_FOLD in served time_machine.js).
