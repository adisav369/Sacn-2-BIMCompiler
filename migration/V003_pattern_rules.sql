-- V003_pattern_rules.sql
-- Pattern multiplication rules for generative buildings (BIM_Designer.md §10)
-- Lives in validation.db — generative concern, not extraction
-- APPEND-ONLY: never modify this migration after first run

-- ============================================================
-- SCHEMA: ad_pattern_rule
-- ============================================================

CREATE TABLE IF NOT EXISTS ad_pattern_rule (
    id              INTEGER PRIMARY KEY,
    rule_name       TEXT NOT NULL,           -- 'WINDOW_SPACING_EXTERIOR'
    description     TEXT,
    -- Scope: what room/BOM type this applies to
    bom_category    TEXT,                    -- 'BEDROOM', 'KITCHEN', NULL=any
    discipline      TEXT,                    -- 'ARC', 'FPR', 'ELEC', NULL=any
    min_room_area_m2 REAL,                  -- only apply if room >= this area
    -- What to repeat
    child_product_id TEXT NOT NULL,          -- product value: 'WINDOW_STD', 'SPRINKLER_HEAD'
    child_ifc_class  TEXT,                   -- 'IfcWindow', 'IfcFireSuppressionTerminal'
    -- How to repeat
    pattern_type    TEXT NOT NULL,           -- 'LINEAR', 'GRID', 'FIXED_COUNT'
    axis            TEXT,                    -- 'X', 'Y', 'XY' (for GRID), NULL for FIXED_COUNT
    spacing_mm      REAL,                    -- every N mm (NULL for FIXED_COUNT)
    spacing_y_mm    REAL,                    -- Y spacing for GRID type
    margin_start_mm REAL DEFAULT 600,        -- offset from parent origin
    margin_end_mm   REAL DEFAULT 600,        -- stop before parent end
    offset_z_mm     REAL DEFAULT 0,          -- height offset (e.g., below ceiling)
    min_count       INTEGER DEFAULT 1,
    max_count       INTEGER,                 -- NULL = unlimited
    fixed_count     INTEGER,                 -- for FIXED_COUNT type
    alignment       TEXT DEFAULT 'CENTER',   -- 'CENTER', 'START', 'END'
    on_remainder    TEXT DEFAULT 'SKIP',     -- 'SKIP', 'FILL', 'STRETCH_LAST'
    -- Where on the room (for wall-mounted elements)
    wall_filter     TEXT,                    -- 'EXTERIOR', 'INTERIOR', 'NORTH', NULL=any/ceiling
    -- Governance
    jurisdiction    TEXT,                    -- NULL=universal, 'MY'=jurisdiction-specific
    provenance      TEXT DEFAULT 'DESIGNED', -- 'DESIGNED', 'MINED:Terminal'
    is_active       INTEGER DEFAULT 1,
    notes           TEXT
);

-- ============================================================
-- SEED: Residential pattern rules (generative path)
-- ============================================================

-- P1: Windows on exterior walls — every 2500mm, 600mm margin from corners
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline, child_product_id, child_ifc_class,
    pattern_type, axis, spacing_mm, margin_start_mm, margin_end_mm,
    offset_z_mm, min_count, alignment, wall_filter, notes)
VALUES (1, 'WINDOW_EXTERIOR_SPACING',
    'Standard window spacing on exterior walls',
    NULL, 'ARC', 'WINDOW_STD', 'IfcWindow',
    'LINEAR', 'X', 2500, 600, 600,
    900, 1, 'CENTER', 'EXTERIOR',
    'Window sill at 900mm. Count = floor((wall_length - margins) / spacing) + 1');

-- P2: Sprinkler grid — 3000×3000mm grid, centered in room, 150mm below ceiling
-- Only in rooms >= 9m² (small bathrooms exempt)
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline, min_room_area_m2,
    child_product_id, child_ifc_class,
    pattern_type, axis, spacing_mm, spacing_y_mm,
    margin_start_mm, margin_end_mm, offset_z_mm,
    alignment, notes)
VALUES (2, 'SPRINKLER_GRID_LH',
    'Fire sprinkler grid — NFPA 13 Light Hazard spacing',
    NULL, 'FPR', 9.0,
    'SPRINKLER_HEAD', 'IfcFireSuppressionTerminal',
    'GRID', 'XY', 3000, 3000,
    500, 500, -150,
    'CENTER',
    'Below ceiling 150mm. Exempt rooms < 9m². Spacing from NFPA 13 LH (max 4600mm, typical 3000mm)');

-- P3: Light fixture grid — 3000×3000mm grid, centered in room
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline,
    child_product_id, child_ifc_class,
    pattern_type, axis, spacing_mm, spacing_y_mm,
    margin_start_mm, margin_end_mm, offset_z_mm,
    alignment, notes)
VALUES (3, 'LIGHT_GRID_RESIDENTIAL',
    'Ceiling light fixture grid — residential spacing',
    NULL, 'ELEC',
    'LIGHT_RECESSED', 'IfcLightFixture',
    'GRID', 'XY', 3000, 3000,
    500, 500, 0,
    'CENTER',
    'Flush with ceiling. Same grid as sprinklers but on ELEC discipline');

-- P4: Floor slab — one per room, fills parent AABB
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline,
    child_product_id, child_ifc_class,
    pattern_type, fixed_count, notes)
VALUES (4, 'FLOOR_SLAB_PER_ROOM',
    'Floor slab — one per room, fills parent AABB',
    NULL, 'ARC',
    'SLAB_150', 'IfcSlab',
    'FIXED_COUNT', 1,
    'Dimensions from parent AABB. dz=0 (floor level)');

-- P5: Internal door — one per room (except LIVING which has external entry)
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline,
    child_product_id, child_ifc_class,
    pattern_type, fixed_count, wall_filter, notes)
VALUES (5, 'DOOR_INTERNAL_PER_ROOM',
    'Internal door — one per enclosed room',
    NULL, 'ARC',
    'DOOR_D2', 'IfcDoor',
    'FIXED_COUNT', 1, 'INTERIOR',
    'Standard 750mm door. Living room gets DOOR_D1 (900mm) on exterior wall instead');

-- P6: Exterior walls — one per exterior face, parametric length
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline,
    child_product_id, child_ifc_class,
    pattern_type, wall_filter, notes)
VALUES (6, 'WALL_EXTERIOR_PER_FACE',
    'Exterior wall segment — one per exposed room face',
    NULL, 'ARC',
    'WALL_EXT_200', 'IfcWall',
    'FIXED_COUNT', 'EXTERIOR',
    'Parametric length from parent AABB face. Height from storey. 200mm thick');

-- P7: Sprinkler branch piping — connects heads to riser
-- LINEAR along room length, one pipe per sprinkler row
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline, min_room_area_m2,
    child_product_id, child_ifc_class,
    pattern_type, axis, offset_z_mm, notes)
VALUES (7, 'SPRINKLER_BRANCH_PIPE',
    'Fire sprinkler branch pipe — connects head rows',
    NULL, 'FPR', 9.0,
    'PIPE_FPR_25', 'IfcPipeSegment',
    'LINEAR', 'X', -200,
    'One pipe per Y-row of sprinkler grid. Routes along X axis. 200mm below ceiling (above heads)');

-- P8: Electrical conduit — connects light rows
INSERT INTO ad_pattern_rule (id, rule_name, description,
    bom_category, discipline,
    child_product_id, child_ifc_class,
    pattern_type, axis, offset_z_mm, notes)
VALUES (8, 'LIGHT_CONDUIT',
    'Electrical conduit — connects light fixture rows',
    NULL, 'ELEC',
    'CONDUIT_20', 'IfcCableCarrierSegment',
    'LINEAR', 'X', -50,
    'One conduit per Y-row of light grid. 50mm above ceiling (in plenum)');
