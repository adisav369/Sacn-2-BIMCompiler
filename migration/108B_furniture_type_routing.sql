-- Phase 108B: Furniture Type Routing + Space Adjacency Metadata
-- Date: 2026-02-10
-- Purpose: Data-driven furniture dispatch per space type, replacing hardcoded if/else

-- 1. Furniture routing table — maps space types to furniture handlers
CREATE TABLE IF NOT EXISTS ad_space_type_furniture (
    space_type_id   TEXT NOT NULL,
    furniture_bom_id TEXT,           -- FK to ad_bom (NULL = no furniture for this type)
    fallback_rule    TEXT DEFAULT 'NONE',  -- NONE | WORKSTATION | SEATING | CANTEEN
    is_active        INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_id)
);

INSERT OR REPLACE INTO ad_space_type_furniture (space_type_id, furniture_bom_id, fallback_rule) VALUES
('OFFICE',          'ROOM_FURNITURE', 'NONE'),
('STAFFROOM',       'ROOM_FURNITURE', 'NONE'),
('OPEN_PLAN',       'ROOM_FURNITURE', 'NONE'),
('LOBBY',           'ROOM_FURNITURE', 'NONE'),
('LIFT_LOBBY',       NULL,            'SEATING'),
('CLASSROOM',        NULL,            'WORKSTATION'),
('STUDY_NOOK',       NULL,            'WORKSTATION'),
('ASSEMBLY_HALL',    NULL,            'SEATING'),
('BEDROOM',          NULL,            'NONE'),
('MASTER_BEDROOM',   NULL,            'NONE'),
('LIVING',           NULL,            'NONE'),
('KITCHEN',          NULL,            'NONE'),
('WET_KITCHEN',      NULL,            'NONE'),
('DINING',           NULL,            'CANTEEN'),
('CANTEEN',          NULL,            'CANTEEN'),
('BATHROOM',         NULL,            'NONE'),
('TOILET_BLOCK',     NULL,            'NONE'),
('CORRIDOR',         NULL,            'NONE'),
('STAIR',            NULL,            'NONE'),
('STAIR_ENCLOSURE',  NULL,            'NONE'),
('PORCH',            NULL,            'NONE'),
('CAR_PORCH',        NULL,            'NONE'),
('VERANDAH',         NULL,            'NONE'),
('STORAGE',          NULL,            'NONE'),
('GARAGE',           NULL,            'NONE'),
('GENERIC',          NULL,            'NONE'),
('GATE',             NULL,            'NONE'),
('CONCOURSE',        NULL,            'NONE'),
('DEPARTURE_LOUNGE', NULL,            'SEATING'),
('TANK_ROOM',        NULL,            'NONE'),
('MACHINE_ROOM',     NULL,            'NONE'),
('TNB_ROOM',         NULL,            'NONE'),
('PUMP_ROOM',        NULL,            'NONE'),
('GENSET_ROOM',      NULL,            'NONE');

-- 2. Space adjacency metadata (not consumed by code yet — design intent documentation)
CREATE TABLE IF NOT EXISTS ad_space_adjacency (
    space_type_a    TEXT NOT NULL,
    space_type_b    TEXT NOT NULL,
    relationship    TEXT NOT NULL,    -- REQUIRED | PREFERRED | PROHIBITED | ENSUITE
    door_required   INTEGER DEFAULT 1,
    notes           TEXT,
    is_active       INTEGER DEFAULT 1,
    PRIMARY KEY (space_type_a, space_type_b)
);

INSERT OR REPLACE INTO ad_space_adjacency (space_type_a, space_type_b, relationship, door_required, notes) VALUES
('MASTER_BEDROOM', 'BATHROOM',     'ENSUITE',    1, 'Master ensuite'),
('BEDROOM',        'BATHROOM',     'PREFERRED',  1, 'Ensuite optional'),
('BEDROOM',        'CORRIDOR',     'REQUIRED',   1, 'Must have access'),
('MASTER_BEDROOM', 'CORRIDOR',     'REQUIRED',   1, 'Must have access'),
('LIVING',         'KITCHEN',      'PREFERRED',  0, 'Open plan ok'),
('LIVING',         'DINING',       'PREFERRED',  0, 'Open plan ok'),
('LIVING',         'CORRIDOR',     'REQUIRED',   1, 'Main entry'),
('KITCHEN',        'DINING',       'PREFERRED',  0, 'Adjacent or open plan'),
('CORRIDOR',       'STAIR',        'REQUIRED',   1, 'Fire escape access'),
('CORRIDOR',       'LIFT_LOBBY',   'REQUIRED',   0, 'Circulation link'),
('TOILET_BLOCK',   'LIFT_LOBBY',   'REQUIRED',   1, 'Public access'),
('TOILET_BLOCK',   'CORRIDOR',     'PREFERRED',  1, 'Alternative access'),
('BATHROOM',       'KITCHEN',      'PROHIBITED', 0, 'Wet rooms not adjacent per hygiene'),
('BEDROOM',        'GENSET_ROOM',  'PROHIBITED', 0, 'Noise separation'),
('BEDROOM',        'TNB_ROOM',     'PROHIBITED', 0, 'Safety separation');
