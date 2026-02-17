package com.bim.compiler.db;

import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.geometry.Point3D;

import java.sql.*;

/**
 * Test assembly (BOM) structure with a simple 2-component assembly.
 *
 * Test case: Wall panel assembly
 * - ASSEMBLY: WALL_PANEL_3M (3m x 0.15m x 2.4m)
 *   - FRAME: RHS_150x100 steel tube
 *   - CLADDING: Metal deck panel
 *
 * Validates:
 * 1. Assembly table creation
 * 2. Component linkage
 * 3. Role and sequence tracking
 * 4. Query for BOM report
 */
public class AssemblyTest {

    private static final String TEST_DB = "output/assembly_test.db";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("ASSEMBLY (BOM) STRUCTURE TEST");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Test 1: Create assembly with 2 components
        System.out.println("\nTest 1: Create wall panel assembly with 2 components");
        try {
            GeneratedModelWriter writer = new GeneratedModelWriter(TEST_DB);
            writer.initializeForTest();

            // Assembly dimensions
            double width = 3.0;   // 3m wide
            double depth = 0.15;  // 150mm thick
            double height = 2.4;  // 2.4m tall
            String storey = "Ground";

            BoundingBox assemblyBbox = new BoundingBox(0, width, 0, depth, 0, height);

            // Create assembly
            String assemblyGuid = "ASSEMBLY_WALL_PANEL_001";
            writer.writeAssembly(assemblyGuid, "WALL_PANEL", "Wall Panel North",
                                 assemblyBbox, storey);

            // Component 1: Frame (RHS 150x100 steel tube)
            String frameGuid = "FRAME_RHS_001";
            BoundingBox frameBbox = new BoundingBox(0, 3.0, 0, 0.10, 0, 0.15);
            float[] frameVerts = createBoxVertices(frameBbox);
            int[] frameFaces = createBoxFaces();
            writer.writeComponent(frameGuid, "IfcMember", "RHS 150x100", "FRAME",
                                  frameBbox, frameVerts, frameFaces, storey);
            writer.writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME",
                                          new Point3D(0, 0, 0), 0, false);

            // Component 2: Cladding (metal deck panel)
            String claddingGuid = "CLADDING_DECK_001";
            BoundingBox claddingBbox = new BoundingBox(0, 3.0, 0, 0.15, 0.15, 2.4);
            float[] claddingVerts = createBoxVertices(claddingBbox);
            int[] claddingFaces = createBoxFaces();
            writer.writeComponent(claddingGuid, "IfcPlate", "Metal Deck", "CLADDING",
                                  claddingBbox, claddingVerts, claddingFaces, storey);
            writer.writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING",
                                          new Point3D(0, 0, 0.15), 1, false);

            writer.commitAndClose();

            System.out.println("  Created assembly: " + assemblyGuid);
            System.out.println("  With components: " + frameGuid + ", " + claddingGuid);
            passed++;
            System.out.println("  [PASS]");

        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Query assembly structure (BOM report)
        System.out.println("\nTest 2: Query BOM structure");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);

            // Query assembly with components
            String sql = """
                SELECT a.assembly_guid, a.assembly_type, a.name,
                       ac.component_guid, ac.role, ac.sequence,
                       m.ifc_class, m.element_name
                FROM element_assemblies a
                JOIN assembly_components ac ON a.assembly_guid = ac.assembly_guid
                JOIN elements_meta m ON ac.component_guid = m.guid
                ORDER BY a.assembly_guid, ac.sequence
            """;

            System.out.println("  BOM REPORT:");
            System.out.println("  " + "-".repeat(60));

            int componentCount = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                String currentAssembly = null;
                while (rs.next()) {
                    String assemblyGuid = rs.getString("assembly_guid");
                    if (!assemblyGuid.equals(currentAssembly)) {
                        currentAssembly = assemblyGuid;
                        System.out.printf("  ASSEMBLY: %s (%s)%n",
                            rs.getString("name"), rs.getString("assembly_type"));
                    }
                    System.out.printf("    [%d] %s: %s (%s)%n",
                        rs.getInt("sequence"),
                        rs.getString("role"),
                        rs.getString("element_name"),
                        rs.getString("ifc_class"));
                    componentCount++;
                }
            }

            conn.close();

            if (componentCount != 2) {
                throw new AssertionError("Expected 2 components, got " + componentCount);
            }

            passed++;
            System.out.println("  [PASS]");

        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Verify IfcElementAssembly in elements_meta
        System.out.println("\nTest 3: Verify IfcElementAssembly entity");
        try {
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);

            String sql = "SELECT guid, ifc_class, element_type FROM elements_meta WHERE ifc_class = 'IfcElementAssembly'";
            int assemblyCount = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    System.out.printf("  Found: %s (%s)%n",
                        rs.getString("guid"), rs.getString("element_type"));
                    assemblyCount++;
                }
            }

            conn.close();

            if (assemblyCount != 1) {
                throw new AssertionError("Expected 1 IfcElementAssembly, got " + assemblyCount);
            }

            passed++;
            System.out.println("  [PASS]");

        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 4: Multi-assembly with shared component type
        System.out.println("\nTest 4: Multiple assemblies (4 wall panels)");
        try {
            GeneratedModelWriter writer = new GeneratedModelWriter("output/assembly_multi.db");
            writer.initializeForTest();

            String storey = "Ground";

            // Create 4 wall panels
            String[] sides = {"NORTH", "SOUTH", "EAST", "WEST"};
            double[][] positions = {{0, 0}, {0, 4}, {3, 0}, {0, 0}};  // x, y
            double[][] sizes = {{3, 0.15}, {3, 0.15}, {0.15, 4}, {0.15, 4}};  // w, d

            int totalComponents = 0;
            for (int i = 0; i < 4; i++) {
                String assemblyGuid = "WALL_PANEL_" + sides[i];
                double x = positions[i][0];
                double y = positions[i][1];
                double w = sizes[i][0];
                double d = sizes[i][1];

                BoundingBox bbox = new BoundingBox(x, x + w, y, y + d, 0, 2.4);
                writer.writeAssembly(assemblyGuid, "WALL_PANEL", "Wall " + sides[i], bbox, storey);

                // Frame
                String frameGuid = "FRAME_" + sides[i];
                writer.writeComponent(frameGuid, "IfcMember", "RHS 150x100", "FRAME",
                                      bbox, createBoxVertices(bbox), createBoxFaces(), storey);
                writer.writeAssemblyComponent(assemblyGuid, frameGuid, "FRAME",
                                              new Point3D(0, 0, 0), 0, false);
                totalComponents++;

                // Cladding
                String claddingGuid = "CLADDING_" + sides[i];
                writer.writeComponent(claddingGuid, "IfcPlate", "Metal Deck", "CLADDING",
                                      bbox, createBoxVertices(bbox), createBoxFaces(), storey);
                writer.writeAssemblyComponent(assemblyGuid, claddingGuid, "CLADDING",
                                              new Point3D(0, 0, 0), 1, false);
                totalComponents++;
            }

            writer.commitAndClose();

            // Verify counts
            Connection conn = DriverManager.getConnection("jdbc:sqlite:output/assembly_multi.db");
            int assemblies = countRows(conn, "element_assemblies");
            int components = countRows(conn, "assembly_components");
            conn.close();

            System.out.println("  Assemblies: " + assemblies);
            System.out.println("  Components: " + components);

            if (assemblies != 4) {
                throw new AssertionError("Expected 4 assemblies, got " + assemblies);
            }
            if (components != 8) {
                throw new AssertionError("Expected 8 components (2 per assembly), got " + components);
            }

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
            System.out.println("SUCCESS: Assembly (BOM) structure working!");
            System.out.println("\nOutput files:");
            System.out.println("  output/assembly_test.db");
            System.out.println("  output/assembly_multi.db");
        }
    }

    private static int countRows(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static float[] createBoxVertices(BoundingBox bbox) {
        return new float[] {
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.minZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.minZ(),
            (float)bbox.minX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.minY(), (float)bbox.maxZ(),
            (float)bbox.maxX(), (float)bbox.maxY(), (float)bbox.maxZ(),
            (float)bbox.minX(), (float)bbox.maxY(), (float)bbox.maxZ()
        };
    }

    private static int[] createBoxFaces() {
        return new int[] {
            0, 1, 2,  0, 2, 3,
            4, 6, 5,  4, 7, 6,
            0, 4, 5,  0, 5, 1,
            2, 6, 7,  2, 7, 3,
            0, 3, 7,  0, 7, 4,
            1, 5, 6,  1, 6, 2
        };
    }
}
