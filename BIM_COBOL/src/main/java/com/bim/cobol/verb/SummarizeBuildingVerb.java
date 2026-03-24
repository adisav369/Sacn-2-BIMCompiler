package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.*;

/**
 * SUMMARIZE BUILDING &lt;doc_sub_type&gt; — overview of compiled building in output.db.
 *
 * <p>Queries the output database (elements_meta, elements_rtree, spatial_structure,
 * c_orderline) and returns a structured project overview.
 *
 * <p>Reports: element count, storey breakdown, IFC class distribution,
 * spatial extents (AABB).
 *
 * <p>Grammar: {@code SUMMARIZE BUILDING SH} or {@code SUMMARIZE BUILDING DX}
 *
 * <p>Read-only — no DB writes. Requires outputConn.
 */
public class SummarizeBuildingVerb implements Verb<SummarizeBuildingVerb.SummaryPayload> {

    @Override
    public String keyword() { return "SUMMARIZE BUILDING"; }

    @Override
    public VerbResult<SummaryPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: SUMMARIZE BUILDING <doc_sub_type>", null);

        Connection outputConn = ctx.outputConn();
        if (outputConn == null)
            return VerbResult.fail(keyword(),
                "outputConn required — SUMMARIZE BUILDING reads output.db", null);

        String docSubType = args[0].toUpperCase();

        // 1. Total element count
        int totalElements = countQuery(outputConn,
            "SELECT COUNT(*) FROM elements_meta");

        if (totalElements == 0)
            return VerbResult.fail(keyword(),
                docSubType + ": output.db has 0 elements — not compiled?", null);

        // 2. Storey breakdown
        List<StoreyBreakdown> storeys = new ArrayList<>();
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT storey, COUNT(*) as cnt FROM elements_meta GROUP BY storey ORDER BY storey")) {
            while (rs.next()) {
                storeys.add(new StoreyBreakdown(
                    rs.getString("storey"), rs.getInt("cnt")));
            }
        }

        // 3. IFC class distribution (top 20)
        List<ClassBreakdown> ifcClasses = new ArrayList<>();
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT ifc_class, COUNT(*) as cnt FROM elements_meta "
                 + "GROUP BY ifc_class ORDER BY cnt DESC LIMIT 20")) {
            while (rs.next()) {
                ifcClasses.add(new ClassBreakdown(
                    rs.getString("ifc_class"), rs.getInt("cnt")));
            }
        }

        // 4. Spatial extents (AABB) from elements_rtree
        double[] aabb = {0, 0, 0, 0, 0, 0};
        try (Statement stmt = outputConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), "
                 + "MIN(minZ), MAX(maxZ) FROM elements_rtree")) {
            if (rs.next()) {
                aabb[0] = rs.getDouble(1); aabb[1] = rs.getDouble(2);
                aabb[2] = rs.getDouble(3); aabb[3] = rs.getDouble(4);
                aabb[4] = rs.getDouble(5); aabb[5] = rs.getDouble(6);
            }
        }

        // 5. C_OrderLine count (if table exists)
        int orderLineCount = safeCount(outputConn,
            "SELECT COUNT(*) FROM c_orderline");

        // 6. Spatial slot level counts (deprecated — CO_EmptySpace removed)
        int esL0 = 0; // CO_EmptySpace removed — WHERE = M_BOM_Line dx/dy/dz
        int esL1 = 0;
        int esL2 = 0;

        double width = aabb[1] - aabb[0];
        double depth = aabb[3] - aabb[2];
        double height = aabb[5] - aabb[4];

        SummaryPayload payload = new SummaryPayload(
            docSubType, totalElements, storeys.size(),
            storeys, ifcClasses,
            aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5],
            width, depth, height,
            orderLineCount, esL0, esL1, esL2);

        return VerbResult.ok(keyword(),
            String.format("SUMMARIZE %s: %d elements, %d storeys, "
                + "AABB=%.1fx%.1fx%.1fm, C_OrderLine=%d, ES=L0:%d/L1:%d/L2:%d",
                docSubType, totalElements, storeys.size(),
                width, depth, height,
                orderLineCount, esL0, esL1, esL2),
            payload);
    }

    private int countQuery(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int safeCount(Connection conn, String sql) {
        try {
            return countQuery(conn, sql);
        } catch (SQLException e) {
            return -1; // table doesn't exist
        }
    }

    public record StoreyBreakdown(String storey, int elementCount) {}
    public record ClassBreakdown(String ifcClass, int elementCount) {}

    public record SummaryPayload(
        String docSubType, int totalElements, int storeyCount,
        List<StoreyBreakdown> storeys, List<ClassBreakdown> ifcClasses,
        double minX, double maxX, double minY, double maxY,
        double minZ, double maxZ,
        double width, double depth, double height,
        int orderLineCount, int esL0, int esL1, int esL2
    ) {}
}
