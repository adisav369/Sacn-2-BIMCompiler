-- DV046: LOD geometry bridge for generative MEP device products
-- Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 §11 — Witness: W-LOD-BRIDGE
--
-- Problem: Generative MEP products (TOILET, LIGHT, SINK, etc.) were created by
-- DV043 with NULL source_element_ref. The LOD geometry resolver joins M_Product
-- to I_Geometry_Map via source_element_ref, so these products can never resolve
-- to geometry. This migration backfills the IFC family names from Duplex extraction.
--
-- Mappings extracted from I_Geometry_Map WHERE building_type LIKE '%Duplex%'.
-- Only updates rows where source_element_ref IS NULL (don't overwrite existing values).

UPDATE M_Product SET source_element_ref = 'M_Water Closet - Flush Tank:Private - 6.1 Lpf:Private - 6.1 Lpf'
  WHERE product_id = 'TOILET' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Sink - Island - Single:455 mmx455 mm - Private:455 mmx455 mm - Private'
  WHERE product_id = 'SINK' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Pendant Light - Hemisphere:150W - 120V:150W - 120V'
  WHERE product_id = 'LIGHT' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Lighting Switches:Single Pole:Single Pole'
  WHERE product_id = 'SWITCH' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Duplex Receptacle:Duplex Receptacle:Duplex Receptacle'
  WHERE product_id = 'OUTLET' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Refrigerator:850 x 760mm:850 x 760mm'
  WHERE product_id = 'FRIDGE' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Smoke Detector:Smoke Detector:Smoke Detector'
  WHERE product_id = 'SMOKE_DETECTOR' AND source_element_ref IS NULL;

-- S152: Remaining 5 generative MEP products — mapped from Revit_MEP and SJTII_Terminal extractions.
-- All 5 have matching geometry in I_Geometry_Map (component_library.db).

UPDATE M_Product SET source_element_ref = 'M_Supply Diffuser - Rectangular Face Round Neck:600x600 - 250 Neck'
  WHERE product_id = 'SUPPLY_DIFFUSER' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Exhaust FAn Ventilation:1200mm x 1100mm'
  WHERE product_id = 'EXHAUST_FAN' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'E_Fan_Ceiling_1500mm_V1:Ceiling Fan'
  WHERE product_id = 'CEILING_FAN' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'M_Sprinkler - Pendent - Hosted:15 mm Pendent on Drop with Guard'
  WHERE product_id = 'SPRINKLER' AND source_element_ref IS NULL;

UPDATE M_Product SET source_element_ref = 'Fancoil Unit:ACSU-02'
  WHERE product_id = 'AIRCON_POINT' AND source_element_ref IS NULL;
