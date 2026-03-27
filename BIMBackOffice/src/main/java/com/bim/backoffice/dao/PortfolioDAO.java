package com.bim.backoffice.dao;

import com.bim.orm.BIMLogger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-office Portfolio DAO — cross-project analysis table.
 *
 * <p>Scans all {PREFIX}_BOM.db files in the library directory, opens each,
 * runs cost/schedule/carbon rollups, and returns a unified analysis table.
 * This is the ERP-style "all projects at a glance" view.
 *
 * <p>Serves: project analysis table, Kanban board, balanced scorecard.
 * All outputs are structured records — client renders as table, chart, or Excel.
 *
 * // Implementing TIER1_SRS.md §7 — Witness: W-PORTFOLIO-1
 */
public class PortfolioDAO {

    private static final String TAG = "PortfolioDAO";

    // ── Record types ────────────────────────────────────────────────

    /** One row in the project analysis table. */
    public record ProjectRow(
            String projectId,           // "SH", "TE", "BR", etc.
            String projectName,         // "SH Sample House"
            String facilityType,        // "BUILDING", "BRIDGE", "ROAD", "RAILWAY"
            String docStatus,           // "DR", "IP", "CO", "AP"
            int bomLineCount,           // total BOM lines
            int elementCount,           // expected elements from C_DocType
            double materialCostRm,      // 5D material total
            double laborCostRm,         // 5D labor total
            double equipmentCostRm,     // 5D equipment total
            double totalCostRm,         // 5D grand total
            double carbonKg,            // 6D total embodied carbon
            double carbonPerSqM,        // 6D carbon intensity
            int scheduleDays,           // 4D project duration
            double laborDays,           // 4D total labor-days
            int maintenanceAssets,      // 7D maintainable asset count
            double annualMaintEvents,   // 7D annual maintenance events
            double lifespanAvgYears,    // 7D weighted average lifespan
            String bomDbPath            // path to BOM database
    ) {}

    /** Kanban board card — one per project. */
    public record KanbanCard(
            String projectId,
            String projectName,
            String status,              // "Backlog", "In Progress", "Review", "Complete"
            double progress,            // 0.0-1.0
            double estCostRm,
            int bomLines,
            int blockers                // 0 for now (future: validation fail count)
    ) {}

    /** Balanced scorecard — four perspectives. */
    public record BalancedScorecard(
            List<KpiEntry> financial,
            List<KpiEntry> client,
            List<KpiEntry> process,
            List<KpiEntry> learning
    ) {}

    public record KpiEntry(String name, double value, double target,
                           String unit, String status) {}

    /** Portfolio summary — aggregates across all projects. */
    public record PortfolioSummary(
            List<ProjectRow> projects,
            int totalProjects,
            double totalCostRm,
            double totalCarbonKg,
            int totalBomLines,
            double avgCostPerProject,
            double avgCarbonPerSqM,
            Map<String, Integer> byFacilityType,
            Map<String, Double> costByFacilityType
    ) {}

    // ── Queries ─────────────────────────────────────────────────────

    /**
     * Scan all BOM databases and produce the project analysis table.
     * Opens each {PREFIX}_BOM.db, runs rollups, closes.
     *
     * @param libraryDir path to the library/ directory
     * @param compLibConn shared component_library.db connection
     */
    public PortfolioSummary analysePortfolio(String libraryDir, Connection compLibConn)
            throws SQLException {
        File dir = new File(libraryDir);
        File[] bomFiles = dir.listFiles((d, name) ->
                name.endsWith("_BOM.db") && !name.startsWith("_") && !name.equals("BOM.db"));

        List<ProjectRow> projects = new ArrayList<>();
        double totalCost = 0, totalCarbon = 0;
        int totalBomLines = 0;
        Map<String, Integer> byFacilityType = new LinkedHashMap<>();
        Map<String, Double> costByFacilityType = new LinkedHashMap<>();

        if (bomFiles == null) {
            BIMLogger.warn(TAG, "No BOM files found in {}", libraryDir);
            return new PortfolioSummary(projects, 0, 0, 0, 0, 0, 0,
                    byFacilityType, costByFacilityType);
        }

        CostDAO costDao = new CostDAO();
        SustainabilityDAO carbonDao = new SustainabilityDAO();
        ScheduleDAO schedDao = new ScheduleDAO();
        FacilityMgmtDAO fmDao = new FacilityMgmtDAO();

        for (File bomFile : bomFiles) {
            String prefix = bomFile.getName().replace("_BOM.db", "");
            try (Connection bomConn = DriverManager.getConnection(
                    "jdbc:sqlite:" + bomFile.getAbsolutePath())) {

                ProjectRow row = analyseProject(prefix, bomConn, compLibConn,
                        costDao, carbonDao, schedDao, fmDao, bomFile.getAbsolutePath());
                if (row != null) {
                    projects.add(row);
                    totalCost += row.totalCostRm;
                    totalCarbon += row.carbonKg;
                    totalBomLines += row.bomLineCount;
                    byFacilityType.merge(row.facilityType, 1, Integer::sum);
                    costByFacilityType.merge(row.facilityType, row.totalCostRm, Double::sum);
                }
            } catch (Exception e) {
                BIMLogger.warn(TAG, "Skip {} — {}", prefix, e.getMessage());
            }
        }

        // Sort by total cost descending
        projects.sort((a, b) -> Double.compare(b.totalCostRm, a.totalCostRm));

        double avgCost = projects.isEmpty() ? 0 : totalCost / projects.size();
        double avgCarbon = projects.stream()
                .filter(p -> p.carbonPerSqM > 0)
                .mapToDouble(ProjectRow::carbonPerSqM)
                .average().orElse(0);

        BIMLogger.info(TAG, "Portfolio: {} projects, RM {:.2f} total, {:.0f} kgCO2e",
                projects.size(), totalCost, totalCarbon);

        return new PortfolioSummary(projects, projects.size(), totalCost,
                totalCarbon, totalBomLines, avgCost, avgCarbon,
                byFacilityType, costByFacilityType);
    }

    /**
     * Kanban board — maps DocStatus to board columns.
     */
    public List<KanbanCard> kanbanBoard(String libraryDir, Connection compLibConn)
            throws SQLException {
        PortfolioSummary portfolio = analysePortfolio(libraryDir, compLibConn);
        List<KanbanCard> cards = new ArrayList<>();

        for (ProjectRow p : portfolio.projects()) {
            String status = switch (p.docStatus()) {
                case "DR" -> "Backlog";
                case "IP" -> "In Progress";
                case "CO" -> "Review";
                case "AP" -> "Complete";
                default -> "Backlog";
            };
            double progress = switch (p.docStatus()) {
                case "DR" -> 0.0;
                case "IP" -> 0.5;
                case "CO" -> 0.9;
                case "AP" -> 1.0;
                default -> 0.0;
            };
            cards.add(new KanbanCard(p.projectId(), p.projectName(),
                    status, progress, p.totalCostRm(), p.bomLineCount(), 0));
        }
        return cards;
    }

    /**
     * Balanced scorecard — four perspectives aggregated from portfolio.
     */
    public BalancedScorecard balancedScorecard(String libraryDir, Connection compLibConn)
            throws SQLException {
        PortfolioSummary p = analysePortfolio(libraryDir, compLibConn);

        // Financial perspective
        List<KpiEntry> financial = List.of(
                new KpiEntry("Total Portfolio Cost", p.totalCostRm(), 0, "RM",
                        p.totalCostRm() > 0 ? "GREEN" : "RED"),
                new KpiEntry("Avg Cost per Project", p.avgCostPerProject(), 0, "RM", "GREEN"),
                new KpiEntry("Cost by Building", p.costByFacilityType().getOrDefault("BUILDING", 0.0),
                        0, "RM", "GREEN"),
                new KpiEntry("Cost by Infrastructure", p.costByFacilityType().entrySet().stream()
                        .filter(e -> !"BUILDING".equals(e.getKey()))
                        .mapToDouble(Map.Entry::getValue).sum(), 0, "RM", "GREEN")
        );

        // Client perspective
        long completed = p.projects().stream().filter(r -> "AP".equals(r.docStatus())).count();
        long inProgress = p.projects().stream().filter(r -> "IP".equals(r.docStatus())).count();
        List<KpiEntry> client = List.of(
                new KpiEntry("Total Projects", p.totalProjects(), 0, "count", "GREEN"),
                new KpiEntry("Completed", completed, 0, "count",
                        completed > 0 ? "GREEN" : "AMBER"),
                new KpiEntry("In Progress", inProgress, 0, "count", "GREEN"),
                new KpiEntry("Avg Elements per Project",
                        p.projects().stream().mapToInt(ProjectRow::elementCount).average().orElse(0),
                        0, "count", "GREEN")
        );

        // Process perspective
        List<KpiEntry> process = List.of(
                new KpiEntry("Total BOM Lines", p.totalBomLines(), 0, "lines", "GREEN"),
                new KpiEntry("Avg BOM Lines per Project",
                        p.totalProjects() > 0 ? (double) p.totalBomLines() / p.totalProjects() : 0,
                        0, "lines", "GREEN"),
                new KpiEntry("Building Types", p.byFacilityType().size(), 0, "types", "GREEN"),
                new KpiEntry("Facility Types",
                        p.byFacilityType().keySet().stream().filter(k -> !"BUILDING".equals(k)).count(),
                        0, "infra types", "GREEN")
        );

        // Learning / sustainability perspective
        List<KpiEntry> learning = List.of(
                new KpiEntry("Total Carbon", p.totalCarbonKg(), 0, "kgCO2e",
                        p.totalCarbonKg() > 0 ? "GREEN" : "RED"),
                new KpiEntry("Avg Carbon Intensity", p.avgCarbonPerSqM(), 50.0, "kgCO2e/m2",
                        p.avgCarbonPerSqM() <= 50 ? "GREEN" : "AMBER"),
                new KpiEntry("Avg Maintenance Assets",
                        p.projects().stream().mapToInt(ProjectRow::maintenanceAssets).average().orElse(0),
                        0, "assets", "GREEN"),
                new KpiEntry("Projects with Carbon Data",
                        p.projects().stream().filter(r -> r.carbonKg() > 0).count(),
                        p.totalProjects(), "count",
                        p.projects().stream().allMatch(r -> r.carbonKg() > 0) ? "GREEN" : "AMBER")
        );

        return new BalancedScorecard(financial, client, process, learning);
    }

    // ── Internal ────────────────────────────────────────────────────

    private ProjectRow analyseProject(String prefix, Connection bomConn,
                                       Connection compLibConn,
                                       CostDAO costDao, SustainabilityDAO carbonDao,
                                       ScheduleDAO schedDao, FacilityMgmtDAO fmDao,
                                       String bomDbPath) throws SQLException {
        // Read building metadata from C_DocType
        String buildingName = prefix;
        String facilityType = "BUILDING";
        String docStatus = "IP";
        int expectedElements = 0;

        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT d.Name, b.m_product_category_id, d.doc_sub_type, d.ProjectName, d.ExpectedElements " +
                "FROM C_DocType d " +
                // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
                "LEFT JOIN m_bom b ON b.doc_sub_type = d.doc_sub_type " +
                "  AND b.bom_id NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) AND b.is_active = 1 " +
                "WHERE d.IsActive = 1 LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    buildingName = rs.getString("Name");
                    String baseType = rs.getString("m_product_category_id");
                    expectedElements = rs.getInt("ExpectedElements");
                    if ("IN".equals(baseType)) {
                        String sub = rs.getString("doc_sub_type");
                        facilityType = switch (sub) {
                            case "BR" -> "BRIDGE";
                            case "RD" -> "ROAD";
                            case "RL" -> "RAILWAY";
                            default -> "BUILDING";
                        };
                    }
                }
            }
        } catch (SQLException e) {
            // C_DocType may not exist in all BOM files
        }

        // Count BOM lines
        int bomLineCount = 0;
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE is_active = 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) bomLineCount = rs.getInt(1);
            }
        }

        if (bomLineCount == 0) return null; // skip empty databases

        // 5D Cost
        double matCost = 0, labCost = 0, eqCost = 0, totalCost = 0;
        try {
            CostDAO.CostSummary cost = costDao.costBreakdown(bomConn, compLibConn, prefix);
            matCost = cost.materialTotal();
            labCost = cost.laborTotal();
            eqCost = cost.equipmentTotal();
            totalCost = cost.grandTotal();
        } catch (Exception e) {
            BIMLogger.warn(TAG, "{} cost failed: {}", prefix, e.getMessage());
        }

        // 6D Carbon
        double carbonKg = 0, carbonPerSqM = 0;
        try {
            SustainabilityDAO.CarbonSummary carbon =
                    carbonDao.carbonFootprint(bomConn, compLibConn, prefix);
            carbonKg = carbon.totalCarbonKg();
            carbonPerSqM = carbon.carbonPerSqM();
        } catch (Exception e) {
            BIMLogger.warn(TAG, "{} carbon failed: {}", prefix, e.getMessage());
        }

        // 4D Schedule
        int scheduleDays = 0;
        double laborDays = 0;
        try {
            ScheduleDAO.ScheduleSummary sched =
                    schedDao.constructionSchedule(bomConn, compLibConn, prefix, "2026-04-01");
            scheduleDays = sched.totalDays();
            laborDays = sched.totalLaborDays();
        } catch (Exception e) {
            BIMLogger.warn(TAG, "{} schedule failed: {}", prefix, e.getMessage());
        }

        // 7D Maintenance
        int maintAssets = 0;
        double annualEvents = 0, avgLifespan = 0;
        try {
            FacilityMgmtDAO.MaintenanceSchedule maint =
                    fmDao.maintenanceSchedule(bomConn, compLibConn, prefix);
            maintAssets = maint.totalAssets();
            annualEvents = maint.annualMaintenanceEvents();
            avgLifespan = maint.items().stream()
                    .mapToInt(FacilityMgmtDAO.MaintenanceItem::lifespanYears)
                    .average().orElse(0);
        } catch (Exception e) {
            BIMLogger.warn(TAG, "{} maintenance failed: {}", prefix, e.getMessage());
        }

        return new ProjectRow(prefix, buildingName, facilityType, docStatus,
                bomLineCount, expectedElements, matCost, labCost, eqCost, totalCost,
                carbonKg, carbonPerSqM, scheduleDays, laborDays,
                maintAssets, annualEvents, avgLifespan, bomDbPath);
    }
}
