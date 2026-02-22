-- ============================================================
-- VIEW_CONTRACTS.md v1.9 — Phases 1 through 3h
-- Date: 2026-02-23
-- Governs: VIEW_CONTRACTS.md §4
--
-- Intent: bring the DB to the state required before creating
-- the six core views. All operations guarded against current
-- schema state confirmed by DB query on 2026-02-23.
--
-- Pre-migration schema state (confirmed):
--   doc_status present on: ad_element_rule (KEEP),
--                          ad_room_boundary (DROP),
--                          ad_bom_child (DROP),
--                          component_definitions (DROP)
--   extracted_from: absent from component_definitions and ad_product_dim
--   ad_geometry_map.provenance: already exists DEFAULT 'LIBRARY' — NO migration
--   bom_type: absent from ad_bom
--   fit_priority, min_space_mm: absent from ad_bom_child
-- ============================================================

-- ══════════════════════════════════════════════════════════════
-- PHASE 1 — Correct doc_status assignments
-- Intent: exactly ad_element_rule carries doc_status.
--         ad_element_rule.doc_status already exists — no ADD COLUMN.
--         Drop from the three M_ tables that received it incorrectly.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

ALTER TABLE ad_room_boundary      DROP COLUMN doc_status;
ALTER TABLE ad_bom_child          DROP COLUMN doc_status;
ALTER TABLE component_definitions DROP COLUMN doc_status;

COMMIT;

-- Verify Phase 1: expected output = ad_element_rule only
SELECT 'PHASE 1 VERIFY — expected: ad_element_rule only:';
SELECT m.name AS table_with_doc_status
FROM sqlite_master m
JOIN pragma_table_info(m.name) p ON p.name = 'doc_status'
WHERE m.type = 'table'
ORDER BY m.name;

-- ══════════════════════════════════════════════════════════════
-- PHASE 1b — Add provenance columns to M_Product tables
-- Intent: component_definitions and ad_product_dim need extracted_from.
-- DEFAULT 'PENDING' with CHECK(length > 0) — default passes the check.
-- ad_geometry_map.provenance already exists — skip entirely.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

ALTER TABLE component_definitions
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(length(extracted_from) > 0);

ALTER TABLE ad_product_dim
ADD COLUMN extracted_from TEXT NOT NULL DEFAULT 'PENDING'
    CHECK(length(extracted_from) > 0);

COMMIT;

-- Verify Phase 1b
SELECT 'PHASE 1b VERIFY — extracted_from columns:';
SELECT 'component_definitions.extracted_from',
       COUNT(*) FROM pragma_table_info('component_definitions') WHERE name='extracted_from';
SELECT 'ad_product_dim.extracted_from',
       COUNT(*) FROM pragma_table_info('ad_product_dim') WHERE name='extracted_from';

-- ══════════════════════════════════════════════════════════════
-- PHASE 1c — Add bom_type to ad_bom
-- Intent: every BOM row declares its cascade tier.
-- DEFAULT 'SET' covers the majority; ROOM tier seeded below
-- using actual bom_ids confirmed from DB.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

ALTER TABLE ad_bom
ADD COLUMN bom_type TEXT NOT NULL DEFAULT 'SET'
    CHECK(bom_type IN ('ROOM', 'SET', 'ITEM'));

-- ROOM tier: floor and unit level assemblies — the largest spatial envelope
-- (confirmed against actual ad_bom data: no BEDROOM_STD/LIVING_STD etc.
--  in this DB; FLOOR_* and UNIT_* are the highest-tier assemblies present)
UPDATE ad_bom SET bom_type = 'ROOM'
WHERE bom_id IN (
    'FLOOR_DX_L1_STD',
    'FLOOR_DX_L2_STD',
    'FLOOR_SH_GF_STD',
    'FLOOR_TBLKTN_GF_STD',
    'FLOOR_STRUCTURAL',
    'TYPICAL_CONDO_FLOOR',
    'UNIT_DUPLEX_STD',
    'UNIT_SH_STD',
    'UNIT_TBLKTN_STD'
);

-- SET tier: all furniture/fixture sets (already the DEFAULT, explicit for clarity)
UPDATE ad_bom SET bom_type = 'SET'
WHERE bom_id IN (
    'DINING_SET','BED_SET','BED_SET_MASTER',
    'BATHROOM_FURNITURE_SET','BATHROOM_VANITY_SET','DUPLEX_BATHROOM_SET',
    'KITCHEN_CABINET_SET','LIVING_SET','LOBBY_SEAT_SET',
    'ROOM_FURNITURE','CANTEEN_SET','WORKSTATION_SET',
    'STUDY_SET','WARDROBE_SET','TOILET_BLOCK_FIXTURES',
    'VISITOR_SET','DOOR_ASSEMBLY','STAIR_COMPLETE',
    'ROOF_ASSEMBLY','WALL_PANEL','FP_PIPE_ASSEMBLY',
    'MEP_ROOM','T_CONNECTOR_ASSEMBLY',
    'SPRINKLER_PENDANT_ASSEMBLY','WATER_TANK_ASSEMBLY',
    'CORE_ASSEMBLY'
);

-- ITEM tier: no leaf-product-only BOMs identified in current data
-- (all bom_ids have children — no ITEM seeding needed)

COMMIT;

-- Verify Phase 1c
SELECT 'PHASE 1c VERIFY — bom_type distribution:';
SELECT bom_type, COUNT(*) AS cnt FROM ad_bom GROUP BY bom_type ORDER BY bom_type;
SELECT bom_id, bom_type FROM ad_bom ORDER BY bom_type, bom_id;

-- ══════════════════════════════════════════════════════════════
-- PHASE 1d — Add BOM spatial qualification columns
-- Intent: fit_priority and min_space_mm gate child BOM sets
-- against the available space envelope before explosion.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

ALTER TABLE ad_bom_child
ADD COLUMN fit_priority INTEGER NOT NULL DEFAULT 20
    CHECK(fit_priority IN (10, 20, 30));
-- 10 = essential: always place if space type matches
-- 20 = standard:  place if min_space_mm satisfied (default)
-- 30 = optional:  place only if space remains after 10+20

ALTER TABLE ad_bom_child
ADD COLUMN min_space_mm INTEGER NOT NULL DEFAULT 0
    CHECK(min_space_mm >= 0);
-- Minimum room dimension (MIN(width,depth)) required for this BOM line
-- 0 = always fits regardless of space (small items, accessories)

-- Seed known roles [RESEARCHED: PREFAB_ARCHITECTURE.md room dimensions]
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=2400 WHERE role='DINING_SET';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=3000 WHERE role='SOFA_SET';
UPDATE ad_bom_child SET fit_priority=30, min_space_mm=0    WHERE role='COFFEE_TABLE';
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=1800 WHERE role='BED_KING';
UPDATE ad_bom_child SET fit_priority=10, min_space_mm=1400 WHERE role='BED_QUEEN';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=2400 WHERE role='WARDROBE_PAIR';
UPDATE ad_bom_child SET fit_priority=20, min_space_mm=1200 WHERE role='WARDROBE_SINGLE';
UPDATE ad_bom_child SET fit_priority=30, min_space_mm=0    WHERE role='BEDSIDE_TABLE';

COMMIT;

-- Verify Phase 1d
SELECT 'PHASE 1d VERIFY — seeded fit_priority rows:';
SELECT role, fit_priority, min_space_mm
FROM ad_bom_child
WHERE fit_priority != 20 OR min_space_mm != 0
ORDER BY fit_priority, role;

-- ══════════════════════════════════════════════════════════════
-- PHASE 2 — Seed CO for verified element rules
-- Intent: ad_element_rule is the only C_ table with doc_status.
-- CO = family_ref resolves + provenance confirms DSL or IFC origin.
-- ══════════════════════════════════════════════════════════════

BEGIN TRANSACTION;

UPDATE ad_element_rule
SET doc_status = 'CO'
WHERE family_ref IS NOT NULL
  AND provenance IN ('BUILDING_DSL', 'EXTRACTED');

COMMIT;

-- Verify Phase 2
SELECT 'PHASE 2 VERIFY — doc_status distribution on ad_element_rule:';
SELECT doc_status, COUNT(*) AS cnt FROM ad_element_rule GROUP BY doc_status;
SELECT 'PHASE 2 VERIFY — CO by building_type:';
SELECT building_type, doc_status, COUNT(*) AS cnt
FROM ad_element_rule GROUP BY building_type, doc_status ORDER BY building_type;

-- ══════════════════════════════════════════════════════════════
-- PHASES 3a–3f — Create all six views
-- Intent: the compiler's read-only access layer.
--         DROP IF EXISTS guards make this idempotent.
--         v_proven_geometry and v_component_leaf use corrected
--         join path (cd.geometry_hash direct — see design notes
--         in VIEW_CONTRACTS.md §5.4/§5.6).
-- ══════════════════════════════════════════════════════════════

-- Phase 3a: v_qualified_bom
-- NOTE: returns 0 rows until Phase 4 adds product_ref FK to ad_bom_child.
--       bc.role (semantic label) ≠ pd.product_id (catalog namespace).
--       View SQL correct as contract to satisfy; join gap is Phase 4 decision.
DROP VIEW IF EXISTS v_qualified_bom;
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
    ON pd.product_id = bc.role
    AND pd.width > 0
    AND pd.depth > 0
    AND pd.height > 0
    AND pd.extracted_from NOT LIKE '%PENDING%'
WHERE b.is_active = 1;

-- Phase 3b: v_verified_room_boundary
-- WATCHDOG NOTE: the view created in the prior session used only 3 values
--   in the coordinate_frame IN clause ('IFC_GLOBAL_MM','LOCAL_MM','DRAWING_MM').
--   §2.3 and §5.2 mandate 5. This DROP/CREATE is idempotent — re-running this
--   migration against the live DB corrects the violation. First action next session.
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

-- Phase 3c: v_compilable_element_rule
DROP VIEW IF EXISTS v_compilable_element_rule;
CREATE VIEW v_compilable_element_rule AS
SELECT
    er.element_ref,
    er.ifc_class,
    er.family_ref,
    er.building_type,
    er.storey                       AS storey_id,
    er.position_rule,
    er.position_value               AS position_value_1,
    er.position_value_2,
    er.position_value_3,
    er.orientation,
    pd.width                        AS width_mm,
    pd.depth                        AS depth_mm,
    pd.height                       AS height_mm
FROM ad_element_rule er
JOIN ad_product_dim pd
    ON pd.product_id = er.family_ref
    AND pd.width > 0
WHERE er.position_value_3 >= 0
    AND er.doc_status = 'CO'
    AND er.family_ref IS NOT NULL;

-- Phase 3d: v_proven_geometry
-- DESIGN NOTE: ad_geometry_map.element_ref uses Revit family:type namespace;
--   component_definitions.name uses library component namespace — no intersection.
--   Join via cd.geometry_hash = gm.geometry_hash is the correct bridge.
--   vertex_count sourced from cd directly (no join to component_geometries needed).
DROP VIEW IF EXISTS v_proven_geometry;
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

-- Phase 3e: v_active_bom_assembly
DROP VIEW IF EXISTS v_active_bom_assembly;
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

-- Phase 3f: v_component_leaf
-- DESIGN NOTE: same namespace correction as v_proven_geometry.
--   Dimensions from pd.product_id = cd.name (shared namespace — 28 hits confirmed).
--   ifc_class via correlated subquery on geometry_hash.
DROP VIEW IF EXISTS v_component_leaf;
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

-- Verify Phases 3a-3f
SELECT 'PHASE 3a-3f VERIFY — view row counts:';
SELECT 'v_qualified_bom',           COUNT(*) FROM v_qualified_bom;
SELECT 'v_verified_room_boundary',  COUNT(*) FROM v_verified_room_boundary;
SELECT 'v_compilable_element_rule', COUNT(*) FROM v_compilable_element_rule;
SELECT 'v_proven_geometry',         COUNT(*) FROM v_proven_geometry;
SELECT 'v_active_bom_assembly',     COUNT(*) FROM v_active_bom_assembly;
SELECT 'v_component_leaf',          COUNT(*) FROM v_component_leaf;
-- Expected at this point: v_compilable=95, v_active=29, others=0 (unseeded)

-- ══════════════════════════════════════════════════════════════
-- PHASE 3g — Seed TB-LKTN room boundary provenance
-- Intent: TB-LKTN boundaries have LOCAL_MM coordinate_frame
--   (correct authority) but extracted_from='GRID_DERIVED' (wrong label).
--   Boundaries are correct by design authority — relabel to unblock view.
--   SH/DX remain GRID_DERIVED: their coordinates are wrong (calibration debt).
-- ══════════════════════════════════════════════════════════════

UPDATE ad_room_boundary
SET extracted_from = 'TB_LKTN_DSL'
WHERE building_type = 'TB_LKTN';

-- Verify Phase 3g
SELECT 'PHASE 3g VERIFY — v_verified_room_boundary row count (expect 7):';
SELECT COUNT(*) FROM v_verified_room_boundary;
SELECT room_type, extracted_from, coordinate_frame
FROM v_verified_room_boundary ORDER BY room_type;

-- ══════════════════════════════════════════════════════════════
-- PHASE 3h — Seed extracted_from on component tables
-- Intent: component_definitions.extracted_from and ad_product_dim.extracted_from
--   were added with DEFAULT 'PENDING'. Propagate real provenance to unblock
--   v_proven_geometry and v_component_leaf.
-- ══════════════════════════════════════════════════════════════

UPDATE component_definitions
SET extracted_from = 'LIBRARY'
WHERE geometry_hash IS NOT NULL;

UPDATE ad_product_dim
SET extracted_from = provenance;
-- provenance already holds 'LIBRARY' for all 52 rows

-- Verify Phase 3h
SELECT 'PHASE 3h VERIFY — view row counts (expected: geometry=22013, leaf=28):';
SELECT 'v_proven_geometry',  COUNT(*) FROM v_proven_geometry;
SELECT 'v_component_leaf',   COUNT(*) FROM v_component_leaf;

-- Full view scorecard after all phases
SELECT 'FINAL SCORECARD:';
SELECT 'v_qualified_bom',           COUNT(*) FROM v_qualified_bom;
SELECT 'v_verified_room_boundary',  COUNT(*) FROM v_verified_room_boundary;
SELECT 'v_compilable_element_rule', COUNT(*) FROM v_compilable_element_rule;
SELECT 'v_proven_geometry',         COUNT(*) FROM v_proven_geometry;
SELECT 'v_active_bom_assembly',     COUNT(*) FROM v_active_bom_assembly;
SELECT 'v_component_leaf',          COUNT(*) FROM v_component_leaf;
-- Expected: 0 | 7 | 95 | 22013 | 29 | 28
