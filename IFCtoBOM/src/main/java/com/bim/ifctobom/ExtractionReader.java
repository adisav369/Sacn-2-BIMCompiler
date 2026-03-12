package com.bim.ifctobom;

import java.sql.*;
import java.util.*;

/**
 * Reads {@code I_Element_Extraction} from component_library.db.
 * Pure reader — no writes, no side effects.
 */
public class ExtractionReader {

    /**
     * One extracted IFC element with its spatial data.
     * Coordinates in metres (as stored in component_library.db).
     */
    public record ExtractionElement(
            String storey, String ifcClass, String elementRef, int ordinal,
            double minX, double maxX, double minY, double maxY, double minZ, double maxZ,
            String orientation, String materialName, String materialRgba,
            String mProductId
    ) {
        public double centroidX() { return (minX + maxX) / 2; }
        public double centroidY() { return (minY + maxY) / 2; }
        public double centroidZ() { return (minZ + maxZ) / 2; }

        /** Element width in mm. */
        public double widthMm() { return (maxX - minX) * 1000; }
        /** Element depth in mm. */
        public double depthMm() { return (maxY - minY) * 1000; }
        /** Element height in mm. */
        public double heightMm() { return (maxZ - minZ) * 1000; }
    }

    /**
     * Read all active elements for a building type, grouped by storey.
     * Order: storey, ifc_class, ordinal.
     */
    public static Map<String, List<ExtractionElement>> readByStorey(
            Connection compConn, String buildingType) throws SQLException {

        String sql = """
                SELECT storey, ifc_class, element_ref, ordinal,
                       min_x, max_x, min_y, max_y, min_z, max_z,
                       orientation, material_name, material_rgba, M_Product_ID
                FROM I_Element_Extraction
                WHERE building_type = ?
                ORDER BY storey, ifc_class, ordinal
                """;

        Map<String, List<ExtractionElement>> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ExtractionElement e = new ExtractionElement(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                            rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8),
                            rs.getDouble(9), rs.getDouble(10),
                            rs.getString(11), rs.getString(12), rs.getString(13),
                            rs.getString(14)
                    );
                    result.computeIfAbsent(e.storey(), k -> new ArrayList<>()).add(e);
                }
            }
        }
        return result;
    }

    /**
     * Flat list of all elements for a building type.
     */
    public static List<ExtractionElement> readAll(
            Connection compConn, String buildingType) throws SQLException {
        List<ExtractionElement> all = new ArrayList<>();
        readByStorey(compConn, buildingType).values().forEach(all::addAll);
        return all;
    }
}
