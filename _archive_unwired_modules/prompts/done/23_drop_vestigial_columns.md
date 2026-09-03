# DONE — Pre-flight FAILED: doc_sub_type is structural, not vestigial
> Commit: (no code change — findings superseded by prompt 25/S84)

Schema cleanup: drop vestigial TEXT columns replaced by AD_Org_ID (S78)
and M_Product_Category routing (S77). These columns are @Deprecated and
no longer read in the compile path.

Spec: SpecsAnalysis.txt §10 steps 5-6, DATA_MODEL.md §7.6

## Pre-flight

Before touching code, verify these columns are truly unused in the compile path:
1. `grep -rn "bom_category" src/main/java/ --include='*.java'` — expect only @Deprecated accessors and comments
2. `grep -rn "doc_base_type\|doc_sub_type\|DocBaseType\|DocSubType" src/main/java/ --include='*.java'` — expect only @Deprecated accessors
3. `grep -rn "bom_category" src/test/java/ --include='*.java'` — check test usage (may need updating)

If any non-deprecated PRODUCTION read remains, STOP and report. Do not drop.

## Migrations

**W012_drop_bom_category.sql** (per-building BOM.db + output template):
- SQLite cannot DROP COLUMN directly. Use rename-copy-drop pattern:
  CREATE TABLE new WITHOUT the column, INSERT INTO new SELECT (all cols except dropped), DROP old, ALTER TABLE RENAME
- Drop `bom_category` from `m_bom` table
- Drop `bom_category` from `m_bom_line` table (if present)
- Verify: `SELECT * FROM m_bom LIMIT 1` — no bom_category column

**W013_drop_docbasetype.sql** (per-building BOM.db):
- Drop `doc_base_type` from `m_bom` table
- Drop `doc_sub_type` from `m_bom` table
- Drop `doc_base_type` from `c_doctype` table (if present)
- Verify: `SELECT * FROM m_bom LIMIT 1` — no doc_base_type/doc_sub_type columns

## Java cleanup

- `X_M_BOM.java`: remove @Deprecated getBomCategory/setBomCategory, getDocBaseType/setDocBaseType, getDocSubType/setDocSubType accessors
- `StructuralBomBuilder.java`: remove bom_category and doc_base_type/doc_sub_type from INSERT SQL (these were kept for backward compat — no longer needed)
- `BuildSpatialStructureVerb.java`: if it still queries `doc_sub_type` (flagged in AUDIT Appendix U §U.2.5), migrate to `m_product_category_id`
- Any other files found in pre-flight grep: update accordingly

## Test cleanup

- Update any test that INSERTs into m_bom with bom_category or doc_base_type columns
- Run `mvn compile -q` and `mvn test-compile -q` after changes
- Run `./scripts/run_RosettaStones.sh classify_sh.yaml` to verify SH still passes

## Witness claims (write BEFORE implementing)

- W-CLEANUP-1: m_bom rows lack bom_category column; AD_Org_ID provides discipline resolution
- W-CLEANUP-2: m_bom rows lack doc_base_type/doc_sub_type; M_Product_Category routing unchanged

## Sacred Files warning

X_M_BOM.java is a Sacred File. Edit with care — remove only the @Deprecated
accessors for dropped columns. Do not touch EntityType guards or GodMode bypass.

Commit separately from any prior uncommitted work.
Commit message prefix: [S84-cleanup].

## Coder Report — Pre-flight FAILED (2026-03-26)

## Verdict: CANNOT DROP. Non-deprecated production reads remain.

### doc_sub_type on m_bom — 6 non-deprecated production reads

1. **MBOM.java:65-68** — `getBuildingBom(conn, docSubType)` queries `WHERE doc_sub_type = ?`
2. **MBOM.java:88-90** — `getByDocSubType(conn, docSubType)` queries `WHERE doc_sub_type = ? OR doc_sub_type IS NULL`
3. **MBOM.java:249-250** — `findByCategory()` queries doc_sub_type conditionally
4. **BomValidator.java:135** — `SELECT m_product_category_id, doc_sub_type FROM m_bom WHERE bom_type = 'BUILDING'`
5. **BomTemplateComposer.java:205,227** — calls `bom.getDocSubType()` (indirect read via MBOM)
6. **BomTemplateContract.java:123** — filters by `getDocSubType()` (indirect read via MBOM)

None of these methods are @Deprecated. MBOM.getBuildingBom() and getByDocSubType() are core ORM queries used by the compile path.

### doc_base_type — write-only (INSERTs in StructuralBomBuilder:190, DisciplineBomBuilder:207)

doc_base_type appears only in INSERT statements, not SELECTs. Could be dropped independently, but doc_sub_type blocks W013 as written.

### DocBaseType/DocSubType on C_DocType — fully active model

X_C_DocType.java, MCDocType.java, PlaceBomVerb.java, PortfolioDAO.java, AllModelsReportGenerator.java all actively query these columns. C_DocType is NOT a candidate for column drops.

### bom_category — YAML field, not m_bom column

ClassificationYaml.java:117,159 reads `bom_category` from YAML config (storey/floor_room config). NewBuildingGenerator.java generates YAML with `bom_category` fields. These are YAML keys, not m_bom column reads. The m_bom.bom_category column itself may be safe to drop (W012), but needs separate verification of whether any INSERT writes it from the YAML value.

### Recommendation

1. **W013 blocked** — doc_sub_type must first be migrated out of MBOM queries. The ORM layer needs a replacement lookup (likely m_product_category_id or a join through C_DocType). This is a code migration, not a column drop.
2. **W012 possibly safe** — bom_category on m_bom may be droppable if no INSERT path still writes it, but should be verified in a separate pre-flight.
3. **C_DocType untouchable** — DocBaseType/DocSubType are structural columns of the document type model, not vestigial.
