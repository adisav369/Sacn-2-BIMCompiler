-- ════════════════════════════════════════════════════════
-- ROOM012: Garage_ARC.db room recompile — §7 ROOM WELL-FORMEDNESS (bim-ootb modeller/)
--
-- ROOM_INJECTION_HYBRID.md §7 (2026-07-11): §WALL-VERT (curtain-wall IfcMember/IfcPlate
-- children join the raster iff bbox_z >= 0.5×median door height) + §STOREY-Z ('Unknown'-storey
-- enclosure/doors reassigned to their z-nearest real floor) + §RECT-HONESTY (largest inscribed
-- rectangle, expanded to raw walls — a room rect can no longer cross a wall) + §ROOM-FORM
-- (user doctrine: 'a room must be well formed, fully enclosed, has door'; failures carried as
-- SUSPECT_NO_DOOR/SUSPECT_OPEN '⚠'-marked review candidates, excluded from containment).
--
-- Source: build/room_walker.js RoomWalker.walk(db,{write:true}) against the real shipped
-- Garage_ARC.db geometry (fable/modeller-lod400-livewire): 5 IfcSpace (3 SUSPECT_*),
-- 0 rel_contained_in_space rows. Parity: W-ROOM-WALKER-PARITY 6/6 byte-identical vs
-- scripts/compile_rooms.py; falsifiers W-ROOM-WELLFORMED 19/19 (zero wall-crossing rects,
-- zero suspect containment, HHS flood-fill on all 3 levels — no door-partition corridors).
-- Apply:  sqlite3 modeller/Garage_ARC.db < prompts/Modeller/DISC_Walker/embed8_scripts/ROOM012_Garage_wellformed.sql
-- Verify: sqlite3 modeller/Garage_ARC.db "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace' AND guid LIKE 'RM\_%' ESCAPE '\'" -- expect 5
-- ════════════════════════════════════════════════════════

DELETE FROM spatial_structure WHERE (type='IfcBuildingStorey' AND guid LIKE 'STC\_%' ESCAPE '\')
   OR (type='IfcSpace' AND guid LIKE 'RM\_%' ESCAPE '\');
DELETE FROM rel_contained_in_space WHERE space_guid LIKE 'RM\_%' ESCAPE '\';

INSERT INTO spatial_structure VALUES('STC_Exist_Garage_-_Ground_Level','IfcBuildingStorey','Exist Garage - Ground Level',NULL,'COMPILED',NULL,NULL,NULL,172.545087578986283,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_1','IfcSpace','≈ Exist Garage - Ground Level R1','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',191.918292145439295,116.096082230170765,172.545087578986283,16.0000000000000284,4.59999999999999431,12.8336275801402344);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_2','IfcSpace','≈ Exist Garage - Ground Level R2','STC_Exist_Garage_-_Ground_Level','COMPILED','INTERNAL',193.718292145439278,104.796082230170767,172.545087578986283,12.4000000000000341,4.40000000000000568,12.8336275801402344);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_3','IfcSpace','⚠ Exist Garage - Ground Level R3','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',197.818292145439272,134.296082230170782,172.545087578986283,6.60000000000002273,1.40000000000000568,12.8336275801402344);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_4','IfcSpace','⚠ Exist Garage - Ground Level R4','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',199.418292145439266,171.396082230170776,172.545087578986283,6.19999999999998863,2.4000000000000341,12.8336275801402344);
INSERT INTO spatial_structure VALUES('RM_Exist_Garage_-_Ground_Level_5','IfcSpace','⚠ Exist Garage - Ground Level R5','STC_Exist_Garage_-_Ground_Level','COMPILED','SUSPECT_NO_DOOR',200.318292145439272,155.596082230170765,172.545087578986283,1.60000000000002273,7.59999999999996589,12.8336275801402344);

