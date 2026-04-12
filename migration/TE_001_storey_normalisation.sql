-- TE_001_storey_normalisation.sql
-- Terminal Phase B Step 1: Assign storeys to Terminal elements
-- All 51,088 elements currently have storey='Unknown'.
-- Uses Z-centroid bands derived from Terminal_Extracted.db storey elevations.
-- ========================================================================

-- Step 1: Storey normalisation via Z-centroid bands
-- Z boundaries from Terminal_Extracted.db elevation analysis:
--   FDN:  avg_z < 0     (Aras Asas, GROUND FLOOR LEVEL footings)
--   GF:   avg_z 0–4.5   (Aras Tanah, Kedai, Aras Jalan)
--   L01:  avg_z 4.5–8.0 (02 FIRST FLOOR LEVEL, Aras 01, Ceiling Level 01/02)
--   L02:  avg_z 8.0–11.5 (03 SECOND FLOOR LEVEL, Aras 02, Ceiling Level 03)
--   L03:  avg_z 11.5–15.0 (04 THIRD FLOOR LEVEL, Aras 03)
--   L04:  avg_z 15.0–18.0 (05 FOURTH FLOOR LEVEL, Aras 04, Ceiling Level 04)
--   RF:   avg_z >= 18.0  (06 ROOF LEVEL, Aras Bumbung, roof IfcPlate)

UPDATE I_Element_Extraction SET storey = CASE
    WHEN (min_z + max_z) / 2.0 < 0.0   THEN 'Foundation'
    WHEN (min_z + max_z) / 2.0 < 4.5   THEN 'Ground Floor'
    WHEN (min_z + max_z) / 2.0 < 8.0   THEN 'Level 1'
    WHEN (min_z + max_z) / 2.0 < 11.5  THEN 'Level 2'
    WHEN (min_z + max_z) / 2.0 < 15.0  THEN 'Level 3'
    WHEN (min_z + max_z) / 2.0 < 18.0  THEN 'Level 4'
    ELSE 'Roof'
END
WHERE building_type = 'Terminal';

-- Step 2: Deactivate REBAR elements (2,660 elements)
-- REBAR is deferred — IfcOpenShell Python handles rebar generation dynamically.
UPDATE I_Element_Extraction
SET is_active = 0
WHERE building_type = 'Terminal' AND discipline = 'REB';
