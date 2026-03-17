-- V002_validation_seed.sql
-- Seed AD_Val_Rule from:
--   1. UBBL 2012 residential (DocValidate §9.2)
--   2. Multi-jurisdiction (DocValidate §11.3)
--   3. Mined spatial patterns from TE + DX (DocValidate §7)
--   4. Cross-discipline clash rules
-- APPEND-ONLY: never modify this migration after first run

-- ============================================================
-- OCCUPANCY CLASSES
-- ============================================================
INSERT INTO AD_Occupancy_Class VALUES (1, 'LH',  'Light Hazard Occupancy',    'NFPA 13 §5.2', 'Offices, hotels, residential, airports');
INSERT INTO AD_Occupancy_Class VALUES (2, 'OH1', 'Ordinary Hazard Group 1',   'NFPA 13 §5.3', 'Parking garages, laundries, restaurants');
INSERT INTO AD_Occupancy_Class VALUES (3, 'OH2', 'Ordinary Hazard Group 2',   'NFPA 13 §5.3', 'Warehouses, manufacturing');
INSERT INTO AD_Occupancy_Class VALUES (4, 'RES', 'Residential',              'UBBL 2012',     'Houses, apartments, condominiums');
INSERT INTO AD_Occupancy_Class VALUES (5, 'COM', 'Commercial',              'UBBL 2012',     'Shops, offices, malls');
INSERT INTO AD_Occupancy_Class VALUES (6, 'APT', 'Airport Terminal',         'SS CP 52',      'Passenger terminals, concourses');

-- ============================================================
-- SECTION A: UBBL 2012 — Malaysian residential (DocValidate §9.2)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (101, 'UBBL_BEDROOM_MIN_AREA',      'Minimum bedroom floor area',       'COMPLIANCE', 'ARC', 'UBBL 2012 s33(1)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (102, 'UBBL_BEDROOM_MIN_DIM',       'Minimum bedroom dimension',        'COMPLIANCE', 'ARC', 'UBBL 2012 s33(1)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (103, 'UBBL_KITCHEN_MIN_AREA',      'Minimum kitchen floor area',       'COMPLIANCE', 'ARC', 'UBBL 2012 s33(2)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (104, 'UBBL_KITCHEN_MIN_DIM',       'Minimum kitchen dimension',        'COMPLIANCE', 'ARC', 'UBBL 2012 s33(2)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (105, 'UBBL_BATHROOM_MIN_AREA',     'Minimum bathroom floor area',      'COMPLIANCE', 'ARC', 'UBBL 2012 s33(3)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (106, 'UBBL_LIVING_MIN_AREA',       'Minimum living room floor area',   'COMPLIANCE', 'ARC', 'UBBL 2012 s33(4)', 'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (107, 'UBBL_CEILING_MIN_HEIGHT',    'Minimum ceiling height',           'COMPLIANCE', 'ARC', 'UBBL 2012 s36',    'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (108, 'UBBL_CORRIDOR_MIN_WIDTH',    'Minimum corridor width',           'COMPLIANCE', 'ARC', 'UBBL 2012 s40',    'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (109, 'UBBL_DOOR_MIN_WIDTH',        'Minimum door clear opening',       'COMPLIANCE', 'ARC', 'UBBL 2012 s41',    'MY', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (110, 'UBBL_WINDOW_MIN_AREA_RATIO', 'Minimum window-to-floor area',     'COMPLIANCE', 'ARC', 'UBBL 2012 s39',    'MY', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (1011, 101, 'min_area_m2',     '9.2',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1012, 101, 'bom_category',    'BEDROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1021, 102, 'min_dim_mm',      '3000', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1022, 102, 'bom_category',    'BEDROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1031, 103, 'min_area_m2',     '4.5',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1032, 103, 'bom_category',    'KITCHEN', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1041, 104, 'min_dim_mm',      '1500', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1042, 104, 'bom_category',    'KITCHEN', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1051, 105, 'min_area_m2',     '1.5',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1052, 105, 'bom_category',    'BATHROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1061, 106, 'min_area_m2',     '12.0', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1062, 106, 'bom_category',    'LIVING', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1071, 107, 'min_height_mm',   '2600', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1081, 108, 'min_width_mm',    '900',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1082, 108, 'bom_category',    'CORRIDOR', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1091, 109, 'min_width_mm',    '750',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (1101, 110, 'min_ratio',       '0.10', 'NUM', NULL);

-- UBBL → Residential occupancy
INSERT INTO AD_Val_Rule_Occupancy VALUES (101, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (102, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (103, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (104, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (105, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (106, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (107, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (108, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (109, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (110, 4);

-- ============================================================
-- SECTION B: US IRC 2021 (DocValidate §11.3)
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (201, 'IRC_HABITABLE_MIN_AREA',  'Minimum habitable room area',       'COMPLIANCE', 'ARC', 'IRC 2021 R304.1', 'US', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (202, 'IRC_HABITABLE_MIN_DIM',   'Minimum habitable room dimension',  'COMPLIANCE', 'ARC', 'IRC 2021 R304.2', 'US', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (203, 'IRC_CEILING_MIN_HEIGHT',  'Minimum habitable ceiling height',  'COMPLIANCE', 'ARC', 'IRC 2021 R305.1', 'US', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (204, 'IRC_BATH_CEILING_HEIGHT', 'Minimum bathroom ceiling height',   'COMPLIANCE', 'ARC', 'IRC 2021 R305.1', 'US', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (205, 'IRC_DOOR_MIN_WIDTH',      'Minimum door clear opening',        'COMPLIANCE', 'ARC', 'IRC 2021 R311.2', 'US', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (2011, 201, 'min_area_m2',  '6.5',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (2021, 202, 'min_dim_mm',   '2134', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (2031, 203, 'min_height_mm','2134', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (2041, 204, 'min_height_mm','2032', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (2042, 204, 'bom_category', 'BATHROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (2051, 205, 'min_width_mm', '813',  'NUM', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (201, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (202, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (203, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (204, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (205, 4);

-- ============================================================
-- SECTION C: UK NDSS 2015 / Building Regulations
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (301, 'UK_SINGLE_BED_MIN_AREA', 'Single bedroom minimum area',  'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (302, 'UK_DOUBLE_BED_MIN_AREA', 'Double bedroom minimum area',  'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (303, 'UK_SINGLE_BED_MIN_DIM',  'Single bedroom minimum dim',   'COMPLIANCE', 'ARC', 'NDSS 2015', 'UK', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (304, 'UK_CEILING_MIN_HEIGHT',  'Minimum ceiling height',       'COMPLIANCE', 'ARC', 'UK Regs',   'UK', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (3011, 301, 'min_area_m2',  '7.5',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3012, 301, 'bom_category', 'BEDROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3013, 301, 'bed_type',     'SINGLE', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3021, 302, 'min_area_m2',  '11.5', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3022, 302, 'bom_category', 'BEDROOM', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3023, 302, 'bed_type',     'DOUBLE', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3031, 303, 'min_dim_mm',   '2150', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (3041, 304, 'min_height_mm','2300', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (301, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (302, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (303, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (304, 4);

-- ============================================================
-- SECTION D: Australia NCC 2022
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (401, 'AU_CEILING_HABITABLE',   'Habitable room ceiling height',  'COMPLIANCE', 'ARC', 'NCC 2022 F5/10.3', 'AU', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (402, 'AU_CEILING_SERVICE',     'Service room ceiling height',    'COMPLIANCE', 'ARC', 'NCC 2022 F5/10.3', 'AU', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (403, 'AU_DOOR_MIN_WIDTH',      'Minimum door clear opening',     'COMPLIANCE', 'ARC', 'NCC 2022',         'AU', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (404, 'AU_CORRIDOR_MIN_WIDTH',  'Minimum corridor width',         'COMPLIANCE', 'ARC', 'NCC 2022',         'AU', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (4011, 401, 'min_height_mm','2400', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (4021, 402, 'min_height_mm','2100', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (4022, 402, 'bom_category', 'BATHROOM,KITCHEN,LAUNDRY,CORRIDOR', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (4031, 403, 'min_width_mm', '820',  'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (4041, 404, 'min_width_mm', '1000', 'NUM', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (401, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (402, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (403, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (404, 4);

-- ============================================================
-- SECTION E: Singapore BCA
-- ============================================================
INSERT INTO AD_Val_Rule VALUES (501, 'SG_CEILING_MIN_HEIGHT',  'Minimum ceiling height',  'COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (502, 'SG_CORRIDOR_MIN_WIDTH',  'Minimum corridor width',  'COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 'RESEARCHED', NULL, NULL, 1);
INSERT INTO AD_Val_Rule VALUES (503, 'SG_DOOR_MIN_WIDTH',      'Minimum door clear width','COMPLIANCE', 'ARC', 'BCA Approved Document', 'SG', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (5011, 501, 'min_height_mm','2400', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (5021, 502, 'min_width_mm', '1200', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (5031, 503, 'min_width_mm', '850',  'NUM', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (501, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (502, 4);
INSERT INTO AD_Val_Rule_Occupancy VALUES (503, 4);

-- ============================================================
-- SECTION F: MINED rules from SJTII_Terminal (48,428 elements)
-- Sprinkler spacing, cross-discipline clearances
-- Mined 2026-03-18. Coordinates in meters, converted to mm.
-- ============================================================

-- F1: Sprinkler head nearest-neighbor spacing
-- Mined: 909 IfcFireSuppressionTerminal heads, NN distribution:
--   p5=600mm, p25=1338mm, p50=1899mm, p75=3000mm, p95=3202mm
-- Observed max grid spacing ~3500mm (well under NFPA 13 LH max 4600mm)
-- Rule: warn if NN > 3500mm (observed p95 envelope)
INSERT INTO AD_Val_Rule VALUES (601, 'MINED_FPR_NN_SPACING',
    'Sprinkler head nearest-neighbor max spacing (mined from TE)',
    'COMPLIANCE', 'FPR', 'NFPA 13 §8.6 (observed)', 'INTL',
    'MINED:SJTII_Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (6011, 601, 'max_nn_spacing_mm', '4600', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (6012, 601, 'ifc_class', 'IfcFireSuppressionTerminal', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (601, 1);  -- Light Hazard
INSERT INTO AD_Val_Rule_Occupancy VALUES (601, 6);  -- Airport Terminal

INSERT INTO AD_Val_Rule_Mining_Source VALUES (601, 'SJTII_Terminal', 891, 79, 3500, 1899, 3202, 'mm',
    'NN centroid distance between IfcFireSuppressionTerminal on same storey, excluding co-located (<10mm)', datetime('now'));

-- F2: NFPA 13 code maximum (for comparison / generative enforcement)
INSERT INTO AD_Val_Rule VALUES (602, 'NFPA13_LH_MAX_SPACING',
    'NFPA 13 Light Hazard maximum sprinkler spacing',
    'COMPLIANCE', 'FPR', 'NFPA 13 §8.6.2.2.1', 'US',
    'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (6021, 602, 'max_spacing_mm', '4600', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (6022, 602, 'ifc_class', 'IfcFireSuppressionTerminal', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Occupancy VALUES (602, 1);

-- F3: ELEC-SP minimum clearance (mined from TE Ground Floor)
-- Mined: 402 ELEC × 169 SP on Ground Floor
--   Centroid NN: min observed 0mm (co-routed), but p5 ~100mm
--   Real-world code: NEC 300.4 requires 150mm separation
-- Rule uses code value, not mined (mined shows some violations — real building)
INSERT INTO AD_Val_Rule VALUES (603, 'MINED_ELEC_SP_CLEARANCE',
    'Electrical-to-plumbing clearance (mined from TE, code from NEC)',
    'CLEARANCE', NULL, 'NEC 300.4', 'INTL',
    'MINED:SJTII_Terminal', NULL, NULL, 1);

INSERT INTO AD_Val_Rule_Param VALUES (6031, 603, 'min_clearance_mm', '150', 'NUM', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (6032, 603, 'discipline_a', 'ELEC', 'TEXT', NULL);
INSERT INTO AD_Val_Rule_Param VALUES (6033, 603, 'discipline_b', 'SP', 'TEXT', NULL);

INSERT INTO AD_Val_Rule_Mining_Source VALUES (603, 'SJTII_Terminal', 571, 0, 2000, 900, 1700, 'mm',
    'Centroid-to-centroid distance ELEC vs SP on Ground Floor', datetime('now'));

-- ============================================================
-- SECTION G: Cross-discipline clash rules (from DocValidate §4.1)
-- ============================================================

-- G1: MEP through structural element = hard clash
INSERT INTO AD_Val_Rule VALUES (701, 'CLASH_MEP_STR_HARD',
    'MEP element intersecting structural member', 'CLASH', NULL,
    'General engineering practice', 'INTL', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Clash_Rule VALUES (1, 'ELEC', 'STR',
    NULL, 'ifc_class IN (''IfcBeam'', ''IfcColumn'')',
    'HARD', NULL, 'BLOCK',
    'Reroute conduit or coordinate beam penetration with structural engineer', 701);

INSERT INTO AD_Clash_Rule VALUES (2, 'SP', 'STR',
    NULL, 'ifc_class IN (''IfcBeam'', ''IfcColumn'')',
    'HARD', NULL, 'BLOCK',
    'Reroute pipe or add beam sleeve', 701);

INSERT INTO AD_Clash_Rule VALUES (3, 'ACMV', 'STR',
    NULL, 'ifc_class IN (''IfcBeam'', ''IfcColumn'')',
    'HARD', NULL, 'BLOCK',
    'Reroute duct or coordinate beam penetration', 701);

INSERT INTO AD_Clash_Rule VALUES (4, 'FP', 'STR',
    NULL, 'ifc_class IN (''IfcBeam'', ''IfcColumn'')',
    'HARD', NULL, 'BLOCK',
    'Reroute fire protection pipe', 701);

-- G2: PLB pipe near ELC conduit = clearance
INSERT INTO AD_Val_Rule VALUES (702, 'CLASH_PLB_ELC_CLEARANCE',
    'Plumbing-electrical minimum separation', 'CLASH', NULL,
    'NEC 300.4', 'INTL', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Clash_Rule VALUES (5, 'SP', 'ELEC',
    NULL, NULL,
    'CLEARANCE', 150, 'WARN',
    'Maintain 150mm separation per NEC 300.4', 702);

-- G3: FPR sprinkler head near ACMV duct = clearance (obstruction rule)
INSERT INTO AD_Val_Rule VALUES (703, 'CLASH_FPR_ACMV_OBSTRUCTION',
    'Sprinkler head obstructed by ductwork', 'CLASH', 'FPR',
    'NFPA 13 §8.5.5', 'INTL', 'RESEARCHED', NULL, NULL, 1);

INSERT INTO AD_Clash_Rule VALUES (6, 'FP', 'ACMV',
    'ifc_class = ''IfcFireSuppressionTerminal''',
    'ifc_class IN (''IfcDuctSegment'', ''IfcDuctFitting'')',
    'CLEARANCE', 300, 'BLOCK',
    'Reposition head per NFPA 13 obstruction rules (3× obstruction width)', 703);

-- ============================================================
-- SECTION H: DX known exceptions (DocValidate §7.3)
-- ============================================================
INSERT INTO AD_Val_Rule_Exception VALUES (1, 'Ifc2x3_Duplex', 702, 'P23 MEP corner fittings', 358,
    'Original engineer', 'MEP corner clearance violations — fittings placed without connecting pipes', datetime('now'));

-- TE isolated sprinkler heads (NN > NFPA max 4600mm — remote service areas)
INSERT INTO AD_Val_Rule_Exception VALUES (2, 'SJTII_Terminal', 601,
    'placement_id IN (1042729, 1042761)', 2,
    'Original engineer', 'Isolated sprinkler heads in remote service areas — NN > 4600mm acceptable per local authority', datetime('now'));
