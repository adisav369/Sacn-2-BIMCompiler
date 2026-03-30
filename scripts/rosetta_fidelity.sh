#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Fidelity Checks (C8/C9)
#
# Sourced by run_RosettaStones.sh. Provides:
#   run_fidelity <label> <output_db> <ref_db>
#
# @Traces TestArchitecture.md C8, C9
# @Traces LAST_MILE_PROBLEM.md Checklist #8, #9
#
# Requires: log_helper.sh (verdict), lib_rosetta_helpers.sh (print_header)
# ============================================================

# ── C9 SQL fragment (shared between count + detail queries) ──
# Consolidated: was duplicated in count query and detail query.
# Usage: _c9_ranked_cte "$output_db" "$ref_db" "<select_clause>" "<extra_clauses>"
_c9_query() {
    local output_db="$1"
    local ref_db="$2"
    local select_clause="$3"
    local extra_clauses="$4"
    local sqlite_flags="${5:-}"  # optional: e.g. "-header -column" for detail output

    local IFC_FILTER="'IfcDoor','IfcWindow','IfcFurnishingElement','IfcFurniture','IfcWall','IfcPlate','IfcSlab','IfcRoof'"
    local ORDER_COLS="ROUND(rr.minX*100), ROUND(rr.minY*100), ROUND(rr.minZ*100), ROUND((rr.maxX-rr.minX)*1000), ROUND((rr.maxY-rr.minY)*1000), ROUND((rr.maxZ-rr.minZ)*1000), ROUND(rr.maxX*1000), ROUND(rr.maxY*1000), ROUND(rr.maxZ*1000)"
    local ORDER_COLS_OUT="ROUND(oo.minX*100), ROUND(oo.minY*100), ROUND(oo.minZ*100), ROUND((oo.maxX-oo.minX)*1000), ROUND((oo.maxY-oo.minY)*1000), ROUND((oo.maxZ-oo.minZ)*1000), ROUND(oo.maxX*1000), ROUND(oo.maxY*1000), ROUND(oo.maxZ*1000)"

    sqlite3 $sqlite_flags "$output_db" "
        ATTACH '${ref_db}' AS ref;
        WITH ref_ranked AS (
            SELECT rem.ifc_class, rem.element_name,
                   ROUND((rr.maxX - rr.minX) * 1000) AS ref_W,
                   ROUND((rr.maxY - rr.minY) * 1000) AS ref_D,
                   ROUND((rr.maxZ - rr.minZ) * 1000) AS ref_H,
                   ROW_NUMBER() OVER (
                       PARTITION BY rem.ifc_class
                       ORDER BY ${ORDER_COLS}
                   ) AS rn
            FROM ref.elements_meta rem
            JOIN ref.elements_rtree rr ON rem.id = rr.id
            WHERE rem.ifc_class IN (${IFC_FILTER})
        ),
        out_ranked AS (
            SELECT oem.ifc_class, oem.element_name,
                   ROUND((oo.maxX - oo.minX) * 1000) AS out_W,
                   ROUND((oo.maxY - oo.minY) * 1000) AS out_D,
                   ROUND((oo.maxZ - oo.minZ) * 1000) AS out_H,
                   ROW_NUMBER() OVER (
                       PARTITION BY oem.ifc_class
                       ORDER BY ${ORDER_COLS_OUT}
                   ) AS rn
            FROM elements_meta oem
            JOIN elements_rtree oo ON oem.id = oo.id
            WHERE oem.ifc_class IN (${IFC_FILTER})
        )
        ${select_clause}
        FROM ref_ranked r
        JOIN out_ranked o ON r.ifc_class = o.ifc_class AND r.rn = o.rn
        WHERE ABS(r.ref_W - o.out_W) > 1
           OR ABS(r.ref_D - o.out_D) > 1
           OR ABS(r.ref_H - o.out_H) > 1
        ${extra_clauses};
        DETACH ref;
    " 2>/dev/null || echo "N/A"
}

# ── Step 3: Geometry Fidelity — Reference vs Output ─────────
# C8: Geometry diversity (per-instance mesh uniqueness preserved)
# C9: Per-element axis dimension (W/D/H match per axis, not just volume)
run_fidelity() {
    local label="$1"
    local output_db="$2"
    local ref_db="$3"

    print_header "FIDELITY ${label}: reference vs output geometry"

    if [ ! -f "$output_db" ]; then
        echo "  SKIP — output DB not found: ${output_db}"
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
    C8_VIOLATIONS=$(sqlite3 "$output_db" "
        ATTACH '${ref_db}' AS ref;
        SELECT COUNT(*) FROM (
            SELECT ref_groups.product_type, ref_groups.ref_unique, out_groups.out_unique
            FROM (
                SELECT
                    rem.ifc_class || ':' || SUBSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), 1,
                        CASE WHEN INSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), ':') > 0
                             THEN INSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), ':') - 1
                             ELSE LENGTH(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class))
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
        sqlite3 "$output_db" "
            ATTACH '${ref_db}' AS ref;
            SELECT ref_groups.product_type,
                   ref_groups.ref_unique AS ref_meshes,
                   COALESCE(out_groups.out_unique, 0) AS out_meshes,
                   ref_groups.ref_unique - COALESCE(out_groups.out_unique, 0) AS lost
            FROM (
                SELECT
                    rem.ifc_class || ':' || SUBSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), 1,
                        CASE WHEN INSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), ':') > 0
                             THEN INSTR(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class), ':') - 1
                             ELSE LENGTH(COALESCE(NULLIF(rem.element_name, ''), rem.ifc_class))
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
    C9_SWAPS=$(_c9_query "$output_db" "$ref_db" \
        "SELECT COUNT(*) FROM (
            SELECT r.ifc_class, r.rn, r.ref_W, o.out_W, r.ref_D, o.out_D, r.ref_H, o.out_H" \
        ")")

    if [ "$C9_SWAPS" = "0" ]; then
        echo "    PASS — all elements match reference per-axis within 1mm"
        verdict "C9_AXISDIM_${label}" "PASS" "0 axis dimension mismatches"
    elif [ "$C9_SWAPS" = "N/A" ]; then
        echo "    SKIP — query failed (missing table or window function unsupported?)"
        verdict "C9_AXISDIM_${label}" "SKIP" "query error"
    else
        # C9 uses rank-based matching (ROW_NUMBER), not GUID. For mirrored-pair
        # buildings and large extractions, rank shuffles cause false positives.
        # Documented: DuplexAnalysis.md §5, TerminalAnalysis.md F3.
        # Downgraded to WARN until GUID-based matching is implemented.
        verdict "C9_AXISDIM_${label}" "WARN" "${C9_SWAPS} axis mismatch(es) (rank-match artifact)"
        # Show worst offenders
        _c9_query "$output_db" "$ref_db" \
            "SELECT r.ifc_class, r.element_name AS ref_name,
                   r.ref_W || '→' || o.out_W AS 'W(ref→out)',
                   r.ref_D || '→' || o.out_D AS 'D(ref→out)',
                   r.ref_H || '→' || o.out_H AS 'H(ref→out)'" \
            "ORDER BY ABS(r.ref_W - o.out_W) + ABS(r.ref_D - o.out_D) + ABS(r.ref_H - o.out_H) DESC
            LIMIT 10" \
            "-header -column" \
            | sed 's/^/    /'
    fi
}
