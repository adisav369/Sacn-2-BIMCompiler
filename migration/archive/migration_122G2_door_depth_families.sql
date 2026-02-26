-- Migration 122G2: Additional door families for Terminal convergence
-- Phase 122G: Fix door depth matching + new family assignments
--
-- Reference gaps found after 122G:
--   900×2100×150mm fire door (13 ref) — FD1 at 900mm, thinner than INST_DOOR_900
--   1050×2175×200mm (10 ref) — wider single, family exists but not assigned

-- FD1 fire door at 900mm width, 150mm depth (distinct from INST_DOOR_900 at 200mm)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_FIRE_DOOR_900', 'Institutional Fire Door 900mm', 'DOOR', 'IfcDoor',
     900, 2100, 1,
     'FD1 fire-rated single leaf 900mm (thinner frame)', 1, 150);

-- Assign INST_DOOR_1050 to DINING rooms (wider entry for high-traffic dining)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('DINING', 'EGRESS', 'INST_DOOR_1050', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- Assign INST_FIRE_DOOR_900 to LOBBY as secondary fire exit
-- (different from ENTRY which uses INST_DOOR_900 at 200mm depth)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('LOBBY', 'EGRESS', 'INST_FIRE_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- Assign INST_FIRE_DOOR_900 to OPEN_PLAN as egress
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OPEN_PLAN', 'EGRESS', 'INST_FIRE_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');
