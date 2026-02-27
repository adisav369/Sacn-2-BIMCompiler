-- migration_phase_F_order_rename.sql
-- Phase F: Rename ad_building_registry → c_order, ad_element_rule → c_orderline
-- iDempiere naming alignment: C_Order + C_OrderLine
-- Geometry impact: ZERO (pure rename)

-- Step 1: Rename core tables
ALTER TABLE ad_building_registry RENAME TO c_order;
ALTER TABLE ad_element_rule RENAME TO c_orderline;

-- Step 2: Recreate view against new table name
DROP VIEW IF EXISTS v_compilable_element_rule;
CREATE VIEW v_compilable_element_rule AS
SELECT
    er.id,
    er.building_type,
    er.storey,
    er.element_ref,
    er.ifc_class,
    er.discipline,
    er.host_type,
    er.host_ref,
    er.position_rule,
    er.position_value,
    er.position_value_2,
    er.position_value_3,
    er.height_mm,
    er.height_extent_mm,
    er.family_ref,
    er.width_mm,
    er.depth_mm,
    er.orientation,
    er.geometry_hash,
    er.material_name,
    er.material_rgba
FROM c_orderline er
WHERE er.is_active = 1;
