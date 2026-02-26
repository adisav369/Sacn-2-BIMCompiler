-- ============================================================
-- Phase 4 — product_ref FK on ad_bom_child
-- Date: 2026-02-23
-- Governs: VIEW_CONTRACTS.md §5.1 (v_qualified_bom)
--
-- Decision: Option A (Watchdog approved) — explicit FK, not LIKE bbox path.
-- iDempiere analogue: C_BOM_Line.M_Product_ID
--
-- Scope of seeding: ONLY rows where child_name_pattern currently resolves
-- through the full chain at runtime:
--   child_name_pattern LIKE → component_definitions.name → ad_product_dim.product_id (exact)
-- This is 10 rows. All other bom_child rows stay NULL — either:
--   (a) empty pattern (sub-assembly dispatch, not leaf product)
--   (b) pattern resolves to cd but cd.name ∉ ad_product_dim.product_id
--       (needs new pd entries — separate session)
--
-- NULL rows are correct, not an error. The view returns no row for them —
-- that is an honest gate. Wrong dimensions are worse than zero rows.
--
-- Patterns confirmed NULL (no pd match):
--   DUPLEX_BATHROOM_SET: BASIN(Lavatory_%), BATHING(Bath_%/Shower_%), TOILET(WC_%)
--   KITCHEN_CABINET_SET: SINK(Sink_Island%) — cd=Sink_Island_Single_456x455, pd=Sink_Island (mismatch)
--   TOILET_BLOCK_FIXTURES: TOILET(Toilet%), SINK(MRV basic round sink), HAND_BIDET, FLOOR_TRAP, EXHAUST_FAN
--   T_CONNECTOR_ASSEMBLY: DROP_PIPE, TEE, TRANSITION — no FP pipe entries in ad_product_dim
--   WORKSTATION_SET: DESK(Desk_with_return%), MONITOR(Apple_iMac%), CHAIR(Chair%Desk%)
--   LOBBY_SEAT_SET, ROOM_FURNITURE: GUEST_SEAT(Waiting_Room_Seat%)
--   WATER_TANK_ASSEMBLY: VESSEL(Water_Tank_FRP_%)
--   SPRINKLER_PENDANT_ASSEMBLY: SPRINKLER_HEAD(FP_Sprinkler_Head_Pendent)
-- ============================================================

-- ══════════════════════════════════════════════════════════════
-- Step 1 — Add product_ref column (nullable FK)
-- ══════════════════════════════════════════════════════════════

ALTER TABLE ad_bom_child
ADD COLUMN product_ref TEXT REFERENCES ad_product_dim(product_id);

-- Verify column added
SELECT 'Step 1 VERIFY — product_ref column:';
SELECT COUNT(*) FROM pragma_table_info('ad_bom_child') WHERE name = 'product_ref';

-- ══════════════════════════════════════════════════════════════
-- Step 2 — Seed confirmed pattern→cd→pd chain (10 rows)
-- Only exact chains that FurnitureBOMResolver walks at runtime.
-- Written as explicit VALUES — no LIKE in the seed.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

-- Tall_Cabinet% → Tall_Cabinet (bom_child_ids 92, 93, 69, 70)
UPDATE ad_bom_child SET product_ref = 'Tall_Cabinet'
WHERE child_name_pattern LIKE 'Tall_Cabinet%' AND is_active = 1;

-- Vanity_Cabinet% → Vanity_Cabinet (bom_child_ids 94, 95, 96)
UPDATE ad_bom_child SET product_ref = 'Vanity_Cabinet'
WHERE child_name_pattern LIKE 'Vanity_Cabinet%' AND is_active = 1;

-- Base_Cabinet% → Base_Cabinet (bom_child_id 81)
UPDATE ad_bom_child SET product_ref = 'Base_Cabinet'
WHERE child_name_pattern LIKE 'Base_Cabinet%' AND is_active = 1;

-- Upper_Cabinet% → Upper_Cabinet (bom_child_id 82)
UPDATE ad_bom_child SET product_ref = 'Upper_Cabinet'
WHERE child_name_pattern LIKE 'Upper_Cabinet%' AND is_active = 1;

-- Counter_Top% → Counter_Top (bom_child_id 83)
UPDATE ad_bom_child SET product_ref = 'Counter_Top'
WHERE child_name_pattern LIKE 'Counter_Top%' AND is_active = 1;

COMMIT;

-- Verify Step 2
SELECT 'Step 2 VERIFY — seeded product_ref rows (expect 10):';
SELECT bom_id, role, child_name_pattern, product_ref
FROM ad_bom_child
WHERE product_ref IS NOT NULL AND is_active = 1
ORDER BY bom_id, role;

SELECT 'Step 2 VERIFY — seeded count:';
SELECT COUNT(*) FROM ad_bom_child WHERE product_ref IS NOT NULL AND is_active = 1;

-- ══════════════════════════════════════════════════════════════
-- Step 3 — Rebuild v_qualified_bom with product_ref FK join
-- ══════════════════════════════════════════════════════════════

DROP VIEW IF EXISTS v_qualified_bom;
CREATE VIEW v_qualified_bom AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    bc.bom_child_id,
    bc.role,
    bc.sequence,
    bc.fit_priority,
    bc.min_space_mm,
    bc.child_name_pattern,
    pd.width            AS width_mm,
    pd.depth            AS depth_mm,
    pd.height           AS height_mm,
    pd.extracted_from
FROM ad_bom b
JOIN ad_bom_child bc
    ON bc.bom_id = b.bom_id
    AND bc.is_active = 1
JOIN ad_product_dim pd
    ON pd.product_id = bc.product_ref       -- FK join: product_ref not role
    AND pd.width > 0
    AND pd.depth > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE b.is_active = 1;

-- Verify Step 3
SELECT 'Step 3 VERIFY — v_qualified_bom row count (expect > 0):';
SELECT COUNT(*) FROM v_qualified_bom;

SELECT 'Step 3 VERIFY — spot-check width/depth/height from ad_product_dim:';
SELECT bom_id, role, width_mm, depth_mm, height_mm FROM v_qualified_bom
ORDER BY bom_id, role;
