package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

/**
 * BOM Schedule Report — work package breakdown for contractor pricing.
 *
 * <p>Ported from Federation addon work package breakdown panel.
 * Reads CostDAO for quantities + costs, formats as structured JSON
 * and plain-text summary. PDF generation deferred to Phase B.
 *
 * // Porting Federation addon work package panel — Witness: W-RPT-BOM-1
 */
public class BomScheduleReport {

    private static final String TAG = "BomScheduleReport";

    // ── Record types ────────────────────────────────────────────────

    public record ReportOutput(String reportId, String title, String buildingId,
                               String generatedDate, Summary summary,
                               List<Section> sections, String plainText) {}

    public record Summary(int totalLines, int totalQty, double totalCostRm,
                          double materialRm, double laborRm, double equipmentRm) {}

    public record Section(String discipline, List<LineItem> items,
                          double sectionTotal, int sectionQty) {}

    public record LineItem(String productName, int qty, String uom,
                           double materialCost, double laborCost,
                           double equipmentCost, double totalCost) {}

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId) {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary cost;
        try {
            cost = dao.costBreakdown(bomConn, compLibConn, buildingId);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "generate: {}", e.getMessage());
            return new ReportOutput("BIM-RPT-BOM", "BOM Schedule",
                    buildingId, LocalDate.now().toString(),
                    new Summary(0, 0, 0, 0, 0, 0), List.of(), "Error: " + e.getMessage());
        }

        // Group lines by discipline
        Map<String, List<CostDAO.CostLine>> byDisc = new LinkedHashMap<>();
        for (var line : cost.lines()) {
            byDisc.computeIfAbsent(line.discipline() != null ? line.discipline() : "OTHER",
                    k -> new ArrayList<>()).add(line);
        }

        List<Section> sections = new ArrayList<>();
        for (var entry : byDisc.entrySet()) {
            List<LineItem> items = new ArrayList<>();
            double sectionTotal = 0;
            int sectionQty = 0;
            for (var line : entry.getValue()) {
                items.add(new LineItem(line.productName(), line.qty(), line.uom(),
                        line.materialCost(), line.laborCost(),
                        line.equipmentCost(), line.totalCost()));
                sectionTotal += line.totalCost();
                sectionQty += line.qty();
            }
            sections.add(new Section(entry.getKey(), items, sectionTotal, sectionQty));
        }

        int totalQty = cost.lines().stream().mapToInt(CostDAO.CostLine::qty).sum();
        Summary summary = new Summary(cost.lines().size(), totalQty,
                cost.grandTotal(), cost.materialTotal(),
                cost.laborTotal(), cost.equipmentTotal());

        String plainText = formatPlainText(buildingId, summary, sections);
        return new ReportOutput("BIM-RPT-BOM", "BOM Schedule — " + buildingId,
                buildingId, LocalDate.now().toString(), summary, sections, plainText);
    }

    // ── Plain text formatter ────────────────────────────────────────

    private String formatPlainText(String buildingId, Summary s, List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        sb.append("BOM SCHEDULE REPORT — ").append(buildingId).append('\n');
        sb.append("Generated: ").append(LocalDate.now()).append('\n');
        sb.append("═".repeat(60)).append('\n');
        sb.append(String.format("Total Lines: %d | Total Qty: %d | Grand Total: RM %.2f%n",
                s.totalLines(), s.totalQty(), s.totalCostRm()));
        sb.append(String.format("  Material: RM %.2f | Labor: RM %.2f | Equipment: RM %.2f%n",
                s.materialRm(), s.laborRm(), s.equipmentRm()));
        sb.append("─".repeat(60)).append('\n');

        for (var section : sections) {
            sb.append(String.format("%n▸ %s (%d items, RM %.2f)%n",
                    section.discipline(), section.sectionQty(), section.sectionTotal()));
            for (var item : section.items()) {
                sb.append(String.format("  %-30s %4d %-4s  RM %10.2f%n",
                        truncate(item.productName(), 30), item.qty(),
                        item.uom(), item.totalCost()));
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return (s != null && s.length() > max) ? s.substring(0, max - 2) + ".." : (s != null ? s : "");
    }
}
