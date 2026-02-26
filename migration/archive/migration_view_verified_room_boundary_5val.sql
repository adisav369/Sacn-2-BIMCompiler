-- ============================================================
-- Fix: v_verified_room_boundary — 5-value coordinate_frame IN clause
-- Date: 2026-02-23
-- Watchdog: prior session created view with 3 values only.
-- §2.3 + §5.2 mandate 5. DERIVED_MM (Template Topology Path) and
-- CONSTRAINT_SOLVED (SpaceSolver) were silently excluded.
-- ============================================================

DROP VIEW IF EXISTS v_verified_room_boundary;
CREATE VIEW v_verified_room_boundary AS
SELECT
    rb.id                           AS room_id,
    rb.building_type,
    rb.room_type,
    rb.min_x_mm,
    rb.max_x_mm,
    rb.min_y_mm,
    rb.max_y_mm,
    rb.storey                       AS storey_id,
    rb.extracted_from,
    rb.coordinate_frame,
    (rb.max_x_mm - rb.min_x_mm)    AS width_mm,
    (rb.max_y_mm - rb.min_y_mm)    AS depth_mm
FROM ad_room_boundary rb
WHERE rb.coordinate_frame IN (
        'IFC_GLOBAL_MM',
        'LOCAL_MM',
        'DRAWING_MM',
        'CONSTRAINT_SOLVED',
        'DERIVED_MM'
    )
    AND rb.extracted_from NOT IN ('PENDING','','TODO','UNKNOWN','GRID_DERIVED');

-- Verify: row count must remain 7 (TB-LKTN LOCAL_MM rows unaffected)
SELECT 'VERIFY row count (expect 7):';
SELECT COUNT(*) FROM v_verified_room_boundary;
