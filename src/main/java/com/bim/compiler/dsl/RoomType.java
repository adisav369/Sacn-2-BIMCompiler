package com.bim.compiler.dsl;

/**
 * Room types supported by the DSL.
 * Maps to IFC IfcSpaceTypeEnum and OmniClass Table 13.
 *
 * Added ON DEMAND as DSL requires them.
 */
public enum RoomType {
    // Residential
    BEDROOM("13-21 11 00"),
    BATHROOM("13-21 13 00"),
    KITCHEN("13-21 15 00"),
    LIVING("13-21 17 00"),
    CORRIDOR("13-81 11 00"),
    LOBBY("13-81 13 00"),
    OFFICE("13-61 11 00"),
    STORAGE("13-71 31 00"),
    GARAGE("13-71 11 00"),

    // Terminal/Commercial (Phase 14B)
    DEPARTURE_LOUNGE("13-11 21 00"),  // OmniClass: Lounge Space
    GATE("13-11 21 11"),               // OmniClass: Boarding Gate
    CONCOURSE("13-81 11 11");          // OmniClass: Concourse

    private final String omniClassCode;

    RoomType(String omniClassCode) {
        this.omniClassCode = omniClassCode;
    }

    public String getOmniClassCode() {
        return omniClassCode;
    }

    public static RoomType fromKeyword(String keyword) {
        return switch (keyword.toUpperCase()) {
            case "BEDROOM" -> BEDROOM;
            case "BATHROOM" -> BATHROOM;
            case "KITCHEN" -> KITCHEN;
            case "LIVING", "LIVINGROOM", "LIVING_ROOM" -> LIVING;
            case "CORRIDOR" -> CORRIDOR;
            case "LOBBY" -> LOBBY;
            case "OFFICE" -> OFFICE;
            case "STORAGE" -> STORAGE;
            case "GARAGE" -> GARAGE;
            // Terminal/Commercial (Phase 14B)
            case "DEPARTURE_LOUNGE", "LOUNGE" -> DEPARTURE_LOUNGE;
            case "GATE", "BOARDING_GATE" -> GATE;
            case "CONCOURSE" -> CONCOURSE;
            default -> throw new IllegalArgumentException("Unknown room type: " + keyword);
        };
    }
}
