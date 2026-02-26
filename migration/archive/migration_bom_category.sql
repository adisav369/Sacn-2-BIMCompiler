-- =============================================================================
-- BOM Category Dimension
-- Phase BOM-Cat: adds bom_category to ad_bom + ad_building.
--
-- NULL  = globally scoped (any building may use it).
-- 'SH'  = Ifc4_SampleHouse specific.
-- 'DX'  = Ifc2x3_Duplex specific.
-- 'TB'  = TB-LKTN (Rumah Rakyat) specific.
-- 'MY'  = Malaysian terrace standard (wall/room prefabs, generative BOMs).
-- 'TL'  = Terminal — reserved, not yet wired for BOM Drop.
--
-- ad_product_dim intentionally excluded:
--   products are catalog items (intrinsic geometry); categorisation is an
--   assembly/dispatch concern and lives at BOM level.
--
-- ad_bom_child inherits category implicitly through bom_id FK — no extra column.
--
-- ad_room_slot.building_type (added migration_room_slot_building_type.sql)
--   is superseded for BOM-scope filtering once bom_category is live on ad_bom.
--   Planned cleanup: unify both scoping mechanisms in a future migration.
--   Until then, BOTH may be active without conflict (bom_category is a filter
--   dimension; building_type is still used by SlotRegistry.getSlotsForType()).
-- =============================================================================

-- PART 1: Schema changes (idempotent — SQLite ignores duplicate column adds via
-- error, wrap in SELECT to verify before/after if needed)

ALTER TABLE ad_bom      ADD COLUMN bom_category TEXT DEFAULT NULL;
ALTER TABLE ad_building ADD COLUMN bom_category TEXT DEFAULT NULL;

-- PART 2: Tag BOM assemblies

-- SH — Ifc4_SampleHouse specific
UPDATE ad_bom SET bom_category = 'SH' WHERE bom_id IN (
    'UNIT_SH_STD',
    'FLOOR_SH_GF_STD',
    'SH_BED_SET',
    'SH_LIVING_SET',
    'SH_DINING_SET'
);

-- DX — Ifc2x3_Duplex specific
UPDATE ad_bom SET bom_category = 'DX' WHERE bom_id IN (
    'UNIT_DUPLEX_STD',
    'FLOOR_DX_L1_STD',
    'FLOOR_DX_L2_STD',
    'DUPLEX_BATHROOM_SET'
);

-- TB — TB-LKTN specific
UPDATE ad_bom SET bom_category = 'TB' WHERE bom_id IN (
    'UNIT_TBLKTN_STD',
    'FLOOR_TBLKTN_GF_STD'
);

-- MY — Malaysian terrace standard (wall/room prefab BOMs from topology bootstrap)
-- These rows are inserted by migration_topology_maker_bootstrap.sql.
-- UPDATE runs after that migration is applied; INSERT OR IGNORE means rows may
-- already exist; UPDATE is idempotent.
UPDATE ad_bom SET bom_category = 'MY' WHERE bom_id IN (
    'WALL_EXT_MY_150_SOLID',
    'WALL_EXT_MY_150_WIN_STD',
    'WALL_EXT_MY_150_WIN_WIDE',
    'BEDROOM_PREFAB_MY_3100',
    'LIVING_PREFAB_MY',
    'BATHROOM_PREFAB_MY',
    'PORCH_MODULE_MY'
);

-- Global BOMs (LIVING_SET, DINING_SET, BED_SET_MASTER, KITCHEN_CABINET_SET, etc.)
-- remain NULL — DEFAULT NULL covers all untagged rows, no UPDATE needed.

-- PART 3: Tag buildings

UPDATE ad_building SET bom_category = 'SH' WHERE building_type = 'Ifc4_SampleHouse';
UPDATE ad_building SET bom_category = 'DX' WHERE building_type = 'Ifc2x3_Duplex';
UPDATE ad_building SET bom_category = 'TB' WHERE building_type = 'TB_LKTN';
-- Terminal (SJTII_Terminal) has no BOM Drop scope yet — bom_category stays NULL.
-- Tag as 'TL' in a future migration when Terminal BOM wiring begins.

-- PART 4: Verify
SELECT 'ad_bom tagged:' AS msg, bom_category, COUNT(*) AS n
FROM ad_bom GROUP BY bom_category ORDER BY bom_category;

SELECT 'ad_building tagged:' AS msg, building_type, bom_category
FROM ad_building ORDER BY bom_category;
