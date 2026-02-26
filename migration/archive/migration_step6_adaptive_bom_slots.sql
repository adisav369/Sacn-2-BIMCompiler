-- Phase RM-11 Step 6: Size-adaptive BOM slot selection
-- Strategy: ad_room_slot.min_area threshold — largest-fitting BOM wins per slot.
-- SH/DX room arrangements ARE the canonical full-size first candidates.
-- Smaller rooms fall through to child BOMs via lower min_area thresholds.
--
-- UNIQUE(room_type, slot_name, profile) — multiple candidates use distinct profile values.
-- Algorithm: among all candidates where min_area <= roomArea, pick highest min_area.
-- Null profile = generic fallback (min_area=0 = always fits, any room size).

-- ─── Step 1: Add min_area column ─────────────────────────────────────────────
ALTER TABLE ad_room_slot ADD COLUMN min_area REAL DEFAULT 0.0;

-- ─── Step 2: BEDROOM — size-adaptive cascade ─────────────────────────────────
-- profile='MASTER' candidate (min_area=12.0): rooms ≥ 12m² → king bed + wardrobe
-- profile=NULL (existing, min_area=0.0): rooms < 12m² → queen bed + desk + cabinets
INSERT OR IGNORE INTO ad_room_slot
    (room_type, slot_name, profile, assembly_id, slot_face, slot_priority, is_required, min_area)
VALUES
    ('BEDROOM', 'FURNITURE', 'MASTER', 'BED_SET_MASTER', 'BACK', 5, 0, 12.0);

-- ─── Step 3: LIVING — LIVING_SET for large rooms (≥ 15m²) ───────────────────
-- LIVING already has LIVING_SET (slot FURNITURE, priority 10) + DINING_SET (slot DINING, priority 20).
-- That covers the combined living+dining case for rooms like TB-LKTN common (22.94m²).
-- No structural change needed — both slots already dispatch independently.
-- Set explicit min_area on DINING slot so DINING_SET only appears in rooms large enough for a table.
UPDATE ad_room_slot
SET min_area = 6.0
WHERE room_type = 'LIVING' AND slot_name = 'DINING' AND profile IS NULL;

-- ─── Step 4: COMMON room type (combined living+dining area, e.g. open-plan) ──
-- For buildings that declare a COMMON room type (not LIVING+separate KITCHEN).
-- Large COMMON (≥ 18m²) → LIVING_SET (sofa zone); DINING_SET (dining zone, min_area=8m²)
INSERT OR IGNORE INTO ad_room_slot
    (room_type, slot_name, profile, assembly_id, slot_face, slot_priority, is_required, min_area)
VALUES
    ('COMMON', 'FURNITURE', NULL, 'LIVING_SET',  'BACK',  10, 0, 15.0),
    ('COMMON', 'DINING',    NULL, 'DINING_SET',  'FRONT', 20, 0,  8.0);

-- ─── Verification queries ─────────────────────────────────────────────────────
-- Check new BEDROOM MASTER row:
-- SELECT * FROM ad_room_slot WHERE room_type='BEDROOM' ORDER BY min_area DESC;
-- Expected: profile='MASTER' (min_area=12.0) + profile=NULL (min_area=0.0)
--
-- Check COMMON type:
-- SELECT * FROM ad_room_slot WHERE room_type='COMMON' ORDER BY slot_priority;
-- Expected: 2 rows — LIVING_SET (15.0) + DINING_SET (8.0)
