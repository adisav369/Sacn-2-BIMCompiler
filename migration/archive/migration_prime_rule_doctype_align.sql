-- Prime Rule DocType Alignment
-- Design: scripts/run_RosettaStones.sh lines 48-105
-- Gaps:   memory/prime-rule-design.md
--
-- Three-key match: C_Order.(AABB + DocType + DocSubType) == m_bom.(AABB + DocType + DocSubType)
-- This migration aligns column names so both sides speak the same language.

-- 1. m_bom: add doc_base_type (mirrors C_DocType.DocBaseType)
ALTER TABLE m_bom ADD COLUMN doc_base_type TEXT DEFAULT NULL;

-- Backfill: all BUILDING BOMs are Residential
UPDATE m_bom SET doc_base_type = 'RE' WHERE bom_type = 'BUILDING' AND is_active = 1;

-- 2. C_DocType: widen DocBaseType CHECK to include 'ST'
--    SQLite cannot ALTER CHECK — must recreate table
CREATE TABLE C_DocType_new (
    C_DocType_ID   TEXT PRIMARY KEY,
    Name           TEXT NOT NULL,
    DocBaseType    TEXT NOT NULL CHECK(DocBaseType IN ('RE','CO','IN','ST')),
    DocSubType     TEXT,
    IsDefault      INTEGER DEFAULT 0,
    IsActive       INTEGER DEFAULT 1,
    Description    TEXT,
    ProjectName    TEXT,
    DSLContent     TEXT,
    OutputDbPath   TEXT,
    ReferenceDbPath TEXT,
    ExpectedElements INTEGER,
    Provenance     TEXT DEFAULT 'EXTRACTED',
    GeometryFailThreshold INTEGER DEFAULT 0,
    SeqNo          INTEGER DEFAULT 10,
    AabbWidthMm    REAL,
    AabbDepthMm    REAL,
    AabbHeightMm   REAL,
    C_Campaign_ID  TEXT REFERENCES C_Campaign(C_Campaign_ID),
    SalesRep_ID    INTEGER REFERENCES AD_User(AD_User_ID)
);

INSERT INTO C_DocType_new SELECT * FROM C_DocType;

-- Retire RE_ST, create ST_SH and ST_DX
DELETE FROM C_DocType_new WHERE C_DocType_ID = 'RE_ST';

INSERT INTO C_DocType_new (C_DocType_ID, Name, DocBaseType, DocSubType, IsActive, Description, ProjectName, SeqNo)
VALUES ('ST_SH', 'Standard Sample House', 'ST', 'SH', 1,
        'Template path — select BOMs from catalog by AABB fit for SH-sized building',
        'ST_SH', 40);

INSERT INTO C_DocType_new (C_DocType_ID, Name, DocBaseType, DocSubType, IsActive, Description, ProjectName, SeqNo)
VALUES ('ST_DX', 'Standard Duplex', 'ST', 'DX', 1,
        'Template path — select BOMs from catalog by AABB fit for DX-sized building',
        'ST_DX', 41);

DROP TABLE C_DocType;
ALTER TABLE C_DocType_new RENAME TO C_DocType;

-- 3. M_BomCategory: add doc_sub_type, widen doc_type CHECK to include 'ST'
ALTER TABLE M_BomCategory ADD COLUMN doc_sub_type TEXT DEFAULT NULL;

-- Create ST-SH and ST-DX template category records with AABB
-- (iDempiere composite virtual ID pattern: DocType-DocSubType)
INSERT OR REPLACE INTO M_BomCategory (M_BomCategory_ID, Name, Value, doc_type, doc_sub_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, IsActive)
VALUES ('ST-SH', 'Standard - AABB SH', 'ST-SH', 'Residential', 'SH',
        16868, 8668, 3945, 1);

INSERT OR REPLACE INTO M_BomCategory (M_BomCategory_ID, Name, Value, doc_type, doc_sub_type,
    aabb_width_mm, aabb_depth_mm, aabb_height_mm, IsActive)
VALUES ('ST-DX', 'Standard - AABB DX', 'ST-DX', 'Residential', 'DX',
        9215, 26565, 7885, 1);
