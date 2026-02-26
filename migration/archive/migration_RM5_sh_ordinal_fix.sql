-- =============================================================================
-- Phase RM-5: Fix SH ad_geometry_map ordinals to match ad_element_placement
-- =============================================================================
-- SH geometry_map has mixed ordinal scheme:
--   IfcDoor (1-3) + IfcFurnishingElement (4-17) = global ordinals (correct)
--   IfcMember (1-20), IfcPlate (1-6), etc. = per-class ordinals (WRONG)
-- ad_element_placement uses global ordinals (1-55).
-- MeshBinder resolves by ordinal, so they MUST match.
-- Run against: library/component_library.db
-- =============================================================================

-- Use temp-range approach to avoid UNIQUE constraint conflicts during renumber.

-- IfcMember: ordinals 1-20 → 18-37 (offset +17)
UPDATE ad_geometry_map SET ordinal = ordinal + 10000
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcMember';
UPDATE ad_geometry_map SET ordinal = ordinal - 10000 + 17
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcMember';

-- IfcPlate: ordinals 1-6 → 38-43 (offset +37)
UPDATE ad_geometry_map SET ordinal = ordinal + 10000
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcPlate';
UPDATE ad_geometry_map SET ordinal = ordinal - 10000 + 37
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcPlate';

-- IfcRoof: ordinal 1 → 44
UPDATE ad_geometry_map SET ordinal = 44
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcRoof';

-- IfcSlab: two entries both at ordinal 1. Fix by row id.
-- id=47 (Floor:Floor-Grnd-Susp...) → ordinal 45
-- id=48 (Floor:Simple floor) → ordinal 46
UPDATE ad_geometry_map SET ordinal = 45
WHERE id = 47 AND building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcSlab';
UPDATE ad_geometry_map SET ordinal = 46
WHERE id = 48 AND building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcSlab';

-- IfcWall: ordinals 1-5 → 47-51 (offset +46)
UPDATE ad_geometry_map SET ordinal = ordinal + 10000
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcWall';
UPDATE ad_geometry_map SET ordinal = ordinal - 10000 + 46
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcWall';

-- IfcWindow: ordinals 1-4 → 52-55 (offset +51)
UPDATE ad_geometry_map SET ordinal = ordinal + 10000
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcWindow';
UPDATE ad_geometry_map SET ordinal = ordinal - 10000 + 51
WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcWindow';

-- Mark all SH geometry_map entries as EXTRACTED provenance
UPDATE ad_geometry_map SET provenance = 'EXTRACTED'
WHERE building_type = 'Ifc4_SampleHouse';

-- ─── Verification ──────────────────────────────────────────────────────────
-- After running, verify all 55 placements match:
-- SELECT ep.ifc_class, ep.placement_id, gm.ordinal, gm.geometry_hash
-- FROM ad_element_placement ep
-- LEFT JOIN ad_geometry_map gm ON ep.building_type = gm.building_type
--     AND ep.ifc_class = gm.ifc_class AND ep.placement_id = gm.ordinal
-- WHERE ep.building_type = 'Ifc4_SampleHouse'
-- ORDER BY ep.placement_id;
