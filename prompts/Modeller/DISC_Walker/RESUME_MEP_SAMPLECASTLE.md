# RESUME — MEP demo on SampleCastle (user request 2026-06-22)

## ⚠ DO NOT REMOVE — scope + standing rules
Move the MEP coordination/route demo from SampleHouse to **SampleCastle**. NON-INVENT: rooms/envelopes/origin must
be EXTRACTED+baked from the real SC model, never fabricated. Read the log after every run. Whitebox §-log is the
proof. [[feedback_whitebox_deduce_not_browser]] [[feedback_read_java_spec_first]]

## WHY (user)
"the MEP demo i like it to be on SampleCastle." The current MEP lane (✅ DONE/LIVE 2026-06-22, `RESUME_MEP_COORDINATION.md`,
bim-ootb PR#477 sw v696) was demoed on **SampleHouse** as a fallback — because **SampleCastle has NO baked rooms**
and Terminal wouldn't route in the JS port. SC is the building the user actually wants the demo on.

## CURRENT STATE (verified)
- `deploy/buildings/SampleCastle_extracted.db` exists (7.9MB, ~3284 elements; IFC2x3 — colors via Node.js extractor,
  see [[feedback_extractor]] / project_sc_coloring). `SampleCastle_library.db` + `SampleCastle_positions.bin` present.
- **ARC envelopes baked earlier: SC 2372 ARC** (per MEP-coord memory). But **`building_room` NOT baked** for SC,
  and `arc_envelope`/`building_origin` sidecar status unconfirmed for the room layer.
- MEP route walker (`viewer/routewalker.js`) needs ROOMS (wet recipes per room: BATHROOM/KITCHEN/WET_KITCHEN…) to
  place fixtures + generate routes. No rooms → no fixtures → nothing to coordinate. That is the blocker.

## THE GAP / PLAN (strict, not yet executed)
1. **Read the room-bake path first** — how SH/Terminal `building_room` (+ `arc_envelope`, `building_origin`) sidecars
   are produced (find the bake script; SH has them, SC/Terminal don't). Confirm what input it needs from the
   extracted DB (spaces / IfcSpace / boundaries).
2. **Bake SampleCastle `building_room`** from `SampleCastle_extracted.db` (extract IfcSpace/boundaries → room
   polygons + storeys). If SC has no IfcSpace, that's the real blocker to surface (Terminal `building_room` was
   ⛔ unbakeable for that reason) — prove it from the DB, don't assume.
3. **Wire SC into the MEP demo** — fixtures auto-place on SC rooms, per-discipline colour, wall-snap to SC
   `arc_envelope`, coordination (rwCoordinate / rwClearStructure). Reuse the SH path; no redesign.
4. **Witness** W-MEP-COORD-ROUTE on **real SampleCastle** (mirror the SH witness), §-log proof of rooms→fixtures→
   routes→clash-gate. Honest fallback if SC wall model is partial (SH did 6/13 on real walls).

## TRAPS
- ⛔ Terminal `building_room` was declared **unbakeable** — verify SC isn't the same before promising the demo.
- Don't re-bake SC with the Python extractor (loses IFC2x3 colors — [[feedback_extractor]]). Use Node.js extraction.
- DOCTRINE: only ARC(PLACE)+STR(FRAME) are placed; FP/ELEC/ACMV/CW/SP/LPG are ROUTE/COVERING-generated.

## RELATED
- `prompts/RESUME_MEP_COORDINATION.md` (the landed SH demo) · MEMORY MEP-coordination line.
- Orthogonal to the drop-fidelity strict lane (`prompts/RESUME_DROP_VS_JAVA_STRICT.md`).
