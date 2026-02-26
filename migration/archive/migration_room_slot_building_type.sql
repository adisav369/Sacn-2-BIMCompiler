-- Add building_type column to ad_room_slot for building isolation.
-- NULL = global slot (applies to all buildings, backward-compatible).
-- Non-NULL = applies only to the named building_id.

BEGIN TRANSACTION;

ALTER TABLE ad_room_slot ADD COLUMN building_type TEXT DEFAULT NULL;

-- Tag SH-specific assemblies — they were globally visible and could fire in DX
UPDATE ad_room_slot
SET building_type = 'Ifc4_SampleHouse'
WHERE assembly_id IN ('SH_LIVING_SET', 'SH_DINING_SET', 'SH_BED_SET');

-- Verify: SELECT slot_id, room_type, assembly_id, building_type FROM ad_room_slot
--         WHERE building_type IS NOT NULL;
-- Expected: 3 rows (slots 60, 61, 62) all with building_type='Ifc4_SampleHouse'

COMMIT;
