#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Helper Functions
#
# Sourced by run_RosettaStones.sh. Provides:
#   parse_yaml <file> <key>      — extract YAML field (grep-based)
#   print_header <title>         — boxed section header
#   prepare_compile_db <args...> — create per-building _XX_compile.db
#   cleanup_compile_db           — remove temp compile DB
#
# Usage:
#   source "$(dirname "$0")/lib_rosetta_helpers.sh"
# ============================================================

# ── YAML parser (grep-based, no yq dependency) ─────────────
# Extracts top-level building.* fields from classification YAML.
parse_yaml() {
    local yaml="$1"
    local key="$2"
    grep -E "^\s+${key}:" "$yaml" | head -1 | sed 's/.*:\s*//' | sed 's/\s*#.*//' | tr -d '\r' | xargs
}

print_header() {
    echo ""
    echo "════════════════════════════════════════"
    echo "  $1"
    echo "════════════════════════════════════════"
}

# ── Build per-building compile DB ─────────────────────────────
# Creates a temporary library/_XX_compile.db from *_BOM.db + full schema + C_DocType.
# Java reads the path from -Dbom.db system property (NO hardcoded BOM.db).
# Naming: _SH_compile.db, _DX_compile.db — strongly typed, unmistakably temp.
# This isolates each build — no contamination from other buildings.
prepare_compile_db() {
    local prefix="$1"
    local bom_db="library/${prefix}_BOM.db"
    local building_type="$2"
    local doc_sub_type="$3"
    local product_category="$4"
    local name="$5"
    local building_bom_id="$6"
    local yaml_file="$7"

    # Per-building compile DB — strongly typed name
    COMPILE_DB="library/_${prefix}_compile.db"

    if [ ! -f "$bom_db" ] || [ ! -s "$bom_db" ]; then
        echo "  [FAIL] ${bom_db} not found or empty — IFCtoBOM pipeline failed for ${prefix}"
        echo "         Check logs/pipeline_*${prefix}* for QA report"
        verdict "BOM_${prefix}" "FAIL" "BOM.db not found or empty"
        return 1
    fi

    # Start with clean *_BOM.db (has m_bom, m_bom_line, ad_sysconfig)
    cp "$bom_db" "$COMPILE_DB"

    # Add missing tables from schema snapshot (IF NOT EXISTS for safety)
    sed 's/CREATE TABLE \([^I"]\)/CREATE TABLE IF NOT EXISTS \1/g; s/CREATE TABLE "\([^I]\)/CREATE TABLE IF NOT EXISTS "\1/g' \
        library/schema_snapshot_bom.sql | sqlite3 "$COMPILE_DB" 2>/dev/null || true

    # R27: C_DocType now written by IFCtoBOM pipeline into {PREFIX}_BOM.db.
    # Shell injection removed — C_DocType arrives via the BOM DB copy above.

    # Tier 2 Phase C: INTEGER PK columns now native in IFCtoBOM DDL + backfill.
    # ALTER TABLE workaround removed — S91 (Phase A+B used ALTER TABLE; Phase C updated Java DDL).

    echo "  _${prefix}_compile.db prepared from ${bom_db}"
    return 0
}

cleanup_compile_db() {
    rm -f "$COMPILE_DB"
}
