-- DV027: Phase B — M_Product_Category INTEGER PK conformance
-- TEXT codes ('RE','GF','STR'...) move to Value column
-- New INTEGER PK is opaque surrogate (iDempiere convention)
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 — Witness: W-DV-CAT-PK

-- Step 1: Create new table with INTEGER PK
CREATE TABLE M_Product_Category_new (
    M_Product_Category_ID INTEGER PRIMARY KEY AUTOINCREMENT,
    Value                 TEXT NOT NULL UNIQUE,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    IFC_Class             TEXT,
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1
);

-- Step 2: Copy data (old TEXT PK → Value column)
INSERT INTO M_Product_Category_new (Value, Name, Description, IFC_Class, SeqNo, IsActive)
SELECT M_Product_Category_ID, Name, Description, IFC_Class, SeqNo, IsActive
FROM M_Product_Category;

-- Step 3a: Update M_Product FK (TEXT → INTEGER via subquery)
UPDATE M_Product
SET M_Product_Category_ID = (
    SELECT cn.M_Product_Category_ID
    FROM M_Product_Category_new cn
    WHERE cn.Value = M_Product.M_Product_Category_ID
)
WHERE M_Product_Category_ID IS NOT NULL;

-- Step 3b: Update M_BOM FK (TEXT → INTEGER via subquery)
UPDATE M_BOM
SET M_Product_Category_ID = (
    SELECT cn.M_Product_Category_ID
    FROM M_Product_Category_new cn
    WHERE cn.Value = M_BOM.M_Product_Category_ID
)
WHERE M_Product_Category_ID IS NOT NULL;

-- Step 3c: Update M_BOM_Line FK (TEXT → INTEGER via subquery)
UPDATE M_BOM_Line
SET M_Product_Category_ID = (
    SELECT cn.M_Product_Category_ID
    FROM M_Product_Category_new cn
    WHERE cn.Value = M_BOM_Line.M_Product_Category_ID
)
WHERE M_Product_Category_ID IS NOT NULL;

-- Step 4: Drop old, rename new
DROP TABLE M_Product_Category;
ALTER TABLE M_Product_Category_new RENAME TO M_Product_Category;

-- Step 5: Recreate index
CREATE UNIQUE INDEX idx_m_product_category_value ON M_Product_Category(Value);
