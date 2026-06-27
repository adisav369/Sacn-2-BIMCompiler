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

## ⛓ MODELLER LANE ANCHOR — DO NOT LOSE SIGHT (read FIRST)
This rule-mining is NOT a standalone disc-rules project — it is the **"MEP walker fills ARC"** leg of the
overarching Modeller vision. Keep the whole picture in view every step:
- **VISION-LOCK** (`prompts/RESUME_GRAPH_MODELLER_INTEGRATION.md §VISION-LOCK`, memory `project_modeller_vision_lock`):
  open a WHOLE ARC building & EDIT it; **ARC = the sole edited substrate**; **3D GRID = primary handle**; every
  non-ARC discipline = a **WALKER that fills ARC** (STR strengthens, MEP/CW/SP service, 4D/5D/ERP enterprise-ify).
  `duplex_rules.db` exists to make the **MEP walker** honest on residential ARC — that is its only reason to exist.
- **3D GRID is the user's NEXT primary focus** (edit ARC overall + fine-tune via the grid). The DX-MEP standard
  must SERVE that: a walked discipline rides the edited ARC/grid, re-folds when the grid moves. Don't build rules
  that can't follow a grid edit. If this work ever competes with the 3DGrid/editor, the editor wins — surface it.
- **End state** = every drag on the ARC/grid is a signed fact the disciplines (and the enterprise) fold from.
  Mining is plumbing for that; ship the smallest mining that makes the MEP walker honest, then return to the editor.
- Companion lane cards (don't orphan them): `prompts/RESUME_MODELLER_POLISH.md` (gizmo/UX polish incl. SCALE,
  cursor, toast), the still-open bim-ootb gaps (rotate-witness vertex hardening, confidence 3D-highlight render).

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

## 🗂 DATA LOCALITY & DRIFT — GH-modeller vs OCI-viewer MUST stay SEPARATE (user 2026-06-27)
The Modeller and the Viewer treat building data DIFFERENTLY and must NOT share copies — that mixing is the
drift confusion (today's stale `modeller/terminal_rules.db` vs the rebaked one was exactly this).
- **Modeller (GH-Pages):** plain `fetch()` of `modeller/*.db` into an in-memory sql.js DB; the SW **skips .db**
  (network, no precache); curated ARC/STR substrate + `*_rules.db` for the disc-walker. Small, GH-served, repo-tracked.
- **Viewer (OCI):** `buildings/*.db` uploaded to the OCI bucket, **httpvfs range-streamed** with DLOD / full-hydrate
  guard (large city/Hospital DBs). Different transport, different shape, different lifecycle.
- **Current overlap = the hazard:** `Duplex_extracted.db`, `Terminal_meta.db`, `Terminal_geo.db` exist in BOTH
  `modeller/` and `buildings/` and can silently diverge. Clinic/Hospital exist ONLY in `buildings/` (viewer/OCI).
- **RULE for this plan:** every DB the Modeller needs (re-extracted `Duplex_mep_meta.db`, `duplex_rules.db`,
  and modeller-appropriate Clinic/Hospital extractions for Step 4) gets its **own GH copy under `modeller/`**,
  produced from the bim-compiler source (extract/bake) and COPIED in on deploy — **never point the modeller at
  `buildings/`** and never assume the two mirror. Stamp provenance so a stale copy is detectable (e.g. a
  `rules_meta(version, built_from, sha)` row, or filename/version the §-log prints on dwInit), so we never again
  ship the live engine against a stale DB. Viewer/OCI copies are produced separately and are NOT touched here.

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
- ⚠ Clinic/Hospital live ONLY in `buildings/` (viewer/OCI). To test them in the MODELLER, first make their
  **own GH copies under `modeller/`** (modeller-appropriate extraction), per §DATA LOCALITY — do NOT point the
  modeller at `buildings/`.
- Test BOTH standards on Clinic/Hospital: do the huge complexes round-trip better under Terminal-class rules
  (they have deep services) — and do they ultimately need their OWN mining (a 3rd standard)? This answers
  "where is the class boundary" empirically. Hospital (19670 MEP) is the stress test for the O(n) gate/router.

## ✅ POC RESULT (2026-06-27 — Steps 0-2 DONE, witnessed; user: "sufficient for POC, important is it works")
- **Step 0** ✅ extracted `Ifc2x3_Duplex_Federated.ifc` → `build/Duplex_mep_extracted.db` + meta clone
  `build/Duplex_mep_meta.db` (ARC253/STR12/**MEP904** carried, 0 failed, 0 bbox_fallback, ROT_TRUTH 1169 ok,
  all 904 MEP transforms real & distinct, 0 orphans). ⚠ DATA CORRECTION: §THE DATA's specialized classes
  (IfcPipeSegment/IfcSanitaryTerminal/IfcValve/IfcLightFixture…) DO NOT EXIST — this IFC2x3 model uses GENERIC
  flow classes only (IfcFlowSegment 427/FlowFitting 358/FlowTerminal 105/FlowController 14/FlowMovingDevice 4=908).
- **Step 1** ✅ `build/bake_duplex_rules.py` LIVE-mines `build/duplex_rules.db` (16 placement / 10 space_bom /
  3 routing / 5 place_order / 4 avoidance). Sub-disc (PLB/ELEC/ACMV) recovered NON-INVENT: name-keywords +
  nearest-neighbour for generic fittings, persisted to `mep_subdisc` table. Storey DERIVED from measured z-gap
  (split z=1.93, 2 storeys). **§DXM-CLEARANCE (the thesis, MEASURED):** ELEC|PLB **0.416m/0.499m**,
  ACMV|PLB 0.953m, ACMV|ELEC 1.754m — vs Terminal's 2.27-2.82m plenum numbers. Residential trades share ONE
  tight ceiling void; forcing the airport's wide cross-disc clearance is what manufactured the SH/DX/SC clashes.
- **Step 2** ✅ `build/witness_duplex_rules.py` round-trip (mirror §TRM-RT): **PLB GREEN 4/0/0** (the real
  residential plumbing reproduces — FlowSegment cover 0.93, Fitting 0.85, terminals/controllers array GREEN);
  **ELEC WEAK** (89 fixtures GREEN, sparse 8-seg conduit honest WEAK); **ACMV RED** (n=2 ducts — a house has
  ~no ductwork; honest, NOT tuned). PLB fitting→segment chain ratio=1.00 GREEN. seg→seg = structural main-run.
- **CROSS-BUILDING SURVEY** (user-invited; Terminal LOD400 noted): overall MEP p05 NN is DENSE everywhere
  (intra-run pipe packing ~0.02-0.12m in Duplex/Hospital/Terminal/offices) — it does NOT discriminate class.
  The discriminator is the CROSS-disc gap (Terminal 2.4m vs residential 0.4-0.5m). MEP counts: Hospital 19670,
  Terminal 9733, WBDG_Office 5728, HHS_Office 3390, Clinic 102, Duplex 904, SC 73.
- **REMAINING (not blocking POC):** Step 3 = walk `duplex_rules.db` onto SH/SC via `disc_walker.js` (rules=duplex
  variant of `witness_disc_walk_generalize.js`) → prove the RED-clash count COLLAPSES with residential clearances
  (this is the final clash-cure proof; rules+oracle are ready). Step 4 = Clinic/Hospital scale tier + class
  boundary. DEPLOY: per §DATA LOCALITY, copy `duplex_rules.db` to a `modeller/` GH copy with provenance stamp.

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
