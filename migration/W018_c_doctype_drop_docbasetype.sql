-- W018: Drop DocBaseType from C_DocType, rename DocSubType → doc_sub_type
-- iDempiere alignment: BIM has one DocType (ConstructionOrder).
-- Routing via M_Product_Category lives on m_bom.m_product_category_id (since S77).
-- Building scoping via doc_sub_type stays (1:1 FK to m_bom BUILDING).
--
-- Implementing prompts/47_drop_legacy_columns.md — Witness: W-PRIME-1..4

CREATE TABLE C_DocType_new (
    C_DocType_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    Value                 TEXT NOT NULL UNIQUE,
    Name                  TEXT NOT NULL,
    doc_sub_type          TEXT,          -- was DocSubType; FK to m_bom.doc_sub_type
    IsDefault             INTEGER DEFAULT 0,
    IsActive              INTEGER DEFAULT 1,
    Description           TEXT,
    ProjectName           TEXT,
    DSLContent            TEXT,
    OutputDbPath          TEXT,
    ReferenceDbPath       TEXT,
    ExpectedElements      INTEGER,
    Provenance            TEXT DEFAULT 'EXTRACTED',
    GeometryFailThreshold INTEGER DEFAULT 0,
    SeqNo                 INTEGER DEFAULT 10,
    AabbWidthMm           REAL,
    AabbDepthMm           REAL,
    AabbHeightMm          REAL,
    C_Campaign_ID         TEXT,
    SalesRep_ID           INTEGER
);

INSERT INTO C_DocType_new SELECT
    C_DocType_ID, Value, Name, DocSubType,
    IsDefault, IsActive, Description, ProjectName, DSLContent,
    OutputDbPath, ReferenceDbPath, ExpectedElements, Provenance,
    GeometryFailThreshold, SeqNo, AabbWidthMm, AabbDepthMm, AabbHeightMm,
    C_Campaign_ID, SalesRep_ID
FROM C_DocType;

DROP TABLE C_DocType;
ALTER TABLE C_DocType_new RENAME TO C_DocType;
