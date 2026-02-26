-- Phase 109B: House Design Templates + Variants POC
-- Date: 2026-02-10
-- Purpose: Add house unit type templates (HOUSE_3BR, HOUSE_2BR, DUPLEX_GROUND,
--          DUPLEX_UPPER) with fractional room layouts, single-storey building
--          template, and activate dormant 2BR_A rooms.

-- ============================================================
-- PART 1: Building template — single-storey house
-- ============================================================

INSERT OR IGNORE INTO ad_building_template
    (template_id, template_name, building_type, description,
     default_floors, min_floors, max_floors, typical_floor_start,
     typical_floor_end, structural_system, code_reference, is_active)
VALUES
    ('LANDED_1S', 'Single-Storey House', 'RESIDENTIAL', 'Single-storey landed',
     1, 1, 1, 1, 1, 'FRAMED', 'UBBL', 1);

-- ============================================================
-- PART 2: Unit types — 4 house variants
-- ============================================================

INSERT OR IGNORE INTO ad_unit_type
    (unit_type_id, unit_name, unit_category, typical_area,
     bedroom_count, bathroom_count, has_balcony, has_utility,
     room_layout, mep_load, is_active)
VALUES
    ('HOUSE_3BR', '3-Bedroom House', 'LANDED', 110.0,
     3, 1, 0, 0,
     '{"porch":25,"common":50,"master_bed":17,"bed_2":17,"bathroom":6,"storage":6}',
     '{"elec_kw":8,"plumb_lpm":30}', 1),

    ('HOUSE_2BR', '2-Bedroom Compact', 'LANDED', 85.0,
     2, 1, 0, 0,
     '{"porch":18,"common":40,"master_bed":10,"bed_2":7,"bathroom":5}',
     '{"elec_kw":6,"plumb_lpm":25}', 1),

    ('DUPLEX_GROUND', 'Duplex Ground Floor', 'LANDED', 67.0,
     0, 1, 0, 0,
     '{"foyer":20,"living":28,"kitchen":13,"bathroom":4,"stair":5}',
     '{"elec_kw":5,"plumb_lpm":20}', 1),

    ('DUPLEX_UPPER', 'Duplex Upper Floor', 'LANDED', 67.0,
     2, 1, 0, 0,
     '{"hallway":10,"bedroom_1":23,"bedroom_2":23,"bathroom_2":5,"utility":4}',
     '{"elec_kw":5,"plumb_lpm":20}', 1);

-- ============================================================
-- PART 3: Room templates — HOUSE_3BR (TB-LKTN single-storey, 13m×8.5m)
-- ============================================================

INSERT OR REPLACE INTO ad_unit_type_room
    (unit_type_id, room_key, room_type, frac_min_x, frac_min_y,
     frac_max_x, frac_max_y, needs_window, opens_to, is_active)
VALUES
    ('HOUSE_3BR', 'porch',      'PORCH',     0.47, 0.00, 0.75, 0.27, 0, NULL,     1),
    ('HOUSE_3BR', 'common',     'OPEN_PLAN', 0.23, 0.27, 0.75, 1.00, 1, NULL,     1),
    ('HOUSE_3BR', 'master_bed', 'BEDROOM',   0.00, 0.27, 0.23, 0.81, 1, 'common', 1),
    ('HOUSE_3BR', 'bathroom',   'BATHROOM',  0.00, 0.81, 0.23, 1.00, 1, 'common', 1),
    ('HOUSE_3BR', 'bed_2',      'BEDROOM',   0.75, 0.27, 1.00, 0.81, 1, 'common', 1),
    ('HOUSE_3BR', 'storage',    'STORAGE',   0.75, 0.81, 1.00, 1.00, 1, 'common', 1);

-- ============================================================
-- PART 4: Room templates — HOUSE_2BR (compact variant, 10m×8.5m)
-- ============================================================

INSERT OR REPLACE INTO ad_unit_type_room
    (unit_type_id, room_key, room_type, frac_min_x, frac_min_y,
     frac_max_x, frac_max_y, needs_window, opens_to, is_active)
VALUES
    ('HOUSE_2BR', 'porch',      'PORCH',     0.40, 0.00, 0.70, 0.25, 0, NULL,     1),
    ('HOUSE_2BR', 'common',     'OPEN_PLAN', 0.00, 0.25, 0.70, 1.00, 1, NULL,     1),
    ('HOUSE_2BR', 'master_bed', 'BEDROOM',   0.70, 0.25, 1.00, 0.65, 1, 'common', 1),
    ('HOUSE_2BR', 'bed_2',      'BEDROOM',   0.70, 0.65, 1.00, 0.85, 1, 'common', 1),
    ('HOUSE_2BR', 'bathroom',   'BATHROOM',  0.70, 0.85, 1.00, 1.00, 1, 'common', 1);

-- ============================================================
-- PART 5: Room templates — DUPLEX_GROUND (foyer-spine, 4m×17m per unit)
-- ============================================================

INSERT OR REPLACE INTO ad_unit_type_room
    (unit_type_id, room_key, room_type, frac_min_x, frac_min_y,
     frac_max_x, frac_max_y, needs_window, opens_to, is_active)
VALUES
    ('DUPLEX_GROUND', 'foyer',    'CORRIDOR',  0.00, 0.00, 0.27, 1.00, 0, NULL,      1),
    ('DUPLEX_GROUND', 'living',   'LIVING',    0.27, 0.00, 1.00, 0.55, 1, 'foyer',   1),
    ('DUPLEX_GROUND', 'kitchen',  'KITCHEN',   0.27, 0.55, 1.00, 0.78, 0, 'foyer',   1),
    ('DUPLEX_GROUND', 'bathroom', 'BATHROOM',  0.27, 0.78, 0.64, 0.92, 0, 'kitchen', 1),
    ('DUPLEX_GROUND', 'stair',    'STAIR',     0.00, 0.78, 0.27, 1.00, 0, 'foyer',   1);

-- ============================================================
-- PART 6: Room templates — DUPLEX_UPPER (bedrooms + hallway, 4m×17m per unit)
-- ============================================================

INSERT OR REPLACE INTO ad_unit_type_room
    (unit_type_id, room_key, room_type, frac_min_x, frac_min_y,
     frac_max_x, frac_max_y, needs_window, opens_to, is_active)
VALUES
    ('DUPLEX_UPPER', 'hallway',    'CORRIDOR',       0.00, 0.44, 0.25, 1.00, 0, NULL,      1),
    ('DUPLEX_UPPER', 'bedroom_1',  'MASTER_BEDROOM', 0.25, 0.00, 1.00, 0.50, 1, 'hallway', 1),
    ('DUPLEX_UPPER', 'bedroom_2',  'BEDROOM',        0.25, 0.50, 1.00, 1.00, 1, 'hallway', 1),
    ('DUPLEX_UPPER', 'bathroom_2', 'BATHROOM',       0.00, 0.00, 0.25, 0.32, 0, 'hallway', 1),
    ('DUPLEX_UPPER', 'utility',    'UTILITY',        0.00, 0.32, 0.25, 0.44, 0, 'bathroom_2', 1);

-- ============================================================
-- PART 7: Activate dormant 2BR_A room templates
-- ============================================================

UPDATE ad_unit_type_room SET is_active = 1 WHERE unit_type_id = '2BR_A';
