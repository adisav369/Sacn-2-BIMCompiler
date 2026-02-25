package com.bim.compiler.topologymaker.db;

import com.bim.compiler.topologymaker.RoomCell;
import com.bim.compiler.topologymaker.SiteEnvelope;
import com.bim.compiler.topologymaker.po.M_AdBuildingRegistry;
import com.bim.compiler.topologymaker.po.M_AdRoomBoundary;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes topology output to the library DB.
 *
 * <p>THREE-TABLE AUTHORITY compliance:
 * <ul>
 *   <li>ad_room_boundary — spatial coordinates (DERIVED_MM coordinate_frame)</li>
 *   <li>m_bom / m_bom_line — prefab BOM hierarchy (FLOOR + UNIT layers)</li>
 *   <li>ad_building_registry — one new row per generated building</li>
 * </ul>
 * Never writes to ad_element_rule or ad_product_dim.
 *
 * <p>Room boundary and building registry writes delegate to M_ PO objects.
 * BOM writes use raw JDBC (m_bom is out of scope for this PO phase).
 *
 * <p>All writes for one order are in a single transaction. Rollback on any failure.
 */
public final class TopologyWriter implements AutoCloseable {

    private static final String LIBRARY_DB_PATH = "library/component_library.db";

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
     * Write room cells as ad_room_boundary rows via M_AdRoomBoundary.
     *
     * <p>The PO's beforeSave() enforces DERIVED_MM coordinate_frame and dimension
     * sanity. INSERT OR IGNORE handles the UNIQUE(building_type, storey, room_name)
     * constraint idempotently.
     *
     * @param buildingType  Value for ad_room_boundary.building_type (= orderId)
     * @param typologyId    Recorded in extracted_from column
     * @param cells         Room cells from GridStrategy
     * @return Number of room cells processed (not deduplicated)
     * @throws SQLException on any DB error — caller should rollback
     */
    public int writeRoomBoundaries(String buildingType, String typologyId,
                                   List<RoomCell> cells) throws SQLException {
        int written = 0;
        for (RoomCell cell : cells) {
            M_AdRoomBoundary b = M_AdRoomBoundary.fromCell(conn, buildingType, typologyId, cell);
            b.save();
            written++;
        }
        return written;
    }

    /**
     * Write a PrefabBom (FLOOR or UNIT level) to m_bom + m_bom_line.
     * Raw JDBC — m_bom is outside the PO phase scope.
     * Idempotent: uses INSERT OR IGNORE on bom_id PK.
     *
     * @return 1 if m_bom row was new; 0 if already existed
     */
    public int writeBom(PrefabBom bom) throws SQLException {
        String bomSql = "INSERT OR IGNORE INTO m_bom " +
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

        String childSql = "INSERT OR IGNORE INTO m_bom_line " +
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
     * Register the generated building in ad_building_registry via M_AdBuildingRegistry.
     *
     * <p>M_ beforeSave() defaults doc_status='DR' and provenance='GENERATIVE' if not set.
     * INSERT OR IGNORE handles duplicate orderId idempotently.
     *
     * @param orderId     Becomes building_id and part of DSL content
     * @param unitBomId   The top-level UNIT BOM id (for DSL reference)
     * @param site        Site envelope for DSL metadata
     */
    public void registerBuilding(String orderId, String unitBomId,
                                 SiteEnvelope site) throws SQLException {
        M_AdBuildingRegistry reg = new M_AdBuildingRegistry(conn);
        reg.setBuildingId(orderId);
        reg.setBuildingName(orderId.replace('_', ' '));
        reg.setBuildingType("RESIDENTIAL");
        reg.setDslContent(generateDsl(orderId, unitBomId, site));
        reg.setOutputDbPath("DAGCompiler/lib/output/" + orderId + ".db");
        reg.setProvenance("GENERATIVE");
        reg.setDocStatus("DR");
        reg.setIsActive(true);
        reg.save();
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
         * @param childBomId   FK to m_bom.bom_id
         * @param role         Semantic role label (e.g. "BED_1", "GF")
         * @param sequence     Processing order
         * @param minSpaceMm   Minimum space required in mm (0 = no constraint)
         */
        public record Child(String childBomId, String role, int sequence, int minSpaceMm) {}

        private final String bomId;
        private final String bomType;
        private final String bomName;
        private final String description;
        private final List<Child> children = new ArrayList<>();

        public PrefabBom(String bomId, String bomType, String description) {
            this.bomId = bomId;
            this.bomType = bomType;
            this.bomName = description;
            this.description = description;
        }

        public PrefabBom addChild(String childBomId, String role,
                                   int sequence, int minSpaceMm) {
            children.add(new Child(childBomId, role, sequence, minSpaceMm));
            return this;
        }

        public String bomId()         { return bomId; }
        public String bomType()       { return bomType; }
        public String bomName()       { return bomName; }
        public String description()   { return description; }
        public List<Child> children() { return List.copyOf(children); }
    }
}
