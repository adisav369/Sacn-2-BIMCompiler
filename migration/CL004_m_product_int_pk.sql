-- CL004: Tier 2 — M_Product INTEGER PK (component_library.db)
-- Reference: docs/ID_NAME_VALUE_STUDY.md §3.4, prompts/33
-- Strategy: Rename-copy-drop. M_Product_ID INTEGER PK AUTOINCREMENT.
--           product_id TEXT kept for backward compat (Java queries unchanged).
-- Phase A+B: schema only, zero Java changes.
-- component_library.db is local-only — no git operations.

-- 1. Create new table with INTEGER PK
CREATE TABLE M_Product_new (
    M_Product_ID                INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id                  TEXT NOT NULL,          -- old TEXT PK preserved
    Value                       TEXT NOT NULL,          -- iDempiere SearchKey (= product_id)
    Name                        TEXT,                   -- iDempiere Name (= product_id initially)
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

-- 2. Copy data
INSERT INTO M_Product_new (
    product_id, Value, Name, product_type, width, depth, height,
    ifc_class, extracted_from, is_active, building_type,
    unit_cost_rm, cost_uom, cost_spec,
    construction_phase, construction_sequence,
    labor_resource, labor_rate_rm_per_day, labor_crew_size, productivity_rate,
    equipment_type, equipment_rate_rm_per_day, equipment_duration_factor,
    carbon_kg_per_unit, recyclability, eol_strategy,
    lifespan_years, maintenance_interval_months, M_Product_Category_ID)
SELECT
    product_id, product_id, product_id, product_type, width, depth, height,
    ifc_class, extracted_from, is_active, building_type,
    unit_cost_rm, cost_uom, cost_spec,
    construction_phase, construction_sequence,
    labor_resource, labor_rate_rm_per_day, labor_crew_size, productivity_rate,
    equipment_type, equipment_rate_rm_per_day, equipment_duration_factor,
    carbon_kg_per_unit, recyclability, eol_strategy,
    lifespan_years, maintenance_interval_months, M_Product_Category_ID
FROM M_Product;

-- 3. Swap
DROP TABLE M_Product;
ALTER TABLE M_Product_new RENAME TO M_Product;

-- 4. Indexes
CREATE UNIQUE INDEX idx_m_product_product_id ON M_Product(product_id);
CREATE UNIQUE INDEX idx_m_product_value ON M_Product(Value);
