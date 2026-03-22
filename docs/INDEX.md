# BIM Intent Compiler — Documentation Index

Single-page entry point. All active docs by tier.

---

## T1 Foundation — Master References

| Doc | What |
|-----|------|
| [BOMBasedCompilation.md](BOMBasedCompilation.md) | MASTER SPEC: tack, walker, BUFFER, gospel |
| [DATA_MODEL.md](DATA_MODEL.md) | Schema reference, tack columns, 4-DB architecture |
| [BIM_COBOL.md](BIM_COBOL.md) | Verb grammar, 63 verbs, TILE/CLUSTER/ROUTE/FRAME |
| [ConstructionAsERP.md](ConstructionAsERP.md) | iDempiere mapping, C_Order model, BOM dimension model (Appendix A) |
| [TestArchitecture.md](TestArchitecture.md) | G1-G6 gates, tamper seal, traceability matrix |
| [ACTION_ROADMAP.md](ACTION_ROADMAP.md) | Phases 0-H, gates G-1..G-12 |
| [SourceCodeGuide.md](SourceCodeGuide.md) | Code navigation, entry points, DAO patterns, glossary |

## T2 SRS — Requirement Specifications

| Doc | What |
|-----|------|
| [BIM_Designer_SRS.md](BIM_Designer_SRS.md) | UX requirements (50 numbered), user journeys, state machine |
| [G4_SRS.md](G4_SRS.md) | work_output.db, master-detail DocStatus, AP gate |
| [DocAction_SRS.md](DocAction_SRS.md) | processIt() lifecycle (DR→IP→CO→AP), discipline routing |
| [DocValidate.md](DocValidate.md) | AD_Val_Rule, 3-tier validation, mining pipeline |
| [DISC_VALIDATE_SRS.md](DISC_VALIDATE_SRS.md) | Multi-discipline BOM tree, LOD resolution, handlers H1-H6 |
| [ASSEMBLY_BUILDER_SRS.md](ASSEMBLY_BUILDER_SRS.md) | G-7 SRS: layer-by-layer TACK, U-value calc, 17 witnesses |
| [CALIBRATION_SRS.md](CALIBRATION_SRS.md) | DocEvent generic vs Terminal, density/spacing comparison |
| [DISC_VALIDATION_DB_SRS.md](DISC_VALIDATION_DB_SRS.md) | disc_validation.db: CalibrationDAO, IFC class map |
| [CORE_SRS.md](CORE_SRS.md) | Scale research, Report Engine 4D-7D, compliance, schema |
| [INFRA_DESIGNER_SRS.md](INFRA_DESIGNER_SRS.md) | Infra Designer: terrain, alignment, 5 phases I-1..I-5 |
| [TIER1_SRS.md](TIER1_SRS.md) | 6D carbon, 7D FM, audit trail, 3D native |
| [BACK_OFFICE_SRS.md](BACK_OFFICE_SRS.md) | BackOffice HTTP server, SessionManager, portfolio |
| [INSTALLER_SPEC.md](INSTALLER_SPEC.md) | Installation and deployment specification |
| [EYES_SRS.md](EYES_SRS.md) | BIMEyes geometric comprehension engine, 26 proofs, shape/compare/diff |

## T3 Analysis — Rosetta Stone Guardrails

| Doc | What |
|-----|------|
| [SampleHouseAnalysis.md](SampleHouseAnalysis.md) | SH guardrails, 55 elements, hello-world proof |
| [DuplexAnalysis.md](DuplexAnalysis.md) | DX mirror algorithm, partition, MEP symmetry |
| [TerminalAnalysis.md](TerminalAnalysis.md) | 48K-element TE, verb factorization, guardrails |
| [FZKHausAnalysis.md](FZKHausAnalysis.md) | FK (FZK Haus) 82-element residential |
| [ACInstituteAnalysis.md](ACInstituteAnalysis.md) | IN (AC Institute) 699-element institutional |
| [InfrastructureAnalysis.md](InfrastructureAnalysis.md) | Infrastructure IFC4X3, FACILITY/SEGMENT mapping |
| [TE_MINING_RESULTS.md](TE_MINING_RESULTS.md) | M1/M4/M5/M6/M12 distributions (V004 seed data) |

## T4 Guides — User-Facing

| Doc | What |
|-----|------|
| [BIM_Designer_UserGuide.md](BIM_Designer_UserGuide.md) | BIM Designer GUI user guide |
| [BackOfficeUserGuide.md](BackOfficeUserGuide.md) | BackOffice user guide |
| [USER_GUIDE.md](USER_GUIDE.md) | General user guide |

## Standalone — Market / Academic

| Doc | What |
|-----|------|
| [BIMERPPaper.md](BIMERPPaper.md) | Academic paper: BIM as ERP |
| [StrategicIndustryPositioning.md](StrategicIndustryPositioning.md) | Market positioning, 4 moats, IFC scorecard |
| [BIM_Compiler_Market_Impact_Report.pdf](BIM_Compiler_Market_Impact_Report.pdf) | Market impact: USD 10B BIM market, MY mandate, go-to-market timeline, risk assessment |

## Operational — Reference

| Doc | What |
|-----|------|
| [BIMLogger.md](BIMLogger.md) | Levelled pipeline logging spec, grep patterns |
| [WorkOrderGuide.md](WorkOrderGuide.md) | Pipeline config, discipline mapping |
| [BlenderBridge.md](BlenderBridge.md) | Java-smart/Python-dumb pipe |
| [LAST_MILE_PROBLEM.md](LAST_MILE_PROBLEM.md) | R1-R30 gap tracking, session checklist |
| [TACK_FIX_SPEC.md](TACK_FIX_SPEC.md) | FIX-1/2/3 method specs, pipeline coordination |
| [PREFAB_ARCHITECTURE.md](PREFAB_ARCHITECTURE.md) | 6-level assembly hierarchy, MRP BOM drop |
| [VIEW_CONTRACTS.md](VIEW_CONTRACTS.md) | Data access layer, 6 SQL views |
| [BIM_Designer.md](BIM_Designer.md) | GUI, ASI, 4-action persistence, Design Mode |
| [DEPLOYMENT.md](DEPLOYMENT.md) | Deployment procedures |
| [IFC_ONBOARDING_RUNBOOK.md](IFC_ONBOARDING_RUNBOOK.md) | Self-service IFC onboarding: 8-step pipeline, template generator |

## Database — Schema & ERDs (`database/`)

| Doc | What |
|-----|------|
| [DATABASE_SCHEMA.md](../database/DATABASE_SCHEMA.md) | Full table inventory: purpose, Java access, review status |
| [bim_architecture_viz.html](../database/bim_architecture_viz.html) | Interactive ERD: clickable 4-DB tables, compilation pipeline, BOM tree |

## Archived

Superseded docs live in `docs/archive/`. Key archived docs:

| Doc | Superseded by |
|-----|---------------|
| `DEVELOPER_GUIDE.md` | [SourceCodeGuide.md](SourceCodeGuide.md) (DAO patterns merged) |
| `BIMasBOMConcept.md` | [ConstructionAsERP.md](ConstructionAsERP.md) Appendix A (dimension model merged) |
| `VerbPatternArchitecture.md` | [BIM_COBOL.md](BIM_COBOL.md) §19 (verb detection, formats, results merged) |
