package com.bim.designer.api;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * FP (Fire Protection) discipline suggestion — proposes sprinkler/emergency light
 * OrderLines per room.
 *
 * <p>Products: SPRINKLER, EMERGENCY_LIGHT.
 * Quantities from ad_space_type_mep_bom (qty_normal or per_area_normal × room area).
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
 */
public class FPSuggestion implements OrderLineMutation {

    private static final String TAG = "FPSuggestion";

    @Override
    public String discipline() { return "FP"; }

    // Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
    @Override
    public List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                           String orderId, List<String> packIds) throws SQLException {
        MEPBOMQuery mepQuery = new MEPBOMQuery(ruleDb);
        List<ProposedOrderLine> proposals = new ArrayList<>();

        List<RoomContext> rooms = RoomContext.findRooms(woConn, orderId);
        BIMLogger.info(TAG, "PROPOSE FP on order {} — {} rooms, packs={}", orderId, rooms.size(), packIds);

        for (RoomContext room : rooms) {
            String spaceType = OrderMutationService.deriveSpaceType(room.bomCategory());
            if (spaceType == null) continue;

            List<MEPRequirement> reqs = mepQuery.queryForDiscipline(spaceType, "FP", packIds);
            for (MEPRequirement req : reqs) {
                int qty = ELECSuggestion.computeQty(req, room.areaSqM());
                proposals.add(new ProposedOrderLine(
                        room.orderLineId(), room.bomCategory(),
                        req.mepProductId(), "FP", qty,
                        req.placementRule(), req.buildingCode(), req.codeClause()));
            }
        }

        BIMLogger.info(TAG, "PROPOSE FP → {} lines", proposals.size());
        return proposals;
    }
}
