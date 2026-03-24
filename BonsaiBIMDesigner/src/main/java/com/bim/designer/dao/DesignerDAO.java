package com.bim.designer.dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Data Access Object for the BIM Designer module.
 *
 * <p>Follows the iDempiere DAO pattern: connection-per-call, caller owns
 * lifecycle. Never closes connections — the caller's try-with-resources does.
 *
 * <p>This DAO reads from two sources:
 * <ul>
 *   <li>{@code BOM.db} — C_DocType (building types), m_bom (BOM headers),
 *       m_bom_line (BOM children), M_Product_Category (room types)</li>
 *   <li>{@code component_library.db} — M_Product (product catalog)</li>
 * </ul>
 *
 * <p>The DAO is read-only for the designer's query path. Writes go through
 * the existing PO layer (MBOM, MBOMLine) which enforces EntityType guards.
 */
public class DesignerDAO {

    private static final Logger LOG = Logger.getLogger(DesignerDAO.class.getName());

    private final Connection bomConn;

    public DesignerDAO(Connection bomConn) {
        this.bomConn = bomConn;
    }

    // ── Building types (C_DocType) ──────────────────────────────────

    /**
     * Resolve buildingId → building_type Name from C_DocType.
     * Returns null if not found.
     */
    public String resolveBuildingType(String buildingId) throws SQLException {
        String sql = "SELECT Name FROM C_DocType WHERE C_DocType_ID = ? OR Name = ?";
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            ps.setString(2, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("Name") : null;
            }
        }
    }

    /** Active building types from C_DocType, with AABB from BUILDING-level m_bom. */
    public List<BuildingTypeRow> listBuildingTypes() throws SQLException {
        String sql = """
                SELECT d.C_DocType_ID, d.ProjectName, d.Name,
                       d.DocBaseType, d.DocSubType, d.IsActive,
                       d.ExpectedElements,
                       COALESCE(b.aabb_width_mm, 0)  AS aabb_width_mm,
                       COALESCE(b.aabb_depth_mm, 0)  AS aabb_depth_mm,
                       COALESCE(b.aabb_height_mm, 0) AS aabb_height_mm
                FROM C_DocType d
                LEFT JOIN m_bom b ON b.doc_sub_type = d.DocSubType
                  AND b.doc_base_type = d.DocBaseType
                  AND b.bom_type = 'BUILDING' AND b.is_active = 1
                WHERE d.IsActive = 1
                ORDER BY d.SeqNo
                """;
        List<BuildingTypeRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new BuildingTypeRow(
                        rs.getString("C_DocType_ID"),
                        rs.getString("ProjectName"),
                        rs.getString("Name"),
                        rs.getString("DocBaseType"),
                        rs.getString("DocSubType"),
                        rs.getInt("ExpectedElements"),
                        rs.getDouble("aabb_width_mm"),
                        rs.getDouble("aabb_depth_mm"),
                        rs.getDouble("aabb_height_mm")
                ));
            }
        }
        return rows;
    }

    /** Single building type by projectName (buildingId). */
    public BuildingTypeRow getBuildingType(String buildingId) throws SQLException {
        String sql = """
                SELECT d.C_DocType_ID, d.ProjectName, d.Name,
                       d.DocBaseType, d.DocSubType,
                       d.ExpectedElements,
                       COALESCE(b.aabb_width_mm, 0)  AS aabb_width_mm,
                       COALESCE(b.aabb_depth_mm, 0)  AS aabb_depth_mm,
                       COALESCE(b.aabb_height_mm, 0) AS aabb_height_mm
                FROM C_DocType d
                LEFT JOIN m_bom b ON b.doc_sub_type = d.DocSubType
                  AND b.doc_base_type = d.DocBaseType
                  AND b.bom_type = 'BUILDING' AND b.is_active = 1
                WHERE d.ProjectName = ?
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, buildingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BuildingTypeRow(
                            rs.getString("C_DocType_ID"),
                            rs.getString("ProjectName"),
                            rs.getString("Name"),
                            rs.getString("DocBaseType"),
                            rs.getString("DocSubType"),
                            rs.getInt("ExpectedElements"),
                            rs.getDouble("aabb_width_mm"),
                            rs.getDouble("aabb_depth_mm"),
                            rs.getDouble("aabb_height_mm")
                    );
                }
            }
        }
        return null;
    }

    // ── BOM categories ──────────────────────────────────────────────

    /** Distinct BOM categories for a given DocSubType. */
    public List<CategoryRow> listCategories(String docSubType) throws SQLException {
        String sql = """
                SELECT m_product_category_id, bom_type, COUNT(*) AS bom_count
                FROM m_bom
                WHERE doc_sub_type = ? AND is_active = 1
                  AND m_product_category_id IS NOT NULL
                GROUP BY m_product_category_id, bom_type
                ORDER BY m_product_category_id
                """;
        List<CategoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CategoryRow(
                            rs.getString("m_product_category_id"),
                            rs.getString("bom_type"),
                            rs.getInt("bom_count")
                    ));
                }
            }
        }
        return rows;
    }

    // ── Segments (infra FLOOR-level BOMs) ───────────────────────────

    /**
     * Lists FLOOR-level BOMs (segments) for a BUILDING bom_id.
     * Each segment includes its m_product_category_id and the disciplines
     * (SET-level children m_product_category_ids).
     *
     * // Implementing INFRA_DESIGNER_SRS.md §0.2 — Witness: W-INFRA-SEG-1
     */
    public List<SegmentRow> listSegments(String buildingBomId) throws SQLException {
        // Step 1: find FLOOR-level BOMs that are children of the BUILDING BOM
        String floorSQL = """
                SELECT f.bom_id, f.bom_name, f.m_product_category_id,
                       (SELECT COUNT(*) FROM m_bom_line fl
                        WHERE fl.bom_id IN (
                            SELECT s.bom_id FROM m_bom s
                            WHERE s.bom_type = 'SET' AND s.is_active = 1
                              AND EXISTS (SELECT 1 FROM m_bom_line sl
                                          WHERE sl.bom_id = ? AND sl.child_product_id = f.bom_id)
                        )) AS element_count
                FROM m_bom f
                JOIN m_bom_line bl ON bl.bom_id = ? AND bl.child_product_id = f.bom_id
                WHERE f.bom_type = 'FLOOR' AND f.is_active = 1
                ORDER BY f.bom_id
                """;
        List<SegmentRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(floorSQL)) {
            ps.setString(1, buildingBomId);
            ps.setString(2, buildingBomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String bomId = rs.getString("bom_id");
                    String name = rs.getString("bom_name");
                    String category = rs.getString("m_product_category_id");
                    int elemCount = rs.getInt("element_count");
                    List<String> disciplines = loadDisciplines(bomId);
                    rows.add(new SegmentRow(bomId, name, category, elemCount, disciplines));
                }
            }
        }
        return rows;
    }

    /** Load SET-level m_product_category_ids (disciplines) for a FLOOR bom_id. */
    private List<String> loadDisciplines(String floorBomId) throws SQLException {
        String sql = """
                SELECT DISTINCT s.m_product_category_id
                FROM m_bom s
                JOIN m_bom_line bl ON bl.bom_id = ? AND bl.child_product_id = s.bom_id
                WHERE s.bom_type = 'SET' AND s.is_active = 1
                ORDER BY s.m_product_category_id
                """;
        List<String> disciplines = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, floorBomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    disciplines.add(rs.getString("m_product_category_id"));
                }
            }
        }
        return disciplines;
    }

    public record SegmentRow(
            String bomId, String name, String bomCategory,
            int elementCount, List<String> disciplines
    ) {}

    // ── BOM tree walk (for preview) ─────────────────────────────────

    /** BOM header by bom_id. */
    public BomHeaderRow getBomHeader(String bomId) throws SQLException {
        String sql = """
                SELECT bom_id, bom_name, bom_type, m_product_category_id,
                       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
                       entity_type
                FROM m_bom WHERE bom_id = ?
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BomHeaderRow(
                            rs.getString("bom_id"),
                            rs.getString("bom_name"),
                            rs.getString("bom_type"),
                            rs.getString("m_product_category_id"),
                            rs.getInt("aabb_width_mm"),
                            rs.getInt("aabb_depth_mm"),
                            rs.getInt("aabb_height_mm"),
                            rs.getString("entity_type")
                    );
                }
            }
        }
        return null;
    }

    /** BOM lines for a given bom_id. */
    public List<BomLineRow> getBomLines(String bomId) throws SQLException {
        String sql = """
                SELECT bom_child_id, bom_id, child_product_id,
                       component_type, dx, dy, dz,
                       allocated_width_mm, allocated_depth_mm, allocated_height_mm,
                       storey, element_ref, verb_ref, sequence
                FROM m_bom_line
                WHERE bom_id = ? AND is_active = 1
                ORDER BY sequence, bom_child_id
                """;
        List<BomLineRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, bomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new BomLineRow(
                            rs.getInt("bom_child_id"),
                            rs.getString("bom_id"),
                            rs.getString("child_product_id"),
                            rs.getString("component_type"),
                            rs.getDouble("dx"),
                            rs.getDouble("dy"),
                            rs.getDouble("dz"),
                            rs.getInt("allocated_width_mm"),
                            rs.getInt("allocated_depth_mm"),
                            rs.getInt("allocated_height_mm"),
                            rs.getString("storey"),
                            rs.getString("element_ref"),
                            rs.getString("verb_ref"),
                            rs.getInt("sequence")
                    ));
                }
            }
        }
        return rows;
    }

    // ── Product catalog browse (component_library.db M_Product) ────

    /**
     * Browse products with search, category filter, and pagination.
     * Dimensions are stored in metres in M_Product — converted to mm here.
     * In production, M_Product lives in component_library.db (ATTACHed).
     * In tests, it lives in the same in-memory DB.
     */
    public List<ProductRow> browseProducts(String search, String category,
            String buildingType, int offset, int limit) throws SQLException {

        StringBuilder sql = new StringBuilder("""
                SELECT product_id, product_type,
                       width * 1000 AS width_mm,
                       depth * 1000 AS depth_mm,
                       height * 1000 AS height_mm,
                       ifc_class, building_type
                FROM M_Product
                WHERE is_active = 1
                """);
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, search, category, buildingType);
        sql.append(" ORDER BY product_type, product_id LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<ProductRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ProductRow(
                            rs.getString("product_id"),
                            rs.getString("product_type"),
                            rs.getDouble("width_mm"),
                            rs.getDouble("depth_mm"),
                            rs.getDouble("height_mm"),
                            rs.getString("ifc_class"),
                            rs.getString("building_type")
                    ));
                }
            }
        }
        return rows;
    }

    /** Total count matching the same filters (for pagination). */
    public int countProducts(String search, String category,
            String buildingType) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM M_Product WHERE is_active = 1");
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, search, category, buildingType);

        try (PreparedStatement ps = bomConn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Per-category counts for the sidebar (matching search + buildingType filters). */
    public List<CategoryCountRow> categoryCounts(String search,
            String buildingType) throws SQLException {

        StringBuilder sql = new StringBuilder("""
                SELECT product_type, COUNT(*) AS cnt
                FROM M_Product WHERE is_active = 1
                """);
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql.append(" AND product_id LIKE ?");
            params.add("%" + search.trim() + "%");
        }
        if (buildingType != null && !buildingType.isBlank()) {
            sql.append(" AND building_type = ?");
            params.add(buildingType);
        }
        sql.append(" GROUP BY product_type ORDER BY product_type");

        List<CategoryCountRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CategoryCountRow(
                            rs.getString("product_type"),
                            rs.getInt("cnt")
                    ));
                }
            }
        }
        return rows;
    }

    private void appendFilters(StringBuilder sql, List<Object> params,
            String search, String category, String buildingType) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND product_id LIKE ?");
            params.add("%" + search.trim() + "%");
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND product_type = ?");
            params.add(category);
        }
        if (buildingType != null && !buildingType.isBlank()) {
            sql.append(" AND building_type = ?");
            params.add(buildingType);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String s) ps.setString(i + 1, s);
            else if (p instanceof Integer n) ps.setInt(i + 1, n);
            else ps.setObject(i + 1, p);
        }
    }

    // ── Row records ─────────────────────────────────────────────────

    public record BuildingTypeRow(
            String docTypeId, String projectName, String name,
            String docBaseType, String docSubType,
            int expectedElements,
            double aabbWidthMm, double aabbDepthMm, double aabbHeightMm
    ) {}

    public record CategoryRow(String categoryName, String bomType, int bomCount) {}

    public record BomHeaderRow(
            String bomId, String bomName, String bomType, String bomCategory,
            int aabbWidthMm, int aabbDepthMm, int aabbHeightMm,
            String entityType
    ) {}

    public record BomLineRow(
            int bomChildId, String bomId, String childProductId,
            String componentType,
            double dx, double dy, double dz,
            int allocatedWidthMm, int allocatedDepthMm, int allocatedHeightMm,
            String storey, String elementRef, String verbRef, int sequence
    ) {}

    public record ProductRow(
            String productId, String productType,
            double widthMm, double depthMm, double heightMm,
            String ifcClass, String buildingType
    ) {}

    public record CategoryCountRow(String productType, int count) {}

    // ── Selection Cascade (BBC.md §3.5) ─────────────────────────────

    /**
     * BBC.md §3.5 Selection Cascade — find SET BOMs matching a room slot.
     *
     * <p>Five-tier priority:
     * <ol>
     *   <li>Scope: m_product_category_id = room's category</li>
     *   <li>Fit: SET AABB ≤ slot AABB (AABB=0 = universal fit)</li>
     *   <li>Rank: largest volume first</li>
     *   <li>Tiebreak: seq_no ascending</li>
     * </ol>
     *
     * // Implementing BBC.md §3.5, GENERATIVE_ROOM_SRS.md §3 — Witness: W-GEN-1
     */
    public List<SetMatchRow> findMatchingSets(String category,
            int slotWidthMm, int slotDepthMm, int slotHeightMm) throws SQLException {
        String sql = """
                SELECT b.bom_id, b.bom_name, b.m_product_category_id,
                       b.aabb_width_mm, b.aabb_depth_mm, b.aabb_height_mm,
                       (SELECT COUNT(*) FROM m_bom_line l
                        WHERE l.bom_id = b.bom_id AND l.is_active = 1
                          AND l.component_type != 'PHANTOM') AS child_count
                FROM m_bom b
                WHERE b.m_product_category_id = ?
                  AND b.bom_type = 'SET'
                  AND b.is_active = 1
                  AND (b.aabb_width_mm <= ? OR b.aabb_width_mm = 0)
                  AND (b.aabb_depth_mm <= ? OR b.aabb_depth_mm = 0)
                  AND (b.aabb_height_mm <= ? OR b.aabb_height_mm = 0)
                ORDER BY (CAST(b.aabb_width_mm AS INTEGER) * CAST(b.aabb_depth_mm AS INTEGER)
                          * CAST(b.aabb_height_mm AS INTEGER)) DESC,
                         b.seq_no ASC
                """;
        List<SetMatchRow> rows = new ArrayList<>();
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setInt(2, slotWidthMm);
            ps.setInt(3, slotDepthMm);
            ps.setInt(4, slotHeightMm);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SetMatchRow(
                            rs.getString("bom_id"),
                            rs.getString("bom_name"),
                            rs.getString("m_product_category_id"),
                            rs.getInt("aabb_width_mm"),
                            rs.getInt("aabb_depth_mm"),
                            rs.getInt("aabb_height_mm"),
                            rs.getInt("child_count")
                    ));
                }
            }
        }
        return rows;
    }

    public record SetMatchRow(
            String bomId, String bomName, String bomCategory,
            int aabbWidthMm, int aabbDepthMm, int aabbHeightMm,
            int childCount
    ) {}

    // ── Find Similar (§25.3 — JEPA-inspired embedding similarity) ──

    /**
     * Find products similar to the given product, ranked by cosine similarity
     * of deterministic feature vectors built from existing columns.
     *
     * <p>Feature vector per product:
     * <ol>
     *   <li>Normalised dimensions: width, depth, height (divided by max in catalog)</li>
     *   <li>Aspect ratios: w/d, w/h (shape signature)</li>
     *   <li>product_type match: 1.0 if same type, 0.0 otherwise</li>
     *   <li>ifc_class match: 1.0 if same class, 0.0 otherwise</li>
     * </ol>
     *
     * <p>Cosine similarity is computed in Java, not SQL, because SQLite has no
     * vector operations. For 608 products this is < 1ms (brute-force is fine).
     *
     * // Implementing BIM_Designer_SRS.md §25.3 — Witness: W-EMB-SIM-1
     */
    public List<SimilarProductRow> findSimilar(String productId, int limit,
            double containerWidthMm, double containerDepthMm,
            double containerHeightMm) throws SQLException {

        // 1. Load target product
        ProductRow target = getProduct(productId);
        if (target == null) return List.of();

        // 2. Load all active products (excluding target)
        List<ProductRow> all = browseProducts(null, null, null, 0, Integer.MAX_VALUE);

        // 3. Find dimension maxima for normalisation
        double maxW = 1, maxD = 1, maxH = 1;
        for (ProductRow p : all) {
            if (p.widthMm() > maxW) maxW = p.widthMm();
            if (p.depthMm() > maxD) maxD = p.depthMm();
            if (p.heightMm() > maxH) maxH = p.heightMm();
        }

        // 4. Build target feature vector
        double[] targetVec = featureVector(target, maxW, maxD, maxH);

        // 5. Compute cosine similarity for each candidate
        List<SimilarProductRow> scored = new ArrayList<>();
        for (ProductRow p : all) {
            if (p.productId().equals(productId)) continue; // exclude self

            double[] candidateVec = featureVector(p, maxW, maxD, maxH);
            // Boost same product_type and ifc_class
            candidateVec[5] = target.productType().equals(p.productType()) ? 1.0 : 0.0;
            candidateVec[6] = target.ifcClass() != null && target.ifcClass().equals(p.ifcClass()) ? 1.0 : 0.0;
            // Recompute target vec with self-match = 1.0
            targetVec[5] = 1.0;
            targetVec[6] = 1.0;

            double similarity = cosineSimilarity(targetVec, candidateVec);
            scored.add(new SimilarProductRow(p, similarity));
        }

        // 6. Sort descending by similarity, take top N
        scored.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
        if (scored.size() > limit) scored = new ArrayList<>(scored.subList(0, limit));

        return scored;
    }

    /** Build a 7-element feature vector from product columns. */
    private double[] featureVector(ProductRow p, double maxW, double maxD, double maxH) {
        double normW = p.widthMm() / maxW;
        double normD = p.depthMm() / maxD;
        double normH = p.heightMm() / maxH;
        double aspectWD = p.depthMm() > 0 ? p.widthMm() / p.depthMm() : 1.0;
        double aspectWH = p.heightMm() > 0 ? p.widthMm() / p.heightMm() : 1.0;
        // Slots 5 and 6 are filled per-comparison (type match, class match)
        return new double[]{ normW, normD, normH, aspectWD, aspectWH, 0.0, 0.0 };
    }

    /** Cosine similarity between two vectors. */
    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.length; i++) {
            dot  += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }
        double denom = Math.sqrt(magA) * Math.sqrt(magB);
        return denom > 0 ? dot / denom : 0.0;
    }

    /** A product row with its similarity score to the query product. */
    public record SimilarProductRow(ProductRow product, double similarity) {}

    // ── Single product lookup (for placeItem) ───────────────────────

    /** Single product by product_id. Returns null if not found. */
    public ProductRow getProduct(String productId) throws SQLException {
        String sql = """
                SELECT product_id, product_type,
                       width * 1000 AS width_mm,
                       depth * 1000 AS depth_mm,
                       height * 1000 AS height_mm,
                       ifc_class, building_type
                FROM M_Product
                WHERE product_id = ? AND is_active = 1
                """;
        try (PreparedStatement ps = bomConn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ProductRow(
                            rs.getString("product_id"),
                            rs.getString("product_type"),
                            rs.getDouble("width_mm"),
                            rs.getDouble("depth_mm"),
                            rs.getDouble("height_mm"),
                            rs.getString("ifc_class"),
                            rs.getString("building_type")
                    );
                }
            }
        }
        return null;
    }
}
