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
 * ADD FLOOR &lt;name&gt; TO &lt;unit_bom_id&gt; HEIGHT &lt;h&gt; ROOMS &lt;cats...&gt;
 *
 * <p>Level 3 building verb (§18.8). Creates slab + floor BOM (via
 * CREATE FLOOR logic), appends to unit. Calls STACK FLOORS to
 * recompute all dz.
 *
 * <p>Grammar:
 * <pre>
 *   ADD FLOOR L2 TO SY_UNIT_01 HEIGHT 3000 ROOMS LI BD KT BT
 * </pre>
 */
public class AddFloorVerb implements Verb<AddFloorVerb.AddFloorPayload> {

    @Override
    public String keyword() { return "ADD FLOOR"; }

    @Override
    public VerbResult<AddFloorPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: ADD FLOOR <name> TO <unit_bom_id> HEIGHT <h> ROOMS <cats...>
        if (args.length < 6)
            return VerbResult.fail(keyword(),
                "usage: ADD FLOOR <name> TO <unit_bom_id> HEIGHT <h> ROOMS <cats...>", null);

        String floorName = args[0].toUpperCase();

        if (!"TO".equalsIgnoreCase(args[1]))
            return VerbResult.fail(keyword(),
                "expected TO, got: " + args[1], null);

        String unitBomId = args[2];

        // Parse HEIGHT and ROOMS
        int heightMm = 0;
        List<String> categories = new ArrayList<>();
        int i = 3;
        while (i < args.length) {
            String token = args[i].toUpperCase();
            switch (token) {
                case "HEIGHT" -> { heightMm = Integer.parseInt(args[++i]); }
                case "ROOMS" -> {
                    // Collect remaining as categories until end or next keyword
                    i++;
                    while (i < args.length) {
                        String t = args[i].toUpperCase();
                        if ("HEIGHT".equals(t)) break;
                        categories.add(t);
                        i++;
                    }
                    continue;  // don't increment i again
                }
                default -> {}
            }
            i++;
        }

        if (heightMm <= 0)
            return VerbResult.fail(keyword(), "HEIGHT must be positive", null);
        if (categories.isEmpty())
            return VerbResult.fail(keyword(), "at least one room category required", null);

        Connection conn = ctx.bomConn();

        // Validate unit BOM exists
        MBOM unit = MBOM.get(conn, unitBomId);
        if (unit == null)
            return VerbResult.fail(keyword(),
                "unit BOM not found: " + unitBomId, null);

        // EntityType guard
        if (MBOM.ENTITYTYPE_Dictionary.equals(unit.getEntityType()))
            return VerbResult.fail(keyword(),
                unitBomId + " is read-only (EntityType=D)", null);

        // Compute width/depth from existing unit children (or use large defaults)
        List<MBOMLine> existingLines = MBOMLine.getByBom(conn, unitBomId);
        int widthMm = 0, depthMm = 0;
        int maxSeq = 0;
        for (MBOMLine line : existingLines) {
            widthMm = Math.max(widthMm, line.getAllocatedWidthMm());
            depthMm = Math.max(depthMm, line.getAllocatedDepthMm());
            maxSeq = Math.max(maxSeq, line.getSequence());
        }
        if (widthMm == 0) widthMm = 10000;  // default if no existing floors
        if (depthMm == 0) depthMm = 8000;

        // Step 1: Create slab BOM
        String slabBomId = "SY_SLAB_" + floorName + "_" + widthMm + "x" + depthMm;
        int slabHeightMm = 200;  // standard slab thickness
        MBOM slabExisting = MBOM.get(conn, slabBomId);
        if (slabExisting == null) {
            MBOM slab = new MBOM(conn);
            slab.setBomId(slabBomId);
            slab.setBomName("Slab " + floorName);
            slab.setBomType("ITEM");
            slab.setBomCategory("SL");
            slab.setGroupBy("BUILDING");
            slab.setIsActive(true);
            slab.setBomLevel("ITEM");
            slab.setSeqNo(10);
            slab.setEntityType(MBOM.ENTITYTYPE_User);
            slab.save();
        }

        // Step 2: Create floor BOM via CREATE FLOOR logic
        CreateFloorVerb createFloor = new CreateFloorVerb();
        VerbResult<CreateFloorVerb.CreateFloorPayload> floorResult = createFloor.execute(
            ctx, floorName, "ROOMS",
            String.join(" ", categories).split("\\s+")[0],  // we need to pass all cats
            "WIDTH", String.valueOf(widthMm),
            "DEPTH", String.valueOf(depthMm),
            "HEIGHT", String.valueOf(heightMm));

        // If categories > 1, we need a proper args array
        if (categories.size() > 1) {
            List<String> floorArgs = new ArrayList<>();
            floorArgs.add(floorName);
            floorArgs.add("ROOMS");
            floorArgs.addAll(categories);
            floorArgs.add("WIDTH");
            floorArgs.add(String.valueOf(widthMm));
            floorArgs.add("DEPTH");
            floorArgs.add(String.valueOf(depthMm));
            floorArgs.add("HEIGHT");
            floorArgs.add(String.valueOf(heightMm));
            floorResult = createFloor.execute(ctx, floorArgs.toArray(new String[0]));
        }

        String floorBomId;
        if (!floorResult.pass()) {
            // Floor creation failed — still add slab
            floorBomId = null;
        } else {
            floorBomId = floorResult.payload().bomId();
        }

        // Step 3: Add slab line to unit
        MBOMLine slabLine = new MBOMLine(conn);
        slabLine.setBomId(unitBomId);
        slabLine.setChildProductId(slabBomId);
        slabLine.setRole("SLAB_" + floorName);
        slabLine.setSequence(maxSeq + 10);
        slabLine.setDx(0);
        slabLine.setDy(0);
        slabLine.setDz(0);
        slabLine.setAllocatedWidthMm(widthMm);
        slabLine.setAllocatedDepthMm(depthMm);
        slabLine.setAllocatedHeightMm(slabHeightMm);
        slabLine.setComponentType("MAKE");
        slabLine.setEntityType(MBOM.ENTITYTYPE_User);
        slabLine.setIsActive(true);
        slabLine.save();

        // Step 4: Add floor line to unit (if creation succeeded)
        if (floorBomId != null) {
            MBOMLine floorLine = new MBOMLine(conn);
            floorLine.setBomId(unitBomId);
            floorLine.setChildProductId(floorBomId);
            floorLine.setRole("FLOOR_" + floorName);
            floorLine.setSequence(maxSeq + 20);
            floorLine.setDx(0);
            floorLine.setDy(0);
            floorLine.setDz(0);
            floorLine.setAllocatedWidthMm(widthMm);
            floorLine.setAllocatedDepthMm(depthMm);
            floorLine.setAllocatedHeightMm(heightMm);
            floorLine.setComponentType("MAKE");
            floorLine.setEntityType(MBOM.ENTITYTYPE_User);
            floorLine.setIsActive(true);
            floorLine.save();
        }

        // Step 5: Recompute all dz via STACK FLOORS
        StackFloorsVerb stacker = new StackFloorsVerb();
        VerbResult<StackFloorsVerb.StackPayload> stackResult =
            stacker.execute(ctx, "IN", unitBomId);

        double newRoofDz = stackResult.pass()
            ? stackResult.payload().totalHeight()
            : 0.0;

        return VerbResult.ok(keyword(),
            String.format("ADD FLOOR %s TO %s → slab=%s, floor=%s, roofDz=%.3fm",
                floorName, unitBomId, slabBomId,
                floorBomId != null ? floorBomId : "FAILED",
                newRoofDz),
            new AddFloorPayload(unitBomId, floorBomId, slabBomId, newRoofDz));
    }

    public record AddFloorPayload(
        String unitBomId, String floorBomId,
        String slabBomId, double newRoofDz
    ) {}
}
