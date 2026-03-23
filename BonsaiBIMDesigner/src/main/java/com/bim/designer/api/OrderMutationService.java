package com.bim.designer.api;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Order mutation operations — extracted from DesignerAPIImpl to prevent
 * God Object growth across Sessions A-E (ProjectOrderBlueprint.md §14.3).
 *
 * <p>Each mutation type (Add, Remove, Compress, Replace) will be a method here.
 * DesignerAPIImpl delegates to this service.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session A — Witness: W-DM-TC5-1
 */
public class OrderMutationService {

    private static final String TAG = "OrderMutation";

    /**
     * Result of an addDiscipline operation.
     *
     * @param linesCreated   number of PROPOSED C_OrderLines inserted
     * @param roomsProcessed number of rooms that had matching MEP requirements
     * @param discipline     the discipline that was added
     * @param details        per-room breakdown (room bom_category → product list)
     */
    public record AddDisciplineResult(
            int linesCreated,
            int roomsProcessed,
            String discipline,
            List<ProposedLine> details
    ) {}

    /**
     * One proposed C_OrderLine created by addDiscipline.
     */
    public record ProposedLine(
            int orderLineId,
            int parentRoomLineId,
            String roomCategory,
            String mepProductId,
            int qty,
            String placementRule
    ) {}

    /**
     * Add a discipline to an existing order by querying ad_space_type_mep_bom
     * for each ROOM in the order and creating PROPOSED C_OrderLines.
     *
     * <p>For each room C_OrderLine, derives space_type from bom_category
     * (BEDROOM, BATHROOM, KITCHEN, etc.), queries disc_validation.db for
     * MEP products matching the given discipline, and inserts LEAF
     * C_OrderLines as children of the room with proposal_status='PROPOSED'.
     *
     * <p>bom_child_id is NULL — these are rule-driven lines, not BOM-derived.
     * OrderLineWalker handles null bom_child_id gracefully.
     *
     * @param woConn          work_output.db connection (has C_Order + C_OrderLine)
     * @param discValConn     disc_validation.db connection (has ad_space_type_mep_bom)
     * @param orderId         C_Order_ID to add discipline lines to
     * @param discipline      discipline code: FP, ELEC, SP, ACMV
     * @param jurisdiction    ISO country code for rule selection (e.g., "MY", "US")
     * @return result with line count, room count, and per-line details
     */
    public AddDisciplineResult addDiscipline(Connection woConn, Connection discValConn,
                                              String orderId, String discipline,
                                              String jurisdiction) throws SQLException {
        if (orderId == null || discipline == null) {
            return new AddDisciplineResult(0, 0, discipline, List.of());
        }

        MEPBOMQuery mepQuery = new MEPBOMQuery(discValConn);
        List<ProposedLine> details = new ArrayList<>();
        int roomsProcessed = 0;

        // 1. Find all ROOM-level C_OrderLines in this order
        List<RoomRow> rooms = findRooms(woConn, orderId);
        BIMLogger.info(TAG, "ADD_DISCIPLINE {} on order {} — {} rooms found",
                discipline, orderId, rooms.size());

        for (RoomRow room : rooms) {
            // 2. Derive space_type from bom_category (BEDROOM → BEDROOM, etc.)
            String spaceType = deriveSpaceType(room.bomCategory);
            if (spaceType == null) continue;

            // 3. Query MEP requirements for this space type + discipline
            List<MEPRequirement> requirements = mepQuery.queryForDiscipline(spaceType, discipline);
            if (requirements.isEmpty()) continue;

            roomsProcessed++;

            // 4. Insert PROPOSED C_OrderLine per MEP product
            for (MEPRequirement req : requirements) {
                int qty = req.qtyNormal();  // STANDARD profile
                if (qty <= 0) qty = 1;

                // Compute qty from per_area if available
                if (req.perAreaNormal() > 0 && room.areaSqM > 0) {
                    int areaQty = (int) Math.ceil(room.areaSqM * req.perAreaNormal());
                    qty = Math.max(qty, areaQty);
                }

                int lineId = insertProposedLine(woConn, orderId, room.orderLineId,
                        req.mepProductId(), discipline, room.bomCategory,
                        req.placementRule(), qty);

                details.add(new ProposedLine(lineId, room.orderLineId,
                        room.bomCategory, req.mepProductId(), qty, req.placementRule()));
            }
        }

        BIMLogger.info(TAG, "ADD_DISCIPLINE {} → {} lines across {} rooms",
                discipline, details.size(), roomsProcessed);
        return new AddDisciplineResult(details.size(), roomsProcessed, discipline, details);
    }

    // ── Room discovery ──────────────────────────────────────────────

    private record RoomRow(int orderLineId, String bomCategory, double areaSqM) {}

    /**
     * Find ROOM-level C_OrderLines in the given order.
     * Room area is computed from AABB width × depth (mm² → m²).
     */
    private List<RoomRow> findRooms(Connection woConn, String orderId) throws SQLException {
        String sql = "SELECT C_OrderLine_ID, bom_category, "
                + "COALESCE(aabb_width_mm, 0) * COALESCE(aabb_depth_mm, 0) / 1e6 AS area_sqm "
                + "FROM C_OrderLine "
                + "WHERE C_Order_ID = ? AND host_type = 'ROOM' "
                + "ORDER BY Line";
        List<RoomRow> rooms = new ArrayList<>();
        try (PreparedStatement ps = woConn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rooms.add(new RoomRow(
                            rs.getInt("C_OrderLine_ID"),
                            rs.getString("bom_category"),
                            rs.getDouble("area_sqm")));
                }
            }
        }
        return rooms;
    }

    /**
     * Derive ad_space_type_mep_bom.space_type_id from bom_category.
     * BOM categories map directly to space types in most cases.
     * Returns null for categories that have no MEP mapping.
     */
    static String deriveSpaceType(String bomCategory) {
        if (bomCategory == null) return null;
        return switch (bomCategory.toUpperCase()) {
            case "BEDROOM", "BED" -> "BEDROOM";
            case "MASTER_BEDROOM", "MASTER_BED", "MASTER" -> "MASTER_BEDROOM";
            case "BATHROOM", "BATH" -> "BATHROOM";
            case "TOILET", "WC" -> "TOILET";
            case "KITCHEN", "KIT" -> "KITCHEN";
            case "WET_KITCHEN" -> "WET_KITCHEN";
            case "LIVING", "LIV" -> "LIVING";
            case "DINING", "DIN" -> "LIVING";  // dining uses living MEP rules
            case "CORRIDOR", "COR" -> "CORRIDOR";
            case "STORE", "STR_RM" -> "STORE";
            case "OFFICE" -> "OFFICE";
            case "CLASSROOM" -> "CLASSROOM";
            case "ASSEMBLY_HALL" -> "ASSEMBLY_HALL";
            default -> {
                BIMLogger.info(TAG, "No MEP space_type mapping for bom_category={}", bomCategory);
                yield null;
            }
        };
    }

    // ── Line insertion ──────────────────────────────────────────────

    /**
     * Insert a PROPOSED LEAF C_OrderLine for an MEP product.
     * bom_child_id is NULL (rule-driven, not BOM-derived).
     * Discipline is set explicitly. proposal_status='PROPOSED'.
     */
    private int insertProposedLine(Connection woConn, String orderId, int parentLineId,
                                    String mepProductId, String discipline,
                                    String roomCategory, String placementRule,
                                    int qty) throws SQLException {
        // Next Line sequence
        int nextLine = 10;
        try (PreparedStatement ps = woConn.prepareStatement(
                "SELECT COALESCE(MAX(Line), 0) FROM C_OrderLine WHERE C_Order_ID = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) nextLine = rs.getInt(1) + 10;
            }
        }

        String sql = "INSERT INTO C_OrderLine "
                + "(C_Order_ID, Parent_OrderLine_ID, Line, family_ref, host_type, "
                + " bom_category, M_Product_ID, Discipline, Qty, proposal_status) "
                + "VALUES (?, ?, ?, ?, 'LEAF', ?, ?, ?, ?, 'PROPOSED')";
        try (PreparedStatement ps = woConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderId);
            ps.setInt(2, parentLineId);
            ps.setInt(3, nextLine);
            ps.setString(4, mepProductId);       // family_ref = product ID
            ps.setString(5, roomCategory);        // bom_category from parent room
            ps.setString(6, mepProductId);        // M_Product_ID
            ps.setString(7, discipline);          // FP, ELEC, SP, ACMV
            ps.setInt(8, qty);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }
}
