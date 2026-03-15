package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Ensures M_Product rows exist in the output BOM DB for every distinct
 * product referenced in the extraction.
 *
 * <h3>LESSON LEARNED (2026-03-15): The geometry link must be self-creating</h3>
 * <p>M_Product_Image (M_Product_ID → geometry_hash) in component_library.db
 * was initially expected to be populated by a separate migration. That migration
 * targeted the wrong table and was never applied — leaving the compiler unable
 * to resolve geometry for any product. Fix: {@link #ensureProductImages} now
 * auto-creates M_Product_Image from deterministic join of I_Element_Extraction
 * × I_Geometry_Map. No manual migration needed. No invention — pure join of
 * existing extracted data.
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
     * Ensure M_Product_Image rows exist in component_library.db for all
     * products with M_Product_ID in the extraction. Derives geometry_hash
     * from I_Geometry_Map via deterministic join on (building_type, element_ref).
     *
     * <p>This is the one-time extraction guarantee: every element in
     * I_Element_Extraction that has a M_Product_ID gets a corresponding
     * M_Product_Image row linking it to its geometry. The compiler's
     * resolveByProduct() path depends on this table existing.
     *
     * <p>Idempotent — uses INSERT OR IGNORE. Only writes, never deletes.
     *
     * @param compConn     writable connection to component_library.db
     * @param buildingType the building_type string (e.g. "Ifc4_SampleHouse")
     * @return number of new M_Product_Image rows inserted
     */
    public static int ensureProductImages(Connection compConn,
                                          String buildingType) throws SQLException {
        // Create table if not exists (first run)
        try (Statement stmt = compConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS M_Product_Image (
                        M_Product_ID  TEXT PRIMARY KEY,
                        geometry_hash TEXT NOT NULL,
                        up_axis       TEXT NOT NULL DEFAULT 'Z',
                        forward_axis  TEXT NOT NULL DEFAULT 'Y',
                        attachment_face TEXT NOT NULL DEFAULT 'CENTER'
                    )""");
        }

        // Deterministic join: I_Element_Extraction.M_Product_ID × I_Geometry_Map.geometry_hash
        // One geometry_hash per M_Product_ID (product type = one canonical shape).
        // Joins on (building_type, element_ref) — ordinal-independent.
        // TODO: Derive up_axis/forward_axis/attachment_face from extraction orientation
        // data rather than defaulting to Z/Y/CENTER. The orientation field in
        // I_Element_Extraction carries the IFC element rotation which determines
        // axis alignment. Currently defaults are safe for SH/DX residential elements.
        String sql = """
                INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
                SELECT e.M_Product_ID, g.geometry_hash
                FROM I_Element_Extraction e
                JOIN I_Geometry_Map g ON e.building_type = g.building_type
                    AND e.element_ref = g.element_ref
                WHERE e.building_type = ?
                    AND e.M_Product_ID IS NOT NULL
                    AND e.is_active = 1
                GROUP BY e.M_Product_ID
                """;

        int count = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            stmt.setString(1, buildingType);
            count = stmt.executeUpdate();
        }
        return count;
    }

    /**
     * Derive M_Product.product_type from IFC class name.
     *
     * <p>ASSUMPTION: This switch covers building IFC classes (IFC2x3/IFC4).
     * Infrastructure IFC4X3 entities (IfcCourse, IfcRail, IfcTrackElement,
     * IfcSign, IfcGeographicElement, IfcSurfaceFeature, IfcEarthworksFill)
     * are not mapped and will silently default to "ELEMENT". Extend this
     * switch before adding infrastructure support.
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
