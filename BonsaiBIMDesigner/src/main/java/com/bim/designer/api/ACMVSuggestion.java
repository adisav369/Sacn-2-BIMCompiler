package com.bim.designer.api;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ACMV (Air Conditioning, Mechanical Ventilation) discipline suggestion —
 * proposes aircon/ventilation OrderLines per room.
 *
 * <p>Products: SUPPLY_DIFFUSER, EXHAUST_FAN, AIRCON_POINT.
 * Quantities from ad_space_type_mep_bom (qty_normal or per_area_normal × room area).
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session B — Witness: W-DM-FP-VAL-1
 */
public class ACMVSuggestion implements OrderLineMutation {

    private static final String TAG = "ACMVSuggestion";

    @Override
    public String discipline() { return "ACMV"; }

    @Override
    public List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                           String orderId) throws SQLException {
        MEPBOMQuery mepQuery = new MEPBOMQuery(ruleDb);
        List<ProposedOrderLine> proposals = new ArrayList<>();

        List<RoomContext> rooms = RoomContext.findRooms(woConn, orderId);
        BIMLogger.info(TAG, "PROPOSE ACMV on order {} — {} rooms", orderId, rooms.size());

        for (RoomContext room : rooms) {
            String spaceType = OrderMutationService.deriveSpaceType(room.bomCategory());
            if (spaceType == null) continue;

            List<MEPRequirement> reqs = mepQuery.queryForDiscipline(spaceType, "ACMV");
            for (MEPRequirement req : reqs) {
                int qty = ELECSuggestion.computeQty(req, room.areaSqM());
                proposals.add(new ProposedOrderLine(
                        room.orderLineId(), room.bomCategory(),
                        req.mepProductId(), "ACMV", qty,
                        req.placementRule(), req.buildingCode(), req.codeClause()));
            }
        }

        BIMLogger.info(TAG, "PROPOSE ACMV → {} lines", proposals.size());
        return proposals;
    }
}
