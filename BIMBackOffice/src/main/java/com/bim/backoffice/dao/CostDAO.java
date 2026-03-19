package com.bim.backoffice.dao;

import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO for 5D Cost & Quantity Takeoff — BOM × M_Product unit costs.
 *
 * <p>Borrows CIDB Malaysia 2024 rates from IfcOpenShell Federation addon
 * (boq/comprehensive_boq_export.py) and re-grounds them on the ERP product table.
 * Each M_Product carries unit_cost_rm, labor rates, and equipment allocation.
 *
 * <p>Three cost components per BOM line:
 * <ol>
 *   <li><b>Material</b> — qty × unit_cost_rm</li>
 *   <li><b>Labor</b> — (qty / productivity) × crew_size × labor_rate_rm_per_day</li>
 *   <li><b>Equipment</b> — labor_days × equipment_duration_factor × equipment_rate_rm_per_day</li>
 * </ol>
 *
 * // Implementing TIER1_SRS.md §5D — Witness: W-5D-COST-1
 */
public class CostDAO {

    private static final String TAG = "CostDAO";

    // ── Record types ────────────────────────────────────────────────

    public record CostLine(String bomId, String productName, String discipline,
                           int qty, String uom,
                           double materialCost, double laborCost,
                           double equipmentCost, double totalCost,
                           String laborResource, double laborDays) {}

    public record CostSummary(String buildingId, double grandTotal,
                              double materialTotal, double laborTotal,
                              double equipmentTotal,
                              double materialPct, double laborPct,
                              double equipmentPct,
                              List<CostLine> lines,
                              Map<String, Double> byDiscipline,
                              Map<String, Double> byPhase,
                              Map<String, Double> byResource) {}

    public record CostDelta(String bomId, String productName,
                            double oldCost, double newCost,
                            double delta) {}

    // ── Queries ─────────────────────────────────────────────────────

    /**
     * Full cost breakdown for a compiled building.
     * Three-component cost: material + labor + equipment.
     */
    public CostSummary costBreakdown(Connection bomConn, Connection compLibConn,
                                      String buildingId) throws SQLException {
        Map<String, ProductCost> costMap = loadCostMap(compLibConn);

        String sql = """
                SELECT bl.child_product_id, bl.qty, b.bom_category, b.bom_name
                FROM m_bom_line bl
                JOIN m_bom b ON bl.bom_id = b.bom_id
                WHERE bl.child_product_id IS NOT NULL AND bl.is_active = 1
                ORDER BY bl.bom_child_id
                """;

        List<CostLine> lines = new ArrayList<>();
        Map<String, Double> byDiscipline = new LinkedHashMap<>();
        Map<String, Double> byPhase = new LinkedHashMap<>();
        Map<String, Double> byResource = new LinkedHashMap<>();
        double materialTotal = 0, laborTotal = 0, equipmentTotal = 0;

        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String productId = rs.getString("child_product_id");
                int qty = rs.getInt("qty");
                String discipline = rs.getString("bom_category");
                if (discipline == null) discipline = "GEN";

                ProductCost pc = costMap.get(productId);
                if (pc == null || pc.unitCost <= 0) continue;

                // Material cost
                double matCost = qty * pc.unitCost;

                // Labor cost: (qty / productivity) × crew × daily_rate
                double laborDays = pc.productivity > 0
                        ? (double) qty / pc.productivity : 0;
                double labCost = laborDays * pc.crewSize * pc.laborRate;

                // Equipment cost: labor_days × factor × equip_rate
                double eqCost = laborDays * pc.equipFactor * pc.equipRate;

                double total = matCost + labCost + eqCost;

                materialTotal += matCost;
                laborTotal += labCost;
                equipmentTotal += eqCost;

                lines.add(new CostLine(
                        productId, productId, discipline,
                        qty, pc.uom, matCost, labCost, eqCost, total,
                        pc.laborResource, laborDays));

                byDiscipline.merge(discipline, total, Double::sum);
                if (pc.phase != null) byPhase.merge(pc.phase, total, Double::sum);
                if (pc.laborResource != null)
                    byResource.merge(pc.laborResource, labCost, Double::sum);
            }
        }

        double grand = materialTotal + laborTotal + equipmentTotal;
        double matPct = grand > 0 ? materialTotal / grand * 100 : 0;
        double labPct = grand > 0 ? laborTotal / grand * 100 : 0;
        double eqPct = grand > 0 ? equipmentTotal / grand * 100 : 0;

        BIMLogger.info(TAG, "Cost breakdown: RM {:.2f} (mat {:.0f}% + lab {:.0f}% + eq {:.0f}%), {} lines",
                grand, matPct, labPct, eqPct, lines.size());

        return new CostSummary(buildingId, grand,
                materialTotal, laborTotal, equipmentTotal,
                matPct, labPct, eqPct,
                lines, byDiscipline, byPhase, byResource);
    }

    /**
     * Cost of change — delta between old and new bbox sets.
     * For costOfChange() wire action during drag operations.
     */
    public List<CostDelta> costOfChange(Connection compLibConn,
                                         List<String> addedProducts,
                                         List<String> removedProducts)
            throws SQLException {
        Map<String, ProductCost> costMap = loadCostMap(compLibConn);
        List<CostDelta> deltas = new ArrayList<>();

        for (String pid : addedProducts) {
            ProductCost pc = costMap.get(pid);
            double cost = pc != null ? pc.unitCost : 0;
            deltas.add(new CostDelta(pid, pid, 0, cost, cost));
        }
        for (String pid : removedProducts) {
            ProductCost pc = costMap.get(pid);
            double cost = pc != null ? pc.unitCost : 0;
            deltas.add(new CostDelta(pid, pid, cost, 0, -cost));
        }
        return deltas;
    }

    /**
     * Quick cost lookup for a single product — for properties popup.
     */
    public CostLine elementCost(Connection compLibConn, String productId, int qty)
            throws SQLException {
        Map<String, ProductCost> costMap = loadCostMap(compLibConn);
        ProductCost pc = costMap.get(productId);
        if (pc == null) return null;

        double matCost = qty * pc.unitCost;
        double laborDays = pc.productivity > 0 ? (double) qty / pc.productivity : 0;
        double labCost = laborDays * pc.crewSize * pc.laborRate;
        double eqCost = laborDays * pc.equipFactor * pc.equipRate;

        return new CostLine(productId, productId, null,
                qty, pc.uom, matCost, labCost, eqCost,
                matCost + labCost + eqCost,
                pc.laborResource, laborDays);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private record ProductCost(double unitCost, String uom, String phase,
                               String laborResource, double laborRate,
                               int crewSize, double productivity,
                               double equipRate, double equipFactor) {}

    private Map<String, ProductCost> loadCostMap(Connection compLibConn)
            throws SQLException {
        Map<String, ProductCost> map = new LinkedHashMap<>();
        String sql = """
                SELECT product_id, unit_cost_rm, cost_uom, construction_phase,
                       labor_resource, labor_rate_rm_per_day, labor_crew_size,
                       productivity_rate, equipment_rate_rm_per_day,
                       equipment_duration_factor
                FROM M_Product
                WHERE is_active = 1
                """;
        try (PreparedStatement ps = compLibConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("product_id"),
                        new ProductCost(
                                rs.getDouble("unit_cost_rm"),
                                rs.getString("cost_uom"),
                                rs.getString("construction_phase"),
                                rs.getString("labor_resource"),
                                rs.getDouble("labor_rate_rm_per_day"),
                                rs.getInt("labor_crew_size"),
                                rs.getDouble("productivity_rate"),
                                rs.getDouble("equipment_rate_rm_per_day"),
                                rs.getDouble("equipment_duration_factor")));
            }
        }
        return map;
    }
}
