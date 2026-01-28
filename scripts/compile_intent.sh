#!/bin/bash
#
# Phase 20: Intent to IFC Pipeline
#
# Usage: ./compile_intent.sh "3 bedroom house" [output_dir]
#

set -e

INTENT="$1"
OUTPUT_DIR="${2:-output/intent}"

if [ -z "$INTENT" ]; then
    echo "Usage: $0 \"intent text\" [output_dir]"
    echo ""
    echo "Examples:"
    echo "  $0 \"2 bedroom apartment\""
    echo "  $0 \"3 bedroom house with ensuite\" output/my_house"
    echo "  $0 \"large 4 bedroom townhouse\""
    exit 1
fi

echo "============================================================"
echo "INTENT TO IFC PIPELINE"
echo "============================================================"
echo "Intent: $INTENT"
echo "Output: $OUTPUT_DIR"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Step 1: NL → JSON
echo "Step 1: Parsing intent..."
source venv/bin/activate
python scripts/intent_resolver.py "$INTENT" "$OUTPUT_DIR/spec.json"

echo ""
echo "Step 2: JSON → DSL → DB..."
# Step 2: JSON → DSL → DB
mvn exec:java -Dexec.mainClass="com.bim.compiler.dsl.IntentCompiler" \
    -Dexec.args="$OUTPUT_DIR/spec.json $OUTPUT_DIR" -q

echo ""
echo "Step 3: DB → IFC..."
# Step 3: DB → IFC
python scripts/export_building_to_ifc.py \
    "$OUTPUT_DIR/building.db" "$OUTPUT_DIR/building.ifc"

echo ""
echo "============================================================"
echo "COMPLETE"
echo "============================================================"
echo "Generated files:"
echo "  $OUTPUT_DIR/spec.json     - Intent spec"
echo "  $OUTPUT_DIR/building.bim  - DSL"
echo "  $OUTPUT_DIR/building.db   - Database"
echo "  $OUTPUT_DIR/building.ifc  - IFC model"
echo ""
echo "View in Blender: bonsai && File > Import > IFC > $OUTPUT_DIR/building.ifc"
