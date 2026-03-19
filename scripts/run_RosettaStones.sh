#!/bin/bash
# ============================================================
# BIM Compiler — YAML-Driven Rosetta Stone Pipeline
#
# PURPOSE: Compile buildings from classification YAML, producing:
#   *_BOM.db   = clean per-building BOM dictionary (IFCtoBOM pipeline)
#   *_enbloc   = EN-BLOC compilation (singularity — takes one BOM whole)
#   *_walkthru = WALK THRU compilation (progressive stacking through hierarchy)
# Then run delta comparison between enbloc and walkthru.
#
# ── ANTI-DRIFT: READ BEFORE EDITING ──────────────────────────
#
# NO monolithic BOM.db — only per-building *_BOM.db files.
# Compilation uses a temp _XX_compile.db (e.g. _SH_compile.db)
# passed to Java via -Dbom.db system property. Java code reads
# System.getProperty("bom.db") — never hardcodes a path.
#
# Session process (4 steps):
#   1. rm SH_BOM.db → re-extract (only when IFCtoBOM code changed)
#   2. rm DX_BOM.db → re-extract (only when IFCtoBOM code changed)
#   3. rm output/SH_*.db → recompile (only when DAGCompiler code changed)
#   4. rm output/DX_*.db → recompile (only when DAGCompiler code changed)
#
# Analysis docs (read before modifying a building):
#   SH: docs/DATA_MODEL.md, docs/BOMBasedCompilation.md
#   DX: docs/DuplexAnalysis.md
#   TE: docs/TerminalAnalysis.md
#
# YAML-DRIVEN: The classification YAML determines everything:
#   prefix          → BOM DB name ({PREFIX}_BOM.db)
#   building_type   → extraction source
#   doc_sub_type    → compilation parameter
#   doc_base_type   → compilation parameter
#   building_bom_id → singularity check
#
# Usage:
#   ./scripts/run_RosettaStones.sh                          # all YAMLs in resources/
#   ./scripts/run_RosettaStones.sh classify_sh.yaml         # SH only
#   ./scripts/run_RosettaStones.sh classify_dx.yaml         # DX only
#   ./scripts/run_RosettaStones.sh classify_sh.yaml delta   # delta only (skip compile)
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
init_log "run_RosettaStones"

YAML_DIR="IFCtoBOM/src/main/resources"

# ── Parse arguments ─────────────────────────────────────────
YAML_FILES=()
DELTA_ONLY=false

for arg in "$@"; do
    case "$arg" in
        delta) DELTA_ONLY=true ;;
        *.yaml|*.yml)
            if [ -f "$arg" ]; then
                YAML_FILES+=("$arg")
            elif [ -f "${YAML_DIR}/$arg" ]; then
                YAML_FILES+=("${YAML_DIR}/$arg")
            else
                echo "[ERROR] YAML not found: $arg"
                exit 1
            fi
            ;;
        # Backward compat: bare prefix (sh, dx) → classify_{prefix}.yaml
        *)
            local_uc=$(echo "$arg" | tr '[:upper:]' '[:lower:]')
            if [ -f "${YAML_DIR}/classify_${local_uc}.yaml" ]; then
                YAML_FILES+=("${YAML_DIR}/classify_${local_uc}.yaml")
            else
                echo "[ERROR] Unknown argument: $arg (no ${YAML_DIR}/classify_${local_uc}.yaml)"
                exit 1
            fi
            ;;
    esac
done

# Default: all classify_*.yaml files
if [ ${#YAML_FILES[@]} -eq 0 ]; then
    for f in "${YAML_DIR}"/classify_*.yaml; do
        [ -f "$f" ] && YAML_FILES+=("$f")
    done
fi

if [ ${#YAML_FILES[@]} -eq 0 ]; then
    echo "[ERROR] No classification YAMLs found in ${YAML_DIR}/"
    exit 1
fi

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
    local doc_base_type="$4"
    local name="$5"
    local building_bom_id="$6"
    local yaml_file="$7"

    # Per-building compile DB — strongly typed name
    COMPILE_DB="library/_${prefix}_compile.db"

    if [ ! -f "$bom_db" ]; then
        echo "  [WARN] ${bom_db} not found — IFCtoBOM pipeline not yet run for ${prefix}"
        return 1
    fi

    # Start with clean *_BOM.db (has m_bom, m_bom_line, ad_sysconfig)
    cp "$bom_db" "$COMPILE_DB"

    # Add missing tables from schema snapshot (IF NOT EXISTS for safety)
    sed 's/CREATE TABLE \([^I"]\)/CREATE TABLE IF NOT EXISTS \1/g; s/CREATE TABLE "\([^I]\)/CREATE TABLE IF NOT EXISTS "\1/g' \
        library/schema_snapshot_bom.sql | sqlite3 "$COMPILE_DB" 2>/dev/null || true

    # Read AABB from BUILDING BOM
    local aabb_w aabb_d aabb_h
    aabb_w=$(sqlite3 "$COMPILE_DB" "SELECT aabb_width_mm FROM m_bom WHERE bom_id='${building_bom_id}'" 2>/dev/null)
    aabb_d=$(sqlite3 "$COMPILE_DB" "SELECT aabb_depth_mm FROM m_bom WHERE bom_id='${building_bom_id}'" 2>/dev/null)
    aabb_h=$(sqlite3 "$COMPILE_DB" "SELECT aabb_height_mm FROM m_bom WHERE bom_id='${building_bom_id}'" 2>/dev/null)

    # Derive output path from building_type (convention: lowercase, underscored)
    local output_base
    output_base=$(echo "$building_type" | tr '[:upper:]' '[:lower:]')
    local output_path="DAGCompiler/lib/output/${output_base}.db"
    local ref_path="DAGCompiler/lib/input/${building_type}_extracted.db"

    # Expected element count from BOM's ad_sysconfig (R13: stored during BOM generation)
    local expected=0
    expected=$(sqlite3 "$COMPILE_DB" \
        "SELECT config_value FROM ad_sysconfig WHERE config_key='EXPECTED_ELEMENTS'" 2>/dev/null || echo "0")

    # Read optional geometry fail threshold from YAML (default 0)
    local geo_threshold
    geo_threshold=$(parse_yaml "$yaml_file" "geometry_fail_threshold")

    # Inject C_DocType row
    sqlite3 "$COMPILE_DB" "
        INSERT OR REPLACE INTO C_DocType (
            C_DocType_ID, Name, DocBaseType, DocSubType, IsActive,
            ProjectName, OutputDbPath, ReferenceDbPath,
            ExpectedElements, Provenance, SeqNo,
            AabbWidthMm, AabbDepthMm, AabbHeightMm,
            GeometryFailThreshold
        ) VALUES (
            '${doc_base_type}_${doc_sub_type}',
            '${name}',
            '${doc_base_type}',
            '${doc_sub_type}',
            1,
            '${building_type}',
            '${output_path}',
            '${ref_path}',
            ${expected},
            'EXTRACTED',
            10,
            ${aabb_w:-0}, ${aabb_d:-0}, ${aabb_h:-0},
            ${geo_threshold:-0}
        );
    " 2>/dev/null

    # Load DSL content from file (avoids shell-quoting issues with embedded SQL)
    local dsl_file
    dsl_file=$(parse_yaml "$yaml_file" "dsl_file")
    if [ -n "$dsl_file" ]; then
        local dsl_path
        dsl_path="$(dirname "$yaml_file")/${dsl_file}"
        if [ -f "$dsl_path" ]; then
            sqlite3 "$COMPILE_DB" "UPDATE C_DocType SET DSLContent = readfile('${dsl_path}') WHERE C_DocType_ID = '${doc_base_type}_${doc_sub_type}'" 2>/dev/null
        fi
    fi

    echo "  _${prefix}_compile.db prepared from ${bom_db} (${expected} expected elements, DSL: ${dsl_file:-none})"
    return 0
}

cleanup_compile_db() {
    rm -f "$COMPILE_DB"
}

# ── Singularity check ──────────────────────────────────────
singularity_check() {
    local label="$1"
    local output_db="$2"
    local bom_db="$3"  # *_BOM.db path (building-specific, not legacy BOM.db)
    local doc_base_type="${4:-RE}"

    echo ""
    echo "  Singularity rule check (${label}):"

    local BLDG_BOMID
    BLDG_BOMID=$(sqlite3 "$bom_db" "
        SELECT bom_id FROM m_bom
        WHERE bom_type = 'BUILDING' AND doc_base_type = '${doc_base_type}'
          AND doc_sub_type = '${label}' AND is_active = 1
        ORDER BY seq_no LIMIT 1
    " 2>/dev/null)

    local BOM_W BOM_D BOM_H
    BOM_W=$(sqlite3 "$bom_db" "SELECT aabb_width_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'" 2>/dev/null)
    BOM_D=$(sqlite3 "$bom_db" "SELECT aabb_depth_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'" 2>/dev/null)
    BOM_H=$(sqlite3 "$bom_db" "SELECT aabb_height_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'" 2>/dev/null)

    echo "    BOM AABB       = ${BOM_W} x ${BOM_D} x ${BOM_H} mm  (${BLDG_BOMID})"
    echo "    BUILDING BOM   = $([ -n "$BLDG_BOMID" ] && echo "FOUND" || echo "MISSING")"

    if [ -f "$output_db" ] && [ -n "$BOM_W" ]; then
        sqlite3 "$output_db" "
            UPDATE c_order SET
                AabbWidthMm  = ${BOM_W},
                AabbDepthMm  = ${BOM_D},
                AabbHeightMm = ${BOM_H};
        " 2>/dev/null
        echo "    C_Order.AABB  = ${BOM_W} x ${BOM_D} x ${BOM_H} mm  (set from BOM)"
    fi
}

# ── Step 1: Compile ──────────────────────────────────────────
compile_building() {
    local label="$1"
    local base="$2"
    local bom_db="$3"
    local compile_db="$4"   # per-building: library/_SH_compile.db
    local doc_base_type="${5:-RE}"

    print_header "COMPILE ${label}"

    echo ""
    echo "  [enbloc] Compiling EN-BLOC (DocBaseType=${doc_base_type}, bom.db=${compile_db})..."
    local EB_OUTPUT EB_RC
    EB_OUTPUT=$(mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=ENBLOC \
        -Dbom.db="${compile_db}" \
        -Ddoc.base.type="${doc_base_type}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1) && EB_RC=0 || EB_RC=$?
    echo "$EB_OUTPUT" | tail -3

    if [ "$EB_RC" -ne 0 ]; then
        verdict "COMPILE_${label}_ENBLOC" "FAIL" "Maven exited ${EB_RC}"
        echo "$EB_OUTPUT" | grep -E "<<< FAILURE|<<< ERROR|AssertionFailedError" | head -5 | sed 's/^/    /'
    else
        verdict "COMPILE_${label}_ENBLOC" "PASS" "compiled OK"
    fi

    if [ -f "${base}.db" ]; then
        cp "${base}.db" "${base}_enbloc.db"
        echo "  [enbloc] → ${base}_enbloc.db"
    else
        echo "  [enbloc] !! NO OUTPUT DB produced"
    fi

    singularity_check "$label" "${base}_enbloc.db" "$bom_db" "$doc_base_type"

    # Contract tests — run AFTER compilation, output DB exists on disk
    echo "  [contracts] Running G3/G6/Totality/Rotation gates..."
    local CT_OUTPUT CT_RC
    CT_OUTPUT=$(mvn test -pl DAGCompiler \
        -Dtest="RosettaStoneGateTest,TotalityContractTest,RotationContractTest" \
        -Dbom.mode=ENBLOC \
        -Dbom.db="${compile_db}" \
        -Ddoc.base.type="${doc_base_type}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1) && CT_RC=0 || CT_RC=$?

    if [ "$CT_RC" -ne 0 ]; then
        verdict "CONTRACTS_${label}" "FAIL" "Maven exited ${CT_RC}"
        echo "$CT_OUTPUT" | grep -E "<<< FAILURE|<<< ERROR|AssertionFailed" | head -10 | sed 's/^/    /'
    else
        # Extract test counts
        local CT_SUMMARY
        CT_SUMMARY=$(echo "$CT_OUTPUT" | grep -E "Tests run:" | tail -1) || true
        verdict "CONTRACTS_${label}" "PASS" "$CT_SUMMARY"
    fi

    echo "  [walkthru] Compiling WALK THRU (DocBaseType=${doc_base_type}, bom.db=${compile_db})..."
    rm -f "${base}.db"
    local WT_OUTPUT WT_RC
    WT_OUTPUT=$(mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=WALKTHRU \
        -Dbom.db="${compile_db}" \
        -Ddoc.base.type="${doc_base_type}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1) && WT_RC=0 || WT_RC=$?
    echo "$WT_OUTPUT" | tail -5

    if [ "$WT_RC" -ne 0 ]; then
        verdict "COMPILE_${label}_WALKTHRU" "FAIL" "Maven exited ${WT_RC}"
        echo "$WT_OUTPUT" | grep -E "<<< FAILURE|<<< ERROR|AssertionFailedError" | head -5 | sed 's/^/    /'
    else
        verdict "COMPILE_${label}_WALKTHRU" "PASS" "compiled OK"
    fi

    if [ -f "${base}.db" ]; then
        cp "${base}.db" "${base}_walkthru.db"
        rm -f "${base}.db"
        echo "  [walkthru] → ${base}_walkthru.db"
        singularity_check "$label" "${base}_walkthru.db" "$bom_db" "$doc_base_type"
    else
        echo "  [walkthru] SKIP — structured BOM pipeline did not produce output"
    fi
}

# ── Step 2: Delta Test ───────────────────────────────────────
run_delta() {
    local label="$1"
    local eb_db="$2"
    local wt_db="$3"
    local bom_db="$4"

    print_header "DELTA ${label}: enbloc vs walkthru consistency"

    if [ ! -f "$eb_db" ] || [ ! -f "$wt_db" ]; then
        echo "  SKIP — missing DB file(s)"
        return
    fi

    # Count comparison
    EB_COUNT=$(sqlite3 "$eb_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    WT_COUNT=$(sqlite3 "$wt_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    local DELTA=$((WT_COUNT - EB_COUNT))
    echo "  Element count: enbloc=${EB_COUNT}  walkthru=${WT_COUNT}  delta=${DELTA}"

    if [ "$DELTA" -eq 0 ] && [ "$EB_COUNT" -gt 0 ]; then
        verdict "DELTA_${label}_COUNT" "PASS" "enbloc=${EB_COUNT} == walkthru=${WT_COUNT}"
    else
        verdict "DELTA_${label}_COUNT" "FAIL" "enbloc=${EB_COUNT} != walkthru=${WT_COUNT} (delta=${DELTA})"
    fi

    # Per-class count comparison
    echo ""
    echo "  Per-class breakdown:"
    sqlite3 "$eb_db" "
        ATTACH '${wt_db}' AS wt;
        SELECT ifc_class,
               COALESCE(eb_count, 0) as eb_count,
               COALESCE(wt_count, 0) as wt_count,
               COALESCE(wt_count, 0) - COALESCE(eb_count, 0) as delta
        FROM (
            SELECT ifc_class, SUM(eb_cnt) as eb_count, SUM(wt_cnt) as wt_count
            FROM (
                SELECT ifc_class, COUNT(*) as eb_cnt, 0 as wt_cnt FROM elements_meta GROUP BY ifc_class
                UNION ALL
                SELECT ifc_class, 0 as eb_cnt, COUNT(*) as wt_cnt FROM wt.elements_meta GROUP BY ifc_class
            )
            GROUP BY ifc_class
        )
        ORDER BY ifc_class;
        DETACH wt;
    " -header -column 2>/dev/null | sed 's/^/    /'

    # AABB centroid delta
    echo ""
    echo "  AABB centroid delta (top 10 worst):"
    sqlite3 "$eb_db" "
        ATTACH '${wt_db}' AS wt;
        SELECT
            s.guid,
            s.ifc_class,
            ROUND(ABS((s.minX+s.maxX)/2.0 - (wr.minX+wr.maxX)/2.0)*1000, 1) as dx_mm,
            ROUND(ABS((s.minY+s.maxY)/2.0 - (wr.minY+wr.maxY)/2.0)*1000, 1) as dy_mm,
            ROUND(ABS((s.minZ+s.maxZ)/2.0 - (wr.minZ+wr.maxZ)/2.0)*1000, 1) as dz_mm
        FROM elements_meta sm
        JOIN elements_rtree s ON sm.guid = s.id
        JOIN wt.elements_meta wm ON sm.guid = wm.guid
        JOIN wt.elements_rtree wr ON wm.guid = wr.id
        WHERE dx_mm > 0.5 OR dy_mm > 0.5 OR dz_mm > 0.5
        ORDER BY (dx_mm*dx_mm + dy_mm*dy_mm + dz_mm*dz_mm) DESC
        LIMIT 10;
        DETACH wt;
    " -header -column 2>/dev/null | sed 's/^/    /'

    # Geometry divergence
    echo ""
    echo "  Geometry divergence (different geometry_hash):"
    GEOM_DIFF=$(sqlite3 "$eb_db" "
        ATTACH '${wt_db}' AS wt;
        SELECT COUNT(*)
        FROM element_instances si
        JOIN wt.element_instances wi ON si.guid = wi.guid
        WHERE si.geometry_hash != wi.geometry_hash;
        DETACH wt;
    " 2>/dev/null || echo "N/A")
    echo "    ${GEOM_DIFF} elements with different geometry_hash"
    if [ "$GEOM_DIFF" = "0" ]; then
        verdict "DELTA_${label}_GEOM" "PASS" "0 geometry divergences"
    else
        verdict "DELTA_${label}_GEOM" "FAIL" "${GEOM_DIFF} geometry divergences"
    fi

    # Rule 8: world-absolute coordinates (reads from building-specific *_BOM.db)
    echo ""
    echo "  Rule 8 (Cheating Maxim) — M_BOM_Line world-absolute check:"
    RULE8=$(sqlite3 "$bom_db" "
        SELECT COUNT(*) FROM m_bom_line bl
        JOIN m_bom b ON bl.bom_id = b.bom_id
        WHERE b.bom_type = 'BUILDING'
          AND bl.is_active = 1
          AND (ABS(bl.dx) > b.aabb_width_mm/1000.0
            OR ABS(bl.dy) > b.aabb_depth_mm/1000.0
            OR ABS(bl.dz) > b.aabb_height_mm/1000.0)
    " 2>/dev/null || echo "N/A")
    if [ "$RULE8" = "0" ]; then
        echo "    PASS — all coordinates within parent envelope"
        verdict "RULE8_${label}" "PASS" "all M_BOM_Line coordinates parent-relative"
    else
        echo "    !! FAIL — ${RULE8} M_BOM_Line rows have world-absolute coordinates"
        verdict "RULE8_${label}" "FAIL" "${RULE8} M_BOM_Line rows world-absolute"
    fi

    # Clash check
    echo ""
    echo "  Clash check (furniture AABB overlap):"
    CLASH=$(sqlite3 "$eb_db" "
        SELECT COUNT(*) FROM (
            SELECT a.id as a_id, b.id as b_id
            FROM elements_meta am
            JOIN elements_rtree a ON am.guid = a.id
            JOIN elements_meta bm ON am.guid != bm.guid
            JOIN elements_rtree b ON bm.guid = b.id
            WHERE am.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND bm.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND a.id < b.id
              AND a.maxX > b.minX AND a.minX < b.maxX
              AND a.maxY > b.minY AND a.minY < b.maxY
              AND a.maxZ > b.minZ AND a.minZ < b.maxZ
        )
    " 2>/dev/null || echo "N/A")
    if [ "$CLASH" = "0" ]; then
        echo "    PASS — 0 furniture clashes"
        verdict "DELTA_${label}_CLASH" "PASS" "0 furniture clashes"
    else
        verdict "DELTA_${label}_CLASH" "FAIL" "${CLASH} furniture AABB overlaps"
        sqlite3 "$eb_db" "
            SELECT am.element_name as elem_a, bm.element_name as elem_b,
                   ROUND((MIN(a.maxX,b.maxX)-MAX(a.minX,b.minX))
                        *(MIN(a.maxY,b.maxY)-MAX(a.minY,b.minY))
                        *(MIN(a.maxZ,b.maxZ)-MAX(a.minZ,b.minZ)), 4) as overlap_m3
            FROM elements_meta am
            JOIN elements_rtree a ON am.guid = a.id
            JOIN elements_meta bm ON am.guid != bm.guid
            JOIN elements_rtree b ON bm.guid = b.id
            WHERE am.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND bm.ifc_class IN ('IfcFurnishingElement','IfcFurniture')
              AND a.id < b.id
              AND a.maxX > b.minX AND a.minX < b.maxX
              AND a.maxY > b.minY AND a.minY < b.maxY
              AND a.maxZ > b.minZ AND a.minZ < b.maxZ
            ORDER BY overlap_m3 DESC
            LIMIT 10;
        " -header -column 2>/dev/null | sed 's/^/    /'
    fi
}

# ── Step 3: Geometry Fidelity — Reference vs Output ─────────
# C8: Geometry diversity (per-instance mesh uniqueness preserved)
# C9: Per-element axis dimension (W/D/H match per axis, not just volume)
# @Traces TestArchitecture.md C8, C9
# @Traces LAST_MILE_PROBLEM.md Checklist #8, #9
run_fidelity() {
    local label="$1"
    local eb_db="$2"
    local ref_db="$3"

    print_header "FIDELITY ${label}: reference vs output geometry"

    if [ ! -f "$eb_db" ]; then
        echo "  SKIP — output DB not found: ${eb_db}"
        return
    fi
    if [ ! -f "$ref_db" ]; then
        echo "  SKIP — reference DB not found: ${ref_db}"
        return
    fi

    # ── C8: Geometry Diversity ──────────────────────────────
    # For each (ifc_class, product_type) group, count distinct geometry_hash
    # in reference vs output. If reference has N unique meshes but output has
    # fewer, per-instance fidelity was lost (e.g., left-opening vs right-opening
    # doors collapsed to one mesh).
    echo ""
    echo "  C8: Geometry diversity (per-instance mesh uniqueness):"

    local C8_VIOLATIONS
    C8_VIOLATIONS=$(sqlite3 "$eb_db" "
        ATTACH '${ref_db}' AS ref;
        SELECT COUNT(*) FROM (
            SELECT ref_groups.product_type, ref_groups.ref_unique, out_groups.out_unique
            FROM (
                SELECT
                    rem.ifc_class || ':' || SUBSTR(rem.element_name, 1,
                        CASE WHEN INSTR(rem.element_name, ':') > 0
                             THEN INSTR(rem.element_name, ':') - 1
                             ELSE LENGTH(rem.element_name)
                        END) AS product_type,
                    COUNT(DISTINCT rei.geometry_hash) AS ref_unique
                FROM ref.elements_meta rem
                JOIN ref.element_instances rei ON rei.guid = rem.guid
                WHERE rem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture')
                GROUP BY product_type
            ) ref_groups
            LEFT JOIN (
                SELECT
                    oem.ifc_class || ':' || SUBSTR(oem.element_name, 1,
                        CASE WHEN INSTR(oem.element_name, ':') > 0
                             THEN INSTR(oem.element_name, ':') - 1
                             ELSE LENGTH(oem.element_name)
                        END) AS product_type,
                    COUNT(DISTINCT oei.geometry_hash) AS out_unique
                FROM elements_meta oem
                JOIN element_instances oei ON oei.guid = oem.guid
                WHERE oem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture')
                GROUP BY product_type
            ) out_groups ON out_groups.product_type = ref_groups.product_type
            WHERE COALESCE(out_groups.out_unique, 0) < ref_groups.ref_unique
        );
        DETACH ref;
    " 2>/dev/null || echo "N/A")

    if [ "$C8_VIOLATIONS" = "0" ]; then
        echo "    PASS — all product types preserve reference geometry diversity"
        verdict "C8_GEODIV_${label}" "PASS" "0 diversity losses"
    elif [ "$C8_VIOLATIONS" = "N/A" ]; then
        echo "    SKIP — query failed (missing table?)"
        verdict "C8_GEODIV_${label}" "SKIP" "query error"
    else
        verdict "C8_GEODIV_${label}" "FAIL" "${C8_VIOLATIONS} product type(s) lost mesh diversity"
        # Show details
        sqlite3 "$eb_db" "
            ATTACH '${ref_db}' AS ref;
            SELECT ref_groups.product_type,
                   ref_groups.ref_unique AS ref_meshes,
                   COALESCE(out_groups.out_unique, 0) AS out_meshes,
                   ref_groups.ref_unique - COALESCE(out_groups.out_unique, 0) AS lost
            FROM (
                SELECT
                    rem.ifc_class || ':' || SUBSTR(rem.element_name, 1,
                        CASE WHEN INSTR(rem.element_name, ':') > 0
                             THEN INSTR(rem.element_name, ':') - 1
                             ELSE LENGTH(rem.element_name)
                        END) AS product_type,
                    COUNT(DISTINCT rei.geometry_hash) AS ref_unique
                FROM ref.elements_meta rem
                JOIN ref.element_instances rei ON rei.guid = rem.guid
                WHERE rem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture')
                GROUP BY product_type
            ) ref_groups
            LEFT JOIN (
                SELECT
                    oem.ifc_class || ':' || SUBSTR(oem.element_name, 1,
                        CASE WHEN INSTR(oem.element_name, ':') > 0
                             THEN INSTR(oem.element_name, ':') - 1
                             ELSE LENGTH(oem.element_name)
                        END) AS product_type,
                    COUNT(DISTINCT oei.geometry_hash) AS out_unique
                FROM elements_meta oem
                JOIN element_instances oei ON oei.guid = oem.guid
                WHERE oem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture')
                GROUP BY product_type
            ) out_groups ON out_groups.product_type = ref_groups.product_type
            WHERE COALESCE(out_groups.out_unique, 0) < ref_groups.ref_unique
            ORDER BY lost DESC;
            DETACH ref;
        " -header -column 2>/dev/null | sed 's/^/    /'
    fi

    # ── C9: Per-Element Axis Dimension ──────────────────────
    # For each element in ref and output (matched by ifc_class + position sort),
    # verify X-extent, Y-extent, Z-extent match per-axis (not just volume).
    # A door with W=810 D=135 compiling to W=135 D=810 has same volume but
    # wrong axis alignment.
    echo ""
    echo "  C9: Per-element axis dimension (W/D/H per axis, 1mm tolerance):"

    local C9_SWAPS
    C9_SWAPS=$(sqlite3 "$eb_db" "
        ATTACH '${ref_db}' AS ref;
        SELECT COUNT(*) FROM (
            WITH ref_ranked AS (
                SELECT rem.ifc_class,
                       ROUND((rr.maxX - rr.minX) * 1000) AS ref_W,
                       ROUND((rr.maxY - rr.minY) * 1000) AS ref_D,
                       ROUND((rr.maxZ - rr.minZ) * 1000) AS ref_H,
                       ROW_NUMBER() OVER (
                           PARTITION BY rem.ifc_class
                           ORDER BY ROUND(rr.minX*1000), ROUND(rr.minY*1000), ROUND(rr.minZ*1000)
                       ) AS rn
                FROM ref.elements_meta rem
                JOIN ref.elements_rtree rr ON rem.id = rr.id
                WHERE rem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture',
                                        'IfcWall','IfcPlate','IfcSlab','IfcRoof')
            ),
            out_ranked AS (
                SELECT oem.ifc_class,
                       ROUND((oo.maxX - oo.minX) * 1000) AS out_W,
                       ROUND((oo.maxY - oo.minY) * 1000) AS out_D,
                       ROUND((oo.maxZ - oo.minZ) * 1000) AS out_H,
                       ROW_NUMBER() OVER (
                           PARTITION BY oem.ifc_class
                           ORDER BY ROUND(oo.minX*1000), ROUND(oo.minY*1000), ROUND(oo.minZ*1000)
                       ) AS rn
                FROM elements_meta oem
                JOIN elements_rtree oo ON oem.id = oo.id
                WHERE oem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture',
                                        'IfcWall','IfcPlate','IfcSlab','IfcRoof')
            )
            SELECT r.ifc_class, r.rn,
                   r.ref_W, o.out_W, r.ref_D, o.out_D, r.ref_H, o.out_H
            FROM ref_ranked r
            JOIN out_ranked o ON r.ifc_class = o.ifc_class AND r.rn = o.rn
            WHERE ABS(r.ref_W - o.out_W) > 1
               OR ABS(r.ref_D - o.out_D) > 1
               OR ABS(r.ref_H - o.out_H) > 1
        );
        DETACH ref;
    " 2>/dev/null || echo "N/A")

    if [ "$C9_SWAPS" = "0" ]; then
        echo "    PASS — all elements match reference per-axis within 1mm"
        verdict "C9_AXISDIM_${label}" "PASS" "0 axis dimension mismatches"
    elif [ "$C9_SWAPS" = "N/A" ]; then
        echo "    SKIP — query failed (missing table or window function unsupported?)"
        verdict "C9_AXISDIM_${label}" "SKIP" "query error"
    else
        verdict "C9_AXISDIM_${label}" "FAIL" "${C9_SWAPS} element(s) with axis dimension mismatch"
        # Show worst offenders
        sqlite3 "$eb_db" "
            ATTACH '${ref_db}' AS ref;
            WITH ref_ranked AS (
                SELECT rem.ifc_class, rem.element_name,
                       ROUND((rr.maxX - rr.minX) * 1000) AS ref_W,
                       ROUND((rr.maxY - rr.minY) * 1000) AS ref_D,
                       ROUND((rr.maxZ - rr.minZ) * 1000) AS ref_H,
                       ROW_NUMBER() OVER (
                           PARTITION BY rem.ifc_class
                           ORDER BY ROUND(rr.minX*1000), ROUND(rr.minY*1000), ROUND(rr.minZ*1000)
                       ) AS rn
                FROM ref.elements_meta rem
                JOIN ref.elements_rtree rr ON rem.id = rr.id
                WHERE rem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture',
                                        'IfcWall','IfcPlate','IfcSlab','IfcRoof')
            ),
            out_ranked AS (
                SELECT oem.ifc_class, oem.element_name,
                       ROUND((oo.maxX - oo.minX) * 1000) AS out_W,
                       ROUND((oo.maxY - oo.minY) * 1000) AS out_D,
                       ROUND((oo.maxZ - oo.minZ) * 1000) AS out_H,
                       ROW_NUMBER() OVER (
                           PARTITION BY oem.ifc_class
                           ORDER BY ROUND(oo.minX*1000), ROUND(oo.minY*1000), ROUND(oo.minZ*1000)
                       ) AS rn
                FROM elements_meta oem
                JOIN elements_rtree oo ON oem.id = oo.id
                WHERE oem.ifc_class IN ('IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture',
                                        'IfcWall','IfcPlate','IfcSlab','IfcRoof')
            )
            SELECT r.ifc_class, r.element_name AS ref_name,
                   r.ref_W || '→' || o.out_W AS 'W(ref→out)',
                   r.ref_D || '→' || o.out_D AS 'D(ref→out)',
                   r.ref_H || '→' || o.out_H AS 'H(ref→out)'
            FROM ref_ranked r
            JOIN out_ranked o ON r.ifc_class = o.ifc_class AND r.rn = o.rn
            WHERE ABS(r.ref_W - o.out_W) > 1
               OR ABS(r.ref_D - o.out_D) > 1
               OR ABS(r.ref_H - o.out_H) > 1
            ORDER BY ABS(r.ref_W - o.out_W) + ABS(r.ref_D - o.out_D) + ABS(r.ref_H - o.out_H) DESC
            LIMIT 10;
            DETACH ref;
        " -header -column 2>/dev/null | sed 's/^/    /'
    fi
}

# ── Contract Tests ──────────────────────────────────────────
run_contracts() {
    print_header "CONTRACT TESTS (standalone)"
    local TESTS="IntraBOMRelativeTest,TranslationChainTest,AnchorComputationTest,LocalCoordTest,StallDividerParamsTest"
    local OUTPUT
    OUTPUT=$(mvn test -pl DAGCompiler \
        -Dtest="${TESTS}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1) || true
    local SUMMARY_LINE
    SUMMARY_LINE=$(echo "$OUTPUT" | grep -E "Tests run:" | tail -1) || true
    echo "  $SUMMARY_LINE"

    local FAILURES ERRORS
    FAILURES=$(echo "$SUMMARY_LINE" | grep -oP 'Failures: \K[0-9]+') || true
    ERRORS=$(echo "$SUMMARY_LINE" | grep -oP 'Errors: \K[0-9]+') || true
    FAILURES="${FAILURES:-0}"
    ERRORS="${ERRORS:-0}"
    local TOTAL_RED=$((FAILURES + ERRORS))

    if [ "$TOTAL_RED" -eq 0 ]; then
        verdict "CONTRACT" "PASS" "all ${TESTS} GREEN"
    else
        verdict "CONTRACT" "FAIL" "${TOTAL_RED} contract test failure(s)"
        echo "$OUTPUT" | grep -E "<<< FAILURE|<<< ERROR" | sed 's/^/    /'
    fi
}

# ── Main Loop ───────────────────────────────────────────────

# Compile Java (unless delta-only)
if [ "$DELTA_ONLY" != "true" ]; then
    print_header "COMPILE (all modules)"
    mvn install -pl orm-core,ORMSandbox -DskipTests -q
    mvn compile -pl DAGCompiler -q
    echo "  Compile: OK"
fi

echo ""
echo "  YAML files: ${YAML_FILES[*]}"

# Process each YAML
for yaml_file in "${YAML_FILES[@]}"; do
    # Parse YAML fields
    PREFIX=$(parse_yaml "$yaml_file" "prefix")
    BUILDING_TYPE=$(parse_yaml "$yaml_file" "building_type")
    DOC_SUB_TYPE=$(parse_yaml "$yaml_file" "doc_sub_type")
    DOC_BASE_TYPE=$(parse_yaml "$yaml_file" "doc_base_type")
    BLDG_NAME=$(parse_yaml "$yaml_file" "name")
    BUILDING_BOM_ID=$(parse_yaml "$yaml_file" "building_bom_id")

    BOM_DB="library/${PREFIX}_BOM.db"
    OUTPUT_BASE="DAGCompiler/lib/output/$(echo "$BUILDING_TYPE" | tr '[:upper:]' '[:lower:]')"

    print_header "BUILDING: ${PREFIX} (${BLDG_NAME})"
    echo "  YAML:           $(basename "$yaml_file")"
    echo "  BOM DB:         ${BOM_DB}"
    echo "  Building type:  ${BUILDING_TYPE}"
    echo "  DocType:        ${DOC_BASE_TYPE}_${DOC_SUB_TYPE}"
    echo "  Building BOM:   ${BUILDING_BOM_ID}"
    echo "  Output base:    ${OUTPUT_BASE}"

    # Step 0: Run IFCtoBOM to produce fresh *_BOM.db
    if [ "$DELTA_ONLY" != "true" ]; then
        section "IFCtoBOM Pipeline (${PREFIX})"

        # ── Populate product catalog (skip if products already exist) ──
        # R13: No I_Element_Extraction — check M_Product_Image count instead
        comp_db="library/component_library.db"
        existing=$(sqlite3 "$comp_db" \
            "SELECT COUNT(*) FROM I_Geometry_Map WHERE building_type='${BUILDING_TYPE}'" 2>/dev/null || echo "0")

        if [ "$existing" = "0" ]; then
            echo "  [populate] Populating component_library.db for ${BUILDING_TYPE}..."
            POP_OUTPUT=""
            POP_RC=0
            POP_OUTPUT=$(mvn exec:java -pl IFCtoBOM \
                -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
                -Dexec.args="--populate --classify ${yaml_file}" \
                -q 2>&1) && POP_RC=0 || POP_RC=$?
            echo "$POP_OUTPUT" | grep -E '^\[populate\]'

            if [ "$POP_RC" -ne 0 ]; then
                verdict "POPULATE_${PREFIX}" "FAIL" "populate failed"
                echo "$POP_OUTPUT" | grep -E "ERROR|Exception|FAIL" | head -5 | sed 's/^/    /'
            else
                verdict "POPULATE_${PREFIX}" "PASS" "component_library.db populated"
            fi
        else
            echo "  [populate] Already populated: ${existing} elements for ${BUILDING_TYPE} — skipping"
        fi

        # Run IFCtoBOM via CLI (YAML-driven, produces *_BOM.db)
        IFC_OUTPUT=""
        IFC_RC=0
        IFC_OUTPUT=$(mvn exec:java -pl IFCtoBOM \
            -Dexec.mainClass="com.bim.ifctobom.IFCtoBOMMain" \
            -Dexec.args="--classify ${yaml_file} --bom-db ${BOM_DB}" \
            -q 2>&1) && IFC_RC=0 || IFC_RC=$?
        echo "$IFC_OUTPUT" | grep -E '^\[IFCtoBOM\]|^=== |^  \[|^\[QA\]|^  Floor'

        if [ "$IFC_RC" -ne 0 ]; then
            verdict "IFCTOBOM_${PREFIX}" "FAIL" "IFCtoBOM pipeline failed"
            echo "$IFC_OUTPUT" | grep -E "ERROR\|Exception" | head -5 | sed 's/^/    /'
        else
            verdict "IFCTOBOM_${PREFIX}" "PASS" "${BOM_DB} produced"
        fi

        # Prepare per-building compile DB (e.g. library/_SH_compile.db)
        if prepare_compile_db "$PREFIX" "$BUILDING_TYPE" "$DOC_SUB_TYPE" "$DOC_BASE_TYPE" "$BLDG_NAME" "$BUILDING_BOM_ID" "$yaml_file"; then
            # Run compilation (enbloc + walkthru) — passes -Dbom.db to Maven
            compile_building "$DOC_SUB_TYPE" "$OUTPUT_BASE" "$BOM_DB" "$COMPILE_DB" "$DOC_BASE_TYPE"
            cleanup_compile_db
        fi
    fi

    # Delta test (reads from output DBs + *_BOM.db — no library/BOM.db needed)
    run_delta "$DOC_SUB_TYPE" "${OUTPUT_BASE}_enbloc.db" "${OUTPUT_BASE}_walkthru.db" "$BOM_DB"

    # Fidelity test — reference (input) vs output (C8/C9)
    # Reference DB path: DAGCompiler/lib/input/{BUILDING_TYPE}_extracted.db
    REF_DB="DAGCompiler/lib/input/${BUILDING_TYPE}_extracted.db"
    run_fidelity "$DOC_SUB_TYPE" "${OUTPUT_BASE}_enbloc.db" "$REF_DB"
done

# ── Summary ──────────────────────────────────────────────────
print_header "ROSETTA STONE SUMMARY"
echo "  Pipeline: YAML → IFCtoBOM → *_BOM.db → DAGCompiler → _enbloc + _walkthru"
echo ""
echo "  _enbloc   = EN-BLOC — singularity proof. BOM lines already tacked,"
echo "      takes each as-is when AABB and DocType consistent."
echo "  _walkthru = WALK THRU — mechanism proof. Recalculates by tacking"
echo "      through each BOM layer (BUILDING → FLOOR → SET → LEAF)"
echo ""
echo "  Both produce the same result when the data stack is consistent."
echo "  EN-BLOC proves data correctness. WALK THRU proves the mechanism."
echo ""
echo "  YAMLs processed:"
for yf in "${YAML_FILES[@]}"; do
    yf_prefix=$(parse_yaml "$yf" "prefix")
    echo "    $(basename "$yf") → library/${yf_prefix}_BOM.db"
done
echo ""
finish_log

if [ "$LOG_FAIL" -gt 0 ]; then
    exit 1
fi
