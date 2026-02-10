-- Phase 113: Migrate FRP water tanks from federation DB to component library
-- 3 tanks: 5x6 (rooftop gravity), 4x4 (rooftop gravity), 2x4 (ground pump)
-- Geometry BLOBs copied by scripts/migrate_tank_geometry.py (SQL can't cross-DB blob copy)
-- Idempotent: INSERT OR IGNORE throughout

-- Component type: WATER_TANK under IfcBuildingElementProxy, discipline SP (sanitary/plumbing)
INSERT OR IGNORE INTO component_types (id, ifc_class, category, discipline)
VALUES (21, 'IfcBuildingElementProxy', 'WATER_TANK', 'SP');

-- Component definitions (geometry BLOBs already copied by Python helper)
-- Local bounds computed from federation rtree world coords (size only, origin at 0)

-- Tank A: 5x6 — large rooftop gravity tank
INSERT OR IGNORE INTO component_definitions
    (id, type_id, name, geometry_hash,
     local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
     attachment_face, up_axis, forward_axis, orientation, default_rotation,
     vertex_count, face_count, instance_count)
VALUES
    (17718, 21, 'Water_Tank_FRP_5x6', '91f7a4dc26d9690d',
     0.0, 5.276, 0.0, 6.143, 0.0, 2.893,
     'BOTTOM', 'Z', 'Y', 'UPRIGHT', 0,
     40798, 80660, 1);

-- Tank B: 4x4 — medium rooftop gravity tank
INSERT OR IGNORE INTO component_definitions
    (id, type_id, name, geometry_hash,
     local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
     attachment_face, up_axis, forward_axis, orientation, default_rotation,
     vertex_count, face_count, instance_count)
VALUES
    (17719, 21, 'Water_Tank_FRP_4x4', '40a57b3321618ef3',
     0.0, 4.257, 0.0, 4.613, 0.0, 2.922,
     'BOTTOM', 'Z', 'Y', 'UPRIGHT', 0,
     34450, 68060, 1);

-- Tank C: 2x4 — small ground pump tank
INSERT OR IGNORE INTO component_definitions
    (id, type_id, name, geometry_hash,
     local_min_x, local_max_x, local_min_y, local_max_y, local_min_z, local_max_z,
     attachment_face, up_axis, forward_axis, orientation, default_rotation,
     vertex_count, face_count, instance_count)
VALUES
    (17720, 21, 'Water_Tank_FRP_2x4', '191c806d5f90f5f0',
     0.0, 4.257, 0.0, 2.307, 0.0, 2.922,
     'BOTTOM', 'Z', 'Y', 'UPRIGHT', 0,
     16999, 33586, 1);

-- BOM recipe: WATER_TANK_ASSEMBLY
INSERT OR IGNORE INTO ad_bom (bom_id, bom_name, description, target_ifc_class, group_by, is_active)
VALUES ('WATER_TANK_ASSEMBLY', 'Water Tank Assembly',
        'FRP water tank with plumbing connections', 'IfcElementAssembly', 'ELEMENT_NAME', 1);

-- BOM child: vessel role
INSERT OR IGNORE INTO ad_bom_child
    (bom_child_id, bom_id, child_ifc_class, child_name_pattern, role, qty_type, sequence, is_active)
VALUES
    (80, 'WATER_TANK_ASSEMBLY', 'IfcBuildingElementProxy', 'Water_Tank_FRP_%', 'VESSEL', 'FIXED', 10, 1);

-- BOM child params
INSERT OR IGNORE INTO ad_bom_child_param (bom_child_id, param_key, param_value, param_type, description)
VALUES
    (80, 'connects_to', 'PLUMBING_RISER', 'TEXT', 'Connected system'),
    (80, 'supply_type', 'GRAVITY', 'TEXT', 'Default supply type (GRAVITY for roof, PUMP for ground)'),
    (80, 'location_rule', 'ROOF_MECH', 'TEXT', 'Default placement rule (ROOF_MECH or GROUND)');
