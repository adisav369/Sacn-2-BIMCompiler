package com.bim.compiler.dsl;
import com.bim.compiler.dsl.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class StructuralIntegrationTest {
    public static void main(String[] args) throws Exception {
        String dsl = Files.readString(Path.of("examples/apartment_test.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingCompiler.BuildingSpec spec = BuildingCompiler.compile(def);

        System.out.println("=".repeat(60));
        System.out.println("PHASE 23+24: STRUCTURAL & HVAC INTEGRATION TEST");
        System.out.println("=".repeat(60));

        int totalColumns = 0;
        int totalBeams = 0;
        int totalDiffusers = 0;

        for (var storey : spec.storeys()) {
            System.out.printf("%nStorey: %s (level %d)%n", storey.name(), storey.level());
            System.out.printf("  Rooms: %d%n", storey.rooms().size());
            System.out.printf("  Doors: %d%n", storey.doors().size());
            System.out.printf("  Windows: %d%n", storey.windows().size());
            System.out.printf("  Fixtures: %d%n", storey.fixtures().size());
            System.out.printf("  Columns: %d%n", storey.columns().size());
            System.out.printf("  Beams/Lintels: %d%n", storey.beams().size());
            System.out.printf("  Diffusers: %d%n", storey.diffusers().size());

            System.out.println("\n  Columns:");
            for (var column : storey.columns()) {
                System.out.printf("    - %s (%s) at (%.2f, %.2f) h=%.2fm%n",
                    column.id(), column.columnType(),
                    column.x(), column.y(), column.height());
            }

            System.out.println("\n  Beams/Lintels:");
            for (var beam : storey.beams()) {
                System.out.printf("    - %s (%s) len=%.2fm%n",
                    beam.id(), beam.beamType(), beam.length());
            }

            System.out.println("\n  Diffusers:");
            int supplyCount = 0, returnCount = 0, exhaustCount = 0;
            int totalCFM = 0;
            for (var d : storey.diffusers()) {
                totalCFM += d.cfmRating();
                switch (d.diffuserType()) {
                    case "supply" -> supplyCount++;
                    case "return" -> returnCount++;
                    case "exhaust" -> exhaustCount++;
                }
            }
            System.out.printf("    Supply: %d, Return: %d, Exhaust: %d%n",
                supplyCount, returnCount, exhaustCount);
            System.out.printf("    Total CFM: %d%n", totalCFM);

            totalColumns += storey.columns().size();
            totalBeams += storey.beams().size();
            totalDiffusers += storey.diffusers().size();
        }

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.printf("SUMMARY:%n");
        System.out.printf("  Columns: %d (4 corner minimum)%n", totalColumns);
        System.out.printf("  Beams/Lintels: %d%n", totalBeams);
        System.out.printf("  Diffusers: %d%n", totalDiffusers);

        boolean structPass = totalColumns >= 4;
        boolean hvacPass = totalDiffusers >= 1;
        boolean pass = structPass && hvacPass;

        System.out.printf("%nRESULT: %s%n", pass ? "PASS" : "FAIL");
        if (!structPass) System.out.println("  [FAIL] Need at least 4 columns");
        if (!hvacPass) System.out.println("  [FAIL] Need at least 1 diffuser");
        System.out.println("=".repeat(60));

        if (!pass) System.exit(1);
    }
}
