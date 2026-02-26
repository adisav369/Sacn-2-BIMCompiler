-- ============================================================
-- VIEW_CONTRACTS.md §7 — Phase 1: doc_status column addition
-- Date: 2026-02-22
-- Adds doc_status (DR/CO/VO) to 4 core tables.
-- Seeds CO for all currently verified rows.
-- NO views created. NO Java changes. Data-only.
-- SpatialDigests must be identical before and after.
--
-- CO seeding rationale per VIEW_CONTRACTS.md §2.2:
--   ad_bom_child      → active leaf (pattern resolves) + active sub-BOM (bom_id exists)
--   ad_element_rule   → active=1 (all current working rules are verified)
--   ad_room_boundary  → TB-LKTN only (design-derived, generative authority)
--                       SH/DX: DR — wrong coordinates, calibration deferred to Phase 2+
--   component_definitions → has matching geometry_hash in component_geometries
--
-- iDempiere analogue: DocStatus on C_Invoice — DR=being prepared, CO=verified, VO=superseded
-- ============================================================

BEGIN TRANSACTION;

-- ══════════════════════════════════════════════════════════════
-- 1. ad_bom_child
-- ══════════════════════════════════════════════════════════════

ALTER TABLE ad_bom_child
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- CO: active leaf children whose name pattern resolves to a real component
UPDATE ad_bom_child
SET doc_status = 'CO'
WHERE is_active = 1
  AND child_name_pattern IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM component_definitions cd
    WHERE cd.name LIKE '%' || REPLACE(child_name_pattern, '%', '') || '%'
  );

-- CO: active sub-BOM children (hierarchy nodes) whose child_bom_id exists in ad_bom
UPDATE ad_bom_child
SET doc_status = 'CO'
WHERE is_active = 1
  AND child_bom_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM ad_bom b WHERE b.bom_id = child_bom_id);

-- ══════════════════════════════════════════════════════════════
-- 2. ad_element_rule
-- ══════════════════════════════════════════════════════════════

ALTER TABLE ad_element_rule
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- CO: all active rules — these are the current verified working set
-- Terminal has no relational element rules (FLAT extracted path)
-- All active rules here belong to SH, DX, or TB-LKTN and are proven by 119 tests
UPDATE ad_element_rule
SET doc_status = 'CO'
WHERE is_active = 1;

-- ══════════════════════════════════════════════════════════════
-- 3. ad_room_boundary
-- ══════════════════════════════════════════════════════════════

ALTER TABLE ad_room_boundary
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- CO: TB-LKTN — design-derived generative building.
--     Compiler IS the authority. These coordinates are correct for TB-LKTN.
--     coordinate_frame = LOCAL_MM (passes v_verified_room_boundary filter)
UPDATE ad_room_boundary
SET doc_status = 'CO'
WHERE building_type = 'TB_LKTN';

-- SH: DR — GRID_DERIVED, X range (1620,6265) does not match IFC global frame.
--     Calibration pending Phase 2 (view) + room boundary re-extraction.
-- DX: DR — 44 artificial grid cells, no relation to IFC IfcSpace extents.
--     Calibration pending Phase 2 (view) + 11 real IFC rooms insertion.
-- (Default 'DR' already applied by ALTER TABLE — no UPDATE needed)

-- ══════════════════════════════════════════════════════════════
-- 4. component_definitions
-- ══════════════════════════════════════════════════════════════

ALTER TABLE component_definitions
ADD COLUMN doc_status TEXT NOT NULL DEFAULT 'DR'
    CHECK(doc_status IN ('DR', 'CO', 'VO'));

-- CO: all components whose geometry_hash resolves in component_geometries
-- Phase 2 v_proven_geometry will further filter to vertex_count > 8
-- (eliminating 8-vertex BBox placeholders from compilation)
UPDATE component_definitions
SET doc_status = 'CO'
WHERE EXISTS (
    SELECT 1 FROM component_geometries cg
    WHERE cg.geometry_hash = component_definitions.geometry_hash
);

COMMIT;

-- ── Verification ──────────────────────────────────────────────
SELECT 'ad_bom_child', doc_status, COUNT(*)
FROM ad_bom_child GROUP BY doc_status;

SELECT 'ad_element_rule', doc_status, COUNT(*)
FROM ad_element_rule GROUP BY doc_status;

SELECT 'ad_room_boundary', building_type, doc_status, COUNT(*)
FROM ad_room_boundary GROUP BY building_type, doc_status ORDER BY building_type;

SELECT 'component_definitions', doc_status, COUNT(*)
FROM component_definitions GROUP BY doc_status;
