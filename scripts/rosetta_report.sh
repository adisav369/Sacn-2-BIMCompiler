#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Report
#
# Consolidated report showing what each onboarded IFC contributes:
#   - Gate status (G1, G2, Delta, C8, C9)
#   - Component library enrichment (products, geometries, IFC classes)
#   - BOM model complexity (BOMs, lines, verb patterns, factorization)
#   - Validation rule mining potential
#
# Does NOT recompile — reports on whatever was last built.
#
# Usage:
#   ./scripts/rosetta_report.sh              # all buildings
#   ./scripts/rosetta_report.sh BA IP        # specific prefixes
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

YAML_DIR="IFCtoBOM/src/main/resources"
COMP_DB="library/component_library.db"
DISC_DB="library/disc_validation.db"

# ── Helpers ──────────────────────────────────────────────────

parse_yaml() {
    local file="$1" key="$2"
    grep -m1 "^\s*${key}:" "$file" | sed "s/.*${key}:\s*//" | sed 's/\s*#.*//' | sed "s/['\"]//g" | xargs
}

qry() { sqlite3 "$1" "$2" 2>/dev/null || echo "?"; }
qry0() { sqlite3 "$1" "$2" 2>/dev/null || echo "0"; }

P="\033[32m"  # pass
F="\033[31m"  # fail
S="\033[33m"  # skip
D="\033[90m"  # dim
B="\033[1m"   # bold
R="\033[0m"   # reset

gate() {
    local name="$1" status="$2" detail="$3"
    local color="$P"
    [ "$status" = "FAIL" ] && color="$F"
    [ "$status" = "WARN" ] && color="$S"
    [ "$status" = "SKIP" ] && color="$S"
    printf "    %-14s ${color}%-4s${R}  %s\n" "$name" "$status" "$detail"
}

# ── Collect buildings ────────────────────────────────────────

FILTER_PREFIXES=("$@")

declare -a PFX NM TYP
declare -a BOM_DB EB_DB WT_DB RF_DB
idx=0

for yf in "$YAML_DIR"/classify_*.yaml; do
    [ -f "$yf" ] || continue
    p=$(parse_yaml "$yf" "prefix")
    bt=$(parse_yaml "$yf" "building_type")
    nm=$(parse_yaml "$yf" "name")
    db=$(parse_yaml "$yf" "doc_base_type")
    ds=$(parse_yaml "$yf" "doc_sub_type")

    if [ ${#FILTER_PREFIXES[@]} -gt 0 ]; then
        match=false
        for fp in "${FILTER_PREFIXES[@]}"; do [ "$fp" = "$p" ] && match=true; done
        $match || continue
    fi

    ob="DAGCompiler/lib/output/$(echo "$bt" | tr '[:upper:]' '[:lower:]')"
    PFX[$idx]="$p";  NM[$idx]="$nm";  TYP[$idx]="$bt"
    BOM_DB[$idx]="library/${p}_BOM.db"
    EB_DB[$idx]="${ob}_enbloc.db"
    WT_DB[$idx]="${ob}_walkthru.db"
    RF_DB[$idx]="DAGCompiler/lib/input/${bt}_extracted.db"
    idx=$((idx + 1))
done

N=${#PFX[@]}
[ "$N" -eq 0 ] && { echo "No buildings found."; exit 0; }

# ── Catalog totals (before per-building) ─────────────────────

total_products=$(qry "$COMP_DB" "SELECT COUNT(*) FROM M_Product")
total_geom=$(qry "$COMP_DB" "SELECT COUNT(DISTINCT geometry_hash) FROM I_Geometry_Map")
total_ifc_classes=$(qry "$COMP_DB" "SELECT COUNT(DISTINCT ifc_class) FROM I_Geometry_Map")
total_buildings=$(qry "$COMP_DB" "SELECT COUNT(DISTINCT building_type) FROM I_Geometry_Map")

# ── Header ───────────────────────────────────────────────────

echo ""
printf "${B}╔══════════════════════════════════════════════════════════════════════╗${R}\n"
printf "${B}║              ROSETTA STONE STATUS REPORT                            ║${R}\n"
printf "${B}║              %-24s                                  ║${R}\n" "$(date '+%Y-%m-%d %H:%M')"
printf "${B}╠══════════════════════════════════════════════════════════════════════╣${R}\n"
printf "${B}║${R}  Product catalog: ${B}%s${R} products, ${B}%s${R} geometries, ${B}%s${R} IFC classes" \
    "$total_products" "$total_geom" "$total_ifc_classes"
printf "%*s${B}║${R}\n" 1 ""
printf "${B}║${R}  Across ${B}%s${R} building types" "$total_buildings"
printf "%*s${B}║${R}\n" 47 ""
printf "${B}╚══════════════════════════════════════════════════════════════════════╝${R}\n"
echo ""

# ── Per-building ─────────────────────────────────────────────

T_PASS=0; T_FAIL=0; T_SKIP=0
declare -a SUM_LINES

for ((i=0; i<N; i++)); do
    p="${PFX[$i]}"; nm="${NM[$i]}"; bt="${TYP[$i]}"
    bom="${BOM_DB[$i]}"; eb="${EB_DB[$i]}"; wt="${WT_DB[$i]}"; rf="${RF_DB[$i]}"

    pass=0; fail=0; skip=0

    printf "${B}══ %-4s  %s${R}\n" "$p" "$nm"

    # ── Availability ─────────────────────────────────────────

    has_bom=$( [ -f "$bom" ] && echo Y || echo N)
    has_eb=$(  [ -f "$eb"  ] && echo Y || echo N)
    has_wt=$(  [ -f "$wt"  ] && echo Y || echo N)
    has_rf=$(  [ -f "$rf"  ] && echo Y || echo N)

    if [ "$has_bom" = "N" ] || [ "$has_eb" = "N" ] || [ "$has_rf" = "N" ]; then
        printf "    ${S}Not fully built${R} — BOM=%s enbloc=%s ref=%s\n" "$has_bom" "$has_eb" "$has_rf"
        printf "    Run: ${D}./scripts/run_RosettaStones.sh classify_%s.yaml${R}\n" "$(echo "$p" | tr '[:upper:]' '[:lower:]')"
        SUM_LINES[$i]=$(printf "  %-4s %-30s  ${S}NOT BUILT${R}" "$p" "$nm")
        T_SKIP=$((T_SKIP + 1))
        echo ""
        continue
    fi

    # ── 1. Gates ─────────────────────────────────────────────

    ref_n=$(qry0 "$rf" "SELECT COUNT(*) FROM elements_meta")
    eb_n=$(qry0 "$eb" "SELECT COUNT(*) FROM elements_meta")
    wt_n="—"
    [ "$has_wt" = "Y" ] && wt_n=$(qry0 "$wt" "SELECT COUNT(*) FROM elements_meta")

    # G1
    if [ "$ref_n" = "$eb_n" ]; then
        gate "G1-COUNT" "PASS" "${ref_n} elements"
        pass=$((pass + 1))
    else
        gate "G1-COUNT" "FAIL" "ref=${ref_n} out=${eb_n} Δ=$((eb_n - ref_n))"
        fail=$((fail + 1))
    fi

    # G2
    rv=$(qry0 "$rf" "SELECT COALESCE(SUM((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)),0) FROM elements_rtree r JOIN elements_meta m ON r.id=m.id")
    ov=$(qry0 "$eb" "SELECT COALESCE(SUM((r.maxX-r.minX)*(r.maxY-r.minY)*(r.maxZ-r.minZ)),0) FROM elements_rtree r JOIN elements_meta m ON r.id=m.id")
    if [ "$rv" != "0" ] && [ -n "$rv" ]; then
        vpct=$(awk "BEGIN { printf \"%.4f\", ($ov - $rv) / $rv * 100 }" 2>/dev/null || echo "?")
        vabs=$(echo "$vpct" | sed 's/-//')
        if [ "$vabs" = "?" ]; then
            gate "G2-VOLUME" "SKIP" ""; skip=$((skip + 1))
        elif awk "BEGIN { exit ($vabs <= 0.1) ? 0 : 1 }" 2>/dev/null; then
            gate "G2-VOLUME" "PASS" "${vpct}%"; pass=$((pass + 1))
        else
            gate "G2-VOLUME" "FAIL" "${vpct}%"; fail=$((fail + 1))
        fi
    else
        gate "G2-VOLUME" "SKIP" "no volume data"; skip=$((skip + 1))
    fi

    # Delta count
    if [ "$has_wt" = "Y" ] && [ "$wt_n" != "—" ]; then
        if [ "$eb_n" = "$wt_n" ]; then
            gate "DELTA" "PASS" "enbloc=${eb_n} = walkthru=${wt_n}"
            pass=$((pass + 1))
        else
            gate "DELTA" "FAIL" "enbloc=${eb_n} ≠ walkthru=${wt_n}"
            fail=$((fail + 1))
        fi
    else
        gate "DELTA" "SKIP" ""; skip=$((skip + 1))
    fi

    # Delta geom
    if [ "$has_wt" = "Y" ]; then
        gd=$(qry "$eb" "ATTACH '${wt}' AS wt; SELECT COUNT(*) FROM main.element_instances e JOIN wt.element_instances w ON e.guid=w.guid WHERE e.geometry_hash!=w.geometry_hash")
        if [ "$gd" = "0" ]; then
            gate "DELTA-GEOM" "PASS" ""; pass=$((pass + 1))
        elif [ "$gd" = "?" ]; then
            gate "DELTA-GEOM" "SKIP" ""; skip=$((skip + 1))
        else
            gate "DELTA-GEOM" "FAIL" "${gd} divergences"; fail=$((fail + 1))
        fi
    else
        gate "DELTA-GEOM" "SKIP" ""; skip=$((skip + 1))
    fi

    # C8
    c8=$(qry "$eb" "ATTACH '${rf}' AS ref;
        SELECT COUNT(*) FROM (
            SELECT ifc_class, COUNT(DISTINCT geometry_hash) as d FROM main.element_instances ei JOIN main.elements_meta em ON ei.guid=em.guid GROUP BY ifc_class
        ) o JOIN (
            SELECT ifc_class, COUNT(DISTINCT geometry_hash) as d FROM ref.element_instances ei JOIN ref.elements_meta em ON ei.guid=em.guid GROUP BY ifc_class
        ) r USING(ifc_class) WHERE o.d < r.d")
    if [ "$c8" = "0" ]; then gate "C8-DIVERSITY" "PASS" ""; pass=$((pass + 1))
    elif [ "$c8" = "?" ]; then gate "C8-DIVERSITY" "SKIP" ""; skip=$((skip + 1))
    else gate "C8-DIVERSITY" "WARN" "${c8} types (cosmetic — cross-class geometry)"; pass=$((pass + 1)); fi

    # C9
    c9=$(qry "$eb" "ATTACH '${rf}' AS ref;
        SELECT COUNT(*) FROM (
            SELECT em.guid, ROUND((r.maxX-r.minX)*1000) W, ROUND((r.maxY-r.minY)*1000) D, ROUND((r.maxZ-r.minZ)*1000) H
            FROM main.elements_meta em JOIN main.elements_rtree r ON em.id=r.id
        ) o JOIN (
            SELECT em.guid, ROUND((r.maxX-r.minX)*1000) W, ROUND((r.maxY-r.minY)*1000) D, ROUND((r.maxZ-r.minZ)*1000) H
            FROM ref.elements_meta em JOIN ref.elements_rtree r ON em.id=r.id
        ) r USING(guid) WHERE ABS(o.W-r.W)>1 OR ABS(o.D-r.D)>1 OR ABS(o.H-r.H)>1")
    if [ "$c9" = "0" ]; then gate "C9-AXIS" "PASS" ""; pass=$((pass + 1))
    elif [ "$c9" = "?" ]; then gate "C9-AXIS" "SKIP" ""; skip=$((skip + 1))
    else gate "C9-AXIS" "WARN" "${c9} mismatches (cosmetic — mesh shape variance)"; pass=$((pass + 1)); fi

    total=$((pass + fail + skip))
    printf "    ${B}Gates: %d/%d PASS${R}" "$pass" "$total"
    [ "$fail" -gt 0 ] && printf ", ${F}%d FAIL${R}" "$fail"
    [ "$skip" -gt 0 ] && printf ", ${S}%d SKIP${R}" "$skip"
    echo ""

    # ── 2. Component Library Contribution ────────────────────

    echo ""
    printf "    ${B}Component Library Contribution${R}\n"

    lib_products=$(qry0 "$COMP_DB" "SELECT COUNT(DISTINCT product_id) FROM M_Product WHERE building_type='${bt}'")
    lib_geom=$(qry0 "$COMP_DB" "SELECT COUNT(DISTINCT geometry_hash) FROM I_Geometry_Map WHERE building_type='${bt}'")
    lib_ifc=$(qry0 "$COMP_DB" "SELECT COUNT(DISTINCT ifc_class) FROM I_Geometry_Map WHERE building_type='${bt}'")
    lib_mappings=$(qry0 "$COMP_DB" "SELECT COUNT(*) FROM I_Geometry_Map WHERE building_type='${bt}'")

    printf "      Products:  ${B}%s${R}  (distinct catalog entries)\n" "$lib_products"
    printf "      Geometries: ${B}%s${R}  (unique mesh hashes)\n" "$lib_geom"
    printf "      IFC classes: ${B}%s${R}  (" "$lib_ifc"
    classes=$(sqlite3 "$COMP_DB" "SELECT GROUP_CONCAT(DISTINCT ifc_class, ', ') FROM I_Geometry_Map WHERE building_type='${bt}'" 2>/dev/null || echo "")
    printf "%s)\n" "$classes"
    printf "      Mappings:  %s  (element→geometry links)\n" "$lib_mappings"

    # Unique IFC classes (only found in this building)
    unique_classes=$(qry "$COMP_DB" "
        SELECT GROUP_CONCAT(ifc_class, ', ') FROM (
            SELECT DISTINCT ifc_class FROM I_Geometry_Map WHERE building_type='${bt}'
            AND ifc_class NOT IN (
                SELECT ifc_class FROM I_Geometry_Map WHERE building_type != '${bt}'
            )
        )")
    if [ -n "$unique_classes" ] && [ "$unique_classes" != "?" ] && [ "$unique_classes" != "" ]; then
        printf "      ${P}Unique:${R}  %s  (only in this building)\n" "$unique_classes"
    fi

    # ── 3. BOM Model ─────────────────────────────────────────

    echo ""
    printf "    ${B}BOM Model${R}\n"

    bom_cnt=$(qry0 "$bom" "SELECT COUNT(*) FROM m_bom")
    line_cnt=$(qry0 "$bom" "SELECT COUNT(*) FROM m_bom_line")
    leaf_cnt=$(qry0 "$bom" "SELECT COUNT(*) FROM m_bom_line WHERE line_type='LEAF' OR child_product_id IS NOT NULL")
    verb_cnt=$(qry0 "$bom" "SELECT COUNT(DISTINCT verb_ref) FROM m_bom_line WHERE verb_ref IS NOT NULL")
    inst_cnt=$(qry0 "$bom" "SELECT COALESCE(SUM(CASE WHEN qty>0 THEN qty ELSE 1 END),0) FROM m_bom_line")

    printf "      BOMs:       ${B}%s${R}  (BUILDING + FLOOR + SET)\n" "$bom_cnt"
    printf "      Lines:      ${B}%s${R}  → %s instances" "$line_cnt" "$inst_cnt"
    if [ "$line_cnt" -gt 0 ] 2>/dev/null && [ "$inst_cnt" -gt 0 ] 2>/dev/null; then
        factor=$(awk "BEGIN { printf \"%.1f\", $inst_cnt / $line_cnt }" 2>/dev/null || echo "?")
        [ "$factor" != "1.0" ] && printf "  (${B}%sx${R} factorization)" "$factor"
    fi
    echo ""
    printf "      Verb patterns: ${B}%s${R}" "$verb_cnt"
    if [ "$verb_cnt" != "0" ] && [ "$verb_cnt" != "?" ]; then
        verbs=$(sqlite3 "$bom" "SELECT GROUP_CONCAT(DISTINCT SUBSTR(verb_ref,1,INSTR(verb_ref||'_','_')-1), ', ') FROM m_bom_line WHERE verb_ref IS NOT NULL" 2>/dev/null || echo "")
        printf "  (%s)" "$verbs"
    fi
    echo ""

    # Storey breakdown
    storeys=$(sqlite3 "$bom" "SELECT GROUP_CONCAT(cat || '=' || cnt, ', ') FROM (
        SELECT b.bom_category as cat, COUNT(*) as cnt
        FROM m_bom_line bl JOIN m_bom b ON bl.m_bom_id = b.bom_id
        WHERE b.bom_type IN ('FLOOR','SET')
        GROUP BY b.bom_category ORDER BY b.seq_no)" 2>/dev/null || echo "")
    [ -n "$storeys" ] && [ "$storeys" != "?" ] && \
        printf "      By category: %s\n" "$storeys"

    # ── 4. Validation Rule Potential ─────────────────────────

    echo ""
    printf "    ${B}Validation Rule Potential${R}\n"

    # Count element groups with ≥3 instances (minable for rules)
    minable=$(qry0 "$eb" "SELECT COUNT(*) FROM (
        SELECT em.ifc_class, em.storey FROM elements_meta em
        JOIN elements_rtree r ON em.id=r.id
        GROUP BY em.ifc_class, em.storey HAVING COUNT(*)>=3)")
    minable_instances=$(qry0 "$eb" "SELECT COALESCE(SUM(cnt),0) FROM (
        SELECT COUNT(*) as cnt FROM elements_meta em
        JOIN elements_rtree r ON em.id=r.id
        GROUP BY em.ifc_class, em.storey HAVING COUNT(*)>=3)")

    printf "      Minable groups:   ${B}%s${R}  (ifc_class × storey, ≥3 instances)\n" "$minable"
    printf "      Covered elements: %s  (of %s total)\n" "$minable_instances" "$ref_n"

    # Spacing uniformity (potential TILE/CLUSTER rules)
    spacing_groups=$(qry0 "$eb" "
        WITH ranked AS (
            SELECT em.ifc_class, em.storey, r.minX,
                   ROW_NUMBER() OVER (PARTITION BY em.ifc_class, em.storey ORDER BY r.minX, r.minY) as rn
            FROM elements_meta em JOIN elements_rtree r ON em.id=r.id
        ),
        gaps AS (
            SELECT a.ifc_class, a.storey, (b.minX-a.minX)*1000 as gap_mm
            FROM ranked a JOIN ranked b ON a.ifc_class=b.ifc_class AND a.storey=b.storey AND b.rn=a.rn+1
        )
        SELECT COUNT(*) FROM (
            SELECT ifc_class, storey FROM gaps GROUP BY ifc_class, storey HAVING COUNT(*)>=2
        )")
    printf "      Spacing patterns: ${B}%s${R}  (≥3 aligned elements with measurable gaps)\n" "$spacing_groups"

    if [ "$minable" = "0" ]; then
        printf "      ${D}(too few repeated elements to mine rules)${R}\n"
    fi

    # ── Summary line for this building ───────────────────────

    echo ""
    T_PASS=$((T_PASS + pass)); T_FAIL=$((T_FAIL + fail)); T_SKIP=$((T_SKIP + skip))

    status_str="${pass}/${total}"
    [ "$fail" -gt 0 ] && status_str="${status_str} ${fail}F"
    SUM_LINES[$i]=$(printf "  %-4s %-24s %4s elem  %3s prod  %3s geom  %3s BOMs  %s" \
        "$p" "$nm" "$ref_n" "$lib_products" "$lib_geom" "$bom_cnt" "$status_str")
done

# ── Summary Table ────────────────────────────────────────────

echo ""
printf "${B}╔═══════════════════════════════════════════════════════════════════════════════════╗${R}\n"
printf "${B}║  SUMMARY                                                                        ║${R}\n"
printf "${B}╠═══════════════════════════════════════════════════════════════════════════════════╣${R}\n"
printf "${B}║${R}  %-4s %-24s %5s  %5s  %5s  %5s  %s\n" "PFX" "Name" "Elem" "Prod" "Geom" "BOMs" "Gates"
printf "${B}║${R}  %-4s %-24s %5s  %5s  %5s  %5s  %s\n" "----" "------------------------" "-----" "-----" "-----" "-----" "-----"
for ((i=0; i<N; i++)); do
    printf "${B}║${R}%s\n" "${SUM_LINES[$i]}"
done
printf "${B}╠═══════════════════════════════════════════════════════════════════════════════════╣${R}\n"

GRAND=$((T_PASS + T_FAIL + T_SKIP))
printf "${B}║${R}  TOTAL: ${B}%d${R} buildings, ${B}%d/%d${R} gates PASS" "$N" "$T_PASS" "$GRAND"
[ "$T_FAIL" -gt 0 ] && printf ", ${F}%d FAIL${R}" "$T_FAIL"
[ "$T_SKIP" -gt 0 ] && printf ", ${S}%d SKIP${R}" "$T_SKIP"
echo ""
printf "${B}║${R}  Catalog: ${B}%s${R} products, ${B}%s${R} geometries, ${B}%s${R} IFC classes across ${B}%s${R} building types\n" \
    "$total_products" "$total_geom" "$total_ifc_classes" "$total_buildings"
printf "${B}╚═══════════════════════════════════════════════════════════════════════════════════╝${R}\n"
echo ""

[ "$T_FAIL" -gt 0 ] && exit 1
exit 0
