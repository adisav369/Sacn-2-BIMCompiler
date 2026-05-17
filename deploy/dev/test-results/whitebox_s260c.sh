#!/bin/bash
# §S260c Whitebox test suite — exercises ground Y, storey bands, diff logic
# Uses sqlite3 CLI — no Node.js WASM dependency.
# Run: bash deploy/dev/test-results/whitebox_s260c.sh > deploy/dev/test-results/whitebox_s260c.log 2>&1
# Then read the log. Preservable for regression.

set -e

# Default to Clinic, accept arg for other buildings
DB="${1:-deploy/buildings/Clinic_extracted.db}"
DB2="${2:-}"  # optional second DB for diff test
DBNAME=$(basename "$DB" .db | sed 's/_extracted//')

echo "════════════════════════════════════════"
echo "§WHITEBOX_START building=$DBNAME db=$DB"
echo "════════════════════════════════════════"

if [ ! -f "$DB" ]; then echo "§WHITEBOX_FAIL db=$DB not found"; exit 1; fi

# ── TEST 1: Ground Y — storey name matching ──
echo ""
echo "── TEST 1: Ground Y ──"
echo "§GROUND_Y_STOREYS all:"
sqlite3 "$DB" "SELECT DISTINCT storey FROM elements_meta ORDER BY storey;"

echo ""
echo "§GROUND_Y Step1 — storey name match for GF slabs (top 5 by area):"
sqlite3 -header -column "$DB" "
SELECT t.center_z - t.bbox_z/2 AS bottom,
       ROUND(t.bbox_x * t.bbox_y, 1) AS area_m2,
       m.storey
FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid
WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0
  AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL
  AND m.storey IN ('Ground Floor','Ground','First Floor','1st Floor','Level 0','Level 00','Level 1','GF','L0','L00','L1','00','0','1F','EG','Erdgeschoss','Storey 1','Plan 1','VÅN 1','VÅNING 1','1. OG','Rez-de-chaussée','RC','Planta Baja','PB','Piso 0','Begane grond','BG','GROUND FLOOR LEVEL','Ground Lev','Aras Tanah','u.etg')
ORDER BY area_m2 DESC
LIMIT 5;
"
STEP1_COUNT=$(sqlite3 "$DB" "
SELECT COUNT(*)
FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid
WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0
  AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL
  AND m.storey IN ('Ground Floor','Ground','First Floor','1st Floor','Level 0','Level 00','Level 1','GF','L0','L00','L1','00','0','1F','EG','Erdgeschoss','Storey 1','Plan 1','VÅN 1','VÅNING 1','1. OG','Rez-de-chaussée','RC','Planta Baja','PB','Piso 0','Begane grond','BG','GROUND FLOOR LEVEL','Ground Lev','Aras Tanah','u.etg');
")
echo "§GROUND_Y Step1_count=$STEP1_COUNT"

if [ "$STEP1_COUNT" = "0" ]; then
  echo ""
  echo "§GROUND_Y Step2 — largest above-grade slab (top 15 by area):"
  sqlite3 -header -column "$DB" "
  SELECT t.center_z - t.bbox_z/2 AS bottom,
         ROUND(t.bbox_x * t.bbox_y, 1) AS area_m2,
         ROUND(t.center_z, 3) AS center_z,
         m.storey,
         CASE WHEN t.center_z >= -3 THEN 'ABOVE' ELSE 'BELOW' END AS grade
  FROM element_transforms t JOIN elements_meta m ON t.guid=m.guid
  WHERE m.ifc_class='IfcSlab' AND t.bbox_z IS NOT NULL AND t.bbox_z < 1.0
    AND t.bbox_x IS NOT NULL AND t.bbox_y IS NOT NULL
  ORDER BY area_m2 DESC
  LIMIT 15;
  "
fi

# ── TEST 2: Storey band sorting (median Z vs min Z) ──
echo ""
echo "── TEST 2: Storey bands (BUG 5 — median vs min) ──"
echo "§STOREY_BANDS (median Z sort):"
sqlite3 -header -column "$DB" "
WITH storey_stats AS (
  SELECT m.storey,
         COUNT(*) as cnt,
         ROUND(MIN(COALESCE(t.center_z,0)), 2) as min_z,
         ROUND(AVG(COALESCE(t.center_z,0)), 2) as avg_z
  FROM elements_meta m
  LEFT JOIN element_transforms t ON t.guid = m.guid
  WHERE m.ifc_class != 'IfcOpeningElement'
  GROUP BY m.storey
)
SELECT storey, cnt, min_z, avg_z
FROM storey_stats
ORDER BY avg_z;
"
# Note: SQLite doesn't have MEDIAN(), using AVG as proxy (close enough for ordering).
# The actual JS code computes true median — this verifies the ordering is sensible.

echo ""
echo "§STOREY_BANDS_MINZ_ORDER (old — by min Z):"
sqlite3 "$DB" "
WITH storey_stats AS (
  SELECT m.storey,
         MIN(COALESCE(t.center_z,0)) as min_z
  FROM elements_meta m
  LEFT JOIN element_transforms t ON t.guid = m.guid
  WHERE m.ifc_class != 'IfcOpeningElement'
  GROUP BY m.storey
)
SELECT storey || ' minZ=' || ROUND(min_z,2) FROM storey_stats ORDER BY min_z;
"

echo ""
echo "§STOREY_BANDS_AVGZ_ORDER (new proxy — by avg Z ≈ median Z):"
sqlite3 "$DB" "
WITH storey_stats AS (
  SELECT m.storey,
         AVG(COALESCE(t.center_z,0)) as avg_z
  FROM elements_meta m
  LEFT JOIN element_transforms t ON t.guid = m.guid
  WHERE m.ifc_class != 'IfcOpeningElement'
  GROUP BY m.storey
)
SELECT storey || ' avgZ=' || ROUND(avg_z,2) FROM storey_stats ORDER BY avg_z;
"

# ── TEST 3: Storey panel data ──
echo ""
echo "── TEST 3: Storey panel data ──"
sqlite3 "$DB" "SELECT storey, COUNT(*) as cnt FROM elements_meta GROUP BY storey ORDER BY storey;" | while read line; do
  echo "§STOREY_DATA $line"
done

# ── TEST 4: Diff pipeline (self-diff) ──
echo ""
echo "── TEST 4: Diff pipeline (self-diff) ──"
GUID_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta;")
echo "§DIFF_SELF guids=$GUID_COUNT — self-diff expects added=0 removed=0 changed=0"

if [ -n "$DB2" ] && [ -f "$DB2" ]; then
  echo ""
  echo "── TEST 4b: Cross-diff with $DB2 ──"
  COUNT2=$(sqlite3 "$DB2" "SELECT COUNT(*) FROM elements_meta;")
  echo "§DIFF_CROSS base=$GUID_COUNT variation=$COUNT2"
  # Find added (in DB2 not in DB1)
  ADDED=$(sqlite3 "$DB2" "SELECT COUNT(*) FROM elements_meta WHERE guid NOT IN (SELECT guid FROM elements_meta);" 2>/dev/null || echo "0")
  echo "§DIFF_CROSS added=$ADDED (GUIDs in variation not in base)"
fi

# ── TEST 5: DB export validation check ──
echo ""
echo "── TEST 5: DB integrity ──"
TABLES=$(sqlite3 "$DB" "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;" | tr '\n' ',')
ELEM_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta;")
TRANS_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM element_transforms;")
INST_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM element_instances;" 2>/dev/null || echo "N/A")
GEO_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM component_geometries;" 2>/dev/null || echo "N/A")
echo "§DB_INTEGRITY tables=$TABLES"
echo "§DB_INTEGRITY elements=$ELEM_COUNT transforms=$TRANS_COUNT instances=$INST_COUNT geometries=$GEO_COUNT"
# Check for orphan elements (no transform)
ORPHANS=$(sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta m LEFT JOIN element_transforms t ON m.guid=t.guid WHERE t.guid IS NULL;")
echo "§DB_INTEGRITY orphan_elements_no_transform=$ORPHANS"

echo ""
echo "§WHITEBOX_DONE building=$DBNAME"
