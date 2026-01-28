package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingDefinition.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for BUILDING DSL syntax.
 *
 * Uses brace-counting for nested blocks instead of complex regex.
 */
public class BuildingParser {

    // Simple patterns (no nested braces)
    private static final Pattern STAIR_PATTERN = Pattern.compile(
        "STAIR\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+width:([\\d.]+)m\\s+to:\"([^\"]+)\""
    );

    private static final Pattern LANDING_PATTERN = Pattern.compile(
        "LANDING\\s+\"([^\"]+)\"\\s+at:(\\w+)\\s+size:([\\d.]+)x([\\d.]+)m\\s+from:\"([^\"]+)\""
    );

    private static final Pattern DOOR_PATTERN = Pattern.compile(
        "DOOR\\s+(north|south|east|west)(?:\\s+to:(\\w+))?(?:\\s+size:(\\d+)x(\\d+))?"
    );

    private static final Pattern WINDOW_PATTERN = Pattern.compile(
        "WINDOW\\s+(north|south|east|west)(?:\\s+size:(\\d+)x(\\d+))?"
    );

    private static final Pattern ROOF_PATTERN = Pattern.compile(
        "ROOF\\s+pitch:(\\d+)deg"
    );

    private static final Pattern SPRINKLER_PATTERN = Pattern.compile(
        "SPRINKLERS\\s+grid:([\\d.]+)m"
    );

    private static final Pattern LIGHT_PATTERN = Pattern.compile(
        "LIGHTS\\s+grid:([\\d.]+)m(?:\\s+type:(\\w+))?"
    );

    // Layer 3: Constraint patterns
    // Note: ADJACENT must NOT match inside NOT_ADJACENT, so use negative lookbehind
    private static final Pattern ADJACENT_PATTERN = Pattern.compile(
        "(?<!not_)adjacent:\\s*(\\w+)"
    );

    private static final Pattern NOT_ADJACENT_PATTERN = Pattern.compile(
        "not_adjacent:\\s*(\\w+)"
    );

    private static final Pattern EXTERIOR_PATTERN = Pattern.compile(
        "exterior:\\s*(north|south|east|west)"
    );

    // Phase 16: Vertical constraint (cross-storey alignment)
    private static final Pattern ALIGNS_PATTERN = Pattern.compile(
        "aligns:\\s*(\\w+)"
    );

    // Phase 17: Extended vertical vocabulary
    private static final Pattern ABOVE_PATTERN = Pattern.compile(
        "above:\\s*(\\w+)"
    );

    private static final Pattern BELOW_PATTERN = Pattern.compile(
        "below:\\s*(\\w+)"
    );

    private static final Pattern STACK_PATTERN = Pattern.compile(
        "stack:\\s*(\\w+)"
    );

    /**
     * Parse BUILDING DSL input.
     */
    public static BuildingDefinition parse(String dsl) {
        // Find BUILDING block
        int buildingStart = dsl.indexOf("BUILDING");
        if (buildingStart < 0) {
            throw new IllegalArgumentException("No BUILDING found in DSL");
        }

        // Extract building name
        int nameStart = dsl.indexOf('"', buildingStart) + 1;
        int nameEnd = dsl.indexOf('"', nameStart);
        String buildingName = dsl.substring(nameStart, nameEnd);

        // Find building content between braces
        String buildingContent = extractBlock(dsl, nameEnd);

        // Parse storeys
        List<StoreyDef> storeys = new ArrayList<>();
        int pos = 0;
        while (true) {
            int storeyStart = buildingContent.indexOf("STOREY", pos);
            if (storeyStart < 0) break;

            StoreyDef storey = parseStorey(buildingContent, storeyStart);
            storeys.add(storey);

            // Move past this storey block
            int braceStart = buildingContent.indexOf('{', storeyStart);
            String storeyContent = extractBlock(buildingContent, braceStart - 1);
            pos = braceStart + storeyContent.length() + 2; // +2 for braces
        }

        // Sort by level
        storeys.sort((a, b) -> Integer.compare(a.level(), b.level()));

        // Parse roof
        RoofDef roof = null;
        Matcher roofMatcher = ROOF_PATTERN.matcher(buildingContent);
        if (roofMatcher.find()) {
            roof = new RoofDef(Double.parseDouble(roofMatcher.group(1)));
        }

        return new BuildingDefinition(buildingName, storeys, roof);
    }

    private static StoreyDef parseStorey(String content, int start) {
        // Extract: STOREY "name" level:N height:Xm {
        Pattern headerPattern = Pattern.compile(
            "STOREY\\s+\"([^\"]+)\"\\s+level:(\\d+)\\s+height:([\\d.]+)m"
        );

        Matcher header = headerPattern.matcher(content.substring(start));
        if (!header.find()) {
            throw new IllegalArgumentException("Invalid STOREY syntax at " + start);
        }

        String name = header.group(1);
        int level = Integer.parseInt(header.group(2));
        double height = Double.parseDouble(header.group(3));

        // Extract storey content
        int braceStart = content.indexOf('{', start);
        String storeyContent = extractBlock(content, braceStart - 1);

        // Parse rooms
        List<RoomDef> rooms = new ArrayList<>();
        String[] roomTypes = {
            // Residential
            "LIVING", "BEDROOM", "KITCHEN", "BATHROOM", "CORRIDOR", "OFFICE", "STORAGE",
            "DINING", "STUDY", "LAUNDRY", "GARAGE", "UTILITY",
            // Terminal/Commercial (Phase 14B)
            "DEPARTURE_LOUNGE", "GATE", "CONCOURSE"
        };

        for (String roomType : roomTypes) {
            int pos = 0;
            while (true) {
                int roomStart = storeyContent.indexOf(roomType, pos);
                if (roomStart < 0) break;

                // Make sure it's a room definition (not part of another word)
                if (roomStart > 0 && Character.isLetter(storeyContent.charAt(roomStart - 1))) {
                    pos = roomStart + 1;
                    continue;
                }

                RoomDef room = parseRoom(storeyContent, roomStart, roomType);
                if (room != null) {
                    rooms.add(room);
                }

                pos = roomStart + 1;
            }
        }

        // Parse stairs
        List<StairDef> stairs = new ArrayList<>();
        Matcher stairMatcher = STAIR_PATTERN.matcher(storeyContent);
        while (stairMatcher.find()) {
            stairs.add(new StairDef(
                stairMatcher.group(1),
                stairMatcher.group(2),
                Double.parseDouble(stairMatcher.group(3)),
                stairMatcher.group(4)
            ));
        }

        // Parse landings
        List<LandingDef> landings = new ArrayList<>();
        Matcher landingMatcher = LANDING_PATTERN.matcher(storeyContent);
        while (landingMatcher.find()) {
            landings.add(new LandingDef(
                landingMatcher.group(1),
                landingMatcher.group(2),
                Double.parseDouble(landingMatcher.group(3)),
                Double.parseDouble(landingMatcher.group(4)),
                landingMatcher.group(5)
            ));
        }

        return new StoreyDef(name, level, height, rooms, stairs, landings);
    }

    private static RoomDef parseRoom(String content, int start, String roomType) {
        // Try explicit position first: ROOMTYPE "name" at:XX size:WxDm {
        Pattern explicitPattern = Pattern.compile(
            roomType + "\\s+\"([^\"]+)\"\\s+at:(\\w+(?:-\\w+)?)\\s+size:([\\d.]+)x([\\d.]+)m"
        );

        // Constraint mode (no at:): ROOMTYPE "name" size:WxDm {
        Pattern constraintPattern = Pattern.compile(
            roomType + "\\s+\"([^\"]+)\"\\s+size:([\\d.]+)x([\\d.]+)m"
        );

        Matcher explicitMatcher = explicitPattern.matcher(content.substring(start));
        Matcher constraintMatcher = constraintPattern.matcher(content.substring(start));

        String name;
        String gridPos;
        double width, depth;
        int headerEnd;

        if (explicitMatcher.find()) {
            // Mode A: Explicit position
            name = explicitMatcher.group(1);
            gridPos = explicitMatcher.group(2);
            width = Double.parseDouble(explicitMatcher.group(3));
            depth = Double.parseDouble(explicitMatcher.group(4));
            headerEnd = explicitMatcher.end();
        } else if (constraintMatcher.find()) {
            // Mode B: Constraint-based (no at:)
            name = constraintMatcher.group(1);
            gridPos = null;  // Solver will determine position
            width = Double.parseDouble(constraintMatcher.group(2));
            depth = Double.parseDouble(constraintMatcher.group(3));
            headerEnd = constraintMatcher.end();
        } else {
            return null;
        }

        // Extract room content
        int braceStart = content.indexOf('{', start + headerEnd);
        if (braceStart < 0) {
            return new RoomDef(roomType, name, gridPos, width, depth, List.of());
        }

        String roomContent = extractBlock(content, braceStart - 1);

        // Parse openings
        List<OpeningDef> openings = new ArrayList<>();

        Matcher doorMatcher = DOOR_PATTERN.matcher(roomContent);
        while (doorMatcher.find()) {
            String wall = doorMatcher.group(1);
            String connectsTo = doorMatcher.group(2);
            double w = doorMatcher.group(3) != null ? Integer.parseInt(doorMatcher.group(3)) / 1000.0 : 0.9;
            double h = doorMatcher.group(4) != null ? Integer.parseInt(doorMatcher.group(4)) / 1000.0 : 2.1;
            openings.add(new OpeningDef("DOOR", wall, connectsTo, w, h));
        }

        Matcher windowMatcher = WINDOW_PATTERN.matcher(roomContent);
        while (windowMatcher.find()) {
            String wall = windowMatcher.group(1);
            double w = windowMatcher.group(2) != null ? Integer.parseInt(windowMatcher.group(2)) / 1000.0 : 1.2;
            double h = windowMatcher.group(3) != null ? Integer.parseInt(windowMatcher.group(3)) / 1000.0 : 1.0;
            openings.add(new OpeningDef("WINDOW", wall, null, w, h));
        }

        // Parse sprinklers (Phase 14B)
        Double sprinklerSpacing = null;
        Matcher sprinklerMatcher = SPRINKLER_PATTERN.matcher(roomContent);
        if (sprinklerMatcher.find()) {
            sprinklerSpacing = Double.parseDouble(sprinklerMatcher.group(1));
        }

        // Parse lights (Phase 14B)
        Double lightSpacing = null;
        Matcher lightMatcher = LIGHT_PATTERN.matcher(roomContent);
        if (lightMatcher.find()) {
            lightSpacing = Double.parseDouble(lightMatcher.group(1));
        }

        // Parse constraints (Layer 3)
        List<String> adjacentTo = new ArrayList<>();
        Matcher adjacentMatcher = ADJACENT_PATTERN.matcher(roomContent);
        while (adjacentMatcher.find()) {
            adjacentTo.add(adjacentMatcher.group(1));
        }

        List<String> notAdjacentTo = new ArrayList<>();
        Matcher notAdjacentMatcher = NOT_ADJACENT_PATTERN.matcher(roomContent);
        while (notAdjacentMatcher.find()) {
            notAdjacentTo.add(notAdjacentMatcher.group(1));
        }

        String exteriorWall = null;
        Matcher exteriorMatcher = EXTERIOR_PATTERN.matcher(roomContent);
        if (exteriorMatcher.find()) {
            exteriorWall = exteriorMatcher.group(1);
        }

        // Phase 16: Vertical alignment constraint
        String alignsWith = null;
        Matcher alignsMatcher = ALIGNS_PATTERN.matcher(roomContent);
        if (alignsMatcher.find()) {
            alignsWith = alignsMatcher.group(1);
        }

        // Phase 17: Extended vertical vocabulary
        String above = null;
        Matcher aboveMatcher = ABOVE_PATTERN.matcher(roomContent);
        if (aboveMatcher.find()) {
            above = aboveMatcher.group(1);
        }

        String below = null;
        Matcher belowMatcher = BELOW_PATTERN.matcher(roomContent);
        if (belowMatcher.find()) {
            below = belowMatcher.group(1);
        }

        String stack = null;
        Matcher stackMatcher = STACK_PATTERN.matcher(roomContent);
        if (stackMatcher.find()) {
            stack = stackMatcher.group(1);
        }

        return new RoomDef(roomType, name, gridPos, width, depth, openings,
                          sprinklerSpacing, lightSpacing, adjacentTo, notAdjacentTo,
                          exteriorWall, alignsWith, above, below, stack);
    }

    /**
     * Extract content between matching braces starting after position.
     */
    private static String extractBlock(String content, int searchFrom) {
        int braceStart = content.indexOf('{', searchFrom);
        if (braceStart < 0) return "";

        int depth = 1;
        int i = braceStart + 1;
        while (i < content.length() && depth > 0) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            i++;
        }

        return content.substring(braceStart + 1, i - 1);
    }

    // =========================================================================
    // Test
    // =========================================================================

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

        BuildingDefinition building = parse(dsl);

        System.out.println("Building: " + building.name());
        System.out.println("Storeys: " + building.storeys().size());

        for (StoreyDef storey : building.storeys()) {
            System.out.printf("\n  Storey: %s (level %d, height %.1fm)%n",
                storey.name(), storey.level(), storey.height());
            System.out.println("    Rooms: " + storey.rooms().size());
            for (RoomDef room : storey.rooms()) {
                System.out.printf("      %s \"%s\" at %s size %.1fx%.1fm%n",
                    room.type(), room.name(), room.gridPosition(), room.width(), room.depth());
                for (OpeningDef opening : room.openings()) {
                    System.out.printf("        %s %s%n", opening.type(), opening.wall());
                }
            }
            System.out.println("    Stairs: " + storey.stairs().size());
            for (StairDef stair : storey.stairs()) {
                System.out.printf("      %s at %s width %.1fm to %s%n",
                    stair.name(), stair.gridPosition(), stair.width(), stair.toStorey());
            }
            System.out.println("    Landings: " + storey.landings().size());
            for (LandingDef landing : storey.landings()) {
                System.out.printf("      %s at %s size %.1fx%.1fm from %s%n",
                    landing.name(), landing.gridPosition(), landing.width(), landing.depth(), landing.fromStair());
            }
        }

        if (building.roof() != null) {
            System.out.printf("\nRoof: %.0f deg pitch%n", building.roof().pitchDegrees());
        }

        System.out.println("\n[PASS] Parser test complete");
    }
}
