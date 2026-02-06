package com.bim.tools.sanity;

import com.bim.tools.sanity.checks.*;
import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;
import com.bim.tools.sanity.report.SanityReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * House Sanity Checker - Phase 0 Probe
 *
 * Independent verification that generated building output is recognizably
 * a valid house before fine-grained geometric analysis.
 *
 * This tool reads .db files and reports findings WITHOUT modifying anything.
 * It does NOT import any compiler code.
 *
 * Phase 78: Metadata-driven check selection via SanityCheckAD.
 *
 * Exit codes:
 *   0 = All checks PASS
 *   1 = At least one FAIL
 *   2 = Input file not found or invalid
 *   3 = Checker internal error
 */
public class HouseSanityChecker {

    // Library DB path (for AD queries)
    private static final String LIBRARY_DB_PATH = "library/component_library.db";
    private static final String LIBRARY_DB_PATH_ALT = "../../library/component_library.db";

    // Default jurisdiction
    private static final String DEFAULT_JURISDICTION = "MALAYSIA";

    /**
     * Run all applicable sanity checks on a database file.
     * Uses AD to determine which checks apply based on building parameters.
     */
    public static SanityReport check(String dbPath) throws SQLException {
        SanityReport report = new SanityReport(dbPath);

        try (SanityModel model = new SanityModel(dbPath)) {
            // Get AD context for this building
            ADContext context = createADContext(model);

            // Get applicable checks from AD (or fall back to all checks)
            List<SanityCheck> checks = getApplicableChecks(context, model);

            // Execute checks
            for (SanityCheck check : checks) {
                CheckResult result = check.execute(model, context);
                report.addResult(result);
            }
        }

        return report;
    }

    /**
     * Run sanity checks with specific check selection.
     */
    public static SanityReport check(String dbPath, List<String> checkIds) throws SQLException {
        SanityReport report = new SanityReport(dbPath);

        List<SanityCheck> selectedChecks = CheckRegistry.getByIds(checkIds);

        try (SanityModel model = new SanityModel(dbPath)) {
            ADContext context = createADContext(model);

            for (SanityCheck check : selectedChecks) {
                CheckResult result = check.execute(model, context);
                report.addResult(result);
            }
        }

        return report;
    }

    /**
     * Create AD context from building model.
     */
    private static ADContext createADContext(SanityModel model) {
        // Find library DB
        String libraryPath = findLibraryDb();
        SanityCheckAD ad = libraryPath != null ? new SanityCheckAD(libraryPath) : null;

        // Extract building parameters from model
        int storeys = model.getStoreyCount();
        double areaM2 = model.getTotalFloorArea();
        double heightM = model.getBuildingHeight();
        boolean sprinklered = model.hasSprinklers();
        String occupancy = inferOccupancy(model);

        return new ADContext(ad, storeys, occupancy, areaM2, heightM,
                             sprinklered, DEFAULT_JURISDICTION);
    }

    /**
     * Get applicable checks based on AD rules or fall back to all checks.
     */
    private static List<SanityCheck> getApplicableChecks(ADContext context, SanityModel model) {
        // If AD is not available, use all registered checks
        if (!context.hasAD()) {
            return new ArrayList<>(CheckRegistry.getAll());
        }

        // Query AD for applicable checks
        SanityCheckAD ad = context.getAD();
        List<SanityCheckAD.CheckSpec> specs = ad.getApplicableChecks(
            context.getStoreys(),
            context.getOccupancy(),
            context.getAreaM2(),
            context.getJurisdiction()
        );

        // Map to check instances
        List<SanityCheck> checks = new ArrayList<>();
        for (SanityCheckAD.CheckSpec spec : specs) {
            SanityCheck check = CheckRegistry.get(spec.checkId());
            if (check != null) {
                checks.add(check);
            }
        }

        return checks;
    }

    /**
     * Infer occupancy group from building contents.
     */
    private static String inferOccupancy(SanityModel model) {
        // Check for educational indicators
        if (hasSpaceType(model, "CLASSROOM", "DEWAN", "KANTIN", "BILIK_GURU")) {
            return "E";  // Educational
        }

        // Check for assembly indicators
        if (hasSpaceType(model, "ASSEMBLY", "AUDITORIUM", "THEATER")) {
            return "A";  // Assembly
        }

        // Check for business indicators
        if (hasSpaceType(model, "OFFICE", "CONFERENCE", "PEJABAT")) {
            return "B";  // Business
        }

        // Default to residential
        return "R";
    }

    private static boolean hasSpaceType(SanityModel model, String... types) {
        for (var space : model.getSpaces()) {
            String name = space.name();
            if (name != null) {
                String upper = name.toUpperCase();
                for (String type : types) {
                    if (upper.contains(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Find library database path.
     */
    private static String findLibraryDb() {
        if (new java.io.File(LIBRARY_DB_PATH).exists()) {
            return LIBRARY_DB_PATH;
        }
        if (new java.io.File(LIBRARY_DB_PATH_ALT).exists()) {
            return LIBRARY_DB_PATH_ALT;
        }
        return null;
    }

    // =========================================================================
    // Main Entry Point
    // =========================================================================

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(2);
        }

        String dbPath = null;
        String jsonOutput = null;
        boolean verbose = false;

        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            if ("--json".equals(args[i]) && i + 1 < args.length) {
                jsonOutput = args[++i];
            } else if ("--verbose".equals(args[i]) || "-v".equals(args[i])) {
                verbose = true;
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                printUsage();
                System.exit(0);
            } else if (!args[i].startsWith("-")) {
                dbPath = args[i];
            }
        }

        if (dbPath == null) {
            System.err.println("Error: No database file specified");
            printUsage();
            System.exit(2);
        }

        // Check file exists
        Path path = Path.of(dbPath);
        if (!Files.exists(path)) {
            System.err.println("Error: File not found: " + dbPath);
            System.exit(2);
        }

        if (!Files.isRegularFile(path)) {
            System.err.println("Error: Not a file: " + dbPath);
            System.exit(2);
        }

        try {
            // Run checks
            SanityReport report = check(dbPath);

            // Output console report
            System.out.println(report.toConsoleString(verbose));

            // Output JSON if requested
            if (jsonOutput != null) {
                String json = report.toJsonString();
                Files.writeString(Path.of(jsonOutput), json);
                System.out.println("JSON report written to: " + jsonOutput);
            }

            // Exit with appropriate code
            System.exit(report.getVerdict() == SanityReport.Verdict.PASS ? 0 : 1);

        } catch (SQLException e) {
            System.err.println("Error: Failed to read database: " + e.getMessage());
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Error: Failed to write JSON output: " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Internal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static void printUsage() {
        System.out.println("""
            House Sanity Checker - Phase 0 Probe (Phase 78: AD Integration)

            Usage: java -jar sanity-checker.jar <database.db> [options]

            Options:
              --json <file>    Write machine-readable JSON report to file
              --verbose, -v    Show all details even for passing checks
              --help, -h       Show this help message

            Exit codes:
              0 = All checks PASS
              1 = At least one FAIL
              2 = Input file not found or invalid
              3 = Checker internal error

            Check Selection:
              Checks are automatically selected based on building type via AD.
              - Single-storey: Stairwell check skipped
              - Non-corridor buildings: Dead-end check skipped
              - Thresholds vary by occupancy and jurisdiction

            Categories:
              ENVELOPE    - Foundation, entry, windows, roof, containment
              GEOMETRY    - Connectivity, proportions, ceiling, floor area
              MEP         - Electrical, plumbing, fire protection
              EGRESS      - Escape routes, stairs, doors
              STRUCTURAL  - Walls, grid alignment
              COMPLIANCE  - Witness verification, Pattern B
            """);
    }
}
