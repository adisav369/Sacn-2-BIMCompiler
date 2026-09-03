# S155 — Device Spacing Rules + Descriptive Naming

**Prior work:** S154 delivered shim entities (IfcVirtualElement phantom + child device),
LOD geometry bridge (CL_001 migration), facing direction per wall, standoff per surface,
host IFC class in shim familyRef, S20 test with shim/surface/offset/facing assertions.
SH 9/9 with manual compile. DX 8/9 (36 pre-existing geometry scale failures, not S154).
See `internal/DISCSpecToCode.md` §Summary + §Gaps Found in S154.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE
**EXTRACT OR COMPILE ONLY.** Query the database. Copy patterns you find. Never invent.

## Development Cycle (README Mantra)
1. Follow specs before coding — read §12a-§12f in DISC_VALIDATION_DB_SRS.md
2. Write tests before coding — the test defines "done"
3. Analyse debug logs and review code to fix
4. If you need to change code, change specs first

## What S154 Left Undone (from internal/DISCSpecToCode.md)

### Priority 1: Descriptive Element Names (GAP-9)

Users hover over devices in Bonsai and see "TOILET" or "IfcFlowTerminal" —
meaningless. The IFC family name is already in ERP.db:

```
M_Product.source_element_ref = 'M_Water Closet - Flush Tank:Private - 6.1 Lpf'
```

The output DB `elements_meta.element_name` should carry this descriptive name.

**Root cause:** PlacementCollectorVisitor creates the Placement with
`familyRef = dp.deviceId()` (e.g. "TOILET"). BuildingWriter:1561 writes
`familyRef` as `element_name`. The descriptive `source_element_ref` is
never read from ERP.db during placement.

**Fix:**
1. In `placeGenerativeDevices()`, before creating the device Placement,
   query ERP.db: `SELECT source_element_ref FROM M_Product WHERE product_id = ?`
2. Pass `source_element_ref` as `familyRef` on the Placement record
3. BuildingWriter already writes familyRef → element_name — no change needed there

**Test:** Assert `elements_meta.element_name` contains ":" (IFC family format)
for all IfcFlowTerminal devices in output DB.

### Priority 2: Ceiling Device Spacing (GAP-10)

CEILING_CENTER dumps FAN + LIGHT + DIFFUSER at the exact same point.
`ad_code_requirement` has spacing rules (NFPA 13: sprinkler max_spacing=4.6m,
NEC: light min 1 per room) but the placer never reads them.

**Root cause:** `MEPDevicePlacer.placeDevices()` calls
`SpaceScheduleDAO.computePosition()` which uses `ad_placement_offset` only.
It never consults `ad_code_requirement.max_spacing` or `max_area_per_device`.

**Fix:**
1. After computing base position for a CEILING device, check if another device
   is already placed within `max_spacing / 2` of that position
2. If so, offset along room's dominant axis by `max_spacing / 2`
3. Read spacing from `ad_code_requirement WHERE element_type = ? AND space_type = ?`
4. Fallback: 0.5m minimum separation if no code rule exists

S154 added a 100mm co-location offset as interim fix. Replace it with
code-rule-based spacing.

**Test:** Assert no two devices in the same room share the same centroid
(within 50mm). This is the P05_NO_DUPLICATE_POSITION prover check.

### Priority 3: SH/DX P05 Proof Violations

S154's co-location offset (100mm per device at same position) is an interim fix.
With Priority 2 done, this offset should be unnecessary — spacing rules will
naturally separate devices. Verify SH and DX pass P05 without the interim offset.

### NOT in scope for S155
- §12c END-join route (S21 test) — deferred to S156
- §12b.5 tack-point walker read — deferred to S156
- §12g GAP-5 anchor discovery — deferred to S156
- DX 36 geometry scale failures (pre-existing, not generative devices)

## Gate

- SH: 9/9 PASS (no P05 violations)
- DX: 9/9 PASS (generative devices — DX geometry scale is pre-existing, not gate)
- S20: PASS with all shim assertions
- elements_meta.element_name contains descriptive IFC family name for all generative devices
- No two generative devices in the same room share a centroid (within 50mm)
- internal/DISCSpecToCode.md: GAP-9 and GAP-10 flipped to Y
