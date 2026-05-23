-- W023_sc_bom_seed.sql
-- S273 Red Pill Hardening: Seed SampleCastle BOM with recomposition rules.
-- Implementing BOM_ENGINE_SPEC.md §3.3 — Witness: W-BOM-ENGINE
-- Applied AFTER W022_bom_engine_columns.sql (adds mandatory, creates_grid, etc.)
-- SC has 348 BOM lines, 7 BOMs, Dutch naming (e.g. buitenblad = exterior leaf wall).

-- ══════════════════════════════════════════════════════════════════════
-- Step 1: Apply W022 columns (SC database lacks them)
-- ══════════════════════════════════════════════════════════════════════
ALTER TABLE m_bom_line ADD COLUMN mandatory       INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN edge_offset_mm   REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN buffer_mm         REAL DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN min_count         INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN max_count         INTEGER DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN fill_axis         TEXT DEFAULT 'x';
ALTER TABLE m_bom_line ADD COLUMN creates_grid      INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN drag_axis         TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_shared_key   TEXT DEFAULT NULL;
ALTER TABLE m_bom_line ADD COLUMN grid_editable     INTEGER DEFAULT 1;

-- ══════════════════════════════════════════════════════════════════════
-- Step 2: Populate child_element_type from dominant IFC class
-- ══════════════════════════════════════════════════════════════════════

-- Walls (IfcWall / IfcWallStandardCase)
UPDATE m_bom_line SET child_element_type = 'IfcWall'
WHERE child_product_id IN (
  'buitenblad','binnenblad','HSB-element','knieschot','spouwisolatie',
  'zijwang dakk','opgaand werk','metalstudwand','balkonopstand'
);
UPDATE m_bom_line SET child_element_type = 'IfcWallStandardCase'
WHERE child_product_id IN (
  'separatiewand','binnenwand','binnenwanden','kozijn','liftwand'
);

-- Columns (IfcColumn)
UPDATE m_bom_line SET child_element_type = 'IfcColumn'
WHERE child_product_id IN ('K80/80/4','K152,4/12,5','balusters');

-- Windows (IfcWindow)
UPDATE m_bom_line SET child_element_type = 'IfcWindow'
WHERE child_product_id IN (
  'stelkozijn','dakkoepel 1000x','velux K08'
) OR child_product_id IN (
  'merk B1-R','merk B1sp','merk M','merk K','merk Csp','merk C-R',
  'merk C','merk Lsp-R','merk L-R','merk Ksp','merk Jsp','merk J',
  'merk Dsp','merk B2-R','merk B1sp-R','merk B1','merk N','merk G',
  'merk D','merk Csp-R','merk B2sp'
);

-- Doors (IfcDoor) — D[n]L/R pattern + merk A/E/F/H series
UPDATE m_bom_line SET child_element_type = 'IfcDoor'
WHERE child_product_id IN (
  'D1L','D1R','D2L','D2R','D3L','D3R','D4R','D5L','D5R',
  'D6L','D7L','D8R','D9L','liftdeur'
) OR child_product_id IN (
  'merk A','merk A1sp','merk A2','merk A2sp','merk A3','merk A3sp',
  'merk A4','merk A5','merk A6','merk E1-R','merk E2-R','merk E3-R',
  'merk E4-R','merk E5-R','merk F-R','merk H'
);

-- Slabs (IfcSlab)
UPDATE m_bom_line SET child_element_type = 'IfcSlab'
WHERE child_product_id IN (
  'dekvloer','vloerveld','vloerstort V0','vloer_V0','sporenkap',
  'plat dak','plafond','dakvloer','dakelement','dakaftimmering',
  'dak dakkapel','prefab balkon','gootconstructie','schoonloopmat',
  'vloertegels','lifttop','liftputvloer','dakisolatie'
);

-- Coverings (IfcCovering)
UPDATE m_bom_line SET child_element_type = 'IfcCovering'
WHERE child_product_id IN (
  'gevelisolatie','zinkwerk','betontegels','wandtegelwerk','afdekker',
  'vensterbank','dakopstand','wandtegelwerk+','vloertegelwerk',
  'gipsplafond','gootplafond','dakpan (vlak)','dakschroot',
  'afschotisolatie','isolatie','kantafwerking','boeideel',
  'muurafdekker','kantplank','waterslag','aftimmering'
);

-- Flow segments (IfcFlowSegment) — MEP discipline
UPDATE m_bom_line SET child_element_type = 'IfcFlowSegment'
WHERE child_product_id = 'hwa afvoer';

-- Beams (IfcBeam)
UPDATE m_bom_line SET child_element_type = 'IfcBeam'
WHERE child_product_id IN (
  'HEA180','HEA220','HEB220','staallatei','staallatei ??',
  'betonlatei','geveldrager','hoeklijn bordes','L150/100/10 (?)',
  'stripstaal 80/8','staal halfspant','fund_strook','fund_poer',
  'fund_opstort','liftputwand'
);

-- Railings (IfcRailing)
UPDATE m_bom_line SET child_element_type = 'IfcRailing'
WHERE child_product_id IN ('traphek','afscheiding','doorvalregel','leuning');

-- Stairs (IfcStair)
UPDATE m_bom_line SET child_element_type = 'IfcStair'
WHERE child_product_id IN ('trappen','trapbordes');

-- Proxy elements (IfcBuildingElementProxy)
UPDATE m_bom_line SET child_element_type = 'IfcBuildingElementProxy'
WHERE child_product_id IN ('ROOT nulpunt','bellentableau','brievenbussen');

-- ══════════════════════════════════════════════════════════════════════
-- Step 3: Assign layout_strategy + recomposition rules by IFC class
-- ══════════════════════════════════════════════════════════════════════

-- IfcWall / IfcWallStandardCase → SPAN, mandatory, creates grid
UPDATE m_bom_line SET
  layout_strategy = 'SPAN',
  mandatory = 1,
  creates_grid = 1,
  fill_axis = 'x'
WHERE child_element_type IN ('IfcWall','IfcWallStandardCase');

-- Interior partition walls: editable grids, draggable
UPDATE m_bom_line SET
  grid_editable = 1,
  drag_axis = 'x'
WHERE child_element_type = 'IfcWallStandardCase'
  AND child_product_id IN ('separatiewand','binnenwand','binnenwanden');

-- Exterior walls: display-only grids (not editable)
UPDATE m_bom_line SET
  grid_editable = 0
WHERE child_element_type = 'IfcWall'
  AND child_product_id IN ('buitenblad','HSB-element','opgaand werk');

-- IfcColumn → FIXED, mandatory, creates grid
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  creates_grid = 1,
  fill_axis = 'x'
WHERE child_element_type = 'IfcColumn';

-- IfcWindow → UNIFORM, fill along x
UPDATE m_bom_line SET
  layout_strategy = 'UNIFORM',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 1,
  grid_editable = 1,
  drag_axis = 'x'
WHERE child_element_type = 'IfcWindow';

-- Shared grid key for window merk families
UPDATE m_bom_line SET grid_shared_key = 'WIN_MERK_B'
WHERE child_element_type = 'IfcWindow'
  AND child_product_id LIKE 'merk B%';
UPDATE m_bom_line SET grid_shared_key = 'WIN_MERK_C'
WHERE child_element_type = 'IfcWindow'
  AND child_product_id LIKE 'merk C%';

-- IfcDoor → FIXED, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcDoor';

-- IfcSlab → SPAN, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'SPAN',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcSlab';

-- IfcCovering → UNIFORM
UPDATE m_bom_line SET
  layout_strategy = 'UNIFORM',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcCovering';

-- IfcFlowSegment → ROUTE (MEP)
UPDATE m_bom_line SET
  layout_strategy = 'ROUTE',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcFlowSegment';

-- IfcBeam → SPAN, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'SPAN',
  mandatory = 1,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcBeam';

-- IfcRailing → LINEAR (default, just set mandatory=0 explicitly)
UPDATE m_bom_line SET
  layout_strategy = 'LINEAR',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcRailing';

-- IfcStair → FIXED, mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'z',
  creates_grid = 0
WHERE child_element_type = 'IfcStair';

-- IfcBuildingElementProxy → FIXED, non-mandatory
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 0,
  fill_axis = 'x',
  creates_grid = 0
WHERE child_element_type = 'IfcBuildingElementProxy';

-- ══════════════════════════════════════════════════════════════════════
-- Step 4: Building-level BOM (storey children) → FIXED, z-stacked
-- ══════════════════════════════════════════════════════════════════════
UPDATE m_bom_line SET
  layout_strategy = 'FIXED',
  mandatory = 1,
  fill_axis = 'z',
  creates_grid = 0
WHERE bom_id = 'BUILDING_SC_STD';

-- ══════════════════════════════════════════════════════════════════════
-- Step 5: Populate storey from elements_meta (most common storey per product per BOM)
-- ══════════════════════════════════════════════════════════════════════
UPDATE m_bom_line SET storey = (
  SELECT em.storey FROM elements_meta em
  WHERE em.element_name = m_bom_line.child_product_id
    AND em.storey IS NOT NULL AND em.storey != ''
  GROUP BY em.storey ORDER BY COUNT(*) DESC LIMIT 1
)
WHERE storey IS NULL OR storey = '';

-- ══════════════════════════════════════════════════════════════════════
-- Step 6: Populate element_ref from a representative elements_meta GUID
-- ══════════════════════════════════════════════════════════════════════
UPDATE m_bom_line SET element_ref = (
  SELECT em.guid FROM elements_meta em
  WHERE em.element_name = m_bom_line.child_product_id
  LIMIT 1
)
WHERE child_element_type IS NOT NULL AND child_element_type != '';

-- ══════════════════════════════════════════════════════════════════════
-- Step 7: Populate allocated dimensions from element_transforms bbox
-- ══════════════════════════════════════════════════════════════════════
UPDATE m_bom_line SET
  allocated_width_mm = COALESCE((
    SELECT CAST(ABS(et.bbox_x) * 1000 AS INTEGER) FROM element_transforms et
    WHERE et.guid = m_bom_line.element_ref LIMIT 1
  ), 0),
  allocated_depth_mm = COALESCE((
    SELECT CAST(ABS(et.bbox_z) * 1000 AS INTEGER) FROM element_transforms et
    WHERE et.guid = m_bom_line.element_ref LIMIT 1
  ), 0),
  allocated_height_mm = COALESCE((
    SELECT CAST(ABS(et.bbox_y) * 1000 AS INTEGER) FROM element_transforms et
    WHERE et.guid = m_bom_line.element_ref LIMIT 1
  ), 0)
WHERE child_element_type IS NOT NULL AND child_element_type != '';
