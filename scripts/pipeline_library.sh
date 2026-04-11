#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# pipeline_library.sh — Extract → Bake → Prove → Ready for Library button
#
# Usage:
#   ./scripts/pipeline_library.sh Hospital
#   ./scripts/pipeline_library.sh SampleHouse
#   ./scripts/pipeline_library.sh Hospital --fine     # verbose per-element
#
# Steps:
#   [1/4] EXTRACT   — per-discipline extraction → merged DB + library.db
#   [2/4] PROVE     — standalone orientation proof (pure math, no Blender)
#   [3/4] BAKE      — library.db → library.blend (one-time, Blender CLI)
#   [4/4] SUMMARY   — counts, sizes, proof results
#
# Stops on first failure. Log saved next to output DB.
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

BUILDING="${1:?Usage: $0 <building> [--fine]}"
FINE_FLAG=""
[[ "${2:-}" == "--fine" ]] && FINE_FLAG="--fine"

# ── Paths ──
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBRARY_DB="$PROJECT_ROOT/library/component_library.db"
LIBRARY_BLEND="$PROJECT_ROOT/library/library.blend"
PROOF_SCRIPT="$PROJECT_ROOT/scripts/test_orientation_proof.py"
BAKE_SCRIPT="$PROJECT_ROOT/scripts/bake_library_blend.py"
EXTRACT_SCRIPT="$PROJECT_ROOT/scripts/extract_merge_disciplines.py"
EXTRACTOR="$PROJECT_ROOT/DAGCompiler/python/extractIFCtoDB.py"

# ── Resolve building paths ──
case "$BUILDING" in
    Hospital)
        IFC_DIR="$PROJECT_ROOT/DAGCompiler/lib/input/IFC/UNMERGED"
        IFC_PATTERN="Hospital_IFC4_*.ifc"
        OUTPUT_DB="$PROJECT_ROOT/DAGCompiler/lib/input/Hospital_extracted.db"
        ;;
    SampleHouse|SH)
        IFC_DIR="$PROJECT_ROOT/DAGCompiler/lib/input/IFC"
        IFC_PATTERN="Ifc4_SampleHouse.ifc"
        OUTPUT_DB="$PROJECT_ROOT/DAGCompiler/lib/input/Ifc4_SampleHouse_extracted.db"
        ;;
    *)
        # Generic: look for IFC files matching the name
        IFC_DIR="$PROJECT_ROOT/DAGCompiler/lib/input/IFC/UNMERGED"
        IFC_PATTERN="${BUILDING}_*.ifc"
        OUTPUT_DB="$PROJECT_ROOT/DAGCompiler/lib/input/${BUILDING}_extracted.db"
        if ! ls "$IFC_DIR"/$IFC_PATTERN >/dev/null 2>&1; then
            IFC_DIR="$PROJECT_ROOT/DAGCompiler/lib/input/IFC"
            IFC_PATTERN="${BUILDING}*.ifc"
        fi
        ;;
esac

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG="$PROJECT_ROOT/DAGCompiler/lib/input/${BUILDING}_pipeline_${TIMESTAMP}.log"

# ── Logging ──
exec > >(tee -a "$LOG") 2>&1

echo "============================================================"
echo "PIPELINE: $BUILDING — $(date)"
echo "  IFC:     $IFC_DIR/$IFC_PATTERN"
echo "  Output:  $OUTPUT_DB"
echo "  Library: $LIBRARY_DB"
echo "  Blend:   $LIBRARY_BLEND"
echo "  Log:     $LOG"
echo "============================================================"

IFC_COUNT=$(ls "$IFC_DIR"/$IFC_PATTERN 2>/dev/null | wc -l)
if [ "$IFC_COUNT" -eq 0 ]; then
    echo "FATAL: No IFC files matching $IFC_PATTERN in $IFC_DIR"
    exit 1
fi
echo "  IFC files: $IFC_COUNT"
echo ""

# ── [1/4] EXTRACT ──
echo "============================================================"
echo "[1/4] EXTRACT — $IFC_COUNT discipline(s) → $OUTPUT_DB"
echo "============================================================"
T1_START=$(date +%s)

if [ "$IFC_COUNT" -gt 1 ]; then
    python3 "$EXTRACT_SCRIPT" \
        --ifc-dir "$IFC_DIR" \
        --pattern "$IFC_PATTERN" \
        --output "$OUTPUT_DB" \
        --library "$LIBRARY_DB"
else
    # Single file — use direct extractor
    IFC_FILE=$(ls "$IFC_DIR"/$IFC_PATTERN | head -1)
    python3 "$EXTRACTOR" \
        --ifc "$IFC_FILE" \
        -o "$OUTPUT_DB" \
        --library "$LIBRARY_DB"
fi

T1_END=$(date +%s)
T1_ELAPSED=$((T1_END - T1_START))

ELEM_COUNT=$(python3 -c "import sqlite3; print(sqlite3.connect('$OUTPUT_DB').execute('SELECT COUNT(*) FROM element_transforms').fetchone()[0])")
HASH_COUNT=$(python3 -c "import sqlite3; print(sqlite3.connect('$OUTPUT_DB').execute('SELECT COUNT(DISTINCT geometry_hash) FROM element_instances').fetchone()[0])")
LIB_COUNT=$(python3 -c "import sqlite3; print(sqlite3.connect('$LIBRARY_DB').execute('SELECT COUNT(*) FROM component_geometries WHERE vertices IS NOT NULL').fetchone()[0])")
DB_SIZE=$(du -h "$OUTPUT_DB" | cut -f1)

echo ""
echo "  §DONE EXTRACT  ${ELEM_COUNT} elements, ${HASH_COUNT} unique hashes, ${DB_SIZE} — ${T1_ELAPSED}s"
echo "  §DONE LIBRARY   ${LIB_COUNT} meshes in component_library.db"
echo ""

# ── [2/4] PROVE ──
echo "============================================================"
echo "[2/4] PROVE — orientation proof (pure math, no Blender)"
echo "============================================================"
T2_START=$(date +%s)

python3 "$PROOF_SCRIPT" \
    --db "$OUTPUT_DB" \
    --library "$LIBRARY_DB" \
    --max 500

T2_END=$(date +%s)
T2_ELAPSED=$((T2_END - T2_START))
echo ""
echo "  §DONE PROVE  ${T2_ELAPSED}s"
echo ""

# ── [3/4] BAKE ──
echo "============================================================"
echo "[3/4] BAKE — library.db → library.blend"
echo "============================================================"
T3_START=$(date +%s)

blender --background --python "$BAKE_SCRIPT" -- \
    --library "$LIBRARY_DB" \
    --output "$LIBRARY_BLEND"

T3_END=$(date +%s)
T3_ELAPSED=$((T3_END - T3_START))
BLEND_SIZE=$(du -h "$LIBRARY_BLEND" | cut -f1)

echo ""
echo "  §DONE BAKE  ${LIB_COUNT} meshes → ${BLEND_SIZE} — ${T3_ELAPSED}s"
echo ""

# ── [4/4] SUMMARY ──
echo "============================================================"
echo "[4/4] SUMMARY"
echo "============================================================"
TOTAL_ELAPSED=$((T3_END - T1_START))
echo "  Building:      $BUILDING"
echo "  Elements:      $ELEM_COUNT"
echo "  Unique meshes: $HASH_COUNT"
echo "  Library total: $LIB_COUNT meshes"
echo "  Extracted DB:  $DB_SIZE ($OUTPUT_DB)"
echo "  Library blend: $BLEND_SIZE ($LIBRARY_BLEND)"
echo "  Pipeline time: ${TOTAL_ELAPSED}s (extract=${T1_ELAPSED}s prove=${T2_ELAPSED}s bake=${T3_ELAPSED}s)"
echo "  Log:           $LOG"
echo ""
echo "§PROOF PIPELINE PASS"
echo ""
echo "Ready: open Blender → set DB path → click [Library]"
echo "============================================================"
