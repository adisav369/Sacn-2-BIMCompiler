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
 * Exit codes:
 *   0 = All checks PASS
 *   1 = At least one FAIL
 *   2 = Input file not found or invalid
 *   3 = Checker internal error
 */
public class HouseSanityChecker {

    private static final List<SanityCheck> ALL_CHECKS = List.of(
        new FoundationCheck(),
        new EntryDoorCheck(),
        new WindowPlacementCheck(),
        new RoomConnectivityCheck(),
        new RoomProportionCheck(),
        new RoofCoverageCheck(),
        new EnvelopeContainmentCheck(),
        // Phase 42: New MEP and multi-storey checks
        new StoreyCountCheck(),
        new ElectricalElementsCheck(),
        new PlumbingElementsCheck(),
        new WitnessVerificationCheck(),
        // Phase 54: Pattern B compliance check (catches strewn objects)
        new PatternBCheck(),
        // Phase 57: Fire Protection MATHS check
        new FireProtectionCheck(),
        // Phase 65: Fire Compartment MATHS check
        new CompartmentCheck(),
        // Phase 66: Wall Continuity MATHS check
        new WallContinuityCheck(),
        // Phase 67: Escape Route MATHS check
        new EscapeRouteCheck(),
        // Phase 68: Dead-End Corridor MATHS check
        new DeadEndCorridorCheck()
    );

    /**
     * Run all sanity checks on a database file.
     */
    public static SanityReport check(String dbPath) throws SQLException {
        SanityReport report = new SanityReport(dbPath);

        try (SanityModel model = new SanityModel(dbPath)) {
            for (SanityCheck check : ALL_CHECKS) {
                CheckResult result = check.execute(model);
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

        List<SanityCheck> selectedChecks = new ArrayList<>();
        for (SanityCheck check : ALL_CHECKS) {
            if (checkIds.contains(check.getId())) {
                selectedChecks.add(check);
            }
        }

        try (SanityModel model = new SanityModel(dbPath)) {
            for (SanityCheck check : selectedChecks) {
                CheckResult result = check.execute(model);
                report.addResult(result);
            }
        }

        return report;
    }

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
            House Sanity Checker - Phase 0 Probe

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

            Checks performed:
              1. Foundation Ground Level - Building sits on ground (Z≈0)
              2. Entry Door - At least one door on building perimeter
              3. Window Placement - All windows on exterior walls only
              4. Room Connectivity - All rooms reachable via doors
              5. Room Proportions - No impossibly narrow rooms
              6. Roof Coverage - Roof covers entire building footprint
              7. Envelope Containment - All rooms inside building envelope
              8. Storey Count - Verify single/multi-storey (Phase 42)
              9. Electrical Elements - Lights, outlets, switches (Phase 42)
             10. Plumbing Elements - Pipes, fixtures (Phase 42)
             11. Witness Verification - Cross-check with witness file (Phase 42)
            """);
    }
}
