-- Phase 108C: Fix Metadata Gaps — Orphan Names + Missing Coverage
-- Date: 2026-02-10
-- Purpose: Fix naming mismatches, add missing opening/MEP specs for uncovered space types
--
-- Survey found:
--   ad_space_type_mep_bom: STORE→STORAGE, TOILET→TOILET_BLOCK (orphans)
--   ad_space_type_opening: LAUNDRY→WET_KITCHEN, STUDY→OFFICE (orphans)
--   ad_space_dim: MECHANICAL/PLANT/UTILITY (no ad_space_type row)
--   14 space types missing opening specs
--   22 space types missing MEP BOM specs

-- ============================================================
-- PART 1: Fix orphan naming in ad_space_type_mep_bom
-- ============================================================

-- STORE → STORAGE (5 rows)
UPDATE ad_space_type_mep_bom SET space_type_id = 'STORAGE'
    WHERE space_type_id = 'STORE';

-- TOILET → TOILET_BLOCK (4 rows)
UPDATE ad_space_type_mep_bom SET space_type_id = 'TOILET_BLOCK'
    WHERE space_type_id = 'TOILET';

-- ============================================================
-- PART 2: Fix orphan naming in ad_space_type_opening
-- ============================================================

-- LAUNDRY → WET_KITCHEN (alias mapping says LAUNDRY→WET_KITCHEN)
UPDATE ad_space_type_opening SET space_type_id = 'WET_KITCHEN'
    WHERE space_type_id = 'LAUNDRY';

-- STUDY → OFFICE (alias mapping says STUDY→OFFICE)
-- But OFFICE already has ENTRY+NATURAL_LIGHT rows. Delete STUDY duplicates.
DELETE FROM ad_space_type_opening WHERE space_type_id = 'STUDY';

-- ============================================================
-- PART 3: Add missing ad_space_type rows for dim-only types
-- ============================================================

-- These exist in ad_space_dim but not ad_space_type.
-- Add them so the type registry is complete.
INSERT OR IGNORE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule) VALUES
    ('UTILITY',    'SERVICE', '13-21 21 00', 'ENCLOSED'),
    ('PLANT',      'SERVICE', '13-21 21 00', 'ENCLOSED'),
    ('MECHANICAL', 'SERVICE', '13-21 21 00', 'ENCLOSED');

-- Also add to ad_space_type_furniture (all NONE — utility rooms)
INSERT OR IGNORE INTO ad_space_type_furniture (space_type_id, furniture_bom_id, fallback_rule) VALUES
    ('UTILITY',    NULL, 'NONE'),
    ('PLANT',      NULL, 'NONE'),
    ('MECHANICAL', NULL, 'NONE');

-- ============================================================
-- PART 4: Fill missing ad_space_type_opening specs
-- Rules: Every habitable room needs ENTRY door.
--        Rooms with exterior walls get windows per pattern.
--        Utility/circulation get minimal openings.
-- ============================================================

-- ASSEMBLY_HALL: large room, needs entry + natural light
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('ASSEMBLY_HALL', 'ENTRY',         'DOUBLE_LEAF',  'FIXED', 1, 'interior'),
    ('ASSEMBLY_HALL', 'NATURAL_LIGHT', 'LARGE_WINDOW', 'PER_EXTERIOR_WALL', 1, 'exterior');

-- CANTEEN: entry door + windows (same pattern as DINING)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('CANTEEN', 'ENTRY',         'RES_INTERNAL',  'FIXED', 1, 'interior'),
    ('CANTEEN', 'NATURAL_LIGHT', 'STD_WINDOW',    'PER_EXTERIOR_WALL', 1, 'exterior');

-- STAFFROOM: same as OFFICE (internal door + std window)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('STAFFROOM', 'ENTRY',         'RES_INTERNAL', 'FIXED', 1, 'interior'),
    ('STAFFROOM', 'NATURAL_LIGHT', 'STD_WINDOW',   'PER_EXTERIOR_WALL', 1, 'exterior');

-- STUDY_NOOK: small room, internal door + std window
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('STUDY_NOOK', 'ENTRY',         'RES_INTERNAL', 'FIXED', 1, 'interior'),
    ('STUDY_NOOK', 'NATURAL_LIGHT', 'STD_WINDOW',   'PER_EXTERIOR_WALL', 1, 'exterior');

-- OPEN_PLAN: entry door + large window (open office/living)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('OPEN_PLAN', 'ENTRY',         'RES_INTERNAL',  'FIXED', 1, 'interior'),
    ('OPEN_PLAN', 'NATURAL_LIGHT', 'LARGE_WINDOW',  'PER_EXTERIOR_WALL', 1, 'exterior');

-- WET_KITCHEN: entry door + ventilation window (from old LAUNDRY rows, now merged)
-- Already has rows from LAUNDRY rename above. Skip if exists.

-- GARAGE: vehicle door (wide), no window
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('GARAGE', 'ENTRY', 'DOUBLE_LEAF', 'FIXED', 1, 'exterior');

-- GENERIC: minimal — 1 entry door, no window (catch-all)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('GENERIC', 'ENTRY', 'RES_INTERNAL', 'FIXED', 1, 'interior');

-- Semi-exterior types: PORCH, CAR_PORCH, VERANDAH — open-air, no doors/windows needed
-- (no insert — intentionally empty)

-- Transit types: CONCOURSE, DEPARTURE_LOUNGE, GATE — large public spaces
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall)
VALUES
    ('CONCOURSE',        'ENTRY', 'DOUBLE_LEAF', 'FIXED', 1, 'interior'),
    ('DEPARTURE_LOUNGE', 'ENTRY', 'DOUBLE_LEAF', 'FIXED', 1, 'interior'),
    ('DEPARTURE_LOUNGE', 'NATURAL_LIGHT', 'LARGE_WINDOW', 'PER_EXTERIOR_WALL', 1, 'exterior'),
    ('GATE',             'ENTRY', 'DOUBLE_LEAF', 'FIXED', 1, 'interior');

-- ============================================================
-- PART 5: Fill missing ad_space_type_mep_bom specs
-- Rules: Every enclosed room needs emergency lighting.
--        Rooms >6m² get sprinkler (unless excluded by fire code).
--        Habitable rooms get at least switch + outlet.
-- Extracted from existing patterns, NOT imagined.
-- ============================================================

-- CANTEEN: same as KITCHEN minus plumbing (handled by fixtures)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('CANTEEN', 'CEILING_FAN',     1),
    ('CANTEEN', 'LIGHT',           0),   -- 0 = per-area auto
    ('CANTEEN', 'SPRINKLER',       0),
    ('CANTEEN', 'SUPPLY_DIFFUSER', 1),
    ('CANTEEN', 'OUTLET',          4),
    ('CANTEEN', 'SWITCH',          2);

-- DINING: same MEP as LIVING (already has openings but not MEP BOM)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('DINING', 'CEILING_FAN',     1),
    ('DINING', 'LIGHT',           2),
    ('DINING', 'OUTLET',          3),
    ('DINING', 'SPRINKLER',       0),
    ('DINING', 'SUPPLY_DIFFUSER', 1),
    ('DINING', 'SWITCH',          2);

-- STAFFROOM: same as OFFICE
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('STAFFROOM', 'AIRCON_POINT',    1),
    ('STAFFROOM', 'CEILING_FAN',     1),
    ('STAFFROOM', 'DATA_POINT',      0),
    ('STAFFROOM', 'LIGHT',           0),
    ('STAFFROOM', 'OUTLET',          0),
    ('STAFFROOM', 'SPRINKLER',       0),
    ('STAFFROOM', 'SUPPLY_DIFFUSER', 1),
    ('STAFFROOM', 'SWITCH',          2);

-- OPEN_PLAN: same as OFFICE (larger space, auto-qty)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('OPEN_PLAN', 'AIRCON_POINT',    1),
    ('OPEN_PLAN', 'CEILING_FAN',     1),
    ('OPEN_PLAN', 'DATA_POINT',      0),
    ('OPEN_PLAN', 'LIGHT',           0),
    ('OPEN_PLAN', 'OUTLET',          0),
    ('OPEN_PLAN', 'SPRINKLER',       0),
    ('OPEN_PLAN', 'SUPPLY_DIFFUSER', 1),
    ('OPEN_PLAN', 'SWITCH',          2);

-- STUDY_NOOK: minimal office-like
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('STUDY_NOOK', 'CEILING_FAN',     1),
    ('STUDY_NOOK', 'LIGHT',           1),
    ('STUDY_NOOK', 'OUTLET',          2),
    ('STUDY_NOOK', 'SPRINKLER',       0),
    ('STUDY_NOOK', 'SUPPLY_DIFFUSER', 1),
    ('STUDY_NOOK', 'SWITCH',          1);

-- LIFT_LOBBY: similar to LOBBY (already exists) — emergency light + sprinkler
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('LIFT_LOBBY', 'CEILING_FAN',      1),
    ('LIFT_LOBBY', 'EMERGENCY_LIGHT',  1),
    ('LIFT_LOBBY', 'LIGHT',            0),
    ('LIFT_LOBBY', 'SPRINKLER',        0),
    ('LIFT_LOBBY', 'SUPPLY_DIFFUSER',  1);

-- GENSET_ROOM: emergency light + exhaust only (no sprinkler per code — fuel hazard)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('GENSET_ROOM', 'EMERGENCY_LIGHT', 1),
    ('GENSET_ROOM', 'EXHAUST_FAN',     1),
    ('GENSET_ROOM', 'LIGHT',           1),
    ('GENSET_ROOM', 'OUTLET',          1),
    ('GENSET_ROOM', 'SWITCH',          1);

-- TNB_ROOM: emergency light only (electrical switchroom — no water-based suppression)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('TNB_ROOM', 'EMERGENCY_LIGHT', 1),
    ('TNB_ROOM', 'LIGHT',           1),
    ('TNB_ROOM', 'SWITCH',          1);

-- PUMP_ROOM: emergency light + outlet for pump controls
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('PUMP_ROOM', 'EMERGENCY_LIGHT', 1),
    ('PUMP_ROOM', 'LIGHT',           1),
    ('PUMP_ROOM', 'OUTLET',          1),
    ('PUMP_ROOM', 'SWITCH',          1);

-- MACHINE_ROOM: emergency light + exhaust + outlet
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('MACHINE_ROOM', 'EMERGENCY_LIGHT', 1),
    ('MACHINE_ROOM', 'EXHAUST_FAN',     1),
    ('MACHINE_ROOM', 'LIGHT',           1),
    ('MACHINE_ROOM', 'OUTLET',          1),
    ('MACHINE_ROOM', 'SWITCH',          1);

-- TANK_ROOM: minimal — emergency light only
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('TANK_ROOM', 'EMERGENCY_LIGHT', 1),
    ('TANK_ROOM', 'LIGHT',           1),
    ('TANK_ROOM', 'SWITCH',          1);

-- STAIR_ENCLOSURE: emergency light (pressurized, no sprinkler per fire code)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('STAIR_ENCLOSURE', 'EMERGENCY_LIGHT', 1),
    ('STAIR_ENCLOSURE', 'LIGHT',           1);

-- GARAGE: emergency light + exhaust (CO ventilation)
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('GARAGE', 'EMERGENCY_LIGHT', 1),
    ('GARAGE', 'EXHAUST_FAN',     1),
    ('GARAGE', 'LIGHT',           1),
    ('GARAGE', 'OUTLET',          1),
    ('GARAGE', 'SWITCH',          1);

-- PORCH, CAR_PORCH, VERANDAH: outdoor — 1 light + switch
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('PORCH',     'LIGHT',  1),
    ('PORCH',     'SWITCH', 1),
    ('CAR_PORCH', 'LIGHT',  1),
    ('CAR_PORCH', 'SWITCH', 1),
    ('VERANDAH',  'LIGHT',  1),
    ('VERANDAH',  'SWITCH', 1);

-- GENERIC: minimal fallback — 1 light + switch
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('GENERIC', 'LIGHT',  1),
    ('GENERIC', 'SWITCH', 1);

-- CONCOURSE, DEPARTURE_LOUNGE, GATE: large public transit spaces
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('CONCOURSE',        'EMERGENCY_LIGHT', 4),
    ('CONCOURSE',        'LIGHT',           0),
    ('CONCOURSE',        'SPRINKLER',       0),
    ('CONCOURSE',        'SUPPLY_DIFFUSER', 1),
    ('DEPARTURE_LOUNGE', 'EMERGENCY_LIGHT', 2),
    ('DEPARTURE_LOUNGE', 'LIGHT',           0),
    ('DEPARTURE_LOUNGE', 'OUTLET',          4),
    ('DEPARTURE_LOUNGE', 'SPRINKLER',       0),
    ('DEPARTURE_LOUNGE', 'SUPPLY_DIFFUSER', 1),
    ('GATE',             'EMERGENCY_LIGHT', 1),
    ('GATE',             'LIGHT',           0),
    ('GATE',             'SUPPLY_DIFFUSER', 1);

-- UTILITY, PLANT, MECHANICAL: new types added in Part 3
INSERT OR IGNORE INTO ad_space_type_mep_bom (space_type_id, mep_product_id, qty_normal) VALUES
    ('UTILITY',    'EMERGENCY_LIGHT', 1),
    ('UTILITY',    'LIGHT',           1),
    ('UTILITY',    'SWITCH',          1),
    ('PLANT',      'EMERGENCY_LIGHT', 1),
    ('PLANT',      'EXHAUST_FAN',     1),
    ('PLANT',      'LIGHT',           1),
    ('PLANT',      'SWITCH',          1),
    ('MECHANICAL', 'EMERGENCY_LIGHT', 1),
    ('MECHANICAL', 'EXHAUST_FAN',     1),
    ('MECHANICAL', 'LIGHT',           1),
    ('MECHANICAL', 'SWITCH',          1);
