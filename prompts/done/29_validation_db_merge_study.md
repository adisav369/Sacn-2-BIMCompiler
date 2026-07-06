# DONE — Validation DB Merge Study
> Commit: (study only — no code change. Decision: DO NOT MERGE. Tracked in ACTION_ROADMAP.)

Impact study — no code changes. Report findings only.

CTFL review (prompt 28) found: validation.db holds AD_Val_Rule (compliance
rules) — an ERP concern. AD_Val_Rule belongs alongside AD_Org in ERP.db,
not in a separate database. Both are AD-prefix tables serving the same
ERP validation engine.

Current state: 4-DB architecture (BOM.db, ERP.db, component_library.db, output.db)
plus validation.db as a 5th undocumented database.
[NOTE: premise incorrect — validation.db IS documented in DISC_VALIDATION_DB_SRS.md.
Architecture is 5-DB, not 4+1 undocumented. See §4 findings below.]

Tasks:

1. Query validation.db — list all tables, row counts, column names.
   Compare with ERP.db AD-prefix tables. Identify overlaps or conflicts.

2. Check Java code — which classes connect to validation.db?
   `grep -rn "validation.db" --include='*.java' | head -30`
   How many separate connection strings / paths exist?

3. Assess merge feasibility:
   - Can all validation.db tables be moved to ERP.db without name collisions?
   - Are there FK relationships between validation.db tables and ERP.db tables?
   - Would merging break any test that opens validation.db independently?

4. Check if validation.db is documented in DATA_MODEL.md or DATABASE_SCHEMA.md.
   If not, that's a doc gap regardless of merge decision.

5. Estimate scope: how many Java files, migrations, and docs would change?

Do NOT create migrations or edit Java files.

Commit message prefix: [S88-study].

---

## Findings appended by coder (S88, 2026-03-26)

## 1. validation.db Schema (library/validation.db — 78 KB)

| Table                      | Rows | Purpose                              |
|----------------------------|------|--------------------------------------|
| AD_Val_Rule                |   63 | Compliance rules (NFPA, UBBL, mined) |
| AD_Val_Rule_Param          |  132 | Rule parameters (spacing, clearance)  |
| AD_Val_Rule_Occupancy      |   31 | Rule ↔ occupancy class mapping        |
| AD_Occupancy_Class         |    6 | Occupancy classifications             |
| AD_Clash_Rule              |    6 | Inter-discipline clash rules          |
| AD_Validation_Result       |    0 | Per-line compliance verdicts (empty)  |
| AD_Val_Rule_Exception      |    2 | User overrides for WARN rules         |
| AD_Val_Rule_Mining_Source   |    3 | Provenance of mined rules             |
| ad_pattern_rule            |    8 | Generative placement patterns         |
| **Total**                  | **251** |                                    |

## 2. NAME COLLISION — BLOCKER

ERP.db already contains `ad_val_rule` (415 rows) and `ad_val_rule_param` (1,245 rows)
with **different schemas and different data**:

| Column           | validation.db AD_Val_Rule          | ERP.db ad_val_rule                |
|------------------|------------------------------------|-----------------------------------|
| PK               | ad_val_rule_id (manual)            | ad_val_rule_id (AUTOINCREMENT)    |
| Name column      | `name`                             | `rule_name`                       |
| Type column      | `rule_type` (COMPLIANCE/CLASH/...) | `check_method` (DIMENSION_RANGE)  |
| Scope            | `discipline`, `jurisdiction`       | `ifc_class`, `provenance`         |
| Standards        | `standard_ref`, `valid_from/to`    | (none)                            |
| Purpose          | Authored compliance rules          | Mined dimension ranges            |

Same table name, incompatible schemas, non-overlapping ID spaces.
**Cannot merge without renaming one set.**

ERP.db ad_val_rule_param also differs: has `param_name`/`param_value` (no value_type,
no condition_expr) vs validation.db's `name`/`value`/`value_type`/`condition_expr`.

## 3. Java Code — Connection Points

**1 production connection path:** `jdbc:sqlite:library/validation.db`

**3 production Java files:**
- `DesignerAPIImpl.java:1130` — opens valConn in `activateForFacilityType()`
- `PlacementValidatorImpl.java` — loads AD_Val_Rule + AD_Val_Rule_Param, caches by category
- `CalibrationDAO.java:257-276` — queries AD_Val_Rule_Param for DocEvent pitch

**8 test files** (all use `Path.of("library/validation.db")`):
- PlacementValidatorImplTest, DemoHouseTest, NonDisturbanceTest, CalibrationTest,
  PatternRuleTest, InfraUIFilterTest, BridgeRulesTest, InfraRulesTest

**1 script:** `scripts/run_NonDisturbance.sh`

## 4. Documentation Status

- **DISC_VALIDATION_DB_SRS.md** — primary spec, 18+ mentions. Defines 3-DB concern split.
- **DATA_MODEL.md** — mentions legacy name `disc_validation.db` (renamed S76). Needs update.
- **DATABASE_SCHEMA.md** — does NOT exist as a local doc (link points to GitHub).
- **16 other docs** mention validation.db (SourceCodeGuide, WorkOrderGuide, etc.)

validation.db is well-documented. The "5th undocumented database" premise in prompt
preamble is incorrect — DISC_VALIDATION_DB_SRS.md defines it as the third library DB
in a 3+1 architecture (ERP.db, component_library.db, validation.db + output.db).

## 5. Scope Estimate (if merge proceeds)

| Artifact          | Count | Notes                                        |
|-------------------|-------|----------------------------------------------|
| Java (production) |     3 | Connection string + query changes             |
| Java (test)       |     8 | Path.of + connection changes                  |
| Migrations        |     8 | V001, V003, V015, DV006-DV010, W001 comments |
| Docs              |    17 | DISC_VALIDATION_DB_SRS.md + 16 others         |
| Scripts           |     1 | run_NonDisturbance.sh                         |
| **Total files**   | **37** |                                              |

## 6. Feasibility Assessment

**MERGE IS NOT STRAIGHTFORWARD.** Three obstacles:

1. **Name collision** (blocker): Two `ad_val_rule` / `ad_val_rule_param` tables with
   incompatible schemas. One must be renamed. The ERP.db versions (mined dimension
   rules) could become `ad_val_rule_mined` / `ad_val_rule_mined_param`, or the
   validation.db versions (compliance rules) could become `ad_val_rule_compliance`.

2. **Concern separation is intentional**: DISC_VALIDATION_DB_SRS.md §2 defines
   validation.db as "RULES + VERDICTS (compliance engine)" — a distinct concern from
   ERP.db's "discipline metadata." The 3-DB split was a design decision, not an
   oversight. The two ad_val_rule tables serve different pipelines.

3. **7 unique tables** would move to ERP.db (AD_Clash_Rule, AD_Occupancy_Class,
   AD_Val_Rule_Occupancy, AD_Validation_Result, AD_Val_Rule_Exception,
   AD_Val_Rule_Mining_Source, ad_pattern_rule). No FK conflicts with existing ERP.db
   tables, but adds compliance-engine tables to what is currently a metadata store.

## 7. Recommendation

**Do not merge.** The two databases serve different concerns with name-colliding tables.
The merge would require:
- Renaming one `ad_val_rule` family (breaking all queries and migrations)
- Rewriting DISC_VALIDATION_DB_SRS.md (the authoritative spec)
- Touching 37 files for zero functional gain

The 4-DB claim in the prompt preamble undercounts — the architecture is actually
**4+1** (4 documented + validation.db documented separately in its own SRS). If the
goal is a clean DB count, the answer is to **acknowledge 5 databases** in DATA_MODEL.md,
not to merge two that were intentionally separated.

Alternative: if ERP alignment requires a single "AD_" database, rename the ERP.db
mined-dimension tables (ad_val_rule → ad_mined_dimension_rule) since those are the
newer, auto-generated ones. But this is a larger refactor with no current driver.

## WATCHDOG REVIEWED — 2026-03-26

**Verified:**

1. **9 tables in validation.db** — confirmed via `sqlite_master`. Table list matches §1.
2. **Name collision** — confirmed via `PRAGMA table_info`. validation.db `AD_Val_Rule` has
   `name`, `rule_type`, `discipline`, `jurisdiction`, `standard_ref`. ERP.db `ad_val_rule` has
   `rule_name`, `ifc_class`, `check_method`, `severity`. Incompatible schemas, same table name. BLOCKER.
3. **Row counts** — validation.db: 63 rows. ERP.db: 415 rows. Confirmed.
4. **Recommendation: Do not merge** — AGREE. Concern separation is by design
   (DISC_VALIDATION_DB_SRS.md §2). The fix is to acknowledge 5 databases, not collapse 2.

**Issues:**

- **No commit exists.** `git log --grep="S88"` returns nothing. Findings are working-tree-only.
  The coder appended results but did not commit. Must commit before moving to `done/`.
- **DONE marker on line 38, not line 1.** Protocol: first line = `# DONE — Title`.
- **validation.db untracked** in git status. This is a library DB (like component_library.db) —
  should be in `.gitignore` or committed. Needs decision.
- **DATA_MODEL.md** still says 4-DB. Open item from resume: update to acknowledge 5 (4+1).

**Action needed:**

1. Commit prompt 29 findings + validation.db decision: `[S88-study] validation.db merge study — do not merge (name collision)`
2. Fix DONE marker to line 1
3. Update DATA_MODEL.md: 4-DB → 5-DB (4+1)
4. After commit, move to `prompts/done/`
