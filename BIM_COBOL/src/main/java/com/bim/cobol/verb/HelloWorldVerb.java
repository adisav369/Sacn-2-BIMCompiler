package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.compiler.validation.SpatialDigest;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * HELLO WORLD [SH|DX] — permanent HelloWorld proof.
 *
 * <p>"Can this plane even take off?" The most basic regression test.
 * Proves that compilation output matches the reference extracted DB.
 *
 * <h3>The Singularity Rule</h3>
 * <pre>
 *   IF   C_DocType.DocSubType = M_BOM.doc_sub_type   -- same building type
 *   AND  C_DocType.AABB       = BOM envelope AABB     -- same envelope
 *   THEN result count = 1  →  SINGULARITY
 * </pre>
 *
 * <h3>Steps</h3>
 * <ol>
 *   <li>Singularity check (BOM.db query)</li>
 *   <li>Inventory compilation output</li>
 *   <li>Inventory reference extracted DB</li>
 *   <li>Comparison (count, volume, digest)</li>
 * </ol>
 *
 * <p>Grammar: {@code HELLO WORLD}, {@code HELLO WORLD SH}, {@code HELLO WORLD DX}
 */
public class HelloWorldVerb implements Verb<HelloWorldVerb.HelloWorldPayload> {

    private static final double VOLUME_DRIFT_THRESHOLD = 0.001; // 0.1%

    @Override
    public String keyword() { return "HELLO WORLD"; }

    @Override
    public VerbResult<HelloWorldPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        Connection bomConn = ctx.bomConn();

        List<String> targets = new ArrayList<>();
        if (args.length == 0) {
            targets.add("SH");
            targets.add("DX");
        } else {
            targets.add(args[0].toUpperCase());
        }

        List<BuildingResult> results = new ArrayList<>();
        StringBuilder output = new StringBuilder();
        boolean allPass = true;

        for (String docSubType : targets) {
            BuildingResult br = proveBuilding(bomConn, docSubType);
            results.add(br);
            if (!br.pass) allPass = false;
            output.append(formatResult(docSubType, br));
        }

        HelloWorldPayload payload = new HelloWorldPayload(results, allPass);

        if (allPass)
            return VerbResult.ok(keyword(), output.toString(), payload);
        else
            return VerbResult.fail(keyword(), output.toString(), payload);
    }

    // ── Step 1: Singularity Check ──────────────────────────────────

    private SingularityCheck checkSingularity(Connection bomConn, String docSubType)
            throws SQLException {
        // Implementing DISC_VALIDATION_DB_SRS.md §10.4.5 — Witness: W-TREE-1
        // Find tree-root BOMs matching doc_sub_type (root = not a child of any other BOM)
        String buildingBomId = null;
        int buildingCount = 0;
        try (PreparedStatement ps = bomConn.prepareStatement(
                "SELECT Value FROM m_bom "
                + "WHERE bom_id NOT IN (SELECT child_product_id FROM m_bom_line WHERE is_active = 1) "
                + "AND doc_sub_type = ?")) {
            ps.setString(1, docSubType);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    buildingBomId = rs.getString(1);
                    buildingCount++;
                }
            }
        }

        // Read AABB from BUILDING BOM header (top-down, set from extraction)
        double bomWidth = 0, bomDepth = 0, bomHeight = 0;
        if (buildingBomId != null) {
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT aabb_width_mm, aabb_depth_mm, aabb_height_mm "
                    + "FROM m_bom WHERE Value = ?")) {
                ps.setString(1, buildingBomId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        bomWidth  = rs.getDouble(1);
                        bomDepth  = rs.getDouble(2);
                        bomHeight = rs.getDouble(3);
                    }
                }
            }
        }

        boolean docSubTypeMatch = buildingCount > 0;
        // Singularity: exactly 1 BUILDING BOM for this DocSubType
        boolean singularity = docSubTypeMatch && buildingCount == 1;

        return new SingularityCheck(
                docSubType, buildingBomId,
                bomWidth, bomDepth, bomHeight,
                docSubTypeMatch, buildingCount, singularity);
    }

    // ── Steps 2-3: Inventory ────────────────────────────────────────

    private DbInventory inventoryDb(String dbPath, String label) {
        File f = new File(dbPath);
        if (!f.exists())
            return new DbInventory(dbPath, label, false, 0, 0,
                    0, 0, 0, 0, 0, null, 0.0);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            int elements = safeCount(conn, "SELECT COUNT(*) FROM elements_meta");
            int cOrderLine = safeCount(conn, "SELECT COUNT(*) FROM c_orderline");
            int esL0 = 0; // CO_EmptySpace removed — WHERE = M_BOM_Line dx/dy/dz
            int esL1 = 0;
            int esL2 = 0;
            int spatial = safeCount(conn, "SELECT COUNT(*) FROM spatial_structure");
            int geom = safeCount(conn, "SELECT COUNT(*) FROM base_geometries");

            String digest = null;
            if (elements > 0) {
                SpatialDigest.DigestReport report =
                        SpatialDigest.computeWithReport(dbPath, false);
                digest = report.digest();
            }

            double volume = computeVolume(conn);

            return new DbInventory(dbPath, label, true, elements,
                    cOrderLine, esL0, esL1, esL2, spatial, geom,
                    digest, volume);
        } catch (SQLException e) {
            return new DbInventory(dbPath, label, false, 0, 0,
                    0, 0, 0, 0, 0, null, 0.0);
        }
    }

    private double computeVolume(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT (MAX(maxX)-MIN(minX)) * (MAX(maxY)-MIN(minY)) "
                     + "* (MAX(maxZ)-MIN(minZ)) FROM elements_rtree")) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        } catch (SQLException e) {
            return 0.0;
        }
    }

    private int safeCount(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return -1;
        }
    }

    // ── Step 4: Compare ─────────────────────────────────────────────

    private Comparison compare(String label, DbInventory a, DbInventory b) {
        if (a == null || b == null || !a.exists || !b.exists)
            return new Comparison(label, 0, 0.0, false, false,
                    "missing DB");

        int countDelta = a.elements - b.elements;

        double volumeDrift = 0.0;
        if (b.volume > 0)
            volumeDrift = Math.abs(a.volume - b.volume) / b.volume;

        boolean digestMatch = a.digest != null && b.digest != null
                && a.digest.equals(b.digest);

        boolean pass = countDelta == 0
                && volumeDrift <= VOLUME_DRIFT_THRESHOLD
                && digestMatch;

        return new Comparison(label, countDelta, volumeDrift,
                digestMatch, pass, null);
    }

    // ── Orchestrate ─────────────────────────────────────────────────

    private BuildingResult proveBuilding(Connection bomConn, String docSubType) {
        try {
            // Step 1
            SingularityCheck singularity = checkSingularity(bomConn, docSubType);

            // Get DB paths from C_DocType
            String outputPath = null, refPath = null;
            try (PreparedStatement ps = bomConn.prepareStatement(
                    "SELECT OutputDbPath, ReferenceDbPath "
                    + "FROM C_DocType WHERE doc_sub_type = ?")) {
                ps.setString(1, docSubType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        outputPath = rs.getString(1);
                        refPath = rs.getString(2);
                    }
                }
            }

            if (outputPath == null || refPath == null)
                return new BuildingResult(docSubType, singularity,
                        null, null, null, false);

            // Steps 2-3: inventory output and reference
            DbInventory compiled = inventoryDb(outputPath,
                    "OUT_" + docSubType);
            DbInventory reference = inventoryDb(refPath,
                    "REF_" + docSubType);

            // Step 4: compare output vs reference
            Comparison outVsRef = compare("output vs ref", compiled, reference);

            return new BuildingResult(docSubType, singularity,
                    compiled, reference, outVsRef, outVsRef.pass);
        } catch (Exception e) {
            return new BuildingResult(docSubType, null,
                    null, null, null, false);
        }
    }

    // ── Formatting ──────────────────────────────────────────────────

    private String formatResult(String docSubType, BuildingResult br) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%nHELLO WORLD %s%n", docSubType));
        sb.append("══════════════════════════════════════════════════════════════\n");

        // Step 1
        SingularityCheck s = br.singularity;
        if (s != null) {
            sb.append("  Step 1 — Singularity check:\n");
            sb.append(String.format("    DocSubType = %s  BUILDING BOM = %s  %s%n",
                    s.docSubType, s.buildingBomId != null ? s.buildingBomId : "none",
                    s.docSubTypeMatch ? "MATCH" : "NO MATCH"));
            sb.append(String.format("    BOM AABB   = %.0f x %.0f x %.0f mm%n",
                    s.bomWidthMm, s.bomDepthMm, s.bomHeightMm));
            sb.append(String.format("    BUILDING count = %d → %s%n%n",
                    s.buildingCount, s.singularity ? "SINGULARITY" : "EXPLODE"));
        }

        // Steps 2-3
        formatInventory(sb, "Step 2 — Compiled output:", br.compiled);
        formatInventory(sb, "Step 3 — Reference:", br.reference);

        // Step 4
        sb.append("  Step 4 — Comparison:\n");
        formatComparison(sb, br.outVsRef);
        sb.append(String.format("%n  HELLO WORLD %s: %s%n",
                docSubType, br.pass ? "PASS" : "FAIL"));
        sb.append("══════════════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private void formatInventory(StringBuilder sb, String header, DbInventory inv) {
        sb.append(String.format("  %s%n", header));
        if (inv == null || !inv.exists) {
            sb.append(String.format("    %s — NOT FOUND%n%n",
                    inv != null ? inv.dbPath : "unknown"));
            return;
        }
        sb.append(String.format("    %s%n", inv.dbPath));
        sb.append(String.format("    BOM=%s  elements=%d  c_orderline=%d%n",
                inv.label, inv.elements, inv.cOrderLine));
        sb.append(String.format("    spatial_structure=%d  base_geometries=%d%n",
                inv.spatialStructure, inv.baseGeometries));
        sb.append(String.format("    digest=%s%n%n",
                inv.digest != null ? inv.digest.substring(0, Math.min(8, inv.digest.length()))
                        + "..." : "N/A"));
    }

    private void formatComparison(StringBuilder sb, Comparison c) {
        if (c == null) {
            sb.append("    (no comparison)\n");
            return;
        }
        if (c.error != null) {
            sb.append(String.format("    %-20s %s  FAIL%n", c.label + ":", c.error));
            return;
        }
        sb.append(String.format("    %-20s count=%dΔ  volume=%.2f%%Δ  digest=%s  %s%n",
                c.label + ":",
                c.countDelta,
                c.volumeDrift * 100,
                c.digestMatch ? "MATCH" : "MISMATCH",
                c.pass ? "PASS" : "FAIL"));
    }

    // ── Records ─────────────────────────────────────────────────────

    public record SingularityCheck(
            String docSubType,
            String buildingBomId,
            double bomWidthMm, double bomDepthMm, double bomHeightMm,
            boolean docSubTypeMatch,
            int buildingCount,
            boolean singularity
    ) {}

    public record DbInventory(
            String dbPath,
            String label,
            boolean exists,
            int elements,
            int cOrderLine,
            int esL0, int esL1, int esL2,
            int spatialStructure,
            int baseGeometries,
            String digest,
            double volume
    ) {}

    public record Comparison(
            String label,
            int countDelta,
            double volumeDrift,
            boolean digestMatch,
            boolean pass,
            String error
    ) {}

    public record BuildingResult(
            String docSubType,
            SingularityCheck singularity,
            DbInventory compiled,
            DbInventory reference,
            Comparison outVsRef,
            boolean pass
    ) {}

    public record HelloWorldPayload(
            List<BuildingResult> results,
            boolean allPass
    ) {}
}
