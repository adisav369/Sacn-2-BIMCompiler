#!/bin/bash
# oci_bucket_copy.sh — Copy objects between OCI buckets (deploy/rollback)
#
# Uses download+upload (no service policy needed, works on Always Free tier).
#
# Usage:
#   bash scripts/oci_bucket_copy.sh <source-bucket> <dest-bucket> [prefix]
#
# Examples:
#   bash scripts/oci_bucket_copy.sh bim-ootb-full bim-ootb-backup sandbox/   # snapshot
#   bash scripts/oci_bucket_copy.sh bim-ootb-dev bim-ootb-full sandbox/      # deploy
#   bash scripts/oci_bucket_copy.sh bim-ootb-backup bim-ootb-full sandbox/   # rollback

set -euo pipefail

SRC="${1:?Usage: oci_bucket_copy.sh <source> <dest> [prefix]}"
DST="${2:?Usage: oci_bucket_copy.sh <source> <dest> [prefix]}"
PREFIX="${3:-}"
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo "═══ OCI Bucket Copy ═══"
echo "  FROM: $SRC"
echo "  TO:   $DST"
echo "  PREFIX: ${PREFIX:-<all>}"
echo ""

# List objects in source bucket
OBJECTS=$(oci os object list --bucket-name "$SRC" \
  ${PREFIX:+--prefix "$PREFIX"} \
  --query 'data[*].name' --output json 2>/dev/null | tr -d '[]" ' | tr ',' '\n' | grep -v '^$')

if [ -z "$OBJECTS" ]; then
  echo "ERROR: No objects found in $SRC with prefix '$PREFIX'"
  exit 1
fi

COUNT=$(echo "$OBJECTS" | wc -l)
echo "  Objects to copy: $COUNT"
echo ""

COPIED=0
FAILED=0
for obj in $OBJECTS; do
  # Determine content type
  ext="${obj##*.}"
  ct="application/octet-stream"
  case "$ext" in
    js)   ct="application/javascript" ;;
    html) ct="text/html" ;;
    json) ct="application/json" ;;
    db)   ct="application/octet-stream" ;;
  esac

  # Download from source
  local_file="$TMPDIR/$(basename "$obj")"
  if oci os object get --bucket-name "$SRC" --name "$obj" --file "$local_file" 2>/dev/null; then
    # Upload to destination
    if oci os object put --bucket-name "$DST" --name "$obj" --file "$local_file" \
      --content-type "$ct" --force 2>/dev/null; then
      echo "  ✓ $obj"
      COPIED=$((COPIED + 1))
    else
      echo "  ✗ $obj (upload failed)"
      FAILED=$((FAILED + 1))
    fi
  else
    echo "  ✗ $obj (download failed)"
    FAILED=$((FAILED + 1))
  fi
  rm -f "$local_file"
done

echo ""
echo "═══ DONE: $COPIED/$COUNT copied, $FAILED failed ═══"

if [ "$FAILED" -gt 0 ]; then
  echo "WARNING: $FAILED objects failed. Check OCI permissions."
  exit 1
fi
