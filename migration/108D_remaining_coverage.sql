-- Phase 108D: Complete remaining coverage gaps
-- Date: 2026-02-10
-- Purpose: Fill last gaps in opening specs + space dimensions

-- ============================================================
-- PART 1: Opening specs for enclosed utility types
-- ============================================================

-- MECHANICAL, PLANT, UTILITY: fire-rated entry door (enclosed service rooms)
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, is_fire_rated)
VALUES
    ('MECHANICAL', 'ENTRY', 'FIRE_DOOR', 'FIXED', 1, 'interior', 1),
    ('PLANT',      'ENTRY', 'FIRE_DOOR', 'FIXED', 1, 'interior', 1),
    ('UTILITY',    'ENTRY', 'FIRE_DOOR', 'FIXED', 1, 'interior', 1);

-- PORCH, CAR_PORCH, VERANDAH: open-air — no doors/windows
-- (intentionally empty — documented here for completeness)

-- ============================================================
-- PART 2: Space dimensions for transit/exterior types
-- These are minimal constraints — mostly unconstrained spaces.
-- ============================================================

INSERT OR IGNORE INTO ad_space_dim
    (space_type, min_width, min_depth, min_area, min_height, preferred_ratio, max_ratio,
     required_products, optional_products, door_on_wall, window_on_wall, facade_type)
VALUES
    ('CAR_PORCH',        2.5, 5.0,  12.5, 2.4, 2.0, 4.0, '[]', '[]', 'ANY', 'NONE', 'OPAQUE'),
    ('PORCH',            1.2, 1.2,   2.0, 2.4, 1.5, 3.0, '[]', '[]', 'ANY', 'NONE', 'OPAQUE'),
    ('VERANDAH',         1.5, 2.0,   3.0, 2.4, 2.0, 5.0, '[]', '[]', 'ANY', 'NONE', 'OPAQUE'),
    ('CONCOURSE',        3.0, 6.0,  50.0, 3.0, 3.0, 8.0, '[]', '[]', 'ANY', 'EXTERIOR', 'GLASS_CURTAIN'),
    ('DEPARTURE_LOUNGE', 4.0, 6.0,  40.0, 3.0, 2.0, 4.0, '[]', '[]', 'ANY', 'EXTERIOR', 'GLASS_CURTAIN'),
    ('GATE',             3.0, 4.0,  15.0, 3.0, 1.5, 3.0, '[]', '[]', 'ANY', 'EXTERIOR', 'GLASS_CURTAIN');

-- Also add MECHANICAL, PLANT, UTILITY to ad_space_dim (if not present via ad_space_dim orphans)
-- MECHANICAL/PLANT/UTILITY already in ad_space_dim from earlier phases — verify and skip duplicates
