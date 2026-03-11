package com.bim.compiler.topologymaker.db;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import com.bim.cobol.verb.ClearVarianceVerb;
import com.bim.cobol.verb.ComposePrefabBomVerb;
import com.bim.cobol.verb.FillBuffersVerb;
import com.bim.compiler.topologymaker.RoomCell;
import com.bim.compiler.topologymaker.SiteEnvelope;
import com.bim.compiler.topologymaker.po.MOrder;
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
 *   <li>c_order — one new row per generated building</li>
 * </ul>
 * Never writes to c_orderline or ad_product_dim.
 *
 * <p>Room boundary and building registry writes delegate to M_ PO objects.
 * BOM writes use raw JDBC (m_bom is out of scope for this PO phase).
 *
 * <p>All writes for one order are in a single transaction. Rollback on any failure.
 */
public final class TopologyWriter implements AutoCloseable {

    private static final String DB_PATH = "library/BOM.db";

    private final Connection conn;

    public TopologyWriter(String dbPath) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        this.conn.setAutoCommit(false);
    }

    public TopologyWriter() throws SQLException {
        this(DB_PATH);
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
        // Build verb line: COMPOSE PREFAB BOM <id> NAME <name> DESC <desc> TYPE <type> [CHILD ...]
        StringBuilder line = new StringBuilder();
        line.append("COMPOSE PREFAB BOM ").append(bom.bomId())
            .append(" NAME \"").append(bom.bomName()).append('"')
            .append(" DESC \"").append(bom.description()).append('"')
            .append(" TYPE ").append(bom.bomType());

        for (PrefabBom.Child child : bom.children()) {
            line.append(" CHILD ").append(child.childBomId())
                .append(" ROLE ").append(child.role())
                .append(" SEQ ").append(child.sequence())
                .append(" MIN_SPACE ").append(child.minSpaceMm());
        }

        VerbContext ctx = VerbContext.ofBom(conn);
        VerbResult<?> result = VerbRegistry.createDefault().dispatch(ctx, line.toString());
        if (!result.pass())
            throw new SQLException("COMPOSE PREFAB BOM failed: " + result.summary());

        ComposePrefabBomVerb.ComposePrefabBomPayload p =
            (ComposePrefabBomVerb.ComposePrefabBomPayload) result.payload();
        return p.bomInserted() ? 1 : 0;
    }

    /**
     * Register the generated building in c_order via MOrder.
     *
     * <p>M_ beforeSave() defaults doc_status='DR' and provenance='GENERATIVE' if not set.
     * INSERT OR IGNORE handles duplicate orderId idempotently.
     *
     * @param orderId     Becomes C_Order_ID and part of DSL content
     * @param unitBomId   The top-level UNIT BOM id (for DSL reference)
     * @param site        Site envelope for DSL metadata
     */
    public void registerBuilding(String orderId, String unitBomId,
                                 SiteEnvelope site) throws SQLException {
        MOrder reg = new MOrder(conn);
        reg.setCOrderId(orderId);
        reg.setName(orderId.replace('_', ' '));
        reg.setDSLContent(generateDsl(orderId, unitBomId, site));
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
        try { conn.rollback(); } catch (SQLException rollbackEx) { /* best-effort rollback */ }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // ── Buffer Fill ──────────────────────────────────────────────────────────

    /**
     * Create interstitial filler elements between consecutive fixed items in a SET BOM.
     *
     * <p>Fillers are real IFC bounding-box elements (is_variance=1) that tile every gap
     * between adjacent furniture items. N fixed items → N−1 fillers. Together with the
     * fixed items they perfectly cover the parent width (ground truth).
     *
     * <p>Axis model: Width=SUM (strip packing), Depth=MAX (clearance), Height=MAX (clearance).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Read fixed children (ordered by sequence)</li>
     *   <li>Delete old trailing/stale buffer rows</li>
     *   <li>Renumber fixed children: 10, 30, 50, …</li>
     *   <li>INSERT N−1 interstitial fillers at sequences 20, 40, 60, …</li>
     *   <li>Distribute remaining width evenly (last filler absorbs rounding remainder)</li>
     * </ol>
     *
     * @param setBomId  the SET BOM whose buffer children to fill
     * @param parentW   parent allocated width in mm
     * @param parentD   parent allocated depth in mm (clearance check only)
     * @param parentH   parent allocated height in mm (clearance check only)
     * @return log messages describing what was done
     */
    public List<String> fillBuffers(String setBomId, int parentW, int parentD, int parentH)
            throws SQLException {

        List<String> log = new ArrayList<>();
        VerbContext ctx = VerbContext.ofBom(conn);
        VerbRegistry registry = VerbRegistry.createDefault();

        // Step 1: clear existing variance (buffer) rows
        VerbResult<?> clearResult = registry.dispatch(ctx,
            "CLEAR VARIANCE FROM BOM " + setBomId);
        if (!clearResult.pass())
            throw new SQLException("CLEAR VARIANCE failed: " + clearResult.summary());

        ClearVarianceVerb.ClearVariancePayload clearPayload =
            (ClearVarianceVerb.ClearVariancePayload) clearResult.payload();
        if (clearPayload.deletedCount() > 0) {
            log.add("[CLEAR] " + setBomId + ": " + clearPayload.deletedCount() + " variance rows deleted");
        }

        // Step 2: fill buffers
        VerbResult<?> fillResult = registry.dispatch(ctx,
            String.format("FILL BUFFERS IN BOM %s WIDTH %d DEPTH %d HEIGHT %d",
                setBomId, parentW, parentD, parentH));
        if (!fillResult.pass())
            throw new SQLException("FILL BUFFERS failed: " + fillResult.summary());

        FillBuffersVerb.FillBuffersPayload fillPayload =
            (FillBuffersVerb.FillBuffersPayload) fillResult.payload();

        // Relay warnings
        for (String w : fillPayload.warnings()) {
            log.add("[WARN] " + w);
        }

        if (fillPayload.fillersInserted() > 0) {
            log.add("[BUFFER] " + setBomId + ": " + fillPayload.fillersInserted()
                    + " interstitial fillers, remainW=" + fillPayload.remainWidth()
                    + " (parentW=" + parentW + ")");
        }

        return log;
    }

    /**
     * Traverse a parent BOM's children to find SETs with buffer children and fill them.
     *
     * <p>Walks one or two levels deep: parent → child (if SET with buffers), or
     * parent → child (ROOM/PREFAB) → grandchild (SET with buffers).
     *
     * @param parentBomId  the FLOOR-level BOM to scan
     * @return log messages from all buffer fills
     */
    public List<String> fillFloorSetBuffers(String parentBomId) throws SQLException {
        List<String> log = new ArrayList<>();

        // Read parent's children that reference child BOMs (MAKE component_type)
        String sql = "SELECT child_product_id, allocated_width_mm, allocated_depth_mm, allocated_height_mm " +
                     "FROM m_bom_line WHERE bom_id = ? AND is_active = 1 AND component_type = 'MAKE' AND child_product_id IS NOT NULL";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parentBomId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String childBomId = rs.getString("child_product_id");
                    int pw = rs.getInt("allocated_width_mm");
                    int pd = rs.getInt("allocated_depth_mm");
                    int ph = rs.getInt("allocated_height_mm");

                    if (pw > 0) {
                        // Check if this child itself has buffers
                        if (hasBufferChildren(childBomId)) {
                            log.addAll(fillBuffers(childBomId, pw, pd, ph));
                        }
                        // Also check one level deeper (ROOM → SET)
                        log.addAll(fillNestedSetBuffers(childBomId));
                    }
                }
            }
        }
        return log;
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private boolean hasBufferChildren(String bomId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom_line WHERE bom_id = ? AND is_variance = 1 AND is_active = 1")) {
            stmt.setString(1, bomId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private List<String> fillNestedSetBuffers(String parentBomId) throws SQLException {
        List<String> log = new ArrayList<>();
        String sql = "SELECT child_product_id, allocated_width_mm, allocated_depth_mm, allocated_height_mm " +
                     "FROM m_bom_line WHERE bom_id = ? AND is_active = 1 AND component_type = 'MAKE' AND child_product_id IS NOT NULL";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, parentBomId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String childBomId = rs.getString("child_product_id");
                    int pw = rs.getInt("allocated_width_mm");
                    int pd = rs.getInt("allocated_depth_mm");
                    int ph = rs.getInt("allocated_height_mm");

                    if (pw > 0 && hasBufferChildren(childBomId)) {
                        log.addAll(fillBuffers(childBomId, pw, pd, ph));
                    }
                }
            }
        }
        return log;
    }

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
