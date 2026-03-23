-- DV015: Copy M_Product + M_Product_Category from component_library.db to disc_validation.db
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 Step 3
--
-- SAFE: INSERT OR IGNORE — idempotent, re-runnable.
-- NOTE: This COPIES data. component_library.db is NOT modified.
--       M_Product_Image stays in component_library.db (geometry link).

-- ── M_Product_Category (must come first — FK parent) ─────────────────────

CREATE TABLE IF NOT EXISTS M_Product_Category (
    M_Product_Category_ID TEXT PRIMARY KEY,
    Name                  TEXT NOT NULL,
    Description           TEXT,
    Parent_Category_ID    TEXT,
    IFC_Class             TEXT,
    SeqNo                 INTEGER DEFAULT 10,
    IsActive              INTEGER DEFAULT 1,
    FOREIGN KEY (Parent_Category_ID) REFERENCES M_Product_Category(M_Product_Category_ID)
);

-- ── M_Product ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS M_Product (
    product_id                  TEXT PRIMARY KEY,
    product_type                TEXT NOT NULL,
    width                       REAL NOT NULL,
    depth                       REAL NOT NULL,
    height                      REAL NOT NULL,
    ifc_class                   TEXT,
    extracted_from              TEXT NOT NULL DEFAULT 'IFC_EXTRACTION',
    is_active                   INTEGER DEFAULT 1,
    building_type               TEXT,
    unit_cost_rm                REAL DEFAULT 0,
    cost_uom                    TEXT DEFAULT 'EA',
    cost_spec                   TEXT,
    construction_phase          TEXT DEFAULT 'Architecture',
    construction_sequence       INTEGER DEFAULT 6,
    labor_resource              TEXT DEFAULT 'GENERAL',
    labor_rate_rm_per_day       REAL DEFAULT 145.0,
    labor_crew_size             INTEGER DEFAULT 2,
    productivity_rate           REAL DEFAULT 10.0,
    equipment_type              TEXT,
    equipment_rate_rm_per_day   REAL DEFAULT 0,
    equipment_duration_factor   REAL DEFAULT 0,
    carbon_kg_per_unit          REAL DEFAULT 0,
    recyclability               TEXT DEFAULT 'UNKNOWN',
    eol_strategy                TEXT DEFAULT 'LANDFILL',
    lifespan_years              INTEGER DEFAULT 50,
    maintenance_interval_months INTEGER DEFAULT 12,
    M_Product_Category_ID       TEXT REFERENCES M_Product_Category(M_Product_Category_ID)
);

-- ── Copy data via ATTACH ─────────────────────────────────────────────────

ATTACH DATABASE 'library/component_library.db' AS comp;

INSERT OR IGNORE INTO M_Product_Category
SELECT * FROM comp.M_Product_Category;

INSERT OR IGNORE INTO M_Product
SELECT * FROM comp.M_Product;

DETACH DATABASE comp;

-- ── Version stamp ────────────────────────────────────────────────────────

INSERT OR REPLACE INTO AD_SysConfig (Name, Value)
VALUES ('M_PRODUCT_VERSION', 'DV015');

UPDATE AD_SysConfig SET Value = 'DV015' WHERE Name = 'SCHEMA_VERSION';
