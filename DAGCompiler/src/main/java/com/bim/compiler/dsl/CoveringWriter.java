/*
 * BIM Intent Compiler — DAGCompiler Pipeline
 * Copyright (c) 2025-2026 Redhuan D. Oon <red1org@gmail.com>
 * SPDX-License-Identifier: MIT
 */
package com.bim.compiler.dsl;

import com.bim.compiler.BIMConstants;
import com.bim.compiler.dsl.BuildingSpecs.*;

import java.sql.*;
import java.util.*;

/**
 * Phase 122J: Generates IfcCovering elements (ceiling tiles) from ad_covering_type metadata.
 * Stone says: Terminal has 82 IfcCovering at 16mm — all ceiling tiles matching room footprints.
 */
class CoveringWriter {

    private final ElementPersistence ep;
    int coveringCount = 0;

    // Loaded once from library
    private String coveringName;
    private double coveringThickM;

    CoveringWriter(ElementPersistence ep) {
        this.ep = ep;
        loadCoveringType();
    }

    /**
     * Write ceiling coverings for all rooms in a storey.
     * Each room gets one ceiling tile matching its XY footprint.
     */
    void writeCoverings(List<RoomSpec> rooms, String storeyName,
                        double storeyBaseZ, double storeyHeight) throws SQLException {
        if (coveringName == null) return; // no covering type in library

        double slabThickness = BIMConstants.STANDARD_SLAB_THICKNESS;
        double ceilingZ = storeyBaseZ + storeyHeight - slabThickness;

        for (RoomSpec room : rooms) {
            if (room.minX() >= room.maxX() || room.minY() >= room.maxY()) continue;

            double coveringMinZ = ceilingZ - coveringThickM;

            String guid = "COVERING_" + storeyName.toUpperCase().replace(" ", "_")
                + "_" + room.name().toUpperCase().replace(" ", "_");

            ep.writeElement(
                guid,
                "IfcCovering",
                coveringName,
                "CEILING",
                storeyName,
                ep.createBoxGeometry(
                    room.minX(), room.minY(), coveringMinZ,
                    room.maxX(), room.maxY(), ceilingZ
                )
            );
            coveringCount++;
        }
    }

    private void loadCoveringType() {
        String sql = "SELECT covering_name, thickness_mm FROM ad_covering_type "
            + "WHERE covering_category = 'CEILING' AND is_active = 1 LIMIT 1";
        try (Connection libConn = DriverManager.getConnection(
                "jdbc:sqlite:" + System.getProperty("bom.db"));
             Statement stmt = libConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                coveringName = rs.getString(1);
                coveringThickM = rs.getInt(2) / 1000.0;
            }
        } catch (SQLException e) {
            // Silently skip — no coverings generated if table missing
        }
    }
}
