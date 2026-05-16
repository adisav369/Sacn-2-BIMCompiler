#!/bin/bash
# split_db.sh — Split large extracted DBs into meta + geo for range-request streaming
# Usage: ./scripts/split_db.sh path/to/Building_extracted.db
# Produces: Building_meta.db (~5-40MB) + Building_geo.db (rest)
# Only useful for DBs with > 20,000 elements — small DBs load faster as single file.

set -e
DB="$1"
if [ -z "$DB" ] || [ ! -f "$DB" ]; then
  echo "Usage: $0 <path/to/Building_extracted.db>"
  exit 1
fi

ELEM_COUNT=$(sqlite3 "$DB" "SELECT COUNT(*) FROM elements_meta;" 2>/dev/null || echo 0)
SIZE=$(stat -c%s "$DB" 2>/dev/null || stat -f%z "$DB")
SIZE_MB=$((SIZE / 1024 / 1024))
echo "Source: $DB (${SIZE_MB}MB, ${ELEM_COUNT} elements)"

if [ "$ELEM_COUNT" -lt 15000 ]; then
  echo "Skip: ${ELEM_COUNT} elements < 15,000 threshold — full download is faster"
  exit 0
fi

BASE="${DB%_extracted.db}"
META="${BASE}_meta.db"
GEO="${BASE}_geo.db"

echo "Splitting into:"
echo "  Meta: $META"
echo "  Geo:  $GEO"

# Meta: clone then drop geometry tables
echo "Creating meta DB..."
rm -f "$META"
sqlite3 "$DB" ".clone $META"
sqlite3 "$META" "DROP TABLE IF EXISTS component_geometries; DROP TABLE IF EXISTS base_geometries; VACUUM;"
META_SIZE=$(stat -c%s "$META" 2>/dev/null || stat -f%z "$META")
echo "  Meta: $((META_SIZE / 1024 / 1024))MB"

# Geo: clone then drop metadata tables
echo "Creating geo DB..."
rm -f "$GEO"
sqlite3 "$DB" ".clone $GEO"
sqlite3 "$GEO" "DROP TABLE IF EXISTS elements_meta; DROP TABLE IF EXISTS element_transforms; DROP TABLE IF EXISTS element_instances; DROP TABLE IF EXISTS project_metadata; DROP TABLE IF EXISTS ifc_properties; VACUUM;"
GEO_SIZE=$(stat -c%s "$GEO" 2>/dev/null || stat -f%z "$GEO")
echo "  Geo:  $((GEO_SIZE / 1024 / 1024))MB"

echo "Done. Upload all three files to bucket."

# Generate _positions.bin for instant bbox loading
POS="${BASE}_positions.bin"
echo "Creating positions.bin..."
python3 -c "
import sqlite3, struct, sys
conn = sqlite3.connect('$DB')
rows = conn.execute('''
    SELECT t.center_x, t.center_y, t.center_z,
           COALESCE(t.bbox_x, 1), COALESCE(t.bbox_y, 1), COALESCE(t.bbox_z, 1)
    FROM element_transforms t
    JOIN element_instances i ON t.guid = i.guid
    WHERE i.geometry_hash IS NOT NULL
''').fetchall()
conn.close()
data = struct.pack('<I', len(rows))
for r in rows:
    data += struct.pack('<6f', *r)
with open('$POS', 'wb') as f:
    f.write(data)
print(f'  Positions: {len(rows)} elements, {len(data)/1024/1024:.1f}MB')
" || echo "  WARN: python3 not available, skipping positions.bin"
