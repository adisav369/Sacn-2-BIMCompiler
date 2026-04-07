-- DV048: Backfill source_element_ref for remaining unmapped generative products
-- Implementing DISC_VALIDATION_DB_SRS.md §12g GAP-9 — Witness: W-SHIM-DEVICE
--
-- S155 found 18 generative devices (FLOOR_TRAP, OUTLET_20A, OUTLET_GFCI) with
-- NULL source_element_ref. Without it, BuildingWriter writes the abstract token
-- as element_name ("FLOOR_TRAP") instead of the IFC family name.
--
-- Mappings from I_Geometry_Map in component_library.db.

UPDATE M_Product SET source_element_ref = 'jkrAR16_plb-fx_LSf_TDS_(LSf001)-3 Floor Trap:jkrAR16_plb-fx_LSf_TDS_(LSf001)-3 Floor Trap'
  WHERE product_id = 'FLOOR_TRAP' AND (source_element_ref IS NULL OR source_element_ref = '');

UPDATE M_Product SET source_element_ref = 'M_Duplex Receptacle:Standard'
  WHERE product_id = 'OUTLET_20A' AND (source_element_ref IS NULL OR source_element_ref = '');

UPDATE M_Product SET source_element_ref = 'M_Duplex Receptacle:GFCI'
  WHERE product_id = 'OUTLET_GFCI' AND (source_element_ref IS NULL OR source_element_ref = '');
