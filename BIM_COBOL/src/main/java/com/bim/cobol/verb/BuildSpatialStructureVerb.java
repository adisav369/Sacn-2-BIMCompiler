package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.compiler.dsl.SpatialStructureBuilder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BUILD SPATIAL STRUCTURE &lt;doc_sub_type&gt;
 *
 * <p>Delegates to {@link SpatialStructureBuilder} which handles:
 * <ol>
 *   <li>normalizeStoreyNames — INSERT IfcBuildingStorey for missing storeys</li>
 *   <li>emitIfcSpaceFromRoomSlots — INSERT IfcSpace from BOM-derived room slots</li>
 *   <li>populateSpaceContainment — INSERT/UPDATE rel_contained_in_space</li>
 * </ol>
 *
 * <p>Room slots are computed from BOM.db (m_bom + m_bom_line + ad_room_boundary),
 * replacing the former co_empty_space_line dependency.
 *
 * <p>Requires bomConn (BOM.db) and outputConn (output.db).
 */
public class BuildSpatialStructureVerb implements Verb<BuildSpatialStructureVerb.SpatialStructurePayload> {

    /** Category code → room type name for ad_room_boundary lookup. */
    private static final Map<String, String> CATEGORY_TO_ROOM_TYPE = Map.of(
        "LI", "LIVING",
        "BD", "BEDROOM",
        "KT", "KITCHEN",
        "BT", "BATHROOM",
        "DN", "DINING"
    );

    @Override
    public String keyword() { return "BUILD SPATIAL STRUCTURE"; }

    @Override
    public VerbResult<SpatialStructurePayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 1)
            return VerbResult.fail(keyword(),
                "usage: BUILD SPATIAL STRUCTURE <doc_sub_type>", null);

        String docSubType = args[0];

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — BUILD SPATIAL STRUCTURE writes to output.db", null);

        Connection bomConn = ctx.bomConn();
        if (bomConn == null)
            return VerbResult.fail(keyword(),
                "bomConn required — BUILD SPATIAL STRUCTURE reads room slots from BOM.db", null);

        // Compute room slots from BOM.db
        List<SpatialStructureBuilder.RoomSlot> roomSlots = computeRoomSlots(bomConn, outputConn, docSubType);

        // Delegate to SpatialStructureBuilder
        SpatialStructureBuilder builder = new SpatialStructureBuilder(outputConn, roomSlots);
        SpatialStructureBuilder.Result result = builder.buildAll();

        SpatialStructurePayload payload = new SpatialStructurePayload(
            docSubType, result.storeysAdded(), result.spacesEmitted(),
            result.storeyContained(), result.roomContained());

        return VerbResult.ok(keyword(),
            String.format("BUILD SPATIAL STRUCTURE %s: %d storeys, %d spaces, %d+%d containment",
                docSubType, result.storeysAdded(), result.spacesEmitted(),
                result.storeyContained(), result.roomContained()),
            payload);
    }

    /**
     * Compute room slots from BOM.db by walking BUILDING → FLOOR → room-category children,
     * then looking up AABB from ad_room_boundary.
     *
     * <p>Storey names are derived from output.db elements_meta (DISTINCT storey values),
     * matched to BOM children by position index.
     */
    private List<SpatialStructureBuilder.RoomSlot> computeRoomSlots(Connection bomConn, Connection outputConn,
                                             String docSubType) throws SQLException {
        List<SpatialStructureBuilder.RoomSlot> slots = new ArrayList<>();

        // 1. Get the BUILDING BOM (Tier 2: use M_BOM_ID INTEGER PK)
        int buildingBomId;
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT M_BOM_ID FROM m_bom WHERE bom_type = 'BUILDING' AND doc_sub_type = ?")) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return slots;
                buildingBomId = rs.getInt(1);
                if (buildingBomId == 0) return slots;
            }
        }

        // 2. Get storey names from output.db elements_meta (ordered)
        List<String> storeyNames = new ArrayList<>();
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT DISTINCT storey FROM elements_meta WHERE storey IS NOT NULL ORDER BY storey")) {
            while (rs.next()) {
                storeyNames.add(rs.getString(1));
            }
        }

        // 3. Walk BUILDING children (floors) — Tier 2: JOIN via M_BOM_ID
        List<int[]> floorChildren = new ArrayList<>(); // [M_BOM_ID of child BOM]
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT mbl.child_product_id, mbl.role, mbl.dz, mb2.M_BOM_ID " +
                "FROM m_bom_line mbl " +
                "LEFT JOIN m_bom mb2 ON mb2.Value = mbl.child_product_id " +
                "WHERE mbl.M_BOM_ID = ? AND mbl.is_active = 1 ORDER BY mbl.sequence")) {
            ps.setInt(1, buildingBomId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String role = rs.getString("role");
                    if (role != null && (role.contains("LEVEL") || role.contains("GROUND_FLOOR"))) {
                        int childMBomId = rs.getInt("M_BOM_ID");
                        if (childMBomId > 0) {
                            floorChildren.add(new int[]{childMBomId});
                        }
                    }
                }
            }
        }

        // 4. For each floor, match to storey name by index, then query room children
        for (int storeyIdx = 0; storeyIdx < floorChildren.size(); storeyIdx++) {
            if (storeyIdx >= storeyNames.size()) break;
            String storeyName = storeyNames.get(storeyIdx);
            int floorBomId = floorChildren.get(storeyIdx)[0];

            // Query room-category children of floor BOM — Tier 2: JOIN via M_BOM_ID
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT mbl.role, mb2.m_product_category_id " +
                    "FROM m_bom_line mbl " +
                    "LEFT JOIN m_bom mb2 ON mb2.Value = mbl.child_product_id " +
                    "WHERE mbl.M_BOM_ID = ? AND mbl.is_active = 1 " +
                    "AND mb2.m_product_category_id IN ('LI','BD','KT','BT','DN')")) {
                ps.setInt(1, floorBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String bomLineRole = rs.getString("role");
                        String category = rs.getString("m_product_category_id");
                        String roomType = CATEGORY_TO_ROOM_TYPE.get(category);
                        if (roomType == null) continue;

                        // Look up AABB from ad_room_boundary
                        try (PreparedStatement aabbPs = bomConn.prepareStatement(
                                "SELECT min_x_mm, max_x_mm, min_y_mm, max_y_mm " +
                                "FROM ad_room_boundary " +
                                "WHERE building_type = ? AND storey = ? AND room_type = ?")) {
                            aabbPs.setString(1, docSubType);
                            aabbPs.setString(2, storeyName);
                            aabbPs.setString(3, roomType);
                            try (ResultSet aabbRs = aabbPs.executeQuery()) {
                                if (aabbRs.next()) {
                                    double minX = aabbRs.getDouble("min_x_mm");
                                    double maxX = aabbRs.getDouble("max_x_mm");
                                    double minY = aabbRs.getDouble("min_y_mm");
                                    double maxY = aabbRs.getDouble("max_y_mm");
                                    // Z bounds: use 0 to very large (full storey height)
                                    // Room boundary only has X/Y; Z spans full storey
                                    slots.add(new SpatialStructureBuilder.RoomSlot(
                                        storeyName, roomType, bomLineRole,
                                        minX, minY, 0.0,
                                        maxX, maxY, 1e9));
                                }
                            }
                        }
                    }
                }
            }
        }

        return slots;
    }

    public record SpatialStructurePayload(
        String docSubType, int storeysAdded, int spacesEmitted,
        int storeyContained, int roomContained
    ) {}
}
