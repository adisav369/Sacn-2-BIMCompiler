package com.bim.compiler.dsl;

import com.bim.compiler.dsl.BuildingSpecs.*;

import java.sql.*;
import java.util.*;

/**
 * Phase 122J: Generates IfcRailing elements from ad_railing_type metadata.
 * Stone says: Terminal has 34 IfcRailing — stairwell guard rails at 1100mm height.
 */
class RailingWriter {

    private final ElementPersistence ep;
    int railingCount = 0;

    // Loaded once from library
    private String railingName;
    private double railingHeightM;
    private double railingWidthM;

    RailingWriter(ElementPersistence ep) {
        this.ep = ep;
        loadRailingType();
    }

    /**
     * Write guard railings along stair edges.
     * Each stair gets two railings (left and right sides).
     */
    void writeStairRailings(List<StairSpec> stairs, String storeyName) throws SQLException {
        if (railingName == null) return; // no railing type in library

        for (StairSpec stair : stairs) {
            double stairMinX = stair.x();
            double stairMinY = stair.y();
            double stairMaxX = stairMinX + stair.width();
            double stairMaxY = stairMinY + stair.run();
            double stairMinZ = stair.z();
            double stairMaxZ = stair.z() + stair.rise() + railingHeightM;

            String storeyTag = storeyName.toUpperCase().replace(" ", "_");
            String stairTag = stair.name().toUpperCase().replace(" ", "_");

            // Left railing (along minX edge, running Y direction)
            String guidL = "RAILING_" + storeyTag + "_" + stairTag + "_L";
            ep.writeElement(
                guidL,
                "IfcRailing",
                railingName,
                "STAIR_GUARD",
                storeyName,
                ep.createBoxGeometry(
                    stairMinX - railingWidthM, stairMinY, stairMinZ,
                    stairMinX, stairMaxY, stairMaxZ
                )
            );
            railingCount++;

            // Right railing (along maxX edge, running Y direction)
            String guidR = "RAILING_" + storeyTag + "_" + stairTag + "_R";
            ep.writeElement(
                guidR,
                "IfcRailing",
                railingName,
                "STAIR_GUARD",
                storeyName,
                ep.createBoxGeometry(
                    stairMaxX, stairMinY, stairMinZ,
                    stairMaxX + railingWidthM, stairMaxY, stairMaxZ
                )
            );
            railingCount++;
        }
    }

    private void loadRailingType() {
        String sql = "SELECT railing_name, height_mm, width_mm FROM ad_railing_type "
            + "WHERE host_type = 'STAIR' AND is_active = 1 LIMIT 1";
        try (Connection libConn = DriverManager.getConnection(
                "jdbc:sqlite:library/component_library.db");
             Statement stmt = libConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                railingName = rs.getString(1);
                railingHeightM = rs.getInt(2) / 1000.0;
                railingWidthM = rs.getInt(3) / 1000.0;
            }
        } catch (SQLException e) {
            // Silently skip — no railings generated if table missing
        }
    }
}
