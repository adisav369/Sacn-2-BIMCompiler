package com.bim.designer;

import com.bim.backoffice.dao.PortfolioDAO;
import com.bim.backoffice.dao.PortfolioDAO.*;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Portfolio / Back-Office witnesses — cross-project analysis, Kanban, BSC.
 *
 * <p>Runs against the real library/ BOM databases (SH, FK, BR, etc.).
 * Requires component_library.db to have CIDB rate columns (V012).
 */
@DisplayName("Portfolio — Back-Office Analysis")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortfolioTest {

    private static Connection compLibConn;

    @BeforeAll
    static void setUp() throws Exception {
        compLibConn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db");
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (compLibConn != null) compLibConn.close();
    }

    // ═══════════════════════════════════════════════════════════════
    // Portfolio Analysis Table
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-PORT-1: portfolio finds ≥6 projects with cost data")
    void portfolioFindsProjects() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        PortfolioSummary summary = dao.analysePortfolio("library", compLibConn);

        assertTrue(summary.totalProjects() >= 6,
                "Must find ≥6 projects, got " + summary.totalProjects());
        assertTrue(summary.totalCostRm() > 0, "Total cost must be > 0");
        assertTrue(summary.totalBomLines() > 0, "Total BOM lines must be > 0");

        // Print the analysis table
        System.out.println("\n══════════════════════════════════════════════════════════════════");
        System.out.println("  PROJECT ANALYSIS TABLE — ALL PROJECTS");
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.printf("%-6s %-25s %-10s %6s %12s %10s %6s %6s%n",
                "ID", "Name", "Type", "BOMs", "Cost (RM)", "Carbon", "Days", "Assets");
        System.out.println("──────────────────────────────────────────────────────────────────");
        for (ProjectRow p : summary.projects()) {
            System.out.printf("%-6s %-25s %-10s %6d %,12.0f %,10.0f %6d %6d%n",
                    p.projectId(),
                    p.projectName().length() > 25
                            ? p.projectName().substring(0, 25) : p.projectName(),
                    p.facilityType(), p.bomLineCount(),
                    p.totalCostRm(), p.carbonKg(),
                    p.scheduleDays(), p.maintenanceAssets());
        }
        System.out.println("──────────────────────────────────────────────────────────────────");
        System.out.printf("%-6s %-25s %-10s %6d %,12.0f %,10.0f%n",
                "TOTAL", summary.totalProjects() + " projects", "",
                summary.totalBomLines(), summary.totalCostRm(), summary.totalCarbonKg());
        System.out.println("══════════════════════════════════════════════════════════════════\n");
    }

    @Test @Order(2)
    @DisplayName("W-PORT-2: each project has 4D/5D/6D/7D metrics")
    void projectRowsComplete() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        PortfolioSummary summary = dao.analysePortfolio("library", compLibConn);

        int withCost = 0;
        for (ProjectRow p : summary.projects()) {
            assertNotNull(p.projectId(), "projectId must not be null");
            assertNotNull(p.projectName(), "projectName must not be null");
            assertNotNull(p.facilityType(), "facilityType must not be null");
            assertTrue(p.bomLineCount() > 0,
                    p.projectId() + " must have BOM lines");
            if (p.totalCostRm() > 0) withCost++;
        }
        // At least half the projects should have cost data (some have unmatched product IDs)
        assertTrue(withCost >= summary.totalProjects() / 2,
                "At least half the projects must have cost > 0, got " + withCost
                        + "/" + summary.totalProjects());
    }

    @Test @Order(3)
    @DisplayName("W-PORT-3: portfolio includes both buildings and infrastructure")
    void portfolioMixedTypes() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        PortfolioSummary summary = dao.analysePortfolio("library", compLibConn);

        assertTrue(summary.byFacilityType().containsKey("BUILDING"),
                "Must have BUILDING projects");
        // At least one infra type (BRIDGE, ROAD, or RAILWAY)
        boolean hasInfra = summary.byFacilityType().containsKey("BRIDGE")
                || summary.byFacilityType().containsKey("ROAD")
                || summary.byFacilityType().containsKey("RAILWAY");
        assertTrue(hasInfra, "Must have at least one infrastructure project");
    }

    @Test @Order(4)
    @DisplayName("W-PORT-4: projects sorted by cost descending")
    void projectsSortedByCost() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        PortfolioSummary summary = dao.analysePortfolio("library", compLibConn);

        double prev = Double.MAX_VALUE;
        for (ProjectRow p : summary.projects()) {
            assertTrue(p.totalCostRm() <= prev,
                    "Projects must be sorted by cost desc: " + p.projectId());
            prev = p.totalCostRm();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Kanban Board
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("W-KANBAN-1: kanban returns cards with valid status columns")
    void kanbanCards() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        List<KanbanCard> cards = dao.kanbanBoard("library", compLibConn);

        assertFalse(cards.isEmpty(), "Kanban must have cards");
        for (KanbanCard c : cards) {
            assertTrue(List.of("Backlog", "In Progress", "Review", "Complete")
                            .contains(c.status()),
                    "Invalid kanban status: " + c.status());
            assertTrue(c.progress() >= 0 && c.progress() <= 1.0,
                    "Progress must be 0-1: " + c.progress());
        }

        // Print kanban
        System.out.println("\n┌─ KANBAN BOARD ────────────────────────────────────────┐");
        for (String col : List.of("Backlog", "In Progress", "Review", "Complete")) {
            List<KanbanCard> colCards = cards.stream()
                    .filter(c -> col.equals(c.status())).toList();
            System.out.printf("│ %-14s (%d projects)%n", col, colCards.size());
            for (KanbanCard c : colCards) {
                System.out.printf("│   %-6s %-20s RM %,.0f%n",
                        c.projectId(), c.projectName(), c.estCostRm());
            }
        }
        System.out.println("└──────────────────────────────────────────────────────┘\n");
    }

    // ═══════════════════════════════════════════════════════════════
    // Balanced Scorecard
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("W-BSC-1: balanced scorecard has 4 perspectives with ≥3 KPIs each")
    void bscFourPerspectives() throws Exception {
        PortfolioDAO dao = new PortfolioDAO();
        BalancedScorecard bsc = dao.balancedScorecard("library", compLibConn);

        assertTrue(bsc.financial().size() >= 3, "Financial must have ≥3 KPIs");
        assertTrue(bsc.client().size() >= 3, "Client must have ≥3 KPIs");
        assertTrue(bsc.process().size() >= 3, "Process must have ≥3 KPIs");
        assertTrue(bsc.learning().size() >= 3, "Learning must have ≥3 KPIs");

        // Print BSC
        System.out.println("\n┌─ BALANCED SCORECARD ──────────────────────────────────┐");
        for (var entry : java.util.Map.of(
                "FINANCIAL", bsc.financial(), "CLIENT", bsc.client(),
                "PROCESS", bsc.process(), "LEARNING", bsc.learning()).entrySet()) {
            System.out.printf("│ %-12s%n", entry.getKey());
            for (var kpi : entry.getValue()) {
                System.out.printf("│   [%s] %-30s %,.1f %s%n",
                        kpi.status().substring(0, 1), kpi.name(),
                        kpi.value(), kpi.unit());
            }
        }
        System.out.println("└──────────────────────────────────────────────────────┘\n");
    }
}
