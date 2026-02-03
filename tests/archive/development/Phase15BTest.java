package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingCompiler.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phase 15B Verification Test
 *
 * Tests:
 * 1. Interior walls between adjacent rooms (shared edges)
 * 2. Auto-door placement for ADJACENT constraints
 * 3. Auto-window placement for EXTERIOR constraints
 *
 * Expected: 6+ walls, 2+ doors, 2+ windows, walkable building
 */
public class Phase15BTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("PHASE 15B: INTERIOR WALLS + AUTO-DOORS TEST");
        System.out.println("=".repeat(60));

        // Parse apartment test
        String dsl = Files.readString(Path.of("examples/apartment_test.bim"));
        System.out.println("\nDSL:");
        System.out.println(dsl);

        System.out.println("\n" + "-".repeat(60));
        System.out.println("PARSING...");

        BuildingDefinition def = BuildingParser.parse(dsl);
        System.out.println("Parsed " + def.storeys().get(0).rooms().size() + " rooms");

        // Print constraints
        System.out.println("\nConstraints:");
        for (var room : def.storeys().get(0).rooms()) {
            System.out.printf("  %s: adj=%s notAdj=%s ext=%s%n",
                room.name(),
                room.adjacentTo(),
                room.notAdjacentTo(),
                room.exteriorWall());
        }

        System.out.println("\n" + "-".repeat(60));
        System.out.println("COMPILING (includes solver + wall generation)...");

        BuildingSpec spec = BuildingCompiler.compile(def);
        StoreySpec ground = spec.storeys().get(0);

        System.out.println("\n" + "-".repeat(60));
        System.out.println("RESULTS:");
        System.out.println("-".repeat(60));

        // Count results
        int wallCount = ground.walls().size();
        int doorCount = ground.doors().size();
        int windowCount = ground.windows().size();

        System.out.printf("Walls:   %d (expected: >=6, got %d perimeter + %d interior)%n",
            wallCount, 4, wallCount - 4);
        System.out.printf("Doors:   %d (expected: >=2 auto-doors for adjacent rooms)%n", doorCount);
        System.out.printf("Windows: %d (expected: >=2 auto-windows for exterior rooms)%n", windowCount);

        // List walls
        System.out.println("\nWall Details:");
        for (var wall : ground.walls()) {
            System.out.printf("  %s: %.1fm x %.1fm%n",
                wall.assemblyName(), wall.length(), wall.height());
        }

        // List doors
        System.out.println("\nDoor Details:");
        for (var door : ground.doors()) {
            System.out.printf("  %s: room=%s wall=%s pos=(%.1f,%.1f)%n",
                door.name(), door.roomName(), door.wall(), door.x(), door.y());
        }

        // List windows
        System.out.println("\nWindow Details:");
        for (var window : ground.windows()) {
            System.out.printf("  %s: room=%s wall=%s pos=(%.1f,%.1f)%n",
                window.name(), window.roomName(), window.wall(), window.x(), window.y());
        }

        // List rooms with positions
        System.out.println("\nRoom Positions:");
        for (var room : ground.rooms()) {
            System.out.printf("  %s: (%.1f,%.1f) to (%.1f,%.1f)%n",
                room.name(), room.minX(), room.minY(), room.maxX(), room.maxY());
        }

        // Verification checks
        System.out.println("\n" + "=".repeat(60));
        System.out.println("VERIFICATION:");
        System.out.println("=".repeat(60));

        boolean pass = true;

        // Check 1: Walls >= 6 (4 perimeter + at least 2 interior)
        if (wallCount >= 6) {
            System.out.println("[PASS] Walls: " + wallCount + " >= 6 (includes interior walls)");
        } else {
            System.out.println("[FAIL] Walls: " + wallCount + " < 6 (missing interior walls)");
            pass = false;
        }

        // Check 2: Doors >= 2 (adjacent rooms should have doors)
        if (doorCount >= 2) {
            System.out.println("[PASS] Doors: " + doorCount + " >= 2 (auto-placed for adjacent rooms)");
        } else {
            System.out.println("[FAIL] Doors: " + doorCount + " < 2 (missing auto-doors)");
            pass = false;
        }

        // Check 3: Windows >= 2 (exterior rooms should have windows)
        if (windowCount >= 2) {
            System.out.println("[PASS] Windows: " + windowCount + " >= 2 (auto-placed for exterior rooms)");
        } else {
            System.out.println("[FAIL] Windows: " + windowCount + " < 2 (missing auto-windows)");
            pass = false;
        }

        // Check 4: Interior walls exist
        long interiorWalls = ground.walls().stream()
            .filter(w -> w.assemblyName().startsWith("INTERIOR_"))
            .count();
        if (interiorWalls > 0) {
            System.out.println("[PASS] Interior walls: " + interiorWalls + " (rooms have physical separation)");
        } else {
            System.out.println("[FAIL] Interior walls: 0 (no room separation)");
            pass = false;
        }

        // Check 5: Auto-doors have correct naming
        long autoDoors = ground.doors().stream()
            .filter(d -> d.name().contains("_to_") && d.name().endsWith("_door"))
            .count();
        if (autoDoors > 0) {
            System.out.println("[PASS] Auto-doors: " + autoDoors + " (correctly named for adjacency)");
        } else {
            System.out.println("[INFO] Auto-doors: 0 (may have manual doors only)");
        }

        // Check 6: Auto-windows have correct naming
        long autoWindows = ground.windows().stream()
            .filter(w -> w.name().contains("_auto_window_"))
            .count();
        if (autoWindows > 0) {
            System.out.println("[PASS] Auto-windows: " + autoWindows + " (correctly named for exterior)");
        } else {
            System.out.println("[INFO] Auto-windows: 0 (may have manual windows only)");
        }

        System.out.println("\n" + "=".repeat(60));
        if (pass) {
            System.out.println("OVERALL: PASS - Interior walls and auto-openings working!");
        } else {
            System.out.println("OVERALL: FAIL - Some checks failed");
        }
        System.out.println("=".repeat(60));
    }
}
