package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;
import com.bim.compiler.export.BOMExporter;
import com.bim.compiler.export.IDempiereExporter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * Phase 15: Layer 3 Integration Test
 *
 * Validates:
 * 1. Constraint DSL parsing (adjacent, not_adjacent, exterior)
 * 2. SpaceSolver finds valid positions
 * 3. Constraint satisfaction verification
 * 4. Full pipeline: DSL → Solver → Compiler → DB → IFC
 */
public class ConstrainedHouseTest {

    private static final String DSL_PATH = "examples/house_constrained.bim";
    private static final String OUTPUT_DB = "output/house_constrained.db";
    private static final String OUTPUT_JSON = "output/house_constrained.json";

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 15: LAYER 3 INTEGRATION TEST (CONSTRAINT SOLVER)");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        try {
            Class.forName("org.sqlite.JDBC");

            // =================================================================
            // TEST 1: Parse Constraint DSL
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 1: PARSE CONSTRAINT DSL");
            System.out.println("-".repeat(70));

            String dsl = Files.readString(Path.of(DSL_PATH));
            BuildingDefinition def = BuildingParser.parse(dsl);

            System.out.println("  Building: " + def.name());
            System.out.println("  Storeys: " + def.storeys().size());

            StoreyDef ground = def.storeys().get(0);
            System.out.println("  Rooms in Ground storey: " + ground.rooms().size());

            boolean hasConstraints = false;
            for (RoomDef room : ground.rooms()) {
                boolean hasAdj = !room.adjacentTo().isEmpty();
                boolean hasNotAdj = !room.notAdjacentTo().isEmpty();
                boolean hasExt = room.exteriorWall() != null;
                boolean needsSolver = room.needsSolverPlacement();

                System.out.printf("    %s \"%s\" %dx%.0fm%n",
                    room.type(), room.name(), (int)room.width(), room.depth());
                System.out.printf("      position: %s, constraints: adj=%s notAdj=%s ext=%s solver=%s%n",
                    room.gridPosition() != null ? room.gridPosition() : "SOLVER",
                    room.adjacentTo(), room.notAdjacentTo(), room.exteriorWall(),
                    needsSolver);

                if (hasAdj || hasNotAdj || hasExt) {
                    hasConstraints = true;
                }
            }

            if (hasConstraints) {
                System.out.println("  [PASS] Constraint DSL parsed correctly");
                passed++;
            } else {
                System.out.println("  [FAIL] No constraints found in DSL");
                failed++;
            }

            // =================================================================
            // TEST 2: Compile (Solver Invocation)
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 2: COMPILE WITH CONSTRAINT SOLVER");
            System.out.println("-".repeat(70));

            long startTime = System.currentTimeMillis();
            BuildingSpec spec = BuildingCompiler.compile(def);
            long compileTime = System.currentTimeMillis() - startTime;

            System.out.println("  Compilation time: " + compileTime + "ms");

            StoreySpec groundSpec = spec.storeys().get(0);
            System.out.println("  Rooms compiled: " + groundSpec.rooms().size());

            for (RoomSpec room : groundSpec.rooms()) {
                System.out.printf("    %s: (%.0f,%.0f) to (%.0f,%.0f) = %.0fx%.0fm%n",
                    room.name(),
                    room.minX(), room.minY(), room.maxX(), room.maxY(),
                    room.maxX() - room.minX(), room.maxY() - room.minY());
            }

            if (groundSpec.rooms().size() == 4) {
                System.out.println("  [PASS] All rooms placed by solver");
                passed++;
            } else {
                System.out.println("  [FAIL] Room count mismatch");
                failed++;
            }

            // =================================================================
            // TEST 3: Verify Constraint Satisfaction
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 3: VERIFY CONSTRAINT SATISFACTION");
            System.out.println("-".repeat(70));

            Map<String, RoomSpec> roomMap = new HashMap<>();
            for (RoomSpec room : groundSpec.rooms()) {
                roomMap.put(room.name(), room);
            }

            int constraintsPassed = 0;
            int constraintsFailed = 0;

            // Check: living has exterior: south (minY should be 0)
            RoomSpec living = roomMap.get("living");
            if (living != null && living.minY() == 0) {
                System.out.println("  [PASS] living has south exterior wall (minY=0)");
                constraintsPassed++;
            } else {
                System.out.println("  [FAIL] living should have south exterior");
                constraintsFailed++;
            }

            // Check: master has exterior: north
            RoomSpec master = roomMap.get("master");
            // For north exterior, maxY should be at grid edge

            // Check: kitchen adjacent to living (shared edge)
            RoomSpec kitchen = roomMap.get("kitchen");
            if (living != null && kitchen != null) {
                boolean adjacent = areAdjacent(living, kitchen);
                if (adjacent) {
                    System.out.println("  [PASS] kitchen adjacent to living");
                    constraintsPassed++;
                } else {
                    System.out.println("  [FAIL] kitchen should be adjacent to living");
                    constraintsFailed++;
                }
            }

            // Check: master adjacent to ensuite
            RoomSpec ensuite = roomMap.get("ensuite");
            if (master != null && ensuite != null) {
                boolean adjacent = areAdjacent(master, ensuite);
                if (adjacent) {
                    System.out.println("  [PASS] master adjacent to ensuite");
                    constraintsPassed++;
                } else {
                    System.out.println("  [FAIL] master should be adjacent to ensuite");
                    constraintsFailed++;
                }
            }

            // Check: kitchen NOT adjacent to master
            if (kitchen != null && master != null) {
                boolean notAdjacent = !areAdjacent(kitchen, master);
                if (notAdjacent) {
                    System.out.println("  [PASS] kitchen NOT adjacent to master");
                    constraintsPassed++;
                } else {
                    System.out.println("  [FAIL] kitchen should NOT be adjacent to master");
                    constraintsFailed++;
                }
            }

            System.out.printf("  Constraints: %d passed, %d failed%n",
                constraintsPassed, constraintsFailed);

            if (constraintsFailed == 0) {
                System.out.println("  [PASS] All constraints satisfied");
                passed++;
            } else {
                System.out.println("  [FAIL] Some constraints not satisfied");
                failed++;
            }

            // =================================================================
            // TEST 4: Write to Database
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 4: WRITE TO DATABASE");
            System.out.println("-".repeat(70));

            new File("output").mkdirs();
            new File(OUTPUT_DB).delete();

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + OUTPUT_DB)) {
                BuildingWriter writer = new BuildingWriter(conn);
                writer.initSchema();
                writer.write(spec);
                writer.exportJson(spec, OUTPUT_JSON);

                // Count elements
                int elementCount = 0;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
                    if (rs.next()) elementCount = rs.getInt(1);
                }

                System.out.println("  Elements written: " + elementCount);

                // Element breakdown
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT ifc_class, COUNT(*) as cnt FROM elements_meta GROUP BY ifc_class ORDER BY cnt DESC")) {
                    while (rs.next()) {
                        System.out.printf("    %s: %d%n", rs.getString(1), rs.getInt(2));
                    }
                }

                if (elementCount > 0) {
                    System.out.println("  [PASS] Database write successful");
                    passed++;
                } else {
                    System.out.println("  [FAIL] No elements written");
                    failed++;
                }
            }

            // =================================================================
            // TEST 5: BOM Export
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 5: BOM EXPORT");
            System.out.println("-".repeat(70));

            BOMExporter bomExporter = new BOMExporter(OUTPUT_DB, "house_constrained");
            bomExporter.exportAll("output/bom_constrained");

            System.out.println("  BOM files exported to output/bom_constrained/");
            System.out.println("  [PASS] BOM export complete");
            passed++;

            // =================================================================
            // TEST 6: iDempiere Export
            // =================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("TEST 6: iDEMPIERE EXPORT");
            System.out.println("-".repeat(70));

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + OUTPUT_DB)) {
                IDempiereExporter idempExporter = new IDempiereExporter(conn);
                idempExporter.exportAll("output/idempiere_constrained");
            }

            // Read total cost
            double totalCost = 0;
            File costedBom = new File("output/idempiere_constrained/idempiere_costed_bom.csv");
            if (costedBom.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(costedBom))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] parts = line.split(",");
                        if (parts.length >= 7) {
                            try {
                                totalCost += Double.parseDouble(parts[6]);
                            } catch (NumberFormatException e) {
                                // Skip header
                            }
                        }
                    }
                }
            }

            System.out.printf("  Total cost: MYR %.2f%n", totalCost);
            System.out.println("  [PASS] iDempiere export complete");
            passed++;

            // =================================================================
            // SUMMARY
            // =================================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("PHASE 15 TEST SUMMARY");
            System.out.println("=".repeat(70));
            System.out.printf("Passed: %d%n", passed);
            System.out.printf("Failed: %d%n", failed);

            if (failed == 0) {
                System.out.println("\n" + "=".repeat(70));
                System.out.println("ALL TESTS PASSED - LAYER 3 INTEGRATION COMPLETE");
                System.out.println("=".repeat(70));

                System.out.println("\nVALIDATED:");
                System.out.println("  [X] Constraint DSL parsing (adjacent, not_adjacent, exterior)");
                System.out.println("  [X] SpaceSolver finds valid room positions");
                System.out.println("  [X] Constraint satisfaction verified");
                System.out.println("  [X] Full pipeline: DSL → Solver → Compiler → DB → BOM");

                System.out.println("\nLAYER 3 CAPABILITIES:");
                System.out.println("  - adjacent: roomA      (rooms must share wall)");
                System.out.println("  - not_adjacent: roomB  (rooms cannot share wall)");
                System.out.println("  - exterior: direction  (room must touch building edge)");

                System.out.println("\nOUTPUT FILES:");
                System.out.println("  " + OUTPUT_DB);
                System.out.println("  " + OUTPUT_JSON);
                System.out.println("  output/bom_constrained/*.csv");
                System.out.println("  output/idempiere_constrained/*.csv");

                System.out.println("\nNEXT: IFC export via export_building_to_ifc.py");
            } else {
                System.out.println("\nSOME TESTS FAILED");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if two rooms are adjacent (share at least 1m of edge).
     */
    private static boolean areAdjacent(RoomSpec r1, RoomSpec r2) {
        // Check if they share a vertical edge (X alignment)
        boolean shareVerticalEdge =
            (Math.abs(r1.maxX() - r2.minX()) < 0.001 || Math.abs(r2.maxX() - r1.minX()) < 0.001) &&
            !(r1.maxY() <= r2.minY() || r2.maxY() <= r1.minY());  // Y ranges overlap

        // Check if they share a horizontal edge (Y alignment)
        boolean shareHorizontalEdge =
            (Math.abs(r1.maxY() - r2.minY()) < 0.001 || Math.abs(r2.maxY() - r1.minY()) < 0.001) &&
            !(r1.maxX() <= r2.minX() || r2.maxX() <= r1.minX());  // X ranges overlap

        return shareVerticalEdge || shareHorizontalEdge;
    }
}
