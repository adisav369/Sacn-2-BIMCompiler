package com.bim.compiler.dsl;

import com.bim.compiler.export.IFCExportConfig;
import com.bim.compiler.util.BIMLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Test unified IFC export with walls, spaces, and sprinklers.
 *
 * Phase 10: Combines previously separate export paths into single output.
 *
 * Test case:
 * - 2 rooms (bedroom + corridor)
 * - 4 walls per room + shared wall = ~7 unique walls
 * - 1 door between rooms
 * - Sprinklers in both rooms
 *
 * Expected output: Single IFC file with walls, openings, spaces, AND sprinklers.
 */
public class UnifiedExportTest {

    private static final String OUTPUT_DIR = "output";

    public static void main(String[] args) throws Exception {
        BIMLogger.setLevel(BIMLogger.Level.INFO);

        System.out.println("=".repeat(70));
        System.out.println("PHASE 10: UNIFIED IFC EXPORT TEST");
        System.out.println("=".repeat(70));

        // Ensure output directory exists
        Files.createDirectories(Path.of(OUTPUT_DIR));

        int passed = 0;
        int failed = 0;

        // Test 1: Basic unified export
        System.out.println("\nTest 1: Unified export (walls + spaces + sprinklers)");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    BEDROOM "master" at:A1 size:5x4m {
                        DOOR south to:corridor
                        WINDOW north
                        SPRINKLERS grid:4.6m
                    }
                    CORRIDOR "corridor" at:A2 size:5x1.2m {
                        DOOR north to:master
                    }
                }
                """;

            // Parse DSL
            StoreyDefinition storey = DSLParser.parse(dsl);
            System.out.println("  Parsed: " + storey.rooms().size() + " rooms");

            // Resolve layout
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            System.out.println("  Resolved: " + layout.size() + " room polygons");

            // Compile to ConstructionSpec
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);
            System.out.println("  Compiled: " +
                spec.walls().size() + " walls, " +
                spec.spaces().size() + " spaces, " +
                spec.sprinklerGrids().size() + " sprinkler grids");

            // Count expected openings
            int openings = spec.walls().stream()
                .mapToInt(w -> w.openings().size())
                .sum();
            System.out.println("  Openings: " + openings);

            // Export to IFC
            String outputPath = OUTPUT_DIR + "/unified_test.ifc";
            DSLExporter exporter = new DSLExporter(IFCExportConfig.residential());
            boolean success = exporter.exportStorey(spec, outputPath);

            if (!success) {
                throw new AssertionError("IFC export failed");
            }

            // Verify file exists and has content
            Path ifcFile = Path.of(outputPath);
            if (!Files.exists(ifcFile)) {
                throw new AssertionError("IFC file not created");
            }
            long fileSize = Files.size(ifcFile);
            System.out.println("  File size: " + fileSize + " bytes");

            if (fileSize < 1000) {
                throw new AssertionError("IFC file too small: " + fileSize);
            }

            // Count IFC elements using grep
            int wallCount = countIfcEntities(outputPath, "IFCWALL");
            int openingCount = countIfcEntities(outputPath, "IFCOPENINGELEMENT");
            int spaceCount = countIfcEntities(outputPath, "IFCSPACE");
            int sprinklerCount = countIfcEntities(outputPath, "IFCFIRESUPPRESSIONTERMINAL");

            System.out.println("  IFC element counts:");
            System.out.println("    Walls: " + wallCount);
            System.out.println("    Openings: " + openingCount);
            System.out.println("    Spaces: " + spaceCount);
            System.out.println("    Sprinklers: " + sprinklerCount);

            // Verify counts
            if (wallCount < 4) {
                throw new AssertionError("Expected at least 4 walls, got " + wallCount);
            }
            if (spaceCount != 2) {
                throw new AssertionError("Expected 2 spaces, got " + spaceCount);
            }
            if (sprinklerCount == 0) {
                throw new AssertionError("Expected sprinklers, got 0");
            }

            passed++;
            System.out.println("  [PASS]");

        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Larger room with multiple sprinklers
        System.out.println("\nTest 2: Multi-sprinkler grid (10x10m room)");
        try {
            String dsl = """
                STOREY "Ground" height:3.0m {
                    LIVING "great_room" at:A1 size:10x10m {
                        SPRINKLERS grid:4.6m
                    }
                }
                """;

            // Parse and compile
            StoreyDefinition storey = DSLParser.parse(dsl);
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);

            // Export
            String outputPath = OUTPUT_DIR + "/unified_large_room.ifc";
            DSLExporter exporter = new DSLExporter(IFCExportConfig.residential());
            boolean success = exporter.exportStorey(spec, outputPath);

            if (!success) {
                throw new AssertionError("IFC export failed");
            }

            // Count sprinklers
            int sprinklerCount = countIfcEntities(outputPath, "IFCFIRESUPPRESSIONTERMINAL");

            // 10x10m room with 4.6m spacing
            // Grid positions: (2.3, 2.3), (6.9, 2.3), (2.3, 6.9), (6.9, 6.9)
            // Expected: 2x2 = 4 sprinklers (or 3x3=9 if edge cases)
            System.out.println("  Room: 10x10m with 4.6m grid");
            System.out.println("  Sprinklers: " + sprinklerCount);

            // Allow either 4 or 9 depending on edge handling
            if (sprinklerCount < 4) {
                throw new AssertionError("Expected at least 4 sprinklers, got " + sprinklerCount);
            }

            passed++;
            System.out.println("  [PASS]");

        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Room without sprinklers (should still work)
        System.out.println("\nTest 3: Room without sprinklers");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    BEDROOM "small" at:A1 size:3x3m {
                        DOOR south to:hall
                    }
                }
                """;

            StoreyDefinition storey = DSLParser.parse(dsl);
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);

            String outputPath = OUTPUT_DIR + "/unified_no_sprinklers.ifc";
            DSLExporter exporter = new DSLExporter(IFCExportConfig.residential());
            boolean success = exporter.exportStorey(spec, outputPath);

            if (!success) {
                throw new AssertionError("IFC export failed");
            }

            int wallCount = countIfcEntities(outputPath, "IFCWALL");
            int sprinklerCount = countIfcEntities(outputPath, "IFCFIRESUPPRESSIONTERMINAL");

            System.out.println("  Walls: " + wallCount);
            System.out.println("  Sprinklers: " + sprinklerCount);

            if (wallCount < 4) {
                throw new AssertionError("Expected at least 4 walls, got " + wallCount);
            }
            if (sprinklerCount != 0) {
                throw new AssertionError("Expected 0 sprinklers, got " + sprinklerCount);
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
            System.out.println("SUCCESS: Unified IFC export working!");
            System.out.println("\nOutput files:");
            System.out.println("  " + OUTPUT_DIR + "/unified_test.ifc");
            System.out.println("  " + OUTPUT_DIR + "/unified_large_room.ifc");
            System.out.println("  " + OUTPUT_DIR + "/unified_no_sprinklers.ifc");
        }
    }

    /**
     * Count IFC entities in file using grep.
     */
    private static int countIfcEntities(String ifcPath, String entityType) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "grep", "-c", entityType + "(", ifcPath
            );
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            String line = reader.readLine();
            process.waitFor();
            return line != null ? Integer.parseInt(line.trim()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
