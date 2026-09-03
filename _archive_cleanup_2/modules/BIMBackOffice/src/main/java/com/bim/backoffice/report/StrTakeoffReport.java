package com.bim.backoffice.report;

// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-07 — Witness: W-RPT-STR

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
 * Structural Takeoff Report (BIM-RPT-07).
 *
 * <p>Filters CostDAO output to discipline = STR and groups quantities:
 * <ul>
 *   <li>Concrete (m³) — columns, beams, slabs, foundations</li>
 *   <li>Reinforcement (kg) — rebar by diameter</li>
 *   <li>Formwork (m²) — by element type</li>
 * </ul>
 */
public class StrTakeoffReport {

    private static final String TAG = "StrTakeoffReport";
    private static final String DISCIPLINE = "STR";

    public record TakeoffGroup(String groupName, String uom,
                               List<TakeoffItem> items, double groupTotal) {}

    public record TakeoffItem(String productName, int qty, String uom,
                              double materialCost, double totalCost) {}

    public record TakeoffResult(String buildingId, String discipline,
                                List<TakeoffGroup> groups,
                                double grandTotal, int lineCount) {}

    /**
     * Generate STR takeoff by filtering CostDAO output to STR discipline.
     */
    public TakeoffResult generate(Connection bomConn, Connection compLibConn,
                                  String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        // Filter lines to STR discipline
        List<CostLine> strLines = summary.lines().stream()
                .filter(l -> DISCIPLINE.equalsIgnoreCase(l.discipline()))
                .toList();

        // Group by product category (product name prefix as proxy for concrete/rebar/formwork)
        Map<String, List<CostLine>> grouped = new LinkedHashMap<>();
        for (CostLine line : strLines) {
            String group = classifyStrGroup(line.productName());
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

        BIMLogger.info(TAG, "STR takeoff: {} groups, {} lines, RM {:.2f}",
                groups.size(), strLines.size(), grandTotal);

        return new TakeoffResult(buildingId, DISCIPLINE, groups,
                grandTotal, strLines.size());
    }

    private String classifyStrGroup(String productName) {
        if (productName == null) return "Other";
        String lower = productName.toLowerCase();
        if (lower.contains("concrete") || lower.contains("slab")
                || lower.contains("column") || lower.contains("beam")
                || lower.contains("foundation") || lower.contains("footing"))
            return "Concrete";
        if (lower.contains("rebar") || lower.contains("reinforc")
                || lower.contains("bar") || lower.contains("mesh"))
            return "Reinforcement";
        if (lower.contains("formwork") || lower.contains("shuttering")
                || lower.contains("plywood"))
            return "Formwork";
        return "Other";
    }

    private String inferGroupUom(String group) {
        return switch (group) {
            case "Concrete" -> "m³";
            case "Reinforcement" -> "kg";
            case "Formwork" -> "m²";
            default -> "unit";
        };
    }
}
