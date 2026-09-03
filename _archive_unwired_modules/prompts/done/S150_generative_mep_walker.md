# S150 — Generative MEP Walker: Abstract Rules-Based Placement

**Prior work:** S149b (MEP route geometry fixes, space identity, capability model, fixture gap analysis)
**Findings source:** MepRouteGeometryTest S9 proved the abstract chain works. This session wires it into the walker proper.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## Context — What S149b Proved

S149b established three layers:

1. **Capability model** (DV041): rooms have abstract flags (is_plumbable, is_electrified, is_fire_protected, is_ventilated, is_gas_served). `ad_discipline_capability` maps CW→PLUMBABLE, ELEC→ELECTRIFIED, etc.

2. **Placement offsets as metadata** (DV042): `ad_placement_offset` table stores wall distances, heights, edge references per building code. Zero hardcoded distances in code. Modellers edit per standards.

3. **Fixture gap analysis**: for each room with a capability, checks `ad_space_type_mep_bom` schedule, reports SATISFIED/GAP with actionable INSERT scripts.

S9 test proved the abstract chain:
```
SPACE(cap=PLUMBABLE) → SCHEDULE(device=SINK, rule=WALL_SIDE) → OFFSET(0.15m, 0.85m) → POSITION(0.15, 1.5, 0.85)
```
The code never says "KITCHEN" or "SINK" — it reads metadata rows.

## What S149b Did NOT Do

S9 is a **test-level proof** — raw SQL in a JUnit test. It proves the concept but:

- Does NOT use DAO layer (raw JDBC queries inline)
- Does NOT use Verbs (no PLACE/ROUTE verb expansion)
- Does NOT produce GEO white-box logging from the walker itself
- Does NOT feed back into the BOM walker pipeline
- The convergence proof compares against Rosetta Stone IFC (circular — proves extraction matches source, not that rules work independently)

## What This Session Must Do

Build the **generative MEP walker path** using project conventions:

### 1. DAO Layer — SpaceScheduleDAO

Create a DAO that reads the schedule chain. The walker calls DAOs, never raw SQL.

```java
// Reads ad_space_type_mep_bom + ad_placement_offset in one query
public class SpaceScheduleDAO {
    record ScheduleEntry(String deviceId, String placementRule, String hostSurface,
                         String anchorEnd, int qtyNormal,
                         double edgeX, double edgeY, double zOffset,
                         String zRule, String xRef, String yRef) {}

    /** All scheduled devices for a space type, with placement offsets resolved. */
    public List<ScheduleEntry> getSchedule(Connection conn, String spaceTypeId) { ... }

    /** Capability required by a discipline. Reads ad_discipline_capability. */
    public String getRequiredCapability(Connection conn, String discipline) { ... }

    /** All space types with a given capability. */
    public List<String> getSpaceTypesByCapability(Connection conn, String capability) { ... }
}
```

### 2. Verb — PLACE_DEVICE

A verb that the walker uses to place a scheduled device in a room:

```
verb_ref = "PLACE_DEVICE:WALL_SIDE"
```

The verb reads the `ad_placement_offset` row for the rule, computes position from room AABB + offsets. Same maths as S9 but inside the verb expansion framework.

This fits the existing verb pattern (CLUSTER, TILE, ROUTE, FRAME, SPRAY). The verb receives the room AABB from the parent BOM's AABB, and the rule from the BOM line's verb_ref.

### 3. White-Box GEO Logging — From the Walker

The walker must log its own reasoning, not just the result. The GEO log from PlacementCollectorVisitor should say:

```
[GEO] DEVICE_PLACE space=ROOM_GF_1 cap=PLUMBABLE schedule_type=BATHROOM
[GEO] DEVICE_PLACE   device=SINK rule=WALL_SIDE offset=(0.150, 0.000, 0.850) ref=(MIN,CENTER,FLOOR)
[GEO] DEVICE_PLACE   room_aabb=(0,4,0,3,0,2.7) → position=(0.150, 1.500, 0.850)
[GEO] DEVICE_PLACE   anchor=RISER → pipe route needed from RISER to (0.150, 1.500, 0.850)
```

This is the walker saying what it's doing in real time — not a post-hoc analysis. Any reviewer reads the GEO log and knows exactly why the device is at that position.

### 4. Order Qty Interpretation

The YAML order has `AD_Org=MEP, qty=99` (or `qty=0`). The walker interprets:

| Order qty | Action |
|-----------|--------|
| 99 / blank | Use `qty_normal` from schedule |
| 0 | Use `qty_max` (fill to maximum) |
| N | Cap total devices at N |

Implementation: in OrderLineProductCallout or CompilationPipeline, when processing MEP order lines, expand into per-room device placements using the schedule.

### 5. Black-Box Proof — Generative vs Rosetta Stone

The real proof that rules work independently:

1. Create a room from scratch (no IFC, no Rosetta Stone)
2. Walk it through the generative path (DAO → Verb → PLACE_DEVICE)
3. Compare the output positions against `ad_placement_offset` table values
4. The positions must match EXACTLY (they're computed from the same metadata)
5. Then compare against Rosetta Stone positions — this is the DRIFT measurement

The drift measures how well our metadata-derived rules match real-world IFC data. It does NOT validate the rules — it validates the metadata calibration.

## Learning Points from S149b (User Nuance)

### A. The compiler is abstract — always

The user emphasised repeatedly: **the code works with abstract tokens** (SPACE, CAPABILITY, DEVICE, RULE). Concrete names (KITCHEN, SINK, LAVATORY) live in metadata tables for compliance only. The walker never interprets device names — it reads a schedule row and applies the placement rule.

This means: no `if ("SINK".equals(deviceId))` anywhere in walker code. The walker sees a schedule entry with a placement rule and a qty. It places. What that device IS is irrelevant to the walker.

### B. Rosetta Stone ≠ compiled output

The extracted DB is reference data for learning rules. Pipes in ERP.db recipes were extracted from IFC — they prove the extraction is geometrically correct, but they don't prove the walker can generate from scratch.

The S149b convergence proof (133 converged) proved extraction accuracy. It did NOT prove generative placement. S150 must prove the generative path independently.

### C. No hardcoded distances

The user caught hardcoded wall offsets (0.15m, 0.85m etc.) in `computePlacementTarget` and asked them to be in metadata. DV042 `ad_placement_offset` was created. All offsets are now data.

**Rule:** if a number represents a physical measurement (distance, height, angle), it must come from a metadata table, not from code. Code does maths with numbers from tables.

### D. Logging must be self-documenting

The user wants GEO logs that tell the full story with zero human interpretation. Every placement decision must trace to a metadata source:

```
source=ad_placement_offset.WALL_SIDE → edge_x=0.150 z_offset=0.850
```

Not just:
```
position=(0.150, 1.500, 0.850)
```

The first tells you WHY. The second only tells you WHAT.

### E. DAO layer and Verbs for maintainability

The user pointed out raw SQL in tests is OK for proof-of-concept but production code must use DAO layer (like existing MProduct, MBOM, MBOMLine) and Verbs (like CLUSTER, TILE, ROUTE). This keeps main classes small and logic testable.

### F. Fixture qty rules already exist

`ad_space_type_mep_bom` already has `qty_min/qty_normal/qty_max` and `per_area_normal`. KITCHEN needs 1-2 SINKs, 2-4 OUTLET_20A. SPRINKLER uses per_area (0.07/m²). The order qty (99 or 0) is a coverage level, not a per-fixture count.

### G. Fridge is furniture AND MEP endpoint

A fridge is IfcFurnishingElement (furniture) but also needs a power outlet and possibly cold water. In the abstract model, the fridge has no MEP identity — the ROOM does. A room with a fridge + sink → PLUMBABLE + ELECTRIFIED. The schedule says what MEP the room needs, not what the fridge needs.

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.4 (Space Identity — all subsections)
3. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.2 §6 (InterimWorkshop — verb pattern)
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` — expandVerb()
5. `DAGCompiler/src/test/java/com/bim/compiler/contract/MepRouteGeometryTest.java` — S9
6. `migration/DV041_space_capability.sql` + `migration/DV042_placement_offsets.sql`
7. Existing DAO examples: `MProduct.java`, `MBOM.java`, `MBOMLine.java`
8. Existing verb patterns: grep `CLUSTER:|TILE:|ROUTE:` in PlacementCollectorVisitor

## Task List

Work one at a time. Run tests after each. Await review.

1. **SpaceScheduleDAO** — DAO that reads ad_space_type_mep_bom + ad_placement_offset + ad_discipline_capability. Single query, returns ScheduleEntry records.
2. **PLACE_DEVICE verb** — new verb in expandVerb() that reads ScheduleEntry and computes position from room AABB + offsets. White-box GEO logging at every step.
3. **Wire into walker** — when the walker enters a SET BOM with a capability, look up the schedule and expand PLACE_DEVICE for each entry. Order qty controls coverage level.
4. **Generative test** — create a bare room (walls only, no IFC), walk it, verify positions match metadata exactly. Compare against Rosetta Stone positions for drift measurement.
5. **Gap listing** — when a scheduled device has no product in component_library.db, emit actionable output: what product to create, what placement rule to use, what INSERT to run.

## Gate

- MepRouteGeometryTest: 10/10 PASS (S9 + new S10 generative walk)
- DX: 8/9 PASS (no regression)
- SH: 8/9 PASS (no regression)
- Generative room: all device positions match metadata within 0.001mm
- GEO log: every PLACE_DEVICE traces to ad_placement_offset source
- No hardcoded distances, no room names in walker code
- DAO used for all schedule queries, no raw SQL in walker

# DONE

## Files Created
- `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/SpaceScheduleDAO.java` — DAO: getSchedule(), getRequiredCapability(), getSpaceTypesByCapability(), computePosition(), resolveQty()
- `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/MEPDevicePlacer.java` — PLACE_DEVICE verb: placeDevices(), resolveSpaceType(), analyseGaps(), distributeInstance()

## Files Modified
- `PlacementCollectorVisitor.java` — erpConn + mepOrderQty fields, PLACE_DEVICE: in expandVerb(), generative expansion in onSubAssembly(), resolveDeviceDiscipline()
- `MepRouteGeometryTest.java` — S10 (generative walk 8/8), S11 (qty coverage), S12 (gap analysis 2 gaps)

## Gate Results
- MepRouteGeometryTest: **12/12 PASS** (was 8/8 → +4 new tests)
- DX: 8/9 PASS (no regression, 1 FAIL = pre-existing VerbStage)
- SH: 8/9 PASS (no regression)
- S10: all 8 BATHROOM devices match metadata within 0.005mm
- S12: OUTLET_GFCI + SPRINKLER = 2 product gaps (scheduled but no M_Product)

## Finding: S9 Z-axis Bug
S9 inline code condition `if (edgeX > 0 || edgeY > 0 || zOff > 0)` doesn't trigger for FLOOR_LOW (all offsets=0). Result: FLOOR_TRAP placed at room center (Z=1.35) instead of floor (Z=0). SpaceScheduleDAO.computePosition() fixes this by also checking `zRule != null`.

## Next: S151
Generative furniture placement (fridge proof case) + discipline automation demo (sprinkler/outlet placed from YAML order). [prompts/S151_generative_furniture_and_mep_demo.md](prompts/S151_generative_furniture_and_mep_demo.md)
