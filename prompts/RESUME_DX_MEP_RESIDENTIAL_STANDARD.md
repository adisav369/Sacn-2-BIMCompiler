# RESUME — DX-MEP as the RESIDENTIAL / small-building disc standard (the building-class plan)

```
# ⚠ DO NOT REMOVE
SCOPE: Build a SECOND disc-rule standard — mined from DUPLEX's OWN MEP — for residential / small
buildings, the way terminal_rules.db is the standard for LARGE COMPLEX. One size does NOT fit: today's
RED clashes on SH/DX come from forcing Terminal's 2.429m clearance (a deep-plenum airport number) onto a
house. The fix is the RIGHT-CLASS rules, MINED, not tuned. METHOD = mirror the Terminal arc exactly
(extract → mine → round-trip witness against the building's OWN real MEP → reconcile). PRIME GUARD:
NON-INVENT. The oracle is DX's REAL MEP (round-trip = walk mined rules back onto DX ARC/STR, land on the
real MEP). An honest RED/WEAK is correct; never tune a clearance/band to pass. Read the §-log after every run.
ENGINE = build/disc_walker.js (shared Placer/Router/Gate — disc=data filter, do NOT fork per building).
PRECEDENT to copy: prompts/RESUME_TERMINAL_RULE_MINING.md (mine→bake→reconcile) + the §TRM-RT round-trip
oracle build/witness_terminal_rules.py. Today's clash-gate work: build/disc_walker.js gate() + G4 in
witness_disc_walk_generalize.js (iterate-in-envelope + FLAG, no silent clash).
```

## WHY (the thesis the user set, 2026-06-27)
Disc rules are **building-class-specific**, not universal. Terminal = large-complex (deep plenums, 2.4m
clearances, 3–6m bays, multi-storey z-bands). A Duplex/house has none of that → Terminal-density MEP
**cannot** fit clash-free (witnessed: SH 5, DX 192, SC 2091 irreducible RED clashes — the gate now flags
them honestly instead of hiding/burying). The remedy is a **residential standard mined from a real
residential MEP model**, then proven to generalize to other small buildings. Big complexes (Clinic/Hospital)
are a separate scale tier tested last.

## THE DATA (verified 2026-06-27 — all present, non-invent foundation)
- **DX has its OWN MEP at the SOURCE** (the deployed `Duplex_extracted.db` dropped it = ARC253/STR12 only):
  `reference/residential/Ifc2x3_Duplex_MEP.ifc` (+ `_Mechanical.ifc`, `_Plumbing.ifc`, `_Federated.ifc`,
  `_Architecture.ifc`). MEP entity counts: **IfcFlowSegment 427, IfcPipeSegment 407, IfcFlowFitting 358,
  IfcFlowTerminal 105, IfcPipeFitting 59, IfcFlowController 14, IfcElectricAppliance 7, IfcSanitaryTerminal 6,
  IfcValve 3, IfcLightFixture 3, IfcDuctSegment 2, IfcDuctFitting 1** — rich enough to mine PLB + small ACMV +
  ELEC residential cadence/clearance.
- **SH** (`SampleHouse_extracted.db`): ARC39/STR26, **no MEP** → the pure generalize target (walk DX rules,
  honest-place by residential cadence, no round-trip oracle since SH has no MEP to land on).
- **SC** (`SampleCastle_extracted.db`): ARC3317/STR206, **MEP 60** (IfcFlowSegment×60) → partial oracle.
- **Huge-complex scale tier** (already extracted, `~/bim-ootb/buildings/`): **Clinic** (PLB6585/ACMV3704/
  ELEC2118/ARC1984/STR1621/MEP102), **Hospital** (MEP19670/ARC14641/FP14357/PLB9121/STR2828/ELEC2798),
  Hospital_3. Plus `docs/HospitalAnalysis.md`, existing MEP infra (DV037/039/040/043, ad_mep schema,
  scripts/witness_mep_coordination_route.js).

## THE PLAN (next session — ordered, each step witness-first, mirrors the Terminal arc)
### Step 0 — Re-extract DX MEP (the missing substrate)
- Extract `Ifc2x3_Duplex_MEP.ifc` (or `_Federated`) → a `Duplex_mep_meta.db` with `elements_meta` +
  `element_transforms` (same schema the miner/witness read), discipline-tagged (PLB/ACMV/ELEC), MEP carried
  (NOT ARC/STR-only). Reuse `extractIFCtoDB.py`. WITNESS: counts match the IFC entity counts above (±0).
  ⚠ this is the one real build risk — if extraction drops MEP again, fix the extractor, don't fake the DB.

### Step 1 — Mine DX-native residential rules → `duplex_rules.db`
- Same mining the Terminal got (placement z-bands per storey, routing nn-chains with MEASURED gaps, array
  cadence, **avoidance/clearance MINED FROM DX's own p05 NN distances** — these will be far < 2.429m, the
  whole point). Bake like `bake_terminal_rules.py` → `build/duplex_rules.db`. NON-INVENT: every rule row
  carries n_measured + src_guids from the real DX MEP.

### Step 2 — Round-trip witness (the oracle, mirror §TRM-RT)
- `witness_duplex_rules.py` (clone of `witness_terminal_rules.py`): replay `duplex_rules.db` against
  `Duplex_mep_meta.db` → do the mined rules REPRODUCE DX's real MEP? Per-disc GREEN/WEAK/RED, no tuning.
  This is the "compare back as done with Terminal" the user asked for. The clearance round-trip here is the
  key new artifact: residential min_clear that actually fits.

### Step 3 — Generalize test: is DX the SMALL-BUILDING standard?
- Walk `duplex_rules.db` onto SH and SC via the shared `disc_walker.js` (extend
  `witness_disc_walk_generalize.js` with a `rules=duplex` variant). Run the HARDENED gate (iterate-in-envelope
  + flag): with residential clearances, the RED-clash count should collapse toward ~0 on SH/DX/SC (the proof
  the right rules fit where Terminal's didn't). SC's 60 real MEP = a partial oracle to compare against.
  DECISION: does DX generalize cleanly → adopt `duplex_rules.db` as the residential standard + roster it in
  the modeller alongside terminal_rules.db (building-class auto-select, or a user toggle).

### Step 4 — Scale tier: huge complex (Clinic → Hospital)
- Test BOTH standards on Clinic/Hospital: do the huge complexes round-trip better under Terminal-class rules
  (they have deep services) — and do they ultimately need their OWN mining (a 3rd standard)? This answers
  "where is the class boundary" empirically. Hospital (19670 MEP) is the stress test for the O(n) gate/router.

## ACCEPTANCE / WHAT "DONE" LOOKS LIKE
- `duplex_rules.db` round-trips GREEN (or honest WEAK) on DX's own MEP (§DX-RT, mirror §TRM-RT-DISC).
- With DX residential clearances, SH/DX/SC walk with **few/zero irreducible RED clashes** (the gate flags,
  but there's little to flag because the rules FIT) — the direct cure for today's finding.
- A clear DECISION recorded: DX = residential standard (which buildings it covers) + the class boundary
  where Terminal-class or per-building mining takes over.
- Regression: all current gates stay green (generalize 49/49, shim 6/6, erp-equiv 14/14, nnchain 6/6,
  W-TRM-RT 12/1/0, W-TERM-WALK 10/10).

## GUARDRAILS
- Do NOT fork the engine — `disc_walker.js` stays one Placer/Router/Gate; the rules DB is the only variable.
- Oracle = the building's OWN real MEP (round-trip). `extracted.db` is the substrate; the MEP IFC is truth.
- Clearances/bands are MINED (p05 NN, measured z-bands), never typed in to pass. Honest RED is a result.
- "if unexpected issues, return to the blueprint discussion" (user standing rule) — surface, don't grind.

## POINTERS
- Terminal precedent: `prompts/RESUME_TERMINAL_RULE_MINING.md`, `build/{bake,reconcile,witness}_terminal_rules.py`.
- Engine + today's gate fix: `build/disc_walker.js` gate() (iterate+flag), `build/witness_disc_walk_generalize.js` G4.
- DX MEP source: `reference/residential/Ifc2x3_Duplex_MEP.ifc`. Huge complex: `~/bim-ootb/buildings/{Clinic,Hospital}_*.db`.
- Memory: [[feedback_whitebox_no_handwave_geometry]], project_terminal_rule_mining, project_modeller_rosettastone_mission (SIMPLE-set-first doctrine — DX/SH/SC before huge complex).
```
