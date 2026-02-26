-- Phase A: Duplex unit dimension correction + slab spec entries
-- Reference: Ifc2x3_Duplex_extracted.db
--
-- Unit footprint from reference: 7.966m × 9.308m (extracted from 127mm Slab on Grade)
-- Foundation: 7.966×9.308×0.127 (no extend, exact unit footprint)
-- Upper floor: 7.966×9.308×0.305 (Wood Joist with Subflooring, per-unit)

-- 1. Fix unit dimensions from metadata estimation to reference-extracted values
UPDATE ad_unit_type SET width = 7.966, depth = 9.308 WHERE unit_type_id = 'DUPLEX_GROUND';
UPDATE ad_unit_type SET width = 7.966, depth = 9.308 WHERE unit_type_id = 'DUPLEX_UPPER';

-- 2. Add slab spec entries — extend=0 because reference slabs match unit footprint exactly
INSERT INTO ad_slab_spec (building_type, slab_role, extend_x_m, extend_y_m, thickness_m, slab_name, ceiling_z_m, is_active)
VALUES ('Ifc2x3_Duplex', 'FOUNDATION', 0.0, 0.0, 0.127, '127mm Slab on Grade', NULL, 1);

INSERT INTO ad_slab_spec (building_type, slab_role, extend_x_m, extend_y_m, thickness_m, slab_name, ceiling_z_m, is_active)
VALUES ('Ifc2x3_Duplex', 'FLOOR', 0.0, 0.0, 0.305, 'Residential - Wood Joist with Subflooring', NULL, 1);
