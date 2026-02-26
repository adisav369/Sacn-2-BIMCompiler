-- Phase 122B: Rosetta Stone convergence — doors, furniture, multi-slot dispatch
-- Target: SampleHouse 28% → 53%, Duplex 40% → 42%

-- 1. Interior adjacency door defaults for UK_Residential
-- CORRIDOR and LIVING ENTRY doors are exterior (front/patio doors).
-- Room-to-room adjacency connections need interior doors (810mm single).
INSERT OR IGNORE INTO ad_space_type_opening
    (space_type_id, opening_role, family_id, profile, placement_wall, priority)
VALUES
    ('CORRIDOR', 'ADJACENT', 'UK_INT_DOOR_810', 'UK_Residential', 'interior', 50),
    ('LIVING', 'ADJACENT', 'UK_INT_DOOR_810', 'UK_Residential', 'interior', 50);

-- 2. Fix Dining_Table name_pattern to match SampleHouse reference (2000x1000x750)
-- Was Dining_Table (1800x900x750), now Dining_Table_With_Chairs (2000x1000x750)
UPDATE ad_bom_child_param SET param_value = 'Dining_Table_With_Chairs'
WHERE bom_child_id = (
    SELECT bom_child_id FROM ad_bom_child
    WHERE bom_id = 'DINING_SET' AND role = 'TABLE'
) AND param_key = 'name_pattern';

-- 3. Add DESK to BEDROOM BOM (Desk 1563x819x762 = exact SampleHouse ref match)
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('BED_SET', 'IfcFurniture', 'DESK', 5, 1);

-- Add params for DESK placement
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'BED_SET' AND role = 'DESK'
) c, (
    SELECT 'name_pattern' as key, 'Desk' as val UNION ALL
    SELECT 'dx', '-1.5' UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '0.0' UNION ALL
    SELECT 'wall_rule', 'OPPOSITE_WORK'
) p;

-- 4. Add PIANO to LIVING_SET BOM (Piano 1372x600x1170 = exact SampleHouse ref match)
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES ('LIVING_SET', 'IfcFurniture', 'PIANO', 8, 1);

-- Add params for PIANO placement
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'LIVING_SET' AND role = 'PIANO'
) c, (
    SELECT 'name_pattern' as key, 'Piano' as val UNION ALL
    SELECT 'dx', '2.0' UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '0.0'
) p;
