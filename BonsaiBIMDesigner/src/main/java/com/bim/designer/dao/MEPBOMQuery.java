package com.bim.designer.dao;

import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Query ad_space_type_mep_bom from disc_validation.db for Click-to-Place.
 *
 * <p>Maps (space_type, discipline) → list of MEP products with qty and placement rules.
 * This is the data-driven replacement for hardcoded discipline→product keyword mapping.
 *
 * <p>Discipline grouping (extracted from disc_validation.db product inventory):
 * <ul>
 *   <li>FP: SPRINKLER, EMERGENCY_LIGHT</li>
 *   <li>ELEC: LIGHT, OUTLET, OUTLET_20A, OUTLET_GFCI, SWITCH, DATA_POINT, CEILING_FAN</li>
 *   <li>SP: TOILET, SINK, FLOOR_TRAP, WASHING_TAP</li>
 *   <li>ACMV: SUPPLY_DIFFUSER, EXHAUST_FAN, AIRCON_POINT</li>
 *   <li>ARC: (no MEP products — furniture comes from component_library.db M_Product)</li>
 * </ul>
 *
 * // Implementing BIM_Designer.md §18.8 — Witness: W-CTP-MEP-1
 */
public class MEPBOMQuery {

    private static final String TAG = "MEPBOMQuery";

    private final Connection conn;

    public MEPBOMQuery(Connection conn) {
        this.conn = conn;
    }

    /**
     * A MEP product requirement for a room type.
     */
    public record MEPRequirement(
            String spaceType,
            String mepProductId,
            int qtyMin,
            int qtyNormal,
            int qtyMax,
            double perAreaNormal,
            String placementRule,
            String hostSurface,
            String buildingCode,
            String codeClause
    ) {}

    /**
     * Query all MEP products required for a given space type and discipline.
     *
     * @param spaceType  room category: BEDROOM, BATHROOM, KITCHEN, LIVING, etc.
     * @param discipline discipline code: FP, ELEC, SP, ACMV (ARC returns empty)
     * @return list of MEP requirements, ordered by mep_product_id
     */
    public List<MEPRequirement> queryForDiscipline(String spaceType, String discipline) throws SQLException {
        return queryForDiscipline(spaceType, discipline, List.of());
    }

    // Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
    public List<MEPRequirement> queryForDiscipline(String spaceType, String discipline,
                                                    List<String> packIds) throws SQLException {
        if (spaceType == null || discipline == null) return List.of();

        // Map discipline to the set of mep_product_ids it covers
        List<String> productIds = disciplineProducts(discipline);
        if (productIds.isEmpty()) return List.of();

        // Build IN clause for products
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < productIds.size(); i++) {
            if (i > 0) inClause.append(",");
            inClause.append("?");
        }

        // Build pack_id filter (empty = no filter, backward compatible)
        String packFilter = "";
        if (packIds != null && !packIds.isEmpty()) {
            StringBuilder packIn = new StringBuilder();
            for (int i = 0; i < packIds.size(); i++) {
                if (i > 0) packIn.append(",");
                packIn.append("?");
            }
            packFilter = " AND pack_id IN (" + packIn + ")";
        }

        String sql = "SELECT space_type_id, mep_product_id, qty_min, qty_normal, qty_max, "
                + "per_area_normal, placement_rule, host_surface, building_code, code_clause "
                + "FROM ad_space_type_mep_bom "
                + "WHERE space_type_id = ? AND mep_product_id IN (" + inClause + ")"
                + packFilter
                + " ORDER BY mep_product_id";

        List<MEPRequirement> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, spaceType);
            for (String pid : productIds) {
                ps.setString(idx++, pid);
            }
            if (packIds != null) {
                for (String pack : packIds) {
                    ps.setString(idx++, pack);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MEPRequirement(
                            rs.getString("space_type_id"),
                            rs.getString("mep_product_id"),
                            rs.getInt("qty_min"),
                            rs.getInt("qty_normal"),
                            rs.getInt("qty_max"),
                            rs.getDouble("per_area_normal"),
                            rs.getString("placement_rule"),
                            rs.getString("host_surface"),
                            rs.getString("building_code"),
                            rs.getString("code_clause")
                    ));
                }
            }
        }

        BIMLogger.info(TAG, "QUERY {} + {} packs={} → {} requirements",
                spaceType, discipline, packIds, result.size());
        return result;
    }

    /**
     * Query all MEP products for a space type (all disciplines).
     */
    public List<MEPRequirement> queryAll(String spaceType) throws SQLException {
        if (spaceType == null) return List.of();

        String sql = "SELECT space_type_id, mep_product_id, qty_min, qty_normal, qty_max, "
                + "per_area_normal, placement_rule, host_surface, building_code, code_clause "
                + "FROM ad_space_type_mep_bom "
                + "WHERE space_type_id = ? "
                + "ORDER BY mep_product_id";

        List<MEPRequirement> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spaceType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new MEPRequirement(
                            rs.getString("space_type_id"),
                            rs.getString("mep_product_id"),
                            rs.getInt("qty_min"),
                            rs.getInt("qty_normal"),
                            rs.getInt("qty_max"),
                            rs.getDouble("per_area_normal"),
                            rs.getString("placement_rule"),
                            rs.getString("host_surface"),
                            rs.getString("building_code"),
                            rs.getString("code_clause")
                    ));
                }
            }
        }
        return result;
    }

    /**
     * Map discipline code to mep_product_id set.
     * Extracted from disc_validation.db product inventory analysis.
     */
    public static List<String> disciplineProducts(String discipline) {
        return switch (discipline) {
            case "FP" -> List.of("SPRINKLER", "EMERGENCY_LIGHT");
            case "ELEC" -> List.of("LIGHT", "OUTLET", "OUTLET_20A", "OUTLET_GFCI",
                    "SWITCH", "DATA_POINT", "CEILING_FAN");
            case "SP" -> List.of("TOILET", "SINK", "FLOOR_TRAP", "WASHING_TAP");
            case "ACMV" -> List.of("SUPPLY_DIFFUSER", "EXHAUST_FAN", "AIRCON_POINT");
            default -> List.of(); // ARC = no MEP products
        };
    }
}
