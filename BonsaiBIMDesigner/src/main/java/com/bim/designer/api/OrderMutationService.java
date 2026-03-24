package com.bim.designer.api;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;
import com.bim.designer.dao.WorkOutputDAO;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order mutation operations — extracted from DesignerAPIImpl to prevent
 * God Object growth across Sessions A-E (ProjectOrderBlueprint.md §14.3).
 *
 * <p>Session B refactor: addDiscipline() now delegates to {@link OrderLineMutation}
 * implementations (FPSuggestion, ELECSuggestion, ACMVSuggestion). The propose()
 * call generates ProposedOrderLines, then this service persists them.
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
 */
public class OrderMutationService {

    private static final String TAG = "OrderMutation";

    private static final Map<String, OrderLineMutation> SUGGESTIONS = Map.of(
            "ELEC", new ELECSuggestion(),
            "FP",   new FPSuggestion(),
            "ACMV", new ACMVSuggestion()
    );

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
     * Add a discipline to an existing order via the OrderLineMutation interface.
     *
     * <p>Session B refactor: delegates to the appropriate *Suggestion implementation
     * to compute proposals, then persists each as a PROPOSED C_OrderLine.
     * Backward compatible — same signature, same result type, same behavior.
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

        // Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
        // Look up the OrderLineMutation for this discipline
        OrderLineMutation mutation = SUGGESTIONS.get(discipline);

        List<ProposedOrderLine> proposals;
        if (mutation != null) {
            // Session B path: delegate to interface implementation
            proposals = mutation.propose(woConn, discValConn, orderId);
        } else {
            // Fallback for disciplines without an OrderLineMutation (e.g., SP)
            // Uses the original inline logic
            proposals = proposeInline(woConn, discValConn, orderId, discipline);
        }

        // Persist each proposal as a PROPOSED C_OrderLine
        List<ProposedLine> details = new ArrayList<>();
        java.util.Set<Integer> roomsSeen = new java.util.HashSet<>();

        for (ProposedOrderLine p : proposals) {
            int lineId = insertProposedLine(woConn, orderId, p.parentRoomLineId(),
                    p.mepProductId(), p.discipline(), p.roomCategory(),
                    p.placementRule(), p.qty());
            details.add(new ProposedLine(lineId, p.parentRoomLineId(),
                    p.roomCategory(), p.mepProductId(), p.qty(), p.placementRule()));
            roomsSeen.add(p.parentRoomLineId());
        }

        BIMLogger.info(TAG, "ADD_DISCIPLINE {} → {} lines across {} rooms",
                discipline, details.size(), roomsSeen.size());
        return new AddDisciplineResult(details.size(), roomsSeen.size(), discipline, details);
    }

    /**
     * Propose all disciplines at once via OrderLineMutation interface.
     * Returns proposals without persisting — caller decides what to do.
     *
     * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
     */
    public List<ProposedOrderLine> proposeAll(Connection woConn, Connection ruleDb,
                                               String orderId) throws SQLException {
        List<ProposedOrderLine> all = new ArrayList<>();
        for (OrderLineMutation mutation : SUGGESTIONS.values()) {
            all.addAll(mutation.propose(woConn, ruleDb, orderId));
        }
        return all;
    }

    /**
     * Get the OrderLineMutation for a given discipline, or null if none registered.
     */
    public static OrderLineMutation getMutation(String discipline) {
        return SUGGESTIONS.get(discipline);
    }

    // ── Fallback inline proposal (for disciplines without an OrderLineMutation) ──

    private List<ProposedOrderLine> proposeInline(Connection woConn, Connection discValConn,
                                                   String orderId, String discipline) throws SQLException {
        MEPBOMQuery mepQuery = new MEPBOMQuery(discValConn);
        List<ProposedOrderLine> proposals = new ArrayList<>();

        List<RoomContext> rooms = RoomContext.findRooms(woConn, orderId);
        BIMLogger.info(TAG, "PROPOSE_INLINE {} on order {} — {} rooms", discipline, orderId, rooms.size());

        for (RoomContext room : rooms) {
            String spaceType = deriveSpaceType(room.bomCategory());
            if (spaceType == null) continue;

            List<MEPRequirement> reqs = mepQuery.queryForDiscipline(spaceType, discipline);
            for (MEPRequirement req : reqs) {
                int qty = ELECSuggestion.computeQty(req, room.areaSqM());
                proposals.add(new ProposedOrderLine(
                        room.orderLineId(), room.bomCategory(),
                        req.mepProductId(), discipline, qty,
                        req.placementRule(), req.buildingCode(), req.codeClause()));
            }
        }
        return proposals;
    }

    // ── Space type mapping ──────────────────────────────────────────

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
