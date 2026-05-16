#!/bin/bash
# split_bucket_buildings.sh — Download large DBs from bucket, split, re-upload
# Buildings > 50MB get _meta.db + _geo.db for range-request streaming
set -e

BUCKET="bim-ootb-live"
TMP="/tmp/bim_split"
mkdir -p "$TMP"

BUILDINGS=(
  "Terminal"
  "Hospital"
  "Clinic"
  "HHS_Office_Federated"
  "WBDG_Office"
)

for BLD in "${BUILDINGS[@]}"; do
  DB="buildings/${BLD}_extracted.db"
  LOCAL="$TMP/${BLD}_extracted.db"
  META="$TMP/${BLD}_meta.db"
  GEO="$TMP/${BLD}_geo.db"

  echo "================================================"
  echo "Processing: $BLD"
  echo "================================================"

  # Check if already split
  EXISTS=$(oci os object head --bucket-name "$BUCKET" --name "buildings/${BLD}_meta.db" 2>&1 | grep -c "200" || true)
  if [ "$EXISTS" -gt 0 ]; then
    echo "  SKIP — ${BLD}_meta.db already exists in bucket"
    continue
  fi

  # Download
  echo "  Downloading $DB..."
  oci os object get --bucket-name "$BUCKET" --name "$DB" --file "$LOCAL"
  SIZE=$(stat -c%s "$LOCAL" 2>/dev/null || stat -f%z "$LOCAL")
  echo "  Downloaded: $((SIZE / 1024 / 1024))MB"

  # Split
  echo "  Splitting..."
  /home/red1/bim-compiler/scripts/split_db.sh "$LOCAL"

  # Upload
  echo "  Uploading ${BLD}_meta.db..."
  oci os object put --bucket-name "$BUCKET" --name "buildings/${BLD}_meta.db" \
    --file "$META" --content-type "application/octet-stream" --force

  echo "  Uploading ${BLD}_geo.db..."
  oci os object put --bucket-name "$BUCKET" --name "buildings/${BLD}_geo.db" \
    --file "$GEO" --content-type "application/octet-stream" --force

  # Cleanup
  rm -f "$LOCAL" "$META" "$GEO"
  echo "  DONE: $BLD"
done

echo ""
echo "All splits complete."
