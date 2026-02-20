-- Migration: Metadata Integrity — fix dangling references discovered by audit
-- Date: 2026-02-20
-- Context: Layer 2 enforcement — referential integrity for ad_* tables

-- FIX 1: Add 'NONE' wall type sentinel
-- TB_LKTN walls without typed face reference 'NONE' which didn't exist
INSERT OR IGNORE INTO ad_wall_type (wall_type_id, category, construction, total_mm, description, is_active)
VALUES ('NONE', 'INTERIOR', 'NONE', 1, 'Sentinel: no wall type assigned', 1);

-- FIX 2: Add generic room types missing from ad_space_type
-- SH/DX generic rooms use 'ROOM', TB_LKTN toilet uses 'TOILET', stair landing uses 'LANDING'
INSERT OR IGNORE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule, is_active)
VALUES ('ROOM', 'HABITABLE', '13-21 00 00', 'ENCLOSED', 1);
INSERT OR IGNORE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule, is_active)
VALUES ('TOILET', 'SERVICE', '13-25 17 00', 'ENCLOSED', 1);
INSERT OR IGNORE INTO ad_space_type (space_type_id, category, omniclass_code, wall_rule, is_active)
VALUES ('LANDING', 'CIRCULATION', '13-23 11 00', 'NONE', 1);

-- FIX 3: Add 'compartment' check applicability
-- ad_check_threshold references 'compartment' which didn't exist in ad_check_applicability
INSERT OR IGNORE INTO ad_check_applicability (check_id, check_name, check_class, category, is_active)
VALUES ('compartment', 'Fire Compartment Check', 'CompartmentCheck', 'EGRESS', 1);

-- FIX 4: Mark legacy building_type rows inactive in ad_element_placement
-- 16,244 rows reference DUPLEX/SAMPLE_HOUSE/TERMINAL — legacy names before registry
-- PlacementAD/RelationalResolver filter by building_type directly, not is_active — safe
UPDATE ad_element_placement SET is_active = 0
WHERE building_type IN ('DUPLEX', 'SAMPLE_HOUSE', 'TERMINAL') AND is_active = 1;
