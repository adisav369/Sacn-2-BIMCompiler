-- DV039: MEP metadata tables — standards as visible data
-- Implementing DISC_VALIDATION_DB_SRS.md §8a-§8c, §9
-- Walker + validation read these. Code never embeds standard values.

-- §8a: Slope gradients, clearances, spacing
CREATE TABLE IF NOT EXISTS ad_mep_laying_rule (
    rule_id     TEXT PRIMARY KEY,
    discipline  TEXT NOT NULL,
    rule_type   TEXT NOT NULL,
    parameter   TEXT NOT NULL,
    value       REAL NOT NULL,
    standard    TEXT,
    section     TEXT
);

INSERT OR IGNORE INTO ad_mep_laying_rule (rule_id, discipline, rule_type, parameter, value, standard, section) VALUES
    ('LAY-SP-001',  'SP',  'GRADIENT', 'min_slope', 0.025, 'MS 1228',   '§5.3'),
    ('LAY-SP-002',  'SP',  'GRADIENT', 'max_slope', 0.040, 'MS 1228',   '§5.3'),
    ('LAY-LPG-001', 'LPG', 'GRADIENT', 'min_slope', 0.010, 'MS 830',    '§4.2'),
    ('LAY-LPG-002', 'LPG', 'GRADIENT', 'max_slope', 0.100, 'MS 830',    '§4.2');

-- §8b: Connection offsets per fitting type
CREATE TABLE IF NOT EXISTS ad_mep_fitting_rule (
    rule_id     TEXT PRIMARY KEY,
    piece_type  TEXT NOT NULL,
    connection  TEXT NOT NULL,
    axis        TEXT NOT NULL,
    offset_rule TEXT NOT NULL,
    standard    TEXT
);

INSERT OR IGNORE INTO ad_mep_fitting_rule (rule_id, piece_type, connection, axis, offset_rule, standard) VALUES
    ('FIT-TEE-001', 'PIPE_TEE',     'MAIN_OUT',   'X',      'diameter/2 from centre', NULL),
    ('FIT-TEE-002', 'PIPE_TEE',     'BRANCH_OUT', 'Y',      'diameter/2 from centre', NULL),
    ('FIT-ELB-001', 'PIPE_ELBOW',   'OUT',        'varies',  'angle × bend_radius',   NULL),
    ('FIT-RED-001', 'PIPE_REDUCER', 'OUT',        'X',       'length along axis',      NULL);

-- §8c: Vertical run constraints per discipline
CREATE TABLE IF NOT EXISTS ad_mep_riser_rule (
    rule_id     TEXT PRIMARY KEY,
    discipline  TEXT NOT NULL,
    parameter   TEXT NOT NULL,
    value       REAL NOT NULL,
    standard    TEXT,
    section     TEXT
);

INSERT OR IGNORE INTO ad_mep_riser_rule (rule_id, discipline, parameter, value, standard, section) VALUES
    ('RSR-FP-001', 'FP', 'max_floor_height_mm',  4000, 'NFPA 13', '§8.15'),
    ('RSR-FP-002', 'FP', 'riser_diameter_mm',       65, 'NFPA 13', '§8.15.1'),
    ('RSR-CW-001', 'CW', 'min_pressure_kpa',       150, 'MS 1228', '§3.4'),
    ('RSR-SP-001', 'SP', 'stack_min_diameter_mm',   100, 'MS 1228', '§5.7');
