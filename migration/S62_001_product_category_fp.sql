-- S62_001_product_category_fp.sql
-- Implementing BIM_Designer_SRS.md §30.7.1 — Witness: W-FP-TRIAL-1
--
-- Creates M_Product_Category table in component_library.db.
-- Seeds discipline hierarchy (STR, MEP, ARC, ASM) + IFC class leaves.
-- Adds M_Product_Category_ID FK column to M_Product and backfills.
-- Onboards 3 FP products from TE extraction (geometry already in component_definitions).
--
-- Source: migration/archive/migration_M_Product_Category.sql (archived spec)
-- Idempotent: safe to re-run (IF NOT EXISTS, INSERT OR IGNORE).

-- ============================================================
-- Step 1: DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS M_Product_Category (
    M_Product_Category_ID TEXT PRIMARY KEY,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    Parent_Category_ID    TEXT,
    IFC_Class             TEXT,              -- IFC4 class name (leaf nodes only)
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1,
    FOREIGN KEY (Parent_Category_ID) REFERENCES M_Product_Category(M_Product_Category_ID)
);

-- ============================================================
-- Step 2: Parent categories (disciplines)
-- ============================================================

INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Description, Parent_Category_ID, IFC_Class, SeqNo) VALUES
    ('STR',   'Structural',    'Load-bearing structure: walls, slabs, beams, columns, members, plates',  NULL, NULL, 10),
    ('MEP',   'MEP',           'Mechanical, Electrical, Plumbing: pipes, fittings, terminals, devices',  NULL, NULL, 20),
    ('ARC',   'Architectural', 'Architectural elements: doors, windows, furniture, railings, stairs',     NULL, NULL, 30),
    ('ASM',   'Assembly',      'Non-physical: sets, phantoms, floors, BOMs — assembly groupings',         NULL, NULL, 40);

-- ============================================================
-- Step 3: Leaf categories (IFC classes)
-- ============================================================

-- Structural
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_WALL',          'Wall',                    'Vertical enclosure',               'STR', 'IfcWall',                    10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SLAB',          'Slab',                    'Horizontal plate (floor/roof)',     'STR', 'IfcSlab',                    20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_BEAM',          'Beam',                    'Horizontal structural member',      'STR', 'IfcBeam',                    30, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_COLUMN',        'Column',                  'Vertical structural member',        'STR', 'IfcColumn',                  40, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_MEMBER',        'Member',                  'Generic structural member',         'STR', 'IfcMember',                  50, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PLATE',         'Plate',                   'Thin structural plate',             'STR', 'IfcPlate',                   60, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_ROOF',          'Roof',                    'Roof element',                      'STR', 'IfcRoof',                    70, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_STAIR',         'Stair',                   'Stair assembly',                    'STR', 'IfcStair',                   80, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_STAIRFLIGHT',   'Stair Flight',            'Single run of stairs',              'STR', 'IfcStairFlight',             81, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FOOTING',       'Footing',                 'Foundation footing',                'STR', 'IfcFooting',                 90, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PILE',          'Pile',                    'Foundation pile',                   'STR', 'IfcPile',                    91, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_RAMPFLIGHT',    'Ramp Flight',             'Single run of ramp',                'STR', 'IfcRampFlight',              82, 1);

-- MEP — Piping
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWSEGMENT',   'Pipe / Duct Segment',     'Straight pipe or duct run',         'MEP', 'IfcFlowSegment',             10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWFITTING',   'Pipe / Duct Fitting',     'Elbow, tee, reducer, coupling',     'MEP', 'IfcFlowFitting',             20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWCONTROLLER','Flow Controller',         'Valve, damper, detector',           'MEP', 'IfcFlowController',          30, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWTERMINAL',  'Flow Terminal',           'Fixture, outlet, drain, tap',       'MEP', 'IfcFlowTerminal',            40, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PIPESEGMENT',   'Pipe Segment',            'Pipe run (IFC2x3 specific)',        'MEP', 'IfcPipeSegment',             11, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PIPEFITTING',   'Pipe Fitting',            'Pipe fitting (IFC2x3 specific)',    'MEP', 'IfcPipeFitting',             21, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_DUCTSEGMENT',   'Duct Segment',            'Duct run',                          'MEP', 'IfcDuctSegment',             12, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_DUCTFITTING',   'Duct Fitting',            'Duct elbow, tee, reducer',          'MEP', 'IfcDuctFitting',             22, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_VALVE',         'Valve',                   'Isolation, check, pressure valve',  'MEP', 'IfcValve',                   35, 1);

-- MEP — Electrical
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_LIGHTFIXTURE',  'Light Fixture',           'Luminaire',                         'MEP', 'IfcLightFixture',            50, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_OUTLET',        'Electrical Outlet',       'Power/data outlet',                 'MEP', 'IfcOutlet',                  60, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SWITCHDEVICE',  'Switching Device',        'Switch, dimmer, sensor',            'MEP', 'IfcSwitchingDevice',         70, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_ELECTRICAPPL',  'Electric Appliance',      'Fixed electrical equipment',         'MEP', 'IfcElectricAppliance',       71, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_CONTROLLER',    'Controller',              'Building automation controller',     'MEP', 'IfcController',              72, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SENSOR',        'Sensor',                  'Environmental sensor',              'MEP', 'IfcSensor',                  73, 1);

-- MEP — HVAC
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_AIRTERMINAL',   'Air Terminal',            'Diffuser, grille, register',        'MEP', 'IfcAirTerminal',             80, 1);

-- MEP — Fire Protection
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FIRESUPPTERM',  'Fire Suppression Terminal','Sprinkler head, nozzle',           'MEP', 'IfcFireSuppressionTerminal', 100, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_ALARM',         'Alarm',                   'Fire alarm, smoke/heat detector',   'MEP', 'IfcAlarm',                   101, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SANITARYTERM',  'Sanitary Terminal',       'WC, basin, bath, shower',           'MEP', 'IfcSanitaryTerminal',        110, 1);

-- Architectural
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_DOOR',          'Door',                    'Hinged, sliding, folding',          'ARC', 'IfcDoor',                    10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_WINDOW',        'Window',                  'Fixed, casement, sliding',          'ARC', 'IfcWindow',                  20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FURNISHING',    'Furnishing Element',      'Furniture, equipment (IFC4)',        'ARC', 'IfcFurnishingElement',       30, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FURNITURE',     'Furniture',               'Furniture (IFC2x3)',                'ARC', 'IfcFurniture',               31, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_RAILING',       'Railing',                 'Guard rail, handrail, balustrade',  'ARC', 'IfcRailing',                 40, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_COVERING',      'Covering',                'Ceiling, cladding, flooring',       'ARC', 'IfcCovering',                50, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_OPENINGELEMENT','Opening Element',         'Void in wall/slab for door/window', 'ARC', 'IfcOpeningElement',          60, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_BLDGELEMPROXY', 'Building Element Proxy',  'Generic/unclassified element',      'ARC', 'IfcBuildingElementProxy',    70, 1);

-- Assembly (non-physical)
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_SET',           'Set',                     'BOM assembly group',                'ASM', NULL, 10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_PHANTOM',       'Phantom',                 'Phantom BOM (pass-through)',        'ASM', NULL, 20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_FLOOR',         'Floor',                   'Floor-level assembly',              'ASM', NULL, 30, 1);

-- ============================================================
-- Step 4: Add FK column to M_Product (no-op if already exists)
-- ============================================================

ALTER TABLE M_Product ADD COLUMN M_Product_Category_ID TEXT REFERENCES M_Product_Category(M_Product_Category_ID);

-- ============================================================
-- Step 5: Backfill existing products from ifc_class
-- ============================================================

UPDATE M_Product SET M_Product_Category_ID = (
    SELECT c.M_Product_Category_ID
    FROM M_Product_Category c
    WHERE c.IFC_Class = M_Product.ifc_class
)
WHERE ifc_class IS NOT NULL AND M_Product_Category_ID IS NULL;

-- ============================================================
-- Step 6: Onboard 3 FP products from TE extraction
-- Dimensions from component_definitions (metres, extracted not invented)
-- Geometry already exists: IDs 8986 (pendent), 8983 (upright), 17028 (smoke)
-- ============================================================

INSERT OR IGNORE INTO M_Product (product_id, product_type, width, depth, height, ifc_class, extracted_from, is_active, M_Product_Category_ID)
VALUES
    ('jkrME18_spr_sprinkler head_pendent',  'FP', 0.0318, 0.0265, 0.0578, 'IfcFireSuppressionTerminal', 'IFC_EXTRACTION', 1, 'IFC_FIRESUPPTERM'),
    ('jkrME18_spr_sprinkler head_upright',  'FP', 0.0318, 0.0265, 0.0578, 'IfcFireSuppressionTerminal', 'IFC_EXTRACTION', 1, 'IFC_FIRESUPPTERM'),
    ('jkrME18_fir-al_smoke detector',       'FP', 0.170,  0.170,  0.075,  'IfcAlarm',                   'IFC_EXTRACTION', 1, 'IFC_ALARM');

-- ============================================================
-- Step 7: Verification
-- ============================================================

SELECT 'M_Product_Category rows' AS metric, COUNT(*) AS value FROM M_Product_Category
UNION ALL SELECT 'Parent categories', COUNT(*) FROM M_Product_Category WHERE Parent_Category_ID IS NULL
UNION ALL SELECT 'Leaf categories', COUNT(*) FROM M_Product_Category WHERE IFC_Class IS NOT NULL
UNION ALL SELECT 'M_Product total', COUNT(*) FROM M_Product
UNION ALL SELECT 'M_Product with category', COUNT(*) FROM M_Product WHERE M_Product_Category_ID IS NOT NULL
UNION ALL SELECT 'M_Product FP products', COUNT(*) FROM M_Product WHERE ifc_class IN ('IfcFireSuppressionTerminal','IfcAlarm');
