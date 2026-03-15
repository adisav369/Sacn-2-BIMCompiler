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
            String orientation, String discipline, String materialName, String materialRgba,
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
     *
     * <p>ASSUMPTION: Inactive elements (is_active=0) are excluded globally.
     * Currently used for TE IfcReinforcingBar (2,660 elements deferred to
     * IfcOpenShell Python). If a building type needs partial activation
     * per storey or per discipline, this filter must be refined.
     *
     * <p>ASSUMPTION: The storey column in I_Element_Extraction contains
     * spatial container names from IfcBuildingStorey. Infrastructure IFCs
     * (IFC4X3) use IfcFacilityPart (IfcRoadPart, IfcBridgePart, etc.)
     * instead — the extraction layer must map these to storey-equivalent
     * names before this reader can consume them.
     */
    public static Map<String, List<ExtractionElement>> readByStorey(
            Connection compConn, String buildingType) throws SQLException {

        String sql = """
                SELECT storey, ifc_class, element_ref, ordinal,
                       min_x, max_x, min_y, max_y, min_z, max_z,
                       orientation, discipline, material_name, material_rgba, M_Product_ID
                FROM I_Element_Extraction
                WHERE building_type = ? AND is_active = 1
                ORDER BY storey, ifc_class, ordinal
                """;

        Map<String, List<ExtractionElement>> result = new LinkedHashMap<>();
        int nullProductCount = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ExtractionElement e = new ExtractionElement(
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                            rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8),
                            rs.getDouble(9), rs.getDouble(10),
                            rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
                            rs.getString(15)
                    );
                    if (e.mProductId() == null || e.mProductId().isBlank()) {
                        nullProductCount++;
                    }
                    result.computeIfAbsent(e.storey(), k -> new ArrayList<>()).add(e);
                }
            }
        }
        // GUARD: NULL M_Product_ID means BOM leaf lines will have NULL child_product_id
        // → BOMWalker skips them → 0 placements at compile time. This is always a data
        // defect. Fail at the source — don't waste the entire pipeline building BOMs
        // from broken extraction data. Fix: run ExtractionPopulator or apply migration.
        if (nullProductCount > 0) {
            int total = result.values().stream().mapToInt(List::size).sum();
            throw new SQLException(String.format(
                    "[FAIL] %d/%d elements have NULL M_Product_ID in "
                    + "I_Element_Extraction for %s — pipeline aborted. "
                    + "Run ExtractionPopulator to fix.",
                    nullProductCount, total, buildingType));
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
