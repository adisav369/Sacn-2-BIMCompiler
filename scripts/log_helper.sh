#!/bin/bash
# ============================================================
# BIM Compiler — Shared Logging Infrastructure
#
# Sourced by all scripts. Provides:
#   init_log <script_name>  — creates logs/ dir, opens tee
#   section <title>         — prints boxed section header
#   verdict <name> <P/F/W> <detail> — consistent formatted verdict
#   finish_log              — prints summary with log file path
#
# Usage:
#   source "$(dirname "$0")/log_helper.sh"
#   init_log "run_tests"
#   section "My Section"
#   verdict "CHECK_1" "PASS" "all good"
#   finish_log
# ============================================================

LOG_FILE=""
LOG_PASS=0
LOG_FAIL=0
LOG_WARN=0

init_log() {
    local script_name="$1"
    local timestamp
    timestamp=$(date +%Y%m%d_%H%M%S)
    local log_dir
    log_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/logs"

    mkdir -p "$log_dir"
    LOG_FILE="${log_dir}/${script_name}_${timestamp}.txt"

    # Tee stdout+stderr to log file while preserving terminal output
    exec > >(tee "$LOG_FILE") 2>&1

    echo "═══ ${script_name^^} — $(date '+%Y-%m-%d %H:%M:%S') ═══"
    echo "═══ Log: ${LOG_FILE} ═══"
    echo ""
}

section() {
    local title="$1"
    echo ""
    echo "── ${title} ──────────────────────────────────"
}

verdict() {
    local check_name="$1"
    local status="$2"
    local detail="$3"

    case "$status" in
        PASS) LOG_PASS=$((LOG_PASS + 1)) ;;
        FAIL) LOG_FAIL=$((LOG_FAIL + 1)) ;;
        WARN) LOG_WARN=$((LOG_WARN + 1)) ;;
    esac

    echo "  VERDICT: ${status} — ${detail}"
}

finish_log() {
    local total=$((LOG_PASS + LOG_FAIL + LOG_WARN))
    echo ""
    if [ "$LOG_FAIL" -gt 0 ]; then
        echo "═══ SUMMARY: ${LOG_PASS}/${total} PASS, ${LOG_FAIL} FAIL ═══"
    elif [ "$LOG_WARN" -gt 0 ]; then
        echo "═══ SUMMARY: ${LOG_PASS}/${total} PASS, ${LOG_WARN} WARN ═══"
    else
        echo "═══ SUMMARY: ${LOG_PASS}/${total} PASS ═══"
    fi
    echo "═══ Log saved to: ${LOG_FILE} ═══"
}
