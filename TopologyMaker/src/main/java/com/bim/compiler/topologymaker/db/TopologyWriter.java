package com.bim.compiler.topologymaker.db;

import com.bim.compiler.topologymaker.RoomCell;
import com.bim.compiler.topologymaker.SiteEnvelope;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes topology output to the library DB.
 *
 * <p>THREE-TABLE AUTHORITY compliance:
 * <ul>
 *   <li>ad_room_boundary — spatial coordinates (DERIVED_MM coordinate_frame)</li>
 *   <li>ad_bom / ad_bom_child — prefab BOM hierarchy (FLOOR + UNIT layers)</li>
 *   <li>ad_building_registry — one new row per generated building</li>
 * </ul>
 * Never writes to ad_element_rule or ad_product_dim.
 *
 * <p>All writes for one order are in a single transaction. Rollback on any failure.
 */
public final class TopologyWriter implements AutoCloseable {

    private static final String LIBRARY_DB_PATH = "library/component_library.db";
    private static final String STOREY_LABEL = "Ground Floor";
    private static final String COORDINATE_FRAME = "DERIVED_MM";

    private final Connection conn;

    public TopologyWriter(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.conn.setAutoCommit(false);
    }

    public TopologyWriter() throws SQLException {
        this(LIBRARY_DB_PATH);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Write room cells as ad_room_boundary rows.
     *
     * @param buildingType  Value for ad_room_boundary.building_type (= orderId)
     * @param typologyId    Recorded in extracted_from column
     * @param cells         Room cells from GridStrategy
     * @return Number of rows inserted
     * @throws SQLException on any DB error — caller should rollback
     */
    public int writeRoomBoundaries(String buildingType, String typologyId,
                                   List<RoomCell> cells) throws SQLException {
        String sql = "INSERT OR IGNORE INTO ad_room_boundary " +
                     "(building_type, storey, room_name, room_type, " +
                     " grid_min_x, grid_max_x, grid_min_y, grid_max_y, " +
                     " min_x_mm, max_x_mm, min_y_mm, max_y_mm, " +
                     " extracted_from, coordinate_frame, is_active) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,1)";

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (RoomCell cell : cells) {
                stmt.setString(1, buildingType);
                stmt.setString(2, STOREY_LABEL);
                stmt.setString(3, cell.roomName());
                stmt.setString(4, cell.roomType());
                // Grid labels: use mm values as string labels (no grid defined)
                stmt.setString(5, String.valueOf(cell.minXMm()));
                stmt.setString(6, String.valueOf(cell.maxXMm()));
                stmt.setString(7, String.valueOf(cell.minYMm()));
                stmt.setString(8, String.valueOf(cell.maxYMm()));
                stmt.setInt(9,  cell.minXMm());
                stmt.setInt(10, cell.maxXMm());
                stmt.setInt(11, cell.minYMm());
                stmt.setInt(12, cell.maxYMm());
                stmt.setString(13, "TOPOLOGY_MAKER:" + typologyId);
                stmt.setString(14, COORDINATE_FRAME);
                inserted += stmt.executeUpdate();
            }
        }
        return inserted;
    }

    /**
     * Write a PrefabBom (FLOOR or UNIT level) to ad_bom + ad_bom_child.
     * Idempotent: uses INSERT OR IGNORE on bom_id PK.
     *
     * @return 1 if ad_bom row was new; 0 if already existed
     */
    public int writeBom(PrefabBom bom) throws SQLException {
        String bomSql = "INSERT OR IGNORE INTO ad_bom " +
                        "(bom_id, bom_name, description, bom_type, group_by, is_active) " +
                        "VALUES (?,?,?,?,?,1)";
        int inserted;
        try (PreparedStatement stmt = conn.prepareStatement(bomSql)) {
            stmt.setString(1, bom.bomId());
            stmt.setString(2, bom.bomName());
            stmt.setString(3, bom.description());
            stmt.setString(4, bom.bomType());
            stmt.setString(5, "BUILDING");
            inserted = stmt.executeUpdate();
        }

        String childSql = "INSERT OR IGNORE INTO ad_bom_child " +
                          "(bom_id, child_bom_id, role, sequence, rotation_rule, " +
                          " fit_priority, min_space_mm, is_active) " +
                          "VALUES (?,?,?,?,?,?,?,1)";
        try (PreparedStatement stmt = conn.prepareStatement(childSql)) {
            for (PrefabBom.Child child : bom.children()) {
                stmt.setString(1, bom.bomId());
                stmt.setString(2, child.childBomId());
                stmt.setString(3, child.role());
                stmt.setInt(4, child.sequence());
                stmt.setString(5, "0");
                stmt.setInt(6, 10);
                stmt.setInt(7, child.minSpaceMm());
                stmt.executeUpdate();
            }
        }
        return inserted;
    }

    /**
     * Register the generated building in ad_building_registry.
     * Idempotent — INSERT OR IGNORE on building_id PK.
     *
     * @param orderId     Becomes building_id and part of DSL content
     * @param unitBomId   The top-level UNIT BOM id (for DSL reference)
     * @param site        Site envelope for DSL metadata
     */
    public void registerBuilding(String orderId, String unitBomId,
                                 SiteEnvelope site) throws SQLException {
        String dslContent = generateDsl(orderId, unitBomId, site);
        String outputPath = "DAGCompiler/lib/output/" + orderId + ".db";

        String sql = "INSERT OR IGNORE INTO ad_building_registry " +
                     "(building_id, building_name, building_type, dsl_content, " +
                     " output_db_path, provenance, doc_status, is_active) " +
                     "VALUES (?,?,?,?,?,'GENERATIVE','DR',1)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, orderId);
            stmt.setString(2, orderId.replace('_', ' '));
            stmt.setString(3, "RESIDENTIAL");
            stmt.setString(4, dslContent);
            stmt.setString(5, outputPath);
            stmt.executeUpdate();
        }
    }

    /** Commit the current transaction. */
    public void commit() throws SQLException {
        conn.commit();
    }

    /** Rollback the current transaction. */
    public void rollback() {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String generateDsl(String orderId, String unitBomId, SiteEnvelope site) {
        return String.format(
            "# Generated by TopologyMaker\n" +
            "building %s {\n" +
            "  type: RESIDENTIAL\n" +
            "  site_width_mm: %d\n" +
            "  site_depth_mm: %d\n" +
            "  num_bedrooms: %d\n" +
            "  has_porch: %s\n" +
            "  unit_bom: %s\n" +
            "}\n",
            orderId, site.widthMm(), site.depthMm(),
            site.numBedrooms(), site.hasPorch(),
            unitBomId
        );
    }

    // ── PrefabBom inner builder ────────────────────────────────────────────────

    /**
     * Builder for a FLOOR or UNIT level BOM row + its children.
     * All children reference other BOM IDs (sub-assemblies), not leaf products.
     */
    public static final class PrefabBom {

        /**
         * A child sub-assembly reference within a PrefabBom.
         *
         * @param childBomId   FK to ad_bom.bom_id
         * @param role         Semantic role label (e.g. "BED_1", "GF")
         * @param sequence     Processing order
         * @param minSpaceMm   Minimum space required in mm (0 = no constraint)
         */
        public record Child(String childBomId, String role, int sequence, int minSpaceMm) {}

        private final String bomId;
        private final String bomType;  // FLOOR or UNIT
        private final String bomName;
        private final String description;
        private final List<Child> children = new ArrayList<>();

        public PrefabBom(String bomId, String bomType, String description) {
            this.bomId = bomId;
            this.bomType = bomType;
            this.bomName = description;
            this.description = description;
        }

        /** Add a child sub-assembly. minSpaceMm = spatial hint for the resolver. */
        public PrefabBom addChild(String childBomId, String role,
                                   int sequence, int minSpaceMm) {
            children.add(new Child(childBomId, role, sequence, minSpaceMm));
            return this;
        }

        public String bomId()       { return bomId; }
        public String bomType()     { return bomType; }
        public String bomName()     { return bomName; }
        public String description() { return description; }
        public List<Child> children() { return List.copyOf(children); }
    }
}
