package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 90: Furniture Presence Check
 *
 * Validates that rooms with floor area > 10m² have at least 1 furniture element.
 * Uses elements_rtree for room dimensions and IfcFurnishingElement guid matching.
 */
public class FurniturePresenceCheck implements SanityCheck {

    private static final double MIN_AREA_M2 = 6.0;

    private static final Set<String> EXEMPT_PATTERNS = Set.of(
        "stair", "shaft", "riser", "void", "tank", "machine", "bathroom", "toilet", "wc"
    );

    @Override
    public String getId() { return "furniture_presence"; }

    @Override
    public String getName() { return "Furniture Presence"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();
        List<String> emptyRooms = new ArrayList<>();
        int totalChecked = 0;
        int furnished = 0;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // Get furniture GUIDs (contain room names)
            Set<String> furnitureGuids = new HashSet<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT guid FROM elements_meta WHERE ifc_class = 'IfcFurniture'")) {
                while (rs.next()) {
                    furnitureGuids.add(rs.getString("guid").toUpperCase());
                }
            }

            // Check each IfcSpace with area > threshold
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                    "SELECT e.element_name, " +
                    "  (r.maxX - r.minX) * (r.maxY - r.minY) as area " +
                    "FROM elements_meta e " +
                    "JOIN elements_rtree r ON e.id = r.id " +
                    "WHERE e.ifc_class = 'IfcSpace'")) {
                while (rs.next()) {
                    String name = rs.getString("element_name");
                    if (name == null) continue;
                    String lower = name.toLowerCase();

                    boolean exempt = false;
                    for (String ex : EXEMPT_PATTERNS) {
                        if (lower.contains(ex)) { exempt = true; break; }
                    }
                    if (exempt) continue;

                    double area = rs.getDouble("area");
                    if (area < MIN_AREA_M2) continue;

                    totalChecked++;

                    // Check if any furniture GUID contains this room name
                    String nameUpper = name.toUpperCase();
                    boolean hasFurniture = false;
                    for (String guid : furnitureGuids) {
                        if (guid.contains(nameUpper)) {
                            hasFurniture = true;
                            break;
                        }
                    }

                    if (hasFurniture) {
                        furnished++;
                    } else {
                        emptyRooms.add(String.format("%s (%.0fm²)", name, area));
                    }
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("SQL error: " + e.getMessage())
                .build();
        }

        if (totalChecked == 0) {
            return CheckResult.pass(getId(), getName())
                .summary("No rooms large enough to check for furniture")
                .build();
        }

        if (emptyRooms.isEmpty()) {
            return CheckResult.pass(getId(), getName())
                .summary(String.format("All %d rooms (>%.0fm²) have furniture", totalChecked, MIN_AREA_M2))
                .build();
        }

        CheckResult.Builder result = CheckResult.warning(getId(), getName())
            .summary(String.format("%d of %d rooms (>%.0fm²) have no furniture",
                emptyRooms.size(), totalChecked, MIN_AREA_M2));

        for (int i = 0; i < Math.min(emptyRooms.size(), 10); i++) {
            result.detail("Empty: " + emptyRooms.get(i));
        }
        if (emptyRooms.size() > 10) {
            result.detail(String.format("... and %d more", emptyRooms.size() - 10));
        }

        result.guidance("Add furniture to habitable rooms for realistic model");
        result.data("emptyRooms", emptyRooms.size());
        result.data("totalChecked", totalChecked);

        return result.build();
    }
}
