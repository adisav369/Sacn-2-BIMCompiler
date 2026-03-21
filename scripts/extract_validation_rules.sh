#!/bin/bash
# ============================================================
# BIM Compiler — Validation Rules Extraction
#
# Mines structural dimension patterns from compiled output DBs
# to generate validation rule SQL for disc_validation.db.
#
# Usage:
#   ./scripts/extract_validation_rules.sh BA          # single building
#   ./scripts/extract_validation_rules.sh BA BH BS    # multiple
#   ./scripts/extract_validation_rules.sh             # all with output DBs
#
# Output:
#   Prints mined rules to stdout. Redirect to create migration:
#   ./scripts/extract_validation_rules.sh BA > migration/DV0XX_ba_rules.sql
#
# See: docs/IFC_ONBOARDING_RUNBOOK.md §Step 9
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

YAML_DIR="IFCtoBOM/src/main/resources"

# ── Helpers ──────────────────────────────────────────────────

parse_yaml() {
    local file="$1" key="$2"
    grep -m1 "^\s*${key}:" "$file" | sed "s/.*${key}:\s*//" | sed 's/\s*#.*//' | sed "s/['\"]//g" | xargs
}

# ── Collect buildings ────────────────────────────────────────

FILTER_PREFIXES=("$@")

for yaml_file in "$YAML_DIR"/classify_*.yaml; do
    [ -f "$yaml_file" ] || continue

    prefix=$(parse_yaml "$yaml_file" "prefix")
    building_type=$(parse_yaml "$yaml_file" "building_type")
    name=$(parse_yaml "$yaml_file" "name")
    doc_base=$(parse_yaml "$yaml_file" "doc_base_type")

    # Filter if prefixes specified
    if [ ${#FILTER_PREFIXES[@]} -gt 0 ]; then
        match=false
        for fp in "${FILTER_PREFIXES[@]}"; do
            [ "$fp" = "$prefix" ] && match=true
        done
        $match || continue
    fi

    output_base="DAGCompiler/lib/output/$(echo "$building_type" | tr '[:upper:]' '[:lower:]')"
    output_db="${output_base}.db"

    if [ ! -f "$output_db" ]; then
        echo "-- SKIP ${prefix} (${name}): no output DB at ${output_db}" >&2
        continue
    fi

    echo "-- ════════════════════════════════════════════════════════"
    echo "-- ${prefix}: ${name} (${building_type})"
    echo "-- Source: ${output_db}"
    echo "-- Generated: $(date '+%Y-%m-%d %H:%M')"
    echo "-- ════════════════════════════════════════════════════════"
    echo ""

    # ── 1. Per-(ifc_class, storey) dimension statistics ──────

    echo "-- §1: Structural dimensions per (ifc_class, storey)"
    echo "-- Use: identify typical element sizes for validation rules"
    echo ""
    sqlite3 "$output_db" -header -column "
        SELECT em.ifc_class, em.storey, COUNT(*) as cnt,
               ROUND(AVG((r.maxX-r.minX)*1000)) as avg_W_mm,
               ROUND(AVG((r.maxY-r.minY)*1000)) as avg_D_mm,
               ROUND(AVG((r.maxZ-r.minZ)*1000)) as avg_H_mm,
               ROUND(MIN((r.maxX-r.minX)*1000)) as min_W_mm,
               ROUND(MAX((r.maxX-r.minX)*1000)) as max_W_mm
        FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
        GROUP BY em.ifc_class, em.storey HAVING COUNT(*) > 1
        ORDER BY cnt DESC
    " 2>/dev/null | sed 's/^/-- /'
    echo ""

    # ── 2. Material distribution ─────────────────────────────

    echo "-- §2: Material distribution"
    echo ""
    sqlite3 "$output_db" -header -column "
        SELECT em.ifc_class, em.material_name, COUNT(*) as cnt
        FROM elements_meta em
        WHERE em.material_name IS NOT NULL AND em.material_name != ''
        GROUP BY em.ifc_class, em.material_name
        ORDER BY cnt DESC
    " 2>/dev/null | sed 's/^/-- /'
    echo ""

    # ── 3. Spacing patterns (for TILE/CLUSTER verb rules) ────

    echo "-- §3: Spacing patterns (adjacent element gaps)"
    echo "-- Elements of the same ifc_class on the same storey, sorted by X"
    echo ""
    sqlite3 "$output_db" -header -column "
        WITH ranked AS (
            SELECT em.ifc_class, em.storey,
                   r.minX, r.minY, r.minZ,
                   (r.maxX-r.minX)*1000 as W_mm,
                   ROW_NUMBER() OVER (PARTITION BY em.ifc_class, em.storey ORDER BY r.minX, r.minY) as rn
            FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
        ),
        gaps AS (
            SELECT a.ifc_class, a.storey,
                   ROUND((b.minX - a.minX)*1000) as gap_X_mm,
                   ROUND((b.minY - a.minY)*1000) as gap_Y_mm
            FROM ranked a JOIN ranked b
              ON a.ifc_class = b.ifc_class AND a.storey = b.storey AND b.rn = a.rn + 1
        )
        SELECT ifc_class, storey, COUNT(*) as pairs,
               ROUND(AVG(gap_X_mm)) as avg_gap_X_mm,
               ROUND(MIN(gap_X_mm)) as min_gap_X_mm,
               ROUND(MAX(gap_X_mm)) as max_gap_X_mm,
               ROUND(STDEV(gap_X_mm)) as stdev_X_mm
        FROM gaps
        GROUP BY ifc_class, storey HAVING COUNT(*) >= 2
        ORDER BY pairs DESC
    " 2>/dev/null | sed 's/^/-- /'
    echo ""

    # ── 4. IFC class inventory ───────────────────────────────

    echo "-- §4: IFC class inventory"
    echo ""
    sqlite3 "$output_db" -header -column "
        SELECT ifc_class, discipline, COUNT(*) as cnt
        FROM elements_meta
        GROUP BY ifc_class, discipline
        ORDER BY cnt DESC
    " 2>/dev/null | sed 's/^/-- /'
    echo ""

    # ── 5. Candidate validation rule SQL ─────────────────────

    echo "-- §5: Candidate validation rules for disc_validation.db"
    echo "-- Review and adjust before applying. Rule IDs are placeholders."
    echo ""

    # Generate INSERT stubs for element types with ≥3 instances
    sqlite3 "$output_db" "
        SELECT em.ifc_class, em.storey, COUNT(*) as cnt,
               ROUND(AVG((r.maxX-r.minX)*1000)) as avg_W,
               ROUND(AVG((r.maxY-r.minY)*1000)) as avg_D,
               ROUND(AVG((r.maxZ-r.minZ)*1000)) as avg_H
        FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
        GROUP BY em.ifc_class, em.storey HAVING COUNT(*) >= 3
        ORDER BY cnt DESC
    " 2>/dev/null | while IFS='|' read -r ifc_class storey cnt avg_w avg_d avg_h; do
        rule_name="${ifc_class}_${storey// /_}"
        echo "-- Rule: ${rule_name} (${cnt} instances, avg ${avg_w}x${avg_d}x${avg_h} mm)"
        echo "-- INSERT INTO ad_val_rule (rule_name, ifc_class, check_method, severity, is_active,"
        echo "--     description, provenance)"
        echo "-- VALUES ('${rule_name}', '${ifc_class}', 'DIMENSION_RANGE', 'WARNING', 1,"
        echo "--     '${ifc_class} on ${storey}: ${cnt} instances, avg W=${avg_w} D=${avg_d} H=${avg_h}mm',"
        echo "--     '${building_type}');"
        echo "-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)"
        echo "-- VALUES (last_insert_rowid(), 'typical_width_mm', '${avg_w}');"
        echo "-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)"
        echo "-- VALUES (last_insert_rowid(), 'typical_depth_mm', '${avg_d}');"
        echo "-- INSERT INTO ad_val_rule_param (ad_val_rule_id, param_name, param_value)"
        echo "-- VALUES (last_insert_rowid(), 'typical_height_mm', '${avg_h}');"
        echo ""
    done

    echo ""
done
