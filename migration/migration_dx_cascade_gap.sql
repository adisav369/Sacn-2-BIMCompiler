-- Migration: DX cascade gap fix (2026-03-05)
-- Target: library/component_library.db
-- Purpose: Insert 14 missing IfcFlowController + rename DUPLEX→Ifc2x3_Duplex + activate
--
-- Run: sqlite3 library/component_library.db < migration/migration_dx_cascade_gap.sql

-- Step 1: Insert 14 IfcFlowController missing from legacy flat extraction
-- Source: DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db (reference DB)
-- 6 smoke detectors, 4 ball valves, 4 backflow preventers — all MEP, storey Unknown
INSERT INTO ad_element_placement (building_type, storey, ifc_class, element_ref, ordinal, min_x, max_x, min_y, max_y, min_z, max_z, orientation, discipline, source, is_active, material_name, material_rgba)
VALUES
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610550',1,1.81811285018921,1.95811307430267,-4.92313766479492,-4.78313684463501,2.49829959869385,2.60000038146973,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610280',2,6.60560894012451,6.74560928344727,-12.7896862030029,-12.6496858596802,2.49829959869385,2.60000038146973,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Ball Valve - 50-150 mm:50 mm:50 mm:578433',3,5.44283533096313,5.58183622360229,-7.23348236083984,-7.15348148345947,2.88401246070862,3.10001254081726,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Ball Valve - 50-150 mm:50 mm:50 mm:579237',4,3.2771635055542,3.35716390609741,-10.6465187072754,-10.5075168609619,2.92895603179932,3.14495611190796,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm:593194',5,3.27087473869324,3.39627647399902,-11.0359506607056,-10.9409475326538,3.19300580024719,3.55300664901733,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm:591225',6,3.2551634311676,3.35016393661499,-10.5283374786377,-10.1683359146118,3.20654988288879,3.33195209503174,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm:593973',7,5.44963550567627,5.54463624954224,-7.6028881072998,-7.24288702011108,3.23631572723389,3.36171746253967,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Backflow Preventer_ DCW to Hydronic Supply_15-50 mm_:25 mm:25 mm:594130',8,5.40372276306152,5.52912569046021,-6.83712148666382,-6.74212121963501,3.25805902481079,3.6180591583252,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Ball Valve - 50-150 mm:50 mm:50 mm:578607',9,5.38847351074219,5.46847438812256,-7.00138759613037,-6.86238670349121,3.34432458877563,3.56032538414001,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Ball Valve - 50-150 mm:50 mm:50 mm:579260',10,3.33152604103088,3.4115264415741,-10.9376125335693,-10.7986116409302,3.34849953651428,3.56450009346008,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610469',11,2.09079885482788,2.23079895973206,-4.88524389266968,-4.74524307250977,5.59829998016357,5.70000076293945,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610426',12,2.24250793457031,2.38250827789307,-13.1490497589111,-13.0090494155884,5.59829998016357,5.70000076293945,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610319',13,6.44512605667114,6.58512783050537,-13.153998374939,-13.0139980316162,5.59829998016357,5.70000076293945,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL),
('DUPLEX','Unknown','IfcFlowController','M_Smoke Detector:Smoke Detector:Smoke Detector:610482',14,6.45928430557251,6.59928512573242,-4.92307806015015,-4.78307771682739,5.59829998016357,5.70000076293945,NULL,'MEP','[EXTRACTED: DUPLEX]',0,NULL,NULL);

-- Step 2: Rename DUPLEX → Ifc2x3_Duplex (match C_DocType.ProjectName)
UPDATE ad_element_placement SET building_type='Ifc2x3_Duplex' WHERE building_type='DUPLEX';

-- Step 3: Activate all Ifc2x3_Duplex rows
UPDATE ad_element_placement SET is_active=1 WHERE building_type='Ifc2x3_Duplex';

-- Verify: should return 1099
SELECT COUNT(*) || ' elements (expected 1099)' FROM ad_element_placement WHERE building_type='Ifc2x3_Duplex' AND is_active=1;
