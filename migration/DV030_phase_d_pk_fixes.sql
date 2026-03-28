-- DV030: Phase D PK conformance fixes
-- (1) M_Product.M_Product_Category_ID TEXT → INTEGER FK
-- (2) ad_element_mep_alias.canonical_type FK fix (element_type → Value)
--
-- Safe: M_Product has 2481 rows, 377 NULL category FKs (preserved).
-- Safe: ad_element_mep_alias has 84 rows.
-- Implementing DISC_VALIDATION_DB_SRS.md §11.6.5 — Witness: W-DV-PHASE-D

-- ============================================================
-- (1) M_Product.M_Product_Category_ID TEXT → INTEGER
-- ============================================================
-- Column stores stringified integers ("7","8","14") referencing
-- M_Product_Category.M_Product_Category_ID INTEGER PK.
-- CAST preserves NULL for the 377 uncategorized products.

CREATE TABLE M_Product_new (
    M_Product_ID                INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id                  TEXT NOT NULL,
    Value                       TEXT NOT NULL,
    Name                        TEXT,
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
    M_Product_Category_ID       INTEGER REFERENCES M_Product_Category(M_Product_Category_ID)
);

INSERT INTO M_Product_new (
    M_Product_ID, product_id, Value, Name, product_type,
    width, depth, height, ifc_class, extracted_from,
    is_active, building_type, unit_cost_rm, cost_uom, cost_spec,
    construction_phase, construction_sequence, labor_resource,
    labor_rate_rm_per_day, labor_crew_size, productivity_rate,
    equipment_type, equipment_rate_rm_per_day, equipment_duration_factor,
    carbon_kg_per_unit, recyclability, eol_strategy,
    lifespan_years, maintenance_interval_months,
    M_Product_Category_ID
)
SELECT
    M_Product_ID, product_id, Value, Name, product_type,
    width, depth, height, ifc_class, extracted_from,
    is_active, building_type, unit_cost_rm, cost_uom, cost_spec,
    construction_phase, construction_sequence, labor_resource,
    labor_rate_rm_per_day, labor_crew_size, productivity_rate,
    equipment_type, equipment_rate_rm_per_day, equipment_duration_factor,
    carbon_kg_per_unit, recyclability, eol_strategy,
    lifespan_years, maintenance_interval_months,
    CAST(M_Product_Category_ID AS INTEGER)
FROM M_Product;

DROP TABLE M_Product;
ALTER TABLE M_Product_new RENAME TO M_Product;

CREATE UNIQUE INDEX idx_m_product_product_id ON M_Product(product_id);
CREATE UNIQUE INDEX idx_m_product_value ON M_Product(Value);

-- ============================================================
-- (2) ad_element_mep_alias FK fix
-- ============================================================
-- DV028 renamed ad_element_mep.element_type → Value, but the FK
-- reference in ad_element_mep_alias still points to element_type.
-- PRAGMA foreign_key_check confirms: "foreign key mismatch".

CREATE TABLE ad_element_mep_alias_new (
    alias_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    canonical_type    TEXT NOT NULL REFERENCES ad_element_mep(Value),
    match_field       TEXT NOT NULL CHECK(match_field IN (
                        'ifc_class', 'predefined_type', 'type_class', 'element_name')),
    match_value       TEXT NOT NULL,
    priority          INTEGER NOT NULL DEFAULT 10,
    source            TEXT,
    notes             TEXT,
    is_active         INTEGER DEFAULT 1,
    Name              TEXT,
    Value             TEXT,
    UNIQUE(canonical_type, match_field, match_value)
);

INSERT INTO ad_element_mep_alias_new (
    alias_id, canonical_type, match_field, match_value,
    priority, source, notes, is_active, Name, Value
)
SELECT
    alias_id, canonical_type, match_field, match_value,
    priority, source, notes, is_active, Name, Value
FROM ad_element_mep_alias;

DROP TABLE ad_element_mep_alias;
ALTER TABLE ad_element_mep_alias_new RENAME TO ad_element_mep_alias;

CREATE INDEX idx_alias_match ON ad_element_mep_alias(match_field, match_value);
CREATE INDEX idx_alias_canonical ON ad_element_mep_alias(canonical_type);
