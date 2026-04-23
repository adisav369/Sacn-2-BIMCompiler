-- DV036: Wire M_Product_Category → AD_Org_ID discipline chain
-- Implementing DISC_VALIDATION_DB_SRS.md §6.4 — Witness: W-DISC-CAT
--
-- Completes the chain: M_Product → M_Product_Category → AD_Org_ID
-- so discipline resolves from the child product per §6.4 BOM Tree Structure.
--
-- Applied to: ERP.db (library/ERP.db)

-- ═══════════════════════════════════════════════════════════════════
-- Step 1: Add AD_Org_ID column to M_Product_Category
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE M_Product_Category ADD COLUMN AD_Org_ID INTEGER
    REFERENCES AD_Org(AD_Org_ID);

-- ═══════════════════════════════════════════════════════════════════
-- Step 2: Map abstract discipline categories to AD_Org
-- ═══════════════════════════════════════════════════════════════════

UPDATE M_Product_Category SET AD_Org_ID = 2  WHERE Value = 'STR';   -- Structural
UPDATE M_Product_Category SET AD_Org_ID = 10 WHERE Value = 'MEP';   -- MEP generic
UPDATE M_Product_Category SET AD_Org_ID = 1  WHERE Value = 'ARC';   -- Architecture
UPDATE M_Product_Category SET AD_Org_ID = 0  WHERE Value = 'ASM';   -- Assembly (shared)

-- Specific discipline categories
UPDATE M_Product_Category SET AD_Org_ID = 3  WHERE Value = 'FP';    -- Fire Protection
UPDATE M_Product_Category SET AD_Org_ID = 5  WHERE Value = 'ACMV';  -- HVAC
UPDATE M_Product_Category SET AD_Org_ID = 4  WHERE Value = 'ELEC';  -- Electrical
UPDATE M_Product_Category SET AD_Org_ID = 6  WHERE Value = 'CW';    -- Cold Water
UPDATE M_Product_Category SET AD_Org_ID = 7  WHERE Value = 'SP';    -- Sanitary/Plumbing
UPDATE M_Product_Category SET AD_Org_ID = 8  WHERE Value = 'LPG';   -- Gas Piping

-- ═══════════════════════════════════════════════════════════════════
-- Step 3: Map IFC-class categories to discipline AD_Org
-- ═══════════════════════════════════════════════════════════════════

-- ARC (AD_Org=1): architectural elements
UPDATE M_Product_Category SET AD_Org_ID = 1 WHERE Value IN (
    'IFC_WALL','IFC_SLAB','IFC_ROOF','IFC_STAIR','IFC_STAIRFLIGHT',
    'IFC_RAMPFLIGHT','IFC_DOOR','IFC_WINDOW','IFC_FURNISHING',
    'IFC_FURNITURE','IFC_RAILING','IFC_COVERING','IFC_OPENINGELEMENT',
    'IFC_BLDGELEMPROXY');

-- STR (AD_Org=2): structural elements
UPDATE M_Product_Category SET AD_Org_ID = 2 WHERE Value IN (
    'IFC_BEAM','IFC_COLUMN','IFC_MEMBER','IFC_PLATE',
    'IFC_FOOTING','IFC_PILE');

-- MEP generic (AD_Org=10): pipe/duct segments+fittings (discipline from RouteWalker)
UPDATE M_Product_Category SET AD_Org_ID = 10 WHERE Value IN (
    'IFC_FLOWSEGMENT','IFC_FLOWFITTING','IFC_FLOWCONTROLLER',
    'IFC_FLOWTERMINAL','IFC_PIPESEGMENT','IFC_PIPEFITTING',
    'IFC_DUCTSEGMENT','IFC_DUCTFITTING','IFC_VALVE');

-- ELEC (AD_Org=4): electrical-specific terminals
UPDATE M_Product_Category SET AD_Org_ID = 4 WHERE Value IN (
    'IFC_LIGHTFIXTURE','IFC_OUTLET','IFC_SWITCHDEVICE',
    'IFC_ELECTRICAPPL','IFC_CONTROLLER','IFC_SENSOR');

-- ACMV (AD_Org=5): HVAC terminals
UPDATE M_Product_Category SET AD_Org_ID = 5 WHERE Value = 'IFC_AIRTERMINAL';

-- FP (AD_Org=3): fire protection terminals
UPDATE M_Product_Category SET AD_Org_ID = 3 WHERE Value IN (
    'IFC_FIRESUPPTERM','IFC_ALARM');

-- SP (AD_Org=7): sanitary terminals
UPDATE M_Product_Category SET AD_Org_ID = 7 WHERE Value = 'IFC_SANITARYTERM';

-- ═══════════════════════════════════════════════════════════════════
-- Step 4: Backfill M_Product.M_Product_Category_ID from ifc_class
-- Only for products where category is currently NULL.
-- ═══════════════════════════════════════════════════════════════════

UPDATE M_Product
SET M_Product_Category_ID = (
    SELECT c.M_Product_Category_ID
    FROM M_Product_Category c
    WHERE c.IFC_Class = M_Product.ifc_class
    LIMIT 1
)
WHERE M_Product_Category_ID IS NULL
  AND ifc_class IS NOT NULL;

-- ═══════════════════════════════════════════════════════════════════
-- Step 5: Fix smoke detector discipline in _import_joint_piece_types
-- Smoke detectors are FP (Fire Protection), not CW (Cold Water).
-- ═══════════════════════════════════════════════════════════════════

UPDATE _import_joint_piece_types
SET discipline = 'FP'
WHERE ifc_class = 'IfcFlowController'
  AND element_type = 'Smoke Detector';

-- ═══════════════════════════════════════════════════════════════════
-- Step 6: Bump schema version
-- ═══════════════════════════════════════════════════════════════════

UPDATE AD_SysConfig SET Value = '36' WHERE Name = 'SCHEMA_VERSION';
