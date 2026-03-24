#!/bin/bash
# mine_building_profiles.sh — Mine element class distributions from extracted DBs
# and populate ad_building_profile in ERP.db.
#
# Usage: ./scripts/mine_building_profiles.sh [--dry-run]
#
# Scans all *_extracted.db files, computes IFC class distribution per building,
# and inserts into ERP.db. Idempotent (INSERT OR REPLACE).

set -euo pipefail

DB="library/ERP.db"
INPUT_DIR="DAGCompiler/lib/input"
DRY_RUN=0

if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=1
    echo "DRY RUN — no changes will be applied"
fi

# Verify schema exists
if ! sqlite3 "$DB" "SELECT COUNT(*) FROM ad_building_profile" >/dev/null 2>&1; then
    echo "ERROR: ad_building_profile table not found. Run DV011 migration first."
    exit 1
fi

TOTAL_BUILDINGS=0
TOTAL_ROWS=0

for extracted in "$INPUT_DIR"/*_extracted.db; do
    [ -f "$extracted" ] || continue
    BTYPE=$(basename "$extracted" _extracted.db)

    # Skip tiny/empty DBs
    COUNT=$(sqlite3 "$extracted" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo 0)
    [ "$COUNT" -eq 0 ] && continue

    CLASSES=$(sqlite3 "$extracted" "SELECT COUNT(DISTINCT ifc_class) FROM elements_meta" 2>/dev/null)

    if [[ $DRY_RUN -eq 1 ]]; then
        echo "  WOULD MINE $BTYPE: $COUNT elements, $CLASSES classes"
    else
        # Generate INSERT OR REPLACE for each (building, ifc_class)
        SQL=$(sqlite3 "$extracted" "
            SELECT 'INSERT OR REPLACE INTO ad_building_profile '
                || '(building_type, ifc_class, element_count, element_ratio, total_elements, class_count) '
                || 'VALUES (''$BTYPE'', ''' || ifc_class || ''', '
                || COUNT(*) || ', '
                || ROUND(100.0 * COUNT(*) / (SELECT COUNT(*) FROM elements_meta), 1) || ', '
                || (SELECT COUNT(*) FROM elements_meta) || ', '
                || (SELECT COUNT(DISTINCT ifc_class) FROM elements_meta) || ');'
            FROM elements_meta
            GROUP BY ifc_class
            ORDER BY COUNT(*) DESC;
        " 2>/dev/null)

        if [ -n "$SQL" ]; then
            echo "BEGIN; $SQL COMMIT;" | sqlite3 "$DB"
            ROWS=$(echo "$SQL" | grep -c "INSERT" || true)
            echo "  MINED $BTYPE: $COUNT elements, $CLASSES classes, $ROWS profile rows"
            TOTAL_ROWS=$((TOTAL_ROWS + ROWS))
        fi
    fi

    TOTAL_BUILDINGS=$((TOTAL_BUILDINGS + 1))
done

echo ""
echo "Summary:"
echo "  Buildings mined: $TOTAL_BUILDINGS"
echo "  Profile rows:    $TOTAL_ROWS"

if [[ $DRY_RUN -eq 0 ]]; then
    FINAL=$(sqlite3 "$DB" "SELECT COUNT(*) FROM ad_building_profile")
    BUILDINGS=$(sqlite3 "$DB" "SELECT COUNT(DISTINCT building_type) FROM ad_building_profile")
    CLASSES=$(sqlite3 "$DB" "SELECT COUNT(DISTINCT ifc_class) FROM ad_building_profile")
    echo "  DB rows:         $FINAL"
    echo "  DB buildings:    $BUILDINGS"
    echo "  DB IFC classes:  $CLASSES"

    echo ""
    echo "Archetype distribution (top class per building):"
    sqlite3 -column -header "$DB" "
        SELECT building_type, ifc_class as dominant_class,
               element_ratio || '%' as ratio, total_elements
        FROM ad_building_profile p1
        WHERE element_ratio = (
            SELECT MAX(element_ratio) FROM ad_building_profile p2
            WHERE p2.building_type = p1.building_type
        )
        ORDER BY total_elements DESC
    "
fi
