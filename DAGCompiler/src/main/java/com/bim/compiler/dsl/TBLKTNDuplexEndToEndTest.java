package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.GeometryIntegrityChecker;
import com.bim.compiler.validation.PlacementProver;
import com.bim.compiler.validation.SpatialDigest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Ifc2x3_Duplex Rosetta Stone E2E Test
 *
 * Compiles Ifc2x3_Duplex.bim and writes output DB.
 * Reference: DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db (ground truth from IFC)
 * Output:    DAGCompiler/lib/output/ifc2x3_duplex.db (compiled — must converge to match reference)
 *
 * Run spatial_checker.py to X-ray compare output vs reference.
 */
public class TBLKTNDuplexEndToEndTest {

    private static final String DSL_PATH = "examples/Ifc2x3_Duplex.bim";
    private static final String DB_PATH = "DAGCompiler/lib/output/ifc2x3_duplex.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("Ifc2x3_Duplex ROSETTA STONE TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        String dsl = Files.readString(Path.of(DSL_PATH));

        // STEP 1: Parse
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Building: " + def.name());

        if (def.name().equals("Ifc2x3_Duplex")) {
            System.out.println("[PASS] Building name");
            passed++;
        } else {
            System.out.println("[FAIL] Building name");
            failed++;
        }

        // STEP 2: Validate multi-unit structure
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: MULTI-UNIT STRUCTURE");
        System.out.println("-".repeat(70));

        boolean hasUnits = def.units() != null && !def.units().isEmpty();
        System.out.println("Multi-unit: " + hasUnits);

        if (hasUnits && def.units().size() == 2) {
            System.out.println("Units: " + def.units().size());
            for (var unit : def.units()) {
                System.out.printf("  - Unit %s: %d storeys, %d rooms total%n",
                    unit.name(), unit.storeys().size(),
                    unit.storeys().stream().mapToInt(s -> s.rooms().size()).sum());
            }
            System.out.println("[PASS] 2-unit duplex structure");
            passed++;
        } else {
            System.out.println("[FAIL] Expected 2 units");
            failed++;
        }

        // STEP 3: Validate per-unit structure (manifest-style: storeys expanded from metadata at compile time)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: PER-UNIT STRUCTURE");
        System.out.println("-".repeat(70));

        boolean unitsOk = true;
        for (var unit : def.units()) {
            if (unit.storeys().isEmpty()) {
                System.out.printf("  Unit %s: manifest-style (storeys from metadata)%n", unit.name());
            } else {
                for (var storey : unit.storeys()) {
                    System.out.printf("  Unit %s / %s: %d rooms%n",
                        unit.name(), storey.name(), storey.rooms().size());
                }
            }
        }

        if (def.units().size() == 2) {
            System.out.println("[PASS] 2 units present");
            passed++;
        } else {
            System.out.printf("[FAIL] Expected 2 units, got %d%n", def.units().size());
            failed++;
        }

        // STEP 4: Validate SHARED block
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 4: SHARED BLOCK");
        System.out.println("-".repeat(70));

        if (def.shared() != null && def.shared().storeys() != null) {
            int sharedStoreys = def.shared().storeys().size();
            int totalSharedRooms = def.shared().storeys().stream()
                .mapToInt(s -> s.rooms().size()).sum();
            System.out.printf("Shared storeys: %d, rooms: %d%n", sharedStoreys, totalSharedRooms);
            System.out.println("[PASS] SHARED block parsed");
            passed++;
        } else {
            System.out.println("[FAIL] No SHARED block found");
            failed++;
        }

        // STEP 5: Full compilation
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 5: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();
        int storeyCount = spec.storeys().size();

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                storey.name(),
                storey.rooms().size(),
                storey.walls().size(),
                storey.doors().size(),
                storey.windows().size());
        }

        if (storeyCount >= 2) {
            System.out.printf("[PASS] Compilation produced %d storeys%n", storeyCount);
            passed++;
        } else {
            System.out.printf("[FAIL] Compilation produced %d storeys (expected >= 2)%n", storeyCount);
            failed++;
        }

        // STEP 6: Room count
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 6: ROOM COUNT");
        System.out.println("-".repeat(70));

        int totalRooms = spec.storeys().stream().mapToInt(s -> s.rooms().size()).sum();
        System.out.printf("Total compiled rooms: %d%n", totalRooms);

        if (totalRooms >= 8) {
            System.out.printf("[PASS] Room count %d >= 8%n", totalRooms);
            passed++;
        } else {
            System.out.printf("[FAIL] Room count %d < 8%n", totalRooms);
            failed++;
        }

        // STEP 7: Write to DB and check party walls
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 7: WRITE TO DB + PARTY WALL CHECK");
        System.out.println("-".repeat(70));

        new File("output").mkdirs();
        new File(DB_PATH).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);

            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
            System.out.println("Database written: " + DB_PATH);

            int elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            System.out.printf("Total elements: %d%n", elementCount);

            // Check for walls (IfcPlate or IfcWall) as evidence of compilation
            int totalWalls = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE ifc_class IN ('IfcPlate', 'IfcWall')");
            System.out.printf("Total walls (IfcPlate+IfcWall): %d%n", totalWalls);

            // Check for party walls via element_name or element_type
            int partyWalls = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE ifc_class IN ('IfcPlate', 'IfcWall') " +
                "AND (element_name LIKE '%PARTY%' OR element_type LIKE '%PARTY%')");
            System.out.printf("Party walls: %d%n", partyWalls);

            if (totalWalls > 0) {
                System.out.printf("[PASS] Walls present: %d total, %d party%n", totalWalls, partyWalls);
                passed++;
            } else {
                System.out.println("[FAIL] No walls found");
                failed++;
            }
        }

        // STEP 8: SpatialDigest — deterministic regression fingerprint
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 8: SPATIAL DIGEST");
        System.out.println("-".repeat(70));
        SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(DB_PATH);
        System.out.println(digestReport);

        // STEP 9: Relational Shadow Validation (Phase RM-2)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 9: RELATIONAL SHADOW VALIDATION");
        System.out.println("-".repeat(70));
        {
            int mismatches = RelationalResolver.getInstance().shadowValidate("Ifc2x3_Duplex");
            if (mismatches == 0) {
                System.out.println("[PASS] Relational resolver matches oracle (0 mismatches)");
                passed++;
            } else if (mismatches < 0) {
                System.out.println("[SKIP] No relational rules — shadow validation skipped");
            } else {
                System.out.printf("[FAIL] Relational resolver: %d mismatches%n", mismatches);
                failed++;
            }
        }

        // STEP 10: Geometry Integrity Check (Phase RM-4)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 10: GEOMETRY INTEGRITY CHECK");
        System.out.println("-".repeat(70));
        {
            String refPath = "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";
            GeometryIntegrityChecker.CheckReport geoReport =
                GeometryIntegrityChecker.check(DB_PATH, refPath);
            GeometryIntegrityChecker.printReport(geoReport);

            if (geoReport.failCount() == 0) {
                System.out.printf("[PASS] Geometry integrity: %d OK, %d WARN%n",
                    geoReport.fullyOk(), geoReport.warnCount());
                passed++;
            } else {
                System.out.printf("[FAIL] Geometry integrity: %d failures%n", geoReport.failCount());
                failed++;
            }
        }

        // STEP 11: Placement Mathematical Proof
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 11: PLACEMENT MATHEMATICAL PROOF");
        System.out.println("-".repeat(70));
        {
            PlacementProver.ProofReport proofReport = PlacementProver.proveFromDB(DB_PATH);
            PlacementProver.printReport(proofReport);
            if (proofReport.criticalViolations() == 0) {
                System.out.printf("[PASS] All critical proofs satisfied (%d proven, %d advisory violations)%n",
                    proofReport.proven(), proofReport.violated());
                passed++;
            } else {
                System.out.printf("[FAIL] %d critical proof violations (GATES test)%n",
                    proofReport.criticalViolations());
                failed++;
            }
            if (proofReport.violated() > 0 && proofReport.criticalViolations() == 0) {
                System.out.printf("[INFO] %d advisory violations (non-blocking)%n", proofReport.violated());
            }
        }

        // Summary
        System.out.println("=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n%n", passed, failed);

        if (failed == 0) {
            System.out.println("[SUCCESS] Ifc2x3_Duplex Rosetta Stone test complete");
        } else {
            System.out.println("[FAILURE] " + failed + " test(s) failed");
            System.exit(1);
        }
        System.out.println("=".repeat(70));
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
