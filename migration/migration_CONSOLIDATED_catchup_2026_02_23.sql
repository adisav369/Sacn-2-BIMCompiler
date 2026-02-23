-- ============================================================
-- CONSOLIDATED CATCH-UP MIGRATION — 2026-02-23
-- Brings library/component_library.db from LFS-HEAD state
-- to full Phase 4 state in one atomic transaction.
--
-- Applies (in order):
--   1. VIEW_CONTRACTS columns: extracted_from on component_definitions,
--      ad_product_dim; bom_type on ad_bom; fit_priority/min_space_mm
--      on ad_bom_child; coordinate_frame+extracted_from on ad_room_boundary
--   2. Phase 4: product_ref column on ad_bom_child
--   3. doc_status on ad_building_registry
--   4. 5-tier bom_type CHECK (recreate ad_bom + views)
--   5. Six views (v_qualified_bom, v_verified_room_boundary, ...)
--   6. Seed product_refs for DINING_SET and LIVING_SET
--   7. Calibrate SH room boundaries to IFC global frame
--   8. Seed bom_type UNIT/FLOOR for hierarchy BOMs
--
-- Pre-state (LFS HEAD + DX rooms already restored):
--   - No views, no product_ref, no bom_type, no coordinate_frame
--   - DX: 53 room_boundary rows + 1080 active element_rules
--   - SH: 2 rooms with wrong LOCAL grid coords
-- ============================================================

BEGIN TRANSACTION;

-- ══════════════════════════════════════════════════════════════
-- PART 1 — Add missing columns
-- ══════════════════════════════════════════════════════════════

-- 1a. component_definitions.extracted_from
ALTER TABLE component_definitions
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(extracted_from NOT IN ('','TODO','UNKNOWN'));

-- Mark all components with valid geometry as EXTRACTED
UPDATE component_definitions
SET extracted_from = 'EXTRACTED'
WHERE geometry_hash IS NOT NULL
  AND EXISTS (SELECT 1 FROM component_geometries cg WHERE cg.geometry_hash = component_definitions.geometry_hash);

-- 1b. ad_product_dim.extracted_from
ALTER TABLE ad_product_dim
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(extracted_from NOT IN ('','TODO','UNKNOWN'));

-- Mark all product dims as EXTRACTED (they are from reference IFC)
UPDATE ad_product_dim SET extracted_from = 'EXTRACTED';

-- 1c. ad_bom.bom_type
ALTER TABLE ad_bom
ADD COLUMN bom_type TEXT NOT NULL DEFAULT 'SET';

-- 1d. ad_bom_child: fit_priority, min_space_mm, product_ref
ALTER TABLE ad_bom_child
ADD COLUMN fit_priority INTEGER NOT NULL DEFAULT 20;

ALTER TABLE ad_bom_child
ADD COLUMN min_space_mm INTEGER NOT NULL DEFAULT 0;

ALTER TABLE ad_bom_child
ADD COLUMN product_ref TEXT REFERENCES ad_product_dim(product_id);

-- 1e. ad_room_boundary: coordinate_frame, extracted_from
ALTER TABLE ad_room_boundary
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'GRID_DERIVED';

ALTER TABLE ad_room_boundary
ADD COLUMN coordinate_frame TEXT NOT NULL DEFAULT 'LOCAL_MM';

-- ══════════════════════════════════════════════════════════════
-- PART 2 — Set correct extracted_from values for room boundaries
-- ══════════════════════════════════════════════════════════════

-- TB-LKTN: generative authority — design-derived, correct for compilation
UPDATE ad_room_boundary
SET extracted_from = 'BUILDING_DSL', coordinate_frame = 'LOCAL_MM'
WHERE building_type = 'TB_LKTN';

-- DX IFC-calibrated rooms (ROOM_A*, ROOM_B*): extracted from reference IFC
UPDATE ad_room_boundary
SET extracted_from = 'EXTRACTED', coordinate_frame = 'IFC_GLOBAL_MM'
WHERE building_type = 'Ifc2x3_Duplex'
  AND (room_name LIKE 'ROOM_A%' OR room_name LIKE 'ROOM_B%');

-- DX ROOM_Level_* remain GRID_DERIVED (excluded from v_verified_room_boundary)
-- SH rooms: calibrate to IFC global frame (fix from migration_G8_SH_room_boundary_calibration.sql)
UPDATE ad_room_boundary
SET min_x_mm = -7510.0, max_x_mm = 1359.0, min_y_mm = -281.0, max_y_mm = 4409.0,
    extracted_from = 'EXTRACTED', coordinate_frame = 'IFC_GLOBAL_MM'
WHERE building_type = 'Ifc4_SampleHouse'
  AND room_name = 'ROOM_Ground_Floor_1'
  AND room_type = 'LIVING';

UPDATE ad_room_boundary
SET min_x_mm = 4113.0, max_x_mm = 6120.0, min_y_mm = 946.0, max_y_mm = 4367.0,
    extracted_from = 'EXTRACTED', coordinate_frame = 'IFC_GLOBAL_MM'
WHERE building_type = 'Ifc4_SampleHouse'
  AND room_name = 'ROOM_Ground_Floor_2'
  AND room_type = 'BEDROOM';

-- ══════════════════════════════════════════════════════════════
-- PART 3 — ad_building_registry.doc_status
-- ══════════════════════════════════════════════════════════════

ALTER TABLE ad_building_registry
ADD COLUMN doc_status TEXT DEFAULT 'DR'
    CHECK(doc_status IS NULL OR doc_status IN ('DR','IP','CO','VO'));

UPDATE ad_building_registry SET doc_status = 'CO';

-- ══════════════════════════════════════════════════════════════
-- PART 4 — 5-tier bom_type (recreate ad_bom with CHECK)
-- ══════════════════════════════════════════════════════════════

-- Views referencing ad_bom must be dropped before DROP TABLE
DROP VIEW IF EXISTS v_qualified_bom;
DROP VIEW IF EXISTS v_active_bom_assembly;
DROP VIEW IF EXISTS v_component_leaf;

CREATE TABLE ad_bom_new (
    bom_id            TEXT PRIMARY KEY,
    bom_name          TEXT NOT NULL,
    description       TEXT,
    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
    group_by          TEXT NOT NULL,
    is_active         INTEGER DEFAULT 1,
    bom_level         TEXT DEFAULT 'SET',
    bom_type          TEXT NOT NULL DEFAULT 'SET'
        CHECK(bom_type IN ('UNIT', 'FLOOR', 'ROOM', 'SET', 'ITEM'))
);

INSERT INTO ad_bom_new SELECT bom_id, bom_name, description, target_ifc_class, group_by, is_active, bom_level, bom_type FROM ad_bom;
DROP TABLE ad_bom;
ALTER TABLE ad_bom_new RENAME TO ad_bom;

-- Set correct bom_type tiers
UPDATE ad_bom SET bom_type = 'UNIT'
WHERE bom_id IN ('UNIT_DUPLEX_STD', 'UNIT_SH_STD', 'UNIT_TBLKTN_STD');

UPDATE ad_bom SET bom_type = 'FLOOR'
WHERE bom_id IN (
    'FLOOR_DX_L1_STD', 'FLOOR_DX_L2_STD',
    'FLOOR_SH_GF_STD', 'FLOOR_TBLKTN_GF_STD',
    'FLOOR_STRUCTURAL', 'TYPICAL_CONDO_FLOOR'
);
-- All remaining active bom_ids remain SET (default)

-- ══════════════════════════════════════════════════════════════
-- PART 5 — Seed product_ref for DINING_SET and LIVING_SET
-- ══════════════════════════════════════════════════════════════

UPDATE ad_bom_child SET product_ref = 'Dining_Table_With_Chairs'
WHERE bom_id = 'DINING_SET' AND role = 'TABLE' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Dining_Chair'
WHERE bom_id = 'DINING_SET'
  AND role IN ('CHAIR_A','CHAIR_B','CHAIR_C','CHAIR_D','CHAIR_E','CHAIR_F')
  AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Sofa'
WHERE bom_id = 'LIVING_SET' AND role = 'SOFA' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Sofa_Loveseat'
WHERE bom_id = 'LIVING_SET' AND role = 'SOFA_B' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Coffee_Table'
WHERE bom_id = 'LIVING_SET' AND role = 'COFFEE_TABLE' AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Side_Table_Cube_610'
WHERE bom_id = 'LIVING_SET' AND role IN ('SIDE_TABLE_A','SIDE_TABLE_B') AND is_active = 1;

UPDATE ad_bom_child SET product_ref = 'Piano'
WHERE bom_id = 'LIVING_SET' AND role = 'PIANO' AND is_active = 1;

-- Previously seeded in Phase 4 (cabinets/storage)
UPDATE ad_bom_child SET product_ref = 'Tall_Cabinet'
WHERE bom_id IN ('BED_SET','BED_SET_MASTER','WARDROBE_SET')
  AND role LIKE 'TALL_CABINET%' AND is_active = 1
  AND product_ref IS NULL;

UPDATE ad_bom_child SET product_ref = 'Base_Cabinet'
WHERE role = 'BASE_CABINET' AND is_active = 1 AND product_ref IS NULL;

UPDATE ad_bom_child SET product_ref = 'Upper_Cabinet'
WHERE role = 'UPPER_CABINET' AND is_active = 1 AND product_ref IS NULL;

UPDATE ad_bom_child SET product_ref = 'Counter_Top'
WHERE role = 'COUNTER_TOP' AND is_active = 1 AND product_ref IS NULL;

UPDATE ad_bom_child SET product_ref = 'Vanity_Cabinet'
WHERE role LIKE 'VANITY%' AND is_active = 1 AND product_ref IS NULL;

-- ══════════════════════════════════════════════════════════════
-- PART 6 — Create all 6 views
-- ══════════════════════════════════════════════════════════════

-- v_qualified_bom: Phase 4 version (joins on product_ref, not role)
CREATE VIEW v_qualified_bom AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    bc.bom_child_id,
    bc.role,
    bc.sequence,
    bc.fit_priority,
    bc.min_space_mm,
    bc.child_name_pattern,
    pd.width            AS width_mm,
    pd.depth            AS depth_mm,
    pd.height           AS height_mm,
    pd.extracted_from
FROM ad_bom b
JOIN ad_bom_child bc
    ON bc.bom_id = b.bom_id
    AND bc.is_active = 1
JOIN ad_product_dim pd
    ON pd.product_id = bc.product_ref
    AND pd.width > 0
    AND pd.depth > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE b.is_active = 1;

-- v_verified_room_boundary
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

-- v_compilable_element_rule
CREATE VIEW v_compilable_element_rule AS
SELECT
    er.id,
    er.building_type,
    er.storey,
    er.element_ref,
    er.ifc_class,
    er.discipline,
    er.host_type,
    er.host_ref,
    er.position_rule,
    er.position_value,
    er.position_value_2,
    er.position_value_3,
    er.height_mm,
    er.height_extent_mm,
    er.family_ref,
    er.width_mm,
    er.depth_mm,
    er.orientation,
    er.geometry_hash,
    er.material_name,
    er.material_rgba,
    er.provenance
FROM ad_element_rule er
WHERE er.is_active = 1
  AND er.provenance IN ('BUILDING_DSL','EXTRACTED','MIGRATION','RESEARCHED');

-- v_proven_geometry
CREATE VIEW v_proven_geometry AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM ad_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    cd.geometry_hash,
    cd.vertex_count,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL;

-- v_active_bom_assembly
CREATE VIEW v_active_bom_assembly AS
SELECT
    b.bom_id,
    b.bom_name,
    b.bom_type,
    COUNT(bc.bom_child_id)                              AS child_count,
    SUM(CASE WHEN bc.is_active = 1 THEN 1 ELSE 0 END)  AS active_children
FROM ad_bom b
JOIN ad_bom_child bc ON bc.bom_id = b.bom_id
WHERE b.is_active = 1
GROUP BY b.bom_id, b.bom_name, b.bom_type
HAVING child_count = active_children;

-- v_component_leaf
CREATE VIEW v_component_leaf AS
SELECT
    cd.id,
    cd.name,
    (SELECT MIN(gm.ifc_class)
     FROM ad_geometry_map gm
     WHERE gm.geometry_hash = cd.geometry_hash) AS ifc_class,
    pd.width                        AS width_mm,
    pd.depth                        AS depth_mm,
    pd.height                       AS height_mm,
    cd.geometry_hash,
    cd.extracted_from               AS geometry_source
FROM component_definitions cd
JOIN ad_product_dim pd
    ON pd.product_id = cd.name
    AND pd.width  > 0
    AND pd.depth  > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE cd.vertex_count > 8
  AND cd.extracted_from NOT LIKE '%PENDING%'
  AND cd.geometry_hash IS NOT NULL
  AND cd.name NOT IN (SELECT bom_id FROM ad_bom WHERE is_active = 1);

COMMIT;

-- ── Verification ────────────────────────────────────────────────────────
SELECT 'views:', COUNT(*) FROM sqlite_master WHERE type='view';
SELECT 'v_qualified_bom rows:', COUNT(*) FROM v_qualified_bom;
SELECT 'v_verified_room_boundary rows:', COUNT(*) FROM v_verified_room_boundary;
SELECT 'bom_type distribution:', bom_type, COUNT(*) FROM ad_bom WHERE is_active=1 GROUP BY bom_type;
SELECT 'SH room coords:';
SELECT room_name, room_type, min_x_mm, max_x_mm, coordinate_frame FROM ad_room_boundary WHERE building_type='Ifc4_SampleHouse';
SELECT 'product_ref seeded (DINING/LIVING):', COUNT(*) FROM ad_bom_child WHERE bom_id IN ('DINING_SET','LIVING_SET') AND product_ref IS NOT NULL AND is_active=1;
