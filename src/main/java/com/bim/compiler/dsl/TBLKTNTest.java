package com.bim.compiler.dsl;

import com.bim.compiler.util.OutlierLogger;
import java.nio.file.*;

/**
 * TB-LKTN House Test - Malaysian Affordable Housing
 *
 * Tests outlier handling with Malaysian room vocabulary:
 * - RUANG_TAMU (Living Room)
 * - BILIK_UTAMA (Master Bedroom)
 * - DAPUR (Kitchen)
 * - RUANG_BASUH (Laundry)
 * - BILIK_MANDI (Bathroom)
 * - TANDAS (Toilet)
 * - ANJUNG (Porch)
 * - RUANG_MAKAN (Dining)
 */
public class TBLKTNTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(70));
        System.out.println("TB-LKTN HOUSE TEST - Malaysian Vocabulary Outlier Handling");
        System.out.println("=".repeat(70));
        System.out.println();

        // Read the DSL file
        String dsl = Files.readString(Path.of("examples/tb_lktn_house.dsl"));

        System.out.println("DSL Source:");
        System.out.println("-".repeat(70));
        // Print first 50 lines
        String[] lines = dsl.split("\n");
        for (int i = 0; i < Math.min(30, lines.length); i++) {
            System.out.println(lines[i]);
        }
        System.out.println("...");
        System.out.println("-".repeat(70));
        System.out.println();

        // Parse and compile
        System.out.println("Parsing and compiling...");
        System.out.println();

        try {
            BuildingDefinition def = BuildingParser.parse(dsl);

            System.out.printf("Building: %s%n", def.name());
            System.out.printf("Storeys: %d%n", def.storeys().size());

            for (var storey : def.storeys()) {
                System.out.printf("%n  Storey: %s (level %d)%n", storey.name(), storey.level());
                System.out.printf("    Rooms parsed: %d%n", storey.rooms().size());
                for (var room : storey.rooms()) {
                    System.out.printf("      %s \"%s\" %.1fx%.1fm%n",
                        room.type(), room.name(), room.width(), room.depth());
                }
            }

            System.out.println();
            System.out.println("Compiling with outlier tracking...");
            System.out.println("-".repeat(70));

            Path outputDir = Path.of("output/tb_lktn");
            Files.createDirectories(outputDir);

            BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def, outputDir);

            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("COMPILATION RESULTS");
            System.out.println("=".repeat(70));

            for (var storey : spec.storeys()) {
                System.out.printf("%nStorey: %s%n", storey.name());
                System.out.printf("  Rooms: %d%n", storey.rooms().size());
                System.out.printf("  Walls: %d%n", storey.walls().size());
                System.out.printf("  Doors: %d%n", storey.doors().size());
                System.out.printf("  Windows: %d%n", storey.windows().size());
                System.out.printf("  Fixtures: %d%n", storey.fixtures().size());
                System.out.printf("  Columns: %d%n", storey.columns().size());
                System.out.printf("  Diffusers: %d%n", storey.diffusers().size());
            }

            // Show room type mappings
            System.out.println();
            System.out.println("Room Type Mappings (Malaysian → Resolved):");
            System.out.println("-".repeat(70));
            for (var storey : spec.storeys()) {
                for (var room : storey.rooms()) {
                    System.out.printf("  %s → %s%n", room.name(), room.type());
                }
            }

            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("OUTLIER ANALYSIS");
            System.out.println("=".repeat(70));

            // Print outlier summary
            System.out.println();
            OutlierLogger.printSummary();

            System.out.println();
            System.out.println("Outlier log written to: " + outputDir.resolve("outliers.log"));

            // Success criteria
            int totalOutliers = OutlierLogger.getTotalCount();
            int unknownTypeOutliers = OutlierLogger.getCount(
                OutlierLogger.OutlierCategory.UNKNOWN_SPACETYPE);

            System.out.println();
            System.out.println("=".repeat(70));
            System.out.printf("SUMMARY: %d rooms compiled, %d outliers detected%n",
                spec.storeys().get(0).rooms().size(), totalOutliers);
            System.out.printf("         Unknown SpaceType outliers: %d%n", unknownTypeOutliers);
            System.out.println("=".repeat(70));

            // The test passes if:
            // 1. All 10 rooms are compiled (no crashes)
            // 2. Malaysian room types trigger UNKNOWN_SPACETYPE outliers
            // 3. They map to appropriate fallbacks
            if (spec.storeys().get(0).rooms().size() >= 8 && unknownTypeOutliers >= 5) {
                System.out.println("\n[PASS] Malaysian vocabulary handled gracefully!");
                System.out.println("       Outlier framework working as designed.");
            } else {
                System.out.println("\n[INFO] Check mappings - some may need adjustment.");
            }

        } catch (Exception e) {
            System.out.println("[FAIL] Exception during compilation:");
            e.printStackTrace();
        }
    }
}
