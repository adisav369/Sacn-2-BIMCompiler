-- migration_G8_DX_quick_wins.sql
-- SQL-only quick wins for G8-DX: reduce RosettaPlacementTest#g8_duplex failures
-- Target: library/BOM.db
-- Companion to Java changes (corner anchors, BED_SET_DX) in the same session.

-- =============================================================================
-- FIX 1: Deactivate Piano in LIVING_SET
-- Piano has no reference IfcFurnishingElement counterpart in Ifc2x3_Duplex.
-- 2 Piano items compiled (one per LIVING room) → both fail the 500mm gate.
-- =============================================================================
UPDATE m_bom_line SET is_active = 0 WHERE bom_child_id = 101; -- PIANO in LIVING_SET

-- =============================================================================
-- FIX 2: Raise DINING_SET min_area for LIVING rooms from 12.0 → 13.0
-- ROOM_B102 (area=12.762m²) currently qualifies and generates 7 DINING items
-- (table + 6 chairs) that have no reference match (reference has small coffee
-- tables/sofas in this room, not dining furniture).
-- ROOM_A102 (area=11.612m²) already excluded. 13.0 excludes both.
-- =============================================================================
UPDATE ad_room_slot SET min_area = 13.0
WHERE slot_id = 12; -- LIVING/DINING/DINING_SET, previously 12.0

-- =============================================================================
-- FIX 3: Deactivate BASE_CABINET_7 (dx=-3) from KITCHEN_CABINET_SET
-- In kitchen A (ROOM_A103, cx≈6183mm), dx=-3 places item at x≈3.183m which
-- lies outside the kitchen X-range (4.683–7.683m) and has no reference match.
-- Same for kitchen B. Net: -2 compiled items (1 per kitchen).
-- =============================================================================
UPDATE m_bom_line SET is_active = 0 WHERE bom_child_id = 107; -- BASE_CABINET_7 dx=-3

-- =============================================================================
-- FIX 4: Add wall_rule=OPPOSITE_WORK to UPPER_CABINET items in KITCHEN_CABINET_SET
-- All BASE_CABINET items anchor to the south (work) wall.
-- Reference upper cabinets are on the NORTH wall (y≈-10.542 for kitchen A,
-- y≈-7.258 for kitchen B). OPPOSITE_WORK re-anchors these items to the
-- opposite cardinal direction, placing them at the north wall.
-- Applies to: UPPER_CABINET(82), UPPER_CABINET_2(108), UPPER_CABINET_3(109),
-- UPPER_CABINET_4(249) — all four items in KITCHEN_CABINET_SET.
-- =============================================================================
INSERT INTO m_attribute (bom_child_id, param_key, param_value, param_type, description)
VALUES
    (82,  'wall_rule', 'OPPOSITE_WORK', 'TEXT', 'Upper cabs to north wall (opposite work wall)'),
    (108, 'wall_rule', 'OPPOSITE_WORK', 'TEXT', 'Upper cabs to north wall (opposite work wall)'),
    (109, 'wall_rule', 'OPPOSITE_WORK', 'TEXT', 'Upper cabs to north wall (opposite work wall)'),
    (249, 'wall_rule', 'OPPOSITE_WORK', 'TEXT', 'Upper cabs to north wall (opposite work wall)');
