package com.bim.backoffice.federation;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * Work Package Selector — ported from Federation addon operator.py selection logic.
 *
 * <p>Selects BOM sub-trees that form a work package: all elements under a
 * given BOM parent, optionally filtered by discipline. Used to build
 * sub-orders for contractor scoping.
 *
 * // Porting Federation addon operator.py §selection — Witness: W-WORK-PKG-1
 */
public class WorkPackageSelector {

    private static final String TAG = "WorkPackageSelector";

    // ── Record types ────────────────────────────────────────────────

    public record WorkPackage(String bomId, String bomName, String discipline,
                              List<PackageLine> lines, int totalQty,
                              double totalCostRm) {}

    public record PackageLine(String childProductId, int qty, String uom,
                              double unitCostRm, double lineCostRm) {}

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Select a work package: all BOM lines under a given BOM parent.
     * Enriches with cost data from component library if available.
     */
    public WorkPackage selectPackage(Connection bomConn, Connection compLibConn,
                                     String bomId) {
        String bomName = "";
        String discipline = "";

        // Read BOM header
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT bom_name, mpc.Value AS m_product_category_id FROM m_bom b " +
                "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID " +
                "WHERE b.Value = ? AND b.is_active = 1")) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    bomName = rs.getString("bom_name");
                    discipline = rs.getString("m_product_category_id");
                }
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "selectPackage header: {}", e.getMessage());
        }

        // Load cost map from component library
        Map<String, Double> costMap = loadCostMap(compLibConn);

        // Read BOM lines
        List<PackageLine> lines = new ArrayList<>();
        int totalQty = 0;
        double totalCost = 0;

        String sql = "SELECT child_product_id, qty, uom FROM m_bom_line " +
                     "WHERE M_BOM_ID = (SELECT M_BOM_ID FROM m_bom WHERE Value = ? AND is_active = 1) " +
                     "AND is_active = 1 ORDER BY line_no";
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String childId = rs.getString("child_product_id");
                    int qty = rs.getInt("qty");
                    String uom = rs.getString("uom");
                    double unitCost = costMap.getOrDefault(childId, 0.0);
                    double lineCost = qty * unitCost;
                    lines.add(new PackageLine(childId, qty, uom, unitCost, lineCost));
                    totalQty += qty;
                    totalCost += lineCost;
                }
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "selectPackage lines: {}", e.getMessage());
        }

        return new WorkPackage(bomId, bomName, discipline, lines, totalQty, totalCost);
    }

    /**
     * Select all work packages for a discipline.
     * Returns one WorkPackage per BOM that matches the discipline filter.
     */
    public List<WorkPackage> selectByDiscipline(Connection bomConn, Connection compLibConn,
                                                 String discipline) {
        List<WorkPackage> packages = new ArrayList<>();
        String sql = "SELECT b.Value FROM m_bom b " +
                "LEFT JOIN M_Product_Category mpc ON b.m_product_category_id = mpc.M_Product_Category_ID " +
                "WHERE b.is_active = 1 AND UPPER(mpc.Value) = ?";
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, discipline.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bomId = rs.getString("Value");
                    packages.add(selectPackage(bomConn, compLibConn, bomId));
                }
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "selectByDiscipline: {}", e.getMessage());
        }
        return packages;
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Double> loadCostMap(Connection compLibConn) {
        Map<String, Double> map = new HashMap<>();
        if (compLibConn == null) return map;
        try (var stmt = compLibConn.prepareStatement(
                "SELECT Value, unit_cost_rm FROM M_Product WHERE unit_cost_rm > 0");
             var rs = stmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("Value"), rs.getDouble("unit_cost_rm"));
            }
        } catch (Exception e) {
            BIMLogger.warn(TAG, "loadCostMap: {}", e.getMessage());
        }
        return map;
    }
}
