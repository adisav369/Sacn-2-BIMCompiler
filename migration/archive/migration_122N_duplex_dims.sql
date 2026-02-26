-- Phase 122N: Fill unit zone dimensions from reference stone.
-- Building (Level 4) defines its unit types (Level 2) via ad_unit_type.
-- Room dimensions resolved from ad_unit_type_room fractional coords × unit width/depth.
-- Reference: Ifc2x3_Duplex_Architecture.ifc — internal width 8.0m, each unit ~8.5m deep.

UPDATE ad_unit_type SET width = 8.0, depth = 8.5
WHERE unit_type_id = 'DUPLEX_GROUND';

UPDATE ad_unit_type SET width = 8.0, depth = 8.5
WHERE unit_type_id = 'DUPLEX_UPPER';
