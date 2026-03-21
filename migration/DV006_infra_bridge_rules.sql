-- DV006b: Infrastructure bridge validation rules — schema-corrected rewrite of DV006
-- DV006 had wrong column names (rule_id, rule_name, mining_source, param_name, etc.)
-- This file uses the actual validation.db schema:
--   AD_Val_Rule: ad_val_rule_id, name, description, rule_type, discipline, provenance, is_active
--   AD_Val_Rule_Param: ad_val_rule_param_id, ad_val_rule_id, name, value, value_type
--
-- Source: DAGCompiler/lib/output/infra_bridge_enbloc.db (48 elements, 10/10 PASS)
-- See docs/InfrastructureAnalysis.md §7.1 for derivation.
-- Migration: append-only (CLAUDE.md sacred files rule).

-- ── Structural dimension rules (901-907) ──────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(901, 'BRIDGE_ARCH_MEMBER_DIM', 'STR', 'DIMENSION',
 'IfcMember in superstructure: avg 6080x5531x4084mm (8 instances in railbridge superstructure)',
 'Infra_Bridge', 1),

(902, 'BRIDGE_PIER_COLUMN_RAIL', 'STR', 'DIMENSION',
 'IfcColumn in rail pier: 3499x4561x3780mm (4 instances)',
 'Infra_Bridge', 1),

(903, 'BRIDGE_PIER_COLUMN_ROAD', 'STR', 'DIMENSION',
 'IfcColumn in road pier: 2276x3393x2286mm (3 instances)',
 'Infra_Bridge', 1),

(904, 'BRIDGE_FOOTING_RAIL', 'STR', 'DIMENSION',
 'IfcFooting under rail pier: 6231x7293x1000mm (4 instances)',
 'Infra_Bridge', 1),

(905, 'BRIDGE_FOOTING_ROAD', 'STR', 'DIMENSION',
 'IfcFooting under road pier: 4319x5380x700mm (3 instances)',
 'Infra_Bridge', 1),

(906, 'BRIDGE_DECK_THICKNESS', 'STR', 'DIMENSION',
 'IfcSlab in deck segment: 56mm height (thin plate, 1 instance)',
 'Infra_Bridge', 1),

(907, 'BRIDGE_APPROACH_SLAB', 'STR', 'DIMENSION',
 'IfcSlab in approach segment: 441mm height (2 instances)',
 'Infra_Bridge', 1);

-- ── Ratio rules (908-909) ─────────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(908, 'FOOTING_SPREAD_RATIO', 'STR', 'RATIO',
 'Footing width must exceed column width. Rail: 1.78x, Road: 1.90x. Min observed: 1.78x.',
 'Infra_Bridge', 1),

(909, 'FOOTING_DEPTH_RATIO', 'STR', 'RATIO',
 'Footing height / column height. Rail: 0.26x, Road: 0.31x. Range: 0.26-0.31x.',
 'Infra_Bridge', 1);

-- ── Placement rules (910-911) ─────────────────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(910, 'BRIDGE_RAILING_HEIGHT', 'ARC', 'MIN_DIMENSION',
 'IfcRailing on deck: H=956mm (2 instances). Safety: bridge railing minimum.',
 'Infra_Bridge', 1),

(911, 'BRIDGE_SIGNAGE_COUNT', 'SIGN', 'MIN_COUNT',
 'IfcSign: 4 signs per superstructure, 1401x826x400mm. Regulatory identification.',
 'Infra_Bridge', 1);

-- ── Segment Z-continuity rules (912-913) ──────────────────────────────

INSERT OR IGNORE INTO AD_Val_Rule (ad_val_rule_id, name, discipline, rule_type, description, provenance, is_active)
VALUES
(912, 'SEGMENT_Z_PIER_DECK', 'STR', 'Z_CONTINUITY',
 'Pier z_max must approximate deck z_min (gap <= 100mm). Observed: 22mm gap.',
 'Infra_Bridge', 1),

(913, 'SEGMENT_Z_BELOW_GROUND', 'STR', 'Z_RANGE',
 'Pier segments must extend below Z=0. Road pier: -3.5m, Rail pier: -1.49m.',
 'Infra_Bridge', 1);

-- ── Rule parameters ───────────────────────────────────────────────────
-- Convention: ad_val_rule_param_id = rule_id * 10 + seq

INSERT OR IGNORE INTO AD_Val_Rule_Param (ad_val_rule_param_id, ad_val_rule_id, name, value, value_type)
VALUES
-- 901: Arch member
(9011, 901, 'avg_width_mm', '6080', 'NUM'),
(9012, 901, 'avg_depth_mm', '5531', 'NUM'),
(9013, 901, 'avg_height_mm', '4084', 'NUM'),
(9014, 901, 'instance_count', '8', 'NUM'),

-- 902: Pier columns (rail)
(9021, 902, 'width_mm', '3499', 'NUM'),
(9022, 902, 'depth_mm', '4561', 'NUM'),
(9023, 902, 'height_mm', '3780', 'NUM'),

-- 903: Pier columns (road)
(9031, 903, 'width_mm', '2276', 'NUM'),
(9032, 903, 'depth_mm', '3393', 'NUM'),
(9033, 903, 'height_mm', '2286', 'NUM'),

-- 904: Footings (rail)
(9041, 904, 'width_mm', '6231', 'NUM'),
(9042, 904, 'depth_mm', '7293', 'NUM'),
(9043, 904, 'height_mm', '1000', 'NUM'),

-- 905: Footings (road)
(9051, 905, 'width_mm', '4319', 'NUM'),
(9052, 905, 'depth_mm', '5380', 'NUM'),
(9053, 905, 'height_mm', '700', 'NUM'),

-- 906: Deck
(9061, 906, 'min_height_mm', '56', 'NUM'),

-- 907: Approach slab
(9071, 907, 'height_mm', '441', 'NUM'),

-- 908: Footing spread ratio
(9081, 908, 'min_ratio', '1.78', 'NUM'),
(9082, 908, 'max_ratio', '1.90', 'NUM'),

-- 909: Footing depth ratio
(9091, 909, 'min_ratio', '0.26', 'NUM'),
(9092, 909, 'max_ratio', '0.31', 'NUM'),

-- 910: Railing height
(9101, 910, 'min_height_mm', '956', 'NUM'),

-- 911: Signage count
(9111, 911, 'min_count', '4', 'NUM'),
(9112, 911, 'sign_width_mm', '1401', 'NUM'),
(9113, 911, 'sign_height_mm', '400', 'NUM'),

-- 912: Z pier-deck continuity
(9121, 912, 'max_gap_mm', '100', 'NUM'),

-- 913: Z below ground
(9131, 913, 'rail_min_depth_m', '-1.49', 'NUM'),
(9132, 913, 'road_min_depth_m', '-3.50', 'NUM');
