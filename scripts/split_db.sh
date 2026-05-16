#!/bin/bash
# split_db.sh — Split large extracted DBs into meta + geo for range-request streaming
# Usage: ./scripts/split_db.sh path/to/Building_extracted.db
# Produces: Building_meta.db (~5-40MB) + Building_geo.db (rest)
# Only useful for DBs > 50MB — small DBs load faster as single file.

set -e
DB="$1"
if [ -z "$DB" ] || [ ! -f "$DB" ]; then
  echo "Usage: $0 <path/to/Building_extracted.db>"
  exit 1
fi

SIZE=$(stat -c%s "$DB" 2>/dev/null || stat -f%z "$DB")
SIZE_MB=$((SIZE / 1024 / 1024))
echo "Source: $DB (${SIZE_MB}MB)"

if [ "$SIZE_MB" -lt 50 ]; then
  echo "Skip: ${SIZE_MB}MB < 50MB threshold — full download is faster"
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
