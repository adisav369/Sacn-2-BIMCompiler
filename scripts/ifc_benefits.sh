#!/bin/bash
# ============================================================
# BIM Compiler — IFC Benefits Report (Pre-Onboarding)
#
# Analyzes an extracted DB BEFORE onboarding to show what the
# IFC would contribute to the system if accepted:
#   - New IFC classes not yet in the component library
#   - New product types and geometry diversity
#   - BOM model complexity estimate
#   - Validation rule mining potential
#   - IFC class coverage check (Step 0 of runbook)
#
# Usage:
#   ./scripts/ifc_benefits.sh Building_Architecture
#   ./scripts/ifc_benefits.sh Infra_Plumbing Building_Hvac
#   ./scripts/ifc_benefits.sh --all                          # all extracted DBs
#
# Requires: extracted DB at DAGCompiler/lib/input/{type}_extracted.db
# See: docs/IFC_ONBOARDING_RUNBOOK.md
# ============================================================


# --- utf8-console guard (2026-09-05) ------------------------------------------
# Belt to the per-script Python guard's braces. Every Python file in this repo that prints a
# non-ASCII LITERAL now reconfigures its own stdout, but that detection cannot see non-ASCII
# arriving at RUNTIME (a material name, an IFC family, a path). PYTHONUTF8 makes the
# interpreter use UTF-8 for stdio regardless, so nothing launched from this script can die on
# a Windows cp1252 console the way restore_generative_meshes.py did -- it created its
# back-compat view and then crashed on the very next print, before restoring any mesh.
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8
# ------------------------------------------------------------------------------

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

COMP_DB="library/component_library.db"
DISC_DB="library/disc_patterns.db"   # de-ERP: canonical pattern-store name (ERP.db is a back-compat symlink)
INPUT_DIR="DAGCompiler/lib/input"

B="\033[1m"
R="\033[0m"
G="\033[32m"
Y="\033[33m"
RED="\033[31m"
D="\033[90m"

# ── Collect building types ───────────────────────────────────

TYPES=()
if [ "$1" = "--all" ]; then
    for db in "$INPUT_DIR"/*_extracted.db; do
        [ -f "$db" ] || continue
        bt=$(basename "$db" _extracted.db)
        # Skip copies and lowercase duplicates
        echo "$bt" | grep -q "Copy" && continue
        echo "$bt" | grep -q "^ifc4_" && continue
        TYPES+=("$bt")
    done
else
    TYPES=("$@")
fi

if [ ${#TYPES[@]} -eq 0 ]; then
    echo "Usage: ./scripts/ifc_benefits.sh <BuildingType> [BuildingType ...]"
    echo "       ./scripts/ifc_benefits.sh --all"
    echo ""
    echo "Available extracted DBs:"
    for db in "$INPUT_DIR"/*_extracted.db; do
        [ -f "$db" ] || continue
        bt=$(basename "$db" _extracted.db)
        echo "$bt" | grep -q "Copy" && continue
        echo "$bt" | grep -q "^ifc4_" && continue
        n=$(sqlite3 "$db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "?")
        printf "  %-35s %s elements\n" "$bt" "$n"
    done
    exit 0
fi

# ── Catalog baseline ─────────────────────────────────────────

existing_classes=$(sqlite3 "$COMP_DB" "SELECT DISTINCT ifc_class FROM I_Geometry_Map" 2>/dev/null | sort)
existing_products=$(sqlite3 "$COMP_DB" "SELECT COUNT(*) FROM M_Product" 2>/dev/null || echo "0")
existing_geom=$(sqlite3 "$COMP_DB" "SELECT COUNT(DISTINCT geometry_hash) FROM I_Geometry_Map" 2>/dev/null || echo "0")

echo ""
printf "${B}╔══════════════════════════════════════════════════════════════════════╗${R}\n"
printf "${B}║              IFC BENEFITS REPORT (Pre-Onboarding)                   ║${R}\n"
printf "${B}║              %-24s                                  ║${R}\n" "$(date '+%Y-%m-%d %H:%M')"
printf "${B}╠══════════════════════════════════════════════════════════════════════╣${R}\n"
printf "${B}║${R}  Current catalog: ${B}%s${R} products, ${B}%s${R} geometries" "$existing_products" "$existing_geom"
printf "%*s${B}║${R}\n" 1 ""
printf "${B}╚══════════════════════════════════════════════════════════════════════╝${R}\n"
echo ""

# ── Per building type ────────────────────────────────────────

for bt in "${TYPES[@]}"; do
    ref_db="${INPUT_DIR}/${bt}_extracted.db"

    printf "${B}══ %s${R}\n" "$bt"

    if [ ! -f "$ref_db" ]; then
        printf "    ${RED}Not found:${R} %s\n" "$ref_db"
        printf "    Run: ${D}python3 tools/extract.py --to reference <source.ifc> -o %s${R}\n" "$ref_db"
        echo ""
        continue
    fi

    # ── 1. Overview ──────────────────────────────────────────

    total=$(sqlite3 "$ref_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "?")
    storeys=$(sqlite3 "$ref_db" "SELECT GROUP_CONCAT(s || ' (' || n || ')', ', ') FROM (
        SELECT storey as s, COUNT(*) as n FROM elements_meta GROUP BY storey ORDER BY MIN(id)
    )" 2>/dev/null || echo "?")
    class_count=$(sqlite3 "$ref_db" "SELECT COUNT(DISTINCT ifc_class) FROM elements_meta" 2>/dev/null || echo "?")

    printf "    Elements: ${B}%s${R}\n" "$total"
    printf "    Storeys:  %s\n" "$storeys"
    printf "    IFC classes: ${B}%s${R}\n" "$class_count"

    # Envelope
    envelope=$(sqlite3 "$ref_db" "
        SELECT ROUND(MAX(maxX)-MIN(minX),1) || 'm × ' ||
               ROUND(MAX(maxY)-MIN(minY),1) || 'm × ' ||
               ROUND(MAX(maxZ)-MIN(minZ),1) || 'm'
        FROM elements_rtree" 2>/dev/null || echo "?")
    printf "    Envelope: %s\n" "$envelope"
    echo ""

    # ── 2. IFC Class Coverage (Step 0) ───────────────────────

    printf "    ${B}IFC Class Coverage${R}\n"

    # Classes in this IFC
    ifc_classes=$(sqlite3 "$ref_db" "SELECT DISTINCT ifc_class FROM elements_meta ORDER BY ifc_class" 2>/dev/null)

    registered_ok=0
    registered_fail=0
    new_to_lib=0

    while IFS= read -r cls; do
        [ -z "$cls" ] && continue
        # Check if registered in ad_ifc_class_map
        reg=$(sqlite3 "$DISC_DB" "SELECT COUNT(*) FROM ad_ifc_class_map WHERE ifc_class='${cls}' AND is_active=1" 2>/dev/null || echo "0")
        cnt=$(sqlite3 "$ref_db" "SELECT COUNT(*) FROM elements_meta WHERE ifc_class='${cls}'" 2>/dev/null || echo "?")

        # Check if already in component library
        in_lib=$(echo "$existing_classes" | grep -c "^${cls}$" || true)

        if [ "$reg" = "0" ]; then
            printf "      ${RED}✗${R} %-30s %4s elements  ${RED}NOT REGISTERED${R}\n" "$cls" "$cnt"
            registered_fail=$((registered_fail + 1))
        elif [ "$in_lib" = "0" ]; then
            printf "      ${G}✓${R} %-30s %4s elements  ${G}NEW to library${R}\n" "$cls" "$cnt"
            new_to_lib=$((new_to_lib + 1))
            registered_ok=$((registered_ok + 1))
        else
            printf "      ${G}✓${R} %-30s %4s elements\n" "$cls" "$cnt"
            registered_ok=$((registered_ok + 1))
        fi
    done <<< "$ifc_classes"

    echo ""
    if [ "$registered_fail" -gt 0 ]; then
        printf "    ${RED}⚠ %d class(es) not registered in ad_ifc_class_map — will be silently skipped!${R}\n" "$registered_fail"
        printf "    ${D}Register them before onboarding (see IFC_ONBOARDING_RUNBOOK.md §Step 0)${R}\n"
    else
        printf "    ${G}All %d classes registered.${R}\n" "$registered_ok"
    fi
    if [ "$new_to_lib" -gt 0 ]; then
        printf "    ${G}%d class(es) would be NEW to the component library.${R}\n" "$new_to_lib"
    fi
    echo ""

    # ── 3. Expected Library Enrichment ───────────────────────

    printf "    ${B}Expected Library Enrichment${R}\n"

    distinct_geom=$(sqlite3 "$ref_db" "SELECT COUNT(DISTINCT geometry_hash) FROM element_instances" 2>/dev/null || echo "?")
    distinct_types=$(sqlite3 "$ref_db" "
        SELECT COUNT(*) FROM (
            SELECT DISTINCT ifc_class, element_type FROM elements_meta
        )" 2>/dev/null || echo "?")

    # Check how many geometries are already in library
    already_in_lib="0"
    if [ "$distinct_geom" != "?" ]; then
        already_in_lib=$(sqlite3 "$ref_db" "
            SELECT COUNT(DISTINCT ei.geometry_hash)
            FROM element_instances ei
            WHERE ei.geometry_hash IN (
                SELECT geometry_hash FROM '${COMP_DB}'.component_geometries
            )" 2>/dev/null || echo "0")
        # Attach approach if the above fails
        if [ "$already_in_lib" = "0" ]; then
            already_in_lib=$(sqlite3 "$ref_db" "
                ATTACH '${COMP_DB}' AS lib;
                SELECT COUNT(DISTINCT ei.geometry_hash)
                FROM element_instances ei
                WHERE ei.geometry_hash IN (SELECT geometry_hash FROM lib.component_geometries)
            " 2>/dev/null || echo "0")
        fi
    fi

    new_geom=$((distinct_geom - already_in_lib))
    [ "$new_geom" -lt 0 ] && new_geom=0

    printf "      Distinct geometries:  ${B}%s${R}" "$distinct_geom"
    if [ "$already_in_lib" != "0" ]; then
        printf "  (%s already in library, ${G}%s new${R})" "$already_in_lib" "$new_geom"
    else
        printf "  (${G}all new${R})"
    fi
    echo ""
    printf "      Product types:        ${B}%s${R}  (ifc_class × element_type)\n" "$distinct_types"
    printf "      Est. new products:    ${B}~%s${R}\n" "$distinct_types"
    echo ""

    # ── 4. BOM Complexity Estimate ───────────────────────────

    printf "    ${B}BOM Model Estimate${R}\n"

    storey_count=$(sqlite3 "$ref_db" "SELECT COUNT(DISTINCT storey) FROM elements_meta" 2>/dev/null || echo "?")
    est_boms=$((1 + storey_count))  # BUILDING + 1 per storey

    # Factorization potential: how many elements share geometry?
    shared=$(sqlite3 "$ref_db" "
        SELECT COALESCE(SUM(cnt), 0) FROM (
            SELECT COUNT(*) as cnt FROM element_instances
            GROUP BY geometry_hash HAVING COUNT(*) > 1
        )" 2>/dev/null || echo "0")
    shared_pct=0
    [ "$total" != "?" ] && [ "$total" -gt 0 ] 2>/dev/null && \
        shared_pct=$(awk "BEGIN { printf \"%.0f\", $shared / $total * 100 }" 2>/dev/null || echo "0")

    printf "      Storeys:          %s  → ~%s BOMs (BUILDING + FLOOR)\n" "$storey_count" "$est_boms"
    printf "      Shared geometry:  %s/%s elements (%s%%)  → verb factorization potential\n" \
        "$shared" "$total" "$shared_pct"
    echo ""

    # ── 5. Validation Rule Potential ─────────────────────────

    printf "    ${B}Validation Rule Potential${R}\n"

    minable=$(sqlite3 "$ref_db" "
        SELECT COUNT(*) FROM (
            SELECT ifc_class, storey FROM elements_meta
            GROUP BY ifc_class, storey HAVING COUNT(*) >= 3
        )" 2>/dev/null || echo "0")
    minable_elem=$(sqlite3 "$ref_db" "
        SELECT COALESCE(SUM(cnt), 0) FROM (
            SELECT COUNT(*) as cnt FROM elements_meta
            GROUP BY ifc_class, storey HAVING COUNT(*) >= 3
        )" 2>/dev/null || echo "0")

    printf "      Minable groups:   ${B}%s${R}  (ifc_class × storey, ≥3 instances)\n" "$minable"
    printf "      Covered elements: %s/%s\n" "$minable_elem" "$total"

    # Per-class detail for minable groups
    if [ "$minable" != "0" ]; then
        sqlite3 "$ref_db" "
            SELECT ifc_class, storey, COUNT(*) as n,
                   ROUND(AVG((r.maxX-r.minX)*1000)) as W,
                   ROUND(AVG((r.maxY-r.minY)*1000)) as D,
                   ROUND(AVG((r.maxZ-r.minZ)*1000)) as H
            FROM elements_meta em JOIN elements_rtree r ON em.id = r.id
            GROUP BY ifc_class, storey HAVING COUNT(*) >= 3
            ORDER BY n DESC
        " 2>/dev/null | while IFS='|' read -r cls st n w d h; do
            printf "        %-25s %-15s %3s × avg %sx%sx%s mm\n" "$cls" "$st" "$n" "$w" "$d" "$h"
        done
    fi
    echo ""

    # ── 6. Onboarding Command ────────────────────────────────

    printf "    ${B}Onboarding${R}\n"
    # Check if already onboarded
    already=""
    for yf in IFCtoBOM/src/main/resources/classify_*.yaml; do
        [ -f "$yf" ] || continue
        ybt=$(grep -m1 "building_type:" "$yf" | sed 's/.*building_type:\s*//' | sed "s/['\"]//g" | xargs)
        if [ "$ybt" = "$bt" ]; then
            already="$yf"
            break
        fi
    done
    if [ -n "$already" ]; then
        printf "      ${G}Already onboarded:${R} %s\n" "$(basename "$already")"
    else
        printf "      ${Y}Not yet onboarded.${R} Next steps:\n"
        printf "      ${D}1. Create YAML:  classify_xx.yaml + dsl_xx.bim${R}\n"
        printf "      ${D}2. Add to:       scripts/construction_manifest.yaml${R}\n"
        printf "      ${D}3. Add to:       GATE_SCOPE in BuildingRegistryTest + RosettaStoneGateTest${R}\n"
        printf "      ${D}4. Run:          ./scripts/run_RosettaStones.sh classify_xx.yaml${R}\n"
        printf "      ${D}See:             docs/IFC_ONBOARDING_RUNBOOK.md${R}\n"
    fi
    echo ""
done
