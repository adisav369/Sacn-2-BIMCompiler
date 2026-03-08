package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * EXPORT BOM &lt;bom_id&gt; [AS CSV|JSON] [FILE &lt;path&gt;] — export BOM tree to file.
 *
 * <p>Recursively walks the BOM tree and exports all children to CSV or JSON.
 * If FILE is omitted, exports to stdout (summary in payload).
 *
 * <p>CSV columns: bom_id, child_product_id, component_type, role, sequence,
 * dx, dy, dz, rotation_rule, allocated_width_mm, allocated_depth_mm,
 * allocated_height_mm, locator_ref, bom_level.
 *
 * <p>Examples:
 * <pre>
 * EXPORT BOM EB_SH
 * EXPORT BOM EB_DX AS CSV FILE /tmp/duplex_bom.csv
 * EXPORT BOM EB_SH AS JSON
 * </pre>
 *
 * <p>Read-only — writes to filesystem, not to DB.
 */
public class ExportBomVerb implements Verb<ExportBomVerb.ExportBomPayload> {

    @Override
    public String keyword() { return "EXPORT BOM"; }

    @Override
    public VerbResult<ExportBomPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: EXPORT BOM <bom_id> [AS CSV|JSON] [FILE <path>]", null);

        String bomId = args[0];
        String format = "CSV"; // default
        String filePath = null;

        // Parse optional AS and FILE
        for (int i = 1; i < args.length; i++) {
            if ("AS".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                format = args[++i].toUpperCase();
            } else if ("FILE".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                filePath = args[++i];
            }
        }

        Connection conn = ctx.bomConn();
        MBOM root = MBOM.get(conn, bomId);
        if (root == null)
            return VerbResult.fail(keyword(), bomId + " not found", null);

        // Walk tree and collect rows
        List<ExportRow> rows = new ArrayList<>();
        walkExport(conn, bomId, 0, rows);

        // Count unique products
        long uniqueProducts = rows.stream()
            .map(ExportRow::childProductId)
            .distinct().count();

        // Write to file if requested
        String outputPath = null;
        if (filePath != null) {
            try {
                outputPath = writeFile(rows, format, filePath, bomId);
            } catch (IOException e) {
                return VerbResult.fail(keyword(),
                    "file write error: " + e.getMessage(), null);
            }
        }

        ExportBomPayload payload = new ExportBomPayload(
            bomId, format, rows.size(), (int) uniqueProducts,
            outputPath, rows);

        return VerbResult.ok(keyword(),
            String.format("EXPORT %s: %d rows (%d unique products), format=%s%s",
                bomId, rows.size(), uniqueProducts, format,
                outputPath != null ? ", file=" + outputPath : ""),
            payload);
    }

    private void walkExport(Connection conn, String bomId, int level,
                             List<ExportRow> rows) throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : children) {
            rows.add(new ExportRow(
                bomId, line.getChildProductId(), line.getComponentType(),
                line.getRole(), line.getSequence(),
                line.getDx(), line.getDy(), line.getDz(),
                line.getRotationRule(),
                line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                line.getAllocatedHeightMm(),
                line.getLocatorRef(), level
            ));

            // Recurse into MAKE children
            if ("MAKE".equals(line.getComponentType())
                    && line.getChildProductId() != null) {
                MBOM childBom = MBOM.get(conn, line.getChildProductId());
                if (childBom != null) {
                    walkExport(conn, line.getChildProductId(), level + 1, rows);
                }
            }
        }
    }

    private String writeFile(List<ExportRow> rows, String format,
                              String filePath, String bomId) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            if ("JSON".equals(format)) {
                writeJson(pw, rows, bomId);
            } else {
                writeCsv(pw, rows);
            }
        }
        return filePath;
    }

    private void writeCsv(PrintWriter pw, List<ExportRow> rows) {
        pw.println("bom_id,child_product_id,component_type,role,sequence,"
            + "dx,dy,dz,rotation_rule,allocated_width_mm,allocated_depth_mm,"
            + "allocated_height_mm,locator_ref,bom_level");
        for (ExportRow r : rows) {
            pw.printf("%s,%s,%s,%s,%d,%.4f,%.4f,%.4f,%s,%d,%d,%d,%s,%d%n",
                r.bomId(), r.childProductId(), r.componentType(),
                r.role() != null ? r.role() : "",
                r.sequence(),
                r.dx(), r.dy(), r.dz(),
                r.rotationRule() != null ? r.rotationRule() : "",
                r.allocatedWidthMm(), r.allocatedDepthMm(), r.allocatedHeightMm(),
                r.locatorRef() != null ? r.locatorRef() : "",
                r.bomLevel());
        }
    }

    private void writeJson(PrintWriter pw, List<ExportRow> rows, String bomId) {
        pw.println("{");
        pw.printf("  \"bomId\": \"%s\",%n", bomId);
        pw.printf("  \"rowCount\": %d,%n", rows.size());
        pw.println("  \"rows\": [");
        for (int i = 0; i < rows.size(); i++) {
            ExportRow r = rows.get(i);
            pw.printf("    {\"bomId\":\"%s\",\"childProductId\":\"%s\","
                + "\"componentType\":\"%s\",\"role\":\"%s\","
                + "\"sequence\":%d,\"dx\":%.4f,\"dy\":%.4f,\"dz\":%.4f,"
                + "\"rotationRule\":\"%s\","
                + "\"allocatedWidthMm\":%d,\"allocatedDepthMm\":%d,"
                + "\"allocatedHeightMm\":%d,"
                + "\"locatorRef\":\"%s\",\"bomLevel\":%d}%s%n",
                r.bomId(), r.childProductId(), r.componentType(),
                r.role() != null ? r.role() : "",
                r.sequence(), r.dx(), r.dy(), r.dz(),
                r.rotationRule() != null ? r.rotationRule() : "",
                r.allocatedWidthMm(), r.allocatedDepthMm(), r.allocatedHeightMm(),
                r.locatorRef() != null ? r.locatorRef() : "",
                r.bomLevel(),
                i < rows.size() - 1 ? "," : "");
        }
        pw.println("  ]");
        pw.println("}");
    }

    public record ExportRow(
        String bomId, String childProductId, String componentType,
        String role, int sequence,
        double dx, double dy, double dz, String rotationRule,
        int allocatedWidthMm, int allocatedDepthMm, int allocatedHeightMm,
        String locatorRef, int bomLevel
    ) {}

    public record ExportBomPayload(
        String bomId, String format, int rowCount, int uniqueProducts,
        String filePath, List<ExportRow> rows
    ) {}
}
