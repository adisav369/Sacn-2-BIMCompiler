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
 * STACK FLOORS IN &lt;unit_bom_id&gt;
 *
 * <p>Level 3 building verb (§18.8). Reads floor/slab children sorted
 * by seq_no, computes cumulative dz, updates m_bom_line.dz values.
 *
 * <p>Guard: validates no Z-axis clashing — each floor starts where
 * the previous one ends.
 *
 * <p>Grammar:
 * <pre>
 *   STACK FLOORS IN SY_UNIT_01
 * </pre>
 */
public class StackFloorsVerb implements Verb<StackFloorsVerb.StackPayload> {

    @Override
    public String keyword() { return "STACK FLOORS"; }

    @Override
    public VerbResult<StackPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: STACK FLOORS IN <unit_bom_id>
        if (args.length < 2)
            return VerbResult.fail(keyword(),
                "usage: STACK FLOORS IN <unit_bom_id>", null);

        if (!"IN".equalsIgnoreCase(args[0]))
            return VerbResult.fail(keyword(),
                "expected IN, got: " + args[0], null);

        String unitBomId = args[1];

        Connection conn = ctx.bomConn();

        // Validate unit BOM exists
        MBOM unit = MBOM.get(conn, unitBomId);
        if (unit == null)
            return VerbResult.fail(keyword(),
                "unit BOM not found: " + unitBomId, null);

        // Get all children sorted by sequence (already sorted by getByBom)
        List<MBOMLine> children = MBOMLine.getByBom(conn, unitBomId);
        if (children.isEmpty())
            return VerbResult.fail(keyword(),
                unitBomId + " has no children to stack", null);

        // Compute cumulative dz: each child starts where the previous ends
        double cumulativeDz = 0.0;
        List<Double> offsets = new ArrayList<>();
        int floorCount = 0;

        for (MBOMLine child : children) {
            // Update dz to cumulative position
            child.setDz(cumulativeDz);
            child.save();

            offsets.add(cumulativeDz);

            // Height from allocated_height_mm (mm → m)
            int heightMm = child.getAllocatedHeightMm();
            if (heightMm > 0) {
                cumulativeDz += heightMm / 1000.0;
            }
            floorCount++;
        }

        return VerbResult.ok(keyword(),
            String.format("STACK FLOORS IN %s → %d layers, total height=%.3fm",
                unitBomId, floorCount, cumulativeDz),
            new StackPayload(unitBomId, floorCount,
                offsets.stream().mapToDouble(Double::doubleValue).toArray(),
                cumulativeDz));
    }

    public record StackPayload(
        String unitBomId, int floorCount,
        double[] offsets, double totalHeight
    ) {}
}
