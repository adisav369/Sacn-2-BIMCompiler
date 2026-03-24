package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
import com.bim.ormsandbox.po.MBOM;
import com.bim.ormsandbox.po.MBOMLine;

import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * REPORT BOM CATALOG [FILE &lt;path.xlsx&gt;] — XLSX catalog of all active BOMs.
 *
 * <p>Produces a professional Excel report listing every active BOM with
 * child counts by component type and ERP dimension metadata.
 *
 * <p>Columns: bom_id, bom_name, bom_type, m_product_category_id, doc_sub_type,
 * entity_type, child_count, buy_count, make_count, phantom_count.
 *
 * <p>Standards compliance: ISO 19650 information containers, IFC4 classification,
 * iDempiere BOM explosion (Libero MRP).
 *
 * <p>Read-only — no DB writes.
 */
public class ReportBomCatalogVerb implements Verb<ReportBomCatalogVerb.CatalogPayload> {

    @Override
    public String keyword() { return "REPORT BOM CATALOG"; }

    @Override
    public VerbResult<CatalogPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        String filePath = null;
        for (int i = 0; i < args.length; i++) {
            if ("FILE".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                filePath = args[++i];
            }
        }

        Connection conn = ctx.bomConn();

        // Collect all active BOMs across all types
        List<CatalogRow> rows = new ArrayList<>();
        for (String type : List.of("BUILDING", "FLOOR", "ROOM", "SET", "ITEM")) {
            for (MBOM bom : MBOM.getByType(conn, type)) {
                List<MBOMLine> children = MBOMLine.getByBom(conn, bom.getBomId());
                int buyCount = 0, makeCount = 0, phantomCount = 0;
                for (MBOMLine line : children) {
                    switch (line.getComponentType()) {
                        case "BUY"     -> buyCount++;
                        case "MAKE"    -> makeCount++;
                        case "PHANTOM" -> phantomCount++;
                    }
                }
                rows.add(new CatalogRow(
                    bom.getBomId(), bom.getBomName(),
                    bom.getBomType(), bom.getBomCategory(),
                    bom.getDocSubType() != null ? bom.getDocSubType() : "",
                    bom.getEntityType() != null ? bom.getEntityType() : "D",
                    children.size(), buyCount, makeCount, phantomCount
                ));
            }
        }
        rows.sort((a, b) -> a.bomId().compareTo(b.bomId()));

        // Write XLSX if FILE requested
        String outputPath = null;
        if (filePath != null) {
            try {
                outputPath = writeXlsx(rows, filePath);
            } catch (IOException e) {
                return VerbResult.fail(keyword(),
                    "file write error: " + e.getMessage(), null);
            }
        }

        CatalogPayload payload = new CatalogPayload(
            rows.size(), outputPath, rows);

        return VerbResult.ok(keyword(),
            String.format("BOM CATALOG: %d BOMs%s",
                rows.size(),
                outputPath != null ? ", file=" + outputPath : ""),
            payload);
    }

    private String writeXlsx(List<CatalogRow> rows, String filePath) throws IOException {
        String[] headers = {
            "BOM ID", "BOM Name", "BOM Type", "BOM Category",
            "Doc Sub Type", "Entity Type",
            "Child Count", "BUY Count", "MAKE Count", "PHANTOM Count"
        };

        try (XSSFWorkbook wb = ReportTemplate.createWorkbook()) {
            XSSFSheet sheet = wb.createSheet("BOM Catalog");

            int headerRow = ReportTemplate.writeHeader(sheet,
                "BOM CATALOG REPORT",
                "Complete inventory of Bill of Materials definitions — "
                    + "all active BOMs with component breakdown",
                headers.length);

            ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

            CellStyle[] dataStyles = ReportTemplate.createDataStyles(wb);
            CellStyle intStyleEven = ReportTemplate.createIntStyle(wb, false);
            CellStyle intStyleOdd  = ReportTemplate.createIntStyle(wb, true);

            int rowIdx = headerRow + 1;
            for (int i = 0; i < rows.size(); i++) {
                CatalogRow r = rows.get(i);
                boolean odd = (i % 2 == 1);
                CellStyle ds = odd ? dataStyles[1] : dataStyles[0];
                CellStyle is = odd ? intStyleOdd : intStyleEven;

                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, r.bomId(), ds);
                setCellStr(row, 1, r.bomName(), ds);
                setCellStr(row, 2, r.bomType(), ds);
                setCellStr(row, 3, r.bomCategory(), ds);
                setCellStr(row, 4, r.docSubType(), ds);
                setCellStr(row, 5, r.entityType(), ds);
                setCellNum(row, 6, r.childCount(), is);
                setCellNum(row, 7, r.buyCount(), is);
                setCellNum(row, 8, r.makeCount(), is);
                setCellNum(row, 9, r.phantomCount(), is);
            }

            ReportTemplate.writeFooter(sheet, rowIdx + 1, rows.size(), headers.length);
            ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);

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

    public record CatalogRow(
        String bomId, String bomName, String bomType, String bomCategory,
        String docSubType, String entityType,
        int childCount, int buyCount, int makeCount, int phantomCount
    ) {}

    public record CatalogPayload(
        int count, String filePath, List<CatalogRow> rows
    ) {}
}
