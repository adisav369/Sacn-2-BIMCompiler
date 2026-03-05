-- migration_M_Product_Category.sql
-- Creates M_Product_Category table in BOM.db with IFC classification hierarchy.
-- Adds M_Product_Category_ID FK column to M_Product and backfills.
--
-- iDempiere pattern: M_Product_Category is a self-referencing tree.
-- Parent categories = discipline (Structural, MEP, Architectural, Assembly).
-- Child categories = IFC class name (IfcWall, IfcDoor, IfcFlowSegment, etc.).
-- M_Product.M_Product_Category_ID FK points to the LEAF (IFC class) category.
--
-- Idempotent: safe to re-run.

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

-- MEP — Piping
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWSEGMENT',   'Pipe / Duct Segment',     'Straight pipe or duct run',         'MEP', 'IfcFlowSegment',             10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWFITTING',   'Pipe / Duct Fitting',     'Elbow, tee, reducer, coupling',     'MEP', 'IfcFlowFitting',             20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWCONTROLLER','Flow Controller',         'Valve, damper, detector',           'MEP', 'IfcFlowController',          30, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FLOWTERMINAL',  'Flow Terminal',           'Fixture, outlet, drain, tap',       'MEP', 'IfcFlowTerminal',            40, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PIPESEGMENT',   'Pipe Segment',            'Pipe run (IFC2x3 specific)',        'MEP', 'IfcPipeSegment',             11, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_PIPEFITTING',   'Pipe Fitting',            'Pipe fitting (IFC2x3 specific)',    'MEP', 'IfcPipeFitting',             21, 1);

-- MEP — Electrical
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_LIGHTFIXTURE',  'Light Fixture',           'Luminaire',                         'MEP', 'IfcLightFixture',            50, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_OUTLET',        'Electrical Outlet',       'Power/data outlet',                 'MEP', 'IfcOutlet',                  60, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SWITCHDEVICE',  'Switching Device',        'Switch, dimmer, sensor',            'MEP', 'IfcSwitchingDevice',         70, 1);

-- MEP — HVAC
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_AIRTERMINAL',   'Air Terminal',            'Diffuser, grille, register',        'MEP', 'IfcAirTerminal',             80, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FAN',           'Fan',                     'Exhaust/supply fan',                'MEP', 'IfcFan',                     90, 1);

-- MEP — Fire Protection
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FIRESUPPTERM',  'Fire Suppression Terminal','Sprinkler head, nozzle',           'MEP', 'IfcFireSuppressionTerminal', 100, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_SANITARYTERM',  'Sanitary Terminal',       'WC, basin, bath, shower',           'MEP', 'IfcSanitaryTerminal',        110, 1);

-- Architectural
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_DOOR',          'Door',                    'Hinged, sliding, folding',          'ARC', 'IfcDoor',                    10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_WINDOW',        'Window',                  'Fixed, casement, sliding',          'ARC', 'IfcWindow',                  20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FURNISHING',    'Furnishing Element',      'Furniture, equipment (IFC4)',        'ARC', 'IfcFurnishingElement',       30, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_FURNITURE',     'Furniture',               'Furniture (IFC2x3)',                'ARC', 'IfcFurniture',               31, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_RAILING',       'Railing',                 'Guard rail, handrail, balustrade',  'ARC', 'IfcRailing',                 40, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_DISCRETEACC',   'Discrete Accessory',      'Bracket, anchor, support',          'ARC', 'IfcDiscreteAccessory',       50, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('IFC_BLDGELEMPROXY', 'Building Element Proxy',  'Generic/unclassified element',      'ARC', 'IfcBuildingElementProxy',    60, 1);

-- Assembly (non-physical)
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_SET',           'Set',                     'BOM assembly group',                'ASM', NULL, 10, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_PHANTOM',       'Phantom',                 'Phantom BOM (pass-through)',        'ASM', NULL, 20, 1);
INSERT OR IGNORE INTO M_Product_Category VALUES ('ASM_FLOOR',         'Floor',                   'Floor-level assembly',              'ASM', NULL, 30, 1);

-- ============================================================
-- Step 4: Add FK column to M_Product
-- ============================================================

-- SQLite ALTER TABLE ADD COLUMN is safe (no-op if already exists fails gracefully)
ALTER TABLE M_Product ADD COLUMN M_Product_Category_ID TEXT REFERENCES M_Product_Category(M_Product_Category_ID);

-- ============================================================
-- Step 5: Backfill from ifc_class (104 products with ifc_class set)
-- ============================================================

UPDATE M_Product SET M_Product_Category_ID = (
    SELECT c.M_Product_Category_ID
    FROM M_Product_Category c
    WHERE c.IFC_Class = M_Product.ifc_class
)
WHERE ifc_class IS NOT NULL;

-- ============================================================
-- Step 6: Backfill from product_type (83 products with NULL ifc_class)
-- ============================================================

UPDATE M_Product SET M_Product_Category_ID = 'IFC_FURNITURE'  WHERE ifc_class IS NULL AND product_type = 'FURNITURE';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_FURNITURE'  WHERE ifc_class IS NULL AND product_type = 'FURN';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_DOOR'       WHERE ifc_class IS NULL AND product_type = 'DOOR';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_WINDOW'     WHERE ifc_class IS NULL AND product_type = 'WINDOW';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_LIGHTFIXTURE' WHERE ifc_class IS NULL AND product_type = 'ELECTRICAL';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_FIRESUPPTERM' WHERE ifc_class IS NULL AND product_type = 'FP';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_SANITARYTERM' WHERE ifc_class IS NULL AND product_type = 'FIXTURE';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_FLOWTERMINAL'  WHERE ifc_class IS NULL AND product_type = 'DRAINAGE';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_ROOF'        WHERE ifc_class IS NULL AND product_type = 'MESH_ROOF';
UPDATE M_Product SET M_Product_Category_ID = 'IFC_DISCRETEACC' WHERE ifc_class IS NULL AND product_type = 'ITEM';
UPDATE M_Product SET M_Product_Category_ID = 'ASM_SET'         WHERE ifc_class IS NULL AND product_type = 'SET';
UPDATE M_Product SET M_Product_Category_ID = 'ASM_PHANTOM'     WHERE ifc_class IS NULL AND product_type = 'PHANTOM';
UPDATE M_Product SET M_Product_Category_ID = 'ASM_FLOOR'       WHERE ifc_class IS NULL AND product_type = 'FLOOR';

-- ============================================================
-- Step 7: Verification
-- ============================================================

SELECT 'M_Product_Category rows' AS metric, COUNT(*) AS value FROM M_Product_Category
UNION ALL SELECT 'Parent categories', COUNT(*) FROM M_Product_Category WHERE Parent_Category_ID IS NULL
UNION ALL SELECT 'Leaf categories', COUNT(*) FROM M_Product_Category WHERE IFC_Class IS NOT NULL
UNION ALL SELECT 'M_Product with category', COUNT(*) FROM M_Product WHERE M_Product_Category_ID IS NOT NULL
UNION ALL SELECT 'M_Product without category', COUNT(*) FROM M_Product WHERE M_Product_Category_ID IS NULL;
