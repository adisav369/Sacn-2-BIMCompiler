package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.BomTemplateComposer;
import com.bim.ormsandbox.po.BomTemplateComposer.CompositionReport;
import com.bim.ormsandbox.po.BomTemplateComposer.NodeSelection;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * COMPOSE BUILDING &lt;docBaseType&gt; &lt;width&gt; &lt;depth&gt; &lt;height&gt; UNITS &lt;n&gt;
 *
 * <p>Level 3 building verb (§18.8). <b>Delegates to
 * {@link BomTemplateComposer#compose()}</b> for MRP explosion — no
 * reinvention. Materialises the CompositionReport into m_bom + m_bom_line
 * tree via DAO saves.
 *
 * <p>Duplex handling: PR + PARTY_WALL_PI → sets rotation_rule=π on UNIT_B.
 *
 * <p>Grammar:
 * <pre>
 *   COMPOSE BUILDING RESIDENTIAL 10000 8000 6000 UNITS 1
 *   COMPOSE BUILDING COMMERCIAL 40000 30000 16000 UNITS 1
 * </pre>
 */
public class ComposeBuildingVerb implements Verb<ComposeBuildingVerb.ComposeBuildingPayload> {

    /** Map from building_type shorthand to M_BomCategory doc_type (short code). */
    private static final Map<String, String> DOC_TYPE_MAP = Map.of(
        "RESIDENTIAL", "RE",
        "COMMERCIAL", "CO",
        "INDUSTRIAL", "IN"
    );

    @Override
    public String keyword() { return "COMPOSE BUILDING"; }

    @Override
    public VerbResult<ComposeBuildingPayload> execute(VerbContext ctx, String... args)
            throws SQLException {

        // Parse: COMPOSE BUILDING <docBaseType> <width> <depth> <height> UNITS <n>
        if (args.length < 5)
            return VerbResult.fail(keyword(),
                "usage: COMPOSE BUILDING <docBaseType> <width> <depth> <height> UNITS <n>", null);

        String docBaseType = args[0].toUpperCase();
        String docType = DOC_TYPE_MAP.get(docBaseType);
        if (docType == null)
            return VerbResult.fail(keyword(),
                "unknown building type: " + docBaseType
                    + ", must be one of " + DOC_TYPE_MAP.keySet(), null);

        int widthMm, depthMm, heightMm;
        try {
            widthMm = Integer.parseInt(args[1]);
            depthMm = Integer.parseInt(args[2]);
            heightMm = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            return VerbResult.fail(keyword(), "invalid dimension: " + e.getMessage(), null);
        }

        // Parse UNITS
        int numUnits = 1;
        for (int i = 4; i < args.length - 1; i++) {
            if ("UNITS".equalsIgnoreCase(args[i])) {
                numUnits = Integer.parseInt(args[i + 1]);
                break;
            }
        }

        Connection conn = ctx.bomConn();

        // Step 1: Delegate to BomTemplateComposer for MRP explosion
        CompositionReport report = BomTemplateComposer.compose(
            conn, docType, widthMm, depthMm, heightMm, numUnits);

        if (report.selections().isEmpty())
            return VerbResult.fail(keyword(),
                "no template found for " + docType, null);

        // Step 2: Create the BUILDING BOM header
        String prefix = docBaseType.substring(0, Math.min(2, docBaseType.length()));
        String bldgBomId = String.format("SY_%s_BLDG_%dx%dx%d",
            prefix, widthMm, depthMm, heightMm);

        MBOM existing = MBOM.get(conn, bldgBomId);
        if (existing != null)
            return VerbResult.fail(keyword(), bldgBomId + " already exists", null);

        MBOM bldgBom = new MBOM(conn);
        bldgBom.setBomId(bldgBomId);
        bldgBom.setBomName(docBaseType + " Building " + widthMm + "x" + depthMm + "x" + heightMm);
        bldgBom.setBomType("BUILDING");
        bldgBom.setBomCategory(prefix);   // RE, CO, IN — derived from docBaseType
        bldgBom.setGroupBy("BUILDING");
        bldgBom.setIsActive(true);
        bldgBom.setBomLevel("BUILDING");
        bldgBom.setSeqNo(10);
        bldgBom.setEntityType(MBOM.ENTITYTYPE_User);
        bldgBom.save();

        // Step 3: Materialise leaf selections into m_bom_line entries
        int seq = 10;
        int totalLines = 0;
        int floorCount = 0;

        for (NodeSelection node : report.selections()) {
            if (node.selectedBomId() == null) continue;  // skip unresolved gaps

            MBOMLine line = new MBOMLine(conn);
            line.setBomId(bldgBomId);
            line.setChildProductId(node.selectedBomId());
            line.setRole(node.categoryId() + "_" + node.categoryName().replace(' ', '_'));
            line.setSequence(seq);
            line.setDx(0);
            line.setDy(0);
            line.setDz(0);
            line.setAllocatedWidthMm(node.allocW());
            line.setAllocatedDepthMm(node.allocD());
            line.setAllocatedHeightMm(node.allocH());
            line.setComponentType("MAKE");
            line.setEntityType(MBOM.ENTITYTYPE_User);
            line.setIsActive(true);

            // Duplex mirroring: PARTY_WALL_PI sets rotation_rule=π
            if ("PARTY_WALL_PI".equals(node.mirroringRule())) {
                line.setRotationRule(String.valueOf(Math.PI));
            } else {
                line.setRotationRule("0");
            }

            line.save();
            seq += 10;
            totalLines++;

            // Count floors (categories that represent floor-level nodes)
            if ("GF".equals(node.categoryId()) || "L1".equals(node.categoryId())
                    || "L2".equals(node.categoryId())) {
                floorCount++;
            }
        }

        // Step 4: Stack floors to compute dz offsets
        if (totalLines > 1) {
            new StackFloorsVerb().execute(ctx, "IN", bldgBomId);
        }

        int totalBoms = 1 + totalLines;  // unit header + lines
        String[] gaps = report.gaps().toArray(new String[0]);

        return VerbResult.ok(keyword(),
            String.format("COMPOSE BUILDING %s %dx%dx%d UNITS %d → %s (%d lines, %d gaps)",
                docBaseType, widthMm, depthMm, heightMm, numUnits,
                bldgBomId, totalLines, gaps.length),
            new ComposeBuildingPayload(bldgBomId, totalBoms, totalLines, floorCount, gaps));
    }

    public record ComposeBuildingPayload(
        String bldgBomId, int totalBoms, int totalLines,
        int floors, String[] gaps
    ) {}
}
