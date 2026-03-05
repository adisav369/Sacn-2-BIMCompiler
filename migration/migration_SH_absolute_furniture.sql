-- migration_SH_absolute_furniture.sql
-- SH is EXTRACTED — furniture must use ABSOLUTE placement from IFC reference,
-- not BOM chain explosion (which produces wrong positions and dimensions).
-- Pattern: same as DX T3 migration.
--
-- Column mapping for ABSOLUTE:
--   position_value  = cx_mm (centroid X)
--   position_value_2 = cy_mm (centroid Y)
--   height_mm       = base Z in mm (compiler uses this for minZ, NOT position_value_3)
--   width_mm        = bbox width (X extent)
--   depth_mm        = bbox depth (Y extent)
--   height_extent_mm = bbox height (Z extent)
--   position_value_3 = 0 (unused for ABSOLUTE)
--
-- All values: full double precision from Ifc4_SampleHouse_extracted.db elements_rtree
-- Target: library/BOM.db

-- Step 1: Deactivate BOM chain FURN anchors for SH
UPDATE c_orderline SET is_active = 0
WHERE building_type = 'Ifc4_SampleHouse'
  AND discipline = 'FURN'
  AND element_ref IN ('UNIT_SH', 'FLOOR_SH_GF');

-- Step 2: Insert 14 ABSOLUTE furniture c_orderlines from IFC reference
-- Delete existing rows first (INSERT OR REPLACE requires matching UNIQUE constraint)
DELETE FROM c_orderline
WHERE building_type = 'Ifc4_SampleHouse'
  AND element_ref LIKE 'IfcFurnishingElement_%';

INSERT INTO c_orderline
  (building_type, storey, element_ref, ifc_class, discipline,
   host_type, host_ref, position_rule, position_value, position_value_2, position_value_3,
   height_mm, width_mm, depth_mm, height_extent_mm,
   family_ref, material_name, material_rgba, is_active)
VALUES
  -- 1. Desk (id=1)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_287689',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   5317.65151023865, 3957.10742473602, 0.0,
   0.0, 1563.10033798218, 819.15020942688, 762.000024318695,
   'Furniture_Desk:1525x762mm:287689', 'Laminate, Ivory, Matte', '0.969,0.969,0.937,1.000', 1),

  -- 2. Dining Table with Chairs (id=2)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_289768',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -5409.01160240173, 441.239956766367, 0.0,
   0.0, 2000.00047683716, 1000.00011175871, 750.0,
   'Furniture_Table_Dining_w-Chairs_Rectangular:2000x1000x750mm_w-6_Seats:289768',
   'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 3. Chair - Dining 289769 (id=3)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_289769',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -5759.01174545288, 944.737702608109, 0.0,
   -0.705102051142603, 442.539215087891, 426.933944225311, 1227.0124941715,
   'Chair - Dining:Chair - Dining:289769', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 4. Chair - Dining 289770 (id=4)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_289770',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -5059.01169776917, 944.737702608109, 0.0,
   -0.705102051142603, 442.537784576416, 426.933944225311, 1227.0124941715,
   'Chair - Dining:Chair - Dining:289770', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 5. Chair - Dining 289771 (id=5)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_289771',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -5759.01174545288, -67.7800700068474, 0.0,
   -0.705102051142603, 442.539215087891, 426.933899521828, 1227.0124941715,
   'Chair - Dining:Chair - Dining:289771', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 6. Chair - Dining 289772 (id=6)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_289772',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -5059.01169776917, -67.7800700068474, 0.0,
   -0.705102051142603, 442.537784576416, 426.933899521828, 1227.0124941715,
   'Chair - Dining:Chair - Dining:289772', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 7. Chair - Dining 290097 (rotated, id=7)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_290097',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -6412.50944137573, 441.239953041077, 0.0,
   -0.705102051142603, 426.934242248535, 442.537665367126, 1227.0124941715,
   'Chair - Dining:Chair - Dining:290097', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 8. Chair - Dining 290098 (rotated, id=8)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_290098',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -4405.51376342773, 441.239953041077, 0.0,
   -0.705102051142603, 426.934242248535, 442.537665367126, 1227.0124941715,
   'Chair - Dining:Chair - Dining:290098', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 9. Couch Viper (id=9)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_290852',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -4627.78913974762, 3830.75964450836, 0.0,
   0.0, 2286.88216209412, 977.268934249878, 958.148896694183,
   'Furniture_Couch_Viper:2290x950x340mm:290852', 'Metal - Chrome', '0.969,0.969,0.969,1.000', 1),

  -- 10. Chair Viper 291916 (id=10)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_291916',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -6796.2474822998, 3226.60434246063, 0.0,
   0.0, 1428.07197570801, 1400.36368370056, 918.262660503387,
   'Furniture_Chair_Viper:1120x940x350mm:291916', 'Metal - Chrome', '0.969,0.969,0.969,1.000', 1),

  -- 11. Chair Viper 292127 (id=11)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_292127',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -2413.13236951828, 3323.0664730072, 0.0,
   0.0, 1423.70617389679, 1389.1339302063, 918.262660503387,
   'Furniture_Chair_Viper:1120x940x350mm:292127', 'Metal - Chrome', '0.969,0.969,0.969,1.000', 1),

  -- 12. Coffee Table (id=12)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_293046',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   -4534.84833240509, 2708.58252048492, 0.0,
   0.0, 1200.00052452087, 550.000429153442, 450.000047683716,
   'Furniture_Table_Coffee_1:1200x550x450mm:293046', 'Wood - Birch', '0.835,0.643,0.404,1.000', 1),

  -- 13. Piano (id=13)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_293961',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   672.651614528149, 4108.58249664307, 0.0,
   0.139550189487636, 1371.60021904856, 600.000381469727, 1169.86064531375,
   'Furniture_Piano:1370x600x1170mm:293961', 'Wood - Mahogany', '0.541,0.337,0.176,1.000', 1),

  -- 14. Bed Queen (id=14)
  ('Ifc4_SampleHouse', 'Ground Floor', 'IfcFurnishingElement_295878',
   'IfcFurnishingElement', 'FURN', 'BUILDING', 'BUILDING', 'ABSOLUTE',
   5116.6512966156, 1846.08238935471, 0.0,
   0.0, 2007.00044631958, 1800.00030994415, 482.600003480911,
   'Furniture_Bed_1:1525x2007x355mm-Queen:295878', 'Textile - White', '0.969,0.969,0.937,1.000', 1);
