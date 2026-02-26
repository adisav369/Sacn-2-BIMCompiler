-- ============================================================
-- Phase C: BOM Product Material
-- Adds material_name and material_rgba to ad_product_dim.
--
-- Material IS intrinsic to the product (Piano = Wood, Toilet = Ceramic)
-- — same authority level as width/depth/height.
-- THREE-TABLE AUTHORITY: "no rotation, no position" — material is neither.
--
-- RGBA format: "R,G,B,A" normalized floats (0.0–1.0), matching
-- elements_meta.material_rgba convention in output DBs.
--
-- Idempotent: ALTER TABLE is guarded by column existence check.
-- ============================================================

-- ── Add columns ───────────────────────────────────────────────────────────────
-- SQLite does not support IF NOT EXISTS on ALTER TABLE, so these will
-- error harmlessly if columns already exist. Run with `sqlite3 -bail`
-- disabled, or wrap in a script that ignores "duplicate column" errors.

ALTER TABLE ad_product_dim ADD COLUMN material_name TEXT;
ALTER TABLE ad_product_dim ADD COLUMN material_rgba TEXT;

-- ── Seed FIXTURE materials ────────────────────────────────────────────────────

UPDATE ad_product_dim SET material_name = 'Ceramic',      material_rgba = '0.950,0.950,0.950,1.000'
WHERE product_id = 'FIXTURE_SHOWER'       AND material_name IS NULL;

UPDATE ad_product_dim SET material_name = 'Ceramic',      material_rgba = '0.950,0.950,0.950,1.000'
WHERE product_id = 'FIXTURE_SINK'         AND material_name IS NULL;

UPDATE ad_product_dim SET material_name = 'Ceramic',      material_rgba = '0.950,0.950,0.950,1.000'
WHERE product_id = 'FIXTURE_TOILET'       AND material_name IS NULL;

UPDATE ad_product_dim SET material_name = 'Metal - Steel', material_rgba = '0.753,0.753,0.753,1.000'
WHERE product_id = 'FIXTURE_WATER_HEATER' AND material_name IS NULL;

-- ── Seed FURNITURE materials ──────────────────────────────────────────────────

-- Cabinets: birch wood
UPDATE ad_product_dim SET material_name = 'Wood - Birch', material_rgba = '0.871,0.722,0.529,1.000'
WHERE product_id IN ('Base_Cabinet', 'Tall_Cabinet', 'Upper_Cabinet', 'Vanity_Cabinet', 'Wardrobe')
  AND material_name IS NULL;

-- Beds: textile
UPDATE ad_product_dim SET material_name = 'Textile - White', material_rgba = '0.961,0.961,0.961,1.000'
WHERE product_id IN ('Bed_King', 'Bed_Queen', 'FURN_BED_DOUBLE', 'FURN_BED_KING', 'FURN_BED_SINGLE')
  AND material_name IS NULL;

-- Coffee table / Piano: mahogany
UPDATE ad_product_dim SET material_name = 'Wood - Mahogany', material_rgba = '0.600,0.400,0.200,1.000'
WHERE product_id IN ('Coffee_Table', 'FURN_COFFEE_TABLE', 'Piano', 'FURN_PIANO')
  AND material_name IS NULL;

-- Kitchen: laminate
UPDATE ad_product_dim SET material_name = 'Laminate, Ivory, Matte', material_rgba = '1.000,1.000,0.941,1.000'
WHERE product_id IN ('Counter_Top', 'Sink_Island', 'FURN_KITCHEN_BASE', 'FURN_KITCHEN_COUNTER', 'FURN_KITCHEN_UPPER')
  AND material_name IS NULL;

-- Desks and chairs: birch wood
UPDATE ad_product_dim SET material_name = 'Wood - Birch', material_rgba = '0.871,0.722,0.529,1.000'
WHERE product_id IN ('Desk', 'FURN_DESK', 'Dining_Chair', 'FURN_DINING_CHAIR',
                     'Dining_Table_With_Chairs', 'FURN_DINING_TABLE')
  AND material_name IS NULL;

-- Side tables: birch wood
UPDATE ad_product_dim SET material_name = 'Wood - Birch', material_rgba = '0.871,0.722,0.529,1.000'
WHERE product_id IN ('Side_Table', 'Side_Table_Cube_610')
  AND material_name IS NULL;

-- Sofa: textile
UPDATE ad_product_dim SET material_name = 'Textile - White', material_rgba = '0.961,0.961,0.961,1.000'
WHERE product_id IN ('Sofa', 'Sofa_Loveseat', 'FURN_SOFA')
  AND material_name IS NULL;

-- TV unit, wardrobe: birch wood
UPDATE ad_product_dim SET material_name = 'Wood - Birch', material_rgba = '0.871,0.722,0.529,1.000'
WHERE product_id IN ('FURN_TV_UNIT', 'FURN_WARDROBE')
  AND material_name IS NULL;

-- ── Verify ────────────────────────────────────────────────────────────────────
SELECT product_id, product_type, material_name, material_rgba
FROM ad_product_dim
WHERE product_type IN ('FURNITURE', 'FIXTURE') AND is_active = 1
ORDER BY product_type, product_id;
