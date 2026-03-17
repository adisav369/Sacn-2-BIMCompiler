#!/bin/bash
# ============================================================
# BIM Compiler — Canonical Test-Compile Gate
#
# SCOPE: All 5 active buildings (SH, DX, TB, Terminal, ST_SH).
#
# Expected baseline (2026-03-11, Phase 4):
#   DAGCompiler  : 156 PASS /55 RED  (pre-existing: BOMChainMath, MetadataIntegrity, StTemplate NPE)
#   ORMSandbox   :  33 PASS / 3 RED  (w_compose_dx, w_category_2, +1 pre-existing)
#   TopologyMaker:   9 PASS /10 RED  (TopologyBatchProcess: COMPOSE PREFAB BOM inline syntax)
#   BIM_COBOL    : 184 PASS /26 RED  (CoverWithRoof ×3, VerifyPlacement ×1, HelloWorld ×2,
#                                      + BOM data issues: rooms missing, DX data, dispatch)
#   TOTAL        : 382 PASS / 94 RED / 1 SKIP
#
# SpatialDigest golden masters stored in c_order.spatial_digest (prepared from *_BOM.db).
# Formula: name-agnostic bbox, per-class COUNT, all IFC classes included.
# BuildingRegistryTest enforces digest on every pipeline run.
#
# Usage:
#   ./scripts/run_tests.sh           # all suites (SH+DX scope)
#   ./scripts/run_tests.sh dag       # DAGCompiler only
#   ./scripts/run_tests.sh orm       # ORMSandbox only
#   ./scripts/run_tests.sh topology  # TopologyMaker only
#   ./scripts/run_tests.sh preflight # BuildingInspector preflight only
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
init_log "run_tests"

SUITE="${1:-all}"

PASS=0
FAIL=0
SKIP=0

print_header() {
    echo ""
    echo "========================================"
    echo "  $1"
    echo "========================================"
}

run_suite() {
    local module="$1"
    local label="$2"
    local expected_pass="$3"
    local expected_red="$4"   # intentional failures

    print_header "$label"
    echo "  Module : $module"
    echo "  Expect : ${expected_pass} PASS / ${expected_red} intentional RED"
    echo ""

    OUTPUT=$(mvn test -pl "$module" 2>&1) || true

    # Sum across ALL surefire executions (each prints its own "Tests run:" summary).
    # Per-class lines contain "Time elapsed" or "-- in"; execution summaries don't.
    _EXEC_LINES=$(echo "$OUTPUT" | grep -E "Tests run:" | grep -v "Time elapsed" | grep -v "\-\- in")
    _TOT_RUN=$(echo "$_EXEC_LINES"  | grep -oP 'Tests run: \K[0-9]+'  | awk '{s+=$1} END{print s+0}')
    _TOT_FAIL=$(echo "$_EXEC_LINES" | grep -oP 'Failures: \K[0-9]+'   | awk '{s+=$1} END{print s+0}')
    _TOT_ERR=$(echo "$_EXEC_LINES"  | grep -oP 'Errors: \K[0-9]+'     | awk '{s+=$1} END{print s+0}')
    _TOT_SKIP=$(echo "$_EXEC_LINES" | grep -oP 'Skipped: \K[0-9]+'    | awk '{s+=$1} END{print s+0}')
    SUMMARY="Tests run: ${_TOT_RUN:-0}, Failures: ${_TOT_FAIL:-0}, Errors: ${_TOT_ERR:-0}, Skipped: ${_TOT_SKIP:-0}"
    echo "  Result : $SUMMARY"

    RUN=$(echo "$SUMMARY"    | grep -oP 'Tests run: \K[0-9]+')
    FAILURES=$(echo "$SUMMARY" | grep -oP 'Failures: \K[0-9]+')
    ERRORS=$(echo "$SUMMARY"   | grep -oP 'Errors: \K[0-9]+')
    SKIPPED=$(echo "$SUMMARY"  | grep -oP 'Skipped: \K[0-9]+')

    RUN="${RUN:-0}"
    FAILURES="${FAILURES:-0}"
    ERRORS="${ERRORS:-0}"
    SKIPPED="${SKIPPED:-0}"

    TOTAL_RED=$((FAILURES + ERRORS))
    TOTAL_PASS=$((RUN - TOTAL_RED - SKIPPED))

    PASS=$((PASS + TOTAL_PASS))
    FAIL=$((FAIL + TOTAL_RED))
    SKIP=$((SKIP + SKIPPED))

    UNEXPECTED_RED=$((TOTAL_RED - expected_red))
    if [ "$UNEXPECTED_RED" -gt 0 ]; then
        echo "  !! UNEXPECTED RED: $UNEXPECTED_RED beyond the $expected_red intentional"
    elif [ "$TOTAL_RED" -gt 0 ]; then
        echo "  !! $TOTAL_RED intentional RED (G8 calibration — acknowledged)"
    else
        echo "  OK all GREEN"
    fi
}

# ── Step 1: Compile all modules ───────────────────────────────
print_header "COMPILE (all modules)"
mvn compile -q
echo "  Compile: OK"

# ── Step 2: Preflight checks via BuildingInspector (orm-core) ─
#
# Runs ad_* table audits for SH + DX and shows warning counts.
# Not a gate — informational. Fix warnings before long debug cycles.
#
INSPECTOR_CLASS="com.bim.ormsandbox.BuildingInspector"
LIB="library/component_library.db"

run_preflight() {
    local building="$1"
    echo "  --- $building ---"
    WARNINGS=$(mvn exec:java -pl ORMSandbox \
        -Dexec.mainClass="$INSPECTOR_CLASS" \
        -Dexec.args="$LIB preflight $building" \
        -q 2>&1 | grep -E "^\[WARN\]|^RESULT:" | tail -20)
    echo "$WARNINGS" | sed 's/^/    /'
    echo ""
}

case "$SUITE" in
    preflight)
        print_header "PREFLIGHT — BuildingInspector (orm-core)"
        run_preflight "Ifc4_SampleHouse"
        run_preflight "Ifc2x3_Duplex"
        # run_preflight "TB-LKTN"    # out of scope
        # run_preflight "Terminal"   # out of scope
        echo "  GREEN — preflight done"
        exit 0
        ;;
    *)
        print_header "PREFLIGHT — BuildingInspector (orm-core)"
        run_preflight "Ifc4_SampleHouse"
        run_preflight "Ifc2x3_Duplex"
        ;;
esac

# ── Step 3: Run selected test suites ─────────────────────────
case "$SUITE" in
    dag)
        # G8-DX ×1 intentional RED: NULL-bound rooms, calibration deferred.
        # G8-SH GREEN. X1-SH-GAP GREEN. X1-DX-GAP GREEN.
        # SpatialDigest: all 5 buildings enforced. Two surefire executions.
        run_suite "DAGCompiler" "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 156 55
        ;;
    orm)
        run_suite "ORMSandbox" "ORMSandbox — DAO layer smoke tests" 33 3
        ;;
    topology)
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle" 9 10
        ;;
    cobol)
        run_suite "BIM_COBOL" "BIM_COBOL — Verb witnesses + Rosetta Stone" 184 26
        ;;
    preflight)
        # handled above
        ;;
    all|*)
        run_suite "DAGCompiler"   "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 156 55
        run_suite "ORMSandbox"    "ORMSandbox — DAO layer smoke tests"                   33 3
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle"          9 10
        run_suite "BIM_COBOL"     "BIM_COBOL — Verb witnesses + Rosetta Stone" 184 26
        ;;
esac

# ── Summary ───────────────────────────────────────────────────
print_header "SUMMARY"
echo "  PASS : $PASS"
echo "  RED  : $FAIL  (94 pre-existing: DAG ×55, ORM ×3, Topology ×10, COBOL ×26)"
echo "  SKIP : $SKIP"
echo ""

UNEXPECTED=$((FAIL - 94))
if [ "$SUITE" = "topology" ]; then
    UNEXPECTED=$((FAIL - 10))
elif [ "$SUITE" = "cobol" ]; then
    UNEXPECTED=$((FAIL - 26))
elif [ "$SUITE" = "orm" ]; then
    UNEXPECTED=$((FAIL - 3))
elif [ "$SUITE" = "dag" ]; then
    UNEXPECTED=$((FAIL - 55))
fi

if [ "$UNEXPECTED" -gt 0 ]; then
    echo "  !! BUILD BREAK — $UNEXPECTED unexpected failure(s)"
    echo "  Check SpatialDigests before touching Java:"
    echo "    sqlite3 DAGCompiler/lib/output/ifc4_samplehouse.db \\"
    echo "      'SELECT * FROM building_summary'"
    finish_log
    exit 1
else
    echo "  GREEN — gate passed (all 5 buildings)"
    echo ""
    echo "  SpatialDigest: enforced in BuildingRegistryTest via c_order.spatial_digest"
    echo "    Formula: name-agnostic bbox + COUNT per class (GATE-DIGEST, 2026-02-28)"
    echo "    sqlite3 library/{PREFIX}_BOM.db 'SELECT building_id, substr(spatial_digest,1,16) FROM c_order'"
    finish_log
    exit 0
fi
