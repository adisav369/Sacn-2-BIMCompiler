-- migration_122K_library_gap_fill.sql
-- Generated from library_gap_filler.py audit of all 3 Rosetta stones
-- Date: 2026-02-14
-- Idempotent: all INSERT OR IGNORE

-- ============================================================
-- SLABS: 7 missing thicknesses across 3 stones
-- ============================================================

-- SampleHouse (UK_Residential): 165mm, 470mm
INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_165', 'Structural Slab 165mm', 'STRUCTURAL', 4.0, 0.165, 1, 'UK_Residential');

INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_470', 'Structural Slab 470mm', 'STRUCTURAL', 4.0, 0.470, 1, 'UK_Residential');

-- Duplex (US_Residential): 127mm, 305mm, 457mm
INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_127', 'Structural Slab 127mm (5")', 'STRUCTURAL', 4.0, 0.127, 1, 'US_Residential');

INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_305', 'Structural Slab 305mm (12")', 'STRUCTURAL', 4.0, 0.305, 1, 'US_Residential');

INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_457', 'Structural Slab 457mm (18")', 'STRUCTURAL', 4.0, 0.457, 1, 'US_Residential');

-- Terminal (Malaysian_Institutional): 1158mm, 1175mm (deep transfer slabs/landings)
INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_1158', 'Deep Slab 1158mm', 'TRANSFER', 4.0, 1.158, 1, 'Malaysian_Institutional');

INSERT OR IGNORE INTO ad_floor_type
  (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
  VALUES ('STRUCTURAL_1175', 'Deep Slab 1175mm', 'TRANSFER', 4.0, 1.175, 1, 'Malaysian_Institutional');

-- ============================================================
-- DOORS: 2 missing families (Terminal)
-- ============================================================

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_DOOR_1000x2175', 'MY Door 1000x2175mm', 'DOOR', 'IfcDoor', 1000, 2175, 200, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_DOOR_1780x2100', 'MY Double Door 1780x2100mm', 'DOOR', 'IfcDoor', 1780, 2100, 150, 1);

-- ============================================================
-- WINDOWS: 9 missing families (1 Duplex + 8 Terminal)
-- ============================================================

-- Duplex: skylight/transom (1225x178mm, deep sill)
INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('US_WINDOW_1225x178', 'US Transom 1225x178mm', 'WINDOW', 'IfcWindow', 1225, 178, 1173, 1);

-- Terminal: 8 window families (curtain wall panels + large windows)
INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_2562x3449', 'MY CW Panel 2562x3449mm', 'WINDOW', 'IfcWindow', 2562, 3449, 223, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_2058x1950', 'MY Window 2058x1950mm', 'WINDOW', 'IfcWindow', 2058, 1950, 150, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_6725x2125', 'MY CW Panel 6725x2125mm', 'WINDOW', 'IfcWindow', 6725, 2125, 150, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_2600x3400', 'MY CW Panel 2600x3400mm', 'WINDOW', 'IfcWindow', 2600, 3400, 200, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_4300x2150', 'MY CW Panel 4300x2150mm', 'WINDOW', 'IfcWindow', 4300, 2150, 200, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_4975x3400', 'MY CW Panel 4975x3400mm', 'WINDOW', 'IfcWindow', 4975, 3400, 280, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_7075x3400', 'MY CW Panel 7075x3400mm', 'WINDOW', 'IfcWindow', 7075, 3400, 200, 1);

INSERT OR IGNORE INTO ad_opening_family
  (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
  VALUES ('MY_WINDOW_7700x4000', 'MY CW Panel 7700x4000mm', 'WINDOW', 'IfcWindow', 7700, 4000, 255, 1);

-- ============================================================
-- RAILINGS: 8 missing heights (2 Duplex + 6 Terminal)
-- ============================================================

-- Duplex: full-storey balustrades
INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('US_BALUSTRADE_2957', 'US Floor Balustrade 2957mm', 'BALCONY', 2957, 50, 'SLAB_EDGE', 'Wood', 'US_Residential', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('US_BALUSTRADE_3952', 'US Storey Balustrade 3952mm', 'BALCONY', 3952, 50, 'SLAB_EDGE', 'Wood', 'US_Residential', 1);

-- Terminal: full-height barriers
INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_4815', 'MY Barrier 4815mm', 'WALL_GUARD', 4815, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_4933', 'MY Barrier 4933mm', 'WALL_GUARD', 4933, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_4946', 'MY Barrier 4946mm', 'WALL_GUARD', 4946, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_4976', 'MY Barrier 4976mm', 'WALL_GUARD', 4976, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_5100', 'MY Barrier 5100mm', 'WALL_GUARD', 5100, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);

INSERT OR IGNORE INTO ad_railing_type
  (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile, is_active)
  VALUES ('MY_BARRIER_5379', 'MY Barrier 5379mm', 'WALL_GUARD', 5379, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional', 1);
