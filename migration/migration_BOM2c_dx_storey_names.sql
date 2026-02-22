-- Phase BOM-2c: Unify DX storey names across relational metadata and BuildingSpec output.
--
-- Root cause: ad_element_rule/ad_room_boundary use IFC-derived storey names ('Level 1'/'Level 2')
-- while BuildingSpec compiles storeys as 'Ground'/'Upper' (from DX DSL spec).
-- This mismatch breaks:
--   (a) computeUnitAnchor() storey filter:  storeyName.equals(room.storey()) fails
--   (b) WriteStage containment JOIN:        em.storey = ss.name fails (ss.name='Ground'/'Upper')
--
-- Fix: change relational metadata to 'Ground'/'Upper' matching the compiled BuildingSpec.
--
-- Verification:
--   sqlite3 library/component_library.db "SELECT DISTINCT storey FROM ad_element_rule
--   WHERE building_type='Ifc2x3_Duplex' AND is_active=1"
--   → should return: Ground, Upper (not Level 1, Level 2)
--
-- [EXTRACTED: RelationalResolver.loadFloorStoreys() — storey from ad_element_rule]
-- [EXTRACTED: BuildingWriter — spatial_structure.name = storey.name() from BuildingSpec]

UPDATE ad_element_rule
   SET storey = 'Ground'
 WHERE building_type = 'Ifc2x3_Duplex' AND storey = 'Level 1';

UPDATE ad_element_rule
   SET storey = 'Upper'
 WHERE building_type = 'Ifc2x3_Duplex' AND storey = 'Level 2';

UPDATE ad_room_boundary
   SET storey = 'Ground'
 WHERE building_type = 'Ifc2x3_Duplex' AND storey = 'Level 1';

UPDATE ad_room_boundary
   SET storey = 'Upper'
 WHERE building_type = 'Ifc2x3_Duplex' AND storey = 'Level 2';
