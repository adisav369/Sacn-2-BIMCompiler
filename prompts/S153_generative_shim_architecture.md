# S153 — Generative Device Shim Architecture + END-Join Route

**Prior work:** S152 committed. Generative MEP pipeline works — DX 9/9 (329=215+114), SH 9/9 (82=58+24). LOD geometry bridged for all 11 products. Discipline resolver fixed (SPRINKLER→FP, SUPPLY_DIFFUSER→ACMV). Tack-to points seeded in DV047. Specs written: §6.12.4 §11-§12.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Terminology (S152 correction)
- **Input:** IFC file, YAML order, ERP.db rules/schedules
- **Output:** Walker-compiled c_orderline with positions — ARC/STR from BOM walk, MEP from Walker
- No "extracted vs generative" in the output. All buildings are compiled. Input sources differ.

## CRITICAL: Development Cycle (README Mantra)
1. **Follow specs before coding.** Read the relevant SRS section.
2. **Write tests before coding.** The test defines "done".
3. **Analyse debug logs and review code to fix.** If logs don't reveal the issue, improve logging first.
4. **If you need to change code, change specs first.** Then back to step 1.

## What S152 Found (Visual Inspection of SH Output)

1. **Toilet inside cupboard** — PLACE_DEVICE ignores sibling furniture LEAFs. Cupboard is an ARC element already placed. The generative device uses only room AABB, not the occupied zones within it.

2. **Toilet not facing right** — no rotation on generative devices. No shim → no wall-normal → no facing direction. The fixture is axis-aligned to world, not to the host wall.

3. **Ceiling devices overlap** — LIGHT, SPRINKLER, CEILING_FAN, SUPPLY_DIFFUSER all at same position (room center, 50mm below ceiling). Each ceiling placement rule resolves to the same point. Needs spatial deconfliction — same furniture collision logic applies to ceiling devices.

4. **Sprinklers and lights are under the curved roof but above the ceiling slab** — correct Z placement (aligned to bottom of ceiling at 3.119m). They already have the right host surface semantics.

5. **Parametric boxes on floor/wall** — outlets show as parametric AABB (proper shape pending LOD bridge now fixed). Floor-level boxes may be from the AABB fallback renderer.

## The Architectural Decision (from S152 triage)

**All three findings resolve from one change: generative devices must go through shims, not bare AABB placement.**

Currently `MEPDevicePlacer.placeDevices()` computes positions directly from room AABB + `ad_placement_offset`. The correct approach:

1. Create phantom SHIM on target wall/ceiling/floor (host surface from placement_rule)
2. Check wall zone for existing furniture (sibling LEAFs under same SET BOM) — shift if occupied
3. Attach device as child of SHIM (offset = standoff distance, e.g. 5mm)
4. Facing inherited from shim's wall normal — no rotation math needed
5. Device's tack-to point (ad_assembly_connector) gives pipe the connection target
6. Walker generates END-join route from infrastructure anchor to tack-to point
7. Last piece is VARIABLE-length (InterimWorkshop §6) — halt before overshoot, trim to exact length

## Specs to Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.4 §11 (LOD bridge), §12a-§12f (shim architecture, tack points, END-join, collision, test specs)
3. `docs/DISC_VALIDATION_DB_SRS.md` §5 (MEP BOM — shim root, joint piece children)
4. `docs/DISC_VALIDATION_DB_SRS.md` §6 (InterimWorkshop — variable-length pieces)
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` lines 365-470 (current generative block)
6. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/MEPDevicePlacer.java` (current placer)
7. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/SpaceScheduleDAO.java` (schedule + offset queries)
8. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/ShimMatcher.java` (existing shim logic for extracted MEP)

## What This Session Must Do

### Phase 1: Write Tests First (Mantra Step 2)

Implement the test specs from §12f:
- **S20 (W-SHIM-DEVICE):** Assert generative devices have parent shim, no furniture overlap
- **S21 (W-END-JOIN):** Assert pipe routes to tack-to point, no overshoot, VARIABLE terminal piece
- **S22 (W-TACK-POINT):** Assert ad_assembly_connector has non-placeholder entries for all fixtures
- **S23 (W-DISC-RESOLVE):** Assert discipline matches connects_to (already passing after S152 fix)

S20/S21 will FAIL before the shim refactor. That's the point.

### Phase 2: Refactor PLACE_DEVICE to Use Shims (Mantra Step 3)

Refactor `PlacementCollectorVisitor` generative block:
1. Instead of `MEPDevicePlacer.placeDevices()` returning bare positions, it should create shim BOM lines + device children
2. Shim host surface from `ad_placement_offset.z_rule` (CEILING → ceiling shim, FLOOR → floor shim, WALL → wall shim)
3. Furniture collision check against sibling LEAFs
4. Device offset from shim = standoff (5mm wall-mounted, 50mm ceiling-mounted)

### Phase 3: END-Join Route Generation

After fixture placement, generate pipe route to tack-to point:
1. Read `ad_assembly_connector` for fixture's connection point
2. Find nearest infrastructure anchor (from `connects_to` mapping)
3. Generate standard-length segments from anchor toward tack-to
4. Halt at last mile — create VARIABLE terminal piece via InterimWorkshop
5. Assert convergence within 1mm

## Gate

All new tests (S19-S23) must run against BOTH SH and DX BOM databases.
Do not assume SH-only or DX-only — both buildings exercise different room
types, different furniture layouts, and different ceiling geometries.
Run `./scripts/run_RosettaStones.sh classify_sh.yaml classify_dx.yaml`
and verify both pipelines green with generative shims.

- SH: 9/9 PASS + generative devices have shim parents
- DX: 9/9 PASS + generative devices have shim parents
- S20 (SH + DX): PASS — all generative devices use shim, no furniture overlap
- S21 (SH + DX): PASS — pipe routes converge on tack-to within 1mm, no overshoot
- S22: PASS — all fixtures have non-placeholder tack points
- S23 (SH + DX): PASS — discipline matches connects_to
- MepRouteGeometryTest: 22+/22+ PASS (S1-S18 existing + S19-S23 new)
- GENERATIVE log (SH + DX): shim creation + furniture collision check logged
- Visual check: reload SH and DX in Bonsai — devices not inside furniture, facing correct walls
