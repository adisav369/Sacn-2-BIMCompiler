-- TE_002: Remove IfcReinforcingBar from extraction data
-- Rebar is a fast Bonsai Python addon (adds rebar to any beam in STR).
-- Need not be part of main construction BOM.
-- Affects: component_library.db (I_Element_Extraction), Terminal_Extracted.db (elements_meta)

-- component_library.db
DELETE FROM I_Element_Extraction WHERE ifc_class = 'IfcReinforcingBar';

-- Terminal_Extracted.db (run separately against that DB)
-- DELETE FROM elements_meta WHERE ifc_class = 'IfcReinforcingBar';
