-- ════════════════════════════════════════════════════════
-- ROOM018: Garage_ARC.db room recompile — §8 MULTI-RECT + §DISC-ARC (bim-ootb modeller/)
--
-- ROOM_INJECTION_HYBRID.md §8 (2026-07-11): a confirmed room is now a SET of non-overlapping
-- rectangles (repeated constrained maximal-rectangle scan over the seal-band-recovered region;
-- sub-rects >= NOISE_FLOOR in both axes; stop at 0.95 coverage / 8 rects) — one row per
-- sub-rect, grouped by the NEW room_guid column (= primary guid; extras lettered RM_..b/c).
-- Includes the same-day §DISC-ARC fix (discipline='ARC' on all enclosure queries) and
-- supersedes ROOM12's §7 single-rect data. SUSPECT rooms stay single-rect.
-- 5 logical rooms (3 SUSPECT) as 7 rect rows + 0 rel rows (logical guids only).
-- Witness: W-ROOM-WALKER-PARITY 6/6 · W-ROOM-WELLFORMED 19/19 · W-ROOM-FILL 18/18 · W-HBA-MULTIRECT 6/6.
-- Apply:  sqlite3 modeller/Garage_ARC.db < prompts/Modeller/DISC_Walker/embed8_scripts/ROOM018_Garage_multirect.sql
-- Verify: sqlite3 modeller/Garage_ARC.db "SELECT COUNT(*), COUNT(DISTINCT room_guid) FROM spatial_structure WHERE type='IfcSpace'" -- expect 7|5
-- ════════════════════════════════════════════════════════

-- NOTE: errors harmlessly ("duplicate column") when room_guid already exists — sqlite3 CLI
-- continues past it; a programmatic applier (patches/ loader) must run statements tolerantly.
ALTER TABLE spatial_structure ADD COLUMN room_guid TEXT;

DELETE FROM spatial_structure WHERE (type='IfcBuildingStorey' AND guid LIKE 'STC\_%' ESCAPE '\')
   OR (type='IfcSpace' AND guid LIKE 'RM\_%' ESCAPE '\');
DELETE FROM rel_contained_in_space WHERE space_guid LIKE 'RM\_%' ESCAPE '\';

INSERT INTO spatial_structure VALUES('STC_Exist_Garage_-_Ground_Level','IfcBuildingStorey','Exist Garage - Ground Level',NULL,'COMPILED',NULL,NULL,NULL,170.442992264719947,NULL,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_1','IfcSpace','≈ Exist Garage - Ground Level R1','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',192.118292145439284,116.013805197488466,170.442992264719947,15.6000000000000227,4.60000000000002273,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_1');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_1b','IfcSpace','≈ Exist Garage - Ground Level R1','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',193.718292145439249,110.313805197488463,170.442992264719947,11.5999999999999943,4.79999999999998294,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_1');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_1c','IfcSpace','≈ Exist Garage - Ground Level R1','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',198.418292145439295,113.213805197488454,170.442992264719947,3.0,1.0,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_1');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_2','IfcSpace','≈ Exist Garage - Ground Level R2','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',193.718292145439249,104.813805197488463,170.442992264719947,11.5999999999999943,4.59999999999999431,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_2');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_3','IfcSpace','⚠ Exist Garage - Ground Level R3','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',197.818292145439272,134.113805197488488,170.442992264719947,5.79999999999998294,1.59999999999999431,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_3');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_4','IfcSpace','⚠ Exist Garage - Ground Level R4','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',199.318292145439272,171.313805197488477,170.442992264719947,5.60000000000002273,2.40000000000000568,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_4');
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_5','IfcSpace','⚠ Exist Garage - Ground Level R5','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',200.318292145439272,155.513805197488466,170.442992264719947,1.60000000000002273,6.80000000000001136,8.215614621527493,'RM_Exist_Garage_-_Ground_Level_5');

