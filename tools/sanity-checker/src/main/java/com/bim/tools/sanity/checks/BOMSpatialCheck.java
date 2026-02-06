package com.bim.tools.sanity.checks;

import com.bim.tools.sanity.model.SanityModel;
import com.bim.tools.sanity.report.CheckResult;

import java.sql.*;
import java.util.*;

/**
 * Check 28: BOM Spatial Overlap (Phase 84)
 *
 * BOM-metadata-driven check that detects spatial anomalies via SQL:
 * 1. MEP-in-Slab: sprinklers hidden inside slabs (Z overlap > 50mm)
 * 2. Sprinkler-Pipe: sprinklers hidden inside pipe segments
 * 3. Beam Protrusion: beams extending beyond building envelope
 *
 * Principle: BOM assemblies are the primary construct. Checks query assembly
 * metadata to detect anomalies — less custom geometry code, more SQL against
 * BOM relationships.
 */
public class BOMSpatialCheck implements SanityCheck {

    @Override
    public String getId() { return "bom_spatial"; }

    @Override
    public String getName() { return "BOM Spatial Overlap"; }

    @Override
    public CheckResult execute(SanityModel model, ADContext context) {
        List<String> issues = new ArrayList<>();
        List<String> details = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + model.getDbPath())) {
            // Check if rtree table exists
            if (!tableExists(conn, "elements_rtree") || !tableExists(conn, "elements_meta")) {
                return CheckResult.pass(getId(), getName())
                    .summary("Required tables not present (pre-BOM database)")
                    .build();
            }

            // 3a. MEP-in-Slab overlap
            int slabOverlaps = checkSprinklerSlabOverlap(conn, issues);

            // 3b. Sprinkler-Pipe overlap
            int pipeOverlaps = checkSprinklerPipeOverlap(conn, issues);

            // 3c. Beam envelope protrusion
            int beamProtrusions = checkBeamProtrusion(conn, issues);

            // Build summary
            int totalIssues = slabOverlaps + pipeOverlaps + beamProtrusions;

            details.add(String.format("Sprinkler-slab overlaps: %d", slabOverlaps));
            details.add(String.format("Sprinkler-pipe overlaps: %d", pipeOverlaps));
            details.add(String.format("Beam protrusions: %d", beamProtrusions));

            if (totalIssues == 0) {
                return CheckResult.pass(getId(), getName())
                    .summary("No spatial overlap anomalies detected")
                    .details(details)
                    .data("slab_overlaps", slabOverlaps)
                    .data("pipe_overlaps", pipeOverlaps)
                    .data("beam_protrusions", beamProtrusions)
                    .build();
            }

            // Issues found — report as WARNING
            CheckResult.Builder result = CheckResult.warning(getId(), getName())
                .summary(String.format("%d spatial anomalies detected", totalIssues));

            for (String d : details) result.detail(d);
            result.detail("");
            int shown = 0;
            for (String issue : issues) {
                if (shown < 15) {
                    result.detail("  - " + issue);
                    shown++;
                }
            }
            if (issues.size() > 15) {
                result.detail(String.format("  ... and %d more", issues.size() - 15));
            }

            result.guidance("Sprinkler/slab overlaps suggest sprinkler Z offset needs adjustment. " +
                "Beam protrusions suggest perimeter beams extend beyond wall envelope.");
            result.data("slab_overlaps", slabOverlaps);
            result.data("pipe_overlaps", pipeOverlaps);
            result.data("beam_protrusions", beamProtrusions);

            return result.build();

        } catch (SQLException e) {
            return CheckResult.fail(getId(), getName())
                .summary("Database error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 3a. Detect sprinklers embedded in slabs (Z overlap > 50mm).
     */
    private int checkSprinklerSlabOverlap(Connection conn, List<String> issues) throws SQLException {
        String sql = """
            SELECT s.guid as sprinkler, sl.guid as slab,
                   (slr.maxZ - sr.minZ) as embed_depth
            FROM elements_meta s
            JOIN elements_rtree sr ON s.id = sr.id
            JOIN elements_meta sl ON sl.ifc_class = 'IfcSlab'
            JOIN elements_rtree slr ON sl.id = slr.id
            WHERE s.ifc_class = 'IfcFireSuppressionTerminal'
              AND sr.minZ < slr.maxZ AND sr.maxZ > slr.minZ
              AND sr.minX < slr.maxX AND sr.maxX > slr.minX
              AND sr.minY < slr.maxY AND sr.maxY > slr.minY
              AND (slr.maxZ - sr.minZ) > 0.05
            """;

        int count = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                count++;
                if (count <= 5) {
                    issues.add(String.format("Sprinkler %s overlaps slab %s (%.0fmm embedded)",
                        rs.getString("sprinkler"), rs.getString("slab"),
                        rs.getDouble("embed_depth") * 1000));
                }
            }
        }
        if (count > 5) {
            issues.add(String.format("... %d more sprinkler-slab overlaps", count - 5));
        }
        return count;
    }

    /**
     * 3b. Detect sprinklers hidden inside pipe segments (3D bbox overlap).
     */
    private int checkSprinklerPipeOverlap(Connection conn, List<String> issues) throws SQLException {
        String sql = """
            SELECT sp.guid as sprinkler, p.guid as pipe
            FROM elements_meta sp
            JOIN elements_rtree spr ON sp.id = spr.id
            JOIN elements_meta p ON p.ifc_class = 'IfcPipeSegment'
            JOIN elements_rtree pr ON p.id = pr.id
            WHERE sp.ifc_class = 'IfcFireSuppressionTerminal'
              AND sp.storey = p.storey
              AND spr.minX < pr.maxX AND spr.maxX > pr.minX
              AND spr.minY < pr.maxY AND spr.maxY > pr.minY
              AND spr.minZ < pr.maxZ AND spr.maxZ > pr.minZ
            """;

        int count = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                count++;
                if (count <= 5) {
                    issues.add(String.format("Sprinkler %s overlaps pipe %s",
                        rs.getString("sprinkler"), rs.getString("pipe")));
                }
            }
        }
        if (count > 5) {
            issues.add(String.format("... %d more sprinkler-pipe overlaps", count - 5));
        }
        return count;
    }

    /**
     * 3c. Detect beams protruding beyond building envelope (wall footprint + 100mm tolerance).
     */
    private int checkBeamProtrusion(Connection conn, List<String> issues) throws SQLException {
        // Check if storey column exists in elements_meta
        boolean hasStorey = columnExists(conn, "elements_meta", "storey");

        String sql;
        if (hasStorey) {
            sql = """
                WITH envelope AS (
                    SELECT m.storey,
                           MIN(r.minX) as eMinX, MAX(r.maxX) as eMaxX,
                           MIN(r.minY) as eMinY, MAX(r.maxY) as eMaxY
                    FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN ('IfcWall')
                       OR (m.ifc_class = 'IfcPlate' AND m.element_type = 'CLADDING')
                    GROUP BY m.storey
                )
                SELECT b.guid, b.storey FROM elements_meta b
                JOIN elements_rtree br ON b.id = br.id
                LEFT JOIN envelope e ON b.storey = e.storey
                WHERE b.ifc_class IN ('IfcBeam', 'IfcMember') AND b.guid LIKE 'BEAM%'
                  AND e.storey IS NOT NULL
                  AND (br.minX < e.eMinX - 0.1 OR br.maxX > e.eMaxX + 0.1
                    OR br.minY < e.eMinY - 0.1 OR br.maxY > e.eMaxY + 0.1)
                """;
        } else {
            // No storey column — use global envelope
            sql = """
                WITH envelope AS (
                    SELECT MIN(r.minX) as eMinX, MAX(r.maxX) as eMaxX,
                           MIN(r.minY) as eMinY, MAX(r.maxY) as eMaxY
                    FROM elements_meta m JOIN elements_rtree r ON m.id = r.id
                    WHERE m.ifc_class IN ('IfcWall')
                       OR (m.ifc_class = 'IfcPlate' AND m.element_type = 'CLADDING')
                )
                SELECT b.guid FROM elements_meta b
                JOIN elements_rtree br ON b.id = br.id
                CROSS JOIN envelope e
                WHERE b.ifc_class IN ('IfcBeam', 'IfcMember') AND b.guid LIKE 'BEAM%'
                  AND (br.minX < e.eMinX - 0.1 OR br.maxX > e.eMaxX + 0.1
                    OR br.minY < e.eMinY - 0.1 OR br.maxY > e.eMaxY + 0.1)
                """;
        }

        int count = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                count++;
                if (count <= 5) {
                    String guid = rs.getString("guid");
                    String storey = hasStorey ? rs.getString("storey") : "N/A";
                    issues.add(String.format("Beam %s protrudes beyond envelope (storey: %s)",
                        guid, storey));
                }
            }
        }
        if (count > 5) {
            issues.add(String.format("... %d more beam protrusions", count - 5));
        }
        return count;
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equals(rs.getString("name"))) return true;
            }
        }
        return false;
    }
}
