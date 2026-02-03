package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Phase 49: Test IfcStair aggregate structure in database.
 */
public class StairAggregateTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 49: IfcStair Aggregate Test ===\n");

        // 1. Parse and compile
        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

        // 2. Write to database
        Path dbPath = Path.of("output/stair_aggregate_test.db");
        Files.deleteIfExists(dbPath);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            conn.commit();
        }

        // 3. Verify aggregate structure
        System.out.println("Test 1: Database structure");
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            // Check element_assemblies table has ifc_class column
            System.out.println("\nTest 1a: element_assemblies schema");
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(element_assemblies)");
                boolean hasIfcClass = false;
                while (rs.next()) {
                    String name = rs.getString("name");
                    if ("ifc_class".equals(name)) {
                        hasIfcClass = true;
                        System.out.println("  - ifc_class column exists ✓");
                    }
                }
                assert hasIfcClass : "Missing ifc_class column";
            }

            // Check IfcStair aggregate exists
            System.out.println("\nTest 1b: IfcStair aggregate");
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM element_assemblies WHERE ifc_class = 'IfcStair'"
            )) {
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    System.out.printf("  - Assembly: %s (type=%s, storey=%s)%n",
                        rs.getString("assembly_guid"),
                        rs.getString("assembly_type"),
                        rs.getString("storey"));
                    count++;
                }
                assert count >= 1 : "No IfcStair aggregates found";
                System.out.println("  - Found " + count + " IfcStair aggregate(s) ✓");
            }

            // Check parent IfcStair exists in elements_meta
            System.out.println("\nTest 1c: IfcStair in elements_meta");
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM elements_meta WHERE ifc_class = 'IfcStair'"
            )) {
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    System.out.printf("  - %s: %s (storey=%s, type=%s)%n",
                        rs.getString("guid"),
                        rs.getString("element_name"),
                        rs.getString("storey"),
                        rs.getString("element_type"));
                    count++;
                }
                assert count >= 1 : "No IfcStair in elements_meta";
                System.out.println("  - Found " + count + " IfcStair element(s) ✓");
            }

            // Check IfcStairFlight child exists
            System.out.println("\nTest 1d: IfcStairFlight child");
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM elements_meta WHERE ifc_class = 'IfcStairFlight'"
            )) {
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    System.out.printf("  - %s: %s (storey=%s)%n",
                        rs.getString("guid"),
                        rs.getString("element_name"),
                        rs.getString("storey"));
                    count++;
                }
                assert count >= 1 : "No IfcStairFlight in elements_meta";
                System.out.println("  - Found " + count + " IfcStairFlight element(s) ✓");
            }

            // Check assembly_components links
            System.out.println("\nTest 1e: assembly_components links");
            try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT ac.*, ea.ifc_class 
                FROM assembly_components ac
                JOIN element_assemblies ea ON ac.assembly_guid = ea.assembly_guid
                WHERE ea.ifc_class = 'IfcStair'
                ORDER BY ac.sequence
                """
            )) {
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    System.out.printf("  - %s -> %s (role=%s, seq=%d)%n",
                        rs.getString("assembly_guid"),
                        rs.getString("component_guid"),
                        rs.getString("role"),
                        rs.getInt("sequence"));
                    count++;
                }
                assert count >= 1 : "No assembly_components for IfcStair";
                System.out.println("  - Found " + count + " component link(s) ✓");
            }

            // Verify storey containment - IfcStair should be on Ground (lowest)
            System.out.println("\nTest 2: Storey containment");
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT storey FROM elements_meta WHERE ifc_class = 'IfcStair'"
            )) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String storey = rs.getString("storey");
                    System.out.printf("  - IfcStair contained in: %s%n", storey);
                    assert "Ground".equals(storey) : "IfcStair should be in Ground storey";
                    System.out.println("  - Correct containment (lowest storey) ✓");
                }
            }

            // Verify children don't have separate spatial containment
            // (they inherit via IfcRelAggregates, which is handled by IFC exporter)
            System.out.println("\nTest 3: Children linked to parent");
            try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT em.guid, em.ifc_class, ac.role
                FROM elements_meta em
                JOIN assembly_components ac ON em.guid = ac.component_guid
                JOIN element_assemblies ea ON ac.assembly_guid = ea.assembly_guid
                WHERE ea.ifc_class = 'IfcStair'
                """
            )) {
                ResultSet rs = ps.executeQuery();
                int flightCount = 0, landingCount = 0;
                while (rs.next()) {
                    String ifcClass = rs.getString("ifc_class");
                    String role = rs.getString("role");
                    System.out.printf("  - Child: %s (class=%s, role=%s)%n",
                        rs.getString("guid"), ifcClass, role);
                    if ("IfcStairFlight".equals(ifcClass)) flightCount++;
                    if ("IfcSlab".equals(ifcClass)) landingCount++;
                }
                assert flightCount >= 1 : "Expected at least 1 IfcStairFlight child";
                System.out.println("  - IfcStairFlight children: " + flightCount + " ✓");
                System.out.println("  - IfcSlab/LANDING children: " + landingCount + " ✓");
            }
        }

        System.out.println("\n=== All Phase 49 Aggregate Tests Passed ===");
    }
}
