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
     * Create M_Product rows in both component_library.db and ERP.db.
     * Products are created in component_library.db (for M_Product_Image geometry join)
     * and ERP.db (authoritative master catalog for downstream readers).
     *
     * <p>Idempotent — INSERT OR IGNORE. Only new products are added.
     *
     * @param compConn     writable connection to component_library.db (geometry join)
     * @param discConn     writable connection to ERP.db (master catalog)
     * @param elements     all extraction elements for the building
     * @param buildingType the building_type string (for logging)
     * @return number of new products inserted (0 = all reused)
     */
    public static int ensureProductCatalog(Connection compConn, Connection discConn,
                                           List<ExtractionElement> elements,
                                           String buildingType) throws SQLException {
        // Create table if not exists (first run)
        // Implementing BBC.md §14.3 IDV-1 — Witness: W-TIER2-DDL
        try (Statement stmt = compConn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS M_Product (
                        M_Product_ID      INTEGER PRIMARY KEY AUTOINCREMENT,
                        product_id        TEXT NOT NULL UNIQUE,
                        Value             TEXT,
                        product_type      TEXT NOT NULL,
                        width             REAL NOT NULL,
                        depth             REAL NOT NULL,
                        height            REAL NOT NULL,
                        ifc_class         TEXT,
                        extracted_from    TEXT NOT NULL DEFAULT 'IFC_EXTRACTION',
                        is_active         INTEGER DEFAULT 1,
                        building_type     TEXT,
                        source_element_ref TEXT
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
                (product_id, Value, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active, building_type, source_element_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'IFC_EXTRACTION', 1, ?, ?)
                """;

        int count = 0;
        int reused = 0;
        // Dual-write: component_library.db (geometry join) + ERP.db (master catalog)
        try (PreparedStatement stmt = compConn.prepareStatement(sql);
             PreparedStatement discStmt = discConn.prepareStatement(sql)) {
            for (Map.Entry<String, ExtractionElement> entry : distinct.entrySet()) {
                ExtractionElement e = entry.getValue();
                String productId = entry.getKey();
                String productType = deriveProductType(e.ifcClass());
                double w = e.maxX() - e.minX();
                double d = e.maxY() - e.minY();
                double h = e.maxZ() - e.minZ();
                String ifcClass = e.ifcClass();
                // element_ref = raw IFC name (geometry bridge to I_Geometry_Map)
                String sourceElementRef = e.elementRef();

                // Write to component_library.db (for ensureProductImages geometry join)
                stmt.setString(1, productId);
                stmt.setString(2, productId);  // Value = product_id
                stmt.setString(3, productType);
                stmt.setDouble(4, w);
                stmt.setDouble(5, d);
                stmt.setDouble(6, h);
                stmt.setString(7, ifcClass);
                stmt.setString(8, buildingType);
                stmt.setString(9, sourceElementRef);
                int rows = stmt.executeUpdate();
                if (rows > 0) count++;
                else reused++;

                // Write to ERP.db (authoritative master catalog)
                discStmt.setString(1, productId);
                discStmt.setString(2, productId);  // Value = product_id
                discStmt.setString(3, productType);
                discStmt.setDouble(4, w);
                discStmt.setDouble(5, d);
                discStmt.setDouble(6, h);
                discStmt.setString(7, ifcClass);
                discStmt.setString(8, buildingType);
                discStmt.setString(9, sourceElementRef);
                discStmt.executeUpdate();
            }
        }
        if (reused > 0) {
            System.out.printf("[ProductRegistrar] %d products reused from catalog, %d new%n",
                    reused, count);
        }
        return count;
    }

    /**
     * Copy M_Product rows from ERP.db catalog to the BOM DB.
     * The BOM DB gets a local copy for backward compatibility — downstream
     * readers (BOMWalker, OrderLineWalker) read from ERP.db directly.
     *
     * <p>DEAD CODE per R7: BOMWalker reads from compConn (now ERP.db).
     * Kept for single-arg BOMWalker constructor used by some tests.
     *
     * <p>Only copies products referenced by the current building's extraction.
     * Uses INSERT OR IGNORE — safe for re-runs.
     *
     * @param bomConn  writable connection to output BOM DB
     * @param compConn read connection to ERP.db (master product catalog)
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
                SELECT Value, product_type, width, depth, height,
                       ifc_class, extracted_from, is_active
                FROM M_Product WHERE Value = ?
                """;
        String insertIntoBom = """
                INSERT OR IGNORE INTO M_Product
                (product_id, Value, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                            String val = rs.getString(1);
                            writeStmt.setString(1, val);
                            writeStmt.setString(2, val);  // Value = product_id
                            writeStmt.setString(3, rs.getString(2));
                            writeStmt.setDouble(4, rs.getDouble(3));
                            writeStmt.setDouble(5, rs.getDouble(4));
                            writeStmt.setDouble(6, rs.getDouble(5));
                            writeStmt.setString(7, rs.getString(6));
                            writeStmt.setString(8, rs.getString(7));
                            writeStmt.setInt(9, rs.getInt(8));
                            int rows = writeStmt.executeUpdate();
                            if (rows > 0) { count++; copied = true; }
                        }
                    }
                }

                // Fallback: create from extraction data (backward compatibility)
                if (!copied) {
                    writeStmt.setString(1, productId);
                    writeStmt.setString(2, productId);  // Value = product_id
                    writeStmt.setString(3, deriveProductType(e.ifcClass()));
                    writeStmt.setDouble(4, e.maxX() - e.minX());
                    writeStmt.setDouble(5, e.maxY() - e.minY());
                    writeStmt.setDouble(6, e.maxZ() - e.minZ());
                    writeStmt.setString(7, e.ifcClass());
                    writeStmt.setString(8, "IFC_EXTRACTION");
                    writeStmt.setInt(9, 1);
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
                (product_id, Value, product_type, width, depth, height,
                 extracted_from, is_active)
                VALUES (?, ?, ?, 0.001, 0.001, 0.001, 'BOM_ASSEMBLY', 0)
                """;
        try (PreparedStatement stmt = bomConn.prepareStatement(sql)) {
            stmt.setString(1, productId);
            stmt.setString(2, productId);  // Value = product_id
            stmt.setString(3, productType);
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

        // R17+DV033: Join M_Product → I_Geometry_Map via source_element_ref.
        // source_element_ref = raw IFC name (geometry key in I_Geometry_Map).
        // product_id/Value = abstract catalog name (used as M_Product_Image.M_Product_ID).
        // Fallback: if source_element_ref is NULL (pre-DV033 data), use Value.
        // One geometry_hash per product (product type = one canonical shape).
        String sql = """
                INSERT OR IGNORE INTO M_Product_Image (M_Product_ID, geometry_hash)
                SELECT p.Value, g.geometry_hash
                FROM M_Product p
                JOIN I_Geometry_Map g ON g.element_ref = COALESCE(p.source_element_ref, p.Value)
                WHERE p.building_type = ?
                    AND p.is_active = 1
                GROUP BY p.Value
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
                SELECT COUNT(DISTINCT p.Value)
                FROM M_Product p
                LEFT JOIN M_Product_Image i ON p.Value = i.M_Product_ID
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
