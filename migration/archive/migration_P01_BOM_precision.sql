-- P0.1-VERIFY: Restore full-precision BOM coordinates from PlacementAD source
--
-- Problem: allocated_*_mm stored as INTEGER (rounded from exact float).
--          Centroid dx/dy/dz truncated to 6 decimal places.
--          This causes ~10% of AABB coordinates to mismatch by ±1mm when reconstructed.
--
-- Fix: Update m_bom_line with exact REAL values from ad_element_placement.
--      SQLite stores REAL in INTEGER-affinity columns transparently (no ALTER needed).
--
-- Match: child_product_id + centroid proximity (within 1mm)
-- Scope: EXT_SH (55 lines) + EXT_DX (1099 lines) = 1154 total

ATTACH DATABASE 'library/component_library.db' AS clib;

-- Exact centroid: dx = (min_x + max_x) / 2.0
UPDATE m_bom_line SET
  dx = (SELECT (ap.min_x + ap.max_x) / 2.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1),
  dy = (SELECT (ap.min_y + ap.max_y) / 2.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1),
  dz = (SELECT (ap.min_z + ap.max_z) / 2.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1),
  allocated_width_mm = (SELECT (ap.max_x - ap.min_x) * 1000.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1),
  allocated_depth_mm = (SELECT (ap.max_y - ap.min_y) * 1000.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1),
  allocated_height_mm = (SELECT (ap.max_z - ap.min_z) * 1000.0
        FROM clib.ad_element_placement ap
        WHERE ap.M_Product_ID = m_bom_line.child_product_id
        AND ap.building_type = CASE m_bom_line.bom_id
            WHEN 'EXT_SH' THEN 'Ifc4_SampleHouse'
            WHEN 'EXT_DX' THEN 'Ifc2x3_Duplex' END
        AND abs(m_bom_line.dx - (ap.min_x + ap.max_x)/2.0) < 0.001
        AND abs(m_bom_line.dy - (ap.min_y + ap.max_y)/2.0) < 0.001
        AND abs(m_bom_line.dz - (ap.min_z + ap.max_z)/2.0) < 0.001
        AND ap.is_active = 1)
WHERE bom_id IN ('EXT_SH', 'EXT_DX') AND is_active = 1;

-- Verification: count rows that were updated (should be 1154)
SELECT 'Updated rows: ' || changes();

-- Verification: no NULL dx after update (would indicate failed match)
SELECT 'NULL dx count: ' || COUNT(*) FROM m_bom_line
WHERE bom_id IN ('EXT_SH', 'EXT_DX') AND is_active = 1 AND dx IS NULL;

-- Verification: typeof check — allocated_*_mm should now be real
SELECT 'Type check: ' || typeof(allocated_width_mm) || '/' || typeof(dx)
FROM m_bom_line WHERE bom_id = 'EXT_SH' AND is_active = 1 LIMIT 1;

DETACH DATABASE clib;
