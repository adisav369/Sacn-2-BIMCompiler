package com.bim.compiler.dsl;

import com.bim.compiler.factory.GridPlacementSpec;
import com.bim.compiler.factory.HybridFactory;
import com.bim.compiler.geometry.BoundingBox;
import com.bim.compiler.library.ComponentLibrary;
import com.bim.compiler.model.ISpatialElement;
import com.bim.compiler.topology.BIMObjectType;
import com.bim.compiler.topology.Discipline;
import com.bim.compiler.util.BIMLogger;

import java.util.List;
import java.util.Map;

/**
 * Test DSL → Library integration for sprinklers.
 *
 * Validates:
 *   1. DSL parsing recognizes SPRINKLERS keyword
 *   2. RoomCompiler generates SprinklerGridSpec
 *   3. HybridFactory creates sprinkler elements
 *   4. Grid spacing matches NFPA 13 (4.6m)
 */
public class DSLSprinklerTest {

    private static final String LIBRARY_PATH = "library/component_library.db";

    public static void main(String[] args) throws Exception {
        BIMLogger.setLevel(BIMLogger.Level.DEBUG);

        System.out.println("=".repeat(70));
        System.out.println("DSL → LIBRARY INTEGRATION TEST (Sprinklers)");
        System.out.println("=".repeat(70));

        int passed = 0;
        int failed = 0;

        // Test 1: Parse DSL with SPRINKLERS
        System.out.println("\nTest 1: Parse DSL with SPRINKLERS keyword");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    BEDROOM "master" at:A1 size:5x4m {
                        DOOR south to:corridor
                        SPRINKLERS grid:4.6m
                    }
                    CORRIDOR "corridor" at:A2-B2 width:1.2m {
                        SPRINKLERS
                    }
                }
                """;

            StoreyDefinition storey = DSLParser.parse(dsl);

            // Verify rooms parsed
            if (storey.rooms().size() != 2) {
                throw new AssertionError("Expected 2 rooms, got " + storey.rooms().size());
            }

            // Verify sprinklers parsed
            RoomDefinition master = storey.rooms().get(0);
            if (master.sprinklers() == null) {
                throw new AssertionError("master room should have sprinklers");
            }
            if (Math.abs(master.sprinklers().gridSpacing() - 4.6) > 0.001) {
                throw new AssertionError("Expected 4.6m spacing, got " + master.sprinklers().gridSpacing());
            }

            // Verify default spacing for corridor (no explicit grid:)
            RoomDefinition corridor = storey.rooms().get(1);
            if (corridor.sprinklers() == null) {
                throw new AssertionError("corridor should have sprinklers");
            }
            if (Math.abs(corridor.sprinklers().gridSpacing() - 4.6) > 0.001) {
                throw new AssertionError("Expected default 4.6m spacing, got " + corridor.sprinklers().gridSpacing());
            }

            System.out.println("  Parsed 2 rooms with sprinklers");
            System.out.println("  master: " + master.sprinklers().gridSpacing() + "m spacing");
            System.out.println("  corridor: " + corridor.sprinklers().gridSpacing() + "m spacing (default)");
            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Compile to ConstructionSpec with SprinklerGridSpec
        System.out.println("\nTest 2: Compile to ConstructionSpec with sprinkler grids");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    BEDROOM "master" at:A1 size:5x4m {
                        SPRINKLERS grid:4.6m
                    }
                }
                """;

            StoreyDefinition storey = DSLParser.parse(dsl);
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);

            // Verify sprinkler grid generated
            if (spec.sprinklerGrids().isEmpty()) {
                throw new AssertionError("No sprinkler grids generated");
            }

            ConstructionSpec.SprinklerGridSpec grid = spec.sprinklerGrids().get(0);
            System.out.println("  Room: " + grid.roomName());
            System.out.println("  Area: " + grid.area());
            System.out.println("  Spacing: " + grid.spacing() + "m");
            System.out.println("  Attachment Z: " + grid.attachmentZ() + "m (ceiling)");

            // Verify attachment Z = storey height
            if (Math.abs(grid.attachmentZ() - 2.8) > 0.001) {
                throw new AssertionError("Attachment Z should be 2.8m, got " + grid.attachmentZ());
            }

            passed++;
            System.out.println("  [PASS]");
        } catch (Exception e) {
            System.out.println("  [FAIL] " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: Full pipeline - DSL → Library → Elements
        System.out.println("\nTest 3: Full pipeline DSL → Library → Sprinkler Elements");
        try {
            String dsl = """
                STOREY "Ground" height:2.8m {
                    LIVING "lounge" at:A1 size:6x5m {
                        SPRINKLERS grid:4.6m
                    }
                }
                """;

            // Parse and compile
            StoreyDefinition storey = DSLParser.parse(dsl);
            Map<String, RoomPolygon> layout = GridLayoutResolver.resolve(storey);
            ConstructionSpec spec = RoomCompiler.compile(layout, storey);

            // Create factory
            ComponentLibrary library = new ComponentLibrary(LIBRARY_PATH);
            HybridFactory factory = new HybridFactory(library);

            // Convert SprinklerGridSpec to GridPlacementSpec and create elements
            ConstructionSpec.SprinklerGridSpec gridSpec = spec.sprinklerGrids().get(0);
            GridPlacementSpec placementSpec = GridPlacementSpec.gridBuilder()
                .id("SPRINKLER_" + gridSpec.roomName())
                .type(BIMObjectType.IFC_FIRE_SUPPRESSION_TERMINAL)
                .discipline(Discipline.FP)
                .libraryComponentId("pendent")
                .area(gridSpec.area())
                .spacing(gridSpec.spacing())
                .attachmentZ(gridSpec.attachmentZ())
                .attachmentFace("TOP")
                .build();

            List<ISpatialElement> elements = factory.createGrid(placementSpec);

            System.out.println("  Created " + elements.size() + " sprinklers");

            // Room 6x5m = 30m² with 4.6m spacing
            // Expected: 2x2 = 4 sprinklers (offset from walls)
            if (elements.isEmpty()) {
                throw new AssertionError("No sprinkler elements created");
            }

            // Print first few positions
            for (int i = 0; i < Math.min(4, elements.size()); i++) {
                ISpatialElement e = elements.get(i);
                System.out.printf("    %s at (%.2f, %.2f, %.2f)%n",
                    e.getGuid(), e.getPosition().x(), e.getPosition().y(), e.getPosition().z());
            }

            library.close();
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
            System.out.println("ALL TESTS PASSED - DSL → Library integration working!");
        }
    }
}
