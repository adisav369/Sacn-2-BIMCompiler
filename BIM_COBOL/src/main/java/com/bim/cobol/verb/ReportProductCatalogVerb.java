package com.bim.cobol.verb;

import com.bim.cobol.Verb;
import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbResult;
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
 * REPORT PRODUCT CATALOG [FILE &lt;path.xlsx&gt;] — XLSX catalog of all M_Products.
 *
 * <p>Produces a professional Excel report listing every active product with
 * intrinsic geometry dimensions (width/depth/height in metres, volume in m³).
 *
 * <p>Columns: product_id, product_name (extracted_from), ifc_class, product_type,
 * width_m, depth_m, height_m, volume_m3.
 *
 * <p>TRAP: M_Product dimensions are in METRES, not mm.
 *
 * <p>Read-only — no DB writes.
 */
public class ReportProductCatalogVerb implements Verb<ReportProductCatalogVerb.ProductCatalogPayload> {

    @Override
    public String keyword() { return "REPORT PRODUCT CATALOG"; }

    @Override
    public VerbResult<ProductCatalogPayload> execute(VerbContext ctx, String... args)
            throws SQLException {
        String filePath = null;
        for (int i = 0; i < args.length; i++) {
            if ("FILE".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                filePath = args[++i];
            }
        }

        Connection conn = ctx.bomConn();
        List<MProduct> products = MProduct.getAll(conn);

        List<ProductRow> rows = new ArrayList<>();
        for (MProduct p : products) {
            rows.add(new ProductRow(
                p.getProductId(),
                p.getExtractedFrom(),
                p.getIfcClass() != null ? p.getIfcClass() : "",
                p.getProductType(),
                p.getWidth(), p.getDepth(), p.getHeight(),
                p.volumeM3()
            ));
        }

        String outputPath = null;
        if (filePath != null) {
            try {
                outputPath = writeXlsx(rows, filePath);
            } catch (IOException e) {
                return VerbResult.fail(keyword(),
                    "file write error: " + e.getMessage(), null);
            }
        }

        ProductCatalogPayload payload = new ProductCatalogPayload(
            rows.size(), outputPath, rows);

        return VerbResult.ok(keyword(),
            String.format("PRODUCT CATALOG: %d products%s",
                rows.size(),
                outputPath != null ? ", file=" + outputPath : ""),
            payload);
    }

    private String writeXlsx(List<ProductRow> rows, String filePath) throws IOException {
        String[] headers = {
            "Product ID", "Product Name", "IFC Class", "Product Type",
            "Width (m)", "Depth (m)", "Height (m)", "Volume (m\u00B3)"
        };

        try (XSSFWorkbook wb = ReportTemplate.createWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Product Catalog");

            int headerRow = ReportTemplate.writeHeader(sheet,
                "PRODUCT CATALOG REPORT",
                "Complete inventory of M_Product definitions — "
                    + "IFC-classified placeable components with intrinsic geometry",
                headers.length);

            ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

            CellStyle[] dataStyles = ReportTemplate.createDataStyles(wb);
            CellStyle numStyleEven = ReportTemplate.createNumericStyle(wb, false);
            CellStyle numStyleOdd  = ReportTemplate.createNumericStyle(wb, true);

            int rowIdx = headerRow + 1;
            for (int i = 0; i < rows.size(); i++) {
                ProductRow r = rows.get(i);
                boolean odd = (i % 2 == 1);
                CellStyle ds = odd ? dataStyles[1] : dataStyles[0];
                CellStyle ns = odd ? numStyleOdd : numStyleEven;

                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, r.productId(), ds);
                setCellStr(row, 1, r.productName(), ds);
                setCellStr(row, 2, r.ifcClass(), ds);
                setCellStr(row, 3, r.productType(), ds);
                setCellNum(row, 4, r.widthM(), ns);
                setCellNum(row, 5, r.depthM(), ns);
                setCellNum(row, 6, r.heightM(), ns);
                setCellNum(row, 7, r.volumeM3(), ns);
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

    private static void setCellNum(Row row, int col, double val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        cell.setCellStyle(style);
    }

    public record ProductRow(
        String productId, String productName, String ifcClass, String productType,
        double widthM, double depthM, double heightM, double volumeM3
    ) {}

    public record ProductCatalogPayload(
        int count, String filePath, List<ProductRow> rows
    ) {}
}
