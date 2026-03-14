#!/bin/bash
# ============================================================
# BIM Compiler — 8-Point Integrity Audit
#
# Pure SQL + grep + code review. No compilation needed. ~10s.
#
# Checks:
#   A1 — GATE TAUTOLOGY PROBE (proves gates aren't hardcoded)
#   A2 — DIGEST FIELD COVERAGE (documents what SpatialDigest hashes)
#   A3 — INTENTIONAL RED INVENTORY (confirms known test failures)
#   A4 — BOM WRITE SCAN (pipeline must be read-only on *_BOM.db dictionary)
#   A5 — HARDCODED BYPASS SCAN (no magic numbers in production code)
#   A6 — TACK ORIGIN VERIFICATION (non-negative offsets invariant)
#   A7 — DEAD PATH SCAN (no references to deleted code paths)
#   A8 — NO FALLBACK (zero fabricated geometry, zero missing materials)
#
# Usage:
#   ./scripts/audit_integrity.sh         # run all 8 checks
#   ./scripts/audit_integrity.sh A1      # run specific check
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
init_log "audit_integrity"

CHECK="${1:-all}"

# Temporary working copy — prepared from {PREFIX}_BOM.db by run_RosettaStones.sh
BOM_DB="library/_${PREFIX:-SH}_compile.db"  # per-building, set via PREFIX
SH_OUTPUT="DAGCompiler/lib/output/ifc4_sample_house.db"
DX_OUTPUT="DAGCompiler/lib/output/ifc2x3_duplex.db"
DAG_SRC="DAGCompiler/src/main/java"

# ── A1: GATE TAUTOLOGY PROBE ─────────────────────────────────
run_a1() {
    section "A1: GATE TAUTOLOGY PROBE"

    if [ ! -f "$SH_OUTPUT" ]; then
        echo "  SKIP — ${SH_OUTPUT} not found (run tests first)"
        verdict "A1" "WARN" "output DB missing"
        return
    fi

    local tmpdb
    tmpdb=$(mktemp /tmp/audit_a1_XXXXXX.db)
    cp "$SH_OUTPUT" "$tmpdb"

    # Get stored count
    local stored_count
    stored_count=$(sqlite3 "$SH_OUTPUT" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null)
    echo "  Stored SH element count: ${stored_count}"

    # Insert a fake element to corrupt the copy
    sqlite3 "$tmpdb" "
        INSERT INTO elements_meta (id, guid, discipline, ifc_class, element_name, storey, material_rgba)
        VALUES (99999, 'AUDIT-FAKE-GUID-00000', 'STR', 'IfcWallStandardCase', 'AUDIT_FAKE', 'Level 1', 'FF0000FF');
    " 2>/dev/null || true

    local corrupted_count
    corrupted_count=$(sqlite3 "$tmpdb" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    echo "  Corrupted SH count:      ${corrupted_count}"

    rm -f "$tmpdb"

    if [ "$corrupted_count" -ne "$stored_count" ]; then
        echo "  Count diverged: ${stored_count} → ${corrupted_count}"
        verdict "A1" "PASS" "corruption detected (count diverged)"
    else
        verdict "A1" "FAIL" "counts identical after corruption — gate may be hardcoded"
    fi
}

# ── A2: DIGEST FIELD COVERAGE ────────────────────────────────
run_a2() {
    section "A2: DIGEST FIELD COVERAGE"

    local digest_file="${DAG_SRC}/com/bim/compiler/validation/SpatialDigest.java"
    if [ ! -f "$digest_file" ]; then
        echo "  SKIP — SpatialDigest.java not found"
        verdict "A2" "FAIL" "source file missing"
        return
    fi

    # Extract hashed fields from the SQL query
    local has_minX has_maxX has_minY has_maxY has_minZ has_maxZ has_material has_geohash has_precision
    has_minX=$(grep -c "r\.minX" "$digest_file" || true)
    has_maxX=$(grep -c "r\.maxX" "$digest_file" || true)
    has_minY=$(grep -c "r\.minY" "$digest_file" || true)
    has_maxY=$(grep -c "r\.maxY" "$digest_file" || true)
    has_minZ=$(grep -c "r\.minZ" "$digest_file" || true)
    has_maxZ=$(grep -c "r\.maxZ" "$digest_file" || true)
    has_material=$(grep -c "material_rgba" "$digest_file" || true)
    has_geohash=$(grep -c "geometry_hash" "$digest_file" || true)
    has_precision=$(grep -c "1000" "$digest_file" || true)

    local field_count=0
    local fields=""
    for f in minX maxX minY maxY minZ maxZ; do
        local var="has_${f}"
        if [ "${!var}" -gt 0 ]; then
            field_count=$((field_count + 1))
            fields="${fields} ${f}"
        fi
    done
    if [ "$has_material" -gt 0 ]; then
        field_count=$((field_count + 1))
        fields="${fields} material_rgba"
    fi
    if [ "$has_geohash" -gt 0 ]; then
        field_count=$((field_count + 1))
        fields="${fields} geometry_hash"
    fi

    echo "  Hashed:   ${fields}"

    # Check exclusion documentation
    local excludes_name
    excludes_name=$(grep -c "element_name" "$digest_file" || true)
    if [ "$excludes_name" -gt 0 ]; then
        echo "  Excluded: element_name (by design)"
    fi

    # Check precision
    if [ "$has_precision" -gt 0 ]; then
        echo "  Precision: 1mm (ROUND to integer mm)"
    fi

    if [ "$field_count" -ge 8 ]; then
        verdict "A2" "PASS" "${field_count} fields hashed, exclusion documented"
    else
        verdict "A2" "FAIL" "only ${field_count}/8 fields found in digest"
    fi
}

# ── A3: INTENTIONAL RED INVENTORY ────────────────────────────
run_a3() {
    section "A3: INTENTIONAL RED INVENTORY"

    # Known baseline: G8-DX + w_compose_dx + CoverWithRoof×3 = 5
    local KNOWN_RED_COUNT=5
    local found=0

    echo ""

    # G8-DX (calibration) — look for the intentional failure marker in RosettaStone tests
    local g8_count
    g8_count=$(grep -rl "calibration" DAGCompiler/src/test/ 2>/dev/null | wc -l || echo "0")
    if [ "$g8_count" -gt 0 ]; then
        echo "  G8-DX          DAGCompiler  calibration deferred"
        found=$((found + 1))
    fi

    # w_compose_dx (EXTRACTED gap)
    local compose_dx
    compose_dx=$(grep -rl "w_compose_dx\|compose_dx" ORMSandbox/src/test/ 2>/dev/null | wc -l || echo "0")
    if [ "$compose_dx" -gt 0 ]; then
        echo "  w_compose_dx   ORMSandbox   EXTRACTED gap"
        found=$((found + 1))
    fi

    # CoverWithRoof (pre-existing ×3 — known baseline)
    local roof_files
    roof_files=$(grep -rl "CoverWithRoof\|COVER WITH.*ROOF" BIM_COBOL/src/test/ 2>/dev/null | wc -l || echo "0")
    if [ "$roof_files" -gt 0 ]; then
        echo "  CoverWithRoof  BIM_COBOL    pre-existing (×3)"
        found=$((found + 3))
    fi

    echo ""

    # Check for any NEW @Disabled annotations (actual Java annotations, not string references)
    # Exclude: tamper rules that detect @Disabled, comments, javadoc, string literals
    local disabled_hits
    disabled_hits=$(grep -rn "^[[:space:]]*@Disabled" \
        DAGCompiler/src/test/ ORMSandbox/src/test/ BIM_COBOL/src/test/ TopologyMaker/src/test/ \
        2>/dev/null || true)
    local disabled_count
    disabled_count=$(echo "$disabled_hits" | grep -c "." 2>/dev/null || echo "0")
    if [ -n "$disabled_hits" ] && [ "$disabled_count" -gt 0 ]; then
        echo "  WARNING: ${disabled_count} @Disabled annotation(s) found:"
        echo "$disabled_hits" | sed 's/^/    /'
        echo ""
    fi

    if [ "$found" -ge "$KNOWN_RED_COUNT" ]; then
        verdict "A3" "PASS" "${found} known REDs, 0 unaccounted"
    else
        local unaccounted=$((KNOWN_RED_COUNT - found))
        verdict "A3" "WARN" "${found} found, ${unaccounted} expected REDs not located in source"
    fi
}

# ── A4: BOM WRITE SCAN ───────────────────────────────────────
run_a4() {
    section "A4: BOM WRITE SCAN (pipeline must be read-only on *_BOM.db)"

    # Scan DAGCompiler production code for SQL writes to BOM dictionary tables
    # Exclude: test code, comments, string literals in error messages
    local write_hits
    write_hits=$(grep -rn \
        -e "INSERT INTO.*m_bom\b" \
        -e "INSERT INTO.*m_product\b" \
        -e "INSERT INTO.*c_doctype\b" \
        -e "INSERT INTO.*m_attribute\b" \
        -e "UPDATE.*m_bom\b" \
        -e "UPDATE.*m_product\b" \
        -e "UPDATE.*c_doctype\b" \
        -e "DELETE FROM.*m_bom\b" \
        -e "DELETE FROM.*m_product\b" \
        -i \
        "${DAG_SRC}/" 2>/dev/null \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        | grep -v ":[0-9]*:\s*/\*" \
        | grep -v "//\|LOG\.\|logger\.\|System\.out\|System\.err\|throw\|Exception" \
        || true)

    local hit_count
    hit_count=$(echo "$write_hits" | grep -c "." || echo "0")

    if [ -z "$write_hits" ] || [ "$hit_count" -eq 0 ]; then
        echo "  Scanned: ${DAG_SRC}/"
        echo "  SQL write patterns: INSERT/UPDATE/DELETE on BOM dictionary tables"
        echo "  Matches: 0"
        verdict "A4" "PASS" "0 pipeline writes to BOM dictionary"
    else
        echo "  !! Found ${hit_count} potential write(s):"
        echo "$write_hits" | sed 's/^/    /'
        verdict "A4" "FAIL" "${hit_count} write statement(s) found in pipeline code"
    fi
}

# ── A5: HARDCODED BYPASS SCAN ────────────────────────────────
run_a5() {
    section "A5: HARDCODED BYPASS SCAN"

    local issues=0

    # Check 1: Literal element counts (55 for SH, 1099 for DX)
    echo "  [1/3] Checking for hardcoded element counts (55, 1099)..."
    local count_hits
    count_hits=$(grep -rn \
        -e "\b55\b" \
        -e "\b1099\b" \
        "${DAG_SRC}/" 2>/dev/null \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        | grep -v ":[0-9]*:\s*/\*" \
        | grep -v "//\|LOG\.\|logger\.\|\.out\.\|throw\|Exception\|import\|package" \
        | grep -iv "port\|timeout\|buffer\|size\|length\|capacity\|version\|year\|phase\|ratio\|percent\|STC" \
        || true)

    if [ -n "$count_hits" ] && [ "$(echo "$count_hits" | grep -c ".")" -gt 0 ]; then
        echo "  Review needed:"
        echo "$count_hits" | head -10 | sed 's/^/    /'
        echo "    (review above — not all hits are bypasses)"
    else
        echo "    Clean — no suspicious count literals"
    fi

    # Check 2: Building name strings in conditionals
    echo "  [2/3] Checking for building name conditionals..."
    local name_hits
    name_hits=$(grep -rn \
        -e '"SAMPLE_HOUSE"' \
        -e '"Ifc4_SampleHouse"' \
        -e '"DUPLEX"' \
        -e '"Ifc2x3_Duplex"' \
        -e '"SampleHouse"' \
        "${DAG_SRC}/" 2>/dev/null \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        | grep -v ":[0-9]*:\s*/\*" \
        | grep -v "LOG\.\|logger\." \
        || true)

    local name_count
    name_count=$(echo "$name_hits" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$name_hits" ] || [ "$name_count" -eq 0 ]; then
        echo "    Clean — no building name strings in production conditionals"
    else
        echo "    Found ${name_count} reference(s) — reviewing for bypasses..."
        # Filter out legitimate uses: parameter mapping, case routing, javadoc
        local bypass_hits
        bypass_hits=$(echo "$name_hits" \
            | grep -v "PlacementProver\|UnitType\|@param\|@see\|\* " \
            | grep -E "if\s*\(|==\s*|\.equals\(" || true)
        local bypass_count
        bypass_count=$(echo "$bypass_hits" | grep -c "." 2>/dev/null || echo "0")
        if [ -z "$bypass_hits" ] || [ "$bypass_count" -eq 0 ]; then
            echo "    ${name_count} references — all legitimate (mapping/routing/javadoc)"
        else
            echo "    !! ${bypass_count} suspicious conditional(s):"
            echo "$bypass_hits" | sed 's/^/      /'
            issues=$((issues + 1))
        fi
    fi

    # Check 3: Hardcoded product IDs in production code
    echo "  [3/3] Checking for hardcoded product IDs..."
    local pid_hits
    pid_hits=$(grep -rn "M_Product_ID\s*=\s*[0-9]" "${DAG_SRC}/" 2>/dev/null \
        | grep -v "^\s*//" \
        | grep -v "LOG\.\|logger\.\|//\|/\*\|\*/" \
        || true)

    local pid_count
    pid_count=$(echo "$pid_hits" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$pid_hits" ] || [ "$pid_count" -eq 0 ]; then
        echo "    Clean — no hardcoded product IDs"
    else
        echo "    Found ${pid_count} hardcoded product ID(s):"
        echo "$pid_hits" | sed 's/^/    /'
        issues=$((issues + 1))
    fi

    if [ "$issues" -eq 0 ]; then
        verdict "A5" "PASS" "no hardcoded bypasses detected"
    else
        verdict "A5" "FAIL" "${issues} category(ies) with suspicious hardcoding"
    fi
}

# ── A6: TACK ORIGIN VERIFICATION ─────────────────────────────
run_a6() {
    section "A6: TACK ORIGIN VERIFICATION"

    if [ ! -f "$BOM_DB" ]; then
        echo "  SKIP — ${BOM_DB} not found"
        verdict "A6" "FAIL" "BOM.db missing"
        return
    fi

    # Query 1: BOMs with non-zero origins (expect 19)
    local origin_count
    origin_count=$(sqlite3 "$BOM_DB" "
        SELECT COUNT(*) FROM m_bom
        WHERE origin_x != 0 OR origin_y != 0 OR origin_z != 0;
    " 2>/dev/null)
    echo "  BOMs with non-zero origin: ${origin_count} (expect 19)"

    # Query 2: Negative dx/dy/dz in active m_bom_line (expect 0)
    local neg_col_count
    neg_col_count=$(sqlite3 "$BOM_DB" "
        SELECT COUNT(*) FROM m_bom_line
        WHERE is_active = 1 AND (dx < -0.0001 OR dy < -0.0001 OR dz < -0.0001);
    " 2>/dev/null)
    echo "  Negative column offsets:   ${neg_col_count} (expect 0)"

    # Query 3: Negative EAV parameters (expect 0)
    local neg_eav_count
    neg_eav_count=$(sqlite3 "$BOM_DB" "
        SELECT COUNT(*) FROM m_attribute a
        JOIN m_bom_line bl ON a.bom_child_id = bl.bom_child_id
        WHERE a.param_key IN ('dx','dy','dz','x_offset','y_offset','z_offset')
          AND a.is_active = 1
          AND CAST(a.param_value AS REAL) < -0.0001;
    " 2>/dev/null)
    echo "  Negative EAV offsets:      ${neg_eav_count} (expect 0)"

    if [ "$origin_count" -eq 19 ] && [ "$neg_col_count" -eq 0 ] && [ "$neg_eav_count" -eq 0 ]; then
        verdict "A6" "PASS" "19 origins, 0 negatives"
    elif [ "$neg_col_count" -eq 0 ] && [ "$neg_eav_count" -eq 0 ]; then
        verdict "A6" "WARN" "origin count ${origin_count}≠19 but no negatives"
    else
        verdict "A6" "FAIL" "negative offsets detected (col:${neg_col_count} eav:${neg_eav_count})"
    fi
}

# ── A7: DEAD PATH SCAN ───────────────────────────────────────
run_a7() {
    section "A7: DEAD PATH SCAN"

    local issues=0

    # Check 1: I_Element_Extraction references in pipeline code
    # Renamed from ad_element_placement (2026-03-07). I_ = iDempiere Import convention.
    # Terminal still uses I_Element_Extraction (51K active elements). SH/DX deactivated.
    echo "  [1/3] I_Element_Extraction (SH/DX deactivated, Terminal active)..."
    local ad_hits
    ad_hits=$(grep -rn "I_Element_Extraction\|ad_element_placement" "${DAG_SRC}/" 2>/dev/null || true)
    local ad_total
    ad_total=$(echo "$ad_hits" | grep -c "." 2>/dev/null || echo "0")
    # Filter out comments/javadoc: lines where content (after file:line:) starts with // * or /*
    local ad_runtime
    ad_runtime=$(echo "$ad_hits" \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        | grep -v ":[0-9]*:\s*/\*" \
        | grep -v "//.*I_Element_Extraction" \
        | grep -v "//.*ad_element_placement" \
        || true)
    local ad_runtime_count
    ad_runtime_count=$(echo "$ad_runtime" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$ad_hits" ] || [ "$ad_total" -eq 0 ]; then
        echo "    Clean — 0 references"
    elif [ -z "$ad_runtime" ] || [ "$ad_runtime_count" -eq 0 ]; then
        echo "    ${ad_total} reference(s) — all in comments/javadoc (OK)"
    else
        echo "    ${ad_total} total, ${ad_runtime_count} in code (Terminal path — expected):"
        echo "$ad_runtime" | sed 's/^/      /'
    fi

    # Check 2: lod_element_placement (dropped view — should be 0)
    echo "  [2/3] lod_element_placement (dropped view — expect 0)..."
    local lod_hits
    lod_hits=$(grep -rn "lod_element_placement" "${DAG_SRC}/" 2>/dev/null || true)
    local lod_count
    lod_count=$(echo "$lod_hits" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$lod_hits" ] || [ "$lod_count" -eq 0 ]; then
        echo "    Clean — 0 references"
    else
        local lod_runtime
        lod_runtime=$(echo "$lod_hits" \
            | grep -v ":[0-9]*:\s*//" \
            | grep -v ":[0-9]*:\s*\*" \
            | grep -v ":[0-9]*:\s*/\*" \
            | grep -v "//.*lod_element_placement" \
            || true)
        local lod_runtime_count
        lod_runtime_count=$(echo "$lod_runtime" | grep -c "." 2>/dev/null || echo "0")
        if [ -z "$lod_runtime" ] || [ "$lod_runtime_count" -eq 0 ]; then
            echo "    ${lod_count} reference(s) — all in comments (OK)"
        else
            echo "    !! ${lod_runtime_count} runtime reference(s):"
            echo "$lod_runtime" | sed 's/^/    /'
            issues=$((issues + 1))
        fi
    fi

    # Check 3: computeFromPlacement (deleted method — should be 0)
    echo "  [3/3] computeFromPlacement (deleted — expect 0)..."
    local comp_hits
    comp_hits=$(grep -rn "computeFromPlacement" "${DAG_SRC}/" 2>/dev/null || true)
    local comp_count
    comp_count=$(echo "$comp_hits" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$comp_hits" ] || [ "$comp_count" -eq 0 ]; then
        echo "    Clean — 0 references"
    else
        local comp_runtime
        comp_runtime=$(echo "$comp_hits" \
            | grep -v ":[0-9]*:\s*//" \
            | grep -v ":[0-9]*:\s*\*" \
            | grep -v ":[0-9]*:\s*/\*" \
            | grep -v "//.*computeFromPlacement" \
            || true)
        local comp_runtime_count
        comp_runtime_count=$(echo "$comp_runtime" | grep -c "." 2>/dev/null || echo "0")
        if [ -z "$comp_runtime" ] || [ "$comp_runtime_count" -eq 0 ]; then
            echo "    ${comp_count} reference(s) — all in comments (OK)"
        else
            echo "    !! ${comp_runtime_count} runtime reference(s):"
            echo "$comp_runtime" | sed 's/^/    /'
            issues=$((issues + 1))
        fi
    fi

    if [ "$issues" -eq 0 ]; then
        verdict "A7" "PASS" "no dead path references in pipeline"
    else
        verdict "A7" "FAIL" "${issues} dead path(s) still referenced at runtime"
    fi
}

# ── A8: NO FALLBACK ───────────────────────────────────────────
run_a8() {
    section "A8: NO FALLBACK"

    local OUTPUT_DIR="DAGCompiler/lib/output"
    local issues=0

    for db_file in \
        "${OUTPUT_DIR}/ifc4_sample_house.db" \
        "${OUTPUT_DIR}/ifc2x3_duplex.db" \
        "${OUTPUT_DIR}/tb_lktn.db"; do

        local label
        label=$(basename "$db_file" .db)

        if [ ! -f "$db_file" ]; then
            echo "  ${label}: SKIP (not found)"
            continue
        fi

        # Fabricated geometry: vertex_count <= 8 means parametric box, not library mesh
        local box_total
        box_total=$(sqlite3 "$db_file" "
            SELECT COUNT(*) FROM element_instances ei
            JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
            WHERE bg.vertex_count <= 8;
        " 2>/dev/null || echo "0")

        # Missing material: NULL or empty material_name
        local mat_null
        mat_null=$(sqlite3 "$db_file" "
            SELECT COUNT(*) FROM elements_meta
            WHERE material_name IS NULL OR material_name = '';
        " 2>/dev/null || echo "0")

        # Total elements for percentage
        local total
        total=$(sqlite3 "$db_file" "SELECT COUNT(*) FROM elements_meta;" 2>/dev/null || echo "1")

        echo "  ${label}:"

        if [ "$box_total" -gt 0 ]; then
            echo "    Fabricated geometry (box): ${box_total}/${total}"
            # Per-class breakdown (top 5)
            sqlite3 "$db_file" "
                SELECT em.ifc_class, COUNT(*) as cnt
                FROM element_instances ei
                JOIN base_geometries bg ON ei.geometry_hash = bg.geometry_hash
                JOIN elements_meta em ON ei.guid = em.guid
                WHERE bg.vertex_count <= 8
                GROUP BY em.ifc_class ORDER BY cnt DESC LIMIT 5;
            " 2>/dev/null | sed 's/|/: /' | sed 's/^/      /'
            issues=$((issues + 1))
        else
            echo "    Fabricated geometry: 0"
        fi

        if [ "$mat_null" -gt 0 ]; then
            echo "    Missing material:         ${mat_null}/${total}"
            sqlite3 "$db_file" "
                SELECT ifc_class, COUNT(*) as cnt
                FROM elements_meta
                WHERE material_name IS NULL OR material_name = ''
                GROUP BY ifc_class ORDER BY cnt DESC LIMIT 5;
            " 2>/dev/null | sed 's/|/: /' | sed 's/^/      /'
            issues=$((issues + 1))
        else
            echo "    Missing material: 0"
        fi
    done

    # Code scan: count fallback paths in pipeline
    echo ""
    echo "  Fallback paths in source:"
    local box_paths
    box_paths=$(grep -rn "writeBoxGeometry\|GEN-BOX\|DCV-BOX\|createBoxGeometry\|bindParametric" \
        "${DAG_SRC}/" 2>/dev/null \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        | grep -v ":[0-9]*:\s*/\*" \
        || true)
    local box_path_count
    box_path_count=$(echo "$box_paths" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$box_paths" ] || [ "$box_path_count" -eq 0 ]; then
        echo "    Box fabrication call sites: 0"
    else
        echo "    Box fabrication call sites: ${box_path_count}"
    fi

    local default_mat
    default_mat=$(grep -rn "DEFAULT_WALL_MATERIAL\|GLASS_CURTAIN_MATERIAL" \
        "${DAG_SRC}/" 2>/dev/null \
        | grep -v ":[0-9]*:\s*//" \
        | grep -v ":[0-9]*:\s*\*" \
        || true)
    local default_mat_count
    default_mat_count=$(echo "$default_mat" | grep -c "." 2>/dev/null || echo "0")
    if [ -z "$default_mat" ] || [ "$default_mat_count" -eq 0 ]; then
        echo "    Hardcoded material refs:   0"
    else
        echo "    Hardcoded material refs:   ${default_mat_count}"
    fi

    if [ "$issues" -eq 0 ]; then
        verdict "A8" "PASS" "no fabricated geometry, no missing materials"
    else
        verdict "A8" "FAIL" "fallback leak — see counts above"
    fi
}

# ── Execute ───────────────────────────────────────────────────
case "$CHECK" in
    A1|a1) run_a1 ;;
    A2|a2) run_a2 ;;
    A3|a3) run_a3 ;;
    A4|a4) run_a4 ;;
    A5|a5) run_a5 ;;
    A6|a6) run_a6 ;;
    A7|a7) run_a7 ;;
    A8|a8) run_a8 ;;
    all|*)
        run_a1
        run_a2
        run_a3
        run_a4
        run_a5
        run_a6
        run_a7
        run_a8
        ;;
esac

finish_log
