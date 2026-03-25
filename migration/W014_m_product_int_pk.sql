-- W014: Tier 2 — M_Product INTEGER PK (BOM.db template)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Rename-copy-drop. M_Product_ID INTEGER PK AUTOINCREMENT.
--           product_id TEXT kept for backward compat (Java queries unchanged).
-- Phase A+B: schema only, zero Java changes.
-- Apply to SH_BOM.db as proof. Remaining 33 rebuilt via re-extraction.

-- 1. Create new table with INTEGER PK
CREATE TABLE M_Product_new (
    M_Product_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id            TEXT NOT NULL,            -- old TEXT PK preserved
    Value                 TEXT NOT NULL,            -- iDempiere SearchKey (= product_id)
    Name                  TEXT,                     -- iDempiere Name (= product_id initially)
    product_type          TEXT NOT NULL,
    width                 REAL NOT NULL,
    depth                 REAL NOT NULL,
    height                REAL NOT NULL,
    clear_front           REAL DEFAULT 0,
    clear_back            REAL DEFAULT 0,
    clear_left            REAL DEFAULT 0,
    clear_right           REAL DEFAULT 0,
    clear_above           REAL DEFAULT 0,
    clear_below           REAL DEFAULT 0,
    fits_in               TEXT,
    requires_host         TEXT,
    host_min_width        REAL,
    host_min_height       REAL,
    qty_per_area          REAL,
    qty_per_room          INTEGER,
    qty_per_person        REAL,
    max_spacing           REAL,
    conn_points           TEXT,
    code_ref              TEXT,
    is_active             INTEGER DEFAULT 1,
    extracted_from        TEXT NOT NULL DEFAULT 'PENDING',
    material_name         TEXT,
    material_rgba         TEXT,
    component_id          INTEGER,
    bom_id                TEXT,
    ifc_class             TEXT,
    M_Product_Category_ID TEXT
);

-- 2. Copy data (product_id → product_id, Value, Name)
INSERT INTO M_Product_new (
    product_id, Value, Name, product_type, width, depth, height,
    clear_front, clear_back, clear_left, clear_right, clear_above, clear_below,
    fits_in, requires_host, host_min_width, host_min_height,
    qty_per_area, qty_per_room, qty_per_person, max_spacing,
    conn_points, code_ref, is_active, extracted_from,
    material_name, material_rgba, component_id, bom_id, ifc_class,
    M_Product_Category_ID)
SELECT
    product_id, product_id, product_id, product_type, width, depth, height,
    clear_front, clear_back, clear_left, clear_right, clear_above, clear_below,
    fits_in, requires_host, host_min_width, host_min_height,
    qty_per_area, qty_per_room, qty_per_person, max_spacing,
    conn_points, code_ref, is_active, extracted_from,
    material_name, material_rgba, component_id, bom_id, ifc_class,
    M_Product_Category_ID
FROM M_Product;

-- 3. Swap
DROP TABLE M_Product;
ALTER TABLE M_Product_new RENAME TO M_Product;

-- 4. Indexes — product_id remains the lookup key for Java queries
CREATE UNIQUE INDEX idx_m_product_product_id ON M_Product(product_id);
CREATE UNIQUE INDEX idx_m_product_value ON M_Product(Value);
