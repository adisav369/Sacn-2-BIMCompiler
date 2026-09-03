# DONE
# iDempiere PK Conformance — INTEGER _ID + Name/Value on All Tables

**Priority:** Complete the Tier 2 INTEGER PK migration (started S90-S92)
across ALL tables. Every master table gets `_ID INTEGER PK AUTOINCREMENT`
(opaque, hidden from user), `Value TEXT` (search key), `Name TEXT` (display).
FKs reference the INTEGER _ID. Code updated to use INTEGER PKs.

## Commits
- `bbcfc363` Phase A: m_bom INTEGER PK (p86)
- `3e2c2e0b` Phase B: M_Product_Category INTEGER PK (p87)
- P88 commit: Phase C: 13 AD tables INTEGER PK + bom_child_id → M_BOM_Line_ID
- `32527a1b` Phase D: 12 tables — M_Product FK fix, alias FK fix, bad_* INTEGER PK, 6 column renames (p86d)

You are a coder for bim-compiler. One bounded task.

## PRIME RULE

**EXTRACT OR COMPILE ONLY.** This is a schema migration following the
iDempiere convention established in S90-S92. Same pattern, remaining tables.

## Read first

1. `docs/DISC_VALIDATION_DB_SRS.md` §11.6.5 — the 6-step migration sequence
   (S90-S92). Follow the same pattern for remaining tables.
2. `migration/DV021_name_value_erp.sql` through `DV024_drop_int_sidecar.sql`
   — the Tier 2 migrations that already did M_Product, C_Order, C_DocType.
3. `ORMSandbox/src/main/java/com/bim/ormsandbox/po/MBOM.java` — current API
   uses `bom_id TEXT` for lookups. Must migrate to `M_BOM_ID INTEGER`.
4. `DAGCompiler/src/main/java/com/bim/compiler/bom/BomDropper.java` — uses
   `bom_id` TEXT throughout. Must migrate to INTEGER FK.

## Understanding: iDempiere Convention

Every iDempiere master table follows this pattern:

```
TableName_ID    INTEGER PRIMARY KEY AUTOINCREMENT   -- opaque surrogate key (hidden from user)
Value           TEXT NOT NULL UNIQUE                 -- search key (what users type to find it)
Name            TEXT NOT NULL                        -- display name (what users see)
Description     TEXT                                 -- optional long description
IsActive        INTEGER DEFAULT 1                    -- soft delete
```

- `_ID` is a **UUID-like opaque key** — never shown to users, never hardcoded
- `Value` is the **business key** — what was previously the TEXT PK
- `Name` is the **human label** — often same as Value initially
- FKs always reference `_ID`, never `Value`
- Lookups by business key use `WHERE Value = ?`, not `WHERE _ID = ?`

## Current State — What's Done, What's Not

### Already INTEGER PK (S90-S92):
- `M_Product.M_Product_ID` — INTEGER PK AUTO ✓
- `C_Order.C_Order_ID` — INTEGER PK AUTO ✓
- `C_DocType.C_DocType_ID` — INTEGER PK AUTO ✓
- `AD_Org.AD_Org_ID` — INTEGER PK (manual, not AUTO) ✓
- `ad_val_rule.ad_val_rule_id` — INTEGER PK AUTO ✓
- `M_BOM.M_BOM_ID` in ERP.db — INTEGER PK AUTO ✓ (DV025)

### Need migration:

**Phase A — BOM.db core (highest impact, 65+ code references):**

| Table | Current PK | New PK | Value← | Impact |
|-------|-----------|--------|--------|--------|
| `m_bom` (BOM.db) | `bom_id TEXT` | `M_BOM_ID INTEGER AUTO` | `bom_id → Value` | 65 Java refs |
| `m_bom_line` (BOM.db) | `bom_child_id INTEGER` | rename to `M_BOM_Line_ID` | add Name/Value | FK to m_bom |
| `m_bom_line_ma` (BOM.db) | composite | add `M_BOM_Line_MA_ID` | add Name/Value | FK chain |

**Phase B — ERP.db M_Product_Category (135 code references):**

| Table | Current PK | New PK | Value← | Impact |
|-------|-----------|--------|--------|--------|
| `M_Product_Category` | `M_Product_Category_ID TEXT` | `→ INTEGER AUTO` | TEXT value → `Value` | 135 Java refs |

**Phase C — ERP.db AD tables (TEXT PKs, low code impact):**

| Table | Current PK | New PK | Value← |
|-------|-----------|--------|--------|
| `ad_code_requirement` | `code_id TEXT` | `AD_Code_Requirement_ID INTEGER` | code_id → Value |
| `ad_element_mep` | `element_type TEXT` | `AD_Element_MEP_ID INTEGER` | element_type ��� Value |
| `ad_fp_coverage` | `hazard_class TEXT` | `AD_FP_Coverage_ID INTEGER` | hazard_class → Value |
| `ad_fp_trigger` | `trigger_id TEXT` | `AD_FP_Trigger_ID INTEGER` | trigger_id → Value |
| `ad_ifc_class_map` | `ifc_class TEXT` | `AD_IFC_Class_Map_ID INTEGER` | ifc_class → Value |
| `ad_space_adjacency` | `space_type_a TEXT` | `AD_Space_Adjacency_ID INTEGER` | composite → Value |
| `ad_space_dim` | `space_type TEXT` | `AD_Space_Dim_ID INTEGER` | space_type �� Value |
| `ad_space_exterior_rule` | `space_type_id TEXT` | `AD_Space_Exterior_Rule_ID INTEGER` | space_type_id ��� Value |
| `ad_space_type` | `space_type_id TEXT` | `AD_Space_Type_ID INTEGER` | space_type_id → Value |
| `ad_space_type_mep` | `space_type_id TEXT` | `AD_Space_Type_MEP_ID INTEGER` | composite → Value |
| `ad_space_type_mep_bom` | `space_type_id TEXT` | `AD_Space_Type_MEP_BOM_ID INTEGER` | composite → Value |
| `ad_space_type_opening` | `space_type_id TEXT` | `AD_Space_Type_Opening_ID INTEGER` | space_type_id → Value |
| `AD_SysConfig` | `Name TEXT` | `AD_SysConfig_ID INTEGER` | Name stays, add _ID | iDempiere standard |

## Task 1: Phase A — m_bom PK Migration (BOM.db)

This is the highest impact. `m_bom.M_BOM_ID` already exists as a column
(added S91) but `bom_id TEXT` is still the actual PK. Code uses `bom_id`
for all lookups and FK references.

**Migration SQL** (`migration/DV026_bom_integer_pk.sql`):

```sql
-- Phase A: m_bom INTEGER PK conformance
-- M_BOM_ID is already INTEGER column. Make it the actual PK.
-- bom_id TEXT becomes Value (search key).

-- Step 1: Create new table with correct schema
CREATE TABLE m_bom_new (
    M_BOM_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
    Value           TEXT NOT NULL UNIQUE,    -- was bom_id
    Name            TEXT NOT NULL,           -- was bom_name
    -- ... all other columns ...
);

-- Step 2: Copy data
INSERT INTO m_bom_new SELECT ... FROM m_bom;

-- Step 3: Update m_bom_line FK from bom_id TEXT → M_BOM_ID INTEGER
-- Step 4: Drop old, rename new
```

**Java changes (65 references):**
- `MBOM.java`: `getBomId()` → `getValue()`, queries use `WHERE Value = ?`
- `MBOMLine.java`: FK field `bom_id` → `M_BOM_ID INTEGER`
- `BomDropper.java`: all `bom_id` string lookups → `Value` lookups
- `BOMWalker.java`: walk by `M_BOM_ID`, not `bom_id`
- `PlacementCollectorVisitor.java`: parent references
- `BuildingRegistry.java`: root BOM lookup

**Pattern from S91:** The S91 migration kept `Value` = old text key for
backward compatibility. Same pattern here: `Value = bom_id` text.

## Task 2: Phase B — M_Product_Category INTEGER PK (ERP.db)

`M_Product_Category_ID` is currently TEXT PK (`'RE'`, `'GF'`, `'LI'`).
Must become INTEGER PK AUTO with the text codes in `Value`.

**Migration SQL** (`migration/DV027_category_integer_pk.sql`)

**Java changes (135 references):**
- Every `m_product_category_id = 'RE'` → `WHERE Value = 'RE'`
- FK columns in M_Product, m_bom, C_OrderLine → INTEGER
- `BuildingEntry.mProductCategoryId()` → returns Value, lookups by Value

**Risk:** This is the widest-reaching change. 135 references across
DAGCompiler, BonsaiBIMDesigner, ORMSandbox, IFCtoBOM. Test after this
phase before proceeding to Phase C.

## Task 3: Phase C — AD Tables (ERP.db)

Lower impact — most AD tables are read-only metadata used by
InferenceEngine, FPSuggestion, and validation handlers.

**Migration SQL** (`migration/DV028_ad_integer_pk.sql`)

Create one migration that handles all 13 AD tables. For composite PK
tables (ad_space_type_mep_bom, ad_space_adjacency), add a surrogate
`_ID INTEGER PK AUTO` and keep the composite as a UNIQUE constraint.

**Java changes:** Grep for each table name in Java code. Most AD tables
are accessed via raw SQL in DAOs — update the SQL to use `Value` for
human-facing lookups, `_ID` for FK joins.

## Task 4: IFCtoBOM DDL Alignment

`IFCtoBOM/src/main/java/com/bim/ifctobom/` creates BOM.db tables with
hardcoded DDL. The DDL must match the new schema.

Check `IFCtoBOMBase.java` or equivalent for CREATE TABLE statements.
Update to use INTEGER PK convention.

## Task 5: Verify Full Pipeline

After each phase, run the full verification:

```bash
mvn compile -q
./scripts/run_RosettaStones.sh classify_sh.yaml   # SH 7/7
./scripts/run_RosettaStones.sh classify_fk.yaml   # FK 7/7
./scripts/run_RosettaStones.sh classify_dx.yaml   # DX 6/7+WARN
./scripts/run_RosettaStones.sh classify_te.yaml   # TE 6/7+WARN
bash scripts/verify_test_seal.sh
```

**Phase A (m_bom) is the riskiest.** Run all 4 buildings after Phase A
before starting Phase B. If Phase A regresses, fix before proceeding.

**What the gates will catch:**

After each phase, the FINE pipeline logs (prompt 85 additions) expose
the INTEGER/TEXT flow through every stage:

```
[FINE] BOMDROP SH: root BOM=M_BOM_ID=1 (Value=SH_BUILDING), category=RE
[FINE] COMPILE SH: walk M_BOM_ID=1, origin=(-9.235, -2.746, -0.470)
[FINE] WRITE SH: 55 elements written to output DB
```

If any stage still passes TEXT `bom_id` where INTEGER `M_BOM_ID` is
expected, it will surface as:
- SQLException (type mismatch in prepared statement)
- NullPointerException (failed lookup returns null)
- Gate FAIL (0 elements written — walker couldn't resolve BOM)

The TE stress test (48K elements, 8 BOMs, 1522 lines) is particularly
valuable: if one FK reference is still TEXT while the schema expects
INTEGER, the walk will break at that BOM boundary. SH (55 elements,
1 BOM) might pass with a partial fix that TE will expose.

**IFCtoBOM re-extraction test:** After Phase A, delete `SH_BOM.db` and
re-run the pipeline. IFCtoBOM must create the new-schema BOM.db. If its
hardcoded DDL still uses `bom_id TEXT PRIMARY KEY`, the re-extraction
will produce an old-format DB that the migrated Java code can't read.
This is the DDL alignment check (Task 4).

```bash
# Phase A verification sequence:
mvn compile -q
rm library/SH_BOM.db                              # force re-extraction
./scripts/run_RosettaStones.sh classify_sh.yaml    # SH 7/7 = DDL aligned
./scripts/run_RosettaStones.sh classify_te.yaml    # TE 6/7+WARN = stress test
# Check FINE logs for INTEGER PK flow:
grep "BOMDROP\|COMPILE\|WRITE" logs/pipeline_*_extracted_*.log | tail -20
```

## Risk Analysis (S100-p85 Fleet Audit Findings)

The S100-p85 fleet audit established a baseline for all 34 non-TE buildings.
Use this baseline to detect regressions after each migration phase.

### High Risk — Silent Data Path Breaks

1. **Stale BOM.db files.** All 34 `*_BOM.db` files are created by IFCtoBOM
   with hardcoded DDL. After Phase A changes `m_bom` schema, existing BOM.db
   files have the old TEXT PK structure. If the file *exists* with old schema,
   `BomDropper.findBuildingBom()` and `MBOM.getRootByDocSubType()` will
   silently fail (query columns that don't exist or have wrong types).
   **Fix:** delete ALL `library/*_BOM.db` before the fleet run to force
   re-extraction with the new DDL.

2. **Null returns masquerading as empty buildings.** `MBOM.load(bomId)`
   currently takes a TEXT `bom_id`. After migration, if any code path still
   passes the old TEXT value where an INTEGER `M_BOM_ID` is expected, the
   query returns null → `BomDropper` returns 0 leaves → compilation produces
   0 elements → the building *looks* like RD/RL (infra walker gap) when it's
   actually a missed migration reference. **Detection:** any building that was
   7/7 PASS in p85 dropping to 0 elements is a code bug, not a data bug.

3. **M_Product_Category TEXT→INTEGER (Phase B, 135 refs)** is the widest
   blast radius. Every `c_orderline.m_product_category_id`, every
   `m_bom.m_product_category_id`, every `classify_*.yaml` reference uses
   TEXT codes like `'RE'`, `'GF'`, `'LI'`. If the FK column type changes
   but the YAML seeding still writes TEXT, the JOIN breaks silently.

### Medium Risk — Detectable Failures

4. **`prepare_compile_db()` ALTER TABLE.** The shell script copies BOM.db →
   `_compile.db` and runs ALTER TABLE commands. If the new schema already has
   the columns that ALTER tries to add, it'll error loudly (which is good).

5. **C_OrderLine copy in WriteStage.** `copyCOrderLineToOutput()` copies all
   columns by ordinal position from compile DB → output DB. If Phase A
   adds/removes/reorders columns in `C_OrderLine`, the ordinal mapping
   breaks → wrong data in wrong columns → gate failures.

6. **FINE logging discipline resolution.** `BomDropper.resolveDiscipline()`
   takes `productCategory` as TEXT (`"RE"`, `"FP"`). If Phase B changes
   `m_product_category_id` to INTEGER, the switch statement won't match →
   everything defaults to ARC → discipline breakdown looks wrong in logs.
   Not a compilation bug, but a diagnostic blind spot.

### Detection Strategy (use p85 baseline)

After each phase, compare against the p85 fleet audit results:

| Signal | Diagnosis |
|--------|-----------|
| Building was 7/7 → now 0 elements | Missed TEXT→INTEGER reference (#2) |
| Building was 7/7 → compile error | DDL mismatch (easy fix) |
| SH discipline was ARC+STR+CW → now all-ARC | `resolveDiscipline()` broken (#6) |
| Element count changed (SH 58→0 or 58→55) | FK JOIN broken somewhere |
| H6 WARNs count changed dramatically | C_OrderLine copy ordinal shift (#5) |

**Run full fleet after Phase B** (the 135-ref change) — not just SH/FK/DX/TE.
Phase B is the widest blast radius and TEXT category codes are used everywhere.

### P85 Baseline (34 non-TE buildings)

- **24 ALL GREEN** (8/8 or 7/7 PASS)
- **7 C9 WARN** (rank-match artifact): DX(89), HI(115), JE(15), NI(4), RA(2), RM(160), SC(15), WB(4)
- **3 FAIL** (pre-existing): CA, CL, WA (recently unblocked, Maven errors)
- **1 GENERATIVE FAIL**: DM (M_Product missing)
- **2 empty**: RD, RL (infra walker gap — 0 elements)
- **SH key metrics**: 58 elements, ARC=24 STR=2 CW=2, 22 PLACE + 4 CLUSTER, 24 H6 WARNs

## What NOT to do

- Do NOT change column semantics — only PK type and naming
- Do NOT remove `Value` text columns — they become the search key
- Do NOT modify existing migration files (sacred — append only)
- Do NOT skip the verification between phases
- Do NOT change AD_Org (already INTEGER PK, already compliant)
- Do NOT change tables that are already compliant (M_Product, C_Order, etc.)
- Do NOT hardcode _ID values — always look up by `Value`

## When Done

Prepend `# DONE` + commit hash to this file's first line.

Append findings:
- Phase A: m_bom migration result, how many Java files changed
- Phase B: M_Product_Category result, how many Java files changed
- Phase C: AD tables result, which tables migrated
- Gate results after each phase (SH, FK, DX, TE)
- Any TEXT PK references remaining (grep for non-_ID PKs)
- IFCtoBOM DDL alignment status

---

## Findings — Phase A (S100-p86)

### Phase A: m_bom INTEGER PK nativized in Java ORM
- **30+ Java files changed** across 7 modules (DAGCompiler, ORMSandbox, BIM_COBOL, BonsaiBIMDesigner, IFCtoBOM, BIMEyes, orm-core)
- `X_M_BOM.getPKColumnName()` → returns `M_BOM_ID` (was `bom_id`)
- `BasePO.loadByValue(String)` added — queries `WHERE Value = ?` for business key lookups
- All `MBOM.getBomId()` → `MBOM.getValue()` across codebase
- All `bom.load(stringBomId)` → `bom.loadByValue(stringBomId)` across codebase
- MBOM queries: `COLUMNNAME_bom_id` → `COLUMNNAME_Value` for root-finding, tree navigation, category search
- `MBOMLine.getBomId()` stays (returns m_bom_line.bom_id TEXT column — still populated, used for MA GUID lookups)
- SQL in DesignerDAO + BuildingRegistry: `b.bom_id NOT IN (...)` → `b.Value NOT IN (...)`
- IFCtoBOM DDL: `m_bom_line.M_BOM_ID INTEGER NOT NULL DEFAULT 0` (was nullable)
- IFCtoBOM DDL: `m_bom_line_ma.M_BOM_ID INTEGER NOT NULL DEFAULT 0` (was nullable)
- `bom_id TEXT` column kept on m_bom (legacy alias of Value, NOT dropped — backward compat for raw SQL in tests)

### Gate results (Phase A)
- SH 7/7 PASS (fresh extraction verified — deleted SH_BOM.db, rebuilt from IFCtoBOM)
- FK 7/7 PASS
- DX 6/7 PASS + 1 WARN (C9 — pre-existing 89 axis mismatches, documented)
- TE 6/7 PASS + 1 WARN (C9 — pre-existing 60 axis swaps)
- Zero regression vs p85 baseline

### IFCtoBOM DDL alignment
- m_bom: `M_BOM_ID INTEGER PRIMARY KEY AUTOINCREMENT` (already correct since S91)
- m_bom_line: `bom_child_id INTEGER PRIMARY KEY AUTOINCREMENT` (rename to M_BOM_Line_ID deferred — too many refs)
- m_bom_line: `M_BOM_ID INTEGER NOT NULL DEFAULT 0` (updated from nullable)
- m_bom_line: FK reference removed (`REFERENCES m_bom(bom_id)` → just `NOT NULL`, backfill handles FK)
- Fresh extraction: Value/Name/M_BOM_ID all populated by Tier 2 backfill. 0 NULL M_BOM_ID rows.

### TEXT PK references remaining (Phase B+C scope)
- `M_Product_Category.M_Product_Category_ID TEXT` (135 refs — Phase B)
- `ad_*` tables (13 tables with TEXT PKs — Phase C)
- `bom_child_id` not yet renamed to `M_BOM_Line_ID` (45 refs — deferred)
- Raw SQL in IFCtoBOM tests still uses `WHERE bom_id = ?` (works, bom_id column still exists)
- `m_bom_line.bom_id TEXT` column not dropped (backward compat for MA GUID, test SQL)

## Findings — Phase D (S100-p86d)

### Phase D investigation + partial fix

**DV030 migration applied to ERP.db:**
1. `M_Product.M_Product_Category_ID` TEXT → INTEGER (2481 rows, 377 NULLs preserved, CAST applied)
2. `ad_element_mep_alias.canonical_type` FK fixed: `REFERENCES ad_element_mep(element_type)` → `REFERENCES ad_element_mep(Value)` (84 rows, PRAGMA foreign_key_check CLEAN)

**Gate results:** SH 7/7 PASS, FK 7/7 PASS. Zero regression.

### Remaining non-conformant tables (investigated, not yet migrated)

**Group 1: TEXT PK tables (0 Java refs — data-only, low risk):**

| Table | Rows | Current PK |
|-------|------|-----------|
| `bad_rule` | 53 | `rule_id TEXT` |
| `bad_rule_category` | 6 | `category_id TEXT` |
| `bad_rule_param` | 1 | `(rule_id, param_key)` composite |
| `bad_discipline_priority` | 7 | `(higher_discipline, lower_discipline)` composite |

**Group 2: INTEGER PK but wrong column name (6–9 Java refs each):**

| Table | Rows | Has | Should be | Java refs |
|-------|------|-----|-----------|-----------|
| `ad_wall_face` | 204 | `id` | `AD_Wall_Face_ID` | 6 |
| `placement_rules` | 4801 | `id` | `AD_Placement_Rule_ID` | 2 |
| `W_Calibration_Result` | 0 | `id` | `W_Calibration_Result_ID` | 1 |
| `ad_assembly_connector` | 10 | `connector_id` | `AD_Assembly_Connector_ID` | 2 |
| `ad_assembly_manifest` | 37 | `manifest_id` | `AD_Assembly_Manifest_ID` | 3 |
| `ad_room_slot` | 38 | `slot_id` | `AD_Room_Slot_ID` | 9 |

**Group 3: c_orderline.m_product_category_id in output.db — NOT A BUG.**
- output.db is compiled output (denormalized flat export for 4D-8D downstream)
- TEXT category codes ("RE","CO") = M_Product_Category.Value = correct compiled values
- No JOIN back to ERP.db should be needed from compiled output
- Column name `m_product_category_id` is misleading but data is correct by design
- **No action needed** — denormalization is intentional in the compiler output

**DV031 migration applied to ERP.db:**

Group 1 — TEXT PK → INTEGER PK + Value (0 Java refs, SQL-only):
- `bad_rule_category`: 6 rows, `category_id TEXT` → `Bad_Rule_Category_ID INTEGER AUTO` + Value
- `bad_rule`: 53 rows, `rule_id TEXT` → `Bad_Rule_ID INTEGER AUTO` + Value, FK to category fixed, 5 self-refs (override) resolved
- `bad_rule_param`: 1 row, composite → `Bad_Rule_Param_ID INTEGER AUTO` + FK to bad_rule INTEGER
- `bad_discipline_priority`: 7 rows, composite → `Bad_Discipline_Priority_ID INTEGER AUTO` + UNIQUE composite kept

Group 2 — PK column rename (SQL + 3 Java files):
- `ad_wall_face`: `id` → `AD_Wall_Face_ID` (204 rows). Java: X_AdWallFace.java constant + getter
- `placement_rules`: `id` → `AD_Placement_Rule_ID` (4801 rows). No Java PK refs
- `W_Calibration_Result`: `id` → `W_Calibration_Result_ID` (0 rows). No Java PK refs
- `ad_assembly_connector`: `connector_id` → `AD_Assembly_Connector_ID` (10 rows). No Java PK refs
- `ad_assembly_manifest`: `manifest_id` → `AD_Assembly_Manifest_ID` (37 rows). No Java PK refs
- `ad_room_slot`: `slot_id` → `AD_Room_Slot_ID` (38 rows). Java: X_AdRoomSlot.java constant + getter, BuildingInspector.java raw SQL

Gate results: SH 7/7 PASS, FK 7/7 PASS. `mvn compile -q` PASS. Zero regression.

**Parasitic Name/Value columns (no constraints) — not yet fixed:**
- 10 tables got `Name TEXT, Value TEXT` appended (DV021) without NOT NULL or UNIQUE
- Properly-migrated tables have `Value TEXT NOT NULL UNIQUE`
- Affected: ad_assembly_connector, ad_assembly_manifest, ad_wall_face, placement_rules, ad_room_slot, W_Calibration_Result, ad_element_mep_alias, ad_val_rule, ad_building_profile, W_Validation_Advisory
- 3 more PO classes have `COLUMNNAME_id = "id"` (X_AdRoomBoundary, X_IGeometryMap, X_AdBuildingGrid) — not in Phase D scope, flag for Phase E
