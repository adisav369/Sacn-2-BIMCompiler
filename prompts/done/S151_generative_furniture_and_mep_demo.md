# S151 — Generative Furniture + MEP Discipline Automation Demo

**Prior work:** S150/S151 committed (`b219cdc3`). SpaceScheduleDAO, MEPDevicePlacer, PLACE_DEVICE verb, generative walk proof. 329 DX elements (215 extracted + 114 generative). 14/14 MepRouteGeometryTest. 0 gaps across 27 space types.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Compute positions via verbs. Never invent.

## CRITICAL: Fix These Two Bugs First

Read `docs/DuplexAnalysis.md §S150/S151 Known Findings` for full context.

### Bug 1: Z-axis breach — bomAABB height = furniture extent, not room height

13/114 generative devices breach their room Z-axis. The BOM AABB height comes from `childBom.getAabbHeightMm()` which is the furniture bounding box (812mm sofa, 820mm vanity), NOT the room height (2700mm). Placement offsets (SWITCH at 1200mm, SINK at 850mm) exceed this.

**Proof from logs** (grep `GENERATIVE.*BREACH`):
```
BREACH SWITCH pos=Z=2.750 room=[1.550→2.362] X=OK Y=OK Z=OUT
BREACH SINK   pos=Z=2.400 room=[1.550→2.370] X=OK Y=OK Z=OUT
```

**Fix must read actual room height** from storey or spatial_structure, not from furniture AABB. The Rtree XY positions are correct — only Z is wrong.

### Bug 2: LOD geometry — Placement AABB hardcoded ±0.05m

In PlacementCollectorVisitor generative block, line `dp.position()[0] - 0.05, dp.position()[0] + 0.05` etc. creates a 0.1m cube for every device regardless of actual size. BuildingWriter scales the LOD mesh to fit this AABB. A FRIDGE (0.7×0.7×1.8m) gets squashed to 0.1m. Fix: read M_Product dimensions from ERP.db (`width`, `depth`, `height` columns) and use those for the Placement min/max.

### User Nuances (Do Not Forget)

1. **Rtree preview works, Full Load doesn't** — the positions (anchor + offset) are computed correctly. The problem is downstream: BuildingWriter geometry binding reads the Placement AABB to scale LOD meshes. Fix the Placement AABB, not the position.

2. **LODs look blocky** — box stubs (`f7051d6c5f17ad77`, 8 vertices) are used for products with zero extraction geometry (SWITCH, OUTLET, FRIDGE, etc.). These will always be boxes. Products with real extraction geometry (SPRINKLER, SINK, TOILET, LIGHT) resolve via LIKE match and should look correct once the Placement AABB is fixed.

3. **Log-first debugging** — do NOT guess at fixes. Improve logging to reveal the issue, run the pipeline, read the logs. The GENERATIVE channel (grep `GENERATIVE`) already has ROOM/PLACE/BREACH/SUMMARY. Add more if needed before changing any maths.

4. **Products are NOT missing from input** — SINK, TOILET, LIGHT, SPRINKLER etc. have real IFC geometry in component_library.db under extraction names (e.g., `005_915x535_single_end_bowl_sink`). The LIKE lookup in `ComponentLibrary.getByName(familyRef)` resolves them. Only truly novel products (SWITCH, OUTLET, FRIDGE, AIRCON_POINT) need box stubs.

5. **Schema snapshot** — `library/schema_snapshot_bom.sql` was updated to add `C_BPartner_ID` on C_Order. ComponentLibrary.getByName() was guarded against NULL orientation/attachment_face from extraction rows.

## What S150/S151 Already Built (Committed)

All in commit `b219cdc3`. Do not rebuild — extend.

## Two Capabilities This Session Proves

### A. Furniture Placed by Knowing Its Space (Fridge Proof)

A fridge is IfcFurnishingElement (furniture) AND an MEP endpoint (needs ELECTRIFIED + optionally PLUMBABLE). In the abstract model, the fridge has no MEP identity — the ROOM does. A KITCHEN with capability ELECTRIFIED+PLUMBABLE gets a FRIDGE from the furniture schedule, and the room's MEP schedule independently places the outlet that powers it.

The existing infrastructure:
- `ad_space_type_furniture` maps KITCHEN → KITCHEN_CABINET_SET (furniture BOM)
- `ad_space_type_mep_bom` maps KITCHEN → OUTLET_20A, SINK, SPRINKLER, etc. (MEP schedule)
- `Filler.fill()` creates PHANTOM children in SET BOMs to fill gaps between real furniture
- `findBestFitAnyOwner()` finds BOMs that fit within an AABB

**The gap:** No FRIDGE product or BOM exists. No mechanism links PHANTOM gaps to generative furniture placement. The furniture schedule (`ad_space_type_furniture`) is a static BOM reference, not a generative placement chain.

**What to build:** Extend `ad_space_type_furniture` to include individual furniture items (not just the set BOM) with placement rules — same pattern as `ad_space_type_mep_bom`. A KITCHEN schedules 1 FRIDGE (rule=WALL_SIDE, fallback=PHANTOM_GAP). The generative placer checks PHANTOM gaps for space, computes position, places.

### B. Discipline Automation Demo (Sprinkler + Outlet)

The S150 gap analysis found OUTLET_GFCI and SPRINKLER are not in the original DX IFC file — they're scheduled in `ad_space_type_mep_bom` but have no M_Product. The killer demo:

1. User YAML order says `AD_Org: MEP, qty: 99` (standard coverage)
2. Compiler walks every room, reads MEP schedule from metadata
3. For BATHROOM: places SPRINKLER at ceiling center, OUTLET_GFCI above sink
4. These devices were NEVER in the original IFC — the compiler generated them from rules
5. The output.db has more MEP elements than the input IFC

This proves the compiler is **generative**, not just extractive. It compiles a building from an order + rules, adding code-required devices that the architect didn't explicitly model.

## What S150 Already Built

- `SpaceScheduleDAO` — reads schedule + offsets + capabilities from ERP.db
- `MEPDevicePlacer.placeDevices()` — computes positions from room AABB + schedule
- `MEPDevicePlacer.analyseGaps()` — reports missing products with actionable INSERTs
- `PlacementCollectorVisitor` — wired: `setErpConn()` + generative expansion in `onSubAssembly()`
- `PLACE_DEVICE:` verb prefix in `expandVerb()`
- MepRouteGeometryTest S10-S12: 12/12 PASS

## What This Session Must Do

### Task 1: Create Missing Products (SPRINKLER, OUTLET_GFCI, FRIDGE)

Add products to ERP.db so the generative path can place them. These are BUY products (leaf elements, no children).

```sql
-- S151: Products for generative MEP + furniture placement
INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, is_active,
    aabb_width, aabb_depth, aabb_height)
VALUES
    ('SPRINKLER', 'SPRINKLER', 'Fire Sprinkler Head', 'FIXTURE', 1, 0.10, 0.10, 0.15),
    ('OUTLET_GFCI', 'OUTLET_GFCI', 'GFCI Outlet', 'FIXTURE', 1, 0.07, 0.04, 0.12),
    ('FRIDGE', 'FRIDGE', 'Refrigerator', 'FIXTURE', 1, 0.70, 0.70, 1.80);
```

The dimensions are standard catalogue sizes. The compiler doesn't care about exact dimensions for placement (AABB comes from the room, not the product) — these are for volume estimation and LOD mesh binding.

### Task 2: Extend Furniture Schedule

Add FRIDGE to the kitchen furniture schedule. Two options:

**Option A:** Add to `ad_space_type_mep_bom` (since fridge is an MEP endpoint):
```sql
INSERT OR IGNORE INTO ad_space_type_mep_bom
    (space_type_id, mep_product_id, qty_normal, placement_rule, host_surface, anchor_end)
VALUES ('KITCHEN', 'FRIDGE', 1, 'WALL_SIDE', 'FLOOR', 'PANEL');
```

**Option B:** Create `ad_space_type_furniture_item` table for individual furniture (not the set BOM):
```sql
CREATE TABLE IF NOT EXISTS ad_space_type_furniture_item (
    space_type_id   TEXT NOT NULL,
    product_id      TEXT NOT NULL,
    qty_normal      INTEGER DEFAULT 1,
    placement_rule  TEXT DEFAULT 'WALL_SIDE',
    min_gap_width   REAL DEFAULT 0,    -- minimum PHANTOM gap width (metres)
    min_gap_depth   REAL DEFAULT 0,    -- minimum PHANTOM gap depth (metres)
    PRIMARY KEY (space_type_id, product_id)
);
INSERT INTO ad_space_type_furniture_item VALUES
    ('KITCHEN', 'FRIDGE', 1, 'WALL_SIDE', 0.60, 0.60);
```

**Decision:** Option A is simpler (reuse existing MEP schedule). The fridge is treated as a FLOOR-placed MEP device with anchor=PANEL (needs electrical outlet). The MEPDevicePlacer already handles this. Option B is cleaner long-term but adds schema.

Recommend: **Option A for this session** (one row in existing table). Document in spec that furniture-as-MEP-endpoint is valid. Revisit Option B if more furniture items need scheduling.

### Task 3: PHANTOM Gap Awareness

Currently `MEPDevicePlacer.placeDevices()` places devices at metadata-computed positions regardless of whether space is available. For FLOOR-placed items (FRIDGE, TOILET), the device should respect PHANTOM gaps:

1. Read PHANTOM children from the room's SET BOM
2. Each PHANTOM has an AABB (dx/dy/dz from the BOM line)
3. If the device's AABB fits in a PHANTOM gap, place inside the gap
4. If no gap fits, place at the metadata-computed position (fallback)

This is a refinement, not a blocker. The metadata position is correct by building code — PHANTOM gap awareness adds furniture-collision avoidance.

### Task 4: Wire Order Qty from YAML

The YAML order has `discipline_counts.MEP: 904` (or a qty on the discipline OrderLine). The walker needs to read this as the `mepOrderQty` parameter:

1. In `CompilationPipeline` or `OrderLineProductCallout`, when processing MEP order lines:
   - Read the order qty from the discipline OrderLine
   - Pass to `PlacementCollectorVisitor.setMepOrderQty(qty)`
2. qty=99 → standard coverage (place normal qty per room)
3. qty=0 → maximum coverage (fill to code maximum)
4. The walker then calls `MEPDevicePlacer.placeDevices()` for every room SET BOM

### Task 5: Demo Test — DX with Generative MEP

The proof that discipline automation works:

1. Take DX (Duplex) — already has rooms classified (2 KITCHEN, 2 BATHROOM, etc.)
2. Set erpConn on the walker, mepOrderQty=99
3. Walk DX through the normal pipeline
4. Count output elements:
   - Original DX: 1119 elements (ARC + STR + existing MEP)
   - With generative MEP: 1119 + N new devices (SPRINKLER, OUTLET_GFCI, FRIDGE)
5. Verify: every BATHROOM has a SPRINKLER, every KITCHEN has a FRIDGE
6. Verify: every device position traces to `ad_placement_offset` source

This is the demo: **user orders a building with MEP, compiler adds code-required devices that weren't in the original IFC**. The output has more elements than the input.

### Task 6: Drift Measurement (Optional)

Compare generative device positions against Rosetta Stone IFC positions for buildings that DO have these devices (e.g., Hospital Auckland has sprinklers). The drift measures how well our metadata calibration matches real-world installations.

## Learning Points from S150

### A. The DAO + Verb + Walker pattern works

S150 proved the chain: DAO reads metadata → Verb computes position → Walker emits placement. S10 test verified 8/8 BATHROOM devices match metadata exactly.

### B. S9 had a subtle bug that S10's DAO fixed

S9 inline computation didn't trigger z_rule for FLOOR_LOW (z_offset=0, edge offsets=0 → fell to center). SpaceScheduleDAO.computePosition() correctly checks `zRule != null` — FLOOR_LOW at Z=0.0, not Z=1.35.

### C. Product gaps are a feature, not a bug

The 2 gaps (OUTLET_GFCI, SPRINKLER) represent **devices the compiler knows about but can't yet render**. Creating the products is a one-time setup. The schedule already says KITCHEN needs 2 GFCI outlets — the infrastructure is complete, only the product catalog entry is missing.

### D. Fridge as cross-domain proof

The fridge spans furniture (IfcFurnishingElement) and MEP (needs ELECTRIFIED outlet). Placing it from the space type schedule proves the abstract model handles cross-domain items without special-casing.

## Read First

1. `CLAUDE.md` + `PROGRESS.md` §Current State
2. `docs/DISC_VALIDATION_DB_SRS.md` §6.12.4 (Space Identity — all subsections)
3. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/SpaceScheduleDAO.java`
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/MEPDevicePlacer.java`
5. `DAGCompiler/src/main/java/com/bim/compiler/bom/walker/PlacementCollectorVisitor.java` — onSubAssembly generative block
6. `DAGCompiler/src/test/java/com/bim/compiler/contract/MepRouteGeometryTest.java` — S10-S12
7. `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java` — Filler.fill(), findBestFitAnyOwner()
8. Existing schedules: `ad_space_type_mep_bom`, `ad_space_type_furniture` in ERP.db

## Task List

Work one at a time. Run tests after each. Await review.

1. **Create missing products** — SPRINKLER, OUTLET_GFCI, FRIDGE in ERP.db via migration. Run S12 gap analysis to confirm gaps closed.
2. **Add FRIDGE to KITCHEN schedule** — one row in ad_space_type_mep_bom. Run S10 against KITCHEN to verify placement.
3. **Wire order qty from YAML** — CompilationPipeline reads MEP order qty, passes to walker. Default 99 (standard).
4. **Demo test: DX with generative MEP** — walk DX with erpConn set, count new generative elements, verify every room has its scheduled devices. This is the discipline automation demo.
5. **PHANTOM gap awareness** (refinement) — for FLOOR-placed items, check PHANTOM gaps before placing. Skip if no SET BOM children available.
6. **Gap listing for all room types** — run analyseGaps() across all active space types, emit master gap report.

## Gate

- MepRouteGeometryTest: 16/16 PASS (S10-S16)
- DX: 8/9 PASS (no regression) + 114 generative MEP elements
- S16 DX demo: 329 placements (215+114), 11 rooms, 0 breaches, 0 FALLBACKs
- KITCHEN has FRIDGE placed at WALL_FLOOR, Z=0 (floor)
- Every BATHROOM has SPRINKLER at ceiling center
- Every KITCHEN has OUTLET_GFCI at counter height
- All device positions trace to ad_placement_offset source
- No hardcoded distances, no room names in walker code
- Gap report: 0 gaps across 27 space types (S14)

# DONE

## Commits
- `e09294f8` — Bug 1 (Z breach) + Bug 2 (LOD AABB)
- `097c2ae6` — AABB log + S15 witness + close DuplexAnalysis findings
- `e97d8e03` — Wire MEP order qty + S16 DX demo + DV045 product dims

## Findings

**F1: PHANTOM gap awareness deferred.** DX SET BOMs have no BUFFER/PHANTOM children
(Filler.fill() not run). Feature requires gap-filled BOMs as prerequisite.

**F2: 9 products had zero dimensions.** CEILING_FAN, EXHAUST_FAN, FLOOR_TRAP, LIGHT,
OUTLET, SINK, SUPPLY_DIFFUSER, SWITCH, TOILET — all zeroes in M_Product. DV045 fills them.
Without DV045, Placement AABB falls back to 0.1m cube (logged as FALLBACK).

**F3: GENERATIVE log channel fully forensic.** Six trace points:
CEILING_OVERRIDE, ROOM, PLACE, AABB, BREACH, SUMMARY. grep `GENERATIVE` for full audit.

**F4: MEP order qty was hardcoded system property.** Now flows from YAML
(`mep_order_qty`) → `ad_sysconfig MEP_ORDER_QTY` → walker with fallback chain.
Source logged as `MEP_ORDER_QTY=99 source=default|ad_sysconfig|system.property`.
