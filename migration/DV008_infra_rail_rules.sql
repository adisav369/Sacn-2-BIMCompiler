-- DV008: Infrastructure rail validation rules — mined from Infra_Rail_extracted.db
-- Source: DAGCompiler/lib/input/Infra_Rail_extracted.db (73 elements, 2 IfcRailway, 2 IfcRailwayPart)
-- See docs/InfrastructureAnalysis.md §2.1 for file inventory.
--
-- Mining queries run against extracted DB on 2026-03-19.
-- Key finding: sleeper spacing is perfectly uniform at 606mm — ideal TILE verb candidate.
-- Target DB: validation.db (AD_Val_Rule + AD_Val_Rule_Param)

-- ── Track component dimension rules (1101-1103) ──────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1101, 'RAIL_SLEEPER_DIM', 'STR', 'DIMENSION',
 'IfcTrackElement sleeper wood: 1517x2377x150mm (66 instances). Concrete alternative possible.',
 'Infra_Rail', 1),

(1102, 'RAIL_RAIL_DIM', 'STR', 'DIMENSION',
 'IfcRail: 17396x10130x172mm AABB (4 rails, 2 tracks × 2 rails). Long continuous element.',
 'Infra_Rail', 1),

(1103, 'RAIL_BALLAST_DIM', 'STR', 'DIMENSION',
 'IfcCourse ballastbed: 19071x13031x246mm (2 instances). Foundation for track bed.',
 'Infra_Rail', 1);

-- ── Spacing rules (1104-1105) ─────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1104, 'RAIL_SLEEPER_SPACING', 'STR', 'SPACING',
 'IfcTrackElement NN spacing: 606mm uniform (all 66 sleepers). Perfect TILE verb pattern.',
 'Infra_Rail', 1),

(1105, 'RAIL_GAUGE', 'STR', 'SPACING',
 'Rail-to-rail gauge distance: 1500mm (standard gauge 1435mm + rail head width).',
 'Infra_Rail', 1);

-- ── Z-stacking rules (1106-1107) ─────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(1106, 'RAIL_Z_STACKING', 'STR', 'Z_CONTINUITY',
 'Rail track Z-stacking: ballast 7375-7621 → sleepers 7450-7600 (embedded) → rails 7603-7775.',
 'Infra_Rail', 1),

(1107, 'RAIL_SLEEPER_EMBED', 'STR', 'Z_RANGE',
 'Sleepers embedded in ballast: sleeper z_min(7450) > ballast z_min(7375), sleeper z_max(7600) < ballast z_max(7621).',
 'Infra_Rail', 1);

-- ── Rule parameters ───────────────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule_Param (ad_val_rule_param_id, ad_val_rule_id, name, value, value_type)
VALUES
-- Sleeper dimensions
(11011, 1101, 'width_mm', '1517', 'NUM'),
(11012, 1101, 'depth_mm', '2377', 'NUM'),
(11013, 1101, 'height_mm', '150', 'NUM'),
(11014, 1101, 'instance_count', '66', 'NUM'),

-- Rail dimensions (AABB of continuous rail)
(11021, 1102, 'width_mm', '17396', 'NUM'),
(11022, 1102, 'depth_mm', '10130', 'NUM'),
(11023, 1102, 'height_mm', '172', 'NUM'),
(11024, 1102, 'rail_count', '4', 'NUM'),

-- Ballast dimensions
(11031, 1103, 'width_mm', '19071', 'NUM'),
(11032, 1103, 'depth_mm', '13031', 'NUM'),
(11033, 1103, 'height_mm', '246', 'NUM'),
(11034, 1103, 'instance_count', '2', 'NUM'),

-- Sleeper spacing
(11041, 1104, 'spacing_mm', '606', 'NUM'),
(11042, 1104, 'min_spacing_mm', '606', 'NUM'),
(11043, 1104, 'max_spacing_mm', '606', 'NUM'),
(11044, 1104, 'variance_mm', '0', 'NUM'),

-- Rail gauge
(11051, 1105, 'gauge_mm', '1500', 'NUM'),
(11052, 1105, 'standard_gauge_mm', '1435', 'NUM'),

-- Z-stacking
(11061, 1106, 'ballast_z_min_mm', '7375', 'NUM'),
(11062, 1106, 'ballast_z_max_mm', '7621', 'NUM'),
(11063, 1106, 'sleeper_z_min_mm', '7450', 'NUM'),
(11064, 1106, 'sleeper_z_max_mm', '7600', 'NUM'),
(11065, 1106, 'rail_z_min_mm', '7603', 'NUM'),
(11066, 1106, 'rail_z_max_mm', '7775', 'NUM'),

-- Sleeper embed
(11071, 1107, 'embed_depth_mm', '75', 'NUM'),
(11072, 1107, 'embed_clearance_mm', '21', 'NUM');
