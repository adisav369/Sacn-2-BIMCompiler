#!/bin/bash
# apply_mined_rules.sh — Uncomment and apply all DV_*_rules.sql candidate rules
# to ERP.db ad_val_rule + ad_val_rule_param tables.
#
# Usage: ./scripts/apply_mined_rules.sh [--dry-run]
#
# Reads 22 DV_*_rules.sql files from migration/, strips "-- " comment prefix
# from INSERT statements in §5, and applies them to ERP.db.

set -euo pipefail

DB="library/disc_patterns.db"   # de-ERP: canonical pattern-store name (ERP.db is a back-compat symlink)
MIGRATION_DIR="migration"
DRY_RUN=0

if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=1
    echo "DRY RUN — no changes will be applied"
fi

# Verify schema exists
if ! sqlite3 "$DB" "SELECT COUNT(*) FROM ad_val_rule" >/dev/null 2>&1; then
    echo "ERROR: ad_val_rule table not found in $DB. Run DV010 migration first."
    exit 1
fi

# Count existing rules
EXISTING=$(sqlite3 "$DB" "SELECT COUNT(*) FROM ad_val_rule")
echo "Existing rules: $EXISTING"

# Process each DV_*_rules.sql file
TOTAL_RULES=0
TOTAL_PARAMS=0
PROCESSED_FILES=0
SKIPPED_FILES=0

for f in "$MIGRATION_DIR"/DV_*_rules.sql; do
    BASENAME=$(basename "$f")

    # Extract INSERT statements from §5 section
    # Multi-line INSERTs: strip "-- " prefix from all SQL lines (INSERT, VALUES, continuations)
    # Skip "-- Rule:" comment headers and blank lines
    SQL=$(sed -n '/^-- §5/,$ {
        /^-- §5/d
        /^-- Rule:/d
        /^-- Review/d
        /^$/d
        /^--[[:space:]]/{ s/^-- //; s/^--\t//; p; }
    }' "$f")

    if [[ -z "$SQL" ]]; then
        echo "  SKIP $BASENAME (no candidate rules)"
        SKIPPED_FILES=$((SKIPPED_FILES + 1))
        continue
    fi

    # Fix last_insert_rowid() bug: after first param INSERT, last_insert_rowid()
    # returns the PARAM's rowid, not the RULE's. Replace with subquery for params.
    SQL=$(echo "$SQL" | sed 's/VALUES (last_insert_rowid()/VALUES ((SELECT MAX(ad_val_rule_id) FROM ad_val_rule)/g')

    # Count rules in this file (INSERT INTO ad_val_rule ( = rule header, not param)
    FILE_RULES=$(echo "$SQL" | grep -c "INSERT INTO ad_val_rule (" || true)
    FILE_PARAMS=$(echo "$SQL" | grep -c "INSERT INTO ad_val_rule_param" || true)

    if [[ $DRY_RUN -eq 1 ]]; then
        echo "  WOULD APPLY $BASENAME: $FILE_RULES rules, $FILE_PARAMS params"
    else
        # Apply within a transaction for atomicity
        echo "BEGIN TRANSACTION;
$SQL
COMMIT;" | sqlite3 "$DB"
        echo "  APPLIED $BASENAME: $FILE_RULES rules, $FILE_PARAMS params"
    fi

    TOTAL_RULES=$((TOTAL_RULES + FILE_RULES))
    TOTAL_PARAMS=$((TOTAL_PARAMS + FILE_PARAMS))
    PROCESSED_FILES=$((PROCESSED_FILES + 1))
done

echo ""
echo "Summary:"
echo "  Files processed: $PROCESSED_FILES"
echo "  Files skipped:   $SKIPPED_FILES"
echo "  Total rules:     $TOTAL_RULES"
echo "  Total params:    $TOTAL_PARAMS"

if [[ $DRY_RUN -eq 0 ]]; then
    FINAL=$(sqlite3 "$DB" "SELECT COUNT(*) FROM ad_val_rule")
    FINAL_P=$(sqlite3 "$DB" "SELECT COUNT(*) FROM ad_val_rule_param")
    echo "  DB ad_val_rule:       $FINAL"
    echo "  DB ad_val_rule_param: $FINAL_P"

    # Verify by provenance
    echo ""
    echo "Rules by provenance:"
    sqlite3 -column -header "$DB" \
        "SELECT provenance, COUNT(*) as rules FROM ad_val_rule GROUP BY provenance ORDER BY rules DESC"

    echo ""
    echo "Rules by ifc_class:"
    sqlite3 -column -header "$DB" \
        "SELECT ifc_class, COUNT(*) as rules FROM ad_val_rule GROUP BY ifc_class ORDER BY rules DESC"
fi
