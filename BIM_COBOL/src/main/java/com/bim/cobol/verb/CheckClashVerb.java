package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CHECK CLASH &lt;db_path&gt; [CLEARANCE &lt;mm&gt;]
 *
 * <p>Detects MEP-to-structural bounding-box clashes via direct RTREE query.
 * MEP classes: IfcFlowSegment, IfcFlowFitting, IfcFlowTerminal,
 * IfcFlowController, IfcLightFixture.
 * Structural classes: IfcBeam, IfcColumn, IfcSlab.
 *
 * <p>Read-only verb — opens target DB, never writes.
 */
public class CheckClashVerb implements Verb<CheckClashVerb.ClashCheckPayload> {

    /** Default clearance in meters (50mm = BIMConstants.MEP_STRUCTURE_CLEARANCE). */
    private static final double DEFAULT_CLEARANCE_M = 0.05;

    @Override
    public String keyword() { return "CHECK CLASH"; }

    @Override
    public VerbResult<ClashCheckPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 1)
            return VerbResult.fail(keyword(),
                    "usage: CHECK CLASH <db_path> [CLEARANCE <mm>]", null);

        String dbPath = args[0];
        double clearanceM = DEFAULT_CLEARANCE_M;
        for (int i = 1; i < args.length - 1; i++) {
            if ("CLEARANCE".equals(args[i])) clearanceM = Double.parseDouble(args[i + 1]) / 1000.0;
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            List<ClashRecord> clashes = detectClashes(conn, clearanceM);

            // Count structural and MEP elements for context
            int mepCount = countByClasses(conn,
                    "'IfcFlowSegment','IfcFlowFitting','IfcFlowTerminal',"
                            + "'IfcFlowController','IfcLightFixture',"
                            + "'IfcPipeSegment','IfcPipeFitting',"
                            + "'IfcDuctSegment','IfcDuctFitting'");
            int strCount = countByClasses(conn, "'IfcBeam','IfcColumn','IfcSlab'");

            ClashCheckPayload payload = new ClashCheckPayload(
                    dbPath, clearanceM, mepCount, strCount, clashes);

            if (clashes.isEmpty())
                return VerbResult.ok(keyword(),
                        String.format("0 clashes (%d MEP, %d structural, %.0fmm clearance)",
                                mepCount, strCount, clearanceM * 1000),
                        payload);
            else
                return VerbResult.ok(keyword(),
                        String.format("%d clashes found (%d MEP, %d structural, %.0fmm clearance)",
                                clashes.size(), mepCount, strCount, clearanceM * 1000),
                        payload);
        } catch (SQLException e) {
            return VerbResult.fail(keyword(), "DB error: " + e.getMessage(), null);
        }
    }

    // ── Clash detection ────────────────────────────────────────────

    private List<ClashRecord> detectClashes(Connection conn, double clearanceM)
            throws SQLException {
        List<ClashRecord> clashes = new ArrayList<>();

        // Pure SQL RTREE overlap query with clearance expansion
        String sql = "SELECT mep.guid AS mep_guid, mep.ifc_class AS mep_class,"
                + " str.guid AS str_guid, str.ifc_class AS str_class,"
                + " MAX(0, MIN(mr.maxX, sr.maxX) - MAX(mr.minX, sr.minX)) AS overlap_x,"
                + " MAX(0, MIN(mr.maxY, sr.maxY) - MAX(mr.minY, sr.minY)) AS overlap_y,"
                + " MAX(0, MIN(mr.maxZ, sr.maxZ) - MAX(mr.minZ, sr.minZ)) AS overlap_z"
                + " FROM elements_meta mep"
                + " JOIN elements_rtree mr ON mep.id = mr.id"
                + " JOIN elements_meta str ON str.ifc_class IN ('IfcBeam','IfcColumn','IfcSlab')"
                + " JOIN elements_rtree sr ON str.id = sr.id"
                + " WHERE mep.ifc_class IN ('IfcFlowSegment','IfcFlowFitting','IfcFlowTerminal',"
                + "   'IfcFlowController','IfcLightFixture',"
                + "   'IfcPipeSegment','IfcPipeFitting','IfcDuctSegment','IfcDuctFitting')"
                + " AND mr.minX < sr.maxX + ? AND mr.maxX > sr.minX - ?"
                + " AND mr.minY < sr.maxY + ? AND mr.maxY > sr.minY - ?"
                + " AND mr.minZ < sr.maxZ + ? AND mr.maxZ > sr.minZ - ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) ps.setDouble(i, clearanceM);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clashes.add(new ClashRecord(
                            rs.getString("mep_guid"), rs.getString("mep_class"),
                            rs.getString("str_guid"), rs.getString("str_class"),
                            rs.getDouble("overlap_x"),
                            rs.getDouble("overlap_y"),
                            rs.getDouble("overlap_z")));
                }
            }
        }
        return clashes;
    }

    private int countByClasses(Connection conn, String classesIn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM elements_meta WHERE ifc_class IN (" + classesIn + ")";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Records ────────────────────────────────────────────────────

    /** A single MEP-structural bbox clash. */
    public record ClashRecord(
            String mepGuid, String mepClass,
            String structuralGuid, String structuralClass,
            double overlapX, double overlapY, double overlapZ
    ) {
        public double overlapVolume() { return overlapX * overlapY * overlapZ; }
    }

    /** Verb payload with clash results. */
    public record ClashCheckPayload(
            String dbPath,
            double clearanceM,
            int mepCount,
            int structuralCount,
            List<ClashRecord> clashes
    ) {}
}
