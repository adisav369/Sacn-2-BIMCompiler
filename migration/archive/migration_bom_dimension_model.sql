-- =============================================================================
-- BOM Dimension Model — Category + Owner + SpaceSize + Table Rename
-- Phase BOM-DIM: Three orthogonal dimensions on M_BOM.
--
-- Reference: docs/BIMasBOMConcept.md
--
-- 1. Rename ad_bom → m_bom, ad_bom_child → m_bom_line, ad_bom_child_param → m_attribute
-- 2. M_BomCategory lookup table (= M_Product_Category in iDempiere)
-- 3. m_bom.bom_owner (= C_BPartner — WHO designed/supplied)
-- 4. m_bom_line.space_*_mm (= SpaceSize AABB — HOW MUCH space)
-- 5. ad_building_registry.bom_owner (= BIM / C_Order scoping)
-- 6. Repurpose bom_category from building code → functional code
--
-- Idempotent: SQLite ALTER TABLE RENAME is safe if already renamed.
-- =============================================================================

-- ─── PART 0: Rename tables (ad_ → m_) ──────────────────────────────────────
-- ad_ = system level (AD_Table, AD_Column). BOM is client-level → m_ prefix.
-- SQLite 3.25+ supports ALTER TABLE RENAME TO.

ALTER TABLE ad_bom             RENAME TO m_bom;
ALTER TABLE ad_bom_child       RENAME TO m_bom_line;
ALTER TABLE ad_bom_child_param RENAME TO m_attribute;

-- ─── PART 1: M_BomCategory lookup table ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS M_BomCategory (
    M_BomCategory_ID  TEXT PRIMARY KEY,
    Name              TEXT NOT NULL,
    Description       TEXT,
    IsActive          INTEGER DEFAULT 1
);

INSERT OR IGNORE INTO M_BomCategory VALUES ('LI', 'Living',    'Living room settings',                    1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('BD', 'Bedroom',   'Bedroom settings',                        1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('KT', 'Kitchen',   'Kitchen settings',                        1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('BT', 'Bathroom',  'Bathroom/toilet settings',                1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('DN', 'Dining',    'Dining settings',                         1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('FR', 'Furniture', 'Leaf furniture items (~4th BOM layer)',    1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('ST', 'Space',     'Buffer/empty space (variable AABB)',      1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('L1', 'Level 1',   'Ground floor assembly',                   1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('L2', 'Level 2',   'Upper floor assembly',                    1);
INSERT OR IGNORE INTO M_BomCategory VALUES ('UN', 'Unit',      'Complete building unit',                   1);

-- ─── PART 2: m_bom — add bom_owner ─────────────────────────────────────────

ALTER TABLE m_bom ADD COLUMN bom_owner TEXT DEFAULT NULL;

-- ─── PART 3: m_bom_line — add SpaceSize AABB ───────────────────────────────

ALTER TABLE m_bom_line ADD COLUMN space_width_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN space_depth_mm  INTEGER DEFAULT 0;
ALTER TABLE m_bom_line ADD COLUMN space_height_mm INTEGER DEFAULT 0;

-- ─── PART 4: ad_building_registry — add bom_owner ──────────────────────────

ALTER TABLE ad_building_registry ADD COLUMN bom_owner TEXT DEFAULT NULL;

-- ─── PART 5: Seed bom_owner on buildings (BIM = C_Order scoping) ────────────

UPDATE ad_building_registry SET bom_owner = 'SH' WHERE building_id = 'Ifc4_SampleHouse';
UPDATE ad_building_registry SET bom_owner = 'DX' WHERE building_id = 'Ifc2x3_Duplex';
UPDATE ad_building_registry SET bom_owner = 'TB' WHERE building_id = 'TB_LKTN';
UPDATE ad_building_registry SET bom_owner = 'TE' WHERE building_id = 'SJTII_Terminal';

-- ─── PART 6: Seed bom_owner on BOMs (mirrors the old bom_category meaning) ─

UPDATE m_bom SET bom_owner = 'SH' WHERE bom_category = 'SH';
UPDATE m_bom SET bom_owner = 'DX' WHERE bom_category = 'DX';
UPDATE m_bom SET bom_owner = 'TB' WHERE bom_category = 'TB';
UPDATE m_bom SET bom_owner = 'MY' WHERE bom_category = 'MY';

-- ─── PART 7: Repurpose bom_category → functional codes ─────────────────────

-- SH BOMs
UPDATE m_bom SET bom_category = 'UN' WHERE bom_id = 'UNIT_SH_STD';
UPDATE m_bom SET bom_category = 'L1' WHERE bom_id = 'FLOOR_SH_GF_STD';
UPDATE m_bom SET bom_category = 'BD' WHERE bom_id = 'SH_BED_SET';
UPDATE m_bom SET bom_category = 'LI' WHERE bom_id = 'SH_LIVING_SET';
UPDATE m_bom SET bom_category = 'DN' WHERE bom_id = 'SH_DINING_SET';

-- DX BOMs
UPDATE m_bom SET bom_category = 'UN' WHERE bom_id = 'UNIT_DUPLEX_STD';
UPDATE m_bom SET bom_category = 'L1' WHERE bom_id = 'FLOOR_DX_L1_STD';
UPDATE m_bom SET bom_category = 'L2' WHERE bom_id = 'FLOOR_DX_L2_STD';
UPDATE m_bom SET bom_category = 'BT' WHERE bom_id = 'DUPLEX_BATHROOM_SET';

-- TB BOMs
UPDATE m_bom SET bom_category = 'UN' WHERE bom_id = 'UNIT_TBLKTN_STD';
UPDATE m_bom SET bom_category = 'L1' WHERE bom_id = 'FLOOR_TBLKTN_GF_STD';

-- MY BOMs
UPDATE m_bom SET bom_category = 'BD' WHERE bom_id = 'BEDROOM_PREFAB_MY_3100';
UPDATE m_bom SET bom_category = 'LI' WHERE bom_id = 'LIVING_PREFAB_MY';
UPDATE m_bom SET bom_category = 'BT' WHERE bom_id = 'BATHROOM_PREFAB_MY';

-- Global BOMs (bom_owner NULL) — assign functional categories
UPDATE m_bom SET bom_category = 'LI' WHERE bom_id = 'LIVING_SET'           AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'DN' WHERE bom_id = 'DINING_SET'           AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'BD' WHERE bom_id = 'BED_SET'              AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'BD' WHERE bom_id = 'BED_SET_MASTER'       AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'KT' WHERE bom_id = 'KITCHEN_CABINET_SET'  AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'BT' WHERE bom_id LIKE 'TOILET_BLOCK%'     AND bom_category IS NULL;
UPDATE m_bom SET bom_category = 'BT' WHERE bom_id LIKE 'BATHROOM%SET'      AND bom_category IS NULL;

-- ─── PART 8: Verify ─────────────────────────────────────────────────────────

SELECT 'M_BomCategory rows:' AS msg, COUNT(*) AS n FROM M_BomCategory;

SELECT 'm_bom bom_owner:' AS msg, bom_owner, COUNT(*) AS n
FROM m_bom GROUP BY bom_owner ORDER BY bom_owner;

SELECT 'm_bom bom_category:' AS msg, bom_category, COUNT(*) AS n
FROM m_bom GROUP BY bom_category ORDER BY bom_category;

SELECT 'BIM bom_owner:' AS msg, building_id, bom_owner
FROM ad_building_registry ORDER BY bom_owner;

-- W-CATEGORY-1: no building codes should remain in bom_category
SELECT 'W-CATEGORY-1 violations (expect 0):' AS msg, COUNT(*) AS n
FROM m_bom WHERE bom_category IN ('SH', 'DX', 'TB', 'MY', 'TL');
