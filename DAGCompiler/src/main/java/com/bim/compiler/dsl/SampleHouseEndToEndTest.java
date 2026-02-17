package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.validation.SpatialDigest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Ifc4_SampleHouse Rosetta Stone E2E Test
 *
 * Compiles Ifc4_SampleHouse.bim and writes output DB.
 * Reference: DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db (ground truth from IFC)
 * Output:    DAGCompiler/lib/output/ifc4_sample_house.db (compiled — must converge to match reference)
 *
 * Run spatial_checker.py to X-ray compare output vs reference.
 */
public class SampleHouseEndToEndTest {

    private static final String DSL_PATH = "examples/Ifc4_SampleHouse.bim";
    private static final String DB_PATH = "DAGCompiler/lib/output/ifc4_sample_house.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("Ifc4_SampleHouse ROSETTA STONE TEST");
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

        if (def.name().equals("Ifc4_SampleHouse")) {
            System.out.println("[PASS] Building name");
            passed++;
        } else {
            System.out.println("[FAIL] Building name: " + def.name());
            failed++;
        }

        // STEP 2: Single-unit structure
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: SINGLE-UNIT STRUCTURE");
        System.out.println("-".repeat(70));

        if (def.storeys() != null && !def.storeys().isEmpty()) {
            System.out.printf("Storeys: %d%n", def.storeys().size());
            for (var storey : def.storeys()) {
                System.out.printf("  %s: %d rooms%n", storey.name(), storey.rooms().size());
            }
            System.out.println("[PASS] Storeys parsed");
            passed++;
        } else {
            System.out.println("[FAIL] No storeys found");
            failed++;
        }

        // STEP 3: Compile
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                storey.name(), storey.rooms().size(), storey.walls().size(),
                storey.doors().size(), storey.windows().size());
        }

        int totalRooms = spec.storeys().stream().mapToInt(s -> s.rooms().size()).sum();
        if (totalRooms >= 3) {
            System.out.printf("[PASS] Room count %d >= 3%n", totalRooms);
            passed++;
        } else {
            System.out.printf("[FAIL] Room count %d < 3%n", totalRooms);
            failed++;
        }

        // STEP 4: Write to DB
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 4: WRITE TO DB");
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

            if (elementCount > 0) {
                System.out.println("[PASS] Elements written");
                passed++;
            } else {
                System.out.println("[FAIL] No elements written");
                failed++;
            }

            // Class breakdown
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) FROM elements_meta GROUP BY ifc_class ORDER BY COUNT(*) DESC")) {
                System.out.println("\nElement classes:");
                while (rs.next()) {
                    System.out.printf("  %-30s %d%n", rs.getString(1), rs.getInt(2));
                }
            }
        }

        // STEP 5: SpatialDigest
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 5: SPATIAL DIGEST");
        System.out.println("-".repeat(70));
        SpatialDigest.DigestReport digestReport = SpatialDigest.computeWithReport(DB_PATH);
        System.out.println(digestReport);

        // STEP 6: Relational Shadow Validation (Phase RM-2)
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 6: RELATIONAL SHADOW VALIDATION");
        System.out.println("-".repeat(70));
        {
            int mismatches = RelationalResolver.getInstance().shadowValidate("Ifc4_SampleHouse");
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

        // Summary
        System.out.println("=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n%n", passed, failed);

        if (failed == 0) {
            System.out.println("[SUCCESS] Ifc4_SampleHouse Rosetta Stone test complete");
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
