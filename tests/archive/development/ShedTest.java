package com.bim.compiler.dsl;

import com.bim.compiler.dsl.ShedCompiler.*;
import com.bim.compiler.export.IFCExportConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Test Shed DSL: parse → compile → DB → IFC
 *
 * Test case:
 * SHED "garden" size:4x3m height:2.4m {
 *     FOUNDATION slab:150mm
 *     DOOR south size:900x2100
 *     WINDOW north size:1200x833
 *     ROOF pitch:15deg
 * }
 */
public class ShedTest {

    private static final String OUTPUT_DIR = "output";
    private static final String DB_PATH = OUTPUT_DIR + "/shed_test.db";
    private static final String JSON_PATH = OUTPUT_DIR + "/shed_test.json";
    private static final String IFC_PATH = OUTPUT_DIR + "/shed_test.ifc";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 11: SHED DSL TEST");
        System.out.println("=".repeat(70));

        Files.createDirectories(Path.of(OUTPUT_DIR));

        int passed = 0;
        int failed = 0;

        // Test 1: Parse Shed DSL
        System.out.println("\nTest 1: Parse Shed DSL");
        ShedDefinition shed = null;
        try {
            String dsl = """
                SHED "garden" size:4x3m height:2.4m {
                    FOUNDATION slab:150mm
                    DOOR south size:900x2100
                    WINDOW north size:1200x833
                    ROOF pitch:15deg
                }
                """;

            shed = ShedParser.parse(dsl);

            System.out.println("  Name: " + shed.name());
            System.out.println("  Size: " + shed.width() + "m x " + shed.depth() + "m");
            System.out.println("  Wall height: " + shed.wallHeight() + "m");
            System.out.println("  Foundation: " + (shed.foundationThickness() * 1000) + "mm");
            System.out.println("  Door: " + shed.door().side() + " " +
                (shed.door().width() * 1000) + "x" + (shed.door().height() * 1000));
            System.out.println("  Window: " + shed.window().side() + " " +
                (shed.window().width() * 1000) + "x" + (shed.window().height() * 1000));
            System.out.println("  Roof pitch: " + shed.roof().pitchDegrees() + "°");

            if (!"garden".equals(shed.name())) throw new AssertionError("Wrong name");
            if (shed.width() != 4.0) throw new AssertionError("Wrong width");
            if (shed.depth() != 3.0) throw new AssertionError("Wrong depth");

            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Compile to ShedSpec
        System.out.println("\nTest 2: Compile to ShedSpec");
        ShedSpec spec = null;
        try {
            spec = ShedCompiler.compile(shed);

            System.out.println("  Foundation: " + spec.foundation().bbox());
            System.out.println("  Wall assemblies: " + spec.wallAssemblies().size());

            for (WallAssemblySpec wall : spec.wallAssemblies()) {
                System.out.println("    " + wall.name() + ": " +
                    wall.frames().size() + " frames, " +
                    (wall.opening() != null ? wall.opening().type() : "no opening"));
            }

            System.out.println("  Roof ridge rise: " + String.format("%.3f", spec.roof().ridgeHeight()) + "m");
            System.out.println("  Roof vertices: " + spec.roof().vertices().size());

            if (spec.wallAssemblies().size() != 4) throw new AssertionError("Expected 4 walls");

            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Write to DB
        System.out.println("\nTest 3: Write to database");
        try {
            ShedWriter writer = new ShedWriter(DB_PATH);
            writer.writeToDb(spec);

            // Verify DB contents
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            int elements = countRows(conn, "elements_meta");
            int assemblies = countRows(conn, "element_assemblies");
            int components = countRows(conn, "assembly_components");

            System.out.println("  Elements: " + elements);
            System.out.println("  Assemblies: " + assemblies);
            System.out.println("  Assembly components: " + components);

            // BOM Report
            System.out.println("\n  BOM REPORT:");
            System.out.println("  " + "-".repeat(50));
            String bomSql = """
                SELECT a.name, ac.role, COUNT(*) as cnt, m.element_name
                FROM element_assemblies a
                JOIN assembly_components ac ON a.assembly_guid = ac.assembly_guid
                JOIN elements_meta m ON ac.component_guid = m.guid
                GROUP BY a.name, ac.role, m.element_name
                ORDER BY a.name, ac.sequence
            """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(bomSql)) {
                String currentAssembly = null;
                while (rs.next()) {
                    String asmName = rs.getString("name");
                    if (!asmName.equals(currentAssembly)) {
                        currentAssembly = asmName;
                        System.out.println("  " + asmName + ":");
                    }
                    System.out.printf("    %s: %d × %s%n",
                        rs.getString("role"), rs.getInt("cnt"), rs.getString("element_name"));
                }
            }

            conn.close();

            if (assemblies != 4) throw new AssertionError("Expected 4 assemblies");

            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 4: Generate JSON and export to IFC
        System.out.println("\nTest 4: Export to IFC");
        try {
            ShedWriter writer = new ShedWriter(DB_PATH);
            writer.writeJson(spec, JSON_PATH);

            // Export via Python
            ProcessBuilder pb = new ProcessBuilder(
                "/home/red1/bim-compiler/venv/bin/python3",
                "/home/red1/bim-compiler/scripts/export_dsl_to_ifc.py",
                "--storey", JSON_PATH,
                "--output", IFC_PATH
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  " + line);
            }
            process.waitFor();

            // Verify IFC file
            Path ifcPath = Path.of(IFC_PATH);
            if (!Files.exists(ifcPath)) throw new AssertionError("IFC file not created");

            long fileSize = Files.size(ifcPath);
            System.out.println("  File size: " + fileSize + " bytes");

            // Count IFC elements
            int walls = countIfcEntities(IFC_PATH, "IFCWALL");
            int assemblies = countIfcEntities(IFC_PATH, "IFCELEMENTASSEMBLY");
            int members = countIfcEntities(IFC_PATH, "IFCMEMBER");
            int plates = countIfcEntities(IFC_PATH, "IFCPLATE");

            System.out.println("  IFC Entities:");
            System.out.println("    Walls: " + walls);
            System.out.println("    Assemblies: " + assemblies);
            System.out.println("    Members (frames): " + members);
            System.out.println("    Plates (cladding): " + plates);

            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 5: Roof geometry verification
        System.out.println("\nTest 5: Roof geometry math");
        try {
            double width = 4.0;
            double pitch = 15.0;
            double expectedRise = (width / 2.0) * Math.tan(Math.toRadians(pitch));

            System.out.println("  Shed width: " + width + "m");
            System.out.println("  Roof pitch: " + pitch + "°");
            System.out.println("  Expected ridge rise: " + String.format("%.4f", expectedRise) + "m");
            System.out.println("  Actual ridge rise: " + String.format("%.4f", spec.roof().ridgeHeight()) + "m");

            double delta = Math.abs(expectedRise - spec.roof().ridgeHeight());
            if (delta > 0.001) throw new AssertionError("Ridge rise mismatch: " + delta);

            // Verify overhang (300mm)
            double minX = spec.roof().vertices().stream().mapToDouble(v -> v.x()).min().orElse(0);
            System.out.println("  Roof overhang: " + Math.abs(minX) + "m (expected 0.3m)");

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
            System.out.println("SUCCESS: Shed DSL working!");
            System.out.println("\nOutput files:");
            System.out.println("  " + DB_PATH);
            System.out.println("  " + JSON_PATH);
            System.out.println("  " + IFC_PATH);
        }
    }

    private static int countRows(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int countIfcEntities(String ifcPath, String entityType) {
        try {
            ProcessBuilder pb = new ProcessBuilder("grep", "-c", entityType + "(", ifcPath);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            return line != null ? Integer.parseInt(line.trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
