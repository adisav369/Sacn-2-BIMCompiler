package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.compiler.bom.walker.BOMWalker;
import com.bim.compiler.bom.walker.PlacementCollectorVisitor;
import com.bim.compiler.dsl.PlacementLoader;
import com.bim.ormsandbox.po.MBOM;

import java.sql.*;
import java.util.List;

/**
 * EN-BLOC &lt;doc_sub_type&gt; — singularity compilation path.
 *
 * <p>When C_Order.C_BPartner matches the BUILDING BOM's DocSubType and AABB fits,
 * the compiler takes the BOM as-is without recalculating through hierarchy layers.
 * One C_OrderLine, one CO_EmptySpaceLine.
 *
 * <p>WALK THRU is the proper normal path — recalculates by tacking
 * through each BOM layer. Both produce the same result when the data
 * stack is consistent. EN-BLOC is the basic sanity proof.
 *
 * <p>Grammar: {@code EN-BLOC SH} or {@code EN-BLOC DX}
 */
public class EnBlocVerb implements Verb<EnBlocVerb.EnBlocPayload> {

    @Override
    public String keyword() { return "EN-BLOC"; }

    @Override
    public VerbResult<EnBlocPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(), "usage: EN-BLOC <doc_sub_type>", null);

        String docSubType = args[0].toUpperCase();
        Connection conn = ctx.bomConn();

        // Find the building type from C_DocType
        String projectName = lookupProjectName(conn, docSubType);
        if (projectName == null)
            return VerbResult.fail(keyword(),
                    "No C_DocType for DocSubType=" + docSubType, null);

        // Find the BUILDING BOM for this building type
        MBOM match = MBOM.getBuildingBom(conn, docSubType);

        if (match == null)
            return VerbResult.fail(keyword(),
                    "No BUILDING BOM for DocSubType=" + docSubType, null);

        // Walk the BOM — singularity: take it whole (M_Product from disc_validation.db)
        try (Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/disc_validation.db")) {
            PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(conn, projectName);
            BOMWalker walker = new BOMWalker(conn, compConn);
            walker.walkSelf(match.getBomId(), List.of(visitor), projectName);

            List<PlacementLoader.Placement> placements = visitor.getPlacements();

            EnBlocPayload payload = new EnBlocPayload(
                    match.getBomId(), docSubType, projectName, placements.size());

            return VerbResult.ok(keyword(),
                    String.format("EN-BLOC %s: %s → %d placements (singularity)",
                            docSubType, match.getBomId(), placements.size()),
                    payload);
        }
    }

    private String lookupProjectName(Connection conn, String docSubType) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                     "SELECT ProjectName FROM C_DocType WHERE DocSubType=?")) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public record EnBlocPayload(
            String bomId,
            String docSubType,
            String projectName,
            int placementCount
    ) {}
}
