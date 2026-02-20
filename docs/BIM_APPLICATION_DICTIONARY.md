# BIM Application Dictionary (BAD)

**Version:** 1.0  
**Date:** 2026-02-20  
**Prerequisite:** Read METADATA_DRIVEN_ARCHITECTURE.md first — this builds on the five-domain model.  
**Purpose:** Establish the rule framework that governs all element placement BEFORE buildings are authored. The AD layer must exist before content enters the system.  
**Principle:** The rules governing element placement are themselves data — not Java logic, not hardcoded conditions in resolvers.

---

## 1. What This Is

Compiere built the entire Application Dictionary — AD_Column, AD_Reference, AD_Val_Rule, AD_Process, AD_Workflow — before a single C_Order existed. The framework governed what data COULD enter before any data DID enter.

BAD does the same for construction. Every placement constraint, clearance rule, discipline relationship, adjacency requirement, and inter-floor dependency is captured as queryable, auditable data with source citations. When buildings are later authored via DSL or GUI, the rules are already there governing every element.

**BAD is a SEPARATE SUBPROJECT from compiler development.** It is pure domain knowledge capture. No Java code changes. No compiler modifications. Just SQL tables populated from construction standards and practice.

---

## 2. Schema

All tables use the `bad_` prefix. BAD is its own domain — it governs all other domains the way AD_* governs all iDempiere tables.

### 2.1 Rule Categories (≈ AD_Reference)

```sql
CREATE TABLE bad_rule_category (
    category_id     TEXT PRIMARY KEY,
    category_name   TEXT NOT NULL,
    description     TEXT NOT NULL,
    seq_no          INTEGER NOT NULL,
    is_active       INTEGER DEFAULT 1
);

INSERT INTO bad_rule_category VALUES
('SPATIAL',       'Spatial Rules',                'How spaces relate to each other — adjacency, stacking, access',                    10, 1),
('PLACEMENT',     'Element Placement Rules',      'How elements sit within spaces — facing, offset, clearance, anchoring',            20, 1),
('STRUCTURAL',    'Structural Rules',             'How structure organises — grid, columns, beams, load paths',                       30, 1),
('MEP',           'MEP Rules',                    'How services coordinate — pipe sizing, riser alignment, head spacing',             40, 1),
('INTER_FLOOR',   'Inter-Floor Rules',            'How storeys relate — vertical alignment, riser continuity, structural consistency', 50, 1),
('COORDINATION',  'Discipline Coordination Rules','How systems avoid conflict — priority, routing, penetration',                      60, 1);
```

### 2.2 Rules (≈ AD_Val_Rule + AD_Column constraints)

```sql
CREATE TABLE bad_rule (
    rule_id         TEXT PRIMARY KEY,
    category_id     TEXT NOT NULL REFERENCES bad_rule_category,
    rule_name       TEXT NOT NULL,

    -- Rule severity
    rule_type       TEXT NOT NULL,
    -- CONSTRAINT  : hard limit, blocks compilation if violated
    -- PREFERENCE  : soft, logged as advisory warning
    -- RELATIONSHIP: defines required adjacency or dependency
    -- COORDINATION: cross-discipline clash prevention

    -- What it applies to (domain + type from METADATA_DRIVEN_ARCHITECTURE)
    applies_to_domain TEXT NOT NULL,     -- 'sd', 'cd', 'rd', 'sf', 'bt'
    applies_to_type   TEXT NOT NULL,     -- 'BEDROOM_MY', 'TOILET_*', '*'
                                         -- Wildcards: * = all, TOILET_* = all toilets

    -- The rule expression
    property        TEXT NOT NULL,        -- what is being constrained
    operator        TEXT NOT NULL,        -- how it is constrained
    value           TEXT NOT NULL,        -- the constraint value
    value_unit      TEXT,                 -- unit of measurement

    -- Conditional application (NULL = always applies)
    condition_expr  TEXT,                 -- 'building_type = RESIDENTIAL'
                                         -- 'floor_count > 4'
                                         -- 'region = MY'

    -- Authority and citation
    authority_id    TEXT,                 -- FK to rd_code_authority when it exists
    clause_ref      TEXT,                 -- 'UBBL Part III Section 40(1)'
    authority_name  TEXT,                 -- 'UBBL' (denormalised for standalone use)

    -- Severity and status
    severity        TEXT DEFAULT 'ERROR', -- ERROR = must comply, WARNING = advisory, INFO = logged
    is_active       INTEGER DEFAULT 1,
    seq_no          INTEGER DEFAULT 10,

    -- Override: more specific rule replaces generic
    override_rule_id TEXT REFERENCES bad_rule,

    -- Provenance (PRIME RULE: every rule must cite its source)
    provenance      TEXT NOT NULL
    -- RESEARCHED_UBBL_2012          : extracted from UBBL 2012 Amendment 2020
    -- RESEARCHED_MS_1228            : extracted from MS 1228
    -- RESEARCHED_BOMBA_MS_1910      : extracted from BOMBA / MS 1910
    -- RESEARCHED_CONSTRUCTION_PRACTICE : standard construction practice (cite source)
    -- PENDING_NEEDS_CODE_CITATION   : rule is correct but needs specific clause
);
```

### 2.3 Operators Reference

```sql
-- Operator vocabulary for bad_rule.operator
-- Numeric comparison:
--   GTE           : greater than or equal (min_area_sqm GTE 11.15)
--   LTE           : less than or equal (max_spacing_mm LTE 4300)
--   EQUALS        : exact match
--   BETWEEN       : range (value = 'min|max', e.g. '900|1200')
--
-- Spatial relationship:
--   FACES_AWAY_FROM  : element faces away from target (toilet from door)
--   FACES_TOWARD     : element faces toward target (basin toward user)
--   CENTRED_ON       : element centred on target (light in room)
--   PARALLEL_TO      : element parallel to target (beam to grid line)
--   PERPENDICULAR_TO : element perpendicular to target
--   ADJACENT_TO      : element touching or within clearance of target
--   OFFSET_FROM      : element at specified distance from target
--
-- Topological relationship:
--   MUST_ADJOIN      : spaces must share a wall (bathroom-kitchen)
--   MUST_NOT_ADJOIN  : spaces must not share a wall
--   MUST_HAVE        : space must contain element (bedroom must have window)
--   MUST_STACK       : spaces must align vertically across floors
--   MUST_CONNECT     : spaces must have circulation path between them
--
-- Structural:
--   AT_GRID_INTERSECTION : element at grid node
--   SPANS_BETWEEN        : element spans between two grid nodes
--
-- Priority:
--   HAS_PRIORITY_OVER   : this discipline takes precedence
--   ROUTES_AROUND       : this discipline routes around other
```

### 2.4 Rule Parameters (≈ AD_PInstance_Para)

For rules that need multiple values beyond the single property/operator/value:

```sql
CREATE TABLE bad_rule_param (
    rule_id         TEXT NOT NULL REFERENCES bad_rule,
    param_key       TEXT NOT NULL,
    param_value     TEXT NOT NULL,
    param_unit      TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (rule_id, param_key)
);
```

### 2.5 Discipline Priority Matrix

```sql
CREATE TABLE bad_discipline_priority (
    higher_discipline TEXT NOT NULL,
    lower_discipline  TEXT NOT NULL,
    resolution       TEXT NOT NULL,
    -- ROUTE_AROUND            : lower routes around higher
    -- PENETRATE_WITH_APPROVAL : lower can penetrate with engineer sign-off
    -- COORDINATE              : neither yields, resolve case by case
    -- FIRE_HAS_PRIORITY       : fire protection always wins
    notes           TEXT,
    provenance      TEXT NOT NULL,
    PRIMARY KEY (higher_discipline, lower_discipline)
);
```

---

## 3. Rule Population

**PRIME RULE: Extract, don't imagine.** Every rule must cite a specific source. If the source is "everyone knows toilets face away from doors," write `RESEARCHED_CONSTRUCTION_PRACTICE` and add a note. If the source is UBBL Section 40, write `RESEARCHED_UBBL_2012` with the clause reference.

### 3.1 SPATIAL Rules — How Spaces Relate

```sql
-- ═══════════════════════════════════════════════════════════════
-- SPATIAL RULES
-- ═══════════════════════════════════════════════════════════════

-- SPT_001: Bedroom minimum area (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_001', 'SPATIAL', 'Bedroom minimum area (MY)', 'CONSTRAINT',
    'sd', 'BEDROOM_MY',
    'min_area_sqm', 'GTE', '11.15', 'sqm',
    'region = MY',
    NULL, 'UBBL Part III Section 40(1)', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_002: Bedroom minimum width (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_002', 'SPATIAL', 'Bedroom minimum width (MY)', 'CONSTRAINT',
    'sd', 'BEDROOM_MY',
    'min_width_mm', 'GTE', '2750', 'mm',
    'region = MY',
    NULL, 'UBBL Part III Section 40(1)', 'UBBL',
    'ERROR', 1, 20, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_003: Corridor minimum width (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_003', 'SPATIAL', 'Corridor minimum width (MY)', 'CONSTRAINT',
    'sd', 'CORRIDOR_MY',
    'min_width_mm', 'GTE', '900', 'mm',
    'region = MY AND building_type = RESIDENTIAL',
    NULL, 'UBBL Part III Section 42', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_004: Corridor minimum width — accessible (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_004', 'SPATIAL', 'Corridor minimum width accessible (MY)', 'CONSTRAINT',
    'sd', 'CORRIDOR_MY',
    'min_width_mm', 'GTE', '1200', 'mm',
    'region = MY AND accessible = true',
    NULL, 'MS 1184 Section 5.2', 'MS 1184',
    'ERROR', 1, 10, 'SPT_003',
    'RESEARCHED_MS_1184'
);

-- SPT_005: Bedroom must have external wall (daylight/ventilation)
INSERT INTO bad_rule VALUES (
    'SPT_005', 'SPATIAL', 'Bedroom requires external wall', 'CONSTRAINT',
    'sd', 'BEDROOM_*',
    'has_external_wall', 'MUST_HAVE', 'EXTERNAL_WALL', NULL,
    NULL,
    NULL, 'UBBL Part III Section 39', 'UBBL',
    'ERROR', 1, 30, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_006: Wet areas share plumbing wall
INSERT INTO bad_rule VALUES (
    'SPT_006', 'SPATIAL', 'Wet area plumbing wall sharing', 'PREFERENCE',
    'sd', 'BATHROOM_*',
    'adjacency', 'MUST_ADJOIN', 'KITCHEN_*', NULL,
    NULL,
    NULL, NULL, NULL,
    'WARNING', 1, 40, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- SPT_007: Kitchen minimum area (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_007', 'SPATIAL', 'Kitchen minimum area (MY)', 'CONSTRAINT',
    'sd', 'KITCHEN_MY',
    'min_area_sqm', 'GTE', '4.5', 'sqm',
    'region = MY',
    NULL, 'UBBL Part III Section 40(2)', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_008: Minimum floor-to-ceiling height habitable room (Malaysia)
INSERT INTO bad_rule VALUES (
    'SPT_008', 'SPATIAL', 'Minimum ceiling height habitable (MY)', 'CONSTRAINT',
    'sd', '*',
    'min_height_mm', 'GTE', '2600', 'mm',
    'region = MY AND space_category = HABITABLE',
    NULL, 'UBBL Part III Section 41', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_009: Stairwell connects to corridor every floor
INSERT INTO bad_rule VALUES (
    'SPT_009', 'SPATIAL', 'Stairwell corridor connection', 'CONSTRAINT',
    'sd', 'STAIRWELL_*',
    'connection', 'MUST_CONNECT', 'CORRIDOR_*', NULL,
    'floor_count > 1',
    NULL, 'UBBL Part VII Section 165', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- SPT_010: Living room minimum area (Malaysia PR1MA standard)
INSERT INTO bad_rule VALUES (
    'SPT_010', 'SPATIAL', 'Living room minimum area (MY PR1MA)', 'CONSTRAINT',
    'sd', 'LIVING_MY',
    'min_area_sqm', 'GTE', '13.0', 'sqm',
    'region = MY AND building_class = AFFORDABLE',
    NULL, 'PR1MA Design Guidelines Section 3.2', 'PR1MA',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_PR1MA_GUIDELINES'
);
```

### 3.2 PLACEMENT Rules — How Elements Sit in Spaces

```sql
-- ═══════════════════════════════════════════════════════════════
-- PLACEMENT RULES
-- ═══════════════════════════════════════════════════════════════

-- PLT_001: Toilet faces away from door
INSERT INTO bad_rule VALUES (
    'PLT_001', 'PLACEMENT', 'Toilet facing rule', 'CONSTRAINT',
    'cd', 'TOILET_*',
    'rotation_rule', 'FACES_AWAY_FROM', 'DOOR', NULL,
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 10, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_002: Toilet back to wall
INSERT INTO bad_rule VALUES (
    'PLT_002', 'PLACEMENT', 'Toilet wall mounting', 'CONSTRAINT',
    'cd', 'TOILET_*',
    'mounting', 'ADJACENT_TO', 'WALL', NULL,
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 20, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_003: Basin adjacent to toilet
INSERT INTO bad_rule VALUES (
    'PLT_003', 'PLACEMENT', 'Basin adjacency to toilet', 'PREFERENCE',
    'cd', 'BASIN_*',
    'adjacency', 'ADJACENT_TO', 'TOILET_*', NULL,
    'space_category = WET',
    NULL, NULL, NULL,
    'WARNING', 1, 30, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_004: Door swing into room (Malaysia residential)
INSERT INTO bad_rule VALUES (
    'PLT_004', 'PLACEMENT', 'Door swing direction residential', 'CONSTRAINT',
    'cd', 'DOOR_*',
    'swing_direction', 'EQUALS', 'INTO_ROOM', NULL,
    'region = MY AND building_type = RESIDENTIAL AND space_category != WET',
    NULL, 'UBBL Part III Section 43(a)', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- PLT_005: Bathroom door swings outward (Malaysia)
INSERT INTO bad_rule VALUES (
    'PLT_005', 'PLACEMENT', 'Bathroom door swing outward', 'CONSTRAINT',
    'cd', 'DOOR_*',
    'swing_direction', 'EQUALS', 'OUT_OF_ROOM', NULL,
    'region = MY AND space_category = WET',
    NULL, 'UBBL Part III Section 43(b)', 'UBBL',
    'ERROR', 1, 10, 'PLT_004',
    'RESEARCHED_UBBL_2012'
);

-- PLT_006: Window centred on external wall
INSERT INTO bad_rule VALUES (
    'PLT_006', 'PLACEMENT', 'Window wall centring', 'PREFERENCE',
    'cd', 'WINDOW_*',
    'position', 'CENTRED_ON', 'EXTERNAL_WALL', NULL,
    NULL,
    NULL, NULL, NULL,
    'WARNING', 1, 10, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_007: Minimum clearance from door swing arc
INSERT INTO bad_rule VALUES (
    'PLT_007', 'PLACEMENT', 'Door swing clearance', 'CONSTRAINT',
    'cd', '*',
    'min_clearance_from_door_arc_mm', 'GTE', '200', 'mm',
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 40, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_008: Light fitting centred in room
INSERT INTO bad_rule VALUES (
    'PLT_008', 'PLACEMENT', 'Room light centring', 'PREFERENCE',
    'cd', 'LIGHT_CEILING_*',
    'position', 'CENTRED_ON', 'ROOM', NULL,
    'space_category = HABITABLE',
    NULL, NULL, NULL,
    'WARNING', 1, 10, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_009: Light fitting grid-aligned in corridor
INSERT INTO bad_rule VALUES (
    'PLT_009', 'PLACEMENT', 'Corridor light grid alignment', 'PREFERENCE',
    'cd', 'LIGHT_CEILING_*',
    'position', 'EQUALS', 'GRID_CENTRE_LINE', NULL,
    'space_category = CIRCULATION',
    NULL, NULL, NULL,
    'WARNING', 1, 10, 'PLT_008',
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- PLT_010: Furniture clearance — sofa to coffee table
INSERT INTO bad_rule VALUES (
    'PLT_010', 'PLACEMENT', 'Sofa to table clearance', 'CONSTRAINT',
    'cd', 'SOFA_*',
    'min_clearance_mm', 'GTE', '400', 'mm',
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 50, NULL,
    'RESEARCHED_MS_1064'
);
INSERT INTO bad_rule_param VALUES
('PLT_010', 'clearance_to', 'COFFEE_TABLE_*', NULL, 'RESEARCHED_MS_1064');

-- PLT_011: Walkthrough clearance
INSERT INTO bad_rule VALUES (
    'PLT_011', 'PLACEMENT', 'Minimum walkthrough gap', 'CONSTRAINT',
    'cd', '*',
    'min_walkthrough_mm', 'GTE', '900', 'mm',
    NULL,
    NULL, 'UBBL Part III Section 42', 'UBBL',
    'ERROR', 1, 60, NULL,
    'RESEARCHED_UBBL_2012'
);

-- PLT_012: Wall-mounted element offset from wall
INSERT INTO bad_rule VALUES (
    'PLT_012', 'PLACEMENT', 'Wall mount minimum offset', 'CONSTRAINT',
    'cd', '*',
    'wall_offset_min_mm', 'GTE', '50', 'mm',
    'mounting_face IS NOT NULL',
    NULL, NULL, NULL,
    'ERROR', 1, 70, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);
```

### 3.3 STRUCTURAL Rules — How Structure Organises

```sql
-- ═══════════════════════════════════════════════════════════════
-- STRUCTURAL RULES
-- ═══════════════════════════════════════════════════════════════

-- STR_001: Column at grid intersection
INSERT INTO bad_rule VALUES (
    'STR_001', 'STRUCTURAL', 'Column at grid node', 'CONSTRAINT',
    'sf', 'COLUMN_*',
    'position', 'AT_GRID_INTERSECTION', '*', NULL,
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 10, NULL,
    'RESEARCHED_STRUCTURAL_ENGINEERING'
);

-- STR_002: Beam spans between columns
INSERT INTO bad_rule VALUES (
    'STR_002', 'STRUCTURAL', 'Beam grid span', 'CONSTRAINT',
    'sf', 'BEAM_*',
    'span', 'SPANS_BETWEEN', 'COLUMN_*', NULL,
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 20, NULL,
    'RESEARCHED_STRUCTURAL_ENGINEERING'
);

-- STR_003: Maximum beam span without support (concrete)
INSERT INTO bad_rule VALUES (
    'STR_003', 'STRUCTURAL', 'Concrete beam max span', 'CONSTRAINT',
    'sf', 'BEAM_CONCRETE_*',
    'max_span_mm', 'LTE', '8000', 'mm',
    'material = CONCRETE',
    NULL, 'BS EN 1992-1-1 Table NA.1', NULL,
    'WARNING', 1, 30, NULL,
    'RESEARCHED_EC2_GENERAL'
);

-- STR_004: Slab max span per thickness
INSERT INTO bad_rule VALUES (
    'STR_004', 'STRUCTURAL', 'Slab span-to-depth ratio', 'CONSTRAINT',
    'sf', 'SLAB_*',
    'span_depth_ratio', 'LTE', '26', 'ratio',
    'slab_type = ONE_WAY AND end_condition = SIMPLY_SUPPORTED',
    NULL, 'BS EN 1992-1-1 Table 7.4N', NULL,
    'ERROR', 1, 40, NULL,
    'RESEARCHED_EC2_GENERAL'
);

-- STR_005: Structural grid consistent across typical floors
INSERT INTO bad_rule VALUES (
    'STR_005', 'STRUCTURAL', 'Grid consistency across floors', 'CONSTRAINT',
    'sf', 'GRID_*',
    'vertical_consistency', 'MUST_STACK', 'GRID_*', NULL,
    'storey_type = TYPICAL',
    NULL, NULL, NULL,
    'ERROR', 1, 50, NULL,
    'RESEARCHED_STRUCTURAL_ENGINEERING'
);

-- STR_006: Shear wall required above 4 storeys (Malaysia)
INSERT INTO bad_rule VALUES (
    'STR_006', 'STRUCTURAL', 'Shear wall requirement (MY)', 'CONSTRAINT',
    'sf', 'BUILDING_*',
    'requires_shear_wall', 'EQUALS', 'true', NULL,
    'region = MY AND floor_count > 4',
    NULL, 'JKR Guidelines', 'JKR',
    'ERROR', 1, 60, NULL,
    'RESEARCHED_JKR_GUIDELINES'
);
```

### 3.4 MEP Rules — How Services Coordinate

```sql
-- ═══════════════════════════════════════════════════════════════
-- MEP RULES
-- ═══════════════════════════════════════════════════════════════

-- MEP_001: Sprinkler maximum spacing
INSERT INTO bad_rule VALUES (
    'MEP_001', 'MEP', 'Sprinkler head max spacing', 'CONSTRAINT',
    'cd', 'SPRINKLER_*',
    'max_spacing_mm', 'LTE', '4300', 'mm',
    NULL,
    NULL, 'MS 1910 Table 5.1', 'BOMBA',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_BOMBA_MS_1910'
);

-- MEP_002: Sprinkler minimum spacing
INSERT INTO bad_rule VALUES (
    'MEP_002', 'MEP', 'Sprinkler head min spacing', 'CONSTRAINT',
    'cd', 'SPRINKLER_*',
    'min_spacing_mm', 'GTE', '2000', 'mm',
    NULL,
    NULL, 'MS 1910 Table 5.1', 'BOMBA',
    'ERROR', 1, 20, NULL,
    'RESEARCHED_BOMBA_MS_1910'
);

-- MEP_003: Smoke detector coverage radius
INSERT INTO bad_rule VALUES (
    'MEP_003', 'MEP', 'Smoke detector coverage', 'CONSTRAINT',
    'cd', 'SMOKE_DETECTOR_*',
    'max_coverage_radius_mm', 'LTE', '7500', 'mm',
    NULL,
    NULL, 'MS 1910 Part 4', 'BOMBA',
    'ERROR', 1, 30, NULL,
    'RESEARCHED_BOMBA_MS_1910'
);

-- MEP_004: Riser vertical alignment across all floors
INSERT INTO bad_rule VALUES (
    'MEP_004', 'MEP', 'Riser vertical continuity', 'CONSTRAINT',
    'cd', 'RISER_*',
    'vertical_alignment', 'MUST_STACK', 'RISER_*', NULL,
    'floor_count > 1',
    NULL, NULL, NULL,
    'ERROR', 1, 40, NULL,
    'RESEARCHED_PLUMBING_PRACTICE'
);

-- MEP_005: Horizontal branch max length per pipe diameter
INSERT INTO bad_rule VALUES (
    'MEP_005', 'MEP', 'Branch pipe max run', 'CONSTRAINT',
    'cd', 'PIPE_BRANCH_*',
    'max_length_mm', 'LTE', '6000', 'mm',
    'pipe_dn_mm <= 50',
    NULL, 'MS 1228 Table 4.2', 'MS 1228',
    'ERROR', 1, 50, NULL,
    'RESEARCHED_MS_1228'
);

-- MEP_006: Toilet fixture units (loading)
INSERT INTO bad_rule VALUES (
    'MEP_006', 'MEP', 'Toilet fixture unit loading', 'CONSTRAINT',
    'cd', 'TOILET_*',
    'fixture_units', 'EQUALS', '6', 'FU',
    NULL,
    NULL, 'MS 1228 Table 3.1', 'MS 1228',
    'INFO', 1, 60, NULL,
    'RESEARCHED_MS_1228'
);

-- MEP_007: Basin fixture units
INSERT INTO bad_rule VALUES (
    'MEP_007', 'MEP', 'Basin fixture unit loading', 'CONSTRAINT',
    'cd', 'BASIN_*',
    'fixture_units', 'EQUALS', '1', 'FU',
    NULL,
    NULL, 'MS 1228 Table 3.1', 'MS 1228',
    'INFO', 1, 70, NULL,
    'RESEARCHED_MS_1228'
);

-- MEP_008: Electrical DB per floor
INSERT INTO bad_rule VALUES (
    'MEP_008', 'MEP', 'Distribution board per floor', 'CONSTRAINT',
    'cd', 'DISTRIBUTION_BOARD_*',
    'count_per_storey', 'GTE', '1', 'units',
    'floor_count > 1',
    NULL, 'MS IEC 60364', NULL,
    'ERROR', 1, 80, NULL,
    'RESEARCHED_MS_IEC_60364'
);

-- MEP_009: MEP zone below slab above ceiling
INSERT INTO bad_rule VALUES (
    'MEP_009', 'MEP', 'MEP service zone depth', 'CONSTRAINT',
    'cd', 'MEP_*',
    'service_zone_mm', 'BETWEEN', '300|600', 'mm',
    NULL,
    NULL, NULL, NULL,
    'WARNING', 1, 90, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);

-- MEP_010: No services through structural beams
INSERT INTO bad_rule VALUES (
    'MEP_010', 'MEP', 'No beam penetration without approval', 'CONSTRAINT',
    'cd', 'MEP_*',
    'penetration', 'MUST_NOT_CROSS', 'BEAM_*', NULL,
    NULL,
    NULL, NULL, NULL,
    'ERROR', 1, 100, NULL,
    'RESEARCHED_STRUCTURAL_COORDINATION'
);
```

### 3.5 INTER-FLOOR Rules — How Storeys Relate

```sql
-- ═══════════════════════════════════════════════════════════════
-- INTER-FLOOR RULES
-- ═══════════════════════════════════════════════════════════════

-- IFL_001: Wet area vertical stacking
INSERT INTO bad_rule VALUES (
    'IFL_001', 'INTER_FLOOR', 'Wet area stacking', 'PREFERENCE',
    'sd', 'BATHROOM_*',
    'vertical_alignment', 'MUST_STACK', 'BATHROOM_*', NULL,
    'floor_count > 1',
    NULL, NULL, NULL,
    'WARNING', 1, 10, NULL,
    'RESEARCHED_PLUMBING_EFFICIENCY'
);

-- IFL_002: Kitchen vertical stacking
INSERT INTO bad_rule VALUES (
    'IFL_002', 'INTER_FLOOR', 'Kitchen stacking', 'PREFERENCE',
    'sd', 'KITCHEN_*',
    'vertical_alignment', 'MUST_STACK', 'KITCHEN_*', NULL,
    'floor_count > 1',
    NULL, NULL, NULL,
    'WARNING', 1, 20, NULL,
    'RESEARCHED_PLUMBING_EFFICIENCY'
);

-- IFL_003: Structural grid consistency on typical floors
INSERT INTO bad_rule VALUES (
    'IFL_003', 'INTER_FLOOR', 'Structural grid floor consistency', 'CONSTRAINT',
    'sf', 'GRID_*',
    'grid_layout', 'EQUALS', 'IDENTICAL_TO_TYPICAL', NULL,
    'storey_type = TYPICAL',
    NULL, NULL, NULL,
    'ERROR', 1, 30, NULL,
    'RESEARCHED_STRUCTURAL_ENGINEERING'
);

-- IFL_004: Floor-to-floor height consistency on typical floors
INSERT INTO bad_rule VALUES (
    'IFL_004', 'INTER_FLOOR', 'Typical floor height consistency', 'CONSTRAINT',
    'sf', 'STOREY_*',
    'floor_to_floor_mm', 'EQUALS', 'SAME_AS_TYPICAL', NULL,
    'storey_type = TYPICAL',
    NULL, NULL, NULL,
    'ERROR', 1, 40, NULL,
    'RESEARCHED_STRUCTURAL_ENGINEERING'
);

-- IFL_005: Stair landing aligns with floor level
INSERT INTO bad_rule VALUES (
    'IFL_005', 'INTER_FLOOR', 'Stair landing floor alignment', 'CONSTRAINT',
    'cd', 'STAIR_LANDING_*',
    'z_level', 'EQUALS', 'STOREY_FLOOR_LEVEL', NULL,
    NULL,
    NULL, 'UBBL Part VII Section 165(3)', 'UBBL',
    'ERROR', 1, 50, NULL,
    'RESEARCHED_UBBL_2012'
);
```

### 3.6 COORDINATION Rules — How Disciplines Avoid Conflict

```sql
-- ═══════════════════════════════════════════════════════════════
-- DISCIPLINE COORDINATION
-- ═══════════════════════════════════════════════════════════════

-- Priority matrix
INSERT INTO bad_discipline_priority VALUES
('STRUCTURAL',       'ARCHITECTURAL',  'COORDINATE',            'Neither yields automatically — resolve per case', 'RESEARCHED_BIM_COORDINATION'),
('STRUCTURAL',       'MEP',            'ROUTE_AROUND',          'MEP always routes around structure',              'RESEARCHED_BIM_COORDINATION'),
('STRUCTURAL',       'FIRE_PROTECTION','COORDINATE',            'Fire penetrations need structural engineer sign-off','RESEARCHED_STRUCTURAL_COORDINATION'),
('FIRE_PROTECTION',  'MEP',            'FIRE_HAS_PRIORITY',     'Fire suppression has priority over general MEP',  'RESEARCHED_BOMBA'),
('FIRE_PROTECTION',  'ARCHITECTURAL',  'FIRE_HAS_PRIORITY',     'Fire exits cannot be blocked by architectural elements','RESEARCHED_BOMBA'),
('ARCHITECTURAL',    'MEP',            'COORDINATE',            'MEP visible elements coordinate with architecture','RESEARCHED_BIM_COORDINATION'),
('ELECTRICAL',       'PLUMBING',       'COORDINATE',            'Electrical above plumbing in service zone',       'RESEARCHED_MS_IEC_60364');

-- Coordination rules
-- CDN_001: Sprinkler below ductwork
INSERT INTO bad_rule VALUES (
    'CDN_001', 'COORDINATION', 'Sprinkler below ductwork', 'CONSTRAINT',
    'cd', 'SPRINKLER_*',
    'z_position', 'EQUALS', 'BELOW_DUCTWORK', NULL,
    NULL,
    NULL, 'MS 1910 Section 5.3', 'BOMBA',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_BOMBA_MS_1910'
);

-- CDN_002: Sprinkler above lighting
INSERT INTO bad_rule VALUES (
    'CDN_002', 'COORDINATION', 'Sprinkler above lighting', 'CONSTRAINT',
    'cd', 'SPRINKLER_*',
    'z_position', 'EQUALS', 'ABOVE_LIGHTING', NULL,
    NULL,
    NULL, 'MS 1910 Section 5.3', 'BOMBA',
    'ERROR', 1, 20, NULL,
    'RESEARCHED_BOMBA_MS_1910'
);

-- CDN_003: Fire stopping at service penetrations
INSERT INTO bad_rule VALUES (
    'CDN_003', 'COORDINATION', 'Fire stopping at wall penetration', 'CONSTRAINT',
    'cd', 'MEP_*',
    'fire_stopping', 'MUST_HAVE', 'WALL_PENETRATION', NULL,
    'wall_type = FIRE_RATED',
    NULL, 'UBBL Part VIII Section 185', 'UBBL',
    'ERROR', 1, 30, NULL,
    'RESEARCHED_UBBL_2012'
);

-- CDN_004: Waterproofing extent in wet areas
INSERT INTO bad_rule VALUES (
    'CDN_004', 'COORDINATION', 'Wet area waterproofing upstand', 'CONSTRAINT',
    'cd', 'WATERPROOFING_*',
    'wall_upstand_mm', 'GTE', '150', 'mm',
    'space_category = WET',
    NULL, NULL, NULL,
    'ERROR', 1, 40, NULL,
    'RESEARCHED_CONSTRUCTION_PRACTICE'
);
```

---

## 4. Fire Protection Deep Dive (Malaysia — UBBL + BOMBA)

Fire protection rules are complex enough to warrant explicit detail. These are the rules that BOMBA checks during building plan approval.

```sql
-- FPR_001: Fire escape max travel distance — residential
INSERT INTO bad_rule VALUES (
    'FPR_001', 'SPATIAL', 'Fire escape travel distance residential (MY)', 'CONSTRAINT',
    'sd', '*',
    'max_travel_distance_m', 'LTE', '45', 'm',
    'region = MY AND building_type = RESIDENTIAL AND has_sprinkler = false',
    NULL, 'UBBL Part VII Section 166(1)(a)', 'UBBL',
    'ERROR', 1, 10, NULL,
    'RESEARCHED_UBBL_2012'
);

-- FPR_002: Fire escape travel distance — sprinklered residential
INSERT INTO bad_rule VALUES (
    'FPR_002', 'SPATIAL', 'Fire escape travel distance sprinklered (MY)', 'CONSTRAINT',
    'sd', '*',
    'max_travel_distance_m', 'LTE', '60', 'm',
    'region = MY AND building_type = RESIDENTIAL AND has_sprinkler = true',
    NULL, 'UBBL Part VII Section 166(1)(b)', 'UBBL',
    'ERROR', 1, 10, 'FPR_001',
    'RESEARCHED_UBBL_2012'
);

-- FPR_003: Fire escape travel distance — institutional
INSERT INTO bad_rule VALUES (
    'FPR_003', 'SPATIAL', 'Fire escape travel distance institutional (MY)', 'CONSTRAINT',
    'sd', '*',
    'max_travel_distance_m', 'LTE', '30', 'm',
    'region = MY AND building_type = INSTITUTIONAL',
    NULL, 'UBBL Part VII Section 166(2)', 'UBBL',
    'ERROR', 1, 10, 'FPR_001',
    'RESEARCHED_UBBL_2012'
);

-- FPR_004: Minimum two exits for rooms above threshold
INSERT INTO bad_rule VALUES (
    'FPR_004', 'SPATIAL', 'Minimum two exits (MY)', 'CONSTRAINT',
    'sd', '*',
    'min_exit_count', 'GTE', '2', 'exits',
    'region = MY AND occupant_load > 50',
    NULL, 'UBBL Part VII Section 163', 'UBBL',
    'ERROR', 1, 20, NULL,
    'RESEARCHED_UBBL_2012'
);

-- FPR_005: Fire door minimum fire rating
INSERT INTO bad_rule VALUES (
    'FPR_005', 'PLACEMENT', 'Fire door minimum rating (MY)', 'CONSTRAINT',
    'cd', 'DOOR_FIRE_*',
    'fire_rating_min', 'GTE', '30', 'minutes',
    'region = MY',
    NULL, 'UBBL Part VIII Section 184', 'UBBL',
    'ERROR', 1, 30, NULL,
    'RESEARCHED_UBBL_2012'
);

-- FPR_006: Fire door self-closing
INSERT INTO bad_rule VALUES (
    'FPR_006', 'PLACEMENT', 'Fire door self-closing (MY)', 'CONSTRAINT',
    'cd', 'DOOR_FIRE_*',
    'self_closing', 'EQUALS', 'true', NULL,
    'region = MY',
    NULL, 'UBBL Part VIII Section 184(2)', 'UBBL',
    'ERROR', 1, 40, NULL,
    'RESEARCHED_UBBL_2012'
);
```

---

## 5. Verification Queries

After population, run these to verify BAD integrity:

```sql
-- Count rules per category
SELECT c.category_name, COUNT(r.rule_id) as rule_count
FROM bad_rule_category c
LEFT JOIN bad_rule r ON c.category_id = r.category_id AND r.is_active = 1
GROUP BY c.category_name
ORDER BY c.seq_no;

-- Rules without provenance (MUST BE ZERO)
SELECT rule_id, rule_name FROM bad_rule WHERE provenance IS NULL OR provenance = '';

-- Rules without authority citation (acceptable for CONSTRUCTION_PRACTICE only)
SELECT rule_id, rule_name, provenance FROM bad_rule 
WHERE clause_ref IS NULL AND provenance NOT LIKE '%CONSTRUCTION_PRACTICE%';

-- Override chain integrity (override target must exist)
SELECT r.rule_id, r.override_rule_id 
FROM bad_rule r 
WHERE r.override_rule_id IS NOT NULL 
AND r.override_rule_id NOT IN (SELECT rule_id FROM bad_rule);

-- Duplicate property constraints on same type (potential conflict)
SELECT applies_to_type, property, COUNT(*) as cnt
FROM bad_rule WHERE is_active = 1
GROUP BY applies_to_type, property
HAVING cnt > 1;

-- Discipline priority completeness
SELECT DISTINCT 'MISSING: ' || a.d || ' vs ' || b.d
FROM (SELECT DISTINCT higher_discipline as d FROM bad_discipline_priority
      UNION SELECT DISTINCT lower_discipline FROM bad_discipline_priority) a
CROSS JOIN (SELECT DISTINCT higher_discipline as d FROM bad_discipline_priority
            UNION SELECT DISTINCT lower_discipline FROM bad_discipline_priority) b
WHERE a.d < b.d
AND NOT EXISTS (
    SELECT 1 FROM bad_discipline_priority 
    WHERE (higher_discipline = a.d AND lower_discipline = b.d)
       OR (higher_discipline = b.d AND lower_discipline = a.d)
);
```

---

## 6. How BAD Connects to the Compiler (Future — NOT NOW)

**Actual prerequisite state (from codebase audit, 2026-02-20):**

| Prerequisite | Status |
|-------------|--------|
| CompilerStage interface | ✅ DONE — 8-stage pipeline active |
| MetadataValidator as Stage 1 | ✅ DONE — blocks on bad metadata |
| DomainStore (Phase B) | ❌ NOT DONE — lazy singleton Resolvers instead |
| Typed domain records (Phase C) | ❌ NOT DONE — raw SQL rows |
| Domain migration (Phase D) | ❌ NOT DONE — all tables still ad_* |

BADValidator is still Phase E. The two ✅ prerequisites being done means Phase B entry is closer than originally estimated, but DomainStore itself does not exist. The gate remains Phase B completion.

When the compiler is ready to consume BAD (Phase E of METADATA_DRIVEN_ARCHITECTURE.md):

```java
/**
 * BADValidator implements CompilerValidator.
 * Reads bad_rule table, evaluates against compiled model.
 * Fires at beforeWrite stage — after geometry resolved, before IFC output.
 *
 * DO NOT IMPLEMENT UNTIL:
 * - DomainStore exists (Phase B) — currently: lazy singleton Resolvers only
 * - Typed domain records exist (Phase C) — currently: raw SQL Maps
 * - Domain migration complete (Phase D) — currently: all ad_* tables
 */
public class BADValidator implements CompilerValidator {

    @Override
    public String beforeWrite(BtBuilding building, CompilationContext ctx) {
        List<BADRule> rules = domainStore.bad().activeRulesFor(building);
        List<BADViolation> violations = new ArrayList<>();

        for (BADRule rule : rules) {
            if (!rule.conditionMet(building)) continue;

            BADResult result = rule.evaluate(building, ctx);
            if (!result.passed()) {
                violations.add(new BADViolation(rule, result));
            }
        }

        // Report
        for (BADViolation v : violations) {
            if (v.severity() == ERROR) {
                return "BAD VIOLATION: " + v.rule().ruleId()
                    + " — " + v.rule().ruleName()
                    + " [" + v.rule().clauseRef() + "]";
            }
            ctx.addWarning(v.toString());  // WARNINGs don't block
        }
        return null;  // all ERRORs passed
    }
}
```

---

## 7. Working Instructions for Code

### 7.1 What to Do NOW

1. **Create the four `bad_*` tables** (Section 2) in component_library.db
2. **Populate all rules from Section 3** exactly as written
3. **Run all verification queries from Section 5** — paste results
4. **Do NOT create any Java code** — BAD is pure data, no compiler changes
5. **Do NOT modify any existing tables** — BAD is additive only

### 7.2 How to Add New Rules

When new rules are identified during future work:

```sql
-- Follow the pattern: category prefix + sequential number
-- SPT_ = Spatial, PLT_ = Placement, STR_ = Structural
-- MEP_ = MEP, IFL_ = Inter-Floor, CDN_ = Coordination, FPR_ = Fire

INSERT INTO bad_rule VALUES (
    'SPT_011',                          -- rule_id: category prefix + number
    'SPATIAL',                          -- category_id
    'New spatial rule name',            -- rule_name: human readable
    'CONSTRAINT',                       -- rule_type: CONSTRAINT/PREFERENCE/RELATIONSHIP/COORDINATION
    'sd', 'BEDROOM_MY',                -- domain + type it applies to
    'property_name', 'GTE', '10', 'sqm', -- the rule expression
    'region = MY',                      -- condition (NULL = always)
    NULL, 'UBBL Section XX', 'UBBL',   -- authority + clause
    'ERROR', 1, 10, NULL,              -- severity, active, seq, override
    'RESEARCHED_UBBL_2012'             -- provenance: ALWAYS REQUIRED
);
```

### 7.3 Provenance Requirements (PRIME RULE)

Every rule MUST have provenance. Acceptable values:

```
RESEARCHED_UBBL_2012              — from UBBL 2012 (with/without Amendment 2020)
RESEARCHED_MS_xxxx                — from Malaysian Standard (specify number)
RESEARCHED_BOMBA_MS_1910          — from BOMBA fire protection standard
RESEARCHED_JKR_GUIDELINES         — from JKR structural guidelines
RESEARCHED_PR1MA_GUIDELINES       — from PR1MA affordable housing specs
RESEARCHED_MS_IEC_60364           — from electrical installation standard
RESEARCHED_BS_EN_xxxx             — from British/European standard
RESEARCHED_CONSTRUCTION_PRACTICE  — standard practice (add note explaining source)
RESEARCHED_BIM_COORDINATION       — from BIM coordination guidelines
PENDING_NEEDS_CODE_CITATION       — rule is correct, needs specific clause reference
EXTRACTED_SAMPLEHOUSE             — derived from reference building analysis
EXTRACTED_DUPLEX                  — derived from reference building analysis
EXTRACTED_TERMINAL                — derived from reference building analysis
```

**Rules with PENDING provenance are allowed during development but MUST be resolved before any building transitions to VALIDATED status.**

---

## 8. Growth Path

BAD grows continuously. Each new jurisdiction, building type, or discipline adds rows:

```
CURRENT (Malaysia residential):     ~40 rules
+ Malaysia institutional:           +20 rules (BOMBA public assembly, larger spaces)
+ Singapore (BCA):                  +30 rules (different fire codes, accessibility)
+ UK (Building Regulations):        +30 rules (Part B fire, Part M access, Part L energy)
+ Multi-storey structural:          +15 rules (shear walls, transfer structures)
+ Landscape/external:               +10 rules (setbacks, drainage, parking)
+ Acoustic:                         +10 rules (MS 1346, STC ratings)
+ Energy efficiency:                +10 rules (OTTV, RTTV, MS 1525)
───────────────────────────────────────────
PLATFORM MATURITY:                  ~165+ rules governing all placement

Each rule is one SQL INSERT. No Java changes. No compiler modifications.
The BADValidator reads them all from the same table.
```

This is the moat. Anyone can build a BIM compiler. Nobody else has a machine-readable, provenance-tracked, clause-cited construction rule dictionary covering multiple jurisdictions. The compiler is the engine. BAD is the knowledge. The knowledge is the product.
