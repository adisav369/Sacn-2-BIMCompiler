package com.bim.backoffice.report;

import com.bim.backoffice.dao.PortfolioDAO;
import com.bim.backoffice.dao.PortfolioDAO.PortfolioSummary;
import com.bim.backoffice.dao.PortfolioDAO.ProjectRow;
import com.bim.orm.BIMLogger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * BIM-RPT-16: Portfolio Dashboard — multi-project WIP, overrun flags.
 *
 * <p>Wraps PortfolioDAO.analysePortfolio() in report format with
 * overrun risk identification and facility type breakdown.
 *
 * // Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-16 — Witness: W-RPT-PORTFOLIO
 */
public class PortfolioDashboardReport {

    private static final String TAG = "PortfolioDashboardReport";

    public record OverrunRisk(String projectId, String projectName,
                              double totalCostRm, int scheduleDays,
                              String riskReason) {}

    public record PortfolioOutput(int totalProjects, int wipCount,
                                  double totalCostRm, double avgCarbonPerSqM,
                                  Map<String, Integer> byFacilityType,
                                  Map<String, Double> costByFacilityType,
                                  List<ProjectRow> projects,
                                  List<OverrunRisk> topOverrunRisks,
                                  String plainText) {}

    /**
     * Generate portfolio dashboard from PortfolioDAO analysis.
     */
    public PortfolioOutput generate(String libraryDir, Connection compLibConn)
            throws SQLException {
        PortfolioDAO dao = new PortfolioDAO();
        PortfolioSummary portfolio = dao.analysePortfolio(libraryDir, compLibConn);

        // Count WIP (In Progress)
        long wipCount = portfolio.projects().stream()
                .filter(p -> "IP".equals(p.docStatus()))
                .count();

        // Identify overrun risks: projects with high cost or long duration
        List<OverrunRisk> risks = new ArrayList<>();
        if (!portfolio.projects().isEmpty()) {
            double avgCost = portfolio.avgCostPerProject();
            int avgDays = (int) portfolio.projects().stream()
                    .mapToInt(ProjectRow::scheduleDays)
                    .average().orElse(0);

            for (ProjectRow p : portfolio.projects()) {
                List<String> reasons = new ArrayList<>();
                if (avgCost > 0 && p.totalCostRm() > avgCost * 1.5) {
                    reasons.add(String.format("Cost %.0f%% above average",
                            ((p.totalCostRm() / avgCost) - 1) * 100));
                }
                if (avgDays > 0 && p.scheduleDays() > avgDays * 1.5) {
                    reasons.add(String.format("Duration %.0f%% above average",
                            ((double) p.scheduleDays() / avgDays - 1) * 100));
                }
                if (p.carbonPerSqM() > 50) {
                    reasons.add(String.format("Carbon %.0f kgCO2e/m2 exceeds 50 target",
                            p.carbonPerSqM()));
                }
                if (!reasons.isEmpty()) {
                    risks.add(new OverrunRisk(p.projectId(), p.projectName(),
                            p.totalCostRm(), p.scheduleDays(),
                            String.join("; ", reasons)));
                }
            }
            // Sort risks by cost descending, take top 5
            risks.sort(Comparator.comparingDouble(OverrunRisk::totalCostRm).reversed());
            if (risks.size() > 5) risks = risks.subList(0, 5);
        }

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("PORTFOLIO DASHBOARD\n");
        sb.append("═".repeat(80)).append("\n\n");
        sb.append(String.format("Total Projects:    %d%n", portfolio.totalProjects()));
        sb.append(String.format("Work in Progress:  %d%n", wipCount));
        sb.append(String.format("Total Cost:        RM %,.2f%n", portfolio.totalCostRm()));
        sb.append(String.format("Total Carbon:      %,.0f kgCO2e%n", portfolio.totalCarbonKg()));
        sb.append(String.format("Avg Carbon/m2:     %.1f kgCO2e/m2%n", portfolio.avgCarbonPerSqM()));
        sb.append("\n");

        sb.append("FACILITY TYPE BREAKDOWN\n");
        sb.append("─".repeat(50)).append("\n");
        for (var entry : portfolio.byFacilityType().entrySet()) {
            double typeCost = portfolio.costByFacilityType().getOrDefault(entry.getKey(), 0.0);
            sb.append(String.format("  %-20s %d projects  RM %,.2f%n",
                    entry.getKey(), entry.getValue(), typeCost));
        }
        sb.append("\n");

        sb.append("PROJECT SUMMARY\n");
        sb.append("─".repeat(80)).append("\n");
        sb.append(String.format("%-6s %-25s %-10s %15s %8s %10s%n",
                "ID", "Name", "Status", "Cost (RM)", "Days", "Carbon/m2"));
        sb.append("─".repeat(80)).append("\n");
        for (ProjectRow p : portfolio.projects()) {
            sb.append(String.format("%-6s %-25s %-10s %,15.2f %8d %10.1f%n",
                    p.projectId(),
                    p.projectName().length() > 25
                            ? p.projectName().substring(0, 22) + "..." : p.projectName(),
                    p.docStatus(), p.totalCostRm(), p.scheduleDays(), p.carbonPerSqM()));
        }
        sb.append("\n");

        if (!risks.isEmpty()) {
            sb.append("TOP OVERRUN RISKS\n");
            sb.append("─".repeat(80)).append("\n");
            for (OverrunRisk risk : risks) {
                sb.append(String.format("  %-6s %-25s RM %,.2f — %s%n",
                        risk.projectId(), risk.projectName(),
                        risk.totalCostRm(), risk.riskReason()));
            }
        }

        BIMLogger.info(TAG, "Portfolio dashboard: {} projects, {} WIP, RM {:.2f}",
                portfolio.totalProjects(), wipCount, portfolio.totalCostRm());

        return new PortfolioOutput(portfolio.totalProjects(), (int) wipCount,
                portfolio.totalCostRm(), portfolio.avgCarbonPerSqM(),
                portfolio.byFacilityType(), portfolio.costByFacilityType(),
                portfolio.projects(), risks, sb.toString());
    }
}
