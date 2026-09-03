# DONE — Apply DV017 migration to all BOM databases
> Commit: 4ad0ec5 [S68e]

You are a coder for bim-compiler. Small bounded task.

DV017_product_category_rename.sql was written in S68 and applied to SH_BOM.db.
It renames bom_category → m_product_category_id on m_bom, C_OrderLine,
ad_pattern_rule, ad_val_rule_param, and renames table M_BomCategory →
M_Product_Category.

## Task

Apply this migration to ALL remaining *_BOM.db files in library/.
Not all databases have all tables — skip errors silently.

```bash
for db in library/*_BOM.db; do
  [ "$(basename $db)" = "SH_BOM.db" ] && continue  # already done
  sqlite3 "$db" < migration/DV017_product_category_rename.sql 2>/dev/null
  echo "Applied: $(basename $db)"
done
```

Also apply to ERP.db if it has the bom_category column.

## Verify

After running, confirm no BOM database still has bom_category column:

```bash
for db in library/*_BOM.db; do
  sqlite3 "$db" "PRAGMA table_info(m_bom);" 2>/dev/null | grep bom_category && echo "FAIL: $db"
done
```

## Constraints

- Do NOT modify migration SQL
- Do NOT modify Java
- Just run the existing migration
- Gate: `mvn compile -q` must still pass

## When Done

Commit with message linking to this prompt. Leave this file for watchdog review.
Watchdog will review and move to prompts/done/.

## WATCHDOG REVIEWED
**S69 Watchdog** — 2026-03-24

Verified: `PRAGMA table_info(m_bom)` across all *_BOM.db files returns zero `bom_category` hits. DV017 fully applied. Compile clean.
