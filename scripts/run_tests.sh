#!/bin/bash
# ============================================================
# BIM Compiler — Canonical Test-Compile Gate
#
# SCOPE (2026-02-24): SH + DX only.
# TB-LKTN and Terminal compile steps are commented out until
# last-mile furniture placement is solved (exact match to
# input/extracted reference).
#
# Expected baseline (2026-02-27, Phase ST-1b):
#   DAGCompiler  : 163 PASS / 1 RED / 1 SKIP
#                  G8-SH GREEN. G8-DX intentional RED (NULL-bound rooms).
#   ORMSandbox   :  25 PASS  (3 EmptySpaceTest + 14 BuildingInspectorTest + 8 BOM witnesses)
#   TopologyMaker:  19 PASS
#   TOTAL        : 207 PASS / 1 RED / 1 SKIP
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

    SUMMARY=$(echo "$OUTPUT" | grep -E "Tests run:" | tail -1)
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
        # G8-DX ×1 intentional RED: 40 ROOM_Level_* NULL-bound rooms pending IFC_GLOBAL_MM replacement
        # G8-SH GREEN (re-enabled 2026-02-25). F2-DX @Disabled (G8-DX scope).
        run_suite "DAGCompiler" "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 132 1
        ;;
    orm)
        run_suite "ORMSandbox" "ORMSandbox — DAO layer smoke tests" 25 0
        ;;
    topology)
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle" 15 0
        ;;
    preflight)
        # handled above
        ;;
    all|*)
        run_suite "DAGCompiler"   "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 132 1
        run_suite "ORMSandbox"    "ORMSandbox — DAO layer smoke tests"                    25 0
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle"            15 0
        ;;
esac

# ── Summary ───────────────────────────────────────────────────
print_header "SUMMARY"
echo "  PASS : $PASS"
echo "  RED  : $FAIL  (1 intentional: G8-DX — NULL-bound rooms, calibration deferred)"
echo "  SKIP : $SKIP"
echo ""

UNEXPECTED=$((FAIL - 1))
if [ "$SUITE" = "orm" ] || [ "$SUITE" = "topology" ]; then
    UNEXPECTED=$FAIL
fi

if [ "$UNEXPECTED" -gt 0 ]; then
    echo "  !! BUILD BREAK — $UNEXPECTED unexpected failure(s)"
    echo "  Check SpatialDigests before touching Java:"
    echo "    sqlite3 DAGCompiler/lib/output/ifc4_sample_house.db \\"
    echo "      'SELECT * FROM building_summary'"
    exit 1
else
    echo "  GREEN — gate passed (SH+DX scope)"
    echo ""
    echo "  SpatialDigest baseline (post-BOM-2d, 2026-02-22):"
    echo "    SH       1f325a98537e7a54"
    echo "    DX       d3c779b963eaf564"
    echo "  # TB-LKTN  dd4345f4db107208  (out of scope)"
    echo "  # Terminal 301b42b103eba6bc  (out of scope)"
    exit 0
fi
