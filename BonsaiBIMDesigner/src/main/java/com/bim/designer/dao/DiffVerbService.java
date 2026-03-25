package com.bim.designer.dao;

import com.bim.backoffice.dao.ChangelogDAO;
import com.bim.orm.BIMLogger;
import com.bim.ormsandbox.po.M_W_Verb_Node;
import com.bim.ormsandbox.po.M_W_Verb_NodeProduct;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Records a DiffVerb — a spatial mutation captured as a replayable W_Verb_Node.
 *
 * <p>iDempiere parallel: when a user drags an element in the viewport, the
 * gesture is recorded as a W_Verb_Node (verb_ref='DIFF') with delta parameters
 * in W_Verb_NodeProduct. The CalloutEngine then fires any matching AD_Rule chain.
 *
 * <p>DiffVerb is NOT a BIM_COBOL compilation verb (TILE, ROUTE, etc.).
 * It is a design-time mutation that lives alongside WorkOutputDAO.
 *
 * // Implementing ProjectOrderBlueprint.md §9.2 — Witness: W-DIFF-1
 */
public class DiffVerbService {

    private static final String TAG = "DiffVerbService";
    private static final String VERB_REF = "DIFF";

    private final Connection conn;

    public DiffVerbService(Connection conn) {
        this.conn = conn;
    }

    /**
     * Parameters for a spatial diff (position change).
     *
     * @param buildingId   C_Order_ID of the building
     * @param locatorRef   locator_ref of the moved element (e.g. "L1.Living.FP_Mantel")
     * @param deltaDx      X displacement in mm (parent-relative)
     * @param deltaDy      Y displacement in mm
     * @param deltaDz      Z displacement in mm
     * @param oldX         previous X position in mm
     * @param oldY         previous Y position in mm
     * @param oldZ         previous Z position in mm
     * @param source       provenance: "viewport_drag", "api_call", "callout"
     */
    public record DiffParams(String buildingId, String locatorRef,
                             double deltaDx, double deltaDy, double deltaDz,
                             double oldX, double oldY, double oldZ,
                             String source) {}

    /**
     * Result of recording a DiffVerb + firing callouts.
     *
     * @param nodeId        W_Verb_Node_ID of the recorded diff
     * @param locatorRef    which element was moved
     * @param calloutsFired number of callout rules that fired
     * @param calloutResults detailed results from CalloutEngine
     */
    public record DiffResult(int nodeId, String locatorRef,
                             int calloutsFired,
                             List<CalloutEngine.CalloutResult> calloutResults) {}

    /**
     * Record a spatial diff and fire the callout chain.
     *
     * <ol>
     *   <li>Create W_Verb_Node row with verb_ref='DIFF'</li>
     *   <li>Create W_Verb_NodeProduct rows for delta/old/source params</li>
     *   <li>Log to bim_changelog as MOVE action</li>
     *   <li>Fire CalloutEngine for each changed axis (dx/dy/dz)</li>
     * </ol>
     *
     * @return DiffResult with node ID and callout results
     */
    public DiffResult recordDiff(DiffParams params) throws SQLException {
        // 1. Find next SeqNo for this building's verb nodes
        int nextSeqNo = getNextSeqNo(params.buildingId());

        // 2. Create W_Verb_Node
        M_W_Verb_Node node = new M_W_Verb_Node(conn);
        node.setC_Order_ID(params.buildingId());
        node.setSeqNo(nextSeqNo);
        node.setName(VERB_REF);
        node.setDescription("DIFF " + params.locatorRef()
                + " delta(" + params.deltaDx() + "," + params.deltaDy() + "," + params.deltaDz() + ")");
        node.setDocStatus("CO");
        node.save();

        int nodeId = node.getW_Verb_Node_ID();

        // 3. Create W_Verb_NodeProduct rows for structured params
        Map<String, String> paramMap = Map.of(
                "locator_ref", params.locatorRef(),
                "delta_dx", String.valueOf(params.deltaDx()),
                "delta_dy", String.valueOf(params.deltaDy()),
                "delta_dz", String.valueOf(params.deltaDz()),
                "old_x", String.valueOf(params.oldX()),
                "old_y", String.valueOf(params.oldY()),
                "old_z", String.valueOf(params.oldZ()),
                "source", params.source()
        );

        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            M_W_Verb_NodeProduct param = new M_W_Verb_NodeProduct(conn);
            param.setW_Verb_Node_ID(nodeId);
            param.setName(entry.getKey());
            param.setValue(entry.getValue());
            param.setValueType(inferType(entry.getValue()));
            param.save();
        }

        // 4. Log to bim_changelog
        ChangelogDAO changelog = new ChangelogDAO(conn);
        double newX = params.oldX() + params.deltaDx();
        double newY = params.oldY() + params.deltaDy();
        double newZ = params.oldZ() + params.deltaDz();
        changelog.logChange(params.buildingId(), null, "MOVE",
                "C_OrderLine", params.locatorRef(),
                "position",
                String.format("%.0f,%.0f,%.0f", params.oldX(), params.oldY(), params.oldZ()),
                String.format("%.0f,%.0f,%.0f", newX, newY, newZ),
                null);

        // 5. Fire callout chain for each changed axis
        CalloutEngine calloutEngine = new CalloutEngine(conn);
        List<CalloutEngine.CalloutResult> allCallouts = new java.util.ArrayList<>();

        if (params.deltaDx() != 0) {
            allCallouts.addAll(calloutEngine.fire("C_OrderLine", "dx"));
        }
        if (params.deltaDy() != 0) {
            allCallouts.addAll(calloutEngine.fire("C_OrderLine", "dy"));
        }
        if (params.deltaDz() != 0) {
            allCallouts.addAll(calloutEngine.fire("C_OrderLine", "dz"));
        }

        int fired = (int) allCallouts.stream().filter(CalloutEngine.CalloutResult::fired).count();

        BIMLogger.info(TAG, "DiffVerb recorded: {} → node {} | {} callouts fired",
                params.locatorRef(), nodeId, fired);

        return new DiffResult(nodeId, params.locatorRef(), fired, allCallouts);
    }

    // ── Internals ────────────────────────────────────────────────────

    private int getNextSeqNo(String buildingId) throws SQLException {
        List<M_W_Verb_Node> existing = M_W_Verb_Node.getByOrder(conn, buildingId);
        if (existing.isEmpty()) return 10;
        return existing.get(existing.size() - 1).getSeqNo() + 10;
    }

    private static String inferType(String value) {
        if (value == null) return "TEXT";
        try {
            Integer.parseInt(value);
            return "NUM";
        } catch (NumberFormatException e1) {
            try {
                Double.parseDouble(value);
                return "NUM";
            } catch (NumberFormatException e2) {
                return "TEXT";
            }
        }
    }
}
