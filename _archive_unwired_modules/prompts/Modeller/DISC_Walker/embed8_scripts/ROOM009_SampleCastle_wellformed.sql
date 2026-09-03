-- ════════════════════════════════════════════════════════
-- ROOM009: SampleCastle_ARC.db room recompile — §7 ROOM WELL-FORMEDNESS (bim-ootb modeller/)
--
-- ROOM_INJECTION_HYBRID.md §7 (2026-07-11): §WALL-VERT (curtain-wall IfcMember/IfcPlate
-- children join the raster iff bbox_z >= 0.5×median door height) + §STOREY-Z ('Unknown'-storey
-- enclosure/doors reassigned to their z-nearest real floor) + §RECT-HONESTY (largest inscribed
-- rectangle, expanded to raw walls — a room rect can no longer cross a wall) + §ROOM-FORM
-- (user doctrine: 'a room must be well formed, fully enclosed, has door'; failures carried as
-- SUSPECT_NO_DOOR/SUSPECT_OPEN '⚠'-marked review candidates, excluded from containment).
--
-- Source: build/room_walker.js RoomWalker.walk(db,{write:true}) against the real shipped
-- SampleCastle_ARC.db geometry (fable/modeller-lod400-livewire): 51 IfcSpace (9 SUSPECT_*),
-- 104 rel_contained_in_space rows. Parity: W-ROOM-WALKER-PARITY 6/6 byte-identical vs
-- scripts/compile_rooms.py; falsifiers W-ROOM-WELLFORMED 19/19 (zero wall-crossing rects,
-- zero suspect containment, HHS flood-fill on all 3 levels — no door-partition corridors).
-- Apply:  sqlite3 modeller/SampleCastle_ARC.db < prompts/Modeller/DISC_Walker/embed8_scripts/ROOM009_SampleCastle_wellformed.sql
-- Verify: sqlite3 modeller/SampleCastle_ARC.db "SELECT COUNT(*) FROM spatial_structure WHERE type='IfcSpace' AND guid LIKE 'RM\_%' ESCAPE '\'" -- expect 51
-- ════════════════════════════════════════════════════════

DELETE FROM spatial_structure WHERE (type='IfcBuildingStorey' AND guid LIKE 'STC\_%' ESCAPE '\')
   OR (type='IfcSpace' AND guid LIKE 'RM\_%' ESCAPE '\');
DELETE FROM rel_contained_in_space WHERE space_guid LIKE 'RM\_%' ESCAPE '\';

INSERT INTO spatial_structure VALUES('STC_00_begane_grond','IfcBuildingStorey','00 begane grond',NULL,'COMPILED',NULL,NULL,NULL,1.32865367212314877,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('STC_01_eerste_verdieping','IfcBuildingStorey','01 eerste verdieping',NULL,'COMPILED',NULL,NULL,NULL,4.37636263888026721,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('STC_02_tweede_verdieping','IfcBuildingStorey','02 tweede verdieping',NULL,'COMPILED',NULL,NULL,NULL,7.09613639445931365,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('STC_03_derde_verdieping','IfcBuildingStorey','03 derde verdieping',NULL,'COMPILED',NULL,NULL,NULL,10.1670031965739955,NULL,NULL,NULL);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_1','IfcSpace','⚠ 00 begane grond R1','STC_00_begane_grond','COMPILED','SUSPECT_OPEN',3.34999995949268303,18.2999999530399435,1.32865367212314877,6.0,5.20000000000000284,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_2','IfcSpace','≈ 00 begane grond R2','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',4.04999995949268321,13.8999999530399414,1.32865367212314877,1.40000000000000035,1.59999999999999964,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_3','IfcSpace','⚠ 00 begane grond R3','STC_00_begane_grond','COMPILED','SUSPECT_OPEN',8.24999995949268338,3.69999995303994211,1.32865367212314877,5.40000000000000124,4.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_4','IfcSpace','≈ 00 begane grond R4','STC_00_begane_grond','COMPILED','INTERNAL',7.24999995949268338,8.49999995303994282,1.32865367212314877,3.0,3.60000000000000142,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_5','IfcSpace','⚠ 00 begane grond R5','STC_00_begane_grond','COMPILED','SUSPECT_OPEN',7.74999995949268338,13.9999999530399428,1.32865367212314877,4.0,2.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_6','IfcSpace','≈ 00 begane grond R6','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',7.64999995949268374,17.7999999530399435,1.32865367212314877,1.80000000000000071,1.39999999999999857,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_7','IfcSpace','≈ 00 begane grond R7','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',9.3499999594926848,16.1999999530399421,1.32865367212314877,4.40000000000000035,1.0,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_8','IfcSpace','≈ 00 begane grond R8','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',10.2499999594926833,20.0999999530399442,1.32865367212314877,2.59999999999999964,1.60000000000000142,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_9','IfcSpace','≈ 00 begane grond R9','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',12.6499999594926837,10.2999999530399435,1.32865367212314877,1.39999999999999857,2.79999999999999893,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_10','IfcSpace','≈ 00 begane grond R10','STC_00_begane_grond','COMPILED','INTERNAL',13.7499999594926833,16.7999999530399435,1.32865367212314877,3.59999999999999786,2.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_11','IfcSpace','≈ 00 begane grond R11','STC_00_begane_grond','COMPILED','INTERNAL',13.7499999594926833,19.4999999530399463,1.32865367212314877,3.59999999999999786,2.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_12','IfcSpace','⚠ 00 begane grond R12','STC_00_begane_grond','COMPILED','SUSPECT_OPEN',16.4499999594926862,4.29999995303994264,1.32865367212314877,5.00000000000000177,6.00000000000000088,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_13','IfcSpace','≈ 00 begane grond R13','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',14.7499999594926833,11.9999999530399428,1.32865367212314877,1.59999999999999786,3.0,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_14','IfcSpace','≈ 00 begane grond R14','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',17.1499999594926855,10.6999999530399421,1.32865367212314877,2.40000000000000035,1.59999999999999964,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_15','IfcSpace','≈ 00 begane grond R15','STC_00_begane_grond','COMPILED','INTERNAL',18.8499999594926848,13.4999999530399428,1.32865367212314877,3.80000000000000071,3.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_16','IfcSpace','≈ 00 begane grond R16','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',19.7499999594926869,9.59999995303994246,1.32865367212314877,2.0,3.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_00_begane_grond_17','IfcSpace','≈ 00 begane grond R17','STC_00_begane_grond','COMPILED','INTERNAL_SMALL',20.6499999594926855,5.59999995303994246,1.32865367212314877,2.60000000000000142,1.40000000000000035,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_1','IfcSpace','⚠ 01 eerste verdieping R1','STC_01_eerste_verdieping','COMPILED','SUSPECT_OPEN',2.90704409512760841,17.3999999530398952,4.37636263888026721,4.79999999999999982,7.00000000000000177,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_2','IfcSpace','≈ 01 eerste verdieping R2','STC_01_eerste_verdieping','COMPILED','INTERNAL',9.50704409512760761,3.99999995303989219,4.37636263888026721,7.59999999999999964,5.40000000000000035,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_3','IfcSpace','≈ 01 eerste verdieping R3','STC_01_eerste_verdieping','COMPILED','INTERNAL',7.40704409512760975,8.69999995303989237,4.37636263888026721,3.00000000000000088,2.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_4','IfcSpace','≈ 01 eerste verdieping R4','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',7.20704409512760868,11.3999999530398934,4.37636263888026721,2.60000000000000053,1.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_5','IfcSpace','≈ 01 eerste verdieping R5','STC_01_eerste_verdieping','COMPILED','INTERNAL',7.80704409512760833,13.999999953039893,4.37636263888026721,3.79999999999999982,2.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_6','IfcSpace','≈ 01 eerste verdieping R6','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',7.70704409512760868,17.7999999530398937,4.37636263888026721,2.0,1.39999999999999857,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_7','IfcSpace','≈ 01 eerste verdieping R7','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',9.40704409512760975,16.1999999530398923,4.37636263888026721,4.20000000000000106,1.0,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_8','IfcSpace','≈ 01 eerste verdieping R8','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',10.2070440951276104,20.0999999530398945,4.37636263888026721,2.59999999999999964,1.60000000000000142,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_9','IfcSpace','≈ 01 eerste verdieping R9','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',12.0070440951276076,10.999999953039893,4.37636263888026721,2.59999999999999964,1.39999999999999857,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_10','IfcSpace','≈ 01 eerste verdieping R10','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',13.5070440951276093,16.7999999530398937,4.37636263888026721,3.19999999999999928,2.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_11','IfcSpace','≈ 01 eerste verdieping R11','STC_01_eerste_verdieping','COMPILED','INTERNAL',13.5070440951276093,19.4999999530398966,4.37636263888026721,3.19999999999999928,2.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_12','IfcSpace','⚠ 01 eerste verdieping R12','STC_01_eerste_verdieping','COMPILED','SUSPECT_OPEN',17.4070440951276097,3.89999995303989255,4.37636263888026721,7.0,5.20000000000000106,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_13','IfcSpace','≈ 01 eerste verdieping R13','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',16.2070440951276069,13.499999953039893,4.37636263888026721,1.80000000000000071,3.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_14','IfcSpace','≈ 01 eerste verdieping R14','STC_01_eerste_verdieping','COMPILED','INTERNAL',19.107044095127609,12.999999953039893,4.37636263888026721,3.20000000000000284,4.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_01_eerste_verdieping_15','IfcSpace','≈ 01 eerste verdieping R15','STC_01_eerste_verdieping','COMPILED','INTERNAL_SMALL',19.7070440951276104,8.69999995303989237,4.37636263888026721,2.0,3.60000000000000142,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_1','IfcSpace','≈ 02 tweede verdieping R1','STC_02_tweede_verdieping','COMPILED','INTERNAL',9.51000003393794912,3.9896153845855844,7.09613639445931365,7.59999999999999964,5.20000000000000106,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_2','IfcSpace','≈ 02 tweede verdieping R2','STC_02_tweede_verdieping','COMPILED','INTERNAL',7.4100000339379477,8.78961538458558422,7.09613639445931365,3.0,2.79999999999999982,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_3','IfcSpace','≈ 02 tweede verdieping R3','STC_02_tweede_verdieping','COMPILED','INTERNAL_SMALL',7.21000003393794841,11.4896153845855852,7.09613639445931365,2.60000000000000142,1.80000000000000071,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_4','IfcSpace','≈ 02 tweede verdieping R4','STC_02_tweede_verdieping','COMPILED','INTERNAL',7.81000003393794806,13.9896153845855835,7.09613639445931365,3.80000000000000071,2.40000000000000035,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_5','IfcSpace','≈ 02 tweede verdieping R5','STC_02_tweede_verdieping','COMPILED','INTERNAL_SMALL',12.0100000339379491,10.9896153845855852,7.09613639445931365,2.59999999999999964,1.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_6','IfcSpace','⚠ 02 tweede verdieping R6','STC_02_tweede_verdieping','COMPILED','SUSPECT_OPEN',17.4100000339379477,3.9896153845855844,7.09613639445931365,6.99999999999999822,5.20000000000000106,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_7','IfcSpace','≈ 02 tweede verdieping R7','STC_02_tweede_verdieping','COMPILED','INTERNAL_SMALL',16.2100000339379484,13.5896153845855849,7.09613639445931365,1.80000000000000248,3.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_8','IfcSpace','≈ 02 tweede verdieping R8','STC_02_tweede_verdieping','COMPILED','INTERNAL',19.1100000339379505,13.0896153845855849,7.09613639445931365,3.20000000000000284,4.19999999999999928,2.0);
INSERT INTO spatial_structure VALUES('RM_02_tweede_verdieping_9','IfcSpace','≈ 02 tweede verdieping R9','STC_02_tweede_verdieping','COMPILED','INTERNAL_SMALL',19.7100000339379519,8.78961538458558422,7.09613639445931365,2.00000000000000355,3.60000000000000053,2.0);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_1','IfcSpace','⚠ 03 derde verdieping R1','STC_03_derde_verdieping','COMPILED','SUSPECT_OPEN',10.1516960939439719,4.14176220321655375,10.1670031965739955,6.40000000000000035,5.19999999999999928,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_2','IfcSpace','≈ 03 derde verdieping R2','STC_03_derde_verdieping','COMPILED','INTERNAL',7.65169609394397287,8.74176220321655428,10.1670031965739955,2.59999999999999964,2.80000000000000071,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_3','IfcSpace','≈ 03 derde verdieping R3','STC_03_derde_verdieping','COMPILED','INTERNAL',7.55169609394397234,14.0417622032165549,10.1670031965739955,3.60000000000000053,2.19999999999999928,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_4','IfcSpace','≈ 03 derde verdieping R4','STC_03_derde_verdieping','COMPILED','INTERNAL_SMALL',7.35169609394397305,11.4417622032165553,10.1670031965739955,2.0,1.80000000000000071,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_5','IfcSpace','≈ 03 derde verdieping R5','STC_03_derde_verdieping','COMPILED','INTERNAL_SMALL',12.0516960939439741,10.9417622032165553,10.1670031965739955,2.59999999999999964,1.19999999999999928,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_6','IfcSpace','≈ 03 derde verdieping R6','STC_03_derde_verdieping','COMPILED','INTERNAL_SMALL',14.4516960939439726,12.241762203216556,10.1670031965739955,1.0,2.59999999999999964,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_7','IfcSpace','⚠ 03 derde verdieping R7','STC_03_derde_verdieping','COMPILED','SUSPECT_OPEN',16.6516960939439755,4.0417622032165541,10.1670031965739955,5.80000000000000071,5.0,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_8','IfcSpace','≈ 03 derde verdieping R8','STC_03_derde_verdieping','COMPILED','INTERNAL_SMALL',16.2516960939439734,13.4417622032165553,10.1670031965739955,1.80000000000000248,3.0,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_9','IfcSpace','≈ 03 derde verdieping R9','STC_03_derde_verdieping','COMPILED','INTERNAL',18.7516960939439734,12.741762203216556,10.1670031965739955,2.80000000000000426,3.59999999999999964,2.06239730426182976);
INSERT INTO spatial_structure VALUES('RM_03_derde_verdieping_10','IfcSpace','≈ 03 derde verdieping R10','STC_03_derde_verdieping','COMPILED','INTERNAL_SMALL',19.6516960939439755,9.24176220321655428,10.1670031965739955,1.80000000000000071,2.60000000000000053,2.06239730426182976);

INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_9','1ezF2lk9bATPGbDzbqi3lV');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_15','2q30Qb1_LBxgFSuRvgl_Fw');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_14','2aob8QCl16Q8N5ZYzE4b9k');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_14','2DoYNgCXz1jwarhecdsY1w');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_15','2ZJwwyMMvB5OA8maPq1bML');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_15','1ZG4DbpJf0lf8Ak2_bQJHW');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_11','1YpnSB4hH71g3zKepeErHS');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_11','1s_IKjunz8NgE4RwXYmqYm');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_11','1stCMPwc91r80V5f3OShVm');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_6','1d0MiLLsLCdBSuTHp6ynR7');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_9','2EK7acXG14vgx_QU1OeZ7J');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_17','1vpshNcXb0UeBNhC5kKbgC');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_17','2LoDEf2jP0b9CMdtWhwNOO');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_4','0QGCC$bkn95gfJjhCRK1S$');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_17','1m_qhh$In8cv9sJA57EIx9');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_16','2ZCCQn6f5CnA2EvHQZH92C');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_14','2dHfVnv319IQI87h$OrLBS');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_13','3kdu3VxUzF1R6ZJfnANstW');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_15','0HSv6PpbD2BfTJigk5W2Ro');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_11','1TnqA0GVv37hIAk1d8SLxS');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_10','0$5GRLLgn7I8ZPcI7rOsbG');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_8','3pCtkV8rH5cfLSpwnY7plt');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_2','3fBmwjfbHClhZFnLmF3vib');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_6','1Ubc9b75DETwPSDHK48dhV');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_2','1OIsB5jXb41PiO2kBNmaM4');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_2','2s_5IkW0XAae4X3I5zK7q4');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_13','2hh9UjWIL2w9WwuMjzqstz');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_11','2ah3ltN7nCQ9VF6L1zEK1m');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_6','287l_KcXPC0g4vq6mShiRv');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_2','2MGtJUm9nD$Re1_MDIv0g2');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_3','1ERFleB4fBBfBOswxk$Xdp');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_4','0KiQHeBg1E1vCJHKQmVSFE');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_15','3TyRkCAozEAhemCLbgpc0y');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_14','0MI_2HZ992Bx$nIp0Ts0HN');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_13','3z$jhdTIz3KQIVA03ccSlX');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_11','1hLy1Edbj5rgNEl39t96Zu');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','3U5VF90aX4EPY0n0sy5tfn');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_8','1TMPN6ORz3V97VjLq_yYT$');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_6','2WXOgWSjzCfR4IRJ_Z1gjK');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_1','0QENsgqy1AHgG_7VgYdbwc');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_1','1A4uhs68j3Suvp_DeIOyYn');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_7','3y2ALpG0z8_OFPbRCDWgIT');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_1','3IbuwYOm5EV9Q6cXmwVWqd');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_2','23kW8A8yPA5wftTGD5UJ8H');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_3','24d$bXEtDFMPrak4J5bypx');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_9','1kXSox_0P3kBFuaJXmZIbM');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_8','1PBBUyIv59YPrzStA9_TH5');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_7','3LCPmQCcP9UfP49BhFjY_v');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','3VK06fkLr3MfpgZhk2ZxRI');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','3AqFO5i0TDCQqSc0OVWUzc');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_3','2HEBa_Npr1jQ8s9obbPF2y');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_3','2wsdn60pL2PgWkM4Apz3uQ');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_2','11b4l8r$L2VO3CaHmD2Nt0');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_4','0BA5pIAPb4QBkmNlgEbu7Z');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_10','2xJxW3_1LEheyM8N4ef$kW');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','2Zh_HSGGPDUAc40uyeUaqQ');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_8','25tyzjfSP76hJKtX0Kf9lQ');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','0jZ0KfZxz8BQL8ZDATGOfF');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','1ImJUzssz2EfAnoEV3fllB');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','2zM1Tk9LH6aQ9sE5J9MG5E');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','0TWmTOBPH0afnbECFJTJU9');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','2AxmdTss5DgR7LwNn2YGAN');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','07XglCvfr2NBer4fwBJv6e');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','38Qe2zvj9E0OBObNVI4$Zz');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','2jS7doCMf3$gQseynrXN3R');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','1rcblQbSX0w8i_vHHFGq3M');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_5','05ZZdIzTnEbQa7eCaF53yx');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','0IAn27JfLBpu8ypPPKG8k3');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','0eucvL7jTBzhWlFD6WbUM1');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','1RN0CQ6Lb1ku4LwN9DvM0W');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_4','2$MSonz3z9Y9Jeihbq_AXi');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_3','1HQGFZa79CiRrtzPU0iA54');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_17','1LRccoRIL3pOi1F3fWQ823');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_9','3YLr44W_T0$hR$y7rYh5os');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_13','3sKQ6QuHD31wtOK0FcID0a');
INSERT INTO rel_contained_in_space VALUES('RM_00_begane_grond_8','06TesYVdv1thCfCzos6o8w');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','3FoypEz3L1afXc_6qgavRq');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','0AxVSwDQ90PfbBvFbPBxDk');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','2NzsfLBnz43R5cy8gHV1eH');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','0KOIvJyqbFcuFeGyLdVonv');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_10','1Sr4z35Cf9O9$YQvAN1CQC');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_11','1l9L7qKrzEL9azLVBcAE3S');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_9','2kF53bHFnFMvuaRuGEYDUH');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_9','1sT4IHpZr3me_3$srzb_zp');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_13','1J9r4Yftj7Ixm9V32QS7kQ');
INSERT INTO rel_contained_in_space VALUES('RM_01_eerste_verdieping_8','0GF4P9$lH0GQxrb5w2JDMA');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_5','1hTtFy5GX29gKSYz67rHrm');
INSERT INTO rel_contained_in_space VALUES('RM_02_tweede_verdieping_7','2fYhFjv$X1Wv9AkbHXwz4$');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_3','39GpFl7Sb9COtYLmEq1bBG');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_3','3ampq18Jf7lwcdUQsC9ELw');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_4','3hp1ogHnHEC9eB1EF4bOaz');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_4','0UHoHjlq907fKvrMiyD5Gz');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_4','1tAQUP$z94UwTQpNeJX4E8');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_4','1I8GdRgtDEoxj2$_ds2ndO');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_2','0lJ567uOr8MeD_B0FMWVVF');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_2','0hT8iB0E1A7wfEmOXSnJvc');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_2','05Ymi586r3ggIG8cxB0Rf_');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_2','24t9qQjNT1bfDuk21kjNTC');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','2HZVTQfvz2_RY0R3QBsj6V');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','35i9FqpvT028dX6SBVtQmA');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','334Y17iJr22BXIk291fNGo');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_9','1fj0gUwAz2eAsbUPokBdb0');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_5','3SYCrkVTLBseq0W7bRCSq6');
INSERT INTO rel_contained_in_space VALUES('RM_03_derde_verdieping_8','15Z5paLVTCWeFsxBI51Mb7');
