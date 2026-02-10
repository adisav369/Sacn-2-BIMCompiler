-- Phase 108E: External Space Adjacency Rules
-- Date: 2026-02-10
-- Purpose: Add building-to-site and unit-to-core external spatial relationships
--          that UnitInteriorResolver needs when activating residential units.

-- ============================================================
-- PART 1: Extend ad_space_adjacency with external relationships
-- ============================================================

-- Unit access: every unit type must connect to circulation
INSERT OR IGNORE INTO ad_space_adjacency (space_type_a, space_type_b, relationship, door_required, notes) VALUES
    ('BEDROOM',        'LIVING',       'PREFERRED',  0, 'Bedroom accessible from living area'),
    ('MASTER_BEDROOM', 'LIVING',       'PREFERRED',  0, 'Master accessible from living area'),
    ('KITCHEN',        'LIVING',       'REQUIRED',   0, 'Kitchen opens to living (no dead-end cooking)'),
    ('LIVING',         'LIFT_LOBBY',   'REQUIRED',   1, 'Unit main entry via lift lobby'),
    ('LIVING',         'STAIR',        'PREFERRED',  1, 'Alternative egress via stair'),
    ('BATHROOM',       'BEDROOM',      'PREFERRED',  1, 'Ensuite access (reverse of existing)'),
    ('CORRIDOR',       'LIVING',       'PREFERRED',  1, 'Corridor-fed unit entry (alternative to lobby)');

-- ============================================================
-- PART 2: New ad_space_exterior_rule table
-- Captures which space types expect exterior wall exposure
-- (windows, ventilation, facade treatment decisions)
-- ============================================================

CREATE TABLE IF NOT EXISTS ad_space_exterior_rule (
    space_type_id     TEXT NOT NULL,
    requires_exterior INTEGER DEFAULT 0,  -- 1 = must have at least one exterior wall
    preferred_exterior TEXT,               -- 'ANY', 'SOUTH', 'NORTH', 'STREET_FACING'
    min_exterior_ratio REAL DEFAULT 0,     -- min fraction of perimeter on exterior (0-1)
    facade_preference  TEXT,               -- 'GLASS_CURTAIN', 'OPAQUE', 'MIXED'
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_id)
);

-- Residential rooms: bedrooms MUST have exterior (window for ventilation + egress)
INSERT OR REPLACE INTO ad_space_exterior_rule
    (space_type_id, requires_exterior, preferred_exterior, min_exterior_ratio, facade_preference, notes)
VALUES
    ('BEDROOM',        1, 'ANY',           0.25, 'OPAQUE',        'IRC R310 emergency egress window required'),
    ('MASTER_BEDROOM', 1, 'ANY',           0.25, 'OPAQUE',        'IRC R310 emergency egress window required'),
    ('LIVING',         1, 'STREET_FACING', 0.25, 'MIXED',         'Living room faces street for natural light'),
    ('KITCHEN',        1, 'ANY',           0.15, 'OPAQUE',        'Kitchen needs ventilation window'),
    ('WET_KITCHEN',    1, 'ANY',           0.15, 'OPAQUE',        'Wet kitchen exhaust to exterior'),
    ('DINING',         0, 'ANY',           0,    'OPAQUE',        'Dining can be interior (open to living)'),
    ('BATHROOM',       0, 'ANY',           0,    'OPAQUE',        'Can be interior (mechanical vent fallback)'),
    ('OFFICE',         1, 'ANY',           0.20, 'GLASS_CURTAIN', 'Natural light for workplace'),
    ('CLASSROOM',      1, 'ANY',           0.30, 'OPAQUE',        'MS 1525 daylighting for classrooms'),
    ('LOBBY',          1, 'STREET_FACING', 0.30, 'GLASS_CURTAIN', 'Main entry visibility'),
    ('LIFT_LOBBY',     0, 'ANY',           0,    'OPAQUE',        'Interior circulation node'),
    ('CORRIDOR',       0, 'ANY',           0,    'OPAQUE',        'Interior circulation'),
    ('STAIR',          0, 'ANY',           0,    'OPAQUE',        'Fire-rated enclosure'),
    ('STAIR_ENCLOSURE',0, 'ANY',           0,    'OPAQUE',        'Fire-rated enclosure'),
    ('STORAGE',        0, 'ANY',           0,    'OPAQUE',        'No exterior requirement'),
    ('TOILET_BLOCK',   0, 'ANY',           0,    'OPAQUE',        'Interior ok (mechanical vent)'),
    ('GENSET_ROOM',    1, 'ANY',           0.15, 'OPAQUE',        'Exhaust to exterior required'),
    ('PUMP_ROOM',      0, 'ANY',           0,    'OPAQUE',        'Interior ok'),
    ('TNB_ROOM',       0, 'ANY',           0,    'OPAQUE',        'Interior ok (ventilation grille)'),
    ('TANK_ROOM',      0, 'ANY',           0,    'OPAQUE',        'Interior ok'),
    ('MACHINE_ROOM',   1, 'ANY',           0.15, 'OPAQUE',        'Exhaust to exterior'),
    ('PORCH',          1, 'STREET_FACING', 1.0,  'OPAQUE',        'Fully exterior'),
    ('CAR_PORCH',      1, 'STREET_FACING', 0.5,  'OPAQUE',        'Open on entry side'),
    ('VERANDAH',       1, 'ANY',           0.5,  'OPAQUE',        'Semi-outdoor');
