-- Phase RM-9: Single authority — rotation_rule in ad_bom_child_param
--
-- Three tables, three concerns, no overlap:
--   ad_product_dim      — intrinsic geometry (width, depth, height, mounting_face)
--   ad_bom_child_param  — assembly-relative offset + rotation (dx, dy, dz, rotation_rule)
--   ad_element_rule     — room-relative placement (wall_face, fraction, offset, orientation)
--
-- rotation_rule values:
--   Literal radians: "0", "3.14159265", "1.5708"
--   Semantic rules:  "FACE_INTO_ROOM", "FACE_AWAY_FROM_WALL", "PARALLEL_TO_WALL"

-- Step 1: Rename 'rotation' → 'rotation_rule' (literal radian values preserved as-is)
UPDATE ad_bom_child_param
SET param_key = 'rotation_rule'
WHERE param_key = 'rotation';

-- Step 2: Rename 'facing' → 'rotation_rule' with semantic constant mapping
UPDATE ad_bom_child_param
SET param_key = 'rotation_rule',
    param_value = CASE param_value
        WHEN 'away_from_wall' THEN 'FACE_AWAY_FROM_WALL'
        WHEN 'into_room' THEN 'FACE_INTO_ROOM'
        ELSE param_value
    END
WHERE param_key = 'facing';

-- Step 3: Add rotation_rule for center fixtures (no rotation/facing before)
-- FLOOR_TRAP (bom_child_id=61): symmetric, 0 is intentional metadata not Java default
INSERT INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, description, is_active)
VALUES (61, 'rotation_rule', '0', 'DOUBLE', 'Symmetric fixture - no orientation', 1);

-- EXHAUST_FAN (bom_child_id=62): symmetric ceiling fixture
INSERT INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, description, is_active)
VALUES (62, 'rotation_rule', '0', 'DOUBLE', 'Symmetric ceiling fixture - no orientation', 1);
