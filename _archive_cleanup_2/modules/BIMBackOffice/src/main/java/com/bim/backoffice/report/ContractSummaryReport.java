package com.bim.backoffice.report;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.CostDAO.CostSummary;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * BIM-RPT-02: JKR Contract Cost Summary.
 *
 * <p>Produces a contract summary with standard Malaysian construction
 * allowances: provisional sums (10% material), PC sums (5% grand),
 * contingencies (5% subtotal), and SST (6% on services).
 *
 * <p>Percentages are standard MY construction practice — marked ESTIMATE provenance.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-02 — Witness: W-RPT-CONTRACT
 */
public class ContractSummaryReport {

    // ── Output records ──────────────────────────────────────────────

    public record ReportOutput(
            String reportId,
            String title,
            String buildingId,
            String generatedDate,
            String contractNo,
            String currencyCode,
            double materialTotal,
            double laborTotal,
            double equipmentTotal,
            double subtotalDirect,
            double provisionalSums,
            double pcSums,
            double subtotalBeforeContingency,
            double contingencies,
            double subtotalBeforeSST,
            double sst,
            double contractSum,
            List<SummaryLine> lines,
            String provenance,
            String plainText
    ) {}

    public record SummaryLine(String description, String basis, double amount) {}

    // ── Standard MY percentages (ESTIMATE provenance) ───────────────

    private static final double PROVISIONAL_PCT = 0.10;  // 10% of material
    private static final double PC_PCT          = 0.05;  // 5% of grand total
    private static final double CONTINGENCY_PCT = 0.05;  // 5% of subtotal
    private static final double SST_PCT         = 0.06;  // 6% on services (labor + equipment)

    // ── Generate ────────────────────────────────────────────────────

    public ReportOutput generate(Connection bomConn, Connection compLibConn,
                                 String buildingId) throws SQLException {
        CostDAO dao = new CostDAO();
        CostSummary summary = dao.costBreakdown(bomConn, compLibConn, buildingId);

        double material   = summary.materialTotal();
        double labor      = summary.laborTotal();
        double equipment  = summary.equipmentTotal();
        double directCost = material + labor + equipment;

        // Standard JKR allowances
        double provisionalSums = material * PROVISIONAL_PCT;
        double pcSums          = directCost * PC_PCT;
        double subtotalBeforeContingency = directCost + provisionalSums + pcSums;
        double contingencies   = subtotalBeforeContingency * CONTINGENCY_PCT;
        double subtotalBeforeSST = subtotalBeforeContingency + contingencies;
        // SST applies to services (labor + equipment), not material
        double sst             = (labor + equipment) * SST_PCT;
        double contractSum     = subtotalBeforeSST + sst;

        // Build structured lines
        List<SummaryLine> lines = new ArrayList<>();
        lines.add(new SummaryLine("Material Cost", "CostDAO material total", material));
        lines.add(new SummaryLine("Labor Cost", "CostDAO labor total", labor));
        lines.add(new SummaryLine("Equipment Cost", "CostDAO equipment total", equipment));
        lines.add(new SummaryLine("Direct Cost Subtotal", "material + labor + equipment", directCost));
        lines.add(new SummaryLine("Provisional Sums", "ESTIMATE: 10% of material", provisionalSums));
        lines.add(new SummaryLine("PC Sums", "ESTIMATE: 5% of direct cost", pcSums));
        lines.add(new SummaryLine("Subtotal", "direct + provisional + PC", subtotalBeforeContingency));
        lines.add(new SummaryLine("Contingencies", "ESTIMATE: 5% of subtotal", contingencies));
        lines.add(new SummaryLine("Subtotal before SST", "subtotal + contingencies", subtotalBeforeSST));
        lines.add(new SummaryLine("SST (6% on services)", "ESTIMATE: 6% of (labor + equipment)", sst));
        lines.add(new SummaryLine("CONTRACT SUM", "total", contractSum));

        // Plain text
        StringBuilder sb = new StringBuilder();
        sb.append("JKR CONTRACT COST SUMMARY — ").append(buildingId).append("\n");
        sb.append("Standard: JKR Malaysia\n");
        sb.append("Contract: DRAFT\n");
        sb.append("Currency: MYR\n");
        sb.append("Date: ").append(LocalDate.now()).append("\n");
        sb.append("Provenance: ESTIMATE (standard MY construction percentages)\n\n");
        for (SummaryLine line : lines) {
            sb.append(String.format("  %-35s %12.2f  [%s]%n",
                    line.description(), line.amount(), line.basis()));
        }
        sb.append(String.format("%n  CONTRACT SUM: MYR %,.2f%n", contractSum));

        return new ReportOutput(
                "BIM-RPT-02",
                "JKR Contract Cost Summary",
                buildingId,
                LocalDate.now().toString(),
                "DRAFT",
                "MYR",
                material, labor, equipment, directCost,
                provisionalSums, pcSums,
                subtotalBeforeContingency, contingencies,
                subtotalBeforeSST, sst, contractSum,
                lines,
                "ESTIMATE",
                sb.toString()
        );
    }
}
