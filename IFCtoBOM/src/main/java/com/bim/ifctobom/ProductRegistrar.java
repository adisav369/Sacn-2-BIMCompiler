package com.bim.ifctobom;

import com.bim.ifctobom.ExtractionReader.ExtractionElement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.*;

/**
 * Manages the M_Product lifecycle for BOM compilation.
 *
 * <h3>Product Catalog Strategy (S168)</h3>
 * <p>component_library.db is populated at <b>extraction time</b> by
 * {@code extractIFCtoDB.py --library}. IFCtoBOM is a <b>read-only consumer</b>
 * of the library. {@link #ensureProductCatalog} writes M_Product to ERP.db only.
 *
 * <p>The BOM DB ({@code *_BOM.db}) is for <b>spatial arrangement only</b>:
 * m_bom (structure) + m_bom_line (placement with dx/dy/dz). It should reference
 * products by ID, not own product definitions.
 *
 * <p>Flow: extractIFCtoDB.py → M_Product (component_library.db, extraction)
 *       → ensureProductCatalog → M_Product (ERP.db, runtime catalog)
 *       → BOMWalker reads via compConn (read-only)
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
     * Create M_Product rows in ERP.db (master catalog).
     *
     * <p>S168: component_library.db is now populated at extraction time by
     * extractIFCtoDB.py. IFCtoBOM is a read-only consumer of the library.
     * M_Product writes go ONLY to ERP.db.
     *
     * <p>Idempotent — INSERT OR IGNORE. Only new products are added.
     *
     * @param compConn     read-only connection to component_library.db (kept for signature compat)
     * @param discConn     writable connection to ERP.db (master catalog)
     * @param elements     all extraction elements for the building
     * @param buildingType the building_type string (for logging)
     * @return number of new products inserted (0 = all reused)
     */
    public static int ensureProductCatalog(Connection compConn, Connection discConn,
                                           List<ExtractionElement> elements,
                                           String buildingType) throws SQLException {

        // Group by M_Product_ID, keeping first occurrence for dimensions
        Map<String, ExtractionElement> distinct = new LinkedHashMap<>();
        for (ExtractionElement e : elements) {
            if (e.mProductId() != null && !e.mProductId().isBlank()) {
                distinct.putIfAbsent(e.mProductId(), e);
            }
        }

        // S168: Write ONLY to ERP.db — component_library.db is populated at extraction
        String sql = """
                INSERT OR IGNORE INTO M_Product
                (product_id, Value, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active, building_type, source_element_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'IFC_EXTRACTION', 1, ?, ?)
                """;

        int count = 0;
        int reused = 0;
        try (PreparedStatement discStmt = discConn.prepareStatement(sql)) {
            for (Map.Entry<String, ExtractionElement> entry : distinct.entrySet()) {
                ExtractionElement e = entry.getValue();
                String productId = entry.getKey();
                String productType = deriveProductType(e.ifcClass());
                double w = e.maxX() - e.minX();
                double d = e.maxY() - e.minY();
                double h = e.maxZ() - e.minZ();
                String ifcClass = e.ifcClass();
                String sourceElementRef = e.elementRef();

                discStmt.setString(1, productId);
                discStmt.setString(2, productId);
                discStmt.setString(3, productType);
                discStmt.setDouble(4, w);
                discStmt.setDouble(5, d);
                discStmt.setDouble(6, h);
                discStmt.setString(7, ifcClass);
                discStmt.setString(8, buildingType);
                discStmt.setString(9, sourceElementRef);
                int rows = discStmt.executeUpdate();
                if (rows > 0) count++;
                else reused++;
            }
        }
        if (reused > 0) {
            BIMLogger.fine("PRODUCT_REG", "{} products reused from catalog, {} new",
                    reused, count);
        }

        // Backfill M_Product_Category_ID from ifc_class → M_Product_Category.IFC_Class.
        // Implementing DISC_VALIDATION_DB_SRS.md §6.4 — Witness: W-DISC-CAT
        // Only on ERP.db (discConn) — component_library.db has no category column.
        {
            try (Statement stmt = discConn.createStatement()) {
                stmt.executeUpdate("""
                        UPDATE M_Product
                        SET M_Product_Category_ID = (
                            SELECT c.M_Product_Category_ID
                            FROM M_Product_Category c
                            WHERE c.IFC_Class = M_Product.ifc_class
                            LIMIT 1
                        )
                        WHERE M_Product_Category_ID IS NULL
                          AND ifc_class IS NOT NULL
                        """);
            }
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
        // Name = IFC family name from source_element_ref (human-readable).
        // Value = abstract product_id (machine key). BOM DB is spatial
        // arrangement for ARC/STR only — ERP.db is for DISC (§10.4.4).
        String copyFromCatalog = """
                SELECT Value, product_type, width, depth, height,
                       ifc_class, extracted_from, is_active, source_element_ref
                FROM M_Product WHERE Value = ?
                """;
        String insertIntoBom = """
                INSERT OR IGNORE INTO M_Product
                (product_id, Value, Name, product_type, width, depth, height,
                 ifc_class, extracted_from, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                            String srcRef = rs.getString(9); // source_element_ref
                            writeStmt.setString(1, val);
                            writeStmt.setString(2, val);  // Value = product_id (machine key)
                            writeStmt.setString(3, deriveName(srcRef, val)); // Name = IFC family
                            writeStmt.setString(4, rs.getString(2));
                            writeStmt.setDouble(5, rs.getDouble(3));
                            writeStmt.setDouble(6, rs.getDouble(4));
                            writeStmt.setDouble(7, rs.getDouble(5));
                            writeStmt.setString(8, rs.getString(6));
                            writeStmt.setString(9, rs.getString(7));
                            writeStmt.setInt(10, rs.getInt(8));
                            int rows = writeStmt.executeUpdate();
                            if (rows > 0) { count++; copied = true; }
                        }
                    }
                }

                // Fallback: create from extraction data (backward compatibility)
                if (!copied) {
                    writeStmt.setString(1, productId);
                    writeStmt.setString(2, productId);  // Value = product_id (machine key)
                    writeStmt.setString(3, deriveName(e.elementRef(), productId)); // Name = IFC family
                    writeStmt.setString(4, deriveProductType(e.ifcClass()));
                    writeStmt.setDouble(5, e.maxX() - e.minX());
                    writeStmt.setDouble(6, e.maxY() - e.minY());
                    writeStmt.setDouble(7, e.maxZ() - e.minZ());
                    writeStmt.setString(8, e.ifcClass());
                    writeStmt.setString(9, "IFC_EXTRACTION");
                    writeStmt.setInt(10, 1);
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
     * @param buildingType the building_type string (e.g. "SampleHouse")
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
     * Bridge source_element_ref for generative MEP products that have NULL values.
     * Looks up ad_element_mep_alias (match_field='element_name') to find the IFC
     * family name pattern, then resolves it against I_Geometry_Map in component_library.db.
     *
     * <p>This closes the LOD gap: generative products (TOILET, LIGHT, SINK, etc.)
     * are inserted by migration scripts without extraction data, so source_element_ref
     * is NULL. Without it, ensureProductImages cannot join to I_Geometry_Map and the
     * product gets no geometry_hash — invisible at compile time.
     *
     * <p>Idempotent — only updates rows where source_element_ref IS NULL.
     *
     * // Implementing DISC_VALIDATION_DB_SRS.md §6.12.4 §11 — Witness: W-LOD-BRIDGE
     *
     * @param compConn writable connection to component_library.db (I_Geometry_Map source)
     * @param discConn writable connection to ERP.db (M_Product to update)
     * @return number of products bridged
     */
    public static int bridgeSourceElementRef(Connection compConn,
                                             Connection discConn) throws SQLException {
        // Find generative products with NULL source_element_ref
        String findNulls = """
                SELECT p.product_id, a.match_value
                FROM M_Product p
                JOIN ad_element_mep_alias a
                  ON a.canonical_type = p.product_id
                 AND a.match_field = 'element_name'
                 AND a.is_active = 1
                WHERE p.source_element_ref IS NULL
                  AND p.is_active = 1
                ORDER BY a.priority ASC
                """;

        // For each product, find the best matching element_ref in I_Geometry_Map
        String findGeometry = """
                SELECT element_ref FROM I_Geometry_Map
                WHERE element_ref LIKE ?
                  AND building_type IS NOT NULL
                LIMIT 1
                """;

        String updateRef = """
                UPDATE M_Product SET source_element_ref = ?
                WHERE product_id = ? AND source_element_ref IS NULL
                """;

        int bridged = 0;
        // Track which products we've already bridged (first alias match wins)
        Set<String> done = new HashSet<>();

        try (PreparedStatement findStmt = discConn.prepareStatement(findNulls);
             PreparedStatement geoStmt = compConn.prepareStatement(findGeometry);
             PreparedStatement updateStmt = discConn.prepareStatement(updateRef);
             ResultSet rs = findStmt.executeQuery()) {

            while (rs.next()) {
                String productId = rs.getString(1);
                if (done.contains(productId)) continue;

                String aliasPattern = rs.getString(2); // e.g. '%Water Closet%'
                geoStmt.setString(1, aliasPattern);
                try (ResultSet geoRs = geoStmt.executeQuery()) {
                    if (geoRs.next()) {
                        String elementRef = geoRs.getString(1);
                        updateStmt.setString(1, elementRef);
                        updateStmt.setString(2, productId);
                        int rows = updateStmt.executeUpdate();
                        if (rows > 0) {
                            bridged++;
                            done.add(productId);
                            BIMLogger.fine("PRODUCT_REG",
                                    "LOD bridge: {} → {}", productId, elementRef);
                        }
                    }
                }
            }
        }
        if (bridged > 0) {
            BIMLogger.info("PRODUCT_REG",
                    "LOD bridge: {} generative products linked to geometry", bridged);
        }
        return bridged;
    }

    /**
     * Derive human-readable Name from IFC source_element_ref.
     * Strips dimension suffix (e.g. ":1370x600x1170mm") to keep the
     * IFC family name ("Furniture_Piano"). Falls back to product_id
     * if source_element_ref is null.
     */
    static String deriveName(String sourceElementRef, String fallback) {
        if (sourceElementRef == null || sourceElementRef.isBlank()) return fallback;
        // IFC element_ref format: "FamilyName:TypeName" or "FamilyName:TypeName:Dims"
        // Strip trailing dimension segment (digits, x, mm patterns)
        int colon = sourceElementRef.indexOf(':');
        if (colon > 0) {
            return sourceElementRef.substring(0, colon);
        }
        return sourceElementRef;
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
