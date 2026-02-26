-- Phase RM-4 Fix: TB-LKTN Geometry Resolution
-- Applied to component_library.db
-- Fixes: C1 (rear wall gap), C2 (remove IfcRoof rule), C4 (door/window library mapping)
-- C3 (furniture LOD400) via ad_geometry_map entries

-- ============================================================
-- C1: Fix Rear Wall Gap
-- ============================================================
-- Missing exterior wall at Y=8500, X:1300-3100 (north face of corridor between
-- bilik_mandi and common area). The corridor B-C / 3-5 is not assigned to any
-- room, leaving a 1800mm gap in the north perimeter.
-- Wall center: X=(1300+3100)/2=2200, Y=8500. Width=1800mm, depth=100mm (V1 Teablock).

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'IfcPlate_20', 'IfcPlate', 'ARC',
     'WALL', 'WALL_corridor_NORTH', 'BOUNDARY', 2200, 8500,
     0, 1800, 3000, 100,
     'EW', 'Teablock_V1_100', '220,210,200,255', 4);

-- Verification: North perimeter at Y=8500 is now covered:
--   IfcPlate_11: X:0-1300 (bilik_mandi NORTH)
--   IfcPlate_20: X:1300-3100 (corridor NORTH) -- NEW
--   IfcPlate_8:  X:3100-6800 (common NORTH)
--   IfcPlate_5:  X:6800-9900 (bilik_3 NORTH)
--   Total: 0+1300+3700+3100 = 9900mm = full building width ✓

-- ============================================================
-- C2: Fix IfcRoof_1 — correct ridge height
-- ============================================================
-- Original had height_extent_mm=500 (flat box maxZ=3.5m).
-- Correct: ridgeRise = (6200/2) × tan(25°) = 1445mm → maxZ=4.445m
-- Roof covers main building + 700mm overhang: X:-700→10600, Y:1600→9200
-- TB_LKTN compilation relies on PlacementAD for all elements including roof,
-- so the DSL compilation path alone cannot produce a standalone roof.

DELETE FROM ad_element_rule
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcRoof_1';

INSERT INTO ad_element_rule (building_type, storey, element_ref, ifc_class, discipline,
    host_type, host_ref, position_rule, position_value, position_value_2,
    height_mm, width_mm, height_extent_mm, depth_mm,
    orientation, material_name, material_rgba, building_id)
VALUES
    ('TB_LKTN', 'Ground Floor', 'IfcRoof_1', 'IfcRoof', 'ARC',
     'BUILDING', 'BUILDING', 'ENVELOPE', 4950, 5400,
     3000, 11300, 1445, 7600,
     NULL, 'Metal_Roofing_R1', '150,50,50,255', 4);

-- ============================================================
-- C4: Door/Window Library Geometry Mapping
-- ============================================================
-- Map TB_LKTN doors and windows to existing library geometry via ad_geometry_map.
-- Uses instance-level lookup: (building_type, ifc_class, storey, ordinal).
-- Ordinals must match emission order from PlacementAD (sorted by element_ref).
-- Door element_refs: IfcDoor_1..6, Window element_refs: IfcWindow_1..11

-- Doors: Map to SH single internal door geometry (1336 V, 2636 F)
-- D1 (main entry, 900x2100) → ordinal 1
-- D2-D4 (internal, 800x2100) → ordinals 2-4
-- D5-D6 (bathroom, 700x2100) → ordinals 5-6
INSERT INTO ad_geometry_map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source)
VALUES
    ('TB_LKTN', 'Door_D1_Main', 'IfcDoor', 'Ground Floor', 1, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Door_D2_Internal', 'IfcDoor', 'Ground Floor', 2, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Door_D2_Internal', 'IfcDoor', 'Ground Floor', 3, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Door_D2_Internal', 'IfcDoor', 'Ground Floor', 4, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Door_D3_Bathroom', 'IfcDoor', 'Ground Floor', 5, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Door_D3_Bathroom', 'IfcDoor', 'Ground Floor', 6, 'c5357415e37e7593', 'TB_LKTN:mapped_from_SH');

-- Windows: Map to SH window geometry (108 V, 208 F)
-- IfcWindow_1..11, ordinals 1..11
INSERT INTO ad_geometry_map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source)
VALUES
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor',  1, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W2', 'IfcWindow', 'Ground Floor',  2, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W2', 'IfcWindow', 'Ground Floor',  3, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor',  4, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor',  5, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor',  6, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W3_Small', 'IfcWindow', 'Ground Floor',  7, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor',  8, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W3_Small', 'IfcWindow', 'Ground Floor',  9, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor', 10, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH'),
    ('TB_LKTN', 'Window_W1', 'IfcWindow', 'Ground Floor', 11, '8aabd3e04dfb3bb2', 'TB_LKTN:mapped_from_SH');

-- ============================================================
-- C3: Furniture LOD400 Library Mapping
-- ============================================================
-- Map TB_LKTN furniture to existing library geometry via ad_geometry_map.
-- Furniture IfcFurnishingElement ordinals: fixtures first (1-5), then furniture (6-10).
-- PlacementAD sorts by element_ref alphabetically within ifc_class:
--   IfcFurnishingElement_1..5 (fixtures) → ordinals 1-5
--   Furniture_Bed_Queen_bilik_utama → ordinal 6
--   Furniture_Bed_Single_bilik_2 → ordinal 7
--   Furniture_Bed_Single_bilik_3 → ordinal 8
--   Furniture_Coffee_Table_common → ordinal 9
--   Furniture_Sofa_common → ordinal 10
-- Fixtures (kitchen sink, basin, WC, shower) don't have library geometry — skip.

INSERT INTO ad_geometry_map (building_type, element_ref, ifc_class, storey, ordinal, geometry_hash, source)
VALUES
    ('TB_LKTN', 'Furniture_Bed_Queen_bilik_utama', 'IfcFurnishingElement', 'Ground Floor', 6, '3764b0bd44f1ac08', 'TB_LKTN:Bed_Queen'),
    ('TB_LKTN', 'Furniture_Bed_Single_bilik_2', 'IfcFurnishingElement', 'Ground Floor', 7, '3764b0bd44f1ac08', 'TB_LKTN:Bed_Single'),
    ('TB_LKTN', 'Furniture_Bed_Single_bilik_3', 'IfcFurnishingElement', 'Ground Floor', 8, '3764b0bd44f1ac08', 'TB_LKTN:Bed_Single'),
    ('TB_LKTN', 'Furniture_Coffee_Table_common', 'IfcFurnishingElement', 'Ground Floor', 9, '738682c11dd4d4f1', 'TB_LKTN:Coffee_Table_Rect_1200'),
    ('TB_LKTN', 'Furniture_Sofa_common', 'IfcFurnishingElement', 'Ground Floor', 10, '7e00cab6cc030d4c', 'TB_LKTN:Sofa');
