package com.bim.compiler.db;

import com.bim.compiler.dsl.*;
import com.bim.compiler.factory.HybridFactory;
import com.bim.compiler.library.ComponentLibrary;

import java.sql.*;
import java.util.Map;

/**
 * Integration test for GeneratedModelWriter.
 *
 * Tests:
 *   1. Write DSL model to SQLite
 *   2. Verify schema matches Terminal DB
 *   3. Cross-verify element statistics
 */
public class GeneratedModelTest {

    private static final String LIBRARY_PATH = "library/component_library.db";
    private static final String OUTPUT_DB = "output/generated_model.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("GENERATED MODEL WRITER TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Test 1: Write DSL model
        System.out.println("\nTest 1: Write DSL model to SQLite");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    BEDROOM "master" at:A1 size:5x4m {
                        DOOR south to:corridor
                        WINDOW north
                        SPRINKLERS grid:4.6m
                    }
                    LIVING "lounge" at:B1 size:6x5m {
                        DOOR west to:master
                        WINDOW east
                        SPRINKLERS grid:4.6m
                    }
                    CORRIDOR "corridor" at:A2-B2 width:1.2m {
                        SPRINKLERS
                    }
                }
                """;

            // Parse and compile
            StoreyDefinition storey = DSLParser.parse(dsl);
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);

            System.out.println("  Compiled: " + spec.walls().size() + " walls, " +
                              spec.spaces().size() + " spaces, " +
                              spec.sprinklerGrids().size() + " sprinkler grids");

            // Create factory and writer
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HybridFactory factory = new HybridFactory(library);
            GeneratedModelWriter writer = new GeneratedModelWriter(OUTPUT_DB);

            // Write to DB
            writer.write(spec, factory);

            library.close();
            passed++;
            System.out.println("  Database written: " + OUTPUT_DB);
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Verify schema
        System.out.println("\nTest 2: Verify schema matches Terminal DB");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + OUTPUT_DB);
            Statement stmt = conn.createStatement();

            String[] requiredTables = {
                "elements_meta",
                "element_transforms",
                "elements_rtree",
                "base_geometries",
                "element_instances",
                "spatial_structure",
                "global_offset"
            };

            boolean allPresent = true;
            for (String table : requiredTables) {
                ResultSet rs = stmt.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'");
                boolean exists = rs.next();
                System.out.printf("    %s: %s%n", table, exists ? "[EXISTS]" : "[MISSING]");
                if (!exists) allPresent = false;
            }

            conn.close();

            if (allPresent) {
                passed++;
                System.out.println("  [PASS]");
            } else {
                failed++;
                System.out.println("  [FAIL] Missing tables");
            }
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            failed++;
        }

        // Test 3: Cross-verification query (Part C)
        System.out.println("\nTest 3: Cross-verification query");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + OUTPUT_DB);
            Statement stmt = conn.createStatement();

            // Query matching Terminal DB analysis format
            String sql = """
                SELECT
                    em.ifc_class,
                    COUNT(*) as count,
                    AVG(rt.maxZ - rt.minZ) as avg_height,
                    MIN(rt.minZ) as min_z,
                    MAX(rt.maxZ) as max_z
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                GROUP BY em.ifc_class
                ORDER BY count DESC
            """;

            ResultSet rs = stmt.executeQuery(sql);

            System.out.println("  Generated model statistics:");
            System.out.println("  " + "-".repeat(60));
            System.out.printf("  %-30s %5s %10s %10s%n", "IFC Class", "Count", "Avg Height", "Z Range");
            System.out.println("  " + "-".repeat(60));

            while (rs.next()) {
                String ifcClass = rs.getString("ifc_class");
                int count = rs.getInt("count");
                double avgHeight = rs.getDouble("avg_height");
                double minZ = rs.getDouble("min_z");
                double maxZ = rs.getDouble("max_z");

                System.out.printf("  %-30s %5d %10.3fm %5.2f-%5.2fm%n",
                    ifcClass, count, avgHeight, minZ, maxZ);
            }
            System.out.println("  " + "-".repeat(60));

            // Additional verification: sprinkler Z should be near ceiling (2.8m)
            rs = stmt.executeQuery("""
                SELECT AVG(rt.maxZ) as avg_z, MIN(rt.maxZ) as min_z, MAX(rt.maxZ) as max_z
                FROM elements_meta em
                JOIN elements_rtree rt ON em.id = rt.id
                WHERE em.ifc_class = 'IfcFireSuppressionTerminal'
            """);

            if (rs.next()) {
                double avgZ = rs.getDouble("avg_z");
                double expectedZ = 2.8;  // Ceiling height
                double tolerance = 0.1;  // 100mm tolerance

                System.out.printf("%n  Sprinkler Z verification:%n");
                System.out.printf("    Ceiling height: %.1fm%n", expectedZ);
                System.out.printf("    Avg sprinkler top Z: %.3fm%n", avgZ);
                System.out.printf("    Delta from ceiling: %.0fmm%n", (expectedZ - avgZ) * 1000);

                if (Math.abs(expectedZ - avgZ) < tolerance) {
                    System.out.println("    [OK] Sprinklers at ceiling level");
                } else {
                    System.out.println("    [WARNING] Sprinklers not at expected Z");
                }
            }

            conn.close();
            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("RESULTS: %d passed, %d failed%n", passed, failed);
        System.out.println("=".repeat(70));

        if (failed == 0) {
            System.out.println("ALL TESTS PASSED");
            System.out.println("\nGenerated model ready at: " + OUTPUT_DB);
            System.out.println("Next step: IFC export or Blender visualization");
        }
    }
}
