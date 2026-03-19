package com.bim.designer;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ScheduleDAO;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 4D Schedule + 5D Cost DAO witnesses.
 *
 * <p>Uses in-memory SQLite with BOM + component_library test data.
 * CIDB Malaysia 2024 rates from Federation addon boq/comprehensive_boq_export.py.
 */
@DisplayName("4D Schedule + 5D Cost")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Schedule5DCostTest {

    private static Connection bomConn;
    private static Connection compLibConn;

    @BeforeAll
    static void setUp() throws Exception {
        // ── BOM database ────────────────────────────────────────
        bomConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = bomConn.createStatement()) {
            s.execute("""
                CREATE TABLE m_bom (
                    bom_id TEXT PRIMARY KEY, bom_name TEXT NOT NULL,
                    bom_type TEXT NOT NULL, bom_category TEXT,
                    is_active INTEGER DEFAULT 1,
                    aabb_width_mm INTEGER DEFAULT 0,
                    aabb_depth_mm INTEGER DEFAULT 0,
                    aabb_height_mm INTEGER DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE m_bom_line (
                    bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bom_id TEXT NOT NULL, child_product_id TEXT,
                    qty INTEGER NOT NULL DEFAULT 1,
                    is_active INTEGER DEFAULT 1, storey TEXT
                )
                """);
            s.execute("""
                INSERT INTO m_bom VALUES
                ('SH_GF', 'Ground Floor', 'FLOOR', 'GF', 1, 9000, 7000, 3000),
                ('SH_STR', 'Structure', 'SET', 'STR', 1, 9000, 7000, 6000),
                ('SH_ARC', 'Architecture', 'SET', 'ARC', 1, 9000, 7000, 6000),
                ('SH_FIN', 'Finishes', 'SET', 'FIN', 1, 9000, 7000, 6000)
                """);
            s.execute("""
                INSERT INTO m_bom_line (bom_id, child_product_id, qty, is_active, storey) VALUES
                ('SH_STR', 'col_300x300', 6, 1, 'GF'),
                ('SH_STR', 'beam_300x200', 8, 1, 'GF'),
                ('SH_STR', 'slab_150', 2, 1, 'GF'),
                ('SH_ARC', 'wall_ext_200', 10, 1, 'GF'),
                ('SH_ARC', 'door_900x2100', 5, 1, 'GF'),
                ('SH_ARC', 'window_1200x1500', 6, 1, 'GF'),
                ('SH_ARC', 'roof_metal', 2, 1, 'RF'),
                ('SH_FIN', 'railing_ss', 4, 1, 'GF'),
                ('SH_STR', 'stair_precast', 1, 1, 'GF'),
                ('SH_STR', 'footing_pad', 6, 1, 'GF')
                """);
        }

        // ── Component library with CIDB rates ───────────────────
        compLibConn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = compLibConn.createStatement()) {
            s.execute("""
                CREATE TABLE M_Product (
                    product_id TEXT PRIMARY KEY,
                    product_type TEXT NOT NULL,
                    width REAL DEFAULT 0, depth REAL DEFAULT 0, height REAL DEFAULT 0,
                    is_active INTEGER DEFAULT 1,
                    unit_cost_rm REAL DEFAULT 0, cost_uom TEXT DEFAULT 'EA',
                    cost_spec TEXT,
                    construction_phase TEXT, construction_sequence INTEGER DEFAULT 0,
                    labor_resource TEXT, labor_rate_rm_per_day REAL DEFAULT 0,
                    labor_crew_size INTEGER DEFAULT 1, productivity_rate REAL DEFAULT 1,
                    equipment_type TEXT, equipment_rate_rm_per_day REAL DEFAULT 0,
                    equipment_duration_factor REAL DEFAULT 0,
                    carbon_kg_per_unit REAL DEFAULT 0,
                    recyclability TEXT DEFAULT 'UNKNOWN',
                    eol_strategy TEXT DEFAULT 'LANDFILL',
                    lifespan_years INTEGER DEFAULT 50,
                    maintenance_interval_months INTEGER DEFAULT 12
                )
                """);
            s.execute("""
                INSERT INTO M_Product (product_id, product_type, is_active,
                    unit_cost_rm, cost_uom, construction_phase, construction_sequence,
                    labor_resource, labor_rate_rm_per_day, labor_crew_size,
                    productivity_rate, equipment_type, equipment_rate_rm_per_day,
                    equipment_duration_factor) VALUES
                ('footing_pad', 'ELEMENT', 1, 320.0, 'M3', 'Substructure', 1,
                    'CONCRETE_GANG', 145.0, 6, 15.0, 'CONCRETE_PUMP', 950.0, 0.3),
                ('col_300x300', 'COLUMN', 1, 1250.0, 'M', 'Superstructure', 2,
                    'STEEL_ERECTOR', 195.0, 4, 6.0, 'MOBILE_CRANE_20T', 1850.0, 0.5),
                ('beam_300x200', 'BEAM', 1, 680.0, 'M', 'Superstructure', 3,
                    'STEEL_ERECTOR', 195.0, 4, 8.0, 'MOBILE_CRANE_20T', 1850.0, 0.5),
                ('slab_150', 'SLAB', 1, 285.0, 'M2', 'Superstructure', 4,
                    'CONCRETE_GANG', 145.0, 6, 35.0, 'CONCRETE_PUMP', 950.0, 0.3),
                ('stair_precast', 'STAIR', 1, 3500.0, 'EA', 'Superstructure', 4,
                    'CONCRETE_GANG', 145.0, 4, 2.0, 'MOBILE_CRANE_20T', 1850.0, 0.3),
                ('wall_ext_200', 'WALL', 1, 145.0, 'M2', 'Architecture', 6,
                    'MASON', 155.0, 3, 12.0, NULL, 0, 0),
                ('door_900x2100', 'DOOR', 1, 2850.0, 'EA', 'Architecture', 7,
                    'CARPENTER', 165.0, 2, 6.0, NULL, 0, 0),
                ('window_1200x1500', 'WINDOW', 1, 1580.0, 'EA', 'Architecture', 7,
                    'CARPENTER', 165.0, 2, 4.0, NULL, 0, 0),
                ('roof_metal', 'ROOF', 1, 238.0, 'M2', 'Architecture', 8,
                    'ROOFER', 175.0, 3, 20.0, 'SCISSOR_LIFT_8M', 285.0, 0.3),
                ('railing_ss', 'RAILING', 1, 280.0, 'M', 'Finishes', 10,
                    'STEEL_ERECTOR', 195.0, 2, 15.0, NULL, 0, 0)
                """);
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (bomConn != null) bomConn.close();
        if (compLibConn != null) compLibConn.close();
    }

    // ═══════════════════════════════════════════════════════════════
    // 4D Schedule
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("W-4D-SCHED-1: constructionSchedule returns tasks ordered by sequence")
    void scheduleNonEmpty() throws Exception {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary summary =
                dao.constructionSchedule(bomConn, compLibConn, "SH", "2026-04-01");

        assertNotNull(summary);
        assertFalse(summary.tasks().isEmpty(), "Schedule must have tasks");
        assertTrue(summary.totalTasks() > 0);
        assertTrue(summary.totalDays() > 0, "Project duration must be > 0 days");

        // Verify ordering: each task sequence >= previous
        int prevSeq = 0;
        for (var task : summary.tasks()) {
            assertTrue(task.sequence() >= prevSeq,
                    "Tasks must be ordered by sequence: " + task.taskId());
            prevSeq = task.sequence();
        }
    }

    @Test @Order(2)
    @DisplayName("W-4D-SCHED-2: phases follow construction order (Substructure → Superstructure → Architecture → Finishes)")
    void phaseOrdering() throws Exception {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary summary =
                dao.constructionSchedule(bomConn, compLibConn, "SH", "2026-04-01");

        // Collect phase first-appearances
        java.util.List<String> phases = new java.util.ArrayList<>();
        for (var task : summary.tasks()) {
            if (!phases.contains(task.phase())) phases.add(task.phase());
        }

        // Substructure must come before Superstructure
        int subIdx = phases.indexOf("Substructure");
        int supIdx = phases.indexOf("Superstructure");
        int arcIdx = phases.indexOf("Architecture");
        int finIdx = phases.indexOf("Finishes");

        assertTrue(subIdx >= 0, "Must have Substructure phase");
        assertTrue(supIdx > subIdx, "Superstructure must follow Substructure");
        assertTrue(arcIdx > supIdx, "Architecture must follow Superstructure");
        assertTrue(finIdx > arcIdx, "Finishes must follow Architecture");
    }

    @Test @Order(3)
    @DisplayName("W-4D-SCHED-3: dates are valid and finish > start")
    void datesValid() throws Exception {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary summary =
                dao.constructionSchedule(bomConn, compLibConn, "SH", "2026-04-01");

        for (var task : summary.tasks()) {
            LocalDate start = LocalDate.parse(task.startDate());
            LocalDate finish = LocalDate.parse(task.finishDate());
            assertTrue(finish.isAfter(start) || finish.isEqual(start),
                    "Finish must be >= start for " + task.taskId());
        }

        LocalDate projStart = LocalDate.parse(summary.projectStartDate());
        LocalDate projFinish = LocalDate.parse(summary.projectFinishDate());
        assertTrue(projFinish.isAfter(projStart), "Project finish must be after start");
    }

    @Test @Order(4)
    @DisplayName("W-4D-SCHED-4: labor days by resource populated")
    void laborByResource() throws Exception {
        ScheduleDAO dao = new ScheduleDAO();
        ScheduleDAO.ScheduleSummary summary =
                dao.constructionSchedule(bomConn, compLibConn, "SH", "2026-04-01");

        assertFalse(summary.laborDaysByResource().isEmpty());
        assertTrue(summary.totalLaborDays() > 0, "Total labor days must be > 0");

        // Should have multiple resources
        assertTrue(summary.laborDaysByResource().size() >= 3,
                "Must have ≥3 labor resources (CONCRETE_GANG, STEEL_ERECTOR, MASON, ...)");
    }

    @Test @Order(5)
    @DisplayName("W-4D-SCHED-5: addWorkingDays skips Sundays")
    void workingDaysSkipSunday() {
        // 2026-04-01 is Wednesday. Add 5 working days → skip Sunday.
        LocalDate start = LocalDate.of(2026, 4, 1);  // Wed
        LocalDate result = ScheduleDAO.addWorkingDays(start, 5);
        // Wed+5 working days (Thu,Fri,Sat,Mon,Tue) = Apr 7 (Tue)
        // Actually: Thu(2), Fri(3), Sat(4), Sun(skip), Mon(6), Tue(7)
        assertEquals(LocalDate.of(2026, 4, 7), result,
                "5 working days from Wed should be Tue (skip Sunday)");
    }

    // ═══════════════════════════════════════════════════════════════
    // 5D Cost
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("W-5D-COST-1: costBreakdown returns lines with material + labor + equipment")
    void costBreakdownNonEmpty() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary summary = dao.costBreakdown(bomConn, compLibConn, "SH");

        assertNotNull(summary);
        assertFalse(summary.lines().isEmpty(), "Cost lines must not be empty");
        assertTrue(summary.grandTotal() > 0, "Grand total must be > 0");
        assertTrue(summary.materialTotal() > 0, "Material total must be > 0");
        assertTrue(summary.laborTotal() > 0, "Labor total must be > 0");
    }

    @Test @Order(11)
    @DisplayName("W-5D-COST-2: grandTotal = material + labor + equipment")
    void costTotalsMath() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary summary = dao.costBreakdown(bomConn, compLibConn, "SH");

        double sum = summary.materialTotal() + summary.laborTotal() + summary.equipmentTotal();
        assertEquals(sum, summary.grandTotal(), 0.01,
                "Grand total must equal material + labor + equipment");

        // Per-line check
        for (var line : summary.lines()) {
            double lineSum = line.materialCost() + line.laborCost() + line.equipmentCost();
            assertEquals(lineSum, line.totalCost(), 0.01,
                    "Line total must equal mat + lab + eq for " + line.bomId());
        }
    }

    @Test @Order(12)
    @DisplayName("W-5D-COST-3: percentages sum to 100")
    void costPercentages() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary summary = dao.costBreakdown(bomConn, compLibConn, "SH");

        double pctSum = summary.materialPct() + summary.laborPct() + summary.equipmentPct();
        assertEquals(100.0, pctSum, 0.1,
                "Cost percentages must sum to 100%");
        assertTrue(summary.materialPct() > summary.laborPct(),
                "Material should be largest cost component");
    }

    @Test @Order(13)
    @DisplayName("W-5D-COST-4: byDiscipline keys match BOM categories")
    void costByDiscipline() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary summary = dao.costBreakdown(bomConn, compLibConn, "SH");

        assertFalse(summary.byDiscipline().isEmpty());
        // Should have STR, ARC, FIN disciplines
        assertTrue(summary.byDiscipline().containsKey("STR"), "Must have STR discipline");
        assertTrue(summary.byDiscipline().containsKey("ARC"), "Must have ARC discipline");
    }

    @Test @Order(14)
    @DisplayName("W-5D-COST-5: byPhase covers construction phases")
    void costByPhase() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostSummary summary = dao.costBreakdown(bomConn, compLibConn, "SH");

        assertFalse(summary.byPhase().isEmpty());
        assertTrue(summary.byPhase().containsKey("Superstructure"),
                "Must have Superstructure phase cost");
    }

    @Test @Order(15)
    @DisplayName("W-5D-COST-6: elementCost returns single-product cost lookup")
    void elementCostLookup() throws Exception {
        CostDAO dao = new CostDAO();
        CostDAO.CostLine line = dao.elementCost(compLibConn, "beam_300x200", 10);

        assertNotNull(line);
        assertEquals(6800.0, line.materialCost(), 0.01,
                "10 beams × RM680/M = RM6800");
        assertTrue(line.laborCost() > 0, "Labor cost must be > 0");
        assertTrue(line.equipmentCost() > 0, "Equipment cost must be > 0 (crane)");
    }
}
