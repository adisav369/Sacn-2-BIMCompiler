-- Phase RM-4: Create ad_building parent table + FK linkage to child tables.
-- Pattern: like AD_Window in iDempiere — master record that anchors all building data.
-- The eventual CompilerEditor lists buildings from this table first.

-- ============================================================
-- 1. CREATE PARENT TABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS ad_building (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    building_type   TEXT NOT NULL UNIQUE,  -- key used by all child tables
    name            TEXT NOT NULL,         -- display name
    description     TEXT,
    width_mm        REAL,                  -- envelope X extent
    depth_mm        REAL,                  -- envelope Y extent
    height_mm       REAL,                  -- wall/storey height
    num_storeys     INTEGER DEFAULT 1,
    num_units       INTEGER DEFAULT 1,     -- >1 for duplex/townhouse
    building_class  TEXT DEFAULT 'RESIDENTIAL',  -- RESIDENTIAL, COMMERCIAL, INSTITUTIONAL
    has_ifc_ref     INTEGER DEFAULT 0,     -- 1 = extracted from IFC (Rosetta stones), 0 = generative
    is_active       INTEGER DEFAULT 1
);

-- ============================================================
-- 2. BACKFILL EXISTING BUILDINGS + ADD TB_LKTN
-- ============================================================
INSERT INTO ad_building (building_type, name, description, width_mm, depth_mm, height_mm, num_storeys, num_units, building_class, has_ifc_ref)
VALUES
    ('Ifc4_SampleHouse',  'IFC4 Sample House',       'Rosetta Stone 1 — single-storey residential', 16867, 8667, 3475, 1, 1, 'RESIDENTIAL', 1),
    ('Ifc2x3_Duplex',     'IFC2x3 Duplex',           'Rosetta Stone 2 — 2-unit side-by-side duplex', 9214, 26565, 6634, 2, 2, 'RESIDENTIAL', 1),
    ('SJTII_Terminal',     'SJTII Airport Terminal',   'Rosetta Stone 3 — 4-storey commercial terminal', 73669, 59123, 29128, 4, 1, 'COMMERCIAL', 1),
    ('TB_LKTN',            'TB-LKTN Rumah Rakyat',    'Phase RM-4 — generative from drawings, no IFC reference', 9900, 8500, 3000, 1, 1, 'RESIDENTIAL', 0);

-- ============================================================
-- 3. ADD building_id FK TO CHILD TABLES + POPULATE
-- ============================================================

-- 3a. ad_building_grid
ALTER TABLE ad_building_grid ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_building_grid SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_building_grid.building_type);

-- 3b. ad_room_boundary
ALTER TABLE ad_room_boundary ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_room_boundary SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_room_boundary.building_type);

-- 3c. ad_wall_face
ALTER TABLE ad_wall_face ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_wall_face SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_wall_face.building_type);

-- 3d. ad_element_rule
ALTER TABLE ad_element_rule ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_element_rule SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_element_rule.building_type);

-- 3e. ad_element_dependency
ALTER TABLE ad_element_dependency ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_element_dependency SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_element_dependency.building_type);

-- 3f. ad_element_placement (flat oracle — 68,472 rows)
ALTER TABLE ad_element_placement ADD COLUMN building_id INTEGER REFERENCES ad_building(id);
UPDATE ad_element_placement SET building_id = (SELECT id FROM ad_building WHERE ad_building.building_type = ad_element_placement.building_type);
