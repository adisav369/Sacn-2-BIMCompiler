-- S67_001_onboard_elec_products.sql
-- Implementing DISC_VALIDATION_DB_SRS.md §12 — Witness: W-DM-TC5-3
--
-- Onboards 2 ELEC products from TE extraction into disc_validation.db M_Product.
-- Geometry already exists in component_library.db component_definitions.
-- Same pattern as S62 FP onboarding (S62_001_product_category_fp.sql Step 6).
--
-- Idempotent: INSERT OR IGNORE.

-- ============================================================
-- Step 1: Onboard ELEC products
-- Dimensions from component_definitions (metres, extracted not invented)
-- Geometry already exists: E_Light_1 X 14W (hash 0b7d13f1ea16db04), E_Data Point_V1
-- ============================================================

INSERT OR IGNORE INTO M_Product (product_id, product_type, width, depth, height, ifc_class, extracted_from, is_active, M_Product_Category_ID)
VALUES
    ('E_Light_1 X 14W_Surface_LED T8_V1', 'ELEC', 0.0540, 0.6000, 0.0806, 'IfcLightFixture',     'IFC_EXTRACTION', 1, 'IFC_LIGHTFIXTURE'),
    ('E_Data Point_V1',                    'ELEC', 0.0870, 0.0480, 0.0870, 'IfcElectricAppliance', 'IFC_EXTRACTION', 1, 'IFC_ELECTRICAPPL');

-- ============================================================
-- Step 2: Verification
-- ============================================================

SELECT 'ELEC products' AS metric, COUNT(*) AS value FROM M_Product
WHERE product_id IN ('E_Light_1 X 14W_Surface_LED T8_V1', 'E_Data Point_V1');
