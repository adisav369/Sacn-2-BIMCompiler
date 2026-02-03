package com.bim.compiler.dsl;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test parsing CONDO-MID.bim with new vertical circulation elements.
 * Phase 56: Validates ELEVATOR, ELEVATOR_LOBBY, SHAFT parsing.
 */
public class CondoMidParseTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== CONDO-MID Parse Test ===\n");

        // Read DSL file
        String dsl = Files.readString(Path.of("examples/CONDO-MID.bim"));
        System.out.println("Read " + dsl.length() + " chars from CONDO-MID.bim\n");

        // Parse
        BuildingDefinition building = BuildingParser.parse(dsl);

        System.out.println("Building: " + building.name());
        System.out.println("Storeys: " + building.storeys().size());
        System.out.println();

        // Check each storey
        for (var storey : building.storeys()) {
            System.out.println("STOREY " + storey.name() + " (level " + storey.level() + "):");
            System.out.println("  Rooms: " + storey.rooms().size());
            System.out.println("  Stairs: " + storey.stairs().size());
            System.out.println("  Landings: " + storey.landings().size());
            System.out.println("  Elevators: " + storey.elevators().size());
            System.out.println("  Lobbies: " + storey.lobbies().size());
            System.out.println("  Shafts: " + storey.shafts().size());

            // Detail elevators
            for (var elev : storey.elevators()) {
                System.out.println("    ELEVATOR " + elev.name() + " type:" + elev.type() +
                    " car:" + elev.carWidthMm() + "x" + elev.carDepthMm() +
                    " door:" + elev.doorWidthMm());
            }

            // Detail lobbies
            for (var lobby : storey.lobbies()) {
                System.out.println("    LOBBY " + lobby.name() +
                    " bounds:" + lobby.gridBounds() +
                    " pressurized:" + lobby.pressurized() +
                    " elevators:" + lobby.elevators().size());
            }

            // Detail shafts
            for (var shaft : storey.shafts()) {
                System.out.println("    SHAFT " + shaft.name() +
                    " type:" + shaft.type() +
                    " at:" + shaft.gridPosition() +
                    " size:" + shaft.widthM() + "x" + shaft.depthM() + "m");
            }

            System.out.println();
        }

        // Validate with AD
        System.out.println("=== Vertical Circulation AD Validation ===\n");
        String dbPath = "library/component_library.db";
        VerticalCirculationAD ad = new VerticalCirculationAD(dbPath);

        int storeys = building.storeys().size();
        double height = storeys * 3.0; // Approximate
        int occupantLoad = 360; // 4 units * 4 persons * 18 floors / 0.8

        System.out.println(ad.generateComplianceReport(
            storeys, height, occupantLoad,
            "R", "MALAYSIA", true
        ));

        System.out.println("\n=== TEST COMPLETE ===");
    }
}
