-- ============================================================
-- Migration: HW-5 SH — Structured BOM Population (SH only)
--
-- Populates UNIT_SH_STD from EXTRACTED BOM data (EXT_SH),
-- grouped by storey into 3 sub-BOMs.
--
-- Strategy:
--   1. Set UNIT_SH_STD origin to match EXT_SH origin
--   2. Deactivate old MAKE lines under UNIT_SH_STD
--   3. Create storey-level sub-BOMs (origin 0,0,0)
--   4. INSERT...SELECT BUY lines from EXT_SH → storey sub-BOMs
--
-- Because UNIT origin matches EXTRACTED origin and all intermediate
-- offsets are zero, BUY leaf dx/dy/dz values transfer directly.
-- The BOMWalker computes identical world positions for both paths:
--   world = UNIT_origin + MAKE_offset(0) + BOM_origin(0) + BUY_dx
--         = EXT_origin + BUY_dx   (same formula)
--
-- DX is NOT included — the duplex requires MIRROR verb support
-- in PlacementCollectorVisitor before its structured BOM can work.
-- See derivation: 444 paired + 211 unpaired = 1099. Existing
-- DUPLEX_SET_STD already has UNIT_A(rot=0) + UNIT_B(rot=pi)
-- but BOMWalker does not apply rotation_rule to coordinates yet.
--
-- Idempotent: INSERT OR IGNORE for BOMs and products. BUY line
-- INSERT is additive — rerun requires DELETE of new lines first.
--
-- Gate: run_RosettaStones.sh _e must match _s for SH.
-- ============================================================

BEGIN TRANSACTION;

-- ============================================================
-- Step 1: Set UNIT_SH_STD origin to match EXT_SH origin
-- ============================================================

UPDATE m_bom
SET origin_x = (SELECT origin_x FROM m_bom WHERE bom_id = 'EXT_SH'),
    origin_y = (SELECT origin_y FROM m_bom WHERE bom_id = 'EXT_SH'),
    origin_z = (SELECT origin_z FROM m_bom WHERE bom_id = 'EXT_SH')
WHERE bom_id = 'UNIT_SH_STD';

-- ============================================================
-- Step 2: Deactivate existing MAKE lines under UNIT_SH_STD
--
-- Only deactivates the UNIT-level MAKE references.
-- The child BOMs (FLOOR_SH_GF_STD, SH_LIVING_SET, etc.) and
-- their contents remain intact — just not walked.
-- ============================================================

UPDATE m_bom_line SET is_active = 0 WHERE bom_id = 'UNIT_SH_STD';

-- ============================================================
-- Step 3: Create M_Product entries for new assembly BOMs
-- (Assembly pattern: is_active=0, dims=0.001, BOM_ASSEMBLY)
-- ============================================================

INSERT OR IGNORE INTO M_Product
    (product_id, product_type, width, depth, height, extracted_from, is_active, M_Product_Category_ID)
VALUES
    ('SH_GF_STR',      'FLOOR', 0.001, 0.001, 0.001, 'BOM_ASSEMBLY', 0, 'ASM_FLOOR'),
    ('SH_ROOF_STR',    'FLOOR', 0.001, 0.001, 0.001, 'BOM_ASSEMBLY', 0, 'ASM_FLOOR'),
    ('SH_CW_STR',      'FLOOR', 0.001, 0.001, 0.001, 'BOM_ASSEMBLY', 0, 'ASM_FLOOR');

-- ============================================================
-- Step 4: Create storey sub-BOMs (all origin 0,0,0)
-- ============================================================

INSERT OR IGNORE INTO m_bom
    (bom_id, bom_name, group_by, bom_type, bom_level, is_active)
VALUES
    ('SH_GF_STR',      'SH Ground Floor Structured',   'STOREY', 'FLOOR', 'FLOOR', 1),
    ('SH_ROOF_STR',    'SH Roof Structured',           'STOREY', 'FLOOR', 'FLOOR', 1),
    ('SH_CW_STR',      'SH Curtain Wall Structured',   'STOREY', 'FLOOR', 'FLOOR', 1);

-- ============================================================
-- Step 5: Add MAKE lines under UNIT_SH_STD for new sub-BOMs
-- (All with dx=0, dy=0, dz=0 — offsets are in the BUY leaves)
-- ============================================================

INSERT INTO m_bom_line (bom_id, child_product_id, component_type, role, is_active, sequence)
VALUES
    ('UNIT_SH_STD',     'SH_GF_STR',      'MAKE', 'GROUND_FLOOR', 1, 10),
    ('UNIT_SH_STD',     'SH_ROOF_STR',    'MAKE', 'ROOF',         1, 20),
    ('UNIT_SH_STD',     'SH_CW_STR',      'MAKE', 'CURTAIN_WALL', 1, 30);

-- ============================================================
-- Step 6: Copy BUY lines from EXT_SH into storey sub-BOMs
--
-- Each INSERT...SELECT copies all columns needed for compilation:
--   child_product_id, role, dx/dy/dz, allocated dims, rotation,
--   storey, element_ref, ordinal, orientation, material.
-- ============================================================

-- --- SH Ground Floor (27 elements) ---
INSERT INTO m_bom_line
    (bom_id, child_product_id, component_type, role,
     dx, dy, dz,
     allocated_width_mm, allocated_depth_mm, allocated_height_mm,
     rotation_rule, storey, element_ref, ordinal, orientation,
     material_name, material_rgba, is_active, sequence, qty_type)
SELECT
    'SH_GF_STR', child_product_id, 'BUY', role,
    dx, dy, dz,
    allocated_width_mm, allocated_depth_mm, allocated_height_mm,
    rotation_rule, storey, element_ref, ordinal, orientation,
    material_name, material_rgba, 1, sequence, 'VARIABLE'
FROM m_bom_line
WHERE bom_id = 'EXT_SH' AND storey = 'Ground Floor' AND is_active = 1;

-- --- SH Roof (2 elements) ---
INSERT INTO m_bom_line
    (bom_id, child_product_id, component_type, role,
     dx, dy, dz,
     allocated_width_mm, allocated_depth_mm, allocated_height_mm,
     rotation_rule, storey, element_ref, ordinal, orientation,
     material_name, material_rgba, is_active, sequence, qty_type)
SELECT
    'SH_ROOF_STR', child_product_id, 'BUY', role,
    dx, dy, dz,
    allocated_width_mm, allocated_depth_mm, allocated_height_mm,
    rotation_rule, storey, element_ref, ordinal, orientation,
    material_name, material_rgba, 1, sequence, 'VARIABLE'
FROM m_bom_line
WHERE bom_id = 'EXT_SH' AND storey = 'Roof' AND is_active = 1;

-- --- SH Curtain Wall / Unknown (26 elements) ---
INSERT INTO m_bom_line
    (bom_id, child_product_id, component_type, role,
     dx, dy, dz,
     allocated_width_mm, allocated_depth_mm, allocated_height_mm,
     rotation_rule, storey, element_ref, ordinal, orientation,
     material_name, material_rgba, is_active, sequence, qty_type)
SELECT
    'SH_CW_STR', child_product_id, 'BUY', role,
    dx, dy, dz,
    allocated_width_mm, allocated_depth_mm, allocated_height_mm,
    rotation_rule, storey, element_ref, ordinal, orientation,
    material_name, material_rgba, 1, sequence, 'VARIABLE'
FROM m_bom_line
WHERE bom_id = 'EXT_SH' AND storey = 'Unknown' AND is_active = 1;

-- ============================================================
-- Verification queries (run after migration)
-- ============================================================
-- Expected counts:
--   SH_GF_STR:     27
--   SH_ROOF_STR:    2
--   SH_CW_STR:     26
--   Total: 55
--
-- SELECT bom_id, COUNT(*) FROM m_bom_line
-- WHERE bom_id LIKE 'SH_%_STR' AND component_type = 'BUY' AND is_active = 1
-- GROUP BY bom_id ORDER BY bom_id;
--
-- SELECT COUNT(*) FROM m_bom_line
-- WHERE bom_id = 'UNIT_SH_STD' AND is_active = 1;
-- Expected: 3 (new MAKE lines for SH_GF_STR, SH_ROOF_STR, SH_CW_STR)

COMMIT;
