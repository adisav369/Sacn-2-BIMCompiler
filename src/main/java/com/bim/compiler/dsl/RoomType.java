package com.bim.compiler.dsl;

import com.bim.compiler.util.OutlierLogger;

/**
 * Room types supported by the DSL.
 * Maps to IFC IfcSpaceTypeEnum and OmniClass Table 13.
 *
 * Added ON DEMAND as DSL requires them.
 * Phase 25: Added GENERIC fallback for unknown types.
 * Phase 28: Added WallRule for wall generation behavior.
 */
public enum RoomType {
    // Residential - ENCLOSED (4 walls)
    BEDROOM("13-21 11 00", WallRule.ENCLOSED),
    MASTER_BEDROOM("13-21 11 11", WallRule.ENCLOSED),  // Bilik Utama
    BATHROOM("13-21 13 00", WallRule.ENCLOSED),
    KITCHEN("13-21 15 00", WallRule.ENCLOSED),
    WET_KITCHEN("13-21 15 11", WallRule.ENCLOSED),     // Dapur Basuh (laundry/utility)
    DINING("13-21 17 11", WallRule.ENCLOSED),          // Ruang Makan
    LIVING("13-21 17 00", WallRule.ENCLOSED),
    CORRIDOR("13-81 11 00", WallRule.AS_REQUIRED),
    LOBBY("13-81 13 00", WallRule.AS_REQUIRED),
    OFFICE("13-61 11 00", WallRule.ENCLOSED),
    STORAGE("13-71 31 00", WallRule.ENCLOSED),
    GARAGE("13-71 11 00", WallRule.ENCLOSED),

    // Terminal/Commercial (Phase 14B)
    DEPARTURE_LOUNGE("13-11 21 00", WallRule.PERIMETER_ONLY),
    GATE("13-11 21 11", WallRule.PERIMETER_ONLY),
    CONCOURSE("13-81 11 11", WallRule.PERIMETER_ONLY),

    // Phase 26/27: Construction-aware types
    PORCH("13-81 15 00", WallRule.NONE),           // Anjung - No walls (posts/columns only)
    CAR_PORCH("13-81 15 11", WallRule.NONE),       // Anjung Kereta - Car porch
    VERANDAH("13-81 15 21", WallRule.NONE),        // Serambi - Covered outdoor
    OPEN_PLAN("13-21 17 21", WallRule.PERIMETER_ONLY),  // Exterior walls only, no internal

    // Phase 25: Fallback for unknown types
    GENERIC("13-00 00 00", WallRule.AS_REQUIRED);

    /**
     * Phase 28: Wall generation rules.
     * Determines how walls are generated for a space type.
     */
    public enum WallRule {
        ENCLOSED,        // All 4 walls (private rooms: BEDROOM, BATHROOM)
        PERIMETER_ONLY,  // Exterior walls only (OPEN_PLAN, DEPARTURE_LOUNGE)
        NONE,            // No walls - posts/columns only (PORCH)
        AS_REQUIRED      // Context-dependent (CORRIDOR, LOBBY)
    }

    private final String omniClassCode;
    private final WallRule wallRule;

    RoomType(String omniClassCode, WallRule wallRule) {
        this.omniClassCode = omniClassCode;
        this.wallRule = wallRule;
    }

    // Backward-compatible constructor (defaults to ENCLOSED)
    RoomType(String omniClassCode) {
        this(omniClassCode, WallRule.ENCLOSED);
    }

    public String getOmniClassCode() {
        return omniClassCode;
    }

    /**
     * Phase 28: Get wall generation rule for this space type.
     */
    public WallRule getWallRule() {
        return wallRule;
    }

    /**
     * Check if this space type requires full enclosure (4 walls).
     */
    public boolean requiresEnclosure() {
        return wallRule == WallRule.ENCLOSED;
    }

    /**
     * Check if this space type allows internal walls.
     * PERIMETER_ONLY and NONE types don't allow internal walls.
     */
    public boolean allowsInternalWalls() {
        return wallRule == WallRule.ENCLOSED || wallRule == WallRule.AS_REQUIRED;
    }

    /**
     * Resolve keyword to RoomType with graceful fallback.
     * Phase 25: Graceful degradation instead of throwing exceptions.
     *
     * Fallback cascade:
     * 1. Exact match in enum
     * 2. Partial match (MASTER_BEDROOM → BEDROOM)
     * 3. Category guess from name
     * 4. GENERIC fallback
     */
    public static RoomType fromKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            logFallback("(empty)", "GENERIC");
            return GENERIC;
        }

        String upper = keyword.toUpperCase().trim();

        // 1. Exact match
        RoomType exact = tryExactMatch(upper);
        if (exact != null) {
            return exact;
        }

        // 2. Partial match - check if keyword contains known type
        RoomType partial = tryPartialMatch(upper);
        if (partial != null) {
            logFallback(keyword, partial.name());
            return partial;
        }

        // 3. Category guess from common naming patterns
        RoomType category = guessCategory(upper);
        if (category != null) {
            logFallback(keyword, category.name());
            return category;
        }

        // 4. GENERIC fallback
        logFallback(keyword, "GENERIC");
        return GENERIC;
    }

    /**
     * Log fallback resolution via OutlierLogger.
     * Phase 25: Integrated with OutlierLogger for actionable guidance.
     */
    private static void logFallback(String keyword, String resolvedTo) {
        OutlierLogger.logUnknownSpaceType(keyword, resolvedTo);
    }

    /**
     * Try exact match against known keywords.
     */
    /**
     * Try exact match against known keywords.
     * Phase 29: Added Malaysian vocabulary from TB-LKTN Vision AI extraction.
     *
     * Malaysian terms (UBBL 1984 / Bahasa Malaysia):
     *   MR = Master Room (Bilik Utama)
     *   BT = Bilik Tidur (Bedroom)
     *   RT = Ruang Tamu (Living Room)
     *   WC = Water Closet (Tandas)
     *   CP = Car Porch (Anjung Kereta)
     */
    private static RoomType tryExactMatch(String upper) {
        return switch (upper) {
            // Bedroom variants (English + Malaysian)
            case "BEDROOM", "BED", "BT", "BILIK_TIDUR" -> BEDROOM;
            case "MASTER_BEDROOM", "MASTER", "MR", "BILIK_UTAMA" -> MASTER_BEDROOM;

            // Bathroom variants (English + Malaysian)
            case "BATHROOM", "BATH", "BILIK_MANDI" -> BATHROOM;
            case "WC", "TOILET", "TANDAS" -> BATHROOM;

            // Kitchen variants (English + Malaysian)
            case "KITCHEN", "DAPUR" -> KITCHEN;
            case "WET_KITCHEN", "DAPUR_BASUH", "LAUNDRY", "BASUH" -> WET_KITCHEN;

            // Living/Dining variants (English + Malaysian)
            case "LIVING", "LIVINGROOM", "LIVING_ROOM", "RUANG_TAMU", "RT", "TAMU" -> LIVING;
            case "DINING", "DINING_ROOM", "RUANG_MAKAN", "MAKAN" -> DINING;

            // Circulation
            case "CORRIDOR", "HALL", "HALLWAY" -> CORRIDOR;
            case "LOBBY", "FOYER", "ENTRY" -> LOBBY;

            // Work/Storage
            case "OFFICE", "STUDY", "PEJABAT" -> OFFICE;
            case "STORAGE", "STORE", "STOR", "PANTRY" -> STORAGE;
            case "GARAGE", "GARAJ" -> GARAGE;

            // Terminal/Commercial (Phase 14B)
            case "DEPARTURE_LOUNGE", "LOUNGE" -> DEPARTURE_LOUNGE;
            case "GATE", "BOARDING_GATE" -> GATE;
            case "CONCOURSE" -> CONCOURSE;

            // Exterior/Semi-exterior (Phase 26/27 + Malaysian)
            case "PORCH", "ANJUNG" -> PORCH;
            case "CAR_PORCH", "CARPORT", "CP", "ANJUNG_KERETA" -> CAR_PORCH;
            case "VERANDAH", "VERANDA", "SERAMBI" -> VERANDAH;

            // Open plan
            case "OPEN_PLAN", "OPENPLAN", "COMMON" -> OPEN_PLAN;

            // Fallback
            case "GENERIC" -> GENERIC;
            default -> null;
        };
    }

    /**
     * Try partial match - keyword contains a known type.
     * Examples: MASTER_BEDROOM → BEDROOM, ENSUITE_BATHROOM → BATHROOM
     */
    private static RoomType tryPartialMatch(String upper) {
        // Order matters - check more specific first
        if (upper.contains("BEDROOM") || upper.contains("BED_ROOM")) return BEDROOM;
        if (upper.contains("BATHROOM") || upper.contains("BATH_ROOM") || upper.contains("ENSUITE")) return BATHROOM;
        if (upper.contains("KITCHEN")) return KITCHEN;
        if (upper.contains("LIVING") || upper.contains("LOUNGE")) return LIVING;
        if (upper.contains("CORRIDOR") || upper.contains("HALL")) return CORRIDOR;
        if (upper.contains("LOBBY")) return LOBBY;
        if (upper.contains("OFFICE") || upper.contains("STUDY")) return OFFICE;
        if (upper.contains("STORAGE") || upper.contains("CLOSET") || upper.contains("PANTRY")) return STORAGE;
        if (upper.contains("GARAGE") || upper.contains("CARPORT")) return GARAGE;
        if (upper.contains("DEPARTURE") || upper.contains("WAITING")) return DEPARTURE_LOUNGE;
        if (upper.contains("GATE") || upper.contains("BOARDING")) return GATE;
        if (upper.contains("CONCOURSE")) return CONCOURSE;
        return null;
    }

    /**
     * Guess category from common naming patterns.
     * Examples: SUNROOM → LIVING (habitable with exterior)
     *           WC → BATHROOM
     *           UTILITY → STORAGE
     *
     * Phase 25: Added Malaysian vocabulary support (UBBL 1984)
     * - RUANG_TAMU = Living Room
     * - BILIK_UTAMA = Master Bedroom
     * - DAPUR = Kitchen
     * - TANDAS = Toilet
     * - BILIK_MANDI = Bathroom
     * - RUANG_BASUH = Laundry
     * - ANJUNG = Porch
     */
    private static RoomType guessCategory(String upper) {
        // Bathroom variants (English + Malaysian)
        // TANDAS = Toilet, MANDI = Bath
        if (upper.contains("WC") || upper.contains("TOILET") || upper.contains("LAVATORY") ||
            upper.contains("POWDER") || upper.contains("TANDAS") || upper.contains("MANDI")) {
            return BATHROOM;
        }

        // Bedroom variants (Malaysian) - check before LIVING
        // BILIK = Room (commonly bedroom), BILIK_UTAMA = Master Bedroom
        if (upper.contains("BILIK") && !upper.contains("MANDI")) {
            return BEDROOM;
        }

        // Living/habitable variants (English + Malaysian)
        // RUANG_TAMU = Living Room, RUANG_MAKAN = Dining Room
        if (upper.contains("SUN") || upper.contains("FAMILY") || upper.contains("SITTING") ||
            upper.contains("DEN") || upper.contains("GREAT") ||
            upper.contains("TAMU") || upper.contains("MAKAN")) {
            return LIVING;
        }

        // Kitchen variants (English + Malaysian)
        // DAPUR = Kitchen
        if (upper.contains("SCULLERY") || upper.contains("COOK") || upper.contains("DAPUR")) {
            return KITCHEN;
        }

        // Storage/Utility variants (English + Malaysian)
        // RUANG_BASUH = Laundry/Washing Room
        if (upper.contains("UTILITY") || upper.contains("LAUNDRY") || upper.contains("MUD") ||
            upper.contains("BASUH") || upper.contains("STOR")) {
            return STORAGE;
        }

        // Office variants
        if (upper.contains("STUDY") || upper.contains("WORK") || upper.contains("HOME_OFFICE")) {
            return OFFICE;
        }

        // Circulation/Entry variants (English + Malaysian)
        // ANJUNG = Porch/Veranda, SERAMBI = Veranda
        if (upper.contains("ENTRY") || upper.contains("FOYER") || upper.contains("VESTIBULE") ||
            upper.contains("ANJUNG") || upper.contains("SERAMBI") || upper.contains("PORCH")) {
            return LOBBY;
        }

        return null; // Cannot guess
    }

    /**
     * Check if this RoomType is habitable (requires natural light).
     */
    public boolean isHabitable() {
        return switch (this) {
            case BEDROOM, MASTER_BEDROOM, LIVING, DINING, KITCHEN, OFFICE,
                 DEPARTURE_LOUNGE, GATE, OPEN_PLAN -> true;
            case BATHROOM, WET_KITCHEN, CORRIDOR, LOBBY, STORAGE, GARAGE,
                 CONCOURSE, PORCH, CAR_PORCH, VERANDAH, GENERIC -> false;
        };
    }

    /**
     * Check if this RoomType is a sleeping room (requires emergency egress).
     */
    public boolean isSleepingRoom() {
        return this == BEDROOM || this == MASTER_BEDROOM;
    }

    /**
     * Check if this is an open-plan space (no internal walls).
     */
    public boolean isOpenPlan() {
        return this == OPEN_PLAN || this == DEPARTURE_LOUNGE || this == CONCOURSE;
    }

    /**
     * Check if this is an exterior/semi-exterior space.
     */
    public boolean isExteriorSpace() {
        return this == PORCH || this == CAR_PORCH || this == VERANDAH;
    }
}
