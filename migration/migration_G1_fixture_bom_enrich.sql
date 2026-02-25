-- ============================================================================
-- Phase G-1: Enrich DUPLEX_BATHROOM_SET with fixture placement params
--
-- DUPLEX_BATHROOM_SET children (85-88) currently have zero m_attribute params
-- and dx/dy/dz=0. This migration adds fixture-style placement params so the
-- unified BOMTierResolver can place them via the fixture-param dispatch path.
--
-- Also sets product_ref on children that lack it, pointing to ad_product_dim.
--
-- Run: sqlite3 library/BOM.db < migration/migration_G1_fixture_bom_enrich.sql
-- Idempotent: uses INSERT OR REPLACE on (bom_child_id, param_key) uniqueness.
-- ============================================================================

-- ── 1. Set product_ref + SpaceSize on DUPLEX_BATHROOM_SET children ─────────
-- SpaceSize from ad_product_dim (mm): TOILET 400×700×400, SINK 500×450×200, SHOWER 900×900×2100

UPDATE m_bom_line SET product_ref = 'FIXTURE_TOILET',
    space_width_mm = 400, space_depth_mm = 700, space_height_mm = 400
WHERE bom_child_id = 85 AND bom_id = 'DUPLEX_BATHROOM_SET';

UPDATE m_bom_line SET product_ref = 'FIXTURE_SINK',
    space_width_mm = 500, space_depth_mm = 450, space_height_mm = 200
WHERE bom_child_id = 86 AND bom_id = 'DUPLEX_BATHROOM_SET';

-- Bath — use FIXTURE_SHOWER dims as proxy (no dedicated bathtub product yet)
UPDATE m_bom_line SET product_ref = 'FIXTURE_SHOWER',
    space_width_mm = 900, space_depth_mm = 900, space_height_mm = 2100
WHERE bom_child_id = 87 AND bom_id = 'DUPLEX_BATHROOM_SET';

UPDATE m_bom_line SET product_ref = 'FIXTURE_SHOWER',
    space_width_mm = 900, space_depth_mm = 900, space_height_mm = 2100
WHERE bom_child_id = 88 AND bom_id = 'DUPLEX_BATHROOM_SET';

-- ── 2. Add fixture placement params for child 85 (TOILET) ─────────────────
--    Back wall, one per stall, facing away from wall

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'placement_wall', 'back', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'rotation_rule', 'FACE_AWAY_FROM_WALL', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'wall_offset', '0.05', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'spacing', '1.3', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'qty_rule', 'per_wall_length', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (85, 'z_offset', '0', 1);

-- ── 3. Add fixture placement params for child 86 (BASIN) ──────────────────
--    Side interior wall, single unit, facing into room, counter height

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (86, 'placement_wall', 'side_interior', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (86, 'rotation_rule', 'FACE_INTO_ROOM', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (86, 'wall_offset', '0.05', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (86, 'spacing', '0.8', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (86, 'z_offset', '0.85', 1);

-- ── 4. Add fixture placement params for child 87 (BATHING/Bath) ───────────
--    Door wall, single unit, facing into room

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (87, 'placement_wall', 'door', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (87, 'rotation_rule', 'FACE_INTO_ROOM', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (87, 'wall_offset', '0.05', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (87, 'spacing', '1.0', 1);

-- ── 5. Add fixture placement params for child 88 (BATHING/Shower) ─────────
--    Floor center position (single fixture)

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (88, 'position', 'floor_center', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (88, 'rotation_rule', '0', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (88, 'qty', '1', 1);

INSERT OR REPLACE INTO m_attribute (bom_child_id, param_key, param_value, is_active)
VALUES (88, 'z_offset', '0', 1);

-- ── Verification ──────────────────────────────────────────────────────────
-- Expected: 18 new params for children 85-88

SELECT bc.bom_child_id, bc.role, bc.product_ref,
       GROUP_CONCAT(a.param_key || '=' || a.param_value, ', ') AS params
FROM m_bom_line bc
LEFT JOIN m_attribute a ON a.bom_child_id = bc.bom_child_id AND a.is_active = 1
WHERE bc.bom_id = 'DUPLEX_BATHROOM_SET' AND bc.is_active = 1
GROUP BY bc.bom_child_id
ORDER BY bc.sequence;
