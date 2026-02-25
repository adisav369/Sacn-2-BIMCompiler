-- ============================================================
-- Phase A: DX Room Boundary Expansion
-- Expands 2 calibrated room bounds to contain BOM-placed furniture.
--
-- Root cause: IFC IfcSpace extents exclude wall thickness, so BOM-placed
--   furniture at room edges (Piano, Dining Chairs) can have centroids
--   marginally outside the extracted boundary.
--
-- Note: 40 ROOM_Level_* rooms with NULL min_x_mm must stay active
--   because 774 ad_element_rule entries reference them by host_ref.
--   F2-DX test remains @Disabled due to MULTI_UNIT coordinate frame
--   mismatch (compiled Y is positive, room boundary Y is negative).
--
-- Idempotent: safe to run multiple times.
-- ============================================================

BEGIN TRANSACTION;

-- ── Expand A102 (Living) northward ─────────────────────────────────────────
-- A-unit Piano centroid at Y=-13.384m, A102 max_y_mm was -13725mm (IFC space edge).
-- Expand max_y_mm to -13100mm (~625mm north = half-wall + furniture overhang).

UPDATE ad_room_boundary
SET max_y_mm = -13100.0
WHERE building_type = 'Ifc2x3_Duplex'
  AND room_name = 'ROOM_A102'
  AND max_y_mm = -13725.0;

-- ── Expand B102 (Living) northward + eastward ──────────────────────────────
-- B-unit Piano centroid at Y=-0.271m, B102 max_y_mm was -612mm.
-- B-unit Dining Chairs at X=8.613m, B102 max_x_mm was 8313mm.

UPDATE ad_room_boundary
SET max_y_mm = -100.0,
    max_x_mm = 8700.0
WHERE building_type = 'Ifc2x3_Duplex'
  AND room_name = 'ROOM_B102'
  AND max_y_mm = -612.0;

COMMIT;

-- ── Verify ──────────────────────────────────────────────────────────────────
SELECT room_name, min_x_mm, max_x_mm, min_y_mm, max_y_mm
FROM ad_room_boundary
WHERE building_type = 'Ifc2x3_Duplex'
  AND room_name IN ('ROOM_A102', 'ROOM_B102');
