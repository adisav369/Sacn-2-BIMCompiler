#!/bin/bash
# minify_viewer.sh — Minify viewer JS files for OCI deployment
# Uses terser (must be installed: npm i -g terser)
# Output: deploy/min/ (same filenames, minified)
#
# Usage: bash scripts/minify_viewer.sh
#        bash scripts/minify_viewer.sh --upload full   # minify + upload to bim-ootb-full
#        bash scripts/minify_viewer.sh --upload live   # minify + upload to bim-ootb-live

set -euo pipefail
cd "$(dirname "$0")/.."

SRC="deploy/dev"
OUT="deploy/min"
BUCKET=""

if [[ "${1:-}" == "--upload" ]]; then
  BUCKET="bim-ootb-${2:?Usage: --upload <full|live|dev>}"
fi

# Viewer JS files (load order matches index.html)
FILES="config.js scene.js helpers.js streaming.js panels.js tools.js picking.js
       tour.js measure.js sitecam.js issues.js excel.js walk.js navigate.js
       city.js rates.js locale_loader.js nlp.js main.js sw.js
       import.js import_db_builder.js import_worker.js mesh_import_worker.js
       ifc_export_worker.js semantic_enrichment.js scene_to_db.js
       section_cut.js elevation.js grid_dims.js diff.js variation_order.js
       dxf_export.js loader.js"

mkdir -p "$OUT"

TOTAL_ORIG=0
TOTAL_MIN=0

echo "═══ Minify Viewer JS ═══"
for f in $FILES; do
  src="$SRC/$f"
  # Follow symlinks
  [ -L "$src" ] && src=$(readlink -f "$src")
  [ ! -f "$src" ] && continue

  dst="$OUT/$f"
  orig=$(wc -c < "$src")
  terser "$src" -c -m --output "$dst" 2>/dev/null
  min=$(wc -c < "$dst")
  pct=$(( (orig - min) * 100 / (orig > 0 ? orig : 1) ))
  printf "  %-28s %6d → %6d  (%2d%%)\n" "$f" "$orig" "$min" "$pct"
  TOTAL_ORIG=$((TOTAL_ORIG + orig))
  TOTAL_MIN=$((TOTAL_MIN + min))
done

echo ""
printf "  TOTAL: %d → %d bytes (%.0f%% reduction)\n" \
  "$TOTAL_ORIG" "$TOTAL_MIN" "$(echo "scale=1; ($TOTAL_ORIG - $TOTAL_MIN) * 100 / $TOTAL_ORIG" | bc)"

# Upload if requested
if [ -n "$BUCKET" ]; then
  echo ""
  echo "═══ Upload to $BUCKET ═══"
  for f in $FILES; do
    [ ! -f "$OUT/$f" ] && continue
    oci os object put --bucket-name "$BUCKET" --name "sandbox/$f" \
      --file "$OUT/$f" --content-type "application/javascript" --force 2>/dev/null
    echo "  ✓ sandbox/$f"
  done
  echo "═══ DONE ═══"
fi
