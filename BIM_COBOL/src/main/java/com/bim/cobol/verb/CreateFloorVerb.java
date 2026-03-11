package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * CREATE FLOOR &lt;name&gt; ROOMS &lt;cats...&gt; WIDTH &lt;w&gt; DEPTH &lt;d&gt; HEIGHT &lt;h&gt;
 *
 * <p>Level 2 floor verb (§18.7). Calls PARTITION AABB internally,
 * creates m_bom (FLOOR, entity_type=U) + m_bom_line per room.
 *
 * <p>Guard: validates AABB overflow — sum of room widths must not exceed floor width.
 *
 * <p>Grammar:
 * <pre>
 *   CREATE FLOOR GF ROOMS LI BD KT BT WIDTH 10000 DEPTH 8000 HEIGHT 3000
 * </pre>
 */
public class CreateFloorVerb implements Verb<CreateFloorVerb.CreateFloorPayload> {

    @Override
    public String keyword() { return "CREATE FLOOR"; }

    @Override
    public VerbResult<CreateFloorPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        if (args.length < 6)
            return VerbResult.fail(keyword(),
                "usage: CREATE FLOOR <name> ROOMS <cats...> WIDTH <w> DEPTH <d> HEIGHT <h>", null);

        String floorName = args[0].toUpperCase();

        // Find ROOMS keyword
        int roomsIdx = -1;
        for (int i = 1; i < args.length; i++) {
            if ("ROOMS".equalsIgnoreCase(args[i])) { roomsIdx = i; break; }
        }
        if (roomsIdx < 0)
            return VerbResult.fail(keyword(), "missing ROOMS keyword", null);

        // Collect categories until we hit WIDTH/DEPTH/HEIGHT
        List<String> categories = new ArrayList<>();
        int widthMm = 0, depthMm = 0, heightMm = 0;
        int i = roomsIdx + 1;
        while (i < args.length) {
            String token = args[i].toUpperCase();
            switch (token) {
                case "WIDTH"  -> { widthMm = Integer.parseInt(args[++i]); }
                case "DEPTH"  -> { depthMm = Integer.parseInt(args[++i]); }
                case "HEIGHT" -> { heightMm = Integer.parseInt(args[++i]); }
                default       -> categories.add(token);
            }
            i++;
        }

        if (categories.isEmpty())
            return VerbResult.fail(keyword(), "at least one room category required", null);
        if (widthMm <= 0 || depthMm <= 0 || heightMm <= 0)
            return VerbResult.fail(keyword(), "WIDTH, DEPTH, HEIGHT must all be positive", null);

        Connection conn = ctx.bomConn();

        // Step 1: Partition — find best-fit BOM per category via strip-packing
        List<RoomSlot> roomSlots = new ArrayList<>();
        int usedWidth = 0;

        for (String cat : categories) {
            int remainingW = widthMm - usedWidth;
            if (remainingW <= 0) {
                return VerbResult.fail(keyword(),
                    String.format("AABB overflow: floor width %d exhausted after %d rooms, cannot fit %s",
                        widthMm, roomSlots.size(), cat), null);
            }

            MBOM bestFit = MBOM.findBestFitAnyOwner(conn, cat,
                remainingW, depthMm, heightMm);
            if (bestFit == null) {
                return VerbResult.fail(keyword(),
                    String.format("no BOM fits category %s within %dx%dx%d",
                        cat, remainingW, depthMm, heightMm), null);
            }

            int[] childSpace = MBOM.computeTotalChildSpace(conn, bestFit.getBomId());
            int allocW = childSpace[0] > 0 ? childSpace[0] : remainingW;

            roomSlots.add(new RoomSlot(cat, bestFit.getBomId(), allocW, usedWidth));
            usedWidth += allocW;
        }

        // Guard: AABB overflow check — sum of room widths must fit
        if (usedWidth > widthMm) {
            return VerbResult.fail(keyword(),
                String.format("AABB overflow: room widths sum=%d > floor width=%d",
                    usedWidth, widthMm), null);
        }

        // Step 2: Create floor BOM header
        String bomId = "SY_FLOOR_" + floorName + "_" + widthMm + "x" + depthMm;
        MBOM existing = MBOM.get(conn, bomId);
        if (existing != null)
            return VerbResult.fail(keyword(), bomId + " already exists", null);

        MBOM bom = new MBOM(conn);
        bom.setBomId(bomId);
        bom.setBomName("Floor " + floorName);
        bom.setBomType("FLOOR");
        bom.setBomCategory("GF");  // default floor category
        bom.setGroupBy("BUILDING");
        bom.setIsActive(true);
        bom.setBomLevel("FLOOR");
        bom.setSeqNo(10);
        bom.setEntityType(MBOM.ENTITYTYPE_User);
        bom.save();

        // Step 3: Create m_bom_line per room with strip-packed offsets
        int seq = 10;
        List<String> roomBomIds = new ArrayList<>();
        for (RoomSlot slot : roomSlots) {
            MBOMLine line = new MBOMLine(conn);
            line.setBomId(bomId);
            line.setChildProductId(slot.bestFitBomId);
            line.setRole("ROOM_" + slot.category);
            line.setSequence(seq);
            line.setDx(slot.offsetX / 1000.0);  // mm → m for tack convention
            line.setDy(0);
            line.setDz(0);
            line.setAllocatedWidthMm(slot.allocW);
            line.setAllocatedDepthMm(depthMm);
            line.setAllocatedHeightMm(heightMm);
            // component_type is not a decision field — currently all BUY.
            // Future: MAKE = LOD created on-the-fly via Mesh2Library.txt.
            line.setComponentType("BUY");
            line.setEntityType(MBOM.ENTITYTYPE_User);
            line.setIsActive(true);
            line.save();
            roomBomIds.add(slot.bestFitBomId);
            seq += 10;
        }

        double wastePercent = widthMm > 0
            ? 100.0 * (widthMm - usedWidth) / widthMm
            : 0.0;

        return VerbResult.ok(keyword(),
            String.format("CREATE FLOOR %s → %s (%d rooms, %.1f%% waste)",
                floorName, bomId, roomSlots.size(), wastePercent),
            new CreateFloorPayload(bomId, roomSlots.size(),
                roomBomIds.toArray(new String[0]), wastePercent));
    }

    private record RoomSlot(String category, String bestFitBomId, int allocW, int offsetX) {}

    public record CreateFloorPayload(
        String bomId, int roomCount,
        String[] rooms, double wastePercent
    ) {}
}
