package com.bim.backoffice.federation;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Dimension Query — direct SQL queries for 4D–7D element filtering.
 *
 * <p>Ported from Federation addon operator.py query dispatch.
 * NLP parser is deferred — this provides the direct SQL layer that
 * NLP will eventually call.
 *
 * <p>Each query returns a list of element IDs matching a dimension filter,
 * suitable for highlighting in the WebUI via ColorSchemeEngine.
 *
 * // Porting Federation addon operator.py §query — Witness: W-DIM-QUERY-1
 */
public class DimensionQuery {

    private static final String TAG = "DimensionQuery";

    // ── Record types ────────────────────────────────────────────────

    public record QueryResult(String dimension, String filter, List<String> elementIds,
                              int matchCount) {}

    // ── Queries ─────────────────────────────────────────────────────

    /** Elements belonging to a specific discipline (e.g. "ARC", "STR", "FP"). */
    public QueryResult byDiscipline(Connection bomConn, String discipline) {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT bl.child_product_id FROM m_bom_line bl " +
                     "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                     "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID " +
                     "WHERE bl.is_active = 1 AND UPPER(mpc.Value) = ?";
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, discipline.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "byDiscipline: {}", e.getMessage());
        }
        return new QueryResult("discipline", discipline, ids, ids.size());
    }

    /** Elements in a specific construction phase (from M_Product.construction_phase). */
    public QueryResult byPhase(Connection bomConn, Connection compLibConn, String phase) {
        // Load products with matching phase from component_library
        Set<String> matchingProducts = new HashSet<>();
        String phaseSql = "SELECT Value FROM M_Product WHERE construction_phase = ?";
        try (PreparedStatement ps = compLibConn.prepareStatement(phaseSql)) {
            ps.setString(1, phase);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) matchingProducts.add(rs.getString(1));
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "byPhase product lookup: {}", e.getMessage());
        }

        // Find BOM lines referencing those products
        List<String> ids = new ArrayList<>();
        if (!matchingProducts.isEmpty()) {
            String bomSql = "SELECT child_product_id FROM m_bom_line WHERE is_active = 1";
            try (PreparedStatement ps = bomConn.prepareStatement(bomSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String pid = rs.getString(1);
                    if (matchingProducts.contains(pid)) ids.add(pid);
                }
            } catch (Exception e) {
                BIMLogger.warn(TAG, "byPhase bom lookup: {}", e.getMessage());
            }
        }
        return new QueryResult("phase", phase, ids, ids.size());
    }

    /** Elements on a specific floor/storey (BOM tree: FLOOR parent). */
    public QueryResult byFloor(Connection bomConn, String floorBomId) {
        List<String> ids = new ArrayList<>();
        String sql = "SELECT bl.child_product_id FROM m_bom_line bl " +
                     "JOIN m_bom b ON bl.M_BOM_ID = b.M_BOM_ID " +
                     "WHERE bl.is_active = 1 AND b.Value = ?";
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, floorBomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getString(1));
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "byFloor: {}", e.getMessage());
        }
        return new QueryResult("floor", floorBomId, ids, ids.size());
    }

    /** Elements with cost above a threshold (RM). */
    public QueryResult byCostAbove(Connection bomConn, Connection compLibConn,
                                   String buildingId, double thresholdRm) {
        List<String> ids = new ArrayList<>();
        try {
            var dao = new com.bim.backoffice.dao.CostDAO();
            var summary = dao.costBreakdown(bomConn, compLibConn, buildingId);
            for (var line : summary.lines()) {
                if (line.totalCost() >= thresholdRm) ids.add(line.bomId());
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "byCostAbove: {}", e.getMessage());
        }
        return new QueryResult("cost-above", String.valueOf(thresholdRm), ids, ids.size());
    }

    /** Elements with carbon above a threshold (kg CO₂). */
    public QueryResult byCarbonAbove(Connection bomConn, Connection compLibConn,
                                     String buildingId, double thresholdKg) {
        List<String> ids = new ArrayList<>();
        try {
            var dao = new com.bim.backoffice.dao.SustainabilityDAO();
            var summary = dao.carbonFootprint(bomConn, compLibConn, buildingId);
            for (var line : summary.lines()) {
                if (line.totalCarbon() >= thresholdKg) ids.add(line.bomId());
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "byCarbonAbove: {}", e.getMessage());
        }
        return new QueryResult("carbon-above", String.valueOf(thresholdKg), ids, ids.size());
    }
}
