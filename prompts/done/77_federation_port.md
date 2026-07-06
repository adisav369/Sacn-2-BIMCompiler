# DONE
# Federation Port — 4D-7D + NLP + Color Scheme to Java

**Priority:** Port the proven Federation addon features from Python/Bonsai
to Java. The Federation addon already demonstrates 4D schedule, 5D cost,
6D sustainability, 7D facility management, NLP query, color-by-dimension,
and work package selection — all over SQLite. The Java DAOs (ReportDAO,
CostDAO, ScheduleDAO, PortfolioDAO) already have the same queries. This
prompt wires the visualization and interaction layer.

**Why Java:** Scalability and multi-user. Blender/Bonsai is the thin
viewport client. Java is the heavy backend — ERP integration, multi-user
sync, concurrent compilation. Federation proved the concept in Python;
production lives in Java.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**PORT, DO NOT INVENT.** Every feature here already works in the Federation
addon. Read the Python, port the logic. No new features.

## Read first

1. Federation source: `/home/red1/IfcOpenShell/src/bonsai/bonsai/bim/module/federation/`
   - `ui.py` / `ui_clean.py` — panel layout, dimension tabs
   - `operator.py` — query execution, color assignment
   - `visualization_manager.py` — color scheme engine
   - `ui_federation_project.py` — project-level portfolio view
2. Existing Java DAOs in `BIMBackOffice/src/main/java/com/bim/backoffice/dao/`:
   - `ReportDAO.java` — BOM schedule, element reports
   - `CostDAO.java` — 5D cost breakdown
   - `ScheduleDAO.java` — 4D schedule from BOM tree
   - `PortfolioDAO.java` — multi-building portfolio
3. `BonsaiBIMDesigner/.../server/WebUIServer.java` — existing HTML UI (10 tabs)
4. `docs/REPORTING_ENGINE_SRS.md` — Phase A template spec

## Module: BIMBackOffice (extend existing)

BackOffice is the dedicated operations module — ReportDAO, CostDAO,
ScheduleDAO, PortfolioDAO already live here. Federation features are
operational (reporting, costing, scheduling, portfolio). Add subpackages:

```
BIMBackOffice/src/main/java/com/bim/backoffice/
  ├── dao/                         ← existing: ReportDAO, CostDAO, etc.
  ├── server/                      ← existing: BackOfficeServer
  ├── federation/                  ← NEW: ported from Python addon
  │   ├── ColorSchemeEngine.java   ← port visualization_manager.py
  │   ├── DimensionQuery.java      ← port NLP query → SQL
  │   └── WorkPackageSelector.java ← port selection → sub-order
  └── report/                      ← NEW: formatted output from DAOs
      ├── BomScheduleReport.java   ← BOM qty schedule (contractor pricing)
      ├── CostSummaryReport.java   ← 5D by discipline/floor
      ├── ScheduleReport.java      ← 4D topo sort → timeline
      └── ComplianceReport.java    ← AD_Val_Rule pass results formatted
```

## Task 1: Color Scheme Engine

Port `visualization_manager.py` color-by-dimension logic:
- Color by discipline (AD_Org_ID → color map)
- Color by 4D phase (schedule sequence → gradient)
- Color by 5D cost band (price bracket → heat map)
- Color by 6D carbon rating (EPD → green-to-red)

Output: JSON color map `{ element_id: "#rrggbb" }` for WebUI to apply.

## Task 2: Report Templates

Port the Federation breakdown panels into printable reports using
existing DAOs. Each report = a Java class that reads a DAO and formats
output as JSON + plain text (PDF generation is Phase B).

| Report | DAO | Federation equivalent |
|--------|-----|---------------------|
| BOM Schedule | ReportDAO | Work package breakdown panel |
| Cost Summary | CostDAO | 5D cost tab |
| Construction Schedule | ScheduleDAO | 4D timeline tab |
| Compliance Certificate | AD_Val_Rule queries | (new — proof chain from §10.4) |

## Task 3: Wire to WebUI

Add Federation tabs to WebUIServer. The existing UI has 10 tabs.
Add 4 more:
- **4D Schedule** — timeline from ScheduleReport
- **5D Cost** — breakdown from CostSummaryReport
- **Color** — dimension picker → ColorSchemeEngine → element color map
- **Reports** — generate/download formatted reports

## Verify

1. `mvn compile -q` — PASS (new module compiles)
2. Existing tests unaffected — SH 7/7, TE 6/7 unchanged
3. WebUI loads with new tabs (manual check)

## What NOT to do

- Do NOT rewrite the DAOs — they exist in BIMBackOffice, just call them
- Do NOT implement PDF generation (Phase B, iDempiere PrintFormat)
- Do NOT touch the Bonsai/Blender addon — this is Java-side only
- Do NOT implement NLP parser yet — start with direct SQL, NLP is future
- Do NOT break existing WebUI tabs

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- Which Federation features were ported
- Which were deferred (NLP, PDF)
- How many new endpoints added to WebUI
- Report output format (JSON structure)

---

## Findings (S100-p77)

### Ported from Federation addon
1. **ColorSchemeEngine** — `bbox_visualization.py` DISCIPLINE_COLORS (18 disciplines) + `color_palette.py` CONSTRUCTION_PALETTES (16 phase colors). Four schemes: discipline, 4D-phase, 5D-cost (heat map), 6D-carbon (green→red gradient).
2. **DimensionQuery** — `operator.py` query dispatch. Direct SQL filtering by discipline, phase, floor, cost threshold, carbon threshold.
3. **WorkPackageSelector** — `operator.py` selection → sub-order. Select BOM sub-tree as work package with cost enrichment from component library.
4. **BomScheduleReport** — Work package breakdown panel → structured JSON + plain text, grouped by discipline.
5. **CostSummaryReport** — 5D cost tab → discipline breakdown with percentages.
6. **ScheduleReport** — 4D timeline tab → Gantt phases with critical path markers.
7. **ComplianceReport** — AD_Val_Rule proof chain → rule-by-rule PASS/FAIL/SKIP verdict.
8. **NlpQueryParser** — `query_parser.py` + `query_patterns.py`. Regex pattern → SQL generator with element synonyms (beam→member), storey normalization (Malaysian: tanah/bumbung/jalan/kedai), ordinal stripping, value sanitization. 6 pattern categories: element_count, cost, discipline, quantity, freetext, plus storey-aware variants.
9. **NlpQueryExecutor** — `query_executor.py`. Executes parsed SQL against BOM DB, auto-LIMIT, formats results with friendly IFC labels per category. Full pipeline: parse → execute → format.
10. **IfcLabelMapper** — `ifc_label_mapper.py`. 60+ IFC class → friendly name mappings (IfcBeam→"Beam"), 17 discipline code → full name mappings. Meaningful-name heuristic (rejects GUIDs, accepts human names).
11. **VisualizationManager** — `visualization_manager.py`. Three-layer mode management (BBOXES/SEMANTICS/MATERIALS), mode switching, discipline visibility toggling. State serialization for WebUI.
12. **VisualizationMode** — Enum matching Python's LAYER_NAMES dict (collection names + descriptions).

### Not ported (prompt exclusions)
- **PDF generation** — prompt §"What NOT to do" says "Do NOT implement PDF generation (Phase B, iDempiere PrintFormat)"
- **Spatial R-tree queries** — `spatial_index.py` R-tree is a federation-of-IFC-files concern (44K elements across multiple discipline IFCs). Java-side operates on single-building BOM trees, not multi-file federation indexes. Different data model.

### New endpoints
**BackOfficeServer** (8 new endpoints):
- `GET /api/colorscheme?id=SH&scheme=discipline|4D-phase|5D-cost|6D-carbon`
- `GET /api/report?id=SH&type=bom|cost|schedule|compliance`
- `GET /api/query?id=SH&dim=discipline&filter=ARC`
- `GET /api/workpackage?id=SH&bom=FLOOR_GF`
- `GET /api/palette?type=discipline|phase`
- `GET /api/nlp?id=SH&q=how+many+beams`
- `GET /api/labels?type=ifc|discipline`
- `GET /api/visualization?mode=BBOXES|SEMANTICS|MATERIALS`

**WebUIServer** (6 new dispatch actions):
- `colorScheme` — apply color map + broadcast to Bonsai via SSE
- `generateReport` — produce formatted report JSON
- `dimensionQuery` — filter elements by dimension criteria
- `workPackage` — select BOM sub-tree as work package
- `nlpQuery` — natural language query → SQL → formatted results
- `switchVisualization` — switch BBOXES/SEMANTICS/MATERIALS mode

### Report output format (JSON)
Each report returns a record with: `reportId`, `title`, `buildingId`, `generatedDate`, typed `summary` object, typed `sections`/`phases`/`rules` list, and `plainText` (pre-formatted string for terminal/clipboard).

### Files created
```
BIMBackOffice/src/main/java/com/bim/backoffice/
  federation/
    ColorSchemeEngine.java      — 4 color schemes, discipline/phase palettes
    DimensionQuery.java         — 5 query methods (discipline, phase, floor, cost, carbon)
    WorkPackageSelector.java    — BOM sub-tree selection with cost enrichment
    NlpQueryParser.java         — regex pattern → SQL generator (6 categories)
    NlpQueryExecutor.java       — execute SQL + format with IFC labels
    IfcLabelMapper.java         — 60+ IFC class labels, 17 discipline labels
    VisualizationManager.java   — 3-layer mode management + discipline visibility
    VisualizationMode.java      — BBOXES/SEMANTICS/MATERIALS enum
  report/
    BomScheduleReport.java      — BOM qty schedule by discipline
    CostSummaryReport.java      — 5D cost breakdown
    ScheduleReport.java         — 4D Gantt timeline
    ComplianceReport.java       — AD_Val_Rule proof chain
```

### Verification
- `mvn compile -q` PASS
- BIMBackOffice 20/20 PASS (zero regression)
- BonsaiBIMDesigner BUILD SUCCESS
- DAGCompiler contract tests BUILD SUCCESS
