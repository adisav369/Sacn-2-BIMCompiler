-- DV041: Abstract Space Capabilities — discipline-agnostic room classification
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-SPACE-LINK
--
-- The compiler thinks in capabilities, not room names.
-- KITCHEN/BATHROOM are compliance labels in ad_space_type (metadata).
-- The crawler uses: is this room PLUMBABLE? ELECTRIFIED? FIRE_PROTECTED?
--
-- Capability columns on ad_space_type — inferred from fixture presence,
-- consumed by MEP recipe linkage. Concrete names stay for code compliance.

ALTER TABLE ad_space_type ADD COLUMN is_plumbable      INTEGER DEFAULT 0;
ALTER TABLE ad_space_type ADD COLUMN is_electrified     INTEGER DEFAULT 0;
ALTER TABLE ad_space_type ADD COLUMN is_fire_protected  INTEGER DEFAULT 0;
ALTER TABLE ad_space_type ADD COLUMN is_ventilated      INTEGER DEFAULT 0;
ALTER TABLE ad_space_type ADD COLUMN is_gas_served      INTEGER DEFAULT 0;

-- Populate from known room types:
-- PLUMBABLE: rooms that need water supply (CW) or waste drain (SP)
UPDATE ad_space_type SET is_plumbable = 1
  WHERE Value IN ('BATHROOM','KITCHEN','WET_KITCHEN','UTILITY','LAUNDRY','TOILET_BLOCK');

-- ELECTRIFIED: all habitable + service rooms (everything except pure circulation)
UPDATE ad_space_type SET is_electrified = 1
  WHERE category IN ('HABITABLE','SERVICE') OR Value IN ('GARAGE','CAR_PORCH','STAIR');

-- FIRE_PROTECTED: all enclosed rooms (code-dependent, conservative default)
UPDATE ad_space_type SET is_fire_protected = 1
  WHERE category IN ('HABITABLE','SERVICE');

-- VENTILATED: interior rooms without exterior walls, or wet rooms
UPDATE ad_space_type SET is_ventilated = 1
  WHERE is_exterior = 0 OR Value IN ('BATHROOM','KITCHEN','WET_KITCHEN','TOILET_BLOCK');

-- GAS_SERVED: kitchens with gas appliances
UPDATE ad_space_type SET is_gas_served = 1
  WHERE Value IN ('KITCHEN','WET_KITCHEN');

-- Discipline→capability mapping table (read by crawler, never hard-coded)
CREATE TABLE IF NOT EXISTS ad_discipline_capability (
    discipline  TEXT NOT NULL,
    capability  TEXT NOT NULL,
    PRIMARY KEY (discipline, capability)
);

INSERT OR IGNORE INTO ad_discipline_capability VALUES ('CW',   'PLUMBABLE');
INSERT OR IGNORE INTO ad_discipline_capability VALUES ('SP',   'PLUMBABLE');
INSERT OR IGNORE INTO ad_discipline_capability VALUES ('ELEC', 'ELECTRIFIED');
INSERT OR IGNORE INTO ad_discipline_capability VALUES ('FP',   'FIRE_PROTECTED');
INSERT OR IGNORE INTO ad_discipline_capability VALUES ('ACMV', 'VENTILATED');
INSERT OR IGNORE INTO ad_discipline_capability VALUES ('LPG',  'GAS_SERVED');
