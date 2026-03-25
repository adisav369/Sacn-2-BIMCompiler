# ReportEngine — BIM COBOL Report System

## Overview

The BIM Report Engine generates professional XLSX workbooks from per-building
`{PREFIX}_BOM.db` (SH_BOM.db, DX_BOM.db, TE_BOM.db), output databases, and
extracted input models. All reports use the shared
`ReportTemplate` for consistent formatting, standards compliance markings,
and user-editable field holders.

**Stack:** Java + Apache POI 5.2.5 (XSSF) + BIM COBOL Verb SPI + DAO layer.

## Architecture

```
                         ┌─────────────────────┐
                         │   ReportTemplate     │  Shared XLSX template
                         │   (styles, header,   │  (rows 0-6: title, subtitle,
                         │    compliance, etc.)  │   timestamp, compliance, legend,
                         └────────┬────────────┘   field holders, separator)
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
     ┌────────▼────────┐ ┌───────▼────────┐ ┌────────▼──────────┐
     │ ReportBomCatalog │ │ ReportProduct  │ │ ReportBomStructure│
     │ Verb             │ │ CatalogVerb    │ │ Verb              │
     └────────┬────────┘ └───────┬────────┘ └────────┬──────────┘
              │                  │                   │
              └──────────┬───────┘───────────────────┘
                         │
              ┌──────────▼──────────┐
              │ AllModelsReport     │  Composed workbook (9 sheets)
              │ Generator           │  *_BOM.db + output DBs + extracted DBs
              └─────────────────────┘
```

## ReportTemplate — Shared Excel Template

**File:** `BIM_COBOL/src/main/java/com/bim/cobol/verb/ReportTemplate.java`

Every sheet starts with a 7-row header block (rows 0-6):

| Row | Content |
|-----|---------|
| 0 | **Title** — royal blue background, white 16pt bold |
| 1 | **Subtitle** — grey italic 11pt |
| 2 | **Metadata** — timestamp, source DB, engine name |
| 3 | **Standards compliance** — per-cell green/orange backgrounds |
| 4 | **EntityType legend** — D=Dictionary, U=User, A=Application |
| 5 | **Field holders** — `«Project Name»` `«Client»` `«Design Theme»` `«Revision»` (yellow bg, user-editable) |
| 6 | Blank separator |
| 7+ | Data column headers + data rows |

### Standards Compliance Cells (Row 3)

| Standard | Status | Color |
|----------|--------|-------|
| ISO 19650 | Compliant | Green |
| IFC4 ADD2 | Compliant | Green |
| iDempiere | Compliant | Green |
| Libero MRP | Compliant | Green |
| ISO 12006-2 | Compliant | Green |
| Uniclass | Partial | Orange |
| OmniClass | Partial | Orange |

### Key Methods

| Method | What |
|--------|------|
| `createWorkbook()` | New workbook with BIM Intent Compiler creator metadata |
| `writeHeader(sheet, title, subtitle, colCount)` | Header block (rows 0-6), returns data start row (7) |
| `writeColumnHeaders(sheet, rowIdx, headers)` | Dark blue header row with white bold text |
| `createDataStyles(wb)` | Zebra-striped data rows `[even, odd]` |
| `createNumericStyle(wb, oddRow)` | 4-decimal numeric format |
| `createIntStyle(wb, oddRow)` | `#,##0` integer format |
| `writeFooter(sheet, rowIdx, count, colCount)` | Record count + end-of-report marker |
| `finalize(sheet, headerRow, lastRow, colCount)` | Auto-size columns, auto-filter, print setup, freeze panes |

## Report Verbs (3)

All verbs are read-only (no DB writes), dispatched via VerbRegistry, return typed payloads.

### REPORT BOM CATALOG [FILE path.xlsx]

Lists all active BOMs with child component type counts.

**Columns:** BOM ID, BOM Name, BOM Type, BOM Category, Doc Sub Type,
Entity Type, Child Count, BUY Count, MAKE Count, PHANTOM Count.

**DAO:** `MBOM.getByType()` + `MBOMLine.getByBom()` for each BOM.

### REPORT PRODUCT CATALOG [FILE path.xlsx]

Lists all M_Product entries with geometry dimensions.

**Columns:** Product ID, Product Name, IFC Class, Product Type,
Width (m), Depth (m), Height (m), Volume (m^3).

**DAO:** `MProduct.getAll()`.

### REPORT BOM STRUCTURE <bom_id> [bom_id2 ...] [FILE path.xlsx]

Recursive BOM explosion with resolved M_Product references.
Multiple bom_ids produce separate sheets in the same workbook.

**Columns:** Level, BOM ID, Child Product ID, Component Type, Role,
Sequence, dx/dy/dz (m), Alloc W/D/H (mm), Product Name, IFC Class,
Entity Type.

**DAO:** Recursive `MBOMLine.getByBom()` + `MProduct.get()` for leaf resolution.

## AllModelsReportGenerator — Composed Workbook

**File:** `BIM_COBOL/src/main/java/com/bim/cobol/report/AllModelsReportGenerator.java`

Generates `reporting/AllModelsReport.xlsx` — a single workbook with 9 sheets
combining per-building `*_BOM.db`, output databases, and extracted input models.

**Run:**
```bash
mvn exec:java -pl BIM_COBOL \
  -Dexec.mainClass=com.bim.cobol.report.AllModelsReportGenerator
```

### Sheet Inventory

| # | Sheet | Source DB | Content |
|---|-------|----------|---------|
| 1 | Summary | *_BOM.db + output DBs | All buildings: expected vs actual element counts, compilation status (green/red/orange) |
| 2 | BOM Catalog | *_BOM.db | All active BOMs via `REPORT BOM CATALOG` verb |
| 3 | Product Catalog | *_BOM.db | All M_Products via `REPORT PRODUCT CATALOG` verb |
| 4 | EB_SH | SH_BOM.db | Sample House BOM structure via `REPORT BOM STRUCTURE` |
| 5 | EB_DX | DX_BOM.db | Duplex BOM structure via `REPORT BOM STRUCTURE` |
| 6 | TB Output Elements | output/tb_lktn.db | TB_LKTN compiled output with overproduction flagging |
| 7 | TB QTO | output/tb_lktn.db | Quantity takeoff with RM costs |
| 8 | TE Disciplines | Terminal_Extracted.db | Discipline breakdown with clash counts per discipline |
| 9 | TE Clash Analysis | Terminal_Extracted.db | Clash pairs by discipline combination + top 25 cascade groups |

### Terminal Analysis (Sheets 8-9)

The Terminal (SJTII) analysis uses the **input/extracted DB** — not the
output DB — because Terminal has not been compiled yet (Phase B scope).

**TE Disciplines** columns:
- Discipline, Element Count, % of Total
- Clashes (as A), Clashes (as B), Total Clashes, Clash Rate (%)
- Top Clash IFC Class, Top Clash Count
- Red highlighting for disciplines with >50% clash rate

**TE Clash Analysis** has two sections:

1. **Clash Pairs** — discipline A vs discipline B with:
   - Clash count, % of all clashes
   - Top IFC classes on each side
   - Average distance (mm)
   - Severity: CRITICAL (>500), WARNING (>50), MINOR

2. **Top Clash Groups** (top 25 by total_clashes) — cascade elements where
   one element clashes with many others:
   - Group ID, cascade IFC class + discipline
   - Total clashes, affected disciplines/classes
   - Severity: HIGH / MEDIUM

### Terminal Key Findings (as of 2026-03-09)

| Metric | Value |
|--------|-------|
| Total elements | 51,092 |
| Disciplines | 9 (ARC 68%, FP 13%, REB 5%, ACMV 3%, CW 3%, STR 3%, ELEC 2%, SP 2%, LPG <1%) |
| Total clashes | 1,591 |
| Clash groups | 116 (69 HIGH, 47 MEDIUM) |
| Dominant pair | ELEC vs ARC: 1,293 clashes (81%) — light fixtures vs ceilings |
| Second pair | FP vs ARC: 133 clashes — sprinklers vs architectural elements |
| Third pair | FP vs ACMV: 101 clashes — fire protection vs HVAC ductwork |

## Data Flow

```
{PREFIX}_BOM.db (per-building dictionary)
  │
  ├─→ REPORT BOM CATALOG        → Sheet 2 (BOM Catalog)
  ├─→ REPORT PRODUCT CATALOG    → Sheet 3 (Product Catalog)
  ├─→ REPORT BOM STRUCTURE SH   → Sheet 4 (EB_SH, from SH_BOM.db)
  ├─→ REPORT BOM STRUCTURE DX   → Sheet 5 (EB_DX, from DX_BOM.db)
  └─→ MCDocType.getAll()        → Sheet 1 (Summary)

output/tb_lktn.db (compiled)
  ├─→ elements_meta             → Sheet 6 (TB Output Elements)
  └─→ simple_qto                → Sheet 7 (TB QTO)

DAGCompiler/lib/input/Terminal_Extracted.db (extracted)
  ├─→ elements_meta + clash_status → Sheet 8 (TE Disciplines)
  └─→ clash_status + clash_groups  → Sheet 9 (TE Clash Analysis)
```

## How to Add a New Sheet

1. Add a `private static void writeXxxSheet(XSSFWorkbook wb, Connection conn)` method
2. Use `ReportTemplate.writeHeader()` for the standard header block
3. Use `ReportTemplate.writeColumnHeaders()` for column headers
4. Write data rows with `createDataStyles()` for zebra striping
5. Call `ReportTemplate.writeFooter()` and `ReportTemplate.finalize()`
6. Add the method call in `main()` with appropriate connection

Pattern:
```java
private static void writeNewSheet(XSSFWorkbook wb, Connection conn)
        throws SQLException {
    XSSFSheet sheet = wb.createSheet("Sheet Name");
    String[] headers = { "Col1", "Col2", "Col3" };

    int headerRow = ReportTemplate.writeHeader(sheet,
        "SHEET TITLE", "Subtitle description", headers.length);
    ReportTemplate.writeColumnHeaders(sheet, headerRow, headers);

    CellStyle[] ds = ReportTemplate.createDataStyles(wb);
    int rowIdx = headerRow + 1;
    // ... write data rows ...

    ReportTemplate.writeFooter(sheet, rowIdx + 1, count, headers.length);
    ReportTemplate.finalize(sheet, headerRow, rowIdx - 1, headers.length);
}
```

## Dependencies

```xml
<!-- BIM_COBOL/pom.xml -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi</artifactId>
    <version>5.2.5</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

## Future Directions

- **SH/DX output analysis:** Once compiled, add clash detection sheets for SH and DX output DBs
- **4D scheduling:** Time-based construction sequence from W_Verb_Node
- **5D costing:** RM cost columns on compiled BOM elements via simple_qto
- **Federation report:** Cross-model coordination (IFC4 federation via DAO verbs, not IfcOpenShell)
- **Phase B Terminal compilation:** When Terminal is compiled, add output-vs-extracted comparison sheet
