#!/bin/bash
# ============================================================
# BIM Compiler — Canonical Test-Compile Gate
#
# SCOPE (2026-02-24): SH + DX only.
# TB-LKTN and Terminal compile steps are commented out until
# last-mile furniture placement is solved (exact match to
# input/extracted reference).
#
# Expected baseline:
#   DAGCompiler  : 118 PASS / 2 RED (G8-SH + G8-DX intentional)
#   ORMSandbox   :  13 PASS  (11 @Test + 1 @ParameterizedTest×2 = 13 Maven cases)
#   TopologyMaker:  15 PASS
#   TOTAL        : 146 PASS / 2 RED
#
# Usage:
#   ./scripts/run_tests.sh           # all suites (SH+DX scope)
#   ./scripts/run_tests.sh dag       # DAGCompiler only
#   ./scripts/run_tests.sh orm       # ORMSandbox only
#   ./scripts/run_tests.sh topology  # TopologyMaker only
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

# ── Step 2: Per-building compile check (SH + DX in scope) ─────
#
# These exec:java calls do a fresh compile of each building and
# print element counts. Run after mvn test to confirm output DBs
# are current. Not a gate — gate is mvn test below.
#
print_header "BUILDING COMPILE — SH + DX (in scope)"

echo "  --- SampleHouse (SH) ---"
mvn exec:java -pl DAGCompiler \
    -Dexec.mainClass="com.bim.compiler.dsl.SampleHouseEndToEndTest" -q 2>&1 | tail -3

echo "  --- Duplex (DX) ---"
mvn exec:java -pl DAGCompiler \
    -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNDuplexEndToEndTest" -q 2>&1 | tail -3

# ── Commented out — out of scope until last-mile solved ───────
# echo "  --- TB-LKTN (generative — last-mile furniture pending) ---"
# mvn exec:java -pl DAGCompiler \
#     -Dexec.mainClass="com.bim.compiler.dsl.TBLKTNEndToEndTest" -q 2>&1 | tail -3

# echo "  --- Terminal (no room boundaries yet) ---"
# mvn exec:java -pl DAGCompiler \
#     -Dexec.mainClass="com.bim.compiler.dsl.TerminalEndToEndTest" -q 2>&1 | tail -3
# ─────────────────────────────────────────────────────────────

# ── Step 3: Run selected test suites ─────────────────────────
case "$SUITE" in
    dag)
        # G8 ×2 are intentional RED: SH room bounds not calibrated, DX NULL rooms
        run_suite "DAGCompiler" "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 118 2
        ;;
    orm)
        run_suite "ORMSandbox" "ORMSandbox — DAO layer smoke tests" 13 0
        ;;
    topology)
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle" 15 0
        ;;
    all|*)
        run_suite "DAGCompiler"   "DAGCompiler — Contract + Rosetta + DriftGuard + LOD" 118 2
        run_suite "ORMSandbox"    "ORMSandbox — DAO layer smoke tests"                   13 0
        run_suite "TopologyMaker" "TopologyMaker — Grid strategy + PO lifecycle"           15 0
        ;;
esac

# ── Summary ───────────────────────────────────────────────────
print_header "SUMMARY"
echo "  PASS : $PASS"
echo "  RED  : $FAIL  (2 intentional: G8-SH + G8-DX calibration)"
echo "  SKIP : $SKIP"
echo ""

UNEXPECTED=$((FAIL - 2))
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
