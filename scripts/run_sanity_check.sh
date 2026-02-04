#!/bin/bash
#
# BIM Sanity Checker - Run sanity checks on database files
#
# Usage:
#   ./scripts/run_sanity_check.sh output/tb_lktn.db      # Check single DB
#   ./scripts/run_sanity_check.sh output/                # Check all DBs in folder
#   ./scripts/run_sanity_check.sh                        # Check all DBs in ./output/
#
# Results are written to SanityCheckResults.txt

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

JAR_PATH="tools/sanity-checker/target/sanity-checker-1.0-SNAPSHOT.jar"
RESULTS_FILE="SanityCheckResults.txt"

# Check if JAR exists
if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: Sanity checker JAR not found at $JAR_PATH"
    echo "Build it first: cd tools/sanity-checker && mvn clean package"
    exit 2
fi

# Initialize results file
echo "========================================" > "$RESULTS_FILE"
echo "BIM SANITY CHECK RESULTS" >> "$RESULTS_FILE"
echo "Date: $(date '+%Y-%m-%d %H:%M:%S')" >> "$RESULTS_FILE"
echo "========================================" >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

TOTAL_DBS=0
PASSED_DBS=0
FAILED_DBS=0

run_check() {
    local db_file="$1"
    local base_name=$(basename "$db_file")

    echo "Checking: $base_name"
    echo "----------------------------------------" >> "$RESULTS_FILE"
    echo "FILE: $db_file" >> "$RESULTS_FILE"
    echo "----------------------------------------" >> "$RESULTS_FILE"

    TOTAL_DBS=$((TOTAL_DBS + 1))

    # Run sanity checker and capture output
    if java -jar "$JAR_PATH" "$db_file" >> "$RESULTS_FILE" 2>&1; then
        echo "  -> PASS"
        echo "" >> "$RESULTS_FILE"
        PASSED_DBS=$((PASSED_DBS + 1))
    else
        EXIT_CODE=$?
        echo "  -> FAIL (exit code: $EXIT_CODE)"
        echo "" >> "$RESULTS_FILE"
        FAILED_DBS=$((FAILED_DBS + 1))
    fi
}

# Determine what to check
if [ -n "$1" ]; then
    TARGET="$1"
else
    TARGET="output/"
fi

# Check if target is a file or directory
if [ -f "$TARGET" ]; then
    # Single database file
    if [[ "$TARGET" == *.db ]]; then
        run_check "$TARGET"
    else
        echo "ERROR: File must be a .db file: $TARGET"
        exit 2
    fi
elif [ -d "$TARGET" ]; then
    # Directory - check all .db files
    echo "Scanning directory: $TARGET"
    echo "Input: All .db files in $TARGET" >> "$RESULTS_FILE"
    echo "" >> "$RESULTS_FILE"

    DB_FILES=$(find "$TARGET" -maxdepth 1 -name "*.db" -type f | sort)

    if [ -z "$DB_FILES" ]; then
        echo "No .db files found in $TARGET"
        echo "No .db files found." >> "$RESULTS_FILE"
        exit 0
    fi

    for db_file in $DB_FILES; do
        run_check "$db_file"
    done
else
    echo "ERROR: Target not found: $TARGET"
    exit 2
fi

# Write summary
echo "" >> "$RESULTS_FILE"
echo "========================================" >> "$RESULTS_FILE"
echo "SUMMARY" >> "$RESULTS_FILE"
echo "========================================" >> "$RESULTS_FILE"
echo "Total databases checked: $TOTAL_DBS" >> "$RESULTS_FILE"
echo "Passed: $PASSED_DBS" >> "$RESULTS_FILE"
echo "Failed: $FAILED_DBS" >> "$RESULTS_FILE"
echo "" >> "$RESULTS_FILE"

if [ $FAILED_DBS -eq 0 ]; then
    echo "OVERALL: ALL CHECKS PASSED" >> "$RESULTS_FILE"
else
    echo "OVERALL: $FAILED_DBS DATABASE(S) HAVE ISSUES" >> "$RESULTS_FILE"
fi

echo ""
echo "========================================"
echo "SANITY CHECK COMPLETE"
echo "========================================"
echo "Total: $TOTAL_DBS | Passed: $PASSED_DBS | Failed: $FAILED_DBS"
echo ""
echo "Results written to: $RESULTS_FILE"

# Exit with appropriate code
if [ $FAILED_DBS -gt 0 ]; then
    exit 1
fi
exit 0
