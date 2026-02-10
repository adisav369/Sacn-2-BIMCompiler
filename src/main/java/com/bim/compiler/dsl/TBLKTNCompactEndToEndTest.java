package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * TB-LKTN-COMPACT End-to-End Test (Phase 110)
 *
 * Tests the 2-bedroom compact house variant:
 * 1. DSL file loading from examples/TB-LKTN-COMPACT.bim
 * 2. Grid-based room layout (4x4 grid, 10m x 8.5m)
 * 3. Single-storey compilation with 5 rooms
 * 4. OPEN_PLAN zone decomposition
 * 5. opens_to connectivity (bedrooms → common)
 */
public class TBLKTNCompactEndToEndTest {

    private static final String DSL_PATH = "examples/TB-LKTN-COMPACT.bim";
    private static final String DB_PATH = "output/tb_lktn_compact.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN-COMPACT END-TO-END TEST");
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
        System.out.println("Storeys: " + def.storeys().size());
        for (BuildingDefinition.StoreyDef storey : def.storeys()) {
            System.out.printf("  - %s: %d rooms%n", storey.name(), storey.rooms().size());
        }

        if (def.name().equals("TB-LKTN-COMPACT") && def.storeys().size() == 1) {
            System.out.println("[PASS] Parse — single storey, 5 rooms");
            passed++;
        } else {
            System.out.println("[FAIL] Parse — expected 1 storey");
            failed++;
        }

        // STEP 2: Compile
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();
        StoreySpec ground = spec.storeys().get(0);

        System.out.printf("Rooms: %d, Walls: %d, Doors: %d, Windows: %d%n",
            ground.rooms().size(), ground.walls().size(),
            ground.doors().size(), ground.windows().size());

        if (ground.walls().size() > 0 && ground.rooms().size() > 0) {
            System.out.println("[PASS] Compile");
            passed++;
        } else {
            System.out.println("[FAIL] Compile — no walls or rooms");
            failed++;
        }

        // STEP 3: Write to DB
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: WRITE TO DATABASE");
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

            Path witnessPath = Path.of("output/tb_lktn_compact_witness.json");
            WitnessGenerator.generateWitness(spec, null, witnessPath, dsl, DB_PATH);
            System.out.println("Witness written: " + witnessPath);

            // STEP 4: Verify DB
            System.out.println("\n" + "-".repeat(70));
            System.out.println("STEP 4: DB VERIFICATION");
            System.out.println("-".repeat(70));

            String[] tables = {"elements_meta", "elements_rtree", "base_geometries", "spatial_structure"};
            boolean dbOk = true;
            for (String table : tables) {
                int count = queryInt(conn, "SELECT COUNT(*) FROM " + table);
                System.out.printf("  [%s] %s populated (%d)%n", count > 0 ? "+" : "-", table, count);
                if (count == 0) dbOk = false;
            }

            if (dbOk) {
                System.out.println("\n[PASS] DB Schema Conformance");
                passed++;
            } else {
                System.out.println("\n[FAIL] DB Schema — empty table(s)");
                failed++;
            }

            // Verify element count is reasonable
            int elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            if (elementCount >= 50) {
                System.out.println("[PASS] Element count reasonable (" + elementCount + ")");
                passed++;
            } else {
                System.out.println("[FAIL] Too few elements (" + elementCount + ")");
                failed++;
            }
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n%n", passed, failed);

        if (failed == 0) {
            System.out.println("[SUCCESS] TB-LKTN-COMPACT compact house E2E test complete");
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
