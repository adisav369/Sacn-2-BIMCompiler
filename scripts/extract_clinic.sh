#!/bin/bash
# extract_clinic.sh — Extract 5 Clinic IFC files → single Clinic_extracted.db → split
# Uses extractIFC2DB.js (Node.js + web-ifc) per discipline, then merges via SQLite.
# Output: deploy/buildings/Clinic_extracted.db + Clinic_meta.db + Clinic_geo.db + Clinic_positions.bin
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"
IFC_DIR="$ROOT/DAGCompiler/lib/input/IFC/UNMERGED"
TMP_DIR="$(mktemp -d)"
OUT="$ROOT/deploy/buildings/Clinic_extracted.db"

echo "=== Clinic Multi-Discipline Extraction ==="
echo "IFC dir: $IFC_DIR"
echo "Temp:    $TMP_DIR"
echo "Output:  $OUT"
echo

# Map filename → discipline override
declare -A DISC_MAP=(
  ["Clinic_Architectural_IFC2x3.ifc"]=""
  ["Clinic_Structural_IFC2x3.ifc"]="STR"
  ["Clinic_Electrical_IFC2x3.ifc"]="ELEC"
  ["Clinic_HVAC_IFC2x3.ifc"]="ACMV"
  ["Clinic_Plumbing_IFC2x3.ifc"]="PLB"
)

# Step 1: Extract each IFC to its own temp DB
for IFC_FILE in "$IFC_DIR"/Clinic_*.ifc; do
  BASENAME="$(basename "$IFC_FILE")"
  TMP_DB="$TMP_DIR/${BASENAME%.ifc}.db"
  DISC="${DISC_MAP[$BASENAME]}"
  DISC_ARG=""
  if [ -n "$DISC" ]; then
    DISC_ARG="--disc $DISC"
  fi
  echo "── Extracting: $BASENAME ${DISC:+(forced disc=$DISC)}"
  node "$SCRIPT_DIR/extractIFC2DB.js" --ifc "$IFC_FILE" -o "$TMP_DB" $DISC_ARG
  echo "   → $(sqlite3 "$TMP_DB" 'SELECT COUNT(*) FROM elements_meta') elements"
  echo
done

# Step 2: Merge all temp DBs into single output
echo "── Merging into $OUT"
rm -f "$OUT"

# Use first DB as base
FIRST_DB=$(ls "$TMP_DIR"/*.db | head -1)
cp "$FIRST_DB" "$OUT"

# Attach and insert from remaining DBs
for DB in "$TMP_DIR"/*.db; do
  [ "$DB" = "$FIRST_DB" ] && continue
  LABEL="$(basename "$DB")"
  echo "   Merging: $LABEL"
  # Discover actual table names (extractIFC2DB.js uses component_geometries, not base_geometries)
  sqlite3 "$OUT" <<SQL
ATTACH '$DB' AS src;
INSERT OR IGNORE INTO elements_meta SELECT * FROM src.elements_meta;
INSERT OR IGNORE INTO element_transforms SELECT * FROM src.element_transforms;
INSERT OR IGNORE INTO element_instances SELECT * FROM src.element_instances;
INSERT OR IGNORE INTO component_geometries SELECT * FROM src.component_geometries;
DETACH src;
SQL
done

TOTAL=$(sqlite3 "$OUT" "SELECT COUNT(*) FROM elements_meta")
SIZE=$(stat -c%s "$OUT" | numfmt --to=iec)
echo "   DONE: $TOTAL elements, $SIZE"
echo

# Step 3: Split if >15K elements
echo "── Running split_db.sh"
"$SCRIPT_DIR/split_db.sh" "$OUT" || true

# Cleanup
rm -rf "$TMP_DIR"
echo
echo "=== Complete. Files in deploy/buildings/: ==="
ls -lh "$ROOT/deploy/buildings"/Clinic_*
