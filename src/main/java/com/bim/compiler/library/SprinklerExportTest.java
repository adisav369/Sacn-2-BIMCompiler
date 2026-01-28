package com.bim.compiler.library;

import com.bim.compiler.geometry.Point3D;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.SprinklerPlacer.SprinklerInstance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Test full sprinkler export pipeline:
 * SprinklerPlacer → JSON → Python → IFC
 *
 * Verifies:
 * 1. Grid placement generates correct JSON
 * 2. IFC export succeeds
 * 3. File size is reasonable (instancing works)
 */
public class SprinklerExportTest {

    private static final String LIBRARY_PATH = "library/component_library.db";
    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("SPRINKLER EXPORT TEST");
        System.out.println("=".repeat(70));

        Files.createDirectories(Path.of(OUTPUT_DIR));

        ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
        SprinklerPlacer placer = new SprinklerPlacer(library);

        // Test 1: Single pendant sprinkler
        System.out.println("\nTest 1: Single pendant sprinkler");
        testSingleSprinkler(placer);

        // Test 2: Grid placement (simulates SPRINKLERS grid:4.6m area:ROOM)
        System.out.println("\nTest 2: Grid placement (20m x 20m room)");
        testGridPlacement(placer);

        // Test 3: Verify file size efficiency
        System.out.println("\nTest 3: Compare file sizes (instancing efficiency)");
        testInstancingEfficiency(placer);

        library.close();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("ALL EXPORT TESTS COMPLETE");
        System.out.println("=".repeat(70));
    }

    private static void testSingleSprinkler(SprinklerPlacer placer) throws Exception {
        Point3D ceiling = new Point3D(5.0, 5.0, 3.0);
        SprinklerInstance instance = placer.placePendant(ceiling, 0.0);

        List<Map<String, Object>> json = toJson(List.of(instance));
        String jsonPath = OUTPUT_DIR + "/single_sprinkler.json";
        String ifcPath = OUTPUT_DIR + "/single_sprinkler.ifc";

        writeJson(json, jsonPath);
        boolean success = runPythonExport(jsonPath, ifcPath);

        System.out.println("  JSON: " + jsonPath);
        System.out.println("  IFC:  " + ifcPath);
        System.out.println("  Export: " + (success ? "SUCCESS" : "FAILED"));
    }

    private static void testGridPlacement(SprinklerPlacer placer) throws Exception {
        // 20m x 20m room with 4.6m spacing = ~16 sprinklers
        BoundingBox area = new BoundingBox(0, 20, 0, 20, 0, 3);
        List<SprinklerInstance> instances = placer.placeGrid(area, 3.0, 4.6);

        System.out.println("  Sprinklers placed: " + instances.size());

        List<Map<String, Object>> json = toJson(instances);
        String jsonPath = OUTPUT_DIR + "/grid_sprinklers.json";
        String ifcPath = OUTPUT_DIR + "/grid_sprinklers.ifc";

        writeJson(json, jsonPath);
        boolean success = runPythonExport(jsonPath, ifcPath);

        long fileSize = Files.size(Path.of(ifcPath));
        System.out.println("  JSON: " + jsonPath);
        System.out.println("  IFC:  " + ifcPath + " (" + fileSize / 1024 + " KB)");
        System.out.println("  Export: " + (success ? "SUCCESS" : "FAILED"));
    }

    private static void testInstancingEfficiency(SprinklerPlacer placer) throws Exception {
        // 50m x 50m area with 4.6m spacing = ~100 sprinklers
        BoundingBox area = new BoundingBox(0, 50, 0, 50, 0, 3);
        List<SprinklerInstance> instances = placer.placeGrid(area, 4.6, 4.6);

        System.out.println("  Sprinklers: " + instances.size());

        List<Map<String, Object>> json = toJson(instances);
        String jsonPath = OUTPUT_DIR + "/many_sprinklers.json";
        String ifcPath = OUTPUT_DIR + "/many_sprinklers.ifc";

        writeJson(json, jsonPath);
        boolean success = runPythonExport(jsonPath, ifcPath);

        if (success) {
            long fileSize = Files.size(Path.of(ifcPath));
            double kbPerSprinkler = (double) fileSize / 1024.0 / instances.size();
            System.out.println("  File size: " + fileSize / 1024 + " KB");
            System.out.println("  KB per sprinkler: " + String.format("%.2f", kbPerSprinkler));

            // Estimate: without instancing, each sprinkler would be ~50KB (2000 vertices)
            // With instancing, should be much less
            if (kbPerSprinkler < 10) {
                System.out.println("  Instancing: EFFICIENT (< 10 KB/sprinkler)");
            } else {
                System.out.println("  Instancing: NEEDS IMPROVEMENT (> 10 KB/sprinkler)");
            }
        }
    }

    private static List<Map<String, Object>> toJson(List<SprinklerInstance> instances) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SprinklerInstance s : instances) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", s.type().name());
            m.put("position", List.of(
                s.worldCenter().x(),
                s.worldCenter().y(),
                s.worldCenter().z()
            ));
            m.put("rotation", s.rotation());
            m.put("geometry_hash", s.definition().geometryHash());
            result.add(m);
        }
        return result;
    }

    private static void writeJson(List<Map<String, Object>> data, String path) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(Path.of(path), gson.toJson(data));
    }

    private static boolean runPythonExport(String jsonPath, String ifcPath) {
        try {
            // Use venv Python with ifcopenshell
            ProcessBuilder pb = new ProcessBuilder(
                "venv/bin/python", "scripts/export_sprinklers_to_ifc.py",
                jsonPath, ifcPath
            );
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("  Error: " + e.getMessage());
            return false;
        }
    }
}
