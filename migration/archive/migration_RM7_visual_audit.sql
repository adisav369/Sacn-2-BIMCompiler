-- ============================================================
-- Phase RM-7: Visual Defect Audit — Metadata Fixes
-- Date: 2026-02-19
-- ============================================================

-- ============================================================
-- ISSUE 1a: SH — Fix geometry_map storey='Unknown' (26 elements)
-- SH has no Unknown-storey entries at Ground Floor for these classes,
-- so a simple storey UPDATE suffices (no ordinal conflict).
-- ============================================================

-- SH: 26 elements (20 IfcMember + 6 IfcPlate) — curtain wall
UPDATE ad_geometry_map SET storey = 'Ground Floor'
WHERE building_type = 'Ifc4_SampleHouse' AND storey = 'Unknown';

-- ============================================================
-- ISSUE 1b: DX — Fix geometry_map storey='Unknown' (11 elements)
-- In RELATIONAL mode, ordinals come from extractPlacementId(element_ref).
-- Must renumber geometry_map ordinals to match relational ordinals AND
-- fix the storey to 'Level 1'. This enables direct ordinal lookup.
--
-- Mapping (from ad_element_placement.placement_id):
--   IfcMember:      ordinal 1->1029, 2->1030, 3->1031, 4->1032
--   IfcRailing:     ordinal 1->1033, 2->1034, 3->1035, 4->1036
--   IfcSlab:        ordinal 1->1057
--   IfcStairFlight: ordinal 1->1058, 2->1059
-- ============================================================

-- IfcMember (4 elements) — no existing Level 1 entries
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1029
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcMember' AND storey = 'Unknown' AND ordinal = 1;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1030
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcMember' AND storey = 'Unknown' AND ordinal = 2;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1031
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcMember' AND storey = 'Unknown' AND ordinal = 3;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1032
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcMember' AND storey = 'Unknown' AND ordinal = 4;

-- IfcRailing (4 elements) — no existing Level 1 entries
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1033
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcRailing' AND storey = 'Unknown' AND ordinal = 1;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1034
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcRailing' AND storey = 'Unknown' AND ordinal = 2;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1035
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcRailing' AND storey = 'Unknown' AND ordinal = 3;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1036
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcRailing' AND storey = 'Unknown' AND ordinal = 4;

-- IfcStairFlight (2 elements) — no existing Level 1 entries
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1058
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcStairFlight' AND storey = 'Unknown' AND ordinal = 1;
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1059
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcStairFlight' AND storey = 'Unknown' AND ordinal = 2;

-- IfcSlab (1 element) — Level 1 already has ordinals 1-10, so use 1057
UPDATE ad_geometry_map SET storey = 'Level 1', ordinal = 1057
WHERE building_type = 'Ifc2x3_Duplex' AND ifc_class = 'IfcSlab' AND storey = 'Unknown' AND ordinal = 1;

-- ============================================================
-- ISSUE 2: TB-LKTN — Interior NS wall orientation
-- Verified: RM5 defect_fixes swap was never applied to the DB.
-- Original RM4 data is CORRECT: width=4mm (X-thickness), depth=3100mm (Y-length).
-- NO ACTION NEEDED — data already correct.
-- ============================================================

-- ============================================================
-- ISSUE 3: TB-LKTN — Furniture room overflow
-- ============================================================

-- FE_4 (shower): bilik_mandi X=[0,1300], element 900mm wide
-- fraction 0.2 -> center=260mm -> overflows west by 190mm
-- fraction 0.5 -> center=650mm -> fits [200, 1100]
UPDATE ad_element_rule SET position_value = 0.5
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_4';

-- FE_5 (WC in tandas): Y=[5400,7000], 700mm deep
-- fraction 0.85 -> center=6760mm -> overflows north by 110mm
-- fraction 0.7  -> center=6520mm -> fits [6170, 6870]
UPDATE ad_element_rule SET position_value_2 = 0.7
WHERE building_type = 'TB_LKTN' AND element_ref = 'IfcFurnishingElement_5';
