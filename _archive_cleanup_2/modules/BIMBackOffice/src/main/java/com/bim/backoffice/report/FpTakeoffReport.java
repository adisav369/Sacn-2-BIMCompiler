package com.bim.backoffice.report;

// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-08 — Witness: W-RPT-FP

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.CostDAO.CostLine;
import com.bim.backoffice.dao.CostDAO.CostSummary;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fire Protection Takeoff Report (BIM-RPT-08).
 *
 * <p>Filters CostDAO output to discipline = FP and groups quantities:
 * <ul>
 *   <li>Sprinkler heads (count) — by type (pendant, upright, sidewall)</li>
 *   <li>Pipe runs (m) — by diameter</li>
 *   <li>Valves (count) — by type</li>
 *   <li>Pump assemblies (count)</li>
 * </ul>
 */
public class FpTakeoffReport {

    private static final String TAG = "FpTakeoffReport";
    private static final String DISCIPLINE = "FP";

    public record TakeoffGroup(String groupName, String uom,
                               List<TakeoffItem> items, double groupTotal) {}

    public record TakeoffItem(String productName, int qty, String uom,
                              double materialCost, double totalCost) {}

    public record TakeoffResult(String buildingId, String discipline,
                                List<TakeoffGroup> groups,
                                double grandTotal, int lineCount) {}

    /**
     * Generate FP takeoff by filtering CostDAO output to FP discipline.
     */
    public TakeoffResult generate(Connection bomConn, Connection compLibConn,
                                  String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        // Filter lines to FP discipline
        List<CostLine> fpLines = summary.lines().stream()
                .filter(l -> DISCIPLINE.equalsIgnoreCase(l.discipline()))
                .toList();

        // Group by FP component type
        Map<String, List<CostLine>> grouped = new LinkedHashMap<>();
        for (CostLine line : fpLines) {
            String group = classifyFpGroup(line.productName());
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(line);
        }

        List<TakeoffGroup> groups = new ArrayList<>();
        double grandTotal = 0;

        for (var entry : grouped.entrySet()) {
            List<TakeoffItem> items = new ArrayList<>();
            double groupTotal = 0;
            for (CostLine line : entry.getValue()) {
                items.add(new TakeoffItem(line.productName(), line.qty(),
                        line.uom(), line.materialCost(), line.totalCost()));
                groupTotal += line.totalCost();
            }
            String uom = inferGroupUom(entry.getKey());
            groups.add(new TakeoffGroup(entry.getKey(), uom, items, groupTotal));
            grandTotal += groupTotal;
        }

        BIMLogger.info(TAG, "FP takeoff: {} groups, {} lines, RM {:.2f}",
                groups.size(), fpLines.size(), grandTotal);

        return new TakeoffResult(buildingId, DISCIPLINE, groups,
                grandTotal, fpLines.size());
    }

    private String classifyFpGroup(String productName) {
        if (productName == null) return "Other";
        String lower = productName.toLowerCase();
        if (lower.contains("sprinkler") || lower.contains("head")
                || lower.contains("pendant") || lower.contains("upright")
                || lower.contains("sidewall"))
            return "Sprinkler Heads";
        if (lower.contains("pipe") || lower.contains("riser")
                || lower.contains("main") || lower.contains("branch"))
            return "Pipe Runs";
        if (lower.contains("valve") || lower.contains("gate")
                || lower.contains("check") || lower.contains("alarm"))
            return "Valves";
        if (lower.contains("pump") || lower.contains("jockey")
                || lower.contains("booster"))
            return "Pump Assemblies";
        return "Other";
    }

    private String inferGroupUom(String group) {
        return switch (group) {
            case "Sprinkler Heads" -> "count";
            case "Pipe Runs" -> "m";
            case "Valves" -> "count";
            case "Pump Assemblies" -> "count";
            default -> "unit";
        };
    }
}
