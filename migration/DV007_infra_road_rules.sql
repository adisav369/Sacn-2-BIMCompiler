-- DV007: Infrastructure road validation rules — mined from Infra_Road_extracted.db
-- Source: DAGCompiler/lib/input/Infra_Road_extracted.db (53 elements, 5 IfcRoad, 26 IfcRoadPart)
-- See docs/InfrastructureAnalysis.md §2.1 for file inventory.
--
-- Mining queries run against extracted DB on 2026-03-19.
-- Pattern: same as DV006b bridge rules (dimension + ratio + Z-continuity).
-- Target DB: validation.db (AD_Val_Rule + AD_Val_Rule_Param)

-- ── Layer thickness rules (1001-1004) ─────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1001, 'ROAD_SURFACE_COURSE', 'STR', 'DIMENSION',
 'IfcCourse asphalt surface course: 40mm thickness. Top layer of road pavement stack.',
 'Infra_Road', 1),

(1002, 'ROAD_BINDER_COURSE', 'STR', 'DIMENSION',
 'IfcCourse asphalt binder course: 80mm thickness. Second layer from top.',
 'Infra_Road', 1),

(1003, 'ROAD_BASE_COURSE', 'STR', 'DIMENSION',
 'IfcEarthworksFill base course: 120mm thickness. Structural layer below asphalt.',
 'Infra_Road', 1),

(1004, 'ROAD_SUBGRADE', 'STR', 'DIMENSION',
 'IfcEarthworksFill subgrade: 250mm thickness. Bottom foundation layer.',
 'Infra_Road', 1);

-- ── Road segment footprint rules (1005-1007) ─────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1005, 'ROAD_CARRIAGEWAY_FOOTPRINT', 'STR', 'DIMENSION',
 'IfcCourse in main carriageway: 19121x13118mm footprint (2 identical segments, 4 layers each).',
 'Infra_Road', 1),

(1006, 'ROAD_PARKING_FOOTPRINT', 'STR', 'DIMENSION',
 'IfcCourse in parking segment: 9526x5804mm footprint (4 layers each).',
 'Infra_Road', 1),

(1007, 'ROAD_BRIDGE_SEGMENT_FOOTPRINT', 'STR', 'DIMENSION',
 'IfcCourse in bridge road segment: 4068x4427mm footprint (4 layers each).',
 'Infra_Road', 1);

-- ── Layer stacking rules (1008-1009) ──────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1008, 'ROAD_TOTAL_PAVEMENT_DEPTH', 'STR', 'DIMENSION',
 'Total pavement stack depth: 490mm (subgrade 250 + base 120 + binder 80 + surface 40).',
 'Infra_Road', 1),

(1009, 'ROAD_LAYER_Z_CONTINUITY', 'STR', 'Z_CONTINUITY',
 'Road layers must stack Z-continuously: subgrade.z_max = base.z_min, etc. Observed: exact match.',
 'Infra_Road', 1);

-- ── Surface feature rules (1010) ──────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1010, 'ROAD_LINE_MARKING', 'SIGN', 'DIMENSION',
 'IfcSurfaceFeature line marking: avg 1782x1087mm, zero height (painted). 20 instances.',
 'Infra_Road', 1);

-- ── Rule parameters ───────────────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule_Param (ad_val_rule_param_id, ad_val_rule_id, name, value, value_type)
VALUES
-- Layer thicknesses
(10011, 1001, 'thickness_mm', '40', 'NUM'),
(10012, 1001, 'material', 'asphalt', 'TEXT'),
(10013, 1001, 'layer_position', '1', 'NUM'),

(10021, 1002, 'thickness_mm', '80', 'NUM'),
(10022, 1002, 'material', 'asphalt', 'TEXT'),
(10023, 1002, 'layer_position', '2', 'NUM'),

(10031, 1003, 'thickness_mm', '120', 'NUM'),
(10032, 1003, 'material', 'aggregate', 'TEXT'),
(10033, 1003, 'layer_position', '3', 'NUM'),

(10041, 1004, 'thickness_mm', '250', 'NUM'),
(10042, 1004, 'material', 'earthworks', 'TEXT'),
(10043, 1004, 'layer_position', '4', 'NUM'),

-- Carriageway footprint
(10051, 1005, 'width_mm', '19121', 'NUM'),
(10052, 1005, 'depth_mm', '13118', 'NUM'),
(10053, 1005, 'segment_count', '2', 'NUM'),

-- Parking footprint
(10061, 1006, 'width_mm', '9526', 'NUM'),
(10062, 1006, 'depth_mm', '5804', 'NUM'),

-- Bridge road footprint
(10071, 1007, 'width_mm', '4068', 'NUM'),
(10072, 1007, 'depth_mm', '4427', 'NUM'),

-- Total pavement
(10081, 1008, 'total_depth_mm', '490', 'NUM'),
(10082, 1008, 'layer_count', '4', 'NUM'),

-- Z-continuity
(10091, 1009, 'max_gap_mm', '0', 'NUM'),
(10092, 1009, 'z_top_mm', '0', 'NUM'),
(10093, 1009, 'z_bottom_mm', '-490', 'NUM'),

-- Line markings
(10101, 1010, 'avg_width_mm', '1782', 'NUM'),
(10102, 1010, 'avg_depth_mm', '1087', 'NUM'),
(10103, 1010, 'height_mm', '0', 'NUM'),
(10104, 1010, 'instance_count', '20', 'NUM');
