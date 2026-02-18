-- Migration: Fix ordinal mismatches and dimension swaps
-- Date: 2026-02-18
-- Context: Compiler Chasm — 5 blind spots discovered post RM-4
-- Target: library/component_library.db

-- =============================================================================
-- FIX 1: SH Furniture Ordinal Mismatch (Blind Spot 1)
-- =============================================================================
-- ad_element_rule has IfcFurnishingElement_4 through _17 (ordinals 4-17)
-- ad_element_placement has placement_id 4-17 (the flat oracle)
-- ad_geometry_map HAD ordinals 1-14 (misaligned by 3)
-- Fix: Renumber ad_geometry_map ordinals from 1-14 to 4-17 to match
-- (Can't renumber ad_element_rule because shadow validator matches by placement_id)

-- Use +100 as temp offset to avoid unique constraint violations
UPDATE ad_geometry_map SET ordinal = ordinal + 100
  WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcFurnishingElement';
UPDATE ad_geometry_map SET ordinal = ordinal - 100 + 3
  WHERE building_type = 'Ifc4_SampleHouse' AND ifc_class = 'IfcFurnishingElement';
-- Result: geometry_map ordinals now 4-17, matching element_rule and element_placement

-- =============================================================================
-- FIX 2: TB-LKTN Door Width/Depth Swap (Blind Spot 5)
-- =============================================================================
-- Internal doors D2-D6 have width_mm=4.0 and depth_mm=700-800
-- Columns are swapped — doors should be 700-800mm wide
-- Door D1 (front door, 900mm wide, 100mm deep) is correct
-- Only fix where width_mm < 10

UPDATE ad_element_rule
SET width_mm = depth_mm, depth_mm = width_mm
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcDoor' AND width_mm < 10;

-- After swap, depth_mm is 4.0 (bad original value, not a real wall thickness)
-- Set to 100mm to match D1 and standard internal partition thickness
UPDATE ad_element_rule
SET depth_mm = 100.0
WHERE building_type = 'TB_LKTN' AND ifc_class = 'IfcDoor' AND depth_mm < 10;
