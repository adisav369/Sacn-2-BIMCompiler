package com.bim.compiler.dsl;

import com.bim.ormsandbox.po.M_AdSpaceType;
import java.sql.*;
import java.util.*;

/**
 * Phase 92D: Data-driven MEP resolver using ad_space_type_mep_bom table.
 *
 * Replaces hardcoded MEP guarantee loop. Loads per-space-type MEP recipes
 * from component_library.db and resolves quantities based on room area.
 *
 * Products we can produce: SPRINKLER, LIGHT, CEILING_FAN, SUPPLY_DIFFUSER.
 * Other products (OUTLET, SWITCH, AIRCON_POINT, etc.) are ignored — they
 * need wall-mount placement which is handled by other systems.
 */
public class MEPBOMResolver {

    private static final String LIB_PATH = "library/BOM.db";

    /** Ceiling MEP products our pipeline can place */
    private static final Set<String> CEILING_PRODUCTS = Set.of(
        "SPRINKLER", "LIGHT", "CEILING_FAN", "SUPPLY_DIFFUSER"
    );

    /** Big-room threshold for doubling MEP sets */
    private static final double BIG_ROOM_AREA = 80.0;
    private static final double BIG_ROOM_MIN_DIM = 3.0;

    public record MEPSet(String productId, int quantity, String placementRule) {}

    private final Map<String, List<MEPRule>> rulesByType = new HashMap<>();
    private final Map<String, String> aliasMap = new HashMap<>();

    public MEPBOMResolver() {
        loadFromLibrary();
    }

    private void loadFromLibrary() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + LIB_PATH)) {
            // Load alias map
            aliasMap.putAll(M_AdSpaceType.loadAllAliases(conn));

            // Load ceiling MEP rules
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT space_type_id, mep_product_id, qty_normal, per_area_normal, " +
                     "       placement_rule, qty_min, qty_max " +
                     "FROM ad_space_type_mep_bom " +
                     "WHERE mep_product_id IN ('SPRINKLER','LIGHT','CEILING_FAN','SUPPLY_DIFFUSER') " +
                     "ORDER BY space_type_id, mep_product_id")) {
                while (rs.next()) {
                    String spaceType = rs.getString("space_type_id").toUpperCase();
                    MEPRule rule = new MEPRule(
                        rs.getString("mep_product_id"),
                        rs.getInt("qty_normal"),
                        rs.getDouble("per_area_normal"),
                        rs.getString("placement_rule"),
                        rs.getInt("qty_min"),
                        rs.getInt("qty_max")
                    );
                    rulesByType.computeIfAbsent(spaceType, k -> new ArrayList<>()).add(rule);
                }
            }

            System.out.printf("[MEP-BOM] Loaded %d space types with ceiling MEP rules%n",
                rulesByType.size());

        } catch (SQLException e) {
            System.err.println("[MEP-BOM] Failed to load from library: " + e.getMessage());
        }
    }

    /**
     * Resolve ceiling MEP sets for a room.
     *
     * @param roomType  The room's space type (e.g., "CORRIDOR", "GENERIC")
     * @param roomName  Room name for stair detection
     * @param area      Room area in m²
     * @param roomW     Room width in meters
     * @param roomD     Room depth in meters
     * @return List of MEP sets to place (product + quantity + placement rule)
     */
    public List<MEPSet> resolveForRoom(String roomType, String roomName, double area,
                                        double roomW, double roomD) {
        // Map room type to BOM space type
        String spaceType = mapToSpaceType(roomType, roomName);

        // Look up rules
        List<MEPRule> rules = rulesByType.get(spaceType);
        if (rules == null) {
            // Fallback to OFFICE (generic habitable default)
            rules = rulesByType.get("OFFICE");
        }
        if (rules == null) {
            return List.of(); // No rules at all
        }

        // Big-room multiplier
        int multiplier = (area >= BIG_ROOM_AREA && roomW >= BIG_ROOM_MIN_DIM
                          && roomD >= BIG_ROOM_MIN_DIM) ? 2 : 1;

        List<MEPSet> result = new ArrayList<>();
        for (MEPRule rule : rules) {
            int qty;
            if (rule.perAreaNormal > 0) {
                qty = (int) Math.ceil(area * rule.perAreaNormal);
                qty = Math.max(rule.qtyMin, Math.min(rule.qtyMax, qty));
            } else {
                qty = rule.qtyNormal;
            }

            // Apply big-room multiplier
            qty *= multiplier;

            if (qty > 0) {
                result.add(new MEPSet(rule.productId, qty, rule.placementRule));
            }
        }

        return result;
    }

    /**
     * Map runtime room type + name to BOM space type.
     * Priority: direct match → alias → name-based inference → OFFICE default.
     */
    String mapToSpaceType(String roomType, String roomName) {
        if (roomType == null) roomType = "GENERIC";
        String upper = roomType.toUpperCase();

        // Direct match in BOM rules
        if (rulesByType.containsKey(upper)) {
            return upper;
        }

        // Alias lookup
        String aliased = aliasMap.get(upper);
        if (aliased != null && rulesByType.containsKey(aliased)) {
            return aliased;
        }

        // Name-based inference for GENERIC rooms
        if (roomName != null) {
            String lower = roomName.toLowerCase();
            if (lower.contains("stair")) return "STAIR";
            if (lower.contains("lobby")) return "LOBBY";
            if (lower.contains("corridor") || lower.contains("hall")) return "CORRIDOR";
        }

        // Default
        return "OFFICE";
    }

    private record MEPRule(String productId, int qtyNormal, double perAreaNormal,
                           String placementRule, int qtyMin, int qtyMax) {}
}
