# BIM Intent Compiler — Documentation Index

Single-page entry point. All active docs by tier.

---

## T0 Governing — Read First

| Doc | What |
|-----|------|
| [MANIFESTO.md](MANIFESTO.md) | **READ FIRST:** The ERP world view — why construction is manufacturing, iDempiere pattern, three concerns |
| [ProjectOrderBlueprint.md](ProjectOrderBlueprint.md) | FRONTIER: §1-§14 future features, §2.1 CTFL test plan, §2.2 site layout, §14 implementation sessions |

## Last Mile — Proof & Gap Closure

| Doc | What |
|-----|------|
| [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) | R1-R30 gap tracking, session checklist — the distance between "it compiles" and "it ships" |
| [TestArchitecture.md](TestArchitecture.md) | G1-G6 gates, tamper seal, traceability matrix, 35 Rosetta Stones |
| [ShipYard.md](ShipYard.md) | **Domain-agnostic treatise:** marine hulls, tunnels, earthworks, industrial plant — same engine, different data |

## T1 Foundation — Master References

| Doc | What |
|-----|------|
| [BOMBasedCompilation.md](BOMBasedCompilation.md) | MASTER SPEC: tack, walker, BUFFER, gospel |
| [DATA_MODEL.md](DATA_MODEL.md) | Schema reference, tack columns, 5-DB architecture |
| [BIM_COBOL.md](BIM_COBOL.md) | Verb grammar, 75 verbs, TILE/CLUSTER/ROUTE/FRAME |
| [ACTION_ROADMAP.md](ACTION_ROADMAP.md) | Navigation hub: "I need to..." → spec pointer. Known debt. Go-to-market |
| [SourceCodeGuide.md](SourceCodeGuide.md) | Code navigation, entry points, DAO patterns, glossary |

## T2 SRS — Requirement Specifications

| Doc | What |
|-----|------|
| [BIM_Designer_SRS.md](BIM_Designer_SRS.md) | UX requirements (50 numbered), user journeys, state machine |
| [G4_SRS.md](G4_SRS.md) | output.db (compile DB), master-detail DocStatus, AP gate |
| [DocAction_SRS.md](DocAction_SRS.md) | processIt() lifecycle (DR→IP→CO→AP), discipline routing |
| [DocValidate.md](DocValidate.md) | **Spatial + regulatory rule symbiosis** (§0), AD_Val_Rule, 3-tier validation, jurisdiction packs (9 countries), mining pipeline |
| [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) | Multi-discipline BOM tree, LOD resolution, handlers H1-H6 |
| [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) | G-7 SRS: layer-by-layer TACK, U-value calc, 17 witnesses |
| [CALIBRATION_SRS.md](CALIBRATION_SRS.md) | DocEvent generic vs Terminal, density/spacing comparison |
| [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) | ERP.db: CalibrationDAO, IFC class map |
| [INFRA_DESIGNER_SRS.md](INFRA_DESIGNER_SRS.md) | Infra Designer: terrain, alignment, 5 phases I-1..I-5 |
| [TIER1_SRS.md](TIER1_SRS.md) | 6D carbon, 7D FM, audit trail, 3D native |
| [BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md) | BackOffice HTTP server, SessionManager, portfolio |
| [INSTALLER_SPEC.md](INSTALLER_SPEC.md) | Installation and deployment specification |
| [EYES_SRS.md](EYES_SRS.md) | BIMEyes geometric comprehension engine, 26 proofs, shape/compare/diff |
| [GENERATIVE_HOUSE_SRS.md](GENERATIVE_HOUSE_SRS.md) | Generative compilation: BOM explosion, selection cascade, DemoHouse |

## T3 Analysis — Rosetta Stone Guardrails

| Doc | What |
|-----|------|
| [SampleHouseAnalysis.md](SampleHouseAnalysis.md) | SH guardrails, 55 elements, hello-world proof |
| [DuplexAnalysis.md](DuplexAnalysis.md) | DX mirror algorithm, partition, MEP symmetry |
| [TerminalAnalysis.md](TerminalAnalysis.md) | 48K-element TE, verb factorization, guardrails |
| [FZKHausAnalysis.md](FZKHausAnalysis.md) | FK (FZK Haus) 82-element residential |
| [ACInstituteAnalysis.md](ACInstituteAnalysis.md) | IN (AC Institute) 699-element institutional |
| [DemoHouseAnalysis.md](DemoHouseAnalysis.md) | DM guardrails: 3-OrderLine compilation, SH base + FK roof + FP discipline |
| [InfrastructureAnalysis.md](InfrastructureAnalysis.md) | Infrastructure IFC4X3, FACILITY/SEGMENT mapping |
| [TE_MINING_RESULTS.md](TE_MINING_RESULTS.md) | M1/M4/M5/M6/M12 distributions (V004 seed data) |

## T4 Guides — User-Facing

| Doc | What |
|-----|------|
| [BIM_Designer_UserGuide.md](BIM_Designer_UserGuide.md) | BIM Designer GUI user guide |
| [BackOfficeUserGuide.md](BackOfficeUserGuide.md) | BackOffice user guide |
| [USER_GUIDE.md](USER_GUIDE.md) | General user guide |
| [SYSTEMS_INSTALLER_GUIDE.md](SYSTEMS_INSTALLER_GUIDE.md) | Full platform setup for sysadmins and developers |

## Standalone — Market / Academic

| Doc | What |
|-----|------|
| [BIMERPPaper.md](BIMERPPaper.md) | Academic paper: BIM as ERP |
| [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) | Market positioning, 4 moats, IFC scorecard |
| [BIM_Compiler_Market_Impact_Report.pdf](BIM_Compiler_Market_Impact_Report.pdf) | Market impact: USD 10B BIM market, MY mandate, go-to-market timeline, risk assessment |
| [TheRosettaStoneStrategy.md](TheRosettaStoneStrategy.md) | Why real buildings are ground truth: deterministic proofs, no AI in gates |

## Companion Projects — Spatial Extensions

| Doc | What |
|-----|------|
| [Enterprise.md](Enterprise.md) | **FederatedModel Enterprise Platform**: nD dimensions (4D–8D), NLP queries, Color Studio, River IoT, HTML UI |
| [PDF_TERRAIN.md](PDF_TERRAIN.md) | Survey to 3D terrain: PDF → elevation points → IFC. Site topology for plot placement |
| [2D_LAYOUT.md](2D_LAYOUT.md) | Architectural drawings from compiled BOM: floor plans, elevations, sections as SVG |

## Operational — Reference

| Doc | What |
|-----|------|
| [BIMLogger.md](BIMLogger.md) | Levelled pipeline logging spec, grep patterns |
| [WorkOrderGuide.md](WorkOrderGuide.md) | Pipeline config, discipline mapping |
| [BlenderBridge.md](BlenderBridge.md) | Java-smart/Python-dumb pipe |
| [TACK_FIX_SPEC.md](TACK_FIX_SPEC.md) | FIX-1/2/3 method specs, pipeline coordination |
| [PREFAB_ARCHITECTURE.md](PREFAB_ARCHITECTURE.md) | 6-level assembly hierarchy, MRP BOM drop |
| [VIEW_CONTRACTS.md](VIEW_CONTRACTS.md) | Data access layer, 6 SQL views |
| [BIM_Designer.md](BIM_Designer.md) | GUI, ASI, 4-action persistence, Design Mode |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Deployment procedures |
| [AUDIT_S51_FOCUSED.md](AUDIT_S51_FOCUSED.md) | S30–S50 audit: geometry, migrations, test integrity, security, API |
| [ID_NAME_VALUE_STUDY.md](ID_NAME_VALUE_STUDY.md) | iDempiere _ID/Name/Value column convention impact study |
| [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) | Self-service IFC onboarding: 8-step pipeline, template generator |

## Database — Schema & ERDs (`database/`)

| Doc | What |
|-----|------|
| [DATABASE_SCHEMA.md](https://github.com/red1oon/BIMCompiler/blob/master/database/DATABASE_SCHEMA.md) | Full table inventory: purpose, Java access, review status |
| [bim_architecture_viz.html](https://github.com/red1oon/BIMCompiler/blob/master/database/bim_architecture_viz.html) | Interactive ERD: clickable 4-DB tables, compilation pipeline, BOM tree |

## Archived

Superseded docs live in `docs/archive/`. Key archived docs:

| Doc | Superseded by |
|-----|---------------|
| `DEVELOPER_GUIDE.md` | [SourceCodeGuide.md](SourceCodeGuide.md) (DAO patterns merged) |
| `BIMasBOMConcept.md` | [BBC.md](BOMBasedCompilation.md) §1 (dimension model merged) |
| `MANIFESTO.md` | Content distributed: entity registry → [DATA_MODEL.md](DATA_MODEL.md), three concerns → [MANIFESTO.md](MANIFESTO.md), gaps → [ACTION_ROADMAP.md](ACTION_ROADMAP.md) |
| `ConstructionAsERP.md` | [MANIFESTO.md](MANIFESTO.md) §2 (entity registry) + [BBC.md](BOMBasedCompilation.md) §1 (iDempiere mapping) + [DATA_MODEL.md](DATA_MODEL.md) §1 (4-DB) |
| `VerbPatternArchitecture.md` | [BIM_COBOL.md](BIM_COBOL.md) §19 (verb detection, formats, results merged) |
