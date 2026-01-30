package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;
import java.nio.file.*;

/**
 * Phase 26 Test - Construction-Aware DSL
 *
 * Tests:
 * 1. GRID parsing (axes, spacing)
 * 2. ENVELOPE/DRAINAGE parsing
 * 3. Grid bounds room placement (bounds:A2-B4)
 * 4. PORCH roof semantics (ATTACHED/SEPARATE)
 * 5. ROOF overhang
 */
public class Phase26Test {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("PHASE 26 TEST - Construction-Aware DSL");
        System.out.println("=".repeat(70));
        System.out.println();

        // Read the grid-based DSL file
        String dsl = Files.readString(Path.of("examples/tb_lktn_grid.dsl"));

        System.out.println("Parsing grid-based DSL...");
        System.out.println();

        BuildingDefinition def = BuildingParser.parse(dsl);

        // 1. Test GRID parsing
        System.out.println("1. GRID PARSING");
        System.out.println("-".repeat(70));
        if (def.grid() != null) {
            GridDef grid = def.grid();
            System.out.printf("  X Axes: %s%n", grid.xAxes());
            System.out.printf("  Y Axes: %s%n", grid.yAxes());
            System.out.printf("  X Spacing: %s%n", grid.xSpacing());
            System.out.printf("  Y Spacing: %s%n", grid.ySpacing());
            System.out.printf("  From PDF: %s%n", grid.fromPdf());

            // Calculate grid positions
            System.out.println("\n  Grid positions:");
            for (String x : grid.xAxes()) {
                System.out.printf("    %s -> X=%.2fm%n", x, grid.getX(x));
            }
            for (String y : grid.yAxes()) {
                System.out.printf("    %s -> Y=%.2fm%n", y, grid.getY(y));
            }

            // Column placement points
            System.out.println("\n  Grid intersections (column points):");
            var intersections = grid.getIntersections();
            System.out.printf("    Total: %d points%n", intersections.size());
            for (double[] pt : intersections.subList(0, Math.min(5, intersections.size()))) {
                System.out.printf("    (%.2f, %.2f)%n", pt[0], pt[1]);
            }
            System.out.println("    ...");

            System.out.println("\n  [PASS] GRID parsing works");
        } else {
            System.out.println("  [FAIL] No GRID found");
        }

        // 2. Test ENVELOPE/DRAINAGE parsing
        System.out.println("\n2. ENVELOPE/DRAINAGE PARSING");
        System.out.println("-".repeat(70));
        if (def.envelope() != null && def.envelope().drainage() != null) {
            DrainageDef drain = def.envelope().drainage();
            System.out.printf("  Perimeter drain offset: %.0fmm (%.2fm)%n",
                drain.perimeterDrainOffsetMm(), drain.perimeterDrainOffsetMeters());
            System.out.printf("  Connects to: %s%n", drain.connectsTo());
            System.out.printf("  Downpipe locations: %s%n", drain.downpipeLocations());
            System.out.println("\n  [PASS] ENVELOPE/DRAINAGE parsing works");
        } else {
            System.out.println("  [FAIL] No ENVELOPE/DRAINAGE found");
        }

        // 3. Test ROOF with overhang
        System.out.println("\n3. ROOF WITH OVERHANG");
        System.out.println("-".repeat(70));
        if (def.roof() != null) {
            System.out.printf("  Pitch: %.0f deg%n", def.roof().pitchDegrees());
            System.out.printf("  Overhang: %.0fmm (%.2fm)%n",
                def.roof().overhangMm(), def.roof().overhangMeters());

            // Check correlation: overhang should match drain offset
            if (def.envelope() != null && def.envelope().drainage() != null) {
                double roofOverhang = def.roof().overhangMm();
                double drainOffset = def.envelope().drainage().perimeterDrainOffsetMm();
                if (roofOverhang == drainOffset) {
                    System.out.println("\n  [PASS] Roof overhang = Drain offset (construction correlation)");
                } else {
                    System.out.printf("\n  [INFO] Overhang %.0f != Drain offset %.0f%n",
                        roofOverhang, drainOffset);
                }
            }
            System.out.println("\n  [PASS] ROOF overhang parsing works");
        } else {
            System.out.println("  [FAIL] No ROOF found");
        }

        // 4. Test room parsing with grid bounds
        System.out.println("\n4. ROOM PARSING WITH GRID BOUNDS");
        System.out.println("-".repeat(70));
        var storey = def.storeys().get(0);
        System.out.printf("  Storey: %s, Rooms: %d%n%n", storey.name(), storey.rooms().size());

        int boundsCount = 0;
        int porchCount = 0;

        for (RoomDef room : storey.rooms()) {
            System.out.printf("  %s \"%s\"%n", room.type(), room.name());
            if (room.hasGridBounds()) {
                System.out.printf("    bounds: %s%n", room.gridBounds());
                GridBounds gb = room.getParsedGridBounds();
                if (gb != null) {
                    System.out.printf("    parsed: %s%s → %s%s%n",
                        gb.startX(), gb.startY(), gb.endX(), gb.endY());
                    if (def.grid() != null) {
                        double[] dims = gb.getDimensions(def.grid());
                        System.out.printf("    calculated size: %.2fm x %.2fm%n", dims[0], dims[1]);
                    }
                }
                boundsCount++;
            } else if (room.width() > 0) {
                System.out.printf("    explicit size: %.1fm x %.1fm%n", room.width(), room.depth());
            }
            if (room.porchRoofType() != null) {
                System.out.printf("    porch roof: %s%n", room.porchRoofType());
                porchCount++;
            }
            System.out.println();
        }

        System.out.printf("  Rooms with grid bounds: %d%n", boundsCount);
        System.out.printf("  Rooms with porch roof type: %d%n", porchCount);

        if (boundsCount > 0) {
            System.out.println("\n  [PASS] Grid bounds parsing works");
        } else {
            System.out.println("\n  [INFO] No grid bounds found in rooms");
        }

        // Summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("PHASE 26 TEST SUMMARY");
        System.out.println("=".repeat(70));

        int passed = 0;
        int total = 4;

        if (def.grid() != null) passed++;
        if (def.envelope() != null) passed++;
        if (def.roof() != null && def.roof().overhangMm() > 0) passed++;
        if (boundsCount > 0) passed++;

        System.out.printf("Tests passed: %d/%d%n", passed, total);

        if (passed == total) {
            System.out.println("\n[SUCCESS] Phase 26 Construction-Aware DSL is working!");
        } else {
            System.out.println("\n[PARTIAL] Some features need attention.");
        }
    }
}
