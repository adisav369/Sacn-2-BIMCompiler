#!/usr/bin/env bash
# make_resident_meta.sh — project a pristine meta.db (bbox substrate) out of each building's
# extracted.db for the Modeller's permanent residents. NON-INVENT: pure subset of the pristine
# extraction — keeps elements_meta/element_transforms/spatial tables, DROPS the cooked recipe
# (m_bom/m_bom_line) and the mesh blobs (component_geometries) + 4D/qto. Result is range-friendly
# raw sqlite, uploaded to OCI buildings/ and walked by str_walker (W-RESIDENT-OPEN).
#
# Usage:  scripts/make_resident_meta.sh [Building ...]   (default: SampleHouse Duplex Schependomlaan)
# Then:   oci os object put -bn bim-ootb --name buildings/<B>_meta.db --file deploy/buildings/<B>_meta.db \
#               --content-type application/octet-stream --force
set -euo pipefail
cd "$(dirname "$0")/../deploy/buildings"
KEEP="elements_meta element_transforms element_instances project_metadata rel_contained_in_space spatial_structure surface_styles"
BUILDINGS=("${@:-SampleHouse Duplex Schependomlaan}")
for b in ${BUILDINGS[@]}; do
  IN="${b}_extracted.db"; OUT="${b}_meta.db"
  [ -f "$IN" ] || { echo "skip $b (no $IN)"; continue; }
  rm -f "$OUT"
  have=$(sqlite3 "$IN" "SELECT name FROM sqlite_master WHERE type='table'")
  sql="ATTACH '$IN' AS src;"
  for t in $KEEP; do echo "$have" | grep -qx "$t" && sql="$sql CREATE TABLE $t AS SELECT * FROM src.$t;"; done
  sql="$sql CREATE INDEX IF NOT EXISTS ix_et_guid ON element_transforms(guid);"
  sql="$sql CREATE INDEX IF NOT EXISTS ix_em_guid ON elements_meta(guid);"
  echo "$sql" | sqlite3 "$OUT"
  bad=$(sqlite3 "$OUT" "SELECT count(*) FROM sqlite_master WHERE name IN ('m_bom','m_bom_line','component_geometries')")
  printf '%-22s %6.2f MB  elements=%s  cooked-tables=%s\n' "$OUT" "$(echo "scale=2;$(stat -c%s "$OUT")/1048576"|bc)" \
    "$(sqlite3 "$OUT" 'SELECT count(*) FROM elements_meta')" "$bad"
done
