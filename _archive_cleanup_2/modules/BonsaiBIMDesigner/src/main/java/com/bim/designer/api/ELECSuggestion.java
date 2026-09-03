package com.bim.designer.api;

import com.bim.designer.dao.MEPBOMQuery;
import com.bim.designer.dao.MEPBOMQuery.MEPRequirement;
import com.bim.orm.BIMLogger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ELEC discipline suggestion — proposes electrical OrderLines per room.
 *
 * <p>Products: LIGHT, OUTLET, OUTLET_20A, OUTLET_GFCI, SWITCH, DATA_POINT, CEILING_FAN.
 * Quantities from ad_space_type_mep_bom (qty_normal or per_area_normal × room area).
 *
 * <p>// Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
 */
public class ELECSuggestion implements OrderLineMutation {

    private static final String TAG = "ELECSuggestion";

    @Override
    public String discipline() { return "ELEC"; }

    // Implementing ProjectOrderBlueprint.md §14.3 Session C — Witness: W-RULEPACK-1
    @Override
    public List<ProposedOrderLine> propose(Connection woConn, Connection ruleDb,
                                           String orderId, List<String> packIds) throws SQLException {
        MEPBOMQuery mepQuery = new MEPBOMQuery(ruleDb);
        List<ProposedOrderLine> proposals = new ArrayList<>();

        List<RoomContext> rooms = RoomContext.findRooms(woConn, orderId);
        BIMLogger.info(TAG, "PROPOSE ELEC on order {} — {} rooms, packs={}", orderId, rooms.size(), packIds);

        for (RoomContext room : rooms) {
            String spaceType = OrderMutationService.deriveSpaceType(room.productCategory());
            if (spaceType == null) continue;

            List<MEPRequirement> reqs = mepQuery.queryForDiscipline(spaceType, "ELEC", packIds);
            for (MEPRequirement req : reqs) {
                int qty = computeQty(req, room.areaSqM());
                proposals.add(new ProposedOrderLine(
                        room.orderLineId(), room.productCategory(),
                        req.mepProductId(), "ELEC", qty,
                        req.placementRule(), req.buildingCode(), req.codeClause()));
            }
        }

        BIMLogger.info(TAG, "PROPOSE ELEC → {} lines", proposals.size());
        return proposals;
    }

    static int computeQty(MEPRequirement req, double areaSqM) {
        int qty = req.qtyNormal();
        if (qty <= 0) qty = 1;
        if (req.perAreaNormal() > 0 && areaSqM > 0) {
            int areaQty = (int) Math.ceil(areaSqM * req.perAreaNormal());
            qty = Math.max(qty, areaQty);
        }
        return qty;
    }
}
