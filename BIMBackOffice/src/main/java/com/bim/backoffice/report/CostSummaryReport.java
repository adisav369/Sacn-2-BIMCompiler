package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

/**
 * Cost Summary Report — 5D cost breakdown by discipline and floor.
 *
 * <p>Ported from Federation addon 5D cost tab. Reads CostDAO and
 * formats as structured JSON + plain text. Maps to future
 * AD_PrintFormat BIM-RPT-02 (JKR Contract Cost Summary).
 *
 * // Porting Federation addon 5D cost tab — Witness: W-RPT-COST-1
 */
public class CostSummaryReport {

    private static final String TAG = "CostSummaryReport";

    // ── Record types ────────────────────────────────────────────────

    public record ReportOutput(String reportId, String title, String buildingId,
                               String generatedDate,
                               double grandTotalRm, double materialPct,
                               double laborPct, double equipmentPct,
                               List<DisciplineBreakdown> byDiscipline,
                               String plainText) {}

    public record DisciplineBreakdown(String discipline, double totalCostRm,
                                      double percentOfGrand, int lineCount) {}

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId) {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary cost;
        try {
            cost = dao.costBreakdown(bomConn, compLibConn, buildingId);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "generate: {}", e.getMessage());
            return new ReportOutput("BIM-RPT-COST", "Cost Summary",
                    buildingId, LocalDate.now().toString(),
                    0, 0, 0, 0, List.of(), "Error: " + e.getMessage());
        }

        // Discipline breakdown
        Map<String, double[]> discAgg = new LinkedHashMap<>(); // [cost, count]
        for (var line : cost.lines()) {
            String disc = line.discipline() != null ? line.discipline() : "OTHER";
            double[] agg = discAgg.computeIfAbsent(disc, k -> new double[2]);
            agg[0] += line.totalCost();
            agg[1] += 1;
        }

        List<DisciplineBreakdown> byDisc = new ArrayList<>();
        for (var entry : discAgg.entrySet()) {
            double pct = cost.grandTotal() > 0
                    ? (entry.getValue()[0] / cost.grandTotal()) * 100.0 : 0;
            byDisc.add(new DisciplineBreakdown(entry.getKey(),
                    entry.getValue()[0], pct, (int) entry.getValue()[1]));
        }
        // Sort by cost descending
        byDisc.sort(Comparator.comparingDouble(DisciplineBreakdown::totalCostRm).reversed());

        String plainText = formatPlainText(buildingId, cost, byDisc);
        return new ReportOutput("BIM-RPT-COST", "5D Cost Summary — " + buildingId,
                buildingId, LocalDate.now().toString(),
                cost.grandTotal(), cost.materialPct(), cost.laborPct(),
                cost.equipmentPct(), byDisc, plainText);
    }

    private String formatPlainText(String buildingId, CostDAO.CostSummary cost,
                                   List<DisciplineBreakdown> byDisc) {
        StringBuilder sb = new StringBuilder();
        sb.append("5D COST SUMMARY — ").append(buildingId).append('\n');
        sb.append("Generated: ").append(LocalDate.now()).append('\n');
        sb.append("═".repeat(60)).append('\n');
        sb.append(String.format("Grand Total: RM %,.2f%n", cost.grandTotal()));
        sb.append(String.format("  Material: %.1f%% | Labor: %.1f%% | Equipment: %.1f%%%n",
                cost.materialPct(), cost.laborPct(), cost.equipmentPct()));
        sb.append("─".repeat(60)).append('\n');
        sb.append(String.format("%-15s %15s %8s %6s%n", "Discipline", "Cost (RM)", "Lines", "%"));
        sb.append("─".repeat(60)).append('\n');
        for (var d : byDisc) {
            sb.append(String.format("%-15s %,15.2f %8d %5.1f%%%n",
                    d.discipline(), d.totalCostRm(), d.lineCount(), d.percentOfGrand()));
        }
        return sb.toString();
    }
}
