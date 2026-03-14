package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug test to check shared definition state.
 */
public class StackedDuplexDebugTest {
    public static void main(String[] args) throws Exception {
        String dsl = Files.readString(Path.of("examples/Stacked-Duplex.bim"));
        BuildingDefinition def = BuildingParser.parse(dsl);

        System.out.println("Building: " + def.name());
        System.out.println("Units: " + def.units().size());
        System.out.println("Shared: " + (def.shared() != null ? "present" : "null"));

        if (def.shared() != null) {
            System.out.println("  isEmpty: " + def.shared().isEmpty());
            System.out.println("  storeys: " + def.shared().storeys().size());
            for (var storey : def.shared().storeys()) {
                System.out.println("    storey: " + storey.name() + " level:" + storey.level());
                System.out.println("    rooms: " + storey.rooms().size());
                for (var room : storey.rooms()) {
                    System.out.println("      - " + room.name());
                }
                System.out.println("    stairs: " + storey.stairs().size());
                for (var stair : storey.stairs()) {
                    System.out.println("      - " + stair.name() + " to:" + stair.toStorey());
                }
                System.out.println("    landings: " + storey.landings().size());
                for (var landing : storey.landings()) {
                    System.out.println("      - " + landing.name() + " from:" + landing.fromStair());
                }
            }
        }
    }
}
