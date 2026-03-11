package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;
import com.bim.ormsandbox.po.MProduct;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * REPORT BOM STRUCTURE &lt;bom_id&gt; [bom_id2 ...] [FILE &lt;path.xlsx&gt;]
 *
 * <p>Recursively walks BOM tree(s) and resolves reference component details
 * from M_Product for BUY leaves. Multiple bom_ids produce separate sheets
 * in the same workbook (e.g. SH as one sheet, DX as another).
 *
 * <p>Columns: level, bom_id, child_product_id, component_type, role, sequence,
 * dx, dy, dz, allocated_w_mm, allocated_d_mm, allocated_h_mm,
 * product_name, ifc_class, entity_type.
 *
 * <p>Read-only — no DB writes.
 */
public class ReportBomStructureVerb implements Verb<ReportBomStructureVerb.StructurePayload> {

    @Override
    public String keyword() { return "REPORT BOM STRUCTURE"; }

    @Override
    public VerbResult<StructurePayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        if (args.length == 0)
            return VerbResult.fail(keyword(),
                "usage: REPORT BOM STRUCTURE <bom_id> [bom_id2 ...] [FILE <path.xlsx>]", null);

        // Parse bom_ids and FILE path
        List<String> bomIds = new ArrayList<>();
        String filePath = null;
        for (int i = 0; i < args.length; i++) {
            if ("FILE".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                filePath = args[++i];
            } else {
                bomIds.add(args[i]);
            }
        }

        Connection conn = ctx.bomConn();

        // Walk all requested BOMs
        List<StructureRow> allRows = new ArrayList<>();
        List<BomSheet> sheets = new ArrayList<>();
        for (String bomId : bomIds) {
            MBOM root = MBOM.get(conn, bomId);
            if (root == null)
                return VerbResult.fail(keyword(), bomId + " not found", null);

            List<StructureRow> rows = new ArrayList<>();
            walkStructure(conn, bomId, 0, rows);
            allRows.addAll(rows);
            sheets.add(new BomSheet(bomId, root.getBomName(), rows));
        }

        String outputPath = null;
        if (filePath != null) {
            try {
                outputPath = writeXlsx(sheets, filePath);
            } catch (IOException e) {
                return VerbResult.fail(keyword(),
                    "file write error: " + e.getMessage(), null);
            }
        }

        long uniqueProducts = allRows.stream()
            .map(StructureRow::childProductId)
            .filter(id -> id != null && !id.isEmpty())
            .distinct().count();

        String primaryBom = bomIds.get(0);
        StructurePayload payload = new StructurePayload(
            primaryBom, allRows.size(), (int) uniqueProducts, outputPath, allRows);

        String bomDesc = bomIds.size() == 1 ? primaryBom
            : String.join("+", bomIds);

        return VerbResult.ok(keyword(),
            String.format("BOM STRUCTURE %s: %d rows (%d unique products)%s",
                bomDesc, allRows.size(), uniqueProducts,
                outputPath != null ? ", file=" + outputPath : ""),
            payload);
    }

    private void walkStructure(Connection conn, String bomId, int level,
                                List<StructureRow> rows) throws SQLException {
        List<MBOMLine> children = MBOMLine.getByBom(conn, bomId);
        for (MBOMLine line : children) {
            // Resolve M_Product for leaf details
            String productName = "";
            String ifcClass = "";
            if (line.getChildProductId() != null) {
                MProduct product = MProduct.get(conn, line.getChildProductId());
                if (product != null) {
                    productName = product.getExtractedFrom() != null
                        ? product.getExtractedFrom() : "";
                    ifcClass = product.getIfcClass() != null
                        ? product.getIfcClass() : "";
                }
            }

            rows.add(new StructureRow(
                level, bomId,
                line.getChildProductId() != null ? line.getChildProductId() : "",
                line.getComponentType(),
                line.getRole() != null ? line.getRole() : "",
                line.getSequence(),
                line.getDx(), line.getDy(), line.getDz(),
                line.getAllocatedWidthMm(), line.getAllocatedDepthMm(),
                line.getAllocatedHeightMm(),
                productName, ifcClass,
                line.getEntityType() != null ? line.getEntityType() : "D"
            ));

            // Recurse into sub-BOMs (tree structure, not component_type).
            // Future: MAKE = LOD created on-the-fly via Mesh2Library.txt.
            if (line.getChildProductId() != null) {
                MBOM childBom = MBOM.get(conn, line.getChildProductId());
                if (childBom != null) {
                    walkStructure(conn, line.getChildProductId(), level + 1, rows);
                }
            }
        }
    }

    private String writeXlsx(List<BomSheet> bomSheets, String filePath) throws IOException {
        String[] headers = {
            "Level", "BOM ID", "Child Product ID", "Component Type",
            "Role", "Sequence", "dx (m)", "dy (m)", "dz (m)",
            "Alloc W (mm)", "Alloc D (mm)", "Alloc H (mm)",
            "Product Name", "IFC Class", "Entity Type"
        };

        try (XSSFWorkbook wb = ReportTemplate.createWorkbook()) {
            for (BomSheet bs : bomSheets) {
                // Sheet name: bom_id (truncated to 31 chars — Excel limit)
                String sheetName = bs.bomId().length() > 31
                    ? bs.bomId().substring(0, 31) : bs.bomId();
                XSSFSheet sheet = wb.createSheet(sheetName);

                int headerRow = ReportTemplate.writeHeader(sheet,
                    "BOM STRUCTURE — " + bs.bomId(),
                    "Full BOM explosion for " + bs.bomId()
                        + " (" + bs.bomName() + ") — "
                        + "recursive tree walk with resolved M_Product references",
                    headers.length);

                ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

                CellStyle[] dataStyles = ReportTemplate.createDataStyles(wb);
                CellStyle numStyleEven = ReportTemplate.createNumericStyle(wb, false);
                CellStyle numStyleOdd  = ReportTemplate.createNumericStyle(wb, true);
                CellStyle intStyleEven = ReportTemplate.createIntStyle(wb, false);
                CellStyle intStyleOdd  = ReportTemplate.createIntStyle(wb, true);

                int rowIdx = headerRow + 1;
                for (int i = 0; i < bs.rows().size(); i++) {
                    StructureRow r = bs.rows().get(i);
                    boolean odd = (i % 2 == 1);
                    CellStyle ds = odd ? dataStyles[1] : dataStyles[0];
                    CellStyle ns = odd ? numStyleOdd : numStyleEven;
                    CellStyle is = odd ? intStyleOdd : intStyleEven;

                    // Indented BOM ID style
                    int indent = Math.min(r.level(), 5);
                    CellStyle indentStyle = wb.createCellStyle();
                    indentStyle.cloneStyleFrom(ds);
                    indentStyle.setIndention((short) (indent * 2));

                    Row row = sheet.createRow(rowIdx++);
                    setCellNum(row, 0, r.level(), is);
                    Cell bomCell = row.createCell(1);
                    bomCell.setCellValue(r.bomId());
                    bomCell.setCellStyle(indentStyle);
                    setCellStr(row, 2, r.childProductId(), ds);
                    setCellStr(row, 3, r.componentType(), ds);
                    setCellStr(row, 4, r.role(), ds);
                    setCellNum(row, 5, r.sequence(), is);
                    setCellDbl(row, 6, r.dx(), ns);
                    setCellDbl(row, 7, r.dy(), ns);
                    setCellDbl(row, 8, r.dz(), ns);
                    setCellNum(row, 9, r.allocatedWMm(), is);
                    setCellNum(row, 10, r.allocatedDMm(), is);
                    setCellNum(row, 11, r.allocatedHMm(), is);
                    setCellStr(row, 12, r.productName(), ds);
                    setCellStr(row, 13, r.ifcClass(), ds);
                    setCellStr(row, 14, r.entityType(), ds);
                }

                ReportTemplate.writeFooter(sheet, rowIdx + 1, bs.rows().size(),
                    headers.length);
                ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                wb.write(fos);
            }
        }
        return filePath;
    }

    private static void setCellStr(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val != null ? val : "");
        cell.setCellStyle(style);
    }

    private static void setCellNum(Row row, int col, int val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }

    private static void setCellDbl(Row row, int col, double val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }

    /** Internal: one sheet's data for multi-sheet workbook. */
    record BomSheet(String bomId, String bomName, List<StructureRow> rows) {}

    public record StructureRow(
        int level, String bomId, String childProductId, String componentType,
        String role, int sequence,
        double dx, double dy, double dz,
        int allocatedWMm, int allocatedDMm, int allocatedHMm,
        String productName, String ifcClass, String entityType
    ) {}

    public record StructurePayload(
        String bomId, int rowCount, int uniqueProducts,
        String filePath, List<StructureRow> rows
    ) {}
}
