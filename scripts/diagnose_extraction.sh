#!/usr/bin/env bash
# ============================================================
# diagnose_extraction.sh — Diagnose why a building fails QA
#
# When IFCtoBOM aborts with "N extraction LEAFs vs M extracted (delta=-X)",
# this script shows exactly which elements were dropped and why.
#
# Usage:
#   ./scripts/diagnose_extraction.sh classify_rm.yaml
#
# What it does:
#   1. Checks extracted DB exists and has elements
#   2. Shows element distribution by IFC class
#   3. Identifies unclassified elements (no product mapping)
#   4. Suggests alias entries to close the gap
#
# Prerequisites:
#   - Extracted DB must exist: DAGCompiler/lib/input/{building_type}_extracted.db
#   - If missing, run: python3 tools/extract.py --to reference <IFC> -o <extracted.db>
#   - For multi-discipline IFC, merge first: python3 tools/federation_preprocessor.py
#     (see docs/RevitAnalysis.md)
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

if [ $# -lt 1 ]; then
    echo "Usage: $0 <classify_yaml>"
    echo "Example: $0 classify_rm.yaml"
    exit 1
fi

YAML_FILE="$1"
# Resolve YAML path
if [ ! -f "$YAML_FILE" ]; then
    YAML_FILE="IFCtoBOM/src/main/resources/$YAML_FILE"
fi
if [ ! -f "$YAML_FILE" ]; then
    echo "ERROR: YAML not found: $1"
    exit 1
fi

# Parse building_type from YAML
BUILDING_TYPE=$(grep 'building_type:' "$YAML_FILE" | head -1 | sed 's/.*building_type: *//' | tr -d '[:space:]')
PREFIX=$(grep 'prefix:' "$YAML_FILE" | head -1 | sed 's/.*prefix: *//' | tr -d '[:space:]')

echo "════════════════════════════════════════"
echo "  EXTRACTION DIAGNOSIS: ${PREFIX} (${BUILDING_TYPE})"
echo "════════════════════════════════════════"
echo ""

# Step 1: Check extracted DB
EXTRACTED_DB="DAGCompiler/lib/input/${BUILDING_TYPE}_extracted.db"
if [ ! -f "$EXTRACTED_DB" ] || [ ! -s "$EXTRACTED_DB" ]; then
    echo "  PROBLEM: Extracted DB missing or empty: ${EXTRACTED_DB}"
    echo ""
    echo "  TO FIX:"
    echo "    1. Find the IFC file in DAGCompiler/lib/input/IFC/"
    echo "    2. If multi-discipline (ARC/STR/MEP), merge first:"
    echo "       python3 tools/federation_preprocessor.py \\"
    echo "         --files IFC/UNMERGED/file_ARC.ifc IFC/UNMERGED/file_STR.ifc IFC/UNMERGED/file_MEP.ifc \\"
    echo "         --disciplines ARC STR MEP \\"
    echo "         --output DAGCompiler/lib/input/IFC/${BUILDING_TYPE}_Federated.ifc"
    echo "    3. Extract:"
    echo "       python3 tools/extract.py --to reference <IFC_FILE> -o ${EXTRACTED_DB}"
    exit 1
fi

TOTAL=$(sqlite3 "$EXTRACTED_DB" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
echo "  Extracted DB: ${EXTRACTED_DB}"
echo "  Total elements: ${TOTAL}"
echo ""

# Step 2: Element distribution
echo "  IFC class distribution:"
sqlite3 "$EXTRACTED_DB" "
    SELECT printf('    %-35s %5d', ifc_class, COUNT(*))
    FROM elements_meta
    GROUP BY ifc_class
    ORDER BY COUNT(*) DESC
" 2>/dev/null
echo ""

# Step 3: Check which elements have product mappings
ERP_DB="library/ERP.db"
COMP_DB="library/component_library.db"

echo "  Product mapping coverage:"
# Elements whose element_name (type) exists as a product
MAPPED=$(sqlite3 "$EXTRACTED_DB" "
    SELECT COUNT(DISTINCT e.guid)
    FROM elements_meta e
    WHERE EXISTS (
        SELECT 1 FROM '${COMP_DB}'.M_Product p
        WHERE p.Value LIKE '%' || REPLACE(REPLACE(e.element_name, ' ', '_'), ':', '_') || '%'
    )
" 2>/dev/null 2>&1 || echo "?")

echo "    Total: ${TOTAL}"
echo "    (Run IFCtoBOM to see exact BOM line count vs extraction count)"
echo ""

# Step 4: Show likely unclassified classes
echo "  Classes most likely to need product aliases:"
sqlite3 "$EXTRACTED_DB" "
    SELECT printf('    %-35s %5d elements  (sample: %s)',
        ifc_class, COUNT(*), MIN(element_name))
    FROM elements_meta
    WHERE ifc_class IN ('IfcBuildingElementProxy', 'IfcMember', 'IfcPlate',
                         'IfcFlowSegment', 'IfcFlowFitting', 'IfcFlowTerminal')
    GROUP BY ifc_class
    ORDER BY COUNT(*) DESC
" 2>/dev/null
echo ""

# Step 5: Show distinct element names for proxy/member (the actual classification task)
PROXY_COUNT=$(sqlite3 "$EXTRACTED_DB" "SELECT COUNT(*) FROM elements_meta WHERE ifc_class='IfcBuildingElementProxy'" 2>/dev/null || echo "0")
if [ "$PROXY_COUNT" -gt 0 ]; then
    echo "  IfcBuildingElementProxy types (need alias → real IFC class):"
    sqlite3 "$EXTRACTED_DB" "
        SELECT printf('    %-50s %3d', element_name, COUNT(*))
        FROM elements_meta
        WHERE ifc_class = 'IfcBuildingElementProxy'
        GROUP BY element_name
        ORDER BY COUNT(*) DESC
        LIMIT 20
    " 2>/dev/null
    echo ""
    echo "  TO FIX: Add alias entries to ERP.db ad_element_product_alias table:"
    echo "    INSERT INTO ad_element_product_alias (abstract_type, match_field, match_value, priority, source)"
    echo "    VALUES ('<PRODUCT_ID>', 'element_name', '<TYPE_NAME>', 10, '${PREFIX} onboard');"
    echo ""
fi

MEMBER_COUNT=$(sqlite3 "$EXTRACTED_DB" "SELECT COUNT(*) FROM elements_meta WHERE ifc_class='IfcMember'" 2>/dev/null || echo "0")
if [ "$MEMBER_COUNT" -gt 0 ]; then
    echo "  IfcMember types (may need product entries):"
    sqlite3 "$EXTRACTED_DB" "
        SELECT printf('    %-50s %3d', element_name, COUNT(*))
        FROM elements_meta
        WHERE ifc_class = 'IfcMember'
        GROUP BY element_name
        ORDER BY COUNT(*) DESC
        LIMIT 20
    " 2>/dev/null
    echo ""
fi

echo "════════════════════════════════════════"
echo "  NEXT STEPS"
echo "════════════════════════════════════════"
echo "  1. Add product aliases for unclassified elements (see above)"
echo "  2. Re-run: ./scripts/run_RosettaStones.sh ${1}"
echo "  3. Check GEO proof output in logs/pipeline_${PREFIX}*.log"
echo ""
