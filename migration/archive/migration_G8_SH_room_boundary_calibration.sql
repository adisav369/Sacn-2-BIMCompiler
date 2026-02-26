-- ============================================================
-- G8 Calibration — SH Room Boundary Coordinates
-- Source: [EXTRACTED: Ifc4_SampleHouse_extracted.db]
--   rel_contained_in_space → elements_rtree → MIN/MAX per IfcSpace
--
-- Root cause: both rooms were set to the EASTERN X range (1620-6265mm)
--   when the reference IFC has the living room on the WESTERN side (X<0).
--
-- Room names PRESERVED (ad_element_rule and ad_wall_face reference them).
-- Only min_x_mm/max_x_mm/min_y_mm/max_y_mm values are corrected.
-- ============================================================

-- ROOM_Ground_Floor_1 = "1 - Living room" in reference IFC
-- Extracted: 12 elements, guid 3w0zWKm7n8SB1qbfwUzt0U
-- minX=-7.5103m, maxX=1.3585m, minY=-0.2812m, maxY=4.4086m
UPDATE ad_room_boundary
SET
    min_x_mm = -7510.0,
    max_x_mm =  1359.0,
    min_y_mm =  -281.0,
    max_y_mm =  4409.0
WHERE building_type = 'Ifc4_SampleHouse'
  AND room_name     = 'ROOM_Ground_Floor_1'
  AND room_type     = 'LIVING';

-- ROOM_Ground_Floor_2 = "2 - Bedroom" in reference IFC
-- Extracted: 2 elements, guid 3w0zWKm7n8SB1qbfwUzt0J
-- minX=4.1132m, maxX=6.1202m, minY=0.9461m, maxY=4.3667m
UPDATE ad_room_boundary
SET
    min_x_mm = 4113.0,
    max_x_mm = 6120.0,
    min_y_mm =  946.0,
    max_y_mm = 4367.0
WHERE building_type = 'Ifc4_SampleHouse'
  AND room_name     = 'ROOM_Ground_Floor_2'
  AND room_type     = 'BEDROOM';

-- Verify
SELECT room_name, room_type, min_x_mm, max_x_mm, min_y_mm, max_y_mm
FROM ad_room_boundary
WHERE building_type = 'Ifc4_SampleHouse'
ORDER BY room_name;
