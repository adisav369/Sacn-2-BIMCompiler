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

        // Phase 56B: Check CORE
        var core = building.core();
        if (core != null) {
            System.out.println("\nCORE \"" + core.name() + "\" bounds:" + core.gridBounds());
            System.out.println("  Stairs: " + core.stairs().size());
            for (var stair : core.stairs()) {
                System.out.println("    STAIR " + stair.name() + " at:" + stair.gridPosition() +
                    " width:" + stair.width() + "m type:" + stair.toStorey());
            }
            System.out.println("  Lobbies: " + core.lobbies().size());
            for (var lobby : core.lobbies()) {
                System.out.println("    LOBBY " + lobby.name() + " bounds:" + lobby.gridBounds() +
                    " pressurized:" + lobby.pressurized() + " elevators:" + lobby.elevators().size());
                for (var elev : lobby.elevators()) {
                    System.out.println("      ELEVATOR " + elev.name() + " type:" + elev.type() +
                        " car:" + elev.carWidthMm() + "x" + elev.carDepthMm());
                }
            }
            System.out.println("  Shafts: " + core.shafts().size());
            for (var shaft : core.shafts()) {
                System.out.println("    SHAFT " + shaft.name() + " type:" + shaft.type() +
                    " at:" + shaft.gridPosition() + " size:" + shaft.widthM() + "x" + shaft.depthM() + "m");
            }
        } else {
            System.out.println("\nNo CORE block found");
        }
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
        String dbPath = "library/BOM.db";
        VerticalCirculationAD ad = new VerticalCirculationAD(dbPath);

        int storeys = building.storeys().size();
        double height = storeys * 3.0; // Approximate
        int occupantLoad = 360; // 4 units * 4 persons * 18 floors / 0.8

        System.out.println(ad.generateComplianceReport(
            storeys, height, occupantLoad,
            "R", "MALAYSIA", true
        ));

        // Phase 56B: Test compilation of CORE elements
        System.out.println("\n=== COMPILATION TEST ===\n");
        try {
            var spec = BuildingCompiler.compile(building);
            System.out.println("Compiled: " + spec.name());
            System.out.println("StoreySpecs: " + spec.storeys().size());

            // Verify CORE elements are in each storey
            int totalStairs = 0;
            int totalElevators = 0;
            int totalShafts = 0;
            int totalLobbies = 0;

            for (var storeySpec : spec.storeys()) {
                totalStairs += storeySpec.stairs().size();
                totalElevators += storeySpec.elevators().size();
                totalShafts += storeySpec.shafts().size();
                totalLobbies += storeySpec.lobbies().size();
            }

            System.out.println("\nCORE element totals across all storeys:");
            System.out.println("  Stairs: " + totalStairs + " (expect " + (building.storeys().size() * 2) + ")");
            System.out.println("  Elevators: " + totalElevators + " (expect " + (building.storeys().size() * 3) + ")");
            System.out.println("  Shafts: " + totalShafts + " (expect " + (building.storeys().size() * 2) + ")");
            System.out.println("  Lobbies: " + totalLobbies + " (expect " + building.storeys().size() + ")");

            // Sample one storey
            var sample = spec.storeys().get(5); // Typical floor
            System.out.println("\nSample storey (" + sample.name() + "):");
            System.out.println("  Stairs: " + sample.stairs().size());
            for (var stair : sample.stairs()) {
                System.out.println("    " + stair.name() + " at (" + stair.x() + "," + stair.y() + ")");
            }
            System.out.println("  Elevators: " + sample.elevators().size());
            for (var elev : sample.elevators()) {
                System.out.println("    " + elev.name() + " type:" + elev.type() + " at (" + elev.x() + "," + elev.y() + ")");
            }
            System.out.println("  Shafts: " + sample.shafts().size());
            for (var shaft : sample.shafts()) {
                System.out.println("    " + shaft.name() + " type:" + shaft.type());
            }

            System.out.println("\n[PASS] CONDO-MID compilation succeeded!");

        } catch (Exception e) {
            System.out.println("[FAIL] Compilation error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== TEST COMPLETE ===");
    }
}
