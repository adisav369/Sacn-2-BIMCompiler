#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# migration_bom_db_extract.sh
# Phase 4 Part 1: Extract BOM tables from component_library.db → BOM.db
#
# Copies schema + data for 4 tables:
#   m_bom, m_bom_line, m_attribute, M_BomCategory
# Verifies row counts, then drops from component_library.db.
# ─────────────────────────────────────────────────────────────────────────────
set -eu

SRC="library/component_library.db"
DST="library/BOM.db"

if [ ! -f "$SRC" ]; then
    echo "ERROR: $SRC not found. Run from project root." >&2
    exit 1
fi

echo "=== Phase 4 Part 1: BOM.db Extraction ==="

# 1. Remove existing BOM.db if present
rm -f "$DST"
echo "[1/5] Creating $DST from scratch"

# 2. Dump schema + data for the 4 BOM tables
sqlite3 "$SRC" <<'SQL' | sqlite3 "$DST"
.dump m_bom
.dump m_bom_line
.dump m_attribute
.dump M_BomCategory
SQL

echo "[2/5] Schema + data copied to $DST"

# 3. Verify row counts match
echo "[3/5] Verifying row counts..."
TABLES=("m_bom" "m_bom_line" "m_attribute" "M_BomCategory")
ALL_OK=true

for TBL in "${TABLES[@]}"; do
    SRC_CNT=$(sqlite3 "$SRC" "SELECT COUNT(*) FROM \"$TBL\"")
    DST_CNT=$(sqlite3 "$DST" "SELECT COUNT(*) FROM \"$TBL\"")
    if [ "$SRC_CNT" != "$DST_CNT" ]; then
        echo "  FAIL: $TBL src=$SRC_CNT dst=$DST_CNT" >&2
        ALL_OK=false
    else
        echo "  OK: $TBL = $SRC_CNT rows"
    fi
done

if [ "$ALL_OK" != "true" ]; then
    echo "ERROR: Row count mismatch — aborting. $DST left for inspection." >&2
    exit 1
fi

# 4. Drop BOM tables from component_library.db
echo "[4/5] Dropping BOM tables from $SRC"
sqlite3 "$SRC" <<'SQL'
DROP TABLE IF EXISTS m_attribute;
DROP TABLE IF EXISTS m_bom_line;
DROP TABLE IF EXISTS M_BomCategory;
DROP TABLE IF EXISTS m_bom;
SQL
echo "  Dropped: m_attribute, m_bom_line, M_BomCategory, m_bom"

# 5. Vacuum both DBs
echo "[5/5] VACUUM on both databases"
sqlite3 "$SRC" "VACUUM;"
sqlite3 "$DST" "VACUUM;"

echo "=== Migration complete ==="
echo "  $DST: $(sqlite3 "$DST" "SELECT COUNT(*) FROM m_bom") BOMs, $(sqlite3 "$DST" "SELECT COUNT(*) FROM m_bom_line") lines, $(sqlite3 "$DST" "SELECT COUNT(*) FROM m_attribute") attrs, $(sqlite3 "$DST" "SELECT COUNT(*) FROM M_BomCategory") categories"
echo "  $SRC: BOM tables removed"
