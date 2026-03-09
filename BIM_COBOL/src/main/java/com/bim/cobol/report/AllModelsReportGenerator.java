package com.bim.cobol.report;

import com.bim.cobol.VerbContext;
import com.bim.cobol.VerbRegistry;
import com.bim.cobol.VerbResult;
import com.bim.cobol.verb.ReportBomCatalogVerb;
import com.bim.cobol.verb.ReportBomStructureVerb;
import com.bim.cobol.verb.ReportProductCatalogVerb;
import com.bim.cobol.verb.ReportTemplate;
import com.bim.ormsandbox.po.MCDocType;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * H0b: Composed AllModelsReport — single XLSX workbook with all BIM data.
 *
 * <p>Sheets:
 * <ol>
 *   <li><b>Summary</b> — all buildings from C_DocType, expected vs actual counts</li>
 *   <li><b>BOM Catalog</b> — all BOMs from BOM.db</li>
 *   <li><b>Product Catalog</b> — all M_Products from BOM.db</li>
 *   <li><b>BUILDING_SH_STD</b> — Sample House BOM structure</li>
 *   <li><b>BUILDING_DX_STD</b> — Duplex BOM structure</li>
 *   <li><b>TB Output Elements</b> — TB_LKTN compiled output (overproduction exposed)</li>
 *   <li><b>TB QTO</b> — TB_LKTN quantity takeoff</li>
 *   <li><b>TE Disciplines</b> — Terminal extracted: discipline breakdown + clash counts</li>
 *   <li><b>TE Clash Analysis</b> — Terminal extracted: clash pairs, top groups, severity</li>
 * </ol>
 *
 * <p>Usage: {@code mvn exec:java -pl BIM_COBOL -Dexec.mainClass=com.bim.cobol.report.AllModelsReportGenerator}
 *
 * <p>All data via DAO — zero raw SQL writes. Output DB read via JDBC (read-only).
 */
public class AllModelsReportGenerator {

    private static final String BOM_DB = "library/BOM.db";
    private static final String TB_OUTPUT_DB = "output/tb_lktn.db";
    private static final String TE_INPUT_DB = "DAGCompiler/lib/input/Terminal_Extracted.db";
    private static final String OUTPUT_PATH = "reporting/AllModelsReport.xlsx";

    public static void main(String[] args) throws Exception {
        String outputPath = args.length > 0 ? args[0] : OUTPUT_PATH;

        System.out.println("AllModelsReport: generating " + outputPath);

        try (Connection bomConn = DriverManager.getConnection("jdbc:sqlite:" + BOM_DB)) {
            Connection tbConn = null;
            try {
                tbConn = DriverManager.getConnection("jdbc:sqlite:" + TB_OUTPUT_DB);
            } catch (SQLException e) {
                System.out.println("  WARNING: " + TB_OUTPUT_DB + " not available: " + e.getMessage());
            }
            Connection teConn = null;
            try {
                teConn = DriverManager.getConnection("jdbc:sqlite:" + TE_INPUT_DB);
            } catch (SQLException e) {
                System.out.println("  WARNING: " + TE_INPUT_DB + " not available: " + e.getMessage());
            }

            try (XSSFWorkbook wb = ReportTemplate.createWorkbook()) {
                // Sheet 1: Summary
                writeSummarySheet(wb, bomConn, tbConn);

                // Sheet 2: BOM Catalog (via verb dispatch)
                writeBomCatalogSheet(wb, bomConn);

                // Sheet 3: Product Catalog (via verb dispatch)
                writeProductCatalogSheet(wb, bomConn);

                // Sheet 4-5: BOM Structure for SH and DX
                writeBomStructureSheet(wb, bomConn, "BUILDING_SH_STD");
                writeBomStructureSheet(wb, bomConn, "BUILDING_DX_STD");

                // Sheet 6-7: TB Output (if available)
                if (tbConn != null) {
                    writeTbElementsSheet(wb, tbConn);
                    writeTbQtoSheet(wb, tbConn);
                }

                // Sheet 8-9: Terminal Analysis (input/extracted, not output)
                if (teConn != null) {
                    writeTerminalDisciplineSheet(wb, teConn);
                    writeTerminalClashSheet(wb, teConn);
                }

                try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                    wb.write(fos);
                }

                System.out.println("AllModelsReport: DONE → " + outputPath);
                System.out.println("  Sheets: " + wb.getNumberOfSheets());
            } finally {
                if (tbConn != null) tbConn.close();
                if (teConn != null) teConn.close();
            }
        }
    }

    // ── Sheet 1: Summary ──────────────────────────────────────────────────

    private static void writeSummarySheet(XSSFWorkbook wb, Connection bomConn,
                                           Connection tbConn) throws SQLException {
        XSSFSheet sheet = wb.createSheet("Summary");
        String[] headers = {
            "Building", "DocType", "DocSubType", "Provenance",
            "Expected", "Actual", "Delta", "Status",
            "Design Theme", "BOM Count"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "ALL MODELS SUMMARY",
            "BIM Intent Compiler — complete model inventory with compilation status",
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        // Status styles
        CellStyle greenStyle = wb.createCellStyle();
        greenStyle.cloneStyleFrom(ds[0]);
        greenStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        greenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle redStyle = wb.createCellStyle();
        redStyle.cloneStyleFrom(ds[0]);
        redStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        redStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font redFont = wb.createFont();
        redFont.setBold(true);
        redFont.setColor(IndexedColors.DARK_RED.getIndex());
        redStyle.setFont(redFont);
        CellStyle orangeStyle = wb.createCellStyle();
        orangeStyle.cloneStyleFrom(ds[0]);
        orangeStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        orangeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        List<MCDocType> docTypes = MCDocType.getAll(bomConn);
        int rowIdx = headerRow + 1;
        for (int i = 0; i < docTypes.size(); i++) {
            MCDocType dt = docTypes.get(i);
            boolean odd = (i % 2 == 1);
            CellStyle d = odd ? ds[1] : ds[0];
            CellStyle is = odd ? intOdd : intEven;

            int expected = dt.getExpectedElements();
            int actual = getActualCount(dt, tbConn);
            int delta = actual - expected;
            String status;
            CellStyle statusStyle;
            if (actual == 0) {
                status = "NOT COMPILED";
                statusStyle = orangeStyle;
            } else if (delta == 0) {
                status = "EXACT MATCH";
                statusStyle = greenStyle;
            } else if (delta > 0) {
                status = "OVERPRODUCTION +" + delta;
                statusStyle = redStyle;
            } else {
                status = "UNDERPRODUCTION " + delta;
                statusStyle = redStyle;
            }

            // Count BOMs for this doc sub type
            int bomCount = countBomsForDocSubType(bomConn, dt.getDocSubType());

            Row row = sheet.createRow(rowIdx++);
            setCellStr(row, 0, dt.getName(), d);
            setCellStr(row, 1, dt.getDocTypeId(), d);
            setCellStr(row, 2, dt.getDocSubType() != null ? dt.getDocSubType() : "", d);
            setCellStr(row, 3, dt.getProvenance() != null ? dt.getProvenance() : "", d);
            setCellNum(row, 4, expected, is);
            setCellNum(row, 5, actual, is);
            setCellNum(row, 6, delta, is);
            setCellStr(row, 7, status, statusStyle);
            setCellStr(row, 8, dt.getCCampaignId() != null ? dt.getCCampaignId() : "", d);
            setCellNum(row, 9, bomCount, is);
        }

        ReportTemplate.writeFooter(sheet, rowIdx + 1, docTypes.size(), headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
    }

    private static int getActualCount(MCDocType dt, Connection tbConn) {
        String subType = dt.getDocSubType();
        if (subType == null) return 0;
        // TB is the only one with output data currently
        if ("TB".equals(subType) && tbConn != null) {
            try (Statement st = tbConn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) { return 0; }
        }
        // SH/DX/TE/ST: check for output DB files with data
        String outputPath = dt.getOutputDbPath();
        if (outputPath != null && !outputPath.isEmpty()) {
            java.io.File f = new java.io.File(outputPath);
            if (f.exists() && f.length() > 0) {
                try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + outputPath);
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                         "SELECT COUNT(*) FROM elements_meta")) {
                    return rs.next() ? rs.getInt(1) : 0;
                } catch (SQLException e) { return 0; }
            }
        }
        return 0;
    }

    private static int countBomsForDocSubType(Connection conn, String docSubType) {
        if (docSubType == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM m_bom WHERE doc_sub_type = ? AND is_active = 1")) {
            ps.setString(1, docSubType);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    // ── Sheet 2: BOM Catalog ──────────────────────────────────────────────

    private static void writeBomCatalogSheet(XSSFWorkbook wb, Connection bomConn)
            throws SQLException {
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> result = reg.dispatch(ctx, "REPORT BOM CATALOG");

        if (!result.pass()) {
            System.out.println("  WARNING: BOM CATALOG failed: " + result.summary());
            return;
        }

        ReportBomCatalogVerb.CatalogPayload payload =
            (ReportBomCatalogVerb.CatalogPayload) result.payload();

        XSSFSheet sheet = wb.createSheet("BOM Catalog");
        String[] headers = {
            "BOM ID", "BOM Name", "BOM Type", "BOM Category",
            "Doc Sub Type", "Entity Type",
            "Child Count", "BUY Count", "MAKE Count", "PHANTOM Count"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "BOM CATALOG",
            "All active Bill of Materials definitions from BOM.db",
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        int rowIdx = headerRow + 1;
        for (int i = 0; i < payload.rows().size(); i++) {
            ReportBomCatalogVerb.CatalogRow r = payload.rows().get(i);
            boolean odd = (i % 2 == 1);
            CellStyle d = odd ? ds[1] : ds[0];
            CellStyle is = odd ? intOdd : intEven;

            Row row = sheet.createRow(rowIdx++);
            setCellStr(row, 0, r.bomId(), d);
            setCellStr(row, 1, r.bomName(), d);
            setCellStr(row, 2, r.bomType(), d);
            setCellStr(row, 3, r.bomCategory(), d);
            setCellStr(row, 4, r.docSubType(), d);
            setCellStr(row, 5, r.entityType(), d);
            setCellNum(row, 6, r.childCount(), is);
            setCellNum(row, 7, r.buyCount(), is);
            setCellNum(row, 8, r.makeCount(), is);
            setCellNum(row, 9, r.phantomCount(), is);
        }

        ReportTemplate.writeFooter(sheet, rowIdx + 1, payload.rows().size(), headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
    }

    // ── Sheet 3: Product Catalog ──────────────────────────────────────────

    private static void writeProductCatalogSheet(XSSFWorkbook wb, Connection bomConn)
            throws SQLException {
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> result = reg.dispatch(ctx, "REPORT PRODUCT CATALOG");

        if (!result.pass()) {
            System.out.println("  WARNING: PRODUCT CATALOG failed: " + result.summary());
            return;
        }

        ReportProductCatalogVerb.ProductCatalogPayload payload =
            (ReportProductCatalogVerb.ProductCatalogPayload) result.payload();

        XSSFSheet sheet = wb.createSheet("Product Catalog");
        String[] headers = {
            "Product ID", "Product Name", "IFC Class", "Product Type",
            "Width (m)", "Depth (m)", "Height (m)", "Volume (m\u00B3)"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "PRODUCT CATALOG",
            "All active M_Product definitions from BOM.db — IFC-classified components",
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle numEven = ReportTemplate.createNumericStyle(wb, false);
        CellStyle numOdd = ReportTemplate.createNumericStyle(wb, true);

        int rowIdx = headerRow + 1;
        for (int i = 0; i < payload.rows().size(); i++) {
            ReportProductCatalogVerb.ProductRow r = payload.rows().get(i);
            boolean odd = (i % 2 == 1);
            CellStyle d = odd ? ds[1] : ds[0];
            CellStyle ns = odd ? numOdd : numEven;

            Row row = sheet.createRow(rowIdx++);
            setCellStr(row, 0, r.productId(), d);
            setCellStr(row, 1, r.productName(), d);
            setCellStr(row, 2, r.ifcClass(), d);
            setCellStr(row, 3, r.productType(), d);
            setCellDbl(row, 4, r.widthM(), ns);
            setCellDbl(row, 5, r.depthM(), ns);
            setCellDbl(row, 6, r.heightM(), ns);
            setCellDbl(row, 7, r.volumeM3(), ns);
        }

        ReportTemplate.writeFooter(sheet, rowIdx + 1, payload.rows().size(), headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
    }

    // ── Sheet 4-5: BOM Structure (per building) ──────────────────────────

    private static void writeBomStructureSheet(XSSFWorkbook wb, Connection bomConn,
                                                String bomId) throws SQLException {
        VerbContext ctx = VerbContext.ofBom(bomConn);
        VerbRegistry reg = VerbRegistry.createDefault();
        VerbResult<?> result = reg.dispatch(ctx, "REPORT BOM STRUCTURE " + bomId);

        if (!result.pass()) {
            System.out.println("  WARNING: BOM STRUCTURE " + bomId + " failed: " + result.summary());
            return;
        }

        ReportBomStructureVerb.StructurePayload payload =
            (ReportBomStructureVerb.StructurePayload) result.payload();

        XSSFSheet sheet = wb.createSheet(bomId);
        String[] headers = {
            "Level", "BOM ID", "Child Product ID", "Component Type",
            "Role", "Sequence", "dx (m)", "dy (m)", "dz (m)",
            "Alloc W (mm)", "Alloc D (mm)", "Alloc H (mm)",
            "Product Name", "IFC Class", "Entity Type"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "BOM STRUCTURE — " + bomId,
            "Full BOM explosion — recursive tree with resolved M_Product references",
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle numEven = ReportTemplate.createNumericStyle(wb, false);
        CellStyle numOdd = ReportTemplate.createNumericStyle(wb, true);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        int rowIdx = headerRow + 1;
        for (int i = 0; i < payload.rows().size(); i++) {
            ReportBomStructureVerb.StructureRow r = payload.rows().get(i);
            boolean odd = (i % 2 == 1);
            CellStyle d = odd ? ds[1] : ds[0];
            CellStyle ns = odd ? numOdd : numEven;
            CellStyle is = odd ? intOdd : intEven;

            int indent = Math.min(r.level(), 5);
            CellStyle indentStyle = wb.createCellStyle();
            indentStyle.cloneStyleFrom(d);
            indentStyle.setIndention((short) (indent * 2));

            Row row = sheet.createRow(rowIdx++);
            setCellNum(row, 0, r.level(), is);
            Cell bomCell = row.createCell(1);
            bomCell.setCellValue(r.bomId());
            bomCell.setCellStyle(indentStyle);
            setCellStr(row, 2, r.childProductId(), d);
            setCellStr(row, 3, r.componentType(), d);
            setCellStr(row, 4, r.role(), d);
            setCellNum(row, 5, r.sequence(), is);
            setCellDbl(row, 6, r.dx(), ns);
            setCellDbl(row, 7, r.dy(), ns);
            setCellDbl(row, 8, r.dz(), ns);
            setCellNum(row, 9, r.allocatedWMm(), is);
            setCellNum(row, 10, r.allocatedDMm(), is);
            setCellNum(row, 11, r.allocatedHMm(), is);
            setCellStr(row, 12, r.productName(), d);
            setCellStr(row, 13, r.ifcClass(), d);
            setCellStr(row, 14, r.entityType(), d);
        }

        ReportTemplate.writeFooter(sheet, rowIdx + 1, payload.rows().size(), headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
    }

    // ── Sheet 6: TB Output Elements (overproduction exposed) ─────────────

    private static void writeTbElementsSheet(XSSFWorkbook wb, Connection tbConn)
            throws SQLException {
        XSSFSheet sheet = wb.createSheet("TB Output Elements");
        String[] headers = {
            "ID", "GUID", "Discipline", "IFC Class",
            "Element Name", "Element Type", "Storey", "Status"
        };

        // Count actual vs expected
        int actual;
        try (Statement st = tbConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM elements_meta")) {
            actual = rs.next() ? rs.getInt(1) : 0;
        }
        int expected = 139;
        int overproduction = actual - expected;

        int headerRow = ReportTemplate.writeHeader(sheet,
            "TB_LKTN OUTPUT — COMPILATION AUDIT",
            String.format("Expected: %d elements | Actual: %d | OVERPRODUCTION: +%d (+%.0f%%) — NEEDS OVERHAUL",
                expected, actual, overproduction, (overproduction * 100.0 / expected)),
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);

        // Overproduction warning style
        CellStyle warnStyle = wb.createCellStyle();
        warnStyle.cloneStyleFrom(ds[0]);
        warnStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        warnStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        int rowIdx = headerRow + 1;
        int count = 0;
        try (Statement st = tbConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, guid, discipline, ifc_class, element_name, "
                 + "element_type, storey FROM elements_meta ORDER BY id")) {
            while (rs.next()) {
                boolean odd = (count % 2 == 1);
                CellStyle d = odd ? ds[1] : ds[0];

                // Elements beyond expected count get red "EXCESS" status
                boolean excess = count >= expected;
                String status = excess ? "EXCESS" : "OK";

                Row row = sheet.createRow(rowIdx++);
                setCellNum(row, 0, rs.getInt("id"), intEven);
                setCellStr(row, 1, rs.getString("guid"), d);
                setCellStr(row, 2, rs.getString("discipline"), d);
                setCellStr(row, 3, rs.getString("ifc_class"), d);
                setCellStr(row, 4, rs.getString("element_name"), d);
                setCellStr(row, 5, rs.getString("element_type"), d);
                setCellStr(row, 6, rs.getString("storey"), d);
                setCellStr(row, 7, status, excess ? warnStyle : d);
                count++;
            }
        }

        // Add discipline breakdown after data
        int breakdownRow = rowIdx + 2;
        Row brkHeader = sheet.createRow(breakdownRow);
        CellStyle boldStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);
        setCellStr(brkHeader, 0, "DISCIPLINE BREAKDOWN", boldStyle);
        setCellStr(brkHeader, 1, "IFC Class", boldStyle);
        setCellStr(brkHeader, 2, "Count", boldStyle);
        setCellStr(brkHeader, 3, "% of Total", boldStyle);

        int brkIdx = breakdownRow + 1;
        try (Statement st = tbConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT discipline, ifc_class, COUNT(*) as cnt "
                 + "FROM elements_meta GROUP BY discipline, ifc_class "
                 + "ORDER BY cnt DESC")) {
            while (rs.next()) {
                int cnt = rs.getInt("cnt");
                Row row = sheet.createRow(brkIdx++);
                setCellStr(row, 0, rs.getString("discipline"), ds[0]);
                setCellStr(row, 1, rs.getString("ifc_class"), ds[0]);
                setCellNum(row, 2, cnt, intEven);
                setCellDbl(row, 3, cnt * 100.0 / actual,
                    ReportTemplate.createNumericStyle(wb, false));
            }
        }

        ReportTemplate.writeFooter(sheet, brkIdx + 1, count, headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
    }

    // ── Sheet 7: TB QTO (Quantity Takeoff) ────────────────────────────────

    private static void writeTbQtoSheet(XSSFWorkbook wb, Connection tbConn)
            throws SQLException {
        XSSFSheet sheet = wb.createSheet("TB QTO");
        String[] headers = {
            "Discipline", "IFC Class", "Storey", "Measurement",
            "Element Count", "Total Qty", "UoM",
            "Avg Qty", "Unit Cost (RM)", "Total Cost (RM)"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "TB_LKTN QUANTITY TAKEOFF",
            "Compiled QTO from GENERATIVE pipeline — costs in Malaysian Ringgit (RM)",
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle numEven = ReportTemplate.createNumericStyle(wb, false);
        CellStyle numOdd = ReportTemplate.createNumericStyle(wb, true);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        int rowIdx = headerRow + 1;
        int count = 0;
        try (Statement st = tbConn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM simple_qto ORDER BY discipline, ifc_class")) {
            while (rs.next()) {
                boolean odd = (count % 2 == 1);
                CellStyle d = odd ? ds[1] : ds[0];
                CellStyle ns = odd ? numOdd : numEven;
                CellStyle is = odd ? intOdd : intEven;

                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, rs.getString("discipline"), d);
                setCellStr(row, 1, rs.getString("ifc_class"), d);
                setCellStr(row, 2, rs.getString("storey"), d);
                setCellStr(row, 3, rs.getString("measurement_type"), d);
                setCellNum(row, 4, rs.getInt("element_count"), is);
                setCellDbl(row, 5, rs.getDouble("total_quantity"), ns);
                setCellStr(row, 6, rs.getString("uom"), d);
                setCellDbl(row, 7, rs.getDouble("avg_quantity"), ns);
                setCellDbl(row, 8, rs.getDouble("unit_cost_rm"), ns);
                setCellDbl(row, 9, rs.getDouble("total_cost_rm"), ns);
                count++;
            }
        }

        // Add total cost row
        Row totalRow = sheet.createRow(rowIdx + 1);
        CellStyle totalStyle = wb.createCellStyle();
        Font totalFont = wb.createFont();
        totalFont.setBold(true);
        totalFont.setFontHeightInPoints((short) 11);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        setCellStr(totalRow, 0, "TOTAL", totalStyle);
        try (Statement st = tbConn.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT SUM(element_count), SUM(total_cost_rm) FROM simple_qto")) {
            if (rs.next()) {
                setCellNum(totalRow, 4, rs.getInt(1), totalStyle);
                setCellDbl(totalRow, 9, rs.getDouble(2),
                    ReportTemplate.createNumericStyle(wb, false));
            }
        }

        ReportTemplate.writeFooter(sheet, rowIdx + 3, count, headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx, headers.length);
    }

    // ── Sheet 8: Terminal Discipline Summary (input/extracted DB) ────────

    private static void writeTerminalDisciplineSheet(XSSFWorkbook wb, Connection teConn)
            throws SQLException {
        XSSFSheet sheet = wb.createSheet("TE Disciplines");

        // Get totals
        int totalElements;
        int totalClashes;
        try (Statement st = teConn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM elements_meta");
            totalElements = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            rs = st.executeQuery("SELECT COUNT(*) FROM clash_status");
            totalClashes = rs.next() ? rs.getInt(1) : 0;
            rs.close();
        }

        String[] headers = {
            "Discipline", "Element Count", "% of Total",
            "Clashes (as A)", "Clashes (as B)", "Total Clashes",
            "Clash Rate (%)", "Top Clash IFC Class", "Top Clash Count"
        };

        int headerRow = ReportTemplate.writeHeader(sheet,
            "TERMINAL — DISCIPLINE ANALYSIS",
            String.format("SJTII Terminal (extracted) — %,d elements, %,d clashes across %d disciplines"
                + " — INPUT/EXTRACTED DB (pre-compilation)",
                totalElements, totalClashes, 9),
            headers.length);
        ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle numEven = ReportTemplate.createNumericStyle(wb, false);
        CellStyle numOdd = ReportTemplate.createNumericStyle(wb, true);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        // Red highlight for high-clash disciplines
        CellStyle highClashStyle = wb.createCellStyle();
        highClashStyle.cloneStyleFrom(ds[0]);
        highClashStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        highClashStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font hcFont = wb.createFont();
        hcFont.setBold(true);
        hcFont.setColor(IndexedColors.DARK_RED.getIndex());
        highClashStyle.setFont(hcFont);

        // Query: discipline → element count + clash counts
        String sql = "SELECT d.discipline, d.cnt AS elem_count, "
            + "COALESCE(ca.clash_a, 0) AS clashes_a, "
            + "COALESCE(cb.clash_b, 0) AS clashes_b, "
            + "COALESCE(ca.clash_a, 0) + COALESCE(cb.clash_b, 0) AS total_clashes, "
            + "tc.top_class, tc.top_count "
            + "FROM (SELECT discipline, COUNT(*) AS cnt FROM elements_meta GROUP BY discipline) d "
            + "LEFT JOIN (SELECT discipline_a AS discipline, COUNT(*) AS clash_a "
            + "           FROM clash_status GROUP BY discipline_a) ca ON d.discipline = ca.discipline "
            + "LEFT JOIN (SELECT discipline_b AS discipline, COUNT(*) AS clash_b "
            + "           FROM clash_status GROUP BY discipline_b) cb ON d.discipline = cb.discipline "
            + "LEFT JOIN (SELECT discipline, ifc_class AS top_class, cnt AS top_count FROM ("
            + "  SELECT discipline, ifc_class, COUNT(*) AS cnt, "
            + "         ROW_NUMBER() OVER (PARTITION BY discipline ORDER BY COUNT(*) DESC) AS rn "
            + "  FROM elements_meta GROUP BY discipline, ifc_class"
            + ") WHERE rn = 1) tc ON d.discipline = tc.discipline "
            + "ORDER BY d.cnt DESC";

        int rowIdx = headerRow + 1;
        int count = 0;
        try (Statement st = teConn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                boolean odd = (count % 2 == 1);
                CellStyle d = odd ? ds[1] : ds[0];
                CellStyle ns = odd ? numOdd : numEven;
                CellStyle is = odd ? intOdd : intEven;

                int elemCount = rs.getInt("elem_count");
                int clashesA = rs.getInt("clashes_a");
                int clashesB = rs.getInt("clashes_b");
                int totClash = rs.getInt("total_clashes");
                double clashRate = elemCount > 0 ? (totClash * 100.0 / elemCount) : 0;

                // Highlight if clash rate > 50%
                CellStyle rateStyle = clashRate > 50 ? highClashStyle : ns;

                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, rs.getString("discipline"), d);
                setCellNum(row, 1, elemCount, is);
                setCellDbl(row, 2, elemCount * 100.0 / totalElements, ns);
                setCellNum(row, 3, clashesA, is);
                setCellNum(row, 4, clashesB, is);
                setCellNum(row, 5, totClash, totClash > 100 ? highClashStyle : is);
                setCellDbl(row, 6, clashRate, rateStyle);
                setCellStr(row, 7, rs.getString("top_class") != null
                    ? rs.getString("top_class") : "", d);
                setCellNum(row, 8, rs.getInt("top_count"), is);
                count++;
            }
        }

        // Total row
        Row totalRow = sheet.createRow(rowIdx + 1);
        CellStyle totalStyle = wb.createCellStyle();
        Font totalFont = wb.createFont();
        totalFont.setBold(true);
        totalFont.setFontHeightInPoints((short) 11);
        totalStyle.setFont(totalFont);
        totalStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        setCellStr(totalRow, 0, "TOTAL", totalStyle);
        setCellNum(totalRow, 1, totalElements, totalStyle);
        setCellDbl(totalRow, 2, 100.0,
            ReportTemplate.createNumericStyle(wb, false));
        setCellNum(totalRow, 5, totalClashes, totalStyle);

        ReportTemplate.writeFooter(sheet, rowIdx + 3, count, headers.length);
        ReportTemplate.finalize(sheet, headerRow, rowIdx, headers.length);
    }

    // ── Sheet 9: Terminal Clash Analysis ───────────────────────────────────

    private static void writeTerminalClashSheet(XSSFWorkbook wb, Connection teConn)
            throws SQLException {
        XSSFSheet sheet = wb.createSheet("TE Clash Analysis");

        int totalClashes;
        int totalGroups;
        int highGroups;
        try (Statement st = teConn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM clash_status");
            totalClashes = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            rs = st.executeQuery("SELECT COUNT(*) FROM clash_groups");
            totalGroups = rs.next() ? rs.getInt(1) : 0;
            rs.close();
            rs = st.executeQuery("SELECT COUNT(*) FROM clash_groups WHERE severity = 'HIGH'");
            highGroups = rs.next() ? rs.getInt(1) : 0;
            rs.close();
        }

        // This sheet has two sections: Clash Pairs + Top Clash Groups
        int colCount = 9; // max column width for both sections

        int headerRow = ReportTemplate.writeHeader(sheet,
            "TERMINAL — CLASH ANALYSIS",
            String.format("SJTII Terminal — %,d clashes, %d groups (%d HIGH / %d MEDIUM)"
                + " — pre-compilation extracted model",
                totalClashes, totalGroups, highGroups, totalGroups - highGroups),
            colCount);

        // ── Section 1: Clash Pairs by Discipline ────────────────────────
        String[] pairHeaders = {
            "Discipline A", "Discipline B", "Clash Count",
            "% of All Clashes", "Top IFC Class A", "Top IFC Class B",
            "Avg Distance (mm)", "Status"
        };
        ReportTemplate.writeColumnHeaders(sheet, headerRow, pairHeaders);

        CellStyle[] ds = ReportTemplate.createDataStyles(wb);
        CellStyle numEven = ReportTemplate.createNumericStyle(wb, false);
        CellStyle numOdd = ReportTemplate.createNumericStyle(wb, true);
        CellStyle intEven = ReportTemplate.createIntStyle(wb, false);
        CellStyle intOdd = ReportTemplate.createIntStyle(wb, true);

        // High-count clash pair style (red)
        CellStyle critStyle = wb.createCellStyle();
        critStyle.cloneStyleFrom(ds[0]);
        critStyle.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        critStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font critFont = wb.createFont();
        critFont.setBold(true);
        critFont.setColor(IndexedColors.DARK_RED.getIndex());
        critStyle.setFont(critFont);

        // Warning style (orange)
        CellStyle warnStyle = wb.createCellStyle();
        warnStyle.cloneStyleFrom(ds[0]);
        warnStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        warnStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        String pairSql = "SELECT discipline_a, discipline_b, COUNT(*) AS cnt, "
            + "AVG(distance) AS avg_dist, "
            + "( SELECT ifc_class_a FROM clash_status cs2 "
            + "  WHERE cs2.discipline_a = cs.discipline_a AND cs2.discipline_b = cs.discipline_b "
            + "  GROUP BY ifc_class_a ORDER BY COUNT(*) DESC LIMIT 1 ) AS top_class_a, "
            + "( SELECT ifc_class_b FROM clash_status cs2 "
            + "  WHERE cs2.discipline_a = cs.discipline_a AND cs2.discipline_b = cs.discipline_b "
            + "  GROUP BY ifc_class_b ORDER BY COUNT(*) DESC LIMIT 1 ) AS top_class_b "
            + "FROM clash_status cs "
            + "GROUP BY discipline_a, discipline_b "
            + "ORDER BY cnt DESC";

        int rowIdx = headerRow + 1;
        int pairCount = 0;
        try (Statement st = teConn.createStatement();
             ResultSet rs = st.executeQuery(pairSql)) {
            while (rs.next()) {
                boolean odd = (pairCount % 2 == 1);
                CellStyle d = odd ? ds[1] : ds[0];
                CellStyle ns = odd ? numOdd : numEven;
                CellStyle is = odd ? intOdd : intEven;

                int cnt = rs.getInt("cnt");
                double pct = cnt * 100.0 / totalClashes;
                String severity;
                CellStyle sevStyle;
                if (cnt > 500) {
                    severity = "CRITICAL";
                    sevStyle = critStyle;
                } else if (cnt > 50) {
                    severity = "WARNING";
                    sevStyle = warnStyle;
                } else {
                    severity = "MINOR";
                    sevStyle = d;
                }

                Row row = sheet.createRow(rowIdx++);
                setCellStr(row, 0, rs.getString("discipline_a"), d);
                setCellStr(row, 1, rs.getString("discipline_b"), d);
                setCellNum(row, 2, cnt, cnt > 500 ? critStyle : is);
                setCellDbl(row, 3, pct, ns);
                setCellStr(row, 4, rs.getString("top_class_a") != null
                    ? rs.getString("top_class_a") : "", d);
                setCellStr(row, 5, rs.getString("top_class_b") != null
                    ? rs.getString("top_class_b") : "", d);
                setCellDbl(row, 6, rs.getDouble("avg_dist"), ns);
                setCellStr(row, 7, severity, sevStyle);
                pairCount++;
            }
        }

        // ── Section 2: Top Clash Groups ─────────────────────────────────
        int groupHeaderIdx = rowIdx + 2;
        CellStyle sectionStyle = wb.createCellStyle();
        Font sectionFont = wb.createFont();
        sectionFont.setBold(true);
        sectionFont.setFontHeightInPoints((short) 13);
        sectionFont.setColor(ReportTemplate.HEADER_FG);
        sectionStyle.setFont(sectionFont);
        sectionStyle.setFillForegroundColor(ReportTemplate.HEADER_BG);
        sectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Row sectionRow = sheet.createRow(groupHeaderIdx);
        sectionRow.setHeightInPoints(22);
        Cell sectionCell = sectionRow.createCell(0);
        sectionCell.setCellValue("TOP CLASH GROUPS — Cascade Elements (single element → many clashes)");
        sectionCell.setCellStyle(sectionStyle);
        sheet.addMergedRegion(new CellRangeAddress(
            groupHeaderIdx, groupHeaderIdx, 0, colCount - 1));

        String[] groupHeaders = {
            "Group ID", "Cascade IFC Class", "Cascade Discipline",
            "Total Clashes", "Affected Disciplines", "Affected IFC Classes",
            "Severity"
        };
        int groupColRow = groupHeaderIdx + 1;
        ReportTemplate.writeColumnHeaders(sheet, groupColRow, groupHeaders);

        String groupSql = "SELECT group_id, cascade_element_class, "
            + "cascade_element_discipline, total_clashes, "
            + "affected_disciplines, affected_classes, severity "
            + "FROM clash_groups ORDER BY total_clashes DESC LIMIT 25";

        int gRowIdx = groupColRow + 1;
        int groupCount = 0;
        try (Statement st = teConn.createStatement();
             ResultSet rs = st.executeQuery(groupSql)) {
            while (rs.next()) {
                boolean odd = (groupCount % 2 == 1);
                CellStyle d = odd ? ds[1] : ds[0];
                CellStyle is = odd ? intOdd : intEven;

                int tc = rs.getInt("total_clashes");
                String sev = rs.getString("severity");
                CellStyle sevStyle = "HIGH".equals(sev) ? critStyle : warnStyle;

                Row row = sheet.createRow(gRowIdx++);
                setCellStr(row, 0, rs.getString("group_id"), d);
                setCellStr(row, 1, rs.getString("cascade_element_class"), d);
                setCellStr(row, 2, rs.getString("cascade_element_discipline"), d);
                setCellNum(row, 3, tc, tc > 100 ? critStyle : is);
                setCellStr(row, 4, rs.getString("affected_disciplines"), d);
                setCellStr(row, 5, rs.getString("affected_classes"), d);
                setCellStr(row, 6, sev, sevStyle);
                groupCount++;
            }
        }

        // Summary footer
        ReportTemplate.writeFooter(sheet, gRowIdx + 1,
            pairCount + groupCount, colCount);
        ReportTemplate.finalize(sheet, headerRow, gRowIdx - 1, colCount);
    }

    // ── Cell helpers ──────────────────────────────────────────────────────

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
}
