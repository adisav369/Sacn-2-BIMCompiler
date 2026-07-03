# Reporting Engine — BIM Report Templates (Light → iDempiere Plugin)

> **Foundation:** [BACK_OFFICE_SRS](BACK_OFFICE_SRS.md) (ReportDAO, CostDAO, ScheduleDAO) ·
> [TIER1_SRS](TIER1_SRS.md) (4D-7D DAOs) · [DATA_MODEL](DATA_MODEL.md) (ERP schema) ·
> [STANDARDS_COMPLIANCE_SRS](STANDARDS_COMPLIANCE_SRS.md) (SC certificates)

<div class="bim-banner" markdown>
<b>DAO numbers become documents contractors sign and authorities stamp.</b> Standalone template engine (Phase A) with future iDempiere PrintFormat integration (Phase B).
</div>

**The 5D DAOs produce numbers. The Reporting Engine turns those numbers into
documents that contractors sign, authorities stamp, and directors present to boards.**

---

## §1 Architecture — Two-Phase Strategy

### Phase A: Standalone (this codebase, now)
Light template engine. No iDempiere dependency. Reads from existing DAOs
(`ReportDAO`, `CostDAO`, `ScheduleDAO`, `PortfolioDAO`) and output DBs.
Produces JSON + plain-text summaries. PDF generation via a minimal library
(e.g. OpenPDF or iText-LGPL). **This is what gets built first.**

### Phase B: iDempiere Plugin (later, when ERP runtime is live)
OSGi bundle wrapping Phase A DAOs into `AD_PrintFormat` metadata rows.
Users open iDempiere's PrintFormat designer, drag columns, change fonts.
The data layer is identical — only the rendering/layout layer changes.

**iDempiere dev environment on this machine:**
- Source: `/home/red1/idempiere-dev-setup/idempiere/`
- Eclipse IDE: `/home/red1/idempiere-dev-setup/eclipse/`
- Docker PostgreSQL: `postgres start postgres` (container command)
- `AD_PrintFormat` model: `org.adempiere.base/src/org/compiere/model/X_AD_PrintFormat.java`
- `AD_PrintFormatItem` model: `org.adempiere.base/src/org/compiere/model/X_AD_PrintFormatItem.java`
- `MPrintFormat` implementation: `org.adempiere.base/src/org/compiere/print/MPrintFormat.java`
- `AD_ReportView` model: `org.adempiere.base/src/org/compiere/model/X_AD_ReportView.java`
- PrintFormat editor UI: `org.idempiere.printformat.editor/src/.../WPrintFormatEditor.java`
- Pack-in/Pack-out handlers: `org.adempiere.pipo.handlers/src/.../PrintFormatElementHandler.java`

Phase A templates are designed so each maps 1:1 to a future `AD_PrintFormat`
row. Column definitions map to `AD_PrintFormatItem` rows. When Phase B arrives,
it is a packaging exercise — the report logic does not change.

---

## §2 What Already Exists (Do Not Rebuild)

| Existing Component | Actual Location | What It Provides |
|---|---|---|
| `ReportDAO` | `BIMBackOffice/.../dao/ReportDAO.java` | 8-method interface: `constructionSequence()`, `costBreakdown()`, `carbonFootprint()`, `assetRegister()`, `kpiDashboard()`, `boardStatus()`, `complianceMatrix()`, `milestoneEvent()` |
| `CostDAO` | `BIMBackOffice/.../dao/CostDAO.java` | 5D cost breakdowns — element-level cost by discipline, category |
| `ScheduleDAO` | `BIMBackOffice/.../dao/ScheduleDAO.java` | 4D construction scheduling — Gantt tasks, dependencies, durations |
| `PortfolioDAO` | `BIMBackOffice/.../dao/PortfolioDAO.java` | Cross-project portfolio analysis — WIP, completion %, overrun flags |
| `BackOfficeServer` | `BIMBackOffice/.../server/BackOfficeServer.java` | HTTP server exposing DAOs as REST-like endpoints |
| `ReportDAO.GanttTask` | same file | `record(id, name, phase, sequence, durationDays, dependency)` |
| `ReportDAO.CostLine` | same file | `record(discipline, category, productName, qty, unitCost, totalCost, uom)` — uom from M_Product.cost_uom (EA/M/M2). Qty × unitCost is only correct when cost_uom matches trade convention (see DISC_VALIDATION_DB_SRS §10.4.11 T3.5) |
| `ReportDAO.CarbonLine` | same file | `record(element, material, qty, carbonPerUnit, totalCarbon)` |
| `ReportDAO.AssetRecord` | same file | `record(guid, type, location, floor, system, manufacturer, maintenanceInterval)` |
| `ReportDAO.ComplianceResult` | same file | `record(ruleId, ruleName, jurisdiction, verdict, citation, detail)` |
| `ReportDAO.KPI` | same file | `record(name, value, target, unit, status)` |
| `ReportDAO.BoardCard` | same file | `record(orderId, name, status, progress, blockers, estCost)` |

**The data layer is complete.** Reports are a rendering concern on top of these
existing DAO record types. No new data access code is needed for Phase A.

---

## §3 Report Catalogue — 16 Templates

Each template is a standalone class that takes a DAO connection context and
produces structured output. In Phase A this is JSON + text. In Phase B this
becomes an `AD_PrintFormat` row with `AD_PrintFormatItem` columns.

### Category 1 — Malaysian Standards

| ID | Report | DAO Source | Key Output |
|---|---|---|---|
| BIM-RPT-01 | CIDB Bill of Quantities | `CostDAO.costBreakdown()` | PWD 203A sections, quantities, rates, totals |
| BIM-RPT-02 | JKR Contract Cost Summary | `CostDAO.costBreakdown()` | PC sums, provisional sums, contingency, SST |
| BIM-RPT-03 | CIDB IBS Content Score | `CostDAO.costBreakdown()` | Structural/wall/other IBS scoring |
| BIM-RPT-11 | UBBL Compliance Summary | `ReportDAO.complianceMatrix()` | One-page: rule count, pass rate, certificate ID |

### Category 2 — 2D Drawing Schedules

| ID | Report | DAO Source | Key Output |
|---|---|---|---|
| BIM-RPT-04 | Room Schedule | output.db `spatial_structure` | Name, area, dimensions, finish, compliance |
| BIM-RPT-05 | Door Schedule | output.db `elements_meta` (IfcDoor) | Mark, type, W×H, fire rating, hardware |
| BIM-RPT-06 | Window Schedule | output.db `elements_meta` (IfcWindow) | Mark, type, W×H, glazing, U-value |

### Category 3 — Discipline Takeoffs

| ID | Report | DAO Source | Key Output |
|---|---|---|---|
| BIM-RPT-07 | Structural Takeoff | `CostDAO.costBreakdown()` filtered STR | Concrete m³, rebar kg, formwork m² |
| BIM-RPT-08 | Fire Protection Takeoff | `CostDAO.costBreakdown()` filtered FP | Heads count, pipe m by dia, valve list |
| BIM-RPT-09 | ACMV Takeoff | `CostDAO.costBreakdown()` filtered ACMV | Duct m², AHU list, diffusers, CFM |

### Category 4 — Standards Compliance

| ID | Report | DAO Source | Key Output |
|---|---|---|---|
| BIM-RPT-10 | Full Proof Chain | `compliance_proof.db` SC_Proof_Line | One row per rule per space — full evidence |
| BIM-RPT-11 | UBBL Summary | `compliance_proof.db` SC_Run | One-page authority submission |

### Category 5 — Financial

| ID | Report | DAO Source | Key Output |
|---|---|---|---|
| BIM-RPT-12 | 4D Construction Programme | `ScheduleDAO` → `ReportDAO.constructionSequence()` | Gantt, WBS, critical path |
| BIM-RPT-13 | Earned Value Management | `CostDAO` × `ScheduleDAO` | PV, EV, AC, SPI, CPI, EAC, VAC |
| BIM-RPT-14 | Project P&L Summary | `CostDAO` | Contract sum, variations, cost to complete, margin |
| BIM-RPT-15 | Cash Flow Forecast | `CostDAO` × `ScheduleDAO` | Monthly inflow/outflow, retention |
| BIM-RPT-16 | Portfolio Dashboard | `PortfolioDAO` → `ReportDAO.boardStatus()` | Multi-project WIP, overrun flags |

---

## §4 Phase A Implementation — Exact Java

### §4.1 Package Layout

```
DAGCompiler/src/main/java/com/bim/compiler/report/
├── BIMReportEngine.java           # Dispatcher — route template ID to renderer
├── ReportContext.java             # What building, jurisdiction, date range
├── ReportOutput.java             # Record: json + plainText + optional pdfBytes
├── template/
│   ├── BOQTemplate.java          # BIM-RPT-01: CIDB BQ
│   ├── ContractSummaryTemplate.java  # BIM-RPT-02
│   ├── IBSScoreTemplate.java     # BIM-RPT-03
│   ├── RoomScheduleTemplate.java # BIM-RPT-04
│   ├── DoorScheduleTemplate.java # BIM-RPT-05
│   ├── WindowScheduleTemplate.java   # BIM-RPT-06
│   ├── StrTakeoffTemplate.java   # BIM-RPT-07
│   ├── FpTakeoffTemplate.java    # BIM-RPT-08
│   ├── AcmvTakeoffTemplate.java  # BIM-RPT-09
│   ├── ProofChainTemplate.java   # BIM-RPT-10
│   ├── UbblSummaryTemplate.java  # BIM-RPT-11
│   ├── GanttTemplate.java        # BIM-RPT-12
│   ├── EvmTemplate.java          # BIM-RPT-13
│   ├── ProfitLossTemplate.java   # BIM-RPT-14
│   ├── CashFlowTemplate.java     # BIM-RPT-15
│   └── PortfolioTemplate.java    # BIM-RPT-16
└── format/
    └── ReportTemplate.java       # Interface — all templates implement this
```

### §4.2 ReportTemplate Interface

```java
// DAGCompiler/src/main/java/com/bim/compiler/report/format/ReportTemplate.java
package com.bim.compiler.report.format;

import com.bim.compiler.report.ReportContext;
import com.bim.compiler.report.ReportOutput;
import java.sql.Connection;

public interface ReportTemplate {
    /** Template ID — matches BIM-RPT-NN catalogue. */
    String templateId();

    /** Human-readable report name. */
    String name();

    /** Generate report from DAO connections. */
    ReportOutput generate(ReportContext ctx, Connection bomConn,
                          Connection compConn, Connection valConn);
}
```

### §4.3 ReportContext Record

```java
// DAGCompiler/src/main/java/com/bim/compiler/report/ReportContext.java
package com.bim.compiler.report;

public record ReportContext(
    String buildingPrefix,      // "DM", "SH", "TE"
    String buildingName,        // "Demo House (Malaysia)"
    String jurisdiction,        // "MY", "UK", null
    String reportDate,          // ISO date for report header
    String clientName,          // from project config or classify YAML
    String contractorName,
    String contractNo,
    String currencyCode,        // MYR, GBP, SGD
    String preparedBy
) {}
```

### §4.4 ReportOutput Record

```java
// DAGCompiler/src/main/java/com/bim/compiler/report/ReportOutput.java
package com.bim.compiler.report;

public record ReportOutput(
    String templateId,
    String json,              // Structured data — always produced
    String plainText,         // Human-readable summary — always produced
    byte[] pdfBytes           // PDF — null if PDF library not available
) {
    public boolean hasPdf() { return pdfBytes != null && pdfBytes.length > 0; }
}
```

### §4.5 BIMReportEngine — Dispatcher

```java
// DAGCompiler/src/main/java/com/bim/compiler/report/BIMReportEngine.java
package com.bim.compiler.report;

import com.bim.compiler.report.format.ReportTemplate;
import com.bim.compiler.report.template.*;
import java.sql.Connection;
import java.util.*;

public class BIMReportEngine {

    private static final Map<String, ReportTemplate> TEMPLATES = new LinkedHashMap<>();
    static {
        register(new BOQTemplate());
        register(new ContractSummaryTemplate());
        register(new IBSScoreTemplate());
        register(new RoomScheduleTemplate());
        register(new DoorScheduleTemplate());
        register(new WindowScheduleTemplate());
        register(new StrTakeoffTemplate());
        register(new FpTakeoffTemplate());
        register(new AcmvTakeoffTemplate());
        register(new ProofChainTemplate());
        register(new UbblSummaryTemplate());
        register(new GanttTemplate());
        register(new EvmTemplate());
        register(new ProfitLossTemplate());
        register(new CashFlowTemplate());
        register(new PortfolioTemplate());
    }

    private static void register(ReportTemplate t) { TEMPLATES.put(t.templateId(), t); }

    /** Generate one report by template ID. */
    public ReportOutput generate(String templateId, ReportContext ctx,
                                  Connection bomConn, Connection compConn,
                                  Connection valConn) {
        ReportTemplate t = TEMPLATES.get(templateId);
        if (t == null) throw new IllegalArgumentException("Unknown template: " + templateId);
        return t.generate(ctx, bomConn, compConn, valConn);
    }

    /** Generate all applicable reports for a building. */
    public List<ReportOutput> generateAll(ReportContext ctx,
                                           Connection bomConn, Connection compConn,
                                           Connection valConn) {
        List<ReportOutput> results = new ArrayList<>();
        for (ReportTemplate t : TEMPLATES.values()) {
            results.add(t.generate(ctx, bomConn, compConn, valConn));
        }
        return results;
    }

    /** List available template IDs. */
    public static Set<String> availableTemplates() { return TEMPLATES.keySet(); }
}
```

### §4.6 Example Template — BOQTemplate (BIM-RPT-01)

```java
// DAGCompiler/src/main/java/com/bim/compiler/report/template/BOQTemplate.java
package com.bim.compiler.report.template;

import com.bim.backoffice.dao.CostDAO;
import com.bim.backoffice.dao.ReportDAO.CostLine;
import com.bim.compiler.report.*;
import com.bim.compiler.report.format.ReportTemplate;
import java.sql.Connection;
import java.util.*;

public class BOQTemplate implements ReportTemplate {

    @Override public String templateId() { return "BIM-RPT-01"; }
    @Override public String name() { return "CIDB Bill of Quantities"; }

    @Override
    public ReportOutput generate(ReportContext ctx, Connection bomConn,
                                  Connection compConn, Connection valConn) {
        CostDAO costDAO = new CostDAO();
        List<CostLine> lines = costDAO.costBreakdown(bomConn, compConn, ctx.buildingPrefix());

        // Group by discipline → PWD 203A section mapping
        Map<String, List<CostLine>> sections = groupBySection(lines);

        // Build JSON
        String json = toJson(ctx, sections);

        // Build plain text
        StringBuilder sb = new StringBuilder();
        sb.append("BILL OF QUANTITIES — ").append(ctx.buildingName()).append("\n");
        sb.append("Contract: ").append(ctx.contractNo()).append("\n");
        sb.append("Currency: ").append(ctx.currencyCode()).append("\n\n");
        double grandTotal = 0;
        for (var entry : sections.entrySet()) {
            sb.append("Section: ").append(entry.getKey()).append("\n");
            double sectionTotal = 0;
            for (CostLine line : entry.getValue()) {
                sb.append("  ").append(line.productName())
                  .append("  ").append(line.qty()).append(" ").append(line.uom())
                  .append("  @ ").append(line.unitCost())
                  .append("  = ").append(line.totalCost()).append("\n");
                sectionTotal += line.totalCost();
            }
            sb.append("  Section Total: ").append(sectionTotal).append("\n\n");
            grandTotal += sectionTotal;
        }
        sb.append("GRAND TOTAL: ").append(ctx.currencyCode())
          .append(" ").append(grandTotal).append("\n");

        return new ReportOutput(templateId(), json, sb.toString(), null);
    }

    private Map<String, List<CostLine>> groupBySection(List<CostLine> lines) {
        // PWD 203A section mapping: discipline → section letter
        Map<String, String> sectionMap = Map.of(
            "ARC", "D-Brickwork/Blockwork", "STR", "C-Concrete Frame",
            "FPR", "J-Fire Protection", "ELC", "I-Electrical",
            "PLB", "H-Plumbing", "ACMV", "K-ACMV"
        );
        Map<String, List<CostLine>> grouped = new LinkedHashMap<>();
        for (CostLine line : lines) {
            String section = sectionMap.getOrDefault(line.discipline(), "L-External Works");
            grouped.computeIfAbsent(section, k -> new ArrayList<>()).add(line);
        }
        return grouped;
    }

    private String toJson(ReportContext ctx, Map<String, List<CostLine>> sections) {
        // Minimal JSON serialisation — no dependency needed
        // In Phase B this becomes AD_PrintFormat metadata
        return "{}"; // stub — implement with project's JSON pattern
    }
}
```

---

## §5 Phase B — iDempiere Plugin (Future)

When the project runs an iDempiere server instance, Phase A templates become
data rows in the iDempiere `AD_PrintFormat` system. The mapping:

| Phase A Concept | iDempiere Equivalent |
|---|---|
| `ReportTemplate` interface | `AD_PrintFormat` row (one per template) |
| Template columns (fields) | `AD_PrintFormatItem` rows (one per column) |
| `ReportContext` | `AD_ReportView` + `WhereClause` |
| `BIMReportEngine` dispatcher | `IPrintFormatFactory` extension point |
| Template ID `BIM-RPT-01` | `AD_PrintFormat.Name = 'BIM CIDB Bill of Quantities (MY)'` |
| JSON output | `AD_ReportView` SQL view over output.db |

### iDempiere AD_PrintFormat Key Columns (from actual source)

From `X_AD_PrintFormat.java` at `/home/red1/idempiere-dev-setup/idempiere/org.adempiere.base/src/org/compiere/model/`:

- `AD_PrintFormat_ID` (PK), `Name`, `Description`, `IsActive`
- `AD_Table_ID` — which table this format renders
- `AD_ReportView_ID` — optional report view (SQL filter)
- `IsForm` (Y = form layout, N = columnar list)
- `IsTableBased` (Y = direct table, N = via AD_ReportView)
- `AD_PrintFont_ID`, `AD_PrintColor_ID`, `AD_PrintTableFormat_ID`
- `HeaderMargin`, `FooterMargin` (1/72 inch)
- `JasperProcess_ID` — optional Jasper Report process
- `FileNamePattern` — output filename

### iDempiere AD_PrintFormatItem Key Columns (from actual source)

From `X_AD_PrintFormatItem.java`:

- `AD_PrintFormatItem_ID` (PK), `AD_PrintFormat_ID` (FK parent)
- `SeqNo`, `SortNo` — display and sort order
- `PrintFormatType` — Text, Image, Shape, Line, Rectangle, Barcode
- `PrintAreaType` — Content, Column, Header, Footer
- `AD_Column_ID` — database column being printed
- `FieldAlignmentType` — Default, Left, Center, Right, Block
- `IsPrinted`, `IsGroupBy`, `IsOrderBy`, `IsSummarized`, `IsAveraged`
- `IsMinCalc`, `IsMaxCalc`, `IsCounted`, `IsDeviationCalc`, `IsVarianceCalc`
- `XPosition`, `YPosition`, `MaxWidth`, `MaxHeight`
- `AD_PrintFont_ID`, `AD_PrintColor_ID` — item-level styling
- `FormatPattern` — number/date format
- `DisplayLogic` — dynamic display condition
- `AD_PrintFormatChild_ID` — included sub-format (for composition)

### Plugin Bundle Identity (Phase B)

```
Bundle-Name:        BIM Reporting Engine
Bundle-SymbolicName: org.red1.bim.report;singleton:=true
Bundle-Version:     1.0.0.qualifier
Fragment-Host:      org.adempiere.base
Require-Bundle:     org.adempiere.base;bundle-version="11.0.0",
                    org.compiere.report;bundle-version="11.0.0"
Bundle-RequiredExecutionEnvironment: JavaSE-17
```

**Phase B is a wrapping exercise.** The Phase A `ReportTemplate` implementations
become the data-access layer behind iDempiere's `MPrintFormat.createPDF()`.
No business logic changes — only presentation routing changes.

---

## §6 CIDB BQ Section Structure (PWD 203A)

```
Section A  — Preliminary and General
Section B  — Substructure (earthworks, piling, foundations)
Section C  — Concrete Frame (columns, beams, slabs, staircases)
Section D  — Brickwork and Blockwork
Section E  — Roofing
Section F  — Woodwork (doors, windows, frames)
Section G  — Metal Work
Section H  — Plumbing and Drainage (MS 1228)
Section I  — Electrical Installation (MS IEC 60364)
Section J  — Fire Protection (MS 1910 / UBBL Part VII)
Section K  — Air Conditioning and Mechanical Ventilation (MS 1525)
Section L  — External Works
Provisional Sums
PC Sums
Contingencies
```

Each section maps to `CostLine.discipline()` codes from `CostDAO.costBreakdown()`.

---

## §7 EVM Fields (BIM-RPT-13)

These are computed from the intersection of `CostDAO` and `ScheduleDAO`:

```
PV  (Planned Value)     = scheduled cost to date from ScheduleDAO
EV  (Earned Value)      = % complete × BAC from CostDAO
AC  (Actual Cost)       = purchase orders + labour records (C_Order actuals)
SPI (Schedule Perf.)    = EV / PV
CPI (Cost Perf.)        = EV / AC
EAC (Est. at Complete)  = BAC / CPI
VAC (Variance at Compl) = BAC - EAC
```

**AC Gap:** AC requires real purchase order actuals from iDempiere
`M_InOut` / `C_Invoice`. Until wired, EVM stubs AC = 0 and marks report
as DRAFT. This is a Phase B dependency.

---

## §8 Phase Sequence

### Phase RE-1 — Scaffold + BOQ (2 sessions)
1. Create `report/` package under DAGCompiler
2. Implement `ReportTemplate` interface, `ReportContext`, `ReportOutput`, `BIMReportEngine`
3. Implement `BOQTemplate` (BIM-RPT-01) reading from `CostDAO.costBreakdown()`
4. Implement `ContractSummaryTemplate` (BIM-RPT-02)
5. **Gate:** DemoHouse_MY produces BOQ JSON with PWD 203A sections.
   Grand total matches `CostDAO` sum.

### Phase RE-2 — Drawing Schedules (1 session)
1. Implement Room, Door, Window schedule templates (BIM-RPT-04/05/06)
2. Read from output.db `spatial_structure` and `elements_meta` directly
3. **Gate:** SampleHouse produces room schedule with correct areas matching output.db.

### Phase RE-3 — Discipline Takeoffs + IBS (1 session)
1. Implement STR/FP/ACMV takeoff templates (BIM-RPT-07/08/09)
2. Implement IBS score template (BIM-RPT-03)
3. **Gate:** Takeoff quantities match discipline-filtered `CostDAO` output.

### Phase RE-4 — Compliance Reports (1 session, after SC-1)
1. Implement ProofChainTemplate (BIM-RPT-10) reading `compliance_proof.db`
2. Implement UbblSummaryTemplate (BIM-RPT-11)
3. **Blocked by:** STANDARDS_COMPLIANCE_SRS Phase SC-1

### Phase RE-5 — Financial + Portfolio (2 sessions)
1. Implement Gantt (BIM-RPT-12) from `ScheduleDAO`
2. Implement EVM (BIM-RPT-13), P&L (BIM-RPT-14), Cash Flow (BIM-RPT-15)
3. Implement Portfolio (BIM-RPT-16) from `PortfolioDAO`
4. **EVM caveat:** AC = 0 stub until iDempiere procurement wired. DRAFT watermark.

### Phase RE-6 — iDempiere Plugin (deferred, Phase B)
1. Create OSGi bundle at `org.red1.bim.report`
2. Seed `AD_PrintFormat` rows (IDs 2100001–2100016)
3. Seed `AD_PrintFormatItem` rows per template
4. Register virtual `AD_Table` stubs for BIM views
5. Wire `IPrintFormatFactory` extension point
6. **Gate:** Templates visible in iDempiere PrintFormat designer UI.
   User can drag-reorder columns. PDF output matches Phase A JSON.

---

## §9 Open Gaps (Honest)

| Gap | Status | Note |
|---|---|---|
| AC (Actual Cost) source | PENDING | Requires iDempiere procurement wiring. EVM stubs AC = 0. |
| CIDB BQ section mapping for infra | PENDING | PWD 203A is buildings only. JKR road schedule = different sections. |
| IBS score 2022 amendment | PENDING | CIDB updated scoring weights. Source document required. |
| Retention schedule | PENDING | MY standard: 5% to 50%, 2.5% to DLP. Needs ScheduleDAO milestones. |
| PDF generation library | DESIGN | OpenPDF (LGPL) or iText 5 (AGPL). Decide before Phase RE-1. |
| Digital signature on PDF | DEFERRED | PKI infrastructure. Low priority for Phase A. |
| Bilingual output (BM/EN) | DEFERRED | CIDB submission ideally Bahasa Malaysia. Phase B concern. |

---

*Cross-references:*
*[BACK_OFFICE_SRS](BACK_OFFICE_SRS.md) — ReportDAO interface (existing 8-method interface)*
*[TIER1_SRS](TIER1_SRS.md) — CostDAO, ScheduleDAO implementations*
*[STANDARDS_COMPLIANCE_SRS](STANDARDS_COMPLIANCE_SRS.md) — SC_Run, SC_Proof_Line tables*
*[DATA_MODEL](DATA_MODEL.md) — output.db views (room_areas, area_by_storey)*
*[ACTION_ROADMAP §Open Gaps](ACTION_ROADMAP.md#open-gaps) — GAP-DA-4 dual rule ecosystem*

---

# APPENDIX — Codebase Reality Check (2026-03-27)

Audited by Claude against current `master`.

## Data Layer — EXISTS and ready

| Component | Status | Location |
|---|---|---|
| `ReportDAO` (8 methods) | EXISTS | `BIMBackOffice/.../dao/ReportDAO.java` |
| `CostDAO` | EXISTS | `BIMBackOffice/.../dao/CostDAO.java` |
| `ScheduleDAO` | EXISTS | `BIMBackOffice/.../dao/ScheduleDAO.java` |
| `PortfolioDAO` | EXISTS | `BIMBackOffice/.../dao/PortfolioDAO.java` |
| `BackOfficeServer` | EXISTS | `BIMBackOffice/.../server/BackOfficeServer.java` |

## Rendering Layer — TO BE BUILT (Phase A)

| Component | Status | Notes |
|---|---|---|
| `report/` package | MISSING | Create under DAGCompiler |
| `ReportTemplate` interface | MISSING | Phase RE-1 |
| All 16 template classes | MISSING | Phases RE-1 through RE-5 |
| `BIMReportEngine` dispatcher | MISSING | Phase RE-1 |

## iDempiere Layer — DEFERRED (Phase B)

| Component | Status | Notes |
|---|---|---|
| `org.red1.bim.report` OSGi bundle | MISSING | Phase RE-6 |
| `AD_PrintFormat` seed rows | MISSING | Phase RE-6 |
| `AD_PrintFormatItem` seed rows | MISSING | Phase RE-6 |
| iDempiere dev setup | EXISTS | `/home/red1/idempiere-dev-setup/idempiere/` |
| Docker PostgreSQL | EXISTS | `postgres start postgres` |

## Verdict

**Data layer complete. Rendering layer needs building. iDempiere integration deferred.**
Spec now uses exact DAO record types and method signatures from the codebase.
Phase A templates can be coded directly from §4 without further research.

*Copyright (c) 2025-2026 Redhuan D. Oon. MIT Licensed.*
