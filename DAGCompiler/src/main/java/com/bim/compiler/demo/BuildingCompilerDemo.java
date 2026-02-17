package com.bim.compiler.demo;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingCompiler;
import com.bim.compiler.dsl.BuildingSpecs.*;
import com.bim.compiler.dsl.BuildingDefinition;
import com.bim.compiler.dsl.BuildingParser;

/**
 * Demo/test runner for BuildingCompiler.
 *
 * This is a standalone demo - NOT the CLI entry point.
 * For actual DSL file compilation, use: BuildingCompilerCLI
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.bim.compiler.demo.BuildingCompilerDemo"
 */
public class BuildingCompilerDemo {

    private static final double MAX_RISER = BIMConstants.IRC_MAX_RISER_HEIGHT;
    private static final double MIN_TREAD = BIMConstants.IRC_MIN_TREAD_DEPTH;

    public static void main(String[] args) {
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

        BuildingDefinition def = BuildingParser.parse(dsl);
        BuildingSpec spec = BuildingCompiler.compile(def);

        System.out.println("=".repeat(60));
        System.out.println("BUILDING COMPILATION DEMO");
        System.out.println("=".repeat(60));

        System.out.println("\nBuilding: " + spec.name());
        System.out.println("Storeys: " + spec.storeys().size());

        for (StoreySpec storey : spec.storeys()) {
            System.out.printf("\n  Storey: %s (level %d)%n", storey.name(), storey.level());
            System.out.printf("    Base Z: %.3fm, Height: %.3fm%n", storey.baseZ(), storey.height());

            System.out.printf("    Slab: %s [%.2f,%.2f] to [%.2f,%.2f] Z[%.2f-%.2f]%n",
                storey.slab().name(),
                storey.slab().minX(), storey.slab().minY(),
                storey.slab().maxX(), storey.slab().maxY(),
                storey.slab().minZ(), storey.slab().maxZ());

            System.out.printf("    Walls: %d assemblies%n", storey.walls().size());
            for (WallAssemblySpec wall : storey.walls()) {
                System.out.printf("      %s: %.2f x %.2f x %.2fm%n",
                    wall.assemblyName(), wall.length(), wall.thickness(), wall.height());
            }

            System.out.printf("    Rooms: %d%n", storey.rooms().size());
            System.out.printf("    Stairs: %d%n", storey.stairs().size());

            for (StairSpec stair : storey.stairs()) {
                System.out.printf("      %s: %d risers @ %.0fmm, treads %.0fmm%n",
                    stair.name(), stair.numRisers(),
                    stair.riserHeight() * 1000, stair.treadDepth() * 1000);
                System.out.printf("        IRC Check: Riser %.0fmm <= 196mm? %s%n",
                    stair.riserHeight() * 1000,
                    stair.riserHeight() <= MAX_RISER ? "PASS" : "FAIL");
                System.out.printf("        IRC Check: Tread %.0fmm >= 254mm? %s%n",
                    stair.treadDepth() * 1000,
                    stair.treadDepth() >= MIN_TREAD ? "PASS" : "FAIL");
            }
        }

        if (spec.roof() != null) {
            System.out.printf("\nRoof: %s, %.0f deg pitch%n",
                spec.roof().type(), spec.roof().pitchDegrees());
            System.out.printf("  Ridge rise: %.3fm%n", spec.roof().ridgeRise());
        }

        // Verify Z-continuity
        System.out.println("\n" + "-".repeat(60));
        System.out.println("Z-CONTINUITY CHECK:");
        for (int i = 0; i < spec.storeys().size() - 1; i++) {
            StoreySpec lower = spec.storeys().get(i);
            StoreySpec upper = spec.storeys().get(i + 1);

            double lowerTop = lower.baseZ() + lower.height();
            double upperBase = upper.baseZ();
            double gap = Math.abs(upperBase - lowerTop);

            System.out.printf("  %s top (%.3fm) -> %s base (%.3fm): gap %.3fm %s%n",
                lower.name(), lowerTop,
                upper.name(), upperBase,
                gap, gap < 0.001 ? "✓" : "✗");
        }

        System.out.println("\n[PASS] Demo complete");
    }
}
