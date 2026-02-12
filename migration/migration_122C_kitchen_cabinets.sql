-- Phase 122C: Kitchen cabinet run + bathroom vanity for Duplex convergence
-- Target: +14 Base_Cabinet, +6 Upper_Cabinet, +4 Vanity_Cabinet = +24 X-ray matches

-- 1. Add dx/dy offsets to existing BASE_CABINET to trigger residential-style placement
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT 81, key, val FROM (
    SELECT 'dx' as key, '0.0' as val UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '0.0'
);

-- 2. Add 6 more BASE_CABINET roles (2-7) with 1m spacing along wall
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_2', 5, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_3', 6, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_4', 7, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_5', 8, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_6', 9, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'BASE_CABINET_7', 10, 1);

-- Add params for each new base cabinet
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_2'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '1.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_3'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '2.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_4'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '3.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_5'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '-1.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_6'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '-2.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'BASE_CABINET_7'
) c, (
    SELECT 'name_pattern' as key, 'Base_Cabinet' as val UNION ALL
    SELECT 'dx', '-3.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

-- 3. Add dx offsets to existing UPPER_CABINET (bom_child_id=82)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT 82, key, val FROM (
    SELECT 'dx' as key, '0.0' as val UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '1.0'
);

-- 4. Add 2 more UPPER_CABINET roles
INSERT OR IGNORE INTO ad_bom_child (bom_id, child_ifc_class, role, sequence, is_active)
VALUES
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'UPPER_CABINET_2', 11, 1),
    ('KITCHEN_CABINET_SET', 'IfcFurniture', 'UPPER_CABINET_3', 12, 1);

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_2'
) c, (
    SELECT 'name_pattern' as key, 'Upper_Cabinet' as val UNION ALL
    SELECT 'dx', '1.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '1.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'KITCHEN_CABINET_SET' AND role = 'UPPER_CABINET_3'
) c, (
    SELECT 'name_pattern' as key, 'Upper_Cabinet' as val UNION ALL
    SELECT 'dx', '-1.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '1.0'
) p;

-- 5. Add COUNTER and SINK offsets (needed for residential-style path)
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT 83, key, val FROM (
    SELECT 'dx' as key, '0.0' as val UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '0.86'
);
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT 84, key, val FROM (
    SELECT 'dx' as key, '1.0' as val UNION ALL
    SELECT 'dy', '0.0' UNION ALL
    SELECT 'dz', '0.86'
);

-- 6. Ensure BATHROOM_VANITY_SET exists in ad_bom (bom_name is NOT NULL)
INSERT OR REPLACE INTO ad_bom (bom_id, bom_name, group_by, is_active)
VALUES ('BATHROOM_VANITY_SET', 'Bathroom Vanity Set', 'ROOM', 1);

-- 7. Bathroom vanity — add name_pattern to BATHROOM_VANITY_SET
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'BATHROOM_VANITY_SET' AND role = 'VANITY_A'
) c, (
    SELECT 'name_pattern' as key, 'Vanity_Cabinet' as val UNION ALL
    SELECT 'dx', '0.0' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;

INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value)
SELECT bom_child_id, key, val FROM (
    SELECT bom_child_id FROM ad_bom_child WHERE bom_id = 'BATHROOM_VANITY_SET' AND role = 'VANITY_B'
) c, (
    SELECT 'name_pattern' as key, 'Vanity_Cabinet' as val UNION ALL
    SELECT 'dx', '0.8' UNION ALL SELECT 'dy', '0.0' UNION ALL SELECT 'dz', '0.0'
) p;
