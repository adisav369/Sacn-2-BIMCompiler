package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;

import java.sql.*;
import java.util.*;

/**
 * Manages the M_Product lifecycle across component_library.db and BOM DBs.
 *
 * <h3>Product Catalog Strategy</h3>
 * <p>component_library.db is the <b>master product catalog</b> and source of
 * truth for product definitions, geometry, and orientation. Products are created
 * there first ({@link #ensureProductCatalog}) so they persist across BOM rebuilds
 * and can be reused across buildings (INSERT OR IGNORE).
 *
 * <p>The BOM DB ({@code *_BOM.db}) is for <b>spatial arrangement only</b>:
 * m_bom (structure) + m_bom_line (placement with dx/dy/dz). It should reference
 * products by ID, not own product definitions.
 *
 * <p>DEAD CODE: {@link #ensureProducts} still copies M_Product to the BOM DB
 * but BOMWalker was refactored (R7, 2026-03-16) to read from compConn
 * (component_library.db). The BOM DB copy is no longer read by any production
 * code. Pending removal — kept temporarily for backward compatibility of
 * single-arg BOMWalker constructor used by some tests.
 *
 * <p>Flow: I_Element_Extraction → M_Product (component_library.db, master)
 *       → BOMWalker reads via compConn (no BOM DB copy needed)
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
     * Create M_Product rows in component_library.db as the persistent catalog.
     * Products are created here first so they survive BOM rebuilds and can be
     * reused across buildings. If a product already exists (same product_id
     * from a prior extraction or another building), it is reused as-is.
     *
     * <p>Idempotent — INSERT OR IGNORE. Only new products are added.
     *
     * @param compConn     writable connection to component_library.db
     * @param elements     all extraction elements for the building
     * @param buildingType the building_type string (for logging)
     * @return number of new products inserted (0 = all reused)
     */
    public static int ensureProductCatalog(Connection compConn,
                                           List<ExtractionElement> elements,
                                           String buildingType) throws SQLException {
        // Create table if not exists (first run)
        try (Statement stmt = compConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS M_Product (
                        product_id        TEXT PRIMARY KEY,
                        product_type      TEXT NOT NULL,
                        width             REAL NOT NULL,
                        depth             REAL NOT NULL,
                        height            REAL NOT NULL,
                        ifc_class         TEXT,
                        extracted_from    TEXT NOT NULL DEFAULT 'IFC_EXTRACTION',
                        is_active         INTEGER DEFAULT 1,
                        building_type     TEXT
                    )""");
        }

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
                 ifc_class, extracted_from, is_active, building_type)
                VALUES (?, ?, ?, ?, ?, ?, 'IFC_EXTRACTION', 1, ?)
                """;

        int count = 0;
        int reused = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            for (Map.Entry<String, ExtractionElement> entry : distinct.entrySet()) {
                ExtractionElement e = entry.getValue();
                stmt.setString(1, entry.getKey());
                stmt.setString(2, deriveProductType(e.ifcClass()));
                stmt.setDouble(3, e.maxX() - e.minX());
                stmt.setDouble(4, e.maxY() - e.minY());
                stmt.setDouble(5, e.maxZ() - e.minZ());
                stmt.setString(6, e.ifcClass());
                stmt.setString(7, buildingType);
                int rows = stmt.executeUpdate();
                if (rows > 0) count++;
                else reused++;
            }
        }
        if (reused > 0) {
            System.out.printf("[ProductRegistrar] %d products reused from catalog, %d new%n",
                    reused, count);
        }
        return count;
    }

    /**
     * Copy M_Product rows from component_library.db catalog to the BOM DB.
     * The BOM DB must be self-contained for compilation — the compiler
     * reads M_Product from the BOM DB, not from component_library.db.
     *
     * <p>Only copies products referenced by the current building's extraction.
     * Uses INSERT OR IGNORE — safe for re-runs.
     *
     * @param bomConn  writable connection to output BOM DB
     * @param compConn read connection to component_library.db
     * @param elements all extraction elements for the building
     * @return number of products copied to BOM DB
     */
    public static int ensureProducts(Connection bomConn, Connection compConn,
                                     List<ExtractionElement> elements) throws SQLException {

        // Group by M_Product_ID, keeping first occurrence for dimensions
        Map<String, ExtractionElement> distinct = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            if (e.mProductId() != null && !e.mProductId().isBlank()) {
                distinct.putIfAbsent(e.mProductId(), e);
            }
        }

        // Try to copy from component_library.db catalog first (reuse)
        String copyFromCatalog = """
                SELECT product_id, product_type, width, depth, height,
                       ifc_class, extracted_from, is_active
                FROM M_Product WHERE product_id = ?
                """;
        String insertIntoBom = """
                INSERT OR IGNORE INTO M_Product
                (product_id, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        int count = 0;
        boolean catalogExists = tableExists(compConn, "M_Product");

        try (PreparedStatement readStmt = catalogExists
                    ? compConn.prepareStatement(copyFromCatalog) : null;
             PreparedStatement writeStmt = bomConn.prepareStatement(insertIntoBom)) {

            for (Map.Entry<String, ExtractionElement> entry : distinct.entrySet()) {
                String productId = entry.getKey();
                ExtractionElement e = entry.getValue();
                boolean copied = false;

                // Try catalog first — reuse existing product definition
                if (readStmt != null) {
                    readStmt.setString(1, productId);
                    try (ResultSet rs = readStmt.executeQuery()) {
                        if (rs.next()) {
                            writeStmt.setString(1, rs.getString(1));
                            writeStmt.setString(2, rs.getString(2));
                            writeStmt.setDouble(3, rs.getDouble(3));
                            writeStmt.setDouble(4, rs.getDouble(4));
                            writeStmt.setDouble(5, rs.getDouble(5));
                            writeStmt.setString(6, rs.getString(6));
                            writeStmt.setString(7, rs.getString(7));
                            writeStmt.setInt(8, rs.getInt(8));
                            int rows = writeStmt.executeUpdate();
                            if (rows > 0) { count++; copied = true; }
                        }
                    }
                }

                // Fallback: create from extraction data (backward compatibility)
                if (!copied) {
                    writeStmt.setString(1, productId);
                    writeStmt.setString(2, deriveProductType(e.ifcClass()));
                    writeStmt.setDouble(3, e.maxX() - e.minX());
                    writeStmt.setDouble(4, e.maxY() - e.minY());
                    writeStmt.setDouble(5, e.maxZ() - e.minZ());
                    writeStmt.setString(6, e.ifcClass());
                    writeStmt.setString(7, "IFC_EXTRACTION");
                    writeStmt.setInt(8, 1);
                    int rows = writeStmt.executeUpdate();
                    if (rows > 0) count++;
                }
            }
        }
        return count;
    }

    private static boolean tableExists(Connection conn, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
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

        // R17: Join M_Product → I_Geometry_Map (I_Element_Extraction removed).
        // M_Product.product_id = element_ref by extraction convention.
        // One geometry_hash per product (product type = one canonical shape).
        String sql = """
                INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
                SELECT p.product_id, g.geometry_hash
                FROM M_Product p
                JOIN I_Geometry_Map g ON g.element_ref = p.product_id
                WHERE p.building_type = ?
                    AND p.is_active = 1
                GROUP BY p.product_id
                """;

        int count = 0;
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            stmt.setString(1, buildingType);
            count = stmt.executeUpdate();
        }
        return count;
    }

    /**
     * Count products in M_Product catalog that have NO geometry link
     * in M_Product_Image. These products will produce 0 placements at
     * compile time because resolveByProduct() requires geometry_hash.
     *
     * <p>R17: Queries M_Product (not I_Element_Extraction, which was removed).
     *
     * @param compConn     read connection to component_library.db
     * @param buildingType the building_type string
     * @return number of distinct product_ids with no geometry_hash
     */
    public static int countUnlinkedProducts(Connection compConn,
                                            String buildingType) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT p.product_id)
                FROM M_Product p
                LEFT JOIN M_Product_Image i ON p.product_id = i.M_Product_ID
                WHERE p.building_type = ?
                    AND p.is_active = 1
                    AND i.M_Product_ID IS NULL
                """;
        try (PreparedStatement stmt = compConn.prepareStatement(sql)) {
            stmt.setString(1, buildingType);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
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
