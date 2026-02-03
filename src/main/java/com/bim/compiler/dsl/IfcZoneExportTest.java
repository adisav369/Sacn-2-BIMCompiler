package com.bim.compiler.dsl;

import com.bim.compiler.builder.WallSpec;
import com.bim.compiler.export.IFCExportConfig;
import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.topology.WallThickness;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 47E Test: Verify IfcZone export for multi-unit buildings.
 *
 * Tests that spaces with unit_id are grouped into IfcZone entities,
 * enabling IFC consumers to distinguish dwelling units.
 */
public class IfcZoneExportTest {

    private static final String OUTPUT_DIR = "output";
    private static final String IFC_PATH = OUTPUT_DIR + "/zone_test.ifc";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Phase 47E: IfcZone Export Test ===\n");

        // Ensure output directory exists
        Files.createDirectories(Path.of(OUTPUT_DIR));

        testMultiUnitZoneExport();

        System.out.println("\n=== Phase 47E Test Passed ===");
    }

    private static void testMultiUnitZoneExport() throws Exception {
        System.out.println("Test: Multi-unit IfcZone export");

        // Create a minimal multi-unit construction spec
        // Two units (A and B), each with 2 rooms
        List<ConstructionSpec.SpaceSpec> spaces = new ArrayList<>();

        // Unit A spaces
        spaces.add(new ConstructionSpec.SpaceSpec(
            "living_a", RoomType.LIVING,
            0.0, 5.0, 0.0, 5.0, 2.8, "A"));
        spaces.add(new ConstructionSpec.SpaceSpec(
            "kitchen_a", RoomType.KITCHEN,
            5.0, 8.0, 0.0, 4.0, 2.8, "A"));

        // Unit B spaces
        spaces.add(new ConstructionSpec.SpaceSpec(
            "living_b", RoomType.LIVING,
            10.0, 15.0, 0.0, 5.0, 2.8, "B"));
        spaces.add(new ConstructionSpec.SpaceSpec(
            "kitchen_b", RoomType.KITCHEN,
            15.0, 18.0, 0.0, 4.0, 2.8, "B"));

        // Minimal walls (just exterior boundary for valid IFC)
        List<WallSpec> walls = new ArrayList<>();
        walls.add(new WallSpec(
            new Point3D(0, 0, 0), new Point3D(18, 0, 0),
            WallThickness.MM_250, 2.8, List.of()));
        walls.add(new WallSpec(
            new Point3D(18, 0, 0), new Point3D(18, 5, 0),
            WallThickness.MM_250, 2.8, List.of()));
        walls.add(new WallSpec(
            new Point3D(18, 5, 0), new Point3D(0, 5, 0),
            WallThickness.MM_250, 2.8, List.of()));
        walls.add(new WallSpec(
            new Point3D(0, 5, 0), new Point3D(0, 0, 0),
            WallThickness.MM_250, 2.8, List.of()));

        ConstructionSpec spec = new ConstructionSpec(
            "Ground",
            2.8,
            walls,
            spaces,
            List.of()  // No sprinklers
        );

        System.out.println("  - Created spec with " + spaces.size() + " spaces");
        System.out.println("    Unit A: living_a, kitchen_a");
        System.out.println("    Unit B: living_b, kitchen_b");

        // Export to IFC
        DSLExporter exporter = new DSLExporter(IFCExportConfig.residential());
        boolean success = exporter.exportStorey(spec, IFC_PATH);

        if (!success) {
            throw new AssertionError("IFC export failed");
        }
        System.out.println("  - IFC export succeeded");

        // Verify IFC file contains IfcZone entities
        String ifcContent = Files.readString(Path.of(IFC_PATH));

        // Count IfcZone entities
        int zoneCount = countPattern(ifcContent, "IFCZONE");
        System.out.println("  - IfcZone count: " + zoneCount);

        if (zoneCount != 2) {
            throw new AssertionError("Expected 2 IfcZone entities (Unit A and B), got " + zoneCount);
        }

        // Verify zone names
        boolean hasUnitA = ifcContent.contains("'Unit_A'");
        boolean hasUnitB = ifcContent.contains("'Unit_B'");

        if (!hasUnitA || !hasUnitB) {
            throw new AssertionError("Missing expected zone names (Unit_A, Unit_B)");
        }
        System.out.println("  - Zone names: Unit_A, Unit_B - verified");

        // Verify IfcRelAssignsToGroup exists (links spaces to zones)
        int relCount = countPattern(ifcContent, "IFCRELASSIGNSTOGROUP");
        System.out.println("  - IfcRelAssignsToGroup count: " + relCount);

        if (relCount < 2) {
            throw new AssertionError("Expected at least 2 IfcRelAssignsToGroup, got " + relCount);
        }

        // Verify IfcSpace entities
        int spaceCount = countPattern(ifcContent, "IFCSPACE");
        System.out.println("  - IfcSpace count: " + spaceCount);

        if (spaceCount != 4) {
            throw new AssertionError("Expected 4 IfcSpace entities, got " + spaceCount);
        }

        System.out.println("\n  Test PASSED: IfcZone export working correctly");
        System.out.println("  Output: " + IFC_PATH);
    }

    private static int countPattern(String content, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(pattern, index)) != -1) {
            count++;
            index++;
        }
        return count;
    }
}
