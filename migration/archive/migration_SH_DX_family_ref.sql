-- Phase RM-11 Step 4: Fix family_ref for SH/DX furniture
-- Root Cause 3 fix: SH/DX IfcFurnishingElement rows have Revit family names
-- as family_ref (extracted verbatim from IFC). These don't exist in ad_product_dim.
-- This migration maps Revit names → canonical product_ids.
--
-- NOTE: SH/DX ABSOLUTE rows have host_type=BUILDING (world coords), NOT host_type=ROOM.
-- They are legitimate world-coordinate placements from the reference IFC.
-- Only ROOM-level ABSOLUTE placement is the "flat data" problem (blocked by Step 5).

-- ─── New products needed for SH/DX furniture types ───────────────────────────
INSERT OR IGNORE INTO ad_product_dim
    (product_id, product_type, width, depth, height, clear_front, is_active)
VALUES
    -- Coffee table (M_Table-Coffee various sizes)
    ('FURN_COFFEE_TABLE',     'FURNITURE', 1.0,  0.6,  0.45, 0.3,  1),
    -- Kitchen base cabinet (M_Base Cabinet-Double Door & 2 Drawer:1000mm)
    ('FURN_KITCHEN_BASE',     'FURNITURE', 1.0,  0.625, 0.86, 0.6,  1),
    -- Kitchen upper cabinet (M_Upper Cabinet-Double Door-Wall:1000mm, height 1400mm)
    ('FURN_KITCHEN_UPPER',    'FURNITURE', 1.0,  0.344, 1.4,  0.0,  1),
    -- Kitchen counter top (M_Counter Top:600mm Depth, various widths)
    ('FURN_KITCHEN_COUNTER',  'FURNITURE', 3.0,  0.625, 0.86, 0.6,  1),
    -- Desk (Furniture_Desk:1525x762mm)
    ('FURN_DESK',             'FURNITURE', 1.525,0.762, 0.75, 0.6,  1),
    -- Piano (Furniture_Piano:1370x600x1170mm)
    ('FURN_PIANO',            'FURNITURE', 1.371,0.6,   1.17, 0.6,  1),
    -- King bed (M_Bed-Standard:1981x2032mm)
    ('FURN_BED_KING',         'FURNITURE', 2.032,1.981, 0.5,  0.6,  1);

-- ─── Ifc4_SampleHouse family_ref fix ─────────────────────────────────────────
-- Map Revit family names → product_ids using CASE on the existing family_ref value.
-- Only touches IfcFurnishingElement rows for this building.

UPDATE ad_element_rule
SET family_ref = CASE family_ref
    WHEN 'Chair - Dining:Chair - Dining'
         THEN 'FURN_DINING_CHAIR'
    WHEN 'Furniture_Table_Coffee_1:1200x550x450mm'
         THEN 'FURN_COFFEE_TABLE'
    WHEN 'Furniture_Chair_Viper:1120x940x350mm'
         THEN 'FURN_DINING_CHAIR'
    WHEN 'Furniture_Piano:1370x600x1170mm'
         THEN 'FURN_PIANO'
    WHEN 'Furniture_Table_Dining_w-Chairs_Rectangular:2000x1000x750mm_w-6_Seats'
         THEN 'FURN_DINING_TABLE'
    WHEN 'Furniture_Couch_Viper:2290x950x340mm'
         THEN 'FURN_SOFA'
    WHEN 'Furniture_Bed_1:1525x2007x355mm-Queen'
         THEN 'FURN_BED_DOUBLE'
    WHEN 'Furniture_Desk:1525x762mm'
         THEN 'FURN_DESK'
    ELSE family_ref
END
WHERE building_type = 'Ifc4_SampleHouse'
  AND ifc_class = 'IfcFurnishingElement'
  AND is_active = 1;

-- ─── Ifc2x3_Duplex family_ref fix ────────────────────────────────────────────
-- Map Revit family names → product_ids. DX has 61 IfcFurnishingElement rows
-- (8 ABSOLUTE tall cabinets + 53 FRACTION kitchen/bedroom/living items).
-- Vanity cabinet with sink → FIXTURE_SINK (has BACK conn_point → correct rotation).

UPDATE ad_element_rule
SET family_ref = CASE family_ref
    WHEN 'M_Tall Cabinet-Single Door(2):800 mm:800 mm'
         THEN 'FURN_WARDROBE'
    WHEN 'M_Upper Cabinet-Double Door-Wall:1000 mm:1000 mm'
         THEN 'FURN_KITCHEN_UPPER'
    WHEN 'M_Upper Cabinet-Double Door-Wall:1000mm:1000mm'
         THEN 'FURN_KITCHEN_UPPER'
    WHEN 'M_Sofa:1830mm:1830mm'
         THEN 'FURN_SOFA'
    WHEN 'M_Table-Coffee:0915 x 1830 x 0457mm:0915 x 1830 x 0457mm'
         THEN 'FURN_COFFEE_TABLE'
    WHEN 'M_Base Cabinet-Double Door & 2 Drawer:1000mm:1000mm'
         THEN 'FURN_KITCHEN_BASE'
    WHEN 'M_Base Cabinet-Double Door & 2 Drawer:1000 mm:1000 mm'
         THEN 'FURN_KITCHEN_BASE'
    WHEN 'M_Counter Top w Sink Hole:600mm Depth:600mm Depth'
         THEN 'FURN_KITCHEN_COUNTER'
    WHEN 'M_Counter Top:600mm Depth:600mm Depth'
         THEN 'FURN_KITCHEN_COUNTER'
    WHEN 'M_Counter Top:600mm Depth'
         THEN 'FURN_KITCHEN_COUNTER'
    WHEN 'M_Table-Coffee:0610 x 0610 x 0610mm:0610 x 0610 x 0610mm'
         THEN 'FURN_COFFEE_TABLE'
    WHEN 'M_Bed-Standard:1525 x 2007mm - Queen:1525 x 2007mm - Queen'
         THEN 'FURN_BED_DOUBLE'
    WHEN 'M_Bed-Standard:1981 x 2032mm - King:1981 x 2032mm - King'
         THEN 'FURN_BED_KING'
    WHEN 'M_Vanity Cabinet-Double Door Sink Unit:650 x 450 mm:650 x 450 mm'
         THEN 'FIXTURE_SINK'
    WHEN 'M_Vanity Cabinet-Double Door Sink Unit:450 x 450 mm:450 x 450 mm'
         THEN 'FIXTURE_SINK'
    ELSE family_ref
END
WHERE building_type = 'Ifc2x3_Duplex'
  AND ifc_class = 'IfcFurnishingElement'
  AND is_active = 1;

-- ─── Verification queries ─────────────────────────────────────────────────────
-- Expected: 0 rows (zero furniture with invalid family_ref for SH/DX)
-- SELECT er.element_ref, er.family_ref
-- FROM ad_element_rule er
-- LEFT JOIN ad_product_dim pd ON er.family_ref = pd.product_id
-- WHERE er.building_type IN ('Ifc4_SampleHouse','Ifc2x3_Duplex')
--   AND er.ifc_class = 'IfcFurnishingElement'
--   AND er.is_active = 1
--   AND pd.product_id IS NULL;
