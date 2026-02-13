-- Migration 122E: Institutional Opening Families for Terminal Convergence
-- Phase 122E: Add curtain wall windows and institutional doors
-- Target: Terminal ARC score 9% → higher
--
-- Reference gaps:
--   65 × 1310x3450mm curtain wall windows (0 in output)
--   23 × 900x2175mm institutional doors (0 in output)
--   12 × 950x2175mm institutional doors (0 in output)

-- =========================================================================
-- 1. New Opening Families
-- =========================================================================

-- Curtain wall window — full-height glass panel for terminal lobbies/departure
-- Reference signature: 1312×223×3449mm (65 count in Terminal)
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_CURTAIN_WINDOW', 'Institutional Curtain Wall Window', 'WINDOW', 'IfcWindow',
     1310, 3450, 0,
     'Full-height curtain wall glass panel for institutional lobbies', 1, 223);

-- Institutional standard door 900mm — fire-rated, matching reference 900x2175
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_DOOR_900', 'Institutional Door 900mm', 'DOOR', 'IfcDoor',
     900, 2175, 1,
     'Standard institutional single-leaf door (fire rated)', 1, 200);

-- Institutional office door 950mm — fire-rated, matching reference 950x2175
INSERT OR IGNORE INTO ad_opening_family
    (family_id, family_name, opening_type, ifc_class,
     default_width_mm, default_height_mm, is_fire_rated,
     description, is_active, depth_mm)
VALUES
    ('INST_DOOR_950', 'Institutional Door 950mm', 'DOOR', 'IfcDoor',
     950, 2175, 1,
     'Institutional single-leaf office door (fire rated)', 1, 200);

-- =========================================================================
-- 2. Profile-Specific Space Type Opening Entries (Malaysian_Institutional)
-- =========================================================================
-- Two-pass resolution: these override generic entries for the same role.
-- Priority 50 = higher priority than generic (100).

-- LOBBY windows: curtain wall for terminal lobbies (arrival, departure, etc.)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('LOBBY', 'NATURAL_LIGHT', 'INST_CURTAIN_WINDOW', 'PER_EXTERIOR_WALL', 1,
     NULL, NULL, 0,
     'exterior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- LOBBY doors: institutional entry
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('LOBBY', 'ENTRY', 'INST_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- OPEN_PLAN windows: curtain wall for check-in counters, gates
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OPEN_PLAN', 'NATURAL_LIGHT', 'INST_CURTAIN_WINDOW', 'PER_EXTERIOR_WALL', 1,
     NULL, NULL, 0,
     'exterior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- OPEN_PLAN doors: institutional entry
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OPEN_PLAN', 'ENTRY', 'INST_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- DINING windows: curtain wall for canteen/food court
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('DINING', 'NATURAL_LIGHT', 'INST_CURTAIN_WINDOW', 'PER_EXTERIOR_WALL', 1,
     NULL, NULL, 0,
     'exterior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- DINING doors: institutional entry
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('DINING', 'ENTRY', 'INST_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- OFFICE doors: institutional office door 950mm
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('OFFICE', 'ENTRY', 'INST_DOOR_950', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- STAFFROOM doors: institutional office door 950mm
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('STAFFROOM', 'ENTRY', 'INST_DOOR_950', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- CORRIDOR windows: curtain wall for terminal corridors with exterior walls
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('CORRIDOR', 'NATURAL_LIGHT', 'INST_CURTAIN_WINDOW', 'PER_EXTERIOR_WALL', 1,
     NULL, NULL, 0,
     'exterior', 'CENTER', 0,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- TOILET_BLOCK doors: institutional standard door 900mm
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base,
     override_width_mm, override_height_mm, sill_height_mm,
     placement_wall, placement_rule, is_fire_rated,
     building_code, code_clause, priority, is_active, profile)
VALUES
    ('TOILET_BLOCK', 'ENTRY', 'INST_DOOR_900', 'FIXED', 1,
     NULL, NULL, 0,
     'interior', 'CENTER', 1,
     NULL, NULL, 50, 1, 'Malaysian_Institutional');

-- =========================================================================
-- Verification queries (run manually)
-- =========================================================================
-- SELECT * FROM ad_opening_family WHERE family_id LIKE 'INST%';
-- SELECT * FROM ad_space_type_opening WHERE profile = 'Malaysian_Institutional';
