# ⚠ RESUME — MEP discipline-coordination lane (authored 2026-06-21)

## STRATEGY (agreed with user)
- **SampleCastle = the STAGE** (clean ARC shell ≈3.3k ARC, almost no MEP → route generated MEP onto it; fits the
  doctrine: ARC/STR placed, FP/ELEC/ACMV/etc are ROUTE-generated). **EVOLVED 2026-06-26: only ARC is placed/editable;
  STR is now WALKED too (structural RouteWalker — `STR_ROUTEWALKING_SPEC.md`), alongside MEP/FP/ELEC/ACMV.**
- **Terminal = the RULEBOOK SOURCE** (real recipes/anchors). Its GEO-matched extraction is
  `library/archive/Terminal_extracted.db` (elements_rtree centres == the 340 ad_mep_anchor x_m/y_m/z_m exactly;
  site_normalization already applied). ⚠ The `deploy/buildings/` & `deploy/dev/buildings/` Terminal copies are
  DIFFERENT extractions in other frames (guid-0 / coord mismatch) — ORPHAN TRAP, do NOT use them.
- **Coordination rules are NOT in our data** — they are external building-services knowledge → researched online,
  every rule cited (non-invent). The Rosetta Stone proves geometry, not priority. See [[feedback_rosetta_proof_real_building]].

## DONE / LIVE (committed on branch lane/benchmark-clash-resolution, all pushed)
1. **Canvas zero-drift proof** (the reconstruction truth, in JS): `scripts/rosetta_canvas_sh.js`
   — SampleHouse 55/55 pairs 0.000mm + Duplex 1085/1085 588k pairs 0.000mm. ZERO DRIFT. `scripts/geo_verify.js`
   = faithful JS port of the canonical `scripts/geo_verify.py` yardstick.
2. **Envelopes baked** into `deploy/dev/mep_rw.db` via `scripts/build_mep_arc_envelope.js` (now format-aware:
   served-format bbox OR extraction-format elements_rtree; reads building DBs via better-sqlite3 for rtree):
   Terminal 1122 ARC boxes (frame-verified: 340/340 anchors inside), SampleCastle 2372 ARC boxes. (Duplex/SH unchanged.)
   ⛔ Terminal `building_room` NOT baked — no ROOM-grain BOM & no IfcSpace (no real source = would be invention).
3. **Cited ruleset** `docs/MEP_COORDINATION_RULESET.md` (provenance-flagged VERIFIED/PENDING/REFUTED).
4. **CoordinationHandler** `deploy/dev/mep_coordination.js` + `scripts/test_mep_coordination.js` (W-MEP-COORD 14/14):
   priorityRank/yields/minSeparation/resolveMove/arbitrate; ENFORCES only VERIFIED rows, PENDING = advisory.

## RESEARCH STATE (deep-research run wf_105ef846)
- ✅ VERIFIED 3-0 (cited, enforced): data↔power 50mm (BS6701/NEC800.52); sprinkler↔structure 50mm + penetration
  oversize (NFPA13 §18.4.2/.9); **duct depress ≤30%** jog (SMACNA); duct avoids electrical rooms; **SMACNA sets
  NO priority → the who-goes-first order is BSRIA BG6 *convention*, not a code clause.**
- 🟡 PENDING (gathered+cited but verifiers ABSTAINED on the session limit — NOT disproven): the **priority order**
  (gravity-first), **gravity-holds-at-clash**, **ceiling-void stacking** (⚠ two sources CONFLICT: sprinkler-highest
  vs duct-highest), AS/NZS electrical separations (elec↔water/gas 25mm, elec↔hot 300mm).
- ❌ REFUTED (drop): duct 1in all-round clearance; duct-always-yields-around-beam; feeder 600mm halvable.

## ✅ DONE 2026-06-22 (all three NEXT items)
1. **RE-VERIFIED the 🟡 rows** (deep-research run wsog87r4b, 25 claims, 10 confirmed/15 killed):
   - ✅ DRAIN-holds (gravity fixed-fall, BS EN 12056) + STRUCT-holds = VERIFIED who-yields.
   - 🟡→❌ full largest/rigid LADDER stays PENDING (specific sequence REFUTED 1-2/0-3); BG6 = framework only.
   - ✅ AS/NZS 3000 seps PROMOTED: elec↔water/gas 25mm, elec↔telecom 50mm (3-0).
   - ❌ ceiling-void STACKING dropped (sprinkler-highest AND duct-highest both REFUTED 0-3 → no `stackZ`).
   - ❌ tray↔hot 300mm AS/NZS attribution REFUTED → advisory. Promoted in BOTH the doc + `mep_coordination.js`.
2. **WIRED into RouteWalker** (`deploy/dev/routewalker.js`, current 1090-line wall-snap version — local bim-ootb
   was 46 commits stale; deploy/dev ≡ origin/main): `rwCoordinate()` (disc-vs-disc arbitration) +
   `rwClearStructure()` (FP↔STRUCT 50mm NFPA, the one VERIFIED-enforced clearance) + disc-aware tol on
   `_rwClashesWithArc`. Revert-safe (opt-in; emit paths untouched). Provenance-gated: acts only on VERIFIED.
3. **DEMO**: SampleCastle has NO baked rooms (and Terminal won't route in the JS port — anchors are FIXTURE/VALVE,
   patterns need METER/JUNCTION) → demoed on the PROVEN **SampleHouse**: `scripts/witness_mep_coordination_route.js`
   **W-MEP-COORD-ROUTE 5/5** (S1 FP↔STRUCT enforced 50mm on real walls raw5→resolved0; S2 non-FP touch-ok delta;
   S3 real ACMV×ELEC 19 crossings ADVISORY no-move; S4 provenance gate; S5 deterministic). W-MEP-COORD 21/21.
   SHIPPED: bim-compiler commit `6ddf97e9` (lane/benchmark-clash-resolution); bim-ootb **PR #477** sw v696 (auto-merge).

## FOLLOW-ON (optional, not blocking)
- Wire `rwClearStructure`/`rwCoordinate` into the LIVE emit path (currently opt-in) — changes placement behaviour,
  needs its own witness + care (don't drop pipes that were fine). 
- If the priority LADDER ever gets a normative source (not single-blog), promote ACMV/FP/DWATER ordering → enforced.

## COMMANDS
- canvas proof: `node scripts/rosetta_canvas_sh.js [Building] [origExtraction.db]`
- bake envelopes: `node scripts/build_mep_arc_envelope.js`
- coord rules test: `node scripts/test_mep_coordination.js`
