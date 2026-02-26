-- Phase 122J: Convert finish slab from hardcoded switch to library lookup (Pattern A)
-- Previously getFinishSlabThickness() was a hardcoded switch in BuildingWriter.
-- Now: ad_floor_type + ad_floor_type_rule = profile-aware finish slab dispatch.

-- =====================================================================
-- 1. Add profile column to ad_floor_type
-- =====================================================================
-- SQLite ALTER TABLE only supports ADD COLUMN, not modify.
-- Check if column exists first via pragma, but ALTER is idempotent if column name is unique.

ALTER TABLE ad_floor_type ADD COLUMN profile TEXT;

-- =====================================================================
-- 2. Add finish floor type entries (missing from original table)
-- =====================================================================

-- US/UK: 13mm ceramic tile for wet rooms
INSERT OR IGNORE INTO ad_floor_type (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
VALUES ('FINISH_CERAMIC_13', 'Finish Floor - Ceramic Tile 13mm', 'FINISH', 4.0, 0.013, 1, NULL);

-- US/UK: 19mm hardwood for dry rooms
INSERT OR IGNORE INTO ad_floor_type (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active, profile)
VALUES ('FINISH_WOOD_19', 'Finish Floor - Hardwood 19mm', 'FINISH', 4.0, 0.019, 1, NULL);

-- Tag existing entries with profile where appropriate
UPDATE ad_floor_type SET profile = 'Malaysian_Institutional' WHERE floor_type_id = 'FINISH_TILE_30';
UPDATE ad_floor_type SET profile = 'Malaysian_Institutional' WHERE floor_type_id = 'FINISH_THICK_50';

-- =====================================================================
-- 3. Create floor type dispatch rule table (mirrors ad_wall_type_rule)
-- =====================================================================

CREATE TABLE IF NOT EXISTS ad_floor_type_rule (
    rule_id         INTEGER PRIMARY KEY,
    room_type       TEXT NOT NULL,
    floor_type_id   TEXT NOT NULL,
    priority        INTEGER DEFAULT 50,
    profile         TEXT,               -- NULL = generic fallback
    is_active       INTEGER DEFAULT 1
);

-- =====================================================================
-- 4. US_Residential finish floor rules (from Duplex Stone)
-- =====================================================================

-- Wet rooms: 13mm ceramic tile
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (1, 'BATHROOM', 'FINISH_CERAMIC_13', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (2, 'TOILET', 'FINISH_CERAMIC_13', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (3, 'KITCHEN', 'FINISH_CERAMIC_13', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (4, 'TOILET_BLOCK', 'FINISH_CERAMIC_13', 50, 'US_Residential', 1);

-- Dry rooms: 19mm hardwood
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (5, 'BEDROOM', 'FINISH_WOOD_19', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (6, 'LIVING', 'FINISH_WOOD_19', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (7, 'CORRIDOR', 'FINISH_WOOD_19', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (8, 'STUDY', 'FINISH_WOOD_19', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (9, 'DINING', 'FINISH_WOOD_19', 50, 'US_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (10, 'OFFICE', 'FINISH_WOOD_19', 50, 'US_Residential', 1);

-- =====================================================================
-- 5. Malaysian_Institutional finish floor rules (from Terminal Stone)
--    Stone says: 55 slabs at 30mm — ceramic tile everywhere
-- =====================================================================

INSERT OR IGNORE INTO ad_floor_type_rule VALUES (20, 'OFFICE', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (21, 'LOBBY', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (22, 'CORRIDOR', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (23, 'DINING', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (24, 'STAFFROOM', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (25, 'OPEN_PLAN', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (26, 'TOILET_BLOCK', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (27, 'BATHROOM', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (28, 'KITCHEN', 'FINISH_TILE_30', 50, 'Malaysian_Institutional', 1);

-- =====================================================================
-- 6. UK_Residential finish floor rules (from SampleHouse Stone)
--    Same as US for now — SampleHouse ref has ~19mm finish slabs
-- =====================================================================

INSERT OR IGNORE INTO ad_floor_type_rule VALUES (40, 'BATHROOM', 'FINISH_CERAMIC_13', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (41, 'KITCHEN', 'FINISH_CERAMIC_13', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (42, 'BEDROOM', 'FINISH_WOOD_19', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (43, 'LIVING', 'FINISH_WOOD_19', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (44, 'CORRIDOR', 'FINISH_WOOD_19', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (45, 'STUDY', 'FINISH_WOOD_19', 50, 'UK_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (46, 'DINING', 'FINISH_WOOD_19', 50, 'UK_Residential', 1);

-- =====================================================================
-- 7. Malaysian_Residential finish floor rules (TBLKTN, condo)
-- =====================================================================

INSERT OR IGNORE INTO ad_floor_type_rule VALUES (60, 'BATHROOM', 'FINISH_CERAMIC_13', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (61, 'TOILET', 'FINISH_CERAMIC_13', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (62, 'KITCHEN', 'FINISH_CERAMIC_13', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (63, 'BEDROOM', 'FINISH_WOOD_19', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (64, 'LIVING', 'FINISH_WOOD_19', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (65, 'CORRIDOR', 'FINISH_WOOD_19', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (66, 'STUDY', 'FINISH_WOOD_19', 50, 'Malaysian_Residential', 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (67, 'DINING', 'FINISH_WOOD_19', 50, 'Malaysian_Residential', 1);

-- =====================================================================
-- 8. Generic fallback rules (profile IS NULL — any profile without specific rules)
-- =====================================================================

INSERT OR IGNORE INTO ad_floor_type_rule VALUES (80, 'BATHROOM', 'FINISH_CERAMIC_13', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (81, 'TOILET', 'FINISH_CERAMIC_13', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (82, 'TOILET_BLOCK', 'FINISH_CERAMIC_13', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (83, 'KITCHEN', 'FINISH_CERAMIC_13', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (84, 'BEDROOM', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (85, 'LIVING', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (86, 'CORRIDOR', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (87, 'OFFICE', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (88, 'LOBBY', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (89, 'DINING', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (90, 'STUDY', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (91, 'STAFFROOM', 'FINISH_WOOD_19', 10, NULL, 1);
INSERT OR IGNORE INTO ad_floor_type_rule VALUES (92, 'OPEN_PLAN', 'FINISH_WOOD_19', 10, NULL, 1);
