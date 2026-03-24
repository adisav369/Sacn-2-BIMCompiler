-- DV013_ad_org.sql
-- Creates AD_Org table in ERP.db — disciplines as organizational units.
-- Pattern: iDempiere AD_Org partitions data by organizational unit.
-- In construction, disciplines ARE organizational units (each is a trade).
-- APPEND-ONLY: never modify this migration after first run
--
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.2 — Witness: W-DV-DB-ORG
--
-- NOTE: AD_Org_ID 10-15 (MEP, ROAD, GEO, RAIL, LAND, SIGN) exist in AD_Org
-- but NOT in the Discipline.java enum. Discipline.fromString("ROAD") returns null.
-- These are data-only entries for ad_ifc_class_map resolution. If Java code needs
-- to work with infra disciplines, add them to the enum (Step 5 scope).

CREATE TABLE IF NOT EXISTS AD_Org (
    AD_Org_ID     INTEGER PRIMARY KEY,
    AD_Client_ID  INTEGER NOT NULL DEFAULT 1,
    Value         TEXT    NOT NULL UNIQUE,
    Name          TEXT    NOT NULL,
    Description   TEXT,
    IsSummary     TEXT    NOT NULL DEFAULT 'N',
    IsActive      TEXT    NOT NULL DEFAULT 'Y',
    AD_Org_Type   TEXT    NOT NULL DEFAULT 'DISCIPLINE',
    element_count INTEGER DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ad_org_value ON AD_Org(Value);

-- Seed: 10 rows (0='*' shared + 9 disciplines from Discipline.java enum)
-- element_count from TE census (informational, not contractual)
INSERT OR IGNORE INTO AD_Org (AD_Org_ID, Value, Name, Description, AD_Org_Type, element_count) VALUES
    (0, '*',    'Shared',            'Shared infrastructure visible to all trades', 'SHARED',     0),
    (1, 'ARC',  'Architecture',      'Doors, windows, furniture, finishes',         'DISCIPLINE', 35338),
    (2, 'STR',  'Structural',        'Beams, columns, slabs, foundations',          'DISCIPLINE', 1429),
    (3, 'FP',   'Fire Protection',   'Sprinklers, alarms, risers',                  'DISCIPLINE', 6884),
    (4, 'ELEC', 'Electrical',        'Lights, outlets, switches, cable trays',      'DISCIPLINE', 1172),
    (5, 'ACMV', 'HVAC',             'Ducts, diffusers, AHUs',                      'DISCIPLINE', 1621),
    (6, 'CW',   'Cold Water',        'Pipes, fittings, valves',                     'DISCIPLINE', 1431),
    (7, 'SP',   'Sanitary/Plumbing', 'Fixtures, waste pipes',                       'DISCIPLINE', 979),
    (8, 'LPG',  'Gas Piping',        'Gas pipes, meters',                           'DISCIPLINE', 209),
    (9, 'REB',  'Reinforcement',     'Rebar, mesh, post-tension',                   'DISCIPLINE', 2660),
    (10, 'MEP',  'MEP (Generic)',    'Generic MEP — resolves to specific trade at placement', 'DISCIPLINE', 0),
    (11, 'ROAD', 'Road',             'Road infrastructure (IFC4X3)',                          'DISCIPLINE', 0),
    (12, 'GEO',  'Geotechnical',     'Earthworks, fills, cuts',                               'DISCIPLINE', 0),
    (13, 'RAIL', 'Rail',             'Rail infrastructure (IFC4X3)',                           'DISCIPLINE', 0),
    (14, 'LAND', 'Landscape',        'Geographic, landscape elements',                        'DISCIPLINE', 0),
    (15, 'SIGN', 'Signage',          'Signs and signals',                                     'DISCIPLINE', 0);

-- Update AD_SysConfig version
INSERT OR REPLACE INTO AD_SysConfig (Name, Value, Description)
    VALUES ('AD_ORG_VERSION', 'DV013', 'AD_Org table created');
