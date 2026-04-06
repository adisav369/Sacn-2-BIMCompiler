-- DV043: Generative MEP products — devices the compiler places from schedule metadata
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 — Witness: W-DEVICE-PLACE
--
-- These products exist in ad_space_type_mep_bom (schedule) but had no M_Product.
-- The compiler generates placements from rules — these are NOT extracted from IFC.
-- Dimensions are standard catalogue sizes for volume estimation + LOD mesh binding.

-- MEP fixture products (gap closers from S150 S12)
INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, is_active,
    width, depth, height)
VALUES
    ('SPRINKLER',    'SPRINKLER',    'Fire Sprinkler Head',  'FIXTURE', 1, 0.10, 0.10, 0.15),
    ('OUTLET_GFCI',  'OUTLET_GFCI',  'GFCI Outlet',          'FIXTURE', 1, 0.07, 0.04, 0.12);

-- Additional scheduled devices found by S14 master gap report
INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, is_active,
    width, depth, height)
VALUES
    ('OUTLET_20A',      'OUTLET_20A',      '20A Power Outlet',    'FIXTURE', 1, 0.07, 0.04, 0.12),
    ('AIRCON_POINT',    'AIRCON_POINT',    'Aircon Connection',   'FIXTURE', 1, 0.20, 0.10, 0.10),
    ('DATA_POINT',      'DATA_POINT',      'Data Point',          'FIXTURE', 1, 0.07, 0.04, 0.07),
    ('EMERGENCY_LIGHT', 'EMERGENCY_LIGHT', 'Emergency Light',     'FIXTURE', 1, 0.30, 0.10, 0.10);

-- Furniture-as-MEP-endpoint: fridge needs ELECTRIFIED capability
INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, is_active,
    width, depth, height)
VALUES
    ('FRIDGE', 'FRIDGE', 'Refrigerator', 'FIXTURE', 1, 0.70, 0.70, 1.80);

-- FRIDGE in KITCHEN schedule — placed by knowing the space type
-- anchor=PANEL (needs electrical outlet), host=FLOOR (floor-standing)
INSERT OR IGNORE INTO ad_space_type_mep_bom
    (space_type_id, mep_product_id, qty_min, qty_normal, qty_max,
     placement_rule, host_surface, anchor_end)
VALUES
    ('KITCHEN',     'FRIDGE', 1, 1, 1, 'WALL_FLOOR', 'FLOOR', 'PANEL'),
    ('WET_KITCHEN', 'FRIDGE', 1, 1, 1, 'WALL_FLOOR', 'FLOOR', 'PANEL');

-- Placement offset for WALL_HIGH (aircon point, already in schedule but no offset)
INSERT OR IGNORE INTO ad_placement_offset VALUES
    ('WALL_HIGH', 'High wall mount (aircon, exhaust)',
     0.15, 0, 'CEILING', 0.30, 'MIN', 'CENTER', NULL, 'Aircon 300mm below ceiling, 150mm from wall'),
    ('WALL_FLOOR', 'Floor-standing against wall (fridge, washer)',
     0.05, 0, 'FLOOR', 0, 'MIN', 'CENTER', NULL, 'Floor level, 50mm from wall');
