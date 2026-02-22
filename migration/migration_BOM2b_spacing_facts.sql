-- =============================================================================
-- Phase BOM-2b: Spacing Facts for BOM Anchor Orderlines
-- =============================================================================
-- Each BOM anchor Orderline (discipline='FURN', host_type='ROOM') must carry
-- the SPACING FACTS of the space it occupies — the room's actual bbox dimensions.
--
-- ERP analogy: C_OrderLine attributes = X/Y/Z position + space product dims.
-- The "space product" for a ROOM-level BOM is the room itself.
-- Dims are EXTRACTED from ad_room_boundary — never invented.
--
-- Before this migration:
--   width_mm = 0, depth_mm = 0, height_extent_mm = 0  (missing spacing facts)
-- After:
--   width_mm  = room width  (max_x_mm - min_x_mm)
--   depth_mm  = room depth  (max_y_mm - min_y_mm)
--   height_extent_mm = room height (max_z_mm - min_z_mm)
--
-- BOM Drop source: migration_RM6_bom_anchors.sql (Phase BOM-1)
-- GGF/GF catalog: migration_BOM2a_lod_and_ggf.sql (Phase BOM-2a)
-- =============================================================================

-- ad_room_boundary stores XY bounds (min/max _mm) + z_offset_mm (floor Z).
-- No ceiling Z stored — height_extent uses standard residential storey height (2700mm).
UPDATE ad_element_rule
SET
    width_mm         = (SELECT rb.max_x_mm - rb.min_x_mm
                        FROM ad_room_boundary rb
                        WHERE rb.building_type = ad_element_rule.building_type
                          AND rb.room_name     = ad_element_rule.host_ref
                        LIMIT 1),
    depth_mm         = (SELECT rb.max_y_mm - rb.min_y_mm
                        FROM ad_room_boundary rb
                        WHERE rb.building_type = ad_element_rule.building_type
                          AND rb.room_name     = ad_element_rule.host_ref
                        LIMIT 1),
    height_mm        = 0.0,      -- furniture sits on floor; mounting height above floor = 0
    height_extent_mm = 2700.0   -- standard residential clear height; updated per storey when known
WHERE discipline = 'FURN'
  AND host_type  = 'ROOM'
  AND is_active  = 1;
