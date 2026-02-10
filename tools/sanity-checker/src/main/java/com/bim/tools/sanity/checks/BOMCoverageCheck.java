package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * BOM Coverage Check: Verifies all physical elements are grouped into assemblies.
 *
 * Phase 83: Detects orphaned elements not in any BOM assembly.
 * Reports by storey and IFC class, flags when coverage drops below threshold.
 *
 * BOM principle: Every physical element should belong to at least one assembly
 * for proper outliner hierarchy, en-bloc procurement, and cost rollup.
 */
public class BOMCoverageCheck implements SanityCheck {

    // Elements exempt from BOM coverage (spatial/organizational, not physical)
    private static final Set<String> EXEMPT_CLASSES = Set.of(
        "IfcBuildingStorey", "IfcBuilding", "IfcSite",
        "IfcSpace", "IfcElementAssembly", "IfcAlarm"
    );

    private static final double PASS_THRESHOLD = 95.0; // % coverage required for PASS

    @Override
    public String getId() { return "bom_coverage"; }

    @Override
    public String getName() { return "BOM Assembly Coverage"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();

        // Check if assembly tables exist
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            if (!tableExists(conn, "element_assemblies") || !tableExists(conn, "assembly_components")) {
                return CheckResult.pass(getId(), getName())
                    .summary("BOM assembly tables not present (pre-BOM database)")
                    .build();
            }

            return checkCoverage(conn);
        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    private CheckResult checkCoverage(Connection conn) throws SQLException {
        // Count total physical elements
        int totalElements = 0;
        int assembledElements = 0;

        // Orphan breakdown by class
        Map<String, Integer> orphansByClass = new LinkedHashMap<>();
        // Orphan breakdown by storey
        Map<String, Integer> orphansByStorey = new LinkedHashMap<>();
        // Assembly type summary
        Map<String, int[]> assemblyStats = new LinkedHashMap<>(); // [count, children]

        // 1. Count orphaned elements (not in any assembly)
        String orphanSql = """
            SELECT m.ifc_class, m.storey, COUNT(*) as cnt
            FROM elements_meta m
            LEFT JOIN assembly_components ac ON m.guid = ac.component_guid
            WHERE ac.assembly_guid IS NULL
            GROUP BY m.ifc_class, m.storey
            ORDER BY cnt DESC
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(orphanSql)) {
            while (rs.next()) {
                String ifcClass = rs.getString("ifc_class");
                String storey = rs.getString("storey");
                int cnt = rs.getInt("cnt");

                if (EXEMPT_CLASSES.contains(ifcClass)) continue;

                orphansByClass.merge(ifcClass, cnt, Integer::sum);
                if (storey != null) {
                    orphansByStorey.merge(storey, cnt, Integer::sum);
                }
            }
        }

        // 2. Count total physical elements
        String totalSql = """
            SELECT COUNT(*) as cnt FROM elements_meta
            WHERE ifc_class NOT IN ('IfcBuildingStorey','IfcBuilding','IfcSite','IfcSpace','IfcElementAssembly')
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(totalSql)) {
            if (rs.next()) totalElements = rs.getInt("cnt");
        }

        int totalOrphans = orphansByClass.values().stream().mapToInt(Integer::intValue).sum();
        assembledElements = totalElements - totalOrphans;

        // 3. Assembly type statistics
        String assemblySql = """
            SELECT ea.assembly_type,
                   COUNT(DISTINCT ea.assembly_guid) as asm_count,
                   COUNT(ac.component_guid) as child_count
            FROM element_assemblies ea
            JOIN assembly_components ac ON ea.assembly_guid = ac.assembly_guid
            GROUP BY ea.assembly_type
            ORDER BY child_count DESC
            """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(assemblySql)) {
            while (rs.next()) {
                assemblyStats.put(rs.getString("assembly_type"),
                    new int[]{ rs.getInt("asm_count"), rs.getInt("child_count") });
            }
        }

        // Calculate coverage percentage
        double coveragePct = totalElements > 0
            ? 100.0 * assembledElements / totalElements : 100.0;

        // Build result
        if (totalOrphans == 0) {
            CheckResult.Builder result = CheckResult.pass(getId(), getName())
                .summary(String.format("All %d elements in BOM assemblies (100%% coverage)", totalElements));

            // Assembly breakdown
            for (var entry : assemblyStats.entrySet()) {
                result.detail(String.format("  %s: %d assemblies, %d components",
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
            }

            result.data("totalElements", totalElements);
            result.data("assembledElements", assembledElements);
            result.data("coveragePct", coveragePct);
            result.data("assemblyTypes", assemblyStats.size());

            return result.build();
        }

        // Orphans found
        boolean isFail = coveragePct < PASS_THRESHOLD;
        CheckResult.Builder result = isFail
            ? CheckResult.fail(getId(), getName())
            : CheckResult.warning(getId(), getName());

        result.summary(String.format("%d of %d elements orphaned (%.1f%% BOM coverage)",
            totalOrphans, totalElements, coveragePct));

        // Orphan breakdown by IFC class
        result.detail("Orphaned elements by type:");
        for (var entry : orphansByClass.entrySet()) {
            result.detail(String.format("  %s: %d orphaned", entry.getKey(), entry.getValue()));
        }

        // Orphan breakdown by storey (top 5)
        if (!orphansByStorey.isEmpty()) {
            result.detail("");
            result.detail("Orphaned elements by storey (top 5):");
            orphansByStorey.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> result.detail(String.format("  %s: %d orphaned",
                    entry.getKey(), entry.getValue())));
        }

        // Assembly summary
        result.detail("");
        result.detail("Assembly coverage:");
        for (var entry : assemblyStats.entrySet()) {
            result.detail(String.format("  %s: %d assemblies, %d components",
                entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }

        result.guidance("Add BOM recipes to ad_bom/ad_bom_child in component_library.db for orphaned IFC classes");
        result.data("totalElements", totalElements);
        result.data("orphanedElements", totalOrphans);
        result.data("coveragePct", coveragePct);
        result.data("assemblyTypes", assemblyStats.size());

        return result.build();
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }
}
