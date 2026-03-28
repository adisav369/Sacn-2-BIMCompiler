-- DV025: Shared discipline recipes in ERP.db
-- Implementing DISC_VALIDATION_DB_SRS.md §10.4.6 — Shared Discipline Recipes
-- Witness: W-RECIPE-1
--
-- Creates M_BOM + M_BOM_Line tables in ERP.db for shared discipline recipes.
-- Seeds FP_SYSTEM (Fire Protection) as first recipe.
-- These are template BOMs: when a discipline C_OrderLine is added to an order,
-- the compiler instantiates the shared recipe into the building's BOM.db.

-- ============================================================
-- 1. FP tier categories (substitution shelves per §10.4.6)
-- ============================================================
INSERT OR IGNORE INTO M_Product_Category (M_Product_Category_ID, Name, Value, Description, SeqNo, IsActive)
VALUES
    ('FP_MAIN_ROOM',    'FP Main Room System',      'FP_MAIN_ROOM',    'Top-level fire protection system per room',  10, 1),
    ('FP_RISER',        'FP Riser',                  'FP_RISER',        'Vertical riser pipe from pump to floor',     20, 1),
    ('FP_DISTRIBUTION', 'FP Distribution',           'FP_DISTRIBUTION', 'Horizontal sprinkler layout per floor',      30, 1),
    ('FP_SUPPLY',       'FP Supply',                 'FP_SUPPLY',       'Pump-to-riser supply link',                  40, 1);

-- ============================================================
-- 2. FP assembly products (parents in the recipe cascade)
-- ============================================================
INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, width, depth, height, ifc_class, extracted_from, is_active, M_Product_Category_ID)
VALUES
    ('FP_SYSTEM',           'FP_SYSTEM',           'Fire Protection System',    'ASSEMBLY', 0, 0, 0, 'IfcSystem',            'SHARED_RECIPE', 1, 'FP_MAIN_ROOM'),
    ('FP_RISER',            'FP_RISER',            'Fire Protection Riser',     'ASSEMBLY', 0, 0, 0, 'IfcPipeSegment',       'SHARED_RECIPE', 1, 'FP_RISER'),
    ('FP_SPRINKLER_LAYOUT', 'FP_SPRINKLER_LAYOUT', 'Sprinkler Layout',          'ASSEMBLY', 0, 0, 0, 'IfcDistributionSystem','SHARED_RECIPE', 1, 'FP_DISTRIBUTION'),
    ('FP_PUMP_LINK',        'FP_PUMP_LINK',        'Pump Supply Link',          'ASSEMBLY', 0, 0, 0, 'IfcPipeSegment',       'SHARED_RECIPE', 1, 'FP_SUPPLY');

-- ============================================================
-- 3. M_BOM table — shared discipline recipes
-- ============================================================
-- Schema is a compatible subset of BOM.db m_bom + AD_Org_ID for discipline ownership.
CREATE TABLE IF NOT EXISTS M_BOM (
    M_BOM_ID              INTEGER PRIMARY KEY AUTOINCREMENT,
    bom_id                TEXT NOT NULL UNIQUE,
    Value                 TEXT,
    Name                  TEXT,
    M_Product_Category_ID TEXT REFERENCES M_Product_Category(M_Product_Category_ID),
    AD_Org_ID             INTEGER REFERENCES AD_Org(AD_Org_ID),
    BOMType               TEXT DEFAULT 'A',
    BOMUse                TEXT DEFAULT 'M',
    entity_type           TEXT DEFAULT 'D',
    IsActive              TEXT DEFAULT 'Y',
    Description           TEXT
);

-- ============================================================
-- 4. M_BOM_Line table — children of shared recipes
-- ============================================================
CREATE TABLE IF NOT EXISTS M_BOM_Line (
    M_BOM_Line_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
    M_BOM_ID              INTEGER NOT NULL REFERENCES M_BOM(M_BOM_ID),
    bom_id                TEXT NOT NULL REFERENCES M_BOM(bom_id),
    child_product_id      TEXT,
    M_Product_Category_ID TEXT REFERENCES M_Product_Category(M_Product_Category_ID),
    qty                   INTEGER NOT NULL DEFAULT 1,
    sequence              INTEGER DEFAULT 10,
    verb_ref              TEXT,
    component_type        TEXT NOT NULL DEFAULT 'MAKE',
    entity_type           TEXT DEFAULT 'D',
    IsActive              TEXT DEFAULT 'Y',
    Description           TEXT
);

-- ============================================================
-- 5. Seed FP_SYSTEM recipe (§10.4.6 cascade)
-- ============================================================
INSERT INTO M_BOM (bom_id, Value, Name, M_Product_Category_ID, AD_Org_ID, BOMType, BOMUse, entity_type, IsActive, Description)
VALUES ('FP_SYSTEM', 'FP_SYSTEM', 'Fire Protection System', 'FP_MAIN_ROOM', 3, 'A', 'M', 'D', 'Y',
        'Shared FP recipe: riser + sprinkler layout + pump link per DISC_VALIDATION_DB_SRS §10.4.6');

-- ============================================================
-- 6. Seed FP_SYSTEM children (3 sub-assemblies)
-- ============================================================
INSERT INTO M_BOM_Line (M_BOM_ID, bom_id, child_product_id, M_Product_Category_ID, qty, sequence, verb_ref, component_type, entity_type, IsActive, Description)
VALUES
    (1, 'FP_SYSTEM', 'FP_RISER',            'FP_RISER',        1, 10, 'ROUTE', 'MAKE', 'D', 'Y',
     'Vertical riser pipe — floor-to-floor, verb=ROUTE determines path'),
    (1, 'FP_SYSTEM', 'FP_SPRINKLER_LAYOUT', 'FP_DISTRIBUTION', 1, 20, 'ROUTE', 'MAKE', 'D', 'Y',
     'Horizontal sprinkler heads — per-floor layout, verb=ROUTE determines spacing'),
    (1, 'FP_SYSTEM', 'FP_PUMP_LINK',        'FP_SUPPLY',       1, 30, 'ROUTE', 'MAKE', 'D', 'Y',
     'Pump-to-riser connection — one per building, verb=ROUTE determines run');
