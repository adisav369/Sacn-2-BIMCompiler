-- W012: Drop doc_base_type from m_bom (write-only, duplicates m_product_category_id)
-- Implementing SpecsAnalysis.txt §10 step 6 — doc_base_type only. doc_sub_type stays (STRUCTURAL).

-- SQLite rename-copy-drop pattern
ALTER TABLE m_bom RENAME TO m_bom_old;

CREATE TABLE m_bom (
    bom_id           TEXT PRIMARY KEY,
    bom_name         TEXT NOT NULL,
    description      TEXT,
    target_ifc_class TEXT DEFAULT 'IfcElementAssembly',
    group_by         TEXT NOT NULL,
    is_active        INTEGER DEFAULT 1,
    bom_level        TEXT DEFAULT 'SET',
    bom_type         TEXT NOT NULL,
    m_product_category_id TEXT,
    doc_sub_type     TEXT,
    seq_no           INTEGER DEFAULT 10,
    origin_x         REAL DEFAULT 0.0,
    origin_y         REAL DEFAULT 0.0,
    origin_z         REAL DEFAULT 0.0,
    entity_type      TEXT DEFAULT 'D',
    aabb_width_mm    INTEGER DEFAULT 0,
    aabb_depth_mm    INTEGER DEFAULT 0,
    aabb_height_mm   INTEGER DEFAULT 0,
    aabb_qualifier   TEXT DEFAULT 'OUTER'
        CHECK(aabb_qualifier IN ('INNER','STRUCTURAL','OUTER','OPENING'))
);

INSERT INTO m_bom (bom_id, bom_name, description, target_ifc_class, group_by,
    is_active, bom_level, bom_type, m_product_category_id, doc_sub_type,
    seq_no, origin_x, origin_y, origin_z, entity_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier)
SELECT bom_id, bom_name, description, target_ifc_class, group_by,
    is_active, bom_level, bom_type, m_product_category_id, doc_sub_type,
    seq_no, origin_x, origin_y, origin_z, entity_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, aabb_qualifier
FROM m_bom_old;

DROP TABLE m_bom_old;
