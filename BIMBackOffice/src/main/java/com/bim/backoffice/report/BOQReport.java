package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.CostDAO.CostLine;
import com.bim.backoffice.dao.CostDAO.CostSummary;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * BIM-RPT-01: CIDB Bill of Quantities — PWD 203A section structure.
 *
 * <p>Groups cost lines by discipline into PWD 203A sections, lists items
 * with qty x unit cost = line total, and produces a grand total.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-01 — Witness: W-RPT-BOQ
 */
public class BOQReport {

    // ── Output records ──────────────────────────────────────────────

    public record ReportOutput(
            String reportId,
            String title,
            String buildingId,
            String generatedDate,
            String contractNo,
            String currencyCode,
            List<Section> sections,
            double grandTotal,
            String plainText
    ) {}

    public record Section(String sectionCode, String sectionName,
                          List<LineItem> items, double sectionTotal) {}

    public record LineItem(String productName, int qty, String uom,
                           double unitCost, double totalCost) {}

    // ── PWD 203A discipline → section mapping (from REPORTING_ENGINE_SRS §6) ──

    private static final Map<String, String> SECTION_CODE = new LinkedHashMap<>();
    private static final Map<String, String> SECTION_NAME = new LinkedHashMap<>();
    static {
        map("STR",  "C", "Concrete Frame");
        map("ARC",  "D", "Brickwork/Blockwork");
        map("PLUMB","H", "Plumbing and Drainage");
        map("PLB",  "H", "Plumbing and Drainage");
        map("ELC",  "I", "Electrical Installation");
        map("ELEC", "I", "Electrical Installation");
        map("FP",   "J", "Fire Protection");
        map("FPR",  "J", "Fire Protection");
        map("ACMV", "K", "Air Conditioning and Mechanical Ventilation");
    }
    private static void map(String discipline, String code, String name) {
        SECTION_CODE.put(discipline, code);
        SECTION_NAME.put(discipline, name);
    }

    private static final String FALLBACK_CODE = "L";
    private static final String FALLBACK_NAME = "External Works";

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        // Group lines by PWD 203A section
        Map<String, List<CostLine>> grouped = new LinkedHashMap<>();
        for (CostLine line : summary.lines()) {
            String disc = line.discipline();
            String code = SECTION_CODE.getOrDefault(disc, FALLBACK_CODE);
            String name = SECTION_NAME.getOrDefault(disc, FALLBACK_NAME);
            String key = code + "-" + name;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }

        // Build sections
        List<Section> sections = new ArrayList<>();
        double grandTotal = 0;
        for (var entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("-", 2);
            String sectionCode = parts[0];
            String sectionName = parts.length > 1 ? parts[1] : "";
            List<LineItem> items = new ArrayList<>();
            double sectionTotal = 0;
            for (CostLine cl : entry.getValue()) {
                double unitCost = cl.totalCost() / Math.max(cl.qty(), 1);
                items.add(new LineItem(cl.productName(), cl.qty(), cl.uom(),
                        unitCost, cl.totalCost()));
                sectionTotal += cl.totalCost();
            }
            sections.add(new Section(sectionCode, sectionName, items, sectionTotal));
            grandTotal += sectionTotal;
        }

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("BILL OF QUANTITIES — ").append(buildingId).append("\n");
        sb.append("Standard: PWD 203A (CIDB Malaysia)\n");
        sb.append("Contract: DRAFT\n");
        sb.append("Currency: MYR\n");
        sb.append("Date: ").append(LocalDate.now()).append("\n\n");

        for (Section s : sections) {
            sb.append("Section ").append(s.sectionCode()).append(" — ")
              .append(s.sectionName()).append("\n");
            for (LineItem li : s.items()) {
                sb.append(String.format("  %-40s %5d %-6s @ %10.2f = %12.2f%n",
                        li.productName(), li.qty(), li.uom(),
                        li.unitCost(), li.totalCost()));
            }
            sb.append(String.format("  Section Total: %52.2f%n%n", s.sectionTotal()));
        }
        sb.append(String.format("GRAND TOTAL: MYR %,.2f%n", grandTotal));

        return new ReportOutput(
                "BIM-RPT-01",
                "CIDB Bill of Quantities",
                buildingId,
                LocalDate.now().toString(),
                "DRAFT",
                "MYR",
                sections,
                grandTotal,
                sb.toString()
        );
    }
}
