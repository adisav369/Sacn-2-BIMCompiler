-- DV031: Service room seed — resolve P96 blockers + seed discipline data
-- Implementing BBC.md §3.6.2 — Witness: W-DISC-ROOM-1
-- Implementing DISC_VALIDATION_DB_SRS §10.4.6 — shared discipline BOMs
-- Implementing DISC_VALIDATION_DB_SRS §10.4.11 T0.1 — service room seed
--
-- Blocker 1: Rename M_Product_Category CW (ID=114, "Curtain Wall") → CURTAIN_WALL
-- Blocker 2: DEFERRED — M_BOM/M_BOM_Line TEXT FK → INTEGER belongs with P86 _ID fixing pattern
-- Blocker 3: Confirmed non-blocker — discipline linkage is category-only per §3.6.2

-- ============================================================
-- (1) Blocker 1: Rename CW "Curtain Wall" → CURTAIN_WALL
-- ============================================================
-- CW (ID=114) is "Curtain Wall" (architectural). AD_Org CW (ID=6) is "Cold Water".
-- BBC §3.6.2 category match uses Value='CW' — must mean Cold Water, not Curtain Wall.
-- Zero products, BOMs, or BOM lines reference category 114 — safe to rename.

UPDATE M_Product_Category
SET Value = 'CURTAIN_WALL', Name = 'Curtain Wall'
WHERE M_Product_Category_ID = 114 AND Value = 'CW';

-- ============================================================
-- (2) Seed 6 discipline categories (Value matches AD_Org.Value)
-- ============================================================
-- FP_MAIN_ROOM (118) exists but is a tier sub-category, not the discipline root.
-- These are discipline-level categories for BBC §3.6.2 matching.

INSERT OR IGNORE INTO M_Product_Category (Value, Name)
VALUES
    ('FP',   'Fire Protection'),
    ('ACMV', 'HVAC'),
    ('ELEC', 'Electrical'),
    ('CW',   'Cold Water'),
    ('SP',   'Sanitary/Plumbing'),
    ('LPG',  'Gas Piping');

-- ============================================================
-- (3) Seed 6 service room products (discipline anchor rooms)
-- ============================================================
-- These are ARC products (architectural rooms) with discipline-typed categories.
-- BBC §3.6.2: compiler matches category to find tack anchors for parasitic walk.
-- product_type=SERVICE_ROOM, ifc_class=IfcSpace, extracted_from=SPEC_SEED.
-- width/depth/height=0 — actual dimensions come from the building's BOM tack.

INSERT OR IGNORE INTO M_Product (product_id, Value, Name, product_type, width, depth, height, ifc_class, extracted_from, M_Product_Category_ID)
VALUES
    ('ROOM_PUMP',       'ROOM_PUMP',       'Pump Room',        'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'FP')),
    ('ROOM_AHU',        'ROOM_AHU',        'AHU / Plant Room', 'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'ACMV')),
    ('ROOM_DB',         'ROOM_DB',         'DB Room',          'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'ELEC')),
    ('ROOM_WATER_TANK', 'ROOM_WATER_TANK', 'Water Tank Room',  'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'CW')),
    ('ROOM_WET_CORE',   'ROOM_WET_CORE',   'Wet Core',         'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'SP')),
    ('ROOM_GAS_METER',  'ROOM_GAS_METER',  'Gas Meter Room',   'SERVICE_ROOM', 0, 0, 0, 'IfcSpace', 'SPEC_SEED', (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'LPG'));

-- ============================================================
-- (4) Seed 5 missing shared discipline BOMs (FP_SYSTEM exists from DV025)
-- ============================================================
-- Each discipline gets one root BOM in ERP.db per §10.4.6.
-- AD_Org_ID matches the discipline org. Category matches the discipline root.
-- bom_id = Value for iDempiere BOM convention.

INSERT OR IGNORE INTO M_BOM (bom_id, Value, Name, M_Product_Category_ID, AD_Org_ID, Description)
VALUES
    ('ACMV_SYSTEM', 'ACMV_SYSTEM', 'HVAC System',
     (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'ACMV'),
     5, 'Shared ACMV recipe per DISC_VALIDATION_DB_SRS §10.4.6'),

    ('ELEC_SYSTEM', 'ELEC_SYSTEM', 'Electrical System',
     (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'ELEC'),
     4, 'Shared ELEC recipe per DISC_VALIDATION_DB_SRS §10.4.6'),

    ('CW_SYSTEM', 'CW_SYSTEM', 'Cold Water System',
     (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'CW'),
     6, 'Shared CW recipe per DISC_VALIDATION_DB_SRS §10.4.6'),

    ('SP_SYSTEM', 'SP_SYSTEM', 'Sanitary/Plumbing System',
     (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'SP'),
     7, 'Shared SP recipe per DISC_VALIDATION_DB_SRS §10.4.6'),

    ('LPG_SYSTEM', 'LPG_SYSTEM', 'Gas Piping System',
     (SELECT M_Product_Category_ID FROM M_Product_Category WHERE Value = 'LPG'),
     8, 'Shared LPG recipe per DISC_VALIDATION_DB_SRS §10.4.6');
