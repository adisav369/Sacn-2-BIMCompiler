-- migration_RM6_plumbing.sql
-- Phase RM-6: Wet room fixture fixes + plumbing system rules + CONNECTS_TO graph
--
-- Changes:
-- 1. Delete extra WC from bilik_mandi (FE_3)
-- 2. Reposition WC in tandas to back wall (FE_5)
-- 3. Add TOILET room slots (matching TOILET_BLOCK pattern)
-- 4. Add 9 plumbing pipes as IfcFlowSegment (matching Duplex pattern)
-- 5. Add CONNECTS_TO dependency edges for TB-LKTN pipe graph
-- 6. Add CONNECTS_TO dependency edges for Duplex flow system
--
-- Element count: TB-LKTN 61 -> 69 (delete FE_3 = -1, add 9 pipes = +9)

-- ==========================================================================
-- Part 1: Wet Room Fixture Fixes
-- ==========================================================================

-- 1a. Delete extra toilet from bilik_mandi
-- FE_3 is WC_Seating in bathroom (bilik_mandi) -- WRONG. Tandas is the toilet room.
-- Bilik_mandi should have: basin (FE_2) + shower (FE_4) only.
DELETE FROM ad_element_rule WHERE building_type='TB_LKTN' AND element_ref='IfcFurnishingElement_3';
DELETE FROM ad_geometry_map WHERE building_type='TB_LKTN' AND element_ref='IfcFurnishingElement_3';
DELETE FROM ad_element_dependency WHERE building_type='TB_LKTN' AND element_ref='IfcFurnishingElement_3';

-- 1b. Fix WC position in tandas -- push to back wall
-- FE_5 at position_value_2=0.5 (centered in Y). Tandas Y=5400-7000.
-- WC should be against NORTH wall (back). Change to 0.85 -> cy=5400+0.85*1600=6760mm.
UPDATE ad_element_rule SET position_value_2 = 0.85
WHERE building_type='TB_LKTN' AND element_ref='IfcFurnishingElement_5';

-- 1c. Add TOILET room slots (matching TOILET_BLOCK pattern from Duplex)
INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, assembly_id, version_range, slot_face, slot_priority, is_required)
VALUES ('TOILET', 'SANITARY', 'TOILET_BLOCK_FIXTURES', '[1.0,2.0)', 'BACK', 10, 1);

INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, version_range, slot_face, slot_priority, is_required)
VALUES ('TOILET', 'FLOOR_TRAP', '[1.0,2.0)', 'BOTTOM', 60, 0);

INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, version_range, slot_face, slot_priority, is_required)
VALUES ('TOILET', 'EXHAUST', '[1.0,2.0)', 'TOP', 50, 0);

INSERT OR IGNORE INTO ad_room_slot (room_type, slot_name, version_range, slot_face, slot_priority, is_required)
VALUES ('TOILET', 'BASIN', '[1.0,2.0)', 'LEFT', 20, 0);


-- ==========================================================================
-- Part 2: Plumbing Pipe Rules (9 IfcFlowSegments -- matching Duplex pattern)
-- ==========================================================================

-- TB-LKTN waste/supply plumbing. Uses IfcFlowSegment (same as Duplex).
-- Host type = ROOM (contained in room, same as Duplex).
-- Discipline = MEP, family_ref = Pipe Types:xxx (same as Duplex families).
--
-- Waste system: fixture -> branch -> riser -> underground -> MH
-- Supply: cold water main feeds wet rooms
--
-- Positioning: FRACTION within host room bbox.

-- Pipe 1: Waste riser in tandas (vertical, along west wall)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_1', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'tandas', 'FRACTION',
    0.1, 0.5, -150.0,
    'Pipe Types:Waste', 100.0, 3150.0, 100.0,
    'VERTICAL', 1
);

-- Pipe 2: WC branch to tandas riser (horizontal EW in tandas)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_2', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'tandas', 'FRACTION',
    0.5, 0.85, -150.0,
    'Pipe Types:Waste', 800.0, 100.0, 100.0,
    'EW', 1
);

-- Pipe 3: Basin branch in bilik_mandi to tandas riser (horizontal)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_3', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'bilik_mandi', 'FRACTION',
    0.5, 0.8, -150.0,
    'Pipe Types:Waste', 800.0, 40.0, 40.0,
    'EW', 1
);

-- Pipe 4: Shower branch in bilik_mandi to tandas riser (horizontal)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_4', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'bilik_mandi', 'FRACTION',
    0.2, 0.5, -150.0,
    'Pipe Types:Waste', 600.0, 50.0, 50.0,
    'EW', 1
);

-- Pipe 5: Kitchen sink branch to kitchen riser (horizontal NS in common)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_5', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'common', 'FRACTION',
    0.5, 0.9, -150.0,
    'Pipe Types:Waste', 40.0, 40.0, 1200.0,
    'NS', 1
);

-- Pipe 6: Kitchen waste riser (vertical, north wall of common)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_6', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'common', 'FRACTION',
    0.5, 0.95, -150.0,
    'Pipe Types:Waste', 100.0, 3150.0, 100.0,
    'VERTICAL', 1
);

-- Pipe 7: Underground waste main (runs NS under building to MH)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_7', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'common', 'FRACTION',
    0.1, 0.5, -500.0,
    'Pipe Types:PVC', 100.0, 100.0, 5500.0,
    'NS', 1
);

-- Pipe 8: Vent pipe (tandas riser to above roof)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_8', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'tandas', 'FRACTION',
    0.1, 0.5, 3000.0,
    'Pipe Types:PVC', 50.0, 2000.0, 50.0,
    'VERTICAL', 1
);

-- Pipe 9: Cold water supply main (horizontal EW across building)
INSERT INTO ad_element_rule (
    building_type, element_ref, ifc_class, storey, discipline,
    host_type, host_ref, position_rule,
    position_value, position_value_2, height_mm,
    family_ref, width_mm, height_extent_mm, depth_mm,
    orientation, is_active
) VALUES (
    'TB_LKTN', 'IfcFlowSegment_9', 'IfcFlowSegment', 'Ground Floor', 'MEP',
    'ROOM', 'common', 'FRACTION',
    0.5, 0.1, 2500.0,
    'Pipe Types:Cold Water', 3000.0, 25.0, 25.0,
    'EW', 1
);


-- Part 3: Geometry map entries for pipes -- SKIPPED
-- Pipes use parametric box geometry via MeshBinder.bindParametric() fallback.
-- No ad_geometry_map entries needed -- absence of entry triggers parametric path.


-- ==========================================================================
-- Part 4: CONTAINED_IN dependencies for TB-LKTN pipes (matching Duplex pattern)
-- ==========================================================================

INSERT INTO ad_element_dependency (building_type, element_ref, parent_ref, relation, cascade_rule, is_active, building_id)
VALUES
    ('TB_LKTN', 'IfcFlowSegment_1', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_2', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_3', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_4', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_5', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_6', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_7', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_8', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_9', 'IfcSlab_1', 'CONTAINED_IN', 'MOVE', 1, 4);


-- ==========================================================================
-- Part 5: CONNECTS_TO edges for TB-LKTN pipe graph
-- ==========================================================================
-- Graph: fixture -> branch -> riser -> underground -> (MH implicit)
-- These create the MEP system topology for proof P17 (system connectivity).

-- Waste system: fixtures -> branches -> risers -> underground
INSERT INTO ad_element_dependency (building_type, element_ref, parent_ref, relation, cascade_rule, is_active, building_id)
VALUES
    -- WC -> branch pipe 2
    ('TB_LKTN', 'IfcFurnishingElement_5', 'IfcFlowSegment_2', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Basin (bilik_mandi) -> branch pipe 3
    ('TB_LKTN', 'IfcFurnishingElement_2', 'IfcFlowSegment_3', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Shower -> branch pipe 4
    ('TB_LKTN', 'IfcFurnishingElement_4', 'IfcFlowSegment_4', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Kitchen sink -> branch pipe 5
    ('TB_LKTN', 'IfcFurnishingElement_1', 'IfcFlowSegment_5', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Tandas branch pipes -> tandas riser
    ('TB_LKTN', 'IfcFlowSegment_2', 'IfcFlowSegment_1', 'CONNECTS_TO', 'NONE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_3', 'IfcFlowSegment_1', 'CONNECTS_TO', 'NONE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_4', 'IfcFlowSegment_1', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Kitchen branch -> kitchen riser
    ('TB_LKTN', 'IfcFlowSegment_5', 'IfcFlowSegment_6', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Risers -> underground main
    ('TB_LKTN', 'IfcFlowSegment_1', 'IfcFlowSegment_7', 'CONNECTS_TO', 'NONE', 1, 4),
    ('TB_LKTN', 'IfcFlowSegment_6', 'IfcFlowSegment_7', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Vent pipe -> tandas riser
    ('TB_LKTN', 'IfcFlowSegment_8', 'IfcFlowSegment_1', 'CONNECTS_TO', 'NONE', 1, 4),
    -- Supply main -> WC (cold water feed)
    ('TB_LKTN', 'IfcFlowSegment_9', 'IfcFurnishingElement_5', 'CONNECTS_TO', 'NONE', 1, 4);


-- ==========================================================================
-- Part 6: Verify counts
-- ==========================================================================
-- Expected: 60 - 1 (FE_3) + 9 (pipes) = 68 active rules for TB-LKTN
-- SELECT COUNT(*) FROM ad_element_rule WHERE building_type='TB_LKTN' AND is_active=1;
