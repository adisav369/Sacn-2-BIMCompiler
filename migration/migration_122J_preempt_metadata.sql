-- Phase 122J: Preemptive metadata gaps — all Bucket A inserts
-- Queried reference DBs, found missing vocabulary, inserting before code.

-- =====================================================================
-- 1. FLOOR TYPES — missing slab thicknesses
-- =====================================================================

-- Terminal: 55 slabs at 30mm (finish tiles/flooring)
INSERT OR IGNORE INTO ad_floor_type (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active)
VALUES ('FINISH_TILE_30', 'Finish Floor - Ceramic Tile 30mm', 'TYPICAL', 4.0, 0.03, 1);

-- Terminal: 27 slabs at 50mm (stair landings/thick finish)
INSERT OR IGNORE INTO ad_floor_type (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active)
VALUES ('FINISH_THICK_50', 'Finish Floor - Thick 50mm', 'TYPICAL', 4.0, 0.05, 1);

-- Terminal: 3 slabs at 150mm (structural thin)
-- Already have TYPICAL_CONDO at 150mm — OK

-- Terminal: 4 slabs at ~1160mm (deep transfer beams as slab? or roof structure)
-- These are likely roof structures, not standard slabs — skip for now

-- Terminal: 1 slab at 300mm
INSERT OR IGNORE INTO ad_floor_type (floor_type_id, floor_name, floor_category, floor_height, slab_thickness, is_active)
VALUES ('STRUCTURAL_300', 'Structural Slab 300mm', 'TRANSFER', 4.0, 0.30, 1);

-- Duplex: slabs at 19mm (finish wood), 13mm (finish tile)
-- Already generating finish floors at ~19mm — but Duplex ref has specific sizes
-- The thickness issue is secondary — the DIMENSION mismatch is the real gap

-- =====================================================================
-- 2. WALL TYPES — missing thicknesses for Terminal
-- =====================================================================

-- Terminal ref: 2 walls at 184mm — already have INTERIOR_PLUMBING_152 at 184mm total
-- Wait — INTERIOR_PLUMBING_152 has total_mm=184. So 184mm IS covered. Check dispatch.

-- Terminal ref: 3 walls at 435mm — FOUNDATION_435 exists at 435mm
-- Terminal ref: 2 walls at 493mm — PARTY_CMU at 493mm exists
-- Terminal ref: 2 walls at 550mm — PARTY_CMU_550 at 550mm exists

-- Missing DISPATCH RULES for Malaysian_Institutional profile:
-- Currently only 150mm exterior and 150mm interior rules exist for MY_Institutional
-- Need: AHU room walls at 230mm, and thicker exterior variants

-- Wall dispatch: 230mm AHU room walls (already in ad_wall_type as INTERIOR_MY_AHU_230)
INSERT OR IGNORE INTO ad_wall_type_rule (rule_id, context, space_type_a, wall_type_id, priority, description, is_active, profile)
VALUES (30, 'INTERIOR', 'AHU_ROOM', 'INTERIOR_MY_AHU_230', 50, 'AHU plant room: 230mm brick for acoustic isolation', 1, 'Malaysian_Institutional');

-- Wall dispatch: 250mm exterior walls (some Terminal storeys)
INSERT OR IGNORE INTO ad_wall_type_rule (rule_id, context, wall_type_id, priority, description, is_active, profile)
VALUES (31, 'EXTERIOR', 'EXTERIOR_MY_BRICK_250', 40, 'MY institutional exterior variant: 250mm brick', 1, 'Malaysian_Institutional');

-- =====================================================================
-- 3. OPENING FAMILIES — missing Terminal door/window sizes
-- =====================================================================

-- Terminal: 6 doors at 1850x3431mm (tall glass entrance doors, 209mm depth)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_GLASS_ENTRANCE_1850', 'MY Glass Entrance Door 1850mm', 'DOOR', 'IfcDoor', 1850, 3431, 209, 1);

-- Terminal: 5 doors at 1464x2290mm (wide fire-rated double door, 371mm depth)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_FIRE_DOUBLE_1464', 'MY Fire Double Door 1464mm', 'DOOR', 'IfcDoor', 1464, 2290, 371, 1);

-- Terminal: 2 doors at 1700x2000mm (147mm depth — service door)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_SERVICE_DOOR_1700', 'MY Service Door 1700mm', 'DOOR', 'IfcDoor', 1700, 2000, 147, 1);

-- Terminal: 2 doors at 1750x2450mm (192mm depth — tall service door)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_TALL_SERVICE_1750', 'MY Tall Service Door 1750mm', 'DOOR', 'IfcDoor', 1750, 2450, 192, 1);

-- Terminal: 2 doors at 1700x2000mm (185mm depth variant)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_SERVICE_DOOR_1700B', 'MY Service Door 1700mm (185)', 'DOOR', 'IfcDoor', 1700, 2000, 185, 1);

-- Terminal: 1 door at 840x2020mm (245mm depth — heavy duty)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_HEAVY_DOOR_840', 'MY Heavy Duty Door 840mm', 'DOOR', 'IfcDoor', 840, 2020, 245, 1);

-- Terminal windows: 24 at 1312x3974mm (curtain wall tall variant)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_CURTAIN_TALL_3974', 'MY Curtain Wall Window 3974mm', 'WINDOW', 'IfcWindow', 1312, 3974, 223, 1);

-- Terminal windows: 12 at 1312x4349mm (curtain wall extra tall)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_CURTAIN_XTALL_4349', 'MY Curtain Wall Window 4349mm', 'WINDOW', 'IfcWindow', 1312, 4349, 223, 1);

-- Terminal windows: 12 at 1312x4849mm (curtain wall tallest)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_CURTAIN_TALLEST_4849', 'MY Curtain Wall Window 4849mm', 'WINDOW', 'IfcWindow', 1312, 4849, 223, 1);

-- Terminal windows: 12 at 1250x3100mm (large standard)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_3100', 'MY Window 1250x3100mm', 'WINDOW', 'IfcWindow', 1250, 3100, 150, 1);

-- Terminal windows: 10 at 1220x1920mm (medium)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_1920', 'MY Window 1220x1920mm', 'WINDOW', 'IfcWindow', 1220, 1920, 150, 1);

-- Terminal windows: 8 at 1250x3400mm (tall, 135mm depth)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_3400', 'MY Window 1250x3400mm', 'WINDOW', 'IfcWindow', 1250, 3400, 135, 1);

-- Terminal windows: 7 at 1390x3527mm (curtain variant)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_CURTAIN_3527', 'MY Curtain Wall 1390x3527mm', 'WINDOW', 'IfcWindow', 1390, 3527, 223, 1);

-- Terminal windows: 6 at 1250x1300mm (small high)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_1300', 'MY Window 1250x1300mm', 'WINDOW', 'IfcWindow', 1250, 1300, 150, 1);

-- Terminal windows: 6 at 1250x2200mm
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_2200', 'MY Window 1250x2200mm', 'WINDOW', 'IfcWindow', 1250, 2200, 150, 1);

-- Terminal windows: 6 at 2499x1200mm (wide low — maybe clerestory)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_CLERESTORY_2499', 'MY Clerestory Window 2499x1200mm', 'WINDOW', 'IfcWindow', 2499, 1200, 150, 1);

-- Terminal windows: 6 at 2562x3449mm (classroom double-wide)
-- Already exists as Classroom Window 2562x3449 — OK

-- Terminal windows: 4 at 2500x3450mm (135mm depth variant)
INSERT OR IGNORE INTO ad_opening_family (family_id, family_name, opening_type, ifc_class, default_width_mm, default_height_mm, depth_mm, is_active)
VALUES ('MY_WINDOW_WIDE_3450', 'MY Wide Window 2500x3450mm', 'WINDOW', 'IfcWindow', 2500, 3450, 135, 1);

-- =====================================================================
-- 4. SPACE TYPE OPENING — dispatch rules for Terminal rooms
-- =====================================================================

-- Tall glass entrance for LOBBY
INSERT OR IGNORE INTO ad_space_type_opening (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, profile, priority)
VALUES ('LOBBY', 'ENTRY', 'MY_GLASS_ENTRANCE_1850', 'FIXED', 1, 'exterior', 'Malaysian_Institutional', 50);

-- Fire double doors for STAIR
INSERT OR IGNORE INTO ad_space_type_opening (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, profile, priority)
VALUES ('STAIR', 'EGRESS', 'MY_FIRE_DOUBLE_1464', 'FIXED', 1, 'interior', 'Malaysian_Institutional', 50);

-- Curtain wall tall variants for OFFICE rooms
INSERT OR IGNORE INTO ad_space_type_opening (space_type_id, opening_role, family_id, qty_rule, qty_base, placement_wall, profile, priority)
VALUES ('OFFICE', 'NATURAL_LIGHT', 'MY_CURTAIN_TALL_3974', 'PER_EXTERIOR_WALL', 1, 'exterior', 'Malaysian_Institutional', 40);

-- =====================================================================
-- 5. COVERING TYPE — new table (Bucket C)
-- =====================================================================

CREATE TABLE IF NOT EXISTS ad_covering_type (
    covering_type_id   TEXT PRIMARY KEY,
    covering_name      TEXT NOT NULL,
    covering_category  TEXT NOT NULL,       -- CEILING, FLOOR_FINISH, WALL_FINISH, INSULATION
    ifc_class          TEXT DEFAULT 'IfcCovering',
    thickness_mm       INTEGER NOT NULL,
    material           TEXT,
    profile            TEXT,
    is_active          INTEGER DEFAULT 1
);

-- Terminal coverings: ALL 82 at 16mm — ceiling tiles
INSERT OR IGNORE INTO ad_covering_type (covering_type_id, covering_name, covering_category, thickness_mm, material, profile)
VALUES ('MY_CEILING_TILE_16', 'Ceiling Tile 16mm', 'CEILING', 16, 'Mineral Fiber', 'Malaysian_Institutional');

-- Generic ceiling tile for other profiles
INSERT OR IGNORE INTO ad_covering_type (covering_type_id, covering_name, covering_category, thickness_mm, material)
VALUES ('CEILING_TILE_16', 'Ceiling Tile 16mm', 'CEILING', 16, 'Mineral Fiber');

-- Duplex: no IfcCovering in reference — skip

-- =====================================================================
-- 6. RAILING TYPE — new table (Bucket C)
-- =====================================================================

CREATE TABLE IF NOT EXISTS ad_railing_type (
    railing_type_id    TEXT PRIMARY KEY,
    railing_name       TEXT NOT NULL,
    railing_category   TEXT NOT NULL,       -- STAIR_GUARD, BALCONY, WALL_GUARD, HANDRAIL
    ifc_class          TEXT DEFAULT 'IfcRailing',
    height_mm          INTEGER NOT NULL,    -- railing height (typically 1000-1100mm)
    width_mm           INTEGER DEFAULT 50,  -- railing thickness/depth
    host_type          TEXT,                -- STAIR, SLAB_EDGE, WALL_TOP
    material           TEXT,
    profile            TEXT,
    is_active          INTEGER DEFAULT 1
);

-- Terminal railings: stairwell guard rails — 1100mm height, hosted on stairs
INSERT OR IGNORE INTO ad_railing_type (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile)
VALUES ('MY_STAIR_GUARD', 'Stairwell Guard Rail', 'STAIR_GUARD', 1100, 50, 'STAIR', 'Steel', 'Malaysian_Institutional');

-- Terminal railings: balcony/gallery railings
INSERT OR IGNORE INTO ad_railing_type (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile)
VALUES ('MY_GALLERY_GUARD', 'Gallery Guard Rail', 'STAIR_GUARD', 1100, 50, 'SLAB_EDGE', 'Steel', 'Malaysian_Institutional');

-- Duplex railings: 4 at various sizes — stair handrails
INSERT OR IGNORE INTO ad_railing_type (railing_type_id, railing_name, railing_category, height_mm, width_mm, host_type, material, profile)
VALUES ('US_STAIR_HANDRAIL', 'Stair Handrail', 'HANDRAIL', 900, 50, 'STAIR', 'Wood', 'US_Residential');
