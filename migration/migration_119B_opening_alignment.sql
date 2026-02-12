-- Phase 119B: Profile-aware opening families + Duplex/SampleHouse dimensions
-- Idempotent: uses INSERT OR IGNORE, ALTER TABLE IF NOT EXISTS pattern

-- 1. Add profile column to ad_space_type_opening (nullable = generic applies to all profiles)
-- SQLite doesn't support IF NOT EXISTS for ALTER TABLE, so check via pragma
-- Run this in Python or handle the "duplicate column" error gracefully

ALTER TABLE ad_space_type_opening ADD COLUMN profile TEXT DEFAULT NULL;

-- 2. New opening families for Duplex (US residential)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, is_fire_rated, description, is_active)
VALUES
    -- Duplex doors (from Ifc2x3_Duplex reference, bbox incl frame: +152mm W, +76mm H)
    ('US_INT_DOOR_762',   'US Interior Door 762mm',     'DOOR',   'IfcDoor',   914,  2108, 0, 'US interior single 914x2108mm (incl frame)', 1),
    ('US_INT_DOOR_864',   'US Interior Door 864mm',     'DOOR',   'IfcDoor',  1016,  2108, 0, 'US interior single 1016x2108mm (incl frame)', 1),
    ('US_GLASS_DOOR_813', 'US Glass Door 813mm',        'DOOR',   'IfcDoor',   965,  2496, 0, 'US interior glass 965x2496mm (incl frame)',  1),
    ('US_DBL_DOOR_1250',  'US Double Door 1250mm',      'DOOR',   'IfcDoor',  1402,  2086, 0, 'US double door 1402x2086mm (incl frame)',    1),
    -- Duplex windows
    ('US_CASEMENT_819',   'US Casement 819mm',          'WINDOW', 'IfcWindow',  819,   759, 0, 'US casement 819x759mm',     1),
    ('US_FIXED_2800',     'US Fixed Window 2800mm',     'WINDOW', 'IfcWindow', 2800,  2410, 0, 'US fixed 2800x2410mm',      1),
    ('US_FIXED_4835',     'US Picture Window 4835mm',   'WINDOW', 'IfcWindow', 4835,  2420, 0, 'US picture 4835x2420mm',    1),
    ('US_FIXED_750',      'US Sidelight 750mm',         'WINDOW', 'IfcWindow',  750,  2200, 0, 'US sidelight 750x2200mm',   1),
    ('US_SKYLIGHT_1180',  'US Skylight 1180mm',         'WINDOW', 'IfcWindow', 1180,  1170, 0, 'US skylight 1180x1170mm',   1);

-- 3. New opening families for SampleHouse (UK residential)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, is_fire_rated, description, is_active)
VALUES
    ('UK_EXT_DBL_DOOR',  'UK Exterior Double Door',    'DOOR',   'IfcDoor',  1860, 2110, 0, 'UK exterior double flush 1860x2110mm (incl frame)', 1),
    ('UK_INT_DOOR_810',  'UK Interior Door 810mm',     'DOOR',   'IfcDoor',   880, 2150, 0, 'UK interior single 880x2150mm (incl frame)',        1),
    ('UK_WINDOW_1810',   'UK Window 1810mm',           'WINDOW', 'IfcWindow', 1860, 1210, 0, 'UK single plain 1860x1210mm (incl frame)',          1);

-- 4. Profile-specific opening rules for Duplex (US_Residential)
-- Priority 50 beats generic priority 100
INSERT OR IGNORE INTO ad_space_type_opening (space_type_id, opening_role, family_id, qty_rule, qty_base, sill_height_mm, placement_wall, placement_rule, priority, is_active, profile)
VALUES
    -- Bedroom: 864mm door + 819x759 casement window
    ('BEDROOM',        'ENTRY',         'US_INT_DOOR_864',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'US_Residential'),
    ('BEDROOM',        'NATURAL_LIGHT', 'US_CASEMENT_819',  'PER_EXTERIOR_WALL', 1, 900,  'exterior', 'CENTER', 50, 1, 'US_Residential'),
    -- Master bedroom: same door, larger window
    ('MASTER_BEDROOM', 'ENTRY',         'US_INT_DOOR_864',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'US_Residential'),
    ('MASTER_BEDROOM', 'NATURAL_LIGHT', 'US_FIXED_2800',    'PER_EXTERIOR_WALL', 1, 300,  'exterior', 'CENTER', 50, 1, 'US_Residential'),
    -- Living: double door + large picture window
    ('LIVING',         'ENTRY',         'US_DBL_DOOR_1250', 'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'US_Residential'),
    ('LIVING',         'NATURAL_LIGHT', 'US_FIXED_4835',    'PER_EXTERIOR_WALL', 1, 300,  'exterior', 'CENTER', 50, 1, 'US_Residential'),
    -- Kitchen: 864mm door + casement window
    ('KITCHEN',        'ENTRY',         'US_INT_DOOR_864',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'US_Residential'),
    ('KITCHEN',        'NATURAL_LIGHT', 'US_CASEMENT_819',  'PER_EXTERIOR_WALL', 1, 900,  'exterior', 'CENTER', 50, 1, 'US_Residential'),
    -- Bathroom: 762mm door (narrower) + small casement
    ('BATHROOM',       'ENTRY',         'US_INT_DOOR_762',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'US_Residential'),
    ('BATHROOM',       'VENTILATION',   'US_CASEMENT_819',  'PER_EXTERIOR_WALL', 1, 1400, 'exterior', 'CENTER', 50, 1, 'US_Residential'),
    -- Corridor/foyer: 864mm entry door
    ('CORRIDOR',       'ENTRY',         'US_INT_DOOR_864',  'FIXED',             1, 0,    'exterior', 'CENTER', 50, 1, 'US_Residential');

-- 5. Profile-specific opening rules for SampleHouse (UK_Residential)
INSERT OR IGNORE INTO ad_space_type_opening (space_type_id, opening_role, family_id, qty_rule, qty_base, sill_height_mm, placement_wall, placement_rule, priority, is_active, profile)
VALUES
    -- Bedroom: UK 810mm door + 1810mm window
    ('BEDROOM',        'ENTRY',         'UK_INT_DOOR_810',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'UK_Residential'),
    ('BEDROOM',        'NATURAL_LIGHT', 'UK_WINDOW_1810',   'PER_EXTERIOR_WALL', 1, 900,  'exterior', 'CENTER', 50, 1, 'UK_Residential'),
    -- Living: UK double door + 1810mm window
    ('LIVING',         'ENTRY',         'UK_EXT_DBL_DOOR',  'FIXED',             1, 0,    'exterior', 'CENTER', 50, 1, 'UK_Residential'),
    ('LIVING',         'NATURAL_LIGHT', 'UK_WINDOW_1810',   'PER_EXTERIOR_WALL', 1, 900,  'exterior', 'CENTER', 50, 1, 'UK_Residential'),
    -- Kitchen: UK 810mm door + 1810mm window
    ('KITCHEN',        'ENTRY',         'UK_INT_DOOR_810',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'UK_Residential'),
    ('KITCHEN',        'NATURAL_LIGHT', 'UK_WINDOW_1810',   'PER_EXTERIOR_WALL', 1, 900,  'exterior', 'CENTER', 50, 1, 'UK_Residential'),
    -- Bathroom: UK 810mm door
    ('BATHROOM',       'ENTRY',         'UK_INT_DOOR_810',  'FIXED',             1, 0,    'interior', 'CENTER', 50, 1, 'UK_Residential'),
    -- Corridor/entrance: UK exterior double door
    ('CORRIDOR',       'ENTRY',         'UK_EXT_DBL_DOOR',  'FIXED',             1, 0,    'exterior', 'CENTER', 50, 1, 'UK_Residential');
