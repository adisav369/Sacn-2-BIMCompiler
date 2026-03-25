-- W015: Tier 2 — m_bom + m_bom_line INTEGER PK/FK (BOM.db template)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Rename-copy-drop m_bom. M_BOM_ID INTEGER PK AUTOINCREMENT.
--           bom_id TEXT kept for backward compat (Java queries unchanged).
--           Phase B: add M_BOM_ID INTEGER FK to m_bom_line + m_bom_line_ma.
-- Apply to SH_BOM.db as proof. Remaining 33 rebuilt via re-extraction.

-- ═══════════════════════════════════════════════════════════════
-- Phase A: m_bom — add INTEGER PK
-- ═══════════════════════════════════════════════════════════════

-- 1. Create new table with INTEGER PK
CREATE TABLE m_bom_new (
    M_BOM_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id            TEXT NOT NULL,            -- old TEXT PK preserved
    Value             TEXT NOT NULL,            -- iDempiere SearchKey (= bom_id)
    Name              TEXT,                     -- iDempiere Name (= bom_name)
    bom_name          TEXT NOT NULL,
    description       TEXT,
    target_ifc_class  TEXT DEFAULT 'IfcElementAssembly',
    group_by          TEXT NOT NULL,
    is_active         INTEGER DEFAULT 1,
    bom_level         TEXT DEFAULT 'SET',
    bom_type          TEXT NOT NULL,
    m_product_category_id TEXT,
    doc_sub_type      TEXT,
    seq_no            INTEGER DEFAULT 10,
    origin_x          REAL DEFAULT 0.0,
    origin_y          REAL DEFAULT 0.0,
    origin_z          REAL DEFAULT 0.0,
    entity_type       TEXT DEFAULT 'D',
    aabb_width_mm     INTEGER DEFAULT 0,
    aabb_depth_mm     INTEGER DEFAULT 0,
    aabb_height_mm    INTEGER DEFAULT 0,
    aabb_qualifier    TEXT DEFAULT 'OUTER'
        CHECK(aabb_qualifier IN ('INNER','STRUCTURAL','OUTER','OPENING'))
);

-- 2. Copy data (bom_id → bom_id, Value; bom_name → Name)
INSERT INTO m_bom_new (
    bom_id, Value, Name, bom_name, description, target_ifc_class, group_by,
    is_active, bom_level, bom_type, m_product_category_id, doc_sub_type,
    seq_no, origin_x, origin_y, origin_z, entity_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier)
SELECT
    bom_id, bom_id, bom_name, bom_name, description, target_ifc_class, group_by,
    is_active, bom_level, bom_type, m_product_category_id, doc_sub_type,
    seq_no, origin_x, origin_y, origin_z, entity_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier
FROM m_bom;

-- 3. Swap
DROP TABLE m_bom;
ALTER TABLE m_bom_new RENAME TO m_bom;

-- 4. Indexes — bom_id remains the lookup key for Java queries
CREATE UNIQUE INDEX idx_m_bom_bom_id ON m_bom(bom_id);
CREATE UNIQUE INDEX idx_m_bom_value ON m_bom(Value);

-- ═══════════════════════════════════════════════════════════════
-- Phase B: m_bom_line — add M_BOM_ID INTEGER FK alongside bom_id TEXT
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE m_bom_line ADD COLUMN M_BOM_ID INTEGER;
UPDATE m_bom_line SET M_BOM_ID = (
    SELECT m_bom.M_BOM_ID FROM m_bom WHERE m_bom.bom_id = m_bom_line.bom_id
);

-- Phase B: m_bom_line_ma — add M_BOM_ID INTEGER FK alongside bom_id TEXT
ALTER TABLE m_bom_line_ma ADD COLUMN M_BOM_ID INTEGER;
UPDATE m_bom_line_ma SET M_BOM_ID = (
    SELECT m_bom.M_BOM_ID FROM m_bom WHERE m_bom.bom_id = m_bom_line_ma.bom_id
);
