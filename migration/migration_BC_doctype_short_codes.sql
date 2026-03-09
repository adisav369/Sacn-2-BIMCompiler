-- Migration: M_BomCategory.doc_type → short codes (RE, CO, IN)
-- Aligns M_BomCategory.doc_type with C_DocType.DocBaseType codes.
-- Before: 'Residential', 'Commercial', 'Industrial' (long labels)
-- After:  'RE', 'CO', 'IN' (match C_DocType.DocBaseType)
--
-- Standard Template categories (ST-SH, ST-DX): doc_type='RE', doc_sub_type='ST'.
-- ST is a DocSubType value, not DocBaseType. AABB distinguishes SH vs DX.

-- 1. Recreate M_BomCategory with corrected CHECK (no 'ST' in doc_type)
CREATE TABLE M_BomCategory_new (
    M_BomCategory_ID  TEXT PRIMARY KEY,
    Name              TEXT NOT NULL,
    Description       TEXT,
    IsActive          INTEGER DEFAULT 1,
    C_BPartner_ID     TEXT REFERENCES C_BPartner(C_BPartner_ID),
    Value             TEXT,
    aabb_width_mm     INTEGER DEFAULT 0,
    aabb_depth_mm     INTEGER DEFAULT 0,
    aabb_height_mm    INTEGER DEFAULT 0,
    doc_type          TEXT DEFAULT NULL
        CHECK(doc_type IS NULL OR doc_type IN ('RE','CO','IN')),
    doc_sub_type      TEXT DEFAULT NULL
);

-- 2. Copy data: long labels → short codes, ST-SH/ST-DX get RE/ST together
INSERT INTO M_BomCategory_new
SELECT M_BomCategory_ID, Name, Description, IsActive, C_BPartner_ID, Value,
       aabb_width_mm, aabb_depth_mm, aabb_height_mm,
       CASE
           WHEN doc_type = 'Residential' THEN 'RE'
           WHEN doc_type = 'Commercial'  THEN 'CO'
           WHEN doc_type = 'Industrial'  THEN 'IN'
           ELSE 'RE'
       END,
       CASE
           WHEN M_BomCategory_ID IN ('ST-SH','ST-DX') THEN 'ST'
           ELSE doc_sub_type
       END
FROM M_BomCategory;

-- 3. Swap
DROP TABLE M_BomCategory;
ALTER TABLE M_BomCategory_new RENAME TO M_BomCategory;
