#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# bake_all_sandbox.sh — Populate library with all sandbox buildings
#
# Two paths:
#   HAS_BLOBS  → copy BLOBs from extracted DB → component_library.db (fast, ~1s)
#   NO_BLOBS   → re-extract from IFC with --library (slow, ~30s-5min each)
#
# Then rebake library.blend once at the end.
#
# Usage:  ./scripts/bake_all_sandbox.sh
# ──────────────────────────────────────────────────────────────────
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIBRARY_DB="$PROJECT_ROOT/library/component_library.db"
LIBRARY_BLEND="$PROJECT_ROOT/library/library.blend"
EXTRACTOR="$PROJECT_ROOT/DAGCompiler/python/extractIFCtoDB.py"
MERGE_EXTRACTOR="$PROJECT_ROOT/scripts/extract_merge_disciplines.py"
BAKE_SCRIPT="$PROJECT_ROOT/scripts/bake_library_blend.py"
IFC_DIR="$PROJECT_ROOT/DAGCompiler/lib/input/IFC"
IFC_UNMERGED="$PROJECT_ROOT/DAGCompiler/lib/input/IFC/UNMERGED"
INPUT_DIR="$PROJECT_ROOT/DAGCompiler/lib/input"
LOG="$INPUT_DIR/bake_all_sandbox_$(date +%Y%m%d_%H%M%S).log"

exec > >(tee -a "$LOG") 2>&1

echo "============================================================"
echo "BAKE ALL SANDBOX BUILDINGS — $(date)"
echo "  Library DB:    $LIBRARY_DB"
echo "  Library blend: $LIBRARY_BLEND"
echo "  Log:           $LOG"
echo "============================================================"

PRE_COUNT=$(python3 -c "import sqlite3; print(sqlite3.connect('$LIBRARY_DB').execute('SELECT COUNT(*) FROM component_geometries WHERE vertices IS NOT NULL').fetchone()[0])")
echo "  Library pre-count: $PRE_COUNT meshes"
echo ""

COPIED=0
EXTRACTED=0
FAILED=0

# ──────────────────────────────────────────────────────────────────
# PATH 1: HAS_BLOBS — copy from extracted DB → component_library.db
# ──────────────────────────────────────────────────────────────────
echo "============================================================"
echo "PATH 1: COPY BLOBS (extracted DB → library)"
echo "============================================================"

HAS_BLOBS_BUILDINGS=(
    Schependomlaan SampleCastle Molio BimWhale_Basic BimWhale_Large
    BimWhale_Tall Ifc2x3_AC11Institute Jesse HospitalAuckland
    AC9_HausGH AC90_Niedriha AC90_Jasmin Ifc4_FZKHaus Esplanades
)

for bldg in "${HAS_BLOBS_BUILDINGS[@]}"; do
    db="$INPUT_DIR/${bldg}_extracted.db"
    if [ ! -f "$db" ]; then
        echo "  §SKIP $bldg — no extracted DB"
        continue
    fi

    T_START=$(date +%s)
    # Copy BLOBs from extracted.base_geometries → library.component_geometries
    # base_geometries has no normals column — insert NULL for normals
    python3 -c "
import sqlite3

src = sqlite3.connect('$db')
lib = sqlite3.connect('$LIBRARY_DB', timeout=30)
lib.execute('PRAGMA journal_mode=WAL')

rows = src.execute('''
    SELECT geometry_hash, vertices, faces, vertex_count, face_count
    FROM base_geometries WHERE vertices IS NOT NULL
''').fetchall()

inserted = 0
for r in rows:
    try:
        lib.execute('''
            INSERT OR IGNORE INTO component_geometries
            (geometry_hash, vertices, faces, normals, vertex_count, face_count)
            VALUES (?, ?, ?, NULL, ?, ?)
        ''', r)
        inserted += 1
    except Exception as e:
        pass

lib.commit()
lib.close()
src.close()
print(f'  \u00a7DONE {\"$bldg\":30s} {inserted:>5} / {len(rows)} BLOBs copied')
" || { echo "  §FAIL $bldg"; ((FAILED++)) || true; continue; }

    T_END=$(date +%s)
    ((COPIED++)) || true
done

echo ""

# ──────────────────────────────────────────────────────────────────
# PATH 2: NO_BLOBS — re-extract from IFC with --library
# ──────────────────────────────────────────────────────────────────
echo "============================================================"
echo "PATH 2: RE-EXTRACT (IFC → extracted DB + library)"
echo "============================================================"

# Format: NAME|IFC_PATTERN|IFC_LOCATION|MODE
NO_BLOBS_BUILDINGS=(
    "BimWhale_Advanced|BimWhale_AdvancedProject.ifc|IFC|single"
    "Duplex|Ifc2x3_Duplex_Federated.ifc|IFC|single"
    "SmileyWest|Smiley_West_upgraded.ifc|IFC|single"
    "VogelGesamt|Vogel_Gesamt_upgraded.ifc|IFC|single"
    "SampleHouse|Ifc4_SampleHouse.ifc|IFC|single"
    "HospitalGarage|HospitalGarage_IFC4.ifc|IFC|single"
    "HITOS|HITOS_070308.ifc|IFC|single"
    "WBDG_Office|WBDG_Office_*_IFC2x3.ifc|IFC|merge"
    "Ifc4_Revit|Ifc4_Revit_*.ifc|UNMERGED|merge"
    "LTU_AHouse|LTU_AHouse_*.ifc|UNMERGED|merge"
    "HHS_Office|opensourceBIM_HHS_Office_*.ifc|IFC|merge"
)

for entry in "${NO_BLOBS_BUILDINGS[@]}"; do
    IFS='|' read -r NAME PATTERN LOC MODE <<< "$entry"

    if [ "$LOC" = "UNMERGED" ]; then
        SRC_DIR="$IFC_UNMERGED"
    else
        SRC_DIR="$IFC_DIR"
    fi

    OUTPUT_DB="$INPUT_DIR/${NAME}_extracted.db"

    if ! ls "$SRC_DIR"/$PATTERN >/dev/null 2>&1; then
        echo "  §SKIP $NAME — no IFC: $SRC_DIR/$PATTERN"
        ((FAILED++)) || true
        continue
    fi

    echo "  extracting $NAME ($MODE)..."
    T_START=$(date +%s)

    if [ "$MODE" = "merge" ]; then
        python3 "$MERGE_EXTRACTOR" \
            --ifc-dir "$SRC_DIR" \
            --pattern "$PATTERN" \
            --output "$OUTPUT_DB" \
            --library "$LIBRARY_DB" || {
            echo "  §FAIL $NAME"
            ((FAILED++)) || true
            continue
        }
    else
        IFC_FILE=$(ls "$SRC_DIR"/$PATTERN | head -1)
        python3 "$EXTRACTOR" \
            --ifc "$IFC_FILE" \
            -o "$OUTPUT_DB" \
            --library "$LIBRARY_DB" || {
            echo "  §FAIL $NAME"
            ((FAILED++)) || true
            continue
        }
    fi

    T_END=$(date +%s)
    ELEMS=$(python3 -c "import sqlite3; print(sqlite3.connect('$OUTPUT_DB').execute('SELECT COUNT(*) FROM element_transforms').fetchone()[0])" 2>/dev/null || echo "?")
    echo "  §DONE ${NAME} — ${ELEMS} elements, $((T_END-T_START))s"
    ((EXTRACTED++)) || true
done

echo ""

# ──────────────────────────────────────────────────────────────────
# POST-COUNT + BAKE
# ──────────────────────────────────────────────────────────────────
POST_COUNT=$(python3 -c "import sqlite3; print(sqlite3.connect('$LIBRARY_DB').execute('SELECT COUNT(*) FROM component_geometries WHERE vertices IS NOT NULL').fetchone()[0])")
NEW_MESHES=$((POST_COUNT - PRE_COUNT))
echo "============================================================"
echo "Library: $PRE_COUNT → $POST_COUNT meshes (+$NEW_MESHES new)"
echo "  Copied:    $COPIED buildings (BLOB transfer)"
echo "  Extracted: $EXTRACTED buildings (IFC re-extract)"
echo "  Failed:    $FAILED"
echo "============================================================"
echo ""

echo "============================================================"
echo "BAKE — library.db → library.blend"
echo "============================================================"
T_BAKE_START=$(date +%s)

blender --background --python "$BAKE_SCRIPT" -- \
    --library "$LIBRARY_DB" \
    --output "$LIBRARY_BLEND"

T_BAKE_END=$(date +%s)
BLEND_SIZE=$(du -h "$LIBRARY_BLEND" | cut -f1)

echo ""
echo "============================================================"
echo "§PROOF BAKE_ALL_SANDBOX"
echo "  Library:    $POST_COUNT meshes (+$NEW_MESHES new)"
echo "  Blend:      $BLEND_SIZE"
echo "  Copied:     $COPIED buildings"
echo "  Extracted:  $EXTRACTED buildings"
echo "  Failed:     $FAILED"
echo "  Bake time:  $((T_BAKE_END - T_BAKE_START))s"
echo "  Log:        $LOG"
echo "============================================================"
