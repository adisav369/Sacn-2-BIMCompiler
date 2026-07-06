# ⚠⚠ NEW SESSION STARTS HERE — MEP STUDY AFRESH (authored 2026-06-21 on user decree)

## ═══ #1 GUARD RAIL — READ FIRST, NEVER VIOLATE ═══
**The Rosetta Stone reconstruction truth lives in JAVA. DO NOT EVER reinvent it. DO NOT EVER commit a hand-rolled
JS/Python reconstruct yardstick again.**
- `RosettaStoneGateTest` (DAGCompiler/src/test/java/com/bim/compiler/contract/RosettaStoneGateTest.java) G1–G6
  ALREADY prove the compiler round-trips **IFC → BOM → compile → output → reconstruct == source, LOSSLESSLY.**
  The BOM IS the faithful, exact encoding of the building. SH is a proven stone.
- To check ANY building's reconstruction, **RUN THE JAVA GATE**:
  `./scripts/run_RosettaStones.sh classify_sh.yaml`  (classify_*.yaml live in `IFCtoBOM/target/classes/`).
- What went WRONG (2026-06-21, do not repeat): I hand-rolled a per-element Python reconstruct check with my OWN
  corner→center + bbox-match heuristic, got a FALSE "0.70 m drift", and cast false doubt on tested-perfect Java
  code — then wrote that false finding into memory. It was reverted (commit c3c9c30a). That is the NON-INVENT prime
  directive broken. See [[feedback_read_java_spec_first]] + [[feedback_stop_on_invent_not_instruct]].
- RULE: when fidelity is "already proven in Java", TRUST + RUN the Java proof. Never build a parallel yardstick.

## STUDY AFRESH (start clean — discard my polluted conclusions from the prior session)
The goal the user set: wet rooms + complex disciplines (FP, ACMV) on a rich building (Terminal), and HOW the
disciplines coordinate (which routes first, by regulation). Restart the study from the Java truth, test afresh:

1. **Re-establish reconstruction truth via the JAVA gate** (not a reinvented one):
   - Run `./scripts/run_RosettaStones.sh classify_sh.yaml` and read its result. That is the SH fidelity answer.
   - For Terminal: find/derive its classify yaml (`IFCtoBOM/target/classes/classify_*.yaml`) and run the gate to
     establish whether Terminal reconstructs (memory said Terminal reconstruct was "never done" — VERIFY via the
     gate, do not assume, do not reinvent).
2. **THEN** the MEP direction (only once reconstruction is trusted from the Java gate):
   - Terminal is the rich building: 340 real MEP anchors; wet-room recipes ALREADY exist in mep_rw.db
     `ad_space_type_mep_bom` (BATHROOM/KITCHEN/WET_KITCHEN/TOILET_BLOCK/PUMP_ROOM/PLANT…).
   - Gap to bake (same pipeline as SH/Duplex): Terminal's `building_room` + `arc_envelope` + `building_origin`
     sidecars in mep_rw.db (build_mep_room_envelopes.js / build_mep_arc_envelope.js).
   - NEW work = multi-discipline COORDINATION by regulation (which discipline holds its path, which yields):
     research the priority order + governing codes (gravity drainage first → large ACMV duct → FP sprinkler mains
     → pressurized CW/HW/gas → electrical/cable-tray/data last; "gravity>pressure, big>small, rigid>flexible").
     Then encode that priority into the clash gate (extend "vs walls" → "vs higher-priority disciplines").

## ALREADY DONE / LIVE (do NOT redo) — SH "all systems go" RENDER lane is complete
- Fixtures auto-place on SH drop (bim-ootb PR#463 v685); per-discipline COLOUR (PR#464 v686); X-ray glass→glow
  reveal (PR#465 v687); progressive WALK+whoosh (PR#466 v688); wall-host fixtures SNAP to real arc_envelope walls
  (PR#467 v689, routewalker.js?v=5). All whitebox §-log + puppeteer witnessed. bim-compiler backups on
  feat/erp-substrate-phase012 (pushed, 0 local-only).
- These are RENDER/placement-of-MEP features over the existing BOM. They do NOT re-prove structural reconstruction
  — that is the Java Rosetta's job (guard rail above).
