package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 90: Ground Floor Access Check
 *
 * Validates:
 * 1. Ground floor has at least 1 door >= 2000mm wide (commercial entry)
 * 2. Ground floor lobby has windows
 * 3. Utility rooms (tnb, pump, genset) have doors
 */
public class GroundFloorAccessCheck implements SanityCheck {

    private static final double MIN_COMMERCIAL_DOOR_WIDTH_MM = 2000.0;

    @Override
    public String getId() { return "ground_floor_access"; }

    @Override
    public String getName() { return "Ground Floor Access"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        String dbPath = model.getDbPath();
        List<String> details = new ArrayList<>();
        boolean hasFail = false;
        boolean hasWarn = false;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {

            // 1. Check for large entry door on ground floor (dimensions via rtree)
            double maxDoorWidth = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT e.element_name, " +
                    "  MAX(r.maxX - r.minX, r.maxY - r.minY) as leaf_width " +
                    "FROM elements_meta e " +
                    "JOIN elements_rtree r ON e.id = r.id " +
                    "WHERE e.ifc_class = 'IfcDoor' AND LOWER(e.storey) LIKE '%ground%'")) {
                ResultSet rs = ps.executeQuery();
                int groundDoors = 0;
                while (rs.next()) {
                    groundDoors++;
                    double w = rs.getDouble("leaf_width") * 1000;
                    if (w > maxDoorWidth) maxDoorWidth = w;
                }
                if (groundDoors == 0) {
                    details.add("FAIL: No doors found on ground floor");
                    hasFail = true;
                } else if (maxDoorWidth < MIN_COMMERCIAL_DOOR_WIDTH_MM) {
                    details.add(String.format("WARN: Largest ground door is %.0fmm (need >= %.0fmm for commercial entry)",
                        maxDoorWidth, MIN_COMMERCIAL_DOOR_WIDTH_MM));
                    hasWarn = true;
                } else {
                    details.add(String.format("PASS: Ground floor has entry door %.0fmm wide", maxDoorWidth));
                }
            }

            // 2. Check lobby has windows (match via guid containing 'LOBBY')
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) as cnt FROM elements_meta " +
                    "WHERE ifc_class = 'IfcWindow' AND LOWER(storey) LIKE '%ground%' " +
                    "AND LOWER(guid) LIKE '%lobby%'")) {
                ResultSet rs = ps.executeQuery();
                int lobbyWindows = rs.next() ? rs.getInt("cnt") : 0;
                if (lobbyWindows == 0) {
                    details.add("WARN: No windows found in ground floor lobby");
                    hasWarn = true;
                } else {
                    details.add(String.format("PASS: Ground lobby has %d window(s)", lobbyWindows));
                }
            }

            // 3. Check utility rooms have doors (match via guid containing room name)
            String[] utilityRooms = {"tnb", "pump", "genset"};
            for (String util : utilityRooms) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) as cnt FROM elements_meta " +
                        "WHERE ifc_class = 'IfcDoor' AND LOWER(guid) LIKE ?")) {
                    ps.setString(1, "%" + util + "%");
                    ResultSet rs = ps.executeQuery();
                    int doors = rs.next() ? rs.getInt("cnt") : 0;
                    if (doors == 0) {
                        details.add(String.format("WARN: Utility room '%s' has no door", util));
                        hasWarn = true;
                    } else {
                        details.add(String.format("PASS: Utility room '%s' has %d door(s)", util, doors));
                    }
                }
            }

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("SQL error: " + e.getMessage())
                .build();
        }

        if (hasFail) {
            return CheckResult.fail(getId(), getName())
                .summary("Ground floor access issues found")
                .details(details)
                .guidance("Add commercial-grade entry doors and utility room access doors")
                .build();
        } else if (hasWarn) {
            return CheckResult.warning(getId(), getName())
                .summary("Ground floor access has warnings")
                .details(details)
                .guidance("Consider larger entry doors and lobby windows")
                .build();
        } else {
            return CheckResult.pass(getId(), getName())
                .summary("Ground floor access is adequate")
                .details(details)
                .build();
        }
    }
}
