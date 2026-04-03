#!/bin/bash
# ============================================================
# BIM Compiler — User-Facing Build Script
#
# Compiles a building against an initialised output.db with
# C_Order/C_OrderLine stubs from C_DocType.
#
# DIFFERENT FROM run_RosettaStones.sh:
#   RosettaStones  = developer diagnostic (SH/DX dual _s/_e + delta)
#   This script    = user workflow (any building, single output)
#
# WORKFLOW (e.g., TB_LKTN):
#   1. Script initialises empty output.db from template
#   2. Creates C_Order stub from C_DocType config
#   3. User can inspect/edit the C_Order before compiling
#   4. Compile runs pipeline against the output.db
#   5. User saves copy<timestamp> before next run
#
# Usage:
#   ./scripts/run.sh TB_LKTN              # compile TB_LKTN
#   ./scripts/run.sh Ifc4_SampleHouse     # compile SH
#   ./scripts/run.sh Ifc2x3_Duplex        # compile DX
#   ./scripts/run.sh --list               # list available buildings
#   ./scripts/run.sh --init TB_LKTN       # init only (no compile)
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Temporary working copy — prepared from {PREFIX}_BOM.db (SH_BOM.db, DX_BOM.db, TE_BOM.db)
# by run_RosettaStones.sh prepare_bom_db(). Must exist before running this script.
BOM_DB="library/_${PREFIX:-SH}_compile.db"  # per-building, set via PREFIX
TEMPLATE_DB="library/output_template.db"

print_header() {
    echo ""
    echo "════════════════════════════════════════"
    echo "  $1"
    echo "════════════════════════════════════════"
}

# ── List available buildings ─────────────────────────────────
if [ "$1" = "--list" ]; then
    print_header "Available Buildings (C_DocType)"
    sqlite3 "$BOM_DB" "
        SELECT printf('  %-8s %-20s %-10s %dx%dx%d mm',
               DocSubType, ProjectName,
               CASE WHEN DocSubType IN ('SH','DX','TE') THEN 'EXTRACTED'
                    ELSE 'GENERATIVE' END,
               AabbWidthMm, AabbDepthMm, AabbHeightMm)
        FROM C_DocType ORDER BY C_DocType_ID
    " 2>/dev/null
    echo ""
    exit 0
fi

# ── Parse args ───────────────────────────────────────────────
INIT_ONLY=0
if [ "$1" = "--init" ]; then
    INIT_ONLY=1
    shift
fi

BUILDING="${1:?Usage: $0 [--init] <ProjectName|DocSubType>}"

# Resolve building name → C_DocType row
DOC_TYPE=$(sqlite3 "$BOM_DB" "
    SELECT C_DocType_ID FROM C_DocType
    WHERE ProjectName = '${BUILDING}' OR DocSubType = '${BUILDING}'
    LIMIT 1
" 2>/dev/null)

if [ -z "$DOC_TYPE" ]; then
    echo "ERROR: Building '${BUILDING}' not found in C_DocType"
    echo "  Run: $0 --list"
    exit 1
fi

# Load config from C_DocType
eval $(sqlite3 "$BOM_DB" "
    SELECT printf('PROJECT_NAME=%s OUTPUT_PATH=%s DOC_SUB_TYPE=%s AABB_W=%d AABB_D=%d AABB_H=%d SEQ_NO=%d EXPECTED=%d PROVENANCE=%s',
           ProjectName, OutputDbPath, DocSubType,
           AabbWidthMm, AabbDepthMm, AabbHeightMm,
           SeqNo, COALESCE(ExpectedElements,0),
           COALESCE(Provenance,'EXTRACTED'))
    FROM C_DocType WHERE C_DocType_ID = '${DOC_TYPE}'
" 2>/dev/null)

# DSL files removed (S140) — YAML is sole intent source

print_header "BUILD: ${PROJECT_NAME} (${DOC_TYPE})"
echo "  DocSubType : ${DOC_SUB_TYPE}"
echo "  Output     : ${OUTPUT_PATH}"
echo "  AABB       : ${AABB_W} x ${AABB_D} x ${AABB_H} mm"

# ── Step 1: Initialise output.db ─────────────────────────────
print_header "INIT output.db"

if [ -f "$OUTPUT_PATH" ]; then
    TIMESTAMP=$(date +%Y%m%d_%H%M%S)
    BACKUP="${OUTPUT_PATH%.db}_copy${TIMESTAMP}.db"
    echo "  Backing up existing → ${BACKUP}"
    cp "$OUTPUT_PATH" "$BACKUP"
fi

if [ -f "$TEMPLATE_DB" ]; then
    cp "$TEMPLATE_DB" "$OUTPUT_PATH"
    echo "  Template copied → ${OUTPUT_PATH}"
else
    echo "  No template DB — pipeline will create schema"
fi

# ── Step 2: Create C_Order stub ──────────────────────────────
echo ""
echo "  Creating C_Order stub..."

# Template already has c_order/c_orderline tables — insert from prepared BOM.db via ATTACH.
sqlite3 "$OUTPUT_PATH" <<EOSQL
ATTACH '${BOM_DB}' AS bom;
INSERT OR REPLACE INTO c_order (
    C_Order_ID, Name, Description, DocStatus, C_DocType_ID,
    OutputDbPath, SeqNo, ExpectedElements,
    Provenance, AabbWidthMm, AabbDepthMm, AabbHeightMm, IsActive
) SELECT
    dt.ProjectName, dt.ProjectName, 'Build ' || dt.DocSubType,
    'DR', dt.C_DocType_ID,
    dt.OutputDbPath, dt.SeqNo,
    COALESCE(dt.ExpectedElements, 0),
    COALESCE(dt.Provenance, 'EXTRACTED'),
    dt.AabbWidthMm, dt.AabbDepthMm, dt.AabbHeightMm, 1
FROM bom.C_DocType dt
WHERE dt.C_DocType_ID = '${DOC_TYPE}';
DETACH bom;
EOSQL
echo "  C_Order: ${PROJECT_NAME} (DocStatus=DR, C_DocType=${DOC_TYPE})"

# ── Step 3: C_OrderLine table ─────────────────────────────────
echo ""
echo "  C_OrderLine table ready (template schema; user populates before compile)"

if [ "$INIT_ONLY" = "1" ]; then
    print_header "INIT COMPLETE"
    echo "  Output DB initialised with C_Order/C_OrderLine stubs."
    echo "  Edit the stubs, then run: $0 ${BUILDING}"
    echo ""
    echo "  Inspect: sqlite3 ${OUTPUT_PATH} '.tables'"
    echo "  Edit:    sqlite3 ${OUTPUT_PATH}"
    exit 0
fi

# ── Step 4: Compile ──────────────────────────────────────────
print_header "COMPILE ${PROJECT_NAME}"
echo "  Running pipeline..."

mvn test -pl DAGCompiler \
    -Dtest="BuildingRegistryTest#compile_${DOC_SUB_TYPE}" \
    -q 2>&1 | tail -10 || true

# ── Step 5: Verify ───────────────────────────────────────────
print_header "VERIFY"
ELEMENT_COUNT=$(sqlite3 "$OUTPUT_PATH" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
DOC_STATUS=$(sqlite3 "$OUTPUT_PATH" "SELECT DocStatus FROM c_order WHERE C_Order_ID='${PROJECT_NAME}'" 2>/dev/null || echo "?")
echo "  Elements  : ${ELEMENT_COUNT}"
echo "  DocStatus : ${DOC_STATUS}"

if [ "$DOC_STATUS" = "CO" ]; then
    echo "  Result    : COMPLETE"
else
    echo "  Result    : INCOMPLETE (DocStatus != CO)"
fi

print_header "DONE"
echo "  Output: ${OUTPUT_PATH}"
echo "  Backup: ${BACKUP:-none}"
echo ""
echo "  To save before next run:"
echo "    cp ${OUTPUT_PATH} ${OUTPUT_PATH%.db}_copy\$(date +%Y%m%d_%H%M%S).db"
echo ""
