-- DV020: Drop Parent_Category_ID from M_Product_Category
-- Implementing SpecsAnalysis.txt §3 — drop Parent_Category_ID
--
-- iDempiere M_Product_Category is FLAT. No parent FK.
-- The cascade (RE→GF→LIVING→leaf) is expressed by M_BOM_Line parent-child,
-- not by a category tree. Parent_Category_ID was a project invention — unnecessary.
--
-- SQLite cannot DROP COLUMN directly. Use rename-copy-drop pattern.

BEGIN;

CREATE TABLE M_Product_Category_new (
    M_Product_Category_ID TEXT PRIMARY KEY,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    IFC_Class             TEXT,
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1
);

INSERT INTO M_Product_Category_new
    (M_Product_Category_ID, Name, Description, IFC_Class, SeqNo, IsActive)
SELECT M_Product_Category_ID, Name, Description, IFC_Class, SeqNo, IsActive
FROM M_Product_Category;

DROP TABLE M_Product_Category;

ALTER TABLE M_Product_Category_new RENAME TO M_Product_Category;

-- Note: ERP.db has no _migration_log table. Migration tracking is via git commit.

COMMIT;
