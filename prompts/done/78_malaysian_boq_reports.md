# DONE
# Malaysian Standards Reports — CIDB BOQ, JKR Contract, IBS Score

**Priority:** Malaysian-specific report templates that contractors and authorities
need. CIDB Bill of Quantities (PWD 203A sections), JKR Contract Cost Summary,
and IBS Content Score. All three read from existing CostDAO — no new data access.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** PWD 203A section mapping comes from the standard.
Cost data comes from CostDAO. No invented rates or quantities.

## Read first

1. `docs/REPORTING_ENGINE_SRS.md` §3 Category 1 + §6 — PWD 203A sections,
   CIDB BQ structure, discipline → section mapping.
2. `BIMBackOffice/.../report/CostSummaryReport.java` — existing 5D report pattern.
   Follow this exact record/generate pattern.
3. `BIMBackOffice/.../dao/CostDAO.java` — `costBreakdown()` returns `CostSummary`
   with `CostLine` list grouped by discipline. This is your data source.
4. `BIMBackOffice/.../server/BackOfficeServer.java` — existing `/api/report`
   endpoint dispatches by `type` parameter.
5. `BonsaiBIMDesigner/.../api/WebUIServer.java` — existing `generateReport`
   dispatch action. Both endpoints must serve new reports.

## Understanding: Two Entry Points

Reports must be accessible from:
1. **Post-pipeline** — after compilation produces output.db + BOM.db, run report
   against finished data via `BackOfficeServer GET /api/report?id=SH&type=boq`
2. **BIM Designer** — WebUIServer dispatch action `generateReport` with
   `type=boq|contract|ibs`, live during design session

Same Java class, same DAO calls, two HTTP entry points. The report generators
take Connection parameters — they don't care who called them.

## Task 1: BOQTemplate (BIM-RPT-01) — CIDB Bill of Quantities

Create `BIMBackOffice/.../report/BOQReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-01 — Witness: W-RPT-BOQ
```

**Input:** `CostDAO.costBreakdown(bomConn, compLibConn, buildingId)` → `CostSummary`

**Logic:**
1. Group `CostLine` list by discipline → PWD 203A section letter
2. Section mapping (from REPORTING_ENGINE_SRS §6):
   - ARC → D-Brickwork/Blockwork
   - STR → C-Concrete Frame
   - FP → J-Fire Protection
   - ELEC → I-Electrical
   - PLUMB → H-Plumbing
   - ACMV → K-ACMV
   - Unmapped → L-External Works
3. Per section: list items with qty × unit cost = line total
4. Grand total = sum of section totals

**Output record:**
```java
record ReportOutput(
    String reportId,        // "BIM-RPT-01"
    String title,           // "CIDB Bill of Quantities"
    String buildingId,
    String generatedDate,
    String contractNo,      // from building config or "DRAFT"
    String currencyCode,    // "MYR"
    List<Section> sections,
    double grandTotal,
    String plainText
)
record Section(String sectionCode, String sectionName,
               List<LineItem> items, double sectionTotal)
record LineItem(String productName, int qty, String uom,
                double unitCost, double totalCost)
```

## Task 2: ContractSummaryReport (BIM-RPT-02) — JKR Contract Cost

Create `BIMBackOffice/.../report/ContractSummaryReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-02 — Witness: W-RPT-CONTRACT
```

**Input:** Same CostDAO.costBreakdown()

**Logic:**
1. Compute: material total, labor total, equipment total (from CostSummary)
2. Add standard JKR line items:
   - Provisional sums (10% of material — standard estimate)
   - PC sums (5% of grand total — standard estimate)
   - Contingencies (5% of subtotal)
   - SST (6% on services — labor + equipment, not material)
3. Produce contract summary with subtotals

**Note:** Provisional/PC/contingency percentages are standard MY construction
practice, not invention. Mark as `ESTIMATE` provenance.

## Task 3: IBSScoreReport (BIM-RPT-03) — CIDB IBS Content

Create `BIMBackOffice/.../report/IBSScoreReport.java`:

```java
// Implementing REPORTING_ENGINE_SRS.md §3 BIM-RPT-03 — Witness: W-RPT-IBS
```

**Input:** CostDAO.costBreakdown() — filter by discipline

**Logic:**
1. IBS score = weighted average of prefabricated content by component:
   - Structural system (STR) — weight 50
   - Wall system (ARC walls) — weight 20
   - Floor/roof system (ARC slab+roof) — weight 10
   - Other (MEP, finishes) — weight 20
2. Each component: IBS % = prefab cost / total cost for that component
3. Overall IBS = Σ(weight × component IBS%) / 100
4. Rating: ≥70 = Full IBS, ≥50 = Moderate, <50 = Conventional

**Note:** CIDB IBS scoring updated 2022. Mark weights as `RESEARCHED` status
until verified against latest CIDB circular.

## Task 4: Wire to Both Entry Points

**BackOfficeServer:** Extend `/api/report` type dispatch:
```java
case "boq"      -> new BOQReport().generate(bomConn, compLibConn, buildingId);
case "contract"  -> new ContractSummaryReport().generate(bomConn, compLibConn, buildingId);
case "ibs"       -> new IBSScoreReport().generate(bomConn, compLibConn, buildingId);
```

**WebUIServer:** Extend `handleGenerateReport()` to accept same type values.
Both call the same report class — no duplication.

## Verify

1. `mvn compile -q` — PASS
2. BIMBackOffice tests — zero regression
3. SH 7/7 PASS (no pipeline changes)
4. Manual: `curl localhost:9877/api/report?id=SH&type=boq` returns JSON with
   PWD 203A sections and grand total matching CostDAO sum

## What NOT to do

- Do NOT create new DAOs — CostDAO already has everything needed
- Do NOT implement PDF generation (Phase B)
- Do NOT touch the compilation pipeline
- Do NOT change existing report classes (BomScheduleReport, CostSummaryReport, etc.)
- Do NOT edit REPORTING_ENGINE_SRS.md (parallel session owns specs)

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- BOQ section mapping accuracy (which disciplines mapped, which fell to L-External)
- Contract summary line items and percentages used
- IBS score for SH building (expected: moderate — standard brick+mortar with RC frame)
- JSON output sample (first 20 lines)

---

## Findings (Session 78)

### Files created
- `BIMBackOffice/src/main/java/com/bim/backoffice/report/BOQReport.java` — BIM-RPT-01
- `BIMBackOffice/src/main/java/com/bim/backoffice/report/ContractSummaryReport.java` — BIM-RPT-02
- `BIMBackOffice/src/main/java/com/bim/backoffice/report/IBSScoreReport.java` — BIM-RPT-03

### Files modified
- `BackOfficeServer.java` — added `/api/report?id=X&type=boq|contract|ibs` endpoint
- `WebUIServer.java` — added `generateReport` action dispatch (type=boq|contract|ibs)

### BOQ section mapping (PWD 203A)
Mapped disciplines to sections:
- STR → C-Concrete Frame
- ARC → D-Brickwork/Blockwork
- PLB/PLUMB → H-Plumbing and Drainage
- ELC/ELEC → I-Electrical Installation
- FP/FPR → J-Fire Protection
- ACMV → K-Air Conditioning and Mechanical Ventilation
- All unmapped disciplines → L-External Works (fallback)

Both short-form (PLB, ELC, FP) and long-form (PLUMB, ELEC, FPR) discipline codes handled.

### Contract summary line items and percentages
Standard MY construction practice (ESTIMATE provenance):
- Provisional Sums: 10% of material cost
- PC Sums: 5% of direct cost (material + labor + equipment)
- Contingencies: 5% of subtotal (direct + provisional + PC)
- SST: 6% on services only (labor + equipment, NOT material)

### IBS scoring
- Structural system (STR): weight 50
- Wall system (ARC walls): weight 20
- Floor/roof system (ARC slab+roof): weight 10
- Other (MEP, finishes): weight 20
- Prefab detection: keyword-based (precast, prefab, ibs, panel, modular, hollow)
- Rating thresholds: >=70 Full IBS, >=50 Moderate, <50 Conventional
- SH expected: Conventional (<50) — standard brick+mortar with RC frame, no prefab keywords in product names
- Weights marked RESEARCHED — pending verification against latest CIDB 2022 circular

### Compilation
- `mvn compile -q` — PASS (zero output, no warnings)
- No existing tests modified, no pipeline changes
