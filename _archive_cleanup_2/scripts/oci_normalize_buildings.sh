#!/usr/bin/env bash
# Download each SERVED building DB from OCI bim-ootb, normalize storey (strip Revit reference-plane
# levels), and upload back IF it had pollution. Operates on the actual OCI bytes — never overwrites
# concurrent geometry changes (only the storey field is touched). Served = _meta.db for split
# buildings (those that have one) else _extracted.db. _geo/_BOM/_positions/city_index are skipped
# (no storey). §-logs each step; reads the log = the evidence.
set -u
cd /home/red1/bim-compiler
BUCKET=bim-ootb
TMP=$(mktemp -d)
LOG=logs/oci_normalize.log
mkdir -p logs
: > "$LOG"

echo "§OCI_NORMALIZE start $(date -u +%H:%M:%S)" | tee -a "$LOG"

# all .db object names in buildings/
mapfile -t ALL < <(oci os object list --bucket-name "$BUCKET" --prefix buildings/ \
  --query 'data[?ends_with(name, `.db`)].name' --output json 2>/dev/null | grep -oE 'buildings/[^"]+\.db')

# build the SERVED set
declare -A META   # base -> has _meta
for n in "${ALL[@]}"; do case "$n" in *_meta.db) META["${n%_meta.db}"]=1;; esac; done
SERVED=()
for n in "${ALL[@]}"; do
  case "$n" in
    *_geo.db|*_BOM.db|*city_index*) continue;;   # no elements_meta / no storey
    *_meta.db|*_extracted.db) SERVED+=("$n");;     # ALL meta + extracted (either can be loaded by URL)
  esac
done
echo "§OCI_SERVED count=${#SERVED[@]}: ${SERVED[*]}" | tee -a "$LOG"

_poll() { sqlite3 "$1" "SELECT COUNT(*) FROM elements_meta WHERE storey LIKE '%Ceiling%' OR storey LIKE '%TOS%';" 2>/dev/null || echo ERR; }

up=0; skip=0; fail=0
for name in "${SERVED[@]}"; do
  raw="$TMP/$(basename "$name").raw"   # bytes as stored on OCI (may be gzip)
  f="$TMP/$(basename "$name")"          # plain sqlite working copy
  if ! oci os object get --bucket-name "$BUCKET" --name "$name" --file "$raw" >/dev/null 2>&1; then
    echo "§OCI_GET_FAIL $name" | tee -a "$LOG"; fail=$((fail+1)); continue
  fi
  # The served _meta.db files are stored gzip-compressed (Content-Encoding: gzip). `oci os object get`
  # returns the RAW stored bytes (no auto-decompress), so sqlite3 must read the decompressed copy or it
  # sees "not a database" and the building is wrongly skipped. Detect by magic, decompress, remember.
  was_gz=0
  if [ "$(head -c2 "$raw" | xxd -p)" = "1f8b" ]; then
    if ! gzip -dc "$raw" > "$f" 2>/dev/null; then echo "§OCI_GUNZIP_FAIL $name" | tee -a "$LOG"; fail=$((fail+1)); rm -f "$raw"; continue; fi
    was_gz=1
  else
    mv "$raw" "$f"
  fi
  poll=$(_poll "$f")
  if [ "$poll" = "0" ]; then echo "§OCI_CLEAN $name (no pollution)" | tee -a "$LOG"; skip=$((skip+1)); rm -f "$f" "$raw"; continue; fi
  if [ "$poll" = "ERR" ]; then echo "§OCI_NO_META $name (no elements_meta — skip)" | tee -a "$LOG"; skip=$((skip+1)); rm -f "$f" "$raw"; continue; fi
  python3 scripts/normalize_storey.py "$f" >>"$LOG" 2>&1
  after=$(_poll "$f")
  if [ "$after" != "0" ]; then echo "§OCI_NORM_INCOMPLETE $name residual=$after — NOT uploading" | tee -a "$LOG"; fail=$((fail+1)); rm -f "$f" "$raw"; continue; fi
  # Re-upload in the SAME wire form it arrived in: gzip back + set Content-Encoding so the viewer's
  # fetch still auto-decompresses. Plain DBs upload as-is.
  if [ "$was_gz" = "1" ]; then
    gzip -c "$f" > "$raw"
    ok=$(oci os object put --bucket-name "$BUCKET" --file "$raw" --name "$name" \
         --content-type application/octet-stream --content-encoding gzip --force >/dev/null 2>&1 && echo Y || echo N)
  else
    ok=$(oci os object put --bucket-name "$BUCKET" --file "$f" --name "$name" \
         --content-type application/octet-stream --force >/dev/null 2>&1 && echo Y || echo N)
  fi
  if [ "$ok" = "Y" ]; then
    echo "§OCI_UPLOAD $name polluted=$poll -> 0 OK (gzip=$was_gz)" | tee -a "$LOG"; up=$((up+1))
  else
    echo "§OCI_PUT_FAIL $name" | tee -a "$LOG"; fail=$((fail+1))
  fi
  rm -f "$f" "$raw"
done
echo "§OCI_NORMALIZE done uploaded=$up cleaned_already=$skip failed=$fail $(date -u +%H:%M:%S)" | tee -a "$LOG"
rm -rf "$TMP"
[ "$fail" -eq 0 ]
