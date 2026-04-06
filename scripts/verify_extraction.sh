#!/bin/bash
# Verify an extracted DB — runs Java ExtractionPostProcessor, logs to file.
#
# Usage:
#   ./scripts/verify_extraction.sh DAGCompiler/lib/input/LTU_AHouse_extracted.db
#
# Output: logs/extraction_verify_{building}_{timestamp}.txt

DB="${1:?Usage: verify_extraction.sh <extracted.db>}"
BUILDING=$(basename "$DB" _extracted.db)
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG="logs/extraction_verify_${BUILDING}_${TIMESTAMP}.txt"

mkdir -p logs

echo "Verifying: $DB"
echo "Log: $LOG"

# Run Java post-processor (read-only verification + forensic abstract)
mvn -pl DAGCompiler -q exec:java \
    -Dexec.mainClass="com.bim.compiler.db.ExtractionPostProcessor" \
    -Dexec.args="$DB" \
    2>&1 | tee "$LOG"

# Append raw SQL checks for cross-reference
echo "" >> "$LOG"
echo "========================================================================" >> "$LOG"
echo "  RAW SQL VERIFICATION" >> "$LOG"
echo "========================================================================" >> "$LOG"

echo "" >> "$LOG"
echo "--- Discipline counts ---" >> "$LOG"
sqlite3 "$DB" "SELECT discipline, COUNT(*) FROM elements_meta GROUP BY discipline ORDER BY COUNT(*) DESC;" >> "$LOG"

echo "" >> "$LOG"
echo "--- Geometry scale (element_transforms) ---" >> "$LOG"
sqlite3 "$DB" "SELECT MIN(center_x), MAX(center_x), MIN(center_y), MAX(center_y), MIN(center_z), MAX(center_z) FROM element_transforms;" >> "$LOG"

echo "" >> "$LOG"
echo "--- Bbox scale (elements_rtree) ---" >> "$LOG"
sqlite3 "$DB" "SELECT MIN(minX), MAX(maxX), MIN(minY), MAX(maxY), MIN(minZ), MAX(maxZ) FROM elements_rtree;" >> "$LOG"

echo "" >> "$LOG"
echo "--- Camera (project_metadata) ---" >> "$LOG"
sqlite3 "$DB" "SELECT key, value FROM project_metadata WHERE key LIKE 'view%';" >> "$LOG"

echo "" >> "$LOG"
echo "--- Geometry hell check ---" >> "$LOG"
# Any discipline with max abs center > 500 is suspect
sqlite3 "$DB" "
SELECT em.discipline,
       MAX(ABS(et.center_x)) as max_x,
       MAX(ABS(et.center_y)) as max_y,
       MAX(ABS(et.center_z)) as max_z,
       CASE WHEN MAX(ABS(et.center_x)) > 500 OR MAX(ABS(et.center_y)) > 500
            THEN 'SUSPECT_MM' ELSE 'OK_METRES' END as verdict
FROM element_transforms et
JOIN elements_meta em ON em.guid = et.guid
GROUP BY em.discipline
ORDER BY max_x DESC;" >> "$LOG"

echo "" >> "$LOG"
echo "--- Storey structure ---" >> "$LOG"
sqlite3 "$DB" "SELECT storey, COUNT(*) FROM elements_meta GROUP BY storey ORDER BY COUNT(*) DESC;" >> "$LOG"

echo "" >> "$LOG"
echo "--- Total ---" >> "$LOG"
sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta;" >> "$LOG"

echo "" >> "$LOG"
echo "Verification complete: $LOG"
