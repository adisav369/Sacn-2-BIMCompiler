# S160 — Fridge/Floor-Standing Device Placement Fix

**Prior work:** S159 fixed floating switches (ScopeBomBuilder empty-room floor datum).
BPartnerCatalogTest written: 3/4 PASS (W-BPARTNER-COMPLETENESS fails — DV051 pending).

You are a coder for bim-compiler. One bounded task.

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Never invent. Spec before code, test before implement.

## Two Bugs — Floor-Standing Device Placement

### Bug 1 — Z: Fridge CENTER at floor instead of BOTTOM at floor

**Evidence (DX Kitchen A):**
- IFC extracted fridge: BOM Z = 1.550–3.380m, bottom=1.550m, center=2.465m ✓
- Generative fridge:    BOM Z = 0.650–2.450m, bottom=0.635m, center=1.550m ✗ (buried 0.928m)

**Root cause:** `SpaceScheduleDAO.computePosition`:
```java
case "FLOOR" -> minZ + entry.zOffset();  // zOffset=0 → CENTER at floor
```
For floor-standing devices, zOffset=0 means BOTTOM at floor, but code treats it as CENTER.

**Fix:** In `PlacementCollectorVisitor` after `getProductDimensions` (line ~650),
when `dp.hostSurface()` equals `"FLOOR"` and `prodDims != null`:
```java
// FLOOR-standing: zOffset is BOTTOM above floor, not CENTER
// pos[2] from computePosition = floor + zOffset (= floor for zOffset=0)
// Lift by half-height so bottom sits on floor
if ("FLOOR".equals(dp.hostSurface()) && prodDims != null) {
    pos[2] += prodDims[2] / 2.0;  // prodDims[2] = height in metres
}
```
Do this BEFORE building `deviceBox`. Only when `host_surface = "FLOOR"`.

**Witness:** W-FRIDGE-Z — generative fridge bottom Z = room anchor Z (within 5mm).
```
After fix: fridge AABB minZ ≈ 1.550m = kitchen anchor Z = 1.550m  ✓
```

### Bug 2 — Y: WALL_FLOOR places fridge at room CENTER-Y, should be back wall (MAX-Y)

**Evidence (DX Kitchen A):**
- IFC fridge Y: 11.008–11.813m → back wall (room maxY=11.813m) ✓
- Generative Y: 10.342–11.042m → room center Y=10.692m ✗

**Root cause:** `ad_placement_offset` for WALL_FLOOR:
```
WALL_FLOOR | from_edge_x=0.05 | from_edge_y=0.0 | yRef=CENTER | xRef=MIN
```
yRef=CENTER places fridge at room mid-Y. Fridge belongs at back wall (yRef=MAX).

**Fix:** Update `ad_placement_offset` for WALL_FLOOR:
```sql
UPDATE ad_placement_offset
SET y_ref = 'MAX', from_edge_y = 0.0
WHERE placement_rule = 'WALL_FLOOR';
```
Apply to `library/ERP.db` AND write as `migration/DV052_wall_floor_yref.sql`.

**Witness:** W-FRIDGE-Y — generative fridge center-Y within 50mm of room maxY minus half-depth.
```
Room maxY = 11.813m, fridge half-depth = 0.38m
Expected center Y ≈ 11.813 - 0.38 = 11.433m
IFC reference center Y = 11.411m  → delta = 22mm < 50mm ✓
```

## Test: MepRouteGeometryTest or new BPartnerCatalogTest extension

Add witness assertions to `MepRouteGeometryTest` or a new `FloorDevicePlacementTest`:

```java
// W-FRIDGE-Z: fridge bottom at room floor (within 5mm)
double kitchenAnchorZ = 1.550;  // DX_A103_SET anchor Z
double fridgeMinZ = ...; // query from duplex.db
assertEquals(kitchenAnchorZ, fridgeMinZ, 0.005, "W-FRIDGE-Z: fridge buried in floor");

// W-FRIDGE-Y: fridge near back wall (within 50mm of maxY - half-depth)
double roomMaxY = 11.813;
double fridgeHalfDepth = 0.38;
double fridgeCenterY = ...;
assertEquals(roomMaxY - fridgeHalfDepth, fridgeCenterY, 0.05, "W-FRIDGE-Y: wrong wall");
```

## Also Pending — DV051 (BPartnerCatalogTest 4/4)

BPartnerCatalogTest W-BPARTNER-COMPLETENESS currently FAILS.
After fridge fix, also write `migration/DV051_m_product_bpartner.sql`:
```sql
-- RE generative fixtures → Duplex (C_BPartner_ID=1)
UPDATE M_Product SET C_BPartner_ID = 1
WHERE product_id IN (
    'FRIDGE','OUTLET_20A','OUTLET_GFCI','OUTLET','CEILING_FAN','EXHAUST_FAN',
    'FLOOR_TRAP','SINK','SWITCH','TOILET','WASHING_TAP','AIRCON_POINT'
);
-- Universal → NULL (already NULL, just document)
-- SPRINKLER, LIGHT, SUPPLY_DIFFUSER, DATA_POINT, EMERGENCY_LIGHT → NULL
```
Apply to `library/ERP.db`. Then BPartnerCatalogTest 4/4 PASS.

## Gate
- DX 9/9 PASS (no regression)
- SH 9/9 PASS
- Generative fridge: bottom at floor, back at maxY wall
- BPartnerCatalogTest 4/4 PASS

## C_BPartner Doc (also deferred from S159)
- Add §C_BPartner to `docs/DuplexAnalysis.md` (RE/CO catalog, DX as RE reference)
- Add link from `docs/DISC_VALIDATION_DB_SRS.md §12h` → DuplexAnalysis.md
