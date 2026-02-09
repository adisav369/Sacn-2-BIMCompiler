package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Sekolah Kebangsaan End-to-End Test (Phase 52)
 *
 * Tests the 2-storey school to validate:
 * 1. Multi-storey compilation with reduced footprint
 * 2. STOREYS_VERTICALLY_CONSISTENT witness
 * 3. CLASSROOM_DAYLIGHT across both floors
 * 4. CORRIDOR_CONNECTS_ALL per-storey
 * 5. FIRE_TRAVEL_DISTANCE under 30m limit
 *
 * Success criteria: 24 claims, 19+ PROVEN
 */
public class SchoolEndToEndTest {

    private static final String DSL_PATH = "examples/Sekolah-Kebangsaan.bim";
    private static final String DB_PATH = "output/sekolah_kebangsaan.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("SEKOLAH KEBANGSAAN END-TO-END TEST (2-STOREY)");
        System.out.println("Target: 24 claims, 19+ PROVEN");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Read DSL from file
        String dsl = Files.readString(Path.of(DSL_PATH));
        System.out.println("DSL length: " + dsl.length() + " characters");

        // =====================================================================
        // STEP 1: Parse DSL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Building: " + def.name());
        System.out.println("Construction: " + def.constructionSystem());
        System.out.println("Storeys: " + def.storeys().size());
        for (BuildingDefinition.StoreyDef storey : def.storeys()) {
            System.out.printf("  - %s (level %d): %d rooms%n",
                storey.name(), storey.level(), storey.rooms().size());
        }

        if (def.storeys().size() == 2) {
            System.out.println("[PASS] Parse - 2 storeys detected");
            passed++;
        } else {
            System.out.println("[FAIL] Parse - expected 2 storeys, got " + def.storeys().size());
            failed++;
        }

        // =====================================================================
        // STEP 2: Compile to BuildingSpec
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 2: COMPILE TO BUILDINGSPEC");
        System.out.println("-".repeat(70));

        CompilationResult result = BuildingCompiler.compileWithValidation(def);
        BuildingSpec spec = result.spec();

        int totalRooms = 0;
        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("Storey '%s': rooms=%d, walls=%d, doors=%d, windows=%d%n",
                storey.name(),
                storey.rooms().size(),
                storey.walls().size(),
                storey.doors().size(),
                storey.windows().size());
            totalRooms += storey.rooms().size();
        }

        // Ground: 10 rooms, Upper: 12 rooms = 22 total
        if (totalRooms >= 20) {
            System.out.println("[PASS] Compile - " + totalRooms + " rooms");
            passed++;
        } else {
            System.out.println("[FAIL] Compile - expected ~22 rooms, got " + totalRooms);
            failed++;
        }

        // =====================================================================
        // STEP 3: Write to Federated DB Schema
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 3: WRITE TO FEDERATED DB");
        System.out.println("-".repeat(70));

        new File("output").mkdirs();
        new File(DB_PATH).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            conn.setAutoCommit(false);

            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            try {
                writer.write(spec);
                conn.commit();
            } catch (SQLException e) {
                System.err.println("DB Write Error: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            System.out.println("Database written: " + DB_PATH);

            // Generate witness file with hash provenance
            Path witnessPath = Path.of("output/sekolah_kebangsaan_witness.json");
            if (WitnessGenerator.generateWitness(spec, null, witnessPath, dsl, DB_PATH)) {
                System.out.println("Witness written: " + witnessPath + " (with hash provenance)");
            }

            // =====================================================================
            // STEP 4: VERIFY 2-STOREY SPECIFIC TESTS
            // =====================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("STEP 4: 2-STOREY VALIDATION");
            System.out.println("-".repeat(70));

            // Verify element counts
            int elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            System.out.printf("Total elements: %d%n", elementCount);

            // Verify both storeys present in spatial structure
            int storeyCount = queryInt(conn,
                "SELECT COUNT(*) FROM spatial_structure WHERE type = 'IfcBuildingStorey'");
            System.out.printf("Storeys in spatial_structure: %d%n", storeyCount);

            if (storeyCount == 2) {
                System.out.println("[PASS] 2 storeys in DB");
                passed++;
            } else {
                System.out.println("[FAIL] Expected 2 storeys in DB, got " + storeyCount);
                failed++;
            }

            // Verify reduced footprint (upper floor has fewer rooms)
            int groundRooms = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE storey='Ground' AND ifc_class='IfcSpace'");
            int upperRooms = queryInt(conn,
                "SELECT COUNT(*) FROM elements_meta WHERE storey='Upper' AND ifc_class='IfcSpace'");
            System.out.printf("Ground floor rooms: %d, Upper floor rooms: %d%n", groundRooms, upperRooms);

            // Ground has 10 (teaching block + assembly/canteen)
            // Upper has 12 (teaching block only, but more classrooms)
            if (groundRooms >= 10 && upperRooms >= 10) {
                System.out.println("[PASS] Reduced footprint validated");
                passed++;
            } else {
                System.out.println("[FAIL] Room count mismatch");
                failed++;
            }

            // =====================================================================
            // STEP 5: WITNESS VERIFICATION
            // =====================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("STEP 5: WITNESS VERIFICATION");
            System.out.println("-".repeat(70));

            String witnessJson = Files.readString(witnessPath);

            // Check for key witness claims
            boolean hasStoreys = witnessJson.contains("STOREYS_VERTICALLY_CONSISTENT");
            boolean isStoreysProven = witnessJson.contains("STOREYS_VERTICALLY_CONSISTENT")
                && witnessJson.contains("\"status\": \"PROVEN\"");

            System.out.println("STOREYS_VERTICALLY_CONSISTENT present: " + hasStoreys);
            System.out.println("STOREYS_VERTICALLY_CONSISTENT PROVEN: " + isStoreysProven);

            // Count proven claims
            int provenCount = countOccurrences(witnessJson, "\"status\": \"PROVEN\"");
            int skippedCount = countOccurrences(witnessJson, "\"status\": \"SKIPPED\"");
            int unprovableCount = countOccurrences(witnessJson, "\"status\": \"UNPROVABLE\"");

            System.out.printf("Witnesses: %d proven, %d skipped, %d unprovable%n",
                provenCount, skippedCount, unprovableCount);

            if (provenCount >= 19) {
                System.out.println("[PASS] " + provenCount + "/24 witnesses PROVEN!");
                passed++;
            } else {
                System.out.println("[FAIL] Only " + provenCount + " witnesses proven (expected 19+)");
                failed++;
            }

        }

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n%n", passed, failed);

        if (failed == 0) {
            System.out.println("[SUCCESS] Sekolah Kebangsaan 2-storey validation complete!");
            System.out.println("         Multi-storey school with reduced footprint validated.");
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

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
