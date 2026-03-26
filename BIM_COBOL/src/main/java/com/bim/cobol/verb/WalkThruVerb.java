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
 * WALK THRU &lt;doc_sub_type&gt; — structured BOM traversal.
 *
 * <p>Walker enters each sub-assembly in the BOM hierarchy, accumulating
 * tack coordinates (BUILDING → FLOOR → SET → BUY). C_OrderLine per slot,
 * spatial data from M_BOM_Line dx/dy/dz.
 *
 * <p>Triggered when the BOM has sub-assemblies — the walker detects them
 * structurally (child_product_id matches a bom_id) and enters each one.
 *
 * <p>For the Rosetta Stone POC, WALK THRU proves that the structured BOM
 * hierarchy produces the same leaves as EN-BLOC. Delta between _s and _e
 * reveals what the structured BOM is still missing.
 *
 * <p>Grammar: {@code WALK THRU SH} or {@code WALK THRU DX}
 */
public class WalkThruVerb implements Verb<WalkThruVerb.WalkThruPayload> {

    @Override
    public String keyword() { return "WALK THRU"; }

    @Override
    public VerbResult<WalkThruPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(), "usage: WALK THRU <doc_sub_type>", null);

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
                    "No BUILDING BOM for DocSubType=" + docSubType,
                    new WalkThruPayload(null, docSubType, projectName, 0, 0));

        // Walk the structured BOM hierarchy (M_Product from ERP.db)
        try (Connection compConn = DriverManager.getConnection("jdbc:sqlite:library/ERP.db")) {
            PlacementCollectorVisitor visitor = new PlacementCollectorVisitor(conn, projectName);
            BOMWalker walker = new BOMWalker(conn, compConn);
            walker.walkSelf(match.getBomId(), List.of(visitor), projectName);

            List<PlacementLoader.Placement> placements = visitor.getPlacements();

            // Count sub-assemblies entered (depth > 0 levels)
            int subAssemblies = visitor.getSubAssemblyCount();

            WalkThruPayload payload = new WalkThruPayload(
                    match.getBomId(), docSubType, projectName,
                    placements.size(), subAssemblies);

            return VerbResult.ok(keyword(),
                    String.format("WALK THRU %s: %s → %d placements, %d sub-assemblies",
                            docSubType, match.getBomId(), placements.size(), subAssemblies),
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

    public record WalkThruPayload(
            String bomId,
            String docSubType,
            String projectName,
            int placementCount,
            int subAssemblyCount
    ) {}
}
