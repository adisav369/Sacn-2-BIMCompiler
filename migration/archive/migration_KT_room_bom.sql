-- migration_KT_room_bom.sql
-- KT Room BOM Layer: create KITCHEN_PREFAB_MY room BOM, deactivate KA/KB categories
--
-- Gap: M_BomCategoryLine has GF → KT at seq=40, but no ROOM-level Kitchen BOM exists.
-- Other rooms (LI, BD, BT) have bom_type='ROOM' BOMs wrapping their content SETs.
-- Kitchen goes directly FLOOR → KITCHEN_CABINET_SET (SET level), skipping ROOM intermediate.
--
-- Fix: create KITCHEN_PREFAB_MY (ROOM) wrapping wall + cabinet set + light.
-- Retire KA/KB categories — single KT per style, PR→HU handles duplex mirroring.

-- =============================================================================
-- PART 1: KITCHEN_PREFAB_MY room BOM
-- =============================================================================

INSERT OR IGNORE INTO m_bom(
    bom_id, bom_name, description,
    bom_type, group_by, bom_level,
    bom_category, doc_sub_type, entity_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm,
    origin_x, origin_y, origin_z,
    is_active
) VALUES (
    'KITCHEN_PREFAB_MY',
    'Kitchen — MY Terrace',
    'Exterior window wall + cabinet set + ceiling light. 3.6m wide cabinet run, 3.3m deep work triangle, 2.7m ceiling.',
    'ROOM', 'ROOM', 'ROOM',
    'KT', 'MY', 'D',
    3600, 3300, 2700,
    0.0, 0.0, -0.3,
    1
);

-- Children (following LIVING_PREFAB_MY / BEDROOM_PREFAB_MY_3100 pattern)

-- seq 10: exterior wall with window
INSERT OR IGNORE INTO m_bom_line(
    bom_id, child_product_id, component_type,
    role, sequence,
    rotation_rule, fit_priority, min_space_mm, is_active
) VALUES (
    'KITCHEN_PREFAB_MY', 'WALL_EXT_MY_150_WIN_STD', 'MAKE',
    'WALL_EXT', 10,
    'FACE_OUTSIDE', 10, 0, 1
);

-- seq 20: kitchen cabinet set (the existing 7100×600×900 cabinet set)
INSERT OR IGNORE INTO m_bom_line(
    bom_id, child_product_id, component_type,
    role, sequence,
    rotation_rule, fit_priority, min_space_mm, is_active
) VALUES (
    'KITCHEN_PREFAB_MY', 'KITCHEN_CABINET_SET', 'MAKE',
    'FURNITURE', 20,
    'FACE_AWAY_FROM_WALL', 20, 600, 1
);

-- seq 30: ceiling light fixture
INSERT OR IGNORE INTO m_bom_line(
    bom_id, child_product_id, component_type,
    role, sequence,
    rotation_rule, dx, dy, dz,
    fit_priority, is_active
) VALUES (
    'KITCHEN_PREFAB_MY', 'ELEC_LIGHT', 'BUY',
    'LIGHT', 30,
    '0', 1.8, 1.65, -0.3,
    30, 1
);

-- =============================================================================
-- PART 2: Deactivate KA/KB categories
-- =============================================================================
-- KA/KB retire → single KT per style, PR→HU handles duplex mirroring.
-- DX kitchen variants (KITCHEN_CABINET_SET_DX_A/B) stay active as SET BOMs
-- but are no longer category-level entries.

UPDATE M_BomCategory SET IsActive = 0 WHERE M_BomCategory_ID = 'KA';
UPDATE M_BomCategory SET IsActive = 0 WHERE M_BomCategory_ID = 'KB';
