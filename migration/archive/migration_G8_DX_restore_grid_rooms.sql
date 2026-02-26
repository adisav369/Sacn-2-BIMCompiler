-- ============================================================
-- migration_G8_DX_restore_grid_rooms.sql
-- Restore ad_room_boundary bounds + fix ad_wall_face storey names
-- for Ifc2x3_Duplex ROOM_Level_* grid rooms.
--
-- Bounds sourced from elements_rtree (IFC meters → mm).
-- Generated: 2026-02-23
-- Rooms : 40  (IFC bounds: 40, fallback: 0)
--
-- Also restores is_active=1 for 715 ARC/MEP/STR element_rules
-- that were incorrectly retired by migration_G8_DX_retire_stale_room_rules.sql.
-- FURN ROOM rules (27 rows, is_active=0) are intentionally kept deactivated.
-- Safe to re-apply (INSERT OR IGNORE + idempotent UPDATEs).
-- ============================================================

BEGIN TRANSACTION;

-- ── Section 0: Remove stale wall_face rows (old 'Level 1'/'Level 2' storey) ──
-- 168 rows will be deleted.
DELETE FROM ad_wall_face
  WHERE building_type = 'Ifc2x3_Duplex'
    AND room_name LIKE 'ROOM_Level_%'
    AND storey IN ('Level 1', 'Level 2');

-- ── Section A: ad_room_boundary ─────────────────────────────

-- ROOM_Level_1_1 | Ground | LIVING | IFC_RTREE | elements=23 matched=23
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_1', 'LIVING', '', '', '', '', 0.0, 1, -389.4, 8800.0, -17800.0, -2942.1, 2);
UPDATE ad_room_boundary SET min_x_mm=-389.4, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=-2942.1, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_1';

-- ROOM_Level_1_10 | Ground | GENERIC | IFC_RTREE | elements=13 matched=13
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_10', 'GENERIC', '', '', '', '', 0.0, 1, 0.0, 8800.0, -17800.0, -2756.5, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=-2756.5, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_10';

-- ROOM_Level_1_11 | Ground | LIVING | IFC_RTREE | elements=6 matched=6
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_11', 'LIVING', '', '', '', '', 0.0, 1, 448.7, 8800.0, -17800.0, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=448.7, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=-417.0, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_11';

-- ROOM_Level_1_12 | Ground | KITCHEN | IFC_RTREE | elements=33 matched=33
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_12', 'KITCHEN', '', '', '', '', 0.0, 1, 363.0, 8437.0, -17351.3, -3038.4, 2);
UPDATE ad_room_boundary SET min_x_mm=363.0, max_x_mm=8437.0, min_y_mm=-17351.3, max_y_mm=-3038.4, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_12';

-- ROOM_Level_1_13 | Ground | GENERIC | IFC_RTREE | elements=103 matched=103
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_13', 'GENERIC', '', '', '', '', 0.0, 1, -44.0, 8437.0, -17529.1, -270.9, 2);
UPDATE ad_room_boundary SET min_x_mm=-44.0, max_x_mm=8437.0, min_y_mm=-17529.1, max_y_mm=-270.9, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_13';

-- ROOM_Level_1_14 | Ground | KITCHEN | IFC_RTREE | elements=33 matched=33
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_14', 'KITCHEN', '', '', '', '', 0.0, 1, -0.0, 8437.0, -17800.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8437.0, min_y_mm=-17800.0, max_y_mm=0.0, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_14';

-- ROOM_Level_1_15 | Ground | KITCHEN | IFC_RTREE | elements=3 matched=3
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_15', 'KITCHEN', '', '', '', '', 0.0, 1, 511.5, 6280.0, -10776.6, -7127.2, 2);
UPDATE ad_room_boundary SET min_x_mm=511.5, max_x_mm=6280.0, min_y_mm=-10776.6, max_y_mm=-7127.2, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_15';

-- ROOM_Level_1_16 | Ground | KITCHEN | IFC_RTREE | elements=4 matched=4
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_16', 'KITCHEN', '', '', '', '', 0.0, 1, 505.2, 8347.3, -11425.6, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=505.2, max_x_mm=8347.3, min_y_mm=-11425.6, max_y_mm=-417.0, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_16';

-- ROOM_Level_1_17 | Ground | KITCHEN | IFC_RTREE | elements=28 matched=28
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_17', 'KITCHEN', '', '', '', '', 0.0, 1, -72.5, 8400.0, -17431.7, -362.3, 2);
UPDATE ad_room_boundary SET min_x_mm=-72.5, max_x_mm=8400.0, min_y_mm=-17431.7, max_y_mm=-362.3, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_17';

-- ROOM_Level_1_18 | Ground | KITCHEN | IFC_RTREE | elements=82 matched=82
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_18', 'KITCHEN', '', '', '', '', 0.0, 1, -389.5, 8431.8, -17431.8, -362.3, 2);
UPDATE ad_room_boundary SET min_x_mm=-389.5, max_x_mm=8431.8, min_y_mm=-17431.8, max_y_mm=-362.3, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_18';

-- ROOM_Level_1_19 | Ground | KITCHEN | IFC_RTREE | elements=33 matched=33
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_19', 'KITCHEN', '', '', '', '', 0.0, 1, 0.0, 8800.0, -17800.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=0.0, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_19';

-- ROOM_Level_1_2 | Ground | GENERIC | IFC_RTREE | elements=15 matched=15
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_2', 'GENERIC', '', '', '', '', 0.0, 1, -0.0, 8800.0, -14483.0, -5195.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8800.0, min_y_mm=-14483.0, max_y_mm=-5195.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_2';

-- ROOM_Level_1_20 | Ground | LIVING | IFC_RTREE | elements=5 matched=5
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_20', 'LIVING', '', '', '', '', 0.0, 1, -0.0, 8800.0, -17800.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=0.0, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_20';

-- ROOM_Level_1_21 | Ground | GENERIC | IFC_RTREE | elements=14 matched=14
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_21', 'GENERIC', '', '', '', '', 0.0, 1, 362.7, 8437.3, -15056.2, -399.7, 2);
UPDATE ad_room_boundary SET min_x_mm=362.7, max_x_mm=8437.3, min_y_mm=-15056.2, max_y_mm=-399.7, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_21';

-- ROOM_Level_1_22 | Ground | GENERIC | IFC_RTREE | elements=3 matched=3
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_22', 'GENERIC', '', '', '', '', 0.0, 1, 452.7, 6294.0, -11286.6, -6666.0, 2);
UPDATE ad_room_boundary SET min_x_mm=452.7, max_x_mm=6294.0, min_y_mm=-11286.6, max_y_mm=-6666.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_22';

-- ROOM_Level_1_23 | Ground | GENERIC | IFC_RTREE | elements=24 matched=24
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_23', 'GENERIC', '', '', '', '', 0.0, 1, 406.5, 8437.3, -17394.5, -500.4, 2);
UPDATE ad_room_boundary SET min_x_mm=406.5, max_x_mm=8437.3, min_y_mm=-17394.5, max_y_mm=-500.4, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_23';

-- ROOM_Level_1_24 | Ground | KITCHEN | IFC_RTREE | elements=7 matched=7
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_24', 'KITCHEN', '', '', '', '', 0.0, 1, 0.0, 8800.0, -17800.0, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=-417.0, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_24';

-- ROOM_Level_1_25 | Ground | LIVING | IFC_RTREE | elements=2 matched=2
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_25', 'LIVING', '', '', '', '', 0.0, 1, 4770.0, 6324.0, -17383.0, -10246.0, 2);
UPDATE ad_room_boundary SET min_x_mm=4770.0, max_x_mm=6324.0, min_y_mm=-17383.0, max_y_mm=-10246.0, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_25';

-- ROOM_Level_1_26 | Ground | GENERIC | IFC_RTREE | elements=13 matched=13
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_26', 'GENERIC', '', '', '', '', 0.0, 1, 878.8, 8437.3, -17429.3, -399.7, 2);
UPDATE ad_room_boundary SET min_x_mm=878.8, max_x_mm=8437.3, min_y_mm=-17429.3, max_y_mm=-399.7, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_26';

-- ROOM_Level_1_28 | Ground | GENERIC | IFC_RTREE | elements=1 matched=1
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_28', 'GENERIC', '', '', '', '', 0.0, 1, 0.0, 8800.0, -417.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-417.0, max_y_mm=0.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_28';

-- ROOM_Level_1_29 | Ground | GENERIC | IFC_RTREE | elements=9 matched=9
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_29', 'GENERIC', '', '', '', '', 0.0, 1, 362.7, 7887.5, -17431.7, -6885.7, 2);
UPDATE ad_room_boundary SET min_x_mm=362.7, max_x_mm=7887.5, min_y_mm=-17431.7, max_y_mm=-6885.7, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_29';

-- ROOM_Level_1_3 | Ground | GENERIC | IFC_RTREE | elements=5 matched=5
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_3', 'GENERIC', '', '', '', '', 0.0, 1, 0.0, 8383.0, -17800.0, -0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8383.0, min_y_mm=-17800.0, max_y_mm=-0.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_3';

-- ROOM_Level_1_30 | Ground | LIVING | IFC_RTREE | elements=21 matched=21
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_30', 'LIVING', '', '', '', '', 0.0, 1, 0.0, 8434.9, -17469.9, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8434.9, min_y_mm=-17469.9, max_y_mm=-417.0, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_30';

-- ROOM_Level_1_5 | Ground | GENERIC | IFC_RTREE | elements=13 matched=13
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_5', 'GENERIC', '', '', '', '', 0.0, 1, -0.0, 8800.0, -14483.0, -0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8800.0, min_y_mm=-14483.0, max_y_mm=-0.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_5';

-- ROOM_Level_1_6 | Ground | LIVING | IFC_RTREE | elements=2 matched=2
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_6', 'LIVING', '', '', '', '', 0.0, 1, 448.7, 2572.7, -11321.5, -7833.7, 2);
UPDATE ad_room_boundary SET min_x_mm=448.7, max_x_mm=2572.7, min_y_mm=-11321.5, max_y_mm=-7833.7, room_type='LIVING', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_6';

-- ROOM_Level_1_7 | Ground | KITCHEN | IFC_RTREE | elements=16 matched=16
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_7', 'KITCHEN', '', '', '', '', 0.0, 1, 454.9, 8383.0, -15335.1, -448.8, 2);
UPDATE ad_room_boundary SET min_x_mm=454.9, max_x_mm=8383.0, min_y_mm=-15335.1, max_y_mm=-448.8, room_type='KITCHEN', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_7';

-- ROOM_Level_1_8 | Ground | GENERIC | IFC_RTREE | elements=32 matched=32
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_8', 'GENERIC', '', '', '', '', 0.0, 1, 0.0, 8800.0, -17800.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=0.0, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_8';

-- ROOM_Level_1_9 | Ground | GENERIC | IFC_RTREE | elements=3 matched=3
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_9', 'GENERIC', '', '', '', '', 0.0, 1, 4076.0, 8437.0, -10364.4, -5018.3, 2);
UPDATE ad_room_boundary SET min_x_mm=4076.0, max_x_mm=8437.0, min_y_mm=-10364.4, max_y_mm=-5018.3, room_type='GENERIC', z_offset_mm=0.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Ground' AND room_name='ROOM_Level_1_9';

-- ROOM_Level_2_1 | Upper | GENERIC | IFC_RTREE | elements=6 matched=6
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_1', 'GENERIC', '', '', '', '', 3100.0, 1, 371.5, 8383.0, -17351.3, -6168.1, 2);
UPDATE ad_room_boundary SET min_x_mm=371.5, max_x_mm=8383.0, min_y_mm=-17351.3, max_y_mm=-6168.1, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_1';

-- ROOM_Level_2_10 | Upper | BEDROOM | IFC_RTREE | elements=7 matched=7
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_10', 'BEDROOM', '', '', '', '', 3100.0, 1, -0.0, 8383.0, -17383.0, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8383.0, min_y_mm=-17383.0, max_y_mm=-417.0, room_type='BEDROOM', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_10';

-- ROOM_Level_2_11 | Upper | GENERIC | IFC_RTREE | elements=1 matched=1
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_11', 'GENERIC', '', '', '', '', 3100.0, 1, 417.0, 8383.0, -9725.0, -417.0, 2);
UPDATE ad_room_boundary SET min_x_mm=417.0, max_x_mm=8383.0, min_y_mm=-9725.0, max_y_mm=-417.0, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_11';

-- ROOM_Level_2_12 | Upper | GENERIC | IFC_RTREE | elements=6 matched=6
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_12', 'GENERIC', '', '', '', '', 3100.0, 1, -0.0, 8383.0, -17800.0, -7466.0, 2);
UPDATE ad_room_boundary SET min_x_mm=-0.0, max_x_mm=8383.0, min_y_mm=-17800.0, max_y_mm=-7466.0, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_12';

-- ROOM_Level_2_2 | Upper | GENERIC | IFC_RTREE | elements=1 matched=1
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_2', 'GENERIC', '', '', '', '', 3100.0, 1, 2574.0, 4030.0, -7554.0, -7430.0, 2);
UPDATE ad_room_boundary SET min_x_mm=2574.0, max_x_mm=4030.0, min_y_mm=-7554.0, max_y_mm=-7430.0, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_2';

-- ROOM_Level_2_3 | Upper | BEDROOM | IFC_RTREE | elements=7 matched=7
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_3', 'BEDROOM', '', '', '', '', 3100.0, 1, 0.0, 8800.0, -14839.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=0.0, max_x_mm=8800.0, min_y_mm=-14839.0, max_y_mm=0.0, room_type='BEDROOM', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_3';

-- ROOM_Level_2_4 | Upper | GENERIC | IFC_RTREE | elements=8 matched=8
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_4', 'GENERIC', '', '', '', '', 3100.0, 1, 417.0, 8800.0, -17800.0, 0.0, 2);
UPDATE ad_room_boundary SET min_x_mm=417.0, max_x_mm=8800.0, min_y_mm=-17800.0, max_y_mm=0.0, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_4';

-- ROOM_Level_2_5 | Upper | KITCHEN | IFC_RTREE | elements=75 matched=75
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_5', 'KITCHEN', '', '', '', '', 3100.0, 1, 365.1, 8434.9, -17429.3, -364.7, 2);
UPDATE ad_room_boundary SET min_x_mm=365.1, max_x_mm=8434.9, min_y_mm=-17429.3, max_y_mm=-364.7, room_type='KITCHEN', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_5';

-- ROOM_Level_2_6 | Upper | GENERIC | IFC_RTREE | elements=5 matched=5
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_6', 'GENERIC', '', '', '', '', 3100.0, 1, 365.1, 8328.0, -13199.0, -364.8, 2);
UPDATE ad_room_boundary SET min_x_mm=365.1, max_x_mm=8328.0, min_y_mm=-13199.0, max_y_mm=-364.8, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_6';

-- ROOM_Level_2_7 | Upper | GENERIC | IFC_RTREE | elements=4 matched=4
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_7', 'GENERIC', '', '', '', '', 3100.0, 1, 2605.7, 8825.0, -16879.0, -6544.1, 2);
UPDATE ad_room_boundary SET min_x_mm=2605.7, max_x_mm=8825.0, min_y_mm=-16879.0, max_y_mm=-6544.1, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_7';

-- ROOM_Level_2_8 | Upper | KITCHEN | IFC_RTREE | elements=75 matched=75
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_8', 'KITCHEN', '', '', '', '', 3100.0, 1, 807.4, 8383.0, -10610.1, -6126.0, 2);
UPDATE ad_room_boundary SET min_x_mm=807.4, max_x_mm=8383.0, min_y_mm=-10610.1, max_y_mm=-6126.0, room_type='KITCHEN', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_8';

-- ROOM_Level_2_9 | Upper | GENERIC | IFC_RTREE | elements=6 matched=6
INSERT OR IGNORE INTO ad_room_boundary (building_type, storey, room_name, room_type, grid_min_x, grid_max_x, grid_min_y, grid_max_y, z_offset_mm, is_active, min_x_mm, max_x_mm, min_y_mm, max_y_mm, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_9', 'GENERIC', '', '', '', '', 3100.0, 1, 3040.6, 8357.6, -11134.0, -6155.4, 2);
UPDATE ad_room_boundary SET min_x_mm=3040.6, max_x_mm=8357.6, min_y_mm=-11134.0, max_y_mm=-6155.4, room_type='GENERIC', z_offset_mm=3100.0 WHERE building_type='Ifc2x3_Duplex' AND storey='Upper' AND room_name='ROOM_Level_2_9';


-- ── Section B: ad_wall_face ──────────────────────────────────
-- Inserts 160 rows with correct storey='Ground'/'Upper'.

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_1', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_1', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_1', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_1', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_10', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_10', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_10', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_10', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_11', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_11', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_11', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_11', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_12', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_12', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_12', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_12', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_13', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_13', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_13', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_13', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_14', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_14', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_14', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_14', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_15', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_15', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_15', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_15', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_16', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_16', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_16', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_16', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_17', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_17', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_17', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_17', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_18', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_18', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_18', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_18', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_19', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_19', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_19', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_19', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_2', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_2', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_2', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_2', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_20', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_20', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_20', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_20', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_21', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_21', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_21', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_21', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_22', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_22', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_22', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_22', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_23', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_23', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_23', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_23', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_24', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_24', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_24', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_24', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_25', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_25', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_25', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_25', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_26', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_26', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_26', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_26', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_28', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_28', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_28', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_28', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_29', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_29', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_29', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_29', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_3', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_3', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_3', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_3', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_30', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_30', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_30', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_30', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_5', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_5', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_5', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_5', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_6', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_6', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_6', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_6', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_7', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_7', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_7', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_7', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_8', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_8', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_8', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_8', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_9', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_9', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_9', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Ground', 'ROOM_Level_1_9', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_1', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_1', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_1', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_1', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_10', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_10', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_10', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_10', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_11', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_11', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_11', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_11', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_12', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_12', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_12', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_12', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_2', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_2', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_2', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_2', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_3', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_3', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_3', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_3', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_4', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_4', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_4', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_4', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_5', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_5', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_5', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_5', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_6', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_6', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_6', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_6', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_7', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_7', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_7', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_7', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_8', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_8', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_8', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_8', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);

INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_9', 'NORTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_9', 'SOUTH', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_9', 'EAST', 'INTERIOR_PARTITION_92', 0, 1, 2);
INSERT OR IGNORE INTO ad_wall_face (building_type, storey, room_name, face, wall_type_id, is_exterior, is_active, building_id) VALUES ('Ifc2x3_Duplex', 'Upper', 'ROOM_Level_2_9', 'WEST', 'INTERIOR_PARTITION_92', 0, 1, 2);


-- ── Section C: Restore retired ARC/MEP/STR element_rules ───────────────────
-- Reverses migration_G8_DX_retire_stale_room_rules.sql
-- FURN ROOM rules (27 rows) intentionally excluded — remain is_active=0.

UPDATE ad_element_rule
SET is_active = 1
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   IN ('ARC', 'MEP', 'STR')
  AND host_type    = 'ROOM'
  AND host_ref LIKE 'ROOM_Level_%';

UPDATE ad_element_rule
SET is_active = 1
WHERE building_type = 'Ifc2x3_Duplex'
  AND discipline   = 'ARC'
  AND host_type    = 'WALL'
  AND host_ref LIKE 'WALL_ROOM_Level_%';

COMMIT;

-- Verification queries (run after applying):
-- SELECT COUNT(*) FROM ad_room_boundary
--   WHERE building_type='Ifc2x3_Duplex' AND room_name LIKE 'ROOM_Level_%';
-- Expected: 40

-- SELECT COUNT(*) FROM ad_wall_face
--   WHERE building_type='Ifc2x3_Duplex' AND room_name LIKE 'ROOM_Level_%'
--   AND storey IN ('Ground','Upper');
-- Expected: 160

-- SELECT COUNT(*) FROM ad_element_rule
--   WHERE building_type='Ifc2x3_Duplex' AND is_active=1;
-- Expected: ~1032 (317 + 715 restored)

-- SELECT COUNT(*) FROM ad_element_rule
--   WHERE building_type='Ifc2x3_Duplex' AND is_active=0
--     AND discipline='FURN' AND host_type='ROOM';
-- Expected: 27 (still deactivated — intentional)

-- ── End of migration ─────────────────────────────────────────