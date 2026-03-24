# CORE SRS — BIM Intent Compiler Platform
> **Foundation:** [BBC](BOMBasedCompilation.md) · [DATA_MODEL](DATA_MODEL.md) · [BIM_COBOL](BIM_COBOL.md) · [SystemContract](SystemContract.md) · [TestArchitecture](TestArchitecture.md)

**Version:** 1.0 | **Date:** 2026-03-19
**Scope:** Scale research, reporting engine, industry gap closure, moat strategy
**Companion:** `BIM_Designer.md` (UI), `StrategicIndustryPositioning.md` (market)

---

## §1 Scale Research Program

### §1.1 Objective

Prove the compiler handles real-world diversity and volume. Current proof is
3 building types (residential SH/DX, commercial TE) + 3 infrastructure types
(bridge/road/rail). Industry tools handle thousands of building types at 1M+
element counts. We must prove verb grammar generalises and pipeline scales.

### §1.2 IFC Source Repositories

> **Moved to:** [`DAGCompiler/lib/input/IFC/IFCAnalysis.md`](../DAGCompiler/lib/input/IFC/IFCAnalysis.md)
> — full inventory, quality ratings, entity coverage matrix, and manual-download guide.
>
> **Current state:** 24 IFC files on disk (12 in-pipeline + 12 downloaded).
> Covers 6 building types: residential, commercial/terminal, institutional,
> and 3 infrastructure (bridge, road, rail via IFC4X3 PCERT).
>
> **Download target:** 50+ distinct models across 8 building typologies.

### §1.3 Extraction Pipeline for New Models

For each downloaded IFC:

```
Step 1: Extract    → python extract.py <file>.ifc → <prefix>_extracted.db
Step 2: Classify   → write classify_<prefix>.yaml (discipline mapping)
Step 3: Compile    → run_RosettaStones.sh <prefix> → <prefix>_BOM.db
Step 4: Mine       → run mining queries → DV0xx migration SQL
Step 5: Validate   → BOM QA checks (G1-G6 gates, or subset)
Step 6: Verb-mine  → VerbDetector report → new verb candidates
Step 7: Document   → <Prefix>Analysis.md (element census, verb distribution)
```

**Automation gate:** Steps 1-3 should be scriptable for batch processing.
`scripts/batch_extract.sh` takes a directory of IFC files and produces
extracted DBs + YAML templates.

### §1.4 Scale Stress Test Protocol

| Test | Target | Current Proven | Metric |
|------|--------|----------------|--------|
| ST-1 Element count | 500K elements | 48K (TE) | Pipeline completes without OOM |
| ST-2 BOM depth | 10 levels | 4 levels (TE) | Parent-relative offsets correct |
| ST-3 Verb factorization | 100:1 ratio | 37:1 (TE) | At least 50:1 on data centre |
| ST-4 Concurrent buildings | 20 buildings | 4 (SH/DX/TE/BR) | Total wall clock < 60s |
| ST-5 Discipline count | 12 disciplines | 8 (TE) | All IFC4X3 facility types |
| ST-6 Mixed IFC versions | IFC2X3 + IFC4 + IFC4X3 | 2X3 + 4 | Single pipeline run |
| ST-7 Memory ceiling | <4GB heap | ~2GB (TE) | -Xmx4g with 500K elements |

**Pass criteria:** G1-COUNT passes for every model. G2-VOLUME within 1%.
Pipeline wall clock scales linearly (not quadratic) with element count.

### §1.5 Verb Discovery from New Models

Each new model type should yield at least one new verb candidate:

| Building Type | Expected Verb | Pattern |
|--------------|---------------|---------|
| Hospital | ZONE | Sterile/non-sterile boundary placement |
| High-rise | STACK | Floor plate repetition with setbacks |
| Data centre | GRID_FILL | Cabinet rows with hot/cold aisle spacing |
| Tunnel | BORE | Circular segmental lining ring pattern |
| Hotel | MIRROR_STACK | Floor plate mirror + vertical repeat |
| Dam | TAPER | Gravity section with varying thickness |
| Factory | BAY | Portal frame bay repetition |

### §1.6 Rule Mining Protocol

For each new extracted model, run the standard mining queries
(same pattern as `TE_MINING_RESULTS.md` and `DV006b/DV007/DV008`):

```sql
-- Dimension census: avg W×D×H per ifc_class per segment
SELECT em.ifc_class, ss.name, COUNT(*),
       ROUND(AVG(rt.maxX-rt.minX)*1000,0),
       ROUND(AVG(rt.maxY-rt.minY)*1000,0),
       ROUND(AVG(rt.maxZ-rt.minZ)*1000,0)
FROM elements_meta em
JOIN elements_rtree rt ON em.id = rt.id
JOIN ... GROUP BY em.ifc_class, ss.name;

-- NN spacing: nearest-neighbour distances per class
-- Z-continuity: layer stacking, embed depth
-- Ratio rules: cross-element dimensional relationships
```

Output: `migration/DVxxx_<prefix>_rules.sql` + `<Prefix>RulesTest.java`.

---

## §2 Reporting & Analytics Engine

### §2.1 Architecture

The reporting engine bridges the compiler's structured output (5 databases)
to multiple client presentation layers. The engine is Java DAO + SQL, not
a monolithic report renderer. Each report is a **verb** that queries the
database and emits structured data (JSON/CSV/XML).

```
┌─────────────────────────────────────────────────────────┐
│                  REPORT ENGINE ARCHITECTURE              │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  DATA LAYER (existing databases)                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐ │
│  │component_lib │ │ {P}_BOM.db   │ │ ERP  │ │
│  │ M_Product    │ │ m_bom        │ │ ad_space_type    │ │
│  │ geometries   │ │ m_bom_line   │ │ placement_rules  │ │
│  └──────────────┘ └──────────────┘ └──────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐                      │
│  │ validation   │ │ work_output  │                      │
│  │ AD_Val_Rule  │ │ C_Order      │                      │
│  │ AD_Val_Param │ │ W_Variant    │                      │
│  └──────────────┘ └──────────────┘                      │
│                                                         │
│  REPORT DAO LAYER (Java)                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │ ReportDAO                                        │   │
│  │  .bomSummary(buildingId)     → BOMReport         │   │
│  │  .complianceMatrix(jurisd)  → ComplianceReport   │   │
│  │  .costBreakdown(buildingId) → CostReport         │   │
│  │  .scheduleSequence(orderId) → ScheduleReport     │   │
│  │  .carbonFootprint(buildingId)→ CarbonReport      │   │
│  │  .assetRegistry(facilityId) → AssetReport        │   │
│  │  .kpiDashboard(portfolioId) → KPIReport          │   │
│  │  .verbDistribution(buildingId)→ VerbReport       │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  EXPORT ADAPTERS                                        │
│  ┌───────┐ ┌───────┐ ┌──────┐ ┌──────┐ ┌────────────┐  │
│  │ JSON  │ │  CSV  │ │ XLSX │ │ IFC  │ │ iDempiere  │  │
│  │ (API) │ │(batch)│ │(CIDB)│ │(BCF) │ │  (REST)    │  │
│  └───────┘ └───────┘ └──────┘ └──────┘ └────────────┘  │
│                                                         │
│  CLIENT PRESENTATION (consuming adapters)               │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌─────────────┐  │
│  │ Kanban   │ │  KPI     │ │ Social │ │  Balanced   │  │
│  │ Board    │ │Dashboard │ │ Media  │ │  Scorecard  │  │
│  └──────────┘ └──────────┘ └────────┘ └─────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌─────────────┐  │
│  │ BCF      │ │ COBie    │ │ PDF    │ │  Regulatory  │  │
│  │ Issues   │ │ Handover │ │ Report │ │  Submission  │  │
│  └──────────┘ └──────────┘ └────────┘ └─────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### §2.2 Report Types (4D-7D)

#### 4D — Schedule Reports

| Report | Source | Output |
|--------|--------|--------|
| R-4D-01 Construction Sequence | PP_Order_Node verb ordering | Gantt JSON for timeline renderers |
| R-4D-02 Critical Path | PP_Order_Node dependencies | Critical chain with float analysis |
| R-4D-03 Phase Progress | C_Order.DocStatus per storey | % complete by discipline per floor |
| R-4D-04 Verb Execution Plan | VerbDetector verb distribution | Which verbs fire in which order |

**iDempiere analogue:** `PP_Order` → `PP_Order_Node` → `PP_Order_Workflow`.
Our `PP_Order_Node` already stores verb execution sequence. The reporting
engine reads this as a Gantt source.

#### 5D — Cost & Quantity Reports

| Report | Source | Output |
|--------|--------|--------|
| R-5D-01 BOM Cost Summary | m_bom_line × M_Product.cost | Total by discipline, storey, category |
| R-5D-02 Elemental Cost Plan | m_bom_line × CIDB rates | NRM-1 compliant cost plan |
| R-5D-03 Material Takeoff | m_bom_line grouped by material | Concrete m³, steel tonnes, timber m³ |
| R-5D-04 Procurement Schedule | m_bom_line × PP_Order_Node | What to buy when (material + time) |
| R-5D-05 Variance Report | C_Order vs W_Variant | Cost delta between design iterations |

**iDempiere analogue:** `M_CostDetail` → `C_InvoiceLine`. Our M_Product already
carries cost columns. The DAO joins m_bom_line quantities × product unit costs.

#### 6D — Sustainability Reports

| Report | Source | Output |
|--------|--------|--------|
| R-6D-01 Embodied Carbon | M_Product.carbon_kg_per_unit × qty | Total kgCO2e by element type |
| R-6D-02 Energy Model Input | m_bom_line envelope + U-values | IDF/gbXML export for EnergyPlus |
| R-6D-03 Material Passport | M_Product.material_name × qty | Circular economy material inventory |
| R-6D-04 LEED/BREEAM Checklist | AD_Val_Rule sustainability rules | Credit compliance matrix |

**Schema gap:** M_Product needs `carbon_kg_per_unit REAL`, `recyclability TEXT`,
`eol_strategy TEXT` columns. Migration: `V010_sustainability_columns.sql`.

#### 7D — Facility Management Reports

| Report | Source | Output |
|--------|--------|--------|
| R-7D-01 Asset Register | M_Product × m_bom_line with location | COBie-format asset handover |
| R-7D-02 Maintenance Schedule | AD_Val_Rule maintenance intervals | PM calendar by asset type |
| R-7D-03 Sensor Compliance | AD_Val_Rule sensor rules (NFPA/ASHRAE) | IoT sensor placement audit |
| R-7D-04 Lifecycle Cost | M_Product.cost × maintenance_interval × lifespan | NPV total cost of ownership |
| R-7D-05 Space Utilisation | m_bom floor AABB vs occupied area | Efficiency ratio per storey |

**IfcOpenShell Federation already has:** `asset_registry.py`, `maintenance_manager.py`,
`sensor_installation.py`, `compliance_templates.py`. These Python PoCs become
Java DAOs reading the same database schemas.

### §2.3 Client Presentation Layers

#### Kanban Board

Construction progress as card-based workflow. Each card = one `C_Order`
(building) or `C_OrderLine` (element group).

| Column | Maps to | Cards show |
|--------|---------|------------|
| Backlog | DocStatus = DR | Building name, element count, est. cost |
| In Progress | DocStatus = IP | % verbs executed, current discipline |
| Review | DocStatus = CO | Validation pass/fail count, blockers |
| Complete | DocStatus = AP | Final cost, duration, compliance score |

**Data source:** `ReportDAO.boardStatus(portfolioId)` returns JSON array
of `{orderId, name, status, progress, blockerCount, estCost}`.

**Integration targets:**
- Trello API (POST /1/cards)
- Jira REST (POST /rest/api/3/issue)
- GitHub Projects (GraphQL mutation)
- Notion API (POST /v1/pages)

#### KPI Dashboard

Real-time compilation metrics for project managers.

| KPI | Formula | Target |
|-----|---------|--------|
| Compilation Success Rate | PASS buildings / total buildings | > 95% |
| BOM Factorization Ratio | extracted elements / BOM lines | > 30:1 |
| Validation Compliance | PASS rules / total rules | 100% for AP |
| Cost Variance | (actual - baseline) / baseline | < 5% |
| Schedule Variance | (actual days - planned) / planned | < 10% |
| Carbon Intensity | total kgCO2e / gross floor area m² | < benchmark |
| Element Reuse | shared M_Product / total M_Product | > 60% |

**Data source:** `ReportDAO.kpiDashboard(portfolioId)` returns JSON map.

#### Balanced Scorecard (BSC)

Strategic performance measurement across four perspectives:

| Perspective | Measures | Source |
|-------------|----------|--------|
| **Financial** | Cost variance, procurement efficiency, NPV lifecycle | R-5D-01, R-7D-04 |
| **Client** | Design iteration count, approval cycle time, defect rate | W_Variant count, DocStatus timestamps |
| **Process** | Compilation throughput, gate pass rate, verb coverage | Pipeline metrics, G1-G6 results |
| **Learning** | New verb discovery rate, rule mining yield, model diversity | VerbDetector reports, DV0xx count |

**Data source:** `ReportDAO.balancedScorecard(orgId, period)` returns
four-quadrant JSON with traffic-light status per measure.

#### Social Media / Stakeholder Communication

Automated project milestone posts for non-technical stakeholders.

| Trigger | Content | Channel |
|---------|---------|---------|
| Building compiled (AP) | "Project X: 1,099 elements compiled to BOM in 2.3s. 100% compliance." | LinkedIn, Twitter/X |
| Gate passed (G1-G6) | "Rosetta Stone gate PASS: 48,428 elements verified." | Slack #project-updates |
| Milestone reached | "Phase G complete: BIM Designer handles all building types." | Email digest |
| Compliance report | "Jurisdiction MY: 30/30 rules PASS for Project X." | PDF attachment |

**Data source:** `ReportDAO.milestoneEvent(buildingId)` returns
structured event with headline, body, metrics, image path.

**Integration targets:**
- Slack Webhook (POST with Block Kit JSON)
- LinkedIn API (POST /v2/ugcPosts)
- Twitter/X API (POST /2/tweets)
- Email (SMTP with HTML template)
- MS Teams Webhook (POST with Adaptive Card JSON)

#### BCF Issue Management

BIM Collaboration Format (ISO 19650) for model issues.

| Issue Type | Source | BCF Fields |
|-----------|--------|------------|
| Clash | AD_Val_Rule CLASH type | guid, viewpoint, description, assignee |
| Non-compliance | AD_Val_Rule FAIL verdict | rule_id, jurisdiction, element_guid |
| Design query | W_Variant comparison | before/after viewpoint, cost delta |

**Data source:** `ReportDAO.bcfIssues(buildingId)` returns BCF 3.0 XML.

#### COBie Handover

Construction Operations Building information exchange (ISO 15686-4).

| COBie Sheet | Source |
|-------------|--------|
| Facility | C_Order building record |
| Floor | m_bom WHERE bom_type='FLOOR' |
| Space | m_bom WHERE bom_type='SET' (rooms) |
| Type | M_Product catalog |
| Component | m_bom_line LEAF elements |
| System | mep_systems from extraction |
| Attribute | M_AttributeSetInstance |

**Data source:** `ReportDAO.cobieExport(buildingId)` returns XLSX
with all 19 COBie sheets populated from BOM data.

#### Regulatory Submission Templates

Jurisdiction-specific compliance reports with verb citations.

| Template | Jurisdiction | Content |
|----------|-------------|---------|
| UBBL Compliance | MY (Malaysia) | AD_Val_Rule results, room dimensions, fire rating |
| Building Regs Part B | UK | Fire escape routes, compartmentation |
| IBC Chapter 10 | US | Means of egress, occupancy loads |
| BCA Section J | AU | Energy efficiency, glazing ratios |
| EN 1991-1-1 | EU | Structural load combinations |

**Data source:** `ReportDAO.regulatoryReport(buildingId, jurisdiction)`
returns structured report with rule citations and pass/fail verdicts.

### §2.4 ReportDAO Interface

```java
// Implementing CORE_SRS.md §2.4 — Witness: W-REPORT-DAO
public interface ReportDAO {

    // 4D Schedule
    record GanttTask(String id, String name, String phase,
                     int sequence, int durationDays, String dependency) {}
    List<GanttTask> constructionSequence(Connection bomConn, String buildingId);

    // 5D Cost
    record CostLine(String discipline, String category, String productName,
                    int qty, double unitCost, double totalCost, String uom) {}
    List<CostLine> costBreakdown(Connection bomConn, Connection compConn,
                                 String buildingId);

    // 6D Carbon
    record CarbonLine(String element, String material, int qty,
                      double carbonPerUnit, double totalCarbon) {}
    List<CarbonLine> carbonFootprint(Connection bomConn, Connection compConn,
                                     String buildingId);

    // 7D Assets
    record AssetRecord(String guid, String type, String location,
                       String floor, String system, String manufacturer,
                       String maintenanceInterval) {}
    List<AssetRecord> assetRegister(Connection bomConn, String buildingId);

    // KPI
    record KPI(String name, double value, double target,
               String unit, String status) {}
    List<KPI> kpiDashboard(Connection bomConn, Connection valConn,
                           String portfolioId);

    // Board
    record BoardCard(String orderId, String name, String status,
                     double progress, int blockers, double estCost) {}
    List<BoardCard> boardStatus(Connection workConn);

    // Compliance
    record ComplianceResult(String ruleId, String ruleName,
                            String jurisdiction, String verdict,
                            String citation, String detail) {}
    List<ComplianceResult> complianceMatrix(Connection valConn,
                                           String buildingId, String jurisdiction);

    // Milestone events (for social/notification)
    record MilestoneEvent(String type, String headline, String body,
                          Map<String, String> metrics) {}
    MilestoneEvent milestoneEvent(Connection bomConn, String buildingId);
}
```

---

## §3 Industry Gap Closure

### §3.1 Gap Matrix — Current vs Required

| Capability | Solibri | Autodesk ACC | BIM Compiler (now) | BIM Compiler (target) | Priority |
|-----------|---------|-------------|-------------------|----------------------|----------|
| IFC4X3 infrastructure | No | No | **30 rules mined** | 100+ rules, 8 types | P1 |
| Clash detection (hard) | Full | Full | Spatial predicates | BBox + clearance check | P1 |
| Clash detection (soft) | Full | Full | None | Zone overlap check | P2 |
| Multi-model federation | Full | Full | Single model | Federated C_Order set | P1 |
| Issue management (BCF) | Native | Native | None | BCF 3.0 export | P2 |
| PDF report export | Native | Native | None | Via ReportDAO + template | P2 |
| Real-time multi-user | No | Yes | Single TCP | WebSocket + OT | P3 |
| AI-assisted design | No | Partial | BIM COBOL NL | NL→verb compiler | P3 |
| COBie handover | Plugin | Native | None | ReportDAO.cobieExport | P1 |
| Regulatory templates | 8 codes | 3 codes | 1 code (MY) | 6+ codes | P1 |
| 500K+ elements | Yes | Yes | 48K proven | ST-1 stress test | P1 |
| Version comparison | Yes | Yes | W_Variant | Visual diff report | P2 |
| Drawing export (2D) | Yes | Yes | Stub only | Phase C SVG pipeline | P2 |

### §3.2 Moat Deepening Strategy

**Existing moats (from StrategicIndustryPositioning.md):**
1. IFC-native compilation (not export)
2. DB/ERP integration (Spatial MRP)
3. LLM development velocity
4. Symbolic inference (proof trees)

**New moats to build:**

#### Moat 5: Infrastructure First-Mover

No commercial BIM tool validates infrastructure IFC4X3 with parametric rules.
We have 30 mined rules from real bridge/road/rail extractions. Extending to
tunnel, dam, and port infrastructure gives us a rule library that Solibri
cannot match without the same extraction-mining pipeline.

**Action:** Scale research (§1) targets 10+ infrastructure models by Q3 2026.
Each yields 5-15 rules. Target: 150+ infrastructure validation rules.

#### Moat 6: Calibration-Proven Rules

CalibrationTest proves rules against 48K-element ground truth. No competitor
validates rules against extraction oracles. Every new Rosetta Stone extends
the calibration corpus. A rule that passes calibration is worth more than
a rule that passes specification review.

**Action:** Each new model becomes a calibration oracle. CalibrationTest
generalises from TE-specific to model-agnostic (accept any `_extracted.db`).

#### Moat 7: Verb Grammar as Knowledge Capture

63 verbs encode construction patterns as executable code. Each verb captures
engineering knowledge that took decades to formalise. TILE captures sleeper
spacing. ROUTE captures pipe runs. CLUSTER captures equipment groups.
New verbs (ZONE, STACK, BORE, TAPER) capture domain-specific patterns.

**Action:** Verb discovery from scale research (§1.5). Each new verb is a
patent-grade knowledge capture that competitors cannot replicate without
the same extraction → factorization → witness pipeline.

#### Moat 8: Multi-Client Report Distribution

The ReportDAO abstraction (§2.4) allows the same compiled data to reach:
- Construction managers (Kanban)
- Executives (BSC)
- Regulators (compliance templates)
- Public stakeholders (social media)
- Facility managers (COBie)
- Architects (BCF issues)

No competitor serves all six audiences from a single compilation. Revit
exports to Navisworks for clash, separately to Cost for QTO, separately
to Maximo for FM. We serve all from one `C_Order`.

### §3.3 IfcOpenShell Federation Integration

The Federation addon (50+ Python files) has proven capabilities as hardcoded
scripts. Integration strategy: Java DAO reads the same SQLite schemas that
the Python PoCs write.

| Federation PoC | Java DAO | Status |
|---------------|----------|--------|
| `fast_bbox_loader.py` | PlacementLoader (existing) | DONE — same spatial index |
| `boq/` BOQ calculator | ReportDAO.costBreakdown | Schema match needed |
| `tandem/asset_registry.py` | ReportDAO.assetRegister | Schema match needed |
| `tandem/maintenance_manager.py` | ReportDAO.maintenanceSchedule | Schema match needed |
| `tandem/sensor_installation.py` | AD_Val_Rule sensor rules | DV006b pattern — mine + migrate |
| `semantic_utils.py` | ClassifyDAOImpl (existing) | DONE — same discipline map |
| `clash/` spatial intersection | SpatialPredicates (existing) | Needs hard clash impl |

**Principle:** Python scripts are PoC. Java DAOs are production. The database
schema is the contract between them. IfcOpenShell writes, Java reads.

---

## §4 Compliance & Regulatory Framework

### §4.1 Jurisdiction Expansion

Current: MY (Malaysia UBBL) — 30 researched rules.

| Jurisdiction | Code | Priority | Rule Source |
|-------------|------|----------|-------------|
| MY | UBBL 1984 (amended 2021) | DONE | 30 rules in validation.db |
| SG | Building Control Act / SS CP5 | P1 | Similar tropical climate |
| UK | Building Regulations 2010 | P1 | Largest openBIM mandate market |
| AU | NCC/BCA 2024 | P2 | Performance-based, good test case |
| US | IBC 2024 / NFPA | P2 | Largest BIM market by value |
| EU | Eurocodes (EN 1990-1999) | P3 | Structural focus, 27 countries |
| HK | BD Buildings Ordinance | P3 | High-rise specialist market |

**Rule template:** Each jurisdiction contributes ~30 rules to `AD_Val_Rule`
with `jurisdiction` column. Total target: 200+ jurisdiction-specific rules.

### §4.2 Compliance Report Verbs

```
REPORT COMPLIANCE <building_id> JURISDICTION <code>
  → ComplianceReport { rules[], pass_count, fail_count, citations[] }

REPORT COMPLIANCE_MATRIX <portfolio_id>
  → Matrix { buildings × jurisdictions × verdicts }

REPORT REGULATORY_SUBMISSION <building_id> JURISDICTION <code> TEMPLATE <format>
  → PDF/XLSX with jurisdiction-specific sections, rule citations, evidence
```

### §4.3 Verb-Cited Evidence

Every compliance report line includes:

```
Rule:     UBBL_BEDROOM_MIN_AREA (AD_Val_Rule 101)
Verdict:  PASS
Evidence: m_bom_line 'TE_GF_BEDROOM_01' → AABB 3100×3100mm → 9.61m²
Citation: UBBL Schedule 3, Table 1: Min bedroom area 9.2m²
Verb:     // Implementing BBC.md §3.2 — Witness: W-CAL-RULES-EXIST
```

---

## §5 Schema Extensions

### §5.1 New Columns (future migrations)

```sql
-- V010: Sustainability columns on M_Product
ALTER TABLE M_Product ADD COLUMN carbon_kg_per_unit REAL DEFAULT 0;
ALTER TABLE M_Product ADD COLUMN recyclability TEXT DEFAULT 'UNKNOWN';
ALTER TABLE M_Product ADD COLUMN eol_strategy TEXT DEFAULT 'LANDFILL';
ALTER TABLE M_Product ADD COLUMN lifespan_years INTEGER DEFAULT 50;
ALTER TABLE M_Product ADD COLUMN maintenance_interval_months INTEGER DEFAULT 12;

-- V011: Facility type on AD_Val_Rule (infrastructure scoping)
ALTER TABLE AD_Val_Rule ADD COLUMN facility_type TEXT;
-- Values: BUILDING, BRIDGE, ROAD, RAIL, TUNNEL, DAM, PORT, null=any
UPDATE AD_Val_Rule SET facility_type = 'BRIDGE' WHERE provenance = 'Infra_Bridge';
UPDATE AD_Val_Rule SET facility_type = 'ROAD' WHERE provenance = 'Infra_Road';
UPDATE AD_Val_Rule SET facility_type = 'RAIL' WHERE provenance = 'Infra_Rail';

-- V012: Report configuration
CREATE TABLE IF NOT EXISTS AD_Report_Config (
    report_id TEXT PRIMARY KEY,
    report_name TEXT NOT NULL,
    report_type TEXT NOT NULL,  -- SCHEDULE, COST, CARBON, ASSET, KPI, COMPLIANCE
    template_path TEXT,
    jurisdiction TEXT,
    is_active INTEGER DEFAULT 1
);
```

### §5.2 Database Split Status

| Database | Purpose | Status |
|----------|---------|--------|
| component_library.db | Product catalog + geometries | Stable (616 products, 24K geometries) |
| {PREFIX}_BOM.db | Per-building compiled BOM | Stable (4 buildings) |
| validation.db | AD_Val_Rule + params | Growing (63 rules, 132 params) |
| ERP.db | Discipline metadata (split from component_lib) | Phase 2 in progress |
| work_output.db | Designer save/recall/promote | Stable (G4 schema) |

---

## §6 Implementation Priority

### Phase R (Research) — Scale & Rule Mining

| Step | Task | Deliverable |
|------|------|-------------|
| R-1 | Download 50+ IFC models from public sources | `reference/<type>/` directories |
| R-2 | Batch extraction script | `scripts/batch_extract.sh` |
| R-3 | Extract + classify 20 models | 20 `_extracted.db` + YAML files |
| R-4 | Mine rules from 10 models | DV010-DV020 migrations |
| R-5 | Scale stress test ST-1 through ST-7 | ScaleStressTest.java |
| R-6 | Verb discovery report | New verb candidates documented |

### Phase RE (Report Engine) — DAO + Adapters

| Step | Task | Deliverable |
|------|------|-------------|
| RE-1 | ReportDAO interface + impl | ReportDAOImpl.java |
| RE-2 | 5D cost breakdown (first report) | R-5D-01 working with test |
| RE-3 | Compliance matrix report | R-4.2 working with MY rules |
| RE-4 | COBie handover export | 19-sheet XLSX from BOM data |
| RE-5 | JSON API adapter | REST endpoint for dashboard clients |
| RE-6 | Kanban/KPI/BSC templates | JSON schemas for each client type |
| RE-7 | Social media adapter | Slack webhook + LinkedIn post |
| RE-8 | BCF 3.0 issue export | XML from AD_Val_Rule results |

### Phase J (Jurisdiction) — Regulatory Expansion

| Step | Task | Deliverable |
|------|------|-------------|
| J-1 | SG CP5 rules (30 rules) | DV030_sg_cp5.sql + test |
| J-2 | UK Building Regs (30 rules) | DV031_uk_bregs.sql + test |
| J-3 | Regulatory template engine | PDF generator with citations |
| J-4 | Multi-jurisdiction comparison | Matrix report across codes |

---

## §7 Success Metrics

| Metric | Current | 6-month Target | 12-month Target |
|--------|---------|----------------|-----------------|
| Rosetta Stone buildings | 4 | 15 | 30 |
| Reference models extracted | 14 | 50 | 100 |
| Validation rules | 63 | 200 | 500 |
| Jurisdictions | 1 (MY) | 3 (MY/SG/UK) | 6 |
| BIM verbs | 63 | 75 | 100 |
| Max element count proven | 48K | 500K | 1M |
| Report types | 3 (existing) | 12 | 20 |
| Client integrations | 0 | 3 (Slack/Kanban/PDF) | 8 |
| Infrastructure types | 3 (BR/RD/RL) | 6 | 10 |
| Test witnesses | 166 | 250 | 400 |
| Industry compliance score | 23/30 | 27/30 | 29/30 |

---

## §8 References

| Document | Covers |
|----------|--------|
| `BIM_Designer.md` | UI specification (all presentation-layer details) |
| `StrategicIndustryPositioning.md` | Market analysis, competitive matrix, moat thesis |
| `BOMBasedCompilation.md` | Master spec: tack, walker, verb grammar, pipeline |
| `SystemContract.md` | iDempiere table mapping, C_Order model |
| `DocValidate.md` | Validation architecture, AD_Val_Rule |
| `InfrastructureAnalysis.md` | Infrastructure IFC4X3, segment mapping |
| `CALIBRATION_SRS.md` | Extraction oracle vs rule prediction |
| `ACTION_ROADMAP.md` | Phase 0-H implementation plan |

---

*CORE_SRS.md v1.0 — BIM Intent Compiler Platform Specification*
*Phases R (Research), RE (Report Engine), J (Jurisdiction) extend ACTION_ROADMAP.md*
