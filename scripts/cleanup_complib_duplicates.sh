#!/bin/bash
# cleanup_complib_duplicates.sh — Drop stale duplicate tables from component_library.db
# These tables now live authoritatively in ERP.db (Steps 1-4 of §11.6.5)
#
# DESTRUCTIVE — manual run only, NOT in pipeline
# component_library.db is SACRED — no git operations on it
#
# Usage: ./scripts/cleanup_complib_duplicates.sh [--dry-run]

set -euo pipefail

DB="library/component_library.db"

if [ ! -f "$DB" ]; then
    echo "ERROR: $DB not found"
    exit 1
fi

# Tables that now live authoritatively in ERP.db
TABLES=(
    # AD discipline tables (moved Steps 1-2)
    ad_assembly_connector
    ad_assembly_manifest
    ad_code_requirement
    ad_element_mep
    ad_fp_coverage
    ad_fp_trigger
    ad_room_slot
    ad_space_adjacency
    ad_space_dim
    ad_space_exterior_rule
    ad_space_type
    ad_space_type_mep
    ad_space_type_mep_bom
    ad_space_type_opening
    ad_wall_face
    placement_rules
    # bad_* tables (moved Step 4 — DV019)
    bad_discipline_priority
    bad_rule
    bad_rule_category
    bad_rule_param
    # M_Product_Category (moved Step 3 — DV015)
    M_Product_Category
)

# M_Product deliberately NOT in this list — Step 6 (not yet)

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
    DRY_RUN=true
    echo "=== DRY RUN — no changes ==="
fi

for table in "${TABLES[@]}"; do
    exists=$(sqlite3 "$DB" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table';")
    if [ "$exists" = "1" ]; then
        count=$(sqlite3 "$DB" "SELECT COUNT(*) FROM \"$table\";" 2>/dev/null || echo "?")
        if $DRY_RUN; then
            echo "WOULD DROP: $table ($count rows)"
        else
            sqlite3 "$DB" "DROP TABLE \"$table\";"
            echo "DROPPED: $table ($count rows)"
        fi
    else
        echo "SKIP: $table (not present)"
    fi
done

if $DRY_RUN; then
    echo "=== DRY RUN complete — no changes made ==="
else
    echo "=== Cleanup complete ==="
    remaining=$(sqlite3 "$DB" "SELECT COUNT(*) FROM sqlite_master WHERE type='table';")
    echo "Tables remaining in component_library.db: $remaining"
fi
