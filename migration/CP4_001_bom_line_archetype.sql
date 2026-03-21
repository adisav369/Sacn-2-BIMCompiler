-- CP-4 Phase 4a: Add shape_archetype and scale_band to m_bom_line
-- Implementing BBC.md §2.2.1 — geometric identity replaces IFC class switches
--
-- shape_archetype: PLANAR | ELONGATED | COMPACT | MIXED
--   Computed from dimensionless ratios of sorted AABB dimensions (S/M/L).
--   Replaces switch(ifcClass) for geometric decisions (overlap tolerance,
--   Z-band, mesh binding relaxation).
--
-- scale_band: ARCHITECTURAL | FURNITURE | FITTING | TINY
--   Computed from AABB volume in m³. Replaces hardcoded size thresholds.
--
-- Both columns are computed at IFCtoBOM time from allocated_width/depth/height_mm.
-- Existing rows get NULL (backfill via UPDATE below).

ALTER TABLE m_bom_line ADD COLUMN shape_archetype TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN scale_band TEXT DEFAULT NULL;

-- Backfill existing LEAF rows from their allocated dimensions.
-- S = smallest, M = middle, L = largest of (W, D, H).
-- planarity = S/L, elongation = M/L, volume = S*M*L / 1e9.

UPDATE m_bom_line
SET shape_archetype = CASE
    WHEN allocated_width_mm = 0 AND allocated_depth_mm = 0 AND allocated_height_mm = 0 THEN NULL
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) < 0.15
     AND (allocated_width_mm + allocated_depth_mm + allocated_height_mm
          - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
          - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) >= 0.40
    THEN 'PLANAR'
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) < 0.15
     AND (allocated_width_mm + allocated_depth_mm + allocated_height_mm
          - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
          - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) < 0.40
    THEN 'ELONGATED'
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) >= 0.25
     AND (allocated_width_mm + allocated_depth_mm + allocated_height_mm
          - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
          - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * 1.0 / MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) >= 0.50
    THEN 'COMPACT'
    ELSE 'MIXED'
    END,
    scale_band = CASE
    WHEN allocated_width_mm = 0 AND allocated_depth_mm = 0 AND allocated_height_mm = 0 THEN NULL
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * (allocated_width_mm + allocated_depth_mm + allocated_height_mm
            - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
            - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         / 1e9 > 1.0
    THEN 'ARCHITECTURAL'
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * (allocated_width_mm + allocated_depth_mm + allocated_height_mm
            - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
            - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         / 1e9 > 0.01
    THEN 'FURNITURE'
    WHEN MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         * (allocated_width_mm + allocated_depth_mm + allocated_height_mm
            - MIN(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
            - MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm))
         * MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm)
         / 1e9 > 0.0001
    THEN 'FITTING'
    ELSE 'TINY'
    END
WHERE component_type = 'LEAF'
  AND MAX(allocated_width_mm, allocated_depth_mm, allocated_height_mm) > 0;
