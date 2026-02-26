-- Migration: Phase 120 — TB-LKTN Porch Canopy IfcRoof
-- 
-- Adds a second IfcRoof element for the porch (anjung) canopy.
-- The main gable (IfcRoof_1) covers the main body: rows 2-5 (A-E = 9900mm).
-- The canopy covers the porch: columns C-D (3100-6800mm), rows 1-2 (0-2300mm).
--
-- Canopy geometry (derived from 2D PDF, pitch 25deg, overhang 700mm):
--   E-W: cMinX = 3100-700 = 2400mm, cMaxX = 6800+700 = 7500mm → width = 5100mm
--   N-S: cMinY = 0-700 = -700mm (south eave), cMaxY = 2300mm (north wall, no overhang) → depth = 3000mm
--   Center X = (2400+7500)/2 = 4950mm, Center Y = (-700+2300)/2 = 800mm
--   Ridge rise = (3000/2) * tan(25°) = 1500 * 0.4663 = 699mm
--   Eave height = 3000mm (same as main roof)
--
-- The GABLE_25 orientation triggers writeGableGeometry() in BuildingWriter,
-- which computes ridgeAlongX=(5100>=3000) → ridge runs E-W, gable end faces south.
-- This matches the front elevation showing the canopy triangle below the main apex.

PRAGMA foreign_keys = ON;

INSERT OR IGNORE INTO ad_element_rule (
    building_type,
    storey,
    element_ref,
    ifc_class,
    discipline,
    host_type,
    host_ref,
    position_rule,
    position_value,    -- cx_mm: center X of canopy = 4950mm
    position_value_2,  -- cy_mm: center Y of canopy = 800mm
    height_mm,         -- eave Z height = 3000mm (ground floor ceiling)
    width_mm,          -- E-W width with overhangs = 5100mm
    height_extent_mm,  -- ridge rise = 699mm (1500 * tan(25°))
    depth_mm,          -- N-S depth: south eave to north wall = 3000mm
    orientation,       -- GABLE_25 → writeGableGeometry() with 25deg pitch
    is_active
) VALUES (
    'TB_LKTN',
    'Ground Floor',
    'IfcRoofCanopy_1',
    'IfcRoof',
    'ARC',
    'BUILDING',
    'BUILDING',
    'ENVELOPE',
    4950.0,
    800.0,
    3000.0,
    5100.0,
    699.0,
    3000.0,
    'GABLE_25',
    1
);

-- Verify
SELECT 'Inserted canopy rule: ' || element_ref || 
       ' width=' || width_mm || 'mm depth=' || depth_mm || 'mm rise=' || height_extent_mm || 'mm'
FROM ad_element_rule
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcRoofCanopy_1';
