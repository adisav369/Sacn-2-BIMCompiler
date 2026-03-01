package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.cobol.geometry.FittingConnector;
import com.bim.cobol.geometry.FittingConnector.ConnectionSpec;
import com.bim.cobol.geometry.FittingConnector.ExistingPipe;
import com.bim.cobol.geometry.FittingConnector.Fitting;
import com.bim.compiler.geometry.Point3D;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * CONNECT FITTINGS &lt;extracted_db_path&gt; [SYSTEM &lt;name&gt;] [MAX_GAP &lt;meters&gt;]
 *
 * <p>Reads fittings and pipe segments from an extracted IFC database,
 * identifies orphan fitting pairs missing connecting pipe segments,
 * and generates pipe connections to fill the gaps.
 *
 * <p>Read-only verb — opens extracted DB as read-only source.
 * Does not modify any database. Returns payload with computed connections.
 */
public class ConnectFittingsVerb implements Verb<ConnectFittingsVerb.ConnectFittingsPayload> {

    /** Default maximum gap to bridge (meters). */
    private static final double DEFAULT_MAX_GAP_M = 3.0;

    @Override
    public String keyword() { return "CONNECT FITTINGS"; }

    @Override
    public VerbResult<ConnectFittingsPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length < 1)
            return VerbResult.fail(keyword(),
                    "usage: CONNECT FITTINGS <extracted_db_path>"
                            + " [SYSTEM <name>] [MAX_GAP <meters>]", null);

        String extractedPath = args[0];
        String systemFilter = null;
        double maxGapM = DEFAULT_MAX_GAP_M;

        for (int i = 1; i < args.length - 1; i++) {
            if ("SYSTEM".equals(args[i])) systemFilter = args[i + 1];
            if ("MAX_GAP".equals(args[i])) maxGapM = Double.parseDouble(args[i + 1]);
        }

        // Open extracted DB read-only
        try (Connection extConn = DriverManager.getConnection(
                "jdbc:sqlite:" + extractedPath)) {

            // Load fittings
            List<Fitting> fittings = loadFittings(extConn, systemFilter);
            if (fittings.isEmpty())
                return VerbResult.fail(keyword(), "no fittings found", null);

            // Load existing pipe segments
            List<ExistingPipe> pipes = loadPipes(extConn, systemFilter);

            // Run connector algorithm
            List<ConnectionSpec> connections = FittingConnector.connect(
                    fittings, pipes, maxGapM);

            // Compute stats
            double totalLengthM = connections.stream()
                    .mapToDouble(ConnectionSpec::gapM).sum();
            long elbowGaps = connections.stream()
                    .filter(c -> c.from().type().contains("Elbow")
                            || c.to().type().contains("Elbow"))
                    .count();

            ConnectFittingsPayload payload = new ConnectFittingsPayload(
                    extractedPath, fittings.size(), pipes.size(),
                    connections, totalLengthM, elbowGaps);

            return VerbResult.ok(keyword(),
                    String.format("%d connections generated (%.1fm total pipe), "
                                    + "%d fittings, %d existing pipes",
                            connections.size(), totalLengthM,
                            fittings.size(), pipes.size()),
                    payload);
        }
    }

    // ── DB loaders ──────────────────────────────────────────────

    private List<Fitting> loadFittings(Connection conn, String systemFilter)
            throws SQLException {
        List<Fitting> result = new ArrayList<>();
        String sql = "SELECT m.id, m.guid, m.element_type, m.storey,"
                + " t.center_x, t.center_y, t.center_z,"
                + " r.maxX - r.minX AS dx, r.maxY - r.minY AS dy, r.maxZ - r.minZ AS dz"
                + " FROM elements_meta m"
                + " JOIN element_transforms t ON m.guid = t.guid"
                + " JOIN elements_rtree r ON m.id = r.id"
                + " WHERE m.ifc_class = 'IfcFlowFitting'";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = rs.getString("element_type");
                // Infer system from nearest pipe later; for now use storey as proxy
                String system = rs.getString("storey");
                result.add(new Fitting(
                        rs.getString("guid"), type, system,
                        new Point3D(rs.getDouble("center_x"),
                                rs.getDouble("center_y"),
                                rs.getDouble("center_z")),
                        rs.getDouble("dx"), rs.getDouble("dy"), rs.getDouble("dz")));
            }
        }
        return result;
    }

    private List<ExistingPipe> loadPipes(Connection conn, String systemFilter)
            throws SQLException {
        List<ExistingPipe> result = new ArrayList<>();
        String sql = "SELECT m.id, m.guid, m.element_type,"
                + " t.center_x, t.center_y, t.center_z,"
                + " r.maxX - r.minX AS dx, r.maxY - r.minY AS dy, r.maxZ - r.minZ AS dz"
                + " FROM elements_meta m"
                + " JOIN element_transforms t ON m.guid = t.guid"
                + " JOIN elements_rtree r ON m.id = r.id"
                + " WHERE m.ifc_class = 'IfcFlowSegment'"
                + " AND m.element_type LIKE '%Pipe%'";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String type = rs.getString("element_type");
                String system = extractSystem(type);
                result.add(ExistingPipe.fromRtree(
                        rs.getString("guid"), type, system,
                        new Point3D(rs.getDouble("center_x"),
                                rs.getDouble("center_y"),
                                rs.getDouble("center_z")),
                        rs.getDouble("dx"), rs.getDouble("dy"), rs.getDouble("dz")));
            }
        }
        return result;
    }

    /** Extract system name from Revit element_type like "Pipe Types:Cold Water:583054". */
    public static String extractSystem(String elementType) {
        if (elementType == null) return "UNKNOWN";
        String[] parts = elementType.split(":");
        if (parts.length >= 2) return parts[1].trim();
        return "UNKNOWN";
    }

    /** Payload with all connection results. */
    public record ConnectFittingsPayload(
            String extractedDbPath,
            int fittingCount,
            int existingPipeCount,
            List<ConnectionSpec> connections,
            double totalPipeLengthM,
            long elbowGapCount
    ) {}
}
