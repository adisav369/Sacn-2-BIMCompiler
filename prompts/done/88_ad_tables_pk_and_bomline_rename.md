# DONE
# Phase C — AD Tables INTEGER PK + bom_child_id Rename

**Priority:** Final phase of iDempiere PK conformance. Add surrogate
`_ID INTEGER PK AUTOINCREMENT` to 13 AD tables in ERP.db, and rename
`bom_child_id` → `M_BOM_Line_ID` in BOM.db. Low risk — AD tables are
read-only metadata, and the rename is cosmetic.

**Prerequisite:** Phase A (m_bom, prompt 86) and Phase B
(M_Product_Category, prompt 87) are DONE and committed.

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** Schema migration following the iDempiere
convention established in Phase A/B. Same pattern, final tables.

## Read first

1. `CLAUDE.md` + `PROGRESS.md` — current state
2. `prompts/86_idempiere_pk_conformance.md` §Understanding — the convention
3. `docs/DATA_MODEL.md` — iDempiere PK Convention section (updated Phase B)
4. `library/ERP.db` — run `.schema ad_space_type` etc. to see current schemas
5. `IFCtoBOM/src/main/java/com/bim/ifctobom/IFCtoBOMPipeline.java` — BOM.db DDL
   for `m_bom_line` (has `bom_child_id`)

## Understanding: What Changes, What Doesn't

### AD tables: add surrogate _ID, keep TEXT natural keys

AD tables are **lookup/reference data**. Their TEXT PKs are meaningful
natural keys (room type names, IFC classes, hazard classes). Unlike
M_Product_Category where `'RE'` was an opaque code, these TEXT values
ARE the business meaning.

The iDempiere convention still applies: add `_ID INTEGER PK AUTO` as
the surrogate, move the old TEXT PK to `Value`. But most Java code will
keep using `WHERE Value = ?` (or the original column name) because the
TEXT value is how these tables are queried.

### bom_child_id → M_BOM_Line_ID: column rename only

`bom_child_id` is already `INTEGER PRIMARY KEY AUTOINCREMENT`. It just
needs renaming to follow the `TableName_ID` convention. No type change.

## Part 1: AD Tables Migration (DV028)

### Table inventory — 3 tiers by complexity

**Tier 1 — Simple TEXT PK (8 tables, straightforward):**

| Table | Current PK | Rows | Java refs |
|-------|-----------|------|-----------|
| `ad_element_mep` | `element_type TEXT` | 12 | 10 |
| `ad_fp_coverage` | `hazard_class TEXT` | 4 | 19 |
| `ad_fp_trigger` | `trigger_id TEXT` | 12 | 14 |
| `ad_ifc_class_map` | `ifc_class TEXT` | 47 | 0 (SQL only) |
| `ad_space_dim` | `space_type TEXT` | 37 | 14 |
| `ad_space_exterior_rule` | `space_type_id TEXT` | 24 | 3 |
| `ad_space_type` | `space_type_id TEXT` | 41 | 106 |
| `ad_space_type_mep` | `space_type_id TEXT` | 22 | 50 |

Pattern: CREATE new table with `_ID INTEGER PK AUTO` + `Value TEXT UNIQUE`,
copy data, drop old, rename. Old TEXT PK column becomes `Value`.

**Tier 2 — Composite PK (4 tables, add surrogate):**

| Table | Current PK | Rows | Java refs |
|-------|-----------|------|-----------|
| `ad_code_requirement` | `(code_id, clause, element_type, space_type)` | 23 | 4 |
| `ad_space_adjacency` | `(space_type_a, space_type_b)` | 22 | 0 |
| `ad_space_type_mep_bom` | `(space_type_id, mep_product_id)` | 186 | 40 |
| `ad_space_type_opening` | `(space_type_id, opening_role, family_id)` | 103 | 12 |

Pattern: Add `_ID INTEGER PK AUTO` as surrogate. Keep composite columns
as `UNIQUE` constraint. Composite is still the logical key — the
surrogate is for FK consistency only.

**Tier 3 — AD_SysConfig (iDempiere standard, special case):**

| Table | Current PK | Rows | Java refs |
|-------|-----------|------|-----------|
| `AD_SysConfig` (ERP.db) | `Name TEXT` | 8 | 2 |
| `ad_sysconfig` (BOM.db) | `id INTEGER` (already!) | varies | 8 |

ERP.db `AD_SysConfig`: Add `AD_SysConfig_ID INTEGER PK AUTO`, `Name`
stays as `Value`. BOM.db `ad_sysconfig`: rename `id` → `AD_SysConfig_ID`,
add `Value` + `Name` columns.

### Migration SQL

Create `migration/DV028_ad_integer_pk.sql`. One migration, all 13 tables.
For each table:

```sql
-- Example: ad_space_type (Tier 1)
CREATE TABLE ad_space_type_new (
    AD_Space_Type_ID  INTEGER PRIMARY KEY AUTOINCREMENT,
    Value             TEXT NOT NULL UNIQUE,  -- was space_type_id
    -- ... all other columns unchanged ...
);
INSERT INTO ad_space_type_new (Value, ...)
SELECT space_type_id, ... FROM ad_space_type;
DROP TABLE ad_space_type;
ALTER TABLE ad_space_type_new RENAME TO ad_space_type;
```

```sql
-- Example: ad_space_type_mep_bom (Tier 2 — composite)
CREATE TABLE ad_space_type_mep_bom_new (
    AD_Space_Type_MEP_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    space_type_id  TEXT NOT NULL,
    mep_product_id TEXT NOT NULL,
    -- ... all other columns ...
    UNIQUE (space_type_id, mep_product_id)
);
INSERT INTO ad_space_type_mep_bom_new (space_type_id, mep_product_id, ...)
SELECT space_type_id, mep_product_id, ... FROM ad_space_type_mep_bom;
DROP TABLE ad_space_type_mep_bom;
ALTER TABLE ad_space_type_mep_bom_new RENAME TO ad_space_type_mep_bom;
```

### Java changes

Most AD table access is read-only SQL with `WHERE space_type_id = ?` or
similar. These queries work unchanged — the TEXT column still exists
(as `Value` or kept as original name in composite tables).

**What must change:**
- Any `INSERT` that uses the old TEXT column as PK
- Any FK reference to an AD table's old TEXT PK
- Column name changes: `space_type_id` → `Value` in simple PK tables

**What likely stays the same:**
- `WHERE space_type_id = ?` queries in Tier 2 tables (column name unchanged)
- Read-only lookups that don't reference the PK column by name

**Check each file group:**
```bash
# Highest impact — check these first:
grep -rn "ad_space_type\b" --include="*.java" | grep -v target | grep -v worktree | grep -v test
grep -rn "ad_space_type_mep\b" --include="*.java" | grep -v target | grep -v worktree | grep -v test
grep -rn "ad_fp_coverage\|ad_fp_trigger" --include="*.java" | grep -v target | grep -v worktree | grep -v test
```

## Part 2: bom_child_id → M_BOM_Line_ID Rename

### Scope

- **55 non-test Java refs** across 7 modules
- **38 test refs** across 11 test files
- **IFCtoBOM DDL** in IFCtoBOMPipeline.java (line 583-584)
- **BOM.db tables**: `m_bom_line.bom_child_id` → `M_BOM_Line_ID`
- **m_bom_line_ma**: if it references bom_child_id as FK, update

### Migration

No separate migration file needed — the IFCtoBOM DDL creates BOM.db
tables fresh each extraction. Update the DDL, then re-extract.

For ERP.db: if `M_BOM_Line` exists there (DV025 shared recipes),
check its PK column name too.

### Java changes

Global rename: `bom_child_id` → `M_BOM_Line_ID` in:
- Column name strings in SQL
- `X_M_BOMLine.java` constant + accessor
- All files that reference the column in queries

**Pattern from Phase A:** Same as `bom_id` → `Value`. The column is
already INTEGER PK AUTO — just the name changes.

## Execution Order

1. DV028 migration on ERP.db (all 13 AD tables)
2. Java changes for AD tables
3. `mvn compile -q` — verify
4. IFCtoBOM DDL: `bom_child_id` → `M_BOM_Line_ID`
5. Java rename: `bom_child_id` → `M_BOM_Line_ID` (55+38 refs)
6. `mvn compile -q` — verify
7. Delete all `library/*_BOM.db` — re-extract (DDL changed)
8. Full verification

## Verify

**After AD tables (Part 1):**
```bash
mvn compile -q
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
```

**After rename + re-extract (Part 2):**
```bash
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
./scripts/run_RosettaStones.sh classify_fk.yaml   # FK 7/7
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN
bash scripts/verify_test_seal.sh
```

Full fleet run is NOT required for Phase C — AD tables are read-only
metadata (no FK cascade risk like Phase B), and the rename is cosmetic.
SH + FK + TE is sufficient.

## What NOT to do

- Do NOT modify existing migration files (sacred — append only)
- Do NOT remove the original composite columns in Tier 2 tables —
  they stay as UNIQUE constraint, the surrogate _ID is additive
- Do NOT change AD_Org (already INTEGER PK, already compliant)
- Do NOT change tables already migrated (M_Product, C_Order, C_DocType,
  M_Product_Category, m_bom)
- Do NOT change query semantics — `WHERE space_type_id = 'LIVING'`
  stays the same, just the PK is now a surrogate _ID

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- DV028 result: which tables migrated, row counts
- Java files changed (AD tables vs rename — separate counts)
- Which AD table queries needed updating vs stayed unchanged
- bom_child_id rename: files changed count
- Gate results: SH, FK, TE
- Any TEXT PK references remaining in AD tables
- Re-extraction confirmation

---

## Findings (S100-p88)

### DV028 Migration: 13 AD tables migrated

| Table | Tier | Rows | Old PK → New |
|-------|------|------|-------------|
| ad_space_type | 1 | 41 | space_type_id TEXT → AD_Space_Type_ID INTEGER + Value |
| ad_element_mep | 1 | 12 | element_type TEXT → AD_Element_MEP_ID INTEGER + Value |
| ad_fp_coverage | 1 | 4 | hazard_class TEXT → AD_FP_Coverage_ID INTEGER + Value |
| ad_fp_trigger | 1 | 12 | trigger_id TEXT → AD_FP_Trigger_ID INTEGER + Value |
| ad_ifc_class_map | 1 | 47 | ifc_class TEXT → AD_IFC_Class_Map_ID INTEGER + Value |
| ad_space_dim | 1 | 37 | space_type TEXT → AD_Space_Dim_ID INTEGER + Value |
| ad_space_exterior_rule | 1 | 24 | space_type_id TEXT → AD_Space_Exterior_Rule_ID INTEGER + Value |
| ad_space_type_mep | 1 | 22 | space_type_id TEXT → AD_Space_Type_MEP_ID INTEGER + Value |
| ad_code_requirement | 2 | 23 | composite → AD_Code_Requirement_ID INTEGER + UNIQUE |
| ad_space_adjacency | 2 | 22 | composite → AD_Space_Adjacency_ID INTEGER + UNIQUE |
| ad_space_type_mep_bom | 2 | 186 | composite → AD_Space_Type_MEP_BOM_ID INTEGER + UNIQUE |
| ad_space_type_opening | 2 | 103 | composite → AD_Space_Type_Opening_ID INTEGER + UNIQUE |
| AD_SysConfig (ERP.db) | 3 | 8 | Name TEXT → AD_SysConfig_ID INTEGER + Value + Name kept |

### Java changes for AD tables (Part 1): 11 files

**Production code (7 files):**
- MEPAD.java — element_type→Value, hazard_class→Value (SQL + ResultSet)
- ADSession.java — hazard_class→Value (SQL + ResultSet)
- SpaceTypeAD.java — space_type_id→Value in SELECT, JOIN, WHERE
- ExteriorRuleAD.java — space_type_id→Value (SELECT + rs.getString)
- SpaceDimResolver.java — space_type→Value (SELECT + rs.getString)
- FireProtectionAD.java — trigger_id→Value (SELECT + rs.getString)
- StandardsResolver.java — trigger_id→Value (SELECT)
- WireLightingVerb.java — space_type_id→Value (WHERE clause)

**Test code (4 files):**
- DiscValidationDBTest.java — element_type→Value, space_type_id→Value in subqueries
- MetadataIntegrityTest.java — st.space_type_id→st.Value in JOINs, satellite array updated
- RosettaStoneTest.java — space_type_id→Value (WHERE clause)

**Queries unchanged (Tier 2 composite columns kept):**
- ad_space_type_mep_bom: space_type_id, mep_product_id unchanged
- ad_space_type_opening: space_type_id, opening_role, family_id unchanged
- ad_code_requirement: code_id, clause, element_type, space_type unchanged
- ad_space_adjacency: space_type_a, space_type_b unchanged

### bom_child_id → M_BOM_Line_ID rename (Part 2): 39 files

- IFCtoBOMPipeline.java DDL updated (m_bom_line PK column)
- X_M_BOMLine.java: COLUMNNAME_bom_child_id→COLUMNNAME_M_BOM_Line_ID, getBomChildId→getBomLineId
- X_M_Attribute.java: COLUMNNAME_bom_child_id→COLUMNNAME_M_BOM_Line_ID
- MAttribute.java, Filler.java, BuildingInspector.java, DesignerDAO.java
- BomDropper.java, CompilationPipeline.java, BOMWalker.java, OrderLineWalker.java
- BOMTreeLoader.java, QualifiedBom.java, BuildingWriter.java, MetadataValidator.java
- BOMRuleAD.java, PlacementLoader.java, FillBuffersVerb.java
- 7 BIM_COBOL verbs (AddLine, CheckBom, SelectBom, SetDimensions, SetRotation, SetTack, FillBuffers)
- 11 test files across DAGCompiler + BonsaiBIMDesigner + ORMSandbox
- audit_integrity.sh, schema_snapshot_bom.sql
- BOM.db ad_sysconfig DDL: id→AD_SysConfig_ID, Name+Value columns added

### Gate results
- SH 7/7 PASS
- FK 7/7 PASS
- TE 6/7 PASS + 1 WARN (C9 pre-existing axis swap)
- Seal v9 (1be55364)

### TEXT PK references remaining in AD tables: NONE
All 13 AD tables now have INTEGER PK AUTOINCREMENT. Old TEXT PKs moved to Value (Tier 1) or UNIQUE constraint preserved (Tier 2).

### Re-extraction: CONFIRMED
All 35 BOM.db files deleted and re-extracted. SH_BOM.db verified: M_BOM_Line_ID column present in m_bom_line DDL.

### Sacred files NOT modified
- migration/seed_dm_bom.sql — still references bom_child_id (DM generative, separate lifecycle)
- migration/S60_schema.sql — still references bom_child_id (output.db historical schema)
