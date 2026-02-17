#!/bin/bash
# BIM Intent Compiler - Full Cycle Script
# Compiles DSL, exports 3D + 2D, opens viewer
#
# Usage: ./scripts/full_cycle.sh examples/TB-LKTN.bim
#        ./scripts/full_cycle.sh  (runs all three test buildings)

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "========================================"
echo "BIM INTENT COMPILER - FULL CYCLE"
echo "========================================"

if [ -n "$1" ]; then
    # Single DSL file
    DSL_FILE="$1"
    BASE_NAME=$(basename "$DSL_FILE" .bim | tr '[:upper:]' '[:lower:]' | tr '-' '_')

    echo "Compiling: $DSL_FILE"
    mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.BuildingCompiler" \
        -Dexec.args="$DSL_FILE output/${BASE_NAME}.db" -q

    echo "Exporting glTF..."
    python3 scripts/export_to_gltf.py "output/${BASE_NAME}.db" "output/${BASE_NAME}.glb"

    echo "Generating 2D drawings..."
    python3 scripts/export_2d_drawings.py "output/${BASE_NAME}.db" "output/drawings_${BASE_NAME}"

    echo ""
    echo "DONE! Outputs:"
    echo "  3D Model: output/${BASE_NAME}.glb"
    echo "  2D Drawings: output/drawings_${BASE_NAME}/drawing_index.html"
    echo "  Witness: output/${BASE_NAME}_witness.json"
else
    # Run all test buildings
    echo ""
    echo "--- TB-LKTN (Single-storey house) ---"
    mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q 2>&1 | tail -5
    python3 scripts/export_to_gltf.py output/tb_lktn.db output/tb_lktn.glb
    python3 scripts/export_2d_drawings.py output/tb_lktn.db output/drawings_tb_lktn

    echo ""
    echo "--- TB-LKTN-2S (Two-storey house) ---"
    mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.TBLKTN2SEndToEndTest" -q 2>&1 | tail -5
    python3 scripts/export_to_gltf.py output/tb_lktn_2s.db output/tb_lktn_2s.glb
    python3 scripts/export_2d_drawings.py output/tb_lktn_2s.db output/drawings_tb_lktn_2s

    echo ""
    echo "--- Sekolah Kebangsaan (Two-storey school) ---"
    mvn exec:java -pl DAGCompiler -Dexec.mainClass="com.bim.compiler.dsl.SchoolEndToEndTest" -q 2>&1 | tail -5
    python3 scripts/export_to_gltf.py output/sekolah_kebangsaan.db output/sekolah_kebangsaan.glb
    python3 scripts/export_2d_drawings.py output/sekolah_kebangsaan.db output/drawings_school
fi

echo ""
echo "========================================"
echo "ALL EXPORTS COMPLETE"
echo "========================================"
echo ""
echo "To view:"
echo "  python3 -m http.server 8080"
echo "  Open: http://localhost:8080/viewer/"
echo "  Open: http://localhost:8080/output/drawings_tb_lktn/drawing_index.html"
echo ""
