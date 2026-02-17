package com.bim.compiler.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for the BIM DSL.
 *
 * Simple regex-based parsing. No parser generator needed.
 *
 * DSL Format:
 *   STOREY "name" height:2.8m {
 *       ROOMTYPE "name" at:A1 size:5x3m {
 *           DOOR direction to:roomname
 *           WINDOW direction
 *       }
 *   }
 */
public class DSLParser {

    // Patterns
    private static final Pattern STOREY_PATTERN =
        Pattern.compile("STOREY\\s+\"([^\"]+)\"\\s+height:([\\d.]+)m\\s*\\{");

    private static final Pattern ROOM_PATTERN =
        Pattern.compile("(BEDROOM|BATHROOM|KITCHEN|LIVING|CORRIDOR|LOBBY|OFFICE|STORAGE)\\s+" +
                        "\"([^\"]+)\"\\s+at:([A-Z]\\d+(?:-[A-Z]\\d+)?)\\s+" +
                        "(?:size:([\\d.]+)x([\\d.]+)m|width:([\\d.]+)m)\\s*\\{");

    private static final Pattern DOOR_PATTERN =
        Pattern.compile("DOOR\\s+(north|south|east|west)\\s+to:(\\w+)");

    private static final Pattern WINDOW_PATTERN =
        Pattern.compile("WINDOW\\s+(north|south|east|west)");

    private static final Pattern SPRINKLERS_PATTERN =
        Pattern.compile("SPRINKLERS(?:\\s+grid:([\\d.]+)m)?");

    /**
     * Parse DSL text into StoreyDefinition.
     */
    public static StoreyDefinition parse(String dsl) {
        // Remove comments
        dsl = dsl.replaceAll("//.*", "");
        dsl = dsl.replaceAll("/\\*.*?\\*/", "");

        // Find storey
        Matcher storeyMatcher = STOREY_PATTERN.matcher(dsl);
        if (!storeyMatcher.find()) {
            throw new DSLParseException("No STOREY definition found");
        }

        StoreyDefinition.Builder storeyBuilder = new StoreyDefinition.Builder()
            .name(storeyMatcher.group(1))
            .height(Double.parseDouble(storeyMatcher.group(2)));

        // Find storey content (between first { and matching })
        int braceStart = storeyMatcher.end() - 1;
        int braceEnd = findMatchingBrace(dsl, braceStart);
        String storeyContent = dsl.substring(braceStart + 1, braceEnd);

        // Parse rooms
        List<RoomDefinition> rooms = parseRooms(storeyContent);
        for (RoomDefinition room : rooms) {
            storeyBuilder.addRoom(room);
        }

        return storeyBuilder.build();
    }

    /**
     * Parse all rooms from storey content.
     */
    private static List<RoomDefinition> parseRooms(String content) {
        List<RoomDefinition> rooms = new ArrayList<>();

        Matcher roomMatcher = ROOM_PATTERN.matcher(content);
        while (roomMatcher.find()) {
            RoomDefinition.Builder roomBuilder = new RoomDefinition.Builder()
                .type(RoomType.fromKeyword(roomMatcher.group(1)))
                .name(roomMatcher.group(2));

            // Parse grid position
            String gridSpec = roomMatcher.group(3);
            if (gridSpec.contains("-")) {
                String[] parts = gridSpec.split("-");
                roomBuilder.gridStart(RoomDefinition.GridCell.parse(parts[0]));
                roomBuilder.gridEnd(RoomDefinition.GridCell.parse(parts[1]));
            } else {
                roomBuilder.gridStart(RoomDefinition.GridCell.parse(gridSpec));
            }

            // Parse size
            if (roomMatcher.group(4) != null) {
                // size:5x3m format
                roomBuilder.width(Double.parseDouble(roomMatcher.group(4)));
                roomBuilder.depth(Double.parseDouble(roomMatcher.group(5)));
            } else {
                // width:1.2m format (for corridors)
                roomBuilder.width(Double.parseDouble(roomMatcher.group(6)));
                roomBuilder.depth(Double.parseDouble(roomMatcher.group(6))); // square by default
            }

            // Find room content
            int roomBraceStart = content.indexOf('{', roomMatcher.start());
            int roomBraceEnd = findMatchingBrace(content, roomBraceStart);
            String roomContent = content.substring(roomBraceStart + 1, roomBraceEnd);

            // Parse doors
            Matcher doorMatcher = DOOR_PATTERN.matcher(roomContent);
            while (doorMatcher.find()) {
                roomBuilder.addDoor(DoorDefinition.parse(
                    doorMatcher.group(1),
                    doorMatcher.group(2)
                ));
            }

            // Parse windows
            Matcher windowMatcher = WINDOW_PATTERN.matcher(roomContent);
            while (windowMatcher.find()) {
                roomBuilder.addWindow(WindowDefinition.parse(windowMatcher.group(1)));
            }

            // Parse sprinklers
            Matcher sprinklerMatcher = SPRINKLERS_PATTERN.matcher(roomContent);
            if (sprinklerMatcher.find()) {
                String spacingStr = sprinklerMatcher.group(1);
                roomBuilder.sprinklers(SprinklerDefinition.parse(spacingStr));
            }

            rooms.add(roomBuilder.build());
        }

        return rooms;
    }

    /**
     * Find matching closing brace.
     */
    private static int findMatchingBrace(String text, int openPos) {
        int depth = 1;
        for (int i = openPos + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        throw new DSLParseException("Unmatched brace at position " + openPos);
    }

    /**
     * Parse exception.
     */
    public static class DSLParseException extends RuntimeException {
        public DSLParseException(String message) {
            super(message);
        }
    }
}
