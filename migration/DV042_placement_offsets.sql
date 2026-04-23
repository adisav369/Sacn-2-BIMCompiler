-- DV042: Placement offsets as metadata — modellers can examine and edit per standards
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — no hardcoded offsets in code
--
-- Each placement_rule has default offsets from room edges (in metres).
-- Modellers edit these per building code / local standards.
-- The code reads them; never hardcodes wall distances.

CREATE TABLE IF NOT EXISTS ad_placement_offset (
    placement_rule  TEXT PRIMARY KEY,
    description     TEXT NOT NULL,
    from_edge_x     REAL NOT NULL DEFAULT 0,   -- offset from room min-X edge (metres)
    from_edge_y     REAL NOT NULL DEFAULT 0,   -- offset from room min-Y edge (metres)
    z_rule          TEXT NOT NULL DEFAULT 'FLOOR', -- FLOOR, CEILING, MID, or fixed height
    z_offset        REAL NOT NULL DEFAULT 0,   -- offset from z_rule datum (metres)
    x_ref           TEXT NOT NULL DEFAULT 'CENTER', -- CENTER, MIN, MAX (which X edge)
    y_ref           TEXT NOT NULL DEFAULT 'CENTER', -- CENTER, MIN, MAX (which Y edge)
    standard        TEXT,                       -- building code reference
    notes           TEXT
);

-- Wall placements — offsets from room edges per standards
INSERT OR IGNORE INTO ad_placement_offset VALUES
    ('WALL_BACK',      'Back wall, floor level (WC, bidet)',
     0, 0.15, 'FLOOR', 0.2, 'CENTER', 'MAX', 'IPC 2021 §405', 'WC 150mm from back wall, 200mm above floor'),
    ('WALL_SIDE',      'Side wall, counter height (lavatory, sink)',
     0.15, 0, 'FLOOR', 0.85, 'MIN', 'CENTER', 'IPC 2021 §405', 'Sink 150mm from side wall, 850mm counter height'),
    ('WALL_ENTRY',     'Near entry, switch height (light switch)',
     0.15, 0.15, 'FLOOR', 1.2, 'MIN', 'MIN', 'NEC 2020 §404.4', 'Switch 1200mm above floor, near door'),
    ('WALL_SINK',      'Above sink, outlet height (GFCI outlet)',
     0.15, 0, 'FLOOR', 1.0, 'MIN', 'CENTER', 'NEC 2020 §210.8', 'GFCI 1000mm above floor, above sink'),
    ('WALL_COOKER',    'Above cooker, exhaust height',
     0, 0.15, 'FLOOR', 1.5, 'CENTER', 'MAX', 'IMC 2021 §403', 'Exhaust 1500mm above floor, above cooker'),
    ('WALL_SPACED',    'Spaced along wall (bedroom outlets)',
     0.3, 0, 'FLOOR', 0.3, 'MIN', 'CENTER', 'NEC 2020 §210.52', 'Outlets 300mm above floor, 300mm from corner'),
    ('COUNTER_BACK',   'Counter back wall (kitchen sink)',
     0, 0.6, 'FLOOR', 0.85, 'CENTER', 'MAX', 'IPC 2021 §405', 'Kitchen sink 600mm from back wall, counter height'),
    ('COUNTER_SINK',   'Counter near sink (GFCI outlet)',
     0.6, 0, 'FLOOR', 1.0, 'MIN', 'CENTER', 'NEC 2020 §210.8', 'GFCI near sink, 1000mm above floor');

-- Ceiling placements
INSERT OR IGNORE INTO ad_placement_offset VALUES
    ('CEILING_CENTER', 'Ceiling center (light, exhaust fan, sprinkler)',
     0, 0, 'CEILING', 0.05, 'CENTER', 'CENTER', NULL, 'Centered on ceiling, 50mm below'),
    ('CEILING_GRID',   'Ceiling grid (sprinkler per NFPA 13)',
     0, 0, 'CEILING', 0.05, 'CENTER', 'CENTER', 'NFPA 13 §8.5', 'Grid-aligned on ceiling');

-- Floor placements
INSERT OR IGNORE INTO ad_placement_offset VALUES
    ('FLOOR_LOW',      'Floor level, room center (floor trap)',
     0, 0, 'FLOOR', 0, 'CENTER', 'CENTER', 'MS 1228 §5', 'Floor trap at lowest point'),
    ('FLOOR_CENTER',   'Floor center (generic floor placement)',
     0, 0, 'FLOOR', 0, 'CENTER', 'CENTER', NULL, 'Centered on floor'),
    ('AUTO',           'Auto-placed by walker (default)',
     0, 0, 'MID', 0, 'CENTER', 'CENTER', NULL, 'Mid-height, room center');
