# DONE 153436f
# Category hierarchy + BUILDING BOM backfill + AD table consolidation

You are a coder for bim-compiler. Schema + data migration, no docs.

Read first:
1. docs/MANIFESTO.md — category cascade (RE→floor→room→leaf)
2. docs/DATA_MODEL.md §7 — findings: missing categories, BUILDING BOMs with NULL m_product_category_id
3. docs/DISC_VALIDATION_DB_SRS.md §11.6.5 Step 4 (AD table move spec)
4. PROGRESS.md

## TASK 1: Add missing M_Product_Category rows (DV018)

ERP.db M_Product_Category has 46 rows — IFC element classification
only. Missing: the cascade levels from MANIFESTO.

Write `migration/DV018_category_hierarchy.sql`:

```sql
-- Top-level building types
INSERT OR IGNORE INTO M_Product_Category (m_product_category_id, name, parent_category, seq_no, is_active)
VALUES
  ('RE', 'Residential', NULL, 10, 1),
  ('IN', 'Infrastructure', NULL, 20, 1),
  ('CO', 'Commercial', NULL, 30, 1),
  ('IP', 'Industrial Plant', NULL, 40, 1);

-- Floor-level (children of building types)
-- GF, L1, L2, L3, L4, L5, RF, FN, MS

-- Room-level (children of floors)
-- LIVING, KITCHEN, BEDROOM, BATHROOM, DINING, MASTER, CORRIDOR, OFFICE

-- Infra segments (children of IN)
-- SUP, DCK, ABT, TRK, ROAD, RAIL, GEO
```

Source the actual values from existing m_bom rows across BOM databases:
```bash
for db in library/*_BOM.db; do
  sqlite3 "$db" "SELECT DISTINCT m_product_category_id FROM m_bom WHERE m_product_category_id IS NOT NULL;" 2>/dev/null
done | sort -u
```

Register every value found as an M_Product_Category row. Set parent_category
based on the cascade model (floor categories → parent RE or IN or CO as appropriate).

Apply to ERP.db: `sqlite3 library/ERP.db < migration/DV018_category_hierarchy.sql`

## TASK 2: Backfill BUILDING BOMs

BUILDING-level m_bom rows have `doc_base_type=RE` but NULL `m_product_category_id`.
Backfill across all BOM databases:

```bash
for db in library/*_BOM.db; do
  sqlite3 "$db" "UPDATE m_bom SET m_product_category_id = doc_base_type WHERE bom_type = 'BUILDING' AND m_product_category_id IS NULL;" 2>/dev/null
  echo "Backfilled: $(basename $db)"
done
```

Verify: no BUILDING BOM should have NULL m_product_category_id after this.

## TASK 3: Move bad_* tables to ERP.db (Step 4)

component_library.db has 4 AD tables not yet in ERP.db:
- bad_discipline_priority (7 rows)
- bad_rule (53 rows)
- bad_rule_category (6 rows)
- bad_rule_param (1 row)

Write `migration/DV019_move_bad_tables.sql`:
- CREATE TABLE IF NOT EXISTS for each in ERP.db
- ATTACH component_library.db
- INSERT OR IGNORE from component_library.db copy

Apply to ERP.db.

No Java readers exist for these tables (confirmed S69 investigation) — no Java changes needed.

Write `scripts/cleanup_complib_duplicates.sh` to drop stale duplicate tables
from component_library.db. Destructive — manual run only, not in pipeline.

## Constraints

- component_library.db is SACRED — no git operations on it
- Append-only migrations
- Do NOT run tests — code through, `mvn compile -q` at the end only
- Pre-flight: `// Implementing DATA_MODEL.md §7 — M_Product_Category hierarchy`

## When Done

Prepend `# DONE` + commit hash to this file's first line before committing.
Commit with `[S75] M_Product_Category hierarchy + BUILDING backfill + AD table consolidation`.
