#!/bin/bash
# ============================================================
# BIM Compiler — Outlier Trace Report (post-hoc)
#
# Reads BOTH boxes' outputs after pipeline completes:
#   - Black box: LMP-DIFF outlier lines from pipeline log
#   - White box: TACK LEAF lines from same pipeline log
#   - Output DB: element_ref → ifc_class mapping
#
# Correlates them into a single trace per outlier element,
# so the next session/coder can pick up without re-investigation.
#
# Usage:
#   ./scripts/rosetta_trace.sh <pipeline_log> <output_db> [ref_db]
#
# Output: prints trace report to stdout, saves to logs/trace_<building>.txt
# ============================================================

set -e

if [ $# -lt 2 ]; then
    echo "Usage: $0 <pipeline_log> <output_db> [ref_db]"
    echo "  e.g. $0 'logs/pipeline_DX Duplex_extracted_20260405_164719.log' DAGCompiler/lib/output/ifc2x3_duplex.db DAGCompiler/lib/input/Ifc2x3_Duplex_extracted.db"
    exit 1
fi

LOG="$1"
OUT_DB="$2"
REF_DB="${3:-}"

if [ ! -f "$LOG" ]; then echo "ERROR: Log not found: $LOG"; exit 1; fi
if [ ! -f "$OUT_DB" ]; then echo "ERROR: Output DB not found: $OUT_DB"; exit 1; fi

# Extract building name from log filename
BUILDING=$(basename "$LOG" | sed 's/pipeline_//; s/_extracted.*//; s/_20[0-9]*.*//')
TRACE_FILE="logs/trace_${BUILDING}_$(date +%Y%m%d_%H%M%S).txt"

{
echo "═══════════════════════════════════════════════════════════"
echo "  OUTLIER TRACE REPORT: ${BUILDING}"
echo "  Generated: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  Pipeline log: $LOG"
echo "  Output DB:    $OUT_DB"
echo "  Reference DB: ${REF_DB:-none}"
echo "═══════════════════════════════════════════════════════════"
echo ""

# ── Section 1: Black box summary (from LMP-DIFF log lines) ──
echo "── BLACK BOX: LMP-DIFF Summary ──"
grep 'LMP-DIFF' "$LOG" | grep -E 'SpatialDiff:|Modal shift:|OUTLIER:|DIAGNOSIS:' | sed 's/.*LMP-DIFF */  /'
echo ""

# ── Section 2: Per-class outlier breakdown ──
echo "── BLACK BOX: Outlier Breakdown ──"
grep 'LMP-DIFF.*OUTLIER.*shifted' "$LOG" | sed 's/.*LMP-DIFF */  /'
echo ""
grep 'LMP-DIFF.*DIAGNOSIS' "$LOG" | sed 's/.*LMP-DIFF */  /'
grep 'LMP-DIFF.*sym.*asym' "$LOG" | sed 's/.*LMP-DIFF */  /'
echo ""

# ── Section 3: Outlier ifc_classes from log ──
OUTLIER_CLASSES=$(grep 'LMP-DIFF.*OUTLIER.*shifted' "$LOG" \
    | sed 's/.*OUTLIER \([^:]*\):.*/\1/' | sort -u)

if [ -z "$OUTLIER_CLASSES" ]; then
    echo "  No outliers found — all clean."
    echo ""
else
    # ── Section 4: Output DB element inventory per outlier class ──
    echo "── OUTPUT DB: Elements per outlier class ──"
    for cls in $OUTLIER_CLASSES; do
        count=$(sqlite3 "$OUT_DB" "SELECT count(*) FROM elements_meta WHERE ifc_class='$cls'" 2>/dev/null || echo "?")
        echo "  $cls: $count elements in output"
        # Show element_refs for this class (guids in output)
        sqlite3 "$OUT_DB" "SELECT '    ' || m.guid || '  AABB X=[' || printf('%.3f', r.minX) || ',' || printf('%.3f', r.maxX) || '] Y=[' || printf('%.3f', r.minY) || ',' || printf('%.3f', r.maxY) || '] Z=[' || printf('%.3f', r.minZ) || ',' || printf('%.3f', r.maxZ) || ']' FROM elements_meta m JOIN elements_rtree r ON m.id = r.id WHERE m.ifc_class='$cls' ORDER BY r.minX, r.minY" 2>/dev/null || true
    done
    echo ""

    # ── Section 5: Reference DB element inventory (if provided) ──
    if [ -n "$REF_DB" ] && [ -f "$REF_DB" ]; then
        echo "── REFERENCE DB: Elements per outlier class ──"
        for cls in $OUTLIER_CLASSES; do
            count=$(sqlite3 "$REF_DB" "SELECT count(*) FROM elements_meta WHERE ifc_class='$cls'" 2>/dev/null || echo "?")
            echo "  $cls: $count elements in reference"
            sqlite3 "$REF_DB" "SELECT '    ' || m.guid || '  AABB X=[' || printf('%.3f', r.minX) || ',' || printf('%.3f', r.maxX) || '] Y=[' || printf('%.3f', r.minY) || ',' || printf('%.3f', r.maxY) || '] Z=[' || printf('%.3f', r.minZ) || ',' || printf('%.3f', r.maxZ) || ']' FROM elements_meta m JOIN elements_rtree r ON m.id = r.id WHERE m.ifc_class='$cls' ORDER BY r.minX, r.minY" 2>/dev/null || true
        done
        echo ""

        # ── Section 6: Count match check ──
        echo "── PAIRING QUALITY CHECK ──"
        for cls in $OUTLIER_CLASSES; do
            ref_count=$(sqlite3 "$REF_DB" "SELECT count(*) FROM elements_meta WHERE ifc_class='$cls'" 2>/dev/null || echo "0")
            out_count=$(sqlite3 "$OUT_DB" "SELECT count(*) FROM elements_meta WHERE ifc_class='$cls'" 2>/dev/null || echo "0")
            if [ "$ref_count" != "$out_count" ]; then
                echo "  $cls: ref=$ref_count out=$out_count (MISMATCH — sort-zip pairing will mis-pair)"
            else
                echo "  $cls: ref=$ref_count out=$out_count (count match)"
            fi
        done
        echo ""
    fi

    # ── Section 7: White box TACK for outlier classes ──
    # TACK uses IFC GUIDs (from reference DB) with A_/B_ prefix.
    # Output DB uses compiled element_refs, not IFC GUIDs.
    # Correlation: reference DB guid → grep TACK log for that guid.
    echo "── WHITE BOX: TACK LEAF for outlier class elements ──"
    for cls in $OUTLIER_CLASSES; do
        if [ -n "$REF_DB" ] && [ -f "$REF_DB" ]; then
            refs=$(sqlite3 "$REF_DB" "SELECT guid FROM elements_meta WHERE ifc_class='$cls' ORDER BY guid" 2>/dev/null)
        else
            refs=""
        fi
        ref_count=$(echo "$refs" | grep -c . 2>/dev/null || echo "0")
        # Count TACK LEAF lines matching these GUIDs (TACK uses guid=A_xxx or guid=B_xxx)
        tack_count=0
        if [ -n "$refs" ]; then
            # Count total TACK LEAF hits (use fgrep for $ in IFC GUIDs)
            for ref in $refs; do
                hits=$(grep -cF "guid=A_${ref}" "$LOG" 2>/dev/null) || hits=0
                hits2=$(grep -cF "guid=B_${ref}" "$LOG" 2>/dev/null) || hits2=0
                tack_count=$((tack_count + hits + hits2))
            done
        fi
        echo "  $cls: $ref_count ref elements, $tack_count TACK LEAF hits"
        # Show first 5 elements with their TACK LEAF entries
        shown=0
        for ref in $(echo "$refs" | head -5); do
            tack_lines=$(grep -F "guid=A_${ref}" "$LOG" 2>/dev/null; grep -F "guid=B_${ref}" "$LOG" 2>/dev/null) || true
            # Filter to TACK LEAF lines only
            tack_lines=$(echo "$tack_lines" | grep 'TACK.*LEAF' || true)
            if [ -n "$tack_lines" ]; then
                echo "    ── IFC guid: $ref ──"
                echo "$tack_lines" | sed 's/.*TACK */      /'
            else
                echo "    ── IFC guid: $ref — no TACK LEAF found ──"
            fi
            shown=$((shown + 1))
        done
        if [ "$ref_count" -gt 5 ]; then
            echo "    ... and $((ref_count - 5)) more elements (grep guid=<IFC_GUID> in log)"
        fi
        echo ""
    done
fi

# ── Section 8: Quick action items ──
echo "── ACTION ITEMS ──"
asym=$(grep 'LMP-DIFF.*DIAGNOSIS.*asymmetric' "$LOG" | grep -oP '\d+ asymmetric' | grep -oP '^\d+')
sym=$(grep 'LMP-DIFF.*DIAGNOSIS.*symmetric' "$LOG" | grep -oP '\d+ symmetric' | head -1 | grep -oP '^\d+')
asym=${asym:-0}
sym=${sym:-0}

if [ "$asym" -gt "$sym" ]; then
    echo "  1. FIX BLACK BOX PAIRING FIRST — $asym/$((asym+sym)) outliers are asymmetric"
    echo "     SpatialDiff.diffByPosition() sorts by (minX, minY, minZ) then zips."
    echo "     Furniture clusters at varied positions → wrong pairs."
    echo "     Consider identity-based matching (element_ref) for IfcFurnishingElement."
    echo "  2. THEN re-run trace to see remaining symmetric outliers (real errors)."
elif [ "$sym" -gt 0 ]; then
    echo "  1. $sym symmetric outliers = real position errors."
    echo "     Trace each through TACK LEAF (anchor + offset) vs reference AABB."
    echo "     Root cause likely in VerbFactorizer offsets or SET BOM LBD origin."
fi
if [ "$asym" -eq 0 ] && [ "$sym" -eq 0 ]; then
    echo "  No outliers — spatial accuracy is clean."
fi
echo ""

} | tee "$TRACE_FILE"

echo "═══ Trace saved to: $TRACE_FILE ═══"
