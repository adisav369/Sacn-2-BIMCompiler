-- Migration: fix ad_geometry_map for Ifc2x3_Duplex IfcBeam rows.
--
-- PROBLEM: After BOM-2c storey rename (Level 1→Ground, Level 2→Upper in ad_element_rule),
-- ad_geometry_map still uses old storey strings AND sequential ordinals (1-4 per storey),
-- while RelationalResolver assigns ordinals from extractPlacementId (IfcBeam_56 → 56).
-- resolveGeometryByInstance() direct-ordinal lookup therefore misses on both axes.
--
-- FIX (two UPDATE statements, one logical fix):
--   Level 1 (4 beams: IfcBeam_56-59) → storey='Ground', ordinal += 55 (1→56, 2→57, 3→58, 4→59)
--   Level 2 (4 beams: IfcBeam_60-63) → storey='Upper',  ordinal += 59 (1→60, 2→61, 3→62, 4→63)
--
-- PRECONDITION: geometry_map ordinals 1-4 were extracted in IFC STEP order, matching the
-- suffix numbering of element_refs (IfcBeam_56 is the 1st Ground beam, etc.)
--
-- THREE-TABLE AUTHORITY: this modifies the geometry lookup index only; no product dims or
-- room-relative placement coords are changed.

UPDATE ad_geometry_map
SET storey  = 'Ground',
    ordinal = ordinal + 55
WHERE building_type = 'Ifc2x3_Duplex'
  AND ifc_class     = 'IfcBeam'
  AND storey        = 'Level 1';

UPDATE ad_geometry_map
SET storey  = 'Upper',
    ordinal = ordinal + 59
WHERE building_type = 'Ifc2x3_Duplex'
  AND ifc_class     = 'IfcBeam'
  AND storey        = 'Level 2';
