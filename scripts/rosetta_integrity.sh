#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Integrity Checks
#
# Sourced by run_RosettaStones.sh. Provides:
#   run_integrity <label> <output_db> <bom_db>
#
# Requires: log_helper.sh (verdict), lib_rosetta_helpers.sh (print_header)
# ============================================================

# ── Step 2: Integrity checks (Rule 8 + Clash) ───────────────
run_integrity() {
    local label="$1"
    local output_db="$2"
    local bom_db="$3"

    print_header "INTEGRITY ${label}"

    if [ ! -f "$output_db" ]; then
        echo "  SKIP — output DB not found: ${output_db}"
        return
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
    CLASH=$(sqlite3 "$output_db" "
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
    if [ "$CLASH" = "0" ] || [ "$CLASH" = "N/A" ]; then
        echo "    PASS — ${CLASH} furniture clashes"
        verdict "CLASH_${label}" "PASS" "${CLASH} furniture clashes"
    else
        verdict "CLASH_${label}" "FAIL" "${CLASH} furniture AABB overlaps"
        sqlite3 "$output_db" "
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
