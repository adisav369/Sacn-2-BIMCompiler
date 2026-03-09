-- Migration: Add AABB columns to m_bom header
--
-- iDempiere convention: M_Product has dimensions, M_Product is flattened into m_bom.
-- The BOM header stores its own AABB envelope. Children have their own AABB
-- (allocated_*_mm on m_bom_line). Sum check: children AABB == parent AABB.
--
-- At BOM creation time (MBOM.beforeSave), the parent AABB is recomputed from children.
-- BuildingRegistry reads m_bom.aabb_* directly — no runtime recomputation.

ALTER TABLE m_bom ADD COLUMN aabb_width_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom ADD COLUMN aabb_depth_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom ADD COLUMN aabb_height_mm INTEGER DEFAULT 0;

-- Backfill hierarchical BOMs (SET/ROOM/FLOOR): Width=SUM, Depth=MAX, Height=MAX
-- Buffer fillers ensure children sum to parent (strip-packing invariant).
UPDATE m_bom
SET aabb_width_mm = COALESCE((
        SELECT SUM(bl.allocated_width_mm)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0),
    aabb_depth_mm = COALESCE((
        SELECT MAX(bl.allocated_depth_mm)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0),
    aabb_height_mm = COALESCE((
        SELECT MAX(bl.allocated_height_mm)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0)
WHERE bom_id NOT LIKE 'EB_%';

-- Backfill EB_ (extracted) BOMs: bounding box from placed children.
-- EB_ children are placed in 3D (dx/dy/dz in metres, allocated_*_mm in mm).
-- Envelope = outer bounding box of all placed elements.
UPDATE m_bom
SET aabb_width_mm = COALESCE((
        SELECT CAST((MAX(bl.dx + bl.allocated_width_mm  / 2000.0)
                    - MIN(bl.dx - bl.allocated_width_mm  / 2000.0)) * 1000 AS INTEGER)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0),
    aabb_depth_mm = COALESCE((
        SELECT CAST((MAX(bl.dy + bl.allocated_depth_mm  / 2000.0)
                    - MIN(bl.dy - bl.allocated_depth_mm  / 2000.0)) * 1000 AS INTEGER)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0),
    aabb_height_mm = COALESCE((
        SELECT CAST((MAX(bl.dz + bl.allocated_height_mm / 2000.0)
                    - MIN(bl.dz - bl.allocated_height_mm / 2000.0)) * 1000 AS INTEGER)
        FROM m_bom_line bl WHERE bl.bom_id = m_bom.bom_id AND bl.is_active = 1
    ), 0)
WHERE bom_id LIKE 'EB_%';
