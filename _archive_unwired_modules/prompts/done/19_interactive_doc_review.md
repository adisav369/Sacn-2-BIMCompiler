# DONE — S82 Doc Review — Full Scan Results
> Commit: e7ed9792 [S82]

### Scan scope: all ~40 active docs in docs/

### CLOSED (no action needed)
- disc_validation.db refs — all historical context (DATA_MODEL, AUDIT). CLEAN
- SystemContract.md links — fixed in S81 commit 409cb23. CLEAN
- CO_EmptySpaceLine — cleaned in S73-S74. Only in AUDIT (historical). CLEAN
- DocBaseType — all refs are migration/historical context. CLEAN
- ConstructionAsERP — ConstructionAsERPII.txt exists, links valid. CLEAN
- DISC_VALIDATION_DB_SRS.md title — already reads "ERP.db SRS". CLOSED
- Number consistency (verbs=64, buildings=35, ALL GREEN=19, tests=408, products=2475) — CLEAN

### FIX NOW (this session)
1. [x] 5-database → 4-database (7 lines, 4 files: StrategicIndustryPositioning, DocValidate, BOMBasedCompilation, BIM_Designer_SRS) — DONE
2. [x] SQL bom_category column refs where column is now m_product_category_id (selective: SYSTEMS_INSTALLER_GUIDE, TIER1_SRS, G4_SRS) — DONE
3. [x] AUDIT §U.3.4 — mark CLOSED (title already fixed) — DONE

### DEFERRED (tracked in AUDIT §U.3.2)
- work_output.db refs (~60 lines, ~20 files) — full session task. Many are in unbuilt feature specs (G4_SRS, BIM_Designer_SRS). Rewrite when features are implemented, not before.

### NOT STALE (leave alone)
- YAML bom_category: key — matches live scripts/construction_manifest.yaml
- bom_category in DocValidate SQL fixtures, BIM_COBOL examples, CompilationAudit, Analysis files — test data, language examples, or historical audit trails
