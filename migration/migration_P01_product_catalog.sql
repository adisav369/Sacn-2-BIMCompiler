-- ============================================================================
-- P0.1-DEDUP: Product Catalog Normalisation — BOM.db migration
-- ============================================================================
-- Applies to: library/BOM.db
-- Prerequisite: Phase 0 complete (122 M_Product rows, C_DocType live)
--
-- Creates M_AttributeSet table, adds 3 columns to M_Product, seeds attribute
-- sets, inserts ~66 new DX-extracted products, updates ~14 overlapping rows.
-- ============================================================================

-- -------------------------------------------------------------------------
-- 1. CREATE TABLE M_AttributeSet
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS M_AttributeSet (
    M_AttributeSet_ID   TEXT PRIMARY KEY,
    Name                TEXT NOT NULL,
    IsInstanceAttribute INTEGER NOT NULL DEFAULT 0,
    Description         TEXT
);

-- -------------------------------------------------------------------------
-- 2. ALTER TABLE M_Product — add 3 new columns
-- -------------------------------------------------------------------------
ALTER TABLE M_Product ADD COLUMN M_AttributeSet_ID TEXT;
ALTER TABLE M_Product ADD COLUMN Name TEXT;
ALTER TABLE M_Product ADD COLUMN Description TEXT;

-- -------------------------------------------------------------------------
-- 3. SEED M_AttributeSet (5 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_AttributeSet (M_AttributeSet_ID, Name, IsInstanceAttribute, Description) VALUES
    ('BIM_Pipe',      'Pipe',      1, 'Linear pipe — cross-section product, length varies per instance'),
    ('BIM_Conduit',   'Conduit',   1, 'Electrical conduit — cross-section product, length varies'),
    ('BIM_Wall',      'Wall',      1, 'Wall type — thickness product, length/height vary'),
    ('BIM_Slab',      'Slab',      1, 'Floor slab — thickness product, area varies'),
    ('BIM_Component', 'Component', 0, 'Discrete items — identical, no instance attributes');

-- -------------------------------------------------------------------------
-- 4. UPDATE existing overlapping M_Product rows (14 rows)
--    Add Name, Description, M_AttributeSet_ID, update extracted_from
-- -------------------------------------------------------------------------

-- Doors
UPDATE M_Product SET
    Name = 'Entry Door 1250x2010',
    Description = 'M_Single-Flush:1250mm x 2010mm:1250mm x 2010mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'DOOR_DUPLEX_ENTRY';

UPDATE M_Product SET
    Name = 'Glass Door 813x2420',
    Description = 'M_Single-Glass 1:0813 x 2420mm:0813 x 2420mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'DOOR_DUPLEX_GLASS';

-- Windows
UPDATE M_Product SET
    Name = 'Fixed Window 819x759',
    Description = 'M_Fixed:819mm x 759mm:819mm x 759mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'WINDOW_DUPLEX_SMALL';

UPDATE M_Product SET
    Name = 'Fixed Window 2800x2410',
    Description = 'M_Fixed:2800mm x 2410mm:2800mm x 2410mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'WINDOW_DUPLEX_BEDROOM';

UPDATE M_Product SET
    Name = 'Fixed Window 4835x2420',
    Description = 'M_Fixed:4835mm x 2420mm:4835mm x 2420mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'WINDOW_DUPLEX_LIVING';

-- Fixtures
UPDATE M_Product SET
    Name = 'Smoke Detector',
    Description = 'M_Smoke Detector:Smoke Detector:Smoke Detector',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'FP_SMOKE';

UPDATE M_Product SET
    Name = 'Toilet Flush Tank',
    Description = 'M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'FIXTURE_TOILET';

UPDATE M_Product SET
    Name = 'Shower Stall Rectangular',
    Description = 'M_Shower Stall - Rectangular:865 mmx815 mm - Private:865 mmx815 mm - Private',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'FIXTURE_SHOWER';

-- Electrical
UPDATE M_Product SET
    Name = 'Duplex Receptacle',
    Description = 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'ELEC_OUTLET';

UPDATE M_Product SET
    Name = 'Light Switch Single Pole',
    Description = 'M_Lighting Switches:Single Pole:Single Pole',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'ELEC_SWITCH';

-- Furniture
UPDATE M_Product SET
    Name = 'Base Cabinet 1000mm',
    Description = 'M_Base Cabinet-Double Door & 2 Drawer:1000mm:1000mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'FURN_KITCHEN_BASE';

UPDATE M_Product SET
    Name = 'Coffee Table Cube 610mm',
    Description = 'M_Table-Coffee:0610 x 0610 x 0610mm:0610 x 0610 x 0610mm',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'Side_Table_Cube_610';

UPDATE M_Product SET
    Name = 'Bed Queen',
    Description = 'M_Bed-Standard:1525 x 2007mm - Queen:1525 x 2007mm - Queen',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'Bed_Queen';

UPDATE M_Product SET
    Name = 'Bed King',
    Description = 'M_Bed-Standard:1981 x 2032mm - King:1981 x 2032mm - King',
    M_AttributeSet_ID = 'BIM_Component',
    extracted_from = 'Ifc2x3_Duplex'
WHERE product_id = 'Bed_King';

-- -------------------------------------------------------------------------
-- 5. INSERT new PIPE products (8 rows)
--    Convention: width=depth=height=diameter (cross-section stamp)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('PIPE_COLD_WATER_13MM',   'PIPE', 0.013, 0.013, 0.013, 'BIM_Pipe', 'Cold Water Pipe 13mm',   'Pipe Types:Cold Water (nominal 1/2")',   'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_COLD_WATER_25MM',   'PIPE', 0.025, 0.025, 0.025, 'BIM_Pipe', 'Cold Water Pipe 25mm',   'Pipe Types:Cold Water (nominal 1")',     'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_HOT_WATER_13MM',    'PIPE', 0.013, 0.013, 0.013, 'BIM_Pipe', 'Hot Water Pipe 13mm',    'Pipe Types:Hot Water (nominal 1/2")',    'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_HOT_WATER_25MM',    'PIPE', 0.025, 0.025, 0.025, 'BIM_Pipe', 'Hot Water Pipe 25mm',    'Pipe Types:Hot Water (nominal 1")',      'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_MECHANICAL_33MM',   'PIPE', 0.033, 0.033, 0.033, 'BIM_Pipe', 'Mechanical Pipe 33mm',   'Pipe Types:Mechanical Pipe',             'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_PVC_33MM',          'PIPE', 0.033, 0.033, 0.033, 'BIM_Pipe', 'PVC Pipe 33mm',          'Pipe Types:PVC',                         'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_WASTE_33MM',        'PIPE', 0.033, 0.033, 0.033, 'BIM_Pipe', 'Waste Pipe 33mm',        'Pipe Types:Waste (nominal 1-1/4")',      'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('PIPE_WASTE_48MM',        'PIPE', 0.048, 0.048, 0.048, 'BIM_Pipe', 'Waste Pipe 48mm',        'Pipe Types:Waste (nominal 2")',          'Ifc2x3_Duplex', 'IfcFlowSegment', 1);

-- -------------------------------------------------------------------------
-- 6. INSERT new CONDUIT/DUCT products (3 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('CONDUIT_EMT_30MM',       'CONDUIT', 0.030, 0.030, 0.030, 'BIM_Conduit', 'EMT Conduit 30mm',       'Conduit with Fittings:Electrical Metallic Tubing (EMT)',                  'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('CONDUIT_ELBOW_STEEL',    'CONDUIT', 0.116, 0.136, 0.154, 'BIM_Component', 'Conduit Elbow Steel',    'M_Conduit Elbow - Steel:Conduit Elbow - Steel:Conduit Elbow - Steel',    'Ifc2x3_Duplex', 'IfcFlowSegment', 1),
    ('DUCT_ROUND_250MM',       'DUCT',    0.250, 0.250, 0.250, 'BIM_Pipe', 'Round Duct 250mm',       'Round Duct:Taps',                                                         'Ifc2x3_Duplex', 'IfcFlowSegment', 1);

-- -------------------------------------------------------------------------
-- 7. INSERT new FITTING products (6 rows)
--    Dims: canonical sorted (smallest, mid, largest)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('FITTING_ELBOW_GENERIC',      'FITTING', 0.033, 0.033, 0.034, 'BIM_Component', 'Elbow Generic',                   'M_Elbow - Generic:Elbow - Generic:Elbow - Generic',                                             'Ifc2x3_Duplex', 'IfcFlowFitting', 1),
    ('FITTING_TEE_GENERIC',        'FITTING', 0.045, 0.048, 0.059, 'BIM_Component', 'Tee Generic',                     'M_Tee - Generic:Tee - Generic:Tee - Generic',                                                   'Ifc2x3_Duplex', 'IfcFlowFitting', 1),
    ('FITTING_TRANSITION_GENERIC', 'FITTING', 0.025, 0.030, 0.033, 'BIM_Component', 'Transition Generic',              'M_Transition - Generic:Transition - Generic:Transition - Generic',                               'Ifc2x3_Duplex', 'IfcFlowFitting', 1),
    ('FITTING_BEND_PVC_DWV',       'FITTING', 0.066, 0.072, 0.077, 'BIM_Component', 'Bend PVC DWV',                    'M_Bend - PVC - Sch 40 - DWV:Bend - PVC - Sch 40 - DWV:Bend - PVC - Sch 40 - DWV',              'Ifc2x3_Duplex', 'IfcFlowFitting', 1),
    ('FITTING_ROOF_DRAIN_380',     'FITTING', 0.300, 0.300, 0.585, 'BIM_Component', 'Roof Drain 380mm',                'M_Roof Drain:380 mm Strainer - 25 mm Drain:380 mm Strainer - 25 mm Drain',                       'Ifc2x3_Duplex', 'IfcFlowFitting', 1),
    ('FITTING_RECT_ROUND_45',      'FITTING', 0.063, 0.250, 0.250, 'BIM_Component', 'Rectangular to Round Transition', 'M_Rectangular to Round Transition - Angle:45 Degree:45 Degree',                                  'Ifc2x3_Duplex', 'IfcFlowFitting', 1);

-- -------------------------------------------------------------------------
-- 8. INSERT new TERMINAL/CONTROLLER products (17 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('LIGHT_PENDANT_150W',     'ELECTRICAL', 0.506, 0.507, 0.675, 'BIM_Component', 'Pendant Light 150W',              'M_Pendant Light - Hemisphere:150W - 120V:150W - 120V',                                           'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('LIGHT_SCONCE_100W',      'ELECTRICAL', 0.200, 0.397, 0.400, 'BIM_Component', 'Sconce Light 100W',               'M_Sconce Light - Sphere:100W - 120V:100W - 120V',                                                'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('TELEPHONE_OUTLET',       'ELECTRICAL', 0.064, 0.064, 0.114, 'BIM_Component', 'Telephone Outlet',                'M_Telephone Outlet:Telophone Outlet:Telophone Outlet',                                           'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('TELEPHONE_BOARD',        'ELECTRICAL', 0.044, 0.300, 0.300, 'BIM_Component', 'Telephone Terminal Board',         'M_Telephone Terminal Board:TTB 300x300:TTB1',                                                    'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('PANELBOARD_208V_400A',   'ELECTRICAL', 0.146, 0.508, 1.270, 'BIM_Component', 'Panelboard 208V 400A',            'M_Lighting and Appliance Panelboard - 208V MLO:400 A',                                           'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('FIRE_ALARM_PANEL',       'FP',         0.138, 0.400, 0.475, 'BIM_Component', 'Fire Alarm Control Panel',        'M_Fire Alarm Control Panel:400x475:Fire Alarm Panel',                                            'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('BALL_VALVE_50MM',        'VALVE',      0.080, 0.139, 0.216, 'BIM_Component', 'Ball Valve 50mm',                 'M_Ball Valve - 50-150 mm:50 mm:50 mm',                                                           'Ifc2x3_Duplex', 'IfcFlowController',  1),
    ('BACKFLOW_PREVENTER_25MM','VALVE',      0.095, 0.125, 0.360, 'BIM_Component', 'Backflow Preventer 25mm',         'M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm',                             'Ifc2x3_Duplex', 'IfcFlowController',  1),
    ('LAVATORY_OVAL_650',      'FIXTURE',    0.228, 0.485, 0.650, 'BIM_Component', 'Lavatory Oval 650mm',             'M_Lavatory - Oval:650 mmx485 mm - Private:650 mmx485 mm - Private',                              'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('LAVATORY_OVAL_535',      'FIXTURE',    0.228, 0.485, 0.535, 'BIM_Component', 'Lavatory Oval 535mm',             'M_Lavatory - Oval:535 mmx485 mm - Private:535 mmx485 mm - Private',                              'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('SINK_ISLAND',            'FIXTURE',    0.455, 0.457, 0.486, 'BIM_Component', 'Sink Island Single',              'M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private',                       'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('BATH_TUB_1525',          'FIXTURE',    0.578, 0.760, 1.525, 'BIM_Component', 'Bath Tub 1525mm',                 'M_Bath Tub:1525 mmx760 mm - Private:1525 mmx760 mm - Private',                                   'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('REFRIGERATOR',           'APPLIANCE',  0.804, 0.850, 1.830, 'BIM_Component', 'Refrigerator',                    'M_Refrigerator:850 x 760mm:850 x 760mm',                                                        'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('RANGE',                  'APPLIANCE',  0.663, 0.760, 1.041, 'BIM_Component', 'Range',                           'M_Range:760 x 650mm:760 x 650mm',                                                               'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('MICROWAVE',              'APPLIANCE',  0.399, 0.450, 0.760, 'BIM_Component', 'Microwave',                       'M_Microwave:760 x 400 x 450mm:760 x 400 x 450mm',                                               'Ifc2x3_Duplex', 'IfcFlowTerminal',    1),
    ('COUNTER_TOP_600',        'FURNITURE',  0.142, 0.625, 2.512, 'BIM_Component', 'Counter Top 600mm',               'M_Counter Top:600mm Depth:600mm Depth',                                                          'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('COUNTER_TOP_SINK_600',   'FURNITURE',  0.142, 0.625, 3.000, 'BIM_Component', 'Counter Top with Sink 600mm',     'M_Counter Top w Sink Hole:600mm Depth:600mm Depth',                                              'Ifc2x3_Duplex', 'IfcFurnishingElement', 1);

-- -------------------------------------------------------------------------
-- 9. INSERT new FURNISHING products (6 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('TALL_CABINET_800',       'FURNITURE',  0.545, 0.800, 2.000, 'BIM_Component', 'Tall Cabinet 800mm',              'M_Tall Cabinet-Single Door(2):800 mm:800 mm',                                                    'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('UPPER_CABINET_1000',     'FURNITURE',  0.345, 0.600, 1.000, 'BIM_Component', 'Upper Cabinet 1000mm',            'M_Upper Cabinet-Double Door-Wall:1000 mm:1000 mm',                                               'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('SOFA_1830',              'FURNITURE',  0.813, 1.245, 1.245, 'BIM_Component', 'Sofa 1830mm',                     'M_Sofa:1830mm:1830mm',                                                                           'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('COFFEE_TABLE_RECT',      'FURNITURE',  0.457, 0.915, 1.830, 'BIM_Component', 'Coffee Table Rectangular',        'M_Table-Coffee:0915 x 1830 x 0457mm:0915 x 1830 x 0457mm',                                      'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('VANITY_CABINET_650',     'FURNITURE',  0.475, 0.650, 0.820, 'BIM_Component', 'Vanity Cabinet 650x450',          'M_Vanity Cabinet-Double Door Sink Unit:650 x 450 mm:650 x 450 mm',                               'Ifc2x3_Duplex', 'IfcFurnishingElement', 1),
    ('VANITY_CABINET_450',     'FURNITURE',  0.450, 0.475, 0.820, 'BIM_Component', 'Vanity Cabinet 450x450',          'M_Vanity Cabinet-Double Door Sink Unit:450 x 450 mm:450 x 450 mm',                               'Ifc2x3_Duplex', 'IfcFurnishingElement', 1);

-- -------------------------------------------------------------------------
-- 10. INSERT new DOOR products (2 rows — 2 overlap handled above)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('DOOR_FLUSH_864',         'DOOR', 0.455, 0.735, 2.108, 'BIM_Component', 'Flush Door 864x2032',             'M_Single-Flush:0864 x 2032mm:0864 x 2032mm',                                                    'Ifc2x3_Duplex', 'IfcDoor', 1),
    ('DOOR_FLUSH_762',         'DOOR', 0.544, 0.544, 2.108, 'BIM_Component', 'Flush Door 762x2032',             'M_Single-Flush:0762 x 2032mm:0762 x 2032mm',                                                    'Ifc2x3_Duplex', 'IfcDoor', 1);

-- -------------------------------------------------------------------------
-- 11. INSERT new WINDOW products (3 rows — 3 overlap handled above)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('WINDOW_CASEMENT_819',    'WINDOW', 0.618, 0.618, 0.759, 'BIM_Component', 'Casement Window 819x759',         'M_Casement:819mm x 759mm:819mm x 759mm',                                                        'Ifc2x3_Duplex', 'IfcWindow', 1),
    ('WINDOW_FIXED_750x2200',  'WINDOW', 0.417, 0.750, 2.200, 'BIM_Component', 'Fixed Window 750x2200',           'M_Fixed:750mm x 2200mm:750mm x 2200mm',                                                         'Ifc2x3_Duplex', 'IfcWindow', 1),
    ('SKYLIGHT_1180',          'WINDOW', 0.178, 1.173, 1.225, 'BIM_Component', 'Skylight 1180x1170',              'M_Skylight:1180 x 1170mm:1180 x 1170mm',                                                        'Ifc2x3_Duplex', 'IfcWindow', 1);

-- -------------------------------------------------------------------------
-- 12. INSERT new BEAM products (2 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('BEAM_W410X60',           'STRUCTURAL', 0.407, 1.492, 3.487, 'BIM_Component', 'W-Flange W410X60',               'M_W-Wide Flange:W410X60:W410X60',                                                                'Ifc2x3_Duplex', 'IfcBeam', 1),
    ('BEAM_W310X60',           'STRUCTURAL', 0.203, 0.303, 7.421, 'BIM_Component', 'W-Flange W310X60',               'M_W-Wide Flange:W310X60:W310X60',                                                                'Ifc2x3_Duplex', 'IfcBeam', 1);

-- -------------------------------------------------------------------------
-- 13. INSERT new WALL products (8 rows)
--     Convention: width=thickness, depth=1.0, height=1.0
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('WALL_INT_PARTITION_92',  'WALL', 0.124, 1.0, 1.0, 'BIM_Wall', 'Interior Partition 92mm Stud',              'Basic Wall:Interior - Partition (92mm Stud)',                                                     'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_EXT_BRICK_BLOCK',   'WALL', 0.417, 1.0, 1.0, 'BIM_Wall', 'Exterior Brick on Block',                   'Basic Wall:Exterior - Brick on Block',                                                            'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_INT_FURRING_38',    'WALL', 0.054, 1.0, 1.0, 'BIM_Wall', 'Interior Furring 38mm Stud',                'Basic Wall:Interior - Furring (38 mm Stud)',                                                      'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_INT_FURRING_152',   'WALL', 0.152, 1.0, 1.0, 'BIM_Wall', 'Interior Furring 152mm Stud',               'Basic Wall:Interior - Furring (152 mm Stud)',                                                     'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_FOUNDATION_417',    'WALL', 0.417, 1.0, 1.0, 'BIM_Wall', 'Foundation Concrete 417mm',                 'Basic Wall:Foundation - Concrete (417mm)',                                                         'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_PARTY_CMU',         'WALL', 0.493, 1.0, 1.0, 'BIM_Wall', 'Party Wall CMU',                            'Basic Wall:Party Wall - CMU Residential Unit Dimising Wall',                                      'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_FOUNDATION_435',    'WALL', 0.435, 1.0, 1.0, 'BIM_Wall', 'Foundation Concrete 435mm',                 'Basic Wall:Foundation - Concrete (435mm)',                                                         'Ifc2x3_Duplex', 'IfcWall', 1),
    ('WALL_INT_PLUMBING_152',  'WALL', 0.184, 1.0, 1.0, 'BIM_Wall', 'Interior Plumbing 152mm Stud',              'Basic Wall:Interior - Plumbing (152mm Stud)',                                                     'Ifc2x3_Duplex', 'IfcWall', 1);

-- -------------------------------------------------------------------------
-- 14. INSERT new SLAB products (6 rows)
--     Convention: width=thickness, depth=1.0, height=1.0
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('SLAB_FINISH_WOOD',       'SLAB', 0.019, 1.0, 1.0, 'BIM_Slab', 'Finish Floor Wood',                        'Floor:Finish Floor - Wood',                                                                       'Ifc2x3_Duplex', 'IfcSlab', 1),
    ('SLAB_FINISH_CERAMIC',    'SLAB', 0.013, 1.0, 1.0, 'BIM_Slab', 'Finish Floor Ceramic Tile',                'Floor:Finish Floor - Ceramic Tile',                                                               'Ifc2x3_Duplex', 'IfcSlab', 1),
    ('SLAB_GRADE_127',         'SLAB', 0.127, 1.0, 1.0, 'BIM_Slab', 'Slab on Grade 127mm',                      'Floor:127mm Slab on Grade',                                                                       'Ifc2x3_Duplex', 'IfcSlab', 1),
    ('SLAB_GRADE_150',         'SLAB', 0.150, 1.0, 1.0, 'BIM_Slab', 'Exterior Slab on Grade 150mm',             'Floor:150mm Exterior Slab on Grade',                                                              'Ifc2x3_Duplex', 'IfcSlab', 1),
    ('SLAB_WOOD_JOIST',        'SLAB', 0.305, 1.0, 1.0, 'BIM_Slab', 'Wood Joist with Subflooring',              'Floor:Residential - Wood Joist with Subflooring',                                                 'Ifc2x3_Duplex', 'IfcSlab', 1),
    ('SLAB_ROOF_LIVE',         'SLAB', 0.457, 1.0, 1.0, 'BIM_Slab', 'Live Roof over Wood Joist',                'Basic Roof:Live Roof over Wood Joist Flat Roof',                                                  'Ifc2x3_Duplex', 'IfcSlab', 1);

-- -------------------------------------------------------------------------
-- 15. INSERT new RAILING products (2 rows)
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('RAILING_GUARD_1100',     'STRUCTURAL', 0.090, 3.805, 3.952, 'BIM_Component', 'Guard Rail 1100mm',               'Railing:1100mm Guard Rail',                                                                       'Ifc2x3_Duplex', 'IfcRailing', 1),
    ('RAILING_HANDRAIL_900',   'STRUCTURAL', 0.040, 2.957, 3.750, 'BIM_Component', 'Handrail 900mm',                  'Railing:900mm Handrail Only',                                                                     'Ifc2x3_Duplex', 'IfcRailing', 1);

-- -------------------------------------------------------------------------
-- 16. INSERT new STAIR products (2 rows)
--     Stair stringers (IfcMember) and stair flights (IfcStairFlight) are
--     separate products — same family, different geometry.
-- -------------------------------------------------------------------------
INSERT INTO M_Product (product_id, product_type, width, depth, height, M_AttributeSet_ID, Name, Description, extracted_from, ifc_class, is_active) VALUES
    ('STAIR_STRINGER',         'STRUCTURAL', 0.050, 3.100, 3.750, 'BIM_Component', 'Stair Stringer Residential',      'Stair:Residential - 200mm Max Riser 250mm Tread',                                                'Ifc2x3_Duplex', 'IfcMember',      1),
    ('STAIR_FLIGHT',           'STRUCTURAL', 0.914, 3.772, 3.050, 'BIM_Component', 'Stair Flight Residential',        'Stair:Residential - 200mm Max Riser 250mm Tread',                                                'Ifc2x3_Duplex', 'IfcStairFlight', 1);

-- ============================================================================
-- END OF MIGRATION
-- Total: 14 UPDATEs + 66 INSERTs = 80 products touched
-- Final M_Product count: 122 + 66 = 188
-- ============================================================================
