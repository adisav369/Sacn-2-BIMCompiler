package com.bim.compiler.test;

import com.bim.compiler.db.FederatedDBReader;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.validation.*;

import java.sql.SQLException;
import java.util.*;

/**
 * Runs all validators against the Terminal model to calibrate thresholds.
 *
 * Expected results:
 * - Zero errors on data-derived rules (wall thickness, pipe diameter)
 * - Some warnings on clearance rules (real buildings have tight spots)
 */
public class ValidatorCalibrationTest {

    private static final String DB_PATH =
        "/home/red1/IfcOpenShell/WORK_DIR/databases/enhanced_federation_GI.db";

    public static void main(String[] args) {
        try {
            new ValidatorCalibrationTest().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws SQLException {
        System.out.println("=".repeat(70));
        System.out.println("VALIDATOR CALIBRATION TEST");
        System.out.println("=".repeat(70));
        System.out.println();

        // Load database
        try (FederatedDBReader db = new FederatedDBReader(DB_PATH)) {
            db.loadAllElements();
            System.out.println();

            List<ISpatialElement> allElements = db.getAllElements();

            // Print element counts by discipline
            System.out.println("Elements by Discipline:");
            Map<Discipline, Integer> discCounts = db.getCountsByDiscipline();
            discCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> System.out.printf("  %-6s: %,d%n", e.getKey(), e.getValue()));
            System.out.println();

            // Create validators
            List<IValidator> validators = Arrays.asList(
                new WallThicknessValidator(),
                new PipeDiameterValidator(),
                new OpeningPlacementValidator(),
                new MultiStoryElementValidator(),
                new MepStructureClearanceValidator(),
                new SprinklerSpacingValidator()
            );

            // Run each validator
            Map<String, ValidationResult> results = new LinkedHashMap<>();

            for (IValidator validator : validators) {
                System.out.printf("Running: %s...%n", validator.getName());
                long start = System.currentTimeMillis();

                ValidationResult result = validator.validateAll(allElements);
                results.put(validator.getName(), result);

                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("  Completed in %dms%n", elapsed);
            }

            // Print summary
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("VALIDATION RESULTS SUMMARY");
            System.out.println("=".repeat(70));
            System.out.println();

            System.out.printf("%-30s %10s %10s %10s%n",
                "Validator", "Errors", "Warnings", "Info");
            System.out.println("-".repeat(62));

            int totalErrors = 0;
            int totalWarnings = 0;
            int totalInfo = 0;

            for (Map.Entry<String, ValidationResult> entry : results.entrySet()) {
                ValidationResult r = entry.getValue();
                System.out.printf("%-30s %10d %10d %10d%n",
                    entry.getKey(),
                    r.getErrorCount(),
                    r.getWarningCount(),
                    r.getInfoCount());

                totalErrors += r.getErrorCount();
                totalWarnings += r.getWarningCount();
                totalInfo += r.getInfoCount();
            }

            System.out.println("-".repeat(62));
            System.out.printf("%-30s %10d %10d %10d%n",
                "TOTAL", totalErrors, totalWarnings, totalInfo);
            System.out.println();

            // Print sample messages from each validator
            System.out.println("=".repeat(70));
            System.out.println("SAMPLE MESSAGES (up to 5 per validator)");
            System.out.println("=".repeat(70));

            for (Map.Entry<String, ValidationResult> entry : results.entrySet()) {
                ValidationResult r = entry.getValue();
                if (r.getErrorCount() > 0 || r.getWarningCount() > 0) {
                    System.out.println();
                    System.out.println(entry.getKey() + ":");

                    // Show errors first
                    r.getErrors().stream().limit(3).forEach(m ->
                        System.out.println("  [ERROR] " + m.text()));

                    // Then warnings
                    r.getWarnings().stream().limit(5).forEach(m ->
                        System.out.println("  [WARN]  " + m.text()));
                }
            }

            // Print calibration assessment
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("CALIBRATION ASSESSMENT");
            System.out.println("=".repeat(70));
            System.out.println();

            assessCalibration(results);
        }
    }

    private void assessCalibration(Map<String, ValidationResult> results) {
        // Data-derived validators should have zero errors
        checkZeroErrors(results, "Wall Thickness", "Data-derived (FACT 3: 4 values)");
        checkZeroErrors(results, "Pipe Diameter", "Data-derived (FACT 4: discipline ranges)");
        checkZeroErrors(results, "Opening Placement", "Data-derived (98% overlap confirmed)");

        // Clearance validators may have some warnings (real buildings have tight spots)
        ValidationResult mep = results.get("MEP-Structure Clearance");
        if (mep != null) {
            int warnings = mep.getWarningCount();
            if (warnings == 0) {
                System.out.println("[REVIEW] MEP-Structure: Zero warnings - threshold may be too loose");
            } else if (warnings > 1000) {
                System.out.println("[REVIEW] MEP-Structure: " + warnings + " warnings - threshold may be too strict");
            } else {
                System.out.println("[OK] MEP-Structure: " + warnings + " warnings - expected for real building");
            }
        }

        ValidationResult sprinkler = results.get("Sprinkler Spacing");
        if (sprinkler != null) {
            int warnings = sprinkler.getWarningCount();
            if (warnings == 0) {
                System.out.println("[OK] Sprinkler Spacing: Zero warnings - designed to code");
            } else {
                System.out.println("[REVIEW] Sprinkler Spacing: " + warnings + " warnings - may need review");
            }
        }

        ValidationResult multiStory = results.get("Multi-Story Element");
        if (multiStory != null) {
            int info = multiStory.getInfoCount();
            System.out.println("[INFO] Multi-Story: " + info + " elements span >8m (expected: rebar, columns)");
        }
    }

    private void checkZeroErrors(Map<String, ValidationResult> results,
                                  String validatorName, String reason) {
        ValidationResult r = results.get(validatorName);
        if (r == null) {
            System.out.println("[SKIP] " + validatorName + ": Not found");
            return;
        }

        if (r.getErrorCount() == 0 && r.getWarningCount() == 0) {
            System.out.println("[OK] " + validatorName + ": Zero issues - " + reason);
        } else if (r.getErrorCount() == 0) {
            System.out.println("[OK] " + validatorName + ": Zero errors, " +
                r.getWarningCount() + " warnings - " + reason);
        } else {
            System.out.println("[FAIL] " + validatorName + ": " +
                r.getErrorCount() + " errors - UNEXPECTED for data-derived rule");
        }
    }
}
