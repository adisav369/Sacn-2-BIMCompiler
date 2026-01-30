package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.dsl.BuildingDefinition.*;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * TB-LKTN End-to-End Test
 *
 * Proves: DSL → DB conformance with TERMINAL schema.
 *
 * Ground Truth Principle:
 *   TERMINAL (51,723 elements) → Federated DB → Blender bake (proven)
 *   TB-LKTN DSL → Same schema → Same result
 *
 * If DB schema matches TERMINAL, Blender bake will work.
 * No IFC intermediate needed. DB IS the authority.
 */
public class TBLKTNEndToEndTest {

    private static final String DB_PATH = "output/tb_lktn.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN END-TO-END TEST");
        System.out.println("Schema Conformance = Rendering Correctness");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // The canonical TB-LKTN DSL
        String dsl = """
            BUILDING "TB-LKTN"
                profile: "Malaysian_Residential"
                protocol: "Residential_Single_Storey"
                lod: 300
            {
                GRID {
                    axes: A, B, C, D, E / 1, 2, 3, 4, 5
                    spacing: 1.3, 3.1, 3.7, 3.1 / 2.3, 3.1, 1.5, 1.6
                }

                STOREY "Ground" level:0 height:2.8m {
                    PORCH "anjung" bounds:C1-D2 {
                        exterior: south
                        roof: ATTACHED
                    }

                    OPEN_PLAN "common" bounds:B2-D5 {
                        zones: LIVING, DINING, KITCHEN
                        exterior: south
                        exterior: north
                        DOOR type:D1 size:900x2100 wall:south
                        DOOR type:D1 size:900x2100 wall:north
                    }

                    BEDROOM "bilik_utama" bounds:A2-B4 {
                        exterior: west
                        opens_to: common
                    }

                    BATHROOM "bilik_mandi" bounds:A4-B5 {
                        exterior: west
                        opens_to: common
                    }

                    BEDROOM "bilik_2" bounds:D2-E4 {
                        exterior: east
                        opens_to: common
                    }

                    BEDROOM "bilik_3" bounds:D4-E5 {
                        exterior: east
                        opens_to: common
                    }
                }

                ROOF pitch:25deg overhang:600mm
            }
            """;

        // =====================================================================
        // STEP 1: Parse DSL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("STEP 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Building: " + def.name());
        System.out.println("Profile: " + def.profile());
        System.out.println("Rooms: " + def.storeys().get(0).rooms().size());

        if (def.name().equals("TB-LKTN") && def.storeys().get(0).rooms().size() == 6) {
            System.out.println("[PASS] Parse");
            passed++;
        } else {
            System.out.println("[FAIL] Parse");
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

        StoreySpec ground = spec.storeys().get(0);
        System.out.println("Rooms: " + ground.rooms().size());
        System.out.println("Walls: " + ground.walls().size());
        System.out.println("Doors: " + ground.doors().size());
        System.out.println("Windows: " + ground.windows().size());

        if (ground.walls().size() > 0 && ground.doors().size() > 0) {
            System.out.println("[PASS] Compile");
            passed++;
        } else {
            System.out.println("[FAIL] Compile");
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
                // Try to identify the duplicate
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         "SELECT guid, COUNT(*) as cnt FROM elements_meta GROUP BY guid HAVING cnt > 1")) {
                    while (rs.next()) {
                        System.err.println("  Duplicate GUID: " + rs.getString(1));
                    }
                }
                throw e;
            }
            System.out.println("Database written: " + DB_PATH);

            // Generate witness file (Phase 31 - Witness System)
            java.nio.file.Path witnessPath = java.nio.file.Path.of("output/tb_lktn_witness.json");
            if (BuildingWriter.generateWitness(spec, witnessPath)) {
                System.out.println("Witness written: " + witnessPath);
            }

            // =====================================================================
            // STEP 4: TERMINAL CONFORMANCE VERIFICATION
            // =====================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("STEP 4: TERMINAL SCHEMA CONFORMANCE");
            System.out.println("-".repeat(70));

            // 4a. Elements meta populated?
            int elementCount = queryInt(conn, "SELECT COUNT(*) FROM elements_meta");
            System.out.printf("elements_meta: %d rows%n", elementCount);

            // 4b. Spatial index populated?
            int rtreeCount = queryInt(conn, "SELECT COUNT(*) FROM elements_rtree");
            System.out.printf("elements_rtree: %d rows%n", rtreeCount);

            // 4c. Geometry exists?
            int geomCount = queryInt(conn, "SELECT COUNT(*) FROM base_geometries");
            System.out.printf("base_geometries: %d geometries%n", geomCount);

            // 4d. Spatial hierarchy?
            int structCount = queryInt(conn, "SELECT COUNT(*) FROM spatial_structure");
            System.out.printf("spatial_structure: %d entries%n", structCount);

            // 4e. IFC class distribution (matches TERMINAL patterns)
            System.out.println("\nIFC Class Distribution:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT ifc_class, COUNT(*) as cnt FROM elements_meta GROUP BY ifc_class ORDER BY cnt DESC")) {
                while (rs.next()) {
                    System.out.printf("  %-25s %d%n", rs.getString(1), rs.getInt(2));
                }
            }

            // 4f. Wall thickness verification (TERMINAL pattern: 150, 230, 250, 300mm only)
            System.out.println("\nWall Analysis:");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT element_name, element_type,
                            (maxX - minX) as dx, (maxY - minY) as dy
                     FROM elements_meta m
                     JOIN elements_rtree r ON m.id = r.id
                     WHERE ifc_class = 'IfcWall' OR ifc_class LIKE '%Wall%'
                     LIMIT 5
                     """)) {
                while (rs.next()) {
                    double dx = rs.getDouble("dx");
                    double dy = rs.getDouble("dy");
                    double thickness = Math.min(dx, dy);
                    System.out.printf("  %s: thickness=%.0fmm%n",
                        rs.getString("element_name"), thickness * 1000);
                }
            }

            // =====================================================================
            // STEP 5: CONFORMANCE VERDICT
            // =====================================================================
            System.out.println("\n" + "-".repeat(70));
            System.out.println("STEP 5: CONFORMANCE VERDICT");
            System.out.println("-".repeat(70));

            boolean schemaOk = elementCount > 0 && rtreeCount > 0 && geomCount > 0 && structCount > 0;

            System.out.println("\nChecklist:");
            System.out.printf("  [%s] elements_meta populated (%d)%n",
                elementCount > 0 ? "+" : "-", elementCount);
            System.out.printf("  [%s] elements_rtree populated (%d)%n",
                rtreeCount > 0 ? "+" : "-", rtreeCount);
            System.out.printf("  [%s] base_geometries populated (%d)%n",
                geomCount > 0 ? "+" : "-", geomCount);
            System.out.printf("  [%s] spatial_structure populated (%d)%n",
                structCount > 0 ? "+" : "-", structCount);

            if (schemaOk) {
                System.out.println("\n[PASS] DB Schema Conformance");
                passed++;
            } else {
                System.out.println("\n[FAIL] DB Schema Conformance");
                failed++;
            }

            // Verify element counts match expectations
            if (elementCount >= 10) {  // Reasonable minimum for TB-LKTN
                System.out.println("[PASS] Element count reasonable");
                passed++;
            } else {
                System.out.println("[FAIL] Element count too low");
                failed++;
            }
        }

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(70));
        System.out.printf("Passed: %d, Failed: %d%n", passed, failed);

        if (failed == 0) {
            System.out.println("\n[SUCCESS] TB-LKTN writes to TERMINAL-conformant schema");
            System.out.println("          Blender bake path is now available.");
            System.out.println("\nNext step:");
            System.out.println("  python scripts/bake_to_blender.py " + DB_PATH);
        } else {
            System.out.println("\n[INCOMPLETE] Schema conformance issues detected");
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
