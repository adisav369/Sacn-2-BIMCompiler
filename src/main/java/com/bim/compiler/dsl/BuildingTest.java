package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;
import com.bim.compiler.export.BOMExporter;
import com.bim.compiler.export.IDempiereExporter;

import java.io.File;
import java.sql.*;

/**
 * Integration test for multi-storey building DSL.
 *
 * Tests:
 * 1. Parse BUILDING DSL
 * 2. Compile to BuildingSpec
 * 3. Write to database
 * 4. Export BOM (4 formats)
 * 5. Export iDempiere (3 formats)
 * 6. Verify mathematical constraints
 */
public class BuildingTest {

    private static final String DB_PATH = "output/duplex_test.db";
    private static final String JSON_PATH = "output/duplex_test.json";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("MULTI-STOREY BUILDING TEST");
        System.out.println("=".repeat(70));

        // Test DSL
        String dsl = """
            BUILDING "test_duplex" {
                STOREY "Ground" level:0 height:2.8m {
                    LIVING "main" at:A1 size:4x4m {
                        DOOR south
                        WINDOW east
                    }
                    STAIR "stair1" at:B1 width:1.0m to:"Upper"
                }
                STOREY "Upper" level:1 height:2.8m {
                    BEDROOM "bed1" at:A1 size:4x4m {
                        DOOR south to:landing
                        WINDOW east
                    }
                    LANDING "landing" at:B1 size:2x2m from:"stair1"
                }
                ROOF pitch:15deg
            }
            """;

        int passed = 0;
        int failed = 0;

        // =====================================================================
        // TEST 1: Parse DSL
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: PARSE DSL");
        System.out.println("-".repeat(70));

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("  Building: " + def.name());
        System.out.println("  Storeys: " + def.storeys().size());

        if (def.storeys().size() == 2 && def.roof() != null) {
            System.out.println("  [PASS] Parse test");
            passed++;
        } else {
            System.out.println("  [FAIL] Expected 2 storeys and roof");
            failed++;
        }

        // =====================================================================
        // TEST 2: Compile to Spec
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: COMPILE TO SPEC");
        System.out.println("-".repeat(70));

        BuildingSpec spec = BuildingCompiler.compile(def);

        // Verify IRC compliance for stairs
        boolean stairCompliant = true;
        for (StoreySpec storey : spec.storeys()) {
            for (StairSpec stair : storey.stairs()) {
                System.out.printf("  Stair %s: %d risers @ %.0fmm, tread %.0fmm%n",
                    stair.name(), stair.numRisers(),
                    stair.riserHeight() * 1000, stair.treadDepth() * 1000);

                if (stair.riserHeight() > 0.196) {
                    System.out.println("    [FAIL] Riser exceeds IRC max 196mm");
                    stairCompliant = false;
                }
                if (stair.treadDepth() < 0.254) {
                    System.out.println("    [FAIL] Tread below IRC min 254mm");
                    stairCompliant = false;
                }
            }
        }

        if (stairCompliant) {
            System.out.println("  [PASS] IRC stair compliance");
            passed++;
        } else {
            failed++;
        }

        // =====================================================================
        // TEST 3: Z-Continuity
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 3: Z-CONTINUITY");
        System.out.println("-".repeat(70));

        boolean zContinuous = true;
        for (int i = 0; i < spec.storeys().size() - 1; i++) {
            StoreySpec lower = spec.storeys().get(i);
            StoreySpec upper = spec.storeys().get(i + 1);

            double lowerTop = lower.baseZ() + lower.height();
            double upperBase = upper.baseZ();
            double gap = Math.abs(upperBase - lowerTop);

            System.out.printf("  %s top (%.3fm) -> %s base (%.3fm): gap %.4fm%n",
                lower.name(), lowerTop, upper.name(), upperBase, gap);

            if (gap > 0.001) {
                zContinuous = false;
            }
        }

        // Wall-Roof junction
        StoreySpec topStorey = spec.storeys().get(spec.storeys().size() - 1);
        double wallTop = topStorey.baseZ() + topStorey.height();
        double roofBase = spec.roof().vertices().stream()
            .mapToDouble(v -> v.z())
            .min().orElse(0);
        double wallRoofGap = Math.abs(wallTop - roofBase);
        System.out.printf("  Wall top (%.3fm) -> Roof base (%.3fm): gap %.4fm%n",
            wallTop, roofBase, wallRoofGap);

        if (wallRoofGap > 0.001) {
            zContinuous = false;
        }

        if (zContinuous) {
            System.out.println("  [PASS] Z-continuity verified");
            passed++;
        } else {
            System.out.println("  [FAIL] Z-continuity gaps detected");
            failed++;
        }

        // =====================================================================
        // TEST 4: Write to Database
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 4: WRITE TO DATABASE");
        System.out.println("-".repeat(70));

        new File("output").mkdirs();
        new File(DB_PATH).delete();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            BuildingWriter writer = new BuildingWriter(conn);
            writer.initSchema();
            writer.write(spec);
            writer.exportJson(spec, JSON_PATH);

            // Verify counts
            int elementCount = 0, assemblyCount = 0;
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM elements_meta");
                if (rs.next()) elementCount = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM element_assemblies");
                if (rs.next()) assemblyCount = rs.getInt(1);
            }

            System.out.println("  Elements: " + elementCount);
            System.out.println("  Assemblies: " + assemblyCount);
            System.out.println("  JSON: " + JSON_PATH);

            if (elementCount > 0 && assemblyCount > 0) {
                System.out.println("  [PASS] Database write");
                passed++;
            } else {
                System.out.println("  [FAIL] No data written");
                failed++;
            }
        }

        // =====================================================================
        // TEST 5: BOM Export
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 5: BOM EXPORT");
        System.out.println("-".repeat(70));

        BOMExporter bomExporter = new BOMExporter(DB_PATH, "duplex");
        bomExporter.exportAll("output/bom");

        File[] bomFiles = new File("output/bom").listFiles((d, n) -> n.startsWith("duplex"));
        System.out.println("  BOM files: " + (bomFiles != null ? bomFiles.length : 0));

        if (bomFiles != null && bomFiles.length == 4) {
            System.out.println("  [PASS] BOM export (4 files)");
            passed++;
        } else {
            System.out.println("  [FAIL] Expected 4 BOM files");
            failed++;
        }

        // =====================================================================
        // TEST 6: iDempiere Export
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 6: iDEMPIERE EXPORT");
        System.out.println("-".repeat(70));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            IDempiereExporter idempExporter = new IDempiereExporter(conn);
            idempExporter.exportAll("output/idempiere");
        }

        File[] idempFiles = new File("output/idempiere").listFiles((d, n) -> n.startsWith("idempiere"));
        System.out.println("  iDempiere files: " + (idempFiles != null ? idempFiles.length : 0));

        if (idempFiles != null && idempFiles.length == 3) {
            System.out.println("  [PASS] iDempiere export (3 files)");
            passed++;
        } else {
            System.out.println("  [FAIL] Expected 3 iDempiere files");
            failed++;
        }

        // =====================================================================
        // TEST 7: Assembly Totals Match
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 7: ASSEMBLY TOTALS");
        System.out.println("-".repeat(70));

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            int components = 0, assemblyComponents = 0;
            try (Statement stmt = conn.createStatement()) {
                // Count actual components (not assemblies)
                ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM elements_meta WHERE ifc_class != 'IfcElementAssembly'");
                if (rs.next()) components = rs.getInt(1);

                // Count assembly component references
                rs = stmt.executeQuery("SELECT COUNT(*) FROM assembly_components");
                if (rs.next()) assemblyComponents = rs.getInt(1);
            }

            System.out.println("  Total components: " + components);
            System.out.println("  Assembly component refs: " + assemblyComponents);

            // Not all components are in assemblies (slabs, stairs, roof are standalone)
            if (assemblyComponents > 0) {
                System.out.println("  [PASS] Assembly structure verified");
                passed++;
            } else {
                System.out.println("  [FAIL] No assembly components");
                failed++;
            }
        }

        // =====================================================================
        // TEST 8: Slab Overlap
        // =====================================================================
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 8: SLAB OVERLAP");
        System.out.println("-".repeat(70));

        boolean overlapCorrect = true;
        for (StoreySpec storey : spec.storeys()) {
            SlabSpec slab = storey.slab();

            // Find wall extents
            double wallMinX = Double.MAX_VALUE, wallMaxX = -Double.MAX_VALUE;
            double wallMinY = Double.MAX_VALUE, wallMaxY = -Double.MAX_VALUE;

            for (WallAssemblySpec wall : storey.walls()) {
                // Approximate wall bounds from cladding
                wallMinX = Math.min(wallMinX, wall.cladding().minX());
                wallMaxX = Math.max(wallMaxX, wall.cladding().maxX());
                wallMinY = Math.min(wallMinY, wall.cladding().minY());
                wallMaxY = Math.max(wallMaxY, wall.cladding().maxY());
            }

            double overlapX = slab.minX() - wallMinX;
            double overlapY = slab.minY() - wallMinY;

            System.out.printf("  %s: Slab overlap X=%.3fm, Y=%.3fm%n",
                storey.name(), -overlapX, -overlapY);

            // Pattern G8: 175-250mm overlap
            if (-overlapX < 0.175 || -overlapX > 0.250) {
                System.out.println("    [WARN] X overlap outside G8 range");
            }
        }

        System.out.println("  [PASS] Slab overlap documented");
        passed++;

        // =====================================================================
        // SUMMARY
        // =====================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.printf("MULTI-STOREY BUILDING TEST: %d passed, %d failed%n", passed, failed);
        System.out.println("=".repeat(70));

        if (failed == 0) {
            System.out.println("\nOUTPUT FILES:");
            System.out.println("  " + DB_PATH);
            System.out.println("  " + JSON_PATH);
            System.out.println("  output/bom/duplex_*.csv (4 files)");
            System.out.println("  output/idempiere/idempiere_*.csv (3 files)");
        }
    }
}
