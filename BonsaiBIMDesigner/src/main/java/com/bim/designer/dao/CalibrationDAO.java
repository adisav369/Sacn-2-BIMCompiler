package com.bim.designer.dao;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * DAO for calibration queries — DocEvent generic vs Terminal extracted.
 *
 * <p>Two concerns:
 * <ol>
 *   <li><b>Oracle extraction:</b> query TE reference DB for ground truth metrics
 *       (element counts, NN spacing, Z bands)</li>
 *   <li><b>Rule prediction:</b> compute DocEvent expected layout from
 *       AD_Val_Rule + ad_space_type_mep_bom rules + floor AABB</li>
 * </ol>
 *
 * <p>This DAO does NOT generate placements. It computes what DocEvent WOULD
 * produce given the rules and floor geometry, for comparison against what
 * Terminal engineers actually placed.
 *
 * // Implementing CALIBRATION_SRS.md §5.2 — Witness: W-CAL-DAO-1
 */
public class CalibrationDAO {

    private static final String TAG = "CalibrationDAO";

    // ── Oracle: Terminal ground truth ─────────────────────────────────

    /**
     * Count elements per storey from TE reference DB.
     *
     * @param teConn     connection to SJTII_Terminal_extracted.db
     * @param ifcClass   e.g. "IfcFireSuppressionTerminal"
     * @return map of storey → count
     */
    public Map<String, Integer> teCountByStorey(Connection teConn, String ifcClass)
            throws SQLException {
        String sql = """
                SELECT ss.storey, COUNT(*) as cnt
                FROM elements_meta em
                JOIN spatial_structure ss ON em.guid = ss.guid
                WHERE em.ifc_class = ?
                GROUP BY ss.storey
                ORDER BY ss.storey
                """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = teConn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return result;
    }

    /**
     * Compute median NN distance for an IFC class per storey from TE reference DB.
     * Uses elements_rtree for spatial lookup.
     *
     * <p>For IfcFireSuppressionTerminal, filters to sprinkler heads only
     * (excludes hose reels which are sparse floor-mounted units that inflate NN).
     * // Implementing CALIBRATION_SRS.md §7.3 — Witness: W-CAL-FP-SPACING
     *
     * @param teConn     connection to SJTII_Terminal_extracted.db
     * @param ifcClass   e.g. "IfcFireSuppressionTerminal"
     * @return map of storey → median NN distance in mm
     */
    public Map<String, Double> teNNMedianByStorey(Connection teConn, String ifcClass)
            throws SQLException {
        // FP head-only filter: exclude hose reels (sparse, not grid-placed)
        String nameFilter = ifcClass.equals("IfcFireSuppressionTerminal")
                ? " AND a.element_name LIKE '%sprinkler head%'" +
                  " AND b.element_name LIKE '%sprinkler head%'"
                : "";

        // Per-storey: for each element, find nearest same-class neighbour (planar XY)
        String sql = """
                SELECT storey, AVG(nn_mm) as median_nn FROM (
                    SELECT ss_a.storey,
                        a.id,
                        MIN(SQRT(
                            (ra.minX - rb.minX)*(ra.minX - rb.minX) +
                            (ra.minY - rb.minY)*(ra.minY - rb.minY)
                        )) * 1000 as nn_mm
                    FROM elements_meta a
                    JOIN elements_rtree ra ON a.id = ra.id
                    JOIN spatial_structure ss_a ON a.guid = ss_a.guid
                    JOIN elements_meta b ON a.ifc_class = b.ifc_class AND a.id != b.id
                    JOIN elements_rtree rb ON b.id = rb.id
                    JOIN spatial_structure ss_b ON b.guid = ss_b.guid
                    WHERE a.ifc_class = ?
                      AND ss_a.storey = ss_b.storey
                """ + nameFilter + """

                    GROUP BY ss_a.storey, a.id
                    HAVING nn_mm > 100
                )
                GROUP BY storey
                ORDER BY storey
                """;

        Map<String, Double> result = new LinkedHashMap<>();
        try (PreparedStatement ps = teConn.prepareStatement(sql)) {
            ps.setString(1, ifcClass);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getDouble(2));
                }
            }
        }
        return result;
    }

    /**
     * Total element count per discipline from TE reference DB.
     *
     * @return map of discipline → count
     */
    public Map<String, Integer> teDisciplineCounts(Connection teConn) throws SQLException {
        String sql = """
                SELECT em.discipline, COUNT(*) as cnt
                FROM elements_meta em
                WHERE em.discipline IS NOT NULL
                GROUP BY em.discipline
                ORDER BY cnt DESC
                """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (PreparedStatement ps = teConn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return result;
    }

    // ── Oracle: Floor geometry from TE_BOM.db ────────────────────────

    /**
     * Get floor AABB dimensions from TE_BOM.db.
     *
     * @param bomConn connection to TE_BOM.db
     * @return map of bom_id → [width_mm, depth_mm, height_mm]
     */
    public Map<String, double[]> floorAABB(Connection bomConn) throws SQLException {
        String sql = """
                SELECT Value AS bom_id, aabb_width_mm, aabb_depth_mm, aabb_height_mm
                FROM m_bom WHERE bom_type = 'FLOOR'
                ORDER BY Value
                """;
        Map<String, double[]> result = new LinkedHashMap<>();
        try (Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString(1), new double[]{
                    rs.getDouble(2), rs.getDouble(3), rs.getDouble(4)
                });
            }
        }
        return result;
    }

    /**
     * Get per-storey discipline BOM element counts from TE_BOM.db.
     *
     * @return map of "storey|discipline" → total qty
     */
    public Map<String, Integer> bomDisciplineCounts(Connection bomConn) throws SQLException {
        String sql = """
                SELECT mb.Value AS bom_id, mb.m_product_category_id, SUM(ml.qty) as total_qty
                FROM m_bom_line ml
                JOIN m_bom mb ON ml.M_BOM_ID = mb.M_BOM_ID
                WHERE mb.m_product_category_id IN ('FP','ELEC','CW','SP','ACMV')
                  AND mb.bom_type = 'SET'
                GROUP BY mb.Value, mb.m_product_category_id
                ORDER BY mb.Value
                """;
        Map<String, Integer> result = new LinkedHashMap<>();
        try (Statement st = bomConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String key = rs.getString(1) + "|" + rs.getString(2);
                result.put(key, rs.getInt(3));
            }
        }
        return result;
    }

    // ── Prediction: DocEvent rule-based computation ──────────────────

    /**
     * Compute DocEvent predicted element count from ad_space_type_mep_bom rules.
     *
     * <p>Formula (DISC_VALIDATE_SRS §9.3):
     * <ul>
     *   <li>Fixed qty: qty_normal (if per_area_normal = 0)</li>
     *   <li>Per-area: ceil(floor_area_m2 × per_area_normal)</li>
     * </ul>
     *
     * @param discConn      connection to ERP.db (Phase 2; falls back to component_library.db)
     * @param mepProduct    e.g. "SPRINKLER", "LIGHT"
     * @param floorAreaM2   floor area in m²
     * @return predicted element count, or -1 if no rule found
     */
    // Implementing DISC_VALIDATION_DB_SRS.md §6 Phase 2 — Witness: W-DV-DB-DUAL-READ
    public int docEventQty(Connection discConn, String mepProduct, double floorAreaM2)
            throws SQLException {
        // Query ad_space_type_mep_bom for generic/aggregate per_area
        // Use GENERIC space type as baseline for whole-floor comparison
        String sql = """
                SELECT qty_normal, per_area_normal
                FROM ad_space_type_mep_bom
                WHERE mep_product_id = ? AND space_type_id = 'GENERIC'
                """;
        try (PreparedStatement ps = discConn.prepareStatement(sql)) {
            ps.setString(1, mepProduct);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double qtyNormal = rs.getDouble(1);
                    double perArea = rs.getDouble(2);
                    if (perArea > 0) {
                        return (int) Math.ceil(floorAreaM2 * perArea);
                    } else if (qtyNormal > 0) {
                        return (int) qtyNormal;
                    }
                }
            }
        }

        // Fallback: try any space type to get per_area_normal
        String fallbackSql = """
                SELECT AVG(per_area_normal) as avg_per_area
                FROM ad_space_type_mep_bom
                WHERE mep_product_id = ? AND per_area_normal > 0
                """;
        try (PreparedStatement ps = discConn.prepareStatement(fallbackSql)) {
            ps.setString(1, mepProduct);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double avgPerArea = rs.getDouble(1);
                    if (avgPerArea > 0) {
                        return (int) Math.ceil(floorAreaM2 * avgPerArea);
                    }
                }
            }
        }

        return -1;  // no seed data for this product
    }

    /**
     * Compute DocEvent grid pitch from AD_Val_Rule parameters.
     *
     * <p>Formula (DocAction_SRS §7.2):
     * {@code pitch = min(max_spacing, dim / ceil(dim / typical_spacing))}
     *
     * @param valConn       connection to validation.db
     * @param ruleId        AD_Val_Rule ID (e.g. 601 for FP, 803 for ELEC)
     * @param floorDimMm    floor dimension to compute pitch against (width or depth)
     * @return computed pitch in mm, or -1 if rule not found
     */
    public double docEventPitch(Connection valConn, int ruleId, double floorDimMm)
            throws SQLException {
        String sql = """
                SELECT name, CAST(value AS REAL) FROM AD_Val_Rule_Param
                WHERE ad_val_rule_id = ?
                """;
        Map<String, Double> params = new LinkedHashMap<>();
        try (PreparedStatement ps = valConn.prepareStatement(sql)) {
            ps.setInt(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        params.put(rs.getString(1), rs.getDouble(2));
                    } catch (Exception e) {
                        // skip non-numeric params
                    }
                }
            }
        }

        double maxSpacing = params.getOrDefault("max_spacing_mm",
                params.getOrDefault("max_nn_spacing_mm", -1.0));
        double typicalSpacing = params.getOrDefault("typical_spacing_mm", maxSpacing);

        if (maxSpacing <= 0 || typicalSpacing <= 0) return -1;

        // DocAction_SRS §7.2 formula
        double pitch = Math.min(maxSpacing,
                floorDimMm / Math.ceil(floorDimMm / typicalSpacing));

        BIMLogger.fine(TAG, "docEventPitch: rule={}, dim={:.0f}, typical={:.0f}, max={:.0f}, pitch={:.0f}",
                ruleId, floorDimMm, typicalSpacing, maxSpacing, pitch);
        return pitch;
    }
}
