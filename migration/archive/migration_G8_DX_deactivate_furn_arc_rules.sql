-- ============================================================
-- migration_G8_DX_deactivate_furn_arc_rules.sql
-- Deactivate ARC discipline element_rules with FURN_ family_refs
-- for Ifc2x3_Duplex ROOM_Level_* grid rooms.
--
-- Root cause:
--   migration_G8_DX_restore_grid_rooms.sql re-activated 715
--   DX ROOM_Level_* rules. It intentionally kept FURN ROOM rules
--   (is_active=0) but inadvertently missed the ARC discipline
--   rules that ALSO carry FURN_ family_refs (FURN_SOFA,
--   FURN_KITCHEN_BASE, FURN_COFFEE_TABLE, etc.).
--
--   These 48 ARC rules produce BBox-only furniture geometry via
--   the ARC path instead of the BOM cascade path, causing:
--     - X1 gate failure: 48 BBox-only FURN elements
--     - dx_s3_furnitureInEnvelope: furniture outside envelope
--     - dx_furnitureNotBunchedByStorey: Dining_Chair/Sofa bunching
--
-- Surfaced by: BuildingInspector preflight Check F (2026-02-23)
--
-- Idempotent: UPDATE WHERE is_active=1 (re-apply = no-op after first run)
-- ============================================================

BEGIN TRANSACTION;

UPDATE ad_element_rule
SET    is_active = 0
WHERE  building_type = 'Ifc2x3_Duplex'
  AND  discipline    = 'ARC'
  AND  family_ref LIKE 'FURN_%'
  AND  host_ref   LIKE 'ROOM_Level_%';

-- Verify: SELECT COUNT(*) should be 0 after this migration
-- SELECT COUNT(*) FROM ad_element_rule
-- WHERE building_type='Ifc2x3_Duplex' AND is_active=1
-- AND discipline='ARC' AND family_ref LIKE 'FURN_%' AND host_ref LIKE 'ROOM_Level_%';

COMMIT;
