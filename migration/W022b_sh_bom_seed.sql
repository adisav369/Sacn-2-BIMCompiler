-- W022b_sh_bom_seed.sql
-- S272 BOM Engine Phase 2: Seed SampleHouse BOM with recomposition rules.
-- Implementing BOM_ENGINE_SPEC.md §3.3 — Witness: W-BOM-ENGINE
-- Applied AFTER W022_bom_engine_columns.sql

-- ── SH_GF_STR: Ground Floor Structure ──

-- Floor slab: SPAN (stretches to fill), mandatory
UPDATE m_bom_line SET
  layout_strategy = 'SPAN',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Floor:%';

-- Exterior walls: FIXED (proportional reposition), mandatory, creates display-only grids
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 1,
  grid_editable = 0
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Basic Wall:Wall-Ext%';

-- Interior partition walls: FIXED, creates editable grids
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 1,
  grid_editable = 1,
  drag_axis = 'x'
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Basic Wall:Wall-Partn%';

-- Interior doors: FIXED, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Doors_IntSgl%';

-- Exterior door: FIXED, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Doors_ExtDbl%';

-- Windows: UNIFORM (recount on resize), shared grid key
UPDATE m_bom_line SET
  layout_strategy = 'UNIFORM',
  mandatory = 0,
  fill_axis = 'x',
  min_space_mm = 1800,
  edge_offset_mm = 200,
  creates_grid = 1,
  grid_editable = 1,
  grid_shared_key = 'WIN_1810x1210',
  drag_axis = 'x'
WHERE bom_id = 'SH_GF_STR' AND child_product_id LIKE 'Windows_%';

-- ── BUILDING_SH_STD: Building Root ──

-- Floor children: FIXED (one per storey, z-stacked)
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'z',
  creates_grid = 0
WHERE bom_id = 'BUILDING_SH_STD' AND child_product_id IN (
  'SH_GF_STR', 'FLOOR_SH_GF_STD', 'FLOOR_SLAB_GF'
);

-- Roof: FIXED, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'z',
  creates_grid = 0
WHERE bom_id = 'BUILDING_SH_STD' AND child_product_id IN (
  'SH_ROOF_STR', 'ROOF_ASSEMBLY'
);

-- Curtain wall: FIXED
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 0
WHERE bom_id = 'BUILDING_SH_STD' AND child_product_id = 'SH_CW_STR';

-- ── Furniture sets: FIXED (user-placed positions) ──

UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 0,
  fill_axis = 'x'
WHERE bom_id IN ('SH_LIVING_SET', 'SH_DINING_SET', 'SH_BED_SET');
