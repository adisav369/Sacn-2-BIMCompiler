-- DV006: Infrastructure bridge validation rules — mined from BR Rosetta Stone
-- Source: DAGCompiler/lib/output/infra_bridge_enbloc.db (48 elements, 10/10 PASS)
-- Pattern: same as TE-mined NFPA13 spacing rules (see TE_MINING_RESULTS.md)
-- See docs/InfrastructureAnalysis.md §7.1 for derivation.
--
-- Migration: append-only (CLAUDE.md sacred files rule).
-- Target DB: validation.db (AD_Val_Rule + AD_Val_Rule_Param)
--
-- ══════════════════════════════════════════════════════════════════════
-- TO APPLY (next session):
--   sqlite3 library/validation.db < migration/DV006_infra_bridge_rules.sql
--
-- TO MINE (refresh from latest extraction):
--   Run the mining queries below against infra_bridge_enbloc.db
--   Update the INSERT values if the reference model changes
-- ══════════════════════════════════════════════════════════════════════

-- ── Structural dimension rules ───────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (rule_id, rule_name, discipline, rule_type, description, mining_source, is_active)
VALUES
('BRIDGE_ARCH_MEMBER_DIM', 'Bridge arch member dimensions', 'STR', 'DIMENSION',
 'IfcMember in superstructure: avg 6080x5531x4084mm (8 instances in railbridge superstructure)',
 'Infra_Bridge', 1),

('BRIDGE_PIER_COLUMN_RAIL', 'Rail bridge pier column dimensions', 'STR', 'DIMENSION',
 'IfcColumn in rail pier: 3499x4561x3780mm (4 instances)',
 'Infra_Bridge', 1),

('BRIDGE_PIER_COLUMN_ROAD', 'Road bridge pier column dimensions', 'STR', 'DIMENSION',
 'IfcColumn in road pier: 2276x3393x2286mm (3 instances)',
 'Infra_Bridge', 1),

('BRIDGE_FOOTING_RAIL', 'Rail bridge footing dimensions', 'STR', 'DIMENSION',
 'IfcFooting under rail pier: 6231x7293x1000mm (4 instances)',
 'Infra_Bridge', 1),

('BRIDGE_FOOTING_ROAD', 'Road bridge footing dimensions', 'STR', 'DIMENSION',
 'IfcFooting under road pier: 4319x5380x700mm (3 instances)',
 'Infra_Bridge', 1),

('BRIDGE_DECK_THICKNESS', 'Bridge deck slab thickness', 'STR', 'DIMENSION',
 'IfcSlab in deck segment: 56mm height (thin plate, 1 instance)',
 'Infra_Bridge', 1),

('BRIDGE_APPROACH_SLAB', 'Bridge approach slab thickness', 'STR', 'DIMENSION',
 'IfcSlab in approach segment: 441mm height (2 instances)',
 'Infra_Bridge', 1);

-- ── Ratio rules (cross-element) ─────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (rule_id, rule_name, discipline, rule_type, description, mining_source, is_active)
VALUES
('FOOTING_SPREAD_RATIO', 'Footing-to-column spread ratio', 'STR', 'RATIO',
 'Footing width must exceed column width. Rail: 1.78x, Road: 1.90x. Min observed: 1.78x.',
 'Infra_Bridge', 1),

('FOOTING_DEPTH_RATIO', 'Footing depth-to-column height ratio', 'STR', 'RATIO',
 'Footing height / column height. Rail: 0.26x, Road: 0.31x. Range: 0.26-0.31x.',
 'Infra_Bridge', 1);

-- ── Placement rules ─────────────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (rule_id, rule_name, discipline, rule_type, description, mining_source, is_active)
VALUES
('BRIDGE_RAILING_HEIGHT', 'Bridge railing minimum height', 'ARC', 'MIN_DIMENSION',
 'IfcRailing on deck: H=956mm (2 instances). Safety: bridge railing minimum.',
 'Infra_Bridge', 1),

('BRIDGE_SIGNAGE_COUNT', 'Bridge signage per superstructure', 'SIGN', 'MIN_COUNT',
 'IfcSign: 4 signs per superstructure, 1401x826x400mm. Regulatory identification.',
 'Infra_Bridge', 1);

-- ── Segment Z-continuity rules ──────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (rule_id, rule_name, discipline, rule_type, description, mining_source, is_active)
VALUES
('SEGMENT_Z_PIER_DECK', 'Pier-to-deck vertical continuity', 'STR', 'Z_CONTINUITY',
 'Pier z_max must approximate deck z_min (gap <= 100mm). Observed: 22mm gap.',
 'Infra_Bridge', 1),

('SEGMENT_Z_BELOW_GROUND', 'Pier embeds below ground level', 'STR', 'Z_RANGE',
 'Pier segments must extend below Z=0. Road pier: -3.5m, Rail pier: -1.49m.',
 'Infra_Bridge', 1);

-- ── Rule parameters ─────────────────────────────────────────────────
-- These provide the numeric thresholds for PlacementValidator queries.

INSERT OR IGNORE INTO AD_Val_Rule_Param (rule_id, param_name, param_value, unit, description)
VALUES
-- Arch member
('BRIDGE_ARCH_MEMBER_DIM', 'avg_width_mm', '6080', 'mm', 'Average member width'),
('BRIDGE_ARCH_MEMBER_DIM', 'avg_depth_mm', '5531', 'mm', 'Average member depth'),
('BRIDGE_ARCH_MEMBER_DIM', 'avg_height_mm', '4084', 'mm', 'Average member height'),
('BRIDGE_ARCH_MEMBER_DIM', 'instance_count', '8', 'count', 'Instances in superstructure'),

-- Pier columns
('BRIDGE_PIER_COLUMN_RAIL', 'width_mm', '3499', 'mm', 'Column width'),
('BRIDGE_PIER_COLUMN_RAIL', 'depth_mm', '4561', 'mm', 'Column depth'),
('BRIDGE_PIER_COLUMN_RAIL', 'height_mm', '3780', 'mm', 'Column height'),
('BRIDGE_PIER_COLUMN_ROAD', 'width_mm', '2276', 'mm', 'Column width'),
('BRIDGE_PIER_COLUMN_ROAD', 'depth_mm', '3393', 'mm', 'Column depth'),
('BRIDGE_PIER_COLUMN_ROAD', 'height_mm', '2286', 'mm', 'Column height'),

-- Footings
('BRIDGE_FOOTING_RAIL', 'width_mm', '6231', 'mm', 'Footing width'),
('BRIDGE_FOOTING_RAIL', 'depth_mm', '7293', 'mm', 'Footing depth'),
('BRIDGE_FOOTING_RAIL', 'height_mm', '1000', 'mm', 'Footing height'),
('BRIDGE_FOOTING_ROAD', 'width_mm', '4319', 'mm', 'Footing width'),
('BRIDGE_FOOTING_ROAD', 'depth_mm', '5380', 'mm', 'Footing depth'),
('BRIDGE_FOOTING_ROAD', 'height_mm', '700', 'mm', 'Footing height'),

-- Deck and approach
('BRIDGE_DECK_THICKNESS', 'min_height_mm', '56', 'mm', 'Deck slab thickness'),
('BRIDGE_APPROACH_SLAB', 'height_mm', '441', 'mm', 'Approach slab thickness'),

-- Ratios
('FOOTING_SPREAD_RATIO', 'min_ratio', '1.78', 'ratio', 'Minimum footing/column width ratio'),
('FOOTING_SPREAD_RATIO', 'max_ratio', '1.90', 'ratio', 'Maximum observed ratio'),
('FOOTING_DEPTH_RATIO', 'min_ratio', '0.26', 'ratio', 'Minimum footing height/column height'),
('FOOTING_DEPTH_RATIO', 'max_ratio', '0.31', 'ratio', 'Maximum observed ratio'),

-- Placement
('BRIDGE_RAILING_HEIGHT', 'min_height_mm', '956', 'mm', 'Minimum railing height'),
('BRIDGE_SIGNAGE_COUNT', 'min_count', '4', 'count', 'Minimum signs per superstructure'),
('BRIDGE_SIGNAGE_COUNT', 'sign_width_mm', '1401', 'mm', 'Sign width'),
('BRIDGE_SIGNAGE_COUNT', 'sign_height_mm', '400', 'mm', 'Sign height'),

-- Z-continuity
('SEGMENT_Z_PIER_DECK', 'max_gap_mm', '100', 'mm', 'Maximum pier-deck Z gap'),
('SEGMENT_Z_BELOW_GROUND', 'rail_min_depth_m', '-1.49', 'm', 'Rail pier min Z'),
('SEGMENT_Z_BELOW_GROUND', 'road_min_depth_m', '-3.50', 'm', 'Road pier min Z');
