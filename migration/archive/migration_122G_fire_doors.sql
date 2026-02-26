-- Migration 122G: Fire Door Families + Wall-Length Window Scaling
-- Phase 122G: Increase Terminal door/window count to match reference
--
-- Reference gaps:
--   Windows: 57 output vs 236 reference (need ~4x)
--   Doors: 39 output vs 135 reference (need ~3.5x)
--
-- Strategy: Add fire door families + FIRE_EXIT role for institutional rooms.
-- Window qty scaling is handled in Java (PER_EXTERIOR_WALL → wall-length-based).

-- =========================================================================
-- 1. New Fire Door Families (matching reference dimension signatures)
-- =========================================================================

-- FD1 fire door 750mm — matches reference 750×2100×150mm (24 count)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_FIRE_DOOR_750', 'Institutional Fire Door 750mm', 'DOOR', 'IfcDoor',
     750, 2100, 1,
     'FD1 fire-rated single leaf 750mm for escape routes', 1, 150);

-- FD4 fire door double 1500mm — matches reference 1500×2100×147mm (13 count)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_FIRE_DOOR_DOUBLE', 'Institutional Fire Door Double 1500mm', 'DOOR', 'IfcDoor',
     1500, 2100, 1,
     'FD4 fire-rated double leaf 1500mm for corridors', 1, 147);

-- FD2 fire door wide 1800mm — matches reference 1800×2100×177mm (9 count)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_FIRE_DOOR_WIDE', 'Institutional Fire Door Wide 1800mm', 'DOOR', 'IfcDoor',
     1800, 2100, 1,
     'FD2 fire-rated wide double leaf 1800mm for lobby exits', 1, 177);

-- Wider single door 1050mm — matches reference 1050×2175×200mm (10 count)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_DOOR_1050', 'Institutional Door 1050mm', 'DOOR', 'IfcDoor',
     1050, 2175, 0,
     'Institutional wider single door for larger rooms', 1, 200);

-- =========================================================================
-- 2. FIRE_EXIT Role Assignments (Malaysian_Institutional)
-- =========================================================================
-- These add a second door (fire exit) to institutional rooms that already
-- have an ENTRY door. The Java code distributes doors across different walls.

-- CORRIDOR: double fire door exits (FD4) — corridors are escape routes
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('CORRIDOR', 'FIRE_EXIT', 'INST_FIRE_DOOR_DOUBLE', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- LOBBY: wide fire exit (FD2) — main escape route from lobbies
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('LOBBY', 'FIRE_EXIT', 'INST_FIRE_DOOR_WIDE', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- OPEN_PLAN: fire exit single (FD1 750mm)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OPEN_PLAN', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- DINING: fire exit single (FD1 750mm)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('DINING', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- OFFICE: fire exit single (FD1 750mm) — larger offices need fire escape
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OFFICE', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- STAFFROOM: fire exit single (FD1 750mm)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('STAFFROOM', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- TOILET_BLOCK: fire exit single (FD1 750mm)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('TOILET_BLOCK', 'FIRE_EXIT', 'INST_FIRE_DOOR_750', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     'UBBL', 'Part VII', 50, 1, 'Malaysian_Institutional');

-- =========================================================================
-- Verification queries (run manually)
-- =========================================================================
-- SELECT * FROM ad_opening_family WHERE family_id LIKE 'INST%';
-- SELECT * FROM ad_space_type_opening WHERE profile = 'Malaysian_Institutional' ORDER BY space_type_id, opening_role;
