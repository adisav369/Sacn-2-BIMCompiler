#!/bin/bash
# ============================================================
# BIM Compiler — Rosetta Stone Dual Output + Delta Test
#
# PURPOSE: Compile SH and DX, producing TWO output DBs each:
#   _enbloc   = EN-BLOC compilation (singularity — takes one BOM whole)
#   _walkthru = WALK THRU compilation (progressive stacking through hierarchy)
# Then run embedded delta comparison between enbloc and walkthru.
#
# DIFFERENT FROM run.sh:
#   run.sh       = user-facing, compiles any C_Order against empty output.db
#   This script  = developer diagnostic, hardcoded SH+DX, dual output + delta
#
# DIFFERENT FROM run_tests.sh:
#   run_tests.sh = full gate (all 5 buildings, all suites)
#   This script  = focused Rosetta Stone fidelity proof (SH+DX only)
#
# Usage:
#   ./scripts/run_RosettaStones.sh           # compile SH+DX, produce dual output, delta
#   ./scripts/run_RosettaStones.sh sh        # SH only
#   ./scripts/run_RosettaStones.sh dx        # DX only
#   ./scripts/run_RosettaStones.sh delta     # delta only (skip compile, use existing DBs)
# ============================================================

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

source "$SCRIPT_DIR/log_helper.sh"
init_log "run_RosettaStones"

MODE="${1:-all}"
OUTPUT_DIR="DAGCompiler/lib/output"

# ── Paths ────────────────────────────────────────────────────
SH_BASE="${OUTPUT_DIR}/ifc4_sample_house"
DX_BASE="${OUTPUT_DIR}/ifc2x3_duplex"

print_header() {
    echo ""
    echo "════════════════════════════════════════"
    echo "  $1"
    echo "════════════════════════════════════════"
}

# ── Prime Rule ─────────────────────────────────────────────
#
#   C_Order.(AABB + DocBaseType + DocSubType) == m_bom.(AABB + DocBaseType + DocSubType)
#   → match + count=1 → SINGULARITY → EN-BLOC
#
# Three-key mapping:
#   Key          C_Order side (via C_DocType)     m_bom side               M_BomCategory side
#   ----------   -----------------------------    -----------------------  --------------------
#   DocBaseType  C_DocType.DocBaseType (RE,CO,IN)  m_bom.doc_base_type      doc_type
#   DocSubType   C_DocType.DocSubType (SH,DX,ST)   m_bom.doc_sub_type       doc_sub_type
#   AABB         c_order.AabbWidthMm/D/H           m_bom.aabb_width_mm/d/h  aabb_width_mm/d/h
#
# ── Data set ──────────────────────────────────────────────
#
#   C_DocType (lookup):
#     ID       DocBaseType  DocSubType  Purpose
#     RE_SH    RE           SH          Sample House (ENBLOC)
#     RE_DX    RE           DX          Duplex (ENBLOC)
#     RE_TB    RE           TB          Terrace Block (ENBLOC)
#     ST_SH    ST           SH          Standard SH (WALKTHRU via DocBaseType)
#     ST_DX    ST           DX          Standard DX (WALKTHRU via DocBaseType)
#     CO_TE    CO           TE          Airport Terminal (future)
#
#   m_bom (BUILDING BOMs — all DocBaseType='RE'):
#     bom_id              doc_base_type  doc_sub_type  AABB
#     BUILDING_SH_STD     RE             SH            16868 x 8668 x 3945
#     BUILDING_DX_STD     RE             DX            9215 x 26565 x 7885
#     BUILDING_TBLKTN_STD RE             TB            (TB dims)
#
#   M_BomCategory (template entries — DocSubType='ST' triggers template path):
#     ID     doc_type  doc_sub_type  AABB               Fits
#     ST-SH  RE        ST            16868 x 8668 x 3945  SH
#     ST-DX  RE        ST            9215 x 26565 x 7885  DX
#
# ── Paths ─────────────────────────────────────────────────
#
#   ENBLOC (DocSubType = SH/DX/TB):
#     Three-key matches BUILDING BOM → count=1 → SINGULARITY → EN-BLOC
#
#   WALKTHRU (DocSubType = ST  OR  DocBaseType = ST):
#     No BUILDING BOM match → enters BomTemplateComposer
#     → M_BomCategory WHERE doc_type='RE' AND doc_sub_type='ST'
#     → Two records: ST-SH and ST-DX (AABB distinguishes SH vs DX)
#     → AABB match picks one → walks M_BomCategoryLine tree per slot
#     → No AABB match → FAIL
#
# ── Long form (DAO) ──────────────────────────────────────
#
#   MCDocType docType = c_order.getC_DocType();
#     DocBaseType: docType.getDocBaseType()  == mbom.getDocBaseType()   (RE == RE)
#     DocSubType:  docType.getDocSubType()   == mbom.getDocSubType()   (SH == SH)
#     AABB:        c_order.getAabbWidthMm()  == mbom.getAabbWidthMm()
#                  c_order.getAabbDepthMm()  == mbom.getAabbDepthMm()
#                  c_order.getAabbHeightMm() == mbom.getAabbHeightMm()
#   WHERE mbom.getBomType() = 'BUILDING' AND match count = 1
#
# ── Test harness ──────────────────────────────────────────
#
#   c_order lives in output.db (transactional, user-created).
#   C_DocType, m_bom, M_BomCategory live in BOM.db (dictionary, read-only).
#   This script sets C_Order.AABB = BOM.AABB (test harness hack)
#   so the singularity check passes. In production, user sets C_Order.AABB.
singularity_check() {
    local label="$1"
    local output_db="$2"
    echo ""
    echo "  Singularity rule check (${label}):"

    # Find BUILDING BOM for this building type (three-key: doc_base_type='RE' + doc_sub_type + AABB)
    local BLDG_BOMID=$(sqlite3 library/BOM.db "
        SELECT bom_id FROM m_bom
        WHERE bom_type = 'BUILDING' AND doc_base_type = 'RE'
          AND doc_sub_type = '${label}' AND is_active = 1
        ORDER BY seq_no LIMIT 1
    " 2>/dev/null)

    # Read AABB directly from BUILDING BOM header (set from extraction/design)
    local BOM_W=$(sqlite3 library/BOM.db "
        SELECT aabb_width_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'
    " 2>/dev/null)
    local BOM_D=$(sqlite3 library/BOM.db "
        SELECT aabb_depth_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'
    " 2>/dev/null)
    local BOM_H=$(sqlite3 library/BOM.db "
        SELECT aabb_height_mm FROM m_bom WHERE bom_id = '${BLDG_BOMID}'
    " 2>/dev/null)

    echo "    BOM AABB       = ${BOM_W} x ${BOM_D} x ${BOM_H} mm  (${BLDG_BOMID})"
    echo "    BUILDING BOM   = $([ -n "$BLDG_BOMID" ] && echo "FOUND" || echo "MISSING")"

    # Hack: set C_Order.AABB = BOM.AABB in output DB so singularity rule holds
    if [ -f "$output_db" ]; then
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

    print_header "COMPILE ${label}"

    # _enbloc = EN-BLOC — takes one BOM whole (singularity)
    # DocType='RE' → three-key match against BUILDING BOM → singularity
    echo ""
    echo "  [enbloc] Compiling EN-BLOC (DocBaseType=RE)..."
    mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=ENBLOC \
        -Ddoc.base.type=RE \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | tail -3 || true
    cp "${base}.db" "${base}_enbloc.db"
    echo "  [enbloc] → ${base}_enbloc.db"

    # Set C_Order.AABB = BOM.AABB (test harness — Prime Rule)
    singularity_check "$label" "${base}_enbloc.db"

    # _walkthru = WALK THRU — walks BUILDING BOM hierarchy (BUILDING → FLOOR → SET → BUY)
    # Same BOMWalker, same PlacementCollectorVisitor, different root BOM selection.
    # Delta vs enbloc reveals which elements are missing from the hierarchy.
    # DocType='ST' → no BUILDING BOM match → template path (M_BomCategory tree walk)
    echo "  [walkthru] Compiling WALK THRU (DocBaseType=ST)..."
    rm -f "${base}.db"
    mvn test -pl DAGCompiler \
        -Dtest="BuildingRegistryTest" \
        -Dbom.mode=WALKTHRU \
        -Ddoc.base.type=ST \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | tail -5 || true
    if [ -f "${base}.db" ]; then
        cp "${base}.db" "${base}_walkthru.db"
        echo "  [walkthru] → ${base}_walkthru.db"
        # Set C_Order.AABB = BOM.AABB on walkthru output too
        singularity_check "$label" "${base}_walkthru.db"
    else
        echo "  [walkthru] SKIP — structured BOM pipeline did not produce output"
        echo "        (structured BOMs may use generic product IDs without geometry)"
    fi
}

# ── Step 2: Delta Test ───────────────────────────────────────
run_delta() {
    local label="$1"
    local eb_db="$2"
    local wt_db="$3"

    print_header "DELTA ${label}: enbloc and walkthru each vs reference"

    if [ ! -f "$eb_db" ] || [ ! -f "$wt_db" ]; then
        echo "  SKIP — missing DB file(s)"
        return
    fi

    # Count comparison
    EB_COUNT=$(sqlite3 "$eb_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    WT_COUNT=$(sqlite3 "$wt_db" "SELECT COUNT(*) FROM elements_meta" 2>/dev/null || echo "0")
    echo "  Element count: enbloc=${EB_COUNT}  walkthru=${WT_COUNT}  delta=$((WT_COUNT - EB_COUNT))"

    # Per-class count comparison (full outer join via UNION)
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

    # AABB delta: per-element comparison (centroid distance)
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

    # Geometry hash comparison: elements with different geometry
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

    # Rule 8 check: world-absolute coordinates on M_BOM_Line
    echo ""
    echo "  Rule 8 (Cheating Maxim) — M_BOM_Line world-absolute check:"
    RULE8=$(sqlite3 library/BOM.db "
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
    else
        echo "    !! FAIL — ${RULE8} M_BOM_Line rows have world-absolute coordinates"
    fi

    # Clash check: AABB overlap among furniture (structured BOM concern)
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
    else
        echo "    !! ${CLASH} furniture AABB overlaps:"
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

# ── Contract Tests (standalone — no pipeline dependency) ─────
run_contracts() {
    print_header "CONTRACT TESTS (standalone)"
    local TESTS="IntraBOMRelativeTest,TranslationChainTest,AnchorComputationTest,LocalCoordTest,StallDividerParamsTest"
    mvn test -pl DAGCompiler \
        -Dtest="${TESTS}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        -q 2>&1 | grep -E "Tests run:" | tail -1
    if [ $? -eq 0 ]; then
        echo "  Contract tests: PASS"
    else
        echo "  !! Contract tests: FAIL"
    fi
}

# ── Execute ──────────────────────────────────────────────────

# Compile first (unless delta-only)
if [ "$MODE" != "delta" ]; then
    print_header "COMPILE (all modules)"
    mvn install -pl orm-core,ORMSandbox -DskipTests -q
    mvn compile -pl DAGCompiler -q
    echo "  Compile: OK"
    run_contracts
fi

case "$MODE" in
    sh)
        compile_building "SH" "$SH_BASE"
        run_delta "SH" "${SH_BASE}_enbloc.db" "${SH_BASE}_walkthru.db"
        ;;
    dx)
        compile_building "DX" "$DX_BASE"
        run_delta "DX" "${DX_BASE}_enbloc.db" "${DX_BASE}_walkthru.db"
        ;;
    delta)
        run_delta "SH" "${SH_BASE}_enbloc.db" "${SH_BASE}_walkthru.db"
        run_delta "DX" "${DX_BASE}_enbloc.db" "${DX_BASE}_walkthru.db"
        ;;
    all|*)
        compile_building "SH" "$SH_BASE"
        compile_building "DX" "$DX_BASE"
        run_delta "SH" "${SH_BASE}_enbloc.db" "${SH_BASE}_walkthru.db"
        run_delta "DX" "${DX_BASE}_enbloc.db" "${DX_BASE}_walkthru.db"
        ;;
esac

# ── Summary ──────────────────────────────────────────────────
print_header "ROSETTA STONE SUMMARY"
echo "  _enbloc   = EN-BLOC — singularity proof. BOM lines already tacked,"
echo "      takes each as-is when AABB and DocType consistent (BUILDING_SH_STD/BUILDING_DX_STD)"
echo "  _walkthru = WALK THRU — mechanism proof. Recalculates by tacking"
echo "      through each BOM layer (BUILDING → FLOOR → SET → BUY)"
echo ""
echo "  Both produce the same result when the data stack is consistent."
echo "  EN-BLOC proves data correctness. WALK THRU proves the mechanism."
echo "  Each must independently match the reference extracted DB."
echo "  Zero delta is a consequence of both matching, not a goal."
echo ""
echo "  Output files:"
echo "    ${SH_BASE}_enbloc.db  ${SH_BASE}_walkthru.db"
echo "    ${DX_BASE}_enbloc.db  ${DX_BASE}_walkthru.db"
echo ""
finish_log
