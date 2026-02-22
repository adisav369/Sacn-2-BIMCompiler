-- ============================================================
-- Seed product_ref for DINING_SET and LIVING_SET BOM children
-- Date: 2026-02-23
--
-- Context: Phase 4 seeded product_ref for 10 cabinet/storage rows.
-- DINING_SET and LIVING_SET children had no child_name_pattern and
-- no product_ref — compiler fell through to bbox fallback silently.
-- These are the primary living-area assemblies dispatched by ad_room_slot
-- for LIVING rooms in SH and DX.
--
-- Mapping confirmed from compiled output (ifc4_sample_house.db):
--   TABLE       → Dining_Table_With_Chairs (1.5×0.9×0.75m)
--   CHAIR_*     → Dining_Chair (0.45×0.45×0.45m)
--   SOFA        → Sofa (2.0×0.8×0.45m) — anchored at zone centre
--   SOFA_B      → Sofa_Loveseat (1.6×0.8×0.45m) — dx=-1.5 offset
--   COFFEE_TABLE→ Coffee_Table (1.0×0.6×0.45m)
--   SIDE_TABLE_*→ Side_Table_Cube_610 (0.61×0.61×0.61m)
--   PIANO       → Piano (1.371×0.6×1.17m)
--
-- iDempiere analogue: C_BOM_Line.M_Product_ID — exact product FK
-- ============================================================

BEGIN TRANSACTION;

-- ══════════════════════════════════════════════════════════════
-- 1. DINING_SET
-- ══════════════════════════════════════════════════════════════

UPDATE ad_bom_child SET product_ref = 'Dining_Table_With_Chairs'
WHERE bom_id = 'DINING_SET' AND role = 'TABLE' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Dining_Chair'
WHERE bom_id = 'DINING_SET'
  AND role IN ('CHAIR_A','CHAIR_B','CHAIR_C','CHAIR_D','CHAIR_E','CHAIR_F')
  AND is_active = 1;

-- ══════════════════════════════════════════════════════════════
-- 2. LIVING_SET
-- ══════════════════════════════════════════════════════════════

UPDATE ad_bom_child SET product_ref = 'Sofa'
WHERE bom_id = 'LIVING_SET' AND role = 'SOFA' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Sofa_Loveseat'
WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Coffee_Table'
WHERE bom_id = 'LIVING_SET' AND role = 'COFFEE_TABLE' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Side_Table_Cube_610'
WHERE bom_id = 'LIVING_SET' AND role IN ('SIDE_TABLE_A','SIDE_TABLE_B') AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Piano'
WHERE bom_id = 'LIVING_SET' AND role = 'PIANO' AND is_active = 1;

COMMIT;

-- ── Verification ──────────────────────────────────────────────
SELECT 'DINING_SET product_ref:';
SELECT role, product_ref, dx, dy
FROM ad_bom_child
WHERE bom_id = 'DINING_SET' AND is_active = 1
ORDER BY sequence;

SELECT 'LIVING_SET product_ref:';
SELECT role, product_ref, dx, dy, rotation_rule
FROM ad_bom_child
WHERE bom_id = 'LIVING_SET' AND is_active = 1
ORDER BY sequence;

SELECT 'v_qualified_bom row count (expect > 10):';
SELECT COUNT(*) FROM v_qualified_bom;

SELECT 'v_qualified_bom — new DINING/LIVING rows:';
SELECT bom_id, role, width_mm, depth_mm, height_mm, fit_priority
FROM v_qualified_bom
WHERE bom_id IN ('DINING_SET','LIVING_SET')
ORDER BY bom_id, sequence;
