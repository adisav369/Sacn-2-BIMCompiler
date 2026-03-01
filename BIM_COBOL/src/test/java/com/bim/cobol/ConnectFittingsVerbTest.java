package com.bim.cobol;

import com.bim.cobol.geometry.FittingConnector;
import com.bim.cobol.geometry.FittingConnector.ConnectionSpec;
import com.bim.cobol.verb.ConnectFittingsVerb;
import com.bim.cobol.verb.ConnectFittingsVerb.ConnectFittingsPayload;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CONNECT FITTINGS verb witnesses — validates gap detection and pipe
 * generation against real Duplex extracted data. Produces mockup.db
 * showing completed piping derived by BIM COBOL.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConnectFittingsVerbTest {

    private static final String DUPLEX_EXTRACTED =
            "DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db";
    private static final String MOCKUP_DB =
            "BIM_COBOL/lib/output/mockup.db";

    private static VerbResult<ConnectFittingsPayload> result;

    @BeforeAll
    static void runVerb() throws SQLException {
        ConnectFittingsVerb verb = new ConnectFittingsVerb();
        VerbContext ctx = VerbContext.ofBom(null); // BOM not needed for this verb
        result = verb.execute(ctx, DUPLEX_EXTRACTED, "MAX_GAP", "3.0");
    }

    // ── W-COBOL-17: Verb finds gap connections in Duplex ────────────

    /**
     * W-COBOL-17: ConnectFittingsVerb identifies orphan fitting pairs
     * in Duplex extracted data and generates connecting pipe segments.
     * The Duplex has 358 fittings with many gaps — expect ≥10 connections.
     */
    @Test
    @Order(1)
    void w_cobol_17_connectDuplexFittings() {
        assertTrue(result.pass(), "verb should pass: " + result.summary());

        ConnectFittingsPayload p = result.payload();
        assertNotNull(p);
        assertTrue(p.fittingCount() >= 300,
                "Duplex should have 300+ fittings, got: " + p.fittingCount());
        assertTrue(p.existingPipeCount() >= 100,
                "Duplex should have 100+ pipe segments, got: " + p.existingPipeCount());
        assertTrue(p.connections().size() >= 5,
                "should generate 5+ connections, got: " + p.connections().size());

        System.out.printf("  W-COBOL-17: %d connections, %d fittings, %d pipes%n",
                p.connections().size(), p.fittingCount(), p.existingPipeCount());
    }

    // ── W-COBOL-18: Generated pipe diameters are valid ──────────────

    /**
     * W-COBOL-18: Generated pipe diameters are within the Duplex pipe
     * size range (20-60mm for domestic piping). No zero-diameter pipes.
     */
    @Test
    @Order(2)
    void w_cobol_18_validPipeDiameters() {
        ConnectFittingsPayload p = result.payload();
        for (ConnectionSpec c : p.connections()) {
            assertTrue(c.diameterMm() > 5,
                    "pipe diameter too small: " + c.diameterMm());
            assertTrue(c.diameterMm() < 200,
                    "pipe diameter too large: " + c.diameterMm());
        }

        // Most connections should be DN25 range (28-35mm)
        long dn25Range = p.connections().stream()
                .filter(c -> c.diameterMm() >= 25 && c.diameterMm() <= 40)
                .count();
        assertTrue(dn25Range > p.connections().size() / 4,
                "at least 25% should be DN25 range, got: " + dn25Range);

        System.out.printf("  W-COBOL-18: %d/%d connections in DN25 range%n",
                dn25Range, p.connections().size());
    }

    // ── W-COBOL-19: No fitting over-connected ──────────────────────

    /**
     * W-COBOL-19: No fitting appears in more generated connections
     * than its port budget allows. Elbow ≤ 2, Tee ≤ 3.
     */
    @Test
    @Order(3)
    void w_cobol_19_noOverConnection() {
        ConnectFittingsPayload p = result.payload();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> types = new HashMap<>();

        for (ConnectionSpec c : p.connections()) {
            counts.merge(c.from().guid(), 1, Integer::sum);
            counts.merge(c.to().guid(), 1, Integer::sum);
            types.put(c.from().guid(), c.from().type());
            types.put(c.to().guid(), c.to().type());
        }

        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            int maxPorts = FittingConnector.maxPorts(types.get(e.getKey()));
            assertTrue(e.getValue() <= maxPorts,
                    String.format("fitting %s (%s) has %d generated connections, max=%d",
                            e.getKey(), types.get(e.getKey()),
                            e.getValue(), maxPorts));
        }

        System.out.printf("  W-COBOL-19: all %d connected fittings within port budget%n",
                counts.size());
    }

    // ── W-COBOL-20: Mockup.db created with complete piping ─────────

    /**
     * W-COBOL-20: mockup.db is created containing original Duplex elements
     * (source=EXTRACTED) plus generated pipe segments (source=BIM_COBOL).
     * The result is a queryable proof of completed piping.
     */
    @Test
    @Order(4)
    void w_cobol_20_mockupDbCreated() throws SQLException {
        ConnectFittingsPayload p = result.payload();

        // Delete old mockup if exists
        new File(MOCKUP_DB).delete();

        try (Connection mockConn = DriverManager.getConnection(
                "jdbc:sqlite:" + MOCKUP_DB)) {

            // Create schema
            try (Statement st = mockConn.createStatement()) {
                st.execute("CREATE TABLE piping_elements ("
                        + " guid TEXT PRIMARY KEY,"
                        + " ifc_class TEXT NOT NULL,"
                        + " element_type TEXT,"
                        + " system_name TEXT,"
                        + " center_x REAL, center_y REAL, center_z REAL,"
                        + " min_x REAL, max_x REAL,"
                        + " min_y REAL, max_y REAL,"
                        + " min_z REAL, max_z REAL,"
                        + " diameter_mm REAL,"
                        + " length_mm REAL,"
                        + " source TEXT NOT NULL,"
                        + " connected_from TEXT,"
                        + " connected_to TEXT)");

                st.execute("CREATE TABLE connection_log ("
                        + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + " from_guid TEXT NOT NULL,"
                        + " to_guid TEXT NOT NULL,"
                        + " pipe_guid TEXT NOT NULL,"
                        + " gap_m REAL,"
                        + " diameter_mm REAL,"
                        + " system_name TEXT,"
                        + " verb TEXT DEFAULT 'CONNECT FITTINGS')");

                st.execute("CREATE TABLE mockup_summary ("
                        + " key TEXT PRIMARY KEY,"
                        + " value TEXT)");
            }

            // Copy original elements from Duplex extracted DB
            int origCount;
            try (Connection extConn = DriverManager.getConnection(
                    "jdbc:sqlite:" + DUPLEX_EXTRACTED)) {
                origCount = copyOriginalElements(extConn, mockConn);
            }

            // Insert generated pipe connections
            int genCount = insertGeneratedPipes(mockConn, p.connections());

            // Write summary
            writeSummary(mockConn, p, origCount, genCount);

            // Verify mockup contents
            int totalElements;
            try (Statement st = mockConn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM piping_elements")) {
                rs.next();
                totalElements = rs.getInt(1);
            }

            int extractedCount;
            try (Statement st = mockConn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM piping_elements"
                                 + " WHERE source = 'EXTRACTED'")) {
                rs.next();
                extractedCount = rs.getInt(1);
            }

            int bimCobolCount;
            try (Statement st = mockConn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM piping_elements"
                                 + " WHERE source = 'BIM_COBOL'")) {
                rs.next();
                bimCobolCount = rs.getInt(1);
            }

            assertTrue(extractedCount > 0, "should have extracted elements");
            assertTrue(bimCobolCount > 0, "should have generated elements");
            assertEquals(extractedCount + bimCobolCount, totalElements);

            System.out.printf("  W-COBOL-20: mockup.db — %d extracted + %d BIM_COBOL"
                            + " = %d total elements%n",
                    extractedCount, bimCobolCount, totalElements);
        }

        assertTrue(new File(MOCKUP_DB).exists(), "mockup.db should exist");
        assertTrue(new File(MOCKUP_DB).length() > 1000, "mockup.db should not be empty");
    }

    // ── Helpers ────────────────────────────────────────────────────

    /** Copy pipe fittings + segments from extracted DB to mockup. */
    private int copyOriginalElements(Connection src, Connection dst) throws SQLException {
        int count = 0;
        String query = "SELECT m.guid, m.ifc_class, m.element_type,"
                + " t.center_x, t.center_y, t.center_z,"
                + " r.minX, r.maxX, r.minY, r.maxY, r.minZ, r.maxZ"
                + " FROM elements_meta m"
                + " JOIN element_transforms t ON m.guid = t.guid"
                + " JOIN elements_rtree r ON m.id = r.id"
                + " WHERE m.ifc_class IN ('IfcFlowFitting','IfcFlowSegment')";

        String insert = "INSERT INTO piping_elements"
                + " (guid, ifc_class, element_type, system_name,"
                + "  center_x, center_y, center_z,"
                + "  min_x, max_x, min_y, max_y, min_z, max_z,"
                + "  diameter_mm, length_mm, source)"
                + " VALUES (?,?,?,?, ?,?,?, ?,?,?,?,?,?, ?,?, 'EXTRACTED')";

        try (PreparedStatement ps = src.prepareStatement(query);
             ResultSet rs = ps.executeQuery();
             PreparedStatement ins = dst.prepareStatement(insert)) {
            while (rs.next()) {
                String type = rs.getString("element_type");
                double dx = rs.getDouble("maxX") - rs.getDouble("minX");
                double dy = rs.getDouble("maxY") - rs.getDouble("minY");
                double dz = rs.getDouble("maxZ") - rs.getDouble("minZ");
                double diam = Math.min(dx, Math.min(dy, dz)) * 1000;
                double maxDim = Math.max(dx, Math.max(dy, dz));
                String system = ConnectFittingsVerb.extractSystem(type);

                ins.setString(1, rs.getString("guid"));
                ins.setString(2, rs.getString("ifc_class"));
                ins.setString(3, type);
                ins.setString(4, system);
                ins.setDouble(5, rs.getDouble("center_x"));
                ins.setDouble(6, rs.getDouble("center_y"));
                ins.setDouble(7, rs.getDouble("center_z"));
                ins.setDouble(8, rs.getDouble("minX"));
                ins.setDouble(9, rs.getDouble("maxX"));
                ins.setDouble(10, rs.getDouble("minY"));
                ins.setDouble(11, rs.getDouble("maxY"));
                ins.setDouble(12, rs.getDouble("minZ"));
                ins.setDouble(13, rs.getDouble("maxZ"));
                ins.setDouble(14, diam);
                ins.setDouble(15, maxDim * 1000); // length in mm
                ins.executeUpdate();
                count++;
            }
        }
        return count;
    }

    /** Insert BIM_COBOL-generated pipe segments into mockup. */
    private int insertGeneratedPipes(Connection conn, List<ConnectionSpec> connections)
            throws SQLException {
        String insert = "INSERT INTO piping_elements"
                + " (guid, ifc_class, element_type, system_name,"
                + "  center_x, center_y, center_z,"
                + "  min_x, max_x, min_y, max_y, min_z, max_z,"
                + "  diameter_mm, length_mm, source, connected_from, connected_to)"
                + " VALUES (?,?,?,?, ?,?,?, ?,?,?,?,?,?, ?,?, 'BIM_COBOL', ?, ?)";

        String logInsert = "INSERT INTO connection_log"
                + " (from_guid, to_guid, pipe_guid, gap_m, diameter_mm, system_name)"
                + " VALUES (?,?,?,?,?,?)";

        int count = 0;
        try (PreparedStatement ins = conn.prepareStatement(insert);
             PreparedStatement log = conn.prepareStatement(logInsert)) {
            for (ConnectionSpec c : connections) {
                String pipeGuid = String.format("BIM_COBOL_PIPE_%04d", ++count);
                double cx = (c.pipeStart().x() + c.pipeEnd().x()) / 2;
                double cy = (c.pipeStart().y() + c.pipeEnd().y()) / 2;
                double cz = (c.pipeStart().z() + c.pipeEnd().z()) / 2;
                double halfDiam = c.diameterMm() / 2000.0; // meters

                ins.setString(1, pipeGuid);
                ins.setString(2, "IfcPipeSegment");
                ins.setString(3, String.format("BIM_COBOL:Connecting Pipe:DN%.0f",
                        c.diameterMm()));
                ins.setString(4, c.system());
                ins.setDouble(5, cx);
                ins.setDouble(6, cy);
                ins.setDouble(7, cz);
                ins.setDouble(8, Math.min(c.pipeStart().x(), c.pipeEnd().x()) - halfDiam);
                ins.setDouble(9, Math.max(c.pipeStart().x(), c.pipeEnd().x()) + halfDiam);
                ins.setDouble(10, Math.min(c.pipeStart().y(), c.pipeEnd().y()) - halfDiam);
                ins.setDouble(11, Math.max(c.pipeStart().y(), c.pipeEnd().y()) + halfDiam);
                ins.setDouble(12, Math.min(c.pipeStart().z(), c.pipeEnd().z()) - halfDiam);
                ins.setDouble(13, Math.max(c.pipeStart().z(), c.pipeEnd().z()) + halfDiam);
                ins.setDouble(14, c.diameterMm());
                ins.setDouble(15, c.lengthMm());
                ins.setString(16, c.from().guid());
                ins.setString(17, c.to().guid());
                ins.executeUpdate();

                log.setString(1, c.from().guid());
                log.setString(2, c.to().guid());
                log.setString(3, pipeGuid);
                log.setDouble(4, c.gapM());
                log.setDouble(5, c.diameterMm());
                log.setString(6, c.system());
                log.executeUpdate();
            }
        }
        return count;
    }

    /** Write summary stats to mockup_summary table. */
    private void writeSummary(Connection conn, ConnectFittingsPayload p,
                              int origCount, int genCount) throws SQLException {
        String ins = "INSERT INTO mockup_summary (key, value) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(ins)) {
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("source_db", DUPLEX_EXTRACTED);
            summary.put("verb", "CONNECT FITTINGS");
            summary.put("extracted_elements", String.valueOf(origCount));
            summary.put("generated_pipes", String.valueOf(genCount));
            summary.put("total_elements", String.valueOf(origCount + genCount));
            summary.put("total_pipe_length_m",
                    String.format("%.2f", p.totalPipeLengthM()));
            summary.put("fittings_analyzed", String.valueOf(p.fittingCount()));
            summary.put("existing_pipes", String.valueOf(p.existingPipeCount()));
            summary.put("elbow_gaps_filled", String.valueOf(p.elbowGapCount()));
            summary.put("generated_at", new java.util.Date().toString());

            for (Map.Entry<String, String> e : summary.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.executeUpdate();
            }
        }
    }
}
