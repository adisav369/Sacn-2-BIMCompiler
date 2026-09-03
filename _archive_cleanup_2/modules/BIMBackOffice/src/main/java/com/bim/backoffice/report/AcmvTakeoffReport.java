package com.bim.backoffice.report;

// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-09 — Witness: W-RPT-ACMV

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
 * ACMV Takeoff Report (BIM-RPT-09).
 *
 * <p>Filters CostDAO output to discipline = ACMV and groups quantities:
 * <ul>
 *   <li>Duct (m²) — by size/type</li>
 *   <li>AHU units (count) — by capacity</li>
 *   <li>Diffusers (count) — by type</li>
 *   <li>Total CFM/capacity</li>
 * </ul>
 */
public class AcmvTakeoffReport {

    private static final String TAG = "AcmvTakeoffReport";
    private static final String DISCIPLINE = "ACMV";

    public record TakeoffGroup(String groupName, String uom,
                               List<TakeoffItem> items, double groupTotal) {}

    public record TakeoffItem(String productName, int qty, String uom,
                              double materialCost, double totalCost) {}

    public record TakeoffResult(String buildingId, String discipline,
                                List<TakeoffGroup> groups,
                                double grandTotal, int lineCount) {}

    /**
     * Generate ACMV takeoff by filtering CostDAO output to ACMV discipline.
     */
    public TakeoffResult generate(Connection bomConn, Connection compLibConn,
                                  String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        // Filter lines to ACMV discipline
        List<CostLine> acmvLines = summary.lines().stream()
                .filter(l -> DISCIPLINE.equalsIgnoreCase(l.discipline()))
                .toList();

        // Group by ACMV component type
        Map<String, List<CostLine>> grouped = new LinkedHashMap<>();
        for (CostLine line : acmvLines) {
            String group = classifyAcmvGroup(line.productName());
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

        BIMLogger.info(TAG, "ACMV takeoff: {} groups, {} lines, RM {:.2f}",
                groups.size(), acmvLines.size(), grandTotal);

        return new TakeoffResult(buildingId, DISCIPLINE, groups,
                grandTotal, acmvLines.size());
    }

    private String classifyAcmvGroup(String productName) {
        if (productName == null) return "Other";
        String lower = productName.toLowerCase();
        if (lower.contains("duct") || lower.contains("ductwork"))
            return "Duct";
        if (lower.contains("ahu") || lower.contains("air handling")
                || lower.contains("handler"))
            return "AHU Units";
        if (lower.contains("diffuser") || lower.contains("grille")
                || lower.contains("register"))
            return "Diffusers";
        if (lower.contains("cfm") || lower.contains("fan")
                || lower.contains("blower") || lower.contains("compressor")
                || lower.contains("chiller") || lower.contains("condenser"))
            return "Capacity/CFM";
        return "Other";
    }

    private String inferGroupUom(String group) {
        return switch (group) {
            case "Duct" -> "m²";
            case "AHU Units" -> "count";
            case "Diffusers" -> "count";
            case "Capacity/CFM" -> "CFM";
            default -> "unit";
        };
    }
}
