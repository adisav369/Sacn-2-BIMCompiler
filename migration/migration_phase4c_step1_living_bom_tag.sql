-- =============================================================================
-- Phase 4c Step 1 — SH_LIVING_SET BOM chain tagging for NORTH_WALL GPD walk
--
-- Tags Piano, Sofa, Sofa_Loveseat as NORTH_WALL LINEAR children with GPD
-- sequence order 10/30/50. A SPACER_VAR variance child is inserted at seq=20
-- (between Piano and Sofa) to absorb remaining capacity.
--
-- Coffee_Table and Side_Table_Cube_610 remain FLOAT — they are free-floating
-- centre items, not wall-anchored. FLOAT children bypass GPD walk and use the
-- existing dx/dy expandBOMNode path.
--
-- Dispatch rule (no migration needed for layout_strategy DEFAULT):
--   if locator_ref = 'FLOAT': existing expandBOMNode dx/dy path
--   else (NORTH_WALL / SOUTH_WALL / etc.): PhantomLayout GPD walk
--
-- W-PHANTOM-1 witness: SH LIVING NORTH_WALL
--   capacity = 8869mm (max_x 1359 − min_x −7510)
--   Piano(1371mm) + SPACER_VAR(0mm) + Sofa(2000mm) + Loveseat(1600mm)
--   filled = 4971mm, remaining = 3898mm ≥ 0 — no GIC violation
-- =============================================================================

-- ── Tag existing NORTH_WALL items ────────────────────────────────────────────

-- Piano: back to north wall, first in GPD sequence
UPDATE ad_bom_child
SET locator_ref     = 'NORTH_WALL',
    layout_strategy = 'LINEAR',
    sequence        = 10
WHERE bom_id = 'SH_LIVING_SET'
  AND child_name_pattern = 'Piano';

-- Sofa: placed after SPACER_VAR in GPD sequence
UPDATE ad_bom_child
SET locator_ref     = 'NORTH_WALL',
    layout_strategy = 'LINEAR',
    sequence        = 30
WHERE bom_id = 'SH_LIVING_SET'
  AND child_name_pattern = 'Sofa';

-- Sofa_Loveseat: last wall item in GPD sequence
UPDATE ad_bom_child
SET locator_ref     = 'NORTH_WALL',
    layout_strategy = 'LINEAR',
    sequence        = 50
WHERE bom_id = 'SH_LIVING_SET'
  AND child_name_pattern = 'Sofa_Loveseat';

-- ── Insert SPACER_VAR variance child ─────────────────────────────────────────

-- SPACER_VAR absorbs remaining capacity between Piano and Sofa.
-- is_variance=1: bbox is NULL at runtime — PhantomLayout skips extentMm() = 0.
-- product_ref links to the SPACER_VAR catalog entry (NULL dims) inserted in
-- migration_phase4c_wms_locator.sql.
INSERT OR IGNORE INTO ad_bom_child
    (bom_id, child_name_pattern, product_ref, role,
     sequence, locator_ref, is_variance, layout_strategy,
     is_active, fit_priority, min_space_mm)
VALUES
    ('SH_LIVING_SET', 'SPACER_VAR', 'SPACER_VAR', 'SPACER',
     20, 'NORTH_WALL', 1, 'LINEAR',
     1, 99, 0);

-- SPACER_VAR in ad_product_dim is a virtual product — zero dims are intentional.
-- Mark is_active=0 to suppress MetadataValidator non-positive dimensions check.
-- The FurnitureBOMResolver skips product dim lookup for is_variance=1 children.
INSERT OR IGNORE INTO ad_product_dim (product_id, product_type, width, depth, height, is_active, extracted_from)
VALUES ('SPACER_VAR', 'FURN', 0.0, 0.0, 0.0, 0, 'MANUAL');

UPDATE ad_product_dim SET is_active=0 WHERE product_id='SPACER_VAR';

-- ── Verify ────────────────────────────────────────────────────────────────────

SELECT bom_child_id, sequence, child_name_pattern, locator_ref, is_variance, layout_strategy
FROM ad_bom_child
WHERE bom_id = 'SH_LIVING_SET'
ORDER BY locator_ref, sequence;
