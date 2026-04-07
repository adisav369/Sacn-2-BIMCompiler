-- CL_001: Seed M_Product_Image for generative MEP device products
-- Implementing DISC_VALIDATION_DB_SRS.md §12a Priority 1 — Witness: W-LOD-BRIDGE
--
-- Problem: Generative devices (TOILET, SINK, LIGHT, etc.) are abstract product
-- tokens with no building_type. ensureProductImages() only bridges extracted
-- products with building_type. These abstract tokens need M_Product_Image rows
-- so MeshBinder.resolveByProduct() can find their geometry.
--
-- Source: DV046 mapped each generative product_id → IFC family name in
-- source_element_ref. This migration creates the M_Product_Image rows by
-- joining those family names to I_Geometry_Map.
--
-- Idempotent: INSERT OR IGNORE — safe to re-run.
-- Target DB: component_library.db (NOT ERP.db)

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'TOILET', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'SINK', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'LIGHT', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Pendant Light - Hemisphere:150W - 120V:150W - 120V'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'SWITCH', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Lighting Switches:Single Pole:Single Pole'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'OUTLET', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'FRIDGE', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Refrigerator:850 x 760mm:850 x 760mm'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'SMOKE_DETECTOR', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Smoke Detector:Smoke Detector:Smoke Detector'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'SUPPLY_DIFFUSER', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Supply Diffuser - Rectangular Face Round Neck:600x600 - 250 Neck'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'EXHAUST_FAN', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Exhaust FAn Ventilation:1200mm x 1100mm'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'CEILING_FAN', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'E_Fan_Ceiling_1500mm_V1:Ceiling Fan'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'SPRINKLER', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Sprinkler - Pendent - Hosted:15 mm Pendent on Drop with Guard'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'AIRCON_POINT', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'Fancoil Unit:ACSU-02'
LIMIT 1;

-- DV048/S156: Remaining 3 unmapped generative products
INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'FLOOR_TRAP', geometry_hash FROM I_Geometry_Map
WHERE element_ref LIKE '%Floor Trap%'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'OUTLET_20A', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Duplex Receptacle:Standard'
LIMIT 1;

INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
SELECT 'OUTLET_GFCI', geometry_hash FROM I_Geometry_Map
WHERE element_ref = 'M_Duplex Receptacle:GFCI'
LIMIT 1;
