package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Ensures M_Product rows exist in the output BOM DB for every distinct
 * product referenced in the extraction.
 *
 * <p>Auto-derives intrinsic dimensions from the element's AABB (metres).
 * Idempotent — uses INSERT OR IGNORE so existing products are untouched.
 *
 * <p>Uses direct JDBC (not PO) because the output DB may lack reference
 * tables that PO beforeSave() hooks check.
 */
public class ProductRegistrar {

    /**
     * Register all distinct M_Product_ID values from extraction elements.
     *
     * @param bomConn  writable connection to output BOM DB
     * @param elements all extraction elements for the building
     * @return number of new products inserted
     */
    public static int ensureProducts(Connection bomConn,
                                     List<ExtractionElement> elements) throws SQLException {

        // Group by M_Product_ID, keeping first occurrence for dimensions
        Map<String, ExtractionElement> distinct = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            if (e.mProductId() != null && !e.mProductId().isBlank()) {
                distinct.putIfAbsent(e.mProductId(), e);
            }
        }

        String sql = """
                INSERT OR IGNORE INTO M_Product
                (product_id, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 'IFC_EXTRACTION', 1)
                """;

        int count = 0;
        try (PreparedStatement stmt = bomConn.prepareStatement(sql)) {
            for (Map.Entry<String, ExtractionElement> entry : distinct.entrySet()) {
                String productId = entry.getKey();
                ExtractionElement e = entry.getValue();

                // Derive product type from IFC class
                String productType = deriveProductType(e.ifcClass());

                // Intrinsic dimensions in metres
                double w = e.maxX() - e.minX();
                double d = e.maxY() - e.minY();
                double h = e.maxZ() - e.minZ();

                stmt.setString(1, productId);
                stmt.setString(2, productType);
                stmt.setDouble(3, w);
                stmt.setDouble(4, d);
                stmt.setDouble(5, h);
                stmt.setString(6, e.ifcClass());
                int rows = stmt.executeUpdate();
                if (rows > 0) count++;
            }
        }
        return count;
    }

    /**
     * Register assembly stub products (FLOOR, BUILDING) used as BOM
     * parent placeholders. is_active=0 (not placeable catalog items).
     */
    public static void ensureAssemblyStub(Connection bomConn,
                                          String productId, String productType) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO M_Product
                (product_id, product_type, width, depth, height,
                 extracted_from, is_active)
                VALUES (?, ?, 0.001, 0.001, 0.001, 'BOM_ASSEMBLY', 0)
                """;
        try (PreparedStatement stmt = bomConn.prepareStatement(sql)) {
            stmt.setString(1, productId);
            stmt.setString(2, productType);
            stmt.executeUpdate();
        }
    }

    /**
     * Derive M_Product.product_type from IFC class name.
     */
    static String deriveProductType(String ifcClass) {
        if (ifcClass == null) return "ELEMENT";
        return switch (ifcClass) {
            case "IfcWall", "IfcWallStandardCase" -> "WALL";
            case "IfcSlab" -> "SLAB";
            case "IfcDoor" -> "DOOR";
            case "IfcWindow" -> "WINDOW";
            case "IfcColumn" -> "COLUMN";
            case "IfcBeam" -> "BEAM";
            case "IfcRoof" -> "ROOF";
            case "IfcStair", "IfcStairFlight" -> "STAIR";
            case "IfcRailing" -> "RAILING";
            case "IfcCurtainWall" -> "CURTAIN_WALL";
            case "IfcPlate" -> "PLATE";
            case "IfcMember" -> "MEMBER";
            case "IfcBuildingElementProxy" -> "PROXY";
            default -> "ELEMENT";
        };
    }
}
