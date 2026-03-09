-- G7-GEOMETRY: Fix M_Product_Image geometry_hash for 7 SH furniture products
-- Problem: swap chains and wrong assignments — products have each other's meshes.
-- Evidence: reference Ifc4_SampleHouse_extracted.db vertex counts vs compiled output.
--
-- Step 1: Import 3 missing LOD_Objects from reference (run via Python/Java, not SQL)
-- Step 2: Fix M_Product_Image mappings (this file, run against component_library.db)
--
-- Affected products (current → correct):
--   Dining_Chair:         349e2fcef4a79049 (1829v armchair) → f59c7608ea56f0d4 (222v chair)
--   FURN_ARMCHAIR_VIPER:  f59c7608ea56f0d4 (222v chair)    → 349e2fcef4a79049 (1829v armchair)
--   FURN_SOFA:            1be344f9ee741ac8 (120v unknown)   → e31eb5752dacf95f (1953v couch)
--   FURN_COFFEE_TABLE:    e31eb5752dacf95f (1953v couch)    → f4335100325a091c (40v table)
--   FURN_PIANO:           f4335100325a091c (40v table)      → 37530df88a465287 (268v piano) [LOD import]
--   FURN_DESK:            1f825c2c6829e3c6 (1829v armchair) → e0cd90130806acba (200v desk) [LOD import]
--   Bed_Queen:            5333fe23d241272a (372v wrong bed)  → dcfd8ca743c39461 (634v queen) [LOD import]

-- Dining_Chair <-> FURN_ARMCHAIR_VIPER (swap)
UPDATE M_Product_Image SET geometry_hash = 'f59c7608ea56f0d4'
  WHERE M_Product_ID = 'Dining_Chair';
UPDATE M_Product_Image SET geometry_hash = '349e2fcef4a79049'
  WHERE M_Product_ID = 'FURN_ARMCHAIR_VIPER';

-- FURN_SOFA: had unknown mesh, should be couch
UPDATE M_Product_Image SET geometry_hash = 'e31eb5752dacf95f'
  WHERE M_Product_ID = 'FURN_SOFA';

-- FURN_COFFEE_TABLE: had couch mesh, should be coffee table
UPDATE M_Product_Image SET geometry_hash = 'f4335100325a091c'
  WHERE M_Product_ID = 'FURN_COFFEE_TABLE';

-- FURN_PIANO: had coffee table mesh, should be piano (requires LOD import)
UPDATE M_Product_Image SET geometry_hash = '37530df88a465287'
  WHERE M_Product_ID = 'FURN_PIANO';

-- FURN_DESK: had armchair instance mesh, should be desk (requires LOD import)
UPDATE M_Product_Image SET geometry_hash = 'e0cd90130806acba'
  WHERE M_Product_ID = 'FURN_DESK';

-- Bed_Queen: wrong bed variant (requires LOD import)
UPDATE M_Product_Image SET geometry_hash = 'dcfd8ca743c39461'
  WHERE M_Product_ID = 'Bed_Queen';
